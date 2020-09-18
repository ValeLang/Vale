#ifndef REGION_COMMON_HGM_HGM_H_
#define REGION_COMMON_HGM_HGM_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include "globalstate.h"
#include "function/function.h"
#include <region/common/fatweaks/fatweaks.h>
#include <region/common/referendptrmaker.h>

class HybridGenerationalMemory {
public:
  HybridGenerationalMemory(
      GlobalState* globalState_,
      IReferendStructsSource* referendStructsSource_,
      IWeakRefStructsSource* weakRefStructsSource_);

  Ref assembleWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceType,
      Reference* targetType,
      Ref sourceRef);

  WeakFatPtrLE weakStructPtrToGenWeakInterfacePtr(
      GlobalState *globalState,
      FunctionState *functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructReferend *sourceStructReferendM,
      Reference *sourceStructTypeM,
      InterfaceReferend *targetInterfaceReferendM,
      Reference *targetInterfaceTypeM);

  // Makes a non-weak interface ref into a weak interface ref
  WeakFatPtrLE assembleInterfaceWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceType,
      Reference* targetType,
      InterfaceReferend* interfaceReferendM,
      InterfaceFatPtrLE sourceInterfaceFatPtrLE);

  WeakFatPtrLE assembleStructWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structTypeM,
      Reference* targetTypeM,
      StructReferend* structReferendM,
      WrapperPtrLE objPtrLE);

  WeakFatPtrLE assembleKnownSizeArrayWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceKSAMT,
      KnownSizeArrayT* knownSizeArrayMT,
      Reference* targetKSAWeakRefMT,
      WrapperPtrLE objPtrLE);

  WeakFatPtrLE assembleUnknownSizeArrayWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceType,
      UnknownSizeArrayT* unknownSizeArrayMT,
      Reference* targetUSAWeakRefMT,
      WrapperPtrLE sourceRefLE);

  LLVMValueRef lockGenFatPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      WeakFatPtrLE weakRefLE);

  void innerNoteWeakableDestroyed(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* concreteRefM,
      ControlBlockPtrLE controlBlockPtrLE);


  void aliasWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefMT,
      Ref weakRef);

  void discardWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefMT,
      Ref weakRef);

  LLVMValueRef getIsAliveFromWeakFatPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      WeakFatPtrLE weakFatPtrLE);

  Ref getIsAliveFromWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef);

  LLVMValueRef fillWeakableControlBlock(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* referendM,
      LLVMValueRef controlBlockLE);

  WeakFatPtrLE weakInterfaceRefToWeakStructRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakInterfaceRefMT,
      WeakFatPtrLE weakInterfaceFatPtrLE);

  void buildCheckWeakRef(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef);

  static LLVMTypeRef makeWeakRefHeaderStruct(GlobalState* globalState);

private:
  LLVMValueRef getTargetGenFromWeakRef(
      LLVMBuilderRef builder,
      WeakFatPtrLE weakRefLE);

  GlobalState* globalState;
  FatWeaks fatWeaks_;
  IReferendStructsSource* referendStructsSource;
  IWeakRefStructsSource* weakRefStructsSource;
};

#endif