
#include <region/common/common.h>
#include <function/expressions/shared/shared.h>
#include <function/expressions/shared/string.h>
#include "structs.h"


constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_WRCI = 0;

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;
constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_LGTI = 1;


ReferendStructs::ReferendStructs(GlobalState* globalState_, ControlBlock controlBlock_)
  : globalState(globalState_),
    controlBlock(controlBlock_) {

  auto voidLT = LLVMVoidType();
  auto int8LT = LLVMInt8Type();
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  {
    stringInnerStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), "__Str");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(LLVMInt64Type());
    memberTypesL.push_back(LLVMArrayType(int8LT, 0));
    LLVMStructSetBody(
        stringInnerStructL, memberTypesL.data(), memberTypesL.size(), false);
  }

  {
    stringWrapperStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), "__Str_rc");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(controlBlock.getStruct());
    memberTypesL.push_back(stringInnerStructL);
    LLVMStructSetBody(
        stringWrapperStructL, memberTypesL.data(), memberTypesL.size(), false);
  }

  stringInnerStructPtrLT = LLVMPointerType(stringInnerStructL, 0);
}

ControlBlock* ReferendStructs::getControlBlock(Referend* referend) {
  return &controlBlock;
}
LLVMTypeRef ReferendStructs::getInnerStruct(StructReferend* structReferend) {
  auto structIter = innerStructs.find(structReferend->fullName->name);
  assert(structIter != innerStructs.end());
  return structIter->second;
}
LLVMTypeRef ReferendStructs::getWrapperStruct(StructReferend* structReferend) {
  auto structIter = wrapperStructs.find(structReferend->fullName->name);
  assert(structIter != wrapperStructs.end());
  return structIter->second;
}
LLVMTypeRef ReferendStructs::getKnownSizeArrayWrapperStruct(KnownSizeArrayT* ksaMT) {
  auto structIter = knownSizeArrayWrapperStructs.find(ksaMT->name->name);
  assert(structIter != knownSizeArrayWrapperStructs.end());
  return structIter->second;
}
LLVMTypeRef ReferendStructs::getUnknownSizeArrayWrapperStruct(UnknownSizeArrayT* usaMT) {
  auto structIter = unknownSizeArrayWrapperStructs.find(usaMT->name->name);
  assert(structIter != unknownSizeArrayWrapperStructs.end());
  return structIter->second;
}
LLVMTypeRef ReferendStructs::getInterfaceRefStruct(InterfaceReferend* interfaceReferend) {
  auto structIter = interfaceRefStructs.find(interfaceReferend->fullName->name);
  assert(structIter != interfaceRefStructs.end());
  return structIter->second;
}
LLVMTypeRef ReferendStructs::getInterfaceTableStruct(InterfaceReferend* interfaceReferend) {
  auto structIter = interfaceTableStructs.find(interfaceReferend->fullName->name);
  assert(structIter != interfaceTableStructs.end());
  return structIter->second;
}
LLVMTypeRef ReferendStructs::getStringWrapperStruct() {
  return stringWrapperStructL;
}


WeakableReferendStructs::WeakableReferendStructs(
  GlobalState* globalState_,
  ControlBlock controlBlock,
  LLVMTypeRef weakRefHeaderStructL_)
: globalState(globalState_),
  referendStructs(globalState_, std::move(controlBlock)),
  weakRefHeaderStructL(weakRefHeaderStructL_) {

  assert(weakRefHeaderStructL);

  // This is a weak ref to a void*. When we're calling an interface method on a weak,
  // we have no idea who the receiver is. They'll receive this struct as the correctly
  // typed flavor of it (from structWeakRefStructs).
  weakVoidRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), "__Weak_VoidP");
  std::vector<LLVMTypeRef> structWeakRefStructMemberTypesL;
  structWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
  structWeakRefStructMemberTypesL.push_back(LLVMPointerType(LLVMVoidType(), 0));
  LLVMStructSetBody(weakVoidRefStructL, structWeakRefStructMemberTypesL.data(), structWeakRefStructMemberTypesL.size(), false);
}

