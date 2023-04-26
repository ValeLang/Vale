#include "../common/fatweaks/fatweaks.h"
#include "../common/hgm/hgm.h"
#include "../common/lgtweaks/lgtweaks.h"
#include "../common/wrcweaks/wrcweaks.h"
#include "../../translatetype.h"
#include "../common/common.h"
#include "../../utils/counters.h"
#include "../common/controlblock.h"
#include "../../utils/branch.h"
#include "../common/heap.h"
#include "../../function/expressions/shared/members.h"
#include "../../function/expressions/shared/elements.h"
#include "../../function/expressions/shared/string.h"
#include "safe.h"
#include <sstream>

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;
constexpr int WEAK_REF_MEMBER_INDEX_FOR_HEADER = 0;
constexpr int WEAK_REF_MEMBER_INDEX_FOR_OBJPTR = 1;

static WeakFatPtrLE assembleWeakFatPtr(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* weakRefMT,
    LLVMTypeRef weakRefStruct,
    LLVMValueRef headerLE,
    LLVMValueRef innerRefLE) {
  auto weakRefLE = LLVMGetUndef(weakRefStruct);
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, innerRefLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,"");
  return kindStructs->makeWeakFatPtr(weakRefMT, weakRefLE);
}

static LLVMValueRef makeGenHeader(
    GlobalState* globalState,
    KindStructs* kindStructs,
    LLVMBuilderRef builder,
    Kind* kind,
    LLVMValueRef targetGenLE) {
  auto headerLE = LLVMGetUndef(kindStructs->getWeakRefHeaderStruct(kind));
  headerLE = LLVMBuildInsertValue(builder, headerLE, targetGenLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "header");
  return headerLE;
}

static LLVMValueRef getGenerationPtrFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtr) {
  assert(LLVMTypeOf(controlBlockPtr.refLE) == LLVMPointerType(structs->getControlBlock(kindM)->getStruct(), 0));
  auto genPtrLE =
      LLVMBuildStructGEP2(
          builder,
          controlBlockPtr.structLT,
          controlBlockPtr.refLE,
          structs->getControlBlock(kindM)->getMemberIndex(ControlBlockMember::GENERATION),
          "genPtr");
  return genPtrLE;
}

static LLVMValueRef getGenerationFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtr) {
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);
  auto genPtrLE =
      getGenerationPtrFromControlBlockPtr(globalState, builder, structs, kindM, controlBlockPtr);
  auto resultLE = LLVMBuildLoad2(builder, genLT, genPtrLE, "genD");
  assert(LLVMTypeOf(resultLE) == genLT);
  return resultLE;
}

static WeakFatPtrLE assembleStructWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* targetTypeM,
    StructKind* structKindM,
    LLVMValueRef currentGenLE,
    WrapperPtrLE objPtrLE) {
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);
  assert(LLVMTypeOf(currentGenLE) == genLT);
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, structKindM, currentGenLE);
  auto weakRefStructLT = kindStructs->getStructWeakRefStruct(structKindM);
  return assembleWeakFatPtr(
      functionState, builder, kindStructs, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

static WeakFatPtrLE assembleStructWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* structTypeM,
    Reference* targetTypeM,
    StructKind* structKindM,
    WrapperPtrLE objPtrLE) {
  auto controlBlockPtrLE = kindStructs->getConcreteControlBlockPtr(FL(), functionState, builder, structTypeM, objPtrLE);
  auto currentGenLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, structTypeM->kind, controlBlockPtrLE);
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);
  assert(LLVMTypeOf(currentGenLE) == genLT);
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, structKindM, currentGenLE);
  auto weakRefStructLT = kindStructs->getStructWeakRefStruct(structKindM);
  return assembleWeakFatPtr(
      functionState, builder, kindStructs, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}


static LLVMValueRef getHeaderFromWeakRef(
    LLVMBuilderRef builder,
    WeakFatPtrLE weakRefLE) {
  return LLVMBuildExtractValue(builder, weakRefLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "weakRefHeader");
}

static LLVMValueRef getTargetGenFromWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Kind* kind,
    WeakFatPtrLE weakRefLE) {
  auto headerLE = getHeaderFromWeakRef(builder, weakRefLE);
  assert(LLVMTypeOf(headerLE) == kindStructs->getWeakRefHeaderStruct(kind));
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "actualGeni");
}

// Dont use this function for V2
static LLVMValueRef getInnerRefFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    WeakFatPtrLE weakFatPtrLE) {
  assert(
      weakRefM->ownership == Ownership::MUTABLE_BORROW ||
      weakRefM->ownership == Ownership::IMMUTABLE_BORROW ||
      weakRefM->ownership == Ownership::WEAK);

  auto innerRefLE = LLVMBuildExtractValue(builder, weakFatPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR, "");
  // We dont check that its valid because if it's a weak ref, it might *not* be pointing at
  // a valid reference.
  return innerRefLE;
}

static LLVMValueRef getInnerRefFromWeakRefWithoutCheck(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    WeakFatPtrLE weakRefLE) {
  assert(
      weakRefM->ownership == Ownership::IMMUTABLE_BORROW ||
      weakRefM->ownership == Ownership::MUTABLE_BORROW ||
      weakRefM->ownership == Ownership::WEAK);

  auto innerRefLE =
      LLVMBuildExtractValue(builder, weakRefLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR, "");
  // We dont check that its valid because if it's a weak ref, it might *not* be pointing at
  // a valid reference.
  return innerRefLE;
}

static LLVMValueRef getIsAliveFromWeakFatPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* weakRefM,
    WeakFatPtrLE weakFatPtrLE,
    bool knownLive) {
  bool skipCheck =
      (globalState->opt->elideChecksForKnownLive && knownLive) ||
      (globalState->opt->elideChecksForRegions && weakRefM->ownership == Ownership::IMMUTABLE_BORROW);
  if (!globalState->opt->census && skipCheck) {
    return LLVMConstInt(LLVMInt1TypeInContext(globalState->context), 1, false);
  } else {
    // Get target generation from the ref
    auto targetGenLE = getTargetGenFromWeakRef(globalState, builder, kindStructs, weakRefM->kind, weakFatPtrLE);

    // Get actual generation from the table
    auto innerRefLE =
        getInnerRefFromWeakRefWithoutCheck(functionState, builder, weakRefM,
            weakFatPtrLE);
    auto controlBlockPtrLE =
        kindStructs->getControlBlockPtrWithoutChecking(
            FL(), functionState, builder, innerRefLE, weakRefM);
    auto actualGenLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, weakRefM->kind,
        controlBlockPtrLE);

    buildFlare(FL(), globalState, functionState, builder, "Comparing ", actualGenLE, " and ", targetGenLE);

    auto isLiveLE = LLVMBuildICmp(builder, LLVMIntEQ, actualGenLE, targetGenLE, "isLive");
    if (globalState->opt->census && skipCheck) {
      // See MPESC for status codes
      buildAssertWithExitCodeV(
          globalState, functionState, builder, isLiveLE, 116, "knownLive is true, but object is dead!");
    }

    return isLiveLE;
  }
}

static WeakFatPtrLE assembleRuntimeSizedArrayWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* targetTypeM,
    RuntimeSizedArrayT* staticSizedArrayMT,
    LLVMValueRef currentGenLE,
    WrapperPtrLE objPtrLE) {
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);
  assert(LLVMTypeOf(currentGenLE) == genLT);
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, staticSizedArrayMT, currentGenLE);
  auto weakRefStructLT = kindStructs->getRuntimeSizedArrayWeakRefStruct(staticSizedArrayMT);
  return assembleWeakFatPtr(
      functionState, builder, kindStructs, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

static WeakFatPtrLE assembleRuntimeSizedArrayWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
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
      globalState, functionState, builder, kindStructs, targetSSAWeakRefMT, staticSizedArrayMT, genLE, sourceRefLE);
}


