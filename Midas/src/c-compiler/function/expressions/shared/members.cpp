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
    Reference* resultType,
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
      acquireReference(from, globalState, functionState, builder, resultType, resultLE);
      return resultLE;
    } else if (structRefM->ownership == Ownership::WEAK) {
      auto thing = forceDerefWeak(globalState, functionState, builder, structRefM, structRefLE);
      LLVMValueRef innerStructPtrLE = getStructContentsPtr(builder, thing);
//      start here, need to make own go to weak
      auto resultLE =
          loadInnerStructMember(
              builder, innerStructPtrLE, memberIndex, memberName);
      checkValidReference(FL(), globalState, functionState, builder, resultType, resultLE);
      acquireReference(from, globalState, functionState, builder, resultType, resultLE);
      return resultLE;
    } else assert(false);
  } else {
    assert(false);
    return nullptr;
  }
}

LLVMValueRef swapMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structDefM,
    Reference* structRefM,
    LLVMValueRef structRefLE,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newMemberLE) {

  LLVMValueRef innerStructPtrLE;
  switch (structRefM->ownership) {
    case Ownership::OWN:
    case Ownership::BORROW:
    case Ownership::SHARE:
      innerStructPtrLE = getStructContentsPtr(builder, structRefLE);
      break;
    case Ownership::WEAK:
      auto voidPtrLE =
          getInnerRefFromWeakRef(globalState, functionState, builder, structRefM, structRefLE);
      innerStructPtrLE = getStructContentsPtr(builder, voidPtrLE);
      break;
  }

  assert(structDefM->mutability == Mutability::MUTABLE);

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
