#include <region/common/fatweaks/fatweaks.h>
#include <region/common/hgm/hgm.h>
#include <translatetype.h>
#include <region/common/common.h>
#include <utils/counters.h>
#include <region/common/controlblock.h>
#include <utils/branch.h>
#include <region/common/heap.h>
#include <function/expressions/shared/members.h>
#include <function/expressions/shared/elements.h>
#include <function/expressions/shared/string.h>
#include "resilientv3.h"
#include <sstream>

ControlBlock makeResilientV3WeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutControlBlock"));
  controlBlock.addMember(ControlBlockMember::GENERATION);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.build();
  return controlBlock;
}

ResilientV3::ResilientV3(GlobalState *globalState_, RegionId *regionId_) :
    globalState(globalState_),
    regionId(regionId_),
    mutWeakableStructs(
        globalState,
        makeResilientV3WeakableControlBlock(globalState),
        HybridGenerationalMemory::makeWeakRefHeaderStruct(globalState, regionId)),
    referendStructs(
        globalState, [this](Referend *referend) -> IReferendStructsSource * { return &mutWeakableStructs; }),
    weakRefStructs([this](Referend *referend) -> IWeakRefStructsSource * { return &mutWeakableStructs; }),
    fatWeaks(globalState_, &weakRefStructs),
    hgmWeaks(
        globalState_,
        mutWeakableStructs.getControlBlock(),
        &referendStructs,
        &weakRefStructs,
        globalState->opt->elideChecksForKnownLive,
        false,
        // V3 doesnt use the undead cycle, so any struct will do here
        globalState->metalCache->emptyTupleStruct) {
}

void ResilientV3::mainSetup(FunctionState* functionState, LLVMBuilderRef builder) {
  hgmWeaks.mainSetup(functionState, builder);
}

void ResilientV3::mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) {
  hgmWeaks.mainCleanup(functionState, builder);
}

RegionId *ResilientV3::getRegionId() {
  return regionId;
}

Ref ResilientV3::constructKnownSizeArray(
    Ref regionInstanceRef,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *referenceM,
    KnownSizeArrayT *referendM) {
  auto ksaDef = globalState->program->getKnownSizeArray(referendM->name);
  auto resultRef =
      ::constructKnownSizeArray(
          globalState, functionState, builder, referenceM, referendM, &referendStructs,
          [this, functionState, referenceM, referendM](LLVMBuilderRef innerBuilder,
                                                       ControlBlockPtrLE controlBlockPtrLE) {
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

Ref ResilientV3::mallocStr(
    Ref regionInstanceRef,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE,
    LLVMValueRef sourceCharsPtrLE) {
  assert(false);
  exit(1);
}

Ref ResilientV3::allocate(
    Ref regionInstanceRef,
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *desiredReference,
    const std::vector<Ref> &memberRefs) {
  auto structReferend = dynamic_cast<StructReferend *>(desiredReference->referend);
  auto structM = globalState->program->getStruct(structReferend->fullName);
  auto resultRef =
      innerAllocate(
          FL(), globalState, functionState, builder, desiredReference, &referendStructs, memberRefs,
          Weakability::WEAKABLE,
          [this, functionState, desiredReference, structM](LLVMBuilderRef innerBuilder,
                                                           ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(), functionState, innerBuilder, desiredReference->referend,
                controlBlockPtrLE, structM->name->name);
          });
  return resultRef;
}

void ResilientV3::alias(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceRef,
    Ref expr) {
  auto sourceRnd = sourceRef->referend;

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
      // We might be loading a member as an own if we're destructuring.
      // Don't adjust the RC, since we're only moving it.
    } else if (sourceRef->ownership == Ownership::BORROW ||
               sourceRef->ownership == Ownership::WEAK) {
      aliasWeakRef(from, functionState, builder, sourceRef, expr);
    } else if (sourceRef->ownership == Ownership::SHARE) {
      if (sourceRef->location == Location::INLINE) {
        // Do nothing, we can just let inline structs disappear
      } else {
        adjustStrongRc(from, globalState, functionState, &referendStructs, builder, expr, sourceRef, 1);
      }
    } else
      assert(false);
  } else {
    std::cerr << "Unimplemented type in acquireReference: "
              << typeid(*sourceRef->referend).name() << std::endl;
    assert(false);
  }
}

