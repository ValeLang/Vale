#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <region/common/controlblock.h>
#include <function/expressions/shared/members.h>
#include <utils/counters.h>
#include <function/expressions/shared/elements.h>
#include "common.h"

LLVMValueRef upcastThinPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    IReferendStructsSource* referendStructsSource,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    WrapperPtrLE sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  assert(sourceStructTypeM->location != Location::INLINE);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      assert(sourceStructTypeM->ownership == Ownership::SHARE ||
          sourceStructTypeM->ownership == Ownership::OWN ||
          sourceStructTypeM->ownership == Ownership::BORROW);
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      assert(sourceStructTypeM->ownership == Ownership::SHARE ||
          sourceStructTypeM->ownership == Ownership::OWN);
      break;
    }
    default:
      assert(false);
  }
  ControlBlockPtrLE controlBlockPtrLE =
      referendStructsSource->getConcreteControlBlockPtr(
          functionState, builder, sourceStructTypeM, sourceRefLE);
  auto interfaceRefLE =
      makeInterfaceRefStruct(
          globalState, functionState, builder, sourceStructReferendM, targetInterfaceReferendM,
          controlBlockPtrLE);
  return interfaceRefLE;
}

LLVMTypeRef translateReferenceSimple(GlobalState* globalState, Referend* referend) {
  if (auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT *>(referend)) {
    assert(false); // impl
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT *>(referend)) {
    auto unknownSizeArrayCountedStructLT =
        globalState->region->getReferendStructsSource()->getUnknownSizeArrayWrapperStruct(usaMT);
    return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
  } else if (auto structReferend = dynamic_cast<StructReferend *>(referend)) {
    auto countedStructL = globalState->region->getReferendStructsSource()->getWrapperStruct(structReferend);
    return LLVMPointerType(countedStructL, 0);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend *>(referend)) {
    auto interfaceRefStructL =
        globalState->region->getReferendStructsSource()->getInterfaceRefStruct(interfaceReferend);
    return interfaceRefStructL;
  } else {
    std::cerr << "Unimplemented type: " << typeid(*referend).name() << std::endl;
    assert(false);
    return nullptr;
  }
}

LLVMTypeRef translateWeakReference(GlobalState* globalState, Referend* referend) {
  if (auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT *>(referend)) {
    assert(false); // impl
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT *>(referend)) {
    return globalState->region->getWeakRefStructsSource()->getUnknownSizeArrayWeakRefStruct(usaMT);
  } else if (auto structReferend = dynamic_cast<StructReferend *>(referend)) {
    return globalState->region->getWeakRefStructsSource()->getStructWeakRefStruct(structReferend);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend *>(referend)) {
    return globalState->region->getWeakRefStructsSource()->getInterfaceWeakRefStruct(interfaceReferend);
  } else {
    assert(false);
  }
}

Ref loadInnerInnerStructMember(
    IRegion* region,
    LLVMBuilderRef builder,
    LLVMValueRef innerStructPtrLE,
    int memberIndex,
    Reference* expectedType,
    std::string memberName) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);

  auto result =
      LLVMBuildLoad(
          builder,
          LLVMBuildStructGEP(
              builder, innerStructPtrLE, memberIndex, memberName.c_str()),
          memberName.c_str());
  return wrap(region, expectedType, result);
}

void storeInnerInnerStructMember(
    LLVMBuilderRef builder, LLVMValueRef innerStructPtrLE, int memberIndex, std::string memberName, LLVMValueRef newValueLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);
  LLVMBuildStore(
      builder,
      newValueLE,
      LLVMBuildStructGEP(
          builder, innerStructPtrLE, memberIndex, memberName.c_str()));
}

LLVMValueRef getItablePtrFromInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE) {
  buildFlare(FL(), globalState, functionState, builder);
  assert(LLVMTypeOf(virtualArgLE.refLE) == functionState->defaultRegion->translateType(virtualParamMT));
  return getTablePtrFromInterfaceRef(builder, virtualArgLE);
}


