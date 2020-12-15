#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <region/common/controlblock.h>
#include <utils/counters.h>
#include <utils/branch.h>
#include <region/common/common.h>
#include "hgm.h"

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;

HybridGenerationalMemory::HybridGenerationalMemory(
    GlobalState* globalState_,
    IReferendStructsSource* referendStructsSource_,
    IWeakRefStructsSource* weakRefStructsSource_,
    bool elideChecksForKnownLive_,
    bool limitMode_)
  : globalState(globalState_),
    fatWeaks_(globalState_, weakRefStructsSource_),
    referendStructsSource(referendStructsSource_),
    weakRefStructsSource(weakRefStructsSource_),
    elideChecksForKnownLive(elideChecksForKnownLive_),
    limitMode(limitMode_) {}

LLVMValueRef HybridGenerationalMemory::getTargetGenFromWeakRef(
    LLVMBuilderRef builder,
    IWeakRefStructsSource* weakRefStructsSource,
    Referend* referend,
    WeakFatPtrLE weakRefLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V2 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_LIMIT);
  auto headerLE = fatWeaks_.getHeaderFromWeakRef(builder, weakRefLE);
  assert(LLVMTypeOf(headerLE) == weakRefStructsSource->getWeakRefHeaderStruct(referend));
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "actualGeni");
}

static LLVMValueRef makeGenHeader(
    GlobalState* globalState,
    IWeakRefStructsSource* weakRefStructsSource,
    LLVMBuilderRef builder,
    Referend* referend,
    LLVMValueRef targetGenLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V2 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_LIMIT);
  auto headerLE = LLVMGetUndef(weakRefStructsSource->getWeakRefHeaderStruct(referend));
  headerLE = LLVMBuildInsertValue(builder, headerLE, targetGenLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "header");
  return headerLE;
}

static LLVMValueRef getGenerationFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    IReferendStructsSource* structs,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtr) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V2 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_LIMIT);
  assert(LLVMTypeOf(controlBlockPtr.refLE) == LLVMPointerType(structs->getControlBlock(referendM)->getStruct(), 0));

  auto genPtrLE =
      LLVMBuildStructGEP(
          builder,
          controlBlockPtr.refLE,
          structs->getControlBlock(referendM)->getMemberIndex(ControlBlockMember::GENERATION),
          "genPtr");
  return LLVMBuildLoad(builder, genPtrLE, "gen");
}

WeakFatPtrLE HybridGenerationalMemory::weakStructPtrToGenWeakInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      // continue
      break;
    case RegionOverride::FAST:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
      assert(false);
      break;
    default:
      assert(false);
      break;
  }

//  checkValidReference(
//      FL(), globalState, functionState, builder, sourceStructTypeM, sourceRefLE);
  auto controlBlockPtr =
      referendStructsSource->getConcreteControlBlockPtr(
          FL(), functionState, builder, sourceStructTypeM,
          referendStructsSource->makeWrapperPtr(
              FL(), functionState, builder, sourceStructTypeM,
              fatWeaks_.getInnerRefFromWeakRef(
                  functionState, builder, sourceStructTypeM, sourceRefLE)));

  auto interfaceRefLT =
      weakRefStructsSource->getInterfaceWeakRefStruct(
          targetInterfaceReferendM);
  auto headerLE = fatWeaks_.getHeaderFromWeakRef(builder, sourceRefLE);

  auto objPtr =
      makeInterfaceRefStruct(
          globalState, functionState, builder, referendStructsSource, sourceStructReferendM, targetInterfaceReferendM, controlBlockPtr);

  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetInterfaceTypeM, interfaceRefLT, headerLE, objPtr);
}

