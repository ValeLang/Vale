#include <llvm-c/Types.h>
#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/shared/shared.h>
#include <region/common/controlblock.h>
#include <utils/counters.h>
#include <utils/branch.h>
#include <region/common/common.h>
#include "hgm.h"

constexpr int WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN = 0;

HybridGenerationalMemory::HybridGenerationalMemory(
    GlobalState* globalState_,
    KindStructs* kindStructs_,
    bool elideChecksForKnownLive_,
    bool false_,
    StructKind* anyMT_)
  : globalState(globalState_),
    fatWeaks(globalState_, kindStructs_),
    kindStructs(kindStructs_),
    elideChecksForKnownLive(elideChecksForKnownLive_),
//    false(false_),
    anyMT(anyMT_),
    globalNullPtrPtrByKind(0, globalState->makeAddressHasher<Kind*>()) {
}

void HybridGenerationalMemory::mainSetup(FunctionState* functionState, LLVMBuilderRef builder) {
  if (anyMT != globalState->metalCache->emptyTupleStruct) {
    auto anyRefMT = globalState->metalCache->getReference(Ownership::OWN, Location::YONDER, anyMT);
    auto anyInlRefMT = globalState->metalCache->getReference(Ownership::OWN, Location::INLINE, anyMT);
    auto anyRefLT = globalState->getRegion(anyMT)->translateType(anyRefMT);
    auto anyInlRefLT = globalState->getRegion(anyMT)->translateType(anyInlRefMT);

    undeadCycleNodeLT = LLVMStructCreateNamed(globalState->context, "__ValeHGM_UndeadCycleNode");
    std::vector<LLVMTypeRef> undeadCycleNodeMembers = {
        LLVMPointerType(undeadCycleNodeLT, 0),
        anyRefLT,
    };
    LLVMStructSetBody(undeadCycleNodeLT, undeadCycleNodeMembers.data(), undeadCycleNodeMembers.size(), false);

    undeadCycleHeadNodePtrPtrLE =
        LLVMAddGlobal(globalState->mod, LLVMPointerType(undeadCycleNodeLT, 0), "__ValeHGM_undeadCycleCurrent");
    LLVMSetInitializer(undeadCycleHeadNodePtrPtrLE, LLVMConstNull(LLVMPointerType(undeadCycleNodeLT, 0)));

    halfProtectedI8PtrPtrLE = LLVMAddGlobal(globalState->mod, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "halfProtectedI8Ptr");
    LLVMSetInitializer(halfProtectedI8PtrPtrLE, LLVMConstNull(LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0)));

    buildFlare(FL(), globalState, functionState, builder, "Half protected ptr: ", ptrToIntLE(globalState, builder, halfProtectedI8PtrPtrLE));

    auto setupFuncProto = makeMainSetupFunction();
    auto setupFuncL = globalState->extraFunctions.find(setupFuncProto)->second;
    LLVMBuildCall(builder, setupFuncL, nullptr, 0, "");

    cleanupIterPrototype = makeCleanupIterFunction();
  }
}

Prototype* HybridGenerationalMemory::makeMainSetupFunction() {
  auto anyRefMT = globalState->metalCache->getReference(Ownership::OWN, Location::YONDER, anyMT);
  auto anyInlRefMT = globalState->metalCache->getReference(Ownership::OWN, Location::INLINE, anyMT);
  auto anyRefLT = globalState->getRegion(anyMT)->translateType(anyRefMT);
  auto anyInlRefLT = globalState->getRegion(anyMT)->translateType(anyInlRefMT);

  auto setupFuncName =
      globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__ValeHGM_mainSetup");
  auto setupFuncProto =
      globalState->metalCache->getPrototype(setupFuncName, globalState->metalCache->i64Ref, {});
  declareAndDefineExtraFunction(
      globalState, setupFuncProto, setupFuncName->name,
      [this, anyRefMT, anyRefLT, anyInlRefLT](FunctionState *functionState, LLVMBuilderRef builder) {

        buildFlare(FL(), globalState, functionState, builder);

        auto protectedTwinPagePtrLE =
            LLVMBuildCall(builder, globalState->externs->initTwinPages, nullptr, 0, "protectedTwinPagePtr");
        size_t numProtectedBytes = LLVMABISizeOfType(globalState->dataLayout, anyInlRefLT);
        LLVMValueRef negativeNumProtectedBytesLE = constI64LE(globalState, -numProtectedBytes);

        buildFlare(FL(), globalState, functionState, builder);

        auto halfProtectedI8PtrLE =
            LLVMBuildGEP(builder, protectedTwinPagePtrLE, &negativeNumProtectedBytesLE, 1, "");
        LLVMBuildStore(builder, halfProtectedI8PtrLE, halfProtectedI8PtrPtrLE);

        buildFlare(FL(), globalState, functionState, builder);

        if (globalState->opt->census) {
          std::cerr << "HGM half-protected block has no census fields!" << std::endl;
          LLVMBuildCall(builder, globalState->externs->censusAdd, &halfProtectedI8PtrLE, 1, "");
        }

        buildFlare(FL(), globalState, functionState, builder);

        auto halfProtectedAnyObjWrapperPtrLE =
            kindStructs->makeWrapperPtr(
                FL(), functionState, builder, anyRefMT,
                LLVMBuildPointerCast(builder, halfProtectedI8PtrLE, anyRefLT, "halfProtectedAnyObjPtr"));
        auto halfProtectedObjControlBlockPtrLE =
            kindStructs->getConcreteControlBlockPtr(
                FL(), functionState, builder, anyRefMT, halfProtectedAnyObjWrapperPtrLE);

        buildFlare(FL(), globalState, functionState, builder);

        auto controlBlock = kindStructs->getControlBlock(anyMT);

        auto genMemberIndex = controlBlock->getMemberIndex(ControlBlockMember::GENERATION_32B);
        auto genPtrLE =
            LLVMBuildStructGEP(builder, halfProtectedObjControlBlockPtrLE.refLE, genMemberIndex, "genPtr");
        auto genLT = LLVMIntTypeInContext(globalState->context, GENERATION_NUM_BITS);
        auto intMaxLE = LLVMConstSExt(constI8LE(globalState, 0xFF), genLT);
        LLVMBuildStore(builder, intMaxLE, genPtrLE);

        buildFlare(FL(), globalState, functionState, builder);

        auto tetherMemberIndex = controlBlock->getMemberIndex(ControlBlockMember::TETHER_32B);
        auto tetheredPtrLE = LLVMBuildStructGEP(
            builder, halfProtectedObjControlBlockPtrLE.refLE, tetherMemberIndex, "tetherPtr");
        auto isTetheredLE = constI32LE(globalState, 1);
        LLVMBuildStore(builder, isTetheredLE, tetheredPtrLE);

        buildFlare(FL(), globalState, functionState, builder);

        auto undeadCycleHeadNodeI8PtrLE =
            callMalloc(
                globalState,
                builder,
                constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, undeadCycleNodeLT)));
        auto undeadCycleHeadNodePtrLE =
            LLVMBuildPointerCast(
                builder, undeadCycleHeadNodeI8PtrLE, LLVMPointerType(undeadCycleNodeLT, 0),
                "undeadCycleHeadNodePtr");
        //buildFlare(FL(), globalState, functionState, builder, "head: ", ptrToIntLE(globalState, builder, undeadCycleHeadNodeI8PtrLE));
        // Make it point at itself for its next ptr
        auto undeadCycleHeadNodeNextPtrPtrLE =
            LLVMBuildStructGEP(builder, undeadCycleHeadNodePtrLE, 0, "nextPtrPtr");
        LLVMBuildStore(builder, undeadCycleHeadNodePtrLE, undeadCycleHeadNodeNextPtrPtrLE);
        //buildFlare(FL(), globalState, functionState, builder, "next: ", ptrToIntLE(globalState, builder, undeadCycleHeadNodePtrLE));
        // Point it at the half protected global, arbitrarily. Could use something else if we wanted to, but might as well.
        auto undeadCycleHeadNodeObjPtrPtrLE =
            LLVMBuildStructGEP(builder, undeadCycleHeadNodePtrLE, 1, "objPtrPtr");
        LLVMBuildStore(builder, halfProtectedAnyObjWrapperPtrLE.refLE, undeadCycleHeadNodeObjPtrPtrLE);
        //buildFlare(FL(), globalState, functionState, builder, "obj: ", ptrToIntLE(globalState, builder, halfProtectedAnyObjWrapperPtrLE.refLE));

        LLVMBuildStore(builder, undeadCycleHeadNodePtrLE, undeadCycleHeadNodePtrPtrLE);

        // The caller is about to kill our builder, so we're making another one pointing at the same block.
        setupBuilder = LLVMCreateBuilderInContext(globalState->context);
        LLVMPositionBuilderAtEnd(setupBuilder, LLVMGetInsertBlock(builder));

        buildFlare(FL(), globalState, functionState, builder);
      });
  return setupFuncProto;
}

