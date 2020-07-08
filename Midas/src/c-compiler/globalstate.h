#ifndef GLOBALSTATE_H_
#define GLOBALSTATE_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <metal/metalcache.h>

#include "metal/ast.h"
#include "metal/instructions.h"

#define CONTROL_BLOCK_STRUCT_NAME "__ControlBlock"

class GlobalState {
public:
  LLVMTargetDataRef dataLayout;
  LLVMModuleRef mod;

  MetalCache metalCache;

  Program* program;
  LLVMValueRef objIdCounter;
  LLVMValueRef liveHeapObjCounter;
  LLVMValueRef malloc, free, assert, exit, assertI64Eq, flareI64, printCStr,
      getch, printInt, printBool, initStr, addStr, eqStr, printVStr, intToCStr,
      strlen, censusContains, censusAdd, censusRemove, panic;

  int controlBlockTypeStrIndex;
  int controlBlockObjIdIndex;
  int controlBlockRcMemberIndex;
  LLVMTypeRef controlBlockStructL;
  LLVMTypeRef stringWrapperStructL;
  LLVMTypeRef stringInnerStructL;

  LLVMBuilderRef stringConstantBuilder;
  std::unordered_map<std::string, LLVMValueRef> stringConstants;

  // These don't have a ref count.
  // They're used directly for inl imm references, and
  // also used inside the below countedStructs.
  std::unordered_map<std::string, LLVMTypeRef> innerStructs;
  // These contain a ref count and the above val struct. Yon references
  // point to these.
  std::unordered_map<std::string, LLVMTypeRef> countedStructs;
  // These contain a pointer to the interface table struct below and a void*
  // to the underlying struct.
  std::unordered_map<std::string, LLVMTypeRef> interfaceRefStructs;
  // These contain a bunch of function pointer fields.
  std::unordered_map<std::string, LLVMTypeRef> interfaceTableStructs;

  // These contain a ref count and an array type. Yon references
  // point to these.
  std::unordered_map<Name*, LLVMTypeRef> knownSizeArrayCountedStructs;
  std::unordered_map<Name*, LLVMTypeRef> unknownSizeArrayCountedStructs;

  std::unordered_map<Edge*, LLVMValueRef> interfaceTablePtrs;

  std::unordered_map<std::string, LLVMValueRef> functions;

  LLVMValueRef getFunction(Name* name) {
    auto functionIter = functions.find(name->name);
    assert(functionIter != functions.end());
    return functionIter->second;
  }

  LLVMTypeRef getInnerStruct(Name* name) {
    auto structIter = innerStructs.find(name->name);
    assert(structIter != innerStructs.end());
    return structIter->second;
  }
  LLVMTypeRef getCountedStruct(Name* name) {
    auto structIter = countedStructs.find(name->name);
    assert(structIter != countedStructs.end());
    return structIter->second;
  }
  LLVMTypeRef getInterfaceRefStruct(Name* name) {
    auto structIter = interfaceRefStructs.find(name->name);
    assert(structIter != interfaceRefStructs.end());
    return structIter->second;
  }
  LLVMTypeRef getInterfaceTableStruct(Name* name) {
    auto structIter = interfaceTableStructs.find(name->name);
    assert(structIter != interfaceTableStructs.end());
    return structIter->second;
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
