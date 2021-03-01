#ifndef REGION_COMMON_DEFAULTLAYOUT_STRUCTS_H_
#define REGION_COMMON_DEFAULTLAYOUT_STRUCTS_H_

#include <globalstate.h>
#include "region/common/controlblock.h"
#include "ireferendstructssource.h"


class IWeakRefStructsSource {
public:
  virtual ~IWeakRefStructsSource() = default;
  virtual LLVMTypeRef getStructWeakRefStruct(StructReferend *structReferend) = 0;
  virtual LLVMTypeRef getKnownSizeArrayWeakRefStruct(KnownSizeArrayT *ksaMT) = 0;
  virtual LLVMTypeRef getUnknownSizeArrayWeakRefStruct(UnknownSizeArrayT *usaMT) = 0;
  virtual LLVMTypeRef getInterfaceWeakRefStruct(InterfaceReferend *interfaceReferend) = 0;
  virtual WeakFatPtrLE makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) = 0;

  virtual LLVMTypeRef getWeakRefHeaderStruct(Referend* referend) = 0;
  // This is a weak ref to a void*. When we're calling an interface method on a weak,
  // we have no idea who the receiver is. They'll receive this struct as the correctly
  // typed flavor of it (from structWeakRefStructs).
  virtual LLVMTypeRef getWeakVoidRefStruct(Referend* referend) = 0;
};


// This is a collection of layouts and LLVM types for all sorts of NON-WEAKABLE referends.
// We feed it referends, and it defines structs for us.
// In every mode, immStructs and non-weakable muts use this *directly*.
// (Keep in mind, resilient modes have no non-weakable muts)
// Note how we said *directly* above. This is also used *indirectly* by WeakableStructs, who
// puts somet extra things into the ControlBlock for its own weakability purposes.
class ReferendStructs : public IReferendStructsSource {
public:
  ReferendStructs(GlobalState* globalState_, ControlBlock controlBlock_);

  ControlBlock* getControlBlock(Referend* referend) override;
  LLVMTypeRef getInnerStruct(StructReferend* structReferend) override;
  LLVMTypeRef getWrapperStruct(StructReferend* structReferend) override;
  LLVMTypeRef getKnownSizeArrayWrapperStruct(KnownSizeArrayT* ksaMT) override;
  LLVMTypeRef getUnknownSizeArrayWrapperStruct(UnknownSizeArrayT* usaMT) override;
  LLVMTypeRef getInterfaceRefStruct(InterfaceReferend* interfaceReferend) override;
  LLVMTypeRef getInterfaceTableStruct(InterfaceReferend* interfaceReferend) override;
  LLVMTypeRef getStringWrapperStruct() override;
  void defineStruct(StructDefinition* structM, std::vector<LLVMTypeRef> membersLT) override;
  void declareStruct(StructDefinition* structM) override;
  void declareEdge(Edge* edge) override;
  void defineEdge(
      Edge* edge,
      std::vector<LLVMTypeRef> interfaceFunctionsLT,
      std::vector<LLVMValueRef> functions) override;
  void declareInterface(InterfaceDefinition* interface) override;
  void defineInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) override;
  void declareKnownSizeArray(KnownSizeArrayDefinitionT* knownSizeArrayMT) override;
  void declareUnknownSizeArray(UnknownSizeArrayDefinitionT* unknownSizeArrayMT) override;
  void defineUnknownSizeArray(UnknownSizeArrayDefinitionT* unknownSizeArrayMT, LLVMTypeRef elementLT) override;
  void defineKnownSizeArray(KnownSizeArrayDefinitionT* knownSizeArrayMT, LLVMTypeRef elementLT) override;

  LLVMTypeRef getControlBlockStruct() {
    return controlBlock.getStruct();
  }

  LLVMTypeRef getStringWrapperStructL() {
    return stringWrapperStructL;
  }

  LLVMValueRef getObjIdFromControlBlockPtr(
    LLVMBuilderRef builder,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtr) override;

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
      WrapperPtrLE ref) override;

  ControlBlockPtrLE getConcreteControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* reference,
      WrapperPtrLE wrapperPtrLE) override;


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

  // See CRCISFAORC for why we don't take in a mutability.
  // Strong means owning or borrow or shared; things that control the lifetime.
  LLVMValueRef getStrongRcPtrFromControlBlockPtr(
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtr) override;

  // See CRCISFAORC for why we don't take in a mutability.
  // Strong means owning or borrow or shared; things that control the lifetime.
  LLVMValueRef getStrongRcFromControlBlockPtr(
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtr) override;

  ControlBlockPtrLE getControlBlockPtrWithoutChecking(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      // This will be a pointer if a mutable struct, or a fat ref if an interface.
      LLVMValueRef ref,
      Reference* referenceM) override;

  LLVMValueRef getStringLen(FunctionState* functionState, LLVMBuilderRef builder, WrapperPtrLE ptrLE) override;


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
  ControlBlockPtrLE makeControlBlockPtr(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* referendM,
      LLVMValueRef controlBlockPtrLE);

  ControlBlockPtrLE makeControlBlockPtrWithoutChecking(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* referendM,
      LLVMValueRef controlBlockPtrLE);

  WrapperPtrLE makeWrapperPtrWithoutChecking(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      LLVMValueRef ptrLE);

  ControlBlockPtrLE getConcreteControlBlockPtrWithoutChecking(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* reference,
      WrapperPtrLE wrapperPtrLE);

