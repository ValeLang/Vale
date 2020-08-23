#include "shared.h"
#include "weaks.h"

#include "translatetype.h"
#include "controlblock.h"
#include "branch.h"

constexpr uint64_t WRC_ALIVE_BIT = 0x8000000000000000;
constexpr uint64_t WRC_INITIAL_VALUE = WRC_ALIVE_BIT;

void buildCheckWrc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE) {
  LLVMBuildCall(builder, globalState->checkWrc, &wrciLE, 1, "");
}

LLVMValueRef getWrciFromWeakRef(
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  return LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_RCINDEX_MEMBER_INDEX, "wrci");
}

LLVMValueRef getWrciFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef controlBlockPtr) {

  if (refM->ownership == Ownership::SHARE) {
    // Shares never have weak refs
    assert(false);
  } else {
    auto wrciPtrLE =
        LLVMBuildStructGEP(
            builder,
            controlBlockPtr,
            globalState->mutControlBlockWrciMemberIndex,
            "wrciPtr");
    return LLVMBuildLoad(builder, wrciPtrLE, "wrci");
  }
}

LLVMValueRef getInnerRefFromWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    LLVMValueRef weakRefLE) {
  checkValidReference(FL(), globalState, functionState, builder, weakRefM, weakRefLE);
  auto innerRefLE = LLVMBuildExtractValue(builder, weakRefLE, WEAK_REF_OBJPTR_MEMBER_INDEX, "");
  // We dont check that its valid because if it's a weak ref, it might *not* be pointing at
  // a valid reference.
  return innerRefLE;
}

LLVMValueRef getWrcPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE) {
  auto wrcEntriesPtrLE =
      LLVMBuildLoad(builder, globalState->wrcEntriesArrayPtr, "wrcEntriesArrayPtr");
  auto ptrToWrcLE =
      LLVMBuildGEP(builder, wrcEntriesPtrLE, &wrciLE, 1, "ptrToWrc");
  return ptrToWrcLE;
}

void maybeReleaseWrc(
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
    LLVMValueRef exprLE) {
  buildFlare(from, globalState, functionState, builder, "Incrementing");
  auto wrciLE = getWrciFromWeakRef(builder, exprLE);
  if (globalState->opt->census) {
    buildCheckWrc(globalState, builder, wrciLE);
  }

  auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
  adjustCounter(builder, ptrToWrcLE, 1);
}

void discardWeakRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE) {
  buildFlare(from, globalState, functionState, builder, "Decrementing");
  auto wrciLE = getWrciFromWeakRef(builder, exprLE);
  if (globalState->opt->census) {
    buildCheckWrc(globalState, builder, wrciLE);
  }

  auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
  auto wrcLE = adjustCounter(builder, ptrToWrcLE, -1);

  maybeReleaseWrc(globalState, functionState, builder, wrciLE, ptrToWrcLE, wrcLE);
}

// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
LLVMValueRef derefMaybeWeakRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef weakRefLE) {
  switch (refM->ownership) {
    case Ownership::OWN:
    case Ownership::BORROW:
    case Ownership::SHARE: {
      auto objPtrLE = weakRefLE;
      return objPtrLE;
    }
    case Ownership::WEAK: {
      auto fatPtrLE = weakRefLE;
      auto wrciLE = getWrciFromWeakRef(builder, fatPtrLE);
      auto isAliveLE = getIsAliveFromWeakRef(globalState, builder, fatPtrLE);
      buildIf(
          functionState, builder, isZeroLE(builder, isAliveLE),
          [from, globalState, functionState, wrciLE](LLVMBuilderRef thenBuilder) {
            buildPrintAreaAndFileAndLine(globalState, thenBuilder, from);
            buildPrint(globalState, thenBuilder, "Tried dereferencing dangling reference, wrci: ");
            buildPrint(globalState, thenBuilder, wrciLE);
            buildPrint(globalState, thenBuilder, ", exiting!\n");
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

void noteWeakableDestroyed(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* concreteRefM,
    LLVMValueRef concreteRefLE) {
  auto controlBlockPtrLE = getControlBlockPtr(builder, concreteRefLE, concreteRefM);
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, concreteRefM, controlBlockPtrLE);

//  LLVMBuildCall(builder, globalState->noteWeakableDestroyed, &wrciLE, 1, "");

  auto ptrToWrcLE = getWrcPtr(globalState, builder, wrciLE);
  auto prevWrcLE = LLVMBuildLoad(builder, ptrToWrcLE, "wrc");

  auto wrcLE =
      LLVMBuildAnd(
          builder,
          prevWrcLE,
          LLVMConstInt(LLVMInt64Type(), ~WRC_ALIVE_BIT, true),
          "");

  // Equivalent:
  // __wrc_entries[wrcIndex] = __wrc_entries[wrcIndex] & ~WRC_LIVE_BIT;
  // *wrcPtr = *wrcPtr & ~WRC_LIVE_BIT;
  LLVMBuildStore(builder, wrcLE, ptrToWrcLE);

  maybeReleaseWrc(globalState, functionState, builder, wrciLE, ptrToWrcLE, wrcLE);
}


LLVMValueRef getIsAliveFromWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  auto wrciLE = getWrciFromWeakRef(builder, weakRefLE);
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
          LLVMConstInt(LLVMInt64Type(), WRC_ALIVE_BIT, false),
          "wrcLiveBitOrZero"),
      constI64LE(0),
      "wrcLive");
}

LLVMValueRef noteWeakableCreated(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder) {
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
      LLVMConstInt(LLVMInt64Type(), WRC_INITIAL_VALUE, false),
      wrcPtrLE);

  return resultWrciLE;
}

