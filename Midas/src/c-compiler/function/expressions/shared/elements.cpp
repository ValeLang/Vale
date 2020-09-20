#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "utils/branch.h"
#include "elements.h"
#include "utils/counters.h"

LLVMValueRef getKnownSizeArrayContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE knownSizeArrayWrapperPtrLE) {
  return LLVMBuildStructGEP(
      builder,
      knownSizeArrayWrapperPtrLE.refLE,
      1, // Array is after the control block.
      "ksaElemsPtr");
}

LLVMValueRef getUnknownSizeArrayContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE arrayWrapperPtrLE) {

  return LLVMBuildStructGEP(
      builder,
      arrayWrapperPtrLE.refLE,
      2, // Array is after the control block and length.
      "usaElemsPtr");
}

LLVMValueRef getUnknownSizeArrayLengthPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE unknownSizeArrayWrapperPtrLE) {
  auto resultLE =
      LLVMBuildStructGEP(
          builder,
          unknownSizeArrayWrapperPtrLE.refLE,
          1, // Length is after the control block and before contents.
          "usaLenPtr");
  assert(LLVMTypeOf(resultLE) == LLVMPointerType(LLVMInt64Type(), 0));
  return resultLE;
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
  auto resultLE =
      LLVMBuildLoad(
          builder,
          LLVMBuildGEP(
              builder, elemsPtrLE, indices, 2, "indexPtr"),
          "index");

  return resultLE;
}

void storeInnerArrayMember(
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
  LLVMBuildStore(
      builder,
      sourceLE,
      LLVMBuildGEP(
          builder, elemsPtrLE, indices, 2, "indexPtr"));
}

Ref loadElementWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    Ref sizeRef,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    Ref indexRef) {
  auto indexLE = globalState->region->checkValidReference(FL(), functionState, builder, globalState->metalCache.intRef, indexRef);
  auto sizeLE = globalState->region->checkValidReference(FL(), functionState, builder, globalState->metalCache.intRef, sizeRef);

  auto isNonNegativeLE = LLVMBuildICmp(builder, LLVMIntSGE, indexLE, constI64LE(0), "isNonNegative");
  auto isUnderLength = LLVMBuildICmp(builder, LLVMIntSLT, indexLE, sizeLE, "isUnderLength");
  auto isWithinBounds = LLVMBuildAnd(builder, isNonNegativeLE, isUnderLength, "isWithinBounds");
  buildFlare(FL(), globalState, functionState, builder, "index: ", indexLE);
  buildFlare(FL(), globalState, functionState, builder, "size: ", sizeLE);
  buildAssert(globalState, functionState, builder, isWithinBounds, "Index out of bounds!");

  LLVMValueRef fromArrayLE = nullptr;
  if (mutability == Mutability::IMMUTABLE) {
    if (arrayRefM->location == Location::INLINE) {
      assert(false);
//      return LLVMBuildExtractValue(builder, structExpr, indexLE, "index");
    } else {
      fromArrayLE = loadInnerArrayMember(builder, arrayPtrLE, indexLE);
    }
  } else if (mutability == Mutability::MUTABLE) {
    fromArrayLE = loadInnerArrayMember(builder, arrayPtrLE, indexLE);
  } else {
    assert(false);
  }

  {
    // Careful here! This is a bit cheaty; we shouldn't pretend we have the source reference,
    // because we don't. We're *reading* from it, but by wrapping it, we're pretending we *have* it.
    // We're only doing this here so we can feed it to checkValidReference, and immediately throwing
    // it away.
    auto sourceRef = wrap(functionState->defaultRegion, elementRefM, fromArrayLE);
    globalState->region->checkValidReference(FL(), functionState, builder, elementRefM, sourceRef);
    return sourceRef;
  }
}

//Ref loadElementWithUpgrade(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    BlockState* blockState,
//    LLVMBuilderRef builder,
//    Reference* arrayRefM,
//    Reference* elementRefM,
//    Ref sizeRef,
//    LLVMValueRef arrayPtrLE,
//    Mutability mutability,
//    Ref indexRef,
//    Reference* resultRefM) {
//  auto fromArrayRef =
//      loadElementWithoutUpgrade(
//          globalState, functionState, builder, arrayRefM, elementRefM, sizeRef, arrayPtrLE, mutability, indexRef);
//  return upgradeLoadResultToRefWithTargetOwnership(globalState, functionState, builder, elementRefM,
//      resultRefM,
//      fromArrayRef);
//}