static WeakFatPtrLE assembleStaticSizedArrayWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* targetTypeM,
    StaticSizedArrayT* staticSizedArrayMT,
    LLVMValueRef currentGenLE,
    WrapperPtrLE objPtrLE) {
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);
  assert(LLVMTypeOf(currentGenLE) == genLT);
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, staticSizedArrayMT, currentGenLE);
  auto weakRefStructLT = kindStructs->getStaticSizedArrayWeakRefStruct(staticSizedArrayMT);
  return assembleWeakFatPtr(
      functionState, builder, kindStructs, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

static WeakFatPtrLE assembleStaticSizedArrayWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
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
      globalState, functionState, builder, kindStructs, targetSSAWeakRefMT, staticSizedArrayMT, genLE, sourceRefLE);
}


static WeakFatPtrLE assembleInterfaceWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* targetType,
    InterfaceKind* interfaceKindM,
    LLVMValueRef currentGenLE,
    InterfaceFatPtrLE sourceInterfaceFatPtrLE) {
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);
  assert(LLVMTypeOf(currentGenLE) == genLT);
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, interfaceKindM, currentGenLE);
  auto weakRefStructLT =
      kindStructs->getInterfaceWeakRefStruct(interfaceKindM);
  return assembleWeakFatPtr(
      functionState, builder, kindStructs, targetType, weakRefStructLT, headerLE, sourceInterfaceFatPtrLE.refLE);
}

// Makes a non-weak interface ref into a weak interface ref
static WeakFatPtrLE assembleInterfaceWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
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
  return assembleInterfaceWeakRef(globalState, functionState, builder, kindStructs, targetType, interfaceKindM, genLE, sourceInterfaceFatPtrLE);
}

static Ref assembleWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
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
            globalState, functionState, builder, kindStructs, sourceType, targetType, structKind, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto interfaceKindM = dynamic_cast<InterfaceKind*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, false, sourceType, sourceRef);
    auto sourceInterfaceFatPtrLE = kindStructs->makeInterfaceFatPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleInterfaceWeakRef(
            globalState, functionState, builder, kindStructs, sourceType, targetType, interfaceKindM, sourceInterfaceFatPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto staticSizedArray = dynamic_cast<StaticSizedArrayT*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, false, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleStaticSizedArrayWeakRef(
            globalState, functionState, builder, kindStructs, sourceType, staticSizedArray, targetType, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto runtimeSizedArray = dynamic_cast<RuntimeSizedArrayT*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, false, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleRuntimeSizedArrayWeakRef(
            globalState, functionState, builder, kindStructs, sourceType, runtimeSizedArray, targetType, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else assert(false);
}

static ControlBlock makeSafeNonWeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutControlBlock"));
  controlBlock.addMember(ControlBlockMember::GENERATION);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.build();
  return controlBlock;
}

static ControlBlock makeSafeWeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutControlBlock"));
  controlBlock.addMember(ControlBlockMember::GENERATION);
  // controlBlock.addMember(ControlBlockMember::WEAK_SOMETHING); impl weaks
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.build();
  return controlBlock;
}

static LLVMTypeRef makeSafeWeakRefHeaderStruct(GlobalState* globalState) {
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);

  auto refStructL = LLVMStructCreateNamed(globalState->context, "__SafeWeakRef");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN == memberTypesL.size());
  memberTypesL.push_back(genLT);

  LLVMStructSetBody(refStructL, memberTypesL.data(), memberTypesL.size(), false);

  return refStructL;
}

static Ref crashifyReference(
    GlobalState* globalState,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference *refMT,
    WeakFatPtrLE weakFatPtrLE) {
  // Tried doing constI64LE(globalState, 13371337UL) here but it caused a LLVM crash ("Cannot emit
  // physreg copy instruction"). Perhaps LLVM has trouble with inline constants for insertvalue
  // instructions or something.
  auto genLE = getTargetGenFromWeakRef(globalState, builder, kindStructs, refMT->kind, weakFatPtrLE);
  auto crashPtrLE = LLVMBuildLoad2(builder, LLVMPointerType(LLVMInt64TypeInContext(globalState->context), 0), globalState->crashGlobalLE, "crashVoidPtrLE");

  auto innerLE = getInnerRefFromWeakRef(functionState, builder, refMT, weakFatPtrLE);

  if (auto structKind = dynamic_cast<StructKind*>(refMT->kind)) {
    auto oldStructPtrLE = innerLE;
    auto newStructPtrLE =
        LLVMBuildPointerCast(builder, crashPtrLE, LLVMTypeOf(oldStructPtrLE), "crashPtrLE");

    auto structWrapperLT = kindStructs->getStructWrapperStruct(structKind);
    auto newStructWrapperPtrLE = WrapperPtrLE(refMT, structWrapperLT, newStructPtrLE);
    auto resultLE =
        assembleStructWeakRef(
            globalState, functionState, builder, kindStructs, refMT, structKind, genLE, newStructWrapperPtrLE);
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
            globalState, functionState, builder, kindStructs, refMT, interfaceKindM, genLE, newInterfaceFatPtrLE);
    return wrap(globalState->getRegion(refMT), refMT, newInterfaceWeakFatPtrLE);
  } else if (auto staticSizedArray = dynamic_cast<StaticSizedArrayT*>(refMT->kind)) {
    auto oldSsaPtrLE = innerLE;
    auto newSsaPtrLE =
        LLVMBuildPointerCast(builder, crashPtrLE, LLVMTypeOf(oldSsaPtrLE), "crashPtrLE");
    auto wrapperStructLT = kindStructs->getStaticSizedArrayWrapperStruct(staticSizedArray);
    auto newSsaWrapperPtrLE = WrapperPtrLE(refMT, wrapperStructLT, newSsaPtrLE);
    auto resultLE =
        assembleStaticSizedArrayWeakRef(
            globalState, functionState, builder, kindStructs, refMT, staticSizedArray, genLE, newSsaWrapperPtrLE);
    return wrap(globalState->getRegion(refMT), refMT, resultLE);
  } else if (auto runtimeSizedArray = dynamic_cast<RuntimeSizedArrayT*>(refMT->kind)) {
    auto oldRsaPtrLE = innerLE;
    auto newRsaPtrLE =
        LLVMBuildPointerCast(builder, crashPtrLE, LLVMTypeOf(oldRsaPtrLE), "crashPtrLE");
    auto wrapperStructLT = kindStructs->getRuntimeSizedArrayWrapperStruct(runtimeSizedArray);
    auto newRsaWrapperPtrLE = WrapperPtrLE(refMT, wrapperStructLT, newRsaPtrLE);
    auto resultLE =
        assembleRuntimeSizedArrayWeakRef(
            globalState, functionState, builder, kindStructs, refMT, runtimeSizedArray, genLE, newRsaWrapperPtrLE);
    return wrap(globalState->getRegion(refMT), refMT, resultLE);
  } else assert(false);
}

static LiveRef preCheckFatPtr(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* refM,
    Ref ref,
    bool knownLive) {
  bool skipCheck =
      (globalState->opt->elideChecksForKnownLive && knownLive) ||
      (globalState->opt->elideChecksForRegions && refM->ownership == Ownership::IMMUTABLE_BORROW);

  auto maybeAliveRefLE =
      globalState->getRegion(refM)->checkValidReference(
          FL(), functionState, builder, false, refM, ref);
  auto weakFatPtrLE = kindStructs->makeWeakFatPtr(refM, maybeAliveRefLE);

  assert(refM->ownership == Ownership::MUTABLE_BORROW);

  if (skipCheck && !globalState->opt->census) {
    // Do nothing, just wrap it and return it.
    auto refLE = ::checkValidReference(FL(), globalState, functionState, builder, true, refM, ref);
    return LiveRef(refM, refLE);
  } else {
    if (globalState->opt->printMemOverhead) {
      adjustCounterV(
          globalState, builder, globalState->metalCache->i64,
          globalState->livenessPreCheckCounterLE, 1, false);
    }
    auto isAliveLE = getIsAliveFromWeakFatPtr(globalState, functionState, builder, kindStructs, refM, weakFatPtrLE, knownLive);
    auto resultRef =
        buildIfElseV(
            globalState, functionState, builder,
            wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isZeroLE(builder, isAliveLE)),
            refM, refM,
            [globalState, kindStructs, from, functionState, refM, weakFatPtrLE](LLVMBuilderRef thenBuilder) -> Ref {
              return crashifyReference(globalState, functionState, thenBuilder, kindStructs, refM, weakFatPtrLE);
            },
            [globalState, skipCheck, from, ref](LLVMBuilderRef elseBuilder) -> Ref {
              // If we get here, we know at runtime it's not alive.

              // If at compile-time we thought it was alive, then we found a bug!
              if (skipCheck && globalState->opt->census) {
                buildPrint(globalState, elseBuilder, "Known-live is actually dead! Exiting!\n");
                globalState->externs->exit.call(elseBuilder, {constI64LE(globalState, 1)}, "");
                return ref;
              } else {
                return ref;
              }
            });
    auto refLE = ::checkValidReference(FL(), globalState, functionState, builder, true, refM, resultRef);
    return LiveRef(refM, refLE);
  }
}

