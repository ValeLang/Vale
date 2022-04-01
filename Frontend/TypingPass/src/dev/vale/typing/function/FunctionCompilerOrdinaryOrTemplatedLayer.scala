package dev.vale.typing.function

import dev.vale.{Err, Interner, Ok, RangeS, vassert}
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.{ArgumentRuneS, CodeBodyS, IRuneS}
import dev.vale.postparsing.rules.RuneUsage
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.function.FunctionCompiler.IEvaluateFunctionResult
import dev.vale.postparsing.patterns._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.postparsing._
import dev.vale.typing.OverloadResolver.InferFailure
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import FunctionCompiler.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
import dev.vale.typing.ast.{FunctionBannerT, FunctionHeaderT, PrototypeT}
import dev.vale.typing.env.{BuildingFunctionEnvironmentWithClosureds, BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs, TemplataEnvEntry, TemplataLookupContext}
import dev.vale.typing.{ConvertHelper, InferCompiler, InitialKnown, InitialSend, TypingPassOptions, TemplataCompiler, CompilerOutputs}
import dev.vale.typing.names.{BuildingFunctionNameWithClosuredsAndTemplateArgsT, FullNameT, NameTranslator, RuneNameT}
import dev.vale.typing.templata.{CoordTemplata, ITemplata}
import dev.vale.typing.types.ParamFilter
import dev.vale.typing.names.BuildingFunctionNameWithClosuredsAndTemplateArgsT
import dev.vale.{Err, Interner, Ok, Profiler, RangeS, vcurious, vimpl}
//import dev.vale.typingpass.infer.{InferSolveFailure, InferSolveSuccess}
import dev.vale.vwat

import scala.collection.immutable.{List, Set}

