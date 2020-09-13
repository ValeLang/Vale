#include "controlblock.h"
#include "function/expressions/shared/shared.h"
#include "utils/counters.h"

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef getStrongRcPtrFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      assert(refM->ownership == Ownership::SHARE);
      break;
    default:
      assert(false);
  }

  return LLVMBuildStructGEP(
      builder,
      controlBlockPtr.refLE,
      globalState->region->getControlBlock(refM->referend)->getMemberIndex(ControlBlockMember::STRONG_RC),
      "rcPtr");
}

LLVMValueRef getObjIdFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtr) {
  assert(globalState->opt->census);
  return LLVMBuildLoad(
      builder,
      LLVMBuildStructGEP(
          builder,
          controlBlockPtr.refLE,
          globalState->region->getControlBlock(referendM)->getMemberIndex(ControlBlockMember::CENSUS_OBJ_ID),
          "objIdPtr"),
      "objId");
}

LLVMValueRef getTypeNameStrPtrFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
  return LLVMBuildLoad(
      builder,
      LLVMBuildStructGEP(
          builder,
          controlBlockPtr.refLE,
          globalState->region->getControlBlock(refM->referend)->getMemberIndex(ControlBlockMember::CENSUS_TYPE_STR),
          "typeNameStrPtrPtr"),
      "typeNameStrPtr");
}

LLVMValueRef getStrongRcFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE structExpr) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      assert(refM->ownership == Ownership::SHARE);
      break;
    default:
      assert(false);
  }

  auto rcPtrLE = getStrongRcPtrFromControlBlockPtr(globalState, builder, refM, structExpr);
  return LLVMBuildLoad(builder, rcPtrLE, "rc");
}

ControlBlockPtrLE getConcreteControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    WrapperPtrLE wrapperPtrLE) {
  // Control block is always the 0th element of every concrete struct.
  return globalState->region->makeControlBlockPtr(wrapperPtrLE.refM->referend, LLVMBuildStructGEP(builder, wrapperPtrLE.refLE, 0, "controlPtr"));
}

ControlBlockPtrLE getControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    InterfaceFatPtrLE interfaceFatPtrLE) {
  // Interface fat pointer's first element points directly at the control block,
  // and we dont have to cast it. We would have to cast if we were accessing the
  // actual object though.
  return globalState->region->makeControlBlockPtr(interfaceFatPtrLE.refM->referend, LLVMBuildExtractValue(builder, interfaceFatPtrLE.refLE, 0, "controlPtr"));
}

ControlBlockPtrLE getControlBlockPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    Ref ref,
    Reference* referenceM) {
  auto referendM = referenceM->referend;
  if (dynamic_cast<InterfaceReferend*>(referendM)) {
    auto referenceLE =
        functionState->defaultRegion->makeInterfaceFatPtr(
            referenceM,
            globalState->region->checkValidReference(FL(), functionState, builder, referenceM, ref));
    return getControlBlockPtr(globalState, builder, referenceLE);
  } else if (dynamic_cast<StructReferend*>(referendM)) {
    auto referenceLE =
        functionState->defaultRegion->makeWrapperPtr(
            referenceM,
            globalState->region->checkValidReference(FL(), functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(globalState, builder, referenceLE);
  } else if (dynamic_cast<KnownSizeArrayT*>(referendM)) {
    auto referenceLE =
        functionState->defaultRegion->makeWrapperPtr(
            referenceM,
            globalState->region->checkValidReference(FL(), functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(globalState, builder, referenceLE);
  } else if (dynamic_cast<UnknownSizeArrayT*>(referendM)) {
    auto referenceLE =
        functionState->defaultRegion->makeWrapperPtr(
            referenceM,
            globalState->region->checkValidReference(FL(), functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(globalState, builder, referenceLE);
  } else if (dynamic_cast<Str*>(referendM)) {
    auto referenceLE =
        functionState->defaultRegion->makeWrapperPtr(
            referenceM,
            globalState->region->checkValidReference(FL(), functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(globalState, builder, referenceLE);
  } else {
    assert(false);
  }
}

ControlBlockPtrLE getControlBlockPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  auto referendM = referenceM->referend;
  if (dynamic_cast<InterfaceReferend*>(referendM)) {
    auto referenceLE = functionState->defaultRegion->makeInterfaceFatPtr(referenceM, ref);
    return getControlBlockPtr(globalState, builder, referenceLE);
  } else if (dynamic_cast<StructReferend*>(referendM)) {
    auto referenceLE = functionState->defaultRegion->makeWrapperPtr(referenceM, ref);
    return getConcreteControlBlockPtr(globalState, builder, referenceLE);
  } else if (dynamic_cast<KnownSizeArrayT*>(referendM)) {
    auto referenceLE = functionState->defaultRegion->makeWrapperPtr(referenceM, ref);
    return getConcreteControlBlockPtr(globalState, builder, referenceLE);
  } else if (dynamic_cast<UnknownSizeArrayT*>(referendM)) {
    auto referenceLE = functionState->defaultRegion->makeWrapperPtr(referenceM, ref);
    return getConcreteControlBlockPtr(globalState, builder, referenceLE);
  } else if (dynamic_cast<Str*>(referendM)) {
    auto referenceLE = functionState->defaultRegion->makeWrapperPtr(referenceM, ref);
    return getConcreteControlBlockPtr(globalState, builder, referenceLE);
  } else {
    assert(false);
  }
}
