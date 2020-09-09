#ifndef FUNCTION_EXPRESSIONS_SHARED_CONTROLBLOCK_H_
#define FUNCTION_EXPRESSIONS_SHARED_CONTROLBLOCK_H_

#include "globalstate.h"
#include "shared.h"
#include <llvm-c/Core.h>
#include <function/function.h>

constexpr int WEAK_REF_MEMBER_INDEX_FOR_HEADER = 0;
constexpr int WEAK_REF_MEMBER_INDEX_FOR_OBJPTR = 1;

// A concrete is a struct, known size array, unknown size array, or Str.
ControlBlockPtrLE getConcreteControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    WrapperPtrLE wrapperPtrLE);

ControlBlockPtrLE getControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    InterfaceFatPtrLE interfaceFatPtrLE);

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

#endif //VALEC_CONTROLBLOCK_H
