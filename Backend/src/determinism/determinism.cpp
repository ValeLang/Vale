#include <function/expressions/expressions.h>
#include <region/rcimm/rcimm.h>
#include <region/linear/linear.h>
#include <utils/definefunction.h>
#include <utils/branch.h>
#include <utils/flags.h>
#include <utils/counters.h>
#include <function/boundary.h>
#include "determinism.h"

static const uint64_t RECORDING_FILE_CONSTANT = 0x4a1e133713371337ULL;

const std::string VALE_REPLAY_FLAG = "--vale_replay";
const std::string VALE_RECORD_FLAG = "--vale_record";

static const std::string replayerFuncPrefix = "__vale_replayer__";
static const std::string maybeStartDeterministicModeFuncName = "__vale_determinism_maybe_start";
static const std::string startRecordingFuncName = "__vale_determinism_start_recording";
static const std::string startReplayingFuncName = "__vale_determinism_start_replaying";
static const std::string writeCallBeginToFileFuncName = "__vale_determinism_record_call_begin";
static const std::string writeRefToFileFuncName = "__vale_determinism_record_ref";
//static const std::string writeValueToFileFuncName = "__vale_determinism_record_value";
static const std::string recordCallEndFuncName = "__vale_determinism_record_call_end";
static const std::string matchCallFromRecordingFileFuncName = "__vale_determinism_replay_call_begin";
static const std::string matchRefFromRecordingFileFuncName = "__vale_determinism_replay_match_ref";
//static const std::string readValueFromFileFuncName = "__vale_determinism_replay_read_value";
//static const std::string getNextExportCallStringFuncName = "__vale_determinism_replay_get_next_export_call";
static const std::string getMaybeReplayerFuncForNextExportNameFuncName = "__vale_determinism_get_maybe_replayer_func_for_next_export";

static const std::string replayerMapName = "__vale_export_func_name_to_replayer_func_map";


LLVMValueRef calcPaddedStrLen(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthNotIncludingNullTerminatorLE) {
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int64Size = LLVMABISizeOfType(globalState->dataLayout, int64LT);

  auto lengthIncludingNullTerminatorLE =
      LLVMBuildAdd(builder, lengthNotIncludingNullTerminatorLE, constI64LE(globalState, 1), "");
  auto lengthIncludingNullTerminatorAndPaddingLE =
      roundUp(
          globalState, builder, int64Size, lengthIncludingNullTerminatorLE);
  return lengthIncludingNullTerminatorAndPaddingLE;
}

LLVMValueRef addFunction(
    GlobalState* globalState,
    const std::string& name,
    LLVMTypeRef returnLT,
    std::vector<LLVMTypeRef> argsLT) {
  auto functionLT = LLVMFunctionType(returnLT, argsLT.data(), argsLT.size(), false);
  auto functionLF = LLVMAddFunction(globalState->mod, name.c_str(), functionLT);
  return functionLF;
}

LLVMTypeRef makeReplayerFuncLT(GlobalState* globalState) {
  // No params, it gets the FILE* from a global / thread local.
  return LLVMFunctionType(LLVMVoidTypeInContext(globalState->context), nullptr, 0, false);
}

Determinism::Determinism(GlobalState* globalState_) :
    globalState(globalState_),
    functionsMap(
        globalState->addressNumberer->makeHasher<Prototype*>(),
        AddressEquator<Prototype*>{}),
    functionsMapLT(
        globalState,
        LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0),
        makeReplayerFuncLT(globalState),
        makeEmptyStructType(globalState),
        makeEmptyStructType(globalState),
        globalState->externs->strHasherCallLF,
        globalState->externs->strEquatorCallLF),
    finalizedExportNameToReplayerFunctionMapGlobalLE{} {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrPtrLT = LLVMPointerType(int8PtrLT, 0);

  fileDescriptorPtrGlobalLE =
      LLVMAddGlobal(globalState->mod, LLVMInt64TypeInContext(globalState->context), "__vale_determinism_file");
  LLVMSetLinkage(fileDescriptorPtrGlobalLE, LLVMExternalLinkage);
  LLVMSetInitializer(fileDescriptorPtrGlobalLE, LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 0, false));

  maybeStartDeterministicModeLF =
      addFunction(globalState, maybeStartDeterministicModeFuncName, int64LT, {int64LT, int8PtrPtrLT});
  writeCallBeginToFileLF =
      addFunction(globalState, writeCallBeginToFileFuncName, voidLT, {int64LT, int8PtrLT});
  writeRefToFileLF =
      addFunction(globalState, writeRefToFileFuncName, int8PtrLT, {});
