#include "shared.h"
#include "weaks.h"

#include "translatetype.h"
#include "controlblock.h"
#include "branch.h"

constexpr uint32_t WRC_ALIVE_BIT = 0x80000000;
constexpr uint32_t WRC_INITIAL_VALUE = WRC_ALIVE_BIT;

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI = 0;

constexpr int LGT_ENTRY_MEMBER_INDEX_FOR_GEN = 0;
constexpr int LGT_ENTRY_MEMBER_INDEX_FOR_NEXT_FREE = 1;

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI = 0;
constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 1;

static void buildCheckWrc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE) {
  LLVMBuildCall(builder, globalState->checkWrci, &wrciLE, 1, "");
}

static void buildCheckGen(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE) {
  LLVMBuildCall(builder, globalState->checkLgti, &lgtiLE, 1, "");
}

static LLVMValueRef getWrciFromWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  assert(globalState->opt->regionOverride != RegionOverride::RESILIENT_V1);
  auto headerLE =
      LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "weakRefHeader");
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI, "wrci");
}

static LLVMValueRef getHeaderFromWeakRef(
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  return LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "weakRefHeader");
}

static LLVMValueRef getLgtiFromWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);
  auto headerLE =
      LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "weakRefHeader");
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI, "lgti");
}

static LLVMValueRef getLgtiFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef controlBlockPtr) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);

  if (refM->ownership == Ownership::SHARE) {
    // Shares never have weak refs
    assert(false);
  } else {
    auto lgtiPtrLE =
        LLVMBuildStructGEP(
            builder,
            controlBlockPtr,
            globalState->getControlBlockLayout(refM->referend)->getMemberIndex(ControlBlockMember::LGTI),
            "lgtiPtr");
    return LLVMBuildLoad(builder, lgtiPtrLE, "lgti");
  }
}

static LLVMValueRef getWrciFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef controlBlockPtr) {
  assert(globalState->opt->regionOverride != RegionOverride::RESILIENT_V1);

  if (refM->ownership == Ownership::SHARE) {
    // Shares never have weak refs
    assert(false);
  } else {
    auto wrciPtrLE =
        LLVMBuildStructGEP(
            builder,
            controlBlockPtr,
            globalState->getControlBlockLayout(refM->referend)->getMemberIndex(ControlBlockMember::WRCI),
            "wrciPtr");
    return LLVMBuildLoad(builder, wrciPtrLE, "wrci");
  }
}

static LLVMValueRef getWrcPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE) {
  auto wrcEntriesPtrLE =
      LLVMBuildLoad(builder, globalState->wrcEntriesArrayPtr, "wrcEntriesArrayPtr");
  auto ptrToWrcLE =
      LLVMBuildGEP(builder, wrcEntriesPtrLE, &wrciLE, 1, "ptrToWrc");
  return ptrToWrcLE;
}

static LLVMValueRef getTargetGenFromWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);
  auto headerLE =
      LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "weakRefHeader");
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "actualGeni");
}

static LLVMValueRef getLGTEntryGenPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE) {
  auto genEntriesPtrLE =
      LLVMBuildLoad(builder, globalState->lgtEntriesArrayPtr, "lgtEntriesArrayPtr");
  auto ptrToLGTEntryLE =
      LLVMBuildGEP(builder, genEntriesPtrLE, &lgtiLE, 1, "ptrToLGTEntry");
  auto ptrToLGTEntryGenLE =
      LLVMBuildStructGEP(builder, ptrToLGTEntryLE, LGT_ENTRY_MEMBER_INDEX_FOR_GEN, "ptrToLGTEntryGen");
  return ptrToLGTEntryGenLE;
}

static LLVMValueRef getActualGen(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE) {
  return LLVMBuildLoad(builder, getLGTEntryGenPtr(globalState, functionState, builder, lgtiLE), "lgti");
}

static LLVMValueRef getLGTEntryNextFreePtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE) {
  auto genEntriesPtrLE =
      LLVMBuildLoad(builder, globalState->lgtEntriesArrayPtr, "genEntriesArrayPtr");
  auto ptrToLGTEntryLE =
      LLVMBuildGEP(builder, genEntriesPtrLE, &lgtiLE, 1, "ptrToLGTEntry");
  auto ptrToLGTEntryGenLE =
      LLVMBuildStructGEP(builder, ptrToLGTEntryLE, LGT_ENTRY_MEMBER_INDEX_FOR_NEXT_FREE, "ptrToLGTEntryNextFree");
  return ptrToLGTEntryGenLE;
}

