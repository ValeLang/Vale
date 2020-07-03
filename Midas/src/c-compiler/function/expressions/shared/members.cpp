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

LLVMValueRef getCountedContents(LLVMBuilderRef builder, LLVMValueRef structLE) {
  return LLVMBuildStructGEP(
      builder,
      structLE,
      1, // Inner struct is after the control block.
      "innerStructPtr");
}

LLVMValueRef loadInnerStructMember(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef innerStructPtrLE,
    Reference* memberType,
    int memberIndex,
    const std::string& memberName) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);

  auto result =
      LLVMBuildLoad(
          builder,
          LLVMBuildStructGEP(
              builder, innerStructPtrLE, memberIndex, memberName.c_str()),
          memberName.c_str());
  acquireReference(globalState, builder, memberType, result);
  return result;
}

void storeInnerStructMember(
    LLVMBuilderRef builder,
    LLVMValueRef innerStructPtrLE,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newValueLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);
  LLVMBuildStore(
      builder,
      newValueLE,
      LLVMBuildStructGEP(
          builder, innerStructPtrLE, memberIndex, memberName.c_str()));
}

LLVMValueRef loadMember(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    LLVMValueRef structExpr,
    Mutability mutability,
    Reference* memberType,
    int memberIndex,
    const std::string& memberName) {

  if (mutability == Mutability::IMMUTABLE) {
    if (isInlImm(globalState, structRefM)) {
      return LLVMBuildExtractValue(
          builder, structExpr, memberIndex, memberName.c_str());
    } else {
      LLVMValueRef innerStructPtrLE = getCountedContents(builder, structExpr);
      return loadInnerStructMember(
          globalState, builder, innerStructPtrLE, memberType, memberIndex, memberName);
    }
  } else if (mutability == Mutability::MUTABLE) {
    LLVMValueRef innerStructPtrLE = getCountedContents(builder, structExpr);
    return loadInnerStructMember(
        globalState, builder, innerStructPtrLE, memberType, memberIndex, memberName);
  } else {
    assert(false);
    return nullptr;
  }
}

LLVMValueRef storeMember(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    StructDefinition* structDefM,
    LLVMValueRef structExpr,
    Reference* memberType,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newValueLE) {
  assert(structDefM->mutability == Mutability::MUTABLE);
  LLVMValueRef innerStructPtrLE = getCountedContents(builder, structExpr);
  LLVMValueRef oldMember =
      loadInnerStructMember(
          globalState, builder, innerStructPtrLE, memberType, memberIndex, memberName);
  storeInnerStructMember(
      builder, innerStructPtrLE, memberIndex, memberName, newValueLE);
  return oldMember;
}

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef getRcPtr(
    LLVMBuilderRef builder,
    LLVMValueRef structPtrLE) {
  // Control block is always the 0th element of every struct.
  auto controlPtrLE = LLVMBuildStructGEP(builder, structPtrLE, 0, "__control_ptr");
  // RC is the 0th member of the RC struct.
  auto rcPtrLE = LLVMBuildStructGEP(builder, controlPtrLE, 0, "__rc_ptr");
  return rcPtrLE;
}

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef getInterfaceRcPtr(
    LLVMBuilderRef builder,
    LLVMValueRef interfaceRefLE) {
  // Interfaces point at an object's control block.
  // It should be at the top of the object, so it's really still zero.
  auto controlPtrLE = getControlBlockPtrFromInterfaceRef(builder, interfaceRefLE);
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
    LLVMValueRef structPtrLE,
    // 1 or -1
    int adjustAmount) {
  adjustCounter(builder, getRcPtr(builder, structPtrLE), adjustAmount);
}

void adjustInterfaceRC(
    LLVMBuilderRef builder,
    LLVMValueRef interfaceRefLE,
    // 1 or -1
    int adjustAmount) {
  adjustCounter(builder, getInterfaceRcPtr(builder, interfaceRefLE), adjustAmount);
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

void fillControlBlock(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtrLE) {
  // TODO: maybe make a const global we can load from, instead of running these
  //  instructions every time.
  LLVMValueRef newControlBlockLE = LLVMGetUndef(globalState->controlBlockStructL);
  newControlBlockLE =
      LLVMBuildInsertValue(
          builder,
          newControlBlockLE,
          // Start at 1, 0 would mean it's dead.
          LLVMConstInt(LLVMInt64Type(), 1, false),
          0,
          "__crc");
  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE);
}
