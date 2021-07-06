package net.verdagon.vale.templar.expression

import net.verdagon.vale.astronomer.GlobalFunctionFamilyNameA
import net.verdagon.vale.scout.{ITemplexS, RangeS}
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar._
import net.verdagon.vale.{vassert, vfail}

import scala.collection.immutable.List

object CallTemplar {
  val CALL_FUNCTION_NAME = "__call"

  // Don't use these for imm structs and interfaces, use Imm[Struct|Interface]DestructorName2 instead.
  val MUT_INTERFACE_DESTRUCTOR_NAME = "idestructor"
  val MUT_DESTRUCTOR_NAME = "destructor"

  val MUT_DROP_FUNCTION_NAME = "drop"
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
      range: RangeS,
      callableExpr: ReferenceExpressionTE,
      explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
      givenArgsExprs2: List[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    callableExpr.resultRegister.reference.kind match {
      case NeverT() | BoolT() => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "wot " + callableExpr.resultRegister.reference.kind))
      }
      case structRef @ StructRefT(_) => {
        evaluateClosureCall(
          fate, temputs, range, structRef, explicitlySpecifiedTemplateArgTemplexesS, callableExpr, givenArgsExprs2)
      }
      case interfaceRef @ InterfaceRefT(_) => {
        evaluateClosureCall(
          fate, temputs, range, interfaceRef, explicitlySpecifiedTemplateArgTemplexesS, callableExpr, givenArgsExprs2)
      }
      case OverloadSet(_, functionName, _) => {
        val unconvertedArgsPointerTypes2 =
          givenArgsExprs2.map(_.resultRegister.expectReference().reference)

        // We want to get the prototype here, not the entire header, because
        // we might be in the middle of a recursive call like:
        // fn main():Int(main())

        val argsParamFilters =
          unconvertedArgsPointerTypes2.map(unconvertedArgsPointerType2 => {
            ParamFilter(unconvertedArgsPointerType2, None)
          })

        val prototype =
          overloadTemplar.scoutExpectedFunctionForPrototype(
              fate.snapshot,
              temputs,
              range,
              functionName,
              explicitlySpecifiedTemplateArgTemplexesS,
              argsParamFilters,
              List.empty,
              false) match {
            case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
              throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, seff))
//              vfail("Couldn't find function to call!\n" + seff.toString)
            }
            case (ScoutExpectedFunctionSuccess(p)) => (p)
          }
        val argsExprs2 =
          convertHelper.convertExprs(
            fate.snapshot, temputs, range, givenArgsExprs2, prototype.paramTypes)

        checkTypes(
          temputs,
          prototype.paramTypes,
          argsExprs2.map(a => a.resultRegister.reference),
          exact = true)

        (FunctionCallTE(prototype, argsExprs2))
      }
