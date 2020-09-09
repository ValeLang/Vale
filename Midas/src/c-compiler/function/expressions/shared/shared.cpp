#include <region/common/common.h>
#include <region/common/fatweaks/fatweaks.h>
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

LLVMValueRef makeEmptyTuple(GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder) {
  return LLVMGetUndef(
      functionState->defaultRegion->translateType(globalState->metalCache.emptyTupleStructRef));
}

Ref makeEmptyTupleRef(GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder) {
  auto emptyTupleLE = makeEmptyTuple(globalState, functionState, builder);
  return wrap(functionState->defaultRegion, globalState->metalCache.emptyTupleStructRef, emptyTupleLE);
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
    Ref refToStore) {
  auto toStoreLE =
      checkValidReference(FL(), globalState, functionState, builder, local->type, refToStore);
  auto localAddr =
      makeMidasLocal(
          functionState,
          builder,
          functionState->defaultRegion->translateType(local->type),
          local->id->maybeName.c_str(),
          toStoreLE);
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
    InterfaceFatPtrLE interfaceRefLE) {
  return LLVMBuildExtractValue(builder, interfaceRefLE.refLE, 1, "itablePtr");
}

LLVMValueRef getControlBlockPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  auto referendM = referenceM->referend;
  if (dynamic_cast<InterfaceReferend*>(referendM)) {
    auto referenceLE = InterfaceFatPtrLE(referenceM, ref);
    return getControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<StructReferend*>(referendM)) {
    auto referenceLE = WrapperPtrLE(referenceM, ref);
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<KnownSizeArrayT*>(referendM)) {
    auto referenceLE = WrapperPtrLE(referenceM, ref);
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<UnknownSizeArrayT*>(referendM)) {
    auto referenceLE = WrapperPtrLE(referenceM, ref);
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<Str*>(referendM)) {
    auto referenceLE = WrapperPtrLE(referenceM, ref);
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else {
    assert(false);
    return nullptr;
  }
}

