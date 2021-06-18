#ifndef REGION_COMMON_DEFAULTLAYOUT_STRUCTS_H_
#define REGION_COMMON_DEFAULTLAYOUT_STRUCTS_H_

#include <globalstate.h>
#include "region/common/controlblock.h"
#include "ikindstructssource.h"

// This is a collection of layouts and LLVM types for all sorts of kinds.
// We feed it kinds, and it defines structs for us.
class KindStructs {// : public KindStructs, public KindStructs {
public:
  KindStructs(
      GlobalState* globalState_,
      ControlBlock nonWeakableControlBlock,
      ControlBlock weakableControlBlock,
      LLVMTypeRef weakRefHeaderStructL_);

//  ControlBlock* getControlBlock();
  ControlBlock* getControlBlock(Kind* kind);
  LLVMTypeRef getInnerStruct(StructKind* structKind);
  LLVMTypeRef getWrapperStruct(StructKind* structKind);
  LLVMTypeRef getStaticSizedArrayWrapperStruct(StaticSizedArrayT* ssaMT);
  LLVMTypeRef getRuntimeSizedArrayWrapperStruct(RuntimeSizedArrayT* rsaMT);
  LLVMTypeRef getInterfaceRefStruct(InterfaceKind* interfaceKind);
  LLVMTypeRef getInterfaceTableStruct(InterfaceKind* interfaceKind);

  void defineStruct(StructKind* structM, std::vector<LLVMTypeRef> membersLT);
  void declareStruct(StructKind* structM, Weakability weakable);
  void declareEdge(Edge* edge);
  void defineEdge(
      Edge* edge,
      std::vector<LLVMTypeRef> interfaceFunctionsLT,
      std::vector<LLVMValueRef> functions);
  void declareInterface(InterfaceKind* interface, Weakability weakable);
  void defineInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL);
  void declareStaticSizedArray(StaticSizedArrayT* staticSizedArrayMT, Weakability weakable);
  void declareRuntimeSizedArray(RuntimeSizedArrayT* runtimeSizedArrayMT, Weakability weakable);
  void defineRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT, LLVMTypeRef elementLT);
  void defineStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayMT, LLVMTypeRef elementLT);

  LLVMTypeRef getStructWeakRefStruct(StructKind* structKind);
  LLVMTypeRef getStaticSizedArrayWeakRefStruct(StaticSizedArrayT* ssaMT);
  LLVMTypeRef getRuntimeSizedArrayWeakRefStruct(RuntimeSizedArrayT* rsaMT);
  LLVMTypeRef getInterfaceWeakRefStruct(InterfaceKind* interfaceKind);

  WeakFatPtrLE makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE);
  WeakFatPtrLE downcastWeakFatPtr(
      LLVMBuilderRef builder,
      StructKind* targetStructKind,
      Reference* targetRefMT,
      LLVMValueRef sourceWeakFatPtrLE);

  ControlBlockPtrLE getConcreteControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* reference,
      WrapperPtrLE wrapperPtrLE);


  LLVMTypeRef getStringWrapperStruct();
  WrapperPtrLE makeWrapperPtr(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      LLVMValueRef ptrLE);

  InterfaceFatPtrLE makeInterfaceFatPtr(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM_,
      LLVMValueRef ptrLE);

  InterfaceFatPtrLE makeInterfaceFatPtrWithoutChecking(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM_,
      LLVMValueRef ptrLE);

