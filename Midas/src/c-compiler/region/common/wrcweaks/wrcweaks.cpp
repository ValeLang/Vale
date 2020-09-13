#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <region/common/controlblock.h>
#include <utils/counters.h>
#include <utils/branch.h>
#include <region/common/common.h>
#include "wrcweaks.h"

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI = 0;

constexpr uint32_t WRC_ALIVE_BIT = 0x80000000;
constexpr uint32_t WRC_INITIAL_VALUE = WRC_ALIVE_BIT;

static LLVMValueRef makeWrciHeader(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE) {
  auto headerLE = LLVMGetUndef(globalState->region->getWeakRefHeaderStruct());
  return LLVMBuildInsertValue(builder, headerLE, wrciLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI, "header");
}

void WrcWeaks::buildCheckWrc(
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::FAST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
      // fine, proceed
      break;
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      // These dont have WRCs
      assert(false);
      break;
    default:
      assert(false);
      break;
  }
  LLVMBuildCall(builder, checkWrci, &wrciLE, 1, "");
}

LLVMValueRef WrcWeaks::getWrciFromWeakRef(
    LLVMBuilderRef builder,
    WeakFatPtrLE weakFatPtrLE) {
  assert(globalState->opt->regionOverride != RegionOverride::RESILIENT_V1);
  auto headerLE = fatWeaks_.getHeaderFromWeakRef(builder, weakFatPtrLE);
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI, "wrci");
}

void WrcWeaks::maybeReleaseWrc(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE,
    LLVMValueRef ptrToWrcLE,
    LLVMValueRef wrcLE) {
  buildIf(
      functionState,
      builder,
      isZeroLE(builder, wrcLE),
      [this, functionState, wrciLE, ptrToWrcLE](LLVMBuilderRef thenBuilder) {
        // __wrc_entries[wrcIndex] = __wrc_firstFree;
        LLVMBuildStore(
            thenBuilder,
            LLVMBuildLoad(
                thenBuilder, wrcFirstFreeWrciPtr, "firstFreeWrci"),
            ptrToWrcLE);
        // __wrc_firstFree = wrcIndex;
        LLVMBuildStore(thenBuilder, wrciLE, wrcFirstFreeWrciPtr);
      });
}

static LLVMValueRef getWrciFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
  assert(globalState->opt->regionOverride != RegionOverride::RESILIENT_V1);

  if (refM->ownership == Ownership::SHARE) {
    // Shares never have weak refs
    assert(false);
  } else {
    auto wrciPtrLE =
        LLVMBuildStructGEP(
            builder,
            controlBlockPtr.refLE,
            globalState->region->getControlBlock(refM->referend)->getMemberIndex(ControlBlockMember::WRCI),
            "wrciPtr");
    return LLVMBuildLoad(builder, wrciPtrLE, "wrci");
  }
}

LLVMValueRef WrcWeaks::getWrcPtr(
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE) {
  auto wrcEntriesPtrLE =
      LLVMBuildLoad(builder, wrcEntriesArrayPtr, "wrcEntriesArrayPtr");
  auto ptrToWrcLE =
      LLVMBuildGEP(builder, wrcEntriesPtrLE, &wrciLE, 1, "ptrToWrc");
  return ptrToWrcLE;
}

WrcWeaks::WrcWeaks(GlobalState *globalState_)
  : globalState(globalState_),
    fatWeaks_(globalState_) {
  auto voidLT = LLVMVoidType();
  auto voidPtrLT = LLVMPointerType(voidLT, 0);
  auto int1LT = LLVMInt1Type();
  auto int8LT = LLVMInt8Type();
  auto int32LT = LLVMInt32Type();
  auto int64LT = LLVMInt64Type();
  auto int8PtrLT = LLVMPointerType(int8LT, 0);


  if (globalState->opt->census) {
    LLVMValueRef args[3] = {
        LLVMConstInt(LLVMInt64Type(), 0, false),
        LLVMBuildZExt(
            globalState->valeMainBuilder,
            LLVMBuildCall(
                globalState->valeMainBuilder, getNumWrcs, nullptr, 0, "numWrcs"),
            LLVMInt64Type(),
            ""),
        globalState->getOrMakeStringConstant("WRC leaks!"),
    };
    LLVMBuildCall(globalState->valeMainBuilder, globalState->assertI64Eq, args, 3, "");
  }

  expandWrcTable = addExtern(globalState->mod, "__expandWrcTable", voidLT, {});
  checkWrci = addExtern(globalState->mod, "__checkWrc", voidLT, {int32LT});
  getNumWrcs = addExtern(globalState->mod, "__getNumWrcs", int32LT, {});

  wrcCapacityPtr = LLVMAddGlobal(globalState->mod, LLVMInt32Type(), "__wrc_capacity");
  LLVMSetLinkage(wrcCapacityPtr, LLVMExternalLinkage);

  wrcFirstFreeWrciPtr = LLVMAddGlobal(globalState->mod, LLVMInt32Type(), "__wrc_firstFree");
  LLVMSetLinkage(wrcFirstFreeWrciPtr, LLVMExternalLinkage);

  wrcEntriesArrayPtr = LLVMAddGlobal(globalState->mod, LLVMPointerType(LLVMInt32Type(), 0), "__wrc_entries");
  LLVMSetLinkage(wrcEntriesArrayPtr, LLVMExternalLinkage);
}

