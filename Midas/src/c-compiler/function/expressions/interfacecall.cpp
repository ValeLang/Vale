#include <iostream>
#include "function/expressions/shared/shared.h"
#include "region/common/controlblock.h"

#include "translatetype.h"

#include "function/expression.h"

Ref translateInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    InterfaceCall* call) {

  auto argExprs = call->argExprs;
  auto virtualParamIndex = call->virtualParamIndex;
  auto interfaceRef = call->interfaceRef;
  auto indexInEdge = call->indexInEdge;
  auto functionType = call->functionType;

  auto argExprsLE =
      translateExpressions(globalState, functionState, blockState, builder, call->argExprs);

  auto argsLE = std::vector<Ref>{};
  argsLE.reserve(call->argExprs.size());
  for (int i = 0; i < call->argExprs.size(); i++) {
    auto argLE = translateExpression(globalState, functionState, blockState, builder, call->argExprs[i]);
    globalState->getRegion(call->functionType->params[i])
        ->checkValidReference(FL(), functionState, builder, call->functionType->params[i], argLE);
    argsLE.push_back(argLE);
  }


  auto virtualArgRefMT = functionType->params[virtualParamIndex];
  auto virtualArgRef = argsLE[virtualParamIndex];
  auto methodFunctionPtrLE =
      globalState->getRegion(virtualArgRefMT)
          ->getInterfaceMethodFunctionPtr(functionState, builder, virtualArgRefMT, virtualArgRef, indexInEdge);
  auto resultLE =
      buildInterfaceCall(
          globalState,
          functionState,
          builder,
          call->functionType,
          methodFunctionPtrLE,
          argExprsLE,
          call->virtualParamIndex);
  globalState->getRegion(call->functionType->returnType)
      ->checkValidReference(FL(), functionState, builder, call->functionType->returnType, resultLE);

  if (call->functionType->returnType->referend == globalState->metalCache->never) {
    return wrap(
        globalState->getRegion(globalState->metalCache->neverRef),
        globalState->metalCache->neverRef,
        LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL)));
  } else {
    return resultLE;
  }
}
