#include <function/expressions/shared/shared.h>
#include <utils/counters.h>
#include <function/expressions/shared/branch.h>
#include <function/expressions/shared/controlblock.h>
#include <function/expressions/shared/heap.h>
#include "defaultrefcounting.h"

void DefaultRefCounting::discard(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto sourceRnd = sourceMT->referend;

  if (dynamic_cast<Int *>(sourceRnd) ||
      dynamic_cast<Bool *>(sourceRnd) ||
      dynamic_cast<Float *>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (auto interfaceRnd = dynamic_cast<InterfaceReferend *>(sourceRnd)) {
    assert(sourceMT->ownership == Ownership::SHARE);
    if (sourceMT->location == Location::INLINE) {
      assert(false); // impl
    } else {
      auto rcLE = adjustStrongRc(from, globalState, functionState, builder, sourceRef, sourceMT,
          -1);
      buildIf(
          functionState,
          builder,
          isZeroLE(builder, rcLE),
          [globalState, functionState, sourceRef, interfaceRnd, sourceMT](
              LLVMBuilderRef thenBuilder) {
            auto immDestructor = globalState->program->getImmDestructor(sourceMT->referend);

            auto interfaceM = globalState->program->getInterface(interfaceRnd->fullName);
            int indexInEdge = -1;
            for (int i = 0; i < interfaceM->methods.size(); i++) {
              if (interfaceM->methods[i]->prototype == immDestructor) {
                indexInEdge = i;
              }
            }
            assert(indexInEdge >= 0);

            std::vector<Ref> argExprsL = {sourceRef};
            buildInterfaceCall(globalState, functionState, thenBuilder, immDestructor, argExprsL,
                0, indexInEdge);
          });
    }
  } else if (dynamic_cast<StructReferend *>(sourceRnd) ||
      dynamic_cast<KnownSizeArrayT *>(sourceRnd) ||
      dynamic_cast<UnknownSizeArrayT *>(sourceRnd)) {
    assert(sourceMT->ownership == Ownership::SHARE);
    if (sourceMT->location == Location::INLINE) {
      // Do nothing, we can just let inline structs disappear
    } else {
      auto rcLE = adjustStrongRc(from, globalState, functionState, builder, sourceRef, sourceMT,
          -1);
      buildIf(
          functionState,
          builder,
          isZeroLE(builder, rcLE),
          [from, globalState, functionState, sourceRef, sourceMT](LLVMBuilderRef thenBuilder) {
            auto immDestructor = globalState->program->getImmDestructor(sourceMT->referend);
            auto funcL = globalState->getFunction(immDestructor->name);

            auto sourceLE =
                checkValidReference(
                    FL(), globalState, functionState, thenBuilder, sourceMT, sourceRef);
            std::vector<LLVMValueRef> argExprsL = {sourceLE};
            return LLVMBuildCall(thenBuilder, funcL, argExprsL.data(), argExprsL.size(), "");
          });
    }
  } else if (dynamic_cast<Str *>(sourceRnd)) {
    assert(sourceMT->ownership == Ownership::SHARE);
    auto rcLE = adjustStrongRc(from, globalState, functionState, builder, sourceRef, sourceMT,
        -1);
    buildIf(
        functionState,
        builder,
        isZeroLE(builder, rcLE),
        [from, globalState, functionState, blockState, sourceRef, sourceMT](
            LLVMBuilderRef thenBuilder) {
          buildFlare(from, globalState, functionState, thenBuilder, "Freeing shared str!");
          auto sourceWrapperPtrLE =
              WrapperPtrLE(
                  sourceMT,
                  checkValidReference(FL(), globalState, functionState, thenBuilder, sourceMT,
                      sourceRef));
          auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, thenBuilder,
              sourceWrapperPtrLE);
          freeConcrete(
              from, globalState, functionState, blockState, thenBuilder, controlBlockPtrLE,
              sourceMT);
        });
  } else {
    std::cerr << "Unimplemented type in discard: "
        << typeid(*sourceMT->referend).name() << std::endl;
    assert(false);
  }
}


LLVMTypeRef DefaultRefCounting::translateType(GlobalState* globalState, Reference* referenceM) {
  if (primitives.isPrimitive(referenceM)) {
    return primitives.translatePrimitive(referenceM);
  } else {
    if (dynamic_cast<Str *>(referenceM->referend) != nullptr) {
      assert(referenceM->location != Location::INLINE);
      assert(referenceM->ownership == Ownership::SHARE);
      return LLVMPointerType(globalState->stringWrapperStructL, 0);
    } else if (auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT *>(referenceM->referend)) {
      assert(referenceM->location != Location::INLINE);
      auto knownSizeArrayCountedStructLT = globalState->getKnownSizeArrayWrapperStruct(
          knownSizeArrayMT->name);
      return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
    } else if (auto unknownSizeArrayMT =
        dynamic_cast<UnknownSizeArrayT *>(referenceM->referend)) {
      assert(referenceM->location != Location::INLINE);
      auto unknownSizeArrayCountedStructLT =
          globalState->getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT->name);
      return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
    } else if (auto structReferend =
        dynamic_cast<StructReferend *>(referenceM->referend)) {
      if (referenceM->location == Location::INLINE) {
        auto innerStructL = globalState->getInnerStruct(structReferend->fullName);
        return innerStructL;
      } else {
        auto countedStructL = globalState->getWrapperStruct(structReferend->fullName);
        return LLVMPointerType(countedStructL, 0);
      }
    } else if (auto interfaceReferend =
        dynamic_cast<InterfaceReferend *>(referenceM->referend)) {
      assert(referenceM->location != Location::INLINE);
      auto interfaceRefStructL =
          globalState->getInterfaceRefStruct(interfaceReferend->fullName);
      return interfaceRefStructL;
    } else if (dynamic_cast<Never*>(referenceM->referend)) {
      auto result = LLVMPointerType(makeNeverType(), 0);
      assert(LLVMTypeOf(globalState->neverPtr) == result);
      return result;
    } else {
      std::cerr << "Unimplemented type: " << typeid(*referenceM->referend).name() << std::endl;
      assert(false);
      return nullptr;
    }
  }
}