package dev.vale.typing

import dev.vale.highertyping.FunctionA
import dev.vale._
import dev.vale.postparsing._
import dev.vale.postparsing.rules._
import dev.vale.solver._
import dev.vale.postparsing._
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.ast.{PrototypeT, PrototypeTemplataCalleeCandidate}
import dev.vale.typing.citizen.{IResolveOutcome, IsParent, IsParentResult, IsntParent, ResolveFailure, ResolveSuccess}
import dev.vale.typing.env.{CitizenEnvironmentT, EnvironmentHelper, GeneralEnvironmentT, GlobalEnvironment, IEnvEntry, IEnvironmentT, IInDenizenEnvironmentT, ILookupContext, IVariableT, TemplataEnvEntry, TemplataLookupContext, TemplatasStore}
import dev.vale.typing.function._
import dev.vale.typing.infer.{CompilerSolver, CouldntFindFunction, CouldntFindImpl, CouldntResolveKind, IInfererDelegate, ITypingPassSolverError, ReturnTypeConflict}
import dev.vale.typing.names.{BuildingFunctionNameWithClosuredsT, IImplNameT, INameT, ITemplateNameT, IdT, ImplNameT, NameTranslator, ReachablePrototypeNameT, ResolvingEnvNameT, RuneNameT}
import dev.vale.typing.templata._
import dev.vale.typing.types._

import scala.collection.immutable.{List, Set}

//ISolverOutcome[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError]

sealed trait ICompilerSolverOutcome {
  def getOrDie(): Map[IRuneS, ITemplataT[ITemplataType]]
}
sealed trait IIncompleteOrFailedCompilerSolve extends ICompilerSolverOutcome {
  def unsolvedRules: Vector[IRulexSR]
  def unsolvedRunes: Vector[IRuneS]
  def steps: Stream[Step[IRulexSR, IRuneS, ITemplataT[ITemplataType]]]
}
case class CompleteCompilerSolve(
  steps: Stream[Step[IRulexSR, IRuneS, ITemplataT[ITemplataType]]],
  conclusions: Map[IRuneS, ITemplataT[ITemplataType]],
  runeToBound: InstantiationBoundArgumentsT,
  declaredBounds: Vector[PrototypeTemplataT],
  reachableBounds: Vector[PrototypeTemplataT] // DO NOT SUBMIT document. is this for the call site?
) extends ICompilerSolverOutcome {
  override def getOrDie(): Map[IRuneS, ITemplataT[ITemplataType]] = conclusions
}
case class IncompleteCompilerSolve(
  steps: Stream[Step[IRulexSR, IRuneS, ITemplataT[ITemplataType]]],
  unsolvedRules: Vector[IRulexSR],
  unknownRunes: Set[IRuneS],
  incompleteConclusions: Map[IRuneS, ITemplataT[ITemplataType]]
) extends IIncompleteOrFailedCompilerSolve {
  vassert(unknownRunes.nonEmpty)
  override def getOrDie(): Map[IRuneS, ITemplataT[ITemplataType]] = vfail()
  override def unsolvedRunes: Vector[IRuneS] = unknownRunes.toVector
}

case class FailedCompilerSolve(
  steps: Stream[Step[IRulexSR, IRuneS, ITemplataT[ITemplataType]]],
  unsolvedRules: Vector[IRulexSR],
  error: ISolverError[IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError]
) extends IIncompleteOrFailedCompilerSolve {
  override def getOrDie(): Map[IRuneS, ITemplataT[ITemplataType]] = vfail()
  override def unsolvedRunes: Vector[IRuneS] = Vector()
}

case class InferEnv(
  // This is the only one that matters when checking template instantiations.
  // This is also the one that the placeholders come from.
  originalCallingEnv: IInDenizenEnvironmentT,

  parentRanges: List[RangeS],
  callLocation: LocationInDenizen,

  // We look in this for everything else, such as type names like "int" etc.
  selfEnv: IEnvironmentT,


  // Sometimes these can be all equal.

  contextRegion: RegionT
)