WeakFatPtrLE WrcWeaks::weakStructPtrToWrciWeakInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceWeakStructFatPtrLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::FAST:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
      // continue
      break;
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      assert(false);
      break;
    default:
      assert(false);
      break;
  }

//  checkValidReference(
//      FL(), globalState, functionState, builder, sourceStructTypeM, sourceRefLE);
  auto controlBlockPtr =
      getConcreteControlBlockPtr(globalState,
          builder,
          functionState->defaultRegion->makeWrapperPtr(
              sourceStructTypeM,
              fatWeaks_.getInnerRefFromWeakRef(
                  functionState, builder, sourceStructTypeM, sourceWeakStructFatPtrLE)));

  auto interfaceRefLT =
      globalState->region->getWeakRefStructsSource()->getInterfaceWeakRefStruct(targetInterfaceReferendM);
  auto wrciLE = getWrciFromWeakRef(builder, sourceWeakStructFatPtrLE);
  auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

  auto innerRefLE =
      makeInterfaceRefStruct(
          globalState, functionState, builder, sourceStructReferendM,
          targetInterfaceReferendM,
          controlBlockPtr);

  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetInterfaceTypeM, interfaceRefLT, headerLE, innerRefLE);
}

// Makes a non-weak interface ref into a weak interface ref
WeakFatPtrLE WrcWeaks::assembleInterfaceWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    InterfaceReferend* interfaceReferendM,
    InterfaceFatPtrLE sourceInterfaceFatPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
    if (sourceType->ownership == Ownership::BORROW) {
      assert(false); // curiosity, wouldnt we just return sourceRefLE?
    }
    assert(sourceType->ownership == Ownership::SHARE || sourceType->ownership == Ownership::OWN);
  }

  auto controlBlockPtrLE = getControlBlockPtr(globalState, builder, sourceInterfaceFatPtrLE);
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, sourceType,
      controlBlockPtrLE);
  auto headerLE = LLVMGetUndef(globalState->region->getWeakRefHeaderStruct());
  headerLE = LLVMBuildInsertValue(builder, headerLE, wrciLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI, "header");

  auto weakRefStructLT =
      globalState->region->getWeakRefStructsSource()->getInterfaceWeakRefStruct(interfaceReferendM);

  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetType, weakRefStructLT, headerLE, sourceInterfaceFatPtrLE.refLE);
}

WeakFatPtrLE WrcWeaks::assembleStructWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    Reference* targetTypeM,
    StructReferend* structReferendM,
    WrapperPtrLE objPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
    assert(structTypeM->ownership == Ownership::OWN || structTypeM->ownership == Ownership::SHARE);
  } else if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
      globalState->opt->regionOverride == RegionOverride::NAIVE_RC ||
      globalState->opt->regionOverride == RegionOverride::FAST) {
    assert(structTypeM->ownership == Ownership::OWN || structTypeM->ownership == Ownership::SHARE || structTypeM->ownership == Ownership::BORROW);
  } else assert(false);

  auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, objPtrLE);
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);
  auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

  auto weakRefStructLT =
      globalState->region->getWeakRefStructsSource()->getStructWeakRefStruct(structReferendM);

  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

WeakFatPtrLE WrcWeaks::assembleKnownSizeArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceKSAMT,
    KnownSizeArrayT* knownSizeArrayMT,
    Reference* targetKSAWeakRefMT,
    WrapperPtrLE objPtrLE) {
  auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, objPtrLE);
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, sourceKSAMT, controlBlockPtrLE);
  auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

  auto weakRefStructLT =
      globalState->region->getWeakRefStructsSource()->getKnownSizeArrayWeakRefStruct(knownSizeArrayMT);

  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetKSAWeakRefMT, weakRefStructLT, headerLE, objPtrLE.refLE);
}

