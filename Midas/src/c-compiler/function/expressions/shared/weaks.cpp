#include <region/common/fatweaks/fatweaks.h>
#include "shared.h"
#include "weaks.h"

#include "translatetype.h"
#include "controlblock.h"
#include "branch.h"

constexpr uint32_t WRC_ALIVE_BIT = 0x80000000;
constexpr uint32_t WRC_INITIAL_VALUE = WRC_ALIVE_BIT;

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI = 0;

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;
constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI = 1;

constexpr int LGT_ENTRY_MEMBER_INDEX_FOR_GEN = 0;
constexpr int LGT_ENTRY_MEMBER_INDEX_FOR_NEXT_FREE = 1;


static void buildCheckWrc(
    GlobalState* globalState,
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
  LLVMBuildCall(builder, globalState->checkWrci, &wrciLE, 1, "");
}

static void buildCheckLgti(
    GlobalState* globalState,
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
      // These dont have LGT
      assert(false);
      break;
    default:
      assert(false);
      break;
  }
  LLVMBuildCall(builder, globalState->checkLgti, &lgtiLE, 1, "");
}

LLVMValueRef getWrciFromWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef weakFatPtrLE) {
  assert(globalState->opt->regionOverride != RegionOverride::RESILIENT_V1);
  auto headerLE =
      LLVMBuildExtractValue(builder, weakFatPtrLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "weakRefHeader");
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI, "wrci");
}

LLVMValueRef getHeaderFromWeakRef(
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

static LLVMValueRef getGenerationFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef controlBlockPtr) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V2);
  assert(LLVMTypeOf(controlBlockPtr) == LLVMPointerType(globalState->getControlBlockStruct(referendM), 0));

  auto genPtrLE =
      LLVMBuildStructGEP(
          builder,
          controlBlockPtr,
          globalState->getControlBlockLayout(referendM)->getMemberIndex(ControlBlockMember::GENERATION),
          "genPtr");
  return LLVMBuildLoad(builder, genPtrLE, "gen");
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
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V2);
  auto headerLE =
      LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "weakRefHeader");
  assert(LLVMTypeOf(headerLE) == globalState->weakRefHeaderStructL);
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

static LLVMValueRef getActualGenFromLGT(
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
    LLVMValueRef targetGenLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V2);
  auto headerLE = LLVMGetUndef(globalState->weakRefHeaderStructL);
  headerLE = LLVMBuildInsertValue(builder, headerLE, targetGenLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "header");
  return headerLE;
}

static LLVMValueRef makeLgtiHeader(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef lgtiLE,
    LLVMValueRef targetGenLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);
  auto headerLE = LLVMGetUndef(globalState->weakRefHeaderStructL);
  headerLE = LLVMBuildInsertValue(builder, headerLE, lgtiLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI, "headerWithLgti");
  headerLE = LLVMBuildInsertValue(builder, headerLE, targetGenLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "header");
  return headerLE;
}

LLVMValueRef makeWrciHeader(
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

void aliasWeakRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V1:
      // Do nothing!
      break;
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::FAST: {
      auto weakFatPtrLE =
          checkValidReference(FL(), globalState, functionState, builder, weakRefMT, weakRef);
      auto wrciLE = getWrciFromWeakRef(globalState, builder, weakFatPtrLE);
      if (globalState->opt->census) {
        buildCheckWrc(globalState, builder, wrciLE);
      }

      auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
      adjustCounter(builder, ptrToWrcLE, 1);
      break;
    }
    default:
      assert(false);
      break;
  }
}

void discardWeakRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::FAST: {
      auto weakFatPtrLE =
          checkValidReference(FL(), globalState, functionState, builder, weakRefMT, weakRef);
      auto wrciLE = getWrciFromWeakRef(globalState, builder, weakFatPtrLE);
      if (globalState->opt->census) {
        buildCheckWrc(globalState, builder, wrciLE);
      }

      auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
      auto wrcLE = adjustCounter(builder, ptrToWrcLE, -1);

      maybeReleaseWrc(globalState, functionState, builder, wrciLE, ptrToWrcLE, wrcLE);
      break;
    }
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      // Do nothing!
      break;
    default:
      assert(false);
      break;
  }
}

