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
    UnconvertedReference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    UnconvertedReference* targetInterfaceTypeM);

LLVMValueRef weakStructPtrToLgtiWeakInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sourceRefLE,
    StructReferend* sourceStructReferendM,
    UnconvertedReference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    UnconvertedReference* targetInterfaceTypeM);

LLVMValueRef weakStructPtrToWrciWeakInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sourceRefLE,
    StructReferend* sourceStructReferendM,
    UnconvertedReference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    UnconvertedReference* targetInterfaceTypeM);

LLVMValueRef upcastThinPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    UnconvertedReference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    LLVMValueRef sourceRefLE,

    UnconvertedReference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM);

LLVMValueRef upcastWeakFatPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    UnconvertedReference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    LLVMValueRef sourceRefLE,

    UnconvertedReference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM);

#endif