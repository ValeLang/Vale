#include <function/expressions/shared/shared.h>
#include <utils/counters.h>
#include <utils/branch.h>
#include <region/common/controlblock.h>
#include <region/common/heap.h>
#include <function/expressions/shared/string.h>
#include <region/common/common.h>
#include <sstream>
#include <function/expressions/shared/elements.h>
#include <midasfunctions.h>
#include "linear.h"
#include "translatetype.h"
#include "region/rcimm/rcimm.h"

Ref unsafeCast(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* desiredRefMT,
    Ref sourceRef) {
  auto sourcePtrLE = globalState->getRegion(sourceRefMT)->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef);
  auto desiredPtrLT = globalState->getRegion(sourceRefMT)->translateType(desiredRefMT);
  auto desiredPtrLE = LLVMBuildPointerCast(builder, sourcePtrLE, desiredPtrLT, "destStructPtr");
  auto desiredRef = wrap(globalState->getRegion(desiredRefMT), desiredRefMT, desiredPtrLE);
  return desiredRef;
}

LLVMValueRef hexRoundDown(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef n) {
  // Mask off the last four bits, to round downward to the next multiple of 16.
  auto mask = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), ~0xFULL, false);
  return LLVMBuildAnd(builder, n, mask, "rounded");
}

LLVMValueRef lowerAndHexRoundDownPointer(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef rawPtrLE,
    LLVMValueRef subtractIntLE) {
  auto rawPtrIntLE =
      LLVMBuildPtrToInt(
          builder, rawPtrLE, LLVMInt64TypeInContext(globalState->context), "rawPtrInt");
  auto loweredRawPointerIntLE = LLVMBuildSub(builder, rawPtrIntLE, subtractIntLE, "loweredRawPtrInt");
  auto roundedLoweredRawPointerIntLE = hexRoundDown(globalState, builder, loweredRawPointerIntLE);
  return LLVMBuildIntToPtr(builder, roundedLoweredRawPointerIntLE, LLVMTypeOf(rawPtrLE), "loweredRoundedRawPtr");
}

Linear::Linear(GlobalState* globalState_)
  : globalState(globalState_),
    structs(globalState_),
    hostKindByValeKind(0, globalState->addressNumberer->makeHasher<Kind*>()),
    valeKindByHostKind(0, globalState->addressNumberer->makeHasher<Kind*>()) {

  regionKind =
      globalState->metalCache->getStructKind(
          globalState->metalCache->getName(
              globalState->metalCache->builtinPackageCoord, namePrefix + "_Region"));
  regionRefMT =
      globalState->metalCache->getReference(
          Ownership::BORROW, Location::YONDER, regionKind);
  globalState->regionIdByKind.emplace(regionKind, globalState->metalCache->linearRegionId);
  structs.declareStruct(regionKind);
  structs.defineStruct(regionKind, {
      // Pointer to the beginning of the destination buffer
      LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0),
      // Offset into the destination buffer to write to
      LLVMInt64TypeInContext(globalState->context),
      // "rootMetadataBytesNeeded", the number of bytes needed after the next thing is serialized, see MAPOWN.
      LLVMInt64TypeInContext(globalState->context),
  });

  startMetadataKind =
      globalState->metalCache->getStructKind(
          globalState->metalCache->getName(
              globalState->metalCache->builtinPackageCoord, namePrefix + "_StartMetadata"));
  startMetadataRefMT =
      globalState->metalCache->getReference(
          Ownership::BORROW, Location::YONDER, startMetadataKind);
  globalState->regionIdByKind.emplace(startMetadataKind, globalState->metalCache->linearRegionId);
  structs.declareStruct(startMetadataKind);
  structs.defineStruct(startMetadataKind, {
      // Size
      LLVMInt64TypeInContext(globalState->context),
      // Start address, to subtract from all pointers
      LLVMInt64TypeInContext(globalState->context),
      // Root object address, to start reading from
      LLVMInt64TypeInContext(globalState->context),
  });

  rootMetadataKind =
      globalState->metalCache->getStructKind(
          globalState->metalCache->getName(
              globalState->metalCache->builtinPackageCoord, namePrefix + "_RootMetadata"));
  rootMetadataRefMT =
      globalState->metalCache->getReference(
          Ownership::BORROW, Location::YONDER, rootMetadataKind);
  globalState->regionIdByKind.emplace(rootMetadataKind, globalState->metalCache->linearRegionId);
  structs.declareStruct(rootMetadataKind);
  structs.defineStruct(rootMetadataKind, {
      LLVMInt64TypeInContext(globalState->context),
      LLVMInt64TypeInContext(globalState->context),
  });

  linearStr = globalState->metalCache->getStr(globalState->metalCache->linearRegionId);
  linearStrRefMT =
      globalState->metalCache->getReference(
          Ownership::SHARE, Location::YONDER, linearStr);

  addMappedKind(globalState->metalCache->i32, globalState->metalCache->getInt(getRegionId(), 32));
  addMappedKind(globalState->metalCache->i64, globalState->metalCache->getInt(getRegionId(), 64));
  addMappedKind(globalState->metalCache->boool, globalState->metalCache->getBool(getRegionId()));
  addMappedKind(globalState->metalCache->flooat, globalState->metalCache->getFloat(getRegionId()));
  addMappedKind(globalState->metalCache->str, linearStr);
  addMappedKind(globalState->metalCache->never, globalState->metalCache->getNever(getRegionId()));
}

void Linear::declareExtraFunctions() {
  auto boolMT = globalState->metalCache->boolRef;
  auto valeStrMT = globalState->metalCache->strRef;
  auto prototype =
      globalState->metalCache->getPrototype(
          globalState->serializeName, linearStrRefMT, {regionRefMT, valeStrMT, boolMT});
  auto nameL = globalState->serializeName->name + "__str";
  declareExtraFunction(globalState, prototype, nameL);
}

void Linear::defineExtraFunctions() {
  defineConcreteSerializeFunction(globalState->metalCache->str);
}

RegionId* Linear::getRegionId() {
  return globalState->metalCache->linearRegionId;
}

void Linear::alias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    Ref ref) {
  assert(false); // impl
}

void Linear::dealias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto sourceRefLE = checkValidReference(FL(), functionState, builder, sourceMT, sourceRef);

  auto sourceI8PtrLE =
      LLVMBuildPointerCast(
          builder,
          sourceRefLE,
          LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0),
          "extStrPtrLE");

  LLVMBuildCall(builder, globalState->externs->free, &sourceI8PtrLE, 1, "");
}

Ref Linear::lockWeak(
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

Ref Linear::asSubtype(
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
  assert(false);
}

LLVMTypeRef Linear::translateType(Reference* referenceM) {
  if (auto innt = dynamic_cast<Int*>(referenceM->kind)) {
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMIntTypeInContext(globalState->context, innt->bits);
  } else if (dynamic_cast<Bool*>(referenceM->kind) != nullptr) {
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMInt8TypeInContext(globalState->context);
  } else if (dynamic_cast<Float*>(referenceM->kind) != nullptr) {
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMDoubleTypeInContext(globalState->context);
  } else if (dynamic_cast<Never*>(referenceM->kind) != nullptr) {
    return LLVMArrayType(LLVMIntTypeInContext(globalState->context, NEVER_INT_BITS), 0);
  } else if (dynamic_cast<Str *>(referenceM->kind) != nullptr) {
    assert(referenceM->location != Location::INLINE);
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMPointerType(structs.getStringStruct(), 0);
  } else if (auto staticSizedArrayMT = dynamic_cast<StaticSizedArrayT *>(referenceM->kind)) {
    assert(referenceM->location != Location::INLINE);
    auto staticSizedArrayCountedStructLT = structs.getStaticSizedArrayStruct(staticSizedArrayMT);
    return LLVMPointerType(staticSizedArrayCountedStructLT, 0);
  } else if (auto runtimeSizedArrayMT =
      dynamic_cast<RuntimeSizedArrayT *>(referenceM->kind)) {
    assert(referenceM->location != Location::INLINE);
    auto runtimeSizedArrayCountedStructLT =
        structs.getRuntimeSizedArrayStruct(runtimeSizedArrayMT);
    return LLVMPointerType(runtimeSizedArrayCountedStructLT, 0);
  } else if (auto structKind =
      dynamic_cast<StructKind *>(referenceM->kind)) {
    if (referenceM->location == Location::INLINE) {
      auto innerStructL = structs.getStructStruct(structKind);
      return innerStructL;
    } else {
      auto countedStructL = structs.getStructStruct(structKind);
      return LLVMPointerType(countedStructL, 0);
    }
  } else if (auto interfaceKind =
      dynamic_cast<InterfaceKind *>(referenceM->kind)) {
    assert(referenceM->location != Location::INLINE);
    auto interfaceRefStructL =
        structs.getInterfaceRefStruct(interfaceKind);
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

LLVMValueRef Linear::getCensusObjectId(
    AreaAndFileAndLine checkerAFL,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *refM,
    Ref ref) {
  return constI64LE(globalState, 0);
}

Ref Linear::upcastWeak(
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

void Linear::declareStaticSizedArray(
    StaticSizedArrayDefinitionT* ssaDefM) {

  auto hostName =
      globalState->metalCache->getName(
          globalState->metalCache->builtinPackageCoord, namePrefix + "_" + ssaDefM->name->name);
  auto hostKind = globalState->metalCache->getStaticSizedArray(hostName);
  addMappedKind(ssaDefM->kind, hostKind);
  globalState->regionIdByKind.emplace(hostKind, getRegionId());

  structs.declareStaticSizedArray(hostKind);
}

void Linear::defineStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT) {
  auto ssaDef = globalState->program->getStaticSizedArray(staticSizedArrayMT->kind);
  auto elementLT =
      translateType(
          linearizeReference(
              staticSizedArrayMT->rawArray->elementType));
  auto hostKind = hostKindByValeKind.find(staticSizedArrayMT->kind)->second;
  auto hostKsaMT = dynamic_cast<StaticSizedArrayT*>(hostKind);
  assert(hostKsaMT);

  structs.defineStaticSizedArray(hostKsaMT, ssaDef->size, elementLT);
}

void Linear::declareStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* ssaDef) {
  declareConcreteSerializeFunction(ssaDef->kind);
}

void Linear::defineStaticSizedArrayExtraFunctions(StaticSizedArrayDefinitionT* ssaDef) {
  defineConcreteSerializeFunction(ssaDef->kind);
}

void Linear::declareRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* rsaDefM) {
  auto hostName =
      globalState->metalCache->getName(
          globalState->metalCache->builtinPackageCoord, namePrefix + "_" + rsaDefM->name->name);
  auto hostKind = globalState->metalCache->getRuntimeSizedArray(hostName);
  addMappedKind(rsaDefM->kind, hostKind);
  globalState->regionIdByKind.emplace(hostKind, getRegionId());

  structs.declareRuntimeSizedArray(hostKind);
}

void Linear::defineRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) {
  auto elementLT =
      translateType(
          linearizeReference(
              runtimeSizedArrayMT->rawArray->elementType));
  auto hostKind = hostKindByValeKind.find(runtimeSizedArrayMT->kind)->second;
  auto hostUsaMT = dynamic_cast<RuntimeSizedArrayT*>(hostKind);
  assert(hostUsaMT);
  structs.defineRuntimeSizedArray(hostUsaMT, elementLT);
}