ControlBlock* WeakableReferendStructs::getControlBlock(Referend* referend) {
  return referendStructs.getControlBlock(referend);
}
LLVMTypeRef WeakableReferendStructs::getInnerStruct(StructReferend* structReferend) {
  return referendStructs.getInnerStruct(structReferend);
}
LLVMTypeRef WeakableReferendStructs::getWrapperStruct(StructReferend* structReferend) {
  return referendStructs.getWrapperStruct(structReferend);
}
LLVMTypeRef WeakableReferendStructs::getKnownSizeArrayWrapperStruct(KnownSizeArrayT* ksaMT) {
  return referendStructs.getKnownSizeArrayWrapperStruct(ksaMT);
}
LLVMTypeRef WeakableReferendStructs::getUnknownSizeArrayWrapperStruct(UnknownSizeArrayT* usaMT) {
  return referendStructs.getUnknownSizeArrayWrapperStruct(usaMT);
}
LLVMTypeRef WeakableReferendStructs::getInterfaceRefStruct(InterfaceReferend* interfaceReferend) {
  return referendStructs.getInterfaceRefStruct(interfaceReferend);
}
LLVMTypeRef WeakableReferendStructs::getInterfaceTableStruct(InterfaceReferend* interfaceReferend) {
  return referendStructs.getInterfaceRefStruct(interfaceReferend);
}
LLVMTypeRef WeakableReferendStructs::getStructWeakRefStruct(StructReferend* structReferend) {
  auto structIter = structWeakRefStructs.find(structReferend->fullName->name);
  assert(structIter != structWeakRefStructs.end());
  return structIter->second;
}
LLVMTypeRef WeakableReferendStructs::getKnownSizeArrayWeakRefStruct(KnownSizeArrayT* ksaMT) {
  auto structIter = knownSizeArrayWeakRefStructs.find(ksaMT->name->name);
  assert(structIter != knownSizeArrayWeakRefStructs.end());
  return structIter->second;
}
LLVMTypeRef WeakableReferendStructs::getUnknownSizeArrayWeakRefStruct(UnknownSizeArrayT* usaMT) {
  auto structIter = unknownSizeArrayWeakRefStructs.find(usaMT->name->name);
  assert(structIter != unknownSizeArrayWeakRefStructs.end());
  return structIter->second;
}
LLVMTypeRef WeakableReferendStructs::getInterfaceWeakRefStruct(InterfaceReferend* interfaceReferend) {
  auto interfaceIter = interfaceWeakRefStructs.find(interfaceReferend->fullName->name);
  assert(interfaceIter != interfaceWeakRefStructs.end());
  return interfaceIter->second;
}








void ReferendStructs::translateStruct(
    StructDefinition* struuct,
    std::vector<LLVMTypeRef> membersLT) {
  LLVMTypeRef valStructL = getInnerStruct(struuct->referend);
  LLVMStructSetBody(
      valStructL, membersLT.data(), membersLT.size(), false);

  LLVMTypeRef wrapperStructL = getWrapperStruct(struuct->referend);
  std::vector<LLVMTypeRef> wrapperStructMemberTypesL;

  // First member is a ref counts struct. We don't include the int directly
  // because we want fat pointers to point to this struct, so they can reach
  // into it and increment without doing any casting.
  wrapperStructMemberTypesL.push_back(controlBlock.getStruct());

  wrapperStructMemberTypesL.push_back(valStructL);

  LLVMStructSetBody(
      wrapperStructL, wrapperStructMemberTypesL.data(), wrapperStructMemberTypesL.size(), false);
}

void ReferendStructs::declareStruct(StructDefinition* structM) {

  auto innerStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), structM->name->name.c_str());
  assert(innerStructs.count(structM->name->name) == 0);
  innerStructs.emplace(structM->name->name, innerStructL);

  auto wrapperStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (structM->name->name + "rc").c_str());
  assert(wrapperStructs.count(structM->name->name) == 0);
  wrapperStructs.emplace(structM->name->name, wrapperStructL);
}


