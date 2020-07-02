#include <iostream>

#include "translatetype.h"

#include "shared.h"

LLVMValueRef loadInnerArrayMember(
    LLVMBuilderRef builder,
    LLVMValueRef innerStructPtrLE,
    LLVMValueRef indexLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      LLVMConstInt(LLVMInt64Type(), 0, false),
      indexLE
  };
  return LLVMBuildLoad(
      builder,
      LLVMBuildGEP(
          builder, innerStructPtrLE, indices, 2, "indexPtr"),
      "index");
}

LLVMValueRef loadElement(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    LLVMValueRef structExpr,
    Mutability mutability,
    LLVMValueRef indexLE) {

  if (mutability == Mutability::IMMUTABLE) {
    if (isInlImm(globalState, structRefM)) {
      assert(false);
//      return LLVMBuildExtractValue(
//          builder, structExpr, indexLE, "index");
      return nullptr;
    } else {
      LLVMValueRef innerStructPtrLE = getCountedContents(builder, structExpr);
      return loadInnerArrayMember(
          builder, innerStructPtrLE, indexLE);
    }
  } else if (mutability == Mutability::MUTABLE) {
    LLVMValueRef innerStructPtrLE = getCountedContents(builder, structExpr);
    return loadInnerArrayMember(
        builder, innerStructPtrLE, indexLE);
  } else {
    assert(false);
    return nullptr;
  }
}
