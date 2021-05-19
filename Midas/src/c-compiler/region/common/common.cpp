#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <region/common/controlblock.h>
#include <function/expressions/shared/members.h>
#include <utils/counters.h>
#include <function/expressions/shared/elements.h>
#include <utils/branch.h>
#include <function/expressions/shared/string.h>
#include "common.h"

LLVMValueRef upcastThinPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    IReferendStructsSource* referendStructsSource,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructReferend* sourceStructReferendM,
    WrapperPtrLE sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  assert(sourceStructTypeM->location != Location::INLINE);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      assert(sourceStructTypeM->ownership == Ownership::SHARE ||
          sourceStructTypeM->ownership == Ownership::OWN ||
          sourceStructTypeM->ownership == Ownership::BORROW);
      break;
    }
    case RegionOverride::RESILIENT_V3: case RegionOverride::RESILIENT_V4: {
      assert(sourceStructTypeM->ownership == Ownership::SHARE ||
          sourceStructTypeM->ownership == Ownership::OWN);
      break;
    }
    default:
      assert(false);
  }
  ControlBlockPtrLE controlBlockPtrLE =
      referendStructsSource->getConcreteControlBlockPtr(
          FL(), functionState, builder, sourceStructTypeM, sourceRefLE);
  auto interfaceRefLE =
      makeInterfaceRefStruct(
          globalState, functionState, builder, referendStructsSource, sourceStructReferendM, targetInterfaceReferendM,
          controlBlockPtrLE);
  return interfaceRefLE;
}

LLVMTypeRef translateReferenceSimple(GlobalState* globalState, IReferendStructsSource* structs, Referend* referend) {
  if (auto ksaMT = dynamic_cast<KnownSizeArrayT *>(referend)) {
    auto knownSizeArrayCountedStructLT =
        structs->getKnownSizeArrayWrapperStruct(ksaMT);
    return LLVMPointerType(knownSizeArrayCountedStructLT, 0);
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT *>(referend)) {
    auto unknownSizeArrayCountedStructLT =
        structs->getUnknownSizeArrayWrapperStruct(usaMT);
    return LLVMPointerType(unknownSizeArrayCountedStructLT, 0);
  } else if (auto structReferend = dynamic_cast<StructReferend *>(referend)) {
    auto countedStructL = structs->getWrapperStruct(structReferend);
    return LLVMPointerType(countedStructL, 0);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend *>(referend)) {
    auto interfaceRefStructL = structs->getInterfaceRefStruct(interfaceReferend);
    return interfaceRefStructL;
  } else {
    std::cerr << "Unimplemented type: " << typeid(*referend).name() << std::endl;
    assert(false);
    return nullptr;
  }
}

LLVMTypeRef translateWeakReference(GlobalState* globalState, IWeakRefStructsSource* weakRefStructs, Referend* referend) {
  if (auto ksaMT = dynamic_cast<KnownSizeArrayT *>(referend)) {
    return weakRefStructs->getKnownSizeArrayWeakRefStruct(ksaMT);
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT *>(referend)) {
    return weakRefStructs->getUnknownSizeArrayWeakRefStruct(usaMT);
  } else if (auto structReferend = dynamic_cast<StructReferend *>(referend)) {
    return weakRefStructs->getStructWeakRefStruct(structReferend);
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend *>(referend)) {
    return weakRefStructs->getInterfaceWeakRefStruct(interfaceReferend);
  } else {
    assert(false);
  }
}

LoadResult loadInnerInnerStructMember(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef innerStructPtrLE,
    int memberIndex,
    Reference* expectedType,
    std::string memberName) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);

  auto result =
      LLVMBuildLoad(
          builder,
          LLVMBuildStructGEP(
              builder, innerStructPtrLE, memberIndex, memberName.c_str()),
          memberName.c_str());
  return LoadResult{wrap(globalState->getRegion(expectedType), expectedType, result)};
}

void storeInnerInnerStructMember(
    LLVMBuilderRef builder, LLVMValueRef innerStructPtrLE, int memberIndex, std::string memberName, LLVMValueRef newValueLE) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);
  LLVMBuildStore(
      builder,
      newValueLE,
      LLVMBuildStructGEP(
          builder, innerStructPtrLE, memberIndex, memberName.c_str()));
}

LLVMValueRef getItablePtrFromInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    InterfaceFatPtrLE virtualArgLE) {
  buildFlare(FL(), globalState, functionState, builder);
  assert(LLVMTypeOf(virtualArgLE.refLE) == globalState->getRegion(virtualParamMT)->translateType(virtualParamMT));
  return getTablePtrFromInterfaceRef(builder, virtualArgLE);
}


LLVMValueRef fillControlBlockCensusFields(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    IReferendStructsSource* structs,
    LLVMBuilderRef builder,
    Referend* referendM,
    LLVMValueRef newControlBlockLE,
    const std::string& typeName) {
  if (globalState->opt->census) {
    auto objIdLE = adjustCounter(globalState, builder, globalState->objIdCounter, 1);
    newControlBlockLE =
        LLVMBuildInsertValue(
            builder,
            newControlBlockLE,
            objIdLE,
            structs->getControlBlock(referendM)->getMemberIndex(ControlBlockMember::CENSUS_OBJ_ID),
            "strControlBlockWithObjId");
    newControlBlockLE =
        LLVMBuildInsertValue(
            builder,
            newControlBlockLE,
            globalState->getOrMakeStringConstant(typeName),
            structs->getControlBlock(referendM)->getMemberIndex(ControlBlockMember::CENSUS_TYPE_STR),
            "strControlBlockWithTypeStr");
    buildFlare(from, globalState, functionState, builder, "Allocating ", typeName, " ", objIdLE);
  }
  return newControlBlockLE;
}

LLVMValueRef insertStrongRc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    IReferendStructsSource* structs,
    Referend* referendM,
    LLVMValueRef newControlBlockLE) {
  return LLVMBuildInsertValue(
      builder,
      newControlBlockLE,
      // Start RC at 1, see SRCAZ.
      LLVMConstInt(LLVMInt32TypeInContext(globalState->context), 1, false),
      structs->getControlBlock(referendM)->getMemberIndex(ControlBlockMember::STRONG_RC),
      "controlBlockWithRc");
}

LoadResult loadElementFromKSAInner(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    int size,
    Reference* elementType,
    Ref indexRef,
    LLVMValueRef arrayElementsPtrLE) {
  auto sizeRef =
      wrap(
          globalState->getRegion(globalState->metalCache->intRef),
          globalState->metalCache->intRef,
          LLVMConstInt(LLVMInt64TypeInContext(globalState->context), size, false));
  buildFlare(FL(), globalState, functionState, builder);
  return loadElement(
      globalState, functionState, builder, arrayElementsPtrLE, elementType, sizeRef, indexRef);
}

