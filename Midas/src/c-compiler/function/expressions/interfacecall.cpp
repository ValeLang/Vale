#include <iostream>
#include "function/expressions/shared/shared.h"

#include "translatetype.h"

#include "function/expression.h"

LLVMValueRef translateInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    InterfaceCall* call) {
//  auto funcIter = globalState->functions.find(call->function->name->name);
//  assert(funcIter != globalState->functions.end());
//  auto funcL = funcIter->second;
//
  auto argExprsLE =
      translateExpressions(globalState, functionState, builder, call->argExprs);
  auto virtualArgLE = argExprsLE[call->virtualParamIndex];
  auto objPtrLE =
      LLVMBuildPointerCast(
          builder,
          getControlBlockPtrFromInterfaceRef(builder, virtualArgLE),
          LLVMPointerType(LLVMVoidType(), 0),
          "objAsVoidPtr");
  auto itablePtrLE = getTablePtrFromInterfaceRef(builder, virtualArgLE);
  assert(LLVMGetTypeKind(LLVMTypeOf(itablePtrLE)) == LLVMPointerTypeKind);
  auto funcPtrPtrLE =
      LLVMBuildStructGEP(
          builder, itablePtrLE, call->indexInEdge, "methodPtrPtr");
  auto funcPtrLE = LLVMBuildLoad(builder, funcPtrPtrLE, "methodPtr");

  argExprsLE[call->virtualParamIndex] = objPtrLE;

  return LLVMBuildCall(
      builder, funcPtrLE, argExprsLE.data(), argExprsLE.size(), "");
//  return LLVMConstInt(LLVMInt64Type(), 1338, false);
}
