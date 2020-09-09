#include <iostream>
#include "function/expressions/shared/controlblock.h"
#include "function/expressions/shared/string.h"

#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/heap.h"

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