// Checks that the generation is <= to the actual one.
void buildCheckGen(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef targetGenLE,
    LLVMValueRef actualGenLE) {
  auto isValidLE =
      LLVMBuildICmp(builder, LLVMIntSLE, targetGenLE, actualGenLE, "genIsValid");
  buildAssert(
      globalState, functionState, builder, isValidLE,
      "Invalid generation, from the future!");
}

// Not returning Ref because we might need to wrap it in something else like a weak fat ptr
LLVMValueRef makeInterfaceRefStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* structs,
    StructReferend* sourceStructReferendM,
    InterfaceReferend* targetInterfaceReferendM,
    ControlBlockPtrLE controlBlockPtrLE) {

  auto interfaceRefLT = structs->getInterfaceRefStruct(targetInterfaceReferendM);

  auto interfaceRefLE = LLVMGetUndef(interfaceRefLT);
  interfaceRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceRefLE,
          controlBlockPtrLE.refLE,
          0,
          "interfaceRefWithOnlyObj");
  auto itablePtrLE =
      globalState->getInterfaceTablePtr(
          globalState->program->getStruct(sourceStructReferendM->fullName)
              ->getEdgeForInterface(targetInterfaceReferendM->fullName));
  interfaceRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceRefLE,
          itablePtrLE,
          1,
          "interfaceRef");
  buildFlare(FL(), globalState, functionState, builder, "itable: ", ptrToIntLE(globalState, builder, itablePtrLE), " for ", sourceStructReferendM->fullName->name, " for ", targetInterfaceReferendM->fullName->name);

  return interfaceRefLE;
}


constexpr int INTERFACE_REF_MEMBER_INDEX_FOR_OBJ_PTR = 0;
constexpr int INTERFACE_REF_MEMBER_INDEX_FOR_ITABLE_PTR = 1;

LLVMValueRef getObjPtrFromInterfaceRef(
    LLVMBuilderRef builder,
    InterfaceFatPtrLE interfaceRefLE) {
  return LLVMBuildExtractValue(builder, interfaceRefLE.refLE, INTERFACE_REF_MEMBER_INDEX_FOR_OBJ_PTR, "objPtr");
}

LLVMValueRef getTablePtrFromInterfaceRef(
    LLVMBuilderRef builder,
    InterfaceFatPtrLE interfaceRefLE) {
  return LLVMBuildExtractValue(builder, interfaceRefLE.refLE, INTERFACE_REF_MEMBER_INDEX_FOR_ITABLE_PTR, "itablePtr");
}

LLVMValueRef callFree(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef ptrLE) {
  if (globalState->opt->genHeap) {
    auto concreteAsVoidPtrLE =
        LLVMBuildBitCast(
            builder,
            ptrLE,
            LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0),
            "concreteVoidPtrForFree");
    return LLVMBuildCall(builder, globalState->genFree, &concreteAsVoidPtrLE, 1, "");
  } else {
    auto concreteAsCharPtrLE =
        LLVMBuildBitCast(
            builder,
            ptrLE,
            LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0),
            "concreteCharPtrForFree");
    return LLVMBuildCall(builder, globalState->externs->free, &concreteAsCharPtrLE, 1, "");
  }
}

void innerDeallocateYonder(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    IReferendStructsSource* referendStructsSource,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  if (globalState->opt->census) {
    auto ptrLE =
        globalState->getRegion(refMT)
            ->checkValidReference(FL(), functionState, builder, refMT, ref);
    auto objIdLE =
        globalState->getRegion(refMT)
            ->getCensusObjectId(FL(), functionState, builder, refMT, ref);
    if (dynamic_cast<InterfaceReferend*>(refMT->referend) == nullptr) {
      buildFlare(FL(), globalState, functionState, builder,
          "Deallocating object &", ptrToIntLE(globalState, builder, ptrLE), " obj id ", objIdLE, "\n");
    }
  }

  auto controlBlockPtrLE = referendStructsSource->getControlBlockPtr(from, functionState, builder,
      ref, refMT);

  globalState->getRegion(refMT)
      ->noteWeakableDestroyed(functionState, builder, refMT, controlBlockPtrLE);

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, controlBlockPtrLE.refLE, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "");
    LLVMBuildCall(builder, globalState->externs->censusRemove, &resultAsVoidPtrLE, 1,
        "");
  }

  callFree(globalState, builder, controlBlockPtrLE.refLE);

  if (globalState->opt->census) {
    adjustCounter(globalState, builder, globalState->liveHeapObjCounter, -1);
  }
}

void innerDeallocate(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    IReferendStructsSource* referendStrutsSource,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  if (refMT->ownership == Ownership::SHARE) {
    if (refMT->location == Location::INLINE) {
      // Do nothing, it's inline!
    } else {
      return innerDeallocateYonder(from, globalState, functionState, referendStrutsSource, builder, refMT, ref);
    }
  } else {
    if (refMT->location == Location::INLINE) {
      assert(false); // implement
    } else {
      return innerDeallocateYonder(from, globalState, functionState, referendStrutsSource, builder, refMT, ref);
    }
  }
}

void fillKnownSizeArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref ksaRef,
    const std::vector<Ref>& elementRefs) {

  for (int i = 0; i < elementRefs.size(); i++) {
    globalState->getRegion(ksaRefMT)->initializeElementInKSA(
        functionState, builder, ksaRefMT, ksaMT, ksaRef, true, globalState->constI64(i), elementRefs[i]);
  }
}

void fillUnknownSizeArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Reference* elementType,
    Reference* generatorType,
    Prototype* generatorMethod,
    Ref generatorLE,
    Ref sizeLE,
    Ref usaRef) {

  intRangeLoop(
      globalState, functionState, builder, sizeLE,
      [globalState, functionState, usaRefMT, usaMT, generatorMethod, generatorType, usaRef, generatorLE](
          Ref indexRef, LLVMBuilderRef bodyBuilder) {
        globalState->getRegion(generatorType)->alias(
            AFL("ConstructUSA generate iteration"),
            functionState, bodyBuilder, generatorType, generatorLE);
        std::vector<Ref> argExprsLE = { generatorLE, indexRef };

        auto elementRef =
            buildCall(
                globalState, functionState, bodyBuilder, generatorMethod, argExprsLE);
        globalState->getRegion(usaMT)->initializeElementInUSA(
            functionState, bodyBuilder, usaRefMT, usaMT, usaRef, true, indexRef, elementRef);
      });
}

void fillKnownSizeArrayFromCallable(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Reference* elementType,
    Reference* generatorType,
    Prototype* generatorMethod,
    Ref generatorLE,
    Ref sizeLE,
    Ref ksaRef) {

  intRangeLoop(
      globalState, functionState, builder, sizeLE,
      [globalState, functionState, ksaRefMT, ksaMT, generatorMethod, generatorType, ksaRef, generatorLE](
          Ref indexRef, LLVMBuilderRef bodyBuilder) {
        globalState->getRegion(generatorType)->alias(
            AFL("ConstructKSA generate iteration"),
            functionState, bodyBuilder, generatorType, generatorLE);
        std::vector<Ref> argExprsLE = { generatorLE, indexRef };

        auto elementRef =
            buildCall(
                globalState, functionState, bodyBuilder, generatorMethod, argExprsLE);
        globalState->getRegion(ksaMT)->initializeElementInKSA(
            functionState, bodyBuilder, ksaRefMT, ksaMT, ksaRef, true, indexRef, elementRef);
      });
}

