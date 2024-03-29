#ifndef UTILS_COUNTERS_H_
#define UTILS_COUNTERS_H_

#include <llvm-c/Core.h>
#include "../function/expressions/shared/afl.h"
#include "../globalstate.h"
#include "../function/function.h"

LLVMValueRef adjustCounterV(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Int* innt,
    LLVMValueRef counterPtrLE,
    int adjustAmount,
    bool atomic);

LLVMValueRef adjustCounter(
    LLVMBuilderRef builder,
    LLVMTypeRef type,
    LLVMValueRef counterPtrLE,
    int adjustAmount,
    bool atomic);

LLVMValueRef adjustCounterVReturnOld(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Int* innt,
    LLVMValueRef counterPtrLE,
    int adjustAmount);


LLVMValueRef adjustCounterReturnOld(
    LLVMBuilderRef builder,
    LLVMTypeRef type,
    LLVMValueRef counterPtrLE,
    int64_t adjustAmount);

LLVMValueRef isZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE);
LLVMValueRef isNonZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE);


LLVMValueRef hexRoundDown(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef n);

LLVMValueRef hexRoundUp(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef n);

LLVMValueRef roundUp(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int multipleOfThis,
    LLVMValueRef n);

#endif
