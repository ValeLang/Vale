#ifndef FUNCTION_EXPRESSIONS_SHARED_REF_H_
#define FUNCTION_EXPRESSIONS_SHARED_REF_H_

#include <llvm-c/Core.h>
#include "../../../metal/types.h"
#include "afl.h"

class FunctionState;
class GlobalState;
class IRegion;


struct FuncPtrLE {
  LLVMValueRef ptrLE;
  LLVMTypeRef funcLT;

  FuncPtrLE() : funcLT(nullptr), ptrLE(nullptr) { }

  FuncPtrLE(LLVMTypeRef funcLT_, LLVMValueRef ptrLE_)
    : funcLT(funcLT_),
      ptrLE(ptrLE_) {
    assert(LLVMTypeOf(ptrLE) == LLVMPointerType(funcLT, 0));
  }

  LLVMValueRef call(LLVMBuilderRef builder, const std::vector<LLVMValueRef>& argsLE, const char* name) const {
    return LLVMBuildCall2(
        builder, funcLT, ptrLE, const_cast<LLVMValueRef*>(argsLE.data()), argsLE.size(), name);
  }
};

//
//struct PtrLE {
//  LLVMTypeRef pointeeLT;
//  LLVMValueRef ptrLE;
//
//  PtrLE(LLVMTypeRef pointeeLT_, LLVMValueRef ptrLE_)
//      : pointeeLT(pointeeLT_), ptrLE(ptrLE_) {
//    assert(LLVMTypeOf(ptrLE) == LLVMPointerType(pointeeLT, 0));
//  }
//};


struct WrapperPtrLE {
  Reference* const refM;
  LLVMTypeRef wrapperStructLT;
  // TODO rename to ptrLE
  LLVMValueRef const refLE;

  WrapperPtrLE(Reference* refM_, LLVMTypeRef wrapperStructLT_, LLVMValueRef refLE_)
      : refM(refM_), wrapperStructLT(wrapperStructLT_), refLE(refLE_) {
    assert(LLVMTypeOf(refLE) == LLVMPointerType(wrapperStructLT, 0));
  }
};


struct ControlBlockPtrLE {
  Kind* const kindM;
  // TODO rename to ptrLE
  LLVMValueRef const refLE;

  ControlBlockPtrLE(Kind* refM_, LLVMValueRef refLE_)
    : kindM(refM_), refLE(refLE_) { }
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
  Reference* refM;

  LLVMValueRef refLE;

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

LLVMValueRef checkValidInternalReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool expectLive,
    Reference* refM,
    Ref ref);


#endif
