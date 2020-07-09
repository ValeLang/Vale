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
  auto innerLE =
      translateExpression(
          globalState, functionState, blockState, builder, discardM->sourceExpr);
  checkValidReference(FL(), globalState, functionState, blockState, builder, discardM->sourceResultType, innerLE);
  discard(
      AFL(std::string("Discard from ") + typeid(*discardM->sourceExpr).name()),
      globalState,
      functionState,
      blockState,
      builder,
      discardM->sourceResultType,
      innerLE);
  return makeConstExpr(builder, makeNever());
}
