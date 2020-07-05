#include "utils/fileio.h"
#include "heap.h"
#include "members.h"
#include "shared.h"
#include "controlblock.h"

LLVMValueRef mallocStruct(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef structL) {
  size_t sizeBytes = LLVMABISizeOfType(globalState->dataLayout, structL);
  LLVMValueRef sizeLE = LLVMConstInt(LLVMInt64Type(), sizeBytes, false);

//  buildFlare(FL(), globalState, builder, "Malloc ", sizeLE);

  auto newStructLE =
      LLVMBuildCall(builder, globalState->malloc, &sizeLE, 1, "");

  adjustCounter(builder, globalState->liveHeapObjCounter, 1);

  return LLVMBuildBitCast(
      builder,
      newStructLE,
      LLVMPointerType(structL, 0),
      "newstruct");
}

void freeStruct(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef structPtrLE,
    Reference* structRefM) {

  auto rcIsZeroLE = rcIsZero(globalState, builder, structPtrLE, structRefM);
  buildAssert(from, globalState, functionState, builder, rcIsZeroLE, "Tried to free struct that had nonzero RC!");

  adjustCounter(builder, globalState->liveHeapObjCounter, -1);

  auto structAsCharPtrLE =
      LLVMBuildBitCast(
          builder,
          structPtrLE,
          LLVMPointerType(LLVMInt8Type(), 0),
          "structCharPtrForFree");
  LLVMBuildCall(
      builder, globalState->free, &structAsCharPtrLE, 1, "");
}
