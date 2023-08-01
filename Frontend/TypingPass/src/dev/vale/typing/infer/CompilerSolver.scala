package dev.vale.typing.infer

import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.ShareP
import dev.vale.postparsing.rules._
import dev.vale.{Err, Ok, RangeS, Result, vassert, vassertSome, vimpl, vwat}
import dev.vale.postparsing._
import dev.vale.solver.{CompleteSolve, FailedSolve, ISolveRule, ISolverError, ISolverOutcome, ISolverState, IStepState, IncompleteSolve, RuleError, Solver, SolverConflict}
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.ast.PrototypeT
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale._
import dev.vale.postparsing.ArgumentRuneS
import dev.vale.postparsing.rules._
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.citizen.{IsntParent, ResolveFailure}
import dev.vale.typing.env.TemplataLookupContext
import dev.vale.typing._
import dev.vale.typing.templata.ITemplataT.{expectCoordTemplata, expectKindTemplata}
import dev.vale.typing.types._

import scala.collection.immutable.HashSet
import scala.collection.mutable

sealed trait ITypingPassSolverError
case class KindIsNotConcrete(kind: KindT) extends ITypingPassSolverError
case class KindIsNotInterface(kind: KindT) extends ITypingPassSolverError
case class KindIsNotStruct(kind: KindT) extends ITypingPassSolverError
case class CouldntFindFunction(range: List[RangeS], fff: FindFunctionFailure) extends ITypingPassSolverError {
  vpass()
}
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
case class CallResultWasntExpectedType(expected: ITemplataT[ITemplataType], actual: ITemplataT[ITemplataType]) extends ITypingPassSolverError
case class CallResultIsntCallable(result: ITemplataT[ITemplataType]) extends ITypingPassSolverError
case class OneOfFailed(rule: OneOfSR) extends ITypingPassSolverError
case class IsaFailed(sub: KindT, suuper: KindT) extends ITypingPassSolverError
case class WrongNumberOfTemplateArgs(expectedMinNumArgs: Int, expectedMaxNumArgs: Int) extends ITypingPassSolverError
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

  def getMutability(state: CompilerOutputs, kind: KindT): ITemplataT[MutabilityTemplataType]

  def lookupTemplata(env: InferEnv, state: CompilerOutputs, range: List[RangeS], name: INameT): ITemplataT[ITemplataType]

  def lookupTemplataImprecise(env: InferEnv, state: CompilerOutputs, range: List[RangeS], name: IImpreciseNameS): Option[ITemplataT[ITemplataType]]

  def coerceToCoord(
    env: InferEnv,
    state: CompilerOutputs,
    range: List[RangeS],
    templata: ITemplataT[ITemplataType],
    region: RegionT):
  ITemplataT[ITemplataType]

  def isDescendant(env: InferEnv, state: CompilerOutputs, kind: KindT): Boolean
  def isAncestor(env: InferEnv, state: CompilerOutputs, kind: KindT): Boolean

  def sanityCheckConclusion(env: InferEnv, state: CompilerOutputs, rune: IRuneS, templata: ITemplataT[ITemplataType]): Unit

  // See SFWPRL for how this is different from resolveStruct.
  def predictStruct(
    env: InferEnv,
    state: CompilerOutputs,
    templata: StructDefinitionTemplataT,
    templateArgs: Vector[ITemplataT[ITemplataType]]):
  (KindT)

  // See SFWPRL for how this is different from resolveInterface.
  def predictInterface(
    env: InferEnv,
    state: CompilerOutputs,
    templata: InterfaceDefinitionTemplataT,
    templateArgs: Vector[ITemplataT[ITemplataType]]):
  (KindT)

  def predictStaticSizedArrayKind(
    env: InferEnv,
    state: CompilerOutputs,
    mutability: ITemplataT[MutabilityTemplataType],
    variability: ITemplataT[VariabilityTemplataType],
    size: ITemplataT[IntegerTemplataType],
    element: CoordT,
    region: RegionT):
  StaticSizedArrayTT

  def predictRuntimeSizedArrayKind(
    env: InferEnv,
    state: CompilerOutputs,
    type2: CoordT,
    arrayMutability: ITemplataT[MutabilityTemplataType],
    region: RegionT):
  RuntimeSizedArrayTT

  def getAncestors(env: InferEnv, coutputs: CompilerOutputs, descendant: KindT, includeSelf: Boolean):
  (Set[KindT])

  def isParent(
    env: InferEnv,
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    subKindTT: ISubKindTT,
    superKindTT: ISuperKindTT,
    includeSelf: Boolean):
  Option[ITemplataT[ImplTemplataType]]

  def structIsClosure(state: CompilerOutputs, structTT: StructTT): Boolean

  def kindIsFromTemplate(
    state: CompilerOutputs,
    actualCitizenRef: KindT,
    expectedCitizenTemplata: ITemplataT[ITemplataType]):
  Boolean

  def predictFunction(
    env: InferEnv,
    state: CompilerOutputs,
    functionRange: RangeS,
    name: StrI,
    paramCoords: Vector[CoordT],
    returnCoord: CoordT):
  PrototypeTemplataT

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
  IsaTemplataT
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
          case other => vimpl(other)
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
    initiallyKnownRuneToTemplata: Map[IRuneS, ITemplataT[ITemplataType]]):
  Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError] = {

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
      new Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError](
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
    solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError]):
  Result[Unit, FailedSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError]] = {
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
    solver: Solver[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError]):
  ISolverOutcome[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError] = {
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
  extends ISolveRule[IRulexSR, IRuneS, InferEnv, CompilerOutputs, ITemplataT[ITemplataType], ITypingPassSolverError] {

  override def sanityCheckConclusion(env: InferEnv, state: CompilerOutputs, rune: IRuneS, conclusion: ITemplataT[ITemplataType]): Unit = {
    delegate.sanityCheckConclusion(env, state, rune, conclusion)
  }

  override def complexSolve(
      state: CompilerOutputs,
      env: InferEnv,
      solverState: ISolverState[IRulexSR, IRuneS, ITemplataT[ITemplataType]],
      stepState: IStepState[IRulexSR, IRuneS, ITemplataT[ITemplataType]]):
  Result[Unit, ISolverError[IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError]] = {
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
        val callRulesTemplateRunes =
          unsolvedRules
              .collect({
                case z @ CallSR(range, r, templateRune, _) if equivalencies.getKindEquivalentRunes(r.rune).contains(receiver) => templateRune
              })
        val senderConclusions =
          runesSendingToThisReceiver
            .flatMap(senderRune => solverState.getConclusion(senderRune).map(senderRune -> _))
            .map({
              case (senderRune, CoordTemplataT(coord)) => (senderRune -> coord)
              case other => vwat(other)
            })
            .toVector
        val callTemplates =
          equivalencies.getKindEquivalentRunes(
            callRulesTemplateRunes.map(_.rune))
            .flatMap(solverState.getConclusion)
            .toVector
        vassert(callTemplates.distinct.size <= 1)
        // If true, there are some senders/constraints we don't know yet, so lets be
        // careful to not assume between any possibilities below.
        val allSendersKnown = senderConclusions.size == runesSendingToThisReceiver.size
        val allCallsKnown = callRulesTemplateRunes.size == callTemplates.size
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
                  CoordT(
                    Conversions.evaluateOwnership(vassertSome(ownership)),
                    RegionT(),
                    receiverInstantiationKind)
                }
              }) ++
                senderConclusions.map(_._2).map({ case CoordT(ownership, _, _) =>
                  CoordT(ownership, RegionT(), receiverInstantiationKind)
                })
            if (possibleCoords.nonEmpty) {
              val ownership =
                possibleCoords.map(_.ownership).distinct match {
                  case Vector() => vwat()
                  case Vector(ownership) => ownership
                  case _ => return Err(RuleError(ReceivingDifferentOwnerships(senderConclusions)))
                }
              val region = RegionT()
              Some(receiver -> CoordTemplataT(CoordT(ownership, region, receiverInstantiationKind)))
            } else {
              // Just conclude a kind, which will coerce to an owning coord, and hope it's right.
              Some(receiver -> templata.KindTemplataT(receiverInstantiationKind))
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
    callTemplates: Vector[ITemplataT[ITemplataType]],
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
    solverState: ISolverState[IRulexSR, IRuneS, ITemplataT[ITemplataType]],
    ruleIndex: Int,
    rule: IRulexSR,
    stepState: IStepState[IRulexSR, IRuneS, ITemplataT[ITemplataType]]):
  Result[Unit, ISolverError[IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError]] = {
    solveRule(state, env, ruleIndex, rule, new IStepState[IRulexSR, IRuneS, ITemplataT[ITemplataType]] {
      override def addRule(rule: IRulexSR): Unit = stepState.addRule(rule)
      override def getConclusion(rune: IRuneS): Option[ITemplataT[ITemplataType]] = stepState.getConclusion(rune)
      override def getUnsolvedRules(): Vector[IRulexSR] = stepState.getUnsolvedRules()
      override def concludeRune[ErrType](rangeS: List[RangeS], rune: IRuneS, conclusion: ITemplataT[ITemplataType]): Unit = {
        // I think we do this because the caller can give much better error messages than a general conflict problem.
        // Were there other reasons?
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
    stepState: IStepState[IRulexSR, IRuneS, ITemplataT[ITemplataType]]):
  // One might expect us to return the conclusions in this Result. Instead we take in a
  // lambda to avoid intermediate allocations, for speed.
  Result[Unit, ITypingPassSolverError] = {
    rule match {
      case KindComponentsSR(range, kindRune, mutabilityRune) => {
        val KindTemplataT(kind) = vassertSome(stepState.getConclusion(kindRune.rune))
        val mutability = delegate.getMutability(state, kind)
        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
        Ok(())
      }
      case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => {
        stepState.getConclusion(resultRune.rune) match {
          case None => {
            val OwnershipTemplataT(ownership) = vassertSome(stepState.getConclusion(ownershipRune.rune))
            val KindTemplataT(kind) = vassertSome(stepState.getConclusion(kindRune.rune))
            val region = RegionT()
            val newCoord =
              delegate.getMutability(state, kind) match {
                case MutabilityTemplataT(ImmutableT) => CoordT(ShareT, region, kind)
                case MutabilityTemplataT(MutableT) | PlaceholderTemplataT(_, MutabilityTemplataType()) => {
                  CoordT(ownership, RegionT(), kind)
                }
              }
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, CoordTemplataT(newCoord))
            Ok(())
          }
          case Some(coord) => {
            val CoordTemplataT(CoordT(ownership, region, kind)) = coord
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, ownershipRune.rune, OwnershipTemplataT(ownership))
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, kindRune.rune, KindTemplataT(kind))
            Ok(())
          }
        }
      }
      case PrototypeComponentsSR(range, resultRune, ownershipRune, kindRune) => {
        val PrototypeTemplataT(_, prototype) = vassertSome(stepState.getConclusion(resultRune.rune))
        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, ownershipRune.rune, CoordListTemplataT(prototype.paramTypes))
        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, kindRune.rune, CoordTemplataT(prototype.returnType))
        Ok(())
      }
      case ResolveSR(range, resultRune, name, paramListRune, returnRune) => {
        // If we're here, then we're resolving a prototype.
        // This happens at the call-site.
        // The function (or struct) can either supply a default resolve rule (usually
        // via the `func moo(int)void` syntax) or let the caller pass it in.

        val CoordListTemplataT(paramCoords) = vassertSome(stepState.getConclusion(paramListRune.rune))
        val CoordTemplataT(returnCoord) = vassertSome(stepState.getConclusion(returnRune.rune))
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
          case PrototypeTemplataT(range, prototype) => {
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges,
              paramListRune.rune, CoordListTemplataT(prototype.paramTypes))
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges,
              returnRune.rune, CoordTemplataT(prototype.returnType))
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

        val CoordListTemplataT(paramCoords) = vassertSome(stepState.getConclusion(paramListRune.rune))
        val CoordTemplataT(returnType) = vassertSome(stepState.getConclusion(returnRune.rune))

        // Now introduce a prototype that lets us call it with this new name, that we
        // can call it by.
        val newPrototype =
          delegate.assemblePrototype(env, state, range, name, paramCoords, returnType)

        stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges,
          resultRune.rune, PrototypeTemplataT(range, newPrototype))
        Ok(())
      }
      case CallSiteCoordIsaSR(range, resultRune, subRune, superRune) => {
        val CoordTemplataT(subCoord) =
          vassertSome(stepState.getConclusion(subRune.rune))
        val CoordTemplataT(superCoord) =
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

        val CoordTemplataT(CoordT(_, _, subKindUnchecked)) = vassertSome(stepState.getConclusion(subRune.rune))
        val CoordTemplataT(CoordT(_, _, superKindUnchecked)) = vassertSome(stepState.getConclusion(superRune.rune))

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
            val CoordTemplataT(coord) = vassertSome(stepState.getConclusion(senderRune.rune))
            if (delegate.isDescendant(env, state, coord.kind)) {
              // We know that the sender can be upcast, so we can't shortcut.
              // We need to wait for the receiver rune to know what to do.
              stepState.addRule(CallSiteCoordIsaSR(range, None, senderRune, receiverRune))
              Ok(())
            } else {
              // We're sending something that can't be upcast, so both sides are definitely the same type.
              // We can shortcut things here, even knowing only the sender's type.
              stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, receiverRune.rune, CoordTemplataT(coord))
              Ok(())
            }
          }
          case Some(CoordTemplataT(coord)) => {
            if (delegate.isAncestor(env, state, coord.kind)) {
              // We know that the receiver is an interface, so we can't shortcut.
              // We need to wait for the sender rune to be able to confirm the sender
              // implements the receiver.
              stepState.addRule(CallSiteCoordIsaSR(range, None, senderRune, receiverRune))
              Ok(())
            } else {
              // We're receiving a concrete type, so both sides are definitely the same type.
              // We can shortcut things here, even knowing only the receiver's type.
              stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, senderRune.rune, CoordTemplataT(coord))
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
          case KindTemplataT(kind) => {
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
          case KindTemplataT(kind) => {
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
          case KindTemplataT(kind) => {
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
            val CoordTemplataT(coord) = vassertSome(stepState.getConclusion(coordRune.rune))
            coord.ownership match {
              case OwnT | ShareT => {
                stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, kindRune.rune, KindTemplataT(coord.kind))
                Ok(())
              }
              case _ => {
                return Err(OwnershipDidntMatch(coord, OwnT))
              }
            }
          }
          case Some(kind) => {
            val coerced = delegate.coerceToCoord(env, state, range :: env.parentRanges, kind, RegionT())
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
      case AugmentSR(range, outerCoordRune, maybeAugmentOwnership, innerRune) => {
        stepState.getConclusion(outerCoordRune.rune) match {
          case Some(CoordTemplataT(outerCoord)) => {
            val CoordT(outerOwnership, outerRegion, outerKind) = outerCoord

            val innerOwnership =
              maybeAugmentOwnership match {
                case None => outerOwnership
                case Some(augmentOwnership) => {
                  delegate.getMutability(state, outerKind) match {
                    case PlaceholderTemplataT(_, _) | MutabilityTemplataT(MutableT) => {
                      if (augmentOwnership == ShareP) {
                        return Err(CantShareMutable(outerKind))
                      }
                      if (outerOwnership != Conversions.evaluateOwnership(augmentOwnership)) {
                        return Err(OwnershipDidntMatch(
                          outerCoord,
                          Conversions.evaluateOwnership(augmentOwnership)))
                      }
                      OwnT
                    }
                    case MutabilityTemplataT(ImmutableT) => outerOwnership
                  }
                }
              }

            val innerKind = outerKind

//            val innerCoord = CoordT(innerOwnership, innerRegion, innerKind)
            // DO NOT SUBMIT
            // trying out outerRegion here to see if it passes
            val innerCoord = CoordT(innerOwnership, outerRegion, innerKind)

            stepState.concludeRune[ITypingPassSolverError](
              range :: env.parentRanges, innerRune.rune, CoordTemplataT(innerCoord))
            Ok(())
          }
          case None => {
            val CoordTemplataT(innerCoord) =
              expectCoordTemplata(vassertSome(stepState.getConclusion(innerRune.rune)))
            val newRegion = RegionT()
            val newOwnership =
              maybeAugmentOwnership match {
                case None => innerCoord.ownership
                case Some(augmentOwnership) => {
                  delegate.getMutability(state, innerCoord.kind) match {
                    case MutabilityTemplataT(ImmutableT) => innerCoord.ownership
                    case PlaceholderTemplataT(_, MutabilityTemplataType()) => {
                      if (augmentOwnership == ShareP) {
                        return Err(CantSharePlaceholder(innerCoord.kind))
                      }
                      Conversions.evaluateOwnership(augmentOwnership)
                    }
                    case MutabilityTemplataT(MutableT) => {
                      if (augmentOwnership == ShareP) {
                        return Err(CantShareMutable(innerCoord.kind))
                      }
                      Conversions.evaluateOwnership(augmentOwnership)
                    }
                  }
                }
              }
            val newCoord = CoordT(newOwnership, newRegion, innerCoord.kind)
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, outerCoordRune.rune, CoordTemplataT(newCoord))
            Ok(())
          }
        }
      }
      case PackSR(range, resultRune, memberRunes) => {
        stepState.getConclusion(resultRune.rune) match {
          case None => {
            val members =
              memberRunes.map(memberRune => {
                val CoordTemplataT(coord) = vassertSome(stepState.getConclusion(memberRune.rune))
                coord
              })
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, CoordListTemplataT(members.toVector))
            Ok(())
          }
          case Some(CoordListTemplataT(members)) => {
            vassert(members.size == memberRunes.size)
            memberRunes.zip(members).foreach({ case (rune, coord) =>
              stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templata.CoordTemplataT(coord))
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
        val CoordListTemplataT(coords) = vassertSome(stepState.getConclusion(coordListRune.rune))
        if (coords.forall(_.ownership == ShareT)) {
          stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, MutabilityTemplataT(ImmutableT))
        } else {
          stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, MutabilityTemplataT(MutableT))
        }
        Ok(())
      }
      case CallSR(range, resultRune, templateRune, argRunes) => {
        solveCallRule(state, env, stepState, range, resultRune, templateRune, argRunes)
      }
    }
  }

  private def solveCallRule(
      state: CompilerOutputs,
      env: InferEnv,
      stepState: IStepState[IRulexSR, IRuneS, ITemplataT[ITemplataType]],
      range: RangeS,
      resultRune: RuneUsage,
      templateRune: RuneUsage,
      argRunes: Vector[RuneUsage]):
  Result[Unit, ITypingPassSolverError] = {
    stepState.getConclusion(resultRune.rune) match {
      case Some(result) => {
        result match {
          case KindTemplataT(rsaTT @ contentsRuntimeSizedArrayTT(mutability, memberType, region)) => {
            if (argRunes.size != 2) {
              return Err(WrongNumberOfTemplateArgs(2, 2))
            }

            vassertSome(stepState.getConclusion(templateRune.rune)) match {
              case it@RuntimeSizedArrayTemplateTemplataT() => {
                if (!delegate.kindIsFromTemplate(state, rsaTT, it)) {
                  return Err(CallResultWasntExpectedType(it, result))
                }
              }
              case other => return Err(CallResultWasntExpectedType(other, result))
            }

            val mutabilityRune = argRunes(0)
            val elementRune = argRunes(1)
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplataT(memberType))
            Ok(())
          }
          case KindTemplataT(ssaTT @ contentsStaticSizedArrayTT(size, mutability, variability, memberType, region)) => {
            if (argRunes.size != 4) {
              return Err(WrongNumberOfTemplateArgs(4, 4))
            }

            vassertSome(stepState.getConclusion(templateRune.rune)) match {
              case it@StaticSizedArrayTemplateTemplataT() => {
                if (!delegate.kindIsFromTemplate(state, ssaTT, it)) {
                  return Err(CallResultWasntExpectedType(it, result))
                }
              }
              case other => return Err(CallResultWasntExpectedType(other, result))
            }

            // We don't take in the region rune here because there's no syntactical way to specify it.
            val Vector(sizeRune, mutabilityRune, variabilityRune, elementRune) = argRunes
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, sizeRune.rune, size)
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, variabilityRune.rune, variability)
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplataT(memberType))
            // // We still have the region rune though, the rule still gives it to us.
            // stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, contextRegionRune.rune, region.region)
            Ok(())
          }
          case KindTemplataT(interface@InterfaceTT(_)) => {
            // With all this, it seems like we could solve this rule even
            // without knowing the template. Like we could pull the template
            // from the result. Alas, we couldnt make a template templata
            // for a given lambda. If we can figure that out, this could work.
            // val templateTemplata =
            //   env.selfEnv.lookupNearestWithName(interface.id.localName.template, Set(TemplataLookupContext)) match {
            //     case Some(t@InterfaceDefinitionTemplataT(_, _)) => t
            //     case Some(_) => vwat()
            //     case None => {
            //       // This might happen if there's a lambda?
            //       return Err(CallResultIsntCallable(result))
            //     }
            //   }
            // if (templateTemplata.tyype.paramTypes != interface.id.localName.templateArgs.map(_.tyype)) {
            //   vimpl()//return Err(WrongNumberOfTemplateArgs(argRunes.length, argRunes.length)) need better error
            // }
            // stepState.concludeRune[ITypingPassSolverError](
            //   range :: env.parentRanges, templateRune.rune, templateTemplata)

            vassertSome(stepState.getConclusion(templateRune.rune)) match {
              case it @ InterfaceDefinitionTemplataT(_, _) => {
                if (!delegate.kindIsFromTemplate(state, interface, it)) {
                  return Err(CallResultWasntExpectedType(it, result))
                }
              }
              case other => return Err(CallResultWasntExpectedType(other, result))
            }

            argRunes.zip(interface.id.localName.templateArgs).foreach({ case (rune, templateArg) =>
              stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
            })
            Ok(())
          }
          case KindTemplataT(struct@StructTT(_)) => {
            // With all this, it seems like we could solve this rule even
            // without knowing the template. Like we could pull the template
            // from the result. Alas, we couldnt make a template templata
            // for a given lambda. If we can figure that out, this could work.
            // val templateTemplata =
            //   env.selfEnv.lookupNearestWithName(struct.id.localName.template, Set(TemplataLookupContext)) match {
            //     case Some(t@StructDefinitionTemplataT(_, _)) => t
            //     case Some(_) => vwat()
            //     case None => {
            //       // This might happen if there's a lambda?
            //       return Err(CallResultIsntCallable(result))
            //     }
            //   }
            // if (templateTemplata.tyype.paramTypes != struct.id.localName.templateArgs.map(_.tyype)) {
            //   vimpl() // return Err(WrongNumberOfTemplateArgs(argRunes.length, argRunes.length)) need better error
            // }
            // stepState.concludeRune[ITypingPassSolverError](
            //   range :: env.parentRanges, templateRune.rune, templateTemplata)

            vassertSome(stepState.getConclusion(templateRune.rune)) match {
              case it@StructDefinitionTemplataT(_, _) => {
                if (!delegate.kindIsFromTemplate(state, struct, it)) {
                  return Err(CallResultWasntExpectedType(it, result))
                }
              }
              case other => return Err(CallResultWasntExpectedType(other, result))
            }

            struct.id.localName.templateArgs.zip(argRunes).foreach({ case (templateArg, argRune) =>
              // The user specified this argument, so let's match the result's
              // corresponding generic arg with the user specified argument here.
              stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, argRune.rune, templateArg)
            })

            Ok(())
          }
          case KindTemplataT(StrT() | IntT(_) | BoolT() | FloatT() | VoidT()) => {
            return Err(CallResultIsntCallable(result))
          }
          case KindTemplataT(KindPlaceholderT(_)) => {
            // I can't imagine a placeholder that is a callable template...
            return Err(CallResultIsntCallable(result))
          }
          case other => vwat(other)
        }

        // val template = vassertSome(stepState.getConclusion(templateRune.rune))
        // template match {
        //   case RuntimeSizedArrayTemplateTemplataT() => {
        //     result match {
        //       case CoordTemplataT(CoordT(ShareT | OwnT, _, contentsRuntimeSizedArrayTT(mutability, memberType, region))) => {
        //         vimpl() // doublecheck this case
        //         if (argRunes.size < 2 || argRunes.size > 3) {
        //           return Err(WrongNumberOfTemplateArgs(2, 3))
        //         }
        //         val mutabilityRune = argRunes(0)
        //         val elementRune = argRunes(1)
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplataT(memberType))
        //         if (argRunes.size >= 3) {
        //           val regionRune = argRunes(2)
        //           stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, regionRune.rune, region.region)
        //         }
        //         Ok(())
        //       }
        //       case KindTemplataT(contentsRuntimeSizedArrayTT(mutability, memberType, region)) => {
        //         vimpl() // doublecheck this case
        //         if (argRunes.size < 2 || argRunes.size > 3) {
        //           return Err(WrongNumberOfTemplateArgs(2, 3))
        //         }
        //         val mutabilityRune = argRunes(0)
        //         val elementRune = argRunes(1)
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplataT(memberType))
        //         if (argRunes.size >= 3) {
        //           val regionRune = argRunes(2)
        //           stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, regionRune.rune, region.region)
        //         }
        //         Ok(())
        //       }
        //       case _ => return Err(CallResultWasntExpectedType(template, result))
        //     }
        //   }
        //   case StaticSizedArrayTemplateTemplataT() => {
        //     result match {
        //       case CoordTemplataT(CoordT(ShareT | OwnT, regionFromCoord, contentsStaticSizedArrayTT(size, mutability, variability, memberType, regionFromKind))) => {
        //         vimpl() // doublecheck this case
        //         vassert(regionFromCoord == regionFromKind)
        //         val region = regionFromKind
        //         if (argRunes.size != 4) {
        //           return Err(WrongNumberOfTemplateArgs(4, 4))
        //         }
        //         val Vector(sizeRune, mutabilityRune, variabilityRune, elementRune, regionRune) = argRunes
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, sizeRune.rune, size)
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, variabilityRune.rune, variability)
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplataT(memberType))
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, regionRune.rune, region.region)
        //         Ok(())
        //       }
        //       case KindTemplataT(contentsStaticSizedArrayTT(size, mutability, variability, memberType, region)) => {
        //         vimpl() // doublecheck this case
        //         if (argRunes.size != 4) {
        //           return Err(WrongNumberOfTemplateArgs(4, 4))
        //         }
        //         // We don't take in the region rune here because there's no syntactical way to specify it.
        //         val Vector(sizeRune, mutabilityRune, variabilityRune, elementRune) = argRunes
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, sizeRune.rune, size)
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, mutabilityRune.rune, mutability)
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, variabilityRune.rune, variability)
        //         stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, elementRune.rune, CoordTemplataT(memberType))
        //         // // We still have the region rune though, the rule still gives it to us.
        //         // stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, contextRegionRune.rune, region.region)
        //         Ok(())
        //       }
        //       case _ => return Err(CallResultWasntExpectedType(template, result))
        //     }
        //   }
        //   case it@InterfaceDefinitionTemplataT(_, _) => {
        //     result match {
        //       case KindTemplataT(interface@InterfaceTT(_)) => {
        //         vimpl() // doublecheck this case
        //         if (!delegate.kindIsFromTemplate(state, interface, it)) {
        //           return Err(CallResultWasntExpectedType(it, result))
        //         }
        //         vassert(argRunes.size == interface.id.localName.templateArgs.size)
        //         argRunes.zip(interface.id.localName.templateArgs).foreach({ case (rune, templateArg) =>
        //           stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
        //         })
        //         Ok(())
        //       }
        //       case CoordTemplataT(CoordT(OwnT | ShareT, _, interface@InterfaceTT(_))) => {
        //         vimpl() // doublecheck this case
        //         if (!delegate.kindIsFromTemplate(state, interface, it)) {
        //           return Err(CallResultWasntExpectedType(it, result))
        //         }
        //         vassert(argRunes.size == interface.id.localName.templateArgs.size)
        //         argRunes.zip(interface.id.localName.templateArgs).foreach({ case (rune, templateArg) =>
        //           stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
        //         })
        //         Ok(())
        //       }
        //       case _ => return Err(CallResultWasntExpectedType(template, result))
        //     }
        //   }
        //   case it@KindTemplataT(templateInterface@InterfaceTT(_)) => {
        //     result match {
        //       case KindTemplataT(instantiationInterface@InterfaceTT(_)) => {
        //         vimpl() // doublecheck this case
        //         if (templateInterface != instantiationInterface) {
        //           return Err(CallResultWasntExpectedType(it, result))
        //         }
        //         argRunes.zip(instantiationInterface.id.localName.templateArgs).foreach({ case (rune, templateArg) =>
        //           stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
        //         })
        //         Ok(())
        //       }
        //       case CoordTemplataT(CoordT(OwnT | ShareT, _, instantiationInterface@InterfaceTT(_))) => {
        //         vimpl() // doublecheck this case
        //         if (templateInterface != instantiationInterface) {
        //           return Err(CallResultWasntExpectedType(it, result))
        //         }
        //         argRunes.zip(instantiationInterface.id.localName.templateArgs).foreach({ case (rune, templateArg) =>
        //           stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
        //         })
        //         Ok(())
        //       }
        //       case _ => return Err(CallResultWasntExpectedType(template, result))
        //     }
        //   }
        //   case st@StructDefinitionTemplataT(_, _) => {
        //     result match {
        //       case KindTemplataT(struct@StructTT(_)) => {
        //         vimpl() // doublecheck this case
        //         if (!delegate.kindIsFromTemplate(state, struct, st)) {
        //           return Err(CallResultWasntExpectedType(st, result))
        //         }
        //         // If we get here, we have the resulting struct, and we're trying to feed it
        //         // backwards through a call to get the arguments it was invoked with.
        //
        //         // Let's say the user has a parameter `x Moo`. However, Moo has a default
        //         // generic arg like Moo<moo'>. The user didn't specify it.
        //         // So there will be 1 less argRunes here than the actual template args of the
        //         // incoming result. See DROIGP for more.
        //
        //         // So as we're running the result through the call backwards, we're not going to
        //         // receive that last generic arg into anything. We're instead going to make
        //         // sure it matches the default value for that template. In this case, the
        //         // default value is the context region.
        //
        //         struct.id.localName.templateArgs.zipWithIndex.foreach({ case (templateArg, index) =>
        //           if (index < argRunes.size) {
        //             // The user specified this argument, so let's match the result's
        //             // corresponding generic arg with the user specified argument here.
        //             val rune = argRunes(index)
        //             stepState.concludeRune[ITypingPassSolverError](
        //               range :: env.parentRanges, rune.rune, templateArg)
        //           } else {
        //             vcurious() // shouldnt the highertyper prevent this
        //             // // The user didn't specify this argument, so let's match the result's
        //             // // corresponding generic arg with the default here.
        //             // val genericParam = st.originStruct.genericParameters(index)
        //             // if (genericParam.rune.rune == st.originStruct.regionRune) {
        //             //   // The default value for the default region is the context region
        //             //   // so match it against that.
        //             //   val contextRegion = expectRegion(vassertSome(stepState.getConclusion(contextRegionRune.rune)))
        //             //   if (templateArg != contextRegion) {
        //             //     return Err(CallResultWasntExpectedType(st, result))
        //             //   }
        //             // } else {
        //             //   // If we get here, there's an actual default value for it.
        //             //   vimpl()
        //             // }
        //           }
        //         })
        //
        //         Ok(())
        //       }
        //       case CoordTemplataT(CoordT(OwnT | ShareT, _, struct@StructTT(_))) => {
        //         vimpl() // doublecheck this case
        //         if (!delegate.kindIsFromTemplate(state, struct, st)) {
        //           return Err(CallResultWasntExpectedType(st, result))
        //         }
        //         vassert(argRunes.size == struct.id.localName.templateArgs.size)
        //         argRunes.zip(struct.id.localName.templateArgs).foreach({ case (rune, templateArg) =>
        //           stepState.concludeRune[ITypingPassSolverError](
        //             range :: env.parentRanges, rune.rune, templateArg)
        //         })
        //         Ok(())
        //       }
        //       case _ => return Err(CallResultWasntExpectedType(template, result))
        //     }
        //   }
        //   case it@KindTemplataT(structTT@StructTT(_)) => {
        //     result match {
        //       case KindTemplataT(instantiationStruct@StructTT(_)) => {
        //         vimpl() // doublecheck this case
        //         if (structTT != instantiationStruct) {
        //           return Err(CallResultWasntExpectedType(it, result))
        //         }
        //         argRunes.zip(instantiationStruct.id.localName.templateArgs).foreach({ case (rune, templateArg) =>
        //           stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
        //         })
        //         Ok(())
        //       }
        //       case CoordTemplataT(CoordT(OwnT | ShareT, _, instantiationStruct@StructTT(_))) => {
        //         vimpl() // doublecheck this case
        //         if (structTT != instantiationStruct) {
        //           return Err(CallResultWasntExpectedType(it, result))
        //         }
        //         argRunes.zip(instantiationStruct.id.localName.templateArgs).foreach({ case (rune, templateArg) =>
        //           stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, rune.rune, templateArg)
        //         })
        //         Ok(())
        //       }
        //       case _ => return Err(CallResultWasntExpectedType(template, result))
        //     }
        //   }
        // }
      }
      case None => {
        val template = vassertSome(stepState.getConclusion(templateRune.rune))
        template match {
          case RuntimeSizedArrayTemplateTemplataT() => {
            val args = argRunes.map(argRune => vassertSome(stepState.getConclusion(argRune.rune)))
            val Vector(m, CoordTemplataT(coord)) = args
            val contextRegion = RegionT()
            val mutability = ITemplataT.expectMutability(m)
            val rsaKind = delegate.predictRuntimeSizedArrayKind(env, state, coord, mutability, contextRegion)
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, KindTemplataT(rsaKind))
            Ok(())
          }
          case StaticSizedArrayTemplateTemplataT() => {
            val args = argRunes.map(argRune => vassertSome(stepState.getConclusion(argRune.rune)))
            val Vector(s, m, v, CoordTemplataT(coord)) = args
            val contextRegion = RegionT()
            val size = ITemplataT.expectInteger(s)
            val mutability = ITemplataT.expectMutability(m)
            val variability = ITemplataT.expectVariability(v)
            val rsaKind = delegate.predictStaticSizedArrayKind(env, state, mutability, variability, size, coord, contextRegion)
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, KindTemplataT(rsaKind))
            Ok(())
          }
          case it@StructDefinitionTemplataT(_, _) => {
            val args = argRunes.map(argRune => vassertSome(stepState.getConclusion(argRune.rune)))
            // See SFWPRL for why we're calling predictStruct instead of resolveStruct
            val kind = delegate.predictStruct(env, state, it, args.toVector)
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, KindTemplataT(kind))
            Ok(())
          }
          case it@InterfaceDefinitionTemplataT(_, _) => {
            val args = argRunes.map(argRune => vassertSome(stepState.getConclusion(argRune.rune)))
            // See SFWPRL for why we're calling predictInterface instead of resolveInterface
            val kind = delegate.predictInterface(env, state, it, args.toVector)
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, KindTemplataT(kind))
            Ok(())
          }
          case kt@KindTemplataT(_) => {
            stepState.concludeRune[ITypingPassSolverError](range :: env.parentRanges, resultRune.rune, kt)
            Ok(())
          }
          case other => vimpl(other)
        }
      }
    }
  }

  private def literalToTemplata(literal: ILiteralSL) = {
    literal match {
      case MutabilityLiteralSL(mutability) => MutabilityTemplataT(Conversions.evaluateMutability(mutability))
      case OwnershipLiteralSL(ownership) => OwnershipTemplataT(Conversions.evaluateOwnership(ownership))
      case VariabilityLiteralSL(variability) => VariabilityTemplataT(Conversions.evaluateVariability(variability))
      case StringLiteralSL(string) => StringTemplataT(string)
      case IntLiteralSL(num) => IntegerTemplataT(num)
    }
  }
}