// When typingpassing a function, these things need to happen:
// - Spawn a local environment for the function
// - Add any closure args to the environment
// - Incorporate any template arguments into the environment
// There's a layer to take care of each of these things.
// This file is the outer layer, which spawns a local environment for the function.
class FunctionCompilerOrdinaryOrTemplatedLayer(
    opts: TypingPassOptions,

    interner: Interner,
    nameTranslator: NameTranslator,
    templataCompiler: TemplataCompiler,
    inferCompiler: InferCompiler,
    convertHelper: ConvertHelper,
    structCompiler: StructCompiler,
    delegate: IFunctionCompilerDelegate) {
  val middleLayer = new FunctionCompilerMiddleLayer(opts, interner, nameTranslator, templataCompiler, convertHelper, structCompiler, delegate)

  // This is for the early stages of Compiler when it's scanning banners to put in
  // its env. We just want its banner, we don't want to evaluate it.
  def predictOrdinaryFunctionBanner(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    coutputs: CompilerOutputs):
  (FunctionBannerT) = {
    val function = nearEnv.function
    checkClosureConcernsHandled(nearEnv)

    val inferences =
      inferCompiler.solveExpectComplete(
        nearEnv, coutputs, function.rules, function.runeToType, function.range, Vector(), Vector())
    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)

    middleLayer.predictOrdinaryFunctionBanner(
      runedEnv, coutputs, function)
  }

  def evaluateOrdinaryFunctionFromNonCallForBanner(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    coutputs: CompilerOutputs,
      callRange: RangeS):
  (FunctionBannerT) = {
    val function = nearEnv.function
    checkClosureConcernsHandled(nearEnv)
    vassert(!function.isTemplate)

    val inferences =
      inferCompiler.solveExpectComplete(
        nearEnv, coutputs, function.rules, function.runeToType, function.range, Vector(), Vector())
    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)

    middleLayer.getOrEvaluateFunctionForBanner(runedEnv, coutputs, callRange, function)
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
    coutputs: CompilerOutputs,
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
      inferCompiler.solveComplete(
        nearEnv,
        coutputs,
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
        runedEnv, coutputs, callRange, function)

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
      coutputs: CompilerOutputs,
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
      inferCompiler.solveComplete(
        nearEnv,
        coutputs,
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
        runedEnv, coutputs, callRange, function)
    (EvaluateFunctionSuccess(banner))
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  def evaluateOrdinaryFunctionFromNonCallForHeader(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosureds,
      coutputs: CompilerOutputs):
  (FunctionHeaderT) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)
    vassert(!function.isTemplate)

    val inferences =
      inferCompiler.solveExpectComplete(
        nearEnv, coutputs, function.rules, function.runeToType, function.range, Vector(), Vector())
    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)

    middleLayer.getOrEvaluateFunctionForHeader(
      runedEnv, coutputs, function.range, function)
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  def evaluateTemplatedFunctionFromNonCallForHeader(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    coutputs: CompilerOutputs):
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
          interner.intern(RuneNameT(identifyingRune.rune)), Set(TemplataLookupContext))
          .map(InitialKnown(identifyingRune, _))
      })
    val inferences =
      inferCompiler.solveExpectComplete(
        nearEnv, coutputs, function.rules, function.runeToType, function.range, initialKnowns, Vector())

    // See FunctionCompiler doc for what outer/runes/inner envs are.
    val runedEnv = addRunedDataToNearEnv(nearEnv, function.identifyingRunes.map(_.rune), inferences)

    middleLayer.getOrEvaluateFunctionForHeader(
      runedEnv, coutputs, function.range, function)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // func main():Int{main()}
  def evaluateOrdinaryFunctionFromNonCallForPrototype(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    coutputs: CompilerOutputs,
    callRange: RangeS):
  (PrototypeT) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)
    vassert(!function.isTemplate)

    val inferences =
      inferCompiler.solveExpectComplete(
        nearEnv, coutputs, function.rules, function.runeToType, function.range, Vector(), Vector())
    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)

    middleLayer.getOrEvaluateFunctionForPrototype(
      runedEnv, coutputs, callRange, function)
  }


  // This is called while we're trying to figure out what functionSs to call when there
  // are a lot of overloads available.
  // This assumes it met any type bound restrictions (or, will; not implemented yet)
  def evaluateTemplatedLightBannerFromCall(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosureds,
      coutputs: CompilerOutputs,
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
      inferCompiler.solveComplete(
        nearEnv,
        coutputs,
        function.rules,
        function.runeToType,
        callRange,
        initialKnowns,
        initialSends) match {
      case Err(e) => return EvaluateFunctionFailure(InferFailure(e))
      case Ok(inferredTemplatas) => inferredTemplatas
    }

    // See FunctionCompiler doc for what outer/runes/inner envs are.
    val runedEnv = addRunedDataToNearEnv(nearEnv, function.identifyingRunes.map(_.rune), inferences)

    val banner =
      middleLayer.getOrEvaluateFunctionForBanner(
        runedEnv, coutputs, callRange, function)

    (EvaluateFunctionSuccess(banner))
  }

  private def assembleKnownTemplatas(
    function: FunctionA,
    args: Vector[ParamFilter],
    explicitTemplateArgs: Vector[ITemplata]):
  Vector[InitialKnown] = {
    // Sometimes we look for an overload for a given override, assemble knowns from that here
//    args.zip(function.params).collect({
//      case (ParamFilter(_, Some(OverrideT(argOverrideKind))), ParameterS(AtomSP(_, _, Some(OverrideSP(_, paramOverrideRune)), _, _))) => {
//        InitialKnown(paramOverrideRune, KindTemplata(argOverrideKind))
//      }
//    }) ++
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
          vassert(nearEnv.variables.exists(_.id.last == nameTranslator.translateNameStep(name)))
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
        interner,
        templatasByRune.toVector
          .map({ case (k, v) => (interner.intern(RuneNameT(k)), TemplataEnvEntry(v)) })),
      function,
      variables)
  }
}
