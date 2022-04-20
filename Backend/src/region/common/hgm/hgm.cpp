#include <llvm-c/Types.h>
#include "../../../globalstate.h"
#include "../../../function/function.h"
#include "../../../function/expressions/shared/shared.h"
#include "../controlblock.h"
#include "../../../utils/counters.h"
#include "../../../utils/branch.h"
#include "../common.h"
#include "hgm.h"

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;

HybridGenerationalMemory::HybridGenerationalMemory(
    GlobalState* globalState_,
    KindStructs* kindStructs_,
    bool elideChecksForKnownLive_,
    bool false_)
  : globalState(globalState_),
    fatWeaks(globalState_, kindStructs_),
    kindStructs(kindStructs_),
    elideChecksForKnownLive(elideChecksForKnownLive_),
//    false(false_),
    globalNullPtrPtrByKind(0, globalState->makeAddressHasher<Kind*>()) {
}

LLVMValueRef HybridGenerationalMemory::getTargetGenFromWeakRef(
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Kind* kind,
    WeakFatPtrLE weakRefLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
             globalState->opt->regionOverride == RegionOverride::RESILIENT_V4);
  auto headerLE = fatWeaks.getHeaderFromWeakRef(builder, weakRefLE);
  assert(LLVMTypeOf(headerLE) == kindStructs->getWeakRefHeaderStruct(kind));
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "actualGeni");
}

static LLVMValueRef makeGenHeader(
    GlobalState* globalState,
    KindStructs* kindStructs,
    LLVMBuilderRef builder,
    Kind* kind,
    LLVMValueRef targetGenLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
         globalState->opt->regionOverride == RegionOverride::RESILIENT_V4);
  auto headerLE = LLVMGetUndef(kindStructs->getWeakRefHeaderStruct(kind));
  headerLE = LLVMBuildInsertValue(builder, headerLE, targetGenLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "header");
  return headerLE;
}

static LLVMValueRef getGenerationFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtr) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
             globalState->opt->regionOverride == RegionOverride::RESILIENT_V4);
  assert(LLVMTypeOf(controlBlockPtr.refLE) == LLVMPointerType(structs->getControlBlock(kindM)->getStruct(), 0));

  auto genPtrLE =
      LLVMBuildStructGEP(
          builder,
          controlBlockPtr.refLE,
          structs->getControlBlock(kindM)->getMemberIndex(ControlBlockMember::GENERATION_32B),
          "genPtr");
  return LLVMBuildLoad(builder, genPtrLE, "gen");
}

WeakFatPtrLE HybridGenerationalMemory::weakStructPtrToGenWeakInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructKind* sourceStructKindM,
    Reference* sourceStructTypeM,
    InterfaceKind* targetInterfaceKindM,
    Reference* targetInterfaceTypeM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V3: case RegionOverride::RESILIENT_V4:
      // continue
      break;
    case RegionOverride::FAST:
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
      kindStructs->getConcreteControlBlockPtr(
          FL(), functionState, builder, sourceStructTypeM,
          kindStructs->makeWrapperPtr(
              FL(), functionState, builder, sourceStructTypeM,
              fatWeaks.getInnerRefFromWeakRef(
                  functionState, builder, sourceStructTypeM, sourceRefLE)));

  auto interfaceRefLT =
      kindStructs->getInterfaceWeakRefStruct(
          targetInterfaceKindM);
  auto headerLE = fatWeaks.getHeaderFromWeakRef(builder, sourceRefLE);

  auto objPtr =
      makeInterfaceRefStruct(
          globalState, functionState, builder, kindStructs, sourceStructKindM, targetInterfaceKindM, controlBlockPtr);

  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetInterfaceTypeM, interfaceRefLT, headerLE, objPtr);
}