void ReferendStructs::declareEdge(
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

void ReferendStructs::translateEdge(
    Edge* edge,
    std::vector<LLVMValueRef> functions) {

  auto interfaceTableStructL =
      getInterfaceTableStruct(edge->interfaceName);
  auto builder = LLVMCreateBuilder();
  auto itableLE = LLVMGetUndef(interfaceTableStructL);
  for (int i = 0; i < functions.size(); i++) {
    itableLE = LLVMBuildInsertValue(
        builder,
        itableLE,
        functions[i],
        i,
        std::to_string(i).c_str());
  }
  LLVMDisposeBuilder(builder);

  auto itablePtr = globalState->getInterfaceTablePtr(edge);
  LLVMSetInitializer(itablePtr,  itableLE);
}

void ReferendStructs::declareInterface(InterfaceDefinition* interface) {

  auto interfaceRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), interface->name->name.c_str());
  assert(interfaceRefStructs.count(interface->name->name) == 0);
  interfaceRefStructs.emplace(interface->name->name, interfaceRefStructL);

  auto interfaceTableStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (interface->name->name + "itable").c_str());
  assert(interfaceTableStructs.count(interface->name->name) == 0);
  interfaceTableStructs.emplace(interface->name->name, interfaceTableStructL);
}

void ReferendStructs::translateInterface(
    InterfaceDefinition* interface,
    std::vector<LLVMTypeRef> interfaceMethodTypesL) {
  LLVMTypeRef itableStruct =
      getInterfaceTableStruct(interface->referend);

  LLVMStructSetBody(
      itableStruct, interfaceMethodTypesL.data(), interfaceMethodTypesL.size(), false);

  LLVMTypeRef refStructL = getInterfaceRefStruct(interface->referend);
  std::vector<LLVMTypeRef> refStructMemberTypesL;

  // this points to the control block.
  // It makes it easier to increment and decrement ref counts.
  refStructMemberTypesL.push_back(LLVMPointerType(controlBlock.getStruct(), 0));


  refStructMemberTypesL.push_back(LLVMPointerType(itableStruct, 0));
  LLVMStructSetBody(
      refStructL,
      refStructMemberTypesL.data(),
      refStructMemberTypesL.size(),
      false);
}


void ReferendStructs::declareKnownSizeArray(
    KnownSizeArrayT* knownSizeArrayMT) {

  auto countedStruct = LLVMStructCreateNamed(LLVMGetGlobalContext(), knownSizeArrayMT->name->name.c_str());
  knownSizeArrayWrapperStructs.emplace(knownSizeArrayMT->name->name, countedStruct).first;
}

void ReferendStructs::declareUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT) {
  auto countedStruct = LLVMStructCreateNamed(LLVMGetGlobalContext(), (unknownSizeArrayMT->name->name + "rc").c_str());
  unknownSizeArrayWrapperStructs.emplace(unknownSizeArrayMT->name->name, countedStruct).first;
}

void ReferendStructs::translateUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT,
    LLVMTypeRef elementLT) {

  auto unknownSizeArrayWrapperStruct = getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT);
  auto innerArrayLT = LLVMArrayType(elementLT, 0);

  std::vector<LLVMTypeRef> elementsL;

  elementsL.push_back(controlBlock.getStruct());

  elementsL.push_back(LLVMInt64Type());

  elementsL.push_back(innerArrayLT);

  LLVMStructSetBody(unknownSizeArrayWrapperStruct, elementsL.data(), elementsL.size(), false);
}

void ReferendStructs::translateKnownSizeArray(
    KnownSizeArrayT* knownSizeArrayMT,
    LLVMTypeRef elementLT) {
  auto knownSizeArrayWrapperStruct = getKnownSizeArrayWrapperStruct(knownSizeArrayMT);

  auto innerArrayLT = LLVMArrayType(elementLT, knownSizeArrayMT->size);

  std::vector<LLVMTypeRef> elementsL;

  elementsL.push_back(controlBlock.getStruct());

  elementsL.push_back(innerArrayLT);

  LLVMStructSetBody(knownSizeArrayWrapperStruct, elementsL.data(), elementsL.size(), false);
}


