#include <region/common/fatweaks/fatweaks.h>
#include <region/common/hgm/hgm.h>
#include <region/common/lgtweaks/lgtweaks.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include <translatetype.h>
#include <region/common/common.h>
#include <utils/counters.h>
#include <region/common/controlblock.h>
#include <utils/branch.h>
#include <region/common/heap.h>
#include <function/expressions/shared/members.h>
#include <function/expressions/shared/elements.h>
#include <function/expressions/shared/string.h>
#include "unsafe.h"
#include <sstream>

Unsafe::Unsafe(GlobalState* globalState_) :
    globalState(globalState_),
    mutNonWeakableStructs(globalState, makeFastNonWeakableControlBlock(globalState)),
    mutWeakableStructs(
        globalState,
        makeFastWeakableControlBlock(globalState),
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
  regionLT = LLVMStructCreateNamed(globalState->context, "__Unsafe_Region");
  LLVMStructSetBody(regionLT, nullptr, 0, false);
}

void Unsafe::mainSetup(FunctionState* functionState, LLVMBuilderRef builder) {
  wrcWeaks.mainSetup(functionState, builder);
}

void Unsafe::mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) {
  wrcWeaks.mainCleanup(functionState, builder);
}

RegionId* Unsafe::getRegionId() {
  return globalState->metalCache->unsafeRegionId;
}

Ref Unsafe::constructKnownSizeArray(
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
  return resultRef;
}

Ref Unsafe::mallocStr(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE,
    LLVMValueRef sourceCharsPtrLE) {
  assert(false);
  exit(1);
}

Ref Unsafe::allocate(
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

void Unsafe::alias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
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
    } else if (sourceRef->ownership == Ownership::BORROW) {
      // Do nothing, fast mode doesn't do stuff for borrow refs.
    } else if (sourceRef->ownership == Ownership::WEAK) {
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

void Unsafe::dealias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto sourceRnd = sourceMT->referend;

  if (sourceMT->ownership == Ownership::SHARE) {
    assert(false);
  } else {
    if (sourceMT->ownership == Ownership::OWN) {
      // This can happen if we're sending an owning reference to the outside world, see DEPAR.
    } else if (sourceMT->ownership == Ownership::BORROW) {
      // Do nothing!
    } else if (sourceMT->ownership == Ownership::WEAK) {
      discardWeakRef(from, functionState, builder, sourceMT, sourceRef);
    } else assert(false);
  }
}

Ref Unsafe::weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) {
  return regularWeakAlias(globalState, functionState, &referendStructs, &wrcWeaks, builder, sourceRefMT, targetRefMT, sourceRef);
}

// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
WrapperPtrLE Unsafe::lockWeakRef(
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

Ref Unsafe::lockWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference* resultOptTypeM,
    Reference* constraintRefM,
    Reference* sourceWeakRefMT,
    Ref sourceWeakRefLE,
    bool weakRefKnownLive,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {

  assert(sourceWeakRefMT->ownership == Ownership::WEAK);
  auto isAliveLE =
      getIsAliveFromWeakRef(
          functionState, builder, sourceWeakRefMT, sourceWeakRefLE, weakRefKnownLive);
  auto resultOptTypeLE = globalState->getRegion(resultOptTypeM)->translateType(resultOptTypeM);
  return regularInnerLockWeak(
      globalState, functionState, builder, thenResultIsNever, elseResultIsNever, resultOptTypeM,
      constraintRefM, sourceWeakRefMT, sourceWeakRefLE, buildThen, buildElse,
      isAliveLE, resultOptTypeLE, &weakRefStructs, &fatWeaks);
}


Ref Unsafe::asSubtype(
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

  return regularInnerAsSubtype(
      globalState, functionState, builder, thenResultIsNever, elseResultIsNever, resultOptTypeM, constraintRefM,
      sourceInterfaceRefMT, sourceInterfaceRef, sourceRefKnownLive, targetReferend, buildThen, buildElse);
}

LLVMTypeRef Unsafe::translateType(Reference* referenceM) {
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

Ref Unsafe::upcastWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {
  auto resultWeakInterfaceFatPtr =
      wrcWeaks.weakStructPtrToWrciWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
          sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
  return wrap(this, targetInterfaceTypeM, resultWeakInterfaceFatPtr);
}

void Unsafe::declareKnownSizeArray(
    KnownSizeArrayDefinitionT* knownSizeArrayMT) {
  globalState->regionIdByReferend.emplace(knownSizeArrayMT->referend, getRegionId());

  referendStructs.declareKnownSizeArray(knownSizeArrayMT);
}

void Unsafe::declareUnknownSizeArray(
    UnknownSizeArrayDefinitionT* unknownSizeArrayMT) {
  globalState->regionIdByReferend.emplace(unknownSizeArrayMT->referend, getRegionId());

  referendStructs.declareUnknownSizeArray(unknownSizeArrayMT);
}

void Unsafe::defineUnknownSizeArray(
    UnknownSizeArrayDefinitionT* unknownSizeArrayMT) {
  auto elementLT =
      globalState->getRegion(unknownSizeArrayMT->rawArray->elementType)
          ->translateType(unknownSizeArrayMT->rawArray->elementType);
  referendStructs.defineUnknownSizeArray(unknownSizeArrayMT, elementLT);
}

void Unsafe::defineKnownSizeArray(
    KnownSizeArrayDefinitionT* knownSizeArrayMT) {
  auto elementLT =
      globalState->getRegion(knownSizeArrayMT->rawArray->elementType)
          ->translateType(knownSizeArrayMT->rawArray->elementType);
  referendStructs.defineKnownSizeArray(knownSizeArrayMT, elementLT);
}

void Unsafe::declareStruct(
    StructDefinition* structM) {
  globalState->regionIdByReferend.emplace(structM->referend, getRegionId());

  referendStructs.declareStruct(structM->referend);
}

void Unsafe::defineStruct(
    StructDefinition* structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        globalState->getRegion(structM->members[i]->type)
            ->translateType(structM->members[i]->type));
  }
  referendStructs.defineStruct(structM->referend, innerStructMemberTypesL);
}