void Linear::declareRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* rsaDefM) {
  declareConcreteSerializeFunction(rsaDefM->kind);
}

void Linear::defineRuntimeSizedArrayExtraFunctions(RuntimeSizedArrayDefinitionT* rsaDefM) {
  defineConcreteSerializeFunction(rsaDefM->kind);
}

void Linear::declareStruct(
    StructDefinition* structM) {

  auto hostName =
      globalState->metalCache->getName(
          globalState->metalCache->builtinPackageCoord, namePrefix + "_" + structM->name->name);
  auto hostKind = globalState->metalCache->getStructKind(hostName);
  addMappedKind(structM->kind, hostKind);
  globalState->regionIdByKind.emplace(hostKind, getRegionId());

  structs.declareStruct(hostKind);
}

void Linear::declareStructExtraFunctions(StructDefinition* structDefM) {
  declareConcreteSerializeFunction(structDefM->kind);
}

void Linear::defineStruct(
    StructDefinition* structM) {
  auto hostKind = hostKindByValeKind.find(structM->kind)->second;
  auto hostStructMT = dynamic_cast<StructKind*>(hostKind);
  assert(hostStructMT);

  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        translateType(linearizeReference(structM->members[i]->type)));
  }
  structs.defineStruct(hostStructMT, innerStructMemberTypesL);
}

void Linear::defineStructExtraFunctions(StructDefinition* structDefM) {
  defineConcreteSerializeFunction(structDefM->kind);
}

void Linear::declareEdge(Edge* edge) {
  auto hostStructKind = linearizeStructKind(edge->structName);
  auto hostInterfaceKind = linearizeInterfaceKind(edge->interfaceName);

  structs.declareEdge(hostStructKind, hostInterfaceKind);

  auto interfaceMethod = getSerializeInterfaceMethod(edge->interfaceName);
  auto thunkPrototype = getSerializeThunkPrototype(edge->structName, edge->interfaceName);
  globalState->addEdgeExtraMethod(edge->interfaceName, edge->structName, interfaceMethod, thunkPrototype);
  auto nameL = globalState->serializeName->name + "__" + edge->interfaceName->fullName->name + "__" + edge->structName->fullName->name;
  declareExtraFunction(globalState, thunkPrototype, nameL);
}

void Linear::defineEdge(Edge* edge) {
//  auto interfaceM = globalState->program->getInterface(edge->interfaceName->fullName);

  auto interfaceFunctionsLT = globalState->getInterfaceFunctionTypes(edge->interfaceName);
  auto edgeFunctionsL = globalState->getEdgeFunctions(edge);
  structs.defineEdge(edge, interfaceFunctionsLT, edgeFunctionsL);

  defineEdgeSerializeFunction(edge);
}

void Linear::defineEdgeSerializeFunction(Edge* edge) {
  auto boolMT = globalState->metalCache->boolRef;

  auto thunkPrototype = getSerializeThunkPrototype(edge->structName, edge->interfaceName);
  defineFunctionBody(
      globalState, thunkPrototype,
      [this, boolMT, thunkPrototype, edge](FunctionState* functionState, LLVMBuilderRef builder) {
        auto structPrototype = getSerializePrototype(edge->structName);

        auto valeObjectRefMT = structPrototype->params[1];

        auto regionInstanceRef = wrap(globalState->getRegion(regionRefMT), regionRefMT, LLVMGetParam(functionState->containingFuncL, 0));
        auto valeObjectRef = wrap(globalState->getRegion(valeObjectRefMT), valeObjectRefMT, LLVMGetParam(functionState->containingFuncL, 1));
        auto dryRunBoolRef = wrap(globalState->getRegion(boolMT), boolMT, LLVMGetParam(functionState->containingFuncL, 2));

        auto structRef = buildCall(globalState, functionState, builder, structPrototype, {regionInstanceRef, valeObjectRef, dryRunBoolRef});

        auto hostInterfaceKind = dynamic_cast<InterfaceKind*>(thunkPrototype->returnType->kind);
        assert(hostInterfaceKind);
        auto hostStructKind = dynamic_cast<StructKind*>(structPrototype->returnType->kind);
        assert(hostStructKind);

        auto interfaceRef =
            upcast(
                functionState, builder, structPrototype->returnType, hostStructKind,
                structRef, thunkPrototype->returnType, hostInterfaceKind);
        auto interfaceRefLE = checkValidReference(FL(), functionState, builder, thunkPrototype->returnType, interfaceRef);
        LLVMBuildRet(builder, interfaceRefLE);
      });
}

void Linear::declareInterface(InterfaceDefinition* interfaceM) {
  auto hostName =
      globalState->metalCache->getName(
          globalState->metalCache->builtinPackageCoord, namePrefix + "_" + interfaceM->name->name);
  auto hostKind = globalState->metalCache->getInterfaceKind(hostName);
  addMappedKind(interfaceM->kind, hostKind);
  globalState->regionIdByKind.emplace(hostKind, getRegionId());

  structs.declareInterface(hostKind);
}

void Linear::defineInterface(InterfaceDefinition* interfaceM) {
  auto hostKind = hostKindByValeKind.find(interfaceM->kind)->second;
  auto hostInterfaceMT = dynamic_cast<InterfaceKind*>(hostKind);
  assert(hostInterfaceMT);

  auto interfaceMethodTypesL = globalState->getInterfaceFunctionTypes(interfaceM->kind);
  structs.defineInterface(hostInterfaceMT);
}

void Linear::declareInterfaceSerializeFunction(InterfaceKind* valeInterface) {
  auto interfaceMethod = getSerializeInterfaceMethod(valeInterface);
  globalState->addInterfaceExtraMethod(valeInterface, interfaceMethod);
}

void Linear::declareInterfaceExtraFunctions(InterfaceDefinition* interfaceDefM) {
  declareInterfaceSerializeFunction(interfaceDefM->kind);
}

void Linear::defineInterfaceExtraFunctions(InterfaceDefinition* interfaceDefM) {
}

Ref Linear::weakAlias(
    FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) {
  assert(false);
  exit(1);
}

void Linear::discardOwningRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  assert(false);
}


void Linear::noteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtrLE) {
  // Do nothing
}

Ref Linear::loadMember(
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

void Linear::storeMember(
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
  assert(false);
}

std::tuple<LLVMValueRef, LLVMValueRef> Linear::explodeInterfaceRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* interfaceRefMT,
    Ref interfaceRef) {
  auto interfaceRefLE = checkValidReference(FL(), functionState, builder, interfaceRefMT, interfaceRef);
  auto substructPtrLE = LLVMBuildExtractValue(builder, interfaceRefLE, 0, "substructPtr");
  auto edgeNumLE = LLVMBuildExtractValue(builder, interfaceRefLE, 1, "edgeNum");
  return std::make_tuple(edgeNumLE, substructPtrLE);
}


void Linear::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  assert(false);
}

void Linear::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  assert(false);
}

Ref Linear::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef,
    bool knownLive) {
  assert(false);
  exit(1);
}

LLVMValueRef Linear::getStringBytesPtr(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) {
  auto strWrapperPtrLE =
      checkValidReference(FL(), functionState, builder, linearStrRefMT, ref);
  return structs.getStringBytesPtr(functionState, builder, strWrapperPtrLE);
}

Ref Linear::allocate(
    Ref regionInstanceRef,
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefMT,
    const std::vector<Ref>& memberRefs) {
  // Nobody from the outside should call this.
  // Its API isn't good for us, it assumes we already have references to the members...
  // but Linear needs to allocate memory for the struct before it can serialize its members,
  // see MAPOWM.
  assert(false);
  exit(1);
}

Ref Linear::innerAllocate(
    Ref regionInstanceRef,
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* hostStructRefMT) {

  // We reserve some space for it before we serialize its members
  LLVMValueRef substructSizeIntLE =
      predictShallowSize(builder, hostStructRefMT->kind, constI64LE(globalState, 0));
  bumpDestinationOffset(functionState, builder, regionInstanceRef, substructSizeIntLE);
  buildFlare(FL(), globalState, functionState, builder);
  auto destinationStructRef = getDestinationRef(functionState, builder, regionInstanceRef, hostStructRefMT);

  auto i32MT = globalState->metalCache->i32Ref;
  auto boolMT = globalState->metalCache->boolRef;

  auto valeStructRefMT = unlinearizeReference(hostStructRefMT);
  auto desiredValeStructMT = dynamic_cast<StructKind*>(valeStructRefMT->kind);
  assert(desiredValeStructMT);
  auto valeStructDefM = globalState->program->getStruct(desiredValeStructMT);

  auto destinationPtrLE = checkValidReference(FL(), functionState, builder, hostStructRefMT, destinationStructRef);

  reserveRootMetadataBytesIfNeeded(functionState, builder, regionInstanceRef);

  return destinationStructRef;
}

