package dev.vale.typing.expression

import dev.vale.{Err, Interner, Keywords, Ok, RangeS, vassert, vfail, vwat}
import dev.vale.postparsing.{CodeNameS, IImpreciseNameS, IRuneS}
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing.GlobalFunctionFamilyNameS
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.{CompileErrorExceptionT, Compiler, CompilerOutputs, ConvertHelper, CouldntFindFunctionToCallT, OverloadResolver, RangedInternalErrorT, TemplataCompiler, TypingPassOptions, ast}
import dev.vale.typing.ast.{FunctionCallTE, LocationInFunctionEnvironment, ReferenceExpressionTE}
import dev.vale.typing.env.{NodeEnvironment, NodeEnvironmentBox}
import dev.vale.typing.types.{BoolT, BorrowT, CitizenRefT, CoordT, InterfaceTT, MutableT, NeverT, OverloadSetT, OwnT, ParamFilter, ShareT, StructTT}
import dev.vale.typing.env.FunctionEnvironmentBox
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
      life: LocationInFunctionEnvironment,
      range: RangeS,
      callableExpr: ReferenceExpressionTE,
      explicitTemplateArgRulesS: Vector[IRulexSR],
      explicitTemplateArgRunesS: Array[IRuneS],
      givenArgsExprs2: Vector[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    callableExpr.result.reference.kind match {
      case NeverT(true) => vwat()
      case NeverT(false) | BoolT() => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "wot " + callableExpr.result.reference.kind))
      }
      case structTT @ StructTT(_) => {
        evaluateClosureCall(
          nenv, coutputs, life, range, structTT, explicitTemplateArgRulesS, explicitTemplateArgRunesS, callableExpr, givenArgsExprs2)
      }
      case interfaceTT @ InterfaceTT(_) => {
        evaluateClosureCall(
          nenv, coutputs, life, range, interfaceTT, explicitTemplateArgRulesS, explicitTemplateArgRunesS, callableExpr, givenArgsExprs2)
      }
      case OverloadSetT(overloadSetEnv, functionName) => {
        val unconvertedArgsPointerTypes2 =
          givenArgsExprs2.map(_.result.expectReference().reference)

        // We want to get the prototype here, not the entire header, because
        // we might be in the middle of a recursive call like:
        // func main():Int(main())

        val argsParamFilters =
          unconvertedArgsPointerTypes2.map(unconvertedArgsPointerType2 => {
            ParamFilter(unconvertedArgsPointerType2, None)
          })

        val prototype =
          overloadCompiler.findFunction(
              overloadSetEnv,
              coutputs,
              range,
              functionName,
              explicitTemplateArgRulesS,
              explicitTemplateArgRunesS,
              argsParamFilters,
              Vector.empty,
              false) match {
            case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
            case Ok(x) => x
          }
        val argsExprs2 =
          convertHelper.convertExprs(
            nenv.snapshot, coutputs, range, givenArgsExprs2, prototype.paramTypes)

        checkTypes(
          coutputs,
          prototype.paramTypes,
          argsExprs2.map(a => a.result.reference),
          exact = true)

        (ast.FunctionCallTE(prototype, argsExprs2))
      }
    }
  }

  private def evaluateNamedCall(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironment,
    range: RangeS,
    functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
    givenArgsExprs2: Vector[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    val unconvertedArgsPointerTypes2 =
      givenArgsExprs2.map(_.result.expectReference().reference)

    // We want to get the prototype here, not the entire header, because
    // we might be in the middle of a recursive call like:
    // func main():Int(main())

    val argsParamFilters =
      unconvertedArgsPointerTypes2.map(unconvertedArgsPointerType2 => {
        ParamFilter(unconvertedArgsPointerType2, None)
      })

    val prototype =
      overloadCompiler.findFunction(
        nenv,
        coutputs,
        range,
        functionName,
        explicitTemplateArgRulesS,
        explicitTemplateArgRunesS,
        argsParamFilters,
        Vector.empty,
        false) match {
        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
        case Ok(x) => x
      }
    val argsExprs2 =
      convertHelper.convertExprs(
        nenv, coutputs, range, givenArgsExprs2, prototype.paramTypes)

    checkTypes(
      coutputs,
      prototype.paramTypes,
      argsExprs2.map(a => a.result.reference),
      exact = true)

    (ast.FunctionCallTE(prototype, argsExprs2))
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

  private def evaluateClosureCall(
      nenv: NodeEnvironmentBox,
      coutputs: CompilerOutputs,
      life: LocationInFunctionEnvironment,
      range: RangeS,
      citizenRef: CitizenRefT,
      explicitTemplateArgRulesS: Vector[IRulexSR],
      explicitTemplateArgRunesS: Array[IRuneS],
      givenCallableUnborrowedExpr2: ReferenceExpressionTE,
      givenArgsExprs2: Vector[ReferenceExpressionTE]):
      (FunctionCallTE) = {
    // Whether we're given a borrow or an own, the call itself will be given a borrow.
    val givenCallableBorrowExpr2 =
      givenCallableUnborrowedExpr2.result.reference match {
        case CoordT(BorrowT | ShareT, _) => (givenCallableUnborrowedExpr2)
        case CoordT(OwnT, _) => {
          localHelper.makeTemporaryLocal(coutputs, nenv, range, life, givenCallableUnborrowedExpr2, BorrowT)
        }
      }

    val env =
      citizenRef match {
        case sr @ StructTT(_) => coutputs.getEnvForKind(sr) // coutputs.envByStructRef(sr)
        case ir @ InterfaceTT(_) => coutputs.getEnvForKind(ir) // coutputs.envByInterfaceRef(ir)
      }

    val argsTypes2 = givenArgsExprs2.map(_.result.reference)
    val closureParamType =
      CoordT(
        givenCallableBorrowExpr2.result.reference.ownership,
        citizenRef)
    val paramFilters =
      Vector(ParamFilter(closureParamType, None)) ++
        argsTypes2.map(argType => ParamFilter(argType, None))
    val prototype2 =
      overloadCompiler.findFunction(
        env, coutputs, range, interner.intern(CodeNameS(keywords.CALL_FUNCTION_NAME)), explicitTemplateArgRulesS, explicitTemplateArgRunesS, paramFilters, Vector.empty, false) match {
        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
        case Ok(x) => x
      }

    val mutability = Compiler.getMutability(coutputs, citizenRef)
    val ownership = if (mutability == MutableT) BorrowT else ShareT
    vassert(givenCallableBorrowExpr2.result.reference.ownership == ownership)
    val actualCallableExpr2 = givenCallableBorrowExpr2

    val actualArgsExprs2 = Vector(actualCallableExpr2) ++ givenArgsExprs2

    val argTypes = actualArgsExprs2.map(_.result.reference)
    if (argTypes != prototype2.paramTypes) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "arg param type mismatch. params: " + prototype2.paramTypes + " args: " + argTypes))
    }

    checkTypes(coutputs, prototype2.paramTypes, argTypes, exact = true)

    val resultingExpr2 = FunctionCallTE(prototype2, actualArgsExprs2);

    (resultingExpr2)
  }


  def checkTypes(
    coutputs: CompilerOutputs,
    params: Vector[CoordT],
    args: Vector[CoordT],
    exact: Boolean):
  Unit = {
    vassert(params.size == args.size)
    params.zip(args).foreach({ case (paramsHead, argsHead) =>
      if (paramsHead == argsHead) {

      } else {
        if (!exact) {
          templataCompiler.isTypeConvertible(coutputs, argsHead, paramsHead) match {
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
    life: LocationInFunctionEnvironment,
    range: RangeS,
      callableReferenceExpr2: ReferenceExpressionTE,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
    argsExprs2: Vector[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    val callExpr =
      evaluateCall(
        coutputs, nenv, life, range, callableReferenceExpr2, explicitTemplateArgRulesS, explicitTemplateArgRunesS, argsExprs2)
    (callExpr)
  }

  def evaluateNamedPrefixCall(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    rangeS: RangeS,
    functionName: IImpreciseNameS,
    rules: Vector[IRulexSR],
    templateArgs: Vector[IRuneS],
    argsExprs2: Vector[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    evaluateNamedCall(coutputs, nenv.snapshot, rangeS, functionName, rules, templateArgs.toArray, argsExprs2)
  }
}