//  writeValueToFileLF =
//      addFunction(globalState, writeValueToFileFuncName, voidLT, {int64LT, int8PtrLT});
  recordCallEndLF =
      addFunction(globalState, recordCallEndFuncName, voidLT, {int64LT, int8PtrLT});
  matchCallFromRecordingFileLF =
      addFunction(globalState, matchCallFromRecordingFileFuncName, voidLT, {int64LT, int8PtrLT});
  readAndMapFromFileLE =
      addFunction(globalState, matchRefFromRecordingFileFuncName, voidLT, {int64LT, int8PtrLT});
//  readValueFromFileLF =
//      addFunction(globalState, readValueFromFileFuncName, voidLT, {int64LT, int8PtrLT});
//  getNextExportCallStringLF =
//      addFunction(globalState, getNextExportCallStringFuncName, int8PtrLT, {});
  getMaybeReplayerFuncForNextExportNameLF =
      addFunction(globalState, getMaybeReplayerFuncForNextExportNameFuncName, voidLT, {});

  makeFuncToMaybeStartDeterministicMode();
  makeFuncToWriteCallBeginToFile();
//  makeFuncToGetNextExportCallString();
  makeFuncToWriteRefToFile();
//  makeFuncToWriteValueToFile();
  makeFuncToRecordCallEnd();
  makeFuncToMatchCallFromRecordingFile();
//  makeFuncToReadValueFromFile();
  makeFuncToMatchRefFromRecordingFile();
  makeFuncToStartReplaying();
  makeFuncToStartRecording();
  makeFuncToGetReplayerFuncForExportName();
}

void Determinism::registerFunction(Prototype* prototype) {
  assert(!finalizedExportNameToReplayerFunctionMapGlobalLE.has_value());
  functionsMap.add(prototype, std::make_tuple());
}

void Determinism::finalizeFunctionsMap() {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrPtrLT = LLVMPointerType(int8PtrLT, 0);
//
//  std::vector<LLVMTypeRef> nodeTypesLT = { int8PtrLT, LLVMPointerType(replayerFuncPtrLT, 0) };
//  auto nodeLT = LLVMStructTypeInContext(globalState->context, nodeTypesLT.data(), nodeTypesLT.size(), false);
//  auto nodeArrayLT = LLVMArrayType(nodeLT, functionsMap.capacity);

//  CppSimpleHashMap<Prototype*, std::tuple<>, AddressHasher<Prototype*>, AddressEquator<Prototype*>>
  assert(!finalizedExportNameToReplayerFunctionMapGlobalLE.has_value());

  auto mapLE =
      functionsMapLT.makeGlobalConstSimpleHashMap<Prototype*, std::tuple<>, AddressHasher<Prototype*>, AddressEquator<Prototype*>>(
          functionsMap,
          [this](Prototype* const& prototype, const std::tuple<>& value) -> std::tuple<LLVMValueRef, LLVMValueRef> {

            // TODO: Use exported names instead of regular function names, see URFNIEN.
            auto strLE = globalState->getOrMakeStringConstant(prototype->name->name);
            auto strLenLE = constI64LE(globalState, prototype->name->name.length());

            auto replayerFuncLE = makeFuncToReplayExportCall(prototype);
            return std::make_tuple(strLE, replayerFuncLE);
          },
          replayerMapName,
          makeEmptyStruct(globalState),
          makeEmptyStruct(globalState));
  finalizedExportNameToReplayerFunctionMapGlobalLE = std::optional(mapLE);
}

void Determinism::makeFuncToWriteCallBeginToFile() {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrPtrLT = LLVMPointerType(int8PtrLT, 0);
  defineFunctionBody(
      globalState,
      writeCallBeginToFileLF,
      voidLT,
      writeCallBeginToFileFuncName,
      [this](FunctionState* functionState, LLVMBuilderRef builder){
        auto nameLenLE = LLVMGetParam(functionState->containingFuncL, 0);
        auto nameI8PtrLE = LLVMGetParam(functionState->containingFuncL, 1);
        writeStringToFile(functionState, builder, nameLenLE, nameI8PtrLE);
      });
}

void Determinism::makeFuncToWriteRefToFile() {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int256LT = LLVMIntTypeInContext(globalState->context, 256);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrPtrLT = LLVMPointerType(int8PtrLT, 0);
  defineFunctionBody(
      globalState,
      writeCallBeginToFileLF,
      voidLT,
      writeCallBeginToFileFuncName,
      [this, int256LT](FunctionState* functionState, LLVMBuilderRef builder){
        auto refI256LE = LLVMGetParam(functionState->containingFuncL, 0);
        assert(LLVMTypeOf(refI256LE) == int256LT);
        writeI256ToFile(functionState, builder, refI256LE);
      });
}

void Determinism::makeFuncToRecordCallEnd() {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  defineFunctionBody(
      globalState, startRecordingLF, voidLT, startRecordingFuncName,
      [this](FunctionState *functionState, LLVMBuilderRef builder) {
        writeI64ToFile(functionState, builder, constI64LE(globalState, 0));
      });
}

