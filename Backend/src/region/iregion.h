#ifndef REGION_IREGION_H_
#define REGION_IREGION_H_

#include <llvm-c/Core.h>
#include "../function/expressions/shared/afl.h"
#include "../function/expressions/shared/ref.h"
#include "../metal/types.h"
#include "../metal/ast.h"
#include "../function/expressions/shared/elements.h"

class FunctionState;
class BlockState;

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
      Ref regionInstanceRef,
      Reference* structRefMT,
      LiveRef structRef,
      int memberIndex,
      const std::string& memberName,
      Reference* newMemberRefMT,
      Ref newMemberRef) = 0;

  virtual Ref loadMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* structRefMT,
      LiveRef structRef,
      int memberIndex,
      Reference* expectedMemberType,
      Reference* targetMemberType,
      const std::string& memberName) = 0;

  virtual Ref upcastWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructKind* sourceStructKindM,
      Reference* sourceStructTypeM,
      InterfaceKind* targetInterfaceKindM,
      Reference* targetInterfaceTypeM) = 0;

  virtual Ref upcast(
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructMT,
      StructKind* sourceStructKindM,
      Ref sourceRefLE,

      Reference* targetInterfaceTypeM,
      InterfaceKind* targetInterfaceKindM) = 0;

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

  virtual Ref asSubtype(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* resultOptTypeM,
      Reference* sourceInterfaceRefMT,
      Ref sourceInterfaceRefLE,
      bool sourceRefKnownLive,
      Kind* targetKind,
      std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
      std::function<Ref(LLVMBuilderRef)> buildElse) = 0;

  virtual LiveRef constructStaticSizedArray(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      StaticSizedArrayT* kindM) = 0;

  virtual LiveRef checkRefLive(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* refMT,
      Ref ref,
      bool refKnownLive) = 0;

    virtual LiveRef wrapToLiveRef(
        AreaAndFileAndLine checkerAFL,
        FunctionState* functionState,
        LLVMBuilderRef builder,
        Ref regionInstanceRef,
        Reference* refMT,
        LLVMValueRef ref) = 0;

  virtual LiveRef preCheckBorrow(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* refMT,
      Ref ref,
      bool refKnownLive) = 0;

  virtual Ref mutabilify(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* refMT,
      Ref ref,
      Reference* targetRefMT) = 0;

  virtual LiveRef immutabilify(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* refMT,
      Ref ref,
      Reference* targetRefMT) = 0;

  virtual Ref getRuntimeSizedArrayLength(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* rsaRefMT,
      LiveRef arrayRef) = 0;

  virtual Ref getRuntimeSizedArrayCapacity(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* rsaRefMT,
      LiveRef arrayRef) = 0;

  virtual LLVMValueRef checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      bool expectLive,
      Reference* refM,
      Ref ref) = 0;

  LLVMValueRef checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      LiveRef liveRef) {
    auto ref = wrap(this, refM, liveRef.refLE);
    return checkValidReference(checkerAFL, functionState, builder, true, refM, ref);
  }

  virtual LLVMValueRef getCensusObjectId(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref ref) = 0;

  virtual LLVMTypeRef translateType(Reference* referenceM) = 0;


  virtual std::string getExportName(
      Package* package,
      Reference* reference,
      bool includeProjectName) = 0;

