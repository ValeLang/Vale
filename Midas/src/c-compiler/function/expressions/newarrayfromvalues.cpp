#include <iostream>
#include <region/common/common.h>
#include "region/common/controlblock.h"
#include "function/expressions/shared/elements.h"

#include "translatetype.h"

#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "region/common/heap.h"

Ref translateNewArrayFromValues(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    NewArrayFromValues* newArrayFromValues) {

  auto elementsLE =
      translateExpressions(
          globalState, functionState, blockState, builder, newArrayFromValues->sourceExprs);
  auto ssaDefM = globalState->program->getStaticSizedArray(newArrayFromValues->arrayKind);
  for (auto elementLE : elementsLE) {
    globalState->getRegion(ssaDefM->elementType)
        ->checkValidReference(
            FL(), functionState, builder, ssaDefM->elementType, elementLE);
  }

  auto staticSizedArrayMT = dynamic_cast<StaticSizedArrayT*>(newArrayFromValues->arrayRefType->kind);

  if (newArrayFromValues->arrayRefType->location == Location::INLINE) {
//        auto valStructL =
//            globalState->getInnerStruct(structKind->fullName);
//        return constructInnerStruct(
//            builder, structM, valStructL, membersLE);
    assert(false);
  } else {
    // If we get here, arrayLT is a pointer to our counted struct.
    auto resultLE =
        globalState->getRegion(newArrayFromValues->arrayRefType)->constructStaticSizedArray(
            makeVoidRef(globalState),
            functionState,
            builder,
            newArrayFromValues->arrayRefType,
            newArrayFromValues->arrayKind);
    fillStaticSizedArray(
        globalState,
        functionState,
        builder,
        newArrayFromValues->arrayRefType,
        staticSizedArrayMT,
        resultLE,
        elementsLE);
    globalState->getRegion(newArrayFromValues->arrayRefType)->checkValidReference(FL(), functionState, builder,
        newArrayFromValues->arrayRefType, resultLE);
    return resultLE;
  }
}
