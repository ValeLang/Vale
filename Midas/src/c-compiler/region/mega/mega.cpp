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
#include "mega.h"
#include <sstream>

LLVMTypeRef makeWeakRefHeaderStruct(GlobalState* globalState, RegionId* regionId) {
  if (regionId == globalState->metalCache->naiveRcRegionId) {
    return WrcWeaks::makeWeakRefHeaderStruct(globalState);
  } else if (regionId == globalState->metalCache->resilientV3RegionId ||
      regionId == globalState->metalCache->resilientV2RegionId) {
    return HybridGenerationalMemory::makeWeakRefHeaderStruct(globalState, regionId);
  } else if (regionId == globalState->metalCache->resilientV1RegionId) {
    return LgtWeaks::makeWeakRefHeaderStruct(globalState, regionId);
  } else {
    assert(false);
  }

//  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
//    return LgtWeaks::makeWeakRefHeaderStruct(globalState);
//  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2 ||
//      globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
//      globalState->opt->regionOverride == RegionOverride::RESILIENT_LIMIT) {
//    return HybridGenerationalMemory::makeWeakRefHeaderStruct(globalState);
//  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0 ||
//      globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
//    return WrcWeaks::makeWeakRefHeaderStruct(globalState);
//  } else {
//    assert(false);
//  }
}

ControlBlock makeMutNonWeakableControlBlock(GlobalState* globalState, RegionId* regionId) {
  if (regionId == globalState->metalCache->naiveRcRegionId) {
    return makeAssistAndNaiveRCNonWeakableControlBlock(globalState);
  } else if (regionId == globalState->metalCache->resilientV3RegionId ||
             regionId == globalState->metalCache->resilientV2RegionId) {
    return makeResilientV2WeakableControlBlock(globalState);
  } else if (regionId == globalState->metalCache->resilientV1RegionId) {
    return makeResilientV1WeakableControlBlock(globalState);
  } else {
    assert(false);
  }
//    case RegionOverride::RESILIENT_V0:
//      return makeResilientV0WeakableControlBlock(globalState);
//    case RegionOverride::RESILIENT_V1:
//      return makeResilientV1WeakableControlBlock(globalState);
//    case RegionOverride::RESILIENT_V2:
//    case RegionOverride::RESILIENT_V3:
//    case RegionOverride::RESILIENT_LIMIT:
//      return makeResilientV2WeakableControlBlock(globalState);
//    default:
//      assert(false);
//  }
}

ControlBlock makeMutWeakableControlBlock(GlobalState* globalState, RegionId* regionId) {
  if (regionId == globalState->metalCache->naiveRcRegionId) {
    return makeAssistAndNaiveRCWeakableControlBlock(globalState);
  } else if (regionId == globalState->metalCache->resilientV3RegionId ||
      regionId == globalState->metalCache->resilientV2RegionId) {
    return makeResilientV2WeakableControlBlock(globalState);
  } else if (regionId == globalState->metalCache->resilientV1RegionId) {
    return makeResilientV1WeakableControlBlock(globalState);
  } else {
    assert(false);
  }
//  switch (globalState->opt->regionOverride) {
//    case RegionOverride::NAIVE_RC:
//      return makeAssistAndNaiveRCWeakableControlBlock(globalState);
//    case RegionOverride::RESILIENT_V0:
//      return makeResilientV0WeakableControlBlock(globalState);
//    case RegionOverride::RESILIENT_V1:
//      return makeResilientV1WeakableControlBlock(globalState);
//    case RegionOverride::RESILIENT_V2:
//    case RegionOverride::RESILIENT_V3:
//    case RegionOverride::RESILIENT_LIMIT:
//      return makeResilientV2WeakableControlBlock(globalState);
//    default:
//      assert(false);
//  }
}

Mega::Mega(GlobalState* globalState_, RegionId* regionId_) :
    globalState(globalState_),
    regionId(regionId_),
    mutNonWeakableStructs(globalState, makeMutNonWeakableControlBlock(globalState, regionId)),
    mutWeakableStructs(
        globalState,
        makeMutWeakableControlBlock(globalState, regionId),
        makeWeakRefHeaderStruct(globalState, regionId)),
    referendStructs(
        globalState,
        [this](Referend* referend) -> IReferendStructsSource* {
          switch (globalState->opt->regionOverride) {
            case RegionOverride::NAIVE_RC:
              if (globalState->getReferendWeakability(referend) == Weakability::NON_WEAKABLE) {
                return &mutNonWeakableStructs;
              } else {
                return &mutWeakableStructs;
              }
            case RegionOverride::RESILIENT_V0:
            case RegionOverride::RESILIENT_V1:
            case RegionOverride::RESILIENT_V2:
            case RegionOverride::RESILIENT_V3:
            case RegionOverride::RESILIENT_LIMIT:
              return &mutWeakableStructs;
            default:
              assert(false);
          }
        }),
    weakRefStructs(
        [this](Referend* referend) -> IWeakRefStructsSource* {
          switch (globalState->opt->regionOverride) {
            case RegionOverride::NAIVE_RC:
              if (globalState->getReferendWeakability(referend) == Weakability::NON_WEAKABLE) {
                assert(false);
              } else {
                return &mutWeakableStructs;
              }
            case RegionOverride::RESILIENT_V0:
            case RegionOverride::RESILIENT_V1:
            case RegionOverride::RESILIENT_V2:
            case RegionOverride::RESILIENT_V3:
            case RegionOverride::RESILIENT_LIMIT:
              return &mutWeakableStructs;
            default:
              assert(false);
          }
        }),
    fatWeaks(globalState_, &weakRefStructs),
    wrcWeaks(globalState_, &referendStructs, &weakRefStructs),
    lgtWeaks(
        globalState_, &referendStructs, &weakRefStructs,
        globalState->opt->elideChecksForKnownLive),
    hgmWeaks(
        globalState_, &referendStructs, &weakRefStructs,
        globalState->opt->elideChecksForKnownLive,
        globalState->opt->regionOverride == RegionOverride::RESILIENT_LIMIT) {
}

