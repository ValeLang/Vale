#include <iostream>

#include "translatetype.h"

#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/heap.h"

void fillControlBlock(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef newStructLE) {
  // TODO: maybe make a const global we can load from, instead of running these
  //  instructions every time.
  auto controlBlockPtrLE = getControlBlockPtr(builder, newStructLE);
  LLVMValueRef newControlBlockLE = LLVMGetUndef(globalState->controlBlockStructL);
  newControlBlockLE =
      LLVMBuildInsertValue(
          builder,
          newControlBlockLE,
          // Start at 1, 0 would mean it's dead.
          LLVMConstInt(LLVMInt64Type(), 1, false),
          0,
          "__crc");
  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE);
}

void fillInnerStruct(
    LLVMBuilderRef builder,
    StructDefinition* structM,
    std::vector<LLVMValueRef> membersLE,
    LLVMValueRef structPtrLE) {
  auto innerStructPtrLE = getInnerStructPtr(builder, structPtrLE);
  for (int i = 0; i < membersLE.size(); i++) {
    auto memberName = structM->members[i]->name;
    auto ptrLE =
        LLVMBuildStructGEP(builder, innerStructPtrLE, i, memberName.c_str());
    LLVMBuildStore(builder, membersLE[i], ptrLE);
  }
}

LLVMValueRef constructCountedStruct(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef structL,
    StructDefinition* structM,
    std::vector<LLVMValueRef> membersLE) {
  auto newStructLE = mallocStruct(globalState, builder, structL);
  fillControlBlock(globalState, builder, newStructLE);
  fillInnerStruct(builder, structM, membersLE, newStructLE);
  return newStructLE;
}

LLVMValueRef constructInnerStruct(
    LLVMBuilderRef builder,
    StructDefinition* structM,
    LLVMTypeRef valStructL,
    const std::vector<LLVMValueRef>& membersLE) {

  // We always start with an undef, and then fill in its fields one at a
  // time.
  LLVMValueRef structValueBeingInitialized = LLVMGetUndef(valStructL);
  for (int i = 0; i < membersLE.size(); i++) {
    auto memberName = structM->members[i]->name;
    // Every time we fill in a field, it actually makes a new entire
    // struct value, and gives us a LLVMValueRef for the new value.
    // So, `structValueBeingInitialized` contains the latest one.
    structValueBeingInitialized =
        LLVMBuildInsertValue(
            builder,
            structValueBeingInitialized,
            membersLE[i],
            i,
            memberName.c_str());
  }
  return structValueBeingInitialized;
}

LLVMValueRef translateConstruct(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* desiredReference,
    const std::vector<LLVMValueRef>& membersLE) {

  auto structReferend =
      dynamic_cast<StructReferend*>(desiredReference->referend);
  assert(structReferend);

  auto structM = globalState->program->getStruct(structReferend->fullName);

  switch (structM->mutability) {
    case Mutability::MUTABLE: {
      auto countedStructL = globalState->getCountedStruct(structReferend->fullName);
      return constructCountedStruct(
          globalState, builder, countedStructL, structM, membersLE);
    }
    case Mutability::IMMUTABLE: {
      if (isInlImm(globalState, desiredReference)) {
        auto valStructL =
            globalState->getInnerStruct(structReferend->fullName);
        return constructInnerStruct(
            builder, structM, valStructL, membersLE);
      } else {
        auto countedStructL =
            globalState->getCountedStruct(structReferend->fullName);
        return constructCountedStruct(
            globalState, builder, countedStructL, structM, membersLE);
      }
    }
    default:
      assert(false);
      return nullptr;
  }
}
