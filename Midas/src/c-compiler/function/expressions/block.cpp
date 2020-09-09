#include <iostream>

#include "function/expressions/shared/branch.h"

#include "translatetype.h"

#include "function/expression.h"
#include "expressions.h"

Ref translateBlock(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* parentBlockState,
    LLVMBuilderRef builder,
    Block* block) {

  BlockState childBlockState(parentBlockState);

  auto resultLE =
      translateExpression(globalState, functionState, &childBlockState, builder, block->inner);

  if (block->innerType->referend != globalState->metalCache.never) {
    childBlockState.checkAllIntroducedLocalsWereUnstackified();

    auto childUnstackifiedParentLocalIds =
        childBlockState.getParentLocalIdsThatSelfUnstackified();
    for (auto childUnstackifiedParentLocalId : childUnstackifiedParentLocalIds) {
      parentBlockState->markLocalUnstackified(childUnstackifiedParentLocalId);
    }
  }

  return resultLE;
}
