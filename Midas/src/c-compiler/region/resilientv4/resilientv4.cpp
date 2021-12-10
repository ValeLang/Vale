#include <region/common/fatweaks/fatweaks.h>
#include <region/common/hgm/hgm.h>
#include <memory>
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
  controlBlock.addMember(ControlBlockMember::GENERATION_32B);
  // This is where we put the size in the current generational heap, but only when it's free.
  // When it's alive, we can use it for things, like the tether here.
  controlBlock.addMember(ControlBlockMember::TETHER_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.build();
  return controlBlock;
}

StructKind* makeAny(GlobalState* globalState, RegionId* regionId) {
  auto structKind =
      globalState->metalCache->getStructKind(
          globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__ValeHGMV4_Any"));
  auto iter = globalState->regionIdByKind.emplace(structKind, regionId).first;
  assert(globalState->regionIdByKind[structKind] == regionId);
  return structKind;
}

ResilientV4::ResilientV4(GlobalState *globalState_, RegionId *regionId_) :
    globalState(globalState_),
    regionId(regionId_),
    kindStructs(
        globalState,
        makeResilientV4WeakableControlBlock(globalState),
        makeResilientV4WeakableControlBlock(globalState),
        HybridGenerationalMemory::makeWeakRefHeaderStruct(globalState, regionId)),
    fatWeaks(globalState_, &kindStructs),
    anyMT(makeAny(globalState, regionId)),
    hgmWeaks(
        globalState_,
        &kindStructs,
        globalState->opt->elideChecksForKnownLive,
        false,
        anyMT) {
  kindStructs.declareStruct(anyMT, Weakability::NON_WEAKABLE);
  kindStructs.defineStruct(anyMT, {});
}

void ResilientV4::mainSetup(FunctionState* functionState, LLVMBuilderRef builder) {
//  hgmWeaks.mainSetup(functionState, builder);
}

void ResilientV4::mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) {
//  hgmWeaks.mainCleanup(functionState, builder);
}

RegionId *ResilientV4::getRegionId() {
  return regionId;
}

