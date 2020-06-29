package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.{CodeBodyA, FunctionA, IRulexAR, IRuneA, ITemplataType}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.FunctionTemplar.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
import net.verdagon.vale.templar.infer.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.{vcurious, vimpl}
//import net.verdagon.vale.templar.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.templar.templata.TemplataTemplar
import net.verdagon.vale.{vassert, vfail, vwat}

import scala.collection.immutable.{List, Set}

// When templaring a function, these things need to happen:
// - Spawn a local environment for the function
// - Add any closure args to the environment
// - Incorporate any template arguments into the environment
// There's a layer to take care of each of these things.
// This file is the outer layer, which spawns a local environment for the function.
object FunctionTemplarOrdinaryOrTemplatedLayer {
  // This is for the early stages of Templar when it's scanning banners to put in
  // its env. We just want its banner, we don't want to evaluate it.
  def predictOrdinaryFunctionBanner(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    temputs: TemputsBox):
  (FunctionBanner2) = {
    val function = nearEnv.function
    checkClosureConcernsHandled(nearEnv)

    val inferences =
      InferTemplar.inferOrdinaryRules(
        nearEnv, temputs, function.templateRules, function.typeByRune, function.localRunes)
    val runedEnv = addRunedDataToNearEnv(nearEnv, List(), inferences)

    FunctionTemplarMiddleLayer.predictOrdinaryFunctionBanner(
      runedEnv, temputs, function)
  }

  def evaluateOrdinaryFunctionFromNonCallForBanner(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    temputs: TemputsBox):
  (FunctionBanner2) = {
    val function = nearEnv.function
    checkClosureConcernsHandled(nearEnv)
    vassert(!function.isTemplate)

    val inferences =
      InferTemplar.inferOrdinaryRules(
        nearEnv, temputs, function.templateRules, function.typeByRune, function.localRunes)
    val runedEnv = addRunedDataToNearEnv(nearEnv, List(), inferences)

    FunctionTemplarMiddleLayer.getOrEvaluateFunctionForBanner(runedEnv, temputs, function)
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  // - env is the environment the templated function was made in
  def evaluateTemplatedFunctionFromCallForHeader(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosureds,
      temputs: TemputsBox,
      argTypes2: List[Coord]):
  (FunctionHeader2) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)
    vassert(nearEnv.function.isTemplate)

    val maybeInferredTemplatas =
      InferTemplar.inferFromArgCoords(
        nearEnv,
        temputs,
        function.identifyingRunes,
        function.templateRules,
        function.typeByRune,
        function.localRunes,
        function.params.map(_.pattern),
        function.maybeRetCoordRune,
        List(),
        argTypes2.map(arg => ParamFilter(arg, None)))
    val InferSolveSuccess(inferredTemplatas) = maybeInferredTemplatas

    val runedEnv =
      addRunedDataToNearEnv(
        nearEnv,
        function.identifyingRunes,
        inferredTemplatas.templatasByRune)

