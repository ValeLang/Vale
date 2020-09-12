#ifndef FUNCTION_EXPRESSIONS_SHARED_WEAKS_H_
#define FUNCTION_EXPRESSIONS_SHARED_WEAKS_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"
#include "fileio.h"
#include "shared.h"
//
//void aliasWeakRef(
//    AreaAndFileAndLine from,
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* weakRefMT,
//    Ref weakRef);
//
//void discardWeakRef(
//    AreaAndFileAndLine from,
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* exprMT,
//    Ref exprLE);
//
//WrapperPtrLE lockWeakRef(
//    AreaAndFileAndLine from,
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* refM,
//    Ref weakRefLE);
//
//
//LLVMValueRef getIsAliveFromWeakFatPtr(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* weakRefM,
//    WeakFatPtrLE weakFatPtrLE);
//
//Ref getIsAliveFromWeakRef(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* refM,
//    Ref weakRefLE);
//
//void innerNoteWeakableDestroyed(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* concreteRefM,
//    ControlBlockPtrLE controlBlockPtrLE);
//
//LLVMValueRef noteWeakableCreated(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder);
//
//
//LLVMValueRef fillWeakableControlBlock(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Referend* referendM,
//    LLVMValueRef controlBlockLE);
//
//
//WeakFatPtrLE weakInterfaceRefToWeakStructRef(
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* refM,
//    WeakFatPtrLE exprLE);
//
//void buildCheckWeakRef(
//    AreaAndFileAndLine checkerAFL,
//    GlobalState* globalState,
//    FunctionState* functionState,
//    LLVMBuilderRef builder,
//    Reference* weakRefM,
//    Ref weakRefLE);
//
//
//// Used in interface calling, when we dont know what the underlying struct type is yet.
//WeakFatPtrLE assembleVoidStructWeakRef(
//    GlobalState* globalState,
//    LLVMBuilderRef builder,
//    Reference* refM,
//    ControlBlockPtrLE controlBlockPtrLE,
//    LLVMValueRef wrciLE);
//
//LLVMValueRef getWrciFromWeakRef(
//    GlobalState* globalState,
//    LLVMBuilderRef builder,
//    WeakFatPtrLE weakRefLE);
//
//LLVMValueRef makeWrciHeader(
//    GlobalState* globalState,
//    LLVMBuilderRef builder,
//    LLVMValueRef wrciLE);
//
//LLVMValueRef getHeaderFromWeakRef(
//    LLVMBuilderRef builder,
//    WeakFatPtrLE weakRefLE);

#endif
