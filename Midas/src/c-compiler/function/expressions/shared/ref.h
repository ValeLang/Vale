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

  WrapperPtrLE(Reference* refM_, LLVMValueRef refLE_)
      : refM(refM_), refLE(refLE_) { }
};


struct ControlBlockPtrLE {
  Referend* const referendM;
  // TODO rename to ptrLE
  LLVMValueRef const refLE;

  ControlBlockPtrLE(Referend* refM_, LLVMValueRef refLE_)
    : referendM(refM_), refLE(refLE_) { }
};



struct InterfaceFatPtrLE {
  Reference* const refM;
  // TODO rename to ptrLE
  LLVMValueRef const refLE;

  InterfaceFatPtrLE(Reference* refM_, LLVMValueRef refLE_)
      : refM(refM_), refLE(refLE_) { }
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