Ref ResilientV4::constructStaticSizedArray(
    Ref regionInstanceRef,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *referenceM,
    StaticSizedArrayT *kindM) {
  auto ssaDef = globalState->program->getStaticSizedArray(kindM);
  auto resultRef =
      ::constructStaticSizedArray(
          globalState, functionState, builder, referenceM, kindM, &kindStructs,
          [this, functionState, referenceM, kindM](LLVMBuilderRef innerBuilder,
                                                   ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(),
                functionState,
                innerBuilder,
                referenceM->kind,
                controlBlockPtrLE,
                kindM->name->name);
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
  auto structKind = dynamic_cast<StructKind *>(desiredReference->kind);
  auto structM = globalState->program->getStruct(structKind);
  auto resultRef =
      innerAllocate(
          FL(), globalState, functionState, builder, desiredReference, &kindStructs, memberRefs,
          Weakability::WEAKABLE,
          [this, functionState, desiredReference, structM](LLVMBuilderRef innerBuilder,
                                                           ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(), functionState, innerBuilder, desiredReference->kind,
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
  auto sourceRnd = sourceRef->kind;

  if (dynamic_cast<Int *>(sourceRnd) ||
      dynamic_cast<Bool *>(sourceRnd) ||
      dynamic_cast<Float *>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (dynamic_cast<InterfaceKind *>(sourceRnd) ||
             dynamic_cast<StructKind *>(sourceRnd) ||
             dynamic_cast<StaticSizedArrayT *>(sourceRnd) ||
             dynamic_cast<RuntimeSizedArrayT *>(sourceRnd) ||
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
        adjustStrongRc(from, globalState, functionState, &kindStructs, builder, expr, sourceRef, 1);
      }
    } else
      assert(false);
  } else {
    std::cerr << "Unimplemented type in acquireReference: "
              << typeid(*sourceRef->kind).name() << std::endl;
    assert(false);
  }
}

void ResilientV4::dealias(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceMT,
    Ref sourceRef) {
  auto sourceRnd = sourceMT->kind;

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
      globalState, functionState, builder, sourceRefMT, targetRefMT, &kindStructs, sourceRef);
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
      return kindStructs.makeWrapperPtr(FL(), functionState, builder, refM, weakFatPtrLE);
    }
    case Ownership::BORROW:
    case Ownership::WEAK: {
      auto weakFatPtrLE =
          kindStructs.makeWeakFatPtr(
              refM,
              checkValidReference(
                  FL(), functionState, builder, refM, weakRefLE));
      return kindStructs.makeWrapperPtr(
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
      buildThen, buildElse, isAliveLE, resultOptTypeLE, &kindStructs);
}

Ref ResilientV4::asSubtype(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* resultOptTypeM,
    Reference* sourceInterfaceRefMT,
    Ref sourceInterfaceRef,
    bool sourceRefKnownLive,
    Kind* targetKind,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {
  auto targetStructKind = dynamic_cast<StructKind*>(targetKind);
  assert(targetStructKind);
  auto sourceInterfaceKind = dynamic_cast<InterfaceKind*>(sourceInterfaceRefMT->kind);
  assert(sourceInterfaceKind);

  return resilientDowncast(
      globalState, functionState, builder, &kindStructs, &kindStructs, resultOptTypeM, sourceInterfaceRefMT, sourceInterfaceRef,
      targetKind, buildThen, buildElse, targetStructKind, sourceInterfaceKind);
}

LLVMTypeRef ResilientV4::translateType(Reference *referenceM) {
  switch (referenceM->ownership) {
    case Ownership::SHARE:
      assert(false);
    case Ownership::OWN:
      if (referenceM->location == Location::INLINE) {
        if (auto structKind = dynamic_cast<StructKind *>(referenceM->kind)) {
          return kindStructs.getStructWrapperStruct(structKind);
        } else {
          assert(false);
        }
      } else {
        return translateReferenceSimple(globalState, &kindStructs, referenceM->kind);
      }
    case Ownership::BORROW:
    case Ownership::WEAK:
      assert(referenceM->location != Location::INLINE);
      return translateWeakReference(globalState, &kindStructs, referenceM->kind);
    default:
      assert(false);
  }
}

Ref ResilientV4::upcastWeak(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructKind *sourceStructKindM,
    Reference *sourceStructTypeM,
    InterfaceKind *targetInterfaceKindM,
    Reference *targetInterfaceTypeM) {
  auto resultWeakInterfaceFatPtr =
      hgmWeaks.weakStructPtrToGenWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructKindM,
          sourceStructTypeM, targetInterfaceKindM, targetInterfaceTypeM);
  return wrap(this, targetInterfaceTypeM, resultWeakInterfaceFatPtr);
}

void ResilientV4::declareStaticSizedArray(
    StaticSizedArrayDefinitionT *staticSizedArrayMT) {
  globalState->regionIdByKind.emplace(staticSizedArrayMT->kind, getRegionId());

  // All SSAs are weakable in resilient mode.
  auto weakability = Weakability::WEAKABLE;
  kindStructs.declareStaticSizedArray(staticSizedArrayMT->kind, weakability);
}

void ResilientV4::declareRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT *runtimeSizedArrayMT) {
  globalState->regionIdByKind.emplace(runtimeSizedArrayMT->kind, getRegionId());

  // All SSAs are weakable in resilient mode.
  auto weakability = Weakability::WEAKABLE;
  kindStructs.declareRuntimeSizedArray(runtimeSizedArrayMT->kind, weakability);
}

void ResilientV4::defineRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT *runtimeSizedArrayMT) {
  auto elementLT =
      globalState->getRegion(runtimeSizedArrayMT->elementType)
          ->translateType(runtimeSizedArrayMT->elementType);
  kindStructs.defineRuntimeSizedArray(runtimeSizedArrayMT, elementLT, true);
}

void ResilientV4::defineStaticSizedArray(
    StaticSizedArrayDefinitionT *staticSizedArrayMT) {
  auto elementLT =
      globalState->getRegion(staticSizedArrayMT->elementType)
          ->translateType(staticSizedArrayMT->elementType);
  kindStructs.defineStaticSizedArray(staticSizedArrayMT, elementLT);
}