void HybridGenerationalMemory::mainCleanup(FunctionState* functionState, LLVMBuilderRef builder) {
  if (anyMT != globalState->metalCache->emptyTupleStruct) {
    auto boolRefMT = globalState->metalCache->boolRef;
    auto anyRefMT = globalState->metalCache->getReference(Ownership::OWN, Location::YONDER, anyMT);

    LLVMBuildRet(setupBuilder, constI64LE(globalState, 0));
    LLVMDisposeBuilder(setupBuilder);

    buildFlare(FL(), globalState, functionState, builder);
    auto cleanupFuncProto = makeMainCleanupFunction();
    auto setupFuncL = globalState->extraFunctions.find(cleanupFuncProto)->second;
    buildFlare(FL(), globalState, functionState, builder);
    LLVMBuildCall(builder, setupFuncL, nullptr, 0, "");
    buildFlare(FL(), globalState, functionState, builder);
  }
}

// Attempts to clean up the head's next node.
// Returns 0 if there are none left, 1 if we cleaned one up, 2 if we couldn't clean one up.
Prototype* HybridGenerationalMemory::makeCleanupLoopFunction() {
  auto cleanupLoopFuncName =
      globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__ValeHGM_cleanupIter");
  auto cleanupLoopFuncProto =
      globalState->metalCache->getPrototype(cleanupLoopFuncName, globalState->metalCache->i64Ref, {});
  declareAndDefineExtraFunction(
      globalState, cleanupLoopFuncProto, cleanupLoopFuncName->name,
      [this](FunctionState *functionState, LLVMBuilderRef builder) {
        buildWhile(globalState, functionState, builder,
            [this, functionState](LLVMBuilderRef conditionBuilder) {
              buildFlare(FL(), globalState, functionState, conditionBuilder);
              auto undeadCycleHeadNodePtrLE =
                  LLVMBuildLoad(conditionBuilder, undeadCycleHeadNodePtrPtrLE, "undeadCycleHeadNodePtr");
//              buildFlare(FL(), globalState, functionState, conditionBuilder, "head: ",
//                  ptrToIntLE(globalState, conditionBuilder, undeadCycleHeadNodePtrLE));
              auto undeadCycleHeadNodeNextPtrPtrLE =
                  LLVMBuildStructGEP(
                      conditionBuilder, undeadCycleHeadNodePtrLE, 0, "undeadCycleHeadNodeNextPtrPtr");
              //buildFlare(FL(), globalState, functionState, conditionBuilder);
              auto undeadCycleHeadNodeNextPtrLE =
                  LLVMBuildLoad(conditionBuilder, undeadCycleHeadNodeNextPtrPtrLE, "undeadCycleHeadNodeNextPtr");
//              buildFlare(FL(), globalState, functionState, conditionBuilder, "next: ",
//                  ptrToIntLE(globalState, conditionBuilder, undeadCycleHeadNodeNextPtrLE));
              auto undeadCycleNonEmptyDiffLE =
                  LLVMBuildPtrDiff(
                      conditionBuilder, undeadCycleHeadNodeNextPtrLE, undeadCycleHeadNodePtrLE, "undeadCycleNonEmptyDiff");
//              buildFlare(FL(), globalState, functionState, conditionBuilder, "diff: ", undeadCycleNonEmptyDiffLE);
              auto undeadCycleEmptyLE =
                  LLVMBuildICmp(
                      conditionBuilder, LLVMIntNE, undeadCycleNonEmptyDiffLE, constI64LE(globalState, 0),
                      "undeadCycleNonEmpty");
//              buildFlare(FL(), globalState, functionState, conditionBuilder);
              auto undeadCycleNonEmptyRef =
                  wrap(
                      globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef,
                      undeadCycleEmptyLE);
              return undeadCycleNonEmptyRef;
            },
            [this, functionState](LLVMBuilderRef bodyBuilder) {
              buildCall(globalState, functionState, bodyBuilder, cleanupIterPrototype, {});
            });
        LLVMBuildRet(builder, constI64LE(globalState, 0));
      });
  return cleanupLoopFuncProto;
}

