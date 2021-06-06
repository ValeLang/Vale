
#include <region/common/common.h>
#include <function/expressions/shared/shared.h>
#include <function/expressions/shared/string.h>
#include "structs.h"


constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI = 0;

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;
constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI = 1;


KindStructs::KindStructs(GlobalState* globalState_, ControlBlock controlBlock_)
  : globalState(globalState_),
    controlBlock(controlBlock_) {

//  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  {
    stringInnerStructL =
        LLVMStructCreateNamed(
            globalState->context, "__Str");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));
    memberTypesL.push_back(LLVMArrayType(int8LT, 0));
    LLVMStructSetBody(
        stringInnerStructL, memberTypesL.data(), memberTypesL.size(), false);
  }

  {
    stringWrapperStructL =
        LLVMStructCreateNamed(
            globalState->context, "__Str_rc");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(controlBlock.getStruct());
    memberTypesL.push_back(stringInnerStructL);
    LLVMStructSetBody(
        stringWrapperStructL, memberTypesL.data(), memberTypesL.size(), false);
  }
}

ControlBlock* KindStructs::getControlBlock() {
  return &controlBlock;
}
ControlBlock* KindStructs::getControlBlock(Kind* kind) {
  return &controlBlock;
}
LLVMTypeRef KindStructs::getInnerStruct(StructKind* structKind) {
  auto structIter = innerStructs.find(structKind->fullName->name);
  assert(structIter != innerStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getWrapperStruct(StructKind* structKind) {
  auto structIter = wrapperStructs.find(structKind->fullName->name);
  assert(structIter != wrapperStructs.end());
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
  auto structIter = interfaceRefStructs.find(interfaceKind->fullName->name);
  assert(structIter != interfaceRefStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getInterfaceTableStruct(InterfaceKind* interfaceKind) {
  auto structIter = interfaceTableStructs.find(interfaceKind->fullName->name);
  assert(structIter != interfaceTableStructs.end());
  return structIter->second;
}
LLVMTypeRef KindStructs::getStringWrapperStruct() {
  return stringWrapperStructL;
}

WeakableKindStructs::WeakableKindStructs(
  GlobalState* globalState_,
  ControlBlock controlBlock,
  LLVMTypeRef weakRefHeaderStructL_)
: globalState(globalState_),
  kindStructs(globalState_, std::move(controlBlock)),
  weakRefHeaderStructL(weakRefHeaderStructL_) {

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

ControlBlock* WeakableKindStructs::getControlBlock(Kind* kind) {
  return kindStructs.getControlBlock(kind);
}
ControlBlock* WeakableKindStructs::getControlBlock() {
  return kindStructs.getControlBlock();
}
LLVMTypeRef WeakableKindStructs::getInnerStruct(StructKind* structKind) {
  return kindStructs.getInnerStruct(structKind);
}
LLVMTypeRef WeakableKindStructs::getWrapperStruct(StructKind* structKind) {
  return kindStructs.getWrapperStruct(structKind);
}
LLVMTypeRef WeakableKindStructs::getStaticSizedArrayWrapperStruct(StaticSizedArrayT* ssaMT) {
  return kindStructs.getStaticSizedArrayWrapperStruct(ssaMT);
}
LLVMTypeRef WeakableKindStructs::getRuntimeSizedArrayWrapperStruct(RuntimeSizedArrayT* rsaMT) {
  return kindStructs.getRuntimeSizedArrayWrapperStruct(rsaMT);
}
LLVMTypeRef WeakableKindStructs::getInterfaceRefStruct(InterfaceKind* interfaceKind) {
  return kindStructs.getInterfaceRefStruct(interfaceKind);
}
LLVMTypeRef WeakableKindStructs::getInterfaceTableStruct(InterfaceKind* interfaceKind) {
  return kindStructs.getInterfaceRefStruct(interfaceKind);
}
LLVMTypeRef WeakableKindStructs::getStructWeakRefStruct(StructKind* structKind) {
  auto structIter = structWeakRefStructs.find(structKind->fullName->name);
  assert(structIter != structWeakRefStructs.end());
  return structIter->second;
}
LLVMTypeRef WeakableKindStructs::getStaticSizedArrayWeakRefStruct(StaticSizedArrayT* ssaMT) {
  auto structIter = staticSizedArrayWeakRefStructs.find(ssaMT->name->name);
  assert(structIter != staticSizedArrayWeakRefStructs.end());
  return structIter->second;
}
LLVMTypeRef WeakableKindStructs::getRuntimeSizedArrayWeakRefStruct(RuntimeSizedArrayT* rsaMT) {
  auto structIter = runtimeSizedArrayWeakRefStructs.find(rsaMT->name->name);
  assert(structIter != runtimeSizedArrayWeakRefStructs.end());
  return structIter->second;
}
LLVMTypeRef WeakableKindStructs::getInterfaceWeakRefStruct(InterfaceKind* interfaceKind) {
  auto interfaceIter = interfaceWeakRefStructs.find(interfaceKind->fullName->name);
  assert(interfaceIter != interfaceWeakRefStructs.end());
  return interfaceIter->second;
}








void KindStructs::defineStruct(
    StructKind* structKind,
    std::vector<LLVMTypeRef> membersLT) {
  LLVMTypeRef valStructL = getInnerStruct(structKind);
  LLVMStructSetBody(
      valStructL, membersLT.data(), membersLT.size(), false);

  LLVMTypeRef wrapperStructL = getWrapperStruct(structKind);
  std::vector<LLVMTypeRef> wrapperStructMemberTypesL;

  // First member is a ref counts struct. We don't include the int directly
  // because we want fat pointers to point to this struct, so they can reach
  // into it and increment without doing any casting.
  wrapperStructMemberTypesL.push_back(controlBlock.getStruct());

  wrapperStructMemberTypesL.push_back(valStructL);

  LLVMStructSetBody(
      wrapperStructL, wrapperStructMemberTypesL.data(), wrapperStructMemberTypesL.size(), false);
}

void KindStructs::declareStruct(StructKind* structM) {

  auto innerStructL =
      LLVMStructCreateNamed(
          globalState->context, structM->fullName->name.c_str());
  assert(innerStructs.count(structM->fullName->name) == 0);
  innerStructs.emplace(structM->fullName->name, innerStructL);

  auto wrapperStructL =
      LLVMStructCreateNamed(
          globalState->context, (structM->fullName->name + "rc").c_str());
  assert(wrapperStructs.count(structM->fullName->name) == 0);
  wrapperStructs.emplace(structM->fullName->name, wrapperStructL);
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

  globalState->interfaceTablePtrs.emplace(edge, itablePtr);
}

void KindStructs::defineEdge(
    Edge* edge,
    std::vector<LLVMTypeRef> interfaceFunctionsLT,
    std::vector<LLVMValueRef> functions) {
  auto interfaceTableStructL =
      getInterfaceTableStruct(edge->interfaceName);
  auto builder = LLVMCreateBuilderInContext(globalState->context);
  auto itableLE = LLVMGetUndef(interfaceTableStructL);
  for (int i = 0; i < functions.size(); i++) {
    auto entryLE = LLVMConstBitCast(functions[i], interfaceFunctionsLT[i]);
    itableLE = LLVMBuildInsertValue(builder, itableLE, entryLE, i, std::to_string(i).c_str());
  }
  LLVMDisposeBuilder(builder);

  auto itablePtr = globalState->getInterfaceTablePtr(edge);
  LLVMSetInitializer(itablePtr,  itableLE);
}

void KindStructs::declareInterface(InterfaceDefinition* interface) {
  assert(interfaceTableStructs.count(interface->name->name) == 0);
  auto interfaceTableStructL =
      LLVMStructCreateNamed(
          globalState->context, (interface->name->name + "itable").c_str());
  interfaceTableStructs.emplace(interface->name->name, interfaceTableStructL);


  assert(interfaceRefStructs.count(interface->name->name) == 0);

  auto interfaceRefStructL =
      LLVMStructCreateNamed(
          globalState->context, interface->name->name.c_str());

  std::vector<LLVMTypeRef> refStructMemberTypesL;

  // this points to the control block.
  // It makes it easier to increment and decrement ref counts.
  refStructMemberTypesL.push_back(LLVMPointerType(controlBlock.getStruct(), 0));

  refStructMemberTypesL.push_back(LLVMPointerType(interfaceTableStructL, 0));
  LLVMStructSetBody(
      interfaceRefStructL,
      refStructMemberTypesL.data(),
      refStructMemberTypesL.size(),
      false);

  interfaceRefStructs.emplace(interface->name->name, interfaceRefStructL);
}

void KindStructs::defineInterface(
    InterfaceDefinition* interface,
    std::vector<LLVMTypeRef> interfaceMethodTypesL) {
  LLVMTypeRef itableStruct =
      getInterfaceTableStruct(interface->kind);

  LLVMStructSetBody(
      itableStruct, interfaceMethodTypesL.data(), interfaceMethodTypesL.size(), false);
}


void KindStructs::declareStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT) {

  auto countedStruct = LLVMStructCreateNamed(globalState->context, staticSizedArrayMT->name->name.c_str());
  staticSizedArrayWrapperStructs.emplace(staticSizedArrayMT->name->name, countedStruct);
}

void KindStructs::declareRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) {
  auto countedStruct = LLVMStructCreateNamed(globalState->context, (runtimeSizedArrayMT->name->name + "rc").c_str());
  runtimeSizedArrayWrapperStructs.emplace(runtimeSizedArrayMT->name->name, countedStruct);
}

void KindStructs::defineRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT,
    LLVMTypeRef elementLT) {

  auto runtimeSizedArrayWrapperStruct = getRuntimeSizedArrayWrapperStruct(runtimeSizedArrayMT->kind);
  auto innerArrayLT = LLVMArrayType(elementLT, 0);

  std::vector<LLVMTypeRef> elementsL;

  elementsL.push_back(controlBlock.getStruct());

  elementsL.push_back(LLVMInt32TypeInContext(globalState->context));

  elementsL.push_back(innerArrayLT);

  LLVMStructSetBody(runtimeSizedArrayWrapperStruct, elementsL.data(), elementsL.size(), false);
}

void KindStructs::defineStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT,
    LLVMTypeRef elementLT) {
  auto staticSizedArrayWrapperStruct = getStaticSizedArrayWrapperStruct(staticSizedArrayMT->kind);

  auto innerArrayLT = LLVMArrayType(elementLT, staticSizedArrayMT->size);

  std::vector<LLVMTypeRef> elementsL;

  elementsL.push_back(controlBlock.getStruct());

  elementsL.push_back(innerArrayLT);

  LLVMStructSetBody(staticSizedArrayWrapperStruct, elementsL.data(), elementsL.size(), false);
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
    wrapperStructLT = getWrapperStruct(structKind);
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

  WrapperPtrLE wrapperPtrLE(referenceM, ptrLE);

  return wrapperPtrLE;
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
  auto actualTypeOfControlBlockPtrLE = LLVMTypeOf(controlBlockPtrLE);
  auto expectedControlBlockStructL = getControlBlock(kindM)->getStruct();
  auto expectedControlBlockStructPtrL = LLVMPointerType(expectedControlBlockStructL, 0);
  assert(actualTypeOfControlBlockPtrLE == expectedControlBlockStructPtrL);

  return ControlBlockPtrLE(kindM, controlBlockPtrLE);
}


LLVMValueRef KindStructs::getStringBytesPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE ptrLE) {
  return getCharsPtrFromWrapperPtr(globalState, builder, ptrLE);
}

