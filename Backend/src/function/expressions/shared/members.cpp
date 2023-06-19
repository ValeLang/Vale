#include <iostream>

#include "../../../translatetype.h"

#include "shared.h"
#include "../../../region/common/controlblock.h"


Ref loadMember(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref structRegionInstanceRef,
    Reference* structRefM,
    LiveRef structRef,
    Mutability containingStructMutability,
    Reference* memberType,
    int memberIndex,
    Reference* resultType,
    const std::string& memberName) {
  auto memberRef =
      globalState->getRegion(structRefM)->loadMember(
          functionState, builder, structRegionInstanceRef, structRefM, structRef, memberIndex, memberType, resultType, memberName);
  globalState->getRegion(resultType)->alias(from, functionState, builder, resultType, memberRef);
  return memberRef;
}

Ref swapMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref structRegionInstanceRef,
    StructDefinition* structDefM,
    Reference* structRefMT,
    LiveRef structRef,
    int memberIndex,
    const std::string& memberName,
    Ref newMemberRef) {
  auto memberRefMT = structDefM->members[memberIndex]->type;

  assert(structRef.refM->ownership == Ownership::MUTABLE_BORROW);

  assert(structDefM->mutability == Mutability::MUTABLE);

  Ref oldMember =
      globalState->getRegion(structRefMT)->loadMember(
          functionState, builder, structRegionInstanceRef, structRefMT, structRef, memberIndex, memberRefMT, memberRefMT, memberName);
  // We don't adjust the oldMember's RC here because even though we're acquiring
  // a reference to it, the struct is losing its reference, so it cancels out.

  globalState->getRegion(structRefMT)->storeMember(
      functionState, builder, structRegionInstanceRef, structRefMT, structRef, memberIndex, memberName, memberRefMT, newMemberRef);
  // We don't adjust the newMember's RC here because even though the struct is
  // acquiring a reference to it, we're losing ours, so it cancels out.

  return oldMember;
}
