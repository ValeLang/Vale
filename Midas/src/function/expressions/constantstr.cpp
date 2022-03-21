#include <iostream>
#include "../../region/common/controlblock.h"
#include "shared/string.h"

#include "shared/shared.h"
#include "../../region/common/heap.h"

Ref translateConstantStr(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    ConstantStr* constantStr) {
  auto strRef =
      buildConstantVStr(globalState, functionState, builder, constantStr->value);
  // Dont need to alias here, see SRCAO
  return strRef;
}
