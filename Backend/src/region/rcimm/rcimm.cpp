#include "../../function/expressions/shared/shared.h"
#include "../../utils/counters.h"
#include "../../utils/branch.h"
#include "../common/controlblock.h"
#include "../common/heap.h"
#include "../../function/expressions/shared/string.h"
#include "../common/common.h"
#include <sstream>
#include "../../function/expressions/shared/elements.h"
#include "rcimm.h"
#include "../../translatetype.h"
#include "../linear/linear.h"

enum UnserializeFunctionParameter {
  UNSERIALIZE_PARAM_VALE_REGION_INSTANCE_REF = 0,
  UNSERIALIZE_PARAM_HOST_REGION_INSTANCE_REF = 1,
  UNSERIALIZE_PARAM_HOST_OBJECT_REF = 2,
};

enum FreeFunctionParameter {
  FREE_PARAM_REGION_INSTANCE_REF = 0,
  FREE_PARAM_OBJECT_REF = 1,
};

void fillControlBlock(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* structs,
    LLVMBuilderRef builder,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName) {
  LLVMValueRef newControlBlockLE = LLVMGetUndef(structs->getControlBlock(kindM)->getStruct());
  newControlBlockLE =
      fillControlBlockCensusFields(
          from, globalState, functionState, structs, builder, kindM, newControlBlockLE, typeName);
  newControlBlockLE = insertStrongRc(globalState, builder, structs, kindM, newControlBlockLE);
  LLVMBuildStore(builder, newControlBlockLE, controlBlockPtrLE.refLE);
}

ControlBlock makeImmControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "immControlBlock"));
  controlBlock.addMember(ControlBlockMember::STRONG_RC_32B);
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

RCImm::RCImm(GlobalState* globalState_)
  : globalState(globalState_),
    kindStructs(globalState, makeImmControlBlock(globalState), makeImmControlBlock(globalState), LLVMStructCreateNamed(globalState->context, "immUnused")) {

  regionKind =
      globalState->metalCache->getStructKind(
          globalState->metalCache->getName(
              globalState->metalCache->builtinPackageCoord, namePrefix + "_Region"));
  regionRefMT =
      globalState->metalCache->getReference(
          Ownership::MUTABLE_BORROW, Location::YONDER, regionKind);
  globalState->regionIdByKind.emplace(regionKind, globalState->metalCache->rcImmRegionId);
  kindStructs.declareStruct(regionKind, Weakability::NON_WEAKABLE);
  kindStructs.defineStruct(regionKind, {
      // This region doesnt need anything
  });
}

Reference* RCImm::getRegionRefType() {
  return regionRefMT;
}

RegionId* RCImm::getRegionId() {
  return globalState->metalCache->rcImmRegionId;
}

Ref RCImm::makeRegionInstance(LLVMBuilderRef builder) {
  return toRef(this, regionRefMT, LLVMConstNull(translateType(regionRefMT)));
}

void RCImm::alias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    Ref ref) {
  auto sourceRnd = sourceRef->kind;

  if (dynamic_cast<Int *>(sourceRnd) ||
      dynamic_cast<Bool *>(sourceRnd) ||
      dynamic_cast<Float *>(sourceRnd) ||
      dynamic_cast<Void *>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (dynamic_cast<InterfaceKind *>(sourceRnd) ||
             dynamic_cast<StructKind *>(sourceRnd) ||
             dynamic_cast<StaticSizedArrayT *>(sourceRnd) ||
             dynamic_cast<RuntimeSizedArrayT *>(sourceRnd) ||
             dynamic_cast<Str *>(sourceRnd)) {
    if (sourceRef->location == Location::INLINE) {
      // Do nothing, we can just let inline structs disappear
    } else {
      if (sourceRef->ownership == Ownership::IMMUTABLE_SHARE) {
        // Do nothing, immutable yonders need no RC adjustments.
      } else if (sourceRef->ownership == Ownership::MUTABLE_SHARE) {
        adjustStrongRc(from, globalState, functionState, &kindStructs, builder, ref, sourceRef, 1);
      } else {
        { assert(false); throw 1337; }
      }
    }
  } else {
    std::cerr << "Unimplemented type in acquireReference: "
              << typeid(*sourceRef->kind).name() << std::endl;
    { assert(false); throw 1337; }
  }
}

void RCImm::dealias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  buildFlare(FL(), globalState, functionState, builder);
  discard(from, globalState, functionState, builder, sourceMT, sourceRef);
}

Ref RCImm::lockWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference* resultOptTypeM,
//      LLVMTypeRef resultOptTypeL,
    Reference* constraintRefM,
    Reference* sourceWeakRefMT,
    Ref sourceWeakRefLE,
    bool weakRefKnownLive,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {
  { assert(false); throw 1337; }
  exit(1);
}


Ref RCImm::asSubtype(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* resultOptTypeM,
    Reference* sourceInterfaceRefMT,
    Ref sourceInterfaceRef,
    bool sourceRefKnownLive,
    Kind* targetKind,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {
  return regularDowncast(
      globalState, functionState, builder, &kindStructs, resultOptTypeM,
      sourceInterfaceRefMT, sourceInterfaceRef, sourceRefKnownLive, targetKind, buildThen, buildElse);
}

LLVMValueRef RCImm::getCensusObjectId(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref ref) {
  if (refM->location == Location::INLINE) {
    return constI64LE(globalState, -1);
  } else if (refM == globalState->metalCache->i32Ref) {
    return constI64LE(globalState, -2);
  } else if (refM == globalState->metalCache->i64Ref) {
    return constI64LE(globalState, -3);
  } else if (refM == globalState->metalCache->boolRef) {
    return constI64LE(globalState, -4);
  } else if (refM == globalState->metalCache->neverRef) {
    return constI64LE(globalState, -5);
  } else if (refM == globalState->metalCache->floatRef) {
    return constI64LE(globalState, -6);
  } else {
    auto controlBlockPtrLE =
        kindStructs.getControlBlockPtr(checkerAFL, functionState, builder, ref, refM);
    auto exprLE =
        kindStructs.getObjIdFromControlBlockPtr(builder, refM->kind, controlBlockPtrLE);
    return exprLE;
  }
}

Ref RCImm::upcastWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructKind* sourceStructKindM,
    Reference* sourceStructTypeM,
    InterfaceKind* targetInterfaceKindM,
    Reference* targetInterfaceTypeM) {
  { assert(false); throw 1337; }
  exit(1);
}

void RCImm::declareStruct(
    StructDefinition* structM) {
  globalState->regionIdByKind.emplace(structM->kind, getRegionId());

  kindStructs.declareStruct(structM->kind, structM->weakability);
}

void RCImm::declareStructExtraFunctions(StructDefinition* structDefM) {
  declareConcreteUnserializeFunction(structDefM->kind);
  declareConcreteFreeFunction(structDefM->kind);
}

void RCImm::defineStruct(
    StructDefinition* structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        globalState->getRegion(structM->members[i]->type)
            ->translateType(structM->members[i]->type));
  }
  kindStructs.defineStruct(structM->kind, innerStructMemberTypesL);
}

void RCImm::defineStructExtraFunctions(StructDefinition* structDefM) {
  defineConcreteUnserializeFunction(structDefM->kind);
  defineConcreteFreeFunction(structDefM->kind);
}

void RCImm::declareStaticSizedArray(
    StaticSizedArrayDefinitionT* ssaDefM) {
  globalState->regionIdByKind.emplace(ssaDefM->kind, getRegionId());

  kindStructs.declareStaticSizedArray(ssaDefM->kind, Weakability::NON_WEAKABLE);
}

void RCImm::declareStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* ssaDef) {
  declareConcreteUnserializeFunction(ssaDef->kind);
  declareConcreteFreeFunction(ssaDef->kind);
}

void RCImm::defineStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT) {
  auto elementLT =
      translateType(
          staticSizedArrayMT->elementType);
  kindStructs.defineStaticSizedArray(staticSizedArrayMT, elementLT);
}

void RCImm::defineStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* ssaDef) {
  defineConcreteUnserializeFunction(ssaDef->kind);
  defineConcreteFreeFunction(ssaDef->kind);
}

void RCImm::declareRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* rsaDefM) {
  globalState->regionIdByKind.emplace(rsaDefM->kind, getRegionId());

  kindStructs.declareRuntimeSizedArray(rsaDefM->kind, Weakability::NON_WEAKABLE);
}

void RCImm::declareRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* rsaDefM) {
  declareConcreteUnserializeFunction(rsaDefM->kind);
  declareConcreteFreeFunction(rsaDefM->kind);
}

