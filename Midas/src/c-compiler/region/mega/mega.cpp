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

LLVMTypeRef makeWeakRefHeaderStruct(GlobalState* globalState) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    return LgtWeaks::makeWeakRefHeaderStruct(globalState);
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_LIMIT) {
    return HybridGenerationalMemory::makeWeakRefHeaderStruct(globalState);
  } else if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
      globalState->opt->regionOverride == RegionOverride::FAST ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V0 ||
      globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
    return WrcWeaks::makeWeakRefHeaderStruct(globalState);
  } else {
    assert(false);
  }
}

ControlBlock makeMutNonWeakableControlBlock(GlobalState* globalState) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      return makeAssistAndNaiveRCNonWeakableControlBlock(globalState);
    case RegionOverride::FAST:
      return makeFastNonWeakableControlBlock(globalState);
    case RegionOverride::RESILIENT_V0:
      return makeResilientV0WeakableControlBlock(globalState);
    case RegionOverride::RESILIENT_V1:
      return makeResilientV1WeakableControlBlock(globalState);
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      return makeResilientV2WeakableControlBlock(globalState);
    default:
      assert(false);
  }
}

ControlBlock makeMutWeakableControlBlock(GlobalState* globalState) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      return makeAssistAndNaiveRCWeakableControlBlock(globalState);
    case RegionOverride::FAST:
      return makeFastWeakableControlBlock(globalState);
    case RegionOverride::RESILIENT_V0:
      return makeResilientV0WeakableControlBlock(globalState);
    case RegionOverride::RESILIENT_V1:
      return makeResilientV1WeakableControlBlock(globalState);
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      return makeResilientV2WeakableControlBlock(globalState);
    default:
      assert(false);
  }
}

