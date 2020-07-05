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
    Destroy* destructureM) {
  auto mutability = ownershipToMutability(destructureM->structType->ownership);

  auto structLE =
      translateExpression(
          globalState, functionState, builder, destructureM->structExpr);

  auto structReferend =
      dynamic_cast<StructReferend *>(destructureM->structType->referend);
  assert(structReferend);

  auto structM = globalState->program->getStruct(structReferend->fullName);

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

  if (destructureM->structType->ownership == Ownership::OWN) {
    adjustRc(AFL("Destroy decrementing the owning ref"), globalState, builder, structLE, destructureM->structType, -1);
  } else if (destructureM->structType->ownership == Ownership::SHARE) {
    // We dont decrement anything here, we're only here because we already hit zero.
  } else {
    assert(false);
  }

  freeStruct(AFL("Destroy freeing"), globalState, functionState, builder, structLE, destructureM->structType);

  return makeNever();
}
