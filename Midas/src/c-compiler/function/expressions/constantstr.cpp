#include <iostream>
#include "region/common/controlblock.h"
#include "function/expressions/shared/string.h"

#include "function/expressions/shared/shared.h"
#include "region/common/heap.h"

Ref translateConstantStr(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    ConstantStr* constantStr) {
  auto strWrapperPtrLE =
      buildConstantVStr(globalState, functionState, builder, constantStr->value);
  auto resultRef = wrap(functionState->defaultRegion, globalState->metalCache.strRef, strWrapperPtrLE);
  functionState->defaultRegion->alias(FL(), functionState, builder, globalState->metalCache.strRef, resultRef);
  return resultRef;
}
