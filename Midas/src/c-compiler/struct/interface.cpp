#include <iostream>
#include <function/expressions/shared/shared.h>
#include <function/expressions/shared/weaks.h>

#include "interface.h"

#include "translatetype.h"

void declareInterface(
    GlobalState* globalState,
    InterfaceDefinition* interfaceM) {

  auto interfaceRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), interfaceM->name->name.c_str());
  assert(globalState->interfaceRefStructs.count(interfaceM->name->name) == 0);
  globalState->interfaceRefStructs.emplace(interfaceM->name->name, interfaceRefStructL);

  auto interfaceTableStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (interfaceM->name->name + "itable").c_str());
  assert(globalState->interfaceTableStructs.count(interfaceM->name->name) == 0);
  globalState->interfaceTableStructs.emplace(interfaceM->name->name, interfaceTableStructL);

  auto interfaceWeakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (interfaceM->name->name + "w").c_str());
  assert(globalState->interfaceWeakRefStructs.count(interfaceM->name->name) == 0);
  globalState->interfaceWeakRefStructs.emplace(interfaceM->name->name, interfaceWeakRefStructL);
}

LLVMTypeRef translateInterfaceMethodToFunctionType(
    GlobalState* globalState,
    InterfaceMethod* method) {
  auto returnMT = method->prototype->returnType;
  auto paramsMT = method->prototype->params;
  auto returnLT = translateType(globalState, returnMT);
  auto paramsLT = translateTypes(globalState, paramsMT);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      switch (paramsMT[method->virtualParamIndex]->ownership) {
        case UnconvertedOwnership::BORROW:
        case UnconvertedOwnership::OWN:
        case UnconvertedOwnership::SHARE:
          paramsLT[method->virtualParamIndex] = LLVMPointerType(LLVMVoidType(), 0);
          break;
        case UnconvertedOwnership::WEAK:
          paramsLT[method->virtualParamIndex] = globalState->weakVoidRefStructL;
          break;
      }
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (paramsMT[method->virtualParamIndex]->ownership) {
        case UnconvertedOwnership::OWN:
        case UnconvertedOwnership::SHARE:
          paramsLT[method->virtualParamIndex] = LLVMPointerType(LLVMVoidType(), 0);
          break;
        case UnconvertedOwnership::BORROW:
        case UnconvertedOwnership::WEAK:
          paramsLT[method->virtualParamIndex] = globalState->weakVoidRefStructL;
          break;
      }
      break;
    }
    default:
      assert(false);
  }

  return LLVMFunctionType(returnLT, paramsLT.data(), paramsLT.size(), false);
}

void translateInterface(
    GlobalState* globalState,
    InterfaceDefinition* interfaceM) {
  LLVMTypeRef itableStruct =
      globalState->getInterfaceTableStruct(interfaceM->name);
  std::vector<LLVMTypeRef> interfaceMethodTypesL;
  for (int i = 0; i < interfaceM->methods.size(); i++) {
    interfaceMethodTypesL.push_back(
        LLVMPointerType(
            translateInterfaceMethodToFunctionType(
                globalState, interfaceM->methods[i]),
            0));
  }
  LLVMStructSetBody(
      itableStruct, interfaceMethodTypesL.data(), interfaceMethodTypesL.size(), false);

  LLVMTypeRef refStructL = globalState->getInterfaceRefStruct(interfaceM->name);
  std::vector<LLVMTypeRef> refStructMemberTypesL;

  // this points to the control block.
  // It makes it easier to increment and decrement ref counts.
  if (interfaceM->mutability == Mutability::MUTABLE) {
    if (getEffectiveWeakability(globalState, interfaceM) == Weakability::WEAKABLE) {
      refStructMemberTypesL.push_back(LLVMPointerType(globalState->mutWeakableControlBlockStructL, 0));
    } else {
      refStructMemberTypesL.push_back(LLVMPointerType(globalState->mutNonWeakableControlBlockStructL, 0));
    }
  } else if (interfaceM->mutability == Mutability::IMMUTABLE) {
    refStructMemberTypesL.push_back(LLVMPointerType(globalState->immControlBlockStructL, 0));
  } else assert(false);


  refStructMemberTypesL.push_back(LLVMPointerType(itableStruct, 0));
  LLVMStructSetBody(
      refStructL,
      refStructMemberTypesL.data(),
      refStructMemberTypesL.size(),
      false);

  auto interfaceWeakRefStructL = globalState->getInterfaceWeakRefStruct(interfaceM->name);
  makeInterfaceWeakRefStruct(globalState, interfaceWeakRefStructL, refStructL);
}
