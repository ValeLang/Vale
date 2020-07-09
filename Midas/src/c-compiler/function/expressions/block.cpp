#include <iostream>

#include "function/expressions/shared/branch.h"

#include "translatetype.h"

#include "function/expression.h"
#include "expressions.h"

LLVMValueRef translateBlock(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* parentBlockState,
    LLVMBuilderRef builder,
    Block* block) {

  BlockState childBlockState = *parentBlockState;

  auto resultLE =
      translateExpression(globalState, functionState, &childBlockState, builder, block->inner);


  for (auto childBlockLocalIdAndLocalAddr : childBlockState.localAddrByLocalId) {
    auto childBlockLocalId = childBlockLocalIdAndLocalAddr.first;
    // Ignore those that were made in the parent.
    if (parentBlockState->localAddrByLocalId.count(childBlockLocalId))
      continue;
    // childBlockLocalId came from the child block. Make sure the child unstackified it.
    if (childBlockState.unstackifiedLocalIds.count(childBlockLocalId) == 0) {
      std::cerr << "Un-unstackified local: " << childBlockLocalId->number << childBlockLocalId->maybeName << std::endl;
      assert(false);
    }
  }

  auto childUnstackifiedParentLocalIds =
      getChildUnstackifiedParentLocalIds(parentBlockState, &childBlockState);
  for (auto childUnstackifiedParentLocalId : childUnstackifiedParentLocalIds) {
    parentBlockState->markLocalUnstackified(childUnstackifiedParentLocalId);
  }

  return resultLE;
}
