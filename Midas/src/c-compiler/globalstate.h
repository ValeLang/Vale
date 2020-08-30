#ifndef GLOBALSTATE_H_
#define GLOBALSTATE_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <metal/metalcache.h>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "valeopts.h"

enum class ControlBlockMember {
  UNUSED_32B,
  LGTI,
  GENERATION,
  WRCI,
  STRONG_RC,
  CENSUS_TYPE_STR,
  CENSUS_OBJ_ID
};

class ControlBlockLayout {
public:
  int getMemberIndex(ControlBlockMember member) {
    for (int i = 0; i < members.size(); i++) {
      if (members[i] == member) {
        return i;
      }
    }
    assert(false);
  }

  void addMember(ControlBlockMember member) {
    members.push_back(member);
  }

  std::vector<ControlBlockMember> getMembers() const {
    return members;
  }

private:
  std::vector<ControlBlockMember> members;
};

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

  Program* program = nullptr;
  LLVMValueRef objIdCounter = nullptr;
  LLVMValueRef liveHeapObjCounter = nullptr;
  LLVMValueRef derefCounter = nullptr;
  LLVMValueRef mutRcAdjustCounter = nullptr;
  LLVMValueRef malloc = nullptr, free = nullptr, assert = nullptr, exit = nullptr,
      assertI64Eq = nullptr, flareI64 = nullptr, printCStr = nullptr,
      getch = nullptr, printInt = nullptr, printBool = nullptr, initStr = nullptr, addStr = nullptr,
      eqStr = nullptr, printVStr = nullptr, intToCStr = nullptr,
      strlen = nullptr, censusContains = nullptr, censusAdd = nullptr, censusRemove = nullptr,
      panic = nullptr;

  LLVMValueRef genMalloc = nullptr, genFree = nullptr;

  LLVMValueRef expandWrcTable = nullptr, checkWrci = nullptr, getNumWrcs = nullptr;
  LLVMValueRef expandLgt = nullptr, checkLgti = nullptr, getNumLiveLgtEntries = nullptr;

  LLVMValueRef wrcCapacityPtr = nullptr, wrcFirstFreeWrciPtr = nullptr, wrcEntriesArrayPtr = nullptr;
  LLVMValueRef lgtCapacityPtr = nullptr, lgtFirstFreeLgtiPtr = nullptr, lgtEntriesArrayPtr = nullptr;

  ControlBlockLayout immControlBlockLayout;
  ControlBlockLayout mutNonWeakableControlBlockLayout;
  ControlBlockLayout mutWeakableControlBlockLayout;

