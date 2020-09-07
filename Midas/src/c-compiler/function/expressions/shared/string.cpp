#include "string.h"
#include "heap.h"

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

  auto strWrapperPtrLE = mallocStr(globalState, functionState, builder, lengthLE);

  std::vector<LLVMValueRef> argsLE = {
      getInnerStrPtrFromWrapperPtr(builder, strWrapperPtrLE),
      globalState->getOrMakeStringConstant(contents)
  };
  LLVMBuildCall(builder, globalState->initStr, argsLE.data(), argsLE.size(), "");

  return strWrapperPtrLE;
}
