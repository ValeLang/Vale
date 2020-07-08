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

  auto argsLE = std::vector<LLVMValueRef>{};
  argsLE.reserve(call->argExprs.size());
  for (int i = 0; i < call->argExprs.size(); i++) {
    auto argLE = translateExpression(globalState, functionState, builder, call->argExprs[i]);
    checkValidReference(FL(), globalState, functionState, builder, call->functionType->params[i], argLE);
    argsLE.push_back(argLE);
  }

  auto resultLE =
      buildInterfaceCall(
          builder, argExprsLE, call->virtualParamIndex, call->indexInEdge);
  checkValidReference(FL(), globalState, functionState, builder, call->functionType->returnType, resultLE);

  if (dynamic_cast<Never*>(call->functionType->returnType->referend) != nullptr) {
    return makeConstExpr(builder, makeNever());
  } else {
    return resultLE;
  }
}
