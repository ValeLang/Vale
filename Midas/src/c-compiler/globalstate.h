#ifndef GLOBALSTATE_H_
#define GLOBALSTATE_H_

#include <llvm-c/Core.h>

#include <unordered_map>

#include "metal/ast.h"
#include "metal/instructions.h"

#define CONTROL_BLOCK_STRUCT_NAME "__ControlBlock"

class GlobalState {
public:
  LLVMTargetDataRef dataLayout;

  Program* program;
  LLVMValueRef malloc, free, assert, flareI64;

  LLVMTypeRef controlBlockStructL;

  std::unordered_map<std::string, LLVMTypeRef> structs;
  std::unordered_map<std::string, LLVMValueRef> functions;

  LLVMValueRef getFunction(Function* functionM) {
    auto functionIter = functions.find(functionM->prototype->name->name);
    assert(functionIter != functions.end());
    return functionIter->second;
  }

  LLVMTypeRef getStruct(Name* name) {
    auto structIter = structs.find(name->name);
    assert(structIter != structs.end());
    return structIter->second;
  }
};

#endif