case class InitialSend(
  senderRune: RuneUsage,
  receiverRune: RuneUsage,
  sendTemplata: ITemplataT[ITemplataType])

case class InitialKnown(
  rune: RuneUsage,
  templata: ITemplataT[ITemplataType])

trait IInferCompilerDelegate {
  def resolveStruct(
    callingEnv: IInDenizenEnvironmentT,
    state: CompilerOutputs,
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    templata: StructDefinitionTemplataT,
    templateArgs: Vector[ITemplataT[ITemplataType]],
    verifyConclusions: Boolean):
  IResolveOutcome[StructTT]

  def resolveInterface(
    callingEnv: IInDenizenEnvironmentT,
    state: CompilerOutputs,
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    templata: InterfaceDefinitionTemplataT,
    templateArgs: Vector[ITemplataT[ITemplataType]],
    verifyConclusions: Boolean):
  IResolveOutcome[InterfaceTT]

  def resolveStaticSizedArrayKind(
    coutputs: CompilerOutputs,
    mutability: ITemplataT[MutabilityTemplataType],
    variability: ITemplataT[VariabilityTemplataType],
    size: ITemplataT[IntegerTemplataType],
    element: CoordT,
    region: RegionT):
  StaticSizedArrayTT

  def resolveRuntimeSizedArrayKind(
    coutputs: CompilerOutputs,
    type2: CoordT,
    arrayMutability: ITemplataT[MutabilityTemplataType],
    region: RegionT):
  RuntimeSizedArrayTT

  def resolveFunction(
    callingEnv: IInDenizenEnvironmentT,
    state: CompilerOutputs,
    range: List[RangeS],
    callLocation: LocationInDenizen,
    name: StrI,
    coords: Vector[CoordT],
    contextRegion: RegionT,
    verifyConclusions: Boolean):
  Result[StampFunctionSuccess, FindFunctionFailure]

  def resolveImpl(
    callingEnv: IInDenizenEnvironmentT,
    state: CompilerOutputs,
    range: List[RangeS],
    callLocation: LocationInDenizen,
    subKind: ISubKindTT,
    superKind: ISuperKindTT):
  IsParentResult
}

