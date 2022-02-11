package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.scout.patterns.{AtomSP, OverrideSP}
import net.verdagon.vale.scout.rules.RuneUsage
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.OverloadTemplar.InferFailure
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.{FunctionBannerT, FunctionHeaderT, OverrideT, PrototypeT}
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.FunctionTemplar.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
import net.verdagon.vale.templar.names.{BuildingFunctionNameWithClosuredsAndTemplateArgsT, FullNameT, INameT, NameTranslator, RuneNameT}
import net.verdagon.vale.{Err, IProfiler, Ok, RangeS, vcurious, vimpl}
//import net.verdagon.vale.templar.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.{vassert, vfail, vwat}

import scala.collection.immutable.{List, Set}

// When templaring a function, these things need to happen:
// - Spawn a local environment for the function
// - Add any closure args to the environment
// - Incorporate any template arguments into the environment
// There's a layer to take care of each of these things.
// This file is the outer layer, which spawns a local environment for the function.
class FunctionTemplarOrdinaryOrTemplatedLayer(
    opts: TemplarOptions,
  profiler: IProfiler,
  templataTemplar: TemplataTemplar,
    inferTemplar: InferTemplar,
  convertHelper: ConvertHelper,
    structTemplar: StructTemplar,
    delegate: IFunctionTemplarDelegate) {
  val middleLayer = new FunctionTemplarMiddleLayer(opts, profiler, templataTemplar, convertHelper, structTemplar, delegate)

  // This is for the early stages of Templar when it's scanning banners to put in
  // its env. We just want its banner, we don't want to evaluate it.
  def predictOrdinaryFunctionBanner(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    temputs: Temputs):
  (FunctionBannerT) = {
    val function = nearEnv.function
    checkClosureConcernsHandled(nearEnv)

    val inferences =
      inferTemplar.solveExpectComplete(
        nearEnv, temputs, function.rules, function.runeToType, function.range, Vector(), Vector())
    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)

    middleLayer.predictOrdinaryFunctionBanner(
      runedEnv, temputs, function)
  }

  def evaluateOrdinaryFunctionFromNonCallForBanner(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    temputs: Temputs,
      callRange: RangeS):
  (FunctionBannerT) = {
    val function = nearEnv.function
    checkClosureConcernsHandled(nearEnv)
    vassert(!function.isTemplate)

    val inferences =
      inferTemplar.solveExpectComplete(
        nearEnv, temputs, function.rules, function.runeToType, function.range, Vector(), Vector())
    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)

    middleLayer.getOrEvaluateFunctionForBanner(runedEnv, temputs, callRange, function)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // func main():Int{main()}
  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  // - env is the environment the templated function was made in
  def evaluateTemplatedFunctionFromCallForPrototype(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    temputs: Temputs,
    callRange: RangeS,
    explicitTemplateArgs: Vector[ITemplata],
    args: Vector[ParamFilter]):
  (IEvaluateFunctionResult[PrototypeT]) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)
    vassert(nearEnv.function.isTemplate)

    val initialSends = assembleInitialSendsFromArgs(callRange, function, args)
    val inferredTemplatas =
      inferTemplar.solveComplete(
        nearEnv,
        temputs,
        function.rules,
        function.runeToType,
        callRange,
        assembleKnownTemplatas(function, args, explicitTemplateArgs),
        initialSends
      ) match {
        case Err(e) => return (EvaluateFunctionFailure(InferFailure(e)))
        case Ok(i) => (i)
      }

    val runedEnv = addRunedDataToNearEnv(nearEnv, function.identifyingRunes.map(_.rune), inferredTemplatas)

    val prototype =
      middleLayer.getOrEvaluateFunctionForPrototype(
        runedEnv, temputs, callRange, function)

    (EvaluateFunctionSuccess(prototype))
  }

  private def assembleInitialSendsFromArgs(callRange: RangeS, function: FunctionA, args: Vector[ParamFilter]):
  Vector[InitialSend] = {
    function.params.map(_.pattern.coordRune.get).zip(args).zipWithIndex
      .map({ case ((paramRune, argTemplata), argIndex) =>
        InitialSend(RuneUsage(callRange, ArgumentRuneS(argIndex)), paramRune, CoordTemplata(argTemplata.tyype))
      })
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  // - env is the environment the templated function was made in
  def evaluateTemplatedFunctionFromCallForBanner(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosureds,
      temputs: Temputs,
      callRange: RangeS,
      alreadySpecifiedTemplateArgs: Vector[ITemplata],
      args: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)
    vassert(nearEnv.function.isTemplate)

    val initialSends = assembleInitialSendsFromArgs(callRange, function, args)
    val inferredTemplatas =
      inferTemplar.solveComplete(
        nearEnv,
        temputs,
        function.rules,
        function.runeToType,
        callRange,
        assembleKnownTemplatas(function, args, alreadySpecifiedTemplateArgs),
        initialSends
      ) match {
        case Err(e) => return (EvaluateFunctionFailure(InferFailure(e)))
        case Ok(i) => (i)
      }

    val runedEnv =
      addRunedDataToNearEnv(
        nearEnv, function.identifyingRunes.map(_.rune), inferredTemplatas)

    val banner =
      middleLayer.getOrEvaluateFunctionForBanner(
        runedEnv, temputs, callRange, function)
    (EvaluateFunctionSuccess(banner))
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  def evaluateOrdinaryFunctionFromNonCallForHeader(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosureds,
      temputs: Temputs):
  (FunctionHeaderT) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)
    vassert(!function.isTemplate)

    val inferences =
      inferTemplar.solveExpectComplete(
        nearEnv, temputs, function.rules, function.runeToType, function.range, Vector(), Vector())
    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)

    middleLayer.getOrEvaluateFunctionForHeader(
      runedEnv, temputs, function.range, function)
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  def evaluateTemplatedFunctionFromNonCallForHeader(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    temputs: Temputs):
  (FunctionHeaderT) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)

    // Check preconditions
    function.body match {
      case CodeBodyS(body1) => vassert(body1.closuredNames.isEmpty)
      case _ =>
    }
    vassert(nearEnv.function.isTemplate)

    // See IMCBT for why we can look up identifying runes in the environment.
    val initialKnowns =
      function.identifyingRunes.flatMap(identifyingRune => {
        nearEnv.lookupNearestWithName(
          profiler, RuneNameT(identifyingRune.rune), Set(TemplataLookupContext))
          .map(InitialKnown(identifyingRune, _))
      })
    val inferences =
      inferTemplar.solveExpectComplete(
        nearEnv, temputs, function.rules, function.runeToType, function.range, initialKnowns, Vector())

    // See FunctionTemplar doc for what outer/runes/inner envs are.
    val runedEnv = addRunedDataToNearEnv(nearEnv, function.identifyingRunes.map(_.rune), inferences)

    middleLayer.getOrEvaluateFunctionForHeader(
      runedEnv, temputs, function.range, function)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // func main():Int{main()}
  def evaluateOrdinaryFunctionFromNonCallForPrototype(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    temputs: Temputs,
    callRange: RangeS):
  (PrototypeT) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)
    vassert(!function.isTemplate)

    val inferences =
      inferTemplar.solveExpectComplete(
        nearEnv, temputs, function.rules, function.runeToType, function.range, Vector(), Vector())
    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)

    middleLayer.getOrEvaluateFunctionForPrototype(
      runedEnv, temputs, callRange, function)
  }


  // This is called while we're trying to figure out what functionSs to call when there
  // are a lot of overloads available.
  // This assumes it met any type bound restrictions (or, will; not implemented yet)
  def evaluateTemplatedLightBannerFromCall(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosureds,
      temputs: Temputs,
    callRange: RangeS,
      explicitTemplateArgs: Vector[ITemplata],
      args: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    val function = nearEnv.function
    // Check preconditions
    function.body match {
      case CodeBodyS(body1) => vassert(body1.closuredNames.isEmpty)
      case _ =>
    }
    vassert(nearEnv.function.isTemplate)

    val initialSends = assembleInitialSendsFromArgs(callRange, function, args)
    val initialKnowns = assembleKnownTemplatas(function, args, explicitTemplateArgs)
    val inferences =
      inferTemplar.solveComplete(
        nearEnv,
        temputs,
        function.rules,
        function.runeToType,
        callRange,
        initialKnowns,
        initialSends) match {
      case Err(e) => return EvaluateFunctionFailure(InferFailure(e))
      case Ok(inferredTemplatas) => inferredTemplatas
    }

    // See FunctionTemplar doc for what outer/runes/inner envs are.
    val runedEnv = addRunedDataToNearEnv(nearEnv, function.identifyingRunes.map(_.rune), inferences)

    val banner =
      middleLayer.getOrEvaluateFunctionForBanner(
        runedEnv, temputs, callRange, function)

    (EvaluateFunctionSuccess(banner))
  }

  private def assembleKnownTemplatas(
    function: FunctionA,
    args: Vector[ParamFilter],
    explicitTemplateArgs: Vector[ITemplata]):
  Vector[InitialKnown] = {
    // Sometimes we look for an overload for a given override, assemble knowns from that here
    args.zip(function.params).collect({
      case (ParamFilter(_, Some(OverrideT(argOverrideKind))), ParameterS(AtomSP(_, _, Some(OverrideSP(_, paramOverrideRune)), _, _))) => {
        InitialKnown(paramOverrideRune, KindTemplata(argOverrideKind))
      }
    }) ++
    function.identifyingRunes.zip(explicitTemplateArgs).map({
      case (identifyingRune, explicitArg) => {
        InitialKnown(identifyingRune, explicitArg)
      }
    })
  }

  private def checkClosureConcernsHandled(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds
  ): Unit = {
    val function = nearEnv.function
    function.body match {
      case CodeBodyS(body1) => {
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
    identifyingRunes: Vector[IRuneS],
    templatasByRune: Map[IRuneS, ITemplata]
  ): BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs = {
    val BuildingFunctionEnvironmentWithClosureds(globalEnv, parentEnv, fullName, templatas, function, variables) = nearEnv

    val identifyingTemplatas = identifyingRunes.map(templatasByRune)
    val newName =
      FullNameT(
        fullName.packageCoord,
        fullName.initSteps,
        BuildingFunctionNameWithClosuredsAndTemplateArgsT(
          fullName.last.templateName, identifyingTemplatas))

    BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs(
      globalEnv,
      parentEnv,
      newName,
      templatas.addEntries(
        templatasByRune.toVector
          .map({ case (k, v) => (RuneNameT(k), TemplataEnvEntry(v)) })),
      function,
      variables)
  }
}