// Makes a non-weak interface ref into a weak interface ref
WeakFatPtrLE HybridGenerationalMemory::assembleInterfaceWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    InterfaceKind* interfaceKindM,
    InterfaceFatPtrLE sourceInterfaceFatPtrLE) {
  assert(sourceType->ownership == Ownership::OWN || sourceType->ownership == Ownership::SHARE);
  // curious, if its a borrow, do we just return sourceRefLE?

  LLVMValueRef genLE = nullptr;
  if (sourceType->ownership == Ownership::OWN) {
    auto controlBlockPtrLE =
        kindStructs->getControlBlockPtr(FL(), functionState, builder, interfaceKindM, sourceInterfaceFatPtrLE);
//    if (false) {
//      genLE = constI64LE(globalState, 0);
//    } else {
      genLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, sourceType->kind,
          controlBlockPtrLE);
//    }
  } else if (sourceType->ownership == Ownership::BORROW) {
    assert(false); // impl
  } else {
    assert(false);
  }
  return assembleInterfaceWeakRef(functionState, builder, targetType, interfaceKindM, genLE, sourceInterfaceFatPtrLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleInterfaceWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* targetType,
    InterfaceKind* interfaceKindM,
    LLVMValueRef currentGenLE,
    InterfaceFatPtrLE sourceInterfaceFatPtrLE) {
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, interfaceKindM, currentGenLE);
  auto weakRefStructLT =
      kindStructs->getInterfaceWeakRefStruct(interfaceKindM);
  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetType, weakRefStructLT, headerLE, sourceInterfaceFatPtrLE.refLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleStructWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    Reference* targetTypeM,
    StructKind* structKindM,
    WrapperPtrLE objPtrLE) {
  auto controlBlockPtrLE = kindStructs->getConcreteControlBlockPtr(FL(), functionState, builder, structTypeM, objPtrLE);
  auto currentGenLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, structTypeM->kind, controlBlockPtrLE);
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, structKindM, currentGenLE);
  auto weakRefStructLT = kindStructs->getStructWeakRefStruct(structKindM);
  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleStructWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* targetTypeM,
    StructKind* structKindM,
    LLVMValueRef currentGenLE,
    WrapperPtrLE objPtrLE) {
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, structKindM, currentGenLE);
  auto weakRefStructLT = kindStructs->getStructWeakRefStruct(structKindM);
  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleStaticSizedArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceSSAMT,
    StaticSizedArrayT* staticSizedArrayMT,
    Reference* targetSSAWeakRefMT,
    WrapperPtrLE sourceRefLE) {
  LLVMValueRef genLE = nullptr;
  if (sourceSSAMT->ownership == Ownership::OWN) {
    auto controlBlockPtrLE = kindStructs->getConcreteControlBlockPtr(FL(), functionState, builder, sourceSSAMT, sourceRefLE);
    genLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, sourceSSAMT->kind, controlBlockPtrLE);
  } else if (sourceSSAMT->ownership == Ownership::BORROW) {
    assert(false); // impl
  } else {
    assert(false);
  }
  return assembleStaticSizedArrayWeakRef(
      functionState, builder, targetSSAWeakRefMT, staticSizedArrayMT, genLE, sourceRefLE);
}


WeakFatPtrLE HybridGenerationalMemory::assembleStaticSizedArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* targetTypeM,
    StaticSizedArrayT* staticSizedArrayMT,
    LLVMValueRef currentGenLE,
    WrapperPtrLE objPtrLE) {
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, staticSizedArrayMT, currentGenLE);
  auto weakRefStructLT = kindStructs->getStaticSizedArrayWeakRefStruct(staticSizedArrayMT);
  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleRuntimeSizedArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceSSAMT,
    RuntimeSizedArrayT* staticSizedArrayMT,
    Reference* targetSSAWeakRefMT,
    WrapperPtrLE sourceRefLE) {
  LLVMValueRef genLE = nullptr;
  if (sourceSSAMT->ownership == Ownership::OWN) {
    auto controlBlockPtrLE = kindStructs->getConcreteControlBlockPtr(FL(), functionState, builder, sourceSSAMT, sourceRefLE);
    genLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, sourceSSAMT->kind, controlBlockPtrLE);
  } else if (sourceSSAMT->ownership == Ownership::BORROW) {
    assert(false); // impl
  } else {
    assert(false);
  }
  return assembleRuntimeSizedArrayWeakRef(
      functionState, builder, targetSSAWeakRefMT, staticSizedArrayMT, genLE, sourceRefLE);
}


WeakFatPtrLE HybridGenerationalMemory::assembleRuntimeSizedArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* targetTypeM,
    RuntimeSizedArrayT* staticSizedArrayMT,
    LLVMValueRef currentGenLE,
    WrapperPtrLE objPtrLE) {
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, staticSizedArrayMT, currentGenLE);
  auto weakRefStructLT = kindStructs->getRuntimeSizedArrayWeakRefStruct(staticSizedArrayMT);
  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

