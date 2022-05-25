#ifndef DETERMINISM_DETERMINISM_H
#define DETERMINISM_DETERMINISM_H

#include <globalstate.h>
#include <utils/call.h>
#include <simplehash/cppsimplehashmap.h>
#include <simplehash/llvmsimplehashmap.h>

enum RecordingMode {
  NORMAL = 0,
  RECORDING = 1,
  REPLAYING = 2
};

struct PrototypeNameSimpleStringHasher {
  uint64_t operator()(Prototype* func) const {
    const char* str = func->name->name.c_str();
    uint64_t hash = 0;
    for (int i = 0; str[i]; i++) {
      hash = hash * 37 + str[i];
    }
    return hash;
  }
};

struct PrototypeNameSimpleStringEquator {
  uint64_t operator()(Prototype* a, Prototype* b) const {
    return a->name->name == b->name->name;
  }
};

class Determinism {
public:
  LLVMValueRef buildMaybeStartDeterministicMode(
      LLVMBuilderRef builder, LLVMValueRef argcLE, LLVMValueRef mainArgsLE);
  void buildWriteCallBeginToFile(LLVMBuilderRef builder, Prototype* prototype);
//  LLVMValueRef buildGetNextExportCallString(LLVMBuilderRef builder);
  void buildWriteRefToFile(LLVMBuilderRef builder, LLVMValueRef refI256LE);
  void buildRecordCallEnd(LLVMBuilderRef builder, Prototype* prototype);
  void buildMatchCallFromRecordingFile(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Prototype* prototype);
  Ref buildReadValueFromFile(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* targetRefMT);
  void buildWriteValueToFile(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRefMT,
      Ref sourceRef);
  Ref buildMapRefFromRecordingFile(LLVMBuilderRef builder, Reference* refMT);
  LLVMValueRef buildGetMaybeReplayedFuncForNextExportCall(LLVMBuilderRef builder);
  LLVMValueRef buildGetMode(LLVMBuilderRef builder);

  Determinism(GlobalState* globalState);

  void registerFunction(Prototype* prototype);
  void finalizeFunctionsMap();

private:
  void makeFuncToMaybeStartDeterministicMode();
  void makeFuncToWriteCallBeginToFile();
//  void makeFuncToGetNextExportCallString();
  void makeFuncToWriteRefToFile();
//  void makeFuncToWriteValueToFile();
  void makeFuncToRecordCallEnd();
  void makeFuncToMatchCallFromRecordingFile();
//  void makeFuncToReadValueFromFile();
  void makeFuncToMapRefFromRecordingFile();
  void makeFuncToStartReplaying();
  void makeFuncToStartRecording();
  void makeFuncToGetReplayerFuncForExportName();
  LLVMValueRef makeFuncToReplayExportCall(Prototype* prototype);

  enum class FileOpenMode {
    READ,
    WRITE
  };

  void buildStartReplaying(LLVMBuilderRef builder, LLVMValueRef recordingFilename);
  void buildStartRecording(LLVMBuilderRef builder, LLVMValueRef recordingFilename);
  void writeBytesToFile(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef sizeLE, LLVMValueRef i8PtrLE);
  LLVMValueRef openFile(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef pathI8PtrLE, FileOpenMode mode);
  void writeI64ToFile(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef i64LE);
  void writeI256ToFile(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef i256LE);
  LLVMValueRef readI64FromFile(FunctionState* functionState, LLVMBuilderRef builder);
  LLVMValueRef readI256FromFile(FunctionState* functionState, LLVMBuilderRef builder);
  void writeStringToFile(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef lengthNotIncludingNullTerminatorLE, LLVMValueRef strLE);
//  LLVMValueRef readStringFromFile(FunctionState* functionState, LLVMBuilderRef builder);
  void readLimitedStringFromFile(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef maxSizeLE,
      LLVMValueRef bufferPtrLE);
  Ref i256ToRef(FunctionState* functionState, LLVMBuilderRef builder, Reference* refMT, LLVMValueRef refLE);
  LLVMValueRef refToI256(FunctionState* functionState, LLVMBuilderRef builder, Reference* refMT, Ref ref);

  GlobalState* globalState;

  CppSimpleHashMap<Prototype*, std::tuple<>, PrototypeNameSimpleStringHasher, PrototypeNameSimpleStringEquator> functionsMap;
  std::unique_ptr<LlvmSimpleHashMap> functionsMapLT;
  bool finalizedFunctions = false; // True if we've already registered all the exports and externs.
  LLVMValueRef exportNameToReplayerFunctionMapGlobalLE;

  std::unique_ptr<LlvmSimpleHashMap> recordedRefToReplayedRefMapLT;
  LLVMValueRef recordedRefToReplayedRefMapGlobalLE;


  LLVMValueRef maybeStartDeterministicModeLF = nullptr;
  LLVMValueRef startReplayingLF = nullptr;
  LLVMValueRef startRecordingLF = nullptr;
  LLVMValueRef writeCallBeginToFileLF = nullptr;
  LLVMValueRef writeRefToFileLF = nullptr;
  LLVMValueRef recordCallEndLF = nullptr;
  LLVMValueRef matchCallFromRecordingFileLF = nullptr;
  LLVMValueRef mapRefFromRecordingFileLF = nullptr;
  LLVMValueRef readLimitedStringFromFileLF = nullptr;
  LLVMValueRef getMaybeReplayerFuncForNextExportNameLF = nullptr;

  LLVMValueRef fileHandleGlobalLE = nullptr;
  LLVMValueRef fileOffsetGlobalLE = nullptr;
  // See RecordingMode
  LLVMValueRef recordingModeGlobalLE = nullptr;
};


#endif //DETERMINISM_DETERMINISM_H
