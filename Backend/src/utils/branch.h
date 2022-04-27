#ifndef FUNCTION_EXPRESSIONS_SHARED_BRANCH_H_
#define FUNCTION_EXPRESSIONS_SHARED_BRANCH_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "../metal/ast.h"
#include "../metal/instructions.h"
#include "../globalstate.h"
#include "../function/function.h"


void buildVoidIfElse(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<void(LLVMBuilderRef)> buildThen,
    std::function<void(LLVMBuilderRef)> buildElse);

LLVMValueRef buildSimpleIfElse(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    LLVMTypeRef resultTypeL,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildThen,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildElse);

Ref buildIfElse(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref conditionRef,
    Reference* thenResultMT,
    Reference* elseResultMT,
    std::function<Ref(LLVMBuilderRef)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse);

void buildIfV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<void(LLVMBuilderRef)> buildThen);

void buildIf(
    GlobalState* globalState,
    LLVMValueRef funcL,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<void(LLVMBuilderRef)> buildThen);


void buildBoolyWhileV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<Ref(LLVMBuilderRef, LLVMBasicBlockRef)> buildBody);

void buildBoolyWhile(
    GlobalState* globalState,
    LLVMValueRef funcL,
    LLVMBuilderRef builder,
    std::function<LLVMValueRef(LLVMBuilderRef, LLVMBasicBlockRef)> buildBody);

void buildBreakyWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<void(LLVMBuilderRef, LLVMBasicBlockRef)> buildBody);

void buildWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<Ref(LLVMBuilderRef)> buildCondition,
    std::function<void(LLVMBuilderRef)> buildBody);

#endif