WrapperPtrLE ReferendStructs::makeWrapperPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    LLVMValueRef ptrLE) {
  assert(ptrLE != nullptr);
  Referend* referend = referenceM->referend;

  WrapperPtrLE wrapperPtrLE = makeWrapperPtrWithoutChecking(checkerAFL, functionState, builder, referenceM, ptrLE);

  if (dynamic_cast<StructReferend*>(referend)) {
    auto controlBlockPtrLE = getConcreteControlBlockPtr(checkerAFL, functionState, builder, referenceM, wrapperPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE.refLE);
  } else if (dynamic_cast<InterfaceReferend*>(referend)) {
    // can we even get a wrapper struct for an interface?
    assert(false);
  } else if (dynamic_cast<KnownSizeArrayT*>(referend)) {
    auto controlBlockPtrLE = getConcreteControlBlockPtr(checkerAFL, functionState, builder, referenceM, wrapperPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE.refLE);
  } else if (dynamic_cast<UnknownSizeArrayT*>(referend)) {
    auto controlBlockPtrLE = getConcreteControlBlockPtr(checkerAFL, functionState, builder, referenceM, wrapperPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE.refLE);
  } else if (dynamic_cast<Str*>(referend)) {
    auto controlBlockPtrLE = getConcreteControlBlockPtr(checkerAFL, functionState, builder, referenceM, wrapperPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE.refLE);
  } else assert(false);

  return wrapperPtrLE;
}


WrapperPtrLE ReferendStructs::makeWrapperPtrWithoutChecking(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    LLVMValueRef ptrLE) {
  assert(ptrLE != nullptr);

  Referend* referend = referenceM->referend;
  LLVMTypeRef wrapperStructLT = nullptr;
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    wrapperStructLT = getWrapperStruct(structReferend);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
    assert(false); // can we even get a wrapper struct for an interface?
  } else if (auto ksaMT = dynamic_cast<KnownSizeArrayT*>(referend)) {
    wrapperStructLT = getKnownSizeArrayWrapperStruct(ksaMT);
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT*>(referend)) {
    wrapperStructLT = getUnknownSizeArrayWrapperStruct(usaMT);
  } else if (auto strMT = dynamic_cast<Str*>(referend)) {
    wrapperStructLT = stringWrapperStructL;
  } else assert(false);
  assert(LLVMTypeOf(ptrLE) == LLVMPointerType(wrapperStructLT, 0));

  WrapperPtrLE wrapperPtrLE(referenceM, ptrLE);

  return wrapperPtrLE;
}

InterfaceFatPtrLE ReferendStructs::makeInterfaceFatPtrWithoutChecking(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM_,
    LLVMValueRef ptrLE) {
  auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(referenceM_->referend);
  assert(interfaceReferendM);
  assert(LLVMTypeOf(ptrLE) == getInterfaceRefStruct(interfaceReferendM));

  auto interfaceFatPtrLE = InterfaceFatPtrLE(referenceM_, ptrLE);

  auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
  buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);

  return interfaceFatPtrLE;
}

InterfaceFatPtrLE ReferendStructs::makeInterfaceFatPtr(
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

ControlBlockPtrLE ReferendStructs::makeControlBlockPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef controlBlockPtrLE) {
  auto result = makeControlBlockPtrWithoutChecking(checkerAFL, functionState, builder, referendM, controlBlockPtrLE);
  buildAssertCensusContains(checkerAFL, globalState, functionState, builder, controlBlockPtrLE);
  return result;
}

ControlBlockPtrLE ReferendStructs::makeControlBlockPtrWithoutChecking(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef controlBlockPtrLE) {
  auto actualTypeOfControlBlockPtrLE = LLVMTypeOf(controlBlockPtrLE);
  auto expectedControlBlockStructL = getControlBlock(referendM)->getStruct();
  auto expectedControlBlockStructPtrL = LLVMPointerType(expectedControlBlockStructL, 0);
  assert(actualTypeOfControlBlockPtrLE == expectedControlBlockStructPtrL);

  return ControlBlockPtrLE(referendM, controlBlockPtrLE);
}