LLVMValueRef KindStructs::getStringLen(FunctionState* functionState, LLVMBuilderRef builder, WrapperPtrLE ptrLE) {
  return getLenFromStrWrapperPtr(builder, ptrLE);
}

ControlBlockPtrLE KindStructs::getConcreteControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* reference,
    WrapperPtrLE wrapperPtrLE) {
  // Control block is always the 0th element of every concrete struct.
  return makeControlBlockPtr(
      from, functionState, builder,
      wrapperPtrLE.refM->kind,
      LLVMBuildStructGEP(builder, wrapperPtrLE.refLE, 0, "controlPtr"));
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
      LLVMBuildStructGEP(builder, wrapperPtrLE.refLE, 0, "controlPtr"));
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
            globalState->getRegion(referenceM)->checkValidReference(from, functionState, builder, referenceM, ref));
    return getControlBlockPtr(from, functionState, builder, kindM, referenceLE);
  } else if (dynamic_cast<StructKind*>(kindM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->getRegion(referenceM)->checkValidReference(from, functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<StaticSizedArrayT*>(kindM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->getRegion(referenceM)->checkValidReference(from, functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<RuntimeSizedArrayT*>(kindM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->getRegion(referenceM)->checkValidReference(from, functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<Str*>(kindM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->getRegion(referenceM)->checkValidReference(from, functionState, builder, referenceM, ref));
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
  return LLVMBuildStructGEP(
      builder,
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
  assert(globalState->opt->census);
  return LLVMBuildLoad(
      builder,
      LLVMBuildStructGEP(
          builder,
          controlBlockPtr.refLE,
          getControlBlock(kindM)->getMemberIndex(ControlBlockMember::CENSUS_OBJ_ID),
          "objIdPtr"),
      "objId");
}

LLVMValueRef KindStructs::getStrongRcFromControlBlockPtr(
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE structExpr) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V3: case RegionOverride::RESILIENT_V4:
      assert(refM->ownership == Ownership::SHARE);
      break;
    default:
      assert(false);
  }

  auto rcPtrLE = getStrongRcPtrFromControlBlockPtr(builder, refM, structExpr);
  return LLVMBuildLoad(builder, rcPtrLE, "rc");
}

// See CRCISFAORC for why we don't take in a mutability.
LLVMValueRef KindStructs::getStrongRcPtrFromControlBlockPtr(
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V3: case RegionOverride::RESILIENT_V4:
      assert(refM->ownership == Ownership::SHARE);
      break;
    default:
      assert(false);
  }

  return LLVMBuildStructGEP(
      builder,
      controlBlockPtr.refLE,
      getControlBlock(refM->kind)->getMemberIndex(ControlBlockMember::STRONG_RC_32B),
      "rcPtr");
}

LLVMValueRef KindStructs::downcastPtr(
    LLVMBuilderRef builder,
    Reference* resultStructRefMT,
    LLVMValueRef unknownPossibilityPtrLE) {
  auto resultStructRefLT = globalState->getRegion(resultStructRefMT)->translateType(resultStructRefMT);
  auto resultStructRefLE =
      LLVMBuildPointerCast(builder, unknownPossibilityPtrLE, resultStructRefLT, "subtypePtr");
  return resultStructRefLE;
}




void WeakableKindStructs::defineStruct(
    StructKind* struuct,
    std::vector<LLVMTypeRef> membersLT) {
  assert(weakRefHeaderStructL);

  kindStructs.defineStruct(struuct, membersLT);

  LLVMTypeRef wrapperStructL = getWrapperStruct(struuct);

  auto structWeakRefStructL = getStructWeakRefStruct(struuct);
  std::vector<LLVMTypeRef> structWeakRefStructMemberTypesL;
  structWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
  structWeakRefStructMemberTypesL.push_back(LLVMPointerType(wrapperStructL, 0));
  LLVMStructSetBody(structWeakRefStructL, structWeakRefStructMemberTypesL.data(), structWeakRefStructMemberTypesL.size(), false);
}

void WeakableKindStructs::declareStruct(StructKind* structM) {
  kindStructs.declareStruct(structM);

  auto structWeakRefStructL =
      LLVMStructCreateNamed(
          globalState->context, (structM->fullName->name + "w").c_str());
  assert(structWeakRefStructs.count(structM->fullName->name) == 0);
  structWeakRefStructs.emplace(structM->fullName->name, structWeakRefStructL);
}


void WeakableKindStructs::declareEdge(
    Edge* edge) {
  kindStructs.declareEdge(edge);
}

void WeakableKindStructs::defineEdge(
    Edge* edge,
    std::vector<LLVMTypeRef> interfaceFunctionsLT,
    std::vector<LLVMValueRef> functions) {
  kindStructs.defineEdge(edge, interfaceFunctionsLT, functions);
}

void WeakableKindStructs::declareInterface(InterfaceDefinition* interface) {
  kindStructs.declareInterface(interface);

  auto interfaceWeakRefStructL =
      LLVMStructCreateNamed(
          globalState->context, (interface->name->name + "w").c_str());
  assert(interfaceWeakRefStructs.count(interface->name->name) == 0);

  interfaceWeakRefStructs.emplace(interface->name->name, interfaceWeakRefStructL);


  LLVMTypeRef refStructL = getInterfaceRefStruct(interface->kind);

  std::vector<LLVMTypeRef> interfaceWeakRefStructMemberTypesL;
  interfaceWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
  interfaceWeakRefStructMemberTypesL.push_back(refStructL);
  LLVMStructSetBody(interfaceWeakRefStructL, interfaceWeakRefStructMemberTypesL.data(), interfaceWeakRefStructMemberTypesL.size(), false);
}

void WeakableKindStructs::defineInterface(
    InterfaceDefinition* interface,
    std::vector<LLVMTypeRef> interfaceMethodTypesL) {
  assert(weakRefHeaderStructL);

  kindStructs.defineInterface(interface, interfaceMethodTypesL);
}


void WeakableKindStructs::declareStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT) {
  kindStructs.declareStaticSizedArray(staticSizedArrayMT);

  auto weakRefStructL =
      LLVMStructCreateNamed(
          globalState->context, (staticSizedArrayMT->name->name + "w").c_str());
  assert(staticSizedArrayWeakRefStructs.count(staticSizedArrayMT->name->name) == 0);
  staticSizedArrayWeakRefStructs.emplace(staticSizedArrayMT->name->name, weakRefStructL);
}

void WeakableKindStructs::declareRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) {
  kindStructs.declareRuntimeSizedArray(runtimeSizedArrayMT);

  auto weakRefStructL =
      LLVMStructCreateNamed(
          globalState->context, (runtimeSizedArrayMT->name->name + "w").c_str());
  assert(runtimeSizedArrayWeakRefStructs.count(runtimeSizedArrayMT->name->name) == 0);
  runtimeSizedArrayWeakRefStructs.emplace(runtimeSizedArrayMT->name->name, weakRefStructL);
}

void WeakableKindStructs::defineRuntimeSizedArray(
    RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT,
    LLVMTypeRef elementLT) {
  assert(weakRefHeaderStructL);

  kindStructs.defineRuntimeSizedArray(runtimeSizedArrayMT, elementLT);

  auto runtimeSizedArrayWrapperStruct = getRuntimeSizedArrayWrapperStruct(runtimeSizedArrayMT->kind);

  auto arrayWeakRefStructL = getRuntimeSizedArrayWeakRefStruct(runtimeSizedArrayMT->kind);
  std::vector<LLVMTypeRef> arrayWeakRefStructMemberTypesL;
  arrayWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
  arrayWeakRefStructMemberTypesL.push_back(LLVMPointerType(runtimeSizedArrayWrapperStruct, 0));
  LLVMStructSetBody(arrayWeakRefStructL, arrayWeakRefStructMemberTypesL.data(), arrayWeakRefStructMemberTypesL.size(), false);
}

void WeakableKindStructs::defineStaticSizedArray(
    StaticSizedArrayDefinitionT* staticSizedArrayMT,
    LLVMTypeRef elementLT) {
  assert(weakRefHeaderStructL);

  kindStructs.defineStaticSizedArray(staticSizedArrayMT, elementLT);

  auto staticSizedArrayWrapperStruct = getStaticSizedArrayWrapperStruct(staticSizedArrayMT->kind);

  auto arrayWeakRefStructL = getStaticSizedArrayWeakRefStruct(staticSizedArrayMT->kind);
  std::vector<LLVMTypeRef> arrayWeakRefStructMemberTypesL;
  arrayWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
  arrayWeakRefStructMemberTypesL.push_back(LLVMPointerType(staticSizedArrayWrapperStruct, 0));
  LLVMStructSetBody(arrayWeakRefStructL, arrayWeakRefStructMemberTypesL.data(), arrayWeakRefStructMemberTypesL.size(), false);
}

WeakFatPtrLE WeakableKindStructs::makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) {
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

WeakFatPtrLE WeakableKindStructs::downcastWeakFatPtr(
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
      LLVMPointerType(kindStructs.getWrapperStruct(targetStructKind), 0);
  auto underlyingStructPtrLE =
      LLVMBuildBitCast(builder, objVoidPtrLE, underlyingStructPtrLT, "subtypePtr");

  auto resultStructRefLT = getStructWeakRefStruct(targetStructKind);
  auto resultStructRefLE = LLVMGetUndef(resultStructRefLT);
  resultStructRefLE = LLVMBuildInsertValue(builder, resultStructRefLE, weakRefHeaderStruct, 0, "withHeader");
  resultStructRefLE = LLVMBuildInsertValue(builder, resultStructRefLE, underlyingStructPtrLE, 1, "withBoth");

  auto targetWeakRef = makeWeakFatPtr(targetRefMT, resultStructRefLE);
  return targetWeakRef;
}

ControlBlockPtrLE WeakableKindStructs::getConcreteControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* reference,
    WrapperPtrLE wrapperPtrLE) {
  return kindStructs.getConcreteControlBlockPtr(from, functionState, builder, reference, wrapperPtrLE);
}

LLVMTypeRef WeakableKindStructs::getStringWrapperStruct() {
  return kindStructs.getStringWrapperStruct();
}

WrapperPtrLE WeakableKindStructs::makeWrapperPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    LLVMValueRef ptrLE) {
  return kindStructs.makeWrapperPtr(checkerAFL, functionState, builder, referenceM, ptrLE);
}