LLVMValueRef getControlBlockPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    Ref ref,
    Reference* referenceM) {
  auto referendM = referenceM->referend;
  if (dynamic_cast<InterfaceReferend*>(referendM)) {
    auto referenceLE =
        InterfaceFatPtrLE(
            referenceM,
            checkValidReference(FL(), globalState, functionState, builder, referenceM, ref));
    return getControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<StructReferend*>(referendM)) {
    auto referenceLE =
        WrapperPtrLE(
            referenceM,
            checkValidReference(FL(), globalState, functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<KnownSizeArrayT*>(referendM)) {
    auto referenceLE =
        WrapperPtrLE(
            referenceM,
            checkValidReference(FL(), globalState, functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<UnknownSizeArrayT*>(referendM)) {
    auto referenceLE =
        WrapperPtrLE(
            referenceM,
            checkValidReference(FL(), globalState, functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<Str*>(referendM)) {
    auto referenceLE =
        WrapperPtrLE(
            referenceM,
            checkValidReference(FL(), globalState, functionState, builder, referenceM, ref));
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
    Ref exprRef,
    Reference* refM,
    int amount) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      assert(refM->ownership == Ownership::SHARE);
      break;
    default:
      assert(false);
  }

  auto controlBlockPtrLE =
      getControlBlockPtr(globalState, functionState, builder, exprRef, refM);
  auto rcPtrLE = getStrongRcPtrFromControlBlockPtr(globalState, builder, refM, controlBlockPtrLE);
  auto oldRc = LLVMBuildLoad(builder, rcPtrLE, "oldRc");
  auto newRc = adjustCounter(builder, rcPtrLE, amount);
//  flareAdjustStrongRc(from, globalState, functionState, builder, refM, controlBlockPtrLE, oldRc, newRc);
  return newRc;
}

LLVMValueRef strongRcIsZero(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef controlBlockPtrLE) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      assert(refM->ownership == Ownership::SHARE);
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
    Ref ref) {
  buildPrint(globalState, builder, ref.refLE);
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
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE) {
  buildFlare(FL(), globalState, functionState, builder);
  assert(LLVMTypeOf(virtualArgLE.refLE) == functionState->defaultRegion->translateType(virtualParamMT));
  return getTablePtrFromInterfaceRef(builder, virtualArgLE);
}

LLVMValueRef getObjPtrFromInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE) {
  assert(LLVMTypeOf(virtualArgLE.refLE) == functionState->defaultRegion->translateType(virtualParamMT));
  return LLVMBuildPointerCast(
          builder,
          getControlBlockPtr(builder, virtualArgLE),
          LLVMPointerType(LLVMVoidType(), 0),
          "objAsVoidPtr");
}

Ref buildInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    std::vector<Ref> argRefs,
    int virtualParamIndex,
    int indexInEdge) {
  auto virtualParamMT = prototype->params[virtualParamIndex];
  auto virtualArgRef = argRefs[virtualParamIndex];
  auto virtualArgLE =
      checkValidReference(FL(), globalState, functionState, builder, virtualParamMT, virtualArgRef);

  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef newVirtualArgLE = nullptr;
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      switch (virtualParamMT->ownership) {
        case Ownership::OWN:
        case Ownership::BORROW:
        case Ownership::SHARE: {
          auto virtualArgInterfaceFatPtrLE = InterfaceFatPtrLE(virtualParamMT, virtualArgLE);
          itablePtrLE =
              getItablePtrFromInterfacePtr(
                  globalState, functionState, builder, virtualParamMT, virtualArgInterfaceFatPtrLE);
          auto objVoidPtrLE = getObjPtrFromInterfacePtr(globalState, functionState, builder, virtualParamMT, virtualArgInterfaceFatPtrLE);
          newVirtualArgLE = objVoidPtrLE;
          break;
        }
        case Ownership::WEAK: {
          auto weakFatPtrLE = WeakFatPtrLE(virtualParamMT, virtualArgLE);
          // Disassemble the weak interface ref.
          auto interfaceRefLE =
              InterfaceFatPtrLE(
                  virtualParamMT,
                  FatWeaks().getInnerRefFromWeakRef(
                      globalState, functionState, builder, virtualParamMT, weakFatPtrLE));
          itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);
          // Now, reassemble a weak void* ref to the struct.
          auto weakVoidStructRefLE =
              weakInterfaceRefToWeakStructRef(
                  globalState, functionState, builder, virtualParamMT, weakFatPtrLE);
          newVirtualArgLE = weakVoidStructRefLE.refLE;
          break;
        }
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (virtualParamMT->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE: {
          auto virtualArgInterfaceFatPtrLE = InterfaceFatPtrLE(virtualParamMT, virtualArgLE);
          itablePtrLE = getItablePtrFromInterfacePtr(globalState, functionState, builder, virtualParamMT, virtualArgInterfaceFatPtrLE);
          auto objVoidPtrLE = getObjPtrFromInterfacePtr(globalState, functionState, builder, virtualParamMT, virtualArgInterfaceFatPtrLE);
          newVirtualArgLE = objVoidPtrLE;
          break;
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto virtualArgWeakRef = WeakFatPtrLE(virtualParamMT, virtualArgLE);
          // Disassemble the weak interface ref.
          auto interfaceRefLE =
              InterfaceFatPtrLE(
                  virtualParamMT,
                  FatWeaks().getInnerRefFromWeakRef(
                      globalState, functionState, builder, virtualParamMT, virtualArgWeakRef));
          itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);
          // Now, reassemble a weak void* ref to the struct.
          auto weakVoidStructRefLE =
              weakInterfaceRefToWeakStructRef(
                  globalState, functionState, builder, virtualParamMT, virtualArgWeakRef);
          newVirtualArgLE = weakVoidStructRefLE.refLE;
          break;
        }
      }
      break;
    }
    default:
      assert(false);
  }
  // We can't represent these arguments as refs, because this new virtual arg is a void*, and we
  // can't represent that as a ref.
  std::vector<LLVMValueRef> argsLE;
  for (int i = 0; i < argRefs.size(); i++) {
    argsLE.push_back(
      checkValidReference(
          FL(), globalState, functionState, builder, prototype->params[i], argRefs[i]));
  }
  argsLE[virtualParamIndex] = newVirtualArgLE;

  buildFlare(FL(), globalState, functionState, builder);

  assert(LLVMGetTypeKind(LLVMTypeOf(itablePtrLE)) == LLVMPointerTypeKind);
  auto funcPtrPtrLE =
      LLVMBuildStructGEP(
          builder, itablePtrLE, indexInEdge, "methodPtrPtr");
  buildFlare(FL(), globalState, functionState, builder);

  auto funcPtrLE = LLVMBuildLoad(builder, funcPtrPtrLE, "methodPtr");

  buildFlare(FL(), globalState, functionState, builder);

  auto resultLE =
      LLVMBuildCall(builder, funcPtrLE, argsLE.data(), argsLE.size(), "");
  return wrap(functionState->defaultRegion, prototype->returnType, resultLE);
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

