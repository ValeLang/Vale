#ifndef FUNCTION_EXPRESSIONS_SHARED_H_
#define FUNCTION_EXPRESSIONS_SHARED_H_

#include <llvm-c/Core.h>

#include <unordered_map>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"

LLVMValueRef makeNever();

LLVMValueRef loadMember(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    Mutability mutability,
    int memberMIndex,
    const std::string& memberName);

void makeLocal(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    LLVMValueRef valueToStore);

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef getRcPtr(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr);

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef getRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr);

// See CRCISFAORC for why we don't take in a mutability.
void setRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    LLVMValueRef newRcLE);

// See CRCISFAORC for why we don't take in a mutability.
void adjustRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    // 1 or -1
    int adjustAmount);

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef rcEquals(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    LLVMValueRef equalTo);

void flareRc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int color,
    LLVMValueRef structExpr);

void flare(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int color,
    LLVMValueRef numExpr);

void dropReference(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    LLVMValueRef expr);

#endif