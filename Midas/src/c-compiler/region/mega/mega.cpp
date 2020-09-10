#include <function/expressions/shared/weaks.h>
#include <function/expressions/shared/branch.h>
#include <region/common/fatweaks/fatweaks.h>
#include <region/common/hgm/hgm.h>
#include <region/common/lgtweaks/lgtweaks.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include <translatetype.h>
#include <region/common/common.h>
#include <function/expressions/shared/controlblock.h>
#include <function/expressions/shared/heap.h>
#include <utils/counters.h>
#include "mega.h"


Mega::Mega(GlobalState* globalState_) :
    globalState(globalState_),
    defaultLayout(globalState_) {
}

LLVMValueRef Mega::allocate(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* desiredReference,
    const std::vector<LLVMValueRef>& membersLE) {
  assert(false);
}

void Mega::alias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    Ref expr) {
  auto sourceRnd = sourceRef->referend;

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC: {
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
      break;
    }
    case RegionOverride::FAST: {
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
          // Do nothing, fast mode doesn't do stuff for borrow refs.
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
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
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
        } else if (sourceRef->ownership == Ownership::BORROW ||
            sourceRef->ownership == Ownership::WEAK) {
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
      break;
    }
    default: assert(false);
  }
}

void naiveRcFree(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef thenBuilder,
    Reference* sourceMT,
    Ref sourceRef) {
  if (dynamic_cast<InterfaceReferend *>(sourceMT->referend)) {
    auto sourceInterfacePtrLE =
        InterfaceFatPtrLE(globalState,
            sourceMT,
            checkValidReference(FL(), globalState, functionState, thenBuilder,
                sourceMT, sourceRef));
    auto controlBlockPtrLE = getControlBlockPtr(globalState, thenBuilder,
        sourceInterfacePtrLE);
    freeConcrete(FL(), globalState, functionState, blockState, thenBuilder,
        controlBlockPtrLE, sourceMT);
  } else if (dynamic_cast<StructReferend *>(sourceMT->referend) ||
      dynamic_cast<KnownSizeArrayT *>(sourceMT->referend) ||
      dynamic_cast<UnknownSizeArrayT *>(sourceMT->referend)) {
    auto sourceWrapperPtrLE =
        WrapperPtrLE(
            sourceMT,
            checkValidReference(FL(), globalState, functionState, thenBuilder, sourceMT, sourceRef));
    auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, thenBuilder, sourceWrapperPtrLE);
    freeConcrete(FL(), globalState, functionState, blockState, thenBuilder,
        controlBlockPtrLE, sourceMT);
  } else {
    assert(false);
  }
}

void Mega::dealias(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto sourceRnd = sourceMT->referend;

  if (sourceMT->ownership == Ownership::SHARE) {
    defaultRefCounting.discard(
        from, globalState, functionState, blockState, builder, sourceMT, sourceRef);
  } else {
    switch (globalState->opt->regionOverride) {
      case RegionOverride::NAIVE_RC: {
        if (sourceMT->ownership == Ownership::OWN) {
          // We can't discard owns, they must be destructured.
          assert(false); // impl
        } else if (sourceMT->ownership == Ownership::BORROW) {
          auto rcLE = adjustStrongRc(from, globalState, functionState, builder, sourceRef, sourceMT, -1);
          buildIf(
              functionState, builder, isZeroLE(builder, rcLE),
              [this, functionState, blockState, sourceRef, sourceMT](LLVMBuilderRef thenBuilder) {
                naiveRcFree(globalState, functionState, blockState, thenBuilder, sourceMT, sourceRef);
              });
        } else if (sourceMT->ownership == Ownership::WEAK) {
          discardWeakRef(from, globalState, functionState, builder, sourceMT, sourceRef);
        } else assert(false);
        break;
      }
      case RegionOverride::FAST: {
        if (sourceMT->ownership == Ownership::OWN) {
          // We can't discard owns, they must be destructured.
          assert(false);
        } else if (sourceMT->ownership == Ownership::BORROW) {
          // Do nothing!
        } else if (sourceMT->ownership == Ownership::WEAK) {
          discardWeakRef(from, globalState, functionState, builder, sourceMT, sourceRef);
        } else assert(false);
        break;
      }
      case RegionOverride::RESILIENT_V0:
      case RegionOverride::RESILIENT_V1:
      case RegionOverride::RESILIENT_V2: {
        if (sourceMT->ownership == Ownership::OWN) {
          // We can't discard owns, they must be destructured.
          assert(false); // impl
        } else if (sourceMT->ownership == Ownership::BORROW) {
          discardWeakRef(from, globalState, functionState, builder, sourceMT, sourceRef);
        } else if (sourceMT->ownership == Ownership::WEAK) {
          discardWeakRef(from, globalState, functionState, builder, sourceMT, sourceRef);
        } else assert(false);
        break;
      }
      default:
        assert(false);
    }
  }
}


