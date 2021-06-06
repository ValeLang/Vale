#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <region/common/controlblock.h>
#include <utils/counters.h>
#include <utils/branch.h>
#include <region/common/common.h>
#include "lgtweaks.h"

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;
constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI = 1;

LLVMValueRef LgtWeaks::getTargetGenFromWeakRef(
    LLVMBuilderRef builder,
    IWeakRefStructsSource* weakRefStructsSource,
    Kind* kind,
    WeakFatPtrLE weakRefLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
         globalState->opt->regionOverride == RegionOverride::RESILIENT_V4);
  auto headerLE = fatWeaks_.getHeaderFromWeakRef(builder, weakRefLE);
  assert(LLVMTypeOf(headerLE) == weakRefStructsSource->getWeakRefHeaderStruct(kind));
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "actualGeni");
}

LLVMValueRef LgtWeaks::getLgtiFromWeakRef(
    LLVMBuilderRef builder,
    WeakFatPtrLE weakRefLE) {
//  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);
  auto headerLE = fatWeaks_.getHeaderFromWeakRef(builder, weakRefLE);
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI, "lgti");
}

void LgtWeaks::buildCheckLgti(
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::FAST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
      // These dont have LGT
      assert(false);
      break;
    default:
      assert(false);
      break;
  }
  std::vector<LLVMValueRef> args = { lgtTablePtrLE, lgtiLE };
  LLVMBuildCall(builder, globalState->checkLgti, args.data(), args.size(), "");
}

static LLVMValueRef makeLgtiHeader(
    GlobalState* globalState,
    IWeakRefStructsSource* weakRefStructsSource,
    LLVMBuilderRef builder,
    Kind* kind,
    LLVMValueRef lgtiLE,
    LLVMValueRef targetGenLE) {
//  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);
  auto headerLE = LLVMGetUndef(weakRefStructsSource->getWeakRefHeaderStruct(kind));
  headerLE = LLVMBuildInsertValue(builder, headerLE, lgtiLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI, "headerWithLgti");
  headerLE = LLVMBuildInsertValue(builder, headerLE, targetGenLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "header");
  return headerLE;
}

LLVMValueRef LgtWeaks::getLGTEntryGenPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE) {
  auto genEntriesPtrLE =
      LLVMBuildLoad(builder, getLgtEntriesArrayPtr(builder), "lgtEntriesArrayPtr");
  auto ptrToLGTEntryLE =
      LLVMBuildGEP(builder, genEntriesPtrLE, &lgtiLE, 1, "ptrToLGTEntry");
  auto ptrToLGTEntryGenLE =
      LLVMBuildStructGEP(builder, ptrToLGTEntryLE, LGT_ENTRY_MEMBER_INDEX_FOR_GEN, "ptrToLGTEntryGen");
  return ptrToLGTEntryGenLE;
}

LLVMValueRef LgtWeaks::getLGTEntryNextFreePtr(
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE) {
  auto genEntriesPtrLE =
      LLVMBuildLoad(builder, getLgtEntriesArrayPtr(builder), "genEntriesArrayPtr");
  auto ptrToLGTEntryLE =
      LLVMBuildGEP(builder, genEntriesPtrLE, &lgtiLE, 1, "ptrToLGTEntry");
  auto ptrToLGTEntryGenLE =
      LLVMBuildStructGEP(builder, ptrToLGTEntryLE, LGT_ENTRY_MEMBER_INDEX_FOR_NEXT_FREE, "ptrToLGTEntryNextFree");
  return ptrToLGTEntryGenLE;
}

LLVMValueRef LgtWeaks::getActualGenFromLGT(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE) {
  return LLVMBuildLoad(builder, getLGTEntryGenPtr(functionState, builder, lgtiLE), "lgti");
}

