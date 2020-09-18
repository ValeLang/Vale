#ifndef FUNCTION_EXPRESSIONS_SHARED_ELEMENTS_H_
#define FUNCTION_EXPRESSIONS_SHARED_ELEMENTS_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"

Ref loadElementWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    Ref sizeLE,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    Ref indexLE);

//Ref loadElementWithUpgrade(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    BlockState* blockState,
//    LLVMBuilderRef builder,
//    Reference* arrayRefM,
//    Reference* elementRefM,
//    Ref sizeLE,
//    LLVMValueRef arrayPtrLE,
//    Mutability mutability,
//    Ref indexLE,
//    Reference* resultRefM);

Ref storeElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    Ref sizeLE,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    Ref indexLE,
    Ref sourceLE);



LLVMValueRef loadInnerArrayMember(
    LLVMBuilderRef builder,
    LLVMValueRef elemsPtrLE,
    LLVMValueRef indexLE);

void foreachArrayElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sizeRef,
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

#endif
