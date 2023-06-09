#include <llvm-c/Types.h>
#include "../../../globalstate.h"
#include "../../../function/function.h"
#include "../../../function/expressions/shared/shared.h"
#include "../controlblock.h"
#include "../../../utils/counters.h"
#include "../../../utils/branch.h"
#include "../common.h"
#include "hgm.h"
#include <region/common/migration.h>

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;

constexpr int FIRST_GEN = 25600;

HybridGenerationalMemory::HybridGenerationalMemory(
    GlobalState* globalState_,
    KindStructs* kindStructs_,
    bool elideChecksForKnownLive_,
    bool limitMode_)
  : globalState(globalState_),
    fatWeaks(globalState_, kindStructs_),
    kindStructs(kindStructs_),
    elideChecksForKnownLive(elideChecksForKnownLive_) {
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  nextGenGlobalI32LE = LLVMAddGlobal(globalState_->mod, int32LT, "__vale_nextGen");
  LLVMSetInitializer(nextGenGlobalI32LE, constI32LE(globalState, FIRST_GEN));
}

LLVMValueRef HybridGenerationalMemory::getTargetGenFromWeakRef(
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Kind* kind,
    WeakFatPtrLE weakRefLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
             globalState->opt->regionOverride == RegionOverride::SAFE);
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
         globalState->opt->regionOverride == RegionOverride::SAFE);
  auto headerLE = LLVMGetUndef(kindStructs->getWeakRefHeaderStruct(kind));
  headerLE =
      LLVMBuildInsertValue(
          builder, headerLE, targetGenLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "header");
  return headerLE;
}

static LLVMValueRef getGenerationFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtr) {
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
             globalState->opt->regionOverride == RegionOverride::SAFE);

  assert(LLVMTypeOf(controlBlockPtr.refLE) == LLVMPointerType(structs->getControlBlock(kindM)->getStruct(), 0));

  auto genPtrLE =
      LLVMBuildStructGEP2(
          builder,
          structs->getControlBlock(kindM)->getStruct(),
          controlBlockPtr.refLE,
          structs->getControlBlock(kindM)->getMemberIndex(
              globalState->opt->generationSize == 64 ?
              ControlBlockMember::GENERATION_64B :
              ControlBlockMember::GENERATION_32B),
          "genPtr");
  return LLVMBuildLoad2(builder, int32LT, genPtrLE, "genB");
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
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::SAFE:
      // continue
      break;
    case RegionOverride::FAST:
    case RegionOverride::NAIVE_RC:
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
  assert(
      sourceType->ownership == Ownership::OWN ||
      sourceType->ownership == Ownership::MUTABLE_SHARE ||
      sourceType->ownership == Ownership::IMMUTABLE_SHARE);
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
  } else if (sourceType->ownership == Ownership::MUTABLE_BORROW || sourceType->ownership == Ownership::IMMUTABLE_BORROW) {
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
  } else if (sourceSSAMT->ownership == Ownership::IMMUTABLE_BORROW || sourceSSAMT->ownership == Ownership::MUTABLE_BORROW) {
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
  } else if (sourceSSAMT->ownership == Ownership::MUTABLE_BORROW || sourceSSAMT->ownership == Ownership::IMMUTABLE_BORROW) {
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

LiveRef HybridGenerationalMemory::lockGenFatPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refM,
    Ref ref,
    bool knownLive) {
  auto maybeAliveRefLE = globalState->getRegion(refM)->checkValidReference(FL(), functionState, builder, false, refM, ref);
  auto weakFatPtrLE = kindStructs->makeWeakFatPtr(refM, maybeAliveRefLE);

//  if ((knownLive || refM->ownership == Ownership::IMMUTABLE_SHARE || refM->ownership == Ownership::IMMUTABLE_BORROW) && elideChecksForKnownLive) {
//    globalState->getRegion(refM)
//        ->checkValidReference(FL(), functionState, builder, true, refM, ref);
//    // Do nothing
//  } else {
    if (globalState->opt->printMemOverhead) {
      adjustCounterV(
          globalState, builder, globalState->metalCache->i64, globalState->livenessCheckCounterLE,
          1, false);
    }
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, refM, weakFatPtrLE, knownLive);
    buildIfV(
        globalState, functionState, builder, LLVMBuildNot(builder, isAliveLE, "notAlive"),
        [this, from](LLVMBuilderRef thenBuilder) {
          fastPanic(globalState, from, thenBuilder);
        });
//  }
  auto refLE =
      fatWeaks.getInnerRefFromWeakRef(
          functionState, builder, refM,
          kindStructs->makeWeakFatPtr(
              refM, ::checkValidReference(FL(), globalState, functionState, builder, true, refM, ref)));
  auto wPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, refM, refLE);
  return ::toLiveRef(FL(), globalState, functionState, builder, regionInstanceRef, refM, wPtrLE.refLE);
}

