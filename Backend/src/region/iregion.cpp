#include "iregion.h"

LLVMValueRef checkValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool expectLive,
    Reference* refM,
    Ref ref) {
  return globalState->getRegion(refM)->checkValidReference(
      checkerAFL, functionState, builder, expectLive, refM, ref);
}

LLVMValueRef checkValidReference(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    bool expectLive,
    Reference* refM,
    LiveRef liveRef) {
  auto ref = toRef(globalState, refM, liveRef);
  return checkValidReference(
      checkerAFL, globalState, functionState, builder, expectLive, refM, ref);
}