static LLVMValueRef makeGenHeader(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE,
    LLVMValueRef targetGenLE) {
  auto headerLE = LLVMGetUndef(globalState->weakRefHeaderStructL);
  headerLE = LLVMBuildInsertValue(builder, headerLE, lgtiLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI, "headerWithLgti");
  headerLE = LLVMBuildInsertValue(builder, headerLE, targetGenLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "header");
  return headerLE;
}

static LLVMValueRef makeWrciHeader(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE) {
  auto headerLE = LLVMGetUndef(globalState->weakRefHeaderStructL);
  return LLVMBuildInsertValue(builder, headerLE, wrciLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI, "header");
}

static void maybeReleaseWrc(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE,
    LLVMValueRef ptrToWrcLE,
    LLVMValueRef wrcLE) {
  buildIf(
      functionState,
      builder,
      isZeroLE(builder, wrcLE),
      [globalState, functionState, wrciLE, ptrToWrcLE](LLVMBuilderRef thenBuilder) {
        // __wrc_entries[wrcIndex] = __wrc_firstFree;
        LLVMBuildStore(
            thenBuilder,
            LLVMBuildLoad(
                thenBuilder, globalState->wrcFirstFreeWrciPtr, "firstFreeWrci"),
            ptrToWrcLE);
        // __wrc_firstFree = wrcIndex;
        LLVMBuildStore(thenBuilder, wrciLE, globalState->wrcFirstFreeWrciPtr);
      });
}

LLVMValueRef getInnerRefFromWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    LLVMValueRef weakRefLE) {
  checkValidReference(FL(), globalState, functionState, builder, weakRefM, weakRefLE);
  auto innerRefLE = LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR, "");
  // We dont check that its valid because if it's a weak ref, it might *not* be pointing at
  // a valid reference.
  return innerRefLE;
}

void aliasWeakRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    // Do nothing!
  } else {
    auto wrciLE = getWrciFromWeakRef(globalState, builder, exprLE);
    if (globalState->opt->census) {
      buildCheckWrc(globalState, builder, wrciLE);
    }

    auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
    adjustCounter(builder, ptrToWrcLE, 1);
  }
}

void discardWeakRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    // Do nothing!
  } else {
    auto wrciLE = getWrciFromWeakRef(globalState, builder, exprLE);
    if (globalState->opt->census) {
      buildCheckWrc(globalState, builder, wrciLE);
    }

    auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
    auto wrcLE = adjustCounter(builder, ptrToWrcLE, -1);

    maybeReleaseWrc(globalState, functionState, builder, wrciLE, ptrToWrcLE, wrcLE);
  }
}

// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
LLVMValueRef derefMaybeWeakRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef weakRefLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::FAST:
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::RESILIENT_V0: {
      switch (refM->ownership) {
        case Ownership::OWN:
        case Ownership::BORROW:
        case Ownership::SHARE: {
          auto objPtrLE = weakRefLE;
          return objPtrLE;
        }
        case Ownership::WEAK: {
          auto fatPtrLE = weakRefLE;
          auto isAliveLE = getIsAliveFromWeakRef(globalState, functionState, builder, fatPtrLE);
          buildIf(
              functionState, builder, isZeroLE(builder, isAliveLE),
              [from, globalState, functionState, fatPtrLE](LLVMBuilderRef thenBuilder) {
                buildPrintAreaAndFileAndLine(globalState, thenBuilder, from);
                buildPrint(globalState, thenBuilder, "Tried dereferencing dangling reference! ");
                assert(globalState->opt->regionOverride != RegionOverride::RESILIENT_V1);
                auto wrciLE = getWrciFromWeakRef(globalState, thenBuilder, fatPtrLE);
                buildPrint(globalState, thenBuilder, "Wrci: ");
                buildPrint(globalState, thenBuilder, wrciLE);
                buildPrint(globalState, thenBuilder, " ");
                buildPrint(globalState, thenBuilder, "Exiting!\n");
                auto exitCodeIntLE = LLVMConstInt(LLVMInt8Type(), 255, false);
                LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");
              });
          return getInnerRefFromWeakRef(globalState, functionState, builder, refM, fatPtrLE);
        }
        default:
          assert(false);
          break;
      }
    }
    case RegionOverride::RESILIENT_V1: {
      switch (refM->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE: {
          auto objPtrLE = weakRefLE;
          return objPtrLE;
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto fatPtrLE = weakRefLE;
          auto isAliveLE = getIsAliveFromWeakRef(globalState, functionState, builder, fatPtrLE);
          buildIf(
              functionState, builder, isZeroLE(builder, isAliveLE),
              [from, globalState, functionState, fatPtrLE](LLVMBuilderRef thenBuilder) {
                buildPrintAreaAndFileAndLine(globalState, thenBuilder, from);
                buildPrint(globalState, thenBuilder, "Tried dereferencing dangling reference! ");
                if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
                  auto lgtiLE = getLgtiFromWeakRef(globalState, thenBuilder, fatPtrLE);
                  buildPrint(globalState, thenBuilder, "lgti ");
                  buildPrint(globalState, thenBuilder, lgtiLE);
                  buildPrint(globalState, thenBuilder, " ");
                  auto targetGenLE = getTargetGenFromWeakRef(globalState, thenBuilder, fatPtrLE);
                  buildPrint(globalState, thenBuilder, "targetGen ");
                  buildPrint(globalState, thenBuilder, targetGenLE);
                  buildPrint(globalState, thenBuilder, " ");
                  auto actualGenLE = getActualGen(globalState, functionState, thenBuilder, lgtiLE);
                  buildPrint(globalState, thenBuilder, "actualGen ");
                  buildPrint(globalState, thenBuilder, actualGenLE);
                  buildPrint(globalState, thenBuilder, " ");
                } else {
                  auto wrciLE = getWrciFromWeakRef(globalState, thenBuilder, fatPtrLE);
                  buildPrint(globalState, thenBuilder, "Wrci: ");
                  buildPrint(globalState, thenBuilder, wrciLE);
                  buildPrint(globalState, thenBuilder, " ");
                }
                buildPrint(globalState, thenBuilder, "Exiting!\n");
                auto exitCodeIntLE = LLVMConstInt(LLVMInt8Type(), 255, false);
                LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");
              });
          return getInnerRefFromWeakRef(globalState, functionState, builder, refM, fatPtrLE);
        }
        default:
          assert(false);
          break;
      }
    }
    default:
      assert(false);
      break;
  }
}

void noteWeakableDestroyed(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* concreteRefM,
    LLVMValueRef controlBlockPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, concreteRefM, controlBlockPtrLE);
    auto ptrToActualGenLE = getLGTEntryGenPtr(globalState, functionState, builder, lgtiLE);
    adjustCounter(builder, ptrToActualGenLE, 1);
    auto ptrToLgtEntryNextFreeLE = getLGTEntryNextFreePtr(globalState, builder, lgtiLE);

    // __lgt_entries[lgti] = __lgt_firstFree;
    LLVMBuildStore(
        builder,
        LLVMBuildLoad(
            builder, globalState->lgtFirstFreeLgtiPtr, "firstFreeLgti"),
        ptrToLgtEntryNextFreeLE);
    // __lgt_firstFree = lgti;
    LLVMBuildStore(builder, lgtiLE, globalState->lgtFirstFreeLgtiPtr);
  } else {
    auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, concreteRefM, controlBlockPtrLE);

    //  LLVMBuildCall(builder, globalState->noteWeakableDestroyed, &wrciLE, 1, "");

    auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
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

    maybeReleaseWrc(globalState, functionState, builder, wrciLE, ptrToWrcLE, wrcLE);
  }
}


LLVMValueRef getIsAliveFromWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    // Get target generation from the ref
    auto targetGenLE = getTargetGenFromWeakRef(globalState, builder, weakRefLE);

    // Get actual generation from the table
    auto lgtiLE = getLgtiFromWeakRef(globalState, builder, weakRefLE);
    if (globalState->opt->census) {
      buildCheckGen(globalState, builder, lgtiLE);
    }
    auto ptrToActualGenLE = getLGTEntryGenPtr(globalState, functionState, builder, lgtiLE);
    auto actualGenLE = LLVMBuildLoad(builder, ptrToActualGenLE, "gen");

    return LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        actualGenLE,
        targetGenLE,
        "genLive");
  } else {
    auto wrciLE = getWrciFromWeakRef(globalState, builder, weakRefLE);
    if (globalState->opt->census) {
      buildCheckWrc(globalState, builder, wrciLE);
    }

    auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
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
}

