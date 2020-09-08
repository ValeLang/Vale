#ifndef REGION_COMMON_DEFAULTIMMUTABLES_DEFAULTIMMUTABLES_H_
#define REGION_COMMON_DEFAULTIMMUTABLES_DEFAULTIMMUTABLES_H_

#include <llvm-c/Types.h>
#include <globalstate.h>
#include <iostream>
#include <region/common/primitives.h>

class DefaultRefCounting {
public:
  LLVMTypeRef translateType(GlobalState* globalState, Reference* referenceM) {
    if (primitives.isPrimitive(referenceM)) {
      return primitives.translatePrimitive(referenceM);
    } else {
      if (dynamic_cast<Str *>(referenceM->referend) != nullptr) {
        assert(referenceM->location != Location::INLINE);
        assert(referenceM->ownership == Ownership::SHARE);
        return LLVMPointerType(globalState->stringWrapperStructL, 0);
      } else if (auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT *>(referenceM->referend)) {
        assert(referenceM->location != Location::INLINE);
        auto knownSizeArrayCountedStructLT = globalState->getKnownSizeArrayWrapperStruct(
            knownSizeArrayMT->name);
        return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
      } else if (auto unknownSizeArrayMT =
          dynamic_cast<UnknownSizeArrayT *>(referenceM->referend)) {
        assert(referenceM->location != Location::INLINE);
        auto unknownSizeArrayCountedStructLT =
            globalState->getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT->name);
        return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
      } else if (auto structReferend =
          dynamic_cast<StructReferend *>(referenceM->referend)) {
        if (referenceM->location == Location::INLINE) {
          auto innerStructL = globalState->getInnerStruct(structReferend->fullName);
          return innerStructL;
        } else {
          auto countedStructL = globalState->getWrapperStruct(structReferend->fullName);
          return LLVMPointerType(countedStructL, 0);
        }
      } else if (auto interfaceReferend =
          dynamic_cast<InterfaceReferend *>(referenceM->referend)) {
        assert(referenceM->location != Location::INLINE);
        auto interfaceRefStructL =
            globalState->getInterfaceRefStruct(interfaceReferend->fullName);
        return interfaceRefStructL;
      } else if (dynamic_cast<Never*>(referenceM->referend)) {
        auto result = LLVMPointerType(makeNeverType(), 0);
        assert(LLVMTypeOf(globalState->neverPtr) == result);
        return result;
      } else {
        std::cerr << "Unimplemented type: " << typeid(*referenceM->referend).name() << std::endl;
        assert(false);
        return nullptr;
      }
    }
  }

private:
  DefaultPrimitives primitives;
};

#endif