#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <function/expressions/shared/weaks.h>
#include <region/common/controlblock.h>
#include <region/common/fatweaks/fatweaks.h>
#include <function/expressions/shared/members.h>
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

LLVMTypeRef translateReferenceSimple(GlobalState* globalState, Referend* referend) {
  if (auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT *>(referend)) {
    assert(false); // impl
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT *>(referend)) {
    auto unknownSizeArrayCountedStructLT =
        globalState->getReferendStructsSource()->getUnknownSizeArrayWrapperStruct(usaMT);
    return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
  } else if (auto structReferend = dynamic_cast<StructReferend *>(referend)) {
    auto countedStructL = globalState->getReferendStructsSource()->getWrapperStruct(structReferend);
    return LLVMPointerType(countedStructL, 0);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend *>(referend)) {
    auto interfaceRefStructL =
        globalState->getReferendStructsSource()->getInterfaceRefStruct(interfaceReferend);
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
    return globalState->getWeakRefStructsSource()->getUnknownSizeArrayWeakRefStruct(usaMT);
  } else if (auto structReferend = dynamic_cast<StructReferend *>(referend)) {
    return globalState->getWeakRefStructsSource()->getStructWeakRefStruct(structReferend);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend *>(referend)) {
    return globalState->getWeakRefStructsSource()->getInterfaceWeakRefStruct(interfaceReferend);
  } else {
    assert(false);
  }
}


LLVMValueRef getStructContentsPtrNormal(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref refLE) {
  auto wrapperPtrLE =
      functionState->defaultRegion->makeWrapperPtr(refM,
          globalState->region->checkValidReference(FL(), functionState, builder, refM, refLE));
  return getStructContentsPtr(builder, wrapperPtrLE);
}

LLVMValueRef getStructContentsPtrForce(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref refLE) {
  auto wrapperPtrLE = lockWeakRef(FL(), globalState, functionState, builder, refM, refLE);
  return getStructContentsPtr(builder, wrapperPtrLE);
}

Ref loadInnerInnerStructMember(
    IRegion* region,
    LLVMBuilderRef builder,
    LLVMValueRef innerStructPtrLE,
    int memberIndex,
    Reference* expectedType,
    std::string memberName) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);

  auto result =
      LLVMBuildLoad(
          builder,
          LLVMBuildStructGEP(
              builder, innerStructPtrLE, memberIndex, memberName.c_str()),
          memberName.c_str());
  return wrap(region, expectedType, result);
}

void storeInnerInnerStructMember(
    LLVMBuilderRef builder, LLVMValueRef innerStructPtrLE, int memberIndex, std::string memberName, LLVMValueRef newValueLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);
  LLVMBuildStore(
      builder,
      newValueLE,
      LLVMBuildStructGEP(
          builder, innerStructPtrLE, memberIndex, memberName.c_str()));
}

LLVMValueRef getItablePtrFromInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE) {
  buildFlare(FL(), globalState, functionState, builder);
  assert(LLVMTypeOf(virtualArgLE.refLE) == functionState->defaultRegion->translateType(virtualParamMT));
  return getTablePtrFromInterfaceRef(builder, virtualArgLE);
}

LLVMValueRef getVoidPtrFromInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE) {
  assert(LLVMTypeOf(virtualArgLE.refLE) == functionState->defaultRegion->translateType(virtualParamMT));
  return LLVMBuildPointerCast(
      builder,
      getControlBlockPtr(globalState, builder, virtualArgLE).refLE,
      LLVMPointerType(LLVMVoidType(), 0),
      "objAsVoidPtr");
}