void ResilientV4::declareStruct(
    StructDefinition *structM) {
  globalState->regionIdByKind.emplace(structM->kind, getRegionId());

  // Note how it's not:
  //   auto weakability = structM->weakability;
  // This is because all structs are weakable in resilient mode.
  auto weakability = Weakability::WEAKABLE;
  kindStructs.declareStruct(structM->kind, weakability);
}

void ResilientV4::defineStruct(StructDefinition *structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        globalState->getRegion(structM->members[i]->type)
            ->translateType(structM->members[i]->type));
  }
  kindStructs.defineStruct(structM->kind, innerStructMemberTypesL);
}

void ResilientV4::declareEdge(Edge *edge) {
  kindStructs.declareEdge(edge);
}

void ResilientV4::defineEdge(Edge *edge) {
  auto interfaceFunctionsLT = globalState->getInterfaceFunctionTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  kindStructs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);
}

void ResilientV4::declareInterface(InterfaceDefinition *interfaceM) {
  globalState->regionIdByKind.emplace(interfaceM->kind, getRegionId());

  // Note how it's not:
  //   auto weakability = interfaceM->weakability;
  // This is because all interfaces are weakable in resilient mode.
  auto weakability = Weakability::WEAKABLE;
  kindStructs.declareInterface(interfaceM->kind, weakability);
}

