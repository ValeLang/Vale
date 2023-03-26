#include "migration.h"

LLVMValueRef unmigratedLLVMBuildLoad(LLVMBuilderRef builder, LLVMValueRef ptr, const char* name) {
  return LLVMBuildLoad(builder, ptr, name);
}

LLVMValueRef unmigratedLLVMBuildCall(LLVMBuilderRef builder, LLVMValueRef fn, LLVMValueRef* args, int numArgs, const char* name) {
  return LLVMBuildCall(builder, fn, args, numArgs, name);
}

LLVMValueRef unmigratedLLVMBuildGEP(LLVMBuilderRef builder, LLVMValueRef pointer, LLVMValueRef* indices, int numIndices, const char* name) {
  return LLVMBuildGEP(builder, pointer, indices, numIndices, name);
}

LLVMValueRef unmigratedLLVMBuildStructGEP(LLVMBuilderRef builder, LLVMValueRef pointer, int index, const char* name) {
  return LLVMBuildStructGEP(builder, pointer, index, name);
}

LLVMValueRef problematicLLVMBuildLoad(LLVMBuilderRef builder, LLVMTypeRef pointeeType, LLVMValueRef ptr, const char* name) {
  return LLVMBuildLoad(builder, ptr, name);
}

LLVMValueRef problematicLLVMBuildCall(LLVMBuilderRef builder, LLVMTypeRef resultType, LLVMValueRef fn, LLVMValueRef* args, int numArgs, const char* name) {
  return LLVMBuildCall(builder, fn, args, numArgs, name);
}

LLVMValueRef problematicLLVMBuildGEP(LLVMBuilderRef builder, LLVMTypeRef pointeeType, LLVMValueRef pointer, LLVMValueRef* indices, int numIndices, const char* name) {
  return LLVMBuildGEP(builder, pointer, indices, numIndices, name);
}

LLVMValueRef problematicLLVMBuildStructGEP(LLVMBuilderRef builder, LLVMTypeRef pointeeType, LLVMValueRef pointer, int index, const char* name) {
  return LLVMBuildStructGEP(builder, pointer, index, name);
}
