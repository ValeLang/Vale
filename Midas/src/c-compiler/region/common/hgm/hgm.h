#ifndef REGION_COMMON_HGM_HGM_H_
#define REGION_COMMON_HGM_HGM_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include "globalstate.h"
#include "function/function.h"
#include <region/common/fatweaks/fatweaks.h>

class HybridGenerationalMemory {
public:
  LLVMValueRef weakStructPtrToGenWeakInterfacePtr(
      GlobalState *globalState,
      FunctionState *functionState,
      LLVMBuilderRef builder,
      LLVMValueRef sourceRefLE,
      StructReferend *sourceStructReferendM,
      Reference *sourceStructTypeM,
      InterfaceReferend *targetInterfaceReferendM,
      Reference *targetInterfaceTypeM);

private:
  FatWeaks fatWeaks_;
};

#endif