LLVMValueRef lockWrciFatPtr(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef weakFatPtrLE) {
  auto isAliveLE = getIsAliveFromWeakFatPtr(globalState, functionState, builder, refM, weakFatPtrLE);
  buildIf(
      functionState, builder, isZeroLE(builder, isAliveLE),
      [from, globalState, functionState, weakFatPtrLE](LLVMBuilderRef thenBuilder) {
        buildPrintAreaAndFileAndLine(globalState, thenBuilder, from);
        buildPrint(globalState, thenBuilder, "Tried dereferencing dangling reference! ");
        assert(globalState->opt->regionOverride != RegionOverride::RESILIENT_V1);
        auto wrciLE = getWrciFromWeakRef(globalState, thenBuilder, weakFatPtrLE);
        buildPrint(globalState, thenBuilder, "Wrci: ");
        buildPrint(globalState, thenBuilder, wrciLE);
        buildPrint(globalState, thenBuilder, " ");
        buildPrint(globalState, thenBuilder, "Exiting!\n");
        auto exitCodeIntLE = LLVMConstInt(LLVMInt8Type(), 255, false);
        LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");
      });
  return FatWeaks().getInnerRefFromWeakRef(globalState, functionState, builder, refM, weakFatPtrLE);
}

LLVMValueRef lockLgtiFatPtr(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef weakRefLE) {
  auto fatPtrLE = weakRefLE;
  auto isAliveLE = getIsAliveFromWeakFatPtr(globalState, functionState, builder, refM, fatPtrLE);
  buildIf(
      functionState, builder, isZeroLE(builder, isAliveLE),
      [from, globalState, functionState, fatPtrLE](LLVMBuilderRef thenBuilder) {
        buildPrintAreaAndFileAndLine(globalState, thenBuilder, from);
        buildPrint(globalState, thenBuilder, "Tried dereferencing dangling reference! ");

        switch (globalState->opt->regionOverride) {
          case RegionOverride::RESILIENT_V1: {
            auto lgtiLE = getLgtiFromWeakRef(globalState, thenBuilder, fatPtrLE);
            buildPrint(globalState, thenBuilder, "lgti ");
            buildPrint(globalState, thenBuilder, lgtiLE);
            buildPrint(globalState, thenBuilder, " ");
            auto targetGenLE = getTargetGenFromWeakRef(globalState, thenBuilder, fatPtrLE);
            buildPrint(globalState, thenBuilder, "targetGen ");
            buildPrint(globalState, thenBuilder, targetGenLE);
            buildPrint(globalState, thenBuilder, " ");
            auto actualGenLE = getActualGenFromLGT(globalState, functionState, thenBuilder,
                lgtiLE);
            buildPrint(globalState, thenBuilder, "actualGen ");
            buildPrint(globalState, thenBuilder, actualGenLE);
            buildPrint(globalState, thenBuilder, " ");
            break;
          }
          case RegionOverride::ASSIST:
          case RegionOverride::NAIVE_RC:
          case RegionOverride::RESILIENT_V0:
          case RegionOverride::FAST: {
            auto wrciLE = getWrciFromWeakRef(globalState, thenBuilder, fatPtrLE);
            buildPrint(globalState, thenBuilder, "Wrci: ");
            buildPrint(globalState, thenBuilder, wrciLE);
            buildPrint(globalState, thenBuilder, " ");
            break;
          }
          default:
            assert(false);
            break;
        }
        buildPrint(globalState, thenBuilder, "Exiting!\n");
        auto exitCodeIntLE = LLVMConstInt(LLVMInt8Type(), 255, false);
        LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");
      });
  return FatWeaks().getInnerRefFromWeakRef(globalState, functionState, builder, refM, fatPtrLE);
}

LLVMValueRef lockGenFatPtr(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef weakRefLE) {
  auto fatPtrLE = weakRefLE;
  auto isAliveLE = getIsAliveFromWeakFatPtr(globalState, functionState, builder, refM, fatPtrLE);
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
          auto actualGenLE = getActualGenFromLGT(globalState, functionState, thenBuilder,
              lgtiLE);
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
  return FatWeaks().getInnerRefFromWeakRef(globalState, functionState, builder, refM, fatPtrLE);
}

// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
WrapperPtrLE lockWeakRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref weakRefLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::FAST:
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC: {
      switch (refM->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE:
        case Ownership::BORROW:
          assert(false);
          break;
        case Ownership::WEAK: {
          auto weakFatPtrLE =
              checkValidReference(FL(), globalState, functionState, builder, refM, weakRefLE);
          return WrapperPtrLE(refM,
              lockWrciFatPtr(from, globalState, functionState, builder, refM, weakFatPtrLE));
        }
        default:
          assert(false);
          break;
      }
      break;
    }
    case RegionOverride::RESILIENT_V0: {
      switch (refM->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE:
          assert(false);
          break;
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto weakFatPtrLE =
              checkValidReference(FL(), globalState, functionState, builder, refM, weakRefLE);
          return WrapperPtrLE(refM,
              lockWrciFatPtr(from, globalState, functionState, builder, refM, weakFatPtrLE));
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
          assert(false);
          break;
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto weakFatPtrLE =
              checkValidReference(FL(), globalState, functionState, builder, refM, weakRefLE);
          return WrapperPtrLE(refM, lockLgtiFatPtr(from, globalState, functionState, builder, refM, weakFatPtrLE));
        }
        default:
          assert(false);
          break;
      }
    }
    case RegionOverride::RESILIENT_V2: {
      switch (refM->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE: {
          auto objPtrLE = weakRefLE;
          auto weakFatPtrLE =
              checkValidReference(FL(), globalState, functionState, builder, refM, weakRefLE);
          return WrapperPtrLE(refM, weakFatPtrLE);
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto weakFatPtrLE =
              checkValidReference(FL(), globalState, functionState, builder, refM, weakRefLE);
          return WrapperPtrLE(refM, lockGenFatPtr(from, globalState, functionState, builder, refM, weakFatPtrLE));
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
  assert(false);
}

void noteWeakableDestroyed(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* concreteRefM,
    LLVMValueRef controlBlockPtrLE) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::FAST: {
      auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, concreteRefM,
          controlBlockPtrLE);

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
      break;
    }
    case RegionOverride::RESILIENT_V1: {
      auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, concreteRefM,
          controlBlockPtrLE);
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
      break;
    }
    case RegionOverride::RESILIENT_V2: {
      // No need to do anything!
      break;
    }
    default:
      assert(false);
      break;
  }
}


LLVMValueRef getIsAliveFromWeakFatPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    LLVMValueRef weakFatPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    // Get target generation from the ref
    auto targetGenLE = getTargetGenFromWeakRef(globalState, builder, weakFatPtrLE);

    // Get actual generation from the table
    auto lgtiLE = getLgtiFromWeakRef(globalState, builder, weakFatPtrLE);
    if (globalState->opt->census) {
      buildCheckLgti(globalState, builder, lgtiLE);
    }
    auto ptrToActualGenLE = getLGTEntryGenPtr(globalState, functionState, builder, lgtiLE);
    auto actualGenLE = LLVMBuildLoad(builder, ptrToActualGenLE, "gen");

    return LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        actualGenLE,
        targetGenLE,
        "genLive");
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
    // Get target generation from the ref
    auto targetGenLE = getTargetGenFromWeakRef(globalState, builder, weakFatPtrLE);

    // Get actual generation from the table
    auto innerRefLE =
        FatWeaks().getInnerRefFromWeakRefWithoutCheck(globalState, functionState, builder, weakRefM, weakFatPtrLE);
    auto controlBlockPtrLE = getControlBlockPtr(globalState, functionState, builder, innerRefLE, weakRefM);
    auto actualGenLE = getGenerationFromControlBlockPtr(globalState, builder, weakRefM->referend, controlBlockPtrLE);

    return LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        actualGenLE,
        targetGenLE,
        "genLive");
  } else if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
      globalState->opt->regionOverride == RegionOverride::NAIVE_RC ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V0 ||
      globalState->opt->regionOverride == RegionOverride::FAST) {
    auto wrciLE = getWrciFromWeakRef(globalState, builder, weakFatPtrLE);
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
  } else assert(false);
}

