#include "string.h"
#include "../../../region/common/heap.h"
#include <region/common/migration.h>

LLVMValueRef getInnerStrPtrFromWrapperPtr(
    LLVMBuilderRef builder,
    LLVMTypeRef stringInnerStructLT,
    WrapperPtrLE strWrapperPtrLE) {
  auto resultLE =
      LLVMBuildStructGEP2(
          builder, strWrapperPtrLE.wrapperStructLT, strWrapperPtrLE.refLE, 1, "strInnerStructPtr");
  assert(LLVMTypeOf(resultLE) == LLVMPointerType(stringInnerStructLT, 0));
  return resultLE;
}

LLVMValueRef getLenPtrFromStrWrapperPtr(
    LLVMContextRef context,
    LLVMBuilderRef builder,
    LLVMTypeRef stringInnerStructLT,
    WrapperPtrLE strWrapperPtrLE) {
  auto int32LT = LLVMInt32TypeInContext(context);
  auto int32PtrLT = LLVMPointerType(int32LT, 0);
  auto innerStringPtrLE =
      getInnerStrPtrFromWrapperPtr(builder, stringInnerStructLT, strWrapperPtrLE);
  assert(LLVMTypeOf(innerStringPtrLE) == LLVMPointerType(stringInnerStructLT, 0));
  auto lenPtrLE =
      LLVMBuildStructGEP2(builder, stringInnerStructLT, innerStringPtrLE, 0, "lenPtrA");
  return lenPtrLE;
}

LLVMValueRef getCharsPtrFromWrapperPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef stringInnerStructLT,
    WrapperPtrLE strWrapperPtrLE) {
  auto int8LT = LLVMInt8TypeInContext(globalState->context);

  auto innerStringPtrLE =
      getInnerStrPtrFromWrapperPtr(builder, stringInnerStructLT, strWrapperPtrLE);
  auto charsArrayPtrLE =
      LLVMBuildStructGEP2(builder, stringInnerStructLT, innerStringPtrLE, 1, "charsPtr");
  assert(LLVMTypeOf(charsArrayPtrLE) == LLVMPointerType(LLVMArrayType(int8LT, 0), 0));

  std::vector<LLVMValueRef> indices = { constI64LE(globalState, 0), constI64LE(globalState, 0) };
  auto firstCharPtrLE =
      LLVMBuildInBoundsGEP2(
          builder, LLVMArrayType(int8LT, 0), charsArrayPtrLE, indices.data(), indices.size(), "elementPtr");
  assert(LLVMGetTypeKind(LLVMTypeOf(firstCharPtrLE)) == LLVMPointerTypeKind);
  return firstCharPtrLE;
}

LLVMValueRef getLenFromStrWrapperPtr(
    LLVMContextRef context,
    LLVMBuilderRef builder,
    LLVMTypeRef stringInnerStructLT,
    WrapperPtrLE strWrapperPtrLE) {
  auto int32LT = LLVMInt32TypeInContext(context);
  auto lenPtrLE = getLenPtrFromStrWrapperPtr(context, builder, stringInnerStructLT, strWrapperPtrLE);
  return LLVMBuildLoad2(builder, int32LT, lenPtrLE, "lenX");
}

Ref buildConstantVStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    const std::string& contents) {

  auto lengthLE = constI32LE(globalState, contents.length());

  auto strRef =
      globalState->getRegion(globalState->metalCache->mutStrRef)
          ->mallocStr(
              makeVoidRef(globalState),
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
//  unmigratedLLVMBuildCall(builder, globalState->strncpy, argsLE.data(), argsLE.size(), "");

  return strRef;
}
