#include <iostream>

#include "../../utils/branch.h"

#include "../../translatetype.h"

#include "../expression.h"
#include "expressions.h"

Ref translateIf(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* parentBlockState,
    LLVMBuilderRef builder,
    If* iff) {
  // First, we compile the condition expressions, into wherever we're currently
  // building. It's not really part of this mess, but we do use the resulting
  // bit of it in the indirect-branch instruction.
  auto conditionExpr =
      translateExpression(
          globalState, functionState, parentBlockState, builder, iff->conditionExpr);

  BlockState thenBlockState(globalState->addressNumberer, parentBlockState, std::nullopt);
  BlockState elseBlockState(globalState->addressNumberer, parentBlockState, std::nullopt);

  auto resultLE =
      buildIfElse(
          globalState,
          functionState,
          builder,
          conditionExpr,
          globalState->getRegion(iff->commonSupertype)->translateType(iff->commonSupertype),
          iff->thenResultType,
          iff->elseResultType,
          [globalState, functionState, &thenBlockState, iff](LLVMBuilderRef thenBlockBuilder) {
            return translateExpression(
                globalState, functionState, &thenBlockState, thenBlockBuilder, iff->thenExpr);
          },
          [globalState, functionState, &elseBlockState, iff](LLVMBuilderRef elseBlockBuilder) {
            return translateExpression(
                globalState, functionState, &elseBlockState, elseBlockBuilder, iff->elseExpr);
          });
  globalState->getRegion(iff->commonSupertype)
      ->checkValidReference(
          FL(), functionState, builder, iff->commonSupertype, resultLE);


  bool thenContinues = iff->thenResultType->kind != globalState->metalCache->never;
  bool elseContinues = iff->elseResultType->kind != globalState->metalCache->never;

  auto thenUnstackifiedParentLocalIds = thenBlockState.getParentLocalIdsThatSelfUnstackified();
  auto elseUnstackifiedParentLocalIds = elseBlockState.getParentLocalIdsThatSelfUnstackified();

  // This is the set of locals that we should unstackify from the parent,
  // because they were unstackified from both, or whichever branch actually
  // survived (wasn't never'd).
  std::unordered_set<VariableId*> branchUnstackifiedParentLocalIds;
  if (thenContinues == elseContinues) { // Both continue, or both don't
    // The same outside-if variables should still exist no matter which branch we went down.
    assert(thenUnstackifiedParentLocalIds == elseUnstackifiedParentLocalIds);

    // Theyre both the same, so arbitrarily use then's.
    branchUnstackifiedParentLocalIds = thenUnstackifiedParentLocalIds;
  } else {
    // One of them continues and the other does not.
    if (thenContinues) {
      // Throw away any information from the else. But do consider those from the then.
      branchUnstackifiedParentLocalIds = thenUnstackifiedParentLocalIds;
    } else if (elseContinues) {
      branchUnstackifiedParentLocalIds = elseUnstackifiedParentLocalIds;
    } else assert(false);
  }

  for (auto childUnstackifiedParentLocalId : branchUnstackifiedParentLocalIds) {
    parentBlockState->markLocalUnstackified(childUnstackifiedParentLocalId);
  }

  return resultLE;
}