Mega::Mega(GlobalState* globalState_) :
    globalState(globalState_),
    immStructs(globalState, makeImmControlBlock(globalState)),
    mutNonWeakableStructs(globalState, makeMutNonWeakableControlBlock(globalState)),
    mutWeakableStructs(
        globalState,
        makeMutWeakableControlBlock(globalState),
        makeWeakRefHeaderStruct(globalState)),
    defaultImmutables(globalState, &immStructs),
    referendStructs(
        globalState,
        [this](Referend* referend) -> IReferendStructsSource* {
          switch (globalState->opt->regionOverride) {
            case RegionOverride::ASSIST:
            case RegionOverride::NAIVE_RC:
            case RegionOverride::FAST:
              if (globalState->program->getReferendMutability(referend) == Mutability::IMMUTABLE) {
                return &immStructs;
              } else {
                if (globalState->program->getReferendWeakability(referend) == Weakability::NON_WEAKABLE) {
                  return &mutNonWeakableStructs;
                } else {
                  return &mutWeakableStructs;
                }
              }
            case RegionOverride::RESILIENT_V0:
            case RegionOverride::RESILIENT_V1:
            case RegionOverride::RESILIENT_V2:
            case RegionOverride::RESILIENT_V3:
            case RegionOverride::RESILIENT_LIMIT:
              if (globalState->program->getReferendMutability(referend) == Mutability::IMMUTABLE) {
                return &immStructs;
              } else {
                return &mutWeakableStructs;
              }
            default:
              assert(false);
          }
        }),
    weakRefStructs(
        [this](Referend* referend) -> IWeakRefStructsSource* {
          switch (globalState->opt->regionOverride) {
            case RegionOverride::ASSIST:
            case RegionOverride::NAIVE_RC:
            case RegionOverride::FAST:
              if (globalState->program->getReferendMutability(referend) == Mutability::IMMUTABLE) {
                assert(false);
              } else {
                if (globalState->program->getReferendWeakability(referend) == Weakability::NON_WEAKABLE) {
                  assert(false);
                } else {
                  return &mutWeakableStructs;
                }
              }
            case RegionOverride::RESILIENT_V0:
            case RegionOverride::RESILIENT_V1:
            case RegionOverride::RESILIENT_V2:
            case RegionOverride::RESILIENT_V3:
            case RegionOverride::RESILIENT_LIMIT:
              if (globalState->program->getReferendMutability(referend) == Mutability::IMMUTABLE) {
                assert(false);
              } else {
                return &mutWeakableStructs;
              }
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

Ref Mega::constructKnownSizeArray(FunctionState *functionState, LLVMBuilderRef builder, Reference *referenceM, KnownSizeArrayT *referendM, const std::vector<Ref> &membersLE) {
  auto resultRef =
      ::constructKnownSizeArray(
          globalState, functionState, builder, referenceM, referendM, membersLE, &referendStructs,
          [this, functionState, referenceM, referendM](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(),
                functionState,
                innerBuilder,
                referenceM->referend,
                referendM->rawArray->mutability,
                controlBlockPtrLE,
                referendM->name->name);
          });
  alias(FL(), functionState, builder, referenceM, resultRef);
  return resultRef;
}

WrapperPtrLE Mega::mallocStr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE) {
  return ::mallocStr(
      globalState, functionState, builder, lengthLE, &referendStructs,
      [this, functionState](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
        fillControlBlock(
            FL(), functionState, innerBuilder, globalState->metalCache.str,
            Mutability::IMMUTABLE, controlBlockPtrLE, "Str");
      });
}

Ref Mega::allocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* desiredReference,
    const std::vector<Ref>& membersLE) {
  auto structReferend = dynamic_cast<StructReferend*>(desiredReference->referend);
  auto structM = globalState->program->getStruct(structReferend->fullName);
  auto resultRef =
      innerAllocate(
          FL(), globalState, functionState, builder, desiredReference, &referendStructs, membersLE, Weakability::WEAKABLE,
          [this, functionState, desiredReference, structM](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(), functionState, innerBuilder, desiredReference->referend, structM->mutability,
                controlBlockPtrLE, structM->name->name);
          });
  alias(FL(), functionState, builder, desiredReference, resultRef);
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
    case RegionOverride::ASSIST:
      assert(false);
      break;
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
    case RegionOverride::FAST: {
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
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto sourceRnd = sourceMT->referend;

  if (sourceMT->ownership == Ownership::SHARE) {
    defaultImmutables.discard(
        from, globalState, functionState, blockState, builder, sourceMT, sourceRef);
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
              [this, functionState, blockState, sourceRef, sourceMT](LLVMBuilderRef thenBuilder) {
                deallocate(FL(), functionState, thenBuilder, sourceMT, sourceRef);
              });
        } else if (sourceMT->ownership == Ownership::WEAK) {
          discardWeakRef(from, functionState, builder, sourceMT, sourceRef);
        } else assert(false);
        break;
      }
      case RegionOverride::FAST: {
        if (sourceMT->ownership == Ownership::OWN) {
          // We can't discard owns, they must be destructured.
          assert(false);
        } else if (sourceMT->ownership == Ownership::BORROW) {
          // Do nothing!
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
          // We can't discard owns, they must be destructured.
          assert(false); // impl
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
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
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
    case RegionOverride::FAST:
    case RegionOverride::ASSIST:
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
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      assert(sourceWeakRefMT->ownership == Ownership::WEAK);
      auto isAliveLE =
          getIsAliveFromWeakRef(
              functionState, builder, sourceWeakRefMT, sourceWeakRefLE, weakRefKnownLive);
      auto resultOptTypeLE = translateType(resultOptTypeM);
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
      auto resultOptTypeLE = translateType(resultOptTypeM);
      return resilientThing(globalState, functionState, builder, thenResultIsNever, elseResultIsNever, resultOptTypeM, constraintRefM, sourceWeakRefMT, sourceWeakRefLE, weakRefKnownLive, buildThen, buildElse, isAliveLE, resultOptTypeLE, &weakRefStructs);
    }
    default:
      assert(false);
      break;
  }
}

LLVMTypeRef Mega::translateType(Reference* referenceM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      switch (referenceM->ownership) {
        case Ownership::SHARE:
          return defaultImmutables.translateType(globalState, referenceM);
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
          return defaultImmutables.translateType(globalState, referenceM);
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
    case RegionOverride::FAST:
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
    KnownSizeArrayT* knownSizeArrayMT) {
  referendStructs.declareKnownSizeArray(knownSizeArrayMT);
}

void Mega::declareUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT) {
  referendStructs.declareUnknownSizeArray(unknownSizeArrayMT);
}