void RCImm::defineRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) {
  auto elementLT =
      translateType(
          runtimeSizedArrayMT->elementType);
  kindStructs.defineRuntimeSizedArray(runtimeSizedArrayMT, elementLT, false);
}

void RCImm::defineRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* rsaDefM) {
  defineConcreteUnserializeFunction(rsaDefM->kind);
  defineConcreteFreeFunction(rsaDefM->kind);
}

void RCImm::declareInterface(
    InterfaceDefinition* interfaceM) {
  globalState->regionIdByKind.emplace(interfaceM->kind, getRegionId());

  kindStructs.declareInterface(interfaceM->kind, interfaceM->weakability);
}

void RCImm::declareInterfaceExtraFunctions(InterfaceDefinition* interfaceDefM) {
  declareInterfaceUnserializeFunction(interfaceDefM->kind);
  declareInterfaceFreeFunction(interfaceDefM->kind);
}

void RCImm::defineInterface(
    InterfaceDefinition* interfaceM) {
  auto interfaceMethodTypesL = globalState->getInterfaceFunctionPointerTypes(interfaceM->kind);
  kindStructs.defineInterface(interfaceM, interfaceMethodTypesL);
}

void RCImm::defineInterfaceExtraFunctions(InterfaceDefinition* interfaceDefM) {
}

void RCImm::declareEdge(Edge* edge) {
  kindStructs.declareEdge(edge);

  {
    auto hostStructMT = globalState->linearRegion->linearizeStructKind(edge->structName);
    auto hostInterfaceMT = globalState->linearRegion->linearizeInterfaceKind(edge->interfaceName);

    auto interfaceUnserializeMethod = getUnserializeInterfaceMethod(edge->interfaceName);
    auto unserializeThunkPrototype = getUnserializeThunkPrototype(edge->structName, edge->interfaceName);
    globalState->addEdgeExtraMethod(hostInterfaceMT, hostStructMT, interfaceUnserializeMethod, unserializeThunkPrototype);
    auto unserializeNameL = globalState->unserializeName->name + "__" + edge->interfaceName->fullName->name + "__" + edge->structName->fullName->name;
    declareExtraFunction(globalState, unserializeThunkPrototype, unserializeNameL);
  }

  auto interfaceFreeMethod = getFreeInterfaceMethod(edge->interfaceName);
  auto freeThunkPrototype = getFreeThunkPrototype(edge->structName, edge->interfaceName);
  globalState->addEdgeExtraMethod(edge->interfaceName, edge->structName, interfaceFreeMethod, freeThunkPrototype);
  auto freeNameL = globalState->freeName->name + "__" + edge->interfaceName->fullName->name + "__" + edge->structName->fullName->name;
  declareExtraFunction(globalState, freeThunkPrototype, freeNameL);
}

void RCImm::defineEdge(Edge* edge) {
  auto interfaceM = globalState->program->getInterface(edge->interfaceName);

  auto interfaceFunctionsLT = globalState->getInterfaceFunctionPointerTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  kindStructs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);

  defineEdgeUnserializeFunction(edge);
  defineEdgeFreeFunction(edge);
}

Ref RCImm::weakAlias(
    FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) {
  { assert(false); throw 1337; }
  exit(1);
}

void RCImm::discardOwningRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    LiveRef sourceRef) {
  { assert(false); throw 1337; }
}


void RCImm::noteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtrLE) {
  // Do nothing
}

Ref RCImm::loadMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* structRefMT,
    LiveRef structRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetMemberType,
    const std::string& memberName) {
  auto memberLE =
      loadMember2(
          functionState, builder, regionInstanceRef, structRefMT, structRef, memberIndex, expectedMemberType,
          targetMemberType, memberName);
  auto resultRef =
      upgradeLoadResultToRefWithTargetOwnership(
          functionState, builder, regionInstanceRef, expectedMemberType, targetMemberType, memberLE, false);
  return resultRef;
}

void RCImm::storeMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* structRefMT,
    LiveRef structRef,
    int memberIndex,
    const std::string& memberName,
    Reference* newMemberRefMT,
    Ref newMemberRef) {
  { assert(false); throw 1337; }
}

std::tuple<LLVMValueRef, LLVMValueRef> RCImm::explodeInterfaceRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef) {
  return explodeStrongInterfaceRef(
      globalState, functionState, builder, &kindStructs, virtualParamMT, virtualArgRef);
}


void RCImm::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  { assert(false); throw 1337; }
}

void RCImm::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  { assert(false); throw 1337; }
}

Ref RCImm::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef,
    bool knownLive) {
  { assert(false); throw 1337; }
  exit(1);
}

LLVMValueRef RCImm::getStringBytesPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref regionInstanceRef,
    LiveRef ref) {
  assert(refMT->kind == globalState->metalCache->str);
  auto strWrapperPtrLE =
      toWrapperPtr(functionState, builder, &kindStructs, refMT, ref);
  return kindStructs.getStringBytesPtr(functionState, builder, strWrapperPtrLE);
}

Ref RCImm::allocate(
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
                FL(), globalState, functionState, &kindStructs, innerBuilder, desiredReference->kind,
                controlBlockPtrLE, structM->name->name);
          });
  // Dont need to alias here because the RC starts at 1, see SRCAO
  return resultRef;
}

Ref RCImm::upcast(
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructMT,
    StructKind* sourceStructKindM,
    Ref sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceKind* targetInterfaceKindM) {
  return upcastStrong(globalState, functionState, builder, &kindStructs, sourceStructMT, sourceStructKindM, sourceRefLE, targetInterfaceTypeM, targetInterfaceKindM);
}

WrapperPtrLE RCImm::lockWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref weakRefLE,
    bool weakRefKnownLive) {
  { assert(false); throw 1337; }
  exit(1);
}

LiveRef RCImm::constructStaticSizedArray(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    StaticSizedArrayT* kindM) {
  auto resultRef =
      ::constructStaticSizedArray(
          globalState, functionState, builder, referenceM, kindM, &kindStructs,
          [this, functionState, referenceM, kindM](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
//            fillControlBlock(
//                FL(),
//                functionState,
//                innerBuilder,
//                referenceM->kind,
//                kindM->mutability,
//                controlBlockPtrLE,
//                kindM->name->name);
            fillControlBlock(
                FL(), globalState, functionState, &kindStructs, innerBuilder, kindM, controlBlockPtrLE,
                kindM->name->name);
          });
  // Dont need to alias here because the RC starts at 1, see SRCAO
  return resultRef;
}

Ref RCImm::getRuntimeSizedArrayLength(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* rsaRefMT,
    LiveRef arrayRef) {
  return getRuntimeSizedArrayLengthStrong(globalState, functionState, builder, &kindStructs, rsaRefMT, arrayRef);
}

Ref RCImm::getRuntimeSizedArrayCapacity(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* rsaRefMT,
    LiveRef arrayRef) {
  return getRuntimeSizedArrayCapacityStrong(globalState, functionState, builder, &kindStructs, rsaRefMT, arrayRef);
}

LLVMValueRef RCImm::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool expectLive,
    Reference* refM,
    Ref ref) {
    //buildFlare(FL(), globalState, functionState, builder);
  Reference *actualRefM = nullptr;
  LLVMValueRef refLE = nullptr;
  //buildFlare(FL(), globalState, functionState, builder);
  std::tie(actualRefM, refLE) = megaGetRefInnardsForChecking(ref);
  assert(actualRefM == refM);
  assert(refLE != nullptr);
  //buildFlare(FL(), globalState, functionState, builder);
  assert(LLVMTypeOf(refLE) == globalState->getRegion(refM)->translateType(refM));

  if (globalState->opt->census) {
    checkValidReference(checkerAFL, functionState, builder, &kindStructs, refM, refLE);
  }
  return refLE;
}

Ref RCImm::upgradeLoadResultToRefWithTargetOwnership(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* sourceType,
    Reference* targetType,
    LoadResult sourceLoad,
    bool resultKnownLive) {
  auto sourceRef = sourceLoad.extractForAliasingInternals();
  auto sourceOwnership = sourceType->ownership;
  auto sourceLocation = sourceType->location;
  auto targetOwnership = targetType->ownership;
  auto targetLocation = targetType->location;
//  assert(sourceLocation == targetLocation); // unimplemented

  if (sourceLocation == Location::INLINE) {
    return sourceRef;
  } else {
    return transmutePtr(globalState, functionState, builder, true, sourceType, targetType, sourceRef);
  }
}

void RCImm::checkInlineStructType(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  auto argLE = checkValidReference(FL(), functionState, builder, false, refMT, ref);
  auto structKind = dynamic_cast<StructKind*>(refMT->kind);
  assert(structKind);
  assert(LLVMTypeOf(argLE) == kindStructs.getStructInnerStruct(structKind));
}

