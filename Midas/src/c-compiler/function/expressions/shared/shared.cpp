#include "shared.h"

#include "translatetype.h"

// A "Never" is something that should never be read.
// This is useful in a lot of situations, for example:
// - The return type of Panic()
// - The result of the Discard node
LLVMValueRef makeNever() {
  LLVMValueRef empty[1] = {};
  // We arbitrarily use a zero-len array of i57 here because it's zero sized and
  // very unlikely to be used anywhere else.
  // We could use an empty struct instead, but this'll do.
  return LLVMConstArray(LLVMIntType(NEVER_INT_BITS), empty, 0);
}

void makeLocal(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    LLVMValueRef valueToStore) {
  auto localAddr =
      LLVMBuildAlloca(
          builder,
          translateType(globalState, local->type),
          local->id->maybeName.c_str());
  functionState->localAddrByLocalId.emplace(
      local->id->number, localAddr);
  LLVMBuildStore(builder, valueToStore, localAddr);
}

void flare(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int color,
    LLVMValueRef numExpr) {
  LLVMValueRef args[2] = {
      LLVMConstInt(LLVMInt64Type(), color, false),
      numExpr
  };
  LLVMBuildCall(builder, globalState->flareI64, args, 2, "");
}

void adjustCounter(
    LLVMBuilderRef builder,
    LLVMValueRef counterPtrLE,
    int adjustAmount) {
  auto prevValLE = LLVMBuildLoad(builder, counterPtrLE, "counterPrevVal");
  auto newValLE =
      LLVMBuildAdd(
          builder, prevValLE, LLVMConstInt(LLVMInt64Type(), adjustAmount, true), "counterNewVal");
  LLVMBuildStore(builder, newValLE, counterPtrLE);
}

