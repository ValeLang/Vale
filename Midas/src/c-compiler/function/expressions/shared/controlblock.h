#ifndef FUNCTION_EXPRESSIONS_SHARED_CONTROLBLOCK_H_
#define FUNCTION_EXPRESSIONS_SHARED_CONTROLBLOCK_H_

#include "globalstate.h"
#include <llvm-c/Core.h>

LLVMValueRef getStructControlBlockPtr(
    LLVMBuilderRef builder,
    LLVMValueRef structPtrLE);

LLVMValueRef getInterfaceControlBlockPtr(
    LLVMBuilderRef builder,
    LLVMValueRef interfaceRefLE);

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef getRcPtrFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtr);

LLVMValueRef getObjIdFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtr);

LLVMValueRef getRcFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtrLE);

// Returns object ID
LLVMValueRef fillControlBlock(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtrLE,
    const std::string& typeName);

LLVMValueRef getCountedContentsPtr(LLVMBuilderRef builder, LLVMValueRef structPtrLE);

LLVMValueRef getTypeNameStrPtrFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtr);

#endif //VALEC_CONTROLBLOCK_H
