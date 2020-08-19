#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "controlblock.h"

LLVMValueRef getStructContentsPtr(
    LLVMBuilderRef builder, LLVMValueRef concretePtrLE) {
  return LLVMBuildStructGEP(
      builder,
      concretePtrLE,
      1, // Inner struct is after the control block.
      "contentsPtr");
}

LLVMValueRef loadInnerStructMember(
    LLVMBuilderRef builder,
    LLVMValueRef innerStructPtrLE,
    int memberIndex,
    const std::string& memberName) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);

  auto result =
      LLVMBuildLoad(
          builder,
          LLVMBuildStructGEP(
              builder, innerStructPtrLE, memberIndex, memberName.c_str()),
          memberName.c_str());
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
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    LLVMValueRef structRefLE,
    Mutability containingStructMutability,
    Reference* memberType,
    int memberIndex,
    const std::string& memberName) {

  if (structRefM->location == Location::INLINE) {
    return LLVMBuildExtractValue(
        builder, structRefLE, memberIndex, memberName.c_str());
  } else if (structRefM->location == Location::YONDER) {
    if (structRefM->ownership == Ownership::OWN || structRefM->ownership == Ownership::BORROW || structRefM->ownership == Ownership::SHARE) {
      LLVMValueRef innerStructPtrLE = getStructContentsPtr(builder, structRefLE);
      auto resultLE =
          loadInnerStructMember(
              builder, innerStructPtrLE, memberIndex, memberName);
      acquireReference(from, globalState, functionState, builder, memberType, resultLE);
      return resultLE;
    } else if (structRefM->ownership == Ownership::WEAK) {
      auto thing = forceDerefWeak(globalState, functionState, builder, structRefM, structRefLE);
      LLVMValueRef innerStructPtrLE = getStructContentsPtr(builder, thing);
      auto resultLE =
          loadInnerStructMember(
              builder, innerStructPtrLE, memberIndex, memberName);
      acquireReference(from, globalState, functionState, builder, memberType, resultLE);
      return resultLE;
    } else assert(false);
  } else {
    assert(false);
    return nullptr;
  }
}

LLVMValueRef swapMember(
    LLVMBuilderRef builder,
    StructDefinition* structDefM,
    LLVMValueRef structExpr,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newMemberLE) {
  assert(structDefM->mutability == Mutability::MUTABLE);
  LLVMValueRef innerStructPtrLE = getStructContentsPtr(builder,
      structExpr);

  LLVMValueRef oldMember =
      loadInnerStructMember(
          builder, innerStructPtrLE, memberIndex, memberName);
  // We don't adjust the oldMember's RC here because even though we're acquiring
  // a reference to it, the struct is losing its reference, so it cancels out.

  storeInnerStructMember(
      builder, innerStructPtrLE, memberIndex, memberName, newMemberLE);
  // We don't adjust the newMember's RC here because even though the struct is
  // acquiring a reference to it, we're losing ours, so it cancels out.

  return oldMember;
}
