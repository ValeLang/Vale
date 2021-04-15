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
    GlobalState* globalState,
    LLVMBuilderRef builder,
    WrapperPtrLE strWrapperPtrLE) {
  auto innerStringPtrLE =
      getInnerStrPtrFromWrapperPtr(builder, strWrapperPtrLE);
  auto charsArrayPtrLE =
      LLVMBuildStructGEP(builder, innerStringPtrLE, 1, "charsPtr");

  std::vector<LLVMValueRef> indices = { constI64LE(globalState, 0), constI64LE(globalState, 0) };
  auto firstCharPtrLE =
      LLVMBuildGEP(
          builder, charsArrayPtrLE, indices.data(), indices.size(), "elementPtr");
  assert(LLVMTypeOf(firstCharPtrLE) == LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0));
  return firstCharPtrLE;
}

LLVMValueRef getLenFromStrWrapperPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE strWrapperPtrLE) {
  return LLVMBuildLoad(builder, getLenPtrFromStrWrapperPtr(builder, strWrapperPtrLE), "len");
}

Ref buildConstantVStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    const std::string& contents) {

  auto lengthLE = constI64LE(globalState, contents.length());

  auto strRef =
      globalState->getRegion(globalState->metalCache->strRef)
          ->mallocStr(
              makeEmptyTupleRef(globalState),
              functionState, builder, lengthLE,
              globalState->getOrMakeStringConstant(contents));

  buildFlare(FL(), globalState, functionState, builder, "done storing");

//
//  // Fill the chars
//  std::vector<LLVMValueRef> argsLE = {
//      globalState->getRegion(globalState->metalCache->strRef)->getStringBytesPtr(functionState, builder, strRef),
//      ,
//      lengthLE
//  };
//  LLVMBuildCall(builder, globalState->strncpy, argsLE.data(), argsLE.size(), "");

  return strRef;
}