void Mega::translateUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT) {
  auto elementLT =
      translateType(
          unknownSizeArrayMT->rawArray->elementType);
  referendStructs.translateUnknownSizeArray(unknownSizeArrayMT, elementLT);
}

void Mega::translateKnownSizeArray(
    KnownSizeArrayT* knownSizeArrayMT) {
  auto elementLT =
      translateType(
          knownSizeArrayMT->rawArray->elementType);
  referendStructs.translateKnownSizeArray(knownSizeArrayMT, elementLT);
}

void Mega::declareStruct(
    StructDefinition* structM) {
  referendStructs.declareStruct(structM);
}

void Mega::translateStruct(
    StructDefinition* structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        translateType(
            structM->members[i]->type));
  }
  referendStructs.translateStruct(
      structM,
      innerStructMemberTypesL);
}

void Mega::declareEdge(
    Edge* edge) {
  referendStructs.declareEdge(edge);
}

void Mega::translateEdge(
    Edge* edge) {
  auto interfaceM = globalState->program->getInterface(edge->interfaceName->fullName);

  std::vector<LLVMTypeRef> interfaceFunctionsLT;
  std::vector<LLVMValueRef> edgeFunctionsL;
  for (int i = 0; i < edge->structPrototypesByInterfaceMethod.size(); i++) {
    auto interfaceFunctionLT =
        translateInterfaceMethodToFunctionType(edge->interfaceName, interfaceM->methods[i]);
    interfaceFunctionsLT.push_back(interfaceFunctionLT);

    auto funcName = edge->structPrototypesByInterfaceMethod[i].second->name;
    auto edgeFunctionL = globalState->getFunction(funcName);
    edgeFunctionsL.push_back(edgeFunctionL);
  }
  referendStructs.translateEdge(edge, interfaceFunctionsLT, edgeFunctionsL);
}

void Mega::declareInterface(
    InterfaceDefinition* interfaceM) {
  referendStructs.declareInterface(interfaceM);
}

void Mega::translateInterface(
    InterfaceDefinition* interfaceM) {
  std::vector<LLVMTypeRef> interfaceMethodTypesL;
  for (int i = 0; i < interfaceM->methods.size(); i++) {
    interfaceMethodTypesL.push_back(
        LLVMPointerType(
            translateInterfaceMethodToFunctionType(interfaceM->referend, interfaceM->methods[i]),
            0));
  }
  referendStructs.translateInterface(
      interfaceM,
      interfaceMethodTypesL);
}

