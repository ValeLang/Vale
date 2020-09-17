#ifndef REGION_COMMON_DEFAULTIMMUTABLES_DEFAULTIMMUTABLES_H_
#define REGION_COMMON_DEFAULTIMMUTABLES_DEFAULTIMMUTABLES_H_

#include <llvm-c/Types.h>
#include <globalstate.h>
#include <iostream>
#include <region/common/primitives.h>
#include <function/expressions/shared/afl.h>
#include <function/function.h>
#include <region/common/defaultlayout/structs.h>

ControlBlock makeImmControlBlock(GlobalState* globalState);

class DefaultImmutables {
public:
  DefaultImmutables(
      GlobalState* globalState_,
      ReferendStructs* wrappedStructs_);

  void discard(
      AreaAndFileAndLine from,
      GlobalState* globalState,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef);

  LLVMTypeRef translateType(GlobalState* globalState, Reference* referenceM);

  LLVMTypeRef getControlBlockStruct(Referend* referend);

  ControlBlock* getControlBlock(Referend* referend);

  // TODO get rid of these when refactor is done
  LLVMTypeRef getStringInnerStructPtrL() {
    return stringInnerStructL;
  }
  LLVMTypeRef getStringWrapperStructL() {
    return stringWrapperStructL;
  }

private:
  GlobalState* globalState;

  ReferendStructs* wrappedStructs;

  LLVMTypeRef stringWrapperStructL = nullptr;
  LLVMTypeRef stringInnerStructL = nullptr;

  DefaultPrimitives primitives;
  LLVMTypeRef stringInnerStructPtrLT = nullptr;

};

#endif