// Attempts to clean up the head's next node.
// Returns 0 if there are none left, 1 if we cleaned one up, 2 if we couldn't clean one up.
Prototype* HybridGenerationalMemory::makeCleanupIterFunction() {
  auto anyRefMT = globalState->metalCache->getReference(Ownership::OWN, Location::YONDER, anyMT);

  auto cleanupIterFuncName =
      globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__ValeHGM_cleanupIter");
  auto cleanupIterFuncProto =
      globalState->metalCache->getPrototype(cleanupIterFuncName, globalState->metalCache->i64Ref, {});
  declareAndDefineExtraFunction(
      globalState, cleanupIterFuncProto, cleanupIterFuncName->name,
      [this, anyRefMT](FunctionState *functionState, LLVMBuilderRef builder) {
        buildFlare(FL(), globalState, functionState, builder, "In an iteration!");
        auto undeadCycleHeadNodePtrLE =
            LLVMBuildLoad(builder, undeadCycleHeadNodePtrPtrLE, "undeadCycleHeadNodePtr");
//        buildFlare(FL(), globalState, functionState, builder, "cycle head: ",
//            ptrToIntLE(globalState, builder, undeadCycleHeadNodePtrLE));

        auto undeadCycleHeadNodeNextPtrPtrLE =
            LLVMBuildStructGEP(
                builder, undeadCycleHeadNodePtrLE, 0, "undeadCycleHeadNodeNextPtrPtr");
        // This is the node that we might be removing.
        auto undeadCycleNextNodePtrLE =
            LLVMBuildLoad(builder, undeadCycleHeadNodeNextPtrPtrLE, "undeadCycleNextNodePtr");
//        buildFlare(FL(), globalState, functionState, builder, "cycle head next: ",
//            ptrToIntLE(globalState, builder, undeadCycleNextNodePtrLE));

        auto undeadCycleNextNodeNextPtrPtrLE =
            LLVMBuildStructGEP(
                builder, undeadCycleNextNodePtrLE, 0, "undeadCycleNextNodeNextPtrPtr");
        auto undeadCycleNextNodeNextPtrLE =
            LLVMBuildLoad(builder, undeadCycleNextNodeNextPtrPtrLE, "undeadCycleNextNodeNextPtr");
//        buildFlare(FL(), globalState, functionState, builder, "cycle head next next: ",
//            ptrToIntLE(globalState, builder, undeadCycleNextNodeNextPtrLE));

        // Now we'll get a pointer to its object.
        auto undeadCycleNextNodeObjPtrPtrLE =
            LLVMBuildStructGEP(
                builder, undeadCycleNextNodePtrLE, 1, "undeadCycleNextNodeObjPtrPtr");
        auto undeadCycleNextNodeObjPtrLE =
            LLVMBuildLoad(builder, undeadCycleNextNodeObjPtrPtrLE, "undeadCycleNextNodeObjPtr");
//        buildFlare(FL(), globalState, functionState, builder, "cycle head next obj: ",
//            ptrToIntLE(globalState, builder, undeadCycleNextNodeObjPtrLE));

        auto nextNodeObjWrapperPtrLE =
            kindStructs->makeWrapperPtr(FL(), functionState, builder, anyRefMT, undeadCycleNextNodeObjPtrLE);
        auto nextNodeObjRef = wrap(globalState->getRegion(anyMT), anyRefMT, nextNodeObjWrapperPtrLE);

        auto controlBlock = kindStructs->getControlBlock(anyMT);
        auto tetherMemberIndex = controlBlock->getMemberIndex(ControlBlockMember::TETHER_32B);
        auto controlBlockPtrLE =
            kindStructs->getConcreteControlBlockPtr(FL(), functionState, builder, anyRefMT,
                nextNodeObjWrapperPtrLE);
        auto tetherPtrLE = LLVMBuildStructGEP(builder, controlBlockPtrLE.refLE, tetherMemberIndex, "tetherPtr");
        auto tetherI32LE = LLVMBuildLoad(builder, tetherPtrLE, "tetherI32");
        auto isTetheredLE =
            LLVMBuildTrunc(builder, tetherI32LE, LLVMInt1TypeInContext(globalState->context), "wasAlive");
        auto isTetheredRef = wrap(
            globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isTetheredLE);

        buildFlare(FL(), globalState, functionState, builder, "is tethered: ", tetherI32LE);

        auto resultIntRef =
            buildIfElse(
                globalState, functionState, builder,
                isTetheredRef, LLVMInt64TypeInContext(globalState->context),
                globalState->metalCache->i64Ref,
                globalState->metalCache->i64Ref,
                [this, undeadCycleNextNodePtrLE](LLVMBuilderRef thenBuilder) {
                  // Make the next one the new head.
                  LLVMBuildStore(thenBuilder, undeadCycleNextNodePtrLE, undeadCycleHeadNodePtrPtrLE);
                  return globalState->constI64(0);
                },
                [this, functionState, nextNodeObjRef, nextNodeObjWrapperPtrLE, undeadCycleNextNodePtrLE,
                 undeadCycleNextNodeNextPtrLE, undeadCycleHeadNodeNextPtrPtrLE, anyRefMT](LLVMBuilderRef elseBuilder) {
                  // It's not tethered, so nuke it!
                  buildFlare(FL(), globalState, functionState, elseBuilder, "deallocating! ", ptrToIntLE(globalState, elseBuilder, nextNodeObjWrapperPtrLE.refLE));
                  innerDeallocate(FL(), globalState, functionState, kindStructs, elseBuilder, anyRefMT, nextNodeObjRef);
                  buildFlare(FL(), globalState, functionState, elseBuilder);
                  callFree(globalState, elseBuilder, undeadCycleNextNodePtrLE);
//                  buildFlare(FL(), globalState, functionState, elseBuilder);
                  // Make the head node point somewhere else
                  LLVMBuildStore(elseBuilder, undeadCycleNextNodeNextPtrLE, undeadCycleHeadNodeNextPtrPtrLE);
//                  buildFlare(FL(), globalState, functionState, elseBuilder);
                  return globalState->constI64(1);
                });

        auto resultIntLE =
            globalState->getRegion(globalState->metalCache->i64Ref)->checkValidReference(
                FL(), functionState, builder, globalState->metalCache->i64Ref, resultIntRef);
        LLVMBuildRet(builder, resultIntLE);
      });
  return cleanupIterFuncProto;
}

