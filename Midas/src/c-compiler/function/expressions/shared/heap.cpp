#include "utils/fileio.h"
#include "heap.h"
#include "members.h"
#include "shared.h"
#include "controlblock.h"
#include "string.h"

LLVMValueRef mallocStruct(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef structL) {
  size_t sizeBytes = LLVMABISizeOfType(globalState->dataLayout, structL);
  LLVMValueRef sizeLE = LLVMConstInt(LLVMInt64Type(), sizeBytes, false);

  auto newStructLE =
      LLVMBuildCall(builder, globalState->malloc, &sizeLE, 1, "");

  adjustCounter(builder, globalState->liveHeapObjCounter, 1);

  return LLVMBuildBitCast(
      builder,
      newStructLE,
      LLVMPointerType(structL, 0),
      "newstruct");
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

  auto newWrapperLE =
      LLVMBuildCall(builder, globalState->malloc, &sizeBytesLE, 1, "");

  adjustCounter(builder, globalState->liveHeapObjCounter, 1);

  return LLVMBuildBitCast(
      builder,
      newWrapperLE,
      LLVMPointerType(usaWrapperLT, 0),
      "newstruct");
}

LLVMValueRef mallocStr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE) {

  // The +1 is for the null terminator at the end, for C compatibility.
  auto sizeBytesLE =
      LLVMBuildAdd(
          builder,
          lengthLE,
          makeConstIntExpr(builder,LLVMInt64Type(),  1 + LLVMABISizeOfType(globalState->dataLayout, globalState->stringWrapperStructL)),
          "strMallocSizeBytes");

  auto destCharPtrLE =
      LLVMBuildCall(builder, globalState->malloc, &sizeBytesLE, 1, "donePtr");

  adjustCounter(builder, globalState->liveHeapObjCounter, 1);

  auto newStrWrapperPtrLE =
      LLVMBuildBitCast(
          builder,
          destCharPtrLE,
          LLVMPointerType(globalState->stringWrapperStructL, 0),
          "newStrWrapperPtr");
  fillControlBlock(
      globalState, builder, getConcreteControlBlockPtr(builder, newStrWrapperPtrLE), "Str");
  LLVMBuildStore(builder, lengthLE, getLenPtrFromStrWrapperPtr(builder, newStrWrapperPtrLE));

  // The caller still needs to initialize the actual chars inside!

  return newStrWrapperPtrLE;
}




void freeConcrete(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef concreteLE,
    Reference* concreteRefM) {

  auto rcIsZeroLE = rcIsZero(globalState, builder, concreteLE, concreteRefM);
  buildAssert(from, globalState, functionState, builder, rcIsZeroLE, "Tried to free concrete that had nonzero RC!");

  adjustCounter(builder, globalState->liveHeapObjCounter, -1);

  auto concreteAsCharPtrLE =
      LLVMBuildBitCast(
          builder,
          concreteLE,
          LLVMPointerType(LLVMInt8Type(), 0),
          "concreteCharPtrForFree");
  LLVMBuildCall(
      builder, globalState->free, &concreteAsCharPtrLE, 1, "");
}
