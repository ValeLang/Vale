#include <function/expressions/shared/weaks.h>
#include <function/expressions/shared/branch.h>
#include <region/common/fatweaks/fatweaks.h>
#include <region/common/hgm/hgm.h>
#include <region/common/lgtweaks/lgtweaks.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include <translatetype.h>
#include <region/common/common.h>
#include "assist.h"


Assist::Assist(GlobalState* globalState_) :
    Mega(globalState_) {
}

Ref Assist::lockWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference* resultOptTypeM,
    Reference* constraintRefM,
    Reference* sourceWeakRefMT,
    Ref sourceWeakRef,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {
  auto isAliveLE =
      getIsAliveFromWeakRef(globalState, functionState, builder, sourceWeakRefMT, sourceWeakRef);
  auto resultOptTypeLE = translateType(resultOptTypeM);
  return buildIfElse(
      globalState, functionState, builder, isAliveLE, resultOptTypeLE, resultOptTypeM, resultOptTypeM,
      [this, functionState, sourceWeakRef, constraintRefM, sourceWeakRefMT, buildThen](
          LLVMBuilderRef thenBuilder) -> Ref {
        auto sourceWeakRefLE =
            WeakFatPtrLE(
                sourceWeakRefMT,
                ::checkValidReference(FL(), globalState, functionState, thenBuilder, sourceWeakRefMT, sourceWeakRef));
        auto constraintRefLE =
            fatWeaks.getInnerRefFromWeakRef(
                globalState, functionState, thenBuilder, sourceWeakRefMT, sourceWeakRefLE);
        auto constraintRef =
            wrap(functionState->defaultRegion, constraintRefM, constraintRefLE);
        return buildThen(thenBuilder, constraintRef);
      },
      buildElse);
}

LLVMTypeRef Assist::translateType(Reference* referenceM) {
  switch (referenceM->ownership) {
    case Ownership::SHARE:
      return immutables.translateType(globalState, referenceM);
    case Ownership::OWN:
    case Ownership::BORROW:
      assert(referenceM->location != Location::INLINE);
      return translateReferenceSimple(globalState, referenceM->referend);
    case Ownership::WEAK:
      assert(referenceM->location != Location::INLINE);
      return translateWeakReference(globalState, referenceM->referend);
    default:
      assert(false);
  }
}

LLVMValueRef Assist::upcastWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {
  return wrcWeaks.weakStructPtrToWrciWeakInterfacePtr(
      globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
      sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
}

void Assist::declareKnownSizeArray(
    KnownSizeArrayT* knownSizeArrayMT) {
  defaultLayout.declareKnownSizeArray(knownSizeArrayMT);
}

void Assist::declareUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT) {
  defaultLayout.declareUnknownSizeArray(unknownSizeArrayMT);
}

void Assist::translateUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT) {
  auto elementLT =
      translateType(
          unknownSizeArrayMT->rawArray->elementType);
  defaultLayout.translateUnknownSizeArray(unknownSizeArrayMT, elementLT);
}

void Assist::translateKnownSizeArray(
    KnownSizeArrayT* knownSizeArrayMT) {
  auto elementLT =
      translateType(
          knownSizeArrayMT->rawArray->elementType);
  defaultLayout.translateKnownSizeArray(knownSizeArrayMT, elementLT);
}

void Assist::declareStruct(
    StructDefinition* structM) {
  defaultLayout.declareStruct(structM);
}

void Assist::translateStruct(
    StructDefinition* structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        translateType(
            structM->members[i]->type));
  }
  defaultLayout.translateStruct(
      structM->name,
      structM->mutability,
      getEffectiveWeakability(globalState, structM),
      innerStructMemberTypesL);
}

void Assist::declareEdge(
    Edge* edge) {
  defaultLayout.declareEdge(edge);
}

void Assist::translateEdge(
    Edge* edge) {
  std::vector<LLVMValueRef> functions;
  for (int i = 0; i < edge->structPrototypesByInterfaceMethod.size(); i++) {
    auto funcName = edge->structPrototypesByInterfaceMethod[i].second->name;
    functions.push_back(globalState->getFunction(funcName));
  }
  defaultLayout.translateEdge(edge, functions);
}

void Assist::declareInterface(
    InterfaceDefinition* interfaceM) {
  defaultLayout.declareInterface(interfaceM->name);
}

void Assist::translateInterface(
    InterfaceDefinition* interfaceM) {
  std::vector<LLVMTypeRef> interfaceMethodTypesL;
  for (int i = 0; i < interfaceM->methods.size(); i++) {
    interfaceMethodTypesL.push_back(
        LLVMPointerType(
            translateInterfaceMethodToFunctionType(interfaceM->methods[i]),
            0));
  }
  defaultLayout.translateInterface(
      interfaceM->name,
      interfaceM->mutability,
      getEffectiveWeakability(globalState, interfaceM),
      interfaceMethodTypesL);
}

LLVMTypeRef Assist::translateInterfaceMethodToFunctionType(
    InterfaceMethod* method) {
  auto returnMT = method->prototype->returnType;
  auto paramsMT = method->prototype->params;
  auto returnLT = translateType(returnMT);
  auto paramsLT = translateTypes(globalState, this, paramsMT);

  switch (paramsMT[method->virtualParamIndex]->ownership) {
    case Ownership::BORROW:
    case Ownership::OWN:
    case Ownership::SHARE:
      paramsLT[method->virtualParamIndex] = LLVMPointerType(LLVMVoidType(), 0);
      break;
    case Ownership::WEAK:
      paramsLT[method->virtualParamIndex] = globalState->weakVoidRefStructL;
      break;
  }

  return LLVMFunctionType(returnLT, paramsLT.data(), paramsLT.size(), false);
}
