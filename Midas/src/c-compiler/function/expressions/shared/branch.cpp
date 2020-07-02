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

  // Now, we fill in the "else" block.
  auto elseExpr = buildElse(elseBlockBuilder);
  // Instruction to jump to the afterward block.
  LLVMBuildBr(elseBlockBuilder, afterwardBlockL);

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
