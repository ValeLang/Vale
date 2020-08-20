#include <iostream>
#include "function/expressions/shared/controlblock.h"

#include "translatetype.h"

#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/heap.h"

LLVMValueRef translateLocalLoad(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    LocalLoad* localLoad) {
  auto local = localLoad->local;
  auto localId = local->id;
  auto localName = localLoad->localName;
  auto localType = getEffectiveType(globalState, local->type);
  auto targetOwnership = getEffectiveOwnership(globalState, localLoad->targetOwnership);
  auto targetLocation = targetOwnership == Ownership::SHARE ? localType->location : Location::YONDER;
  auto resultType = globalState->metalCache.getReference(targetOwnership, targetLocation, localType->referend);

  auto localAddr = blockState->getLocalAddr(localId);

  auto sourceRefLE = LLVMBuildLoad(builder, localAddr, localName.c_str());
  checkValidReference(FL(), globalState, functionState, builder, localType, sourceRefLE);

  auto resultRefLE = load(globalState, functionState, builder, localType, resultType, sourceRefLE);
  acquireReference(FL(), globalState, functionState, builder, resultType, resultRefLE);
  return resultRefLE;
}
