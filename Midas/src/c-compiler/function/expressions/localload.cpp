#include <iostream>
#include "region/common/controlblock.h"

#include "translatetype.h"

#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "region/common/heap.h"

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
  auto targetLocation = targetOwnership == Ownership::SHARE ? localType->location : Location::YONDER;
  auto resultType = globalState->metalCache.getReference(targetOwnership, targetLocation, localType->referend);

  auto localAddr = blockState->getLocalAddr(localId);


  auto sourceLE = LLVMBuildLoad(builder, localAddr, localName.c_str());
  auto sourceRef = wrap(functionState->defaultRegion, localType, sourceLE);
  checkValidReference(FL(), globalState, functionState, builder, localType, sourceRef);

  auto resultRefLE =
      upgradeLoadResultToRefWithTargetOwnership(
          globalState, functionState, builder, localType, resultType, sourceLE);
  functionState->defaultRegion->alias(FL(), functionState, builder, resultType, resultRefLE);
  return resultRefLE;
}