WeakFatPtrLE WrcWeaks::assembleUnknownSizeArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    UnknownSizeArrayT* unknownSizeArrayMT,
    Reference* targetUSAWeakRefMT,
    WrapperPtrLE sourceRefLE) {
  auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, sourceRefLE);
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, sourceType, controlBlockPtrLE);
  auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

  auto weakRefStructLT =
      globalState->region->getWeakRefStructsSource()->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT);

  return fatWeaks_.assembleWeakFatPtr(
      functionState, builder, targetUSAWeakRefMT, weakRefStructLT, headerLE, sourceRefLE.refLE);
}

LLVMValueRef WrcWeaks::lockWrciFatPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    WeakFatPtrLE weakFatPtrLE) {
  auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, refM, weakFatPtrLE);
  buildIf(
      functionState, builder, isZeroLE(builder, isAliveLE),
      [this, from, functionState, weakFatPtrLE](LLVMBuilderRef thenBuilder) {
        buildPrintAreaAndFileAndLine(globalState, thenBuilder, from);
        buildPrint(globalState, thenBuilder, "Tried dereferencing dangling reference! ");
        assert(globalState->opt->regionOverride != RegionOverride::RESILIENT_V1);
        auto wrciLE = getWrciFromWeakRef(thenBuilder, weakFatPtrLE);
        buildPrint(globalState, thenBuilder, "Wrci: ");
        buildPrint(globalState, thenBuilder, wrciLE);
        buildPrint(globalState, thenBuilder, " ");
        buildPrint(globalState, thenBuilder, "Exiting!\n");
        auto exitCodeIntLE = LLVMConstInt(LLVMInt8Type(), 255, false);
        LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");
      });
  return fatWeaks_.getInnerRefFromWeakRef(functionState, builder, refM, weakFatPtrLE);
}

LLVMValueRef WrcWeaks::getNewWrci(
    FunctionState* functionState,
    LLVMBuilderRef builder) {
  assert(
      globalState->opt->regionOverride == RegionOverride::ASSIST ||
          globalState->opt->regionOverride == RegionOverride::NAIVE_RC ||
          globalState->opt->regionOverride == RegionOverride::FAST ||
          globalState->opt->regionOverride == RegionOverride::RESILIENT_V0);

  // uint64_t resultWrci = __wrc_firstFree;
  auto resultWrciLE = LLVMBuildLoad(builder, wrcFirstFreeWrciPtr, "resultWrci");

  // if (resultWrci == __wrc_capacity) {
  //   __expandWrcTable();
  // }
  auto atCapacityLE =
      LLVMBuildICmp(
          builder,
          LLVMIntEQ,
          resultWrciLE,
          LLVMBuildLoad(builder, wrcCapacityPtr, "wrcCapacity"),
          "atCapacity");
  buildIf(
      functionState,
      builder,
      atCapacityLE,
      [this](LLVMBuilderRef thenBuilder) {
        LLVMBuildCall(thenBuilder, expandWrcTable, nullptr, 0, "");
      });

  // u64* wrcPtr = &__wrc_entries[resultWrci];
  auto wrcPtrLE = getWrcPtr(builder, resultWrciLE);

  // __wrc_firstFree = *wrcPtr;
  LLVMBuildStore(
      builder,
      // *wrcPtr
      LLVMBuildLoad(builder, wrcPtrLE, ""),
      // __wrc_firstFree
      wrcFirstFreeWrciPtr);

  // *wrcPtr = WRC_INITIAL_VALUE;
  LLVMBuildStore(
      builder,
      LLVMConstInt(LLVMInt32Type(), WRC_INITIAL_VALUE, false),
      wrcPtrLE);

  return resultWrciLE;
}

