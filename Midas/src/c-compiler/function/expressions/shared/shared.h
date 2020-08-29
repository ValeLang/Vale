#ifndef FUNCTION_EXPRESSIONS_SHARED_SHARED_H_
#define FUNCTION_EXPRESSIONS_SHARED_SHARED_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"
#include "utils/fileio.h"

struct AreaAndFileAndLine {
  std::string area;
  std::string file;
  int line;
};

// File and Line
#define FL() (AreaAndFileAndLine{ "", __FILE__, __LINE__ })
// Area and File and Line
#define AFL(area) (AreaAndFileAndLine{ (area), __FILE__, __LINE__ })

LLVMValueRef makeNever();
LLVMTypeRef makeNeverType();

void makeLocal(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Local* local,
    LLVMValueRef valueToStore);

void acquireReference(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    LLVMValueRef expr);

void discard(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    LLVMValueRef expr);


LLVMValueRef getStructContentsPtr(LLVMBuilderRef builder, LLVMValueRef structPtrLE);

LLVMValueRef adjustCounter(
    LLVMBuilderRef builder,
    LLVMValueRef counterPtrLE,
    // Amount to add. Can be negative.
    int adjustAmount);

LLVMValueRef getTablePtrFromInterfaceRef(
    LLVMBuilderRef builder,
    LLVMValueRef interfaceRefLE);

LLVMValueRef getControlBlockPtr(
    LLVMBuilderRef builder,
    // This will be a pointer if a mutable struct, or a fat ref if an interface.
    LLVMValueRef referenceLE,
    Referend* referendM);

// Returns the new RC
LLVMValueRef adjustStrongRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE,
    Reference* refM,
    int amount);

LLVMValueRef strongRcIsZero(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef exprLE);

LLVMValueRef isZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE);
LLVMValueRef isNonZeroLE(LLVMBuilderRef builder, LLVMValueRef intLE);


void buildAssert(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    const std::string& failMessage);


void buildPrint(GlobalState* globalState, LLVMBuilderRef builder, const std::string& first);
void buildPrint(GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef exprLE);
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

LLVMValueRef buildInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    std::vector<LLVMValueRef> argExprsLE,
    int virtualParamIndex,
    int indexInEdge);


LLVMValueRef makeConstIntExpr(LLVMBuilderRef builder, LLVMTypeRef type, int value);

LLVMValueRef makeConstExpr(LLVMBuilderRef builder, LLVMValueRef constExpr);

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

void checkValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef refLE);


LLVMValueRef buildCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    std::vector<LLVMValueRef> argsLE);

LLVMValueRef upcast2(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    LLVMValueRef sourceRefLE,
    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM);

// TODO move these into region classes
Ownership getEffectiveOwnership(GlobalState* globalState, UnconvertedOwnership ownership);
Reference* getEffectiveType(GlobalState* globalState, UnconvertedReference* refM);
Weakability getEffectiveWeakability(GlobalState* globalState, RawArrayT* array);
Weakability getEffectiveWeakability(GlobalState* globalState, StructDefinition* structDef);
Weakability getEffectiveWeakability(GlobalState* globalState, InterfaceDefinition* interfaceDef);
std::vector<Reference*> getEffectiveTypes(GlobalState* globalState, std::vector<UnconvertedReference*> refsM);

// Loads from either a local or a member, and does the appropriate casting.
LLVMValueRef load(
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
    LLVMValueRef controlBlockPtrLE);


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
    Reference* sourceTypeM,
    LLVMValueRef exprLE);

#endif
