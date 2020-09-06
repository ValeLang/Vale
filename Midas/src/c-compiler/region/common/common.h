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

    StructReferend* sourceStructReferendM,
    Ref sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM);

LLVMValueRef upcastWeakFatPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    LLVMValueRef sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM);

LLVMTypeRef translateReferenceSimple(GlobalState* globalState, Referend* referend);

LLVMTypeRef translateWeakReference(GlobalState* globalState, Referend* referend);

#endif