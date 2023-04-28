#ifndef UTILS_LLVM_H
#define UTILS_LLVM_H

#include <llvm-c/Core.h>
#include <string>
#include <vector>

LLVMValueRef constI64LE(LLVMContextRef context, int64_t n);

LLVMValueRef ptrIsNull(LLVMContextRef context, LLVMBuilderRef builder, LLVMValueRef ptrLE);

LLVMValueRef subscript(
    LLVMBuilderRef builder,
    LLVMTypeRef elementLT,
    LLVMValueRef elementsPtrLE,
    LLVMValueRef indexLE,
    const std::string& name = "element");

LLVMValueRef subscriptForPtr(
    LLVMBuilderRef builder,
    LLVMTypeRef elementLT,
    LLVMValueRef elementsPtrLE,
    LLVMValueRef indexLE,
    const std::string& name = "elementPtr");

#endif //UTILS_LLVM_H