InterfaceFatPtrLE WeakableKindStructs::makeInterfaceFatPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM_,
    LLVMValueRef ptrLE) {
  return kindStructs.makeInterfaceFatPtr(checkerAFL, functionState, builder, referenceM_, ptrLE);
}

InterfaceFatPtrLE WeakableKindStructs::makeInterfaceFatPtrWithoutChecking(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM_,
    LLVMValueRef ptrLE) {
  return kindStructs.makeInterfaceFatPtrWithoutChecking(checkerAFL, functionState, builder, referenceM_, ptrLE);
}

//ControlBlockPtrLE WeakableKindStructs::makeControlBlockPtr(
//    AreaAndFileAndLine checkerAFL,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Kind* kindM,
//    LLVMValueRef controlBlockPtrLE) {
//  return kindStructs.makeControlBlockPtr(checkerAFL, functionState, builder, kindM, controlBlockPtrLE);
//}

LLVMValueRef WeakableKindStructs::getStringBytesPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE ptrLE) {
  return kindStructs.getStringBytesPtr(functionState, builder, ptrLE);
}

LLVMValueRef WeakableKindStructs::getStringLen(
    FunctionState* functionState, LLVMBuilderRef builder, WrapperPtrLE ptrLE) {
  return kindStructs.getStringLen(functionState, builder, ptrLE);
}


