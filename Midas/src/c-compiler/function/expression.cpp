#include <iostream>
#include <function/expressions/shared/elements.h>
#include <function/expressions/shared/members.h>

#include "translatetype.h"

#include "expressions/expressions.h"
#include "expressions/shared/shared.h"
#include "expressions/shared/members.h"
#include "expression.h"

std::vector<LLVMValueRef> translateExpressions(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::vector<Expression*> exprs) {
  auto result = std::vector<LLVMValueRef>{};
  result.reserve(exprs.size());
  for (auto expr : exprs) {
    result.push_back(
        translateExpression(globalState, functionState, builder, expr));
  }
  return result;
}

LLVMValueRef translateExpression(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Expression* expr) {
  if (auto constantI64 = dynamic_cast<ConstantI64*>(expr)) {
    // See AZTMCIE for why we load and store here.
    auto localAddr = LLVMBuildAlloca(builder, LLVMInt64Type(), "");
    LLVMBuildStore(
        builder,
        LLVMConstInt(LLVMInt64Type(), constantI64->value, false),
        localAddr);
    return LLVMBuildLoad(builder, localAddr, "");
  } else if (auto constantBool = dynamic_cast<ConstantBool*>(expr)) {
    // See AZTMCIE for why this is an add.
    auto localAddr = LLVMBuildAlloca(builder, LLVMInt1Type(), "");
    LLVMBuildStore(
        builder,
        LLVMConstInt(LLVMInt1Type(), constantBool->value, false),
        localAddr);
    return LLVMBuildLoad(builder, localAddr, "");
  } else if (auto discardM = dynamic_cast<Discard*>(expr)) {
    return translateDiscard(globalState, functionState, builder, discardM);
  } else if (auto ret = dynamic_cast<Return*>(expr)) {
    return LLVMBuildRet(
        builder,
        translateExpression(
            globalState, functionState, builder, ret->sourceExpr));
  } else if (auto stackify = dynamic_cast<Stackify*>(expr)) {
    auto valueToStore =
        translateExpression(
            globalState, functionState, builder, stackify->sourceExpr);
    makeLocal(
        globalState, functionState, builder, stackify->local, valueToStore);
    return makeNever();
  } else if (auto localStore = dynamic_cast<LocalStore*>(expr)) {
    // The purpose of LocalStore is to put a swap value into a local, and give
    // what was in it.
    auto localAddr = functionState->getLocalAddr(*localStore->local->id);
    auto oldValue =
        LLVMBuildLoad(builder, localAddr, localStore->localName.c_str());
    auto valueToStore =
        translateExpression(
            globalState, functionState, builder, localStore->sourceExpr);
    LLVMBuildStore(builder, valueToStore, localAddr);
    return oldValue;
  } else if (auto localLoad = dynamic_cast<LocalLoad*>(expr)) {
    if (isInlImm(globalState, localLoad->local->type)) {
      auto localAddr = functionState->getLocalAddr(*localLoad->local->id);
      return LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
    } else {
      auto localAddr = functionState->getLocalAddr(*localLoad->local->id);
      auto ptrLE =
          LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
      adjustRC(builder, ptrLE, 1);
      return ptrLE;
    }
  } else if (auto unstackify = dynamic_cast<Unstackify*>(expr)) {
    // The purpose of Unstackify is to destroy the local and give what was in
    // it, but in LLVM there's no instruction (or need) for destroying a local.
    // So, we just give what was in it. It's ironically identical to LocalLoad.
    auto localAddr = functionState->getLocalAddr(*unstackify->local->id);
    return LLVMBuildLoad(builder, localAddr, "");
  } else if (auto call = dynamic_cast<Call*>(expr)) {
    return translateCall(globalState, functionState, builder, call);
  } else if (auto externCall = dynamic_cast<ExternCall*>(expr)) {
    return translateExternCall(globalState, functionState, builder, externCall);
  } else if (auto argument = dynamic_cast<Argument*>(expr)) {
    return LLVMGetParam(functionState->containingFunc, argument->argumentIndex);
  } else if (auto newStruct = dynamic_cast<NewStruct*>(expr)) {
    auto memberExprs =
        translateExpressions(
            globalState, functionState, builder, newStruct->sourceExprs);
    return translateConstruct(
        globalState, builder, newStruct->resultType, memberExprs);
  } else if (auto block = dynamic_cast<Block*>(expr)) {
    auto exprs =
        translateExpressions(globalState, functionState, builder, block->exprs);
    assert(!exprs.empty());
    return exprs.back();
  } else if (auto iff = dynamic_cast<If*>(expr)) {
    return translateIf(globalState, functionState, builder, iff);
  } else if (auto whiile = dynamic_cast<While*>(expr)) {
    return translateWhile(globalState, functionState, builder, whiile);
  } else if (auto destructureM = dynamic_cast<Destructure*>(expr)) {
    return translateDestructure(globalState, functionState, builder, destructureM);
  } else if (auto memberLoad = dynamic_cast<MemberLoad*>(expr)) {
    auto structExpr =
        translateExpression(
            globalState, functionState, builder, memberLoad->structExpr);
    auto mutability = ownershipToMutability(memberLoad->structType->ownership);
    auto memberIndex = memberLoad->memberIndex;
    auto memberName = memberLoad->memberName;
    dropReference(
        globalState, functionState, builder, memberLoad->structType, structExpr);
    return loadMember(
        globalState,
        builder,
        memberLoad->structType,
        structExpr,
        mutability,
        memberIndex,
        memberName);
  } else if (auto knownSizeArrayLoad = dynamic_cast<KnownSizeArrayLoad*>(expr)) {
    auto arrayLE =
        translateExpression(
            globalState, functionState, builder, knownSizeArrayLoad->arrayExpr);
    auto indexLE =
        translateExpression(
            globalState, functionState, builder, knownSizeArrayLoad->indexExpr);
    auto mutability = ownershipToMutability(knownSizeArrayLoad->arrayType->ownership);
    dropReference(
        globalState, functionState, builder, knownSizeArrayLoad->arrayType, arrayLE);
    return loadElement(
        globalState,
        builder,
        knownSizeArrayLoad->arrayType,
        arrayLE,
        mutability,
        indexLE);
  } else if (auto newArrayFromValues = dynamic_cast<NewArrayFromValues*>(expr)) {
    return translateNewArrayFromValues(globalState, functionState, builder, newArrayFromValues);
  } else {
    std::string name = typeid(*expr).name();
    std::cout << name << std::endl;
    assert(false);
  }
  assert(false);
}