static LLVMValueRef getLgtiFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    IKindStructsSource* structs,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
//  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);

  if (refM->ownership == Ownership::SHARE) {
    // Shares never have weak refs
    assert(false);
    return nullptr;
  } else {
    auto lgtiPtrLE =
        LLVMBuildStructGEP(
            builder,
            controlBlockPtr.refLE,
            structs->getControlBlock(refM->kind)->getMemberIndex(ControlBlockMember::LGTI_32B),
            "lgtiPtr");
    return LLVMBuildLoad(builder, lgtiPtrLE, "lgti");
  }
}

LgtWeaks::LgtWeaks(
    GlobalState* globalState_,
    IKindStructsSource* kindStructsSource_,
    IWeakRefStructsSource* weakRefStructsSource_,
    bool elideChecksForKnownLive_)
  : globalState(globalState_),
    fatWeaks_(globalState_, weakRefStructsSource_),
    kindStructsSource(kindStructsSource_),
    weakRefStructsSource(weakRefStructsSource_),
    elideChecksForKnownLive(elideChecksForKnownLive_) {
//  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto voidPtrLT = LLVMPointerType(int8LT, 0);
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);


  lgtTablePtrLE = LLVMAddGlobal(globalState->mod, globalState->lgtTableStructLT, "__lgt_table");
  LLVMSetLinkage(lgtTablePtrLE, LLVMExternalLinkage);
  std::vector<LLVMValueRef> wrcTableMembers = {
      constI32LE(globalState, 0),
      constI32LE(globalState, 0),
      LLVMConstNull(globalState->lgtEntryStructLT)
  };
  LLVMSetInitializer(
      lgtTablePtrLE,
      LLVMConstNamedStruct(
          globalState->lgtTableStructLT, wrcTableMembers.data(), wrcTableMembers.size()));
}

void LgtWeaks::mainSetup(FunctionState* functionState, LLVMBuilderRef builder) {

}

void LgtWeaks::mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) {
  if (globalState->opt->census) {
    LLVMValueRef args[3] = {
        LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 0, false),
        LLVMBuildZExt(
            builder,
            LLVMBuildCall(
                builder, globalState->getNumLiveLgtEntries, &lgtTablePtrLE, 1, "numLgtEntries"),
            LLVMInt64TypeInContext(globalState->context),
            ""),
        globalState->getOrMakeStringConstant("WRC leaks!"),
    };
    LLVMBuildCall(builder, globalState->externs->assertI64Eq, args, 3, "");
  }
}


WeakFatPtrLE LgtWeaks::weakStructPtrToLgtiWeakInterfacePtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructKind* sourceStructKindM,
    Reference* sourceStructTypeM,
    InterfaceKind* targetInterfaceKindM,
    Reference* targetInterfaceTypeM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::FAST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
    case RegionOverride::RESILIENT_V3: case RegionOverride::RESILIENT_V4:
      assert(false);
      break;
    default:
      assert(false);
      break;
  }

//  checkValidReference(
//      FL(), globalState, functionState, builder, sourceStructTypeM, sourceRefLE);
  auto controlBlockPtr =
      kindStructsSource->getConcreteControlBlockPtr(
          FL(), functionState, builder, sourceStructTypeM,
          kindStructsSource->makeWrapperPtr(
              FL(), functionState, builder, sourceStructTypeM,
              fatWeaks_.getInnerRefFromWeakRef(
                  functionState, builder, sourceStructTypeM, sourceRefLE)));

  auto interfaceRefLT =
      weakRefStructsSource->getInterfaceWeakRefStruct(
          targetInterfaceKindM);
  auto headerLE = fatWeaks_.getHeaderFromWeakRef(builder, sourceRefLE);

  auto innerRefLE =
      makeInterfaceRefStruct(
          globalState, functionState, builder, kindStructsSource, sourceStructKindM, targetInterfaceKindM, controlBlockPtr);

  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetInterfaceTypeM, interfaceRefLT, headerLE, innerRefLE);
}