ControlBlockPtrLE WeakableKindStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Kind* kindM,
    InterfaceFatPtrLE interfaceFatPtrLE) {
  return kindStructs.getControlBlockPtr(from, functionState, builder, kindM, interfaceFatPtrLE);
}

ControlBlockPtrLE WeakableKindStructs::getControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Kind* kindM,
    InterfaceFatPtrLE interfaceFatPtrLE) {
  return kindStructs.getControlBlockPtrWithoutChecking(from, functionState, builder, kindM, interfaceFatPtrLE);
}

ControlBlockPtrLE WeakableKindStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    Ref ref,
    Reference* referenceM) {
  return kindStructs.getControlBlockPtr(from, functionState, builder, ref, referenceM);
}

ControlBlockPtrLE WeakableKindStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  return kindStructs.getControlBlockPtr(from, functionState, builder, ref, referenceM);
}

ControlBlockPtrLE WeakableKindStructs::getControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  return kindStructs.getControlBlockPtrWithoutChecking(from, functionState, builder, ref, referenceM);
}

LLVMValueRef WeakableKindStructs::getStructContentsPtr(
    LLVMBuilderRef builder,
    Kind* kind,
    WrapperPtrLE wrapperPtrLE) {
  return kindStructs.getStructContentsPtr(builder, kind, wrapperPtrLE);
}

