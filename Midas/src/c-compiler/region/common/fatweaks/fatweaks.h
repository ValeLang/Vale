#ifndef REGION_COMMON_FATWEAKS_FATWEAKS_H_
#define REGION_COMMON_FATWEAKS_FATWEAKS_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include <function/expressions/shared/ref.h>
#include "globalstate.h"
#include "function/function.h"

class FatWeaks {
public:
  LLVMValueRef getInnerRefFromWeakRef(
      GlobalState *globalState,
      FunctionState *functionState,
      LLVMBuilderRef builder,
      Reference *weakRefM,
      WeakFatPtrLE weakRefLE);

  LLVMValueRef getInnerRefFromWeakRefWithoutCheck(
      GlobalState *globalState,
      FunctionState *functionState,
      LLVMBuilderRef builder,
      Reference *weakRefM,
      WeakFatPtrLE weakRefLE);
};

#endif
