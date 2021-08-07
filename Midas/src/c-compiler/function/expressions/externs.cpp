#include <iostream>
#include <function/boundary.h>
#include "function/expressions/shared/shared.h"
#include "function/expressions/shared/string.h"
#include "region/common/controlblock.h"
#include "region/common/heap.h"
#include "region/linear/linear.h"

#include "translatetype.h"

#include "function/expression.h"

Ref buildExternCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    const std::vector<Ref>& args) {
  if (prototype->name->name == "__addI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildAdd(builder, leftLE, rightLE,"add");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__multiplyI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto resultIntLE = LLVMBuildMul(builder, leftLE, rightLE, "mul");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultIntLE);
  } else if (prototype->name->name == "__subtractI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto resultIntLE = LLVMBuildSub(builder, leftLE, rightLE, "diff");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultIntLE);
  } else if (prototype->name->name == "__lessThanI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSLT, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__greaterThanI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSGT, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__greaterThanOrEqI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSGE, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__lessThanOrEqI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSLE, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__eqI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntEQ, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__modI32") {
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    assert(args.size() == 2);
    auto result = LLVMBuildSRem( builder, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__divideI32") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildSDiv(builder, leftLE, rightLE,"add");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else
  if (prototype->name->name == "__addI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildAdd(builder, leftLE, rightLE,"add");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__multiplyI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto resultIntLE = LLVMBuildMul(builder, leftLE, rightLE, "mul");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultIntLE);
  } else if (prototype->name->name == "__subtractI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto resultIntLE = LLVMBuildSub(builder, leftLE, rightLE, "diff");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultIntLE);
  } else if (prototype->name->name == "__lessThanI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSLT, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__greaterThanI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSGT, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__greaterThanOrEqI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSGE, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__lessThanOrEqI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntSLE, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__eqI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntEQ, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__modI64") {
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    assert(args.size() == 2);
    auto result = LLVMBuildSRem( builder, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__divideI64") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildSDiv(builder, leftLE, rightLE,"add");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__divideFloatFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildFDiv(builder, leftLE, rightLE,"divided");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__multiplyFloatFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildFMul(builder, leftLE, rightLE,"multiplied");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__subtractFloatFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildFSub(builder, leftLE, rightLE,"subtracted");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__negateFloat") {
    assert(args.size() == 1);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto result = LLVMBuildFNeg(builder, leftLE, "negated");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__strLength") {
    assert(args.size() == 1);
    auto resultLenLE = globalState->getRegion(globalState->metalCache->strRef)->getStringLen(functionState, builder, args[0]);
    globalState->getRegion(globalState->metalCache->strRef)
        ->dealias(FL(), functionState, builder, globalState->metalCache->strRef, args[0]);
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultLenLE);
  } else if (prototype->name->name == "__addFloatFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildFAdd(builder, leftLE, rightLE, "add");
    return wrap(globalState->getRegion(globalState->metalCache->floatRef), globalState->metalCache->floatRef, result);
  } else if (prototype->name->name == "__panic") {
    // See MPESC for status codes
    auto exitCodeLE = makeConstIntExpr(functionState, builder, LLVMInt64TypeInContext(globalState->context), 1);
    LLVMBuildCall(builder, globalState->externs->exit, &exitCodeLE, 1, "");
    LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
    return wrap(globalState->getRegion(globalState->metalCache->neverRef), globalState->metalCache->neverRef, globalState->neverPtr);
  } else if (prototype->name->name == "__getch") {
    auto resultIntLE = LLVMBuildCall(builder, globalState->externs->getch, nullptr, 0, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultIntLE);
  } else if (prototype->name->name == "__eqFloatFloat") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildFCmp(builder, LLVMRealOEQ, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__eqBoolBool") {
    assert(args.size() == 2);
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    auto result = LLVMBuildICmp(builder, LLVMIntEQ, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__not") {
    assert(args.size() == 1);
    auto result = LLVMBuildNot(
        builder,
        checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]),
        "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__and") {
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    assert(args.size() == 2);
    auto result = LLVMBuildAnd( builder, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else if (prototype->name->name == "__or") {
    auto leftLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[0], args[0]);
    auto rightLE = checkValidInternalReference(FL(), globalState, functionState, builder, prototype->params[1], args[1]);
    assert(args.size() == 2);
    auto result = LLVMBuildOr( builder, leftLE, rightLE, "");
    return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, result);
  } else {
    auto valeArgRefs = std::vector<Ref>{};
    valeArgRefs.reserve(args.size());
    for (int i = 0; i < args.size(); i++) {
      valeArgRefs.push_back(args[i]);
    }

    auto hostArgsLE = std::vector<LLVMValueRef>{};
    hostArgsLE.reserve(args.size() + 1);

    auto sizeArgsLE = std::vector<LLVMValueRef>{};
    sizeArgsLE.reserve(args.size() + 1);

    for (int i = 0; i < args.size(); i++) {
      auto valeArgRefMT = prototype->params[i];
      auto hostArgRefMT =
          (valeArgRefMT->ownership == Ownership::SHARE ?
              globalState->linearRegion->linearizeReference(valeArgRefMT) :
              valeArgRefMT);

      auto valeArg = valeArgRefs[i];
      auto [hostArgRefLE, argSizeLE] =
          sendValeObjectIntoHost(
              globalState, functionState, builder, valeArgRefMT, hostArgRefMT, valeArg);
      if (typeNeedsPointerParameter(globalState, valeArgRefMT)) {
        auto hostArgRefLT = globalState->getRegion(valeArgRefMT)->getExternalType(valeArgRefMT);
        assert(LLVMGetTypeKind(hostArgRefLT) != LLVMPointerTypeKind);
        hostArgsLE.push_back(makeMidasLocal(functionState, builder, hostArgRefLT, "ptrParamLocal", hostArgRefLE));
      } else {
        hostArgsLE.push_back(hostArgRefLE);
      }
      if (includeSizeParam(globalState, prototype, i)) {
        sizeArgsLE.push_back(argSizeLE);
      }
    }

    hostArgsLE.insert(hostArgsLE.end(), sizeArgsLE.begin(), sizeArgsLE.end());
    sizeArgsLE.clear();

    auto externFuncIter = globalState->externFunctions.find(prototype->name->name);
    assert(externFuncIter != globalState->externFunctions.end());
    auto externFuncL = externFuncIter->second;

    buildFlare(FL(), globalState, functionState, builder, "Suspending function ", functionState->containingFuncName);
    buildFlare(FL(), globalState, functionState, builder, "Calling extern function ", prototype->name->name);

    LLVMValueRef hostReturnLE = nullptr;
    if (typeNeedsPointerParameter(globalState, prototype->returnType)) {
      auto hostReturnRefLT = globalState->getRegion(prototype->returnType)->getExternalType(prototype->returnType);
      auto localPtrLE = makeMidasLocal(functionState, builder, hostReturnRefLT, "retOutParam", LLVMGetUndef(hostReturnRefLT));
      buildFlare(FL(), globalState, functionState, builder, "Return ptr! ", ptrToIntLE(globalState, builder, localPtrLE));
      hostArgsLE.insert(hostArgsLE.begin(), localPtrLE);
      LLVMBuildCall(builder, externFuncL, hostArgsLE.data(), hostArgsLE.size(), "");
      hostReturnLE = LLVMBuildLoad(builder, localPtrLE, "hostReturn");
      buildFlare(FL(), globalState, functionState, builder, "Loaded the return! ", LLVMABISizeOfType(globalState->dataLayout, LLVMTypeOf(hostReturnLE)));
    } else {
      hostReturnLE = LLVMBuildCall(builder, externFuncL, hostArgsLE.data(), hostArgsLE.size(), "");
    }

    buildFlare(FL(), globalState, functionState, builder, "Done calling function ", prototype->name->name);
    buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncName);

    if (prototype->returnType->kind == globalState->metalCache->never) {
      LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
      return wrap(globalState->getRegion(globalState->metalCache->neverRef), globalState->metalCache->neverRef, globalState->neverPtr);
    } else {
      if (prototype->returnType == globalState->metalCache->emptyTupleStructRef) {
        return makeEmptyTupleRef(globalState);
      } else {
        buildFlare(FL(), globalState, functionState, builder);

        auto valeReturnRefMT = prototype->returnType;
        auto hostReturnMT =
            (valeReturnRefMT->ownership == Ownership::SHARE ?
                globalState->linearRegion->linearizeReference(valeReturnRefMT) :
                valeReturnRefMT);

        auto valeReturnRef =
            receiveHostObjectIntoVale(
                globalState, functionState, builder, hostReturnMT, valeReturnRefMT, hostReturnLE);

        return valeReturnRef;
      }
    }
  }
  assert(false);
}

Ref translateExternCall(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    ExternCall* call) {
  auto name = call->function->name->name;
  auto params = call->function->params;
  std::vector<Ref> args;
  assert(call->argExprs.size() == call->argTypes.size());
  for (int i = 0; i < call->argExprs.size(); i++) {
    args.emplace_back(
        translateExpression(globalState, functionState, blockState, builder, call->argExprs[i]));
  }
  return buildExternCall(globalState, functionState, builder, call->function, args);
}
