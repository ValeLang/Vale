#ifndef REGION_IREGION_H_
#define REGION_IREGION_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include <function/expressions/shared/ref.h>
#include <metal/types.h>
#include <metal/ast.h>

class FunctionState;
class BlockState;

// When we load something from an array, for example an owning reference,
// we still need to alias it to a constraint reference. This wrapper serves
// as a reminder that we need to do that.
struct LoadResult {
public:
  explicit LoadResult(Ref ref) : ref(ref) {}

  // This method is used when we intended to move the result, so no transformation
  // or aliasing is needed.
  Ref move() { return ref; }

  // This is just a getter for the ref for the methods that actually implement the
  // aliasing. It should ONLY be used by them.
  Ref extractForAliasingInternals() { return ref; }

private:
  Ref ref;
};

class IRegion {
public:
  virtual ~IRegion() = default;

  virtual Ref allocate(
      Ref regionInstanceRef,
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* desiredStructMT,
      const std::vector<Ref>& memberRefs) = 0;

  virtual WrapperPtrLE lockWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref weakRefLE,
      bool weakRefKnownLive) = 0;

  virtual void alias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRef,
      Ref expr) = 0;

  virtual void dealias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef) = 0;

  virtual void storeMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      bool structRefKnownLive,
      int memberIndex,
      const std::string& memberName,
      Reference* newMemberRefMT,
      Ref newMemberRef) = 0;

  virtual Ref loadMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      bool structRefKnownLive,
      int memberIndex,
      Reference* expectedMemberType,
      Reference* targetMemberType,
      const std::string& memberName) = 0;

  virtual Ref upcastWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructReferend* sourceStructReferendM,
      Reference* sourceStructTypeM,
      InterfaceReferend* targetInterfaceReferendM,
      Reference* targetInterfaceTypeM) = 0;

  virtual Ref upcast(
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructMT,
      StructReferend* sourceStructReferendM,
      Ref sourceRefLE,

      Reference* targetInterfaceTypeM,
      InterfaceReferend* targetInterfaceReferendM) = 0;

  virtual Ref lockWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      bool thenResultIsNever,
      bool elseResultIsNever,
      Reference* resultOptTypeM,
      Reference* constraintRefM,
      Reference* sourceWeakRefMT,
      Ref sourceWeakRefLE,
      bool weakRefKnownLive,
      std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
      std::function<Ref(LLVMBuilderRef)> buildElse) = 0;

  virtual Ref constructKnownSizeArray(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      KnownSizeArrayT* referendM) = 0;

  virtual Ref getUnknownSizeArrayLength(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      Ref arrayRef,
      bool arrayRefKnownLive) = 0;

  virtual LLVMValueRef checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref ref) = 0;

  virtual LLVMValueRef getCensusObjectId(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref ref) = 0;

  virtual LLVMTypeRef translateType(Reference* referenceM) = 0;

  virtual std::string getMemberArbitraryRefNameCSeeMMEDT(
      Reference* refMT) = 0;
  virtual void generateStructDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, StructDefinition* refMT) = 0;
  virtual void generateInterfaceDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, InterfaceDefinition* refMT) = 0;
  virtual void generateKnownSizeArrayDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, KnownSizeArrayDefinitionT* ksaDefM) = 0;
  virtual void generateUnknownSizeArrayDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, UnknownSizeArrayDefinitionT* usaDefM) = 0;

  virtual void declareStruct(StructDefinition* structM) = 0;
  virtual void declareStructExtraFunctions(StructDefinition* structM) = 0;
  virtual void defineStruct(StructDefinition* structM) = 0;
  virtual void defineStructExtraFunctions(StructDefinition* structM) = 0;

  virtual void declareInterface(InterfaceDefinition* interfaceM) = 0;
  virtual void declareInterfaceExtraFunctions(InterfaceDefinition* structM) = 0;
  virtual void defineInterface(InterfaceDefinition* interfaceM) = 0;
  virtual void defineInterfaceExtraFunctions(InterfaceDefinition* structM) = 0;

  virtual void declareKnownSizeArray(KnownSizeArrayDefinitionT* knownSizeArrayDefinitionMT) = 0;
  virtual void declareKnownSizeArrayExtraFunctions(KnownSizeArrayDefinitionT* structM) = 0;
  virtual void defineKnownSizeArray(KnownSizeArrayDefinitionT* knownSizeArrayDefinitionMT) = 0;
  virtual void defineKnownSizeArrayExtraFunctions(KnownSizeArrayDefinitionT* structM) = 0;

  virtual void declareUnknownSizeArray(UnknownSizeArrayDefinitionT* unknownSizeArrayDefinitionMT) = 0;
  virtual void declareUnknownSizeArrayExtraFunctions(UnknownSizeArrayDefinitionT* structM) = 0;
  virtual void defineUnknownSizeArray(UnknownSizeArrayDefinitionT* usaDefM) = 0;
  virtual void defineUnknownSizeArrayExtraFunctions(UnknownSizeArrayDefinitionT* structM) = 0;

  virtual void declareEdge(Edge* edge) = 0;
  virtual void defineEdge(Edge* edge) = 0;

  virtual void declareExtraFunctions() = 0;
  virtual void defineExtraFunctions() = 0;


  virtual Ref weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) = 0;

  virtual void discardOwningRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef) = 0;

  virtual void noteWeakableDestroyed(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtrLE) = 0;

  // Gets the itable PTR and the new value that we should put into the virtual param's slot
  // (such as a void* or a weak void ref)
  virtual std::tuple<LLVMValueRef, LLVMValueRef> explodeInterfaceRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      Ref virtualArgRef) = 0;

  virtual LLVMValueRef getInterfaceMethodFunctionPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      Ref virtualArgRef,
      int indexInEdge) = 0;

  virtual void aliasWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefMT,
      Ref weakRef) = 0;

  virtual void discardWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefMT,
      Ref weakRef) = 0;

  virtual Ref getIsAliveFromWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef,
      bool knownLive) = 0;

  virtual LoadResult loadElementFromUSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef) = 0;

  virtual void deallocate(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref ref) = 0;


  virtual Ref constructUnknownSizeArray(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaMT,
      UnknownSizeArrayT* unknownSizeArrayT,
      Ref sizeRef,
      const std::string& typeName) = 0;

  virtual void initializeElementInUSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef,
      Ref elementRef) = 0;

  virtual Ref deinitializeElementFromUSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef) = 0;

  virtual void initializeElementInKSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef,
      Ref elementRef) = 0;

  virtual Ref deinitializeElementFromKSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef) = 0;

  virtual Ref storeElementInUSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef,
      Ref elementRef) = 0;

  virtual void checkInlineStructType(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref ref) = 0;

  virtual Ref upgradeLoadResultToRefWithTargetOwnership(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceType,
      Reference* targetType,
      LoadResult sourceRef) = 0;

  // For instance regions, this will return the handle's type.
  // For value regions, we'll just be returning linear's translateType.
  virtual Reference* getExternalType(Reference* refMT) = 0;

  virtual LoadResult loadElementFromKSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      bool arrayRefKnownLive,
      Ref indexRef) = 0;

  // Receives a regular reference to an object in another region, so we can move
  // (or copy) it.
  virtual Ref receiveUnencryptedAlienReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRefMT,
      Reference* targetRefMT,
      Ref sourceRef) = 0;

  // Receives and decrypts a reference to an object in this region.
  virtual Ref receiveAndDecryptFamiliarReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRefMT,
      Ref sourceRef) = 0;

  // Encrypts and sends a reference to an object in this region.
  virtual Ref encryptAndSendFamiliarReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRefMT,
      Ref sourceRef) = 0;

  virtual LLVMValueRef getStringBytesPtr(
      FunctionState* functionState, LLVMBuilderRef builder, Ref ref) = 0;
  virtual LLVMValueRef getStringLen(
      FunctionState* functionState, LLVMBuilderRef builder, Ref ref) = 0;
  // TODO:
  // One use is for makeNewStrFunc, make that private to the unsafe region.
  // Change this to also take in the bytes pointer.
  virtual Ref mallocStr(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef lengthLE,
      LLVMValueRef sourceCharsPtrLE) = 0;

  virtual LLVMTypeRef getInterfaceMethodVirtualParamAnyType(
      Reference* reference) = 0;

  virtual RegionId* getRegionId() = 0;

  virtual Weakability getReferendWeakability(Referend* referend) = 0;

  virtual LLVMValueRef stackify(
      FunctionState* functionState, LLVMBuilderRef builder, Local* local, Ref refToStore,
      bool knownLive) = 0;

  virtual Ref unstackify(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) = 0;

  virtual Ref loadLocal(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) = 0;

  virtual Ref localStore(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore, bool knownLive) = 0;

  virtual void mainSetup(FunctionState* functionState, LLVMBuilderRef builder) = 0;
  virtual void mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) = 0;
};

#endif
