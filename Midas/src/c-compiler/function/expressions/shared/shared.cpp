#include "shared.h"

#include "translatetype.h"
#include "controlblock.h"
#include "branch.h"

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

LLVMValueRef adjustCounter(
    LLVMBuilderRef builder,
    LLVMValueRef counterPtrLE,
    int adjustAmount) {
  auto prevValLE = LLVMBuildLoad(builder, counterPtrLE, "counterPrevVal");
  auto newValLE =
      LLVMBuildAdd(
          builder, prevValLE, LLVMConstInt(LLVMInt64Type(), adjustAmount, true), "counterNewVal");
  LLVMBuildStore(builder, newValLE, counterPtrLE);
  return newValLE;
}


LLVMValueRef getTablePtrFromInterfaceRef(
    LLVMBuilderRef builder,
    LLVMValueRef interfaceRefLE) {
  return LLVMBuildExtractValue(builder, interfaceRefLE, 1, "itablePtr");
}

LLVMValueRef getControlBlockPtr(
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef referenceLE,
    Reference* refM) {
  if (dynamic_cast<InterfaceReferend*>(refM->referend)) {
    return getInterfaceControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<StructReferend*>(refM->referend)) {
    return getStructControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<KnownSizeArrayT*>(refM->referend)) {
    return getStructControlBlockPtr(builder, referenceLE);
  } else {
    assert(false);
    return nullptr;
  }
}

void flareAdjustRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtr,
    LLVMValueRef oldAmount,
    LLVMValueRef newAmount) {
  buildFlare(
      from,
      globalState,
      builder,
      getTypeNameStrPtrFromControlBlockPtr(globalState, builder, controlBlockPtr),
      getObjIdFromControlBlockPtr(globalState, builder, controlBlockPtr),
      ", ",
      oldAmount,
      "->",
      newAmount);
}

// Returns the new RC
LLVMValueRef adjustRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE,
    Reference* refM,
    int amount) {
  auto controlBlockPtrLE = getControlBlockPtr(builder, exprLE, refM);
  auto rcPtrLE =
      getRcPtrFromControlBlockPtr(globalState, builder, controlBlockPtrLE);
  auto oldRc = LLVMBuildLoad(builder, rcPtrLE, "oldRc");
  auto newRc = adjustCounter(builder, rcPtrLE, amount);
  flareAdjustRc(from, globalState, builder, controlBlockPtrLE, oldRc, newRc);
  return newRc;
}

LLVMValueRef rcIsZero(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE,
    Reference* refM) {
  return isZeroLE(
      builder,
      getRcFromControlBlockPtr(
          globalState, builder, getControlBlockPtr(builder, exprLE, refM)));
}

LLVMValueRef isZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE) {
  return LLVMBuildICmp(
      builder,
      LLVMIntEQ,
      intLE,
      LLVMConstInt(LLVMTypeOf(intLE), 0, false),
      "rcIsZero");
}

LLVMValueRef isNonZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE) {
  return LLVMBuildICmp(
      builder,
      LLVMIntNE,
      intLE,
      LLVMConstInt(LLVMTypeOf(intLE), 0, false),
      "rcIsNonZero");
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    const std::string& first) {
  auto s = globalState->getOrMakeStringConstant(first);
  LLVMBuildCall(builder, globalState->printStr, &s, 1, "");
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE) {
  if (LLVMTypeOf(exprLE) == LLVMInt64Type()) {
    LLVMBuildCall(builder, globalState->printInt, &exprLE, 1, "");
  } else if (LLVMTypeOf(exprLE) == LLVMPointerType(LLVMInt64Type(), 0)) {
    buildPrint(
        globalState,
        builder,
        LLVMBuildPointerCast(builder, exprLE, LLVMInt64Type(), ""));
  } else if (LLVMTypeOf(exprLE) == LLVMPointerType(LLVMInt8Type(), 0)) {
    LLVMBuildCall(builder, globalState->printStr, &exprLE, 1, "");
  } else {
    assert(false);
  }
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int num) {
  buildPrint(globalState, builder, LLVMConstInt(LLVMInt64Type(), num, false));
}

// We'll assert if conditionLE is false.
void buildAssert(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    const std::string& failMessage) {
  buildIf(
      functionState, builder, isZeroLE(builder, conditionLE),
      [from, globalState, failMessage](LLVMBuilderRef thenBuilder) {
        buildFlare(from, globalState, thenBuilder, failMessage, " Exiting!");
        auto exitCodeIntLE = LLVMConstInt(LLVMInt8Type(), 255, false);
        LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");
      });
}
