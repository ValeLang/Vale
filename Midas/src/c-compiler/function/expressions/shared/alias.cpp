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
    LLVMBuilderRef builder,
    Reference* sourceRef,
    LLVMValueRef expr) {

  auto sourceRnd = sourceRef->referend;

  if (dynamic_cast<Int*>(sourceRnd) ||
      dynamic_cast<Bool*>(sourceRnd) ||
      dynamic_cast<Float*>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (dynamic_cast<InterfaceReferend*>(sourceRnd)) {
    if (sourceRef->ownership == Ownership::OWN) {
      // We might be loading a member as an own if we're destructuring.
      // Don't adjust the RC, since we're only moving it.
    } else if (sourceRef->ownership == Ownership::BORROW) {
      adjustRc(from, globalState, builder, expr, sourceRef, 1);
    } else if (sourceRef->ownership == Ownership::SHARE) {
      if (sourceRef->location == Location::INLINE) {
        assert(false); // impl
      } else {
        adjustRc(from, globalState, builder, expr, sourceRef, 1);
      }
    }
  } else if (dynamic_cast<StructReferend*>(sourceRnd) ||
      dynamic_cast<KnownSizeArrayT*>(sourceRnd)) {
    if (sourceRef->ownership == Ownership::OWN) {
      // We might be loading a member as an own if we're destructuring.
      // Don't adjust the RC, since we're only moving it.
    } else if (sourceRef->ownership == Ownership::BORROW) {
      adjustRc(from, globalState, builder, expr, sourceRef, 1);
    } else if (sourceRef->ownership == Ownership::SHARE) {
      if (sourceRef->location == Location::INLINE) {
        // Do nothing, we can just let inline structs disappear
      } else {
        adjustRc(from, globalState, builder, expr, sourceRef, 1);
      }
    }
  } else {
    std::cerr << "Unimplemented type in acquireReference: "
        << typeid(*sourceRef->referend).name() << std::endl;
    assert(false);
  }
}

void discard(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
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
      adjustRc(from, globalState, builder, expr, sourceRef, -1);
    } else if (sourceRef->ownership == Ownership::SHARE) {
      if (sourceRef->location == Location::INLINE) {
        assert(false); // impl
      } else {
        auto rcLE = adjustRc(from, globalState, builder, expr, sourceRef, -1);
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
    }
  } else if (dynamic_cast<StructReferend*>(sourceRnd) ||
      dynamic_cast<KnownSizeArrayT*>(sourceRnd) ||
      dynamic_cast<UnknownSizeArrayT*>(sourceRnd)) {
    if (sourceRef->ownership == Ownership::OWN) {
      // We can't discard owns, they must be destructured.
      assert(false);
    } else if (sourceRef->ownership == Ownership::BORROW) {
      adjustRc(from, globalState, builder, expr, sourceRef, -1);
    } else if (sourceRef->ownership == Ownership::SHARE) {
      if (sourceRef->location == Location::INLINE) {
        // Do nothing, we can just let inline structs disappear
      } else {
        auto rcLE = adjustRc(from, globalState, builder, expr, sourceRef, -1);
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
    }
  } else {
    std::cerr << "Unimplemented type in discard: "
        << typeid(*sourceRef->referend).name() << std::endl;
    assert(false);
  }
}