LoadResult RCImm::loadElementFromSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    LiveRef arrayRef,
    InBoundsLE indexLE) {
  auto ssaDef = globalState->program->getStaticSizedArray(ssaMT);
  return regularloadElementFromSSA(
      globalState, functionState, builder, ssaRefMT, ssaDef->elementType, arrayRef, indexLE, &kindStructs);
}

LoadResult RCImm::loadElementFromRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    LiveRef arrayRef,
    InBoundsLE indexInBoundsLE) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  return regularLoadElementFromRSAWithoutUpgrade(
      globalState,
      functionState,
      builder,
      &kindStructs,
      false,
      rsaRefMT,
      rsaDef->elementType,
      arrayRef,
      indexInBoundsLE);
}

Ref RCImm::popRuntimeSizedArrayNoBoundsCheck(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref arrayRegionInstanceRef,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    LiveRef arrayRef,
    InBoundsLE indexInBoundsLE) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  return regularLoadElementFromRSAWithoutUpgrade(
      globalState, functionState, builder, &kindStructs, false, rsaRefMT, rsaDef->elementType, arrayRef,
      indexInBoundsLE).move();
}


Ref RCImm::storeElementInRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    LiveRef arrayRef,
    InBoundsLE indexInBoundsLE,
    Ref elementRef) {
  { assert(false); throw 1337; }
  exit(1);
}

void RCImm::pushRuntimeSizedArrayNoBoundsCheck(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference *rsaRefMT,
    RuntimeSizedArrayT *rsaMT,
    LiveRef rsaRef,
    InBoundsLE indexInBoundsLE,
    Ref elementRef) {
  auto elementType = globalState->program->getRuntimeSizedArray(rsaMT)->elementType;
  buildFlare(FL(), globalState, functionState, builder);

  auto arrayWrapperPtrLE = toWrapperPtr(functionState, builder, &kindStructs, rsaRefMT, rsaRef);
  auto arrayElementsPtrLE = getRuntimeSizedArrayContentsPtr(builder, false, arrayWrapperPtrLE);
  // We don't increment the size because it's populated when we first create the array.
//  auto incrementedSize =
//      incrementRSASize(globalState, functionState, builder, rsaRefMT, arrayWrapperPtrLE);

  ::initializeElementWithoutIncrementSize(
      globalState, functionState, builder, rsaRefMT->location,
      elementType, arrayElementsPtrLE, indexInBoundsLE, elementRef,
      // We dont need to increment the size, so manually create this reminder object
      IncrementedSize{});
}

void RCImm::deallocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    LiveRef ref) {
  buildFlare(FL(), globalState, functionState, builder);
  { assert(false); throw 1337; } // Outside shouldnt be able to deallocate anything of ours.
  // We deallocate things ourselves when we discard references, via discard.
  // We call innerDeallocate directly.
}


LiveRef RCImm::constructRuntimeSizedArray(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaMT,
    RuntimeSizedArrayT* runtimeSizedArrayT,
    Ref capacityRef,
    const std::string& typeName) {
  auto rsaWrapperPtrLT =
      kindStructs.getRuntimeSizedArrayWrapperStruct(runtimeSizedArrayT);
  auto rsaDef = globalState->program->getRuntimeSizedArray(runtimeSizedArrayT);
  auto elementType = globalState->program->getRuntimeSizedArray(runtimeSizedArrayT)->elementType;
  auto rsaElementLT = globalState->getRegion(elementType)->translateType(elementType);
  buildFlare(FL(), globalState, functionState, builder);
  auto resultRef =
      ::constructRuntimeSizedArray(
          globalState, functionState, builder, &kindStructs, rsaMT, rsaDef->elementType, runtimeSizedArrayT,
          rsaWrapperPtrLT, rsaElementLT,
          // Note we're handing in capacity for the size ref. Because of this, we dont later increment the size
          // when we push elements.
          capacityRef, capacityRef,
          false, typeName,
          [this, functionState, runtimeSizedArrayT, rsaMT, typeName](
              LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(), globalState, functionState, &kindStructs, innerBuilder, runtimeSizedArrayT, controlBlockPtrLE,
                typeName);
          });
  buildFlare(FL(), globalState, functionState, builder);
  // Dont need to alias here because the RC starts at 1, see SRCAO
  return resultRef;
}


Ref RCImm::mallocStr(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE,
    LLVMValueRef sourceCharsPtrLE) {
  auto resultRef =
      toRef(this, globalState->metalCache->mutStrRef, ::mallocStr(
          globalState, functionState, builder, lengthLE, sourceCharsPtrLE, &kindStructs,
          [this, functionState](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
//            fillControlBlock(
//                FL(), functionState, innerBuilder, globalState->metalCache->str,
//                Mutability::IMMUTABLE, controlBlockPtrLE, "Str");
            fillControlBlock(
                FL(), globalState, functionState, &kindStructs, innerBuilder, globalState->metalCache->str, controlBlockPtrLE,
                "str");
          }));
  // Dont need to alias here because the RC starts at 1, see SRCAO
  return resultRef;
}

LLVMValueRef RCImm::getStringLen(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref regionInstanceRef,
    LiveRef ref) {
  assert(refMT->kind == globalState->metalCache->str);
  auto strWrapperPtrLE = toWrapperPtr(functionState, builder, &kindStructs, refMT, ref);
  return kindStructs.getStringLen(functionState, builder, strWrapperPtrLE);
}

void RCImm::discard(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  buildFlare(FL(), globalState, functionState, builder);
  auto sourceRnd = sourceMT->kind;

  buildFlare(FL(), globalState, functionState, builder, typeid(*sourceRnd).name());

  if (dynamic_cast<Int *>(sourceRnd) ||
      dynamic_cast<Bool *>(sourceRnd) ||
      dynamic_cast<Float *>(sourceRnd)) {
    buildFlare(FL(), globalState, functionState, builder);
    // Do nothing for these, they're always inlined and copied.
  } else if (
      dynamic_cast<Str *>(sourceRnd) ||
      dynamic_cast<InterfaceKind *>(sourceRnd) ||
      dynamic_cast<StructKind *>(sourceRnd) ||
      dynamic_cast<StaticSizedArrayT *>(sourceRnd) ||
      dynamic_cast<RuntimeSizedArrayT *>(sourceRnd)) {
    buildFlare(FL(), globalState, functionState, builder);
    if (auto sr = dynamic_cast<StructKind *>(sourceRnd)) {
      buildFlare(FL(), globalState, functionState, builder, sr->fullName->name);
    }
    assert(sourceMT->ownership == Ownership::MUTABLE_SHARE || sourceMT->ownership == Ownership::IMMUTABLE_SHARE);
    if (sourceMT->location == Location::INLINE) {
      buildFlare(FL(), globalState, functionState, builder);
      // Do nothing, we can just let inline structs disappear
    } else {
      if (sourceMT->ownership == Ownership::IMMUTABLE_SHARE) {
        // Do nothing, immutable yonders need no RC adjustments.
      } else if (sourceMT->ownership == Ownership::MUTABLE_SHARE) {
        buildFlare(FL(), globalState, functionState, builder);
        auto rcLE =
            adjustStrongRc(
                from, globalState, functionState, &kindStructs, builder, sourceRef, sourceMT, -1);
        buildFlare(FL(), globalState, functionState, builder, rcLE);
        buildIfV(
            globalState, functionState,
            builder,
            isZeroLE(builder, rcLE),
            [this, from, globalState, functionState, sourceRef, sourceMT](
                LLVMBuilderRef thenBuilder) {
              buildFlare(FL(), globalState, functionState, thenBuilder);
              auto regionInstanceRef = makeRegionInstance(thenBuilder);
              callFree(functionState, thenBuilder, regionInstanceRef, sourceMT->kind, sourceRef);
              //  auto immDestructor = getFreePrototype(sourceMT->kind);
              ////      globalState->program->getImmDestructor(sourceMT->kind);
              //  auto funcL = globalState->getFunction(immDestructor);
              //
              //  auto sourceLE =
              //      globalState->getRegion(sourceMT)->checkValidReference(FL(),
              //          functionState, thenBuilder, true, sourceMT, sourceRef);
              //  std::vector<LLVMValueRef> argExprsL = {sourceLE};
              //  return unmigratedLLVMBuildCall(thenBuilder, funcL, argExprsL.data(), argExprsL.size(), "");
            });
      } else {
        { assert(false); throw 1337; }
      }
    }
  } else {
    std::cerr << "Unimplemented type in discard: "
        << typeid(*sourceMT->kind).name() << std::endl;
    { assert(false); throw 1337; }
  }
  buildFlare(FL(), globalState, functionState, builder);
}