static WrapperPtrLE getWrapperPtr(
    GlobalState* globalState,
    KindStructs* kindStructs,
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LiveRef liveRef) {
  return kindStructs->makeWrapperPtr(FL(), functionState, builder, refM, liveRef.refLE);
}

static WrapperPtrLE lockGenFatPtr(
        GlobalState* globalState,
        AreaAndFileAndLine from,
        FunctionState* functionState,
        KindStructs* kindStructs,
        LLVMBuilderRef builder,
        Ref regionInstanceRef,
        Reference* refM,
        Ref ref,
        bool knownLive) {
  auto maybeAliveRefLE = globalState->getRegion(refM)->checkValidReference(FL(), functionState, builder, false, refM, ref);
  auto weakFatPtrLE = kindStructs->makeWeakFatPtr(refM, maybeAliveRefLE);

  bool skipCheck =
      (globalState->opt->elideChecksForRegions && refM->ownership == Ownership::IMMUTABLE_BORROW) ||
      (globalState->opt->elideChecksForKnownLive && knownLive);

  assert(refM->ownership != Ownership::IMMUTABLE_SHARE); // curious
  if (!globalState->opt->census && skipCheck) {
    globalState->getRegion(refM)
        ->checkValidReference(FL(), functionState, builder, true, refM, ref);
    // Do nothing
  } else {
    if (globalState->opt->printMemOverhead) {
      adjustCounterV(
          globalState, builder, globalState->metalCache->i64, globalState->livenessCheckCounterLE,
          1, false);
    }
    auto isAliveLE = getIsAliveFromWeakFatPtr(globalState, functionState, builder, kindStructs, refM, weakFatPtrLE, knownLive);
    buildIfV(
        globalState, functionState, builder, isZeroLE(builder, isAliveLE),
        [globalState, from, skipCheck](LLVMBuilderRef thenBuilder) {
          if (globalState->opt->census && skipCheck) {
            buildPrint(globalState, thenBuilder, "Known-live is actually dead! Exiting!\n");
            globalState->externs->exit.call(thenBuilder, {constI64LE(globalState, 1)}, "");
          } else {
            fastPanic(globalState, from, thenBuilder);
          }
        });
  }
  // Because we just checked
  auto refLE =
      getInnerRefFromWeakRef(
          functionState, builder, refM,
          kindStructs->makeWeakFatPtr(
              refM, ::checkValidReference(FL(), globalState, functionState, builder, true, refM, ref)));
  return kindStructs->makeWrapperPtr(FL(), functionState, builder, refM, refLE);
}

Safe::Safe(GlobalState* globalState_) :
    globalState(globalState_),
    kindStructs(
        globalState,
        makeSafeNonWeakableControlBlock(globalState),
        makeSafeWeakableControlBlock(globalState),
        makeSafeWeakRefHeaderStruct(globalState)),
    fatWeaks(globalState_, &kindStructs) {
  regionKind =
      globalState->metalCache->getStructKind(
          globalState->metalCache->getName(
              globalState->metalCache->builtinPackageCoord, namePrefix + "_Region"));
  regionRefMT =
      globalState->metalCache->getReference(
          Ownership::MUTABLE_BORROW, Location::YONDER, regionKind);
  globalState->regionIdByKind.emplace(regionKind, globalState->metalCache->mutRegionId);
  kindStructs.declareStruct(regionKind, Weakability::WEAKABLE);
  kindStructs.defineStruct(regionKind, {
      // This region doesnt need anything
  });
}

Reference* Safe::getRegionRefType() {
  return regionRefMT;
}

void Safe::mainSetup(FunctionState* functionState, LLVMBuilderRef builder) {
//  wrcWeaks.mainSetup(functionState, builder);
}

void Safe::mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) {
//  wrcWeaks.mainCleanup(functionState, builder);
}

RegionId* Safe::getRegionId() {
  return globalState->metalCache->mutRegionId;
}

LiveRef Safe::constructStaticSizedArray(
    Ref regionInstanceRef,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refMT,
    StaticSizedArrayT *ssaMT) {
  auto ssaDef = globalState->program->getStaticSizedArray(ssaMT);

  auto structLT =
      kindStructs.getStaticSizedArrayWrapperStruct(ssaMT);
  auto newStructLE =
      kindStructs.makeWrapperPtr(
          FL(), functionState, builder, refMT,
          mallocKnownSize(globalState, functionState, builder, refMT->location, structLT));

  auto controlBlockPtrLE =
      kindStructs.getConcreteControlBlockPtr(FL(), functionState, builder, refMT, newStructLE);

  fillControlBlock(FL(), functionState, builder, ssaMT, controlBlockPtrLE, ssaMT->name->name);

  return toLiveRef(newStructLE);
}

Ref Safe::mallocStr(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE,
    LLVMValueRef sourceCharsPtrLE) {
  assert(false);
  exit(1);
}

Ref Safe::allocate(
    Ref regionInstanceRef,
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* desiredReference,
    const std::vector<Ref>& memberRefs) {
  auto structKind = dynamic_cast<StructKind*>(desiredReference->kind);
  auto structM = globalState->program->getStruct(structKind);
  auto resultRef =
      innerAllocate(
          FL(), globalState, functionState, builder, desiredReference, &kindStructs, memberRefs, Weakability::WEAKABLE,
          [this, functionState, desiredReference, structM](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(), functionState, innerBuilder, desiredReference->kind,
                controlBlockPtrLE, structM->name->name);
          });
  return resultRef;
}

void Safe::alias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    Ref expr) {
  auto sourceRnd = sourceRef->kind;

  if (dynamic_cast<Int *>(sourceRnd) ||
      dynamic_cast<Bool *>(sourceRnd) ||
      dynamic_cast<Float *>(sourceRnd) ||
      dynamic_cast<Void *>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (dynamic_cast<InterfaceKind *>(sourceRnd) ||
             dynamic_cast<StructKind *>(sourceRnd) ||
             dynamic_cast<StaticSizedArrayT *>(sourceRnd) ||
             dynamic_cast<RuntimeSizedArrayT *>(sourceRnd) ||
             dynamic_cast<Str *>(sourceRnd)) {
    if (sourceRef->ownership == Ownership::OWN) {
      // We might be loading a member as an own if we're destructuring.
      // Don't adjust the RC, since we're only moving it.
    } else if (sourceRef->ownership == Ownership::MUTABLE_BORROW || sourceRef->ownership == Ownership::IMMUTABLE_BORROW) {
      // Do nothing, fast mode doesn't do stuff for borrow refs.
    } else if (sourceRef->ownership == Ownership::WEAK) {
      aliasWeakRef(from, functionState, builder, sourceRef, expr);
    } else if (sourceRef->ownership == Ownership::MUTABLE_SHARE || sourceRef->ownership == Ownership::IMMUTABLE_SHARE) {
      if (sourceRef->location == Location::INLINE) {
        // Do nothing, we can just let inline structs disappear
      } else {
        adjustStrongRc(from, globalState, functionState, &kindStructs, builder, expr, sourceRef, 1);
      }
    } else
      assert(false);
  } else {
    std::cerr << "Unimplemented type in acquireReference: "
              << typeid(*sourceRef->kind).name() << std::endl;
    assert(false);
  }
}

void Safe::dealias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto sourceRnd = sourceMT->kind;

  if (sourceMT->ownership == Ownership::MUTABLE_SHARE || sourceMT->ownership == Ownership::IMMUTABLE_SHARE) {
    assert(false);
  } else {
    if (sourceMT->ownership == Ownership::OWN) {
      // This can happen if we're sending an owning reference to the outside world, see DEPAR.
    } else if (sourceMT->ownership == Ownership::MUTABLE_BORROW || sourceMT->ownership == Ownership::IMMUTABLE_BORROW) {
      // Do nothing!
    } else if (sourceMT->ownership == Ownership::WEAK) {
      discardWeakRef(from, functionState, builder, sourceMT, sourceRef);
    } else assert(false);
  }
}

