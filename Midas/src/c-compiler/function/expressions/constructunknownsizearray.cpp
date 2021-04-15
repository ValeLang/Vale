#include <iostream>
#include <region/common/common.h>
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
  auto elementType = constructUnknownSizeArray->elementType;
  auto arrayRefType = constructUnknownSizeArray->arrayRefType;

  auto unknownSizeArrayMT = dynamic_cast<UnknownSizeArrayT*>(constructUnknownSizeArray->arrayRefType->referend);

  auto sizeRef = translateExpression(globalState, functionState, blockState, builder, sizeExpr);

  auto generatorRef = translateExpression(globalState, functionState, blockState, builder, generatorExpr);
  globalState->getRegion(generatorType)->checkValidReference(FL(), functionState, builder,
      generatorType, generatorRef);

  // If we get here, arrayLT is a pointer to our counted struct.
  auto usaRef =
      globalState->getRegion(arrayRefType)->constructUnknownSizeArray(
          makeEmptyTupleRef(globalState),
          functionState,
          builder,
          arrayRefType,
          unknownSizeArrayMT,
          sizeRef,
          unknownSizeArrayMT->name->name);
  buildFlare(FL(), globalState, functionState, builder);
  globalState->getRegion(arrayRefType)->checkValidReference(FL(), functionState, builder,
      arrayRefType, usaRef);

  buildFlare(FL(), globalState, functionState, builder);
  fillUnknownSizeArray(
      globalState,
      functionState,
      builder,
      arrayRefType,
      unknownSizeArrayMT,
      elementType,
      generatorType,
      constructUnknownSizeArray->generatorMethod,
      generatorRef,
      sizeRef,
      usaRef);//getUnknownSizeArrayContentsPtr(builder, usaWrapperPtrLE));
  buildFlare(FL(), globalState, functionState, builder);

  globalState->getRegion(sizeType)->dealias(AFL("ConstructUSA"), functionState, builder, sizeType, sizeRef);
  globalState->getRegion(generatorType)->dealias(AFL("ConstructUSA"), functionState, builder, generatorType, generatorRef);

  return usaRef;
}
