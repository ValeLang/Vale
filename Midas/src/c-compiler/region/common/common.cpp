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

constexpr int INTERFACE_REF_MEMBER_INDEX_FOR_OBJ_PTR = 0;
constexpr int INTERFACE_REF_MEMBER_INDEX_FOR_ITABLE_PTR = 1;

LLVMValueRef upcastThinPtr(
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* kindStructsSource,
    LLVMBuilderRef builder,

    Reference* sourceStructTypeM,
    StructKind* sourceStructKindM,
    WrapperPtrLE sourceRefLE,

    Reference* targetInterfaceTypeM,
    InterfaceKind* targetInterfaceKindM) {
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
      kindStructsSource->getConcreteControlBlockPtr(
          FL(), functionState, builder, sourceStructTypeM, sourceRefLE);
  auto interfaceRefLE =
      makeInterfaceRefStruct(
          globalState, functionState, builder, kindStructsSource, sourceStructKindM, targetInterfaceKindM,
          controlBlockPtrLE);
  return interfaceRefLE;
}

LLVMTypeRef translateReferenceSimple(GlobalState* globalState, KindStructs* structs, Kind* kind) {
  if (auto ssaMT = dynamic_cast<StaticSizedArrayT *>(kind)) {
    auto staticSizedArrayCountedStructLT =
        structs->getStaticSizedArrayWrapperStruct(ssaMT);
    return LLVMPointerType(staticSizedArrayCountedStructLT, 0);
  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT *>(kind)) {
    auto runtimeSizedArrayCountedStructLT =
        structs->getRuntimeSizedArrayWrapperStruct(rsaMT);
    return LLVMPointerType(runtimeSizedArrayCountedStructLT, 0);
  } else if (auto structKind = dynamic_cast<StructKind *>(kind)) {
    auto countedStructL = structs->getStructWrapperStruct(structKind);
    return LLVMPointerType(countedStructL, 0);
  } else if (auto interfaceKind = dynamic_cast<InterfaceKind *>(kind)) {
    auto interfaceRefStructL = structs->getInterfaceRefStruct(interfaceKind);
    return interfaceRefStructL;
  } else {
    std::cerr << "Unimplemented type: " << typeid(*kind).name() << std::endl;
    assert(false);
    return nullptr;
  }
}

LLVMTypeRef translateWeakReference(GlobalState* globalState, KindStructs* weakRefStructs, Kind* kind) {
  if (auto ssaMT = dynamic_cast<StaticSizedArrayT *>(kind)) {
    return weakRefStructs->getStaticSizedArrayWeakRefStruct(ssaMT);
  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT *>(kind)) {
    return weakRefStructs->getRuntimeSizedArrayWeakRefStruct(rsaMT);
  } else if (auto structKind = dynamic_cast<StructKind *>(kind)) {
    return weakRefStructs->getStructWeakRefStruct(structKind);
  } else if (auto interfaceKind = dynamic_cast<InterfaceKind *>(kind)) {
    return weakRefStructs->getInterfaceWeakRefStruct(interfaceKind);
  } else {
    assert(false);
  }
}

LoadResult loadInnerInnerStructMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef innerStructPtrLE,
    int memberIndex,
    Reference* expectedType,
    std::string memberName) {
  assert(LLVMGetTypeKind(LLVMTypeOf(innerStructPtrLE)) == LLVMPointerTypeKind);

  auto ptrToMemberLE =
      LLVMBuildStructGEP(builder, innerStructPtrLE, memberIndex, memberName.c_str());
  buildFlare(FL(), globalState, functionState, builder, "Ptr to member: ", ptrToIntLE(globalState, builder, ptrToMemberLE));
  auto result =
      LLVMBuildLoad(
          builder,
          ptrToMemberLE,
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
    KindStructs* structs,
    LLVMBuilderRef builder,
    Kind* kindM,
    LLVMValueRef newControlBlockLE,
    const std::string& typeName) {
  if (globalState->opt->census) {
    auto objIdLE = adjustCounter(globalState, builder, globalState->metalCache->i64, globalState->objIdCounter, 1);
    newControlBlockLE =
        LLVMBuildInsertValue(
            builder,
            newControlBlockLE,
            objIdLE,
            structs->getControlBlock(kindM)->getMemberIndex(ControlBlockMember::CENSUS_OBJ_ID),
            "strControlBlockWithObjId");
    newControlBlockLE =
        LLVMBuildInsertValue(
            builder,
            newControlBlockLE,
            globalState->getOrMakeStringConstant(typeName),
            structs->getControlBlock(kindM)->getMemberIndex(ControlBlockMember::CENSUS_TYPE_STR),
            "strControlBlockWithTypeStr");
    buildFlare(from, globalState, functionState, builder, "Allocating ", typeName, " ", objIdLE);
  }
  return newControlBlockLE;
}

LLVMValueRef insertStrongRc(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    Kind* kindM,
    LLVMValueRef newControlBlockLE) {
  return LLVMBuildInsertValue(
      builder,
      newControlBlockLE,
      // Start RC at 1, see SRCAZ.
      LLVMConstInt(LLVMInt32TypeInContext(globalState->context), 1, false),
      structs->getControlBlock(kindM)->getMemberIndex(ControlBlockMember::STRONG_RC_32B),
      "controlBlockWithRc");
}

LoadResult loadElementFromSSAInner(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    int size,
    Reference* elementType,
    Ref indexRef,
    LLVMValueRef arrayElementsPtrLE) {
  auto sizeRef =
      wrap(
          globalState->getRegion(globalState->metalCache->i32Ref),
          globalState->metalCache->i32Ref,
          LLVMConstInt(LLVMInt32TypeInContext(globalState->context), size, false));
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
    KindStructs* structs,
    StructKind* sourceStructKindM,
    InterfaceKind* targetInterfaceKindM,
    ControlBlockPtrLE controlBlockPtrLE) {
  auto itablePtrLE =
      globalState->getInterfaceTablePtr(
          globalState->program->getStruct(sourceStructKindM)
              ->getEdgeForInterface(targetInterfaceKindM));
  return makeInterfaceRefStruct(
      globalState, functionState, builder, structs, targetInterfaceKindM, controlBlockPtrLE.refLE, itablePtrLE);
}

LLVMValueRef makeInterfaceRefStruct(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    InterfaceKind* targetInterfaceKindM,
    LLVMValueRef objControlBlockPtrLE,
    LLVMValueRef itablePtrLE) {

  auto interfaceRefLT = structs->getInterfaceRefStruct(targetInterfaceKindM);

  auto interfaceRefLE = LLVMGetUndef(interfaceRefLT);
  interfaceRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceRefLE,
          objControlBlockPtrLE,
          INTERFACE_REF_MEMBER_INDEX_FOR_OBJ_PTR,
          "interfaceRefWithOnlyObj");
  interfaceRefLE =
      LLVMBuildInsertValue(
          builder,
          interfaceRefLE,
          itablePtrLE,
          INTERFACE_REF_MEMBER_INDEX_FOR_ITABLE_PTR,
          "interfaceRef");

  return interfaceRefLE;
}


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

void callFree(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef ptrLE) {
  auto concreteAsCharPtrLE =
      LLVMBuildBitCast(
          builder,
          ptrLE,
          LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0),
          "concreteCharPtrForFree");
  LLVMBuildCall(builder, globalState->externs->free, &concreteAsCharPtrLE, 1, "");
}

void innerDeallocateYonder(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* kindStructsSource,
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
    if (dynamic_cast<InterfaceKind*>(refMT->kind) == nullptr) {
      buildFlare(FL(), globalState, functionState, builder,
          "Deallocating object &", ptrToIntLE(globalState, builder, ptrLE), " obj id ", objIdLE, "\n");
    }
  }

  auto controlBlockPtrLE = kindStructsSource->getControlBlockPtr(from, functionState, builder,
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
    adjustCounter(globalState, builder, globalState->metalCache->i64, globalState->liveHeapObjCounter, -1);
  }
}

void innerDeallocate(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* kindStrutsSource,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  if (refMT->ownership == Ownership::SHARE) {
    if (refMT->location == Location::INLINE) {
      // Do nothing, it's inline!
    } else {
      return innerDeallocateYonder(from, globalState, functionState, kindStrutsSource, builder, refMT, ref);
    }
  } else {
    if (refMT->location == Location::INLINE) {
      assert(false); // implement
    } else {
      return innerDeallocateYonder(from, globalState, functionState, kindStrutsSource, builder, refMT, ref);
    }
  }
}

void fillStaticSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Ref ssaRef,
    const std::vector<Ref>& elementRefs) {

  for (int i = 0; i < elementRefs.size(); i++) {
    globalState->getRegion(ssaRefMT)->initializeElementInSSA(
        functionState, builder, ssaRefMT, ssaMT, ssaRef, true, globalState->constI32(i), elementRefs[i]);
  }
}

void fillRuntimeSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Reference* elementType,
    Reference* generatorType,
    Prototype* generatorMethod,
    Ref generatorLE,
    Ref sizeLE,
    Ref rsaRef) {

  intRangeLoop(
      globalState, functionState, builder, sizeLE,
      [globalState, functionState, rsaRefMT, rsaMT, generatorMethod, generatorType, rsaRef, generatorLE](
          Ref indexRef, LLVMBuilderRef bodyBuilder) {
        globalState->getRegion(generatorType)->alias(
            AFL("ConstructRSA generate iteration"),
            functionState, bodyBuilder, generatorType, generatorLE);
        std::vector<Ref> argExprsLE = { generatorLE, indexRef };

        auto elementRef =
            buildCall(
                globalState, functionState, bodyBuilder, generatorMethod, argExprsLE);
        globalState->getRegion(rsaMT)->pushRuntimeSizedArrayNoBoundsCheck(
            functionState, bodyBuilder, rsaRefMT, rsaMT, rsaRef, true, indexRef, elementRef);
      });
}

void fillStaticSizedArrayFromCallable(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Reference* elementType,
    Reference* generatorType,
    Prototype* generatorMethod,
    Ref generatorLE,
    Ref sizeLE,
    Ref ssaRef) {

  intRangeLoop(
      globalState, functionState, builder, sizeLE,
      [globalState, functionState, ssaRefMT, ssaMT, generatorMethod, generatorType, ssaRef, generatorLE](
          Ref indexRef, LLVMBuilderRef bodyBuilder) {
        globalState->getRegion(generatorType)->alias(
            AFL("ConstructSSA generate iteration"),
            functionState, bodyBuilder, generatorType, generatorLE);
        std::vector<Ref> argExprsLE = { generatorLE, indexRef };

        auto elementRef =
            buildCall(
                globalState, functionState, bodyBuilder, generatorMethod, argExprsLE);
        globalState->getRegion(ssaMT)->initializeElementInSSA(
            functionState, bodyBuilder, ssaRefMT, ssaMT, ssaRef, true, indexRef, elementRef);
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
  assert(LLVMTypeOf(sizeLE) == LLVMInt64TypeInContext(globalState->context));
  return LLVMBuildCall(builder, globalState->externs->malloc, &sizeLE, 1, "");
}

WrapperPtrLE mallocStr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lenI32LE,
    LLVMValueRef sourceCharsPtrLE,
    KindStructs* kindStructs,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock) {
  auto lenI64LE = LLVMBuildZExt(builder, lenI32LE, LLVMInt64TypeInContext(globalState->context), "lenAsI64");
  // The +1 is for the null terminator at the end, for C compatibility.
  auto sizeBytesLE =
      LLVMBuildAdd(
          builder,
          lenI64LE,
          LLVMBuildAdd(
              builder,
              constI64LE(globalState, 1),
              constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, kindStructs->getStringWrapperStruct())),
              "lenPlus1"),
          "strMallocSizeBytes");

  auto destCharPtrLE =callMalloc(globalState, builder, sizeBytesLE);

  if (globalState->opt->census) {
    adjustCounter(globalState, builder, globalState->metalCache->i64, globalState->liveHeapObjCounter, 1);

    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildBitCast(
            builder, destCharPtrLE, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "");
    LLVMBuildCall(builder, globalState->externs->censusAdd, &resultAsVoidPtrLE, 1, "");
  }

  auto newStrWrapperPtrLE =
      kindStructs->makeWrapperPtr(
          FL(), functionState, builder, globalState->metalCache->strRef,
          LLVMBuildBitCast(
              builder,
              destCharPtrLE,
              LLVMPointerType(kindStructs->getStringWrapperStruct(), 0),
              "newStrWrapperPtr"));

  fillControlBlock(
      builder,
      kindStructs->getConcreteControlBlockPtr(FL(), functionState, builder, globalState->metalCache->strRef, newStrWrapperPtrLE));
  assert(LLVMTypeOf(lenI32LE) == LLVMInt32TypeInContext(globalState->context));
  LLVMBuildStore(
      builder,
      lenI32LE,
      getLenPtrFromStrWrapperPtr(builder, newStrWrapperPtrLE));

  // Set the null terminating character to the 0th spot and the end spot, just to guard against bugs
  auto charsBeginPtr = getCharsPtrFromWrapperPtr(globalState, builder, newStrWrapperPtrLE);


  std::vector<LLVMValueRef> strncpyArgsLE = { charsBeginPtr, sourceCharsPtrLE, lenI64LE };
  LLVMBuildCall(builder, globalState->externs->strncpy, strncpyArgsLE.data(), strncpyArgsLE.size(), "");

  auto charsEndPtr = LLVMBuildGEP(builder, charsBeginPtr, &lenI32LE, 1, "charsEndPtr");
  LLVMBuildStore(builder, constI8LE(globalState, 0), charsEndPtr);

  // The caller still needs to initialize the actual chars inside!

  return newStrWrapperPtrLE;
}

