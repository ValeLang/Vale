#ifndef UTILS_DEFINEFUNCTION_H
#define UTILS_DEFINEFUNCTION_H

#include <globalstate.h>

RawFuncPtrLE addRawFunction(
    LLVMModuleRef mod,
    const std::string& name,
    LLVMTypeRef returnLT,
    std::vector<LLVMTypeRef> argsLT);

ValeFuncPtrLE addValeFunction(
    GlobalState* globalState,
    const std::string& name,
    LLVMTypeRef returnLT,
    std::vector<LLVMTypeRef> argsLT);

void defineRawFunctionBody(
    LLVMContextRef context,
    LLVMValueRef functionL,
    LLVMTypeRef returnTypeL,
    const std::string& name,
    std::function<void(FunctionState*, LLVMBuilderRef)> definer);

void defineValeFunctionBody(
    LLVMContextRef context,
    ValeFuncPtrLE functionL,
    LLVMTypeRef returnTypeL,
    const std::string& name,
    std::function<void(FunctionState*, LLVMBuilderRef)> definer);

#endif //UTILS_DEFINEFUNCTION_H
