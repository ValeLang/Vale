#include "call.h"

LLVMValueRef buildCall(
    LLVMBuilderRef builder, LLVMValueRef function,
    std::vector<LLVMValueRef> args,
    const char *name) {
  return LLVMBuildCall(builder, function, args.data(), args.size(), name);
}
