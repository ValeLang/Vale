#include <iostream>

#include "../../../translatetype.h"

#include "shared.h"
#include "../../../utils/branch.h"
#include "elements.h"
#include "../../../utils/counters.h"
#include <region/common/migration.h>

LLVMValueRef checkIndexInBounds(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Int* intMT,
    Ref sizeRef,
    Ref indexRef) {
  auto inntRefMT = globalState->metalCache->getReference(Ownership::SHARE, Location::INLINE, intMT);
  auto sizeLE =
      globalState->getRegion(inntRefMT)
          ->checkValidReference(FL(), functionState, builder, true, inntRefMT, sizeRef);
  auto indexLE =
      globalState->getRegion(inntRefMT)
          ->checkValidReference(FL(), functionState, builder, true, inntRefMT, indexRef);
  auto isNonNegativeLE = LLVMBuildICmp(builder, LLVMIntSGE, indexLE, constI32LE(globalState, 0), "isNonNegative");
  auto isUnderLength = LLVMBuildICmp(builder, LLVMIntSLT, indexLE, sizeLE, "isUnderLength");
  auto isWithinBounds = LLVMBuildAnd(builder, isNonNegativeLE, isUnderLength, "isWithinBounds");
  buildAssertV(globalState, functionState, builder, isWithinBounds, "Index out of bounds!");

  return indexLE;
}

LLVMValueRef getStaticSizedArrayContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE staticSizedArrayWrapperPtrLE) {
  return LLVMBuildStructGEP2(
      builder,
      staticSizedArrayWrapperPtrLE.wrapperStructLT,
      staticSizedArrayWrapperPtrLE.refLE,
      1, // Array is after the control block.
      "ssaElemsPtr");
}

LLVMValueRef getRuntimeSizedArrayContentsPtr(
    LLVMBuilderRef builder,
    bool capacityExists,
    WrapperPtrLE arrayWrapperPtrLE) {
  auto numThingsBefore =
      1 + // control block
      1 + // size
      (capacityExists ? 1 : 0); // capacity
  int index = numThingsBefore;

  return LLVMBuildStructGEP2(
      builder,
      arrayWrapperPtrLE.wrapperStructLT,
      arrayWrapperPtrLE.refLE,
      index,
      "rsaElemsPtr");
}

LLVMValueRef getRuntimeSizedArrayLengthPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    WrapperPtrLE runtimeSizedArrayWrapperPtrLE) {
  auto resultLE =
      LLVMBuildStructGEP2(
          builder,
          runtimeSizedArrayWrapperPtrLE.wrapperStructLT,
          runtimeSizedArrayWrapperPtrLE.refLE,
          1, // Length is after the control block and before the capacity.
          "rsaLenPtr");
  assert(LLVMTypeOf(resultLE) == LLVMPointerType(LLVMInt32TypeInContext(globalState->context), 0));
  return resultLE;
}

LLVMValueRef getRuntimeSizedArrayCapacityPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    WrapperPtrLE runtimeSizedArrayWrapperPtrLE) {
  auto resultLE =
      LLVMBuildStructGEP2(
          builder,
          runtimeSizedArrayWrapperPtrLE.wrapperStructLT,
          runtimeSizedArrayWrapperPtrLE.refLE,
          2, // Length is after the control block and before contents.
          "rsaCapacityPtr");
  assert(LLVMTypeOf(resultLE) == LLVMPointerType(LLVMInt32TypeInContext(globalState->context), 0));
  return resultLE;
}

void decrementRSASize(GlobalState* globalState, FunctionState *functionState, KindStructs* kindStructs, LLVMBuilderRef builder, Reference *rsaRefMT, WrapperPtrLE rsaWrapperPtrLE) {
  auto sizePtrLE = getRuntimeSizedArrayLengthPtr(globalState, builder, rsaWrapperPtrLE);
  adjustCounter(globalState, builder, globalState->metalCache->i32, sizePtrLE, -1);
}


