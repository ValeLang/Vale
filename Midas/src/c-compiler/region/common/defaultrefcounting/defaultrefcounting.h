#ifndef REGION_COMMON_DEFAULTIMMUTABLES_DEFAULTIMMUTABLES_H_
#define REGION_COMMON_DEFAULTIMMUTABLES_DEFAULTIMMUTABLES_H_

#include <llvm-c/Types.h>
#include <globalstate.h>
#include <iostream>
#include <region/common/primitives.h>
#include <function/expressions/shared/afl.h>
#include <function/function.h>

class DefaultRefCounting {
public:
  void discard(
      AreaAndFileAndLine from,
      GlobalState* globalState,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef);

  LLVMTypeRef translateType(GlobalState* globalState, Reference* referenceM);

private:
  DefaultPrimitives primitives;
};

#endif