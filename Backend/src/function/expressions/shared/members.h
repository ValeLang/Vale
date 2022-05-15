#ifndef FUNCTION_EXPRESSIONS_SHARED_MEMBERS_H_
#define FUNCTION_EXPRESSIONS_SHARED_MEMBERS_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "../../../metal/ast.h"
#include "../../../metal/instructions.h"
#include "../../../globalstate.h"
#include "../../function.h"
#include "shared.h"

Ref loadMember(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref structRegionInstanceRef,
    Reference* structRefM,
    Ref structRef,
    bool structKnownLive,
    Mutability containingStructMutability,
    Reference* memberType,
    int memberIndex,
    Reference* resultType,
    const std::string& memberName);

Ref swapMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref structRegionInstanceRef,
    StructDefinition* structDefM,
    Reference* structRefMT,
    Ref structRefLE,
    bool structKnownLive,
    int memberIndex,
    const std::string& memberName,
    Ref newMemberLE);

#endif
