#ifndef REGION_ASSIST_ASSIST_H_
#define REGION_ASSIST_ASSIST_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include <region/resilientv3/resilientv3.h>
#include <region/common/fatweaks/fatweaks.h>
#include <region/common/primitives.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include <region/common/defaultlayout/structsrouter.h>
#include <region/rcimm/rcimm.h>
#include "globalstate.h"
#include "function/function.h"
#include "../iregion.h"

class Assist : public IRegion {
public:
  Assist(GlobalState* globalState);
  ~Assist() override = default;


  void alias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRef,
      Ref ref) override;

  void dealias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef) override;

  Ref lockWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      bool thenResultIsNever,
      bool elseResultIsNever,
      Reference* resultOptTypeM,
//      LLVMTypeRef resultOptTypeL,
      Reference* constraintRefM,
      Reference* sourceWeakRefMT,
      Ref sourceWeakRefLE,
      bool weakRefKnownLive,
      std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
      std::function<Ref(LLVMBuilderRef)> buildElse) override;


  Ref asSubtype(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* resultOptTypeM,
      Reference* sourceInterfaceRefMT,
      Ref sourceInterfaceRef,
      bool sourceRefKnownLive,
      Kind* targetKind,
      std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
      std::function<Ref(LLVMBuilderRef)> buildElse) override;

  LLVMTypeRef translateType(Reference* referenceM) override;

  LLVMValueRef getCensusObjectId(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref ref) override;

  Ref upcastWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructKind* sourceStructKindM,
      Reference* sourceStructTypeM,
      InterfaceKind* targetInterfaceKindM,
      Reference* targetInterfaceTypeM) override;

  void declareStaticSizedArray(
      StaticSizedArrayDefinitionT* staticSizedArrayDefinitionMT) override;

  void declareRuntimeSizedArray(
      RuntimeSizedArrayDefinitionT* runtimeSizedArrayDefinitionMT) override;

  void defineRuntimeSizedArray(
      RuntimeSizedArrayDefinitionT* runtimeSizedArrayDefinitionMT) override;

  void defineStaticSizedArray(
      StaticSizedArrayDefinitionT* staticSizedArrayDefinitionMT) override;

  void declareStruct(
      StructDefinition* structM) override;

  void defineStruct(
      StructDefinition* structM) override;

  void declareEdge(
      Edge* edge) override;

  void defineEdge(
      Edge* edge) override;

  void declareInterface(
      InterfaceDefinition* interfaceM) override;

  void defineInterface(
      InterfaceDefinition* interfaceM) override;

  Ref weakAlias(
      FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) override;

  void discardOwningRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef) override;


  void noteWeakableDestroyed(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtrLE) override;

  Ref loadMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      bool structKnownLive,
      int memberIndex,
      Reference* expectedMemberType,
      Reference* targetMemberType,
      const std::string& memberName) override;

  void storeMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      bool structKnownLive,
      int memberIndex,
      const std::string& memberName,
      Reference* newMemberRefMT,
      Ref newMemberRef) override;

  std::tuple<LLVMValueRef, LLVMValueRef> explodeInterfaceRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      Ref virtualArgRef) override;


  void aliasWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefMT,
      Ref weakRef) override;

  void discardWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefMT,
      Ref weakRef) override;

  Ref getIsAliveFromWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef,
      bool knownLive) override;

  LLVMValueRef getStringBytesPtr(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) override;

  Ref allocate(
      Ref regionInstanceRef,
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* desiredStructMT,
      const std::vector<Ref>& memberRefs) override;

  Ref upcast(
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructMT,
      StructKind* sourceStructKindM,
      Ref sourceRefLE,

      Reference* targetInterfaceTypeM,
      InterfaceKind* targetInterfaceKindM) override;

  WrapperPtrLE lockWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref weakRefLE,
      bool weakRefKnownLive) override;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  Ref constructStaticSizedArray(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      StaticSizedArrayT* kindM) override;

  // should expose a dereference thing instead
//  LLVMValueRef getStaticSizedArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef staticSizedArrayWrapperPtrLE) override;
//  LLVMValueRef getRuntimeSizedArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef runtimeSizedArrayWrapperPtrLE) override;

  Ref getRuntimeSizedArrayLength(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* rsaRefMT,
      Ref arrayRef,
      bool arrayKnownLive) override;

  LLVMValueRef checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref ref) override;


  // TODO maybe combine with alias/acquireReference?
  // After we load from a local, member, or element, we can feed the result through this
  // function to turn it into a desired ownership.
  // Example:
  // - Can load from an owning ref member to get a constraint ref.
  // - Can load from a constraint ref member to get a weak ref.
  Ref upgradeLoadResultToRefWithTargetOwnership(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceType,
      Reference* targetType,
      LoadResult sourceRef) override;

  void checkInlineStructType(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref ref) override;

  LoadResult loadElementFromSSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ssaRefMT,
      StaticSizedArrayT* ssaMT,
      Ref arrayRef,
      bool arrayKnownLive,
      Ref indexRef) override;
  LoadResult loadElementFromRSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      Ref arrayRef,
      bool arrayKnownLive,
      Ref indexRef) override;


  Ref storeElementInRSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      Ref arrayRef,
      bool arrayKnownLive,
      Ref indexRef,
      Ref elementRef) override;


  void deallocate(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref ref) override;


  Ref constructRuntimeSizedArray(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* rsaMT,
      RuntimeSizedArrayT* runtimeSizedArrayT,
      Ref sizeRef,
      const std::string& typeName) override;

  void initializeElementInRSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef,
      Ref elementRef) override;

  Ref deinitializeElementFromRSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef) override;

  void initializeElementInSSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ssaRefMT,
      StaticSizedArrayT* ssaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef,
      Ref elementRef) override;

  Ref deinitializeElementFromSSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ssaRefMT,
      StaticSizedArrayT* ssaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef) override;


  Ref mallocStr(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef lengthLE,
      LLVMValueRef sourceCharsPtrLE) override;

  RegionId* getRegionId() override;

