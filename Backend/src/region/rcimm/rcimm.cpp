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
          Ownership::BORROW, Location::YONDER, regionKind);
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
  return wrap(this, regionRefMT, LLVMConstNull(translateType(regionRefMT)));
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
      adjustStrongRc(from, globalState, functionState, &kindStructs, builder, ref, sourceRef, 1);
    }
  } else {
    std::cerr << "Unimplemented type in acquireReference: "
              << typeid(*sourceRef->kind).name() << std::endl;
    assert(false);
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
  assert(false);
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
  assert(false);
  exit(1);
}

void RCImm::declareStruct(
    StructDefinition* structM) {
  globalState->regionIdByKind.emplace(structM->kind, getRegionId());

  kindStructs.declareStruct(structM->kind, structM->weakability);
}

void RCImm::declareStructExtraFunctions(StructDefinition* structDefM) {
  declareConcreteUnserializeFunction(structDefM->kind);
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
}

void RCImm::declareStaticSizedArray(
    StaticSizedArrayDefinitionT* ssaDefM) {
  globalState->regionIdByKind.emplace(ssaDefM->kind, getRegionId());

  kindStructs.declareStaticSizedArray(ssaDefM->kind, Weakability::NON_WEAKABLE);
}

void RCImm::declareStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* ssaDef) {
  declareConcreteUnserializeFunction(ssaDef->kind);
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
}

void RCImm::declareRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* rsaDefM) {
  globalState->regionIdByKind.emplace(rsaDefM->kind, getRegionId());

  kindStructs.declareRuntimeSizedArray(rsaDefM->kind, Weakability::NON_WEAKABLE);
}

void RCImm::declareRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* rsaDefM) {
  declareConcreteUnserializeFunction(rsaDefM->kind);
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
}

void RCImm::declareInterface(
    InterfaceDefinition* interfaceM) {
  globalState->regionIdByKind.emplace(interfaceM->kind, getRegionId());

  kindStructs.declareInterface(interfaceM->kind, interfaceM->weakability);
}

void RCImm::declareInterfaceExtraFunctions(InterfaceDefinition* interfaceDefM) {
  declareInterfaceUnserializeFunction(interfaceDefM->kind);
}

void RCImm::defineInterface(
    InterfaceDefinition* interfaceM) {
  auto interfaceMethodTypesL = globalState->getInterfaceFunctionTypes(interfaceM->kind);
  kindStructs.defineInterface(interfaceM, interfaceMethodTypesL);
}

void RCImm::defineInterfaceExtraFunctions(InterfaceDefinition* interfaceDefM) {
}

void RCImm::declareEdge(Edge* edge) {
  kindStructs.declareEdge(edge);

  auto hostStructMT = globalState->linearRegion->linearizeStructKind(edge->structName);
  auto hostInterfaceMT = globalState->linearRegion->linearizeInterfaceKind(edge->interfaceName);

  auto interfaceMethod = getUnserializeInterfaceMethod(edge->interfaceName);
  auto thunkPrototype = getUnserializeThunkPrototype(edge->structName, edge->interfaceName);
  globalState->addEdgeExtraMethod(hostInterfaceMT, hostStructMT, interfaceMethod, thunkPrototype);
  auto nameL = globalState->unserializeName->name + "__" + edge->interfaceName->fullName->name + "__" + edge->structName->fullName->name;
  declareExtraFunction(globalState, thunkPrototype, nameL);
}

void RCImm::defineEdge(Edge* edge) {
  auto interfaceM = globalState->program->getInterface(edge->interfaceName);

  auto interfaceFunctionsLT = globalState->getInterfaceFunctionTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  kindStructs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);

  defineEdgeUnserializeFunction(edge);
}

Ref RCImm::weakAlias(
    FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) {
  assert(false);
  exit(1);
}

void RCImm::discardOwningRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  assert(false);
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
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetMemberType,
    const std::string& memberName) {
  auto memberLE =
      loadMember(
          functionState, builder, regionInstanceRef, structRefMT, structRef, memberIndex, expectedMemberType,
          targetMemberType, memberName);
  auto resultRef =
      upgradeLoadResultToRefWithTargetOwnership(
          functionState, builder, expectedMemberType, targetMemberType, memberLE);
  return resultRef;
}