void ResilientV3::dealias(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceMT,
    Ref sourceRef) {
  auto sourceRnd = sourceMT->referend;

  if (sourceMT->ownership == Ownership::SHARE) {
    assert(false);
  } else {
    if (sourceMT->ownership == Ownership::OWN) {
      // This can happen if we're sending an owning reference to the outside world, see DEPAR.
    } else if (sourceMT->ownership == Ownership::BORROW) {
      discardWeakRef(from, functionState, builder, sourceMT, sourceRef);
    } else if (sourceMT->ownership == Ownership::WEAK) {
      discardWeakRef(from, functionState, builder, sourceMT, sourceRef);
    } else
      assert(false);
  }
}

Ref ResilientV3::weakAlias(FunctionState *functionState, LLVMBuilderRef builder, Reference *sourceRefMT,
                           Reference *targetRefMT, Ref sourceRef) {
  assert(sourceRefMT->ownership == Ownership::BORROW);
  return transmuteWeakRef(
      globalState, functionState, builder, sourceRefMT, targetRefMT, &weakRefStructs, sourceRef);
}

// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
WrapperPtrLE ResilientV3::lockWeakRef(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refM,
    Ref weakRefLE,
    bool weakRefKnownLive) {
  switch (refM->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE: {
      auto objPtrLE = weakRefLE;
      auto weakFatPtrLE =
          checkValidReference(
              FL(), functionState, builder, refM, weakRefLE);
      return referendStructs.makeWrapperPtr(FL(), functionState, builder, refM, weakFatPtrLE);
    }
    case Ownership::BORROW:
    case Ownership::WEAK: {
      auto weakFatPtrLE =
          weakRefStructs.makeWeakFatPtr(
              refM,
              checkValidReference(
                  FL(), functionState, builder, refM, weakRefLE));
      return referendStructs.makeWrapperPtr(
          FL(), functionState, builder, refM,
          hgmWeaks.lockGenFatPtr(
              from, functionState, builder, refM, weakFatPtrLE, weakRefKnownLive));
    }
    default:
      assert(false);
      break;
  }
}

