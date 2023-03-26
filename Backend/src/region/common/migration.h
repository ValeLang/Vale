#ifndef MIDAS_MIGRATION_H
#define MIDAS_MIGRATION_H

#include <llvm-c/Core.h>

LLVMValueRef unmigratedLLVMBuildLoad(LLVMBuilderRef builder, LLVMValueRef ptr, const char* name);

LLVMValueRef unmigratedLLVMBuildCall(LLVMBuilderRef builder, LLVMValueRef fn, LLVMValueRef* args, int numArgs, const char* name);

LLVMValueRef unmigratedLLVMBuildGEP(LLVMBuilderRef builder, LLVMValueRef pointer, LLVMValueRef* indices, int numIndices, const char* name);

LLVMValueRef unmigratedLLVMBuildStructGEP(LLVMBuilderRef builder, LLVMValueRef pointer, int index, const char* name);

LLVMValueRef problematicLLVMBuildLoad(LLVMBuilderRef builder, LLVMTypeRef pointeeType, LLVMValueRef ptr, const char* name);

LLVMValueRef problematicLLVMBuildCall(LLVMBuilderRef builder, LLVMTypeRef resultType, LLVMValueRef fn, LLVMValueRef* args, int numArgs, const char* name);

LLVMValueRef problematicLLVMBuildGEP(LLVMBuilderRef builder, LLVMTypeRef pointeeType, LLVMValueRef pointer, LLVMValueRef* indices, int numIndices, const char* name);

LLVMValueRef problematicLLVMBuildStructGEP(LLVMBuilderRef builder, LLVMTypeRef pointeeType, LLVMValueRef pointer, int index, const char* name);

#endif //MIDAS_MIGRATION_H
