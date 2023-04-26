#include <iostream>
#include "region/common/heap.h"

#include "translatetype.h"

std::vector<LLVMTypeRef> translateTypes(
    GlobalState* globalState,
    std::vector<Reference*> referencesM) {
  std::vector<LLVMTypeRef> result;
  for (auto referenceM : referencesM) {
    result.push_back(globalState->getRegion(referenceM)->translateType(referenceM));
  }
  return result;
}

Mutability ownershipToMutability(Ownership ownership) {
  switch (ownership) {
    case Ownership::MUTABLE_SHARE:
    case Ownership::IMMUTABLE_SHARE:
      return Mutability::IMMUTABLE;
    case Ownership::MUTABLE_BORROW:
    case Ownership::IMMUTABLE_BORROW:
    case Ownership::OWN:
    case Ownership::WEAK:
      return Mutability::MUTABLE;
    default:
      assert(false);
  }
}

LLVMTypeRef translatePrototypeToFunctionType(
    GlobalState* globalState,
    Prototype* prototype) {
  auto returnLT = globalState->getRegion(prototype->returnType)->translateType(prototype->returnType);
  auto paramsLT = translateTypes(globalState, prototype->params);
  return LLVMFunctionType(returnLT, paramsLT.data(), paramsLT.size(), false);
}

LLVMTypeRef translateInterfaceMethodToFunctionType(
    GlobalState* globalState,
    InterfaceMethod* method) {
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);
  auto returnMT = method->prototype->returnType;
  auto paramsMT = method->prototype->params;
  auto returnLT = globalState->getRegion(returnMT)->translateType(returnMT);
  auto paramsLT = translateTypes(globalState, paramsMT);
  paramsLT[method->virtualParamIndex] =
      globalState->getRegion(paramsMT[method->virtualParamIndex])
          ->getInterfaceMethodVirtualParamAnyType(paramsMT[method->virtualParamIndex]);
  // For the unique generation number first param.
  // Eventually this might be a region instance pointer, who knows.
  paramsLT.insert(paramsLT.begin(), LLVMPointerType(genLT, 0));
  return LLVMFunctionType(returnLT, paramsLT.data(), paramsLT.size(), false);
}