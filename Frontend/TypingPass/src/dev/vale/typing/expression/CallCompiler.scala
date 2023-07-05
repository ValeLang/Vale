package dev.vale.typing.expression

import dev.vale.{Err, Interner, Keywords, Ok, RangeS, vassert, vfail, vimpl, vwat}
import dev.vale.postparsing._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing.GlobalFunctionFamilyNameS
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.{CompileErrorExceptionT, Compiler, CompilerOutputs, ConvertHelper, CouldntFindFunctionToCallT, OverloadResolver, RangedInternalErrorT, TemplataCompiler, TypingPassOptions, ast}
import dev.vale.typing.ast.{FunctionCallTE, LocationInFunctionEnvironmentT, ReferenceExpressionTE}
import dev.vale.typing.env.{FunctionEnvironmentBoxT, IInDenizenEnvironmentT, NodeEnvironmentT, NodeEnvironmentBox}
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.typing.{ast, _}
import dev.vale.typing.ast._

import scala.collection.immutable.List

class CallCompiler(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    templataCompiler: TemplataCompiler,
    convertHelper: ConvertHelper,
    localHelper: LocalHelper,
    overloadCompiler: OverloadResolver) {

  private def evaluateCall(
      coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironmentT,
      range: List[RangeS],
      callableExpr: ReferenceExpressionTE,
      explicitTemplateArgRulesS: Vector[IRulexSR],
      explicitTemplateArgRunesS: Vector[IRuneS],
      givenArgsExprs2: Vector[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    callableExpr.result.coord.kind match {
      case NeverT(true) => vwat()
      case NeverT(false) | BoolT() => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "wot " + callableExpr.result.coord.kind))
      }
      case OverloadSetT(overloadSetEnv, functionName) => {
        val unconvertedArgsPointerTypes2 =
          givenArgsExprs2.map(_.result.expectReference().coord)

        // We want to get the prototype here, not the entire header, because
        // we might be in the middle of a recursive call like:
        // func main():Int(main())

        val prototype =
          overloadCompiler.findFunction(
              overloadSetEnv,
              coutputs,
              range,
              functionName,
              explicitTemplateArgRulesS,
              explicitTemplateArgRunesS,
              unconvertedArgsPointerTypes2,
              Vector.empty,
              false,
              true) match {
            case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
            case Ok(x) => x
          }
        val argsExprs2 =
          convertHelper.convertExprs(
            nenv.snapshot, coutputs, range, givenArgsExprs2, prototype.prototype.prototype.paramTypes)

        checkTypes(
          coutputs,
          nenv.snapshot,
          range,
          prototype.prototype.prototype.paramTypes,
          argsExprs2.map(a => a.result.coord),
          exact = true)

        vassert(coutputs.getInstantiationBounds(prototype.prototype.prototype.id).nonEmpty)
        (ast.FunctionCallTE(prototype.prototype.prototype, argsExprs2))
      }
      case other => {
        evaluateCustomCall(
          nenv,
          coutputs,
          life,
          range,
          callableExpr.result.coord.kind,
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
      kind: KindT,
      explicitTemplateArgRulesS: Vector[IRulexSR],
      explicitTemplateArgRunesS: Vector[IRuneS],
      givenCallableUnborrowedExpr2: ReferenceExpressionTE,
      givenArgsExprs2: Vector[ReferenceExpressionTE]):
      (FunctionCallTE) = {
    // Whether we're given a borrow or an own, the call itself will be given a borrow.
    val givenCallableBorrowExpr2 =
      givenCallableUnborrowedExpr2.result.coord match {
        case CoordT(BorrowT | ShareT, _, _) => (givenCallableUnborrowedExpr2)
        case CoordT(OwnT, _, _) => {
          localHelper.makeTemporaryLocal(coutputs, nenv, range, life, givenCallableUnborrowedExpr2, BorrowT)
        }
      }

    val env = nenv.snapshot
//    val env = coutputs.getEnvForKind(kind)
//      citizenRef match {
//        case sr @ StructTT(_) => coutputs.getEnvForKind(sr) // coutputs.envByStructRef(sr)
//        case ir @ InterfaceTT(_) => coutputs.getEnvForKind(ir) // coutputs.envByInterfaceRef(ir)
//      }

    val argsTypes2 = givenArgsExprs2.map(_.result.coord)
    val closureParamType = CoordT(givenCallableBorrowExpr2.result.coord.ownership, GlobalRegionT(), kind)
    val paramFilters = Vector(closureParamType) ++ argsTypes2
    val resolved =
      overloadCompiler.findFunction(
        env, coutputs, range,
        interner.intern(CodeNameS(keywords.underscoresCall)),
        explicitTemplateArgRulesS, explicitTemplateArgRunesS, paramFilters, Vector.empty, false, true) match {
        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
        case Ok(x) => x
      }

    val mutability = Compiler.getMutability(coutputs, kind)
    val ownership =
      mutability match {
        case MutabilityTemplataT(MutableT) => BorrowT
        case MutabilityTemplataT(ImmutableT) => ShareT
        case PlaceholderTemplataT(idT, MutabilityTemplataType()) => BorrowT
      }
    vassert(givenCallableBorrowExpr2.result.coord.ownership == ownership)
    val actualCallableExpr2 = givenCallableBorrowExpr2

    val actualArgsExprs2 = Vector(actualCallableExpr2) ++ givenArgsExprs2

    val argTypes = actualArgsExprs2.map(_.result.coord)
    if (argTypes != resolved.prototype.prototype.paramTypes) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "arg param type mismatch. params: " + resolved.prototype.prototype.paramTypes + " args: " + argTypes))
    }

    checkTypes(coutputs, env, range, resolved.prototype.prototype.paramTypes, argTypes, exact = true)

    vassert(coutputs.getInstantiationBounds(resolved.prototype.prototype.id).nonEmpty)
    val resultingExpr2 = FunctionCallTE(resolved.prototype.prototype, actualArgsExprs2);

    (resultingExpr2)
  }


  def checkTypes(
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT,
    parentRanges: List[RangeS],
    params: Vector[CoordT],
    args: Vector[CoordT],
    exact: Boolean):
  Unit = {
    vassert(params.size == args.size)
    params.zip(args).foreach({ case (paramsHead, argsHead) =>
      if (paramsHead == argsHead) {

      } else {
        if (!exact) {
          templataCompiler.isTypeConvertible(coutputs, callingEnv, parentRanges, argsHead, paramsHead) match {
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
//    checkTypes(params.tail, args.tail)
//    vassert(argTypes == callableType.paramTypes, "arg param type mismatch. params: " + callableType.paramTypes + " args: " + argTypes)
  }

  def evaluatePrefixCall(
      coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    range: List[RangeS],
      callableReferenceExpr2: ReferenceExpressionTE,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Vector[IRuneS],
    argsExprs2: Vector[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    val callExpr =
      evaluateCall(
        coutputs, nenv, life, range, callableReferenceExpr2, explicitTemplateArgRulesS, explicitTemplateArgRunesS, argsExprs2)
    (callExpr)
  }
}