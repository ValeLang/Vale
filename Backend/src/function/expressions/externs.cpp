#include <iostream>
#include <utils/branch.h>
#include "../boundary.h"
#include "shared/shared.h"
#include "shared/string.h"
#include "determinism/determinism.h"
#include "../../region/common/controlblock.h"
#include "../../region/common/heap.h"
#include "../../region/linear/linear.h"
#include <region/common/migration.h>

#include "../../translatetype.h"

#include "../expression.h"


Ref buildResultOrEarlyReturnOfNever(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    Ref resultRef) {
  if (prototype->returnType->kind == globalState->metalCache->never) {
    LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
    return wrap(globalState->getRegion(globalState->metalCache->neverRef), globalState->metalCache->neverRef, globalState->neverPtr);
  } else {
    if (prototype->returnType == globalState->metalCache->voidRef) {
      return makeVoidRef(globalState);
    } else {
      return resultRef;
    }
  }
}

void replayExportCalls(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder) {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  buildBoolyWhile(
      globalState, functionState->containingFuncL, builder,
      [globalState, functionState, voidLT](LLVMBuilderRef builder) -> LLVMValueRef {
        buildFlare(FL(), globalState, functionState, builder);
        auto replayerFuncPtrLE =
            globalState->determinism->buildGetMaybeReplayedFuncForNextExportCall(builder);
        assert(
            LLVMTypeOf(replayerFuncPtrLE) ==
            LLVMPointerType(LLVMFunctionType(voidLT, nullptr, 0, false), 0));
        auto replayerFuncPtrAsI64LE = ptrToIntLE(globalState, builder, replayerFuncPtrLE);
        auto replayerFuncPtrNotNullLE =
            LLVMBuildICmp(
                builder, LLVMIntNE, replayerFuncPtrAsI64LE, constI64LE(globalState, 0), "");
        buildIf(
            globalState, functionState->containingFuncL, builder, replayerFuncPtrNotNullLE,
            [replayerFuncPtrLE, voidLT](LLVMBuilderRef thenBuilder) {
              buildSimpleCall(thenBuilder, replayerFuncPtrLE, LLVMFunctionType(voidLT, nullptr, 0, false), {});
            });
        return replayerFuncPtrNotNullLE;
      });
}