LLVMTypeRef RCImm::translateType(Reference* referenceM) {
  if (primitives.isPrimitive(referenceM)) {
    return primitives.translatePrimitive(globalState, referenceM);
  } else if (referenceM == regionRefMT) {
    // We just have a raw pointer to region structs
    return LLVMPointerType(kindStructs.getStructInnerStruct(regionKind), 0);
  } else {
    if (dynamic_cast<Str *>(referenceM->kind) != nullptr) {
      assert(referenceM->location != Location::INLINE);
      assert(referenceM->ownership == Ownership::MUTABLE_SHARE || referenceM->ownership == Ownership::IMMUTABLE_SHARE);
      return LLVMPointerType(kindStructs.getStringWrapperStruct(), 0);
    } else if (auto staticSizedArrayMT = dynamic_cast<StaticSizedArrayT *>(referenceM->kind)) {
      assert(referenceM->location != Location::INLINE);
      auto staticSizedArrayCountedStructLT = kindStructs.getStaticSizedArrayWrapperStruct(staticSizedArrayMT);
      return LLVMPointerType(staticSizedArrayCountedStructLT, 0);
    } else if (auto runtimeSizedArrayMT =
        dynamic_cast<RuntimeSizedArrayT *>(referenceM->kind)) {
      assert(referenceM->location != Location::INLINE);
      auto runtimeSizedArrayCountedStructLT =
          kindStructs.getRuntimeSizedArrayWrapperStruct(runtimeSizedArrayMT);
      return LLVMPointerType(runtimeSizedArrayCountedStructLT, 0);
    } else if (auto structKind =
        dynamic_cast<StructKind *>(referenceM->kind)) {
      if (referenceM->location == Location::INLINE) {
        auto innerStructL = kindStructs.getStructInnerStruct(structKind);
        return innerStructL;
      } else {
        auto countedStructL = kindStructs.getStructWrapperStruct(structKind);
        return LLVMPointerType(countedStructL, 0);
      }
    } else if (auto interfaceKind =
        dynamic_cast<InterfaceKind *>(referenceM->kind)) {
      assert(referenceM->location != Location::INLINE);
      auto interfaceRefStructL =
          kindStructs.getInterfaceRefStruct(interfaceKind);
      return interfaceRefStructL;
    } else if (dynamic_cast<Never*>(referenceM->kind)) {
      auto result = LLVMPointerType(makeNeverType(globalState), 0);
      assert(LLVMTypeOf(globalState->neverPtrLE) == result);
      return result;
    } else {
      std::cerr << "Unimplemented type: " << typeid(*referenceM->kind).name() << std::endl;
      { assert(false); throw 1337; }
      return nullptr;
    }
  }
}


//LLVMTypeRef RCImm::getControlBlockStruct(Kind* kind) {
//  if (auto structKind = dynamic_cast<StructKind*>(kind)) {
//    auto structM = globalState->program->getStruct(structKind);
//    assert(structM->mutability == Mutability::IMMUTABLE);
//  } else if (auto interfaceKind = dynamic_cast<InterfaceKind*>(kind)) {
//    auto interfaceM = globalState->program->getInterface(interfaceKind);
//    assert(interfaceM->mutability == Mutability::IMMUTABLE);
//  } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(kind)) {
//    auto ssaDef = globalState->program->getStaticSizedArray(ssaMT);
//    assert(ssaDef->mutability == Mutability::IMMUTABLE);
//  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(kind)) {
//    auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
//    assert(rsaDef->mutability == Mutability::IMMUTABLE);
//  } else if (auto strMT = dynamic_cast<Str*>(kind)) {
//  } else {
//    { assert(false); throw 1337; }
//  }
//  return kindStructs.getControlBlockStruct();
//}


LoadResult RCImm::loadMember2(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* structRefMT,
    LiveRef structLiveRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {
  if (structRefMT->location == Location::INLINE) {
    auto structRef = toRef(globalState, structRefMT, structLiveRef);
    auto innerStructLE =
        globalState->getRegion(structRefMT)->checkValidReference(
            FL(), functionState, builder, false, structRefMT, structRef);
    auto memberLE =
        LLVMBuildExtractValue(builder, innerStructLE, memberIndex, memberName.c_str());
    return LoadResult{toRef(globalState->getRegion(expectedMemberType), expectedMemberType, memberLE)};
  } else {
    return regularLoadStrongMember(globalState, functionState, builder, &kindStructs, structRefMT, structLiveRef, memberIndex, expectedMemberType, targetType, memberName);
  }
}

void RCImm::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* refM,
    LLVMValueRef refLE) {
  if (refM == regionRefMT) {
    // Region ref has no control block.
  } else {
    regularCheckValidReference(checkerAFL, globalState, functionState, builder, kindStructs, refM, refLE);
  }
}

//std::string RCImm::getMemberArbitraryRefNameCSeeMMEDT(Reference* sourceMT) {
//  { assert(false); throw 1337; }
//  exit(1);
//}

std::string RCImm::generateStructDefsC(
    Package* currentPackage,

    StructDefinition* structDefM) {
  { assert(false); throw 1337; }
  return "";
}

std::string RCImm::generateInterfaceDefsC(
    Package* currentPackage, InterfaceDefinition* interfaceDefM) {
  { assert(false); throw 1337; }
  return "";
}

std::string RCImm::generateRuntimeSizedArrayDefsC(
    Package* currentPackage,
    RuntimeSizedArrayDefinitionT* rsaDefM) {
  if (rsaDefM->mutability == Mutability::IMMUTABLE) {
    { assert(false); throw 1337; }
  } else {
    auto name = currentPackage->getKindExportName(rsaDefM->kind, true);
    return std::string() + "typedef struct " + name + " { void* unused; } " + name + ";\n";
  }
}

std::string RCImm::generateStaticSizedArrayDefsC(
    Package* currentPackage,
    StaticSizedArrayDefinitionT* ssaDefM) {
  if (ssaDefM->mutability == Mutability::IMMUTABLE) {
    { assert(false); throw 1337; }
  } else {
    auto name = currentPackage->getKindExportName(ssaDefM->kind, true);
    return std::string() + "typedef struct " + name + " { void* unused; } " + name + ";\n";
  }
}

LLVMTypeRef RCImm::getExternalType(Reference* refMT) {
  // Instance regions (unlike this one) return their handle types from this method.
  // For this region though, we don't give out handles, we give out copies.
  return globalState->linearRegion->translateType(
      globalState->linearRegion->linearizeReference(refMT, true));
}


std::pair<Ref, Ref> RCImm::receiveUnencryptedAlienReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sourceRegionInstanceRef,
    Ref targetRegionInstanceRef,
    Reference* hostRefMT,
    Reference* valeRefMT,
    Ref sourceRef) {
  buildFlare(FL(), globalState, functionState, builder);

  assert(hostRefMT->ownership == Ownership::MUTABLE_SHARE || hostRefMT->ownership == Ownership::IMMUTABLE_SHARE);

  auto sourceRegion = globalState->getRegion(hostRefMT);
  assert(sourceRegion == globalState->linearRegion);
  auto sourceRefLE = sourceRegion->checkValidReference(FL(), functionState, builder, true, hostRefMT, sourceRef);

  if (dynamic_cast<Void*>(hostRefMT->kind)) {
    auto resultRef = toRef(globalState->getRegion(valeRefMT), valeRefMT, makeVoid(globalState));
    // Vale doesn't care about the size, only extern (linear) does, so just return zero.
    return std::make_pair(resultRef, globalState->constI32(0));
  } else if (dynamic_cast<Int*>(hostRefMT->kind)) {
    auto resultRef = toRef(globalState->getRegion(hostRefMT), valeRefMT, sourceRefLE);
    // Vale doesn't care about the size, only extern (linear) does, so just return zero.
    return std::make_pair(resultRef, globalState->constI32(0));
  } else if (dynamic_cast<Bool*>(hostRefMT->kind)) {
    auto asI1LE =
        LLVMBuildTrunc(
            builder, sourceRefLE, LLVMInt1TypeInContext(globalState->context), "boolAsI1");
    auto resultRef = toRef(this, valeRefMT, asI1LE);
    // Vale doesn't care about the size, only extern (linear) does, so just return zero.
    return std::make_pair(resultRef, globalState->constI32(0));
  } else if (dynamic_cast<Float*>(hostRefMT->kind)) {
    auto resultRef = toRef(globalState->getRegion(hostRefMT), valeRefMT, sourceRefLE);
    // Vale doesn't care about the size, only extern (linear) does, so just return zero.
    return std::make_pair(resultRef, globalState->constI32(0));
  } else if (dynamic_cast<Str*>(hostRefMT->kind) ||
             dynamic_cast<StructKind*>(hostRefMT->kind) ||
             dynamic_cast<InterfaceKind*>(hostRefMT->kind) ||
             dynamic_cast<StaticSizedArrayT*>(hostRefMT->kind) ||
             dynamic_cast<RuntimeSizedArrayT*>(hostRefMT->kind)) {
    buildFlare(FL(), globalState, functionState, builder);
    if (hostRefMT->location == Location::INLINE) {
      if (hostRefMT == globalState->metalCache->voidRef) {
        auto emptyTupleRefMT =
            globalState->linearRegion->unlinearizeReference(globalState->metalCache->voidRef, true);
        auto resultRef = toRef(this, emptyTupleRefMT, LLVMGetUndef(translateType(emptyTupleRefMT)));
        // Vale doesn't care about the size, only extern (linear) does, so just return zero.
        return std::make_pair(resultRef, globalState->constI32(0));
      } else {
        { assert(false); throw 1337; }
      }
    } else {
      auto resultRef = topLevelUnserialize(functionState, builder, targetRegionInstanceRef, sourceRegionInstanceRef, valeRefMT->kind, sourceRef);
      // Vale doesn't care about the size, only extern (linear) does, so just return zero.
      return std::make_pair(resultRef, globalState->constI32(0));
    }
  } else { assert(false); throw 1337; }

  { assert(false); throw 1337; }
}

