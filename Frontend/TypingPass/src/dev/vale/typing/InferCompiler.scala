package dev.vale.typing

import dev.vale.highertyping.FunctionA
import dev.vale.{Err, Interner, Keywords, Ok, Profiler, RangeS, Result, StrI, typing, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import dev.vale.postparsing._
import dev.vale.postparsing.rules._
import dev.vale.solver._
import dev.vale.postparsing._
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.ast.PrototypeT
import dev.vale.typing.citizen.{IResolveOutcome, IsParent, IsParentResult, IsntParent, ResolveFailure, ResolveSuccess}
import dev.vale.typing.env.{CitizenEnvironmentT, EnvironmentHelper, GeneralEnvironmentT, GlobalEnvironment, IEnvEntry, IEnvironmentT, IInDenizenEnvironmentT, ILookupContext, IVariableT, TemplataEnvEntry, TemplataLookupContext, TemplatasStore}
import dev.vale.typing.function._
import dev.vale.typing.infer.{CompilerSolver, CouldntFindFunction, CouldntFindImpl, CouldntResolveKind, IInfererDelegate, ITypingPassSolverError, ReturnTypeConflict}
import dev.vale.typing.names.{BuildingFunctionNameWithClosuredsT, IImplNameT, INameT, ITemplateNameT, IdT, ImplNameT, NameTranslator, ReachablePrototypeNameT, ResolvingEnvNameT, RuneNameT}
import dev.vale.typing.templata.{CoordListTemplataT, CoordTemplataT, ITemplataT, InterfaceDefinitionTemplataT, KindTemplataT, PrototypeTemplataT, RuntimeSizedArrayTemplateTemplataT, StaticSizedArrayTemplateTemplataT, StructDefinitionTemplataT}
import dev.vale.typing.types.{CoordT, ICitizenTT, ISubKindTT, ISuperKindTT, InterfaceTT, KindT, RuntimeSizedArrayTT, StaticSizedArrayTT, StructTT}

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
  reachableBounds: Vector[PrototypeTemplataT]
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
    element: CoordT):
  StaticSizedArrayTT

  def resolveRuntimeSizedArrayKind(
    coutputs: CompilerOutputs,
    type2: CoordT,
    arrayMutability: ITemplataT[MutabilityTemplataType]):
  RuntimeSizedArrayTT

  def resolveFunction(
    callingEnv: IInDenizenEnvironmentT,
    state: CompilerOutputs,
    range: List[RangeS],
    callLocation: LocationInDenizen,
    name: StrI,
    coords: Vector[CoordT],
    verifyConclusions: Boolean):
  Result[EvaluateFunctionSuccess, FindFunctionFailure]

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

  def solveComplete(
    envs: InferEnv, // See CSSNCE
    coutputs: CompilerOutputs,
    rules: Vector[IRulexSR],
    runeToType: Map[IRuneS, ITemplataType],
    invocationRange: List[RangeS],
    callLocation: LocationInDenizen,
    initialKnowns: Vector[InitialKnown],
    initialSends: Vector[InitialSend],
    verifyConclusions: Boolean,
    isRootSolve: Boolean,
    includeReachableBoundsForRunes: Vector[IRuneS]):
  Result[CompleteCompilerSolve, IIncompleteOrFailedCompilerSolve] = {
    val solver =
      makeSolver(envs, coutputs, rules, runeToType, invocationRange, initialKnowns, initialSends)

    continue(envs, coutputs, solver) match {
      case Ok(()) =>
      case Err(e) => return Err(e)
    }

    interpretResults(envs, coutputs, invocationRange, callLocation, runeToType, rules, verifyConclusions, isRootSolve, includeReachableBoundsForRunes, solver) match {
      case f @ FailedCompilerSolve(_, _, _) => Err(f)
      case i @ IncompleteCompilerSolve(_, _, _, _) => Err(i)
      case c @ CompleteCompilerSolve(_, _, _, _) => Ok(c)
    }
  }

  def solveExpectComplete(
    envs: InferEnv, // See CSSNCE
    coutputs: CompilerOutputs,
    rules: Vector[IRulexSR],
    runeToType: Map[IRuneS, ITemplataType],
    invocationRange: List[RangeS],
    callLocation: LocationInDenizen,
    initialKnowns: Vector[InitialKnown],
    initialSends: Vector[InitialSend],
    verifyConclusions: Boolean,
    isRootSolve: Boolean,
    includeReachableBoundsForRunes: Vector[IRuneS]):
  CompleteCompilerSolve = {

    val solver =
      makeSolver(envs, coutputs, rules, runeToType, invocationRange, initialKnowns, initialSends)

    continue(envs, coutputs, solver) match {
      case Ok(()) =>
      case Err(f @ FailedCompilerSolve(_, _, err)) => {
        throw CompileErrorExceptionT(typing.TypingPassSolverError(invocationRange, f))
      }
    }

    expectCompleteSolve(
      envs,
      coutputs,
      rules,
      runeToType,
      invocationRange,
      callLocation,
      verifyConclusions,
      isRootSolve,
      includeReachableBoundsForRunes,
      solver)
  }


  def expectCompleteSolve(
    envs: InferEnv,
    coutputs: CompilerOutputs,
    rules: Vector[IRulexSR],
    runeToType: Map[IRuneS, ITemplataType],
    invocationRange: List[RangeS],
    callLocation: LocationInDenizen,
    verifyConclusions: Boolean,
    isRootSolve: Boolean,
    includeReachableBoundsForRunes: Vector[IRuneS],
    solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError]
  ): CompleteCompilerSolve = {
    interpretResults(
      envs,
      coutputs,
      invocationRange,
      callLocation,
      runeToType,
      rules,
      verifyConclusions,
      isRootSolve,
      includeReachableBoundsForRunes,
      solver) match {
      case f@FailedCompilerSolve(_, _, err) => {
        throw CompileErrorExceptionT(typing.TypingPassSolverError(invocationRange, f))
      }
      case i@IncompleteCompilerSolve(_, _, _, _) => {
        throw CompileErrorExceptionT(typing.TypingPassSolverError(invocationRange, i))
      }
      case c@CompleteCompilerSolve(_, _, _, _) => c
    }
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

  def interpretResults(
    envs: InferEnv, // See CSSNCE
    state: CompilerOutputs,
    invocationRange: List[RangeS],
    callLocation: LocationInDenizen,
    runeToType: Map[IRuneS, ITemplataType],
    initialRules: Vector[IRulexSR],
    verifyConclusions: Boolean,
    isRootSolve: Boolean,
    includeReachableBoundsForRunes: Vector[IRuneS],
    solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError]):
  ICompilerSolverOutcome = {
    compilerSolver.interpretResults(runeToType, solver) match {
      case CompleteSolve(steps, conclusions) => {
        val reachableBounds =
          includeReachableBoundsForRunes
            .map(conclusions)
            .flatMap(conc => TemplataCompiler.getReachableBounds(interner, keywords, state, conc))
        val runeToFunctionBound =
          if (verifyConclusions) {
            checkTemplateInstantiations(envs, state, invocationRange, callLocation, initialRules, conclusions, reachableBounds, isRootSolve) match {
              case Ok(c) => vassertSome(c)
              case Err(e) => return FailedCompilerSolve(steps, Vector(), e)
            }
          } else {
            InstantiationBoundArgumentsT(Map(), Map())
          }
        CompleteCompilerSolve(steps, conclusions, runeToFunctionBound, reachableBounds)
      }
      case IncompleteSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
        if (verifyConclusions) {
          checkTemplateInstantiations(envs, state, invocationRange, callLocation, initialRules, incompleteConclusions, Vector(), isRootSolve) match {
            case Ok(c) =>
            case Err(e) => return FailedCompilerSolve(steps, unsolvedRules, e)
          }
        }
        IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions)
      }
      case FailedSolve(steps, unsolvedRules, error) => FailedCompilerSolve(steps, unsolvedRules, error)
    }
  }

  def checkTemplateInstantiations(
    envs: InferEnv, // See CSSNCE
    state: CompilerOutputs,
    ranges: List[RangeS],
    callLocation: LocationInDenizen,
    rules: Vector[IRulexSR],
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]],
    reachableBounds: Vector[PrototypeTemplataT],
    isRootSolve: Boolean):
  Result[Option[InstantiationBoundArgumentsT], ISolverError[IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError]] = {
    // This is a temporary env which contains all of our conclusions.
    // This is important if we want to resolve some sort of existing type, like how
    //   impl<T> Opt<T> for Some<T> where func drop(T)void;
    // will want to resolve that Some<T> and want it to see that there's a drop(T).
    //
    // However, we *dont* want to use this temporary env when imposing conditions on the caller.
    // If we have:
    //   func moo(x Bork<int>) { }
    //   struct Bork<T> where func drop(T)void { }
    // then when we're compiling moo's Bork<int>, we *dont* want the conclusions we just figured
    // out, because we'd see the temporary func drop(T) void that the CallSiteSR just conjured up.
    //
    // So, if we're invoking a template (like CallSR) then we want to use the temporary env...
    // ...but if we want to impose a restriction on above, we don't.
//    val callingEnv =
//      maybeCallingEnv match {
//        case None => return Ok(())
//        case Some(x) => x
//      }
//    val name = callingEnv.fullName.addStep(ResolvingEnvNameT())

    if (isRootSolve) {
      // If this is the original calling env, in other words, if we're the original caller for
      // this particular solve, then lets add all of our templatas to the environment.
      val originalCallingEnvWithBoundsAndUnverifiedConclusions =
        GeneralEnvironmentT.childOf(
          interner,
          envs.originalCallingEnv,
          envs.originalCallingEnv.id,
          conclusions
            .map({ case (nameS, templata) =>
              interner.intern(RuneNameT((nameS))) -> TemplataEnvEntry(templata)
            }).toVector ++
            // These are the bounds we pulled in from the parameters, return type, impl sub citizen, etc.
          reachableBounds.zipWithIndex.map({ case (reachableBound, index) =>
            interner.intern(ReachablePrototypeNameT(index)) -> TemplataEnvEntry(reachableBound)
          }))

      checkTemplateInstantiationsForEnv(
        originalCallingEnvWithBoundsAndUnverifiedConclusions, state, ranges, callLocation, rules, conclusions)
    } else {
      val envWithBounds =
        GeneralEnvironmentT.childOf(
          interner,
          envs.originalCallingEnv,
          envs.originalCallingEnv.id,
          // These are the bounds we pulled in from the parameters, return type, impl sub citizen, etc.
          reachableBounds.zipWithIndex.map({ case (reachableBound, index) =>
            interner.intern(ReachablePrototypeNameT(index)) -> TemplataEnvEntry(reachableBound)
          }).toVector)

      checkTemplateInstantiationsForEnv(
        envWithBounds, state, ranges, callLocation, rules, conclusions)
    }
  }

  private def checkTemplateInstantiationsForEnv(
    env: IInDenizenEnvironmentT, // See CSSNCE
    state: CompilerOutputs,
    ranges: List[RangeS],
    callLocation: LocationInDenizen,
    rules: Vector[IRulexSR],
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]]):
  Result[Option[InstantiationBoundArgumentsT], ISolverError[IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError]] = {
    // Check all template calls
    rules.foreach({
      case MaybeCoercingCallSR(range, _, templateRune, argRunes) => {
        checkTemplateCall(env, state, ranges, callLocation, range, templateRune, argRunes, conclusions) match {
          case Ok(()) =>
          case Err(e) => return Err(RuleError(CouldntResolveKind(e)))
        }
      }
      case CallSR(range, _, templateRune, argRunes) => {
        checkTemplateCall(env, state, ranges, callLocation, range, templateRune, argRunes, conclusions) match {
          case Ok(()) =>
          case Err(e) => return Err(RuleError(CouldntResolveKind(e)))
        }
      }
      case _ =>
    })

    val maybeRunesAndPrototypes =
      rules.collect({
        case r@ResolveSR(_, _, _, _, _) => {
          checkFunctionCall(env, state, ranges, callLocation, r, conclusions) match {
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
          checkImpl(env, state, ranges, callLocation, r, conclusions) match {
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
  def checkFunctionCall(
    callingEnv: IInDenizenEnvironmentT,
    state: CompilerOutputs,
    ranges: List[RangeS],
    callLocation: LocationInDenizen,
    c: ResolveSR,
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]]):
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
      delegate.resolveFunction(callingEnv, state, range :: ranges, callLocation, name, paramCoords, true) match {
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
  def checkImpl(
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
  def checkTemplateCall(
    callingEnv: IInDenizenEnvironmentT,
    state: CompilerOutputs,
    ranges: List[RangeS],
      callLocation: LocationInDenizen,
    range: RangeS,
    templateRune: RuneUsage,
    argRunes: Vector[RuneUsage],
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]]):
  Result[Unit, ResolveFailure[KindT]] = {
//  Result[Option[(IRuneS, PrototypeTemplata)], ISolverError[IRuneS, ITemplata[ITemplataType], ITypingPassSolverError]] = {
//     val CallSR(range, resultRune, templateRune, argRunes) = c

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
        delegate.resolveRuntimeSizedArrayKind(state, coord, mutability)
        Ok(())
      }
      case StaticSizedArrayTemplateTemplataT() => {
        val Vector(s, m, v, CoordTemplataT(coord)) = args
        val size = ITemplataT.expectInteger(s)
        val mutability = ITemplataT.expectMutability(m)
        val variability = ITemplataT.expectVariability(v)
        delegate.resolveStaticSizedArrayKind(state, mutability, variability, size, coord)
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