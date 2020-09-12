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

#endif