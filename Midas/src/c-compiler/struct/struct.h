#ifndef STRUCT_H_
#define STRUCT_H_

#include <llvm-c/Core.h>

#include <unordered_map>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"

void declareStruct(
    GlobalState* globalState,
    StructDefinition* structM);

void translateStruct(
    GlobalState* globalState,
    StructDefinition* structM);

#endif