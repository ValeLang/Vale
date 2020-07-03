#ifndef FUNCTION_EXPRESSIONS_SHARED_SHARED_H_
#define FUNCTION_EXPRESSIONS_SHARED_SHARED_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"

LLVMValueRef makeNever();

void makeLocal(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    LLVMValueRef valueToStore);

void flare(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int color,
    LLVMValueRef numExpr);

void acquireReference(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    LLVMValueRef expr);

void dropReference(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    LLVMValueRef expr);


LLVMValueRef getControlBlockPtr(LLVMBuilderRef builder, LLVMValueRef structLE);

LLVMValueRef getCountedContents(LLVMBuilderRef builder, LLVMValueRef structLE);

void adjustCounter(
    LLVMBuilderRef builder,
    LLVMValueRef counterPtrLE,
    // Amount to add
    int adjustAmount);

LLVMValueRef getTablePtrFromInterfaceRef(
    LLVMBuilderRef builder,
    LLVMValueRef interfaceRefLE);

LLVMValueRef getControlBlockPtrFromInterfaceRef(
    LLVMBuilderRef builder,
    LLVMValueRef interfaceRefLE);

#endif
