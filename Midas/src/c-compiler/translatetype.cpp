#include <iostream>
#include "function/expressions/shared/heap.h"

#include "translatetype.h"

std::vector<LLVMTypeRef> translateTypes(
    GlobalState* globalState,
    IRegion* region,
    std::vector<Reference*> referencesM) {
  std::vector<LLVMTypeRef> result;
  for (auto referenceM : referencesM) {
    result.push_back(region->translateType(globalState, referenceM));
  }
  return result;
}

Mutability ownershipToMutability(Ownership ownership) {
  switch (ownership) {
    case Ownership::SHARE:
      return Mutability::IMMUTABLE;
    case Ownership::BORROW:
    case Ownership::OWN:
    case Ownership::WEAK:
      return Mutability::MUTABLE;
    default:
      assert(false);
  }
}

Mutability getMutability(GlobalState* globalState, Reference* referenceM) {
  if (dynamic_cast<Int*>(referenceM->referend) ||
      dynamic_cast<Bool*>(referenceM->referend) ||
      dynamic_cast<Float*>(referenceM->referend)) {
    return Mutability::IMMUTABLE;
  } else if (
      auto structRnd = dynamic_cast<StructReferend*>(referenceM->referend)) {
    auto structM = globalState->program->getStruct(structRnd->fullName);
    return structM->mutability;
  } else if (
      auto interfaceRnd = dynamic_cast<InterfaceReferend*>(referenceM->referend)) {
    auto interfaceM = globalState->program->getInterface(interfaceRnd->fullName);
    return interfaceM->mutability;
  } else if (
      auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT*>(referenceM->referend)) {
    return knownSizeArrayMT->rawArray->mutability;
  } else {
    std::cerr << typeid(*referenceM->referend).name() << std::endl;
    assert(false);
    return Mutability::MUTABLE;
  }
}

LLVMTypeRef translatePrototypeToFunctionType(
    GlobalState* globalState,
    IRegion* region,
    Prototype* prototype) {
  auto returnLT = region->translateType(globalState, prototype->returnType);
  auto paramsLT = translateTypes(globalState, region, prototype->params);
  return LLVMFunctionType(returnLT, paramsLT.data(), paramsLT.size(), false);
}