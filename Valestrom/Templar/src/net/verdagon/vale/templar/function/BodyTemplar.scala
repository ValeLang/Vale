package net.verdagon.vale.templar.function


import net.verdagon.vale.astronomer.{AtomAP, BFunctionA, BodyAE, ExportA, IExpressionAE, IFunctionAttributeA, LocalA, ParameterA, PureA, UserFunctionA}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser.CaptureP
import net.verdagon.vale._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.templata.TemplataTemplar

import scala.collection.immutable.{List, Nil, Set}

trait IBodyTemplarDelegate {
  def evaluateBlockStatements(
    temputs: Temputs,
    startingFate: FunctionEnvironment,
    fate: FunctionEnvironmentBox,
    exprs: List[IExpressionAE]):
  (List[ReferenceExpressionTE], Set[CoordT])

  def nonCheckingTranslateList(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    patterns1: List[AtomAP],
    patternInputExprs2: List[ReferenceExpressionTE]):
  (List[ReferenceExpressionTE])
}

class BodyTemplar(
  opts: TemplarOptions,
  profiler: IProfiler,
  newTemplataStore: () => TemplatasStore,
    templataTemplar: TemplataTemplar,
    convertHelper: ConvertHelper,
    delegate: IBodyTemplarDelegate) {

  def evaluateFunctionBody(
      funcOuterEnv: FunctionEnvironmentBox,
      temputs: Temputs,
      params1: List[ParameterA],
      params2: List[ParameterT],
      body1: BodyAE,
      isDestructor: Boolean,
      maybeExpectedResultType: Option[CoordT]):
  Result[(BlockTE, Set[CoordT]), ResultTypeMismatchError] = {
    val env = funcOuterEnv.makeChildEnvironment(newTemplataStore)
    val startingEnv = env.functionEnvironment

    val letExprs2 =
      evaluateLets(funcOuterEnv, temputs, body1.range, params1, params2);

    val (statementsFromBlock, returnsFromInsideMaybeWithNever) =
      delegate.evaluateBlockStatements(temputs, startingEnv, funcOuterEnv, body1.block.exprs);

    vassert(statementsFromBlock.nonEmpty)

    val letsAndExpressionsUnconvertedWithoutReturn = letExprs2 ++ statementsFromBlock
    val initExprs = letsAndExpressionsUnconvertedWithoutReturn.init
    val lastUnconvertedUnreturnedExpr = letsAndExpressionsUnconvertedWithoutReturn.last


    val lastUnreturnedExpr =
      maybeExpectedResultType match {
        case None => lastUnconvertedUnreturnedExpr
        case Some(expectedResultType) => {
          if (templataTemplar.isTypeTriviallyConvertible(temputs, lastUnconvertedUnreturnedExpr.resultRegister.reference, expectedResultType)) {
            if (lastUnconvertedUnreturnedExpr.kind == NeverT()) {
              lastUnconvertedUnreturnedExpr
            } else {
              convertHelper.convert(funcOuterEnv.snapshot, temputs, body1.range, lastUnconvertedUnreturnedExpr, expectedResultType);
            }
          } else {
            return Err(ResultTypeMismatchError(expectedResultType, lastUnconvertedUnreturnedExpr.resultRegister.reference))
          }
        }
      }


    // If the function doesn't end in a ret, then add one for it.
    val lastExpr = if (lastUnreturnedExpr.kind == NeverT()) lastUnreturnedExpr else ReturnTE(lastUnreturnedExpr)
    // Add that result type to the returns, since we just added a Return for it.
    val returnsMaybeWithNever = returnsFromInsideMaybeWithNever + lastUnreturnedExpr.resultRegister.reference
    // If we already had a return, then the above will add a Never to the returns, but that's fine, it will be filtered
    // out below.

    val returns =
      if (returnsMaybeWithNever.size > 1 && returnsMaybeWithNever.contains(CoordT(ShareT, ReadonlyT, NeverT()))) {
        returnsMaybeWithNever - CoordT(ShareT, ReadonlyT, NeverT())
      } else {
        returnsMaybeWithNever
      }


    if (isDestructor) {
      // If it's a destructor, make sure that we've actually destroyed/moved/unlet'd
      // the parameter. For now, we'll just check if it's been moved away, but soon
      // we'll want fate to track whether it's been destroyed, and do that check instead.
      // We don't want the user to accidentally just move it somewhere, they need to
      // promise it gets destroyed.
      val destructeeName = params2.head.name
      if (!funcOuterEnv.unstackifieds.exists(_.last == destructeeName)) {
        throw CompileErrorExceptionT(RangedInternalErrorT(body1.range, "Destructee wasn't moved/destroyed!"))
      }
    }

    val block2 = BlockTE(initExprs :+ lastExpr)

    Ok((block2, returns))
  }

  // Produce the lets at the start of a function.
  private def evaluateLets(
      fate: FunctionEnvironmentBox,
      temputs: Temputs,
    range: RangeS,
      params1: List[ParameterA],
      params2: List[ParameterT]):
  (List[ReferenceExpressionTE]) = {
    val paramLookups2 =
      params2.zipWithIndex.map({ case (p, index) => ArgLookupTE(index, p.tyype) })
    val letExprs2 =
      delegate.nonCheckingTranslateList(
        temputs, fate, params1.map(_.pattern), paramLookups2);

    // todo: at this point, to allow for recursive calls, add a callable type to the environment
    // for everything inside the body to use

    params1.foreach({
      case ParameterA(AtomAP(_, LocalA(name, _, _, _, _, _, _), _, _, _)) => {
        if (!fate.locals.exists(_.id.last == NameTranslator.translateVarNameStep(name))) {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "wot couldnt find " + name))
        }
      }
      case _ =>
    });

    (letExprs2)
  }

}