Ref Linear::upcast(
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructMT,
    StructKind* sourceStructKindM,
    Ref sourceRef,

    Reference* targetInterfaceTypeM,
    InterfaceKind* targetInterfaceKindM) {
  assert(valeKindByHostKind.find(sourceStructMT->kind) != valeKindByHostKind.end());
  assert(valeKindByHostKind.find(sourceStructKindM) != valeKindByHostKind.end());
  assert(valeKindByHostKind.find(targetInterfaceTypeM->kind) != valeKindByHostKind.end());
  assert(valeKindByHostKind.find(targetInterfaceKindM) != valeKindByHostKind.end());

  auto i8PtrLT = LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);

  auto structRefLE = checkValidReference(FL(), functionState, builder, sourceStructMT, sourceRef);
  auto structI8PtrLE = LLVMBuildPointerCast(builder, structRefLE, i8PtrLT, "objAsVoidPtr");

  auto interfaceRefLT = structs.getInterfaceRefStruct(targetInterfaceKindM);

  auto interfaceRefLE = LLVMGetUndef(interfaceRefLT);
  interfaceRefLE = LLVMBuildInsertValue(builder, interfaceRefLE, structI8PtrLE, 0, "interfaceRefWithOnlyObj");
  auto edgeNumber = structs.getEdgeNumber(targetInterfaceKindM, sourceStructKindM);
  LLVMValueRef edgeNumberLE = constI64LE(globalState, edgeNumber);
  interfaceRefLE = LLVMBuildInsertValue(builder, interfaceRefLE, edgeNumberLE, 1, "interfaceRef");

  buildFlare(FL(), globalState, functionState, builder, "Making the interface ref with 0!");

  return wrap(globalState->getRegion(targetInterfaceTypeM), targetInterfaceTypeM, interfaceRefLE);
}

WrapperPtrLE Linear::lockWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref weakRefLE,
    bool weakRefKnownLive) {
  assert(false);
  exit(1);
}

Ref Linear::getRuntimeSizedArrayLength(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    Ref arrayRef,
    bool arrayKnownLive) {
  auto arrayRefLE = checkValidReference(FL(), functionState, builder, rsaRefMT, arrayRef);
  auto resultLE = LLVMBuildStructGEP(builder, arrayRefLE, 0, "rsaLenPtr");
  auto intLE = LLVMBuildLoad(builder, resultLE, "rsaLen");
  return wrap(globalState->getRegion(globalState->metalCache->i32Ref), globalState->metalCache->i32Ref, intLE);
}

LLVMValueRef Linear::checkValidReference(
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
  assert(LLVMTypeOf(refLE) == this->translateType(refM));
  return refLE;
}

Ref Linear::upgradeLoadResultToRefWithTargetOwnership(
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

void Linear::checkInlineStructType(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  auto argLE = checkValidReference(FL(), functionState, builder, refMT, ref);
  auto structKind = dynamic_cast<StructKind*>(refMT->kind);
  assert(structKind);
  assert(LLVMTypeOf(argLE) == structs.getStructStruct(structKind));
}

LoadResult Linear::loadElementFromSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* hostKsaRefMT,
    StaticSizedArrayT* hostKsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto arrayRefLE = checkValidReference(FL(), functionState, builder, hostKsaRefMT, arrayRef);
  // Array is the only member in the SSA struct.
  auto elementsPtrLE = LLVMBuildStructGEP(builder, arrayRefLE, 0, "ssaElemsPtr");

  auto valeKsaMT = unlinearizeSSA(hostKsaMT);
  auto valeKsaMD = globalState->program->getStaticSizedArray(valeKsaMT);
  auto valeMemberRefMT = valeKsaMD->rawArray->elementType;
  auto hostMemberRefMT = linearizeReference(valeMemberRefMT);
  return loadElementFromSSAInner(
      globalState, functionState, builder, hostKsaRefMT, hostKsaMT, valeKsaMD->size, hostMemberRefMT, indexRef, elementsPtrLE);
}

LoadResult Linear::loadElementFromRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* hostUsaRefMT,
    RuntimeSizedArrayT* hostUsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto arrayRefLE = checkValidReference(FL(), functionState, builder, hostUsaRefMT, arrayRef);
  // Size is the first member in the RSA struct.
  auto sizeLE = LLVMBuildLoad(builder, LLVMBuildStructGEP(builder, arrayRefLE, 0, "rsaSizePtr"), "rsaSize");
  auto sizeRef = wrap(this, globalState->metalCache->i32Ref, sizeLE);
  // Elements is the 1th member in the RSA struct, after size.
  auto elementsPtrLE = LLVMBuildStructGEP(builder, arrayRefLE, 1, "rsaElemsPtr");

  auto valeUsaRefMT = unlinearizeReference(hostUsaRefMT);
  auto valeUsaMT = dynamic_cast<RuntimeSizedArrayT*>(valeUsaRefMT->kind);
  assert(valeUsaMT);

  auto rsaDef = globalState->program->getRuntimeSizedArray(valeUsaMT);
  auto hostElementType = linearizeReference(rsaDef->rawArray->elementType);

  buildFlare(FL(), globalState, functionState, builder);
  return loadElement(
      globalState, functionState, builder, elementsPtrLE,
      hostElementType, sizeRef, indexRef);
}


Ref Linear::storeElementInRSA(
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

void Linear::deallocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  auto refLE = checkValidReference(FL(), functionState, builder, refMT, ref);
  auto concreteAsCharPtrLE =
      LLVMBuildBitCast(
          builder,
          refLE,
          LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0),
          "concreteCharPtrForFree");
  LLVMBuildCall(builder, globalState->externs->free, &concreteAsCharPtrLE, 1, "");
}

Ref Linear::constructRuntimeSizedArray(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaMT,
    RuntimeSizedArrayT* runtimeSizedArrayT,
    Ref sizeRef,
    const std::string& typeName) {
  return innerConstructRuntimeSizedArray(
      regionInstanceRef, functionState, builder, rsaMT, runtimeSizedArrayT, sizeRef, typeName, globalState->constI1(false));
}

Ref Linear::constructStaticSizedArray(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT) {
  return innerConstructStaticSizedArray(
      regionInstanceRef, functionState, builder, ssaRefMT, ssaMT, globalState->constI1(false));
}

Ref Linear::innerConstructStaticSizedArray(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* hostKsaMT,
    Ref dryRunBoolRef) {
  buildFlare(FL(), globalState, functionState, builder);

  auto boolMT = globalState->metalCache->boolRef;
  auto i32RefMT = globalState->metalCache->i32Ref;

  assert(ssaRefMT->kind == hostKsaMT);
  assert(globalState->getRegion(hostKsaMT) == this);

  auto valeKindMT = valeKindByHostKind.find(hostKsaMT)->second;
  auto valeKsaMT = dynamic_cast<StaticSizedArrayT*>(valeKindMT);
  assert(valeKsaMT);

  auto valeKsaDefM = globalState->program->getStaticSizedArray(valeKsaMT);
  auto sizeLE = predictShallowSize(builder, hostKsaMT, constI64LE(globalState, valeKsaDefM->size));
  bumpDestinationOffset(functionState, builder, regionInstanceRef, sizeLE);
  buildFlare(FL(), globalState, functionState, builder);

  auto ssaRef = getDestinationRef(functionState, builder, regionInstanceRef, ssaRefMT);
  auto ssaPtrLE = checkValidReference(FL(), functionState, builder, ssaRefMT, ssaRef);

  auto dryRunBoolLE = globalState->getRegion(boolMT)->checkValidReference(FL(), functionState, builder, boolMT, dryRunBoolRef);
  buildIf(
      globalState, functionState, builder, LLVMBuildNot(builder, dryRunBoolLE, "notDryRun"),
      [this, functionState, ssaPtrLE, hostKsaMT](LLVMBuilderRef thenBuilder) mutable {
        buildFlare(FL(), globalState, functionState, thenBuilder);

        auto ssaLT = structs.getStaticSizedArrayStruct(hostKsaMT);
        auto ssaValLE = LLVMGetUndef(ssaLT); // There are no fields
        LLVMBuildStore(thenBuilder, ssaValLE, ssaPtrLE);

        buildFlare(FL(), globalState, functionState, thenBuilder);

        // Caller still needs to initialize the elements!
      });

  reserveRootMetadataBytesIfNeeded(functionState, builder, regionInstanceRef);

  buildFlare(FL(), globalState, functionState, builder);

  return ssaRef;
}

Ref Linear::innerConstructRuntimeSizedArray(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Ref sizeRef,
    const std::string& typeName,
    Ref dryRunBoolRef) {
  buildFlare(FL(), globalState, functionState, builder);

  auto boolMT = globalState->metalCache->boolRef;
  auto i32RefMT = globalState->metalCache->i32Ref;

  assert(rsaRefMT->kind == rsaMT);
  assert(globalState->getRegion(rsaMT) == this);

  auto lenI32LE = globalState->getRegion(i32RefMT)->checkValidReference(FL(), functionState, builder, i32RefMT, sizeRef);
  auto lenI64LE = LLVMBuildZExt(builder, lenI32LE, LLVMInt64TypeInContext(globalState->context), "");

  auto sizeLE = predictShallowSize(builder, rsaMT, lenI64LE);
  bumpDestinationOffset(functionState, builder, regionInstanceRef, sizeLE);
  buildFlare(FL(), globalState, functionState, builder);

  auto rsaRef = getDestinationRef(functionState, builder, regionInstanceRef, rsaRefMT);
  auto rsaPtrLE = checkValidReference(FL(), functionState, builder, rsaRefMT, rsaRef);

  auto dryRunBoolLE = globalState->getRegion(boolMT)->checkValidReference(FL(), functionState, builder, boolMT, dryRunBoolRef);
  buildIf(
      globalState, functionState, builder, LLVMBuildNot(builder, dryRunBoolLE, "notDryRun"),
      [this, functionState, rsaPtrLE, lenI32LE, rsaMT](LLVMBuilderRef thenBuilder) mutable {
        buildFlare(FL(), globalState, functionState, thenBuilder);

        auto rsaLT = structs.getRuntimeSizedArrayStruct(rsaMT);
        auto rsaWithLenVal = LLVMBuildInsertValue(thenBuilder, LLVMGetUndef(rsaLT), lenI32LE, 0, "rsaWithLen");
        LLVMBuildStore(thenBuilder, rsaWithLenVal, rsaPtrLE);

        buildFlare(FL(), globalState, functionState, thenBuilder);

        // Caller still needs to initialize the elements!
      });

  reserveRootMetadataBytesIfNeeded(functionState, builder, regionInstanceRef);

  buildFlare(FL(), globalState, functionState, builder);

  return rsaRef;
}

Ref Linear::mallocStr(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE,
    LLVMValueRef sourceCharsPtrLE) {
  return innerMallocStr(regionInstanceRef, functionState, builder, lengthLE, sourceCharsPtrLE, globalState->constI1(false));
}