Ref Safe::weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) {
  assert(false);
//  return regularWeakAlias(globalState, functionState, &kindStructs, &wrcWeaks, builder, sourceRefMT, targetRefMT, sourceRef);
}

// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
WrapperPtrLE Safe::lockWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref weakRefLE,
    bool weakRefKnownLive) {
  assert(false);
//  switch (refM->ownership) {
//    case Ownership::OWN:
//    case Ownership::MUTABLE_SHARE:
//    case Ownership::IMMUTABLE_SHARE:
//    case Ownership::MUTABLE_BORROW:
//    case Ownership::IMMUTABLE_BORROW:
//      assert(false);
//      break;
//    case Ownership::WEAK: {
//      auto weakFatPtrLE =
//          kindStructs.makeWeakFatPtr(
//              refM,
//              checkValidReference(FL(), functionState, builder, false, refM, weakRefLE));
//      return kindStructs.makeWrapperPtr(
//          FL(), functionState, builder, refM,
//          wrcWeaks.lockWrciFatPtr(from, functionState, builder, refM, weakFatPtrLE));
//    }
//    default:
//      assert(false);
//      break;
//  }
}

Ref Safe::lockWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference* resultOptTypeM,
    Reference* constraintRefM,
    Reference* sourceWeakRefMT,
    Ref sourceWeakRefLE,
    bool weakRefKnownLive,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {
  assert(false);
}


Ref Safe::asSubtype(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* resultOptTypeM,
    Reference* sourceInterfaceRefMT,
    Ref sourceInterfaceRef,
    bool sourceRefKnownLive,
    Kind* targetKind,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {

  return regularDowncast(
      globalState, functionState, builder, &kindStructs, resultOptTypeM,
      sourceInterfaceRefMT, sourceInterfaceRef, sourceRefKnownLive, targetKind, buildThen, buildElse);
}

LLVMTypeRef Safe::translateType(Reference* referenceM) {
  if (referenceM == regionRefMT) {
    // We just have a raw pointer to region structs
    return LLVMPointerType(kindStructs.getStructInnerStruct(regionKind), 0);
  }
  switch (referenceM->ownership) {
    case Ownership::IMMUTABLE_SHARE:
    case Ownership::MUTABLE_SHARE:
      assert(false);
      break;
    case Ownership::OWN:
      assert(referenceM->location != Location::INLINE);
      return translateReferenceSimple(globalState, &kindStructs, referenceM->kind);
    case Ownership::IMMUTABLE_BORROW: {
      if (globalState->opt->elideChecksForRegions) {
        assert(referenceM->location != Location::INLINE);
        return translateReferenceSimple(globalState, &kindStructs, referenceM->kind);
      } else {
        assert(referenceM->location != Location::INLINE);
        return translateWeakReference(globalState, &kindStructs, referenceM->kind);
      }
      break;
    }
    case Ownership::MUTABLE_BORROW:
      assert(referenceM->location != Location::INLINE);
      return translateWeakReference(globalState, &kindStructs, referenceM->kind);
    case Ownership::WEAK:
      assert(false);
      break;
    default:
      assert(false);
      break;
  }
}

Ref Safe::upcastWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructKind* sourceStructKindM,
    Reference* sourceStructTypeM,
    InterfaceKind* targetInterfaceKindM,
    Reference* targetInterfaceTypeM) {
  assert(false);
//  auto resultWeakInterfaceFatPtr =
//      wrcWeaks.weakStructPtrToWrciWeakInterfacePtr(
//          globalState, functionState, builder, sourceRefLE, sourceStructKindM,
//          sourceStructTypeM, targetInterfaceKindM, targetInterfaceTypeM);
//  return wrap(this, targetInterfaceTypeM, resultWeakInterfaceFatPtr);
}

void Safe::declareStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT) {
  globalState->regionIdByKind.emplace(staticSizedArrayMT->kind, getRegionId());

  kindStructs.declareStaticSizedArray(staticSizedArrayMT->kind, Weakability::WEAKABLE);
}

void Safe::declareRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) {
  globalState->regionIdByKind.emplace(runtimeSizedArrayMT->kind, getRegionId());

  kindStructs.declareRuntimeSizedArray(runtimeSizedArrayMT->kind, Weakability::WEAKABLE);
}

void Safe::defineRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) {
  auto elementLT =
      globalState->getRegion(runtimeSizedArrayMT->elementType)
          ->translateType(runtimeSizedArrayMT->elementType);
  kindStructs.defineRuntimeSizedArray(runtimeSizedArrayMT, elementLT, true);
}

void Safe::defineStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT) {
  auto elementLT =
      globalState->getRegion(staticSizedArrayMT->elementType)
          ->translateType(staticSizedArrayMT->elementType);
  kindStructs.defineStaticSizedArray(staticSizedArrayMT, elementLT);
}

void Safe::declareStruct(
    StructDefinition* structM) {
  globalState->regionIdByKind.emplace(structM->kind, getRegionId());

  kindStructs.declareStruct(structM->kind, Weakability::WEAKABLE);
}

void Safe::defineStruct(
    StructDefinition* structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        globalState->getRegion(structM->members[i]->type)
            ->translateType(structM->members[i]->type));
  }
  kindStructs.defineStruct(structM->kind, innerStructMemberTypesL);
}

void Safe::declareEdge(
    Edge* edge) {
  kindStructs.declareEdge(edge);
}

void Safe::defineEdge(
    Edge* edge) {
  auto interfaceFunctionsLT = globalState->getInterfaceFunctionPointerTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  kindStructs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);
}

void Safe::declareInterface(
    InterfaceDefinition* interfaceM) {
  globalState->regionIdByKind.emplace(interfaceM->kind, getRegionId());

  kindStructs.declareInterface(interfaceM->kind, Weakability::WEAKABLE);
}

void Safe::defineInterface(
    InterfaceDefinition* interfaceM) {
  auto interfaceMethodTypesL = globalState->getInterfaceFunctionPointerTypes(interfaceM->kind);
  kindStructs.defineInterface(interfaceM, interfaceMethodTypesL);
}

void Safe::discardOwningRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    LiveRef sourceRef) {
  // Free it!
  deallocate(AFL("discardOwningRef"), functionState, builder, sourceMT, sourceRef);
}

void Safe::noteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtrLE) {
  assert(false);
//  // In fast mode, only shared things are strong RC'd
//  if (refM->ownership == Ownership::MUTABLE_SHARE || refM->ownership == Ownership::IMMUTABLE_SHARE) {
//    assert(false);
////    // Only shared stuff is RC'd in fast mode
////    auto rcIsZeroLE = strongRcIsZero(globalState, &kindStructs, builder, refM, controlBlockPtrLE);
////    buildAssertV(globalState, functionState, builder, rcIsZeroLE,
////        "Tried to free concrete that had nonzero RC!");
//  } else {
//    // It's a mutable, so mark WRCs dead
//
//    if (auto structKindM = dynamic_cast<StructKind *>(refM->kind)) {
//      auto structM = globalState->program->getStruct(structKindM);
//      if (structM->weakability == Weakability::WEAKABLE) {
//        wrcWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
//      }
//    } else if (auto interfaceKindM = dynamic_cast<InterfaceKind *>(refM->kind)) {
//      auto interfaceM = globalState->program->getInterface(interfaceKindM);
//      if (interfaceM->weakability == Weakability::WEAKABLE) {
//        wrcWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
//      }
//    } else {
//      // Do nothing, only structs and interfaces are weakable in assist mode.
//    }
//  }
}