RegionId* Mega::getRegionId() {
  return regionId;
}


Ref Mega::constructKnownSizeArray(
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

Ref Mega::mallocStr(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE,
    LLVMValueRef sourceCharsPtrLE) {
  assert(false);
  exit(1);
}

Ref Mega::allocate(
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

void Mega::alias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    Ref expr) {
  auto sourceRnd = sourceRef->referend;

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
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
          adjustStrongRc(from, globalState, functionState, &referendStructs, builder, expr, sourceRef, 1);
        } else if (sourceRef->ownership == Ownership::BORROW) {
          adjustStrongRc(from, globalState, functionState, &referendStructs, builder, expr, sourceRef, 1);
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
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
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
      break;
    }
    default: assert(false);
  }
}

void Mega::dealias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto sourceRnd = sourceMT->referend;

  if (sourceMT->ownership == Ownership::SHARE) {
    assert(false);
  } else {
    switch (globalState->opt->regionOverride) {
      case RegionOverride::NAIVE_RC: {
        if (sourceMT->ownership == Ownership::OWN) {
          // We can't discard owns, they must be destructured.
          assert(false); // impl
        } else if (sourceMT->ownership == Ownership::BORROW) {
          auto rcLE = adjustStrongRc(from, globalState, functionState, &referendStructs, builder, sourceRef, sourceMT, -1);
          buildIf(
              globalState, functionState, builder, isZeroLE(builder, rcLE),
              [this, functionState, sourceRef, sourceMT](LLVMBuilderRef thenBuilder) {
                deallocate(FL(), functionState, thenBuilder, sourceMT, sourceRef);
              });
        } else if (sourceMT->ownership == Ownership::WEAK) {
          discardWeakRef(from, functionState, builder, sourceMT, sourceRef);
        } else assert(false);
        break;
      }
      case RegionOverride::RESILIENT_V0:
      case RegionOverride::RESILIENT_V1:
      case RegionOverride::RESILIENT_V2:
      case RegionOverride::RESILIENT_V3:
      case RegionOverride::RESILIENT_LIMIT: {
        if (sourceMT->ownership == Ownership::OWN) {
          // This can happen if we're sending an owning reference to the outside world, see DEPAR.
        } else if (sourceMT->ownership == Ownership::BORROW) {
          discardWeakRef(from, functionState, builder, sourceMT, sourceRef);
        } else if (sourceMT->ownership == Ownership::WEAK) {
          discardWeakRef(from, functionState, builder, sourceMT, sourceRef);
        } else assert(false);
        break;
      }
      default:
        assert(false);
    }
  }
}

Ref Mega::weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) {
  assert(sourceRefMT->ownership == Ownership::BORROW);
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      return regularWeakAlias(globalState, functionState, &referendStructs, &wrcWeaks, builder, sourceRefMT, targetRefMT, sourceRef);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      return transmuteWeakRef(
          globalState, functionState, builder, sourceRefMT, targetRefMT, &weakRefStructs, sourceRef);
    default:
      assert(false);
  }
}

// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
WrapperPtrLE Mega::lockWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref weakRefLE,
    bool weakRefKnownLive) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
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
      break;
    }
    case RegionOverride::RESILIENT_V0: {
      switch (refM->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE:
          assert(false);
          break;
        case Ownership::BORROW:
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
    case RegionOverride::RESILIENT_V1: {
      switch (refM->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE: {
          assert(false);
          break;
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
              lgtWeaks.lockLgtiFatPtr(from, functionState, builder, refM, weakFatPtrLE, weakRefKnownLive));
        }
        default:
          assert(false);
          break;
      }
    }
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
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
    default:
      assert(false);
      break;
  }
  assert(false);
}

Ref Mega::lockWeak(
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

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      assert(sourceWeakRefMT->ownership == Ownership::WEAK);
      auto isAliveLE =
          getIsAliveFromWeakRef(
              functionState, builder, sourceWeakRefMT, sourceWeakRefLE, weakRefKnownLive);
      auto resultOptTypeLE = globalState->getRegion(resultOptTypeM)->translateType(resultOptTypeM);
      return regularInnerLockWeak(
          globalState, functionState, builder, thenResultIsNever, elseResultIsNever, resultOptTypeM,
          constraintRefM, sourceWeakRefMT, sourceWeakRefLE, buildThen, buildElse,
          isAliveLE, resultOptTypeLE, &weakRefStructs, &fatWeaks);
      break;
    }
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      assert(sourceWeakRefMT->ownership == Ownership::BORROW ||
          sourceWeakRefMT->ownership == Ownership::WEAK);
      auto isAliveLE =
          getIsAliveFromWeakRef(
              functionState, builder, sourceWeakRefMT, sourceWeakRefLE, weakRefKnownLive);
      auto resultOptTypeLE = globalState->getRegion(resultOptTypeM)->translateType(resultOptTypeM);
      return resilientThing(globalState, functionState, builder, thenResultIsNever, elseResultIsNever, resultOptTypeM, constraintRefM, sourceWeakRefMT, sourceWeakRefLE, weakRefKnownLive, buildThen, buildElse, isAliveLE, resultOptTypeLE, &weakRefStructs);
    }
    default:
      assert(false);
      break;
  }
}

LLVMTypeRef Mega::translateType(Reference* referenceM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
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
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
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
    default:
      assert(false);
      return nullptr;
  }
}

