#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "utils/branch.h"
#include "weaks.h"
#include "elements.h"
#include "utils/counters.h"

LLVMValueRef getKnownSizeArrayContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE knownSizeArrayWrapperPtrLE) {
  return LLVMBuildStructGEP(
      builder,
      knownSizeArrayWrapperPtrLE.refLE,
      1, // Array is after the control block.
      "ksaElemsPtr");
}

LLVMValueRef getUnknownSizeArrayContentsPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE arrayWrapperPtrLE) {

  return LLVMBuildStructGEP(
      builder,
      arrayWrapperPtrLE.refLE,
      2, // Array is after the control block and length.
      "usaElemsPtr");
}

LLVMValueRef getUnknownSizeArrayLengthPtr(
    LLVMBuilderRef builder,
    WrapperPtrLE unknownSizeArrayWrapperPtrLE) {
  auto resultLE =
      LLVMBuildStructGEP(
          builder,
          unknownSizeArrayWrapperPtrLE.refLE,
          1, // Length is after the control block and before contents.
          "usaLenPtr");
  assert(LLVMTypeOf(resultLE) == LLVMPointerType(LLVMInt64Type(), 0));
  return resultLE;
}

WrapperPtrLE getUnknownSizeArrayWrapperPtrNormal(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefMT,
    Ref arrayRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      // good, continue
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (arrayRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
          // good, continue
          break;
        case Ownership::BORROW:
          assert(false);
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
      }
      break;
    }
    default:
      assert(false);
  }
  return functionState->defaultRegion->makeWrapperPtr(arrayRefMT, globalState->region->checkValidReference(FL(), functionState, builder, arrayRefMT, arrayRef));
}

WrapperPtrLE getUnknownSizeArrayWrapperPtrForce(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefMT,
    Ref arrayRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      assert(false);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (arrayRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
          assert(false);
        case Ownership::BORROW:
          // good, continue
          break;
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
      }
      break;
    }
    default:
      assert(false);
  }
  return lockWeakRef(FL(), globalState, functionState, builder, arrayRefMT, arrayRef);
}

Ref getUnknownSizeArrayLength(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE arrayRefLE) {
  auto lengthPtrLE = getUnknownSizeArrayLengthPtr(builder, arrayRefLE);
  auto intLE = LLVMBuildLoad(builder, lengthPtrLE, "usaLen");
  return wrap(functionState->defaultRegion, globalState->metalCache.intRef, intLE);
}

Ref getUnknownSizeArrayLengthNormal(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Ref arrayRef) {
  auto wrapperPtrLE = getUnknownSizeArrayWrapperPtrNormal(globalState, functionState, builder, arrayRefM, arrayRef);
  return getUnknownSizeArrayLength(globalState, functionState, builder, wrapperPtrLE);
}

Ref getUnknownSizeArrayLengthForce(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Ref arrayRef) {
  auto wrapperPtrLE = getUnknownSizeArrayWrapperPtrForce(globalState, functionState, builder, arrayRefM, arrayRef);
  return getUnknownSizeArrayLength(globalState, functionState, builder, wrapperPtrLE);
}

LLVMValueRef loadInnerArrayMember(
    LLVMBuilderRef builder,
    LLVMValueRef elemsPtrLE,
    LLVMValueRef indexLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(elemsPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      constI64LE(0),
      indexLE
  };
  auto resultLE =
      LLVMBuildLoad(
          builder,
          LLVMBuildGEP(
              builder, elemsPtrLE, indices, 2, "indexPtr"),
          "index");

  return resultLE;
}

void storeInnerArrayMember(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef elemsPtrLE,
    LLVMValueRef indexLE,
    LLVMValueRef sourceLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(elemsPtrLE)) == LLVMPointerTypeKind);
  LLVMValueRef indices[2] = {
      constI64LE(0),
      indexLE
  };
  LLVMBuildStore(
      builder,
      sourceLE,
      LLVMBuildGEP(
          builder, elemsPtrLE, indices, 2, "indexPtr"));
}

