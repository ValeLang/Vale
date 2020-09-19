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
    auto leftStrRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);

    auto rightStrTypeM = call->argTypes[1];
    auto rightStrRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]);

    std::vector<LLVMValueRef> argsLE = {
        functionState->defaultRegion->getStringBytesPtr(functionState, builder, leftStrRef),
        functionState->defaultRegion->getStringBytesPtr(functionState, builder, rightStrRef)
    };
    auto resultInt8LE =
        LLVMBuildCall(
            builder,
            globalState->eqStr,
            argsLE.data(),
            argsLE.size(),
            "eqStrResult");
    auto resultBoolLE = LLVMBuildICmp(builder, LLVMIntNE, resultInt8LE, LLVMConstInt(LLVMInt8Type(), 0, false), "");

    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, leftStrRef);
    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, rightStrRef);

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
    auto leftStrRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);
    auto leftStrLenLE = functionState->defaultRegion->getStringLen(functionState, builder, leftStrRef);

    auto rightStrTypeM = call->argTypes[1];
    auto rightStrRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[1]);
    auto rightStrLenLE = functionState->defaultRegion->getStringLen(functionState, builder, rightStrRef);

    auto combinedLenLE =
        LLVMBuildAdd(builder, leftStrLenLE, rightStrLenLE, "lenSum");

    auto destStrWrapperPtrLE = functionState->defaultRegion->mallocStr(functionState, builder, combinedLenLE);

    std::vector<LLVMValueRef> argsLE = {
        functionState->defaultRegion->getStringBytesPtr(functionState, builder, leftStrRef),
        functionState->defaultRegion->getStringBytesPtr(functionState, builder, rightStrRef),
        getCharsPtrFromWrapperPtr(builder, destStrWrapperPtrLE),
    };
    LLVMBuildCall(builder, globalState->addStr, argsLE.data(), argsLE.size(), "");

    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, leftStrRef);
    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, rightStrRef);

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

    std::vector<LLVMValueRef> argsLE = {
        functionState->defaultRegion->getStringBytesPtr(functionState, builder, argStrWrapperRef)
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

    auto strWrapperPtrLE = functionState->defaultRegion->mallocStr(functionState, builder, lengthLE);
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
    auto externishArgsLE = std::vector<LLVMValueRef>{};
    externishArgsLE.reserve(call->argExprs.size());
    for (int i = 0; i < call->argExprs.size(); i++) {
      auto argExpr = call->argExprs[i];
      auto argRefMT = call->function->params[i];
      auto argRef = translateExpression(globalState, functionState, blockState, builder, argExpr);
      auto valishArgLE = globalState->region->checkValidReference(FL(), functionState, builder, argRefMT, argRef);

      LLVMValueRef externishArgLE = nullptr;
      if (call->argTypes[i] == globalState->metalCache.intRef) {
        externishArgLE = valishArgLE;
      } else if (call->argTypes[i] == globalState->metalCache.boolRef) {
        // Outside has i8, we have i1, but clang should be fine doing the conversion
        externishArgLE = valishArgLE;
      } else if (call->argTypes[i] == globalState->metalCache.floatRef) {
        externishArgLE = valishArgLE;
      } else if (call->argTypes[i] == globalState->metalCache.strRef) {
        externishArgLE = functionState->defaultRegion->getStringBytesPtr(functionState, builder, argRef);
      } else if (call->argTypes[i] == globalState->metalCache.neverRef) {
        assert(false); // How can we hand a never into something?
      } else if (call->argTypes[i] == globalState->metalCache.emptyTupleStructRef) {
        assert(false); // How can we hand a void into something?
      } else if (auto structReferend = dynamic_cast<StructReferend*>(argRefMT->referend)) {
        if (argRefMT->ownership == Ownership::SHARE) {
          if (argRefMT->location == Location::INLINE) {
            assert(LLVMTypeOf(valishArgLE) ==
                globalState->region->getReferendStructsSource()->getInnerStruct(structReferend));
            externishArgLE = valishArgLE;
          } else {
            std::cerr << "Can only pass inline imm structs between C and Vale currently." << std::endl;
            assert(false);
          }
        } else {
          std::cerr << "Can only pass inline imm structs between C and Vale currently." << std::endl;
          assert(false);
        }
      } else {
        std::cerr << "Invalid type for extern!" << std::endl;
        assert(false);
      }

      externishArgsLE.push_back(externishArgLE);
    }

    auto externFuncIter = globalState->externFunctions.find(call->function->name->name);
    assert(externFuncIter != globalState->externFunctions.end());
    auto externFuncL = externFuncIter->second;

    buildFlare(FL(), globalState, functionState, builder, "Suspending function ", functionState->containingFuncM->prototype->name->name);
    buildFlare(FL(), globalState, functionState, builder, "Calling extern function ", call->function->name->name);

    auto resultLE = LLVMBuildCall(builder, externFuncL, externishArgsLE.data(), externishArgsLE.size(), "");
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
