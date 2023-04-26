#include <iostream>
#include "../../region/common/common.h"
#include "../../region/common/controlblock.h"
#include "shared/elements.h"

#include "../../translatetype.h"

#include "shared/members.h"
#include "../expression.h"
#include "shared/shared.h"
#include "../../region/common/heap.h"

Ref translateNewImmRuntimeSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    NewImmRuntimeSizedArray* constructRuntimeSizedArray) {

  auto generatorType = constructRuntimeSizedArray->generatorType;
  auto generatorExpr = constructRuntimeSizedArray->generatorExpr;
  auto sizeKind = constructRuntimeSizedArray->sizeKind;
  auto sizeExpr = constructRuntimeSizedArray->sizeExpr;
  auto sizeType = constructRuntimeSizedArray->sizeType;
  auto elementType = constructRuntimeSizedArray->elementType;
  auto arrayRefType = constructRuntimeSizedArray->arrayRefType;

  auto runtimeSizedArrayMT = dynamic_cast<RuntimeSizedArrayT*>(constructRuntimeSizedArray->arrayRefType->kind);

  auto capacityRef = translateExpression(globalState, functionState, blockState, builder, sizeExpr);

  auto generatorRef = translateExpression(globalState, functionState, blockState, builder, generatorExpr);
  globalState->getRegion(generatorType)
      ->checkValidReference(FL(), functionState, builder, true, generatorType, generatorRef);

  // If we get here, arrayLT is a pointer to our counted struct.
  auto rsaRef =
      globalState->getRegion(arrayRefType)->constructRuntimeSizedArray(
          makeVoidRef(globalState),
          functionState,
          builder,
          arrayRefType,
          runtimeSizedArrayMT,
          capacityRef,
          runtimeSizedArrayMT->name->name);
  buildFlare(FL(), globalState, functionState, builder);
//  globalState->getRegion(arrayRefType)
//      ->checkValidReference(FL(), functionState, builder, true, arrayRefType, rsaRef.inner);

  auto arrayRegionInstanceRef =
      // At some point, look up the actual region instance, perhaps from the FunctionState?
      globalState->getRegion(arrayRefType)->createRegionInstanceLocal(functionState, builder);

  buildFlare(FL(), globalState, functionState, builder);
  fillRuntimeSizedArray(
      globalState,
      functionState,
      builder,
      arrayRegionInstanceRef,
      arrayRefType,
      runtimeSizedArrayMT,
      elementType,
      generatorType,
      constructRuntimeSizedArray->generatorMethod,
      generatorRef,
      capacityRef,
      rsaRef);//getRuntimeSizedArrayContentsPtr(builder, rsaWrapperPtrLE));
  buildFlare(FL(), globalState, functionState, builder);

  globalState->getRegion(sizeType)->dealias(AFL("ConstructRSA"), functionState, builder, sizeType, capacityRef);
  globalState->getRegion(generatorType)->dealias(AFL("ConstructRSA"), functionState, builder, generatorType, generatorRef);

  return wrap(globalState, elementType, rsaRef);
}
