package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.{GlobalFunctionFamilyNameA}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser.MutableP
import net.verdagon.vale.scout.ITemplexS
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox, IEnvironment}
import net.verdagon.vale.templar.function.FunctionTemplar
import net.verdagon.vale.{vassert, vcurious, vfail}

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
      temputs: TemputsBox,
      fate: FunctionEnvironmentBox,
      callableExpr: ReferenceExpression2,
      explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
      givenArgsExprs2: List[ReferenceExpression2]):
  (FunctionCall2) = {
    callableExpr.resultRegister.reference.referend match {
      case Never2() | Bool2() => {
        vfail("wot " + callableExpr.resultRegister.reference.referend)
      }
      case structRef @ StructRef2(_) => {
        evaluateClosureCall(
          fate, temputs, structRef, explicitlySpecifiedTemplateArgTemplexesS, callableExpr, givenArgsExprs2)
      }
      case interfaceRef @ InterfaceRef2(_) => {
        evaluateClosureCall(
          fate, temputs, interfaceRef, explicitlySpecifiedTemplateArgTemplexesS, callableExpr, givenArgsExprs2)
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
              functionName,
              explicitlySpecifiedTemplateArgTemplexesS,
              argsParamFilters,
              List(),
              exact = false) match {
            case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
              vfail("Couldn't find function to call!\n" + seff.toString)
            }
            case (ScoutExpectedFunctionSuccess(p)) => (p)
          }
        val argsExprs2 =
          convertHelper.convertExprs(
            fate.snapshot, temputs, givenArgsExprs2, prototype.paramTypes)

        checkTypes(
          temputs,
          prototype.paramTypes,
          argsExprs2.map(a => a.resultRegister.reference),
          exact = true)

        (FunctionCall2(prototype, argsExprs2))
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
//        val callableType = callableExpr.resultRegister.reference.referend.asInstanceOf[FunctionT2]
//
//        checkTypes(temputs, callableType.paramTypes, argsPointerTypes2, exact = true);
//
//        (FunctionPointerCall2(callableExpr, argsExprs2))
//      }
    }
  }

  private def evaluateNamedCall(
    temputs: TemputsBox,
    fate: FunctionEnvironment,
    functionName: GlobalFunctionFamilyNameA,
    explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
    givenArgsExprs2: List[ReferenceExpression2]):
  (FunctionCall2) = {
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
        functionName,
        explicitlySpecifiedTemplateArgTemplexesS,
        argsParamFilters,
        List(),
        exact = false) match {
        case (seff @ ScoutExpectedFunctionFailure(_, _, _, _, _)) => {
          ErrorReporter.report(CouldntFindFunctionToCallT(seff))
        }
        case (ScoutExpectedFunctionSuccess(p)) => (p)
      }
    val argsExprs2 =
      convertHelper.convertExprs(
        fate, temputs, givenArgsExprs2, prototype.paramTypes)

    checkTypes(
      temputs,
      prototype.paramTypes,
      argsExprs2.map(a => a.resultRegister.reference),
      exact = true)

    (FunctionCall2(prototype, argsExprs2))
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
      temputs: TemputsBox,
      citizenRef: CitizenRef2,
      explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
      givenCallableUnborrowedExpr2: ReferenceExpression2,
      givenArgsExprs2: List[ReferenceExpression2]):
      (FunctionCall2) = {
    val env =
      citizenRef match {
        case sr @ StructRef2(_) => temputs.envByStructRef(sr)
        case ir @ InterfaceRef2(_) => temputs.envByInterfaceRef(ir)
      }

    val argsTypes2 = givenArgsExprs2.map(_.resultRegister.reference)
    val paramFilters =
      ParamFilter(templataTemplar.pointifyReferend(temputs, citizenRef, Borrow), None) ::
        argsTypes2.map(argType => ParamFilter(argType, None))
    val (maybePrototype, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunctionS) =
      overloadTemplar.scoutMaybeFunctionForPrototype(
        env, temputs, GlobalFunctionFamilyNameA(CallTemplar.CALL_FUNCTION_NAME), explicitlySpecifiedTemplateArgTemplexesS, paramFilters, List(), false)
    val prototype2 =
      maybePrototype match {
        case None => {
          vfail(
            "Struct not callable: " + citizenRef + "\n" +
              "Outscored:\n" +
              outscoredReasonByPotentialBanner
                .map({ case (k, v) => k + ": " + v })
                .mkString("\n") +
              "\n" +
              "Rejected banners:\n" +
              rejectedReasonByBanner
                .map({ case (k, v) => k + ": " + v })
                .mkString("\n") +
              "\n" +
              "Rejected functionSs:\n" +
              rejectedReasonByFunctionS
                .map({ case (k, v) => k + ": " + v })
                .mkString("\n") +
              "\n")
        }
        case Some(p) => p
      }

    // Whether we're given a borrow or an own, the call itself will be given a borrow.
    val givenCallableBorrowExpr2 =
      givenCallableUnborrowedExpr2.resultRegister.reference match {
        case Coord(Borrow, _) => (givenCallableUnborrowedExpr2)
        case Coord(Share, _) => (givenCallableUnborrowedExpr2)
        case Coord(Own, _) => {
          localHelper.makeTemporaryLocal(temputs, fate, givenCallableUnborrowedExpr2)
        }
      }

    val mutability = Templar.getMutability(temputs, citizenRef)
    val ownership = if (mutability == Mutable) Borrow else Share
    vassert(givenCallableBorrowExpr2.resultRegister.reference == Coord(ownership, citizenRef))
    val actualCallableExpr2 = givenCallableBorrowExpr2

    val actualArgsExprs2 = actualCallableExpr2 :: givenArgsExprs2

    val argTypes = actualArgsExprs2.map(_.resultRegister.reference)
    if (argTypes != prototype2.paramTypes) {
      vfail("arg param type mismatch. params: " + prototype2.paramTypes + " args: " + argTypes)
    }

    checkTypes(temputs, prototype2.paramTypes, argTypes, exact = true)

    val resultingExpr2 = FunctionCall2(prototype2, actualArgsExprs2);

    (resultingExpr2)
  }


  def checkTypes(
    temputs: TemputsBox,
    params: List[Coord],
    args: List[Coord],
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
      temputs: TemputsBox,
      fate: FunctionEnvironmentBox,
      callableReferenceExpr2: ReferenceExpression2,
      explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
      argsExprs2: List[ReferenceExpression2]):
  (FunctionCall2) = {
    val callExpr =
      evaluateCall(temputs, fate, callableReferenceExpr2, explicitlySpecifiedTemplateArgTemplexesS, argsExprs2)
    (callExpr)
  }

  def evaluateNamedPrefixCall(
    temputs: TemputsBox,
    fate: FunctionEnvironmentBox,
    functionName: GlobalFunctionFamilyNameA,
    explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
    argsExprs2: List[ReferenceExpression2]):
  (FunctionCall2) = {
    evaluateNamedCall(temputs, fate.snapshot, functionName, explicitlySpecifiedTemplateArgTemplexesS, argsExprs2)
  }
}