#include "utils/fileio.h"
#include "heap.h"
#include "members.h"
#include "shared.h"

#define buildFlare(globalState,builder,...) buildFlareImpl((globalState), (builder), __FILE__, __LINE__, __VA_ARGS__)

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    const std::string& first) {
  auto s = LLVMBuildGlobalStringPtr(builder, first.c_str(), "");
  LLVMBuildCall(builder, globalState->printStr, &s, 1, "");
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int num) {
  auto n = LLVMConstInt(LLVMInt64Type(), num, false);
  LLVMBuildCall(builder, globalState->printInt, &n, 1, "");
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef intLE) {
  LLVMBuildCall(builder, globalState->printInt, &intLE, 1, "");
}

template<typename First, typename... Rest>
void buildFlareImplInner(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    First&& first,
    Rest&&... rest) {
  buildPrint(globalState, builder, std::forward<First>(first));
  buildFlareImplInner(globalState, builder, std::forward<Rest>(rest)...);
}

template<typename... Rest>
void buildFlareImplInner(
    GlobalState* globalState,
    LLVMBuilderRef builder) {
  auto s = LLVMBuildGlobalStringPtr(builder, "\n", "");
  LLVMBuildCall(builder, globalState->printStr, &s, 1, "");
}

template<typename... T>
void buildFlareImpl(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    const char *file,
    int line,
    T&&... rest) {
  buildPrint(globalState, builder, getFileName(file));
  buildPrint(globalState, builder, ":");
  buildPrint(globalState, builder, line);
  buildFlareImplInner(globalState, builder, std::forward<T>(rest)...);
}

LLVMValueRef mallocStruct(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef structL) {
  size_t sizeBytes = LLVMABISizeOfType(globalState->dataLayout, structL);
  LLVMValueRef sizeLE = LLVMConstInt(LLVMInt64Type(), sizeBytes, false);

  buildFlare(globalState, builder, "Malloc ", sizeLE);

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