Ref loadElementWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    Ref sizeRef,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    Ref indexRef) {
  auto indexLE = globalState->region->checkValidReference(FL(), functionState, builder, globalState->metalCache.intRef, indexRef);
  auto sizeLE = globalState->region->checkValidReference(FL(), functionState, builder, globalState->metalCache.intRef, sizeRef);

  auto isNonNegativeLE = LLVMBuildICmp(builder, LLVMIntSGE, indexLE, constI64LE(0), "isNonNegative");
  auto isUnderLength = LLVMBuildICmp(builder, LLVMIntSLT, indexLE, sizeLE, "isUnderLength");
  auto isWithinBounds = LLVMBuildAnd(builder, isNonNegativeLE, isUnderLength, "isWithinBounds");
  buildFlare(FL(), globalState, functionState, builder, "index: ", indexLE);
  buildFlare(FL(), globalState, functionState, builder, "size: ", sizeLE);
  buildAssert(globalState, functionState, builder, isWithinBounds, "Index out of bounds!");

  LLVMValueRef fromArrayLE = nullptr;
  if (mutability == Mutability::IMMUTABLE) {
    if (arrayRefM->location == Location::INLINE) {
      assert(false);
//      return LLVMBuildExtractValue(builder, structExpr, indexLE, "index");
    } else {
      fromArrayLE = loadInnerArrayMember(builder, arrayPtrLE, indexLE);
    }
  } else if (mutability == Mutability::MUTABLE) {
    fromArrayLE = loadInnerArrayMember(builder, arrayPtrLE, indexLE);
  } else {
    assert(false);
  }

  {
    // Careful here! This is a bit cheaty; we shouldn't pretend we have the source reference,
    // because we don't. We're *reading* from it, but by wrapping it, we're pretending we *have* it.
    // We're only doing this here so we can feed it to checkValidReference, and immediately throwing
    // it away.
    auto sourceRef = wrap(functionState->defaultRegion, elementRefM, fromArrayLE);
    globalState->region->checkValidReference(FL(), functionState, builder, elementRefM, sourceRef);
    return sourceRef;
  }
}

Ref loadElementWithUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    Ref sizeRef,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    Ref indexRef,
    Reference* resultRefM) {
  auto fromArrayRef =
      loadElementWithoutUpgrade(
          globalState, functionState, builder, arrayRefM, elementRefM, sizeRef, arrayPtrLE, mutability, indexRef);
  return upgradeLoadResultToRefWithTargetOwnership(globalState, functionState, builder, elementRefM,
      resultRefM,
      fromArrayRef);
}


Ref storeElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* arrayRefM,
    Reference* elementRefM,
    Ref sizeRef,
    LLVMValueRef arrayPtrLE,
    Mutability mutability,
    Ref indexRef,
    Ref sourceRef) {
  auto sizeLE = globalState->region->checkValidReference(FL(), functionState, builder, globalState->metalCache.intRef, sizeRef);

  auto indexLE = globalState->region->checkValidReference(FL(), functionState, builder, globalState->metalCache.intRef, indexRef);
  auto isNonNegativeLE = LLVMBuildICmp(builder, LLVMIntSGE, indexLE, constI64LE(0), "isNonNegative");
  auto isUnderLength = LLVMBuildICmp(builder, LLVMIntSLT, indexLE, sizeLE, "isUnderLength");
  auto isWithinBounds = LLVMBuildAnd(builder, isNonNegativeLE, isUnderLength, "isWithinBounds");
  buildAssert(globalState, functionState, builder, isWithinBounds, "Index out of bounds!");

//  auto arrayPtrLE = globalState->region->checkValidReference(FL(), functionState, builder, arrayRefM, arrayRef);
  auto sourceLE = globalState->region->checkValidReference(FL(), functionState, builder, elementRefM, sourceRef);

  if (mutability == Mutability::IMMUTABLE) {
    if (arrayRefM->location == Location::INLINE) {
      assert(false);
//      return LLVMBuildExtractValue(builder, structExpr, indexLE, "index");
    } else {
//      auto arrayWrapperPtrLE = getUnknownSizeArrayWrapperPtr(globalState, functionState, builder, arrayRefM, arrayRef);
//      LLVMValueRef arrayPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);

      auto resultLE = loadElementWithoutUpgrade(globalState, functionState, builder, arrayRefM, elementRefM, sizeRef, arrayPtrLE, mutability, indexRef);

      storeInnerArrayMember(globalState, builder, arrayPtrLE, indexLE, sourceLE);
      return resultLE;
    }
  } else if (mutability == Mutability::MUTABLE) {
    auto resultLE = loadInnerArrayMember(builder, arrayPtrLE, indexLE);
    storeInnerArrayMember(globalState, builder, arrayPtrLE, indexLE, sourceLE);
    return wrap(functionState->defaultRegion, elementRefM, resultLE);
  } else {
    assert(false);
  }
}


