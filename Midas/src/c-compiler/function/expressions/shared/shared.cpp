#include <region/common/common.h>
#include "shared.h"

#include "translatetype.h"
#include "controlblock.h"
#include "branch.h"
#include "weaks.h"

// A "Never" is something that should never be read.
// This is useful in a lot of situations, for example:
// - The return type of Panic()
// - The result of the Discard node
LLVMTypeRef makeNeverType() {
  // We arbitrarily use a zero-len array of i57 here because it's zero sized and
  // very unlikely to be used anywhere else.
  // We could use an empty struct instead, but this'll do.
  return LLVMArrayType(LLVMIntType(NEVER_INT_BITS), 0);
}
LLVMValueRef makeNever() {
  LLVMValueRef empty[1] = {};
  // We arbitrarily use a zero-len array of i57 here because it's zero sized and
  // very unlikely to be used anywhere else.
  // We could use an empty struct instead, but this'll do.
  return LLVMConstArray(LLVMIntType(NEVER_INT_BITS), empty, 0);
}

LLVMValueRef makeMidasLocal(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMTypeRef typeL,
    const std::string& name,
    LLVMValueRef valueToStore) {
  auto localAddr =
      LLVMBuildAlloca(
          functionState->localsBuilder,
          typeL,
          name.c_str());
  LLVMBuildStore(builder, valueToStore, localAddr);
  return localAddr;
}

void makeHammerLocal(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Local* local,
    LLVMValueRef valueToStore) {
  auto localAddr =
      makeMidasLocal(
          functionState,
          builder,
          translateType(globalState, local->type),
          local->id->maybeName.c_str(),
          valueToStore);
  blockState->addLocal(local->id, localAddr);
}

LLVMValueRef adjustCounter(
    LLVMBuilderRef builder,
    LLVMValueRef counterPtrLE,
    int adjustAmount) {
  if (LLVMTypeOf(counterPtrLE) == LLVMPointerType(LLVMInt64Type(), 0)) {
    auto prevValLE = LLVMBuildLoad(builder, counterPtrLE, "counterPrevVal");
    auto newValLE =
        LLVMBuildAdd(
            builder, prevValLE, LLVMConstInt(LLVMInt64Type(), adjustAmount, true), "counterNewVal");
    LLVMBuildStore(builder, newValLE, counterPtrLE);

    return newValLE;
  } else if (LLVMTypeOf(counterPtrLE) == LLVMPointerType(LLVMInt32Type(), 0)) {
      auto prevValLE = LLVMBuildLoad(builder, counterPtrLE, "counterPrevVal");
      auto newValLE =
          LLVMBuildAdd(
              builder, prevValLE, LLVMConstInt(LLVMInt32Type(), adjustAmount, true), "counterNewVal");
      LLVMBuildStore(builder, newValLE, counterPtrLE);

      return newValLE;
  } else {
    // impl
    assert(false);
  }
}


LLVMValueRef getTablePtrFromInterfaceRef(
    LLVMBuilderRef builder,
    LLVMValueRef interfaceRefLE) {
  return LLVMBuildExtractValue(builder, interfaceRefLE, 1, "itablePtr");
}

LLVMValueRef getControlBlockPtr(
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef referenceLE,
    Referend* referendM) {
  if (dynamic_cast<InterfaceReferend*>(referendM)) {
    return getControlBlockPtrFromInterfaceRef(builder, referenceLE);
  } else if (dynamic_cast<StructReferend*>(referendM)) {
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<KnownSizeArrayT*>(referendM)) {
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<UnknownSizeArrayT*>(referendM)) {
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<Str*>(referendM)) {
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else {
    assert(false);
    return nullptr;
  }
}

// Returns the new RC
LLVMValueRef adjustStrongRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE,
    UnconvertedReference* refM,
    int amount) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == UnconvertedOwnership::SHARE);
      break;
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      assert(refM->ownership == UnconvertedOwnership::SHARE);
      break;
    default:
      assert(false);
  }

  auto controlBlockPtrLE = getControlBlockPtr(builder, exprLE, refM->referend);
  auto rcPtrLE = getStrongRcPtrFromControlBlockPtr(globalState, builder, refM, controlBlockPtrLE);
  auto oldRc = LLVMBuildLoad(builder, rcPtrLE, "oldRc");
  auto newRc = adjustCounter(builder, rcPtrLE, amount);