Ref buildCallOrSideCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    const std::vector<Ref>& valeArgRefs) {
  auto int8PtrLT = LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);

  auto hostArgsLE = std::vector<LLVMValueRef>{};
  hostArgsLE.reserve(valeArgRefs.size() + 1);

  auto sizeArgsLE = std::vector<LLVMValueRef>{};
  sizeArgsLE.reserve(valeArgRefs.size() + 1);

  for (int i = 0; i < valeArgRefs.size(); i++) {
    auto valeArgRefMT = prototype->params[i];
    auto hostArgRefMT =
        (valeArgRefMT->ownership == Ownership::SHARE ?
         globalState->linearRegion->linearizeReference(valeArgRefMT) :
         valeArgRefMT);

    auto valeRegionInstanceRef =
        // At some point, look up the actual region instance, perhaps from the FunctionState?
        globalState->getRegion(valeArgRefMT)->createRegionInstanceLocal(functionState, builder);

    auto hostRegionInstanceRef =
        globalState->linearRegion->createRegionInstanceLocal(
            functionState, builder, constI1LE(globalState, 0), constI64LE(globalState, 0));

    auto valeArg = valeArgRefs[i];
    auto[hostArgRefLE, argSizeLE] =
    sendValeObjectIntoHostAndDealias(
        globalState, functionState, builder, valeRegionInstanceRef, hostRegionInstanceRef, valeArgRefMT, hostArgRefMT,
        valeArg);
    if (typeNeedsPointerParameter(globalState, valeArgRefMT)) {
      auto hostArgRefLT = globalState->getRegion(valeArgRefMT)->getExternalType(valeArgRefMT);
      assert(LLVMGetTypeKind(hostArgRefLT) != LLVMPointerTypeKind);
      hostArgsLE.push_back(makeBackendLocal(functionState, builder, hostArgRefLT, "ptrParamLocal", hostArgRefLE));
    } else {
      hostArgsLE.push_back(hostArgRefLE);
    }
    if (includeSizeParam(globalState, prototype, i)) {
      sizeArgsLE.push_back(argSizeLE);
    }
  }

  hostArgsLE.insert(hostArgsLE.end(), sizeArgsLE.begin(), sizeArgsLE.end());
  sizeArgsLE.clear();

  auto externFuncIter = globalState->externFunctions.find(prototype->name->name);
  assert(externFuncIter != globalState->externFunctions.end());
  auto externFuncL = externFuncIter->second;

  buildFlare(FL(), globalState, functionState, builder, "Suspending function ", functionState->containingFuncName);
  buildFlare(FL(), globalState, functionState, builder, "Calling extern function ", prototype->name->name);

  auto hostReturnRefLT = globalState->getRegion(prototype->returnType)->getExternalType(prototype->returnType);

  LLVMValueRef hostReturnLE = nullptr;
  if (typeNeedsPointerParameter(globalState, prototype->returnType)) {
    auto localPtrLE =
        makeBackendLocal(functionState, builder, hostReturnRefLT, "retOutParam", LLVMGetUndef(hostReturnRefLT));
    buildFlare(FL(), globalState, functionState, builder, "Return ptr! ", ptrToIntLE(globalState, builder, localPtrLE));
    hostArgsLE.insert(hostArgsLE.begin(), localPtrLE);

    if (globalState->opt->enableSideCalling) {
      auto sideStackI8PtrLE = LLVMBuildLoad2(builder, int8PtrLT, globalState->sideStack, "sideStack");
      auto resultLE =
          buildSideCall(
              globalState, builder, sideStackI8PtrLE, externFuncL, hostArgsLE);
      assert(LLVMTypeOf(resultLE) == LLVMVoidTypeInContext(globalState->context));
    } else {
      auto resultLE = buildMaybeNeverCall(globalState, builder, externFuncL, hostArgsLE);
      assert(LLVMTypeOf(resultLE) == LLVMVoidTypeInContext(globalState->context));
    }
    hostReturnLE = LLVMBuildLoad2(builder, hostReturnRefLT, localPtrLE, "hostReturn");
    buildFlare(FL(), globalState, functionState, builder, "Loaded the return! ",
        LLVMABISizeOfType(globalState->dataLayout, LLVMTypeOf(hostReturnLE)));
  } else {
    if (globalState->opt->enableSideCalling) {
      auto sideStackI8PtrLE = LLVMBuildLoad2(builder, int8PtrLT, globalState->sideStack, "sideStack");
      hostReturnLE =
          buildSideCall(globalState, builder, sideStackI8PtrLE, externFuncL, hostArgsLE);
    } else {
      hostReturnLE =
          buildMaybeNeverCall(globalState, builder, externFuncL, hostArgsLE);
    }
  }

  buildFlare(FL(), globalState, functionState, builder, "Done calling function ", prototype->name->name);
  buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncName);


  buildFlare(FL(), globalState, functionState, builder);

  auto valeReturnRefMT = prototype->returnType;
  auto hostReturnMT =
      (valeReturnRefMT->ownership == Ownership::SHARE ?
       globalState->linearRegion->linearizeReference(valeReturnRefMT) :
       valeReturnRefMT);

  auto valeRegionInstanceRef =
      // At some point, look up the actual region instance, perhaps from the FunctionState?
      globalState->getRegion(valeReturnRefMT)->createRegionInstanceLocal(functionState, builder);

  auto hostRegionInstanceRef =
      globalState->linearRegion->createRegionInstanceLocal(
          functionState, builder, constI1LE(globalState, 0), constI64LE(globalState, 0));

  auto valeReturnRef =
      receiveHostObjectIntoVale(
          globalState, functionState, builder, hostRegionInstanceRef, valeRegionInstanceRef, hostReturnMT, valeReturnRefMT, hostReturnLE);

  // dont we have to free here too

  return valeReturnRef;
}