void Determinism::makeFuncToMatchCallFromRecordingFile() {
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  defineFunctionBody(
      globalState, startRecordingLF, voidLT, startRecordingFuncName,
      [this, int8LT](FunctionState *functionState, LLVMBuilderRef builder) {
        auto replayingCalledFuncNameLenLE = LLVMGetParam(functionState->containingFuncL, 0);
        auto replayingCalledFuncNamePtrLE = LLVMGetParam(functionState->containingFuncL, 1);

        auto bufferPtrLE = LLVMBuildArrayAlloca(builder, int8LT, constI64LE(globalState, 1024), "");
        auto recordedCalledFuncNamePtrLE = ptrToVoidPtrLE(globalState, builder, bufferPtrLE);
        auto recordedCalledFuncNameLenLE = readI64FromFile(functionState, builder);
        auto recordedCalledFuncNameWithPaddingLenLE = calcPaddedStrLen(globalState, builder, recordedCalledFuncNameLenLE);
        readLimitedStringFromFile(functionState, builder, recordedCalledFuncNameWithPaddingLenLE, recordedCalledFuncNamePtrLE);

        auto lengthsDifferentLE = LLVMBuildICmp(builder, LLVMIntNE, recordedCalledFuncNameLenLE, replayingCalledFuncNameLenLE, "lengthsDifferent");
        buildIf(
            globalState, functionState->containingFuncL, builder, lengthsDifferentLE,
            [this, recordedCalledFuncNamePtrLE, replayingCalledFuncNamePtrLE](LLVMBuilderRef builder){
              buildPrint(globalState, builder, "Recording file expected a call to ");
              buildPrint(globalState, builder, recordedCalledFuncNamePtrLE);
              buildPrint(globalState, builder, " but this execution is calling ");
              buildPrint(globalState, builder, replayingCalledFuncNamePtrLE);
              buildPrint(globalState, builder, ", aborting!\n");
              buildCall(builder, globalState->externs->exit, {constI64LE(globalState, 1)});
            });
        auto stringsDifferentLE =
            buildCall(
                builder, functionState->containingFuncL,
                {recordedCalledFuncNameLenLE, recordedCalledFuncNamePtrLE, replayingCalledFuncNamePtrLE});
        buildIf(
            globalState, functionState->containingFuncL, builder, stringsDifferentLE,
            [this, recordedCalledFuncNamePtrLE, replayingCalledFuncNamePtrLE](LLVMBuilderRef builder){
              buildPrint(globalState, builder, "Recording file expected a call to ");
              buildPrint(globalState, builder, recordedCalledFuncNamePtrLE);
              buildPrint(globalState, builder, " but this execution is calling ");
              buildPrint(globalState, builder, replayingCalledFuncNamePtrLE);
              buildPrint(globalState, builder, ", aborting!\n");
              buildCall(builder, globalState->externs->exit, {constI64LE(globalState, 1)});
            });
      });
  // take in a name argument
  // compare it to the file. i think thats it.
}

//void Determinism::makeFuncToReadValueFromFile() {
//  implement
//  // make a function that will recursively read from a slab.
//  // we'll need to more or less reverse the linear writing thing.
//
//  auto voidLT = LLVMVoidTypeInContext(globalState->context);
//  defineFunctionBody(
//      globalState, startRecordingLF, a raw gen ref see URFPL, startRecordingFuncName,
//      [this](FunctionState* functionState, LLVMBuilderRef builder) {
//
//      });
//}

void Determinism::makeFuncToMatchRefFromRecordingFile() {
  // this happens when we get an export called.
  // it also happens when an extern returns a ref.
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int256LT = LLVMIntTypeInContext(globalState->context, 256);
  defineFunctionBody(
      globalState, startRecordingLF, int256LT, startRecordingFuncName,
      [this](FunctionState* functionState, LLVMBuilderRef builder) {
        // read 32 bytes from recording file.
        // does it need to be 32b? can it be 16? after all if we change regions the gen changes right?
        // for now, lets make it 16.
        auto fatRefLE = readI256FromFile(functionState, builder);
        // read from the recording file,
        // look it up in the map. if it doesnt exist, produce a null.
        // if it does exist, return it. (or pre-check it, w/e)
        assert(false); // map it to an existing universal ref and return it
      });
}

void Determinism::makeFuncToStartReplaying() {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  defineFunctionBody(
      globalState, startRecordingLF, voidLT, startRecordingFuncName,
      [this](FunctionState* functionState, LLVMBuilderRef builder){
        auto recordingFilenameLE = LLVMGetParam(functionState->containingFuncL, 0);
        auto fileLE = openFile(functionState, builder, recordingFilenameLE, FileOpenMode::READ);
        LLVMBuildStore(builder, fileLE, fileDescriptorPtrGlobalLE);
        writeI64ToFile(functionState, builder, constI64LE(globalState, RECORDING_FILE_CONSTANT));
      });
}

