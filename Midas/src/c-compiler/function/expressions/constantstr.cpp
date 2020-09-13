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
  return wrap(functionState->defaultRegion, globalState->metalCache.strRef, strWrapperPtrLE);
}