private:

  GlobalState* globalState = nullptr;

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

  LLVMTypeRef stringWrapperStructL = nullptr;
  LLVMTypeRef stringInnerStructL = nullptr;
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

  void defineStruct(StructDefinition* structM, std::vector<LLVMTypeRef> membersLT) override;
  void declareStruct(StructDefinition* structM) override;
  void declareEdge(Edge* edge) override;
  void defineEdge(
      Edge* edge,
      std::vector<LLVMTypeRef> interfaceFunctionsLT,
      std::vector<LLVMValueRef> functions) override;
  void declareInterface(InterfaceDefinition* interface) override;
  void defineInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) override;
  void declareKnownSizeArray(KnownSizeArrayDefinitionT* knownSizeArrayMT) override;
  void declareUnknownSizeArray(UnknownSizeArrayDefinitionT* unknownSizeArrayMT) override;
  void defineUnknownSizeArray(UnknownSizeArrayDefinitionT* unknownSizeArrayMT, LLVMTypeRef elementLT) override;
  void defineKnownSizeArray(KnownSizeArrayDefinitionT* knownSizeArrayMT, LLVMTypeRef elementLT) override;

  LLVMTypeRef getStructWeakRefStruct(StructReferend* structReferend) override;
  LLVMTypeRef getKnownSizeArrayWeakRefStruct(KnownSizeArrayT* ksaMT) override;
  LLVMTypeRef getUnknownSizeArrayWeakRefStruct(UnknownSizeArrayT* usaMT) override;
  LLVMTypeRef getInterfaceWeakRefStruct(InterfaceReferend* interfaceReferend) override;

  WeakFatPtrLE makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) override;

  ControlBlockPtrLE getConcreteControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* reference,
      WrapperPtrLE wrapperPtrLE) override;


  LLVMTypeRef getStringWrapperStruct() override;
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
      WrapperPtrLE ptrLE) override;
  LLVMValueRef getStringLen(
      FunctionState* functionState, LLVMBuilderRef builder, WrapperPtrLE ptrLE) override;


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

  LLVMValueRef getObjIdFromControlBlockPtr(
      LLVMBuilderRef builder,
      Referend* referendM,
      ControlBlockPtrLE controlBlockPtr) override;

  // See CRCISFAORC for why we don't take in a mutability.
  // Strong means owning or borrow or shared; things that control the lifetime.
  LLVMValueRef getStrongRcPtrFromControlBlockPtr(
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtr) override;

  // See CRCISFAORC for why we don't take in a mutability.
  // Strong means owning or borrow or shared; things that control the lifetime.
  LLVMValueRef getStrongRcFromControlBlockPtr(
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtr) override;

  LLVMTypeRef getWeakRefHeaderStruct(Referend* referend) override {
    return weakRefHeaderStructL;
  }
  // This is a weak ref to a void*. When we're calling an interface method on a weak,
  // we have no idea who the receiver is. They'll receive this struct as the correctly
  // typed flavor of it (from structWeakRefStructs).
  LLVMTypeRef getWeakVoidRefStruct(Referend* referend) override {
    return weakVoidRefStructL;
  }

private:
  GlobalState* globalState = nullptr;

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