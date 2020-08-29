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

void makeLocal(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Local* local,
    LLVMValueRef valueToStore) {
  auto localAddr =
      LLVMBuildAlloca(
          builder,
          translateType(globalState, getEffectiveType(globalState, local->type)),
          local->id->maybeName.c_str());
  blockState->addLocal(local->id, localAddr);
  LLVMBuildStore(builder, valueToStore, localAddr);
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

void flareAdjustStrongRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef controlBlockPtr,
    LLVMValueRef oldAmount,
    LLVMValueRef newAmount) {
  if (globalState->opt->census) {
    std::cout << "reenable census flare" << std::endl;
//    buildFlare(
//        from,
//        globalState,
//        functionState,
//        builder,
//        typeid(*refM->referend).name(),
//        " ",
//        getTypeNameStrPtrFromControlBlockPtr(globalState, builder, controlBlockPtr),
//        getObjIdFromControlBlockPtr(globalState, builder, controlBlockPtr),
//        ", ",
//        oldAmount,
//        "->",
//        newAmount);
  }
}

// Returns the new RC
LLVMValueRef adjustStrongRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE,
    Reference* refM,
    int amount) {
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
    Reference* refM,
    LLVMValueRef controlBlockPtrLE) {
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

LLVMValueRef buildInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    std::vector<LLVMValueRef> argExprsLE,
    int virtualParamIndex,
    int indexInEdge) {
  auto virtualArgLE = argExprsLE[virtualParamIndex];

  LLVMValueRef itablePtrLE = nullptr;
  switch (virtualParamMT->ownership) {
    case Ownership::OWN:
    case Ownership::BORROW:
    case Ownership::SHARE: {
      buildFlare(FL(), globalState, functionState, builder);

      assert(LLVMTypeOf(virtualArgLE) == translateType(globalState, virtualParamMT));

      itablePtrLE = getTablePtrFromInterfaceRef(builder, virtualArgLE);

      buildFlare(FL(), globalState, functionState, builder, "itable: ", ptrToVoidPtrLE(builder, itablePtrLE));

      auto objVoidPtrLE =
          LLVMBuildPointerCast(
              builder,
              getControlBlockPtrFromInterfaceRef(builder, virtualArgLE),
              LLVMPointerType(LLVMVoidType(), 0),
              "objAsVoidPtr");
      argExprsLE[virtualParamIndex] = objVoidPtrLE;
      break;
    }
    case Ownership::WEAK: {
      buildFlare(FL(), globalState, functionState, builder);

      // Disassemble the weak interface ref.
      auto interfaceRefLE =
          getInnerRefFromWeakRef(
              globalState,
              functionState,
              builder,
              virtualParamMT,
              virtualArgLE);

      buildFlare(FL(), globalState, functionState, builder);

      itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);

      buildFlare(FL(), globalState, functionState, builder);

      // Now, reassemble a weak void* ref to the struct.
      auto weakVoidStructRefLE =
          weakInterfaceRefToWeakStructRef(
              globalState, functionState, builder, virtualParamMT, virtualArgLE);

      buildFlare(FL(), globalState, functionState, builder);

      argExprsLE[virtualParamIndex] = weakVoidStructRefLE;

      buildFlare(FL(), globalState, functionState, builder);

      break;
    }
  }

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

LLVMValueRef makeConstExpr(LLVMBuilderRef builder, LLVMValueRef constExpr) {
  auto localAddr = LLVMBuildAlloca(builder, LLVMTypeOf(constExpr), "");
  LLVMBuildStore(builder,constExpr,localAddr);
  return LLVMBuildLoad(builder, localAddr, "");
}

LLVMValueRef makeConstIntExpr(LLVMBuilderRef builder, LLVMTypeRef type, int value) {
  return makeConstExpr(builder, LLVMConstInt(type, value, false));
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
    Reference* refM,
    LLVMValueRef refLE) {
  assert(refLE != nullptr);
  assert(LLVMTypeOf(refLE) == translateType(globalState, refM));
  if (globalState->opt->census) {
    if (refM->ownership == Ownership::OWN) {
      if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(refM->referend)) {
        auto itablePtrLE = getTablePtrFromInterfaceRef(builder, refLE);
        buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
      }
      auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM->referend);
      buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
    } else if (refM->ownership == Ownership::SHARE) {
      if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(refM->referend)) {
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

        buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
      } else assert(false);
    } else if (refM->ownership == Ownership::BORROW) {
      if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(refM->referend)) {
        auto itablePtrLE = getTablePtrFromInterfaceRef(builder, refLE);
        buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
      }
      auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM->referend);
      buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
    } else if (refM->ownership == Ownership::WEAK) {
      buildCheckWeakRef(checkerAFL, globalState, functionState, builder, refM, refLE);
    } else assert(false);
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
  checkValidReference(FL(), globalState, functionState, builder, getEffectiveType(globalState, prototype->returnType), resultLE);

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