LoadResult loadElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef elemsPtrLE,
    Reference* elementRefM,
    Ref sizeRef,
    Ref indexRef) {
  auto indexLE = checkIndexInBounds(globalState, functionState, builder, globalState->metalCache->i32, sizeRef, indexRef);

  assert(LLVMGetTypeKind(LLVMTypeOf(elemsPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      constI32LE(globalState, 0),
      indexLE
  };

  auto elementLT = globalState->getRegion(elementRefM)->translateType(elementRefM);
  auto elementPtrLE =
      LLVMBuildGEP2(builder, LLVMArrayType(elementLT, 0), elemsPtrLE, indices, 2, "indexPtr");
  auto fromArrayLE = LLVMBuildLoad2(builder, elementLT, elementPtrLE, "index");

  auto sourceRef = wrap(globalState->getRegion(elementRefM), elementRefM, fromArrayLE);
  globalState->getRegion(elementRefM)
      ->checkValidReference(FL(), functionState, builder, false, elementRefM, sourceRef);
  return LoadResult{sourceRef};
}

void storeInnerArrayMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMTypeRef elementLT,
    LLVMValueRef elemsPtrLE,
    LLVMValueRef indexLE,
    LLVMValueRef sourceLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(elemsPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      constI32LE(globalState, 0),
      indexLE
  };
  auto destPtrLE =
      LLVMBuildGEP2(builder, LLVMArrayType(elementLT, 0), elemsPtrLE, indices, 2, "destPtr");
  LLVMBuildStore(builder, sourceLE, destPtrLE);
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
//      loadElement(
//          globalState, functionState, builder, arrayRefM, elementRefM, sizeRef, arrayPtrLE, mutability, indexRef);
//  return upgradeLoadResultToRefWithTargetOwnership(globalState, functionState, builder, elementRefM,
//      resultRefM,
//      fromArrayRef);
//}

Ref swapElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    Reference* elementRefM,
    Ref sizeRef,
    LLVMValueRef arrayPtrLE,
    Ref indexRef,
    Ref sourceRef) {
  assert(location != Location::INLINE); // impl

  auto indexLE =
      checkIndexInBounds(globalState, functionState, builder, globalState->metalCache->i32, sizeRef, indexRef);
  auto sourceLE =
      globalState->getRegion(elementRefM)
          ->checkValidReference(FL(), functionState, builder, false, elementRefM, sourceRef);
  auto elementLT = globalState->getRegion(elementRefM)->translateType(elementRefM);

  buildFlare(FL(), globalState, functionState, builder);
  auto resultLE = loadElement(globalState, functionState, builder, arrayPtrLE, elementRefM, sizeRef, indexRef);
  storeInnerArrayMember(globalState, functionState, builder, elementLT, arrayPtrLE, indexLE, sourceLE);
  return resultLE.move();
}

void initializeElementAndIncrementSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    Reference* elementRefM,
    LLVMValueRef sizePtrLE,
    LLVMValueRef elemsPtrLE,
    Ref indexRef,
    Ref sourceRef) {
  assert(location != Location::INLINE); // impl

  auto sizeLE = adjustCounter(globalState, builder, globalState->metalCache->i32, sizePtrLE, 1);
  auto sizeRef = wrap(globalState->getRegion(globalState->metalCache->i32), globalState->metalCache->i32Ref, sizeLE);

  auto indexLE = checkIndexInBounds(globalState, functionState, builder, globalState->metalCache->i32, sizeRef, indexRef);
  auto sourceLE =
      globalState->getRegion(elementRefM)
          ->checkValidReference(FL(), functionState, builder, false, elementRefM, sourceRef);
  auto elementLT = globalState->getRegion(elementRefM)->translateType(elementRefM);
  storeInnerArrayMember(
      globalState, functionState, builder, elementLT, elemsPtrLE, indexLE, sourceLE);
}

void initializeElementWithoutIncrementSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    Reference* elementRefM,
    Ref sizeRef,
    LLVMValueRef elemsPtrLE,
    Ref indexRef,
    Ref sourceRef) {
  assert(location != Location::INLINE); // impl

  auto indexLE = checkIndexInBounds(globalState, functionState, builder, globalState->metalCache->i32, sizeRef, indexRef);
  auto sourceLE =
      globalState->getRegion(elementRefM)
          ->checkValidReference(FL(), functionState, builder, false, elementRefM, sourceRef);
  auto elementLT = globalState->getRegion(elementRefM)->translateType(elementRefM);
  storeInnerArrayMember(globalState, functionState, builder, elementLT, elemsPtrLE, indexLE, sourceLE);
}

