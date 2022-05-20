#ifndef REGION_COMMON_COMMON_H_
#define REGION_COMMON_COMMON_H_

#include "../../globalstate.h"
#include "../../function/function.h"
#include <llvm-c/Types.h>
#include "hgm/hgm.h"
#include "wrcweaks/wrcweaks.h"

LLVMValueRef weakStructPtrToGenWeakInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sourceRefLE,
    StructKind* sourceStructKindM,
    Reference* sourceStructTypeM,
    InterfaceKind* targetInterfaceKindM,
    Reference* targetInterfaceTypeM);

LLVMValueRef upcastThinPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* kindStructsSource,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructKind* sourceStructKindM,
    WrapperPtrLE sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceKind* targetInterfaceKindM);

LLVMTypeRef translateReferenceSimple(GlobalState* globalState, KindStructs* structs, Kind* kind);

LLVMTypeRef translateWeakReference(GlobalState* globalState, KindStructs* weakRefStructs, Kind* kind);



LoadResult loadInnerInnerStructMember(
    GlobalState* globalState,
  FunctionState* functionState,
    LLVMBuilderRef builder, LLVMValueRef innerStructPtrLE, int memberIndex, Reference* expectedType, std::string memberName);
void storeInnerInnerStructMember(
    LLVMBuilderRef builder, LLVMValueRef innerStructPtrLE, int memberIndex, std::string memberName, LLVMValueRef newValueLE);


LLVMValueRef getItablePtrFromInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE);


LLVMValueRef fillControlBlockCensusFields(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* structs,
    LLVMBuilderRef builder,
    Kind* kindM,
    LLVMValueRef newControlBlockLE,
    const std::string& typeName);

LLVMValueRef insertStrongRc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    Kind* kindM,
    LLVMValueRef newControlBlockLE);

void buildCheckGen(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef targetGenLE,
    LLVMValueRef actualGenLE);

LoadResult loadElementFromSSAInner(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    int size,
    Reference* elementType,
    Ref indexRef,
    LLVMValueRef arrayElementsPtrLE);

LLVMValueRef makeInterfaceRefStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    StructKind* sourceStructKindM,
    InterfaceKind* targetInterfaceKindM,
    ControlBlockPtrLE controlBlockPtrLE);

LLVMValueRef makeInterfaceRefStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    InterfaceKind* targetInterfaceKindM,
    LLVMValueRef objControlBlockPtrLE,
    LLVMValueRef itablePtrLE);

LLVMValueRef getTablePtrFromInterfaceRef(
    LLVMBuilderRef builder,
    InterfaceFatPtrLE interfaceFatPtrLE);

LLVMValueRef getObjPtrFromInterfaceRef(
    LLVMBuilderRef builder,
    InterfaceFatPtrLE interfaceRefLE);

void innerDeallocate(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* kindStrutsSource,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref);

void fillRuntimeSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref arrayRegionInstanceRef,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Reference* elementType,
    Reference* generatorType,
    Prototype* generatorMethod,
    Ref generatorLE,
    Ref sizeLE,
    Ref rsaRef);

void fillStaticSizedArrayFromCallable(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref arrayRegionInstanceRef,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Reference* elementType,
    Reference* generatorType,
    Prototype* generatorMethod,
    Ref generatorLE,
    Ref sizeLE,
    Ref ssaRef);

std::tuple<Reference*, LLVMValueRef> megaGetRefInnardsForChecking(Ref ref);

LLVMValueRef callMalloc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef sizeLE);

WrapperPtrLE mallocStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE,
    LLVMValueRef sourceCharsPtrLE,
    KindStructs* kindStructs,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock);
LLVMValueRef mallocKnownSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    LLVMTypeRef kindLT);
void fillInnerStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structM,
    std::vector<Ref> membersLE,
    LLVMValueRef innerStructPtrLE);
Ref constructWrappedStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* kindStructsSource,
    LLVMBuilderRef builder,
    LLVMTypeRef structL,
    Reference* structTypeM,
    StructDefinition* structM,
    Weakability effectiveWeakability,
    std::vector<Ref> membersLE,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock);
LLVMValueRef constructInnerStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structM,
    LLVMTypeRef valStructL,
    const std::vector<Ref>& memberRefs);
