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

Ref loadMember(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    Ref structRef,
    Mutability containingStructMutability,
    Reference* memberType,
    int memberIndex,
    Reference* resultType,
    const std::string& memberName);

Ref swapMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structDefM,
    Reference* structRefMT,
    Ref structRefLE,
    int memberIndex,
    const std::string& memberName,
    Ref newMemberLE);

#endif
