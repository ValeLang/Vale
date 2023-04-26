#include "controlblock.h"
#include "../../function/expressions/shared/shared.h"
#include "../../utils/counters.h"
#include <region/common/migration.h>

void ControlBlock::build() {
  assert(!built);

//    auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);
  auto voidPtrLT = LLVMPointerType(int8LT, 0);
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);
  auto int64PtrLT = LLVMPointerType(int64LT, 0);

  std::vector<LLVMTypeRef> membersL;
  for (auto member : members) {
    switch (member) {
      case ControlBlockMember::UNUSED_32B:
        membersL.push_back(int32LT);
        break;
      case ControlBlockMember::TETHER_32B:
        membersL.push_back(int32LT);
        break;
      case ControlBlockMember::GENERATION_32B:
        assert(membersL.empty()); // Generation should be at the top of the object
        membersL.push_back(int32LT);
        break;
      case ControlBlockMember::GENERATION_64B:
        assert(membersL.empty()); // Generation should be at the top of the object
        membersL.push_back(int64LT);
        break;
      case ControlBlockMember::GENERATION:
        assert(membersL.empty()); // Generation should be at the top of the object
        membersL.push_back(genLT);
        break;
      case ControlBlockMember::LGTI_32B:
        membersL.push_back(int32LT);
        break;
      case ControlBlockMember::WRCI_32B:
        membersL.push_back(int32LT);
        break;
      case ControlBlockMember::STRONG_RC_32B:
        membersL.push_back(int32LT);
        break;
      case ControlBlockMember::CENSUS_OBJ_ID:
        membersL.push_back(int64LT);
        break;
      case ControlBlockMember::CENSUS_TYPE_STR:
        membersL.push_back(int8PtrLT);
        break;
      default:
        assert(false);
    }
  }

  LLVMStructSetBody(structL, membersL.data(), membersL.size(), false);

  built = true;
}