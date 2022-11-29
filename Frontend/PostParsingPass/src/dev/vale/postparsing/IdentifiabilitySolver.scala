package dev.vale.postparsing

import dev.vale.postparsing.rules._
import dev.vale.solver.{IIncompleteOrFailedSolve, ISolveRule, ISolverError, ISolverState, IStepState, IncompleteSolve, Solver}
import dev.vale.{Err, Ok, RangeS, Result, vassert, vimpl, vpass}
import dev.vale._
import dev.vale.postparsing.rules._

import scala.collection.immutable.Map

case class IdentifiabilitySolveError(range: List[RangeS], failedSolve: IIncompleteOrFailedSolve[IRulexSR, IRuneS, Boolean, IIdentifiabilityRuleError]) {
  vpass()
}

sealed trait IIdentifiabilityRuleError

object IdentifiabilitySolver {
  def getRunes(rule: IRulexSR): Vector[IRuneS] = {
    val sanityCheck =
      rule match {
        case LookupSR(range, rune, literal) => Vector(rune)
        case RuneParentEnvLookupSR(range, rune) => Vector(rune)
        case EqualsSR(range, left, right) => Vector(left, right)
        case KindComponentsSR(range, resultRune, mutabilityRune) => Vector(resultRune, mutabilityRune)
        case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => Vector(resultRune, ownershipRune, kindRune)
        case PrototypeComponentsSR(range, resultRune, paramsRune, returnRune) => Vector(resultRune, paramsRune, returnRune)
        case ResolveSR(range, resultRune, name, paramsListRune, returnRune) => Vector(resultRune, paramsListRune, returnRune)
        case CallSiteFuncSR(range, prototypeRune, name, paramsListRune, returnRune) => Vector(prototypeRune, paramsListRune, returnRune)
        case DefinitionFuncSR(range, resultRune, name, paramsListRune, returnRune) => Vector(resultRune, paramsListRune, returnRune)
        case CallSiteCoordIsaSR(range, resultRune, sub, suuper) => resultRune.toVector ++ Vector(sub, suuper)
        case DefinitionCoordIsaSR(range, resultRune, sub, suuper) => Vector(resultRune, sub, suuper)
        case OneOfSR(range, rune, literals) => Vector(rune)
        case IsConcreteSR(range, rune) => Vector(rune)
        case IsInterfaceSR(range, rune) => Vector(rune)
        case IsStructSR(range, rune) => Vector(rune)
        case CoerceToCoordSR(range, coordRune, kindRune) => Vector(coordRune, kindRune)
        case LiteralSR(range, rune, literal) => Vector(rune)
        case AugmentSR(range, resultRune, ownership, innerRune) => Vector(resultRune, innerRune)
        case CallSR(range, resultRune, templateRune, args) => Vector(resultRune, templateRune) ++ args
//        case PrototypeSR(range, resultRune, name, parameters, returnTypeRune) => Vector(resultRune) ++ parameters ++ Vector(returnTypeRune)
        case PackSR(range, resultRune, members) => Vector(resultRune) ++ members
//        case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => Vector(resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune)
//        case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) => Vector(resultRune, mutabilityRune, elementRune)
//        case ManualSequenceSR(range, resultRune, elements) => Vector(resultRune) ++ elements
        case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => Vector(resultRune, coordListRune)
//        case CoordListSR(range, resultRune, elements) => Vector(resultRune) ++ elements
      }
    val result = rule.runeUsages
    vassert(result.map(_.rune) sameElements sanityCheck.map(_.rune))
    result.map(_.rune)
  }

  def getPuzzles(rule: IRulexSR): Vector[Vector[IRuneS]] = {
    rule match {
      case EqualsSR(range, leftRune, rightRune) => Vector(Vector(leftRune.rune), Vector(rightRune.rune))
      case LookupSR(range, rune, _) => Vector(Vector())
      case RuneParentEnvLookupSR(range, rune) => {
        // This Vector() literally means nothing can solve this puzzle.
        // It needs to be passed in via identifying rune.
        Vector()
      }
      case CallSR(range, resultRune, templateRune, args) => {
        // We can't determine the template from the result and args because we might be coercing its
        // returned kind to a coord. So we need the template.
        // We can't determine the return type because we don't know whether we're coercing or not.
        Vector(Vector(resultRune.rune, templateRune.rune), Vector(templateRune.rune) ++ args.map(_.rune))
      }
      case PackSR(range, resultRune, members) => {
        // Packs are always lists of coords
        Vector(Vector(resultRune.rune), members.map(_.rune))
      }
      case DefinitionCoordIsaSR(range, resultRune, subRune, superRune) => Vector(Vector())
      case CallSiteCoordIsaSR(range, resultRune, subRune, superRune) => Vector(Vector())
      case KindComponentsSR(range, resultRune, mutabilityRune) => Vector(Vector())
      case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => Vector(Vector())
      case PrototypeComponentsSR(range, resultRune, ownershipRune, kindRune) => Vector(Vector())
      case ResolveSR(range, resultRune, nameRune, paramsListRune, returnRune) => Vector(Vector())
      case CallSiteFuncSR(range, resultRune, nameRune, paramsListRune, returnRune) => Vector(Vector())
      case DefinitionFuncSR(range, resultRune, name, paramsListRune, returnRune) => Vector(Vector())
      case OneOfSR(range, rune, literals) => Vector(Vector())
      case IsConcreteSR(range, rune) => Vector(Vector(rune.rune))
      case IsInterfaceSR(range, rune) => Vector(Vector())
      case IsStructSR(range, rune) => Vector(Vector())
      case CoerceToCoordSR(range, coordRune, kindRune) => Vector(Vector())
      case LiteralSR(range, rune, literal) => Vector(Vector())
      case AugmentSR(range, resultRune, ownership, innerRune) => Vector(Vector())
//      case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => Vector(Vector(resultRune.rune), Vector(mutabilityRune.rune, variabilityRune.rune, sizeRune.rune, elementRune.rune))
//      case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) => Vector(Vector(resultRune.rune), Vector(mutabilityRune.rune, elementRune.rune))
//      case ManualSequenceSR(range, resultRune, elements) => Vector(Vector(resultRune.rune))
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => Vector(Vector())
        // solverState.addPuzzle(ruleIndex, Vector(senderRune, receiverRune))
//      case CoordListSR(range, resultRune, elements) => Vector(Vector())
    }
  }

