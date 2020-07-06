#ifndef FUNCTION_EXPRESSIONS_SHARED_HEAP_H_
#define FUNCTION_EXPRESSIONS_SHARED_HEAP_H_

#include <llvm-c/Core.h>

#include "function/function.h"
#include "globalstate.h"
#include "shared.h"

LLVMValueRef mallocStruct(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef structL);

void freeStruct(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef structLE,
    Reference* structRefM);

LLVMValueRef mallocUnknownSizeArray(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef usaWrapperLT,
    LLVMTypeRef usaElementLT,
    LLVMValueRef lengthLE);

#endif