LLVMValueRef Mega::loadMember(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    LLVMValueRef structExpr,
    Mutability mutability,
    Reference* memberType,
    int memberIndex,
    const std::string& memberName) {
  assert(false);
}

LLVMValueRef Mega::storeMember(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    LLVMValueRef structExpr,
    Mutability mutability,
    Reference* memberType,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef sourceLE) {
  assert(false);
}

std::vector<LLVMValueRef> Mega::destructure(
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* structType,
    LLVMValueRef structLE) {
  assert(false);
}

// Suitable for passing in to an interface method
LLVMValueRef Mega::getConcreteRefFromInterfaceRef(
    LLVMBuilderRef builder,
    LLVMValueRef refLE) {
  assert(false);
}

LLVMValueRef Mega::upcast(
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    LLVMValueRef sourceStructLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  assert(false);
}

// Transmutes a weak ref of one ownership (such as borrow) to another ownership (such as weak).
Ref Mega::transmuteWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceWeakRefMT,
    Reference* targetWeakRefMT,
    Ref sourceWeakRef) {
  // The WeakFatPtrLE constructors here will make sure that its a safe and valid transmutation.
  auto sourceWeakFatPtrLE =
      WeakFatPtrLE(
          globalState,
          sourceWeakRefMT,
          ::checkValidReference(
              FL(), globalState, functionState, builder, sourceWeakRefMT, sourceWeakRef));
  auto sourceWeakFatPtrRawLE = sourceWeakFatPtrLE.refLE;
  auto targetWeakFatPtrLE = WeakFatPtrLE(globalState, targetWeakRefMT, sourceWeakFatPtrRawLE);
  auto targetWeakRef = wrap(functionState->defaultRegion, targetWeakRefMT, targetWeakFatPtrLE);
  return targetWeakRef;
}

Ref Mega::weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) {
  assert(sourceRefMT->ownership == Ownership::BORROW);
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
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
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      return transmuteWeakRef(functionState, builder, sourceRefMT, targetRefMT, sourceRef);
    default:
      assert(false);
  }
}