std::tuple<Reference*, LLVMValueRef> megaGetRefInnardsForChecking(Ref ref) {
  Reference* refM = ref.refM;
  LLVMValueRef refLE = ref.refLE;
  return std::make_tuple(refM, refLE);
}

LLVMValueRef callMalloc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef sizeLE) {
  if (globalState->opt->genHeap) {
    return LLVMBuildCall(builder, globalState->genMalloc, &sizeLE, 1, "");
  } else {
    return LLVMBuildCall(builder, globalState->externs->malloc, &sizeLE, 1, "");
  }
}

WrapperPtrLE mallocStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthLE,
    LLVMValueRef sourceCharsPtrLE,
    IReferendStructsSource* referendStructs,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock) {
  // The +1 is for the null terminator at the end, for C compatibility.
  auto sizeBytesLE =
      LLVMBuildAdd(
          builder,
          lengthLE,
          LLVMBuildAdd(
              builder,
              constI64LE(globalState, 1),
              constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, referendStructs->getStringWrapperStruct())),
              "lenPlus1"),
          "strMallocSizeBytes");

  auto destCharPtrLE = callMalloc(globalState, builder, LLVMBuildZExt(builder, sizeBytesLE, LLVMInt64TypeInContext(globalState->context), "lenPlus1As64"));

  if (globalState->opt->census) {
    adjustCounter(globalState, builder, globalState->liveHeapObjCounter, 1);

    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, destCharPtrLE, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "");
    LLVMBuildCall(builder, globalState->externs->censusAdd, &resultAsVoidPtrLE, 1, "");
  }

  auto newStrWrapperPtrLE =
      referendStructs->makeWrapperPtr(
          FL(), functionState, builder, globalState->metalCache->strRef,
          LLVMBuildBitCast(
              builder,
              destCharPtrLE,
              LLVMPointerType(referendStructs->getStringWrapperStruct(), 0),
              "newStrWrapperPtr"));

  fillControlBlock(
      builder,
      referendStructs->getConcreteControlBlockPtr(FL(), functionState, builder, globalState->metalCache->strRef, newStrWrapperPtrLE));
  LLVMBuildStore(builder, LLVMBuildZExt(builder, lengthLE, LLVMInt64TypeInContext(globalState->context), ""), getLenPtrFromStrWrapperPtr(builder, newStrWrapperPtrLE));

  // Set the null terminating character to the 0th spot and the end spot, just to guard against bugs
  auto charsBeginPtr = getCharsPtrFromWrapperPtr(globalState, builder, newStrWrapperPtrLE);


  std::vector<LLVMValueRef> strncpyArgsLE = { charsBeginPtr, sourceCharsPtrLE, lengthLE };
  LLVMBuildCall(builder, globalState->externs->strncpy, strncpyArgsLE.data(), strncpyArgsLE.size(), "");

  auto charsEndPtr = LLVMBuildGEP(builder, charsBeginPtr, &lengthLE, 1, "charsEndPtr");
  LLVMBuildStore(builder, constI8LE(globalState, 0), charsEndPtr);

  // The caller still needs to initialize the actual chars inside!

  return newStrWrapperPtrLE;
}

LLVMValueRef mallocKnownSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    LLVMTypeRef referendLT) {
  if (globalState->opt->census) {
    adjustCounter(globalState, builder, globalState->liveHeapObjCounter, 1);
  }

  LLVMValueRef resultPtrLE = nullptr;
  if (location == Location::INLINE) {
    resultPtrLE = makeMidasLocal(functionState, builder, referendLT, "newstruct", LLVMGetUndef(referendLT));
  } else if (location == Location::YONDER) {
    size_t sizeBytes = LLVMABISizeOfType(globalState->dataLayout, referendLT);
    LLVMValueRef sizeLE = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), sizeBytes, false);

    auto newStructLE = callMalloc(globalState, builder, sizeLE);

    resultPtrLE =
        LLVMBuildBitCast(
            builder, newStructLE, LLVMPointerType(referendLT, 0), "newstruct");
  } else {
    assert(false);
    return nullptr;
  }

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, resultPtrLE, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "");
    LLVMBuildCall(builder, globalState->externs->censusAdd, &resultAsVoidPtrLE, 1, "");
  }
  return resultPtrLE;
}

void fillInnerStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structM,
    std::vector<Ref> membersLE,
    LLVMValueRef innerStructPtrLE) {
  for (int i = 0; i < membersLE.size(); i++) {
    auto memberRef = membersLE[i];
    auto memberType = structM->members[i]->type;
    auto memberName = structM->members[i]->name;
    auto ptrLE =
        LLVMBuildStructGEP(builder, innerStructPtrLE, i, memberName.c_str());
    auto memberLE =
        globalState->getRegion(memberType)
            ->checkValidReference(FL(), functionState, builder, structM->members[i]->type, memberRef);
    LLVMBuildStore(builder, memberLE, ptrLE);
  }
}

Ref constructWrappedStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    IReferendStructsSource* referendStructsSource,
    LLVMBuilderRef builder,
    LLVMTypeRef structL,
    Reference* structTypeM,
    StructDefinition* structM,
    Weakability effectiveWeakability,
    std::vector<Ref> membersLE,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock) {

  auto ptrLE = mallocKnownSize(globalState, functionState, builder, structTypeM->location, structL);

  WrapperPtrLE newStructWrapperPtrLE =
      referendStructsSource->makeWrapperPtr(
          FL(), functionState, builder, structTypeM,
          ptrLE);
//  globalState->getRegion(refHere)->fillControlBlock(
//      from,
//      functionState, builder,
//      structTypeM->referend,
//      structM->mutability,
//      referendStructsSource->getConcreteControlBlockPtr(from, functionState, builder, structTypeM, newStructWrapperPtrLE), structM->name->name);
  fillControlBlock(
      builder,
      referendStructsSource->getConcreteControlBlockPtr(
          FL(), functionState, builder, structTypeM, newStructWrapperPtrLE));
  fillInnerStruct(
      globalState, functionState,
      builder, structM, membersLE,
      referendStructsSource->getStructContentsPtr(builder, structTypeM->referend, newStructWrapperPtrLE));

  auto refLE = wrap(globalState->getRegion(structTypeM), structTypeM, newStructWrapperPtrLE.refLE);

  if (globalState->opt->census) {
    auto objIdLE =
        globalState->getRegion(structTypeM)
            ->getCensusObjectId(FL(), functionState, builder, structTypeM, refLE);
    buildFlare(
        FL(), globalState, functionState, builder,
        "Allocated object ", structM->name->name, " &", ptrToIntLE(globalState, builder, ptrLE),
        " obj id ", objIdLE, "\n");
  }

  return refLE;
}

