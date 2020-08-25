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
    Reference* refM) {
  if (dynamic_cast<InterfaceReferend*>(refM->referend)) {
    return getControlBlockPtrFromInterfaceRef(builder, referenceLE);
  } else if (dynamic_cast<StructReferend*>(refM->referend)) {
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<KnownSizeArrayT*>(refM->referend)) {
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<UnknownSizeArrayT*>(refM->referend)) {
    return getConcreteControlBlockPtr(builder, referenceLE);
  } else if (dynamic_cast<Str*>(refM->referend)) {
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
  auto controlBlockPtrLE = getControlBlockPtr(builder, exprLE, refM);
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

  LLVMValueRef itablePtrLE;
  switch (virtualParamMT->ownership) {
    case Ownership::OWN:
    case Ownership::BORROW:
    case Ownership::SHARE: {
      itablePtrLE = getTablePtrFromInterfaceRef(builder, virtualArgLE);

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
      // Disassemble the weak interface ref.
      auto interfaceRefLE =
          getInnerRefFromWeakRef(
              globalState,
              functionState,
              builder,
              virtualParamMT,
              virtualArgLE);

      itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);

      // Now, reassemble a weak void* ref to the struct.
      auto weakVoidStructRefLE =
          weakInterfaceRefToWeakStructRef(
              globalState, functionState, builder, virtualParamMT, virtualArgLE);

      argExprsLE[virtualParamIndex] = weakVoidStructRefLE;

      break;
    }
  }

  assert(LLVMGetTypeKind(LLVMTypeOf(itablePtrLE)) == LLVMPointerTypeKind);
  auto funcPtrPtrLE =
      LLVMBuildStructGEP(
          builder, itablePtrLE, indexInEdge, "methodPtrPtr");
  auto funcPtrLE = LLVMBuildLoad(builder, funcPtrPtrLE, "methodPtr");

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
    LLVMValueRef refLE) {
  LLVMValueRef resultAsVoidPtrLE =
      LLVMBuildPointerCast(
          builder, refLE, LLVMPointerType(LLVMVoidType(), 0), "");
  auto isRegisteredIntLE = LLVMBuildCall(builder, globalState->censusContains, &resultAsVoidPtrLE, 1, "");
  auto isRegisteredBoolLE = LLVMBuildTruncOrBitCast(builder,  isRegisteredIntLE, LLVMInt1Type(), "");
  buildAssert(globalState, functionState, builder, isRegisteredBoolLE, "Object not registered with census!");
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
      auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM);
      buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
    } else if (refM->ownership == Ownership::SHARE) {
      if (refM->location == Location::INLINE) {
        // Nothing to do, there's no control block or ref counts or anything.
      } else if (refM->location == Location::YONDER) {
        auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM);

        // We dont check ref count >0 because imm destructors receive with rc=0.
  //      auto rcLE = getRcFromControlBlockPtr(globalState, builder, controlBlockPtrLE);
  //      auto rcPositiveLE = LLVMBuildICmp(builder, LLVMIntSGT, rcLE, constI64LE(0), "");
  //      buildAssert(checkerAFL, globalState, functionState, blockState, builder, rcPositiveLE, "Invalid RC!");

        buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
      } else assert(false);
    } else if (refM->ownership == Ownership::BORROW) {
      auto controlBlockPtrLE = getControlBlockPtr(builder, refLE, refM);
      buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
    } else if (refM->ownership == Ownership::WEAK) {
      buildCheckWeakRef(globalState, builder, refLE);
    } else assert(false);
  } else {
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
  interfaceRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceRefLE,
          globalState->getInterfaceTablePtr(
              globalState->program->getStruct(sourceStructReferendM->fullName)
                  ->getEdgeForInterface(targetInterfaceReferendM->fullName)),
          1,
          "interfaceRef");

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
              globalState, builder, sourceStructReferendM, targetInterfaceReferendM,
              getControlBlockPtr(builder, sourceRefLE, sourceStructTypeM));
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
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
      return Ownership::WEAK;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_FAST) {
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
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
      // All mutables are weakabile in resilient mode
      return Weakability::WEAKABLE;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_FAST) {
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
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
      // All mutable structs are weakability in resilient mode
      return Weakability::WEAKABLE;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_FAST) {
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
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
      // All mutable structs are weakable in resilient mode
      return Weakability::WEAKABLE;
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_FAST) {
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

  buildFlare(FL(), globalState, functionState, builder);
  checkValidReference(FL(), globalState, functionState, builder, sourceType, sourceRefLE);

  buildFlare(FL(), globalState, functionState, builder);
  if (sourceOwnership == Ownership::SHARE) {
    if (sourceLocation == Location::INLINE) {
      buildFlare(FL(), globalState, functionState, builder);
      return sourceRefLE;
    } else {
      buildFlare(FL(), globalState, functionState, builder);
      auto resultRefLE = sourceRefLE;

      return resultRefLE;
    }
  } else if (sourceOwnership == Ownership::OWN) {
    if (targetOwnership == Ownership::OWN) {
      buildFlare(FL(), globalState, functionState, builder);
      // Cant load an owning reference from a owning local. That would require an unstackify.
      assert(false);
    } else if (targetOwnership == Ownership::BORROW) {
      buildFlare(FL(), globalState, functionState, builder);
      auto resultRefLE = sourceRefLE;
      // We do the same thing for inline and yonder muts, the only difference is
      // where the memory lives.

      checkValidReference(
          FL(), globalState, functionState, builder, targetType, resultRefLE);

      return resultRefLE;
    } else if (targetOwnership == Ownership::WEAK) {
      buildFlare(FL(), globalState, functionState, builder);
      // Now we need to package it up into a weak ref.
      if (auto structReferend = dynamic_cast<StructReferend*>(sourceType->referend)) {
        buildFlare(FL(), globalState, functionState, builder);
        auto weakRefLE =
            assembleStructWeakRef(
                globalState, functionState, builder, sourceType, structReferend, sourceRefLE);
        buildFlare(FL(), globalState, functionState, builder);
        return weakRefLE;
      } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(sourceType->referend)) {
        buildFlare(FL(), globalState, functionState, builder);
        auto weakRefLE =
            assembleInterfaceWeakRef(
                globalState, functionState, builder, sourceType, interfaceReferendM, sourceRefLE);
        return weakRefLE;
      } else if (auto knownSizeArray = dynamic_cast<KnownSizeArrayT*>(sourceType->referend)) {
        buildFlare(FL(), globalState, functionState, builder);
        auto weakRefLE =
            assembleKnownSizeArrayWeakRef(
                globalState, builder, sourceType, knownSizeArray, sourceRefLE);
        return weakRefLE;
      } else if (auto unknownSizeArray = dynamic_cast<UnknownSizeArrayT*>(sourceType->referend)) {
        buildFlare(FL(), globalState, functionState, builder);
        auto weakRefLE =
            assembleUnknownSizeArrayWeakRef(
                globalState, functionState, builder, sourceType, unknownSizeArray, sourceRefLE);
        buildFlare(FL(), globalState, functionState, builder);
        return weakRefLE;
      } else assert(false);
    } else {
      assert(false);
    }
  } else if (sourceOwnership == Ownership::BORROW) {
    buildFlare(FL(), globalState, functionState, builder);

    if (targetOwnership == Ownership::OWN) {
      buildFlare(FL(), globalState, functionState, builder);
      assert(false); // Cant load an owning reference from a constraint ref local.
    } else if (targetOwnership == Ownership::BORROW) {
      buildFlare(FL(), globalState, functionState, builder);
      auto resultRefLE = sourceRefLE;
      // We do the same thing for inline and yonder muts, the only difference is
      // where the memory lives.

      return resultRefLE;
    } else if (targetOwnership == Ownership::WEAK) {
      buildFlare(FL(), globalState, functionState, builder);
      // Making a weak ref from a constraint ref local.

      if (auto structReferendM = dynamic_cast<StructReferend*>(sourceType->referend)) {
        buildFlare(FL(), globalState, functionState, builder);
        // We do the same thing for inline and yonder muts, the only difference is
        // where the memory lives.
        auto weakRefLE =
            assembleStructWeakRef(
                globalState, functionState, builder, sourceType, structReferendM, sourceRefLE);
        return weakRefLE;
      } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(sourceType->referend)) {
        buildFlare(FL(), globalState, functionState, builder);
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
      buildFlare(FL(), globalState, functionState, builder);
      assert(false); // Cant load an owning reference from a weak ref local.
    } else if (targetOwnership == Ownership::BORROW) {
      buildFlare(FL(), globalState, functionState, builder);
      assert(false); // Can't implicitly make a constraint ref from a weak ref.
    } else if (targetOwnership == Ownership::WEAK) {
      buildFlare(FL(), globalState, functionState, builder);
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
