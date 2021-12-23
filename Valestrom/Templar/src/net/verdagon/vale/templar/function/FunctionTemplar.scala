package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.{CompileErrorExceptionA, CouldntSolveRulesA, FunctionA}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser._
import net.verdagon.vale.{Err, IProfiler, Ok, Profiler, RangeS, scout, vassert, vassertOne, vassertSome, vimpl, vwat}
import net.verdagon.vale.scout.{RuneTypeSolver, Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, OverrideSP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.templar.OverloadTemplar.IFindFunctionFailureReason
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.{FunctionBannerT, FunctionHeaderT, LocationInFunctionEnvironment, ParameterT, PrototypeT, ReferenceExpressionTE}
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.FunctionTemplar.IEvaluateFunctionResult
import net.verdagon.vale.templar.names.{LambdaCitizenNameT, LambdaCitizenTemplateNameT, NameTranslator}

import scala.collection.immutable.{List, Set}



trait IFunctionTemplarDelegate {
  def evaluateBlockStatements(
    temputs: Temputs,
    startingFate: FunctionEnvironment,
    fate: FunctionEnvironmentBox,
    life: LocationInFunctionEnvironment,
    exprs: Vector[IExpressionSE]):
  (ReferenceExpressionTE, Set[CoordT])

  def translatePatternList(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    life: LocationInFunctionEnvironment,
    patterns1: Vector[AtomSP],
    patternInputExprs2: Vector[ReferenceExpressionTE]):
  ReferenceExpressionTE

  def evaluateParent(
    env: IEnvironment, temputs: Temputs, sparkHeader: FunctionHeaderT):
  Unit

  def generateFunction(
    functionTemplarCore: FunctionTemplarCore,
    generator: IFunctionGenerator,
    env: FunctionEnvironment,
    temputs: Temputs,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    // We might be able to move these all into the function environment... maybe....
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT
}

object FunctionTemplar {
  trait IEvaluateFunctionResult[T]
  case class EvaluateFunctionSuccess[T](function: T) extends IEvaluateFunctionResult[T]
  case class EvaluateFunctionFailure[T](reason: IFindFunctionFailureReason) extends IEvaluateFunctionResult[T]
}

// When templaring a function, these things need to happen:
// - Spawn a local environment for the function
// - Add any closure args to the environment
// - Incorporate any template arguments into the environment
// There's a layer to take care of each of these things.
// This file is the outer layer, which spawns a local environment for the function.
class FunctionTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
    templataTemplar: TemplataTemplar,
    inferTemplar: InferTemplar,
    convertHelper: ConvertHelper,
    structTemplar: StructTemplar,
    delegate: IFunctionTemplarDelegate) {
  val closureOrLightLayer =
    new FunctionTemplarClosureOrLightLayer(
      opts, profiler, templataTemplar, inferTemplar, convertHelper, structTemplar, delegate)

  private def determineClosureVariableMember(
      env: FunctionEnvironment,
      temputs: Temputs,
      name: IVarNameS) = {
    val (variability2, memberType) =
      env.getVariable(NameTranslator.translateVarNameStep(name)).get match {
        case ReferenceLocalVariableT(_, variability, reference) => {
          // See "Captured own is borrow" test for why we do this
          val tyype =
            reference.ownership match {
              case OwnT => ReferenceMemberTypeT(CoordT(BorrowT, reference.permission, reference.kind))
              case PointerT | BorrowT | ShareT => ReferenceMemberTypeT(reference)
            }
          (variability, tyype)
        }
        case AddressibleLocalVariableT(_, variability, reference) => {
          (variability, AddressMemberTypeT(reference))
        }
        case ReferenceClosureVariableT(_, _, variability, reference) => {
          // See "Captured own is borrow" test for why we do this
          val tyype =
            reference.ownership match {
              case OwnT => ReferenceMemberTypeT(CoordT(BorrowT, reference.permission, reference.kind))
              case PointerT | BorrowT | ShareT => ReferenceMemberTypeT(reference)
            }
          (variability, tyype)
        }
        case AddressibleClosureVariableT(_, _, variability, reference) => {
          (variability, AddressMemberTypeT(reference))
        }
      }
    StructMemberT(NameTranslator.translateVarNameStep(name), variability2, memberType)
  }

  def evaluateClosureStruct(
      temputs: Temputs,
      containingFunctionEnv: FunctionEnvironment,
    callRange: RangeS,
    name: IFunctionDeclarationNameS,
      functionA: FunctionA):
  (StructTT) = {
    val CodeBodyS(body) = functionA.body
    val closuredNames = body.closuredNames;

    // Note, this is where the unordered closuredNames set becomes ordered.
    val closuredVarNamesAndTypes =
      closuredNames
        .map(name => determineClosureVariableMember(containingFunctionEnv, temputs, name))
        .toVector;

    val (structTT, _, functionTemplata) =
      structTemplar.makeClosureUnderstruct(
        containingFunctionEnv, temputs, name, functionA, closuredVarNamesAndTypes)

    // Eagerly evaluate the function if it's not a template.
    if (functionA.isTemplate) {
      // Do nothing
    } else {
      val _ =
        evaluateOrdinaryClosureFunctionFromNonCallForHeader(
          functionTemplata.outerEnv, temputs, structTT, functionA)
    }

    (structTT)
  }


  def evaluateOrdinaryFunctionFromNonCallForHeader(
    temputs: Temputs,
    functionTemplata: FunctionTemplata):
  FunctionHeaderT = {
    profiler.newProfile("FunctionTemplarEvaluateOrdinaryFunctionFromNonCallForHeader", functionTemplata.debugString, () => {
        val FunctionTemplata(env, function) = functionTemplata
        if (function.isLight) {
          evaluateOrdinaryLightFunctionFromNonCallForHeader(
            env, temputs, function)
        } else {
          val List(KindTemplata(closureStructRef@StructTT(_))) =
            env.lookupNearestWithImpreciseName(
              profiler,
              vimpl(), //FunctionScout.CLOSURE_STRUCT_ENV_ENTRY_NAME,
              Set(TemplataLookupContext)).toList
          val header =
            evaluateOrdinaryClosureFunctionFromNonCallForHeader(
              env, temputs, closureStructRef, function)
          header
        }
      })

  }

  def evaluateTemplatedFunctionFromNonCallForHeader(
    temputs: Temputs,
    functionTemplata: FunctionTemplata):
  FunctionHeaderT = {
    profiler.newProfile("FunctionTemplarEvaluateOrdinaryFunctionFromNonCallForHeader", functionTemplata.debugString, () => {
      val FunctionTemplata(env, function) = functionTemplata
      if (function.isLight) {
        evaluateTemplatedLightFunctionFromNonCallForHeader(
          env, temputs, function)
      } else {
        val List(KindTemplata(closureStructRef@StructTT(_))) =
          env.lookupNearestWithImpreciseName(
            profiler,
            vimpl(), //FunctionScout.CLOSURE_STRUCT_ENV_ENTRY_NAME,
            Set(TemplataLookupContext)).toList
        val header =
          evaluateTemplatedClosureFunctionFromNonCallForHeader(
            env, temputs, closureStructRef, function)
        header
      }
    })

  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // fn main():Int{main()}
  def evaluateOrdinaryFunctionFromNonCallForPrototype(
    temputs: Temputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata):
  (PrototypeT) = {
    profiler.newProfile("FunctionTemplarEvaluateOrdinaryFunctionFromNonCallForPrototype", functionTemplata.debugString, () => {
        val FunctionTemplata(env, function) = functionTemplata
        if (function.isLight) {
          evaluateOrdinaryLightFunctionFromNonCallForPrototype(
            env, temputs, callRange, function)
        } else {
          val lambdaCitizenName2 =
            functionTemplata.function.name match {
              case LambdaDeclarationNameS(codeLocation) => LambdaCitizenNameT(LambdaCitizenTemplateNameT(NameTranslator.translateCodeLocation(codeLocation)))
              case _ => vwat()
            }

          val KindTemplata(closureStructRef@StructTT(_)) =
            vassertOne(
              env.lookupNearestWithName(
                profiler,
                lambdaCitizenName2,
                Set(TemplataLookupContext)))
          val header =
            evaluateOrdinaryClosureFunctionFromNonCallForHeader(
              env, temputs, closureStructRef, function)
          (header.toPrototype)
        }
      })

  }

  def evaluateOrdinaryFunctionFromNonCallForBanner(
    temputs: Temputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata):
  (FunctionBannerT) = {
    profiler.newProfile("FunctionTemplarEvaluateOrdinaryFunctionFromNonCallForBanner", functionTemplata.debugString, () => {
        val FunctionTemplata(env, function) = functionTemplata
        if (function.isLight()) {
          evaluateOrdinaryLightFunctionFromNonCallForBanner(
            env, temputs, callRange, function)
        } else {
          val lambdaCitizenName2 =
            functionTemplata.function.name match {
              case LambdaDeclarationNameS(codeLocation) => LambdaCitizenNameT(LambdaCitizenTemplateNameT(NameTranslator.translateCodeLocation(codeLocation)))
              case _ => vwat()
            }

          val KindTemplata(closureStructRef@StructTT(_)) =
            vassertOne(
              env.lookupNearestWithName(
                profiler,
                lambdaCitizenName2,
                Set(TemplataLookupContext)))
          evaluateOrdinaryClosureFunctionFromNonCallForBanner(
            env, temputs, callRange, closureStructRef, function)
        }
      })

  }

  private def evaluateOrdinaryLightFunctionFromNonCallForBanner(
      env: IEnvironment,
      temputs: Temputs,
    callRange: RangeS,
    function: FunctionA):
  (FunctionBannerT) = {
    closureOrLightLayer.evaluateOrdinaryLightFunctionFromNonCallForBanner(
      env, temputs, callRange, function)
  }

  def evaluateTemplatedFunctionFromCallForBanner(
    temputs: Temputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata,
    alreadySpecifiedTemplateArgs: Vector[ITemplata],
    paramFilters: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    profiler.newProfile("EvaluateTemplatedFunctionFromCallForBannerProbe", functionTemplata.debugString + "<" + alreadySpecifiedTemplateArgs.mkString(", ") + ">(" + paramFilters.map(_.debugString).mkString(", ") + ")", () => {
        val FunctionTemplata(env, function) = functionTemplata
        if (function.isLight()) {
          evaluateTemplatedLightFunctionFromCallForBanner(
            temputs, callRange, functionTemplata, alreadySpecifiedTemplateArgs, paramFilters)
        } else {
          val lambdaCitizenName2 =
            functionTemplata.function.name match {
              case LambdaDeclarationNameS(codeLocation) => LambdaCitizenNameT(LambdaCitizenTemplateNameT(NameTranslator.translateCodeLocation(codeLocation)))
              case _ => vwat()
            }

          val KindTemplata(closureStructRef@StructTT(_)) =
            vassertOne(
              env.lookupNearestWithName(
                profiler,
                lambdaCitizenName2,
                Set(TemplataLookupContext)))
          val banner =
            evaluateTemplatedClosureFunctionFromCallForBanner(
              env, temputs, callRange, closureStructRef, function, alreadySpecifiedTemplateArgs, paramFilters)
          (banner)
        }
      })

  }

  private def evaluateTemplatedClosureFunctionFromCallForBanner(
      env: IEnvironment,
      temputs: Temputs,
      callRange: RangeS,
      closureStructRef: StructTT,
    function: FunctionA,
    alreadySpecifiedTemplateArgs: Vector[ITemplata],
      argTypes2: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    closureOrLightLayer.evaluateTemplatedClosureFunctionFromCallForBanner(
      env, temputs, callRange, closureStructRef, function,
      alreadySpecifiedTemplateArgs, argTypes2)
  }

  def evaluateTemplatedLightFunctionFromCallForBanner(
    temputs: Temputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata,
    alreadySpecifiedTemplateArgs: Vector[ITemplata],
    paramFilters: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    profiler.newProfile("FunctionTemplarEvaluateTemplatedLightFunctionFromCallForBanner", functionTemplata.debugString + "<" + alreadySpecifiedTemplateArgs.mkString(", ") + ">(" + paramFilters.map(_.debugString).mkString(", ") + ")", () => {
        val FunctionTemplata(env, function) = functionTemplata
        closureOrLightLayer.evaluateTemplatedLightBannerFromCall(
          env, temputs, callRange, function, alreadySpecifiedTemplateArgs, paramFilters)
      })

  }

  private def evaluateOrdinaryClosureFunctionFromNonCallForHeader(
      env: IEnvironment,
      temputs: Temputs,
      closureStructRef: StructTT,
    function: FunctionA):
  (FunctionHeaderT) = {
    closureOrLightLayer.evaluateOrdinaryClosureFunctionFromNonCallForHeader(
      env, temputs, closureStructRef, function)
  }

  private def evaluateTemplatedClosureFunctionFromNonCallForHeader(
    env: IEnvironment,
    temputs: Temputs,
    closureStructRef: StructTT,
    function: FunctionA):
  (FunctionHeaderT) = {
    closureOrLightLayer.evaluateTemplatedClosureFunctionFromNonCallForHeader(
      env, temputs, closureStructRef, function)
  }

  private def evaluateOrdinaryClosureFunctionFromNonCallForBanner(
    env: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    closureStructRef: StructTT,
    function: FunctionA):
  (FunctionBannerT) = {
    closureOrLightLayer.evaluateOrdinaryClosureFunctionFromNonCallForBanner(
      env, temputs, callRange, closureStructRef, function)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // fn main():Int{main()}
  private def evaluateOrdinaryLightFunctionFromNonCallForPrototype(
      env: IEnvironment,
      temputs: Temputs,
    callRange: RangeS,
    function: FunctionA):
  (PrototypeT) = {
    closureOrLightLayer.evaluateOrdinaryLightFunctionFromNonCallForPrototype(
      env, temputs, callRange, function)
  }

  private def evaluateOrdinaryLightFunctionFromNonCallForHeader(
    env: IEnvironment,
    temputs: Temputs,
    function: FunctionA):
  (FunctionHeaderT) = {
    closureOrLightLayer.evaluateOrdinaryLightFunctionFromNonCallForHeader(
      env, temputs, function)
  }

  private def evaluateTemplatedLightFunctionFromNonCallForHeader(
    env: IEnvironment,
    temputs: Temputs,
    function: FunctionA):
  (FunctionHeaderT) = {
    closureOrLightLayer.evaluateTemplatedLightFunctionFromNonCallForHeader(
      env, temputs, function)
  }

  def evaluateOrdinaryLightFunctionFromNonCallForTemputs(
      temputs: Temputs,
      functionTemplata: FunctionTemplata):
  Unit = {
    profiler.newProfile("FunctionTemplarEvaluateOrdinaryLightFunctionFromNonCallForTemputs", functionTemplata.debugString, () => {
        val FunctionTemplata(env, function) = functionTemplata
        val _ =
          evaluateOrdinaryLightFunctionFromNonCallForHeader(
            env, temputs, function)
      })

  }

  def evaluateTemplatedFunctionFromCallForPrototype(
    temputs: Temputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata,
    explicitTemplateArgs: Vector[ITemplata],
    args: Vector[ParamFilter]):
  IEvaluateFunctionResult[PrototypeT] = {
    profiler.newProfile("FunctionTemplarEvaluateTemplatedFunctionFromCallForPrototype", functionTemplata.debugString + "<" + explicitTemplateArgs.mkString(", ") + ">(" + args.mkString(", ") + ")", () => {
        val FunctionTemplata(env, function) = functionTemplata
        if (function.isLight()) {
          evaluateTemplatedLightFunctionFromCallForPrototype(
            env, temputs, callRange, function, explicitTemplateArgs, args)
        } else {
          evaluateTemplatedClosureFunctionFromCallForPrototype(
            env, temputs, callRange, function, explicitTemplateArgs, args)
        }
      })

  }

  private def evaluateTemplatedLightFunctionFromCallForPrototype(
      env: IEnvironment,
      temputs: Temputs,
    callRange: RangeS,
    function: FunctionA,
      explicitTemplateArgs: Vector[ITemplata],
      args: Vector[ParamFilter]):
  IEvaluateFunctionResult[PrototypeT] = {
    closureOrLightLayer.evaluateTemplatedLightFunctionFromCallForPrototype2(
        env, temputs, callRange, function, explicitTemplateArgs, args)
  }

  private def evaluateTemplatedClosureFunctionFromCallForPrototype(
    env: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    function: FunctionA,
    explicitTemplateArgs: Vector[ITemplata],
    args: Vector[ParamFilter]):
  IEvaluateFunctionResult[PrototypeT] = {
    val lambdaCitizenName2 =
      function.name match {
        case LambdaDeclarationNameS(codeLocation) => LambdaCitizenNameT(LambdaCitizenTemplateNameT(NameTranslator.translateCodeLocation(codeLocation)))
        case _ => vwat()
      }
    val KindTemplata(closureStructRef @ StructTT(_)) =
      vassertOne(
        env.lookupNearestWithName(
          profiler,
          lambdaCitizenName2,
          Set(TemplataLookupContext)))
    closureOrLightLayer.evaluateTemplatedClosureFunctionFromCallForPrototype(
      env, temputs, callRange, closureStructRef, function, explicitTemplateArgs, args)
  }
}
