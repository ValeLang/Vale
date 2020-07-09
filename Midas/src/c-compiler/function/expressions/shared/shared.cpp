#include "shared.h"

#include "translatetype.h"
#include "controlblock.h"
#include "branch.h"

// A "Never" is something that should never be read.
// This is useful in a lot of situations, for example:
// - The return type of Panic()
// - The result of the Discard node
LLVMTypeRef makeNeverType() {
  // We arbitrarily use a zero-len array of i57 here because it's zero sized and
  // very unlikely to be used anywhere else.
  // We could use an empty struct instead, but this'll do.
  return LLVMArrayType(LLVMIntType(NEVER_INT_BITS), 0);
}
LLVMValueRef makeNever() {
  LLVMValueRef empty[1] = {};
  // We arbitrarily use a zero-len array of i57 here because it's zero sized and
  // very unlikely to be used anywhere else.
  // We could use an empty struct instead, but this'll do.
  return LLVMConstArray(LLVMIntType(NEVER_INT_BITS), empty, 0);
}

void makeLocal(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Local* local,
    LLVMValueRef valueToStore) {
  auto localAddr =
      LLVMBuildAlloca(
          builder,
          translateType(globalState, local->type),
          local->id->maybeName.c_str());
  assert(blockState->localAddrByLocalId.find(local->id) == blockState->localAddrByLocalId.end());
  blockState->localAddrByLocalId.emplace(local->id, localAddr);
  LLVMBuildStore(builder, valueToStore, localAddr);
}

LLVMValueRef adjustCounter(
    LLVMBuilderRef builder,
    LLVMValueRef counterPtrLE,
    int adjustAmount) {
  auto prevValLE = LLVMBuildLoad(builder, counterPtrLE, "counterPrevVal");
  auto newValLE =
      LLVMBuildAdd(
          builder, prevValLE, LLVMConstInt(LLVMInt64Type(), adjustAmount, true), "counterNewVal");
  LLVMBuildStore(builder, newValLE, counterPtrLE);

  return newValLE;
}


LLVMValueRef getTablePtrFromInterfaceRef(
    LLVMBuilderRef builder,
    LLVMValueRef interfaceRefLE) {
  return LLVMBuildExtractValue(builder, interfaceRefLE, 1, "itablePtr");
}

LLVMValueRef getControlBlockPtr(
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef referenceLE,
    Reference* refM) {
  if (dynamic_cast<InterfaceReferend*>(refM->referend)) {
    return getInterfaceControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<StructReferend*>(refM->referend)) {
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<KnownSizeArrayT*>(refM->referend)) {
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<UnknownSizeArrayT*>(refM->referend)) {
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<Str*>(refM->referend)) {
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else {
    assert(false);
    return nullptr;
  }
}

void flareAdjustRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef controlBlockPtr,
    LLVMValueRef oldAmount,
    LLVMValueRef newAmount) {
  buildFlare(
      from,
      globalState,
      builder,
      typeid(*refM->referend).name(),
      " ",
      getTypeNameStrPtrFromControlBlockPtr(globalState, builder, controlBlockPtr),
      getObjIdFromControlBlockPtr(globalState, builder, controlBlockPtr),
      ", ",
      oldAmount,
      "->",
      newAmount);
}

// Returns the new RC
LLVMValueRef adjustRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE,
    Reference* refM,
    int amount) {
  auto controlBlockPtrLE = getControlBlockPtr(builder, exprLE, refM);
  auto rcPtrLE =
      getRcPtrFromControlBlockPtr(globalState, builder, controlBlockPtrLE);
  auto oldRc = LLVMBuildLoad(builder, rcPtrLE, "oldRc");
  auto newRc = adjustCounter(builder, rcPtrLE, amount);
  flareAdjustRc(from, globalState, builder, refM, controlBlockPtrLE, oldRc, newRc);
  return newRc;
}

LLVMValueRef rcIsZero(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE,
    Reference* refM) {
  return isZeroLE(
      builder,
      getRcFromControlBlockPtr(
          globalState, builder, getControlBlockPtr(builder, exprLE, refM)));
}

LLVMValueRef isZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE) {
  return LLVMBuildICmp(
      builder,
      LLVMIntEQ,
      intLE,
      LLVMConstInt(LLVMTypeOf(intLE), 0, false),
      "rcIsZero");
}

LLVMValueRef isNonZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE) {
  return LLVMBuildICmp(
      builder,
      LLVMIntNE,
      intLE,
      LLVMConstInt(LLVMTypeOf(intLE), 0, false),
      "rcIsNonZero");
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    const std::string& first) {
  auto s = globalState->getOrMakeStringConstant(first);
  LLVMBuildCall(builder, globalState->printCStr, &s, 1, "");
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE) {
  if (LLVMTypeOf(exprLE) == LLVMInt64Type()) {
    LLVMBuildCall(builder, globalState->printInt, &exprLE, 1, "");
  } else if (LLVMTypeOf(exprLE) == LLVMPointerType(LLVMInt8Type(), 0)) {
    LLVMBuildCall(builder, globalState->printCStr, &exprLE, 1, "");
  } else {
    buildPrint(
        globalState,
        builder,
        LLVMBuildPointerCast(builder, exprLE, LLVMInt64Type(), ""));
  }
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int num) {
  buildPrint(globalState, builder, LLVMConstInt(LLVMInt64Type(), num, false));
}

