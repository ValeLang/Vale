
#include "../../rcimm/rcimm.h"
#include "structsrouter.h"

//KindStructsRouter::KindStructsRouter(
//    GlobalState* globalState_,
//    GetKindStructsSource getKindStructsSource_)
//  : globalState(globalState_),
//    getKindStructsSource(getKindStructsSource_) {}
//
//ControlBlock* KindStructsRouter::getControlBlock(Kind* kind) {
//  return getKindStructsSource(kind)->getControlBlock(kind);
//}
//LLVMTypeRef KindStructsRouter::getInnerStruct(StructKind* structKind) {
//  return getKindStructsSource(structKind)->getInnerStruct(structKind);
//}
//LLVMTypeRef KindStructsRouter::getWrapperStruct(StructKind* structKind) {
//  return getKindStructsSource(structKind)->getWrapperStruct(structKind);
//}
//LLVMTypeRef KindStructsRouter::getStaticSizedArrayWrapperStruct(StaticSizedArrayT* ssaMT) {
//  return getKindStructsSource(ssaMT)->getStaticSizedArrayWrapperStruct(ssaMT);
//}
//LLVMTypeRef KindStructsRouter::getRuntimeSizedArrayWrapperStruct(RuntimeSizedArrayT* rsaMT) {
//  return getKindStructsSource(rsaMT)->getRuntimeSizedArrayWrapperStruct(rsaMT);
//}
//LLVMTypeRef KindStructsRouter::getInterfaceRefStruct(InterfaceKind* interfaceKind) {
//  return getKindStructsSource(interfaceKind)->getInterfaceRefStruct(interfaceKind);
//}
//LLVMTypeRef KindStructsRouter::getInterfaceTableStruct(InterfaceKind* interfaceKind) {
//  return getKindStructsSource(interfaceKind)->getInterfaceTableStruct(interfaceKind);
//}
//LLVMTypeRef KindStructsRouter::getStringWrapperStruct() {
//  return getKindStructsSource(globalState->metalCache->str)->getStringWrapperStruct();
//}
//void KindStructsRouter::defineStruct(StructKind* structM, std::vector<LLVMTypeRef> membersLT) {
//  return getKindStructsSource(structM)->defineStruct(structM, membersLT);
//}
//void KindStructsRouter::declareStruct(StructKind* structM) {
//  return getKindStructsSource(structM)->declareStruct(structM);
//}
//void KindStructsRouter::declareEdge(Edge* edge) {
//  return getKindStructsSource(edge->structName)->declareEdge(edge);
//}
//void KindStructsRouter::defineEdge(
//    Edge* edge,
//    std::vector<LLVMTypeRef> interfaceFunctionsLT,
//    std::vector<LLVMValueRef> functions) {
//  return getKindStructsSource(edge->structName)->defineEdge(edge, interfaceFunctionsLT, functions);
//}
//void KindStructsRouter::declareInterface(InterfaceDefinition* interfaceM) {
//  return getKindStructsSource(interfaceM->kind)->declareInterface(interfaceM);
//}
//void KindStructsRouter::defineInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) {
//  return getKindStructsSource(interface->kind)->defineInterface(interface, interfaceMethodTypesL);
//}
//void KindStructsRouter::declareStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayMT) {
//  return getKindStructsSource(staticSizedArrayMT->kind)->declareStaticSizedArray(staticSizedArrayMT);
//}
//void KindStructsRouter::declareRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) {
//  return getKindStructsSource(runtimeSizedArrayMT->kind)->declareRuntimeSizedArray(runtimeSizedArrayMT);
//}
//void KindStructsRouter::defineRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT, LLVMTypeRef elementLT) {
//  return getKindStructsSource(runtimeSizedArrayMT->kind)->defineRuntimeSizedArray(runtimeSizedArrayMT, elementLT);
//}
//void KindStructsRouter::defineStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayMT, LLVMTypeRef elementLT) {
//  return getKindStructsSource(staticSizedArrayMT->kind)->defineStaticSizedArray(staticSizedArrayMT, elementLT);
//}
//
//ControlBlockPtrLE KindStructsRouter::getConcreteControlBlockPtr(
//    AreaAndFileAndLine from,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* reference,
//    WrapperPtrLE wrapperPtrLE) {
//  return getKindStructsSource(reference->kind)->getConcreteControlBlockPtr(from, functionState, builder, reference, wrapperPtrLE);
//}
//
//
//WrapperPtrLE KindStructsRouter::makeWrapperPtr(
//    AreaAndFileAndLine checkerAFL,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* referenceM,
//    LLVMValueRef ptrLE) {
//  return getKindStructsSource(referenceM->kind)->makeWrapperPtr(checkerAFL, functionState, builder, referenceM, ptrLE);
//}
//
//InterfaceFatPtrLE KindStructsRouter::makeInterfaceFatPtr(
//    AreaAndFileAndLine checkerAFL,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* referenceM,
//    LLVMValueRef ptrLE) {
//  return getKindStructsSource(referenceM->kind)->makeInterfaceFatPtr(checkerAFL, functionState, builder, referenceM, ptrLE);
//}
//
//InterfaceFatPtrLE KindStructsRouter::makeInterfaceFatPtrWithoutChecking(
//    AreaAndFileAndLine checkerAFL,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* referenceM,
//    LLVMValueRef ptrLE) {
//  return getKindStructsSource(referenceM->kind)->makeInterfaceFatPtrWithoutChecking(checkerAFL, functionState, builder, referenceM, ptrLE);
//}
//
////ControlBlockPtrLE KindStructsRouter::makeControlBlockPtr(
////    AreaAndFileAndLine checkerAFL,
////    FunctionState* functionState,
////    LLVMBuilderRef builder,
////    Kind* kindM,
////    LLVMValueRef ptrLE) {
////  return getKindStructsSource(kindM)->makeControlBlockPtr(checkerAFL, functionState, builder, kindM, ptrLE);
////}
//
//LLVMValueRef KindStructsRouter::getStringBytesPtr(
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    WrapperPtrLE ptrLE) {
//  return getKindStructsSource(globalState->metalCache->str)->getStringBytesPtr(functionState, builder, ptrLE);
//}
//
//LLVMValueRef KindStructsRouter::getStringLen(FunctionState* functionState, LLVMBuilderRef builder, WrapperPtrLE ptrLE) {
//  return getKindStructsSource(globalState->metalCache->str)->getStringLen(functionState, builder, ptrLE);
//}
//
//ControlBlockPtrLE KindStructsRouter::getControlBlockPtr(
//    AreaAndFileAndLine from,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Kind* kindM,
//    InterfaceFatPtrLE interfaceFatPtrLE) {
//  return getKindStructsSource(kindM)->getControlBlockPtr(from, functionState, builder, kindM, interfaceFatPtrLE);
//}
//
//ControlBlockPtrLE KindStructsRouter::getControlBlockPtrWithoutChecking(
//    AreaAndFileAndLine from,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Kind* kindM,
//    InterfaceFatPtrLE interfaceFatPtrLE) {
//  return getKindStructsSource(kindM)->getControlBlockPtrWithoutChecking(from, functionState, builder, kindM, interfaceFatPtrLE);
//}
//
//ControlBlockPtrLE KindStructsRouter::getControlBlockPtr(
//    AreaAndFileAndLine from,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    // This will be a pointer if a mutable struct, or a fat ref if an interface.
//    Ref ref,
//    Reference* referenceM) {
//  return getKindStructsSource(referenceM->kind)->getControlBlockPtr(from, functionState, builder, ref, referenceM);
//}
//
//LLVMValueRef KindStructsRouter::getStructContentsPtr(
//    LLVMBuilderRef builder,
//    Kind* kind,
//    WrapperPtrLE wrapperPtrLE) {
//  return getKindStructsSource(kind)->getStructContentsPtr(builder, kind, wrapperPtrLE);
//}
//
//ControlBlockPtrLE KindStructsRouter::getControlBlockPtr(
//    AreaAndFileAndLine from,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    // This will be a pointer if a mutable struct, or a fat ref if an interface.
//    LLVMValueRef ref,
//    Reference* referenceM) {
//  return getKindStructsSource(referenceM->kind)->getControlBlockPtr(from, functionState, builder, ref, referenceM);
//}
//
//ControlBlockPtrLE KindStructsRouter::getControlBlockPtrWithoutChecking(
//    AreaAndFileAndLine from,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    // This will be a pointer if a mutable struct, or a fat ref if an interface.
//    LLVMValueRef ref,
//    Reference* referenceM) {
//  return getKindStructsSource(referenceM->kind)->getControlBlockPtrWithoutChecking(
//      from, functionState, builder, ref, referenceM);
//}
//
//LLVMValueRef KindStructsRouter::getVoidPtrFromInterfacePtr(
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* virtualParamMT,
//    InterfaceFatPtrLE virtualArgLE) {
//  return getKindStructsSource(virtualParamMT->kind)->getVoidPtrFromInterfacePtr(functionState, builder, virtualParamMT, virtualArgLE);
//}
//
//LLVMValueRef KindStructsRouter::getObjIdFromControlBlockPtr(
//    LLVMBuilderRef builder,
//    Kind* kindM,
//    ControlBlockPtrLE controlBlockPtr) {
//  return getKindStructsSource(kindM)->getObjIdFromControlBlockPtr(builder, kindM, controlBlockPtr);
//}
//
//LLVMValueRef KindStructsRouter::getStrongRcPtrFromControlBlockPtr(
//    LLVMBuilderRef builder,
//    Reference* refM,
//    ControlBlockPtrLE controlBlockPtr) {
//  return getKindStructsSource(refM->kind)->getStrongRcPtrFromControlBlockPtr(builder, refM, controlBlockPtr);
//}
//
//LLVMValueRef KindStructsRouter::getStrongRcFromControlBlockPtr(
//    LLVMBuilderRef builder,
//    Reference* refM,
//    ControlBlockPtrLE controlBlockPtr) {
//  return getKindStructsSource(refM->kind)->getStrongRcFromControlBlockPtr(builder, refM, controlBlockPtr);
//}
//
//LLVMValueRef KindStructsRouter::downcastPtr(
//    LLVMBuilderRef builder, Reference* resultStructRefMT, LLVMValueRef unknownPossibilityPtrLE) {
//  return getKindStructsSource(resultStructRefMT->kind)->downcastPtr(builder, resultStructRefMT, unknownPossibilityPtrLE);
//}
//
//
//LLVMTypeRef WeakRefStructsRouter::getStructWeakRefStruct(StructKind* structKind) {
//  return getWeakRefStructsSource(structKind)->getStructWeakRefStruct(structKind);
//}
//LLVMTypeRef WeakRefStructsRouter::getStaticSizedArrayWeakRefStruct(StaticSizedArrayT* ssaMT) {
//  return getWeakRefStructsSource(ssaMT)->getStaticSizedArrayWeakRefStruct(ssaMT);
//}
//LLVMTypeRef WeakRefStructsRouter::getRuntimeSizedArrayWeakRefStruct(RuntimeSizedArrayT* rsaMT) {
//  return getWeakRefStructsSource(rsaMT)->getRuntimeSizedArrayWeakRefStruct(rsaMT);
//}
//LLVMTypeRef WeakRefStructsRouter::getInterfaceWeakRefStruct(InterfaceKind* interfaceKind) {
//  return getWeakRefStructsSource(interfaceKind)->getInterfaceWeakRefStruct(interfaceKind);
//}
//WeakFatPtrLE WeakRefStructsRouter::makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) {
//  return getWeakRefStructsSource(referenceM_->kind)->makeWeakFatPtr(referenceM_, ptrLE);
//}
//
//
//WeakFatPtrLE WeakRefStructsRouter::downcastWeakFatPtr(
//    LLVMBuilderRef builder,
//    StructKind* targetStructKind,
//    Reference* targetRefMT,
//    LLVMValueRef sourceWeakFatPtrLE) {
//  return getWeakRefStructsSource(targetStructKind)->downcastWeakFatPtr(
//      builder, targetStructKind, targetRefMT, sourceWeakFatPtrLE);
//}
//
//LLVMTypeRef WeakRefStructsRouter::getWeakRefHeaderStruct(Kind* kind) {
//  return getWeakRefStructsSource(kind)->getWeakRefHeaderStruct(kind);
//}
//LLVMTypeRef WeakRefStructsRouter::getWeakVoidRefStruct(Kind* kind) {
//  return getWeakRefStructsSource(kind)->getWeakVoidRefStruct(kind);
//}
