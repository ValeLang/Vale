#ifndef STRUCT_ARRAY_H_
#define STRUCT_ARRAY_H_

#include <llvm-c/Core.h>

#include <unordered_map>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"


void declareKnownSizeArray(
    GlobalState* globalState,
    KnownSizeArrayT* knownSizeArrayMT);

void translateKnownSizeArray(
    GlobalState* globalState,
    KnownSizeArrayT* knownSizeArrayMT);

void declareUnknownSizeArray(
    GlobalState* globalState,
    UnknownSizeArrayT* unknownSizeArrayMT);

void translateUnknownSizeArray(
    GlobalState* globalState,
    UnknownSizeArrayT* unknownSizeArrayMT);

#endif