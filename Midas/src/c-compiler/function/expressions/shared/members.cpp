#include <iostream>

#include "translatetype.h"

#include "shared.h"

LLVMValueRef getControlBlockPtr(LLVMBuilderRef builder, LLVMValueRef structLE) {
  return LLVMBuildStructGEP(
      builder,
      structLE,
      0, // Control block is always the 0th member.
      CONTROL_BLOCK_STRUCT_NAME "_memberPtr");
}

LLVMValueRef getInnerStructPtr(LLVMBuilderRef builder, LLVMValueRef structLE) {
  return LLVMBuildStructGEP(
      builder,
      structLE,
      1, // Inner struct is after the control block.
      "innerStructPtr");
}

LLVMValueRef loadInnerStructMember(
    LLVMBuilderRef builder,
    LLVMValueRef innerStructPtrLE,
    int memberIndex,
    const std::string& memberName) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);
  return LLVMBuildLoad(
      builder,
      LLVMBuildStructGEP(
          builder, innerStructPtrLE, memberIndex, memberName.c_str()),
      memberName.c_str());
}

LLVMValueRef loadMember(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    LLVMValueRef structExpr,
    Mutability mutability,
    int memberIndex,
    const std::string& memberName) {

  if (mutability == Mutability::IMMUTABLE) {
    if (isInlImm(globalState, structRefM)) {
      return LLVMBuildExtractValue(
          builder, structExpr, memberIndex, memberName.c_str());
    } else {
      LLVMValueRef innerStructPtrLE = getInnerStructPtr(builder, structExpr);
      return loadInnerStructMember(
          builder, innerStructPtrLE, memberIndex, memberName);
    }
  } else if (mutability == Mutability::MUTABLE) {
    LLVMValueRef innerStructPtrLE = getInnerStructPtr(builder, structExpr);
    return loadInnerStructMember(
        builder, innerStructPtrLE, memberIndex, memberName);
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

LLVMValueRef getRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr) {
  auto rcPtrLE = getRcPtr(builder, structExpr);
  auto rcLE = LLVMBuildLoad(builder, rcPtrLE, "__rc");
  return rcLE;
}

void setRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    LLVMValueRef newRcLE) {
  auto rcPtrLE = getRcPtr(builder, structExpr);
  LLVMBuildStore(builder, newRcLE, rcPtrLE);
}


void adjustRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    // 1 or -1
    int adjustAmount) {
  adjustCounter(builder, getRcPtr(builder, structExpr), adjustAmount);
}

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
