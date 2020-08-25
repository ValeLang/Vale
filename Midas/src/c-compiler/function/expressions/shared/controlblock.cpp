#include "controlblock.h"
#include "shared.h"
#include "weaks.h"

LLVMValueRef getConcreteControlBlockPtr(
    LLVMBuilderRef builder,
    LLVMValueRef concretePtrLE) {
  // Control block is always the 0th element of every concrete struct.
  return LLVMBuildStructGEP(builder, concretePtrLE, 0, "controlPtr");
}

LLVMValueRef getControlBlockPtrFromInterfaceRef(
    LLVMBuilderRef builder,
    LLVMValueRef interfaceRefLE) {
  // Interface fat pointer's first element points directly at the control block,
  // and we dont have to cast it. We would have to cast if we were accessing the
  // actual object though.
  return LLVMBuildExtractValue(builder, interfaceRefLE, 0, "controlPtr");
}

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef getStrongRcPtrFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef controlBlockPtr) {

  if (refM->ownership == Ownership::SHARE) {
    // Shares always have ref counts
    return LLVMBuildStructGEP(
        builder,
        controlBlockPtr,
        globalState->immControlBlockRcMemberIndex,
        "rcPtr");
  } else {
    if (globalState->opt->regionOverride == RegionOverride::ASSIST) {
      // Fine to access the strong RC for mutables in assist mode
    } else if (globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
      // Fine to access the strong RC for mutables in naive-rc mode
    } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
      assert(false); // Mutables in fast mode dont have a strong RC
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
      // Fine to access the strong RC for mutables in resilient mode
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_FAST) {
      assert(false);
    } else assert(false);
    return LLVMBuildStructGEP(
        builder,
        controlBlockPtr,
        globalState->mutControlBlockRcMemberIndex,
        "rcPtr");
  }
}

LLVMValueRef getObjIdFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtr) {
  assert(globalState->opt->census);
  return LLVMBuildLoad(
      builder,
      LLVMBuildStructGEP(
          builder,
          controlBlockPtr,
          globalState->controlBlockObjIdIndex,
          "objIdPtr"),
      "objId");
}

LLVMValueRef getTypeNameStrPtrFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtr) {
  return LLVMBuildLoad(
      builder,
      LLVMBuildStructGEP(
          builder,
          controlBlockPtr,
          globalState->controlBlockTypeStrIndex,
          "typeNameStrPtrPtr"),
      "typeNameStrPtr");
}

LLVMValueRef getStrongRcFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef structExpr) {
  auto rcPtrLE = getStrongRcPtrFromControlBlockPtr(globalState, builder, refM, structExpr);
  return LLVMBuildLoad(builder, rcPtrLE, "rc");
}

// Returns object ID
void fillControlBlock(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Mutability mutability,
    Weakability weakability,
    LLVMValueRef controlBlockPtrLE,
    const std::string& typeName) {

  LLVMValueRef newControlBlockLE = nullptr;
  if (mutability == Mutability::MUTABLE) {
    if (weakability == Weakability::WEAKABLE) {
      newControlBlockLE = LLVMGetUndef(globalState->mutWeakableControlBlockStructL);
    } else {
      newControlBlockLE = LLVMGetUndef(globalState->mutNonWeakableControlBlockStructL);
    }
  } else {
    newControlBlockLE = LLVMGetUndef(globalState->immControlBlockStructL);
  }

  if (mutability == Mutability::IMMUTABLE) {
    newControlBlockLE =
        LLVMBuildInsertValue(
            builder,
            newControlBlockLE,
            // Start at 1, 0 would mean it's dead.
            LLVMConstInt(LLVMInt64Type(), 1, false),
            globalState->immControlBlockRcMemberIndex,
            "controlBlockWithRc");
  } else {
    bool hasStrongRc =
        globalState->opt->regionOverride == RegionOverride::ASSIST ||
        globalState->opt->regionOverride == RegionOverride::NAIVE_RC ||
        globalState->opt->regionOverride == RegionOverride::RESILIENT;
    if (hasStrongRc) {
      newControlBlockLE =
          LLVMBuildInsertValue(
              builder,
              newControlBlockLE,
              // Start at 1, 0 would mean it's dead.
              LLVMConstInt(LLVMInt64Type(), 1, false),
              globalState->mutControlBlockRcMemberIndex,
              "controlBlockWithRc");
    }
  }

  if (globalState->opt->census) {
    auto objIdLE = adjustCounter(builder, globalState->objIdCounter, 1);
    newControlBlockLE =
        LLVMBuildInsertValue(
            builder,
            newControlBlockLE,
            objIdLE,
            globalState->controlBlockObjIdIndex,
            "strControlBlockWithObjId");
    newControlBlockLE =
        LLVMBuildInsertValue(
            builder,
            newControlBlockLE,
            globalState->getOrMakeStringConstant(typeName),
            globalState->controlBlockTypeStrIndex,
            "strControlBlockWithTypeStr");
    buildFlare(from, globalState, functionState, builder, "Allocating ", typeName, objIdLE);
  }
  if (weakability == Weakability::WEAKABLE) {
    newControlBlockLE = fillWeakableControlBlock(globalState, functionState, builder, newControlBlockLE);
  }
  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE);
}