void RCImm::storeMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    const std::string& memberName,
    Reference* newMemberRefMT,
    Ref newMemberRef) {
  assert(false);
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
  assert(false);
}

void RCImm::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  assert(false);
}

Ref RCImm::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef,
    bool knownLive) {
  assert(false);
  exit(1);
}

LLVMValueRef RCImm::getStringBytesPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Ref ref) {
  auto strWrapperPtrLE =
      kindStructs.makeWrapperPtr(
          FL(), functionState, builder,
          globalState->metalCache->strRef,
          checkValidReference(
              FL(), functionState, builder, globalState->metalCache->strRef, ref));
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
  assert(false);
  exit(1);
}

Ref RCImm::constructStaticSizedArray(
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
    Ref arrayRef,
    bool arrayKnownLive) {
  return getRuntimeSizedArrayLengthStrong(globalState, functionState, builder, &kindStructs, rsaRefMT, arrayRef);
}

Ref RCImm::getRuntimeSizedArrayCapacity(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* rsaRefMT,
    Ref arrayRef,
    bool arrayKnownLive) {
  return getRuntimeSizedArrayCapacityStrong(globalState, functionState, builder, &kindStructs, rsaRefMT, arrayRef);
}

LLVMValueRef RCImm::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
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
    Reference* sourceType,
    Reference* targetType,
    LoadResult sourceLoad) {
  auto sourceRef = sourceLoad.extractForAliasingInternals();
  auto sourceOwnership = sourceType->ownership;
  auto sourceLocation = sourceType->location;
  auto targetOwnership = targetType->ownership;
  auto targetLocation = targetType->location;
//  assert(sourceLocation == targetLocation); // unimplemented

  if (sourceLocation == Location::INLINE) {
    return sourceRef;
  } else {
    return sourceRef;
  }
}

void RCImm::checkInlineStructType(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  auto argLE = checkValidReference(FL(), functionState, builder, refMT, ref);
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
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto ssaDef = globalState->program->getStaticSizedArray(ssaMT);
  return regularloadElementFromSSA(
      globalState, functionState, builder, ssaRefMT, ssaMT, ssaDef->elementType, ssaDef->size, ssaDef->mutability, arrayRef, arrayKnownLive, indexRef, &kindStructs);
}

LoadResult RCImm::loadElementFromRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  return regularLoadElementFromRSAWithoutUpgrade(
      globalState, functionState, builder, &kindStructs, false, rsaRefMT, rsaMT, rsaDef->mutability, rsaDef->elementType, arrayRef,
      arrayKnownLive, indexRef);
}

Ref RCImm::popRuntimeSizedArrayNoBoundsCheck(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref arrayRegionInstanceRef,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  return regularLoadElementFromRSAWithoutUpgrade(
      globalState, functionState, builder, &kindStructs, false, rsaRefMT, rsaMT, rsaDef->mutability, rsaDef->elementType, arrayRef,
      arrayKnownLive, indexRef).move();
}


Ref RCImm::storeElementInRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    Ref elementRef) {
  assert(false);
  exit(1);
}

void RCImm::pushRuntimeSizedArrayNoBoundsCheck(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference *rsaRefMT,
    RuntimeSizedArrayT *rsaMT,
    Ref rsaRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto elementType = globalState->program->getRuntimeSizedArray(rsaMT)->elementType;
  buildFlare(FL(), globalState, functionState, builder);

  auto arrayWrapperPtrLE =
      kindStructs.makeWrapperPtr(
          FL(), functionState, builder, rsaRefMT,
          globalState->getRegion(rsaRefMT)->checkValidReference(FL(), functionState, builder, rsaRefMT, rsaRef));
  auto sizeRef = ::getRuntimeSizedArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
  auto arrayElementsPtrLE = getRuntimeSizedArrayContentsPtr(builder, false, arrayWrapperPtrLE);
  ::initializeElementWithoutIncrementSize(
      globalState, functionState, builder, rsaRefMT->location,
      elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
}

void RCImm::deallocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  innerDeallocate(from, globalState, functionState, &kindStructs, builder, refMT, ref);
}