LLVMValueRef noteWeakableCreated(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
      // uint64_t resultLgti = __lgt_firstFree;
      auto resultLgtiLE = LLVMBuildLoad(builder, globalState->lgtFirstFreeLgtiPtr, "resultLgti");

      // if (resultLgti == __lgt_capacity) {
      //   __expandLgtTable();
      // }
      auto atCapacityLE =
          LLVMBuildICmp(
              builder,
              LLVMIntEQ,
              resultLgtiLE,
              LLVMBuildLoad(builder, globalState->lgtCapacityPtr, "lgtCapacity"),
              "atCapacity");
      buildIf(
          functionState,
          builder,
          atCapacityLE,
          [globalState](LLVMBuilderRef thenBuilder) {
            LLVMBuildCall(thenBuilder, globalState->expandLgt, nullptr, 0, "");
          });

      // __LGT_Entry* lgtEntryPtr = &__lgt_entries[resultLgti];
      auto lgtNextFreePtrLE = getLGTEntryNextFreePtr(globalState, builder, resultLgtiLE);

      // __lgt_firstFree = lgtEntryPtr->nextFree;
      LLVMBuildStore(
          builder,
          // lgtEntryPtr->nextFree
          LLVMBuildLoad(builder, lgtNextFreePtrLE, ""),
          // __lgt_firstFree
          globalState->lgtFirstFreeLgtiPtr);

      return resultLgtiLE;
  } else {
    // uint64_t resultWrci = __wrc_firstFree;
    auto resultWrciLE = LLVMBuildLoad(builder, globalState->wrcFirstFreeWrciPtr, "resultWrci");

    // if (resultWrci == __wrc_capacity) {
    //   __expandWrcTable();
    // }
    auto atCapacityLE =
        LLVMBuildICmp(
            builder,
            LLVMIntEQ,
            resultWrciLE,
            LLVMBuildLoad(builder, globalState->wrcCapacityPtr, "wrcCapacity"),
            "atCapacity");
    buildIf(
        functionState,
        builder,
        atCapacityLE,
        [globalState](LLVMBuilderRef thenBuilder) {
          LLVMBuildCall(thenBuilder, globalState->expandWrcTable, nullptr, 0, "");
        });

    // u64* wrcPtr = &__wrc_entries[resultWrci];
    auto wrcPtrLE = getWrcPtr(globalState, builder, resultWrciLE);

    // __wrc_firstFree = *wrcPtr;
    LLVMBuildStore(
        builder,
        // *wrcPtr
        LLVMBuildLoad(builder, wrcPtrLE, ""),
        // __wrc_firstFree
        globalState->wrcFirstFreeWrciPtr);

    // *wrcPtr = WRC_INITIAL_VALUE;
    LLVMBuildStore(
        builder,
        LLVMConstInt(LLVMInt32Type(), WRC_INITIAL_VALUE, false),
        wrcPtrLE);

    return resultWrciLE;
  }
}

LLVMValueRef fillWeakableControlBlock(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef controlBlockLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    auto geniLE = noteWeakableCreated(globalState, functionState, builder);
    return LLVMBuildInsertValue(
        builder,
        controlBlockLE,
        geniLE,
        globalState->getControlBlockLayout(referendM)->getMemberIndex(ControlBlockMember::LGTI),
        "controlBlockWithLgti");
  } else {
    auto wrciLE = noteWeakableCreated(globalState, functionState, builder);
    return LLVMBuildInsertValue(
        builder,
        controlBlockLE,
        wrciLE,
        globalState->getControlBlockLayout(referendM)->getMemberIndex(ControlBlockMember::WRCI),
        "weakableControlBlockWithWrci");
  }
}

//LLVMValueRef getControlBlockPtrFromInterfaceWeakRef(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* virtualParamMT,
//    LLVMValueRef virtualArgLE) {
//  auto interfaceRefLE =
//      getInnerRefFromWeakRef(
//          globalState,
//          functionState,
//          builder,
//          virtualParamMT,
//          virtualArgLE);
//  return getControlBlockPtrFromInterfaceRef(builder, interfaceRefLE);
//}

