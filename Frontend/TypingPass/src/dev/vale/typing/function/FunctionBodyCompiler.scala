package dev.vale.typing.function

//import dev.vale.astronomer.{AtomSP, FunctionA, BodySE, ExportA, IExpressionSE, IFunctionAttributeA, LocalA, ParameterS, PureA, UserFunctionA}
import dev.vale.{Err, Ok, RangeS, Result, vassert, vcurious, vpass, vwat}
import dev.vale.highertyping.FunctionA
import dev.vale.parsing.ast.INameDeclarationP
import dev.vale.postparsing.patterns.{AtomSP, CaptureS}
import dev.vale.postparsing._
import dev.vale.typing.{BodyResultDoesntMatch, CompileErrorExceptionT, ConvertHelper, CouldntConvertForReturnT, RangedInternalErrorT, Compiler, TypingPassOptions, TemplataCompiler, CompilerOutputs, ast}
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, LocationInFunctionEnvironmentT, ParameterT, ReferenceExpressionTE, ReturnTE}
import dev.vale.typing.env.{FunctionEnvironmentBoxT, NodeEnvironmentT, NodeEnvironmentBox}
import dev.vale.typing.names.NameTranslator
import dev.vale.typing.types._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale._
import dev.vale.postparsing._
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env._

import scala.collection.immutable.{List, Set}

trait IBodyCompilerDelegate {
  def evaluateBlockStatements(
    coutputs: CompilerOutputs,
    startingNenv: NodeEnvironmentT,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    exprs: BlockSE):
  (ReferenceExpressionTE, Set[CoordT])

  def translatePatternList(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    patterns1: Vector[AtomSP],
    patternInputExprs2: Vector[ReferenceExpressionTE]):
  ReferenceExpressionTE
}