Ref Linear::innerMallocStr(
    Ref regionInstanceRef,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lenI32LE,
    LLVMValueRef sourceCharsPtrLE,
    Ref dryRunBoolRef) {
  auto boolMT = globalState->metalCache->boolRef;

  auto lenI64LE = LLVMBuildZExt(builder, lenI32LE, LLVMInt64TypeInContext(globalState->context), "");

  auto sizeLE = predictShallowSize(builder, linearStr, lenI32LE);
  bumpDestinationOffset(functionState, builder, regionInstanceRef, sizeLE);
  buildFlare(FL(), globalState, functionState, builder);

  buildFlare(FL(), globalState, functionState, builder, "bumping by size: ", lenI64LE);

  auto strRef = getDestinationRef(functionState, builder, regionInstanceRef, linearStrRefMT);
  auto strPtrLE = checkValidReference(FL(), functionState, builder, linearStrRefMT, strRef);

  auto dryRunBoolLE = globalState->getRegion(boolMT)->checkValidReference(FL(), functionState, builder, boolMT, dryRunBoolRef);

  buildIf(
      globalState, functionState, builder, LLVMBuildNot(builder, dryRunBoolLE, "notDryRun"),
      [this, functionState, strPtrLE, lenI32LE, lenI64LE, strRef, sourceCharsPtrLE](LLVMBuilderRef thenBuilder) mutable {
        auto strWithLenValLE = LLVMBuildInsertValue(thenBuilder, LLVMGetUndef(structs.getStringStruct()), lenI32LE, 0, "strWithLen");
        LLVMBuildStore(thenBuilder, strWithLenValLE, strPtrLE);

        buildFlare(FL(), globalState, functionState, thenBuilder, "length for str: ", lenI64LE);

        auto charsBeginPtr = getStringBytesPtr(functionState, thenBuilder, strRef);

        buildFlare(FL(), globalState, functionState, thenBuilder);

        std::vector<LLVMValueRef> argsLE = { charsBeginPtr, sourceCharsPtrLE, lenI64LE };
        LLVMBuildCall(thenBuilder, globalState->externs->strncpy, argsLE.data(), argsLE.size(), "");


        auto charsEndPtr = LLVMBuildGEP(thenBuilder, charsBeginPtr, &lenI64LE, 1, "charsEndPtr");

        buildFlare(FL(), globalState, functionState, thenBuilder, "storing at ", ptrToIntLE(globalState, thenBuilder, charsEndPtr));

        LLVMBuildStore(thenBuilder, constI8LE(globalState, 0), charsEndPtr);

        buildFlare(FL(), globalState, functionState, thenBuilder, "done storing");

        return strRef;
      });

  return strRef;
}

LLVMValueRef Linear::getStringLen(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) {
  auto refPtrLE = checkValidReference(FL(), functionState, builder, linearStrRefMT, ref);
  return LLVMBuildLoad(builder, LLVMBuildStructGEP(builder, refPtrLE, 0, "lenPtr"), "len");
}

LoadResult Linear::loadMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {
  auto structRefLE = checkValidReference(FL(), functionState, builder, structRefMT, structRef);
  if (structRefMT->location == Location::INLINE) {
    auto memberLE = LLVMBuildExtractValue(builder, structRefLE, memberIndex, memberName.c_str());
    return LoadResult{wrap(globalState->getRegion(expectedMemberType), expectedMemberType, memberLE)};
  } else {
    auto structPtrLE = structRefLE;
    return loadInnerInnerStructMember(globalState, builder, structPtrLE, memberIndex, expectedMemberType, memberName);
  }
}

void Linear::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IKindStructsSource* kindStructs,
    Reference* refM,
    LLVMValueRef refLE) {
  regularCheckValidReference(checkerAFL, globalState, functionState, builder, kindStructs, refM, refLE);
}

//std::string Linear::getExportName(
//    Package* package,
//    Reference* reference) {
//  return package->getKindExportName(reference->kind) + (reference->location == Location::YONDER ? "Ref" : "");
//}

std::string Linear::getExportName(Package* currentPackage, Reference* hostRefMT, bool includeProjectName) {
  assert(valeKindByHostKind.find(hostRefMT->kind) != valeKindByHostKind.end());

  auto hostMT = hostRefMT->kind;
  if (auto innt = dynamic_cast<Int *>(hostMT)) {
    return std::string() + "int" + std::to_string(innt->bits) + "_t";
  } else if (dynamic_cast<Bool *>(hostMT)) {
    return "int8_t";
  } else if (dynamic_cast<Float *>(hostMT)) {
    return "double";
  } else if (dynamic_cast<Str *>(hostMT)) {
    return "ValeStr*";
  } else if (auto hostInterfaceMT = dynamic_cast<InterfaceKind *>(hostMT)) {
    auto valeMT = valeKindByHostKind.find(hostMT)->second;
    auto valeInterfaceMT = dynamic_cast<InterfaceKind*>(valeMT);
    assert(valeInterfaceMT);
    auto baseName = currentPackage->getKindExportName(valeInterfaceMT, includeProjectName);
    assert(hostRefMT->ownership == Ownership::SHARE);
//    if (hostRefMT->location == Location::INLINE) {
      return baseName;
//    } else {
//      return baseName + "*";
//    };
  } else if (auto hostStructMT = dynamic_cast<StructKind *>(hostMT)) {
    auto valeMT = valeKindByHostKind.find(hostMT)->second;
    auto valeStructMT = dynamic_cast<StructKind*>(valeMT);
    assert(valeStructMT);
    if (valeStructMT == globalState->metalCache->emptyTupleStruct) {
      return "void";
    }
    auto baseName = currentPackage->getKindExportName(valeStructMT, includeProjectName);
    assert(hostRefMT->ownership == Ownership::SHARE);
    if (hostRefMT->location == Location::INLINE) {
      return baseName;
    } else {
      return baseName + "*";
    }
  } else if (dynamic_cast<StaticSizedArrayT *>(hostMT)) {
    assert(false);
  } else if (auto hostUsaMT = dynamic_cast<RuntimeSizedArrayT *>(hostMT)) {
    auto valeMT = valeKindByHostKind.find(hostMT)->second;
    auto valeUsaMT = dynamic_cast<RuntimeSizedArrayT*>(valeMT);
    assert(valeUsaMT);
    auto baseName = currentPackage->getKindExportName(valeUsaMT, includeProjectName);
    assert(hostRefMT->ownership == Ownership::SHARE);
    if (hostRefMT->location == Location::INLINE) {
      return baseName;
    } else {
      return baseName + "*";
    }
  } else {
    std::cerr << "Unimplemented type in immutables' getExportName: "
              << typeid(*hostRefMT->kind).name() << std::endl;
    assert(false);
  }
}

std::string Linear::generateStructDefsC(
    Package* currentPackage,
    StructDefinition* structDefM) {
  auto name = currentPackage->getKindExportName(structDefM->kind, true);
  std::stringstream s;
  s << "typedef struct " << name << " { " << std::endl;
  for (int i = 0; i < structDefM->members.size(); i++) {
    auto member = structDefM->members[i];
    auto hostMT = hostKindByValeKind.find(member->type->kind)->second;
    auto hostRefMT = globalState->metalCache->getReference(member->type->ownership, member->type->location, hostMT);
    s << "  " << getExportName(currentPackage, hostRefMT, true) << " " << member->name << ";" << std::endl;
  }
  s << "} " << name << ";" << std::endl;
  return s.str();
}

std::string Linear::generateInterfaceDefsC(
    Package* currentPackage,
    InterfaceDefinition* interfaceDefM) {
    std::stringstream s;

  auto interfaceName = currentPackage->getKindExportName(interfaceDefM->kind, true);

  auto hostKind = hostKindByValeKind.find(interfaceDefM->kind)->second;
  auto hostInterfaceKind = dynamic_cast<InterfaceKind*>(hostKind);
  assert(hostInterfaceKind);

  s << "typedef enum " << interfaceName << "_" << "Type {" << std::endl;
  for (auto hostStructKind : structs.getOrderedSubstructs(hostInterfaceKind)) {
    auto valeKind = valeKindByHostKind.find(hostStructKind)->second;
    auto valeStructKind = dynamic_cast<StructKind*>(valeKind);
    assert(valeStructKind);
    s << "  " << interfaceName << "_Type_" << currentPackage->getKindExportName(valeStructKind, false) << "," << std::endl;
  }
  s << "} " << interfaceName << "_Type;" << std::endl;
  s << "typedef struct " << interfaceName << " {" << std::endl;
  s << "void* obj; " << interfaceName << "_Type type;" << std::endl;
  s << "} " << interfaceName << ";" << std::endl;

  return s.str();
}

std::string Linear::generateRuntimeSizedArrayDefsC(
    Package* currentPackage,
    RuntimeSizedArrayDefinitionT* rsaDefM) {
//  auto names = globalState->program->getExportedNames(rsaDefM->name);
//  assert(names.size() > 0);
//  // In the future, we should make this choose the name that was exported
//  // by this module itself. See MMEDT.
//  auto name = names[0];

  auto rsaName = currentPackage->getKindExportName(rsaDefM->kind, true);

  auto valeMemberRefMT = rsaDefM->rawArray->elementType;
  auto hostMemberRefMT = linearizeReference(valeMemberRefMT);
//  auto hostMemberRefName = getMemberArbitraryRefNameCSeeMMEDT(hostMemberRefMT);

  std::stringstream s;
  s << "typedef struct " << rsaName << " {" << std::endl;
  s << "  uint32_t length;" << std::endl;
//  s << "  " << currentPackage->getKindExportName(hostMemberRefMT->kind) << " elements[0];" << std::endl;
  s << "  " << getExportName(currentPackage, hostMemberRefMT, true) << " elements[0];" << std::endl;
  s << "} " << rsaName << ";" << std::endl;
  return s.str();
}

std::string Linear::generateStaticSizedArrayDefsC(
    Package* currentPackage,
    StaticSizedArrayDefinitionT* rsaDefM) {
  assert(false);
  return "";
}

Reference* Linear::getExternalType(Reference* refMT) {
  return refMT;
}

