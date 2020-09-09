#ifndef FUNCTION_EXPRESSIONS_SHARED_HEAP_H_
#define FUNCTION_EXPRESSIONS_SHARED_HEAP_H_

#include <llvm-c/Core.h>

#include "function/function.h"
#include "globalstate.h"
#include "shared.h"

LLVMValueRef mallocKnownSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    LLVMTypeRef referendLT);

LLVMValueRef mallocUnknownSizeArray(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef usaWrapperLT,
    LLVMTypeRef usaElementLT,
    LLVMValueRef lengthLE);

WrapperPtrLE mallocStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE);

// A concrete is a struct, known size array, unknown size array, or Str.
// Takes in a control block ptr so we can free interface refs with this (useful for RC mode)
void freeConcrete(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    ControlBlockPtrLE concreteLE,
    Reference* concreteRefM);

#endif