// Makes a non-weak interface ref into a weak interface ref
WeakFatPtrLE LgtWeaks::assembleInterfaceWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    InterfaceKind* interfaceKindM,
    InterfaceFatPtrLE sourceInterfaceFatPtrLE) {
  assert(sourceType->ownership == Ownership::OWN || sourceType->ownership == Ownership::SHARE);
  // curious, if its a borrow, do we just return sourceRefLE?

  auto controlBlockPtrLE =
      kindStructsSource->getControlBlockPtr(
          FL(), functionState, builder, interfaceKindM, sourceInterfaceFatPtrLE);
  auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, kindStructsSource, sourceType,
      controlBlockPtrLE);
  auto currentGenLE = getActualGenFromLGT(functionState, builder, lgtiLE);
  auto headerLE = makeLgtiHeader(globalState, weakRefStructsSource, builder, interfaceKindM, lgtiLE, currentGenLE);

  auto weakRefStructLT =
      weakRefStructsSource->getInterfaceWeakRefStruct(interfaceKindM);

  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetType, weakRefStructLT, headerLE, sourceInterfaceFatPtrLE.refLE);
}

WeakFatPtrLE LgtWeaks::assembleStructWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    Reference* targetTypeM,
    StructKind* structKindM,
    WrapperPtrLE objPtrLE) {
  assert(structTypeM->ownership == Ownership::OWN || structTypeM->ownership == Ownership::SHARE);
  // curious, if its a borrow, do we just return sourceRefLE?

  auto controlBlockPtrLE =
      kindStructsSource->getConcreteControlBlockPtr(
          FL(), functionState, builder, structTypeM, objPtrLE);
  auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, kindStructsSource, structTypeM, controlBlockPtrLE);
  buildFlare(FL(), globalState, functionState, builder, lgtiLE);
  auto currentGenLE = getActualGenFromLGT(functionState, builder, lgtiLE);
  auto headerLE = makeLgtiHeader(globalState, weakRefStructsSource, builder, structKindM, lgtiLE, currentGenLE);
  auto weakRefStructLT =
      weakRefStructsSource->getStructWeakRefStruct(structKindM);
  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

WeakFatPtrLE LgtWeaks::assembleStaticSizedArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceSSAMT,
    StaticSizedArrayT* staticSizedArrayMT,
    Reference* targetSSAWeakRefMT,
    WrapperPtrLE objPtrLE) {
  // impl
  assert(false);
  exit(1);
}

WeakFatPtrLE LgtWeaks::assembleRuntimeSizedArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    RuntimeSizedArrayT* runtimeSizedArrayMT,
    Reference* targetRSAWeakRefMT,
    WrapperPtrLE sourceRefLE) {
  auto controlBlockPtrLE =
      kindStructsSource->getConcreteControlBlockPtr(
          FL(), functionState, builder, sourceType, sourceRefLE);
  auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, kindStructsSource, sourceType, controlBlockPtrLE);
  auto targetGenLE = getActualGenFromLGT(functionState, builder, lgtiLE);
  auto headerLE = makeLgtiHeader(globalState, weakRefStructsSource, builder, runtimeSizedArrayMT, lgtiLE, targetGenLE);

  auto weakRefStructLT =
      weakRefStructsSource->getRuntimeSizedArrayWeakRefStruct(runtimeSizedArrayMT);
  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetRSAWeakRefMT, weakRefStructLT, headerLE, sourceRefLE.refLE);
}

