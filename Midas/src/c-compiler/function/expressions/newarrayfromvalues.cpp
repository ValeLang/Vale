#include <iostream>
#include "function/expressions/shared/controlblock.h"
#include "function/expressions/shared/elements.h"

#include "translatetype.h"

#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/heap.h"

void fillKnownSizeArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* elementMT,
    LLVMValueRef arrayLE,
    const std::vector<Ref>& elementsLE) {

  for (int i = 0; i < elementsLE.size(); i++) {
    auto memberName = std::string("element") + std::to_string(i);
    LLVMValueRef indices[2] = {
        LLVMConstInt(LLVMInt64Type(), 0, false),
        LLVMConstInt(LLVMInt64Type(), i, false),
    };
    auto elementLE = checkValidReference(FL(), globalState, functionState, builder, elementMT, elementsLE[i]);
    // Every time we fill in a field, it actually makes a new entire
    // struct value, and gives us a LLVMValueRef for the new value.
    // So, `structValueBeingInitialized` contains the latest one.
    LLVMBuildStore(
        builder,
        elementLE,
        LLVMBuildGEP(builder, arrayLE, indices, 2, memberName.c_str()));
  }
}

Ref constructKnownSizeArrayCountedStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    KnownSizeArrayT* knownSizeArrayT,
    LLVMTypeRef structLT,
    const std::vector<Ref>& membersLE,
    const std::string& typeName) {
  auto newStructLE =
      WrapperPtrLE(refM, mallocKnownSize(globalState, functionState, builder, refM->location, structLT));
  fillControlBlock(
      FL(),
      globalState,
      functionState,
      builder,
      refM->referend,
      knownSizeArrayT->rawArray->mutability,
      getEffectiveWeakability(globalState, knownSizeArrayT->rawArray),
      getConcreteControlBlockPtr(builder, newStructLE),
      typeName);
  fillKnownSizeArray(
      globalState,
      functionState,
      builder,
      knownSizeArrayT->rawArray->elementType,
      getKnownSizeArrayContentsPtr(builder, newStructLE),
      membersLE);
  return wrap(functionState->defaultRegion, refM, newStructLE.refLE);
}

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
    checkValidReference(FL(), globalState, functionState, builder,
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
        auto knownSizeArrayCountedStructLT =
                globalState->getKnownSizeArrayWrapperStruct(knownSizeArrayMT->name);
        auto resultLE =
            constructKnownSizeArrayCountedStruct(
                globalState,
                functionState,
                builder,
                newArrayFromValues->arrayRefType,
                newArrayFromValues->arrayReferend,
                knownSizeArrayCountedStructLT,
                elementsLE,
                knownSizeArrayMT->name->name);
        checkValidReference(FL(), globalState, functionState, builder,
            newArrayFromValues->arrayRefType, resultLE);
        return resultLE;
      }
    }
    default:
      assert(false);
  }
}
