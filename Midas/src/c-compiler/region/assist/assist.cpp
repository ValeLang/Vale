#include <utils/branch.h>
#include <region/common/fatweaks/fatweaks.h>
#include <region/common/hgm/hgm.h>
#include <region/common/lgtweaks/lgtweaks.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include <translatetype.h>
#include <region/common/common.h>
#include <region/common/heap.h>
#include <sstream>
#include <function/expressions/shared/elements.h>
#include <utils/counters.h>
#include "assist.h"

Assist::Assist(GlobalState* globalState_) :
    globalState(globalState_),
    mutNonWeakableStructs(globalState, makeAssistAndNaiveRCNonWeakableControlBlock(globalState)),
    mutWeakableStructs(
        globalState,
        makeAssistAndNaiveRCWeakableControlBlock(globalState),
        WrcWeaks::makeWeakRefHeaderStruct(globalState)),
    referendStructs(
        globalState,
        [this](Referend* referend) -> IReferendStructsSource* {
          if (globalState->getReferendWeakability(referend) == Weakability::NON_WEAKABLE) {
            return &mutNonWeakableStructs;
          } else {
            return &mutWeakableStructs;
          }
        }),
    weakRefStructs(
        [this](Referend* referend) -> IWeakRefStructsSource* {
          if (globalState->getReferendWeakability(referend) == Weakability::NON_WEAKABLE) {
            assert(false);
          } else {
            return &mutWeakableStructs;
          }
        }),
    fatWeaks(globalState_, &weakRefStructs),
    wrcWeaks(globalState_, &referendStructs, &weakRefStructs) {
  regionLT = LLVMStructCreateNamed(globalState->context, "__Assist_Region");
  LLVMStructSetBody(regionLT, nullptr, 0, false);
}

RegionId* Assist::getRegionId() {
  return globalState->metalCache->assistRegionId;
}

void Assist::mainSetup(FunctionState* functionState, LLVMBuilderRef builder) {
  wrcWeaks.mainSetup(functionState, builder);
}

void Assist::mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) {
  wrcWeaks.mainCleanup(functionState, builder);
}

void Assist::alias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    Ref ref) {
  auto sourceRnd = sourceRef->referend;
  buildFlare(FL(), globalState, functionState, builder);
  if (dynamic_cast<Int *>(sourceRnd) ||
      dynamic_cast<Bool *>(sourceRnd) ||
      dynamic_cast<Float *>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (dynamic_cast<InterfaceReferend *>(sourceRnd) ||
      dynamic_cast<StructReferend *>(sourceRnd) ||
      dynamic_cast<KnownSizeArrayT *>(sourceRnd) ||
      dynamic_cast<UnknownSizeArrayT *>(sourceRnd) ||
      dynamic_cast<Str *>(sourceRnd)) {
    if (sourceRef->ownership == Ownership::OWN) {
      // This can happen if we just allocated something. It's RC is already zero, and we want to
      // bump it to 1 for the owning reference.
      adjustStrongRc(from, globalState, functionState, &referendStructs, builder, ref, sourceRef, 1);
    } else if (sourceRef->ownership == Ownership::BORROW) {
      adjustStrongRc(from, globalState, functionState, &referendStructs, builder, ref, sourceRef, 1);
    } else if (sourceRef->ownership == Ownership::WEAK) {
      aliasWeakRef(from, functionState, builder, sourceRef, ref);
    } else if (sourceRef->ownership == Ownership::SHARE) {
      if (sourceRef->location == Location::INLINE) {
        // Do nothing, we can just let inline structs disappear
      } else {
        adjustStrongRc(from, globalState, functionState, &referendStructs, builder, ref, sourceRef, 1);
      }
    } else
      assert(false);
  } else {
    std::cerr << "Unimplemented type in alias: "
        << typeid(*sourceRef->referend).name() << std::endl;
    assert(false);
  }
  buildFlare(FL(), globalState, functionState, builder);
}


void Assist::dealias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  if (sourceMT->ownership == Ownership::SHARE) {
    assert(false);
  } else if (sourceMT->ownership == Ownership::OWN) {
    // This can happen if we're sending an owning reference to the outside world, see DEPAR.
    adjustStrongRc(from, globalState, functionState, &referendStructs, builder, sourceRef, sourceMT, -1);
  } else if (sourceMT->ownership == Ownership::BORROW) {
    adjustStrongRc(from, globalState, functionState, &referendStructs, builder, sourceRef, sourceMT, -1);
  } else if (sourceMT->ownership == Ownership::WEAK) {
    discardWeakRef(from, functionState, builder, sourceMT, sourceRef);
  } else assert(false);
}