// We'll assert if conditionLE is false.
void buildAssert(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    const std::string& failMessage) {
  buildIf(
      functionState, builder, isZeroLE(builder, conditionLE),
      [from, globalState, failMessage](LLVMBuilderRef thenBuilder) {
        buildFlare(from, globalState, thenBuilder, failMessage, " Exiting!");
        auto exitCodeIntLE = LLVMConstInt(LLVMInt8Type(), 255, false);
        LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");
      });
}

LLVMValueRef buildInterfaceCall(
    LLVMBuilderRef builder,
    std::vector<LLVMValueRef> argExprsLE,
    int virtualParamIndex,
    int indexInEdge) {
  auto virtualArgLE = argExprsLE[virtualParamIndex];
  auto objPtrLE =
      LLVMBuildPointerCast(
          builder,
          getInterfaceControlBlockPtr(builder, virtualArgLE),
          LLVMPointerType(LLVMVoidType(), 0),
          "objAsVoidPtr");
  auto itablePtrLE = getTablePtrFromInterfaceRef(builder, virtualArgLE);
  assert(LLVMGetTypeKind(LLVMTypeOf(itablePtrLE)) == LLVMPointerTypeKind);
  auto funcPtrPtrLE =
      LLVMBuildStructGEP(
          builder, itablePtrLE, indexInEdge, "methodPtrPtr");
  auto funcPtrLE = LLVMBuildLoad(builder, funcPtrPtrLE, "methodPtr");

  argExprsLE[virtualParamIndex] = objPtrLE;

  return LLVMBuildCall(
      builder, funcPtrLE, argExprsLE.data(), argExprsLE.size(), "");
}

LLVMValueRef makeConstExpr(LLVMBuilderRef builder, LLVMValueRef constExpr) {
  auto localAddr = LLVMBuildAlloca(builder, LLVMTypeOf(constExpr), "");
  LLVMBuildStore(builder,constExpr,localAddr);
  return LLVMBuildLoad(builder, localAddr, "");
}

LLVMValueRef makeConstIntExpr(LLVMBuilderRef builder, LLVMTypeRef type, int value) {
  return makeConstExpr(builder, LLVMConstInt(type, value, false));
}

void buildAssertCensusContains(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef refLE) {
  LLVMValueRef resultAsVoidPtrLE =
      LLVMBuildBitCast(
          builder, refLE, LLVMPointerType(LLVMVoidType(), 0), "");
  auto isRegisteredIntLE = LLVMBuildCall(builder, globalState->censusContains, &resultAsVoidPtrLE, 1, "");
  auto isRegisteredBoolLE = LLVMBuildTruncOrBitCast(builder,  isRegisteredIntLE, LLVMInt1Type(), "");
  buildAssert(checkerAFL, globalState, functionState, builder, isRegisteredBoolLE, "Object not registered with census!");
}

void checkValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef refLE) {
  if (refM->ownership == Ownership::OWN) {
    auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
  } else if (refM->ownership == Ownership::SHARE) {
    if (refM->location == Location::INLINE) {
      // Nothing to do, there's no control block or ref counts or anything.
    } else if (refM->location == Location::YONDER) {
      auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM);

      // We dont check ref count >0 because imm destructors receive with rc=0.
//      auto rcLE = getRcFromControlBlockPtr(globalState, builder, controlBlockPtrLE);
//      auto rcPositiveLE = LLVMBuildICmp(builder, LLVMIntSGT, rcLE, constI64LE(0), "");
//      buildAssert(checkerAFL, globalState, functionState, blockState, builder, rcPositiveLE, "Invalid RC!");

      buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
    } else assert(false);
  } else if (refM->ownership == Ownership::BORROW) {
    auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
  } else assert(false);
}

// Get parent local IDs that the child unstackified.
std::unordered_set<VariableId*> getChildUnstackifiedParentLocalIds(
    BlockState* parentBlockState,
    BlockState* childBlockState) {
  std::unordered_set<VariableId*> childUnstackifiedParentLocalIds;
  for (VariableId* unstackifiedLocalId : childBlockState->unstackifiedLocalIds) {
    // Ignore any that were made by the child block
    if (childBlockState->localAddrByLocalId.count(unstackifiedLocalId))
      continue;
    // Ignore any that were already unstackified by the parent
    if (parentBlockState->unstackifiedLocalIds.count(unstackifiedLocalId))
      continue;
    childUnstackifiedParentLocalIds.insert(unstackifiedLocalId);
  }
  return childUnstackifiedParentLocalIds;
}
