#include <llvm-c/Core.h>
#include "ref.h"
#include "region/iregion.h"
#include "globalstate.h"

Ref wrap(IRegion* region, Reference* refM, LLVMValueRef exprLE) {
  assert(LLVMTypeOf(exprLE) == region->translateType(refM));
  return Ref(refM, exprLE);
}

Ref wrap(IRegion* region, Reference* refM, WrapperPtrLE wrapperPtr) {
  assert(refM == wrapperPtr.refM);
  assert(LLVMTypeOf(wrapperPtr.refLE) == region->translateType(refM));
  return Ref(refM, wrapperPtr.refLE);
}

Ref wrap(IRegion* region, Reference* refM, InterfaceFatPtrLE interfaceFatPtrLE) {
  assert(refM == interfaceFatPtrLE.refM);
  assert(LLVMTypeOf(interfaceFatPtrLE.refLE) == region->translateType(refM));
  return Ref(refM, interfaceFatPtrLE.refLE);
}

Ref wrap(IRegion* region, Reference* refM, WeakFatPtrLE weakFatPtrLE) {
  assert(refM == weakFatPtrLE.refM);
  assert(LLVMTypeOf(weakFatPtrLE.refLE) == region->translateType(refM));
  return Ref(refM, weakFatPtrLE.refLE);
}

ControlBlockPtrLE::ControlBlockPtrLE(
    GlobalState* globalState,
    Referend* referendM_,
    LLVMValueRef controlBlockPtrLE_)
  : referendM(referendM_),
    refLE(controlBlockPtrLE_) {
  assert(LLVMTypeOf(controlBlockPtrLE_) == LLVMPointerType(globalState->getControlBlockStruct(referendM), 0));
}

InterfaceFatPtrLE::InterfaceFatPtrLE(
    GlobalState* globalState,
    Reference* referenceM_,
    LLVMValueRef controlBlockPtrLE_)
    : refM(referenceM_),
      refLE(controlBlockPtrLE_) {
  auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(referenceM_->referend);
  assert(interfaceReferendM);
  assert(
      LLVMTypeOf(controlBlockPtrLE_) == globalState->getInterfaceRefStruct(interfaceReferendM->fullName));
}

WeakFatPtrLE::WeakFatPtrLE(
    GlobalState* globalState,
    Reference* referenceM_,
    LLVMValueRef ptrLE)
    : refM(referenceM_),
    refLE(ptrLE) {
  if (auto structReferendM = dynamic_cast<StructReferend*>(referenceM_->referend)) {
    assert(LLVMTypeOf(ptrLE) == globalState->getStructWeakRefStruct(structReferendM->fullName));
  } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(referenceM_->referend)) {
    assert(
        LLVMTypeOf(ptrLE) == globalState->weakVoidRefStructL ||
        LLVMTypeOf(ptrLE) == globalState->getInterfaceWeakRefStruct(interfaceReferendM->fullName));
  } else if (auto ksaT = dynamic_cast<KnownSizeArrayT*>(referenceM_->referend)) {
    assert(LLVMTypeOf(ptrLE) == globalState->getKnownSizeArrayWeakRefStruct(ksaT->name));
  } else if (auto usaT = dynamic_cast<UnknownSizeArrayT*>(referenceM_->referend)) {
    assert(LLVMTypeOf(ptrLE) == globalState->getUnknownSizeArrayWeakRefStruct(usaT->name));
  } else {
    assert(false);
  }
}
