#include <region/common/heap.h>
#include <region/common/migration.h>
#include "counters.h"

LLVMValueRef adjustCounterV(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Int* innt,
    LLVMValueRef counterPtrLE,
    int adjustAmount,
    bool atomic) {
  auto intLT = LLVMIntTypeInContext(globalState->context, innt->bits);
  return adjustCounter(builder, intLT, counterPtrLE, adjustAmount, atomic);
}

LLVMValueRef adjustCounter(
    LLVMBuilderRef builder,
    LLVMTypeRef type,
    LLVMValueRef counterPtrLE,
    int adjustAmount,
    bool atomic) {
  auto adjustByLE = LLVMConstInt(type, adjustAmount, true);
  assert(LLVMTypeOf(adjustByLE) == type);
  if (atomic) {
    return LLVMBuildAtomicRMW(builder, LLVMAtomicRMWBinOpAdd, counterPtrLE, adjustByLE, LLVMAtomicOrderingMonotonic, !atomic);
  } else {
    auto prevValLE = LLVMBuildLoad2(builder, type, counterPtrLE, "counterPrevVal");
    assert(LLVMTypeOf(prevValLE) == type);
    auto newValLE = LLVMBuildAdd(builder, prevValLE, adjustByLE, "counterNewVal");
    LLVMBuildStore(builder, newValLE, counterPtrLE);
    return newValLE;
  }
}

LLVMValueRef adjustCounterReturnOld(
    LLVMBuilderRef builder,
    LLVMTypeRef type,
    LLVMValueRef counterPtrLE,
    int adjustAmount) {
  auto prevValLE = LLVMBuildLoad2(builder, type, counterPtrLE, "counterPrevVal");
  auto adjustByLE = LLVMConstInt(type, adjustAmount, true);
  assert(LLVMTypeOf(prevValLE) == LLVMTypeOf(adjustByLE));
  auto newValLE = LLVMBuildAdd(builder, prevValLE, adjustByLE, "counterNewVal");
  LLVMBuildStore(builder, newValLE, counterPtrLE);

  return prevValLE;
}

LLVMValueRef adjustCounterVReturnOld(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Int* innt,
    LLVMValueRef counterPtrLE,
    int adjustAmount) {
  auto intLT = LLVMIntTypeInContext(globalState->context, innt->bits);
  return adjustCounterReturnOld(builder, intLT, counterPtrLE, adjustAmount);
}

LLVMValueRef isZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(intLE)) == LLVMIntegerTypeKind);
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