void Unsafe::declareEdge(
    Edge* edge) {
  referendStructs.declareEdge(edge);
}

void Unsafe::defineEdge(
    Edge* edge) {
  auto interfaceFunctionsLT = globalState->getInterfaceFunctionTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  referendStructs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);
}

void Unsafe::declareInterface(
    InterfaceDefinition* interfaceM) {
  globalState->regionIdByReferend.emplace(interfaceM->referend, getRegionId());

  referendStructs.declareInterface(interfaceM);
}

void Unsafe::defineInterface(
    InterfaceDefinition* interfaceM) {
  auto interfaceMethodTypesL = globalState->getInterfaceFunctionTypes(interfaceM->referend);
  referendStructs.defineInterface(interfaceM, interfaceMethodTypesL);
}

void Unsafe::discardOwningRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  // Free it!
  deallocate(AFL("discardOwningRef"), functionState, builder, sourceMT, sourceRef);
}

void Unsafe::noteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtrLE) {
  // In fast mode, only shared things are strong RC'd
  if (refM->ownership == Ownership::SHARE) {
    assert(false);
//    // Only shared stuff is RC'd in fast mode
//    auto rcIsZeroLE = strongRcIsZero(globalState, &referendStructs, builder, refM, controlBlockPtrLE);
//    buildAssert(globalState, functionState, builder, rcIsZeroLE,
//        "Tried to free concrete that had nonzero RC!");
  } else {
    // It's a mutable, so mark WRCs dead

    if (auto structReferendM = dynamic_cast<StructReferend *>(refM->referend)) {
      auto structM = globalState->program->getStruct(structReferendM->fullName);
      if (structM->weakability == Weakability::WEAKABLE) {
        wrcWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
      }
    } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
      auto interfaceM = globalState->program->getStruct(interfaceReferendM->fullName);
      if (interfaceM->weakability == Weakability::WEAKABLE) {
        wrcWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
      }
    } else {
      // Do nothing, only structs and interfaces are weakable in assist mode.
    }
  }
}

