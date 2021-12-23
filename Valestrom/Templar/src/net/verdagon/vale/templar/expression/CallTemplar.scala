package net.verdagon.vale.templar.expression

import net.verdagon.vale.scout.{CodeNameS, GlobalFunctionFamilyNameS, IImpreciseNameS, IRuneS}
import net.verdagon.vale.scout.rules.IRulexSR
import net.verdagon.vale.templar.OverloadTemplar.FindFunctionFailure
import net.verdagon.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.{ast, _}
import net.verdagon.vale.templar.ast.{FunctionCallTE, LocationInFunctionEnvironment, ReferenceExpressionTE}
import net.verdagon.vale.{RangeS, vassert, vfail}

import scala.collection.immutable.List

object CallTemplar {
  val CALL_FUNCTION_NAME = "__call"

  // See NSIDN for more on this.
  // Every type, including interfaces, has a function of this name. These won't be virtual.
  val DROP_FUNCTION_NAME = "drop"
  // Every interface *also* has a function of this name. It's abstract, and an override is defined for each impl.
  val VIRTUAL_DROP_FUNCTION_NAME = "vdrop"
  // Interface's drop function simply calls vdrop.
  // A struct's vdrop function calls the struct's drop function.
}

class CallTemplar(
    opts: TemplarOptions,
    templataTemplar: TemplataTemplar,
    convertHelper: ConvertHelper,
    localHelper: LocalHelper,
    overloadTemplar: OverloadTemplar) {

  private def evaluateCall(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      life: LocationInFunctionEnvironment,
      range: RangeS,
      callableExpr: ReferenceExpressionTE,
      explicitTemplateArgRulesS: Vector[IRulexSR],
      explicitTemplateArgRunesS: Array[IRuneS],
      givenArgsExprs2: Vector[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    callableExpr.result.reference.kind match {
      case NeverT() | BoolT() => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "wot " + callableExpr.result.reference.kind))
      }
      case structTT @ StructTT(_) => {
        evaluateClosureCall(
          fate, temputs, life, range, structTT, explicitTemplateArgRulesS, explicitTemplateArgRunesS, callableExpr, givenArgsExprs2)
      }
      case interfaceTT @ InterfaceTT(_) => {
        evaluateClosureCall(
          fate, temputs, life, range, interfaceTT, explicitTemplateArgRulesS, explicitTemplateArgRunesS, callableExpr, givenArgsExprs2)
      }
      case OverloadSet(overloadSetEnv, functionName, _) => {
        val unconvertedArgsPointerTypes2 =
          givenArgsExprs2.map(_.result.expectReference().reference)

        // We want to get the prototype here, not the entire header, because
        // we might be in the middle of a recursive call like:
        // fn main():Int(main())

        val argsParamFilters =
          unconvertedArgsPointerTypes2.map(unconvertedArgsPointerType2 => {
            ParamFilter(unconvertedArgsPointerType2, None)
          })

        val prototype =
          overloadTemplar.findFunction(
              overloadSetEnv,
              temputs,
              range,
              functionName,
              explicitTemplateArgRulesS,
              explicitTemplateArgRunesS,
              argsParamFilters,
              Vector.empty,
              false)
        val argsExprs2 =
          convertHelper.convertExprs(
            fate.snapshot, temputs, range, givenArgsExprs2, prototype.paramTypes)

        checkTypes(
          temputs,
          prototype.paramTypes,
          argsExprs2.map(a => a.result.reference),
          exact = true)

        (FunctionCallTE(prototype, argsExprs2))
      }
    }
  }

  private def evaluateNamedCall(
    temputs: Temputs,
    fate: FunctionEnvironment,
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
    // fn main():Int(main())

    val argsParamFilters =
      unconvertedArgsPointerTypes2.map(unconvertedArgsPointerType2 => {
        ParamFilter(unconvertedArgsPointerType2, None)
      })

    val prototype =
      overloadTemplar.findFunction(
        fate,
        temputs,
        range,
        functionName,
        explicitTemplateArgRulesS,
        explicitTemplateArgRunesS,
        argsParamFilters,
        Vector.empty,
        false)
    val argsExprs2 =
      convertHelper.convertExprs(
        fate, temputs, range, givenArgsExprs2, prototype.paramTypes)

    checkTypes(
      temputs,
      prototype.paramTypes,
      argsExprs2.map(a => a.result.reference),
      exact = true)

    (FunctionCallTE(prototype, argsExprs2))
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
      fate: FunctionEnvironmentBox,
      temputs: Temputs,
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
        case CoordT(BorrowT | PointerT | ShareT, _, _) => (givenCallableUnborrowedExpr2)
        case CoordT(OwnT, _, _) => {
          localHelper.makeTemporaryLocal(temputs, fate, life, givenCallableUnborrowedExpr2, BorrowT)
        }
      }

    val env =
      citizenRef match {
        case sr @ StructTT(_) => temputs.getEnvForKind(sr) // temputs.envByStructRef(sr)
        case ir @ InterfaceTT(_) => temputs.getEnvForKind(ir) // temputs.envByInterfaceRef(ir)
      }

    val argsTypes2 = givenArgsExprs2.map(_.result.reference)
    val closureParamType =
      CoordT(
        givenCallableBorrowExpr2.result.reference.ownership,
        givenCallableUnborrowedExpr2.result.reference.permission,
        citizenRef)
    val paramFilters =
      Vector(ParamFilter(closureParamType, None)) ++
        argsTypes2.map(argType => ParamFilter(argType, None))
    val prototype2 =
      overloadTemplar.findFunction(
        env, temputs, range, CodeNameS(CallTemplar.CALL_FUNCTION_NAME), explicitTemplateArgRulesS, explicitTemplateArgRunesS, paramFilters, Vector.empty, false)

    val mutability = Templar.getMutability(temputs, citizenRef)
    val ownership = if (mutability == MutableT) BorrowT else ShareT
    vassert(givenCallableBorrowExpr2.result.reference.ownership == ownership)
    val actualCallableExpr2 = givenCallableBorrowExpr2

    val actualArgsExprs2 = Vector(actualCallableExpr2) ++ givenArgsExprs2

    val argTypes = actualArgsExprs2.map(_.result.reference)
    if (argTypes != prototype2.paramTypes) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "arg param type mismatch. params: " + prototype2.paramTypes + " args: " + argTypes))
    }

    checkTypes(temputs, prototype2.paramTypes, argTypes, exact = true)

    val resultingExpr2 = ast.FunctionCallTE(prototype2, actualArgsExprs2);

    (resultingExpr2)
  }


  def checkTypes(
    temputs: Temputs,
    params: Vector[CoordT],
    args: Vector[CoordT],
    exact: Boolean):
  Unit = {
    vassert(params.size == args.size)
    params.zip(args).foreach({ case (paramsHead, argsHead) =>
      if (paramsHead == argsHead) {

      } else {
        if (!exact) {
          templataTemplar.isTypeConvertible(temputs, argsHead, paramsHead) match {
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
          // do stuff here.
          // also there is one special case here, which is when we try to hand in
          // an owning when they just want a borrow, gotta account for that here
          vfail("do stuff " + argsHead + " and " + paramsHead)
        }
      }
    })
//    checkTypes(params.tail, args.tail)
//    vassert(argTypes == callableType.paramTypes, "arg param type mismatch. params: " + callableType.paramTypes + " args: " + argTypes)
  }

  def evaluatePrefixCall(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
    life: LocationInFunctionEnvironment,
    range: RangeS,
      callableReferenceExpr2: ReferenceExpressionTE,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
    argsExprs2: Vector[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    val callExpr =
      evaluateCall(
        temputs, fate, life, range, callableReferenceExpr2, explicitTemplateArgRulesS, explicitTemplateArgRunesS, argsExprs2)
    (callExpr)
  }

  def evaluateNamedPrefixCall(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    rangeS: RangeS,
    functionName: IImpreciseNameS,
    rules: Vector[IRulexSR],
    templateArgs: Vector[IRuneS],
    argsExprs2: Vector[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    evaluateNamedCall(temputs, fate.snapshot, rangeS, functionName, rules, templateArgs.toArray, argsExprs2)
  }
}