//  flareAdjustStrongRc(from, globalState, functionState, builder, refM, controlBlockPtrLE, oldRc, newRc);
  return newRc;
}

LLVMValueRef strongRcIsZero(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    UnconvertedReference* refM,
    LLVMValueRef controlBlockPtrLE) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == UnconvertedOwnership::SHARE);
      break;
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      assert(refM->ownership == UnconvertedOwnership::SHARE);
      break;
    default:
      assert(false);
  }

  return isZeroLE(builder, getStrongRcFromControlBlockPtr(globalState, builder, refM, controlBlockPtrLE));
}

LLVMValueRef isZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE) {
  return LLVMBuildICmp(
      builder,
      LLVMIntEQ,
      intLE,
      LLVMConstInt(LLVMTypeOf(intLE), 0, false),
      "strongRcIsZero");
}

LLVMValueRef isNonZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE) {
  return LLVMBuildICmp(
      builder,
      LLVMIntNE,
      intLE,
      LLVMConstInt(LLVMTypeOf(intLE), 0, false),
      "rcIsNonZero");
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    const std::string& first) {
  auto s = globalState->getOrMakeStringConstant(first);
  LLVMBuildCall(builder, globalState->printCStr, &s, 1, "");
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE) {
  if (LLVMTypeOf(exprLE) == LLVMInt64Type()) {
    LLVMBuildCall(builder, globalState->printInt, &exprLE, 1, "");
  } else if (LLVMTypeOf(exprLE) == LLVMInt32Type()) {
    auto i64LE = LLVMBuildZExt(builder, exprLE, LLVMInt64Type(), "asI64");
    LLVMBuildCall(builder, globalState->printInt, &i64LE, 1, "");
  } else if (LLVMTypeOf(exprLE) == LLVMPointerType(LLVMInt8Type(), 0)) {
    LLVMBuildCall(builder, globalState->printCStr, &exprLE, 1, "");
  } else if (LLVMTypeOf(exprLE) == LLVMPointerType(LLVMVoidType(), 0)) {
    auto asIntLE = LLVMBuildPointerCast(builder, exprLE, LLVMInt64Type(), "asI64");
    LLVMBuildCall(builder, globalState->printInt, &asIntLE, 1, "");
  } else {
    assert(false);
//    buildPrint(
//        globalState,
//        builder,
//        LLVMBuildPointerCast(builder, exprLE, LLVMInt64Type(), ""));
  }
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int num) {
  buildPrint(globalState, builder, LLVMConstInt(LLVMInt64Type(), num, false));
}

// We'll assert if conditionLE is false.
void buildAssert(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    const std::string& failMessage) {
  buildIf(
      functionState, builder, isZeroLE(builder, conditionLE),
      [globalState, functionState, failMessage](LLVMBuilderRef thenBuilder) {
        buildPrint(globalState, thenBuilder, failMessage + " Exiting!\n");
        auto exitCodeIntLE = LLVMConstInt(LLVMInt8Type(), 255, false);
        LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");
      });
}

LLVMValueRef getItablePtrFromInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* virtualParamMT,
    LLVMValueRef virtualArgLE) {
  buildFlare(FL(), globalState, functionState, builder);
  assert(LLVMTypeOf(virtualArgLE) == translateType(globalState, virtualParamMT));
  return getTablePtrFromInterfaceRef(builder, virtualArgLE);
}

LLVMValueRef getObjPtrFromInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* virtualParamMT,
    LLVMValueRef virtualArgLE) {
  assert(LLVMTypeOf(virtualArgLE) == translateType(globalState, virtualParamMT));
  return LLVMBuildPointerCast(
          builder,
          getControlBlockPtrFromInterfaceRef(builder, virtualArgLE),
          LLVMPointerType(LLVMVoidType(), 0),
          "objAsVoidPtr");
}

