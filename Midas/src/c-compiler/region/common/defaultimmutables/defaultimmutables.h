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


  Ref loadMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      int memberIndex,
      Reference* expectedMemberType,
      Reference* targetType,
      const std::string& memberName);

  void checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      IReferendStructsSource* referendStructs,
      Reference* refM,
      LLVMValueRef refLE);

private:
  GlobalState* globalState;

  ReferendStructs* referendStructs;

  DefaultPrimitives primitives;

};

#endif