Ref Linear::topLevelSerialize(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Kind* valeKind,
    Ref ref) {
  auto rootMetadataSize =
      LLVMABISizeOfType(globalState->dataLayout, structs.getStructStruct(rootMetadataKind));
  auto startMetadataSize =
      LLVMABISizeOfType(globalState->dataLayout, structs.getStructStruct(startMetadataKind));

  auto valeRefMT =
      globalState->metalCache->getReference(
          Ownership::SHARE, Location::YONDER, valeKind);
  auto hostRefMT = linearizeReference(valeRefMT);

  auto nullLT = LLVMConstNull(LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0));
  // This is an arbitrary number, 0xFFFFFFFF * 1000000, which is 4294967296000000 or 0xF424000000000.
  // If we see it somewhere in a bug, all the 0s might be a hint to search for it and find it here.
  auto dryRunCounterBeginLE = constI64LE(globalState, 0xFFFFFFFFULL * 1000000ULL);
  // We'll keep subtracting from this (similar to how a program stack works) and the final address
  // will be subtracted from this number to find the needed size.

  auto regionLT = structs.getStructStruct(regionKind);

  auto dryRunInitialRegionStructLE = LLVMGetUndef(regionLT);
  dryRunInitialRegionStructLE = LLVMBuildInsertValue(builder, dryRunInitialRegionStructLE, nullLT, 0, "regionStruct");
  dryRunInitialRegionStructLE = LLVMBuildInsertValue(builder, dryRunInitialRegionStructLE, dryRunCounterBeginLE, 1, "regionStruct");
  dryRunInitialRegionStructLE = LLVMBuildInsertValue(builder, dryRunInitialRegionStructLE, constI64LE(globalState, rootMetadataSize), 2, "regionStruct");
  auto dryRunRegionInstancePtrLE = makeMidasLocal(functionState, builder, regionLT, "region", dryRunInitialRegionStructLE);
  auto dryRunRegionInstanceRef = wrap(this, regionRefMT, dryRunRegionInstancePtrLE);

  callSerialize(functionState, builder, valeKind, dryRunRegionInstanceRef, ref, globalState->constI1(true));

  // Reserve some space for the beginning metadata block
  bumpDestinationOffset(functionState, builder, dryRunRegionInstanceRef, constI64LE(globalState, startMetadataSize));
  buildFlare(FL(), globalState, functionState, builder);

  auto dryRunFinalOffsetLE = getDestinationOffset(builder, dryRunRegionInstancePtrLE);
  auto sizeIntLE = LLVMBuildSub(builder, dryRunCounterBeginLE, dryRunFinalOffsetLE, "size");

  LLVMValueRef bufferBeginPtrLE = callMalloc(globalState, builder, sizeIntLE);
  buildFlare(FL(), globalState, functionState, builder, "malloced ", sizeIntLE, " got ptr ", ptrToIntLE(globalState, builder, bufferBeginPtrLE));

  auto initialRegionStructLE = LLVMGetUndef(regionLT);
  initialRegionStructLE = LLVMBuildInsertValue(builder, initialRegionStructLE, bufferBeginPtrLE, 0, "regionStruct");
  initialRegionStructLE = LLVMBuildInsertValue(builder, initialRegionStructLE, sizeIntLE, 1, "regionStruct");
  initialRegionStructLE = LLVMBuildInsertValue(builder, initialRegionStructLE, constI64LE(globalState, rootMetadataSize), 2, "regionStruct");
  auto regionInstancePtrLE = makeMidasLocal(functionState, builder, regionLT, "region", initialRegionStructLE);
  auto regionInstanceRef = wrap(this, regionRefMT, regionInstancePtrLE);
//
//  bumpDestinationOffset(functionState, builder, regionInstanceRef, constI64LE(globalState, 0x10));
//  auto trailingBeginPtrI8PtrLE = getDestinationPtr(functionState, builder, regionInstanceRef);
//  auto trailingBeginPtrPtrLE =
//      LLVMBuildPointerCast(
//          builder,
//          trailingBeginPtrI8PtrLE,
//          LLVMPointerType(LLVMPointerType(structs.getStructStruct(rootMetadataKind), 0), 0),
//          "trailingBeginPtrPtr");

  auto resultRef =
      callSerialize(
          functionState, builder, valeKind, regionInstanceRef, ref, globalState->constI1(false));

  auto rootObjectPtrLE =
      (dynamic_cast<InterfaceKind*>(valeKind) != nullptr ?
          std::get<1>(explodeInterfaceRef(functionState, builder, hostRefMT, resultRef)) :
          checkValidReference(FL(), functionState, builder, hostRefMT, resultRef));
  auto rootMetadataPtrLE =
      LLVMBuildPointerCast(builder,
          lowerAndHexRoundDownPointer(globalState, builder, rootObjectPtrLE, constI64LE(globalState, rootMetadataSize)),
          translateType(rootMetadataRefMT),
          "rootMetadataPtr");

  bumpDestinationOffset(functionState, builder, regionInstanceRef, constI64LE(globalState, startMetadataSize));
  buildFlare(FL(), globalState, functionState, builder);
  auto startMetadataRef = getDestinationRef(functionState, builder, regionInstanceRef, startMetadataRefMT);
  auto startMetadataPtrLE = checkValidReference(FL(), functionState, builder, startMetadataRefMT, startMetadataRef);
  LLVMBuildStore(builder,
      sizeIntLE,
      LLVMBuildStructGEP(builder, startMetadataPtrLE, 0, "sizePtr"));
  LLVMBuildStore(builder,
      ptrToIntLE(globalState, builder, bufferBeginPtrLE),
      LLVMBuildStructGEP(builder, startMetadataPtrLE, 1, "startIntPtr"));
  LLVMBuildStore(builder,
      ptrToIntLE(globalState, builder, rootObjectPtrLE),
      LLVMBuildStructGEP(builder, startMetadataPtrLE, 2, "rootIntPtr"));

  LLVMBuildStore(builder,
      ptrToIntLE(globalState, builder, startMetadataPtrLE),
      LLVMBuildStructGEP(builder, rootMetadataPtrLE, 0, "rootIntPtr"));
  LLVMBuildStore(builder,
      sizeIntLE,
      LLVMBuildStructGEP(builder, rootMetadataPtrLE, 1, "rootIntPtr"));

  auto destinationIntLE = getDestinationOffset(builder, regionInstancePtrLE);
  auto condLE = LLVMBuildICmp(builder, LLVMIntEQ, destinationIntLE, constI64LE(globalState, 0), "cond");
  buildAssert(globalState, functionState, builder, condLE, "Serialization start mismatch!");

  return resultRef;
}

Ref Linear::receiveUnencryptedAlienReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef) {
  assert(sourceRefMT->ownership == Ownership::SHARE);

  auto sourceRegion = globalState->getRegion(sourceRefMT);

  auto sourceRefLE =
      globalState->getRegion(sourceRefMT)
          ->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef);

  if (dynamic_cast<Int*>(sourceRefMT->kind)) {
    return wrap(globalState->getRegion(sourceRefMT), targetRefMT, sourceRefLE);
  } else if (dynamic_cast<Bool*>(sourceRefMT->kind)) {
    auto resultLE = LLVMBuildZExt(builder, sourceRefLE, LLVMInt8TypeInContext(globalState->context), "boolAsI8");
    return wrap(globalState->getRegion(sourceRefMT), targetRefMT, resultLE);
  } else if (dynamic_cast<Float*>(sourceRefMT->kind)) {
    return wrap(globalState->getRegion(sourceRefMT), targetRefMT, sourceRefLE);
  } else if (dynamic_cast<Str*>(sourceRefMT->kind) ||
      dynamic_cast<StructKind*>(sourceRefMT->kind) ||
      dynamic_cast<InterfaceKind*>(sourceRefMT->kind) ||
      dynamic_cast<StaticSizedArrayT*>(sourceRefMT->kind) ||
      dynamic_cast<RuntimeSizedArrayT*>(sourceRefMT->kind)) {
    if (sourceRefMT->location == Location::INLINE) {
      if (sourceRefMT == globalState->metalCache->emptyTupleStructRef) {
        auto emptyTupleRefMT = linearizeReference(globalState->metalCache->emptyTupleStructRef);
        return wrap(this, emptyTupleRefMT, LLVMGetUndef(translateType(emptyTupleRefMT)));
      } else {
        assert(false);
      }
    } else {
      return topLevelSerialize(functionState, builder, sourceRefMT->kind, sourceRef);
    }
  } else assert(false);

  assert(false);
}

LLVMTypeRef Linear::getInterfaceMethodVirtualParamAnyType(Reference* reference) {
  return LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
}

LLVMValueRef Linear::predictShallowSize(LLVMBuilderRef builder, Kind* kind, LLVMValueRef lenI32LE) {
  assert(globalState->getRegion(kind) == this);
  auto lenI64LE = LLVMBuildZExt(builder, lenI32LE, LLVMInt64TypeInContext(globalState->context), "lenAsI64");
  if (auto structKind = dynamic_cast<StructKind*>(kind)) {
    return constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, structs.getStructStruct(structKind)));
  } else if (kind == linearStr) {
    auto headerBytesLE =
        constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, structs.getStringStruct()));
    auto lenAndNullTermLE = LLVMBuildAdd(builder, lenI64LE, constI64LE(globalState, 1), "lenAndNullTerm");
    return LLVMBuildAdd(builder, headerBytesLE, lenAndNullTermLE, "sum");
  } else if (auto hostUsaMT = dynamic_cast<RuntimeSizedArrayT*>(kind)) {
    auto headerBytesLE =
        constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, structs.getRuntimeSizedArrayStruct(hostUsaMT)));

    auto valeKindMT = valeKindByHostKind.find(hostUsaMT)->second;
    auto valeUsaMT = dynamic_cast<RuntimeSizedArrayT*>(valeKindMT);
    assert(valeUsaMT);
    auto valeElementRefMT = globalState->program->getRuntimeSizedArray(valeUsaMT)->rawArray->elementType;
    auto hostElementRefMT = linearizeReference(valeElementRefMT);
    auto hostElementRefLT = translateType(hostElementRefMT);

    auto sizePerElement = LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(hostElementRefLT, 1));
    // The above line tries to include padding... if the below fails, we know there are some serious shenanigans
    // going on in LLVM.
    assert(sizePerElement * 2 == LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(hostElementRefLT, 2)));
    auto elementsSizeLE = LLVMBuildMul(builder, constI64LE(globalState, sizePerElement), lenI64LE, "elementsSize");

    return LLVMBuildAdd(builder, headerBytesLE, elementsSizeLE, "sum");
  } else if (auto hostKsaMT = dynamic_cast<StaticSizedArrayT*>(kind)) {
    auto headerBytesLE =
        constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, structs.getStaticSizedArrayStruct(hostKsaMT)));

    auto valeKindMT = valeKindByHostKind.find(hostKsaMT)->second;
    auto valeKsaMT = dynamic_cast<StaticSizedArrayT*>(valeKindMT);
    assert(valeKsaMT);
    auto valeElementRefMT = globalState->program->getStaticSizedArray(valeKsaMT)->rawArray->elementType;
    auto hostElementRefMT = linearizeReference(valeElementRefMT);
    auto hostElementRefLT = translateType(hostElementRefMT);

    auto sizePerElement = LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(hostElementRefLT, 1));
    // The above line tries to include padding... if the below fails, we know there are some serious shenanigans
    // going on in LLVM.
    assert(sizePerElement * 2 == LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(hostElementRefLT, 2)));
    auto elementsSizeLE = LLVMBuildMul(builder, constI64LE(globalState, sizePerElement), lenI64LE, "elementsSize");

    return LLVMBuildAdd(builder, headerBytesLE, elementsSizeLE, "sum");
  } else assert(false);
}

