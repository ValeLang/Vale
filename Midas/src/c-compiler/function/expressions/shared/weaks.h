#ifndef FUNCTION_EXPRESSIONS_SHARED_WEAKS_H_
#define FUNCTION_EXPRESSIONS_SHARED_WEAKS_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"
#include "utils/fileio.h"
#include "shared.h"

void aliasWeakRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE);

void discardWeakRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE);

// Doesn't return a constraint ref, returns a raw ref to the wrapper struct.
LLVMValueRef lockWeakRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* refM,
    LLVMValueRef weakRefLE);

LLVMValueRef getInnerRefFromWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* weakRefM,
    LLVMValueRef weakRefLE);

LLVMValueRef getIsAliveFromWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* refM,
    LLVMValueRef weakRefLE);

void noteWeakableDestroyed(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* concreteRefM,
    LLVMValueRef controlBlockPtrLE);

LLVMValueRef noteWeakableCreated(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder);


LLVMValueRef fillWeakableControlBlock(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef controlBlockLE);


LLVMValueRef weakInterfaceRefToWeakStructRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* refM,
    LLVMValueRef exprLE);

LLVMValueRef weakStructRefToWeakInterfaceRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sourceRefLE,
    StructReferend* sourceStructReferendM,
    UnconvertedReference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    UnconvertedReference* targetInterfaceTypeM);

void buildCheckWeakRef(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* weakRefM,
    LLVMValueRef weakRefLE);


LLVMValueRef assembleInterfaceWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* sourceType,
    InterfaceReferend* interfaceReferendM,
    LLVMValueRef sourceRefLE);


LLVMValueRef assembleStructWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* structTypeM,
    StructReferend* structReferendM,
    LLVMValueRef objPtrLE);

LLVMValueRef assembleKnownSizeArrayWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    UnconvertedReference* structTypeM,
    KnownSizeArrayT* knownSizeArrayMT,
    LLVMValueRef objPtrLE);

LLVMValueRef assembleUnknownSizeArrayWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* sourceType,
    UnknownSizeArrayT* unknownSizeArrayMT,
    LLVMValueRef objPtrLE);

// Used in interface calling, when we dont know what the underlying struct type is yet.
LLVMValueRef assembleVoidStructWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef controlBlockPtrLE,
    LLVMValueRef wrciLE);

// Makes the part of the weak ref that contains information on how to know if the contained thing
// is weakable.
// A "weak reference" contains this and also the actual object pointer.
void makeWeakRefStructs(GlobalState* globalState);

void initWeakInternalExterns(GlobalState* globalState);


void makeStructWeakRefStruct(GlobalState* globalState, LLVMTypeRef structWeakRefStructL, LLVMTypeRef wrapperStructL);

void makeInterfaceWeakRefStruct(GlobalState* globalState, LLVMTypeRef interfaceWeakRefStructL, LLVMTypeRef refStructL);

void makeVoidPtrWeakRefStruct(GlobalState* globalState, LLVMTypeRef weakVoidRefStructL);


void makeUnknownSizeArrayWeakRefStruct(
    GlobalState* globalState,
    LLVMTypeRef unknownSizeArrayWrapperStruct,
    LLVMTypeRef arrayWeakRefStructL);

void makeKnownSizeArrayWeakRefStruct(
    GlobalState* globalState,
    LLVMTypeRef knownSizeArrayWrapperStruct,
    LLVMTypeRef arrayWeakRefStructL);

LLVMValueRef getWrciFromWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE);

LLVMValueRef makeWrciHeader(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef wrciLE);

LLVMValueRef getHeaderFromWeakRef(
    LLVMBuilderRef builder,
    LLVMValueRef weakRefLE);

#endif
