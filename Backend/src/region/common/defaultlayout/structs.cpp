
#include "../common.h"
#include "../../../function/expressions/shared/shared.h"
#include "../../../function/expressions/shared/string.h"
#include "structs.h"
#include <region/common/migration.h>


constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI = 0;

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;
constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI = 1;

KindStructs::KindStructs(
    GlobalState* globalState_,
    ControlBlock nonWeakableControlBlock_,
    ControlBlock weakableControlBlock_,
    LLVMTypeRef weakRefHeaderStructL_)
    : globalState(globalState_),
      nonWeakableControlBlock(std::move(nonWeakableControlBlock_)),
      weakableControlBlock(std::move(weakableControlBlock_)),
      weakRefHeaderStructL(weakRefHeaderStructL_),
      structWeakRefStructs(0, globalState_->addressNumberer->makeHasher<StructKind*>()),
      interfaceWeakRefStructs(0, globalState_->addressNumberer->makeHasher<InterfaceKind*>()),
      staticSizedArrayWeakRefStructs(0, globalState_->addressNumberer->makeHasher<StaticSizedArrayT*>()),
      runtimeSizedArrayWeakRefStructs(0, globalState_->addressNumberer->makeHasher<RuntimeSizedArrayT*>()),
      interfaceTableStructs(0, globalState_->addressNumberer->makeHasher<InterfaceKind*>()),
      interfaceRefStructs(0, globalState_->addressNumberer->makeHasher<InterfaceKind*>()) {

//  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  {
    stringInnerStructL = LLVMStructCreateNamed(globalState->context, "__Str");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));
    memberTypesL.push_back(LLVMArrayType(int8LT, 0));
    LLVMStructSetBody(
        stringInnerStructL, memberTypesL.data(), memberTypesL.size(), false);
  }

  {
    stringWrapperStructL = LLVMStructCreateNamed(globalState->context, "__Str_rc");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(nonWeakableControlBlock.getStruct());
    memberTypesL.push_back(stringInnerStructL);
    LLVMStructSetBody(
        stringWrapperStructL, memberTypesL.data(), memberTypesL.size(), false);
  }

  assert(weakRefHeaderStructL);

  // This is a weak ref to a void*. When we're calling an interface method on a weak,
  // we have no idea who the receiver is. They'll receive this struct as the correctly
  // typed flavor of it (from structWeakRefStructs).
  weakVoidRefStructL =
      LLVMStructCreateNamed(
          globalState->context, "__Weak_VoidP");
  std::vector<LLVMTypeRef> structWeakRefStructMemberTypesL;
  structWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
  structWeakRefStructMemberTypesL.push_back(LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0));
  LLVMStructSetBody(weakVoidRefStructL, structWeakRefStructMemberTypesL.data(), structWeakRefStructMemberTypesL.size(), false);
}

