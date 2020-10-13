#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <region/common/controlblock.h>
#include <utils/counters.h>
#include <utils/branch.h>
#include <region/common/common.h>
#include "lgtweaks.h"

constexpr int LGT_ENTRY_MEMBER_INDEX_FOR_GEN = 0;
constexpr int LGT_ENTRY_MEMBER_INDEX_FOR_NEXT_FREE = 1;

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;
constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI = 1;

LLVMValueRef LgtWeaks::getTargetGenFromWeakRef(
    LLVMBuilderRef builder,
    WeakFatPtrLE weakRefLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V2 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_LIMIT);
  auto headerLE = fatWeaks_.getHeaderFromWeakRef(builder, weakRefLE);
  assert(LLVMTypeOf(headerLE) == globalState->region->getWeakRefHeaderStruct());
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "actualGeni");
}

LLVMValueRef LgtWeaks::getLgtiFromWeakRef(
    LLVMBuilderRef builder,
    WeakFatPtrLE weakRefLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);
  auto headerLE = fatWeaks_.getHeaderFromWeakRef(builder, weakRefLE);
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI, "lgti");
}

void LgtWeaks::buildCheckLgti(
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V1:
      // fine, proceed
      break;
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::FAST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      // These dont have LGT
      assert(false);
      break;
    default:
      assert(false);
      break;
  }
  LLVMBuildCall(builder, checkLgti, &lgtiLE, 1, "");
}

LLVMTypeRef makeLgtEntryStruct(GlobalState* globalState) {
  auto lgtEntryStructL = LLVMStructCreateNamed(globalState->context, "__LgtEntry");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(LGT_ENTRY_MEMBER_INDEX_FOR_GEN == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));

  assert(LGT_ENTRY_MEMBER_INDEX_FOR_NEXT_FREE == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));

  LLVMStructSetBody(lgtEntryStructL, memberTypesL.data(), memberTypesL.size(), false);

  return lgtEntryStructL;
}

static LLVMValueRef makeLgtiHeader(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE,
    LLVMValueRef targetGenLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);
  auto headerLE = LLVMGetUndef(globalState->region->getWeakRefHeaderStruct());
  headerLE = LLVMBuildInsertValue(builder, headerLE, lgtiLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI, "headerWithLgti");
  headerLE = LLVMBuildInsertValue(builder, headerLE, targetGenLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "header");
  return headerLE;
}

LLVMValueRef LgtWeaks::getLGTEntryGenPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE) {
  auto genEntriesPtrLE =
      LLVMBuildLoad(builder, lgtEntriesArrayPtr, "lgtEntriesArrayPtr");
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
      LLVMBuildLoad(builder, lgtEntriesArrayPtr, "genEntriesArrayPtr");
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
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);

  if (refM->ownership == Ownership::SHARE) {
    // Shares never have weak refs
    assert(false);
  } else {
    auto lgtiPtrLE =
        LLVMBuildStructGEP(
            builder,
            controlBlockPtr.refLE,
            globalState->region->getControlBlock(refM->referend)->getMemberIndex(ControlBlockMember::LGTI),
            "lgtiPtr");
    return LLVMBuildLoad(builder, lgtiPtrLE, "lgti");
  }
}