//  ControlBlockPtrLE makeControlBlockPtr(
//      AreaAndFileAndLine checkerAFL,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Kind* kindM,
//      LLVMValueRef controlBlockPtrLE);

  LLVMValueRef getStringBytesPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      WrapperPtrLE ptrLE);
  LLVMValueRef getStringLen(
      FunctionState* functionState, LLVMBuilderRef builder, WrapperPtrLE ptrLE);


  ControlBlockPtrLE getControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Kind* kindM,
      InterfaceFatPtrLE interfaceFatPtrLE);

  ControlBlockPtrLE getControlBlockPtrWithoutChecking(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Kind* kindM,
      InterfaceFatPtrLE interfaceFatPtrLE);

  ControlBlockPtrLE getControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      // This will be a pointer if a mutable struct, or a fat ref if an interface.
      Ref ref,
      Reference* referenceM);

  ControlBlockPtrLE getControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      // This will be a pointer if a mutable struct, or a fat ref if an interface.
      LLVMValueRef ref,
      Reference* referenceM);

  ControlBlockPtrLE getControlBlockPtrWithoutChecking(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      // This will be a pointer if a mutable struct, or a fat ref if an interface.
      LLVMValueRef ref,
      Reference* referenceM);

  LLVMValueRef getStructContentsPtr(
      LLVMBuilderRef builder,
      Kind* kind,
      WrapperPtrLE wrapperPtrLE);

  LLVMValueRef getVoidPtrFromInterfacePtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      InterfaceFatPtrLE virtualArgLE);

  LLVMValueRef getObjIdFromControlBlockPtr(
      LLVMBuilderRef builder,
      Kind* kindM,
      ControlBlockPtrLE controlBlockPtr);

  // See CRCISFAORC for why we don't take in a mutability.
  // Strong means owning or borrow or shared; things that control the lifetime.
  LLVMValueRef getStrongRcPtrFromControlBlockPtr(
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtr);

  // See CRCISFAORC for why we don't take in a mutability.
  // Strong means owning or borrow or shared; things that control the lifetime.
  LLVMValueRef getStrongRcFromControlBlockPtr(
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtr);

  LLVMValueRef downcastPtr(LLVMBuilderRef builder, Reference* resultStructRefMT, LLVMValueRef unknownPossibilityPtrLE);

  LLVMTypeRef getWeakRefHeaderStruct(Kind* kind) {
    return weakRefHeaderStructL;
  }
  // This is a weak ref to a void*. When we're calling an interface method on a weak,
  // we have no idea who the receiver is. They'll receive this struct as the correctly
  // typed flavor of it (from structWeakRefStructs).
  LLVMTypeRef getWeakVoidRefStruct(Kind* kind) {
    return weakVoidRefStructL;
  }

//  LLVMTypeRef getControlBlockStruct() {
//    return controlBlock.getStruct();
//  }

  LLVMTypeRef getStringWrapperStructL() {
    return stringWrapperStructL;
  }

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

  Weakability structIsWeakable(StructKind* struuct);
  Weakability interfaceIsWeakable(InterfaceKind* struuct);
  Weakability staticSizedArrayIsWeakable(StaticSizedArrayT* struuct);
  Weakability runtimeSizedArrayIsWeakable(RuntimeSizedArrayT* struuct);

private:
  GlobalState* globalState = nullptr;

  LLVMTypeRef weakRefHeaderStructL = nullptr; // contains generation and maybe gen index
  // This is a weak ref to a void*. When we're calling an interface method on a weak,
  // we have no idea who the receiver is. They'll receive this struct as the correctly
  // typed flavor of it (from structWeakRefStructs).
  LLVMTypeRef weakVoidRefStructL = nullptr;

  std::unordered_map<StructKind*, LLVMTypeRef, AddressHasher<StructKind*>> structWeakRefStructs;
  std::unordered_map<InterfaceKind*, LLVMTypeRef, AddressHasher<InterfaceKind*>> interfaceWeakRefStructs;
  std::unordered_map<StaticSizedArrayT*, LLVMTypeRef, AddressHasher<StaticSizedArrayT*>> staticSizedArrayWeakRefStructs;
  std::unordered_map<RuntimeSizedArrayT*, LLVMTypeRef, AddressHasher<RuntimeSizedArrayT*>> runtimeSizedArrayWeakRefStructs;

  ControlBlock nonWeakableControlBlock;
  ControlBlock weakableControlBlock;

  // These contain a bunch of function pointer fields.
  std::unordered_map<InterfaceKind*, LLVMTypeRef, AddressHasher<InterfaceKind*>> interfaceTableStructs;
  // These contain a pointer to the interface table struct below and a void*
  // to the underlying struct.
  std::unordered_map<InterfaceKind*, LLVMTypeRef, AddressHasher<InterfaceKind*>> interfaceRefStructs;
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

#endif