LLVMValueRef checkValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref ref) {
  assert(ref.refM == refM);
  auto refLE = ref.refLE;
  assert(refLE != nullptr);
  assert(LLVMTypeOf(refLE) == functionState->defaultRegion->translateType(refM));
  if (globalState->opt->census) {
    if (refM->ownership == Ownership::OWN) {
      if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
        auto interfaceFatPtrLE = InterfaceFatPtrLE(refM, refLE);
        auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
        buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
      }
      auto controlBlockPtrLE =
          getControlBlockPtr(globalState, functionState, builder, refLE, refM);
      buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
    } else if (refM->ownership == Ownership::SHARE) {
      if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
        auto interfaceFatPtrLE = InterfaceFatPtrLE(refM, refLE);
        auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
        buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
      }
      if (refM->location == Location::INLINE) {
        // Nothing to do, there's no control block or ref counts or anything.
      } else if (refM->location == Location::YONDER) {
        auto controlBlockPtrLE =
            getControlBlockPtr(globalState, functionState, builder, refLE, refM);

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
          if (refM->ownership == Ownership::BORROW) {
            if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
              auto interfaceFatPtrLE = InterfaceFatPtrLE(refM, refLE);
              auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
              buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
            }
            auto controlBlockPtrLE = getControlBlockPtr(globalState, functionState, builder, refLE, refM);
            buildAssertCensusContains(checkerAFL, globalState, functionState, builder,
                controlBlockPtrLE);
          } else if (refM->ownership == Ownership::WEAK) {
            buildCheckWeakRef(checkerAFL, globalState, functionState, builder, refM, ref);
          } else
            assert(false);
          break;
        }
        case RegionOverride::RESILIENT_V0:
        case RegionOverride::RESILIENT_V1:
        case RegionOverride::RESILIENT_V2: {
          buildCheckWeakRef(checkerAFL, globalState, functionState, builder, refM, ref);
          break;
        }
        default:
          assert(false);
      }
    }
  }
  return refLE;
}

Ref buildCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    std::vector<Ref> argRefs) {
  auto funcIter = globalState->functions.find(prototype->name->name);
  assert(funcIter != globalState->functions.end());
  auto funcL = funcIter->second;

  buildFlare(FL(), globalState, functionState, builder, "Suspending function ", functionState->containingFuncM->prototype->name->name);
  buildFlare(FL(), globalState, functionState, builder, "Calling function ", prototype->name->name);

  std::vector<LLVMValueRef> argsLE;
  for (int i = 0; i < argRefs.size(); i++) {
    argsLE.push_back(
        checkValidReference(
            FL(), globalState, functionState, builder, prototype->params[i], argRefs[i]));
  }

  auto resultLE = LLVMBuildCall(builder, funcL, argsLE.data(), argsLE.size(), "");
  auto resultRef = wrap(functionState->defaultRegion, prototype->returnType, resultLE);
  checkValidReference(FL(), globalState, functionState, builder, prototype->returnType, resultRef);

  if (prototype->returnType->referend == globalState->metalCache.never) {
    buildFlare(FL(), globalState, functionState, builder, "Done calling function ", prototype->name->name);
    buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncM->prototype->name->name);
    LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
    return wrap(functionState->defaultRegion, globalState->metalCache.neverRef, globalState->neverPtr);
  } else {
    buildFlare(FL(), globalState, functionState, builder, "Done calling function ", prototype->name->name);
    buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncM->prototype->name->name);
    return resultRef;
  }
}

