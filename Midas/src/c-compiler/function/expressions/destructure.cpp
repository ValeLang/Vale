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
  if (destructureM->structType->ownership == Ownership::OWN) {
    auto structLE =
        translateExpression(
            globalState, functionState, builder, destructureM->structExpr);

    auto structReferend =
        dynamic_cast<StructReferend *>(destructureM->structType->referend);
    assert(structReferend);

    auto structM = globalState->program->getStruct(structReferend->fullName);

    for (int i = 0; i < structM->members.size(); i++) {
      auto memberName = structM->members[i]->name;
      auto memberLE =
          loadMember(
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

    if (destructureM->structType->ownership == Ownership::OWN) {
      // We dont check rc=1 here, that's done in freeStruct.
      freeStruct(globalState, builder, structLE);
    }

    return makeNever();
  } else {
    // TODO implement
    assert(false);
    return nullptr;
  }
}
