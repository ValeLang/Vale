#ifndef UTILS_DEFINEFUNCTION_H
#define UTILS_DEFINEFUNCTION_H

#include <globalstate.h>

void defineFunctionBody(
    GlobalState* globalState,
    LLVMValueRef functionL,
    LLVMTypeRef returnTypeL,
    const std::string& name,
    std::function<void(FunctionState*, LLVMBuilderRef)> definer);

#endif //UTILS_DEFINEFUNCTION_H
