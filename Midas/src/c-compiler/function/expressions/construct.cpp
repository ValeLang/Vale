#include <iostream>
#include "region/common/controlblock.h"

#include "translatetype.h"

#include "function/expressions/shared/ref.h"
#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "region/common/heap.h"

Ref translateConstruct(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* desiredReference,
    const std::vector<Ref>& memberRefs) {
  return globalState->getRegion(desiredReference)
      ->allocate(
          makeVoidRef(globalState), from, functionState, builder, desiredReference, memberRefs);
}