void Determinism::makeFuncToStartRecording() {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  defineFunctionBody(
      globalState, startRecordingLF, voidLT, startRecordingFuncName,
      [this](FunctionState* functionState, LLVMBuilderRef builder){
        auto recordingFilenameLE = LLVMGetParam(functionState->containingFuncL, 0);
        auto fileLE = openFile(functionState, builder, recordingFilenameLE, FileOpenMode::WRITE);
        LLVMBuildStore(builder, fileLE, fileDescriptorPtrGlobalLE);
        writeI64ToFile(functionState, builder, constI64LE(globalState, RECORDING_FILE_CONSTANT));
      });
}

void Determinism::writeBytesToFile(
    FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef sizeLE, LLVMValueRef i8PtrLE) {
  auto resultLE =
      buildCall(
          builder, globalState->externs->fwrite,
          {
              i8PtrLE,
              sizeLE,
              constI64LE(globalState, 1),
              LLVMBuildLoad(builder, fileDescriptorPtrGlobalLE, ""),
          });
  buildIf(
      globalState, functionState->containingFuncL, builder,
      LLVMBuildICmp(builder, LLVMIntULT, resultLE, constI64LE(globalState, 1), ""),
      [this](LLVMBuilderRef builder){
        buildPrint(globalState, builder, "Couldn't write to recording file.");
        buildCall(builder, globalState->externs->exit, {constI64LE(globalState, 1)});
      });
}

LLVMValueRef Determinism::openFile(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef pathI8PtrLE, FileOpenMode mode) {
  LLVMValueRef modeStrLE = nullptr;
  switch (mode) {
    case FileOpenMode::READ:
      modeStrLE = globalState->getOrMakeStringConstant("r");
      break;
    case FileOpenMode::WRITE:
      modeStrLE = globalState->getOrMakeStringConstant("w");
      break;
    default:
      assert(false);
  }
  auto fileLE = buildCall(builder, globalState->externs->fopen, {pathI8PtrLE, modeStrLE});
  auto fileAsI64LE = ptrToIntLE(globalState, builder, fileLE);
  buildIf(
      globalState, functionState->containingFuncL, builder,
      LLVMBuildICmp(builder, LLVMIntNE, fileAsI64LE, constI64LE(globalState, 0), ""),
      [this](LLVMBuilderRef builder){
        buildPrint(globalState, builder, "Couldn't open recording file.");
        buildCall(builder, globalState->externs->exit, {constI64LE(globalState, 1)});
      });
  return fileLE;
}

void Determinism::writeI64ToFile(
    FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef i64LE) {
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  assert(LLVMTypeOf(i64LE) == int64LT);
  auto i64PtrLE = makeBackendLocal(functionState, builder, int64LT, "", i64LE);
  writeBytesToFile(
      functionState,
      builder,
      constI64LE(globalState, LLVMABISizeOfType(globalState->dataLayout, int64LT)),
      ptrToVoidPtrLE(globalState, builder, i64PtrLE));
}

void Determinism::writeI256ToFile(
    FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef i256LE) {
  auto int256LT = LLVMIntTypeInContext(globalState->context, 256);
  assert(LLVMTypeOf(i256LE) == int256LT);
  auto i256PtrLE = makeBackendLocal(functionState, builder, int256LT, "", i256LE);
  writeBytesToFile(
      functionState,
      builder,
      constI256LEFromI64(globalState, LLVMABISizeOfType(globalState->dataLayout, int256LT)),
      ptrToVoidPtrLE(globalState, builder, i256PtrLE));
}

LLVMValueRef Determinism::readI64FromFile(
    FunctionState* functionState, LLVMBuilderRef builder) {
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int64Size = LLVMABISizeOfType(globalState->dataLayout, int64LT);

  auto i64PtrLE = makeBackendLocal(functionState, builder, int64LT, "", constI64LE(globalState, 0));

  auto resultLE =
      buildCall(
          builder, globalState->externs->fread,
          {
              ptrToVoidPtrLE(globalState, builder, i64PtrLE),
              constI64LE(globalState, int64Size),
              constI64LE(globalState, 1),
              LLVMBuildLoad(builder, fileDescriptorPtrGlobalLE, "")
          });
  buildIf(
      globalState, functionState->containingFuncL, builder,
      LLVMBuildICmp(builder, LLVMIntULT, resultLE, constI64LE(globalState, 1), ""),
      [this](LLVMBuilderRef builder){
        buildPrint(globalState, builder, "Couldn't read from recording file.");
        buildCall(builder, globalState->externs->exit, {constI64LE(globalState, 1)});
      });

  return LLVMBuildLoad(builder, i64PtrLE, "int64FromFile");
}

