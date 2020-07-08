#ifndef FUNCTION_EXPRESSIONS_SHARED_BRANCH_H_
#define FUNCTION_EXPRESSIONS_SHARED_BRANCH_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"

LLVMValueRef buildIfElse(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    LLVMTypeRef resultTypeL,
    bool thenResultIsNever,
    bool elseResultIsNever,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildThen,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildElse);

void buildIf(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<void(LLVMBuilderRef)> buildThen);


void buildWhile(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildBody);

void buildWhile(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildCondition,
    std::function<void(LLVMBuilderRef)> buildBody);

#endif
