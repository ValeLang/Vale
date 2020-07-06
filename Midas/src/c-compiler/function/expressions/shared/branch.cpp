#include "shared.h"

#include "translatetype.h"

#include <functional>

void buildIf(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    std::function<void(LLVMBuilderRef)> buildThen) {

  // We already are in the "current" block (which is what `builder` is
  // pointing at currently), but we're about to make two more: "then" and
  // "afterward".
  //              .-----> then -----.
  //  current ---:                   :---> afterward
  //              '-----------------'
  // Right now, the `builder` is pointed at the "current" block.
  // After we're done, we'll change it to point at the "afterward" block, so
  // that subsequent instructions (after the If) can keep using the same
  // builder, but they'll be adding to the "afterward" block we're making
  // here.

  LLVMBasicBlockRef thenBlockL =
      LLVMAppendBasicBlock(
          functionState->containingFunc,
          functionState->nextBlockName().c_str());
  LLVMBuilderRef thenBlockBuilder = LLVMCreateBuilder();
  LLVMPositionBuilderAtEnd(thenBlockBuilder, thenBlockL);

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlock(
          functionState->containingFunc,
          functionState->nextBlockName().c_str());

  LLVMBuildCondBr(builder, conditionLE, thenBlockL, afterwardBlockL);

  // Now, we fill in the "then" block.
  buildThen(thenBlockBuilder);
  // Instruction to jump to the afterward block.
  LLVMBuildBr(thenBlockBuilder, afterwardBlockL);

  LLVMDisposeBuilder(thenBlockBuilder);

  // Like explained above, here we're re-pointing the `builder` to point at
  // the afterward block, so that subsequent instructions (after the If) can
  // keep using the same builder, but they'll be adding to the "afterward"
  // block we're making here.
  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);

  // We're done with the "current" block, and also the "then" and "else"
  // blocks, nobody else will write to them now.
  // We re-pointed the `builder` to point at the "afterward" block, and
  // subsequent instructions after the if will keep adding to that.
}

LLVMValueRef buildIfElse(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    LLVMTypeRef resultTypeL,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildThen,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildElse) {

  // We already are in the "current" block (which is what `builder` is
  // pointing at currently), but we're about to make three more: "then",
  // "else", and "afterward".
  //              .-----> then -----.
  //  current ---:                   :---> afterward
  //              '-----> else -----'
  // Right now, the `builder` is pointed at the "current" block.
  // After we're done, we'll change it to point at the "afterward" block, so
  // that subsequent instructions (after the If) can keep using the same
  // builder, but they'll be adding to the "afterward" block we're making
  // here.

  LLVMBasicBlockRef thenBlockL =
      LLVMAppendBasicBlock(
          functionState->containingFunc,
          functionState->nextBlockName().c_str());
  LLVMBuilderRef thenBlockBuilder = LLVMCreateBuilder();
  LLVMPositionBuilderAtEnd(thenBlockBuilder, thenBlockL);

  LLVMBasicBlockRef elseBlockL =
      LLVMAppendBasicBlock(
          functionState->containingFunc,
          functionState->nextBlockName().c_str());
  LLVMBuilderRef elseBlockBuilder = LLVMCreateBuilder();
  LLVMPositionBuilderAtEnd(elseBlockBuilder, elseBlockL);

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlock(
          functionState->containingFunc,
          functionState->nextBlockName().c_str());

  LLVMBuildCondBr(builder, conditionLE, thenBlockL, elseBlockL);

  // Now, we fill in the "then" block.
  auto thenExpr = buildThen(thenBlockBuilder);
  // Instruction to jump to the afterward block.
  LLVMBuildBr(thenBlockBuilder, afterwardBlockL);
  LLVMDisposeBuilder(thenBlockBuilder);

  // Now, we fill in the "else" block.
  auto elseExpr = buildElse(elseBlockBuilder);
  // Instruction to jump to the afterward block.
  LLVMBuildBr(elseBlockBuilder, afterwardBlockL);
  LLVMDisposeBuilder(elseBlockBuilder);

  // Like explained above, here we're re-pointing the `builder` to point at
  // the afterward block, so that subsequent instructions (after the If) can
  // keep using the same builder, but they'll be adding to the "afterward"
  // block we're making here.
  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);

  // Now, we fill in the afterward block, to receive the result value of the
  // then or else block, whichever we just came from.
  auto phi = LLVMBuildPhi(builder, resultTypeL, "");
  LLVMValueRef incomingValueRefs[2] = { thenExpr, elseExpr };
  LLVMBasicBlockRef incomingBlocks[2] = { thenBlockL, elseBlockL };
  LLVMAddIncoming(phi, incomingValueRefs, incomingBlocks, 2);

  // We're done with the "current" block, and also the "then" and "else"
  // blocks, nobody else will write to them now.
  // We re-pointed the `builder` to point at the "afterward" block, and
  // subsequent instructions after the if will keep adding to that.

  return phi;
}

void buildWhile(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildBody) {

  // While only has a body expr, no separate condition.
  // If the body itself returns true, then we'll run the body again.

  // We already are in the "current" block (which is what `builder` is
  // pointing at currently), but we're about to make two more: "body" and
  // "afterward".
  //              .-----> body -----.
  //  current ---'         ↑         :---> afterward
  //                       `--------'
  // Right now, the `builder` is pointed at the "current" block.
  // After we're done, we'll change it to point at the "afterward" block, so
  // that subsequent instructions (after the While) can keep using the same
  // builder, but they'll be adding to the "afterward" block we're making
  // here.

  LLVMBasicBlockRef bodyBlockL =
      LLVMAppendBasicBlock(
          functionState->containingFunc,
          functionState->nextBlockName().c_str());
  LLVMBuilderRef bodyBlockBuilder = LLVMCreateBuilder();
  LLVMPositionBuilderAtEnd(bodyBlockBuilder, bodyBlockL);

  // Jump from our previous block into the body for the first time.
  LLVMBuildBr(builder, bodyBlockL);

  auto continueLE = buildBody(bodyBlockBuilder);

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlock(
          functionState->containingFunc,
          functionState->nextBlockName().c_str());

  LLVMBuildCondBr(bodyBlockBuilder, continueLE, bodyBlockL, afterwardBlockL);

  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);
}

void buildWhile(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    std::function<LLVMValueRef(LLVMBuilderRef)> buildCondition,
    std::function<void(LLVMBuilderRef)> buildBody) {
  buildWhile(
      functionState,
      builder,
      [functionState, buildCondition, buildBody](LLVMBuilderRef bodyBuilder) {
        auto conditionLE = buildCondition(bodyBuilder);
        return buildIfElse(
            functionState, bodyBuilder, conditionLE, LLVMInt1Type(),
            [buildBody](LLVMBuilderRef thenBlockBuilder) {
              buildBody(thenBlockBuilder);
              // Return true, so the while loop will keep executing.
              return makeConstIntExpr(thenBlockBuilder, LLVMInt1Type(), 1);
            },
            [](LLVMBuilderRef elseBlockBuilder) {
              // Return false, so the while loop will stop executing.
              return makeConstIntExpr(elseBlockBuilder, LLVMInt1Type(), 0);
            });
      });
}