LLVMValueRef constructInnerStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructDefinition* structM,
    LLVMTypeRef valStructL,
    const std::vector<Ref>& memberRefs) {

  // We always start with an undef, and then fill in its fields one at a
  // time.
  LLVMValueRef structValueBeingInitialized = LLVMGetUndef(valStructL);
  for (int i = 0; i < memberRefs.size(); i++) {
    auto memberLE =
        globalState->getRegion(structM->members[i]->type)
            ->checkValidReference(FL(), functionState, builder, structM->members[i]->type, memberRefs[i]);
    auto memberName = structM->members[i]->name;
    // Every time we fill in a field, it actually makes a new entire
    // struct value, and gives us a LLVMValueRef for the new value.
    // So, `structValueBeingInitialized` contains the latest one.
    structValueBeingInitialized =
        LLVMBuildInsertValue(
            builder,
            structValueBeingInitialized,
            memberLE,
            i,
            memberName.c_str());
  }
  return structValueBeingInitialized;
}

Ref innerAllocate(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* desiredReference,
    IReferendStructsSource* referendStructs,
    const std::vector<Ref>& memberRefs,
    Weakability effectiveWeakability,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock) {
  auto structReferend = dynamic_cast<StructReferend*>(desiredReference->referend);
  auto structM = globalState->program->getStruct(structReferend->fullName);

  switch (structM->mutability) {
    case Mutability::MUTABLE: {
      auto countedStructL = referendStructs->getWrapperStruct(structReferend);
      return constructWrappedStruct(
          globalState, functionState, referendStructs, builder, countedStructL, desiredReference,
          structM, effectiveWeakability, memberRefs, fillControlBlock);
    }
    case Mutability::IMMUTABLE: {
      if (desiredReference->location == Location::INLINE) {
        auto valStructL =
            referendStructs->getInnerStruct(structReferend);
        auto innerStructLE =
            constructInnerStruct(
                globalState, functionState, builder, structM, valStructL, memberRefs);
        return wrap(globalState->getRegion(desiredReference), desiredReference, innerStructLE);
      } else {
        auto countedStructL =
            referendStructs->getWrapperStruct(structReferend);
        return constructWrappedStruct(
            globalState, functionState, referendStructs, builder, countedStructL, desiredReference,
            structM, effectiveWeakability, memberRefs, fillControlBlock);
      }
    }
    default:
      assert(false);
  }
  assert(false);
}

// Transmutes a weak ref of one ownership (such as borrow) to another ownership (such as weak).
Ref transmuteWeakRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceWeakRefMT,
    Reference* targetWeakRefMT,
    IWeakRefStructsSource* weakRefStructs,
    Ref sourceWeakRef) {
  // The WeakFatPtrLE constructors here will make sure that its a safe and valid transmutation.
  auto sourceWeakFatPtrLE =
      weakRefStructs->makeWeakFatPtr(
          sourceWeakRefMT,
          globalState->getRegion(sourceWeakRefMT)->checkValidReference(
              FL(), functionState, builder, sourceWeakRefMT, sourceWeakRef));
  auto sourceWeakFatPtrRawLE = sourceWeakFatPtrLE.refLE;
  auto targetWeakFatPtrLE = weakRefStructs->makeWeakFatPtr(targetWeakRefMT, sourceWeakFatPtrRawLE);
  auto targetWeakRef = wrap(globalState->getRegion(targetWeakRefMT), targetWeakRefMT, targetWeakFatPtrLE);
  return targetWeakRef;
}

LLVMValueRef mallocUnknownSizeArray(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef usaWrapperLT,
    LLVMTypeRef usaElementLT,
    LLVMValueRef lengthLE) {
  auto sizeBytesLE =
      LLVMBuildAdd(
          builder,
          constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, usaWrapperLT)),
          LLVMBuildMul(
              builder,
              constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(usaElementLT, 1))),
              lengthLE,
              ""),
          "usaMallocSizeBytes");

  auto newWrapperPtrLE = callMalloc(globalState, builder, sizeBytesLE);

  if (globalState->opt->census) {
    adjustCounter(globalState, builder, globalState->liveHeapObjCounter, 1);
  }

  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, newWrapperPtrLE, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "");
    LLVMBuildCall(builder, globalState->externs->censusAdd, &resultAsVoidPtrLE, 1, "");
  }

  return LLVMBuildBitCast(
      builder,
      newWrapperPtrLE,
      LLVMPointerType(usaWrapperLT, 0),
      "newstruct");
}

// Transmutes a ptr of one ownership (such as own) to another ownership (such as borrow).
Ref transmutePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef) {
  // The WrapperPtrLE constructors here will make sure that its a safe and valid transmutation.
  auto sourcePtrRawLE =
      globalState->getRegion(sourceRefMT)
          ->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef);
  auto targetWeakRef = wrap(globalState->getRegion(targetRefMT), targetRefMT, sourcePtrRawLE);
  return targetWeakRef;
}


Ref getUnknownSizeArrayLength(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE arrayRefLE) {
  auto lengthPtrLE = getUnknownSizeArrayLengthPtr(globalState, builder, arrayRefLE);
  auto intLE = LLVMBuildLoad(builder, lengthPtrLE, "usaLen");
  return wrap(globalState->getRegion(globalState->metalCache->intRef), globalState->metalCache->intRef, intLE);
}

ControlBlock makeAssistAndNaiveRCNonWeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutNonWeakableControlBlock"));
  controlBlock.addMember(ControlBlockMember::STRONG_RC);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.build();
  return controlBlock;
}

ControlBlock makeAssistAndNaiveRCWeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutWeakableControlBlock"));
  controlBlock.addMember(ControlBlockMember::STRONG_RC);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.addMember(ControlBlockMember::WRCI);
  // We could add this in to avoid an InstructionCombiningPass bug where when it inlines things
  // it doesnt seem to realize that there's padding at the end of structs.
  // To see it, make loadFromWeakable test in fast mode, see its .ll and its .opt.ll, it seems
  // to get the wrong pointer for the first member.
  // mutWeakableControlBlock.addMember(ControlBlockMember::UNUSED_32B);
  controlBlock.build();
  return controlBlock;
}
// TODO see if we can combine this with assist+naiverc weakable.
ControlBlock makeFastWeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutWeakableControlBlock"));
  // Fast mode mutables have no strong RC
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.addMember(ControlBlockMember::WRCI);
  controlBlock.build();
  return controlBlock;
}

ControlBlock makeFastNonWeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutNonWeakableControlBlock"));
  // Fast mode mutables have no strong RC
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.build();
  return controlBlock;
}


