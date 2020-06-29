#include <llvm-c/Core.h>
#include <llvm-c/DebugInfo.h>
#include <llvm-c/ExecutionEngine.h>
#include <llvm-c/Analysis.h>

#include <stdio.h>
#include <assert.h>
#include <string>
#include <vector>
#include <iostream>
#include <memory>
#include <fstream>
#include <unordered_map>
#include <sstream>

#include <nlohmann/json.hpp>

#include "types.h"
#include "ast.h"
#include "instructions.h"

#include "function.h"
#include "struct.h"
#include "readjson.h"
#include "vale.h"

// for convenience
using json = nlohmann::json;

void compileValeCode(LLVMModuleRef mod, const char* filename) {
  std::ifstream instream(filename);
  std::string str(std::istreambuf_iterator<char>{instream}, {});

  assert(str.size() > 0);
  auto programJ = json::parse(str.c_str());
  auto program = readProgram(programJ);

  GlobalState globalState;
  globalState.program = program;

  for (auto p : program->structs) {
    auto name = p.first;
    auto structL = p.second;
    declareStruct(&globalState, structL);
  }

  // eventually, would declare interfaces here

  for (auto p : program->structs) {
    auto name = p.first;
    auto structL = p.second;
    translateStruct(&globalState, structL);
  }



  LLVMValueRef mainL = nullptr;
  for (auto p : program->functions) {
    auto name = p.first;
    auto function = p.second;
    LLVMValueRef entryFunctionL = declareFunction(&globalState, mod, function);
    if (function->prototype->name->name == "F(\"main\")") {
      mainL = entryFunctionL;
    }
  }
  assert(mainL != nullptr);
//  mainL = testMainL;


  for (auto p : program->functions) {
    auto name = p.first;
    auto function = p.second;
    translateFunction(&globalState, function);
  }

  auto paramTypesL = std::vector<LLVMTypeRef>{
      LLVMInt64Type(),
      LLVMPointerType(LLVMPointerType(LLVMInt8Type(), 0), 0)
  };
  LLVMTypeRef functionTypeL =
      LLVMFunctionType(
          LLVMInt64Type(), paramTypesL.data(), paramTypesL.size(), 0);
  LLVMValueRef entryFunctionL =
      LLVMAddFunction(mod, "main", functionTypeL);
  LLVMSetLinkage(entryFunctionL, LLVMDLLExportLinkage);
  LLVMSetDLLStorageClass(entryFunctionL, LLVMDLLExportStorageClass);
  LLVMSetFunctionCallConv(entryFunctionL, LLVMX86StdcallCallConv);
  LLVMBuilderRef builder = LLVMCreateBuilder();
  LLVMBasicBlockRef blockL =
      LLVMAppendBasicBlock(entryFunctionL, "thebestblock");
  LLVMPositionBuilderAtEnd(builder, blockL);
  LLVMValueRef emptyValues[1] = {};
  LLVMBuildRet(
      builder,
//      LLVMConstInt(LLVMInt64Type(), 42, false));
      LLVMBuildCall(builder, mainL, emptyValues, 0, "valeMainCall"));
}
