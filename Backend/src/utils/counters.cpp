#include <region/common/heap.h>
#include <region/common/migration.h>
#include "counters.h"

LLVMValueRef adjustCounter(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Int* innt,
    LLVMValueRef counterPtrLE,
    int adjustAmount) {
  auto intLT = LLVMIntTypeInContext(globalState->context, innt->bits);
  auto prevValLE = LLVMBuildLoad2(builder, intLT, counterPtrLE, "counterPrevVal");
  auto adjustByLE = LLVMConstInt(intLT, adjustAmount, true);
  assert(LLVMTypeOf(prevValLE) == LLVMTypeOf(adjustByLE));
  auto newValLE =LLVMBuildAdd(builder, prevValLE, adjustByLE, "counterNewVal");
  LLVMBuildStore(builder, newValLE, counterPtrLE);

  return newValLE;
}

LLVMValueRef adjustCounterReturnOld(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Int* innt,
    LLVMValueRef counterPtrLE,
    int adjustAmount) {
  auto intLT = LLVMIntTypeInContext(globalState->context, innt->bits);
  auto prevValLE = LLVMBuildLoad2(builder, intLT, counterPtrLE, "counterPrevVal");
  auto adjustByLE = LLVMConstInt(intLT, adjustAmount, true);
  assert(LLVMTypeOf(prevValLE) == LLVMTypeOf(adjustByLE));
  auto newValLE =LLVMBuildAdd(builder, prevValLE, adjustByLE, "counterNewVal");
  LLVMBuildStore(builder, newValLE, counterPtrLE);

  return prevValLE;
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


LLVMValueRef hexRoundDown(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef n) {
  // Mask off the last four bits, to round downward to the next multiple of 16.
  auto mask = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), ~0xFULL, false);
  return LLVMBuildAnd(builder, n, mask, "rounded");
}

LLVMValueRef hexRoundUp(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef n) {
  return roundUp(globalState, builder, 16, n);
}

LLVMValueRef roundUp(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int multipleOfThis,
    LLVMValueRef n) {
  if ((multipleOfThis & (multipleOfThis - 1)) == 0) {
    // If it's a multiple of two, we can do the below bitwise or trick.
    return LLVMBuildAdd(
        builder,
        LLVMBuildOr(
            builder,
            LLVMBuildSub(builder, n, constI64LE(globalState, 1), "subd1"),
            constI64LE(globalState, multipleOfThis - 1), "ord"),
        constI64LE(globalState, 1), "subd2");
  } else {
    // implement
    assert(false);
  }
}