ControlBlock makeResilientV0WeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutWeakableControlBlock"));
  controlBlock.addMember(ControlBlockMember::WRCI);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.build();
  return controlBlock;
}
Ref resilientLockWeak(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference* resultOptTypeM,
    Reference* constraintRefM,
    Reference* sourceWeakRefMT,
    Ref sourceWeakRefLE,
    bool weakRefKnownLive,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse,
    Ref isAliveLE,
    LLVMTypeRef resultOptTypeL,
    IWeakRefStructsSource* weakRefStructs) {
  return buildIfElse(
      globalState, functionState, builder, isAliveLE,
      resultOptTypeL,
      resultOptTypeM,
      resultOptTypeM,
      [globalState, functionState, constraintRefM, weakRefStructs, sourceWeakRefLE, sourceWeakRefMT, buildThen](LLVMBuilderRef thenBuilder) {
        // TODO extract more of this common code out?
        // The incoming "constraint" ref is actually already a week ref, so just return it
        // (after wrapping it in a different Ref that actually thinks/knows it's a weak
        // reference).
        auto constraintRef =
            transmuteWeakRef(
                globalState, functionState, thenBuilder, sourceWeakRefMT, constraintRefM,
                weakRefStructs, sourceWeakRefLE);
        return buildThen(thenBuilder, constraintRef);
      },
      buildElse);
}

ControlBlock makeResilientV1WeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutControlBlock"));
  controlBlock.addMember(ControlBlockMember::LGTI);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.build();
  return controlBlock;
}

Ref normalLocalStore(GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr, Ref refToStore) {
  auto region = globalState->getRegion(local->type);
  // We need to load the old ref *after* we evaluate the source expression,
  // Because of expressions like: Ship() = (mut b = (mut a = (mut b = Ship())));
  // See mutswaplocals.vale for test case.
  auto oldRefLE = LLVMBuildLoad(builder, localAddr, local->id->maybeName.c_str());
  auto oldRef = wrap(region, local->type, oldRefLE);
  region->checkValidReference(FL(), functionState, builder, local->type, oldRef);
  auto toStoreLE = region->checkValidReference(FL(), functionState, builder, local->type, refToStore);
  LLVMBuildStore(builder, toStoreLE, localAddr);
  return oldRef;
}

//StructsRouter makeAssistAndNaiveRCModeLayoutter(GlobalState* globalState) {
//  return StructsRouter(
//      globalState,
//      makeImmControlBlock(globalState),
//      makeAssistAndNaiveRCWeakableControlBlock(globalState),
//      makeAssistAndNaiveRCNonWeakableControlBlock(globalState));
//}
//StructsRouter makeFastModeLayoutter(GlobalState* globalState) {
//  return StructsRouter(
//      globalState,
//      makeImmControlBlock(globalState),
//      makeFastNonWeakableControlBlock(globalState),
//      makeFastWeakableControlBlock(globalState));
//}
//StructsRouter makeResilientV0Layoutter(GlobalState* globalState) {
//  return StructsRouter(
//      globalState,
//      makeImmControlBlock(globalState),
//      makeResilientV0WeakableControlBlock(globalState),
//      makeResilientV0WeakableControlBlock(globalState));
//}
//StructsRouter makeResilientV1Layoutter(GlobalState* globalState) {
//  return StructsRouter(
//      globalState,
//      makeImmControlBlock(globalState),
//      makeResilientV1WeakableControlBlock(globalState),
//      makeResilientV1WeakableControlBlock(globalState));
//}
//StructsRouter makeResilientV2Layoutter(GlobalState* globalState) {
//  return StructsRouter(
//      globalState,
//      makeImmControlBlock(globalState),
//      makeResilientV3WeakableControlBlock(globalState),
//      makeResilientV3WeakableControlBlock(globalState));
//}

// Returns a LLVMValueRef for a ref to the string object.
// The caller should then use getStringBytesPtr to then fill the string's contents.
Ref constructKnownSizeArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    KnownSizeArrayT* ksaMT,
    IReferendStructsSource* referendStructs,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock) {

  auto structLT =
      referendStructs->getKnownSizeArrayWrapperStruct(ksaMT);
  auto newStructLE =
      referendStructs->makeWrapperPtr(
          FL(), functionState, builder, refM,
          mallocKnownSize(globalState, functionState, builder, refM->location, structLT));
  fillControlBlock(
      builder,
      referendStructs->getConcreteControlBlockPtr(FL(), functionState, builder, refM, newStructLE));
  return wrap(globalState->getRegion(refM), refM, newStructLE.refLE);
}


void regularCheckValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* refM,
    LLVMValueRef refLE) {

  if (auto interfaceReferendM = dynamic_cast<InterfaceReferend *>(refM->referend)) {
    auto interfaceFatPtrLE = referendStructs->makeInterfaceFatPtr(checkerAFL, functionState, builder,
        refM, refLE);
    auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
  }
  if (refM->location == Location::INLINE) {
    // Nothing to do, there's no control block or ref counts or anything.
  } else if (refM->location == Location::YONDER) {
    auto controlBlockPtrLE =
        referendStructs->getControlBlockPtr(checkerAFL, functionState, builder, refLE, refM);

    // We dont check ref count >0 because imm destructors receive with rc=0.
    //      auto rcLE = getRcFromControlBlockPtr(globalState, builder, controlBlockPtrLE);
    //      auto rcPositiveLE = LLVMBuildICmp(builder, LLVMIntSGT, rcLE, constI64LE(globalState, 0), "");
    //      buildAssert(checkerAFL, globalState, functionState, blockState, builder, rcPositiveLE, "Invalid RC!");

    buildAssertCensusContains(checkerAFL, globalState, functionState, builder,
        controlBlockPtrLE.refLE);
  } else
    assert(false);
}

LoadResult regularLoadElementFromUSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Mutability mutability,
    Reference* elementType,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto wrapperPtrLE =
      referendStructs->makeWrapperPtr(
          FL(), functionState, builder, usaRefMT,
          globalState->getRegion(usaRefMT)->checkValidReference(FL(), functionState, builder, usaRefMT, arrayRef));
  auto sizeRef = ::getUnknownSizeArrayLength(globalState, functionState, builder, wrapperPtrLE);
  auto arrayElementsPtrLE =
      getUnknownSizeArrayContentsPtr(builder,
          referendStructs->makeWrapperPtr(
              FL(), functionState, builder, usaRefMT,
              globalState->getRegion(usaRefMT)->checkValidReference(FL(), functionState, builder, usaRefMT, arrayRef)));
  buildFlare(FL(), globalState, functionState, builder);
  return loadElement(
      globalState, functionState, builder, arrayElementsPtrLE, elementType, sizeRef, indexRef);
}

