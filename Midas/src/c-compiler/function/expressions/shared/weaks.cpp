#include "shared.h"
#include "weaks.h"

#include "translatetype.h"
#include "controlblock.h"
#include "branch.h"

constexpr uint64_t WRC_ALIVE_BIT = 0x8000000000000000;

LLVMValueRef getWrcPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE) {
  auto wrcEntriesPtrLE =
      LLVMBuildLoad(builder, globalState->wrcEntriesArrayPtr, "wrcEntriesArrayPtr");
  auto ptrToWrcLE =
      LLVMBuildGEP(builder, wrcEntriesPtrLE, &wrciLE, 1, "ptrToWrc");
  return ptrToWrcLE;
}

void maybeReleaseWrc(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE,
    LLVMValueRef ptrToWrcLE,
    LLVMValueRef wrcLE) {
  buildIf(
      functionState,
      builder,
      isZeroLE(builder, wrcLE),
      [globalState, functionState, wrciLE, ptrToWrcLE](LLVMBuilderRef thenBuilder) {
        // __wrc_entries[wrcIndex] = __wrc_firstFree;
        LLVMBuildStore(
            thenBuilder,
            LLVMBuildLoad(
                thenBuilder, globalState->wrcFirstFreeWrciPtr, "firstFreeWrci"),
            ptrToWrcLE);
        // __wrc_firstFree = wrcIndex;
        LLVMBuildStore(thenBuilder, wrciLE, globalState->wrcFirstFreeWrciPtr);
      });
}

void adjustWeakRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE,
    int amount) {
  buildFlare(from, globalState, functionState, builder, "Adjusting by ", amount);
  auto wrciLE = getWrciFromWeakRef(builder, exprLE);
  if (globalState->opt->census) {
    buildCheckWrc(globalState, builder, wrciLE);
  }

  auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
  auto wrcLE = adjustCounter(builder, ptrToWrcLE, amount);

  // if (amount == 1) {
  //   LLVMBuildCall(builder, globalState->incrementWrc, &wrciLE, 1, "");
  // } else if (amount == -1) {
  //   LLVMBuildCall(builder, globalState->decrementWrc, &wrciLE, 1, "");
  // } else assert(false);

  if (amount < 0) {
    maybeReleaseWrc(globalState, functionState, builder, wrciLE, ptrToWrcLE, wrcLE);
  }
}


// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
LLVMValueRef forceDerefWeak(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef weakRefLE) {
  auto wrciLE = getWrciFromWeakRef(builder, weakRefLE);
  auto isAliveLE = getIsAliveFromWeakRef(globalState, builder, weakRefLE);
  buildIf(
      functionState, builder, isZeroLE(builder, isAliveLE),
      [from, globalState, functionState, wrciLE](LLVMBuilderRef thenBuilder) {
        buildPrintAreaAndFileAndLine(globalState, thenBuilder, from);
        buildPrint(globalState, thenBuilder, "Tried dereferencing dangling reference, wrci: ");
        buildPrint(globalState, thenBuilder, wrciLE);
        buildPrint(globalState, thenBuilder, ", exiting!\n");
        auto exitCodeIntLE = LLVMConstInt(LLVMInt8Type(), 255, false);
        LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");
      });
  return getInnerRefFromWeakRef(globalState, functionState, builder, refM, weakRefLE);
}

void markWrcDead(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* concreteRefM,
    LLVMValueRef concreteRefLE) {
  auto controlBlockPtrLE = getControlBlockPtr(builder, concreteRefLE, concreteRefM);
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, concreteRefM, controlBlockPtrLE);

//  LLVMBuildCall(builder, globalState->markWrcDead, &wrciLE, 1, "");

  auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
  auto prevWrcLE = LLVMBuildLoad(builder, ptrToWrcLE, "wrc");

  auto wrcLE =
      LLVMBuildAnd(
          builder,
          prevWrcLE,
          LLVMConstInt(LLVMInt64Type(), ~WRC_ALIVE_BIT, true),
          "");

  // Equivalent:
  // __wrc_entries[wrcIndex] = __wrc_entries[wrcIndex] & ~WRC_LIVE_BIT;
  // *wrcPtr = *wrcPtr & ~WRC_LIVE_BIT;
  LLVMBuildStore(builder, wrcLE, ptrToWrcLE);

  maybeReleaseWrc(globalState, functionState, builder, wrciLE, ptrToWrcLE, wrcLE);
}


LLVMValueRef getIsAliveFromWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  auto wrciLE = getWrciFromWeakRef(builder, weakRefLE);
  if (globalState->opt->census) {
    buildCheckWrc(globalState, builder, wrciLE);
  }

  auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
  auto wrcLE = LLVMBuildLoad(builder, ptrToWrcLE, "wrc");
  return LLVMBuildICmp(
      builder,
      LLVMIntNE,
      LLVMBuildAnd(
          builder,
          wrcLE,
          LLVMConstInt(LLVMInt64Type(), WRC_ALIVE_BIT, false),
          "wrcLiveBitOrZero"),
      constI64LE(0),
      "wrcLive");
}