LLVMValueRef buildInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* virtualParamMT,
    std::vector<LLVMValueRef> argExprsLE,
    int virtualParamIndex,
    int indexInEdge) {
  auto virtualArgLE = argExprsLE[virtualParamIndex];
  assert(LLVMTypeOf(virtualArgLE) == translateType(globalState, virtualParamMT));

  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef newVirtualArgLE = nullptr;
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      switch (virtualParamMT->ownership) {
        case UnconvertedOwnership::OWN:
        case UnconvertedOwnership::BORROW:
        case UnconvertedOwnership::SHARE: {
          itablePtrLE = getItablePtrFromInterfacePtr(globalState, functionState, builder, virtualParamMT, virtualArgLE);
          auto objVoidPtrLE = getObjPtrFromInterfacePtr(globalState, functionState, builder, virtualParamMT, virtualArgLE);
          newVirtualArgLE = objVoidPtrLE;
          break;
        }
        case UnconvertedOwnership::WEAK: {
          // Disassemble the weak interface ref.
          auto interfaceRefLE =
              getInnerRefFromWeakRef(
                  globalState, functionState, builder, virtualParamMT, virtualArgLE);
          itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);
          // Now, reassemble a weak void* ref to the struct.
          auto weakVoidStructRefLE =
              weakInterfaceRefToWeakStructRef(
                  globalState, functionState, builder, virtualParamMT, virtualArgLE);
          newVirtualArgLE = weakVoidStructRefLE;
          break;
        }
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (virtualParamMT->ownership) {
        case UnconvertedOwnership::OWN:
        case UnconvertedOwnership::SHARE: {
          itablePtrLE = getItablePtrFromInterfacePtr(globalState, functionState, builder, virtualParamMT, virtualArgLE);
          auto objVoidPtrLE = getObjPtrFromInterfacePtr(globalState, functionState, builder, virtualParamMT, virtualArgLE);
          newVirtualArgLE = objVoidPtrLE;
          break;
        }
        case UnconvertedOwnership::BORROW:
        case UnconvertedOwnership::WEAK: {
          // Disassemble the weak interface ref.
          auto interfaceRefLE =
              getInnerRefFromWeakRef(
                  globalState, functionState, builder, virtualParamMT, virtualArgLE);
          itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);
          // Now, reassemble a weak void* ref to the struct.
          auto weakVoidStructRefLE =
              weakInterfaceRefToWeakStructRef(
                  globalState, functionState, builder, virtualParamMT, virtualArgLE);
          newVirtualArgLE = weakVoidStructRefLE;
          break;
        }
      }
      break;
    }
    default:
      assert(false);
  }
  argExprsLE[virtualParamIndex] = newVirtualArgLE;

  buildFlare(FL(), globalState, functionState, builder);

  assert(LLVMGetTypeKind(LLVMTypeOf(itablePtrLE)) == LLVMPointerTypeKind);
  auto funcPtrPtrLE =
      LLVMBuildStructGEP(
          builder, itablePtrLE, indexInEdge, "methodPtrPtr");
  buildFlare(FL(), globalState, functionState, builder);

  auto funcPtrLE = LLVMBuildLoad(builder, funcPtrPtrLE, "methodPtr");

  buildFlare(FL(), globalState, functionState, builder);

  return LLVMBuildCall(
      builder, funcPtrLE, argExprsLE.data(), argExprsLE.size(), "");
}

LLVMValueRef makeConstExpr(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef constExpr) {
  auto localAddr = makeMidasLocal(functionState, builder, LLVMTypeOf(constExpr), "", constExpr);
  return LLVMBuildLoad(builder, localAddr, "");
}

LLVMValueRef makeConstIntExpr(FunctionState* functionState, LLVMBuilderRef builder, LLVMTypeRef type, int value) {
  return makeConstExpr(functionState, builder, LLVMConstInt(type, value, false));
}