Ref ResilientV3::lockWeak(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference *resultOptTypeM,
    Reference *constraintRefM,
    Reference *sourceWeakRefMT,
    Ref sourceWeakRefLE,
    bool weakRefKnownLive,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {

  assert(sourceWeakRefMT->ownership == Ownership::BORROW ||
         sourceWeakRefMT->ownership == Ownership::WEAK);
  auto isAliveLE =
      getIsAliveFromWeakRef(
          functionState, builder, sourceWeakRefMT, sourceWeakRefLE, weakRefKnownLive);
  auto resultOptTypeLE = globalState->getRegion(resultOptTypeM)->translateType(resultOptTypeM);
  return resilientLockWeak(
      globalState, functionState, builder, thenResultIsNever, elseResultIsNever,
      resultOptTypeM, constraintRefM, sourceWeakRefMT, sourceWeakRefLE, weakRefKnownLive,
      buildThen, buildElse, isAliveLE, resultOptTypeLE, &weakRefStructs);
}

Ref ResilientV3::asSubtype(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference* resultOptTypeM,
    Reference* constraintRefM,
    Reference* sourceInterfaceRefMT,
    Ref sourceInterfaceRef,
    bool sourceRefKnownLive,
    Referend* targetReferend,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {
  auto targetStructReferend = dynamic_cast<StructReferend*>(targetReferend);
  assert(targetStructReferend);
  auto sourceInterfaceReferend = dynamic_cast<InterfaceReferend*>(sourceInterfaceRefMT->referend);
  assert(sourceInterfaceReferend);

  return resilientDowncast(
      globalState, functionState, builder, &weakRefStructs, resultOptTypeM, sourceInterfaceRefMT, sourceInterfaceRef,
      targetReferend, buildThen, buildElse, targetStructReferend, sourceInterfaceReferend);
}

LLVMTypeRef ResilientV3::translateType(Reference *referenceM) {
  switch (referenceM->ownership) {
    case Ownership::SHARE:
      assert(false);
    case Ownership::OWN:
      assert(referenceM->location != Location::INLINE);
      return translateReferenceSimple(globalState, &referendStructs, referenceM->referend);
    case Ownership::BORROW:
    case Ownership::WEAK:
      assert(referenceM->location != Location::INLINE);
      return translateWeakReference(globalState, &weakRefStructs, referenceM->referend);
    default:
      assert(false);
  }
}

Ref ResilientV3::upcastWeak(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructReferend *sourceStructReferendM,
    Reference *sourceStructTypeM,
    InterfaceReferend *targetInterfaceReferendM,
    Reference *targetInterfaceTypeM) {
  auto resultWeakInterfaceFatPtr =
      hgmWeaks.weakStructPtrToGenWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
          sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
  return wrap(this, targetInterfaceTypeM, resultWeakInterfaceFatPtr);
}

void ResilientV3::declareKnownSizeArray(
    KnownSizeArrayDefinitionT *knownSizeArrayMT) {
  globalState->regionIdByReferend.emplace(knownSizeArrayMT->referend, getRegionId());

  referendStructs.declareKnownSizeArray(knownSizeArrayMT);
}

void ResilientV3::declareUnknownSizeArray(
    UnknownSizeArrayDefinitionT *unknownSizeArrayMT) {
  globalState->regionIdByReferend.emplace(unknownSizeArrayMT->referend, getRegionId());

  referendStructs.declareUnknownSizeArray(unknownSizeArrayMT);
}

void ResilientV3::defineUnknownSizeArray(
    UnknownSizeArrayDefinitionT *unknownSizeArrayMT) {
  auto elementLT =
      globalState->getRegion(unknownSizeArrayMT->rawArray->elementType)
          ->translateType(unknownSizeArrayMT->rawArray->elementType);
  referendStructs.defineUnknownSizeArray(unknownSizeArrayMT, elementLT);
}

void ResilientV3::defineKnownSizeArray(
    KnownSizeArrayDefinitionT *knownSizeArrayMT) {
  auto elementLT =
      globalState->getRegion(knownSizeArrayMT->rawArray->elementType)
          ->translateType(knownSizeArrayMT->rawArray->elementType);
  referendStructs.defineKnownSizeArray(knownSizeArrayMT, elementLT);
}

void ResilientV3::declareStruct(
    StructDefinition *structM) {
  globalState->regionIdByReferend.emplace(structM->referend, getRegionId());

  referendStructs.declareStruct(structM->referend);
}

void ResilientV3::defineStruct(StructDefinition *structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        globalState->getRegion(structM->members[i]->type)
            ->translateType(structM->members[i]->type));
  }
  referendStructs.defineStruct(structM->referend, innerStructMemberTypesL);
}

void ResilientV3::declareEdge(Edge *edge) {
  referendStructs.declareEdge(edge);
}

void ResilientV3::defineEdge(Edge *edge) {
  auto interfaceFunctionsLT = globalState->getInterfaceFunctionTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  referendStructs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);
}

void ResilientV3::declareInterface(InterfaceDefinition *interfaceM) {
  globalState->regionIdByReferend.emplace(interfaceM->referend, getRegionId());
  referendStructs.declareInterface(interfaceM);
}

