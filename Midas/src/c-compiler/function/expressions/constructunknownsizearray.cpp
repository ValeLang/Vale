#include <iostream>
#include "region/common/controlblock.h"
#include "function/expressions/shared/elements.h"

#include "translatetype.h"

#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "region/common/heap.h"

Ref translateConstructUnknownSizeArray(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    ConstructUnknownSizeArray* constructUnknownSizeArray) {

  auto generatorType = constructUnknownSizeArray->generatorType;
  auto generatorExpr = constructUnknownSizeArray->generatorExpr;
  auto sizeReferend = constructUnknownSizeArray->sizeReferend;
  auto sizeExpr = constructUnknownSizeArray->sizeExpr;
  auto sizeType = constructUnknownSizeArray->sizeType;

  auto unknownSizeArrayMT = dynamic_cast<UnknownSizeArrayT*>(constructUnknownSizeArray->arrayRefType->referend);

  auto usaWrapperPtrLT = functionState->defaultRegion->translateType(constructUnknownSizeArray->arrayRefType);
  auto usaElementLT = functionState->defaultRegion->translateType(unknownSizeArrayMT->rawArray->elementType);

  auto sizeLE = translateExpression(globalState, functionState, blockState, builder, sizeExpr);

  auto generatorLE = translateExpression(globalState, functionState, blockState, builder, generatorExpr);
  functionState->defaultRegion->checkValidReference(FL(), functionState, builder,
      constructUnknownSizeArray->generatorType, generatorLE);

  // If we get here, arrayLT is a pointer to our counted struct.
  auto usaRef =
      functionState->defaultRegion->constructUnknownSizeArrayCountedStruct(
          functionState,
          blockState,
          builder,
          constructUnknownSizeArray->arrayRefType,
          unknownSizeArrayMT,
          generatorType,
          constructUnknownSizeArray->generatorMethod,
          generatorLE,
          usaElementLT,
          sizeLE,
          unknownSizeArrayMT->name->name);
  functionState->defaultRegion->checkValidReference(FL(), functionState, builder,
      constructUnknownSizeArray->arrayRefType, usaRef);

  functionState->defaultRegion->dealias(AFL("ConstructUSA"), functionState, blockState, builder, sizeType, sizeLE);
  functionState->defaultRegion->dealias(AFL("ConstructUSA"), functionState, blockState, builder, generatorType, generatorLE);

  return usaRef;
}