LLVMValueRef HybridGenerationalMemory::lockGenFatPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    WeakFatPtrLE weakRefLE,
    bool knownLive) {
  auto fatPtrLE = weakRefLE;
  auto innerLE = fatWeaks.getInnerRefFromWeakRef(functionState, builder, refM, fatPtrLE);

  if (knownLive && elideChecksForKnownLive) {
    // Do nothing
  } else {
    if (globalState->opt->printMemOverhead) {
      adjustCounter(globalState, builder, globalState->metalCache->i64, globalState->livenessCheckCounter, 1);
    }
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, refM, fatPtrLE, knownLive);
    buildIf(
        globalState, functionState, builder, isZeroLE(builder, isAliveLE),
        [this, from](LLVMBuilderRef thenBuilder) {
          fastPanic(globalState, from, thenBuilder);
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
  if (false) {
    return LLVMConstInt(LLVMInt1TypeInContext(globalState->context), 1, false);
  } else if (knownLive && elideChecksForKnownLive) {
    return LLVMConstInt(LLVMInt1TypeInContext(globalState->context), 1, false);
  } else {
    // Get target generation from the ref
    auto targetGenLE = getTargetGenFromWeakRef(builder, kindStructs, weakRefM->kind, weakFatPtrLE);

    // Get actual generation from the table
    auto innerRefLE =
        fatWeaks.getInnerRefFromWeakRefWithoutCheck(functionState, builder, weakRefM,
            weakFatPtrLE);
    auto controlBlockPtrLE =
        kindStructs->getControlBlockPtrWithoutChecking(
            FL(), functionState, builder, innerRefLE, weakRefM);
    auto actualGenLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, weakRefM->kind,
        controlBlockPtrLE);

    auto isLiveLE = LLVMBuildICmp(builder, LLVMIntEQ, actualGenLE, targetGenLE, "isLive");
    if (knownLive && !elideChecksForKnownLive) {
      // See MPESC for status codes
      buildAssertWithExitCodeV(
          globalState, functionState, builder, isLiveLE, 116, "knownLive is true, but object is dead!");
    }

    return isLiveLE;
  }
}

Ref HybridGenerationalMemory::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef,
    bool knownLive) {
  if (false || (knownLive && elideChecksForKnownLive)) {
    // Do nothing, just return a constant true
    auto isAliveLE = LLVMConstInt(LLVMInt1TypeInContext(globalState->context), 1, false);
    return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isAliveLE);
  } else {
    assert(
        weakRefM->ownership == Ownership::BORROW ||
            weakRefM->ownership == Ownership::WEAK);

    auto weakFatPtrLE =
        kindStructs->makeWeakFatPtr(
            weakRefM,
            globalState->getRegion(weakRefM)
                ->checkValidReference(
                    FL(), functionState, builder, weakRefM, weakRef));
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, weakRefM, weakFatPtrLE, knownLive);
    return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isAliveLE);
  }
}

