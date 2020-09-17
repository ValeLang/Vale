#include <iostream>
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/string.h"
#include "region/common/controlblock.h"
#include "region/common/heap.h"

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
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0]));
    auto rightLE =
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1]));
    auto result = LLVMBuildAdd(builder, leftLE, rightLE,"add");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, result);
  } else if (name == "__divideIntInt") {
    assert(call->argExprs.size() == 2);
    auto leftLE =
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0]));
    auto rightLE =
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
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
        functionState->defaultRegion->makeWrapperPtr(
            globalState->metalCache.strRef,
            globalState->region->checkValidReference(FL(), functionState, builder,
                globalState->metalCache.strRef, leftStrWrapperRef));

    auto rightStrTypeM = call->argTypes[1];
    auto rightStrWrapperRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]);
    auto rightStrWrapperPtrLE =
        functionState->defaultRegion->makeWrapperPtr(
            globalState->metalCache.strRef,
            globalState->region->checkValidReference(FL(), functionState, builder,
                globalState->metalCache.strRef, rightStrWrapperRef));

    std::vector<LLVMValueRef> argsLE = {
        getCharsPtrFromWrapperPtr(builder, leftStrWrapperPtrLE),
        getCharsPtrFromWrapperPtr(builder, rightStrWrapperPtrLE)
    };
    auto resultInt8LE =
        LLVMBuildCall(
            builder,
            globalState->eqStr,
            argsLE.data(),
            argsLE.size(),
            "eqStrResult");
    auto resultBoolLE = LLVMBuildICmp(builder, LLVMIntNE, resultInt8LE, LLVMConstInt(LLVMInt8Type(), 0, false), "");

    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, leftStrWrapperRef);
    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, rightStrWrapperRef);

    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, resultBoolLE);
  } else if (name == "__addFloatFloat") {
    // VivemExterns.addFloatFloat
    assert(false);
  } else if (name == "__panic") {
    auto exitCodeLE = makeConstIntExpr(functionState, builder, LLVMInt8Type(), 255);
    LLVMBuildCall(builder, globalState->exit, &exitCodeLE, 1, "");
    LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
    return wrap(functionState->defaultRegion, globalState->metalCache.neverRef, globalState->neverPtr);
  } else if (name == "__multiplyIntInt") {
    assert(call->argExprs.size() == 2);
    auto resultIntLE =
        LLVMBuildMul(
            builder,
            globalState->region->checkValidReference(FL(),
                functionState, builder, call->function->params[0],
                translateExpression(
                    globalState, functionState, blockState, builder, call->argExprs[0])),
            globalState->region->checkValidReference(FL(),
                functionState, builder, call->function->params[1],
                translateExpression(
                    globalState, functionState, blockState, builder, call->argExprs[1])),
            "mul");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, resultIntLE);
  } else if (name == "__subtractIntInt") {
    assert(call->argExprs.size() == 2);
    auto resultIntLE =
        LLVMBuildSub(
            builder,
            globalState->region->checkValidReference(FL(),
                functionState, builder, call->function->params[0],
                translateExpression(
                    globalState, functionState, blockState, builder, call->argExprs[0])),
            globalState->region->checkValidReference(FL(),
                functionState, builder, call->function->params[1],
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
        functionState->defaultRegion->makeWrapperPtr(
            globalState->metalCache.strRef,
            globalState->region->checkValidReference(FL(), functionState, builder, call->argTypes[0],
                leftStrWrapperRef));
    auto leftStrLenLE = getLenFromStrWrapperPtr(builder, leftStrWrapperPtrLE);

    auto rightStrTypeM = call->argTypes[1];
    auto rightStrWrapperRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]);
    auto rightStrWrapperPtrLE =
        functionState->defaultRegion->makeWrapperPtr(
            globalState->metalCache.strRef,
            globalState->region->checkValidReference(FL(),
                functionState, builder, call->argTypes[1], rightStrWrapperRef));
    auto rightStrLenLE = getLenFromStrWrapperPtr(builder, rightStrWrapperPtrLE);

    auto combinedLenLE =
        LLVMBuildAdd(builder, leftStrLenLE, rightStrLenLE, "lenSum");

    auto destStrWrapperPtrLE = mallocStr(globalState, functionState, builder, combinedLenLE);

    std::vector<LLVMValueRef> argsLE = {
        getCharsPtrFromWrapperPtr(builder, leftStrWrapperPtrLE),
        getCharsPtrFromWrapperPtr(builder, rightStrWrapperPtrLE),
        getCharsPtrFromWrapperPtr(builder, destStrWrapperPtrLE),
    };
    LLVMBuildCall(builder, globalState->addStr, argsLE.data(), argsLE.size(), "");

    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, leftStrWrapperRef);
    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, rightStrWrapperRef);

    auto destStrRef = wrap(functionState->defaultRegion, globalState->metalCache.strRef, destStrWrapperPtrLE);
    globalState->region->checkValidReference(FL(), functionState, builder, call->function->returnType, destStrRef);

    return destStrRef;
  } else if (name == "__getch") {
    auto resultIntLE = LLVMBuildCall(builder, globalState->getch, nullptr, 0, "");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, resultIntLE);
  } else if (name == "__lessThanInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSLT,
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__greaterThanInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSGT,
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__greaterThanOrEqInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSGE,
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__lessThanOrEqInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSLE,
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__eqIntInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__eqBoolBool") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntEQ,
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
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
        functionState->defaultRegion->makeWrapperPtr(
            globalState->metalCache.strRef,
            globalState->region->checkValidReference(FL(),
                functionState, builder, call->argTypes[0], argStrWrapperRef));

    std::vector<LLVMValueRef> argsLE = {
        getCharsPtrFromWrapperPtr(builder, argStrWrapperPtrLE),
    };
    LLVMBuildCall(builder, globalState->printVStr, argsLE.data(), argsLE.size(), "");

    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, argStrWrapperRef);

    return makeEmptyTupleRef(globalState, functionState, builder);
  } else if (name == "__not") {
    assert(call->argExprs.size() == 1);
    auto result = LLVMBuildNot(
        builder,
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__castIntStr") {
    assert(call->argExprs.size() == 1);
    auto intLE =
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
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
    // Set the length
    LLVMBuildStore(builder, lengthLE, getLenPtrFromStrWrapperPtr(builder, strWrapperPtrLE));
    // Fill the chars
    auto charsPtrLE = getCharsPtrFromWrapperPtr(builder, strWrapperPtrLE);
    std::vector<LLVMValueRef> argsLE = { charsPtrLE, itoaDestPtrLE };
    LLVMBuildCall(builder, globalState->initStr, argsLE.data(), argsLE.size(), "");

    return wrap(functionState->defaultRegion, globalState->metalCache.strRef, strWrapperPtrLE);
  } else if (name == "__and") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildAnd(
        builder,
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__or") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildOr(
        builder,
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__mod") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildSRem(
        builder,
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        globalState->region->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, result);
  } else {
    auto argsLE = std::vector<LLVMValueRef>{};
    argsLE.reserve(call->argExprs.size());
    for (int i = 0; i < call->argExprs.size(); i++) {
      auto argLE = translateExpression(globalState, functionState, blockState, builder, call->argExprs[i]);
      argsLE.push_back(globalState->region->checkValidReference(FL(), functionState, builder, call->function->params[i], argLE));
    }

    auto externFuncIter = globalState->externFunctions.find(call->function->name->name);
    assert(externFuncIter != globalState->externFunctions.end());
    auto externFuncL = externFuncIter->second;

    buildFlare(FL(), globalState, functionState, builder, "Suspending function ", functionState->containingFuncM->prototype->name->name);
    buildFlare(FL(), globalState, functionState, builder, "Calling extern function ", call->function->name->name);

    auto resultLE = LLVMBuildCall(builder, externFuncL, argsLE.data(), argsLE.size(), "");
    auto resultRef = wrap(functionState->defaultRegion, call->function->returnType, resultLE);
    globalState->region->checkValidReference(FL(), functionState, builder, call->function->returnType, resultRef);

    if (call->function->returnType->referend == globalState->metalCache.never) {
      buildFlare(FL(), globalState, functionState, builder, "Done calling function ", call->function->name->name);
      buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncM->prototype->name->name);
      LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
      return wrap(functionState->defaultRegion, globalState->metalCache.neverRef, globalState->neverPtr);
    } else {
      buildFlare(FL(), globalState, functionState, builder, "Done calling function ", call->function->name->name);
      buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncM->prototype->name->name);
      return resultRef;
    }
  }
  assert(false);
}
