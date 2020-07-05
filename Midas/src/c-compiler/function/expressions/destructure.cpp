#include <iostream>
#include <function/expressions/shared/members.h>
#include <function/expressions/shared/heap.h>

#include "translatetype.h"

#include "function/expression.h"
#include "function/expressions/shared/shared.h"

LLVMValueRef translateDestructure(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Destructure* destructureM) {
  auto mutability = ownershipToMutability(destructureM->structType->ownership);

  auto structLE =
      translateExpression(
          globalState, functionState, builder, destructureM->structExpr);

  auto structReferend =
      dynamic_cast<StructReferend *>(destructureM->structType->referend);
  assert(structReferend);

  auto structM = globalState->program->getStruct(structReferend->fullName);

  if (destructureM->structType->ownership == Ownership::OWN) {
    for (int i = 0; i < structM->members.size(); i++) {
      auto memberName = structM->members[i]->name;
      LLVMValueRef innerStructPtrLE = getCountedContentsPtr(builder, structLE);
      auto memberLE =
          loadInnerStructMember(
              builder, innerStructPtrLE, i, memberName);
      makeLocal(
          globalState,
          functionState,
          builder,
          destructureM->localIndices[i],
          memberLE);
    }

    adjustRc(AFL("Destructure decrementing before freeing"), globalState, builder, structLE, destructureM->structType, -1);
    // We dont check rc=0 here, that's done in freeStruct.
    freeStruct(AFL("Destructure decrementing before freeing"), globalState, functionState, builder, structLE, destructureM->structType);

    return makeNever();
  } else if (destructureM->structType->ownership == Ownership::SHARE) {

    for (int i = 0; i < structM->members.size(); i++) {
      auto memberName = structM->members[i]->name;
      auto memberLE =
          loadMember(
              AFL("Destructure load"),
              globalState,
              builder,
              destructureM->structType,
              structLE,
              mutability,
              structM->members[i]->type,
              i,
              memberName + "_local");
      makeLocal(
          globalState,
          functionState,
          builder,
          destructureM->localIndices[i],
          memberLE);
    }

    dropReference(
        AFL("destructure"),
        globalState,
        functionState,
        builder,
        destructureM->structType,
        structLE);

    return makeNever();
  } else {
    // TODO implement
    assert(false);
    return nullptr;
  }
}