LLVMValueRef fillWeakableControlBlock(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockLE) {
  auto wrciLE = noteWeakableCreated(globalState, functionState, builder);
  return LLVMBuildInsertValue(
      builder,
      controlBlockLE,
      wrciLE,
      globalState->mutControlBlockWrciMemberIndex,
      "strControlBlockWithWrci");
}

LLVMValueRef getControlBlockPtrFromInterfaceWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    LLVMValueRef virtualArgLE) {
  auto interfaceRefLE =
      getInnerRefFromWeakRef(
          globalState,
          functionState,
          builder,
          virtualParamMT,
          virtualArgLE);
  return getControlBlockPtrFromInterfaceRef(builder, interfaceRefLE);
}



LLVMValueRef weakInterfaceRefToWeakStructRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef exprLE) {
  // Disassemble the weak interface ref.
  auto wrciLE = getWrciFromWeakRef(builder, exprLE);
  auto interfaceRefLE =
      getInnerRefFromWeakRef(
          globalState,
          functionState,
          builder,
          refM,
          exprLE);
  auto controlBlockPtrLE = getControlBlockPtrFromInterfaceRef(builder, interfaceRefLE);

  // Now, reassemble a weak void* ref to the struct.
  auto weakVoidStructRefLE =
      assembleVoidStructWeakRef(globalState, builder, controlBlockPtrLE, wrciLE);

  return weakVoidStructRefLE;
}

void buildCheckWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE) {
  // WARNING: This check has false positives.
  auto wrciLE = getWrciFromWeakRef(builder, weakRefLE);
  buildCheckWrc(globalState, builder, wrciLE);
}


LLVMValueRef assembleInterfaceWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* interfaceTypeM,
    InterfaceReferend* interfaceReferendM,
    LLVMValueRef fatPtrLE) {
  auto controlBlockPtrLE = getControlBlockPtr(builder, fatPtrLE, interfaceTypeM);
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, interfaceTypeM, controlBlockPtrLE);

  auto weakRefLE = LLVMGetUndef(globalState->getInterfaceWeakRefStruct(interfaceReferendM->fullName));
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, wrciLE, WEAK_REF_RCINDEX_MEMBER_INDEX, "");
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, fatPtrLE, WEAK_REF_OBJPTR_MEMBER_INDEX, "");

  return weakRefLE;
}

LLVMValueRef assembleStructWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    StructReferend* structReferendM,
    LLVMValueRef objPtrLE) {
  auto controlBlockPtrLE = getControlBlockPtr(builder, objPtrLE, structTypeM);
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);

  auto weakRefLE = LLVMGetUndef(globalState->getStructWeakRefStruct(structReferendM->fullName));
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, wrciLE, WEAK_REF_RCINDEX_MEMBER_INDEX, "");
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objPtrLE, WEAK_REF_OBJPTR_MEMBER_INDEX, "");

  return weakRefLE;
}

LLVMValueRef assembleKnownSizeArrayWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    KnownSizeArrayT* knownSizeArrayMT,
    LLVMValueRef objPtrLE) {
  auto controlBlockPtrLE = getControlBlockPtr(builder, objPtrLE, structTypeM);
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);

  auto weakRefLE = LLVMGetUndef(globalState->getKnownSizeArrayWeakRefStruct(knownSizeArrayMT->name));
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, wrciLE, WEAK_REF_RCINDEX_MEMBER_INDEX, "");
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objPtrLE, WEAK_REF_OBJPTR_MEMBER_INDEX, "");

  return weakRefLE;
}

LLVMValueRef assembleUnknownSizeArrayWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    UnknownSizeArrayT* unknownSizeArrayMT,
    LLVMValueRef objPtrLE) {
  auto controlBlockPtrLE = getControlBlockPtr(builder, objPtrLE, structTypeM);
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);

  auto weakRefLE = LLVMGetUndef(globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name));
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, wrciLE, WEAK_REF_RCINDEX_MEMBER_INDEX, "");
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objPtrLE, WEAK_REF_OBJPTR_MEMBER_INDEX, "");

  return weakRefLE;
}

// Used in interface calling, when we dont know what the underlying struct type is yet.
LLVMValueRef assembleVoidStructWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtrLE,
    LLVMValueRef wrciLE) {
//  auto controlBlockPtrLE = getControlBlockPtr(builder, objPtrLE, structTypeM);
//  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, structTypeM, controlBlockPtrLE);

  auto objVoidPtrLE =
      LLVMBuildPointerCast(
          builder,
          controlBlockPtrLE,
          LLVMPointerType(LLVMVoidType(), 0),
          "objAsVoidPtr");

  auto weakRefLE = LLVMGetUndef(globalState->weakVoidRefStructL);
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, wrciLE, WEAK_REF_RCINDEX_MEMBER_INDEX, "");
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objVoidPtrLE, WEAK_REF_OBJPTR_MEMBER_INDEX, "");

//  adjustWeakRc(globalState, builder, weakRefLE, 1);

  return weakRefLE;
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

  auto interfaceWeakRefLE = LLVMGetUndef(interfaceRefLT);
  interfaceWeakRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceWeakRefLE,
          getWrciFromWeakRef(builder, sourceRefLE),
          WEAK_REF_RCINDEX_MEMBER_INDEX,
          "interfaceRefWithOnlyObj");
  interfaceWeakRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceWeakRefLE,
          makeInterfaceRefStruct(
              globalState, builder, sourceStructReferendM, targetInterfaceReferendM,
              controlBlockPtr),
          WEAK_REF_OBJPTR_MEMBER_INDEX,
          "interfaceRef");
  checkValidReference(
      FL(), globalState, functionState, builder, targetInterfaceTypeM, interfaceWeakRefLE);
  return interfaceWeakRefLE;
}