Ref Mega::lockWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference* resultOptTypeM,
    Reference* constraintRefM,
    Reference* sourceWeakRefMT,
    Ref sourceWeakRefLE,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      assert(sourceWeakRefMT->ownership == Ownership::WEAK);
      break;
    }
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V2: {
      assert(sourceWeakRefMT->ownership == Ownership::BORROW ||
          sourceWeakRefMT->ownership == Ownership::WEAK);
      break;
    }
    default:
      assert(false);
      break;
  }

  auto isAliveLE = getIsAliveFromWeakRef(globalState, functionState, builder, sourceWeakRefMT, sourceWeakRefLE);

  auto resultOptTypeLE = translateType(resultOptTypeM);

  return buildIfElse(
      globalState, functionState, builder, isAliveLE,
      resultOptTypeLE, resultOptTypeM, resultOptTypeM,
      [this, functionState, constraintRefM, sourceWeakRefLE, sourceWeakRefMT, buildThen](LLVMBuilderRef thenBuilder) {
        // TODO extract more of this common code out?
        switch (globalState->opt->regionOverride) {
          case RegionOverride::NAIVE_RC:
          case RegionOverride::FAST: {
            auto weakFatPtrLE =
                WeakFatPtrLE(
                    globalState,
                    sourceWeakRefMT,
                    ::checkValidReference(
                        FL(), globalState, functionState, thenBuilder, sourceWeakRefMT, sourceWeakRefLE));
            auto constraintRefLE =
                FatWeaks().getInnerRefFromWeakRef(
                    globalState,
                    functionState,
                    thenBuilder,
                    sourceWeakRefMT,
                    weakFatPtrLE);
            auto constraintRef =
                wrap(functionState->defaultRegion, constraintRefM, constraintRefLE);
            return buildThen(thenBuilder, constraintRef);
          }
          case RegionOverride::RESILIENT_V1:
          case RegionOverride::RESILIENT_V0:
          case RegionOverride::RESILIENT_V2: {
            // The incoming "constraint" ref is actually already a week ref, so just return it
            // (after wrapping it in a different Ref that actually thinks/knows it's a weak
            // reference).
            auto constraintRef =
                transmuteWeakRef(
                    functionState, thenBuilder, sourceWeakRefMT, constraintRefM, sourceWeakRefLE);
            return buildThen(thenBuilder, constraintRef);
          }
          default:
            assert(false);
            break;
        }
      },
      buildElse);
}

// Returns a LLVMValueRef for a ref to the string object.
// The caller should then use getStringBytesPtr to then fill the string's contents.
LLVMValueRef Mega::constructString(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE) {
  assert(false);
}

// Returns a LLVMValueRef for a pointer to the strings contents bytes
LLVMValueRef Mega::getStringBytesPtr(
    LLVMBuilderRef builder,
    LLVMValueRef stringRefLE) {
  assert(false);
}

LLVMValueRef Mega::getStringLength(
    LLVMBuilderRef builder,
    LLVMValueRef stringRefLE) {
  assert(false);
}

// Returns a LLVMValueRef for a ref to the string object.
// The caller should then use getStringBytesPtr to then fill the string's contents.
LLVMValueRef Mega::constructKnownSizeArray(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    KnownSizeArrayT* referendM,
    const std::vector<LLVMValueRef>& membersLE) {
  assert(false);
}

// Returns a LLVMValueRef for a ref to the string object.
// The caller should then use getStringBytesPtr to then fill the string's contents.
LLVMValueRef Mega::constructUnknownSizeArray(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaMT,
    LLVMValueRef sizeLE,
    const std::string& typeName) {
  assert(false);
}

// should expose a dereference thing instead
//  LLVMValueRef getKnownSizeArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef knownSizeArrayWrapperPtrLE) {

//  LLVMValueRef getUnknownSizeArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef unknownSizeArrayWrapperPtrLE) {

//  LLVMValueRef getUnknownSizeArrayLength(
//      LLVMBuilderRef builder,
//      LLVMValueRef unknownSizeArrayWrapperPtrLE) {


void Mega::destroyArray(
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* arrayType,
    LLVMValueRef arrayWrapperLE) {
  assert(false);
}

LLVMTypeRef Mega::getKnownSizeArrayRefType(
    Reference* referenceM,
    KnownSizeArrayT* knownSizeArrayMT) {
  assert(false);
}

LLVMTypeRef Mega::getUnknownSizeArrayRefType(
    Reference* referenceM,
    UnknownSizeArrayT* unknownSizeArrayMT) {
  assert(false);
}

LLVMValueRef Mega::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    Ref refLE) {
  assert(false);
}

LLVMValueRef Mega::loadElement(
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* structRefM,
    Reference* elementRefM,
    LLVMValueRef sizeIntLE,
    LLVMValueRef arrayCRefLE,
    Mutability mutability,
    LLVMValueRef indexIntLE) {
  assert(false);
}

LLVMValueRef Mega::storeElement(
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    LLVMValueRef sizeIntLE,
    LLVMValueRef arrayCRefLE,
    Mutability mutability,
    LLVMValueRef indexIntLE,
    LLVMValueRef sourceLE) {
  assert(false);
}

