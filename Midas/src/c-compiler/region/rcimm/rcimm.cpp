#include <function/expressions/shared/shared.h>
#include <utils/counters.h>
#include <utils/branch.h>
#include <region/common/controlblock.h>
#include <region/common/heap.h>
#include <function/expressions/shared/string.h>
#include <region/common/common.h>
#include <sstream>
#include <function/expressions/shared/elements.h>
#include "rcimm.h"
#include "translatetype.h"
#include "region/linear/linear.h"

void fillControlBlock(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    IReferendStructsSource* structs,
    LLVMBuilderRef builder,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName) {
  LLVMValueRef newControlBlockLE = LLVMGetUndef(structs->getControlBlock(referendM)->getStruct());
  newControlBlockLE =
      fillControlBlockCensusFields(
          from, globalState, functionState, structs, builder, referendM, newControlBlockLE, typeName);
  newControlBlockLE = insertStrongRc(globalState, builder, structs, referendM, newControlBlockLE);
  LLVMBuildStore(builder, newControlBlockLE, controlBlockPtrLE.refLE);
}

ControlBlock makeImmControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "immControlBlock"));
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

RCImm::RCImm(GlobalState* globalState_)
  : globalState(globalState_),
    referendStructs(globalState, makeImmControlBlock(globalState)) {
}

RegionId* RCImm::getRegionId() {
  return globalState->metalCache->rcImmRegionId;
}

void RCImm::alias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    Ref ref) {
  auto sourceRnd = sourceRef->referend;

  if (dynamic_cast<Int *>(sourceRnd) ||
      dynamic_cast<Bool *>(sourceRnd) ||
      dynamic_cast<Float *>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (dynamic_cast<InterfaceReferend *>(sourceRnd) ||
             dynamic_cast<StructReferend *>(sourceRnd) ||
             dynamic_cast<StaticSizedArrayT *>(sourceRnd) ||
             dynamic_cast<RuntimeSizedArrayT *>(sourceRnd) ||
             dynamic_cast<Str *>(sourceRnd)) {
    if (sourceRef->location == Location::INLINE) {
      // Do nothing, we can just let inline structs disappear
    } else {
      adjustStrongRc(from, globalState, functionState, &referendStructs, builder, ref, sourceRef, 1);
    }
  } else {
    std::cerr << "Unimplemented type in acquireReference: "
              << typeid(*sourceRef->referend).name() << std::endl;
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
  return regularDowncast(
      globalState, functionState, builder, thenResultIsNever, elseResultIsNever, resultOptTypeM, constraintRefM,
      sourceInterfaceRefMT, sourceInterfaceRef, sourceRefKnownLive, targetReferend, buildThen, buildElse);
}

LLVMTypeRef RCImm::translateType(Reference* referenceM) {
  return translateType(globalState, referenceM);
}

LLVMValueRef RCImm::getCensusObjectId(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref ref) {
  if (refM == globalState->metalCache->intRef) {
    return constI64LE(globalState, -2);
  } else if (refM == globalState->metalCache->boolRef) {
    return constI64LE(globalState, -3);
  } else if (refM == globalState->metalCache->neverRef) {
    return constI64LE(globalState, -4);
  } else if (refM == globalState->metalCache->floatRef) {
    return constI64LE(globalState, -5);
  } else if (refM->location == Location::INLINE) {
    return constI64LE(globalState, -1);
  } else {
    auto controlBlockPtrLE =
        referendStructs.getControlBlockPtr(checkerAFL, functionState, builder, ref, refM);
    auto exprLE =
        referendStructs.getObjIdFromControlBlockPtr(builder, refM->referend, controlBlockPtrLE);
    return exprLE;
  }
}

Ref RCImm::upcastWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {
  assert(false);
  exit(1);
}

void RCImm::declareStruct(
    StructDefinition* structM) {
  globalState->regionIdByReferend.emplace(structM->referend, getRegionId());

  referendStructs.declareStruct(structM->referend);
}

void RCImm::declareStructExtraFunctions(StructDefinition* structDefM) {
  declareConcreteUnserializeFunction(structDefM->referend);
}

void RCImm::defineStruct(
    StructDefinition* structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        globalState->getRegion(structM->members[i]->type)
            ->translateType(structM->members[i]->type));
  }
  referendStructs.defineStruct(structM->referend, innerStructMemberTypesL);
}

void RCImm::defineStructExtraFunctions(StructDefinition* structDefM) {
  defineConcreteUnserializeFunction(structDefM->referend);
}

void RCImm::declareStaticSizedArray(
    StaticSizedArrayDefinitionT* ssaDefM) {
  globalState->regionIdByReferend.emplace(ssaDefM->referend, getRegionId());

  referendStructs.declareStaticSizedArray(ssaDefM);
}

void RCImm::declareStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* ssaDef) {
  declareConcreteUnserializeFunction(ssaDef->referend);
}

void RCImm::defineStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT) {
  auto elementLT =
      translateType(
          staticSizedArrayMT->rawArray->elementType);
  referendStructs.defineStaticSizedArray(staticSizedArrayMT, elementLT);
}

void RCImm::defineStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* ssaDef) {
  defineConcreteUnserializeFunction(ssaDef->referend);
}

