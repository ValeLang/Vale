
#include "structs.h"


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
    StructReferend* structReferend,
    std::vector<LLVMTypeRef> membersLT) {
  LLVMTypeRef valStructL = getInnerStruct(structReferend);
  LLVMStructSetBody(
      valStructL, membersLT.data(), membersLT.size(), false);

  LLVMTypeRef wrapperStructL = getWrapperStruct(structReferend);
  std::vector<LLVMTypeRef> wrapperStructMemberTypesL;

  // First member is a ref counts struct. We don't include the int directly
  // because we want fat pointers to point to this struct, so they can reach
  // into it and increment without doing any casting.
  wrapperStructMemberTypesL.push_back(controlBlockStructL);

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

void ReferendStructs::declareInterface(InterfaceReferend* interfaceReferend) {

  auto interfaceRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), interfaceReferend->fullName->name.c_str());
  assert(interfaceRefStructs.count(interfaceReferend->fullName->name) == 0);
  interfaceRefStructs.emplace(interfaceReferend->fullName->name, interfaceRefStructL);

  auto interfaceTableStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (interfaceReferend->fullName->name + "itable").c_str());
  assert(interfaceTableStructs.count(interfaceReferend->fullName->name) == 0);
  interfaceTableStructs.emplace(interfaceReferend->fullName->name, interfaceTableStructL);
}

void ReferendStructs::translateInterface(
    InterfaceReferend* interfaceReferend,
    std::vector<LLVMTypeRef> interfaceMethodTypesL) {
  LLVMTypeRef itableStruct =
      getInterfaceTableStruct(interfaceReferend);

  LLVMStructSetBody(
      itableStruct, interfaceMethodTypesL.data(), interfaceMethodTypesL.size(), false);

  LLVMTypeRef refStructL = getInterfaceRefStruct(interfaceReferend);
  std::vector<LLVMTypeRef> refStructMemberTypesL;

  // this points to the control block.
  // It makes it easier to increment and decrement ref counts.
  refStructMemberTypesL.push_back(LLVMPointerType(controlBlockStructL, 0));


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

  elementsL.push_back(controlBlockStructL);

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

  elementsL.push_back(controlBlockStructL);

  elementsL.push_back(innerArrayLT);

  LLVMStructSetBody(knownSizeArrayWrapperStruct, elementsL.data(), elementsL.size(), false);
}



void WeakableReferendStructs::translateStruct(
    StructReferend* structReferend,
    std::vector<LLVMTypeRef> membersLT) {
  referendStructs.translateStruct(structReferend, membersLT);

  LLVMTypeRef valStructL = getInnerStruct(structReferend);
  LLVMStructSetBody(
      valStructL, membersLT.data(), membersLT.size(), false);

  LLVMTypeRef wrapperStructL = getWrapperStruct(structReferend);

  auto structWeakRefStructL = getStructWeakRefStruct(structReferend);
  std::vector<LLVMTypeRef> structWeakRefStructMemberTypesL;
  structWeakRefStructMemberTypesL.push_back(structWeakRefStructL);
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

void WeakableReferendStructs::declareInterface(InterfaceReferend* interfaceReferend) {
  referendStructs.declareInterface(interfaceReferend);

  auto interfaceWeakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (interfaceReferend->fullName->name + "w").c_str());
  assert(interfaceWeakRefStructs.count(interfaceReferend->fullName->name) == 0);
  interfaceWeakRefStructs.emplace(interfaceReferend->fullName->name, interfaceWeakRefStructL);
}

void WeakableReferendStructs::translateInterface(
    InterfaceReferend* interfaceReferend,
    std::vector<LLVMTypeRef> interfaceMethodTypesL) {
  referendStructs.translateInterface(interfaceReferend, interfaceMethodTypesL);

  LLVMTypeRef refStructL = getInterfaceRefStruct(interfaceReferend);

  auto interfaceWeakRefStructL = getInterfaceWeakRefStruct(interfaceReferend);
  std::vector<LLVMTypeRef> interfaceWeakRefStructMemberTypesL;
  interfaceWeakRefStructMemberTypesL.push_back(interfaceWeakRefStructL);
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
  referendStructs.translateKnownSizeArray(knownSizeArrayMT, elementLT);

  auto knownSizeArrayWrapperStruct = getKnownSizeArrayWrapperStruct(knownSizeArrayMT);

  auto arrayWeakRefStructL = getKnownSizeArrayWeakRefStruct(knownSizeArrayMT);
  std::vector<LLVMTypeRef> arrayWeakRefStructMemberTypesL;
  arrayWeakRefStructMemberTypesL.push_back(weakRefHeaderStructL);
  arrayWeakRefStructMemberTypesL.push_back(LLVMPointerType(knownSizeArrayWrapperStruct, 0));
  LLVMStructSetBody(arrayWeakRefStructL, arrayWeakRefStructMemberTypesL.data(), arrayWeakRefStructMemberTypesL.size(), false);
}
