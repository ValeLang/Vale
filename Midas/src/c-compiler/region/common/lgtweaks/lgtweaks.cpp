#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <function/expressions/shared/weaks.h>
#include <region/common/controlblock.h>
#include "lgtweaks.h"

LLVMValueRef LgtWeaks::weakStructPtrToLgtiWeakInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V1:
      // continue
      break;
    case RegionOverride::FAST:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
    case RegionOverride::RESILIENT_V2:
      assert(false);
      break;
    default:
      assert(false);
      break;
  }

//  checkValidReference(
//      FL(), globalState, functionState, builder, sourceStructTypeM, sourceRefLE);
  auto controlBlockPtr =
      getConcreteControlBlockPtr(globalState,
          builder,
          functionState->defaultRegion->makeWrapperPtr(
              sourceStructTypeM,
              fatWeaks_.getInnerRefFromWeakRef(
                  globalState, functionState, builder, sourceStructTypeM, sourceRefLE)));

  auto interfaceRefLT =
      globalState->getInterfaceWeakRefStruct(
          targetInterfaceReferendM->fullName);
  auto headerLE = getHeaderFromWeakRef(builder, sourceRefLE);

  auto interfaceWeakRefLE = LLVMGetUndef(interfaceRefLT);
  interfaceWeakRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceWeakRefLE,
          headerLE,
          WEAK_REF_MEMBER_INDEX_FOR_HEADER,
          "interfaceRefWithOnlyObj");
  interfaceWeakRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceWeakRefLE,
          makeInterfaceRefStruct(
              globalState, functionState, builder, sourceStructReferendM,
              targetInterfaceReferendM,
              controlBlockPtr),
          WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
          "interfaceRef");
//  checkValidReference(
//      FL(), globalState, functionState, builder, targetInterfaceTypeM, interfaceWeakRefLE);
  return interfaceWeakRefLE;
}