LLVMTypeRef Mega::translateInterfaceMethodToFunctionType(
    InterfaceReferend* referend,
    InterfaceMethod* method) {
  auto returnMT = method->prototype->returnType;
  auto paramsMT = method->prototype->params;
  auto returnLT = translateType(returnMT);
  auto paramsLT = translateTypes(globalState, this, paramsMT);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      switch (paramsMT[method->virtualParamIndex]->ownership) {
        case Ownership::BORROW:
        case Ownership::OWN:
        case Ownership::SHARE:
          paramsLT[method->virtualParamIndex] = LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
          break;
        case Ownership::WEAK:
          paramsLT[method->virtualParamIndex] = weakRefStructs.getWeakVoidRefStruct(referend);
          break;
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      switch (paramsMT[method->virtualParamIndex]->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE:
          paramsLT[method->virtualParamIndex] = LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
          break;
        case Ownership::BORROW:
        case Ownership::WEAK:
          paramsLT[method->virtualParamIndex] = weakRefStructs.getWeakVoidRefStruct(referend);
          break;
      }
      break;
    }
    default:
      assert(false);
  }

  return LLVMFunctionType(returnLT, paramsLT.data(), paramsLT.size(), false);
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
    case RegionOverride::FAST: {
      // Do nothing

      // Free it!
      deallocate(AFL("discardOwningRef"), functionState, builder, sourceMT, sourceRef);
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
  } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
    // In fast mode, only shared things are strong RC'd
    if (refM->ownership == Ownership::SHARE) {
      // Only shared stuff is RC'd in fast mode
      auto rcIsZeroLE = strongRcIsZero(globalState, &referendStructs, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
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
    LLVMValueRef newValueLE) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      switch (structRefMT->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE:
        case Ownership::BORROW: {
          storeMemberStrong(
              globalState, functionState, builder, &referendStructs, structRefMT, structRef,
              structKnownLive, memberIndex, memberName, newValueLE);
          break;
        }
        case Ownership::WEAK: {
          storeMemberWeak(
              globalState, functionState, builder, &referendStructs, structRefMT, structRef,
              structKnownLive, memberIndex, memberName, newValueLE);
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
      switch (structRefMT->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE: {
          return storeMemberStrong(
              globalState, functionState, builder, &referendStructs, structRefMT, structRef,
              structKnownLive, memberIndex, memberName, newValueLE);
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          storeMemberWeak(
              globalState, functionState, builder, &referendStructs, structRefMT, structRef,
              structKnownLive, memberIndex, memberName, newValueLE);
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
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
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
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
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
  assert(LLVMTypeOf(refLE) == functionState->defaultRegion->translateType(refM));

  if (globalState->opt->census) {
    switch (globalState->opt->regionOverride) {
      case RegionOverride::ASSIST:
      case RegionOverride::NAIVE_RC:
      case RegionOverride::FAST: {
        if (refM->ownership == Ownership::OWN) {
          regularCheckValidReference(checkerAFL, globalState, functionState, builder, &referendStructs, refM, refLE);
        } else if (refM->ownership == Ownership::SHARE) {
          defaultImmutables.checkValidReference(checkerAFL, functionState, builder, &referendStructs, refM, refLE);
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
          defaultImmutables.checkValidReference(checkerAFL, functionState, builder, &referendStructs, refM, refLE);
        } else {
          wrcWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
        }
        return refLE;
      }
      case RegionOverride::RESILIENT_V1: {
        if (refM->ownership == Ownership::OWN) {
          regularCheckValidReference(checkerAFL, globalState, functionState, builder, &referendStructs, refM, refLE);
        } else if (refM->ownership == Ownership::SHARE) {
          defaultImmutables.checkValidReference(checkerAFL, functionState, builder, &referendStructs, refM, refLE);
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
          defaultImmutables.checkValidReference(checkerAFL, functionState, builder, &referendStructs, refM, refLE);
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
    Ref sourceRef) {
  auto sourceOwnership = sourceType->ownership;
  auto sourceLocation = sourceType->location;
  auto targetOwnership = targetType->ownership;
  auto targetLocation = targetType->location;
//  assert(sourceLocation == targetLocation); // unimplemented

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
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
          auto resultRef = transmutePtr(functionState, builder, sourceType, targetType, sourceRef);
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

        return transmutePtr(functionState, builder, sourceType, targetType, sourceRef);
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

        return transmutePtr(functionState, builder, sourceType, targetType, sourceRef);
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

        return transmutePtr(functionState, builder, sourceType, targetType, sourceRef);
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
    case RegionOverride::FAST:
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
    case RegionOverride::FAST:
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
    case RegionOverride::FAST:
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
    Mutability mutability,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
      assert(false);
      break;
    case RegionOverride::NAIVE_RC: {
      regularFillControlBlock(
          from, globalState, functionState, &referendStructs, builder, referendM, mutability, controlBlockPtrLE,
          typeName, &wrcWeaks);
      break;
    }
    case RegionOverride::FAST: {
      LLVMValueRef newControlBlockLE = LLVMGetUndef(referendStructs.getControlBlock(referendM)->getStruct());

      newControlBlockLE =
          fillControlBlockCensusFields(
              from, globalState, functionState, &referendStructs, builder, referendM, newControlBlockLE, typeName);

      if (mutability == Mutability::IMMUTABLE) {
        newControlBlockLE =
            insertStrongRc(globalState, builder, &referendStructs, referendM, newControlBlockLE);
      } else {
        if (globalState->program->getReferendWeakability(referendM) == Weakability::WEAKABLE) {
          newControlBlockLE = wrcWeaks.fillWeakableControlBlock(functionState, builder, &referendStructs, referendM,
              newControlBlockLE);
        }
      }
      LLVMBuildStore(
          builder,
          newControlBlockLE,
          controlBlockPtrLE.refLE);
      break;
    }
    case RegionOverride::RESILIENT_V0: {
      LLVMValueRef newControlBlockLE = LLVMGetUndef(referendStructs.getControlBlock(referendM)->getStruct());

      newControlBlockLE =
          fillControlBlockCensusFields(
              from, globalState, functionState, &referendStructs, builder, referendM, newControlBlockLE, typeName);

      if (mutability == Mutability::IMMUTABLE) {
        newControlBlockLE =
            insertStrongRc(globalState, builder, &referendStructs, referendM, newControlBlockLE);
      } else {
        newControlBlockLE = wrcWeaks.fillWeakableControlBlock(functionState, builder, &referendStructs, referendM,
            newControlBlockLE);
      }
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

      if (mutability == Mutability::IMMUTABLE) {
        newControlBlockLE =
            insertStrongRc(globalState, builder, &referendStructs, referendM, newControlBlockLE);
      } else {
        newControlBlockLE = lgtWeaks.fillWeakableControlBlock(functionState, builder, &referendStructs, referendM,
            newControlBlockLE);
      }
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
          from, globalState, functionState, &referendStructs, builder, referendM, mutability, controlBlockPtrLE,
          typeName, &hgmWeaks);
      break;
    }
    default:
      assert(false);
  }
}

Ref Mega::loadElementFromKSAWithUpgrade(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    Reference* targetType) {
  Ref memberRef =
      loadElementFromKSAWithoutUpgrade(
          functionState, builder, ksaRefMT, ksaMT, arrayRef, arrayKnownLive, indexRef);
  return upgradeLoadResultToRefWithTargetOwnership(
      functionState, builder, ksaMT->rawArray->elementType, targetType, memberRef);
}

Ref Mega::loadElementFromKSAWithoutUpgrade(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      return regularLoadElementFromKSAWithoutUpgrade(
          globalState, functionState, builder, ksaRefMT, ksaMT, arrayRef, arrayKnownLive, indexRef, &referendStructs);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      return resilientLoadElementFromKSAWithoutUpgrade(
          globalState, functionState, builder, ksaRefMT, ksaMT, arrayRef, arrayKnownLive, indexRef, &referendStructs);
    }
    default:
      assert(false);
  }
}

Ref Mega::loadElementFromUSAWithUpgrade(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    Reference* targetType) {
  Ref memberRef = loadElementFromUSAWithoutUpgrade(functionState, builder, usaRefMT,
      usaMT, arrayRef, arrayKnownLive, indexRef);
  return upgradeLoadResultToRefWithTargetOwnership(
      functionState, builder, usaMT->rawArray->elementType, targetType, memberRef);
}

Ref Mega::loadElementFromUSAWithoutUpgrade(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      return regularLoadElementFromUSAWithoutUpgrade(globalState, functionState, builder, &referendStructs, usaRefMT, usaMT, arrayRef, arrayKnownLive, indexRef);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      return resilientLoadElementFromUSAWithoutUpgrade(globalState, functionState, builder, &referendStructs, usaRefMT, usaMT, arrayRef, arrayKnownLive, indexRef);
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
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      return regularStoreElementInUSA(globalState, functionState, builder, &referendStructs, usaRefMT, usaMT, arrayRef, indexRef, elementRef);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT: {
      return resilientStoreElementInUSA(
          globalState, functionState, builder, &referendStructs, usaRefMT, usaMT, arrayRef,
          arrayKnownLive, indexRef, elementRef,
          [this, functionState, builder, usaRefMT, arrayRef, arrayKnownLive]() -> WrapperPtrLE {
            return lockWeakRef(FL(), functionState, builder, usaRefMT, arrayRef, arrayKnownLive);
          });
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
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
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
    Ref refLE) {
  innerDeallocate(from, globalState, functionState, &referendStructs, builder, refMT, refLE);
}

Ref Mega::constructUnknownSizeArrayCountedStruct(
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* usaMT,
    UnknownSizeArrayT* unknownSizeArrayT,
    Reference* generatorType,
    Prototype* generatorMethod,
    Ref generatorRef,
    LLVMTypeRef usaElementLT,
    Ref sizeRef,
    const std::string& typeName) {
  auto usaWrapperPtrLT =
      referendStructs.getUnknownSizeArrayWrapperStruct(unknownSizeArrayT);
  auto resultRef =
      ::constructUnknownSizeArrayCountedStruct(
           globalState, functionState, blockState, builder, &referendStructs, usaMT, unknownSizeArrayT, generatorType, generatorMethod,
           generatorRef, usaWrapperPtrLT, usaElementLT, sizeRef, typeName,
          [this, functionState, unknownSizeArrayT, usaMT, typeName](
              LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(),
                functionState,
                innerBuilder,
                unknownSizeArrayT,
                unknownSizeArrayT->rawArray->mutability,
                controlBlockPtrLE,
                typeName);
          });
  alias(FL(), functionState, builder, usaMT, resultRef);
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
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      if (structRefMT->ownership == Ownership::SHARE) {
        auto memberLE =
            defaultImmutables.loadMember(
                functionState, builder, structRefMT, structRef, memberIndex, expectedMemberType,
                targetType, memberName);
        auto resultRef =
            upgradeLoadResultToRefWithTargetOwnership(
                functionState, builder, expectedMemberType, targetType, memberLE);
        return resultRef;
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
        auto memberLE =
            defaultImmutables.loadMember(
                functionState, builder, structRefMT, structRef, memberIndex, expectedMemberType,
                targetType, memberName);
        auto resultRef =
            upgradeLoadResultToRefWithTargetOwnership(
                functionState, builder, expectedMemberType, targetType, memberLE);
        return resultRef;
      } else {
        if (structRefMT->location == Location::INLINE) {
          auto structRefLE = checkValidReference(FL(), functionState, builder,
              structRefMT, structRef);
          return wrap(globalState->region, expectedMemberType,
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
    Ref refLE) {
  auto argLE = checkValidReference(FL(), functionState, builder, refMT, refLE);
  auto structReferend = dynamic_cast<StructReferend*>(refMT->referend);
  assert(structReferend);
  assert(LLVMTypeOf(argLE) == referendStructs.getInnerStruct(structReferend));
}


std::string Mega::getRefNameC(Reference* refMT) {
  if (refMT->ownership == Ownership::SHARE) {
    return defaultImmutables.getRefNameC(refMT);
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

void Mega::generateStructDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName, StructDefinition* structDefM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
      assert(false);
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST:
//      return "void* unused;";
      assert(false); // impl
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
      assert(false); // impl
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      if (structDefM->mutability == Mutability::IMMUTABLE) {
        return defaultImmutables.generateStructDefsC(cByExportedName, structDefM);
      } else {
        auto baseName = globalState->program->getExportedName(structDefM->referend->fullName);
        auto refTypeName = baseName + "Ref";
        std::stringstream s;
        s << "typedef struct " << refTypeName << " { uint64_t unused0; void* unused1; } " << refTypeName << ";" << std::endl;
        cByExportedName->insert(std::make_pair(baseName, s.str()));
      }
      break;
  }
}

void Mega::generateInterfaceDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName, InterfaceDefinition* interfaceDefM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
      assert(false);
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST:
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
        defaultImmutables.generateInterfaceDefsC(cByExportedName, interfaceDefM);
      } else {
        auto name = globalState->program->getExportedName(interfaceDefM->referend->fullName);
        std::stringstream s;
        s << "typedef struct " << name << "Ref { uint64_t unused0; void* unused1; void* unused2; } " << name << ";";
        cByExportedName->insert(std::make_pair(name, s.str()));
      }
      break;
  }
}

LLVMTypeRef Mega::getExternalType(Reference* refMT) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
      assert(false);
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST:
      if (refMT->ownership == Ownership::SHARE) {
        return defaultImmutables.getExternalType(refMT);
      } else {
        if (auto structReferend = dynamic_cast<StructReferend*>(refMT->referend)) {
          assert(false); // impl
        } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(refMT->referend)) {
          assert(false); // impl
        } else {
          std::cerr << "Invalid type for extern!" << std::endl;
          assert(false);
        }
      }
      assert(false);
      break;
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
      if (refMT->ownership == Ownership::SHARE) {
        return defaultImmutables.getExternalType(refMT);
      } else {
        if (auto structReferend = dynamic_cast<StructReferend*>(refMT->referend)) {
          assert(false); // impl
        } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(refMT->referend)) {
          assert(false); // impl
        } else {
          std::cerr << "Invalid type for extern!" << std::endl;
          assert(false);
        }
      }
      assert(false);
      break;
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      if (refMT->ownership == Ownership::SHARE) {
        return defaultImmutables.getExternalType(refMT);
      } else {
        if (auto structReferend = dynamic_cast<StructReferend*>(refMT->referend)) {
          return weakRefStructs.getStructWeakRefStruct(structReferend);
        } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(refMT->referend)) {
          assert(false); // impl
        } else {
          std::cerr << "Invalid type for extern!" << std::endl;
          assert(false);
        }
      }
      assert(false);
      break;
    default:
      assert(false);
      break;
  }
  assert(false);
}