LLVMValueRef LgtWeaks::lockLgtiFatPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    WeakFatPtrLE weakRefLE,
    bool knownLive) {
  auto fatPtrLE = weakRefLE;
  if (elideChecksForKnownLive && knownLive) {
    // Do nothing
  } else {
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, refM, fatPtrLE, knownLive);
    buildIf(
        globalState, functionState, builder, isZeroLE(builder, isAliveLE),
        [this, functionState, fatPtrLE](LLVMBuilderRef thenBuilder) {
          //        buildPrintAreaAndFileAndLine(globalState, thenBuilder, from);
          //        buildPrint(globalState, thenBuilder, "Tried dereferencing dangling reference! ");
          //        {
          //          auto lgtiLE = getLgtiFromWeakRef(thenBuilder, fatPtrLE);
          //          buildPrint(globalState, thenBuilder, "lgti ");
          //          buildPrint(globalState, thenBuilder, lgtiLE);
          //          buildPrint(globalState, thenBuilder, " ");
          //          auto targetGenLE = getTargetGenFromWeakRef(thenBuilder, fatPtrLE);
          //          buildPrint(globalState, thenBuilder, "targetGen ");
          //          buildPrint(globalState, thenBuilder, targetGenLE);
          //          buildPrint(globalState, thenBuilder, " ");
          //          auto actualGenLE = getActualGenFromLGT(functionState, thenBuilder,
          //              lgtiLE);
          //          buildPrint(globalState, thenBuilder, "actualGen ");
          //          buildPrint(globalState, thenBuilder, actualGenLE);
          //          buildPrint(globalState, thenBuilder, " ");
          //        }
          //        buildPrint(globalState, thenBuilder, "Exiting!\n");
          //        auto exitCodeIntLE = LLVMConstInt(LLVMInt8TypeInContext(globalState->context), 255, false);
          //        LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");

          auto ptrToWriteToLE = LLVMBuildLoad(thenBuilder, globalState->crashGlobal,
              "crashGlobal");// LLVMConstNull(LLVMPointerType(LLVMInt64TypeInContext(globalState->context), 0));
          LLVMBuildStore(thenBuilder, constI64LE(globalState, 0), ptrToWriteToLE);
        });
  }
  return fatWeaks_.getInnerRefFromWeakRef(functionState, builder, refM, fatPtrLE);
}

LLVMValueRef LgtWeaks::getNewLgti(
    FunctionState* functionState,
    LLVMBuilderRef builder) {
//  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);

  // uint64_t resultLgti = __lgt_firstFree;
  auto resultLgtiLE = LLVMBuildLoad(builder, getLgtFirstFreeLgtiPtr(builder), "resultLgti");

  // if (resultLgti == __lgt_capacity) {
  //   __expandLgtTable();
  // }
  auto atCapacityLE =
      LLVMBuildICmp(
          builder,
          LLVMIntEQ,
          resultLgtiLE,
          LLVMBuildLoad(builder, getLgtCapacityPtr(builder), "lgtCapacity"),
          "atCapacity");
  buildIf(
      globalState, functionState,
      builder,
      atCapacityLE,
      [this](LLVMBuilderRef thenBuilder) {
        LLVMBuildCall(thenBuilder, globalState->expandLgt, &lgtTablePtrLE, 1, "");
      });

  // __LGT_Entry* lgtEntryPtr = &__lgt_entries[resultLgti];
  auto lgtNextFreePtrLE = getLGTEntryNextFreePtr(builder, resultLgtiLE);

  // __lgt_firstFree = lgtEntryPtr->nextFree;
  LLVMBuildStore(
      builder,
      // lgtEntryPtr->nextFree
      LLVMBuildLoad(builder, lgtNextFreePtrLE, ""),
      // __lgt_firstFree
      getLgtFirstFreeLgtiPtr(builder));

  return resultLgtiLE;
}

void LgtWeaks::innerNoteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* concreteRefM,
    ControlBlockPtrLE controlBlockPtrLE) {
  auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, kindStructsSource, concreteRefM,
      controlBlockPtrLE);
  auto ptrToActualGenLE = getLGTEntryGenPtr(functionState, builder, lgtiLE);
  adjustCounter(globalState, builder, globalState->metalCache->i64, ptrToActualGenLE, 1);
  auto ptrToLgtEntryNextFreeLE = getLGTEntryNextFreePtr(builder, lgtiLE);

  // __lgt_entries[lgti] = __lgt_firstFree;
  LLVMBuildStore(
      builder,
      LLVMBuildLoad(
          builder, getLgtFirstFreeLgtiPtr(builder), "firstFreeLgti"),
      ptrToLgtEntryNextFreeLE);
  // __lgt_firstFree = lgti;
  LLVMBuildStore(builder, lgtiLE, getLgtFirstFreeLgtiPtr(builder));
}


