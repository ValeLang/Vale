#ifndef REGION_COMMON_LINEAR_LINEARSTRUCTS_H_
#define REGION_COMMON_LINEAR_LINEARSTRUCTS_H_

#include <algorithm>
#include <llvm-c/Types.h>
#include "../../globalstate.h"
#include <iostream>
#include "../common/primitives.h"
#include "../../function/expressions/shared/afl.h"
#include "../../function/function.h"
#include "../common/defaultlayout/structs.h"

class LinearStructs {
public:
  LinearStructs(GlobalState* globalState_);

  LLVMTypeRef getStructStruct(StructKind* structKind);
  LLVMTypeRef getStaticSizedArrayStruct(StaticSizedArrayT* ssaMT);
  LLVMTypeRef getRuntimeSizedArrayStruct(RuntimeSizedArrayT* rsaMT);
  LLVMTypeRef getInterfaceRefStruct(InterfaceKind* interfaceKind);
  LLVMTypeRef getStringStruct();

  void defineStruct(
      StructKind* struuct,
      std::vector<LLVMTypeRef> membersLT) ;
  void declareStruct(StructKind* structM);
  void declareEdge(StructKind* structKind, InterfaceKind* interfaceKind);
  void defineEdge(
      Edge* edge,
      std::vector<LLVMTypeRef> interfaceFunctionsLT,
      std::vector<ValeFuncPtrLE> functions);
  void declareInterface(InterfaceKind* interface);
  void defineInterface(InterfaceKind* interface);
  void declareStaticSizedArray(
      StaticSizedArrayT* staticSizedArrayMT);
  void declareRuntimeSizedArray(
      RuntimeSizedArrayT* runtimeSizedArrayMT);
  void defineRuntimeSizedArray(
      RuntimeSizedArrayT* runtimeSizedArrayMT,
      LLVMTypeRef elementLT);
  void defineStaticSizedArray(
      StaticSizedArrayT* staticSizedArrayMT,
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
  LLVMValueRef getRuntimeSizedArrayElementsPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      RuntimeSizedArrayT* rsaMT,
      LLVMValueRef ptrLE);
  LLVMValueRef getStaticSizedArrayElementsPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      StaticSizedArrayT* ssaMT,
      LLVMValueRef ptrLE);
//  LLVMValueRef getStringLen(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef ptrLE);
  LLVMValueRef getVoidPtrFromInterfacePtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      InterfaceFatPtrLE virtualArgLE);

  int getEdgeNumber(InterfaceKind* interfaceKind, StructKind* structKind) {
    auto structs = orderedStructsByInterface.find(interfaceKind)->second;
    auto index = std::find(structs.begin(), structs.end(), structKind) - structs.begin();
    assert(index < structs.size());
    return index;
  }
  std::vector<StructKind*> getOrderedSubstructs(InterfaceKind* interfaceKind) {
    auto iter = orderedStructsByInterface.find(interfaceKind);
    assert(iter != orderedStructsByInterface.end());
    return iter->second;
  }

private:
  GlobalState* globalState = nullptr;

  LLVMTypeRef stringStructLT = nullptr;
  std::unordered_map<InterfaceKind*, LLVMTypeRef, AddressHasher<InterfaceKind*>> interfaceRefStructsL;
  std::unordered_map<StructKind*, LLVMTypeRef, AddressHasher<StructKind*>> structStructsL;
  std::unordered_map<StaticSizedArrayT*, LLVMTypeRef, AddressHasher<StaticSizedArrayT*>> staticSizedArrayStructsL;
  std::unordered_map<RuntimeSizedArrayT*, LLVMTypeRef, AddressHasher<RuntimeSizedArrayT*>> runtimeSizedArrayStructsL;

  // The position in the vector is the integer that will be the tag for which actual substruct
  // is being pointed at by an interface ref.
  std::unordered_map<InterfaceKind*, std::vector<StructKind*>, AddressHasher<InterfaceKind*>> orderedStructsByInterface;
};
#endif
