#include <iostream>
#include <utils/call.h>

#include "../../../translatetype.h"

#include "shared.h"
#include "../../../utils/branch.h"
#include "elements.h"
#include "../../../utils/counters.h"
#include <region/common/migration.h>

InBoundsLE checkIndexInBounds(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* intRefMT,
    Ref sizeRef,
    LLVMValueRef indexLE,
    const char* failMessage) {
  auto sizeLE =
      globalState->getRegion(intRefMT)
          ->checkValidReference(FL(), functionState, builder, true, intRefMT, sizeRef);
  auto outOfBounds = LLVMBuildICmp(builder, LLVMIntUGE, indexLE, sizeLE, "outOfBounds");
  buildIfNever(
      globalState, functionState->containingFuncL, builder, outOfBounds,
      [globalState, failMessage](LLVMBuilderRef bodyBuilder) {
        if (globalState->opt->includeBoundsChecks) {
          buildPrintToStderr(globalState, bodyBuilder, failMessage);
          globalState->externs->exit.call(bodyBuilder, {constI64LE(globalState, 1)}, "");
        }
        // buildIfNever will still put in an unreachable here.
      });

  return InBoundsLE{indexLE};
}

InBoundsLE checkLastElementExists(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sizeRef,
    const char* failMessage) {
  auto intRefMT = globalState->metalCache->i32Ref;
  auto sizeLE =
      globalState->getRegion(intRefMT)
          ->checkValidReference(FL(), functionState, builder, true, intRefMT, sizeRef);

  auto indexLE = LLVMBuildSub(builder, sizeLE, constI32LE(globalState, 1), "index");

  auto zeroLE = constI32LE(globalState, 0);
  auto isEmpty = LLVMBuildICmp(builder, LLVMIntEQ, sizeLE, zeroLE, "empty");
  buildIfNever(
      globalState, functionState->containingFuncL, builder, isEmpty,
      [globalState, failMessage](LLVMBuilderRef bodyBuilder) {
        if (globalState->opt->includeBoundsChecks) {
          buildPrintToStderr(globalState, bodyBuilder, failMessage);
          globalState->externs->exit.call(bodyBuilder, {constI64LE(globalState, 1)}, "");
        }
        // buildIfNever will still put in an unreachable here.
      });

  return InBoundsLE{indexLE};
}

void checkArrayEmpty(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sizeRef,
    const char* failMessage) {
  auto intRefMT = globalState->metalCache->i32Ref;
  auto sizeLE =
      globalState->getRegion(intRefMT)
          ->checkValidReference(FL(), functionState, builder, true, intRefMT, sizeRef);

  auto zeroLE = constI32LE(globalState, 0);
  auto nonEmpty = LLVMBuildICmp(builder, LLVMIntNE, sizeLE, zeroLE, "nonEmpty");
  buildIfNever(
      globalState, functionState->containingFuncL, builder, nonEmpty,
      [globalState, failMessage](LLVMBuilderRef bodyBuilder) {
        if (globalState->opt->includeBoundsChecks) {
          buildPrintToStderr(globalState, bodyBuilder, failMessage);
          globalState->externs->exit.call(bodyBuilder, {constI64LE(globalState, 1)}, "");
        }
        // buildIfNever will still put in an unreachable here.
      });
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

void initializeElementInRSAWithoutIncrementSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool capacityExists,
    RuntimeSizedArrayT* rsaMT,
    Reference* rsaRefMT,
    WrapperPtrLE rsaWPtrLE,
    InBoundsLE indexLE,
    Ref elementRef,
    IncrementedSize incrementedSize) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  auto arrayElementsPtrLE = getRuntimeSizedArrayContentsPtr(builder, capacityExists, rsaWPtrLE);
  ::initializeElementWithoutIncrementSize(
      globalState, functionState, builder, rsaRefMT->location,
      rsaDef->elementType, arrayElementsPtrLE, indexLE, elementRef, incrementedSize);
}

