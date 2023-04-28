
#include "globalstate.h"
#include "../function/function.h"
#include "definefunction.h"

FuncPtrLE addFunction(
    LLVMModuleRef mod,
    const std::string& name,
    LLVMTypeRef returnLT,
    std::vector<LLVMTypeRef> argsLT) {
  auto functionLT = LLVMFunctionType(returnLT, argsLT.data(), argsLT.size(), false);
  auto functionLF = LLVMAddFunction(mod, name.c_str(), functionLT);
  return FuncPtrLE(functionLT, functionLF);
}

void defineFunctionBody(
    LLVMContextRef context,
    LLVMValueRef functionL,
    LLVMTypeRef returnTypeL,
    const std::string& name,
    std::function<void(FunctionState*, LLVMBuilderRef)> definer) {

  auto localsBlockName = std::string("localsBlock");
  auto localsBuilder = LLVMCreateBuilderInContext(context);
  LLVMBasicBlockRef localsBlockL = LLVMAppendBasicBlockInContext(context, functionL, localsBlockName.c_str());
  LLVMPositionBuilderAtEnd(localsBuilder, localsBlockL);

  auto firstBlockName = std::string("codeStartBlock");
  LLVMBasicBlockRef firstBlockL = LLVMAppendBasicBlockInContext(context, functionL, firstBlockName.c_str());
  LLVMBuilderRef bodyTopLevelBuilder = LLVMCreateBuilderInContext(context);
  LLVMPositionBuilderAtEnd(bodyTopLevelBuilder, firstBlockL);

  FunctionState functionState(name, functionL, returnTypeL, localsBuilder);

  definer(&functionState, bodyTopLevelBuilder);

  // Now that we've added all the locals we need, lets make the locals block jump to the first
  // code block.
  LLVMBuildBr(localsBuilder, firstBlockL);

  LLVMDisposeBuilder(bodyTopLevelBuilder);
  LLVMDisposeBuilder(localsBuilder);
}
