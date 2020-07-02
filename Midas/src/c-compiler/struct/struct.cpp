#include <iostream>

#include "struct.h"

#include "translatetype.h"

void declareStruct(
    GlobalState* globalState,
    StructDefinition* structM) {

  auto structL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), structM->name->name.c_str());

  assert(globalState->structs.count(structM->name->name) == 0);
  globalState->structs.emplace(structM->name->name, structL);
}

void translateStruct(
    GlobalState* globalState,
    StructDefinition* structM) {
  LLVMTypeRef structL = globalState->getStruct(structM->name);

  std::vector<LLVMTypeRef> memberTypesL;
  if (structM->mutability == Mutability::MUTABLE) {
    // First member is a ref counts struct. We don't include the int directly
    // because we want fat pointers to point to this struct, so they can reach
    // into it and increment without doing any casting.
    memberTypesL.push_back(globalState->controlBlockStructL);
  }
  for (int i = 0; i < structM->members.size(); i++) {
    memberTypesL.push_back(
        translateType(globalState, structM->members[i]->type));
  }
  LLVMStructSetBody(
      structL, memberTypesL.data(), memberTypesL.size(), false);
}