Ref getIsAliveFromWeakRef(
    GlobalState* globalState,
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
      checkValidReference(FL(), globalState, functionState, builder, weakRefM, weakRef);
  auto isAliveLE = getIsAliveFromWeakFatPtr(globalState, functionState, builder, weakRefM, weakFatPtrLE);
  return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, isAliveLE);
}

LLVMValueRef getNewWrci(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder) {
  assert(
      globalState->opt->regionOverride == RegionOverride::ASSIST ||
      globalState->opt->regionOverride == RegionOverride::NAIVE_RC ||
      globalState->opt->regionOverride == RegionOverride::FAST ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V0);

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

LLVMValueRef getNewLgti(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);

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
}

LLVMValueRef fillWeakableControlBlock(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef controlBlockLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    auto geniLE = getNewLgti(globalState, functionState, builder);
    return LLVMBuildInsertValue(
        builder,
        controlBlockLE,
        geniLE,
        globalState->getControlBlockLayout(referendM)->getMemberIndex(ControlBlockMember::LGTI),
        "controlBlockWithLgti");
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
    // The generation was already incremented when we freed it (or malloc'd it for the first time),
    // so nothing to do here!
    return controlBlockLE;
  } else if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
      globalState->opt->regionOverride == RegionOverride::FAST ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V0 ||
      globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
    auto wrciLE = getNewWrci(globalState, functionState, builder);
    return LLVMBuildInsertValue(
        builder,
        controlBlockLE,
        wrciLE,
        globalState->getControlBlockLayout(referendM)->getMemberIndex(ControlBlockMember::WRCI),
        "weakableControlBlockWithWrci");
  } else assert(false);
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
    Reference* weakInterfaceRefMT,
    LLVMValueRef weakInterfaceFatPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
    auto headerLE = getHeaderFromWeakRef(builder, weakInterfaceFatPtrLE);

    auto interfaceFatPtrLE =
        InterfaceFatPtrLE(
            weakInterfaceRefMT, // It's still conceptually weak even though its not in a weak pointer.
            FatWeaks().getInnerRefFromWeakRef(
                globalState,
                functionState,
                builder,
                weakInterfaceRefMT,
                weakInterfaceFatPtrLE));
    auto controlBlockPtrLE = getControlBlockPtr(builder, interfaceFatPtrLE);

    // Now, reassemble a weak void* ref to the struct.
    auto weakVoidStructRefLE =
        assembleVoidStructWeakRef(globalState, builder, controlBlockPtrLE, headerLE);

    return weakVoidStructRefLE;
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0 ||
      globalState->opt->regionOverride == RegionOverride::FAST ||
      globalState->opt->regionOverride == RegionOverride::ASSIST ||
      globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
    // Disassemble the weak interface ref.
    auto wrciLE = getWrciFromWeakRef(globalState, builder, weakInterfaceFatPtrLE);
    auto interfaceRefLE =
        InterfaceFatPtrLE(
            weakInterfaceRefMT, // It's still conceptually weak even though its not in a weak pointer.
            FatWeaks().getInnerRefFromWeakRef(
                globalState,
                functionState,
                builder,
                weakInterfaceRefMT,
                weakInterfaceFatPtrLE));
    auto controlBlockPtrLE = getControlBlockPtr(builder, interfaceRefLE);

    auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

    // Now, reassemble a weak void* ref to the struct.
    auto weakVoidStructRefLE =
        assembleVoidStructWeakRef(globalState, builder, controlBlockPtrLE, headerLE);

    return weakVoidStructRefLE;
  } else {
    assert(false);
  }
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

