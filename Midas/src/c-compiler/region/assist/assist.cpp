#include <function/expressions/shared/weaks.h>
#include <function/expressions/shared/branch.h>
#include <region/common/fatweaks/fatweaks.h>
#include <region/common/hgm/hgm.h>
#include <region/common/lgtweaks/lgtweaks.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include <translatetype.h>
#include "assist.h"


LLVMValueRef Assist::allocate(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* desiredReference,
    const std::vector<LLVMValueRef>& membersLE) {
  assert(false);
}

LLVMValueRef Assist::alias(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    Reference* targetRef,
    LLVMValueRef expr) {
  assert(false);
}

void Assist::dealias(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    LLVMValueRef expr) {
  assert(false);
}

LLVMValueRef Assist::loadMember(
    AreaAndFileAndLine from,
    GlobalState* globalState,
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

LLVMValueRef Assist::storeMember(
    AreaAndFileAndLine from,
    GlobalState* globalState,
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

std::vector<LLVMValueRef> Assist::destructure(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* structType,
    LLVMValueRef structLE) {
  assert(false);
}

// Suitable for passing in to an interface method
LLVMValueRef Assist::getConcreteRefFromInterfaceRef(
    LLVMBuilderRef builder,
    LLVMValueRef refLE) {
  assert(false);
}

LLVMValueRef Assist::upcast(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    LLVMValueRef sourceStructLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  assert(false);
}

LLVMValueRef Assist::lockWeak(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference* resultOptTypeM,
//      LLVMTypeRef resultOptTypeL,
    Reference* constraintRefMT,
    Reference* sourceWeakRefMT,
    LLVMValueRef sourceWeakRefLE,
    std::function<LLVMValueRef(LLVMBuilderRef, LLVMValueRef)> buildThen,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildElse) {

  auto isAliveLE = getIsAliveFromWeakRef(globalState, functionState, builder, sourceWeakRefMT, sourceWeakRefLE);

  auto resultOptTypeLE = translateType(globalState, resultOptTypeM);

  return buildIfElse(functionState, builder, isAliveLE, resultOptTypeLE, false, false,
      [this, globalState, functionState, sourceWeakRefLE, sourceWeakRefMT, buildThen](LLVMBuilderRef thenBuilder) {
        // TODO extract more of this common code out?
        LLVMValueRef someLE = nullptr;
        switch (globalState->opt->regionOverride) {
          case RegionOverride::ASSIST:
          case RegionOverride::NAIVE_RC:
          case RegionOverride::FAST: {
            auto constraintRefLE =
                FatWeaks().getInnerRefFromWeakRef(
                    globalState,
                    functionState,
                    thenBuilder,
                    sourceWeakRefMT,
                    sourceWeakRefLE);
            return buildThen(thenBuilder, constraintRefLE);
          }
          case RegionOverride::RESILIENT_V1:
          case RegionOverride::RESILIENT_V0:
          case RegionOverride::RESILIENT_V2: {
            // The incoming "constraint" ref is actually already a week ref. All we have to
            // do now is wrap it in a Some.
            auto constraintRefLE = sourceWeakRefLE;
            return buildThen(thenBuilder, constraintRefLE);
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
LLVMValueRef Assist::constructString(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE) {
  assert(false);
}

// Returns a LLVMValueRef for a pointer to the strings contents bytes
LLVMValueRef Assist::getStringBytesPtr(
    LLVMBuilderRef builder,
    LLVMValueRef stringRefLE) {
  assert(false);
}

LLVMValueRef Assist::getStringLength(
    LLVMBuilderRef builder,
    LLVMValueRef stringRefLE) {
  assert(false);
}

// Returns a LLVMValueRef for a ref to the string object.
// The caller should then use getStringBytesPtr to then fill the string's contents.
LLVMValueRef Assist::constructKnownSizeArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM,
    KnownSizeArrayT* referendM,
    const std::vector<LLVMValueRef>& membersLE) {
  assert(false);
}

// Returns a LLVMValueRef for a ref to the string object.
// The caller should then use getStringBytesPtr to then fill the string's contents.
LLVMValueRef Assist::constructUnknownSizeArray(
    GlobalState* globalState,
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


void Assist::destroyArray(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* arrayType,
    LLVMValueRef arrayWrapperLE) {
  assert(false);
}

LLVMTypeRef Assist::getKnownSizeArrayRefType(
    GlobalState* globalState,
    Reference* referenceM,
    KnownSizeArrayT* knownSizeArrayMT) {
  assert(false);
}

LLVMTypeRef Assist::getUnknownSizeArrayRefType(
    GlobalState* globalState,
    Reference* referenceM,
    UnknownSizeArrayT* unknownSizeArrayMT) {
  assert(false);
}

void Assist::checkValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    LLVMValueRef refLE) {
  assert(false);
}

LLVMValueRef Assist::loadElement(
    GlobalState* globalState,
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

LLVMValueRef Assist::storeElement(
    GlobalState* globalState,
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

LLVMTypeRef Assist::translateType(GlobalState* globalState, Reference* referenceM) {
  if (dynamic_cast<Int*>(referenceM->referend) != nullptr) {
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMInt64Type();
  } else if (dynamic_cast<Bool*>(referenceM->referend) != nullptr) {
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMInt1Type();
  } else if (dynamic_cast<Str*>(referenceM->referend) != nullptr) {
    assert(referenceM->ownership == Ownership::SHARE);
    return LLVMPointerType(globalState->stringWrapperStructL, 0);
  } else if (dynamic_cast<Never*>(referenceM->referend) != nullptr) {
    return LLVMArrayType(LLVMIntType(NEVER_INT_BITS), 0);
  } else if (auto knownSizeArrayMT =
      dynamic_cast<KnownSizeArrayT*>(referenceM->referend)) {
    if (knownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
      assert(false);
      return nullptr;
    } else {
      auto knownSizeArrayCountedStructLT = globalState->getKnownSizeArrayWrapperStruct(knownSizeArrayMT->name);
      if (referenceM->location == Location::INLINE) {
        return knownSizeArrayCountedStructLT;
      } else {
        if (referenceM->ownership == Ownership::OWN) {
          return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
        } else if (referenceM->ownership == Ownership::BORROW) {
          switch (globalState->opt->regionOverride) {
            case RegionOverride::ASSIST:
            case RegionOverride::NAIVE_RC:
            case RegionOverride::FAST: {
              return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
              break;
            }
            case RegionOverride::RESILIENT_V0:
            case RegionOverride::RESILIENT_V1:
            case RegionOverride::RESILIENT_V2: {
              return globalState->getKnownSizeArrayWeakRefStruct(knownSizeArrayMT->name);
              break;
            }
            default:
              assert(false);
              return nullptr;
          }
        } else if (referenceM->ownership == Ownership::SHARE) {
          return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
        } else if (referenceM->ownership == Ownership::WEAK) {
          return globalState->getKnownSizeArrayWeakRefStruct(knownSizeArrayMT->name);
        } else assert(false);
      }
    }
  } else if (auto unknownSizeArrayMT =
      dynamic_cast<UnknownSizeArrayT*>(referenceM->referend)) {
    auto unknownSizeArrayCountedStructLT = globalState->getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT->name);
    if (referenceM->ownership == Ownership::OWN) {
      return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
    } else if (referenceM->ownership == Ownership::BORROW) {
      switch (globalState->opt->regionOverride) {
        case RegionOverride::ASSIST:
        case RegionOverride::NAIVE_RC:
        case RegionOverride::FAST:
          return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
          break;
        case RegionOverride::RESILIENT_V0:
        case RegionOverride::RESILIENT_V1:
        case RegionOverride::RESILIENT_V2:
          return globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name);
          break;
        default:
          assert(false);
          return nullptr;
      }
    } else if (referenceM->ownership == Ownership::SHARE) {
      return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
    } else if (referenceM->ownership == Ownership::WEAK) {
      return globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name);
    } else {
      assert(false);
      return nullptr;
    }
  } else if (auto structReferend =
      dynamic_cast<StructReferend*>(referenceM->referend)) {

    auto structM = globalState->program->getStruct(structReferend->fullName);
    if (structM->mutability == Mutability::MUTABLE) {
      auto countedStructL = globalState->getWrapperStruct(structReferend->fullName);
      if (referenceM->ownership == Ownership::OWN) {
        return LLVMPointerType(countedStructL, 0);
      } else if (referenceM->ownership == Ownership::BORROW) {
        switch (globalState->opt->regionOverride) {
          case RegionOverride::ASSIST:
          case RegionOverride::NAIVE_RC:
          case RegionOverride::FAST: {
            return LLVMPointerType(countedStructL, 0);
            break;
          }
          case RegionOverride::RESILIENT_V0:
          case RegionOverride::RESILIENT_V1:
          case RegionOverride::RESILIENT_V2: {
            return globalState->getStructWeakRefStruct(structM->name);
            break;
          }
          default:
            assert(false);
            return nullptr;
        }
      } else if (referenceM->ownership == Ownership::WEAK) {
        return globalState->getStructWeakRefStruct(structM->name);
      } else {
        assert(false);
        return nullptr;
      }
    } else {
      auto innerStructL = globalState->getInnerStruct(structReferend->fullName);
      if (referenceM->location == Location::INLINE) {
        return globalState->getInnerStruct(structReferend->fullName);
      } else {
        auto countedStructL = globalState->getWrapperStruct(structReferend->fullName);
        return LLVMPointerType(countedStructL, 0);
      }
    }
  } else if (auto interfaceReferend =
      dynamic_cast<InterfaceReferend*>(referenceM->referend)) {
    auto interfaceM = globalState->program->getInterface(interfaceReferend->fullName);
    auto interfaceRefStructL =
        globalState->getInterfaceRefStruct(interfaceReferend->fullName);
    if (interfaceM->mutability == Mutability::MUTABLE) {
      if (referenceM->ownership == Ownership::OWN) {
        return interfaceRefStructL;
      } else if (referenceM->ownership == Ownership::BORROW) {
        switch (globalState->opt->regionOverride) {
          case RegionOverride::ASSIST:
          case RegionOverride::NAIVE_RC:
          case RegionOverride::FAST: {
            return interfaceRefStructL;
          }
          case RegionOverride::RESILIENT_V0:
          case RegionOverride::RESILIENT_V1:
          case RegionOverride::RESILIENT_V2: {
            return globalState->getInterfaceWeakRefStruct(interfaceM->name);
          }
          default:
            assert(false);
            return nullptr;
        }
      } else if (referenceM->ownership == Ownership::WEAK) {
        return globalState->getInterfaceWeakRefStruct(interfaceM->name);
      } else {
        assert(false);
        return nullptr;
      }
    } else {
      return interfaceRefStructL;
    }
  } else {
    std::cerr << "Unimplemented type: " << typeid(*referenceM->referend).name() << std::endl;
    assert(false);
    return nullptr;
  }
}


LLVMTypeRef Assist::getStructRefType(
    GlobalState* globalState,
    Reference* refM,
    StructReferend* structReferendM) {
  assert(false);
}

LLVMTypeRef Assist::getStringRefType() const {
  assert(false);
}


LLVMValueRef Assist::upcastWeak(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef sourceRefLE,
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
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST: {
      return WrcWeaks().weakStructPtrToWrciWeakInterfacePtr(
          globalState, functionState, builder, sourceRefLE, sourceStructReferendM,
          sourceStructTypeM, targetInterfaceReferendM, targetInterfaceTypeM);
    }
    default:
      assert(false);
      break;
  }
}

void Assist::declareKnownSizeArray(
    GlobalState* globalState,
    KnownSizeArrayT* knownSizeArrayMT) {

  auto countedStruct = LLVMStructCreateNamed(LLVMGetGlobalContext(), knownSizeArrayMT->name->name.c_str());
  globalState->knownSizeArrayWrapperStructs.emplace(knownSizeArrayMT->name->name, countedStruct).first;

  auto weakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (knownSizeArrayMT->name->name + "w").c_str());
  assert(globalState->knownSizeArrayWeakRefStructs.count(knownSizeArrayMT->name->name) == 0);
  globalState->knownSizeArrayWeakRefStructs.emplace(knownSizeArrayMT->name->name, weakRefStructL);
}

void Assist::declareUnknownSizeArray(
    GlobalState* globalState,
    UnknownSizeArrayT* unknownSizeArrayMT) {
  auto countedStruct = LLVMStructCreateNamed(LLVMGetGlobalContext(), (unknownSizeArrayMT->name->name + "rc").c_str());
  globalState->unknownSizeArrayWrapperStructs.emplace(unknownSizeArrayMT->name->name, countedStruct).first;

  auto weakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (unknownSizeArrayMT->name->name + "w").c_str());
  assert(globalState->unknownSizeArrayWeakRefStructs.count(unknownSizeArrayMT->name->name) == 0);
  globalState->unknownSizeArrayWeakRefStructs.emplace(unknownSizeArrayMT->name->name, weakRefStructL);
}

void Assist::translateUnknownSizeArray(
    GlobalState* globalState,
    UnknownSizeArrayT* unknownSizeArrayMT) {

  auto unknownSizeArrayWrapperStruct = globalState->getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT->name);
  auto elementLT =
      translateType(
          globalState,
          unknownSizeArrayMT->rawArray->elementType);
  auto innerArrayLT = LLVMArrayType(elementLT, 0);

  std::vector<LLVMTypeRef> elementsL;

  if (unknownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
    if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
        globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
      elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
      elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
      // In resilient mode, we can have weak refs to arrays
      elementsL.push_back(globalState->mutWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
      // In resilient mode, we can have weak refs to arrays
      elementsL.push_back(globalState->mutWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
      // In resilient mode, we can have weak refs to arrays
      elementsL.push_back(globalState->mutWeakableControlBlockStructL);
    } else assert(false);
  } else if (unknownSizeArrayMT->rawArray->mutability == Mutability::IMMUTABLE) {
    elementsL.push_back(globalState->immControlBlockStructL);
  } else assert(false);

  elementsL.push_back(LLVMInt64Type());

  elementsL.push_back(innerArrayLT);

  LLVMStructSetBody(unknownSizeArrayWrapperStruct, elementsL.data(), elementsL.size(), false);

  auto arrayWeakRefStructL = globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name);
  makeUnknownSizeArrayWeakRefStruct(globalState, unknownSizeArrayWrapperStruct, arrayWeakRefStructL);
}

void Assist::translateKnownSizeArray(
    GlobalState* globalState,
    KnownSizeArrayT* knownSizeArrayMT) {
  auto knownSizeArrayWrapperStruct = globalState->getKnownSizeArrayWrapperStruct(knownSizeArrayMT->name);

  auto elementLT =
      translateType(
          globalState,
          knownSizeArrayMT->rawArray->elementType);
  auto innerArrayLT = LLVMArrayType(elementLT, knownSizeArrayMT->size);

  std::vector<LLVMTypeRef> elementsL;

  if (knownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
    if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
        globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
      elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
      elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
      // In resilient mode, we can have weak refs to arrays
      elementsL.push_back(globalState->mutWeakableControlBlockStructL);
    } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
      // In resilient mode, we can have weak refs to arrays
      elementsL.push_back(globalState->mutWeakableControlBlockStructL);
    } else assert(false);
  } else if (knownSizeArrayMT->rawArray->mutability == Mutability::IMMUTABLE) {
    elementsL.push_back(globalState->immControlBlockStructL);
  } else assert(false);

  elementsL.push_back(innerArrayLT);

  LLVMStructSetBody(knownSizeArrayWrapperStruct, elementsL.data(), elementsL.size(), false);

  auto arrayWeakRefStructL = globalState->getKnownSizeArrayWeakRefStruct(knownSizeArrayMT->name);
  makeKnownSizeArrayWeakRefStruct(globalState, knownSizeArrayWrapperStruct, arrayWeakRefStructL);
}

