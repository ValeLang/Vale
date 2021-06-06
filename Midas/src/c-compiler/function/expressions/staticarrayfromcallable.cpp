#include <iostream>
#include <region/common/common.h>
#include "region/common/controlblock.h"
#include "function/expressions/shared/elements.h"

#include "translatetype.h"

#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "region/common/heap.h"

Ref translateStaticArrayFromCallable(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    StaticArrayFromCallable* staticArrayFromCallable) {

  auto generatorType = staticArrayFromCallable->generatorType;
  auto generatorExpr = staticArrayFromCallable->generatorExpr;
  auto elementType = staticArrayFromCallable->elementType;
  auto arrayRefType = staticArrayFromCallable->arrayRefType;
  auto staticSizedArrayMT = dynamic_cast<StaticSizedArrayT*>(staticArrayFromCallable->arrayRefType->kind);

  auto ssaDefMT = globalState->program->getStaticSizedArray(staticSizedArrayMT);
  auto sizeRef = globalState->constI32(ssaDefMT->size);

  auto generatorRef = translateExpression(globalState, functionState, blockState, builder, generatorExpr);
  globalState->getRegion(generatorType)->checkValidReference(FL(), functionState, builder,
      generatorType, generatorRef);

  std::unique_ptr<Ref> result;
  if (staticArrayFromCallable->arrayRefType->location == Location::INLINE) {
//        auto valStructL =
//            globalState->getInnerStruct(structKind->fullName);
//        return constructInnerStruct(
//            builder, structM, valStructL, membersLE);
    assert(false);
  } else {
    // If we get here, arrayLT is a pointer to our counted struct.
    auto ssaRef =
        globalState->getRegion(staticArrayFromCallable->arrayRefType)->constructStaticSizedArray(
            makeEmptyTupleRef(globalState),
            functionState,
            builder,
            staticArrayFromCallable->arrayRefType,
            staticSizedArrayMT);

    buildFlare(FL(), globalState, functionState, builder);
    fillStaticSizedArrayFromCallable(
        globalState,
        functionState,
        builder,
        arrayRefType,
        staticSizedArrayMT,
        elementType,
        generatorType,
        staticArrayFromCallable->generatorMethod,
        generatorRef,
        sizeRef,
        ssaRef);//getRuntimeSizedArrayContentsPtr(builder, rsaWrapperPtrLE));
    buildFlare(FL(), globalState, functionState, builder);

    globalState->getRegion(staticArrayFromCallable->arrayRefType)->checkValidReference(FL(), functionState, builder,
        staticArrayFromCallable->arrayRefType, ssaRef);
    result.reset(new Ref(ssaRef));
  }

  globalState->getRegion(generatorType)->dealias(AFL("ConstructRSA"), functionState, builder, generatorType, generatorRef);

  return std::move(*result);
}
