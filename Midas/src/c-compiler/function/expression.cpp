#include <iostream>
#include <function/expressions/shared/branch.h>
#include "function/expressions/shared/elements.h"
#include "function/expressions/shared/controlblock.h"
#include "function/expressions/shared/members.h"
#include "function/expressions/shared/heap.h"

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

LLVMValueRef makeConstIntExpr(LLVMBuilderRef builder, LLVMTypeRef type, int value) {
  auto localAddr = LLVMBuildAlloca(builder, type, "");
  LLVMBuildStore(
      builder,
      LLVMConstInt(type, value, false),
      localAddr);
  return LLVMBuildLoad(builder, localAddr, "");
}

LLVMValueRef translateExpression(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Expression* expr) {
  if (auto constantI64 = dynamic_cast<ConstantI64*>(expr)) {
    // See ULTMCIE for why we load and store here.
    return makeConstIntExpr(builder, LLVMInt64Type(), constantI64->value);
  } else if (auto constantBool = dynamic_cast<ConstantBool*>(expr)) {
    // See ULTMCIE for why this is an add.
    return makeConstIntExpr(builder, LLVMInt1Type(), constantBool->value);
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
    if (localLoad->local->type->location == Location::INLINE) {
      auto localAddr = functionState->getLocalAddr(*localLoad->local->id);
      return LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
    } else {
      auto localAddr = functionState->getLocalAddr(*localLoad->local->id);
      auto ptrLE =
          LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
      adjustRc(AFL("LocalLoad"), globalState, builder, ptrLE, localLoad->local->type, 1);
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
        AFL("NewStruct"), globalState, builder, newStruct->resultType, memberExprs);
  } else if (auto block = dynamic_cast<Block*>(expr)) {
    auto exprs =
        translateExpressions(globalState, functionState, builder, block->exprs);
    assert(!exprs.empty());
    return exprs.back();
  } else if (auto iff = dynamic_cast<If*>(expr)) {
    return translateIf(globalState, functionState, builder, iff);
  } else if (auto whiile = dynamic_cast<While*>(expr)) {
    return translateWhile(globalState, functionState, builder, whiile);
  } else if (auto destructureM = dynamic_cast<Destroy*>(expr)) {
    return translateDestructure(globalState, functionState, builder, destructureM);
  } else if (auto memberLoad = dynamic_cast<MemberLoad*>(expr)) {
    auto structExpr =
        translateExpression(
            globalState, functionState, builder, memberLoad->structExpr);
    auto mutability = ownershipToMutability(memberLoad->structType->ownership);
    auto memberIndex = memberLoad->memberIndex;
    auto memberName = memberLoad->memberName;
    auto resultLE =
        loadMember(
            AFL("MemberLoad"),
            globalState,
            builder,
            memberLoad->structType,
            structExpr,
            mutability,
            memberLoad->expectedResultType,
            memberIndex,
            memberName);
    discard(
        AFL("MemberLoad drop struct"),
        globalState, functionState, builder, memberLoad->structType, structExpr);
    return resultLE;
  } else if (auto destroyKnownSizeArrayIntoFunction = dynamic_cast<DestroyKnownSizeArrayIntoFunction*>(expr)) {
    auto consumerType = destroyKnownSizeArrayIntoFunction->consumerType;
    auto arrayWrapperLE =
        translateExpression(
            globalState, functionState, builder, destroyKnownSizeArrayIntoFunction->arrayExpr);
    auto arrayLE =
        getCountedContentsPtr(builder, arrayWrapperLE);
    auto consumerLE =
        translateExpression(
            globalState, functionState, builder, destroyKnownSizeArrayIntoFunction->consumerExpr);


    LLVMValueRef consumeIndexPtrLE = LLVMBuildAlloca(builder, LLVMInt64Type(), "consumeIndexPtr");
    LLVMBuildStore(builder, LLVMConstInt(LLVMInt64Type(), 0, false), consumeIndexPtrLE);

    LLVMBasicBlockRef bodyBlockL =
        LLVMAppendBasicBlock(
            functionState->containingFunc,
            functionState->nextBlockName().c_str());
    LLVMBuilderRef bodyBlockBuilder = LLVMCreateBuilder();
    LLVMPositionBuilderAtEnd(bodyBlockBuilder, bodyBlockL);

    // Jump from our previous block into the body for the first time.
    LLVMBuildBr(builder, bodyBlockL);

    auto consumeIndexLE = LLVMBuildLoad(bodyBlockBuilder, consumeIndexPtrLE, "consumeIndex");
    auto isBeforeEndLE =
        LLVMBuildICmp(
            bodyBlockBuilder,
            LLVMIntSLT,
            consumeIndexLE,
            LLVMConstInt(LLVMInt64Type(), destroyKnownSizeArrayIntoFunction->arrayReferend->size, false),
            "consumeIndexIsBeforeEnd");

    auto continueLE =
        buildIfElse(
            functionState, bodyBlockBuilder, isBeforeEndLE, LLVMInt1Type(),
            [globalState, functionState, arrayLE, consumerLE, consumerType, consumeIndexPtrLE](
                LLVMBuilderRef thenBlockBuilder) {

              acquireReference(AFL("DestroyKSAIntoF consume iteration"), globalState, thenBlockBuilder, consumerType, consumerLE);

              std::vector<LLVMValueRef> indices = {
                  LLVMConstInt(LLVMInt64Type(), 0, false),
                  LLVMBuildLoad(thenBlockBuilder, consumeIndexPtrLE, "consumeIndex")
              };
              auto elementPtrLE = LLVMBuildGEP(thenBlockBuilder, arrayLE, indices.data(), indices.size(), "elementPtr");
              auto elementLE = LLVMBuildLoad(thenBlockBuilder, elementPtrLE, "element");
              std::vector<LLVMValueRef> argExprsLE = {
                  consumerLE,
                  elementLE
              };
              buildInterfaceCall(thenBlockBuilder, argExprsLE, 0, 0);

              adjustCounter(thenBlockBuilder, consumeIndexPtrLE, 1);
              // Return true, so the while loop will keep executing.
              return makeConstIntExpr(thenBlockBuilder, LLVMInt1Type(), 1);
            },
            [globalState, functionState](LLVMBuilderRef elseBlockBuilder) {
              // Return false, so the while loop will stop executing.
              return makeConstIntExpr(elseBlockBuilder, LLVMInt1Type(), 0);
            });

    LLVMBasicBlockRef afterwardBlockL =
        LLVMAppendBasicBlock(
            functionState->containingFunc,
            functionState->nextBlockName().c_str());

    LLVMBuildCondBr(bodyBlockBuilder, continueLE, bodyBlockL, afterwardBlockL);

    LLVMPositionBuilderAtEnd(builder, afterwardBlockL);

    freeStruct(
        AFL("DestroyKSAIntoF"), globalState, functionState, builder,
        arrayWrapperLE, destroyKnownSizeArrayIntoFunction->arrayType);

    discard(
        AFL("DestroyKSAIntoF"), globalState, functionState, builder,
        destroyKnownSizeArrayIntoFunction->consumerType, consumerLE);

    return makeNever();
  } else if (auto knownSizeArrayLoad = dynamic_cast<KnownSizeArrayLoad*>(expr)) {
    auto arrayLE =
        translateExpression(
            globalState, functionState, builder, knownSizeArrayLoad->arrayExpr);
    auto indexLE =
        translateExpression(
            globalState, functionState, builder, knownSizeArrayLoad->indexExpr);
    auto mutability = ownershipToMutability(knownSizeArrayLoad->arrayType->ownership);
    discard(
        AFL("KSALoad"), globalState, functionState, builder,
        knownSizeArrayLoad->arrayType, arrayLE);
    return loadElement(
        globalState,
        builder,
        knownSizeArrayLoad->arrayType,
        arrayLE,
        mutability,
        indexLE);
  } else if (auto newArrayFromValues = dynamic_cast<NewArrayFromValues*>(expr)) {
    return translateNewArrayFromValues(globalState, functionState, builder, newArrayFromValues);
  } else if (auto interfaceCall = dynamic_cast<InterfaceCall*>(expr)) {
    return translateInterfaceCall(
        globalState, functionState, builder, interfaceCall);
  } else if (auto memberStore = dynamic_cast<MemberStore*>(expr)) {
    auto sourceExpr =
        translateExpression(
            globalState, functionState, builder, memberStore->sourceExpr);
    auto structExpr =
        translateExpression(
            globalState, functionState, builder, memberStore->structExpr);
    auto structReferend =
        dynamic_cast<StructReferend*>(memberStore->structType->referend);
    auto structDefM = globalState->program->getStruct(structReferend->fullName);
    auto memberIndex = memberStore->memberIndex;
    auto memberName = memberStore->memberName;
    auto oldMemberLE =
        swapMember(
            builder, structDefM, structExpr, memberIndex, memberName, sourceExpr);
    discard(
        AFL("MemberStore discard struct"), globalState, functionState, builder,
        memberStore->structType, structExpr);
    return oldMemberLE;
  } else if (auto structToInterfaceUpcast =
      dynamic_cast<StructToInterfaceUpcast*>(expr)) {
    auto sourceLE =
        translateExpression(
            globalState, functionState, builder, structToInterfaceUpcast->sourceExpr);

    // If it was inline before, upgrade it to a yonder struct.
    // This however also means that small imm virtual params must be pointers,
    // and value-ify themselves immediately inside their bodies.
    // If the receiver expects a yonder, then they'll assume its on the heap.
    // But if receiver expects an inl, its in a register.
    // But we can only interfacecall with a yonder.
    // So we need a thunk to receive that yonder, copy it, fire it into the
    // real function.
    // fuck... thunks. didnt want to do that.

    // alternative:
    // what if we made it so someone receiving an override of an imm inl interface
    // just takes in that much memory? it really just means a bit of wasted stack
    // space, but it means we wouldnt need any thunking.
    // It also means we wouldnt need any heap allocating.
    // So, the override function will receive the entire interface, and just
    // assume that the right thing is in there.
    // Any callers will also have to wrap in an interface. but theyre copying
    // anyway so should be fine.

    // alternative:
    // only inline primitives. Which cant have interfaces anyway.
    // maybe the best solution for now?

    // maybe function params that are inl can take a pointer, and they can
    // just copy it immediately?

    assert(structToInterfaceUpcast->sourceStructType->location != Location::INLINE);

    auto interfaceRefLT =
        globalState->getInterfaceRefStruct(
            structToInterfaceUpcast->targetInterfaceRef->fullName);

    auto interfaceRefLE = LLVMGetUndef(interfaceRefLT);
    interfaceRefLE =
        LLVMBuildInsertValue(
            builder,
            interfaceRefLE,
            getControlBlockPtr(builder, sourceLE, structToInterfaceUpcast->sourceStructType),
            0,
            "interfaceRefWithOnlyObj");
    interfaceRefLE =
        LLVMBuildInsertValue(
            builder,
            interfaceRefLE,
            globalState->getInterfaceTablePtr(
                globalState->program->getStruct(
                    structToInterfaceUpcast->sourceStructId->fullName)
                    ->getEdgeForInterface(structToInterfaceUpcast->targetInterfaceRef->fullName)),
            1,
            "interfaceRef");
    return interfaceRefLE;
  } else {
    std::string name = typeid(*expr).name();
    std::cout << name << std::endl;
    assert(false);
  }
  assert(false);
}
