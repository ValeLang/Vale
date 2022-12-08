package dev.vale.typing.function

import dev.vale.{Err, Interner, Keywords, Ok, Profiler, RangeS, StrI, typing, vassert, vassertSome, vcurious, vfail, vimpl, vpass}
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.rules.{IRulexSR, RuneUsage}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.function.FunctionCompiler.IEvaluateFunctionResult
import dev.vale.postparsing.patterns._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.postparsing.{IEnvironmentS => _, _}
import dev.vale.typing.OverloadResolver.InferFailure
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import FunctionCompiler.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
import dev.vale.solver.{CompleteSolve, FailedSolve, IncompleteSolve, Solver}
import dev.vale.typing.ast.{FunctionBannerT, FunctionHeaderT, PrototypeT}
import dev.vale.typing.env.{BuildingFunctionEnvironmentWithClosureds, BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs, TemplataEnvEntry, TemplataLookupContext}
import dev.vale.typing.infer.ITypingPassSolverError
import dev.vale.typing.{CompilerOutputs, ConvertHelper, InferCompiler, InitialKnown, InitialSend, TemplataCompiler, TypingPassOptions}
import dev.vale.typing.names.{FunctionNameT, FunctionTemplateNameT, IdT, NameTranslator, PlaceholderNameT, PlaceholderTemplateNameT, ReachablePrototypeNameT, RuneNameT, StructNameT, StructTemplateNameT}
import dev.vale.typing.templata._
import dev.vale.typing.types.CoordT
//import dev.vale.typingpass.infer.{InferSolveFailure, InferSolveSuccess}
import dev.vale.vwat

import scala.collection.immutable.{List, Set}

