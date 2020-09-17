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
#include "mega.h"

// Transmutes a ptr of one ownership (such as own) to another ownership (such as borrow).
Ref transmutePtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef) {
  // The WrapperPtrLE constructors here will make sure that its a safe and valid transmutation.
  auto sourcePtrRawLE =
      functionState->defaultRegion->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef);
  auto targetWeakRef = wrap(functionState->defaultRegion, targetRefMT, sourcePtrRawLE);
  return targetWeakRef;
}


ControlBlock makeAssistAndNaiveRCNonWeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(LLVMStructCreateNamed(LLVMGetGlobalContext(), "mutNonWeakableControlBlock"));
  controlBlock.addMember(ControlBlockMember::STRONG_RC);
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

ControlBlock makeAssistAndNaiveRCWeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(LLVMStructCreateNamed(LLVMGetGlobalContext(), "mutWeakableControlBlock"));
  controlBlock.addMember(ControlBlockMember::STRONG_RC);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.addMember(ControlBlockMember::WRCI);
  // We could add this in to avoid an InstructionCombiningPass bug where when it inlines things
  // it doesnt seem to realize that there's padding at the end of structs.
  // To see it, make loadFromWeakable test in fast mode, see its .ll and its .opt.ll, it seems
  // to get the wrong pointer for the first member.
  // mutWeakableControlBlock.addMember(ControlBlockMember::UNUSED_32B);
  controlBlock.build();
  return controlBlock;
}
// TODO see if we can combine this with assist+naiverc weakable.
ControlBlock makeFastWeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(LLVMStructCreateNamed(LLVMGetGlobalContext(), "mutWeakableControlBlock"));
  // Fast mode mutables have no strong RC
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.addMember(ControlBlockMember::WRCI);
  controlBlock.build();
  return controlBlock;
}

ControlBlock makeFastNonWeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(LLVMStructCreateNamed(LLVMGetGlobalContext(), "mutNonWeakableControlBlock"));
  // Fast mode mutables have no strong RC
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
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


ControlBlock makeResilientV0WeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(LLVMStructCreateNamed(LLVMGetGlobalContext(), "mutWeakableControlBlock"));
  controlBlock.addMember(ControlBlockMember::WRCI);
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
ControlBlock makeResilientV1WeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(LLVMStructCreateNamed(LLVMGetGlobalContext(), "mutControlBlock"));
  controlBlock.addMember(ControlBlockMember::LGTI);
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
ControlBlock makeResilientV2WeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(LLVMStructCreateNamed(LLVMGetGlobalContext(), "mutControlBlock"));
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

//StructsRouter makeAssistAndNaiveRCModeLayoutter(GlobalState* globalState) {
//  return StructsRouter(
//      globalState,
//      makeImmControlBlock(globalState),
//      makeAssistAndNaiveRCWeakableControlBlock(globalState),
//      makeAssistAndNaiveRCNonWeakableControlBlock(globalState));
//}
//StructsRouter makeFastModeLayoutter(GlobalState* globalState) {
//  return StructsRouter(
//      globalState,
//      makeImmControlBlock(globalState),
//      makeFastNonWeakableControlBlock(globalState),
//      makeFastWeakableControlBlock(globalState));
//}
//StructsRouter makeResilientV0Layoutter(GlobalState* globalState) {
//  return StructsRouter(
//      globalState,
//      makeImmControlBlock(globalState),
//      makeResilientV0WeakableControlBlock(globalState),
//      makeResilientV0WeakableControlBlock(globalState));
//}
//StructsRouter makeResilientV1Layoutter(GlobalState* globalState) {
//  return StructsRouter(
//      globalState,
//      makeImmControlBlock(globalState),
//      makeResilientV1WeakableControlBlock(globalState),
//      makeResilientV1WeakableControlBlock(globalState));
//}
//StructsRouter makeResilientV2Layoutter(GlobalState* globalState) {
//  return StructsRouter(
//      globalState,
//      makeImmControlBlock(globalState),
//      makeResilientV2WeakableControlBlock(globalState),
//      makeResilientV2WeakableControlBlock(globalState));
//}

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
      return makeResilientV2WeakableControlBlock(globalState);
    default:
      assert(false);
  }
}

LLVMTypeRef makeWeakRefHeaderStruct(GlobalState* globalState) {
  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    return LgtWeaks::makeWeakRefHeaderStruct(globalState);
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
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
    wrcWeaks(globalState_, &weakRefStructs),
    lgtWeaks(globalState_, &weakRefStructs),
    hgmWeaks(globalState_, &weakRefStructs),
    controlBlockPtrMaker(
        [this](Referend* referend){ return referendStructs.getControlBlock(referend)->getStruct(); }),
    interfaceFatPtrMaker(
        [this](InterfaceReferend* referend){ return referendStructs.getInterfaceRefStruct(referend); }),
    wrapperPtrMaker(
        [this](Reference* reference) -> LLVMTypeRef {
          auto referend = reference->referend;
          if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
            return referendStructs.getWrapperStruct(structReferend);
          } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
            assert(false); // can we even get a wrapper struct for an interface?
          } else if (auto ksaMT = dynamic_cast<KnownSizeArrayT*>(referend)) {
            return referendStructs.getKnownSizeArrayWrapperStruct(ksaMT);
          } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT*>(referend)) {
            return referendStructs.getUnknownSizeArrayWrapperStruct(usaMT);
          } else if (auto strMT = dynamic_cast<Str*>(referend)) {
            return defaultImmutables.getStringWrapperStructL();
          } else assert(false);
        }) {
}

void fillInnerStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structM,
    std::vector<Ref> membersLE,
    LLVMValueRef innerStructPtrLE) {
  for (int i = 0; i < membersLE.size(); i++) {
    auto memberRef = membersLE[i];
    auto memberType = structM->members[i]->type;

    auto memberName = structM->members[i]->name;
    if (structM->members[i]->type->referend == globalState->metalCache.innt) {
      buildFlare(FL(), globalState, functionState, builder, "Initialized member ", memberName, ": ", memberRef);
    }
    auto ptrLE =
        LLVMBuildStructGEP(builder, innerStructPtrLE, i, memberName.c_str());
    auto memberLE =
        globalState->region->checkValidReference(FL(), functionState, builder, structM->members[i]->type, memberRef);
    LLVMBuildStore(builder, memberLE, ptrLE);
  }
}