    FunctionTemplarMiddleLayer.getOrEvaluateFunctionForHeader(runedEnv, temputs, function)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // fn main():Int{main()}
  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  // - env is the environment the templated function was made in
  def evaluateTemplatedFunctionFromCallForPrototype(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    temputs: TemputsBox,
    explicitTemplateArgs: List[ITemplata],
    args: List[ParamFilter]):
  (IEvaluateFunctionResult[Prototype2]) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)
    vassert(nearEnv.function.isTemplate)

    val inferredTemplatas =
      InferTemplar.inferFromArgCoords(
          nearEnv,
          temputs,
          function.identifyingRunes,
        function.templateRules,
        function.typeByRune,
          function.localRunes,
          function.params.map(_.pattern),
          function.maybeRetCoordRune,
          explicitTemplateArgs,
          args) match {
        case (isf @ InferSolveFailure(_, _, _, _, _, _)) => {
          return (EvaluateFunctionFailure(isf.toString))
        }
        case (InferSolveSuccess(i)) => (i)
      }

    val runedEnv = addRunedDataToNearEnv(nearEnv, function.identifyingRunes, inferredTemplatas.templatasByRune)

    val prototype =
      FunctionTemplarMiddleLayer.getOrEvaluateFunctionForPrototype(
        runedEnv, temputs, function)
    (EvaluateFunctionSuccess(prototype))
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  // - env is the environment the templated function was made in
  def evaluateTemplatedFunctionFromCallForBanner(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosureds,
      temputs: TemputsBox,
      alreadySpecifiedTemplateArgs: List[ITemplata],
      paramFilters: List[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBanner2]) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)
    vassert(nearEnv.function.isTemplate)

    val inferredTemplatas =
      InferTemplar.inferFromArgCoords(
        nearEnv,
        temputs,
        function.identifyingRunes,
        function.templateRules,
        function.typeByRune,
        function.localRunes,
        function.params.map(_.pattern),
        function.maybeRetCoordRune,
        alreadySpecifiedTemplateArgs,
        paramFilters) match {
      case (isf @ InferSolveFailure(_, _, _, _, _, _)) => {
        return (EvaluateFunctionFailure(isf.toString))
      }
      case (InferSolveSuccess(i)) => (i)
    }

    val runedEnv =
      addRunedDataToNearEnv(
        nearEnv, function.identifyingRunes, inferredTemplatas.templatasByRune)

    val banner =
      FunctionTemplarMiddleLayer.getOrEvaluateFunctionForBanner(
        runedEnv, temputs, function)
    (EvaluateFunctionSuccess(banner))
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  def evaluateTemplatedFunctionFromNonCallForHeader(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosureds,
      temputs: TemputsBox):
  (FunctionHeader2) = {
    val function = nearEnv.function
    // Check preconditions

    checkClosureConcernsHandled(nearEnv)
    vassert(nearEnv.function.isTemplate)

    vassert(nearEnv.function.identifyingRunes.size == List().size);

    val result =
      InferTemplar.inferFromExplicitTemplateArgs(
        nearEnv,
        temputs,
        function.identifyingRunes,
        function.templateRules,
        function.typeByRune,
        function.localRunes,
        function.params.map(_.pattern),
        function.maybeRetCoordRune,
        List())
    val inferences =
      result match {
        case isf @ InferSolveFailure(_, _, _, _, _, _) => {
          vfail("Couldnt figure out template args! Cause:\n" + isf)
        }
        case InferSolveSuccess(i) => i
      }

    val runedEnv = addRunedDataToNearEnv(nearEnv, List(), inferences.templatasByRune)

    FunctionTemplarMiddleLayer.getOrEvaluateFunctionForHeader(runedEnv, temputs, function)
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  def evaluateOrdinaryFunctionFromNonCallForHeader(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosureds,
      temputs: TemputsBox):
  (FunctionHeader2) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)
    vassert(!function.isTemplate)


    val inferences =
      InferTemplar.inferOrdinaryRules(
        nearEnv, temputs, function.templateRules, function.typeByRune, function.localRunes)
    val runedEnv = addRunedDataToNearEnv(nearEnv, List(), inferences)

    FunctionTemplarMiddleLayer.getOrEvaluateFunctionForHeader(
      runedEnv, temputs, function)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // fn main():Int{main()}
  def evaluateOrdinaryFunctionFromNonCallForPrototype(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    temputs: TemputsBox):
  (Prototype2) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)
    vassert(!function.isTemplate)

    val inferences =
      InferTemplar.inferOrdinaryRules(
        nearEnv, temputs, function.templateRules, function.typeByRune, function.localRunes)
    val runedEnv = addRunedDataToNearEnv(nearEnv, List(), inferences)

    FunctionTemplarMiddleLayer.getOrEvaluateFunctionForPrototype(
      runedEnv, temputs, function)
  }


  // This is called while we're trying to figure out what functionSs to call when there
  // are a lot of overloads available.
  // This assumes it met any type bound restrictions (or, will; not implemented yet)
  def evaluateTemplatedLightBannerFromCall(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosureds,
      temputs: TemputsBox,
      explicitTemplateArgs: List[ITemplata],
      args: List[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBanner2]) = {
    val function = nearEnv.function
    // Check preconditions
    function.body match {
      case CodeBodyA(body1) => vassert(body1.closuredNames.isEmpty)
      case _ =>
    }
    vassert(nearEnv.function.isTemplate)

    val inferences =
      InferTemplar.inferFromArgCoords(
        nearEnv,
        temputs,
        function.identifyingRunes,
        function.templateRules,
        function.typeByRune,
        function.localRunes,
        function.params.map(_.pattern),
        function.maybeRetCoordRune,
        explicitTemplateArgs,
        args) match {
      case (isc @ InferSolveFailure(_, _, _, _, _, _)) => {
        return (EvaluateFunctionFailure[FunctionBanner2](isc.toString))
      }
      case (InferSolveSuccess(inferredTemplatas)) => (inferredTemplatas.templatasByRune)
    }

    // See FunctionTemplar doc for what outer/runes/inner envs are.
    val runedEnv = addRunedDataToNearEnv(nearEnv, function.identifyingRunes, inferences)

    val banner =
      FunctionTemplarMiddleLayer.getOrEvaluateFunctionForBanner(
        runedEnv, temputs, function)

    (EvaluateFunctionSuccess(banner))
  }

