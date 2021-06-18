#ifndef REGION_COMMON_FATWEAKS_FATWEAKS_H_
#define REGION_COMMON_FATWEAKS_FATWEAKS_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include <function/expressions/shared/ref.h>
#include "globalstate.h"
#include "function/function.h"

class FatWeaks {
public:
  FatWeaks(GlobalState* globalState_, KindStructs* weakRefStructsSource_)
  : globalState(globalState_),
  weakRefStructsSource(weakRefStructsSource_){}

  LLVMValueRef getInnerRefFromWeakRef(
      FunctionState *functionState,
      LLVMBuilderRef builder,
      Reference *weakRefM,
      WeakFatPtrLE weakRefLE);

  LLVMValueRef getInnerRefFromWeakRefWithoutCheck(
      FunctionState *functionState,
      LLVMBuilderRef builder,
      Reference *weakRefM,
      WeakFatPtrLE weakRefLE);

  WeakFatPtrLE assembleWeakFatPtr(
      FunctionState *functionState,
      LLVMBuilderRef builder,
      Reference* weakRefMT,
      LLVMTypeRef weakRefStruct,
      LLVMValueRef headerLE,
      LLVMValueRef innerRefLE);

  LLVMValueRef getHeaderFromWeakRef(
      LLVMBuilderRef builder,
      WeakFatPtrLE weakRefLE);

  WeakFatPtrLE assembleVoidStructWeakRef(
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtrLE,
      LLVMValueRef headerLE);

private:
  GlobalState* globalState = nullptr;
  KindStructs* weakRefStructsSource;
};

#endif
