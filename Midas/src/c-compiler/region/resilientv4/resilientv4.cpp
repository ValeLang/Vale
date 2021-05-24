#include <region/common/fatweaks/fatweaks.h>
#include <region/common/hgm/hgm.h>
#include <optional>
#include <translatetype.h>
#include <region/common/common.h>
#include <utils/counters.h>
#include <region/common/controlblock.h>
#include <utils/branch.h>
#include <region/common/heap.h>
#include <function/expressions/shared/members.h>
#include <function/expressions/shared/elements.h>
#include <function/expressions/shared/string.h>
#include "resilientv4.h"
#include <sstream>

ControlBlock makeResilientV4WeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutControlBlock"));
  controlBlock.addMember(ControlBlockMember::GENERATION);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::TETHER_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.build();
  return controlBlock;
}

StructReferend* makeAny(GlobalState* globalState, RegionId* regionId) {
  auto structReferend = globalState->metalCache->getStructReferend(globalState->metalCache->getName("__ValeHGMV4_Any"));
  auto iter = globalState->regionIdByReferend.emplace(structReferend, regionId).first;
  assert(globalState->regionIdByReferend[structReferend] == regionId);
  return structReferend;
}

ResilientV4::ResilientV4(GlobalState *globalState_, RegionId *regionId_) :
    globalState(globalState_),
    regionId(regionId_),
    mutWeakableStructs(
        globalState,
        makeResilientV4WeakableControlBlock(globalState),
        HybridGenerationalMemory::makeWeakRefHeaderStruct(globalState, regionId)),
    referendStructs(
        globalState, [this](Referend *referend) -> IReferendStructsSource * { return &mutWeakableStructs; }),
    weakRefStructs([this](Referend *referend) -> IWeakRefStructsSource * { return &mutWeakableStructs; }),
    fatWeaks(globalState_, &weakRefStructs),
    anyMT(makeAny(globalState, regionId)),
    hgmWeaks(
        globalState_,
        mutWeakableStructs.getControlBlock(),
        &referendStructs,
        &weakRefStructs,
        globalState->opt->elideChecksForKnownLive,
        false,
        anyMT) {
  referendStructs.declareStruct(anyMT);
  referendStructs.defineStruct(anyMT, {});
}

void ResilientV4::mainSetup(FunctionState* functionState, LLVMBuilderRef builder) {
  hgmWeaks.mainSetup(functionState, builder);
}

void ResilientV4::mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) {
  hgmWeaks.mainCleanup(functionState, builder);
}

RegionId *ResilientV4::getRegionId() {
  return regionId;
}