void buildAssertCensusContains(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef ptrLE) {
  LLVMValueRef resultAsVoidPtrLE =
      LLVMBuildPointerCast(
          builder, ptrLE, LLVMPointerType(LLVMVoidType(), 0), "");
  auto isRegisteredIntLE = LLVMBuildCall(builder, globalState->censusContains, &resultAsVoidPtrLE, 1, "");
  auto isRegisteredBoolLE = LLVMBuildTruncOrBitCast(builder,  isRegisteredIntLE, LLVMInt1Type(), "");
  buildIf(functionState, builder, isZeroLE(builder, isRegisteredBoolLE),
      [globalState, checkerAFL](LLVMBuilderRef thenBuilder) {
        buildPrintAreaAndFileAndLine(globalState, thenBuilder, checkerAFL);
        buildPrint(globalState, thenBuilder, "Object not registered with census, exiting!\n");
        auto exitCodeIntLE = LLVMConstInt(LLVMInt8Type(), 255, false);
        LLVMBuildCall(thenBuilder, globalState->exit, &exitCodeIntLE, 1, "");
      });
}

void checkValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* refM,
    LLVMValueRef refLE) {
  assert(refLE != nullptr);
  assert(LLVMTypeOf(refLE) == translateType(globalState, refM));
  if (globalState->opt->census) {
    if (refM->ownership == UnconvertedOwnership::OWN) {
      if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
        auto itablePtrLE = getTablePtrFromInterfaceRef(builder, refLE);
        buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
      }
      auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM->referend);
      buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
    } else if (refM->ownership == UnconvertedOwnership::SHARE) {
      if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
        auto itablePtrLE = getTablePtrFromInterfaceRef(builder, refLE);
        buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
      }
      if (refM->location == Location::INLINE) {
        // Nothing to do, there's no control block or ref counts or anything.
      } else if (refM->location == Location::YONDER) {
        auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM->referend);

        // We dont check ref count >0 because imm destructors receive with rc=0.
        //      auto rcLE = getRcFromControlBlockPtr(globalState, builder, controlBlockPtrLE);
        //      auto rcPositiveLE = LLVMBuildICmp(builder, LLVMIntSGT, rcLE, constI64LE(0), "");
        //      buildAssert(checkerAFL, globalState, functionState, blockState, builder, rcPositiveLE, "Invalid RC!");

        buildAssertCensusContains(checkerAFL, globalState, functionState, builder,
            controlBlockPtrLE);
      } else
        assert(false);
    } else {
      switch (globalState->opt->regionOverride) {
        case RegionOverride::ASSIST:
        case RegionOverride::NAIVE_RC:
        case RegionOverride::FAST: {
          if (refM->ownership == UnconvertedOwnership::BORROW) {
            if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
              auto itablePtrLE = getTablePtrFromInterfaceRef(builder, refLE);
              buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
            }
            auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM->referend);
            buildAssertCensusContains(checkerAFL, globalState, functionState, builder,
                controlBlockPtrLE);
          } else if (refM->ownership == UnconvertedOwnership::WEAK) {
            buildCheckWeakRef(checkerAFL, globalState, functionState, builder, refM, refLE);
          } else
            assert(false);
          break;
        }
        case RegionOverride::RESILIENT_V0:
        case RegionOverride::RESILIENT_V1:
        case RegionOverride::RESILIENT_V2: {
          buildCheckWeakRef(checkerAFL, globalState, functionState, builder, refM, refLE);
          break;
        }
        default:
          assert(false);
      }
    }
  }
}

LLVMValueRef buildCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    std::vector<LLVMValueRef> argsLE) {
  auto funcIter = globalState->functions.find(prototype->name->name);
  assert(funcIter != globalState->functions.end());
  auto funcL = funcIter->second;

  buildFlare(FL(), globalState, functionState, builder, "Suspending function ", functionState->containingFuncM->prototype->name->name);
  buildFlare(FL(), globalState, functionState, builder, "Calling function ", prototype->name->name);

  auto resultLE = LLVMBuildCall(builder, funcL, argsLE.data(), argsLE.size(), "");
  checkValidReference(FL(), globalState, functionState, builder, prototype->returnType, resultLE);

  if (prototype->returnType->referend == globalState->metalCache.never) {
    buildFlare(FL(), globalState, functionState, builder, "Done calling function ", prototype->name->name);
    buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncM->prototype->name->name);

    return LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
  } else {
    buildFlare(FL(), globalState, functionState, builder, "Done calling function ", prototype->name->name);
    buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncM->prototype->name->name);
    return resultLE;
  }
}

