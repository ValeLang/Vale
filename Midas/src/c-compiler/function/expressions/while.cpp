#include <iostream>
#include "function/expressions/shared/shared.h"
#include "utils/branch.h"

#include "translatetype.h"

#include "function/expression.h"

Ref translateWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    While* whiile) {
  buildWhile(
      globalState,
      functionState, builder,
      [globalState, functionState, blockState, whiile](LLVMBuilderRef bodyBuilder) {
        return translateExpression(
            globalState, functionState, blockState, bodyBuilder, whiile->bodyExpr);
      });
  // Nobody should use a result of a while, so we'll just return a Never.
  return makeVoidRef(globalState);
}