Ref Assist::lockWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference* resultOptTypeM,
    Reference* constraintRefM,
    Reference* sourceWeakRefMT,
    Ref sourceWeakRef,
    bool sourceKnownLive,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {
  assert(sourceWeakRefMT->ownership == Ownership::WEAK);
  auto isAliveLE =
      getIsAliveFromWeakRef(
          functionState, builder, sourceWeakRefMT, sourceWeakRef, sourceKnownLive);
  auto resultOptTypeLE = translateType(resultOptTypeM);
  return regularInnerLockWeak(
      globalState, functionState, builder, thenResultIsNever, elseResultIsNever, resultOptTypeM,
      constraintRefM, sourceWeakRefMT, sourceWeakRef, buildThen, buildElse,
      isAliveLE, resultOptTypeLE, &weakRefStructs, &fatWeaks);
}

LLVMTypeRef Assist::translateType(Reference* referenceM) {
  switch (referenceM->ownership) {
    case Ownership::SHARE:
      assert(false);
    case Ownership::OWN:
    case Ownership::BORROW:
      assert(referenceM->location != Location::INLINE);
      return translateReferenceSimple(globalState, &referendStructs, referenceM->referend);
    case Ownership::WEAK:
      assert(referenceM->location != Location::INLINE);
      return translateWeakReference(globalState, &weakRefStructs, referenceM->referend);
    default:
      assert(false);
  }
}

Ref Assist::upcastWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {
  return wrap(
      this,
      targetInterfaceTypeM,
      wrcWeaks.weakStructPtrToWrciWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
          sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM));
}

void Assist::declareKnownSizeArray(
    KnownSizeArrayDefinitionT* knownSizeArrayMT) {
  globalState->regionIdByReferend.emplace(knownSizeArrayMT->referend, getRegionId());
  referendStructs.declareKnownSizeArray(knownSizeArrayMT);
}

void Assist::declareUnknownSizeArray(
    UnknownSizeArrayDefinitionT* unknownSizeArrayMT) {
  globalState->regionIdByReferend.emplace(unknownSizeArrayMT->referend, getRegionId());
  referendStructs.declareUnknownSizeArray(unknownSizeArrayMT);
}

void Assist::defineUnknownSizeArray(
    UnknownSizeArrayDefinitionT* unknownSizeArrayMT) {
  auto elementLT =
      globalState->getRegion(unknownSizeArrayMT->rawArray->elementType)
          ->translateType(unknownSizeArrayMT->rawArray->elementType);
  referendStructs.defineUnknownSizeArray(unknownSizeArrayMT, elementLT);
}

void Assist::defineKnownSizeArray(
    KnownSizeArrayDefinitionT* knownSizeArrayMT) {
  auto elementLT =
      globalState->getRegion(knownSizeArrayMT->rawArray->elementType)
          ->translateType(knownSizeArrayMT->rawArray->elementType);
  referendStructs.defineKnownSizeArray(knownSizeArrayMT, elementLT);
}

void Assist::declareStruct(
    StructDefinition* structM) {
  globalState->regionIdByReferend.emplace(structM->referend, getRegionId());
  referendStructs.declareStruct(structM->referend);
}

void Assist::defineStruct(
    StructDefinition* structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        globalState->getRegion(structM->members[i]->type)
            ->translateType(structM->members[i]->type));
  }
  referendStructs.defineStruct(
      structM->referend,
      innerStructMemberTypesL);
}