void RCImm::declareRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* rsaDefM) {
  globalState->regionIdByReferend.emplace(rsaDefM->referend, getRegionId());

  referendStructs.declareRuntimeSizedArray(rsaDefM);
}

void RCImm::declareRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* rsaDefM) {
  declareConcreteUnserializeFunction(rsaDefM->referend);
}

void RCImm::defineRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) {
  auto elementLT =
      translateType(
          runtimeSizedArrayMT->rawArray->elementType);
  referendStructs.defineRuntimeSizedArray(runtimeSizedArrayMT, elementLT);
}

void RCImm::defineRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* rsaDefM) {
  defineConcreteUnserializeFunction(rsaDefM->referend);
}

void RCImm::declareInterface(
    InterfaceDefinition* interfaceM) {
  globalState->regionIdByReferend.emplace(interfaceM->referend, getRegionId());

  referendStructs.declareInterface(interfaceM);
}

void RCImm::declareInterfaceExtraFunctions(InterfaceDefinition* interfaceDefM) {
  declareInterfaceUnserializeFunction(interfaceDefM->referend);
}

void RCImm::defineInterface(
    InterfaceDefinition* interfaceM) {
  auto interfaceMethodTypesL = globalState->getInterfaceFunctionTypes(interfaceM->referend);
  referendStructs.defineInterface(interfaceM, interfaceMethodTypesL);
}

void RCImm::defineInterfaceExtraFunctions(InterfaceDefinition* interfaceDefM) {
}

void RCImm::declareEdge(Edge* edge) {
  referendStructs.declareEdge(edge);

  auto hostStructMT = globalState->linearRegion->linearizeStructReferend(edge->structName);
  auto hostInterfaceMT = globalState->linearRegion->linearizeInterfaceReferend(edge->interfaceName);

  auto interfaceMethod = getUnserializeInterfaceMethod(edge->interfaceName);
  auto thunkPrototype = getUnserializeThunkPrototype(edge->structName, edge->interfaceName);
  globalState->addEdgeExtraMethod(hostInterfaceMT, hostStructMT, interfaceMethod, thunkPrototype);
  auto nameL = globalState->unserializeName->name + "__" + edge->interfaceName->fullName->name + "__" + edge->structName->fullName->name;
  declareExtraFunction(globalState, thunkPrototype, nameL);
}

void RCImm::defineEdge(Edge* edge) {
  auto interfaceM = globalState->program->getInterface(edge->interfaceName->fullName);

  auto interfaceFunctionsLT = globalState->getInterfaceFunctionTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  referendStructs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);

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
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetMemberType,
    const std::string& memberName) {
  auto memberLE =
      loadMember(
          functionState, builder, structRefMT, structRef, memberIndex, expectedMemberType,
          targetMemberType, memberName);
  auto resultRef =
      upgradeLoadResultToRefWithTargetOwnership(
          functionState, builder, expectedMemberType, targetMemberType, memberLE);
  return resultRef;
}

void RCImm::storeMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
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
      globalState, functionState, builder, &referendStructs, virtualParamMT, virtualArgRef);
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

LLVMValueRef RCImm::getStringBytesPtr(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) {
  auto strWrapperPtrLE =
      referendStructs.makeWrapperPtr(
          FL(), functionState, builder,
          globalState->metalCache->strRef,
          checkValidReference(
              FL(), functionState, builder, globalState->metalCache->strRef, ref));
  return referendStructs.getStringBytesPtr(functionState, builder, strWrapperPtrLE);
}

Ref RCImm::allocate(
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
                FL(), globalState, functionState, &referendStructs, innerBuilder, desiredReference->referend,
                controlBlockPtrLE, structM->name->name);
          });
  // Dont need to alias here because the RC starts at 1, see SRCAO
  return resultRef;
}

Ref RCImm::upcast(
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructMT,
    StructReferend* sourceStructReferendM,
    Ref sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  return upcastStrong(globalState, functionState, builder, &referendStructs, sourceStructMT, sourceStructReferendM, sourceRefLE, targetInterfaceTypeM, targetInterfaceReferendM);
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
    StaticSizedArrayT* referendM) {
  auto resultRef =
      ::constructStaticSizedArray(
          globalState, functionState, builder, referenceM, referendM, &referendStructs,
          [this, functionState, referenceM, referendM](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
//            fillControlBlock(
//                FL(),
//                functionState,
//                innerBuilder,
//                referenceM->referend,
//                referendM->rawArray->mutability,
//                controlBlockPtrLE,
//                referendM->name->name);
            fillControlBlock(
                FL(), globalState, functionState, &referendStructs, innerBuilder, referendM, controlBlockPtrLE,
                referendM->name->name);
          });
  // Dont need to alias here because the RC starts at 1, see SRCAO
  return resultRef;
}

Ref RCImm::getRuntimeSizedArrayLength(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    Ref arrayRef,
    bool arrayKnownLive) {
  return getRuntimeSizedArrayLengthStrong(globalState, functionState, builder, &referendStructs, rsaRefMT, arrayRef);
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
    checkValidReference(checkerAFL, functionState, builder, &referendStructs, refM, refLE);
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
  auto structReferend = dynamic_cast<StructReferend*>(refMT->referend);
  assert(structReferend);
  assert(LLVMTypeOf(argLE) == referendStructs.getInnerStruct(structReferend));
}