Ref RCImm::constructRuntimeSizedArray(
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
          rsaWrapperPtrLT, rsaElementLT, capacityRef, capacityRef, false, typeName,
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
      wrap(this, globalState->metalCache->strRef, ::mallocStr(
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
    Ref regionInstanceRef,
    Ref ref) {
  auto strWrapperPtrLE =
      kindStructs.makeWrapperPtr(
          FL(), functionState, builder,
          globalState->metalCache->strRef,
          checkValidReference(
              FL(), functionState, builder, globalState->metalCache->strRef, ref));
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
  } else if (dynamic_cast<Str *>(sourceRnd)) {
    buildFlare(FL(), globalState, functionState, builder);
    assert(sourceMT->ownership == Ownership::SHARE);
    auto rcLE =
        adjustStrongRc(
            from, globalState, functionState, &kindStructs, builder, sourceRef, sourceMT, -1);
    buildFlare(from, globalState, functionState, builder, "Str RC: ", rcLE);
    buildIfV(
        globalState, functionState,
        builder,
        isZeroLE(builder, rcLE),
        [this, from, globalState, functionState, sourceRef, sourceMT](
            LLVMBuilderRef thenBuilder) {
          buildFlare(from, globalState, functionState, thenBuilder, "Freeing shared str!");
          innerDeallocate(from, globalState, functionState, &kindStructs, thenBuilder, sourceMT, sourceRef);
        });
  } else if (dynamic_cast<InterfaceKind *>(sourceRnd) ||
      dynamic_cast<StructKind *>(sourceRnd) ||
      dynamic_cast<StaticSizedArrayT *>(sourceRnd) ||
      dynamic_cast<RuntimeSizedArrayT *>(sourceRnd)) {
    buildFlare(FL(), globalState, functionState, builder);
    if (auto sr = dynamic_cast<StructKind *>(sourceRnd)) {
      buildFlare(FL(), globalState, functionState, builder, sr->fullName->name);
    }
    assert(sourceMT->ownership == Ownership::SHARE);
    if (sourceMT->location == Location::INLINE) {
      buildFlare(FL(), globalState, functionState, builder);
      // Do nothing, we can just let inline structs disappear
    } else {
      buildFlare(FL(), globalState, functionState, builder);
      auto rcLE =
          adjustStrongRc(
              from, globalState, functionState, &kindStructs, builder, sourceRef, sourceMT, -1);
      buildFlare(FL(), globalState, functionState, builder, rcLE);
      buildIfV(
          globalState, functionState,
          builder,
          isZeroLE(builder, rcLE),
          [from, globalState, functionState, sourceRef, sourceMT](LLVMBuilderRef thenBuilder) {
            buildFlare(FL(), globalState, functionState, thenBuilder);
            auto immDestructor = globalState->program->getImmDestructor(sourceMT->kind);
            auto funcL = globalState->getFunction(immDestructor->name);

            auto sourceLE =
                globalState->getRegion(sourceMT)->checkValidReference(FL(),
                    functionState, thenBuilder, sourceMT, sourceRef);
            std::vector<LLVMValueRef> argExprsL = {sourceLE};
            return LLVMBuildCall(thenBuilder, funcL, argExprsL.data(), argExprsL.size(), "");
          });
    }
  } else {
    std::cerr << "Unimplemented type in discard: "
        << typeid(*sourceMT->kind).name() << std::endl;
    assert(false);
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
      assert(referenceM->ownership == Ownership::SHARE);
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
      assert(LLVMTypeOf(globalState->neverPtr) == result);
      return result;
    } else {
      std::cerr << "Unimplemented type: " << typeid(*referenceM->kind).name() << std::endl;
      assert(false);
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
//    assert(false);
//  }
//  return kindStructs.getControlBlockStruct();
//}


LoadResult RCImm::loadMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {
  if (structRefMT->location == Location::INLINE) {
    auto innerStructLE =
        globalState->getRegion(structRefMT)->checkValidReference(
            FL(), functionState, builder, structRefMT, structRef);
    auto memberLE =
        LLVMBuildExtractValue(builder, innerStructLE, memberIndex, memberName.c_str());
    return LoadResult{wrap(globalState->getRegion(expectedMemberType), expectedMemberType, memberLE)};
  } else {
    return regularLoadStrongMember(globalState, functionState, builder, &kindStructs, structRefMT, structRef, memberIndex, expectedMemberType, targetType, memberName);
  }
}

void RCImm::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* refM,
    LLVMValueRef refLE) {
  regularCheckValidReference(checkerAFL, globalState, functionState, builder, kindStructs, refM, refLE);
}

//std::string RCImm::getMemberArbitraryRefNameCSeeMMEDT(Reference* sourceMT) {
//  assert(false);
//  exit(1);
//}

std::string RCImm::generateStructDefsC(
    Package* currentPackage,

    StructDefinition* structDefM) {
  assert(false);
  return "";
}

std::string RCImm::generateInterfaceDefsC(
    Package* currentPackage, InterfaceDefinition* interfaceDefM) {
  assert(false);
  return "";
}

std::string RCImm::generateRuntimeSizedArrayDefsC(
    Package* currentPackage,
    RuntimeSizedArrayDefinitionT* rsaDefM) {
  if (rsaDefM->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    auto name = currentPackage->getKindExportName(rsaDefM->kind, true);
    return std::string() + "typedef struct " + name + " { void* unused; } " + name + ";\n";
  }
}

std::string RCImm::generateStaticSizedArrayDefsC(
    Package* currentPackage,
    StaticSizedArrayDefinitionT* ssaDefM) {
  if (ssaDefM->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    auto name = currentPackage->getKindExportName(ssaDefM->kind, true);
    return std::string() + "typedef struct " + name + " { void* unused; } " + name + ";\n";
  }
}

LLVMTypeRef RCImm::getExternalType(Reference* refMT) {
  // Instance regions (unlike this one) return their handle types from this method.
  // For this region though, we don't give out handles, we give out copies.
  return globalState->linearRegion->translateType(globalState->linearRegion->linearizeReference(refMT));
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

  assert(hostRefMT->ownership == Ownership::SHARE);

  auto sourceRegion = globalState->getRegion(hostRefMT);
  assert(sourceRegion == globalState->linearRegion);
  auto sourceRefLE = sourceRegion->checkValidReference(FL(), functionState, builder, hostRefMT, sourceRef);

  if (dynamic_cast<Int*>(hostRefMT->kind)) {
    auto resultRef = wrap(globalState->getRegion(hostRefMT), valeRefMT, sourceRefLE);
    // Vale doesn't care about the size, only extern (linear) does, so just return zero.
    return std::make_pair(resultRef, globalState->constI32(0));
  } else if (dynamic_cast<Bool*>(hostRefMT->kind)) {
    auto asI1LE =
        LLVMBuildTrunc(
            builder, sourceRefLE, LLVMInt1TypeInContext(globalState->context), "boolAsI1");
    auto resultRef = wrap(this, valeRefMT, asI1LE);
    // Vale doesn't care about the size, only extern (linear) does, so just return zero.
    return std::make_pair(resultRef, globalState->constI32(0));
  } else if (dynamic_cast<Float*>(hostRefMT->kind)) {
    auto resultRef = wrap(globalState->getRegion(hostRefMT), valeRefMT, sourceRefLE);
    // Vale doesn't care about the size, only extern (linear) does, so just return zero.
    return std::make_pair(resultRef, globalState->constI32(0));
  } else if (dynamic_cast<Str*>(hostRefMT->kind)) {
    auto strLenLE = sourceRegion->getStringLen(functionState, builder, sourceRegionInstanceRef, sourceRef);
    auto strLenBytesPtrLE = sourceRegion->getStringBytesPtr(functionState, builder, sourceRegionInstanceRef, sourceRef);

    auto vstrRef =
        mallocStr(
            makeVoidRef(globalState), functionState, builder, strLenLE, strLenBytesPtrLE);

    buildFlare(FL(), globalState, functionState, builder, "done storing");

    sourceRegion->dealias(FL(), functionState, builder, hostRefMT, sourceRef);

    // Vale doesn't care about the size, only extern (linear) does, so just return zero.
    return std::make_pair(vstrRef, globalState->constI32(0));
  } else if (dynamic_cast<Str*>(hostRefMT->kind) ||
             dynamic_cast<StructKind*>(hostRefMT->kind) ||
             dynamic_cast<InterfaceKind*>(hostRefMT->kind) ||
             dynamic_cast<StaticSizedArrayT*>(hostRefMT->kind) ||
             dynamic_cast<RuntimeSizedArrayT*>(hostRefMT->kind)) {
    buildFlare(FL(), globalState, functionState, builder);
    if (hostRefMT->location == Location::INLINE) {
      if (hostRefMT == globalState->metalCache->voidRef) {
        auto emptyTupleRefMT = globalState->linearRegion->unlinearizeReference(globalState->metalCache->voidRef);
        auto resultRef = wrap(this, emptyTupleRefMT, LLVMGetUndef(translateType(emptyTupleRefMT)));
        // Vale doesn't care about the size, only extern (linear) does, so just return zero.
        return std::make_pair(resultRef, globalState->constI32(0));
      } else {
        assert(false);
      }
    } else {
      auto resultRef = topLevelUnserialize(functionState, builder, targetRegionInstanceRef, sourceRegionInstanceRef, valeRefMT->kind, sourceRef);
      // Vale doesn't care about the size, only extern (linear) does, so just return zero.
      return std::make_pair(resultRef, globalState->constI32(0));
    }
  } else assert(false);

  assert(false);
}

LLVMTypeRef RCImm::getInterfaceMethodVirtualParamAnyType(Reference* reference) {
  return LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
}

Ref RCImm::receiveAndDecryptFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    LLVMValueRef sourceRefLE) {
  assert(false);
  exit(1);
}

LLVMValueRef RCImm::encryptAndSendFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Ref sourceRef) {
  assert(false);
  exit(1);
}