IncrementedSize incrementRSASize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    WrapperPtrLE rsaWPtrLE) {
  auto sizePtrLE = ::getRuntimeSizedArrayLengthPtr(globalState, builder, rsaWPtrLE);
  assert(rsaRefMT->location != Location::INLINE); // impl

  adjustCounterV(globalState, builder, globalState->metalCache->i32, sizePtrLE, 1, false);

//  // Manually making an InBoundsLE because we know it's in bounds because it's the previous size.
//  return InBoundsLE{indexLE};

  return IncrementedSize{};
}

Ref getRuntimeSizedArrayLength(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE arrayRefLE) {
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  auto lengthPtrLE = getRuntimeSizedArrayLengthPtr(globalState, builder, arrayRefLE);
  auto intLE = LLVMBuildLoad2(builder, int32LT, lengthPtrLE, "rsaLen");
  return toRef(globalState->getRegion(globalState->metalCache->i32Ref), globalState->metalCache->i32Ref, intLE);
}

void regularInitializeElementInSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* ssaRefMT,
    Reference* elementType,
    LiveRef arrayRef,
    InBoundsLE indexLE,
    Ref elementRef) {
  auto arrayWPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, ssaRefMT, arrayRef.refLE);
  auto arrayElementsPtrLE = getStaticSizedArrayContentsPtr(builder,arrayWPtrLE);
  buildFlare(FL(), globalState, functionState, builder);
  initializeElementWithoutIncrementSize(
      globalState, functionState, builder, ssaRefMT->location,
      elementType, arrayElementsPtrLE, indexLE, elementRef,
      // Manually making an IncrementedSize because it's an SSA.
      IncrementedSize{});
}

LoadResult regularloadElementFromSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    Reference* elementType,
    LiveRef arrayRef,
    InBoundsLE indexLE,
    KindStructs* kindStructs) {
  auto arrayWPtrLE =
      kindStructs->makeWrapperPtr(FL(), functionState, builder, ssaRefMT, arrayRef.refLE);
  LLVMValueRef arrayElementsPtrLE = getStaticSizedArrayContentsPtr(builder,arrayWPtrLE);
  return loadElementFromSSAInner(
      globalState, functionState, builder, elementType, indexLE, arrayElementsPtrLE);
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
  adjustCounterV(globalState, builder, globalState->metalCache->i32, sizePtrLE, -1, false);
}

LoadResult loadElementFromSSAInner(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* elementType,
    InBoundsLE indexLE,
    LLVMValueRef arrayElementsPtrLE) {
  return loadElement(
      globalState, functionState, builder, arrayElementsPtrLE, elementType, indexLE);
}

LoadResult regularLoadElementFromRSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    bool capacityExists,
    Reference* rsaRefMT,
    Reference* elementType,
    LiveRef arrayRef,
    InBoundsLE indexLE) {
  auto arrayWPtrLE = toWrapperPtr(functionState, builder, kindStructs, rsaRefMT, arrayRef);
  auto arrayElementsPtrLE =
      getRuntimeSizedArrayContentsPtr(builder, capacityExists, arrayWPtrLE);
  buildFlare(FL(), globalState, functionState, builder);
  return loadElement(
      globalState, functionState, builder, arrayElementsPtrLE, elementType, indexLE);
}


