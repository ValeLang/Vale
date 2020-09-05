#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "branch.h"
#include "weaks.h"

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
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    LLVMValueRef arrayRefLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      return LLVMBuildLoad(builder, getUnknownSizeArrayLengthPtr(builder, arrayRefLE), "usaLen");
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      LLVMValueRef arrayWrapperPtrLE = nullptr;
      switch (arrayRefM->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
          arrayWrapperPtrLE = arrayRefLE;
          break;
        case Ownership::BORROW:
          arrayWrapperPtrLE =
              lockWeakRef(FL(), globalState, functionState, builder, arrayRefM, arrayRefLE);
          break;
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
          break;
      }
      return LLVMBuildLoad(builder, getUnknownSizeArrayLengthPtr(builder, arrayWrapperPtrLE), "usaLen");
      break;
    }
    default:
      assert(false);
  }
}

LLVMValueRef getUnknownSizeArrayContentsPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    LLVMValueRef arrayRefLE) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      LLVMValueRef arrayWrapperPtrLE = arrayRefLE;
      return LLVMBuildStructGEP(
          builder,
          arrayWrapperPtrLE,
          2, // Array is after the control block and length.
          "usaElemsPtr");
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      LLVMValueRef arrayWrapperPtrLE = nullptr;
      switch (arrayRefM->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
          arrayWrapperPtrLE = arrayRefLE;
          break;
        case Ownership::BORROW:
          arrayWrapperPtrLE =
              lockWeakRef(FL(), globalState, functionState, builder, arrayRefM, arrayRefLE);
          break;
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
          break;
      }
      return LLVMBuildStructGEP(
          builder,
          arrayWrapperPtrLE,
          2, // Array is after the control block and length.
          "usaElemsPtr");
    }
    default:
      assert(false);
  }
}

LLVMValueRef getContentsPtrFromUnknownSizeArrayWrapperPtr(
    LLVMBuilderRef builder,
    LLVMValueRef arrayWrapperPtrLE) {
  return LLVMBuildStructGEP(
      builder,
      arrayWrapperPtrLE,
      2, // Array is after the control block and length.
      "usaElemsPtr");
}

LLVMValueRef loadInnerArrayMember(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef elemsPtrLE,
    Reference* elementRefM,
    LLVMValueRef indexLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(elemsPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      constI64LE(0),
      indexLE
  };
  auto resultLE =
      LLVMBuildLoad(
          builder,
          LLVMBuildGEP(
              builder, elemsPtrLE, indices, 2, "indexPtr"),
          "index");

  return resultLE;
}

LLVMValueRef storeInnerArrayMember(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef elemsPtrLE,
    LLVMValueRef indexLE,
    LLVMValueRef sourceLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(elemsPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      constI64LE(0),
      indexLE
  };
  auto resultLE =
      LLVMBuildStore(
          builder,
          sourceLE,
          LLVMBuildGEP(
              builder, elemsPtrLE, indices, 2, "indexPtr"));

  return resultLE;
}

LLVMValueRef loadElement(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    Reference* elementRefM,
    LLVMValueRef sizeLE,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    LLVMValueRef indexLE,
    Reference* resultRefM) {

  auto isNonNegativeLE = LLVMBuildICmp(builder, LLVMIntSGE, indexLE, constI64LE(0), "isNonNegative");
  auto isUnderLength = LLVMBuildICmp(builder, LLVMIntSLT, indexLE, sizeLE, "isUnderLength");
  auto isWithinBounds = LLVMBuildAnd(builder, isNonNegativeLE, isUnderLength, "isWithinBounds");
  buildFlare(FL(), globalState, functionState, builder, "index: ", indexLE);
  buildFlare(FL(), globalState, functionState, builder, "size: ", sizeLE);
  buildAssert(globalState, functionState, builder, isWithinBounds, "Index out of bounds!");

  LLVMValueRef fromArrayLE = nullptr;
  if (mutability == Mutability::IMMUTABLE) {
    if (structRefM->location == Location::INLINE) {
      assert(false);
//      return LLVMBuildExtractValue(builder, structExpr, indexLE, "index");
      return nullptr;
    } else {
      fromArrayLE = loadInnerArrayMember(globalState, builder, arrayPtrLE, elementRefM, indexLE);
    }
  } else if (mutability == Mutability::MUTABLE) {
    fromArrayLE = loadInnerArrayMember(globalState, builder, arrayPtrLE, elementRefM, indexLE);
  } else {
    assert(false);
    return nullptr;
  }
  return upgradeLoadResultToRefWithTargetOwnership(globalState, functionState, builder, elementRefM,
      resultRefM,
      fromArrayLE);
}


LLVMValueRef storeElement(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    LLVMValueRef sizeLE,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    LLVMValueRef indexLE,
    LLVMValueRef sourceLE) {

  auto isNonNegativeLE = LLVMBuildICmp(builder, LLVMIntSGE, indexLE, constI64LE(0), "isNonNegative");
  auto isUnderLength = LLVMBuildICmp(builder, LLVMIntSLT, indexLE, sizeLE, "isUnderLength");
  auto isWithinBounds = LLVMBuildAnd(builder, isNonNegativeLE, isUnderLength, "isWithinBounds");
  buildAssert(globalState, functionState, builder, isWithinBounds, "Index out of bounds!");

  if (mutability == Mutability::IMMUTABLE) {
    if (arrayRefM->location == Location::INLINE) {
      assert(false);
//      return LLVMBuildExtractValue(builder, structExpr, indexLE, "index");
      return nullptr;
    } else {
      return storeInnerArrayMember(globalState, builder, arrayPtrLE, indexLE, sourceLE);
    }
  } else if (mutability == Mutability::MUTABLE) {
    return storeInnerArrayMember(globalState, builder, arrayPtrLE, indexLE, sourceLE);
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
  LLVMValueRef iterationIndexPtrLE =
      makeMidasLocal(
          functionState,
          builder,
          LLVMInt64Type(),
          "iterationIndex",
          LLVMConstInt(LLVMInt64Type(),0, false));

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