void ResilientV3::defineInterface(InterfaceDefinition *interfaceM) {
  auto interfaceMethodTypesL = globalState->getInterfaceFunctionTypes(interfaceM->referend);
  referendStructs.defineInterface(interfaceM, interfaceMethodTypesL);
}

void ResilientV3::discardOwningRef(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    BlockState *blockState,
    LLVMBuilderRef builder,
    Reference *sourceMT,
    Ref sourceRef) {
  // Mutables in resilient v1+2 dont have strong RC, and also, they dont adjust
  // weak RC for owning refs

  // Free it!
  deallocate(AFL("discardOwningRef"), functionState, builder, sourceMT, sourceRef);
}

void ResilientV3::noteWeakableDestroyed(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refM,
    ControlBlockPtrLE controlBlockPtrLE) {
  if (refM->ownership == Ownership::SHARE) {
    assert(false);
//    auto rcIsZeroLE = strongRcIsZero(globalState, &referendStructs, builder, refM, controlBlockPtrLE);
//    buildAssert(globalState, functionState, builder, rcIsZeroLE,
//                "Tried to free concrete that had nonzero RC!");
  } else {
    assert(refM->ownership == Ownership::OWN);

    // In resilient mode, every mutable is weakable.
    hgmWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
  }
}

void ResilientV3::storeMember(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    const std::string &memberName,
    Reference *newMemberRefMT,
    Ref newMemberRef) {
  auto newMemberLE =
      globalState->getRegion(newMemberRefMT)->checkValidReference(
          FL(), functionState, builder, newMemberRefMT, newMemberRef);
  switch (structRefMT->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE: {
      return storeMemberStrong(
          globalState, functionState, builder, &referendStructs, structRefMT, structRef,
          structKnownLive, memberIndex, memberName, newMemberLE);
    }
    case Ownership::BORROW:
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
std::tuple<LLVMValueRef, LLVMValueRef> ResilientV3::explodeInterfaceRef(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *virtualParamMT,
    Ref virtualArgRef) {
  switch (virtualParamMT->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE: {
      return explodeStrongInterfaceRef(
          globalState, functionState, builder, &referendStructs, virtualParamMT, virtualArgRef);
    }
    case Ownership::BORROW:
    case Ownership::WEAK: {
      return explodeWeakInterfaceRef(
          globalState, functionState, builder, &referendStructs, &fatWeaks, &weakRefStructs,
          virtualParamMT, virtualArgRef,
          [this, functionState, builder, virtualParamMT](WeakFatPtrLE weakFatPtrLE) {
            return hgmWeaks.weakInterfaceRefToWeakStructRef(
                functionState, builder, virtualParamMT, weakFatPtrLE);
          });
    }
    default:
      assert(false);
  }
}

Ref ResilientV3::getUnknownSizeArrayLength(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *usaRefMT,
    Ref arrayRef,
    bool arrayKnownLive) {
  switch (usaRefMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN: {
      return getUnknownSizeArrayLengthStrong(globalState, functionState, builder, &referendStructs, usaRefMT, arrayRef);
    }
    case Ownership::BORROW: {
      auto wrapperPtrLE =
          lockWeakRef(
              FL(), functionState, builder, usaRefMT, arrayRef, arrayKnownLive);
      return ::getUnknownSizeArrayLength(globalState, functionState, builder, wrapperPtrLE);
    }
    case Ownership::WEAK:
      assert(false); // VIR never loads from a weak ref
  }
}

LLVMValueRef ResilientV3::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refM,
    Ref ref) {
  Reference *actualRefM = nullptr;
  LLVMValueRef refLE = nullptr;
  std::tie(actualRefM, refLE) = megaGetRefInnardsForChecking(ref);
  assert(actualRefM == refM);
  assert(refLE != nullptr);
  assert(LLVMTypeOf(refLE) == globalState->getRegion(refM)->translateType(refM));

  if (globalState->opt->census) {
    if (refM->ownership == Ownership::OWN) {
      regularCheckValidReference(checkerAFL, globalState, functionState, builder, &referendStructs, refM, refLE);
    } else if (refM->ownership == Ownership::SHARE) {
      assert(false);
    } else {
      hgmWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
    }
  }
  return refLE;
}

// TODO maybe combine with alias/acquireReference?
// After we load from a local, member, or element, we can feed the result through this
// function to turn it into a desired ownership.
// Example:
// - Can load from an owning ref member to get a constraint ref.
// - Can load from a constraint ref member to get a weak ref.
Ref ResilientV3::upgradeLoadResultToRefWithTargetOwnership(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceType,
    Reference *targetType,
    LoadResult sourceLoadResult) {
  auto sourceRef = sourceLoadResult.extractForAliasingInternals();
  auto sourceOwnership = sourceType->ownership;
  auto sourceLocation = sourceType->location;
  auto targetOwnership = targetType->ownership;
  auto targetLocation = targetType->location;
//  assert(sourceLocation == targetLocation); // unimplemented

  if (sourceOwnership == Ownership::SHARE) {
    if (sourceLocation == Location::INLINE) {
      return sourceRef;
    } else {
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
      return sourceRef;
    } else if (targetOwnership == Ownership::BORROW
               || targetOwnership == Ownership::WEAK) {
      // Now we need to package it up into a weak ref.
      return hgmWeaks.assembleWeakRef(functionState, builder, sourceType, targetType, sourceRef);
    } else {
      assert(false);
    }
  } else if (sourceOwnership == Ownership::BORROW || sourceOwnership == Ownership::WEAK) {
    assert(targetOwnership == Ownership::BORROW || targetOwnership == Ownership::WEAK);

    return transmutePtr(globalState, functionState, builder, sourceType, targetType, sourceRef);
  } else {
    assert(false);
  }
}

void ResilientV3::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *weakRefMT,
    Ref weakRef) {
  return hgmWeaks.aliasWeakRef(from, functionState, builder, weakRefMT, weakRef);
}

