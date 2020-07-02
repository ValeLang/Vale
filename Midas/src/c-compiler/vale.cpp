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

#include "metal/types.h"
#include "metal/ast.h"
#include "metal/instructions.h"

#include "function/function.h"
#include "struct/struct.h"
#include "metal/readjson.h"
#include "vale.h"

// for convenience
using json = nlohmann::json;

void compileValeCode(LLVMModuleRef mod, LLVMTargetDataRef dataLayout, const char* filename) {
  std::ifstream instream(filename);
  std::string str(std::istreambuf_iterator<char>{instream}, {});

  assert(str.size() > 0);
  auto programJ = json::parse(str.c_str());
  auto program = readProgram(programJ);


  GlobalState globalState;
  globalState.dataLayout = dataLayout;
  globalState.program = program;

  globalState.liveHeapObjCounter =
      LLVMAddGlobal(mod, LLVMInt64Type(), "__liveHeapObjCounter");
  LLVMSetLinkage(globalState.liveHeapObjCounter, LLVMExternalLinkage);
//  LLVMSetVisibility(globalState.liveHeapObjCounter, LLVMHiddenVisibility);

  {
    LLVMTypeRef rettype = LLVMPointerType(LLVMInt8Type(), 0);
    LLVMTypeRef parmtype = LLVMInt64Type();
    LLVMTypeRef fnsig = LLVMFunctionType(rettype, &parmtype, 1, 0);
    globalState.malloc = LLVMAddFunction(mod, "malloc", fnsig);
  }

  {
    LLVMTypeRef rettype = LLVMVoidType();
    LLVMTypeRef parmtype = LLVMPointerType(LLVMInt8Type(), 0);
    LLVMTypeRef fnsig = LLVMFunctionType(rettype, &parmtype, 1, 0);
    globalState.free = LLVMAddFunction(mod, "free", fnsig);
  }

  {
    LLVMTypeRef rettype = LLVMVoidType();
    LLVMTypeRef parmtype = LLVMInt1Type();
    LLVMTypeRef fnsig = LLVMFunctionType(rettype, &parmtype, 1, 0);
    globalState.assert = LLVMAddFunction(mod, "__vassert", fnsig);
  }

  {
    LLVMTypeRef rettype = LLVMVoidType();
    LLVMTypeRef parmtypes[2] = { LLVMInt64Type(), LLVMInt64Type() };
    LLVMTypeRef fnsig = LLVMFunctionType(rettype, parmtypes, 2, 0);
    globalState.assertI64Eq = LLVMAddFunction(mod, "__vassertI64Eq", fnsig);
  }

  {
    LLVMTypeRef rettype = LLVMVoidType();
    LLVMTypeRef parmtypes[] = { LLVMInt64Type(), LLVMInt64Type() };
    LLVMTypeRef fnsig = LLVMFunctionType(rettype, parmtypes, 2, 0);
    globalState.flareI64 = LLVMAddFunction(mod, "__vflare_i64", fnsig);
  }


  {
    auto controlBlockStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), CONTROL_BLOCK_STRUCT_NAME);
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(LLVMInt64Type());
    LLVMStructSetBody(
        controlBlockStructL, memberTypesL.data(), memberTypesL.size(), false);
    globalState.controlBlockStructL = controlBlockStructL;
  }

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
  LLVMValueRef mainResult =
      LLVMBuildCall(builder, mainL, emptyValues, 0, "valeMainCall");

  LLVMValueRef args[2] = {
      LLVMBuildLoad(builder, globalState.liveHeapObjCounter, "numLiveObjs"),
      LLVMConstInt(LLVMInt64Type(), 0, false)
  };
  LLVMBuildCall(builder, globalState.assertI64Eq, args, 2, "");

  LLVMBuildRet(builder, mainResult);
}