void Assist::declareEdge(
    Edge* edge) {
  referendStructs.declareEdge(edge);
}

void Assist::defineEdge(Edge* edge) {
  auto interfaceFunctionsLT = globalState->getInterfaceFunctionTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  referendStructs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);
}

void Assist::declareInterface(
    InterfaceDefinition* interfaceM) {
  globalState->regionIdByReferend.emplace(interfaceM->referend, getRegionId());
  referendStructs.declareInterface(interfaceM);
}

void Assist::defineInterface(
    InterfaceDefinition* interfaceM) {
  auto interfaceMethodTypesL = globalState->getInterfaceFunctionTypes(interfaceM->referend);
  referendStructs.defineInterface(interfaceM, interfaceMethodTypesL);
}

Ref Assist::weakAlias(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef) {
  return regularWeakAlias(globalState, functionState, &referendStructs, &wrcWeaks, builder, sourceRefMT, targetRefMT, sourceRef);
}

void Assist::discardOwningRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto exprWrapperPtrLE =
      referendStructs.makeWrapperPtr(
          FL(), functionState, builder, sourceMT,
          checkValidReference(FL(), functionState, builder, sourceMT, sourceRef));

  adjustStrongRc(
      AFL("Destroy decrementing the owning ref"),
      globalState, functionState, &referendStructs, builder, sourceRef, sourceMT, -1);
  // No need to check the RC, we know we're freeing right now.

  // Free it!v
  deallocate(AFL("discardOwningRef"), functionState, builder, sourceMT, sourceRef);
}

void Assist::noteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtrLE) {
  auto rc = referendStructs.getStrongRcFromControlBlockPtr(builder, refM, controlBlockPtrLE);
  auto conditionLE = LLVMBuildICmp(builder, LLVMIntEQ, rc, constI32LE(globalState, 0), "assertCondition");
  buildIf(
      globalState, functionState, builder, isZeroLE(builder, conditionLE),
      [this](LLVMBuilderRef thenBuilder) {
        buildPrint(globalState, thenBuilder, "Error: Dangling pointers detected!");
        // See MPESC for status codes
        auto exitCodeIntLE = LLVMConstInt(LLVMInt8TypeInContext(globalState->context), 1, false);
        LLVMBuildCall(thenBuilder, globalState->externs->exit, &exitCodeIntLE, 1, "");
      });

  if (auto structReferendM = dynamic_cast<StructReferend*>(refM->referend)) {
    auto structM = globalState->program->getStruct(structReferendM->fullName);
    if (structM->weakability == Weakability::WEAKABLE) {
      wrcWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
    }
  } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(refM->referend)) {
    assert(false); // Do we ever deallocate an interface?
  } else {
    // Do nothing, only structs and interfaces are weakable in assist mode.
  }
}

Ref Assist::loadMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {
  buildFlare(FL(), globalState, functionState, builder);
  if (structRefMT->ownership == Ownership::SHARE) {
    assert(false);
  } else {
    buildFlare(FL(), globalState, functionState, builder);
    auto unupgradedMemberLE =
        regularLoadMember(
            globalState, functionState, builder, &referendStructs, structRefMT, structRef,
            memberIndex, expectedMemberType, targetType, memberName);
    buildFlare(FL(), globalState, functionState, builder);
    return upgradeLoadResultToRefWithTargetOwnership(
        functionState, builder, expectedMemberType, targetType, unupgradedMemberLE);
  }
}

void Assist::storeMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    const std::string& memberName,
    Reference* newMemberRefMT,
    Ref newMemberRef) {
  auto newMemberLE =
      globalState->getRegion(newMemberRefMT)->checkValidReference(
          FL(), functionState, builder, newMemberRefMT, newMemberRef);
  switch (structRefMT->ownership) {
    case Ownership::SHARE:
      assert(false);
    case Ownership::OWN:
    case Ownership::BORROW: {
      storeMemberStrong(
          globalState, functionState, builder, &referendStructs, structRefMT, structRef,
          structKnownLive, memberIndex, memberName, newMemberLE);
      break;
    }
    case Ownership::WEAK: {
      storeMemberWeak(
          globalState, functionState, builder, &referendStructs, structRefMT, structRef,
          structKnownLive, memberIndex, memberName, newMemberLE);
      break;
    }
    default:
      assert(false);
  }
}