void ResilientV3::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *weakRefMT,
    Ref weakRef) {
  return hgmWeaks.discardWeakRef(from, functionState, builder, weakRefMT, weakRef);
}

LLVMValueRef ResilientV3::getCensusObjectId(
    AreaAndFileAndLine checkerAFL,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refM,
    Ref ref) {
  auto controlBlockPtrLE =
      referendStructs.getControlBlockPtr(checkerAFL, functionState, builder, ref, refM);
  return referendStructs.getObjIdFromControlBlockPtr(builder, refM->referend, controlBlockPtrLE);
}

Ref ResilientV3::getIsAliveFromWeakRef(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *weakRefM,
    Ref weakRef,
    bool knownLive) {
  return hgmWeaks.getIsAliveFromWeakRef(functionState, builder, weakRefM, weakRef, knownLive);
}

// Returns object ID
void ResilientV3::fillControlBlock(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Referend *referendM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string &typeName) {

  gmFillControlBlock(
      from, globalState, functionState, &referendStructs, builder, referendM, controlBlockPtrLE,
      typeName, &hgmWeaks);
}

LoadResult ResilientV3::loadElementFromKSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *ksaRefMT,
    KnownSizeArrayT *ksaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto ksaDef = globalState->program->getKnownSizeArray(ksaMT->name);
  return resilientloadElementFromKSA(
      globalState, functionState, builder, ksaRefMT, ksaMT, ksaDef->size, ksaDef->rawArray->mutability,
      ksaDef->rawArray->elementType, arrayRef, arrayKnownLive, indexRef, &referendStructs);
}

LoadResult ResilientV3::loadElementFromUSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *usaRefMT,
    UnknownSizeArrayT *usaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
  return resilientLoadElementFromUSAWithoutUpgrade(
      globalState, functionState, builder, &referendStructs, usaRefMT, usaDef->rawArray->mutability,
      usaDef->rawArray->elementType, usaMT, arrayRef, arrayKnownLive, indexRef);
}