LLVMValueRef mallocKnownSize(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Location location,
    LLVMTypeRef kindLT) {
  if (globalState->opt->census) {
    adjustCounter(globalState, builder, globalState->metalCache->i64, globalState->liveHeapObjCounter, 1);
  }

  LLVMValueRef resultPtrLE = nullptr;
  if (location == Location::INLINE) {
    resultPtrLE = makeMidasLocal(functionState, builder, kindLT, "newstruct", LLVMGetUndef(kindLT));
  } else if (location == Location::YONDER) {
    size_t sizeBytes = LLVMABISizeOfType(globalState->dataLayout, kindLT);
    LLVMValueRef sizeLE = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), sizeBytes, false);

    auto newStructLE = callMalloc(globalState, builder, sizeLE);

    resultPtrLE =
        LLVMBuildBitCast(
            builder, newStructLE, LLVMPointerType(kindLT, 0), "newstruct");
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
    KindStructs* kindStructsSource,
    LLVMBuilderRef builder,
    LLVMTypeRef structL,
    Reference* structTypeM,
    StructDefinition* structM,
    Weakability effectiveWeakability,
    std::vector<Ref> membersLE,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock) {

  auto ptrLE = mallocKnownSize(globalState, functionState, builder, structTypeM->location, structL);

  WrapperPtrLE newStructWrapperPtrLE =
      kindStructsSource->makeWrapperPtr(
          FL(), functionState, builder, structTypeM,
          ptrLE);
//  globalState->getRegion(refHere)->fillControlBlock(
//      from,
//      functionState, builder,
//      structTypeM->kind,
//      structM->mutability,
//      kindStructsSource->getConcreteControlBlockPtr(from, functionState, builder, structTypeM, newStructWrapperPtrLE), structM->name->name);
  fillControlBlock(
      builder,
      kindStructsSource->getConcreteControlBlockPtr(
          FL(), functionState, builder, structTypeM, newStructWrapperPtrLE));
  fillInnerStruct(
      globalState, functionState,
      builder, structM, membersLE,
      kindStructsSource->getStructContentsPtr(builder, structTypeM->kind, newStructWrapperPtrLE));

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
    KindStructs* kindStructs,
    const std::vector<Ref>& memberRefs,
    Weakability effectiveWeakability,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock) {
  auto structKind = dynamic_cast<StructKind*>(desiredReference->kind);
  auto structM = globalState->program->getStruct(structKind);

  switch (structM->mutability) {
    case Mutability::MUTABLE: {
      auto countedStructL = kindStructs->getStructWrapperStruct(structKind);
      return constructWrappedStruct(
          globalState, functionState, kindStructs, builder, countedStructL, desiredReference,
          structM, effectiveWeakability, memberRefs, fillControlBlock);
    }
    case Mutability::IMMUTABLE: {
      if (desiredReference->location == Location::INLINE) {
        auto valStructL =
            kindStructs->getStructInnerStruct(structKind);
        auto innerStructLE =
            constructInnerStruct(
                globalState, functionState, builder, structM, valStructL, memberRefs);
        return wrap(globalState->getRegion(desiredReference), desiredReference, innerStructLE);
      } else {
        auto countedStructL =
            kindStructs->getStructWrapperStruct(structKind);
        return constructWrappedStruct(
            globalState, functionState, kindStructs, builder, countedStructL, desiredReference,
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
    KindStructs* weakRefStructs,
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

LLVMValueRef mallocRuntimeSizedArray(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef rsaWrapperLT,
    LLVMTypeRef rsaElementLT,
    LLVMValueRef lenI32LE) {
  auto lenI64LE = LLVMBuildZExt(builder, lenI32LE, LLVMInt64TypeInContext(globalState->context), "lenI16");
  auto sizeBytesLE =
      LLVMBuildAdd(
          builder,
          constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, rsaWrapperLT)),
          LLVMBuildMul(
              builder,
              constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, LLVMArrayType(rsaElementLT, 1))),
              lenI64LE,
              ""),
          "rsaMallocSizeBytes");

  auto newWrapperPtrLE = callMalloc(globalState, builder, sizeBytesLE);

  if (globalState->opt->census) {
    adjustCounter(globalState, builder, globalState->metalCache->i64, globalState->liveHeapObjCounter, 1);
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
      LLVMPointerType(rsaWrapperLT, 0),
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


Ref getRuntimeSizedArrayLength(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE arrayRefLE) {
  auto lengthPtrLE = getRuntimeSizedArrayLengthPtr(globalState, builder, arrayRefLE);
  auto intLE = LLVMBuildLoad(builder, lengthPtrLE, "rsaLen");
  return wrap(globalState->getRegion(globalState->metalCache->i32Ref), globalState->metalCache->i32Ref, intLE);
}

Ref getRuntimeSizedArrayCapacity(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WrapperPtrLE arrayRefLE) {
  auto capacityPtrLE = getRuntimeSizedArrayCapacityPtr(globalState, builder, arrayRefLE);
  auto intLE = LLVMBuildLoad(builder, capacityPtrLE, "rsaCapacity");
  return wrap(globalState->getRegion(globalState->metalCache->i32Ref), globalState->metalCache->i32Ref, intLE);
}

ControlBlock makeAssistAndNaiveRCNonWeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutNonWeakableControlBlock"));
  controlBlock.addMember(ControlBlockMember::STRONG_RC_32B);
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
  controlBlock.addMember(ControlBlockMember::STRONG_RC_32B);
  // This is where we put the size in the current generational heap, we can use it for something
  // else until we get rid of that.
  controlBlock.addMember(ControlBlockMember::UNUSED_32B);
  if (globalState->opt->census) {
    controlBlock.addMember(ControlBlockMember::CENSUS_TYPE_STR);
    controlBlock.addMember(ControlBlockMember::CENSUS_OBJ_ID);
  }
  controlBlock.addMember(ControlBlockMember::WRCI_32B);
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
  controlBlock.addMember(ControlBlockMember::WRCI_32B);
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
  controlBlock.addMember(ControlBlockMember::WRCI_32B);
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
    KindStructs* weakRefStructs) {
  return buildIfElse(
      globalState, functionState, builder, isAliveLE,
      resultOptTypeL,
      resultOptTypeM,
      resultOptTypeM,
      [globalState, functionState, constraintRefM, weakRefStructs, sourceWeakRefLE, sourceWeakRefMT, buildThen](LLVMBuilderRef thenBuilder) {
        // TODO extract more of this common code out?
        // The incoming "constraint" ref is actually already a weak ref, so just return it
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


Ref interfaceRefIsForEdge(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceInterfaceRefMT,
    Ref sourceInterfaceRef,
    StructKind *targetStructKind,
    InterfaceKind *sourceInterfaceKind) {

  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef possibilityPtrLE = nullptr;
  std::tie(itablePtrLE, possibilityPtrLE) =
      globalState->getRegion(sourceInterfaceRefMT)
          ->explodeInterfaceRef(
              functionState, builder, sourceInterfaceRefMT, sourceInterfaceRef);

  auto targetStructDefM = globalState->program->getStruct(targetStructKind);
  auto targetEdgeM = targetStructDefM->getEdgeForInterface(sourceInterfaceKind);

  auto edgePtrLE = globalState->getInterfaceTablePtr(targetEdgeM);

  auto itablePtrDiffLE = LLVMBuildPtrDiff(builder, itablePtrLE, edgePtrLE, "ptrDiff");
  auto itablePtrsMatchLE = LLVMBuildICmp(builder, LLVMIntEQ, itablePtrDiffLE, constI64LE(globalState, 0), "ptrsMatch");
  auto itablePtrsMatchRef =
      wrap(globalState->getRegion(globalState->metalCache->boolRef),
          globalState->metalCache->boolRef,
          itablePtrsMatchLE);
  return itablePtrsMatchRef;
}

Ref regularDowncast(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    Reference* resultOptTypeM,
    Reference* sourceInterfaceRefMT,
    Ref sourceInterfaceRef,
    bool sourceRefKnownLive,
    Kind* targetKind,
    std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
    std::function<Ref(LLVMBuilderRef)> buildElse) {

  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef newVirtualArgLE = nullptr;
  std::tie(itablePtrLE, newVirtualArgLE) =
      globalState->getRegion(sourceInterfaceRefMT)
          ->explodeInterfaceRef(
              functionState, builder, sourceInterfaceRefMT, sourceInterfaceRef);
  buildFlare(FL(), globalState, functionState, builder);

  auto targetStructKind = dynamic_cast<StructKind*>(targetKind);
  assert(targetStructKind);

  auto sourceInterfaceKind = dynamic_cast<InterfaceKind*>(sourceInterfaceRefMT->kind);
  assert(sourceInterfaceKind);

  auto targetStructDefM = globalState->program->getStruct(targetStructKind);
  auto targetEdgeM = targetStructDefM->getEdgeForInterface(sourceInterfaceKind);

  auto edgePtrLE = globalState->getInterfaceTablePtr(targetEdgeM);

  auto itablePtrDiffLE = LLVMBuildPtrDiff(builder, itablePtrLE, edgePtrLE, "ptrDiff");
  auto itablePtrsMatchLE = LLVMBuildICmp(builder, LLVMIntEQ, itablePtrDiffLE, constI64LE(globalState, 0), "ptrsMatch");
  auto itablePtrsMatchRef =
      wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, itablePtrsMatchLE);

  auto resultOptTypeLE = globalState->getRegion(resultOptTypeM)->translateType(resultOptTypeM);

  return buildIfElse(
      globalState, functionState, builder, itablePtrsMatchRef,
      resultOptTypeLE,
      resultOptTypeM,
      resultOptTypeM,
      [globalState, sourceInterfaceRefMT, structs, targetKind, newVirtualArgLE, buildThen](
          LLVMBuilderRef thenBuilder) {
        auto resultStructRefMT =
            globalState->metalCache->getReference(
                sourceInterfaceRefMT->ownership, sourceInterfaceRefMT->location, targetKind);
        auto resultStructRefLE =
            structs->downcastPtr(thenBuilder, resultStructRefMT, newVirtualArgLE);
        auto resultStructRef = wrap(globalState->getRegion(resultStructRefMT), resultStructRefMT, resultStructRefLE);
        return buildThen(thenBuilder, resultStructRef);
      },
      buildElse);
}

Ref resilientDowncast(
    GlobalState* globalState,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    KindStructs* weakRefStructs,
    Reference *resultOptTypeM,
    Reference *sourceInterfaceRefMT,
    Ref &sourceInterfaceRef,
    Kind *targetKind,
    const std::function<Ref(LLVMBuilderRef, Ref)> &buildThen,
    std::function<Ref(LLVMBuilderRef)> &buildElse,
    StructKind *targetStructKind,
    InterfaceKind *sourceInterfaceKind) {
  auto itablePtrsMatchRef =
      interfaceRefIsForEdge(
          globalState,
          functionState,
          builder,
          sourceInterfaceRefMT,
          sourceInterfaceRef,
          targetStructKind,
          sourceInterfaceKind);

  auto resultOptTypeLE = globalState->getRegion(resultOptTypeM)->translateType(resultOptTypeM);

  return buildIfElse(
      globalState, functionState, builder, itablePtrsMatchRef,
      resultOptTypeLE,
      resultOptTypeM,
      resultOptTypeM,
      [globalState, weakRefStructs, structs, functionState, sourceInterfaceRef, sourceInterfaceRefMT, targetKind, targetStructKind, buildThen](
          LLVMBuilderRef thenBuilder) {
        auto possibilityPtrLE =
            std::get<1>(
                globalState->getRegion(sourceInterfaceRefMT)
                    ->explodeInterfaceRef(functionState, thenBuilder, sourceInterfaceRefMT, sourceInterfaceRef));
        buildFlare(FL(), globalState, functionState, thenBuilder);

        auto resultStructRefMT =
            globalState->metalCache->getReference(
                sourceInterfaceRefMT->ownership, sourceInterfaceRefMT->location, targetKind);
        switch (sourceInterfaceRefMT->ownership) {
          case Ownership::OWN: {
            auto resultStructRefLE = structs->downcastPtr(thenBuilder, resultStructRefMT, possibilityPtrLE);
            auto resultStructRef = wrap(globalState->getRegion(resultStructRefMT), resultStructRefMT, resultStructRefLE);
            return buildThen(thenBuilder, resultStructRef);
          }
          case Ownership::BORROW:
          case Ownership::WEAK: {
            auto resultStructRefLE =
                weakRefStructs->downcastWeakFatPtr(
                    thenBuilder, targetStructKind, resultStructRefMT, possibilityPtrLE);
            auto targetWeakRef = wrap(globalState->getRegion(resultStructRefMT), resultStructRefMT, resultStructRefLE);
            return buildThen(thenBuilder, targetWeakRef);
          }
          default:
            assert(false);
        }
      },
      buildElse);
}

ControlBlock makeResilientV1WeakableControlBlock(GlobalState* globalState) {
  ControlBlock controlBlock(globalState, LLVMStructCreateNamed(globalState->context, "mutControlBlock"));
  controlBlock.addMember(ControlBlockMember::LGTI_32B);
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
Ref constructStaticSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    StaticSizedArrayT* ssaMT,
    KindStructs* kindStructs,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock) {

  auto structLT =
      kindStructs->getStaticSizedArrayWrapperStruct(ssaMT);
  auto newStructLE =
      kindStructs->makeWrapperPtr(
          FL(), functionState, builder, refM,
          mallocKnownSize(globalState, functionState, builder, refM->location, structLT));
  fillControlBlock(
      builder,
      kindStructs->getConcreteControlBlockPtr(FL(), functionState, builder, refM, newStructLE));
  return wrap(globalState->getRegion(refM), refM, newStructLE.refLE);
}


void regularCheckValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* refM,
    LLVMValueRef refLE) {

  if (auto interfaceKindM = dynamic_cast<InterfaceKind *>(refM->kind)) {
    auto interfaceFatPtrLE = kindStructs->makeInterfaceFatPtr(checkerAFL, functionState, builder,
        refM, refLE);
    auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
    buildAssertCensusContains(checkerAFL, globalState, functionState, builder, itablePtrLE);
  }
  if (refM->location == Location::INLINE) {
    // Nothing to do, there's no control block or ref counts or anything.
  } else if (refM->location == Location::YONDER) {
    auto controlBlockPtrLE =
        kindStructs->getControlBlockPtr(checkerAFL, functionState, builder, refLE, refM);

    // We dont check ref count >0 because imm destructors receive with rc=0.
    //      auto rcLE = getRcFromControlBlockPtr(globalState, builder, controlBlockPtrLE);
    //      auto rcPositiveLE = LLVMBuildICmp(builder, LLVMIntSGT, rcLE, constI64LE(globalState, 0), "");
    //      buildAssert(checkerAFL, globalState, functionState, blockState, builder, rcPositiveLE, "Invalid RC!");

    buildAssertCensusContains(checkerAFL, globalState, functionState, builder,
        controlBlockPtrLE.refLE);
  } else
    assert(false);
}

LoadResult regularLoadElementFromRSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    bool capacityExists,
    Reference* rsaRefMT,
    RuntimeSizedArrayT* rsaMT,
    Mutability mutability,
    Reference* elementType,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  auto wrapperPtrLE =
      kindStructs->makeWrapperPtr(
          FL(), functionState, builder, rsaRefMT,
          globalState->getRegion(rsaRefMT)->checkValidReference(FL(), functionState, builder, rsaRefMT, arrayRef));
  auto sizeRef = ::getRuntimeSizedArrayLength(globalState, functionState, builder, wrapperPtrLE);
  auto arrayElementsPtrLE =
      getRuntimeSizedArrayContentsPtr(
          builder,
          capacityExists,
          kindStructs->makeWrapperPtr(
              FL(), functionState, builder, rsaRefMT,
              globalState->getRegion(rsaRefMT)->checkValidReference(FL(), functionState, builder, rsaRefMT, arrayRef)));
  buildFlare(FL(), globalState, functionState, builder);
  return loadElement(
      globalState, functionState, builder, arrayElementsPtrLE, elementType, sizeRef, indexRef);
}

LoadResult resilientLoadElementFromRSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    bool capacityExists,
    Reference* rsaRefMT,
    Mutability mutability,
    Reference* elementType,
    RuntimeSizedArrayT* rsaMT,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef) {
  switch (rsaRefMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN: {
      auto wrapperPtrLE =
          kindStructs->makeWrapperPtr(
              FL(), functionState, builder, rsaRefMT,
              globalState->getRegion(rsaRefMT)->checkValidReference(FL(), functionState, builder, rsaRefMT, arrayRef));
      auto sizeRef = ::getRuntimeSizedArrayLength(globalState, functionState, builder, wrapperPtrLE);
      auto arrayElementsPtrLE =
          getRuntimeSizedArrayContentsPtr(
              builder,
              capacityExists,
              kindStructs->makeWrapperPtr(
                  FL(), functionState, builder, rsaRefMT,
                  globalState->getRegion(rsaRefMT)->checkValidReference(FL(), functionState, builder, rsaRefMT,
                      arrayRef)));
      buildFlare(FL(), globalState, functionState, builder);
      return loadElement(
          globalState, functionState, builder, arrayElementsPtrLE, elementType, sizeRef, indexRef);
    }
    case Ownership::BORROW: {
      auto wrapperPtrLE =
          globalState->getRegion(rsaRefMT)->lockWeakRef(
              FL(), functionState, builder, rsaRefMT, arrayRef, arrayKnownLive);
      auto sizeRef = ::getRuntimeSizedArrayLength(globalState, functionState, builder, wrapperPtrLE);
      auto arrayElementsPtrLE =
          getRuntimeSizedArrayContentsPtr(
              builder,
              capacityExists,
              globalState->getRegion(rsaRefMT)->lockWeakRef(FL(), functionState, builder, rsaRefMT, arrayRef, arrayKnownLive));
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

Ref regularStoreElementInSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* ssaRefMT,
    Reference* elementType,
    int size,
    Ref arrayRef,
    Ref indexRef,
    Ref elementRef) {
  auto arrayElementsPtrLE =
      getStaticSizedArrayContentsPtr(
          builder,
          kindStructs->makeWrapperPtr(
              FL(), functionState, builder, ssaRefMT,
              globalState->getRegion(ssaRefMT)->checkValidReference(FL(), functionState, builder, ssaRefMT, arrayRef)));
  buildFlare(FL(), globalState, functionState, builder);
  return swapElement(
      globalState, functionState, builder, ssaRefMT->location,
      elementType, globalState->constI32(size), arrayElementsPtrLE, indexRef, elementRef);
}

void regularInitializeElementInSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* ssaRefMT,
    Reference* elementType,
    int size,
    Ref arrayRef,
    Ref indexRef,
    Ref elementRef) {
  auto arrayElementsPtrLE =
      getStaticSizedArrayContentsPtr(
          builder,
          kindStructs->makeWrapperPtr(
              FL(), functionState, builder, ssaRefMT,
              globalState->getRegion(ssaRefMT)->checkValidReference(FL(), functionState, builder, ssaRefMT, arrayRef)));
  buildFlare(FL(), globalState, functionState, builder);
  initializeElementWithoutIncrementSize(
      globalState, functionState, builder, ssaRefMT->location,
      elementType, globalState->constI32(size), arrayElementsPtrLE, indexRef, elementRef);
}

Ref constructRuntimeSizedArray(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* rsaMT,
    Reference* elementType,
    RuntimeSizedArrayT* runtimeSizedArrayT,
    LLVMTypeRef rsaWrapperPtrLT,
    LLVMTypeRef rsaElementLT,
    Ref initialSizeRef,
    Ref capacityRef,
    bool capacityExists,
    const std::string& typeName,
    std::function<void(LLVMBuilderRef builder, ControlBlockPtrLE controlBlockPtrLE)> fillControlBlock) {
  buildFlare(FL(), globalState, functionState, builder, "Constructing RSA!");

  auto capacityLE =
      globalState->getRegion(globalState->metalCache->i32Ref)->checkValidReference(FL(),
          functionState, builder, globalState->metalCache->i32Ref, capacityRef);
  auto ptrLE = mallocRuntimeSizedArray(globalState, builder, rsaWrapperPtrLT, rsaElementLT, capacityLE);
  auto rsaWrapperPtrLE =
      kindStructs->makeWrapperPtr(FL(), functionState, builder, rsaMT, ptrLE);
  fillControlBlock(
      builder,
      kindStructs->getConcreteControlBlockPtr(FL(), functionState, builder, rsaMT, rsaWrapperPtrLE));
  auto sizeLE =
      globalState->getRegion(globalState->metalCache->i32Ref)->checkValidReference(FL(),
          functionState, builder, globalState->metalCache->i32Ref, initialSizeRef);
  LLVMBuildStore(builder, sizeLE, getRuntimeSizedArrayLengthPtr(globalState, builder, rsaWrapperPtrLE));
  if (capacityExists) {
    LLVMBuildStore(builder, capacityLE, getRuntimeSizedArrayCapacityPtr(globalState, builder, rsaWrapperPtrLE));
  }
  auto refLE = wrap(globalState->getRegion(rsaMT), rsaMT, rsaWrapperPtrLE.refLE);

  if (globalState->opt->census) {
    auto objIdLE =
        globalState->getRegion(rsaMT)
            ->getCensusObjectId(FL(), functionState, builder, rsaMT, refLE);
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
    KindStructs* kindStructs,
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
        return regularLoadStrongMember(globalState, functionState, builder, kindStructs, structRefMT, structRef, memberIndex, expectedMemberType, targetType, memberName);
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
    KindStructs* kindStructs,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    Reference* expectedMemberType,
    const std::string& memberName) {
  auto wrapperPtrLE =
      globalState->getRegion(structRefMT)->lockWeakRef(
          FL(), functionState, builder, structRefMT, structRef, structKnownLive);
  auto innerStructPtrLE = kindStructs->getStructContentsPtr(builder,
      structRefMT->kind, wrapperPtrLE);
  return loadInnerInnerStructMember(
      globalState, functionState, builder, innerStructPtrLE, memberIndex, expectedMemberType, memberName);
}

Ref upcastStrong(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* sourceStructMT,
    StructKind* sourceStructKindM,
    Ref sourceRefLE,
    Reference* targetInterfaceTypeM,
    InterfaceKind* targetInterfaceKindM) {
  auto sourceStructWrapperPtrLE =
      kindStructs->makeWrapperPtr(
          FL(), functionState, builder, sourceStructMT,
          globalState->getRegion(sourceStructMT)->checkValidReference(FL(),
              functionState, builder, sourceStructMT, sourceRefLE));
  auto resultInterfaceFatPtrLE =
      upcastThinPtr(
          globalState, functionState, kindStructs, builder, sourceStructMT,
          sourceStructKindM,
          sourceStructWrapperPtrLE, targetInterfaceTypeM, targetInterfaceKindM);
  return wrap(globalState->getRegion(targetInterfaceTypeM), targetInterfaceTypeM, resultInterfaceFatPtrLE);
}

Ref upcastWeak(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* weakRefStructs,
    Reference* sourceStructMT,
    StructKind* sourceStructKindM,
    Ref sourceRefLE,
    Reference* targetInterfaceTypeM,
    InterfaceKind* targetInterfaceKindM) {
  auto sourceWeakStructFatPtrLE =
      weakRefStructs->makeWeakFatPtr(
          sourceStructMT,
          globalState->getRegion(sourceStructMT)->checkValidReference(FL(),
              functionState, builder, sourceStructMT, sourceRefLE));
  return globalState->getRegion(sourceStructMT)->upcastWeak(
      functionState,
      builder,
      sourceWeakStructFatPtrLE,
      sourceStructKindM,
      sourceStructMT,
      targetInterfaceKindM,
      targetInterfaceTypeM);
}

LoadResult regularloadElementFromSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    Reference* elementType,
    int arraySize,
    Mutability mutability,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    KindStructs* kindStructs) {
  LLVMValueRef arrayElementsPtrLE =
      getStaticSizedArrayContentsPtr(
          builder,
          kindStructs->makeWrapperPtr(
              FL(), functionState, builder, ssaRefMT,
              globalState->getRegion(ssaRefMT)
                  ->checkValidReference(FL(), functionState, builder, ssaRefMT, arrayRef)));
  return loadElementFromSSAInner(
      globalState, functionState, builder, ssaRefMT, ssaMT, arraySize, elementType, indexRef, arrayElementsPtrLE);
}

LoadResult resilientloadElementFromSSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ssaRefMT,
    StaticSizedArrayT* ssaMT,
    int size,
    Mutability mutability,
    Reference* elementType,
    Ref arrayRef,
    bool arrayKnownLive,
    Ref indexRef,
    KindStructs* kindStructs) {
  switch (ssaRefMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN: {
      LLVMValueRef arrayElementsPtrLE =
          getStaticSizedArrayContentsPtr(
              builder,
              kindStructs->makeWrapperPtr(
                  FL(), functionState, builder, ssaRefMT,
                  globalState->getRegion(ssaRefMT)
                      ->checkValidReference(FL(), functionState, builder, ssaRefMT, arrayRef)));
      return loadElementFromSSAInner(
          globalState, functionState, builder, ssaRefMT, ssaMT, size, elementType, indexRef, arrayElementsPtrLE);
    }
    case Ownership::BORROW: {
      LLVMValueRef arrayElementsPtrLE =
          getStaticSizedArrayContentsPtr(
              builder, globalState->getRegion(ssaRefMT)->lockWeakRef(FL(), functionState, builder, ssaRefMT, arrayRef, arrayKnownLive));
      return loadElementFromSSAInner(globalState, functionState, builder, ssaRefMT, ssaMT, size, elementType, indexRef, arrayElementsPtrLE);
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
    KindStructs* structs,
    LLVMBuilderRef builder,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName,
    WrcWeaks* wrcWeaks) {
  LLVMValueRef newControlBlockLE = LLVMGetUndef(structs->getControlBlock(kindM)->getStruct());

  newControlBlockLE =
      fillControlBlockCensusFields(
          from, globalState, functionState, structs, builder, kindM, newControlBlockLE, typeName);

  newControlBlockLE =
      insertStrongRc(globalState, builder, structs, kindM, newControlBlockLE);
  if (globalState->getKindWeakability(kindM) == Weakability::WEAKABLE) {
    newControlBlockLE = wrcWeaks->fillWeakableControlBlock(functionState, builder, structs, kindM,
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
    KindStructs* structs,
    LLVMBuilderRef builder,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtrLE,
    const std::string& typeName,
    HybridGenerationalMemory* hgmWeaks) {

  LLVMValueRef newControlBlockLE = LLVMGetUndef(structs->getControlBlock(kindM)->getStruct());

  newControlBlockLE =
      fillControlBlockCensusFields(
          from, globalState, functionState, structs, builder, kindM, newControlBlockLE, typeName);
  newControlBlockLE = hgmWeaks->fillWeakableControlBlock(functionState, builder, kindM,
      newControlBlockLE);
  LLVMBuildStore(
      builder,
      newControlBlockLE,
      controlBlockPtrLE.refLE);
}

Ref getRuntimeSizedArrayLengthStrong(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* rsaRefMT,
    Ref arrayRef) {
  auto wrapperPtrLE =
      kindStructs->makeWrapperPtr(
          FL(), functionState, builder, rsaRefMT,
          globalState->getRegion(rsaRefMT)->checkValidReference(
              FL(), functionState, builder, rsaRefMT, arrayRef));
  return ::getRuntimeSizedArrayLength(globalState, functionState, builder, wrapperPtrLE);
}

Ref getRuntimeSizedArrayCapacityStrong(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* rsaRefMT,
    Ref arrayRef) {
  auto wrapperPtrLE =
      kindStructs->makeWrapperPtr(
          FL(), functionState, builder, rsaRefMT,
          globalState->getRegion(rsaRefMT)->checkValidReference(
              FL(), functionState, builder, rsaRefMT, arrayRef));
  return ::getRuntimeSizedArrayCapacity(globalState, functionState, builder, wrapperPtrLE);
}

LoadResult regularLoadStrongMember(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* structRefMT,
    Ref structRef,
    int memberIndex,
    Reference* expectedMemberType,
    Reference* targetType,
    const std::string& memberName) {

  auto wrapperPtrLE =
      kindStructs->makeWrapperPtr(FL(), functionState, builder, structRefMT,
          globalState->getRegion(structRefMT)
              ->checkValidReference(FL(), functionState, builder, structRefMT, structRef));
  auto innerStructPtrLE = kindStructs->getStructContentsPtr(builder,
      structRefMT->kind, wrapperPtrLE);

  auto memberLE =
      loadInnerInnerStructMember(
          globalState, functionState, builder, innerStructPtrLE, memberIndex, expectedMemberType,
          memberName);
  return memberLE;
}

std::tuple<LLVMValueRef, LLVMValueRef> explodeStrongInterfaceRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* virtualParamMT,
    Ref virtualArgRef) {
  auto virtualArgLE =
      globalState->getRegion(virtualParamMT)->checkValidReference(
          FL(), functionState, builder, virtualParamMT, virtualArgRef);
  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef newVirtualArgLE = nullptr;
  auto virtualArgInterfaceFatPtrLE =
      kindStructs->makeInterfaceFatPtr(
          FL(), functionState, builder, virtualParamMT, virtualArgLE);
  itablePtrLE = getItablePtrFromInterfacePtr(globalState, functionState, builder,
      virtualParamMT, virtualArgInterfaceFatPtrLE);
  buildFlare(FL(), globalState, functionState, builder);
  auto objVoidPtrLE =
      kindStructs->getVoidPtrFromInterfacePtr(
          functionState, builder, virtualParamMT, virtualArgInterfaceFatPtrLE);
  newVirtualArgLE = objVoidPtrLE;

  //buildFlare(FL(), globalState, functionState, builder, "itablePtrLE ", ptrToIntLE(globalState, builder, itablePtrLE));

  return std::make_tuple(itablePtrLE, newVirtualArgLE);
}

std::tuple<LLVMValueRef, LLVMValueRef> explodeWeakInterfaceRef(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    FatWeaks* fatWeaks,
    KindStructs* weakRefStructs,
    Reference* virtualParamMT,
    Ref virtualArgRef,
    std::function<WeakFatPtrLE(WeakFatPtrLE weakInterfaceFatPtrLE)> weakInterfaceRefToWeakStructRef) {
  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef objPtrLE = nullptr;
  auto virtualArgLE =
      globalState->getRegion(virtualParamMT)
          ->checkValidReference(FL(), functionState, builder, virtualParamMT, virtualArgRef);
  auto weakFatPtrLE = weakRefStructs->makeWeakFatPtr(virtualParamMT, virtualArgLE);
  // Disassemble the weak interface ref.
  auto interfaceRefLE =
      kindStructs->makeInterfaceFatPtrWithoutChecking(
          FL(), functionState, builder, virtualParamMT,
          fatWeaks->getInnerRefFromWeakRef(
              functionState, builder, virtualParamMT, weakFatPtrLE));
  itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceRefLE);
  // Now, reassemble a weak void* ref to the struct.
  auto weakVoidStructRefLE = weakInterfaceRefToWeakStructRef(weakFatPtrLE);
  objPtrLE = weakVoidStructRefLE.refLE;
  return std::make_tuple(itablePtrLE, objPtrLE);
}

Ref regularWeakAlias(
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* kindStructs,
    WrcWeaks* wrcWeaks,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Reference* targetRefMT,
    Ref sourceRef) {
  if (auto structKindM = dynamic_cast<StructKind*>(sourceRefMT->kind)) {
    auto objPtrLE =
        kindStructs->makeWrapperPtr(
            FL(), functionState, builder, sourceRefMT,
            globalState->getRegion(sourceRefMT)
                ->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef));
    return wrap(
        globalState->getRegion(targetRefMT),
        targetRefMT,
        wrcWeaks->assembleStructWeakRef(
            functionState, builder,
            sourceRefMT, targetRefMT, structKindM, objPtrLE));
  } else if (auto interfaceKind = dynamic_cast<InterfaceKind*>(sourceRefMT->kind)) {
    auto objPtrLE =
        kindStructs->makeInterfaceFatPtr(
            FL(), functionState, builder, sourceRefMT,
            globalState->getRegion(sourceRefMT)
                ->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef));
    return wrap(
        globalState->getRegion(targetRefMT),
        targetRefMT,
        wrcWeaks->assembleInterfaceWeakRef(
            functionState, builder,
            sourceRefMT, targetRefMT, interfaceKind, objPtrLE));
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
    KindStructs* weakRefStructsSource,
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
    KindStructs* kindStructs,
    Reference* structRefMT,
    Ref structRef,
    bool structKnownLive,
    int memberIndex,
    const std::string& memberName,
    LLVMValueRef newValueLE) {
  LLVMValueRef innerStructPtrLE = nullptr;
  auto wrapperPtrLE =
      kindStructs->makeWrapperPtr(
          FL(), functionState, builder, structRefMT,
          globalState->getRegion(structRefMT)->checkValidReference(
              FL(), functionState, builder, structRefMT, structRef));
  innerStructPtrLE = kindStructs->getStructContentsPtr(builder, structRefMT->kind, wrapperPtrLE);
  storeInnerInnerStructMember(builder, innerStructPtrLE, memberIndex, memberName, newValueLE);
}

void storeMemberWeak(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
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
  innerStructPtrLE = kindStructs->getStructContentsPtr(builder, structRefMT->kind, wrapperPtrLE);
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

  auto interfaceMT = dynamic_cast<InterfaceKind*>(virtualParamMT->kind);
  assert(interfaceMT);
//  int indexInEdge = 0;
//  InterfaceMethod* method = nullptr;
//  std::tie(indexInEdge, method) = globalState->getInterfaceMethod(interfaceMT, prototype);

  assert(LLVMGetTypeKind(LLVMTypeOf(itablePtrLE)) == LLVMPointerTypeKind);
  //buildFlare(FL(), globalState, functionState, builder, "index in edge: ", indexInEdge);
  auto funcPtrPtrLE = LLVMBuildStructGEP(builder, itablePtrLE, indexInEdge, "methodPtrPtr");

  auto resultLE = LLVMBuildLoad(builder, funcPtrPtrLE, "methodPtr");
  //buildFlare(FL(), globalState, functionState, builder, "method ptr: ", ptrToIntLE(globalState, builder, resultLE));
  return resultLE;
}


void initializeElementInRSA(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    bool capacityExists,
    bool incrementSize,
    RuntimeSizedArrayT* rsaMT,
    Reference* rsaRefMT,
    WrapperPtrLE arrayWrapperPtrLE,
    Ref rsaRef,
    Ref indexRef,
    Ref elementRef) {
  auto rsaDef = globalState->program->getRuntimeSizedArray(rsaMT);
  auto arrayElementsPtrLE = getRuntimeSizedArrayContentsPtr(builder, capacityExists, arrayWrapperPtrLE);
  if (incrementSize) {
    auto sizePtrLE = ::getRuntimeSizedArrayLengthPtr(globalState, builder, arrayWrapperPtrLE);
    ::initializeElementAndIncrementSize(
        globalState, functionState, builder, rsaRefMT->location,
        rsaDef->elementType, sizePtrLE, arrayElementsPtrLE, indexRef, elementRef);
  } else {
    auto sizeRef = ::getRuntimeSizedArrayLength(globalState, functionState, builder, arrayWrapperPtrLE);
    ::initializeElementWithoutIncrementSize(
        globalState, functionState, builder, rsaRefMT->location,
        rsaDef->elementType, sizeRef, arrayElementsPtrLE, indexRef, elementRef);
  }
}

Ref normalLocalLoad(GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, Local* local, LLVMValueRef localAddr) {
  auto region = globalState->getRegion(local->type);
  auto sourceLE = LLVMBuildLoad(builder, localAddr, local->id->maybeName.c_str());
  auto sourceRef = wrap(region, local->type, sourceLE);
  region->checkValidReference(FL(), functionState, builder, local->type, sourceRef);
  return sourceRef;
}

Ref regularReceiveAndDecryptFamiliarReference(
    GlobalState* globalState,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference *sourceRefMT,
    LLVMValueRef sourceRefLE) {
  if (dynamic_cast<StructKind*>(sourceRefMT->kind) ||
      dynamic_cast<StaticSizedArrayT*>(sourceRefMT->kind) ||
      dynamic_cast<RuntimeSizedArrayT*>(sourceRefMT->kind)) {
    auto handleLT = globalState->getConcreteHandleStruct();
    assert(LLVMTypeOf(sourceRefLE) == handleLT);

    LLVMValueRef refRegionIdLE = nullptr, refObjPtrIntLE = nullptr, refGenIntLE = nullptr, refOffsetIntLE = nullptr;
    std::tie(refRegionIdLE, refObjPtrIntLE, refGenIntLE, refOffsetIntLE) =
        explodeConcreteHandle(globalState, builder, sourceRefLE);
    buildAssertIntEq(globalState, functionState, builder, refRegionIdLE, constI64LE(globalState, externHandleRegionId), "Invalid reference in extern boundary! (r)");
    buildAssertIntEq(globalState, functionState, builder, refGenIntLE, constI32LE(globalState, externHandleGen), "Invalid reference in extern boundary! (g)");
    buildAssertIntEq(globalState, functionState, builder, refOffsetIntLE, constI32LE(globalState, externHandleGenOffset), "Invalid reference in extern boundary! (o)");
    auto refLT = globalState->getRegion(sourceRefMT)->translateType(sourceRefMT);
    auto objPtrLE = LLVMBuildIntToPtr(builder, refObjPtrIntLE, refLT, "refA");
    auto ref = wrap(globalState->getRegion(sourceRefMT), sourceRefMT, objPtrLE);
    globalState->getRegion(sourceRefMT)->checkValidReference(FL(), functionState, builder, sourceRefMT, ref);

    // Alias when receiving from the outside world, see DEPAR.
    globalState->getRegion(sourceRefMT)
        ->alias(FL(), functionState, builder, sourceRefMT, ref);

    return ref;
  } else if (auto interfaceMT = dynamic_cast<InterfaceKind*>(sourceRefMT->kind)) {
    auto handleLT = globalState->getInterfaceHandleStruct();
    assert(LLVMTypeOf(sourceRefLE) == handleLT);

    LLVMValueRef refRegionIdLE = nullptr, refItablePtrIntLE = nullptr, refObjPtrIntLE = nullptr, refGenIntLE = nullptr, refOffsetIntLE = nullptr;
    std::tie(refRegionIdLE, refItablePtrIntLE, refObjPtrIntLE, refGenIntLE, refOffsetIntLE) =
        explodeInterfaceHandle(globalState, builder, sourceRefLE);
    buildAssertIntEq(globalState, functionState, builder, refRegionIdLE, constI64LE(globalState, externHandleRegionId), "Invalid reference in extern boundary! (r)");
    buildAssertIntEq(globalState, functionState, builder, refGenIntLE, constI32LE(globalState, externHandleGen), "Invalid reference in extern boundary! (g)");
    buildAssertIntEq(globalState, functionState, builder, refOffsetIntLE, constI32LE(globalState, externHandleGenOffset), "Invalid reference in extern boundary! (o)");

    auto itablePtrLT = LLVMPointerType(kindStructs->getInterfaceTableStruct(interfaceMT), 0);
    auto objPtrLT = LLVMPointerType(kindStructs->getControlBlock(interfaceMT)->getStruct(), 0);

    auto refLT = globalState->getRegion(sourceRefMT)->translateType(sourceRefMT);
    auto objPtrLE = LLVMBuildIntToPtr(builder, refObjPtrIntLE, objPtrLT, "refB");
    auto itablePtrLE = LLVMBuildIntToPtr(builder, refItablePtrIntLE, itablePtrLT, "refC");
    auto interfaceFatPtrRawLE = makeInterfaceRefStruct(globalState, functionState, builder, kindStructs, interfaceMT, objPtrLE, itablePtrLE);

    auto interfaceFatPtrLE = kindStructs->makeInterfaceFatPtr(FL(), functionState, builder, sourceRefMT, interfaceFatPtrRawLE);

    auto ref = wrap(globalState->getRegion(sourceRefMT), sourceRefMT, interfaceFatPtrLE);
    globalState->getRegion(sourceRefMT)->checkValidReference(FL(), functionState, builder, sourceRefMT, ref);

    // Alias when receiving from the outside world, see DEPAR.
    globalState->getRegion(sourceRefMT)
        ->alias(FL(), functionState, builder, sourceRefMT, ref);

    return ref;
  } else {
    assert(false);
  }
  assert(false);
}

LLVMValueRef regularEncryptAndSendFamiliarReference(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Reference* sourceRefMT,
    Ref sourceRef) {

  // Dealias when sending to the outside world, see DEPAR.
  globalState->getRegion(sourceRefMT)
      ->dealias(FL(), functionState, builder, sourceRefMT, sourceRef);

  if (dynamic_cast<StructKind*>(sourceRefMT->kind) ||
      dynamic_cast<StaticSizedArrayT*>(sourceRefMT->kind) ||
      dynamic_cast<RuntimeSizedArrayT*>(sourceRefMT->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceRefMT)->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef);
    auto objPtrIntLE = LLVMBuildPtrToInt(builder, sourceRefLE, LLVMInt64TypeInContext(globalState->context), "objPtrInt");

    auto handleLE =
        implodeConcreteHandle(
            globalState,
            builder,
            globalState->getConcreteHandleStruct(),
            constI64LE(globalState, externHandleRegionId),
            objPtrIntLE,
            constI32LE(globalState, externHandleGen),
            constI32LE(globalState, externHandleGenOffset));
    return handleLE;
  } else if (dynamic_cast<InterfaceKind*>(sourceRefMT->kind)) {
    globalState->getRegion(sourceRefMT)->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef);
    LLVMValueRef itablePtrLE = nullptr, objPtrLE = nullptr;
    std::tie(itablePtrLE, objPtrLE) = globalState->getRegion(sourceRefMT)->explodeInterfaceRef(functionState, builder, sourceRefMT, sourceRef);
    auto objPtrIntLE = LLVMBuildPtrToInt(builder, objPtrLE, LLVMInt64TypeInContext(globalState->context), "objPtrInt");
    auto itablePtrIntLE = LLVMBuildPtrToInt(builder, itablePtrLE, LLVMInt64TypeInContext(globalState->context), "itablePtrInt");
    auto handleLE =
        implodeInterfaceHandle(
            globalState,
            builder,
            globalState->getInterfaceHandleStruct(),
            constI64LE(globalState, externHandleRegionId),
            itablePtrIntLE,
            objPtrIntLE,
            constI32LE(globalState, externHandleGen),
            constI32LE(globalState, externHandleGenOffset));
    return handleLE;
  } else {
    assert(false);
  }
  assert(false);
}

