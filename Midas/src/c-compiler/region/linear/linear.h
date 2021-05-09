#ifndef REGION_COMMON_LINEAR_LINEAR_H_
#define REGION_COMMON_LINEAR_LINEAR_H_

#include <llvm-c/Types.h>
#include <globalstate.h>
#include <iostream>
#include <region/common/primitives.h>
#include <function/expressions/shared/afl.h>
#include <function/function.h>
#include <region/common/defaultlayout/structs.h>
#include "linearstructs.h"

class Linear : public IRegion {
public:
  Linear(GlobalState* globalState_);


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
      StructReferend* sourceStructReferendM,
      Reference* sourceStructTypeM,
      InterfaceReferend* targetInterfaceReferendM,
      Reference* targetInterfaceTypeM) override;

  void declareKnownSizeArray(KnownSizeArrayDefinitionT* ksaDefM) override;
  void defineKnownSizeArray(KnownSizeArrayDefinitionT* ksaDefM) override;
  void declareKnownSizeArrayExtraFunctions(KnownSizeArrayDefinitionT* ksaDef) override;
  void defineKnownSizeArrayExtraFunctions(KnownSizeArrayDefinitionT* ksaDef) override;

  void declareUnknownSizeArray(UnknownSizeArrayDefinitionT* usaDefM) override;
  void declareUnknownSizeArrayExtraFunctions(UnknownSizeArrayDefinitionT* usaDefM) override;
  void defineUnknownSizeArray(UnknownSizeArrayDefinitionT* usaDefM) override;
  void defineUnknownSizeArrayExtraFunctions(UnknownSizeArrayDefinitionT* usaDefM) override;

  void declareStruct(StructDefinition* structDefM) override;
  void declareStructExtraFunctions(StructDefinition* structDefM) override;
  void defineStruct(StructDefinition* structDefM) override;
  void defineStructExtraFunctions(StructDefinition* structDefM) override;

  void declareInterface(InterfaceDefinition* interfaceDefM) override;
  void declareInterfaceExtraFunctions(InterfaceDefinition* structDefM) override;
  void defineInterface(InterfaceDefinition* interfaceDefM) override;
  void defineInterfaceExtraFunctions(InterfaceDefinition* structDefM) override;

  void declareEdge(Edge* edge) override;
  void defineEdge(Edge* edge) override;

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
      StructReferend* sourceStructReferendM,
      Ref sourceRefLE,

      Reference* targetInterfaceTypeM,
      InterfaceReferend* targetInterfaceReferendM) override;

  WrapperPtrLE lockWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref weakRefLE,
      bool weakRefKnownLive) override;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  Ref constructKnownSizeArray(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      KnownSizeArrayT* referendM) override;

  // should expose a dereference thing instead
//  LLVMValueRef getKnownSizeArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef knownSizeArrayWrapperPtrLE) override;
//  LLVMValueRef getUnknownSizeArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef unknownSizeArrayWrapperPtrLE) override;

  Ref getUnknownSizeArrayLength(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
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

  LoadResult loadElementFromKSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      bool arrayKnownLive,
      Ref indexRef) override;
  LoadResult loadElementFromUSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      bool arrayKnownLive,
      Ref indexRef) override;


  Ref storeElementInUSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
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


  Ref constructUnknownSizeArray(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaMT,
      UnknownSizeArrayT* unknownSizeArrayT,
      Ref sizeRef,
      const std::string& typeName) override;

  void initializeElementInUSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef,
      Ref elementRef) override;

  Ref deinitializeElementFromUSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef) override;

  void initializeElementInKSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef,
      Ref elementRef) override;

  Ref deinitializeElementFromKSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef) override;

  Ref mallocStr(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef lengthLE,
      LLVMValueRef sourceCharsPtrLE) override;

  Ref innerMallocStr(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef lengthLE,
      LLVMValueRef sourceCharsPtrLE,
      Ref dryRunBoolRef);

  Ref innerConstructKnownSizeArray(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      KnownSizeArrayT* referendM,
      Ref dryRunBoolRef);

  LLVMValueRef getStringLen(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) override;

  std::string getMemberArbitraryRefNameCSeeMMEDT(
      Reference* refMT) override;
  void generateStructDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, StructDefinition* refMT) override;
  void generateInterfaceDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, InterfaceDefinition* refMT) override;
  void generateKnownSizeArrayDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, KnownSizeArrayDefinitionT* ksaDefM) override;
  void generateUnknownSizeArrayDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, UnknownSizeArrayDefinitionT* usaDefM) override;


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

  LoadResult loadMember(
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

  LLVMTypeRef getInterfaceMethodVirtualParamAnyType(Reference* reference) override;

  RegionId* getRegionId() override;

  void declareExtraFunctions() override;
  void defineExtraFunctions() override;

  // Temporary, gets the corresponding Linear type reference.
  Referend* linearizeReferend(Referend* referendMT);
  KnownSizeArrayT* unlinearizeKSA(KnownSizeArrayT* referendMT);
  StructReferend* unlinearizeStructReferend(StructReferend* referendMT);
  InterfaceReferend* unlinearizeInterfaceReferend(InterfaceReferend* referendMT);
  StructReferend* linearizeStructReferend(StructReferend* referendMT);
  InterfaceReferend* linearizeInterfaceReferend(InterfaceReferend* referendMT);
  Reference* linearizeReference(Reference* immRcRefMT);
  Reference* unlinearizeReference(Reference* hostRefMT);

  Weakability getReferendWeakability(Referend* referend) override;

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

  Ref localStore(
      FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore, bool knownLive) override;

  void mainSetup(FunctionState* functionState, LLVMBuilderRef builder) override {}
  void mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) override {}

