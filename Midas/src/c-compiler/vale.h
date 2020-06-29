#ifndef VALEMAIN_H_
#define VALEMAIN_H_

#include <llvm-c/Core.h>
#include <llvm-c/DebugInfo.h>
#include <llvm-c/ExecutionEngine.h>
#include <llvm-c/Analysis.h>

#ifdef __cplusplus
#define EXTERNC extern "C"
#else
#define EXTERNC
#endif

EXTERNC void compileValeCode(LLVMModuleRef mod, const char* filename);

#undef EXTERNC

#endif