void LgtWeaks::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  // Do nothing!
}

void LgtWeaks::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  // Do nothing!
}


LLVMValueRef LgtWeaks::getIsAliveFromWeakFatPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    WeakFatPtrLE weakFatPtrLE,
    bool knownLive) {
  if (knownLive && elideChecksForKnownLive) {
    // Do nothing, just return a constant true
    return LLVMConstInt(LLVMInt1TypeInContext(globalState->context), 1, false);
  } else {
    // Get target generation from the ref
    auto targetGenLE = getTargetGenFromWeakRef(builder, weakRefStructsSource, weakRefM->kind, weakFatPtrLE);

    // Get actual generation from the table
    auto lgtiLE = getLgtiFromWeakRef(builder, weakFatPtrLE);
    if (globalState->opt->census) {
      buildCheckLgti(builder, lgtiLE);
    }
    auto ptrToActualGenLE = getLGTEntryGenPtr(functionState, builder, lgtiLE);
    auto actualGenLE = LLVMBuildLoad(builder, ptrToActualGenLE, "gen");

    return LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        actualGenLE,
        targetGenLE,
        "genLive");
  }
}

Ref LgtWeaks::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef,
    bool knownLive) {
  assert(
      weakRefM->ownership == Ownership::BORROW ||
          weakRefM->ownership == Ownership::WEAK);

  if (knownLive && elideChecksForKnownLive) {
    // Do nothing, just return a constant true
    auto isAliveLE = LLVMConstInt(LLVMInt1TypeInContext(globalState->context), 1, false);
    return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isAliveLE);
  } else {
    auto weakFatPtrLE =
        weakRefStructsSource->makeWeakFatPtr(
            weakRefM,
            globalState->getRegion(weakRefM)
                ->checkValidReference(FL(), functionState, builder, weakRefM, weakRef));
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, weakRefM, weakFatPtrLE, knownLive);
    return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isAliveLE);
  }
}

LLVMValueRef LgtWeaks::fillWeakableControlBlock(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IKindStructsSource* structs,
    Kind* kindM,
    LLVMValueRef controlBlockLE) {
  auto geniLE = getNewLgti(functionState, builder);
  return LLVMBuildInsertValue(
      builder,
      controlBlockLE,
      geniLE,
      structs->getControlBlock(kindM)->getMemberIndex(ControlBlockMember::LGTI_32B),
      "controlBlockWithLgti");
}

WeakFatPtrLE LgtWeaks::weakInterfaceRefToWeakStructRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakInterfaceRefMT,
    WeakFatPtrLE weakInterfaceFatPtrLE) {
  auto headerLE = fatWeaks_.getHeaderFromWeakRef(builder, weakInterfaceFatPtrLE);

  // The object might not exist, so skip the check.
  auto interfaceFatPtrLE =
      kindStructsSource->makeInterfaceFatPtrWithoutChecking(
          FL(), functionState, builder,
          weakInterfaceRefMT, // It's still conceptually weak even though its not in a weak pointer.
          fatWeaks_.getInnerRefFromWeakRef(
              functionState,
              builder,
              weakInterfaceRefMT,
              weakInterfaceFatPtrLE));
  auto controlBlockPtrLE =
      kindStructsSource->getControlBlockPtrWithoutChecking(
          FL(), functionState, builder, weakInterfaceRefMT->kind, interfaceFatPtrLE);

  // Now, reassemble a weak void* ref to the struct.
  auto weakVoidStructRefLE =
      fatWeaks_.assembleVoidStructWeakRef(builder, weakInterfaceRefMT, controlBlockPtrLE, headerLE);

  return weakVoidStructRefLE;
}

