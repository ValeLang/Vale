#ifndef EXPRESSIONS_H_
#define EXPRESSIONS_H_

#include <llvm-c/Core.h>
#include <functional>
#include <unordered_map>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"

LLVMValueRef translateBlock(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* parentBlockState,
    LLVMBuilderRef builder,
    Block* block);

LLVMValueRef translateDestructure(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Destroy* destructureM);

LLVMValueRef translateConstruct(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* desiredReference,
    const std::vector<LLVMValueRef>& membersLE);

LLVMValueRef translateCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Call* call);

LLVMValueRef translateInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    InterfaceCall* call);

LLVMValueRef translateExternCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    ExternCall* expr);

LLVMValueRef translateIf(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    If* iff);

LLVMValueRef translateWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    While* whiile);

LLVMValueRef translateDiscard(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Discard* discardM);

LLVMValueRef translateNewArrayFromValues(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    NewArrayFromValues* newArrayFromValues);

LLVMValueRef translateConstructUnknownSizeArray(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    ConstructUnknownSizeArray* constructUnknownSizeArray);

LLVMValueRef translateConstantStr(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    ConstantStr* constantStr);

#endif