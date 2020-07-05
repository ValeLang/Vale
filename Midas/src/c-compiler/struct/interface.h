#ifndef STRUCT_INTERFACE_H_
#define STRUCT_INTERFACE_H_

#include <llvm-c/Core.h>

#include <unordered_map>

#include "metal/ast.h"
#include "globalstate.h"

void declareInterface(
    GlobalState* globalState,
    InterfaceDefinition* interfaceM);

void translateInterface(
    GlobalState* globalState,
    InterfaceDefinition* interfaceM);

#endif
