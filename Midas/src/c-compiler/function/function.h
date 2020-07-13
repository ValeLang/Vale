#ifndef FUNCTION_H_
#define FUNCTION_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <unordered_set>
#include <iostream>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "globalstate.h"

class BlockState {
private:
  const BlockState* maybeParentBlockState;
  std::unordered_map<VariableId*, LLVMValueRef> localAddrByLocalId;
  std::unordered_set<VariableId*> unstackifiedLocalIds;

public:
  BlockState(const BlockState&) = delete;

  BlockState(BlockState* maybeParentBlockState_) :
      maybeParentBlockState(maybeParentBlockState_) {
  }

  LLVMValueRef getLocalAddr(VariableId* varId) const {
    assert(unstackifiedLocalIds.count(varId) == 0);
    auto localAddrIter = localAddrByLocalId.find(varId);
    if (localAddrIter != localAddrByLocalId.end()) {
      return localAddrIter->second;
    }
    if (maybeParentBlockState) {
      return maybeParentBlockState->getLocalAddr(varId);
    } else {
      assert(false);
    }
  }

  bool localExists(VariableId* varId, bool considerParentsToo) const {
    if (localAddrByLocalId.find(varId) != localAddrByLocalId.end()) {
      return true;
    }
    if (considerParentsToo && maybeParentBlockState && maybeParentBlockState->localExists(varId, true)) {
      return true;
    }
    return false;
  }

  void addLocal(VariableId* varId, LLVMValueRef localL) {
    assert(!localExists(varId, true));
    localAddrByLocalId.emplace(varId, localL);
  }

  std::unordered_set<VariableId*> getAllLocalIds(bool considerParentsToo) const {
    std::unordered_set<VariableId*> result;
    if (considerParentsToo && maybeParentBlockState) {
      result = maybeParentBlockState->getAllLocalIds(true);
    }
    for (auto p : localAddrByLocalId) {
      result.insert(p.first);
    }
    return result;
  }

  bool localWasUnstackified(VariableId* varId, bool considerParentsToo) const {
    if (unstackifiedLocalIds.count(varId)) {
      return true;
    }
    if (considerParentsToo && maybeParentBlockState && maybeParentBlockState->localWasUnstackified(varId, true)) {
      return true;
    }
    return false;
  }

  void markLocalUnstackified(VariableId* variableId) {
    assert(!localWasUnstackified(variableId, true));
    unstackifiedLocalIds.insert(variableId);
  }


  void checkAllIntroducedLocalsWereUnstackified() {
    for (auto localIdAndLocalAddr : localAddrByLocalId) {
      auto localId = localIdAndLocalAddr.first;
      // Ignore those that were made in the parent.
      if (maybeParentBlockState &&
          maybeParentBlockState->localAddrByLocalId.count(localId))
        continue;
      // localId came from the child block. Make sure the child unstackified it.
      if (unstackifiedLocalIds.count(localId) == 0) {
        std::cerr << "Un-unstackified local: " << localId->number
            << localId->maybeName << std::endl;
        assert(false);
      }
    }
  }

  // Get parent local IDs that the child unstackified.
  std::unordered_set<VariableId*> getParentLocalIdsThatSelfUnstackified() {
    assert(maybeParentBlockState);
    std::unordered_set<VariableId*> childUnstackifiedParentLocalIds;
    for (VariableId* unstackifiedLocalId : unstackifiedLocalIds) {
      // Ignore any that were made by the child block
      if (localAddrByLocalId.count(unstackifiedLocalId))
        continue;
      // Ignore any that were already unstackified by the parent
      if (maybeParentBlockState->localWasUnstackified(unstackifiedLocalId, true))
        continue;
      childUnstackifiedParentLocalIds.insert(unstackifiedLocalId);
    }
    return childUnstackifiedParentLocalIds;
  }
};

class FunctionState {
public:
  LLVMValueRef containingFunc;
  // This is here so we can return an Undef of this when we realize we just
  // called into a Never-returning function.
  LLVMTypeRef returnTypeL;
  int nextBlockNumber = 1;

  FunctionState(LLVMValueRef containingFunc_, LLVMTypeRef returnTypeL_) :
      containingFunc(containingFunc_),
      returnTypeL(returnTypeL_) {}

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