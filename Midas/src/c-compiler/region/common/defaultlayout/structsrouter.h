#ifndef REGION_COMMON_DEFAULTLAYOUT_STRUCTSROUTER_H_
#define REGION_COMMON_DEFAULTLAYOUT_STRUCTSROUTER_H_

#include <globalstate.h>
#include <function/expressions/shared/weaks.h>
#include "structs.h"

using GetReferendStructsSource = std::function<IReferendStructsSource*(Referend*)>;
using GetWeakRefStructsSource = std::function<IWeakRefStructsSource*(Referend*)>;

// This is a class that wraps three Structses into one, and routes calls to them based on the
// referend's Mutability and Weakability.
class ReferendStructsRouter : public IReferendStructsSource {
public:
  explicit ReferendStructsRouter(GetReferendStructsSource getReferendStructsSource_);

  ControlBlock* getControlBlock(Referend* referend) override;

  LLVMTypeRef getInnerStruct(StructReferend* structReferend) override;
  LLVMTypeRef getWrapperStruct(StructReferend* structReferend) override;
  LLVMTypeRef getKnownSizeArrayWrapperStruct(KnownSizeArrayT* ksaMT) override;
  LLVMTypeRef getUnknownSizeArrayWrapperStruct(UnknownSizeArrayT* usaMT) override;
  LLVMTypeRef getInterfaceRefStruct(InterfaceReferend* interfaceReferend) override;
  LLVMTypeRef getInterfaceTableStruct(InterfaceReferend* interfaceReferend) override;

  void translateStruct(StructDefinition* structM, std::vector<LLVMTypeRef> membersLT) override;
  void declareStruct(StructDefinition* structM) override;
  void declareEdge(Edge* edge) override;
  void translateEdge(Edge* edge, std::vector<LLVMValueRef> functions) override;
  void declareInterface(InterfaceDefinition* interfaceM) override;
  void translateInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) override;
  void declareKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT) override;
  void declareUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT) override;
  void translateUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT, LLVMTypeRef elementLT) override;
  void translateKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT, LLVMTypeRef elementLT) override;

private:
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

private:
  GetWeakRefStructsSource getWeakRefStructsSource;
};

#endif