LLVMValueRef HybridGenerationalMemory::fillWeakableControlBlock(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Kind* kindM,
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
  auto headerLE = fatWeaks.getHeaderFromWeakRef(builder, weakInterfaceFatPtrLE);

  // The object might not exist, so skip the check.
  auto interfaceFatPtrLE =
      kindStructs->makeInterfaceFatPtrWithoutChecking(
          FL(), functionState, builder,
          weakInterfaceRefMT, // It's still conceptually weak even though its not in a weak pointer.
          fatWeaks.getInnerRefFromWeakRef(functionState, builder, weakInterfaceRefMT, weakInterfaceFatPtrLE));
  auto controlBlockPtrLE =
      kindStructs->getControlBlockPtrWithoutChecking(
          FL(), functionState, builder, weakInterfaceRefMT->kind, interfaceFatPtrLE);

  // Now, reassemble a weak void* ref to the struct.
  auto weakVoidStructRefLE =
      fatWeaks.assembleVoidStructWeakRef(builder, weakInterfaceRefMT, controlBlockPtrLE, headerLE);

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
    auto weakFatPtrLE = kindStructs->makeWeakFatPtr(weakRefM, refLE);
    auto innerLE =
        fatWeaks.getInnerRefFromWeakRefWithoutCheck(
            functionState, builder, weakRefM, weakFatPtrLE);

    auto controlBlockPtrLE =
        kindStructs->getControlBlockPtrWithoutChecking(
            FL(), functionState, builder, innerLE, weakRefM);
    // We check that the generation is <= to what's in the actual object.
    auto actualGen =
        getGenerationFromControlBlockPtr(
            globalState, builder, kindStructs, weakRefM->kind, controlBlockPtrLE);
    auto targetGen = getTargetGenFromWeakRef(builder, kindStructs, weakRefM->kind, weakFatPtrLE);
    buildCheckGen(globalState, functionState, builder, targetGen, actualGen);

    if (auto interfaceKindM = dynamic_cast<InterfaceKind *>(weakRefM->kind)) {
      auto interfaceFatPtrLE = kindStructs->makeInterfaceFatPtrWithoutChecking(FL(),
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
  if (auto structKind = dynamic_cast<StructKind*>(sourceType->kind)) {
    auto sourceRefLE =
        globalState->getRegion(sourceType)
            ->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleStructWeakRef(
            functionState, builder, sourceType, targetType, structKind, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto interfaceKindM = dynamic_cast<InterfaceKind*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceInterfaceFatPtrLE = kindStructs->makeInterfaceFatPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleInterfaceWeakRef(
            functionState, builder, sourceType, targetType, interfaceKindM, sourceInterfaceFatPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto staticSizedArray = dynamic_cast<StaticSizedArrayT*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleStaticSizedArrayWeakRef(
            functionState, builder, sourceType, staticSizedArray, targetType, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto runtimeSizedArray = dynamic_cast<RuntimeSizedArrayT*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleRuntimeSizedArrayWeakRef(
            functionState, builder, sourceType, runtimeSizedArray, targetType, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else assert(false);
}


LLVMTypeRef HybridGenerationalMemory::makeWeakRefHeaderStruct(GlobalState* globalState, RegionId* regionId) {
  assert(regionId == globalState->metalCache->mutRegionId);
//  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V2 ||
//      globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
//      globalState->opt->regionOverride == RegionOverride::RESILIENT_LIMIT);
  auto genRefStructL = LLVMStructCreateNamed(globalState->context, "__GenRef");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));

  LLVMStructSetBody(genRefStructL, memberTypesL.data(), memberTypesL.size(), false);

  return genRefStructL;
}

LLVMValueRef HybridGenerationalMemory::implodeConcreteHandle(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef) {
  auto weakFatPtrLE =
      kindStructs->makeWeakFatPtr(
          weakRefM,
          globalState->getRegion(weakRefM)->checkValidReference(FL(), functionState, builder, weakRefM, weakRef));

  auto targetGenLE = getTargetGenFromWeakRef(builder, kindStructs, weakRefM->kind, weakFatPtrLE);

  auto innerRefLE = fatWeaks.getInnerRefFromWeakRef(functionState, builder, weakRefM, weakFatPtrLE);

  // I don't think anything inside a weak ref is going to be more than 8 bytes...
  // if it is, we got problems below.
  assert(LLVMABISizeOfType(globalState->dataLayout, LLVMTypeOf(innerRefLE)) == 8);

  buildFlare(FL(), globalState, functionState, builder, "bork ", targetGenLE);

  auto handleLE =
      ::implodeConcreteHandle(
          globalState,
          builder,
          globalState->getConcreteHandleStruct(),
          constI64LE(globalState, externHandleRegionId),
          LLVMBuildPtrToInt(builder, innerRefLE, LLVMInt64TypeInContext(globalState->context), "objPtrA"),
          targetGenLE,
          constI32LE(globalState, externHandleGenOffset));

  return handleLE;
}

LLVMValueRef HybridGenerationalMemory::implodeInterfaceHandle(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef) {
  auto weakFatPtrLE =
      kindStructs->makeWeakFatPtr(
          weakRefM,
          globalState->getRegion(weakRefM)->checkValidReference(FL(), functionState, builder, weakRefM, weakRef));

  auto targetGenLE = getTargetGenFromWeakRef(builder, kindStructs, weakRefM->kind, weakFatPtrLE);

  LLVMValueRef itablePtrLE = nullptr, objPtrLE = nullptr;
  std::tie(itablePtrLE, objPtrLE) =
      explodeWeakInterfaceRef(
          globalState, functionState, builder, kindStructs, &fatWeaks, kindStructs, weakRefM, weakRef,
          [this, functionState, builder, weakRefM](WeakFatPtrLE weakFatPtrLE) {
            return weakInterfaceRefToWeakStructRef(
                functionState, builder, weakRefM, weakFatPtrLE);
          });

  // The object might not exist, so skip the check.
  auto interfaceFatPtrLE =
      kindStructs->makeInterfaceFatPtrWithoutChecking(
          FL(), functionState, builder,
          weakRefM, // It's still conceptually weak even though its not in a weak pointer.
          fatWeaks.getInnerRefFromWeakRef(functionState, builder, weakRefM, weakFatPtrLE));
  auto controlBlockPtrLE =
      kindStructs->getControlBlockPtrWithoutChecking(
          FL(), functionState, builder, weakRefM->kind, interfaceFatPtrLE);

  auto handleLE =
      ::implodeInterfaceHandle(
          globalState,
          builder,
          globalState->getInterfaceHandleStruct(),
          constI64LE(globalState, externHandleRegionId),
          LLVMBuildPtrToInt(builder, itablePtrLE, LLVMInt64TypeInContext(globalState->context), "itablePtr"),
          LLVMBuildPtrToInt(builder, controlBlockPtrLE.refLE, LLVMInt64TypeInContext(globalState->context), "objPtrB"),
          targetGenLE,
          constI32LE(globalState, externHandleGenOffset));

  return handleLE;
}