void buildCheckWeakRef(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef) {
  auto innerLE = FatWeaks().getInnerRefFromWeakRefWithoutCheck(globalState, functionState, builder, weakRefM, weakRef.refLE);

  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    auto lgtiLE = getLgtiFromWeakRef(globalState, builder, innerLE);
    // WARNING: This check has false negatives, it doesnt catch much.
    buildCheckLgti(globalState, builder, lgtiLE);
    // We check that the generation is <= to what's in the actual object.
    auto actualGen = getActualGenFromLGT(globalState, functionState, builder, lgtiLE);
    auto targetGen = getTargetGenFromWeakRef(globalState, builder, innerLE);
    buildCheckGen(globalState, functionState, builder, targetGen, actualGen);
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {

    auto controlBlockPtrLE = getControlBlockPtr(globalState, functionState, builder, innerLE, weakRefM);
    // We check that the generation is <= to what's in the actual object.
    auto actualGen = getGenerationFromControlBlockPtr(globalState, builder, weakRefM->referend, controlBlockPtrLE);
    auto targetGen = getTargetGenFromWeakRef(globalState, builder, innerLE);
    buildCheckGen(globalState, functionState, builder, targetGen, actualGen);
  } else if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
      globalState->opt->regionOverride == RegionOverride::NAIVE_RC ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V0 ||
      globalState->opt->regionOverride == RegionOverride::FAST) {
    // WARNING: This check has false positives.
    auto wrciLE = getWrciFromWeakRef(globalState, builder, innerLE);
    buildCheckWrc(globalState, builder, wrciLE);
  }

  // This will also run for objects which have since died, which is fine.
  if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(weakRefM->referend)) {
    auto interfaceFatPtrLE = InterfaceFatPtrLE(weakRefM, innerLE);
    auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
  }
}

// Makes a non-weak interface ref into a weak interface ref
LLVMValueRef assembleInterfaceWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    InterfaceReferend* interfaceReferendM,
    InterfaceFatPtrLE sourceInterfaceFatPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    assert(sourceType->ownership == Ownership::OWN || sourceType->ownership == Ownership::SHARE);
    // curious, if its a borrow, do we just return sourceRefLE?

    auto controlBlockPtrLE = getControlBlockPtr(builder, sourceInterfaceFatPtrLE);
    auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, sourceType,
        controlBlockPtrLE);
    auto currentGenLE = getActualGenFromLGT(globalState, functionState, builder, lgtiLE);
    auto headerLE = makeLgtiHeader(globalState, builder, lgtiLE, currentGenLE);

    auto weakRefLE = LLVMGetUndef(
        globalState->getInterfaceWeakRefStruct(interfaceReferendM->fullName));
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE =
        LLVMBuildInsertValue(
            builder, weakRefLE, sourceInterfaceFatPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR, "");

    return weakRefLE;
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
    assert(sourceType->ownership == Ownership::OWN || sourceType->ownership == Ownership::SHARE);
    // curious, if its a borrow, do we just return sourceRefLE?

    LLVMValueRef genLE = nullptr;
    if (sourceType->ownership == Ownership::OWN) {
      auto controlBlockPtrLE = getControlBlockPtr(builder, sourceInterfaceFatPtrLE);
      genLE = getGenerationFromControlBlockPtr(globalState, builder, sourceType->referend, controlBlockPtrLE);
    } else if (sourceType->ownership == Ownership::BORROW) {
      assert(false); // impl
    } else {
      assert(false);
    }
    auto headerLE = makeGenHeader(globalState, builder, genLE);

    auto weakRefLE = LLVMGetUndef(
        globalState->getInterfaceWeakRefStruct(interfaceReferendM->fullName));
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, sourceInterfaceFatPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
        "");

    return weakRefLE;
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
    if (sourceType->ownership == Ownership::BORROW) {
      assert(false); // curiosity, wouldnt we just return sourceRefLE?
    }
    assert(sourceType->ownership == Ownership::SHARE || sourceType->ownership == Ownership::OWN);

    auto controlBlockPtrLE = getControlBlockPtr(builder, sourceInterfaceFatPtrLE);
    auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, sourceType,
        controlBlockPtrLE);
    auto headerLE = LLVMGetUndef(globalState->weakRefHeaderStructL);
    headerLE = LLVMBuildInsertValue(builder, headerLE, wrciLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI, "header");

    auto weakRefLE = LLVMGetUndef(
        globalState->getInterfaceWeakRefStruct(interfaceReferendM->fullName));
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, sourceInterfaceFatPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
        "");

    return weakRefLE;
  } else if (globalState->opt->regionOverride == RegionOverride::NAIVE_RC ||
      globalState->opt->regionOverride == RegionOverride::ASSIST ||
      globalState->opt->regionOverride == RegionOverride::FAST) {
    auto controlBlockPtrLE = getControlBlockPtr(builder, sourceInterfaceFatPtrLE);
    auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, sourceType,
        controlBlockPtrLE);
    auto headerLE = LLVMGetUndef(globalState->weakRefHeaderStructL);
    headerLE = LLVMBuildInsertValue(builder, headerLE, wrciLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI, "header");

    auto weakRefLE = LLVMGetUndef(
        globalState->getInterfaceWeakRefStruct(interfaceReferendM->fullName));
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, sourceInterfaceFatPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
        "");

    return weakRefLE;
  } else {
    assert(false);
  }
}

