#ifndef REGION_COMMON_LGTWEAKS_LGTWEAKS_H_
#define REGION_COMMON_LGTWEAKS_LGTWEAKS_H_

#include <llvm-c/Types.h>
#include <metal/types.h>
#include <globalstate.h>
#include <function/function.h>
#include <region/common/fatweaks/fatweaks.h>

class LgtWeaks {
public:
  LLVMValueRef weakStructPtrToLgtiWeakInterfacePtr(
      GlobalState *globalState,
      FunctionState *functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructReferend *sourceStructReferendM,
      Reference *sourceStructTypeM,
      InterfaceReferend *targetInterfaceReferendM,
      Reference *targetInterfaceTypeM);

private:
  FatWeaks fatWeaks_;
};

#endif