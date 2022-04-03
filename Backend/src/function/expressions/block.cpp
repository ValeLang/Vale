#include <iostream>

#include "../../utils/branch.h"

#include "../../translatetype.h"

#include "../expression.h"
#include "expressions.h"

Ref translateBlock(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* parentBlockState,
    LLVMBuilderRef builder,
    Block* block) {

  BlockState childBlockState(globalState->addressNumberer, parentBlockState, std::nullopt);

  auto resultLE =
      translateExpression(globalState, functionState, &childBlockState, builder, block->inner);

  if (block->innerType->kind != globalState->metalCache->never) {
    childBlockState.checkAllIntroducedLocalsWereUnstackified();

    auto childUnstackifiedParentLocalIds =
        childBlockState.getParentLocalIdsThatSelfUnstackified();
    for (auto childUnstackifiedParentLocalId : childUnstackifiedParentLocalIds) {
      parentBlockState->markLocalUnstackified(childUnstackifiedParentLocalId);
    }
  }

  return resultLE;
}