LLVMValueRef Determinism::readI256FromFile(
    FunctionState* functionState, LLVMBuilderRef builder) {
  auto int256LT = LLVMIntTypeInContext(globalState->context, 256);
  auto int256Size = LLVMABISizeOfType(globalState->dataLayout, int256LT);

  auto i256PtrLE = makeBackendLocal(functionState, builder, int256LT, "", constI256LEFromI64(globalState, 0));

  auto resultLE =
      buildCall(
          builder, globalState->externs->fread,
          {
              ptrToVoidPtrLE(globalState, builder, i256PtrLE),
              constI64LE(globalState, int256Size),
              constI64LE(globalState, 1),
              LLVMBuildLoad(builder, fileDescriptorPtrGlobalLE, "")
          });
  buildIf(
      globalState, functionState->containingFuncL, builder,
      LLVMBuildICmp(builder, LLVMIntULT, resultLE, constI64LE(globalState, 1), ""),
      [this](LLVMBuilderRef builder){
        buildPrint(globalState, builder, "Couldn't read from recording file.");
        buildCall(builder, globalState->externs->exit, {constI64LE(globalState, 1)});
      });

  return LLVMBuildLoad(builder, i256PtrLE, "int256FromFile");
}

LLVMValueRef calcNumTrailingZeroes(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthNotIncludingNullTerminatorLE) {
  auto lengthIncludingNullTerminatorAndPaddingLE =
      calcPaddedStrLen(globalState, builder, lengthNotIncludingNullTerminatorLE);
  auto numZeroesAtEndLE =
      LLVMBuildSub(
          builder,
          lengthIncludingNullTerminatorAndPaddingLE,
          lengthNotIncludingNullTerminatorLE,
          "");
  return numZeroesAtEndLE;
}

void Determinism::readLimitedStringFromFile(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef maxSizeLE,
    LLVMValueRef bufferPtrLE) {
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int64Size = LLVMABISizeOfType(globalState->dataLayout, int64LT);

  auto resultLE =
      buildCall(
          builder, globalState->externs->fread,
          {
              bufferPtrLE,
              maxSizeLE,
              constI64LE(globalState, 1),
              LLVMBuildLoad(builder, fileDescriptorPtrGlobalLE, "")
          });
  buildIf(
      globalState, functionState->containingFuncL, builder,
      LLVMBuildICmp(builder, LLVMIntULT, resultLE, constI64LE(globalState, 1), ""),
      [this](LLVMBuilderRef builder){
        buildPrint(globalState, builder, "Couldn't read from recording file.");
        buildCall(builder, globalState->externs->exit, {constI64LE(globalState, 1)});
      });
}

//LLVMValueRef Determinism::readStringFromFile(
//    FunctionState* functionState, LLVMBuilderRef builder) {
//  auto int64LT = LLVMInt64TypeInContext(globalState->context);
//  auto int64Size = LLVMABISizeOfType(globalState->dataLayout, int64LT);
//
//  auto i64PtrLE = makeBackendLocal(functionState, builder, int64LT, "", constI64LE(globalState, 0));
//
//  auto strLenLE = readI64FromFile(functionState, builder);
//  auto numZeroesLE = calcNumTrailingZeroes(globalState, builder, strLenLE);
//
//
//
//  auto resultLE =
//      buildCall(
//          builder, globalState->externs->fread,
//          {
//              ptrToVoidPtrLE(globalState, builder, i64PtrLE),
//              constI64LE(globalState, int64Size),
//              constI64LE(globalState, 1),
//              LLVMBuildLoad(builder, fileDescriptorPtrGlobalLE, "")
//          });
//  buildIf(
//      globalState, functionState->containingFuncL, builder,
//      LLVMBuildICmp(builder, LLVMIntULT, resultLE, constI64LE(globalState, 1), ""),
//      [this](LLVMBuilderRef builder){
//        buildPrint(globalState, builder, "Couldn't read from recording file.");
//        buildCall(builder, globalState->externs->exit, {constI64LE(globalState, 1)});
//      });
//
//  return i64PtrLE;
//}

void Determinism::writeStringToFile(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef lengthNotIncludingNullTerminatorLE,
    LLVMValueRef strLE) {
  auto int64LT = LLVMInt64TypeInContext(globalState->context);

  writeI64ToFile(functionState, builder, lengthNotIncludingNullTerminatorLE);

  writeBytesToFile(functionState, builder, lengthNotIncludingNullTerminatorLE, strLE);

  auto numZeroesAtEndLE = calcNumTrailingZeroes(globalState, builder, lengthNotIncludingNullTerminatorLE);

  auto zeroI64PtrLE = makeBackendLocal(functionState, builder, int64LT, "", constI64LE(globalState, 0));
  writeBytesToFile(functionState, builder, numZeroesAtEndLE, ptrToVoidPtrLE(globalState, builder, zeroI64PtrLE));
}