void Safe::storeMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* structRefMT,
    LiveRef structRef,
    int memberIndex,
    const std::string& memberName,
    Reference* newMemberRefMT,
    Ref newMemberRef) {
  auto newMemberLE =
      globalState->getRegion(newMemberRefMT)->checkValidReference(
          FL(), functionState, builder, false, newMemberRefMT, newMemberRef);
  switch (structRefMT->ownership) {
    case Ownership::OWN:
    case Ownership::MUTABLE_SHARE:
    case Ownership::IMMUTABLE_SHARE:
    case Ownership::MUTABLE_BORROW:
    case Ownership::IMMUTABLE_BORROW: {
      storeMemberStrong(
          globalState, functionState, builder, &kindStructs, structRefMT, structRef,
          memberIndex, memberName, newMemberLE);
      break;
    }
    case Ownership::WEAK: {
      storeMemberWeak(
          globalState, functionState, builder, &kindStructs, structRefMT, structRef,
          memberIndex, memberName, newMemberLE);
      break;
    }
    default:
      assert(false);
  }
}

// Gets the itable PTR and the new value that we should put into the virtual param's slot
// (such as a void* or a weak void ref)
std::tuple<LLVMValueRef, LLVMValueRef> Safe::explodeInterfaceRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef) {
  assert(false);
//  switch (virtualParamMT->ownership) {
//    case Ownership::OWN:
//    case Ownership::MUTABLE_BORROW:
//    case Ownership::IMMUTABLE_BORROW:
//    case Ownership::MUTABLE_SHARE:
//    case Ownership::IMMUTABLE_SHARE: {
//      return explodeStrongInterfaceRef(
//          globalState, functionState, builder, &kindStructs, virtualParamMT, virtualArgRef);
//    }
//    case Ownership::WEAK: {
//      return explodeWeakInterfaceRef(
//          globalState, functionState, builder, &kindStructs, &fatWeaks, &kindStructs,
//          virtualParamMT, virtualArgRef,
//          [this, functionState, builder, virtualParamMT](WeakFatPtrLE weakFatPtrLE) {
//            return wrcWeaks.weakInterfaceRefToWeakStructRef(
//                functionState, builder, virtualParamMT, weakFatPtrLE);
//          });
//    }
//    default:
//      assert(false);
//  }
}

Ref Safe::getRuntimeSizedArrayLength(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* rsaRefMT,
    LiveRef arrayRef) {
  auto arrayWPtrLE = getWrapperPtrLive(FL(), functionState, builder, rsaRefMT, arrayRef);
  return ::getRuntimeSizedArrayLength(globalState, functionState, builder, arrayWPtrLE);

//  auto refLE =
//      globalState->getRegion(rsaRefMT)
//          ->checkValidReference(FL(), functionState, builder, true, rsaRefMT, arrayRef.inner);
//  auto wrapperPtrLE = kindStructs.makeWrapperPtr(FL(), functionState, builder, rsaRefMT, refLE);
//  return ::getRuntimeSizedArrayLength(globalState, functionState, builder, wrapperPtrLE);
}

Ref Safe::getRuntimeSizedArrayCapacity(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* rsaRefMT,
    LiveRef arrayRef) {
  auto arrayWPtrLE = getWrapperPtrLive(FL(), functionState, builder, rsaRefMT, arrayRef);
  return ::getRuntimeSizedArrayCapacity(globalState, functionState, builder, arrayWPtrLE);
}

LLVMValueRef Safe::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool expectLive,
    Reference* refM,
    Ref ref) {
  if (globalState->opt->census) {
    if (refM->ownership == Ownership::OWN) {
      Reference *actualRefM = nullptr;
      LLVMValueRef refLE = nullptr;
      std::tie(actualRefM, refLE) = megaGetRefInnardsForChecking(ref);
      assert(actualRefM == refM);
      assert(refLE != nullptr);
      assert(LLVMTypeOf(refLE) == translateType(refM));

      regularCheckValidReference(checkerAFL, globalState, functionState, builder, &kindStructs, refM, refLE);

      return refLE;
    } else if (refM->ownership == Ownership::MUTABLE_SHARE || refM->ownership == Ownership::IMMUTABLE_SHARE) {
      assert(false);
    } else {
      auto weakRef = ref;
      auto weakRefM = refM;

      Reference *actualRefM = nullptr;
      LLVMValueRef refLE = nullptr;
      std::tie(actualRefM, refLE) = hgmGetRefInnardsForChecking(weakRef);
      auto weakFatPtrLE = kindStructs.makeWeakFatPtr(weakRefM, refLE);
      auto innerLE =
          fatWeaks.getInnerRefFromWeakRefWithoutCheck(
              functionState, builder, weakRefM, weakFatPtrLE);

      auto controlBlockPtrLE =
          kindStructs.getControlBlockPtrWithoutChecking(
              FL(), functionState, builder, innerLE, weakRefM);
      // We check that the generation is <= to what's in the actual object.
      auto actualGen =
          getGenerationFromControlBlockPtr(
              globalState, builder, &kindStructs, weakRefM->kind, controlBlockPtrLE);
      auto targetGen = getTargetGenFromWeakRef(globalState, builder, &kindStructs, weakRefM->kind, weakFatPtrLE);
      buildCheckGen(globalState, functionState, builder, expectLive, targetGen, actualGen);

      if (auto interfaceKindM = dynamic_cast<InterfaceKind *>(weakRefM->kind)) {
        auto interfaceFatPtrLE = kindStructs.makeInterfaceFatPtrWithoutChecking(FL(),
            functionState, builder, weakRefM, innerLE);
        auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
        buildAssertCensusContains(FL(), globalState, functionState, builder, itablePtrLE);
      }

      return refLE;

//      if (refM->ownership == Ownership::MUTABLE_BORROW || refM->ownership == Ownership::IMMUTABLE_BORROW) {
//        regularCheckValidReference(checkerAFL, globalState, functionState, builder,
//            &kindStructs, refM, refLE);
//      } else if (refM->ownership == Ownership::WEAK) {
//        assert(false);
////      wrcWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
//      } else
//        assert(false);
    }
  } else {
    Reference *actualRefM = nullptr;
    LLVMValueRef refLE = nullptr;
    std::tie(actualRefM, refLE) = megaGetRefInnardsForChecking(ref);
    return refLE;
  }
}