LLVMTypeRef RCImm::getInterfaceMethodVirtualParamAnyType(Reference* reference) {
  return LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
}

Ref RCImm::receiveAndDecryptFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    LLVMValueRef sourceRefLE) {
  { assert(false); throw 1337; }
  exit(1);
}

LLVMValueRef RCImm::encryptAndSendFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Ref sourceRef) {
  { assert(false); throw 1337; }
  exit(1);
}

void RCImm::initializeElementInSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    LiveRef ssaRef,
    InBoundsLE indexInBoundsLE,
    Ref elementRef) {
  auto ssaDefM = globalState->program->getStaticSizedArray(ssaMT);
  auto elementType = ssaDefM->elementType;
  buildFlare(FL(), globalState, functionState, builder);
  regularInitializeElementInSSA(
      globalState, functionState, builder, &kindStructs, ssaRefMT,
      elementType, ssaRef, indexInBoundsLE, elementRef);
}

Ref RCImm::deinitializeElementFromSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    LiveRef arrayRef,
    InBoundsLE indexInBoundsLE) {
  { assert(false); throw 1337; }
  exit(1);
}

Weakability RCImm::getKindWeakability(Kind* kind) {
  return Weakability::NON_WEAKABLE;
}

void RCImm::declareExtraFunctions() {
  auto valeStrMT = globalState->metalCache->mutStrRef;
  auto hostStrMT =
      globalState->metalCache->getReference(
          Ownership::MUTABLE_SHARE,
          Location::YONDER,
          globalState->metalCache->getStr(globalState->metalCache->linearRegionId));

  auto unserializePrototype =
      globalState->metalCache->getPrototype(
          globalState->unserializeName, valeStrMT,
          {getRegionRefType(), globalState->linearRegion->getRegionRefType(), hostStrMT});
  auto unserializeNameL = globalState->unserializeName->name + "__str";
  declareExtraFunction(globalState, unserializePrototype, unserializeNameL);

  auto freePrototype =
      globalState->metalCache->getPrototype(
          globalState->freeName, globalState->metalCache->voidRef,
          {getRegionRefType(), valeStrMT});
  auto freeNameL = globalState->freeName->name + "__str";
  declareExtraFunction(globalState, freePrototype, freeNameL);
}

void RCImm::defineExtraFunctions() {
  defineConcreteUnserializeFunction(globalState->metalCache->str);
  defineConcreteFreeFunction(globalState->metalCache->str);
}

void RCImm::defineEdgeUnserializeFunction(Edge* edge) {
  auto boolMT = globalState->metalCache->boolRef;

  auto thunkPrototype = getUnserializeThunkPrototype(edge->structName, edge->interfaceName);
  defineFunctionBodyV(
      globalState, thunkPrototype,
      [&](FunctionState *functionState, LLVMBuilderRef builder) {
        auto structPrototype = getUnserializePrototype(edge->structName);

        auto hostObjectRefMT = structPrototype->params[2];
        auto valeObjectRefMT = structPrototype->returnType;

        auto hostRegion = globalState->getRegion(hostObjectRefMT);

        auto regionInstanceRef =
            toRef(this, regionRefMT, functionState->getParam(UserArgIndex{UNSERIALIZE_PARAM_VALE_REGION_INSTANCE_REF}));
        auto hostRegionInstanceRef =
            toRef(hostRegion, hostRegion->getRegionRefType(), functionState->getParam(UserArgIndex{UNSERIALIZE_PARAM_HOST_REGION_INSTANCE_REF}));
        auto hostObjectRef =
            toRef(globalState->getRegion(hostObjectRefMT), hostObjectRefMT, functionState->getParam(UserArgIndex{UNSERIALIZE_PARAM_HOST_OBJECT_REF}));

        auto valeStructRef =
            buildCallV(
                globalState, functionState, builder, structPrototype,
                {regionInstanceRef, hostRegionInstanceRef, hostObjectRef});

        auto valeInterfaceKind = dynamic_cast<InterfaceKind *>(thunkPrototype->returnType->kind);
        assert(valeInterfaceKind);
        auto valeStructKind = dynamic_cast<StructKind *>(structPrototype->returnType->kind);
        assert(valeStructKind);

        auto interfaceRef =
            upcast(
                functionState, builder, structPrototype->returnType, valeStructKind,
                valeStructRef, thunkPrototype->returnType, valeInterfaceKind);
        auto interfaceRefLE =
            checkValidReference(FL(), functionState, builder, true, thunkPrototype->returnType, interfaceRef);
        LLVMBuildRet(builder, interfaceRefLE);
      });
}

void RCImm::declareInterfaceUnserializeFunction(InterfaceKind* valeInterface) {
  auto hostKind = globalState->linearRegion->linearizeKind(valeInterface);
  auto hostInterface = dynamic_cast<InterfaceKind*>(hostKind);
  auto interfaceMethod = getUnserializeInterfaceMethod(valeInterface);
  globalState->addInterfaceExtraMethod(hostInterface, interfaceMethod);
}

Ref RCImm::topLevelUnserialize(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Ref sourceRegionInstanceRef,
    Kind* valeKind,
    Ref ref) {
  return callUnserialize(functionState, builder, regionInstanceRef, sourceRegionInstanceRef, valeKind, ref);
}

InterfaceMethod* RCImm::getUnserializeInterfaceMethod(Kind* valeKind) {
  return globalState->metalCache->getInterfaceMethod(
      getUnserializePrototype(valeKind), 2);
}

Ref RCImm::callUnserialize(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Ref sourceRegionInstanceRef,
    Kind* valeKind,
    Ref objectRef) {
  auto prototype = getUnserializePrototype(valeKind);
  if (auto valeInterfaceMT = dynamic_cast<InterfaceKind*>(valeKind)) {
    buildFlare(FL(), globalState, functionState, builder);
    auto hostInterfaceMT = globalState->linearRegion->linearizeInterfaceKind(valeInterfaceMT);
    auto hostVirtualArgRefMT = prototype->params[2];
    int indexInEdge = globalState->getInterfaceMethodIndex(hostInterfaceMT, prototype);
    buildFlare(FL(), globalState, functionState, builder);
    auto methodFunctionPtrLE =
        globalState->getRegion(hostVirtualArgRefMT)
            ->getInterfaceMethodFunctionPtr(functionState, builder, hostVirtualArgRefMT, objectRef, indexInEdge);
    buildFlare(FL(), globalState, functionState, builder);
    return buildInterfaceCall(globalState, functionState, builder, prototype, methodFunctionPtrLE, {regionInstanceRef, sourceRegionInstanceRef, objectRef}, 2);
  } else {
    return buildCallV(globalState, functionState, builder, prototype, {regionInstanceRef, sourceRegionInstanceRef, objectRef});
  }
}

Prototype* RCImm::getUnserializePrototype(Kind* valeKind) {
  auto boolMT = globalState->metalCache->boolRef;
  auto valeRefMT =
      globalState->metalCache->getReference(
          Ownership::MUTABLE_SHARE, Location::YONDER, valeKind);
  auto hostRefMT = globalState->linearRegion->linearizeReference(valeRefMT, true);
  auto hostRegionRefMT = globalState->linearRegion->getRegionRefType();
  return globalState->metalCache->getPrototype(
      globalState->unserializeName, valeRefMT, {regionRefMT, hostRegionRefMT, hostRefMT});
}