//  virtual std::string getMemberArbitraryRefNameCSeeMMEDT(
//      Reference* refMT) = 0;
  virtual std::string generateStructDefsC(
    Package* currentPackage,
      StructDefinition* refMT) = 0;
  virtual std::string generateInterfaceDefsC(
    Package* currentPackage,
      InterfaceDefinition* refMT) = 0;
  virtual std::string generateStaticSizedArrayDefsC(
    Package* currentPackage,
      StaticSizedArrayDefinitionT* ssaDefM) = 0;
  virtual std::string generateRuntimeSizedArrayDefsC(
    Package* currentPackage,
      RuntimeSizedArrayDefinitionT* rsaDefM) = 0;

  virtual void declareStruct(StructDefinition* structM) = 0;
  virtual void declareStructExtraFunctions(StructDefinition* structM) = 0;
  virtual void defineStruct(StructDefinition* structM) = 0;
  virtual void defineStructExtraFunctions(StructDefinition* structM) = 0;

  virtual void declareInterface(InterfaceDefinition* interfaceM) = 0;
  virtual void declareInterfaceExtraFunctions(InterfaceDefinition* structM) = 0;
  virtual void defineInterface(InterfaceDefinition* interfaceM) = 0;
  virtual void defineInterfaceExtraFunctions(InterfaceDefinition* structM) = 0;

  virtual void declareStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayDefinitionMT) = 0;
  virtual void declareStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* structM) = 0;
  virtual void defineStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayDefinitionMT) = 0;
  virtual void defineStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* structM) = 0;

  virtual void declareRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayDefinitionMT) = 0;
  virtual void declareRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* structM) = 0;
  virtual void defineRuntimeSizedArray(RuntimeSizedArrayDefinitionT* rsaDefM) = 0;
  virtual void defineRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* structM) = 0;

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
      LiveRef sourceRef) = 0;

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

  virtual ValeFuncPtrLE getInterfaceMethodFunctionPtr(
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

  virtual LoadResult loadElementFromRSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      LiveRef structRef,
      InBoundsLE indexLE) = 0;

  virtual void deallocate(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      LiveRef ref) = 0;


  virtual LiveRef constructRuntimeSizedArray(
      Ref regionInstanceRef,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* rsaMT,
      RuntimeSizedArrayT* runtimeSizedArrayT,
      Ref capacityRef,
      const std::string& typeName) = 0;

  virtual void pushRuntimeSizedArrayNoBoundsCheck(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      LiveRef arrayRef,
      InBoundsLE sizeLE,
      Ref elementRef) = 0;

  virtual Ref popRuntimeSizedArrayNoBoundsCheck(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      LiveRef arrayRef,
      InBoundsLE indexLE) = 0;

  virtual void initializeElementInSSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* ssaRefMT,
      StaticSizedArrayT* ssaMT,
      LiveRef structRef,
      InBoundsLE indexLE,
      Ref elementRef) = 0;

  virtual Ref deinitializeElementFromSSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ssaRefMT,
      StaticSizedArrayT* ssaMT,
      LiveRef structRef,
      InBoundsLE indexLE) = 0;

  virtual Ref storeElementInRSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* rsaRefMT,
      RuntimeSizedArrayT* rsaMT,
      LiveRef structRef,
      InBoundsLE indexLE,
      Ref elementRef) = 0;

  virtual void checkInlineStructType(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref ref) = 0;

  virtual Ref upgradeLoadResultToRefWithTargetOwnership(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* sourceType,
      Reference* targetType,
      LoadResult sourceRef,
      bool resultKnownLive) = 0;

  // For instance regions, this will return the handle's type.
  // For value regions, we'll just be returning linear's translateType.
  virtual LLVMTypeRef getExternalType(Reference* refMT) = 0;

  virtual LoadResult loadElementFromSSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref regionInstanceRef,
      Reference* ssaRefMT,
      StaticSizedArrayT* ssaMT,
      LiveRef structRef,
      InBoundsLE indexRef) = 0;

  // Receives a regular reference to an object in another region, so we can move
  // (or copy) it.
  virtual std::pair<Ref, Ref> receiveUnencryptedAlienReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref sourceRegionInstanceRef,
      Ref targetRegionInstanceRef,
      Reference* sourceRefMT,
      Reference* targetRefMT,
      Ref sourceRef) = 0;

  // Receives and decrypts a reference to an object in this region.
  virtual Ref receiveAndDecryptFamiliarReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRefMT,
      LLVMValueRef sourceRefLE) = 0;

  // Encrypts and sends a reference to an object in this region.
  virtual LLVMValueRef encryptAndSendFamiliarReference(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRefMT,
      Ref sourceRef) = 0;

  virtual LLVMValueRef getStringBytesPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref regionInstanceRef,
      LiveRef ref) = 0;
  virtual LLVMValueRef getStringLen(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref regionInstanceRef,
      LiveRef ref) = 0;
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

  virtual Weakability getKindWeakability(Kind* kind) = 0;

  virtual LLVMValueRef stackify(
      FunctionState* functionState, LLVMBuilderRef builder, Local* local, Ref refToStore,
      bool knownLive) = 0;

  virtual Ref unstackify(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) = 0;

  virtual Ref loadLocal(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) = 0;

  virtual Ref localStore(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore, bool knownLive) = 0;

  virtual void mainSetup(FunctionState* functionState, LLVMBuilderRef builder) = 0;
  virtual void mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) = 0;

  virtual Reference* getRegionRefType() = 0;

  // This is only temporarily virtual, while we're still creating fake ones on the fly.
  // Soon it'll be non-virtual, and parameters will differ by region.
  // Instead of making them on the fly, we'll need to get these regions from somewhere,
  // perhaps the FunctionState?
  virtual Ref createRegionInstanceLocal(FunctionState* functionState, LLVMBuilderRef builder) = 0;
};

LLVMValueRef checkValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool expectLive,
    Reference* refM,
    Ref ref);

LLVMValueRef checkValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool expectLive,
    Reference* refM,
    LiveRef liveRef);


#endif
