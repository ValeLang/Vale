#include <iostream>
#include <function/expressions/shared/shared.h>

#include "translatetype.h"

#include "function/expression.h"

LLVMValueRef translateWhile(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    While* whiile) {
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

  LLVMBasicBlockRef afterwardBlockL =
      LLVMAppendBasicBlock(
          functionState->containingFunc,
          functionState->nextBlockName().c_str());

  // First, we make the "current" block jump into the "body" block.
  LLVMBuildBr(builder, bodyBlockL);

  // Now, we fill in the body block.
  auto bodyExpr =
      translateExpression(
          globalState, functionState, bodyBlockBuilder, whiile->bodyExpr);

  // Add a conditional branch to go to either itself, or the afterward block.
  LLVMBuildCondBr(bodyBlockBuilder, bodyExpr, bodyBlockL, afterwardBlockL);

  // Like explained above, here we're re-pointing the `builder` to point at
  // the afterward block, so that subsequent instructions (after the While)
  // can keep using the same builder, but they'll be adding to the "afterward"
  // block we're making here.
  LLVMPositionBuilderAtEnd(builder, afterwardBlockL);

  // Nobody should use a result of a while, so we'll just return a Never.
  return makeNever();
}
