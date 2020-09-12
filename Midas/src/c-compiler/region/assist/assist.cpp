#include <function/expressions/shared/weaks.h>
#include <utils/branch.h>
#include <region/common/fatweaks/fatweaks.h>
#include <region/common/hgm/hgm.h>
#include <region/common/lgtweaks/lgtweaks.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include <translatetype.h>
#include <region/common/common.h>
#include <region/common/heap.h>
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
  if (sourceMT->ownership == Ownership::SHARE) {
    defaultImmutables.discard(from, globalState, functionState, blockState, builder, sourceMT, sourceRef);
  } else if (sourceMT->ownership == Ownership::OWN) {
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
            weakFatPtrMaker.make(
                sourceWeakRefMT,
                checkValidReference(FL(), functionState, thenBuilder, sourceWeakRefMT, sourceWeakRef));
        auto constraintRefLE =
            fatWeaks.getInnerRefFromWeakRef(
                globalState, functionState, thenBuilder, sourceWeakRefMT, sourceWeakRefLE);
        auto constraintRef =
            wrap(this, constraintRefM, constraintRefLE);
        return buildThen(thenBuilder, constraintRef);
      },
      buildElse);
}

LLVMTypeRef Assist::translateType(Reference* referenceM) {
  switch (referenceM->ownership) {
    case Ownership::SHARE:
      return defaultImmutables.translateType(globalState, referenceM);
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

Ref Assist::upcastWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {
  return wrap(
      this,
      targetInterfaceTypeM,
      wrcWeaks.weakStructPtrToWrciWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
          sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM));
}

void Assist::declareKnownSizeArray(
    KnownSizeArrayT* knownSizeArrayMT) {
  referendStructs.declareKnownSizeArray(knownSizeArrayMT);
}

void Assist::declareUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT) {
  referendStructs.declareUnknownSizeArray(unknownSizeArrayMT);
}

void Assist::translateUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT) {
  auto elementLT =
      translateType(
          unknownSizeArrayMT->rawArray->elementType);
  referendStructs.translateUnknownSizeArray(unknownSizeArrayMT, elementLT);
}

void Assist::translateKnownSizeArray(
    KnownSizeArrayT* knownSizeArrayMT) {
  auto elementLT =
      translateType(
          knownSizeArrayMT->rawArray->elementType);
  referendStructs.translateKnownSizeArray(knownSizeArrayMT, elementLT);
}

void Assist::declareStruct(
    StructDefinition* structM) {
  referendStructs.declareStruct(structM);
}

void Assist::translateStruct(
    StructDefinition* structM) {
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        translateType(
            structM->members[i]->type));
  }
  referendStructs.translateStruct(
      structM,
      innerStructMemberTypesL);
}

void Assist::declareEdge(
    Edge* edge) {
  referendStructs.declareEdge(edge);
}

void Assist::translateEdge(
    Edge* edge) {
  std::vector<LLVMValueRef> functions;
  for (int i = 0; i < edge->structPrototypesByInterfaceMethod.size(); i++) {
    auto funcName = edge->structPrototypesByInterfaceMethod[i].second->name;
    functions.push_back(globalState->getFunction(funcName));
  }
  referendStructs.translateEdge(edge, functions);
}

void Assist::declareInterface(
    InterfaceDefinition* interfaceM) {
  referendStructs.declareInterface(interfaceM);
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
  referendStructs.translateInterface(
      interfaceM,
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
      paramsLT[method->virtualParamIndex] = mutWeakableStructs.weakVoidRefStructL;
      break;
  }

  return LLVMFunctionType(returnLT, paramsLT.data(), paramsLT.size(), false);
}

Ref Assist::weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) {
  assert(sourceRefMT->ownership == Ownership::BORROW);
  if (auto structReferendM = dynamic_cast<StructReferend*>(sourceRefMT->referend)) {
    auto objPtrLE =
        makeWrapperPtr(
            sourceRefMT,
            globalState->region->checkValidReference(FL(), functionState, builder, sourceRefMT,
                sourceRef));
    return wrap(
        this,
        targetRefMT,
        assembleStructWeakRef(
            globalState, functionState, builder,
            sourceRefMT, targetRefMT, structReferendM, objPtrLE));
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(sourceRefMT->referend)) {
    assert(false); // impl
  } else assert(false);
}