LLVMValueRef upcast2(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    LLVMValueRef sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  assert(sourceStructTypeM->location != Location::INLINE);

  switch (targetInterfaceTypeM->ownership) {
    case Ownership::OWN:
    case Ownership::BORROW:
    case Ownership::SHARE: {
      auto interfaceRefLE =
          makeInterfaceRefStruct(
              globalState, functionState, builder, sourceStructReferendM, targetInterfaceReferendM,
              getControlBlockPtr(builder, sourceRefLE, sourceStructTypeM->referend));
      checkValidReference(
          FL(), globalState, functionState, builder, targetInterfaceTypeM, interfaceRefLE);
      return interfaceRefLE;
    }
    case Ownership::WEAK: {
      return weakStructRefToWeakInterfaceRef(
          globalState,
          functionState,
          builder,
          sourceRefLE,
          sourceStructReferendM,
          sourceStructTypeM,
          targetInterfaceReferendM,
          targetInterfaceTypeM);
    }
    default:
      assert(false);
      return nullptr;
  }
}

// TODO move into region classes
Ownership getEffectiveOwnership(GlobalState* globalState, UnconvertedOwnership ownership) {
  if (ownership == UnconvertedOwnership::SHARE) {
    return Ownership::SHARE;
  } else if (ownership == UnconvertedOwnership::OWN) {
    return Ownership::OWN;
  } else if (ownership == UnconvertedOwnership::WEAK) {
    return Ownership::WEAK;
  } else if (ownership == UnconvertedOwnership::BORROW) {
    if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
        globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
      return Ownership::BORROW;
    } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
      return Ownership::BORROW;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
      return Ownership::WEAK;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
      return Ownership::WEAK;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
      return Ownership::WEAK;
    } else assert(false);
  } else assert(false);
}

// TODO move into region classes
Reference* getEffectiveType(GlobalState* globalState, UnconvertedReference* refM) {
  return globalState->metalCache.getReference(
      getEffectiveOwnership(globalState, refM->ownership),
      refM->location,
      refM->referend);
}

// TODO move into region classes
std::vector<Reference*> getEffectiveTypes(GlobalState* globalState, std::vector<UnconvertedReference*> refsM) {
  std::vector<Reference*> result;
  for (auto thing : refsM) {
    result.push_back(getEffectiveType(globalState, thing));
  }
  return result;
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
// Loads from either a local or a member, and does the appropriate casting.
LLVMValueRef load(
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

  checkValidReference(FL(), globalState, functionState, builder, sourceType, sourceRefLE);

  if (sourceOwnership == Ownership::SHARE) {
    if (sourceLocation == Location::INLINE) {
      return sourceRefLE;
    } else {
      auto resultRefLE = sourceRefLE;

      return resultRefLE;
    }
  } else if (sourceOwnership == Ownership::OWN) {
    if (targetOwnership == Ownership::OWN) {
      // Cant load an owning reference from a owning local. That would require an unstackify.
      assert(false);
    } else if (targetOwnership == Ownership::BORROW) {
      auto resultRefLE = sourceRefLE;
      // We do the same thing for inline and yonder muts, the only difference is
      // where the memory lives.

      checkValidReference(
          FL(), globalState, functionState, builder, targetType, resultRefLE);

      return resultRefLE;
    } else if (targetOwnership == Ownership::WEAK) {
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
  } else if (sourceOwnership == Ownership::BORROW) {
    buildFlare(FL(), globalState, functionState, builder);

    if (targetOwnership == Ownership::OWN) {
      assert(false); // Cant load an owning reference from a constraint ref local.
    } else if (targetOwnership == Ownership::BORROW) {
      auto resultRefLE = sourceRefLE;
      // We do the same thing for inline and yonder muts, the only difference is
      // where the memory lives.

      return resultRefLE;
    } else if (targetOwnership == Ownership::WEAK) {
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
  } else if (sourceOwnership == Ownership::WEAK) {
    if (targetOwnership == Ownership::OWN) {
      assert(false); // Cant load an owning reference from a weak ref local.
    } else if (targetOwnership == Ownership::BORROW) {
      assert(false); // Can't implicitly make a constraint ref from a weak ref.
    } else if (targetOwnership == Ownership::WEAK) {
      auto resultRefLE = sourceRefLE;

      // We do the same thing for inline and yonder muts, the only difference is
      // where the memory lives.
      return resultRefLE;
    } else {
      assert(false);
    }
  } else {
    assert(false);
  }
}

LLVMValueRef addExtern(LLVMModuleRef mod, const std::string& name, LLVMTypeRef retType, std::vector<LLVMTypeRef> paramTypes) {
  LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
  return LLVMAddFunction(mod, name.c_str(), funcType);
}
