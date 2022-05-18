#include "call.h"

LLVMValueRef buildSimpleCall(
    LLVMBuilderRef builder, LLVMValueRef function,
    std::vector<LLVMValueRef> args,
    const std::string& name) {
  return LLVMBuildCall(builder, function, args.data(), args.size(), name.c_str());
}