Ref Linear::receiveAndDecryptFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Ref sourceRef) {
  assert(false);
  exit(1);
}

Ref Linear::encryptAndSendFamiliarReference(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Ref sourceRef) {
  assert(false);
  exit(1);
}

InterfaceMethod* Linear::getSerializeInterfaceMethod(Kind* valeKind) {
  return globalState->metalCache->getInterfaceMethod(
      getSerializePrototype(valeKind), 1);
}

Ref Linear::callSerialize(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Kind* valeKind,
    Ref regionInstanceRef,
    Ref objectRef,
    Ref dryRunBoolRef) {
  auto prototype = getSerializePrototype(valeKind);
  if (auto interfaceKind = dynamic_cast<InterfaceKind*>(valeKind)) {
    auto virtualArgRefMT = prototype->params[1];
    int indexInEdge = globalState->getInterfaceMethodIndex(interfaceKind, prototype);
    auto methodFunctionPtrLE =
        globalState->getRegion(virtualArgRefMT)
            ->getInterfaceMethodFunctionPtr(functionState, builder, virtualArgRefMT, objectRef, indexInEdge);
    return buildInterfaceCall(
        globalState, functionState, builder, prototype, methodFunctionPtrLE, {regionInstanceRef, objectRef, dryRunBoolRef}, 1);
  } else {
    return buildCall(globalState, functionState, builder, prototype, {regionInstanceRef, objectRef, dryRunBoolRef});
  }
}

void Linear::bumpDestinationOffset(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    LLVMValueRef sizeIntLE) {
  auto regionInstancePtrLE =
      checkValidReference(FL(), functionState, builder, regionRefMT, regionInstanceRef);
  auto destinationOffsetPtrLE =
      LLVMBuildStructGEP(builder, regionInstancePtrLE, 1, "destinationOffsetPtr");
  auto destinationOffsetLE = LLVMBuildLoad(builder, destinationOffsetPtrLE, "destinationOffset");
  destinationOffsetLE = LLVMBuildSub(builder, destinationOffsetLE, sizeIntLE, "bumpedDestinationOffset");
  buildFlare(FL(), globalState, functionState, builder, "subtracted: ", destinationOffsetLE);
  destinationOffsetLE = hexRoundDown(globalState, builder, destinationOffsetLE);
  buildFlare(FL(), globalState, functionState, builder, "rounded: ", destinationOffsetLE);
  LLVMBuildStore(builder, destinationOffsetLE, destinationOffsetPtrLE);
  buildFlare(FL(), globalState, functionState, builder);
}

void Linear::reserveRootMetadataBytesIfNeeded(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef) {
  auto regionInstancePtrLE =
      checkValidReference(FL(), functionState, builder, regionRefMT, regionInstanceRef);
  auto rootMetadataBytesNeededPtrLE =
      LLVMBuildStructGEP(builder, regionInstancePtrLE, 2, "rootMetadataBytesNeeded");
  auto rootMetadataBytesNeededLE = LLVMBuildLoad(builder, rootMetadataBytesNeededPtrLE, "rootMetadataBytesNeeded");
  bumpDestinationOffset(functionState, builder, regionInstanceRef, rootMetadataBytesNeededLE);
  buildFlare(FL(), globalState, functionState, builder);
  // Reset it to zero, we only need it once. This will make the next calls not reserve it. See MAPOWN for more.
  LLVMBuildStore(builder, constI64LE(globalState, 0), rootMetadataBytesNeededPtrLE);
}

LLVMValueRef Linear::getDestinationOffset(
    LLVMBuilderRef builder,
    LLVMValueRef regionInstancePtrLE) {
  auto destinationOffsetPtrLE =
      LLVMBuildStructGEP(builder, regionInstancePtrLE, 1, "destinationOffsetPtr");
  return LLVMBuildLoad(builder, destinationOffsetPtrLE, "destinationOffset");
}

LLVMValueRef Linear::getDestinationPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef) {
  auto regionInstancePtrLE =
      checkValidReference(FL(), functionState, builder, regionRefMT, regionInstanceRef);
  auto bufferBeginPtrPtrLE = LLVMBuildStructGEP(builder, regionInstancePtrLE, 0, "bufferBeginPtrPtr");
  auto bufferBeginPtrLE = LLVMBuildLoad(builder, bufferBeginPtrPtrLE, "bufferBeginPtr");

  auto destinationOffsetPtrLE =
      LLVMBuildStructGEP(builder, regionInstancePtrLE, 1, "destinationOffsetPtr");
  auto destinationOffsetLE = LLVMBuildLoad(builder, destinationOffsetPtrLE, "destinationOffset");

  auto destinationI8PtrLE = LLVMBuildGEP(builder, bufferBeginPtrLE, &destinationOffsetLE, 1, "destinationI8Ptr");
  return destinationI8PtrLE;
}

Ref Linear::getDestinationRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* desiredRefMT) {
  auto destinationI8PtrLE = getDestinationPtr(functionState, builder, regionInstanceRef);
  auto desiredRefLT = translateType(desiredRefMT);
  auto destinationPtr = LLVMBuildBitCast(builder, destinationI8PtrLE, desiredRefLT, "destinationPtr");
  return wrap(this, desiredRefMT, destinationPtr);
}

Prototype* Linear::getSerializePrototype(Kind* valeKind) {
  auto boolMT = globalState->metalCache->boolRef;
  auto sourceStructRefMT =
      globalState->metalCache->getReference(
          Ownership::SHARE, Location::YONDER, valeKind);
  auto hostRefMT = linearizeReference(sourceStructRefMT);
  return globalState->metalCache->getPrototype(
      globalState->serializeName, hostRefMT,
      {regionRefMT, sourceStructRefMT, boolMT});
}

Prototype* Linear::getSerializeThunkPrototype(StructKind* structKind, InterfaceKind* interfaceKind) {
  auto boolMT = globalState->metalCache->boolRef;
  auto valeStructRefMT =
      globalState->metalCache->getReference(
          Ownership::SHARE, Location::YONDER, structKind);
  auto valeInterfaceRefMT =
      globalState->metalCache->getReference(
          Ownership::SHARE, Location::YONDER, interfaceKind);
  auto hostRefMT = linearizeReference(valeInterfaceRefMT);
  return globalState->metalCache->getPrototype(
      globalState->serializeThunkName, hostRefMT,
      {regionRefMT, valeStructRefMT, boolMT});
}

void Linear::declareConcreteSerializeFunction(Kind* valeKind) {
  auto prototype = getSerializePrototype(valeKind);
  auto nameL = globalState->serializeName->name + "__" + globalState->getKindName(valeKind)->name;
  declareExtraFunction(globalState, prototype, nameL);
}