LLVMValueRef WeakableKindStructs::getVoidPtrFromInterfacePtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE) {
  return kindStructs.getVoidPtrFromInterfacePtr(
      functionState, builder, virtualParamMT, virtualArgLE);
}

LLVMValueRef WeakableKindStructs::getObjIdFromControlBlockPtr(
    LLVMBuilderRef builder,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtr) {
  return kindStructs.getObjIdFromControlBlockPtr(builder, kindM, controlBlockPtr);
}

// See CRCISFAORC for why we don't take in a mutability.
// Strong means owning or borrow or shared; things that control the lifetime.
LLVMValueRef WeakableKindStructs::getStrongRcPtrFromControlBlockPtr(
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
  return kindStructs.getStrongRcPtrFromControlBlockPtr(builder, refM, controlBlockPtr);
}

// See CRCISFAORC for why we don't take in a mutability.
// Strong means owning or borrow or shared; things that control the lifetime.
LLVMValueRef WeakableKindStructs::getStrongRcFromControlBlockPtr(
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
  return kindStructs.getStrongRcFromControlBlockPtr(builder, refM, controlBlockPtr);
}

LLVMValueRef WeakableKindStructs::downcastPtr(LLVMBuilderRef builder, Reference* resultStructRefMT, LLVMValueRef unknownPossibilityPtrLE) {
  return kindStructs.downcastPtr(builder, resultStructRefMT, unknownPossibilityPtrLE);
}
