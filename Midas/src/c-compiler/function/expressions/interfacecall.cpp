#include <iostream>
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/controlblock.h"

#include "translatetype.h"

#include "function/expression.h"

LLVMValueRef translateInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    InterfaceCall* call) {
  auto argExprsLE =
      translateExpressions(globalState, functionState, builder, call->argExprs);
  return buildInterfaceCall(
      builder,
      argExprsLE,
      call->virtualParamIndex,
      call->indexInEdge);
}
