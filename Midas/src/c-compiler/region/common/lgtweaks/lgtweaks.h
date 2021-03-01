#ifndef REGION_COMMON_LGTWEAKS_LGTWEAKS_H_
#define REGION_COMMON_LGTWEAKS_LGTWEAKS_H_

#include <llvm-c/Types.h>
#include <metal/types.h>
#include <globalstate.h>
#include <function/function.h>
#include <region/common/fatweaks/fatweaks.h>

class LgtWeaks {
public:
  LgtWeaks(
      GlobalState* globalState,
      IReferendStructsSource* referendStructsSource,
      IWeakRefStructsSource* weakRefStructsSource,
      bool elideChecksForKnownLive);

  Ref assembleWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceType,
      Reference* targetType,
      Ref sourceRef);

  WeakFatPtrLE weakStructPtrToLgtiWeakInterfacePtr(
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

  LLVMValueRef lockLgtiFatPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      WeakFatPtrLE weakRefLE,
      bool knownLive);

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
      WeakFatPtrLE weakFatPtrLE,
      bool knownLive);

  Ref getIsAliveFromWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef,
      bool knownLive);

  LLVMValueRef fillWeakableControlBlock(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      IReferendStructsSource* structs,
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


  static LLVMTypeRef makeWeakRefHeaderStruct(GlobalState* globalState, RegionId* regionId);

private:
  LLVMValueRef getTargetGenFromWeakRef(
      LLVMBuilderRef builder,
      IWeakRefStructsSource* weakRefStructsSource,
      Referend* referend,
      WeakFatPtrLE weakRefLE);

  LLVMValueRef getLgtiFromWeakRef(
      LLVMBuilderRef builder,
      WeakFatPtrLE weakRefLE);

  void buildCheckLgti(
      LLVMBuilderRef builder,
      LLVMValueRef lgtiLE);

  LLVMValueRef getNewLgti(
      FunctionState* functionState,
      LLVMBuilderRef builder);

  LLVMValueRef getLGTEntryGenPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef lgtiLE);

  LLVMValueRef getLGTEntryNextFreePtr(
      LLVMBuilderRef builder,
      LLVMValueRef lgtiLE);

  LLVMValueRef getActualGenFromLGT(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef lgtiLE);

  GlobalState* globalState = nullptr;
  FatWeaks fatWeaks_;
  IReferendStructsSource* referendStructsSource;
  IWeakRefStructsSource* weakRefStructsSource;
  bool elideChecksForKnownLive;

  LLVMValueRef lgtTablePtrLE = nullptr;

  LLVMValueRef getLgtCapacityPtr(LLVMBuilderRef builder);
  LLVMValueRef getLgtFirstFreeLgtiPtr(LLVMBuilderRef builder);
  LLVMValueRef getLgtEntriesArrayPtr(LLVMBuilderRef builder);

};

#endif