Ref Mega::internalify(FunctionState *functionState, LLVMBuilderRef builder, Reference *refMT, LLVMValueRef ref) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
      assert(false);
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST:
      if (refMT->ownership == Ownership::SHARE) {
        return defaultImmutables.internalify(functionState, builder, refMT, ref);
      } else {
        assert(false);
      }
      assert(false);
      break;
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
      if (refMT->ownership == Ownership::SHARE) {
        return defaultImmutables.internalify(functionState, builder, refMT, ref);
      } else {
        assert(false);
      }
      assert(false);
      break;
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      if (refMT->ownership == Ownership::SHARE) {
        return defaultImmutables.internalify(functionState, builder, refMT, ref);
      } else {
        assert(false);
      }
      assert(false);
      break;
    default:
      assert(false);
      break;
  }

  assert(false);
}

LLVMValueRef Mega::externalify(
    FunctionState* functionState, LLVMBuilderRef builder, Reference* refMT, Ref ref) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
      assert(false);
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST:
      if (refMT->ownership == Ownership::SHARE) {
        return defaultImmutables.externalify(functionState, builder, refMT, ref);
      } else {
        assert(false); // impl
      }
      assert(false);
      break;
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
      if (refMT->ownership == Ownership::SHARE) {
        return defaultImmutables.externalify(functionState, builder, refMT, ref);
      } else {
        assert(false); // impl
      }
      assert(false);
      break;
    case RegionOverride::RESILIENT_V2:
    case RegionOverride::RESILIENT_V3:
    case RegionOverride::RESILIENT_LIMIT:
      if (refMT->ownership == Ownership::SHARE) {
        return defaultImmutables.externalify(functionState, builder, refMT, ref);
      } else {
        if (globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
          std::cerr
              << "Naive-rc can't call externs with mutables safely yet. (Naive-rc is an experimental region only for use in perf comparisons)"
              << std::endl;
          exit(1);
        }

        if (auto structReferend = dynamic_cast<StructReferend*>(refMT->referend)) {
          assert(refMT->location != Location::INLINE);
          // The outside world gets generational references, even to owning things.
          // So, we need to make an owning generational ref.
          auto borrowRefMT =
              globalState->metalCache.getReference(
                  Ownership::BORROW, Location::YONDER, structReferend);

          switch (refMT->ownership) {
            case Ownership::OWN: {
              auto wrapperPtrLE =
                  referendStructs.makeWrapperPtr(
                      FL(), functionState, builder, refMT,
                      checkValidReference(FL(), functionState, builder, refMT, ref));
              auto weakRefLE =
                  hgmWeaks.assembleStructWeakRef(
                      functionState, builder, refMT, borrowRefMT, structReferend, wrapperPtrLE);
              // All this rigamarole is to get the LLVMValueRef of the above weakRefLE.
              return checkValidReference(
                  FL(), functionState, builder, borrowRefMT,
                  wrap(functionState->defaultRegion, borrowRefMT, weakRefLE));
              break;
            }
            case Ownership::BORROW:
            case Ownership::WEAK:
              return checkValidReference(FL(), functionState, builder, refMT, ref);
            default:
              assert(false);
          }
        } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(refMT->referend)) {
          assert(false); // impl
        } else {
          std::cerr << "Invalid type for extern!" << std::endl;
          assert(false);
        }
      }
      assert(false);
      break;
    default:
      assert(false);
      break;
  }

  assert(false);
}
