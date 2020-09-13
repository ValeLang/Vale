#include "counters.h"

LLVMValueRef adjustCounter(
    LLVMBuilderRef builder,
    LLVMValueRef counterPtrLE,
    int adjustAmount) {
  if (LLVMTypeOf(counterPtrLE) == LLVMPointerType(LLVMInt64Type(), 0)) {
    auto prevValLE = LLVMBuildLoad(builder, counterPtrLE, "counterPrevVal");
    auto newValLE =
        LLVMBuildAdd(
            builder, prevValLE, LLVMConstInt(LLVMInt64Type(), adjustAmount, true), "counterNewVal");
    LLVMBuildStore(builder, newValLE, counterPtrLE);

    return newValLE;
  } else if (LLVMTypeOf(counterPtrLE) == LLVMPointerType(LLVMInt32Type(), 0)) {
    auto prevValLE = LLVMBuildLoad(builder, counterPtrLE, "counterPrevVal");
    auto newValLE =
        LLVMBuildAdd(
            builder, prevValLE, LLVMConstInt(LLVMInt32Type(), adjustAmount, true), "counterNewVal");
    LLVMBuildStore(builder, newValLE, counterPtrLE);

    return newValLE;
  } else {
    // impl
    assert(false);
  }
}

LLVMValueRef isZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE) {
  return LLVMBuildICmp(
      builder,
      LLVMIntEQ,
      intLE,
      LLVMConstInt(LLVMTypeOf(intLE), 0, false),
      "strongRcIsZero");
}

LLVMValueRef isNonZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE) {
  return LLVMBuildICmp(
      builder,
      LLVMIntNE,
      intLE,
      LLVMConstInt(LLVMTypeOf(intLE), 0, false),
      "rcIsNonZero");
}