Ref constructCountedStruct(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMTypeRef structL,
    Reference* structTypeM,
    StructDefinition* structM,
    Weakability effectiveWeakability,
    std::vector<Ref> membersLE) {
  buildFlare(FL(), globalState, functionState, builder, "Filling new struct: ", structM->name->name);
  WrapperPtrLE newStructWrapperPtrLE =
      functionState->defaultRegion->makeWrapperPtr(
          structTypeM,
          mallocKnownSize(globalState, functionState, builder, structTypeM->location, structL));
  globalState->region->fillControlBlock(
      from,
      functionState, builder,
      structTypeM->referend,
      structM->mutability,
      getConcreteControlBlockPtr(globalState, builder, newStructWrapperPtrLE), structM->name->name);
  fillInnerStruct(
      globalState, functionState,
      builder, structM, membersLE,
      getStructContentsPtr(builder, newStructWrapperPtrLE));
  buildFlare(FL(), globalState, functionState, builder, "Done filling new struct");
  return wrap(functionState->defaultRegion, structTypeM, newStructWrapperPtrLE.refLE);
}

LLVMValueRef constructInnerStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structM,
    LLVMTypeRef valStructL,
    const std::vector<Ref>& memberRefs) {

  // We always start with an undef, and then fill in its fields one at a
  // time.
  LLVMValueRef structValueBeingInitialized = LLVMGetUndef(valStructL);
  for (int i = 0; i < memberRefs.size(); i++) {
    if (structM->members[i]->type->referend == globalState->metalCache.innt) {
      buildFlare(FL(), globalState, functionState, builder, "Initialized member ", i, ": ", memberRefs[i]);
    }
    auto memberLE =
        globalState->region->checkValidReference(FL(), functionState, builder, structM->members[i]->type, memberRefs[i]);
    auto memberName = structM->members[i]->name;
    // Every time we fill in a field, it actually makes a new entire
    // struct value, and gives us a LLVMValueRef for the new value.
    // So, `structValueBeingInitialized` contains the latest one.
    structValueBeingInitialized =
        LLVMBuildInsertValue(
            builder,
            structValueBeingInitialized,
            memberLE,
            i,
            memberName.c_str());
  }
  return structValueBeingInitialized;
}