  private def solveRule(
    state: Unit,
    env: Unit,
    ruleIndex: Int,
    callRange: List[RangeS],
    rule: IRulexSR,
    stepState: IStepState[IRulexSR, IRuneS, Boolean]):
  Result[Unit, ISolverError[IRuneS, Boolean, IIdentifiabilityRuleError]] = {
    rule match {
      case KindComponentsSR(range, resultRune, mutabilityRune) => {
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        stepState.concludeRune(range :: callRange, mutabilityRune.rune, true)
        Ok(())
      }
      case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => {
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        stepState.concludeRune(range :: callRange, ownershipRune.rune, true)
        stepState.concludeRune(range :: callRange, kindRune.rune, true)
        Ok(())
      }
      case PrototypeComponentsSR(range, resultRune, paramsRune, returnRune) => {
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        stepState.concludeRune(range :: callRange, paramsRune.rune, true)
        stepState.concludeRune(range :: callRange, returnRune.rune, true)
        Ok(())
      }
      case CallSR(range, resultRune, templateRune, argRunes) => {
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        stepState.concludeRune(range :: callRange, templateRune.rune, true)
        argRunes.map(_.rune).foreach({ case argRune =>
          stepState.concludeRune(range :: callRange, argRune, true)
        })
        Ok(())
      }
      case ResolveSR(range, resultRune, name, paramListRune, returnRune) => {
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        stepState.concludeRune(range :: callRange, paramListRune.rune, true)
        stepState.concludeRune(range :: callRange, returnRune.rune, true)
        Ok(())
      }
      case CallSiteFuncSR(range, resultRune, name, paramListRune, returnRune) => {
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        stepState.concludeRune(range :: callRange, paramListRune.rune, true)
        stepState.concludeRune(range :: callRange, returnRune.rune, true)
        Ok(())
      }
      case DefinitionFuncSR(range, resultRune, name, paramsListRune, returnRune) => {
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        stepState.concludeRune(range :: callRange, paramsListRune.rune, true)
        stepState.concludeRune(range :: callRange, returnRune.rune, true)
        Ok(())
      }
      case DefinitionCoordIsaSR(range, resultRune, subRune, superRune) => {
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        stepState.concludeRune(range :: callRange, subRune.rune, true)
        stepState.concludeRune(range :: callRange, superRune.rune, true)
        Ok(())
      }
      case CallSiteCoordIsaSR(range, resultRune, subRune, superRune) => {
        stepState.concludeRune(range :: callRange, subRune.rune, true)
        stepState.concludeRune(range :: callRange, superRune.rune, true)
        resultRune match {
          case Some(resultRune) => stepState.concludeRune(range :: callRange, resultRune.rune, true)
          case None =>
        }
        Ok(())
      }
      case OneOfSR(range, resultRune, literals) => {
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        Ok(())
      }
      case EqualsSR(range, leftRune, rightRune) => {
        stepState.concludeRune(range :: callRange, leftRune.rune, true)
        stepState.concludeRune(range :: callRange, rightRune.rune, true)
        Ok(())
      }
      case IsConcreteSR(range, rune) => {
        stepState.concludeRune(range :: callRange, rune.rune, true)
        Ok(())
      }
      case IsInterfaceSR(range, rune) => {
        stepState.concludeRune(range :: callRange, rune.rune, true)
        Ok(())
      }
      case IsStructSR(range, rune) => {
        stepState.concludeRune(range :: callRange, rune.rune, true)
        Ok(())
      }
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => {
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        stepState.concludeRune(range :: callRange, coordListRune.rune, true)
        Ok(())
      }
      case CoerceToCoordSR(range, coordRune, kindRune) => {
        stepState.concludeRune(range :: callRange, kindRune.rune, true)
        stepState.concludeRune(range :: callRange, coordRune.rune, true)
        Ok(())
      }
      case LiteralSR(range, rune, literal) => {
        stepState.concludeRune(range :: callRange, rune.rune, true)
        Ok(())
      }
      case LookupSR(range, rune, name) => {
        stepState.concludeRune(range :: callRange, rune.rune, true)
        Ok(())
      }
      case RuneParentEnvLookupSR(range, rune) => {
        vimpl()
//        (env(RuneNameS(rune.rune)), vassertSome(stepState.getConclusion(rune.rune))) match {
//          case (true, true) =>
//          case (TemplateTemplataType(Vector(), true), true) =>
//          case (TemplateTemplataType(Vector(), result), expected) if result == expected =>
//          case (from, to) if from == to =>
//          case (from, to) => {
//            return Err(SolverConflict(rune.rune, to, from))
//          }
//        }
        Ok(())
      }
      case LookupSR(range, rune, name) => {
        stepState.concludeRune(range :: callRange, rune.rune, true)
        Ok(())
      }
      case AugmentSR(range, resultRune, ownership, innerRune) => {
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        stepState.concludeRune(range :: callRange, innerRune.rune, true)
        Ok(())
      }
      case PackSR(range, resultRune, memberRunes) => {
        memberRunes.foreach(x => stepState.concludeRune(range :: callRange, x.rune, true))
        stepState.concludeRune(range :: callRange, resultRune.rune, true)
        Ok(())
      }
//      case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => {
//        stepState.concludeRune(range :: callRange, resultRune.rune, true)
//        stepState.concludeRune(range :: callRange, mutabilityRune.rune, true)
//        stepState.concludeRune(range :: callRange, variabilityRune.rune, true)
//        stepState.concludeRune(range :: callRange, sizeRune.rune, true)
//        stepState.concludeRune(range :: callRange, elementRune.rune, true)
//        Ok(())
//      }
//      case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) => {
//        stepState.concludeRune(range :: callRange, resultRune.rune, true)
//        stepState.concludeRune(range :: callRange, mutabilityRune.rune, true)
//        stepState.concludeRune(range :: callRange, elementRune.rune, true)
//        Ok(())
//      }
    }
  }