void Determinism::makeFuncToGetReplayerFuncForExportName() {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int64Size = LLVMABISizeOfType(globalState->dataLayout, int64LT);
  auto replayerFuncLT = makeReplayerFuncLT(globalState);

  defineFunctionBody(
      globalState,
      getMaybeReplayerFuncForNextExportNameLF,
      voidLT,
      getMaybeReplayerFuncForNextExportNameFuncName,
      [this, int8LT, replayerFuncLT](FunctionState* functionState, LLVMBuilderRef builder){
        auto bufferPtrLE = LLVMBuildArrayAlloca(builder, int8LT, constI64LE(globalState, 1024), "");
        auto bufferI8PtrLE = ptrToVoidPtrLE(globalState, builder, bufferPtrLE);
        auto strLenLE = readI64FromFile(functionState, builder);
        auto strWithPaddingLenLE = calcPaddedStrLen(globalState, builder, strLenLE);
        readLimitedStringFromFile(functionState, builder, strWithPaddingLenLE, bufferI8PtrLE);

        auto foundIndexLE = functionsMapLT.buildFindIndexOf(bufferI8PtrLE);
        auto notFoundLE = LLVMBuildICmp(builder, LLVMIntSLT, foundIndexLE, constI64LE(globalState, 0), "");
        auto resultLE =
          buildIfElse(
              globalState, functionState, builder, LLVMPointerType(replayerFuncLT, 0), notFoundLE,
              [this, replayerFuncLT](LLVMBuilderRef builder){
  //              buildPrint(globalState, builder, "Error: Replay contained a call to ");
  //              buildPrint(globalState, builder, bufferI8PtrLE);
  //              buildPrint(globalState, builder, " at this point, but that function doesn't exist anymore.\n");
  //              buildCall(builder, globalState->externs->exit, { constI64LE(globalState, 1) });
                return LLVMConstNull(replayerFuncLT);
              },
              [this, foundIndexLE](LLVMBuilderRef builder){
                return functionsMapLT.buildGetAtIndex(foundIndexLE);
              });
        LLVMBuildRet(builder, resultLE);
      });
}

void Determinism::makeFuncToMaybeStartDeterministicMode() {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrPtrLT = LLVMPointerType(int8PtrLT, 0);

  defineFunctionBody(
      globalState,
      maybeStartDeterministicModeLF,
      voidLT,
      maybeStartDeterministicModeFuncName,
      [this, int8PtrLT, int64LT](FunctionState* functionState, LLVMBuilderRef builder){
        auto mainArgsCountLE = LLVMGetParam(functionState->containingFuncL, 0);
        auto mainArgsLE = LLVMGetParam(functionState->containingFuncL, 1);
        auto numConsumedArgsLE =
            processFlag(
                globalState, functionState, builder, VALE_RECORD_FLAG, mainArgsCountLE, mainArgsLE,
                [this](LLVMBuilderRef builder, LLVMValueRef recordingFilenameStrLE){
                  buildStartRecording(builder, recordingFilenameStrLE);
                });

        // We're returning the number of args we've consumed.
        return buildIfElse(
            globalState, functionState, builder, int64LT,
            LLVMBuildICmp(builder, LLVMIntEQ, numConsumedArgsLE, constI64LE(globalState, 0), ""),
            [this, functionState, mainArgsCountLE, mainArgsLE](LLVMBuilderRef builder) {
              return processFlag(
                  globalState, functionState, builder, VALE_REPLAY_FLAG, mainArgsCountLE, mainArgsLE,
                  [this](LLVMBuilderRef builder, LLVMValueRef replayingFilenameStrLE) {
                    buildStartReplaying(builder, replayingFilenameStrLE);
                  });
            },
            [this](LLVMBuilderRef builder) {
              return constI64LE(globalState, 0);
            });
      });

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
}

void Determinism::buildStartReplaying(LLVMBuilderRef builder, LLVMValueRef recordingFilename) {
  assert(startReplayingLF);
  buildCall(builder, startReplayingLF, {recordingFilename});
}

void Determinism::buildStartRecording(LLVMBuilderRef builder, LLVMValueRef recordingFilename) {
  assert(startRecordingLF);
  buildCall(builder, startRecordingLF, {recordingFilename});
}

LLVMValueRef Determinism::buildWriteCallBeginToFile(LLVMBuilderRef builder, Prototype* prototype) {
  assert(writeCallBeginToFileLF);

  // TODO: Use exported names instead of regular function names, see URFNIEN.
  auto strLE = globalState->getOrMakeStringConstant(prototype->name->name);
  auto strLenLE = constI64LE(globalState, prototype->name->name.length());

  buildCall(builder, writeCallBeginToFileLF, {strLenLE, strLE});
}

//LLVMValueRef Determinism::buildGetNextExportCallString(LLVMBuilderRef builder) {
//  assert(getNextExportCallStringLF);
//  buildCall(builder, getNextExportCallStringLF, {});
//}