// Three options:
// - Call the function normally
// - Call the function and record its return value
// - Just replay the return value from the file, dont call it
Ref replayReturnOrCallAndOrRecord(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    const std::vector<Ref>& args,
    std::function<Ref(LLVMBuilderRef)> callUserExtern) {
  auto valeReturnRefMT = prototype->returnType;

  if (!globalState->opt->enableReplaying) {
    // This is the simple case, no replaying or recording or anything.

    auto valeReturnRef = callUserExtern(builder);
    return buildResultOrEarlyReturnOfNever(globalState, functionState, builder, prototype, valeReturnRef);
  } else {
    // If we're here, replaying is enabled.
    // We might be replaying, recording, or neither, depending on the flags supplied at runtime.

    LLVMValueRef recordingModeLE = globalState->determinism->buildGetMode(builder);
    Ref isNormalRunRef =
        wrap(
            globalState->getRegion(globalState->metalCache->boolRef),
            globalState->metalCache->boolRef,
            LLVMBuildICmp(
                builder, LLVMIntNE, recordingModeLE,
                constI64LE(globalState, (int64_t)RecordingMode::NORMAL),
                "isNormalRun"));

    return buildIfElseV(
        globalState, functionState, builder, isNormalRunRef, valeReturnRefMT, valeReturnRefMT,
        [globalState, functionState, recordingModeLE, args, prototype, valeReturnRefMT, callUserExtern](
            LLVMBuilderRef outerThenBuilder) -> Ref {
          auto isRecordingRef =
              wrap(
                  globalState->getRegion(globalState->metalCache->boolRef),
                  globalState->metalCache->boolRef,
                  LLVMBuildICmp(
                      outerThenBuilder, LLVMIntEQ, recordingModeLE,
                      constI64LE(globalState, (int64_t) RecordingMode::RECORDING), "isRecording"));
          auto valeReturnLT = globalState->getRegion(valeReturnRefMT)->getExternalType(valeReturnRefMT);
          return buildIfElseV(
              globalState, functionState, outerThenBuilder, isRecordingRef, valeReturnRefMT, valeReturnRefMT,
              [globalState, functionState, prototype, args, valeReturnRefMT, callUserExtern](
                  LLVMBuilderRef builder) -> Ref {
                // If we get here, we're recording.

                // write that we're calling this particular function
                globalState->determinism->buildWriteCallBeginToFile(builder, prototype);

                buildFlare(FL(), globalState, functionState, builder, "Recording arguments for ", prototype->name->name);
                // write the argument to the file
                for (int i = 0; i < args.size(); i++) {
                  auto valeArgRefMT = prototype->params[i];
                  auto argLE =
                      globalState->getRegion(prototype->params[i])
                          ->checkValidReference(FL(), functionState, builder, false, prototype->params[i], args[i]);
                  if (valeArgRefMT->ownership == Ownership::SHARE) {
                    // Don't need to:
                    //   globalState->determinism->buildWriteValueToFile(builder, argLE);
                    // because we dont need these values in the recording.
                  } else {
                    globalState->determinism->buildWriteRefToFile(builder, argLE);
                  }
                }

                buildFlare(FL(), globalState, functionState, builder, "Calling extern for ", prototype->name->name);
                auto valeReturnRef = callUserExtern(builder);

                // Signal that we're ending the call, rather than having some exports call into us.
                globalState->determinism->buildRecordCallEnd(builder, prototype);

                buildFlare(FL(), globalState, functionState, builder, "Recording return for ", prototype->name->name);

                // write to the file what we received from C
                if (valeReturnRefMT->ownership == Ownership::SHARE) {
                  globalState->determinism->buildWriteValueToFile(
                      functionState, builder, prototype->returnType, valeReturnRef);
                } else {
                  auto returnLE =
                      globalState->getRegion(prototype->returnType)
                          ->encryptAndSendFamiliarReference(
                              functionState, builder, prototype->returnType, valeReturnRef);
                  globalState->determinism->buildWriteRefToFile(builder, returnLE);
                }

                return buildResultOrEarlyReturnOfNever(
                    globalState, functionState, builder, prototype, valeReturnRef);
              },
              [globalState, functionState, args, prototype, callUserExtern, valeReturnRefMT](LLVMBuilderRef builder) -> Ref {
                // If we get here, we're replaying.

                // should assert that we're calling the same function as last time
                globalState->determinism->buildMatchCallFromRecordingFile(functionState, builder, prototype);

                bool whitelisted = false;
                auto thisModuleReplayWhitelistedExternsIter =
                    globalState->opt->projectNameToReplayWhitelistedExterns.find(
                        prototype->name->packageCoord->projectName);
                if (thisModuleReplayWhitelistedExternsIter !=
                    globalState->opt->projectNameToReplayWhitelistedExterns.end()) {
                  auto thisModuleReplayWhitelistedExterns =
                      thisModuleReplayWhitelistedExternsIter->second;
                  if (thisModuleReplayWhitelistedExterns.find(prototype->name->name) !=
                      thisModuleReplayWhitelistedExterns.end()) {
                    whitelisted = true;
                  }
                }

                if (whitelisted) {
                  buildFlare(FL(), globalState, functionState, builder, "Allowing whitelisted call for ", prototype->name->name);
                  auto valeReturnRef = callUserExtern(builder);
                  // Ignore the return value, we'll still be using the one from the file.
                  // Later on, we'll add an exception for opaque types here.
                } else {
                  buildFlare(FL(), globalState, functionState, builder, "Skipping call for ", prototype->name->name);
                  // Dealias all the incoming arguments, we don't care about them when we're not
                  // actually calling that extern function.
                  for (int i = 0; i < args.size(); i++) {
                    auto valeArgRefMT = prototype->params[i];
                    if (valeArgRefMT->ownership == Ownership::SHARE) {
                      globalState->getRegion(valeArgRefMT)->dealias(FL(), functionState, builder, valeArgRefMT, args[i]);
                    } else {
                      // read from the file, add mapping to the hash map
                      auto argLE =
                          globalState->getRegion(valeArgRefMT)
                              ->checkValidReference(FL(), functionState, builder, false, valeArgRefMT, args[i]);
                      auto recordedRefLE =
                          globalState->determinism->buildMapRefFromRecordingFile(builder, valeArgRefMT);
                      assert(false);
                    }
                  }
                }

                buildFlare(FL(), globalState, functionState, builder, "Replaying export calls for ", prototype->name->name);
                replayExportCalls(globalState, functionState, builder);

                // above, we consumed a marker that said we're ending this current extern call.

                buildFlare(FL(), globalState, functionState, builder, "Replaying return value for ", prototype->name->name);
                Ref valeReturnRef =
                    (valeReturnRefMT->ownership == Ownership::SHARE ?
                     globalState->determinism->buildReadValueFromFile(functionState, builder, valeReturnRefMT) :
                     globalState->determinism->buildMapRefFromRecordingFile(builder, valeReturnRefMT));
//                Ref valeReturnRef =
//                    wrap(globalState->getRegion(valeReturnRefMT), valeReturnRefMT, valeReturnRefLE);

                return buildResultOrEarlyReturnOfNever(
                    globalState, functionState, builder, prototype, valeReturnRef);
              });
        },
        [globalState, functionState, prototype, callUserExtern](LLVMBuilderRef elseBuilder) -> Ref {
          auto valeReturnRef = callUserExtern(elseBuilder);
          return buildResultOrEarlyReturnOfNever(globalState, functionState, elseBuilder, prototype, valeReturnRef);
        });
  }
}



