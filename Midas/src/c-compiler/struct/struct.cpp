#include <iostream>

#include "struct.h"

#include "translatetype.h"

void declareStruct(
    GlobalState* globalState,
    StructDefinition* structM) {

  auto innerStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), structM->name->name.c_str());
  assert(globalState->innerStructs.count(structM->name->name) == 0);
  globalState->innerStructs.emplace(structM->name->name, innerStructL);

  auto countedStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), structM->name->name.c_str());
  assert(globalState->countedStructs.count(structM->name->name + "rc") == 0);
  globalState->countedStructs.emplace(structM->name->name, countedStructL);
}

void translateStruct(
    GlobalState* globalState,
    StructDefinition* structM) {
  LLVMTypeRef valStructL = globalState->getInnerStruct(structM->name);
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        translateType(globalState, structM->members[i]->type));
  }
  LLVMStructSetBody(
      valStructL, innerStructMemberTypesL.data(), innerStructMemberTypesL.size(), false);

  LLVMTypeRef countedStructL = globalState->getCountedStruct(structM->name);
  std::vector<LLVMTypeRef> countedStructMemberTypesL;
  // First member is a ref counts struct. We don't include the int directly
  // because we want fat pointers to point to this struct, so they can reach
  // into it and increment without doing any casting.
  countedStructMemberTypesL.push_back(globalState->controlBlockStructL);
  countedStructMemberTypesL.push_back(valStructL);
  LLVMStructSetBody(
      countedStructL, countedStructMemberTypesL.data(), countedStructMemberTypesL.size(), false);
}
