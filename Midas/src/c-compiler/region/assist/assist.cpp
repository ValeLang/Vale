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

  auto resultOptTypeLE = ::translateType(globalState, resultOptTypeM);

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
  assert(false);
}

void Assist::declareEdge(
    GlobalState* globalState,
    Edge* edge) {
  assert(false);
}

void Assist::translateEdge(
    GlobalState* globalState,
    Edge* edge) {
  assert(false);
}

LLVMTypeRef Assist::getStructRefType(
    GlobalState* globalState,
    Reference* refM,
    StructReferend* structReferendM) {
  assert(false);
}

void Assist::translateStruct(
    GlobalState* globalState,
    StructDefinition* structM) {
  assert(false);
}

void Assist::declareStruct(
    GlobalState* globalState,
    StructDefinition* structM) {
  assert(false);
}

void Assist::translateInterface(
    GlobalState* globalState,
    InterfaceDefinition* interfaceM) {
  assert(false);
}


void Assist::declareInterface(
    GlobalState* globalState,
    InterfaceDefinition* interfaceM) {
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