Ref innerAllocate(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* desiredReference,
    KindStructs* kindStructs,
    const std::vector<Ref>& memberRefs,
    Weakability effectiveWeakability,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock);
// Transmutes a weak ref of one ownership (such as borrow) to another ownership (such as weak).
Ref transmuteWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceWeakRefMT,
    Reference* targetWeakRefMT,
    KindStructs* weakRefStructs,
    Ref sourceWeakRef);

LLVMValueRef mallocRuntimeSizedArray(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef rsaWrapperLT,
    LLVMTypeRef rsaElementLT,
    LLVMValueRef lengthLE);
// Transmutes a ptr of one ownership (such as own) to another ownership (such as borrow).
Ref transmutePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef);

Ref getRuntimeSizedArrayLength(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE arrayRefLE);

Ref getRuntimeSizedArrayCapacity(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE arrayRefLE);

ControlBlock makeAssistAndNaiveRCNonWeakableControlBlock(GlobalState* globalState);
ControlBlock makeAssistAndNaiveRCWeakableControlBlock(GlobalState* globalState);
ControlBlock makeFastWeakableControlBlock(GlobalState* globalState);
ControlBlock makeFastNonWeakableControlBlock(GlobalState* globalState);
ControlBlock makeResilientV0WeakableControlBlock(GlobalState* globalState);
Ref resilientLockWeak(
    GlobalState* globalState,
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
    std::function<Ref(LLVMBuilderRef)> buildElse,
    Ref isAliveLE,
    LLVMTypeRef resultOptTypeL,
    KindStructs* weakRefStructs);

Ref resilientDowncast(
    GlobalState* globalState,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    KindStructs* weakRefStructs,
    Reference *resultOptTypeM,
    Reference *sourceInterfaceRefMT,
    Ref &sourceInterfaceRef,
    Kind *targetKind,
    const std::function<Ref(LLVMBuilderRef, Ref)> &buildThen,
    std::function<Ref(LLVMBuilderRef)> &buildElse,
    StructKind *targetStructKind,
    InterfaceKind *sourceInterfaceKind);
ControlBlock makeResilientV1WeakableControlBlock(GlobalState* globalState);
ControlBlock makeResilientV3WeakableControlBlock(GlobalState* globalState);
ControlBlock makeMutNonWeakableControlBlock(GlobalState* globalState, RegionId* regionId);
ControlBlock makeMutWeakableControlBlock(GlobalState* globalState, RegionId* regionId);
void fillStaticSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref arrayRegionInstanceRef,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Ref ssaRef,
    const std::vector<Ref>& elementRefs);

// Returns a LLVMValueRef for a ref to the string object.
// The caller should then use getStringBytesPtr to then fill the string's contents.
Ref constructStaticSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    StaticSizedArrayT* ssaMT,
    KindStructs* kindStructs,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock);


void regularCheckValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* refM,
    LLVMValueRef refLE);

LoadResult regularLoadElementFromRSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    bool capacityExists,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Mutability mutability,
    Reference* elementType,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef);

LoadResult resilientLoadElementFromRSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    bool capacityExists,
    Reference* rsaRefMT,
    Mutability mutability,
    Reference* elementType,
    RuntimeSizedArrayT* rsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef);

Ref regularStoreElementInSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* rsaRefMT,
    Reference* elementType,
    int size,
    Ref arrayRef,
    Ref indexRef,
    Ref elementRef);

void regularInitializeElementInSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* ssaRefMT,
    Reference* elementType,
    int size,
    Ref arrayRef,
    Ref indexRef,
    Ref elementRef);

Ref constructRuntimeSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* rsaMT,
    Reference* elementType,
    RuntimeSizedArrayT* runtimeSizedArrayT,
    LLVMTypeRef rsaWrapperPtrLT,
    LLVMTypeRef rsaElementLT,
    Ref initialSizeRef,
    Ref capacityRef,
    bool capacityExists,
    const std::string& typeName,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock);

LoadResult regularLoadStrongMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName);

LoadResult regularLoadMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName);


LoadResult resilientLoadWeakMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    Reference* expectedMemberType,
    const std::string& memberName);


Ref upcastStrong(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* sourceStructMT,
    StructKind* sourceStructKindM,
    Ref sourceRefLE,
    Reference* targetInterfaceTypeM,
    InterfaceKind* targetInterfaceKindM);