void Assist::declareStruct(
    GlobalState* globalState,
    StructDefinition* structM) {

  auto innerStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), structM->name->name.c_str());
  assert(globalState->innerStructs.count(structM->name->name) == 0);
  globalState->innerStructs.emplace(structM->name->name, innerStructL);

  auto wrapperStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (structM->name->name + "rc").c_str());
  assert(globalState->wrapperStructs.count(structM->name->name) == 0);
  globalState->wrapperStructs.emplace(structM->name->name, wrapperStructL);

  auto structWeakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (structM->name->name + "w").c_str());
  assert(globalState->structWeakRefStructs.count(structM->name->name) == 0);
  globalState->structWeakRefStructs.emplace(structM->name->name, structWeakRefStructL);
}

void Assist::translateStruct(
    GlobalState* globalState,
    StructDefinition* structM) {
  LLVMTypeRef valStructL = globalState->getInnerStruct(structM->name);
  std::vector<LLVMTypeRef> innerStructMemberTypesL;
  for (int i = 0; i < structM->members.size(); i++) {
    innerStructMemberTypesL.push_back(
        translateType(
            globalState,
            structM->members[i]->type));
  }
  LLVMStructSetBody(
      valStructL, innerStructMemberTypesL.data(), innerStructMemberTypesL.size(), false);

  LLVMTypeRef wrapperStructL = globalState->getWrapperStruct(structM->name);
  std::vector<LLVMTypeRef> wrapperStructMemberTypesL;

  // First member is a ref counts struct. We don't include the int directly
  // because we want fat pointers to point to this struct, so they can reach
  // into it and increment without doing any casting.
  if (structM->mutability == Mutability::MUTABLE) {
    if (getEffectiveWeakability(globalState, structM) == Weakability::WEAKABLE) {
      wrapperStructMemberTypesL.push_back(globalState->mutWeakableControlBlockStructL);
    } else {
      wrapperStructMemberTypesL.push_back(globalState->mutNonWeakableControlBlockStructL);
    }
  } else if (structM->mutability == Mutability::IMMUTABLE) {
    wrapperStructMemberTypesL.push_back(globalState->immControlBlockStructL);
  } else assert(false);

  wrapperStructMemberTypesL.push_back(valStructL);

  LLVMStructSetBody(
      wrapperStructL, wrapperStructMemberTypesL.data(), wrapperStructMemberTypesL.size(), false);

  auto structWeakRefStructL = globalState->getStructWeakRefStruct(structM->name);
  makeStructWeakRefStruct(globalState, structWeakRefStructL, wrapperStructL);
}