Ref resilientReceiveAndDecryptFamiliarReference(
    GlobalState* globalState,
    FunctionState *functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    KindStructs* weakableKindStructs,
    HybridGenerationalMemory* hgm,
    Reference *sourceRefMT,
    LLVMValueRef sourceRefLE) {
  switch (sourceRefMT->ownership) {
    case Ownership::SHARE:
    case Ownership::OWN:
      return regularReceiveAndDecryptFamiliarReference(globalState, functionState, builder, kindStructs, sourceRefMT, sourceRefLE);
    case Ownership::BORROW:
    case Ownership::WEAK:
      if (auto kindStruct = dynamic_cast<StructKind*>(sourceRefMT->kind)) {
        auto handleLT = globalState->getConcreteHandleStruct();
        assert(LLVMTypeOf(sourceRefLE) == handleLT);

        LLVMValueRef refRegionIdLE = nullptr, refObjPtrIntLE = nullptr, refGenIntLE = nullptr, refOffsetIntLE = nullptr;
        std::tie(refRegionIdLE, refObjPtrIntLE, refGenIntLE, refOffsetIntLE) =
            explodeConcreteHandle(globalState, builder, sourceRefLE);
        // Remove this when we actually have regions
        buildAssertIntEq(globalState, functionState, builder, refRegionIdLE, constI64LE(globalState, externHandleRegionId), "Invalid reference in extern boundary! (r)");
        // Remove this when we have inl
        buildAssertIntEq(globalState, functionState, builder, refOffsetIntLE, constI32LE(globalState, externHandleGenOffset), "Invalid reference in extern boundary! (o)");

        auto wrapperStructPtrLT = LLVMPointerType(weakableKindStructs->getStructWrapperStruct(kindStruct), 0);

        auto wrapperPtrLE =
            weakableKindStructs->makeWrapperPtr(FL(), functionState, builder, sourceRefMT,
                LLVMBuildIntToPtr(builder, refObjPtrIntLE, wrapperStructPtrLT, "refD"));

        auto weakFatPtrLE = hgm->assembleStructWeakRef(functionState, builder, sourceRefMT, kindStruct, refGenIntLE, wrapperPtrLE);
        auto ref = wrap(globalState->getRegion(sourceRefMT), sourceRefMT, weakFatPtrLE);
        globalState->getRegion(sourceRefMT)->checkValidReference(FL(), functionState, builder, sourceRefMT, ref);

        // Alias when receiving from the outside world, see DEPAR.
        globalState->getRegion(sourceRefMT)
            ->alias(FL(), functionState, builder, sourceRefMT, ref);

        return ref;
      } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(sourceRefMT->kind)) {
        auto handleLT = globalState->getConcreteHandleStruct();
        assert(LLVMTypeOf(sourceRefLE) == handleLT);

        LLVMValueRef refRegionIdLE = nullptr, refObjPtrIntLE = nullptr, refGenIntLE = nullptr, refOffsetIntLE = nullptr;
        std::tie(refRegionIdLE, refObjPtrIntLE, refGenIntLE, refOffsetIntLE) =
            explodeConcreteHandle(globalState, builder, sourceRefLE);
        // Remove this when we actually have regions
        buildAssertIntEq(globalState, functionState, builder, refRegionIdLE, constI64LE(globalState, externHandleRegionId), "Invalid reference in extern boundary! (r)");
        // Remove this when we have inl
        buildAssertIntEq(globalState, functionState, builder, refOffsetIntLE, constI32LE(globalState, externHandleGenOffset), "Invalid reference in extern boundary! (o)");

        auto wrapperStructPtrLT = LLVMPointerType(weakableKindStructs->getRuntimeSizedArrayWrapperStruct(rsaMT), 0);

        auto wrapperPtrLE =
            weakableKindStructs->makeWrapperPtr(FL(), functionState, builder, sourceRefMT,
                LLVMBuildIntToPtr(builder, refObjPtrIntLE, wrapperStructPtrLT, "refD"));

        auto weakFatPtrLE = hgm->assembleRuntimeSizedArrayWeakRef(functionState, builder, sourceRefMT, rsaMT, refGenIntLE, wrapperPtrLE);
        auto ref = wrap(globalState->getRegion(sourceRefMT), sourceRefMT, weakFatPtrLE);
        globalState->getRegion(sourceRefMT)->checkValidReference(FL(), functionState, builder, sourceRefMT, ref);

        // Alias when receiving from the outside world, see DEPAR.
        globalState->getRegion(sourceRefMT)
            ->alias(FL(), functionState, builder, sourceRefMT, ref);

        return ref;
      } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(sourceRefMT->kind)) {
        auto handleLT = globalState->getConcreteHandleStruct();
        assert(LLVMTypeOf(sourceRefLE) == handleLT);

        LLVMValueRef refRegionIdLE = nullptr, refObjPtrIntLE = nullptr, refGenIntLE = nullptr, refOffsetIntLE = nullptr;
        std::tie(refRegionIdLE, refObjPtrIntLE, refGenIntLE, refOffsetIntLE) =
            explodeConcreteHandle(globalState, builder, sourceRefLE);
        // Remove this when we actually have regions
        buildAssertIntEq(globalState, functionState, builder, refRegionIdLE, constI64LE(globalState, externHandleRegionId), "Invalid reference in extern boundary! (r)");
        // Remove this when we have inl
        buildAssertIntEq(globalState, functionState, builder, refOffsetIntLE, constI32LE(globalState, externHandleGenOffset), "Invalid reference in extern boundary! (o)");

        auto wrapperStructPtrLT = LLVMPointerType(weakableKindStructs->getStaticSizedArrayWrapperStruct(ssaMT), 0);

        auto wrapperPtrLE =
            weakableKindStructs->makeWrapperPtr(FL(), functionState, builder, sourceRefMT,
                LLVMBuildIntToPtr(builder, refObjPtrIntLE, wrapperStructPtrLT, "refD"));

        auto weakFatPtrLE = hgm->assembleStaticSizedArrayWeakRef(functionState, builder, sourceRefMT, ssaMT, refGenIntLE, wrapperPtrLE);
        auto ref = wrap(globalState->getRegion(sourceRefMT), sourceRefMT, weakFatPtrLE);
        globalState->getRegion(sourceRefMT)->checkValidReference(FL(), functionState, builder, sourceRefMT, ref);

        // Alias when receiving from the outside world, see DEPAR.
        globalState->getRegion(sourceRefMT)
            ->alias(FL(), functionState, builder, sourceRefMT, ref);

        return ref;
      } else if (auto interfaceMT = dynamic_cast<InterfaceKind*>(sourceRefMT->kind)) {
        auto handleLT = globalState->getInterfaceHandleStruct();
        assert(LLVMTypeOf(sourceRefLE) == handleLT);

        LLVMValueRef refRegionIdLE = nullptr, refItablePtrIntLE = nullptr, refObjPtrIntLE = nullptr, refGenIntLE = nullptr, refOffsetIntLE = nullptr;
        std::tie(refRegionIdLE, refItablePtrIntLE, refObjPtrIntLE, refGenIntLE, refOffsetIntLE) =
            explodeInterfaceHandle(globalState, builder, sourceRefLE);
        // Remove this when we actually have regions
        buildAssertIntEq(globalState, functionState, builder, refRegionIdLE, constI64LE(globalState, externHandleRegionId), "Invalid reference in extern boundary! (r)");
        // Remove this when we have inl
        buildAssertIntEq(globalState, functionState, builder, refOffsetIntLE, constI32LE(globalState, externHandleGenOffset), "Invalid reference in extern boundary! (o)");

        auto itablePtrLT = LLVMPointerType(weakableKindStructs->getInterfaceTableStruct(interfaceMT), 0);
        auto objPtrLT = LLVMPointerType(weakableKindStructs->getControlBlock(interfaceMT)->getStruct(), 0);

        auto refLT = globalState->getRegion(sourceRefMT)->translateType(sourceRefMT);
        auto objPtrLE = LLVMBuildIntToPtr(builder, refObjPtrIntLE, objPtrLT, "refE");
        auto itablePtrLE = LLVMBuildIntToPtr(builder, refItablePtrIntLE, itablePtrLT, "refF");

        auto interfaceFatPtrRawLE = makeInterfaceRefStruct(globalState, functionState, builder, weakableKindStructs, interfaceMT, objPtrLE, itablePtrLE);
        auto interfaceFatPtrLE = weakableKindStructs->makeInterfaceFatPtr(FL(), functionState, builder, sourceRefMT, interfaceFatPtrRawLE);
        auto weakFatPtrLE = hgm->assembleInterfaceWeakRef(functionState, builder, sourceRefMT, interfaceMT, refGenIntLE, interfaceFatPtrLE);

        auto ref = wrap(globalState->getRegion(sourceRefMT), sourceRefMT, weakFatPtrLE);
        globalState->getRegion(sourceRefMT)->checkValidReference(FL(), functionState, builder, sourceRefMT, ref);

        // Alias when receiving from the outside world, see DEPAR.
        globalState->getRegion(sourceRefMT)
            ->alias(FL(), functionState, builder, sourceRefMT, ref);

        return ref;
      } else {
        assert(false);
      }
      break;

    default:
      assert(false);
  }
  assert(false);
}