Ref upcastWeak(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* weakRefStructs,
    Reference* sourceStructMT,
    StructKind* sourceStructKindM,
    Ref sourceRefLE,
    Reference* targetInterfaceTypeM,
    InterfaceKind* targetInterfaceKindM);

LoadResult regularloadElementFromSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Reference* elementType,
    int arraySize,
    Mutability mutability,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    KindStructs* kindStructs);

LoadResult resilientloadElementFromSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    int size,
    Mutability mutability,
    Reference* elementType,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    KindStructs* kindStructs);


void regularFillControlBlock(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* structs,
    LLVMBuilderRef builder,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName,
    WrcWeaks* wrcWeaks);

void gmFillControlBlock(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* structs,
    LLVMBuilderRef builder,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName,
    HybridGenerationalMemory* hgmWeaks);

Ref getRuntimeSizedArrayLengthStrong(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* rsaRefMT,
    Ref arrayRef);

Ref getRuntimeSizedArrayCapacityStrong(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* rsaRefMT,
    Ref arrayRef);

std::tuple<LLVMValueRef, LLVMValueRef> explodeStrongInterfaceRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* virtualParamMT,
    Ref virtualArgRef);

std::tuple<LLVMValueRef, LLVMValueRef> explodeWeakInterfaceRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    FatWeaks* fatWeaks,
    KindStructs* weakRefStructs,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    std::function<WeakFatPtrLE(WeakFatPtrLE weakInterfaceFatPtrLE)> weakInterfaceRefToWeakStructRef);


void storeMemberStrong(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newValueLE);

void storeMemberWeak(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newValueLE);


Ref regularWeakAlias(
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* kindStructs,
    WrcWeaks* wrcWeaks,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef);

Ref regularInnerLockWeak(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference* resultOptTypeM,
    Reference* constraintRefM,
    Reference* sourceWeakRefMT,
    Ref sourceWeakRefLE,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse,
    Ref isAliveLE,
    LLVMTypeRef resultOptTypeL,
    KindStructs* weakRefStructsSource,
    FatWeaks* fatWeaks);

void callFree(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef ptrLE);


LLVMValueRef getInterfaceMethodFunctionPtrFromItable(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    int indexInEdge);

void initializeElementInRSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    bool capacityExists,
    bool incrementSize,
    RuntimeSizedArrayT* rsaMT,
    Reference* rsaRefMT,
    WrapperPtrLE arrayWrapperPtrLE,
    Ref rsaRef,
    Ref indexRef,
    Ref elementRef);

Ref normalLocalLoad(
    GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr);

Ref normalLocalStore(GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore);


Ref regularDowncast(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    Reference* resultOptTypeM,
    Reference* sourceInterfaceRefMT,
    Ref sourceInterfaceRef,
    bool sourceRefKnownLive,
    Kind* targetKind,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse);

Ref regularReceiveAndDecryptFamiliarReference(
    GlobalState* globalState,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference *sourceRefMT,
    LLVMValueRef sourceRefLE);

LLVMValueRef regularEncryptAndSendFamiliarReference(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* sourceRefMT,
    Ref sourceRef);

Ref resilientReceiveAndDecryptFamiliarReference(
    GlobalState* globalState,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    KindStructs* weakableKindStructs,
    HybridGenerationalMemory* hgm,
    Reference *sourceRefMT,
    LLVMValueRef sourceRefLE);

LLVMValueRef resilientEncryptAndSendFamiliarReference(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    HybridGenerationalMemory* hgm,
    Reference* sourceRefMT,
    Ref sourceRef);

std::string generateMutableInterfaceHandleDefC(Package* currentPackage, const std::string& name);

std::string generateMutableConcreteHandleDefC(Package* currentPackage, const std::string& name);


void fastPanic(GlobalState* globalState, AreaAndFileAndLine from, LLVMBuilderRef builder);

LLVMValueRef compressI64PtrToI56(GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef ptrLE);
LLVMValueRef compressI64PtrToI52(GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef ptrLE);
LLVMValueRef decompressI56PtrToI64(
    GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef ptrI56LE);
LLVMValueRef decompressI52PtrToI64(GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef ptrI52LE);

#endif
