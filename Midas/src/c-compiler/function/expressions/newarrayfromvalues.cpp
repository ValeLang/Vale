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
  auto ksaDefM = globalState->program->getKnownSizeArray(newArrayFromValues->arrayReferend->name);
  for (auto elementLE : elementsLE) {
    globalState->getRegion(ksaDefM->rawArray->elementType)
        ->checkValidReference(
            FL(), functionState, builder, ksaDefM->rawArray->elementType, elementLE);
  }

  auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT*>(newArrayFromValues->arrayRefType->referend);

  switch (ksaDefM->rawArray->mutability) {
//    case Mutability::MUTABLE: {
//      auto countedArrayL = globalState->getWrapperStruct(structReferend->fullName);
//      return constructWrappedStruct(
//          globalState, builder, countedStructL, structM, membersLE);
//    }
    case Mutability::IMMUTABLE: {
      if (newArrayFromValues->arrayRefType->location == Location::INLINE) {
//        auto valStructL =
//            globalState->getInnerStruct(structReferend->fullName);
//        return constructInnerStruct(
//            builder, structM, valStructL, membersLE);
        assert(false);
      } else {
        // If we get here, arrayLT is a pointer to our counted struct.
        auto resultLE =
            globalState->getRegion(newArrayFromValues->arrayRefType)->constructKnownSizeArray(
                makeEmptyTupleRef(globalState),
                functionState,
                builder,
                newArrayFromValues->arrayRefType,
                newArrayFromValues->arrayReferend);
        fillKnownSizeArray(
            globalState,
            functionState,
            builder,
            newArrayFromValues->arrayRefType,
            knownSizeArrayMT,
            resultLE,
            elementsLE);
        globalState->getRegion(newArrayFromValues->arrayRefType)->checkValidReference(FL(), functionState, builder,
            newArrayFromValues->arrayRefType, resultLE);
        return resultLE;
      }
    }
    default:
      assert(false);
  }
}
