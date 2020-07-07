#include <iostream>
#include <function/expressions/shared/shared.h>

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

  auto argsLE = std::vector<LLVMValueRef>{};
  argsLE.reserve(call->argExprs.size());
  for (int i = 0; i < call->argExprs.size(); i++) {
    auto argLE = translateExpression(globalState, functionState, builder, call->argExprs[i]);
    checkValidReference(FL(), globalState, functionState, builder, call->function->params[i], argLE);
    argsLE.push_back(argLE);
  }

  return LLVMBuildCall(builder, funcL, argsLE.data(), argsLE.size(), "");
}
