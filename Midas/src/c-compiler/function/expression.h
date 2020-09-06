#ifndef EXPRESSION_H_
#define EXPRESSION_H_

#include <llvm-c/Core.h>

#include <unordered_map>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "expressions/shared/ref.h"
#include "function.h"

Ref translateExpression(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Expression* expr);

std::vector<Ref> translateExpressions(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    std::vector<Expression*> exprs);

LLVMValueRef makeNever();

#endif