//  int immControlBlockRcMemberIndex = -1;
//  // 1th member is an unused 32 bits of padding, see genHeap.c
//  int immControlBlockTypeStrIndex = -1;
//  int immControlBlockObjIdIndex = -1;
//
//  int mutNonWeakableControlBlockRcMemberIndex = -1;
//  // 1th member is an unused 32 bits of padding, see genHeap.c
//  int mutNonWeakableControlBlockTypeStrIndex = -1;
//  int mutNonWeakableControlBlockObjIdIndex = -1;
//
//  // Member 0 will always be the LGTI (resilient v1) or WRCI (all others)
//  int mutWeakableControlBlockLgtiMemberIndex = -1; // Must always be at the top of the struct
//  int mutWeakableControlBlockWrciMemberIndex = -1; // Will be at the top of the struct, just to be symmetrical with LGTI
//  // 1th member is an unused 32 bits of padding, see genHeap.c
//  int mutWeakableControlBlockRcMemberIndex = -1;
//  int mutWeakableControlBlockTypeStrIndex = -1;
//  int mutWeakableControlBlockObjIdIndex = -1;

  LLVMTypeRef weakRefHeaderStructL = nullptr; // contains generation and maybe gen index
  LLVMTypeRef lgtEntryStructL = nullptr; // contains generation and next free
  LLVMTypeRef mutNonWeakableControlBlockStructL = nullptr;
  LLVMTypeRef mutWeakableControlBlockStructL = nullptr;
  LLVMTypeRef immControlBlockStructL = nullptr;
  LLVMTypeRef stringWrapperStructL = nullptr;
  LLVMTypeRef stringInnerStructL = nullptr;
  // This is a weak ref to a void*. When we're calling an interface method on a weak,
  // we have no idea who the receiver is. They'll receive this struct as the correctly
  // typed flavor of it (from structWeakRefStructs).
  LLVMTypeRef weakVoidRefStructL = nullptr;

  LLVMBuilderRef stringConstantBuilder = nullptr;
  std::unordered_map<std::string, LLVMValueRef> stringConstants;

  // These don't have a ref count.
  // They're used directly for inl imm references, and
  // also used inside the below wrapperStructs.
  std::unordered_map<std::string, LLVMTypeRef> innerStructs;
  // These contain a ref count and the above val struct. Yon references
  // point to these.
  std::unordered_map<std::string, LLVMTypeRef> wrapperStructs;
  // These contain a pointer to the interface table struct below and a void*
  // to the underlying struct.
  std::unordered_map<std::string, LLVMTypeRef> interfaceRefStructs;
  // These contain a bunch of function pointer fields.
  std::unordered_map<std::string, LLVMTypeRef> interfaceTableStructs;
  // These contain a pointer to the weak ref count int, and a pointer to the underlying struct.
  std::unordered_map<std::string, LLVMTypeRef> structWeakRefStructs;
  // These contain a pointer to the weak ref count int, and a pointer to the underlying known size array.
  std::unordered_map<std::string, LLVMTypeRef> knownSizeArrayWeakRefStructs;
  // These contain a pointer to the weak ref count int, and a pointer to the underlying unknown size array.
  std::unordered_map<std::string, LLVMTypeRef> unknownSizeArrayWeakRefStructs;
  // These contain a pointer to the weak ref count int, and then a regular interface ref struct.
  std::unordered_map<std::string, LLVMTypeRef> interfaceWeakRefStructs;

  // These contain a ref count and an array type. Yon references
  // point to these.
  std::unordered_map<std::string, LLVMTypeRef> knownSizeArrayWrapperStructs;
  std::unordered_map<std::string, LLVMTypeRef> unknownSizeArrayWrapperStructs;

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
  LLVMTypeRef getWrapperStruct(Name* name) {
    auto structIter = wrapperStructs.find(name->name);
    assert(structIter != wrapperStructs.end());
    return structIter->second;
  }
  LLVMTypeRef getStructWeakRefStruct(Name* name) {
    auto structIter = structWeakRefStructs.find(name->name);
    assert(structIter != structWeakRefStructs.end());
    return structIter->second;
  }
  LLVMTypeRef getKnownSizeArrayWeakRefStruct(Name* name) {
    auto structIter = knownSizeArrayWeakRefStructs.find(name->name);
    assert(structIter != knownSizeArrayWeakRefStructs.end());
    return structIter->second;
  }
  LLVMTypeRef getUnknownSizeArrayWeakRefStruct(Name* name) {
    auto structIter = unknownSizeArrayWeakRefStructs.find(name->name);
    assert(structIter != unknownSizeArrayWeakRefStructs.end());
    return structIter->second;
  }
  LLVMTypeRef getKnownSizeArrayWrapperStruct(Name* name) {
    auto structIter = knownSizeArrayWrapperStructs.find(name->name);
    assert(structIter != knownSizeArrayWrapperStructs.end());
    return structIter->second;
  }
  LLVMTypeRef getUnknownSizeArrayWrapperStruct(Name* name) {
    auto structIter = unknownSizeArrayWrapperStructs.find(name->name);
    assert(structIter != unknownSizeArrayWrapperStructs.end());
    return structIter->second;
  }
  LLVMTypeRef getInterfaceRefStruct(Name* name) {
    auto structIter = interfaceRefStructs.find(name->name);
    assert(structIter != interfaceRefStructs.end());
    return structIter->second;
  }
  LLVMTypeRef getInterfaceWeakRefStruct(Name* name) {
    auto interfaceIter = interfaceWeakRefStructs.find(name->name);
    assert(interfaceIter != interfaceWeakRefStructs.end());
    return interfaceIter->second;
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
  ControlBlockLayout* getControlBlockLayout(Referend* referend);
  LLVMTypeRef getControlBlockStruct(Referend* referend);
};

#endif