//      case ft @ FunctionT2(_, _) => {
//        vcurious() // do we ever use this? do we ever deal with function pointers?
//
//        val argsExprs2 =
//          convertHelper.convertExprs(
//            fate.snapshot, temputs, givenArgsExprs2, ft.paramTypes)
//
//        val argsPointerTypes2 = argsExprs2.map(_.resultRegister.expectReference().reference)
//
//        val callableType = callableExpr.resultRegister.reference.kind.asInstanceOf[FunctionT2]
//
//        checkTypes(temputs, callableType.paramTypes, argsPointerTypes2, exact = true);
//
//        (FunctionPointerCall2(callableExpr, argsExprs2))
//      }
    }
  }

  private def evaluateNamedCall(
    temputs: Temputs,
    fate: FunctionEnvironment,
    range: RangeS,
    functionName: GlobalFunctionFamilyNameA,
    explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
    givenArgsExprs2: List[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    val unconvertedArgsPointerTypes2 =
      givenArgsExprs2.map(_.resultRegister.expectReference().reference)

    // We want to get the prototype here, not the entire header, because
    // we might be in the middle of a recursive call like:
    // fn main():Int(main())

    val argsParamFilters =
      unconvertedArgsPointerTypes2.map(unconvertedArgsPointerType2 => {
        ParamFilter(unconvertedArgsPointerType2, None)
      })

    val prototype =
      overloadTemplar.scoutExpectedFunctionForPrototype(
        fate,
        temputs,
        range,
        functionName,
        explicitlySpecifiedTemplateArgTemplexesS,
        argsParamFilters,
        List.empty,
        false) match {
        case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
          ErrorReporter.report(CouldntFindFunctionToCallT(range, seff))
        }
        case (ScoutExpectedFunctionSuccess(p)) => (p)
      }
    val argsExprs2 =
      convertHelper.convertExprs(
        fate, temputs, range, givenArgsExprs2, prototype.paramTypes)

    checkTypes(
      temputs,
      prototype.paramTypes,
      argsExprs2.map(a => a.resultRegister.reference),
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
      range: RangeS,
      citizenRef: CitizenRefT,
      explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
      givenCallableUnborrowedExpr2: ReferenceExpressionTE,
      givenArgsExprs2: List[ReferenceExpressionTE]):
      (FunctionCallTE) = {
    // Whether we're given a borrow or an own, the call itself will be given a borrow.
    val givenCallableBorrowExpr2 =
      givenCallableUnborrowedExpr2.resultRegister.reference match {
        case CoordT(ConstraintT, _, _) => (givenCallableUnborrowedExpr2)
        case CoordT(ShareT, _, _) => (givenCallableUnborrowedExpr2)
        case CoordT(OwnT, _, _) => {
          localHelper.makeTemporaryLocal(temputs, fate, givenCallableUnborrowedExpr2)
        }
      }

    val env =
      citizenRef match {
        case sr @ StructRefT(_) => temputs.getEnvForStructRef(sr) // temputs.envByStructRef(sr)
        case ir @ InterfaceRefT(_) => temputs.getEnvForInterfaceRef(ir) // temputs.envByInterfaceRef(ir)
      }

    val argsTypes2 = givenArgsExprs2.map(_.resultRegister.reference)
    val closureParamType =
      CoordT(
        givenCallableBorrowExpr2.resultRegister.reference.ownership,
        givenCallableUnborrowedExpr2.resultRegister.reference.permission,
        citizenRef)
    val paramFilters =
      ParamFilter(closureParamType, None) ::
        argsTypes2.map(argType => ParamFilter(argType, None))
    val prototype2 =
      overloadTemplar.scoutExpectedFunctionForPrototype(
        env, temputs, range, GlobalFunctionFamilyNameA(CallTemplar.CALL_FUNCTION_NAME), explicitlySpecifiedTemplateArgTemplexesS, paramFilters, List.empty, false) match {
        case ScoutExpectedFunctionSuccess(p) => p
        case seff @ ScoutExpectedFunctionFailure(_, _, _, _, _) => {
          throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, seff))
        }
      }

    val mutability = Templar.getMutability(temputs, citizenRef)
    val ownership = if (mutability == MutableT) ConstraintT else ShareT
//    val permission = if (mutability == Mutable) Readwrite else Readonly // See LHRSP
//    if (givenCallableBorrowExpr2.resultRegister.reference.permission != Readwrite) {
//      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can only call readwrite callables! (LHRSP)"))
//    }
    vassert(givenCallableBorrowExpr2.resultRegister.reference.ownership == ownership)
    val actualCallableExpr2 = givenCallableBorrowExpr2

    val actualArgsExprs2 = actualCallableExpr2 :: givenArgsExprs2

    val argTypes = actualArgsExprs2.map(_.resultRegister.reference)
    if (argTypes != prototype2.paramTypes) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "arg param type mismatch. params: " + prototype2.paramTypes + " args: " + argTypes))
    }

    checkTypes(temputs, prototype2.paramTypes, argTypes, exact = true)

    val resultingExpr2 = FunctionCallTE(prototype2, actualArgsExprs2);

    (resultingExpr2)
  }


  def checkTypes(
    temputs: Temputs,
    params: List[CoordT],
    args: List[CoordT],
    exact: Boolean):
  Unit = {
    vassert(params.size == args.size)
    (params, args) match {
      case (Nil, Nil) =>
      case (paramsHead :: paramsTail, argsHead :: argsTail) => {
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
        // It matches! Now check the rest.
        checkTypes(temputs, paramsTail, argsTail, exact)
      }
      case _ => vfail("wat")
    }
//    checkTypes(params.tail, args.tail)
//    vassert(argTypes == callableType.paramTypes, "arg param type mismatch. params: " + callableType.paramTypes + " args: " + argTypes)
  }

  def evaluatePrefixCall(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
    range: RangeS,
      callableReferenceExpr2: ReferenceExpressionTE,
      explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
      argsExprs2: List[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    val callExpr =
      evaluateCall(temputs, fate, range, callableReferenceExpr2, explicitlySpecifiedTemplateArgTemplexesS, argsExprs2)
    (callExpr)
  }

  def evaluateNamedPrefixCall(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    rangeS: RangeS,
    functionName: GlobalFunctionFamilyNameA,
    explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
    argsExprs2: List[ReferenceExpressionTE]):
  (FunctionCallTE) = {
    evaluateNamedCall(temputs, fate.snapshot, rangeS, functionName, explicitlySpecifiedTemplateArgTemplexesS, argsExprs2)
  }
}