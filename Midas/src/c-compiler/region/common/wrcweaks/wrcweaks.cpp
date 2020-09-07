#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <function/expressions/shared/weaks.h>
#include <function/expressions/shared/controlblock.h>
#include "wrcweaks.h"

LLVMValueRef WrcWeaks::weakStructPtrToWrciWeakInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sourceWeakStructFatPtrLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::FAST:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
      // continue
      break;
    case RegionOverride::RESILIENT_V1:
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
      getConcreteControlBlockPtr(
          builder,
          WrapperPtrLE(
              sourceStructTypeM,
              fatWeaks_.getInnerRefFromWeakRef(
                  globalState, functionState, builder, sourceStructTypeM, sourceWeakStructFatPtrLE)));

  auto interfaceRefLT =
      globalState->getInterfaceWeakRefStruct(
          targetInterfaceReferendM->fullName);
  auto wrciLE = getWrciFromWeakRef(globalState, builder, sourceWeakStructFatPtrLE);
  auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

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