LiveRef HybridGenerationalMemory::preCheckFatPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refM,
    Ref ref,
    bool knownLive) {
  auto maybeAliveRefLE =
      globalState->getRegion(refM)->checkValidReference(
          FL(), functionState, builder, false, refM, ref);
  auto weakFatPtrLE = kindStructs->makeWeakFatPtr(refM, maybeAliveRefLE);

//  assert(refM->ownership == Ownership::MUTABLE_BORROW);

  if (knownLive && elideChecksForKnownLive) {
    // Do nothing, just wrap it and return it.
    return toLiveRef(FL(), globalState, functionState, builder, regionInstanceRef, refM, ref, true);
  } else {
    if (globalState->opt->printMemOverhead) {
      adjustCounterV(
          globalState, builder, globalState->metalCache->i64,
          globalState->livenessPreCheckCounterLE, 1, false);
    }
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, refM, weakFatPtrLE, knownLive);
    auto resultRef =
        buildIfElseV(
            globalState, functionState, builder,
            wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isZeroLE(builder, isAliveLE)),
            refM, refM,
            [this, from, functionState, refM, weakFatPtrLE](LLVMBuilderRef thenBuilder) -> Ref {
              return crashifyReference(functionState, thenBuilder, refM, weakFatPtrLE);
            },
            [from, ref](LLVMBuilderRef elseBuilder) -> Ref {
              return ref;
            });
    return toLiveRef(FL(), globalState, functionState, builder, regionInstanceRef, refM, resultRef, true);
  }
}

WrapperPtrLE HybridGenerationalMemory::getWrapperPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref ref) {
  auto refLE =
      globalState->getRegion(refM)->checkValidReference(
          FL(), functionState, builder, false, refM, ref);
  switch (refM->ownership) {
    case Ownership::OWN:
      return kindStructs->makeWrapperPtr(FL(), functionState, builder, refM, refLE);
    case Ownership::IMMUTABLE_BORROW:
    case Ownership::MUTABLE_BORROW: {
      auto weakFatPtrLE = kindStructs->makeWeakFatPtr(refM, refLE);
      auto innerLE = fatWeaks.getInnerRefFromWeakRef(functionState, builder, refM, weakFatPtrLE);
      globalState->getRegion(refM)
          ->checkValidReference(FL(), functionState, builder, true, refM, ref);
      return kindStructs->makeWrapperPtr(FL(), functionState, builder, refM, innerLE);
    }
    default:
      assert(false);
      break;
  }
  assert(false);
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
  buildFlare(FL(), globalState, functionState, builder, "In getIsAliveFromWeakFatPtr ", knownLive, elideChecksForKnownLive);

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

    buildFlare(FL(), globalState, functionState, builder, "Comparing ", actualGenLE, " and ", targetGenLE);

    auto isLiveLE = LLVMBuildICmp(builder, LLVMIntEQ, actualGenLE, targetGenLE, "isLive");
    if (knownLive && !elideChecksForKnownLive) {
      // See MPESC for status codes
      buildAssertWithExitCodeV(
          globalState, functionState, builder, isLiveLE, 116, "knownLive is true, but object is dead!");
    }

    assert(LLVMTypeOf(isLiveLE) == LLVMInt1TypeInContext(globalState->context));
    return isLiveLE;
  }
  assert(false);
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
        weakRefM->ownership == Ownership::MUTABLE_BORROW ||
        weakRefM->ownership == Ownership::IMMUTABLE_BORROW ||
            weakRefM->ownership == Ownership::WEAK);

    auto weakFatPtrLE =
        kindStructs->makeWeakFatPtr(
            weakRefM,
            globalState->getRegion(weakRefM)
                ->checkValidReference(
                    FL(), functionState, builder, false, weakRefM, weakRef));
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, weakRefM, weakFatPtrLE, knownLive);
    return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isAliveLE);
  }
}

