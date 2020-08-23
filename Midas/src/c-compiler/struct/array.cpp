#include <iostream>
#include <function/expressions/shared/shared.h>
#include <function/expressions/shared/weaks.h>

#include "array.h"

#include "translatetype.h"

LLVMTypeRef makeInnerKnownSizeArrayLT(GlobalState* globalState, KnownSizeArrayT* knownSizeArrayMT) {
  auto elementLT =
      translateType(
          globalState,
          getEffectiveType(globalState, knownSizeArrayMT->rawArray->elementType));
  return LLVMArrayType(elementLT, knownSizeArrayMT->size);
}

LLVMTypeRef makeInnerUnknownSizeArrayLT(GlobalState* globalState, UnknownSizeArrayT* unknownSizeArrayMT) {
  auto elementLT =
      translateType(
          globalState,
          getEffectiveType(globalState, unknownSizeArrayMT->rawArray->elementType));
  return LLVMArrayType(elementLT, 0);
}

void declareKnownSizeArray(
    GlobalState* globalState,
    KnownSizeArrayT* knownSizeArrayMT) {

  auto countedStruct = LLVMStructCreateNamed(LLVMGetGlobalContext(), knownSizeArrayMT->name->name.c_str());
  globalState->knownSizeArrayWrapperStructs.emplace(knownSizeArrayMT->name->name, countedStruct).first;

  auto weakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (knownSizeArrayMT->name->name + "w").c_str());
  assert(globalState->knownSizeArrayWeakRefStructs.count(knownSizeArrayMT->name->name) == 0);
  globalState->knownSizeArrayWeakRefStructs.emplace(knownSizeArrayMT->name->name, weakRefStructL);
}

void translateKnownSizeArray(
    GlobalState* globalState,
    KnownSizeArrayT* knownSizeArrayMT) {
  auto knownSizeArrayWrapperStruct = globalState->getKnownSizeArrayWrapperStruct(knownSizeArrayMT->name);

  auto innerArrayLT = makeInnerKnownSizeArrayLT(globalState, knownSizeArrayMT);

  std::vector<LLVMTypeRef> elementsL;

  if (knownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
    if (globalState->opt->regionOverride == RegionOverride::ASSIST) {
      elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
      elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
      // In resilient mode, we can have weak refs to arrays
      elementsL.push_back(globalState->mutWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_FAST) {
      // In resilient mode, we can have weak refs to arrays
      elementsL.push_back(globalState->mutWeakableControlBlockStructL);
    } else assert(false);
  } else if (knownSizeArrayMT->rawArray->mutability == Mutability::IMMUTABLE) {
    elementsL.push_back(globalState->immControlBlockStructL);
  } else assert(false);

  elementsL.push_back(innerArrayLT);

  LLVMStructSetBody(knownSizeArrayWrapperStruct, elementsL.data(), elementsL.size(), false);

  auto arrayWeakRefStructL = globalState->getKnownSizeArrayWeakRefStruct(knownSizeArrayMT->name);
  makeKnownSizeArrayWeakRefStruct(globalState, knownSizeArrayWrapperStruct, arrayWeakRefStructL);
}

void declareUnknownSizeArray(
    GlobalState* globalState,
    UnknownSizeArrayT* unknownSizeArrayMT) {
  auto countedStruct = LLVMStructCreateNamed(LLVMGetGlobalContext(), (unknownSizeArrayMT->name->name + "rc").c_str());
  globalState->unknownSizeArrayWrapperStructs.emplace(unknownSizeArrayMT->name->name, countedStruct).first;

  auto weakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (unknownSizeArrayMT->name->name + "w").c_str());
  assert(globalState->unknownSizeArrayWeakRefStructs.count(unknownSizeArrayMT->name->name) == 0);
  globalState->unknownSizeArrayWeakRefStructs.emplace(unknownSizeArrayMT->name->name, weakRefStructL);
}

void translateUnknownSizeArray(
    GlobalState* globalState,
    UnknownSizeArrayT* unknownSizeArrayMT) {

  auto unknownSizeArrayWrapperStruct = globalState->getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT->name);

  auto innerArrayLT = makeInnerUnknownSizeArrayLT(globalState, unknownSizeArrayMT);

  std::vector<LLVMTypeRef> elementsL;

  if (unknownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
    if (globalState->opt->regionOverride == RegionOverride::ASSIST) {
      elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
      elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
      // In resilient mode, we can have weak refs to arrays
      elementsL.push_back(globalState->mutWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_FAST) {
      // In resilient mode, we can have weak refs to arrays
      elementsL.push_back(globalState->mutWeakableControlBlockStructL);
    } else assert(false);
  } else if (unknownSizeArrayMT->rawArray->mutability == Mutability::IMMUTABLE) {
    elementsL.push_back(globalState->immControlBlockStructL);
  } else assert(false);

  elementsL.push_back(LLVMInt64Type());

  elementsL.push_back(innerArrayLT);

  LLVMStructSetBody(unknownSizeArrayWrapperStruct, elementsL.data(), elementsL.size(), false);

  auto arrayWeakRefStructL = globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name);
  makeUnknownSizeArrayWeakRefStruct(globalState, unknownSizeArrayWrapperStruct, arrayWeakRefStructL);
}