// Gets the itable PTR and the new value that we should put into the virtual param's slot
// (such as a void* or a weak void ref)
std::tuple<LLVMValueRef, LLVMValueRef> Assist::explodeInterfaceRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef) {
  switch (virtualParamMT->ownership) {
    case Ownership::SHARE:
      assert(false);
    case Ownership::OWN:
    case Ownership::BORROW: {
      return explodeStrongInterfaceRef(
          globalState, functionState, builder, &referendStructs, virtualParamMT, virtualArgRef);
    }
    case Ownership::WEAK: {
      return explodeWeakInterfaceRef(
          globalState, functionState, builder, &referendStructs, &fatWeaks, &weakRefStructs,
          virtualParamMT, virtualArgRef,
          [this, functionState, builder, virtualParamMT](WeakFatPtrLE weakFatPtrLE) {
            return wrcWeaks.weakInterfaceRefToWeakStructRef(
                functionState, builder, virtualParamMT, weakFatPtrLE);
          });
    }
    default:
      assert(false);
  }
}

void Assist::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  return wrcWeaks.aliasWeakRef(from, functionState, builder, weakRefMT, weakRef);
}

void Assist::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  return wrcWeaks.discardWeakRef(from, functionState, builder, weakRefMT, weakRef);
}

Ref Assist::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef,
    bool knownLive) {
  return wrcWeaks.getIsAliveFromWeakRef(functionState, builder, weakRefM, weakRef);
}

LLVMValueRef Assist::getStringBytesPtr(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) {
  auto strWrapperPtrLE =
      referendStructs.makeWrapperPtr(
          FL(), functionState, builder,
          globalState->metalCache->strRef,
          checkValidReference(
              FL(), functionState, builder, globalState->metalCache->strRef, ref));
  return referendStructs.getStringBytesPtr(functionState, builder, strWrapperPtrLE);
}

Ref Assist::constructKnownSizeArray(
    Ref regionInstanceRef,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *referenceM,
    KnownSizeArrayT *referendM) {
  auto ksaDef = globalState->program->getKnownSizeArray(referendM->name);
  auto resultRef =
      ::constructKnownSizeArray(
          globalState, functionState, builder, referenceM, referendM, &referendStructs,
          [this, functionState, referenceM, referendM](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(),
                functionState,
                innerBuilder,
                referenceM->referend,
                controlBlockPtrLE,
                referendM->name->name);
          });
  // We dont increment here, see SRCAO
  return resultRef;
}

Ref Assist::mallocStr(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE,
    LLVMValueRef sourceCharsPtrLE) {
  assert(false);
  exit(1);
}

Ref Assist::allocate(
    Ref regionInstanceRef,
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* desiredReference,
    const std::vector<Ref>& memberRefs) {
  auto structReferend = dynamic_cast<StructReferend*>(desiredReference->referend);
  auto structM = globalState->program->getStruct(structReferend->fullName);
  auto resultRef =
      innerAllocate(
          FL(), globalState, functionState, builder, desiredReference, &referendStructs, memberRefs, Weakability::WEAKABLE,
          [this, functionState, desiredReference, structM](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(), functionState, innerBuilder, desiredReference->referend,
                controlBlockPtrLE, structM->name->name);
          });
  return resultRef;
}

// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
WrapperPtrLE Assist::lockWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref weakRefLE,
    bool weakRefKnownLive) {
  switch (refM->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE:
    case Ownership::BORROW:
      assert(false);
      break;
    case Ownership::WEAK: {
      auto weakFatPtrLE =
          weakRefStructs.makeWeakFatPtr(
              refM,
              checkValidReference(FL(), functionState, builder, refM, weakRefLE));
      return referendStructs.makeWrapperPtr(
          FL(), functionState, builder, refM,
          wrcWeaks.lockWrciFatPtr(from, functionState, builder, refM, weakFatPtrLE));
    }
    default:
      assert(false);
      break;
  }
}

