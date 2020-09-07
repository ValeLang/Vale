#include "utils/fileio.h"
#include "heap.h"
#include "members.h"
#include "shared.h"
#include "controlblock.h"
#include "string.h"
#include "weaks.h"

LLVMValueRef callMalloc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef sizeLE) {
  if (globalState->opt->genHeap) {
    return LLVMBuildCall(builder, globalState->genMalloc, &sizeLE, 1, "");
  } else {
    return LLVMBuildCall(builder, globalState->malloc, &sizeLE, 1, "");
  }
}

LLVMValueRef callFree(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtrLE) {
  if (globalState->opt->genHeap) {
    auto concreteAsVoidPtrLE =
        LLVMBuildBitCast(
            builder,
            controlBlockPtrLE,
            LLVMPointerType(LLVMVoidType(), 0),
            "concreteVoidPtrForFree");
    return LLVMBuildCall(builder, globalState->genFree, &concreteAsVoidPtrLE, 1, "");
  } else {
    auto concreteAsCharPtrLE =
        LLVMBuildBitCast(
            builder,
            controlBlockPtrLE,
            LLVMPointerType(LLVMInt8Type(), 0),
            "concreteCharPtrForFree");
    return LLVMBuildCall(builder, globalState->free, &concreteAsCharPtrLE, 1, "");
  }
}

LLVMValueRef mallocKnownSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    LLVMTypeRef referendLT) {
  if (globalState->opt->census) {
    adjustCounter(builder, globalState->liveHeapObjCounter, 1);
  }

  LLVMValueRef resultPtrLE = nullptr;
  if (location == Location::INLINE) {
    resultPtrLE = makeMidasLocal(functionState, builder, referendLT, "newstruct", LLVMGetUndef(referendLT));
  } else if (location == Location::YONDER) {
    size_t sizeBytes = LLVMABISizeOfType(globalState->dataLayout, referendLT);
    LLVMValueRef sizeLE = LLVMConstInt(LLVMInt64Type(), sizeBytes, false);

    auto newStructLE = callMalloc(globalState, builder, sizeLE);

    resultPtrLE =
        LLVMBuildBitCast(
            builder, newStructLE, LLVMPointerType(referendLT, 0), "newstruct");
  } else {
    assert(false);
    return nullptr;
  }

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, resultPtrLE, LLVMPointerType(LLVMVoidType(), 0), "");
    LLVMBuildCall(builder, globalState->censusAdd, &resultAsVoidPtrLE, 1, "");
  }
  return resultPtrLE;
}

LLVMValueRef mallocUnknownSizeArray(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef usaWrapperLT,
    LLVMTypeRef usaElementLT,
    LLVMValueRef lengthLE) {
  auto sizeBytesLE =
      LLVMBuildAdd(
          builder,
          constI64LE(LLVMABISizeOfType(globalState->dataLayout, usaWrapperLT)),
          LLVMBuildMul(
              builder,
              constI64LE(LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(usaElementLT, 1))),
              lengthLE,
              ""),
          "usaMallocSizeBytes");

  auto newWrapperPtrLE = callMalloc(globalState, builder, sizeBytesLE);

  if (globalState->opt->census) {
    adjustCounter(builder, globalState->liveHeapObjCounter, 1);
  }

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, newWrapperPtrLE, LLVMPointerType(LLVMVoidType(), 0), "");
    LLVMBuildCall(builder, globalState->censusAdd, &resultAsVoidPtrLE, 1, "");
  }

  return LLVMBuildBitCast(
      builder,
      newWrapperPtrLE,
      LLVMPointerType(usaWrapperLT, 0),
      "newstruct");
}

WrapperPtrLE mallocStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE) {

  // The +1 is for the null terminator at the end, for C compatibility.
  auto sizeBytesLE =
      LLVMBuildAdd(
          builder,
          lengthLE,
          makeConstIntExpr(functionState, builder,LLVMInt64Type(),  1 + LLVMABISizeOfType(globalState->dataLayout, globalState->stringWrapperStructL)),
          "strMallocSizeBytes");

  auto destCharPtrLE = callMalloc(globalState, builder, sizeBytesLE);

  if (globalState->opt->census) {
    adjustCounter(builder, globalState->liveHeapObjCounter, 1);
  }

  auto newStrWrapperPtrLE =
      WrapperPtrLE(
          globalState->metalCache.strRef,
          LLVMBuildBitCast(
              builder,
              destCharPtrLE,
              LLVMPointerType(globalState->stringWrapperStructL, 0),
              "newStrWrapperPtr"));
  fillControlBlock(
      FL(),
      globalState, functionState, builder,
      globalState->metalCache.str,
      Mutability::IMMUTABLE,
      Weakability::NON_WEAKABLE,
      getConcreteControlBlockPtr(builder, newStrWrapperPtrLE), "Str");
  LLVMBuildStore(builder, lengthLE, getLenPtrFromStrWrapperPtr(builder, newStrWrapperPtrLE));

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, newStrWrapperPtrLE.refLE, LLVMPointerType(LLVMVoidType(), 0), "");
    LLVMBuildCall(builder, globalState->censusAdd, &resultAsVoidPtrLE, 1, "");
  }

  // The caller still needs to initialize the actual chars inside!

  return newStrWrapperPtrLE;
}




