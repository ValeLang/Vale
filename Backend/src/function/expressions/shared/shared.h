#ifndef FUNCTION_EXPRESSIONS_SHARED_SHARED_H_
#define FUNCTION_EXPRESSIONS_SHARED_SHARED_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "../../../metal/ast.h"
#include "../../../metal/instructions.h"
#include "../../../globalstate.h"
#include "../../function.h"
#include "../../../fileio.h"
#include "ref.h"


LLVMTypeRef makeNeverType(GlobalState* globalState);

LLVMValueRef makeVoid(GlobalState* globalState);
Ref makeVoidRef(GlobalState* globalState);

LLVMValueRef makeBackendLocal(
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
    Ref valueToStore,
    bool knownLive);


// Returns the new RC
LLVMValueRef adjustStrongRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* kindStructsSource,
    LLVMBuilderRef builder,
    Ref exprLE,
    Reference* refM,
    int amount);

LLVMValueRef strongRcIsZero(
    GlobalState* globalState,
    KindStructs* structs,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE exprLE);


void buildAssertV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    const std::string& failMessage);

void buildAssert(
    GlobalState* globalState,
    LLVMValueRef function,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    const std::string& failMessage);

void buildAssertWithExitCodeV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    int exitCode,
    const std::string& failMessage);

void buildAssertIntEq(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef aLE,
    LLVMValueRef bLE,
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

inline void buildPrintIndent(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder) {
  std::string indentStr = "";
  for (int i = 0; i < functionState->instructionDepthInAst; i++)
    indentStr += " ";
  buildPrint(globalState, builder, indentStr);
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

LLVMValueRef getInterfaceMethodFunctionPtrFromItable(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    InterfaceMethod* method,
    LLVMValueRef itablePtrLE);

Ref buildInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    LLVMValueRef methodFunctionPtrLE,
    std::vector<Ref> argRefs,
    int virtualParamIndex);


LLVMValueRef makeConstIntExpr(FunctionState* functionState, LLVMBuilderRef builder, LLVMTypeRef type, int64_t value);

LLVMValueRef makeConstExpr(
    FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef constExpr);

inline LLVMValueRef constI8LE(GlobalState* globalState, int n) {
  return LLVMConstInt(LLVMInt8TypeInContext(globalState->context), n, false);
}

inline LLVMValueRef constI64LE(GlobalState* globalState, int64_t n) {
  return LLVMConstInt(LLVMInt64TypeInContext(globalState->context), n, false);
}

inline LLVMValueRef constI48LE(GlobalState* globalState, int64_t n) {
  return LLVMConstInt(LLVMIntTypeInContext(globalState->context, 48), n, false);
}

inline LLVMValueRef constI16LE(GlobalState* globalState, int64_t n) {
  return LLVMConstInt(LLVMInt16TypeInContext(globalState->context), n, false);
}

inline LLVMValueRef constI1LE(GlobalState* globalState, bool b) {
  return LLVMConstInt(LLVMInt1TypeInContext(globalState->context), b, false);
}

inline LLVMValueRef constI32LE(GlobalState* globalState, int n) {
  return LLVMConstInt(LLVMInt32TypeInContext(globalState->context), n, false);
}


void buildAssertCensusContains(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef ptrLE);

Ref buildCallV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    std::vector<Ref> argRefs);

LLVMValueRef buildCall(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef functionLE,
    std::vector<LLVMValueRef> argsLE);


LLVMValueRef addExtern(
    LLVMModuleRef mod,
    const std::string& name,
    LLVMTypeRef retType,
    std::vector<LLVMTypeRef> paramTypes);

inline LLVMValueRef ptrToVoidPtrLE(GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef ptrLE) {
  return LLVMBuildPointerCast(builder, ptrLE, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "asVoidP");
}

inline LLVMValueRef ptrToIntLE(GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef ptrLE) {
  return LLVMBuildPointerCast(builder, ptrLE, LLVMInt64TypeInContext(globalState->context), "asI64");
}

// A side call is a call using different stack memory
LLVMValueRef buildSideCall(
    GlobalState* globalState,
    LLVMTypeRef calleeFuncLT,
    LLVMBuilderRef entryBuilder,
    LLVMValueRef sideStackStartPtrAsI8PtrLE,
    LLVMValueRef calleeFuncLE,
    const std::vector<LLVMValueRef>& userArgsLE);

#endif