LLVMValueRef resilientEncryptAndSendFamiliarReference(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    HybridGenerationalMemory* hgm,
    Reference* sourceRefMT,
    Ref sourceRef) {

  switch (sourceRefMT->ownership) {
    case Ownership::OWN:
    case Ownership::SHARE: {
      return regularEncryptAndSendFamiliarReference(
          globalState, functionState, builder, kindStructs, sourceRefMT, sourceRef);
    }
    case Ownership::BORROW:
    case Ownership::WEAK: {
      // Dealias when sending to the outside world, see DEPAR.
      globalState->getRegion(sourceRefMT)
          ->dealias(FL(), functionState, builder, sourceRefMT, sourceRef);

      if (dynamic_cast<StructKind*>(sourceRefMT->kind) ||
          dynamic_cast<StaticSizedArrayT*>(sourceRefMT->kind) ||
          dynamic_cast<RuntimeSizedArrayT*>(sourceRefMT->kind)) {
//        auto sourceRefLE = globalState->getRegion(sourceRefMT)->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef);
//        auto objPtrIntLE = LLVMBuildPtrToInt(builder, sourceRefLE, LLVMInt64TypeInContext(globalState->context), "objPtrInt");
//
        return hgm->implodeConcreteHandle(functionState, builder, sourceRefMT, sourceRef);
      } else if (dynamic_cast<InterfaceKind*>(sourceRefMT->kind)) {
//        globalState->getRegion(sourceRefMT)->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef);
//        LLVMValueRef itablePtrLE = nullptr, objPtrLE = nullptr;
//        std::tie(itablePtrLE, objPtrLE) = globalState->getRegion(sourceRefMT)->explodeInterfaceRef(functionState, builder, sourceRefMT, sourceRef);
//        auto objPtrIntLE = LLVMBuildPtrToInt(builder, objPtrLE, LLVMInt64TypeInContext(globalState->context), "objPtrInt");
//        auto itablePtrIntLE = LLVMBuildPtrToInt(builder, itablePtrLE, LLVMInt64TypeInContext(globalState->context), "itablePtrInt");
        return hgm->implodeInterfaceHandle(functionState, builder, sourceRefMT, sourceRef);
      } else {
        assert(false);
      }
      break;
    }
    default:
      assert(false);
  }
  assert(false);
}