void Determinism::buildWriteValueToFile(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRefMT,
    Ref sourceRef) {
  assert(maybeStartDeterministicModeLF);

  // we'll need to memcpy from the temporary linear region into the file.
  // for sending into C thisll be easy.
  // however, when receiving into C, we might need to invoke it manually.
  // also, we may need to have it subtract the start from the pointer, so
  // itll be offset relative to the linear region begin.

  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto floatLT = LLVMDoubleTypeInContext(globalState->context);

  assert(sourceRefMT->ownership == Ownership::SHARE); // not implemented for owns
  auto hostRefMT = globalState->linearRegion->linearizeReference(sourceRefMT);

  auto sourceRefLE =
      globalState->getRegion(sourceRefMT)
          ->checkValidReference(FL(), functionState, builder, sourceRefMT, sourceRef);

  if (dynamic_cast<Int*>(sourceRefMT->kind)) {
    writeI64ToFile(functionState, builder, sourceRefLE);
  } else if (dynamic_cast<Bool*>(sourceRefMT->kind)) {
    auto boolAsI64LE = LLVMBuildZExt(builder, sourceRefLE, int64LT, "boolAsI64");
    writeI64ToFile(functionState, builder, boolAsI64LE);
  } else if (dynamic_cast<Float*>(sourceRefMT->kind)) {
    auto floatAsI64LE = LLVMBuildBitCast(builder, sourceRefLE, floatLT, "floatFromRecording");
    writeI64ToFile(functionState, builder, floatAsI64LE);
  } else if (dynamic_cast<StructKind*>(sourceRefMT->kind)) {

    auto valeRegionInstanceRef =
        // At some point, look up the actual region instance, perhaps from the FunctionState?
        globalState->getRegion(sourceRefMT)->createRegionInstanceLocal(functionState, builder);
    auto useOffsetsLE = constI1LE(globalState, 1);
    auto fileOffsetLE = LLVMBuildLoad(builder, fileOffsetPtrGlobalLE, "fileOffset");
    auto hostRegionInstanceRef =
        globalState->linearRegion->createRegionInstanceLocal(
            functionState, builder, useOffsetsLE, fileOffsetLE);

    LLVMValueRef hostRefLE = nullptr;
    LLVMValueRef sizeLE = nullptr;
    std::tie(hostRefLE, sizeLE) =
        sendValeObjectIntoHost(
            globalState, functionState, builder, valeRegionInstanceRef, hostRegionInstanceRef,
            sourceRefMT, hostRefMT, sourceRef);

    writeI64ToFile(functionState, builder, sizeLE);
    writeBytesToFile(functionState, builder, sizeLE, hostRefLE);

    buildCall(builder, globalState->externs->free, {hostRefLE});
  } else {
    assert(false);
  }
}


LLVMValueRef Determinism::buildMaybeStartDeterministicMode(
    LLVMBuilderRef builder, LLVMValueRef mainArgsLE, LLVMValueRef argcLE) {
  assert(maybeStartDeterministicModeLF);
  return buildCall(builder, maybeStartDeterministicModeLF, {mainArgsLE, argcLE});
}


void Determinism::buildWriteRefToFile(LLVMBuilderRef builder, LLVMValueRef refI256LE) {
  assert(writeRefToFileLF);
  auto int256LT = LLVMIntTypeInContext(globalState->context, 256);
  assert(LLVMTypeOf(refI256LE) == int256LT);
  buildCall(builder, writeRefToFileLF, {refI256LE});
}

void Determinism::buildRecordCallEnd(LLVMBuilderRef builder, Prototype* prototype) {
  assert(recordCallEndLF);

  // TODO: Use exported names instead of regular function names, see URFNIEN.
  auto strLE = globalState->getOrMakeStringConstant(prototype->name->name);
  auto strLenLE = constI64LE(globalState, prototype->name->name.length());

  buildCall(builder, recordCallEndLF, {strLenLE, strLE});
}

void Determinism::buildMatchCallFromRecordingFile(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype) {
  assert(matchCallFromRecordingFileLF);
  // TODO: Use exported names instead of regular function names, see URFNIEN.
  auto strLE = globalState->getOrMakeStringConstant(prototype->name->name);
  auto strLenLE = constI64LE(globalState, prototype->name->name.length());
  buildCall(builder, matchCallFromRecordingFileLF, {strLenLE, strLE});
}

