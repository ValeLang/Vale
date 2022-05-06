#ifndef DETERMINISM_DETERMINISM_H
#define DETERMINISM_DETERMINISM_H

#include <globalstate.h>
#include <utils/call.h>
#include <simplehash/cppsimplehashmap.h>
#include <simplehash/llvmsimplehashmap.h>

class Determinism {
public:
  LLVMValueRef buildMaybeStartDeterministicMode(
      LLVMBuilderRef builder, LLVMValueRef mainArgsLE, LLVMValueRef argcLE);
  LLVMValueRef buildWriteCallBeginToFile(LLVMBuilderRef builder, Prototype* prototype);
//  LLVMValueRef buildGetNextExportCallString(LLVMBuilderRef builder);
  void buildWriteRefToFile(LLVMBuilderRef builder, LLVMValueRef refI128LE);
  void buildRecordCallEnd(LLVMBuilderRef builder, Prototype* prototype);
  void buildMatchCallFromRecordingFile(LLVMBuilderRef builder);
  LLVMValueRef buildReadValueFromFile(LLVMBuilderRef builder);
  void buildWriteValueToFile(LLVMBuilderRef builder, LLVMValueRef argLE);
  LLVMValueRef buildReadAndMapRefFromFile(LLVMBuilderRef builder);
  LLVMValueRef buildGetMaybeReplayedFuncForNextExportCall(LLVMBuilderRef builder);

  Determinism(GlobalState* globalState);

  void registerFunction(Prototype* prototype);
  void finalizeFunctionsMap();

private:
  void makeFuncToMaybeStartDeterministicMode();
  void makeFuncToWriteCallBeginToFile();
//  void makeFuncToGetNextExportCallString();
  void makeFuncToWriteRefToFile();
  void makeFuncToWriteValueToFile();
  void makeFuncToRecordCallEnd();
  void makeFuncToMatchCallFromRecordingFile();
  void makeFuncToReadValueFromFile();
  void makeFuncToMatchRefFromRecordingFile();
  void makeFuncToStartReplaying();
  void makeFuncToStartRecording();
  void makeFuncToGetReplayerFuncForExportName();
  LLVMValueRef makeFuncToReplayExportCall();

  enum class FileOpenMode {
    READ,
    WRITE
  };

  void buildStartReplaying(LLVMBuilderRef builder, LLVMValueRef recordingFilename);
  void buildStartRecording(LLVMBuilderRef builder, LLVMValueRef recordingFilename);
  void writeBytesToFile(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef sizeLE, LLVMValueRef i8PtrLE);
  LLVMValueRef openFile(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef pathI8PtrLE, FileOpenMode mode);
  void writeI64ToFile(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef i64LE);
  void writeI128ToFile(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef i128LE);
  LLVMValueRef readI64FromFile(FunctionState* functionState, LLVMBuilderRef builder);
  void writeStringToFile(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef lengthNotIncludingNullTerminatorLE, LLVMValueRef strLE);
//  LLVMValueRef readStringFromFile(FunctionState* functionState, LLVMBuilderRef builder);
  LLVMValueRef readLimitedStringFromFile(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef maxSizeLE,
      LLVMValueRef bufferPtrLE);

  GlobalState* globalState;

  CppSimpleHashMap<Prototype*, std::tuple<>, AddressHasher<Prototype*>, AddressEquator<Prototype*>> functionsMap;
  LlvmSimpleHashMap functionsMapLT;
  std::optional<LLVMValueRef> finalizedExportNameToReplayerFunctionMapGlobalLE;


  LLVMValueRef maybeStartDeterministicModeLF = nullptr;
  LLVMValueRef startReplayingLF = nullptr;
  LLVMValueRef startRecordingLF = nullptr;
  LLVMValueRef writeCallBeginToFileLF = nullptr;
//  LLVMValueRef getNextExportCallStringLF = nullptr;
  LLVMValueRef writeRefToFileLF = nullptr;
  LLVMValueRef writeValueToFileLF = nullptr;
  LLVMValueRef recordCallEndLF = nullptr;
  LLVMValueRef matchCallFromRecordingFileLF = nullptr;
  LLVMValueRef readValueFromFileLF = nullptr;
  LLVMValueRef readAndMapFromFileLE = nullptr;
  LLVMValueRef readLimitedStringFromFileLF = nullptr;
  LLVMValueRef getMaybeReplayerFuncForNextExportNameLF = nullptr;

  LLVMValueRef fileDescriptorPtrGlobalLE = nullptr;
};


#endif //DETERMINISM_DETERMINISM_H