LgtWeaks::LgtWeaks(
    GlobalState* globalState_,
    IReferendStructsSource* referendStructsSource_,
    IWeakRefStructsSource* weakRefStructsSource_,
    bool elideChecksForKnownLive_)
  : globalState(globalState_),
    fatWeaks_(globalState_, weakRefStructsSource_),
    referendStructsSource(referendStructsSource_),
    weakRefStructsSource(weakRefStructsSource_),
    elideChecksForKnownLive(elideChecksForKnownLive_) {
//  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto voidPtrLT = LLVMPointerType(int8LT, 0);
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  lgtEntryStructL = makeLgtEntryStruct(globalState);


  expandLgt = addExtern(globalState->mod, "__expandLgt", LLVMVoidTypeInContext(globalState->context), {});
  checkLgti = addExtern(globalState->mod, "__checkLgti", LLVMVoidTypeInContext(globalState->context), {int32LT});
  getNumLiveLgtEntries = addExtern(globalState->mod, "__getNumLiveLgtEntries", int32LT, {});

  lgtCapacityPtr = LLVMAddGlobal(globalState->mod, LLVMInt32TypeInContext(globalState->context), "__lgt_capacity");
  LLVMSetLinkage(lgtCapacityPtr, LLVMExternalLinkage);

  lgtFirstFreeLgtiPtr = LLVMAddGlobal(globalState->mod, LLVMInt32TypeInContext(globalState->context), "__lgt_firstFree");
  LLVMSetLinkage(lgtFirstFreeLgtiPtr, LLVMExternalLinkage);

  lgtEntriesArrayPtr = LLVMAddGlobal(globalState->mod, LLVMPointerType(lgtEntryStructL, 0), "__lgt_entries");
  LLVMSetLinkage(lgtEntriesArrayPtr, LLVMExternalLinkage);

  if (globalState->opt->census) {
    LLVMValueRef args[3] = {
        LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 0, false),
        LLVMBuildZExt(
            globalState->valeMainBuilder,
            LLVMBuildCall(
                globalState->valeMainBuilder, getNumLiveLgtEntries, nullptr, 0, "numLgtEntries"),
            LLVMInt64TypeInContext(globalState->context),
            ""),
        globalState->getOrMakeStringConstant("WRC leaks!"),
    };
    LLVMBuildCall(globalState->valeMainBuilder, globalState->assertI64Eq, args, 3, "");
  }

}

WeakFatPtrLE LgtWeaks::weakStructPtrToLgtiWeakInterfacePtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V1:
      // continue
      break;
    case RegionOverride::FAST:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
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
      globalState->region->getWeakRefStructsSource()->getInterfaceWeakRefStruct(
          targetInterfaceReferendM);
  auto headerLE = fatWeaks_.getHeaderFromWeakRef(builder, sourceRefLE);

  auto innerRefLE =
      makeInterfaceRefStruct(
          globalState, functionState, builder, sourceStructReferendM, targetInterfaceReferendM, controlBlockPtr);

  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetInterfaceTypeM, interfaceRefLT, headerLE, innerRefLE);
}


// Makes a non-weak interface ref into a weak interface ref
WeakFatPtrLE LgtWeaks::assembleInterfaceWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    InterfaceReferend* interfaceReferendM,
    InterfaceFatPtrLE sourceInterfaceFatPtrLE) {
  assert(sourceType->ownership == Ownership::OWN || sourceType->ownership == Ownership::SHARE);
  // curious, if its a borrow, do we just return sourceRefLE?

  auto controlBlockPtrLE =
      referendStructsSource->getControlBlockPtr(
          FL(), functionState, builder, interfaceReferendM, sourceInterfaceFatPtrLE);
  auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, sourceType,
      controlBlockPtrLE);
  auto currentGenLE = getActualGenFromLGT(functionState, builder, lgtiLE);
  auto headerLE = makeLgtiHeader(globalState, builder, lgtiLE, currentGenLE);

  auto weakRefStructLT =
      globalState->region->getWeakRefStructsSource()->getInterfaceWeakRefStruct(interfaceReferendM);

  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetType, weakRefStructLT, headerLE, sourceInterfaceFatPtrLE.refLE);
}

WeakFatPtrLE LgtWeaks::assembleStructWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    Reference* targetTypeM,
    StructReferend* structReferendM,
    WrapperPtrLE objPtrLE) {
  assert(structTypeM->ownership == Ownership::OWN || structTypeM->ownership == Ownership::SHARE);
  // curious, if its a borrow, do we just return sourceRefLE?

  auto controlBlockPtrLE =
      referendStructsSource->getConcreteControlBlockPtr(
          FL(), functionState, builder, structTypeM, objPtrLE);
  auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);
  buildFlare(FL(), globalState, functionState, builder, lgtiLE);
  auto currentGenLE = getActualGenFromLGT(functionState, builder, lgtiLE);
  auto headerLE = makeLgtiHeader(globalState, builder, lgtiLE, currentGenLE);
  auto weakRefStructLT =
      globalState->region->getWeakRefStructsSource()->getStructWeakRefStruct(structReferendM);
  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

