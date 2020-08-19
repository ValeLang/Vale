#include <iostream>
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/controlblock.h"

#include "translatetype.h"

#include "function/expression.h"

LLVMValueRef translateInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    InterfaceCall* call) {
  auto argExprsLE =
      translateExpressions(globalState, functionState, blockState, builder, call->argExprs);

  auto argsLE = std::vector<LLVMValueRef>{};
  argsLE.reserve(call->argExprs.size());
  for (int i = 0; i < call->argExprs.size(); i++) {
    auto argLE = translateExpression(globalState, functionState, blockState, builder, call->argExprs[i]);
    checkValidReference(FL(), globalState, functionState, builder, getEffectiveType(globalState, call->functionType->params[i]), argLE);
    argsLE.push_back(argLE);
  }

  LLVMValueRef resultLE;
  switch (call->functionType->params[call->virtualParamIndex]->ownership) {
    case UnconvertedOwnership::OWN:
    case UnconvertedOwnership::BORROW:
    case UnconvertedOwnership::SHARE: {
      resultLE =
          buildInterfaceCall(
              builder, argExprsLE, call->virtualParamIndex, call->indexInEdge);
      break;
    }
    case UnconvertedOwnership::WEAK: {
      assert(false);
      break;
    }
  }
  checkValidReference(FL(), globalState, functionState, builder, getEffectiveType(globalState, call->functionType->returnType), resultLE);

  if (call->functionType->returnType->referend == globalState->metalCache.never) {
    return LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
  } else {
    return resultLE;
  }
}
