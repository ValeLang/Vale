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
  auto virtualArgLE = argExprsLE[call->virtualParamIndex];
  auto objPtrLE =
      LLVMBuildPointerCast(
          builder,
          getInterfaceControlBlockPtr(builder, virtualArgLE),
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
