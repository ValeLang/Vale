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
  ControlBlockPtrLE make(Referend* referendM_, LLVMValueRef controlBlockPtrLE_) {
    assert(LLVMTypeOf(controlBlockPtrLE_) == LLVMPointerType(getControlBlockStruct(referendM_), 0));
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
  InterfaceFatPtrLE make(Reference* referenceM_, LLVMValueRef controlBlockPtrLE_) {
    auto interfaceReferenceM = dynamic_cast<InterfaceReferend*>(referenceM_->referend);
    assert(interfaceReferenceM);
    assert(
        LLVMTypeOf(controlBlockPtrLE_) == getInterfaceFatPtrStruct(interfaceReferenceM));
    return InterfaceFatPtrLE(referenceM_, controlBlockPtrLE_);
  }

private:
  std::function<LLVMTypeRef(Referend*)> getInterfaceFatPtrStruct;
};




struct WeakFatPtrLE {
  Reference* const refM;
  // TODO rename to ptrLE
  LLVMValueRef const refLE;

private:
  WeakFatPtrLE(Reference* refM_, LLVMValueRef refLE_)
      : refM(refM_), refLE(refLE_) { }

  friend struct WeakFatPtrLEMaker;
};

struct WeakFatPtrLEMaker {
  WeakFatPtrLE make(Reference* referenceM_, LLVMValueRef ptrLE) {
    if (auto structReferendM = dynamic_cast<StructReferend*>(referenceM_->referend)) {
      assert(LLVMTypeOf(ptrLE) == getStructWeakRefStruct(structReferendM));
    } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(referenceM_->referend)) {
      assert(
          LLVMTypeOf(ptrLE) == getWeakVoidRefStruct() ||
              LLVMTypeOf(ptrLE) == getInterfaceWeakRefStruct(interfaceReferendM));
    } else if (auto ksaT = dynamic_cast<KnownSizeArrayT*>(referenceM_->referend)) {
      assert(LLVMTypeOf(ptrLE) == getKnownSizeArrayWeakRefStruct(ksaT));
    } else if (auto usaT = dynamic_cast<UnknownSizeArrayT*>(referenceM_->referend)) {
      assert(LLVMTypeOf(ptrLE) == getUnknownSizeArrayWeakRefStruct(usaT));
    } else {
      assert(false);
    }
    return WeakFatPtrLE(referenceM_, ptrLE);
  }

private:
  std::function<LLVMTypeRef()> getWeakVoidRefStruct;
  std::function<LLVMTypeRef(StructReferend*)> getStructWeakRefStruct;
  std::function<LLVMTypeRef(InterfaceReferend*)> getInterfaceWeakRefStruct;
  std::function<LLVMTypeRef(KnownSizeArrayT*)> getKnownSizeArrayWeakRefStruct;
  std::function<LLVMTypeRef(UnknownSizeArrayT*)> getUnknownSizeArrayWeakRefStruct;
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

  friend LLVMValueRef checkValidReference(
      AreaAndFileAndLine checkerAFL,
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref ref);

  friend void buildPrint(
      GlobalState* globalState,
      LLVMBuilderRef builder,
      Ref ref);

  friend void buildCheckWeakRef(
      AreaAndFileAndLine checkerAFL,
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef);
};

Ref wrap(IRegion* region, Reference* refM, LLVMValueRef exprLE);
Ref wrap(IRegion* region, Reference* refM, WrapperPtrLE exprLE);
Ref wrap(IRegion* region, Reference* refM, InterfaceFatPtrLE exprLE);
Ref wrap(IRegion* region, Reference* refM, WeakFatPtrLE exprLE);


#endif
