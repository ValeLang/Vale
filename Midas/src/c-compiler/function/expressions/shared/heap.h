#ifndef FUNCTION_EXPRESSIONS_SHARED_HEAP_H_
#define FUNCTION_EXPRESSIONS_SHARED_HEAP_H_

#include <llvm-c/Core.h>

#include "globalstate.h"

LLVMValueRef mallocStruct(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef structL);

void freeStruct(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef structLE);

#endif
