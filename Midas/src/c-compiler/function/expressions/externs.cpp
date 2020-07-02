#include <iostream>
#include <function/expressions/shared/shared.h>

#include "translatetype.h"

#include "function/expression.h"

LLVMValueRef translateExternCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    ExternCall* call) {
  auto name = call->function->name->name;
  if (name == "F(\"__addIntInt\",[],[R(*,i),R(*,i)])") {
    assert(call->argExprs.size() == 2);
    return LLVMBuildAdd(
        builder,
        translateExpression(
            globalState, functionState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, builder, call->argExprs[1]),
        "add");
  } else if (name == "F(\"__addFloatFloat\",[],[R(*,f),R(*,f)])") {
    // VivemExterns.addFloatFloat
    assert(false);
  } else if (name == "F(\"panic\")") {
    return makeNever();
  } else if (name == "F(\"__multiplyIntInt\",[],[R(*,i),R(*,i)])") {
    assert(call->argExprs.size() == 2);
    return LLVMBuildMul(
        builder,
        translateExpression(
            globalState, functionState, builder, call->argExprs[0]),
        translateExpression(
            globalState, functionState, builder, call->argExprs[1]),
        "mul");
  } else if (name == "F(\"__subtractIntInt\",[],[R(*,i),R(*,i)])") {
    // VivemExterns.subtractIntInt
    assert(false);
  } else if (name == "F(\"__addStrStr\",[],[R(*,s),R(*,s)])") {
    // VivemExterns.addStrStr
    assert(false);
  } else if (name == "F(\"__getch\")") {
    // VivemExterns.getch
    assert(false);
  } else if (name == "F(\"__lessThanInt\",[],[R(*,i),R(*,i)])") {
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
  } else if (name == "F(\"__greaterThanOrEqInt\",[],[R(*,i),R(*,i)])") {
    // VivemExterns.greaterThanOrEqInt
    assert(false);
  } else if (name == "F(\"__eqIntInt\",[],[R(*,i),R(*,i)])") {
    // VivemExterns.eqIntInt
    assert(false);
  } else if (name == "F(\"__eqBoolBool\",[],[R(*,b),R(*,b)])") {
    // VivemExterns.eqBoolBool
    assert(false);
  } else if (name == "F(\"__print\",[],[R(*,s)])") {
    // VivemExterns.print
    assert(false);
  } else if (name == "F(\"__not\",[],[R(*,b)])") {
    // VivemExterns.not
    assert(false);
  } else if (name == "F(\"__castIntStr\",[],[R(*,i)])") {
    // VivemExterns.castIntStr
    assert(false);
  } else if (name == "F(\"__and\",[],[R(*,b),R(*,b)])") {
    // VivemExterns.and
    assert(false);
  } else if (name == "F(\"__mod\",[],[R(*,i),R(*,i)])") {
    // VivemExterns.mod
    assert(false);
  } else {
    assert(false);
  }
}