LoadResult resilientLoadElementFromUSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* usaRefMT,
    Mutability mutability,
    Reference* elementType,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  switch (usaRefMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN: {
      auto wrapperPtrLE =
          referendStructs->makeWrapperPtr(
              FL(), functionState, builder, usaRefMT,
              globalState->getRegion(usaRefMT)->checkValidReference(FL(), functionState, builder, usaRefMT, arrayRef));
      auto sizeRef = ::getUnknownSizeArrayLength(globalState, functionState, builder, wrapperPtrLE);
      auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder,
          referendStructs->makeWrapperPtr(
              FL(), functionState, builder, usaRefMT,
              globalState->getRegion(usaRefMT)->checkValidReference(FL(), functionState, builder, usaRefMT,
                  arrayRef)));
      buildFlare(FL(), globalState, functionState, builder);
      return loadElement(
          globalState, functionState, builder, arrayElementsPtrLE, elementType, sizeRef, indexRef);
    }
    case Ownership::BORROW: {
      auto wrapperPtrLE =
          globalState->getRegion(usaRefMT)->lockWeakRef(
              FL(), functionState, builder, usaRefMT, arrayRef, arrayKnownLive);
      auto sizeRef = ::getUnknownSizeArrayLength(globalState, functionState, builder, wrapperPtrLE);
      auto arrayElementsPtrLE =
          getUnknownSizeArrayContentsPtr(
              builder,
              globalState->getRegion(usaRefMT)->lockWeakRef(FL(), functionState, builder, usaRefMT, arrayRef, arrayKnownLive));
      buildFlare(FL(), globalState, functionState, builder);
      return loadElement(
          globalState, functionState, builder, arrayElementsPtrLE, elementType,
          sizeRef, indexRef);
    }
    case Ownership::WEAK:
      assert(false); // VIR never loads from a weak ref
    default:
      assert(false);
  }
}

Ref regularStoreElementInKSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* ksaRefMT,
    Reference* elementType,
    int size,
    Ref arrayRef,
    Ref indexRef,
    Ref elementRef) {
  auto arrayElementsPtrLE =
      getKnownSizeArrayContentsPtr(
          builder,
          referendStructs->makeWrapperPtr(
              FL(), functionState, builder, ksaRefMT,
              globalState->getRegion(ksaRefMT)->checkValidReference(FL(), functionState, builder, ksaRefMT, arrayRef)));
  buildFlare(FL(), globalState, functionState, builder);
  return swapElement(
      globalState, functionState, builder, ksaRefMT->location,
      elementType, globalState->constI64(size), arrayElementsPtrLE, indexRef, elementRef);
}

void regularInitializeElementInKSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* ksaRefMT,
    Reference* elementType,
    int size,
    Ref arrayRef,
    Ref indexRef,
    Ref elementRef) {
  auto arrayElementsPtrLE =
      getKnownSizeArrayContentsPtr(
          builder,
          referendStructs->makeWrapperPtr(
              FL(), functionState, builder, ksaRefMT,
              globalState->getRegion(ksaRefMT)->checkValidReference(FL(), functionState, builder, ksaRefMT, arrayRef)));
  buildFlare(FL(), globalState, functionState, builder);
  initializeElement(
      globalState, functionState, builder, ksaRefMT->location,
      elementType, globalState->constI64(size), arrayElementsPtrLE, indexRef, elementRef);
}

Ref constructUnknownSizeArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* usaMT,
    Reference* elementType,
    UnknownSizeArrayT* unknownSizeArrayT,
    LLVMTypeRef usaWrapperPtrLT,
    LLVMTypeRef usaElementLT,
    Ref sizeRef,
    const std::string& typeName,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock) {
  buildFlare(FL(), globalState, functionState, builder, "Constructing USA!");

  auto sizeLE =
      globalState->getRegion(globalState->metalCache->intRef)->checkValidReference(FL(),
          functionState, builder, globalState->metalCache->intRef, sizeRef);
  auto ptrLE = mallocUnknownSizeArray(globalState, builder, usaWrapperPtrLT, usaElementLT, sizeLE);
  auto usaWrapperPtrLE =
      referendStructs->makeWrapperPtr(FL(), functionState, builder, usaMT, ptrLE);
  fillControlBlock(
      builder,
      referendStructs->getConcreteControlBlockPtr(FL(), functionState, builder, usaMT, usaWrapperPtrLE));
  LLVMBuildStore(builder, sizeLE, getUnknownSizeArrayLengthPtr(globalState, builder, usaWrapperPtrLE));
  auto refLE = wrap(globalState->getRegion(usaMT), usaMT, usaWrapperPtrLE.refLE);

  if (globalState->opt->census) {
    auto objIdLE =
        globalState->getRegion(usaMT)
            ->getCensusObjectId(FL(), functionState, builder, usaMT, refLE);
    auto addrIntLE = ptrToIntLE(globalState, builder, ptrLE);
    buildFlare(
        FL(), globalState, functionState, builder,
        "Allocated object ", typeName, " &", addrIntLE, " obj id ", objIdLE, "\n");
  }

  return refLE;
}

LoadResult regularLoadMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {

  if (structRefMT->location == Location::INLINE) {
    auto structRefLE = globalState->getRegion(structRefMT)->checkValidReference(FL(), functionState, builder,
        structRefMT, structRef);
    return LoadResult{
      wrap(globalState->getRegion(expectedMemberType), expectedMemberType,
        LLVMBuildExtractValue(
            builder, structRefLE, memberIndex, memberName.c_str()))};
  } else {
    switch (structRefMT->ownership) {
      case Ownership::OWN:
      case Ownership::SHARE:
      case Ownership::BORROW: {
        return regularLoadStrongMember(globalState, functionState, builder, referendStructs, structRefMT, structRef, memberIndex, expectedMemberType, targetType, memberName);
      }
      case Ownership::WEAK:
        assert(false); // we arent supposed to force in naive/fast
        break;
      default:
        assert(false);
    }
  }
}

LoadResult resilientLoadWeakMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    Reference* expectedMemberType,
    const std::string& memberName) {
  auto wrapperPtrLE =
      globalState->getRegion(structRefMT)->lockWeakRef(
          FL(), functionState, builder, structRefMT, structRef, structKnownLive);
  auto innerStructPtrLE = referendStructs->getStructContentsPtr(builder,
      structRefMT->referend, wrapperPtrLE);
  return loadInnerInnerStructMember(
      globalState, builder, innerStructPtrLE, memberIndex, expectedMemberType, memberName);
}

Ref upcastStrong(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* sourceStructMT,
    StructReferend* sourceStructReferendM,
    Ref sourceRefLE,
    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  auto sourceStructWrapperPtrLE =
      referendStructs->makeWrapperPtr(
          FL(), functionState, builder, sourceStructMT,
          globalState->getRegion(sourceStructMT)->checkValidReference(FL(),
              functionState, builder, sourceStructMT, sourceRefLE));
  auto resultInterfaceFatPtrLE =
      upcastThinPtr(
          globalState, functionState, referendStructs, builder, sourceStructMT,
          sourceStructReferendM,
          sourceStructWrapperPtrLE, targetInterfaceTypeM, targetInterfaceReferendM);
  return wrap(globalState->getRegion(targetInterfaceTypeM), targetInterfaceTypeM, resultInterfaceFatPtrLE);
}

