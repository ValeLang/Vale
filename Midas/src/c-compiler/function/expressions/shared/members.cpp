#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "region/common/controlblock.h"


LLVMValueRef getStructContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE wrapperPtrLE) {
  return LLVMBuildStructGEP(
      builder,
      wrapperPtrLE.refLE,
      1, // Inner struct is after the control block.
      "contentsPtr");
}

Ref loadMember(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    Ref structRef,
    Mutability containingStructMutability,
    Reference* memberType,
    int memberIndex,
    Reference* resultType,
    const std::string& memberName) {
  auto memberRef =
      functionState->defaultRegion->loadMember(
          functionState, builder, structRefM, structRef, memberIndex, memberType, resultType, memberName);
  functionState->defaultRegion->alias(from, functionState, builder, resultType, memberRef);
  return memberRef;
}

Ref swapMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structDefM,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    const std::string& memberName,
    Ref newMemberRef) {
  auto memberRefMT = structDefM->members[memberIndex]->type;

  structRef.assertOwnership(Ownership::BORROW);

  assert(structDefM->mutability == Mutability::MUTABLE);

  Ref oldMember =
      functionState->defaultRegion->loadMember(
          functionState, builder, structRefMT, structRef, memberIndex, memberRefMT, memberRefMT, memberName);
  // We don't adjust the oldMember's RC here because even though we're acquiring
  // a reference to it, the struct is losing its reference, so it cancels out.

  auto newMemberLE =
      globalState->region->checkValidReference(FL(), functionState, builder, memberRefMT, newMemberRef);
  functionState->defaultRegion->storeMember(
      functionState, builder, structRefMT, structRef, memberIndex, memberName, newMemberLE);
  // We don't adjust the newMember's RC here because even though the struct is
  // acquiring a reference to it, we're losing ours, so it cancels out.

  return oldMember;
}
