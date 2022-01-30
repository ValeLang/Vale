package net.verdagon.vale.templar.function


//import net.verdagon.vale.astronomer.{AtomSP, FunctionA, BodySE, ExportA, IExpressionSE, IFunctionAttributeA, LocalA, ParameterS, PureA, UserFunctionA}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale._
import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.parser.ast.INameDeclarationP
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.{ArgLookupTE, BlockTE, LocationInFunctionEnvironment, ParameterT, ReferenceExpressionTE, ReturnTE}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.names.NameTranslator

import scala.collection.immutable.{List, Set}

trait IBodyTemplarDelegate {
  def evaluateBlockStatements(
    temputs: Temputs,
    startingFate: FunctionEnvironment,
    fate: FunctionEnvironmentBox,
    life: LocationInFunctionEnvironment,
    exprs: BlockSE):
  (ReferenceExpressionTE, Set[CoordT])

  def translatePatternList(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    life: LocationInFunctionEnvironment,
    patterns1: Vector[AtomSP],
    patternInputExprs2: Vector[ReferenceExpressionTE]):
  ReferenceExpressionTE
}

class BodyTemplar(
  opts: TemplarOptions,
  profiler: IProfiler,

    templataTemplar: TemplataTemplar,
    convertHelper: ConvertHelper,
    delegate: IBodyTemplarDelegate) {

  // Returns:
  // - IF we had to infer it, the return type.
  // - The body.
  def declareAndEvaluateFunctionBody(
      funcOuterEnv: FunctionEnvironmentBox,
      temputs: Temputs,
      life: LocationInFunctionEnvironment,
      function1: FunctionA,
      maybeExplicitReturnCoord: Option[CoordT],
      params2: Vector[ParameterT],
      isDestructor: Boolean):
  (Option[CoordT], BlockTE) = {
    val bodyS =
      function1.body match {
        case CodeBodyS(b) => b
        case _ => vwat()
      }

    profiler.childFrame("evaluate body", () => {
      maybeExplicitReturnCoord match {
        case None => {
          val (body2, returns) =
            evaluateFunctionBody(
                funcOuterEnv, temputs, life, function1.params, params2, bodyS, isDestructor, None) match {
              case Err(ResultTypeMismatchError(expectedType, actualType)) => {
                throw CompileErrorExceptionT(BodyResultDoesntMatch(function1.range, function1.name, expectedType, actualType))

              }
              case Ok((body, returns)) => (body, returns)
            }

          val returnType2 =
            if (returns.isEmpty && body2.result.kind == NeverT()) {
              // No returns yet the body results in a Never. This can happen if we call panic from inside.
              body2.result.reference
            } else {
              vassert(returns.nonEmpty)
              if (returns.size > 1) {
                throw CompileErrorExceptionT(RangedInternalErrorT(bodyS.range, "Can't infer return type because " + returns.size + " types are returned:" + returns.map("\n" + _)))
              }
              returns.head
            }

          (Some(returnType2), body2)
        }
        case Some(explicitRetCoord) => {
          val (body2, returns) =
            evaluateFunctionBody(
                funcOuterEnv,
                temputs,
                life,
                function1.params,
                params2,
                bodyS,
                isDestructor,
                Some(explicitRetCoord)) match {
              case Err(ResultTypeMismatchError(expectedType, actualType)) => {
                throw CompileErrorExceptionT(BodyResultDoesntMatch(function1.range, function1.name, expectedType, actualType))
              }
              case Ok((body, returns)) => (body, returns)
            }

          if (returns == Set(explicitRetCoord)) {
            // Let it through, it returns the expected type.
          } else if (returns == Set(CoordT(ShareT, ReadonlyT, NeverT()))) {
            // Let it through, it returns a never but we expect something else, that's fine
          } else if (returns == Set() && body2.result.kind == NeverT()) {
            // Let it through, it doesn't return anything yet it results in a never, which means
            // we called panic or something from inside.
          } else {
            throw CompileErrorExceptionT(CouldntConvertForReturnT(bodyS.range, explicitRetCoord, returns.head))
          }

          (None, body2)
        }
      }
    })
  }

  case class ResultTypeMismatchError(expectedType: CoordT, actualType: CoordT) {
    val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
    vpass()
  }

  private def evaluateFunctionBody(
    funcOuterEnv: FunctionEnvironmentBox,
    temputs: Temputs,
    life: LocationInFunctionEnvironment,
    params1: Vector[ParameterS],
    params2: Vector[ParameterT],
    body1: BodySE,
    isDestructor: Boolean,
    maybeExpectedResultType: Option[CoordT]):
  Result[(BlockTE, Set[CoordT]), ResultTypeMismatchError] = {
    val env = funcOuterEnv.makeChildBlockEnvironment(Some(body1.block))
    val startingEnv = env.functionEnvironment

    val patternsTE =
      evaluateLets(env, temputs, life + 0, body1.range, params1, params2);

    val (statementsFromBlock, returnsFromInsideMaybeWithNever) =
      delegate.evaluateBlockStatements(temputs, startingEnv, env, life + 1, body1.block);

    val unconvertedBodyWithoutReturn = Templar.consecutive(Vector(patternsTE, statementsFromBlock))


    val convertedBodyWithoutReturn =
      maybeExpectedResultType match {
        case None => unconvertedBodyWithoutReturn
        case Some(expectedResultType) => {
          if (templataTemplar.isTypeConvertible(temputs, unconvertedBodyWithoutReturn.result.reference, expectedResultType)) {
            if (unconvertedBodyWithoutReturn.kind == NeverT()) {
              unconvertedBodyWithoutReturn
            } else {
              convertHelper.convert(funcOuterEnv.snapshot, temputs, body1.range, unconvertedBodyWithoutReturn, expectedResultType);
            }
          } else {
            return Err(ResultTypeMismatchError(expectedResultType, unconvertedBodyWithoutReturn.result.reference))
          }
        }
      }


    // If the function doesn't end in a ret, then add one for it.
    val (convertedBodyWithReturn, returnsMaybeWithNever) =
      if (convertedBodyWithoutReturn.kind == NeverT()) {
        (convertedBodyWithoutReturn, returnsFromInsideMaybeWithNever)
      } else {
        (ReturnTE(convertedBodyWithoutReturn), returnsFromInsideMaybeWithNever + convertedBodyWithoutReturn.result.reference)
      }
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
      if (!env.unstackifieds.exists(_.last == destructeeName)) {
        throw CompileErrorExceptionT(RangedInternalErrorT(body1.range, "Destructee wasn't moved/destroyed!"))
      }
    }

    Ok((BlockTE(convertedBodyWithReturn), returns))
  }

  // Produce the lets at the start of a function.
  private def evaluateLets(
      fate: FunctionEnvironmentBox,
      temputs: Temputs,
    life: LocationInFunctionEnvironment,
    range: RangeS,
      params1: Vector[ParameterS],
      params2: Vector[ParameterT]):
  ReferenceExpressionTE = {
    val paramLookups2 =
      params2.zipWithIndex.map({ case (p, index) => ArgLookupTE(index, p.tyype) })
    val letExprs2 =
      delegate.translatePatternList(
        temputs, fate, life, params1.map(_.pattern), paramLookups2);

    // todo: at this point, to allow for recursive calls, add a callable type to the environment
    // for everything inside the body to use

    params1.foreach({
      case ParameterS(AtomSP(_, Some(CaptureS(name)), _, _, _)) => {
        if (!fate.declaredLocals.exists(_.id.last == NameTranslator.translateVarNameStep(name))) {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "wot couldnt find " + name))
        }
      }
      case _ =>
    });

    (letExprs2)
  }

}
