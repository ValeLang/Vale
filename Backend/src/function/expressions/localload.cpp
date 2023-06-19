#include <iostream>
#include "../../region/common/controlblock.h"

#include "../../translatetype.h"

#include "shared/members.h"
#include "../expression.h"
#include "shared/shared.h"
#include "../../region/common/heap.h"

Ref translateLocalLoad(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    LocalLoad* localLoad) {
  auto local = localLoad->local;
  auto localId = local->id;
  auto localName = localLoad->localName;
  auto localType = local->type;
  auto targetOwnership = localLoad->targetOwnership;
  auto targetLocation = targetOwnership == Ownership::MUTABLE_SHARE ? localType->location : Location::YONDER;
  auto resultType =
      globalState->metalCache->getReference(
          targetOwnership, targetLocation, localType->kind);

  auto regionInstanceRef =
      // At some point, look up the actual region instance, perhaps from the FunctionState?
      globalState->getRegion(localType)->createRegionInstanceLocal(functionState, builder);

  buildFlare(FL(), globalState, functionState, builder);

  auto localAddr = blockState->getLocalAddr(localId, true);

  auto sourceRef = globalState->getRegion(localType)->loadLocal(functionState, builder, local, localAddr);

  auto resultRef =
      globalState->getRegion(localType)->upgradeLoadResultToRefWithTargetOwnership(
          functionState, builder, regionInstanceRef, localType, resultType, LoadResult{sourceRef}, false);
  globalState->getRegion(resultType)->alias(FL(), functionState, builder, resultType, resultRef);

  return resultRef;
}