LLVMValueRef assembleStructWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    Reference* resultTypeM,
    StructReferend* structReferendM,
    WrapperPtrLE objPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
    assert(structTypeM->ownership == Ownership::OWN || structTypeM->ownership == Ownership::SHARE);
    // curious, if its a borrow, do we just return sourceRefLE?

    auto controlBlockPtrLE = getConcreteControlBlockPtr(builder, objPtrLE);
    auto currentGenLE = getGenerationFromControlBlockPtr(globalState, builder, structTypeM->referend, controlBlockPtrLE);
    auto headerLE = makeGenHeader(globalState, builder, currentGenLE);
    auto weakRefLE = LLVMGetUndef(globalState->getStructWeakRefStruct(structReferendM->fullName));
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,"");
    return weakRefLE;
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    assert(structTypeM->ownership == Ownership::OWN || structTypeM->ownership == Ownership::SHARE);
    // curious, if its a borrow, do we just return sourceRefLE?

    auto controlBlockPtrLE = getConcreteControlBlockPtr(builder, objPtrLE);
    auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, structTypeM,
        controlBlockPtrLE);
    buildFlare(FL(), globalState, functionState, builder, lgtiLE);
    auto currentGenLE = getActualGenFromLGT(globalState, functionState, builder, lgtiLE);
    auto headerLE = makeLgtiHeader(globalState, builder, lgtiLE, currentGenLE);
    auto weakRefLE = LLVMGetUndef(globalState->getStructWeakRefStruct(structReferendM->fullName));
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, objPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,"");


    return weakRefLE;
  } else if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
      globalState->opt->regionOverride == RegionOverride::NAIVE_RC ||
      globalState->opt->regionOverride == RegionOverride::FAST ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {

    if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
      assert(structTypeM->ownership == Ownership::OWN || structTypeM->ownership == Ownership::SHARE);
    } else if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
        globalState->opt->regionOverride == RegionOverride::NAIVE_RC ||
        globalState->opt->regionOverride == RegionOverride::FAST) {
      assert(structTypeM->ownership == Ownership::OWN || structTypeM->ownership == Ownership::SHARE || structTypeM->ownership == Ownership::BORROW);
    } else assert(false);

    auto controlBlockPtrLE = getConcreteControlBlockPtr(builder, objPtrLE);
    auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);
    auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

    auto weakRefLE = LLVMGetUndef(globalState->getStructWeakRefStruct(structReferendM->fullName));
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, objPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,"");

    return weakRefLE;
  } else assert(false);
}

LLVMValueRef assembleKnownSizeArrayWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    KnownSizeArrayT* knownSizeArrayMT,
    WrapperPtrLE objPtrLE) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      auto controlBlockPtrLE = getConcreteControlBlockPtr(builder, objPtrLE);
      auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);
      auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

      auto weakRefLE = LLVMGetUndef(
          globalState->getKnownSizeArrayWeakRefStruct(knownSizeArrayMT->name));
      weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
      weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
          "");

      return weakRefLE;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
        // impl
        assert(false);
      } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
        // impl
        assert(false);
      } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
        auto controlBlockPtrLE = getConcreteControlBlockPtr(builder, objPtrLE);
        auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);
        auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

        auto weakRefLE = LLVMGetUndef(
            globalState->getKnownSizeArrayWeakRefStruct(knownSizeArrayMT->name));
        weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
        weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objPtrLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
            "");

        return weakRefLE;
      } else {
        assert(false);
      }
      break;
    }
    default:
      assert(false);
  }
}

