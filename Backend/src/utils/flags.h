#ifndef UTILS_FLAGS_H
#define UTILS_FLAGS_H

#include <globalstate.h>

LLVMValueRef processFlag(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    const std::string& flagName,
    LLVMValueRef mainArgsCountLE,
    LLVMValueRef mainArgsLE,
    std::function<void(LLVMBuilderRef, LLVMValueRef)> thenBody);

#endif //UTILS_FLAGS_H
