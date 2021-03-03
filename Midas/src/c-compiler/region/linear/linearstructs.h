#ifndef REGION_COMMON_LINEAR_LINEARSTRUCTS_H_
#define REGION_COMMON_LINEAR_LINEARSTRUCTS_H_

#include <algorithm>
#include <llvm-c/Types.h>
#include <globalstate.h>
#include <iostream>
#include <region/common/primitives.h>
#include <function/expressions/shared/afl.h>
#include <function/function.h>
#include <region/common/defaultlayout/structs.h>

class LinearStructs {
public:
  LinearStructs(GlobalState* globalState_);

  LLVMTypeRef getStructStruct(StructReferend* structReferend);
  LLVMTypeRef getKnownSizeArrayStruct(KnownSizeArrayT* ksaMT);
  LLVMTypeRef getUnknownSizeArrayStruct(UnknownSizeArrayT* usaMT);
  LLVMTypeRef getInterfaceRefStruct(InterfaceReferend* interfaceReferend);
  LLVMTypeRef getStringStruct();

  void defineStruct(
      StructReferend* struuct,
      std::vector<LLVMTypeRef> membersLT) ;
  void declareStruct(StructReferend* structM);
  void declareEdge(StructReferend* structReferend, InterfaceReferend* interfaceReferend);
  void defineEdge(
      Edge* edge,
      std::vector<LLVMTypeRef> interfaceFunctionsLT,
      std::vector<LLVMValueRef> functions);
  void declareInterface(InterfaceReferend* interface);
  void defineInterface(InterfaceReferend* interface);
  void declareKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT);
  void declareUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT);
  void defineUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT,
      LLVMTypeRef elementLT);
  void defineKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT,
      int size,
      LLVMTypeRef elementLT);
  InterfaceFatPtrLE makeInterfaceFatPtr(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM_,
      LLVMValueRef ptrLE);

  LLVMValueRef getStringBytesPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef ptrLE);
  LLVMValueRef getUnknownSizeArrayElementsPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef ptrLE);
  LLVMValueRef getKnownSizeArrayElementsPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef ptrLE);
  LLVMValueRef getStringLen(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef ptrLE);
  LLVMValueRef getVoidPtrFromInterfacePtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      InterfaceFatPtrLE virtualArgLE);

  int getEdgeNumber(InterfaceReferend* interfaceReferend, StructReferend* structReferend) {
    auto structs = orderedStructsByInterface.find(interfaceReferend)->second;
    auto index = std::find(structs.begin(), structs.end(), structReferend) - structs.begin();
    assert(index < structs.size());
    return index;
  }
  std::vector<StructReferend*> getOrderedSubstructs(InterfaceReferend* interfaceReferend) {
    auto iter = orderedStructsByInterface.find(interfaceReferend);
    assert(iter != orderedStructsByInterface.end());
    return iter->second;
  }

private:
  GlobalState* globalState = nullptr;

  LLVMTypeRef stringStructLT = nullptr;
  std::unordered_map<InterfaceReferend*, LLVMTypeRef, AddressHasher<InterfaceReferend*>> interfaceRefStructsL;
  std::unordered_map<StructReferend*, LLVMTypeRef, AddressHasher<StructReferend*>> structStructsL;
  std::unordered_map<KnownSizeArrayT*, LLVMTypeRef, AddressHasher<KnownSizeArrayT*>> knownSizeArrayStructsL;
  std::unordered_map<UnknownSizeArrayT*, LLVMTypeRef, AddressHasher<UnknownSizeArrayT*>> unknownSizeArrayStructsL;

  // The position in the vector is the integer that will be the tag for which actual substruct
  // is being pointed at by an interface ref.
  std::unordered_map<InterfaceReferend*, std::vector<StructReferend*>, AddressHasher<InterfaceReferend*>> orderedStructsByInterface;
};
#endif