LLVMValueRef ReferendStructs::getStringBytesPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref ref) {
  auto strWrapperPtrLE =
      makeWrapperPtr(
          FL(), functionState, builder,
          globalState->metalCache.strRef,
          globalState->region->checkValidReference(
              FL(), functionState, builder,
              globalState->metalCache.strRef, ref));
  return getCharsPtrFromWrapperPtr(builder, strWrapperPtrLE);
}

LLVMValueRef ReferendStructs::getStringLen(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) {
  auto strWrapperPtrLE =
      makeWrapperPtr(
          FL(), functionState, builder,
          globalState->metalCache.strRef,
          globalState->region->checkValidReference(
              FL(), functionState, builder,
              globalState->metalCache.strRef, ref));
  return getLenFromStrWrapperPtr(builder, strWrapperPtrLE);
}

ControlBlockPtrLE ReferendStructs::getConcreteControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* reference,
    WrapperPtrLE wrapperPtrLE) {
  // Control block is always the 0th element of every concrete struct.
  return makeControlBlockPtr(
      from, functionState, builder,
      wrapperPtrLE.refM->referend,
      LLVMBuildStructGEP(builder, wrapperPtrLE.refLE, 0, "controlPtr"));
}

ControlBlockPtrLE ReferendStructs::getConcreteControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* reference,
    WrapperPtrLE wrapperPtrLE) {
  // Control block is always the 0th element of every concrete struct.
  return makeControlBlockPtrWithoutChecking(
      from, functionState, builder,
      wrapperPtrLE.refM->referend,
      LLVMBuildStructGEP(builder, wrapperPtrLE.refLE, 0, "controlPtr"));
}



ControlBlockPtrLE ReferendStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    InterfaceFatPtrLE interfaceFatPtrLE) {
  // Interface fat pointer's first element points directly at the control block,
  // and we dont have to cast it. We would have to cast if we were accessing the
  // actual object though.
  return makeControlBlockPtr(
      from, functionState, builder,
      interfaceFatPtrLE.refM->referend,
      LLVMBuildExtractValue(builder, interfaceFatPtrLE.refLE, 0, "controlPtr"));
}

ControlBlockPtrLE ReferendStructs::getControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    InterfaceFatPtrLE interfaceFatPtrLE) {
  // Interface fat pointer's first element points directly at the control block,
  // and we dont have to cast it. We would have to cast if we were accessing the
  // actual object though.
  return makeControlBlockPtrWithoutChecking(
      from, functionState, builder,
      interfaceFatPtrLE.refM->referend,
      LLVMBuildExtractValue(builder, interfaceFatPtrLE.refLE, 0, "controlPtr"));
}