Ref Determinism::buildReadValueFromFile(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* targetRefMT) {
  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  auto floatLT = LLVMDoubleTypeInContext(globalState->context);

  assert(targetRefMT->ownership == Ownership::SHARE); // not implemented for owns
  auto hostRefMT = globalState->linearRegion->linearizeReference(targetRefMT);

  if (dynamic_cast<Int*>(targetRefMT->kind)) {
    auto intLE = readI64FromFile(functionState, builder);
    return wrap(globalState->getRegion(targetRefMT), targetRefMT, intLE);
  } else if (dynamic_cast<Bool*>(targetRefMT->kind)) {
    auto boolLE = LLVMBuildTrunc(builder, readI64FromFile(functionState, builder), int1LT, "boolFromRecording");
    return wrap(globalState->getRegion(targetRefMT), targetRefMT, boolLE);
  } else if (dynamic_cast<Float*>(targetRefMT->kind)) {
    auto floatLE = LLVMBuildBitCast(builder, readI64FromFile(functionState, builder), floatLT, "floatFromRecording");
    return wrap(globalState->getRegion(targetRefMT), targetRefMT, floatLE);
  } else if (dynamic_cast<StructKind*>(targetRefMT->kind)) {
    auto valueSizeLE = readI64FromFile(functionState, builder);
    auto tempBufferPtrLE = buildCall(builder, globalState->externs->malloc, {valueSizeLE});

    auto freadResultLE =
        buildCall(
            builder, globalState->externs->fread,
            {
                tempBufferPtrLE,
                valueSizeLE,
                constI64LE(globalState, 1),
                LLVMBuildLoad(builder, fileDescriptorPtrGlobalLE, "")
            });
    buildIf(
        globalState, functionState->containingFuncL, builder,
        LLVMBuildICmp(builder, LLVMIntULT, freadResultLE, constI64LE(globalState, 1), ""),
        [this](LLVMBuilderRef builder){
          buildPrint(globalState, builder, "Couldn't read from recording file.");
          buildCall(builder, globalState->externs->exit, {constI64LE(globalState, 1)});
        });

    auto valeRegionInstanceRef =
        // At some point, look up the actual region instance, perhaps from the FunctionState?
        globalState->getRegion(targetRefMT)->createRegionInstanceLocal(functionState, builder);
    auto useOffsetsLE = constI1LE(globalState, 1);
    auto fileOffsetLE = LLVMBuildLoad(builder, fileOffsetPtrGlobalLE, "fileOffset");
    auto hostRegionInstanceRef =
        globalState->linearRegion->createRegionInstanceLocal(
            functionState, builder, useOffsetsLE, fileOffsetLE);

    auto valeRef =
        receiveHostObjectIntoVale(
            globalState, functionState, builder, hostRegionInstanceRef, valeRegionInstanceRef,
            hostRefMT, targetRefMT, tempBufferPtrLE);

    buildCall(builder, globalState->externs->free, {tempBufferPtrLE});

    return valeRef;
  } else {
    assert(false);
  }
}

Ref Determinism::buildReadAndMapRefFromFile(LLVMBuilderRef builder, Reference* refMT) {
  assert(readAndMapFromFileLE);
  auto refLE = buildCall(builder, readAndMapFromFileLE, {});
  return wrap(globalState->getRegion(refMT), refMT, refLE);
}

LLVMValueRef Determinism::buildGetMaybeReplayedFuncForNextExportCall(LLVMBuilderRef builder) {
  assert(getMaybeReplayerFuncForNextExportNameLF);
  return buildCall(builder, getMaybeReplayerFuncForNextExportNameLF, {});
}

Ref Determinism::i256ToRef(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    LLVMValueRef refLE) {
  auto refLT = globalState->getRegion(refMT)->translateType(refMT);
  assert(LLVMABISizeOfType(globalState->dataLayout, refLT) <= 32);
  assert(LLVMGetTypeKind(refLT) != LLVMPointerTypeKind);
  auto refFrom256LE = LLVMBuildBitCast(builder, refLE, refLT, "refFrom256");
  return wrap(globalState->getRegion(refMT), refMT, refFrom256LE);
}

LLVMValueRef Determinism::refToI256(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* refMT,
    Ref ref) {
  auto int256LT = LLVMIntTypeInContext(globalState->context, 256);
  auto refLT = globalState->getRegion(refMT)->translateType(refMT);
  assert(LLVMABISizeOfType(globalState->dataLayout, refLT) <= 32);
  assert(LLVMGetTypeKind(refLT) != LLVMPointerTypeKind);
  auto refLE = globalState->getRegion(refMT)->checkValidReference(FL(), functionState, builder, refMT, ref);
  return LLVMBuildBitCast(builder, refLE, int256LT, "refFrom256");
}

LLVMValueRef Determinism::makeFuncToReplayExportCall(Prototype* prototype) {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int64Size = LLVMABISizeOfType(globalState->dataLayout, int64LT);
  auto replayerFuncLT = makeReplayerFuncLT(globalState);

  // TODO: Use exported names instead of regular function names, see URFNIEN.
  auto replayerFuncName = replayerFuncPrefix + prototype->name->name;

  auto functionLF = addFunction(globalState, replayerFuncName, voidLT, {});

  defineFunctionBody(
      globalState,
      functionLF,
      voidLT,
      getMaybeReplayerFuncForNextExportNameFuncName,
      [this, int8LT, replayerFuncLT](FunctionState* functionState, LLVMBuilderRef builder){
        buildPrint(globalState, builder, "Implement makeFuncToReplayExportCall");
      });

  return functionLF;
}