void foreachArrayElement(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref sizeRef,
    std::function<void(Ref, LLVMBuilderRef)> iterationBuilder) {
  LLVMValueRef iterationIndexPtrLE =
      makeMidasLocal(
          functionState,
          builder,
          LLVMInt64Type(),
          "iterationIndex",
          LLVMConstInt(LLVMInt64Type(),0, false));

  auto sizeLE = globalState->region->checkValidReference(FL(), functionState, builder, globalState->metalCache.intRef, sizeRef);

  buildWhile(
      globalState,
      functionState,
      builder,
      [globalState, functionState, sizeLE, iterationIndexPtrLE](LLVMBuilderRef conditionBuilder) {
        auto iterationIndexLE =
            LLVMBuildLoad(conditionBuilder, iterationIndexPtrLE, "iterationIndex");
        auto isBeforeEndLE =
            LLVMBuildICmp(
                conditionBuilder,LLVMIntSLT,iterationIndexLE,sizeLE,"iterationIndexIsBeforeEnd");
        return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, isBeforeEndLE);
      },
      [globalState, functionState, iterationBuilder, iterationIndexPtrLE](LLVMBuilderRef bodyBuilder) {
        auto iterationIndexLE = LLVMBuildLoad(bodyBuilder, iterationIndexPtrLE, "iterationIndex");
        auto iterationIndexRef = wrap(functionState->defaultRegion, globalState->metalCache.intRef, iterationIndexLE);
        iterationBuilder(iterationIndexRef, bodyBuilder);
        adjustCounter(bodyBuilder, iterationIndexPtrLE, 1);
      });
}

Ref loadElementtttFromUSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    Ref indexRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      auto sizeRef = std::make_shared<Ref>(getUnknownSizeArrayLengthNormal(globalState, functionState, builder, usaRefMT, arrayRef));
      auto arrayElementsPtrLE =
          getUnknownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  usaRefMT,
                  globalState->region->checkValidReference(FL(), functionState, builder, usaRefMT, arrayRef)));
      return loadElementWithoutUpgrade(
          globalState, functionState, builder, usaRefMT,
          usaMT->rawArray->elementType,
          *sizeRef, arrayElementsPtrLE, usaMT->rawArray->mutability, indexRef);
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (usaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN: {
          auto sizeRef = std::make_shared<Ref>(
              getUnknownSizeArrayLengthNormal(globalState, functionState, builder, usaRefMT,
                  arrayRef));
          auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  usaRefMT,
                  globalState->region->checkValidReference(FL(), functionState, builder, usaRefMT,
                      arrayRef)));
          return loadElementWithoutUpgrade(
              globalState, functionState, builder, usaRefMT,
              usaMT->rawArray->elementType,
              *sizeRef, arrayElementsPtrLE, usaMT->rawArray->mutability, indexRef);
          break;
        }
        case Ownership::BORROW: {
          auto sizeRef = std::make_shared<Ref>(
              getUnknownSizeArrayLengthForce(globalState, functionState, builder, usaRefMT,
                  arrayRef));
          auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder,
              lockWeakRef(FL(), globalState, functionState, builder, usaRefMT, arrayRef));
          return loadElementWithoutUpgrade(
              globalState, functionState, builder, usaRefMT,
              usaMT->rawArray->elementType,
              *sizeRef, arrayElementsPtrLE, usaMT->rawArray->mutability, indexRef);
          break;
        }
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
        default:
          assert(false);
      }
      break;
    }
    default:
      assert(false);
  }
}

