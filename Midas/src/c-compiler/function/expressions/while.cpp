#include <iostream>
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/branch.h"

#include "translatetype.h"

#include "function/expression.h"

LLVMValueRef translateWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    While* whiile) {
  buildWhile(functionState, builder,
      [globalState, functionState, blockState, whiile](LLVMBuilderRef bodyBuilder) {
        return translateExpression(
            globalState, functionState, blockState, bodyBuilder, whiile->bodyExpr);
      });
  // Nobody should use a result of a while, so we'll just return a Never.
  return makeConstExpr(functionState, builder, makeNever());
}
