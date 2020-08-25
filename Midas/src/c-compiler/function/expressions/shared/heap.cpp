#include "utils/fileio.h"
#include "heap.h"
#include "members.h"
#include "shared.h"
#include "controlblock.h"
#include "string.h"
#include "weaks.h"

LLVMValueRef allocateStruct(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    LLVMTypeRef structL) {
  if (globalState->opt->census) {
    adjustCounter(builder, globalState->liveHeapObjCounter, 1);
  }

  LLVMValueRef resultPtrLE = nullptr;
  if (structTypeM->location == Location::INLINE) {
    resultPtrLE = LLVMBuildAlloca(builder, structL, "newstruct");
  } else if (structTypeM->location == Location::YONDER) {
    size_t sizeBytes = LLVMABISizeOfType(globalState->dataLayout, structL);
    LLVMValueRef sizeLE = LLVMConstInt(LLVMInt64Type(), sizeBytes, false);

    auto newStructLE =
        LLVMBuildCall(builder, globalState->malloc, &sizeLE, 1, "");

    resultPtrLE =
        LLVMBuildBitCast(
            builder, newStructLE, LLVMPointerType(structL, 0), "newstruct");
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

  auto newWrapperPtrLE =
      LLVMBuildCall(builder, globalState->malloc, &sizeBytesLE, 1, "");

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

LLVMValueRef mallocStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE) {

  // The +1 is for the null terminator at the end, for C compatibility.
  auto sizeBytesLE =
      LLVMBuildAdd(
          builder,
          lengthLE,
          makeConstIntExpr(builder,LLVMInt64Type(),  1 + LLVMABISizeOfType(globalState->dataLayout, globalState->stringWrapperStructL)),
          "strMallocSizeBytes");

  auto destCharPtrLE =
      LLVMBuildCall(builder, globalState->malloc, &sizeBytesLE, 1, "donePtr");

  if (globalState->opt->census) {
    adjustCounter(builder, globalState->liveHeapObjCounter, 1);
  }

  auto newStrWrapperPtrLE =
      LLVMBuildBitCast(
          builder,
          destCharPtrLE,
          LLVMPointerType(globalState->stringWrapperStructL, 0),
          "newStrWrapperPtr");
  fillControlBlock(
      FL(),
      globalState, functionState, builder,
      Mutability::IMMUTABLE,
      Weakability::NON_WEAKABLE,
      getConcreteControlBlockPtr(builder, newStrWrapperPtrLE), "Str");
  LLVMBuildStore(builder, lengthLE, getLenPtrFromStrWrapperPtr(builder, newStrWrapperPtrLE));

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, newStrWrapperPtrLE, LLVMPointerType(LLVMVoidType(), 0), "");
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
    LLVMValueRef refLE,
    Reference* refM) {

  auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM);

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
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
    if (refM->ownership == Ownership::SHARE) {
      auto rcIsZeroLE = strongRcIsZero(globalState, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
    } else {
      assert(refM->ownership == Ownership::OWN);

      // In resilient mode, every mutable is weakable.
      noteWeakableDestroyed(globalState, functionState, builder, refM, controlBlockPtrLE);
    }
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_FAST) {
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
    auto concreteAsCharPtrLE =
        LLVMBuildBitCast(
            builder,
            controlBlockPtrLE,
            LLVMPointerType(LLVMInt8Type(), 0),
            "concreteCharPtrForFree");
    LLVMBuildCall(
        builder, globalState->free, &concreteAsCharPtrLE, 1, "");
  }

  if (globalState->opt->census) {
    adjustCounter(builder, globalState->liveHeapObjCounter, -1);
  }
}
