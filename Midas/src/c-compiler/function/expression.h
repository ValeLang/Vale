#ifndef EXPRESSION_H_
#define EXPRESSION_H_

#include <llvm-c/Core.h>

#include <unordered_map>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function.h"

LLVMValueRef translateExpression(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Expression* expr);

std::vector<LLVMValueRef> translateExpressions(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::vector<Expression*> exprs);

LLVMValueRef makeNever();

LLVMValueRef loadMember(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    LLVMValueRef structExpr,
    Mutability mutability,
    int memberIndex,
    const std::string& memberName);

void makeLocal(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    LLVMValueRef valueToStore);

#endif