Ref upcastWeak(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IWeakRefStructsSource* weakRefStructs,
    Reference* sourceStructMT,
    StructReferend* sourceStructReferendM,
    Ref sourceRefLE,
    Reference* targetInterfaceTypeM,
    InterfaceReferend* targetInterfaceReferendM) {
  auto sourceWeakStructFatPtrLE =
      weakRefStructs->makeWeakFatPtr(
          sourceStructMT,
          globalState->getRegion(sourceStructMT)->checkValidReference(FL(),
              functionState, builder, sourceStructMT, sourceRefLE));
  return globalState->getRegion(sourceStructMT)->upcastWeak(
      functionState,
      builder,
      sourceWeakStructFatPtrLE,
      sourceStructReferendM,
      sourceStructMT,
      targetInterfaceReferendM,
      targetInterfaceTypeM);
}

LoadResult regularloadElementFromKSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Reference* elementType,
    int arraySize,
    Mutability mutability,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    IReferendStructsSource* referendStructs) {
  LLVMValueRef arrayElementsPtrLE =
      getKnownSizeArrayContentsPtr(
          builder,
          referendStructs->makeWrapperPtr(
              FL(), functionState, builder, ksaRefMT,
              globalState->getRegion(ksaRefMT)
                  ->checkValidReference(FL(), functionState, builder, ksaRefMT, arrayRef)));
  return loadElementFromKSAInner(
      globalState, functionState, builder, ksaRefMT, ksaMT, arraySize, elementType, indexRef, arrayElementsPtrLE);
}

LoadResult resilientloadElementFromKSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    int size,
    Mutability mutability,
    Reference* elementType,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    IReferendStructsSource* referendStructs) {
  switch (ksaRefMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN: {
      LLVMValueRef arrayElementsPtrLE =
          getKnownSizeArrayContentsPtr(
              builder,
              referendStructs->makeWrapperPtr(
                  FL(), functionState, builder, ksaRefMT,
                  globalState->getRegion(ksaRefMT)
                      ->checkValidReference(FL(), functionState, builder, ksaRefMT, arrayRef)));
      return loadElementFromKSAInner(
          globalState, functionState, builder, ksaRefMT, ksaMT, size, elementType, indexRef, arrayElementsPtrLE);
    }
    case Ownership::BORROW: {
      LLVMValueRef arrayElementsPtrLE =
          getKnownSizeArrayContentsPtr(
              builder, globalState->getRegion(ksaRefMT)->lockWeakRef(FL(), functionState, builder, ksaRefMT, arrayRef, arrayKnownLive));
      return loadElementFromKSAInner(globalState, functionState, builder, ksaRefMT, ksaMT, size, elementType, indexRef, arrayElementsPtrLE);
    }
    case Ownership::WEAK:
      assert(false); // VIR never loads from a weak ref
    default:
      assert(false);
  }
}

void regularFillControlBlock(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    IReferendStructsSource* structs,
    LLVMBuilderRef builder,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName,
    WrcWeaks* wrcWeaks) {
  LLVMValueRef newControlBlockLE = LLVMGetUndef(structs->getControlBlock(referendM)->getStruct());

  newControlBlockLE =
      fillControlBlockCensusFields(
          from, globalState, functionState, structs, builder, referendM, newControlBlockLE, typeName);

  newControlBlockLE =
      insertStrongRc(globalState, builder, structs, referendM, newControlBlockLE);
  if (globalState->getReferendWeakability(referendM) == Weakability::WEAKABLE) {
    newControlBlockLE = wrcWeaks->fillWeakableControlBlock(functionState, builder, structs, referendM,
        newControlBlockLE);
  }

  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE.refLE);
}

void gmFillControlBlock(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    IReferendStructsSource* structs,
    LLVMBuilderRef builder,
    Referend* referendM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName,
    HybridGenerationalMemory* hgmWeaks) {

  LLVMValueRef newControlBlockLE = LLVMGetUndef(structs->getControlBlock(referendM)->getStruct());

  newControlBlockLE =
      fillControlBlockCensusFields(
          from, globalState, functionState, structs, builder, referendM, newControlBlockLE, typeName);
  newControlBlockLE = hgmWeaks->fillWeakableControlBlock(functionState, builder, referendM,
      newControlBlockLE);
  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE.refLE);
}

Ref getUnknownSizeArrayLengthStrong(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* usaRefMT,
    Ref arrayRef) {
  auto wrapperPtrLE =
      referendStructs->makeWrapperPtr(
          FL(), functionState, builder, usaRefMT,
          globalState->getRegion(usaRefMT)->checkValidReference(
              FL(), functionState, builder, usaRefMT, arrayRef));
  return ::getUnknownSizeArrayLength(globalState, functionState, builder, wrapperPtrLE);
}

LoadResult regularLoadStrongMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {

  auto wrapperPtrLE =
      referendStructs->makeWrapperPtr(FL(), functionState, builder, structRefMT,
          globalState->getRegion(structRefMT)
              ->checkValidReference(FL(), functionState, builder, structRefMT, structRef));
  auto innerStructPtrLE = referendStructs->getStructContentsPtr(builder,
      structRefMT->referend, wrapperPtrLE);

  auto memberLE =
      loadInnerInnerStructMember(
          globalState, builder, innerStructPtrLE, memberIndex, expectedMemberType,
          memberName);
  return memberLE;
}

std::tuple<LLVMValueRef, LLVMValueRef> explodeStrongInterfaceRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* virtualParamMT,
    Ref virtualArgRef) {
  auto virtualArgLE =
      globalState->getRegion(virtualParamMT)->checkValidReference(
          FL(), functionState, builder, virtualParamMT, virtualArgRef);
  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef newVirtualArgLE = nullptr;
  auto virtualArgInterfaceFatPtrLE =
      referendStructs->makeInterfaceFatPtr(
          FL(), functionState, builder, virtualParamMT, virtualArgLE);
  itablePtrLE = getItablePtrFromInterfacePtr(globalState, functionState, builder,
      virtualParamMT, virtualArgInterfaceFatPtrLE);
  buildFlare(FL(), globalState, functionState, builder);
  auto objVoidPtrLE =
      referendStructs->getVoidPtrFromInterfacePtr(
          functionState, builder, virtualParamMT, virtualArgInterfaceFatPtrLE);
  newVirtualArgLE = objVoidPtrLE;

  buildFlare(FL(), globalState, functionState, builder, "itablePtrLE ", ptrToIntLE(globalState, builder, itablePtrLE));

  return std::make_tuple(itablePtrLE, newVirtualArgLE);
}

std::tuple<LLVMValueRef, LLVMValueRef> explodeWeakInterfaceRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    FatWeaks* fatWeaks,
    IWeakRefStructsSource* weakRefStructs,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    std::function<WeakFatPtrLE(WeakFatPtrLE weakInterfaceFatPtrLE)> weakInterfaceRefToWeakStructRef) {
  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef newVirtualArgLE = nullptr;
  auto virtualArgLE =
      globalState->getRegion(virtualParamMT)
          ->checkValidReference(FL(), functionState, builder, virtualParamMT, virtualArgRef);
  auto weakFatPtrLE = weakRefStructs->makeWeakFatPtr(virtualParamMT, virtualArgLE);
  // Disassemble the weak interface ref.
  auto interfaceRefLE =
      referendStructs->makeInterfaceFatPtrWithoutChecking(
          FL(), functionState, builder, virtualParamMT,
          fatWeaks->getInnerRefFromWeakRef(
              functionState, builder, virtualParamMT, weakFatPtrLE));
  itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);
  // Now, reassemble a weak void* ref to the struct.
  auto weakVoidStructRefLE = weakInterfaceRefToWeakStructRef(weakFatPtrLE);
  newVirtualArgLE = weakVoidStructRefLE.refLE;
  return std::make_tuple(itablePtrLE, newVirtualArgLE);
}

