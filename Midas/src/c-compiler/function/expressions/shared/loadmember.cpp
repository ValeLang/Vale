#include <iostream>

#include "translatetype.h"

#include "shared.h"

LLVMValueRef loadMember(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    Mutability mutability,
    int memberMIndex,
    const std::string& memberName) {

  if (mutability == Mutability::IMMUTABLE) {
    int memberLIndex = memberMIndex;

    bool inliine = true;//memberLoad->structType->location == INLINE; TODO
    if (inliine) {
      return LLVMBuildExtractValue(
          builder,
          structExpr,
          memberLIndex,
          memberName.c_str());
    } else {
      // TODO: make MemberLoad work for non-inlined structs.
      assert(false);
      return nullptr;
    }
  } else if (mutability == Mutability::MUTABLE) {
    // The +1 is because mutables' first member is the refcounts.
    int memberLIndex = memberMIndex + 1;

    assert(LLVMGetTypeKind(LLVMTypeOf(structExpr)) == LLVMPointerTypeKind);
    return LLVMBuildLoad(
        builder,
        LLVMBuildStructGEP(builder, structExpr, memberLIndex,
            memberName.c_str()),
        memberName.c_str());
  } else {
    assert(false);
    return nullptr;
  }
}

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef getRcPtr(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr) {
  // Control block is always the 0th element of every struct.
  auto controlPtrLE = LLVMBuildStructGEP(builder, structExpr, 0, "__control_ptr");
  // RC is the 0th member of the RC struct.
  auto rcPtrLE = LLVMBuildStructGEP(builder, controlPtrLE, 0, "__rc_ptr");
  return rcPtrLE;
}

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef getRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr) {
  auto rcPtrLE = getRcPtr(builder, structExpr);
  auto rcLE = LLVMBuildLoad(builder, rcPtrLE, "__rc");
  return rcLE;
}

// See CRCISFAORC for why we don't take in a mutability.
void setRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    LLVMValueRef newRcLE) {
  auto rcPtrLE = getRcPtr(builder, structExpr);
  LLVMBuildStore(builder, newRcLE, rcPtrLE);
}


// See CRCISFAORC for why we don't take in a mutability.
void adjustRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    // 1 or -1
    int adjustAmount) {
  auto rcLE = getRC(builder, structExpr);
  auto rcPlus1LE =
      LLVMBuildAdd(
          builder, rcLE, LLVMConstInt(LLVMInt64Type(), adjustAmount, true), "__rc_updated");
  setRC(builder, structExpr, rcPlus1LE);
}

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef rcEquals(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    LLVMValueRef equalTo) {
  auto rcLE = getRC(builder, structExpr);
  return LLVMBuildICmp(builder, LLVMIntEQ, rcLE, equalTo, "__rcEqual");
}

void flareRc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int color,
    LLVMValueRef structExpr) {
  auto rcLE = getRC(builder, structExpr);
  flare(globalState, builder, color, rcLE);
}