Ref Mega::upcastWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      auto resultWeakInterfaceFatPtr =
          hgmWeaks.weakStructPtrToGenWeakInterfacePtr(
              globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
              sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
      return wrap(this, targetInterfaceTypeM, resultWeakInterfaceFatPtr);
    }
    case RegionOverride::RESILIENT_V1: {
      auto resultWeakInterfaceFatPtr =
          lgtWeaks.weakStructPtrToLgtiWeakInterfacePtr(
              functionState, builder, sourceRefLE, sourceStructReferendM,
              sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
      return wrap(this, targetInterfaceTypeM, resultWeakInterfaceFatPtr);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::NAIVE_RC: {
      auto resultWeakInterfaceFatPtr =
          wrcWeaks.weakStructPtrToWrciWeakInterfacePtr(
              globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
              sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
      return wrap(this, targetInterfaceTypeM, resultWeakInterfaceFatPtr);
    }
    default:
      assert(false);
      break;
  }
}

void Mega::declareKnownSizeArray(
    KnownSizeArrayDefinitionT* knownSizeArrayMT) {
  globalState->regionIdByReferend.emplace(knownSizeArrayMT->referend, getRegionId());

  referendStructs.declareKnownSizeArray(knownSizeArrayMT);
}

void Mega::declareUnknownSizeArray(
    UnknownSizeArrayDefinitionT* unknownSizeArrayMT) {
  globalState->regionIdByReferend.emplace(unknownSizeArrayMT->referend, getRegionId());

  referendStructs.declareUnknownSizeArray(unknownSizeArrayMT);
}

void Mega::defineUnknownSizeArray(
    UnknownSizeArrayDefinitionT* unknownSizeArrayMT) {
  auto elementLT =
      globalState->getRegion(unknownSizeArrayMT->rawArray->elementType)
          ->translateType(unknownSizeArrayMT->rawArray->elementType);
  referendStructs.defineUnknownSizeArray(unknownSizeArrayMT, elementLT);
}

void Mega::defineKnownSizeArray(
    KnownSizeArrayDefinitionT* knownSizeArrayMT) {
  auto elementLT =
      globalState->getRegion(knownSizeArrayMT->rawArray->elementType)
          ->translateType(knownSizeArrayMT->rawArray->elementType);
  referendStructs.defineKnownSizeArray(knownSizeArrayMT, elementLT);
}
void Mega::declareStruct(
    StructDefinition* structM) {
  globalState->regionIdByReferend.emplace(structM->referend, getRegionId());

  referendStructs.declareStruct(structM);
}

void Mega::defineStruct(
    StructDefinition* structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        globalState->getRegion(structM->members[i]->type)
            ->translateType(structM->members[i]->type));
  }
  referendStructs.defineStruct(
      structM,
      innerStructMemberTypesL);
}

void Mega::declareEdge(
    Edge* edge) {
  referendStructs.declareEdge(edge);
}

void Mega::defineEdge(
    Edge* edge) {
  auto interfaceFunctionsLT = globalState->getInterfaceFunctionTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  referendStructs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);
}

void Mega::declareInterface(
    InterfaceDefinition* interfaceM) {
  globalState->regionIdByReferend.emplace(interfaceM->referend, getRegionId());

  referendStructs.declareInterface(interfaceM);
}

void Mega::defineInterface(
    InterfaceDefinition* interfaceM) {
  auto interfaceMethodTypesL = globalState->getInterfaceFunctionTypes(interfaceM->referend);
  referendStructs.defineInterface(interfaceM, interfaceMethodTypesL);
}

void Mega::discardOwningRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      auto rcLE =
          adjustStrongRc(
              AFL("Destroy decrementing the owning ref"),
              globalState, functionState, &referendStructs, builder, sourceRef, sourceMT, -1);
      buildIf(
          globalState, functionState, builder, isZeroLE(builder, rcLE),
          [this, functionState, blockState, sourceRef, sourceMT](LLVMBuilderRef thenBuilder) {
            deallocate(FL(), functionState, thenBuilder, sourceMT, sourceRef);
          });
      break;
    }
    case RegionOverride::RESILIENT_V0: {
      // Mutables in resilient mode dont have strong RC, and also, they dont adjust
      // weak RC for owning refs

      // Free it!
      deallocate(AFL("discardOwningRef"), functionState, builder, sourceMT, sourceRef);
      break;
    }
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      // Mutables in resilient v1+2 dont have strong RC, and also, they dont adjust
      // weak RC for owning refs

      // Free it!
      deallocate(AFL("discardOwningRef"), functionState, builder,
          sourceMT, sourceRef);
      break;
    }
    default: {
      assert(false);
      break;
    }
  }
}

void Mega::noteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtrLE) {
  if (globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
    // Dont need to assert that the strong RC is zero, thats the only way we'd get here.

    if (auto structReferendM = dynamic_cast<StructReferend*>(refM->referend)) {
      auto structM = globalState->program->getStruct(structReferendM->fullName);
      if (structM->weakability == Weakability::WEAKABLE) {
        wrcWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
      }
    } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(refM->referend)) {
      auto interfaceM = globalState->program->getInterface(interfaceReferendM->fullName);
      if (interfaceM->weakability == Weakability::WEAKABLE) {
        wrcWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
      }
    } else {
      // Do nothing, only structs and interfaces are weakable in naive-rc mode.
    }
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
    if (refM->ownership == Ownership::SHARE) {
      auto rcIsZeroLE = strongRcIsZero(globalState, &referendStructs, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
    } else {
      assert(refM->ownership == Ownership::OWN);

      // In resilient mode, every mutable is weakable.
      wrcWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
    }
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    if (refM->ownership == Ownership::SHARE) {
      auto rcIsZeroLE = strongRcIsZero(globalState, &referendStructs, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
    } else {
      assert(refM->ownership == Ownership::OWN);

      // In resilient mode, every mutable is weakable.
      lgtWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
    }
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_LIMIT) {
    if (refM->ownership == Ownership::SHARE) {
      auto rcIsZeroLE = strongRcIsZero(globalState, &referendStructs, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
    } else {
      assert(refM->ownership == Ownership::OWN);

      // In resilient mode, every mutable is weakable.
      hgmWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
    }
  } else assert(false);
}

void Mega::storeMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    const std::string& memberName,
    Reference* newMemberRefMT,
    Ref newMemberRef) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      switch (structRefMT->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE:
        case Ownership::BORROW: {
          auto newMemberLE =
              globalState->getRegion(newMemberRefMT)->checkValidReference(
                  FL(), functionState, builder, newMemberRefMT, newMemberRef);
          storeMemberStrong(
              globalState, functionState, builder, &referendStructs, structRefMT, structRef,
              structKnownLive, memberIndex, memberName, newMemberLE);
          break;
        }
        case Ownership::WEAK: {
          auto newMemberLE =
              globalState->getRegion(newMemberRefMT)->checkValidReference(
                  FL(), functionState, builder, newMemberRefMT, newMemberRef);
          storeMemberWeak(
              globalState, functionState, builder, &referendStructs, structRefMT, structRef,
              structKnownLive, memberIndex, memberName, newMemberLE);
          break;
        }
        default:
          assert(false);
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
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
      break;
    }
    default:
      assert(false);
  }
}