Prototype* HybridGenerationalMemory::makeMainCleanupFunction() {
  auto anyRefMT = globalState->metalCache->getReference(Ownership::OWN, Location::YONDER, anyMT);
  auto anyInlRefMT = globalState->metalCache->getReference(Ownership::OWN, Location::INLINE, anyMT);
  auto anyRefLT = globalState->getRegion(anyMT)->translateType(anyRefMT);
  auto anyInlRefLT = globalState->getRegion(anyMT)->translateType(anyInlRefMT);
  auto boolRefMT = globalState->metalCache->boolRef;

  auto cleanupFuncName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__ValeHGM_mainCleanup");
  auto cleanupFuncProto =
      globalState->metalCache->getPrototype(cleanupFuncName, globalState->metalCache->i64Ref, {});
  declareAndDefineExtraFunction(
      globalState, cleanupFuncProto, cleanupFuncName->name,
      [this, boolRefMT, anyRefMT](FunctionState *functionState, LLVMBuilderRef builder) {
        buildFlare(FL(), globalState, functionState, builder);

        buildCall(globalState, functionState, builder, makeCleanupLoopFunction(), {});
        LLVMBuildRet(builder, constI64LE(globalState, 0));

//        buildWhile(
//            globalState, functionState, builder,
//            [this, boolRefMT, functionState](LLVMBuilderRef conditionBuilder) {
//              buildFlare(FL(), globalState, functionState, conditionBuilder);
//              auto undeadCycleNodePtrLE =
//                  LLVMBuildLoad(conditionBuilder, undeadCycleHeadNodePtrPtrLE, "undeadCycleNodePtr");
//              buildFlare(FL(), globalState, functionState, conditionBuilder, "head: ", ptrToIntLE(globalState, conditionBuilder, undeadCycleNodePtrLE));
//              auto undeadCycleNodeNextPtrPtrLE =
//                  LLVMBuildStructGEP(
//                      conditionBuilder, undeadCycleNodePtrLE, 0, "undeadCycleNodeNextPtrPtr");
//              buildFlare(FL(), globalState, functionState, conditionBuilder);
//              auto undeadCycleNodeNextPtrLE =
//                  LLVMBuildLoad(conditionBuilder, undeadCycleNodeNextPtrPtrLE, "undeadCycleNodeNextPtr");
//              buildFlare(FL(), globalState, functionState, conditionBuilder, "next: ", ptrToIntLE(globalState, conditionBuilder, undeadCycleNodeNextPtrLE));
//              auto undeadCycleNonEmptyDiffLE =
//                  LLVMBuildPtrDiff(
//                      conditionBuilder, undeadCycleNodeNextPtrLE, undeadCycleNodePtrLE, "undeadCycleNonEmptyDiff");
//              buildFlare(FL(), globalState, functionState, conditionBuilder, "diff: ", undeadCycleNonEmptyDiffLE);
//              auto undeadCycleNonEmptyLE =
//                  LLVMBuildICmp(
//                      conditionBuilder, LLVMIntNE, undeadCycleNonEmptyDiffLE, constI64LE(globalState, 0),
//                      "undeadCycleNonEmpty");
//              buildFlare(FL(), globalState, functionState, conditionBuilder);
//              return wrap(globalState->getRegion(boolRefMT), boolRefMT, undeadCycleNonEmptyLE);
//            },
//            [this, anyRefMT, functionState](LLVMBuilderRef bodyBuilder) {
//              buildFlare(FL(), globalState, functionState, bodyBuilder);
//              auto undeadCycleNodePtrLE = LLVMBuildLoad(bodyBuilder, undeadCycleHeadNodePtrPtrLE, "undeadCycleNodePtr");
//              auto undeadCycleNodeObjPtrPtrLE =
//                  LLVMBuildStructGEP(
//                      bodyBuilder, undeadCycleNodePtrLE, 1, "undeadCycleNodeObjPtrPtr");
//              auto undeadCycleNodeObjPtrLE =
//                  LLVMBuildLoad(bodyBuilder, undeadCycleNodeObjPtrPtrLE, "undeadCycleNodeObjPtr");
//              auto undeadCycleNodeObjRef = wrap(globalState->getRegion(anyMT), anyRefMT, undeadCycleNodeObjPtrLE);
//              buildFlare(FL(), globalState, functionState, bodyBuilder);
//              innerDeallocate(FL(), globalState, functionState, kindStructs, bodyBuilder, anyRefMT,
//                  undeadCycleNodeObjRef);
//              buildFlare(FL(), globalState, functionState, bodyBuilder);
//            });
//        buildFlare(FL(), globalState, functionState, builder);
//        LLVMBuildRet(builder, constI64LE(globalState, 0));
      });
  return cleanupFuncProto;
}

LLVMValueRef HybridGenerationalMemory::getTargetGenFromWeakRef(
    LLVMBuilderRef builder,
    KindStructs* kindStructs,
    Kind* kind,
    WeakFatPtrLE weakRefLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
             globalState->opt->regionOverride == RegionOverride::RESILIENT_V4);
  auto headerLE = fatWeaks.getHeaderFromWeakRef(builder, weakRefLE);
  assert(LLVMTypeOf(headerLE) == kindStructs->getWeakRefHeaderStruct(kind));
  return LLVMBuildExtractValue(builder, headerLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "actualGeni");
}

static LLVMValueRef makeGenHeader(
    GlobalState* globalState,
    KindStructs* kindStructs,
    LLVMBuilderRef builder,
    Kind* kind,
    LLVMValueRef targetGenLE) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
         globalState->opt->regionOverride == RegionOverride::RESILIENT_V4);
  auto headerLE = LLVMGetUndef(kindStructs->getWeakRefHeaderStruct(kind));
  headerLE = LLVMBuildInsertValue(builder, headerLE, targetGenLE, WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN, "header");
  return headerLE;
}

static LLVMValueRef getGenerationFromControlBlockPtr(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    KindStructs* structs,
    Kind* kindM,
    ControlBlockPtrLE controlBlockPtr) {
  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
             globalState->opt->regionOverride == RegionOverride::RESILIENT_V4);
  assert(LLVMTypeOf(controlBlockPtr.refLE) == LLVMPointerType(structs->getControlBlock(kindM)->getStruct(), 0));

  auto genPtrLE =
      LLVMBuildStructGEP(
          builder,
          controlBlockPtr.refLE,
          structs->getControlBlock(kindM)->getMemberIndex(ControlBlockMember::GENERATION_32B),
          "genPtr");
  return LLVMBuildLoad(builder, genPtrLE, "gen");
}

