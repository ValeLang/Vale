#include <iostream>

#include "translatetype.h"

#include "function.h"
#include "expression.h"

LLVMValueRef declareFunction(
    GlobalState* globalState,
    LLVMModuleRef mod,
    Function* functionM) {

  auto paramTypesL = translateTypes(globalState, functionM->prototype->params);
  auto returnTypeL =
      translateType(globalState, functionM->prototype->returnType);
  auto nameL = functionM->prototype->name->name;

  LLVMTypeRef functionTypeL =
      LLVMFunctionType(returnTypeL, paramTypesL.data(), paramTypesL.size(), 0);
  LLVMValueRef functionL = LLVMAddFunction(mod, nameL.c_str(), functionTypeL);

  assert(globalState->functions.count(functionM->prototype->name->name) == 0);
  globalState->functions.emplace(functionM->prototype->name->name, functionL);

  return functionL;
}

void translateFunction(
    GlobalState* globalState,
    Function* functionM) {

  auto functionL = globalState->getFunction(functionM);

  auto localAddrByLocalId = std::unordered_map<int, LLVMValueRef>{};

  FunctionState functionState(functionL);

  int blockNumber = functionState.nextBlockNumber++;
  auto blockName = std::string("block") + std::to_string(blockNumber);
  LLVMBasicBlockRef firstBlockL =
      LLVMAppendBasicBlock(functionState.containingFunc, blockName.c_str());

  LLVMBuilderRef bodyTopLevelBuilder = LLVMCreateBuilder();
  LLVMPositionBuilderAtEnd(bodyTopLevelBuilder, firstBlockL);
  // There are other builders made elsewhere for various blocks in the function,
  // but this is the one for the top level.
  // It's not always pointed at firstBlockL, it can be re-pointed to other
  // blocks at the top level.
  //
  // For example, in:
  //   fn main() {
  //     x! = 5;
  //     if (true && true) {
  //       mut x = 7;
  //     } else {
  //       mut x = 8;
  //     }
  //     println(x);
  //   }
  // There are four blocks:
  // - 1: contains `x! = 5` and `true && true`
  // - 2: contains `mut x = 7;`
  // - 3: contains `mut x = 8;`
  // - 4: contains `println(x)`
  //
  // When it's done making block 1, we'll make block 4 and `bodyTopLevelBuilder`
  // will point at that.
  //
  // The point is, this builder can change to point at other blocks on the same
  // level.
  //
  // All builders work like this, at whatever level theyre on.

  // Translate the body of the function. Can ignore the result because it's a
  // Never, because Valestrom guarantees we end function bodies in a ret.
  translateExpression(
      globalState, &functionState, bodyTopLevelBuilder, functionM->block);
}