LLVMValueRef implodeConcreteHandle(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef concreteHandleLT,
    LLVMValueRef regionIdLE,
    LLVMValueRef objPtrIntLE,
    LLVMValueRef genLE,
    LLVMValueRef offsetToGenLE) {
  assert(LLVMTypeOf(regionIdLE) == LLVMInt64TypeInContext(globalState->context));
  assert(LLVMTypeOf(objPtrIntLE) == LLVMInt64TypeInContext(globalState->context));
  assert(LLVMTypeOf(genLE) == LLVMInt32TypeInContext(globalState->context));
  assert(LLVMTypeOf(offsetToGenLE) == LLVMInt32TypeInContext(globalState->context));

  auto handleLE = LLVMGetUndef(concreteHandleLT);
  handleLE = LLVMBuildInsertValue(builder, handleLE, regionIdLE, 0, "handle");
  handleLE = LLVMBuildInsertValue(builder, handleLE, objPtrIntLE, 1, "handle");
  handleLE = LLVMBuildInsertValue(builder, handleLE, genLE, 2, "handle");
  handleLE = LLVMBuildInsertValue(builder, handleLE, offsetToGenLE, 3, "handle");
  return handleLE;
}

LLVMValueRef implodeInterfaceHandle(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMTypeRef interfaceHandleLT,
    LLVMValueRef regionIdLE,
    LLVMValueRef itableIntLE,
    LLVMValueRef objPtrIntLE,
    LLVMValueRef genLE,
    LLVMValueRef offsetToGenLE) {
  assert(LLVMTypeOf(regionIdLE) == LLVMInt64TypeInContext(globalState->context));
  assert(LLVMTypeOf(itableIntLE) == LLVMInt64TypeInContext(globalState->context));
  assert(LLVMTypeOf(regionIdLE) == LLVMInt64TypeInContext(globalState->context));
  assert(LLVMTypeOf(objPtrIntLE) == LLVMInt64TypeInContext(globalState->context));
  assert(LLVMTypeOf(genLE) == LLVMInt32TypeInContext(globalState->context));
  assert(LLVMTypeOf(offsetToGenLE) == LLVMInt32TypeInContext(globalState->context));

  auto handleLE = LLVMGetUndef(interfaceHandleLT);
  handleLE = LLVMBuildInsertValue(builder, handleLE, regionIdLE, 0, "handle");
  handleLE = LLVMBuildInsertValue(builder, handleLE, itableIntLE, 1, "handle");
  handleLE = LLVMBuildInsertValue(builder, handleLE, objPtrIntLE, 2, "handle");
  handleLE = LLVMBuildInsertValue(builder, handleLE, genLE, 3, "handle");
  handleLE = LLVMBuildInsertValue(builder, handleLE, offsetToGenLE, 4, "handle");
  return handleLE;
}

