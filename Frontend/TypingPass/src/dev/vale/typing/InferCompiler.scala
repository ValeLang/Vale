package dev.vale.typing

import dev.vale.highertyping.FunctionA
import dev.vale._
import dev.vale.postparsing._
import dev.vale.postparsing.rules._
import dev.vale.solver._
import dev.vale.postparsing._
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.ast._
import dev.vale.typing.citizen._
import dev.vale.typing.env._
import dev.vale.typing.function._
import dev.vale.typing.infer._
import dev.vale.typing.names._
import dev.vale.typing.templata.ITemplataT._
import dev.vale.typing.templata._
import dev.vale.typing.types._

import scala.collection.immutable._

//ISolverOutcome[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError]

sealed trait IResolveSolveOutcome
case class CompleteResolveSolve(
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]],
    runeToBound: InstantiationBoundArgumentsT[IFunctionNameT, IImplNameT]
) extends IResolveSolveOutcome

case class CompleteDefineSolve(
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]],
    runeToBound: InstantiationBoundArgumentsT[FunctionBoundNameT, ImplBoundNameT])

sealed trait IIncompleteOrFailedCompilerSolve extends IResolveSolveOutcome {
  def unsolvedRules: Vector[IRulexSR]
  def unsolvedRunes: Vector[IRuneS]
  def steps: Stream[Step[IRulexSR, IRuneS, ITemplataT[ITemplataType]]]
}
case class IncompleteCompilerSolve(
  steps: Stream[Step[IRulexSR, IRuneS, ITemplataT[ITemplataType]]],
  unsolvedRules: Vector[IRulexSR],
  unknownRunes: Set[IRuneS],
  incompleteConclusions: Map[IRuneS, ITemplataT[ITemplataType]]
) extends IIncompleteOrFailedCompilerSolve {
  vassert(unknownRunes.nonEmpty)
  override def unsolvedRunes: Vector[IRuneS] = unknownRunes.toVector
}

case class FailedCompilerSolve(
  steps: Stream[Step[IRulexSR, IRuneS, ITemplataT[ITemplataType]]],
  unsolvedRules: Vector[IRulexSR],
  error: ISolverError[IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError]
) extends IIncompleteOrFailedCompilerSolve {
  override def unsolvedRunes: Vector[IRuneS] = Vector()
}

sealed trait IConclusionResolveError
case class CouldntFindImplForConclusionResolve(range: List[RangeS], fail: IsntParent) extends IConclusionResolveError
case class CouldntFindKindForConclusionResolve(inner: ResolveFailure[KindT]) extends IConclusionResolveError
case class CouldntFindFunctionForConclusionResolve(range: List[RangeS], fff: FindFunctionFailure) extends IConclusionResolveError
case class ReturnTypeConflictInConclusionResolve(range: List[RangeS], expectedReturnType: CoordT, actual: PrototypeT[IFunctionNameT]) extends IConclusionResolveError

sealed trait IResolvingError
case class ResolvingSolveFailedOrIncomplete(inner: IIncompleteOrFailedCompilerSolve) extends IResolvingError
case class ResolvingResolveConclusionError(inner: IConclusionResolveError) extends IResolvingError

