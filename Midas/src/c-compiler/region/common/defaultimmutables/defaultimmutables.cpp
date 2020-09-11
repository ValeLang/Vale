#include <function/expressions/shared/shared.h>
#include <utils/counters.h>
#include <utils/branch.h>
#include <region/common/controlblock.h>
#include <region/common/heap.h>
#include "defaultimmutables.h"

ControlBlock makeImmControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(LLVMStructCreateNamed(LLVMGetGlobalContext(), "immControlBlock"));
  controlBlock.addMember(ControlBlockMember::STRONG_RC);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.build();
  return controlBlock;
}

DefaultImmutables::DefaultImmutables(GlobalState* globalState_, ReferendStructs* wrappedStructs_)
  : globalState(globalState_),
    wrappedStructs(wrappedStructs_) {
  auto int8LT = LLVMInt8Type();

  {
    stringInnerStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), "__Str");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(LLVMInt64Type());
    memberTypesL.push_back(LLVMArrayType(int8LT, 0));
    LLVMStructSetBody(
        stringInnerStructL, memberTypesL.data(), memberTypesL.size(), false);
  }

  {
    stringWrapperStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), "__Str_rc");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(wrappedStructs.controlBlockStructL);
    memberTypesL.push_back(stringInnerStructL);
    LLVMStructSetBody(
        stringWrapperStructL, memberTypesL.data(), memberTypesL.size(), false);
  }
}

void DefaultImmutables::discard(
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
              functionState->defaultRegion->makeWrapperPtr(
                  sourceMT,
                  checkValidReference(FL(), globalState, functionState, thenBuilder, sourceMT,
                      sourceRef));
          auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, thenBuilder,
              sourceWrapperPtrLE);
          deallocate(from, globalState, functionState, thenBuilder, controlBlockPtrLE, sourceMT);
        });
  } else {
    std::cerr << "Unimplemented type in discard: "
        << typeid(*sourceMT->referend).name() << std::endl;
    assert(false);
  }
}


LLVMTypeRef DefaultImmutables::translateType(GlobalState* globalState, Reference* referenceM) {
  if (primitives.isPrimitive(referenceM)) {
    return primitives.translatePrimitive(referenceM);
  } else {
    if (dynamic_cast<Str *>(referenceM->referend) != nullptr) {
      assert(referenceM->location != Location::INLINE);
      assert(referenceM->ownership == Ownership::SHARE);
      return LLVMPointerType(stringWrapperStructL, 0);
    } else if (auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT *>(referenceM->referend)) {
      assert(referenceM->location != Location::INLINE);
      auto knownSizeArrayCountedStructLT = wrappedStructs.getKnownSizeArrayWrapperStruct(knownSizeArrayMT);
      return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
    } else if (auto unknownSizeArrayMT =
        dynamic_cast<UnknownSizeArrayT *>(referenceM->referend)) {
      assert(referenceM->location != Location::INLINE);
      auto unknownSizeArrayCountedStructLT =
          wrappedStructs.getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT);
      return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
    } else if (auto structReferend =
        dynamic_cast<StructReferend *>(referenceM->referend)) {
      if (referenceM->location == Location::INLINE) {
        auto innerStructL = wrappedStructs.getInnerStruct(structReferend);
        return innerStructL;
      } else {
        auto countedStructL = wrappedStructs.getWrapperStruct(structReferend);
        return LLVMPointerType(countedStructL, 0);
      }
    } else if (auto interfaceReferend =
        dynamic_cast<InterfaceReferend *>(referenceM->referend)) {
      assert(referenceM->location != Location::INLINE);
      auto interfaceRefStructL =
          wrappedStructs.getInterfaceRefStruct(interfaceReferend);
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


LLVMTypeRef DefaultImmutables::getControlBlockStruct(Referend* referend) {
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    auto structM = globalState->program->getStruct(structReferend->fullName);
    assert(structM->mutability == Mutability::IMMUTABLE);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
    auto interfaceM = globalState->program->getInterface(interfaceReferend->fullName);
    assert(interfaceM->mutability == Mutability::IMMUTABLE);
  } else if (auto ksaMT = dynamic_cast<KnownSizeArrayT*>(referend)) {
    assert(ksaMT->rawArray->mutability == Mutability::IMMUTABLE);
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT*>(referend)) {
    assert(usaMT->rawArray->mutability == Mutability::IMMUTABLE);
  } else if (auto strMT = dynamic_cast<Str*>(referend)) {
  } else {
    assert(false);
  }
  return wrappedStructs.controlBlockStructL;
}

ControlBlock* DefaultImmutables::getControlBlock(Referend* referend) {
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    auto structM = globalState->program->getStruct(structReferend->fullName);
    assert(structM->mutability == Mutability::IMMUTABLE);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
    auto interfaceM = globalState->program->getInterface(interfaceReferend->fullName);
    assert(interfaceM->mutability == Mutability::IMMUTABLE);
  } else if (auto ksaMT = dynamic_cast<KnownSizeArrayT*>(referend)) {
    assert(ksaMT->rawArray->mutability == Mutability::IMMUTABLE);
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT*>(referend)) {
    assert(usaMT->rawArray->mutability == Mutability::IMMUTABLE);
  } else if (auto strMT = dynamic_cast<Str*>(referend)) {
  } else {
    assert(false);
  }
  return &wrappedStructs.controlBlock;
}
