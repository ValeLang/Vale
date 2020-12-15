
#include <region/common/defaultimmutables/defaultimmutables.h>
#include "structsrouter.h"

ReferendStructsRouter::ReferendStructsRouter(
    GlobalState* globalState_,
    GetReferendStructsSource getReferendStructsSource_)
  : globalState(globalState_),
    getReferendStructsSource(getReferendStructsSource_) {}

ControlBlock* ReferendStructsRouter::getControlBlock(Referend* referend) {
  return getReferendStructsSource(referend)->getControlBlock(referend);
}
LLVMTypeRef ReferendStructsRouter::getInnerStruct(StructReferend* structReferend) {
  return getReferendStructsSource(structReferend)->getInnerStruct(structReferend);
}
LLVMTypeRef ReferendStructsRouter::getWrapperStruct(StructReferend* structReferend) {
  return getReferendStructsSource(structReferend)->getWrapperStruct(structReferend);
}
LLVMTypeRef ReferendStructsRouter::getKnownSizeArrayWrapperStruct(KnownSizeArrayT* ksaMT) {
  return getReferendStructsSource(ksaMT)->getKnownSizeArrayWrapperStruct(ksaMT);
}
LLVMTypeRef ReferendStructsRouter::getUnknownSizeArrayWrapperStruct(UnknownSizeArrayT* usaMT) {
  return getReferendStructsSource(usaMT)->getUnknownSizeArrayWrapperStruct(usaMT);
}
LLVMTypeRef ReferendStructsRouter::getInterfaceRefStruct(InterfaceReferend* interfaceReferend) {
  return getReferendStructsSource(interfaceReferend)->getInterfaceRefStruct(interfaceReferend);
}
LLVMTypeRef ReferendStructsRouter::getInterfaceTableStruct(InterfaceReferend* interfaceReferend) {
  return getReferendStructsSource(interfaceReferend)->getInterfaceTableStruct(interfaceReferend);
}
LLVMTypeRef ReferendStructsRouter::getStringWrapperStruct() {
  return getReferendStructsSource(globalState->metalCache.str)->getStringWrapperStruct();
}
void ReferendStructsRouter::translateStruct(StructDefinition* structM, std::vector<LLVMTypeRef> membersLT) {
  return getReferendStructsSource(structM->referend)->translateStruct(structM, membersLT);
}
void ReferendStructsRouter::declareStruct(StructDefinition* structM) {
  return getReferendStructsSource(structM->referend)->declareStruct(structM);
}
void ReferendStructsRouter::declareEdge(Edge* edge) {
  return getReferendStructsSource(edge->structName)->declareEdge(edge);
}
void ReferendStructsRouter::translateEdge(
    Edge* edge,
    std::vector<LLVMTypeRef> interfaceFunctionsLT,
    std::vector<LLVMValueRef> functions) {
  return getReferendStructsSource(edge->structName)->translateEdge(edge, interfaceFunctionsLT, functions);
}
void ReferendStructsRouter::declareInterface(InterfaceDefinition* interfaceM) {
  return getReferendStructsSource(interfaceM->referend)->declareInterface(interfaceM);
}
void ReferendStructsRouter::translateInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) {
  return getReferendStructsSource(interface->referend)->translateInterface(interface, interfaceMethodTypesL);
}
void ReferendStructsRouter::declareKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT) {
  return getReferendStructsSource(knownSizeArrayMT)->declareKnownSizeArray(knownSizeArrayMT);
}
void ReferendStructsRouter::declareUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT) {
  return getReferendStructsSource(unknownSizeArrayMT)->declareUnknownSizeArray(unknownSizeArrayMT);
}
void ReferendStructsRouter::translateUnknownSizeArray(UnknownSizeArrayT* unknownSizeArrayMT, LLVMTypeRef elementLT) {
  return getReferendStructsSource(unknownSizeArrayMT)->translateUnknownSizeArray(unknownSizeArrayMT, elementLT);
}
void ReferendStructsRouter::translateKnownSizeArray(KnownSizeArrayT* knownSizeArrayMT, LLVMTypeRef elementLT) {
  return getReferendStructsSource(knownSizeArrayMT)->translateKnownSizeArray(knownSizeArrayMT, elementLT);
}

ControlBlockPtrLE ReferendStructsRouter::getConcreteControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* reference,
    WrapperPtrLE wrapperPtrLE) {
  return getReferendStructsSource(reference->referend)->getConcreteControlBlockPtr(from, functionState, builder, reference, wrapperPtrLE);
}


WrapperPtrLE ReferendStructsRouter::makeWrapperPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    LLVMValueRef ptrLE) {
  return getReferendStructsSource(referenceM->referend)->makeWrapperPtr(checkerAFL, functionState, builder, referenceM, ptrLE);
}

InterfaceFatPtrLE ReferendStructsRouter::makeInterfaceFatPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    LLVMValueRef ptrLE) {
  return getReferendStructsSource(referenceM->referend)->makeInterfaceFatPtr(checkerAFL, functionState, builder, referenceM, ptrLE);
}

