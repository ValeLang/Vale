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
    kindStructs(
        globalState,
        [this](Kind* kind) -> IKindStructsSource* {
          if (globalState->getKindWeakability(kind) == Weakability::NON_WEAKABLE) {
            return &mutNonWeakableStructs;
          } else {
            return &mutWeakableStructs;
          }
        }),
    weakRefStructs(
        [this](Kind* kind) -> IWeakRefStructsSource* {
            if (globalState->getKindWeakability(kind) == Weakability::NON_WEAKABLE) {
              assert(false);
            } else {
              return &mutWeakableStructs;
            }
        }),
    fatWeaks(globalState_, &weakRefStructs),
    wrcWeaks(globalState_, &kindStructs, &weakRefStructs) {
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

Ref Unsafe::constructStaticSizedArray(
    Ref regionInstanceRef,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *referenceM,
    StaticSizedArrayT *kindM) {
  auto ssaDef = globalState->program->getStaticSizedArray(kindM);
  auto resultRef =
      ::constructStaticSizedArray(
          globalState, functionState, builder, referenceM, kindM, &kindStructs,
          [this, functionState, referenceM, kindM](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(),
                functionState,
                innerBuilder,
                referenceM->kind,
                controlBlockPtrLE,
                kindM->name->name);
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
  auto structKind = dynamic_cast<StructKind*>(desiredReference->kind);
  auto structM = globalState->program->getStruct(structKind);
  auto resultRef =
      innerAllocate(
          FL(), globalState, functionState, builder, desiredReference, &kindStructs, memberRefs, Weakability::WEAKABLE,
          [this, functionState, desiredReference, structM](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(), functionState, innerBuilder, desiredReference->kind,
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
    } else if (sourceRef->ownership == Ownership::BORROW) {
      // Do nothing, fast mode doesn't do stuff for borrow refs.
    } else if (sourceRef->ownership == Ownership::WEAK) {
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

void Unsafe::dealias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto sourceRnd = sourceMT->kind;

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
  return regularWeakAlias(globalState, functionState, &kindStructs, &wrcWeaks, builder, sourceRefMT, targetRefMT, sourceRef);
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
      return kindStructs.makeWrapperPtr(
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
    Kind* targetKind,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {

  return regularDowncast(
      globalState, functionState, builder, thenResultIsNever, elseResultIsNever, resultOptTypeM, constraintRefM,
      sourceInterfaceRefMT, sourceInterfaceRef, sourceRefKnownLive, targetKind, buildThen, buildElse);
}

LLVMTypeRef Unsafe::translateType(Reference* referenceM) {
  switch (referenceM->ownership) {
    case Ownership::SHARE:
      assert(false);
    case Ownership::OWN:
    case Ownership::BORROW:
      assert(referenceM->location != Location::INLINE);
      return translateReferenceSimple(globalState, &kindStructs, referenceM->kind);
    case Ownership::WEAK:
      assert(referenceM->location != Location::INLINE);
      return translateWeakReference(globalState, &weakRefStructs, referenceM->kind);
    default:
      assert(false);
  }
}

Ref Unsafe::upcastWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructKind* sourceStructKindM,
    Reference* sourceStructTypeM,
    InterfaceKind* targetInterfaceKindM,
    Reference* targetInterfaceTypeM) {
  auto resultWeakInterfaceFatPtr =
      wrcWeaks.weakStructPtrToWrciWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructKindM,
          sourceStructTypeM, targetInterfaceKindM, targetInterfaceTypeM);
  return wrap(this, targetInterfaceTypeM, resultWeakInterfaceFatPtr);
}

void Unsafe::declareStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT) {
  globalState->regionIdByKind.emplace(staticSizedArrayMT->kind, getRegionId());

  kindStructs.declareStaticSizedArray(staticSizedArrayMT);
}

void Unsafe::declareRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) {
  globalState->regionIdByKind.emplace(runtimeSizedArrayMT->kind, getRegionId());

  kindStructs.declareRuntimeSizedArray(runtimeSizedArrayMT);
}

void Unsafe::defineRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) {
  auto elementLT =
      globalState->getRegion(runtimeSizedArrayMT->rawArray->elementType)
          ->translateType(runtimeSizedArrayMT->rawArray->elementType);
  kindStructs.defineRuntimeSizedArray(runtimeSizedArrayMT, elementLT);
}

void Unsafe::defineStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT) {
  auto elementLT =
      globalState->getRegion(staticSizedArrayMT->rawArray->elementType)
          ->translateType(staticSizedArrayMT->rawArray->elementType);
  kindStructs.defineStaticSizedArray(staticSizedArrayMT, elementLT);
}

