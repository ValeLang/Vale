#include "counters.h"

LLVMValueRef adjustCounter(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Int* innt,
    LLVMValueRef counterPtrLE,
    int adjustAmount) {
  auto prevValLE = LLVMBuildLoad(builder, counterPtrLE, "counterPrevVal");
  auto adjustByLE = LLVMConstInt(LLVMIntTypeInContext(globalState->context, innt->bits), adjustAmount, true);
  assert(LLVMTypeOf(prevValLE) == LLVMTypeOf(adjustByLE));
  auto newValLE =LLVMBuildAdd(builder, prevValLE, adjustByLE, "counterNewVal");
  LLVMBuildStore(builder, newValLE, counterPtrLE);

  return newValLE;
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
