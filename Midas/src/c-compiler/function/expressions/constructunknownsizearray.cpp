#include <iostream>
#include "function/expressions/shared/controlblock.h"
#include "function/expressions/shared/elements.h"

#include "translatetype.h"

#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/heap.h"

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
      globalState, functionState, builder, sizeLE, usaElementsPtrLE,
      [globalState, functionState, usaMT, generatorMethod, generatorType, usaElementsPtrLE, generatorLE](Ref indexRef, LLVMBuilderRef bodyBuilder) {
        acquireReference(
            AFL("ConstructUSA generate iteration"),
            globalState, functionState, bodyBuilder, generatorType, generatorLE);

        auto indexLE =
            checkValidReference(
                FL(), globalState, functionState, bodyBuilder, globalState->metalCache.intRef, indexRef);
        std::vector<LLVMValueRef> indices = { constI64LE(0), indexLE };

        auto elementPtrLE =
            LLVMBuildGEP(
                bodyBuilder, usaElementsPtrLE, indices.data(), indices.size(), "elementPtr");
        std::vector<Ref> argExprsLE = { generatorLE, indexRef };
        auto elementRef = buildInterfaceCall(globalState, functionState, bodyBuilder, generatorMethod, argExprsLE, 0, 0);
        auto elementLE =
            checkValidReference(FL(), globalState, functionState, bodyBuilder, usaMT->rawArray->elementType, elementRef);
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
      checkValidReference(FL(),globalState, functionState, builder, globalState->metalCache.intRef, sizeRef);
  auto usaWrapperPtrLE =
      WrapperPtrLE(
          usaMT,
          mallocUnknownSizeArray(
              globalState, builder, usaWrapperPtrLT, usaElementLT, sizeLE));
  fillControlBlock(
      FL(),
      globalState,
      functionState,
      builder,
      unknownSizeArrayT,
      unknownSizeArrayT->rawArray->mutability,
      getEffectiveWeakability(globalState, unknownSizeArrayT->rawArray),
      getConcreteControlBlockPtr(builder, usaWrapperPtrLE),
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
  checkValidReference(FL(), globalState, functionState, builder,
      constructUnknownSizeArray->generatorType, generatorLE);

  // If we get here, arrayLT is a pointer to our counted struct.
  auto unknownSizeArrayCountedStructLT =
      globalState->getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT->name);
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
  checkValidReference(FL(), globalState, functionState, builder,
      constructUnknownSizeArray->arrayRefType, usaRef);

  discard(AFL("ConstructUSA"), globalState, functionState, blockState, builder, sizeType, sizeLE);
  discard(AFL("ConstructUSA"), globalState, functionState, blockState, builder, generatorType, generatorLE);

  return usaRef;
}
