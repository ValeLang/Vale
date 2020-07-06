#include <iostream>
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/string.h"
#include "function/expressions/shared/controlblock.h"
#include "function/expressions/shared/heap.h"

#include "translatetype.h"

#include "function/expression.h"

LLVMValueRef translateExternCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    ExternCall* call) {
  auto name = call->function->name->name;
  if (name == "F(\"__addIntInt\",[],[R(*,<,i),R(*,<,i)])") {
    assert(call->argExprs.size() == 2);
    auto left =
        translateExpression(
            globalState, functionState, builder, call->argExprs[0]);
    auto right =
        translateExpression(
            globalState, functionState, builder, call->argExprs[1]);
    auto result = LLVMBuildAdd(builder, left, right,"add");
    return result;
  } else if (name == "F(\"__eqStrStr\",[],[R(*,>,s),R(*,>,s)])") {
    assert(call->argExprs.size() == 2);

    auto leftStrTypeM = call->argTypes[0];
    auto leftStrWrapperPtrLE =
        translateExpression(
            globalState, functionState, builder, call->argExprs[0]);

    auto rightStrTypeM = call->argTypes[1];
    auto rightStrWrapperPtrLE =
        translateExpression(
            globalState, functionState, builder, call->argExprs[1]);

    std::vector<LLVMValueRef> argsLE = {
        getInnerStrPtrFromWrapperPtr(builder, leftStrWrapperPtrLE),
        getInnerStrPtrFromWrapperPtr(builder, rightStrWrapperPtrLE)
    };
    auto resultInt8LE =
        LLVMBuildCall(
            builder,
            globalState->eqStr,
            argsLE.data(),
            argsLE.size(),
            "eqStrResult");
    auto resultBoolLE = LLVMBuildICmp(builder, LLVMIntNE, resultInt8LE, LLVMConstInt(LLVMInt8Type(), 0, false), "");

    discard(FL(), globalState, functionState, builder, leftStrTypeM, leftStrWrapperPtrLE);
    discard(FL(), globalState, functionState, builder, rightStrTypeM, rightStrWrapperPtrLE);

    return resultBoolLE;
  } else if (name == "F(\"__addFloatFloat\",[],[R(*,<,f),R(*,<,f)])") {
    // VivemExterns.addFloatFloat
    assert(false);
  } else if (name == "F(\"panic\")") {
    return makeNever();
  } else if (name == "F(\"__multiplyIntInt\",[],[R(*,<,i),R(*,<,i)])") {
    assert(call->argExprs.size() == 2);
    return LLVMBuildMul(
        builder,
        translateExpression(
            globalState, functionState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, builder, call->argExprs[1]),
        "mul");
  } else if (name == "F(\"__subtractIntInt\",[],[R(*,<,i),R(*,<,i)])") {
    // VivemExterns.subtractIntInt
    assert(false);
  } else if (name == "F(\"__addStrStr\",[],[R(*,>,s),R(*,>,s)])") {
    assert(call->argExprs.size() == 2);

    auto leftStrTypeM = call->argTypes[0];
    auto leftStrWrapperPtrLE =
        translateExpression(
            globalState, functionState, builder, call->argExprs[0]);
    auto leftStrLenLE = getLenFromStrWrapperPtr(builder, leftStrWrapperPtrLE);

    auto rightStrTypeM = call->argTypes[1];
    auto rightStrWrapperPtrLE =
        translateExpression(
            globalState, functionState, builder, call->argExprs[1]);
    auto rightStrLenLE = getLenFromStrWrapperPtr(builder, rightStrWrapperPtrLE);

    auto combinedLenLE =
        LLVMBuildAdd(builder, leftStrLenLE, rightStrLenLE, "lenSum");

    auto destStrWrapperPtrLE = mallocStr(globalState, builder, combinedLenLE);

    std::vector<LLVMValueRef> argsLE = {
        getInnerStrPtrFromWrapperPtr(builder, leftStrWrapperPtrLE),
        getInnerStrPtrFromWrapperPtr(builder, rightStrWrapperPtrLE),
        getInnerStrPtrFromWrapperPtr(builder, destStrWrapperPtrLE),
    };
    LLVMBuildCall(builder, globalState->addStr, argsLE.data(), argsLE.size(), "");

    discard(FL(), globalState, functionState, builder, leftStrTypeM, leftStrWrapperPtrLE);
    discard(FL(), globalState, functionState, builder, rightStrTypeM, rightStrWrapperPtrLE);

    return destStrWrapperPtrLE;
  } else if (name == "F(\"__getch\")") {
    // VivemExterns.getch
    assert(false);
  } else if (name == "F(\"__lessThanInt\",[],[R(*,<,i),R(*,<,i)])") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSLT,
        translateExpression(
            globalState, functionState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, builder, call->argExprs[1]),
        "");
    return result;
  } else if (name == "F(\"__greaterThanOrEqInt\",[],[R(*,<,i),R(*,<,i)])") {
    // VivemExterns.greaterThanOrEqInt
    assert(false);
  } else if (name == "F(\"__eqIntInt\",[],[R(*,<,i),R(*,<,i)])") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        translateExpression(
            globalState, functionState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, builder, call->argExprs[1]),
        "");
    return result;
  } else if (name == "F(\"__eqBoolBool\",[],[R(*,<,b),R(*,<,b)])") {
    // VivemExterns.eqBoolBool
    assert(false);
  } else if (name == "F(\"__print\",[],[R(*,>,s)])") {
    assert(call->argExprs.size() == 1);

    auto argStrTypeM = call->argTypes[0];
    auto argStrWrapperPtrLE =
        translateExpression(
            globalState, functionState, builder, call->argExprs[0]);

    std::vector<LLVMValueRef> argsLE = {
        getInnerStrPtrFromWrapperPtr(builder, argStrWrapperPtrLE),
    };
    LLVMBuildCall(builder, globalState->printVStr, argsLE.data(), argsLE.size(), "");

    discard(FL(), globalState, functionState, builder, argStrTypeM, argStrWrapperPtrLE);

    return LLVMGetUndef(translateType(globalState, call->function->returnType));
  } else if (name == "F(\"__not\",[],[R(*,<,b)])") {
    // VivemExterns.not
    assert(false);
  } else if (name == "F(\"__castIntStr\",[],[R(*,<,i)])") {
    // VivemExterns.castIntStr
    assert(false);
  } else if (name == "F(\"__and\",[],[R(*,<,b),R(*,<,b)])") {
    // VivemExterns.and
    assert(false);
  } else if (name == "F(\"__mod\",[],[R(*,<,i),R(*,<,i)])") {
    // VivemExterns.mod
    assert(false);
  } else {
    std::cerr << name << std::endl;
    assert(false);
  }
  assert(false);
  return nullptr;
}