Prototype* RCImm::getUnserializeThunkPrototype(StructKind* valeStructKind, InterfaceKind* valeInterfaceKind) {
  auto boolMT = globalState->metalCache->boolRef;
  auto valeStructRefMT =
      globalState->metalCache->getReference(
          Ownership::MUTABLE_SHARE, Location::YONDER, valeStructKind);
  auto hostStructRefMT = globalState->linearRegion->linearizeReference(valeStructRefMT, true);
  auto valeInterfaceRefMT =
      globalState->metalCache->getReference(
          Ownership::MUTABLE_SHARE, Location::YONDER, valeInterfaceKind);
  auto hostInterfaceRefMT = globalState->linearRegion->linearizeReference(valeInterfaceRefMT, true);
  return globalState->metalCache->getPrototype(
      globalState->unserializeThunkName, valeInterfaceRefMT,
      {getRegionRefType(), globalState->linearRegion->getRegionRefType(), hostStructRefMT});
}

void RCImm::declareConcreteUnserializeFunction(Kind* valeKind) {
  auto prototype = getUnserializePrototype(valeKind);
  auto nameL = globalState->unserializeName->name + "__" + globalState->getKindName(valeKind)->name;
  declareExtraFunction(globalState, prototype, nameL);
}

void RCImm::defineConcreteUnserializeFunction(Kind* valeKind) {
  auto i32MT = globalState->metalCache->i32Ref;
  auto boolMT = globalState->metalCache->boolRef;

  auto prototype = getUnserializePrototype(valeKind);

  auto unserializeMemberOrElement =
      [this](
          FunctionState* functionState,
          LLVMBuilderRef builder,
          Ref regionInstanceRef,
          Ref hostRegionInstanceRef,
          Reference* hostMemberRefMT,
          Ref hostMemberRef) {
        auto valeMemberRefMT = globalState->linearRegion->unlinearizeReference(hostMemberRefMT, true);
        auto hostMemberLE =
            globalState->getRegion(hostMemberRefMT)->checkValidReference(
                FL(), functionState, builder, true, hostMemberRefMT, hostMemberRef);
        if (dynamic_cast<Int*>(hostMemberRefMT->kind)) {
          return toRef(globalState->getRegion(valeMemberRefMT), valeMemberRefMT, hostMemberLE);
        } else if (dynamic_cast<Bool*>(hostMemberRefMT->kind)) {
          auto resultLE = LLVMBuildTrunc(builder, hostMemberLE, LLVMInt1TypeInContext(globalState->context), "boolAsI1");
          return toRef(globalState->getRegion(valeMemberRefMT), valeMemberRefMT, resultLE);
        } else if (dynamic_cast<Float*>(hostMemberRefMT->kind)) {
          return toRef(globalState->getRegion(valeMemberRefMT), valeMemberRefMT, hostMemberLE);
        } else if (
            dynamic_cast<Str*>(hostMemberRefMT->kind) ||
            dynamic_cast<StructKind*>(hostMemberRefMT->kind) ||
            dynamic_cast<StaticSizedArrayT*>(hostMemberRefMT->kind) ||
            dynamic_cast<RuntimeSizedArrayT*>(hostMemberRefMT->kind)) {
          auto destinationMemberRef =
              callUnserialize(
                  functionState, builder, regionInstanceRef, hostRegionInstanceRef, valeMemberRefMT->kind, hostMemberRef);
          return destinationMemberRef;
        } else if (dynamic_cast<InterfaceKind*>(hostMemberRefMT->kind)) {
          auto destinationMemberRef =
              callUnserialize(
                  functionState, builder, regionInstanceRef, hostRegionInstanceRef, valeMemberRefMT->kind, hostMemberRef);
          return destinationMemberRef;
        } else { assert(false); throw 1337; }
      };

  defineFunctionBodyV(
      globalState, prototype,
      [&](FunctionState* functionState, LLVMBuilderRef builder) -> void {
        auto hostObjectRefMT = prototype->params[2];
        auto valeObjectRefMT = prototype->returnType;

        auto regionInstanceRef =
            toRef(this, regionRefMT, functionState->getParam(UserArgIndex{UNSERIALIZE_PARAM_VALE_REGION_INSTANCE_REF}));
        auto hostRegionRefMT =
            globalState->linearRegion->getRegionRefType();
        auto hostRegionInstanceRef =
            toRef(globalState->linearRegion, hostRegionRefMT, functionState->getParam(UserArgIndex{UNSERIALIZE_PARAM_HOST_REGION_INSTANCE_REF}));
        auto hostObjectRef =
            toLiveRef(FL(), globalState, functionState, builder, hostRegionInstanceRef, hostObjectRefMT, functionState->getParam(UserArgIndex{UNSERIALIZE_PARAM_HOST_OBJECT_REF}));

        if (auto valeStructKind = dynamic_cast<StructKind *>(valeObjectRefMT->kind)) {
          auto hostStructKind = dynamic_cast<StructKind *>(hostObjectRefMT->kind);
          assert(hostStructKind);
          auto valeStructDefM = globalState->program->getStruct(valeStructKind);

          std::vector<Ref> memberRefs;

          for (int i = 0; i < valeStructDefM->members.size(); i++) {
            auto valeMemberM = valeStructDefM->members[i];
            auto valeMemberRefMT = valeMemberM->type;
            auto hostMemberRefMT = globalState->linearRegion->linearizeReference(valeMemberRefMT, true);
            auto hostMemberRef =
                globalState->getRegion(hostObjectRefMT)->loadMember(
                    functionState, builder, hostRegionInstanceRef, hostObjectRefMT, hostObjectRef,
                    i, hostMemberRefMT, hostMemberRefMT, valeMemberM->name);
            memberRefs.push_back(
                unserializeMemberOrElement(
                    functionState, builder, regionInstanceRef, hostRegionInstanceRef, hostMemberRefMT, hostMemberRef));
          }

          auto resultRef = allocate(makeVoidRef(globalState), FL(), functionState, builder, valeObjectRefMT, memberRefs);

//          // Remember, we're subtracting each size from a very large number, so its easier to round down
//          // to the next multiple of 16.
//          totalSizeIntLE = hexRoundDown(globalState, builder, totalSizeIntLE);

          auto resultRefLE = checkValidReference(FL(), functionState, builder, true, valeObjectRefMT, resultRef);

          LLVMBuildRet(builder, resultRefLE);
        } else if (dynamic_cast<Str*>(valeObjectRefMT->kind)) {
          auto lengthLE =
              globalState->getRegion(hostObjectRefMT)
                  ->getStringLen(
                      functionState, builder, hostObjectRefMT, hostRegionInstanceRef, hostObjectRef);
          auto sourceBytesPtrLE =
              globalState->getRegion(hostObjectRefMT)->
                  getStringBytesPtr(functionState, builder, hostObjectRefMT, hostRegionInstanceRef, hostObjectRef);

          auto strRef = mallocStr(makeVoidRef(globalState), functionState, builder, lengthLE, sourceBytesPtrLE);

          buildFlare(FL(), globalState, functionState, builder, "done storing");

          LLVMBuildRet(
              builder, checkValidReference(FL(), functionState, builder, true, globalState->metalCache->mutStrRef, strRef));
        } else if (auto valeRsaMT = dynamic_cast<RuntimeSizedArrayT *>(valeObjectRefMT->kind)) {
          auto valeRsaRefMT = valeObjectRefMT;
          auto hostRsaMT = dynamic_cast<RuntimeSizedArrayT *>(hostObjectRefMT->kind);
          assert(hostRsaMT);
          auto hostRsaRefMT = hostObjectRefMT;

          auto lengthRef =
              globalState->getRegion(hostObjectRefMT)
                  ->getRuntimeSizedArrayLength(
                      functionState, builder, hostRegionInstanceRef, hostObjectRefMT, hostObjectRef);

          auto lengthLE =
              globalState->getRegion(globalState->metalCache->i32Ref)
                  ->checkValidReference(FL(), functionState, builder, false, globalState->metalCache->i32Ref, lengthRef);
          buildFlare(FL(), globalState, functionState, builder, "Unserialized RSA length ", lengthLE);

          auto valeRsaRef =
              constructRuntimeSizedArray(
                  makeVoidRef(globalState),
                  functionState, builder, valeRsaRefMT, valeRsaMT, lengthRef, "serializedrsa");
          auto valeMemberRefMT = globalState->program->getRuntimeSizedArray(valeRsaMT)->elementType;
          auto hostMemberRefMT = globalState->linearRegion->linearizeReference(valeMemberRefMT, true);

          intRangeLoopReverseV(
              globalState, functionState, builder, globalState->metalCache->i32Ref, lengthRef,

              [this, functionState, regionInstanceRef, hostRegionInstanceRef, hostObjectRefMT, valeRsaRef, hostMemberRefMT, valeObjectRefMT, hostRsaMT, valeRsaMT, hostObjectRef, valeMemberRefMT, unserializeMemberOrElement](
                  Ref indexRef, LLVMBuilderRef bodyBuilder) {
                auto indexLE =
                    globalState->getRegion(globalState->metalCache->i32)
                        ->checkValidReference(FL(), functionState, bodyBuilder, true, globalState->metalCache->i32Ref, indexRef);
                // Manually making InBoundsLE because the array's size is the bound of the containing loop.
                auto indexInBoundsLE = InBoundsLE{indexLE};

                auto hostMemberRef =
                    globalState->getRegion(hostObjectRefMT)
                        ->loadElementFromRSA(
                            functionState, bodyBuilder, hostRegionInstanceRef, hostObjectRefMT, hostRsaMT,
                            hostObjectRef, indexInBoundsLE)
                        .move();
                auto valeElementRef =
                    unserializeMemberOrElement(
                        functionState, bodyBuilder, regionInstanceRef, hostRegionInstanceRef, hostMemberRefMT,
                        hostMemberRef);
                pushRuntimeSizedArrayNoBoundsCheck(
                    functionState, bodyBuilder, regionInstanceRef, valeObjectRefMT, valeRsaMT, valeRsaRef,
                    indexInBoundsLE, valeElementRef);
              });

          LLVMBuildRet(builder, checkValidReference(FL(), functionState, builder, valeRsaRefMT, valeRsaRef));
        } else if (auto valeSsaMT = dynamic_cast<StaticSizedArrayT *>(valeObjectRefMT->kind)) {
          auto hostSsaMT = dynamic_cast<StaticSizedArrayT *>(hostObjectRefMT->kind);
          assert(hostSsaMT);
          auto valeSsaRefMT = valeObjectRefMT;
          auto hostSsaRefMT = hostObjectRefMT;

          auto valeSsaRef =
              constructStaticSizedArray(
                  makeVoidRef(globalState),
                  functionState, builder, valeSsaRefMT, valeSsaMT);
          auto valeSsaDefM = globalState->program->getStaticSizedArray(valeSsaMT);
          int length = valeSsaDefM->size;
          auto valeMemberRefMT = valeSsaDefM->elementType;

          intRangeLoopReverseV(
              globalState, functionState, builder, globalState->metalCache->i32Ref, globalState->constI32(length),
              [this, functionState, regionInstanceRef, hostRegionInstanceRef, hostObjectRefMT, valeSsaRef, valeObjectRefMT, hostSsaMT, valeSsaMT, hostObjectRef, valeMemberRefMT, unserializeMemberOrElement](
                  Ref indexRef, LLVMBuilderRef bodyBuilder) {
                auto indexLE =
                    globalState->getRegion(globalState->metalCache->i32Ref)
                        ->checkValidReference(FL(), functionState, bodyBuilder, true, globalState->metalCache->i32Ref, indexRef);
                // Manually making InBoundsLE because the array's size is the bound of the containing loop.
                auto indexInBoundsLE = InBoundsLE{indexLE};

                auto hostMemberRef =
                    globalState->getRegion(hostObjectRefMT)
                        ->loadElementFromSSA(
                            functionState, bodyBuilder, hostRegionInstanceRef, hostObjectRefMT,
                            hostSsaMT,
                            hostObjectRef, indexInBoundsLE)
                        .move();
                auto hostMemberRefMT = globalState->linearRegion->linearizeReference(valeMemberRefMT, true);
                auto valeElementRef =
                    unserializeMemberOrElement(
                        functionState, bodyBuilder, regionInstanceRef, hostRegionInstanceRef, hostMemberRefMT,
                        hostMemberRef);
                initializeElementInSSA(
                    functionState, bodyBuilder, regionInstanceRef, valeObjectRefMT, valeSsaMT, valeSsaRef,
                    indexInBoundsLE, valeElementRef);
              });


          LLVMBuildRet(builder, checkValidReference(FL(), functionState, builder, valeSsaRefMT, valeSsaRef));
        } else
          { assert(false); throw 1337; }
      });
}