Ref storeElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    Ref sizeRef,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    Ref indexRef,
    Ref sourceRef) {
  auto sizeLE = globalState->region->checkValidReference(FL(), functionState, builder, globalState->metalCache.intRef, sizeRef);

  auto indexLE = globalState->region->checkValidReference(FL(), functionState, builder, globalState->metalCache.intRef, indexRef);
  auto isNonNegativeLE = LLVMBuildICmp(builder, LLVMIntSGE, indexLE, constI64LE(0), "isNonNegative");
  auto isUnderLength = LLVMBuildICmp(builder, LLVMIntSLT, indexLE, sizeLE, "isUnderLength");
  auto isWithinBounds = LLVMBuildAnd(builder, isNonNegativeLE, isUnderLength, "isWithinBounds");
  buildAssert(globalState, functionState, builder, isWithinBounds, "Index out of bounds!");

//  auto arrayPtrLE = globalState->region->checkValidReference(FL(), functionState, builder, arrayRefM, arrayRef);
  auto sourceLE = globalState->region->checkValidReference(FL(), functionState, builder, elementRefM, sourceRef);

  if (mutability == Mutability::IMMUTABLE) {
    if (arrayRefM->location == Location::INLINE) {
      assert(false);
//      return LLVMBuildExtractValue(builder, structExpr, indexLE, "index");
    } else {
//      auto arrayWrapperPtrLE = getUnknownSizeArrayWrapperPtr(globalState, functionState, builder, arrayRefM, arrayRef);
//      LLVMValueRef arrayPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);

      auto resultLE = loadElementWithoutUpgrade(globalState, functionState, builder, arrayRefM, elementRefM, sizeRef, arrayPtrLE, mutability, indexRef);

      storeInnerArrayMember(globalState, builder, arrayPtrLE, indexLE, sourceLE);
      return resultLE;
    }
  } else if (mutability == Mutability::MUTABLE) {
    auto resultLE = loadInnerArrayMember(builder, arrayPtrLE, indexLE);
    storeInnerArrayMember(globalState, builder, arrayPtrLE, indexLE, sourceLE);
    return wrap(functionState->defaultRegion, elementRefM, resultLE);
  } else {
    assert(false);
  }
}


void foreachArrayElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sizeRef,
    std::function<void(Ref, LLVMBuilderRef)> iterationBuilder) {
  LLVMValueRef iterationIndexPtrLE =
      makeMidasLocal(
          functionState,
          builder,
          LLVMInt64Type(),
          "iterationIndex",
          LLVMConstInt(LLVMInt64Type(),0, false));

  auto sizeLE = globalState->region->checkValidReference(FL(), functionState, builder, globalState->metalCache.intRef, sizeRef);

  buildWhile(
      globalState,
      functionState,
      builder,
      [globalState, functionState, sizeLE, iterationIndexPtrLE](LLVMBuilderRef conditionBuilder) {
        auto iterationIndexLE =
            LLVMBuildLoad(conditionBuilder, iterationIndexPtrLE, "iterationIndex");
        auto isBeforeEndLE =
            LLVMBuildICmp(
                conditionBuilder,LLVMIntSLT,iterationIndexLE,sizeLE,"iterationIndexIsBeforeEnd");
        return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, isBeforeEndLE);
      },
      [globalState, functionState, iterationBuilder, iterationIndexPtrLE](LLVMBuilderRef bodyBuilder) {
        auto iterationIndexLE = LLVMBuildLoad(bodyBuilder, iterationIndexPtrLE, "iterationIndex");
        auto iterationIndexRef = wrap(functionState->defaultRegion, globalState->metalCache.intRef, iterationIndexLE);
        iterationBuilder(iterationIndexRef, bodyBuilder);
        adjustCounter(bodyBuilder, iterationIndexPtrLE, 1);
      });
}