void Unsafe::storeMember(
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
    case Ownership::OWN:
    case Ownership::SHARE:
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
std::tuple<LLVMValueRef, LLVMValueRef> Unsafe::explodeInterfaceRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef) {
  switch (virtualParamMT->ownership) {
    case Ownership::OWN:
    case Ownership::BORROW:
    case Ownership::SHARE: {
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

Ref Unsafe::getUnknownSizeArrayLength(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    Ref arrayRef,
    bool arrayKnownLive) {
  return getUnknownSizeArrayLengthStrong(globalState, functionState, builder, &referendStructs, usaRefMT, arrayRef);
}

LLVMValueRef Unsafe::checkValidReference(
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
  assert(LLVMTypeOf(refLE) == translateType(refM));

  if (refM->ownership == Ownership::OWN) {
    regularCheckValidReference(checkerAFL, globalState, functionState, builder, &referendStructs, refM, refLE);
  } else if (refM->ownership == Ownership::SHARE) {
    assert(false);
  } else {
    if (refM->ownership == Ownership::BORROW) {
      regularCheckValidReference(checkerAFL, globalState, functionState, builder,
          &referendStructs, refM, refLE);
    } else if (refM->ownership == Ownership::WEAK) {
      wrcWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
    } else
      assert(false);
  }
  return refLE;
}

// TODO maybe combine with alias/acquireReference?
// After we load from a local, member, or element, we can feed the result through this
// function to turn it into a desired ownership.
// Example:
// - Can load from an owning ref member to get a constraint ref.
// - Can load from a constraint ref member to get a weak ref.
Ref Unsafe::upgradeLoadResultToRefWithTargetOwnership(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
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
    } else if (targetOwnership == Ownership::BORROW) {
      auto resultRef = transmutePtr(globalState, functionState, builder, sourceType, targetType, sourceRef);
      checkValidReference(FL(),
                          functionState, builder, targetType, resultRef);
      return resultRef;
    } else if (targetOwnership == Ownership::WEAK) {
      return wrcWeaks.assembleWeakRef(functionState, builder, sourceType, targetType, sourceRef);
    } else {
      assert(false);
    }
  } else if (sourceOwnership == Ownership::BORROW) {
    buildFlare(FL(), globalState, functionState, builder);

    if (targetOwnership == Ownership::OWN) {
      assert(false); // Cant load an owning reference from a constraint ref local.
    } else if (targetOwnership == Ownership::BORROW) {
      return sourceRef;
    } else if (targetOwnership == Ownership::WEAK) {
      // Making a weak ref from a constraint ref local.
      assert(dynamic_cast<StructReferend*>(sourceType->referend) || dynamic_cast<InterfaceReferend*>(sourceType->referend));
      return wrcWeaks.assembleWeakRef(functionState, builder, sourceType, targetType, sourceRef);
    } else {
      assert(false);
    }
  } else if (sourceOwnership == Ownership::WEAK) {
    assert(targetOwnership == Ownership::WEAK);
    return sourceRef;
  } else {
    assert(false);
  }
  assert(false);
}

void Unsafe::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  return wrcWeaks.aliasWeakRef(from, functionState, builder, weakRefMT, weakRef);
}

void Unsafe::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  return wrcWeaks.discardWeakRef(from, functionState, builder, weakRefMT, weakRef);
}

LLVMValueRef Unsafe::getCensusObjectId(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref ref) {
  auto controlBlockPtrLE =
      referendStructs.getControlBlockPtr(checkerAFL, functionState, builder, ref, refM);
  return referendStructs.getObjIdFromControlBlockPtr(builder, refM->referend, controlBlockPtrLE);
}

Ref Unsafe::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef,
    bool knownLive) {
  return wrcWeaks.getIsAliveFromWeakRef(functionState, builder, weakRefM, weakRef);
}

// Returns object ID
void Unsafe::fillControlBlock(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName) {

  LLVMValueRef newControlBlockLE = LLVMGetUndef(referendStructs.getControlBlock(referendM)->getStruct());

  newControlBlockLE =
      fillControlBlockCensusFields(
          from, globalState, functionState, &referendStructs, builder, referendM, newControlBlockLE, typeName);

  if (globalState->getReferendWeakability(referendM) == Weakability::WEAKABLE) {
    newControlBlockLE = wrcWeaks.fillWeakableControlBlock(functionState, builder, &referendStructs, referendM,
        newControlBlockLE);
  }

  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE.refLE);
}

LoadResult Unsafe::loadElementFromKSA(
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

LoadResult Unsafe::loadElementFromUSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
  return regularLoadElementFromUSAWithoutUpgrade(
      globalState, functionState, builder, &referendStructs, usaRefMT, usaMT, usaDef->rawArray->mutability, usaDef->rawArray->elementType, arrayRef, arrayKnownLive, indexRef);
}

Ref Unsafe::storeElementInUSA(
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

Ref Unsafe::upcast(
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructMT,
    StructReferend* sourceStructReferendM,
    Ref sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {

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


void Unsafe::deallocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  innerDeallocate(from, globalState, functionState, &referendStructs, builder, refMT, ref);
}

Ref Unsafe::constructUnknownSizeArray(
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
  return resultRef;
}

Ref Unsafe::loadMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {

  if (structRefMT->ownership == Ownership::SHARE) {
    assert(false);
  } else {
    auto unupgradedMemberLE =
        regularLoadMember(
            globalState, functionState, builder, &referendStructs, structRefMT, structRef,
            memberIndex, expectedMemberType, targetType, memberName);
    return upgradeLoadResultToRefWithTargetOwnership(
        functionState, builder, expectedMemberType, targetType, unupgradedMemberLE);
  }
}

void Unsafe::checkInlineStructType(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  auto argLE = checkValidReference(FL(), functionState, builder, refMT, ref);
  auto structReferend = dynamic_cast<StructReferend*>(refMT->referend);
  assert(structReferend);
  assert(LLVMTypeOf(argLE) == referendStructs.getInnerStruct(structReferend));
}


std::string Unsafe::getMemberArbitraryRefNameCSeeMMEDT(Reference* refMT) {
  if (refMT->ownership == Ownership::SHARE) {
    assert(false);
  } else if (auto structRefMT = dynamic_cast<StructReferend*>(refMT->referend)) {
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
  } else if (auto interfaceMT = dynamic_cast<InterfaceReferend*>(refMT->referend)) {
    return globalState->program->getMemberArbitraryExportNameSeeMMEDT(interfaceMT->fullName) + "Ref";
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT*>(refMT->referend)) {
    return globalState->program->getMemberArbitraryExportNameSeeMMEDT(usaMT->name) + "Ref";
  } else if (auto ksaMT = dynamic_cast<KnownSizeArrayT*>(refMT->referend)) {
    return globalState->program->getMemberArbitraryExportNameSeeMMEDT(ksaMT->name) + "Ref";
  } else {
    assert(false);
  }
}

void Unsafe::generateStructDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName, StructDefinition* structDefM) {
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

void Unsafe::generateInterfaceDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName, InterfaceDefinition* interfaceDefM) {
//      return "void* unused; void* unused;";
  assert(false); // impl
}

void Unsafe::generateUnknownSizeArrayDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName,
    UnknownSizeArrayDefinitionT* usaDefM) {
  if (usaDefM->rawArray->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    for (auto baseName : globalState->program->getExportedNames(usaDefM->name)) {
      auto refTypeName = baseName + "Ref";
      std::stringstream s;
      s << "typedef struct " << refTypeName << " { void* unused; } " << refTypeName << ";" << std::endl;
      cByExportedName->insert(std::make_pair(baseName, s.str()));
    }
  }
}

