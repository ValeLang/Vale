#include <iostream>
#include <function/expressions/shared/controlblock.h>

#include "translatetype.h"

#include "function/expression.h"
#include "function/expressions/shared/shared.h"

LLVMValueRef translateDiscard(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Discard* discardM) {
  auto innerLE =
      translateExpression(
          globalState, functionState, builder, discardM->sourceExpr);
  checkValidReference(FL(), globalState, functionState, builder, discardM->sourceResultType, innerLE);
  discard(
      AFL(std::string("Discard from ") + typeid(*discardM->sourceExpr).name()),
      globalState,
      functionState,
      builder,
      discardM->sourceResultType,
      innerLE);
  return makeNever();
}