void intRangeLoopV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sizeRef,
    std::function<void(Ref, LLVMBuilderRef)> iterationBuilder) {
  auto sizeLE =
      globalState->getRegion(globalState->metalCache->i32Ref)
          ->checkValidReference(FL(), functionState, builder, true, globalState->metalCache->i32Ref, sizeRef);
  intRangeLoop(
      globalState, functionState, builder, sizeLE,
      [globalState, iterationBuilder](LLVMValueRef iterationIndexLE, LLVMBuilderRef builder) {
        auto iterationIndexRef = wrap(globalState->getRegion(globalState->metalCache->i32Ref), globalState->metalCache->i32Ref, iterationIndexLE);
        iterationBuilder(iterationIndexRef, builder);
      });
}

void intRangeLoop(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sizeLE,
    std::function<void(LLVMValueRef, LLVMBuilderRef)> iterationBuilder) {
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  assert(LLVMTypeOf(sizeLE) == int32LT);

  LLVMValueRef iterationIndexPtrLE =
      makeBackendLocal(
          functionState,
          builder,
          int32LT,
          "iterationIndex",
          constI32LE(globalState, 0));

  buildWhile(
      globalState,
      functionState,
      builder,
      [globalState, int32LT, sizeLE, iterationIndexPtrLE](LLVMBuilderRef conditionBuilder) {
        auto iterationIndexLE =
            LLVMBuildLoad2(conditionBuilder, int32LT, iterationIndexPtrLE, "iterationIndex");
        auto isBeforeEndLE =
            LLVMBuildICmp(conditionBuilder, LLVMIntSLT, iterationIndexLE, sizeLE, "iterationIndexIsBeforeEnd");
        return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isBeforeEndLE);
      },
      [globalState, iterationBuilder, int32LT, iterationIndexPtrLE](LLVMBuilderRef bodyBuilder) {
        auto iterationIndexLE = LLVMBuildLoad2(bodyBuilder, int32LT, iterationIndexPtrLE, "iterationIndex");
        iterationBuilder(iterationIndexLE, bodyBuilder);
        adjustCounter(globalState, bodyBuilder, globalState->metalCache->i32, iterationIndexPtrLE, 1);
      });
}


void intRangeLoopReverseV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Int* innt,
    Ref sizeRef,
    std::function<void(Ref, LLVMBuilderRef)> iterationBuilder) {
  auto intLT = LLVMIntTypeInContext(globalState->context, innt->bits);
  auto inntRefMT = globalState->metalCache->getReference(Ownership::SHARE, Location::INLINE, innt);
  auto sizeLE =
      globalState->getRegion(inntRefMT)
          ->checkValidReference(FL(), functionState, builder, true, inntRefMT, sizeRef);

  LLVMValueRef iterationIndexPtrLE =
      makeBackendLocal(functionState, builder, intLT, "iterationIndex", sizeLE);

  buildWhile(
      globalState,
      functionState,
      builder,
      [globalState, iterationIndexPtrLE, innt, intLT](LLVMBuilderRef conditionBuilder) {
        auto iterationIndexLE =
            LLVMBuildLoad2(conditionBuilder, intLT, iterationIndexPtrLE, "iterationIndex");
        auto zeroLE = LLVMConstInt(LLVMIntTypeInContext(globalState->context, innt->bits), 0, false);
        auto isBeforeEndLE =
            LLVMBuildICmp(conditionBuilder, LLVMIntSGT, iterationIndexLE, zeroLE, "iterationIndexIsBeforeEnd");
        return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isBeforeEndLE);
      },
      [globalState, iterationBuilder, innt, intLT, inntRefMT, iterationIndexPtrLE](LLVMBuilderRef bodyBuilder) {
        adjustCounter(globalState, bodyBuilder, innt, iterationIndexPtrLE, -1);
        auto iterationIndexLE = LLVMBuildLoad2(bodyBuilder, intLT, iterationIndexPtrLE, "iterationIndex");
        auto iterationIndexRef = wrap(globalState->getRegion(inntRefMT), inntRefMT, iterationIndexLE);
        iterationBuilder(iterationIndexRef, bodyBuilder);
      });
}