Ref ResilientV3::storeElementInUSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *usaRefMT,
    UnknownSizeArrayT *usaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
  auto arrayWrapperPtrLE = lockWeakRef(FL(), functionState, builder, usaRefMT, arrayRef, arrayKnownLive);
  auto sizeRef = ::getUnknownSizeArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
  auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);
  buildFlare(FL(), globalState, functionState, builder);
  return ::swapElement(
      globalState, functionState, builder, usaRefMT->location, usaDef->rawArray->elementType, sizeRef,
      arrayElementsPtrLE,
      indexRef, elementRef);
}

Ref ResilientV3::upcast(
    FunctionState *functionState,
    LLVMBuilderRef builder,

    Reference *sourceStructMT,
    StructReferend *sourceStructReferendM,
    Ref sourceRefLE,

    Reference *targetInterfaceTypeM,
    InterfaceReferend *targetInterfaceReferendM) {

  switch (sourceStructMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN: {
      return upcastStrong(globalState, functionState, builder, &referendStructs, sourceStructMT, sourceStructReferendM,
                          sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
    }
    case Ownership::BORROW:
    case Ownership::WEAK: {
      return ::upcastWeak(globalState, functionState, builder, &weakRefStructs, sourceStructMT, sourceStructReferendM,
                          sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
    }
    default:
      assert(false);
  }

}


void ResilientV3::deallocate(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refMT,
    Ref ref) {
  innerDeallocate(from, globalState, functionState, &referendStructs, builder, refMT, ref);
}

Ref ResilientV3::constructUnknownSizeArray(
    Ref regionInstanceRef,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *usaMT,
    UnknownSizeArrayT *unknownSizeArrayT,
    Ref sizeRef,
    const std::string &typeName) {
  auto usaWrapperPtrLT =
      referendStructs.getUnknownSizeArrayWrapperStruct(unknownSizeArrayT);
  auto usaDef = globalState->program->getUnknownSizeArray(unknownSizeArrayT->name);
  auto elementType = globalState->program->getUnknownSizeArray(unknownSizeArrayT->name)->rawArray->elementType;
  auto usaElementLT = globalState->getRegion(elementType)->translateType(elementType);
  auto resultRef =
      ::constructUnknownSizeArray(
          globalState, functionState, builder, &referendStructs, usaMT, usaDef->rawArray->elementType,
          unknownSizeArrayT,
          usaWrapperPtrLT, usaElementLT, sizeRef, typeName,
          [this, functionState, unknownSizeArrayT, typeName](
              LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(), functionState, innerBuilder, unknownSizeArrayT, controlBlockPtrLE, typeName);
          });
  // We dont increment here, see SRCAO
  return resultRef;
}

Ref ResilientV3::loadMember(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    Reference *expectedMemberType,
    Reference *targetType,
    const std::string &memberName) {

  if (structRefMT->ownership == Ownership::SHARE) {
    assert(false);
  } else {
    if (structRefMT->location == Location::INLINE) {
      auto structRefLE = checkValidReference(FL(), functionState, builder,
                                             structRefMT, structRef);
      return wrap(globalState->getRegion(expectedMemberType), expectedMemberType,
                  LLVMBuildExtractValue(
                      builder, structRefLE, memberIndex, memberName.c_str()));
    } else {
      switch (structRefMT->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE: {
          auto unupgradedMemberLE =
              regularLoadMember(
                  globalState, functionState, builder, &referendStructs, structRefMT, structRef,
                  memberIndex, expectedMemberType, targetType, memberName);
          return upgradeLoadResultToRefWithTargetOwnership(
              functionState, builder, expectedMemberType, targetType, unupgradedMemberLE);
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto memberLE =
              resilientLoadWeakMember(
                  globalState, functionState, builder, &referendStructs, structRefMT,
                  structRef,
                  structKnownLive, memberIndex, expectedMemberType, memberName);
          auto resultRef =
              upgradeLoadResultToRefWithTargetOwnership(
                  functionState, builder, expectedMemberType, targetType, memberLE);
          return resultRef;
        }
        default:
          assert(false);
      }
    }
  }
}

void ResilientV3::checkInlineStructType(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refMT,
    Ref ref) {
  auto argLE = checkValidReference(FL(), functionState, builder, refMT, ref);
  auto structReferend = dynamic_cast<StructReferend *>(refMT->referend);
  assert(structReferend);
  assert(LLVMTypeOf(argLE) == referendStructs.getInnerStruct(structReferend));
}


std::string ResilientV3::getMemberArbitraryRefNameCSeeMMEDT(Reference *refMT) {
  if (refMT->ownership == Ownership::SHARE) {
    assert(false);
  } else if (auto structRefMT = dynamic_cast<StructReferend *>(refMT->referend)) {
    auto structMT = globalState->program->getStruct(structRefMT->fullName);
    auto baseName = globalState->program->getMemberArbitraryExportNameSeeMMEDT(structRefMT->fullName);
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
  } else if (auto interfaceMT = dynamic_cast<InterfaceReferend *>(refMT->referend)) {
    return globalState->program->getMemberArbitraryExportNameSeeMMEDT(interfaceMT->fullName) + "Ref";
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT*>(refMT->referend)) {
    return globalState->program->getMemberArbitraryExportNameSeeMMEDT(usaMT->name) + "Ref";
  } else if (auto ksaMT = dynamic_cast<KnownSizeArrayT*>(refMT->referend)) {
    return globalState->program->getMemberArbitraryExportNameSeeMMEDT(ksaMT->name) + "Ref";
  } else {
    assert(false);
  }
}

void ResilientV3::generateUnknownSizeArrayDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName,
    UnknownSizeArrayDefinitionT* usaDefM) {
  if (usaDefM->rawArray->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    for (auto baseName : globalState->program->getExportedNames(usaDefM->name)) {
      auto refTypeName = baseName + "Ref";
      std::stringstream s;
      s << "typedef struct " << refTypeName << " { uint64_t unused0; void* unused; } " << refTypeName << ";" << std::endl;
      cByExportedName->insert(std::make_pair(baseName, s.str()));
    }
  }
}

