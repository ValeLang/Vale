

#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include "common/common.h"

LLVMValueRef weakStructRefToWeakInterfaceRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sourceRefLE,
    StructReferend* sourceStructReferendM,
    UnconvertedReference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    UnconvertedReference* targetInterfaceTypeM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V2: {
      return weakStructPtrToGenWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
          sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
    }
    case RegionOverride::RESILIENT_V1: {
      return weakStructPtrToLgtiWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
          sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
    }
    case RegionOverride::FAST:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST: {
      return weakStructPtrToWrciWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
          sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
    }
    default:
      assert(false);
      break;
  }
}
