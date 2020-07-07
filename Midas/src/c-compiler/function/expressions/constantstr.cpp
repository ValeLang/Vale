#include <iostream>
#include "function/expressions/shared/controlblock.h"
#include "function/expressions/shared/string.h"

#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/heap.h"

LLVMValueRef translateConstantStr(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    LLVMBuilderRef builder,
    ConstantStr* constantStr) {
  return buildConstantVStr(globalState, builder, constantStr->value);
}
