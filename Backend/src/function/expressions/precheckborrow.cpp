#include <iostream>
#include "../../region/common/common.h"
#include "../../region/common/controlblock.h"
#include "shared/elements.h"

#include "../../translatetype.h"

#include "shared/members.h"
#include "../expression.h"
#include "shared/shared.h"
#include "../../region/common/heap.h"

Ref translatePreCheckBorrow(
    GlobalState *globalState,
    FunctionState *functionState,
    BlockState *blockState,
    LLVMBuilderRef builder,
    PreCheckBorrow *preCheckBorrowM) {
  auto sourceResultType = preCheckBorrowM->sourceResultType;
  auto structRegionInstanceRef =
      // At some point, look up the actual region instance, perhaps from the FunctionState?
      globalState->getRegion(preCheckBorrowM->sourceResultType)->createRegionInstanceLocal(functionState, builder);

  Ref result = translateExpression(globalState, functionState, blockState, builder, preCheckBorrowM->sourceExpr);

  bool knownLive = false;
  if (auto localLoad = dynamic_cast<LocalLoad*>(preCheckBorrowM->sourceExpr)) {
    if (localLoad->local->type->ownership == Ownership::OWN) {
      knownLive = true;
    }
  }

  return toRef(globalState, sourceResultType,
              globalState->getRegion(sourceResultType)
      ->preCheckBorrow(
          FL(), functionState, builder, structRegionInstanceRef, sourceResultType, result, knownLive));
}
