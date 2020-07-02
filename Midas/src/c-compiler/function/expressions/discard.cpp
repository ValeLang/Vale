#include <iostream>

#include "translatetype.h"

#include "function/expression.h"
#include "function/expressions/shared/shared.h"

LLVMValueRef translateDiscard(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Discard* discardM) {
  auto inner =
      translateExpression(
          globalState, functionState, builder, discardM->sourceExpr);
  dropReference(globalState, builder, discardM->sourceResultType, inner);
  return makeNever();
}