// Gets the itable PTR and the new value that we should put into the virtual param's slot
// (such as a void* or a weak void ref)
std::tuple<LLVMValueRef, LLVMValueRef> Mega::explodeInterfaceRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
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
    case RegionOverride::RESILIENT_V0: {
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
                return wrcWeaks.weakInterfaceRefToWeakStructRef(
                    functionState, builder, virtualParamMT, weakFatPtrLE);
              });
        }
        default:
          assert(false);
      }
    }
    case RegionOverride::RESILIENT_V1: {
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
                return lgtWeaks.weakInterfaceRefToWeakStructRef(
                    functionState, builder, virtualParamMT, weakFatPtrLE);
              });
        }
        default:
          assert(false);
      }
    }
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
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
                return lgtWeaks.weakInterfaceRefToWeakStructRef(
                    functionState, builder, virtualParamMT, weakFatPtrLE);
              });
        }
        default:
          assert(false);
      }
    }
    default:
      assert(false);
  }
}

Ref Mega::getUnknownSizeArrayLength(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    Ref arrayRef,
    bool arrayKnownLive) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      return getUnknownSizeArrayLengthStrong(globalState, functionState, builder, &referendStructs, usaRefMT, arrayRef);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
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
      break;
    }
    default:
      assert(false);
  }
  assert(false);
}

LLVMValueRef Mega::checkValidReference(
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

  if (globalState->opt->census) {
    switch (globalState->opt->regionOverride) {
      case RegionOverride::NAIVE_RC: {
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
      case RegionOverride::RESILIENT_V0: {
        if (refM->ownership == Ownership::OWN) {
          regularCheckValidReference(checkerAFL, globalState, functionState, builder, &referendStructs, refM, refLE);
        } else if (refM->ownership == Ownership::SHARE) {
          assert(false);
        } else {
          wrcWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
        }
        return refLE;
      }
      case RegionOverride::RESILIENT_V1: {
        if (refM->ownership == Ownership::OWN) {
          regularCheckValidReference(checkerAFL, globalState, functionState, builder, &referendStructs, refM, refLE);
        } else if (refM->ownership == Ownership::SHARE) {
          assert(false);
        } else {
          lgtWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
        }
        return refLE;
      }
      case RegionOverride::RESILIENT_V2:
      case RegionOverride::RESILIENT_V3: {
        if (refM->ownership == Ownership::OWN) {
          regularCheckValidReference(checkerAFL, globalState, functionState, builder, &referendStructs, refM, refLE);
        } else if (refM->ownership == Ownership::SHARE) {
          assert(false);
        } else {
          hgmWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
        }
        return refLE;
      }
      default:
        assert(false);
    }
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
Ref Mega::upgradeLoadResultToRefWithTargetOwnership(
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

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
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
      break;
    }
    case RegionOverride::RESILIENT_V0: {
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
          return wrcWeaks.assembleWeakRef(functionState, builder, sourceType, targetType, sourceRef);
        } else {
          assert(false);
        }
      } else if (sourceOwnership == Ownership::BORROW || sourceOwnership == Ownership::WEAK) {
        assert(targetOwnership == Ownership::BORROW || targetOwnership == Ownership::WEAK);

        return transmutePtr(globalState, functionState, builder, sourceType, targetType, sourceRef);
      } else {
        assert(false);
      }
      break;
    }
    case RegionOverride::RESILIENT_V1: {
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
          return lgtWeaks.assembleWeakRef(functionState, builder, sourceType, targetType, sourceRef);
        } else {
          assert(false);
        }
      } else if (sourceOwnership == Ownership::BORROW || sourceOwnership == Ownership::WEAK) {
        assert(targetOwnership == Ownership::BORROW || targetOwnership == Ownership::WEAK);

        return transmutePtr(globalState, functionState, builder, sourceType, targetType, sourceRef);
      } else {
        assert(false);
      }
      break;
    }
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
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
      break;
    }
    default:
      assert(false);
  }
}

void Mega::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::RESILIENT_V0:
      return wrcWeaks.aliasWeakRef(from, functionState, builder, weakRefMT, weakRef);
    case RegionOverride::RESILIENT_V1:
      return lgtWeaks.aliasWeakRef(from, functionState, builder, weakRefMT, weakRef);
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      return hgmWeaks.aliasWeakRef(from, functionState, builder, weakRefMT, weakRef);
    default:
      assert(false);
  }
}

void Mega::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::RESILIENT_V0:
      return wrcWeaks.discardWeakRef(from, functionState, builder, weakRefMT, weakRef);
    case RegionOverride::RESILIENT_V1:
      return lgtWeaks.discardWeakRef(from, functionState, builder, weakRefMT, weakRef);
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      return hgmWeaks.discardWeakRef(from, functionState, builder, weakRefMT, weakRef);
    default:
      assert(false);
  }
}

