#include <iostream>
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
  for (auto elementLE : elementsLE) {
    functionState->defaultRegion->checkValidReference(FL(), functionState, builder,
        newArrayFromValues->arrayReferend->rawArray->elementType, elementLE);
  }

  auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT*>(newArrayFromValues->arrayRefType->referend);

  switch (newArrayFromValues->arrayReferend->rawArray->mutability) {
//    case Mutability::MUTABLE: {
//      auto countedArrayL = globalState->getWrapperStruct(structReferend->fullName);
//      return constructCountedStruct(
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
            functionState->defaultRegion->constructKnownSizeArray(
                functionState,
                builder,
                newArrayFromValues->arrayRefType,
                newArrayFromValues->arrayReferend,
                elementsLE);
        functionState->defaultRegion->checkValidReference(FL(), functionState, builder,
            newArrayFromValues->arrayRefType, resultLE);
        return resultLE;
      }
    }
    default:
      assert(false);
  }
}
