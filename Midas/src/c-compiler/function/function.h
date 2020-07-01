#ifndef FUNCTION_H_
#define FUNCTION_H_

#include <llvm-c/Core.h>

#include <unordered_map>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"

class FunctionState {
public:
  LLVMValueRef containingFunc;
  std::unordered_map<int, LLVMValueRef> localAddrByLocalId;
  int nextBlockNumber = 1;

  FunctionState(LLVMValueRef containingFunc_) :
      containingFunc(containingFunc_) {}

  LLVMValueRef getLocalAddr(const VariableId& varId) {
    auto localAddrIter = localAddrByLocalId.find(varId.number);
    assert(localAddrIter != localAddrByLocalId.end());
    return localAddrIter->second;
  }

  std::string nextBlockName() {
    return std::string("block") + std::to_string(nextBlockNumber++);
  }
};

void translateFunction(
    GlobalState* globalState,
    Function* functionM);

LLVMValueRef declareFunction(
    GlobalState* globalState,
    LLVMModuleRef mod,
    Function* functionM);

#endif