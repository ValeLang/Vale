#include <iostream>

#include "function/expressions/shared/branch.h"

#include "translatetype.h"

#include "function/expression.h"
#include "expressions.h"

LLVMValueRef translateIf(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    If* iff) {
  // First, we compile the condition expressions, into wherever we're currently
  // building. It's not really part of this mess, but we do use the resulting
  // bit of it in the indirect-branch instruction.
  auto conditionExpr =
      translateExpression(
          globalState, functionState, builder, iff->conditionExpr);
  return buildIfElse(
      functionState,
      builder,
      conditionExpr,
      translateType(globalState, iff->commonSupertype),
      [globalState, functionState, iff](LLVMBuilderRef thenBlockBuilder) {
        return translateExpression(
            globalState, functionState, thenBlockBuilder, iff->thenExpr);
      },
      [globalState, functionState, iff](LLVMBuilderRef elseBlockBuilder) {
        return translateExpression(
            globalState, functionState, elseBlockBuilder, iff->elseExpr);
      });
}