// TODO maybe combine with alias/acquireReference?
// After we load from a local, member, or element, we can feed the result through this
// function to turn it into a desired ownership.
// Example:
// - Can load from an owning ref member to get a constraint ref.
// - Can load from a constraint ref member to get a weak ref.
Ref Safe::upgradeLoadResultToRefWithTargetOwnership(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* sourceType,
    Reference* targetType,
    LoadResult sourceLoadResult,
    bool resultKnownLive) {
  auto sourceRef = sourceLoadResult.extractForAliasingInternals();
  auto sourceOwnership = sourceType->ownership;
  auto sourceLocation = sourceType->location;
  auto targetOwnership = targetType->ownership;
  auto targetLocation = targetType->location;
//  assert(sourceLocation == targetLocation); // unimplemented

  if (sourceOwnership == Ownership::MUTABLE_SHARE) {
    if (sourceLocation == Location::INLINE) {
      return sourceRef;
    } else {
      if (targetOwnership == Ownership::MUTABLE_SHARE) {
        return sourceRef;
      } else if (targetOwnership == Ownership::IMMUTABLE_SHARE) {
        return wrap(
            globalState, targetType,
            immutabilify(
                FL(), functionState, builder, regionInstanceRef, sourceType, sourceRef, targetType));
      } else {
        assert(false);
      }
    }
  } else if (sourceOwnership == Ownership::IMMUTABLE_SHARE) {
    if (sourceLocation == Location::INLINE) {
      assert(false); // curious
      return sourceRef;
    } else {
      if (targetOwnership == Ownership::MUTABLE_SHARE) {
        return sourceRef;
      } else if (targetOwnership == Ownership::IMMUTABLE_SHARE) {
        return mutabilify(
            FL(), functionState, builder, regionInstanceRef, sourceType, sourceRef, targetType);
      } else {
        assert(false);
      }
    }
  } else if (sourceOwnership == Ownership::OWN) {
    if (targetOwnership == Ownership::OWN) {
      // We can never "load" an owning ref from any of these:
      // - We can only get owning refs from locals by unstackifying
      // - We can only get owning refs from structs by destroying
      // - We can only get owning refs from elements by destroying
      // However, we CAN load owning refs by:
      // - Swapping from a local
      // - Swapping from an element
      // - Swapping from a member
      return sourceRef;
    } else if (targetOwnership == Ownership::IMMUTABLE_BORROW) {
      if (globalState->opt->elideChecksForRegions) {
        // An immutable reference is just a raw pointer (and may have an offset when we support
        // inlines). We can translate an owning reference to an immutable borrow easily.
        return transmutePtr(globalState, functionState, builder, false, sourceType, targetType, sourceRef);
      } else {
        // Package it up into a fat pointer
        return assembleWeakRef(globalState, functionState, builder, &kindStructs, sourceType, targetType, sourceRef);
      }
    } else if (
        targetOwnership == Ownership::MUTABLE_BORROW ||
        targetOwnership == Ownership::WEAK) {
      // Now we need to package it up into a fat pointer.
      return assembleWeakRef(globalState, functionState, builder, &kindStructs, sourceType, targetType, sourceRef);
    } else {
      assert(false);
    }
  } else if (sourceOwnership == Ownership::IMMUTABLE_BORROW) {
    if (globalState->opt->elideChecksForRegions) {
      assert(targetOwnership == Ownership::IMMUTABLE_BORROW);
      return sourceRef;
    } else {
      return transmutePtr(globalState, functionState, builder, false, sourceType, targetType, sourceRef);
    }
  } else if (
      sourceOwnership == Ownership::MUTABLE_BORROW ||
      sourceOwnership == Ownership::WEAK) {
    if (targetOwnership == Ownership::IMMUTABLE_BORROW) {
      if (globalState->opt->elideChecksForRegions) {
        auto prechecked =
            preCheckFatPtr(
                FL(), globalState, functionState, builder, &kindStructs, sourceType, sourceRef,
                resultKnownLive);
        return wrap(globalState, targetType, prechecked);
      } else {
        return transmutePtr(globalState, functionState, builder, false, sourceType, targetType, sourceRef);
      }
    } else {
      assert(
          targetOwnership == Ownership::MUTABLE_BORROW ||
          targetOwnership == Ownership::WEAK);
      return transmutePtr(globalState, functionState, builder, false, sourceType, targetType, sourceRef);
    }
  } else {
    assert(false);
  }
  assert(false);
}

void Safe::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  assert(false);
//  return wrcWeaks.aliasWeakRef(from, functionState, builder, weakRefMT, weakRef);
}

void Safe::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  assert(false);
//  return wrcWeaks.discardWeakRef(from, functionState, builder, weakRefMT, weakRef);
}

LLVMValueRef Safe::getCensusObjectId(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref ref) {
  auto controlBlockPtrLE =
      kindStructs.getControlBlockPtr(checkerAFL, functionState, builder, ref, refM);
  return kindStructs.getObjIdFromControlBlockPtr(builder, refM->kind, controlBlockPtrLE);
}

Ref Safe::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef,
    bool knownLive) {
  assert(false);
//  return wrcWeaks.getIsAliveFromWeakRef(functionState, builder, weakRefM, weakRef);
}

LLVMValueRef Safe::fillControlBlockGeneration(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockLE,
    Kind* kindM) {
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);

  // The generation was already incremented when we freed it (or malloc'd it for the first time),
  // but it's very likely that someone else overwrote it with something else, such as a zero. We
  // don't want to use that, we want to use a new gen.
  auto nextGenLocalPtrLE = functionState->nextGenPtrLE.value();
  auto newGenLE = adjustCounterReturnOld(builder, genLT, nextGenLocalPtrLE, 1);

  int genMemberIndex =
      kindStructs.getControlBlock(kindM)->getMemberIndex(ControlBlockMember::GENERATION);
  auto newControlBlockLE =
      LLVMBuildInsertValue(builder, controlBlockLE, newGenLE, genMemberIndex, "newControlBlock");

  return newControlBlockLE;
}

// Returns object ID
void Safe::fillControlBlock(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName) {

  LLVMValueRef controlBlockLE = LLVMGetUndef(kindStructs.getControlBlock(kindM)->getStruct());

  controlBlockLE =
      fillControlBlockCensusFields(
          from, globalState, functionState, &kindStructs, builder, kindM, controlBlockLE, typeName);

  controlBlockLE =
    fillControlBlockGeneration(functionState, builder, controlBlockLE, kindM);

  LLVMBuildStore(builder, controlBlockLE, controlBlockPtrLE.refLE);
}

LoadResult Safe::loadElementFromSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    LiveRef arrayRef,
    InBoundsLE indexInBoundsLE) {
  auto ssaDef = globalState->program->getStaticSizedArray(ssaMT);
  return regularloadElementFromSSA(
      globalState, functionState, builder, ssaRefMT, ssaDef->elementType, arrayRef, indexInBoundsLE, &kindStructs);
}

LoadResult Safe::loadElementFromRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    LiveRef arrayRef,
    InBoundsLE indexInBoundsLE) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  auto wrapperPtrLE = getWrapperPtrLive(FL(), functionState, builder, rsaRefMT, arrayRef);
  auto arrayElementsPtrLE = getRuntimeSizedArrayContentsPtr(builder, true, wrapperPtrLE);
  return loadElement(
      globalState, functionState, builder, arrayElementsPtrLE, rsaDef->elementType, indexInBoundsLE);
}

Ref Safe::storeElementInRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    LiveRef arrayRef,
    InBoundsLE indexInBoundsLE,
    Ref elementRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  auto arrayWrapperPtrLE = getWrapperPtrLive(FL(), functionState, builder, rsaRefMT, arrayRef);
  auto arrayElementsPtrLE = getRuntimeSizedArrayContentsPtr(builder, true, arrayWrapperPtrLE);
  return ::swapElement(
      globalState, functionState, builder, rsaRefMT->location, rsaDef->elementType, arrayElementsPtrLE, indexInBoundsLE, elementRef);
}

Ref Safe::upcast(
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructMT,
    StructKind* sourceStructKindM,
    Ref sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceKind* targetInterfaceKindM) {

  switch (sourceStructMT->ownership) {
    case Ownership::MUTABLE_SHARE:
    case Ownership::IMMUTABLE_SHARE:
    case Ownership::OWN:
    case Ownership::MUTABLE_BORROW:
    case Ownership::IMMUTABLE_BORROW: {
      return upcastStrong(globalState, functionState, builder, &kindStructs, sourceStructMT, sourceStructKindM, sourceRefLE, targetInterfaceTypeM, targetInterfaceKindM);
    }
    case Ownership::WEAK: {
      return ::upcastWeak(globalState, functionState, builder, &kindStructs, sourceStructMT, sourceStructKindM, sourceRefLE, targetInterfaceTypeM, targetInterfaceKindM);
    }
    default:
      assert(false);
  }
}


void Safe::deallocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    LiveRef liveRef) {
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);
  // The generation was already set when we allocated it, but we need to change it now so that
  // nobody can access this object after we free it now.
  auto ref = wrap(globalState, refMT, liveRef);
  auto controlBlockPtrLE =
      kindStructs.getControlBlockPtr(from, functionState, builder, ref, refMT);
  auto genPtrLE =
    getGenerationPtrFromControlBlockPtr(
        globalState, builder, &kindStructs, refMT->kind, controlBlockPtrLE);
  LLVMBuildStore(builder, LLVMConstInt(genLT, 0, false), genPtrLE);

  innerDeallocate(from, globalState, functionState, &kindStructs, builder, refMT, liveRef);
}

