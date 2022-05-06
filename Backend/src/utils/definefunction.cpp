#include "globalstate.h"
#include "../function/function.h"
#include "definefunction.h"

void defineFunctionBody(
    GlobalState* globalState,
    LLVMValueRef functionL,
    LLVMTypeRef returnTypeL,
    const std::string& name,
    std::function<void(FunctionState*, LLVMBuilderRef)> definer) {

  auto localsBlockName = std::string("localsBlock");
  auto localsBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMBasicBlockRef localsBlockL = LLVMAppendBasicBlockInContext(globalState->context, functionL, localsBlockName.c_str());
  LLVMPositionBuilderAtEnd(localsBuilder, localsBlockL);

  auto firstBlockName = std::string("codeStartBlock");
  LLVMBasicBlockRef firstBlockL = LLVMAppendBasicBlockInContext(globalState->context, functionL, firstBlockName.c_str());
  LLVMBuilderRef bodyTopLevelBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(bodyTopLevelBuilder, firstBlockL);

  FunctionState functionState(name, functionL, returnTypeL, localsBuilder);

  definer(&functionState, bodyTopLevelBuilder);

  // Now that we've added all the locals we need, lets make the locals block jump to the first
  // code block.
  LLVMBuildBr(localsBuilder, firstBlockL);

  LLVMDisposeBuilder(bodyTopLevelBuilder);
  LLVMDisposeBuilder(localsBuilder);
}