Ref buildExternCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    const std::vector<Ref>& args) {
  if (prototype->name->name == "__vbi_addI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildAdd(builder, leftLE, rightLE,"add");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_multiplyI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto resultIntLE = LLVMBuildMul(builder, leftLE, rightLE, "mul");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultIntLE);
  } else if (prototype->name->name == "__vbi_subtractI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto resultIntLE = LLVMBuildSub(builder, leftLE, rightLE, "diff");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultIntLE);
  } else if (prototype->name->name == "__vbi_lessThanI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSLT, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_greaterThanI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSGT, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_lessThanFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildFCmp(builder, LLVMRealOLT, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_greaterThanFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildFCmp(builder, LLVMRealOGT, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_greaterThanOrEqI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSGE, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_lessThanOrEqI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSLE, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_eqI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntEQ, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_modI32") {
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    assert(args.size() == 2);
    auto result = LLVMBuildSRem( builder, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_divideI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildSDiv(builder, leftLE, rightLE,"add");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_addI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildAdd(builder, leftLE, rightLE,"add");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_multiplyI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto resultIntLE = LLVMBuildMul(builder, leftLE, rightLE, "mul");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultIntLE);
  } else if (prototype->name->name == "__vbi_subtractI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto resultIntLE = LLVMBuildSub(builder, leftLE, rightLE, "diff");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultIntLE);
  } else if (prototype->name->name == "__vbi_lessThanI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSLT, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_greaterThanI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSGT, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_greaterThanOrEqI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSGE, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_lessThanOrEqI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSLE, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_eqI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntEQ, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_modI64") {
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    assert(args.size() == 2);
    auto result = LLVMBuildSRem( builder, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_divideI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildSDiv(builder, leftLE, rightLE,"add");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_divideFloatFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildFDiv(builder, leftLE, rightLE,"divided");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_multiplyFloatFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildFMul(builder, leftLE, rightLE,"multiplied");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_subtractFloatFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildFSub(builder, leftLE, rightLE,"subtracted");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_negateFloat") {
    assert(args.size() == 1);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto result = LLVMBuildFNeg(builder, leftLE, "negated");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_strLength") {
    assert(args.size() == 1);

    auto strRegionInstanceRef =
        // At some point, look up the actual region instance, perhaps from the FunctionState?
        globalState->getRegion(globalState->metalCache->strRef)
            ->createRegionInstanceLocal(functionState, builder);

    auto resultLenLE = globalState->getRegion(globalState->metalCache->strRef)->getStringLen(functionState, builder, strRegionInstanceRef, args[0]);
    globalState->getRegion(globalState->metalCache->strRef)
        ->dealias(FL(), functionState, builder, globalState->metalCache->strRef, args[0]);
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultLenLE);
  } else if (prototype->name->name == "__vbi_addFloatFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildFAdd(builder, leftLE, rightLE, "add");
    return wrap(globalState->getRegion(globalState->metalCache->floatRef), globalState->metalCache->floatRef, result);
  } else if (prototype->name->name == "__vbi_panic") {
    buildPrintToStderr(globalState, builder, "(panic)\n");
    // See MPESC for status codes
    auto exitCodeLE = makeConstIntExpr(functionState, builder, LLVMInt64TypeInContext(globalState->context), 1);
    globalState->externs->exit.call(builder, {exitCodeLE}, "");
    LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
    return wrap(globalState->getRegion(globalState->metalCache->neverRef), globalState->metalCache->neverRef, globalState->neverPtr);
  } else if (prototype->name->name == "__vbi_getch") {
    auto resultIntLE = globalState->externs->getch.call(builder, {}, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultIntLE);
  } else if (prototype->name->name == "__vbi_eqFloatFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildFCmp(builder, LLVMRealOEQ, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_eqBoolBool") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntEQ, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_not") {
    assert(args.size() == 1);
    auto result = LLVMBuildNot(
        builder,
        checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]),
        "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_and") {
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    assert(args.size() == 2);
    auto result = LLVMBuildAnd( builder, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__vbi_or") {
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, true, prototype->params[1], args[1]);
    assert(args.size() == 2);
    auto result = LLVMBuildOr( builder, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else {
    return replayReturnOrCallAndOrRecord(
        globalState, functionState, builder, prototype, args,
        [globalState, functionState, prototype, args](LLVMBuilderRef builderWhenNotReplaying) {
          return buildCallOrSideCall(globalState, functionState, builderWhenNotReplaying, prototype, args);
        });
  }
  assert(false);
}

Ref translateExternCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    ExternCall* call) {
  auto name = call->function->name->name;
  auto params = call->function->params;
  std::vector<Ref> args;
  assert(call->argExprs.size() == call->argTypes.size());
  for (int i = 0; i < call->argExprs.size(); i++) {
    args.emplace_back(
        translateExpression(globalState, functionState, blockState, builder, call->argExprs[i]));
  }
  return buildExternCall(globalState, functionState, builder, call->function, args);
}
