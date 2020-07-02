#ifndef GLOBALSTATE_H_
#define GLOBALSTATE_H_

#include <llvm-c/Core.h>

#include <unordered_map>

#include "metal/ast.h"
#include "metal/instructions.h"

#define CONTROL_BLOCK_STRUCT_NAME "__ControlBlock"

#define MAX_INLINE_SIZE_BYTES 32

class GlobalState {
public:
  LLVMTargetDataRef dataLayout;

  Program* program;
  LLVMValueRef liveHeapObjCounter;
  LLVMValueRef malloc, free, assert, assertI64Eq, flareI64;

  LLVMTypeRef controlBlockStructL;

  // These don't have a ref count.
  // They're used directly for inl imm references, and
  // also used inside the below countedStructs.
  std::unordered_map<std::string, LLVMTypeRef> innerStructs;
  // These contain a ref count and the above val struct. Yon references
  // point to these.
  std::unordered_map<std::string, LLVMTypeRef> countedStructs;

  // These contain a ref count and an array type. Yon references
  // point to these.
  std::unordered_map<Name*, LLVMTypeRef> knownSizeArrayCountedStructs;
  std::unordered_map<Name*, LLVMTypeRef> unknownSizeArrayCountedStructs;

  std::unordered_map<std::string, LLVMValueRef> functions;

  LLVMValueRef getFunction(Function* functionM) {
    auto functionIter = functions.find(functionM->prototype->name->name);
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
};

#endif
