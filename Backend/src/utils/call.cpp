#include "call.h"

LLVMValueRef unmigratedBuildSimpleCall(
    LLVMBuilderRef builder,
    LLVMValueRef function,
    std::vector<LLVMValueRef> args,
    const std::string& name) {
  return LLVMBuildCall(builder, function, args.data(), args.size(), name.c_str());
}

LLVMValueRef problematicBuildSimpleCall(
    LLVMBuilderRef builder,
    LLVMValueRef function,
    LLVMTypeRef returnLT,
    std::vector<LLVMValueRef> args,
    const std::string& name) {
  return LLVMBuildCall(builder, function, args.data(), args.size(), name.c_str());
}

LLVMValueRef buildSimpleCall(
    LLVMBuilderRef builder,
    LLVMValueRef function,
    LLVMTypeRef funcLT,
    std::vector<LLVMValueRef> args,
    const std::string& name) {
  return LLVMBuildCall2(builder, funcLT, function, args.data(), args.size(), name.c_str());
}
