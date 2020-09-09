#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <function/expressions/shared/weaks.h>
#include <function/expressions/shared/controlblock.h>
#include <region/common/fatweaks/fatweaks.h>
#include "common.h"

LLVMValueRef upcastThinPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    WrapperPtrLE sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  assert(sourceStructTypeM->location != Location::INLINE);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      assert(sourceStructTypeM->ownership == Ownership::SHARE ||
          sourceStructTypeM->ownership == Ownership::OWN ||
          sourceStructTypeM->ownership == Ownership::BORROW);
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      assert(sourceStructTypeM->ownership == Ownership::SHARE ||
          sourceStructTypeM->ownership == Ownership::OWN);
      break;
    }
    default:
      assert(false);
  }
  ControlBlockPtrLE controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, sourceRefLE);
  auto interfaceRefLE =
      makeInterfaceRefStruct(
          globalState, functionState, builder, sourceStructReferendM, targetInterfaceReferendM,
          controlBlockPtrLE);
  return interfaceRefLE;
}

LLVMValueRef upcastWeakFatPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    WeakFatPtrLE sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  assert(sourceStructTypeM->location != Location::INLINE);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      assert(sourceStructTypeM->ownership == Ownership::WEAK);
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      assert(sourceStructTypeM->ownership == Ownership::BORROW ||
          sourceStructTypeM->ownership == Ownership::WEAK);
      break;
    }
  }

  return functionState->defaultRegion->upcastWeak(
      functionState,
      builder,
      sourceRefLE,
      sourceStructReferendM,
      sourceStructTypeM,
      targetInterfaceReferendM,
      targetInterfaceTypeM);
}

LLVMTypeRef translateReferenceSimple(GlobalState* globalState, Referend* referend) {
  if (auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT *>(referend)) {
    assert(false); // impl
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT *>(referend)) {
    auto unknownSizeArrayCountedStructLT =
        globalState->getUnknownSizeArrayWrapperStruct(usaMT->name);
    return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
  } else if (auto structReferend = dynamic_cast<StructReferend *>(referend)) {
    auto countedStructL = globalState->getWrapperStruct(structReferend->fullName);
    return LLVMPointerType(countedStructL, 0);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend *>(referend)) {
    auto interfaceRefStructL =
        globalState->getInterfaceRefStruct(interfaceReferend->fullName);
    return interfaceRefStructL;
  } else {
    std::cerr << "Unimplemented type: " << typeid(*referend).name() << std::endl;
    assert(false);
    return nullptr;
  }
}

LLVMTypeRef translateWeakReference(GlobalState* globalState, Referend* referend) {
  if (auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT *>(referend)) {
    assert(false); // impl
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT *>(referend)) {
    return globalState->getUnknownSizeArrayWeakRefStruct(usaMT->name);
  } else if (auto structReferend = dynamic_cast<StructReferend *>(referend)) {
    return globalState->getStructWeakRefStruct(structReferend->fullName);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend *>(referend)) {
    return globalState->getInterfaceWeakRefStruct(interfaceReferend->fullName);
  } else {
    assert(false);
  }
}