Ref Assist::getUnknownSizeArrayLength(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    Ref arrayRef,
    bool arrayKnownLive) {
  return getUnknownSizeArrayLengthStrong(globalState, functionState, builder, &referendStructs, usaRefMT, arrayRef);
}


LLVMValueRef Assist::getCensusObjectId(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref ref) {
  if (refM == globalState->metalCache->intRef) {
    return constI64LE(globalState, -2);
  } else if (refM == globalState->metalCache->boolRef) {
    return constI64LE(globalState, -3);
  } else if (refM == globalState->metalCache->neverRef) {
    return constI64LE(globalState, -4);
  } else if (refM == globalState->metalCache->floatRef) {
    return constI64LE(globalState, -5);
  } else if (refM->location == Location::INLINE) {
    return constI64LE(globalState, -1);
  } else {
    auto controlBlockPtrLE =
        referendStructs.getControlBlockPtr(checkerAFL, functionState, builder, ref, refM);
    auto exprLE =
        referendStructs.getObjIdFromControlBlockPtr(builder, refM->referend, controlBlockPtrLE);
    return exprLE;
  }
}

LLVMValueRef Assist::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref ref) {
  Reference *actualRefM = nullptr;
  LLVMValueRef refLE = nullptr;
  std::tie(actualRefM, refLE) = megaGetRefInnardsForChecking(ref);
  assert(actualRefM == refM);
  assert(refLE != nullptr);
  assert(LLVMTypeOf(refLE) == globalState->getRegion(refM)->translateType(refM));

  buildFlare(FL(), globalState, functionState, builder);

  if (globalState->opt->census) {
    if (refM->ownership == Ownership::OWN) {
        buildFlare(FL(), globalState, functionState, builder);
      regularCheckValidReference(checkerAFL, globalState, functionState, builder, &referendStructs, refM, refLE);
    } else if (refM->ownership == Ownership::SHARE) {
      assert(false);
    } else {
        buildFlare(FL(), globalState, functionState, builder);
      if (refM->ownership == Ownership::BORROW) {
        regularCheckValidReference(checkerAFL, globalState, functionState, builder, &referendStructs, refM, refLE);
      } else if (refM->ownership == Ownership::WEAK) {
        wrcWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
      } else
        assert(false);
    }
    return refLE;
  } else {
    return refLE;
  }
}

// TODO maybe combine with alias/acquireReference?
// After we load from a local, member, or element, we can feed the result through this
// function to turn it into a desired ownership.
// Example:
// - Can load from an owning ref member to get a constraint ref.
// - Can load from a constraint ref member to get a weak ref.
Ref Assist::upgradeLoadResultToRefWithTargetOwnership(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    LoadResult sourceLoad) {
  auto sourceRef = sourceLoad.extractForAliasingInternals();
  auto sourceOwnership = sourceType->ownership;
  auto sourceLocation = sourceType->location;
  auto targetOwnership = targetType->ownership;
  auto targetLocation = targetType->location;
//  assert(sourceLocation == targetLocation); // unimplemented

  if (sourceOwnership == Ownership::SHARE) {
    if (sourceLocation == Location::INLINE) {
      buildFlare(FL(), globalState, functionState, builder);
      return sourceRef;
    } else {
      buildFlare(FL(), globalState, functionState, builder);
      return sourceRef;
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
      buildFlare(FL(), globalState, functionState, builder);
      return sourceRef;
    } else if (targetOwnership == Ownership::BORROW) {
      auto resultRef = transmutePtr(globalState, functionState, builder, sourceType, targetType, sourceRef);
      buildFlare(FL(), globalState, functionState, builder);
      checkValidReference(FL(),
          functionState, builder, targetType, resultRef);
      buildFlare(FL(), globalState, functionState, builder);
      return resultRef;
    } else if (targetOwnership == Ownership::WEAK) {
      buildFlare(FL(), globalState, functionState, builder);
      return wrcWeaks.assembleWeakRef(functionState, builder, sourceType, targetType, sourceRef);
    } else {
      assert(false);
    }
  } else if (sourceOwnership == Ownership::BORROW) {
    buildFlare(FL(), globalState, functionState, builder);

    if (targetOwnership == Ownership::OWN) {
      assert(false); // Cant load an owning reference from a constraint ref local.
    } else if (targetOwnership == Ownership::BORROW) {
      buildFlare(FL(), globalState, functionState, builder);
      return sourceRef;
    } else if (targetOwnership == Ownership::WEAK) {
      // Making a weak ref from a constraint ref local.
      assert(dynamic_cast<StructReferend *>(sourceType->referend) ||
          dynamic_cast<InterfaceReferend *>(sourceType->referend));
      buildFlare(FL(), globalState, functionState, builder);
      return wrcWeaks.assembleWeakRef(functionState, builder, sourceType, targetType, sourceRef);
    } else {
      assert(false);
    }
  } else if (sourceOwnership == Ownership::WEAK) {
    buildFlare(FL(), globalState, functionState, builder);
    assert(targetOwnership == Ownership::WEAK);
    return sourceRef;
  } else {
    assert(false);
  }
}

