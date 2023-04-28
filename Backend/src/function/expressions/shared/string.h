#ifndef FUNCTION_EXPRESSIONS_SHARED_STRING_H_
#define FUNCTION_EXPRESSIONS_SHARED_STRING_H_

#include "shared.h"

LLVMValueRef getInnerStrPtrFromWrapperPtr(
    LLVMBuilderRef builder,
    LLVMTypeRef stringInnerStructLT,
    WrapperPtrLE strWrapperPtrLE);

LLVMValueRef getCharsPtrFromWrapperPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef stringInnerStructLT,
    WrapperPtrLE strWrapperPtrLE);

LLVMValueRef getLenPtrFromStrWrapperPtr(
    LLVMContextRef context,
    LLVMBuilderRef builder,
    LLVMTypeRef stringInnerStructLT,
    WrapperPtrLE strWrapperPtrLE);

LLVMValueRef getLenFromStrWrapperPtr(
    LLVMContextRef context,
    LLVMBuilderRef builder,
    LLVMTypeRef stringInnerStructLT,
    WrapperPtrLE strWrapperPtrLE);

Ref buildConstantVStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    const std::string& contents);

#endif