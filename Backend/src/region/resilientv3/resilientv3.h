#ifndef REGION_RESILIENTV3_RESILIENTV3_H_
#define REGION_RESILIENTV3_RESILIENTV3_H_

#include <llvm-c/Core.h>
#include "../../function/expressions/shared/afl.h"
#include "../rcimm/rcimm.h"
#include "../common/defaultlayout/structsrouter.h"
#include "../common/wrcweaks/wrcweaks.h"
#include "../common/lgtweaks/lgtweaks.h"
#include "../common/hgm/hgm.h"
#include "../../globalstate.h"
#include "../../function/function.h"
#include "../iregion.h"

class ResilientV3 : public IRegion {
public:
  ResilientV3(GlobalState* globalState, RegionId* regionId);
  ~ResilientV3() override = default;

  Ref allocate(
      Ref regionInstanceRef,
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* desiredStructMT,
      const std::vector<Ref>& memberRefs) override;

  void alias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRef,
      Ref expr) override;

  void dealias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef) override;

  Ref upcastWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructKind* sourceStructKindM,
      Reference* sourceStructTypeM,
      InterfaceKind* targetInterfaceKindM,
      Reference* targetInterfaceTypeM) override;

  Ref upcast(
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructMT,
      StructKind* sourceStructKindM,
      Ref sourceRefLE,

      Reference* targetInterfaceTypeM,
      InterfaceKind* targetInterfaceKindM) override;


  void defineStaticSizedArray(
      StaticSizedArrayDefinitionT* staticSizedArrayDefinitionMT) override;

  void declareStaticSizedArray(
      StaticSizedArrayDefinitionT* staticSizedArrayDefinitionMT) override;

  void declareRuntimeSizedArray(
      RuntimeSizedArrayDefinitionT* runtimeSizedArrayDefinitionMT) override;

  void defineRuntimeSizedArray(
      RuntimeSizedArrayDefinitionT* runtimeSizedArrayDefinitionMT) override;

  LiveRef checkRefLive(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* refMT,
      Ref ref,
      bool refKnownLive) override;

    LiveRef wrapToLiveRef(
        AreaAndFileAndLine checkerAFL,
        FunctionState* functionState,
        LLVMBuilderRef builder,
        Ref regionInstanceRef,
        Reference* refMT,
        LLVMValueRef ref) override;

  LiveRef preCheckBorrow(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* refMT,
      Ref ref,
      bool refKnownLive) override;

  WrapperPtrLE lockWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref weakRefLE,
      bool weakRefKnownLive) override;

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
      Ref sourceInterfaceRefLE,
      bool sourceRefKnownLive,
      Kind* targetKind,
      std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
      std::function<Ref(LLVMBuilderRef)> buildElse) override;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  LiveRef constructStaticSizedArray(
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

  LLVMValueRef getCensusObjectId(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref ref) override;

  Ref getRuntimeSizedArrayLength(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* rsaRefMT,
      LiveRef arrayRef) override;

  Ref getRuntimeSizedArrayCapacity(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* rsaRefMT,
      LiveRef arrayRef) override;

  LLVMValueRef checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      bool expectLive,
      Reference* refM,
      Ref ref) override;

  LLVMTypeRef translateType(Reference* referenceM) override;


  void declareEdge(
      Edge* edge) override;

  void defineEdge(
      Edge* edge) override;

  void defineStruct(
      StructDefinition* structM) override;

  void declareStruct(
      StructDefinition* structM) override;

  void defineInterface(
      InterfaceDefinition* interfaceM) override;


  void declareInterface(
      InterfaceDefinition* interfaceM) override;

  Ref weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) override;

  void discardOwningRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      LiveRef sourceRef) override;

  void noteWeakableDestroyed(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtrLE) override;

  void storeMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* structRefMT,
      LiveRef structRef,
      int memberIndex,
      const std::string& memberName,
      Reference* newMemberRefMT,
      Ref newMemberRef) override;

  Ref loadMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* structRefMT,
      LiveRef structLiveRef,
      int memberIndex,
      Reference* expectedMemberType,
      Reference* targetType,
      const std::string& memberName) override;


  // Gets the itable PTR and the new value that we should put into the virtual param's slot
  // (such as a void* or a weak void ref)
  std::tuple<LLVMValueRef, LLVMValueRef> explodeInterfaceRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      Ref virtualArgRef) override;


  // TODO maybe combine with alias/acquireReference?
  // After we load from a local, member, or element, we can feed the result through this
  // function to turn it into a desired ownership.
  // Example:
  // - Can load from an owning ref member to get a constraint ref.
  // - Can load from a constraint ref member to get a weak ref.
  Ref upgradeLoadResultToRefWithTargetOwnership(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* sourceType,
      Reference* targetType,
      LoadResult sourceRef,
      bool resultKnownLive) override;

  void checkInlineStructType(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref ref) override;

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

  LoadResult loadElementFromSSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* ssaRefMT,
      StaticSizedArrayT* ssaMT,
      LiveRef arrayRef,
      InBoundsLE indexLE) override;
  LoadResult loadElementFromRSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      LiveRef arrayRef,
      InBoundsLE indexLE) override;


  Ref storeElementInRSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      LiveRef arrayRef,
      InBoundsLE indexLE,
      Ref elementRef) override;


  void deallocate(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      LiveRef ref) override;


  LiveRef constructRuntimeSizedArray(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* rsaMT,
      RuntimeSizedArrayT* runtimeSizedArrayT,
      Ref capacityRef,
      const std::string& typeName) override;

  void pushRuntimeSizedArrayNoBoundsCheck(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      LiveRef arrayRef,
      InBoundsLE sizeLE,
      Ref elementRef) override;

  Ref popRuntimeSizedArrayNoBoundsCheck(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      LiveRef arrayRef,
      InBoundsLE indexLE) override;

  void initializeElementInSSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* ssaRefMT,
      StaticSizedArrayT* ssaMT,
      LiveRef arrayRef,
      InBoundsLE indexLE,
      Ref elementRef) override;

  Ref deinitializeElementFromSSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ssaRefMT,
      StaticSizedArrayT* ssaMT,
      LiveRef arrayRef,
      InBoundsLE indexLE) override;


  Ref mallocStr(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef lengthLE,
      LLVMValueRef sourceCharsPtrLE) override;

  RegionId* getRegionId() override;

  LLVMValueRef stackify(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Local* local,
      Ref refToStore,
      bool knownLive) override;

  Ref unstackify(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) override;

  Ref loadLocal(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) override;

  Ref localStore(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore, bool knownLive) override;

  LLVMValueRef getStringBytesPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref regionInstanceRef,
      LiveRef ref) override {
    assert(refMT->kind == globalState->metalCache->str);
    auto strWrapperPtrLE = kindStructs.makeWrapperPtr(FL(), functionState, builder, refMT, ref.refLE);
    return kindStructs.getStringBytesPtr(functionState, builder, strWrapperPtrLE);
  }

  LLVMValueRef getStringLen(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref regionInstanceRef,
      LiveRef ref) override {
    assert(refMT->kind == globalState->metalCache->str);
    auto strWrapperPtrLE = kindStructs.makeWrapperPtr(FL(), functionState, builder, refMT, ref.refLE);
    return kindStructs.getStringLen(functionState, builder, strWrapperPtrLE);
  }
