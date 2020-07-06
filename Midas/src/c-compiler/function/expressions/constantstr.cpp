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

  auto lengthLE = constI64LE(constantStr->value.length());

  auto strWrapperPtrLE = mallocStr(globalState, builder, lengthLE);

  std::vector<LLVMValueRef> argsLE = {
      getInnerStrPtrFromWrapperPtr(builder, strWrapperPtrLE),
      globalState->getOrMakeStringConstant(constantStr->value)
  };
  LLVMBuildCall(builder, globalState->initStr, argsLE.data(), argsLE.size(), "");

  return strWrapperPtrLE;
}
