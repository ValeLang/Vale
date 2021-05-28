package net.verdagon.vale.templar.function


import net.verdagon.vale.astronomer.{AtomAP, BFunctionA, BodyAE, ExportA, IExpressionAE, IFunctionAttributeA, LocalVariableA, ParameterA, PureA, UserFunctionA}
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
  (List[ReferenceExpression2], Set[Coord])

  def nonCheckingTranslateList(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    patterns1: List[AtomAP],
    patternInputExprs2: List[ReferenceExpression2]):
  (List[ReferenceExpression2])
}

class BodyTemplar(
  opts: TemplarOptions,
  profiler: IProfiler,
  newTemplataStore: () => TemplatasStore,
    templataTemplar: TemplataTemplar,
    convertHelper: ConvertHelper,
    delegate: IBodyTemplarDelegate) {

  def declareAndEvaluateFunctionBody(
      funcOuterEnv: FunctionEnvironmentBox,
      temputs: Temputs,
      bfunction1: BFunctionA,
      params2: List[Parameter2],
      isDestructor: Boolean):
  (FunctionHeader2, Block2) = {
    val BFunctionA(function1, _) = bfunction1;
    val functionFullName = funcOuterEnv.fullName

    profiler.childFrame("evaluate body", () => {
      function1.maybeRetCoordRune match {
        case None => {
          val banner = FunctionBanner2(Some(function1), functionFullName, params2)
          val (body2, returns) =
            evaluateFunctionBody(
                funcOuterEnv, temputs, bfunction1.origin.params, params2, bfunction1.body, isDestructor, None) match {
              case Err(ResultTypeMismatchError(expectedType, actualType)) => {
                throw CompileErrorExceptionT(BodyResultDoesntMatch(bfunction1.origin.range, function1.name, expectedType, actualType))

              }
              case Ok((body, returns)) => (body, returns)
            }

          vassert(returns.nonEmpty)
          if (returns.size > 1) {
            throw CompileErrorExceptionT(RangedInternalErrorT(bfunction1.body.range, "Can't infer return type because " + returns.size + " types are returned:" + returns.map("\n" + _)))
          }
          val returnType2 = returns.head

          temputs.declareFunctionReturnType(banner.toSignature, returnType2)
          val attributesA = translateAttributes(function1.attributes)
          val header = FunctionHeader2(functionFullName, attributesA, params2, returnType2, Some(function1));

          (header, body2)
        }
        case Some(expectedRetCoordRune) => {
          val CoordTemplata(expectedRetCoord) =
            vassertSome(
              funcOuterEnv.getNearestTemplataWithAbsoluteName2(
                NameTranslator.translateRune(expectedRetCoordRune),
                Set(TemplataLookupContext)))
          val header = FunctionHeader2(functionFullName, translateAttributes(function1.attributes), params2, expectedRetCoord, Some(function1));
          temputs.declareFunctionReturnType(header.toSignature, expectedRetCoord)

          funcOuterEnv.setReturnType(Some(expectedRetCoord))

          val funcOuterEnvSnapshot = funcOuterEnv.snapshot
          val (body2, returns) =
            evaluateFunctionBody(
                funcOuterEnv,
                temputs,
                bfunction1.origin.params,
                params2,
                bfunction1.body,
                isDestructor,
                Some(expectedRetCoord)) match {
              case Err(ResultTypeMismatchError(expectedType, actualType)) => {
                throw CompileErrorExceptionT(BodyResultDoesntMatch(bfunction1.origin.range, bfunction1.origin.name, expectedType, actualType))
              }
              case Ok((body, returns)) => (body, returns)
            }

          if (returns == Set(expectedRetCoord)) {
            // Let it through, it returns the expected type.
          } else if (returns == Set(Coord(Share, Readonly, Never2()))) {
            // Let it through, it returns a never but we expect something else, that's fine
          } else {
            throw CompileErrorExceptionT(RangedInternalErrorT(bfunction1.body.range, "In function " + header + ":\nExpected return type " + expectedRetCoord + " but was " + returns))
          }

          (header, body2)
        }
      }
    })
  }

  def translateAttributes(attributesA: List[IFunctionAttributeA]) = {
    attributesA.map({
      case ExportA(packageCoord) => Export2(packageCoord)
      case UserFunctionA => UserFunction2
      case PureA => Pure2
    })
  }

  case class ResultTypeMismatchError(expectedType: Coord, actualType: Coord)

  private def evaluateFunctionBody(
      funcOuterEnv: FunctionEnvironmentBox,
      temputs: Temputs,
      params1: List[ParameterA],
      params2: List[Parameter2],
      body1: BodyAE,
      isDestructor: Boolean,
      maybeExpectedResultType: Option[Coord]):
  Result[(Block2, Set[Coord]), ResultTypeMismatchError] = {
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
            if (lastUnconvertedUnreturnedExpr.referend == Never2()) {
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
    val lastExpr = if (lastUnreturnedExpr.referend == Never2()) lastUnreturnedExpr else Return2(lastUnreturnedExpr)
    // Add that result type to the returns, since we just added a Return for it.
    val returnsMaybeWithNever = returnsFromInsideMaybeWithNever + lastUnreturnedExpr.resultRegister.reference
    // If we already had a return, then the above will add a Never to the returns, but that's fine, it will be filtered
    // out below.

    val returns =
      if (returnsMaybeWithNever.size > 1 && returnsMaybeWithNever.contains(Coord(Share, Readonly, Never2()))) {
        returnsMaybeWithNever - Coord(Share, Readonly, Never2())
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

    val block2 = Block2(initExprs :+ lastExpr)

    Ok((block2, returns))
  }

  // Produce the lets at the start of a function.
  private def evaluateLets(
      fate: FunctionEnvironmentBox,
      temputs: Temputs,
    range: RangeS,
      params1: List[ParameterA],
      params2: List[Parameter2]):
  (List[ReferenceExpression2]) = {
    val paramLookups2 =
      params2.zipWithIndex.map({ case (p, index) => ArgLookup2(index, p.tyype) })
    val letExprs2 =
      delegate.nonCheckingTranslateList(
        temputs, fate, params1.map(_.pattern), paramLookups2);

    // todo: at this point, to allow for recursive calls, add a callable type to the environment
    // for everything inside the body to use

    params1.foreach({
      case ParameterA(AtomAP(_, LocalVariableA(name, _, _, _, _, _, _, _), _, _, _)) => {
        if (!fate.locals.exists(_.id.last == NameTranslator.translateVarNameStep(name))) {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "wot couldnt find " + name))
        }
      }
      case _ =>
    });

    (letExprs2)
  }

}
