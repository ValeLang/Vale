#include <iostream>
#include "../../region/common/controlblock.h"

#include "../../translatetype.h"

#include "../expression.h"
#include "shared/shared.h"

Ref translateMutabilify(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Mutabilify* mutabilifyM) {
  auto sourceExpr = mutabilifyM->sourceExpr;
  auto sourceResultType = mutabilifyM->sourceType;
  auto resultType = mutabilifyM->resultType;

  auto sourceRef =
      translateExpression(
          globalState, functionState, blockState, builder, sourceExpr);

  globalState->getRegion(sourceResultType)
      ->checkValidReference(FL(), functionState, builder, false, sourceResultType, sourceRef);

  return globalState->getRegion(sourceResultType)->mutabilify(
      FL(), functionState, builder, regionInstanceRef, sourceResultType, sourceRef, resultType);
}

Ref translateImmutabilify(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Immutabilify* immutabilifyM) {
  auto sourceExpr = immutabilifyM->sourceExpr;
  auto sourceResultType = immutabilifyM->sourceType;
  auto resultType = immutabilifyM->resultType;

  auto sourceRef =
      translateExpression(
          globalState, functionState, blockState, builder, sourceExpr);

  globalState->getRegion(sourceResultType)
      ->checkValidReference(FL(), functionState, builder, false, sourceResultType, sourceRef);

  auto liveRef =
      globalState->getRegion(sourceResultType)->immutabilify(
      FL(), functionState, builder, regionInstanceRef, sourceResultType, sourceRef, resultType);
  return wrap(globalState, resultType, liveRef);
}
