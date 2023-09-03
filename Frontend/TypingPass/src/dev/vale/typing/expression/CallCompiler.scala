package dev.vale.typing.expression

import dev.vale._
import dev.vale.postparsing._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing.GlobalFunctionFamilyNameS
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing.function._
import dev.vale.typing.types._
import dev.vale.typing.{ast, _}
import dev.vale.typing.ast._

import dev.vale.typing.names._

import scala.collection.immutable.List

class CallCompiler(
  opts: TypingPassOptions,
  interner: Interner,
  keywords: Keywords,
  templataCompiler: TemplataCompiler,
  convertHelper: ConvertHelper,
  localHelper: LocalHelper,
  overloadCompiler: OverloadResolver
) {

  private def evaluateCall(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    range: List[RangeS],
    callLocation: LocationInDenizen,
    contextRegion: RegionT,
    callableExpr: ReferenceExpressionTE,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Vector[IRuneS],
    givenArgsExprs2: Vector[ReferenceExpressionTE]):
  (ReferenceExpressionTE) = {
    callableExpr.result.coord.kind match {
      case NeverT(true) => vwat()
      case NeverT(false) | BoolT() => {
        throw CompileErrorExceptionT(RangedInternalErrorT(
          range,
          "wot " + callableExpr.result.coord.kind))
      }
      case OverloadSetT(overloadSetEnv, functionName) => {
        val unconvertedArgsPointerTypes2 =
          givenArgsExprs2.map(_.result.expectReference().coord)

        // We want to get the prototype here, not the entire header, because
        // we might be in the middle of a recursive call like:
        // func main():Int(main())

        val StampFunctionSuccess(pure, maybeNewRegion, prototype, inferences) =
          overloadCompiler.findFunction(
            overloadSetEnv,
            coutputs,
            range,
            callLocation,
            functionName,
            explicitTemplateArgRulesS,
            explicitTemplateArgRunesS,
            contextRegion,
            unconvertedArgsPointerTypes2,
            Vector.empty,
            false) match {
            case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
            case Ok(x) => x
          }
        val argsExprs2 =
          convertHelper.convertExprs(
            nenv.snapshot,
            coutputs,
            range,
            callLocation,
            givenArgsExprs2,
            prototype.paramTypes)

        checkTypes(
          coutputs,
          nenv.snapshot,
          range,
          callLocation,
          prototype.paramTypes,
          argsExprs2.map(a => a.result.coord),
          exact = true)

        vassert(coutputs.getInstantiationBounds(prototype.id).nonEmpty)
        // DO NOT SUBMIT merge this with the other code before FunctionCallTE's elsewhere
        val resultTE =
          maybeNewRegion match {
            case None => prototype.returnType
            case Some(newRegion) => {
              // If we get any instances that are part of the newRegion, we need to interpret them
              // to the contextRegion.
              TemplataCompiler.mergeCoordRegions(
                opts.globalOptions.sanityCheck,
                interner, coutputs, Map(newRegion -> contextRegion), prototype.returnType)
            }
          }
        FunctionCallTE(prototype, pure, maybeNewRegion, argsExprs2, resultTE)
      }
      case other => {
        evaluateCustomCall(
          nenv,
          coutputs,
          life,
          range,
          callLocation,
          contextRegion,
          callableExpr.result.coord,
          explicitTemplateArgRulesS,
          explicitTemplateArgRunesS,
          callableExpr,
          givenArgsExprs2)
      }
    }
  }

  // given args means, the args that the user gave, like in
  // a = 6;
  // f = {[a](x) print(6, x) };
  // f(4);
  // in the f(4), the given args is just 4.
  //
  // however, since f is actually a struct, it's secretly this:
  // a = 6;
  // f = {[a](x) print(6, x) };
  // f.__function(f.__closure, 4);
  // in that f.__function(f.__closure, 4), the given args is just 4, but the actual args is f.__closure and 4.
  // also, the given callable is f, but the actual callable is f.__function.

  // By "custom call" we mean calling __call.
  private def evaluateCustomCall(
    nenv: NodeEnvironmentBox,
    coutputs: CompilerOutputs,
    life: LocationInFunctionEnvironmentT,
    range: List[RangeS],
    callLocation: LocationInDenizen,
    contextRegion: RegionT,
    coord: CoordT,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Vector[IRuneS],
    givenCallableUnborrowedExpr2: ReferenceExpressionTE,
    givenArgsExprs2: Vector[ReferenceExpressionTE]
  ):
  (FunctionCallTE) = {
    // Whether we're given a borrow or an own, the call itself will be given a borrow.
    val givenCallableBorrowExpr2 =
      givenCallableUnborrowedExpr2.result.coord match {
        case CoordT(BorrowT | ShareT, _, _) => (givenCallableUnborrowedExpr2)
        case CoordT(OwnT, _, _) => {
          localHelper.makeTemporaryLocal(
            coutputs,
            nenv,
            range,
            callLocation,
            life,
            contextRegion,
            givenCallableUnborrowedExpr2,
            BorrowT)
        }
      }

    val env = nenv.snapshot
    //    val env = coutputs.getEnvForKind(kind)
    //      citizenRef match {
    //        case sr @ StructTT(_) => coutputs.getEnvForKind(sr) // coutputs.envByStructRef(sr)
    //        case ir @ InterfaceTT(_) => coutputs.getEnvForKind(ir) // coutputs
    //        .envByInterfaceRef(ir)
    //      }

    val argsTypes2 = givenArgsExprs2.map(_.result.coord)
    vcurious(coord.ownership == givenCallableBorrowExpr2.result.coord.ownership)
    val closureParamType = coord
    val paramFilters = Vector(closureParamType) ++ argsTypes2
    val resolved =
      overloadCompiler.findFunction(
        env,
        coutputs,
        range,
        callLocation,
        interner.intern(CodeNameS(keywords.underscoresCall)),
        explicitTemplateArgRulesS,
        explicitTemplateArgRunesS,
        contextRegion,
        paramFilters,
        Vector.empty,
        false) match {
        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
        case Ok(x) => x
      }

    val mutability = Compiler.getMutability(coutputs, coord.kind)
    val ownership =
      mutability match {
        case MutabilityTemplataT(MutableT) => BorrowT
        case MutabilityTemplataT(ImmutableT) => ShareT
        case PlaceholderTemplataT(idT, MutabilityTemplataType()) => BorrowT
      }
    vassert(givenCallableBorrowExpr2.result.coord.ownership == ownership)
    val actualCallableExpr2 = givenCallableBorrowExpr2

    val actualArgsExprs2 = Vector(actualCallableExpr2) ++ givenArgsExprs2

    val StampFunctionSuccess(pure, maybeNewRegion, prototype, inferences) = resolved

    val argTypes = actualArgsExprs2.map(_.result.coord)
    if (argTypes != resolved.prototype.paramTypes) {
      throw CompileErrorExceptionT(RangedInternalErrorT(
        range,
        "arg param type mismatch. params: " +
          prototype.paramTypes +
          " args: " +
          argTypes))
    }

    checkTypes(
      coutputs,
      env,
      range,
      callLocation,
      resolved.prototype.paramTypes,
      argTypes,
      exact = true)

    vassert(coutputs.getInstantiationBounds(resolved.prototype.id).nonEmpty)
    // DO NOT SUBMIT merge this with the other place that calculates the call result
    val resultTE =
      maybeNewRegion match {
        case None => prototype.returnType
        case Some(newRegion) => {
          // If we get any instances that are part of the newRegion, we need to interpret them
          // to the contextRegion.
          TemplataCompiler.mergeCoordRegions(
            opts.globalOptions.sanityCheck,
            interner, coutputs, Map(newRegion -> contextRegion), prototype.returnType)
        }
      }
    val resultingExpr2 = FunctionCallTE(resolved.prototype, pure, maybeNewRegion, actualArgsExprs2, vimpl());

    (resultingExpr2)
  }


  def checkTypes(
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    params: Vector[CoordT],
    args: Vector[CoordT],
    exact: Boolean
  ):
  Unit = {
    vassert(params.size == args.size)
    params.zip(args).foreach({ case (paramsHead, argsHead) =>
      if (paramsHead == argsHead) {

      } else {
        if (!exact) {
          templataCompiler.isTypeConvertible(
            coutputs,
            callingEnv,
            parentRanges,
            callLocation,
            argsHead,
            paramsHead) match {
            case (true) => {

            }
            case (false) => {
              // do stuff here.
              // also there is one special case here, which is when we try to hand in
              // an owning when they just want a borrow, gotta account for that here
              vfail("do stuff " + argsHead + " and " + paramsHead)
            }
          }
        } else {
          argsHead.kind match {
            case NeverT(_) => {
              // This is fine, no conversion will ever actually happen.
              // This can be seen in this call: +(5, panic())
            }
            case _ => {
              // do stuff here.
              // also there is one special case here, which is when we try to hand in
              // an owning when they just want a borrow, gotta account for that here
              vfail("do stuff " + argsHead + " and " + paramsHead)
            }
          }
        }
      }
    })
  }

  def evaluatePrefixCall(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    range: List[RangeS],
    callLocation: LocationInDenizen,
    region: RegionT,
    callableReferenceExpr2: ReferenceExpressionTE,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Vector[IRuneS],
    argsExprs2: Vector[ReferenceExpressionTE]
  ):
  (ReferenceExpressionTE) = {
    val callExpr =
      evaluateCall(
        coutputs,
        nenv,
        life,
        range,
        callLocation,
        region,
        callableReferenceExpr2,
        explicitTemplateArgRulesS,
        explicitTemplateArgRunesS,
        argsExprs2)
    (callExpr)
  }
}