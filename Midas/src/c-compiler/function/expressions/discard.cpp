#include <iostream>
#include <function/expressions/shared/controlblock.h>

#include "translatetype.h"

#include "function/expression.h"
#include "function/expressions/shared/shared.h"

LLVMValueRef translateDiscard(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Discard* discardM) {
  auto sourceExpr = discardM->sourceExpr;
  auto sourceResultType = getEffectiveType(globalState, discardM->sourceResultType);

  auto innerLE =
      translateExpression(
          globalState, functionState, blockState, builder, sourceExpr);
  checkValidReference(FL(), globalState, functionState, builder, sourceResultType, innerLE);
  discard(
      AFL(std::string("Discard from ") + typeid(*sourceExpr).name()),
      globalState,
      functionState,
      blockState,
      builder,
      getEffectiveType(globalState, discardM->sourceResultType),
      innerLE);
  return makeConstExpr(builder, makeNever());
}