//  LLVMTypeRef getWeakRefHeaderStruct(Kind* kind) override {
//    return kindStructs.getWeakRefHeaderStruct(kind);
//  }
//  LLVMTypeRef getWeakVoidRefStruct(Kind* kind) override {
//    return kindStructs.getWeakVoidRefStruct(kind);
//  }
  void fillControlBlock(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Kind* kindM,
      ControlBlockPtrLE controlBlockPtrLE,
      const std::string& typeName);



  std::string getExportName(Package* currentPackage, Reference* refMT, bool includeProjectName) override;
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

  LLVMTypeRef getExternalType(Reference* refMT) override;

  std::pair<Ref, Ref> receiveUnencryptedAlienReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref sourceRegionInstanceRef,
      Ref targetRegionInstanceRef,
      Reference* sourceRefMT,
      Reference* targetRefMT,
      Ref sourceRef) override;

  Ref receiveAndDecryptFamiliarReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRefMT,
      LLVMValueRef sourceRefLE) override;

  LLVMValueRef encryptAndSendFamiliarReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRefMT,
      Ref sourceRef) override;

  LLVMTypeRef getInterfaceMethodVirtualParamAnyType(Reference* reference) override;

  void defineStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* ssaDef) override {}
  void defineRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* rsaDefM) override {}
  void defineStructExtraFunctions(StructDefinition* structDefM) override {}
  void defineInterfaceExtraFunctions(InterfaceDefinition* structDefM) override {}

  void declareExtraFunctions() override {}
  void defineExtraFunctions() override {}

  Weakability getKindWeakability(Kind* kind) override;

  void declareStructExtraFunctions(StructDefinition* structDefM) override {}
  void declareStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* ssaDef) override {}
  void declareRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* rsaDefM) override {}
  void declareInterfaceExtraFunctions(InterfaceDefinition* structDefM) override {}

  ValeFuncPtrLE getInterfaceMethodFunctionPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      Ref virtualArgRef,
      int indexInEdge) override;

  void mainSetup(FunctionState* functionState, LLVMBuilderRef builder) override;
  void mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) override;

  Reference* getRegionRefType() override;

  // This is only temporarily virtual, while we're still creating fake ones on the fly.
  // Soon it'll be non-virtual, and parameters will differ by region.
  Ref createRegionInstanceLocal(FunctionState* functionState, LLVMBuilderRef builder) override;

  Ref mutabilify(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* refMT,
      Ref ref,
      Reference* targetRefMT) override;

  LiveRef immutabilify(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* refMT,
      Ref ref,
      Reference* targetRefMT) override;

protected:
  WrapperPtrLE getWrapperPtrNotLive(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* refM,
      Ref weakRefLE,
      bool weakRefKnownLive);

  WrapperPtrLE getWrapperPtrLive(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      LiveRef ref);

  GlobalState* globalState = nullptr;

  RegionId* regionId;

  KindStructs kindStructs;

  FatWeaks fatWeaks;
  HybridGenerationalMemory hgmWeaks;

  std::string namePrefix = "__ResilientV3";

  StructKind* regionKind = nullptr;
  Reference* regionRefMT = nullptr;
};

#endif