WeakFatPtrLE HybridGenerationalMemory::weakStructPtrToGenWeakInterfacePtr(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    WeakFatPtrLE sourceRefLE,
    StructKind* sourceStructKindM,
    Reference* sourceStructTypeM,
    InterfaceKind* targetInterfaceKindM,
    Reference* targetInterfaceTypeM) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::RESILIENT_V3: case RegionOverride::RESILIENT_V4:
      // continue
      break;
    case RegionOverride::FAST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::ASSIST:
      assert(false);
      break;
    default:
      assert(false);
      break;
  }

//  checkValidReference(
//      FL(), globalState, functionState, builder, sourceStructTypeM, sourceRefLE);
  auto controlBlockPtr =
      kindStructs->getConcreteControlBlockPtr(
          FL(), functionState, builder, sourceStructTypeM,
          kindStructs->makeWrapperPtr(
              FL(), functionState, builder, sourceStructTypeM,
              fatWeaks.getInnerRefFromWeakRef(
                  functionState, builder, sourceStructTypeM, sourceRefLE)));

  auto interfaceRefLT =
      kindStructs->getInterfaceWeakRefStruct(
          targetInterfaceKindM);
  auto headerLE = fatWeaks.getHeaderFromWeakRef(builder, sourceRefLE);

  auto objPtr =
      makeInterfaceRefStruct(
          globalState, functionState, builder, kindStructs, sourceStructKindM, targetInterfaceKindM, controlBlockPtr);

  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetInterfaceTypeM, interfaceRefLT, headerLE, objPtr);
}

// Makes a non-weak interface ref into a weak interface ref
WeakFatPtrLE HybridGenerationalMemory::assembleInterfaceWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    InterfaceKind* interfaceKindM,
    InterfaceFatPtrLE sourceInterfaceFatPtrLE) {
  assert(sourceType->ownership == Ownership::OWN || sourceType->ownership == Ownership::SHARE);
  // curious, if its a borrow, do we just return sourceRefLE?

  LLVMValueRef genLE = nullptr;
  if (sourceType->ownership == Ownership::OWN) {
    auto controlBlockPtrLE =
        kindStructs->getControlBlockPtr(FL(), functionState, builder, interfaceKindM, sourceInterfaceFatPtrLE);
//    if (false) {
//      genLE = constI64LE(globalState, 0);
//    } else {
      genLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, sourceType->kind,
          controlBlockPtrLE);
//    }
  } else if (sourceType->ownership == Ownership::BORROW) {
    assert(false); // impl
  } else {
    assert(false);
  }
  return assembleInterfaceWeakRef(functionState, builder, targetType, interfaceKindM, genLE, sourceInterfaceFatPtrLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleInterfaceWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* targetType,
    InterfaceKind* interfaceKindM,
    LLVMValueRef currentGenLE,
    InterfaceFatPtrLE sourceInterfaceFatPtrLE) {
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, interfaceKindM, currentGenLE);
  auto weakRefStructLT =
      kindStructs->getInterfaceWeakRefStruct(interfaceKindM);
  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetType, weakRefStructLT, headerLE, sourceInterfaceFatPtrLE.refLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleStructWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    Reference* targetTypeM,
    StructKind* structKindM,
    WrapperPtrLE objPtrLE) {
  auto controlBlockPtrLE = kindStructs->getConcreteControlBlockPtr(FL(), functionState, builder, structTypeM, objPtrLE);
  auto currentGenLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, structTypeM->kind, controlBlockPtrLE);
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, structKindM, currentGenLE);
  auto weakRefStructLT = kindStructs->getStructWeakRefStruct(structKindM);
  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleStructWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* targetTypeM,
    StructKind* structKindM,
    LLVMValueRef currentGenLE,
    WrapperPtrLE objPtrLE) {
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, structKindM, currentGenLE);
  auto weakRefStructLT = kindStructs->getStructWeakRefStruct(structKindM);
  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleStaticSizedArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceSSAMT,
    StaticSizedArrayT* staticSizedArrayMT,
    Reference* targetSSAWeakRefMT,
    WrapperPtrLE sourceRefLE) {
  LLVMValueRef genLE = nullptr;
  if (sourceSSAMT->ownership == Ownership::OWN) {
    auto controlBlockPtrLE = kindStructs->getConcreteControlBlockPtr(FL(), functionState, builder, sourceSSAMT, sourceRefLE);
    genLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, sourceSSAMT->kind, controlBlockPtrLE);
  } else if (sourceSSAMT->ownership == Ownership::BORROW) {
    assert(false); // impl
  } else {
    assert(false);
  }
  return assembleStaticSizedArrayWeakRef(
      functionState, builder, targetSSAWeakRefMT, staticSizedArrayMT, genLE, sourceRefLE);
}


WeakFatPtrLE HybridGenerationalMemory::assembleStaticSizedArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* targetTypeM,
    StaticSizedArrayT* staticSizedArrayMT,
    LLVMValueRef currentGenLE,
    WrapperPtrLE objPtrLE) {
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, staticSizedArrayMT, currentGenLE);
  auto weakRefStructLT = kindStructs->getStaticSizedArrayWeakRefStruct(staticSizedArrayMT);
  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

WeakFatPtrLE HybridGenerationalMemory::assembleRuntimeSizedArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceSSAMT,
    RuntimeSizedArrayT* staticSizedArrayMT,
    Reference* targetSSAWeakRefMT,
    WrapperPtrLE sourceRefLE) {
  LLVMValueRef genLE = nullptr;
  if (sourceSSAMT->ownership == Ownership::OWN) {
    auto controlBlockPtrLE = kindStructs->getConcreteControlBlockPtr(FL(), functionState, builder, sourceSSAMT, sourceRefLE);
    genLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, sourceSSAMT->kind, controlBlockPtrLE);
  } else if (sourceSSAMT->ownership == Ownership::BORROW) {
    assert(false); // impl
  } else {
    assert(false);
  }
  return assembleRuntimeSizedArrayWeakRef(
      functionState, builder, targetSSAWeakRefMT, staticSizedArrayMT, genLE, sourceRefLE);
}