ValeFuncPtrLE RCImm::getInterfaceMethodFunctionPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    int indexInEdge) {
  return getInterfaceMethodFunctionPtrFromItable(
      globalState, functionState, builder, &kindStructs, virtualParamMT, virtualArgRef, indexInEdge);
}

LLVMValueRef RCImm::stackify(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    Ref refToStore,
    bool knownLive) {
  auto toStoreLE = checkValidReference(FL(), functionState, builder, false, local->type, refToStore);
  auto typeLT = translateType(local->type);
  return makeBackendLocal(functionState, builder, typeLT, local->id->maybeName.c_str(), toStoreLE);
}

Ref RCImm::unstackify(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return loadLocal(functionState, builder, local, localAddr);
}

Ref RCImm::loadLocal(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return normalLocalLoad(globalState, functionState, builder, local, localAddr);
}

Ref RCImm::localStore(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore, bool knownLive) {
  return normalLocalStore(globalState, functionState, builder, local, localAddr, refToStore);
}

std::string RCImm::getExportName(
    Package* package,
    Reference* reference,
    bool includeProjectName) {
  auto linear = globalState->linearRegion->linearizeReference(reference, true);
  return globalState->linearRegion->getExportName(package, linear, includeProjectName);
//  if (dynamic_cast<InterfaceKind*>(reference->kind)) {
//    return package->getKindExportName(reference->kind);
//  } else {
//    return package->getKindExportName(reference->kind) + (reference->location == Location::YONDER ? "*" : "");
//  }
}

Ref RCImm::createRegionInstanceLocal(FunctionState* functionState, LLVMBuilderRef builder) {
  auto regionLT = kindStructs.getStructInnerStruct(regionKind);
  auto regionInstancePtrLE =
      makeBackendLocal(functionState, builder, regionLT, "region", LLVMGetUndef(regionLT));
  auto regionInstanceRef = toRef(this, regionRefMT, regionInstancePtrLE);

  return regionInstanceRef;
}

void RCImm::declareConcreteFreeFunction(Kind* valeKind) {
  auto prototype = getFreePrototype(valeKind);
  auto nameL = globalState->freeName->name + "__" + globalState->getKindName(valeKind)->name;
  declareExtraFunction(globalState, prototype, nameL);
}

