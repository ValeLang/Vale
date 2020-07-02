#ifndef FUNCTION_EXPRESSIONS_SHARED_ELEMENTS_H_
#define FUNCTION_EXPRESSIONS_SHARED_ELEMENTS_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"
#include "function/function.h"

LLVMValueRef loadElement(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    LLVMValueRef arrayLE,
    Mutability mutability,
    LLVMValueRef indexLE);

#endif