LoadResult RCImm::loadElementFromSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto ssaDef = globalState->program->getStaticSizedArray(ssaMT->name);
  return regularloadElementFromSSA(
      globalState, functionState, builder, ssaRefMT, ssaMT, ssaDef->rawArray->elementType, ssaDef->size, ssaDef->rawArray->mutability, arrayRef, arrayKnownLive, indexRef, &referendStructs);
}

LoadResult RCImm::loadElementFromRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT->name);
  return regularLoadElementFromRSAWithoutUpgrade(
      globalState, functionState, builder, &referendStructs, rsaRefMT, rsaMT, rsaDef->rawArray->mutability, rsaDef->rawArray->elementType, arrayRef,
      arrayKnownLive, indexRef);
}

Ref RCImm::deinitializeElementFromRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT->name);
  return regularLoadElementFromRSAWithoutUpgrade(
      globalState, functionState, builder, &referendStructs, rsaRefMT, rsaMT, rsaDef->rawArray->mutability, rsaDef->rawArray->elementType, arrayRef,
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

void RCImm::initializeElementInRSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *rsaRefMT,
    RuntimeSizedArrayT *rsaMT,
    Ref rsaRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto elementType = globalState->program->getRuntimeSizedArray(rsaMT->name)->rawArray->elementType;
  buildFlare(FL(), globalState, functionState, builder);

  auto arrayWrapperPtrLE =
      referendStructs.makeWrapperPtr(
          FL(), functionState, builder, rsaRefMT,
          globalState->getRegion(rsaRefMT)->checkValidReference(FL(), functionState, builder, rsaRefMT, rsaRef));
  auto sizeRef = ::getRuntimeSizedArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
  auto arrayElementsPtrLE = getRuntimeSizedArrayContentsPtr(builder, arrayWrapperPtrLE);
  ::initializeElement(
      globalState, functionState, builder, rsaRefMT->location,
      elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
}

void RCImm::deallocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  innerDeallocate(from, globalState, functionState, &referendStructs, builder, refMT, ref);
}


