#ifndef FUNCTION_EXPRESSIONS_SHARED_ELEMENTS_H_
#define FUNCTION_EXPRESSIONS_SHARED_ELEMENTS_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"

LLVMValueRef loadElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    Reference* elementRefM,
    LLVMValueRef sizeLE,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    LLVMValueRef indexLE);

LLVMValueRef storeElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    LLVMValueRef sizeLE,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    LLVMValueRef indexLE,
    LLVMValueRef sourceLE);

void foreachArrayElement(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sizeLE,
    LLVMValueRef arrayPtrLE,
    std::function<void(LLVMValueRef, LLVMBuilderRef)> iterationBuilder);

LLVMValueRef getUnknownSizeArrayLength(
    LLVMBuilderRef builder,
    LLVMValueRef arrayPtrLE);

LLVMValueRef getKnownSizeArrayContentsPtr(
    LLVMBuilderRef builder, LLVMValueRef knownSizeArrayWrapperPtrLE);

LLVMValueRef getUnknownSizeArrayLengthPtr(
    LLVMBuilderRef builder, LLVMValueRef unknownSizeArrayWrapperPtrLE);

LLVMValueRef getUnknownSizeArrayContentsPtr(
    LLVMBuilderRef builder, LLVMValueRef knownSizeArrayWrapperPtrLE);
#endif
