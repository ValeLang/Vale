#ifndef REGION_COMMON_DEFAULTLAYOUT_STRUCTS_H_
#define REGION_COMMON_DEFAULTLAYOUT_STRUCTS_H_

#include <globalstate.h>
#include "region/common/controlblock.h"

class IReferendStructsSource {
public:
  virtual ~IReferendStructsSource() = default;
  virtual ControlBlock* getControlBlock(Referend* referend) = 0;
  virtual LLVMTypeRef getInnerStruct(StructReferend* structReferend) = 0;
  virtual LLVMTypeRef getWrapperStruct(StructReferend* structReferend) = 0;
  virtual LLVMTypeRef getKnownSizeArrayWrapperStruct(KnownSizeArrayT* ksaMT) = 0;
  virtual LLVMTypeRef getUnknownSizeArrayWrapperStruct(UnknownSizeArrayT* usaMT) = 0;
  virtual LLVMTypeRef getInterfaceRefStruct(InterfaceReferend* interfaceReferend) = 0;
  virtual LLVMTypeRef getInterfaceTableStruct(InterfaceReferend* interfaceReferend) = 0;
  virtual void translateStruct(StructDefinition* structM, std::vector<LLVMTypeRef> membersLT) = 0;
  virtual void declareStruct(StructDefinition* structM) = 0;
  virtual void declareEdge(Edge* edge) = 0;
  virtual void translateEdge(Edge* edge, std::vector<LLVMValueRef> functions) = 0;
  virtual void declareInterface(InterfaceDefinition* interface) = 0;
  virtual void translateInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) = 0;
  virtual void declareKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT) = 0;
  virtual void declareUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT) = 0;
  virtual void translateUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT, LLVMTypeRef elementLT) = 0;
  virtual void translateKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT, LLVMTypeRef elementLT) = 0;
};


class IWeakRefStructsSource {
public:
  virtual ~IWeakRefStructsSource() = default;
  virtual LLVMTypeRef getStructWeakRefStruct(StructReferend *structReferend) = 0;
  virtual LLVMTypeRef getKnownSizeArrayWeakRefStruct(KnownSizeArrayT *ksaMT) = 0;
  virtual LLVMTypeRef getUnknownSizeArrayWeakRefStruct(UnknownSizeArrayT *usaMT) = 0;
  virtual LLVMTypeRef getInterfaceWeakRefStruct(InterfaceReferend *interfaceReferend) = 0;
};


// This is a collection of layouts and LLVM types for all sorts of NON-WEAKABLE referends.
// We feed it referends, and it defines structs for us.
// In every mode, immStructs and non-weakable muts use this *directly*.
// (Keep in mind, resilient modes have no non-weakable muts)
// Note how we said *directly* above. This is also used *indirectly* by WeakableStructs, who
// puts somet extra things into the ControlBlock for its own weakability purposes.
class ReferendStructs : public IReferendStructsSource {
public:
  ReferendStructs(GlobalState* globalState_, ControlBlock controlBlock_)
      : globalState(globalState_),
      controlBlock(controlBlock_) {}

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
  void declareInterface(InterfaceDefinition* interface) override;
  void translateInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) override;
  void declareKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT) override;
  void declareUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT) override;
  void translateUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT, LLVMTypeRef elementLT) override;
  void translateKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT, LLVMTypeRef elementLT) override;



  GlobalState* globalState;

  ControlBlock controlBlock;

  // These contain a bunch of function pointer fields.
  std::unordered_map<std::string, LLVMTypeRef> interfaceTableStructs;
  // These contain a pointer to the interface table struct below and a void*
  // to the underlying struct.
  std::unordered_map<std::string, LLVMTypeRef> interfaceRefStructs;
  // These don't have a ref count.
  // They're used directly for inl imm references, and
  // also used inside the below wrapperStructs.
  std::unordered_map<std::string, LLVMTypeRef> innerStructs;
  // These contain a ref count and the above val struct. Yon references
  // point to these.
  std::unordered_map<std::string, LLVMTypeRef> wrapperStructs;

  // These contain a ref count and an array type. Yon references
  // point to these.
  std::unordered_map<std::string, LLVMTypeRef> knownSizeArrayWrapperStructs;
  std::unordered_map<std::string, LLVMTypeRef> unknownSizeArrayWrapperStructs;
};

// This is a collection of layouts and LLVM types for all sorts of WEAKABLE referends.
// We feed it referends, and it defines structs for us.
// In every mode, weakable muts use this directly.
// In every mode, immStructs and non-weakable muts DONT use this directly.
// (Keep in mind, in resilient modes, all muts are weakable)
class WeakableReferendStructs : public IReferendStructsSource, public IWeakRefStructsSource {
public:
  WeakableReferendStructs(
      GlobalState* globalState_,
      ControlBlock controlBlock,
      LLVMTypeRef weakRefHeaderStructL_);

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
  void declareInterface(InterfaceDefinition* interface) override;
  void translateInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) override;
  void declareKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT) override;
  void declareUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT) override;
  void translateUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT, LLVMTypeRef elementLT) override;
  void translateKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT, LLVMTypeRef elementLT) override;

  LLVMTypeRef getStructWeakRefStruct(StructReferend* structReferend) override;
  LLVMTypeRef getKnownSizeArrayWeakRefStruct(KnownSizeArrayT* ksaMT) override;
  LLVMTypeRef getUnknownSizeArrayWeakRefStruct(UnknownSizeArrayT* usaMT) override;
  LLVMTypeRef getInterfaceWeakRefStruct(InterfaceReferend* interfaceReferend) override;

  GlobalState* globalState;

  ReferendStructs referendStructs;

  LLVMTypeRef weakRefHeaderStructL = nullptr; // contains generation and maybe gen index
  // This is a weak ref to a void*. When we're calling an interface method on a weak,
  // we have no idea who the receiver is. They'll receive this struct as the correctly
  // typed flavor of it (from structWeakRefStructs).
  LLVMTypeRef weakVoidRefStructL = nullptr;

  // These contain a pointer to the weak ref count int, and a pointer to the underlying struct.
  std::unordered_map<std::string, LLVMTypeRef> structWeakRefStructs;
  // These contain a pointer to the weak ref count int, and then a regular interface ref struct.
  std::unordered_map<std::string, LLVMTypeRef> interfaceWeakRefStructs;
  // These contain a pointer to the weak ref count int, and a pointer to the underlying known size array.
  std::unordered_map<std::string, LLVMTypeRef> knownSizeArrayWeakRefStructs;
  // These contain a pointer to the weak ref count int, and a pointer to the underlying unknown size array.
  std::unordered_map<std::string, LLVMTypeRef> unknownSizeArrayWeakRefStructs;
};

#endif