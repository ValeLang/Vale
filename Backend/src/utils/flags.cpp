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
    std::function<void(LLVMBuilderRef, LLVMValueRef)> thenBody) {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrPtrLT = LLVMPointerType(int8PtrLT, 0);

  auto zerothArgIndexLE = constI64LE(globalState, 0);
  auto zerothArgPtrLE = LLVMBuildGEP(builder, mainArgsLE, &zerothArgIndexLE, 1, "zerothArgPtr");
  auto zerothArgLE = LLVMBuildLoad(builder, zerothArgPtrLE, "zerothArg");
  auto firstArgIndexLE = constI64LE(globalState, 1);
  auto firstArgPtrLE = LLVMBuildGEP(builder, mainArgsLE, &firstArgIndexLE, 1, "firstArgPtr");
  auto firstArgLE = LLVMBuildLoad(builder, firstArgPtrLE, "firstArg");
  auto secondArgIndexLE = constI64LE(globalState, 2);
  auto ptrToSecondMainArgLE = LLVMBuildGEP(builder, mainArgsLE, &secondArgIndexLE, 1, "");
  auto secondMainArgLE = LLVMBuildLoad(builder, ptrToSecondMainArgLE, "");

  return buildIfElse(
      globalState, functionState, builder, int64LT,
      // If >= 2, then there may be args!
      LLVMBuildICmp(builder, LLVMIntUGE, mainArgsCountLE, constI64LE(globalState, 2), ""),
      [globalState, int1LT, functionState, flagName, int8PtrLT, thenBody, mainArgsCountLE, zerothArgLE, ptrToSecondMainArgLE, secondMainArgLE, firstArgLE](
          LLVMBuilderRef builder) {
        buildFlare(FL(), globalState, functionState, builder);

        buildFlare(FL(), globalState, functionState, builder, "args: ", globalState->getOrMakeStringConstant(flagName), ", ", firstArgLE, ", ", constI64LE(globalState, flagName.size()));
        auto stringsDifferentI8LE =
            buildMaybeNeverCall(
                globalState, builder, globalState->externs->strncmp, {
                    globalState->getOrMakeStringConstant(flagName),
                    firstArgLE,
                    constI64LE(globalState, flagName.size())
                });
        auto stringsDifferentLE = LLVMBuildTrunc(builder, stringsDifferentI8LE, int1LT, "isReplaying");
        auto isReplayingLE = LLVMBuildNot(builder, stringsDifferentLE, "");
        buildFlare(FL(), globalState, functionState, builder);
        buildIf(
            globalState, functionState->containingFuncL, builder, isReplayingLE,
            [globalState, functionState, flagName, mainArgsCountLE, zerothArgLE, ptrToSecondMainArgLE, secondMainArgLE, int8PtrLT, thenBody](
                LLVMBuilderRef builder) {
              buildFlare(FL(), globalState, functionState, builder);
              buildIfV(
                  globalState, functionState, builder,
                  LLVMBuildICmp(builder, LLVMIntULE, mainArgsCountLE, constI64LE(globalState, 1), ""),
                  [globalState, flagName, int8PtrLT](LLVMBuilderRef builder) {
                    buildPrintToStderr(globalState, builder, "Error: Must supply a value after ");
                    buildPrintToStderr(globalState, builder, flagName);
                    buildPrintToStderr(globalState, builder, ".\n");
                    buildMaybeNeverCall(globalState, builder, globalState->externs->exit, {constI64LE(globalState, 1)});
                    return LLVMGetUndef(int8PtrLT);
                  });
              buildFlare(FL(), globalState, functionState, builder);
              assert(LLVMTypeOf(secondMainArgLE) == int8PtrLT);
              thenBody(builder, secondMainArgLE);

              return constI64LE(globalState, 2); // We've consumed two arguments.
            });
        buildFlare(FL(), globalState, functionState, builder);
        return constI64LE(globalState, 0); // We've consumed zero arguments
      },
      [globalState, functionState](LLVMBuilderRef builder){
        buildFlare(FL(), globalState, functionState, builder);
        return constI64LE(globalState, 0); // We've consumed zero arguments
      });
}
