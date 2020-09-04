#include <iostream>
#include "function/expressions/shared/controlblock.h"

#include "translatetype.h"

#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/heap.h"

void fillInnerStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structM,
    std::vector<LLVMValueRef> membersLE,
    LLVMValueRef innerStructPtrLE) {
  for (int i = 0; i < membersLE.size(); i++) {
    auto memberLE = membersLE[i];
    auto memberType = structM->members[i]->type;

    auto memberName = structM->members[i]->name;
    if (structM->members[i]->type->referend == globalState->metalCache.innt) {
      buildFlare(FL(), globalState, functionState, builder, "Initialized member ", memberName, ": ", memberLE);
    }
    if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
      if (globalState->opt->census) {
        if (dynamic_cast<StructReferend*>(memberType->referend) ||
            dynamic_cast<InterfaceReferend*>(memberType->referend) ||
            dynamic_cast<KnownSizeArrayT*>(memberType->referend) ||
            dynamic_cast<UnknownSizeArrayT*>(memberType->referend)) {
          if (memberType->ownership == UnconvertedOwnership::WEAK) {
//            auto wrciLE = getWrciFromWeakRef(builder, memberLE);
            buildFlare(FL(), globalState, functionState, builder, "Member ", i, ": WRCI ", "impl");//, wrciLE);
          } else {
            auto controlBlockPtrLE = getControlBlockPtr(builder, memberLE, memberType->referend);
            buildFlare(FL(), globalState, functionState, builder,
                "Member ", i, ": ",
                getObjIdFromControlBlockPtr(globalState, builder, memberType->referend, controlBlockPtrLE));
          }
        }
      }
    }
    auto ptrLE =
        LLVMBuildStructGEP(builder, innerStructPtrLE, i, memberName.c_str());
    LLVMBuildStore(builder, memberLE, ptrLE);
  }
}

LLVMValueRef constructCountedStruct(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMTypeRef structL,
    UnconvertedReference* structTypeM,
    StructDefinition* structM,
    std::vector<LLVMValueRef> membersLE) {
  buildFlare(FL(), globalState, functionState, builder, "Filling new struct: ", structM->name->name);
  LLVMValueRef newStructPtrLE =
      mallocKnownSize(globalState, functionState, builder, structTypeM->location, structL);
  fillControlBlock(
      from,
      globalState, functionState, builder,
      structTypeM->referend,
      structM->mutability,
      getEffectiveWeakability(globalState, structM),
      getConcreteControlBlockPtr(builder, newStructPtrLE), structM->name->name);
  fillInnerStruct(
      globalState, functionState,
      builder, structM, membersLE,
      getStructContentsPtr(builder, newStructPtrLE));
  buildFlare(FL(), globalState, functionState, builder, "Done filling new struct");
  return newStructPtrLE;
}

LLVMValueRef constructInnerStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structM,
    LLVMTypeRef valStructL,
    const std::vector<LLVMValueRef>& membersLE) {

  // We always start with an undef, and then fill in its fields one at a
  // time.
  LLVMValueRef structValueBeingInitialized = LLVMGetUndef(valStructL);
  for (int i = 0; i < membersLE.size(); i++) {
    if (structM->members[i]->type->referend == globalState->metalCache.innt) {
      buildFlare(FL(), globalState, functionState, builder, "Initialized member ", i, ": ", membersLE[i]);
    }
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
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* desiredReference,
    const std::vector<LLVMValueRef>& membersLE) {

  auto structReferend =
      dynamic_cast<StructReferend*>(desiredReference->referend);
  assert(structReferend);

  auto structM = globalState->program->getStruct(structReferend->fullName);

  switch (structM->mutability) {
    case Mutability::MUTABLE: {
      auto countedStructL = globalState->getWrapperStruct(structReferend->fullName);
      return constructCountedStruct(
          from, globalState, functionState, builder, countedStructL, desiredReference, structM, membersLE);
    }
    case Mutability::IMMUTABLE: {
      if (desiredReference->location == Location::INLINE) {
        auto valStructL =
            globalState->getInnerStruct(structReferend->fullName);
        return constructInnerStruct(
            globalState, functionState, builder, structM, valStructL, membersLE);
      } else {
        auto countedStructL =
            globalState->getWrapperStruct(structReferend->fullName);
        return constructCountedStruct(
            from, globalState, functionState, builder, countedStructL, desiredReference, structM, membersLE);
      }
    }
    default:
      assert(false);
      return nullptr;
  }
  assert(false);
}
