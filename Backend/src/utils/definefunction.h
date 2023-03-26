#ifndef UTILS_DEFINEFUNCTION_H
#define UTILS_DEFINEFUNCTION_H

#include <globalstate.h>

FuncPtrLE addFunction(
    LLVMModuleRef mod,
    const std::string& name,
    LLVMTypeRef returnLT,
    std::vector<LLVMTypeRef> argsLT);

void defineFunctionBody(
    LLVMContextRef context,
    LLVMValueRef functionL,
    LLVMTypeRef returnTypeL,
    const std::string& name,
    std::function<void(FunctionState*, LLVMBuilderRef)> definer);

#endif //UTILS_DEFINEFUNCTION_H