WeakFatPtrLE HybridGenerationalMemory::assembleRuntimeSizedArrayWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* targetTypeM,
    RuntimeSizedArrayT* staticSizedArrayMT,
    LLVMValueRef currentGenLE,
    WrapperPtrLE objPtrLE) {
  auto headerLE = makeGenHeader(globalState, kindStructs, builder, staticSizedArrayMT, currentGenLE);
  auto weakRefStructLT = kindStructs->getRuntimeSizedArrayWeakRefStruct(staticSizedArrayMT);
  return fatWeaks.assembleWeakFatPtr(
      functionState, builder, targetTypeM, weakRefStructLT, headerLE, objPtrLE.refLE);
}

LLVMValueRef HybridGenerationalMemory::lockGenFatPtr(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refM,
    WeakFatPtrLE weakRefLE,
    bool knownLive) {
  auto fatPtrLE = weakRefLE;
  auto innerLE = fatWeaks.getInnerRefFromWeakRef(functionState, builder, refM, fatPtrLE);

  if (knownLive && elideChecksForKnownLive) {
    // Do nothing
  } else {
    if (globalState->opt->printMemOverhead) {
      adjustCounter(globalState, builder, globalState->metalCache->i64, globalState->livenessCheckCounter, 1);
    }
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, refM, fatPtrLE, knownLive);
    buildIf(
        globalState, functionState, builder, isZeroLE(builder, isAliveLE),
        [this, from, functionState, fatPtrLE](LLVMBuilderRef thenBuilder) {
          if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
              globalState->opt->regionOverride == RegionOverride::RESILIENT_V4) {
            auto ptrToWriteToLE = LLVMBuildLoad(thenBuilder, globalState->crashGlobal,
                "crashGlobal");
            LLVMBuildStore(thenBuilder, constI64LE(globalState, 0), ptrToWriteToLE);
          } else {
            buildPrintAreaAndFileAndLine(globalState, thenBuilder, from);
            buildPrint(globalState, thenBuilder, "Tried dereferencing dangling reference! ");
            buildPrint(globalState, thenBuilder, "Exiting!\n");
            // See MPESC for status codes
            auto exitCodeIntLE = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 14, false);
            LLVMBuildCall(thenBuilder, globalState->externs->exit, &exitCodeIntLE, 1, "");
          }
        });
  }
  return innerLE;
}

void HybridGenerationalMemory::innerNoteWeakableDestroyed(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* concreteRefM,
    ControlBlockPtrLE controlBlockPtrLE) {
  // No need to do anything!
}


void HybridGenerationalMemory::aliasWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  // Do nothing!
}

void HybridGenerationalMemory::discardWeakRef(
    AreaAndFileAndLine from,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefMT,
    Ref weakRef) {
  // Do nothing!
}

LLVMValueRef HybridGenerationalMemory::getIsAliveFromWeakFatPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    WeakFatPtrLE weakFatPtrLE,
    bool knownLive) {
  if (false) {
    return LLVMConstInt(LLVMInt1TypeInContext(globalState->context), 1, false);
  } else if (knownLive && elideChecksForKnownLive) {
    return LLVMConstInt(LLVMInt1TypeInContext(globalState->context), 1, false);
  } else {
    // Get target generation from the ref
    auto targetGenLE = getTargetGenFromWeakRef(builder, kindStructs, weakRefM->kind, weakFatPtrLE);

    // Get actual generation from the table
    auto innerRefLE =
        fatWeaks.getInnerRefFromWeakRefWithoutCheck(functionState, builder, weakRefM,
            weakFatPtrLE);
    auto controlBlockPtrLE =
        kindStructs->getControlBlockPtrWithoutChecking(
            FL(), functionState, builder, innerRefLE, weakRefM);
    auto actualGenLE = getGenerationFromControlBlockPtr(globalState, builder, kindStructs, weakRefM->kind,
        controlBlockPtrLE);

    auto isLiveLE = LLVMBuildICmp(builder, LLVMIntEQ, actualGenLE, targetGenLE, "isLive");
    if (knownLive && !elideChecksForKnownLive) {
      // See MPESC for status codes
      buildAssertWithExitCode(globalState, functionState, builder, isLiveLE, 116, "knownLive is true, but object is dead!");
    }

    return isLiveLE;
  }
}

Ref HybridGenerationalMemory::getIsAliveFromWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef,
    bool knownLive) {
  if (false || (knownLive && elideChecksForKnownLive)) {
    // Do nothing, just return a constant true
    auto isAliveLE = LLVMConstInt(LLVMInt1TypeInContext(globalState->context), 1, false);
    return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isAliveLE);
  } else {
    assert(
        weakRefM->ownership == Ownership::BORROW ||
            weakRefM->ownership == Ownership::WEAK);

    auto weakFatPtrLE =
        kindStructs->makeWeakFatPtr(
            weakRefM,
            globalState->getRegion(weakRefM)
                ->checkValidReference(
                    FL(), functionState, builder, weakRefM, weakRef));
    auto isAliveLE = getIsAliveFromWeakFatPtr(functionState, builder, weakRefM, weakFatPtrLE, knownLive);
    return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, isAliveLE);
  }
}

LLVMValueRef HybridGenerationalMemory::fillWeakableControlBlock(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Kind* kindM,
    LLVMValueRef controlBlockLE) {
  // The generation was already incremented when we freed it (or malloc'd it for the first time),
  // so nothing to do here!
  return controlBlockLE;
}

WeakFatPtrLE HybridGenerationalMemory::weakInterfaceRefToWeakStructRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakInterfaceRefMT,
    WeakFatPtrLE weakInterfaceFatPtrLE) {
  auto headerLE = fatWeaks.getHeaderFromWeakRef(builder, weakInterfaceFatPtrLE);

  // The object might not exist, so skip the check.
  auto interfaceFatPtrLE =
      kindStructs->makeInterfaceFatPtrWithoutChecking(
          FL(), functionState, builder,
          weakInterfaceRefMT, // It's still conceptually weak even though its not in a weak pointer.
          fatWeaks.getInnerRefFromWeakRef(functionState, builder, weakInterfaceRefMT, weakInterfaceFatPtrLE));
  auto controlBlockPtrLE =
      kindStructs->getControlBlockPtrWithoutChecking(
          FL(), functionState, builder, weakInterfaceRefMT->kind, interfaceFatPtrLE);

  // Now, reassemble a weak void* ref to the struct.
  auto weakVoidStructRefLE =
      fatWeaks.assembleVoidStructWeakRef(builder, weakInterfaceRefMT, controlBlockPtrLE, headerLE);

  return weakVoidStructRefLE;
}

