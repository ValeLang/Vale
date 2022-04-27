#include <function/expressions/expressions.h>
#include "determinism.h"

static const std::string VALE_REPLAY_FLAG = "--vale_replay";
static const std::string VALE_RECORD_FLAG = "--vale_record";

LLVMValueRef Determinism::buildCallToWriteCallToFile(GlobalState* globalState, LLVMBuilderRef builder, Prototype* prototype) {
  buildCall(
      builder,
      writeCallToFileLE,
      { globalState->getOrMakeStringConstant(prototype->name->name) });
}

LLVMValueRef Determinism::buildCallToGetNextExportCallString(GlobalState* globalState, LLVMBuilderRef builder) {
  buildCall(builder, getNextExportCallStringLE, {});
}


std::tuple<LLVMValueRef, LLVMValueRef>
Determinism::buildCallToStartDeterministicMode(
    GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef mainArgsLE, LLVMValueRef argcLE) {

//  buildCall(thenBuilder, globalState->externs->startDeterministicMode, {
//      LLVMBuildLoad(thenBuilder, mainArgsLE, "firstArg")
//  });
}


LLVMValueRef Determinism::makeFuncToWriteCallToFile(GlobalState* globalState, LLVMBuilderRef builder, Prototype* prototype) {

}
LLVMValueRef Determinism::makeFuncToGetNextExportCallString(GlobalState* globalState, LLVMBuilderRef builder) {

}
LLVMValueRef Determinism::makeFuncToStartDeterministicMode(
    GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef argvLE, LLVMValueRef argcLE) {

  auto prototype =
      globalState->metalCache->getPrototype(
          globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__vale_startDeterminism"),
          globalState->metalCache->i64Ref,
          {
            globalState->metalCache->strRef
          });
  declareAndDefineExtraFunction(
      globalState, prototype, "__vale_startDeterminism",
      [globalState](FunctionState* functionState, LLVMBuilderRef builder) {

        auto isReplayingLE =
            buildCall(builder, globalState->externs->strncmp, {
                globalState->getOrMakeStringConstant(VALE_REPLAY_FLAG),
                LLVMBuildLoad(builder, mainArgsLE, "firstArg"),
                constI64LE(globalState, VALE_REPLAY_FLAG.size())
            });
        auto isRecordingLE =
            buildCall(builder, globalState->externs->strncmp, {
                globalState->getOrMakeStringConstant(VALE_RECORD_FLAG),
                LLVMBuildLoad(builder, mainArgsLE, "firstArg"),
                constI64LE(globalState, VALE_RECORD_FLAG.size())
            });
        auto condLE = LLVMBuildOr(builder, isReplayingLE, isRecordingLE, "recordingOrReplaying");
        buildIf(
            globalState, entryFunctionL, builder, condLE,
            [globalState, entryFunctionL, mainArgsLE](LLVMBuilderRef thenBuilder){
              globalState->determinism->startDeterministicMode(
                  globalState,
                  thenBuilder,
                  LLVMBuildLoad(thenBuilder, mainArgsLE, "firstArg"));

              assert(false); // impl

//        set up that global to be extern linked
//        set up startDeterministicMode
//        write the c code that opens the file
//
//        auto workedLE =
//            LLVMBuildICmp(
//                thenBuilder,
//                LLVMIntNE,
//                LLVMBuildLoad(thenBuilder, globalState->recordingModePtrLE.value(), "isRecording"),
//                constI64LE(globalState, 0),
//                "deterministicStarted");
//        buildAssert(globalState, entryFunctionL, thenBuilder, workedLE, "Deterministic mode failed to start!");
            });

      });
  return prototype;
}
