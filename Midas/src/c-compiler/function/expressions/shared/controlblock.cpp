#include "controlblock.h"
#include "shared.h"

LLVMValueRef getConcreteControlBlockPtr(
    LLVMBuilderRef builder,
    LLVMValueRef structPtrLE) {
  // Control block is always the 0th element of every struct.
  return LLVMBuildStructGEP(builder, structPtrLE, 0, "controlPtr");
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
LLVMValueRef getRcPtrFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtr) {
  return LLVMBuildStructGEP(
      builder,
      controlBlockPtr,
      globalState->controlBlockRcMemberIndex,
      "rcPtr");
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

LLVMValueRef getRcFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef structExpr) {
  auto rcPtrLE = getRcPtrFromControlBlockPtr(globalState, builder, structExpr);
  return LLVMBuildLoad(builder, rcPtrLE, "rc");
}

// Returns object ID
LLVMValueRef fillControlBlock(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtrLE,
    const std::string& typeName) {

  auto objIdLE = adjustCounter(builder, globalState->objIdCounter, 1);

  LLVMValueRef newControlBlockLE = LLVMGetUndef(globalState->controlBlockStructL);
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
  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE);
  return objIdLE;
}
