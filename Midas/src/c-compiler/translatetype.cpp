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

    bool inliine = true;//referenceM->location == INLINE; TODO

    auto structL = globalState->getStruct(structReferend->fullName);

    if (inliine) {
      return structL;
    } else {
      // TODO return a pointer to it perhaps?
      assert(false);
      return nullptr;
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
