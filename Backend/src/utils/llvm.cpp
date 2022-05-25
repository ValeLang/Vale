//
// Created by Evan Ovadia on 5/18/22.
//

#include "llvm.h"

inline LLVMValueRef constI64LE(LLVMContextRef context, int64_t n) {
  return LLVMConstInt(LLVMInt64TypeInContext(context), n, false);
}

LLVMValueRef ptrIsNull(LLVMContextRef context, LLVMBuilderRef builder, LLVMValueRef ptrLE) {
  auto int64LT = LLVMInt64TypeInContext(context);
  auto ptrAsI64LE = LLVMBuildPtrToInt(builder, ptrLE, int64LT, "ptrAsI64");
  auto ptrIsNullLE = LLVMBuildICmp(builder, LLVMIntEQ, ptrAsI64LE, constI64LE(context, 0), "ptrIsNull");
  return ptrIsNullLE;
}

LLVMValueRef subscriptForPtr(
    LLVMBuilderRef builder,
    LLVMValueRef elementsPtrLE,
    LLVMValueRef indexLE,
    const std::string& name) {
  std::vector<LLVMValueRef> indices = { indexLE };
  return LLVMBuildGEP(builder, elementsPtrLE, indices.data(), indices.size(), name.c_str());
}

LLVMValueRef subscript(
    LLVMBuilderRef builder,
    LLVMValueRef elementsPtrLE,
    LLVMValueRef indexLE,
    const std::string& name) {
  return LLVMBuildLoad(builder, subscriptForPtr(builder, elementsPtrLE, indexLE), name.c_str());
}
