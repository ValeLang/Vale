package dev.vale.typing.infer

import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.ShareP
import dev.vale.postparsing.rules._
import dev.vale.{Err, Ok, RangeS, Result, vassert, vassertSome, vimpl, vwat}
import dev.vale.postparsing._
import dev.vale.solver.{CompleteSolve, FailedSolve, ISolveRule, ISolverError, ISolverOutcome, ISolverState, IStepState, IncompleteSolve, RuleError, Solver, SolverConflict}
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.ast.PrototypeT
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, IdT, FunctionNameT, IFunctionNameT, IImplNameT, INameT}
import dev.vale.typing.templata.{Conversions, CoordTemplata, PlaceholderTemplata, _}
import dev.vale.typing.types._
import dev.vale._
import dev.vale.postparsing.ArgumentRuneS
import dev.vale.postparsing.rules._
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.citizen.{IsntParent, ResolveFailure}
import dev.vale.typing.{CompilerOutputs, InferEnv, templata, types}
import dev.vale.typing.types._

import scala.collection.immutable.HashSet
import scala.collection.mutable

sealed trait ITypingPassSolverError
case class KindIsNotConcrete(kind: KindT) extends ITypingPassSolverError
case class KindIsNotInterface(kind: KindT) extends ITypingPassSolverError
case class KindIsNotStruct(kind: KindT) extends ITypingPassSolverError
case class CouldntFindFunction(range: List[RangeS], fff: FindFunctionFailure) extends ITypingPassSolverError
case class CouldntFindImpl(range: List[RangeS], fail: IsntParent) extends ITypingPassSolverError
case class CouldntResolveKind(
  rf: ResolveFailure[KindT]
) extends ITypingPassSolverError {
  vpass()
}
case class CantShareMutable(kind: KindT) extends ITypingPassSolverError
case class CantSharePlaceholder(kind: KindT) extends ITypingPassSolverError
case class BadIsaSubKind(kind: KindT) extends ITypingPassSolverError {
  vpass()
}
case class BadIsaSuperKind(kind: KindT) extends ITypingPassSolverError {
  vpass()
}
case class SendingNonCitizen(kind: KindT) extends ITypingPassSolverError {
  vpass()
}
case class CantCheckPlaceholder(range: List[RangeS]) extends ITypingPassSolverError
case class ReceivingDifferentOwnerships(params: Vector[(IRuneS, CoordT)]) extends ITypingPassSolverError
case class SendingNonIdenticalKinds(sendCoord: CoordT, receiveCoord: CoordT) extends ITypingPassSolverError
case class NoCommonAncestors(params: Vector[(IRuneS, CoordT)]) extends ITypingPassSolverError
case class LookupFailed(name: IImpreciseNameS) extends ITypingPassSolverError
case class NoAncestorsSatisfyCall(params: Vector[(IRuneS, CoordT)]) extends ITypingPassSolverError
case class CantDetermineNarrowestKind(kinds: Set[KindT]) extends ITypingPassSolverError
case class OwnershipDidntMatch(coord: CoordT, expectedOwnership: OwnershipT) extends ITypingPassSolverError
case class CallResultWasntExpectedType(expected: ITemplata[ITemplataType], actual: ITemplata[ITemplataType]) extends ITypingPassSolverError {
  vpass()
}
case class OneOfFailed(rule: OneOfSR) extends ITypingPassSolverError
case class IsaFailed(sub: KindT, suuper: KindT) extends ITypingPassSolverError
case class WrongNumberOfTemplateArgs(expectedNumArgs: Int) extends ITypingPassSolverError
case class FunctionDoesntHaveName(range: List[RangeS], name: IFunctionNameT) extends ITypingPassSolverError
case class CantGetComponentsOfPlaceholderPrototype(range: List[RangeS]) extends ITypingPassSolverError
case class ReturnTypeConflict(range: List[RangeS], expectedReturnType: CoordT, actual: PrototypeT) extends ITypingPassSolverError

trait IInfererDelegate {
//  def lookupMemberTypes(
//    state: CompilerOutputs,
//    kind: KindT,
//    // This is here so that the predictor can just give us however many things
//    // we expect.
//    expectedNumMembers: Int
//  ): Option[Vector[CoordT]]

  def getMutability(state: CompilerOutputs, kind: KindT): ITemplata[MutabilityTemplataType]

  def lookupTemplata(env: InferEnv, state: CompilerOutputs, range: List[RangeS], name: INameT): ITemplata[ITemplataType]

  def lookupTemplataImprecise(env: InferEnv, state: CompilerOutputs, range: List[RangeS], name: IImpreciseNameS): Option[ITemplata[ITemplataType]]

  def coerceToCoord(env: InferEnv, state: CompilerOutputs, range: List[RangeS], templata: ITemplata[ITemplataType]): ITemplata[ITemplataType]

  def isDescendant(env: InferEnv, state: CompilerOutputs, kind: KindT): Boolean
  def isAncestor(env: InferEnv, state: CompilerOutputs, kind: KindT): Boolean

  def sanityCheckConclusion(env: InferEnv, state: CompilerOutputs, rune: IRuneS, templata: ITemplata[ITemplataType]): Unit

  // See SFWPRL for how this is different from resolveStruct.
  def predictStruct(
    env: InferEnv,
    state: CompilerOutputs,
    templata: StructDefinitionTemplata,
    templateArgs: Vector[ITemplata[ITemplataType]]):
  (KindT)

  // See SFWPRL for how this is different from resolveInterface.
  def predictInterface(
    env: InferEnv,
    state: CompilerOutputs,
    templata: InterfaceDefinitionTemplata,
    templateArgs: Vector[ITemplata[ITemplataType]]):
  (KindT)

  def predictStaticSizedArrayKind(
    env: InferEnv,
    state: CompilerOutputs,
    mutability: ITemplata[MutabilityTemplataType],
    variability: ITemplata[VariabilityTemplataType],
    size: ITemplata[IntegerTemplataType],
    element: CoordT):
  StaticSizedArrayTT

  def predictRuntimeSizedArrayKind(env: InferEnv, state: CompilerOutputs, type2: CoordT, arrayMutability: ITemplata[MutabilityTemplataType]): RuntimeSizedArrayTT

  def getAncestors(env: InferEnv, coutputs: CompilerOutputs, descendant: KindT, includeSelf: Boolean):
  (Set[KindT])

