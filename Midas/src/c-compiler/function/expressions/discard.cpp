#include <iostream>
#include <region/common/controlblock.h>

#include "translatetype.h"

#include "function/expression.h"
#include "function/expressions/shared/shared.h"

Ref translateDiscard(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Discard* discardM) {
  auto sourceExpr = discardM->sourceExpr;
  auto sourceResultType = discardM->sourceResultType;

  auto sourceRef =
      translateExpression(
          globalState, functionState, blockState, builder, sourceExpr);

  if (sourceResultType == globalState->metalCache->emptyTupleStructRef) {
    return sourceRef;
  }

  globalState->getRegion(sourceResultType)
      ->checkValidReference(FL(), functionState, builder, sourceResultType, sourceRef);
  buildFlare(FL(), globalState, functionState, builder, "discarding!");
  globalState->getRegion(sourceResultType)
      ->dealias(
          AFL(std::string("Discard ") + std::to_string((int)discardM->sourceResultType->ownership) + " " + typeid(*discardM->sourceResultType->referend).name() + " from " + typeid(*sourceExpr).name()),
          functionState,
          builder,
          sourceResultType,
          sourceRef);
  buildFlare(FL(), globalState, functionState, builder, "discarded!");
  return makeEmptyTupleRef(globalState);
}
