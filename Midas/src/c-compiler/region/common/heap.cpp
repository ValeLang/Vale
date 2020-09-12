#include <utils/counters.h>
#include "fileio.h"
#include "heap.h"
#include "function/expressions/shared/members.h"
#include "function/expressions/shared/shared.h"
#include "region/common/controlblock.h"
#include "function/expressions/shared/string.h"
#include "function/expressions/shared/weaks.h"

LLVMValueRef callMalloc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef sizeLE) {
  if (globalState->opt->genHeap) {
    return LLVMBuildCall(builder, globalState->genMalloc, &sizeLE, 1, "");
  } else {
    return LLVMBuildCall(builder, globalState->malloc, &sizeLE, 1, "");
  }
}

LLVMValueRef callFree(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    ControlBlockPtrLE controlBlockPtrLE) {
  if (globalState->opt->genHeap) {
    auto concreteAsVoidPtrLE =
        LLVMBuildBitCast(
            builder,
            controlBlockPtrLE.refLE,
            LLVMPointerType(LLVMVoidType(), 0),
            "concreteVoidPtrForFree");
    return LLVMBuildCall(builder, globalState->genFree, &concreteAsVoidPtrLE, 1, "");
  } else {
    auto concreteAsCharPtrLE =
        LLVMBuildBitCast(
            builder,
            controlBlockPtrLE.refLE,
            LLVMPointerType(LLVMInt8Type(), 0),
            "concreteCharPtrForFree");
    return LLVMBuildCall(builder, globalState->free, &concreteAsCharPtrLE, 1, "");
  }
}

LLVMValueRef mallocKnownSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    LLVMTypeRef referendLT) {
  if (globalState->opt->census) {
    adjustCounter(builder, globalState->liveHeapObjCounter, 1);
  }

  LLVMValueRef resultPtrLE = nullptr;
  if (location == Location::INLINE) {
    resultPtrLE = makeMidasLocal(functionState, builder, referendLT, "newstruct", LLVMGetUndef(referendLT));
  } else if (location == Location::YONDER) {
    size_t sizeBytes = LLVMABISizeOfType(globalState->dataLayout, referendLT);
    LLVMValueRef sizeLE = LLVMConstInt(LLVMInt64Type(), sizeBytes, false);

    auto newStructLE = callMalloc(globalState, builder, sizeLE);

    resultPtrLE =
        LLVMBuildBitCast(
            builder, newStructLE, LLVMPointerType(referendLT, 0), "newstruct");
  } else {
    assert(false);
    return nullptr;
  }

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, resultPtrLE, LLVMPointerType(LLVMVoidType(), 0), "");
    LLVMBuildCall(builder, globalState->censusAdd, &resultAsVoidPtrLE, 1, "");
  }
  return resultPtrLE;
}

LLVMValueRef mallocUnknownSizeArray(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef usaWrapperLT,
    LLVMTypeRef usaElementLT,
    LLVMValueRef lengthLE) {
  auto sizeBytesLE =
      LLVMBuildAdd(
          builder,
          constI64LE(LLVMABISizeOfType(globalState->dataLayout, usaWrapperLT)),
          LLVMBuildMul(
              builder,
              constI64LE(LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(usaElementLT, 1))),
              lengthLE,
              ""),
          "usaMallocSizeBytes");

  auto newWrapperPtrLE = callMalloc(globalState, builder, sizeBytesLE);

  if (globalState->opt->census) {
    adjustCounter(builder, globalState->liveHeapObjCounter, 1);
  }

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, newWrapperPtrLE, LLVMPointerType(LLVMVoidType(), 0), "");
    LLVMBuildCall(builder, globalState->censusAdd, &resultAsVoidPtrLE, 1, "");
  }

  return LLVMBuildBitCast(
      builder,
      newWrapperPtrLE,
      LLVMPointerType(usaWrapperLT, 0),
      "newstruct");
}

WrapperPtrLE mallocStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE) {

  // The +1 is for the null terminator at the end, for C compatibility.
  auto sizeBytesLE =
      LLVMBuildAdd(
          builder,
          lengthLE,
          makeConstIntExpr(
              functionState,
              builder,LLVMInt64Type(),
              1 + LLVMABISizeOfType(globalState->dataLayout, globalState->region->getStringWrapperStruct())),
          "strMallocSizeBytes");

  auto destCharPtrLE = callMalloc(globalState, builder, sizeBytesLE);

  if (globalState->opt->census) {
    adjustCounter(builder, globalState->liveHeapObjCounter, 1);
  }

  auto newStrWrapperPtrLE =
      functionState->defaultRegion->makeWrapperPtr(
          globalState->metalCache.strRef,
          LLVMBuildBitCast(
              builder,
              destCharPtrLE,
              LLVMPointerType(globalState->region->getStringWrapperStruct(), 0),
              "newStrWrapperPtr"));
  globalState->region->fillControlBlock(
      FL(),
      functionState, builder,
      globalState->metalCache.str,
      Mutability::IMMUTABLE,
      getConcreteControlBlockPtr(globalState, builder, newStrWrapperPtrLE), "Str");
  LLVMBuildStore(builder, lengthLE, getLenPtrFromStrWrapperPtr(builder, newStrWrapperPtrLE));

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, newStrWrapperPtrLE.refLE, LLVMPointerType(LLVMVoidType(), 0), "");
    LLVMBuildCall(builder, globalState->censusAdd, &resultAsVoidPtrLE, 1, "");
  }

  // The caller still needs to initialize the actual chars inside!

  return newStrWrapperPtrLE;
}


void deallocate(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    ControlBlockPtrLE controlBlockPtrLE,
    Reference* refM) {

  functionState->defaultRegion->noteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, controlBlockPtrLE.refLE, LLVMPointerType(LLVMVoidType(), 0), "");
    LLVMBuildCall(builder, globalState->censusRemove, &resultAsVoidPtrLE, 1,
        "");
  }

  if (refM->location == Location::INLINE) {
    // Do nothing, it was alloca'd.
  } else if (refM->location == Location::YONDER) {
    callFree(globalState, builder, controlBlockPtrLE);
  }

  if (globalState->opt->census) {
    adjustCounter(builder, globalState->liveHeapObjCounter, -1);
  }
}
