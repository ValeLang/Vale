#ifndef TRANSLATETYPE_H_
#define TRANSLATETYPE_H_

#include <vector>

#include <llvm-c/Core.h>

#include "globalstate.h"

LLVMTypeRef translateType(GlobalState* globalState, Reference* referenceM);

std::vector<LLVMTypeRef> translateTypes(
    GlobalState* globalState, std::vector<Reference*> referencesM);


// We need to pick an arbitrary type to map "Never" to. It shouldn't matter,
// because the type system uses Never to signal that the program will literally
// never get there.
// We arbitrarily use a zero-len array of i57, because it's zero sized and very
// unlikely to be used anywhere else.
// See usages of this int to see where we make those zero-len arrays of these.
constexpr int NEVER_INT_BITS = 57;

Mutability ownershipToMutability(Ownership ownership);

Mutability getMutability(GlobalState* globalState, Reference* referenceM);

LLVMTypeRef translatePrototypeToFunctionType(
    GlobalState* globalState,
    Prototype* prototype);

#endif