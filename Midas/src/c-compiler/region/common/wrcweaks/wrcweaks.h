#ifndef REGION_COMMON_WRCWEAKS_WRCWEAKS_H_
#define REGION_COMMON_WRCWEAKS_WRCWEAKS_H_

#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <region/common/fatweaks/fatweaks.h>

class WrcWeaks {
public:
  WrcWeaks(GlobalState* globalState, IReferendStructsSource* referendStructsSource, IWeakRefStructsSource* weakRefStructsSource);

  WeakFatPtrLE weakStructPtrToWrciWeakInterfacePtr(
      GlobalState *globalState,
      FunctionState *functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructReferend *sourceStructReferendM,
      Reference *sourceStructTypeM,
      InterfaceReferend *targetInterfaceReferendM,
      Reference *targetInterfaceTypeM);

  Ref assembleWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceType,
      Reference* targetType,
      Ref sourceRef);

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

  LLVMValueRef lockWrciFatPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      WeakFatPtrLE weakFatPtrLE);


  void innerNoteWeakableDestroyed(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* concreteRefM,
      ControlBlockPtrLE controlBlockPtrLE);

  LLVMValueRef getIsAliveFromWeakFatPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      WeakFatPtrLE weakFatPtrLE);

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

  Ref getIsAliveFromWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef);

  void buildCheckWeakRef(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef);

  LLVMValueRef getWrciFromWeakRef(
      LLVMBuilderRef builder,
      WeakFatPtrLE weakFatPtrLE);


  static LLVMTypeRef makeWeakRefHeaderStruct(GlobalState* globalState);


  void mainSetup(FunctionState* functionState, LLVMBuilderRef builder);
  void mainCleanup(FunctionState* functionState, LLVMBuilderRef builder);

private:
  void buildCheckWrc(
      LLVMBuilderRef builder,
      LLVMValueRef wrciLE);

  void maybeReleaseWrc(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef wrciLE,
      LLVMValueRef ptrToWrcLE,
      LLVMValueRef wrcLE);

  LLVMValueRef getNewWrci(
      FunctionState* functionState,
      LLVMBuilderRef builder);

  LLVMValueRef getWrcPtr(
      LLVMBuilderRef builder,
      LLVMValueRef wrciLE);

  LLVMValueRef getWrcCapacityPtr(LLVMBuilderRef builder);
  LLVMValueRef getWrcFirstFreeWrciPtr(LLVMBuilderRef builder);
  LLVMValueRef getWrcEntriesArrayPtr(LLVMBuilderRef builder);


  GlobalState* globalState = nullptr;
  FatWeaks fatWeaks_;
  IReferendStructsSource* referendStructsSource;
  IWeakRefStructsSource* weakRefStructsSource;

  LLVMValueRef wrcTablePtrLE = nullptr;
};

#endif