Ref Mega::allocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* desiredReference,
    const std::vector<Ref>& membersLE) {
  auto structReferend = dynamic_cast<StructReferend*>(desiredReference->referend);
  auto structM = globalState->program->getStruct(structReferend->fullName);

  Weakability effectiveWeakability = Weakability::WEAKABLE;
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST:
      effectiveWeakability = structM->weakability;
      break;
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      if (structM->mutability == Mutability::MUTABLE) {
        effectiveWeakability = Weakability::WEAKABLE;
      } else {
        effectiveWeakability = Weakability::NON_WEAKABLE;
      }
      break;
    default:
      assert(false);
  }

  switch (structM->mutability) {
    case Mutability::MUTABLE: {
      auto countedStructL = referendStructs.getWrapperStruct(structReferend);
      return constructCountedStruct(
          from, globalState, functionState, builder, countedStructL, desiredReference, structM, effectiveWeakability, membersLE);
    }
    case Mutability::IMMUTABLE: {
      if (desiredReference->location == Location::INLINE) {
        auto valStructL =
            referendStructs.getInnerStruct(structReferend);
        auto innerStructLE =
            constructInnerStruct(
                globalState, functionState, builder, structM, valStructL, membersLE);
        return wrap(this, desiredReference, innerStructLE);
      } else {
        auto countedStructL =
            referendStructs.getWrapperStruct(structReferend);
        return constructCountedStruct(
            from, globalState, functionState, builder, countedStructL, desiredReference, structM, effectiveWeakability, membersLE);
      }
    }
    default:
      assert(false);
  }
  assert(false);
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
          // We might be loading a member as an own if we're destructuring.
          // Don't adjust the RC, since we're only moving it.
        } else if (sourceRef->ownership == Ownership::BORROW) {
          adjustStrongRc(from, globalState, functionState, builder, expr, sourceRef, 1);
        } else if (sourceRef->ownership == Ownership::WEAK) {
          aliasWeakRef(from, functionState, builder, sourceRef, expr);
        } else if (sourceRef->ownership == Ownership::SHARE) {
          if (sourceRef->location == Location::INLINE) {
            // Do nothing, we can just let inline structs disappear
          } else {
            adjustStrongRc(from, globalState, functionState, builder, expr, sourceRef, 1);
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
            adjustStrongRc(from, globalState, functionState, builder, expr, sourceRef, 1);
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
    case RegionOverride::RESILIENT_V2: {
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
            adjustStrongRc(from, globalState, functionState, builder, expr, sourceRef, 1);
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

void Mega::naiveRcFree(
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef thenBuilder,
    Reference* sourceMT,
    Ref sourceRef) {
  if (dynamic_cast<InterfaceReferend *>(sourceMT->referend)) {
    auto sourceInterfacePtrLE =
        makeInterfaceFatPtr(
            sourceMT,
            checkValidReference(FL(), functionState, thenBuilder, sourceMT, sourceRef));
    auto controlBlockPtrLE = getControlBlockPtr(globalState, thenBuilder,
        sourceInterfacePtrLE);
    deallocate(FL(), globalState, functionState, thenBuilder,
        controlBlockPtrLE, sourceMT);
  } else if (dynamic_cast<StructReferend *>(sourceMT->referend) ||
      dynamic_cast<KnownSizeArrayT *>(sourceMT->referend) ||
      dynamic_cast<UnknownSizeArrayT *>(sourceMT->referend)) {
    auto sourceWrapperPtrLE =
        makeWrapperPtr(
            sourceMT,
            checkValidReference(FL(), functionState, thenBuilder, sourceMT, sourceRef));
    auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, thenBuilder, sourceWrapperPtrLE);
    deallocate(FL(), globalState, functionState, thenBuilder,
        controlBlockPtrLE, sourceMT);
  } else {
    assert(false);
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
          auto rcLE = adjustStrongRc(from, globalState, functionState, builder, sourceRef, sourceMT, -1);
          buildIf(
              functionState, builder, isZeroLE(builder, rcLE),
              [this, functionState, blockState, sourceRef, sourceMT](LLVMBuilderRef thenBuilder) {
                naiveRcFree(functionState, blockState, thenBuilder, sourceMT, sourceRef);
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
      case RegionOverride::RESILIENT_V2: {
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


// Transmutes a weak ref of one ownership (such as borrow) to another ownership (such as weak).
Ref Mega::transmuteWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceWeakRefMT,
    Reference* targetWeakRefMT,
    Ref sourceWeakRef) {
  // The WeakFatPtrLE constructors here will make sure that its a safe and valid transmutation.
  auto sourceWeakFatPtrLE =
      weakRefStructs.makeWeakFatPtr(
          sourceWeakRefMT,
          checkValidReference(FL(), functionState, builder, sourceWeakRefMT, sourceWeakRef));
  auto sourceWeakFatPtrRawLE = sourceWeakFatPtrLE.refLE;
  auto targetWeakFatPtrLE = weakRefStructs.makeWeakFatPtr(targetWeakRefMT, sourceWeakFatPtrRawLE);
  auto targetWeakRef = wrap(this, targetWeakRefMT, targetWeakFatPtrLE);
  return targetWeakRef;
}

Ref Mega::weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) {
  assert(sourceRefMT->ownership == Ownership::BORROW);
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      if (auto structReferendM = dynamic_cast<StructReferend*>(sourceRefMT->referend)) {
        auto objPtrLE =
            this->makeWrapperPtr(
                sourceRefMT,
                globalState->region->checkValidReference(FL(), functionState, builder, sourceRefMT,
                    sourceRef));
        return wrap(
            this,
            targetRefMT,
            wrcWeaks.assembleStructWeakRef(
                functionState, builder,
                sourceRefMT, targetRefMT, structReferendM, objPtrLE));
      } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(sourceRefMT->referend)) {
        assert(false); // impl
      } else assert(false);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      return transmuteWeakRef(functionState, builder, sourceRefMT, targetRefMT, sourceRef);
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
    Ref weakRefLE) {
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
              weakRefStructs.makeWeakFatPtr(                  refM,
                  globalState->region->checkValidReference(FL(), functionState, builder, refM, weakRefLE));
          return functionState->defaultRegion->makeWrapperPtr(refM,
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
                  globalState->region->checkValidReference(FL(), functionState, builder, refM, weakRefLE));
          return functionState->defaultRegion->makeWrapperPtr(refM,
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
              weakRefStructs.makeWeakFatPtr(                  refM,
                  globalState->region->checkValidReference(FL(), functionState, builder, refM, weakRefLE));
          return functionState->defaultRegion->makeWrapperPtr(
              refM,
              lgtWeaks.lockLgtiFatPtr(from, functionState, builder, refM, weakFatPtrLE));
        }
        default:
          assert(false);
          break;
      }
    }
    case RegionOverride::RESILIENT_V2: {
      switch (refM->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE: {
          auto objPtrLE = weakRefLE;
          auto weakFatPtrLE =
              globalState->region->checkValidReference(FL(), functionState, builder, refM, weakRefLE);
          return functionState->defaultRegion->makeWrapperPtr(refM, weakFatPtrLE);
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto weakFatPtrLE =
              weakRefStructs.makeWeakFatPtr(                  refM,
                  globalState->region->checkValidReference(FL(), functionState, builder, refM, weakRefLE));
          return functionState->defaultRegion->makeWrapperPtr(
              refM,
              hgmWeaks.lockGenFatPtr(from, functionState, builder, refM, weakFatPtrLE));
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
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      assert(sourceWeakRefMT->ownership == Ownership::WEAK);
      break;
    }
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V2: {
      assert(sourceWeakRefMT->ownership == Ownership::BORROW ||
          sourceWeakRefMT->ownership == Ownership::WEAK);
      break;
    }
    default:
      assert(false);
      break;
  }

  auto isAliveLE = getIsAliveFromWeakRef(functionState, builder, sourceWeakRefMT, sourceWeakRefLE);

  auto resultOptTypeLE = translateType(resultOptTypeM);

  return buildIfElse(
      globalState, functionState, builder, isAliveLE,
      resultOptTypeLE, resultOptTypeM, resultOptTypeM,
      [this, functionState, constraintRefM, sourceWeakRefLE, sourceWeakRefMT, buildThen](LLVMBuilderRef thenBuilder) {
        // TODO extract more of this common code out?
        switch (globalState->opt->regionOverride) {
          case RegionOverride::NAIVE_RC:
          case RegionOverride::FAST: {
            auto weakFatPtrLE =
                weakRefStructs.makeWeakFatPtr(
                    sourceWeakRefMT,
                    checkValidReference(FL(), functionState, thenBuilder, sourceWeakRefMT, sourceWeakRefLE));
            auto constraintRefLE =
                fatWeaks.getInnerRefFromWeakRef(
                    functionState,
                    thenBuilder,
                    sourceWeakRefMT,
                    weakFatPtrLE);
            auto constraintRef =
                wrap(this, constraintRefM, constraintRefLE);
            return buildThen(thenBuilder, constraintRef);
          }
          case RegionOverride::RESILIENT_V1:
          case RegionOverride::RESILIENT_V0:
          case RegionOverride::RESILIENT_V2: {
            // The incoming "constraint" ref is actually already a week ref, so just return it
            // (after wrapping it in a different Ref that actually thinks/knows it's a weak
            // reference).
            auto constraintRef =
                transmuteWeakRef(
                    functionState, thenBuilder, sourceWeakRefMT, constraintRefM, sourceWeakRefLE);
            return buildThen(thenBuilder, constraintRef);
          }
          default:
            assert(false);
            break;
        }
      },
      buildElse);
}

void fillKnownSizeArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* elementMT,
    LLVMValueRef arrayLE,
    const std::vector<Ref>& elementsLE) {

  for (int i = 0; i < elementsLE.size(); i++) {
    auto memberName = std::string("element") + std::to_string(i);
    LLVMValueRef indices[2] = {
        LLVMConstInt(LLVMInt64Type(), 0, false),
        LLVMConstInt(LLVMInt64Type(), i, false),
    };
    auto elementLE = globalState->region->checkValidReference(FL(), functionState, builder, elementMT, elementsLE[i]);
    // Every time we fill in a field, it actually makes a new entire
    // struct value, and gives us a LLVMValueRef for the new value.
    // So, `structValueBeingInitialized` contains the latest one.
    LLVMBuildStore(
        builder,
        elementLE,
        LLVMBuildGEP(builder, arrayLE, indices, 2, memberName.c_str()));
  }
}

// Returns a LLVMValueRef for a ref to the string object.
// The caller should then use getStringBytesPtr to then fill the string's contents.
Ref Mega::constructKnownSizeArray(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    KnownSizeArrayT* ksaMT,
    const std::vector<Ref>& membersLE) {

  auto structLT =
      referendStructs.getKnownSizeArrayWrapperStruct(ksaMT);
  auto newStructLE =
      this->makeWrapperPtr(
          refM, mallocKnownSize(globalState, functionState, builder, refM->location, structLT));
  fillControlBlock(
      FL(),
      functionState,
      builder,
      refM->referend,
      ksaMT->rawArray->mutability,
      getConcreteControlBlockPtr(globalState, builder, newStructLE),
      ksaMT->name->name);
  fillKnownSizeArray(
      globalState,
      functionState,
      builder,
      ksaMT->rawArray->elementType,
      getKnownSizeArrayContentsPtr(builder, newStructLE),
      membersLE);
  return wrap(this, refM, newStructLE.refLE);
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
          return translateReferenceSimple(globalState, referenceM->referend);
        case Ownership::WEAK:
          assert(referenceM->location != Location::INLINE);
          return translateWeakReference(globalState, referenceM->referend);
        default:
          assert(false);
      }
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (referenceM->ownership) {
        case Ownership::SHARE:
          return defaultImmutables.translateType(globalState, referenceM);
        case Ownership::OWN:
          assert(referenceM->location != Location::INLINE);
          return translateReferenceSimple(globalState, referenceM->referend);
        case Ownership::BORROW:
        case Ownership::WEAK:
          assert(referenceM->location != Location::INLINE);
          return translateWeakReference(globalState, referenceM->referend);
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
    case RegionOverride::RESILIENT_V2: {
      auto resultWeakInterfaceFatPtr =
          hgmWeaks.weakStructPtrToGenWeakInterfacePtr(
              globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
              sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
      return wrap(this, targetInterfaceTypeM, resultWeakInterfaceFatPtr);
    }
    case RegionOverride::RESILIENT_V1: {
      auto resultWeakInterfaceFatPtr =
          lgtWeaks.weakStructPtrToLgtiWeakInterfacePtr(
              globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
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
  std::vector<LLVMValueRef> functions;
  for (int i = 0; i < edge->structPrototypesByInterfaceMethod.size(); i++) {
    auto funcName = edge->structPrototypesByInterfaceMethod[i].second->name;
    functions.push_back(globalState->getFunction(funcName));
  }
  referendStructs.translateEdge(edge, functions);
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
            translateInterfaceMethodToFunctionType(interfaceM->methods[i]),
            0));
  }
  referendStructs.translateInterface(
      interfaceM,
      interfaceMethodTypesL);
}

LLVMTypeRef Mega::translateInterfaceMethodToFunctionType(
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
          paramsLT[method->virtualParamIndex] = LLVMPointerType(LLVMVoidType(), 0);
          break;
        case Ownership::WEAK:
          paramsLT[method->virtualParamIndex] = globalState->region->getWeakVoidRefStruct();
          break;
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (paramsMT[method->virtualParamIndex]->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE:
          paramsLT[method->virtualParamIndex] = LLVMPointerType(LLVMVoidType(), 0);
          break;
        case Ownership::BORROW:
        case Ownership::WEAK:
          paramsLT[method->virtualParamIndex] = globalState->region->getWeakVoidRefStruct();
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
  auto exprWrapperPtrLE =
      this->makeWrapperPtr(
          sourceMT,
          checkValidReference(FL(), functionState, builder, sourceMT, sourceRef));

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC: {
      auto rcLE =
          adjustStrongRc(
              AFL("Destroy decrementing the owning ref"),
              globalState, functionState, builder, sourceRef, sourceMT, -1);
      buildIf(
          functionState, builder, isZeroLE(builder, rcLE),
          [this, functionState, blockState, sourceRef, exprWrapperPtrLE, sourceMT](
              LLVMBuilderRef thenBuilder) {
            if (dynamic_cast<InterfaceReferend*>(sourceMT->referend)) {
              auto sourceInterfacePtrLE =
                  this->makeInterfaceFatPtr(
                      sourceMT,
                      checkValidReference(FL(), functionState, thenBuilder, sourceMT, sourceRef));
              auto controlBlockPtrLE = getControlBlockPtr(globalState, thenBuilder, sourceInterfacePtrLE);
              deallocate(FL(), globalState, functionState, thenBuilder,
                  controlBlockPtrLE, sourceMT);
            } else if (dynamic_cast<StructReferend*>(sourceMT->referend) ||
                dynamic_cast<KnownSizeArrayT*>(sourceMT->referend) ||
                dynamic_cast<UnknownSizeArrayT*>(sourceMT->referend)) {
              auto sourceWrapperPtrLE =
                  this->makeWrapperPtr(
                      sourceMT,
                      checkValidReference(FL(), functionState, thenBuilder, sourceMT,
                          sourceRef));
              auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, thenBuilder, sourceWrapperPtrLE);
              deallocate(FL(), globalState, functionState, thenBuilder, controlBlockPtrLE, sourceMT);
            } else {
              assert(false);
            }
          });
      break;
    }
    case RegionOverride::FAST: {
      // Do nothing

      auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, exprWrapperPtrLE);
      // Free it!
      deallocate(AFL("discardOwningRef"), globalState, functionState, builder,
          controlBlockPtrLE, sourceMT);
      break;
    }
    case RegionOverride::RESILIENT_V0: {
      // Mutables in resilient mode dont have strong RC, and also, they dont adjust
      // weak RC for owning refs

      auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, exprWrapperPtrLE);
      // Free it!
      deallocate(AFL("discardOwningRef"), globalState, functionState, builder,
          controlBlockPtrLE, sourceMT);
      break;
    }
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      // Mutables in resilient v1+2 dont have strong RC, and also, they dont adjust
      // weak RC for owning refs

      auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, exprWrapperPtrLE);
      // Free it!
      deallocate(AFL("discardOwningRef"), globalState, functionState, builder,
          controlBlockPtrLE, sourceMT);
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
      auto rcIsZeroLE = strongRcIsZero(globalState, builder, refM, controlBlockPtrLE);
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
      auto rcIsZeroLE = strongRcIsZero(globalState, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
    } else {
      assert(refM->ownership == Ownership::OWN);

      // In resilient mode, every mutable is weakable.
      wrcWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
    }
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
    if (refM->ownership == Ownership::SHARE) {
      auto rcIsZeroLE = strongRcIsZero(globalState, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
    } else {
      assert(refM->ownership == Ownership::OWN);

      // In resilient mode, every mutable is weakable.
      lgtWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
    }
  } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
    if (refM->ownership == Ownership::SHARE) {
      auto rcIsZeroLE = strongRcIsZero(globalState, builder, refM, controlBlockPtrLE);
      buildAssert(globalState, functionState, builder, rcIsZeroLE,
          "Tried to free concrete that had nonzero RC!");
    } else {
      assert(refM->ownership == Ownership::OWN);

      // In resilient mode, every mutable is weakable.
      hgmWeaks.innerNoteWeakableDestroyed(functionState, builder, refM, controlBlockPtrLE);
    }
  } else assert(false);
}

