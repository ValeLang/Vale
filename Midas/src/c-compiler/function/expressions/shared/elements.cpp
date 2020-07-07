#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "branch.h"

LLVMValueRef getKnownSizeArrayContentsPtr(
    LLVMBuilderRef builder, LLVMValueRef knownSizeArrayWrapperPtrLE) {
  return LLVMBuildStructGEP(
      builder,
      knownSizeArrayWrapperPtrLE,
      1, // Array is after the control block.
      "ksaElemsPtr");
}

LLVMValueRef getUnknownSizeArrayLengthPtr(
    LLVMBuilderRef builder, LLVMValueRef unknownSizeArrayWrapperPtrLE) {
  auto resultLE =
      LLVMBuildStructGEP(
          builder,
          unknownSizeArrayWrapperPtrLE,
          1, // Length is after the control block and before contents.
          "usaLenPtr");
  assert(LLVMTypeOf(resultLE) == LLVMPointerType(LLVMInt64Type(), 0));
  return resultLE;
}

LLVMValueRef getUnknownSizeArrayLength(
    LLVMBuilderRef builder,
    LLVMValueRef arrayWrapperPtrLE) {
  return LLVMBuildLoad(builder, getUnknownSizeArrayLengthPtr(builder, arrayWrapperPtrLE), "usaLen");
}

LLVMValueRef getUnknownSizeArrayContentsPtr(
    LLVMBuilderRef builder, LLVMValueRef unknownSizeArrayWrapperPtrLE) {
  return LLVMBuildStructGEP(
      builder,
      unknownSizeArrayWrapperPtrLE,
      2, // Array is after the control block and length.
      "usaElemsPtr");
}

LLVMValueRef loadInnerArrayMember(
    LLVMBuilderRef builder,
    LLVMValueRef elemsPtrLE,
    LLVMValueRef indexLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(elemsPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      constI64LE(0),
      indexLE
  };
  return LLVMBuildLoad(
      builder,
      LLVMBuildGEP(
          builder, elemsPtrLE, indices, 2, "indexPtr"),
      "index");
}

LLVMValueRef loadElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    LLVMValueRef sizeLE,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    LLVMValueRef indexLE) {

  auto isNonNegativeLE = LLVMBuildICmp(builder, LLVMIntSGE, indexLE, constI64LE(0), "isNonNegative");
  auto isUnderLength = LLVMBuildICmp(builder, LLVMIntSLT, indexLE, sizeLE, "isUnderLength");
  auto isWithinBounds = LLVMBuildAnd(builder, isNonNegativeLE, isUnderLength, "isWithinBounds");
  buildAssert(AFL("Bounds check"), globalState, functionState, builder, isWithinBounds, "Index out of bounds!");

  if (mutability == Mutability::IMMUTABLE) {
    if (structRefM->location == Location::INLINE) {
      assert(false);
//      return LLVMBuildExtractValue(builder, structExpr, indexLE, "index");
      return nullptr;
    } else {
      return loadInnerArrayMember(builder, arrayPtrLE, indexLE);
    }
  } else if (mutability == Mutability::MUTABLE) {
    return loadInnerArrayMember(builder, arrayPtrLE, indexLE);
  } else {
    assert(false);
    return nullptr;
  }
}


void foreachArrayElement(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sizeLE,
    LLVMValueRef arrayPtrLE,
    std::function<void(LLVMValueRef, LLVMBuilderRef)> iterationBuilder) {
  LLVMValueRef iterationIndexPtrLE = LLVMBuildAlloca(builder, LLVMInt64Type(), "iterationIndex");
  LLVMBuildStore(builder, LLVMConstInt(LLVMInt64Type(), 0, false), iterationIndexPtrLE);

  buildWhile(
      functionState,
      builder,
      [sizeLE, iterationIndexPtrLE](LLVMBuilderRef conditionBuilder) {
        auto iterationIndexLE =
            LLVMBuildLoad(conditionBuilder, iterationIndexPtrLE, "iterationIndex");
        auto isBeforeEndLE =
            LLVMBuildICmp(
                conditionBuilder,LLVMIntSLT,iterationIndexLE,sizeLE,"iterationIndexIsBeforeEnd");
        return isBeforeEndLE;
      },
      [iterationBuilder, iterationIndexPtrLE, arrayPtrLE](LLVMBuilderRef bodyBuilder) {
        auto iterationIndexLE = LLVMBuildLoad(bodyBuilder, iterationIndexPtrLE, "iterationIndex");
        iterationBuilder(iterationIndexLE, bodyBuilder);
        adjustCounter(bodyBuilder, iterationIndexPtrLE, 1);
      });
}