void Unsafe::declareStruct(
    StructDefinition* structM) {
  globalState->regionIdByKind.emplace(structM->kind, getRegionId());

  kindStructs.declareStruct(structM->kind);
}

void Unsafe::defineStruct(
    StructDefinition* structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        globalState->getRegion(structM->members[i]->type)
            ->translateType(structM->members[i]->type));
  }
  kindStructs.defineStruct(structM->kind, innerStructMemberTypesL);
}

void Unsafe::declareEdge(
    Edge* edge) {
  kindStructs.declareEdge(edge);
}

void Unsafe::defineEdge(
    Edge* edge) {
  auto interfaceFunctionsLT = globalState->getInterfaceFunctionTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  kindStructs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);
}

void Unsafe::declareInterface(
    InterfaceDefinition* interfaceM) {
  globalState->regionIdByKind.emplace(interfaceM->kind, getRegionId());

  kindStructs.declareInterface(interfaceM);
}

void Unsafe::defineInterface(
    InterfaceDefinition* interfaceM) {
  auto interfaceMethodTypesL = globalState->getInterfaceFunctionTypes(interfaceM->kind);
  kindStructs.defineInterface(interfaceM, interfaceMethodTypesL);
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
//    auto rcIsZeroLE = strongRcIsZero(globalState, &kindStructs, builder, refM, controlBlockPtrLE);
//    buildAssert(globalState, functionState, builder, rcIsZeroLE,
//        "Tried to free concrete that had nonzero RC!");
  } else {
    // It's a mutable, so mark WRCs dead

    if (auto structKindM = dynamic_cast<StructKind *>(refM->kind)) {
      auto structM = globalState->program->getStruct(structKindM);
      if (structM->weakability == Weakability::WEAKABLE) {
        wrcWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
      }
    } else if (auto interfaceKindM = dynamic_cast<InterfaceKind *>(refM->kind)) {
      auto interfaceM = globalState->program->getInterface(interfaceKindM);
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
          globalState, functionState, builder, &kindStructs, structRefMT, structRef,
          structKnownLive, memberIndex, memberName, newMemberLE);
      break;
    }
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
          globalState, functionState, builder, &kindStructs, virtualParamMT, virtualArgRef);
    }
    case Ownership::WEAK: {
      return explodeWeakInterfaceRef(
          globalState, functionState, builder, &kindStructs, &fatWeaks, &weakRefStructs,
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

Ref Unsafe::getRuntimeSizedArrayLength(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    Ref arrayRef,
    bool arrayKnownLive) {
  return getRuntimeSizedArrayLengthStrong(globalState, functionState, builder, &kindStructs, rsaRefMT, arrayRef);
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
    regularCheckValidReference(checkerAFL, globalState, functionState, builder, &kindStructs, refM, refLE);
  } else if (refM->ownership == Ownership::SHARE) {
    assert(false);
  } else {
    if (refM->ownership == Ownership::BORROW) {
      regularCheckValidReference(checkerAFL, globalState, functionState, builder,
          &kindStructs, refM, refLE);
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
      assert(dynamic_cast<StructKind*>(sourceType->kind) || dynamic_cast<InterfaceKind*>(sourceType->kind));
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
      kindStructs.getControlBlockPtr(checkerAFL, functionState, builder, ref, refM);
  return kindStructs.getObjIdFromControlBlockPtr(builder, refM->kind, controlBlockPtrLE);
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
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName) {

  LLVMValueRef newControlBlockLE = LLVMGetUndef(kindStructs.getControlBlock(kindM)->getStruct());

  newControlBlockLE =
      fillControlBlockCensusFields(
          from, globalState, functionState, &kindStructs, builder, kindM, newControlBlockLE, typeName);

  if (globalState->getKindWeakability(kindM) == Weakability::WEAKABLE) {
    newControlBlockLE = wrcWeaks.fillWeakableControlBlock(functionState, builder, &kindStructs, kindM,
        newControlBlockLE);
  }

  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE.refLE);
}

LoadResult Unsafe::loadElementFromSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto ssaDef = globalState->program->getStaticSizedArray(ssaMT);
  return regularloadElementFromSSA(
      globalState, functionState, builder, ssaRefMT, ssaMT, ssaDef->rawArray->elementType, ssaDef->size, ssaDef->rawArray->mutability, arrayRef, arrayKnownLive, indexRef, &kindStructs);
}

LoadResult Unsafe::loadElementFromRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  return regularLoadElementFromRSAWithoutUpgrade(
      globalState, functionState, builder, &kindStructs, rsaRefMT, rsaMT, rsaDef->rawArray->mutability, rsaDef->rawArray->elementType, arrayRef, arrayKnownLive, indexRef);
}