LiveRef Safe::constructRuntimeSizedArray(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaMT,
    RuntimeSizedArrayT* runtimeSizedArrayT,
    Ref capacityRef,
    const std::string& typeName) {
  auto rsaWrapperPtrLT =
      kindStructs.getRuntimeSizedArrayWrapperStruct(runtimeSizedArrayT);
  auto rsaDef = globalState->program->getRuntimeSizedArray(runtimeSizedArrayT);
  auto elementType = globalState->program->getRuntimeSizedArray(runtimeSizedArrayT)->elementType;
  auto rsaElementLT = globalState->getRegion(elementType)->translateType(elementType);
  auto resultRef =
      ::constructRuntimeSizedArray(
          globalState, functionState, builder, &kindStructs, rsaMT, rsaDef->elementType, runtimeSizedArrayT,
          rsaWrapperPtrLT, rsaElementLT, globalState->constI32(0), capacityRef, true, typeName,
          [this, functionState, runtimeSizedArrayT, rsaMT, typeName](
              LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(),
                functionState,
                innerBuilder,
                runtimeSizedArrayT,
                controlBlockPtrLE,
                typeName);
          });
  return resultRef;
}

Ref Safe::loadMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* structRefMT,
    LiveRef structLiveRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {
  auto structMT = dynamic_cast<StructKind*>(structRefMT->kind);
  assert(structMT);
  auto innerStructLT = kindStructs.getStructInnerStruct(structMT);

  if (structRefMT->location == Location::INLINE) {
    auto structRef = wrap(globalState, structRefMT, structLiveRef);
    auto structRefLE =
        globalState->getRegion(structRefMT)
            ->checkValidReference(FL(), functionState, builder, true, structRefMT, structRef);
    LoadResult unupgradedMemberLE = LoadResult{
        wrap(globalState->getRegion(expectedMemberType), expectedMemberType,
            LLVMBuildExtractValue(
                builder, structRefLE, memberIndex, memberName.c_str()))};
    return upgradeLoadResultToRefWithTargetOwnership(
        functionState, builder, regionInstanceRef, expectedMemberType, targetType, unupgradedMemberLE, false);
  } else {
    WrapperPtrLE structWPtrLE =
        getWrapperPtrLive(FL(), functionState, builder, structRefMT, structLiveRef);
    auto innerStructPtrLE =
        kindStructs.getStructContentsPtr(builder, structRefMT->kind, structWPtrLE);
    auto memberLoadedLE =
        loadInnerInnerStructMember(
            globalState,
            functionState,
            builder,
            innerStructLT,
            innerStructPtrLE,
            memberIndex,
            expectedMemberType,
            memberName);
    return upgradeLoadResultToRefWithTargetOwnership(
        functionState, builder, regionInstanceRef, expectedMemberType, targetType, memberLoadedLE, false);
  }
}

void Safe::checkInlineStructType(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  auto argLE = checkValidReference(FL(), functionState, builder, false, refMT, ref);
  auto structKind = dynamic_cast<StructKind*>(refMT->kind);
  assert(structKind);
  assert(LLVMTypeOf(argLE) == kindStructs.getStructInnerStruct(structKind));
}


std::string Safe::generateRuntimeSizedArrayDefsC(
    Package* currentPackage,
    RuntimeSizedArrayDefinitionT* rsaDefM) {
  assert(rsaDefM->mutability == Mutability::MUTABLE);
  return generateUniversalRefStructDefC(currentPackage, currentPackage->getKindExportName(rsaDefM->kind, true));
}

std::string Safe::generateStaticSizedArrayDefsC(
    Package* currentPackage,
    StaticSizedArrayDefinitionT* ssaDefM) {
  assert(ssaDefM->mutability == Mutability::MUTABLE);
  return generateUniversalRefStructDefC(currentPackage, currentPackage->getKindExportName(ssaDefM->kind, true));
}

std::string Safe::generateStructDefsC(
    Package* currentPackage, StructDefinition* structDefM) {
  assert(structDefM->mutability == Mutability::MUTABLE);
  return generateUniversalRefStructDefC(currentPackage, currentPackage->getKindExportName(structDefM->kind, true));
}

std::string Safe::generateInterfaceDefsC(
    Package* currentPackage, InterfaceDefinition* interfaceDefM) {
  assert(interfaceDefM->mutability == Mutability::MUTABLE);
  return generateUniversalRefStructDefC(currentPackage, currentPackage->getKindExportName(interfaceDefM->kind, true));
}


LLVMTypeRef Safe::getExternalType(Reference* refMT) {
  if (dynamic_cast<StructKind*>(refMT->kind) ||
      dynamic_cast<StaticSizedArrayT*>(refMT->kind) ||
      dynamic_cast<RuntimeSizedArrayT*>(refMT->kind)) {
    return globalState->universalRefCompressedStructLT;
  } else if (dynamic_cast<InterfaceKind*>(refMT->kind)) {
    return globalState->universalRefCompressedStructLT;
  } else {
    assert(false);
  }
}

Ref Safe::receiveAndDecryptFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    LLVMValueRef sourceRefLE) {
  assert(sourceRefMT->ownership != Ownership::IMMUTABLE_SHARE);
  return regularReceiveAndDecryptFamiliarReference(
      globalState, functionState, builder, &kindStructs, sourceRefMT, sourceRefLE);
}

LLVMTypeRef Safe::getInterfaceMethodVirtualParamAnyType(Reference* reference) {
  switch (reference->ownership) {
    case Ownership::MUTABLE_BORROW:
    case Ownership::IMMUTABLE_BORROW:
    case Ownership::OWN:
    case Ownership::IMMUTABLE_SHARE:
    case Ownership::MUTABLE_SHARE:
      return LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
    case Ownership::WEAK:
      return kindStructs.getWeakVoidRefStruct(reference->kind);
    default:
      assert(false);
  }
}

std::pair<Ref, Ref> Safe::receiveUnencryptedAlienReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sourceRegionInstanceRef,
    Ref targetRegionInstanceRef,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef) {
  assert(false);
  exit(1);
}

LLVMValueRef Safe::encryptAndSendFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Ref sourceRef) {
  assert(sourceRefMT->ownership != Ownership::MUTABLE_SHARE);
  assert(sourceRefMT->ownership != Ownership::IMMUTABLE_SHARE);
  return regularEncryptAndSendFamiliarReference(
      globalState, functionState, builder, &kindStructs, sourceRefMT, sourceRef);
}

void Safe::pushRuntimeSizedArrayNoBoundsCheck(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference *rsaRefMT,
    RuntimeSizedArrayT *rsaMT,
    LiveRef rsaRef,
    InBoundsLE indexInBoundsLE,
    Ref elementRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  auto arrayWrapperPtrLE = getWrapperPtrLive(FL(), functionState, builder, rsaRefMT, rsaRef);
  auto incrementedSize =
      incrementRSASize(
          globalState, functionState, builder, rsaRefMT, arrayWrapperPtrLE);
  ::initializeElementInRSAWithoutIncrementSize(
      globalState,
      functionState,
      builder,
      true,
      rsaDef->kind,
      rsaRefMT,
      arrayWrapperPtrLE,
      indexInBoundsLE,
      elementRef,
      incrementedSize);
}

Ref Safe::popRuntimeSizedArrayNoBoundsCheck(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref arrayRegionInstanceRef,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    LiveRef arrayRef,
    InBoundsLE indexInBoundsLE) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
//  auto elementLE =
//      regularLoadElementFromRSAWithoutUpgrade(
//          globalState,
//          functionState,
//          builder,
//          &kindStructs,
//          true,
//          rsaRefMT,
//          rsaDef->elementType,
//          arrayRef,
//          indexInBoundsLE)
//          .move();
  auto arrayWPtrLE = getWrapperPtrLive(FL(), functionState, builder, rsaRefMT, arrayRef);
  auto arrayElementsPtrLE = getRuntimeSizedArrayContentsPtr(builder, true, arrayWPtrLE);
  auto elementLE =
      ::loadElement(
          globalState, functionState, builder, arrayElementsPtrLE, rsaDef->elementType, indexInBoundsLE)
          .move();
  decrementRSASize(globalState, functionState, &kindStructs, builder, rsaRefMT, arrayWPtrLE);
  return elementLE;
}

