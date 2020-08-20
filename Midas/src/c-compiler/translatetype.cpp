#include <iostream>
#include "function/expressions/shared/heap.h"

#include "translatetype.h"

LLVMTypeRef translateType(GlobalState* globalState, Reference* referenceM) {
  if (dynamic_cast<Int*>(referenceM->referend) != nullptr) {
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMInt64Type();
  } else if (dynamic_cast<Bool*>(referenceM->referend) != nullptr) {
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMInt1Type();
  } else if (dynamic_cast<Str*>(referenceM->referend) != nullptr) {
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMPointerType(globalState->stringWrapperStructL, 0);
  } else if (dynamic_cast<Never*>(referenceM->referend) != nullptr) {
    return LLVMArrayType(LLVMIntType(NEVER_INT_BITS), 0);
  } else if (auto knownSizeArrayMT =
      dynamic_cast<KnownSizeArrayT*>(referenceM->referend)) {
    if (knownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
      assert(false);
      return nullptr;
    } else {
      auto knownSizeArrayCountedStructLT = globalState->getKnownSizeArrayWrapperStruct(knownSizeArrayMT->name);
      if (referenceM->location == Location::INLINE) {
        return knownSizeArrayCountedStructLT;
      } else {
        if (referenceM->ownership == Ownership::OWN) {
          return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
        } else if (referenceM->ownership == Ownership::BORROW) {
          return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
        } else if (referenceM->ownership == Ownership::SHARE) {
          return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
        } else if (referenceM->ownership == Ownership::WEAK) {
          return globalState->getKnownSizeArrayWeakRefStruct(knownSizeArrayMT->name);
        } else assert(false);
      }
    }
  } else if (auto unknownSizeArrayMT =
      dynamic_cast<UnknownSizeArrayT*>(referenceM->referend)) {
    auto unknownSizeArrayCountedStructLT = globalState->getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT->name);
    if (referenceM->ownership == Ownership::OWN) {
      return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
    } else if (referenceM->ownership == Ownership::BORROW) {
      return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
    } else if (referenceM->ownership == Ownership::SHARE) {
      return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
    } else if (referenceM->ownership == Ownership::WEAK) {
      return globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name);
    } else assert(false);
  } else if (auto structReferend =
      dynamic_cast<StructReferend*>(referenceM->referend)) {

    auto structM = globalState->program->getStruct(structReferend->fullName);
    if (structM->mutability == Mutability::MUTABLE) {
      auto countedStructL = globalState->getWrapperStruct(structReferend->fullName);
      if (referenceM->ownership == Ownership::OWN) {
        return LLVMPointerType(countedStructL, 0);
      } else if (referenceM->ownership == Ownership::BORROW) {
        return LLVMPointerType(countedStructL, 0);
      } else if (referenceM->ownership == Ownership::WEAK) {
        return globalState->getStructWeakRefStruct(structM->name);
      } else {
        assert(false);
      }
    } else {
      auto innerStructL = globalState->getInnerStruct(structReferend->fullName);
      if (referenceM->location == Location::INLINE) {
        return globalState->getInnerStruct(structReferend->fullName);
      } else {
        auto countedStructL = globalState->getWrapperStruct(structReferend->fullName);
        return LLVMPointerType(countedStructL, 0);
      }
    }
  } else if (auto interfaceReferend =
      dynamic_cast<InterfaceReferend*>(referenceM->referend)) {
    auto interfaceM = globalState->program->getInterface(interfaceReferend->fullName);
    auto interfaceRefStructL =
        globalState->getInterfaceRefStruct(interfaceReferend->fullName);
    if (interfaceM->mutability == Mutability::MUTABLE) {
      if (referenceM->ownership == Ownership::OWN) {
        return interfaceRefStructL;
      } else if (referenceM->ownership == Ownership::BORROW) {
        return interfaceRefStructL;
      } else if (referenceM->ownership == Ownership::WEAK) {
        return globalState->getInterfaceWeakRefStruct(interfaceM->name);
      } else {
        assert(false);
      }
    } else {
      return interfaceRefStructL;
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
    case Ownership::WEAK:
      return Mutability::MUTABLE;
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
    Prototype* prototype) {
  auto returnLT = translateType(globalState, getEffectiveType(globalState, prototype->returnType));
  auto paramsLT =
      translateTypes(
          globalState,
          getEffectiveTypes(globalState, prototype->params));
  return LLVMFunctionType(returnLT, paramsLT.data(), paramsLT.size(), false);
}