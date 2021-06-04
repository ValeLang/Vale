#ifndef REGION_COMMON_DEFAULTLAYOUT_STRUCTS_H_
#define REGION_COMMON_DEFAULTLAYOUT_STRUCTS_H_

#include <globalstate.h>
#include "region/common/controlblock.h"
#include "ikindstructssource.h"


class IWeakRefStructsSource {
public:
  virtual ~IWeakRefStructsSource() = default;
  virtual LLVMTypeRef getStructWeakRefStruct(StructKind *structKind) = 0;
  virtual LLVMTypeRef getStaticSizedArrayWeakRefStruct(StaticSizedArrayT *ssaMT) = 0;
  virtual LLVMTypeRef getRuntimeSizedArrayWeakRefStruct(RuntimeSizedArrayT *rsaMT) = 0;
  virtual LLVMTypeRef getInterfaceWeakRefStruct(InterfaceKind *interfaceKind) = 0;
  virtual WeakFatPtrLE makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) = 0;

  virtual LLVMTypeRef getWeakRefHeaderStruct(Kind* kind) = 0;
  // This is a weak ref to a void*. When we're calling an interface method on a weak,
  // we have no idea who the receiver is. They'll receive this struct as the correctly
  // typed flavor of it (from structWeakRefStructs).
  virtual LLVMTypeRef getWeakVoidRefStruct(Kind* kind) = 0;

  virtual WeakFatPtrLE downcastWeakFatPtr(
      LLVMBuilderRef builder,
      StructKind* targetStructKind,
      Reference* targetRefMT,
      LLVMValueRef sourceWeakFatPtrLE) = 0;
};


// This is a collection of layouts and LLVM types for all sorts of NON-WEAKABLE kinds.
// We feed it kinds, and it defines structs for us.
// In every mode, immStructs and non-weakable muts use this *directly*.
// (Keep in mind, resilient modes have no non-weakable muts)
// Note how we said *directly* above. This is also used *indirectly* by WeakableStructs, who
// puts somet extra things into the ControlBlock for its own weakability purposes.
class KindStructs : public IKindStructsSource {
public:
  KindStructs(GlobalState* globalState_, ControlBlock controlBlock_);

  ControlBlock* getControlBlock(Kind* kind) override;
  ControlBlock* getControlBlock();
  LLVMTypeRef getInnerStruct(StructKind* structKind) override;
  LLVMTypeRef getWrapperStruct(StructKind* structKind) override;
  LLVMTypeRef getStaticSizedArrayWrapperStruct(StaticSizedArrayT* ssaMT) override;
  LLVMTypeRef getRuntimeSizedArrayWrapperStruct(RuntimeSizedArrayT* rsaMT) override;
  LLVMTypeRef getInterfaceRefStruct(InterfaceKind* interfaceKind) override;
  LLVMTypeRef getInterfaceTableStruct(InterfaceKind* interfaceKind) override;
  LLVMTypeRef getStringWrapperStruct() override;
  void defineStruct(StructKind* structM, std::vector<LLVMTypeRef> membersLT) override;
  void declareStruct(StructKind* structM) override;
  void declareEdge(Edge* edge) override;
  void defineEdge(
      Edge* edge,
      std::vector<LLVMTypeRef> interfaceFunctionsLT,
      std::vector<LLVMValueRef> functions) override;
  void declareInterface(InterfaceDefinition* interface) override;
  void defineInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) override;
  void declareStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayMT) override;
  void declareRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) override;
  void defineRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT, LLVMTypeRef elementLT) override;
  void defineStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayMT, LLVMTypeRef elementLT) override;

  LLVMTypeRef getControlBlockStruct() {
    return controlBlock.getStruct();
  }

  LLVMTypeRef getStringWrapperStructL() {
    return stringWrapperStructL;
  }

  LLVMValueRef getObjIdFromControlBlockPtr(
    LLVMBuilderRef builder,
    Kind* kindM,
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
//      Kind* kindM,
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
      Kind* kindM,
      InterfaceFatPtrLE interfaceFatPtrLE) override;

  ControlBlockPtrLE getControlBlockPtrWithoutChecking(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Kind* kindM,
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
      Kind* kind,
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
      Kind* kindM,
      LLVMValueRef controlBlockPtrLE);

  ControlBlockPtrLE makeControlBlockPtrWithoutChecking(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Kind* kindM,
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
  std::unordered_map<std::string, LLVMTypeRef> staticSizedArrayWrapperStructs;
  std::unordered_map<std::string, LLVMTypeRef> runtimeSizedArrayWrapperStructs;

  LLVMTypeRef stringWrapperStructL = nullptr;
  LLVMTypeRef stringInnerStructL = nullptr;
};

