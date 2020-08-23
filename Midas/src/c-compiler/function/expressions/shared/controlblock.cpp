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
    } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
      assert(false); // Mutables in fast mode dont have a strong RC
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
      // Fine to access the strong RC for mutables in resilient mode
    }
    return LLVMBuildStructGEP(
        builder,
        controlBlockPtr,
        globalState->mutControlBlockRcMemberIndex,
        "rcPtr");
  }
}

LLVMValueRef getWrciFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef controlBlockPtr) {

  if (refM->ownership == Ownership::SHARE) {
    // Shares never have weak refs
    assert(false);
  } else {
    auto wrciPtrLE =
        LLVMBuildStructGEP(
            builder,
            controlBlockPtr,
            globalState->mutControlBlockWrciMemberIndex,
            "wrciPtr");
    return LLVMBuildLoad(builder, wrciPtrLE, "wrci");
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
    auto wrciLE = allocWrc(globalState, functionState, builder);

    newControlBlockLE =
        LLVMBuildInsertValue(
            builder,
            newControlBlockLE,
            wrciLE,
            globalState->mutControlBlockWrciMemberIndex,
            "strControlBlockWithWrci");
  }
  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE);
}

LLVMValueRef getWrciFromWeakRef(
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  return LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_RCINDEX_MEMBER_INDEX, "wrci");
}

LLVMValueRef getInnerRefFromWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    LLVMValueRef weakRefLE) {
  checkValidReference(FL(), globalState, functionState, builder, weakRefM, weakRefLE);
  auto innerRefLE = LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_OBJPTR_MEMBER_INDEX, "");
  // We dont check that its valid because if it's a weak ref, it might *not* be pointing at
  // a valid reference.
  return innerRefLE;
}