void Safe::initializeElementInSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    LiveRef arrayRef,
    InBoundsLE indexInBoundsLE,
    Ref elementRef) {
  auto ssaDef = globalState->program->getStaticSizedArray(ssaMT);
  auto arrayWrapperPtrLE = getWrapperPtrLive(FL(), functionState, builder, ssaRefMT, arrayRef);
  auto arrayElementsPtrLE = getStaticSizedArrayContentsPtr(builder, arrayWrapperPtrLE);
  ::initializeElementWithoutIncrementSize(
      globalState, functionState, builder, ssaRefMT->location, ssaDef->elementType, arrayElementsPtrLE,
      indexInBoundsLE, elementRef,
      // Manually making an IncrementedSize because it's an SSA.
      IncrementedSize{});
}

Ref Safe::deinitializeElementFromSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    LiveRef arrayRef,
    InBoundsLE indexInBoundsLE) {
  assert(false);
  exit(1);
}

Weakability Safe::getKindWeakability(Kind* kind) {
  if (auto structKind = dynamic_cast<StructKind*>(kind)) {
    return globalState->lookupStruct(structKind)->weakability;
  } else if (auto interfaceKind = dynamic_cast<InterfaceKind*>(kind)) {
    return globalState->lookupInterface(interfaceKind)->weakability;
  } else {
    return Weakability::NON_WEAKABLE;
  }
}

ValeFuncPtrLE Safe::getInterfaceMethodFunctionPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    int indexInEdge) {
  return getInterfaceMethodFunctionPtrFromItable(
      globalState, functionState, builder, &kindStructs, virtualParamMT, virtualArgRef, indexInEdge);
}

LLVMValueRef Safe::stackify(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    Ref refToStore,
    bool knownLive) {
  auto toStoreLE = checkValidReference(FL(), functionState, builder, false, local->type, refToStore);
  auto typeLT = translateType(local->type);
  return makeBackendLocal(functionState, builder, typeLT, local->id->maybeName.c_str(), toStoreLE);
}

Ref Safe::unstackify(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return loadLocal(functionState, builder, local, localAddr);
}

Ref Safe::loadLocal(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return normalLocalLoad(globalState, functionState, builder, local, localAddr);
}

Ref Safe::localStore(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore, bool knownLive) {
  return normalLocalStore(globalState, functionState, builder, local, localAddr, refToStore);
}

std::string Safe::getExportName(
    Package* package,
    Reference* reference,
    bool includeProjectName) {
  return package->getKindExportName(reference->kind, includeProjectName) + (reference->location == Location::YONDER ? "Ref" : "");
}

Ref Safe::createRegionInstanceLocal(FunctionState* functionState, LLVMBuilderRef builder) {
  auto regionLT = kindStructs.getStructInnerStruct(regionKind);
  auto regionInstancePtrLE =
      makeBackendLocal(functionState, builder, regionLT, "region", LLVMGetUndef(regionLT));
  auto regionInstanceRef = wrap(this, regionRefMT, regionInstancePtrLE);
  return regionInstanceRef;
}

LiveRef Safe::checkRefLive(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refMT,
    Ref ref,
    bool refKnownLive) {
  switch (refMT->ownership) {
    case Ownership::IMMUTABLE_SHARE:
    case Ownership::MUTABLE_SHARE:
      assert(false); // curious
    case Ownership::IMMUTABLE_BORROW:
      if (globalState->opt->elideChecksForRegions) {
        // Immutable borrows aren't really live, but we can dereference them as if they are. If they
        // don't point to a live object, they'll point at a protected address instead, and
        // dereferencing will safely fault.
        auto refLE = checkValidReference(FL(), functionState, builder, true, refMT, ref);
        return wrapToLiveRef(FL(), functionState, builder, regionInstanceRef, refMT, refLE);
      } else {
        auto wrapperPtrLE =
            lockGenFatPtr(
                globalState, FL(), functionState, &kindStructs, builder, regionInstanceRef, refMT, ref, refKnownLive);
        return wrapToLiveRef(FL(), functionState, builder, regionInstanceRef, refMT, wrapperPtrLE.refLE);
      }
    case Ownership::OWN: {
      auto refLE = checkValidReference(FL(), functionState, builder, true, refMT, ref);
      return wrapToLiveRef(FL(), functionState, builder, regionInstanceRef, refMT, refLE);
    }
    case Ownership::MUTABLE_BORROW: {
      auto wrapperPtrLE =
          lockGenFatPtr(
              globalState, FL(), functionState, &kindStructs, builder, regionInstanceRef, refMT, ref, refKnownLive);
      return wrapToLiveRef(FL(), functionState, builder, regionInstanceRef, refMT, wrapperPtrLE.refLE);
    }
    case Ownership::WEAK: {
      assert(false);
      break;
    }
    default:
      assert(false);
      break;
  }
}

LiveRef Safe::wrapToLiveRef(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refMT,
    LLVMValueRef ref) {
  kindStructs.makeWrapperPtr(FL(), functionState, builder, refMT, ref); // To trigger its asserts
  return LiveRef(refMT, ref);
}

LiveRef Safe::preCheckBorrow(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refMT,
    Ref ref,
    bool refKnownLive) {
    switch (refMT->ownership) {
        case Ownership::IMMUTABLE_SHARE:
        case Ownership::MUTABLE_SHARE:
        case Ownership::OWN: {
            assert(false); // curious
            break;
        }
        case Ownership::IMMUTABLE_BORROW: {
          assert(false); // curious
          if (globalState->opt->elideChecksForRegions) {
            assert(false); // curious
          } else {
            return preCheckFatPtr(FL(), globalState, functionState, builder, &kindStructs, refMT, ref, refKnownLive);
          }
          break;
        }
        case Ownership::MUTABLE_BORROW: {
            return preCheckFatPtr(FL(), globalState, functionState, builder, &kindStructs, refMT, ref, refKnownLive);
        }
        case Ownership::WEAK: {
            assert(false);
            break;
        }
        default:
            assert(false);
            break;
    }
    assert(false);
}

// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
WrapperPtrLE Safe::getWrapperPtrLive(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refM,
    LiveRef liveRef) {
  switch (refM->ownership) {
    case Ownership::IMMUTABLE_SHARE:
    case Ownership::MUTABLE_SHARE:
      assert(false); // curious
    case Ownership::OWN: {
      return toWrapperPtr(functionState, builder, &kindStructs, refM, liveRef);
    }
    case Ownership::MUTABLE_BORROW:
    case Ownership::IMMUTABLE_BORROW:
    case Ownership::WEAK: {
      return getWrapperPtr(globalState, &kindStructs, from, functionState, builder, refM, liveRef);
    }
    default:
      assert(false);
      break;
  }
}

Ref Safe::mutabilify(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refMT,
    Ref ref,
    Reference* targetRefMT) {
  assert(refMT->ownership == Ownership::MUTABLE_BORROW);
  assert(false); // impl
}

LiveRef Safe::immutabilify(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refMT,
    Ref sourceRef,
    Reference* targetRefMT) {
  assert(refMT->ownership == Ownership::MUTABLE_BORROW);
  if (globalState->opt->elideChecksForRegions) {
    auto liveRef =
        preCheckBorrow(
            checkerAFL, functionState, builder, regionInstanceRef, refMT, sourceRef, false);

    auto wrapperPtrLE =
        getWrapperPtrLive(checkerAFL, functionState, builder, refMT, liveRef);
    auto transmuted =
        kindStructs.makeWrapperPtr(
            checkerAFL, functionState, builder, targetRefMT, wrapperPtrLE.refLE);
    return toLiveRef(transmuted);
  } else {
    auto transmutedRef =
        transmutePtr(globalState, functionState, builder, false, refMT, targetRefMT, sourceRef);
    auto transmutedRefLE =
        checkValidReference(FL(), functionState, builder, false, refMT, transmutedRef);
    return wrapToLiveRef(
        checkerAFL, functionState, builder, regionInstanceRef, targetRefMT, transmutedRefLE);
  }
}