Ref RCImm::constructRuntimeSizedArray(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaMT,
    RuntimeSizedArrayT* runtimeSizedArrayT,
    Ref sizeRef,
    const std::string& typeName) {
  auto rsaWrapperPtrLT =
      referendStructs.getRuntimeSizedArrayWrapperStruct(runtimeSizedArrayT);
  auto rsaDef = globalState->program->getRuntimeSizedArray(runtimeSizedArrayT->name);
  auto elementType = globalState->program->getRuntimeSizedArray(runtimeSizedArrayT->name)->rawArray->elementType;
  auto rsaElementLT = globalState->getRegion(elementType)->translateType(elementType);
  buildFlare(FL(), globalState, functionState, builder);
  auto resultRef =
      ::constructRuntimeSizedArray(
          globalState, functionState, builder, &referendStructs, rsaMT, rsaDef->rawArray->elementType, runtimeSizedArrayT,
          rsaWrapperPtrLT, rsaElementLT, sizeRef, typeName,
          [this, functionState, runtimeSizedArrayT, rsaMT, typeName](
              LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
            fillControlBlock(
                FL(), globalState, functionState, &referendStructs, innerBuilder, runtimeSizedArrayT, controlBlockPtrLE,
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
          globalState, functionState, builder, lengthLE, sourceCharsPtrLE, &referendStructs,
          [this, functionState](LLVMBuilderRef innerBuilder, ControlBlockPtrLE controlBlockPtrLE) {
//            fillControlBlock(
//                FL(), functionState, innerBuilder, globalState->metalCache->str,
//                Mutability::IMMUTABLE, controlBlockPtrLE, "Str");
            fillControlBlock(
                FL(), globalState, functionState, &referendStructs, innerBuilder, globalState->metalCache->str, controlBlockPtrLE,
                "str");
          }));
  // Dont need to alias here because the RC starts at 1, see SRCAO
  return resultRef;
}

LLVMValueRef RCImm::getStringLen(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) {
  auto strWrapperPtrLE =
      referendStructs.makeWrapperPtr(
          FL(), functionState, builder,
          globalState->metalCache->strRef,
          checkValidReference(
              FL(), functionState, builder, globalState->metalCache->strRef, ref));
  return referendStructs.getStringLen(functionState, builder, strWrapperPtrLE);
}

void RCImm::discard(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  buildFlare(FL(), globalState, functionState, builder);
  auto sourceRnd = sourceMT->referend;

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
            from, globalState, functionState, &referendStructs, builder, sourceRef, sourceMT, -1);
    buildFlare(from, globalState, functionState, builder, "Str RC: ", rcLE);
    buildIf(
        globalState, functionState,
        builder,
        isZeroLE(builder, rcLE),
        [this, from, globalState, functionState, sourceRef, sourceMT](
            LLVMBuilderRef thenBuilder) {
          buildFlare(from, globalState, functionState, thenBuilder, "Freeing shared str!");
          innerDeallocate(from, globalState, functionState, &referendStructs, thenBuilder, sourceMT, sourceRef);
        });
  } else if (auto interfaceRnd = dynamic_cast<InterfaceReferend *>(sourceRnd)) {
    buildFlare(FL(), globalState, functionState, builder);
    assert(sourceMT->ownership == Ownership::SHARE);
    if (sourceMT->location == Location::INLINE) {
      assert(false); // impl
    } else {
      auto rcLE =
          adjustStrongRc(
              from, globalState, functionState, &referendStructs, builder, sourceRef, sourceMT, -1);
      buildIf(
          globalState, functionState,
          builder,
          isZeroLE(builder, rcLE),
          [globalState, functionState, sourceRef, interfaceRnd, sourceMT](
              LLVMBuilderRef thenBuilder) {
            auto immDestructor = globalState->program->getImmDestructor(sourceMT->referend);

//            auto virtualArgRefMT = functionType->params[virtualParamIndex];
//            auto virtualArgRef = argsLE[virtualParamIndex];
            int indexInEdge = globalState->getInterfaceMethodIndex(interfaceRnd, immDestructor);
            auto methodFunctionPtrLE =
                globalState->getRegion(sourceMT)
                    ->getInterfaceMethodFunctionPtr(functionState, thenBuilder, sourceMT, sourceRef, indexInEdge);
            buildInterfaceCall(
                globalState, functionState, thenBuilder, immDestructor, methodFunctionPtrLE, {sourceRef}, 0);
          });
    }
  } else if (dynamic_cast<StructReferend *>(sourceRnd) ||
      dynamic_cast<StaticSizedArrayT *>(sourceRnd) ||
      dynamic_cast<RuntimeSizedArrayT *>(sourceRnd)) {
    buildFlare(FL(), globalState, functionState, builder);
    if (auto sr = dynamic_cast<StructReferend *>(sourceRnd)) {
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
              from, globalState, functionState, &referendStructs, builder, sourceRef, sourceMT, -1);
      buildFlare(FL(), globalState, functionState, builder, rcLE);
      buildIf(
          globalState, functionState,
          builder,
          isZeroLE(builder, rcLE),
          [from, globalState, functionState, sourceRef, sourceMT](LLVMBuilderRef thenBuilder) {
            buildFlare(FL(), globalState, functionState, thenBuilder);
            auto immDestructor = globalState->program->getImmDestructor(sourceMT->referend);
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
        << typeid(*sourceMT->referend).name() << std::endl;
    assert(false);
  }
  buildFlare(FL(), globalState, functionState, builder);
}


LLVMTypeRef RCImm::translateType(GlobalState* globalState, Reference* referenceM) {
  if (primitives.isPrimitive(referenceM)) {
    return primitives.translatePrimitive(globalState, referenceM);
  } else {
    if (dynamic_cast<Str *>(referenceM->referend) != nullptr) {
      assert(referenceM->location != Location::INLINE);
      assert(referenceM->ownership == Ownership::SHARE);
      return LLVMPointerType(referendStructs.getStringWrapperStruct(), 0);
    } else if (auto staticSizedArrayMT = dynamic_cast<StaticSizedArrayT *>(referenceM->referend)) {
      assert(referenceM->location != Location::INLINE);
      auto staticSizedArrayCountedStructLT = referendStructs.getStaticSizedArrayWrapperStruct(staticSizedArrayMT);
      return LLVMPointerType(staticSizedArrayCountedStructLT, 0);
    } else if (auto runtimeSizedArrayMT =
        dynamic_cast<RuntimeSizedArrayT *>(referenceM->referend)) {
      assert(referenceM->location != Location::INLINE);
      auto runtimeSizedArrayCountedStructLT =
          referendStructs.getRuntimeSizedArrayWrapperStruct(runtimeSizedArrayMT);
      return LLVMPointerType(runtimeSizedArrayCountedStructLT, 0);
    } else if (auto structReferend =
        dynamic_cast<StructReferend *>(referenceM->referend)) {
      if (referenceM->location == Location::INLINE) {
        auto innerStructL = referendStructs.getInnerStruct(structReferend);
        return innerStructL;
      } else {
        auto countedStructL = referendStructs.getWrapperStruct(structReferend);
        return LLVMPointerType(countedStructL, 0);
      }
    } else if (auto interfaceReferend =
        dynamic_cast<InterfaceReferend *>(referenceM->referend)) {
      assert(referenceM->location != Location::INLINE);
      auto interfaceRefStructL =
          referendStructs.getInterfaceRefStruct(interfaceReferend);
      return interfaceRefStructL;
    } else if (dynamic_cast<Never*>(referenceM->referend)) {
      auto result = LLVMPointerType(makeNeverType(globalState), 0);
      assert(LLVMTypeOf(globalState->neverPtr) == result);
      return result;
    } else {
      std::cerr << "Unimplemented type: " << typeid(*referenceM->referend).name() << std::endl;
      assert(false);
      return nullptr;
    }
  }
}


LLVMTypeRef RCImm::getControlBlockStruct(Referend* referend) {
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    auto structM = globalState->program->getStruct(structReferend->fullName);
    assert(structM->mutability == Mutability::IMMUTABLE);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
    auto interfaceM = globalState->program->getInterface(interfaceReferend->fullName);
    assert(interfaceM->mutability == Mutability::IMMUTABLE);
  } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(referend)) {
    auto ssaDef = globalState->program->getStaticSizedArray(ssaMT->name);
    assert(ssaDef->rawArray->mutability == Mutability::IMMUTABLE);
  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(referend)) {
    auto rsaDef = globalState->program->getStaticSizedArray(rsaMT->name);
    assert(rsaDef->rawArray->mutability == Mutability::IMMUTABLE);
  } else if (auto strMT = dynamic_cast<Str*>(referend)) {
  } else {
    assert(false);
  }
  return referendStructs.getControlBlockStruct();
}


