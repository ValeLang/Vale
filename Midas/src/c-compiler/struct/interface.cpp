#include <iostream>

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
  auto returnLT = translateType(globalState, method->prototype->returnType);
  auto paramsLT = translateTypes(globalState, method->prototype->params);
  paramsLT[method->virtualParamIndex] = LLVMPointerType(LLVMVoidType(), 0);
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
  // The object ptr is the 0th element, so we don't have to add and subtract 1
  // whenever we want to affect its ref count.
  // It points to the any struct, which is a wrapper around a ref count.
  // It makes it easier to increment and decrement ref counts.
  if (interfaceM->weakable) {
    refStructMemberTypesL.push_back(LLVMPointerType(globalState->weakableControlBlockStructL, 0));
  } else {
    refStructMemberTypesL.push_back(LLVMPointerType(globalState->nonWeakableControlBlockStructL, 0));
  }
  refStructMemberTypesL.push_back(LLVMPointerType(itableStruct, 0));
  LLVMStructSetBody(
      refStructL,
      refStructMemberTypesL.data(),
      refStructMemberTypesL.size(),
      false);

  auto interfaceWeakRefStructL = globalState->getInterfaceWeakRefStruct(interfaceM->name);
  std::vector<LLVMTypeRef> interfaceWeakRefStructMemberTypesL;
  interfaceWeakRefStructMemberTypesL.push_back(LLVMPointerType(LLVMInt64Type(), 0));
  interfaceWeakRefStructMemberTypesL.push_back(refStructL);
  LLVMStructSetBody(interfaceWeakRefStructL, interfaceWeakRefStructMemberTypesL.data(), interfaceWeakRefStructMemberTypesL.size(), false);
}
