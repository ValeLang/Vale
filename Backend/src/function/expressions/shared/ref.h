#ifndef FUNCTION_EXPRESSIONS_SHARED_REF_H_
#define FUNCTION_EXPRESSIONS_SHARED_REF_H_

#include <llvm-c/Core.h>
#include "../../../metal/types.h"
#include "afl.h"

class FunctionState;
class GlobalState;
class IRegion;
struct KindStructs;


// Perhaps we should switch to a structure that looks like this:
// struct ArrayType : IType {
//   Ref* refM;
//   IType* elementType;
//   LLVMTypeRef llvmType;
// }
// the LLVM type is no longer recursive since pointers are opaque.
// Then we can have a PtrLE-like thing that contains that IType and an LLVMValueRef.
// Perhaps we dont even need that IType* elementType? probably do. must think on it.

// A "Raw function" is one that doesn't have a restrict nextgen ptr as the first parameter.
// As opposed to a Vale function which does.
struct RawFuncPtrLE {
  LLVMValueRef ptrLE;
  LLVMTypeRef funcLT;

  RawFuncPtrLE() : funcLT(nullptr), ptrLE(nullptr) { }

  RawFuncPtrLE(LLVMTypeRef funcLT_, LLVMValueRef ptrLE_)
      : funcLT(funcLT_),
        ptrLE(ptrLE_) {
    assert(LLVMTypeOf(ptrLE) == LLVMPointerType(funcLT, 0));
  }

  LLVMValueRef call(LLVMBuilderRef builder, const std::vector<LLVMValueRef>& argsLE, const char* name) const {
    return LLVMBuildCall2(
        builder, funcLT, ptrLE, const_cast<LLVMValueRef*>(argsLE.data()), argsLE.size(), name);
  }

  LLVMValueRef getRawArg(int index) {
    return LLVMGetParam(ptrLE, index);
  }
};

// A "Vale function" is one that has a restrict nextgen ptr as the first parameter.
// As opposed to a raw function which doesnt.
struct ValeFuncPtrLE {
  RawFuncPtrLE inner;

  ValeFuncPtrLE() { }

  explicit ValeFuncPtrLE(RawFuncPtrLE inner_)
      : inner(inner_) { }

  LLVMValueRef call(LLVMBuilderRef builder, LLVMValueRef nextGenPtrLE, std::vector<LLVMValueRef> argsLE, const char* name) const {
    argsLE.insert(argsLE.begin(), nextGenPtrLE);
    return inner.call(builder, argsLE, name);
  }

  LLVMValueRef getValeParam(int index) {
    // The 0th argument is always the next gen ptr.
    return inner.getRawArg(index + 1);
  }

//  LLVMValueRef getNextGenPtrArg() {
//    // The 0th argument is always the next gen ptr.
//    return inner.getRawArg(0);
//  }
};

// A type-system token to assure certain functions that we indeed checked the bounds of an array
// we're about to access.
struct InBoundsLE {
  LLVMValueRef refLE;
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
  LLVMTypeRef structLT;
  // TODO rename to ptrLE
  LLVMValueRef const refLE;

  ControlBlockPtrLE(Kind* refM_, LLVMTypeRef structLT_, LLVMValueRef refLE_)
    : kindM(refM_), structLT(structLT_), refLE(refLE_) {
    assert(LLVMTypeOf(refLE) == LLVMPointerType(structLT, 0));
  }
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

// Represents the result of a previous instruction.
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

  friend void buildPrint(GlobalState* globalState, LLVMBuilderRef builder, Ref ref);
  friend void buildPrintToStderr(GlobalState* globalState, LLVMBuilderRef builder, Ref ref);
};

// When we load something from an array, for example an owning reference,
// we still need to alias it to a constraint reference. This wrapper serves
// as a reminder that we need to do that.
struct LoadResult {
public:
  explicit LoadResult(Ref ref) : ref(ref) {}

  // This method is used when we intended to move the result, so no transformation
  // or aliasing is needed.
  Ref move() { return ref; }

  // This is just a getter for the ref for the methods that actually implement the
  // aliasing. It should ONLY be used by them.
  Ref extractForAliasingInternals() { return ref; }

private:
  Ref ref;
};

// A LLVM value that we're sure is alive right now, and safe* to dereference.
// (* Safe could mean NULL if the OS protects us from that.)
// This isn't necessarily the same LLVMValueRef as would be in a Ref. It's up to the particular
// region to decide what would be in here. It's probably either a WrapperPtrLE or a raw pointer.
struct LiveRef {
  Reference* const refM;
  // TODO rename to ptrLE
  LLVMValueRef const refLE;

  LiveRef(Reference* refM_, LLVMValueRef refLE_)
  : refM(refM_), refLE(refLE_) { }
};

// DO NOT SUBMIT rename to toRef
Ref wrap(IRegion* region, Reference* refM, LLVMValueRef exprLE);
Ref wrap(IRegion* region, Reference* refM, WrapperPtrLE exprLE);
Ref wrap(IRegion* region, Reference* refM, InterfaceFatPtrLE exprLE);
Ref wrap(IRegion* region, Reference* refM, WeakFatPtrLE exprLE);
Ref wrap(GlobalState* globalState, Reference* refM, LiveRef exprLE);


WrapperPtrLE toWrapperPtr(FunctionState* functionState, LLVMBuilderRef builder, KindStructs* kindStructs, Reference* refMT, LiveRef liveRef);

LiveRef toLiveRef(WrapperPtrLE wrapperPtrLE);
LiveRef toLiveRef(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref regionInstanceRef,
    Reference* refM,
    LLVMValueRef untrustedRefLE);
LiveRef toLiveRef(AreaAndFileAndLine checkerAFL, GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, Ref regionInstanceRef, Reference* refM, Ref ref, bool knownLive);



LLVMValueRef checkValidInternalReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool expectLive,
    Reference* refM,
    Ref ref);


#endif
