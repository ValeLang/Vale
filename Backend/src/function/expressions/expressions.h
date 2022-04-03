#ifndef EXPRESSIONS_H_
#define EXPRESSIONS_H_

#include <llvm-c/Core.h>
#include <functional>
#include <unordered_map>
#include "shared/ref.h"

#include "../../metal/ast.h"
#include "../../metal/instructions.h"
#include "../../globalstate.h"
#include "../function.h"
#include "../expression.h"
#include "shared/shared.h"

Ref translateBlock(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* parentBlockState,
    LLVMBuilderRef builder,
    Block* block);

Ref translateDestructure(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Destroy* destructureM);

Ref translateConstruct(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* desiredStructMT,
    const std::vector<Ref>& memberRefs);

Ref translateCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Call* call);

Ref translateInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    InterfaceCall* call);

Ref translateExternCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    ExternCall* expr);

Ref buildExternCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    const std::vector<Ref>& args);

Ref translateIf(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    If* iff);

Ref translateWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    While* whiile);

Ref translateDiscard(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Discard* discardM);

Ref translateNewArrayFromValues(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    NewArrayFromValues* newArrayFromValues);

Ref translateStaticArrayFromCallable(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    StaticArrayFromCallable* staticArrayFromCallable);

Ref translateNewImmRuntimeSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    NewImmRuntimeSizedArray* constructRuntimeSizedArray);

Ref translateNewMutRuntimeSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    NewMutRuntimeSizedArray* constructRuntimeSizedArray);

Ref translateConstantStr(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    ConstantStr* constantStr);

Ref translateLocalLoad(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    LocalLoad* localLoad);

#endif