Ref Mega::loadMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {

  LLVMValueRef innerStructPtrLE = nullptr;
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      if (structRefMT->location == Location::INLINE) {
        auto structRefLE = checkValidReference(FL(), functionState, builder,
            structRefMT, structRef);
        return wrap(globalState->region, expectedMemberType,
            LLVMBuildExtractValue(
                builder, structRefLE, memberIndex, memberName.c_str()));
      } else {
        switch (structRefMT->ownership) {
          case Ownership::OWN:
          case Ownership::SHARE:
          case Ownership::BORROW:
            innerStructPtrLE = getStructContentsPtrNormal(globalState, functionState, builder,
                structRefMT, structRef);
            break;
          case Ownership::WEAK:
            assert(false); // we arent supposed to force in naive/fast
//            innerStructPtrLE = getStructContentsPtrForce(globalState, functionState, builder,
//                structRefMT, structRef);
            break;
          default:
            assert(false);
        }
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {

      if (structRefMT->location == Location::INLINE) {
        auto structRefLE = checkValidReference(FL(), functionState, builder,
            structRefMT, structRef);
        return wrap(globalState->region, expectedMemberType,
            LLVMBuildExtractValue(
                builder, structRefLE, memberIndex, memberName.c_str()));
      } else {
        switch (structRefMT->ownership) {
          case Ownership::OWN:
          case Ownership::SHARE:
            innerStructPtrLE = getStructContentsPtrNormal(globalState, functionState, builder,
                structRefMT, structRef);
            break;
          case Ownership::BORROW:
          case Ownership::WEAK:
            innerStructPtrLE = getStructContentsPtrForce(globalState, functionState, builder,
                structRefMT, structRef);
            break;
          default:
            assert(false);
        }
      }
      break;
    }
    default:
      assert(false);
  }

  auto memberLE = loadInnerInnerStructMember(this, builder, innerStructPtrLE, memberIndex, expectedMemberType, memberName);
  auto resultRef =
      upgradeLoadResultToRefWithTargetOwnership(
          functionState, builder, expectedMemberType, targetType, memberLE);
  return resultRef;
}

