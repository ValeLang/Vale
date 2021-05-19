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
  auto knownSizeArrayMT = dynamic_cast<KnownSizeArrayT*>(staticArrayFromCallable->arrayRefType->referend);

  auto ksaDefMT = globalState->program->getKnownSizeArray(knownSizeArrayMT->name);
  auto sizeRef = globalState->constI64(ksaDefMT->size);

  auto generatorRef = translateExpression(globalState, functionState, blockState, builder, generatorExpr);
  globalState->getRegion(generatorType)->checkValidReference(FL(), functionState, builder,
      generatorType, generatorRef);

  std::unique_ptr<Ref> result;
  if (staticArrayFromCallable->arrayRefType->location == Location::INLINE) {
//        auto valStructL =
//            globalState->getInnerStruct(structReferend->fullName);
//        return constructInnerStruct(
//            builder, structM, valStructL, membersLE);
    assert(false);
  } else {
    // If we get here, arrayLT is a pointer to our counted struct.
    auto ksaRef =
        globalState->getRegion(staticArrayFromCallable->arrayRefType)->constructKnownSizeArray(
            makeEmptyTupleRef(globalState),
            functionState,
            builder,
            staticArrayFromCallable->arrayRefType,
            knownSizeArrayMT);

    buildFlare(FL(), globalState, functionState, builder);
    fillKnownSizeArrayFromCallable(
        globalState,
        functionState,
        builder,
        arrayRefType,
        knownSizeArrayMT,
        elementType,
        generatorType,
        staticArrayFromCallable->generatorMethod,
        generatorRef,
        sizeRef,
        ksaRef);//getUnknownSizeArrayContentsPtr(builder, usaWrapperPtrLE));
    buildFlare(FL(), globalState, functionState, builder);

    globalState->getRegion(staticArrayFromCallable->arrayRefType)->checkValidReference(FL(), functionState, builder,
        staticArrayFromCallable->arrayRefType, ksaRef);
    result.reset(new Ref(ksaRef));
  }

  globalState->getRegion(generatorType)->dealias(AFL("ConstructUSA"), functionState, builder, generatorType, generatorRef);

  return std::move(*result);
}
