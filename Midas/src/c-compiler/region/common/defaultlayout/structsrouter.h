#ifndef REGION_COMMON_DEFAULTLAYOUT_STRUCTSROUTER_H_
#define REGION_COMMON_DEFAULTLAYOUT_STRUCTSROUTER_H_

#include <globalstate.h>
#include "structs.h"

//using GetKindStructsSource = std::function<KindStructs*(Kind*)>;
//using GetWeakRefStructsSource = std::function<KindStructs*(Kind*)>;
//
//// This is a class that wraps three Structses into one, and routes calls to them based on the
//// kind's Mutability and Weakability.
//class KindStructsRouter : public KindStructs {
//public:
//  KindStructsRouter(
//      GlobalState* globalState,
//      GetKindStructsSource getKindStructsSource_);
//
//  ControlBlock* getControlBlock(Kind* kind) override;
//
//  LLVMTypeRef getInnerStruct(StructKind* structKind) override;
//  LLVMTypeRef getWrapperStruct(StructKind* structKind) override;
//  LLVMTypeRef getStaticSizedArrayWrapperStruct(StaticSizedArrayT* ssaMT) override;
//  LLVMTypeRef getRuntimeSizedArrayWrapperStruct(RuntimeSizedArrayT* rsaMT) override;
//  LLVMTypeRef getInterfaceRefStruct(InterfaceKind* interfaceKind) override;
//  LLVMTypeRef getInterfaceTableStruct(InterfaceKind* interfaceKind) override;
//  LLVMTypeRef getStringWrapperStruct() override;
//
//  void defineStruct(StructKind* structM, std::vector<LLVMTypeRef> membersLT) override;
//  void declareStruct(StructKind* structM) override;
//  void declareEdge(Edge* edge) override;
//  void defineEdge(
//      Edge* edge,
//      std::vector<LLVMTypeRef> interfaceFunctionsLT,
//      std::vector<LLVMValueRef> functions) override;
//  void declareInterface(InterfaceDefinition* interfaceM) override;
//  void defineInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) override;
//  void declareStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayMT) override;
//  void declareRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) override;
//  void defineRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT, LLVMTypeRef elementLT) override;
//  void defineStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayMT, LLVMTypeRef elementLT) override;
//
//  ControlBlockPtrLE getConcreteControlBlockPtr(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Reference* reference,
//      WrapperPtrLE wrapperPtrLE) override;
//
//  WrapperPtrLE makeWrapperPtr(
//      AreaAndFileAndLine checkerAFL,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Reference* referenceM,
//      LLVMValueRef ptrLE) override;
//
//  InterfaceFatPtrLE makeInterfaceFatPtr(
//      AreaAndFileAndLine checkerAFL,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Reference* referenceM_,
//      LLVMValueRef ptrLE) override;
//
//  InterfaceFatPtrLE makeInterfaceFatPtrWithoutChecking(
//      AreaAndFileAndLine checkerAFL,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Reference* referenceM_,
//      LLVMValueRef ptrLE) override;
//
////  ControlBlockPtrLE makeControlBlockPtr(
////      AreaAndFileAndLine checkerAFL,
////      FunctionState* functionState,
////      LLVMBuilderRef builder,
////      Kind* kindM,
////      LLVMValueRef controlBlockPtrLE) override;
//
//  LLVMValueRef getStringBytesPtr(
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      WrapperPtrLE ptrLE) override;
//
//  LLVMValueRef getStringLen(
//      FunctionState* functionState, LLVMBuilderRef builder, WrapperPtrLE ptrLE) override;
//
//
//  ControlBlockPtrLE getControlBlockPtr(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Kind* kindM,
//      InterfaceFatPtrLE interfaceFatPtrLE) override;
//
//  ControlBlockPtrLE getControlBlockPtrWithoutChecking(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Kind* kindM,
//      InterfaceFatPtrLE interfaceFatPtrLE) override;
//
//  ControlBlockPtrLE getControlBlockPtr(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      // This will be a pointer if a mutable struct, or a fat ref if an interface.
//      Ref ref,
//      Reference* referenceM) override;
//
//   ControlBlockPtrLE getControlBlockPtr(
//       AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      // This will be a pointer if a mutable struct, or a fat ref if an interface.
//      LLVMValueRef ref,
//      Reference* referenceM) override;
//
//  ControlBlockPtrLE getControlBlockPtrWithoutChecking(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      // This will be a pointer if a mutable struct, or a fat ref if an interface.
//      LLVMValueRef ref,
//      Reference* referenceM) override;
//
//  LLVMValueRef getStructContentsPtr(
//      LLVMBuilderRef builder,
//      Kind* kind,
//      WrapperPtrLE wrapperPtrLE) override;
//
//
//  LLVMValueRef getVoidPtrFromInterfacePtr(
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Reference* virtualParamMT,
//      InterfaceFatPtrLE virtualArgLE) override;
//
//  LLVMValueRef getObjIdFromControlBlockPtr(
//      LLVMBuilderRef builder,
//      Kind* kindM,
//      ControlBlockPtrLE controlBlockPtr) override;
//
//  LLVMValueRef getStrongRcPtrFromControlBlockPtr(
//      LLVMBuilderRef builder,
//      Reference* refM,
//      ControlBlockPtrLE controlBlockPtr) override;
//
//  LLVMValueRef getStrongRcFromControlBlockPtr(
//      LLVMBuilderRef builder,
//      Reference* refM,
//      ControlBlockPtrLE controlBlockPtr) override;
//
//  LLVMValueRef downcastPtr(LLVMBuilderRef builder, Reference* resultStructRefMT, LLVMValueRef unknownPossibilityPtrLE) override;
//
//private:
//  GlobalState* globalState = nullptr;
//  GetKindStructsSource getKindStructsSource;
//};
//
//// This is a class that wraps three Structses into one, and routes calls to them based on the
//// kind's Mutability and Weakability.
//class WeakRefStructsRouter : public KindStructs {
//public:
//  explicit WeakRefStructsRouter(GetWeakRefStructsSource getWeakRefStructsSource_)
//    : getWeakRefStructsSource(getWeakRefStructsSource_) {}
//
//  LLVMTypeRef getStructWeakRefStruct(StructKind* structKind) override;
//  LLVMTypeRef getStaticSizedArrayWeakRefStruct(StaticSizedArrayT* ssaMT) override;
//  LLVMTypeRef getRuntimeSizedArrayWeakRefStruct(RuntimeSizedArrayT* rsaMT) override;
//  LLVMTypeRef getInterfaceWeakRefStruct(InterfaceKind* interfaceKind) override;
//  WeakFatPtrLE makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) override;
//  LLVMTypeRef getWeakRefHeaderStruct(Kind* kind) override;
//  LLVMTypeRef getWeakVoidRefStruct(Kind* kind) override;
//  WeakFatPtrLE downcastWeakFatPtr(
//      LLVMBuilderRef builder,
//      StructKind* targetStructKind,
//      Reference* targetRefMT,
//      LLVMValueRef sourceWeakFatPtrLE) override;
//
//private:
//  GetWeakRefStructsSource getWeakRefStructsSource;
//};

#endif
