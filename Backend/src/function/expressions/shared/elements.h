#ifndef FUNCTION_EXPRESSIONS_SHARED_ELEMENTS_H_
#define FUNCTION_EXPRESSIONS_SHARED_ELEMENTS_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "../../../metal/ast.h"
#include "../../../metal/instructions.h"
#include "../../../globalstate.h"
#include "../../function.h"

//Ref loadElementWithUpgrade(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    BlockState* blockState,
//    LLVMBuilderRef builder,
//    Reference* arrayRefM,
//    Reference* elementRefM,
//    Ref sizeLE,
//    LLVMValueRef arrayPtrLE,
//    Mutability mutability,
//    Ref indexLE,
//    Reference* resultRefM);

InBoundsLE checkIndexInBounds(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* intRefMT,
    Ref sizeRef,
    LLVMValueRef indexLE,
    const char* failMessage);

InBoundsLE checkLastElementExists(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sizeRef,
    const char* failMessage);

void checkArrayEmpty(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sizeRef,
    const char* failMessage);

//void initializeElementAndIncrementSize(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Location location,
//    Reference* elementRefM,
//    LLVMValueRef sizePtrLE,
//    LLVMValueRef elemsPtrLE,
//    Ref sourceRef);

struct IncrementedSize {};

void initializeElementWithoutIncrementSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    Reference* elementRefM,
    LLVMValueRef elemsPtrLE,
    InBoundsLE indexRef,
    Ref sourceRef,
    IncrementedSize incrementedSize);

Ref swapElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    Reference* elementRefM,
    LLVMValueRef arrayPtrLE,
    InBoundsLE indexLE,
    Ref sourceLE);

LoadResult loadElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef elemsPtrLE,
    Reference* elementRefM,
    InBoundsLE indexLE);

void intRangeLoop(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sizeRef,
    std::function<void(LLVMValueRef, LLVMBuilderRef)> iterationBuilder);

void intRangeLoopV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sizeRef,
    std::function<void(Ref, LLVMBuilderRef)> iterationBuilder);

void intRangeLoopReverse(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* intRefMT,
    LLVMValueRef sizeLE,
    std::function<void(LLVMValueRef, LLVMBuilderRef)> iterationBuilder);

void intRangeLoopReverseV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* intRefMT,
    Ref sizeRef,
    std::function<void(Ref, LLVMBuilderRef)> iterationBuilder);

LLVMValueRef getStaticSizedArrayContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE staticSizedArrayWrapperPtrLE);

LLVMValueRef getRuntimeSizedArrayContentsPtr(
    LLVMBuilderRef builder,
    bool capacityExists,
    WrapperPtrLE arrayWrapperPtrLE);

LLVMValueRef getRuntimeSizedArrayLengthPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    WrapperPtrLE runtimeSizedArrayWrapperPtrLE);

LLVMValueRef getRuntimeSizedArrayCapacityPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    WrapperPtrLE runtimeSizedArrayWrapperPtrLE);

void decrementRSASize(
    GlobalState* globalState, FunctionState *functionState, KindStructs* kindStructs, LLVMBuilderRef builder, Reference *rsaRefMT, WrapperPtrLE rsaWrapperPtrLE);

// Returns a ptr to the address it just wrote to
void storeInnerArrayMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMTypeRef elementLT,
    LLVMValueRef elemsPtrLE,
    InBoundsLE indexLE,
    LLVMValueRef sourceLE);

LoadResult loadElementFromSSAInner(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* elementType,
    InBoundsLE indexLE,
    LLVMValueRef arrayElementsPtrLE);

Ref getRuntimeSizedArrayLength(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE arrayRefLE);

void regularInitializeElementInSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* ssaRefMT,
    Reference* elementType,
    LiveRef arrayRef,
    InBoundsLE indexLE,
    Ref elementRef);

LoadResult regularloadElementFromSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    Reference* elementType,
    LiveRef arrayRef,
    InBoundsLE indexLE,
    KindStructs* kindStructs);

IncrementedSize incrementRSASize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    WrapperPtrLE rsaWPtrLE);

void initializeElementInRSAWithoutIncrementSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool capacityExists,
    RuntimeSizedArrayT* rsaMT,
    Reference* rsaRefMT,
    WrapperPtrLE rsaWPtrLE,
    InBoundsLE indexLE,
    Ref elementRef,
    IncrementedSize);

LoadResult regularLoadElementFromRSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    bool capacityExists,
    Reference* rsaRefMT,
    Reference* elementType,
    LiveRef arrayRef,
    InBoundsLE indexRef);

#endif
