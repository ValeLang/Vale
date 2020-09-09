#include <llvm-c/Core.h>
#include "ref.h"
#include "region/iregion.h"

Ref wrap(IRegion* region, Reference* refM, LLVMValueRef exprLE) {
  assert(LLVMTypeOf(exprLE) == region->translateType(refM));
  return Ref(refM, exprLE);
}

Ref wrap(IRegion* region, Reference* refM, WrapperPtrLE wrapperPtr) {
  assert(refM == wrapperPtr.refM);
  assert(LLVMTypeOf(wrapperPtr.refLE) == region->translateType(refM));
  return Ref(refM, wrapperPtr.refLE);
}

Ref wrap(IRegion* region, Reference* refM, WeakFatPtrLE weakFatPtrLE) {
  assert(refM == weakFatPtrLE.refM);
  assert(LLVMTypeOf(weakFatPtrLE.refLE) == region->translateType(refM));
  return Ref(refM, weakFatPtrLE.refLE);
}