  def isParent(
    env: InferEnv,
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    subKindTT: ISubKindTT,
    superKindTT: ISuperKindTT,
    includeSelf: Boolean):
  Option[ITemplata[ImplTemplataType]]

  def structIsClosure(state: CompilerOutputs, structTT: StructTT): Boolean

  def kindIsFromTemplate(
    state: CompilerOutputs,
    actualCitizenRef: KindT,
    expectedCitizenTemplata: ITemplata[ITemplataType]):
  Boolean

  def predictFunction(
    env: InferEnv,
    state: CompilerOutputs,
    functionRange: RangeS,
    name: StrI,
    paramCoords: Vector[CoordT],
    returnCoord: CoordT):
  PrototypeTemplata

  def assemblePrototype(
    env: InferEnv,
    state: CompilerOutputs,
    range: RangeS,
    name: StrI,
    coords: Vector[CoordT],
    returnType: CoordT):
  PrototypeT

  def assembleImpl(
    env: InferEnv,
    range: RangeS,
    subKind: KindT,
    superKind: KindT):
  IsaTemplata
}

class CompilerSolver(
  globalOptions: GlobalOptions,
  interner: Interner,
  delegate: IInfererDelegate
) {

  def getRunes(rule: IRulexSR): Vector[IRuneS] = {
    val result = rule.runeUsages.map(_.rune)

    if (globalOptions.sanityCheck) {
      val sanityChecked: Vector[RuneUsage] =
        rule match {
          case LookupSR(range, rune, literal) => Vector(rune)
          case LookupSR(range, rune, literal) => Vector(rune)
          case RuneParentEnvLookupSR(range, rune) => Vector(rune)
          case EqualsSR(range, left, right) => Vector(left, right)
          case DefinitionCoordIsaSR(range, result, sub, suuper) => Vector(result, sub, suuper)
          case CallSiteCoordIsaSR(range, result, sub, suuper) => result.toVector ++ Vector(sub, suuper)
          case KindComponentsSR(range, resultRune, mutabilityRune) => Vector(resultRune, mutabilityRune)
          case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => Vector(resultRune, ownershipRune, kindRune)
          case PrototypeComponentsSR(range, resultRune, paramsRune, returnRune) => Vector(resultRune, paramsRune, returnRune)
          case DefinitionFuncSR(range, resultRune, name, paramsListRune, returnRune) => Vector(resultRune, paramsListRune, returnRune)
          case CallSiteFuncSR(range, resultRune, name, paramsListRune, returnRune) => Vector(resultRune, paramsListRune, returnRune)
          case ResolveSR(range, resultRune, name, paramsListRune, returnRune) => Vector(resultRune, paramsListRune, returnRune)
          case OneOfSR(range, rune, literals) => Vector(rune)
          case IsConcreteSR(range, rune) => Vector(rune)
          case IsInterfaceSR(range, rune) => Vector(rune)
          case IsStructSR(range, rune) => Vector(rune)
          case CoerceToCoordSR(range, coordRune, kindRune) => Vector(coordRune, kindRune)
          case LiteralSR(range, rune, literal) => Vector(rune)
          case AugmentSR(range, resultRune, ownership, innerRune) => Vector(resultRune, innerRune)
          case CallSR(range, resultRune, templateRune, args) => Vector(resultRune, templateRune) ++ args
//          case PrototypeSR(range, resultRune, name, parameters, returnTypeRune) => Vector(resultRune) ++ parameters ++ Vector(returnTypeRune)
          case PackSR(range, resultRune, members) => Vector(resultRune) ++ members
//          case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => Vector(resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune)
//          case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) => Vector(resultRune, mutabilityRune, elementRune)
          //        case ManualSequenceSR(range, resultRune, elements) => Vector(resultRune) ++ elements
          //        case CoordListSR(range, resultRune, elements) => Vector(resultRune) ++ elements
          case CoordSendSR(range, senderRune, receiverRune) => Vector(senderRune, receiverRune)
          case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => Vector(resultRune, coordListRune)
        }
      vassert(result sameElements sanityChecked.map(_.rune))
    }
    result
  }

  def getPuzzles(rule: IRulexSR): Vector[Vector[IRuneS]] = {
    rule match {
      // This means we can solve this puzzle and dont need anything to do it.
      case LookupSR(range, _, _) => Vector(Vector())
      case RuneParentEnvLookupSR(range, rune) => Vector(Vector())
      case CallSR(range, resultRune, templateRune, args) => {
        Vector(
          Vector(templateRune.rune) ++ args.map(_.rune),
          // Do we really need to do
          //   Vector(resultRune.rune, templateRune.rune),
          // Because if we have X = T<A> and we know that X is a Moo<int>
          // then we can know T = Moo and A = int.
          // So maybe one day we can not require templateRune here.
          Vector(resultRune.rune, templateRune.rune))
      }
      case PackSR(range, resultRune, members) => Vector(Vector(resultRune.rune), members.map(_.rune))
      case KindComponentsSR(range, kindRune, mutabilityRune) => Vector(Vector(kindRune.rune))
      case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => Vector(Vector(resultRune.rune), Vector(ownershipRune.rune, kindRune.rune))
      case PrototypeComponentsSR(range, resultRune, paramsRune, returnRune) => Vector(Vector(resultRune.rune))
      case CallSiteFuncSR(range, resultRune, name, paramListRune, returnRune) => Vector(Vector(resultRune.rune))
      // Definition doesn't need the placeholder to be present, it's what populates the placeholder.
      case DefinitionFuncSR(range, placeholderRune, name, paramListRune, returnRune) => Vector(Vector(paramListRune.rune, returnRune.rune))
      case ResolveSR(range, resultRune, name, paramsListRune, returnRune) => Vector(Vector(paramsListRune.rune, returnRune.rune))
      case OneOfSR(range, rune, literals) => Vector(Vector(rune.rune))
      case EqualsSR(range, leftRune, rightRune) => Vector(Vector(leftRune.rune), Vector(rightRune.rune))
      case IsConcreteSR(range, rune) => Vector(Vector(rune.rune))
      case IsInterfaceSR(range, rune) => Vector(Vector(rune.rune))
      case IsStructSR(range, rune) => Vector(Vector(rune.rune))
      case CoerceToCoordSR(range, coordRune, kindRune) => Vector(Vector(coordRune.rune), Vector(kindRune.rune))
      case LiteralSR(range, rune, literal) => Vector(Vector())
      case AugmentSR(range, resultRune, ownership, innerRune) => Vector(Vector(innerRune.rune), Vector(resultRune.rune))
//      case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => Vector(Vector(resultRune.rune), Vector(mutabilityRune.rune, variabilityRune.rune, sizeRune.rune, elementRune.rune))
//      case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) => Vector(Vector(resultRune.rune), Vector(mutabilityRune.rune, elementRune.rune))
      // See SAIRFU, this will replace itself with other rules.
      case CoordSendSR(range, senderRune, receiverRune) => Vector(Vector(senderRune.rune), Vector(receiverRune.rune))
      case DefinitionCoordIsaSR(range, resultRune, senderRune, receiverRune) => Vector(Vector(senderRune.rune, receiverRune.rune))
      case CallSiteCoordIsaSR(range, resultRune, senderRune, receiverRune) => Vector(Vector(senderRune.rune, receiverRune.rune))
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => Vector(Vector(coordListRune.rune))
    }
  }

  def makeSolver(
    range: List[RangeS],
    env: InferEnv,
    state: CompilerOutputs,
    rules: IndexedSeq[IRulexSR],
    initialRuneToType: Map[IRuneS, ITemplataType],
    initiallyKnownRuneToTemplata: Map[IRuneS, ITemplata[ITemplataType]]):
  Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplata[ITemplataType], ITypingPassSolverError] = {

    rules.foreach(rule => rule.runeUsages.foreach(rune => vassert(initialRuneToType.contains(rune.rune))))

    // These two shouldn't both be in the rules, see SROACSD.
    vassert(
      rules.collect({ case CallSiteFuncSR(range, _, _, _, _) => }).isEmpty ||
        rules.collect({ case DefinitionFuncSR(range, _, _, _, _) => }).isEmpty)
    // These two shouldn't both be in the rules, see SROACSD.
    vassert(
      rules.collect({ case CallSiteCoordIsaSR(range, _, _, _) => }).isEmpty ||
        rules.collect({ case DefinitionCoordIsaSR(range, _, _, _) => }).isEmpty)

    initiallyKnownRuneToTemplata.foreach({ case (rune, templata) =>
      if (globalOptions.sanityCheck) {
        delegate.sanityCheckConclusion(env, state, rune, templata)
      }
      vassert(templata.tyype == vassertSome(initialRuneToType.get(rune)))
    })

    val solver =
      new Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplata[ITemplataType], ITypingPassSolverError](
        globalOptions.sanityCheck,
        globalOptions.useOptimizedSolver,
        interner,
        (rule: IRulexSR) => getPuzzles(rule),
        getRunes,
        new CompilerRuleSolver(globalOptions.sanityCheck, interner, delegate, initialRuneToType),
        range,
        rules,
        initiallyKnownRuneToTemplata,
        initialRuneToType.keys.toVector.distinct)

    solver
  }

  // During the solve, we postponed resolving structs and interfaces, see SFWPRL.
  // Caller should remember to do that!
  def continue(
    env: InferEnv,
    state: CompilerOutputs,
    solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplata[ITemplataType], ITypingPassSolverError]):
  Result[Unit, FailedSolve[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError]] = {
    while ( {
      solver.advance(env, state) match {
        case Ok(continue) => continue
        case Err(f@FailedSolve(_, _, _)) => return Err(f)
      }
    }) {}
    // If we get here, then there's nothing more the solver can do.
    Ok(Unit)
  }

  def interpretResults(
    runeToType: Map[IRuneS, ITemplataType],
    solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplata[ITemplataType], ITypingPassSolverError]):
  ISolverOutcome[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError] = {
    val stepsStream = solver.getSteps().toStream
    val conclusionsStream = solver.userifyConclusions().toMap

    val conclusions = conclusionsStream.toMap
    val allRunes = runeToType.keySet ++ solver.getAllRunes().map(solver.getUserRune)

    // During the solve, we postponed resolving structs and interfaces, see SFWPRL.
    // Caller should remember to do that!
    if ((allRunes -- conclusions.keySet).nonEmpty) {
      IncompleteSolve(
        stepsStream,
        solver.getUnsolvedRules(),
        allRunes -- conclusions.keySet,
        conclusions)
    } else {
      CompleteSolve(stepsStream, conclusions)
    }
  }
}

