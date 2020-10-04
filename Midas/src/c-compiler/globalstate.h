#ifndef GLOBALSTATE_H_
#define GLOBALSTATE_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <metal/metalcache.h>
#include <region/common/defaultlayout/structs.h>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "valeopts.h"

class IRegion;
class IReferendStructsSource;
class IWeakRefStructsSource;
class ControlBlock;

class GlobalState {
public:
  LLVMTargetMachineRef machine = nullptr;
  LLVMContextRef context = nullptr;
  LLVMDIBuilderRef dibuilder = nullptr;
  LLVMMetadataRef compileUnit = nullptr;
  LLVMMetadataRef difile = nullptr;

  ValeOptions *opt = nullptr;

  LLVMTargetDataRef dataLayout = nullptr;
  LLVMModuleRef mod = nullptr;
  int ptrSize = 0;

  MetalCache metalCache;

  LLVMTypeRef ram64Struct = nullptr;

  Program* program = nullptr;
  LLVMValueRef objIdCounter = nullptr;
  LLVMValueRef liveHeapObjCounter = nullptr;
  LLVMValueRef derefCounter = nullptr;
  LLVMValueRef mutRcAdjustCounter = nullptr;
  // an i64 pointer to null.
  LLVMValueRef ram64 = nullptr;
  LLVMValueRef writeOnlyGlobal = nullptr;
  // Initialized to &writeOnlyGlobal / 8 in main.
  // We can use this to easily write an i64 into NULL or the write only global at runtime.
  LLVMValueRef ram64IndexToWriteOnlyGlobal = nullptr;
  LLVMValueRef malloc = nullptr, free = nullptr, assert = nullptr, exit = nullptr,
      assertI64Eq = nullptr, flareI64 = nullptr, printCStr = nullptr,
      getch = nullptr, printInt = nullptr, printBool = nullptr, intToCStr = nullptr,
      strlen = nullptr, censusContains = nullptr, censusAdd = nullptr, censusRemove = nullptr,
      panic = nullptr, newVStr = nullptr, getStrCharsFunc = nullptr, getStrNumBytesFunc = nullptr;


  LLVMValueRef initStr = nullptr, addStr = nullptr, eqStr = nullptr, printVStr = nullptr;

  LLVMValueRef genMalloc = nullptr, genFree = nullptr;


  // This is a global, we can return this when we want to return never. It should never actually be
  // used as an input to any expression in any function though.
  LLVMValueRef neverPtr = nullptr;

  LLVMBuilderRef stringConstantBuilder = nullptr;
  std::unordered_map<std::string, LLVMValueRef> stringConstants;

  std::unordered_map<Edge*, LLVMValueRef> interfaceTablePtrs;

  std::unordered_map<std::string, LLVMValueRef> functions;
  std::unordered_map<std::string, LLVMValueRef> externFunctions;

  LLVMBuilderRef valeMainBuilder = nullptr;

  IRegion* region = nullptr;

  LLVMValueRef getFunction(Name* name) {
    auto functionIter = functions.find(name->name);
    assert(functionIter != functions.end());
    return functionIter->second;
  }

  LLVMValueRef getInterfaceTablePtr(Edge* edge) {
    auto iter = interfaceTablePtrs.find(edge);
    assert(iter != interfaceTablePtrs.end());
    return iter->second;
  }
  LLVMValueRef getOrMakeStringConstant(const std::string& str) {
    auto iter = stringConstants.find(str);
    if (iter == stringConstants.end()) {

      iter =
          stringConstants.emplace(
              str,
              LLVMBuildGlobalStringPtr(
                  stringConstantBuilder,
                  str.c_str(),
                  (std::string("conststr") + std::to_string(stringConstants.size())).c_str()))
          .first;
    }
    return iter->second;
  }
};

#endif