class BodyCompiler(
  opts: TypingPassOptions,

  nameTranslator: NameTranslator,

    templataCompiler: TemplataCompiler,
    convertHelper: ConvertHelper,
    delegate: IBodyCompilerDelegate) {

  // Returns:
  // - IF we had to infer it, the return type.
  // - The body.
  def declareAndEvaluateFunctionBody(
    funcOuterEnv: FunctionEnvironmentBoxT,
    coutputs: CompilerOutputs,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
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

      maybeExplicitReturnCoord match {
        case None => {
          val (body2, returns) =
            evaluateFunctionBody(
                funcOuterEnv, coutputs, life, parentRanges, function1.params, params2, bodyS, isDestructor, None) match {
              case Err(ResultTypeMismatchError(expectedType, actualType)) => {
                throw CompileErrorExceptionT(BodyResultDoesntMatch(function1.range :: parentRanges, function1.name, expectedType, actualType))

              }
              case Ok((body, returns)) => (body, returns)
            }

          vassert(body2.result.kind != NeverT(true))
          val returnType2 =
            if (returns.isEmpty && body2.result.kind == NeverT(false)) {
              // No returns yet the body results in a Never. This can happen if we call panic from inside.
              body2.result.coord
            } else {
              vassert(returns.nonEmpty)
              if (returns.size > 1) {
                throw CompileErrorExceptionT(RangedInternalErrorT(bodyS.range :: parentRanges, "Can't infer return type because " + returns.size + " types are returned:" + returns.map("\n" + _)))
              }
              returns.head
            }

          (Some(returnType2), body2)
        }
        case Some(explicitRetCoord) => {
          val (body2, returns) =
            evaluateFunctionBody(
                funcOuterEnv,
                coutputs,
                life,
                parentRanges,
                function1.params,
                params2,
                bodyS,
                isDestructor,
                Some(explicitRetCoord)) match {
              case Err(ResultTypeMismatchError(expectedType, actualType)) => {
                throw CompileErrorExceptionT(BodyResultDoesntMatch(function1.range :: parentRanges, function1.name, expectedType, actualType))
              }
              case Ok((body, returns)) => (body, returns)
            }

          if (returns == Set(explicitRetCoord)) {
            // Let it through, it returns the expected type.
          } else if (returns == Set(CoordT(ShareT, GlobalRegionT(), NeverT(false)))) {
            // Let it through, it returns a never but we expect something else, that's fine
          } else if (returns == Set() && body2.result.kind == NeverT(false)) {
            // Let it through, it doesn't return anything yet it results in a never, which means
            // we called panic or something from inside.
          } else {
            throw CompileErrorExceptionT(CouldntConvertForReturnT(bodyS.range :: parentRanges, explicitRetCoord, returns.head))
          }

          (None, body2)
        }
      }
  }

  case class ResultTypeMismatchError(expectedType: CoordT, actualType: CoordT) {
    val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
    vpass()
  }

  private def evaluateFunctionBody(
    funcOuterEnv: FunctionEnvironmentBoxT,
    coutputs: CompilerOutputs,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    params1: Vector[ParameterS],
    params2: Vector[ParameterT],
    body1: BodySE,
    isDestructor: Boolean,
    maybeExpectedResultType: Option[CoordT]):
  Result[(BlockTE, Set[CoordT]), ResultTypeMismatchError] = {
    val env = NodeEnvironmentBox(funcOuterEnv.makeChildNodeEnvironment(body1.block, life))
    val startingEnv = env.snapshot

    val patternsTE =
      evaluateLets(env, coutputs, life + 0, body1.range :: parentRanges, params1, params2);

    val (statementsFromBlock, returnsFromInsideMaybeWithNever) =
      delegate.evaluateBlockStatements(coutputs, startingEnv, env, life + 1, parentRanges, body1.block);

    val unconvertedBodyWithoutReturn = Compiler.consecutive(Vector(patternsTE, statementsFromBlock))


    val convertedBodyWithoutReturn =
      maybeExpectedResultType match {
        case None => unconvertedBodyWithoutReturn
        case Some(expectedResultType) => {
          if (templataCompiler.isTypeConvertible(coutputs, startingEnv, parentRanges, unconvertedBodyWithoutReturn.result.coord, expectedResultType)) {
            if (unconvertedBodyWithoutReturn.kind == NeverT(false)) {
              unconvertedBodyWithoutReturn
            } else {
              convertHelper.convert(funcOuterEnv.snapshot, coutputs, body1.range :: parentRanges, unconvertedBodyWithoutReturn, expectedResultType);
            }
          } else {
            return Err(ResultTypeMismatchError(expectedResultType, unconvertedBodyWithoutReturn.result.coord))
          }
        }
      }


    // If the function doesn't end in a ret, then add one for it.
    val (convertedBodyWithReturn, returnsMaybeWithNever) =
      if (convertedBodyWithoutReturn.kind == NeverT(false)) {
        (convertedBodyWithoutReturn, returnsFromInsideMaybeWithNever)
      } else {
        (ReturnTE(convertedBodyWithoutReturn), returnsFromInsideMaybeWithNever + convertedBodyWithoutReturn.result.coord)
      }
    // If we already had a ret, then the above will add a Never to the returns, but that's fine, it will be filtered
    // out below.

    val returns =
      if (returnsMaybeWithNever.size > 1 && returnsMaybeWithNever.contains(CoordT(ShareT, GlobalRegionT(), NeverT(false)))) {
        returnsMaybeWithNever - CoordT(ShareT, GlobalRegionT(), NeverT(false))
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
      if (!env.unstackifieds.contains(destructeeName)) {
        throw CompileErrorExceptionT(RangedInternalErrorT(body1.range :: parentRanges, "Destructee wasn't moved/destroyed!"))
      }
    }

    Ok((ast.BlockTE(convertedBodyWithReturn), returns))
  }

  // Produce the lets at the start of a function.
  private def evaluateLets(
    nenv: NodeEnvironmentBox,
      coutputs: CompilerOutputs,
    life: LocationInFunctionEnvironmentT,
    range: List[RangeS],
      params1: Vector[ParameterS],
      params2: Vector[ParameterT]):
  ReferenceExpressionTE = {
    val paramLookups2 =
      params2.zipWithIndex.map({ case (p, index) => ArgLookupTE(index, p.tyype) })
    val letExprs2 =
      delegate.translatePatternList(
        coutputs, nenv, life, range, params1.map(_.pattern), paramLookups2);

    // todo: at this point, to allow for recursive calls, add a callable type to the environment
    // for everything inside the body to use

    params1.foreach({
      case ParameterS(_, _, _, AtomSP(_, Some(CaptureS(name, false)), _, _)) => {
        if (!nenv.declaredLocals.exists(_.name == nameTranslator.translateVarNameStep(name))) {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "wot couldnt find " + name))
        }
      }
      case _ =>
    });

    (letExprs2)
  }

}
