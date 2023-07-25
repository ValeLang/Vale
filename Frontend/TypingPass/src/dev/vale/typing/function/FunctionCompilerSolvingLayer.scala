package dev.vale.typing.function

import dev.vale.{Err, Interner, Keywords, Ok, Profiler, RangeS, StrI, typing, vassert, vassertSome, vcurious, vfail, vimpl, vpass, vregionmut}
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.rules.{IRulexSR, RuneUsage}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.postparsing.patterns._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.postparsing.{IEnvironmentS => _, _}
import dev.vale.typing.OverloadResolver._
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import dev.vale.typing.function._
import dev.vale.solver.{CompleteSolve, FailedSolve, IncompleteSolve, Solver}
import dev.vale.typing.ast.{FunctionBannerT, FunctionHeaderT, PrototypeT}
import dev.vale.typing.env._
import dev.vale.typing.infer.ITypingPassSolverError
import dev.vale.typing.{CompilerOutputs, ConvertHelper, InferCompiler, InitialKnown, InitialSend, TemplataCompiler, TypingPassOptions}
import dev.vale.typing.names._
import dev.vale.typing.templata.ITemplataT._
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
    outerEnv: BuildingFunctionEnvironmentWithClosuredsT,
    coutputs: CompilerOutputs,
    originalCallingEnv: IInDenizenEnvironmentT, // See CSSNCE
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    explicitTemplateArgs: Vector[ITemplataT[ITemplataType]],
    contextRegion: RegionT,
    args: Vector[CoordT]):
  (IEvaluateFunctionResult) = {
    val function = outerEnv.function
    // Check preconditions
    checkClosureConcernsHandled(outerEnv)

    val callSiteRules =
        TemplataCompiler.assembleCallSiteRules(
            function.rules, function.genericParameters, explicitTemplateArgs.size)

    val initialSends = assembleInitialSendsFromArgs(callRange.head, function, args.map(Some(_)))
    val CompleteDefineSolve(inferredTemplatas, runeToFunctionBound, declaredBounds, reachableBounds) =
    // We could probably just solveForResolving (see DBDAR) but seems more future-proof to solveForDefining.
      inferCompiler.solveForDefining(
        InferEnv(originalCallingEnv, callRange, callLocation, outerEnv, contextRegion),
        coutputs,
        callSiteRules,
        function.runeToType,
        callRange,
        callLocation,
        assembleKnownTemplatas(function, explicitTemplateArgs),
        initialSends,
        Vector()
      ) match {
        case Err(e) => return (EvaluateFunctionFailure(e))
        case Ok(i) => (i)
      }

    vimpl() // declaredBounds

    val runedEnv =
      addRunedDataToNearEnv(
        outerEnv,
        function.genericParameters.map(_.rune.rune),
        inferredTemplatas,
        reachableBounds)

    val header =
      middleLayer.getOrEvaluateFunctionForHeader(
        outerEnv, runedEnv, coutputs, callRange, callLocation, function)

    coutputs.addInstantiationBounds(header.toPrototype.id, runeToFunctionBound)
    EvaluateFunctionSuccess(PrototypeTemplataT(function.range, header.toPrototype), inferredTemplatas)
  }

  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  // - env is the environment the templated function was made in
  def evaluateTemplatedFunctionFromCallForBanner(
      // The environment the function was defined in.
      declaringEnv: BuildingFunctionEnvironmentWithClosuredsT,
      coutputs: CompilerOutputs,
      originalCallingEnv: IInDenizenEnvironmentT, // See CSSNCE
      callRange: List[RangeS],
      callLocation: LocationInDenizen,
      alreadySpecifiedTemplateArgs: Vector[ITemplataT[ITemplataType]],
    contextRegion: RegionT,
      args: Vector[CoordT]):
  (IEvaluateFunctionResult) = {
    val function = declaringEnv.function
    // Check preconditions
    checkClosureConcernsHandled(declaringEnv)

    val callSiteRules =
        TemplataCompiler.assembleCallSiteRules(
            function.rules, function.genericParameters, 0)

    val initialSends = assembleInitialSendsFromArgs(callRange.head, function, args.map(Some(_)))
    val CompleteDefineSolve(inferredTemplatas, runeToFunctionBound, Vector(), reachableBounds) = {
      // We could probably just solveForResolving (see DBDAR) but seems more future-proof to solveForDefining.
      inferCompiler.solveForDefining(
        InferEnv(originalCallingEnv, callRange, callLocation, declaringEnv, contextRegion),
        coutputs,
        callSiteRules,
        function.runeToType,
        callRange,
        callLocation,
        assembleKnownTemplatas(function, alreadySpecifiedTemplateArgs),
        initialSends,
        Vector()
      ) match {
        case Err(e) => return EvaluateFunctionFailure(e)
        case Ok(i) => (i)
      }
    }

    val runedEnv =
      addRunedDataToNearEnv(
        declaringEnv,
        function.genericParameters.map(_.rune.rune),
        inferredTemplatas,
        reachableBounds)

    val prototype =
      middleLayer.getOrEvaluateTemplatedFunctionForBanner(
        declaringEnv, runedEnv, coutputs, callRange, callLocation, function)

    coutputs.addInstantiationBounds(prototype.prototype.id, runeToFunctionBound)

    EvaluateFunctionSuccess(prototype, inferredTemplatas)
  }

  // This is called while we're trying to figure out what functionSs to call when there
  // are a lot of overloads available.
  // This assumes it met any type bound restrictions (or, will; not implemented yet)
  def evaluateTemplatedLightBannerFromCall(
      // The environment the function was defined in.
      nearEnv: BuildingFunctionEnvironmentWithClosuredsT,
      coutputs: CompilerOutputs,
    originalCallingEnv: IInDenizenEnvironmentT, // See CSSNCE
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
      explicitTemplateArgs: Vector[ITemplataT[ITemplataType]],
    contextRegion: RegionT,
      args: Vector[CoordT]):
  (IEvaluateFunctionResult) = {
    val function = nearEnv.function
    // Check preconditions
    function.body match {
      case CodeBodyS(body1) => vassert(body1.closuredNames.isEmpty)
      case _ =>
    }

    val callSiteRules =
      TemplataCompiler.assembleCallSiteRules(
        function.rules, function.genericParameters, explicitTemplateArgs.size)

    val initialSends = assembleInitialSendsFromArgs(callRange.head, function, args.map(Some(_)))
    val initialKnowns = assembleKnownTemplatas(function, explicitTemplateArgs)
    val CompleteDefineSolve(inferences, runeToFunctionBound, Vector(), reachableBounds) =
    // We could probably just solveForResolving (see DBDAR) but seems more future-proof to solveForDefining.
      inferCompiler.solveForDefining(
        InferEnv(originalCallingEnv, callRange, callLocation, nearEnv, contextRegion),
        coutputs,
        callSiteRules,
        function.runeToType,
        callRange,
        callLocation,
        initialKnowns,
        initialSends,
        Vector()) match {
      case Err(e) => return EvaluateFunctionFailure(e)
      case Ok(inferredTemplatas) => inferredTemplatas
    }

    // See FunctionCompiler doc for what outer/runes/inner envs are.
    val runedEnv =
      addRunedDataToNearEnv(
        nearEnv,
        function.genericParameters.map(_.rune.rune),
        inferences,
        reachableBounds)

    val prototypeTemplata =
      middleLayer.getOrEvaluateTemplatedFunctionForBanner(
        nearEnv, runedEnv, coutputs, callRange, callLocation, function)

    coutputs.addInstantiationBounds(prototypeTemplata.prototype.id, runeToFunctionBound)
    EvaluateFunctionSuccess(prototypeTemplata, inferences)
  }

  private def assembleKnownTemplatas(
    function: FunctionA,
    explicitTemplateArgs: Vector[ITemplataT[ITemplataType]]):
  Vector[InitialKnown] = {
    function.genericParameters.zip(explicitTemplateArgs).map({
      case (genericParam, explicitArg) => {
        InitialKnown(genericParam.rune, explicitArg)
      }
    })
  }

  private def checkClosureConcernsHandled(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosuredsT
  ): Unit = {
    val function = nearEnv.function
    function.body match {
      case CodeBodyS(body1) => {
        body1.closuredNames.foreach(name => {
          vassert(nearEnv.variables.exists(_.name == nameTranslator.translateNameStep(name)))
        })
      }
      case _ =>
    }
  }

  // IOW, add the necessary data to turn the near env into the runed env.
  private def addRunedDataToNearEnv(
    nearEnv: BuildingFunctionEnvironmentWithClosuredsT,
    identifyingRunes: Vector[IRuneS],
    templatasByRune: Map[IRuneS, ITemplataT[ITemplataType]],
    reachableBoundsFromParamsAndReturn: Vector[PrototypeTemplataT]
    // I suspect we'll eventually need some impl bounds here
  ): BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT = {
    val BuildingFunctionEnvironmentWithClosuredsT(globalEnv, parentEnv, id, templatas, function, variables, isRootCompilingDenizen) = nearEnv

    val identifyingTemplatas = identifyingRunes.map(templatasByRune)

    val newEntries =
      templatas.addEntries(
        interner,
        reachableBoundsFromParamsAndReturn.zipWithIndex.toVector
          .map({ case (t, i) => (interner.intern(ReachablePrototypeNameT(i)), TemplataEnvEntry(t)) }) ++
        templatasByRune.toVector
          .map({ case (k, v) => (interner.intern(RuneNameT(k)), TemplataEnvEntry(v)) }))

    val defaultRegion = RegionT()

    BuildingFunctionEnvironmentWithClosuredsAndTemplateArgsT(
      globalEnv,
      parentEnv,
      id,
      identifyingTemplatas,
      newEntries,
      function,
      variables,
      isRootCompilingDenizen,
      defaultRegion)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // func main():Int{main()}
  // Preconditions:
  // - either no closured vars, or they were already added to the env.
  // - env is the environment the templated function was made in
  def evaluateGenericFunctionFromCallForPrototype(
    // The environment the function was defined in.
    outerEnv: BuildingFunctionEnvironmentWithClosuredsT,
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT, // See CSSNCE
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    explicitTemplateArgs: Vector[ITemplataT[ITemplataType]],
    contextRegion: RegionT,
    args: Vector[Option[CoordT]]):
  (IResolveFunctionResult) = {
    val function = outerEnv.function
    // Check preconditions
    checkClosureConcernsHandled(outerEnv)

    val callSiteRules =
        TemplataCompiler.assembleCallSiteRules(
            function.rules, function.genericParameters, explicitTemplateArgs.size)

    val initialSends = assembleInitialSendsFromArgs(callRange.head, function, args)


    val envs = InferEnv(callingEnv, callRange, callLocation, outerEnv, contextRegion)
    val rules = callSiteRules
    val runeToType = function.runeToType
    val invocationRange = callRange
    val initialKnowns = assembleKnownTemplatas(function, explicitTemplateArgs)
    val includeReachableBoundsForRunes = Vector()

    val solver =
      inferCompiler.makeSolver(envs, coutputs, rules, runeToType, invocationRange, initialKnowns, initialSends)

    var loopCheck = function.genericParameters.size + 1

    // Incrementally solve and add default generic parameters (and context region).
    inferCompiler.incrementallySolve(
      envs, coutputs, solver,
      (solver) => {
        if (loopCheck == 0) {
          throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Infinite loop detected in incremental call solve!"))
        }
        loopCheck = loopCheck - 1

        TemplataCompiler.getFirstUnsolvedIdentifyingRune(
          function.genericParameters,
          (rune) => solver.getConclusion(rune).nonEmpty) match {
          case None => false
          case Some((genericParam, index)) => {
            // This unsolved rune better be one we didn't explicitly hand in already.
            vassert(index >= explicitTemplateArgs.size)

            genericParam.default match {
              case Some(defaultRules) => {
                solver.addRules(defaultRules.rules)
                true
              }
              case None => {
                // There are no defaults for this.
                false
              }
            }
          }
        }
      }) match {
      case Err(f@FailedCompilerSolve(_, _, _)) => {
        return (ResolveFunctionFailure(ResolvingSolveFailedOrIncomplete(f)))
      }
      case Ok(true) =>
      case Ok(false) => // Incomplete, will be detected as IncompleteCompilerSolve below.
    }

    val CompleteResolveSolve(_, inferredTemplatas, runeToFunctionBound, Vector(), reachableBounds) =
      inferCompiler.checkResolvingConclusionsAndResolve(envs, coutputs, invocationRange, callLocation, runeToType, rules, includeReachableBoundsForRunes, solver) match {
        case Err(e) => return (ResolveFunctionFailure(e))
        case Ok(i) => (i)
      }

    val runedEnv =
      addRunedDataToNearEnv(
        outerEnv,
        function.genericParameters.map(_.rune.rune),
        inferredTemplatas,
        reachableBounds)

    val prototype =
      middleLayer.getGenericFunctionPrototypeFromCall(
        runedEnv, coutputs, callRange, function)

    coutputs.addInstantiationBounds(prototype.id, runeToFunctionBound)

    ResolveFunctionSuccess(PrototypeTemplataT(function.range, prototype), inferredTemplatas)
  }

  def evaluateGenericVirtualDispatcherFunctionForPrototype(
    // The environment the function was defined in.
    nearEnv: BuildingFunctionEnvironmentWithClosuredsT,
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT, // See CSSNCE
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
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
    val preliminaryEnvs = InferEnv(callingEnv, callRange, callLocation, nearEnv, RegionT())
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
    // Skip checking that the conclusions are all there, because we don't assume that they will all be there. We expect
    // an incomplete solve.
    val preliminaryInferences = preliminarySolver.userifyConclusions().toMap
    // Now we can use preliminaryInferences to know whether or not we need a placeholder for an
    // identifying rune.
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
            vimpl()
            val placeholderPureHeight = vregionmut(None)
            val templata =
              templataCompiler.createPlaceholder(
                coutputs, callingEnv, callingEnv.id, genericParam, index, function.runeToType, placeholderPureHeight, false)
            Some(InitialKnown(genericParam.rune, templata))
          }
        }
      })

    // Now that we have placeholders, let's do the rest of the solve, so we can get a full
    // prototype out of it.

    val CompleteDefineSolve(inferences, runeToFunctionBound, declaredBounds, reachableBounds) =
      inferCompiler.solveForDefining(
        InferEnv(callingEnv, callRange, callLocation, nearEnv, RegionT()),
        coutputs,
        functionDefinitionRules,
        function.runeToType,
        function.range :: callRange,
        callLocation,
        placeholderInitialKnownsFromFunction,
        Vector(),
        Vector()) match {
        case Err(f) => throw CompileErrorExceptionT(TypingPassDefiningError(function.range :: callRange, f))
        case Ok(c) => c
      }
    val runedEnv =
      addRunedDataToNearEnv(
        nearEnv,
        function.genericParameters.map(_.rune.rune),
        inferences,
        reachableBounds)

    val prototype =
      middleLayer.getGenericFunctionPrototypeFromCall(
        runedEnv, coutputs, callRange, function)

    // Usually when we call a function, we add instantiation bounds. However, we're
    // not calling a function here, we're defining it.
    EvaluateFunctionSuccess(PrototypeTemplataT(function.range, prototype), inferences)
  }

  def precompileGenericFunction(
      coutputs: CompilerOutputs,
      nearEnv: BuildingFunctionEnvironmentWithClosuredsT,
      parentRanges: List[RangeS],
      callLocation: LocationInDenizen
  ): Unit = {
    val function = nearEnv.function
    val range = function.range :: parentRanges
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)

    val functionTemplateId =
      nearEnv.parentEnv.id.addStep(
        nameTranslator.translateGenericFunctionName(nearEnv.function.name))

    val definitionRules = function.rules.filter(InferCompiler.includeRuleInDefinitionSolve)

    // This is so we can automatically grab the bounds from parameters and returns, see NBIFP.
    val paramRunes =
      function.params.flatMap(_.pattern.coordRune.map(_.rune)).distinct.toVector

    val envs = InferEnv(nearEnv, parentRanges, callLocation, nearEnv, RegionT())
    val solver =
      inferCompiler.makeSolver(
        envs, coutputs, definitionRules, function.runeToType, range, Vector(), Vector())
    // Incrementally solve and add placeholders, see IRAGP.
    inferCompiler.incrementallySolve(
      envs, coutputs, solver,
      // Each step happens after the solver has done all it possibly can. Sometimes this can lead
      // to races, see RRBFS.
      (solver) => {
        TemplataCompiler.getFirstUnsolvedIdentifyingRune(function.genericParameters, (rune) => solver.getConclusion(rune).nonEmpty) match {
          case None => false
          case Some((genericParam, index)) => {
            // Make a placeholder for every argument even if it has a default, see DUDEWCD.
            val placeholderPureHeight = vregionmut(None)
            val templata =
              templataCompiler.createPlaceholder(
                coutputs, nearEnv, functionTemplateId, genericParam, index, function.runeToType, placeholderPureHeight, true)
            solver.manualStep(Map(genericParam.rune.rune -> templata))
            true
          }
        }
      }) match {
      case Err(f@FailedCompilerSolve(_, _, err)) => {
        throw CompileErrorExceptionT(typing.TypingPassSolverError(function.range :: parentRanges, f))
      }
      case Ok(true) =>
      case Ok(false) => // Incomplete, will be detected in the below checkDefiningConclusionsAndResolve
    }
    val inferences =
      inferCompiler.interpretResults(function.runeToType, solver) match {
        case Ok(i) => i
        case Err(e) => throw CompileErrorExceptionT(TypingPassDefiningError(range, DefiningSolveFailedOrIncomplete(e)))
      }
    coutputs.declareFunctionInferences(functionTemplateId, inferences) // DO NOT SUBMIT

    // val CompleteDefineSolve(_, _, declaredBounds, reachableBoundsFromParamsAndReturn) =
    //   inferCompiler.checkDefiningConclusionsAndResolve(
    //     envs.originalCallingEnv, coutputs, range, callLocation, envs.contextRegion, definitionRules, paramRunes, inferences) match {
    //     case Err(f) => throw CompileErrorExceptionT(TypingPassDefiningError(range, DefiningResolveConclusionError(f)))
    //     case Ok(c) => c
    // //   }
    //
    // declaredBounds.foreach(bound => {
    //   val PrototypeTemplataT(range, prototype) = bound
    //   // Add it to the overload index
    //   TemplatasStore.getImpreciseName(interner, prototype.id.localName) match {
    //     case None => {
    //       // DO NOT SUBMIT
    //       println("Skipping adding function " + prototype.id.localName + " to overload index")
    //     }
    //     case Some(impreciseName) => {
    //       coutputs.addOverload(
    //         opts.globalOptions.useOverloadIndex,
    //         impreciseName,
    //         prototype.id.localName.parameters.map(x => Some(x)),
    //         PrototypeTemplataCalleeCandidate(range, prototype))
    //     }
    //   }
    // })
    //
    // val runedEnv =
    //   addRunedDataToNearEnv(
    //     nearEnv, function.genericParameters.map(_.rune.rune), inferences, reachableBoundsFromParamsAndReturn)
    //
    // val header =
    //   middleLayer.getOrEvaluateFunctionForHeader(
    //     nearEnv, runedEnv, coutputs, parentRanges, callLocation, function)

    // We don't add these here because we aren't instantiating anything here, we're compiling a
    // function
    // not calling it.
    // coutputs.addInstantiationBounds(header.toPrototype.id, runeToFunctionBound)


    // DO NOT SUBMIT unify with middleLayer.evaluateFunctionParamTypes
    val paramTypes =
      function.params.map(param1 => {
        expectCoordTemplata(inferences(param1.pattern.coordRune.get.rune)).coord
      })
    // val templateArgs = function.genericParameters.map(_.rune.rune).map(inferences)
    // // DO NOT SUBMIT unify with middleLayer.assemblename
    // val functionId =
    //   functionTemplateId.copy(
    //     localName = functionTemplateId.localName.makeFunctionName(interner, keywords, templateArgs, paramTypes))

    // Add it to the overload index
    TemplatasStore.getImpreciseName(interner, nearEnv.id.localName) match {
      case None => {
        // DO NOT SUBMIT
        println("Skipping adding function " + nearEnv.id.localName + " to overload index")
      }
      case Some(impreciseName) => {
        coutputs.addOverload(
          opts.globalOptions.useOverloadIndex,
          impreciseName,
          paramTypes.map({
            case CoordT(_, _, KindPlaceholderT(_)) => None // DO NOT SUBMIT document
            case other => Some(other)
          }),
          FunctionCalleeCandidate(nearEnv.templata))
      }
    }


    // header
  }

  def compileGenericFunction(
    coutputs: CompilerOutputs,
    nearEnv: BuildingFunctionEnvironmentWithClosuredsT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen
  ): FunctionHeaderT = {
    val function = nearEnv.function
    val range = function.range :: parentRanges
    // Check preconditions
    checkClosureConcernsHandled(nearEnv)

    val functionTemplateId =
      nearEnv.parentEnv.id.addStep(
        nameTranslator.translateGenericFunctionName(nearEnv.function.name))

    val definitionRules = function.rules.filter(InferCompiler.includeRuleInDefinitionSolve)

    // This is so we can automatically grab the bounds from parameters and returns, see NBIFP.
    val paramRunes =
      function.params.flatMap(_.pattern.coordRune.map(_.rune)).distinct.toVector

    val envs = InferEnv(nearEnv, parentRanges, callLocation, nearEnv, RegionT())
    val inferences = coutputs.getInferencesForFunction(functionTemplateId)
    val CompleteDefineSolve(_, _, declaredBounds, reachableBoundsFromParamsAndReturn) =
      inferCompiler.checkDefiningConclusionsAndResolve(
        envs.originalCallingEnv, coutputs, range, callLocation, envs.contextRegion, definitionRules, paramRunes, inferences) match {
        case Err(f) => throw CompileErrorExceptionT(TypingPassDefiningError(range, DefiningResolveConclusionError(f)))
        case Ok(c) => c
      }

    declaredBounds.foreach(bound => {
      val PrototypeTemplataT(range, prototype) = bound
      // Add it to the overload index
      TemplatasStore.getImpreciseName(interner, prototype.id.localName) match {
        case None => {
          // DO NOT SUBMIT
          println("Skipping adding function " + prototype.id.localName + " to overload index")
        }
        case Some(impreciseName) => {
          coutputs.addOverload(
            opts.globalOptions.useOverloadIndex,
            impreciseName,
            prototype.id.localName.parameters.map(x => Some(x)),
            PrototypeTemplataCalleeCandidate(range, prototype))
        }
      }
    })

    val runedEnv =
      addRunedDataToNearEnv(
        nearEnv, function.genericParameters.map(_.rune.rune), inferences, reachableBoundsFromParamsAndReturn)

    val header =
      middleLayer.getOrEvaluateFunctionForHeader(
        nearEnv, runedEnv, coutputs, parentRanges, callLocation, function)

    // We don't add these here because we aren't instantiating anything here, we're compiling a
    // function
    // not calling it.
    // coutputs.addInstantiationBounds(header.toPrototype.id, runeToFunctionBound)

    header
  }

  private def assembleInitialSendsFromArgs(callRange: RangeS, function: FunctionA, args: Vector[Option[CoordT]]):
  Vector[InitialSend] = {
    function.params.map(_.pattern.coordRune.get).zip(args).zipWithIndex
      .flatMap({
        case ((_, None), _) => None
        case ((paramRune, Some(argTemplata)), argIndex) => {
          Some(InitialSend(RuneUsage(callRange, ArgumentRuneS(argIndex)), paramRune, CoordTemplataT(argTemplata)))
        }
      })
  }
}
