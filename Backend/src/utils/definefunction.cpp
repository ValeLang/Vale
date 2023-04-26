
#include "globalstate.h"
#include "../function/function.h"
#include "definefunction.h"
#include <llvm/IR/Attributes.h>

RawFuncPtrLE addRawFunction(
    LLVMModuleRef mod,
    const std::string& name,
    LLVMTypeRef returnLT,
    std::vector<LLVMTypeRef> argsLT) {
  auto functionLT = LLVMFunctionType(returnLT, argsLT.data(), argsLT.size(), false);
  auto functionLF = LLVMAddFunction(mod, name.c_str(), functionLT);
  return RawFuncPtrLE(functionLT, functionLF);
}

ValeFuncPtrLE addValeFunction(
    GlobalState* globalState,
    const std::string& name,
    LLVMTypeRef returnLT,
    std::vector<LLVMTypeRef> argsLT) {
  static const std::string NOALIAS = "noalias";

  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);
  // The first parameter is always a restrict "next generation" pointer.
  argsLT.insert(argsLT.begin(), LLVMPointerType(genLT, 0));
  auto functionLT = LLVMFunctionType(returnLT, argsLT.data(), argsLT.size(), false);

  auto functionLF = LLVMAddFunction(globalState->mod, name.c_str(), functionLT);

  // Now lets add the restrict/noalias attribute for that pointer.
  auto noaliasAttribute =
      LLVMCreateEnumAttribute(globalState->context, llvm::Attribute::NoAlias, 0);
  assert(noaliasAttribute);
  // From LLVMAttributeIndex docs: "Attribute index are either LLVMAttributeReturnIndex,
  // LLVMAttributeFunctionIndex or a parameter number from 1 to N."
  // So this 1 means the 0th parameter.
  auto firstParamAttributeIndex = static_cast<LLVMAttributeIndex>(1);
  LLVMAddAttributeAtIndex(functionLF, firstParamAttributeIndex, noaliasAttribute);

  // Add static, should help optimizations
  LLVMSetLinkage(functionLF, LLVMInternalLinkage);

  return ValeFuncPtrLE(RawFuncPtrLE(functionLT, functionLF));
}

void defineRawFunctionBody(
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

  FunctionState functionState(name, functionL, returnTypeL, localsBuilder, std::nullopt);

  definer(&functionState, bodyTopLevelBuilder);

  // Now that we've added all the locals we need, lets make the locals block jump to the first
  // code block.
  LLVMBuildBr(localsBuilder, firstBlockL);

  LLVMDisposeBuilder(bodyTopLevelBuilder);
  LLVMDisposeBuilder(localsBuilder);
}

void defineValeFunctionBody(
    LLVMContextRef context,
    ValeFuncPtrLE funcPtr,
    LLVMTypeRef returnTypeL,
    const std::string& name,
    std::function<void(FunctionState*, LLVMBuilderRef)> definer) {
  auto functionL = funcPtr.inner.ptrLE;

  // DO NOT SUBMIT try calling defineRawFunctionBody from in here instead of repeating

  auto localsBlockName = std::string("localsBlock");
  auto localsBuilder = LLVMCreateBuilderInContext(context);
  LLVMBasicBlockRef localsBlockL = LLVMAppendBasicBlockInContext(context, functionL, localsBlockName.c_str());
  LLVMPositionBuilderAtEnd(localsBuilder, localsBlockL);

  auto firstBlockName = std::string("codeStartBlock");
  LLVMBasicBlockRef firstBlockL = LLVMAppendBasicBlockInContext(context, functionL, firstBlockName.c_str());
  LLVMBuilderRef bodyTopLevelBuilder = LLVMCreateBuilderInContext(context);
  LLVMPositionBuilderAtEnd(bodyTopLevelBuilder, firstBlockL);

  FunctionState functionState(
      name, functionL, returnTypeL, localsBuilder,
      // The 0th parameter of every vale function is the restrict next gen ptr.
      LLVMGetParam(functionL, 0));

  definer(&functionState, bodyTopLevelBuilder);

  // Now that we've added all the locals we need, lets make the locals block jump to the first
  // code block.
  LLVMBuildBr(localsBuilder, firstBlockL);

  LLVMDisposeBuilder(bodyTopLevelBuilder);
  LLVMDisposeBuilder(localsBuilder);
}