//  def scanOrdinaryInterfaceMember(
//    nearEnv: BuildingFunctionEnvironmentWithClosureds,
//    temputs: TemputsBox,
//    interfaceExplicitTemplateArgs: List[ITemplata]):
//  (FunctionHeader2) = {
//    val function = nearEnv.function
//
//    val (rules, typeByRune) =
//      EnvironmentUtils.assembleRulesFromFunctionAndContainers(nearEnv.function)
//    val result =
//      InferTemplar.inferFromExplicitTemplateArgs(
//        nearEnv,
//        temputs,
//        function.identifyingRunes,
//        rules,
//        typeByRune,
//        function.localRunes,
//        function.params.map(_.pattern),
//        function.maybeRetCoordRune,
//        interfaceExplicitTemplateArgs)
//
//    // A precondition of this call was that it was an ordinary function, which is why
//    // maybeTemplataByRune is filled.
//    val templataByRune =
//      result match {
//        case isf @ InferSolveFailure(_, _, _, _, _, _) => {
//          vfail("Couldnt figure out template args! Cause:\n" + isf)
//        }
//        case InferSolveSuccess(i) => i.templatasByRune
//      }
//
//    val runedEnv = addRunedDataToNearEnv(nearEnv, function.identifyingRunes, templataByRune)
//
//    val header = FunctionTemplarMiddleLayer.makeInterfaceFunction(runedEnv, temputs)
//    header
//  }

  private def checkClosureConcernsHandled(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds
  ): Unit = {
    val function = nearEnv.function
    function.body match {
      case CodeBodyA(body1) => {
        body1.closuredNames.foreach(name => {
          vassert(nearEnv.variables.exists(_.id.last == NameTranslator.translateNameStep(name)))
        })
      }
      case _ =>
    }
  }

  // IOW, add the necessary data to turn the near env into the runed env.
  private def addRunedDataToNearEnv(
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    identifyingRunes: List[IRuneA],
    templatasByRune: Map[IRune2, ITemplata]
  ): BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs = {
    val BuildingFunctionEnvironmentWithClosureds(parentEnv, fullName, function, variables, entries) = nearEnv

    val identifyingTemplatas = identifyingRunes.map(NameTranslator.translateRune).map(templatasByRune)
    val newName =
      FullName2(
        fullName.initSteps,
        BuildingFunctionNameWithClosuredsAndTemplateArgs2(
          fullName.last.templateName, identifyingTemplatas))

    BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs(
      parentEnv,
      newName,
      function,
      variables,
      EnvironmentUtils.addEntries(
        entries,
        templatasByRune.map({ case (k, v) => (k, List(TemplataEnvEntry(v))) })
        .toMap[IName2, List[IEnvEntry]]))
  }
}