ControlBlockPtrLE ReferendStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    Ref ref,
    Reference* referenceM) {
  auto referendM = referenceM->referend;
  if (dynamic_cast<InterfaceReferend*>(referendM)) {
    auto referenceLE =
        makeInterfaceFatPtr(
            from, functionState, builder, referenceM,
            globalState->region->checkValidReference(from, functionState, builder, referenceM, ref));
    return getControlBlockPtr(from, functionState, builder, referendM, referenceLE);
  } else if (dynamic_cast<StructReferend*>(referendM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->region->checkValidReference(from, functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<KnownSizeArrayT*>(referendM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->region->checkValidReference(from, functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<UnknownSizeArrayT*>(referendM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->region->checkValidReference(from, functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<Str*>(referendM)) {
    auto referenceLE =
        makeWrapperPtr(
            from, functionState, builder, referenceM,
            globalState->region->checkValidReference(from, functionState, builder, referenceM, ref));
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else {
    assert(false);
  }
}

ControlBlockPtrLE ReferendStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  auto referendM = referenceM->referend;
  if (dynamic_cast<InterfaceReferend*>(referendM)) {
    auto referenceLE = makeInterfaceFatPtr(from, functionState, builder, referenceM, ref);
    return getControlBlockPtr(from, functionState, builder, referendM, referenceLE);
  } else if (dynamic_cast<StructReferend*>(referendM)) {
    auto referenceLE = makeWrapperPtr(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<KnownSizeArrayT*>(referendM)) {
    auto referenceLE = makeWrapperPtr(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<UnknownSizeArrayT*>(referendM)) {
    auto referenceLE = makeWrapperPtr(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<Str*>(referendM)) {
    auto referenceLE = makeWrapperPtr(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtr(from, functionState, builder, referenceM, referenceLE);
  } else {
    assert(false);
  }
}

ControlBlockPtrLE ReferendStructs::getControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  auto referendM = referenceM->referend;
  if (dynamic_cast<InterfaceReferend*>(referendM)) {
    auto referenceLE = makeInterfaceFatPtrWithoutChecking(from, functionState, builder, referenceM, ref);
    return getControlBlockPtrWithoutChecking(from, functionState, builder, referendM, referenceLE);
  } else if (dynamic_cast<StructReferend*>(referendM)) {
    auto referenceLE = makeWrapperPtrWithoutChecking(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtrWithoutChecking(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<KnownSizeArrayT*>(referendM)) {
    auto referenceLE = makeWrapperPtrWithoutChecking(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtrWithoutChecking(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<UnknownSizeArrayT*>(referendM)) {
    auto referenceLE = makeWrapperPtrWithoutChecking(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtrWithoutChecking(from, functionState, builder, referenceM, referenceLE);
  } else if (dynamic_cast<Str*>(referendM)) {
    auto referenceLE = makeWrapperPtrWithoutChecking(from, functionState, builder, referenceM, ref);
    return getConcreteControlBlockPtrWithoutChecking(from, functionState, builder, referenceM, referenceLE);
  } else {
    assert(false);
  }
}


LLVMValueRef ReferendStructs::getStructContentsPtr(
    LLVMBuilderRef builder,
    Referend* referend,
    WrapperPtrLE wrapperPtrLE) {
  return LLVMBuildStructGEP(
      builder,
      wrapperPtrLE.refLE,
      1, // Inner struct is after the control block.
      "contentsPtr");
}


LLVMValueRef ReferendStructs::getVoidPtrFromInterfacePtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE) {
  assert(LLVMTypeOf(virtualArgLE.refLE) == functionState->defaultRegion->translateType(virtualParamMT));
  return LLVMBuildPointerCast(
      builder,
      getControlBlockPtr(FL(), functionState, builder, virtualParamMT->referend, virtualArgLE).refLE,
      LLVMPointerType(LLVMVoidType(), 0),
      "objAsVoidPtr");
}





void WeakableReferendStructs::translateStruct(
    StructDefinition* struuct,
    std::vector<LLVMTypeRef> membersLT) {
  assert(weakRefHeaderStructL);

  referendStructs.translateStruct(struuct, membersLT);

  LLVMTypeRef wrapperStructL = getWrapperStruct(struuct->referend);

  auto structWeakRefStructL = getStructWeakRefStruct(struuct->referend);
  std::vector<LLVMTypeRef> structWeakRefStructMemberTypesL;
  structWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
  structWeakRefStructMemberTypesL.push_back(LLVMPointerType(wrapperStructL, 0));
  LLVMStructSetBody(structWeakRefStructL, structWeakRefStructMemberTypesL.data(), structWeakRefStructMemberTypesL.size(), false);
}

void WeakableReferendStructs::declareStruct(StructDefinition* structM) {
  referendStructs.declareStruct(structM);

  auto structWeakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (structM->name->name + "w").c_str());
  assert(structWeakRefStructs.count(structM->name->name) == 0);
  structWeakRefStructs.emplace(structM->name->name, structWeakRefStructL);
}


void WeakableReferendStructs::declareEdge(
    Edge* edge) {
  referendStructs.declareEdge(edge);
}

void WeakableReferendStructs::translateEdge(
    Edge* edge,
    std::vector<LLVMValueRef> functions) {
  referendStructs.translateEdge(edge, functions);
}

void WeakableReferendStructs::declareInterface(InterfaceDefinition* interface) {
  referendStructs.declareInterface(interface);

  auto interfaceWeakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (interface->name->name + "w").c_str());
  assert(interfaceWeakRefStructs.count(interface->name->name) == 0);
  interfaceWeakRefStructs.emplace(interface->name->name, interfaceWeakRefStructL);
}

void WeakableReferendStructs::translateInterface(
    InterfaceDefinition* interface,
    std::vector<LLVMTypeRef> interfaceMethodTypesL) {
  assert(weakRefHeaderStructL);

  referendStructs.translateInterface(interface, interfaceMethodTypesL);

  LLVMTypeRef refStructL = getInterfaceRefStruct(interface->referend);

  auto interfaceWeakRefStructL = getInterfaceWeakRefStruct(interface->referend);
  std::vector<LLVMTypeRef> interfaceWeakRefStructMemberTypesL;
  interfaceWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
  interfaceWeakRefStructMemberTypesL.push_back(refStructL);
  LLVMStructSetBody(interfaceWeakRefStructL, interfaceWeakRefStructMemberTypesL.data(), interfaceWeakRefStructMemberTypesL.size(), false);
}


void WeakableReferendStructs::declareKnownSizeArray(
    KnownSizeArrayT* knownSizeArrayMT) {
  referendStructs.declareKnownSizeArray(knownSizeArrayMT);

  auto weakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (knownSizeArrayMT->name->name + "w").c_str());
  assert(knownSizeArrayWeakRefStructs.count(knownSizeArrayMT->name->name) == 0);
  knownSizeArrayWeakRefStructs.emplace(knownSizeArrayMT->name->name, weakRefStructL);
}

void WeakableReferendStructs::declareUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT) {
  referendStructs.declareUnknownSizeArray(unknownSizeArrayMT);

  auto weakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (unknownSizeArrayMT->name->name + "w").c_str());
  assert(unknownSizeArrayWeakRefStructs.count(unknownSizeArrayMT->name->name) == 0);
  unknownSizeArrayWeakRefStructs.emplace(unknownSizeArrayMT->name->name, weakRefStructL);
}

void WeakableReferendStructs::translateUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT,
    LLVMTypeRef elementLT) {
  assert(weakRefHeaderStructL);

  referendStructs.translateUnknownSizeArray(unknownSizeArrayMT, elementLT);

  auto unknownSizeArrayWrapperStruct = getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT);

  auto arrayWeakRefStructL = getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT);
  std::vector<LLVMTypeRef> arrayWeakRefStructMemberTypesL;
  arrayWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
  arrayWeakRefStructMemberTypesL.push_back(LLVMPointerType(unknownSizeArrayWrapperStruct, 0));
  LLVMStructSetBody(arrayWeakRefStructL, arrayWeakRefStructMemberTypesL.data(), arrayWeakRefStructMemberTypesL.size(), false);
}

void WeakableReferendStructs::translateKnownSizeArray(
    KnownSizeArrayT* knownSizeArrayMT,
    LLVMTypeRef elementLT) {
  assert(weakRefHeaderStructL);

  referendStructs.translateKnownSizeArray(knownSizeArrayMT, elementLT);

  auto knownSizeArrayWrapperStruct = getKnownSizeArrayWrapperStruct(knownSizeArrayMT);

  auto arrayWeakRefStructL = getKnownSizeArrayWeakRefStruct(knownSizeArrayMT);
  std::vector<LLVMTypeRef> arrayWeakRefStructMemberTypesL;
  arrayWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
  arrayWeakRefStructMemberTypesL.push_back(LLVMPointerType(knownSizeArrayWrapperStruct, 0));
  LLVMStructSetBody(arrayWeakRefStructL, arrayWeakRefStructMemberTypesL.data(), arrayWeakRefStructMemberTypesL.size(), false);
}

WeakFatPtrLE WeakableReferendStructs::makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) {
  if (auto structReferendM = dynamic_cast<StructReferend*>(referenceM_->referend)) {
    assert(LLVMTypeOf(ptrLE) == getStructWeakRefStruct(structReferendM));
  } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(referenceM_->referend)) {
    assert(
        LLVMTypeOf(ptrLE) == weakVoidRefStructL ||
            LLVMTypeOf(ptrLE) == getInterfaceWeakRefStruct(interfaceReferendM));
  } else if (auto ksaT = dynamic_cast<KnownSizeArrayT*>(referenceM_->referend)) {
    assert(LLVMTypeOf(ptrLE) == getKnownSizeArrayWeakRefStruct(ksaT));
  } else if (auto usaT = dynamic_cast<UnknownSizeArrayT*>(referenceM_->referend)) {
    assert(LLVMTypeOf(ptrLE) == getUnknownSizeArrayWeakRefStruct(usaT));
  } else {
    assert(false);
  }
  return WeakFatPtrLE(referenceM_, ptrLE);
}

ControlBlockPtrLE WeakableReferendStructs::getConcreteControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* reference,
    WrapperPtrLE wrapperPtrLE) {
  return referendStructs.getConcreteControlBlockPtr(from, functionState, builder, reference, wrapperPtrLE);
}

LLVMTypeRef WeakableReferendStructs::getStringWrapperStruct() {
  return referendStructs.getStringWrapperStruct();
}

WrapperPtrLE WeakableReferendStructs::makeWrapperPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    LLVMValueRef ptrLE) {
  return referendStructs.makeWrapperPtr(checkerAFL, functionState, builder, referenceM, ptrLE);
}

InterfaceFatPtrLE WeakableReferendStructs::makeInterfaceFatPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM_,
    LLVMValueRef ptrLE) {
  return referendStructs.makeInterfaceFatPtr(checkerAFL, functionState, builder, referenceM_, ptrLE);
}

