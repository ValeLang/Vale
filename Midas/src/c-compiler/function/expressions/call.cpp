#include <iostream>

#include "translatetype.h"

#include "function/expression.h"

LLVMValueRef translateCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Call* call) {
  auto funcIter = globalState->functions.find(call->function->name->name);
  assert(funcIter != globalState->functions.end());
  auto funcL = funcIter->second;

  auto argExprsL =
      translateExpressions(globalState, functionState, builder, call->argExprs);
  return LLVMBuildCall(
      builder, funcL, argExprsL.data(), argExprsL.size(), "");
}