void ResilientV3::generateKnownSizeArrayDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName,
    KnownSizeArrayDefinitionT* ksaDefM) {
  if (ksaDefM->rawArray->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    for (auto baseName : globalState->program->getExportedNames(ksaDefM->name)) {
      auto refTypeName = baseName + "Ref";
      std::stringstream s;
      s << "typedef struct " << refTypeName << " { uint64_t unused0; void* unused; } " << refTypeName << ";" << std::endl;
      cByExportedName->insert(std::make_pair(baseName, s.str()));
    }
  }
}

void ResilientV3::generateStructDefsC(
    std::unordered_map<std::string, std::string> *cByExportedName, StructDefinition *structDefM) {

  if (structDefM->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    for (auto baseName : globalState->program->getExportedNames(structDefM->referend->fullName)) {
      auto refTypeName = baseName + "Ref";
      std::stringstream s;
      s << "typedef struct " << refTypeName << " { uint64_t unused0; void* unused1; } " << refTypeName << ";"
        << std::endl;
      cByExportedName->insert(std::make_pair(baseName, s.str()));
    }
  }
}

void ResilientV3::generateInterfaceDefsC(
    std::unordered_map<std::string, std::string> *cByExportedName, InterfaceDefinition *interfaceDefM) {

  if (interfaceDefM->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    for (auto name : globalState->program->getExportedNames(interfaceDefM->referend->fullName)) {
      std::stringstream s;
      s << "typedef struct " << name << "Ref { uint64_t unused0; void* unused1; void* unused2; } " << name << "Ref;";
      cByExportedName->insert(std::make_pair(name, s.str()));
    }
  }
}