LLVMValueRef fillControlBlockCensusFields(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef newControlBlockLE,
    const std::string& typeName) {
  if (globalState->opt->census) {
    auto objIdLE = adjustCounter(builder, globalState->objIdCounter, 1);
    newControlBlockLE =
        LLVMBuildInsertValue(
            builder,
            newControlBlockLE,
            objIdLE,
            globalState->region->getControlBlock(referendM)->getMemberIndex(ControlBlockMember::CENSUS_OBJ_ID),
            "strControlBlockWithObjId");
    newControlBlockLE =
        LLVMBuildInsertValue(
            builder,
            newControlBlockLE,
            globalState->getOrMakeStringConstant(typeName),
            globalState->region->getControlBlock(referendM)->getMemberIndex(ControlBlockMember::CENSUS_TYPE_STR),
            "strControlBlockWithTypeStr");
    buildFlare(from, globalState, functionState, builder, "Allocating ", typeName, objIdLE);
  }
  return newControlBlockLE;
}

LLVMValueRef insertStrongRc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef newControlBlockLE) {
  return LLVMBuildInsertValue(
      builder,
      newControlBlockLE,
      // Start at 1, 0 would mean it's dead.
      LLVMConstInt(LLVMInt32Type(), 1, false),
      globalState->region->getControlBlock(referendM)->getMemberIndex(
          ControlBlockMember::STRONG_RC),
      "controlBlockWithRc");
}



Ref loadElementFromKSAWithoutUpgradeInner(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref indexRef,
    LLVMValueRef arrayElementsPtrLE) {
  auto sizeRef =
      wrap(
          functionState->defaultRegion,
          globalState->metalCache.intRef,
          LLVMConstInt(LLVMInt64Type(), ksaMT->size, false));
  return loadElementWithoutUpgrade(
      globalState, functionState, builder, ksaRefMT,
      ksaMT->rawArray->elementType,
      sizeRef, arrayElementsPtrLE, ksaMT->rawArray->mutability, indexRef);
}

// Checks that the generation is <= to the actual one.
void buildCheckGen(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef targetGenLE,
    LLVMValueRef actualGenLE) {
  auto isValidLE =
      LLVMBuildICmp(builder, LLVMIntSLE, targetGenLE, actualGenLE, "genIsValid");
  buildAssert(
      globalState, functionState, builder, isValidLE,
      "Invalid generation, from the future!");
}

// Not returning Ref because we might need to wrap it in something else like a weak fat ptr
LLVMValueRef makeInterfaceRefStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructReferend* sourceStructReferendM,
    InterfaceReferend* targetInterfaceReferendM,
    ControlBlockPtrLE controlBlockPtrLE) {

  auto interfaceRefLT =
      globalState->region->getReferendStructsSource()->getInterfaceRefStruct(
          targetInterfaceReferendM);

  auto interfaceRefLE = LLVMGetUndef(interfaceRefLT);
  interfaceRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceRefLE,
          controlBlockPtrLE.refLE,
          0,
          "interfaceRefWithOnlyObj");
  auto itablePtrLE =
      globalState->getInterfaceTablePtr(
          globalState->program->getStruct(sourceStructReferendM->fullName)
              ->getEdgeForInterface(targetInterfaceReferendM->fullName));
  interfaceRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceRefLE,
          itablePtrLE,
          1,
          "interfaceRef");
  buildFlare(FL(), globalState, functionState, builder, "itable: ", ptrToVoidPtrLE(builder, itablePtrLE), " for ", sourceStructReferendM->fullName->name, " for ", targetInterfaceReferendM->fullName->name);

  return interfaceRefLE;
}

LLVMValueRef getTablePtrFromInterfaceRef(
    LLVMBuilderRef builder,
    InterfaceFatPtrLE interfaceRefLE) {
  return LLVMBuildExtractValue(builder, interfaceRefLE.refLE, 1, "itablePtr");
}
