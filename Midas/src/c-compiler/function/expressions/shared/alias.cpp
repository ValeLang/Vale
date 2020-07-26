#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "members.h"
#include "branch.h"
#include "controlblock.h"
#include "heap.h"

// Increments the RC, if needed
void acquireReference(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* resultRef,
    LLVMValueRef expr) {

  auto sourceRnd = resultRef->referend;

  if (dynamic_cast<Int*>(sourceRnd) ||
      dynamic_cast<Bool*>(sourceRnd) ||
      dynamic_cast<Float*>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (dynamic_cast<InterfaceReferend*>(sourceRnd)) {
    if (resultRef->ownership == Ownership::OWN) {
      // We should never acquire an owning reference.
      // If you trip this, perhaps you're trying to borrow, and you handed in
      // the wrong thing for resultRef?
      assert(false);
    } else if (resultRef->ownership == Ownership::BORROW) {
      adjustStrongRc(from, globalState, functionState, builder, expr, resultRef, 1);
    } else if (resultRef->ownership == Ownership::WEAK) {
      assert(false);
    } else if (resultRef->ownership == Ownership::SHARE) {
      if (resultRef->location == Location::INLINE) {
        assert(false); // impl
      } else {
        adjustStrongRc(from, globalState, functionState, builder, expr, resultRef, 1);
      }
    } else assert(false);
  } else if (dynamic_cast<StructReferend*>(sourceRnd) ||
      dynamic_cast<KnownSizeArrayT*>(sourceRnd) ||
      dynamic_cast<UnknownSizeArrayT*>(sourceRnd)) {
    if (resultRef->ownership == Ownership::OWN) {
      // We might be loading a member as an own if we're destructuring.
      // Don't adjust the RC, since we're only moving it.
    } else if (resultRef->ownership == Ownership::BORROW) {
      adjustStrongRc(from, globalState, functionState, builder, expr, resultRef, 1);
    } else if (resultRef->ownership == Ownership::WEAK) {
      assert(false);
    } else if (resultRef->ownership == Ownership::SHARE) {
      if (resultRef->location == Location::INLINE) {
        // Do nothing, we can just let inline structs disappear
      } else {
        adjustStrongRc(from, globalState, functionState, builder, expr, resultRef, 1);
      }
    } else assert(false);
  } else if (dynamic_cast<Str*>(sourceRnd)) {
    assert(resultRef->ownership == Ownership::SHARE);
    assert(resultRef->location == Location::YONDER);
    adjustStrongRc(from, globalState, functionState, builder, expr, resultRef, 1);
  } else {
    std::cerr << "Unimplemented type in acquireReference: "
        << typeid(*resultRef->referend).name() << std::endl;
    assert(false);
  }
}

void discard(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    LLVMValueRef expr) {

  auto sourceRnd = sourceRef->referend;

  if (dynamic_cast<Int*>(sourceRnd) ||
      dynamic_cast<Bool*>(sourceRnd) ||
      dynamic_cast<Float*>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (auto interfaceRnd = dynamic_cast<InterfaceReferend*>(sourceRnd)) {
    if (sourceRef->ownership == Ownership::OWN) {
      // We can't discard owns, they must be destructured.
      assert(false); // impl
    } else if (sourceRef->ownership == Ownership::BORROW) {
      adjustStrongRc(from, globalState, functionState, builder, expr, sourceRef, -1);
    } else if (sourceRef->ownership == Ownership::WEAK) {
      assert(false);
    } else if (sourceRef->ownership == Ownership::SHARE) {
      if (sourceRef->location == Location::INLINE) {
        assert(false); // impl
      } else {
        auto rcLE = adjustStrongRc(from, globalState, functionState, builder, expr, sourceRef, -1);
        buildIf(
            functionState,
            builder,
            isZeroLE(builder, rcLE),
            [globalState, expr, interfaceRnd, sourceRef](LLVMBuilderRef thenBuilder) {
              auto immDestructor = globalState->program->getImmDestructor(sourceRef->referend);

              auto interfaceM = globalState->program->getInterface(interfaceRnd->fullName);
              int indexInEdge = -1;
              for (int i = 0; i < interfaceM->methods.size(); i++) {
                if (interfaceM->methods[i]->prototype == immDestructor) {
                  indexInEdge = i;
                }
              }
              assert(indexInEdge >= 0);

              std::vector<LLVMValueRef> argExprsL = { expr };
              buildInterfaceCall(thenBuilder, argExprsL, 0, indexInEdge);
            });
      }
    } else assert(false);
  } else if (dynamic_cast<StructReferend*>(sourceRnd) ||
      dynamic_cast<KnownSizeArrayT*>(sourceRnd) ||
      dynamic_cast<UnknownSizeArrayT*>(sourceRnd)) {
    if (sourceRef->ownership == Ownership::OWN) {
      // We can't discard owns, they must be destructured.
      assert(false);
    } else if (sourceRef->ownership == Ownership::BORROW) {
      adjustStrongRc(from, globalState, functionState, builder, expr, sourceRef, -1);
    } else if (sourceRef->ownership == Ownership::WEAK) {
      auto structReferend = dynamic_cast<StructReferend*>(sourceRnd);
      assert(structReferend);
      auto wrciLE = getWrciFromWeakRef(builder, expr);
      LLVMBuildCall(builder, globalState->decrementWrc, &wrciLE, 1, "");
    } else if (sourceRef->ownership == Ownership::SHARE) {
      if (sourceRef->location == Location::INLINE) {
        // Do nothing, we can just let inline structs disappear
      } else {
        auto rcLE = adjustStrongRc(from, globalState, functionState, builder, expr, sourceRef, -1);
        buildIf(
            functionState,
            builder,
            isZeroLE(builder, rcLE),
            [from, globalState, functionState, expr, sourceRef](LLVMBuilderRef thenBuilder) {
              auto immDestructor = globalState->program->getImmDestructor(sourceRef->referend);
              auto funcL = globalState->getFunction(immDestructor->name);
              std::vector<LLVMValueRef> argExprsL = { expr };
              return LLVMBuildCall(thenBuilder, funcL, argExprsL.data(), argExprsL.size(), "");
            });
      }
    } else assert(false);
  } else if (dynamic_cast<Str*>(sourceRnd)) {
    assert(sourceRef->ownership == Ownership::SHARE);
    auto rcLE = adjustStrongRc(from, globalState, functionState, builder, expr, sourceRef, -1);
    buildIf(
        functionState,
        builder,
        isZeroLE(builder, rcLE),
        [from, globalState, functionState, blockState, expr, sourceRef](LLVMBuilderRef thenBuilder) {
          freeConcrete(from, globalState, functionState, blockState, thenBuilder, expr, sourceRef);
        });
  } else {
    std::cerr << "Unimplemented type in discard: "
        << typeid(*sourceRef->referend).name() << std::endl;
    assert(false);
  }
}
