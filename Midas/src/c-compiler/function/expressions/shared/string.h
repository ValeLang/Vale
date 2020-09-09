#ifndef FUNCTION_EXPRESSIONS_SHARED_STRING_H_
#define FUNCTION_EXPRESSIONS_SHARED_STRING_H_

#include "shared.h"

LLVMValueRef getInnerStrPtrFromWrapperPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE strWrapperPtrLE);

LLVMValueRef getLenPtrFromStrWrapperPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE strWrapperPtrLE);

LLVMValueRef getLenFromStrWrapperPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE strWrapperPtrLE);

WrapperPtrLE buildConstantVStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    const std::string& contents);

#endif