#include <iostream>
#include "region/common/controlblock.h"
#include "function/expressions/shared/elements.h"

#include "translatetype.h"

#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "region/common/heap.h"

void fillUnknownSizeArray(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    UnknownSizeArrayT* usaMT,
    Reference* generatorType,
    Prototype* generatorMethod,
    Ref generatorLE,
    Ref sizeLE,
    LLVMValueRef usaElementsPtrLE) {

  foreachArrayElement(
      globalState, functionState, builder, sizeLE,
      [globalState, functionState, usaMT, generatorMethod, generatorType, usaElementsPtrLE, generatorLE](Ref indexRef, LLVMBuilderRef bodyBuilder) {
        functionState->defaultRegion->alias(
            AFL("ConstructUSA generate iteration"),
            functionState, bodyBuilder, generatorType, generatorLE);

        auto indexLE =
            globalState->region->checkValidReference(FL(),
                functionState, bodyBuilder, globalState->metalCache.intRef, indexRef);
        std::vector<LLVMValueRef> indices = { constI64LE(0), indexLE };

        auto elementPtrLE =
            LLVMBuildGEP(
                bodyBuilder, usaElementsPtrLE, indices.data(), indices.size(), "elementPtr");
        std::vector<Ref> argExprsLE = { generatorLE, indexRef };
        auto elementRef = buildInterfaceCall(globalState, functionState, bodyBuilder, generatorMethod, argExprsLE, 0, 0);
        auto elementLE =
            globalState->region->checkValidReference(FL(), functionState, bodyBuilder, usaMT->rawArray->elementType, elementRef);
        LLVMBuildStore(bodyBuilder, elementLE, elementPtrLE);
      });
}

Ref constructUnknownSizeArrayCountedStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* usaMT,
    UnknownSizeArrayT* unknownSizeArrayT,
    Reference* generatorType,
    Prototype* generatorMethod,
    Ref generatorRef,
    LLVMTypeRef usaWrapperPtrLT,
    LLVMTypeRef usaElementLT,
    Ref sizeRef,
    const std::string& typeName) {
  buildFlare(FL(), globalState, functionState, builder, "Constructing USA!");

  auto sizeLE =
      globalState->region->checkValidReference(FL(),
          functionState, builder, globalState->metalCache.intRef, sizeRef);
  auto usaWrapperPtrLE =
      functionState->defaultRegion->makeWrapperPtr(
          usaMT,
          mallocUnknownSizeArray(
              globalState, builder, usaWrapperPtrLT, usaElementLT, sizeLE));
  globalState->region->fillControlBlock(
      FL(),
      functionState,
      builder,
      unknownSizeArrayT,
      unknownSizeArrayT->rawArray->mutability,
      getConcreteControlBlockPtr(globalState, builder, usaWrapperPtrLE),
      typeName);
  LLVMBuildStore(builder, sizeLE, getUnknownSizeArrayLengthPtr(builder, usaWrapperPtrLE));
  fillUnknownSizeArray(
      globalState,
      functionState,
      blockState,
      builder,
      unknownSizeArrayT,
      generatorType,
      generatorMethod,
      generatorRef,
      sizeRef,
      getUnknownSizeArrayContentsPtr(builder, usaWrapperPtrLE));
  return wrap(functionState->defaultRegion, usaMT, usaWrapperPtrLE.refLE);
}

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
  globalState->region->checkValidReference(FL(), functionState, builder,
      constructUnknownSizeArray->generatorType, generatorLE);

  // If we get here, arrayLT is a pointer to our counted struct.
  auto unknownSizeArrayCountedStructLT =
      globalState->region->getReferendStructsSource()->getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT);
  auto usaRef =
      constructUnknownSizeArrayCountedStruct(
          globalState,
          functionState,
          blockState,
          builder,
          constructUnknownSizeArray->arrayRefType,
          unknownSizeArrayMT,
          generatorType,
          constructUnknownSizeArray->generatorMethod,
          generatorLE,
          unknownSizeArrayCountedStructLT,
          usaElementLT,
          sizeLE,
          unknownSizeArrayMT->name->name);
  globalState->region->checkValidReference(FL(), functionState, builder,
      constructUnknownSizeArray->arrayRefType, usaRef);

  functionState->defaultRegion->dealias(AFL("ConstructUSA"), functionState, blockState, builder, sizeType, sizeLE);
  functionState->defaultRegion->dealias(AFL("ConstructUSA"), functionState, blockState, builder, generatorType, generatorLE);

  return usaRef;
}