void Mega::storeMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newValueLE) {

  LLVMValueRef innerStructPtrLE = nullptr;
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      switch (structRefMT->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE:
        case Ownership::BORROW:
          innerStructPtrLE = getStructContentsPtrNormal(globalState, functionState, builder, structRefMT, structRef);
          break;
        case Ownership::WEAK:
          innerStructPtrLE = getStructContentsPtrForce(globalState, functionState, builder, structRefMT, structRef);
          break;
        default:
          assert(false);
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (structRefMT->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE:
          innerStructPtrLE = getStructContentsPtrNormal(globalState, functionState, builder, structRefMT, structRef);
          break;
        case Ownership::BORROW:
        case Ownership::WEAK:
          innerStructPtrLE = getStructContentsPtrForce(globalState, functionState, builder, structRefMT, structRef);
          break;
        default:
          assert(false);
      }
      break;
    }
    default:
      assert(false);
  }

  storeInnerInnerStructMember(builder, innerStructPtrLE, memberIndex, memberName, newValueLE);
}

// Gets the itable PTR and the new value that we should put into the virtual param's slot
// (such as a void* or a weak void ref)
std::tuple<LLVMValueRef, LLVMValueRef> Mega::explodeInterfaceRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef) {
  auto virtualArgLE =
      checkValidReference(FL(), functionState, builder, virtualParamMT, virtualArgRef);

  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef newVirtualArgLE = nullptr;
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      switch (virtualParamMT->ownership) {
        case Ownership::OWN:
        case Ownership::BORROW:
        case Ownership::SHARE: {
          auto virtualArgInterfaceFatPtrLE = functionState->defaultRegion->makeInterfaceFatPtr(virtualParamMT, virtualArgLE);
          itablePtrLE =
              getItablePtrFromInterfacePtr(
                  globalState, functionState, builder, virtualParamMT, virtualArgInterfaceFatPtrLE);
          auto objVoidPtrLE = getVoidPtrFromInterfacePtr(globalState, functionState, builder,
              virtualParamMT, virtualArgInterfaceFatPtrLE);
          newVirtualArgLE = objVoidPtrLE;
          break;
        }
        case Ownership::WEAK: {
          auto weakFatPtrLE = weakRefStructs.makeWeakFatPtr(virtualParamMT, virtualArgLE);
          // Disassemble the weak interface ref.
          auto interfaceRefLE =
              functionState->defaultRegion->makeInterfaceFatPtr(
                  virtualParamMT,
                  fatWeaks.getInnerRefFromWeakRef(
                      functionState, builder, virtualParamMT, weakFatPtrLE));
          itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);
          // Now, reassemble a weak void* ref to the struct.
          auto weakVoidStructRefLE =
              wrcWeaks.weakInterfaceRefToWeakStructRef(
                  functionState, builder, virtualParamMT, weakFatPtrLE);
          newVirtualArgLE = weakVoidStructRefLE.refLE;
          break;
        }
      }
      break;
    }
    case RegionOverride::RESILIENT_V0: {
      switch (virtualParamMT->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE: {
          auto virtualArgInterfaceFatPtrLE = functionState->defaultRegion->makeInterfaceFatPtr(virtualParamMT, virtualArgLE);
          itablePtrLE = getItablePtrFromInterfacePtr(globalState, functionState, builder, virtualParamMT, virtualArgInterfaceFatPtrLE);
          auto objVoidPtrLE = getVoidPtrFromInterfacePtr(globalState, functionState, builder,
              virtualParamMT, virtualArgInterfaceFatPtrLE);
          newVirtualArgLE = objVoidPtrLE;
          break;
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto virtualArgWeakRef = weakRefStructs.makeWeakFatPtr(virtualParamMT, virtualArgLE);
          // Disassemble the weak interface ref.
          auto interfaceRefLE =
              functionState->defaultRegion->makeInterfaceFatPtr(
                  virtualParamMT,
                  fatWeaks.getInnerRefFromWeakRef(
                      functionState, builder, virtualParamMT, virtualArgWeakRef));
          itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);
          // Now, reassemble a weak void* ref to the struct.
          auto weakVoidStructRefLE =
              wrcWeaks.weakInterfaceRefToWeakStructRef(
                  functionState, builder, virtualParamMT, virtualArgWeakRef);
          newVirtualArgLE = weakVoidStructRefLE.refLE;
          break;
        }
      }
      break;
    }
    case RegionOverride::RESILIENT_V1: {
      switch (virtualParamMT->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE: {
          auto virtualArgInterfaceFatPtrLE = functionState->defaultRegion->makeInterfaceFatPtr(virtualParamMT, virtualArgLE);
          itablePtrLE = getItablePtrFromInterfacePtr(globalState, functionState, builder, virtualParamMT, virtualArgInterfaceFatPtrLE);
          auto objVoidPtrLE = getVoidPtrFromInterfacePtr(globalState, functionState, builder,
              virtualParamMT, virtualArgInterfaceFatPtrLE);
          newVirtualArgLE = objVoidPtrLE;
          break;
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto virtualArgWeakRef = weakRefStructs.makeWeakFatPtr(virtualParamMT, virtualArgLE);
          // Disassemble the weak interface ref.
          auto interfaceRefLE =
              functionState->defaultRegion->makeInterfaceFatPtr(
                  virtualParamMT,
                  fatWeaks.getInnerRefFromWeakRef(
                      functionState, builder, virtualParamMT, virtualArgWeakRef));
          itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);
          // Now, reassemble a weak void* ref to the struct.
          auto weakVoidStructRefLE =
              lgtWeaks.weakInterfaceRefToWeakStructRef(
                  functionState, builder, virtualParamMT, virtualArgWeakRef);
          newVirtualArgLE = weakVoidStructRefLE.refLE;
          break;
        }
      }
      break;
    }
    case RegionOverride::RESILIENT_V2: {
      switch (virtualParamMT->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE: {
          auto virtualArgInterfaceFatPtrLE = functionState->defaultRegion->makeInterfaceFatPtr(virtualParamMT, virtualArgLE);
          itablePtrLE = getItablePtrFromInterfacePtr(globalState, functionState, builder, virtualParamMT, virtualArgInterfaceFatPtrLE);
          auto objVoidPtrLE = getVoidPtrFromInterfacePtr(globalState, functionState, builder,
              virtualParamMT, virtualArgInterfaceFatPtrLE);
          newVirtualArgLE = objVoidPtrLE;
          break;
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto virtualArgWeakRef = weakRefStructs.makeWeakFatPtr(virtualParamMT, virtualArgLE);
          // Disassemble the weak interface ref.
          auto interfaceRefLE =
              functionState->defaultRegion->makeInterfaceFatPtr(
                  virtualParamMT,
                  fatWeaks.getInnerRefFromWeakRef(
                      functionState, builder, virtualParamMT, virtualArgWeakRef));
          itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);
          // Now, reassemble a weak void* ref to the struct.
          auto weakVoidStructRefLE =
              hgmWeaks.weakInterfaceRefToWeakStructRef(
                  functionState, builder, virtualParamMT, virtualArgWeakRef);
          newVirtualArgLE = weakVoidStructRefLE.refLE;
          break;
        }
      }
      break;
    }
    default:
      assert(false);
  }
  return std::make_tuple(itablePtrLE, newVirtualArgLE);
}