InterfaceFatPtrLE ReferendStructsRouter::makeInterfaceFatPtrWithoutChecking(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    LLVMValueRef ptrLE) {
  return getReferendStructsSource(referenceM->referend)->makeInterfaceFatPtrWithoutChecking(checkerAFL, functionState, builder, referenceM, ptrLE);
}

//ControlBlockPtrLE ReferendStructsRouter::makeControlBlockPtr(
//    AreaAndFileAndLine checkerAFL,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Referend* referendM,
//    LLVMValueRef ptrLE) {
//  return getReferendStructsSource(referendM)->makeControlBlockPtr(checkerAFL, functionState, builder, referendM, ptrLE);
//}

LLVMValueRef ReferendStructsRouter::getStringBytesPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref ref) {
  return getReferendStructsSource(globalState->metalCache.str)->getStringBytesPtr(functionState, builder, ref);
}

LLVMValueRef ReferendStructsRouter::getStringLen(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) {
  return getReferendStructsSource(globalState->metalCache.str)->getStringLen(functionState, builder, ref);
}

ControlBlockPtrLE ReferendStructsRouter::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    InterfaceFatPtrLE interfaceFatPtrLE) {
  return getReferendStructsSource(referendM)->getControlBlockPtr(from, functionState, builder, referendM, interfaceFatPtrLE);
}

ControlBlockPtrLE ReferendStructsRouter::getControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    InterfaceFatPtrLE interfaceFatPtrLE) {
  return getReferendStructsSource(referendM)->getControlBlockPtrWithoutChecking(from, functionState, builder, referendM, interfaceFatPtrLE);
}

ControlBlockPtrLE ReferendStructsRouter::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    Ref ref,
    Reference* referenceM) {
  return getReferendStructsSource(referenceM->referend)->getControlBlockPtr(from, functionState, builder, ref, referenceM);
}

LLVMValueRef ReferendStructsRouter::getStructContentsPtr(
    LLVMBuilderRef builder,
    Referend* referend,
    WrapperPtrLE wrapperPtrLE) {
  return getReferendStructsSource(referend)->getStructContentsPtr(builder, referend, wrapperPtrLE);
}

ControlBlockPtrLE ReferendStructsRouter::getControlBlockPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  return getReferendStructsSource(referenceM->referend)->getControlBlockPtr(from, functionState, builder, ref, referenceM);
}

ControlBlockPtrLE ReferendStructsRouter::getControlBlockPtrWithoutChecking(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM) {
  return getReferendStructsSource(referenceM->referend)->getControlBlockPtrWithoutChecking(
      from, functionState, builder, ref, referenceM);
}

LLVMValueRef ReferendStructsRouter::getVoidPtrFromInterfacePtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE) {
  return getReferendStructsSource(virtualParamMT->referend)->getVoidPtrFromInterfacePtr(functionState, builder, virtualParamMT, virtualArgLE);
}

LLVMValueRef ReferendStructsRouter::getObjIdFromControlBlockPtr(
    LLVMBuilderRef builder,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtr) {
  return getReferendStructsSource(referendM)->getObjIdFromControlBlockPtr(builder, referendM, controlBlockPtr);
}

LLVMValueRef ReferendStructsRouter::getStrongRcPtrFromControlBlockPtr(
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
  return getReferendStructsSource(refM->referend)->getStrongRcPtrFromControlBlockPtr(builder, refM, controlBlockPtr);
}

LLVMValueRef ReferendStructsRouter::getStrongRcFromControlBlockPtr(
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
  return getReferendStructsSource(refM->referend)->getStrongRcFromControlBlockPtr(builder, refM, controlBlockPtr);
}


LLVMTypeRef WeakRefStructsRouter::getStructWeakRefStruct(StructReferend* structReferend) {
  return getWeakRefStructsSource(structReferend)->getStructWeakRefStruct(structReferend);
}
LLVMTypeRef WeakRefStructsRouter::getKnownSizeArrayWeakRefStruct(KnownSizeArrayT* ksaMT) {
  return getWeakRefStructsSource(ksaMT)->getKnownSizeArrayWeakRefStruct(ksaMT);
}
LLVMTypeRef WeakRefStructsRouter::getUnknownSizeArrayWeakRefStruct(UnknownSizeArrayT* usaMT) {
  return getWeakRefStructsSource(usaMT)->getUnknownSizeArrayWeakRefStruct(usaMT);
}
LLVMTypeRef WeakRefStructsRouter::getInterfaceWeakRefStruct(InterfaceReferend* interfaceReferend) {
  return getWeakRefStructsSource(interfaceReferend)->getInterfaceWeakRefStruct(interfaceReferend);
}
WeakFatPtrLE WeakRefStructsRouter::makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) {
  return getWeakRefStructsSource(referenceM_->referend)->makeWeakFatPtr(referenceM_, ptrLE);
}

LLVMTypeRef WeakRefStructsRouter::getWeakRefHeaderStruct(Referend* referend) {
  return getWeakRefStructsSource(referend)->getWeakRefHeaderStruct(referend);
}
LLVMTypeRef WeakRefStructsRouter::getWeakVoidRefStruct(Referend* referend) {
  return getWeakRefStructsSource(referend)->getWeakVoidRefStruct(referend);
}