//ControlBlock* KindStructs::getControlBlock() {
//  return &controlBlock;
//}
ControlBlock* KindStructs::getControlBlock(Kind* kind) {
  if (auto structMT = dynamic_cast<StructKind*>(kind)) {
    return structIsWeakable(structMT) == Weakability::WEAKABLE ? &weakableControlBlock : &nonWeakableControlBlock;
  } else if (auto interfaceMT = dynamic_cast<InterfaceKind*>(kind)) {
    return interfaceIsWeakable(interfaceMT) == Weakability::WEAKABLE ? &weakableControlBlock : &nonWeakableControlBlock;
  } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(kind)) {
    return staticSizedArrayIsWeakable(ssaMT) == Weakability::WEAKABLE ? &weakableControlBlock : &nonWeakableControlBlock;
  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(kind)) {
    return runtimeSizedArrayIsWeakable(rsaMT) == Weakability::WEAKABLE ? &weakableControlBlock : &nonWeakableControlBlock;
  } else if (auto strMT = dynamic_cast<Str*>(kind)) {
    return &nonWeakableControlBlock;
  } else {
    assert(false);
  }
}
LLVMTypeRef KindStructs::getStructInnerStruct(StructKind* structKind) {
  auto structIter = structInnerStructs.find(structKind->fullName->name);
  assert(structIter != structInnerStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getWrapperStruct(Kind* kind) {
  if (auto structMT = dynamic_cast<StructKind*>(kind)) {
    return getStructWrapperStruct(structMT);
  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(kind)) {
    return getRuntimeSizedArrayWrapperStruct(rsaMT);
  } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(kind)) {
    return getStaticSizedArrayWrapperStruct(ssaMT);
  } else {
    // Remember, interfaces dont have wrapper structs
    assert(false);
  }
  assert(false);
}
LLVMTypeRef KindStructs::getStructWrapperStruct(StructKind* structKind) {
  auto structIter = structWrapperStructs.find(structKind->fullName->name);
  assert(structIter != structWrapperStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getStaticSizedArrayWrapperStruct(StaticSizedArrayT* ssaMT) {
  auto structIter = staticSizedArrayWrapperStructs.find(ssaMT->name->name);
  assert(structIter != staticSizedArrayWrapperStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getRuntimeSizedArrayWrapperStruct(RuntimeSizedArrayT* rsaMT) {
  auto structIter = runtimeSizedArrayWrapperStructs.find(rsaMT->name->name);
  assert(structIter != runtimeSizedArrayWrapperStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getInterfaceRefStruct(InterfaceKind* interfaceKind) {
  auto structIter = interfaceRefStructs.find(interfaceKind);
  assert(structIter != interfaceRefStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getInterfaceTableStruct(InterfaceKind* interfaceKind) {
  auto structIter = interfaceTableStructs.find(interfaceKind);
  assert(structIter != interfaceTableStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getStringWrapperStruct() {
  return stringWrapperStructL;
}

LLVMTypeRef KindStructs::getStructWeakRefStruct(StructKind* structKind) {
  auto structIter = structWeakRefStructs.find(structKind);
  assert(structIter != structWeakRefStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getStaticSizedArrayWeakRefStruct(StaticSizedArrayT* ssaMT) {
  auto structIter = staticSizedArrayWeakRefStructs.find(ssaMT);
  assert(structIter != staticSizedArrayWeakRefStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getRuntimeSizedArrayWeakRefStruct(RuntimeSizedArrayT* rsaMT) {
  auto structIter = runtimeSizedArrayWeakRefStructs.find(rsaMT);
  assert(structIter != runtimeSizedArrayWeakRefStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getInterfaceWeakRefStruct(InterfaceKind* interfaceKind) {
  auto interfaceIter = interfaceWeakRefStructs.find(interfaceKind);
  assert(interfaceIter != interfaceWeakRefStructs.end());
  return interfaceIter->second;
}


void KindStructs::declareStruct(StructKind* structM, Weakability weakable) {
  auto innerStructL =
      LLVMStructCreateNamed(
          globalState->context, structM->fullName->name.c_str());
  if (structInnerStructs.count(structM->fullName->name) != 0) {
    std::cerr << "Can't declare " << structM->fullName->name << ", already declared!" << std::endl;
    exit(1);
  }
  structInnerStructs.emplace(structM->fullName->name, innerStructL);

  auto wrapperStructL =
      LLVMStructCreateNamed(
          globalState->context, (structM->fullName->name + "rc").c_str());
  assert(structWrapperStructs.count(structM->fullName->name) == 0);
  structWrapperStructs.emplace(structM->fullName->name, wrapperStructL);

  if (weakable == Weakability::WEAKABLE) {
    auto structWeakRefStructL =
        LLVMStructCreateNamed(
            globalState->context, (structM->fullName->name + "w").c_str());
    assert(structWeakRefStructs.count(structM) == 0);
    structWeakRefStructs.emplace(structM, structWeakRefStructL);
  }
}

void KindStructs::defineStruct(
    StructKind* structKind,
    std::vector<LLVMTypeRef> membersLT) {
  assert(weakRefHeaderStructL);
  Weakability weakable = structIsWeakable(structKind);

  LLVMTypeRef valStructL = getStructInnerStruct(structKind);
  LLVMStructSetBody(
      valStructL, membersLT.data(), membersLT.size(), false);

  LLVMTypeRef wrapperStructL = getStructWrapperStruct(structKind);
  std::vector<LLVMTypeRef> wrapperStructMemberTypesL;

  // First member is a ref counts struct. We don't include the int directly
  // because we want fat pointers to point to this struct, so they can reach
  // into it and increment without doing any casting.
  wrapperStructMemberTypesL.push_back(weakable == Weakability::WEAKABLE ? weakableControlBlock.getStruct() : nonWeakableControlBlock.getStruct());

  wrapperStructMemberTypesL.push_back(valStructL);

  LLVMStructSetBody(
      wrapperStructL, wrapperStructMemberTypesL.data(), wrapperStructMemberTypesL.size(), false);

  if (weakable == Weakability::WEAKABLE) {
    auto structWeakRefStructL = getStructWeakRefStruct(structKind);
    std::vector<LLVMTypeRef> structWeakRefStructMemberTypesL;
    structWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
    structWeakRefStructMemberTypesL.push_back(LLVMPointerType(wrapperStructL, 0));
    LLVMStructSetBody(
        structWeakRefStructL, structWeakRefStructMemberTypesL.data(), structWeakRefStructMemberTypesL.size(), false);
  }
}

Weakability KindStructs::structIsWeakable(StructKind* struuct) {
  return structWeakRefStructs.find(struuct) != structWeakRefStructs.end() ? Weakability::WEAKABLE : Weakability::NON_WEAKABLE;
}

Weakability KindStructs::interfaceIsWeakable(InterfaceKind* interface) {
  return interfaceWeakRefStructs.find(interface) != interfaceWeakRefStructs.end() ? Weakability::WEAKABLE : Weakability::NON_WEAKABLE;
}

Weakability KindStructs::staticSizedArrayIsWeakable(StaticSizedArrayT* ssaMT) {
  return staticSizedArrayWeakRefStructs.find(ssaMT) != staticSizedArrayWeakRefStructs.end() ? Weakability::WEAKABLE : Weakability::NON_WEAKABLE;
}

Weakability KindStructs::runtimeSizedArrayIsWeakable(RuntimeSizedArrayT* ssaMT) {
  return runtimeSizedArrayWeakRefStructs.find(ssaMT) != runtimeSizedArrayWeakRefStructs.end() ? Weakability::WEAKABLE : Weakability::NON_WEAKABLE;
}

void KindStructs::declareEdge(
    Edge* edge) {
  auto interfaceTableStructL =
      getInterfaceTableStruct(edge->interfaceName);

  auto edgeName =
      edge->structName->fullName->name + edge->interfaceName->fullName->name;
  auto itablePtr =
      LLVMAddGlobal(globalState->mod, interfaceTableStructL, edgeName.c_str());
  LLVMSetLinkage(itablePtr, LLVMExternalLinkage);
  // ITables need to be 16-byte aligned, see ITN16BA.
  LLVMSetAlignment(itablePtr, 16);

  globalState->interfaceTablePtrs.emplace(edge, itablePtr);
}

void KindStructs::defineEdge(
    Edge* edge,
    std::vector<LLVMTypeRef> interfaceFunctionsLT,
    std::vector<FuncPtrLE> functions) {
  auto interfaceTableStructL =
      getInterfaceTableStruct(edge->interfaceName);
  auto builder = LLVMCreateBuilderInContext(globalState->context);
  auto itableLE = LLVMGetUndef(interfaceTableStructL);
  for (int i = 0; i < functions.size(); i++) {
    auto entryLE = LLVMConstBitCast(functions[i].ptrLE, interfaceFunctionsLT[i]);
    itableLE = LLVMBuildInsertValue(builder, itableLE, entryLE, i, std::to_string(i).c_str());
  }
  LLVMDisposeBuilder(builder);

  auto itablePtr = globalState->getInterfaceTablePtr(edge);
  LLVMSetInitializer(itablePtr,  itableLE);
}

void KindStructs::declareInterface(InterfaceKind* interface, Weakability weakable) {
  assert(interfaceTableStructs.count(interface) == 0);
  auto interfaceTableStructL =
      LLVMStructCreateNamed(
          globalState->context, (interface->fullName->name + "itable").c_str());
  interfaceTableStructs.emplace(interface, interfaceTableStructL);


  assert(interfaceRefStructs.count(interface) == 0);

  auto interfaceRefStructL =
      LLVMStructCreateNamed(
          globalState->context, interface->fullName->name.c_str());

  std::vector<LLVMTypeRef> refStructMemberTypesL;

  // this points to the control block.
  // It makes it easier to increment and decrement ref counts.
  refStructMemberTypesL.push_back(LLVMPointerType(weakable == Weakability::WEAKABLE ? weakableControlBlock.getStruct() : nonWeakableControlBlock.getStruct(), 0));

  refStructMemberTypesL.push_back(LLVMPointerType(interfaceTableStructL, 0));
  LLVMStructSetBody(
      interfaceRefStructL,
      refStructMemberTypesL.data(),
      refStructMemberTypesL.size(),
      false);

  interfaceRefStructs.emplace(interface, interfaceRefStructL);

  if (weakable == Weakability::WEAKABLE) {
    auto interfaceWeakRefStructL =
        LLVMStructCreateNamed(
            globalState->context, (interface->fullName->name + "w").c_str());
    assert(interfaceWeakRefStructs.count(interface) == 0);

    interfaceWeakRefStructs.emplace(interface, interfaceWeakRefStructL);

    LLVMTypeRef refStructL = getInterfaceRefStruct(interface);

    std::vector<LLVMTypeRef> interfaceWeakRefStructMemberTypesL;
    interfaceWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
    interfaceWeakRefStructMemberTypesL.push_back(refStructL);
    LLVMStructSetBody(
        interfaceWeakRefStructL, interfaceWeakRefStructMemberTypesL.data(), interfaceWeakRefStructMemberTypesL.size(),
        false);
  }
}

void KindStructs::defineInterface(
    InterfaceDefinition* interface,
    std::vector<LLVMTypeRef> interfaceMethodTypesL) {
  assert(weakRefHeaderStructL);

  LLVMTypeRef itableStruct =
      getInterfaceTableStruct(interface->kind);

  LLVMStructSetBody(
      itableStruct, interfaceMethodTypesL.data(), interfaceMethodTypesL.size(), false);
}


void KindStructs::declareStaticSizedArray(
    StaticSizedArrayT* staticSizedArrayMT,
    Weakability weakable) {
  auto countedStruct = LLVMStructCreateNamed(globalState->context, staticSizedArrayMT->name->name.c_str());
  staticSizedArrayWrapperStructs.emplace(staticSizedArrayMT->name->name, countedStruct);

  if (weakable == Weakability::WEAKABLE) {
    auto weakRefStructL =
        LLVMStructCreateNamed(
            globalState->context, (staticSizedArrayMT->name->name + "w").c_str());
    assert(staticSizedArrayWeakRefStructs.count(staticSizedArrayMT) == 0);
    staticSizedArrayWeakRefStructs.emplace(staticSizedArrayMT, weakRefStructL);
  }
}

void KindStructs::declareRuntimeSizedArray(
    RuntimeSizedArrayT* runtimeSizedArrayMT,
    Weakability weakable) {
  auto countedStruct = LLVMStructCreateNamed(globalState->context, (runtimeSizedArrayMT->name->name + "rc").c_str());
  runtimeSizedArrayWrapperStructs.emplace(runtimeSizedArrayMT->name->name, countedStruct);

  if (weakable == Weakability::WEAKABLE) {
    auto weakRefStructL =
        LLVMStructCreateNamed(
            globalState->context, (runtimeSizedArrayMT->name->name + "w").c_str());
    assert(runtimeSizedArrayWeakRefStructs.count(runtimeSizedArrayMT) == 0);
    runtimeSizedArrayWeakRefStructs.emplace(runtimeSizedArrayMT, weakRefStructL);
  }
}

void KindStructs::defineRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT,
    LLVMTypeRef elementLT,
    bool capacityExists) {
  assert(weakRefHeaderStructL);
  Weakability weakable = runtimeSizedArrayIsWeakable(runtimeSizedArrayMT->kind);

  auto runtimeSizedArrayWrapperStruct = getRuntimeSizedArrayWrapperStruct(runtimeSizedArrayMT->kind);
  auto innerArrayLT = LLVMArrayType(elementLT, 0);

  std::vector<LLVMTypeRef> elementsL;

  elementsL.push_back(weakable == Weakability::WEAKABLE ? weakableControlBlock.getStruct() : nonWeakableControlBlock.getStruct());

  // Size
  elementsL.push_back(LLVMInt32TypeInContext(globalState->context));
  if (capacityExists) {
    // Capacity
    elementsL.push_back(LLVMInt32TypeInContext(globalState->context));
  }

  elementsL.push_back(innerArrayLT);

  LLVMStructSetBody(runtimeSizedArrayWrapperStruct, elementsL.data(), elementsL.size(), false);

  if (runtimeSizedArrayIsWeakable(runtimeSizedArrayMT->kind) == Weakability::WEAKABLE) {
    auto arrayWeakRefStructL = getRuntimeSizedArrayWeakRefStruct(runtimeSizedArrayMT->kind);
    std::vector<LLVMTypeRef> arrayWeakRefStructMemberTypesL;
    arrayWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
    arrayWeakRefStructMemberTypesL.push_back(LLVMPointerType(runtimeSizedArrayWrapperStruct, 0));
    LLVMStructSetBody(
        arrayWeakRefStructL, arrayWeakRefStructMemberTypesL.data(), arrayWeakRefStructMemberTypesL.size(), false);
  }
}

void KindStructs::defineStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT,
    LLVMTypeRef elementLT) {
  assert(weakRefHeaderStructL);
  Weakability weakable = staticSizedArrayIsWeakable(staticSizedArrayMT->kind);

  auto staticSizedArrayWrapperStruct = getStaticSizedArrayWrapperStruct(staticSizedArrayMT->kind);

  auto innerArrayLT = LLVMArrayType(elementLT, staticSizedArrayMT->size);

  std::vector<LLVMTypeRef> elementsL;

  elementsL.push_back(weakable == Weakability::WEAKABLE ? weakableControlBlock.getStruct() : nonWeakableControlBlock.getStruct());

  elementsL.push_back(innerArrayLT);

  LLVMStructSetBody(staticSizedArrayWrapperStruct, elementsL.data(), elementsL.size(), false);

  if (staticSizedArrayIsWeakable(staticSizedArrayMT->kind) == Weakability::WEAKABLE) {
    auto arrayWeakRefStructL = getStaticSizedArrayWeakRefStruct(staticSizedArrayMT->kind);
    std::vector<LLVMTypeRef> arrayWeakRefStructMemberTypesL;
    arrayWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
    arrayWeakRefStructMemberTypesL.push_back(LLVMPointerType(staticSizedArrayWrapperStruct, 0));
    LLVMStructSetBody(
        arrayWeakRefStructL, arrayWeakRefStructMemberTypesL.data(), arrayWeakRefStructMemberTypesL.size(), false);
  }
}

WeakFatPtrLE KindStructs::makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) {
  if (auto structKindM = dynamic_cast<StructKind*>(referenceM_->kind)) {
    assert(LLVMTypeOf(ptrLE) == getStructWeakRefStruct(structKindM));
  } else if (auto interfaceKindM = dynamic_cast<InterfaceKind*>(referenceM_->kind)) {
    assert(
        LLVMTypeOf(ptrLE) == weakVoidRefStructL ||
            LLVMTypeOf(ptrLE) == getInterfaceWeakRefStruct(interfaceKindM));
  } else if (auto ssaT = dynamic_cast<StaticSizedArrayT*>(referenceM_->kind)) {
    assert(LLVMTypeOf(ptrLE) == getStaticSizedArrayWeakRefStruct(ssaT));
  } else if (auto rsaT = dynamic_cast<RuntimeSizedArrayT*>(referenceM_->kind)) {
    assert(LLVMTypeOf(ptrLE) == getRuntimeSizedArrayWeakRefStruct(rsaT));
  } else {
    assert(false);
  }
  return WeakFatPtrLE(referenceM_, ptrLE);
}

WeakFatPtrLE KindStructs::downcastWeakFatPtr(
    LLVMBuilderRef builder,
    StructKind* targetStructKind,
    Reference* targetRefMT,
    LLVMValueRef sourceWeakFatPtrLE) {
  assert(targetRefMT->kind == targetStructKind);
  auto weakRefVoidStructLT = getWeakVoidRefStruct(targetStructKind);
  assert(LLVMTypeOf(sourceWeakFatPtrLE) == weakRefVoidStructLT);

  auto weakRefHeaderStruct =
      LLVMBuildExtractValue(builder, sourceWeakFatPtrLE, 0, "weakHeader");
  auto objVoidPtrLE =
      LLVMBuildExtractValue(builder, sourceWeakFatPtrLE, 1, "objVoidPtr");

  auto underlyingStructPtrLT =
      LLVMPointerType(getStructWrapperStruct(targetStructKind), 0);
  auto underlyingStructPtrLE =
      LLVMBuildBitCast(builder, objVoidPtrLE, underlyingStructPtrLT, "subtypePtr");

  auto resultStructRefLT = getStructWeakRefStruct(targetStructKind);
  auto resultStructRefLE = LLVMGetUndef(resultStructRefLT);
  resultStructRefLE = LLVMBuildInsertValue(builder, resultStructRefLE, weakRefHeaderStruct, 0, "withHeader");
  resultStructRefLE = LLVMBuildInsertValue(builder, resultStructRefLE, underlyingStructPtrLE, 1, "withBoth");

  auto targetWeakRef = makeWeakFatPtr(targetRefMT, resultStructRefLE);
  return targetWeakRef;
}

ControlBlockPtrLE KindStructs::getConcreteControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* reference,
    WrapperPtrLE wrapperPtrLE) {
  auto controlBlock = getControlBlock(reference->kind);
  // Control block is always the 0th element of every concrete struct.
  auto controlBlockPtrLE =
      LLVMBuildStructGEP2(builder, wrapperPtrLE.wrapperStructLT, wrapperPtrLE.refLE, 0, "controlPtr");
  assert(LLVMTypeOf(controlBlockPtrLE) == LLVMPointerType(controlBlock->getStruct(), 0));
  return makeControlBlockPtr(
      from, functionState, builder, wrapperPtrLE.refM->kind, controlBlockPtrLE);
}

ControlBlockPtrLE KindStructs::getConcreteControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* reference,
    WrapperPtrLE wrapperPtrLE) {
  // Control block is always the 0th element of every concrete struct.
  return makeControlBlockPtrWithoutChecking(
      from, functionState, builder,
      wrapperPtrLE.refM->kind,
      LLVMBuildStructGEP2(
          builder, wrapperPtrLE.wrapperStructLT, wrapperPtrLE.refLE, 0, "controlPtr"));
}

WrapperPtrLE KindStructs::makeWrapperPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    LLVMValueRef ptrLE) {
  assert(ptrLE != nullptr);
  Kind* kind = referenceM->kind;

  WrapperPtrLE wrapperPtrLE = makeWrapperPtrWithoutChecking(checkerAFL, functionState, builder, referenceM, ptrLE);

  if (dynamic_cast<StructKind*>(kind)) {
    auto controlBlockPtrLE = getConcreteControlBlockPtr(checkerAFL, functionState, builder, referenceM, wrapperPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE.refLE);
  } else if (dynamic_cast<InterfaceKind*>(kind)) {
    // can we even get a wrapper struct for an interface?
    assert(false);
  } else if (dynamic_cast<StaticSizedArrayT*>(kind)) {
    auto controlBlockPtrLE = getConcreteControlBlockPtr(checkerAFL, functionState, builder, referenceM, wrapperPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE.refLE);
  } else if (dynamic_cast<RuntimeSizedArrayT*>(kind)) {
    auto controlBlockPtrLE = getConcreteControlBlockPtr(checkerAFL, functionState, builder, referenceM, wrapperPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE.refLE);
  } else if (dynamic_cast<Str*>(kind)) {
    auto controlBlockPtrLE = getConcreteControlBlockPtr(checkerAFL, functionState, builder, referenceM, wrapperPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE.refLE);
  } else assert(false);

  return wrapperPtrLE;
}

InterfaceFatPtrLE KindStructs::makeInterfaceFatPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM_,
    LLVMValueRef ptrLE) {
  auto interfaceFatPtrLE =
      makeInterfaceFatPtrWithoutChecking(checkerAFL, functionState, builder, referenceM_, ptrLE);

  auto controlBlockPtrLE = getObjPtrFromInterfaceRef(builder, interfaceFatPtrLE);
  buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);

  return interfaceFatPtrLE;
}

InterfaceFatPtrLE KindStructs::makeInterfaceFatPtrWithoutChecking(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM_,
    LLVMValueRef ptrLE) {
  auto interfaceKindM = dynamic_cast<InterfaceKind*>(referenceM_->kind);
  assert(interfaceKindM);
  assert(LLVMTypeOf(ptrLE) == getInterfaceRefStruct(interfaceKindM));

  auto interfaceFatPtrLE = InterfaceFatPtrLE(referenceM_, ptrLE);

  auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
  buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);

  return interfaceFatPtrLE;
}

ControlBlockPtrLE KindStructs::makeControlBlockPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Kind* kindM,
    LLVMValueRef controlBlockPtrLE) {
  auto result = makeControlBlockPtrWithoutChecking(checkerAFL, functionState, builder, kindM, controlBlockPtrLE);
  buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
  return result;
}


ControlBlockPtrLE KindStructs::makeControlBlockPtrWithoutChecking(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Kind* kindM,
    LLVMValueRef controlBlockPtrLE) {
  auto expectedControlBlockStructL = getControlBlock(kindM)->getStruct();
  return ControlBlockPtrLE(kindM, expectedControlBlockStructL, controlBlockPtrLE);
}

LLVMValueRef KindStructs::getStringBytesPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE ptrLE) {
  return getCharsPtrFromWrapperPtr(globalState, builder, stringInnerStructL, ptrLE);
}

LLVMValueRef KindStructs::getStringLen(
    FunctionState* functionState, LLVMBuilderRef builder, WrapperPtrLE ptrLE) {
  return getLenFromStrWrapperPtr(globalState->context, builder, stringInnerStructL, ptrLE);
}

LLVMValueRef KindStructs::getStringLenPtr(
    FunctionState* functionState, LLVMBuilderRef builder, WrapperPtrLE ptrLE) {
  return getLenPtrFromStrWrapperPtr(globalState->context, builder, stringInnerStructL, ptrLE);
}


ControlBlockPtrLE KindStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Kind* kindM,
    InterfaceFatPtrLE interfaceFatPtrLE) {
  // Interface fat pointer's first element points directly at the control block,
  // and we dont have to cast it. We would have to cast if we were accessing the
  // actual object though.
  return makeControlBlockPtr(
      from, functionState, builder,
      interfaceFatPtrLE.refM->kind,
      LLVMBuildExtractValue(builder, interfaceFatPtrLE.refLE, 0, "controlPtr"));
}

ControlBlockPtrLE KindStructs::getControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Kind* kindM,
    InterfaceFatPtrLE interfaceFatPtrLE) {
  // Interface fat pointer's first element points directly at the control block,
  // and we dont have to cast it. We would have to cast if we were accessing the
  // actual object though.
  return makeControlBlockPtrWithoutChecking(
      from, functionState, builder,
      interfaceFatPtrLE.refM->kind,
      LLVMBuildExtractValue(builder, interfaceFatPtrLE.refLE, 0, "controlPtr"));
}

ControlBlockPtrLE KindStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    Ref ref,
    Reference* referenceM) {
  auto kindM = referenceM->kind;
  if (dynamic_cast<InterfaceKind*>(kindM)) {
    auto referenceLE =
        makeInterfaceFatPtr(
            from, functionState, builder, referenceM,
            globalState->getRegion(referenceM)
                ->checkValidReference(from, functionState, builder, true, referenceM, ref));
    return getControlBlockPtr(from, functionState, builder, kindM, referenceLE);
  } else if (dynamic_cast<StructKind*>(kindM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->getRegion(referenceM)
                ->checkValidReference(from, functionState, builder, true, referenceM, ref));
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<StaticSizedArrayT*>(kindM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->getRegion(referenceM)
                ->checkValidReference(from, functionState, builder, true, referenceM, ref));
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<RuntimeSizedArrayT*>(kindM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->getRegion(referenceM)
                ->checkValidReference(from, functionState, builder, true, referenceM, ref));
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<Str*>(kindM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->getRegion(referenceM)
                ->checkValidReference(from, functionState, builder, true, referenceM, ref));
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else {
    assert(false);
  }
}

ControlBlockPtrLE KindStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  auto kindM = referenceM->kind;
  if (dynamic_cast<InterfaceKind*>(kindM)) {
    auto referenceLE = makeInterfaceFatPtr(from, functionState, builder, referenceM, ref);
    return getControlBlockPtr(from, functionState, builder, kindM, referenceLE);
  } else if (dynamic_cast<StructKind*>(kindM)) {
    auto referenceLE = makeWrapperPtr(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<StaticSizedArrayT*>(kindM)) {
    auto referenceLE = makeWrapperPtr(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<RuntimeSizedArrayT*>(kindM)) {
    auto referenceLE = makeWrapperPtr(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<Str*>(kindM)) {
    auto referenceLE = makeWrapperPtr(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else {
    assert(false);
  }
}

ControlBlockPtrLE KindStructs::getControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  auto kindM = referenceM->kind;
  if (dynamic_cast<InterfaceKind*>(kindM)) {
    auto referenceLE = makeInterfaceFatPtrWithoutChecking(from, functionState, builder, referenceM, ref);
    return getControlBlockPtrWithoutChecking(from, functionState, builder, kindM, referenceLE);
  } else if (dynamic_cast<StructKind*>(kindM)) {
    auto referenceLE = makeWrapperPtrWithoutChecking(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtrWithoutChecking(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<StaticSizedArrayT*>(kindM)) {
    auto referenceLE = makeWrapperPtrWithoutChecking(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtrWithoutChecking(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<RuntimeSizedArrayT*>(kindM)) {
    auto referenceLE = makeWrapperPtrWithoutChecking(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtrWithoutChecking(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<Str*>(kindM)) {
    auto referenceLE = makeWrapperPtrWithoutChecking(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtrWithoutChecking(from, functionState, builder, referenceM, referenceLE);
  } else {
    assert(false);
  }
}

LLVMValueRef KindStructs::getStructContentsPtr(
    LLVMBuilderRef builder,
    Kind* kind,
    WrapperPtrLE wrapperPtrLE) {
  return LLVMBuildStructGEP2(
      builder,
      wrapperPtrLE.wrapperStructLT,
      wrapperPtrLE.refLE,
      1, // Inner struct is after the control block.
      "contentsPtr");
}

LLVMValueRef KindStructs::getVoidPtrFromInterfacePtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE) {
  assert(LLVMTypeOf(virtualArgLE.refLE) == globalState->getRegion(virtualParamMT)->translateType(virtualParamMT));
  return LLVMBuildPointerCast(
      builder,
      getControlBlockPtr(FL(), functionState, builder, virtualParamMT->kind, virtualArgLE).refLE,
      LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0),
      "objAsVoidPtr");
}

LLVMValueRef KindStructs::getObjIdFromControlBlockPtr(
    LLVMBuilderRef builder,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtr) {
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  assert(globalState->opt->census);
  return LLVMBuildLoad2(
      builder,
      int64LT,
      LLVMBuildStructGEP2(
          builder,
          controlBlockPtr.structLT,
          controlBlockPtr.refLE,
          getControlBlock(kindM)->getMemberIndex(ControlBlockMember::CENSUS_OBJ_ID),
          "objIdPtr"),
      "objId");
}

LLVMValueRef KindStructs::downcastPtr(LLVMBuilderRef builder, Reference* resultStructRefMT, LLVMValueRef unknownPossibilityPtrLE) {
  auto resultStructRefLT = globalState->getRegion(resultStructRefMT)->translateType(resultStructRefMT);
  auto resultStructRefLE =
      LLVMBuildPointerCast(builder, unknownPossibilityPtrLE, resultStructRefLT, "subtypePtr");
  return resultStructRefLE;
}

LLVMValueRef KindStructs::getStrongRcFromControlBlockPtr(
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE structExpr) {
  auto int32LT = LLVMInt32TypeInContext(globalState->context);

  switch (globalState->opt->regionOverride) {
//    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V3:
      assert(refM->ownership == Ownership::SHARE);
      break;
    default:
      assert(false);
  }

  auto rcPtrLE = getStrongRcPtrFromControlBlockPtr(builder, refM, structExpr);
  return LLVMBuildLoad2(builder, int32LT, rcPtrLE, "rc");
}

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef KindStructs::getStrongRcPtrFromControlBlockPtr(
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V3:
      assert(refM->ownership == Ownership::SHARE);
      break;
    default:
      assert(false);
  }

  return LLVMBuildStructGEP2(
      builder,
      getControlBlock(refM->kind)->getStruct(),
      controlBlockPtr.refLE,
      getControlBlock(refM->kind)->getMemberIndex(ControlBlockMember::STRONG_RC_32B),
      "rcPtr");
}

WrapperPtrLE KindStructs::makeWrapperPtrWithoutChecking(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    LLVMValueRef ptrLE) {
  assert(ptrLE != nullptr);

  Kind* kind = referenceM->kind;
  LLVMTypeRef wrapperStructLT = nullptr;
  if (auto structKind = dynamic_cast<StructKind*>(kind)) {
    wrapperStructLT = getStructWrapperStruct(structKind);
  } else if (auto interfaceKind = dynamic_cast<InterfaceKind*>(kind)) {
    assert(false); // can we even get a wrapper struct for an interface?
  } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(kind)) {
    wrapperStructLT = getStaticSizedArrayWrapperStruct(ssaMT);
  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(kind)) {
    wrapperStructLT = getRuntimeSizedArrayWrapperStruct(rsaMT);
  } else if (auto strMT = dynamic_cast<Str*>(kind)) {
    wrapperStructLT = stringWrapperStructL;
  } else assert(false);
  assert(LLVMTypeOf(ptrLE) == LLVMPointerType(wrapperStructLT, 0));

  WrapperPtrLE wrapperPtrLE(referenceM, wrapperStructLT, ptrLE);

  return wrapperPtrLE;
}