Ref Mega::getUnknownSizeArrayLength(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    Ref arrayRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      return getUnknownSizeArrayLengthNormal(globalState, functionState, builder, usaRefMT, arrayRef);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (usaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
          return getUnknownSizeArrayLengthNormal(globalState, functionState, builder, usaRefMT, arrayRef);
        case Ownership::BORROW:
          return getUnknownSizeArrayLengthForce(globalState, functionState, builder, usaRefMT, arrayRef);
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

std::tuple<Reference*, LLVMValueRef> megaGetRefInnardsForChecking(Ref ref) {
  Reference* refM = ref.refM;
  LLVMValueRef refLE = ref.refLE;
  return std::make_tuple(refM, refLE);
}

LLVMValueRef Mega::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref ref) {
  Reference* actualRefM = nullptr;
  LLVMValueRef refLE = nullptr;
  std::tie(actualRefM, refLE) = megaGetRefInnardsForChecking(ref);
  assert(actualRefM == refM);

  assert(refLE != nullptr);
  assert(LLVMTypeOf(refLE) == functionState->defaultRegion->translateType(refM));
  if (globalState->opt->census) {
    if (refM->ownership == Ownership::OWN) {
      if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
        auto interfaceFatPtrLE = functionState->defaultRegion->makeInterfaceFatPtr(refM, refLE);
        auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
        buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
      }
      auto controlBlockPtrLE =
          getControlBlockPtr(globalState, functionState, builder, refLE, refM);
      buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE.refLE);
    } else if (refM->ownership == Ownership::SHARE) {
      if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
        auto interfaceFatPtrLE = functionState->defaultRegion->makeInterfaceFatPtr(refM, refLE);
        auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
        buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
      }
      if (refM->location == Location::INLINE) {
        // Nothing to do, there's no control block or ref counts or anything.
      } else if (refM->location == Location::YONDER) {
        auto controlBlockPtrLE =
            getControlBlockPtr(globalState, functionState, builder, refLE, refM);

        // We dont check ref count >0 because imm destructors receive with rc=0.
        //      auto rcLE = getRcFromControlBlockPtr(globalState, builder, controlBlockPtrLE);
        //      auto rcPositiveLE = LLVMBuildICmp(builder, LLVMIntSGT, rcLE, constI64LE(0), "");
        //      buildAssert(checkerAFL, globalState, functionState, blockState, builder, rcPositiveLE, "Invalid RC!");

        buildAssertCensusContains(checkerAFL, globalState, functionState, builder,
            controlBlockPtrLE.refLE);
      } else
        assert(false);
    } else {
      switch (globalState->opt->regionOverride) {
        case RegionOverride::ASSIST:
        case RegionOverride::NAIVE_RC:
        case RegionOverride::FAST: {
          if (refM->ownership == Ownership::BORROW) {
            if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
              auto interfaceFatPtrLE = functionState->defaultRegion->makeInterfaceFatPtr(refM, refLE);
              auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
              buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
            }
            auto controlBlockPtrLE = getControlBlockPtr(globalState, functionState, builder, refLE, refM);
            buildAssertCensusContains(checkerAFL, globalState, functionState, builder,
                controlBlockPtrLE.refLE);
          } else if (refM->ownership == Ownership::WEAK) {
            wrcWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
          } else
            assert(false);
          break;
        }
        case RegionOverride::RESILIENT_V0: {
          wrcWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
          break;
        }
        case RegionOverride::RESILIENT_V1: {
          lgtWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
          break;
        }
        case RegionOverride::RESILIENT_V2: {
          hgmWeaks.buildCheckWeakRef(checkerAFL, functionState, builder, refM, ref);
          break;
        }
        default:
          assert(false);
      }
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
          globalState->region->checkValidReference(FL(),
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
    case RegionOverride::RESILIENT_V2: {
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
      return hgmWeaks.discardWeakRef(from, functionState, builder, weakRefMT, weakRef);
    default:
      assert(false);
  }
}

