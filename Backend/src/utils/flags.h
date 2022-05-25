#ifndef UTILS_FLAGS_H
#define UTILS_FLAGS_H

#include <globalstate.h>

// Returns the number of consumed args.
// The caller should adjust argv and argc accordingly, and maybe move argv[0] to argv[2].
LLVMValueRef processFlag(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    const std::string& flagName,
    LLVMValueRef mainArgsCountLE,
    LLVMValueRef mainArgsLE,
    std::function<void(LLVMBuilderRef, LLVMValueRef)> thenBody);

#endif //UTILS_FLAGS_H
