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

void Assist::alias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    Ref expr) {
  auto sourceRnd = sourceRef->referend;

  if (dynamic_cast<Int *>(sourceRnd) ||
      dynamic_cast<Bool *>(sourceRnd) ||
      dynamic_cast<Float *>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (dynamic_cast<InterfaceReferend *>(sourceRnd) ||
      dynamic_cast<StructReferend *>(sourceRnd) ||
      dynamic_cast<KnownSizeArrayT *>(sourceRnd) ||
      dynamic_cast<UnknownSizeArrayT *>(sourceRnd) ||
      dynamic_cast<Str *>(sourceRnd)) {
    if (sourceRef->ownership == Ownership::OWN) {
      // We might be loading a member as an own if we're destructuring.
      // Don't adjust the RC, since we're only moving it.
    } else if (sourceRef->ownership == Ownership::BORROW) {
      adjustStrongRc(from, globalState, functionState, builder, expr, sourceRef, 1);
    } else if (sourceRef->ownership == Ownership::WEAK) {
      aliasWeakRef(from, globalState, functionState, builder, sourceRef, expr);
    } else if (sourceRef->ownership == Ownership::SHARE) {
      if (sourceRef->location == Location::INLINE) {
        // Do nothing, we can just let inline structs disappear
      } else {
        adjustStrongRc(from, globalState, functionState, builder, expr, sourceRef, 1);
      }
    } else
      assert(false);
  } else {
    std::cerr << "Unimplemented type in acquireReference: "
        << typeid(*sourceRef->referend).name() << std::endl;
    assert(false);
  }
}


void Assist::dealias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  if (sourceMT->ownership == Ownership::OWN) {
    // We can't discard owns, they must be destructured.
    assert(false);
  } else if (sourceMT->ownership == Ownership::BORROW) {
    adjustStrongRc(from, globalState, functionState, builder, sourceRef, sourceMT, -1);
  } else if (sourceMT->ownership == Ownership::WEAK) {
    discardWeakRef(from, globalState, functionState, builder, sourceMT, sourceRef);
  } else assert(false);
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
                globalState,
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
      return defaultRefCounting.translateType(globalState, referenceM);
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

Ref Assist::weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) {
  assert(sourceRefMT->ownership == Ownership::BORROW);
  if (auto structReferendM = dynamic_cast<StructReferend*>(sourceRefMT->referend)) {
    auto objPtrLE =
        WrapperPtrLE(
            sourceRefMT,
            ::checkValidReference(FL(), globalState, functionState, builder, sourceRefMT, sourceRef));
    return wrap(
        functionState->defaultRegion,
        targetRefMT,
        assembleStructWeakRef(
            globalState, functionState, builder,
            sourceRefMT, targetRefMT, structReferendM, objPtrLE));
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(sourceRefMT->referend)) {
    assert(false); // impl
  } else assert(false);
}
