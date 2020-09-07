#include <iostream>
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/string.h"
#include "function/expressions/shared/controlblock.h"
#include "function/expressions/shared/heap.h"

#include "translatetype.h"

#include "function/expression.h"

Ref translateExternCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    ExternCall* call) {
  auto name = call->function->name->name;
  if (name == "__addIntInt") {
    assert(call->argExprs.size() == 2);
    auto leftLE =
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0]));
    auto rightLE =
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1]));
    auto result = LLVMBuildAdd(builder, leftLE, rightLE,"add");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, result);
  } else if (name == "__divideIntInt") {
    assert(call->argExprs.size() == 2);
    auto leftLE =
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0]));
    auto rightLE =
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1]));
    auto result = LLVMBuildSDiv(builder, leftLE, rightLE,"add");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, result);
  } else if (name == "__eqStrStr") {
    assert(call->argExprs.size() == 2);

    auto leftStrTypeM = call->argTypes[0];
    auto leftStrWrapperRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);
    auto leftStrWrapperPtrLE =
        WrapperPtrLE(
            globalState->metalCache.strRef,
            checkValidReference(FL(), globalState, functionState, builder, globalState->metalCache.strRef, leftStrWrapperRef));

    auto rightStrTypeM = call->argTypes[1];
    auto rightStrWrapperRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]);
    auto rightStrWrapperPtrLE =
        WrapperPtrLE(
            globalState->metalCache.strRef,
            checkValidReference(FL(), globalState, functionState, builder, globalState->metalCache.strRef, leftStrWrapperRef));

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

    discard(FL(), globalState, functionState, blockState, builder, globalState->metalCache.strRef, leftStrWrapperRef);
    discard(FL(), globalState, functionState, blockState, builder, globalState->metalCache.strRef, rightStrWrapperRef);

    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, resultBoolLE);
  } else if (name == "__addFloatFloat") {
    // VivemExterns.addFloatFloat
    assert(false);
  } else if (name == "__panic") {
    auto exitCodeLE = makeConstIntExpr(functionState, builder, LLVMInt8Type(), 255);
    LLVMBuildCall(builder, globalState->exit, &exitCodeLE, 1, "");
    return wrap(functionState->defaultRegion, globalState->metalCache.neverRef, makeConstExpr(functionState, builder, makeNever()));
  } else if (name == "__multiplyIntInt") {
    assert(call->argExprs.size() == 2);
    auto resultIntLE =
        LLVMBuildMul(
            builder,
            checkValidReference(
                FL(), globalState, functionState, builder, call->function->params[0],
                translateExpression(
                    globalState, functionState, blockState, builder, call->argExprs[0])),
            checkValidReference(
                FL(), globalState, functionState, builder, call->function->params[1],
                translateExpression(
                    globalState, functionState, blockState, builder, call->argExprs[1])),
            "mul");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, resultIntLE);
  } else if (name == "__subtractIntInt") {
    assert(call->argExprs.size() == 2);
    auto resultIntLE =
        LLVMBuildSub(
            builder,
            checkValidReference(
                FL(), globalState, functionState, builder, call->function->params[0],
                translateExpression(
                    globalState, functionState, blockState, builder, call->argExprs[0])),
            checkValidReference(
                FL(), globalState, functionState, builder, call->function->params[1],
                translateExpression(
                    globalState, functionState, blockState, builder, call->argExprs[1])),
            "diff");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, resultIntLE);
  } else if (name == "__addStrStr") {
    assert(call->argExprs.size() == 2);

    auto leftStrTypeM = call->argTypes[0];
    auto leftStrWrapperRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);
    auto leftStrWrapperPtrLE =
        WrapperPtrLE(
            globalState->metalCache.strRef,
            checkValidReference(FL(), globalState, functionState, builder, call->argTypes[0], leftStrWrapperRef));
    auto leftStrLenLE = getLenFromStrWrapperPtr(builder, leftStrWrapperPtrLE);

    auto rightStrTypeM = call->argTypes[1];
    auto rightStrWrapperRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]);
    auto rightStrWrapperPtrLE =
        WrapperPtrLE(
            globalState->metalCache.strRef,
            checkValidReference(
                FL(), globalState, functionState, builder, call->argTypes[1], rightStrWrapperRef));
    auto rightStrLenLE = getLenFromStrWrapperPtr(builder, rightStrWrapperPtrLE);

    auto combinedLenLE =
        LLVMBuildAdd(builder, leftStrLenLE, rightStrLenLE, "lenSum");

    auto destStrWrapperPtrLE = mallocStr(globalState, functionState, builder, combinedLenLE);

    std::vector<LLVMValueRef> argsLE = {
        getInnerStrPtrFromWrapperPtr(builder, leftStrWrapperPtrLE),
        getInnerStrPtrFromWrapperPtr(builder, rightStrWrapperPtrLE),
        getInnerStrPtrFromWrapperPtr(builder, destStrWrapperPtrLE),
    };
    LLVMBuildCall(builder, globalState->addStr, argsLE.data(), argsLE.size(), "");

    discard(FL(), globalState, functionState, blockState, builder, globalState->metalCache.strRef, leftStrWrapperRef);
    discard(FL(), globalState, functionState, blockState, builder, globalState->metalCache.strRef, rightStrWrapperRef);

    auto destStrRef = wrap(functionState->defaultRegion, globalState->metalCache.strRef, destStrWrapperPtrLE);
    checkValidReference(FL(), globalState, functionState, builder, call->function->returnType, destStrRef);

    return destStrRef;
  } else if (name == "__getch") {
    auto resultIntLE = LLVMBuildCall(builder, globalState->getch, nullptr, 0, "");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, resultIntLE);
  } else if (name == "__lessThanInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSLT,
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__greaterThanInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSGT,
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__greaterThanOrEqInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSGE,
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__lessThanOrEqInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSLE,
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__eqIntInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__eqBoolBool") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__print") {
    assert(call->argExprs.size() == 1);

    auto argStrTypeM = call->argTypes[0];
    auto argStrWrapperRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);
    auto argStrWrapperPtrLE =
        WrapperPtrLE(
            globalState->metalCache.strRef,
            checkValidReference(
                FL(), globalState, functionState, builder, call->argTypes[0], argStrWrapperRef));

    std::vector<LLVMValueRef> argsLE = {
        getInnerStrPtrFromWrapperPtr(builder, argStrWrapperPtrLE),
    };
    LLVMBuildCall(builder, globalState->printVStr, argsLE.data(), argsLE.size(), "");

    discard(FL(), globalState, functionState, blockState, builder, globalState->metalCache.strRef, argStrWrapperRef);

    assert(call->function->returnType == globalState->metalCache.voidRef);

    return wrap(functionState->defaultRegion, globalState->metalCache.voidRef,
        LLVMGetUndef(
            functionState->defaultRegion->translateType(globalState->metalCache.voidRef)));
  } else if (name == "__not") {
    assert(call->argExprs.size() == 1);
    auto result = LLVMBuildNot(
        builder,
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__castIntStr") {
    assert(call->argExprs.size() == 1);
    auto intLE =
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0]));

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

    return wrap(functionState->defaultRegion, globalState->metalCache.strRef, strWrapperPtrLE);
  } else if (name == "__and") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildAnd(
        builder,
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__or") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildOr(
        builder,
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__mod") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildSRem(
        builder,
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        checkValidReference(
            FL(), globalState, functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, result);
  } else {
    std::cerr << name << std::endl;
    assert(false);
  }
  assert(false);
}
