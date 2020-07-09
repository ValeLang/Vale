#ifndef FUNCTION_H_
#define FUNCTION_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <unordered_set>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"

class BlockState {
public:
  std::unordered_map<VariableId*, LLVMValueRef> localAddrByLocalId;
  std::unordered_set<VariableId*> unstackifiedLocalIds;

  LLVMValueRef getLocalAddr(VariableId* varId) {
    assert(unstackifiedLocalIds.count(varId) == 0);
    auto localAddrIter = localAddrByLocalId.find(varId);
    assert(localAddrIter != localAddrByLocalId.end());
    return localAddrIter->second;
  }

  void markLocalUnstackified(VariableId* variableId) {
    auto iter = localAddrByLocalId.find(variableId);
    assert(iter != localAddrByLocalId.end());
    localAddrByLocalId.erase(iter);
  }
};

class FunctionState {
public:
  LLVMValueRef containingFunc;
  int nextBlockNumber = 1;

  FunctionState(LLVMValueRef containingFunc_) :
      containingFunc(containingFunc_) {}

  std::string nextBlockName() {
    return std::string("block") + std::to_string(nextBlockNumber++);
  }
};

void translateFunction(
    GlobalState* globalState,
    Function* functionM);

LLVMValueRef declareFunction(
    GlobalState* globalState,
    Function* functionM);

#endif