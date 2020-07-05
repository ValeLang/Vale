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

void dropReference(
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
  } else if (dynamic_cast<InterfaceReferend*>(sourceRnd)) {
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
            [globalState, expr](LLVMBuilderRef thenBuilder) {
              buildFlare(FL(), globalState, thenBuilder, "Should free this imm interface!");
            });
      }
    }
  } else if (dynamic_cast<StructReferend*>(sourceRnd) ||
      dynamic_cast<KnownSizeArrayT*>(sourceRnd)) {
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

              freeStruct(from, globalState, functionState, thenBuilder, expr, sourceRef);
            });
      }
    }
  } else {
    std::cerr << "Unimplemented type in dropReference: "
        << typeid(*sourceRef->referend).name() << std::endl;
    assert(false);
  }
}
