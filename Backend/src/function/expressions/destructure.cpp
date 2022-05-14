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

  auto structRef =
      translateExpression(
          globalState, functionState, blockState, builder, destructureM->structExpr);
  globalState->getRegion(destructureM->structType)->checkValidReference(FL(),
      functionState, builder, destructureM->structType, structRef);

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
            functionState, builder, globalState->makeTempRegionInstance(), destructureM->structType, structRef, true, i, memberType, memberType, memberName);
    makeHammerLocal(
        globalState, functionState, blockState, builder, destructureM->localIndices[i], memberLE, destructureM->localsKnownLives[i]);
    buildFlare(FL(), globalState, functionState, builder);
  }
  buildFlare(FL(), globalState, functionState, builder);

  if (destructureM->structType->ownership == Ownership::OWN) {
    buildFlare(FL(), globalState, functionState, builder);
    globalState->getRegion(destructureM->structType)->discardOwningRef(FL(), functionState, blockState, builder, destructureM->structType, structRef);
  } else if (destructureM->structType->ownership == Ownership::SHARE) {
    buildFlare(FL(), globalState, functionState, builder);
    // We dont decrement anything here, we're only here because we already hit zero.

    globalState->getRegion(destructureM->structType)->deallocate(
        AFL("Destroy freeing"), functionState, builder,
        destructureM->structType, structRef);
  } else {
    assert(false);
  }

  buildFlare(FL(), globalState, functionState, builder);

  return makeVoidRef(globalState);
}