void ResilientV4::defineInterface(InterfaceDefinition *interfaceM) {
  auto interfaceMethodTypesL = globalState->getInterfaceFunctionTypes(interfaceM->kind);
  kindStructs.defineInterface(interfaceM, interfaceMethodTypesL);
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
//    auto rcIsZeroLE = strongRcIsZero(globalState, &kindStructs, builder, refM, controlBlockPtrLE);
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
          globalState, functionState, builder, &kindStructs, structRefMT, structRef,
          structKnownLive, memberIndex, memberName, newMemberLE);
    }
    case Ownership::BORROW:
    case Ownership::WEAK: {
      storeMemberWeak(
          globalState, functionState, builder, &kindStructs, structRefMT, structRef,
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
          globalState, functionState, builder, &kindStructs, virtualParamMT, virtualArgRef);
    }
    case Ownership::BORROW:
    case Ownership::WEAK: {
      return explodeWeakInterfaceRef(
          globalState, functionState, builder, &kindStructs, &fatWeaks, &kindStructs,
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

Ref ResilientV4::getRuntimeSizedArrayLength(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *rsaRefMT,
    Ref arrayRef,
    bool arrayKnownLive) {
  switch (rsaRefMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN: {
      return getRuntimeSizedArrayLengthStrong(globalState, functionState, builder, &kindStructs, rsaRefMT, arrayRef);
    }
    case Ownership::BORROW: {
      auto wrapperPtrLE =
          lockWeakRef(
              FL(), functionState, builder, rsaRefMT, arrayRef, arrayKnownLive);
      return ::getRuntimeSizedArrayLength(globalState, functionState, builder, wrapperPtrLE);
    }
    case Ownership::WEAK:
      assert(false); // VIR never loads from a weak ref
  }
}

Ref ResilientV4::getRuntimeSizedArrayCapacity(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *rsaRefMT,
    Ref arrayRef,
    bool arrayKnownLive) {
  switch (rsaRefMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN: {
      return getRuntimeSizedArrayCapacityStrong(globalState, functionState, builder, &kindStructs, rsaRefMT, arrayRef);
    }
    case Ownership::BORROW: {
      auto wrapperPtrLE =
          lockWeakRef(
              FL(), functionState, builder, rsaRefMT, arrayRef, arrayKnownLive);
      return ::getRuntimeSizedArrayCapacity(globalState, functionState, builder, wrapperPtrLE);
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
      regularCheckValidReference(checkerAFL, globalState, functionState, builder, &kindStructs, refM, refLE);
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
      kindStructs.getControlBlockPtr(checkerAFL, functionState, builder, ref, refM);
  return kindStructs.getObjIdFromControlBlockPtr(builder, refM->kind, controlBlockPtrLE);
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
    Kind *kindM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string &typeName) {

  gmFillControlBlock(
      from, globalState, functionState, &kindStructs, builder, kindM, controlBlockPtrLE,
      typeName, &hgmWeaks);
}

LoadResult ResilientV4::loadElementFromSSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *ssaRefMT,
    StaticSizedArrayT *ssaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto ssaDef = globalState->program->getStaticSizedArray(ssaMT);
  return resilientloadElementFromSSA(
      globalState, functionState, builder, ssaRefMT, ssaMT, ssaDef->size, ssaDef->mutability,
      ssaDef->elementType, arrayRef, arrayKnownLive, indexRef, &kindStructs);
}

LoadResult ResilientV4::loadElementFromRSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *rsaRefMT,
    RuntimeSizedArrayT *rsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  return resilientLoadElementFromRSAWithoutUpgrade(
      globalState, functionState, builder, &kindStructs, true, rsaRefMT, rsaDef->mutability,
      rsaDef->elementType, rsaMT, arrayRef, arrayKnownLive, indexRef);
}

Ref ResilientV4::storeElementInRSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *rsaRefMT,
    RuntimeSizedArrayT *rsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  auto arrayWrapperPtrLE = lockWeakRef(FL(), functionState, builder, rsaRefMT, arrayRef, arrayKnownLive);
  auto sizeRef = ::getRuntimeSizedArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
  auto arrayElementsPtrLE = getRuntimeSizedArrayContentsPtr(builder, true, arrayWrapperPtrLE);
  buildFlare(FL(), globalState, functionState, builder);
  return ::swapElement(
      globalState, functionState, builder, rsaRefMT->location, rsaDef->elementType, sizeRef,
      arrayElementsPtrLE,
      indexRef, elementRef);
}

Ref ResilientV4::upcast(
    FunctionState *functionState,
    LLVMBuilderRef builder,

    Reference *sourceStructMT,
    StructKind *sourceStructKindM,
    Ref sourceRefLE,

    Reference *targetInterfaceTypeM,
    InterfaceKind *targetInterfaceKindM) {

  switch (sourceStructMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN: {
      return upcastStrong(
          globalState, functionState, builder, &kindStructs, sourceStructMT, sourceStructKindM,
          sourceRefLE, targetInterfaceTypeM, targetInterfaceKindM);
    }
    case Ownership::BORROW:
    case Ownership::WEAK: {
      return ::upcastWeak(
          globalState, functionState, builder, &kindStructs, sourceStructMT, sourceStructKindM,
          sourceRefLE, targetInterfaceTypeM, targetInterfaceKindM);
    }
    default:
      assert(false);
  }
}


void ResilientV4::deallocate(
    AreaAndFileAndLine from,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refMT,
    Ref ref) {
  auto sourceRefLE = checkValidReference(FL(), functionState, builder, refMT, ref);
  auto controlBlockPtrLE = kindStructs.getControlBlockPtr(FL(), functionState, builder, sourceRefLE, refMT);
  auto sourceWrapperPtrLE = kindStructs.makeWrapperPtr(from, functionState, builder, refMT, sourceRefLE);
  auto sourceContentsPtrLE = kindStructs.getStructContentsPtr(builder, refMT->kind, sourceWrapperPtrLE);

  auto controlBlock = kindStructs.getControlBlock(refMT->kind);
  auto tetherMemberIndex = controlBlock->getMemberIndex(ControlBlockMember::TETHER_32B);
  auto tetherPtrLE = LLVMBuildStructGEP(builder, controlBlockPtrLE.refLE, tetherMemberIndex, "tetherPtr");
  auto tetherI32LE = LLVMBuildLoad(builder, tetherPtrLE, "tetherI32");
  auto tetheredLE = LLVMBuildTrunc(builder, tetherI32LE, LLVMInt1TypeInContext(globalState->context), "wasAlive");
  buildVoidIfElse(
      globalState, functionState, builder, tetheredLE,
      [this, functionState, sourceContentsPtrLE, refMT, ref, sourceWrapperPtrLE](LLVMBuilderRef thenBuilder) {
        buildPrint(globalState, thenBuilder, "Tried to deallocate an object while borrowed!");
        // See MPESC for status codes
        auto exitCodeIntLE = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 14, false);
        LLVMBuildCall(thenBuilder, globalState->externs->exit, &exitCodeIntLE, 1, "");
      },
      [this, from, functionState, refMT, ref](LLVMBuilderRef elseBuilder) {
        buildFlare(FL(), globalState, functionState, elseBuilder);
        innerDeallocate(from, globalState, functionState, &kindStructs, elseBuilder, refMT, ref);
      });
}

