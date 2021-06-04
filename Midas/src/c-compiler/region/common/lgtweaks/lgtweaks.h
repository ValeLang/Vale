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
      IKindStructsSource* kindStructsSource,
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
      StructKind *sourceStructKindM,
      Reference *sourceStructTypeM,
      InterfaceKind *targetInterfaceKindM,
      Reference *targetInterfaceTypeM);

  // Makes a non-weak interface ref into a weak interface ref
  WeakFatPtrLE assembleInterfaceWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceType,
      Reference* targetType,
      InterfaceKind* interfaceKindM,
      InterfaceFatPtrLE sourceInterfaceFatPtrLE);

  WeakFatPtrLE assembleStructWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structTypeM,
      Reference* targetTypeM,
      StructKind* structKindM,
      WrapperPtrLE objPtrLE);

  WeakFatPtrLE assembleStaticSizedArrayWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceSSAMT,
      StaticSizedArrayT* staticSizedArrayMT,
      Reference* targetSSAWeakRefMT,
      WrapperPtrLE objPtrLE);

  WeakFatPtrLE assembleRuntimeSizedArrayWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceType,
      RuntimeSizedArrayT* runtimeSizedArrayMT,
      Reference* targetRSAWeakRefMT,
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
      IKindStructsSource* structs,
      Kind* kindM,
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

  void mainSetup(FunctionState* functionState, LLVMBuilderRef builder);
  void mainCleanup(FunctionState* functionState, LLVMBuilderRef builder);

private:
  LLVMValueRef getTargetGenFromWeakRef(
      LLVMBuilderRef builder,
      IWeakRefStructsSource* weakRefStructsSource,
      Kind* kind,
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
  IKindStructsSource* kindStructsSource;
  IWeakRefStructsSource* weakRefStructsSource;
  bool elideChecksForKnownLive;

  LLVMValueRef lgtTablePtrLE = nullptr;

  LLVMValueRef getLgtCapacityPtr(LLVMBuilderRef builder);
  LLVMValueRef getLgtFirstFreeLgtiPtr(LLVMBuilderRef builder);
  LLVMValueRef getLgtEntriesArrayPtr(LLVMBuilderRef builder);

};

#endif