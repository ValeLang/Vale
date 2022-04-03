#include <iostream>
#include "shared/shared.h"
#include "../../utils/branch.h"

#include "../../translatetype.h"

#include "../expression.h"

Ref translateWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* parentBlockState,
    LLVMBuilderRef builder,
    While* whiile) {
  buildBreakyWhile(
      globalState,
      functionState, builder,
      [globalState, functionState, parentBlockState, whiile](
          LLVMBuilderRef bodyBuilder, LLVMBasicBlockRef loopEnd) {
        BlockState childBlockState(
            parentBlockState->addressNumberer, parentBlockState, std::optional(loopEnd));
        auto resultRef =
            translateExpression(
                globalState, functionState, &childBlockState, bodyBuilder, whiile->bodyExpr);
        globalState->getRegion(globalState->metalCache->voidRef)
            ->checkValidReference(FL(), functionState, bodyBuilder, globalState->metalCache->voidRef, resultRef);
      });
  // Nobody should use a result of a while, so we'll just return a Never.
  return makeVoidRef(globalState);
}
