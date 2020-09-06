#include <iostream>
#include <function/expressions/shared/controlblock.h>

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

  auto innerLE =
      translateExpression(
          globalState, functionState, blockState, builder, sourceExpr);
  checkValidReference(FL(), globalState, functionState, builder, sourceResultType, innerLE);
  discard(
      AFL(std::string("Discard ") + std::to_string((int)discardM->sourceResultType->ownership) + " " + typeid(*discardM->sourceResultType->referend).name() + " from " + typeid(*sourceExpr).name()),
      globalState,
      functionState,
      blockState,
      builder,
      innerLE);
  auto resultLE = makeConstExpr(functionState, builder, makeNever());
  return wrap(functionState->defaultRegion, globalState->metalCache.neverRef, resultLE);
}