void WrcWeaks::innerNoteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* concreteRefM,
    ControlBlockPtrLE controlBlockPtrLE) {
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, concreteRefM,
      controlBlockPtrLE);

  //  LLVMBuildCall(builder, globalState->noteWeakableDestroyed, &wrciLE, 1, "");

  auto ptrToWrcLE = getWrcPtr(builder, wrciLE);
  auto prevWrcLE = LLVMBuildLoad(builder, ptrToWrcLE, "wrc");

  auto wrcLE =
      LLVMBuildAnd(
          builder,
          prevWrcLE,
          LLVMConstInt(LLVMInt32Type(), ~WRC_ALIVE_BIT, true),
          "");

  // Equivalent:
  // __wrc_entries[wrcIndex] = __wrc_entries[wrcIndex] & ~WRC_LIVE_BIT;
  // *wrcPtr = *wrcPtr & ~WRC_LIVE_BIT;
  LLVMBuildStore(builder, wrcLE, ptrToWrcLE);

  buildFlare(FL(), globalState, functionState, builder, "maybeReleasing wrci ", wrciLE, " is now ", wrcLE);

  maybeReleaseWrc(functionState, builder, wrciLE, ptrToWrcLE, wrcLE);
}


void WrcWeaks::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  auto weakFatPtrLE =
      functionState->defaultRegion->makeWeakFatPtr(
          weakRefMT,
          globalState->region->checkValidReference(FL(), functionState, builder, weakRefMT, weakRef));
  auto wrciLE = getWrciFromWeakRef(builder, weakFatPtrLE);
  if (globalState->opt->census) {
    buildCheckWrc(builder, wrciLE);
  }

  auto ptrToWrcLE = getWrcPtr(builder, wrciLE);
  adjustCounter(builder, ptrToWrcLE, 1);
}

void WrcWeaks::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  auto weakFatPtrLE =
      functionState->defaultRegion->makeWeakFatPtr(
          weakRefMT,
          globalState->region->checkValidReference(FL(), functionState, builder, weakRefMT, weakRef));
  auto wrciLE = getWrciFromWeakRef(builder, weakFatPtrLE);
  if (globalState->opt->census) {
    buildCheckWrc(builder, wrciLE);
  }

  auto ptrToWrcLE = getWrcPtr(builder, wrciLE);
  auto wrcLE = adjustCounter(builder, ptrToWrcLE, -1);

  buildFlare(FL(), globalState, functionState, builder, "decrementing ", wrciLE, " to ", wrcLE);

  maybeReleaseWrc(functionState, builder, wrciLE, ptrToWrcLE, wrcLE);
}


LLVMValueRef WrcWeaks::getIsAliveFromWeakFatPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    WeakFatPtrLE weakFatPtrLE) {
  auto wrciLE = getWrciFromWeakRef(builder, weakFatPtrLE);
  if (globalState->opt->census) {
    buildCheckWrc(builder, wrciLE);
  }

  auto ptrToWrcLE = getWrcPtr(builder, wrciLE);
  auto wrcLE = LLVMBuildLoad(builder, ptrToWrcLE, "wrc");
  return LLVMBuildICmp(
      builder,
      LLVMIntNE,
      LLVMBuildAnd(
          builder,
          wrcLE,
          LLVMConstInt(LLVMInt32Type(), WRC_ALIVE_BIT, false),
          "wrcLiveBitOrZero"),
      constI32LE(0),
      "wrcLive");
}

Ref WrcWeaks::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      assert(
          weakRefM->ownership == Ownership::BORROW ||
              weakRefM->ownership == Ownership::WEAK);
      break;
    case RegionOverride::FAST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
      assert(weakRefM->ownership == Ownership::WEAK);
      break;
    default:
      assert(false);
      break;
  }

  auto weakFatPtrLE =
      functionState->defaultRegion->makeWeakFatPtr(          weakRefM,
          globalState->region->checkValidReference(FL(), functionState, builder, weakRefM, weakRef));
  auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, weakRefM, weakFatPtrLE);
  return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, isAliveLE);
}

LLVMValueRef WrcWeaks::fillWeakableControlBlock(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef controlBlockLE) {
  auto wrciLE = getNewWrci(functionState, builder);
  return LLVMBuildInsertValue(
      builder,
      controlBlockLE,
      wrciLE,
      globalState->region->getControlBlock(referendM)->getMemberIndex(ControlBlockMember::WRCI),
      "weakableControlBlockWithWrci");
}