LLVMValueRef HybridGenerationalMemory::fillWeakableControlBlock(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Kind* kindM,
    LLVMValueRef controlBlockLE) {
  // The generation was already incremented when we freed it (or malloc'd it for the first time), but
  // it's very likely that someone else overwrote it with something else, such as a zero. We don't want
  // to use that, we want to use a random gen.
  auto newGenLE =
      adjustCounterVReturnOld(
          globalState, builder, globalState->metalCache->i32, nextGenGlobalI32LE, 1);

  int genMemberIndex =
      kindStructs->getControlBlock(kindM)->getMemberIndex(
          globalState->opt->generationSize == 64 ?
          ControlBlockMember::GENERATION_64B :
          ControlBlockMember::GENERATION_32B);
  auto newControlBlockLE =
      LLVMBuildInsertValue(builder, controlBlockLE, newGenLE, genMemberIndex, "newControlBlock");

  return newControlBlockLE;
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
    bool expectLive,
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
    buildCheckGen(globalState, functionState, builder, expectLive, targetGen, actualGen);

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
            ->checkValidReference(FL(), functionState, builder, false, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleStructWeakRef(
            functionState, builder, sourceType, targetType, structKind, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto interfaceKindM = dynamic_cast<InterfaceKind*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, false, sourceType, sourceRef);
    auto sourceInterfaceFatPtrLE = kindStructs->makeInterfaceFatPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleInterfaceWeakRef(
            functionState, builder, sourceType, targetType, interfaceKindM, sourceInterfaceFatPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto staticSizedArray = dynamic_cast<StaticSizedArrayT*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, false, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleStaticSizedArrayWeakRef(
            functionState, builder, sourceType, staticSizedArray, targetType, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto runtimeSizedArray = dynamic_cast<RuntimeSizedArrayT*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, false, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleRuntimeSizedArrayWeakRef(
            functionState, builder, sourceType, runtimeSizedArray, targetType, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else assert(false);
}

Ref HybridGenerationalMemory::crashifyReference(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refMT,
    WeakFatPtrLE weakFatPtrLE) {
  // Tried doing constI64LE(globalState, 13371337UL) here but it caused a LLVM crash ("Cannot emit
  // physreg copy instruction"). Perhaps LLVM has trouble with inline constants for insertvalue
  // instructions or something.
  auto genLE = getTargetGenFromWeakRef(builder, kindStructs, refMT->kind, weakFatPtrLE);
  auto crashPtrLE = LLVMBuildLoad2(builder,LLVMPointerType(LLVMInt64TypeInContext(globalState->context), 0), globalState->crashGlobalLE, "crashVoidPtrLE");

  auto innerLE = fatWeaks.getInnerRefFromWeakRef(functionState, builder, refMT, weakFatPtrLE);

  if (auto structKind = dynamic_cast<StructKind*>(refMT->kind)) {
    auto oldStructPtrLE = innerLE;
    auto newStructPtrLE =
        LLVMBuildPointerCast(builder, crashPtrLE, LLVMTypeOf(oldStructPtrLE), "crashPtrLE");
    auto structLT = kindStructs->getStructWrapperStruct(structKind);
    auto newStructWrapperPtrLE = WrapperPtrLE(refMT, structLT, newStructPtrLE);
    auto resultLE =
        assembleStructWeakRef(
            functionState, builder, refMT, structKind, genLE, newStructWrapperPtrLE);
    return wrap(globalState->getRegion(refMT), refMT, resultLE);
  } else if (auto interfaceKindM = dynamic_cast<InterfaceKind*>(refMT->kind)) {
    auto oldInterfaceFatPtrLE =
        kindStructs->makeInterfaceFatPtr(FL(), functionState, builder, refMT, innerLE);
    // Preserve the old itable ptr, I'd think we still want function calls on it to go through.
    // Not super important though.
    auto itablePtrLE =
        getItablePtrFromInterfacePtr(
            globalState, functionState, builder, refMT, oldInterfaceFatPtrLE);
    // Now package it up with the crash pointer and the new generation.
    auto newInterfaceFatPtrRawLE =
        makeInterfaceRefStruct(
            globalState, functionState, builder, kindStructs, interfaceKindM, crashPtrLE, itablePtrLE);
    auto newInterfaceFatPtrLE =
        kindStructs->makeInterfaceFatPtrWithoutChecking(
            FL(), functionState, builder, refMT, newInterfaceFatPtrRawLE);
    auto newInterfaceWeakFatPtrLE =
        assembleInterfaceWeakRef(
            functionState, builder, refMT, interfaceKindM, genLE, newInterfaceFatPtrLE);
    return wrap(globalState->getRegion(refMT), refMT, newInterfaceWeakFatPtrLE);
  } else if (auto staticSizedArray = dynamic_cast<StaticSizedArrayT*>(refMT->kind)) {
    auto oldSsaPtrLE = innerLE;
    auto newSsaPtrLE =
        LLVMBuildPointerCast(builder, crashPtrLE, LLVMTypeOf(oldSsaPtrLE), "crashPtrLE");
    auto ssaWrapperStructLT = kindStructs->getStaticSizedArrayWrapperStruct(staticSizedArray);
    auto newSsaWrapperPtrLE = WrapperPtrLE(refMT, ssaWrapperStructLT, newSsaPtrLE);
    auto resultLE =
        assembleStaticSizedArrayWeakRef(
            functionState, builder, refMT, staticSizedArray, genLE, newSsaWrapperPtrLE);
    return wrap(globalState->getRegion(refMT), refMT, resultLE);
  } else if (auto runtimeSizedArray = dynamic_cast<RuntimeSizedArrayT*>(refMT->kind)) {
    auto oldRsaPtrLE = innerLE;
    auto newRsaPtrLE =
        LLVMBuildPointerCast(builder, crashPtrLE, LLVMTypeOf(oldRsaPtrLE), "crashPtrLE");
    auto rsaWrapperStructLT = kindStructs->getRuntimeSizedArrayWrapperStruct(runtimeSizedArray);
    auto newRsaWrapperPtrLE = WrapperPtrLE(refMT, rsaWrapperStructLT, newRsaPtrLE);
    auto resultLE =
        assembleRuntimeSizedArrayWeakRef(
            functionState, builder, refMT, runtimeSizedArray, genLE, newRsaWrapperPtrLE);
    return wrap(globalState->getRegion(refMT), refMT, resultLE);
  } else assert(false);
}

//WrapperPtrLE HybridGenerationalMemory::getWrapperPtr(
//    AreaAndFileAndLine from,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* sourceType,
//    LiveRef sourceRef) {
//  // Now we need to package it up into a weak ref.
//  if (auto structKind = dynamic_cast<StructKind*>(sourceType->kind)) {
//    auto sourceRefLE =
//        globalState->getRegion(sourceType)
//            ->checkValidReference(FL(), functionState, builder, false, sourceType, sourceRef.inner);
//    return kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
//  } else if (auto interfaceKindM = dynamic_cast<InterfaceKind*>(sourceType->kind)) {
//    assert(false);
////    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, false, sourceType, sourceRef.inner);
////    return kindStructs->makeInterfaceFatPtr(FL(), functionState, builder, sourceType, sourceRefLE);
//  } else if (auto staticSizedArray = dynamic_cast<StaticSizedArrayT*>(sourceType->kind)) {
//    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, false, sourceType, sourceRef.inner);
//    return kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
//  } else if (auto runtimeSizedArray = dynamic_cast<RuntimeSizedArrayT*>(sourceType->kind)) {
//    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, false, sourceType, sourceRef.inner);
//    return kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
//  } else assert(false);
//}

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
          globalState->getRegion(weakRefM)->checkValidReference(FL(), functionState, builder, false, weakRefM, weakRef));

  auto targetGenLE = getTargetGenFromWeakRef(builder, kindStructs, weakRefM->kind, weakFatPtrLE);

  auto innerRefLE = fatWeaks.getInnerRefFromWeakRef(functionState, builder, weakRefM, weakFatPtrLE);

  // I don't think anything inside a weak ref is going to be more than 8 bytes...
  // if it is, we got problems below.
  assert(LLVMABISizeOfType(globalState->dataLayout, LLVMTypeOf(innerRefLE)) == 8);

  buildFlare(FL(), globalState, functionState, builder, "bork ", targetGenLE);

  auto handleLE =
      globalState->getUniversalRefStructLT()->implodeForGenerationalConcrete(
          globalState,
          functionState,
          builder,
          LLVMBuildPtrToInt(builder, innerRefLE, LLVMInt64TypeInContext(globalState->context), "objPtrA"),
          targetGenLE);

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
          globalState->getRegion(weakRefM)
              ->checkValidReference(FL(), functionState, builder, false, weakRefM, weakRef));

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
      globalState->getUniversalRefStructLT()->implodeForGenerationalInterface(
          globalState,
          functionState,
          builder,
          LLVMBuildPtrToInt(builder, itablePtrLE, LLVMInt64TypeInContext(globalState->context), "itablePtr"),
          LLVMBuildPtrToInt(builder, controlBlockPtrLE.refLE, LLVMInt64TypeInContext(globalState->context), "objPtrB"),
          targetGenLE);
  return handleLE;
}

void HybridGenerationalMemory::deallocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    LiveRef sourceRef) {
  // Increment the generation since we're freeing it.
  auto sourceWrapperPtr =
      toWrapperPtr(functionState, builder, kindStructs, sourceRefMT, sourceRef);
  auto controlBlockPtr =
      kindStructs->getConcreteControlBlockPtr(
          FL(), functionState, builder, sourceRefMT, sourceWrapperPtr);
  int genMemberIndex =
      kindStructs->getControlBlock(sourceRefMT->kind)->getMemberIndex(
          globalState->opt->generationSize == 64 ?
          ControlBlockMember::GENERATION_64B :
          ControlBlockMember::GENERATION_32B);
  auto genPtrLE =
      LLVMBuildStructGEP2(
          builder,
          kindStructs->getControlBlock(sourceRefMT->kind)->getStruct(),
          controlBlockPtr.refLE,
          genMemberIndex,
          "genPtr");
  adjustCounterV(globalState, builder, globalState->metalCache->i32, genPtrLE, 1, false);

  innerDeallocate(from, globalState, functionState, kindStructs, builder, sourceRefMT, sourceRef);
}