class CompilerRuleSolver(
  sanityCheck: Boolean,
    interner: Interner,
    delegate: IInfererDelegate,
    runeToType: Map[IRuneS, ITemplataType])
  extends ISolveRule[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplata[ITemplataType], ITypingPassSolverError] {

  override def sanityCheckConclusion(env: InferEnv, state: CompilerOutputs, rune: IRuneS, conclusion: ITemplata[ITemplataType]): Unit = {
    delegate.sanityCheckConclusion(env, state, rune, conclusion)
  }

  override def complexSolve(
      state: CompilerOutputs,
      env: InferEnv,
      solverState: ISolverState[IRulexSR, IRuneS, ITemplata[ITemplataType]],
      stepState: IStepState[IRulexSR, IRuneS, ITemplata[ITemplataType]]):
  Result[Unit, ISolverError[IRuneS, ITemplata[ITemplataType], ITypingPassSolverError]] = {
    val equivalencies = new Equivalencies(solverState.getUnsolvedRules())

    val unsolvedRules = solverState.getUnsolvedRules()
    val (unsolvedReceiverRunes, ranges) =
      unsolvedRules.collect({
        case CoordSendSR(range, _, receiverRune) => (receiverRune.rune, range)
        // We don't do this for DefinitionCoordIsaSR, see RRBFS.
        // case DefinitionCoordIsaSR(range, _, _, receiverRune) => (receiverRune.rune, range)
        case CallSiteCoordIsaSR(range, _, _, receiverRune) => (receiverRune.rune, range)
      }).unzip
    val receiverRunes =
      equivalencies.getKindEquivalentRunes(unsolvedReceiverRunes)

    val newConclusions =
      receiverRunes.flatMap(receiver => {
        val runesSendingToThisReceiver =
          equivalencies.getKindEquivalentRunes(
            unsolvedRules.collect({
              case CoordSendSR(range, s, r) if r.rune == receiver => s.rune
              // We don't do this for DefinitionCoordIsaSR, see RRBFS.
              // case DefinitionCoordIsaSR(range, _, s, r) if r.rune == receiver => s.rune
              case CallSiteCoordIsaSR(range, _, s, r) if r.rune == receiver => s.rune
            }))
        val callRules =
          unsolvedRules.collect({ case z @ CallSR(range, r, _, _) if equivalencies.getKindEquivalentRunes(r.rune).contains(receiver) => z })
        val senderConclusions =
          runesSendingToThisReceiver
            .flatMap(senderRune => solverState.getConclusion(senderRune).map(senderRune -> _))
            .map({
              case (senderRune, CoordTemplata(coord)) => (senderRune -> coord)
              case other => vwat(other)
            })
            .toVector
        val callTemplates =
          equivalencies.getKindEquivalentRunes(
            callRules.map(_.templateRune.rune))
            .flatMap(solverState.getConclusion)
            .toVector
        vassert(callTemplates.distinct.size <= 1)
        // If true, there are some senders/constraints we don't know yet, so lets be
        // careful to not assume between any possibilities below.
        val allSendersKnown = senderConclusions.size == runesSendingToThisReceiver.size
        val allCallsKnown = callRules.size == callTemplates.size
        solveReceives(env, state, senderConclusions, callTemplates, allSendersKnown, allCallsKnown) match {
          case Err(e) => return Err(RuleError(e))
          case Ok(None) => None
          case Ok(Some(receiverInstantiationKind)) => {
            // We know the kind, but to really know the coord we have to look at all the rules that
            // factored into it, and may even have to default to something else.

            val possibleCoords =
              unsolvedRules.collect({
                case AugmentSR(range, resultRune, ownership, innerRune)
                  if resultRune.rune == receiver => {
                  types.CoordT(
                    Conversions.evaluateOwnership(ownership),
                    receiverInstantiationKind)
                }
              }) ++
                senderConclusions.map(_._2).map({ case CoordT(ownership, _) =>
                  types.CoordT(ownership, receiverInstantiationKind)
                })
            if (possibleCoords.nonEmpty) {
              val ownership =
                possibleCoords.map(_.ownership).distinct match {
                  case Vector() => vwat()
                  case Vector(ownership) => ownership
                  case _ => return Err(RuleError(ReceivingDifferentOwnerships(senderConclusions)))
                }
              Some(receiver -> CoordTemplata(types.CoordT(ownership, receiverInstantiationKind)))
            } else {
              // Just conclude a kind, which will coerce to an owning coord, and hope it's right.
              Some(receiver -> templata.KindTemplata(receiverInstantiationKind))
            }
          }
        }
      }).toMap

    newConclusions.foreach({ case (rune, conclusion) =>
      stepState.concludeRune[ITypingPassSolverError](ranges.head :: env.parentRanges, rune, conclusion)
    })

    Ok(())
  }

  private def solveReceives(
    env: InferEnv,
    state: CompilerOutputs,
    senders: Vector[(IRuneS, CoordT)],
    callTemplates: Vector[ITemplata[ITemplataType]],
    allSendersKnown: Boolean,
    allCallsKnown: Boolean):
  Result[Option[KindT], ITypingPassSolverError] = {
    val senderKinds = senders.map(_._2.kind)
    if (senderKinds.isEmpty) {
      return Ok(None)
    }

    // For example [Flamethrower, Rockets] becomes [[Flamethrower, IWeapon, ISystem], [Rockets, IWeapon, ISystem]]
    val senderAncestorLists = senderKinds.map(delegate.getAncestors(env, state, _, true))
    // Calculates the intersection of them all, eg [IWeapon, ISystem]
    val commonAncestors = senderAncestorLists.reduce(_.intersect(_))
    if (commonAncestors.size == 0) {
      return Err(NoCommonAncestors(senders))
    }
    // Filter by any call templates. eg if there's a X = ISystem:Y call, then we're now [ISystem]
    val commonAncestorsCallConstrained =
      if (callTemplates.isEmpty) {
        commonAncestors
      } else {
        commonAncestors.filter(ancestor => callTemplates.exists(template => delegate.kindIsFromTemplate(state,ancestor, template)))
      }

    val narrowedCommonAncestor =
      if (commonAncestorsCallConstrained.size == 0) {
        // If we get here, it means we passed in a bunch of nonsense that doesn't match our Call rules.
        // For example, passing in a Some<T> when a List<T> is expected.
        return Err(NoAncestorsSatisfyCall(senders))
      } else if (commonAncestorsCallConstrained.size == 1) {
        // If we get here, it doesn't matter if there are any other senders or calls, we know
        // it has to be this.
        // If we're wrong, it will be doublechecked by the solver anyway.
        commonAncestorsCallConstrained.head
      } else {
        if (!allSendersKnown) {
          // There are some senders out there, which might force us to choose one of the ancestors.
          // We don't know them yet, so we can't conclude anything.
          return Ok(None)
        }
        if (!allCallsKnown) {
          // There are some calls out there, which will determine which one of the possibilities it is.
          // We don't know them yet, so we can't conclude anything.
          return Ok(None)
        }
        // If there are multiple, like [IWeapon, ISystem], get rid of any that are parents of others, now [IWeapon].
        narrow(env, state, commonAncestorsCallConstrained) match {
          case Ok(x) => x
          case Err(e) => return Err(e)
        }
      }
    Ok(Some(narrowedCommonAncestor))
  }

  def narrow(
    env: InferEnv,
    state: CompilerOutputs,
    kinds: Set[KindT]):
  Result[KindT, ITypingPassSolverError] = {
    vassert(kinds.size > 1)
    val narrowedAncestors = mutable.HashSet[KindT]()
    narrowedAncestors ++= kinds
    // Remove anything that's an ancestor of something else in the set
    kinds.foreach(kind => {
      narrowedAncestors --= delegate.getAncestors(env, state, kind, false)
    })
    if (narrowedAncestors.size == 0) {
      vwat() // Shouldnt happen
    } else if (narrowedAncestors.size == 1) {
      Ok(narrowedAncestors.head)
    } else {
      Err(CantDetermineNarrowestKind(narrowedAncestors.toSet))
    }
  }

  override def solve(
    state: CompilerOutputs,
    env: InferEnv,
    solverState: ISolverState[IRulexSR, IRuneS, ITemplata[ITemplataType]],
    ruleIndex: Int,
    rule: IRulexSR,
    stepState: IStepState[IRulexSR, IRuneS, ITemplata[ITemplataType]]):
  Result[Unit, ISolverError[IRuneS, ITemplata[ITemplataType], ITypingPassSolverError]] = {
    solveRule(state, env, ruleIndex, rule, new IStepState[IRulexSR, IRuneS, ITemplata[ITemplataType]] {
      override def addRule(rule: IRulexSR): Unit = stepState.addRule(rule)
      override def getConclusion(rune: IRuneS): Option[ITemplata[ITemplataType]] = stepState.getConclusion(rune)
      override def getUnsolvedRules(): Vector[IRulexSR] = stepState.getUnsolvedRules()
      override def concludeRune[ErrType](rangeS: List[RangeS], rune: IRuneS, conclusion: ITemplata[ITemplataType]): Unit = {
//        val coerced =
//          delegate.coerce(
//            env,
//            state,
//            rangeS,
//            vassertSome(runeToType.get(rune)),
//            conclusion)
        vassert(conclusion.tyype == vassertSome(runeToType.get(rune)))
        stepState.concludeRune[ErrType](rangeS, rune, conclusion)
      }
    }) match {
      case Ok(x) => Ok(x)
      case Err(e) => Err(RuleError(e))
    }
  }

  private def solveRule(
    state: CompilerOutputs,
    env: InferEnv,
    ruleIndex: Int,
    rule: IRulexSR,
    stepState: IStepState[IRulexSR, IRuneS, ITemplata[ITemplataType]]):
  // One might expect us to return the conclusions in this Result. Instead we take in a
  // lambda to avoid intermediate allocations, for speed.
  Result[Unit, ITypingPassSolverError] = {
    rule match {
      case KindComponentsSR(range, kindRune, mutabilityRune) => {
        val KindTemplata(kind) = vassertSome(stepState.getConclusion(kindRune.rune))
        val mutability = delegate.getMutability(state, kind)
        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
        Ok(())
      }
      case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => {
        stepState.getConclusion(resultRune.rune) match {
          case None => {
            val OwnershipTemplata(ownership) = vassertSome(stepState.getConclusion(ownershipRune.rune))
            val KindTemplata(kind) = vassertSome(stepState.getConclusion(kindRune.rune))
            val newCoord =
              delegate.getMutability(state, kind) match {
                case MutabilityTemplata(ImmutableT) => CoordT(ShareT, kind)
                case MutabilityTemplata(MutableT) | PlaceholderTemplata(_, MutabilityTemplataType()) => {
                  CoordT(ownership, kind)
                }
              }
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, CoordTemplata(newCoord))
            Ok(())
          }
          case Some(coord) => {
            val CoordTemplata(CoordT(ownership, kind)) = coord
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, ownershipRune.rune, OwnershipTemplata(ownership))
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, kindRune.rune, KindTemplata(kind))
            Ok(())
          }
        }
      }
      case PrototypeComponentsSR(range, resultRune, ownershipRune, kindRune) => {
        val PrototypeTemplata(_, prototype) = vassertSome(stepState.getConclusion(resultRune.rune))
        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, ownershipRune.rune, CoordListTemplata(prototype.paramTypes))
        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, kindRune.rune, CoordTemplata(prototype.returnType))
        Ok(())
      }
      case ResolveSR(range, resultRune, name, paramListRune, returnRune) => {
        // If we're here, then we're resolving a prototype.
        // This happens at the call-site.
        // The function (or struct) can either supply a default resolve rule (usually
        // via the `func moo(int)void` syntax) or let the caller pass it in.

        val CoordListTemplata(paramCoords) = vassertSome(stepState.getConclusion(paramListRune.rune))
        val CoordTemplata(returnCoord) = vassertSome(stepState.getConclusion(returnRune.rune))
        // We only pretend this function exists for now, and postpone actually resolving it until later, see SFWPRL.
        val prototypeTemplata = delegate.predictFunction(env, state, range, name, paramCoords, returnCoord)
        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, prototypeTemplata)
        Ok(())
      }
      case CallSiteFuncSR(range, prototypeRune, name, paramListRune, returnRune) => {
        // If we're here, then we're solving in the callsite, not the definition.
        // This should look up a function with that name and param list, and make sure
        // its return matches.

        vassertSome(stepState.getConclusion(prototypeRune.rune)) match {
          case PrototypeTemplata(range, prototype) => {
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges,
              paramListRune.rune, CoordListTemplata(prototype.paramTypes))
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges,
              returnRune.rune, CoordTemplata(prototype.returnType))
          }
          case _ => {
            return Err(CantCheckPlaceholder(range :: env.parentRanges))
          }
        }

        Ok(())
      }
      case DefinitionFuncSR(range, resultRune, name, paramListRune, returnRune) => {
        // If we're here, then we're solving in the definition, not the callsite.
        // Skip checking that they match, just assume they do.

        val CoordListTemplata(paramCoords) = vassertSome(stepState.getConclusion(paramListRune.rune))
        val CoordTemplata(returnType) = vassertSome(stepState.getConclusion(returnRune.rune))

        // Now introduce a prototype that lets us call it with this new name, that we
        // can call it by.
        val newPrototype =
          delegate.assemblePrototype(env, state, range, name, paramCoords, returnType)

        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges,
          resultRune.rune, PrototypeTemplata(range, newPrototype))
        Ok(())
      }
      case CallSiteCoordIsaSR(range, resultRune, subRune, superRune) => {
        val CoordTemplata(subCoord) =
          vassertSome(stepState.getConclusion(subRune.rune))
        val CoordTemplata(superCoord) =
          vassertSome(stepState.getConclusion(superRune.rune))

        val resultingIsaTemplata =
          if (subCoord == superCoord) {
            delegate.assembleImpl(env, range, subCoord.kind, superCoord.kind)
          } else if (subCoord.kind match { case NeverT(_) => true case _ => false }) {
            delegate.assembleImpl(env, range, subCoord.kind, superCoord.kind)
          } else {
            val subKind =
              subCoord.kind match {
                case x : ISubKindTT => x
                case other => return Err(BadIsaSubKind(other))
              }
            val superKind =
              superCoord.kind match {
                case x : ISuperKindTT => x
                case other => return Err(BadIsaSuperKind(other))
              }
            delegate.isParent(env, state, env.parentRanges, subKind, superKind, true) match {
              case None => return Err(IsaFailed(subKind, superKind))
              case Some(implTemplata) => implTemplata
            }
          }

        resultRune match {
          case Some(resultRune) => {
            stepState.concludeRune[ITypingPassSolverError](
              range :: env.parentRanges, resultRune.rune, resultingIsaTemplata)
          }
          case None =>
        }
        Ok(())
      }
      case DefinitionCoordIsaSR(range, resultRune, subRune, superRune) => {
        // If we're here, then we're solving in the definition, not the callsite.
        // Skip checking that they match, just assume they do.

        val CoordTemplata(CoordT(_, subKindUnchecked)) = vassertSome(stepState.getConclusion(subRune.rune))
        val CoordTemplata(CoordT(_, superKindUnchecked)) = vassertSome(stepState.getConclusion(superRune.rune))

        val subKind =
          subKindUnchecked match {
            case z : ISubKindTT => z
            case _ => return Err(BadIsaSubKind(subKindUnchecked))
          }
        val superKind =
          superKindUnchecked match {
            case z : ISuperKindTT => z
            case _ => return Err(BadIsaSuperKind(superKindUnchecked))
          }

        // Now introduce an impl so that we can later know sub implements super.
        val newImpl = delegate.assembleImpl(env, range, subKind, superKind)

        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges,
          resultRune.rune, newImpl)
        Ok(())
      }
      case EqualsSR(range, leftRune, rightRune) => {
        stepState.getConclusion(leftRune.rune) match {
          case None => {
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, leftRune.rune, vassertSome(stepState.getConclusion(rightRune.rune)))
            Ok(())
          }
          case Some(left) => {
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rightRune.rune, left)
            Ok(())
          }
        }
      }
      case CoordSendSR(range, senderRune, receiverRune) => {
        // See IRFU and SRCAMP for what's going on here.
        stepState.getConclusion(receiverRune.rune) match {
          case None => {
            val CoordTemplata(coord) = vassertSome(stepState.getConclusion(senderRune.rune))
            if (delegate.isDescendant(env, state, coord.kind)) {
              // We know that the sender can be upcast, so we can't shortcut.
              // We need to wait for the receiver rune to know what to do.
              stepState.addRule(CallSiteCoordIsaSR(range, None, senderRune, receiverRune))
              Ok(())
            } else {
              // We're sending something that can't be upcast, so both sides are definitely the same type.
              // We can shortcut things here, even knowing only the sender's type.
              stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, receiverRune.rune, CoordTemplata(coord))
              Ok(())
            }
          }
          case Some(CoordTemplata(coord)) => {
            if (delegate.isAncestor(env, state, coord.kind)) {
              // We know that the receiver is an interface, so we can't shortcut.
              // We need to wait for the sender rune to be able to confirm the sender
              // implements the receiver.
              stepState.addRule(CallSiteCoordIsaSR(range, None, senderRune, receiverRune))
              Ok(())
            } else {
              // We're receiving a concrete type, so both sides are definitely the same type.
              // We can shortcut things here, even knowing only the receiver's type.
              stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, senderRune.rune, CoordTemplata(coord))
              Ok(())
            }
          }
          case other => vwat(other)
        }
      }
      case rule @ OneOfSR(range, resultRune, literals) => {
        val result = vassertSome(stepState.getConclusion(resultRune.rune))
        val templatas = literals.map(literalToTemplata)
        if (templatas.contains(result)) {
          Ok(())
        } else {
          Err(OneOfFailed(rule))
        }
      }
      case rule @ IsConcreteSR(range, rune) => {
        val templata = vassertSome(stepState.getConclusion(rune.rune))
        templata match {
          case KindTemplata(kind) => {
            kind match {
              case InterfaceTT(_) => {
                Err(KindIsNotConcrete(kind))
              }
              case _ => Ok(())
            }
          }
          case _ => vwat() // Should be impossible, all template rules are type checked
        }
      }
      case rule @ IsInterfaceSR(range, rune) => {
        val templata = vassertSome(stepState.getConclusion(rune.rune))
        templata match {
          case KindTemplata(kind) => {
            kind match {
              case InterfaceTT(_) => Ok(())
              case _ => Err(KindIsNotInterface(kind))
            }
          }
          case _ => vwat() // Should be impossible, all template rules are type checked
        }
      }
      case IsStructSR(range, rune) => {
        val templata = vassertSome(stepState.getConclusion(rune.rune))
        templata match {
          case KindTemplata(kind) => {
            kind match {
              case StructTT(_) => Ok(())
              case _ => Err(KindIsNotStruct(kind))
            }
          }
          case _ => vwat() // Should be impossible, all template rules are type checked
        }
      }
      case CoerceToCoordSR(range, coordRune, kindRune) => {
        stepState.getConclusion(kindRune.rune) match {
          case None => {
            val CoordTemplata(coord) = vassertSome(stepState.getConclusion(coordRune.rune))
            coord.ownership match {
              case OwnT | ShareT => {
                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, kindRune.rune, KindTemplata(coord.kind))
                Ok(())
              }
              case _ => {
                return Err(OwnershipDidntMatch(coord, OwnT))
              }
            }
          }
          case Some(kind) => {
            val coerced = delegate.coerceToCoord(env, state, range :: env.parentRanges, kind)
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, coordRune.rune, coerced)
            Ok(())
          }
        }
      }
      case LiteralSR(range, rune, literal) => {
        val templata = literalToTemplata(literal)
        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templata)
        Ok(())
      }
      case LookupSR(range, rune, name) => {
        val result =
          delegate.lookupTemplataImprecise(env, state, range :: env.parentRanges, name) match {
            case None => return Err(LookupFailed(name))
            case Some(x) => x
          }
        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, result)
        Ok(())
      }
      case RuneParentEnvLookupSR(range, rune) => {
        // This rule does nothing, it was actually preprocessed.
        Ok(())
      }
      case AugmentSR(range, resultRune, augmentOwnership, innerRune) => {
        stepState.getConclusion(innerRune.rune) match {
          case Some(CoordTemplata(initialCoord)) => {
            val newCoord =
              delegate.getMutability(state, initialCoord.kind) match {
                case PlaceholderTemplata(_, MutabilityTemplataType()) => {
                  if (augmentOwnership == ShareP) {
                    return Err(CantSharePlaceholder(initialCoord.kind))
                  }
                  initialCoord
                    .copy(ownership = Conversions.evaluateOwnership(augmentOwnership))
                }
                case MutabilityTemplata(MutableT) => {
                  if (augmentOwnership == ShareP) {
                    return Err(CantShareMutable(initialCoord.kind))
                  }
                  initialCoord
                    .copy(ownership = Conversions.evaluateOwnership(augmentOwnership))
                }
                case MutabilityTemplata(ImmutableT) => initialCoord
              }
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, CoordTemplata(newCoord))
            Ok(())
          }
          case None => {
            val CoordTemplata(initialCoord) = vassertSome(stepState.getConclusion(resultRune.rune))
            val newCoord =
              delegate.getMutability(state, initialCoord.kind) match {
                case PlaceholderTemplata(_, _) | MutabilityTemplata(MutableT) => {
                  if (augmentOwnership == ShareP) {
                    return Err(CantShareMutable(initialCoord.kind))
                  }
                  if (initialCoord.ownership != Conversions.evaluateOwnership(augmentOwnership)) {
                    return Err(OwnershipDidntMatch(initialCoord, Conversions.evaluateOwnership(augmentOwnership)))
                  }
                  initialCoord.copy(ownership = OwnT)
                }
                case MutabilityTemplata(ImmutableT) => initialCoord
              }
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, innerRune.rune, CoordTemplata(newCoord))
            Ok(())
          }
        }

      }
      case PackSR(range, resultRune, memberRunes) => {
        stepState.getConclusion(resultRune.rune) match {
          case None => {
            val members =
              memberRunes.map(memberRune => {
                val CoordTemplata(coord) = vassertSome(stepState.getConclusion(memberRune.rune))
                coord
              })
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, CoordListTemplata(members.toVector))
            Ok(())
          }
          case Some(CoordListTemplata(members)) => {
            vassert(members.size == memberRunes.size)
            memberRunes.zip(members).foreach({ case (rune, coord) =>
              stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templata.CoordTemplata(coord))
            })
            Ok(())
          }
        }
      }
