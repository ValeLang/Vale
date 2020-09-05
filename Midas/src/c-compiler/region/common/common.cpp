#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <function/expressions/shared/weaks.h>
#include <function/expressions/shared/controlblock.h>
#include "common.h"

LLVMValueRef upcastThinPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    LLVMValueRef sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  assert(sourceStructTypeM->location != Location::INLINE);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      assert(sourceStructTypeM->ownership == Ownership::SHARE ||
          sourceStructTypeM->ownership == Ownership::OWN ||
          sourceStructTypeM->ownership == Ownership::BORROW);
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      assert(sourceStructTypeM->ownership == Ownership::SHARE ||
          sourceStructTypeM->ownership == Ownership::OWN);
      break;
    }
    default:
      assert(false);
  }

  auto interfaceRefLE =
      makeInterfaceRefStruct(
          globalState, functionState, builder, sourceStructReferendM, targetInterfaceReferendM,
          getControlBlockPtr(builder, sourceRefLE, sourceStructTypeM->referend));
  checkValidReference(
      FL(), globalState, functionState, builder, targetInterfaceTypeM, interfaceRefLE);
  return interfaceRefLE;
}

LLVMValueRef upcastWeakFatPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    LLVMValueRef sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  assert(sourceStructTypeM->location != Location::INLINE);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      assert(sourceStructTypeM->ownership == Ownership::WEAK);
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      assert(sourceStructTypeM->ownership == Ownership::BORROW ||
          sourceStructTypeM->ownership == Ownership::WEAK);
      break;
    }
  }

  return functionState->defaultRegion.upcastWeak(
      globalState,
      functionState,
      builder,
      sourceRefLE,
      sourceStructReferendM,
      sourceStructTypeM,
      targetInterfaceReferendM,
      targetInterfaceTypeM);
}
