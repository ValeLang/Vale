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
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0]));
    auto rightLE =
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1]));
    auto result = LLVMBuildAdd(builder, leftLE, rightLE,"add");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, result);
  } else if (name == "__divideIntInt") {
    assert(call->argExprs.size() == 2);
    auto leftLE =
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0]));
    auto rightLE =
        functionState->defaultRegion->checkValidReference(FL(),
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
    auto resultBoolLE = LLVMBuildICmp(builder, LLVMIntNE, resultInt8LE, LLVMConstInt(LLVMInt8TypeInContext(globalState->context), 0, false), "");

    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, leftStrRef);
    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, rightStrRef);

    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, resultBoolLE);
  } else if (name == "__strLength") {
    assert(call->argExprs.size() == 1);

    auto leftStrRef =
        translateExpression(
            globalState, functionState, blockState, builder, call->argExprs[0]);
    auto resultLenLE = functionState->defaultRegion->getStringLen(functionState, builder, leftStrRef);

    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, leftStrRef);

    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, resultLenLE);
  } else if (name == "__addFloatFloat") {
    // VivemExterns.addFloatFloat
    assert(false);
  } else if (name == "__panic") {
    auto exitCodeLE = makeConstIntExpr(functionState, builder, LLVMInt8TypeInContext(globalState->context), 255);
    LLVMBuildCall(builder, globalState->exit, &exitCodeLE, 1, "");
    LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
    return wrap(functionState->defaultRegion, globalState->metalCache.neverRef, globalState->neverPtr);
  } else if (name == "__multiplyIntInt") {
    assert(call->argExprs.size() == 2);
    auto resultIntLE =
        LLVMBuildMul(
            builder,
            functionState->defaultRegion->checkValidReference(FL(),
                functionState, builder, call->function->params[0],
                translateExpression(
                    globalState, functionState, blockState, builder, call->argExprs[0])),
            functionState->defaultRegion->checkValidReference(FL(),
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
            functionState->defaultRegion->checkValidReference(FL(),
                functionState, builder, call->function->params[0],
                translateExpression(
                    globalState, functionState, blockState, builder, call->argExprs[0])),
            functionState->defaultRegion->checkValidReference(FL(),
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
        getCharsPtrFromWrapperPtr(globalState, builder, destStrWrapperPtrLE),
    };
    LLVMBuildCall(builder, globalState->addStr, argsLE.data(), argsLE.size(), "");

    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, leftStrRef);
    functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, globalState->metalCache.strRef, rightStrRef);

    auto destStrRef = wrap(functionState->defaultRegion, globalState->metalCache.strRef, destStrWrapperPtrLE);
    functionState->defaultRegion->checkValidReference(FL(), functionState, builder, call->function->returnType, destStrRef);

    return destStrRef;
  } else if (name == "__getch") {
    auto resultIntLE = LLVMBuildCall(builder, globalState->getch, nullptr, 0, "");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, resultIntLE);
  } else if (name == "__lessThanInt") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildICmp(
        builder,
        LLVMIntSLT,
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        functionState->defaultRegion->checkValidReference(FL(),
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
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        functionState->defaultRegion->checkValidReference(FL(),
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
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        functionState->defaultRegion->checkValidReference(FL(),
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
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        functionState->defaultRegion->checkValidReference(FL(),
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
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        functionState->defaultRegion->checkValidReference(FL(),
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
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        functionState->defaultRegion->checkValidReference(FL(),
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
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__castIntStr") {
    assert(call->argExprs.size() == 1);
    auto intLE =
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0]));

    int bufferSize = 150;
    auto charsPtrLocalLE =
        makeMidasLocal(
            functionState,
            builder,
            LLVMArrayType(LLVMInt8TypeInContext(globalState->context), bufferSize),
            "charsPtrLocal",
            LLVMGetUndef(LLVMArrayType(LLVMInt8TypeInContext(globalState->context), bufferSize)));
    auto itoaDestPtrLE =
        LLVMBuildPointerCast(
            builder, charsPtrLocalLE, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "itoaDestPtr");
    std::vector<LLVMValueRef> atoiArgsLE = { intLE, itoaDestPtrLE, constI64LE(globalState, bufferSize) };
    LLVMBuildCall(builder, globalState->intToCStr, atoiArgsLE.data(), atoiArgsLE.size(), "");

    std::vector<LLVMValueRef> strlenArgsLE = { itoaDestPtrLE };
    auto lengthLE = LLVMBuildCall(builder, globalState->strlen, strlenArgsLE.data(), strlenArgsLE.size(), "");

    auto strWrapperPtrLE = functionState->defaultRegion->mallocStr(functionState, builder, lengthLE);
    // Set the length
    LLVMBuildStore(builder, lengthLE, getLenPtrFromStrWrapperPtr(builder, strWrapperPtrLE));
    // Fill the chars
    auto charsPtrLE = getCharsPtrFromWrapperPtr(globalState, builder, strWrapperPtrLE);
    std::vector<LLVMValueRef> argsLE = {
        charsPtrLE,
        itoaDestPtrLE,
        lengthLE
    };
    LLVMBuildCall(builder, globalState->initStr, argsLE.data(), argsLE.size(), "");

    buildFlare(FL(), globalState, functionState, builder, "making chars ptr", ptrToVoidPtrLE(globalState, builder, charsPtrLE));

    return wrap(functionState->defaultRegion, globalState->metalCache.strRef, strWrapperPtrLE);
  } else if (name == "__and") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildAnd(
        builder,
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__or") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildOr(
        builder,
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, result);
  } else if (name == "__mod") {
    assert(call->argExprs.size() == 2);
    auto result = LLVMBuildSRem(
        builder,
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[0],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[0])),
        functionState->defaultRegion->checkValidReference(FL(),
            functionState, builder, call->function->params[1],
            translateExpression(
                globalState, functionState, blockState, builder, call->argExprs[1])),
        "");
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, result);
  } else {

    auto args = std::vector<Ref>{};
    args.reserve(call->argExprs.size());
    for (int i = 0; i < call->argExprs.size(); i++) {
      auto argExpr = call->argExprs[i];
      auto argRefMT = call->function->params[i];
      auto argRef = translateExpression(globalState, functionState, blockState, builder, argExpr);
      args.push_back(argRef);
    }

    auto argsLE = std::vector<LLVMValueRef>{};
    argsLE.reserve(call->argExprs.size());
    for (int i = 0; i < call->argExprs.size(); i++) {
      auto argRefMT = call->function->params[i];
      auto argLE = functionState->defaultRegion->checkValidReference(FL(), functionState, builder, argRefMT, args[i]);

      if (call->argTypes[i] == globalState->metalCache.intRef) {
      } else if (call->argTypes[i] == globalState->metalCache.boolRef) {
        // Outside has i8, we have i1, but clang should be fine doing the conversion
      } else if (call->argTypes[i] == globalState->metalCache.floatRef) {
      } else if (call->argTypes[i] == globalState->metalCache.strRef) {
      } else if (call->argTypes[i] == globalState->metalCache.neverRef) {
        assert(false); // How can we hand a never into something?
      } else if (call->argTypes[i] == globalState->metalCache.emptyTupleStructRef) {
        assert(false); // How can we hand a void into something?
      } else if (auto structReferend = dynamic_cast<StructReferend*>(argRefMT->referend)) {
        if (argRefMT->ownership == Ownership::SHARE) {
          if (argRefMT->location == Location::INLINE) {
            assert(LLVMTypeOf(argLE) ==
                functionState->defaultRegion->getReferendStructsSource()->getInnerStruct(structReferend));
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

      argsLE.push_back(argLE);
    }

    auto externFuncIter = globalState->externFunctions.find(call->function->name->name);
    assert(externFuncIter != globalState->externFunctions.end());
    auto externFuncL = externFuncIter->second;

    buildFlare(FL(), globalState, functionState, builder, "Suspending function ", functionState->containingFuncName);
    buildFlare(FL(), globalState, functionState, builder, "Calling extern function ", call->function->name->name);

    auto resultLE = LLVMBuildCall(builder, externFuncL, argsLE.data(), argsLE.size(), "");
    auto resultRef = wrap(functionState->defaultRegion, call->function->returnType, resultLE);
    functionState->defaultRegion->checkValidReference(FL(), functionState, builder, call->function->returnType, resultRef);

    for (int i = 0; i < call->argExprs.size(); i++) {
      auto argRefMT = call->function->params[i];
      // Extern isnt expected to drop the ref, so we do
      functionState->defaultRegion->dealias(FL(), functionState, blockState, builder, argRefMT, args[i]);
    }

    if (call->function->returnType->referend == globalState->metalCache.never) {
      buildFlare(FL(), globalState, functionState, builder, "Done calling function ", call->function->name->name);
      buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncName);
      LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
      return wrap(functionState->defaultRegion, globalState->metalCache.neverRef, globalState->neverPtr);
    } else {
      buildFlare(FL(), globalState, functionState, builder, "Done calling function ", call->function->name->name);
      buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncName);
      return resultRef;
    }
  }
  assert(false);
}