class InferCompiler(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    nameTranslator: NameTranslator,
    infererDelegate: IInfererDelegate,
    delegate: IInferCompilerDelegate) {
  val compilerSolver = new CompilerSolver(opts.globalOptions, interner, infererDelegate)

  // The difference between solveForDefining and solveForResolving is whether we declare the function bounds that the
  // rules mention, see DBDAR.
  def solveForDefining(
    envs: InferEnv, // See CSSNCE
    coutputs: CompilerOutputs,
    rules: Vector[IRulexSR],
    runeToType: Map[IRuneS, ITemplataType],
    invocationRange: List[RangeS],
    callLocation: LocationInDenizen,
    initialKnowns: Vector[InitialKnown],
    initialSends: Vector[InitialSend],
    includeReachableBoundsForRunes: Vector[IRuneS]):
  Result[CompleteCompilerSolve, IIncompleteOrFailedCompilerSolve] = {
    val solver =
      makeSolver(envs, coutputs, rules, runeToType, invocationRange, initialKnowns, initialSends)
    continue(envs, coutputs, solver) match {
      case Ok(()) =>
      case Err(e) => return Err(e)
    }

    checkDefiningConclusionsAndResolve(
      envs, coutputs, invocationRange, callLocation, runeToType, rules, includeReachableBoundsForRunes, solver)
  }

  // The difference between solveForDefining and solveForResolving is whether we declare the function bounds that the
  // rules mention, see DBDAR.
  def solveForResolving(
      envs: InferEnv, // See CSSNCE
      coutputs: CompilerOutputs,
      rules: Vector[IRulexSR],
      runeToType: Map[IRuneS, ITemplataType],
      invocationRange: List[RangeS],
      callLocation: LocationInDenizen,
      initialKnowns: Vector[InitialKnown],
      initialSends: Vector[InitialSend],
      includeReachableBoundsForRunes: Vector[IRuneS]):
  Result[CompleteCompilerSolve, IIncompleteOrFailedCompilerSolve] = {
    val solver =
      makeSolver(envs, coutputs, rules, runeToType, invocationRange, initialKnowns, initialSends)
    continue(envs, coutputs, solver) match {
      case Ok(()) =>
      case Err(e) => return Err(e)
    }
    checkResolvingConclusionsAndResolve(
      envs, coutputs, invocationRange, callLocation, runeToType, rules, includeReachableBoundsForRunes, solver)
  }

  def partialSolve(
      envs: InferEnv, // See CSSNCE
      coutputs: CompilerOutputs,
      rules: Vector[IRulexSR],
      runeToType: Map[IRuneS, ITemplataType],
      invocationRange: List[RangeS],
      initialKnowns: Vector[InitialKnown],
      initialSends: Vector[InitialSend]):
  Result[Map[IRuneS, ITemplataT[ITemplataType]], FailedCompilerSolve] = {
    val solver =
      makeSolver(envs, coutputs, rules, runeToType, invocationRange, initialKnowns, initialSends)
    continue(envs, coutputs, solver) match {
      case Ok(()) =>
      case Err(e) => return Err(e)
    }
    Ok(solver.userifyConclusions().toMap)
  }


  def makeSolver(
    envs: InferEnv, // See CSSNCE
    state: CompilerOutputs,
    initialRules: Vector[IRulexSR],
    initialRuneToType: Map[IRuneS, ITemplataType],
    invocationRange: List[RangeS],
    initialKnowns: Vector[InitialKnown],
    initialSends: Vector[InitialSend]):
  Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType],
    ITypingPassSolverError] = {
    Profiler.frame(() => {
      val runeToType =
        initialRuneToType ++
          initialSends.map({ case InitialSend(senderRune, _, _) =>
            senderRune.rune -> CoordTemplataType()
          })
      val rules =
        initialRules ++
          initialSends.map({ case InitialSend(senderRune, receiverRune, _) =>
            CoordSendSR(receiverRune.range, senderRune, receiverRune)
          })
      val alreadyKnown =
        initialKnowns.map({ case InitialKnown(rune, templata) =>
          if (opts.globalOptions.sanityCheck) {
            infererDelegate.sanityCheckConclusion(envs, state, rune.rune, templata)
          }
          rune.rune -> templata
        }).toMap ++
          initialSends.map({ case InitialSend(senderRune, _, senderTemplata) =>
            if (opts.globalOptions.sanityCheck) {
              infererDelegate.sanityCheckConclusion(envs, state, senderRune.rune, senderTemplata)
            }
            (senderRune.rune -> senderTemplata)
          })

      val solver =
        compilerSolver.makeSolver(invocationRange, envs, state, rules, runeToType, alreadyKnown)
      solver
    })
  }

  def continue(
    envs: InferEnv, // See CSSNCE
    state: CompilerOutputs,
    solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError]):
  Result[Unit, FailedCompilerSolve] = {
    compilerSolver.continue(envs, state, solver) match {
      case Ok(()) => Ok(())
      case Err(FailedSolve(steps, unsolvedRules, error)) => Err(FailedCompilerSolve(steps, unsolvedRules, error))
    }
  }

  def checkResolvingConclusionsAndResolve(
      envs: InferEnv, // See CSSNCE
      state: CompilerOutputs,
      invocationRange: List[RangeS],
      callLocation: LocationInDenizen,
      runeToType: Map[IRuneS, ITemplataType],
      initialRules: Vector[IRulexSR],
      includeReachableBoundsForRunes: Vector[IRuneS],
      solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError]):
  Result[CompleteCompilerSolve, IIncompleteOrFailedCompilerSolve] = {
    compilerSolver.interpretResults(runeToType, solver) match {
      case CompleteSolve(steps, conclusions) => {
        // DO NOT SUBMIT might be slow.
        val declaredBounds =
          initialRules
              .collect({ case DefinitionFuncSR(_, resultRune, _, _, _) => resultRune.rune })
              .map(conclusions)
              .map({
                case p @ PrototypeTemplataT(_, _) => p
                case other => vwat(other)
              })
        val reachableBounds =
          includeReachableBoundsForRunes
              .map(conclusions)
              .flatMap(conc => TemplataCompiler.getReachableBounds(interner, keywords, state, conc))
        val envWithConclusions = importReachableBounds(state, envs.originalCallingEnv, declaredBounds, reachableBounds)
        val runeToFunctionBound =
          resolveConclusions(
            envWithConclusions, state, invocationRange, callLocation, envs.contextRegion, initialRules, conclusions) match {
            case Ok(c) => vassertSome(c)
            case Err(e) => return Err(FailedCompilerSolve(steps, Vector(), e))
          }
        Ok(CompleteCompilerSolve(steps, conclusions, runeToFunctionBound, declaredBounds, reachableBounds))
      }
      case i @ IncompleteSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
        Err(IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions))
      }
      case FailedSolve(steps, unsolvedRules, error) => {
        Err(FailedCompilerSolve(steps, unsolvedRules, error))
      }
    }
  }

  def checkDefiningConclusionsAndResolve(
      envs: InferEnv, // See CSSNCE
      state: CompilerOutputs,
      invocationRange: List[RangeS],
      callLocation: LocationInDenizen,
      runeToType: Map[IRuneS, ITemplataType],
      initialRules: Vector[IRulexSR],
      includeReachableBoundsForRunes: Vector[IRuneS],
      solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError]):
  Result[CompleteCompilerSolve, IIncompleteOrFailedCompilerSolve] = {
    compilerSolver.interpretResults(runeToType, solver) match {
      case CompleteSolve(steps, conclusions) => {
        // DO NOT SUBMIT might be slow.
        val declaredBounds =
          initialRules
              .collect({ case DefinitionFuncSR(_, resultRune, _, _, _) => resultRune.rune })
              .map(conclusions)
              .map({
                case p@PrototypeTemplataT(_, _) => p
                case other => vwat(other)
              })
        val reachableBounds =
          includeReachableBoundsForRunes
              .map(conclusions)
              .flatMap(conc => TemplataCompiler.getReachableBounds(interner, keywords, state, conc))
        val environmentForFinalizing =
          importConclusionsAndReachableBounds(state, envs.originalCallingEnv, conclusions, declaredBounds, reachableBounds)
        val instantiationBoundArgs =
          resolveConclusions(
            environmentForFinalizing, state, invocationRange, callLocation, envs.contextRegion, initialRules, conclusions) match {
              case Ok(c) => vassertSome(c)
              case Err(e) => return Err(FailedCompilerSolve(steps, Vector(), e))
            }
        Ok(CompleteCompilerSolve(steps, conclusions, instantiationBoundArgs, declaredBounds, reachableBounds))
      }
      case IncompleteSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
        Err(IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions))
      }
      case FailedSolve(steps, unsolvedRules, error) => {
        Err(FailedCompilerSolve(steps, unsolvedRules, error))
      }
    }
  }

  def importReachableBounds(
    coutputs: CompilerOutputs,
    originalCallingEnv: IInDenizenEnvironmentT, // See CSSNCE
    declaredBounds: Vector[PrototypeTemplataT],
    reachableBounds: Vector[PrototypeTemplataT]):
  GeneralEnvironmentT[INameT] = {

    declaredBounds.foreach({ case PrototypeTemplataT(range, prototype) =>
      // DO NOT SUBMIT move from TemplatasStore
      TemplatasStore.getImpreciseName(interner, prototype.id.localName) match {
        case None => println("Skipping adding bound " + prototype.id.localName) // DO NOT SUBMIT
        case Some(impreciseName) => {
          coutputs.addOverload(
            opts.globalOptions.useOverloadIndex,
            impreciseName,
            prototype.id.localName.parameters.map(x => Some(x)),
            PrototypeTemplataCalleeCandidate(range, prototype))
        }
      }
    })

    GeneralEnvironmentT.childOf(
      interner,
      originalCallingEnv,
      originalCallingEnv.id,
      // These are the bounds we pulled in from the parameters, return type, impl sub citizen, etc.
      reachableBounds.zipWithIndex.map({ case (reachableBound, index) =>
        interner.intern(ReachablePrototypeNameT(index)) -> TemplataEnvEntry(reachableBound)
      }))
  }

  // This includes putting newly defined bound functions in.
  def importConclusionsAndReachableBounds(
      coutputs: CompilerOutputs,
      originalCallingEnv: IInDenizenEnvironmentT, // See CSSNCE
      conclusions: Map[IRuneS, ITemplataT[ITemplataType]],
      declaredBounds: Vector[PrototypeTemplataT],
      reachableBounds: Vector[PrototypeTemplataT]):
  GeneralEnvironmentT[INameT] = {
    // If this is the original calling env, in other words, if we're the original caller for
    // this particular solve, then lets add all of our templatas to the environment.

    (declaredBounds ++ reachableBounds).foreach({ case PrototypeTemplataT(range, prototype) =>
      // DO NOT SUBMIT move from TemplatasStore
      TemplatasStore.getImpreciseName(interner, prototype.id.localName) match {
        case None => println("Skipping adding bound " + prototype.id.localName) // DO NOT SUBMIT
        case Some(impreciseName) => {
          coutputs.addOverload(
            opts.globalOptions.useOverloadIndex,
            impreciseName,
            prototype.id.localName.parameters.map(x => Some(x)),
            PrototypeTemplataCalleeCandidate(range, prototype))
        }
      }
    })

    GeneralEnvironmentT.childOf(
      interner,
      originalCallingEnv,
      originalCallingEnv.id,
      conclusions
          .map({ case (nameS, templata) =>
            interner.intern(RuneNameT((nameS))) -> TemplataEnvEntry(templata)
          }).toVector ++
          // These are the bounds we pulled in from the parameters, return type, impl sub citizen, etc.
          reachableBounds.zipWithIndex.map({ case (reachableBound, index) =>
            interner.intern(ReachablePrototypeNameT(index)) -> TemplataEnvEntry(reachableBound)
          }))
  }

  private def resolveConclusions(
    env: IInDenizenEnvironmentT, // See CSSNCE
    state: CompilerOutputs,
    ranges: List[RangeS],
    callLocation: LocationInDenizen,
    contextRegion: RegionT,
    rules: Vector[IRulexSR],
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]]):
  Result[Option[InstantiationBoundArgumentsT], ISolverError[IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError]] = {
    // Check all template calls
    rules.foreach({
      case r@CallSR(_, _, _, _) => {
        resolveTemplateCallConclusion(env, state, ranges, callLocation, r, conclusions) match {
          case Ok(()) =>
          case Err(e) => return Err(RuleError(CouldntResolveKind(e)))
        }
      }
      case _ =>
    })

    val maybeRunesAndPrototypes =
      rules.collect({
        case r@ResolveSR(_, _, _, _, _) => {
          resolveFunctionCallConclusion(env, state, ranges, callLocation, r, conclusions, contextRegion) match {
            case Ok(maybeRuneAndPrototype) => maybeRuneAndPrototype
            case Err(e) => return Err(e)
          }
        }
      })
    val runeToPrototype = maybeRunesAndPrototypes.flatten.toMap
    if (runeToPrototype.size < maybeRunesAndPrototypes.size) {
      // checkFunctionCall returns None if it was an incomplete solve and we didn't have some
      // param types so it didn't attempt to resolve them.
      // If that happened at all, return None for the entire time.
      return Ok(None)
    }

    val maybeRunesAndImpls =
      rules.collect({
        case r@CallSiteCoordIsaSR(_, _, _, _) => {
          resolveImplConclusion(env, state, ranges, callLocation, r, conclusions) match {
            case Ok(maybeRuneAndPrototype) => maybeRuneAndPrototype
            case Err(e) => return Err(e)
          }
        }
      })
    val runeToImpl = maybeRunesAndImpls.flatten.toMap
    if (runeToImpl.size < maybeRunesAndImpls.size) {
      // checkFunctionCall returns None if it was an incomplete solve and we didn't have some
      // param types so it didn't attempt to resolve them.
      // If that happened at all, return None for the entire time.
      return Ok(None)
    }

    Ok(Some(InstantiationBoundArgumentsT(runeToPrototype, runeToImpl)))
  }

  // Returns None for any call that we don't even have params for,
  // like in the case of an incomplete solve.
  def resolveFunctionCallConclusion(
    callingEnv: IInDenizenEnvironmentT,
    state: CompilerOutputs,
    ranges: List[RangeS],
    callLocation: LocationInDenizen,
    c: ResolveSR,
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]],
    contextRegion: RegionT):
  Result[Option[(IRuneS, PrototypeT)], ISolverError[IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError]] = {
    val ResolveSR(range, resultRune, name, paramsListRune, returnRune) = c

    // If it was an incomplete solve, then just skip.
    val returnCoord =
      conclusions.get(returnRune.rune) match {
        case Some(CoordTemplataT(t)) => t
        case None => return Ok(None)
      }
    val paramCoords =
      conclusions.get(paramsListRune.rune) match {
        case None => return Ok(None)
        case Some(CoordListTemplataT(paramList)) => paramList
      }

    val funcSuccess =
      delegate.resolveFunction(callingEnv, state, range :: ranges, callLocation, name, paramCoords, contextRegion, true) match {
        case Err(e) => return Err(RuleError(CouldntFindFunction(range :: ranges, e)))
        case Ok(x) => x
      }

    if (funcSuccess.prototype.prototype.returnType != returnCoord) {
      return Err(RuleError(ReturnTypeConflict(range :: ranges, returnCoord, funcSuccess.prototype.prototype)))
    }

    Ok(Some((resultRune.rune, funcSuccess.prototype.prototype)))
  }

  // Returns None for any call that we don't even have params for,
  // like in the case of an incomplete solve.
  def resolveImplConclusion(
    callingEnv: IInDenizenEnvironmentT,
    state: CompilerOutputs,
    ranges: List[RangeS],
    callLocation: LocationInDenizen,
    c: CallSiteCoordIsaSR,
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]]):
  Result[Option[(IRuneS, IdT[IImplNameT])], ISolverError[IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError]] = {
    val CallSiteCoordIsaSR(range, resultRune, subRune, superRune) = c

    // If it was an incomplete solve, then just skip.
    val subCoord =
      conclusions.get(subRune.rune) match {
        case Some(CoordTemplataT(t)) => t
        case None => return Ok(None)
      }
    val subKind = subCoord.kind match { case x : ISubKindTT => x case other => vwat(other) }

    val superCoord =
      conclusions.get(superRune.rune) match {
        case Some(CoordTemplataT(t)) => t
        case None => return Ok(None)
      }
    val superKind = superCoord.kind match { case x : ISuperKindTT => x case other => vwat(other) }

    val implSuccess =
      delegate.resolveImpl(callingEnv, state, range :: ranges, callLocation, subKind, superKind) match {
        case x @ IsntParent(_) => return Err(RuleError(CouldntFindImpl(range :: ranges, x)))
        case x @ IsParent(_, _, _) => x
      }

    Ok(Some((vassertSome(resultRune).rune, implSuccess.implId)))
  }

  // Returns None for any call that we don't even have params for,
  // like in the case of an incomplete solve.
  def resolveTemplateCallConclusion(
    callingEnv: IInDenizenEnvironmentT,
    state: CompilerOutputs,
    ranges: List[RangeS],
      callLocation: LocationInDenizen,
    c: CallSR,
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]]):
  Result[Unit, ResolveFailure[KindT]] = {
//  Result[Option[(IRuneS, PrototypeTemplata)], ISolverError[IRuneS, ITemplata[ITemplataType], ITypingPassSolverError]] = {
    val CallSR(range, resultRune, templateRune, argRunes) = c

    // If it was an incomplete solve, then just skip.
    val template =
      conclusions.get(templateRune.rune) match {
        case Some(t) => t
        case None =>  return Ok(None)
      }
    val args =
      argRunes.map(argRune => {
        conclusions.get(argRune.rune) match {
          case Some(t) => t
          case None =>  return Ok(None)
        }
      })

    template match {
      case RuntimeSizedArrayTemplateTemplataT() => {
        val Vector(m, CoordTemplataT(coord)) = args
        val mutability = ITemplataT.expectMutability(m)
        val contextRegion = RegionT()
        delegate.resolveRuntimeSizedArrayKind(state, coord, mutability, contextRegion)
        Ok(())
      }
      case StaticSizedArrayTemplateTemplataT() => {
        val Vector(s, m, v, CoordTemplataT(coord)) = args
        val size = ITemplataT.expectInteger(s)
        val mutability = ITemplataT.expectMutability(m)
        val variability = ITemplataT.expectVariability(v)
        val contextRegion = RegionT()
        delegate.resolveStaticSizedArrayKind(state, mutability, variability, size, coord, contextRegion)
        Ok(())
      }
      case it @ StructDefinitionTemplataT(_, _) => {
        delegate.resolveStruct(callingEnv, state, range :: ranges, callLocation, it, args.toVector, true) match {
          case ResolveSuccess(kind) => kind
          case rf @ ResolveFailure(_, _) => return Err(rf)
        }
        Ok(())
      }
      case it @ InterfaceDefinitionTemplataT(_, _) => {
        delegate.resolveInterface(callingEnv, state, range :: ranges, callLocation, it, args.toVector, true) match {
          case ResolveSuccess(kind) => kind
          case rf @ ResolveFailure(_, _) => return Err(rf)
        }
        Ok(())
      }
      case kt @ KindTemplataT(_) => {
        Ok(())
      }
      case other => vimpl(other)
    }
  }

  def incrementallySolve(
    envs: InferEnv,
    coutputs: CompilerOutputs,
    solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError],
    onIncompleteSolve: (Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError]) => Boolean):
  Result[Boolean, FailedCompilerSolve] = {
    // See IRAGP for why we have this incremental solving/placeholdering.
    while ( {
      continue(envs, coutputs, solver) match {
        case Ok(()) =>
        case Err(f) => return Err(f)
      }

      // During the solve, we postponed resolving structs and interfaces, see SFWPRL.
      // Caller should remember to do that!
      if (!solver.isComplete()) {
        val continue = onIncompleteSolve(solver)
        if (!continue) {
          return Ok(false)
        }
        true
      } else {
        return Ok(true)
      }
    }) {}

    vfail() // Shouldnt get here
  }
}

object InferCompiler {
  // Some rules should be excluded from the call site, see SROACSD.
  def includeRuleInCallSiteSolve(rule: IRulexSR): Boolean = {
    rule match {
      case DefinitionFuncSR(_, _, _, _, _) => false
      case DefinitionCoordIsaSR(_, _, _, _) => false
      case _ => true
    }
  }

  // Some rules should be excluded from the call site, see SROACSD.
  def includeRuleInDefinitionSolve(rule: IRulexSR): Boolean = {
    rule match {
      case CallSiteCoordIsaSR(_, _, _, _) => false
      case CallSiteFuncSR(_, _, _, _, _) => false
      case ResolveSR(_, _, _, _, _) => false
      case _ => true
    }
  }
}