LoadResult RCImm::loadMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
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
    return regularLoadStrongMember(globalState, functionState, builder, &referendStructs, structRefMT, structRef, memberIndex, expectedMemberType, targetType, memberName);
  }
}

void RCImm::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* refM,
    LLVMValueRef refLE) {
  regularCheckValidReference(checkerAFL, globalState, functionState, builder, referendStructs, refM, refLE);
}

std::string RCImm::getMemberArbitraryRefNameCSeeMMEDT(Reference* sourceMT) {
  assert(false);
  exit(1);
}

void RCImm::generateStructDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName,
    StructDefinition* structDefM) {
  assert(false);
}

void RCImm::generateInterfaceDefsC(std::unordered_map<std::string, std::string>* cByExportedName, InterfaceDefinition* interfaceDefM) {
  assert(false);
}

void RCImm::generateRuntimeSizedArrayDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName,
    RuntimeSizedArrayDefinitionT* rsaDefM) {
  if (rsaDefM->rawArray->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    for (auto baseName : globalState->program->getExportedNames(rsaDefM->name)) {
      auto refTypeName = baseName + "Ref";
      std::stringstream s;
      s << "typedef struct " << refTypeName << " { void* unused; } " << refTypeName << ";" << std::endl;
      cByExportedName->insert(std::make_pair(baseName, s.str()));
    }
  }
}

void RCImm::generateStaticSizedArrayDefsC(
    std::unordered_map<std::string, std::string>* cByExportedName,
    StaticSizedArrayDefinitionT* ssaDefM) {
  if (ssaDefM->rawArray->mutability == Mutability::IMMUTABLE) {
    assert(false);
  } else {
    for (auto baseName : globalState->program->getExportedNames(ssaDefM->name)) {
      auto refTypeName = baseName + "Ref";
      std::stringstream s;
      s << "typedef struct " << refTypeName << " { void* unused; } " << refTypeName << ";" << std::endl;
      cByExportedName->insert(std::make_pair(baseName, s.str()));
    }
  }
}

Reference* RCImm::getExternalType(Reference* refMT) {
  // Instance regions (unlike this one) return their handle types from this method.
  // For this region though, we don't give out handles, we give out copies.
  return globalState->linearRegion->linearizeReference(refMT);
}


Ref RCImm::receiveUnencryptedAlienReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* hostRefMT,
    Reference* valeRefMT,
    Ref sourceRef) {
  assert(hostRefMT->ownership == Ownership::SHARE);

  auto sourceRegion = globalState->getRegion(hostRefMT);
  assert(sourceRegion == globalState->linearRegion);
  auto sourceRefLE = sourceRegion->checkValidReference(FL(), functionState, builder, hostRefMT, sourceRef);

  if (dynamic_cast<Int*>(hostRefMT->referend)) {
    return wrap(globalState->getRegion(hostRefMT), valeRefMT, sourceRefLE);
  } else if (dynamic_cast<Bool*>(hostRefMT->referend)) {
    auto asI1LE =
        LLVMBuildTrunc(
            builder, sourceRefLE, LLVMInt1TypeInContext(globalState->context), "boolAsI1");
    return wrap(this, valeRefMT, asI1LE);
  } else if (dynamic_cast<Float*>(hostRefMT->referend)) {
    return wrap(globalState->getRegion(hostRefMT), valeRefMT, sourceRefLE);
  } else if (dynamic_cast<Str*>(hostRefMT->referend)) {
    auto strLenLE = sourceRegion->getStringLen(functionState, builder, sourceRef);
    auto strLenBytesPtrLE = sourceRegion->getStringBytesPtr(functionState, builder, sourceRef);

    auto vstrRef =
        mallocStr(
            makeEmptyTupleRef(globalState), functionState, builder, strLenLE, strLenBytesPtrLE);

    buildFlare(FL(), globalState, functionState, builder, "done storing");

    sourceRegion->dealias(FL(), functionState, builder, hostRefMT, sourceRef);

    return vstrRef;
  } else if (dynamic_cast<Str*>(hostRefMT->referend) ||
             dynamic_cast<StructReferend*>(hostRefMT->referend) ||
             dynamic_cast<InterfaceReferend*>(hostRefMT->referend) ||
             dynamic_cast<StaticSizedArrayT*>(hostRefMT->referend) ||
             dynamic_cast<RuntimeSizedArrayT*>(hostRefMT->referend)) {
    if (hostRefMT->location == Location::INLINE) {
      if (hostRefMT == globalState->metalCache->emptyTupleStructRef) {
        auto emptyTupleRefMT = globalState->linearRegion->unlinearizeReference(globalState->metalCache->emptyTupleStructRef);
        return wrap(this, emptyTupleRefMT, LLVMGetUndef(translateType(emptyTupleRefMT)));
      } else {
        assert(false);
      }
    } else {
      return topLevelUnserialize(functionState, builder, valeRefMT->referend, sourceRef);
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
    Ref sourceRef) {
  assert(false);
  exit(1);
}

Ref RCImm::encryptAndSendFamiliarReference(
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
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Ref ssaRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  auto ssaDefM = globalState->program->getStaticSizedArray(ssaMT->name);
  auto elementType = ssaDefM->rawArray->elementType;
  buildFlare(FL(), globalState, functionState, builder);
  regularInitializeElementInSSA(
      globalState, functionState, builder, &referendStructs, ssaRefMT,
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

Weakability RCImm::getReferendWeakability(Referend* referend) {
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
          globalState->unserializeName, valeStrMT, {hostStrMT});
  auto nameL = globalState->unserializeName->name + "__str";
  declareExtraFunction(globalState, prototype, nameL);
}

void RCImm::defineExtraFunctions() {
  defineConcreteUnserializeFunction(globalState->metalCache->str);
}

void RCImm::defineEdgeUnserializeFunction(Edge* edge) {
  auto boolMT = globalState->metalCache->boolRef;

  auto thunkPrototype = getUnserializeThunkPrototype(edge->structName, edge->interfaceName);
  defineFunctionBody(
      globalState, thunkPrototype,
      [&](FunctionState* functionState, LLVMBuilderRef builder) {
        auto structPrototype = getUnserializePrototype(edge->structName);

        auto hostObjectRefMT = structPrototype->params[0];
        auto valeObjectRefMT = structPrototype->returnType;

        auto hostObjectRef = wrap(globalState->getRegion(hostObjectRefMT), hostObjectRefMT, LLVMGetParam(functionState->containingFuncL, 0));

        auto valeStructRef = buildCall(globalState, functionState, builder, structPrototype, {hostObjectRef});

        auto valeInterfaceReferend = dynamic_cast<InterfaceReferend*>(thunkPrototype->returnType->referend);
        assert(valeInterfaceReferend);
        auto valeStructReferend = dynamic_cast<StructReferend*>(structPrototype->returnType->referend);
        assert(valeStructReferend);

        auto interfaceRef =
            upcast(
                functionState, builder, structPrototype->returnType, valeStructReferend,
                valeStructRef, thunkPrototype->returnType, valeInterfaceReferend);
        auto interfaceRefLE = checkValidReference(FL(), functionState, builder, thunkPrototype->returnType, interfaceRef);
        LLVMBuildRet(builder, interfaceRefLE);
      });
}

void RCImm::declareInterfaceUnserializeFunction(InterfaceReferend* valeInterface) {
  auto hostReferend = globalState->linearRegion->linearizeReferend(valeInterface);
  auto hostInterface = dynamic_cast<InterfaceReferend*>(hostReferend);
  auto interfaceMethod = getUnserializeInterfaceMethod(valeInterface);
  globalState->addInterfaceExtraMethod(hostInterface, interfaceMethod);
}

Ref RCImm::topLevelUnserialize(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* valeReferend,
    Ref ref) {
  return callUnserialize(functionState, builder, valeReferend, ref);
}

InterfaceMethod* RCImm::getUnserializeInterfaceMethod(Referend* valeReferend) {
  return globalState->metalCache->getInterfaceMethod(
      getUnserializePrototype(valeReferend), 0);
}

Ref RCImm::callUnserialize(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Referend* valeReferend,
    Ref objectRef) {
  auto prototype = getUnserializePrototype(valeReferend);
  if (auto valeInterfaceMT = dynamic_cast<InterfaceReferend*>(valeReferend)) {
    auto hostInterfaceMT = globalState->linearRegion->linearizeInterfaceReferend(valeInterfaceMT);
    auto hostVirtualArgRefMT = prototype->params[0];
    int indexInEdge = globalState->getInterfaceMethodIndex(hostInterfaceMT, prototype);
    auto methodFunctionPtrLE =
        globalState->getRegion(hostVirtualArgRefMT)
            ->getInterfaceMethodFunctionPtr(functionState, builder, hostVirtualArgRefMT, objectRef, indexInEdge);
    return buildInterfaceCall(globalState, functionState, builder, prototype, methodFunctionPtrLE, {objectRef}, 0);
  } else {
    return buildCall(globalState, functionState, builder, prototype, {objectRef});
  }
}

Prototype* RCImm::getUnserializePrototype(Referend* valeReferend) {
  auto boolMT = globalState->metalCache->boolRef;
  auto valeRefMT =
      globalState->metalCache->getReference(
          Ownership::SHARE, Location::YONDER, valeReferend);
  auto hostRefMT = globalState->linearRegion->linearizeReference(valeRefMT);
  return globalState->metalCache->getPrototype(globalState->unserializeName, valeRefMT, {hostRefMT});
}

Prototype* RCImm::getUnserializeThunkPrototype(StructReferend* valeStructReferend, InterfaceReferend* valeInterfaceReferend) {
  auto boolMT = globalState->metalCache->boolRef;
  auto valeStructRefMT =
      globalState->metalCache->getReference(
          Ownership::SHARE, Location::YONDER, valeStructReferend);
  auto hostStructRefMT = globalState->linearRegion->linearizeReference(valeStructRefMT);
  auto valeInterfaceRefMT =
      globalState->metalCache->getReference(
          Ownership::SHARE, Location::YONDER, valeInterfaceReferend);
  auto hostInterfaceRefMT = globalState->linearRegion->linearizeReference(valeInterfaceRefMT);
  return globalState->metalCache->getPrototype(
      globalState->unserializeThunkName, valeInterfaceRefMT,
      {hostStructRefMT});
}

void RCImm::declareConcreteUnserializeFunction(Referend* valeReferend) {
  auto prototype = getUnserializePrototype(valeReferend);
  auto nameL = globalState->unserializeName->name + "__" + globalState->getReferendName(valeReferend)->name;
  declareExtraFunction(globalState, prototype, nameL);
}

void RCImm::defineConcreteUnserializeFunction(Referend* valeReferend) {
  auto intMT = globalState->metalCache->intRef;
  auto boolMT = globalState->metalCache->boolRef;

  auto prototype = getUnserializePrototype(valeReferend);

  auto unserializeMemberOrElement =
      [this](
          FunctionState* functionState,
          LLVMBuilderRef builder,
          Reference* hostMemberRefMT,
          Ref hostMemberRef) {
        auto valeMemberRefMT = globalState->linearRegion->unlinearizeReference(hostMemberRefMT);
        auto hostMemberLE =
            globalState->getRegion(hostMemberRefMT)->checkValidReference(
                FL(), functionState, builder, hostMemberRefMT, hostMemberRef);
        if (dynamic_cast<Int*>(hostMemberRefMT->referend)) {
          return wrap(globalState->getRegion(valeMemberRefMT), valeMemberRefMT, hostMemberLE);
        } else if (dynamic_cast<Bool*>(hostMemberRefMT->referend)) {
          auto resultLE = LLVMBuildTrunc(builder, hostMemberLE, LLVMInt1TypeInContext(globalState->context), "boolAsI1");
          return wrap(globalState->getRegion(valeMemberRefMT), valeMemberRefMT, resultLE);
        } else if (dynamic_cast<Float*>(hostMemberRefMT->referend)) {
          return wrap(globalState->getRegion(valeMemberRefMT), valeMemberRefMT, hostMemberLE);
        } else if (
            dynamic_cast<Str*>(hostMemberRefMT->referend) ||
            dynamic_cast<StructReferend*>(hostMemberRefMT->referend) ||
            dynamic_cast<StaticSizedArrayT*>(hostMemberRefMT->referend) ||
            dynamic_cast<RuntimeSizedArrayT*>(hostMemberRefMT->referend)) {
          auto destinationMemberRef =
              callUnserialize(
                  functionState, builder, valeMemberRefMT->referend, hostMemberRef);
          return destinationMemberRef;
        } else if (dynamic_cast<InterfaceReferend*>(hostMemberRefMT->referend)) {
          auto destinationMemberRef =
              callUnserialize(
                  functionState, builder, valeMemberRefMT->referend, hostMemberRef);
          return destinationMemberRef;
        } else assert(false);
      };

  defineFunctionBody(
      globalState, prototype,
      [&](FunctionState* functionState, LLVMBuilderRef builder) -> void {
        auto hostObjectRefMT = prototype->params[0];
        auto valeObjectRefMT = prototype->returnType;

        auto hostObjectRef = wrap(globalState->getRegion(hostObjectRefMT), hostObjectRefMT, LLVMGetParam(functionState->containingFuncL, 0));

        if (auto valeStructReferend = dynamic_cast<StructReferend*>(valeObjectRefMT->referend)) {
          auto hostStructReferend = dynamic_cast<StructReferend*>(hostObjectRefMT->referend);
          assert(hostStructReferend);
          auto valeStructDefM = globalState->program->getStruct(valeStructReferend->fullName);

          std::vector<Ref> memberRefs;

          for (int i = 0; i < valeStructDefM->members.size(); i++) {
            auto valeMemberM = valeStructDefM->members[i];
            auto valeMemberRefMT = valeMemberM->type;
            auto hostMemberRefMT = globalState->linearRegion->linearizeReference(valeMemberRefMT);
            auto hostMemberRef =
                globalState->getRegion(hostObjectRefMT)->loadMember(
                    functionState, builder, hostObjectRefMT, hostObjectRef, true,
                    i, hostMemberRefMT, hostMemberRefMT, valeMemberM->name);
            memberRefs.push_back(
                unserializeMemberOrElement(
                    functionState, builder, hostMemberRefMT, hostMemberRef));
          }

          auto resultRef = allocate(makeEmptyTupleRef(globalState), FL(), functionState, builder, valeObjectRefMT, memberRefs);

//          // Remember, we're subtracting each size from a very large number, so its easier to round down
//          // to the next multiple of 16.
//          totalSizeIntLE = hexRoundDown(globalState, builder, totalSizeIntLE);

          auto resultRefLE = checkValidReference(FL(), functionState, builder, valeObjectRefMT, resultRef);

          LLVMBuildRet(builder, resultRefLE);
        } else if (dynamic_cast<Str*>(valeObjectRefMT->referend)) {
          auto lengthLE = globalState->getRegion(hostObjectRefMT)->getStringLen(functionState, builder, hostObjectRef);
          auto sourceBytesPtrLE = globalState->getRegion(hostObjectRefMT)->getStringBytesPtr(functionState, builder, hostObjectRef);

          auto strRef = mallocStr(makeEmptyTupleRef(globalState), functionState, builder, lengthLE, sourceBytesPtrLE);

          buildFlare(FL(), globalState, functionState, builder, "done storing");

          LLVMBuildRet(builder, checkValidReference(FL(), functionState, builder, globalState->metalCache->strRef, strRef));
        } else if (auto valeUsaMT = dynamic_cast<RuntimeSizedArrayT*>(valeObjectRefMT->referend)) {
          auto valeUsaRefMT = valeObjectRefMT;
          auto hostUsaMT = dynamic_cast<RuntimeSizedArrayT*>(hostObjectRefMT->referend);
          assert(hostUsaMT);
          auto hostUsaRefMT = hostObjectRefMT;

          auto lengthRef = globalState->getRegion(hostObjectRefMT)->getRuntimeSizedArrayLength(functionState, builder, hostObjectRefMT, hostObjectRef, true);

          auto valeUsaRef =
              constructRuntimeSizedArray(
                  makeEmptyTupleRef(globalState),
                  functionState, builder, valeUsaRefMT, valeUsaMT, lengthRef, "serializedrsa");
          auto valeMemberRefMT = globalState->program->getRuntimeSizedArray(valeUsaMT->name)->rawArray->elementType;
          auto hostMemberRefMT = globalState->linearRegion->linearizeReference(valeMemberRefMT);

          intRangeLoopReverse(
              globalState, functionState, builder, lengthRef,
              [this, functionState, hostObjectRefMT, valeUsaRef, hostMemberRefMT, valeObjectRefMT, hostUsaMT, valeUsaMT, hostObjectRef, valeMemberRefMT, unserializeMemberOrElement](
                  Ref indexRef, LLVMBuilderRef bodyBuilder){
                auto hostMemberRef =
                    globalState->getRegion(hostObjectRefMT)
                        ->loadElementFromRSA(functionState, bodyBuilder, hostObjectRefMT, hostUsaMT, hostObjectRef, true, indexRef)
                        .move();
                auto valeElementRef =
                    unserializeMemberOrElement(
                        functionState, bodyBuilder, hostMemberRefMT, hostMemberRef);
                initializeElementInRSA(
                    functionState, bodyBuilder, valeObjectRefMT, valeUsaMT, valeUsaRef, true, indexRef, valeElementRef);
              });

          LLVMBuildRet(builder, checkValidReference(FL(), functionState, builder, valeUsaRefMT, valeUsaRef));
        } else if (auto valeKsaMT = dynamic_cast<StaticSizedArrayT*>(valeObjectRefMT->referend)) {
          auto hostKsaMT = dynamic_cast<StaticSizedArrayT*>(hostObjectRefMT->referend);
          assert(hostKsaMT);
          auto valeKsaRefMT = valeObjectRefMT;
          auto hostKsaRefMT = hostObjectRefMT;

          auto valeKsaRef =
              constructStaticSizedArray(
                  makeEmptyTupleRef(globalState),
                  functionState, builder, valeKsaRefMT, valeKsaMT);
          auto valeKsaDefM = globalState->program->getStaticSizedArray(valeKsaMT->name);
          int length = valeKsaDefM->size;
          auto valeMemberRefMT = valeKsaDefM->rawArray->elementType;

          intRangeLoopReverse(
              globalState, functionState, builder, globalState->constI64(length),
              [this, functionState, hostObjectRefMT, valeKsaRef, valeObjectRefMT, hostKsaMT, valeKsaMT, hostObjectRef, valeMemberRefMT, unserializeMemberOrElement](
                  Ref indexRef, LLVMBuilderRef bodyBuilder){

                auto hostMemberRef =
                    globalState->getRegion(hostObjectRefMT)
                        ->loadElementFromSSA(functionState, bodyBuilder, hostObjectRefMT, hostKsaMT, hostObjectRef, true, indexRef)
                        .move();
                auto hostMemberRefMT = globalState->linearRegion->linearizeReference(valeMemberRefMT);
                auto valeElementRef =
                    unserializeMemberOrElement(
                        functionState, bodyBuilder, hostMemberRefMT, hostMemberRef);
                initializeElementInSSA(
                    functionState, bodyBuilder, valeObjectRefMT, valeKsaMT, valeKsaRef, true, indexRef, valeElementRef);
              });


          LLVMBuildRet(builder, checkValidReference(FL(), functionState, builder, valeKsaRefMT, valeKsaRef));
        } else assert(false);
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
  return makeMidasLocal(functionState, builder, typeLT, local->id->maybeName.c_str(), toStoreLE);
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
