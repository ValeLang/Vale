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
    BlockState* blockState,
    LLVMBuilderRef builder,
    ExternCall* call) {
  auto name = call->function->name->name;
  if (name == "__addIntInt") {
    assert(call->argExprs.size() == 2);
    auto leftLE =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);
    auto rightLE =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]);
    auto result = LLVMBuildAdd(builder, leftLE, rightLE,"add");
    return result;
  } else if (name == "__divideIntInt") {
    assert(call->argExprs.size() == 2);
    auto leftLE =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);
    auto rightLE =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]);
    auto result = LLVMBuildSDiv(builder, leftLE, rightLE,"add");
    return result;
  } else if (name == "__eqStrStr") {
    assert(call->argExprs.size() == 2);

    auto leftStrTypeM = call->argTypes[0];
    auto leftStrWrapperPtrLE =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);
    checkValidReference(FL(), globalState, functionState, builder, getEffectiveType(globalState, call->argTypes[0]), leftStrWrapperPtrLE);

    auto rightStrTypeM = call->argTypes[1];
    auto rightStrWrapperPtrLE =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]);
    checkValidReference(FL(), globalState, functionState, builder, getEffectiveType(globalState, call->argTypes[1]), rightStrWrapperPtrLE);

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

    discard(FL(), globalState, functionState, blockState, builder, getEffectiveType(globalState, leftStrTypeM), leftStrWrapperPtrLE);
    discard(FL(), globalState, functionState, blockState, builder, getEffectiveType(globalState, rightStrTypeM), rightStrWrapperPtrLE);

    return resultBoolLE;
  } else if (name == "__addFloatFloat") {
    // VivemExterns.addFloatFloat
    assert(false);
  } else if (name == "__panic") {
    auto exitCodeLE = makeConstIntExpr(functionState, builder, LLVMInt8Type(), 255);
    LLVMBuildCall(builder, globalState->exit, &exitCodeLE, 1, "");
    return makeConstExpr(functionState, builder, makeNever());
  } else if (name == "__multiplyIntInt") {
    assert(call->argExprs.size() == 2);
    return LLVMBuildMul(
        builder,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]),
        "mul");
  } else if (name == "__subtractIntInt") {
    assert(call->argExprs.size() == 2);
    return LLVMBuildSub(
        builder,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]),
        "diff");
  } else if (name == "__addStrStr") {
    assert(call->argExprs.size() == 2);

    auto leftStrTypeM = call->argTypes[0];
    auto leftStrWrapperPtrLE =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);
    checkValidReference(FL(), globalState, functionState, builder, getEffectiveType(globalState, call->argTypes[0]), leftStrWrapperPtrLE);
    auto leftStrLenLE = getLenFromStrWrapperPtr(builder, leftStrWrapperPtrLE);

    auto rightStrTypeM = call->argTypes[1];
    auto rightStrWrapperPtrLE =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]);
    auto rightStrLenLE = getLenFromStrWrapperPtr(builder, rightStrWrapperPtrLE);
    checkValidReference(FL(), globalState, functionState, builder, getEffectiveType(globalState, call->argTypes[1]), rightStrWrapperPtrLE);

    auto combinedLenLE =
        LLVMBuildAdd(builder, leftStrLenLE, rightStrLenLE, "lenSum");

    auto destStrWrapperPtrLE = mallocStr(globalState, functionState, builder, combinedLenLE);

    std::vector<LLVMValueRef> argsLE = {
        getInnerStrPtrFromWrapperPtr(builder, leftStrWrapperPtrLE),
        getInnerStrPtrFromWrapperPtr(builder, rightStrWrapperPtrLE),
        getInnerStrPtrFromWrapperPtr(builder, destStrWrapperPtrLE),
    };
    LLVMBuildCall(builder, globalState->addStr, argsLE.data(), argsLE.size(), "");

    discard(FL(), globalState, functionState, blockState, builder, getEffectiveType(globalState, leftStrTypeM), leftStrWrapperPtrLE);
    discard(FL(), globalState, functionState, blockState, builder, getEffectiveType(globalState, rightStrTypeM), rightStrWrapperPtrLE);

    checkValidReference(FL(), globalState, functionState, builder, getEffectiveType(globalState, call->function->returnType), destStrWrapperPtrLE);

    return destStrWrapperPtrLE;
  } else if (name == "__getch") {
    return LLVMBuildCall(builder, globalState->getch, nullptr, 0, "");
  } else if (name == "__lessThanInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSLT,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]),
        "");
    return result;
  } else if (name == "__greaterThanInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSGT,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]),
        "");
    return result;
  } else if (name == "__greaterThanOrEqInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSGE,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]),
        "");
    return result;
  } else if (name == "__lessThanOrEqInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSLE,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]),
        "");
    return result;
  } else if (name == "__eqIntInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]),
        "");
    return result;
  } else if (name == "__eqBoolBool") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]),
        "");
    return result;
  } else if (name == "__print") {
    assert(call->argExprs.size() == 1);

    auto argStrTypeM = call->argTypes[0];
    auto argStrWrapperPtrLE =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);
    checkValidReference(FL(), globalState, functionState, builder, getEffectiveType(globalState, call->argTypes[0]), argStrWrapperPtrLE);

    std::vector<LLVMValueRef> argsLE = {
        getInnerStrPtrFromWrapperPtr(builder, argStrWrapperPtrLE),
    };
    LLVMBuildCall(builder, globalState->printVStr, argsLE.data(), argsLE.size(), "");

    discard(FL(), globalState, functionState, blockState, builder, getEffectiveType(globalState, argStrTypeM), argStrWrapperPtrLE);

    return LLVMGetUndef(translateType(globalState, getEffectiveType(globalState, call->function->returnType)));
  } else if (name == "__not") {
    assert(call->argExprs.size() == 1);
    auto result = LLVMBuildNot(
        builder,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        "");
    return result;
  } else if (name == "__castIntStr") {
    assert(call->argExprs.size() == 1);
    auto intLE =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);

    int bufferSize = 150;
    auto charsPtrLocalLE =
        makeMidasLocal(
            functionState,
            builder,
            LLVMArrayType(LLVMInt8Type(), bufferSize),
            "charsPtrLocal",
            LLVMGetUndef(LLVMArrayType(LLVMInt8Type(), bufferSize)));
    auto itoaDestPtrLE =
        LLVMBuildPointerCast(
            builder, charsPtrLocalLE, LLVMPointerType(LLVMInt8Type(), 0), "itoaDestPtr");
    std::vector<LLVMValueRef> atoiArgsLE = { intLE, itoaDestPtrLE, constI64LE(bufferSize) };
    LLVMBuildCall(builder, globalState->intToCStr, atoiArgsLE.data(), atoiArgsLE.size(), "");

    std::vector<LLVMValueRef> strlenArgsLE = { itoaDestPtrLE };
    auto lengthLE = LLVMBuildCall(builder, globalState->strlen, strlenArgsLE.data(), strlenArgsLE.size(), "");

    auto strWrapperPtrLE = mallocStr(globalState, functionState, builder, lengthLE);
    auto innerStrWrapperLE = getInnerStrPtrFromWrapperPtr(builder, strWrapperPtrLE);
    std::vector<LLVMValueRef> argsLE = { innerStrWrapperLE, itoaDestPtrLE };
    LLVMBuildCall(builder, globalState->initStr, argsLE.data(), argsLE.size(), "");

    return strWrapperPtrLE;
  } else if (name == "__and") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildAnd(
        builder,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]),
        "");
    return result;
  } else if (name == "__or") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildOr(
        builder,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]),
        "");
    return result;
  } else if (name == "__mod") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildSRem(
        builder,
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]),
        "");
    return result;
  } else {
    std::cerr << name << std::endl;
    assert(false);
  }
  assert(false);
  return nullptr;
}