void Assist::discardOwningRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto exprWrapperPtrLE =
      makeWrapperPtr(
          sourceMT,
          checkValidReference(FL(), functionState, builder, sourceMT, sourceRef));

  adjustStrongRc(
      AFL("Destroy decrementing the owning ref"),
      globalState, functionState, builder, sourceRef, sourceMT, -1);
  // No need to check the RC, we know we're freeing right now.

  // Free it!
  auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, exprWrapperPtrLE);
  deallocate(AFL("discardOwningRef"), globalState, functionState, builder, controlBlockPtrLE, sourceMT);
}

void Assist::noteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtrLE) {
  auto rcIsZeroLE = strongRcIsZero(globalState, builder, refM, controlBlockPtrLE);
  buildAssert(globalState, functionState, builder, rcIsZeroLE,
      "Tried to free concrete that had nonzero RC!");

  if (auto structReferendM = dynamic_cast<StructReferend*>(refM->referend)) {
    auto structM = globalState->program->getStruct(structReferendM->fullName);
    if (structM->weakability == Weakability::WEAKABLE) {
      innerNoteWeakableDestroyed(globalState, functionState, builder, refM, controlBlockPtrLE);
    }
  } else if (auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(refM->referend)) {
    assert(false); // Do we ever deallocate an interface?
  } else {
    // Do nothing, only structs and interfaces are weakable in assist mode.
  }
}

Ref Assist::loadMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {
  LLVMValueRef innerStructPtrLE = nullptr;
  switch (structRefMT->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE:
    case Ownership::BORROW:
      innerStructPtrLE = getStructContentsPtrNormal(globalState, functionState, builder, structRefMT, structRef);
      break;
    case Ownership::WEAK:
      innerStructPtrLE = getStructContentsPtrForce(globalState, functionState, builder, structRefMT, structRef);
      break;
    default:
      assert(false);
  }
  auto memberLE =
      loadInnerInnerStructMember(this, builder, innerStructPtrLE, memberIndex, expectedMemberType, memberName);
  auto resultRef =
      upgradeLoadResultToRefWithTargetOwnership(
          globalState, functionState, builder, expectedMemberType, targetType, memberLE);
  return resultRef;
}

void Assist::storeMember(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newValueLE) {
  LLVMValueRef innerStructPtrLE = nullptr;
  switch (structRefMT->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE:
    case Ownership::BORROW:
      innerStructPtrLE = getStructContentsPtrNormal(globalState, functionState, builder, structRefMT, structRef);
      break;
    case Ownership::WEAK:
      innerStructPtrLE = getStructContentsPtrForce(globalState, functionState, builder, structRefMT, structRef);
      break;
    default:
      assert(false);
  }
  storeInnerInnerStructMember(builder, innerStructPtrLE, memberIndex, memberName, newValueLE);
}


// Gets the itable PTR and the new value that we should put into the virtual param's slot
// (such as a void* or a weak void ref)
std::tuple<LLVMValueRef, LLVMValueRef> Assist::explodeInterfaceRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef) {
  auto virtualArgLE =
      checkValidReference(FL(), functionState, builder, virtualParamMT, virtualArgRef);

  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef newVirtualArgLE = nullptr;
  switch (virtualParamMT->ownership) {
    case Ownership::OWN:
    case Ownership::BORROW:
    case Ownership::SHARE: {
      auto virtualArgInterfaceFatPtrLE = functionState->defaultRegion->makeInterfaceFatPtr(virtualParamMT, virtualArgLE);
      itablePtrLE =
          getItablePtrFromInterfacePtr(
              globalState, functionState, builder, virtualParamMT, virtualArgInterfaceFatPtrLE);
      auto objVoidPtrLE = getVoidPtrFromInterfacePtr(globalState, functionState, builder,
          virtualParamMT, virtualArgInterfaceFatPtrLE);
      newVirtualArgLE = objVoidPtrLE;
      break;
    }
    case Ownership::WEAK: {
      auto weakFatPtrLE = functionState->defaultRegion->makeWeakFatPtr(virtualParamMT, virtualArgLE);
      // Disassemble the weak interface ref.
      auto interfaceRefLE =
          functionState->defaultRegion->makeInterfaceFatPtr(
              virtualParamMT,
              FatWeaks().getInnerRefFromWeakRef(
                  globalState, functionState, builder, virtualParamMT, weakFatPtrLE));
      itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);
      // Now, reassemble a weak void* ref to the struct.
      auto weakVoidStructRefLE =
          weakInterfaceRefToWeakStructRef(
              globalState, functionState, builder, virtualParamMT, weakFatPtrLE);
      newVirtualArgLE = weakVoidStructRefLE.refLE;
      break;
    }
  }
  return std::make_tuple(itablePtrLE, newVirtualArgLE);
}