// Returns object ID
void Assist::fillControlBlock(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName) {
  regularFillControlBlock(
      from, globalState, functionState, &referendStructs, builder, referendM, controlBlockPtrLE,
      typeName, &wrcWeaks);
}

LoadResult Assist::loadElementFromKSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto ksaDef = globalState->program->getKnownSizeArray(ksaMT->name);
  return regularloadElementFromKSA(
      globalState, functionState, builder, ksaRefMT, ksaMT, ksaDef->rawArray->elementType, ksaDef->size, ksaDef->rawArray->mutability, arrayRef, arrayKnownLive, indexRef, &referendStructs);
}

LoadResult Assist::loadElementFromUSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
  return regularLoadElementFromUSAWithoutUpgrade(
      globalState, functionState, builder, &referendStructs, usaRefMT, usaMT, usaDef->rawArray->mutability, usaDef->rawArray->elementType, arrayRef,
      arrayKnownLive, indexRef);
}

Ref Assist::storeElementInUSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);

  auto arrayWrapperPtrLE =
      referendStructs.makeWrapperPtr(
          FL(), functionState, builder, usaRefMT,
          globalState->getRegion(usaRefMT)->checkValidReference(FL(), functionState, builder, usaRefMT, arrayRef));
  auto sizeRef = ::getUnknownSizeArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
  auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);
  buildFlare(FL(), globalState, functionState, builder);
  return ::swapElement(
      globalState, functionState, builder, usaRefMT->location, usaDef->rawArray->elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
}

Ref Assist::upcast(
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructMT,
    StructReferend* sourceStructReferendM,
    Ref sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
    buildFlare(FL(), globalState, functionState, builder);

  switch (sourceStructMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN:
    case Ownership::BORROW: {
      return upcastStrong(globalState, functionState, builder, &referendStructs, sourceStructMT, sourceStructReferendM, sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
    }
    case Ownership::WEAK: {
      return ::upcastWeak(globalState, functionState, builder, &weakRefStructs, sourceStructMT, sourceStructReferendM, sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
    }
    default:
      assert(false);
  }
}


void Assist::deallocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  innerDeallocate(from, globalState, functionState, &referendStructs, builder, refMT, ref);
}

Ref Assist::constructUnknownSizeArray(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaMT,
    UnknownSizeArrayT* unknownSizeArrayT,
    Ref sizeRef,
    const std::string& typeName) {
  auto usaWrapperPtrLT =
      referendStructs.getUnknownSizeArrayWrapperStruct(unknownSizeArrayT);
  auto usaDef = globalState->program->getUnknownSizeArray(unknownSizeArrayT->name);
  auto elementType = globalState->program->getUnknownSizeArray(unknownSizeArrayT->name)->rawArray->elementType;
  auto usaElementLT = globalState->getRegion(elementType)->translateType(elementType);
  auto resultRef =
      ::constructUnknownSizeArray(
          globalState, functionState, builder, &referendStructs, usaMT, usaDef->rawArray->elementType, unknownSizeArrayT,
          usaWrapperPtrLT, usaElementLT, sizeRef, typeName,
          [this, functionState, unknownSizeArrayT, usaMT, typeName](
              LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(),
                functionState,
                innerBuilder,
                unknownSizeArrayT,
                controlBlockPtrLE,
                typeName);
          });
  // We dont increment here, see SRCAO
  return resultRef;
}

