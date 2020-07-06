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

// A concrete is a struct, known size array, unknown size array, or Str.
void freeConcrete(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef concreteLE,
    Reference* concreteRefM);

LLVMValueRef mallocUnknownSizeArray(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef usaWrapperLT,
    LLVMTypeRef usaElementLT,
    LLVMValueRef lengthLE);

LLVMValueRef mallocStr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE);

#endif
