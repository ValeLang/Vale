#include <iostream>
#include "../../region/common/controlblock.h"
#include "shared/members.h"
#include "../../region/common/heap.h"

#include "../../translatetype.h"

#include "../expression.h"
#include "shared/shared.h"

Ref translateDestructure(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Destroy* destructureM) {
  buildFlare(FL(), globalState, functionState, builder);
  auto mutability = ownershipToMutability(destructureM->structType->ownership);

  auto structRegionInstanceRef =
      // At some point, look up the actual region instance, perhaps from the FunctionState?
      globalState->getRegion(destructureM->structType)->createRegionInstanceLocal(functionState, builder);

  auto structRef =
      translateExpression(
          globalState, functionState, blockState, builder, destructureM->structExpr);
  auto structLiveRef =
      globalState->getRegion(destructureM->structType)->checkRefLive(FL(),
          functionState, builder, structRegionInstanceRef, destructureM->structType, structRef, false);
  globalState->getRegion(destructureM->structType)->checkValidReference(FL(),
      functionState, builder, true, destructureM->structType, structRef);

  buildFlare(FL(), globalState, functionState, builder);

  auto structKind =
      dynamic_cast<StructKind *>(destructureM->structType->kind);
  assert(structKind);

  auto structM = globalState->program->getStruct(structKind);

  for (int i = 0; i < structM->members.size(); i++) {
    buildFlare(FL(), globalState, functionState, builder);
    auto memberName = structM->members[i]->name;
    auto memberType = structM->members[i]->type;
    auto memberLE =
        globalState->getRegion(destructureM->structType)->loadMember(
            functionState, builder, structRegionInstanceRef, destructureM->structType, structLiveRef, i, memberType, memberType, memberName);
    makeHammerLocal(
        globalState, functionState, blockState, builder, destructureM->localIndices[i], memberLE, destructureM->localsKnownLives[i]);
    buildFlare(FL(), globalState, functionState, builder);
  }
  buildFlare(FL(), globalState, functionState, builder);

  if (destructureM->structType->ownership == Ownership::OWN) {
    buildFlare(FL(), globalState, functionState, builder);
    globalState->getRegion(destructureM->structType)
        ->discardOwningRef(FL(), functionState, blockState, builder, destructureM->structType, structLiveRef);
  } else if (destructureM->structType->ownership == Ownership::MUTABLE_SHARE || destructureM->structType->ownership == Ownership::IMMUTABLE_SHARE) {
    buildFlare(FL(), globalState, functionState, builder);
    // We dont decrement anything here, we're only here because we already hit zero.

    globalState->getRegion(destructureM->structType)->deallocate(
        AFL("Destroy freeing"), functionState, builder,
        destructureM->structType, structLiveRef);
  } else {
    assert(false);
  }

  buildFlare(FL(), globalState, functionState, builder);

  return makeVoidRef(globalState);
}

Ref translateDestroySSAIntoLocals(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    DestroyStaticSizedArrayIntoLocals* destroySSAIntoLocalsM) {
  buildFlare(FL(), globalState, functionState, builder);
  auto mutability = ownershipToMutability(destroySSAIntoLocalsM->arrayType->ownership);

  auto structRegionInstanceRef =
      // At some point, look up the actual region instance, perhaps from the FunctionState?
      globalState->getRegion(destroySSAIntoLocalsM->arrayType)->createRegionInstanceLocal(functionState, builder);

  auto structRef =
      translateExpression(
          globalState, functionState, blockState, builder, destroySSAIntoLocalsM->arrayExpr);
  auto structLiveRef =
      globalState->getRegion(destroySSAIntoLocalsM->arrayType)->checkRefLive(FL(),
                                                                     functionState, builder, structRegionInstanceRef, destroySSAIntoLocalsM->arrayType, structRef, false);
  globalState->getRegion(destroySSAIntoLocalsM->arrayType)->checkValidReference(FL(),
                                                                        functionState, builder, true, destroySSAIntoLocalsM->arrayType, structRef);

  buildFlare(FL(), globalState, functionState, builder);

  auto ssaKind =
      dynamic_cast<StaticSizedArrayT *>(destroySSAIntoLocalsM->arrayType->kind);
  assert(ssaKind);

  auto ssaM = globalState->program->getStaticSizedArray(ssaKind);

  for (int i = 0; i < ssaM->size; i++) {
    buildFlare(FL(), globalState, functionState, builder);
    // We know it's in bounds because we used size as a bound for the loop.
    auto inBoundsIndexLE = InBoundsLE{constI64LE(globalState, i)};
    auto memberLoadResult =
        globalState->getRegion(destroySSAIntoLocalsM->arrayType)->loadElementFromSSA(
            functionState, builder, structRegionInstanceRef, destroySSAIntoLocalsM->arrayType, ssaKind, structLiveRef, inBoundsIndexLE);
    auto memberLE =
        globalState->getRegion(destroySSAIntoLocalsM->arrayType)->upgradeLoadResultToRefWithTargetOwnership(
            functionState, builder, structRegionInstanceRef, ssaM->elementType, ssaM->elementType, memberLoadResult, false);
    makeHammerLocal(
        globalState, functionState, blockState, builder, destroySSAIntoLocalsM->localIndices[i], memberLE, false);
    buildFlare(FL(), globalState, functionState, builder);
  }
  buildFlare(FL(), globalState, functionState, builder);

  if (destroySSAIntoLocalsM->arrayType->ownership == Ownership::OWN) {
    buildFlare(FL(), globalState, functionState, builder);
    globalState->getRegion(destroySSAIntoLocalsM->arrayType)
        ->discardOwningRef(FL(), functionState, blockState, builder, destroySSAIntoLocalsM->arrayType, structLiveRef);
  } else if (destroySSAIntoLocalsM->arrayType->ownership == Ownership::MUTABLE_SHARE || destroySSAIntoLocalsM->arrayType->ownership == Ownership::IMMUTABLE_SHARE) {
    buildFlare(FL(), globalState, functionState, builder);
    // We dont decrement anything here, we're only here because we already hit zero.

    globalState->getRegion(destroySSAIntoLocalsM->arrayType)->deallocate(
        AFL("Destroy freeing"), functionState, builder,
        destroySSAIntoLocalsM->arrayType, structLiveRef);
  } else {
    assert(false);
  }

  buildFlare(FL(), globalState, functionState, builder);

  return makeVoidRef(globalState);
}