// Makes a non-weak interface ref into a weak interface ref
WeakFatPtrLE HybridGenerationalMemory::assembleInterfaceWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    InterfaceReferend* interfaceReferendM,
    InterfaceFatPtrLE sourceInterfaceFatPtrLE) {
  assert(sourceType->ownership == Ownership::OWN || sourceType->ownership == Ownership::SHARE);
  // curious, if its a borrow, do we just return sourceRefLE?

  LLVMValueRef genLE = nullptr;
  if (sourceType->ownership == Ownership::OWN) {
    auto controlBlockPtrLE =
        referendStructsSource->getControlBlockPtr(FL(), functionState, builder, interfaceReferendM, sourceInterfaceFatPtrLE);
    if (limitMode) {
      genLE = constI64LE(globalState, 0);
    } else {
      genLE = getGenerationFromControlBlockPtr(globalState, builder, referendStructsSource, sourceType->referend,
          controlBlockPtrLE);
    }
  } else if (sourceType->ownership == Ownership::BORROW) {
    assert(false); // impl
  } else {
    assert(false);
  }
  auto headerLE = makeGenHeader(globalState, weakRefStructsSource, builder, interfaceReferendM, genLE);

  auto weakRefStructLT =
      weakRefStructsSource->getInterfaceWeakRefStruct(interfaceReferendM);
  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetType, weakRefStructLT, headerLE, sourceInterfaceFatPtrLE.refLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleStructWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    Reference* targetTypeM,
    StructReferend* structReferendM,
    WrapperPtrLE objPtrLE) {
  assert(structTypeM->ownership == Ownership::OWN || structTypeM->ownership == Ownership::SHARE);
  // curious, if its a borrow, do we just return sourceRefLE?

  auto controlBlockPtrLE = referendStructsSource->getConcreteControlBlockPtr(FL(), functionState, builder, structTypeM, objPtrLE);
  auto currentGenLE = limitMode ? constI64LE(globalState, 0) : getGenerationFromControlBlockPtr(globalState, builder, referendStructsSource, structTypeM->referend, controlBlockPtrLE);
  auto headerLE = makeGenHeader(globalState, weakRefStructsSource, builder, structReferendM, currentGenLE);
  auto weakRefStructLT =
      weakRefStructsSource->getStructWeakRefStruct(structReferendM);
  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleKnownSizeArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceKSAMT,
    KnownSizeArrayT* knownSizeArrayMT,
    Reference* targetKSAWeakRefMT,
    WrapperPtrLE objPtrLE) {
  // impl
  assert(false);
  exit(1);
}

WeakFatPtrLE HybridGenerationalMemory::assembleUnknownSizeArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    UnknownSizeArrayT* unknownSizeArrayMT,
    Reference* targetUSAWeakRefMT,
    WrapperPtrLE sourceRefLE) {
  LLVMValueRef genLE = nullptr;
  if (sourceType->ownership == Ownership::OWN) {
    auto controlBlockPtrLE = referendStructsSource->getConcreteControlBlockPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    if (limitMode) {
      genLE = constI64LE(globalState, 0);
    } else {
      genLE = getGenerationFromControlBlockPtr(globalState, builder, referendStructsSource, sourceType->referend,
          controlBlockPtrLE);
    }
  } else if (sourceType->ownership == Ownership::BORROW) {
    assert(false); // impl
  } else {
    assert(false);
  }
  auto headerLE = makeGenHeader(globalState, weakRefStructsSource, builder, unknownSizeArrayMT, genLE);

  auto weakRefStructLT =
      weakRefStructsSource->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT);
  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetUSAWeakRefMT, weakRefStructLT, headerLE, sourceRefLE.refLE);
}

LLVMValueRef HybridGenerationalMemory::lockGenFatPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    WeakFatPtrLE weakRefLE,
    bool knownLive) {
  auto fatPtrLE = weakRefLE;
  auto innerLE = fatWeaks_.getInnerRefFromWeakRef(functionState, builder, refM, fatPtrLE);

  if (limitMode || (knownLive && elideChecksForKnownLive)) {
    // Do nothing
  } else {
    if (globalState->opt->printMemOverhead) {
      adjustCounter(globalState, builder, globalState->livenessCheckCounter, 1);
    }
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, refM, fatPtrLE, knownLive);
    buildIf(
        globalState, functionState, builder, isZeroLE(builder, isAliveLE),
        [this, from, functionState, fatPtrLE](LLVMBuilderRef thenBuilder) {
          if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V3) {
            auto ptrToWriteToLE = LLVMBuildLoad(thenBuilder, globalState->crashGlobal,
                "crashGlobal");
            LLVMBuildStore(thenBuilder, constI64LE(globalState, 0), ptrToWriteToLE);
          } else {
            buildPrintAreaAndFileAndLine(globalState, thenBuilder, from);
            buildPrint(globalState, thenBuilder, "Tried dereferencing dangling reference! ");
            buildPrint(globalState, thenBuilder, "Exiting!\n");
            auto exitCodeIntLE = LLVMConstInt(LLVMInt8TypeInContext(globalState->context), 255,
                false);
            LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");
          }
        });
  }
  return innerLE;
}

