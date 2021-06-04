#include "controlblock.h"
#include "function/expressions/shared/shared.h"
#include "utils/counters.h"

LLVMValueRef getTypeNameStrPtrFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    IKindStructsSource* structs,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtr) {
  return LLVMBuildLoad(
      builder,
      LLVMBuildStructGEP(
          builder,
          controlBlockPtr.refLE,
          structs->getControlBlock(refM->kind)->getMemberIndex(ControlBlockMember::CENSUS_TYPE_STR),
          "typeNameStrPtrPtr"),
      "typeNameStrPtr");
}

void ControlBlock::build() {
  assert(!built);

//    auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto voidPtrLT = LLVMPointerType(int8LT, 0);
  auto int32LT = LLVMIntTypeInContext(globalState->context, GENERATION_NUM_BITS);
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