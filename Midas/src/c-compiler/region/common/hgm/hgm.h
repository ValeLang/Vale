#ifndef REGION_COMMON_HGM_HGM_H_
#define REGION_COMMON_HGM_HGM_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include "globalstate.h"
#include "function/function.h"
#include <region/common/fatweaks/fatweaks.h>

class HybridGenerationalMemory {
public:
  HybridGenerationalMemory(
      GlobalState* globalState_,
      KindStructs* kindStructs_,
      bool elideChecksForKnownLive_,
      bool limitMode_,
      StructKind* anyMT);

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

  WeakFatPtrLE assembleInterfaceWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* targetType,
      InterfaceKind* interfaceKindM,
      LLVMValueRef currentGenLE,
      InterfaceFatPtrLE sourceInterfaceFatPtrLE);

  WeakFatPtrLE assembleStructWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structTypeM,
      Reference* targetTypeM,
      StructKind* structKindM,
      WrapperPtrLE objPtrLE);

  WeakFatPtrLE assembleStructWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* targetTypeM,
      StructKind* structKindM,
      LLVMValueRef currentGenLE,
      WrapperPtrLE objPtrLE);

  WeakFatPtrLE assembleStaticSizedArrayWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceSSAMT,
      StaticSizedArrayT* staticSizedArrayMT,
      Reference* targetSSAWeakRefMT,
      WrapperPtrLE objPtrLE);

  WeakFatPtrLE assembleStaticSizedArrayWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* targetTypeM,
      StaticSizedArrayT* staticSizedArrayMT,
      LLVMValueRef currentGenLE,
      WrapperPtrLE objPtrLE);

  WeakFatPtrLE assembleRuntimeSizedArrayWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceSSAMT,
      RuntimeSizedArrayT* staticSizedArrayMT,
      Reference* targetSSAWeakRefMT,
      WrapperPtrLE objPtrLE);

  WeakFatPtrLE assembleRuntimeSizedArrayWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* targetTypeM,
      RuntimeSizedArrayT* staticSizedArrayMT,
      LLVMValueRef currentGenLE,
      WrapperPtrLE objPtrLE);

  LLVMValueRef lockGenFatPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      WeakFatPtrLE weakRefLE,
      bool weakRefKnownLive);

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

  LLVMValueRef implodeConcreteHandle(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef);

  LLVMValueRef implodeInterfaceHandle(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef);

private:
  LLVMValueRef getTargetGenFromWeakRef(
      LLVMBuilderRef builder,
      KindStructs* weakRefStructsSource,
      Kind* kind,
      WeakFatPtrLE weakRefLE);

  Prototype* makeMainSetupFunction();

  GlobalState* globalState = nullptr;
//  ControlBlock* controlBlock = nullptr;
  FatWeaks fatWeaks;
  KindStructs* kindStructs;
//  KindStructs* weakRefStructsSource;

  bool elideChecksForKnownLive = false;

  StructKind* anyMT = nullptr;

  std::unordered_map<Kind*, LLVMValueRef, AddressHasher<Kind*>> globalNullPtrPtrByKind;
};

#endif