LLVMValueRef weakInterfaceRefToWeakStructRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef exprLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    auto headerLE = getHeaderFromWeakRef(builder, exprLE);
    auto interfaceRefLE =
        getInnerRefFromWeakRef(
            globalState,
            functionState,
            builder,
            refM,
            exprLE);
    auto controlBlockPtrLE = getControlBlockPtrFromInterfaceRef(builder, interfaceRefLE);

//    auto objVoidPtrLE =
//        LLVMBuildPointerCast(
//            builder,
//            controlBlockPtrLE,
//            LLVMPointerType(LLVMVoidType(), 0),
//            "objAsVoidPtr");

    // Now, reassemble a weak void* ref to the struct.
    auto weakVoidStructRefLE =
        assembleVoidStructWeakRef(globalState, builder, controlBlockPtrLE, headerLE);

    return weakVoidStructRefLE;
  } else {
    // Disassemble the weak interface ref.
    auto wrciLE = getWrciFromWeakRef(globalState, builder, exprLE);
    auto interfaceRefLE =
        getInnerRefFromWeakRef(
            globalState,
            functionState,
            builder,
            refM,
            exprLE);
    auto controlBlockPtrLE = getControlBlockPtrFromInterfaceRef(builder, interfaceRefLE);

    auto objVoidPtrLE =
        LLVMBuildPointerCast(
            builder,
            controlBlockPtrLE,
            LLVMPointerType(LLVMVoidType(), 0),
            "objAsVoidPtr");
    auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

    // Now, reassemble a weak void* ref to the struct.
    auto weakVoidStructRefLE =
        assembleVoidStructWeakRef(globalState, builder, controlBlockPtrLE, headerLE);

    return weakVoidStructRefLE;
  }
}

void buildCheckWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    // WARNING: This check has false positives.
    auto lgtiLE = getLgtiFromWeakRef(globalState, builder, weakRefLE);
    buildCheckGen(globalState, builder, lgtiLE);
  } else {
    // WARNING: This check has false positives.
    auto wrciLE = getWrciFromWeakRef(globalState, builder, weakRefLE);
    buildCheckWrc(globalState, builder, wrciLE);
  }
}

LLVMValueRef assembleInterfaceWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* interfaceTypeM,
    InterfaceReferend* interfaceReferendM,
    LLVMValueRef fatPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    auto controlBlockPtrLE = getControlBlockPtr(builder, fatPtrLE, interfaceTypeM);
    auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, interfaceTypeM,
        controlBlockPtrLE);
    auto currentGenLE = getActualGen(globalState, functionState, builder, lgtiLE);
    auto headerLE = makeGenHeader(globalState, builder, lgtiLE, currentGenLE);

    auto weakRefLE = LLVMGetUndef(
        globalState->getInterfaceWeakRefStruct(interfaceReferendM->fullName));
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, fatPtrLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
        "");

    return weakRefLE;
  } else {
    auto controlBlockPtrLE = getControlBlockPtr(builder, fatPtrLE, interfaceTypeM);
    auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, interfaceTypeM,
        controlBlockPtrLE);
    auto headerLE = LLVMGetUndef(globalState->weakRefHeaderStructL);
    headerLE = LLVMBuildInsertValue(builder, headerLE, wrciLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI, "header");

    auto weakRefLE = LLVMGetUndef(
        globalState->getInterfaceWeakRefStruct(interfaceReferendM->fullName));
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, fatPtrLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
        "");

    return weakRefLE;
  }
}

LLVMValueRef assembleStructWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    StructReferend* structReferendM,
    LLVMValueRef objPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    buildFlare(FL(), globalState, functionState, builder);
    auto controlBlockPtrLE = getControlBlockPtr(builder, objPtrLE, structTypeM);
    auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, structTypeM,
        controlBlockPtrLE);
    buildFlare(FL(), globalState, functionState, builder, lgtiLE);
//    auto currentGenPtrLE = getLGTEntryGenPtr(globalState, builder, lgtiLE);
//    buildFlare(FL(), globalState, functionState, builder);
    auto currentGenLE = getActualGen(globalState, functionState, builder, lgtiLE);
    buildFlare(FL(), globalState, functionState, builder);
    auto headerLE = makeGenHeader(globalState, builder, lgtiLE, currentGenLE);
    buildFlare(FL(), globalState, functionState, builder);

    auto weakRefLE = LLVMGetUndef(globalState->getStructWeakRefStruct(structReferendM->fullName));
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, objPtrLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,"");

    buildFlare(FL(), globalState, functionState, builder);

    return weakRefLE;
  } else {
    buildFlare(FL(), globalState, functionState, builder);
    auto controlBlockPtrLE = getControlBlockPtr(builder, objPtrLE, structTypeM);
    auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);
    auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

    auto weakRefLE = LLVMGetUndef(globalState->getStructWeakRefStruct(structReferendM->fullName));
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, objPtrLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,"");

    return weakRefLE;
  }
}

LLVMValueRef assembleKnownSizeArrayWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    KnownSizeArrayT* knownSizeArrayMT,
    LLVMValueRef objPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    // impl
    assert(false);
  } else {
    auto controlBlockPtrLE = getControlBlockPtr(builder, objPtrLE, structTypeM);
    auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);
    auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

    auto weakRefLE = LLVMGetUndef(
        globalState->getKnownSizeArrayWeakRefStruct(knownSizeArrayMT->name));
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objPtrLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
        "");

    return weakRefLE;
  }
}

LLVMValueRef assembleUnknownSizeArrayWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    UnknownSizeArrayT* unknownSizeArrayMT,
    LLVMValueRef objPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    auto controlBlockPtrLE = getControlBlockPtr(builder, objPtrLE, structTypeM);
    auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);
    auto targetGenLE = getActualGen(globalState, functionState, builder, lgtiLE);
    auto headerLE = makeGenHeader(globalState, builder, lgtiLE, targetGenLE);

    auto weakRefLE = LLVMGetUndef(
        globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name));
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, objPtrLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,"");

    return weakRefLE;
  } else {
    auto controlBlockPtrLE = getControlBlockPtr(builder, objPtrLE, structTypeM);
    auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);
    auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

    auto weakRefLE = LLVMGetUndef(
        globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name));
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objPtrLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
        "");

    return weakRefLE;
  }
}

// Used in interface calling, when we dont know what the underlying struct type is yet.
LLVMValueRef assembleVoidStructWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtrLE,
    LLVMValueRef headerLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    auto objVoidPtrLE =
        LLVMBuildPointerCast(
            builder,
            controlBlockPtrLE,
            LLVMPointerType(LLVMVoidType(), 0),
            "objAsVoidPtr");

    auto weakRefLE = LLVMGetUndef(globalState->weakVoidRefStructL);
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objVoidPtrLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
        "");

    return weakRefLE;
  } else {
    auto objVoidPtrLE =
        LLVMBuildPointerCast(
            builder,
            controlBlockPtrLE,
            LLVMPointerType(LLVMVoidType(), 0),
            "objAsVoidPtr");

    auto weakRefLE = LLVMGetUndef(globalState->weakVoidRefStructL);
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objVoidPtrLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
        "");

    return weakRefLE;
  }
}


LLVMValueRef weakStructRefToWeakInterfaceRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    checkValidReference(
        FL(), globalState, functionState, builder, sourceStructTypeM, sourceRefLE);
    auto controlBlockPtr =
        getConcreteControlBlockPtr(
            builder,
            getInnerRefFromWeakRef(
                globalState, functionState, builder, sourceStructTypeM, sourceRefLE));

    auto interfaceRefLT =
        globalState->getInterfaceWeakRefStruct(
            targetInterfaceReferendM->fullName);
    auto headerLE = getHeaderFromWeakRef(builder, sourceRefLE);

    auto interfaceWeakRefLE = LLVMGetUndef(interfaceRefLT);
    interfaceWeakRefLE =
        LLVMBuildInsertValue(
            builder,
            interfaceWeakRefLE,
            headerLE,
            WEAK_REF_MEMBER_INDEX_FOR_HEADER,
            "interfaceRefWithOnlyObj");
    interfaceWeakRefLE =
        LLVMBuildInsertValue(
            builder,
            interfaceWeakRefLE,
            makeInterfaceRefStruct(
                globalState, builder, sourceStructReferendM, targetInterfaceReferendM,
                controlBlockPtr),
            WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
            "interfaceRef");
    checkValidReference(
        FL(), globalState, functionState, builder, targetInterfaceTypeM, interfaceWeakRefLE);
    return interfaceWeakRefLE;
  } else {
    checkValidReference(
        FL(), globalState, functionState, builder, sourceStructTypeM, sourceRefLE);
    auto controlBlockPtr =
        getConcreteControlBlockPtr(
            builder,
            getInnerRefFromWeakRef(
                globalState, functionState, builder, sourceStructTypeM, sourceRefLE));

    auto interfaceRefLT =
        globalState->getInterfaceWeakRefStruct(
            targetInterfaceReferendM->fullName);
    auto wrciLE = getWrciFromWeakRef(globalState, builder, sourceRefLE);
    auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

    auto interfaceWeakRefLE = LLVMGetUndef(interfaceRefLT);
    interfaceWeakRefLE =
        LLVMBuildInsertValue(
            builder,
            interfaceWeakRefLE,
            headerLE,
            WEAK_REF_MEMBER_INDEX_FOR_HEADER,
            "interfaceRefWithOnlyObj");
    interfaceWeakRefLE =
        LLVMBuildInsertValue(
            builder,
            interfaceWeakRefLE,
            makeInterfaceRefStruct(
                globalState, builder, sourceStructReferendM, targetInterfaceReferendM,
                controlBlockPtr),
            WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
            "interfaceRef");
    checkValidReference(
        FL(), globalState, functionState, builder, targetInterfaceTypeM, interfaceWeakRefLE);
    return interfaceWeakRefLE;
  }
}