WeakFatPtrLE LgtWeaks::assembleKnownSizeArrayWeakRef(
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

WeakFatPtrLE LgtWeaks::assembleUnknownSizeArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    UnknownSizeArrayT* unknownSizeArrayMT,
    Reference* targetUSAWeakRefMT,
    WrapperPtrLE sourceRefLE) {
  auto controlBlockPtrLE =
      referendStructsSource->getConcreteControlBlockPtr(
          FL(), functionState, builder, sourceType, sourceRefLE);
  auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, sourceType, controlBlockPtrLE);
  auto targetGenLE = getActualGenFromLGT(functionState, builder, lgtiLE);
  auto headerLE = makeLgtiHeader(globalState, builder, lgtiLE, targetGenLE);

  auto weakRefStructLT =
      globalState->region->getWeakRefStructsSource()->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT);
  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetUSAWeakRefMT, weakRefStructLT, headerLE, sourceRefLE.refLE);
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
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);

  // uint64_t resultLgti = __lgt_firstFree;
  auto resultLgtiLE = LLVMBuildLoad(builder, lgtFirstFreeLgtiPtr, "resultLgti");

  // if (resultLgti == __lgt_capacity) {
  //   __expandLgtTable();
  // }
  auto atCapacityLE =
      LLVMBuildICmp(
          builder,
          LLVMIntEQ,
          resultLgtiLE,
          LLVMBuildLoad(builder, lgtCapacityPtr, "lgtCapacity"),
          "atCapacity");
  buildIf(
      globalState, functionState,
      builder,
      atCapacityLE,
      [this](LLVMBuilderRef thenBuilder) {
        LLVMBuildCall(thenBuilder, expandLgt, nullptr, 0, "");
      });

  // __LGT_Entry* lgtEntryPtr = &__lgt_entries[resultLgti];
  auto lgtNextFreePtrLE = getLGTEntryNextFreePtr(builder, resultLgtiLE);

  // __lgt_firstFree = lgtEntryPtr->nextFree;
  LLVMBuildStore(
      builder,
      // lgtEntryPtr->nextFree
      LLVMBuildLoad(builder, lgtNextFreePtrLE, ""),
      // __lgt_firstFree
      lgtFirstFreeLgtiPtr);

  return resultLgtiLE;
}

void LgtWeaks::innerNoteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* concreteRefM,
    ControlBlockPtrLE controlBlockPtrLE) {
  auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, concreteRefM,
      controlBlockPtrLE);
  auto ptrToActualGenLE = getLGTEntryGenPtr(functionState, builder, lgtiLE);
  adjustCounter(globalState, builder, ptrToActualGenLE, 1);
  auto ptrToLgtEntryNextFreeLE = getLGTEntryNextFreePtr(builder, lgtiLE);

  // __lgt_entries[lgti] = __lgt_firstFree;
  LLVMBuildStore(
      builder,
      LLVMBuildLoad(
          builder, lgtFirstFreeLgtiPtr, "firstFreeLgti"),
      ptrToLgtEntryNextFreeLE);
  // __lgt_firstFree = lgti;
  LLVMBuildStore(builder, lgtiLE, lgtFirstFreeLgtiPtr);
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
    auto targetGenLE = getTargetGenFromWeakRef(builder, weakFatPtrLE);

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
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, isAliveLE);
  } else {
    auto weakFatPtrLE =
        weakRefStructsSource->makeWeakFatPtr(
            weakRefM,
            globalState->region->checkValidReference(FL(), functionState, builder, weakRefM,
                weakRef));
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, weakRefM, weakFatPtrLE, knownLive);
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, isAliveLE);
  }
}

LLVMValueRef LgtWeaks::fillWeakableControlBlock(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef controlBlockLE) {
  auto geniLE = getNewLgti(functionState, builder);
  return LLVMBuildInsertValue(
      builder,
      controlBlockLE,
      geniLE,
      globalState->region->getControlBlock(referendM)->getMemberIndex(ControlBlockMember::LGTI),
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
  auto targetGen = getTargetGenFromWeakRef(builder, weakFatPtrLE);
  buildCheckGen(globalState, functionState, builder, targetGen, actualGen);

  // This will also run for objects which have since died, which is fine.
  if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(weakRefM->referend)) {
    auto interfaceFatPtrLE =
        referendStructsSource->makeInterfaceFatPtrWithoutChecking(
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


LLVMTypeRef LgtWeaks::makeWeakRefHeaderStruct(GlobalState* globalState) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);
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