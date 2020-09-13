#ifndef REGION_COMMON_COMMON_H_
#define REGION_COMMON_COMMON_H_

#include <globalstate.h>
#include <function/function.h>
#include <llvm-c/Types.h>

LLVMValueRef weakStructPtrToGenWeakInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM);

LLVMValueRef upcastThinPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    WrapperPtrLE sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM);

LLVMTypeRef translateReferenceSimple(GlobalState* globalState, Referend* referend);

LLVMTypeRef translateWeakReference(GlobalState* globalState, Referend* referend);


LLVMValueRef getStructContentsPtrNormal(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref refLE);
LLVMValueRef getStructContentsPtrForce(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref refLE);
Ref loadInnerInnerStructMember(
    IRegion* region,
    LLVMBuilderRef builder, LLVMValueRef innerStructPtrLE, int memberIndex, Reference* expectedType, std::string memberName);
void storeInnerInnerStructMember(
    LLVMBuilderRef builder, LLVMValueRef innerStructPtrLE, int memberIndex, std::string memberName, LLVMValueRef newValueLE);


LLVMValueRef getItablePtrFromInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE);

LLVMValueRef getVoidPtrFromInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE);


LLVMValueRef fillControlBlockCensusFields(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef newControlBlockLE,
    const std::string& typeName);

LLVMValueRef insertStrongRc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef newControlBlockLE);

Ref loadElementFromKSAWithoutUpgradeNormal(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    Ref indexRef);

Ref loadElementFromKSAWithoutUpgradeForce(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    Ref indexRef);

void buildCheckGen(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef targetGenLE,
    LLVMValueRef actualGenLE);


LLVMValueRef makeInterfaceRefStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructReferend* sourceStructReferendM,
    InterfaceReferend* targetInterfaceReferendM,
    ControlBlockPtrLE controlBlockPtrLE);

LLVMValueRef getTablePtrFromInterfaceRef(
    LLVMBuilderRef builder,
    InterfaceFatPtrLE interfaceFatPtrLE);

#endif