//      case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => {
//        stepState.getConclusion(resultRune.rune) match {
//          case None => {
//            val mutability = ITemplata.expectMutability(vassertSome(stepState.getConclusion(mutabilityRune.rune)))
//            val variability = ITemplata.expectVariability(vassertSome(stepState.getConclusion(variabilityRune.rune)))
//            val size = ITemplata.expectInteger(vassertSome(stepState.getConclusion(sizeRune.rune)))
//            val CoordTemplata(element) = vassertSome(stepState.getConclusion(elementRune.rune))
//            val arrKind =
//              delegate.predictStaticSizedArrayKind(env, state, mutability, variability, size, element)
//            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, KindTemplata(arrKind))
//            Ok(())
//          }
//          case Some(result) => {
//            result match {
//              case KindTemplata(contentsStaticSizedArrayTT(size, mutability, variability, elementType)) => {
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplata(elementType))
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, sizeRune.rune, size)
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, variabilityRune.rune, variability)
//                Ok(())
//              }
//              case CoordTemplata(CoordT(OwnT | ShareT, contentsStaticSizedArrayTT(size, mutability, variability, elementType))) => {
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplata(elementType))
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, sizeRune.rune, size)
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, variabilityRune.rune, variability)
//                Ok(())
//              }
//              case _ => return Err(CallResultWasntExpectedType(StaticSizedArrayTemplateTemplata(), result))
//            }
//          }
//        }
//      }
//      case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) => {
//        stepState.getConclusion(resultRune.rune) match {
//          case None => {
//            val mutability = ITemplata.expectMutability(vassertSome(stepState.getConclusion(mutabilityRune.rune)))
//            val CoordTemplata(element) = vassertSome(stepState.getConclusion(elementRune.rune))
//            val arrKind =
//              delegate.predictRuntimeSizedArrayKind(env, state, element, mutability)
//            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, KindTemplata(arrKind))
//            Ok(())
//          }
//          case Some(result) => {
//            result match {
//              case KindTemplata(contentsRuntimeSizedArrayTT(mutability, elementType)) => {
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplata(elementType))
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
//                Ok(())
//              }
//              case CoordTemplata(CoordT(OwnT | ShareT, contentsRuntimeSizedArrayTT(mutability, elementType))) => {
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplata(elementType))
//                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
//                Ok(())
//              }
//              case _ => return Err(CallResultWasntExpectedType(RuntimeSizedArrayTemplateTemplata(), result))
//            }
//          }
//        }
//      }
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => {
        val CoordListTemplata(coords) = vassertSome(stepState.getConclusion(coordListRune.rune))
        if (coords.forall(_.ownership == ShareT)) {
          stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, MutabilityTemplata(ImmutableT))
        } else {
          stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, MutabilityTemplata(MutableT))
        }
        Ok(())
      }
      case CallSR(range, resultRune, templateRune, argRunes) => {
        stepState.getConclusion(resultRune.rune) match {
          case Some(result) => {
            val template = vassertSome(stepState.getConclusion(templateRune.rune))
            template match {
              case RuntimeSizedArrayTemplateTemplata() => {
                result match {
                  case CoordTemplata(CoordT(ShareT | OwnT, contentsRuntimeSizedArrayTT(mutability, memberType))) => {
                    if (argRunes.size != 2) {
                      return Err(WrongNumberOfTemplateArgs(2))
                    }
                    val Vector(mutabilityRune, elementRune) = argRunes
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplata(memberType))
                    Ok(())
                  }
                  case KindTemplata(contentsRuntimeSizedArrayTT(mutability, memberType)) => {
                    val Vector(mutabilityRune, elementRune) = argRunes
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplata(memberType))
                    Ok(())
                  }
                  case _ => return Err(CallResultWasntExpectedType(template, result))
                }
              }
              case StaticSizedArrayTemplateTemplata() => {
                result match {
                  case CoordTemplata(CoordT(ShareT | OwnT, contentsStaticSizedArrayTT(size, mutability, variability, memberType))) => {
                    if (argRunes.size != 4) {
                      return Err(WrongNumberOfTemplateArgs(4))
                    }
                    val Vector(sizeRune, mutabilityRune, variabilityRune, elementRune) = argRunes
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, sizeRune.rune, size)
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, variabilityRune.rune, variability)
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplata(memberType))
                    Ok(())
                  }
                  case KindTemplata(contentsStaticSizedArrayTT(size, mutability, variability, memberType)) => {
                    if (argRunes.size != 4) {
                      return Err(WrongNumberOfTemplateArgs(4))
                    }
                    val Vector(sizeRune, mutabilityRune, variabilityRune, elementRune) = argRunes
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, sizeRune.rune, size)
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, variabilityRune.rune, variability)
                    stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplata(memberType))
                    Ok(())
                  }
                  case _ => return Err(CallResultWasntExpectedType(template, result))
                }
              }
              case it@InterfaceDefinitionTemplata(_, _) => {
                result match {
                  case KindTemplata(interface@InterfaceTT(_)) => {
                    if (!delegate.kindIsFromTemplate(state, interface, it)) {
                      return Err(CallResultWasntExpectedType(it, result))
                    }
                    vassert(argRunes.size == interface.fullName.localName.templateArgs.size)
                    argRunes.zip(interface.fullName.localName.templateArgs).foreach({ case (rune, templateArg) =>
                      stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
                    })
                    Ok(())
                  }
                  case CoordTemplata(CoordT(OwnT | ShareT, interface@InterfaceTT(_))) => {
                    if (!delegate.kindIsFromTemplate(state, interface, it)) {
                      return Err(CallResultWasntExpectedType(it, result))
                    }
                    vassert(argRunes.size == interface.fullName.localName.templateArgs.size)
                    argRunes.zip(interface.fullName.localName.templateArgs).foreach({ case (rune, templateArg) =>
                      stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
                    })
                    Ok(())
                  }
                  case _ => return Err(CallResultWasntExpectedType(template, result))
                }
              }
              case it@KindTemplata(templateInterface@InterfaceTT(_)) => {
                result match {
                  case KindTemplata(instantiationInterface@InterfaceTT(_)) => {
                    if (templateInterface != instantiationInterface) {
                      return Err(CallResultWasntExpectedType(it, result))
                    }
                    argRunes.zip(instantiationInterface.fullName.localName.templateArgs).foreach({ case (rune, templateArg) =>
                      stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
                    })
                    Ok(())
                  }
                  case CoordTemplata(CoordT(OwnT | ShareT, instantiationInterface@InterfaceTT(_))) => {
                    if (templateInterface != instantiationInterface) {
                      return Err(CallResultWasntExpectedType(it, result))
                    }
                    argRunes.zip(instantiationInterface.fullName.localName.templateArgs).foreach({ case (rune, templateArg) =>
                      stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
                    })
                    Ok(())
                  }
                  case _ => return Err(CallResultWasntExpectedType(template, result))
                }
              }
              case st@StructDefinitionTemplata(_, _) => {
                result match {
                  case KindTemplata(struct@StructTT(_)) => {
                    if (!delegate.kindIsFromTemplate(state, struct, st)) {
                      return Err(CallResultWasntExpectedType(st, result))
                    }
                    vassert(argRunes.size == struct.fullName.localName.templateArgs.size)
                    argRunes.zip(struct.fullName.localName.templateArgs).foreach({ case (rune, templateArg) =>
                      stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
                    })
                    Ok(())
                  }
                  case CoordTemplata(CoordT(OwnT | ShareT, struct@StructTT(_))) => {
                    if (!delegate.kindIsFromTemplate(state, struct, st)) {
                      return Err(CallResultWasntExpectedType(st, result))
                    }
                    vassert(argRunes.size == struct.fullName.localName.templateArgs.size)
                    argRunes.zip(struct.fullName.localName.templateArgs).foreach({ case (rune, templateArg) =>
                      stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
                    })
                    Ok(())
                  }
                  case _ => return Err(CallResultWasntExpectedType(template, result))
                }
              }
              case it@KindTemplata(structTT@StructTT(_)) => {
                result match {
                  case KindTemplata(instantiationStruct@StructTT(_)) => {
                    if (structTT != instantiationStruct) {
                      return Err(CallResultWasntExpectedType(it, result))
                    }
                    argRunes.zip(instantiationStruct.fullName.localName.templateArgs).foreach({ case (rune, templateArg) =>
                      stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
                    })
                    Ok(())
                  }
                  case CoordTemplata(CoordT(OwnT | ShareT, instantiationStruct@StructTT(_))) => {
                    if (structTT != instantiationStruct) {
                      return Err(CallResultWasntExpectedType(it, result))
                    }
                    argRunes.zip(instantiationStruct.fullName.localName.templateArgs).foreach({ case (rune, templateArg) =>
                      stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
                    })
                    Ok(())
                  }
                  case _ => return Err(CallResultWasntExpectedType(template, result))
                }
              }
            }
          }
          case None => {
            val template = vassertSome(stepState.getConclusion(templateRune.rune))
            template match {
              case RuntimeSizedArrayTemplateTemplata() => {
                val args = argRunes.map(argRune => vassertSome(stepState.getConclusion(argRune.rune)))
                val Vector(m, CoordTemplata(coord)) = args
                val mutability = ITemplata.expectMutability(m)
                val rsaKind = delegate.predictRuntimeSizedArrayKind(env, state, coord, mutability)
                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, KindTemplata(rsaKind))
                Ok(())
              }
              case StaticSizedArrayTemplateTemplata() => {
                val args = argRunes.map(argRune => vassertSome(stepState.getConclusion(argRune.rune)))
                val Vector(s, m, v, CoordTemplata(coord)) = args
                val size = ITemplata.expectInteger(s)
                val mutability = ITemplata.expectMutability(m)
                val variability = ITemplata.expectVariability(v)
                val rsaKind = delegate.predictStaticSizedArrayKind(env, state, mutability, variability, size, coord)
                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, KindTemplata(rsaKind))
                Ok(())
              }
              case it @ StructDefinitionTemplata(_, _) => {
                val args = argRunes.map(argRune => vassertSome(stepState.getConclusion(argRune.rune)))
                // See SFWPRL for why we're calling predictStruct instead of resolveStruct
                val kind = delegate.predictStruct(env, state, it, args.toVector)
                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, KindTemplata(kind))
                Ok(())
              }
              case it @ InterfaceDefinitionTemplata(_, _) => {
                val args = argRunes.map(argRune => vassertSome(stepState.getConclusion(argRune.rune)))
                // See SFWPRL for why we're calling predictInterface instead of resolveInterface
                val kind = delegate.predictInterface(env, state, it, args.toVector)
                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, KindTemplata(kind))
                Ok(())
              }
              case kt @ KindTemplata(_) => {
                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, kt)
                Ok(())
              }
              case other => vimpl(other)
            }
          }
        }
      }
    }
  }

  private def literalToTemplata(literal: ILiteralSL) = {
    literal match {
      case MutabilityLiteralSL(mutability) => MutabilityTemplata(Conversions.evaluateMutability(mutability))
      case OwnershipLiteralSL(ownership) => OwnershipTemplata(Conversions.evaluateOwnership(ownership))
      case VariabilityLiteralSL(variability) => VariabilityTemplata(Conversions.evaluateVariability(variability))
      case StringLiteralSL(string) => StringTemplata(string)
      case IntLiteralSL(num) => IntegerTemplata(num)
    }
  }
}
