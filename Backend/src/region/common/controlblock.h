#ifndef FUNCTION_EXPRESSIONS_SHARED_CONTROLBLOCK_H_
#define FUNCTION_EXPRESSIONS_SHARED_CONTROLBLOCK_H_

#include <llvm-c/Core.h>
#include <vector>
#include <cassert>
#include "../../metal/types.h"
#include "../../function/expressions/shared/ref.h"

class GlobalState;
class FunctionState;

enum class ControlBlockMember {
  UNUSED_32B,
  LGTI_32B,
  GENERATION,
  GENERATION_32B,
  GENERATION_64B,
  WRCI_32B,
  STRONG_RC_32B,
  CENSUS_TYPE_STR,
  CENSUS_OBJ_ID,
  // It's 32B because we put it in the spot where the generational heap puts its size,
  // like we do with the UNUSED_32B elsewhere.
  TETHER_32B,
  METADATA_BITS_2B,
};

class ControlBlock {
public:
  // structL should *not* have a body yet, this will fill it.
  ControlBlock(GlobalState* globalState_, LLVMTypeRef structL_) :
      globalState(globalState_),
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

  void build();

  LLVMTypeRef getStruct() {
    assert(built);
    return structL;
  }

private:
  GlobalState* globalState = nullptr;
  std::vector<ControlBlockMember> members;
  LLVMTypeRef structL;
  bool built;
};

#endif //VALEC_CONTROLBLOCK_H
