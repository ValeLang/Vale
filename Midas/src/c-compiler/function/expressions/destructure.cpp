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
  auto mutability = ownershipToMutability(destructureM->structType->ownership);

  auto structLE =
      translateExpression(
          globalState, functionState, blockState, builder, destructureM->structExpr);
  checkValidReference(FL(), globalState, functionState, builder, destructureM->structType, structLE);

  auto structReferend =
      dynamic_cast<StructReferend *>(destructureM->structType->referend);
  assert(structReferend);

  auto structM = globalState->program->getStruct(structReferend->fullName);

  if (structM->weakable) {
    auto controlBlockPtrLE = getControlBlockPtr(builder, structLE, destructureM->structType);
    auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, destructureM->structType, controlBlockPtrLE);
    LLVMBuildCall(builder, globalState->markWrcDead, &wrciLE, 1, "");
  }

  for (int i = 0; i < structM->members.size(); i++) {
    auto memberName = structM->members[i]->name;
    LLVMValueRef innerStructPtrLE = getStructContentsPtr(builder,
        structLE);
    auto memberLE =
        loadInnerStructMember(
            builder, innerStructPtrLE, i, memberName);
    checkValidReference(FL(), globalState, functionState, builder, structM->members[i]->type, memberLE);
    makeLocal(
        globalState,
      functionState,
      blockState,
      builder,
        destructureM->localIndices[i],
        memberLE);
  }

  if (destructureM->structType->ownership == Ownership::OWN) {
    if (globalState->opt->regionOverride == RegionOverride::ASSIST) {
      adjustStrongRc(
          AFL("Destroy decrementing the owning ref"),
          globalState, functionState, builder, structLE, destructureM->structType, -1);
    } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
      // Do nothing
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT) {
      assert(false); // impl
    } else assert(false);
  } else if (destructureM->structType->ownership == Ownership::SHARE) {
    // We dont decrement anything here, we're only here because we already hit zero.
  } else {
    assert(false);
  }

  freeConcrete(AFL("Destroy freeing"), globalState, functionState, blockState, builder,
      structLE, destructureM->structType);

  return makeConstExpr(builder, makeNever());
}