Ref Mega::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST:
    case RegionOverride::RESILIENT_V0:
      return wrcWeaks.getIsAliveFromWeakRef(functionState, builder, weakRefM, weakRef);
    case RegionOverride::RESILIENT_V1:
      return lgtWeaks.getIsAliveFromWeakRef(functionState, builder, weakRefM, weakRef);
    case RegionOverride::RESILIENT_V2:
      return hgmWeaks.getIsAliveFromWeakRef(functionState, builder, weakRefM, weakRef);
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

  LLVMValueRef newControlBlockLE = LLVMGetUndef(globalState->region->getControlBlock(referendM)->getStruct());

  newControlBlockLE =
      fillControlBlockCensusFields(
          from, globalState, functionState, builder, referendM, newControlBlockLE, typeName);

  if (mutability == Mutability::IMMUTABLE) {
    newControlBlockLE =
        insertStrongRc(globalState, builder, referendM, newControlBlockLE);
  } else {
    switch (globalState->opt->regionOverride) {
      case RegionOverride::ASSIST:
      case RegionOverride::NAIVE_RC:
        newControlBlockLE =
            insertStrongRc(globalState, builder, referendM, newControlBlockLE);
        if (globalState->program->getReferendWeakability(referendM) == Weakability::WEAKABLE) {
          newControlBlockLE = wrcWeaks.fillWeakableControlBlock(functionState, builder, referendM, newControlBlockLE);
        }
        break;
      case RegionOverride::FAST:
        if (globalState->program->getReferendWeakability(referendM) == Weakability::WEAKABLE) {
          newControlBlockLE = wrcWeaks.fillWeakableControlBlock(functionState, builder, referendM, newControlBlockLE);
        }
        break;
      case RegionOverride::RESILIENT_V0:
        newControlBlockLE = wrcWeaks.fillWeakableControlBlock(functionState, builder, referendM, newControlBlockLE);
        break;
      case RegionOverride::RESILIENT_V1:
        newControlBlockLE = lgtWeaks.fillWeakableControlBlock(functionState, builder, referendM, newControlBlockLE);
        break;
      case RegionOverride::RESILIENT_V2:
        newControlBlockLE = hgmWeaks.fillWeakableControlBlock(functionState, builder, referendM, newControlBlockLE);
        break;
      default:
        assert(false);
    }
  }
  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE.refLE);
}

Ref Mega::loadElementFromKSAWithUpgrade(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    Ref indexRef,
    Reference* targetType) {
  Ref memberRef = loadElementFromKSAWithoutUpgrade(functionState, builder, ksaRefMT,
      ksaMT, arrayRef, indexRef);
  return upgradeLoadResultToRefWithTargetOwnership(
      functionState, builder, ksaMT->rawArray->elementType, targetType, memberRef);
}

Ref Mega::loadElementFromKSAWithoutUpgrade(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    Ref indexRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      return loadElementFromKSAWithoutUpgradeNormal(globalState, functionState, builder, ksaRefMT, ksaMT, arrayRef, indexRef);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (ksaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
          return loadElementFromKSAWithoutUpgradeNormal(globalState, functionState, builder, ksaRefMT, ksaMT, arrayRef, indexRef);
          break;
        case Ownership::BORROW:
          return loadElementFromKSAWithoutUpgradeForce(globalState, functionState, builder, ksaRefMT, ksaMT, arrayRef, indexRef);
          break;
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
        default:
          assert(false);
      }
      break;
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
    Ref indexRef,
    Reference* targetType) {
  Ref memberRef = loadElementFromUSAWithoutUpgrade(functionState, builder, usaRefMT,
      usaMT, arrayRef, indexRef);
  return upgradeLoadResultToRefWithTargetOwnership(
      functionState, builder, usaMT->rawArray->elementType, targetType, memberRef);
}