void Assist::checkInlineStructType(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  auto argLE = checkValidReference(FL(), functionState, builder, refMT, ref);
  auto structReferend = dynamic_cast<StructReferend*>(refMT->referend);
  assert(structReferend);
  assert(LLVMTypeOf(argLE) == referendStructs.getInnerStruct(structReferend));
}

void Assist::generateUnknownSizeArrayDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName,
    UnknownSizeArrayDefinitionT* usaDefM) {
}

void Assist::generateKnownSizeArrayDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName,
    KnownSizeArrayDefinitionT* usaDefM) {
}

void Assist::generateStructDefsC(std::unordered_map<std::string, std::string>* cByExportedName, StructDefinition* structDefM) {
  if (structDefM->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    for (auto baseName : globalState->program->getExportedNames(structDefM->referend->fullName)) {
      auto refTypeName = baseName + "Ref";
      std::stringstream s;
      s << "typedef struct " << refTypeName << " { void* unused; } " << refTypeName << ";" << std::endl;
      cByExportedName->insert(std::make_pair(baseName, s.str()));
    }
  }
}

void Assist::generateInterfaceDefsC(std::unordered_map<std::string, std::string>* cByExportedName, InterfaceDefinition* interfaceDefM) {
  if (interfaceDefM->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    for (auto name : globalState->program->getExportedNames(interfaceDefM->referend->fullName)) {
      std::stringstream s;
      s << "typedef struct " << name << "Ref { void* unused1; void* unused2; } " << name << "Ref;";
      cByExportedName->insert(std::make_pair(name, s.str()));
    }
  }
}

std::string Assist::getMemberArbitraryRefNameCSeeMMEDT(Reference* refMT) {
  if (refMT->ownership == Ownership::SHARE) {
    assert(false);
  } else if (auto structRefMT = dynamic_cast<StructReferend*>(refMT->referend)) {
    auto structMT = globalState->program->getStruct(structRefMT->fullName);
    for (auto baseName : globalState->program->getExportedNames(structRefMT->fullName)) {
      if (structMT->mutability == Mutability::MUTABLE) {
        assert(refMT->location != Location::INLINE);
        return baseName + "Ref";
      } else {
        if (refMT->location == Location::INLINE) {
          return baseName + "Inl";
        } else {
          return baseName + "Ref";
        }
      }
    }
  } else if (auto interfaceMT = dynamic_cast<InterfaceReferend*>(refMT->referend)) {
    return globalState->program->getMemberArbitraryExportNameSeeMMEDT(interfaceMT->fullName) + "Ref";
  } else {
    assert(false);
  }
  assert(false);
  return "";
}

Reference* Assist::getExternalType(Reference* refMT) {
  return refMT;
//  if (refMT->ownership == Ownership::SHARE) {
//    assert(false);
//  } else {
//    if (auto structReferend = dynamic_cast<StructReferend*>(refMT->referend)) {
//      if (refMT->location == Location::INLINE) {
//        assert(false); // what do we do in this case? malloc an owning thing and send it in?
//        // perhaps just disallow sending inl owns?
//      }
//      return LLVMPointerType(referendStructs.getWrapperStruct(structReferend), 0);
//    } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(refMT->referend)) {
//      return LLVMPointerType(referendStructs.getInterfaceRefStruct(interfaceReferend), 0);
//    } else {
//      std::cerr << "Invalid type for extern!" << std::endl;
//      assert(false);
//    }
//  }
//  assert(false);
}