void RCImm::initializeElementInSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Ref ssaRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto ssaDefM = globalState->program->getStaticSizedArray(ssaMT);
  auto elementType = ssaDefM->elementType;
  buildFlare(FL(), globalState, functionState, builder);
  regularInitializeElementInSSA(
      globalState, functionState, builder, &kindStructs, ssaRefMT,
      elementType, ssaDefM->size, ssaRef, indexRef, elementRef);
}

Ref RCImm::deinitializeElementFromSSA(
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

Weakability RCImm::getKindWeakability(Kind* kind) {
  return Weakability::NON_WEAKABLE;
}

void RCImm::declareExtraFunctions() {
  auto valeStrMT = globalState->metalCache->strRef;
  auto hostStrMT =
      globalState->metalCache->getReference(
          Ownership::SHARE,
          Location::YONDER,
          globalState->metalCache->getStr(globalState->metalCache->linearRegionId));
  auto prototype =
      globalState->metalCache->getPrototype(
          globalState->unserializeName, valeStrMT,
          {getRegionRefType(), globalState->linearRegion->getRegionRefType(), hostStrMT});
  auto nameL = globalState->unserializeName->name + "__str";
  declareExtraFunction(globalState, prototype, nameL);
}

void RCImm::defineExtraFunctions() {
  defineConcreteUnserializeFunction(globalState->metalCache->str);
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
            wrap(this, regionRefMT, LLVMGetParam(functionState->containingFuncL, 0));
        auto hostRegionInstanceRef =
            wrap(hostRegion, hostRegion->getRegionRefType(), LLVMGetParam(functionState->containingFuncL, 1));
        auto hostObjectRef =
            wrap(globalState->getRegion(hostObjectRefMT), hostObjectRefMT, LLVMGetParam(functionState->containingFuncL, 2));

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
            checkValidReference(FL(), functionState, builder, thunkPrototype->returnType, interfaceRef);
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
          Ownership::SHARE, Location::YONDER, valeKind);
  auto hostRefMT = globalState->linearRegion->linearizeReference(valeRefMT);
  auto hostRegionRefMT = globalState->linearRegion->getRegionRefType();
  return globalState->metalCache->getPrototype(
      globalState->unserializeName, valeRefMT, {regionRefMT, hostRegionRefMT, hostRefMT});
}

