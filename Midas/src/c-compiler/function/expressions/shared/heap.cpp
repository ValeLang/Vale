#include "heap.h"
#include "members.h"
#include "shared.h"

LLVMValueRef mallocStruct(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef structL) {
  LLVMValueRef sizeLE =
      LLVMConstInt(
          LLVMInt64Type(),
          LLVMABISizeOfType(globalState->dataLayout, structL),
          false);
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
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef structLE) {
  LLVMValueRef rcIsOne =
      rcEquals(builder, structLE, LLVMConstInt(LLVMInt64Type(), 1, false));
  LLVMBuildCall(
      builder, globalState->assert, &rcIsOne, 1, "");

  adjustCounter(builder, globalState->liveHeapObjCounter, -1);

  auto structAsCharPtrLE =
      LLVMBuildBitCast(
          builder,
          structLE,
          LLVMPointerType(LLVMInt8Type(), 0),
          "charptrstruct");
  LLVMBuildCall(
      builder, globalState->free, &structAsCharPtrLE, 1, "");
}