LLVMValueRef Mega::getCensusObjectId(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref ref) {
  auto controlBlockPtrLE =
      referendStructs.getControlBlockPtr(checkerAFL, functionState, builder, ref, refM);
  return referendStructs.getObjIdFromControlBlockPtr(builder, refM->referend, controlBlockPtrLE);
}

Ref Mega::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef,
    bool knownLive) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::RESILIENT_V0:
      return wrcWeaks.getIsAliveFromWeakRef(functionState, builder, weakRefM, weakRef);
    case RegionOverride::RESILIENT_V1:
      return lgtWeaks.getIsAliveFromWeakRef(functionState, builder, weakRefM, weakRef, knownLive);
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      return hgmWeaks.getIsAliveFromWeakRef(functionState, builder, weakRefM, weakRef, knownLive);
    default:
      assert(false);
  }
}

// Returns object ID
void Mega::fillControlBlock(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      regularFillControlBlock(
          from, globalState, functionState, &referendStructs, builder, referendM, controlBlockPtrLE,
          typeName, &wrcWeaks);
      break;
    }
    case RegionOverride::RESILIENT_V0: {
      LLVMValueRef newControlBlockLE = LLVMGetUndef(referendStructs.getControlBlock(referendM)->getStruct());

      newControlBlockLE =
          fillControlBlockCensusFields(
              from, globalState, functionState, &referendStructs, builder, referendM, newControlBlockLE, typeName);

      newControlBlockLE = wrcWeaks.fillWeakableControlBlock(functionState, builder, &referendStructs, referendM,
          newControlBlockLE);
      LLVMBuildStore(
          builder,
          newControlBlockLE,
          controlBlockPtrLE.refLE);
      break;
    }
    case RegionOverride::RESILIENT_V1: {
      LLVMValueRef newControlBlockLE = LLVMGetUndef(referendStructs.getControlBlock(referendM)->getStruct());

      newControlBlockLE =
          fillControlBlockCensusFields(
              from, globalState, functionState, &referendStructs, builder, referendM, newControlBlockLE, typeName);
      newControlBlockLE = lgtWeaks.fillWeakableControlBlock(functionState, builder, &referendStructs, referendM,
          newControlBlockLE);

      LLVMBuildStore(
          builder,
          newControlBlockLE,
          controlBlockPtrLE.refLE);
      break;
    }
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      gmFillControlBlock(
          from, globalState, functionState, &referendStructs, builder, referendM, controlBlockPtrLE,
          typeName, &hgmWeaks);
      break;
    }
    default:
      assert(false);
  }
}

LoadResult Mega::loadElementFromKSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      auto ksaDef = globalState->program->getKnownSizeArray(ksaMT->name);
      return regularloadElementFromKSA(
          globalState, functionState, builder, ksaRefMT, ksaMT, ksaDef->rawArray->elementType, ksaDef->size, ksaDef->rawArray->mutability, arrayRef, arrayKnownLive, indexRef, &referendStructs);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      auto ksaDef = globalState->program->getKnownSizeArray(ksaMT->name);
      return resilientloadElementFromKSA(
          globalState, functionState, builder, ksaRefMT, ksaMT, ksaDef->size, ksaDef->rawArray->mutability, ksaDef->rawArray->elementType, arrayRef, arrayKnownLive, indexRef, &referendStructs);
    }
    default:
      assert(false);
  }
}

LoadResult Mega::loadElementFromUSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
      return regularLoadElementFromUSAWithoutUpgrade(
          globalState, functionState, builder, &referendStructs, usaRefMT, usaMT, usaDef->rawArray->mutability, usaDef->rawArray->elementType, arrayRef, arrayKnownLive, indexRef);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
      return resilientLoadElementFromUSAWithoutUpgrade(
          globalState, functionState, builder, &referendStructs, usaRefMT, usaDef->rawArray->mutability, usaDef->rawArray->elementType, usaMT, arrayRef, arrayKnownLive, indexRef);
    }
    default:
      assert(false);
  }
}

Ref Mega::storeElementInUSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    Ref elementRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
      auto arrayWrapperPtrLE =
          referendStructs.makeWrapperPtr(
              FL(), functionState, builder, usaRefMT,
              globalState->getRegion(usaRefMT)->checkValidReference(FL(), functionState, builder, usaRefMT, arrayRef));
      auto sizeRef = ::getUnknownSizeArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
      auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);
      return ::swapElement(
          globalState, functionState, builder, usaRefMT->location, usaDef->rawArray->elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
      auto arrayWrapperPtrLE = lockWeakRef(FL(), functionState, builder, usaRefMT, arrayRef, arrayKnownLive);
      auto sizeRef = ::getUnknownSizeArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
      auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);
      return ::swapElement(
          globalState, functionState, builder, usaRefMT->location, usaDef->rawArray->elementType, sizeRef, arrayElementsPtrLE,
          indexRef, elementRef);
    }
    default:
      assert(false);
  }
  assert(false);
}