Ref loadElementttFromKSAWithoutUpgradeInner(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref indexRef,
    LLVMValueRef arrayElementsPtrLE) {
  auto sizeRef =
      wrap(
          functionState->defaultRegion,
          globalState->metalCache.intRef,
          LLVMConstInt(LLVMInt64Type(), ksaMT->size, false));
  return loadElementWithoutUpgrade(
      globalState, functionState, builder, ksaRefMT,
      ksaMT->rawArray->elementType,
      sizeRef, arrayElementsPtrLE, ksaMT->rawArray->mutability, indexRef);
}

Ref loadElementtttFromKSAWithoutUpgradeNormal(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    Ref indexRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      // good, continue
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (ksaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
          // good, continue
          break;
        case Ownership::BORROW:
          assert(false);
          break;
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
        default:
          assert(false);
      }
      break;
    }
    default:
      assert(false);
  }

  LLVMValueRef arrayElementsPtrLE = getKnownSizeArrayContentsPtr(builder,
      functionState->defaultRegion->makeWrapperPtr(
          ksaRefMT,
          globalState->region->checkValidReference(FL(), functionState, builder, ksaRefMT, arrayRef)));

  return loadElementttFromKSAWithoutUpgradeInner(globalState, functionState, builder, ksaRefMT, ksaMT, indexRef, arrayElementsPtrLE);
}

Ref loadElementtttFromKSAWithoutUpgradeForce(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    Ref indexRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      assert(false);
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (ksaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
          assert(false);
          break;
        case Ownership::BORROW:
          // good, continue
          break;
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
        default:
          assert(false);
      }
      break;
    }
    default:
      assert(false);
  }
  LLVMValueRef arrayElementsPtrLE =
      getKnownSizeArrayContentsPtr(
          builder, lockWeakRef(FL(), globalState, functionState, builder, ksaRefMT, arrayRef));

  return loadElementttFromKSAWithoutUpgradeInner(globalState, functionState, builder, ksaRefMT, ksaMT, indexRef, arrayElementsPtrLE);
}

Ref loadElementtttFromKSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    Ref indexRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      return loadElementtttFromKSAWithoutUpgradeNormal(globalState, functionState, builder, ksaRefMT, ksaMT, arrayRef, indexRef);
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (ksaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
          return loadElementtttFromKSAWithoutUpgradeNormal(globalState, functionState, builder, ksaRefMT, ksaMT, arrayRef, indexRef);
          break;
        case Ownership::BORROW:
          return loadElementtttFromKSAWithoutUpgradeForce(globalState, functionState, builder, ksaRefMT, ksaMT, arrayRef, indexRef);
          break;
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
        default:
          assert(false);
      }
      break;
    }
    default:
      assert(false);
  }
}

Ref loadElementtttFromKSAWithUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    Ref indexRef,
    Reference* targetType) {
  Ref memberRef = loadElementtttFromKSAWithoutUpgrade(globalState, functionState, builder, ksaRefMT,
      ksaMT, arrayRef, indexRef);
  return upgradeLoadResultToRefWithTargetOwnership(
      globalState, functionState, builder, ksaMT->rawArray->elementType, targetType, memberRef);
}

Ref loadElementtttFromUSAWithUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    Ref indexRef,
    Reference* targetType) {
  Ref memberRef = loadElementtttFromUSAWithoutUpgrade(globalState, functionState, builder, usaRefMT,
      usaMT, arrayRef, indexRef);
  return upgradeLoadResultToRefWithTargetOwnership(
      globalState, functionState, builder, usaMT->rawArray->elementType, targetType, memberRef);
}

Ref storeElementtttInUSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    Ref indexRef,
    Ref elementRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      auto sizeRef = std::make_shared<Ref>(getUnknownSizeArrayLengthNormal(globalState, functionState, builder, usaRefMT, arrayRef));
      auto arrayElementsPtrLE =
          getUnknownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  usaRefMT,
                  globalState->region->checkValidReference(FL(), functionState, builder, usaRefMT, arrayRef)));
      return storeElement(
          globalState, functionState, builder, usaRefMT,
          usaMT->rawArray->elementType,
          *sizeRef, arrayElementsPtrLE, usaMT->rawArray->mutability, indexRef, elementRef);
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (usaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN: {
          auto sizeRef = std::make_shared<Ref>(
              getUnknownSizeArrayLengthNormal(globalState, functionState, builder, usaRefMT,
                  arrayRef));
          auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  usaRefMT,
                  globalState->region->checkValidReference(FL(), functionState, builder, usaRefMT,
                      arrayRef)));
          break;
        }
        case Ownership::BORROW: {
          auto sizeRef = std::make_shared<Ref>(
              getUnknownSizeArrayLengthForce(globalState, functionState, builder, usaRefMT,
                  arrayRef));
          auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder,
              lockWeakRef(FL(), globalState, functionState, builder, usaRefMT, arrayRef));

          return storeElement(
              globalState, functionState, builder, usaRefMT,
              usaMT->rawArray->elementType,
              *sizeRef, arrayElementsPtrLE, usaMT->rawArray->mutability, indexRef, elementRef);
        }
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
        default:
          assert(false);
      }
      break;
    }
    default:
      assert(false);
  }
  assert(false);
}

Ref storeElementttInKSAInner(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    LLVMValueRef arrayElementsPtrLE,
    Ref indexRef,
    Ref elementRef) {

  auto sizeRef =
      wrap(
          functionState->defaultRegion,
          globalState->metalCache.intRef,
          LLVMConstInt(LLVMInt64Type(), ksaMT->size, false));

  return storeElement(
      globalState, functionState, builder, ksaRefMT,
      ksaMT->rawArray->elementType,
      sizeRef, arrayElementsPtrLE, ksaMT->rawArray->mutability, indexRef, elementRef);
}

Ref storeElementtttInKSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    Ref indexRef,
    Ref elementRef) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      auto arrayElementsPtrLE =
          getKnownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  ksaRefMT,
                  globalState->region->checkValidReference(FL(), functionState, builder, ksaRefMT, arrayRef)));
      return storeElementttInKSAInner(globalState, functionState, builder, ksaRefMT, ksaMT, arrayElementsPtrLE, indexRef, elementRef);
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (ksaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN: {
          auto arrayElementsPtrLE = getKnownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  ksaRefMT,
                  globalState->region->checkValidReference(FL(), functionState, builder, ksaRefMT,
                      arrayRef)));
          return storeElementttInKSAInner(globalState, functionState, builder, ksaRefMT, ksaMT, arrayElementsPtrLE, indexRef, elementRef);
          break;
        }
        case Ownership::BORROW: {
          auto arrayElementsPtrLE = getKnownSizeArrayContentsPtr(builder,
              lockWeakRef(FL(), globalState, functionState, builder, ksaRefMT, arrayRef));
          return storeElementttInKSAInner(globalState, functionState, builder, ksaRefMT, ksaMT, arrayElementsPtrLE, indexRef, elementRef);
          break;
        }
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
        default:
          assert(false);
      }
      break;
    }
    default:
      assert(false);
  }

}
