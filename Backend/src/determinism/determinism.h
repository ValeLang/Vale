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
  Ref buildReadAndMapRefFromFile(LLVMBuilderRef builder, Reference* refMT);
  LLVMValueRef buildGetMaybeReplayedFuncForNextExportCall(LLVMBuilderRef builder);

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
  void makeFuncToMatchRefFromRecordingFile();
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

  CppSimpleHashMap<Prototype*, std::tuple<>, AddressHasher<Prototype*>, AddressEquator<Prototype*>> functionsMap;
  LlvmSimpleHashMap functionsMapLT;
  std::optional<LLVMValueRef> finalizedExportNameToReplayerFunctionMapGlobalLE;


  LLVMValueRef maybeStartDeterministicModeLF = nullptr;
  LLVMValueRef startReplayingLF = nullptr;
  LLVMValueRef startRecordingLF = nullptr;
  LLVMValueRef writeCallBeginToFileLF = nullptr;
  LLVMValueRef writeRefToFileLF = nullptr;
  LLVMValueRef recordCallEndLF = nullptr;
  LLVMValueRef matchCallFromRecordingFileLF = nullptr;
  LLVMValueRef readAndMapFromFileLE = nullptr;
  LLVMValueRef readLimitedStringFromFileLF = nullptr;
  LLVMValueRef getMaybeReplayerFuncForNextExportNameLF = nullptr;

  LLVMValueRef fileDescriptorPtrGlobalLE = nullptr;
  LLVMValueRef fileOffsetPtrGlobalLE = nullptr;
};


#endif //DETERMINISM_DETERMINISM_H
