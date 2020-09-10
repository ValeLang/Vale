#ifndef FUNCTION_EXPRESSIONS_SHARED_SHARED_H_
#define FUNCTION_EXPRESSIONS_SHARED_SHARED_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"
#include "fileio.h"
#include "ref.h"


LLVMTypeRef makeNeverType();

LLVMValueRef makeEmptyTuple(
    GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder);

Ref makeEmptyTupleRef(
    GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder);

LLVMValueRef makeMidasLocal(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMTypeRef typeL,
    const std::string& name,
    LLVMValueRef valueToStore);

void makeHammerLocal(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Local* local,
    Ref valueToStore);


LLVMValueRef getTablePtrFromInterfaceRef(
    LLVMBuilderRef builder,
    InterfaceFatPtrLE interfaceFatPtrLE);

ControlBlockPtrLE getControlBlockPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef ref,
    Reference* referenceM);

ControlBlockPtrLE getControlBlockPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    Ref referenceLE,
    Reference* referenceM);

// Returns the new RC
LLVMValueRef adjustStrongRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref exprLE,
    Reference* refM,
    int amount);

LLVMValueRef strongRcIsZero(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE exprLE);


void buildAssert(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    const std::string& failMessage);


void buildPrint(GlobalState* globalState, LLVMBuilderRef builder, const std::string& first);
void buildPrint(GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef exprLE);
void buildPrint(GlobalState* globalState, LLVMBuilderRef builder, Ref ref);
void buildPrint(GlobalState* globalState, LLVMBuilderRef builder, int num);

template<typename First, typename... Rest>
inline void buildFlareInner(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    First&& first,
    Rest&&... rest) {
  buildPrint(globalState, builder, std::forward<First>(first));
  buildFlareInner(globalState, builder, std::forward<Rest>(rest)...);
}

inline void buildFlareInner(
    GlobalState* globalState,
    LLVMBuilderRef builder) { }

inline void buildPrintAreaAndFileAndLine(GlobalState* globalState, LLVMBuilderRef builder, AreaAndFileAndLine from) {
  buildPrint(globalState, builder, "\033[0;34m");
  buildPrint(globalState, builder, getFileName(from.file));
  buildPrint(globalState, builder, ":");
  buildPrint(globalState, builder, from.line);
  buildPrint(globalState, builder, "\033[0m");
  buildPrint(globalState, builder, " ");
  if (!from.area.empty()) {
    buildPrint(globalState, builder, getFileName(from.area));
    buildPrint(globalState, builder, ": ");
  }
}

template<typename... T>
inline void buildFlare(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    T&&... rest) {
  if (globalState->opt->flares) {
    std::string indentStr = "";
    for (int i = 0; i < functionState->instructionDepthInAst; i++)
      indentStr += " ";

    buildPrint(globalState, builder, indentStr);
    buildPrintAreaAndFileAndLine(globalState, builder, from);
    buildFlareInner(globalState, builder, std::forward<T>(rest)...);
    buildPrint(globalState, builder, "\n");
  }
}

Ref buildInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    std::vector<Ref> argRefs,
    int virtualParamIndex,
    int indexInEdge);


LLVMValueRef makeConstIntExpr(FunctionState* functionState, LLVMBuilderRef builder, LLVMTypeRef type, int value);

LLVMValueRef makeConstExpr(
    FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef constExpr);

inline LLVMValueRef constI64LE(int n) {
  return LLVMConstInt(LLVMInt64Type(), n, false);
}

inline LLVMValueRef constI32LE(int n) {
  return LLVMConstInt(LLVMInt32Type(), n, false);
}


void buildAssertCensusContains(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef ptrLE);

LLVMValueRef checkValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref refLE);


Ref buildCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    std::vector<Ref> argRefs);

// TODO move these into region classes
Weakability getEffectiveWeakability(GlobalState* globalState, RawArrayT* array);
Weakability getEffectiveWeakability(GlobalState* globalState, StructDefinition* structDef);
Weakability getEffectiveWeakability(GlobalState* globalState, InterfaceDefinition* interfaceDef);

// Loads from either a local or a member, and does the appropriate casting.
Ref upgradeLoadResultToRefWithTargetOwnership(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    LLVMValueRef sourceRefLE);

LLVMValueRef makeInterfaceRefStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructReferend* sourceStructReferendM,
    InterfaceReferend* targetInterfaceReferendM,
    ControlBlockPtrLE controlBlockPtrLE);


LLVMValueRef addExtern(
    LLVMModuleRef mod,
    const std::string& name,
    LLVMTypeRef retType,
    std::vector<LLVMTypeRef> paramTypes);

inline LLVMValueRef ptrToVoidPtrLE(LLVMBuilderRef builder, LLVMValueRef ptrLE) {
  return LLVMBuildPointerCast(builder, ptrLE, LLVMPointerType(LLVMVoidType(), 0), "asVoidP");
}

void discardOwningRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef);

Ref upcast(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructMT,
    StructReferend* sourceStructReferendM,
    Ref sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM);

#endif