void freeConcrete(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    WrapperPtrLE refLE,
    Reference* refM) {

  auto controlBlockPtrLE = getConcreteControlBlockPtr(builder, refLE);

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, controlBlockPtrLE, LLVMPointerType(LLVMVoidType(), 0), "");
    LLVMBuildCall(builder, globalState->censusRemove, &resultAsVoidPtrLE, 1,
        "");
  }

  if (globalState->opt->regionOverride == RegionOverride::ASSIST) {
    auto rcIsZeroLE = strongRcIsZero(globalState, builder, refM, controlBlockPtrLE);
    buildAssert(globalState, functionState, builder, rcIsZeroLE,
        "Tried to free concrete that had nonzero RC!");

    if (auto structReferendM = dynamic_cast<StructReferend*>(refM->referend)) {
      auto structM = globalState->program->getStruct(structReferendM->fullName);
      if (getEffectiveWeakability(globalState, structM) == Weakability::WEAKABLE) {
        noteWeakableDestroyed(globalState, functionState, builder, refM, controlBlockPtrLE);
      }
    } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(refM->referend)) {
      assert(false); // Do we ever freeConcrete an interface?
    } else {
      // Do nothing, only structs and interfaces are weakable in assist mode.
    }
  } else if (globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
    // Dont need to assert that the strong RC is zero, thats the only way we'd get here.

    if (auto structReferendM = dynamic_cast<StructReferend*>(refM->referend)) {
      auto structM = globalState->program->getStruct(structReferendM->fullName);
      if (getEffectiveWeakability(globalState, structM) == Weakability::WEAKABLE) {
        noteWeakableDestroyed(globalState, functionState, builder, refM, controlBlockPtrLE);
      }
    } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(refM->referend)) {
      auto interfaceM = globalState->program->getInterface(interfaceReferendM->fullName);
      if (getEffectiveWeakability(globalState, interfaceM) == Weakability::WEAKABLE) {
        noteWeakableDestroyed(globalState, functionState, builder, refM, controlBlockPtrLE);
      }
    } else {
      // Do nothing, only structs and interfaces are weakable in naive-rc mode.
    }
  } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
    // In fast mode, only shared things are strong RC'd
    if (refM->ownership == Ownership::SHARE) {
      // Only shared stuff is RC'd in fast mode
      auto rcIsZeroLE = strongRcIsZero(globalState, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
    } else {
      // It's a mutable, so mark WRCs dead

      if (auto structReferendM = dynamic_cast<StructReferend *>(refM->referend)) {
        auto structM = globalState->program->getStruct(structReferendM->fullName);
        if (getEffectiveWeakability(globalState, structM) == Weakability::WEAKABLE) {
          noteWeakableDestroyed(globalState, functionState, builder, refM, controlBlockPtrLE);
        }
      } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
        auto interfaceM = globalState->program->getStruct(interfaceReferendM->fullName);
        if (getEffectiveWeakability(globalState, interfaceM) == Weakability::WEAKABLE) {
          noteWeakableDestroyed(globalState, functionState, builder, refM, controlBlockPtrLE);
        }
      } else {
        // Do nothing, only structs and interfaces are weakable in assist mode.
      }
    }
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
    if (refM->ownership == Ownership::SHARE) {
      auto rcIsZeroLE = strongRcIsZero(globalState, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
    } else {
      assert(refM->ownership == Ownership::OWN);

      // In resilient mode, every mutable is weakable.
      noteWeakableDestroyed(globalState, functionState, builder, refM, controlBlockPtrLE);
    }
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    if (refM->ownership == Ownership::SHARE) {
      auto rcIsZeroLE = strongRcIsZero(globalState, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
    } else {
      assert(refM->ownership == Ownership::OWN);

      // In resilient mode, every mutable is weakable.
      noteWeakableDestroyed(globalState, functionState, builder, refM, controlBlockPtrLE);
    }
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
    if (refM->ownership == Ownership::SHARE) {
      auto rcIsZeroLE = strongRcIsZero(globalState, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
    } else {
      assert(refM->ownership == Ownership::OWN);

      // In resilient mode, every mutable is weakable.
      noteWeakableDestroyed(globalState, functionState, builder, refM, controlBlockPtrLE);
    }
  } else assert(false);

  if (refM->location == Location::INLINE) {
    // Do nothing, it was alloca'd.
  } else if (refM->location == Location::YONDER) {
    callFree(globalState, builder, controlBlockPtrLE);
  }

  if (globalState->opt->census) {
    adjustCounter(builder, globalState->liveHeapObjCounter, -1);
  }
}
