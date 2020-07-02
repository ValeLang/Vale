#include <iostream>

#include "translatetype.h"

LLVMTypeRef translateType(GlobalState* globalState, Reference* referenceM) {
  if (dynamic_cast<Int*>(referenceM->referend) != nullptr) {
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMInt64Type();
  } else if (dynamic_cast<Bool*>(referenceM->referend) != nullptr) {
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMInt1Type();
  } else if (dynamic_cast<Never*>(referenceM->referend) != nullptr) {
    return LLVMArrayType(LLVMIntType(NEVER_INT_BITS), 0);
  } else if (auto structReferend =
      dynamic_cast<StructReferend*>(referenceM->referend)) {

    auto structM = globalState->program->getStruct(structReferend->fullName);
    if (structM->mutability == Mutability::MUTABLE) {
      auto countedStructL = globalState->getCountedStruct(structReferend->fullName);
      return LLVMPointerType(countedStructL, 0);
    } else {
      auto innerStructL = globalState->getInnerStruct(structReferend->fullName);
      size_t innerStructSizeBytes = LLVMABISizeOfType(globalState->dataLayout, innerStructL);
      bool inliine = innerStructSizeBytes <= MAX_INLINE_SIZE_BYTES;
      if (inliine) {
        return globalState->getInnerStruct(structReferend->fullName);
      } else {
        auto countedStructL = globalState->getCountedStruct(structReferend->fullName);
        return LLVMPointerType(countedStructL, 0);
      }
    }
  } else {
    std::cerr << "Unimplemented type: " << typeid(*referenceM->referend).name() << std::endl;
    assert(false);
    return nullptr;
  }
}

std::vector<LLVMTypeRef> translateTypes(GlobalState* globalState, std::vector<Reference*> referencesM) {
  std::vector<LLVMTypeRef> result;
  for (auto referenceM : referencesM) {
    result.push_back(translateType(globalState, referenceM));
  }
  return result;
}

Mutability ownershipToMutability(Ownership ownership) {
  switch (ownership) {
    case Ownership::SHARE:
      return Mutability::IMMUTABLE;
    case Ownership::BORROW:
    case Ownership::OWN:
      return Mutability::MUTABLE;
  }
  assert(false);
  return Mutability::MUTABLE;
}

bool isInlImm(GlobalState* globalState, Reference* referenceM) {
  if (dynamic_cast<Int*>(referenceM->referend) ||
      dynamic_cast<Bool*>(referenceM->referend) ||
      dynamic_cast<Float*>(referenceM->referend)) {
    return true;
  } else if (
      auto structRnd = dynamic_cast<StructReferend*>(referenceM->referend)) {
    auto structM = globalState->program->getStruct(structRnd->fullName);
    if (structM->mutability == Mutability::MUTABLE) {
      return false;
    } else if (structM->mutability == Mutability::IMMUTABLE) {
      auto innerStructL = globalState->getInnerStruct(structRnd->fullName);
      size_t innerStructSizeBytes =
          LLVMABISizeOfType(globalState->dataLayout, innerStructL);
      bool inliine = innerStructSizeBytes <= MAX_INLINE_SIZE_BYTES;
      return inliine;
    } else {
      assert(false);
      return false;
    }
  } else {
    assert(false); // impl
    return false;
  }
}
