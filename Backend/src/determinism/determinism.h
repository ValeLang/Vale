#ifndef DETERMINISM_DETERMINISM_H
#define DETERMINISM_DETERMINISM_H

#include <globalstate.h>
#include <utils/call.h>

class Determinism {
public:
  LLVMValueRef buildCallToWriteCallToFile(GlobalState* globalState, LLVMBuilderRef builder, Prototype* prototype);
  LLVMValueRef buildCallToGetNextExportCallString(GlobalState* globalState, LLVMBuilderRef builder);
  std::tuple<LLVMValueRef, LLVMValueRef>
  buildCallToStartDeterministicMode(
      GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef argvLE, LLVMValueRef argcLE);


private:
  static LLVMValueRef makeFuncToWriteCallToFile(GlobalState* globalState, LLVMBuilderRef builder, Prototype* prototype);
  static LLVMValueRef makeFuncToGetNextExportCallString(GlobalState* globalState, LLVMBuilderRef builder);
  static LLVMValueRef makeFuncToStartDeterministicMode(
      GlobalState* globalState, LLVMBuilderRef builder, LLVMValueRef argvLE, LLVMValueRef argcLE);

  Prototype* writeCallToFilePrototype = nullptr;
  Prototype* getNextExportCallStringPrototype = nullptr;
  Prototype* startDeterministicModePrototype = nullptr;
};


#endif //DETERMINISM_DETERMINISM_H
