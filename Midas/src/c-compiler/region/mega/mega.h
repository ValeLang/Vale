#ifndef REGION_MEGA_MEGA_H_
#define REGION_MEGA_MEGA_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include <region/rcimm/rcimm.h>
#include <region/common/defaultlayout/structsrouter.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include <region/common/lgtweaks/lgtweaks.h>
#include <region/common/hgm/hgm.h>
#include "globalstate.h"
#include "function/function.h"
#include "../iregion.h"

class Mega : public IRegion {
public:
  Mega(GlobalState* globalState, RegionId* regionId);
  ~Mega() override = default;

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
      StructReferend* sourceStructReferendM,
      Reference* sourceStructTypeM,
      InterfaceReferend* targetInterfaceReferendM,
      Reference* targetInterfaceTypeM) override;

  Ref upcast(
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructMT,
      StructReferend* sourceStructReferendM,
      Ref sourceRefLE,

      Reference* targetInterfaceTypeM,
      InterfaceReferend* targetInterfaceReferendM) override;


  void defineKnownSizeArray(
      KnownSizeArrayDefinitionT* knownSizeArrayDefinitionMT) override;

  void declareKnownSizeArray(
      KnownSizeArrayDefinitionT* knownSizeArrayDefinitionMT) override;

  void declareUnknownSizeArray(
      UnknownSizeArrayDefinitionT* unknownSizeArrayDefinitionMT) override;

  void defineUnknownSizeArray(
      UnknownSizeArrayDefinitionT* unknownSizeArrayDefinitionMT) override;

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

  LLVMValueRef getCensusObjectId(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref ref) override;

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
      Ref sourceRef) override;

  void noteWeakableDestroyed(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtrLE) override;

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

  Ref loadMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      bool structKnownLive,
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
      Reference* sourceType,
      Reference* targetType,
      LoadResult sourceRef) override;

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

  RegionId* getRegionId() override;

//  LLVMValueRef mallocKnownSize(
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Location location,
//      LLVMTypeRef referendLT) override;

//  LLVMValueRef mallocUnknownSizeArray(
//      LLVMBuilderRef builder,
//      LLVMTypeRef usaWrapperLT,
//      LLVMTypeRef usaElementLT,
//      LLVMValueRef lengthLE) override;

  // TODO Make these private once refactor is done
//  WeakFatPtrLE makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) override {
//    return mutWeakableStructs.makeWeakFatPtr(referenceM_, ptrLE);
//  }
  // TODO get rid of these once refactor is done
//  ControlBlock* getControlBlock(Referend* referend) override {
//    return referendStructs.getControlBlock(referend);
//  }
//  IReferendStructsSource* getReferendStructsSource() override {
//    return &referendStructs;
//  }
//  IWeakRefStructsSource* getWeakRefStructsSource() override {
//    return &weakRefStructs;
//  }
  LLVMValueRef getStringBytesPtr(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) override {
    auto strWrapperPtrLE =
        referendStructs.makeWrapperPtr(
            FL(), functionState, builder,
            globalState->metalCache->strRef,
            checkValidReference(
                FL(), functionState, builder, globalState->metalCache->strRef, ref));
    return referendStructs.getStringBytesPtr(functionState, builder, strWrapperPtrLE);
  }
  LLVMValueRef getStringLen(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) override {
    auto strWrapperPtrLE =
        referendStructs.makeWrapperPtr(
            FL(), functionState, builder,
            globalState->metalCache->strRef,
            checkValidReference(
                FL(), functionState, builder, globalState->metalCache->strRef, ref));
    return referendStructs.getStringLen(functionState, builder, strWrapperPtrLE);
  }
//  LLVMTypeRef getWeakRefHeaderStruct(Referend* referend) override {
//    return mutWeakableStructs.getWeakRefHeaderStruct(referend);
//  }
//  LLVMTypeRef getWeakVoidRefStruct(Referend* referend) override {
//    return mutWeakableStructs.getWeakVoidRefStruct(referend);
//  }
  void fillControlBlock(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* referendM,
      ControlBlockPtrLE controlBlockPtrLE,
      const std::string& typeName);


  std::string getRefNameC(
      Reference* refMT) override;
  void generateStructDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, StructDefinition* refMT) override;
  void generateInterfaceDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, InterfaceDefinition* refMT) override;
  void generateKnownSizeArrayDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, KnownSizeArrayDefinitionT* ksaDefM) override;
  void generateUnknownSizeArrayDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, UnknownSizeArrayDefinitionT* usaDefM) override;

  Reference* getExternalType(Reference* refMT) override;

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

  void defineKnownSizeArrayExtraFunctions(KnownSizeArrayDefinitionT* ksaDef) override {}
  void defineUnknownSizeArrayExtraFunctions(UnknownSizeArrayDefinitionT* usaDefM) override {}
  void defineStructExtraFunctions(StructDefinition* structDefM) override {}
  void defineInterfaceExtraFunctions(InterfaceDefinition* structDefM) override {}

  void declareExtraFunctions() override {}
  void defineExtraFunctions() override {}

  Weakability getReferendWeakability(Referend* referend) override;

  void declareStructExtraFunctions(StructDefinition* structDefM) override {}
  void declareKnownSizeArrayExtraFunctions(KnownSizeArrayDefinitionT* ksaDef) override {}
  void declareUnknownSizeArrayExtraFunctions(UnknownSizeArrayDefinitionT* usaDefM) override {}
  void declareInterfaceExtraFunctions(InterfaceDefinition* structDefM) override {}

  LLVMValueRef getInterfaceMethodFunctionPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      Ref virtualArgRef,
      int indexInEdge) override;

protected:
  GlobalState* globalState = nullptr;

  RegionId* regionId;

  ReferendStructs mutNonWeakableStructs;
  WeakableReferendStructs mutWeakableStructs;

  ReferendStructsRouter referendStructs;
  WeakRefStructsRouter weakRefStructs;

  FatWeaks fatWeaks;
  WrcWeaks wrcWeaks;
  LgtWeaks lgtWeaks;
  HybridGenerationalMemory hgmWeaks;

  // TODO see if we can just use referendStructs/weakRefStructs instead of having these?
//  WeakFatPtrLEMaker weakFatPtrMaker;
//  InterfaceFatPtrLEMaker interfaceFatPtrMaker;
//  ControlBlockPtrLEMaker controlBlockPtrMaker;
//  WrapperPtrLEMaker wrapperPtrMaker;
};

#endif