// USE ONLY FOR ASSERTING A REFERENCE IS VALID
std::tuple<Reference*, LLVMValueRef> hgmGetRefInnardsForChecking(Ref ref) {
  Reference* refM = ref.refM;
  LLVMValueRef refLE = ref.refLE;
  return std::make_tuple(refM, refLE);
}

void HybridGenerationalMemory::buildCheckWeakRef(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef) {
  if (globalState->opt->census) {
    Reference *actualRefM = nullptr;
    LLVMValueRef refLE = nullptr;
    std::tie(actualRefM, refLE) = hgmGetRefInnardsForChecking(weakRef);
    auto weakFatPtrLE = kindStructs->makeWeakFatPtr(weakRefM, refLE);
    auto innerLE =
        fatWeaks.getInnerRefFromWeakRefWithoutCheck(
            functionState, builder, weakRefM, weakFatPtrLE);

    auto controlBlockPtrLE =
        kindStructs->getControlBlockPtrWithoutChecking(
            FL(), functionState, builder, innerLE, weakRefM);
    // We check that the generation is <= to what's in the actual object.
    auto actualGen =
        getGenerationFromControlBlockPtr(
            globalState, builder, kindStructs, weakRefM->kind, controlBlockPtrLE);
    auto targetGen = getTargetGenFromWeakRef(builder, kindStructs, weakRefM->kind, weakFatPtrLE);
    buildCheckGen(globalState, functionState, builder, targetGen, actualGen);

    if (auto interfaceKindM = dynamic_cast<InterfaceKind *>(weakRefM->kind)) {
      auto interfaceFatPtrLE = kindStructs->makeInterfaceFatPtrWithoutChecking(FL(),
          functionState, builder, weakRefM, innerLE);
      auto itablePtrLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);
      buildAssertCensusContains(FL(), globalState, functionState, builder, itablePtrLE);
    }
  }
}

Ref HybridGenerationalMemory::assembleWeakRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceType,
    Reference* targetType,
    Ref sourceRef) {
  // Now we need to package it up into a weak ref.
  if (auto structKind = dynamic_cast<StructKind*>(sourceType->kind)) {
    auto sourceRefLE =
        globalState->getRegion(sourceType)
            ->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleStructWeakRef(
            functionState, builder, sourceType, targetType, structKind, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto interfaceKindM = dynamic_cast<InterfaceKind*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceInterfaceFatPtrLE = kindStructs->makeInterfaceFatPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleInterfaceWeakRef(
            functionState, builder, sourceType, targetType, interfaceKindM, sourceInterfaceFatPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto staticSizedArray = dynamic_cast<StaticSizedArrayT*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleStaticSizedArrayWeakRef(
            functionState, builder, sourceType, staticSizedArray, targetType, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else if (auto runtimeSizedArray = dynamic_cast<RuntimeSizedArrayT*>(sourceType->kind)) {
    auto sourceRefLE = globalState->getRegion(sourceType)->checkValidReference(FL(), functionState, builder, sourceType, sourceRef);
    auto sourceWrapperPtrLE = kindStructs->makeWrapperPtr(FL(), functionState, builder, sourceType, sourceRefLE);
    auto resultLE =
        assembleRuntimeSizedArrayWeakRef(
            functionState, builder, sourceType, runtimeSizedArray, targetType, sourceWrapperPtrLE);
    return wrap(globalState->getRegion(targetType), targetType, resultLE);
  } else assert(false);
}


LLVMTypeRef HybridGenerationalMemory::makeWeakRefHeaderStruct(GlobalState* globalState, RegionId* regionId) {
  assert(regionId == globalState->metalCache->resilientV3RegionId ||
             regionId == globalState->metalCache->resilientV4RegionId);
//  assert(globalState->opt->regionOverride == RegionOverride::RESILIENT_V2 ||
//      globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
//      globalState->opt->regionOverride == RegionOverride::RESILIENT_LIMIT);
  auto genRefStructL = LLVMStructCreateNamed(globalState->context, "__GenRef");

  std::vector<LLVMTypeRef> memberTypesL;

  assert(WEAK_REF_HEADER_MEMBER_INDEX_FOR_TARGET_GEN == memberTypesL.size());
  memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));

  LLVMStructSetBody(genRefStructL, memberTypesL.data(), memberTypesL.size(), false);

  return genRefStructL;
}

WrapperPtrLE HybridGenerationalMemory::getHalfProtectedPtr(
    FunctionState* functionState, LLVMBuilderRef builder, Reference* reference, LLVMTypeRef wrapperStructPtrLT) {
  assert(LLVMGetTypeKind(wrapperStructPtrLT) == LLVMPointerTypeKind);
  auto iter = globalNullPtrPtrByKind.find(reference->kind);
  if (iter == globalNullPtrPtrByKind.end()) {
    auto name = std::string("__ValeHGMNull__") + globalState->getKindName(reference->kind)->name;
    auto globalPtrLE = LLVMAddGlobal(globalState->mod, wrapperStructPtrLT, name.c_str());
    // Should be overwritten just below
    LLVMSetInitializer(globalPtrLE, LLVMConstNull(wrapperStructPtrLT));

    auto castedNameL = std::string("castedHalfProtected_") + globalState->getKindName(reference->kind)->name;
    auto halfProtectedI8PtrLE = LLVMBuildLoad(setupBuilder, halfProtectedI8PtrPtrLE, "halfProtectedI8Ptr");
    auto castedHalfProtectedPtrLE =
        LLVMBuildPointerCast(setupBuilder, halfProtectedI8PtrLE, wrapperStructPtrLT, castedNameL.c_str());
    LLVMBuildStore(setupBuilder, castedHalfProtectedPtrLE, globalPtrLE);

    iter = globalNullPtrPtrByKind.emplace(reference->kind, globalPtrLE).first;
  }
  auto halfProtectedPtrLE = LLVMBuildLoad(builder, iter->second, "halfProtectedPtr");
  return kindStructs->makeWrapperPtr(FL(), functionState, builder, reference, halfProtectedPtrLE);
}

void HybridGenerationalMemory::addToUndeadCycle(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    WrapperPtrLE uncastedObjWrapperPtrLE) {
  buildFlare(FL(), globalState, functionState, builder, "Adding to undead cycle!");

  auto anyRefMT = globalState->metalCache->getReference(Ownership::OWN, Location::YONDER, anyMT);
  auto anyRefLT = globalState->getRegion(anyMT)->translateType(anyRefMT);

  auto objAsAnyWrapperPtrLE =
      kindStructs->makeWrapperPtr(
          FL(), functionState, builder, anyRefMT,
          LLVMBuildPointerCast(builder, uncastedObjWrapperPtrLE.refLE, anyRefLT, "objAsAnyWrapperPtr"));

  auto undeadCycleHeadNodePtrLE =
      LLVMBuildLoad(builder, undeadCycleHeadNodePtrPtrLE, "undeadCycleHeadNodePtr");
//  buildFlare(FL(), globalState, functionState, builder, "cycle head: ", ptrToIntLE(globalState, builder, undeadCycleHeadNodePtrLE));
  auto undeadCycleHeadNodeNextPtrPtrLE =
      LLVMBuildStructGEP(builder, undeadCycleHeadNodePtrLE, 0, "undeadCycleHeadNodeNextPtrPtr");
  auto undeadCycleHeadNodeNextPtrLE =
      LLVMBuildLoad(builder, undeadCycleHeadNodeNextPtrPtrLE, "undeadCycleHeadNodeNextPtr");
//  buildFlare(FL(), globalState, functionState, builder, "cycle head next: ", ptrToIntLE(globalState, builder, undeadCycleHeadNodeNextPtrLE));

  // First, make the new node. Point it at the existing head's next node.
  auto newNodeI8PtrLE =
      callMalloc(
          globalState,
          builder,
          constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, undeadCycleNodeLT)));
  auto newNodePtrLE =
      LLVMBuildPointerCast(
          builder, newNodeI8PtrLE, LLVMPointerType(undeadCycleNodeLT, 0),
          "newNodePtr");
//  buildFlare(FL(), globalState, functionState, builder, "new node: ", ptrToIntLE(globalState, builder, newNodePtrLE));
  // Make it point at the existing head's next node.
  auto newNodeNextPtrPtrLE =
      LLVMBuildStructGEP(builder, newNodePtrLE, 0, "newNodeNextPtrPtr");
  LLVMBuildStore(builder, undeadCycleHeadNodeNextPtrLE, newNodeNextPtrPtrLE);
//  buildFlare(FL(), globalState, functionState, builder, "new node's next is now: ", ptrToIntLE(globalState, builder, undeadCycleHeadNodeNextPtrLE));
  // Point the obj field at the now undead object.
  auto newNodeObjPtrPtrLE =
      LLVMBuildStructGEP(builder, newNodePtrLE, 1, "newNodeObjPtrPtr");
  LLVMBuildStore(builder, objAsAnyWrapperPtrLE.refLE, newNodeObjPtrPtrLE);

  // The previous head will still be the head, but it will now point at our new node.
  LLVMBuildStore(builder, newNodePtrLE, undeadCycleHeadNodeNextPtrPtrLE);
//  buildFlare(FL(), globalState, functionState, builder, "cycle head's next is now: ", ptrToIntLE(globalState, builder, newNodePtrLE));
}