  def solve(
    sanityCheck: Boolean,
    useOptimizedSolver: Boolean,
    interner: Interner,
    callRange: List[RangeS],
    rules: IndexedSeq[IRulexSR],
    identifyingRunes: Iterable[IRuneS]):
  Result[Map[IRuneS, Boolean], IdentifiabilitySolveError] = {
    val initiallyKnownRunes = identifyingRunes.map(r => (r, true)).toMap
    val solver =
      new Solver[IRulexSR, IRuneS, Unit, Unit, Boolean, IIdentifiabilityRuleError](
        sanityCheck, useOptimizedSolver, interner)
    val solverState =
      solver
        .makeInitialSolverState(
          callRange, rules, getRunes, (rule: IRulexSR) => getPuzzles(rule), initiallyKnownRunes)
    val (steps, conclusions) =
      solver.solve(
        (rule: IRulexSR) => getPuzzles(rule),
        Unit,
        Unit,
        solverState,
        new ISolveRule[IRulexSR, IRuneS, Unit, Unit, Boolean, IIdentifiabilityRuleError] {
          override def sanityCheckConclusion(env: Unit, state: Unit, rune: IRuneS, conclusion: Boolean): Unit = {}

          override def complexSolve(state: Unit, env: Unit, solverState: ISolverState[IRulexSR, IRuneS, Boolean], stepState: IStepState[IRulexSR, IRuneS, Boolean]): Result[Unit, ISolverError[IRuneS, Boolean, IIdentifiabilityRuleError]] = {
            Ok(())
          }

          override def solve(state: Unit, env: Unit, solverState: ISolverState[IRulexSR, IRuneS, Boolean], ruleIndex: Int, rule: IRulexSR, stepState: IStepState[IRulexSR, IRuneS, Boolean]): Result[Unit, ISolverError[IRuneS, Boolean, IIdentifiabilityRuleError]] = {
            solveRule(state, env, ruleIndex, callRange, rule, stepState)
          }
        }) match {
        case Ok((steps, conclusionsStream)) => (steps, conclusionsStream.toMap)
        case Err(e) => return Err(IdentifiabilitySolveError(callRange, e))
      }
    val allRunes = solverState.getAllRunes().map(solverState.getUserRune)
    val unsolvedRunes = allRunes -- conclusions.keySet
    if (unsolvedRunes.nonEmpty) {
      Err(
        IdentifiabilitySolveError(
          callRange,
          IncompleteSolve(
            steps,
            solverState.getUnsolvedRules(),
            unsolvedRunes,
            conclusions)))
    } else {
      Ok(conclusions)
    }
  }
}