void Linear::defineConcreteSerializeFunction(Kind* valeKind) {
  auto i32MT = globalState->metalCache->i32Ref;
  auto boolMT = globalState->metalCache->boolRef;

  auto prototype = getSerializePrototype(valeKind);

  auto serializeMemberOrElement =
      [this](
          FunctionState* functionState,
          LLVMBuilderRef builder,
          Reference* sourceMemberRefMT,
          Ref regionInstanceRef,
          Ref sourceMemberRef,
          Ref dryRunBoolRef) {
        auto targetMemberRefMT = linearizeReference(sourceMemberRefMT);
        auto sourceMemberLE =
            globalState->getRegion(sourceMemberRefMT)->checkValidReference(
                FL(), functionState, builder, sourceMemberRefMT, sourceMemberRef);
        if (sourceMemberRefMT == globalState->metalCache->i64Ref) {
          return wrap(globalState->getRegion(targetMemberRefMT), targetMemberRefMT, sourceMemberLE);
        } else if (sourceMemberRefMT == globalState->metalCache->i32Ref) {
          return wrap(globalState->getRegion(targetMemberRefMT), targetMemberRefMT, sourceMemberLE);
        } else if (sourceMemberRefMT == globalState->metalCache->boolRef) {
          auto resultLE = LLVMBuildZExt(builder, sourceMemberLE, LLVMInt8TypeInContext(globalState->context), "boolAsI8");
          return wrap(globalState->getRegion(targetMemberRefMT), targetMemberRefMT, resultLE);
        } else if (sourceMemberRefMT == globalState->metalCache->floatRef) {
          return wrap(globalState->getRegion(targetMemberRefMT), targetMemberRefMT, sourceMemberLE);
        } else if (
            dynamic_cast<Str*>(sourceMemberRefMT->kind) ||
            dynamic_cast<StructKind*>(sourceMemberRefMT->kind) ||
            dynamic_cast<InterfaceKind*>(sourceMemberRefMT->kind) ||
            dynamic_cast<StaticSizedArrayT*>(sourceMemberRefMT->kind) ||
            dynamic_cast<RuntimeSizedArrayT*>(sourceMemberRefMT->kind)) {
          auto destinationMemberRef =
              callSerialize(
                  functionState, builder, sourceMemberRefMT->kind, regionInstanceRef, sourceMemberRef, dryRunBoolRef);
          return destinationMemberRef;
        } else assert(false);
      };

  defineFunctionBody(
      globalState, prototype,
      [&](FunctionState* functionState, LLVMBuilderRef builder) -> void {
        auto valeObjectRefMT = prototype->params[1];
        auto hostObjectRefMT = prototype->returnType;

        auto regionInstanceRef = wrap(globalState->getRegion(regionRefMT), regionRefMT, LLVMGetParam(functionState->containingFuncL, 0));
        auto valeObjectRef = wrap(globalState->getRegion(valeObjectRefMT), valeObjectRefMT, LLVMGetParam(functionState->containingFuncL, 1));
        auto dryRunBoolRef = wrap(globalState->getRegion(boolMT), boolMT, LLVMGetParam(functionState->containingFuncL, 2));

        if (auto valeStructKind = dynamic_cast<StructKind*>(valeObjectRefMT->kind)) {
          auto hostKind = hostKindByValeKind.find(valeStructKind)->second;
          auto hostStructKind = dynamic_cast<StructKind*>(hostKind);
          assert(hostStructKind);
          auto valeStructDefM = globalState->program->getStruct(valeStructKind);

          auto hostObjectRef = innerAllocate(regionInstanceRef, FL(), functionState, builder, hostObjectRefMT);
          auto innerStructPtrLE = checkValidReference(FL(), functionState, builder, hostObjectRefMT, hostObjectRef);

          std::vector<Ref> hostMemberRefs;
          for (int i = 0; i < valeStructDefM->members.size(); i++) {
            auto valeMemberM = valeStructDefM->members[i];
            auto sourceMemberRefMT = valeMemberM->type;
            auto sourceMemberRef =
                globalState->getRegion(valeObjectRefMT)->loadMember(
                    functionState, builder, valeObjectRefMT, valeObjectRef, true,
                    i, valeMemberM->type, valeMemberM->type, valeMemberM->name);
            hostMemberRefs.push_back(
                serializeMemberOrElement(
                    functionState, builder, sourceMemberRefMT, regionInstanceRef, sourceMemberRef, dryRunBoolRef));
          }

          auto dryRunBoolLE = globalState->getRegion(boolMT)->checkValidReference(FL(), functionState, builder, boolMT, dryRunBoolRef);
          buildIf(
              globalState, functionState, builder, LLVMBuildNot(builder, dryRunBoolLE, "notDryRun"),
              [this, functionState, valeStructDefM, hostMemberRefs, innerStructPtrLE](LLVMBuilderRef thenBuilder) {
                for (int i = 0; i < valeStructDefM->members.size(); i++) {
                  auto hostMemberRef = hostMemberRefs[i];
                  auto hostMemberType = linearizeReference(valeStructDefM->members[i]->type);
                  auto memberName = valeStructDefM->members[i]->name;
                  auto ptrLE =
                      LLVMBuildStructGEP(thenBuilder, innerStructPtrLE, i, memberName.c_str());
                  auto memberLE =
                      globalState->getRegion(hostMemberType)
                          ->checkValidReference(FL(), functionState, thenBuilder, hostMemberType, hostMemberRef);
                  LLVMBuildStore(thenBuilder, memberLE, ptrLE);
                }
              });

//          // Remember, we're subtracting each size from a very large number, so its easier to round down
//          // to the next multiple of 16.
//          totalSizeIntLE = hexRoundDown(globalState, builder, totalSizeIntLE);

          auto hostObjectRefLE = checkValidReference(FL(), functionState, builder, hostObjectRefMT, hostObjectRef);

          LLVMBuildRet(builder, hostObjectRefLE);
        } else if (dynamic_cast<Str*>(valeObjectRefMT->kind)) {
          auto lengthLE = globalState->getRegion(valeObjectRefMT)->getStringLen(functionState, builder, valeObjectRef);
          auto sourceBytesPtrLE = globalState->getRegion(valeObjectRefMT)->getStringBytesPtr(functionState, builder, valeObjectRef);

          auto strRef = innerMallocStr(regionInstanceRef, functionState, builder, lengthLE, sourceBytesPtrLE, dryRunBoolRef);

          buildFlare(FL(), globalState, functionState, builder, "Returning from serialize function!");

          LLVMBuildRet(builder, checkValidReference(FL(), functionState, builder, linearStrRefMT, strRef));
        } else if (auto valeUsaMT = dynamic_cast<RuntimeSizedArrayT*>(valeObjectRefMT->kind)) {

          buildFlare(FL(), globalState, functionState, builder, "In RSA serialize!");

          auto hostKindMT = hostKindByValeKind.find(valeUsaMT)->second;
          auto hostUsaMT = dynamic_cast<RuntimeSizedArrayT*>(hostKindMT);
          assert(hostUsaMT);
          auto hostUsaRefMT = globalState->metalCache->getReference(Ownership::SHARE, Location::YONDER, hostKindMT);

          auto lengthRef = globalState->getRegion(valeObjectRefMT)->getRuntimeSizedArrayLength(functionState, builder, valeObjectRefMT, valeObjectRef, true);

          buildFlare(FL(), globalState, functionState, builder);

          auto hostUsaRef =
              innerConstructRuntimeSizedArray(
                  regionInstanceRef,
                  functionState, builder, hostUsaRefMT, hostUsaMT, lengthRef, "serializedrsa", dryRunBoolRef);
          auto valeMemberRefMT = globalState->program->getRuntimeSizedArray(valeUsaMT)->rawArray->elementType;

          buildFlare(FL(), globalState, functionState, builder);

          intRangeLoopReverse(
              globalState, functionState, builder, globalState->metalCache->i32, lengthRef,
              [this, functionState, hostObjectRefMT, boolMT, hostUsaRef, valeObjectRefMT, hostUsaMT, valeUsaMT, valeObjectRef, valeMemberRefMT, regionInstanceRef, serializeMemberOrElement, dryRunBoolRef](
                  Ref indexRef, LLVMBuilderRef bodyBuilder){
                buildFlare(FL(), globalState, functionState, bodyBuilder, "In serialize iteration!");

                auto sourceMemberRef =
                    globalState->getRegion(valeObjectRefMT)
                        ->loadElementFromRSA(functionState, bodyBuilder, valeObjectRefMT, valeUsaMT, valeObjectRef, true, indexRef)
                    .move();
                buildFlare(FL(), globalState, functionState, bodyBuilder);
                auto hostElementRef =
                    serializeMemberOrElement(
                        functionState, bodyBuilder, valeMemberRefMT, regionInstanceRef, sourceMemberRef, dryRunBoolRef);
                buildFlare(FL(), globalState, functionState, bodyBuilder);
                auto dryRunBoolLE = globalState->getRegion(boolMT)->checkValidReference(FL(), functionState, bodyBuilder, boolMT, dryRunBoolRef);
                buildIf(
                    globalState, functionState, bodyBuilder, LLVMBuildNot(bodyBuilder, dryRunBoolLE, "notDryRun"),
                    [this, functionState, hostObjectRefMT, hostUsaRef, indexRef, hostElementRef, hostUsaMT](
                        LLVMBuilderRef thenBuilder) mutable {
                      initializeElementInRSA(
                          functionState, thenBuilder, hostObjectRefMT, hostUsaMT, hostUsaRef, true, indexRef, hostElementRef);
                    buildFlare(FL(), globalState, functionState, thenBuilder);
                  });
              });

          buildFlare(FL(), globalState, functionState, builder, "Returning from serialize function!");

          LLVMBuildRet(builder, checkValidReference(FL(), functionState, builder, hostUsaRefMT, hostUsaRef));
        } else if (auto valeKsaMT = dynamic_cast<StaticSizedArrayT*>(valeObjectRefMT->kind)) {
          auto hostKindMT = hostKindByValeKind.find(valeKsaMT)->second;
          auto hostKsaMT = dynamic_cast<StaticSizedArrayT*>(hostKindMT);
          assert(hostKsaMT);
          auto hostKsaRefMT = globalState->metalCache->getReference(Ownership::SHARE, Location::YONDER, hostKindMT);

          auto hostKsaRef =
              innerConstructStaticSizedArray(
                  regionInstanceRef,
                  functionState, builder, hostKsaRefMT, hostKsaMT, dryRunBoolRef);
          auto valeKsaDefM = globalState->program->getStaticSizedArray(valeKsaMT);
          int length = valeKsaDefM->size;
          auto valeMemberRefMT = valeKsaDefM->rawArray->elementType;

          auto dryRunBoolLE = globalState->getRegion(boolMT)->checkValidReference(FL(), functionState, builder, boolMT, dryRunBoolRef);
          buildIf(
              globalState, functionState, builder, LLVMBuildNot(builder, dryRunBoolLE, "notDryRun"),
              [this, functionState, valeMemberRefMT, length, regionInstanceRef, hostObjectRefMT, hostKsaRef, dryRunBoolRef, valeObjectRefMT, hostKsaMT, valeKsaMT, valeObjectRef, serializeMemberOrElement](
                  LLVMBuilderRef thenBuilder) mutable {
                intRangeLoopReverse(
                    globalState, functionState, thenBuilder, globalState->metalCache->i32, globalState->constI32(length),
                    [this, functionState, hostObjectRefMT, hostKsaRef, valeObjectRefMT, hostKsaMT, valeKsaMT, valeObjectRef, valeMemberRefMT, regionInstanceRef, serializeMemberOrElement, dryRunBoolRef](
                        Ref indexRef, LLVMBuilderRef bodyBuilder){

                      auto sourceMemberRef =
                          globalState->getRegion(valeObjectRefMT)
                              ->loadElementFromSSA(functionState, bodyBuilder, valeObjectRefMT, valeKsaMT, valeObjectRef, true, indexRef)
                              .move();
                      auto hostElementRef =
                          serializeMemberOrElement(
                              functionState, bodyBuilder, valeMemberRefMT, regionInstanceRef, sourceMemberRef, dryRunBoolRef);
                      initializeElementInSSA(
                          functionState, bodyBuilder, hostObjectRefMT, hostKsaMT, hostKsaRef, true, indexRef, hostElementRef);
                    });
              });


          LLVMBuildRet(builder, checkValidReference(FL(), functionState, builder, hostKsaRefMT, hostKsaRef));
        } else assert(false);
      });
}

Kind* Linear::linearizeKind(Kind* kindMT) {
  assert(globalState->getRegion(kindMT) == globalState->rcImm);
  return hostKindByValeKind.find(kindMT)->second;
}

