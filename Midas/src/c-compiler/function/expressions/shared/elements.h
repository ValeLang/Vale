#ifndef FUNCTION_EXPRESSIONS_SHARED_ELEMENTS_H_
#define FUNCTION_EXPRESSIONS_SHARED_ELEMENTS_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"

Ref loadElement(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    Ref sizeLE,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    Ref indexLE,
    Reference* resultRefM);

Ref storeElement(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    Ref sizeLE,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    Ref indexLE,
    Ref sourceLE);

void foreachArrayElement(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sizeLE,
    LLVMValueRef arrayPtrLE,
    std::function<void(Ref, LLVMBuilderRef)> iterationBuilder);

LLVMValueRef getKnownSizeArrayContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE knownSizeArrayWrapperPtrLE);

LLVMValueRef getUnknownSizeArrayContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE arrayWrapperPtrLE);

LLVMValueRef getUnknownSizeArrayLengthPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE unknownSizeArrayWrapperPtrLE);

WrapperPtrLE getKnownSizeArrayWrapperPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaMT,
    Ref ksaRef);

WrapperPtrLE getUnknownSizeArrayWrapperPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefMT,
    Ref arrayRef);

Ref getUnknownSizeArrayLength(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE arrayRefLE);

Ref getUnknownSizeArrayLength(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Ref arrayRef);

#endif