LLVMValueRef assembleUnknownSizeArrayWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    UnknownSizeArrayT* unknownSizeArrayMT,
    WrapperPtrLE sourceRefLE) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    auto controlBlockPtrLE = getConcreteControlBlockPtr(builder, sourceRefLE);
    auto lgtiLE = getLgtiFromControlBlockPtr(globalState, builder, sourceType, controlBlockPtrLE);
    auto targetGenLE = getActualGenFromLGT(globalState, functionState, builder, lgtiLE);
    auto headerLE = makeLgtiHeader(globalState, builder, lgtiLE, targetGenLE);

    auto weakRefLE = LLVMGetUndef(
        globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name));
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, sourceRefLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,"");

    return weakRefLE;
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {

    LLVMValueRef genLE = nullptr;
    if (sourceType->ownership == Ownership::OWN) {
      auto controlBlockPtrLE = getConcreteControlBlockPtr(builder, sourceRefLE);
      genLE = getGenerationFromControlBlockPtr(globalState, builder, sourceType->referend, controlBlockPtrLE);
    } else if (sourceType->ownership == Ownership::BORROW) {
      assert(false); // impl
    } else {
      assert(false);
    }
    auto headerLE = makeGenHeader(globalState, builder, genLE);

    auto weakRefLE = LLVMGetUndef(
        globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name));
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE =
        LLVMBuildInsertValue(builder, weakRefLE, sourceRefLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,"");

    return weakRefLE;
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0 ||
      globalState->opt->regionOverride == RegionOverride::NAIVE_RC ||
      globalState->opt->regionOverride == RegionOverride::ASSIST ||
      globalState->opt->regionOverride == RegionOverride::FAST) {
    auto controlBlockPtrLE = getConcreteControlBlockPtr(builder, sourceRefLE);
    auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, sourceType, controlBlockPtrLE);
    auto headerLE = makeWrciHeader(globalState, builder, wrciLE);

    auto weakRefLE = LLVMGetUndef(
        globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name));
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, headerLE, WEAK_REF_MEMBER_INDEX_FOR_HEADER, "");
    weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, sourceRefLE.refLE, WEAK_REF_MEMBER_INDEX_FOR_OBJPTR,
        "");

    return weakRefLE;
  } else {
    assert(false);
  }
}

// Used in interface calling, when we dont know what the underlying struct type is yet.
LLVMValueRef assembleVoidStructWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtrLE,
    LLVMValueRef headerLE) {
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


LLVMTypeRef makeResilientV1GenRefStruct(GlobalState* globalState) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V1);
  auto genRefStructL = LLVMStructCreateNamed(globalState->context, "__GenRef");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32Type());

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32Type());

  LLVMStructSetBody(genRefStructL, memberTypesL.data(), memberTypesL.size(), false);

  assert(
      LLVMABISizeOfType(globalState->dataLayout, genRefStructL) ==
      LLVMABISizeOfType(globalState->dataLayout, LLVMInt64Type()));

  return genRefStructL;
}

LLVMTypeRef makeResilientV2GenRefStruct(GlobalState* globalState) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V2);
  auto genRefStructL = LLVMStructCreateNamed(globalState->context, "__GenRef");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32Type());

  LLVMStructSetBody(genRefStructL, memberTypesL.data(), memberTypesL.size(), false);

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

    globalState->weakRefHeaderStructL = makeResilientV1GenRefStruct(globalState);
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
    globalState->weakRefHeaderStructL = makeResilientV2GenRefStruct(globalState);
  } else if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
      globalState->opt->regionOverride == RegionOverride::FAST ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V0 ||
      globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
    globalState->weakRefHeaderStructL = makeWrciStruct(globalState);
  } else {
    assert(false);
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