LLVMValueRef makeInterfaceRefStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructReferend* sourceStructReferendM,
    InterfaceReferend* targetInterfaceReferendM,
    LLVMValueRef controlBlockPtrLE) {

  auto interfaceRefLT =
      globalState->getInterfaceRefStruct(
          targetInterfaceReferendM->fullName);

  auto interfaceRefLE = LLVMGetUndef(interfaceRefLT);
  interfaceRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceRefLE,
          controlBlockPtrLE,
          0,
          "interfaceRefWithOnlyObj");
  auto itablePtrLE =
      globalState->getInterfaceTablePtr(
          globalState->program->getStruct(sourceStructReferendM->fullName)
              ->getEdgeForInterface(targetInterfaceReferendM->fullName));
  interfaceRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceRefLE,
          itablePtrLE,
          1,
          "interfaceRef");
  buildFlare(FL(), globalState, functionState, builder, "itable: ", ptrToVoidPtrLE(builder, itablePtrLE), " for ", sourceStructReferendM->fullName->name, " for ", targetInterfaceReferendM->fullName->name);

  return interfaceRefLE;
}

LLVMValueRef upcast(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    UnconvertedReference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    LLVMValueRef sourceRefLE,

    UnconvertedReference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      switch (sourceStructTypeM->ownership) {
        case UnconvertedOwnership::SHARE:
        case UnconvertedOwnership::OWN:
        case UnconvertedOwnership::BORROW:
          return upcastThinPtr(globalState, functionState, builder, sourceStructTypeM, sourceStructReferendM, sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
          break;
        case UnconvertedOwnership::WEAK:
          return upcastWeakFatPtr(globalState, functionState, builder, sourceStructTypeM, sourceStructReferendM, sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
          break;
        default:
          assert(false);
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (sourceStructTypeM->ownership) {
        case UnconvertedOwnership::SHARE:
        case UnconvertedOwnership::OWN:
          return upcastThinPtr(globalState, functionState, builder, sourceStructTypeM, sourceStructReferendM, sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
          break;
        case UnconvertedOwnership::BORROW:
        case UnconvertedOwnership::WEAK:
          return upcastWeakFatPtr(globalState, functionState, builder, sourceStructTypeM, sourceStructReferendM, sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
          break;
        default:
          assert(false);
      }
      break;
    }
    default:
      assert(false);
  }

}

// TODO move into region classes
Weakability getEffectiveWeakability(GlobalState* globalState, RawArrayT* array) {
  if (array->mutability == Mutability::IMMUTABLE) {
    return Weakability::NON_WEAKABLE;
  } else {
    if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
        globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
      return Weakability::NON_WEAKABLE;
    } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
      return Weakability::NON_WEAKABLE;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
      // All mutables are weakabile in resilient mode
      return Weakability::WEAKABLE;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
      // All mutables are weakabile in resilient mode
      return Weakability::WEAKABLE;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
      // All mutables are weakabile in resilient mode
      return Weakability::WEAKABLE;
    } else assert(false);
  }
}

// TODO move into region classes
Weakability getEffectiveWeakability(GlobalState* globalState, StructDefinition* structDef) {
  if (structDef->mutability == Mutability::IMMUTABLE) {
    assert(structDef->weakability == UnconvertedWeakability::NON_WEAKABLE);
    return Weakability::NON_WEAKABLE;
  } else {
    if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
        globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
      if (structDef->weakability == UnconvertedWeakability::WEAKABLE) {
        return Weakability::WEAKABLE;
      } else {
        return Weakability::NON_WEAKABLE;
      }
    } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
      if (structDef->weakability == UnconvertedWeakability::WEAKABLE) {
        return Weakability::WEAKABLE;
      } else {
        return Weakability::NON_WEAKABLE;
      }
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
      // All mutable structs are weakability in resilient mode
      return Weakability::WEAKABLE;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
      // All mutable structs are weakability in resilient mode
      return Weakability::WEAKABLE;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
      // All mutable structs are weakability in resilient mode
      return Weakability::WEAKABLE;
    } else assert(false);
  }
}

