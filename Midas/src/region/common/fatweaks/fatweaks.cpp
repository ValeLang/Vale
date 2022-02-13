#include <llvm-c/Types.h>
#include "../../../globalstate.h"
#include "../../../function/function.h"
#include "../../../function/expressions/shared/shared.h"
#include "../controlblock.h"
#include "fatweaks.h"

constexpr int WEAK_REF_MEMBER_INDEX_FOR_HEADER = 0;
constexpr int WEAK_REF_MEMBER_INDEX_FOR_OBJPTR = 1;

// Dont use this function for V2
LLVMValueRef FatWeaks::getInnerRefFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    WeakFatPtrLE weakFatPtrLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V3: case RegionOverride::RESILIENT_V4:
      assert(
          weakRefM->ownership == Ownership::BORROW ||
              weakRefM->ownership == Ownership::WEAK);
      break;
    case RegionOverride::FAST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
      assert(weakRefM->ownership == Ownership::WEAK);
      break;
    default:
      assert(false);
      break;
  }

//  globalState->getRegion(refHere)->checkValidReference(FL(), functionState, builder, weakRefM, weakFatPtrLE);

  auto innerRefLE = LLVMBuildExtractValue(builder, weakFatPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR, "");
  // We dont check that its valid because if it's a weak ref, it might *not* be pointing at
  // a valid reference.
  return innerRefLE;
}

LLVMValueRef FatWeaks::getInnerRefFromWeakRefWithoutCheck(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    WeakFatPtrLE weakRefLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V3: case RegionOverride::RESILIENT_V4:
      assert(
          weakRefM->ownership == Ownership::BORROW ||
              weakRefM->ownership == Ownership::WEAK);
      break;
    case RegionOverride::FAST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
      assert(weakRefM->ownership == Ownership::WEAK);
      break;
    default:
      assert(false);
      break;
  }

  auto innerRefLE = LLVMBuildExtractValue(builder, weakRefLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR, "");
  // We dont check that its valid because if it's a weak ref, it might *not* be pointing at
  // a valid reference.
  return innerRefLE;
}

LLVMValueRef FatWeaks::getHeaderFromWeakRef(
    LLVMBuilderRef builder,
    WeakFatPtrLE weakRefLE) {
  return LLVMBuildExtractValue(builder, weakRefLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "weakRefHeader");
}

WeakFatPtrLE FatWeaks::assembleWeakFatPtr(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    LLVMTypeRef weakRefStruct,
    LLVMValueRef headerLE,
    LLVMValueRef innerRefLE) {
  auto weakRefLE = LLVMGetUndef(weakRefStruct);
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, innerRefLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,"");
  return weakRefStructsSource->makeWeakFatPtr(weakRefMT, weakRefLE);
}

// Used in interface calling, when we dont know what the underlying struct type is yet.
WeakFatPtrLE FatWeaks::assembleVoidStructWeakRef(
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtrLE,
    LLVMValueRef headerLE) {
  auto objVoidPtrLE =
      LLVMBuildPointerCast(
          builder,
          controlBlockPtrLE.refLE,
          LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0),
          "objAsVoidPtr");

  auto weakRefLE = LLVMGetUndef(weakRefStructsSource->getWeakVoidRefStruct(refM->kind));
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
  weakRefLE =
      LLVMBuildInsertValue(builder, weakRefLE, objVoidPtrLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR, "");

  return weakRefStructsSource->makeWeakFatPtr(refM, weakRefLE);
}
