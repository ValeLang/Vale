#ifndef FUNCTION_EXPRESSIONS_SHARED_REF_H_
#define FUNCTION_EXPRESSIONS_SHARED_REF_H_

#include <llvm-c/Core.h>
#include "metal/types.h"
#include "afl.h"

class FunctionState;
class GlobalState;
class IRegion;


struct WrapperPtrLE {
  Reference* const refM;
  // TODO rename to ptrLE
  LLVMValueRef const refLE;

private:
  WrapperPtrLE(Reference* refM_, LLVMValueRef refLE_)
      : refM(refM_), refLE(refLE_) { }

  friend struct WrapperPtrLEMaker;
};

struct WrapperPtrLEMaker {
  WrapperPtrLEMaker(std::function<LLVMTypeRef(Reference*)> getWrapperStruct_)
    : getWrapperStruct(getWrapperStruct_) {}

  WrapperPtrLE make(Reference* referenceM_, LLVMValueRef controlBlockPtrLE_) {
    assert(LLVMTypeOf(controlBlockPtrLE_) == LLVMPointerType(getWrapperStruct(referenceM_), 0));
    return WrapperPtrLE(referenceM_, controlBlockPtrLE_);
  }

private:
  std::function<LLVMTypeRef(Reference*)> getWrapperStruct;
};



struct ControlBlockPtrLE {
  Referend* const referendM;
  // TODO rename to ptrLE
  LLVMValueRef const refLE;

private:
  ControlBlockPtrLE(Referend* refM_, LLVMValueRef refLE_)
    : referendM(refM_), refLE(refLE_) { }

  friend struct ControlBlockPtrLEMaker;
};

struct ControlBlockPtrLEMaker {
  ControlBlockPtrLEMaker(std::function<LLVMTypeRef(Referend*)> getControlBlockStruct_)
    : getControlBlockStruct(getControlBlockStruct_) {}

  ControlBlockPtrLE make(Referend* referendM_, LLVMValueRef controlBlockPtrLE_) {
    auto actualTypeOfControlBlockPtrLE = LLVMTypeOf(controlBlockPtrLE_);
    auto expectedControlBlockStructL = getControlBlockStruct(referendM_);
    auto expectedControlBlockStructPtrL = LLVMPointerType(expectedControlBlockStructL, 0);
    assert(actualTypeOfControlBlockPtrLE == expectedControlBlockStructPtrL);
    return ControlBlockPtrLE(referendM_, controlBlockPtrLE_);
  }

private:
  std::function<LLVMTypeRef(Referend*)> getControlBlockStruct;
};




struct InterfaceFatPtrLE {
  Reference* const refM;
  // TODO rename to ptrLE
  LLVMValueRef const refLE;

private:
  InterfaceFatPtrLE(Reference* refM_, LLVMValueRef refLE_)
      : refM(refM_), refLE(refLE_) { }

  friend struct InterfaceFatPtrLEMaker;
};

struct InterfaceFatPtrLEMaker {
  InterfaceFatPtrLEMaker(std::function<LLVMTypeRef(InterfaceReferend*)> getInterfaceFatPtrStruct_)
    : getInterfaceFatPtrStruct(getInterfaceFatPtrStruct_) {}

  InterfaceFatPtrLE make(Reference* referenceM_, LLVMValueRef controlBlockPtrLE_) {
    auto interfaceReferenceM = dynamic_cast<InterfaceReferend*>(referenceM_->referend);
    assert(interfaceReferenceM);
    assert(
        LLVMTypeOf(controlBlockPtrLE_) == getInterfaceFatPtrStruct(interfaceReferenceM));
    return InterfaceFatPtrLE(referenceM_, controlBlockPtrLE_);
  }

private:
  std::function<LLVMTypeRef(InterfaceReferend*)> getInterfaceFatPtrStruct;
};




struct WeakFatPtrLE {
  Reference* const refM;
  // TODO rename to ptrLE
  LLVMValueRef const refLE;

  WeakFatPtrLE(Reference* refM_, LLVMValueRef refLE_)
      : refM(refM_), refLE(refLE_) { }
};


// An LLVM register, which contains a reference.
struct Ref {
  Ref(Reference* refM_, LLVMValueRef refLE_) : refM(refM_), refLE(refLE_) {}

  void assertOwnership(Ownership ownership) {
    assert(refM->ownership == ownership);
  }

private:
  // This is private to keep us from just grabbing this to hand in to checkValidReference.
  // We should instead always pipe through the code the actual expected type.
  Reference* const refM;
  LLVMValueRef const refLE;

  friend std::tuple<Reference*, LLVMValueRef> megaGetRefInnardsForChecking(Ref ref);
  friend std::tuple<Reference*, LLVMValueRef> hgmGetRefInnardsForChecking(Ref ref);
  friend std::tuple<Reference*, LLVMValueRef> lgtGetRefInnardsForChecking(Ref ref);
  friend std::tuple<Reference*, LLVMValueRef> wrcGetRefInnardsForChecking(Ref ref);

  friend void buildPrint(
      GlobalState* globalState,
      LLVMBuilderRef builder,
      Ref ref);
};

Ref wrap(IRegion* region, Reference* refM, LLVMValueRef exprLE);
Ref wrap(IRegion* region, Reference* refM, WrapperPtrLE exprLE);
Ref wrap(IRegion* region, Reference* refM, InterfaceFatPtrLE exprLE);
Ref wrap(IRegion* region, Reference* refM, WeakFatPtrLE exprLE);


#endif
