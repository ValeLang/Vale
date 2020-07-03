#ifndef FUNCTION_EXPRESSIONS_SHARED_MEMBERS_H_
#define FUNCTION_EXPRESSIONS_SHARED_MEMBERS_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"

LLVMValueRef loadMember(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    LLVMValueRef structExpr,
    Mutability mutability,
    Reference* memberType,
    int memberIndex,
    const std::string& memberName);

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef getRcPtr(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr);

LLVMValueRef getRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr);

void setRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    LLVMValueRef newRcLE);

void adjustRC(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    // 1 or -1
    int adjustAmount);

void adjustInterfaceRC(
    LLVMBuilderRef builder,
    LLVMValueRef interfaceRefLE,
    // 1 or -1
    int adjustAmount);

LLVMValueRef rcEquals(
    LLVMBuilderRef builder,
    LLVMValueRef structExpr,
    LLVMValueRef equalTo);

void flareRc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int color,
    LLVMValueRef structExpr);

void fillControlBlock(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef newStructLE);

LLVMValueRef getControlBlockPtr(LLVMBuilderRef builder, LLVMValueRef structLE);

LLVMValueRef getCountedContents(LLVMBuilderRef builder, LLVMValueRef structLE);

LLVMValueRef storeMember(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    StructDefinition* structDefM,
    LLVMValueRef structExpr,
    Reference* memberType,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newValueLE);

#endif