// TODO move into region classes
Weakability getEffectiveWeakability(GlobalState* globalState, InterfaceDefinition* interfaceDef) {
  if (interfaceDef->mutability == Mutability::IMMUTABLE) {
    assert(interfaceDef->weakability == UnconvertedWeakability::NON_WEAKABLE);
    return Weakability::NON_WEAKABLE;
  } else {
    if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
        globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
      if (interfaceDef->weakability == UnconvertedWeakability::WEAKABLE) {
        return Weakability::WEAKABLE;
      } else {
        return Weakability::NON_WEAKABLE;
      }
    } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
      if (interfaceDef->weakability == UnconvertedWeakability::WEAKABLE) {
        return Weakability::WEAKABLE;
      } else {
        return Weakability::NON_WEAKABLE;
      }
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
      // All mutable structs are weakable in resilient mode
      return Weakability::WEAKABLE;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
      // All mutable structs are weakable in resilient fast mode
      return Weakability::WEAKABLE;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
      // All mutable structs are weakable in resilient fast mode
      return Weakability::WEAKABLE;
    } else assert(false);
  }
}


// TODO maybe combine with alias/acquireReference?
// After we load from a local, member, or element, we can feed the result through this
// function to turn it into a desired ownership.
// Example:
// - Can load from an owning ref member to get a constraint ref.
// - Can load from a constraint ref member to get a weak ref.
LLVMValueRef upgradeLoadResultToRefWithTargetOwnership(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* sourceType,
    UnconvertedReference* targetType,
    LLVMValueRef sourceRefLE) {
  auto sourceOwnership = sourceType->ownership;
  auto sourceLocation = sourceType->location;
  auto targetOwnership = targetType->ownership;
  auto targetLocation = targetType->location;
//  assert(sourceLocation == targetLocation); // unimplemented

  checkValidReference(FL(), globalState, functionState, builder, sourceType, sourceRefLE);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      if (sourceOwnership == UnconvertedOwnership::SHARE) {
        if (sourceLocation == Location::INLINE) {
          return sourceRefLE;
        } else {
          auto resultRefLE = sourceRefLE;

          return resultRefLE;
        }
      } else if (sourceOwnership == UnconvertedOwnership::OWN) {
        if (targetOwnership == UnconvertedOwnership::OWN) {
          // We can never "load" an owning ref from any of these:
          // - We can only get owning refs from locals by unstackifying
          // - We can only get owning refs from structs by destroying
          // - We can only get owning refs from elements by destroying
          // However, we CAN load owning refs by:
          // - Swapping from a local
          // - Swapping from an element
          // - Swapping from a member
          return sourceRefLE;
        } else if (targetOwnership == UnconvertedOwnership::BORROW) {
          auto resultRefLE = sourceRefLE;
          // We do the same thing for inline and yonder muts, the only difference is
          // where the memory lives.

          checkValidReference(
              FL(), globalState, functionState, builder, targetType, resultRefLE);

          return resultRefLE;
        } else if (targetOwnership == UnconvertedOwnership::WEAK) {
          // Now we need to package it up into a weak ref.
          if (auto structReferend = dynamic_cast<StructReferend*>(sourceType->referend)) {
            auto weakRefLE =
                assembleStructWeakRef(
                    globalState, functionState, builder, sourceType, structReferend, sourceRefLE);
            return weakRefLE;
          } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(sourceType->referend)) {
            auto weakRefLE =
                assembleInterfaceWeakRef(
                    globalState, functionState, builder, sourceType, interfaceReferendM, sourceRefLE);
            return weakRefLE;
          } else if (auto knownSizeArray = dynamic_cast<KnownSizeArrayT*>(sourceType->referend)) {
            auto weakRefLE =
                assembleKnownSizeArrayWeakRef(
                    globalState, builder, sourceType, knownSizeArray, sourceRefLE);
            return weakRefLE;
          } else if (auto unknownSizeArray = dynamic_cast<UnknownSizeArrayT*>(sourceType->referend)) {
            auto weakRefLE =
                assembleUnknownSizeArrayWeakRef(
                    globalState, functionState, builder, sourceType, unknownSizeArray, sourceRefLE);
            return weakRefLE;
          } else assert(false);
        } else {
          assert(false);
        }
      } else if (sourceOwnership == UnconvertedOwnership::BORROW) {
        buildFlare(FL(), globalState, functionState, builder);

        if (targetOwnership == UnconvertedOwnership::OWN) {
          assert(false); // Cant load an owning reference from a constraint ref local.
        } else if (targetOwnership == UnconvertedOwnership::BORROW) {
          auto resultRefLE = sourceRefLE;
          // We do the same thing for inline and yonder muts, the only difference is
          // where the memory lives.

          return resultRefLE;
        } else if (targetOwnership == UnconvertedOwnership::WEAK) {
          // Making a weak ref from a constraint ref local.

          if (auto structReferendM = dynamic_cast<StructReferend*>(sourceType->referend)) {
            // We do the same thing for inline and yonder muts, the only difference is
            // where the memory lives.
            auto weakRefLE =
                assembleStructWeakRef(
                    globalState, functionState, builder, sourceType, structReferendM, sourceRefLE);
            return weakRefLE;
          } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(sourceType->referend)) {
            auto weakRefLE =
                assembleInterfaceWeakRef(
                    globalState, functionState, builder, sourceType, interfaceReferendM, sourceRefLE);
            return weakRefLE;
          } else assert(false);
        } else {
          assert(false);
        }
      } else if (sourceOwnership == UnconvertedOwnership::WEAK) {
        assert(targetOwnership == UnconvertedOwnership::WEAK);
        return sourceRefLE;
      } else {
        assert(false);
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      if (sourceOwnership == UnconvertedOwnership::SHARE) {
        if (sourceLocation == Location::INLINE) {
          return sourceRefLE;
        } else {
          auto resultRefLE = sourceRefLE;

          return resultRefLE;
        }
      } else if (sourceOwnership == UnconvertedOwnership::OWN) {
        if (targetOwnership == UnconvertedOwnership::OWN) {
          // We can never "load" an owning ref from any of these:
          // - We can only get owning refs from locals by unstackifying
          // - We can only get owning refs from structs by destroying
          // - We can only get owning refs from elements by destroying
          // However, we CAN load owning refs by:
          // - Swapping from a local
          // - Swapping from an element
          // - Swapping from a member
          return sourceRefLE;
        } else if (targetOwnership == UnconvertedOwnership::BORROW
            || targetOwnership == UnconvertedOwnership::WEAK) {
          // Now we need to package it up into a weak ref.
          if (auto structReferend = dynamic_cast<StructReferend*>(sourceType->referend)) {
            auto weakRefLE =
                assembleStructWeakRef(
                    globalState, functionState, builder, sourceType, structReferend, sourceRefLE);
            return weakRefLE;
          } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(sourceType->referend)) {
            auto weakRefLE =
                assembleInterfaceWeakRef(
                    globalState, functionState, builder, sourceType, interfaceReferendM, sourceRefLE);
            return weakRefLE;
          } else if (auto knownSizeArray = dynamic_cast<KnownSizeArrayT*>(sourceType->referend)) {
            auto weakRefLE =
                assembleKnownSizeArrayWeakRef(
                    globalState, builder, sourceType, knownSizeArray, sourceRefLE);
            return weakRefLE;
          } else if (auto unknownSizeArray = dynamic_cast<UnknownSizeArrayT*>(sourceType->referend)) {
            auto weakRefLE =
                assembleUnknownSizeArrayWeakRef(
                    globalState, functionState, builder, sourceType, unknownSizeArray, sourceRefLE);
            return weakRefLE;
          } else assert(false);
        } else {
          assert(false);
        }
      } else if (sourceOwnership == UnconvertedOwnership::BORROW || sourceOwnership == UnconvertedOwnership::WEAK) {
        assert(targetOwnership == UnconvertedOwnership::BORROW || targetOwnership == UnconvertedOwnership::WEAK);

        auto resultRefLE = sourceRefLE;
        return resultRefLE;
      } else {
        assert(false);
      }
      break;
    }
    default:
      assert(false);
  }

}

LLVMValueRef addExtern(LLVMModuleRef mod, const std::string& name, LLVMTypeRef retType, std::vector<LLVMTypeRef> paramTypes) {
  LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
  return LLVMAddFunction(mod, name.c_str(), funcType);
}
