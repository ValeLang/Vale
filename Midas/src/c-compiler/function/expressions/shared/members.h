#ifndef FUNCTION_EXPRESSIONS_SHARED_MEMBERS_H_
#define FUNCTION_EXPRESSIONS_SHARED_MEMBERS_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"
#include "shared.h"

LLVMValueRef loadMember(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    UnconvertedReference* structRefM,
    LLVMValueRef structRefLE,
    Mutability containingStructMutability,
    UnconvertedReference* memberType,
    int memberIndex,
    UnconvertedReference* resultType,
    const std::string& memberName);

LLVMValueRef loadInnerStructMember(
    LLVMBuilderRef builder,
    LLVMValueRef innerStructPtrLE,
    int memberIndex,
    const std::string& memberName);

LLVMValueRef swapMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structDefM,
    UnconvertedReference* structRefM,
    LLVMValueRef structRefLE,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newMemberLE);

#endif
