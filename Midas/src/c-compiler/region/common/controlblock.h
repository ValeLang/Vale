#ifndef FUNCTION_EXPRESSIONS_SHARED_CONTROLBLOCK_H_
#define FUNCTION_EXPRESSIONS_SHARED_CONTROLBLOCK_H_

#include "globalstate.h"
#include "function/expressions/shared/shared.h"
#include <llvm-c/Core.h>
#include <function/function.h>

constexpr int WEAK_REF_MEMBER_INDEX_FOR_HEADER = 0;
constexpr int WEAK_REF_MEMBER_INDEX_FOR_OBJPTR = 1;

// See CRCISFAORC for why we don't take in a mutability.
// Strong means owning or borrow or shared; things that control the lifetime.
LLVMValueRef getStrongRcPtrFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr);

LLVMValueRef getObjIdFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtr);

// Strong means owning or borrow or shared; things that control the lifetime.
LLVMValueRef getStrongRcFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtrLE);

// Returns object ID
void fillControlBlock(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    Mutability mutability,
    Weakability weakable,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName);

ControlBlockPtrLE getConcreteControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    WrapperPtrLE wrapperPtrLE);

ControlBlockPtrLE getControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    InterfaceFatPtrLE interfaceFatPtrLE);

ControlBlockPtrLE getControlBlockPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    Ref ref,
    Reference* referenceM);

ControlBlockPtrLE getControlBlockPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM);

#endif //VALEC_CONTROLBLOCK_H
