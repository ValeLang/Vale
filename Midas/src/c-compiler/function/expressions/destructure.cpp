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
  checkValidReference(
      FL(), globalState, functionState, builder, destructureM->structType, structRef);
//  buildFlare(FL(), globalState, functionState, builder, "structLE is ", structLE);

  auto structReferend =
      dynamic_cast<StructReferend *>(destructureM->structType->referend);
  assert(structReferend);

  auto structM = globalState->program->getStruct(structReferend->fullName);

  for (int i = 0; i < structM->members.size(); i++) {
    auto memberName = structM->members[i]->name;
    LLVMValueRef innerStructPtrLE =
        getStructContentsPtr(
            globalState, functionState, builder, destructureM->structType, structRef);
    auto memberLE =
        loadInnerStructMember(
            builder, innerStructPtrLE, i, memberName);
    auto resultRef =
        upgradeLoadResultToRefWithTargetOwnership(
            globalState, functionState, builder, structM->members[i]->type, structM->members[i]->type, memberLE);
    makeHammerLocal(
        globalState,
        functionState,
        blockState,
        builder,
        destructureM->localIndices[i],
        resultRef);
  }

  if (destructureM->structType->ownership == Ownership::OWN) {
    discardOwningRef(FL(), globalState, functionState, blockState, builder, destructureM->structType, structRef);
  } else if (destructureM->structType->ownership == Ownership::SHARE) {
    // We dont decrement anything here, we're only here because we already hit zero.

    auto structRefLE =
        functionState->defaultRegion->makeWrapperPtr(
            destructureM->structType,
            checkValidReference(
                FL(), globalState, functionState, builder, destructureM->structType, structRef));
    auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, structRefLE);
    deallocate(
        AFL("Destroy freeing"), globalState, functionState, builder,
        controlBlockPtrLE, destructureM->structType);
  } else {
    assert(false);
  }

  buildFlare(FL(), globalState, functionState, builder);

  return makeEmptyTupleRef(globalState, functionState, builder);
}
