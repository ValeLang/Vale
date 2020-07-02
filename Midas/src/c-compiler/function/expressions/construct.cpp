#include <iostream>

#include "translatetype.h"

#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/heap.h"

void fillInnerStruct(
    LLVMBuilderRef builder,
    StructDefinition* structM,
    std::vector<LLVMValueRef> membersLE,
    LLVMValueRef innerStructPtrLE) {
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
  fillControlBlock(
      globalState, builder, getControlBlockPtr(builder, newStructLE));
  fillInnerStruct(
      builder, structM, membersLE, getCountedContents(builder, newStructLE));
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
