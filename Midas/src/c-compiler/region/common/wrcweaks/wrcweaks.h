#ifndef REGION_COMMON_WRCWEAKS_WRCWEAKS_H_
#define REGION_COMMON_WRCWEAKS_WRCWEAKS_H_

#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <region/common/fatweaks/fatweaks.h>

class WrcWeaks {
public:
  LLVMValueRef weakStructPtrToWrciWeakInterfacePtr(
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