Reference *ResilientV3::getExternalType(Reference *refMT) {
  return refMT;
}

Ref ResilientV3::receiveAndDecryptFamiliarReference(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceRefMT,
    Ref sourceRef) {
  switch (sourceRefMT->ownership) {
    case Ownership::SHARE:
      assert(false);
    case Ownership::OWN:
    case Ownership::BORROW:
    case Ownership::WEAK:
      // Someday we'll do some encryption stuff here
      return sourceRef;
  }
  assert(false);
}

LLVMTypeRef ResilientV3::getInterfaceMethodVirtualParamAnyType(Reference *reference) {
  switch (reference->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE:
      return LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
    case Ownership::BORROW:
    case Ownership::WEAK:
      return weakRefStructs.getWeakVoidRefStruct(reference->referend);
  }
}

Ref ResilientV3::receiveUnencryptedAlienReference(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceRefMT,
    Reference *targetRefMT,
    Ref sourceRef) {
  assert(false);
  exit(1);
}

Ref ResilientV3::encryptAndSendFamiliarReference(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceRefMT,
    Ref sourceRef) {
  // Someday we'll do some encryption stuff here
  return sourceRef;
}

void ResilientV3::initializeElementInUSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *usaRefMT,
    UnknownSizeArrayT *usaMT,
    Ref usaRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  ::initializeElementInUSA(globalState, functionState, builder, &referendStructs, usaMT, usaRefMT, usaRef, indexRef,
                           elementRef);
}

Ref ResilientV3::deinitializeElementFromUSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *usaRefMT,
    UnknownSizeArrayT *usaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef) {
  auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
  return resilientLoadElementFromUSAWithoutUpgrade(
      globalState, functionState, builder, &referendStructs, usaRefMT, usaDef->rawArray->mutability,
      usaDef->rawArray->elementType, usaMT, arrayRef, true, indexRef).move();
}

void ResilientV3::initializeElementInKSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *ksaRefMT,
    KnownSizeArrayT *ksaMT,
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

Ref ResilientV3::deinitializeElementFromKSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *ksaRefMT,
    KnownSizeArrayT *ksaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef) {
  assert(false);
  exit(1);
}

Weakability ResilientV3::getReferendWeakability(Referend *referend) {
  if (auto structReferend = dynamic_cast<StructReferend *>(referend)) {
    return globalState->lookupStruct(structReferend->fullName)->weakability;
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend *>(referend)) {
    return globalState->lookupInterface(interfaceReferend->fullName)->weakability;
  } else {
    return Weakability::NON_WEAKABLE;
  }
}

LLVMValueRef ResilientV3::getInterfaceMethodFunctionPtr(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *virtualParamMT,
    Ref virtualArgRef,
    int indexInEdge) {
  return getInterfaceMethodFunctionPtrFromItable(
      globalState, functionState, builder, virtualParamMT, virtualArgRef, indexInEdge);
}

LLVMValueRef ResilientV3::stackify(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    Ref refToStore,
    bool knownLive) {
  auto toStoreLE = checkValidReference(FL(), functionState, builder, local->type, refToStore);
  auto typeLT = translateType(local->type);
  return makeMidasLocal(functionState, builder, typeLT, local->id->maybeName.c_str(), toStoreLE);
}

Ref ResilientV3::unstackify(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return loadLocal(functionState, builder, local, localAddr);
}

Ref ResilientV3::loadLocal(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return normalLocalLoad(globalState, functionState, builder, local, localAddr);
}

Ref ResilientV3::localStore(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore, bool knownLive) {
  return normalLocalStore(globalState, functionState, builder, local, localAddr, refToStore);
}
