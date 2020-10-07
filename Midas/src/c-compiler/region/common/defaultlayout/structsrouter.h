#ifndef REGION_COMMON_DEFAULTLAYOUT_STRUCTSROUTER_H_
#define REGION_COMMON_DEFAULTLAYOUT_STRUCTSROUTER_H_

#include <globalstate.h>
#include "structs.h"

using GetReferendStructsSource = std::function<IReferendStructsSource*(Referend*)>;
using GetWeakRefStructsSource = std::function<IWeakRefStructsSource*(Referend*)>;

// This is a class that wraps three Structses into one, and routes calls to them based on the
// referend's Mutability and Weakability.
class ReferendStructsRouter : public IReferendStructsSource {
public:
  ReferendStructsRouter(
      GlobalState* globalState,
      GetReferendStructsSource getReferendStructsSource_);

  ControlBlock* getControlBlock(Referend* referend) override;

  LLVMTypeRef getInnerStruct(StructReferend* structReferend) override;
  LLVMTypeRef getWrapperStruct(StructReferend* structReferend) override;
  LLVMTypeRef getKnownSizeArrayWrapperStruct(KnownSizeArrayT* ksaMT) override;
  LLVMTypeRef getUnknownSizeArrayWrapperStruct(UnknownSizeArrayT* usaMT) override;
  LLVMTypeRef getInterfaceRefStruct(InterfaceReferend* interfaceReferend) override;
  LLVMTypeRef getInterfaceTableStruct(InterfaceReferend* interfaceReferend) override;
  LLVMTypeRef getStringWrapperStruct() override;

  void translateStruct(StructDefinition* structM, std::vector<LLVMTypeRef> membersLT) override;
  void declareStruct(StructDefinition* structM) override;
  void declareEdge(Edge* edge) override;
  void translateEdge(
      Edge* edge,
      std::vector<LLVMTypeRef> interfaceFunctionsLT,
      std::vector<LLVMValueRef> functions) override;
  void declareInterface(InterfaceDefinition* interfaceM) override;
  void translateInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) override;
  void declareKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT) override;
  void declareUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT) override;
  void translateUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT, LLVMTypeRef elementLT) override;
  void translateKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT, LLVMTypeRef elementLT) override;

  ControlBlockPtrLE getConcreteControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* reference,
      WrapperPtrLE wrapperPtrLE) override;

  WrapperPtrLE makeWrapperPtr(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      LLVMValueRef ptrLE) override;

  InterfaceFatPtrLE makeInterfaceFatPtr(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM_,
      LLVMValueRef ptrLE) override;

  InterfaceFatPtrLE makeInterfaceFatPtrWithoutChecking(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM_,
      LLVMValueRef ptrLE) override;

//  ControlBlockPtrLE makeControlBlockPtr(
//      AreaAndFileAndLine checkerAFL,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Referend* referendM,
//      LLVMValueRef controlBlockPtrLE) override;

  LLVMValueRef getStringBytesPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref ref) override;

  LLVMValueRef getStringLen(
      FunctionState* functionState, LLVMBuilderRef builder, Ref ref) override;


  ControlBlockPtrLE getControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* referendM,
      InterfaceFatPtrLE interfaceFatPtrLE) override;

  ControlBlockPtrLE getControlBlockPtrWithoutChecking(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* referendM,
      InterfaceFatPtrLE interfaceFatPtrLE) override;

  ControlBlockPtrLE getControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      // This will be a pointer if a mutable struct, or a fat ref if an interface.
      Ref ref,
      Reference* referenceM) override;

   ControlBlockPtrLE getControlBlockPtr(
       AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      // This will be a pointer if a mutable struct, or a fat ref if an interface.
      LLVMValueRef ref,
      Reference* referenceM) override;

  ControlBlockPtrLE getControlBlockPtrWithoutChecking(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      // This will be a pointer if a mutable struct, or a fat ref if an interface.
      LLVMValueRef ref,
      Reference* referenceM) override;

  LLVMValueRef getStructContentsPtr(
      LLVMBuilderRef builder,
      Referend* referend,
      WrapperPtrLE wrapperPtrLE) override;


  LLVMValueRef getVoidPtrFromInterfacePtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      InterfaceFatPtrLE virtualArgLE) override;


private:
  GlobalState* globalState;
  GetReferendStructsSource getReferendStructsSource;
};

// This is a class that wraps three Structses into one, and routes calls to them based on the
// referend's Mutability and Weakability.
class WeakRefStructsRouter : public IWeakRefStructsSource {
public:
  explicit WeakRefStructsRouter(GetWeakRefStructsSource getWeakRefStructsSource_)
    : getWeakRefStructsSource(getWeakRefStructsSource_) {}

  LLVMTypeRef getStructWeakRefStruct(StructReferend* structReferend) override;
  LLVMTypeRef getKnownSizeArrayWeakRefStruct(KnownSizeArrayT* ksaMT) override;
  LLVMTypeRef getUnknownSizeArrayWeakRefStruct(UnknownSizeArrayT* usaMT) override;
  LLVMTypeRef getInterfaceWeakRefStruct(InterfaceReferend* interfaceReferend) override;
  WeakFatPtrLE makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) override;

private:
  GetWeakRefStructsSource getWeakRefStructsSource;
};

#endif