Ref ResilientV4::constructRuntimeSizedArray(
    Ref regionInstanceRef,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *rsaMT,
    RuntimeSizedArrayT *runtimeSizedArrayT,
    Ref capacityRef,
    const std::string &typeName) {
  auto rsaWrapperPtrLT =
      kindStructs.getRuntimeSizedArrayWrapperStruct(runtimeSizedArrayT);
  auto rsaDef = globalState->program->getRuntimeSizedArray(runtimeSizedArrayT);
  auto elementType = globalState->program->getRuntimeSizedArray(runtimeSizedArrayT)->elementType;
  auto rsaElementLT = globalState->getRegion(elementType)->translateType(elementType);
  auto resultRef =
      ::constructRuntimeSizedArray(
          globalState, functionState, builder, &kindStructs, rsaMT, rsaDef->elementType,
          runtimeSizedArrayT,
          rsaWrapperPtrLT, rsaElementLT, globalState->constI32(0), capacityRef, true, typeName,
          [this, functionState, runtimeSizedArrayT, typeName](
              LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(), functionState, innerBuilder, runtimeSizedArrayT, controlBlockPtrLE, typeName);
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
                  globalState, functionState, builder, &kindStructs, structRefMT, structRef,
                  memberIndex, expectedMemberType, targetType, memberName);
          return upgradeLoadResultToRefWithTargetOwnership(
              functionState, builder, expectedMemberType, targetType, unupgradedMemberLE);
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto memberLE =
              resilientLoadWeakMember(
                  globalState, functionState, builder, &kindStructs, structRefMT,
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
  auto structKind = dynamic_cast<StructKind *>(refMT->kind);
  assert(structKind);
  assert(LLVMTypeOf(argLE) == kindStructs.getStructInnerStruct(structKind));
}

std::string ResilientV4::generateRuntimeSizedArrayDefsC(
    Package* currentPackage,
    RuntimeSizedArrayDefinitionT* rsaDefM) {
  assert(rsaDefM->mutability == Mutability::MUTABLE);
  return generateMutableConcreteHandleDefC(currentPackage, currentPackage->getKindExportName(rsaDefM->kind, true));
}

std::string ResilientV4::generateStaticSizedArrayDefsC(
    Package* currentPackage,
    StaticSizedArrayDefinitionT* ssaDefM) {
  assert(ssaDefM->mutability == Mutability::MUTABLE);
  return generateMutableConcreteHandleDefC(currentPackage, currentPackage->getKindExportName(ssaDefM->kind, true));
}

std::string ResilientV4::generateStructDefsC(
    Package* currentPackage, StructDefinition* structDefM) {
  assert(structDefM->mutability == Mutability::MUTABLE);
  return generateMutableConcreteHandleDefC(currentPackage, currentPackage->getKindExportName(structDefM->kind, true));
}

std::string ResilientV4::generateInterfaceDefsC(
    Package* currentPackage, InterfaceDefinition* interfaceDefM) {
  assert(interfaceDefM->mutability == Mutability::MUTABLE);
  return generateMutableInterfaceHandleDefC(currentPackage, currentPackage->getKindExportName(interfaceDefM->kind, true));
}


LLVMTypeRef ResilientV4::getExternalType(Reference *refMT) {
  if (dynamic_cast<StructKind*>(refMT->kind) ||
      dynamic_cast<StaticSizedArrayT*>(refMT->kind) ||
      dynamic_cast<RuntimeSizedArrayT*>(refMT->kind)) {
    return globalState->getConcreteHandleStruct();
  } else if (dynamic_cast<InterfaceKind*>(refMT->kind)) {
    return globalState->getInterfaceHandleStruct();
  } else {
    assert(false);
  }
}

Ref ResilientV4::receiveAndDecryptFamiliarReference(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceRefMT,
    LLVMValueRef sourceRefLE) {
  assert(sourceRefMT->ownership != Ownership::SHARE);
  return resilientReceiveAndDecryptFamiliarReference(
      globalState, functionState, builder, &kindStructs, &kindStructs, &hgmWeaks, sourceRefMT, sourceRefLE);
}

LLVMTypeRef ResilientV4::getInterfaceMethodVirtualParamAnyType(Reference *reference) {
  switch (reference->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE:
      return LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
    case Ownership::BORROW:
    case Ownership::WEAK:
      return kindStructs.getWeakVoidRefStruct(reference->kind);
  }
}

std::pair<Ref, Ref> ResilientV4::receiveUnencryptedAlienReference(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceRefMT,
    Reference *targetRefMT,
    Ref sourceRef) {
  assert(false);
  exit(1);
}

LLVMValueRef ResilientV4::encryptAndSendFamiliarReference(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *sourceRefMT,
    Ref sourceRef) {
  assert(sourceRefMT->ownership != Ownership::SHARE);
  return resilientEncryptAndSendFamiliarReference(
      globalState, functionState, builder, &kindStructs, &hgmWeaks, sourceRefMT, sourceRef);
}

void ResilientV4::pushRuntimeSizedArrayNoBoundsCheck(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *rsaRefMT,
    RuntimeSizedArrayT *rsaMT,
    Ref rsaRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto arrayWrapperPtrLE =
      lockWeakRef(FL(), functionState, builder, rsaRefMT, rsaRef, arrayRefKnownLive);
  ::initializeElementInRSA(
      globalState, functionState, builder, &kindStructs, true, true, rsaMT, rsaRefMT, arrayWrapperPtrLE, rsaRef, indexRef, elementRef);
}

Ref ResilientV4::popRuntimeSizedArrayNoBoundsCheck(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *rsaRefMT,
    RuntimeSizedArrayT *rsaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  auto elementLE = resilientLoadElementFromRSAWithoutUpgrade(
      globalState, functionState, builder, &kindStructs, true, rsaRefMT, rsaDef->mutability,
      rsaDef->elementType, rsaMT, arrayRef, true, indexRef).move();
  auto rsaWrapperPtrLE = lockWeakRef(FL(), functionState, builder, rsaRefMT, arrayRef, arrayRefKnownLive);
  decrementRSASize(globalState, functionState, &kindStructs, builder, rsaRefMT, rsaWrapperPtrLE);
  return elementLE;
}

void ResilientV4::initializeElementInSSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *ssaRefMT,
    StaticSizedArrayT *ssaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto ssaDef = globalState->program->getStaticSizedArray(ssaMT);
  auto arrayWrapperPtrLE =
      kindStructs.makeWrapperPtr(
          FL(), functionState, builder, ssaRefMT,
          globalState->getRegion(ssaRefMT)->checkValidReference(FL(), functionState, builder, ssaRefMT, arrayRef));
  auto sizeRef = globalState->constI32(ssaDef->size);
  auto arrayElementsPtrLE = getStaticSizedArrayContentsPtr(builder, arrayWrapperPtrLE);
  ::initializeElementWithoutIncrementSize(
      globalState, functionState, builder, ssaRefMT->location, ssaDef->elementType, sizeRef, arrayElementsPtrLE,
      indexRef, elementRef);
}