// This is a collection of layouts and LLVM types for all sorts of WEAKABLE kinds.
// We feed it kinds, and it defines structs for us.
// In every mode, weakable muts use this directly.
// In every mode, immStructs and non-weakable muts DONT use this directly.
// (Keep in mind, in resilient modes, all muts are weakable)
class WeakableKindStructs : public IKindStructsSource, public IWeakRefStructsSource {
public:
  WeakableKindStructs(
      GlobalState* globalState_,
      ControlBlock controlBlock,
      LLVMTypeRef weakRefHeaderStructL_);

  ControlBlock* getControlBlock();
  ControlBlock* getControlBlock(Kind* kind) override;
  LLVMTypeRef getInnerStruct(StructKind* structKind) override;
  LLVMTypeRef getWrapperStruct(StructKind* structKind) override;
  LLVMTypeRef getStaticSizedArrayWrapperStruct(StaticSizedArrayT* ssaMT) override;
  LLVMTypeRef getRuntimeSizedArrayWrapperStruct(RuntimeSizedArrayT* rsaMT) override;
  LLVMTypeRef getInterfaceRefStruct(InterfaceKind* interfaceKind) override;
  LLVMTypeRef getInterfaceTableStruct(InterfaceKind* interfaceKind) override;

  void defineStruct(StructKind* structM, std::vector<LLVMTypeRef> membersLT) override;
  void declareStruct(StructKind* structM) override;
  void declareEdge(Edge* edge) override;
  void defineEdge(
      Edge* edge,
      std::vector<LLVMTypeRef> interfaceFunctionsLT,
      std::vector<LLVMValueRef> functions) override;
  void declareInterface(InterfaceDefinition* interface) override;
  void defineInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) override;
  void declareStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayMT) override;
  void declareRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) override;
  void defineRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT, LLVMTypeRef elementLT) override;
  void defineStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayMT, LLVMTypeRef elementLT) override;

  LLVMTypeRef getStructWeakRefStruct(StructKind* structKind) override;
  LLVMTypeRef getStaticSizedArrayWeakRefStruct(StaticSizedArrayT* ssaMT) override;
  LLVMTypeRef getRuntimeSizedArrayWeakRefStruct(RuntimeSizedArrayT* rsaMT) override;
  LLVMTypeRef getInterfaceWeakRefStruct(InterfaceKind* interfaceKind) override;

  WeakFatPtrLE makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) override;
  WeakFatPtrLE downcastWeakFatPtr(
      LLVMBuilderRef builder,
      StructKind* targetStructKind,
      Reference* targetRefMT,
      LLVMValueRef sourceWeakFatPtrLE) override;

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
//      Kind* kindM,
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
      Kind* kindM,
      InterfaceFatPtrLE interfaceFatPtrLE) override;

  ControlBlockPtrLE getControlBlockPtrWithoutChecking(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Kind* kindM,
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
      Kind* kind,
      WrapperPtrLE wrapperPtrLE) override;

  LLVMValueRef getVoidPtrFromInterfacePtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      InterfaceFatPtrLE virtualArgLE) override;

  LLVMValueRef getObjIdFromControlBlockPtr(
      LLVMBuilderRef builder,
      Kind* kindM,
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

  LLVMTypeRef getWeakRefHeaderStruct(Kind* kind) override {
    return weakRefHeaderStructL;
  }
  // This is a weak ref to a void*. When we're calling an interface method on a weak,
  // we have no idea who the receiver is. They'll receive this struct as the correctly
  // typed flavor of it (from structWeakRefStructs).
  LLVMTypeRef getWeakVoidRefStruct(Kind* kind) override {
    return weakVoidRefStructL;
  }

private:
  GlobalState* globalState = nullptr;

  KindStructs kindStructs;

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
  std::unordered_map<std::string, LLVMTypeRef> staticSizedArrayWeakRefStructs;
  // These contain a pointer to the weak ref count int, and a pointer to the underlying unknown size array.
  std::unordered_map<std::string, LLVMTypeRef> runtimeSizedArrayWeakRefStructs;
};

#endif