StructKind* Linear::linearizeStructKind(StructKind* kindMT) {
  assert(globalState->getRegion(kindMT) == globalState->rcImm);
  auto kind = hostKindByValeKind.find(kindMT)->second;
  auto structKind = dynamic_cast<StructKind*>(kind);
  return structKind;
}

StaticSizedArrayT* Linear::unlinearizeSSA(StaticSizedArrayT *kindMT) {
  assert(globalState->getRegion(kindMT) == globalState->linearRegion);
  auto kind = valeKindByHostKind.find(kindMT)->second;
  auto ssaMT = dynamic_cast<StaticSizedArrayT*>(kind);
  return ssaMT;
}

StructKind* Linear::unlinearizeStructKind(StructKind* kindMT) {
  assert(globalState->getRegion(kindMT) == globalState->linearRegion);
  auto kind = valeKindByHostKind.find(kindMT)->second;
  auto structKind = dynamic_cast<StructKind*>(kind);
  return structKind;
}

InterfaceKind* Linear::unlinearizeInterfaceKind(InterfaceKind* kindMT) {
  assert(globalState->getRegion(kindMT) == globalState->linearRegion);
  auto kind = valeKindByHostKind.find(kindMT)->second;
  auto interfaceKind = dynamic_cast<InterfaceKind*>(kind);
  return interfaceKind;
}

InterfaceKind* Linear::linearizeInterfaceKind(InterfaceKind* kindMT) {
  assert(globalState->getRegion(kindMT) == globalState->rcImm);
  auto kind = hostKindByValeKind.find(kindMT)->second;
  auto interfaceKind = dynamic_cast<InterfaceKind*>(kind);
  return interfaceKind;
}

Reference* Linear::linearizeReference(Reference* immRcRefMT) {
  assert(globalState->getRegion(immRcRefMT) == globalState->rcImm);
  auto hostKind = hostKindByValeKind.find(immRcRefMT->kind)->second;
  return globalState->metalCache->getReference(
      immRcRefMT->ownership, immRcRefMT->location, hostKind);
}

Reference* Linear::unlinearizeReference(Reference* hostRefMT) {
  assert(globalState->getRegion(hostRefMT) == globalState->linearRegion);
  auto valeKind = valeKindByHostKind.find(hostRefMT->kind)->second;
  return globalState->metalCache->getReference(
      hostRefMT->ownership, hostRefMT->location, valeKind);
}

void Linear::initializeElementInRSA(
    FunctionState *functionState,
    LLVMBuilderRef builder,
    Reference *hostUsaRefMT,
    RuntimeSizedArrayT *hostUsaMT,
    Ref hostUsaRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {
  buildFlare(FL(), globalState, functionState, builder);
  assert(hostUsaRefMT->kind == hostUsaMT);
  assert(globalState->getRegion(hostUsaMT) == this);

  buildFlare(FL(), globalState, functionState, builder);
  auto valeKindMT = valeKindByHostKind.find(hostUsaMT)->second;
  auto valeUsaMT = dynamic_cast<RuntimeSizedArrayT*>(valeKindMT);
  assert(valeUsaMT);
  auto valeElementRefMT = globalState->program->getRuntimeSizedArray(valeUsaMT)->rawArray->elementType;
  auto hostElementRefMT = linearizeReference(valeElementRefMT);
  auto elementRefLE = globalState->getRegion(hostElementRefMT)->checkValidReference(FL(), functionState, builder, hostElementRefMT, elementRef);

  buildFlare(FL(), globalState, functionState, builder);
  auto i32MT = globalState->metalCache->i32Ref;

  auto indexLE = globalState->getRegion(i32MT)->checkValidReference(FL(), functionState, builder, i32MT, indexRef);

  buildFlare(FL(), globalState, functionState, builder);
  auto rsaPtrLE = checkValidReference(FL(), functionState, builder, hostUsaRefMT, hostUsaRef);
  auto hostUsaElementsPtrLE = structs.getRuntimeSizedArrayElementsPtr(functionState, builder, rsaPtrLE);

  buildFlare(FL(), globalState, functionState, builder, "Storing in: ", indexLE);
//  LLVMBuildStore(
//      builder,
//      elementRefLE,
//      LLVMBuildGEP(
//          builder, hostUsaElementsPtrLE, &indexLE, 1, "indexPtr"));
  buildFlare(FL(), globalState, functionState, builder);
  storeInnerArrayMember(globalState, functionState, builder, hostUsaElementsPtrLE, indexLE, elementRefLE);
  buildFlare(FL(), globalState, functionState, builder);
}

Ref Linear::deinitializeElementFromRSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Ref arrayRef,
    bool arrayRefKnownLive,
    Ref indexRef) {
  assert(false);
  exit(1);
}

void Linear::initializeElementInSSA(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* hostKsaRefMT,
    StaticSizedArrayT* hostKsaMT,
    Ref hostKsaRef,
    bool arrayRefKnownLive,
    Ref indexRef,
    Ref elementRef) {

  assert(hostKsaRefMT->kind == hostKsaMT);
  assert(globalState->getRegion(hostKsaMT) == this);

  auto valeKindMT = valeKindByHostKind.find(hostKsaMT)->second;
  auto valeKsaMT = dynamic_cast<StaticSizedArrayT*>(valeKindMT);
  assert(valeKsaMT);
  auto valeElementRefMT = globalState->program->getStaticSizedArray(valeKsaMT)->rawArray->elementType;
  auto hostElementRefMT = linearizeReference(valeElementRefMT);
  auto elementRefLE = globalState->getRegion(hostElementRefMT)->checkValidReference(FL(), functionState, builder, hostElementRefMT, elementRef);

  auto i32MT = globalState->metalCache->i32Ref;

  auto indexLE = globalState->getRegion(i32MT)->checkValidReference(FL(), functionState, builder, i32MT, indexRef);

  auto rsaPtrLE = checkValidReference(FL(), functionState, builder, hostKsaRefMT, hostKsaRef);
  auto hostKsaElementsPtrLE = structs.getStaticSizedArrayElementsPtr(functionState, builder, rsaPtrLE);

  storeInnerArrayMember(globalState, functionState, builder, hostKsaElementsPtrLE, indexLE, elementRefLE);
}

Ref Linear::deinitializeElementFromSSA(
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

Weakability Linear::getKindWeakability(Kind* kind) {
  return Weakability::NON_WEAKABLE;
}

LLVMValueRef Linear::getInterfaceMethodFunctionPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    int indexInEdge) {

  assert(indexInEdge == 0); // All the below is special cased for just the unserialize method.

  auto hostInterfaceMT = dynamic_cast<InterfaceKind*>(virtualParamMT->kind);
  assert(hostInterfaceMT);
  auto valeInterfaceMT = unlinearizeInterfaceKind(hostInterfaceMT);

  LLVMValueRef edgeNumLE = nullptr;
  LLVMValueRef newVirtualArgLE = nullptr;
  std::tie(edgeNumLE, newVirtualArgLE) =
      explodeInterfaceRef(functionState, builder, virtualParamMT, virtualArgRef);
  buildFlare(FL(), globalState, functionState, builder);

  auto orderedSubstructs = structs.getOrderedSubstructs(hostInterfaceMT);
  auto isValidEdgeNumLE =
      LLVMBuildICmp(builder, LLVMIntULT, edgeNumLE, constI64LE(globalState, orderedSubstructs.size()), "isValidEdgeNum");

  buildIf(
      globalState, functionState, builder, isZeroLE(builder, isValidEdgeNumLE),
      [this, edgeNumLE](LLVMBuilderRef thenBuilder) {
          buildPrint(globalState, thenBuilder, "Invalid edge number (");
          buildPrint(globalState, thenBuilder, edgeNumLE);
          buildPrint(globalState, thenBuilder, "), exiting!\n");
          auto exitCodeIntLE = LLVMConstInt(LLVMInt8TypeInContext(globalState->context), 1, false);
          LLVMBuildCall(thenBuilder, globalState->externs->exit, &exitCodeIntLE, 1, "");
      });

  auto functionLT = globalState->getInterfaceFunctionTypes(hostInterfaceMT)[indexInEdge];

  // This is a function family table, in that it's a table of all of an abstract function's overrides.
  // It's a function family, in Valestrom terms.
  auto fftableLT = LLVMArrayType(functionLT, orderedSubstructs.size());
  auto fftablePtrLE = makeMidasLocal(functionState, builder, fftableLT, "arrays", LLVMGetUndef(fftableLT));
  for (int i = 0; i < orderedSubstructs.size(); i++) {
    auto hostStructMT = orderedSubstructs[i];
    auto valeStructMT = unlinearizeStructKind(hostStructMT);
    auto prototype = globalState->rcImm->getUnserializeThunkPrototype(valeStructMT, valeInterfaceMT);
    auto funcLE = globalState->lookupFunction(prototype);
    auto bitcastedFuncLE = LLVMBuildPointerCast(builder, funcLE, functionLT, "bitcastedFunc");
    // We're using store here because LLVMBuildInsertElement caused LLVM to go into an infinite loop and crash.
    std::vector<LLVMValueRef> indices = { constI64LE(globalState, 0), constI64LE(globalState, i) };
    auto destPtrLE = LLVMBuildGEP(builder, fftablePtrLE, indices.data(), indices.size(), "storeMethodPtrPtr");
    LLVMBuildStore(builder, bitcastedFuncLE, destPtrLE);
  }
  std::vector<LLVMValueRef> indices = { constI64LE(globalState, 0), edgeNumLE };
  auto methodPtrPtrLE = LLVMBuildGEP(builder, fftablePtrLE, indices.data(), indices.size(), "methodPtrPtr");
  auto methodFuncPtr = LLVMBuildLoad(builder, methodPtrPtrLE, "methodFuncPtr");
  return methodFuncPtr;
}

LLVMValueRef Linear::stackify(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Local* local,
    Ref refToStore,
    bool knownLive) {
  auto toStoreLE = checkValidReference(FL(), functionState, builder, local->type, refToStore);
  auto typeLT = translateType(local->type);
  return makeMidasLocal(functionState, builder, typeLT, local->id->maybeName.c_str(), toStoreLE);
}

Ref Linear::unstackify(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return loadLocal(functionState, builder, local, localAddr);
}

Ref Linear::loadLocal(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  return normalLocalLoad(globalState, functionState, builder, local, localAddr);
}

Ref Linear::localStore(FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore, bool knownLive) {
  return normalLocalStore(globalState, functionState, builder, local, localAddr, refToStore);
}
