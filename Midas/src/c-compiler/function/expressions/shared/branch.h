#ifndef FUNCTION_EXPRESSIONS_SHARED_BRANCH_H_
#define FUNCTION_EXPRESSIONS_SHARED_BRANCH_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"

Ref buildIfElse(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref conditionRef,
    LLVMTypeRef resultTypeL,
    Reference* thenResultMT,
    Reference* elseResultMT,
    std::function<Ref(LLVMBuilderRef)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse);

void buildIf(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<void(LLVMBuilderRef)> buildThen);


void buildWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<Ref(LLVMBuilderRef)> buildBody);

void buildWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<Ref(LLVMBuilderRef)> buildCondition,
    std::function<void(LLVMBuilderRef)> buildBody);

#endif