Ref Unsafe::storeElementInRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  auto arrayWrapperPtrLE =
      kindStructs.makeWrapperPtr(
          FL(), functionState, builder, rsaRefMT,
          globalState->getRegion(rsaRefMT)->checkValidReference(FL(), functionState, builder, rsaRefMT, arrayRef));
  auto sizeRef = ::getRuntimeSizedArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
  auto arrayElementsPtrLE = getRuntimeSizedArrayContentsPtr(builder, arrayWrapperPtrLE);
  buildFlare(FL(), globalState, functionState, builder);
  return ::swapElement(
      globalState, functionState, builder, rsaRefMT->location, rsaDef->rawArray->elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
}

Ref Unsafe::upcast(
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructMT,
    StructKind* sourceStructKindM,
    Ref sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceKind* targetInterfaceKindM) {

  switch (sourceStructMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN:
    case Ownership::BORROW: {
      return upcastStrong(globalState, functionState, builder, &kindStructs, sourceStructMT, sourceStructKindM, sourceRefLE, targetInterfaceTypeM, targetInterfaceKindM);
    }
    case Ownership::WEAK: {
      return ::upcastWeak(globalState, functionState, builder, &weakRefStructs, sourceStructMT, sourceStructKindM, sourceRefLE, targetInterfaceTypeM, targetInterfaceKindM);
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
  innerDeallocate(from, globalState, functionState, &kindStructs, builder, refMT, ref);
}

Ref Unsafe::constructRuntimeSizedArray(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaMT,
    RuntimeSizedArrayT* runtimeSizedArrayT,
    Ref sizeRef,
    const std::string& typeName) {
  auto rsaWrapperPtrLT =
      kindStructs.getRuntimeSizedArrayWrapperStruct(runtimeSizedArrayT);
  auto rsaDef = globalState->program->getRuntimeSizedArray(runtimeSizedArrayT);
  auto elementType = globalState->program->getRuntimeSizedArray(runtimeSizedArrayT)->rawArray->elementType;
  auto rsaElementLT = globalState->getRegion(elementType)->translateType(elementType);
  auto resultRef =
      ::constructRuntimeSizedArray(
           globalState, functionState, builder, &kindStructs, rsaMT, rsaDef->rawArray->elementType, runtimeSizedArrayT,
           rsaWrapperPtrLT, rsaElementLT, sizeRef, typeName,
          [this, functionState, runtimeSizedArrayT, rsaMT, typeName](
              LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(),
                functionState,
                innerBuilder,
                runtimeSizedArrayT,
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
            globalState, functionState, builder, &kindStructs, structRefMT, structRef,
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
  auto structKind = dynamic_cast<StructKind*>(refMT->kind);
  assert(structKind);
  assert(LLVMTypeOf(argLE) == kindStructs.getInnerStruct(structKind));
}


//std::string Unsafe::getMemberArbitraryRefNameCSeeMMEDT(Reference* refMT) {
//  if (refMT->ownership == Ownership::SHARE) {
//    assert(false);
//  } else if (auto structRefMT = dynamic_cast<StructKind*>(refMT->kind)) {
//    auto structMT = globalState->program->getStruct(structRefMT);
//    auto baseName = globalState->program->getMemberArbitraryExportNameSeeMMEDT(structRefMT->fullName);
//    if (structMT->mutability == Mutability::MUTABLE) {
//      assert(refMT->location != Location::INLINE);
//      return baseName + "Ref";
//    } else {
//      if (refMT->location == Location::INLINE) {
//        return baseName + "Inl";
//      } else {
//        return baseName + "Ref";
//      }
//    }
//  } else if (auto interfaceMT = dynamic_cast<InterfaceKind*>(refMT->kind)) {
//    return globalState->program->getMemberArbitraryExportNameSeeMMEDT(interfaceMT->fullName) + "Ref";
//  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(refMT->kind)) {
//    return globalState->program->getMemberArbitraryExportNameSeeMMEDT(rsaMT->name) + "Ref";
//  } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(refMT->kind)) {
//    return globalState->program->getMemberArbitraryExportNameSeeMMEDT(ssaMT->name) + "Ref";
//  } else {
//    assert(false);
//  }
//}

std::string Unsafe::generateStructDefsC(
    Package* currentPackage,
    StructDefinition* structDefM) {
  if (structDefM->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    auto name = currentPackage->getKindExportName(structDefM->kind);
    return std::string() + "typedef struct " + name + "Ref { void* unused; } " + name + "Ref;\n";
  }
}

std::string Unsafe::generateInterfaceDefsC(
    Package* currentPackage,
    InterfaceDefinition* interfaceDefM) {
//      return "void* unused; void* unused;";
  assert(false); // impl
  return "";
}

std::string Unsafe::generateRuntimeSizedArrayDefsC(
    Package* currentPackage,
    RuntimeSizedArrayDefinitionT* rsaDefM) {
  if (rsaDefM->rawArray->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    auto name = currentPackage->getKindExportName(rsaDefM->kind);
    return std::string() + "typedef struct " + name + "Ref { void* unused; } " + name + "Ref;\n";
  }
}

std::string Unsafe::generateStaticSizedArrayDefsC(
    Package* currentPackage,
    StaticSizedArrayDefinitionT* ssaDefM) {
  if (ssaDefM->rawArray->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    auto name = currentPackage->getKindExportName(ssaDefM->kind);
    return std::string() + "typedef struct " + name + "Ref { void* unused; } " + name + "Ref;\n";
  }
}

Reference* Unsafe::getExternalType(Reference* refMT) {
  return refMT;
//  if (refMT->ownership == Ownership::SHARE) {
//    assert(false);
//  } else {
//    if (auto structKind = dynamic_cast<StructKind*>(refMT->kind)) {
//      return LLVMPointerType(kindStructs.getWrapperStruct(structKind), 0);
//    } else if (auto interfaceKind = dynamic_cast<InterfaceKind*>(refMT->kind)) {
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
      return mutWeakableStructs.getWeakVoidRefStruct(reference->kind);
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

void Unsafe::initializeElementInRSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *rsaRefMT,
    RuntimeSizedArrayT *rsaMT,
    Ref rsaRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  auto arrayWrapperPtrLE =
      kindStructs.makeWrapperPtr(
          FL(), functionState, builder, rsaRefMT,
          globalState->getRegion(rsaRefMT)->checkValidReference(FL(), functionState, builder, rsaRefMT, rsaRef));
  auto sizeRef = ::getRuntimeSizedArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
  auto arrayElementsPtrLE = getRuntimeSizedArrayContentsPtr(builder, arrayWrapperPtrLE);
  ::initializeElement(
      globalState, functionState, builder, rsaRefMT->location, rsaDef->rawArray->elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
}

Ref Unsafe::deinitializeElementFromRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  return regularLoadElementFromRSAWithoutUpgrade(
      globalState, functionState, builder, &kindStructs, rsaRefMT, rsaMT, rsaDef->rawArray->mutability, rsaDef->rawArray->elementType, arrayRef, true, indexRef).move();
}

void Unsafe::initializeElementInSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto ssaDef = globalState->program->getStaticSizedArray(ssaMT);
  auto arrayWrapperPtrLE =
      kindStructs.makeWrapperPtr(
          FL(), functionState, builder, ssaRefMT,
          globalState->getRegion(ssaRefMT)->checkValidReference(FL(), functionState, builder, ssaRefMT, arrayRef));
  auto sizeRef = globalState->constI64(ssaDef->size);
  auto arrayElementsPtrLE = getStaticSizedArrayContentsPtr(builder, arrayWrapperPtrLE);
  ::initializeElement(
      globalState, functionState, builder, ssaRefMT->location, ssaDef->rawArray->elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
}

Ref Unsafe::deinitializeElementFromSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef) {
  assert(false);
  exit(1);
}

Weakability Unsafe::getKindWeakability(Kind* kind) {
  if (auto structKind = dynamic_cast<StructKind*>(kind)) {
    return globalState->lookupStruct(structKind)->weakability;
  } else if (auto interfaceKind = dynamic_cast<InterfaceKind*>(kind)) {
    return globalState->lookupInterface(interfaceKind)->weakability;
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

std::string Unsafe::getExportName(
    Package* package,
    Reference* reference) {
  return package->getKindExportName(reference->kind) + (reference->location == Location::YONDER ? "Ref" : "");
}
