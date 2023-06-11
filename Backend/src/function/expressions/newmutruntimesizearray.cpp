#include <iostream>
#include "../../region/common/common.h"
#include "../../region/common/controlblock.h"
#include "shared/elements.h"

#include "../../translatetype.h"

#include "shared/members.h"
#include "../expression.h"
#include "shared/shared.h"
#include "../../region/common/heap.h"

Ref translateNewMutRuntimeSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    NewMutRuntimeSizedArray* constructRuntimeSizedArray) {

  auto sizeKind = constructRuntimeSizedArray->sizeKind;
  auto sizeExpr = constructRuntimeSizedArray->sizeExpr;
  auto sizeType = constructRuntimeSizedArray->sizeType;
  auto elementType = constructRuntimeSizedArray->elementType;
  auto arrayRefType = constructRuntimeSizedArray->arrayRefType;

  auto runtimeSizedArrayMT = dynamic_cast<RuntimeSizedArrayT*>(constructRuntimeSizedArray->arrayRefType->kind);

  auto capacityRef = translateExpression(globalState, functionState, blockState, builder, sizeExpr);

  // If we get here, arrayLT is a pointer to our counted struct.
  auto rsaLiveRef =
      globalState->getRegion(arrayRefType)->constructRuntimeSizedArray(
          makeVoidRef(globalState),
          functionState,
          builder,
          arrayRefType,
          runtimeSizedArrayMT,
          capacityRef,
          runtimeSizedArrayMT->name->name);
  auto rsaRef = toRef(globalState, arrayRefType, rsaLiveRef);
  buildFlare(FL(), globalState, functionState, builder);
  globalState->getRegion(arrayRefType)
      ->checkValidReference(FL(), functionState, builder, true, arrayRefType, rsaRef);

  globalState->getRegion(sizeType)->dealias(AFL("ConstructRSA"), functionState, builder, sizeType, capacityRef);

  return rsaRef;
}
