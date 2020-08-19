#include <iostream>
#include "function/expressions/shared/heap.h"

#include "translatetype.h"

LLVMTypeRef makeInnerKnownSizeArrayLT(GlobalState* globalState, KnownSizeArrayT* knownSizeArrayMT) {
  auto elementLT =
      translateType(
          globalState,
          getEffectiveType(globalState, knownSizeArrayMT->rawArray->elementType));
  return LLVMArrayType(elementLT, knownSizeArrayMT->size);
}

// This gives the actual struct, *not* a pointer to a struct, which you sometimes
// might need instead. For that, use translateType.
LLVMTypeRef translateKnownSizeArrayToWrapperStruct(
    GlobalState* globalState,
    KnownSizeArrayT* knownSizeArrayMT) {
  auto innerArrayLT = makeInnerKnownSizeArrayLT(globalState, knownSizeArrayMT);

  auto iter = globalState->knownSizeArrayCountedStructs.find(knownSizeArrayMT->name);
  if (iter == globalState->knownSizeArrayCountedStructs.end()) {
    auto countedStruct = LLVMStructCreateNamed(LLVMGetGlobalContext(), knownSizeArrayMT->name->name.c_str());

    std::vector<LLVMTypeRef> elementsL;

    if (knownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
      if (globalState->opt->regionOverride == RegionOverride::ASSIST) {
        elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
      } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
        elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
      } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
        // In resilient mode, we can have weak refs to arrays
        elementsL.push_back(globalState->mutWeakableControlBlockStructL);
      } else assert(false);
    } else if (knownSizeArrayMT->rawArray->mutability == Mutability::IMMUTABLE) {
      elementsL.push_back(globalState->immControlBlockStructL);
    } else assert(false);

    elementsL.push_back(innerArrayLT);

    LLVMStructSetBody(countedStruct, elementsL.data(), elementsL.size(), false);

    iter = globalState->knownSizeArrayCountedStructs.emplace(knownSizeArrayMT->name, countedStruct).first;
  }

  return iter->second;
}

LLVMTypeRef makeInnerUnknownSizeArrayLT(GlobalState* globalState, UnknownSizeArrayT* unknownSizeArrayMT) {
  auto elementLT = translateType(globalState, getEffectiveType(globalState, unknownSizeArrayMT->rawArray->elementType));
  return LLVMArrayType(elementLT, 0);
}

// This gives the actual struct, *not* a pointer to a struct, which you sometimes
// might need instead. For that, use translateType.
LLVMTypeRef translateUnknownSizeArrayToWrapperStruct(
    GlobalState* globalState,
    UnknownSizeArrayT* unknownSizeArrayMT) {
  auto innerArrayLT = makeInnerUnknownSizeArrayLT(globalState, unknownSizeArrayMT);

  auto iter = globalState->unknownSizeArrayCountedStructs.find(unknownSizeArrayMT->name);
  if (iter == globalState->unknownSizeArrayCountedStructs.end()) {
    auto countedStruct = LLVMStructCreateNamed(LLVMGetGlobalContext(), (unknownSizeArrayMT->name->name + "rc").c_str());

    std::vector<LLVMTypeRef> elementsL;

    if (unknownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
      if (globalState->opt->regionOverride == RegionOverride::ASSIST) {
        elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
      } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
        elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
      } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
        // In resilient mode, we can have weak refs to arrays
        elementsL.push_back(globalState->mutWeakableControlBlockStructL);
      } else assert(false);
    } else if (unknownSizeArrayMT->rawArray->mutability == Mutability::IMMUTABLE) {
      elementsL.push_back(globalState->immControlBlockStructL);
    } else assert(false);

    elementsL.push_back(LLVMInt64Type());

    elementsL.push_back(innerArrayLT);

    LLVMStructSetBody(countedStruct, elementsL.data(), elementsL.size(), false);

    iter = globalState->unknownSizeArrayCountedStructs.emplace(unknownSizeArrayMT->name, countedStruct).first;
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
      auto innerArrayLT = makeInnerKnownSizeArrayLT(globalState,
          knownSizeArrayMT);
      if (referenceM->location == Location::INLINE) {
        return innerArrayLT;
      } else {
        auto knownSizeArrayCountedStructLT =
            translateKnownSizeArrayToWrapperStruct(
                globalState, knownSizeArrayMT);

        return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
      }
    }
  } else if (auto unknownSizeArrayMT =
      dynamic_cast<UnknownSizeArrayT*>(referenceM->referend)) {
    auto knownSizeArrayCountedStructLT =
        translateUnknownSizeArrayToWrapperStruct(
            globalState, unknownSizeArrayMT);
    return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
  } else if (auto structReferend =
      dynamic_cast<StructReferend*>(referenceM->referend)) {

    auto structM = globalState->program->getStruct(structReferend->fullName);
    if (structM->mutability == Mutability::MUTABLE) {
      auto countedStructL = globalState->getCountedStruct(structReferend->fullName);
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
        auto countedStructL = globalState->getCountedStruct(structReferend->fullName);
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