// USE ONLY FOR ASSERTING A REFERENCE IS VALID
std::tuple<Reference*, LLVMValueRef> lgtGetRefInnardsForChecking(Ref ref) {
  Reference* refM = ref.refM;
  LLVMValueRef refLE = ref.refLE;
  return std::make_tuple(refM, refLE);
}

void LgtWeaks::buildCheckWeakRef(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef) {
  Reference *actualRefM = nullptr;
  LLVMValueRef refLE = nullptr;
  std::tie(actualRefM, refLE) = lgtGetRefInnardsForChecking(weakRef);
  auto weakFatPtrLE = weakRefStructsSource->makeWeakFatPtr(weakRefM, refLE);
  auto innerLE =
      fatWeaks_.getInnerRefFromWeakRefWithoutCheck(
          functionState, builder, weakRefM, weakFatPtrLE);

  auto lgtiLE = getLgtiFromWeakRef(builder, weakFatPtrLE);
  // WARNING: This check has false negatives, it doesnt catch much.
  buildCheckLgti(builder, lgtiLE);
  // We check that the generation is <= to what's in the actual object.
  auto actualGen = getActualGenFromLGT(functionState, builder, lgtiLE);
  auto targetGen = getTargetGenFromWeakRef(builder, weakRefStructsSource, weakRefM->kind, weakFatPtrLE);
  buildCheckGen(globalState, functionState, builder, targetGen, actualGen);

  // This will also run for objects which have since died, which is fine.
  if (auto interfaceKindM = dynamic_cast<InterfaceKind *>(weakRefM->kind)) {
    auto interfaceFatPtrLE =
        kindStructsSource->makeInterfaceFatPtrWithoutChecking(
            checkerAFL, functionState, builder, weakRefM, innerLE);
    auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
  }
}

Ref LgtWeaks::assembleWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    Ref sourceRef) {
  // Now we need to package it up into a weak ref.
  if (auto structKind = dynamic_cast<StructKind*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructsSource->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleStructWeakRef(
            functionState, builder, sourceType, targetType, structKind, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto interfaceKindM = dynamic_cast<InterfaceKind*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceInterfaceFatPtrLE = kindStructsSource->makeInterfaceFatPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleInterfaceWeakRef(
            functionState, builder, sourceType, targetType, interfaceKindM, sourceInterfaceFatPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto staticSizedArray = dynamic_cast<StaticSizedArrayT*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructsSource->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleStaticSizedArrayWeakRef(
            functionState, builder, sourceType, staticSizedArray, targetType, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto runtimeSizedArray = dynamic_cast<RuntimeSizedArrayT*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructsSource->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleRuntimeSizedArrayWeakRef(
            functionState, builder, sourceType, runtimeSizedArray, targetType, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else assert(false);
}


LLVMTypeRef LgtWeaks::makeWeakRefHeaderStruct(GlobalState* globalState, RegionId* regionId) {
//  assert(regionId == globalState->metalCache->resilientV1RegionId);
  auto genRefStructL = LLVMStructCreateNamed(globalState->context, "__GenRef");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));

  LLVMStructSetBody(genRefStructL, memberTypesL.data(), memberTypesL.size(), false);

  assert(
      LLVMABISizeOfType(globalState->dataLayout, genRefStructL) ==
          LLVMABISizeOfType(globalState->dataLayout, LLVMInt64TypeInContext(globalState->context)));

  return genRefStructL;
}

LLVMValueRef LgtWeaks::getLgtCapacityPtr(LLVMBuilderRef builder) {
  return LLVMBuildStructGEP(builder, lgtTablePtrLE, 0, "wrcCapacityPtr");
}
LLVMValueRef LgtWeaks::getLgtFirstFreeLgtiPtr(LLVMBuilderRef builder) {
  return LLVMBuildStructGEP(builder, lgtTablePtrLE, 1, "wrcFirstFree");
}
LLVMValueRef LgtWeaks::getLgtEntriesArrayPtr(LLVMBuilderRef builder) {
  return LLVMBuildStructGEP(builder, lgtTablePtrLE, 2, "entries");
}