void RCImm::defineConcreteFreeFunction(Kind* valeKind) {
  auto i32MT = globalState->metalCache->i32Ref;
  auto boolMT = globalState->metalCache->boolRef;

  auto prototype = getFreePrototype(valeKind);

  defineFunctionBodyV(
      globalState, prototype,
      [&](FunctionState* functionState, LLVMBuilderRef builder) -> void {
        auto objectRefMT = prototype->params[1];

        auto regionInstanceRef = makeRegionInstance(builder);

        auto objectRef =
            checkRefLive(
                FL(), functionState, builder, regionInstanceRef, objectRefMT,
                toRef(
                    globalState->getRegion(objectRefMT),
                    objectRefMT,
                    functionState->getParam(UserArgIndex{FREE_PARAM_OBJECT_REF})),
                    false);

        if (auto structKind = dynamic_cast<StructKind *>(objectRefMT->kind)) {
          auto structDefM = globalState->program->getStruct(structKind);

          for (int i = 0; i < structDefM->members.size(); i++) {
            auto memberM = structDefM->members[i];
            auto memberRefMT = memberM->type;
            auto memberRef =
                globalState->getRegion(objectRefMT)->loadMember(
                    functionState, builder, regionInstanceRef, objectRefMT, objectRef,
                    i, memberRefMT, memberRefMT, memberM->name);
            discard(FL(), globalState, functionState, builder, memberRefMT, memberRef);
          }

          innerDeallocate(FL(), globalState, functionState, &kindStructs, builder, objectRefMT, objectRef);
          LLVMBuildRet(builder, makeVoid(globalState));
        } else if (dynamic_cast<Str*>(objectRefMT->kind)) {
          buildFlare(FL(), globalState, functionState, builder, "done storing");

          innerDeallocate(FL(), globalState, functionState, &kindStructs, builder, objectRefMT, objectRef);
          LLVMBuildRet(builder, makeVoid(globalState));
        } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT *>(objectRefMT->kind)) { // XEGDWR combine with below case
          auto rsaRefMT = objectRefMT;

          auto lengthRef =
              getRuntimeSizedArrayLength(
                  functionState, builder, regionInstanceRef, objectRefMT, objectRef);

          auto memberRefMT = globalState->program->getRuntimeSizedArray(rsaMT)->elementType;

          intRangeLoopReverseV(
              globalState, functionState, builder, globalState->metalCache->i32Ref, lengthRef,

              [this, functionState, regionInstanceRef, objectRefMT, rsaMT, objectRef, memberRefMT](
                  Ref indexRef, LLVMBuilderRef bodyBuilder) {
                auto indexLE =
                    globalState->getRegion(globalState->metalCache->i32Ref)
                        ->checkValidReference(FL(), functionState, bodyBuilder, false, globalState->metalCache->i32Ref, indexRef);
                // Manually making InBoundsLE because the array's size is the bound of the containing loop.
                auto indexInBoundsLE = InBoundsLE{indexLE};

                auto memberRef =
                    globalState->getRegion(objectRefMT)
                        ->loadElementFromRSA(
                            functionState, bodyBuilder, regionInstanceRef, objectRefMT, rsaMT,
                            objectRef, indexInBoundsLE)
                        .move();
                discard(FL(), globalState, functionState, bodyBuilder, memberRefMT, memberRef);
              });

          LLVMBuildRet(builder, makeVoid(globalState));
        } else if (auto valeSsaMT = dynamic_cast<StaticSizedArrayT *>(objectRefMT->kind)) { // XEGDWR combine with above case
          auto hostSsaMT = dynamic_cast<StaticSizedArrayT *>(objectRefMT->kind);
          assert(hostSsaMT);
          auto ssaRefMT = objectRefMT;

          auto ssaDefM = globalState->program->getStaticSizedArray(valeSsaMT);
          int length = ssaDefM->size;
          auto memberRefMT = ssaDefM->elementType;

          intRangeLoopReverseV(
              globalState, functionState, builder, globalState->metalCache->i32Ref, globalState->constI32(length),
              [this, functionState, regionInstanceRef, objectRefMT, hostSsaMT, objectRef, memberRefMT](
                  Ref indexRef, LLVMBuilderRef bodyBuilder) {

                auto indexLE =
                    globalState->getRegion(globalState->metalCache->i32Ref)
                        ->checkValidReference(FL(), functionState, bodyBuilder, false, globalState->metalCache->i32Ref, indexRef);
                // Manually making InBoundsLE because the array's size is the bound of the containing loop.
                auto indexInBoundsLE = InBoundsLE{indexLE};

                auto memberRef =
                    globalState->getRegion(objectRefMT)
                        ->loadElementFromSSA(
                            functionState, bodyBuilder, regionInstanceRef, objectRefMT, hostSsaMT,
                            objectRef, indexInBoundsLE)
                        .move();
                discard(FL(), globalState, functionState, bodyBuilder, memberRefMT, memberRef);
              });

          LLVMBuildRet(builder, makeVoid(globalState));
        } else
          { assert(false); throw 1337; }
      });
}

void RCImm::callFree(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Kind* kind,
    Ref objectRef) {
  auto prototype = getFreePrototype(kind);
  if (auto interfaceMT = dynamic_cast<InterfaceKind*>(kind)) {
    buildFlare(FL(), globalState, functionState, builder);
    auto virtualArgRefMT = prototype->params[1];
    int indexInEdge = globalState->getInterfaceMethodIndex(interfaceMT, prototype);
    buildFlare(FL(), globalState, functionState, builder);
    auto methodFunctionPtrLE =
        globalState->getRegion(virtualArgRefMT)
            ->getInterfaceMethodFunctionPtr(functionState, builder, virtualArgRefMT, objectRef, indexInEdge);
    buildFlare(FL(), globalState, functionState, builder);
    buildInterfaceCall(globalState, functionState, builder, prototype, methodFunctionPtrLE, {regionInstanceRef, objectRef}, 1);
  } else {
    buildCallV(globalState, functionState, builder, prototype, {regionInstanceRef, objectRef});
  }
}

Prototype* RCImm::getFreePrototype(Kind* valeKind) {
  auto boolMT = globalState->metalCache->boolRef;
  auto refMT =
      globalState->metalCache->getReference(
          Ownership::MUTABLE_SHARE, Location::YONDER, valeKind);
  return globalState->metalCache->getPrototype(
      globalState->freeName, globalState->metalCache->voidRef, {regionRefMT, refMT});
}

Prototype* RCImm::getFreeThunkPrototype(StructKind* valeStructKind, InterfaceKind* valeInterfaceKind) {
  auto boolMT = globalState->metalCache->boolRef;
  auto structRefMT =
      globalState->metalCache->getReference(
          Ownership::MUTABLE_SHARE, Location::YONDER, valeStructKind);
  auto interfaceRefMT =
      globalState->metalCache->getReference(
          Ownership::MUTABLE_SHARE, Location::YONDER, valeInterfaceKind);
  return globalState->metalCache->getPrototype(
      globalState->freeThunkName, globalState->metalCache->voidRef,
      {getRegionRefType(), structRefMT});
}

void RCImm::defineEdgeFreeFunction(Edge* edge) {
  auto boolMT = globalState->metalCache->boolRef;

  auto thunkPrototype = getFreeThunkPrototype(edge->structName, edge->interfaceName);
  defineFunctionBodyV(
      globalState, thunkPrototype,
      [&](FunctionState *functionState, LLVMBuilderRef builder) {
        auto structPrototype = getFreePrototype(edge->structName);

        auto objectRefMT = structPrototype->params[1];

        auto regionInstanceRef =
            toRef(this, regionRefMT, functionState->getParam(UserArgIndex{FREE_PARAM_REGION_INSTANCE_REF}));
        auto objectRef =
            toRef(globalState->getRegion(objectRefMT), objectRefMT, functionState->getParam(UserArgIndex{FREE_PARAM_OBJECT_REF}));

        buildCallV(
            globalState, functionState, builder, structPrototype,
            {regionInstanceRef, objectRef});

//        auto interfaceKind = dynamic_cast<InterfaceKind *>(thunkPrototype->returnType->kind);
//        assert(interfaceKind);
//        auto structKind = dynamic_cast<StructKind *>(structPrototype->returnType->kind);
//        assert(structKind);

//        auto interfaceRef =
//            upcast(
//                functionState, builder, structPrototype->returnType, structKind,
//                objectRef, thunkPrototype->returnType, interfaceKind);
//
//        checkValidReference(FL(), functionState, builder, true, thunkPrototype->returnType, interfaceRef);
        LLVMBuildRet(builder, makeVoid(globalState));
      });
}

void RCImm::declareInterfaceFreeFunction(InterfaceKind* kind) {
  auto interface = dynamic_cast<InterfaceKind*>(kind);
  auto interfaceMethod = getFreeInterfaceMethod(kind);
  globalState->addInterfaceExtraMethod(interface, interfaceMethod);
}

InterfaceMethod* RCImm::getFreeInterfaceMethod(Kind* valeKind) {
  return globalState->metalCache->getInterfaceMethod(
      getFreePrototype(valeKind), 1);
}

LiveRef RCImm::checkRefLive(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refMT,
    Ref ref,
    bool refKnownLive) {
  // Everything is always known live in an RC world.
  auto refLE = checkValidReference(FL(), functionState, builder, true, refMT, ref);
  return wrapToLiveRef(FL(), functionState, builder, regionInstanceRef, refMT, refLE);
}

LiveRef RCImm::wrapToLiveRef(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refMT,
    LLVMValueRef ref) {
  assert(translateType(refMT) == LLVMTypeOf(ref));
  return LiveRef(refMT, ref);
}

LiveRef RCImm::preCheckBorrow(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refMT,
    Ref ref,
    bool refKnownLive) {
  // Everything is always known live in an RC world.
  return checkRefLive(FL(), functionState, builder, regionInstanceRef, refMT, ref, true);
}

Ref RCImm::mutabilify(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refMT,
    Ref ref,
    Reference* targetRefMT) {
  { assert(false); throw 1337; } // impl
}

LiveRef RCImm::immutabilify(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refMT,
    Ref ref,
    Reference* targetRefMT) {
  // Imm and mut refs in RC are the same, so we can just do a transmute.
  auto transmutedRef =
      transmutePtr(globalState, functionState, builder, true, refMT, targetRefMT, ref);
  auto transmutedRefLE =
      checkValidReference(FL(), functionState, builder, true, targetRefMT, transmutedRef);
  return wrapToLiveRef(FL(), functionState, builder, regionInstanceRef, targetRefMT, transmutedRefLE);
}
