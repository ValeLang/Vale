#include "controlblock.h"
#include "shared.h"

LLVMValueRef getConcreteControlBlockPtr(
    LLVMBuilderRef builder,
    LLVMValueRef concretePtrLE) {
  // Control block is always the 0th element of every concrete struct.
  return LLVMBuildStructGEP(builder, concretePtrLE, 0, "controlPtr");
}

LLVMValueRef getInterfaceControlBlockPtr(
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
    LLVMValueRef controlBlockPtr) {
  return LLVMBuildStructGEP(
      builder,
      controlBlockPtr,
      globalState->controlBlockRcMemberIndex,
      "rcPtr");
}

LLVMValueRef getWrciFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtr) {
  auto wrciPtrLE =
      LLVMBuildStructGEP(
          builder,
          controlBlockPtr,
          globalState->controlBlockWrciMemberIndex,
          "wrciPtr");
  return LLVMBuildLoad(builder, wrciPtrLE, "wrci");
}

LLVMValueRef getObjIdFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtr) {
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
    LLVMValueRef structExpr) {
  auto rcPtrLE = getStrongRcPtrFromControlBlockPtr(globalState, builder, structExpr);
  return LLVMBuildLoad(builder, rcPtrLE, "rc");
}

// Returns object ID
LLVMValueRef fillControlBlock(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtrLE,
    const std::string& typeName) {

  bool weakable = LLVMTypeOf(controlBlockPtrLE) == LLVMPointerType(globalState->weakableControlBlockStructL, 0);

  auto objIdLE = adjustCounter(builder, globalState->objIdCounter, 1);

  LLVMValueRef newControlBlockLE = nullptr;
  if (weakable) {
    newControlBlockLE = LLVMGetUndef(globalState->weakableControlBlockStructL);
  } else {
    newControlBlockLE = LLVMGetUndef(globalState->nonWeakableControlBlockStructL);
  }
  newControlBlockLE =
      LLVMBuildInsertValue(
          builder,
          newControlBlockLE,
          // Start at 1, 0 would mean it's dead.
          LLVMConstInt(LLVMInt64Type(), 1, false),
          globalState->controlBlockRcMemberIndex,
          "controlBlockWithRc");
  newControlBlockLE =
      LLVMBuildInsertValue(
          builder,
          newControlBlockLE,
          objIdLE,
          globalState->controlBlockObjIdIndex,
          "controlBlockWithRcAndObjId");
  newControlBlockLE =
      LLVMBuildInsertValue(
          builder,
          newControlBlockLE,
          globalState->getOrMakeStringConstant(typeName),
          globalState->controlBlockTypeStrIndex,
          "controlBlockComplete");
  if (weakable) {
    auto wrciLE = LLVMBuildCall(builder, globalState->allocWrc, nullptr, 0, "");
    newControlBlockLE =
        LLVMBuildInsertValue(
            builder,
            newControlBlockLE,
            wrciLE,
            globalState->controlBlockWrciMemberIndex,
            "controlBlockComplete");
  }
  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE);
  return objIdLE;
}

LLVMValueRef getWrciFromWeakRef(
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  return LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_RCINDEX_MEMBER_INDEX, "wrci");
}

LLVMValueRef getIsAliveFromWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  auto wrciLE = getWrciFromWeakRef(builder, weakRefLE);
  return LLVMBuildCall(builder, globalState->wrcIsLive, &wrciLE, 1, "isAlive");
}

LLVMValueRef getObjPtrFromWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    LLVMValueRef weakRefLE,
    Reference* constraintRefM) {
  auto refLE = LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_OBJPTR_MEMBER_INDEX, "");
  checkValidReference(FL(), globalState, functionState, builder, constraintRefM, refLE);
  acquireReference(FL(), globalState, functionState, builder, constraintRefM, refLE);
  return refLE;
}