#include <simplehash/llvmsimplehashmap.h>

#include <utility>
#include "flags.h"
#include "branch.h"


LLVMValueRef processFlag(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    const std::string& flagName,
    LLVMValueRef mainArgsCountLE,
    LLVMValueRef mainArgsLE,
    const std::function<void(LLVMBuilderRef, LLVMValueRef)>& thenBody) {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrPtrLT = LLVMPointerType(int8PtrLT, 0);

  // The then and else bodies will return the number of arguments they may have consumed.
  return buildIfElse(
      globalState, functionState, builder, int64LT,
      LLVMBuildICmp(builder, LLVMIntUGT, mainArgsCountLE, constI64LE(globalState, 0), ""),
      [globalState, functionState, flagName, int8PtrLT, thenBody, mainArgsCountLE, mainArgsLE](
          LLVMBuilderRef builder) {
        auto firstArgLE = LLVMBuildLoad(builder, mainArgsLE, "firstArg");
        auto isReplayingLE =
            buildCall(
                globalState, builder, globalState->externs->strncmp, {
                    globalState->getOrMakeStringConstant(flagName),
                    firstArgLE,
                    constI64LE(globalState, flagName.size())
                });
        buildIf(
            globalState, functionState->containingFuncL, builder, isReplayingLE,
            [globalState, functionState, flagName, mainArgsCountLE, mainArgsLE, int8PtrLT, thenBody](
                LLVMBuilderRef builder) {
              auto replayFile =
                  buildIfElse(
                      globalState, functionState, builder,
                      LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0),
                      LLVMBuildICmp(builder, LLVMIntUGT, mainArgsCountLE, constI64LE(globalState, 1), ""),
                      [globalState, mainArgsLE](LLVMBuilderRef builder) {
                        std::vector<LLVMValueRef> indices = {constI64LE(globalState, 1)};
                        auto ptrToSecondMainArgLE =
                            LLVMBuildGEP(builder, mainArgsLE, indices.data(), indices.size(), "");
                        auto secondMainArgLE = LLVMBuildLoad(builder, ptrToSecondMainArgLE, "");
                        return secondMainArgLE;
                      },
                      [globalState, flagName, int8PtrLT](LLVMBuilderRef builder) {
                        buildPrint(globalState, builder, "Error: Must supply a value after ");
                        buildPrint(globalState, builder, flagName);
                        buildPrint(globalState, builder, ".\n");
                        buildCall(globalState, builder, globalState->externs->exit, {constI64LE(globalState, 1)});
                        return LLVMGetUndef(int8PtrLT);
                      });
              thenBody(builder, replayFile);
              return constI64LE(globalState, 2); // 2 means we've consumed two arguments.
            });
        return constI64LE(globalState, 0); // 0 means we've consumed zero arguments.
      },
      [globalState](LLVMBuilderRef builder){
        return constI64LE(globalState, 0); // 0 means we've consumed zero arguments.
      });
}