Ref Mega::loadElementFromUSAWithoutUpgrade(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    Ref indexRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      auto sizeRef = std::make_shared<Ref>(getUnknownSizeArrayLengthNormal(globalState, functionState, builder, usaRefMT, arrayRef));
      auto arrayElementsPtrLE =
          getUnknownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  usaRefMT,
                  globalState->region->checkValidReference(FL(), functionState, builder, usaRefMT, arrayRef)));
      return loadElementWithoutUpgrade(
          globalState, functionState, builder, usaRefMT,
          usaMT->rawArray->elementType,
          *sizeRef, arrayElementsPtrLE, usaMT->rawArray->mutability, indexRef);
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (usaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN: {
          auto sizeRef = std::make_shared<Ref>(
              getUnknownSizeArrayLengthNormal(globalState, functionState, builder, usaRefMT,
                  arrayRef));
          auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  usaRefMT,
                  globalState->region->checkValidReference(FL(), functionState, builder, usaRefMT,
                      arrayRef)));
          return loadElementWithoutUpgrade(
              globalState, functionState, builder, usaRefMT,
              usaMT->rawArray->elementType,
              *sizeRef, arrayElementsPtrLE, usaMT->rawArray->mutability, indexRef);
          break;
        }
        case Ownership::BORROW: {
          auto sizeRef = std::make_shared<Ref>(
              getUnknownSizeArrayLengthForce(globalState, functionState, builder, usaRefMT,
                  arrayRef));
          auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder,
              globalState->region->lockWeakRef(FL(), functionState, builder, usaRefMT, arrayRef));
          return loadElementWithoutUpgrade(
              globalState, functionState, builder, usaRefMT,
              usaMT->rawArray->elementType,
              *sizeRef, arrayElementsPtrLE, usaMT->rawArray->mutability, indexRef);
          break;
        }
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
        default:
          assert(false);
      }
      break;
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
    Ref indexRef,
    Ref elementRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      auto sizeRef = std::make_shared<Ref>(getUnknownSizeArrayLengthNormal(globalState, functionState, builder, usaRefMT, arrayRef));
      auto arrayElementsPtrLE =
          getUnknownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  usaRefMT,
                  globalState->region->checkValidReference(FL(), functionState, builder, usaRefMT, arrayRef)));
      return storeElement(
          globalState, functionState, builder, usaRefMT,
          usaMT->rawArray->elementType,
          *sizeRef, arrayElementsPtrLE, usaMT->rawArray->mutability, indexRef, elementRef);
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (usaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN: {
          auto sizeRef = std::make_shared<Ref>(
              getUnknownSizeArrayLengthNormal(globalState, functionState, builder, usaRefMT,
                  arrayRef));
          auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  usaRefMT,
                  globalState->region->checkValidReference(FL(), functionState, builder, usaRefMT,
                      arrayRef)));
          break;
        }
        case Ownership::BORROW: {
          auto sizeRef = std::make_shared<Ref>(
              getUnknownSizeArrayLengthForce(globalState, functionState, builder, usaRefMT,
                  arrayRef));
          auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder,
              globalState->region->lockWeakRef(FL(), functionState, builder, usaRefMT, arrayRef));

          return storeElement(
              globalState, functionState, builder, usaRefMT,
              usaMT->rawArray->elementType,
              *sizeRef, arrayElementsPtrLE, usaMT->rawArray->mutability, indexRef, elementRef);
        }
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
        default:
          assert(false);
      }
      break;
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
          auto sourceStructWrapperPtrLE =
              functionState->defaultRegion->makeWrapperPtr(
                  sourceStructMT,
                  globalState->region->checkValidReference(FL(),
                      functionState, builder, sourceStructMT, sourceRefLE));
          auto resultInterfaceFatPtrLE =
              upcastThinPtr(
                  globalState, functionState, builder, sourceStructMT, sourceStructReferendM,
                  sourceStructWrapperPtrLE, targetInterfaceTypeM, targetInterfaceReferendM);
          return wrap(functionState->defaultRegion, targetInterfaceTypeM, resultInterfaceFatPtrLE);
        }
        case Ownership::WEAK: {
          auto sourceWeakStructFatPtrLE =
              weakRefStructs.makeWeakFatPtr(
                  sourceStructMT,
                  globalState->region->checkValidReference(FL(),
                      functionState, builder, sourceStructMT, sourceRefLE));
          return functionState->defaultRegion->upcastWeak(
              functionState,
              builder,
              sourceWeakStructFatPtrLE,
              sourceStructReferendM,
              sourceStructMT,
              targetInterfaceReferendM,
              targetInterfaceTypeM);
        }
        default:
          assert(false);
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (sourceStructMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN: {
          auto sourceStructWrapperPtrLE =
              functionState->defaultRegion->makeWrapperPtr(
                  sourceStructMT,
                  globalState->region->checkValidReference(FL(),
                      functionState, builder, sourceStructMT, sourceRefLE));
          auto resultInterfaceFatPtrLE =
              upcastThinPtr(
                  globalState, functionState, builder, sourceStructMT, sourceStructReferendM,
                  sourceStructWrapperPtrLE, targetInterfaceTypeM, targetInterfaceReferendM);
          return wrap(functionState->defaultRegion, targetInterfaceTypeM, resultInterfaceFatPtrLE);
        }
        case Ownership::BORROW:
        case Ownership::WEAK: {
          auto sourceWeakStructFatPtrLE =
              weakRefStructs.makeWeakFatPtr(
                  sourceStructMT,
                  globalState->region->checkValidReference(FL(),
                      functionState, builder, sourceStructMT, sourceRefLE));
          return functionState->defaultRegion->upcastWeak(
              functionState,
              builder,
              sourceWeakStructFatPtrLE,
              sourceStructReferendM,
              sourceStructMT,
              targetInterfaceReferendM,
              targetInterfaceTypeM);
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