InterfaceFatPtrLE WeakableReferendStructs::makeInterfaceFatPtrWithoutChecking(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM_,
    LLVMValueRef ptrLE) {
  return referendStructs.makeInterfaceFatPtrWithoutChecking(checkerAFL, functionState, builder, referenceM_, ptrLE);
}

//ControlBlockPtrLE WeakableReferendStructs::makeControlBlockPtr(
//    AreaAndFileAndLine checkerAFL,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Referend* referendM,
//    LLVMValueRef controlBlockPtrLE) {
//  return referendStructs.makeControlBlockPtr(checkerAFL, functionState, builder, referendM, controlBlockPtrLE);
//}

LLVMValueRef WeakableReferendStructs::getStringBytesPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref ref) {
  return referendStructs.getStringBytesPtr(functionState, builder, ref);
}

LLVMValueRef WeakableReferendStructs::getStringLen(
    FunctionState* functionState, LLVMBuilderRef builder, Ref ref) {
  return referendStructs.getStringLen(functionState, builder, ref);
}


ControlBlockPtrLE WeakableReferendStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    InterfaceFatPtrLE interfaceFatPtrLE) {
  return referendStructs.getControlBlockPtr(from, functionState, builder, referendM, interfaceFatPtrLE);
}

ControlBlockPtrLE WeakableReferendStructs::getControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    InterfaceFatPtrLE interfaceFatPtrLE) {
  return referendStructs.getControlBlockPtrWithoutChecking(from, functionState, builder, referendM, interfaceFatPtrLE);
}

ControlBlockPtrLE WeakableReferendStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    Ref ref,
    Reference* referenceM) {
  return referendStructs.getControlBlockPtr(from, functionState, builder, ref, referenceM);
}

ControlBlockPtrLE WeakableReferendStructs::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  return referendStructs.getControlBlockPtr(from, functionState, builder, ref, referenceM);
}

ControlBlockPtrLE WeakableReferendStructs::getControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  return referendStructs.getControlBlockPtrWithoutChecking(from, functionState, builder, ref, referenceM);
}

LLVMValueRef WeakableReferendStructs::getStructContentsPtr(
    LLVMBuilderRef builder,
    Referend* referend,
    WrapperPtrLE wrapperPtrLE) {
  return referendStructs.getStructContentsPtr(builder, referend, wrapperPtrLE);
}

LLVMValueRef WeakableReferendStructs::getVoidPtrFromInterfacePtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE) {
  return referendStructs.getVoidPtrFromInterfacePtr(
      functionState, builder, virtualParamMT, virtualArgLE);
}