private:
  void declareConcreteSerializeFunction(Referend* valeReferendM);
  void defineConcreteSerializeFunction(Referend* valeReferendM);
  void declareInterfaceSerializeFunction(InterfaceReferend* valeReferend);
  void defineEdgeSerializeFunction(Edge* edge);


  Ref innerConstructUnknownSizeArray(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaMT,
      UnknownSizeArrayT* unknownSizeArrayT,
      Ref sizeRef,
      const std::string& typeName,
      Ref dryRunBoolRef);

  Prototype* getSerializePrototype(Referend* valeReferend);
  Prototype* getSerializeThunkPrototype(StructReferend* structReferend, InterfaceReferend* interfaceReferend);

  LLVMValueRef predictShallowSize(
      LLVMBuilderRef builder,
      Referend* referend,
      // Ignored if referend isn't an array or string.
      // If it's a string, this will be the length of the string.
      // If it's an array, this will be the number of elements.
      LLVMValueRef lenIntLE);

  Ref innerAllocate(
      Ref regionInstanceRef,
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* desiredStructMT);

  InterfaceMethod* getSerializeInterfaceMethod(Referend* valeReferend);

  Ref callSerialize(
      FunctionState *functionState,
      LLVMBuilderRef builder,
      Referend* valeReferend,
      Ref regionInstanceRef,
      Ref objectRef,
      Ref dryRunBoolRef);

  // Does the entire serialization process: measuring the length, allocating a buffer, and
  // serializing into it.
  Ref topLevelSerialize(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* valeReferend,
      Ref ref);

  void bumpDestinationOffset(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      LLVMValueRef sizeIntLE);

  void reserveRootMetadataBytesIfNeeded(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef);

  LLVMValueRef getDestinationPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef);

  Ref getDestinationRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* desiredRefMT);

  LLVMValueRef getDestinationOffset(
      LLVMBuilderRef builder,
      LLVMValueRef regionInstancePtrLE);

  void addMappedReferend(Referend* valeReferend, Referend* hostReferend) {
    hostReferendByValeReferend.emplace(valeReferend, hostReferend);
    valeReferendByHostReferend.emplace(hostReferend, valeReferend);
  }

  GlobalState* globalState = nullptr;

  LinearStructs structs;

  std::unordered_map<
      Referend*,
      Referend*,
      AddressHasher<Referend*>> hostReferendByValeReferend;
  std::unordered_map<
      Referend*,
      Referend*,
      AddressHasher<Referend*>> valeReferendByHostReferend;

  std::string namePrefix = "__Linear";

  StructReferend* regionReferend = nullptr;
  Reference* regionRefMT = nullptr;

  StructReferend* startMetadataReferend = nullptr;
  Reference* startMetadataRefMT = nullptr;

  StructReferend* rootMetadataReferend = nullptr;
  Reference* rootMetadataRefMT = nullptr;

  Str* linearStr = nullptr;
  Reference* linearStrRefMT = nullptr;
};

#endif
