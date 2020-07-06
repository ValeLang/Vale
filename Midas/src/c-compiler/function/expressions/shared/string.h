#ifndef FUNCTION_EXPRESSIONS_SHARED_STRING_H_
#define FUNCTION_EXPRESSIONS_SHARED_STRING_H_

#include "shared.h"

LLVMValueRef getInnerStrPtrFromWrapperPtr(
    LLVMBuilderRef builder,
    LLVMValueRef strWrapperPtrLE);

LLVMValueRef getLenPtrFromStrWrapperPtr(
    LLVMBuilderRef builder,
    LLVMValueRef strWrapperPtrLE);

LLVMValueRef getLenFromStrWrapperPtr(
    LLVMBuilderRef builder,
    LLVMValueRef strWrapperPtrLE);

#endif