Ref regularWeakAlias(
    GlobalState* globalState,
    FunctionState* functionState,
    IReferendStructsSource* referendStructs,
    WrcWeaks* wrcWeaks,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef) {
  if (auto structReferendM = dynamic_cast<StructReferend*>(sourceRefMT->referend)) {
    auto objPtrLE =
        referendStructs->makeWrapperPtr(
            FL(), functionState, builder, sourceRefMT,
            globalState->getRegion(sourceRefMT)
                ->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef));
    return wrap(
        globalState->getRegion(targetRefMT),
        targetRefMT,
        wrcWeaks->assembleStructWeakRef(
            functionState, builder,
            sourceRefMT, targetRefMT, structReferendM, objPtrLE));
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(sourceRefMT->referend)) {
    auto objPtrLE =
        referendStructs->makeInterfaceFatPtr(
            FL(), functionState, builder, sourceRefMT,
            globalState->getRegion(sourceRefMT)
                ->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef));
    return wrap(
        globalState->getRegion(targetRefMT),
        targetRefMT,
        wrcWeaks->assembleInterfaceWeakRef(
            functionState, builder,
            sourceRefMT, targetRefMT, interfaceReferend, objPtrLE));
  } else assert(false);
}

Ref regularInnerLockWeak(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool thenResultIsNever,
    bool elseResultIsNever,
    Reference* resultOptTypeM,
    Reference* constraintRefM,
    Reference* sourceWeakRefMT,
    Ref sourceWeakRefLE,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse,
    Ref isAliveLE,
    LLVMTypeRef resultOptTypeL,
    IWeakRefStructsSource* weakRefStructsSource,
    FatWeaks* fatWeaks) {
  return buildIfElse(
      globalState, functionState, builder, isAliveLE,
      resultOptTypeL,
      resultOptTypeM,
      resultOptTypeM,
      [globalState, functionState, fatWeaks, weakRefStructsSource, constraintRefM, sourceWeakRefLE, sourceWeakRefMT, buildThen](LLVMBuilderRef thenBuilder) {
        auto weakFatPtrLE =
            weakRefStructsSource->makeWeakFatPtr(
                sourceWeakRefMT,
                globalState->getRegion(sourceWeakRefMT)
                    ->checkValidReference(FL(), functionState, thenBuilder, sourceWeakRefMT, sourceWeakRefLE));
        auto constraintRefLE =
            fatWeaks->getInnerRefFromWeakRef(
                functionState,
                thenBuilder,
                sourceWeakRefMT,
                weakFatPtrLE);
        auto constraintRef =
            wrap(globalState->getRegion(constraintRefM), constraintRefM, constraintRefLE);
        return buildThen(thenBuilder, constraintRef);
      },
      buildElse);
}

void storeMemberStrong(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newValueLE) {
  LLVMValueRef innerStructPtrLE = nullptr;
  auto wrapperPtrLE =
      referendStructs->makeWrapperPtr(
          FL(), functionState, builder, structRefMT,
          globalState->getRegion(structRefMT)->checkValidReference(
              FL(), functionState, builder, structRefMT, structRef));
  innerStructPtrLE = referendStructs->getStructContentsPtr(builder, structRefMT->referend, wrapperPtrLE);
  storeInnerInnerStructMember(builder, innerStructPtrLE, memberIndex, memberName, newValueLE);
}

void storeMemberWeak(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newValueLE) {
  LLVMValueRef innerStructPtrLE = nullptr;
  auto wrapperPtrLE =
      globalState->getRegion(structRefMT)->lockWeakRef(
          FL(), functionState, builder, structRefMT, structRef, structKnownLive);
  innerStructPtrLE = referendStructs->getStructContentsPtr(builder, structRefMT->referend, wrapperPtrLE);
  storeInnerInnerStructMember(builder, innerStructPtrLE, memberIndex, memberName, newValueLE);
}

LLVMValueRef getInterfaceMethodFunctionPtrFromItable(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    int indexInEdge) {
  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef newVirtualArgLE = nullptr;
  std::tie(itablePtrLE, newVirtualArgLE) =
      globalState->getRegion(virtualParamMT)
          ->explodeInterfaceRef(
              functionState, builder, virtualParamMT, virtualArgRef);
  buildFlare(FL(), globalState, functionState, builder);

  auto interfaceMT = dynamic_cast<InterfaceReferend*>(virtualParamMT->referend);
  assert(interfaceMT);
//  int indexInEdge = 0;
//  InterfaceMethod* method = nullptr;
//  std::tie(indexInEdge, method) = globalState->getInterfaceMethod(interfaceMT, prototype);

  assert(LLVMGetTypeKind(LLVMTypeOf(itablePtrLE)) == LLVMPointerTypeKind);
  buildFlare(FL(), globalState, functionState, builder, "index in edge: ", indexInEdge);
  auto funcPtrPtrLE = LLVMBuildStructGEP(builder, itablePtrLE, indexInEdge, "methodPtrPtr");

  auto resultLE = LLVMBuildLoad(builder, funcPtrPtrLE, "methodPtr");
  buildFlare(FL(), globalState, functionState, builder, "method ptr: ", ptrToIntLE(globalState, builder, resultLE));
  return resultLE;
}


void initializeElementInUSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    IReferendStructsSource* referendStructs,
    UnknownSizeArrayT* usaMT,
    Reference* usaRefMT,
    Ref usaRef,
    Ref indexRef,
    Ref elementRef) {
  auto usaDef = globalState->program->getUnknownSizeArray(usaMT->name);
  auto arrayWrapperPtrLE =
      referendStructs->makeWrapperPtr(
          FL(), functionState, builder, usaRefMT,
          globalState->getRegion(usaRefMT)->checkValidReference(FL(), functionState, builder, usaRefMT, usaRef));
  auto sizeRef = ::getUnknownSizeArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
  auto arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);
  ::initializeElement(
      globalState, functionState, builder, usaRefMT->location,
      usaDef->rawArray->elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
}

Ref normalLocalLoad(GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  auto region = globalState->getRegion(local->type);
  auto sourceLE = LLVMBuildLoad(builder, localAddr, local->id->maybeName.c_str());
  auto sourceRef = wrap(region, local->type, sourceLE);
  region->checkValidReference(FL(), functionState, builder, local->type, sourceRef);
  return sourceRef;
}
