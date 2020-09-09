#include <iostream>
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/controlblock.h"

#include "translatetype.h"

#include "function/expression.h"

Ref translateInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    InterfaceCall* call) {
  auto argExprsLE =
      translateExpressions(globalState, functionState, blockState, builder, call->argExprs);

  auto argsLE = std::vector<Ref>{};
  argsLE.reserve(call->argExprs.size());
  for (int i = 0; i < call->argExprs.size(); i++) {
    auto argLE = translateExpression(globalState, functionState, blockState, builder, call->argExprs[i]);
    checkValidReference(FL(), globalState, functionState, builder, call->functionType->params[i], argLE);
    argsLE.push_back(argLE);
  }

  buildFlare(FL(), globalState, functionState, builder);

  auto resultLE =
      buildInterfaceCall(
          globalState,
          functionState,
          builder,
          call->functionType,
          argExprsLE,
          call->virtualParamIndex,
          call->indexInEdge);
  checkValidReference(FL(), globalState, functionState, builder, call->functionType->returnType, resultLE);

  if (call->functionType->returnType->referend == globalState->metalCache.never) {
    return wrap(
        functionState->defaultRegion,
        globalState->metalCache.neverRef,
        LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL)));
  } else {
    return resultLE;
  }
}
