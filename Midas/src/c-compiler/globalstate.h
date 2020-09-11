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

class ControlBlock {
public:
  // structL should *not* have a body yet, this will fill it.
  ControlBlock(LLVMTypeRef structL_) :
      structL(structL_),
      built(false) {}

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

  void build() {
    assert(!built);

    auto voidLT = LLVMVoidType();
    auto voidPtrLT = LLVMPointerType(voidLT, 0);
    auto int1LT = LLVMInt1Type();
    auto int8LT = LLVMInt8Type();
    auto int32LT = LLVMInt32Type();
    auto int64LT = LLVMInt64Type();
    auto int8PtrLT = LLVMPointerType(int8LT, 0);
    auto int64PtrLT = LLVMPointerType(int64LT, 0);

    std::vector<LLVMTypeRef> membersL;
    for (auto member : members) {
      switch (member) {
        case ControlBlockMember::UNUSED_32B:
          membersL.push_back(int32LT);
          break;
        case ControlBlockMember::GENERATION:
          assert(membersL.empty()); // Generation should be at the top of the object
          membersL.push_back(int32LT);
          break;
        case ControlBlockMember::LGTI:
          membersL.push_back(int32LT);
          break;
        case ControlBlockMember::WRCI:
          membersL.push_back(int32LT);
          break;
        case ControlBlockMember::STRONG_RC:
          membersL.push_back(int32LT);
          break;
        case ControlBlockMember::CENSUS_OBJ_ID:
          membersL.push_back(int64LT);
          break;
        case ControlBlockMember::CENSUS_TYPE_STR:
          membersL.push_back(int8PtrLT);
          break;
      }
    }

    LLVMStructSetBody(structL, membersL.data(), membersL.size(), false);

    built = true;
  }

private:
  std::vector<ControlBlockMember> members;
  LLVMTypeRef structL;
  bool built;
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

  // This is a global, we can return this when we want to return never. It should never actually be
  // used as an input to any expression in any function though.
  LLVMValueRef neverPtr = nullptr;
//
//  ControlBlock immControlBlock;
//  ControlBlock mutNonWeakableControlBlock;
//  ControlBlock mutWeakableControlBlock;

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
//
//  LLVMTypeRef weakRefHeaderStructL = nullptr; // contains generation and maybe gen index
  LLVMTypeRef lgtEntryStructL = nullptr; // contains generation and next free
//  LLVMTypeRef mutNonWeakableControlBlockStructL = nullptr;
//  LLVMTypeRef mutWeakableControlBlockStructL = nullptr;
//  LLVMTypeRef immControlBlockStructL = nullptr;
//  LLVMTypeRef stringWrapperStructL = nullptr;
//  LLVMTypeRef stringInnerStructL = nullptr;
//  // This is a weak ref to a void*. When we're calling an interface method on a weak,
//  // we have no idea who the receiver is. They'll receive this struct as the correctly
//  // typed flavor of it (from structWeakRefStructs).
//  LLVMTypeRef weakVoidRefStructL = nullptr;

  LLVMBuilderRef stringConstantBuilder = nullptr;
  std::unordered_map<std::string, LLVMValueRef> stringConstants;

  std::unordered_map<Edge*, LLVMValueRef> interfaceTablePtrs;

  std::unordered_map<std::string, LLVMValueRef> functions;

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
  ControlBlock* getControlBlock(Referend* referend, Weakability effectiveWeakability);
  LLVMTypeRef getControlBlockStruct(Referend* referend, Weakability effectiveWeakability);
};

#endif