// When typingpassing a function, these things need to happen:
// - Spawn a local environment for the function
// - Add any closure args to the environment
// - Incorporate any template arguments into the environment
// There's a layer to take care of each of these things.
// This file is the outer layer, which spawns a local environment for the function.
class FunctionCompilerSolvingLayer(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    nameTranslator: NameTranslator,
    templataCompiler: TemplataCompiler,
    inferCompiler: InferCompiler,
    convertHelper: ConvertHelper,
    structCompiler: StructCompiler,
    delegate: IFunctionCompilerDelegate) {
  val middleLayer = new FunctionCompilerMiddleLayer(opts, interner, keywords, nameTranslator, templataCompiler, convertHelper, structCompiler, delegate)

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // func main():Int{main()}
  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  // - env is the environment the templated function was made in
  def evaluateTemplatedFunctionFromCallForPrototype(
    // The environment the function was defined in.
    outerEnv: BuildingFunctionEnvironmentWithClosureds,
    coutputs: CompilerOutputs,
    originalCallingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    explicitTemplateArgs: Vector[ITemplata[ITemplataType]],
    args: Vector[CoordT],
    verifyConclusions: Boolean):
  (IEvaluateFunctionResult) = {
    val function = outerEnv.function
    // Check preconditions
    checkClosureConcernsHandled(outerEnv)

    val callSiteRules =
        TemplataCompiler.assembleCallSiteRules(
            function.rules, function.genericParameters, explicitTemplateArgs.size)

    val initialSends = assembleInitialSendsFromArgs(callRange.head, function, args.map(Some(_)))
    val CompleteCompilerSolve(_, inferredTemplatas, runeToFunctionBound, reachableBounds) =
      inferCompiler.solveComplete(
        InferEnv(originalCallingEnv, callRange, outerEnv),
        coutputs,
        callSiteRules,
        function.runeToType,
        callRange,
        assembleKnownTemplatas(function, explicitTemplateArgs),
        initialSends,
        false,
        false,
        Vector()
      ) match {
        case Err(e) => return (EvaluateFunctionFailure(InferFailure(e)))
        case Ok(i) => (i)
      }

    val runedEnv =
      addRunedDataToNearEnv(outerEnv, function.genericParameters.map(_.rune.rune), inferredTemplatas, reachableBounds)

    val header =
      middleLayer.getOrEvaluateFunctionForHeader(
        outerEnv, runedEnv, coutputs, callRange, function)

    coutputs.addInstantiationBounds(header.toPrototype.fullName, runeToFunctionBound)
    EvaluateFunctionSuccess(PrototypeTemplata(function.range, header.toPrototype), inferredTemplatas)
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  // - env is the environment the templated function was made in
  def evaluateTemplatedFunctionFromCallForBanner(
      // The environment the function was defined in.
      declaringEnv: BuildingFunctionEnvironmentWithClosureds,
      coutputs: CompilerOutputs,
      originalCallingEnv: IEnvironment, // See CSSNCE
      callRange: List[RangeS],
      alreadySpecifiedTemplateArgs: Vector[ITemplata[ITemplataType]],
      args: Vector[CoordT]):
  (IEvaluateFunctionResult) = {
    val function = declaringEnv.function
    // Check preconditions
    checkClosureConcernsHandled(declaringEnv)
    vassert(declaringEnv.function.isTemplate)

    val callSiteRules =
        TemplataCompiler.assembleCallSiteRules(
            function.rules, function.genericParameters, 0)

    val initialSends = assembleInitialSendsFromArgs(callRange.head, function, args.map(Some(_)))
    val CompleteCompilerSolve(_, inferredTemplatas, runeToFunctionBound, reachableBounds) =
      inferCompiler.solveComplete(
        InferEnv(originalCallingEnv, callRange, declaringEnv),
        coutputs,
        callSiteRules,
        function.runeToType,
        callRange,
        assembleKnownTemplatas(function, alreadySpecifiedTemplateArgs),
        initialSends,
        true,
        false,
        Vector()
      ) match {
        case Err(e) => return (EvaluateFunctionFailure(InferFailure(e)))
        case Ok(i) => (i)
      }

    val runedEnv =
      addRunedDataToNearEnv(
        declaringEnv, function.genericParameters.map(_.rune.rune), inferredTemplatas, reachableBounds)

    val prototype =
      middleLayer.getOrEvaluateTemplatedFunctionForBanner(
        declaringEnv, runedEnv, coutputs, callRange, function)

    coutputs.addInstantiationBounds(prototype.prototype.fullName, runeToFunctionBound)

    EvaluateFunctionSuccess(prototype, inferredTemplatas)
  }

  // This is called while we're trying to figure out what functionSs to call when there
  // are a lot of overloads available.
  // This assumes it met any type bound restrictions (or, will; not implemented yet)
  def evaluateTemplatedLightBannerFromCall(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosureds,
      coutputs: CompilerOutputs,
    originalCallingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
      explicitTemplateArgs: Vector[ITemplata[ITemplataType]],
      args: Vector[CoordT]):
  (IEvaluateFunctionResult) = {
    val function = nearEnv.function
    // Check preconditions
    function.body match {
      case CodeBodyS(body1) => vassert(body1.closuredNames.isEmpty)
      case _ =>
    }
    vassert(nearEnv.function.isTemplate)

    val callSiteRules =
      TemplataCompiler.assembleCallSiteRules(
        function.rules, function.genericParameters, explicitTemplateArgs.size)

    val initialSends = assembleInitialSendsFromArgs(callRange.head, function, args.map(Some(_)))
    val initialKnowns = assembleKnownTemplatas(function, explicitTemplateArgs)
    val CompleteCompilerSolve(_, inferences, runeToFunctionBound, reachableBounds) =
      inferCompiler.solveComplete(
        InferEnv(originalCallingEnv, callRange, nearEnv),
        coutputs,
        callSiteRules,
        function.runeToType,
        callRange,
        initialKnowns,
        initialSends,
        true,
        false,
        Vector()) match {
      case Err(e) => return EvaluateFunctionFailure(InferFailure(e))
      case Ok(inferredTemplatas) => inferredTemplatas
    }

    // See FunctionCompiler doc for what outer/runes/inner envs are.
    val runedEnv = addRunedDataToNearEnv(nearEnv, function.genericParameters.map(_.rune.rune), inferences, reachableBounds)

    val prototypeTemplata =
      middleLayer.getOrEvaluateTemplatedFunctionForBanner(
        nearEnv, runedEnv, coutputs, callRange, function)

    coutputs.addInstantiationBounds(prototypeTemplata.prototype.fullName, runeToFunctionBound)
    EvaluateFunctionSuccess(prototypeTemplata, inferences)
  }

  private def assembleKnownTemplatas(
    function: FunctionA,
    explicitTemplateArgs: Vector[ITemplata[ITemplataType]]):
  Vector[InitialKnown] = {
    function.genericParameters.zip(explicitTemplateArgs).map({
      case (genericParam, explicitArg) => {
        InitialKnown(genericParam.rune, explicitArg)
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
          vassert(nearEnv.variables.exists(_.id.localName == nameTranslator.translateNameStep(name)))
        })
      }
      case _ =>
    }
  }

  // IOW, add the necessary data to turn the near env into the runed env.
  private def addRunedDataToNearEnv(
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    identifyingRunes: Vector[IRuneS],
    templatasByRune: Map[IRuneS, ITemplata[ITemplataType]],
    reachableBoundsFromParamsAndReturn: Vector[PrototypeTemplata]
    // I suspect we'll eventually need some impl bounds here
  ): BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs = {
    val BuildingFunctionEnvironmentWithClosureds(globalEnv, parentEnv, fullName, templatas, function, variables, isRootCompilingDenizen) = nearEnv

    val identifyingTemplatas = identifyingRunes.map(templatasByRune)

    val newEntries =
      templatas.addEntries(
        interner,
        reachableBoundsFromParamsAndReturn.zipWithIndex.toVector
          .map({ case (t, i) => (interner.intern(ReachablePrototypeNameT(i)), TemplataEnvEntry(t)) }) ++
        templatasByRune.toVector
          .map({ case (k, v) => (interner.intern(RuneNameT(k)), TemplataEnvEntry(v)) }))

    BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs(
      globalEnv, parentEnv, fullName, identifyingTemplatas, newEntries, function, variables, isRootCompilingDenizen)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // func main():Int{main()}
  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  // - env is the environment the templated function was made in
  def evaluateGenericFunctionFromCallForPrototype(
    // The environment the function was defined in.
    outerEnv: BuildingFunctionEnvironmentWithClosureds,
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    explicitTemplateArgs: Vector[ITemplata[ITemplataType]],
    args: Vector[Option[CoordT]]):
  (IEvaluateFunctionResult) = {
    val function = outerEnv.function
    // Check preconditions
    checkClosureConcernsHandled(outerEnv)

    val callSiteRules =
        TemplataCompiler.assembleCallSiteRules(
            function.rules, function.genericParameters, explicitTemplateArgs.size)

    function.name match {
      case FunctionNameS(StrI("len"), _) => {
        vpass()
      }
      case _ =>
    }

    val initialSends = assembleInitialSendsFromArgs(callRange.head, function, args)
    val CompleteCompilerSolve(_, inferredTemplatas, runeToFunctionBound, reachableBounds) =
      inferCompiler.solveComplete(
        InferEnv(callingEnv, callRange, outerEnv),
        coutputs,
        callSiteRules,
        function.runeToType,
        callRange,
        assembleKnownTemplatas(function, explicitTemplateArgs),
        initialSends,
        true,
        false,
        Vector()
      ) match {
        case Err(e) => return (EvaluateFunctionFailure(InferFailure(e)))
        case Ok(i) => (i)
      }

    val runedEnv = addRunedDataToNearEnv(outerEnv, function.genericParameters.map(_.rune.rune), inferredTemplatas, reachableBounds)

    val prototype =
      middleLayer.getGenericFunctionPrototypeFromCall(
        runedEnv, coutputs, callRange, function)

    coutputs.addInstantiationBounds(prototype.fullName, runeToFunctionBound)

    EvaluateFunctionSuccess(PrototypeTemplata(function.range, prototype), inferredTemplatas)
  }

  def evaluateGenericFunctionParentForPrototype(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    args: Vector[Option[CoordT]]):
  IEvaluateFunctionResult = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)

    val functionDefinitionRules =
      function.rules.filter(InferCompiler.includeRuleInDefinitionSolve)

    val initialSends = assembleInitialSendsFromArgs(callRange.head, function, args)

    // This is so that we can feed in the self interface to see what it indirectly determines.
    // It will turn a:
    //   func map<T, F>(self Opt<T>, f F, t T) { ... }
    // into a:
    //   func map<F>(self Opt<$0>, f F, t $0) { ... }
    val preliminaryEnvs = InferEnv(callingEnv, callRange, nearEnv)
    val preliminarySolver =
      inferCompiler.makeSolver(
        preliminaryEnvs,
        coutputs,
        functionDefinitionRules,
        function.runeToType,
        function.range :: callRange,
        Vector(),
        initialSends)
    inferCompiler.continue(preliminaryEnvs, coutputs, preliminarySolver) match {
      case Ok(()) =>
      case Err(f) => {
        throw CompileErrorExceptionT(typing.TypingPassSolverError(function.range :: callRange, f))
      }
    }
    val preliminaryInferences =
      inferCompiler.interpretResults(
          preliminaryEnvs,
          coutputs,
          function.range :: callRange,
          function.runeToType,
          functionDefinitionRules,
          false,
          true,
          Vector(),
        preliminarySolver) match {
        case f @ FailedCompilerSolve(_, _, err) => {
          throw CompileErrorExceptionT(typing.TypingPassSolverError(function.range :: callRange, f))
        }
        case IncompleteCompilerSolve(_, _, _, incompleteConclusions) => incompleteConclusions
        case CompleteCompilerSolve(_, conclusions, _, Vector()) => conclusions
      }
    // Now we can use preliminaryInferences to know whether or not we need a placeholder for an identifying rune.
    // Our
    //   func map<F>(self Opt<$0>, f F) { ... }
    // will need one placeholder, for F.

    val placeholderInitialKnownsFromFunction =
      function.genericParameters.zipWithIndex.flatMap({ case (genericParam, index) =>
        preliminaryInferences.get(genericParam.rune.rune) match {
          case Some(x) => Some(InitialKnown(genericParam.rune, x))
          case None => {
            // Make a placeholder for every argument even if it has a default, see DUDEWCD.
//            val runeType = vassertSome(function.runeToType.get(genericParam.rune.rune))
            val templata =
              templataCompiler.createPlaceholder(
                coutputs, callingEnv, callingEnv.fullName, genericParam, index, function.runeToType, false)
            Some(InitialKnown(genericParam.rune, templata))
          }
        }
      })

    // Now that we have placeholders, let's do the rest of the solve, so we can get a full prototype out of it.

    val CompleteCompilerSolve(_, inferences, runeToFunctionBound, reachableBounds) =
      inferCompiler.solveExpectComplete(
        InferEnv(callingEnv, callRange, nearEnv),
        coutputs,
        functionDefinitionRules,
        function.runeToType,
        function.range :: callRange,
        placeholderInitialKnownsFromFunction,
        Vector(),
        true,
        true,
        Vector())
    val runedEnv = addRunedDataToNearEnv(nearEnv, function.genericParameters.map(_.rune.rune), inferences, reachableBounds)

    val prototype =
      middleLayer.getGenericFunctionPrototypeFromCall(
        runedEnv, coutputs, callRange, function)

    // Usually when we call a function, we add instantiation bounds. However, we're
    // not calling a function here, we're defining it.
    EvaluateFunctionSuccess(PrototypeTemplata(function.range, prototype), inferences)
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  def evaluateGenericFunctionFromNonCall(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosureds,
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    verifyConclusions: Boolean):
  (FunctionHeaderT) = {
    val function = nearEnv.function
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)

    val functionTemplateFullName =
      nearEnv.parentEnv.fullName.addStep(
        nameTranslator.translateGenericFunctionName(nearEnv.function.name))

    val definitionRules =
      function.rules.filter(
        InferCompiler.includeRuleInDefinitionSolve)

    // This is so we can automatically grab the bounds from parameters and returns, see NBIFP.
    val paramRunes =
      function.params.flatMap(_.pattern.coordRune.map(_.rune)).distinct.toVector

    val envs = InferEnv(nearEnv, parentRanges, nearEnv)
    val (inferences, reachableBoundsFromParamsAndReturn) =
      incrementallySolve(
        envs, coutputs, function.range :: parentRanges, definitionRules, function.runeToType, paramRunes,
        (solver, incompleteSolve) => {
          getFirstUnsolvedIdentifyingRune(function.genericParameters, incompleteSolve.incompleteConclusions) match {
            case None => false
            case Some((genericParam, index)) => {
              // Make a placeholder for every argument even if it has a default, see DUDEWCD.
              val templata =
                templataCompiler.createPlaceholder(
                  coutputs, nearEnv, functionTemplateFullName, genericParam, index, function.runeToType, true)
              solver.manualStep(Map(genericParam.rune.rune -> templata))
              true
            }
          }
        }) match  {
        case f @ FailedCompilerSolve(_, _, err) => {
          throw CompileErrorExceptionT(typing.TypingPassSolverError(function.range :: parentRanges, f))
        }
        case IncompleteCompilerSolve(_, _, _, incompleteConclusions) => {
          (incompleteConclusions, Vector[PrototypeTemplata]())
        }
        case CompleteCompilerSolve(_, conclusions, _, reachableBoundsFromParamsAndReturn) => {
          (conclusions, reachableBoundsFromParamsAndReturn)
        }
      }

    val runedEnv =
      addRunedDataToNearEnv(
        nearEnv, function.genericParameters.map(_.rune.rune), inferences, reachableBoundsFromParamsAndReturn)

    val header =
      middleLayer.getOrEvaluateFunctionForHeader(
        nearEnv, runedEnv, coutputs, parentRanges, function)

    // We don't add these here because we aren't instantiating anything here, we're compiling a function
    // not calling it.
    // coutputs.addInstantiationBounds(header.toPrototype.fullName, runeToFunctionBound)

    header
  }

  private def getFirstUnsolvedIdentifyingRune(
    genericParameters: Vector[GenericParameterS],
    incompleteConclusions: Map[IRuneS, ITemplata[ITemplataType]]):
  Option[(GenericParameterS, Int)] = {
    genericParameters
      .zipWithIndex
      .map({ case (genericParam, index) =>
        (genericParam, index, incompleteConclusions.get(genericParam.rune.rune))
      })
      .filter(_._3.isEmpty)
      .map({ case (genericParam, index, None) => (genericParam, index) })
      .headOption
  }

  private def assembleInitialSendsFromArgs(callRange: RangeS, function: FunctionA, args: Vector[Option[CoordT]]):
  Vector[InitialSend] = {
    function.params.map(_.pattern.coordRune.get).zip(args).zipWithIndex
      .flatMap({
        case ((_, None), _) => None
        case ((paramRune, Some(argTemplata)), argIndex) => {
          Some(InitialSend(RuneUsage(callRange, ArgumentRuneS(argIndex)), paramRune, CoordTemplata(argTemplata)))
        }
      })
  }

  def incrementallySolve(
    envs: InferEnv,
    coutputs: CompilerOutputs,
    range: List[RangeS],
    rules: Vector[IRulexSR],
    runeToType: Map[IRuneS, ITemplataType],
    includeReachableBoundsForRunes: Vector[IRuneS],
    onIncompleteSolve: (Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplata[ITemplataType], ITypingPassSolverError], IncompleteCompilerSolve) => Boolean
  ): ICompilerSolverOutcome = {

    val solver =
      inferCompiler.makeSolver(
        envs,
        coutputs,
        rules,
        runeToType,
        range,
        Vector(),
        Vector())

    // See IRAGP for why we have this incremental solving/placeholdering.
    while ({
      inferCompiler.continue(envs, coutputs, solver) match {
        case Ok(()) =>
        case Err(f) => {
          throw CompileErrorExceptionT(typing.TypingPassSolverError(range, f))
        }
      }

      inferCompiler.interpretResults(
        envs,
        coutputs,
        range,
        runeToType,
        rules,
        true,
        true,
        // This is so we can automatically grab the bounds from parameters, see NBIFP.
        includeReachableBoundsForRunes,
        solver) match {
        case f @ FailedCompilerSolve(_, _, err) => {
          throw CompileErrorExceptionT(typing.TypingPassSolverError(range, f))
        }
        case i @ IncompleteCompilerSolve(_, _, _, incompleteConclusions) => {
          val continue = onIncompleteSolve(solver, i)
          if (!continue) {
            return i
          }
          true
        }
        case c @ CompleteCompilerSolve(_, conclusions, _, reachableBoundsFromParamsAndReturn) => {
          return c
        }
      }
    }) {}

    vfail() // Shouldnt get here
  }
}