LLVMTypeRef Mega::translateType(Reference* referenceM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
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
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (referenceM->ownership) {
        case Ownership::SHARE:
          return defaultRefCounting.translateType(globalState, referenceM);
        case Ownership::OWN:
          assert(referenceM->location != Location::INLINE);
          return translateReferenceSimple(globalState, referenceM->referend);
        case Ownership::BORROW:
        case Ownership::WEAK:
          assert(referenceM->location != Location::INLINE);
          return translateWeakReference(globalState, referenceM->referend);
        default:
          assert(false);
      }
    }
    default:
      assert(false);
      return nullptr;
  }
}


LLVMTypeRef Mega::getStructRefType(
    Reference* refM,
    StructReferend* structReferendM) {
  assert(false);
}

LLVMTypeRef Mega::getStringRefType() const {
  assert(false);
}


LLVMValueRef Mega::upcastWeak(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructReferend* sourceStructReferendM,
    Reference* sourceStructTypeM,
    InterfaceReferend* targetInterfaceReferendM,
    Reference* targetInterfaceTypeM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V2: {
      return HybridGenerationalMemory().weakStructPtrToGenWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
          sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
    }
    case RegionOverride::RESILIENT_V1: {
      return LgtWeaks().weakStructPtrToLgtiWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
          sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
    }
    case RegionOverride::FAST:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::NAIVE_RC: {
      return WrcWeaks().weakStructPtrToWrciWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
          sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
    }
    default:
      assert(false);
      break;
  }
}

void Mega::declareKnownSizeArray(
    KnownSizeArrayT* knownSizeArrayMT) {
  defaultLayout.declareKnownSizeArray(knownSizeArrayMT);
}

void Mega::declareUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT) {
  defaultLayout.declareUnknownSizeArray(unknownSizeArrayMT);
}

void Mega::translateUnknownSizeArray(
    UnknownSizeArrayT* unknownSizeArrayMT) {
  auto elementLT =
      translateType(
          unknownSizeArrayMT->rawArray->elementType);
  defaultLayout.translateUnknownSizeArray(unknownSizeArrayMT, elementLT);
}

void Mega::translateKnownSizeArray(
    KnownSizeArrayT* knownSizeArrayMT) {
  auto elementLT =
      translateType(
          knownSizeArrayMT->rawArray->elementType);
  defaultLayout.translateKnownSizeArray(knownSizeArrayMT, elementLT);
}

void Mega::declareStruct(
    StructDefinition* structM) {
  defaultLayout.declareStruct(structM);
}

void Mega::translateStruct(
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

void Mega::declareEdge(
    Edge* edge) {
  defaultLayout.declareEdge(edge);
}

void Mega::translateEdge(
    Edge* edge) {
  std::vector<LLVMValueRef> functions;
  for (int i = 0; i < edge->structPrototypesByInterfaceMethod.size(); i++) {
    auto funcName = edge->structPrototypesByInterfaceMethod[i].second->name;
    functions.push_back(globalState->getFunction(funcName));
  }
  defaultLayout.translateEdge(edge, functions);
}

void Mega::declareInterface(
    InterfaceDefinition* interfaceM) {
  defaultLayout.declareInterface(interfaceM->name);
}

void Mega::translateInterface(
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

LLVMTypeRef Mega::translateInterfaceMethodToFunctionType(
    InterfaceMethod* method) {
  auto returnMT = method->prototype->returnType;
  auto paramsMT = method->prototype->params;
  auto returnLT = translateType(returnMT);
  auto paramsLT = translateTypes(globalState, this, paramsMT);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
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
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (paramsMT[method->virtualParamIndex]->ownership) {
        case Ownership::OWN:
        case Ownership::SHARE:
          paramsLT[method->virtualParamIndex] = LLVMPointerType(LLVMVoidType(), 0);
          break;
        case Ownership::BORROW:
        case Ownership::WEAK:
          paramsLT[method->virtualParamIndex] = globalState->weakVoidRefStructL;
          break;
      }
      break;
    }
    default:
      assert(false);
  }

  return LLVMFunctionType(returnLT, paramsLT.data(), paramsLT.size(), false);
}