LLVMTypeRef makeGenIndStruct(GlobalState* globalState) {
  auto genRefStructL = LLVMStructCreateNamed(globalState->context, "__GenRef");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32Type());

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32Type());

  LLVMStructSetBody(genRefStructL, memberTypesL.data(), memberTypesL.size(), false);

  assert(
      LLVMABISizeOfType(globalState->dataLayout, genRefStructL) ==
      LLVMABISizeOfType(globalState->dataLayout, LLVMInt64Type()));

  return genRefStructL;
}

LLVMTypeRef makeLgtEntryStruct(GlobalState* globalState) {
  auto lgtEntryStructL = LLVMStructCreateNamed(globalState->context, "__LgtEntry");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(LGT_ENTRY_MEMBER_INDEX_FOR_GEN == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32Type());

  assert(LGT_ENTRY_MEMBER_INDEX_FOR_NEXT_FREE == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32Type());

  LLVMStructSetBody(lgtEntryStructL, memberTypesL.data(), memberTypesL.size(), false);

  return lgtEntryStructL;
}

LLVMTypeRef makeWrciStruct(GlobalState* globalState) {
  auto wrciRefStructL = LLVMStructCreateNamed(globalState->context, "__WrciRef");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32Type());

  LLVMStructSetBody(wrciRefStructL, memberTypesL.data(), memberTypesL.size(), false);

  return wrciRefStructL;
}

void makeWeakRefStructs(GlobalState* globalState) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    globalState->lgtEntryStructL = makeLgtEntryStruct(globalState);

    globalState->weakRefHeaderStructL = makeGenIndStruct(globalState);
  } else {
    globalState->weakRefHeaderStructL = makeWrciStruct(globalState);
  }
}

void initWeakInternalExterns(GlobalState* globalState) {
  auto voidLT = LLVMVoidType();
  auto voidPtrLT = LLVMPointerType(voidLT, 0);
  auto int1LT = LLVMInt1Type();
  auto int8LT = LLVMInt8Type();
  auto int32LT = LLVMInt32Type();
  auto int64LT = LLVMInt64Type();
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {

    globalState->expandLgt = addExtern(globalState->mod, "__expandLgt", voidLT, {});
    globalState->checkLgti = addExtern(globalState->mod, "__checkLgti", voidLT, {int32LT});
    globalState->getNumLiveLgtEntries = addExtern(globalState->mod, "__getNumLiveLgtEntries", int32LT, {});

    globalState->lgtCapacityPtr = LLVMAddGlobal(globalState->mod, LLVMInt32Type(), "__lgt_capacity");
    LLVMSetLinkage(globalState->lgtCapacityPtr, LLVMExternalLinkage);

    globalState->lgtFirstFreeLgtiPtr = LLVMAddGlobal(globalState->mod, LLVMInt32Type(), "__lgt_firstFree");
    LLVMSetLinkage(globalState->lgtFirstFreeLgtiPtr, LLVMExternalLinkage);

    globalState->lgtEntriesArrayPtr = LLVMAddGlobal(globalState->mod, LLVMPointerType(globalState->lgtEntryStructL, 0), "__lgt_entries");
    LLVMSetLinkage(globalState->lgtEntriesArrayPtr, LLVMExternalLinkage);
  } else {
    globalState->expandWrcTable = addExtern(globalState->mod, "__expandWrcTable", voidLT, {});
    globalState->checkWrci = addExtern(globalState->mod, "__checkWrc", voidLT, {int32LT});
    globalState->getNumWrcs = addExtern(globalState->mod, "__getNumWrcs", int32LT, {});

    globalState->wrcCapacityPtr = LLVMAddGlobal(globalState->mod, LLVMInt32Type(), "__wrc_capacity");
    LLVMSetLinkage(globalState->wrcCapacityPtr, LLVMExternalLinkage);

    globalState->wrcFirstFreeWrciPtr = LLVMAddGlobal(globalState->mod, LLVMInt32Type(), "__wrc_firstFree");
    LLVMSetLinkage(globalState->wrcFirstFreeWrciPtr, LLVMExternalLinkage);

    globalState->wrcEntriesArrayPtr = LLVMAddGlobal(globalState->mod, LLVMPointerType(LLVMInt32Type(), 0), "__wrc_entries");
    LLVMSetLinkage(globalState->wrcEntriesArrayPtr, LLVMExternalLinkage);
  }
}

