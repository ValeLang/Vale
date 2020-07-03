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
#include "struct/interface.h"

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
  globalState.mod = mod;
  globalState.program = program;

  globalState.liveHeapObjCounter =
      LLVMAddGlobal(mod, LLVMInt64Type(), "__liveHeapObjCounter");
  LLVMSetLinkage(globalState.liveHeapObjCounter, LLVMExternalLinkage);
//  LLVMSetVisibility(globalState.liveHeapObjCounter, LLVMHiddenVisibility);

  {
    LLVMTypeRef retType = LLVMPointerType(LLVMInt8Type(), 0);
    LLVMTypeRef paramType = LLVMInt64Type();
    LLVMTypeRef funcType = LLVMFunctionType(retType, &paramType, 1, 0);
    globalState.malloc = LLVMAddFunction(mod, "malloc", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    LLVMTypeRef paramType = LLVMPointerType(LLVMInt8Type(), 0);
    LLVMTypeRef funcType = LLVMFunctionType(retType, &paramType, 1, 0);
    globalState.free = LLVMAddFunction(mod, "free", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    LLVMTypeRef paramType = LLVMInt1Type();
    LLVMTypeRef funcType = LLVMFunctionType(retType, &paramType, 1, 0);
    globalState.assert = LLVMAddFunction(mod, "__vassert", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    LLVMTypeRef paramTypes[2] = { LLVMInt64Type(), LLVMInt64Type() };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes, 2, 0);
    globalState.assertI64Eq = LLVMAddFunction(mod, "__vassertI64Eq", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    LLVMTypeRef paramTypes[] = { LLVMInt64Type(), LLVMInt64Type() };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes, 2, 0);
    globalState.flareI64 = LLVMAddFunction(mod, "__vflare_i64", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMPointerType(LLVMInt8Type(), 0)
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState.printStr = LLVMAddFunction(mod, "__vprintCStr", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMInt64Type()
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState.printInt = LLVMAddFunction(mod, "__vprintI64", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMInt1Type()
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState.printBool = LLVMAddFunction(mod, "__vprintBool", funcType);
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
    auto structM = p.second;
    declareStruct(&globalState, structM);
  }

  for (auto p : program->interfaces) {
    auto name = p.first;
    auto interfaceM = p.second;
    declareInterface(&globalState, interfaceM);
  }

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    translateStruct(&globalState, structM);
  }

  for (auto p : program->interfaces) {
    auto name = p.first;
    auto interfaceM = p.second;
    translateInterface(&globalState, interfaceM);
  }

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    for (auto e : structM->edges) {
      declareEdge(&globalState, e);
    }
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

  // We translate the edges after the functions are declared because the
  // functions have to exist for the itables to point to them.
  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    for (auto e : structM->edges) {
      translateEdge(&globalState, e);
    }
  }

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