Ref ResilientV4::constructKnownSizeArray(
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

Ref ResilientV4::mallocStr(
    Ref regionInstanceRef,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE,
    LLVMValueRef sourceCharsPtrLE) {
  assert(false);
  exit(1);
}

Ref ResilientV4::allocate(
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

void ResilientV4::alias(
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

void ResilientV4::dealias(
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

Ref ResilientV4::weakAlias(FunctionState *functionState, LLVMBuilderRef builder, Reference *sourceRefMT,
                           Reference *targetRefMT, Ref sourceRef) {
  assert(sourceRefMT->ownership == Ownership::BORROW);
  return transmuteWeakRef(
      globalState, functionState, builder, sourceRefMT, targetRefMT, &weakRefStructs, sourceRef);
}

// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
WrapperPtrLE ResilientV4::lockWeakRef(
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

Ref ResilientV4::lockWeak(
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

Ref ResilientV4::asSubtype(
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

LLVMTypeRef ResilientV4::translateType(Reference *referenceM) {
  switch (referenceM->ownership) {
    case Ownership::SHARE:
      assert(false);
    case Ownership::OWN:
      if (referenceM->location == Location::INLINE) {
        if (auto structReferend = dynamic_cast<StructReferend *>(referenceM->referend)) {
          return referendStructs.getWrapperStruct(structReferend);
        } else {
          assert(false);
        }
      } else {
        return translateReferenceSimple(globalState, &referendStructs, referenceM->referend);
      }
    case Ownership::BORROW:
    case Ownership::WEAK:
      assert(referenceM->location != Location::INLINE);
      return translateWeakReference(globalState, &weakRefStructs, referenceM->referend);
    default:
      assert(false);
  }
}

Ref ResilientV4::upcastWeak(
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

void ResilientV4::declareKnownSizeArray(
    KnownSizeArrayDefinitionT *knownSizeArrayMT) {
  globalState->regionIdByReferend.emplace(knownSizeArrayMT->referend, getRegionId());

  referendStructs.declareKnownSizeArray(knownSizeArrayMT);
}

void ResilientV4::declareUnknownSizeArray(
    UnknownSizeArrayDefinitionT *unknownSizeArrayMT) {
  globalState->regionIdByReferend.emplace(unknownSizeArrayMT->referend, getRegionId());

  referendStructs.declareUnknownSizeArray(unknownSizeArrayMT);
}

void ResilientV4::defineUnknownSizeArray(
    UnknownSizeArrayDefinitionT *unknownSizeArrayMT) {
  auto elementLT =
      globalState->getRegion(unknownSizeArrayMT->rawArray->elementType)
          ->translateType(unknownSizeArrayMT->rawArray->elementType);
  referendStructs.defineUnknownSizeArray(unknownSizeArrayMT, elementLT);
}

void ResilientV4::defineKnownSizeArray(
    KnownSizeArrayDefinitionT *knownSizeArrayMT) {
  auto elementLT =
      globalState->getRegion(knownSizeArrayMT->rawArray->elementType)
          ->translateType(knownSizeArrayMT->rawArray->elementType);
  referendStructs.defineKnownSizeArray(knownSizeArrayMT, elementLT);
}

void ResilientV4::declareStruct(
    StructDefinition *structM) {
  globalState->regionIdByReferend.emplace(structM->referend, getRegionId());

  referendStructs.declareStruct(structM->referend);
}

void ResilientV4::defineStruct(StructDefinition *structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        globalState->getRegion(structM->members[i]->type)
            ->translateType(structM->members[i]->type));
  }
  referendStructs.defineStruct(structM->referend, innerStructMemberTypesL);
}

void ResilientV4::declareEdge(Edge *edge) {
  referendStructs.declareEdge(edge);
}

void ResilientV4::defineEdge(Edge *edge) {
  auto interfaceFunctionsLT = globalState->getInterfaceFunctionTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  referendStructs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);
}

void ResilientV4::declareInterface(InterfaceDefinition *interfaceM) {
  globalState->regionIdByReferend.emplace(interfaceM->referend, getRegionId());
  referendStructs.declareInterface(interfaceM);
}

void ResilientV4::defineInterface(InterfaceDefinition *interfaceM) {
  auto interfaceMethodTypesL = globalState->getInterfaceFunctionTypes(interfaceM->referend);
  referendStructs.defineInterface(interfaceM, interfaceMethodTypesL);
}

void ResilientV4::discardOwningRef(
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

void ResilientV4::noteWeakableDestroyed(
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

void ResilientV4::storeMember(
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
std::tuple<LLVMValueRef, LLVMValueRef> ResilientV4::explodeInterfaceRef(
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

Ref ResilientV4::getUnknownSizeArrayLength(
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

LLVMValueRef ResilientV4::checkValidReference(
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
Ref ResilientV4::upgradeLoadResultToRefWithTargetOwnership(
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

void ResilientV4::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *weakRefMT,
    Ref weakRef) {
  return hgmWeaks.aliasWeakRef(from, functionState, builder, weakRefMT, weakRef);
}

void ResilientV4::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *weakRefMT,
    Ref weakRef) {
  return hgmWeaks.discardWeakRef(from, functionState, builder, weakRefMT, weakRef);
}

LLVMValueRef ResilientV4::getCensusObjectId(
    AreaAndFileAndLine checkerAFL,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refM,
    Ref ref) {
  auto controlBlockPtrLE =
      referendStructs.getControlBlockPtr(checkerAFL, functionState, builder, ref, refM);
  return referendStructs.getObjIdFromControlBlockPtr(builder, refM->referend, controlBlockPtrLE);
}

Ref ResilientV4::getIsAliveFromWeakRef(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *weakRefM,
    Ref weakRef,
    bool knownLive) {
  return hgmWeaks.getIsAliveFromWeakRef(functionState, builder, weakRefM, weakRef, knownLive);
}

// Returns object ID
void ResilientV4::fillControlBlock(
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

LoadResult ResilientV4::loadElementFromKSA(
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

LoadResult ResilientV4::loadElementFromUSA(
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

Ref ResilientV4::storeElementInUSA(
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

Ref ResilientV4::upcast(
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
      return upcastStrong(
          globalState, functionState, builder, &referendStructs, sourceStructMT, sourceStructReferendM,
          sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
    }
    case Ownership::BORROW:
    case Ownership::WEAK: {
      return ::upcastWeak(
          globalState, functionState, builder, &weakRefStructs, sourceStructMT, sourceStructReferendM,
          sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
    }
    default:
      assert(false);
  }
}


LLVMValueRef ResilientV4::predictShallowSize(LLVMBuilderRef builder, Referend* referend, LLVMValueRef lenIntLE) {
  assert(globalState->getRegion(referend) == this);
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    return constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, referendStructs.getWrapperStruct(structReferend)));
  } else if (dynamic_cast<Str*>(referend)) {
    auto headerBytesLE =
        constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, referendStructs.getStringWrapperStruct()));
    return LLVMBuildAdd(builder, headerBytesLE, lenIntLE, "sum");
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT*>(referend)) {
    auto headerBytesLE =
        constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, referendStructs.getUnknownSizeArrayWrapperStruct(usaMT)));

    auto elementRefMT = globalState->program->getUnknownSizeArray(usaMT->name)->rawArray->elementType;
    auto elementRefLT = globalState->getRegion(elementRefMT)->translateType(elementRefMT);

    auto sizePerElement = LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(elementRefLT, 1));
    // The above line tries to include padding... if the below fails, we know there are some serious shenanigans
    // going on in LLVM.
    assert(sizePerElement * 2 == LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(elementRefLT, 2)));
    auto elementsSizeLE = LLVMBuildMul(builder, constI64LE(globalState, sizePerElement), lenIntLE, "elementsSize");

    return LLVMBuildAdd(builder, headerBytesLE, elementsSizeLE, "sum");
  } else if (auto hostKsaMT = dynamic_cast<KnownSizeArrayT*>(referend)) {
    auto headerBytesLE =
        constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, referendStructs.getKnownSizeArrayWrapperStruct(hostKsaMT)));

    auto elementRefMT = globalState->program->getUnknownSizeArray(usaMT->name)->rawArray->elementType;
    auto elementRefLT = translateType(elementRefMT);

    auto sizePerElement = LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(elementRefLT, 1));
    // The above line tries to include padding... if the below fails, we know there are some serious shenanigans
    // going on in LLVM.
    assert(sizePerElement * 2 == LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(elementRefLT, 2)));
    auto elementsSizeLE = LLVMBuildMul(builder, constI64LE(globalState, sizePerElement), lenIntLE, "elementsSize");

    return LLVMBuildAdd(builder, headerBytesLE, elementsSizeLE, "sum");
  } else assert(false);
}

void ResilientV4::deallocate(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refMT,
    Ref ref) {
  auto sourceRefLE = checkValidReference(FL(), functionState, builder, refMT, ref);
  auto controlBlockPtrLE = referendStructs.getControlBlockPtr(FL(), functionState, builder, sourceRefLE, refMT);
  auto sourceWrapperPtrLE = referendStructs.makeWrapperPtr(from, functionState, builder, refMT, sourceRefLE);
  auto sourceContentsPtrLE = referendStructs.getStructContentsPtr(builder, refMT->referend, sourceWrapperPtrLE);

  auto controlBlock = referendStructs.getControlBlock(refMT->referend);
  auto tetherMemberIndex = controlBlock->getMemberIndex(ControlBlockMember::TETHER_32B);
  auto tetherPtrLE = LLVMBuildStructGEP(builder, controlBlockPtrLE.refLE, tetherMemberIndex, "tetherPtr");
  auto tetherI32LE = LLVMBuildLoad(builder, tetherPtrLE, "tetherI32");
  auto tetheredLE = LLVMBuildTrunc(builder, tetherI32LE, LLVMInt1TypeInContext(globalState->context), "wasAlive");
  buildVoidIfElse(
      globalState, functionState, builder, tetheredLE,
      [this, functionState, sourceContentsPtrLE, refMT, ref, sourceWrapperPtrLE](LLVMBuilderRef thenBuilder) {
        buildFlare(FL(), globalState, functionState, thenBuilder, "still tethered, undeadifying!");
        auto sourceContentsI8PtrLE = LLVMBuildPointerCast(thenBuilder, sourceContentsPtrLE, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "sourceContentsI8Ptr");
//        auto structReferend = dynamic_cast<StructReferend*>(refMT->referend);
//        assert(structReferend);
//        auto structLT = referendStructs.getInnerStruct(structReferend);

        LLVMValueRef lenLE = nullptr;
        if (auto usaMT = dynamic_cast<UnknownSizeArrayT*>(refMT->referend)) {
          buildFlare(FL(), globalState, functionState, thenBuilder);
          lenLE =
              globalState->getRegion(globalState->metalCache->intRef)->checkValidReference(
                  FL(), functionState, thenBuilder, globalState->metalCache->intRef,
                  getUnknownSizeArrayLength(functionState, thenBuilder, refMT, ref, true));
        } else if (auto ksaMT = dynamic_cast<KnownSizeArrayT*>(refMT->referend)) {
          buildFlare(FL(), globalState, functionState, thenBuilder);
          lenLE =
              globalState->getRegion(globalState->metalCache->intRef)->checkValidReference(
                  FL(), functionState, thenBuilder, globalState->metalCache->intRef,
                  getUnknownSizeArrayLength(functionState, thenBuilder, refMT, ref, true));
        } else if (dynamic_cast<StructReferend*>(refMT->referend)) {
          buildFlare(FL(), globalState, functionState, thenBuilder);
          lenLE = constI64LE(globalState, 0);
        } else if (dynamic_cast<InterfaceReferend*>(refMT->referend)) {
          buildFlare(FL(), globalState, functionState, thenBuilder);
          lenLE = constI64LE(globalState, 0);
        }
        auto sizeLE = predictShallowSize(thenBuilder, refMT->referend, lenLE);
        buildFlare(FL(), globalState, functionState, thenBuilder, "size: ", sizeLE);

        std::vector<LLVMValueRef> argsLE = { sourceContentsI8PtrLE, constI8LE(globalState, 0), sizeLE };
        LLVMBuildCall(thenBuilder, globalState->externs->memset, argsLE.data(), argsLE.size(), "");
        buildFlare(FL(), globalState, functionState, thenBuilder, "done!");

        hgmWeaks.addToUndeadCycle(functionState, thenBuilder, refMT, sourceWrapperPtrLE);
      },
      [this, from, functionState, refMT, ref](LLVMBuilderRef elseBuilder) {
        buildFlare(FL(), globalState, functionState, elseBuilder);
        innerDeallocate(from, globalState, functionState, &referendStructs, elseBuilder, refMT, ref);
      });
}

Ref ResilientV4::constructUnknownSizeArray(
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

Ref ResilientV4::loadMember(
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

void ResilientV4::checkInlineStructType(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refMT,
    Ref ref) {
  auto argLE = checkValidReference(FL(), functionState, builder, refMT, ref);
  auto structReferend = dynamic_cast<StructReferend *>(refMT->referend);
  assert(structReferend);
  assert(LLVMTypeOf(argLE) == referendStructs.getInnerStruct(structReferend));
}


std::string ResilientV4::getMemberArbitraryRefNameCSeeMMEDT(Reference *refMT) {
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
  } else {
    assert(false);
  }
}

void ResilientV4::generateUnknownSizeArrayDefsC(
    std::unordered_map<std::string, std::string> *cByExportedName,
    UnknownSizeArrayDefinitionT *usaDefM) {
}

void ResilientV4::generateKnownSizeArrayDefsC(
    std::unordered_map<std::string, std::string> *cByExportedName,
    KnownSizeArrayDefinitionT *usaDefM) {
}

void ResilientV4::generateStructDefsC(
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

void ResilientV4::generateInterfaceDefsC(
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

Reference *ResilientV4::getExternalType(Reference *refMT) {
  return refMT;
}

Ref ResilientV4::receiveAndDecryptFamiliarReference(
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

LLVMTypeRef ResilientV4::getInterfaceMethodVirtualParamAnyType(Reference *reference) {
  switch (reference->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE:
      return LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
    case Ownership::BORROW:
    case Ownership::WEAK:
      return weakRefStructs.getWeakVoidRefStruct(reference->referend);
  }
}

Ref ResilientV4::receiveUnencryptedAlienReference(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceRefMT,
    Reference *targetRefMT,
    Ref sourceRef) {
  assert(false);
  exit(1);
}

Ref ResilientV4::encryptAndSendFamiliarReference(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceRefMT,
    Ref sourceRef) {
  // Someday we'll do some encryption stuff here
  return sourceRef;
}

void ResilientV4::initializeElementInUSA(
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

Ref ResilientV4::deinitializeElementFromUSA(
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

void ResilientV4::initializeElementInKSA(
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

Ref ResilientV4::deinitializeElementFromKSA(
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

Weakability ResilientV4::getReferendWeakability(Referend *referend) {
  if (auto structReferend = dynamic_cast<StructReferend *>(referend)) {
    return globalState->lookupStruct(structReferend->fullName)->weakability;
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend *>(referend)) {
    return globalState->lookupInterface(interfaceReferend->fullName)->weakability;
  } else {
    return Weakability::NON_WEAKABLE;
  }
}

LLVMValueRef ResilientV4::getInterfaceMethodFunctionPtr(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *virtualParamMT,
    Ref virtualArgRef,
    int indexInEdge) {
  return getInterfaceMethodFunctionPtrFromItable(
      globalState, functionState, builder, virtualParamMT, virtualArgRef, indexInEdge);
}

void ResilientV4::untether(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    LLVMValueRef localAddr) {
  auto localStructValueLE = LLVMBuildLoad(builder, localAddr, "localStruct");
  auto sourceRefLE = LLVMBuildExtractValue(builder, localStructValueLE, 0, "ref");
  auto wasAliveLE = LLVMBuildExtractValue(builder, localStructValueLE, 1, "wasAlive");
  auto sourceWeakFatPtrLE = weakRefStructs.makeWeakFatPtr(local->type, sourceRefLE);
  assert(local->type->ownership == Ownership::BORROW);
  ControlBlockPtrLE controlBlockPtrLE =
    (dynamic_cast<InterfaceReferend*>(local->type->referend)) ? [&](){
      auto interfaceFatPtrLE =
          referendStructs.makeInterfaceFatPtr(
              FL(), functionState, builder, local->type,
              fatWeaks.getInnerRefFromWeakRef(functionState, builder, local->type, sourceWeakFatPtrLE));
      return referendStructs.getControlBlockPtr(FL(), functionState, builder, local->type->referend, interfaceFatPtrLE);
    }() : [&](){
      auto wrapperPtrLE =
          referendStructs.makeWrapperPtr(
              FL(), functionState, builder, local->type,
              fatWeaks.getInnerRefFromWeakRef(functionState, builder, local->type, sourceWeakFatPtrLE));
      return referendStructs.getControlBlockPtr(FL(), functionState, builder, wrapperPtrLE.refLE, local->type);
    }();

  auto controlBlock = referendStructs.getControlBlock(local->type->referend);
  auto tetherMemberIndex = controlBlock->getMemberIndex(ControlBlockMember::TETHER_32B);
  auto tetherPtrLE = LLVMBuildStructGEP(builder, controlBlockPtrLE.refLE, tetherMemberIndex, "tetherPtr");
  auto wasAliveI32LE = LLVMBuildZExt(builder, wasAliveLE, LLVMInt32TypeInContext(globalState->context), "wasAliveI32");
  LLVMBuildStore(builder, wasAliveI32LE, tetherPtrLE);
}

Ref ResilientV4::loadAndUntether(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  buildFlare(FL(), globalState, functionState, builder);
  untether(functionState, builder, local, localAddr);
  buildFlare(FL(), globalState, functionState, builder);
  return loadLocal(functionState, builder, local, localAddr);
}

void ResilientV4::storeAndTether(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    Ref refToStore,
    bool knownLive,
    LLVMValueRef localAddr) {
  LLVMTypeRef wrapperStructLT = nullptr;
  if (auto structRReferend = dynamic_cast<StructReferend*>(local->type->referend)) {
    wrapperStructLT = referendStructs.getWrapperStruct(structRReferend);
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT*>(local->type->referend)) {
    wrapperStructLT = referendStructs.getUnknownSizeArrayWrapperStruct(usaMT);
  } else {
    assert(false);
  }
  auto wrapperStructPtrLT = LLVMPointerType(wrapperStructLT, 0);
  auto maybeAliveRefLE = checkValidReference(FL(), functionState, builder, local->type, refToStore);
  auto weakFatPtrLE = weakRefStructs.makeWeakFatPtr(local->type, maybeAliveRefLE);
  auto innerRefLE = fatWeaks.getInnerRefFromWeakRef(functionState, builder, local->type, weakFatPtrLE);
  auto wrapperPtrLE = referendStructs.makeWrapperPtr(FL(), functionState, builder, local->type, innerRefLE);

  auto halfProtectedWrapperPtrLE =
      hgmWeaks.getHalfProtectedPtr(functionState, builder, local->type, wrapperStructPtrLT);

  auto isAliveLE =
      hgmWeaks.getIsAliveFromWeakFatPtr(functionState, builder, local->type, weakFatPtrLE, knownLive);
  // If it's alive, refLE will point to the object. Dereferencing it is fine.
  // If it's dead, refLE will point to a half protected object. Can change its tether, but not dereference its contents.
  assert(wrapperPtrLE.refM == halfProtectedWrapperPtrLE.refM);
  assert(LLVMTypeOf(wrapperPtrLE.refLE) == LLVMTypeOf(halfProtectedWrapperPtrLE.refLE));
  auto newWrapperPtrLE =
      referendStructs.makeWrapperPtr(
          FL(), functionState, builder, local->type,
          LLVMBuildSelect(
              builder, isAliveLE, wrapperPtrLE.refLE, halfProtectedWrapperPtrLE.refLE, "clearableRef"));

  std::unique_ptr<WeakFatPtrLE> newWeakFatPtrU;
  if (auto structRReferend = dynamic_cast<StructReferend*>(local->type->referend)) {
    newWeakFatPtrU =
        std::unique_ptr<WeakFatPtrLE>{
          new WeakFatPtrLE(
            hgmWeaks.assembleStructWeakRef(
                functionState, builder, local->type, local->type, structRReferend, newWrapperPtrLE))};
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT*>(local->type->referend)) {
    newWeakFatPtrU =
        std::unique_ptr<WeakFatPtrLE>{
            new WeakFatPtrLE(
                hgmWeaks.assembleUnknownSizeArrayWeakRef(
                    functionState, builder, local->type, usaMT, local->type, newWrapperPtrLE))};
  } else {
    assert(false);
  }
  auto newWeakFatPtr = *newWeakFatPtrU;



//  auto controlBlockPtrLE = referendStructs.getControlBlockPtr(FL(), functionState, builder, refToStore, local->type);
//  auto sourceWrapperPtrLE = referendStructs.makeWrapperPtr(FL(), functionState, builder, local->type, maybeAliveRefLE);
//  auto sourceContentsPtrLE = referendStructs.getStructContentsPtr(builder, local->type->referend, sourceWrapperPtrLE);

  auto controlBlock = referendStructs.getControlBlock(local->type->referend);
  auto tetherMemberIndex = controlBlock->getMemberIndex(ControlBlockMember::TETHER_32B);
  auto controlBlockPtrLE = referendStructs.getConcreteControlBlockPtr(FL(), functionState, builder, local->type, newWrapperPtrLE);
  auto tetherPtrLE = LLVMBuildStructGEP(builder, controlBlockPtrLE.refLE, tetherMemberIndex, "tetherPtr");
  auto tetherI32LE = LLVMBuildLoad(builder, tetherPtrLE, "tetherI32");
  auto wasTetheredLE = LLVMBuildTrunc(builder, tetherI32LE, LLVMInt1TypeInContext(globalState->context), "wasAlive");

  buildFlare(FL(), globalState, functionState, builder, "Tethering!");
  LLVMBuildStore(builder, constI32LE(globalState, 1), tetherPtrLE);

  auto refMemberPtrLE = LLVMBuildStructGEP(builder, localAddr, 0, "refMemberPtr");
  LLVMBuildStore(builder, newWeakFatPtr.refLE, refMemberPtrLE);

  auto wasTetheredMemberPtrLE = LLVMBuildStructGEP(builder, localAddr, 1, "wasTetheredMemberPtr");
  LLVMBuildStore(builder, wasTetheredLE, wasTetheredMemberPtrLE);
}

LLVMValueRef ResilientV4::stackify(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    Ref refToStore,
    bool knownLive) {
  auto boolLT = LLVMInt1TypeInContext(globalState->context);
  auto refTypeLT = translateType(local->type);

  switch (local->type->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE:
    case Ownership::WEAK: {
      auto refLE = checkValidReference(FL(), functionState, builder, local->type, refToStore);
      return makeMidasLocal(functionState, builder, refTypeLT, local->id->maybeName.c_str(), refLE);
    }
    case Ownership::BORROW: {
      if (local->keepAlive) {
        std::vector<LLVMTypeRef> localStructMembers = {refTypeLT, boolLT};
        auto structLT = LLVMStructType(localStructMembers.data(), localStructMembers.size(), false);
        auto localAddr = LLVMBuildAlloca(functionState->localsBuilder, structLT, local->id->maybeName.c_str());
        storeAndTether(functionState, builder, local, refToStore, knownLive, localAddr);
        return localAddr;
      } else {
        auto refLE = checkValidReference(FL(), functionState, builder, local->type, refToStore);
        return makeMidasLocal(functionState, builder, refTypeLT, local->id->maybeName.c_str(), refLE);
      }
    }
    default:
      assert(false);
  }
  return nullptr;
}

Ref ResilientV4::loadLocal(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  switch (local->type->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE:
    case Ownership::WEAK: {
      auto refLE = LLVMBuildLoad(builder, localAddr, "loaded");
      buildFlare(FL(), globalState, functionState, builder);
      return wrap(this, local->type, refLE);
    }
    case Ownership::BORROW: {
      if (local->keepAlive) {
        auto localStructValueLE = LLVMBuildLoad(builder, localAddr, (local->id->maybeName + "__andWasAlive").c_str());
        auto sourceRefLE = LLVMBuildExtractValue(builder, localStructValueLE, 0, local->id->maybeName.c_str());
        auto sourceRef = wrap(this, local->type, sourceRefLE);
        checkValidReference(FL(), functionState, builder, local->type, sourceRef);
        buildFlare(FL(), globalState, functionState, builder);
        return sourceRef;
      } else {
        buildFlare(FL(), globalState, functionState, builder);
        auto refLE = LLVMBuildLoad(builder, localAddr, "loaded");
        buildFlare(FL(), globalState, functionState, builder);
        return wrap(this, local->type, refLE);
      }
    }
  }
}

Ref ResilientV4::unstackify(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  switch (local->type->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE:
    case Ownership::WEAK: {
      auto refLE = LLVMBuildLoad(builder, localAddr, "loaded");
      return wrap(this, local->type, refLE);
    }
    case Ownership::BORROW:
      if (local->keepAlive) {
        buildFlare(FL(), globalState, functionState, builder);
        return loadAndUntether(functionState, builder, local, localAddr);
      } else {
        auto refLE = LLVMBuildLoad(builder, localAddr, "loaded");
        return wrap(this, local->type, refLE);
      }
    default:
      assert(false);
  }
}

Ref ResilientV4::localStore(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    LLVMValueRef localAddr,
    Ref refToStore,
    bool knownLive) {
  auto oldRef = unstackify(functionState, builder, local, localAddr);

  switch (local->type->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE:
    case Ownership::WEAK: {
      auto refLE = checkValidReference(FL(), functionState, builder, local->type, refToStore);
      LLVMBuildStore(builder, refLE, localAddr);
      break;
    }
    case Ownership::BORROW: {
      if (local->keepAlive) {
        storeAndTether(functionState, builder, local, refToStore, knownLive, localAddr);
      } else {
        auto refLE = checkValidReference(FL(), functionState, builder, local->type, refToStore);
        LLVMBuildStore(builder, refLE, localAddr);
      }
      break;
    }
    default:
      assert(false);
  }
  return oldRef;
}
