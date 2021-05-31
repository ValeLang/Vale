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
  auto ssaDefM = globalState->program->getStaticSizedArray(newArrayFromValues->arrayReferend->name);
  for (auto elementLE : elementsLE) {
    globalState->getRegion(ssaDefM->rawArray->elementType)
        ->checkValidReference(
            FL(), functionState, builder, ssaDefM->rawArray->elementType, elementLE);
  }

  auto staticSizedArrayMT = dynamic_cast<StaticSizedArrayT*>(newArrayFromValues->arrayRefType->referend);

  if (newArrayFromValues->arrayRefType->location == Location::INLINE) {
//        auto valStructL =
//            globalState->getInnerStruct(structReferend->fullName);
//        return constructInnerStruct(
//            builder, structM, valStructL, membersLE);
    assert(false);
  } else {
    // If we get here, arrayLT is a pointer to our counted struct.
    auto resultLE =
        globalState->getRegion(newArrayFromValues->arrayRefType)->constructStaticSizedArray(
            makeEmptyTupleRef(globalState),
            functionState,
            builder,
            newArrayFromValues->arrayRefType,
            newArrayFromValues->arrayReferend);
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
