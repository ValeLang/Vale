#ifndef FUNCTION_EXPRESSIONS_SHARED_CONTROLBLOCK_H_
#define FUNCTION_EXPRESSIONS_SHARED_CONTROLBLOCK_H_

#include <llvm-c/Core.h>
#include <vector>
#include <cassert>
#include <metal/types.h>
#include <function/expressions/shared/ref.h>

class GlobalState;
class FunctionState;

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
    assert(built);
    for (int i = 0; i < members.size(); i++) {
      if (members[i] == member) {
        return i;
      }
    }
    assert(false);
  }

  void addMember(ControlBlockMember member) {
    assert(!built);
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

  LLVMTypeRef getStruct() {
    assert(built);
    return structL;
  }

private:
  std::vector<ControlBlockMember> members;
  LLVMTypeRef structL;
  bool built;
};

// See CRCISFAORC for why we don't take in a mutability.
// Strong means owning or borrow or shared; things that control the lifetime.
LLVMValueRef getStrongRcPtrFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr);

LLVMValueRef getObjIdFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtr);

// Strong means owning or borrow or shared; things that control the lifetime.
LLVMValueRef getStrongRcFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtrLE);

#endif //VALEC_CONTROLBLOCK_H