std::tuple<LLVMValueRef, LLVMValueRef, LLVMValueRef, LLVMValueRef>
explodeConcreteHandle(GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef concreteHandleLE) {
  assert(LLVMTypeOf(concreteHandleLE) == globalState->getConcreteHandleStruct());
  auto regionIdLE = LLVMBuildExtractValue(builder, concreteHandleLE, 0, "regionId");
  auto objPtrIntLE = LLVMBuildExtractValue(builder, concreteHandleLE, 1, "objPtrInt");
  auto genLE = LLVMBuildExtractValue(builder, concreteHandleLE, 2, "gen");
  auto offsetToGenLE = LLVMBuildExtractValue(builder, concreteHandleLE, 3, "offsetToGen");
  return std::make_tuple(regionIdLE, objPtrIntLE, genLE, offsetToGenLE);
}

std::tuple<LLVMValueRef, LLVMValueRef, LLVMValueRef, LLVMValueRef, LLVMValueRef>
explodeInterfaceHandle(GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef interfaceHandleLE) {
  assert(LLVMTypeOf(interfaceHandleLE) == globalState->getInterfaceHandleStruct());
  auto regionIdLE = LLVMBuildExtractValue(builder, interfaceHandleLE, 0, "regionId");
  auto itablePtrIntLE = LLVMBuildExtractValue(builder, interfaceHandleLE, 1, "itablePtrInt");
  auto objPtrIntLE = LLVMBuildExtractValue(builder, interfaceHandleLE, 2, "objPtrInt");
  auto genLE = LLVMBuildExtractValue(builder, interfaceHandleLE, 3, "gen");
  auto offsetToGenLE = LLVMBuildExtractValue(builder, interfaceHandleLE, 4, "offsetToGen");
  return std::make_tuple(regionIdLE, itablePtrIntLE, objPtrIntLE, genLE, offsetToGenLE);
}

