#include "call.h"

LLVMValueRef buildSimpleCall(
    LLVMBuilderRef builder,
    LLVMValueRef function,
    LLVMTypeRef funcLT,
    std::vector<LLVMValueRef> args,
    const std::string& name) {
  return LLVMBuildCall2(builder, funcLT, function, args.data(), args.size(), name.c_str());
}