sealed trait IDefiningError
case class DefiningSolveFailedOrIncomplete(inner: IIncompleteOrFailedCompilerSolve) extends IDefiningError
case class DefiningResolveConclusionError(inner: IConclusionResolveError) extends IDefiningError

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
    templateArgs: Vector[ITemplataT[ITemplataType]]):
  IResolveOutcome[StructTT]

  def resolveInterface(
    callingEnv: IInDenizenEnvironmentT,
    state: CompilerOutputs,
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    templata: InterfaceDefinitionTemplataT,
    templateArgs: Vector[ITemplataT[ITemplataType]]):
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
  Result[CompleteDefineSolve, IDefiningError] = {
    val solver =
      makeSolver(envs, coutputs, rules, runeToType, invocationRange, initialKnowns, initialSends)
    continue(envs, coutputs, solver) match {
      case Ok(()) =>
      case Err(e) => return Err(DefiningSolveFailedOrIncomplete(e))
    }
    val conclusions =
      interpretResults(runeToType, solver) match {
        case Ok(conclusions) => conclusions
        case Err(f) => return Err(DefiningSolveFailedOrIncomplete(f))
      }
    checkDefiningConclusionsAndResolve(
      envs, coutputs, invocationRange, callLocation, rules, includeReachableBoundsForRunes, conclusions) match {
      case Ok(instantiationBoundArgs) => Ok(CompleteDefineSolve(conclusions, instantiationBoundArgs))
      case Err(x) => Err(DefiningResolveConclusionError(x))
    }
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
      initialSends: Vector[InitialSend]):
  Result[CompleteResolveSolve, IResolvingError] = {
    val solver =
      makeSolver(envs, coutputs, rules, runeToType, invocationRange, initialKnowns, initialSends)
    continue(envs, coutputs, solver) match {
      case Ok(()) =>
      case Err(e) => return Err(ResolvingSolveFailedOrIncomplete(e))
    }
    checkResolvingConclusionsAndResolve(
      envs, coutputs, invocationRange, callLocation, runeToType, rules, Vector(), solver)
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
      ranges: List[RangeS],
      callLocation: LocationInDenizen,
      runeToType: Map[IRuneS, ITemplataType],
      rules: Vector[IRulexSR],
      includeReachableBoundsForRunes: Vector[IRuneS],
      solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError]):
  Result[CompleteResolveSolve, IResolvingError] = {
    val (steps, conclusions) =
      compilerSolver.interpretResults(runeToType, solver) match {
        case CompleteSolve(steps, conclusions) => (steps, conclusions)
        case IncompleteSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
          return Err(ResolvingSolveFailedOrIncomplete(IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions)))
        }
        case FailedSolve(steps, unsolvedRules, error) => {
          return Err(ResolvingSolveFailedOrIncomplete(FailedCompilerSolve(steps, unsolvedRules, error)))
        }
      }
    // rules.collect({
    //   case r@CallSR(_, RuneUsage(_, callerResolveResultRune), _, _) => {
    //     val inferences =
    //       resolveTemplateCallConclusion(envs.originalCallingEnv, state, ranges, callLocation, r, conclusions) match {
    //         case Ok(i) => i
    //         case Err(e) => return Err(FailedCompilerSolve(steps, Vector(), RuleError(CouldntResolveKind(e))))
    //       }
    //     val _ = inferences // We don't care, we just did the resolve so that we could instantiate it and add its
    //   }
    // })

    val citizensFromCalls =
      rules
          .collect({ case CallSR(_, RuneUsage(_, resultRune), _, _) => resultRune })
          .map(rune => vassertSome(conclusions.get(rune)))
          .collect({
            case KindTemplataT(c @ ICitizenTT(_)) => c
            case CoordTemplataT(CoordT(_, _, c @ ICitizenTT(_))) => c
          })

    val includeReachableBoundsForRunesWithCitizens =
      includeReachableBoundsForRunes
          .map(rune => rune -> vassertSome(conclusions.get(rune)))
          .collect({
            case (rune, KindTemplataT(c @ ICitizenTT(_))) => rune -> c
            case (rune, CoordTemplataT(CoordT(_, _, c @ ICitizenTT(_)))) => rune -> c
          })
          // See OIRCRR, we intersect the CallSR result runes with includeReachableBoundsForRunes because we only want
          // to supply reachable functions for things that the function definition knows are citizen calls.
          .filter({ case (rune, citizen) => citizensFromCalls.contains(citizen) })

    val reachableBounds =
      includeReachableBoundsForRunesWithCitizens
          .toMap
          .mapValues(citizen => {
            InstantiationReachableBoundArgumentsT(
              TemplataCompiler.getReachableBounds(opts.globalOptions.sanityCheck, interner, keywords, envs.originalCallingEnv.denizenTemplateId, state, citizen)
                  .citizenRuneToReachablePrototype.map({ case (citizenRune, callerPlaceholderedCitizenBound) =>
                // If foo(&HashMap<int>) is calling func moo<H>(self &HashMap<H>) and HashMap has an implicit drop
                // bound, then callerPlaceholderedCitizenBound looks like HashMap.bound:drop(foo$T).
                // But we want the real resolved drop function, func drop(int)void.
                val returnCoord = callerPlaceholderedCitizenBound.returnType
                val paramCoords = callerPlaceholderedCitizenBound.paramTypes
                val funcSuccess =
                  delegate.resolveFunction(
                    envs.originalCallingEnv, state, ranges, callLocation, callerPlaceholderedCitizenBound.id.localName.template.humanName, paramCoords, envs.contextRegion, true) match {
                    case Err(e) => return Err(ResolvingResolveConclusionError(CouldntFindFunctionForConclusionResolve(ranges, e)))
                    case Ok(x) => x
                  }
                if (funcSuccess.prototype.returnType != returnCoord) {
                  return Err(ResolvingResolveConclusionError(ReturnTypeConflictInConclusionResolve(ranges, returnCoord, funcSuccess.prototype)))
                }
                citizenRune -> funcSuccess.prototype
              }))
          })
          .toMap
    val envWithConclusions = importReachableBounds(envs.originalCallingEnv, reachableBounds)
    // Check all template calls
    rules.collect({
      case r@CallSR(_, RuneUsage(_, callerResolveResultRune), _, _) => {
        val inferences =
          resolveTemplateCallConclusion(envWithConclusions, state, ranges, callLocation, r, conclusions) match {
            case Ok(i) => i
            case Err(e) => return Err(ResolvingSolveFailedOrIncomplete(FailedCompilerSolve(steps, Vector(), RuleError(CouldntResolveKind(e)))))
          }
        val _ = inferences // We don't care, we just did the resolve so that we could instantiate it and add its
      }
    })

    val runesAndPrototypes =
      rules.collect({
        case r@ResolveSR(_, _, _, _, _) => {
          resolveFunctionCallConclusion(envWithConclusions, state, ranges, callLocation, r, conclusions, envs.contextRegion) match {
            case Ok(x) => x
            case Err(e) => return Err(ResolvingResolveConclusionError(e))
          }
        }
      })
    val runeToPrototype = runesAndPrototypes.toMap
    if (runeToPrototype.size < runesAndPrototypes.size) {
      // checkFunctionCall returns None if it was an incomplete solve and we didn't have some
      // param types so it didn't attempt to resolve them.
      // If that happened at all, return None for the entire time.
      vwat()
    }

    val runesAndImpls =
      rules.collect({
        case r@CallSiteCoordIsaSR(_, _, _, _) => {
          resolveImplConclusion(envWithConclusions, state, ranges, callLocation, r, conclusions) match {
            case Ok(x) => x
            case Err(e) => return Err(ResolvingResolveConclusionError(e))
          }
        }
      })
    val runeToImpl = runesAndImpls.toMap
    if (runeToImpl.size < runesAndImpls.size) {
      // checkFunctionCall returns None if it was an incomplete solve and we didn't have some
      // param types so it didn't attempt to resolve them.
      // If that happened at all, return None for the entire time.
      vwat()
    }

    val instantiationBoundArgs =
      InstantiationBoundArgumentsT.make[IFunctionNameT, IImplNameT](
        runeToPrototype,
        reachableBounds.filter(_._2.citizenRuneToReachablePrototype.nonEmpty),
        runeToImpl)

    Ok(CompleteResolveSolve(conclusions, instantiationBoundArgs))
  }

  def interpretResults(
      runeToType: Map[IRuneS, ITemplataType],
      solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError]):
  Result[Map[IRuneS, ITemplataT[ITemplataType]], IIncompleteOrFailedCompilerSolve] = {
    compilerSolver.interpretResults(runeToType, solver) match {
      case CompleteSolve(steps, conclusions) => Ok(conclusions)
      case IncompleteSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
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
      initialRules: Vector[IRulexSR],
      includeReachableBoundsForRunes: Vector[IRuneS],
      conclusions: Map[IRuneS, ITemplataT[ITemplataType]]):
  Result[InstantiationBoundArgumentsT[FunctionBoundNameT, ImplBoundNameT], IConclusionResolveError] = {
    val reachableBounds =
      includeReachableBoundsForRunes
          .map(rune => rune -> vassertSome(conclusions.get(rune)))
          .toMap
          .mapValues(templata => {
            val maybeMentionedKind =
              templata match {
                case KindTemplataT(kind) => Some(kind)
                case CoordTemplataT(CoordT(_, _, kind)) => Some(kind)
                case _ => None
              }
            val maybeIdAndTemplateId =
              maybeMentionedKind match {
                case Some(ICitizenTT(id)) => Some((id, TemplataCompiler.getCitizenTemplate(id)))
                // This can happen if we have for example:
                //   struct Bork<T> { x T; }
                //   func Bork<T>(x T) { ... }
                // we're trying to see if there are any bounds we can grab from that placeholder.
                // buuuut let's comment it out because it'll just get caught by the below Some(_) case.
                // case Some(KindPlaceholderT(id)) => Some((id, TemplataCompiler.getPlaceholderTemplate(id)))
                case Some(_) => None
                case None => None
              }
            InstantiationReachableBoundArgumentsT(
              maybeIdAndTemplateId match {
                case None => Map[IRuneS, PrototypeT[FunctionBoundNameT]]()
                case Some((id, templateId)) => {
                  val innerEnv = state.getInnerEnvForType(templateId)
                  val substituter =
                    TemplataCompiler.getPlaceholderSubstituter(
                      opts.globalOptions.sanityCheck,
                      interner, keywords,
                      envs.originalCallingEnv.denizenTemplateId,
                      id,
                      // This function is all about gathering bounds from the incoming parameter types.
                      InheritBoundsFromTypeItself)
                    innerEnv
                      .templatas
                      .entriesByNameT
                      .collect({
                        // We're looking for FunctionBoundNameT, but producing ReachableFunctionNameT.
                        case (RuneNameT(rune), TemplataEnvEntry(PrototypeTemplataT(PrototypeT(IdT(packageCoord, initSteps, FunctionBoundNameT(FunctionBoundTemplateNameT(humanName), templateArgs, params)), returnType)))) => {
                          val prototype =
                            PrototypeT(
                              IdT(packageCoord, initSteps,
                                interner.intern(FunctionBoundNameT(
                                  interner.intern(FunctionBoundTemplateNameT(humanName)), templateArgs, params))),
                              returnType)
                          rune -> substituter.substituteForPrototype[FunctionBoundNameT](state, prototype)
                        }
                      })
                      .toMap
                }
              })
          })
    val environmentForFinalizing =
      importConclusionsAndReachableBounds(envs.originalCallingEnv, conclusions, reachableBounds)
    val instantiationBoundArgs =
      resolveConclusionsForDefine(
        environmentForFinalizing, state, invocationRange, callLocation, envs.contextRegion, initialRules, conclusions, reachableBounds) match {
          case Ok(c) => c
          case Err(e) => return Err(e)
        }
    Ok(instantiationBoundArgs)
  }

  def importReachableBounds(
      originalCallingEnv: IInDenizenEnvironmentT, // See CSSNCE
      reachableBounds: Map[IRuneS, InstantiationReachableBoundArgumentsT[IFunctionNameT]]):
  GeneralEnvironmentT[INameT] = {
    GeneralEnvironmentT.childOf(
      interner,
      originalCallingEnv,
      originalCallingEnv.denizenTemplateId,
      originalCallingEnv.id,
      // These are the bounds we pulled in from the parameters, return type, impl sub citizen, etc.
      reachableBounds.values.flatMap(_.citizenRuneToReachablePrototype.values).zipWithIndex.map({ case (reachableBound, index) =>
        interner.intern(ReachablePrototypeNameT(index)) -> TemplataEnvEntry(PrototypeTemplataT(reachableBound))
      }).toVector)
  }

  // This includes putting newly defined bound functions in.
  def importConclusionsAndReachableBounds(
      originalCallingEnv: IInDenizenEnvironmentT, // See CSSNCE
      conclusions: Map[IRuneS, ITemplataT[ITemplataType]],
      reachableBounds: Map[IRuneS, InstantiationReachableBoundArgumentsT[FunctionBoundNameT]]):
  GeneralEnvironmentT[INameT] = {
    // If this is the original calling env, in other words, if we're the original caller for
    // this particular solve, then lets add all of our templatas to the environment.
    GeneralEnvironmentT.childOf(
      interner,
      originalCallingEnv,
      originalCallingEnv.denizenTemplateId,
      originalCallingEnv.id,
      conclusions
          .map({ case (nameS, templata) =>
            interner.intern(RuneNameT((nameS))) -> TemplataEnvEntry(templata)
          }).toVector ++
          // These are the bounds we pulled in from the parameters, return type, impl sub citizen, etc.
          reachableBounds.values.flatMap(_.citizenRuneToReachablePrototype.values).zipWithIndex.map({ case (reachableBound, index) =>
            interner.intern(ReachablePrototypeNameT(index)) -> TemplataEnvEntry(PrototypeTemplataT(reachableBound))
          }))
  }

  private def resolveConclusionsForDefine(
    env: IInDenizenEnvironmentT, // See CSSNCE
    state: CompilerOutputs,
    ranges: List[RangeS],
    callLocation: LocationInDenizen,
    contextRegion: RegionT,
    rules: Vector[IRulexSR],
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]],
    reachableBounds: Map[IRuneS, InstantiationReachableBoundArgumentsT[FunctionBoundNameT]]):
  Result[InstantiationBoundArgumentsT[FunctionBoundNameT, ImplBoundNameT], IConclusionResolveError] = {
    // Check all template calls
    rules.foreach({
      case r@CallSR(_, _, _, _) => {
        resolveTemplateCallConclusion(env, state, ranges, callLocation, r, conclusions) match {
          case Ok(i) =>
          case Err(e) => return Err(CouldntFindKindForConclusionResolve(e))
        }
      }
      case _ =>
    })

    val runesAndPrototypes =
      rules.collect({
        case r@DefinitionFuncSR(_, RuneUsage(_, resultRune), _, _, _) => {
          vassertSome(conclusions.get(resultRune)) match {
            case PrototypeTemplataT(PrototypeT(IdT(packageCoord, initSteps, FunctionBoundNameT(template, templateArgs, params)), returnType)) => {
              val prototype = PrototypeT(IdT(packageCoord, initSteps, interner.intern(FunctionBoundNameT(template, templateArgs, params))), returnType)
              resultRune -> prototype
            }
            case other => vwat(other)
          }
        }
      })
    val runeToPrototype = runesAndPrototypes.toMap
    if (runeToPrototype.size < runesAndPrototypes.size) {
      // checkFunctionCall returns None if it was an incomplete solve and we didn't have some
      // param types so it didn't attempt to resolve them.
      // If that happened at all, return None for the entire time.
      vwat()
    }

    val maybeRunesAndImpls =
      rules.collect({
        case r@DefinitionCoordIsaSR(_, RuneUsage(_, resultRune), _, _) => {
          vassertSome(conclusions.get(resultRune)) match {
            case IsaTemplataT(range, IdT(packageCoord, initSteps, ImplBoundNameT(template, templateArgs)), subKind, superKind) => {
              val implId = IdT(packageCoord, initSteps, interner.intern(ImplBoundNameT(template, templateArgs)))
              resultRune -> implId
            }
            case other => vwat(other)
          }
        }
      })
    val runeToImpl = maybeRunesAndImpls.toMap
    if (runeToImpl.size < maybeRunesAndImpls.size) {
      // checkFunctionCall returns None if it was an incomplete solve and we didn't have some
      // param types so it didn't attempt to resolve them.
      // If that happened at all, return None for the entire time.
      vwat()
    }

    Ok(InstantiationBoundArgumentsT.make(runeToPrototype, reachableBounds.filter(_._2.citizenRuneToReachablePrototype.nonEmpty), runeToImpl))
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
  Result[(IRuneS, PrototypeT[IFunctionNameT]), IConclusionResolveError] = {
    val ResolveSR(range, resultRune, name, paramsListRune, returnRune) = c

    // If it was an incomplete solve, then just skip.
    val returnCoord =
      conclusions.get(returnRune.rune) match {
        case Some(CoordTemplataT(t)) => t
        case None => vwat()
      }
    val paramCoords =
      conclusions.get(paramsListRune.rune) match {
        case None => vwat()
        case Some(CoordListTemplataT(paramList)) => paramList
      }

    val funcSuccess =
      delegate.resolveFunction(callingEnv, state, range :: ranges, callLocation, name, paramCoords, contextRegion, true) match {
        case Err(e) => return Err(CouldntFindFunctionForConclusionResolve(range :: ranges, e))
        case Ok(x) => x
      }

    if (funcSuccess.prototype.returnType != returnCoord) {
      return Err(ReturnTypeConflictInConclusionResolve(range :: ranges, returnCoord, funcSuccess.prototype))
    }

    Ok((resultRune.rune, funcSuccess.prototype))
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
  Result[(IRuneS, IdT[IImplNameT]), IConclusionResolveError] = {
    val CallSiteCoordIsaSR(range, resultRune, subRune, superRune) = c

    // If it was an incomplete solve, then just skip.
    val subCoord =
      conclusions.get(subRune.rune) match {
        case Some(CoordTemplataT(t)) => t
        case None => vwat()
      }
    val subKind = subCoord.kind match { case x : ISubKindTT => x case other => vwat(other) }

    val superCoord =
      conclusions.get(superRune.rune) match {
        case Some(CoordTemplataT(t)) => t
        case None => vwat()
      }
    val superKind = superCoord.kind match { case x : ISuperKindTT => x case other => vwat(other) }

    val implSuccess =
      delegate.resolveImpl(callingEnv, state, range :: ranges, callLocation, subKind, superKind) match {
        case x @ IsntParent(_) => return Err(CouldntFindImplForConclusionResolve(range :: ranges, x))
        case x @ IsParent(_, _, _) => x
      }

    Ok((vassertSome(resultRune).rune, implSuccess.implId))
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
        delegate.resolveStruct(callingEnv, state, range :: ranges, callLocation, it, args.toVector) match {
          case ResolveSuccess(kind) => kind
          case rf @ ResolveFailure(_, _) => return Err(rf)
        }
        Ok(())
      }
      case it @ InterfaceDefinitionTemplataT(_, _) => {
        delegate.resolveInterface(callingEnv, state, range :: ranges, callLocation, it, args.toVector) match {
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