std::string generateMutableConcreteHandleDefC(Package* currentPackage, const std::string& name) {
  return std::string() + "typedef struct " + name + "Ref { uint64_t unused0; uint64_t unused1; uint32_t unused2; uint32_t unused3; } " + name + "Ref;\n";
}

std::string generateMutableInterfaceHandleDefC(Package* currentPackage, const std::string& name) {
  return std::string() + "typedef struct " + name + "Ref { uint64_t unused0; uint64_t unused1; uint64_t unused2; uint32_t unused3; uint32_t unused4; } " + name + "Ref;\n";
}


void fastPanic(GlobalState* globalState, AreaAndFileAndLine from, LLVMBuilderRef builder) {
  if (globalState->opt->fastCrash) {
    auto ptrToWriteToLE = LLVMBuildLoad(builder, globalState->crashGlobal,
        "crashGlobal");
    LLVMBuildStore(builder, constI64LE(globalState, 0), ptrToWriteToLE);
  } else {
    buildPrintAreaAndFileAndLine(globalState, builder, from);
    buildPrint(globalState, builder, "Tried dereferencing dangling reference! ");
    buildPrint(globalState, builder, "Exiting!\n");
    // See MPESC for status codes
    auto exitCodeIntLE = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 14, false);
    LLVMBuildCall(builder, globalState->externs->exit, &exitCodeIntLE, 1, "");
  }
}

