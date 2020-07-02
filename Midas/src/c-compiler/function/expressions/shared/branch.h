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
    std::function<LLVMValueRef(LLVMBuilderRef)> buildThen,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildElse);

void buildIf(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<void(LLVMBuilderRef)> buildThen);

#endif
