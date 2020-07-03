#include <iostream>

#include "translatetype.h"

LLVMTypeRef makeInnerArrayLT(GlobalState* globalState, KnownSizeArrayT* knownSizeArrayMT) {
  auto elementLT = translateType(globalState, knownSizeArrayMT->rawArray->elementType);
  return LLVMArrayType(elementLT, knownSizeArrayMT->size);
}

// This gives the actual struct, *not* a pointer to a struct, which you sometimes
// might need instead. For that, use translateType.
LLVMTypeRef translateKnownSizeArrayToCountedStruct(GlobalState* globalState, KnownSizeArrayT* knownSizeArrayMT) {
  auto innerArrayLT = makeInnerArrayLT(globalState, knownSizeArrayMT);

  auto iter = globalState->knownSizeArrayCountedStructs.find(knownSizeArrayMT->name);
  if (iter == globalState->knownSizeArrayCountedStructs.end()) {
    auto countedStruct = LLVMStructCreateNamed(LLVMGetGlobalContext(), knownSizeArrayMT->name->name.c_str());
    std::vector<LLVMTypeRef> elementsL;
    elementsL.push_back(globalState->controlBlockStructL);
    elementsL.push_back(innerArrayLT);
    LLVMStructSetBody(countedStruct, elementsL.data(), elementsL.size(), false);

    iter = globalState->knownSizeArrayCountedStructs.emplace(knownSizeArrayMT->name, countedStruct).first;
  }

  return iter->second;
}

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
  } else if (auto knownSizeArrayMT =
      dynamic_cast<KnownSizeArrayT*>(referenceM->referend)) {
    if (knownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
      assert(false);
      return nullptr;
    } else {
      auto innerArrayLT = makeInnerArrayLT(globalState, knownSizeArrayMT);
      size_t innerArraySizeBytes = LLVMABISizeOfType(globalState->dataLayout, innerArrayLT);
      bool inliine = innerArraySizeBytes <= MAX_INLINE_SIZE_BYTES;
      if (inliine) {
        return innerArrayLT;
      } else {
        auto knownSizeArrayCountedStructLT =
            translateKnownSizeArrayToCountedStruct(
                globalState, knownSizeArrayMT);

        return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
      }
    }
  } else if (auto interfaceReferend =
      dynamic_cast<InterfaceReferend*>(referenceM->referend)) {
    auto interfaceRefStructL =
        globalState->getInterfaceRefStruct(interfaceReferend->fullName);
    return interfaceRefStructL;
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
  } else if (
      auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT*>(referenceM->referend)) {
    if (knownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
      return false;
    } else if (knownSizeArrayMT->rawArray->mutability == Mutability::IMMUTABLE) {
      auto innerArrayLT = makeInnerArrayLT(globalState, knownSizeArrayMT);
      size_t innerStructSizeBytes =
          LLVMABISizeOfType(globalState->dataLayout, innerArrayLT);
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

LLVMTypeRef translatePrototypeToFunctionType(
    GlobalState* globalState,
    Prototype* prototype) {
  auto returnLT = translateType(globalState, prototype->returnType);
  auto paramsLT = translateTypes(globalState, prototype->params);
  return LLVMFunctionType(returnLT, paramsLT.data(), paramsLT.size(), false);
}