Ref ResilientV4::deinitializeElementFromSSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *ssaRefMT,
    StaticSizedArrayT *ssaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef) {
  assert(false);
  exit(1);
}

Weakability ResilientV4::getKindWeakability(Kind *kind) {
  if (auto structKind = dynamic_cast<StructKind *>(kind)) {
    return globalState->lookupStruct(structKind)->weakability;
  } else if (auto interfaceKind = dynamic_cast<InterfaceKind *>(kind)) {
    return globalState->lookupInterface(interfaceKind)->weakability;
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
  auto sourceWeakFatPtrLE = kindStructs.makeWeakFatPtr(local->type, sourceRefLE);
  assert(local->type->ownership == Ownership::BORROW);
  ControlBlockPtrLE controlBlockPtrLE =
      (dynamic_cast<InterfaceKind*>(local->type->kind)) ? [&](){
        auto interfaceFatPtrLE =
            kindStructs.makeInterfaceFatPtr(
                FL(), functionState, builder, local->type,
                fatWeaks.getInnerRefFromWeakRef(functionState, builder, local->type, sourceWeakFatPtrLE));
        return kindStructs.getControlBlockPtr(FL(), functionState, builder, local->type->kind, interfaceFatPtrLE);
      }() : [&](){
        auto wrapperPtrLE =
            kindStructs.makeWrapperPtr(
                FL(), functionState, builder, local->type,
                fatWeaks.getInnerRefFromWeakRef(functionState, builder, local->type, sourceWeakFatPtrLE));
        return kindStructs.getControlBlockPtr(FL(), functionState, builder, wrapperPtrLE.refLE, local->type);
      }();

  auto controlBlock = kindStructs.getControlBlock(local->type->kind);
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
  if (auto structRKind = dynamic_cast<StructKind*>(local->type->kind)) {
    wrapperStructLT = kindStructs.getStructWrapperStruct(structRKind);
  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(local->type->kind)) {
    wrapperStructLT = kindStructs.getRuntimeSizedArrayWrapperStruct(rsaMT);
  } else {
    assert(false);
  }
  auto wrapperStructPtrLT = LLVMPointerType(wrapperStructLT, 0);
  auto maybeAliveRefLE = checkValidReference(FL(), functionState, builder, local->type, refToStore);
  auto weakFatPtrLE = kindStructs.makeWeakFatPtr(local->type, maybeAliveRefLE);
  auto innerRefLE = fatWeaks.getInnerRefFromWeakRef(functionState, builder, local->type, weakFatPtrLE);
  auto wrapperPtrLE = kindStructs.makeWrapperPtr(FL(), functionState, builder, local->type, innerRefLE);

  hgmWeaks.lockGenFatPtr(FL(), functionState, builder, local->type, weakFatPtrLE, knownLive);
//  auto isAliveLE =
//      hgmWeaks.getIsAliveFromWeakFatPtr(functionState, builder, local->type, weakFatPtrLE, knownLive);
//  // If it's alive, refLE will point to the object. Dereferencing it is fine.
//  // If it's dead, refLE will point to a half protected object. Can change its tether, but not dereference its contents.
//  assert(wrapperPtrLE.refM == halfProtectedWrapperPtrLE.refM);
//  assert(LLVMTypeOf(wrapperPtrLE.refLE) == LLVMTypeOf(halfProtectedWrapperPtrLE.refLE));
  auto newWrapperPtrLE =
      kindStructs.makeWrapperPtr(
          FL(), functionState, builder, local->type,
          wrapperPtrLE.refLE);
//          LLVMBuildSelect(
//              builder, isAliveLE, wrapperPtrLE.refLE, halfProtectedWrapperPtrLE.refLE, "clearableRef"));

  std::unique_ptr<WeakFatPtrLE> newWeakFatPtrU;
  if (auto structRKind = dynamic_cast<StructKind*>(local->type->kind)) {
    newWeakFatPtrU =
        std::make_unique<WeakFatPtrLE>(
            hgmWeaks.assembleStructWeakRef(
                functionState, builder, local->type, local->type, structRKind, newWrapperPtrLE));
  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(local->type->kind)) {
    newWeakFatPtrU =
        std::unique_ptr<WeakFatPtrLE>{
            new WeakFatPtrLE(
                hgmWeaks.assembleRuntimeSizedArrayWeakRef(
                    functionState, builder, local->type, rsaMT, local->type, newWrapperPtrLE))};
  } else {
    assert(false);
  }
  auto newWeakFatPtr = *newWeakFatPtrU;



//  auto controlBlockPtrLE = kindStructs.getControlBlockPtr(FL(), functionState, builder, refToStore, local->type);
//  auto sourceWrapperPtrLE = kindStructs.makeWrapperPtr(FL(), functionState, builder, local->type, maybeAliveRefLE);
//  auto sourceContentsPtrLE = kindStructs.getStructContentsPtr(builder, local->type->kind, sourceWrapperPtrLE);

  auto controlBlock = kindStructs.getControlBlock(local->type->kind);
  auto tetherMemberIndex = controlBlock->getMemberIndex(ControlBlockMember::TETHER_32B);
  auto controlBlockPtrLE = kindStructs.getConcreteControlBlockPtr(FL(), functionState, builder, local->type, newWrapperPtrLE);
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

std::string ResilientV4::getExportName(
    Package* package,
    Reference* reference,
    bool includeProjectName) {
  return package->getKindExportName(reference->kind, includeProjectName) + (reference->location == Location::YONDER ? "Ref" : "");
}

LLVMValueRef ResilientV4::predictShallowSize(FunctionState* functionState, LLVMBuilderRef builder, bool includeHeader, Kind* kind, LLVMValueRef lenI32LE) {
  auto lenI64LE = LLVMBuildZExt(builder, lenI32LE, LLVMInt64TypeInContext(globalState->context), "lenI32");
  assert(globalState->getRegion(kind) == this);
  if (auto structKind = dynamic_cast<StructKind*>(kind)) {
    auto structLT =
        includeHeader ? kindStructs.getStructWrapperStruct(structKind) : kindStructs.getStructInnerStruct(structKind);
    auto size = LLVMABISizeOfType(globalState->dataLayout, structLT);
    return constI64LE(globalState, size);
  } else if (dynamic_cast<Str*>(kind)) {
    auto sizeLE = lenI64LE;
    if (includeHeader) {
      auto headerBytesLE =
          constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, kindStructs.getStringWrapperStruct()));
      sizeLE =  LLVMBuildAdd(builder, headerBytesLE, lenI64LE, "sum");
    }
    return sizeLE;
  } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(kind)) {
    auto elementRefMT = globalState->program->getStaticSizedArray(ssaMT)->elementType;
    auto elementRefLT = globalState->getRegion(elementRefMT)->translateType(elementRefMT);

    auto sizePerElement = LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(elementRefLT, 1));
    // The above line tries to include padding... if the below fails, we know there are some serious shenanigans
    // going on in LLVM.
    assert(sizePerElement * 2 == LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(elementRefLT, 2)));
    auto elementsSizeLE = LLVMBuildMul(builder, constI64LE(globalState, sizePerElement), lenI64LE, "elementsSize");

    auto sizeLE = elementsSizeLE;
    if (includeHeader) {
      auto headerBytesLE =
          constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, kindStructs.getStaticSizedArrayWrapperStruct(ssaMT)));
      sizeLE = LLVMBuildAdd(builder, headerBytesLE, elementsSizeLE, "sum");
    }
    return sizeLE;
  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(kind)) {
    auto elementRefMT = globalState->program->getRuntimeSizedArray(rsaMT)->elementType;
    auto elementRefLT = globalState->getRegion(elementRefMT)->translateType(elementRefMT);

    auto sizePerElement = LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(elementRefLT, 1));
    // The above line tries to include padding... if the below fails, we know there are some serious shenanigans
    // going on in LLVM.
    assert(sizePerElement * 2 == LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(elementRefLT, 2)));
    auto elementsSizeLE = LLVMBuildMul(builder, constI64LE(globalState, sizePerElement), lenI64LE, "elementsSize");

    auto sizeLE = elementsSizeLE;
    if (includeHeader) {
      auto headerBytesLE =
          constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, kindStructs.getStaticSizedArrayWrapperStruct(ssaMT)));
      sizeLE = LLVMBuildAdd(builder, headerBytesLE, elementsSizeLE, "sum");
    }
    return sizeLE;
  } else assert(false);
}
