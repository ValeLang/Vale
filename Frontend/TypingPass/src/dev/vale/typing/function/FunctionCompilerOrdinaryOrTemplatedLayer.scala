package dev.vale.typing.function

import dev.vale.{Err, Interner, Keywords, Ok, Profiler, RangeS, StrI, typing, vassert, vassertSome, vcurious, vimpl, vpass}
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
import dev.vale.solver.{CompleteSolve, FailedSolve, IncompleteSolve}
import dev.vale.typing.ast.{FunctionBannerT, FunctionHeaderT, PrototypeT}
import dev.vale.typing.env.{BuildingFunctionEnvironmentWithClosureds, BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs, TemplataEnvEntry, TemplataLookupContext}
import dev.vale.typing.{CompilerOutputs, ConvertHelper, InferCompiler, InitialKnown, InitialSend, TemplataCompiler, TypingPassOptions}
import dev.vale.typing.names.{FullNameT, FunctionNameT, FunctionTemplateNameT, NameTranslator, PlaceholderNameT, PlaceholderTemplateNameT, ReachablePrototypeNameT, RuneNameT, StructNameT, StructTemplateNameT}
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
class FunctionCompilerOrdinaryOrTemplatedLayer(
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

//  // This is for the early stages of Compiler when it's scanning banners to put in
//  // its env. We just want its banner, we don't want to evaluate it.
//  def predictOrdinaryFunctionBanner(
//    // The environment the function was defined in.
//    nearEnv: BuildingFunctionEnvironmentWithClosureds,
//    coutputs: CompilerOutputs):
//  (FunctionBannerT) = {
//    val function = nearEnv.function
//    checkClosureConcernsHandled(nearEnv)
//
//    val inferences =
//      inferCompiler.solveExpectComplete(
//        nearEnv, coutputs, vimpl()/*function.rules*/, function.runeToType, function.range, Vector(), Vector())
//    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)
//
//    middleLayer.predictOrdinaryFunctionBanner(
//      runedEnv, coutputs, function)
//  }

//  def evaluateOrdinaryFunctionFromNonCallForBanner(
//      // The environment the function was defined in.
//      nearEnv: BuildingFunctionEnvironmentWithClosureds,
//      coutputs: CompilerOutputs,
//      callRange: List[RangeS],
//      verifyConclusions: Boolean):
//  (PrototypeTemplata) = {
//    val function = nearEnv.function
//    checkClosureConcernsHandled(nearEnv)
//    vassert(!function.isTemplate)
//
//    val definitionRules =
//      function.rules.filter(InferCompiler.includeRuleInDefinitionSolve)
//
//    val inferences =
//      inferCompiler.solveExpectComplete(
//        InferEnv(nearEnv, callRange, nearEnv),
//        coutputs, definitionRules, function.runeToType, function.range :: callRange, Vector(), Vector(), true, true)
//    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)
//
//    coutputs.declareFunctionInnerEnv(nearEnv.fullName, runedEnv)
//
//    middleLayer.getOrEvaluateFunctionForBanner(runedEnv, coutputs, callRange, function)
//  }

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

//  // Preconditions:
//  // - either no closured vars, or they were already added to the env.
//  def evaluateOrdinaryFunctionFromNonCallForHeader(
//      // The environment the function was defined in.
//      declaringEnv: BuildingFunctionEnvironmentWithClosureds,
//      coutputs: CompilerOutputs,
//    parentRanges: List[RangeS],
//      verifyConclusions: Boolean):
//  (FunctionHeaderT) = {
//    val function = declaringEnv.function
//    // Check preconditions
//    checkClosureConcernsHandled(declaringEnv)
//    vassert(!function.isTemplate)
//
//    val definitionRules =
//      function.rules.filter(
//        InferCompiler.includeRuleInDefinitionSolve)
//
//    val inferences =
//      inferCompiler.solveExpectComplete(
//        InferEnv(declaringEnv, parentRanges, declaringEnv),
//        coutputs, definitionRules, function.runeToType, function.range :: parentRanges, Vector(), Vector(), true, true)
//    val runedEnv = addRunedDataToNearEnv(declaringEnv, Vector.empty, inferences)
//
//    coutputs.declareFunctionInnerEnv(declaringEnv.fullName, runedEnv)
//
//    middleLayer.getOrEvaluateFunctionForHeader(
//      runedEnv, coutputs, function.range :: parentRanges, function)
//  }
//
//  // Preconditions:
//  // - either no closured vars, or they were already added to the env.
//  def evaluateOrdinaryFunctionFromCallForPrototype(
//    // The environment the function was defined in.
//    nearEnv: BuildingFunctionEnvironmentWithClosureds,
//    originalCallingEnv: IEnvironment, // See CSSNCE
//    coutputs: CompilerOutputs,
//    parentRanges: List[RangeS]):
//  (PrototypeTemplata) = {
//    val function = nearEnv.function
//    // Check preconditions
//    checkClosureConcernsHandled(nearEnv)
//    vassert(!function.isTemplate)
//
//    val callSiteRules =
//        TemplataCompiler.assembleCallSiteRules(
//            function.rules, function.genericParameters, 0)
//
//    val inferences =
//      inferCompiler.solveExpectComplete(
//        InferEnv(originalCallingEnv, nearEnv),
//        coutputs, callSiteRules, function.runeToType, function.range :: parentRanges, Vector(), Vector(), true, false)
//    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)
//
//    coutputs.declareFunctionInnerEnv(nearEnv.fullName, runedEnv)
//
//    middleLayer.getOrEvaluateOrdinaryFunctionForPrototype(
//      runedEnv, coutputs, function.range :: parentRanges, function)
//  }

//  // We would want only the prototype instead of the entire header if, for example,
//  // we were calling the function. This is necessary for a recursive function like
//  // func main():Int{main()}
//  def evaluateOrdinaryFunctionFromCallForPrototype(
//    // The environment the function was defined in.
//    nearEnv: BuildingFunctionEnvironmentWithClosureds,
//    originalCallingEnv: IEnvironment, // See CSSNCE
//    coutputs: CompilerOutputs,
//    callRange: List[RangeS]):
//  (PrototypeTemplata) = {
//    val function = nearEnv.function
//    // Check preconditions
//    checkClosureConcernsHandled(nearEnv)
//    vassert(!function.isTemplate)
//
//    val callSiteRules =
//      TemplataCompiler.assembleCallSiteRules(
//        function.rules, function.genericParameters, 0)
//
//    val inferences =
//      inferCompiler.solveExpectComplete(
//        InferEnv(originalCallingEnv, callRange, nearEnv),
//        coutputs, callSiteRules, function.runeToType, function.range :: callRange, Vector(), Vector(), true, false)
//    val runedEnv = addRunedDataToNearEnv(nearEnv, Vector.empty, inferences)
//
//    coutputs.declareFunction(runedEnv.fullName)
//    coutputs.declareFunctionOuterEnv(runedEnv.fullName, runedEnv)
//
//    middleLayer.getOrEvaluateOrdinaryFunctionForPrototype(
//      runedEnv, coutputs, callRange, function)
//  }
//
//  // Preconditions:
//  // - either no closured vars, or they were already added to the env.
//  def evaluateTemplatedFunctionFromNonCallForHeader(
//    // The environment the function was defined in.
//    nearEnv: BuildingFunctionEnvironmentWithClosureds,
//    coutputs: CompilerOutputs,
//    parentRanges: List[RangeS],
//    verifyConclusions: Boolean):
//  (FunctionHeaderT) = {
//    val function = nearEnv.function
//    // Check preconditions
//    checkClosureConcernsHandled(nearEnv)
//
//    // Check preconditions
//    function.body match {
//      case CodeBodyS(body1) => vassert(body1.closuredNames.isEmpty)
//      case _ =>
//    }
////    vassert(nearEnv.function.isTemplate)
//
//    // See IMCBT for why we can look up identifying runes in the environment.
//    val initialKnowns =
//      function.genericParameters.flatMap(genericParam => {
//        nearEnv.lookupNearestWithName(
//          interner.intern(RuneNameT(genericParam.rune.rune)), Set(TemplataLookupContext))
//          .map(InitialKnown(genericParam.rune, _))
//      })
//
//    val definitionRules =
//      function.rules.filter(
//        InferCompiler.includeRuleInDefinitionSolve)
//
//    val CompleteCompilerSolve(_, inferences, runeToFunctionBound) =
//      inferCompiler.solveExpectComplete(
//        InferEnv(nearEnv, parentRanges, nearEnv),
//        coutputs, definitionRules, function.runeToType, function.range :: parentRanges, initialKnowns, Vector(), true, true, true)
//
//    // See FunctionCompiler doc for what outer/runes/inner envs are.
//    val runedEnv =
//      addRunedDataToNearEnv(
//        nearEnv, function.genericParameters.map(_.rune.rune), inferences)
//
//    coutputs.addInstantiationBounds(header.toPrototype.fullName, runeToFunctionBound)
//    middleLayer.getOrEvaluateFunctionForHeader(
//      runedEnv, coutputs, function.range :: parentRanges, function)
//  }


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
    // Sometimes we look for an overload for a given override, assemble knowns from that here
//    args.zip(function.params).collect({
//      case (ParamFilter(_, Some(OverrideT(argOverrideKind))), ParameterS(AtomSP(_, _, Some(OverrideSP(_, paramOverrideRune)), _, _))) => {
//        InitialKnown(paramOverrideRune, KindTemplata(argOverrideKind))
//      }
//    }) ++
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
    val preliminaryInferences =
      inferCompiler.solve(
          InferEnv(callingEnv, callRange, nearEnv),
          coutputs,
          functionDefinitionRules,
          function.runeToType,
          function.range :: callRange,
          Vector(),
          initialSends,
        false,
          true,
        Vector()) match {
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

    // This is temporary, to support specialization like:
    //   extern("vale_runtime_sized_array_mut_new")
    //   func Vector<M, E>(size int) []<M>E
    //   where M Mutability = mut, E Ref;
    // In the future we might need to outlaw specialization, unsure.
    val (preliminaryInferences, preliminaryReachableBoundsFromParamsAndReturn) =
      inferCompiler.solve(
        InferEnv(nearEnv, parentRanges, nearEnv),
        coutputs,
        definitionRules,
        function.runeToType,
        function.range :: parentRanges,
        Vector(),
        Vector(),
        true,
        true,
        // This is so we can automatically grab the bounds from parameters, see NBIFP.
        paramRunes) match {
        case f @ FailedCompilerSolve(_, _, err) => {
          throw CompileErrorExceptionT(typing.TypingPassSolverError(function.range :: parentRanges, f))
        }
        case IncompleteCompilerSolve(_, _, _, incompleteConclusions) => (incompleteConclusions, Vector[ITemplata[ITemplataType]]())
        case CompleteCompilerSolve(_, conclusions, _, reachableBoundsFromParamsAndReturn) => (conclusions, reachableBoundsFromParamsAndReturn)
      }
    // Now we can use preliminaryInferences to know whether or not we need a placeholder for an identifying rune.

    val initialKnowns =
      function.genericParameters.zipWithIndex.flatMap({ case (genericParam, index) =>
        preliminaryInferences.get(genericParam.rune.rune) match {
          case Some(x) => Some(InitialKnown(genericParam.rune, x))
          case None => {
            // Make a placeholder for every argument even if it has a default, see DUDEWCD.
            val templata =
              templataCompiler.createPlaceholder(
                coutputs, nearEnv, functionTemplateFullName, genericParam, index, function.runeToType, true)
            Some(InitialKnown(genericParam.rune, templata))
          }
        }
      })

    val CompleteCompilerSolve(_, inferences, runeToFunctionBound, reachableBoundsFromParamsAndReturn) =
      inferCompiler.solveExpectComplete(
        InferEnv(nearEnv, parentRanges, nearEnv),
        coutputs,
        definitionRules,
        function.runeToType,
        function.range :: parentRanges,
        initialKnowns,
        Vector(),
        true,
        true,
        // This is so we can automatically grab the bounds from parameters, see NBIFP.
        paramRunes)
    val runedEnv = addRunedDataToNearEnv(nearEnv, function.genericParameters.map(_.rune.rune), inferences, reachableBoundsFromParamsAndReturn)

    val header =
      middleLayer.getOrEvaluateFunctionForHeader(
        nearEnv, runedEnv, coutputs, parentRanges, function)

    // We don't add these here because we aren't instantiating anything here, we're compiling a function
    // not calling it.
    // coutputs.addInstantiationBounds(header.toPrototype.fullName, runeToFunctionBound)

    header.fullName match {
      case FullNameT(_,Vector(),FunctionNameT(FunctionTemplateNameT(StrI("keys"),_),Vector(_),Vector(CoordT(BorrowT,StructTT(FullNameT(_,Vector(),StructNameT(StructTemplateNameT(StrI("HashMap")),Vector(_)))))))) => {
        vpass()
      }
      case _ =>
    }

    header
  }
}
