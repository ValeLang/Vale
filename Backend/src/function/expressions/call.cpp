#include <iostream>
#include "shared/shared.h"

#include "../../translatetype.h"

#include "../expression.h"


Ref translateCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Call* call) {
  auto argsLE = std::vector<Ref>{};
  argsLE.reserve(call->argExprs.size());
  for (int i = 0; i < call->argExprs.size(); i++) {
    auto argLE = translateExpression(globalState, functionState, blockState, builder, call->argExprs[i]);
    buildFlare(FL(), globalState, functionState, builder);
    globalState->getRegion(call->function->params[i])->checkValidReference(FL(), functionState, builder, call->function->params[i], argLE);
    argsLE.push_back(argLE);
  }

  return buildCallV(globalState, functionState, builder, call->function, argsLE);
}