Ref Mega::upcast(
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructMT,
    StructReferend* sourceStructReferendM,
    Ref sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
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
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      switch (sourceStructMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN: {
          return upcastStrong(globalState, functionState, builder, &referendStructs, sourceStructMT, sourceStructReferendM, sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          return ::upcastWeak(globalState, functionState, builder, &weakRefStructs, sourceStructMT, sourceStructReferendM, sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
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


void Mega::deallocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  innerDeallocate(from, globalState, functionState, &referendStructs, builder, refMT, ref);
}

Ref Mega::constructUnknownSizeArray(
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

Ref Mega::loadMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
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
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
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
    default:
      assert(false);
  }
}

void Mega::checkInlineStructType(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  auto argLE = checkValidReference(FL(), functionState, builder, refMT, ref);
  auto structReferend = dynamic_cast<StructReferend*>(refMT->referend);
  assert(structReferend);
  assert(LLVMTypeOf(argLE) == referendStructs.getInnerStruct(structReferend));
}


std::string Mega::getMemberArbitraryRefNameCSeeMMEDT(Reference* refMT) {
  if (refMT->ownership == Ownership::SHARE) {
    assert(false);
  } else if (auto structRefMT = dynamic_cast<StructReferend*>(refMT->referend)) {
    auto structMT = globalState->program->getStruct(structRefMT->fullName);
    auto baseName = globalState->program->getExportedName(structRefMT->fullName);
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
    return globalState->program->getExportedName(interfaceMT->fullName) + "Ref";
  } else {
    assert(false);
  }
}

void Mega::generateUnknownSizeArrayDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName,
    UnknownSizeArrayDefinitionT* usaDefM) {
}

void Mega::generateKnownSizeArrayDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName,
    KnownSizeArrayDefinitionT* usaDefM) {
}

void Mega::generateStructDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName, StructDefinition* structDefM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
      if (structDefM->mutability == Mutability::IMMUTABLE) {
        assert(false);
      } else {
        auto baseName = globalState->program->getExportedName(structDefM->referend->fullName);
        auto refTypeName = baseName + "Ref";
        std::stringstream s;
        s << "typedef struct " << refTypeName << " { void* unused; } " << refTypeName << ";" << std::endl;
        cByExportedName->insert(std::make_pair(baseName, s.str()));
      }
      break;
    case RegionOverride::RESILIENT_V0:
      if (structDefM->mutability == Mutability::IMMUTABLE) {
        assert(false);
      } else {
        std::cerr << "Resilient v0 can only send immutables across extern boundary" << std::endl;
        assert(false);
      }
      break;
    case RegionOverride::RESILIENT_V1:
      if (structDefM->mutability == Mutability::IMMUTABLE) {
        assert(false);
      } else {
        auto baseName = globalState->program->getExportedName(structDefM->referend->fullName);
        auto refTypeName = baseName + "Ref";
        std::stringstream s;
        s << "typedef struct " << refTypeName << " { uint64_t unused0; void* unused1; } " << refTypeName << ";" << std::endl;
        cByExportedName->insert(std::make_pair(baseName, s.str()));
      }
      break;
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      if (structDefM->mutability == Mutability::IMMUTABLE) {
        assert(false);
      } else {
        auto baseName = globalState->program->getExportedName(structDefM->referend->fullName);
        auto refTypeName = baseName + "Ref";
        std::stringstream s;
        s << "typedef struct " << refTypeName << " { uint64_t unused0; void* unused1; } " << refTypeName << ";" << std::endl;
        cByExportedName->insert(std::make_pair(baseName, s.str()));
      }
      break;
    default:
      assert(false);
  }
}

void Mega::generateInterfaceDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName, InterfaceDefinition* interfaceDefM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
//      return "void* unused; void* unused;";
      assert(false); // impl
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
      assert(false);
      break;
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      if (interfaceDefM->mutability == Mutability::IMMUTABLE) {
        assert(false);
      } else {
        auto name = globalState->program->getExportedName(interfaceDefM->referend->fullName);
        std::stringstream s;
        s << "typedef struct " << name << "Ref { uint64_t unused0; void* unused1; void* unused2; } " << name << "Ref;";
        cByExportedName->insert(std::make_pair(name, s.str()));
      }
      break;
    default:
      assert(false);
  }
}

Reference* Mega::getExternalType(Reference* refMT) {
  return refMT;
//  switch (globalState->opt->regionOverride) {
//    case RegionOverride::NAIVE_RC:
//      if (refMT->ownership == Ownership::SHARE) {
//        assert(false);
//      } else {
//        if (auto structReferend = dynamic_cast<StructReferend*>(refMT->referend)) {
//          return LLVMPointerType(referendStructs.getWrapperStruct(structReferend), 0);
//        } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(refMT->referend)) {
//          assert(false); // impl
//        } else {
//          std::cerr << "Invalid type for extern!" << std::endl;
//          assert(false);
//        }
//      }
//      assert(false);
//      break;
//    case RegionOverride::RESILIENT_V0:
//    case RegionOverride::RESILIENT_V1:
//      if (refMT->ownership == Ownership::SHARE) {
//        assert(false);
//      } else {
//        if (auto structReferend = dynamic_cast<StructReferend*>(refMT->referend)) {
//          return weakRefStructs.getStructWeakRefStruct(structReferend);
//        } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(refMT->referend)) {
//          assert(false); // impl
//        } else {
//          std::cerr << "Invalid type for extern!" << std::endl;
//          assert(false);
//        }
//      }
//      assert(false);
//      break;
//    case RegionOverride::RESILIENT_V2:
//    case RegionOverride::RESILIENT_V3:
//    case RegionOverride::RESILIENT_LIMIT:
//      if (refMT->ownership == Ownership::SHARE) {
//        assert(false);
//      } else {
//        if (auto structReferend = dynamic_cast<StructReferend*>(refMT->referend)) {
//          return weakRefStructs.getStructWeakRefStruct(structReferend);
//        } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(refMT->referend)) {
//          assert(false); // impl
//        } else {
//          std::cerr << "Invalid type for extern!" << std::endl;
//          assert(false);
//        }
//      }
//      assert(false);
//      break;
//    default:
//      assert(false);
//      break;
//  }
//  assert(false);
}