WeakFatPtrLE WrcWeaks::weakInterfaceRefToWeakStructRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakInterfaceRefMT,
    WeakFatPtrLE weakInterfaceFatPtrLE) {
  // Disassemble the weak interface ref.
  auto wrciLE = getWrciFromWeakRef(builder, weakInterfaceFatPtrLE);
  auto interfaceRefLE =
      functionState->defaultRegion->makeInterfaceFatPtr(
          weakInterfaceRefMT, // It's still conceptually weak even though its not in a weak pointer.
          fatWeaks_.getInnerRefFromWeakRef(
              functionState,
              builder,
              weakInterfaceRefMT,
              weakInterfaceFatPtrLE));
  auto controlBlockPtrLE = getControlBlockPtr(globalState, builder, interfaceRefLE);

  auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

  // Now, reassemble a weak void* ref to the struct.
  auto weakVoidStructRefLE =
      fatWeaks_.assembleVoidStructWeakRef(
          builder,
          // We still think of it as an interface pointer, even though its a void*.
          // That kind of makes this makes sense.
          // We could think of this as making an "Any" pointer perhaps?
          weakInterfaceRefMT,
          controlBlockPtrLE,
          headerLE);

  return weakVoidStructRefLE;
}

// USE ONLY FOR ASSERTING A REFERENCE IS VALID
std::tuple<Reference*, LLVMValueRef> wrcGetRefInnardsForChecking(Ref ref) {
  Reference* refM = ref.refM;
  LLVMValueRef refLE = ref.refLE;
  return std::make_tuple(refM, refLE);
}

void WrcWeaks::buildCheckWeakRef(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef) {
  Reference* actualRefM = nullptr;
  LLVMValueRef refLE = nullptr;
  std::tie(actualRefM, refLE) = wrcGetRefInnardsForChecking(weakRef);
  auto weakFatPtrLE = functionState->defaultRegion->makeWeakFatPtr(weakRefM, refLE);
  auto innerLE =
      fatWeaks_.getInnerRefFromWeakRefWithoutCheck(
          functionState, builder, weakRefM, weakFatPtrLE);

  // WARNING: This check has false positives.
  auto wrciLE = getWrciFromWeakRef(builder, weakFatPtrLE);
  buildCheckWrc(builder, wrciLE);

  // This will also run for objects which have since died, which is fine.
  if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(weakRefM->referend)) {
    auto interfaceFatPtrLE = functionState->defaultRegion->makeInterfaceFatPtr(weakRefM, innerLE);
    auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
  }
}

Ref WrcWeaks::assembleWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    Ref sourceRef) {
  // Now we need to package it up into a weak ref.
  if (auto structReferend = dynamic_cast<StructReferend*>(sourceType->referend)) {
    auto sourceRefLE = globalState->region->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = functionState->defaultRegion->makeWrapperPtr(sourceType, sourceRefLE);
    auto resultLE =
        assembleStructWeakRef(
            functionState, builder, sourceType, targetType, structReferend, sourceWrapperPtrLE);
    return wrap(functionState->defaultRegion, targetType, resultLE);
  } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(sourceType->referend)) {
    auto sourceRefLE = globalState->region->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceInterfaceFatPtrLE = functionState->defaultRegion->makeInterfaceFatPtr(sourceType, sourceRefLE);
    auto resultLE =
        assembleInterfaceWeakRef(
            functionState, builder, sourceType, targetType, interfaceReferendM, sourceInterfaceFatPtrLE);
    return wrap(functionState->defaultRegion, targetType, resultLE);
  } else if (auto knownSizeArray = dynamic_cast<KnownSizeArrayT*>(sourceType->referend)) {
    auto sourceRefLE = globalState->region->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = functionState->defaultRegion->makeWrapperPtr(sourceType, sourceRefLE);
    auto resultLE =
        assembleKnownSizeArrayWeakRef(
            functionState, builder, sourceType, knownSizeArray, targetType, sourceWrapperPtrLE);
    return wrap(functionState->defaultRegion, targetType, resultLE);
  } else if (auto unknownSizeArray = dynamic_cast<UnknownSizeArrayT*>(sourceType->referend)) {
    auto sourceRefLE = globalState->region->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = functionState->defaultRegion->makeWrapperPtr(sourceType, sourceRefLE);
    auto resultLE =
        assembleUnknownSizeArrayWeakRef(
            functionState, builder, sourceType, unknownSizeArray, targetType, sourceWrapperPtrLE);
    return wrap(functionState->defaultRegion, targetType, resultLE);
  } else assert(false);
}

LLVMTypeRef WrcWeaks::makeWeakRefHeaderStruct(GlobalState* globalState) {
  auto wrciRefStructL = LLVMStructCreateNamed(globalState->context, "__WrciRef");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32Type());

  LLVMStructSetBody(wrciRefStructL, memberTypesL.data(), memberTypesL.size(), false);

  return wrciRefStructL;
}