Ref Assist::receiveAndDecryptFamiliarReference(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceRefMT,
    Ref sourceRef) {
  assert(sourceRefMT->ownership != Ownership::SHARE);

  // Alias when receiving from the outside world, see DEPAR.
  globalState->getRegion(sourceRefMT)
      ->alias(FL(), functionState, builder, sourceRefMT, sourceRef);

  return sourceRef;
}

LLVMTypeRef Assist::getInterfaceMethodVirtualParamAnyType(Reference* reference) {
  switch (reference->ownership) {
    case Ownership::BORROW:
    case Ownership::OWN:
    case Ownership::SHARE:
      return LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
    case Ownership::WEAK:
      return mutWeakableStructs.getWeakVoidRefStruct(reference->referend);
    default:
      assert(false);
  }
}

Ref Assist::receiveUnencryptedAlienReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef) {
  assert(false);
  exit(1);
}

Ref Assist::encryptAndSendFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Ref sourceRef) {
  // Dealias when sending to the outside world, see DEPAR.
  globalState->getRegion(sourceRefMT)
      ->dealias(FL(), functionState, builder, sourceRefMT, sourceRef);

  return sourceRef;
}

void Assist::initializeElementInUSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *usaRefMT,
    UnknownSizeArrayT *usaMT,
    Ref usaRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
  auto arrayWrapperPtrLE =
      referendStructs.makeWrapperPtr(
          FL(), functionState, builder, usaRefMT,
          globalState->getRegion(usaRefMT)->checkValidReference(FL(), functionState, builder, usaRefMT, usaRef));

  auto sizeRef = ::getUnknownSizeArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
  auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);
  ::initializeElement(
      globalState, functionState, builder, usaRefMT->location, usaDef->rawArray->elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
}

Ref Assist::deinitializeElementFromUSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef) {
  return loadElementFromUSA(functionState, builder, usaRefMT, usaMT, arrayRef, arrayRefKnownLive, indexRef).move();
}

void Assist::initializeElementInKSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto ksaDef = globalState->program->getKnownSizeArray(ksaMT->name);
  auto arrayWrapperPtrLE =
      referendStructs.makeWrapperPtr(
          FL(), functionState, builder, ksaRefMT,
          globalState->getRegion(ksaRefMT)->checkValidReference(FL(), functionState, builder, ksaRefMT, arrayRef));
  auto sizeRef = globalState->constI64(ksaDef->size);
  auto arrayElementsPtrLE = getKnownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);
  ::initializeElement(
      globalState, functionState, builder, ksaRefMT->location, ksaDef->rawArray->elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
}

Ref Assist::deinitializeElementFromKSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef) {
  assert(false);
  exit(1);
}

Weakability Assist::getReferendWeakability(Referend* referend) {
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    return globalState->lookupStruct(structReferend->fullName)->weakability;
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
    return globalState->lookupInterface(interfaceReferend->fullName)->weakability;
  } else {
    return Weakability::NON_WEAKABLE;
  }
}


LLVMValueRef Assist::getInterfaceMethodFunctionPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    int indexInEdge) {
  return getInterfaceMethodFunctionPtrFromItable(
      globalState, functionState, builder, virtualParamMT, virtualArgRef, indexInEdge);
}

LLVMValueRef Assist::stackify(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    Ref refToStore,
    bool knownLive) {
  auto toStoreLE = checkValidReference(FL(), functionState, builder, local->type, refToStore);
  auto typeLT = translateType(local->type);
  return makeMidasLocal(functionState, builder, typeLT, local->id->maybeName.c_str(), toStoreLE);
}

Ref Assist::unstackify(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return loadLocal(functionState, builder, local, localAddr);
}

Ref Assist::loadLocal(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return normalLocalLoad(globalState, functionState, builder, local, localAddr);
}

Ref Assist::localStore(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore, bool knownLive) {
  return normalLocalStore(globalState, functionState, builder, local, localAddr, refToStore);
}