Ref Mega::receiveAndDecryptFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Ref sourceRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
      // Alias when receiving from the outside world, see DEPAR.
      globalState->getRegion(sourceRefMT)
          ->alias(FL(), functionState, builder, sourceRefMT, sourceRef);

      return sourceRef;
      break;
    case RegionOverride::RESILIENT_V0:
      if (sourceRefMT->ownership == Ownership::SHARE) {
        assert(false);
      } else {
        assert(false);
      }
      assert(false);
      break;
    case RegionOverride::RESILIENT_V1:
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
      break;
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
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
      break;
    default:
      assert(false);
      break;
  }

  assert(false);
}
//
//LLVMValueRef Mega::sendRefToWild(
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* sourceRefMT,
//    Ref sourceRef) {
//  assert(sourceRefMT->ownership != Ownership::SHARE);
//
//  switch (globalState->opt->regionOverride) {
//    case RegionOverride::NAIVE_RC:
//      std::cerr << "Naive-rc cant send mutables across the boundary." << std::endl;
//      assert(false);
//      break;
//    case RegionOverride::RESILIENT_V0:
//      std::cerr << "Resilient V0 cant send mutables across the boundary" << std::endl;
//      assert(false);
//      break;
//    case RegionOverride::RESILIENT_V1:
//      if (globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
//        std::cerr
//            << "Naive-rc can't call externs with mutables safely yet. (Naive-rc is an experimental region only for use in perf comparisons)"
//            << std::endl;
//        exit(1);
//      }
//
//      if (auto structReferend = dynamic_cast<StructReferend*>(sourceRefMT->referend)) {
//        assert(sourceRefMT->location != Location::INLINE);
//
//        switch (sourceRefMT->ownership) {
//          case Ownership::OWN: {
//            // The outside world gets generational references, even to owning things.
//            // So, we need to make an owning generational ref.
//            auto borrowRefMT =
//                globalState->metalCache->getReference(
//                    Ownership::BORROW, Location::YONDER, structReferend);
//
//            auto wrapperPtrLE =
//                referendStructs.makeWrapperPtr(
//                    FL(), functionState, builder, sourceRefMT,
//                    checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef));
//            auto weakRefLE =
//                lgtWeaks.assembleStructWeakRef(
//                    functionState, builder, sourceRefMT, borrowRefMT, structReferend, wrapperPtrLE);
//            // All this rigamarole is to get the LLVMValueRef of the above weakRefLE.
//            return checkValidReference(
//                FL(), functionState, builder, borrowRefMT,
//                wrap(globalState->getRegion(borrowRefMT), borrowRefMT, weakRefLE));
//            break;
//          }
//          case Ownership::BORROW:
//          case Ownership::WEAK:
//            return checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef);
//          default:
//            assert(false);
//        }
//      } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(sourceRefMT->referend)) {
//        assert(false); // impl
//      } else {
//        std::cerr << "Invalid type for extern!" << std::endl;
//        assert(false);
//      }
//      assert(false);
//      break;
//    case RegionOverride::RESILIENT_V2:
//    case RegionOverride::RESILIENT_V3:
//    case RegionOverride::RESILIENT_LIMIT:
//      if (globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
//        std::cerr
//            << "Naive-rc can't call externs with mutables safely yet. (Naive-rc is an experimental region only for use in perf comparisons)"
//            << std::endl;
//        exit(1);
//      }
//
//      if (auto structReferend = dynamic_cast<StructReferend*>(sourceRefMT->referend)) {
//        assert(sourceRefMT->location != Location::INLINE);
//
//        switch (sourceRefMT->ownership) {
//          case Ownership::OWN: {
//            // The outside world gets generational references, even to owning things.
//            // So, we need to make an owning generational ref.
//            auto borrowRefMT =
//                globalState->metalCache->getReference(
//                    Ownership::BORROW, Location::YONDER, structReferend);
//
//            auto wrapperPtrLE =
//                referendStructs.makeWrapperPtr(
//                    FL(), functionState, builder, sourceRefMT,
//                    checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef));
//            auto weakRefLE =
//                hgmWeaks.assembleStructWeakRef(
//                    functionState, builder, sourceRefMT, borrowRefMT, structReferend, wrapperPtrLE);
//            // All this rigamarole is to get the LLVMValueRef of the above weakRefLE.
//            return checkValidReference(
//                FL(), functionState, builder, borrowRefMT,
//                wrap(globalState->getRegion(borrowRefMT), borrowRefMT, weakRefLE));
//            break;
//          }
//          case Ownership::BORROW:
//          case Ownership::WEAK:
//            return checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef);
//          default:
//            assert(false);
//        }
//      } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(sourceRefMT->referend)) {
//        assert(false); // impl
//      } else {
//        std::cerr << "Invalid type for extern!" << std::endl;
//        assert(false);
//      }
//      assert(false);
//      break;
//    default:
//      assert(false);
//      break;
//  }
//
//  assert(false);
//}
//
//Ref Mega::receiveRefFromWild(
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* sourceRefMT,
//    LLVMValueRef sourceRef) {
//  assert(sourceRefMT->ownership != Ownership::SHARE);
//
//  switch (globalState->opt->regionOverride) {
//    case RegionOverride::NAIVE_RC: {
//      assert(sourceRefMT->location != Location::INLINE);
//      auto result = wrap(globalState->getRegion(sourceRefMT), sourceRefMT, sourceRef);
//
//      // Alias any object coming from the outside world, see DEPAR.
//      alias(FL(), functionState, builder, sourceRefMT, result);
//
//      return result;
//    }
//    case RegionOverride::RESILIENT_V0:
//      // Is this really true? Doesnt really matter since V0 is only for benchmarking.
//      std::cerr << "Can't have mutables in the outside world in resilient-v0." << std::endl;
//      assert(false);
//      break;
//    case RegionOverride::RESILIENT_V1:
//
//      switch (sourceRefMT->ownership) {
//        case Ownership::SHARE:
//          assert(false);
//        case Ownership::OWN:
//
//          if (auto structReferend = dynamic_cast<StructReferend*>(sourceRefMT->referend)) {
//            assert(sourceRefMT->location != Location::INLINE);
//            // When in the outside world, they're like weak references. We need to turn them back into
//            // owning references.
//            auto borrowRefMT =
//                globalState->metalCache->getReference(
//                    Ownership::BORROW, Location::YONDER, structReferend);
//            auto weakRefLE = weakRefStructs.makeWeakFatPtr(borrowRefMT, sourceRef);
//
//            auto owningPtrLE = lgtWeaks.lockLgtiFatPtr(FL(), functionState, builder, borrowRefMT, weakRefLE, false);
//            auto result = wrap(globalState->getRegion(sourceRefMT), sourceRefMT, owningPtrLE);
//
//            // Alias any object coming from the outside world, see DEPAR.
//            alias(FL(), functionState, builder, sourceRefMT, result);
//
//            return result;
//          } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(sourceRefMT->referend)) {
//            assert(false); // impl
//          } else {
//            std::cerr << "Invalid type for extern!" << std::endl;
//            assert(false);
//          }
//        case Ownership::BORROW:
//        case Ownership::WEAK:
//          auto result = wrap(globalState->getRegion(sourceRefMT), sourceRefMT, sourceRef);
//
//          // Alias any object coming from the outside world, see DEPAR.
//          alias(FL(), functionState, builder, sourceRefMT, result);
//
//          return result;
//      }
//      assert(false);
//      break;
//    case RegionOverride::RESILIENT_V2:
//    case RegionOverride::RESILIENT_V3:
//    case RegionOverride::RESILIENT_LIMIT:
//
//      switch (sourceRefMT->ownership) {
//        case Ownership::SHARE:
//          assert(false);
//        case Ownership::OWN:
//          if (auto structReferend = dynamic_cast<StructReferend*>(sourceRefMT->referend)) {
//            assert(sourceRefMT->location != Location::INLINE);
//            // When in the outside world, they're like weak references. We need to turn them back into
//            // owning references.
//            auto borrowRefMT =
//                globalState->metalCache->getReference(
//                    Ownership::BORROW, Location::YONDER, structReferend);
//            auto weakRefLE = weakRefStructs.makeWeakFatPtr(borrowRefMT, sourceRef);
//
//            auto owningPtrLE = hgmWeaks.lockGenFatPtr(FL(), functionState, builder, borrowRefMT, weakRefLE, false);
//            return wrap(globalState->getRegion(sourceRefMT), sourceRefMT, owningPtrLE);
//          } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(sourceRefMT->referend)) {
//            assert(false); // impl
//          } else {
//            std::cerr << "Invalid type for extern!" << std::endl;
//            assert(false);
//          }
//        case Ownership::BORROW:
//        case Ownership::WEAK:
//          return wrap(globalState->getRegion(sourceRefMT), sourceRefMT, sourceRef);
//      }
//      assert(false);
//      break;
//    default:
//      assert(false);
//      break;
//  }
//
//  assert(false);
//}