Prototype* RCImm::getUnserializeThunkPrototype(StructKind* valeStructKind, InterfaceKind* valeInterfaceKind) {
  auto boolMT = globalState->metalCache->boolRef;
  auto valeStructRefMT =
      globalState->metalCache->getReference(
          Ownership::SHARE, Location::YONDER, valeStructKind);
  auto hostStructRefMT = globalState->linearRegion->linearizeReference(valeStructRefMT);
  auto valeInterfaceRefMT =
      globalState->metalCache->getReference(
          Ownership::SHARE, Location::YONDER, valeInterfaceKind);
  auto hostInterfaceRefMT = globalState->linearRegion->linearizeReference(valeInterfaceRefMT);
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
        auto valeMemberRefMT = globalState->linearRegion->unlinearizeReference(hostMemberRefMT);
        auto hostMemberLE =
            globalState->getRegion(hostMemberRefMT)->checkValidReference(
                FL(), functionState, builder, hostMemberRefMT, hostMemberRef);
        if (dynamic_cast<Int*>(hostMemberRefMT->kind)) {
          return wrap(globalState->getRegion(valeMemberRefMT), valeMemberRefMT, hostMemberLE);
        } else if (dynamic_cast<Bool*>(hostMemberRefMT->kind)) {
          auto resultLE = LLVMBuildTrunc(builder, hostMemberLE, LLVMInt1TypeInContext(globalState->context), "boolAsI1");
          return wrap(globalState->getRegion(valeMemberRefMT), valeMemberRefMT, resultLE);
        } else if (dynamic_cast<Float*>(hostMemberRefMT->kind)) {
          return wrap(globalState->getRegion(valeMemberRefMT), valeMemberRefMT, hostMemberLE);
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
        } else assert(false);
      };

  defineFunctionBodyV(
      globalState, prototype,
      [&](FunctionState* functionState, LLVMBuilderRef builder) -> void {
        auto hostObjectRefMT = prototype->params[2];
        auto valeObjectRefMT = prototype->returnType;

        auto regionInstanceRef =
            wrap(this, regionRefMT, LLVMGetParam(functionState->containingFuncL, 0));
        auto hostRegionRefMT =
            globalState->linearRegion->getRegionRefType();
        auto hostRegionInstanceRef =
            wrap(globalState->linearRegion, hostRegionRefMT, LLVMGetParam(functionState->containingFuncL, 1));
        auto hostObjectRef =
            wrap(globalState->getRegion(hostObjectRefMT), hostObjectRefMT, LLVMGetParam(functionState->containingFuncL, 2));

        if (auto valeStructKind = dynamic_cast<StructKind *>(valeObjectRefMT->kind)) {
          auto hostStructKind = dynamic_cast<StructKind *>(hostObjectRefMT->kind);
          assert(hostStructKind);
          auto valeStructDefM = globalState->program->getStruct(valeStructKind);

          std::vector<Ref> memberRefs;

          for (int i = 0; i < valeStructDefM->members.size(); i++) {
            auto valeMemberM = valeStructDefM->members[i];
            auto valeMemberRefMT = valeMemberM->type;
            auto hostMemberRefMT = globalState->linearRegion->linearizeReference(valeMemberRefMT);
            auto hostMemberRef =
                globalState->getRegion(hostObjectRefMT)->loadMember(
                    functionState, builder, hostRegionInstanceRef, hostObjectRefMT, hostObjectRef, true,
                    i, hostMemberRefMT, hostMemberRefMT, valeMemberM->name);
            memberRefs.push_back(
                unserializeMemberOrElement(
                    functionState, builder, regionInstanceRef, hostRegionInstanceRef, hostMemberRefMT, hostMemberRef));
          }

          auto resultRef = allocate(makeVoidRef(globalState), FL(), functionState, builder, valeObjectRefMT, memberRefs);

//          // Remember, we're subtracting each size from a very large number, so its easier to round down
//          // to the next multiple of 16.
//          totalSizeIntLE = hexRoundDown(globalState, builder, totalSizeIntLE);

          auto resultRefLE = checkValidReference(FL(), functionState, builder, valeObjectRefMT, resultRef);

          LLVMBuildRet(builder, resultRefLE);
        } else if (dynamic_cast<Str*>(valeObjectRefMT->kind)) {
          auto lengthLE =
              globalState->getRegion(hostObjectRefMT)
                  ->getStringLen(functionState, builder, hostRegionInstanceRef, hostObjectRef);
          auto sourceBytesPtrLE =
              globalState->getRegion(hostObjectRefMT)->
                  getStringBytesPtr(functionState, builder, hostRegionInstanceRef, hostObjectRef);

          auto strRef = mallocStr(makeVoidRef(globalState), functionState, builder, lengthLE, sourceBytesPtrLE);

          buildFlare(FL(), globalState, functionState, builder, "done storing");

          LLVMBuildRet(
              builder, checkValidReference(FL(), functionState, builder, globalState->metalCache->strRef, strRef));
        } else if (auto valeRsaMT = dynamic_cast<RuntimeSizedArrayT *>(valeObjectRefMT->kind)) {
          auto valeRsaRefMT = valeObjectRefMT;
          auto hostRsaMT = dynamic_cast<RuntimeSizedArrayT *>(hostObjectRefMT->kind);
          assert(hostRsaMT);
          auto hostRsaRefMT = hostObjectRefMT;

          auto lengthRef =
              globalState->getRegion(hostObjectRefMT)
                  ->getRuntimeSizedArrayLength(
                      functionState, builder, hostRegionInstanceRef, hostObjectRefMT, hostObjectRef, true);

          auto valeRsaRef =
              constructRuntimeSizedArray(
                  makeVoidRef(globalState),
                  functionState, builder, valeRsaRefMT, valeRsaMT, lengthRef, "serializedrsa");
          auto valeMemberRefMT = globalState->program->getRuntimeSizedArray(valeRsaMT)->elementType;
          auto hostMemberRefMT = globalState->linearRegion->linearizeReference(valeMemberRefMT);

          intRangeLoopReverse(
              globalState, functionState, builder, globalState->metalCache->i32, lengthRef,

              [this, functionState, regionInstanceRef, hostRegionInstanceRef, hostObjectRefMT, valeRsaRef, hostMemberRefMT, valeObjectRefMT, hostRsaMT, valeRsaMT, hostObjectRef, valeMemberRefMT, unserializeMemberOrElement](
                  Ref indexRef, LLVMBuilderRef bodyBuilder){
                auto hostMemberRef =
                    globalState->getRegion(hostObjectRefMT)
                        ->loadElementFromRSA(functionState, bodyBuilder, hostRegionInstanceRef, hostObjectRefMT, hostRsaMT, hostObjectRef, true, indexRef)
                        .move();
                auto valeElementRef =
                    unserializeMemberOrElement(
                        functionState, bodyBuilder, regionInstanceRef, hostRegionInstanceRef, hostMemberRefMT, hostMemberRef);
                pushRuntimeSizedArrayNoBoundsCheck(
                    functionState, bodyBuilder, regionInstanceRef, valeObjectRefMT, valeRsaMT, valeRsaRef, true, indexRef, valeElementRef);
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

          intRangeLoopReverse(
              globalState, functionState, builder, globalState->metalCache->i32, globalState->constI32(length),
              [this, functionState, regionInstanceRef, hostRegionInstanceRef, hostObjectRefMT, valeSsaRef, valeObjectRefMT, hostSsaMT, valeSsaMT, hostObjectRef, valeMemberRefMT, unserializeMemberOrElement](
                  Ref indexRef, LLVMBuilderRef bodyBuilder){

                auto hostMemberRef =
                    globalState->getRegion(hostObjectRefMT)
                        ->loadElementFromSSA(
                            functionState, bodyBuilder, hostRegionInstanceRef, hostObjectRefMT, hostSsaMT, hostObjectRef, true, indexRef)
                        .move();
                auto hostMemberRefMT = globalState->linearRegion->linearizeReference(valeMemberRefMT);
                auto valeElementRef =
                    unserializeMemberOrElement(
                        functionState, bodyBuilder, regionInstanceRef, hostRegionInstanceRef, hostMemberRefMT, hostMemberRef);
                initializeElementInSSA(
                    functionState, bodyBuilder, regionInstanceRef, valeObjectRefMT, valeSsaMT, valeSsaRef, true, indexRef, valeElementRef);
              });


          LLVMBuildRet(builder, checkValidReference(FL(), functionState, builder, valeSsaRefMT, valeSsaRef));
        } else
          assert(false);
      });
}

LLVMValueRef RCImm::getInterfaceMethodFunctionPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    int indexInEdge) {
  return getInterfaceMethodFunctionPtrFromItable(
      globalState, functionState, builder, virtualParamMT, virtualArgRef, indexInEdge);
}

LLVMValueRef RCImm::stackify(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    Ref refToStore,
    bool knownLive) {
  auto toStoreLE = checkValidReference(FL(), functionState, builder, local->type, refToStore);
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
  auto linear = globalState->linearRegion->linearizeReference(reference);
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
  auto regionInstanceRef = wrap(this, regionRefMT, regionInstancePtrLE);

  return regionInstanceRef;
}