void makeStructWeakRefStruct(GlobalState* globalState, LLVMTypeRef structWeakRefStructL, LLVMTypeRef wrapperStructL) {
  std::vector<LLVMTypeRef> structWeakRefStructMemberTypesL;
  structWeakRefStructMemberTypesL.push_back(globalState->weakRefHeaderStructL);
  structWeakRefStructMemberTypesL.push_back(LLVMPointerType(wrapperStructL, 0));
  LLVMStructSetBody(structWeakRefStructL, structWeakRefStructMemberTypesL.data(), structWeakRefStructMemberTypesL.size(), false);
}

void makeInterfaceWeakRefStruct(GlobalState* globalState, LLVMTypeRef interfaceWeakRefStructL, LLVMTypeRef refStructL) {
  std::vector<LLVMTypeRef> interfaceWeakRefStructMemberTypesL;
  interfaceWeakRefStructMemberTypesL.push_back(globalState->weakRefHeaderStructL);
  interfaceWeakRefStructMemberTypesL.push_back(refStructL);
  LLVMStructSetBody(interfaceWeakRefStructL, interfaceWeakRefStructMemberTypesL.data(), interfaceWeakRefStructMemberTypesL.size(), false);
}

void makeVoidPtrWeakRefStruct(GlobalState* globalState, LLVMTypeRef weakVoidRefStructL) {
  std::vector<LLVMTypeRef> structWeakRefStructMemberTypesL;
  structWeakRefStructMemberTypesL.push_back(globalState->weakRefHeaderStructL);
  structWeakRefStructMemberTypesL.push_back(LLVMPointerType(LLVMVoidType(), 0));
  LLVMStructSetBody(weakVoidRefStructL, structWeakRefStructMemberTypesL.data(), structWeakRefStructMemberTypesL.size(), false);
}

void makeUnknownSizeArrayWeakRefStruct(
    GlobalState* globalState,
    LLVMTypeRef unknownSizeArrayWrapperStruct,
    LLVMTypeRef arrayWeakRefStructL) {
  std::vector<LLVMTypeRef> arrayWeakRefStructMemberTypesL;
  arrayWeakRefStructMemberTypesL.push_back(globalState->weakRefHeaderStructL);
  arrayWeakRefStructMemberTypesL.push_back(LLVMPointerType(unknownSizeArrayWrapperStruct, 0));
  LLVMStructSetBody(arrayWeakRefStructL, arrayWeakRefStructMemberTypesL.data(), arrayWeakRefStructMemberTypesL.size(), false);
}

void makeKnownSizeArrayWeakRefStruct(
    GlobalState* globalState,
    LLVMTypeRef knownSizeArrayWrapperStruct,
    LLVMTypeRef arrayWeakRefStructL) {
  std::vector<LLVMTypeRef> arrayWeakRefStructMemberTypesL;
  arrayWeakRefStructMemberTypesL.push_back(globalState->weakRefHeaderStructL);
  arrayWeakRefStructMemberTypesL.push_back(LLVMPointerType(knownSizeArrayWrapperStruct, 0));
  LLVMStructSetBody(arrayWeakRefStructL, arrayWeakRefStructMemberTypesL.data(), arrayWeakRefStructMemberTypesL.size(), false);
}
