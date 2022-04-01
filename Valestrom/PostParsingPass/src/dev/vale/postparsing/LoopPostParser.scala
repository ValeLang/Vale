package dev.vale.postparsing

import dev.vale.parsing.ast._
import PostParser.{noDeclarations, noVariableUses}
import dev.vale.parsing.ast.{AugmentPE, BlockPE, BorrowP, ConsecutorPE, FunctionCallPE, IExpressionPE, IterableNameDeclarationP, IterableNameP, IterationOptionNameDeclarationP, IterationOptionNameP, IteratorNameDeclarationP, IteratorNameP, LetPE, LookupNameP, LookupPE, NameP, PatternPP, RangeP, UseP}
import dev.vale.postparsing

object LoopPostParser {
  def scoutLoop(
    expressionScout: ExpressionScout,
    stackFrame0: StackFrame,
    lidb: LocationInDenizenBuilder,
    rangeP: RangeP,
    makeContents: (StackFrame, LocationInDenizenBuilder, Boolean) => (StackFrame, BlockSE, VariableUses, VariableUses)):
  (BlockSE, VariableUses, VariableUses) = {
    // This just scopes the iterable's expression so its things dont outlive the foreach block.
    expressionScout.newBlock(
      stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), PostParser.evalRange(stackFrame0.file, rangeP),
      noDeclarations, true,
      (stackFrame1, lidb, _) => {
        val (stackFrame2, bodySE, selfUses, childUses) =
          makeContents(stackFrame1, lidb, true)
        val whileSE = postparsing.WhileSE(PostParser.evalRange(stackFrame0.file, rangeP), bodySE)
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
      stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), PostParser.evalRange(stackFrame0.file, range),
      noDeclarations, true,
      (stackFrame1, lidb, _) => {
        val (stackFrame2, letIterableSE, letIterableSelfUses, letIterableChildUses) =
          expressionScout.scoutExpressionAndCoerce(
            stackFrame1, lidb.child(),
            LetPE(
              inKeywordRange,
              PatternPP(inKeywordRange, None, Some(IterableNameDeclarationP(inKeywordRange)), None, None, None),
              iterableExpr),
            UseP,
            true)
        val (stackFrame3, letIteratorSE, letIteratorSelfUses, letIteratorChildUses) =
          expressionScout.scoutExpressionAndCoerce(
            stackFrame2, lidb.child(),
            LetPE(
              inKeywordRange,
              PatternPP(inKeywordRange, None, Some(IteratorNameDeclarationP(inKeywordRange)), None, None, None),
              FunctionCallPE(
                inKeywordRange, inKeywordRange,
                LookupPE(LookupNameP(NameP(inKeywordRange, "begin")), None),
                Vector(
                  AugmentPE(
                    inKeywordRange, BorrowP,
                    LookupPE(IterableNameP(inKeywordRange), None))))),
            UseP,
            true)

        val (loopSE, loopBodySelfUses, loopBodyChildUses) =
          expressionScout.newBlock(
            stackFrame3.parentEnv, Some(stackFrame3), lidb.child(), PostParser.evalRange(stackFrame0.file, range),
            noDeclarations, true,
            (stackFrame4, lidb, _) => {
              val (loopBodySE, loopBodySelfUses, lookBodyChildUses) =
                expressionScout.newBlock(
                  stackFrame4.parentEnv, Some(stackFrame4), lidb.child(), PostParser.evalRange(stackFrame0.file, range),
                  noDeclarations, true,
                  (stackFrame5, lidb, _) => {
                    scoutEachBody(expressionScout, stackFrame5, lidb, range, inKeywordRange, entryPatternPP, body)
                  })
              val loopSE =
                if (body.producesResult()) {
                  MapSE(PostParser.evalRange(stackFrame0.file, range), loopBodySE)
                } else {
                  postparsing.WhileSE(PostParser.evalRange(stackFrame0.file, range), loopBodySE)
                }
              (stackFrame4, loopSE, loopBodySelfUses, lookBodyChildUses)
            })

        val contentsSE = PostParser.consecutive(Vector(letIterableSE, letIteratorSE, loopSE))

        val selfUses = letIterableSelfUses.thenMerge(letIteratorSelfUses).thenMerge(loopBodySelfUses)
        val childUses = letIterableChildUses.thenMerge(letIteratorChildUses).thenMerge(loopBodyChildUses)

        (stackFrame3, contentsSE, selfUses, childUses)
      })
  }

  def scoutEachBody(
    expressionScout: ExpressionScout,
    stackFrame0: StackFrame,
    lidb: LocationInDenizenBuilder,
    range: RangeP,
    inKeywordRange: RangeP,
    entryPatternPP: PatternPP,
    bodyPE: BlockPE,
  ): (StackFrame, IExpressionSE, VariableUses, VariableUses) = {
    val (stackFrame4, ifSE, ifSelfUses, ifChildUses) =
      expressionScout.newIf(
        stackFrame0, lidb, true, range,
        (stackFrame1, lidb, _) => {
          val (stackFrame3, condSE, condSelfUses, condChildUses) =
            expressionScout.scoutExpressionAndCoerce(
              stackFrame1,
              lidb,
              ConsecutorPE(
                Vector(
                  LetPE(
                    entryPatternPP.range,
                    PatternPP(inKeywordRange, None, Some(IterationOptionNameDeclarationP(inKeywordRange)), None, None, None),
                    FunctionCallPE(
                      inKeywordRange,
                      inKeywordRange,
                      LookupPE(LookupNameP(NameP(inKeywordRange, "next")), None),
                      Vector(
                        AugmentPE(
                          inKeywordRange,
                          BorrowP,
                          LookupPE(IteratorNameP(inKeywordRange), None))))),
                  FunctionCallPE(
                    inKeywordRange,
                    inKeywordRange,
                    LookupPE(LookupNameP(NameP(inKeywordRange, "isEmpty")), None),
                    Vector(
                      AugmentPE(
                        inKeywordRange,
                        BorrowP,
                        LookupPE(IterationOptionNameP(inKeywordRange), None)))))),
              UseP,
              true)
          (stackFrame3, condSE, condSelfUses, condChildUses)
        },
        (stackFrame1, lidb, _) => {
          val (thenSE, thenUses, thenChildUses) =
            expressionScout.newBlock(
              stackFrame1.parentEnv, Some(stackFrame1), lidb.child(), PostParser.evalRange(stackFrame0.file, range), noDeclarations, true,
              (stackFrame2, lidb, _) => {
                val (stackFrame3, lookupSE, lookupSelfUses, lookupChildUses) =
                  expressionScout.scoutExpressionAndCoerce(
                    stackFrame2,
                    lidb,
                    LookupPE(IterationOptionNameP(inKeywordRange), None),
                    UseP, false)
                val breakSE = postparsing.BreakSE(PostParser.evalRange(stackFrame3.file, range))
                val lookupAndBreakSE =
                  PostParser.consecutive(Vector(lookupSE, breakSE))
                (stackFrame3, lookupAndBreakSE, lookupSelfUses, lookupChildUses)
              })
          (stackFrame1, thenSE, thenUses, thenChildUses)
        },
        (stackFrame1, _, _) => {
          // Else does nothing
          val voidSE =
            postparsing.BlockSE(
              PostParser.evalRange(stackFrame1.file, range),
              Vector(),
              postparsing.VoidSE(PostParser.evalRange(stackFrame1.file, range)))
          (stackFrame1, voidSE, noVariableUses, noVariableUses)
        })

    val (stackFrame5, consumeSomeSE, consumeSomeSelfUses, consumeSomeChildUses) =
      expressionScout.scoutExpressionAndCoerce(
        stackFrame4, lidb.child(),
        LetPE(
          inKeywordRange,
          entryPatternPP,
          FunctionCallPE(
            inKeywordRange,
            inKeywordRange,
            LookupPE(LookupNameP(NameP(inKeywordRange, "get")), None),
            Vector(
              LookupPE(IterationOptionNameP(inKeywordRange), None)))),
        UseP,
        false)

    val (userBodySE, userBodySelfUses, userBodyChildUses) =
      expressionScout.scoutBlock(
        stackFrame5, lidb.child(), noDeclarations, false,
        bodyPE)

    val selfUses = ifSelfUses.thenMerge(consumeSomeSelfUses).thenMerge(userBodySelfUses)
    val childUses = ifChildUses.thenMerge(consumeSomeChildUses).thenMerge(userBodyChildUses)
    val loopBodySE =
      PostParser.consecutive(Vector(ifSE, consumeSomeSE, userBodySE))

    (stackFrame5, loopBodySE, selfUses, childUses)
  }


  def scoutWhile(
    expressionScout: ExpressionScout,
    stackFrame0: StackFrame,
    lidb: LocationInDenizenBuilder,
    range: RangeP,
    conditionPE: IExpressionPE,
    body: BlockPE):
  (BlockSE, VariableUses, VariableUses) = {
    expressionScout.newBlock(
      stackFrame0.parentEnv, Some(stackFrame0), lidb.child(), PostParser.evalRange(stackFrame0.file, range),
      noDeclarations, true,
      (stackFrame1, lidb, _) => {
        val (loopSE, loopBodySelfUses, loopBodyChildUses) =
          expressionScout.newBlock(
            stackFrame1.parentEnv, Some(stackFrame1), lidb.child(), PostParser.evalRange(stackFrame0.file, range),
            noDeclarations, true,
            (stackFrame4, lidb, _) => {
              val (loopBodySE, loopBodySelfUses, lookBodyChildUses) =
                expressionScout.newBlock(
                  stackFrame4.parentEnv, Some(stackFrame4), lidb.child(), PostParser.evalRange(stackFrame0.file, range),
                  noDeclarations, true,
                  (stackFrame5, lidb, _) => {
                    scoutWhileBody(expressionScout, stackFrame5, lidb, range, conditionPE, body)
                  })
              val whileSE = postparsing.WhileSE(PostParser.evalRange(stackFrame0.file, range), loopBodySE)
              (stackFrame4, whileSE, loopBodySelfUses, lookBodyChildUses)
            })
        (stackFrame1, loopSE, loopBodySelfUses, loopBodyChildUses)
      })
  }

  def scoutWhileBody(
    expressionScout: ExpressionScout,
    stackFrame0: StackFrame,
    lidb: LocationInDenizenBuilder,
    range: RangeP,
    conditionPE: IExpressionPE,
    bodyPE: BlockPE,
  ): (StackFrame, IExpressionSE, VariableUses, VariableUses) = {
    val (stackFrame4, ifSE, ifSelfUses, ifChildUses) =
      expressionScout.newIf(
        stackFrame0, lidb, true, range,
        (stackFrame2, lidb, _) => {
          val (stackFrame3, condSE, condSelfUses, condChildUses) =
            expressionScout.scoutExpressionAndCoerce(
              stackFrame2, lidb, conditionPE, UseP, true)
          (stackFrame3, condSE, condSelfUses, condChildUses)
        },
        (stackFrame2, lidb, _) => {
          // Then does nothing, just continue on
          val voidSE =
            postparsing.BlockSE(
              PostParser.evalRange(stackFrame2.file, range),
              Vector(),
              postparsing.VoidSE(PostParser.evalRange(stackFrame2.file, range)))
          (stackFrame2, voidSE, noVariableUses, noVariableUses)
        },
        (stackFrame3, _, _) => {
          val (thenSE, thenUses, thenChildUses) =
            expressionScout.newBlock(
              stackFrame3.parentEnv, Some(stackFrame3), lidb.child(), PostParser.evalRange(stackFrame0.file, range), noDeclarations, true,
              (stackFrame4, lidb, _) => {
                val breakSE = postparsing.BreakSE(PostParser.evalRange(stackFrame4.file, range))
                (stackFrame4, breakSE, noVariableUses, noVariableUses)
              })
          (stackFrame3, thenSE, thenUses, thenChildUses)
        })

    val (userBodySE, userBodySelfUses, userBodyChildUses) =
      expressionScout.scoutBlock(
        stackFrame4, lidb.child(), noDeclarations, false,
        bodyPE)

    val selfUses = ifSelfUses.thenMerge(userBodySelfUses)
    val childUses = ifChildUses.thenMerge(userBodyChildUses)
    val loopBodySE =
      PostParser.consecutive(Vector(ifSE, userBodySE))

    (stackFrame4, loopBodySE, selfUses, childUses)
  }
}