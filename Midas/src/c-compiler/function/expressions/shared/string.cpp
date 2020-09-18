#include "string.h"
#include "region/common/heap.h"

LLVMValueRef getInnerStrPtrFromWrapperPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE strWrapperPtrLE) {
  return LLVMBuildStructGEP(
      builder, strWrapperPtrLE.refLE, 1, "strInnerStructPtr");
}

LLVMValueRef getLenPtrFromStrWrapperPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE strWrapperPtrLE) {
  auto innerStringPtrLE =
      getInnerStrPtrFromWrapperPtr(builder, strWrapperPtrLE);
  auto lenPtrLE =
      LLVMBuildStructGEP(builder, innerStringPtrLE, 0, "lenPtr");
  return lenPtrLE;
}

LLVMValueRef getCharsPtrFromWrapperPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE strWrapperPtrLE) {
  auto innerStringPtrLE =
      getInnerStrPtrFromWrapperPtr(builder, strWrapperPtrLE);
  auto charsArrayPtrLE =
      LLVMBuildStructGEP(builder, innerStringPtrLE, 1, "charsPtr");

  std::vector<LLVMValueRef> indices = { constI64LE(0), constI64LE(0) };
  auto firstCharPtrLE =
      LLVMBuildGEP(
          builder, charsArrayPtrLE, indices.data(), indices.size(), "elementPtr");
  assert(LLVMTypeOf(firstCharPtrLE) == LLVMPointerType(LLVMInt8Type(), 0));
  return firstCharPtrLE;
}

LLVMValueRef getLenFromStrWrapperPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE strWrapperPtrLE) {
  return LLVMBuildLoad(builder, getLenPtrFromStrWrapperPtr(builder, strWrapperPtrLE), "len");
}

WrapperPtrLE buildConstantVStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    const std::string& contents) {

  auto lengthLE = constI64LE(contents.length());

  auto strWrapperPtrLE = functionState->defaultRegion->mallocStr(functionState, builder, lengthLE);

  // Set the length
  LLVMBuildStore(builder, lengthLE, getLenPtrFromStrWrapperPtr(builder, strWrapperPtrLE));
  // Fill the chars
  std::vector<LLVMValueRef> argsLE = {
      getCharsPtrFromWrapperPtr(builder, strWrapperPtrLE),
      globalState->getOrMakeStringConstant(contents)
  };
  LLVMBuildCall(builder, globalState->initStr, argsLE.data(), argsLE.size(), "");

  return strWrapperPtrLE;
}
