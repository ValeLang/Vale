#include <iostream>
#include <region/common/controlblock.h>
#include "function/expressions/shared/members.h"
#include "region/common/heap.h"

#include "translatetype.h"

#include "function/expression.h"
#include "function/expressions/shared/shared.h"

Ref translateDestructure(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Destroy* destructureM) {
  auto mutability = ownershipToMutability(destructureM->structType->ownership);

  auto structRef =
      translateExpression(
          globalState, functionState, blockState, builder, destructureM->structExpr);
  functionState->defaultRegion->checkValidReference(FL(),
      functionState, builder, destructureM->structType, structRef);
//  buildFlare(FL(), globalState, functionState, builder, "structLE is ", structLE);

  auto structReferend =
      dynamic_cast<StructReferend *>(destructureM->structType->referend);
  assert(structReferend);

  auto structM = globalState->program->getStruct(structReferend->fullName);

  for (int i = 0; i < structM->members.size(); i++) {
    auto memberName = structM->members[i]->name;
    auto memberType = structM->members[i]->type;
    auto memberLE =
        functionState->defaultRegion->loadMember(
            functionState, builder, destructureM->structType, structRef, true, i, memberType, memberType, memberName);
    makeHammerLocal(
        globalState,
        functionState,
        blockState,
        builder,
        destructureM->localIndices[i],
        memberLE);
  }

  if (destructureM->structType->ownership == Ownership::OWN) {
    functionState->defaultRegion->discardOwningRef(FL(), functionState, blockState, builder, destructureM->structType, structRef);
  } else if (destructureM->structType->ownership == Ownership::SHARE) {
    // We dont decrement anything here, we're only here because we already hit zero.

    functionState->defaultRegion->deallocate(
        AFL("Destroy freeing"), functionState, builder,
        destructureM->structType, structRef);
  } else {
    assert(false);
  }

  buildFlare(FL(), globalState, functionState, builder);

  return makeEmptyTupleRef(globalState, functionState, builder);
}
