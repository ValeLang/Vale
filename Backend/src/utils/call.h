#ifndef _UTILS_CALL_H_
#define _UTILS_CALL_H_

#include <globalstate.h>

LLVMValueRef unmigratedBuildSimpleCall(
    LLVMBuilderRef builder,
    LLVMValueRef function,
    std::vector<LLVMValueRef> args,
    const std::string& name = "");

LLVMValueRef problematicBuildSimpleCall(
    LLVMBuilderRef builder,
    LLVMValueRef function,
    LLVMTypeRef returnLT,
    std::vector<LLVMValueRef> args,
    const std::string& name = "");

LLVMValueRef buildSimpleCall(
    LLVMBuilderRef builder,
    LLVMValueRef function,
    LLVMTypeRef funcLT,
    std::vector<LLVMValueRef> args,
    const std::string& name = "");

#endif