// Not returning Ref because we might need to wrap it in something else like a weak fat ptr
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

Ref upcast(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructMT,
    StructReferend* sourceStructReferendM,
    Ref sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      switch (sourceStructMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
        case Ownership::BORROW: {
          auto sourceStructWrapperPtrLE =
              WrapperPtrLE(
                  sourceStructMT,
                  checkValidReference(
                      FL(), globalState, functionState, builder, sourceStructMT, sourceRefLE));
          auto resultInterfaceFatPtrLE =
              upcastThinPtr(
                  globalState, functionState, builder, sourceStructMT, sourceStructReferendM,
                  sourceStructWrapperPtrLE, targetInterfaceTypeM, targetInterfaceReferendM);
          return wrap(functionState->defaultRegion, targetInterfaceTypeM, resultInterfaceFatPtrLE);
        }
        case Ownership::WEAK: {
          auto sourceWeakStructFatPtrLE =
              WeakFatPtrLE(
                  sourceStructMT,
                  checkValidReference(
                      FL(), globalState, functionState, builder, sourceStructMT, sourceRefLE));
          auto sourceWeakInterfaceFatPtrLE =
              upcastWeakFatPtr(
                  globalState, functionState, builder, sourceStructMT, sourceStructReferendM,
                  sourceWeakStructFatPtrLE, targetInterfaceTypeM, targetInterfaceReferendM);
          return wrap(
              functionState->defaultRegion, targetInterfaceTypeM, sourceWeakInterfaceFatPtrLE);
        }
        default:
          assert(false);
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (sourceStructMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN: {
          auto sourceStructWrapperPtrLE =
              WrapperPtrLE(
                  sourceStructMT,
                  checkValidReference(
                      FL(), globalState, functionState, builder, sourceStructMT, sourceRefLE));
          auto resultInterfaceFatPtrLE =
              upcastThinPtr(
                  globalState, functionState, builder, sourceStructMT, sourceStructReferendM,
                  sourceStructWrapperPtrLE, targetInterfaceTypeM, targetInterfaceReferendM);
          return wrap(functionState->defaultRegion, targetInterfaceTypeM, resultInterfaceFatPtrLE);
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto sourceWeakStructPtrLE =
              WeakFatPtrLE(
                  sourceStructMT,
                  checkValidReference(
                      FL(), globalState, functionState, builder, sourceStructMT, sourceRefLE));
          auto resultWeakInterfaceFatPtrLE =
              upcastWeakFatPtr(
                  globalState, functionState, builder, sourceStructMT, sourceStructReferendM,
                  sourceWeakStructPtrLE, targetInterfaceTypeM, targetInterfaceReferendM);
          return wrap(
              functionState->defaultRegion, targetInterfaceTypeM, resultWeakInterfaceFatPtrLE);
        }
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
Ref upgradeLoadResultToRefWithTargetOwnership(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    LLVMValueRef sourceRefLE) {
  auto sourceOwnership = sourceType->ownership;
  auto sourceLocation = sourceType->location;
  auto targetOwnership = targetType->ownership;
  auto targetLocation = targetType->location;
//  assert(sourceLocation == targetLocation); // unimplemented

  {
    // Careful here! This is a bit cheaty; we shouldn't pretend we have the source reference,
    // because we don't. We're *reading* from it, but by wrapping it, we're pretending we *have* it.
    // We're only doing this here so we can feed it to checkValidReference, and immediately throwing
    // it away.
    auto sourceRef = wrap(functionState->defaultRegion, sourceType, sourceRefLE);
    checkValidReference(FL(), globalState, functionState, builder, sourceType, sourceRef);
  }

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      if (sourceOwnership == Ownership::SHARE) {
        if (sourceLocation == Location::INLINE) {
          return wrap(functionState->defaultRegion, targetType, sourceRefLE);
        } else {
          auto resultRefLE = sourceRefLE;

          return wrap(functionState->defaultRegion, targetType, resultRefLE);
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
          return wrap(functionState->defaultRegion, targetType, sourceRefLE);
        } else if (targetOwnership == Ownership::BORROW) {
          auto resultRefLE = sourceRefLE;
          // We do the same thing for inline and yonder muts, the only difference is
          // where the memory lives.

          auto resultRef = wrap(functionState->defaultRegion, targetType, resultRefLE);
          checkValidReference(
              FL(), globalState, functionState, builder, targetType, resultRef);
          return resultRef;
        } else if (targetOwnership == Ownership::WEAK) {
          // Now we need to package it up into a weak ref.
          if (auto structReferend = dynamic_cast<StructReferend*>(sourceType->referend)) {
            auto sourceWrapperPtrLE = WrapperPtrLE(sourceType, sourceRefLE);
            auto resultLE =
                assembleStructWeakRef(
                    globalState, functionState, builder, sourceType, targetType, structReferend, sourceWrapperPtrLE);
            return wrap(functionState->defaultRegion, targetType, resultLE);
          } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(sourceType->referend)) {
            auto sourceInterfaceFatPtrLE = InterfaceFatPtrLE(sourceType, sourceRefLE);
            auto resultLE =
                assembleInterfaceWeakRef(
                    globalState, functionState, builder, sourceType, targetType, interfaceReferendM, sourceInterfaceFatPtrLE);
            return wrap(functionState->defaultRegion, targetType, resultLE);
          } else if (auto knownSizeArray = dynamic_cast<KnownSizeArrayT*>(sourceType->referend)) {
            auto sourceWrapperPtrLE = WrapperPtrLE(sourceType, sourceRefLE);
            auto resultLE =
                assembleKnownSizeArrayWeakRef(
                    globalState, functionState, builder, sourceType, knownSizeArray, targetType, sourceWrapperPtrLE);
            return wrap(functionState->defaultRegion, targetType, resultLE);
          } else if (auto unknownSizeArray = dynamic_cast<UnknownSizeArrayT*>(sourceType->referend)) {
            auto sourceWrapperPtrLE = WrapperPtrLE(sourceType, sourceRefLE);
            auto resultLE =
                assembleUnknownSizeArrayWeakRef(
                    globalState, functionState, builder, sourceType, unknownSizeArray, targetType, sourceWrapperPtrLE);
            return wrap(functionState->defaultRegion, targetType, resultLE);
          } else assert(false);
        } else {
          assert(false);
        }
      } else if (sourceOwnership == Ownership::BORROW) {
        buildFlare(FL(), globalState, functionState, builder);

        if (targetOwnership == Ownership::OWN) {
          assert(false); // Cant load an owning reference from a constraint ref local.
        } else if (targetOwnership == Ownership::BORROW) {
          auto resultRefLE = sourceRefLE;
          // We do the same thing for inline and yonder muts, the only difference is
          // where the memory lives.

          auto resultRef = wrap(functionState->defaultRegion, targetType, resultRefLE);
          checkValidReference(
              FL(), globalState, functionState, builder, targetType, resultRef);
          return resultRef;
        } else if (targetOwnership == Ownership::WEAK) {
          // Making a weak ref from a constraint ref local.

          if (auto structReferendM = dynamic_cast<StructReferend*>(sourceType->referend)) {
            // We do the same thing for inline and yonder muts, the only difference is
            // where the memory lives.
            auto sourceWrapperPtrLE = WrapperPtrLE(sourceType, sourceRefLE);
            auto resultLE =
                assembleStructWeakRef(
                    globalState, functionState, builder, sourceType, targetType, structReferendM,
                    sourceWrapperPtrLE);
            return wrap(functionState->defaultRegion, targetType, resultLE);
          } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(sourceType->referend)) {
            auto sourceInterfaceFatPtrLE = InterfaceFatPtrLE(sourceType, sourceRefLE);
            auto resultLE =
                assembleInterfaceWeakRef(
                    globalState, functionState, builder, sourceType, targetType, interfaceReferendM, sourceInterfaceFatPtrLE);
            return wrap(functionState->defaultRegion, targetType, resultLE);
          } else assert(false);
        } else {
          assert(false);
        }
      } else if (sourceOwnership == Ownership::WEAK) {
        assert(targetOwnership == Ownership::WEAK);
        auto resultRefLE = sourceRefLE;
        auto resultRef = wrap(functionState->defaultRegion, targetType, resultRefLE);
        checkValidReference(
            FL(), globalState, functionState, builder, targetType, resultRef);
        return resultRef;
      } else {
        assert(false);
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      if (sourceOwnership == Ownership::SHARE) {
        if (sourceLocation == Location::INLINE) {
          auto resultRefLE = sourceRefLE;
          auto resultRef = wrap(functionState->defaultRegion, targetType, resultRefLE);
          checkValidReference(
              FL(), globalState, functionState, builder, targetType, resultRef);
          return resultRef;
        } else {
          auto resultRefLE = sourceRefLE;

          auto resultRef = wrap(functionState->defaultRegion, targetType, resultRefLE);
          checkValidReference(
              FL(), globalState, functionState, builder, targetType, resultRef);
          return resultRef;
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
          auto resultRefLE = sourceRefLE;
          auto resultRef = wrap(functionState->defaultRegion, targetType, resultRefLE);
          checkValidReference(
              FL(), globalState, functionState, builder, targetType, resultRef);
          return resultRef;
        } else if (targetOwnership == Ownership::BORROW
            || targetOwnership == Ownership::WEAK) {
          // Now we need to package it up into a weak ref.
          if (auto structReferend = dynamic_cast<StructReferend*>(sourceType->referend)) {
            auto sourceWrapperPtrLE = WrapperPtrLE(sourceType, sourceRefLE);
            auto resultLE =
                assembleStructWeakRef(
                    globalState, functionState, builder, sourceType, targetType, structReferend, sourceWrapperPtrLE);
            return wrap(functionState->defaultRegion, targetType, resultLE);
          } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(sourceType->referend)) {
            auto sourceInterfaceFatPtrLE = InterfaceFatPtrLE(sourceType, sourceRefLE);
            auto resultLE =
                assembleInterfaceWeakRef(
                    globalState, functionState, builder, sourceType, targetType, interfaceReferendM, sourceInterfaceFatPtrLE);
            return wrap(functionState->defaultRegion, targetType, resultLE);
          } else if (auto knownSizeArray = dynamic_cast<KnownSizeArrayT*>(sourceType->referend)) {
            auto sourceWrapperPtrLE = WrapperPtrLE(sourceType, sourceRefLE);
            auto resultLE =
                assembleKnownSizeArrayWeakRef(
                    globalState, functionState, builder, sourceType, knownSizeArray, targetType, sourceWrapperPtrLE);
            return wrap(functionState->defaultRegion, targetType, resultLE);
          } else if (auto unknownSizeArray = dynamic_cast<UnknownSizeArrayT*>(sourceType->referend)) {
            auto sourceWrapperPtrLE = WrapperPtrLE(sourceType, sourceRefLE);
            auto resultLE =
                assembleUnknownSizeArrayWeakRef(
                    globalState, functionState, builder, sourceType, unknownSizeArray, targetType, sourceWrapperPtrLE);
            return wrap(functionState->defaultRegion, targetType, resultLE);
          } else assert(false);
        } else {
          assert(false);
        }
      } else if (sourceOwnership == Ownership::BORROW || sourceOwnership == Ownership::WEAK) {
        assert(targetOwnership == Ownership::BORROW || targetOwnership == Ownership::WEAK);

        auto resultRefLE = sourceRefLE;
        auto resultRef = wrap(functionState->defaultRegion, targetType, resultRefLE);
        checkValidReference(
            FL(), globalState, functionState, builder, targetType, resultRef);
        return resultRef;
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