void Assist::declareEdge(
    GlobalState* globalState,
    Edge* edge) {

  auto interfaceTableStructL =
      globalState->getInterfaceTableStruct(edge->interfaceName->fullName);

  auto edgeName =
      edge->structName->fullName->name + edge->interfaceName->fullName->name;
  auto itablePtr =
      LLVMAddGlobal(globalState->mod, interfaceTableStructL, edgeName.c_str());
  LLVMSetLinkage(itablePtr, LLVMExternalLinkage);

  globalState->interfaceTablePtrs.emplace(edge, itablePtr);
}

void Assist::translateEdge(
    GlobalState* globalState,
    Edge* edge) {

  auto interfaceTableStructL =
      globalState->getInterfaceTableStruct(edge->interfaceName->fullName);

  auto builder = LLVMCreateBuilder();
  auto itableLE = LLVMGetUndef(interfaceTableStructL);
  for (int i = 0; i < edge->structPrototypesByInterfaceMethod.size(); i++) {
    auto funcName = edge->structPrototypesByInterfaceMethod[i].second->name;
    itableLE = LLVMBuildInsertValue(
        builder,
        itableLE,
        globalState->getFunction(funcName),
        i,
        std::to_string(i).c_str());
  }
  LLVMDisposeBuilder(builder);

  auto itablePtr = globalState->getInterfaceTablePtr(edge);
  LLVMSetInitializer(itablePtr,  itableLE);
}