//  LLVMValueRef mallocKnownSize(
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Location location,
//      LLVMTypeRef kindLT) override;

//  LLVMValueRef mallocRuntimeSizedArray(
//      LLVMBuilderRef builder,
//      LLVMTypeRef rsaWrapperLT,
//      LLVMTypeRef rsaElementLT,
//      LLVMValueRef lengthLE) override;

  // TODO Make these private once refactor is done
//  WeakFatPtrLE makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) override {
//    return mutWeakableStructs.makeWeakFatPtr(referenceM_, ptrLE);
//  }
  // TODO get rid of these once refactor is done
//  ControlBlock* getControlBlock(Kind* kind) override {
//    return kindStructs.getControlBlock(kind);
//  }
//  KindStructs* getKindStructsSource() override {
//    return &kindStructs;
//  }
//  KindStructs* getWeakRefStructsSource() override {
//    return &weakRefStructs;
//  }
  LLVMValueRef getStringLen(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) override {
    auto strWrapperPtrLE =
        kindStructs.makeWrapperPtr(
            FL(), functionState, builder,
            globalState->metalCache->strRef,
            checkValidReference(
                FL(), functionState, builder, globalState->metalCache->strRef, ref));
    return kindStructs.getStringLen(functionState, builder, strWrapperPtrLE);
  }
//  LLVMTypeRef getWeakRefHeaderStruct(Kind* kind) override {
//    return mutWeakableStructs.getWeakRefHeaderStruct(kind);
//  }
//  LLVMTypeRef getWeakVoidRefStruct(Kind* kind) override {
//    return mutWeakableStructs.getWeakVoidRefStruct(kind);
//  }
  void fillControlBlock(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Kind* kindM,
      ControlBlockPtrLE controlBlockPtrLE,
      const std::string& typeName);


  std::string getExportName(
      Package* package,
      Reference* reference,
      bool includeProjectName) override;
  std::string generateStructDefsC(
    Package* currentPackage,
      StructDefinition* refMT) override;
  std::string generateInterfaceDefsC(
    Package* currentPackage,
      InterfaceDefinition* refMT) override;
  std::string generateStaticSizedArrayDefsC(
    Package* currentPackage,
      StaticSizedArrayDefinitionT* ssaDefM) override;
  std::string generateRuntimeSizedArrayDefsC(
    Package* currentPackage,
      RuntimeSizedArrayDefinitionT* rsaDefM) override;


  Reference* getExternalType(
      Reference* refMT) override;

  Ref receiveUnencryptedAlienReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRefMT,
      Reference* targetRefMT,
      Ref sourceRef) override;

  Ref receiveAndDecryptFamiliarReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRefMT,
      Ref sourceRef) override;

  Ref encryptAndSendFamiliarReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRefMT,
      Ref sourceRef) override;

  LLVMTypeRef getInterfaceMethodVirtualParamAnyType(Reference* reference) override;

  void defineStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* ssaDef) override {}
  void defineRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* rsaDefM) override {}
  void defineStructExtraFunctions(StructDefinition* structDefM) override {}
  void defineInterfaceExtraFunctions(InterfaceDefinition* structDefM) override {}
  void declareStructExtraFunctions(StructDefinition* structDefM) override {}
  void declareStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* ssaDef) override {}
  void declareRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* rsaDefM) override {}
  void declareInterfaceExtraFunctions(InterfaceDefinition* structDefM) override {}

  void declareExtraFunctions() override {}
  void defineExtraFunctions() override {}

  Weakability getKindWeakability(Kind* kind) override;

  LLVMValueRef getInterfaceMethodFunctionPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      Ref virtualArgRef,
      int indexInEdge) override;

  LLVMValueRef stackify(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Local* local,
      Ref refToStore,
      bool knownLive) override;

  Ref unstackify(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) override;

  Ref loadLocal(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) override;

  Ref localStore(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore, bool knownLive) override;

  void mainSetup(FunctionState* functionState, LLVMBuilderRef builder) override;
  void mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) override;

private:

  GlobalState* globalState = nullptr;

  LLVMTypeRef regionLT;

//  KindStructs mutNonWeakableStructs;
  KindStructs kindStructs;

//  KindStructsRouter kindStructs;
//  WeakRefStructsRouter weakRefStructs;

  FatWeaks fatWeaks;
  WrcWeaks wrcWeaks;
};

#endif