LLVMTypeRef Mega::getInterfaceMethodVirtualParamAnyType(Reference* reference) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      switch (reference->ownership) {
        case Ownership::BORROW:
        case Ownership::OWN:
        case Ownership::SHARE:
          return LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
        case Ownership::WEAK:
          return weakRefStructs.getWeakVoidRefStruct(reference->referend);
        default:
          assert(false);
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      switch (reference->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE:
          return LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
        case Ownership::BORROW:
        case Ownership::WEAK:
          return weakRefStructs.getWeakVoidRefStruct(reference->referend);
      }
      break;
    }
    default:
      assert(false);
  }
  assert(false);
}

Ref Mega::receiveUnencryptedAlienReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef) {
  assert(false);
  exit(1);
}

Ref Mega::encryptAndSendFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Ref sourceRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:

      // Dealias when sending to the outside world, see DEPAR.
      globalState->getRegion(sourceRefMT)
          ->dealias(FL(), functionState, builder, sourceRefMT, sourceRef);

      return sourceRef;
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
      // Someday we'll do some encryption stuff here
      return sourceRef;
    case RegionOverride::RESILIENT_V1:
      // Someday we'll do some encryption stuff here
      return sourceRef;
    default:
      assert(false);
  }
}

void Mega::initializeElementInUSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *usaRefMT,
    UnknownSizeArrayT *usaMT,
    Ref usaRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
      auto arrayWrapperPtrLE =
          referendStructs.makeWrapperPtr(
              FL(), functionState, builder, usaRefMT,
              globalState->getRegion(usaRefMT)->checkValidReference(FL(), functionState, builder, usaRefMT, usaRef));
      auto sizeRef = ::getUnknownSizeArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
      auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);
      ::initializeElement(
          globalState, functionState, builder, usaRefMT->location,
          usaDef->rawArray->elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
      break;
    }
    default:
      assert(false);
  }
}

Ref Mega::deinitializeElementFromUSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
      return regularLoadElementFromUSAWithoutUpgrade(
          globalState, functionState, builder, &referendStructs, usaRefMT, usaMT, usaDef->rawArray->mutability, usaDef->rawArray->elementType, arrayRef, true, indexRef).move();
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
      return resilientLoadElementFromUSAWithoutUpgrade(
          globalState, functionState, builder, &referendStructs, usaRefMT, usaDef->rawArray->mutability, usaDef->rawArray->elementType, usaMT, arrayRef, true, indexRef).move();
    }
    default:
      assert(false);
  }
}

void Mega::initializeElementInKSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  assert(false);
}

Ref Mega::deinitializeElementFromKSA(
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

Weakability Mega::getReferendWeakability(Referend* referend) {
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    return globalState->lookupStruct(structReferend->fullName)->weakability;
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
    return globalState->lookupInterface(interfaceReferend->fullName)->weakability;
  } else {
    return Weakability::NON_WEAKABLE;
  }
}

LLVMValueRef Mega::getInterfaceMethodFunctionPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    int indexInEdge) {
  return getInterfaceMethodFunctionPtrFromItable(
      globalState, functionState, builder, virtualParamMT, virtualArgRef, indexInEdge);
}