void Assist::declareInterface(
    GlobalState* globalState,
    InterfaceDefinition* interfaceM) {

  auto interfaceRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), interfaceM->name->name.c_str());
  assert(globalState->interfaceRefStructs.count(interfaceM->name->name) == 0);
  globalState->interfaceRefStructs.emplace(interfaceM->name->name, interfaceRefStructL);

  auto interfaceTableStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (interfaceM->name->name + "itable").c_str());
  assert(globalState->interfaceTableStructs.count(interfaceM->name->name) == 0);
  globalState->interfaceTableStructs.emplace(interfaceM->name->name, interfaceTableStructL);

  auto interfaceWeakRefStructL =
      LLVMStructCreateNamed(
          LLVMGetGlobalContext(), (interfaceM->name->name + "w").c_str());
  assert(globalState->interfaceWeakRefStructs.count(interfaceM->name->name) == 0);
  globalState->interfaceWeakRefStructs.emplace(interfaceM->name->name, interfaceWeakRefStructL);
}

LLVMTypeRef Assist::translateInterfaceMethodToFunctionType(
    GlobalState* globalState,
    InterfaceMethod* method) {
  auto returnMT = method->prototype->returnType;
  auto paramsMT = method->prototype->params;
  auto returnLT = translateType(globalState, returnMT);
  auto paramsLT = translateTypes(globalState, this, paramsMT);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
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

void Assist::translateInterface(
    GlobalState* globalState,
    InterfaceDefinition* interfaceM) {
  LLVMTypeRef itableStruct =
      globalState->getInterfaceTableStruct(interfaceM->name);
  std::vector<LLVMTypeRef> interfaceMethodTypesL;
  for (int i = 0; i < interfaceM->methods.size(); i++) {
    interfaceMethodTypesL.push_back(
        LLVMPointerType(
            translateInterfaceMethodToFunctionType(
                globalState, interfaceM->methods[i]),
            0));
  }
  LLVMStructSetBody(
      itableStruct, interfaceMethodTypesL.data(), interfaceMethodTypesL.size(), false);

  LLVMTypeRef refStructL = globalState->getInterfaceRefStruct(interfaceM->name);
  std::vector<LLVMTypeRef> refStructMemberTypesL;

  // this points to the control block.
  // It makes it easier to increment and decrement ref counts.
  if (interfaceM->mutability == Mutability::MUTABLE) {
    if (getEffectiveWeakability(globalState, interfaceM) == Weakability::WEAKABLE) {
      refStructMemberTypesL.push_back(LLVMPointerType(globalState->mutWeakableControlBlockStructL, 0));
    } else {
      refStructMemberTypesL.push_back(LLVMPointerType(globalState->mutNonWeakableControlBlockStructL, 0));
    }
  } else if (interfaceM->mutability == Mutability::IMMUTABLE) {
    refStructMemberTypesL.push_back(LLVMPointerType(globalState->immControlBlockStructL, 0));
  } else assert(false);


  refStructMemberTypesL.push_back(LLVMPointerType(itableStruct, 0));
  LLVMStructSetBody(
      refStructL,
      refStructMemberTypesL.data(),
      refStructMemberTypesL.size(),
      false);

  auto interfaceWeakRefStructL = globalState->getInterfaceWeakRefStruct(interfaceM->name);
  makeInterfaceWeakRefStruct(globalState, interfaceWeakRefStructL, refStructL);
}
