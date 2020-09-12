#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "weaks.h"
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

Ref loadMemberInner(
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

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      if (structRefM->location == Location::INLINE) {
        auto structRefLE = checkValidReference(FL(), globalState, functionState, builder, structRefM, structRef);
        return wrap(globalState->region, memberType,
            LLVMBuildExtractValue(
                builder, structRefLE, memberIndex, memberName.c_str()));
      } else if (structRefM->location == Location::YONDER) {
        if (structRefM->ownership == Ownership::OWN || structRefM->ownership == Ownership::BORROW || structRefM->ownership == Ownership::SHARE) {
          return functionState->defaultRegion->loadMember(
                  functionState, builder, structRefM, structRef, memberIndex, memberType, resultType, memberName);
        } else if (structRefM->ownership == Ownership::WEAK) {
          assert(false); // Can't load from a weak ref, must be a constraint ref
        } else assert(false);
      } else {
        assert(false);
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      if (structRefM->location == Location::INLINE) {
        auto structRefLE =
            checkValidReference(FL(), globalState, functionState, builder, structRefM, structRef);
        return wrap(globalState->region, memberType,
            LLVMBuildExtractValue(
                builder, structRefLE, memberIndex, memberName.c_str()));
      } else if (structRefM->location == Location::YONDER) {
        if (structRefM->ownership == Ownership::OWN || structRefM->ownership == Ownership::SHARE) {
          return functionState->defaultRegion->loadMember(
                  functionState, builder, structRefM, structRef, memberIndex, memberType, resultType, memberName);
        } else if (structRefM->ownership == Ownership::BORROW || structRefM->ownership == Ownership::WEAK) {
          return functionState->defaultRegion->loadMember(
                  functionState, builder, structRefM, structRef, memberIndex, memberType, resultType, memberName);
        } else assert(false);
      } else {
        assert(false);
      }
      break;
    }
    default:
      assert(false);
  }
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
  Ref memberRef =
      loadMemberInner(
          from, globalState, functionState, builder, structRefM, structRef, containingStructMutability, memberType, memberIndex, resultType, memberName);
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
      checkValidReference(FL(), globalState, functionState, builder, memberRefMT, newMemberRef);
  functionState->defaultRegion->storeMember(
      functionState, builder, structRefMT, structRef, memberIndex, memberName, newMemberLE);
  // We don't adjust the newMember's RC here because even though the struct is
  // acquiring a reference to it, we're losing ours, so it cancels out.

  return oldMember;
}