void HybridGenerationalMemory::innerNoteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* concreteRefM,
    ControlBlockPtrLE controlBlockPtrLE) {
  // No need to do anything!
}


void HybridGenerationalMemory::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  // Do nothing!
}

void HybridGenerationalMemory::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  // Do nothing!
}

LLVMValueRef HybridGenerationalMemory::getIsAliveFromWeakFatPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    WeakFatPtrLE weakFatPtrLE,
    bool knownLive) {
  if (limitMode || (knownLive && elideChecksForKnownLive)) {
    return LLVMConstInt(LLVMInt1TypeInContext(globalState->context), 1, false);
  } else {
    // Get target generation from the ref
    auto targetGenLE = getTargetGenFromWeakRef(builder, weakRefStructsSource, weakRefM->referend, weakFatPtrLE);

    // Get actual generation from the table
    auto innerRefLE =
        fatWeaks_.getInnerRefFromWeakRefWithoutCheck(functionState, builder, weakRefM,
            weakFatPtrLE);
    auto controlBlockPtrLE =
        referendStructsSource->getControlBlockPtrWithoutChecking(
            FL(), functionState, builder, innerRefLE, weakRefM);
    auto actualGenLE = getGenerationFromControlBlockPtr(globalState, builder, referendStructsSource, weakRefM->referend,
        controlBlockPtrLE);

    return LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        actualGenLE,
        targetGenLE,
        "genLive");
  }
}

Ref HybridGenerationalMemory::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef,
    bool knownLive) {
  if (limitMode || (knownLive && elideChecksForKnownLive)) {
    // Do nothing, just return a constant true
    auto isAliveLE = LLVMConstInt(LLVMInt1TypeInContext(globalState->context), 1, false);
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, isAliveLE);
  } else {
    assert(
        weakRefM->ownership == Ownership::BORROW ||
            weakRefM->ownership == Ownership::WEAK);

    auto weakFatPtrLE =
        weakRefStructsSource->makeWeakFatPtr(
            weakRefM,
            globalState->region->checkValidReference(
                FL(), functionState, builder, weakRefM, weakRef));
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, weakRefM, weakFatPtrLE, knownLive);
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, isAliveLE);
  }
}

LLVMValueRef HybridGenerationalMemory::fillWeakableControlBlock(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef controlBlockLE) {
  // The generation was already incremented when we freed it (or malloc'd it for the first time),
  // so nothing to do here!
  return controlBlockLE;
}

WeakFatPtrLE HybridGenerationalMemory::weakInterfaceRefToWeakStructRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakInterfaceRefMT,
    WeakFatPtrLE weakInterfaceFatPtrLE) {
  auto headerLE = fatWeaks_.getHeaderFromWeakRef(builder, weakInterfaceFatPtrLE);

  // The object might not exist, so skip the check.
  auto interfaceFatPtrLE =
      referendStructsSource->makeInterfaceFatPtrWithoutChecking(
          FL(), functionState, builder,
          weakInterfaceRefMT, // It's still conceptually weak even though its not in a weak pointer.
          fatWeaks_.getInnerRefFromWeakRef(
              functionState,
              builder,
              weakInterfaceRefMT,
              weakInterfaceFatPtrLE));
  auto controlBlockPtrLE =
      referendStructsSource->getControlBlockPtrWithoutChecking(
          FL(), functionState, builder, weakInterfaceRefMT->referend, interfaceFatPtrLE);

  // Now, reassemble a weak void* ref to the struct.
  auto weakVoidStructRefLE =
      fatWeaks_.assembleVoidStructWeakRef(builder, weakInterfaceRefMT, controlBlockPtrLE, headerLE);

  return weakVoidStructRefLE;
}

