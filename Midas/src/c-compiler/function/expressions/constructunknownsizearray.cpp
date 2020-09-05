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
    Reference* generatorType,
    LLVMValueRef generatorLE,
    LLVMValueRef sizeLE,
    LLVMValueRef usaElementsPtrLE) {

  foreachArrayElement(
      functionState, builder, sizeLE, usaElementsPtrLE,
      [globalState, functionState, generatorType, usaElementsPtrLE, generatorLE](LLVMValueRef indexLE, LLVMBuilderRef bodyBuilder) {
        acquireReference(
            AFL("ConstructUSA generate iteration"),
            globalState, functionState, bodyBuilder, generatorType, generatorLE);

        std::vector<LLVMValueRef> indices = { constI64LE(0), indexLE };
        auto elementPtrLE =
            LLVMBuildGEP(
                bodyBuilder, usaElementsPtrLE, indices.data(), indices.size(), "elementPtr");
        std::vector<LLVMValueRef> argExprsLE = { generatorLE, indexLE };
        auto elementLE = buildInterfaceCall(globalState, functionState, bodyBuilder, generatorType, argExprsLE, 0, 0);
        LLVMBuildStore(bodyBuilder, elementLE, elementPtrLE);
      });
}

LLVMValueRef constructUnknownSizeArrayCountedStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    UnknownSizeArrayT* unknownSizeArrayT,
    Reference* generatorType,
    LLVMValueRef generatorLE,
    LLVMTypeRef usaWrapperPtrLT,
    LLVMTypeRef usaElementLT,
    LLVMValueRef sizeLE,
    const std::string& typeName) {
  buildFlare(FL(), globalState, functionState, builder, "Constructing USA!");

  auto usaWrapperPtrLE =
      mallocUnknownSizeArray(
          globalState, builder, usaWrapperPtrLT, usaElementLT, sizeLE);
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
  LLVMBuildStore(builder, sizeLE, LLVMBuildStructGEP(builder, usaWrapperPtrLE, 1, "lenPtr"));
  fillUnknownSizeArray(
      globalState,
      functionState,
      blockState,
      builder,
      generatorType,
      generatorLE,
      sizeLE,
      getContentsPtrFromUnknownSizeArrayWrapperPtr(
          builder, usaWrapperPtrLE));
  return usaWrapperPtrLE;
}

LLVMValueRef translateConstructUnknownSizeArray(
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

  auto usaWrapperPtrLT = translateType(globalState, constructUnknownSizeArray->arrayRefType);
  auto usaElementLT = translateType(globalState, unknownSizeArrayMT->rawArray->elementType);

  auto sizeLE = translateExpression(globalState, functionState, blockState, builder, sizeExpr);

  auto generatorLE = translateExpression(globalState, functionState, blockState, builder, generatorExpr);
  checkValidReference(FL(), globalState, functionState, builder,
      constructUnknownSizeArray->generatorType, generatorLE);

  // If we get here, arrayLT is a pointer to our counted struct.
  auto unknownSizeArrayCountedStructLT =
      globalState->getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT->name);
  auto resultLE =
      constructUnknownSizeArrayCountedStruct(
          globalState,
      functionState,
      blockState,
      builder,
          unknownSizeArrayMT,
          generatorType,
          generatorLE,
          unknownSizeArrayCountedStructLT,
          usaElementLT,
          sizeLE,
          unknownSizeArrayMT->name->name);
  checkValidReference(FL(), globalState, functionState, builder,
      constructUnknownSizeArray->arrayRefType, resultLE);

  discard(AFL("ConstructUSA"), globalState, functionState, blockState, builder, sizeType, sizeLE);
  discard(AFL("ConstructUSA"), globalState, functionState, blockState, builder, generatorType, generatorLE);

  return resultLE;
}
