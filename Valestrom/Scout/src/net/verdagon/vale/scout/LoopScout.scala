package net.verdagon.vale.scout

import net.verdagon.vale.RangeS
import net.verdagon.vale.parser.ast.{AugmentPE, BlockPE, BorrowP, ConsecutorPE, ConstantBoolPE, FunctionCallPE, IExpressionPE, IterableNameDeclarationP, IterableNameP, IterationOptionNameDeclarationP, IterationOptionNameP, IteratorNameDeclarationP, IteratorNameP, LetPE, LookupNameP, LookupPE, NameP, NotPE, PatternPP, RangeP, ReadonlyP, ReadwriteP, UseP, WhilePE}
import net.verdagon.vale.scout.Scout.noDeclarations

object LoopScout {
  def scoutLoop(
    expressionScout: ExpressionScout,
    stackFrame0: StackFrame,
    lidb: LocationInDenizenBuilder,
    rangeP: RangeP,
    makeContents: (StackFrame, LocationInDenizenBuilder, Boolean) => (StackFrame, BlockSE, VariableUses, VariableUses)):
  (BlockSE, VariableUses, VariableUses) = {
    // This just scopes the iterable's expression so its things dont outlive the foreach block.
    expressionScout.newBlock(
      stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), Scout.evalRange(stackFrame0.file, rangeP),
      noDeclarations, true,
      (stackFrame1, lidb, _) => {
        val (stackFrame2, bodySE, selfUses, childUses) =
          makeContents(stackFrame1, lidb, true)
        val whileSE = WhileSE(Scout.evalRange(stackFrame0.file, rangeP), bodySE)
        (stackFrame2, whileSE, selfUses, childUses)
      })
  }

  def scoutEach(
    expressionScout: ExpressionScout,
    stackFrame0: StackFrame,
    lidb: LocationInDenizenBuilder,
    range: RangeP,
    entryPatternPP: PatternPP,
    inKeywordRange: RangeP,
    iterableExpr: IExpressionPE,
    body: BlockPE):
  (BlockSE, VariableUses, VariableUses) = {
    expressionScout.newBlock(
      stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), Scout.evalRange(stackFrame0.file, range),
      noDeclarations, true,
      (stackFrame1, lidb, _) => {
        val (stackFrame2, letIterableSE, letIterableSelfUses, letIterableChildUses) =
          expressionScout.scoutExpressionAndCoerce(
            stackFrame1, lidb.child(),
            LetPE(
              inKeywordRange, None,
              PatternPP(inKeywordRange, None, Some(IterableNameDeclarationP(inKeywordRange)), None, None, None),
              iterableExpr),
            UseP,
            true)
        val (stackFrame3, letIteratorSE, letIteratorSelfUses, letIteratorChildUses) =
          expressionScout.scoutExpressionAndCoerce(
            stackFrame2, lidb.child(),
            LetPE(
              inKeywordRange, None,
              PatternPP(inKeywordRange, None, Some(IteratorNameDeclarationP(inKeywordRange)), None, None, None),
              FunctionCallPE(
                inKeywordRange, inKeywordRange,
                LookupPE(LookupNameP(NameP(inKeywordRange, "begin")), None),
                Vector(
                  AugmentPE(
                    inKeywordRange, BorrowP, ReadonlyP,
                    LookupPE(IterableNameP(inKeywordRange), None))),
                false)),
            UseP,
            true)

        val (loopSE, loopBodySelfUses, loopBodyChildUses) =
          scoutLoop(
            expressionScout, stackFrame3, lidb.child(), range,
            (stackFrame4, lidb, _) => {
              val (loopBodySE, loopBodySelfUses, lookBodyChildUses) =
                expressionScout.newBlock(
                  stackFrame4.parentEnv, Some(stackFrame4), lidb.child(), Scout.evalRange(stackFrame0.file, range),
                  noDeclarations, true,
                  (stackFrame5, lidb, _) => {
                    scoutLoopBody(expressionScout, stackFrame5, lidb, range, inKeywordRange, entryPatternPP, body)
                  })
              (stackFrame4, loopBodySE, loopBodySelfUses, lookBodyChildUses)
            })

        val contentsSE = Scout.consecutive(Vector(letIterableSE, letIteratorSE, loopSE))

        val selfUses = letIterableSelfUses.thenMerge(letIteratorSelfUses).thenMerge(loopBodySelfUses)
        val childUses = letIterableChildUses.thenMerge(letIteratorChildUses).thenMerge(loopBodyChildUses)

        (stackFrame3, contentsSE, selfUses, childUses)
      })
  }

  def scoutLoopBody(
    expressionScout: ExpressionScout,
    stackFrame0: StackFrame,
    lidb: LocationInDenizenBuilder,
    range: RangeP,
    inKeywordRange: RangeP,
    entryPatternPP: PatternPP,
    bodyPE: BlockPE,
  ): (StackFrame, IExpressionSE, VariableUses, VariableUses) = {
    expressionScout.newIf(
      stackFrame0, lidb, true, range,
      (stackFrame2, lidb, _) => {
        val (stackFrame3, condSE, condSelfUses, condChildUses) =
          expressionScout.scoutExpressionAndCoerce(
            stackFrame2,
            lidb,
            ConsecutorPE(
              Vector(
                LetPE(
                  entryPatternPP.range,
                  None,
                  PatternPP(inKeywordRange, None, Some(IterationOptionNameDeclarationP(inKeywordRange)), None, None, None),
                  FunctionCallPE(
                    inKeywordRange,
                    inKeywordRange,
                    LookupPE(LookupNameP(NameP(inKeywordRange, "next")), None),
                    Vector(
                      AugmentPE(
                        inKeywordRange,
                        BorrowP,
                        ReadwriteP,
                        LookupPE(IteratorNameP(inKeywordRange), None))),
                    false)),
                NotPE(
                  inKeywordRange,
                  FunctionCallPE(
                    inKeywordRange,
                    inKeywordRange,
                    LookupPE(LookupNameP(NameP(inKeywordRange, "isEmpty")), None),
                    Vector(
                      AugmentPE(
                        inKeywordRange,
                        BorrowP,
                        ReadonlyP,
                        LookupPE(IterationOptionNameP(inKeywordRange), None))),
                    false)))),
            UseP,
            true)
        (stackFrame3, condSE, condSelfUses, condChildUses)
      },
      (stackFrame2, lidb, _) => {
        val (thenSE, thenUses, thenChildUses) =
          expressionScout.scoutBlock(
            stackFrame2, lidb.child(), noDeclarations, true,
            BlockPE(
              bodyPE.range,
              ConsecutorPE(
                Vector(
                  LetPE(
                    inKeywordRange,
                    None,
                    entryPatternPP,
                    FunctionCallPE(
                      inKeywordRange,
                      inKeywordRange,
                      LookupPE(LookupNameP(NameP(inKeywordRange, "get")), None),
                      Vector(
                        LookupPE(IterationOptionNameP(inKeywordRange), None)),
                      false)),
                  bodyPE,
                  ConstantBoolPE(inKeywordRange, true)))))
        (stackFrame2, thenSE, thenUses, thenChildUses)
      },
      (stackFrame3, lidb, _) => {
        val (thenSE, thenUses, thenChildUses) =
          expressionScout.scoutBlock(
            stackFrame3, lidb.child(), noDeclarations, true,
            BlockPE(
              bodyPE.range,
              ConsecutorPE(
                Vector(
                  // This should drop it
                  LookupPE(IterationOptionNameP(inKeywordRange), None),
                  ConstantBoolPE(inKeywordRange, false)))))
        (stackFrame3, thenSE, thenUses, thenChildUses)
      })
  }
}