// USE ONLY FOR ASSERTING A REFERENCE IS VALID
std::tuple<Reference*, LLVMValueRef> hgmGetRefInnardsForChecking(Ref ref) {
  Reference* refM = ref.refM;
  LLVMValueRef refLE = ref.refLE;
  return std::make_tuple(refM, refLE);
}

void HybridGenerationalMemory::buildCheckWeakRef(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef) {
  if (globalState->opt->census) {
    Reference *actualRefM = nullptr;
    LLVMValueRef refLE = nullptr;
    std::tie(actualRefM, refLE) = hgmGetRefInnardsForChecking(weakRef);
    auto weakFatPtrLE = weakRefStructsSource->makeWeakFatPtr(weakRefM, refLE);
    auto innerLE =
        fatWeaks_.getInnerRefFromWeakRefWithoutCheck(
            functionState, builder, weakRefM, weakFatPtrLE);

    auto controlBlockPtrLE =
        referendStructsSource->getControlBlockPtrWithoutChecking(
            FL(), functionState, builder, innerLE, weakRefM);
    // We check that the generation is <= to what's in the actual object.
    auto actualGen =
        getGenerationFromControlBlockPtr(
            globalState, builder, referendStructsSource, weakRefM->referend, controlBlockPtrLE);
    auto targetGen = getTargetGenFromWeakRef(builder, weakRefStructsSource, weakRefM->referend, weakFatPtrLE);
    buildCheckGen(globalState, functionState, builder, targetGen, actualGen);

    if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(weakRefM->referend)) {
      auto interfaceFatPtrLE = referendStructsSource->makeInterfaceFatPtrWithoutChecking(FL(),
          functionState, builder, weakRefM, innerLE);
      auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
      buildAssertCensusContains(FL(), globalState, functionState, builder, itablePtrLE);
    }
  }
}

Ref HybridGenerationalMemory::assembleWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    Ref sourceRef) {
  // Now we need to package it up into a weak ref.
  if (auto structReferend = dynamic_cast<StructReferend*>(sourceType->referend)) {
    auto sourceRefLE = globalState->region->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = referendStructsSource->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleStructWeakRef(
            functionState, builder, sourceType, targetType, structReferend, sourceWrapperPtrLE);
    return wrap(functionState->defaultRegion, targetType, resultLE);
  } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(sourceType->referend)) {
    auto sourceRefLE = globalState->region->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceInterfaceFatPtrLE = referendStructsSource->makeInterfaceFatPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleInterfaceWeakRef(
            functionState, builder, sourceType, targetType, interfaceReferendM, sourceInterfaceFatPtrLE);
    return wrap(functionState->defaultRegion, targetType, resultLE);
  } else if (auto knownSizeArray = dynamic_cast<KnownSizeArrayT*>(sourceType->referend)) {
    auto sourceRefLE = globalState->region->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = referendStructsSource->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleKnownSizeArrayWeakRef(
            functionState, builder, sourceType, knownSizeArray, targetType, sourceWrapperPtrLE);
    return wrap(functionState->defaultRegion, targetType, resultLE);
  } else if (auto unknownSizeArray = dynamic_cast<UnknownSizeArrayT*>(sourceType->referend)) {
    auto sourceRefLE = globalState->region->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = referendStructsSource->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleUnknownSizeArrayWeakRef(
            functionState, builder, sourceType, unknownSizeArray, targetType, sourceWrapperPtrLE);
    return wrap(functionState->defaultRegion, targetType, resultLE);
  } else assert(false);
}


LLVMTypeRef HybridGenerationalMemory::makeWeakRefHeaderStruct(GlobalState* globalState) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V2 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_LIMIT);
  auto genRefStructL = LLVMStructCreateNamed(globalState->context, "__GenRef");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));

  LLVMStructSetBody(genRefStructL, memberTypesL.data(), memberTypesL.size(), false);

  return genRefStructL;
}