#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "weaks.h"
#include "controlblock.h"


LLVMValueRef getStructContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE wrapperPtrLE) {

  return LLVMBuildStructGEP(
      builder,
      wrapperPtrLE.refLE,
      1, // Inner struct is after the control block.
      "contentsPtr");
}

WrapperPtrLE getStructWrapperPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref refLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      return WrapperPtrLE(refM, checkValidReference(FL(), globalState, functionState, builder, refM, refLE));
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      return lockWeakRef(FL(), globalState, functionState, builder, refM, refLE);
    }
    default:
      assert(false);
  }
}

LLVMValueRef getStructContentsPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref refLE) {
  auto wrapperPtrLE = getStructWrapperPtr(globalState, functionState, builder, refM, refLE);
  return getStructContentsPtr(builder, wrapperPtrLE);
}

// This takes in a LLVMValueRef instead of a Ref because it's not easy to get a pointer to a
// struct's contents from a Ref; if in resilient mode, we have to force deref it, and thats not
// something that should be done for a tiny function like this.
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

  LLVMValueRef sourceMemberLE = nullptr;

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      if (structRefM->location == Location::INLINE) {
        auto structRefLE = checkValidReference(FL(), globalState, functionState, builder, structRefM, structRef);
        sourceMemberLE =
            LLVMBuildExtractValue(
                builder, structRefLE, memberIndex, memberName.c_str());
      } else if (structRefM->location == Location::YONDER) {
        if (structRefM->ownership == Ownership::OWN || structRefM->ownership == Ownership::BORROW || structRefM->ownership == Ownership::SHARE) {
          auto innerStructPtrLE =
              getStructContentsPtr(globalState, functionState, builder, structRefM, structRef);
          sourceMemberLE =
              loadInnerStructMember(
                  builder, innerStructPtrLE, memberIndex, memberName);
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
        sourceMemberLE =
            LLVMBuildExtractValue(
                builder, structRefLE, memberIndex, memberName.c_str());
      } else if (structRefM->location == Location::YONDER) {
        if (structRefM->ownership == Ownership::OWN || structRefM->ownership == Ownership::SHARE) {
          auto innerStructPtrLE =
              getStructContentsPtr(globalState, functionState, builder, structRefM, structRef);
          sourceMemberLE =
              loadInnerStructMember(
                  builder, innerStructPtrLE, memberIndex, memberName);
        } else if (structRefM->ownership == Ownership::BORROW || structRefM->ownership == Ownership::WEAK) {
          auto wrapperStructLE =
              lockWeakRef(from, globalState, functionState, builder, structRefM, structRef);
          auto innerStructPtrLE =
              getStructContentsPtr(builder, wrapperStructLE);
          sourceMemberLE =
              loadInnerStructMember(
                  builder, innerStructPtrLE, memberIndex, memberName);
        } else assert(false);
      } else {
        assert(false);
      }
      break;
    }
    default:
      assert(false);
  }

  auto resultRefLE = upgradeLoadResultToRefWithTargetOwnership(globalState, functionState, builder,
      memberType,
      resultType, sourceMemberLE);
  acquireReference(from, globalState, functionState, builder, resultType, resultRefLE);
  return resultRefLE;
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

  auto objPtrLE =
      WrapperPtrLE(
          structRefMT,
          checkValidReference(FL(), globalState, functionState, builder, structRefMT, structRef));
  auto innerStructPtrLE = getStructContentsPtr(builder, objPtrLE);

  assert(structDefM->mutability == Mutability::MUTABLE);

  LLVMValueRef oldMember =
      loadInnerStructMember(
          builder, innerStructPtrLE, memberIndex, memberName);
  // We don't adjust the oldMember's RC here because even though we're acquiring
  // a reference to it, the struct is losing its reference, so it cancels out.

  auto newMemberLE =
      checkValidReference(FL(), globalState, functionState, builder, memberRefMT, newMemberRef);
  storeInnerStructMember(
      builder, innerStructPtrLE, memberIndex, memberName, newMemberLE);
  // We don't adjust the newMember's RC here because even though the struct is
  // acquiring a reference to it, we're losing ours, so it cancels out.

  return wrap(functionState->defaultRegion, memberRefMT, oldMember);
}