LoadResult loadElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef elemsPtrLE,
    Reference* elementRefM,
    InBoundsLE indexLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(elemsPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      constI32LE(globalState, 0),
      indexLE.refLE
  };

  auto elementLT = globalState->getRegion(elementRefM)->translateType(elementRefM);
  auto elementPtrLE =
      LLVMBuildInBoundsGEP2(builder, LLVMArrayType(elementLT, 0), elemsPtrLE, indices, 2, "indexPtr");
  auto fromArrayLE = LLVMBuildLoad2(builder, elementLT, elementPtrLE, "index");

  auto sourceRef = toRef(globalState->getRegion(elementRefM), elementRefM, fromArrayLE);
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
    InBoundsLE indexLE,
    LLVMValueRef sourceLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(elemsPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      constI32LE(globalState, 0),
      indexLE.refLE
  };
  auto destPtrLE =
      LLVMBuildInBoundsGEP2(builder, LLVMArrayType(elementLT, 0), elemsPtrLE, indices, 2, "destPtr");
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
    LLVMValueRef arrayElemsPtrLE,
    InBoundsLE indexLE,
    Ref sourceRef) {
  assert(location != Location::INLINE); // impl

  auto sourceLE =
      globalState->getRegion(elementRefM)
          ->checkValidReference(FL(), functionState, builder, false, elementRefM, sourceRef);
  auto elementLT = globalState->getRegion(elementRefM)->translateType(elementRefM);

  buildFlare(FL(), globalState, functionState, builder);

  auto resultLE = loadElement(globalState, functionState, builder, arrayElemsPtrLE, elementRefM, indexLE);
  storeInnerArrayMember(globalState, functionState, builder, elementLT, arrayElemsPtrLE, indexLE, sourceLE);

  return resultLE.move();
}


void initializeElementWithoutIncrementSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    Reference* elementRefM,
    LLVMValueRef elemsPtrLE,
    InBoundsLE indexLE,
    Ref sourceRef,
    IncrementedSize incrementedSize) {
  // Ignore incrementedSize, it's just a reminder to the caller.

  assert(location != Location::INLINE); // impl

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
        auto iterationIndexRef = toRef(globalState->getRegion(globalState->metalCache->i32Ref), globalState->metalCache->i32Ref, iterationIndexLE);
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
            LLVMBuildICmp(
                conditionBuilder, LLVMIntULT, iterationIndexLE, sizeLE,
                "iterationIndexIsBeforeEnd");
        return isBeforeEndLE;
      },
      [globalState, iterationBuilder, int32LT, iterationIndexPtrLE](LLVMBuilderRef bodyBuilder) {
        auto iterationIndexLE = LLVMBuildLoad2(bodyBuilder, int32LT, iterationIndexPtrLE, "iterationIndex");
        iterationBuilder(iterationIndexLE, bodyBuilder);
        adjustCounterV(
            globalState, bodyBuilder, globalState->metalCache->i32, iterationIndexPtrLE, 1, false);
      });
}


void intRangeLoopReverse(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* intRefMT,
    LLVMValueRef sizeLE,
    std::function<void(LLVMValueRef, LLVMBuilderRef)> iterationBuilder) {
  auto innt = dynamic_cast<Int*>(intRefMT->kind);
  auto intLT = LLVMIntTypeInContext(globalState->context, innt->bits);

  LLVMValueRef iterationIndexPtrLE =
      makeBackendLocal(functionState, builder, intLT, "iterationIndex", sizeLE);

  buildBoolyWhile(
      globalState,
      functionState->containingFuncL,
      builder,
      [globalState, innt, iterationIndexPtrLE, iterationBuilder](LLVMBuilderRef bodyBuilder) {
        auto iterationIndexLE =
            adjustCounterV(
                globalState, bodyBuilder, innt, iterationIndexPtrLE, -1, false);
        iterationBuilder(iterationIndexLE, bodyBuilder);
        auto isBeforeEndLE =
            LLVMBuildICmp(
                bodyBuilder, LLVMIntNE, iterationIndexLE, constI32LE(globalState, 0),
                "iterationIndexIsBeforeEnd");
        return isBeforeEndLE;
      });
}

void intRangeLoopReverseV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* intRefMT,
    Ref sizeRef,
    std::function<void(Ref, LLVMBuilderRef)> iterationBuilder) {
  auto sizeLE =
      globalState->getRegion(intRefMT)
          ->checkValidReference(FL(), functionState, builder, true, intRefMT, sizeRef);

  intRangeLoopReverse(
      globalState, functionState, builder, intRefMT, sizeLE,
      [globalState, iterationBuilder, intRefMT](LLVMValueRef indexLE, LLVMBuilderRef bodyBuilder) {
        auto indexRef = toRef(globalState->getRegion(intRefMT), intRefMT, indexLE);
        iterationBuilder(indexRef, bodyBuilder);
      });
}
