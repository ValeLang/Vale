#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <function/expressions/shared/weaks.h>
#include <region/common/controlblock.h>
#include "fatweaks.h"


// Dont use this function for V2
LLVMValueRef FatWeaks::getInnerRefFromWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    WeakFatPtrLE weakFatPtrLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      assert(
          weakRefM->ownership == Ownership::BORROW ||
              weakRefM->ownership == Ownership::WEAK);
      break;
    case RegionOverride::FAST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
      assert(weakRefM->ownership == Ownership::WEAK);
      break;
    default:
      assert(false);
      break;
  }

//  globalState->region->checkValidReference(FL(), functionState, builder, weakRefM, weakFatPtrLE);

  auto innerRefLE = LLVMBuildExtractValue(builder, weakFatPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR, "");
  // We dont check that its valid because if it's a weak ref, it might *not* be pointing at
  // a valid reference.
  return innerRefLE;
}

LLVMValueRef FatWeaks::getInnerRefFromWeakRefWithoutCheck(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    WeakFatPtrLE weakRefLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      assert(
          weakRefM->ownership == Ownership::BORROW ||
              weakRefM->ownership == Ownership::WEAK);
      break;
    case RegionOverride::FAST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
      assert(weakRefM->ownership == Ownership::WEAK);
      break;
    default:
      assert(false);
      break;
  }

  assert(
  // Resilient V2 does this so it can reach into a dead object to see its generation,
  // which generational heap guarantees is unchanged.
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V2 ||
          // Census does this to get at a weak interface ref's itable, even for a dead object.
              globalState->opt->census);
  auto innerRefLE = LLVMBuildExtractValue(builder, weakRefLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR, "");
  // We dont check that its valid because if it's a weak ref, it might *not* be pointing at
  // a valid reference.
  return innerRefLE;
}
