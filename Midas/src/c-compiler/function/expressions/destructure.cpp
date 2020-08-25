#include <iostream>
#include <function/expressions/shared/controlblock.h>
#include "function/expressions/shared/members.h"
#include "function/expressions/shared/heap.h"

#include "translatetype.h"

#include "function/expression.h"
#include "function/expressions/shared/shared.h"

LLVMValueRef translateDestructure(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Destroy* destructureM) {
  auto mutability = ownershipToMutability(getEffectiveOwnership(globalState, destructureM->structType->ownership));

  auto structLE =
      translateExpression(
          globalState, functionState, blockState, builder, destructureM->structExpr);
  checkValidReference(
      FL(), globalState, functionState, builder, getEffectiveType(globalState, destructureM->structType), structLE);
//  buildFlare(FL(), globalState, functionState, builder, "structLE is ", structLE);

  auto structReferend =
      dynamic_cast<StructReferend *>(destructureM->structType->referend);
  assert(structReferend);

  auto structM = globalState->program->getStruct(structReferend->fullName);

  for (int i = 0; i < structM->members.size(); i++) {
    auto memberName = structM->members[i]->name;
    LLVMValueRef innerStructPtrLE = getStructContentsPtr(builder, structLE);
    auto memberLE =
        loadInnerStructMember(
            builder, innerStructPtrLE, i, memberName);
    checkValidReference(FL(), globalState, functionState, builder, getEffectiveType(globalState, structM->members[i]->type), memberLE);
    makeLocal(
        globalState,
      functionState,
      blockState,
      builder,
        destructureM->localIndices[i],
        memberLE);
  }

  if (getEffectiveOwnership(globalState, destructureM->structType->ownership) == Ownership::OWN) {
    discardOwningRef(FL(), globalState, functionState, blockState, builder, getEffectiveType(globalState, destructureM->structType), structLE);
  } else if (getEffectiveOwnership(globalState, destructureM->structType->ownership) == Ownership::SHARE) {
    // We dont decrement anything here, we're only here because we already hit zero.

    freeConcrete(
        AFL("Destroy freeing"), globalState, functionState, blockState, builder,
        structLE, getEffectiveType(globalState, destructureM->structType));
  } else {
    assert(false);
  }

  buildFlare(FL(), globalState, functionState, builder);

  return makeConstExpr(builder, makeNever());
}