LLVMValueRef HybridGenerationalMemory::implodeConcreteHandle(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef) {
  auto weakFatPtrLE =
      kindStructs->makeWeakFatPtr(
          weakRefM,
          globalState->getRegion(weakRefM)->checkValidReference(FL(), functionState, builder, weakRefM, weakRef));

  auto targetGenLE = getTargetGenFromWeakRef(builder, kindStructs, weakRefM->kind, weakFatPtrLE);

  auto innerRefLE = fatWeaks.getInnerRefFromWeakRef(functionState, builder, weakRefM, weakFatPtrLE);

  // I don't think anything inside a weak ref is going to be more than 8 bytes...
  // if it is, we got problems below.
  assert(LLVMABISizeOfType(globalState->dataLayout, LLVMTypeOf(innerRefLE)) == 8);

  buildFlare(FL(), globalState, functionState, builder, "bork ", targetGenLE);

  auto handleLE =
      ::implodeConcreteHandle(
          globalState,
          builder,
          globalState->getConcreteHandleStruct(),
          constI64LE(globalState, externHandleRegionId),
          LLVMBuildPtrToInt(builder, innerRefLE, LLVMInt64TypeInContext(globalState->context), "objPtrA"),
          targetGenLE,
          constI32LE(globalState, externHandleGenOffset));

  return handleLE;
}

LLVMValueRef HybridGenerationalMemory::implodeInterfaceHandle(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* weakRefM,
    Ref weakRef) {
  auto weakFatPtrLE =
      kindStructs->makeWeakFatPtr(
          weakRefM,
          globalState->getRegion(weakRefM)->checkValidReference(FL(), functionState, builder, weakRefM, weakRef));

  auto targetGenLE = getTargetGenFromWeakRef(builder, kindStructs, weakRefM->kind, weakFatPtrLE);

  LLVMValueRef itablePtrLE = nullptr, objPtrLE = nullptr;
  std::tie(itablePtrLE, objPtrLE) =
      explodeWeakInterfaceRef(
          globalState, functionState, builder, kindStructs, &fatWeaks, kindStructs, weakRefM, weakRef,
          [this, functionState, builder, weakRefM](WeakFatPtrLE weakFatPtrLE) {
            return weakInterfaceRefToWeakStructRef(
                functionState, builder, weakRefM, weakFatPtrLE);
          });

  // The object might not exist, so skip the check.
  auto interfaceFatPtrLE =
      kindStructs->makeInterfaceFatPtrWithoutChecking(
          FL(), functionState, builder,
          weakRefM, // It's still conceptually weak even though its not in a weak pointer.
          fatWeaks.getInnerRefFromWeakRef(functionState, builder, weakRefM, weakFatPtrLE));
  auto controlBlockPtrLE =
      kindStructs->getControlBlockPtrWithoutChecking(
          FL(), functionState, builder, weakRefM->kind, interfaceFatPtrLE);

  auto handleLE =
      ::implodeInterfaceHandle(
          globalState,
          builder,
          globalState->getInterfaceHandleStruct(),
          constI64LE(globalState, externHandleRegionId),
          LLVMBuildPtrToInt(builder, itablePtrLE, LLVMInt64TypeInContext(globalState->context), "itablePtr"),
          LLVMBuildPtrToInt(builder, controlBlockPtrLE.refLE, LLVMInt64TypeInContext(globalState->context), "objPtrB"),
          targetGenLE,
          constI32LE(globalState, externHandleGenOffset));

  return handleLE;
}
