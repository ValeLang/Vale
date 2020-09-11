#include "ast.h"

Weakability Program::getReferendWeakability(Referend* referend) {
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    return getStruct(structReferend->fullName)->weakability;
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
    return getInterface(interfaceReferend->fullName)->weakability;
  } else {
    return Weakability::NON_WEAKABLE;
  }
}

Mutability Program::getReferendMutability(Referend* referendM) {
  if (dynamic_cast<Int*>(referendM) ||
      dynamic_cast<Bool*>(referendM) ||
      dynamic_cast<Float*>(referendM)) {
    return Mutability::IMMUTABLE;
  } else if (
      auto structRnd = dynamic_cast<StructReferend*>(referendM)) {
    auto structM = getStruct(structRnd->fullName);
    return structM->mutability;
  } else if (
      auto interfaceRnd = dynamic_cast<InterfaceReferend*>(referendM)) {
    auto interfaceM = getInterface(interfaceRnd->fullName);
    return interfaceM->mutability;
  } else if (
      auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT*>(referendM)) {
    return knownSizeArrayMT->rawArray->mutability;
  } else {
    std::cerr << typeid(*referendM).name() << std::endl;
    assert(false);
    return Mutability::MUTABLE;
  }
}