void Unsafe::generateKnownSizeArrayDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName,
    KnownSizeArrayDefinitionT* ksaDefM) {
  if (ksaDefM->rawArray->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    for (auto baseName : globalState->program->getExportedNames(ksaDefM->name)) {
      auto refTypeName = baseName + "Ref";
      std::stringstream s;
      s << "typedef struct " << refTypeName << " { void* unused; } " << refTypeName << ";" << std::endl;
      cByExportedName->insert(std::make_pair(baseName, s.str()));
    }
  }
}

Reference* Unsafe::getExternalType(Reference* refMT) {
  return refMT;
//  if (refMT->ownership == Ownership::SHARE) {
//    assert(false);
//  } else {
//    if (auto structReferend = dynamic_cast<StructReferend*>(refMT->referend)) {
//      return LLVMPointerType(referendStructs.getWrapperStruct(structReferend), 0);
//    } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(refMT->referend)) {
//      assert(false); // impl
//    } else {
//      std::cerr << "Invalid type for extern!" << std::endl;
//      assert(false);
//    }
//  }
//  assert(false);
}

Ref Unsafe::receiveAndDecryptFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Ref sourceRef) {
  // Someday, we'll do some encryption stuff here.
  return sourceRef;
}

LLVMTypeRef Unsafe::getInterfaceMethodVirtualParamAnyType(Reference* reference) {
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

Ref Unsafe::receiveUnencryptedAlienReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef) {
  assert(false);
  exit(1);
}

Ref Unsafe::encryptAndSendFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Ref sourceRef) {
  // Someday, we'll do some encryption stuff here.
  return sourceRef;
}

void Unsafe::initializeElementInUSA(
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

Ref Unsafe::deinitializeElementFromUSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef) {
  auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
  return regularLoadElementFromUSAWithoutUpgrade(
      globalState, functionState, builder, &referendStructs, usaRefMT, usaMT, usaDef->rawArray->mutability, usaDef->rawArray->elementType, arrayRef, true, indexRef).move();
}

void Unsafe::initializeElementInKSA(
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

Ref Unsafe::deinitializeElementFromKSA(
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

Weakability Unsafe::getReferendWeakability(Referend* referend) {
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    return globalState->lookupStruct(structReferend->fullName)->weakability;
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
    return globalState->lookupInterface(interfaceReferend->fullName)->weakability;
  } else {
    return Weakability::NON_WEAKABLE;
  }
}

LLVMValueRef Unsafe::getInterfaceMethodFunctionPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    int indexInEdge) {
  return getInterfaceMethodFunctionPtrFromItable(
      globalState, functionState, builder, virtualParamMT, virtualArgRef, indexInEdge);
}

LLVMValueRef Unsafe::stackify(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    Ref refToStore,
    bool knownLive) {
  auto toStoreLE = checkValidReference(FL(), functionState, builder, local->type, refToStore);
  auto typeLT = translateType(local->type);
  return makeMidasLocal(functionState, builder, typeLT, local->id->maybeName.c_str(), toStoreLE);
}

Ref Unsafe::unstackify(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return loadLocal(functionState, builder, local, localAddr);
}

Ref Unsafe::loadLocal(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return normalLocalLoad(globalState, functionState, builder, local, localAddr);
}

Ref Unsafe::localStore(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore, bool knownLive) {
  return normalLocalStore(globalState, functionState, builder, local, localAddr, refToStore);
}
