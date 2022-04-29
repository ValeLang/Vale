package dev.vale.postparsing

import dev.vale.postparsing.rules.{AugmentSR, CallSR, CoerceToCoordSR, CoordComponentsSR, CoordIsaSR, EqualsSR, IRulexSR, IsConcreteSR, IsInterfaceSR, IsStructSR, KindComponentsSR, KindIsaSR, LiteralSR, LookupSR, OneOfSR, PackSR, PrototypeComponentsSR, RefListCompoundMutabilitySR, RuneParentEnvLookupSR, RuntimeSizedArraySR, StaticSizedArraySR}
import dev.vale.solver.{IIncompleteOrFailedSolve, ISolveRule, ISolverError, IStepState, IncompleteSolve, Solver}
import dev.vale.{Err, Ok, RangeS, Result, vassert, vimpl, vpass}
import dev.vale._
import dev.vale.postparsing.rules._
import dev.vale.solver.IStepState

import scala.collection.immutable.Map

case class IdentifiabilitySolveError(range: RangeS, failedSolve: IIncompleteOrFailedSolve[IRulexSR, IRuneS, Boolean, IIdentifiabilityRuleError]) {
  vpass()
}

sealed trait IIdentifiabilityRuleError

object IdentifiabilitySolver {
  def getRunes(rule: IRulexSR): Array[IRuneS] = {
    val sanityCheck =
      rule match {
        case LookupSR(range, rune, literal) => Array(rune)
        case RuneParentEnvLookupSR(range, rune) => Array(rune)
        case EqualsSR(range, left, right) => Array(left, right)
        case CoordIsaSR(range, sub, suuper) => Array(sub, suuper)
        case KindIsaSR(range, sub, suuper) => Array(sub, suuper)
        case KindComponentsSR(range, resultRune, mutabilityRune) => Array(resultRune, mutabilityRune)
        case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => Array(resultRune, ownershipRune, kindRune)
        case PrototypeComponentsSR(range, resultRune, nameRune, paramsListRune, returnRune) => Array(resultRune, nameRune, paramsListRune, returnRune)
        case OneOfSR(range, rune, literals) => Array(rune)
        case IsConcreteSR(range, rune) => Array(rune)
        case IsInterfaceSR(range, rune) => Array(rune)
        case IsStructSR(range, rune) => Array(rune)
        case CoerceToCoordSR(range, coordRune, kindRune) => Array(coordRune, kindRune)
        case LiteralSR(range, rune, literal) => Array(rune)
        case AugmentSR(range, resultRune, ownership, innerRune) => Array(resultRune, innerRune)
        case CallSR(range, resultRune, templateRune, args) => Array(resultRune, templateRune) ++ args
//        case PrototypeSR(range, resultRune, name, parameters, returnTypeRune) => Array(resultRune) ++ parameters ++ Array(returnTypeRune)
        case PackSR(range, resultRune, members) => Array(resultRune) ++ members
        case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => Array(resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune)
        case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) => Array(resultRune, mutabilityRune, elementRune)
//        case ManualSequenceSR(range, resultRune, elements) => Array(resultRune) ++ elements
        case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => Array(resultRune, coordListRune)
//        case CoordListSR(range, resultRune, elements) => Array(resultRune) ++ elements
      }
    val result = rule.runeUsages
    vassert(result.map(_.rune) sameElements sanityCheck.map(_.rune))
    result.map(_.rune)
  }

  def getPuzzles(rule: IRulexSR): Array[Array[IRuneS]] = {
    rule match {
      case EqualsSR(_, leftRune, rightRune) => Array(Array(leftRune.rune), Array(rightRune.rune))
      case LookupSR(_, rune, _) => Array(Array())
      case RuneParentEnvLookupSR(_, rune) => {
        // This Array() literally means nothing can solve this puzzle.
        // It needs to be passed in via identifying rune.
        Array()
      }
      case CallSR(range, resultRune, templateRune, args) => {
        // We can't determine the template from the result and args because we might be coercing its
        // returned kind to a coord. So we need the template.
        // We can't determine the return type because we don't know whether we're coercing or not.
        Array(Array(resultRune.rune, templateRune.rune), Array(templateRune.rune) ++ args.map(_.rune))
      }
      case PackSR(_, resultRune, members) => {
        // Packs are always lists of coords
        Array(Array(resultRune.rune), members.map(_.rune))
      }
      case CoordIsaSR(_, subRune, superRune) => Array(Array())
      case KindComponentsSR(_, resultRune, mutabilityRune) => Array(Array())
      case CoordComponentsSR(_, resultRune, ownershipRune, kindRune) => Array(Array())
      case PrototypeComponentsSR(_, resultRune, nameRune, paramsListRune, returnRune) => Array(Array())
      case OneOfSR(_, rune, literals) => Array(Array())
      case IsConcreteSR(_, rune) => Array(Array(rune.rune))
      case IsInterfaceSR(_, rune) => Array(Array())
      case IsStructSR(_, rune) => Array(Array())
      case CoerceToCoordSR(_, coordRune, kindRune) => Array(Array())
      case LiteralSR(_, rune, literal) => Array(Array())
      case AugmentSR(_, resultRune, ownership, innerRune) => Array(Array())
      case StaticSizedArraySR(_, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => Array(Array(resultRune.rune), Array(mutabilityRune.rune, variabilityRune.rune, sizeRune.rune, elementRune.rune))
      case RuntimeSizedArraySR(_, resultRune, mutabilityRune, elementRune) => Array(Array(resultRune.rune), Array(mutabilityRune.rune, elementRune.rune))
//      case ManualSequenceSR(_, resultRune, elements) => Array(Array(resultRune.rune))
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => Array(Array())
        // solverState.addPuzzle(ruleIndex, Array(senderRune, receiverRune))
//      case CoordListSR(_, resultRune, elements) => Array(Array())
    }
  }

  private def solveRule(
    state: Unit,
    env: Unit,
    ruleIndex: Int,
    rule: IRulexSR,
    stepState: IStepState[IRulexSR, IRuneS, Boolean]):
  Result[Unit, ISolverError[IRuneS, Boolean, IIdentifiabilityRuleError]] = {
    rule match {
      case KindComponentsSR(range, resultRune, mutabilityRune) => {
        stepState.concludeRune(resultRune.rune, true)
        stepState.concludeRune(mutabilityRune.rune, true)
        Ok(())
      }
      case CallSR(range, resultRune, templateRune, argRunes) => {
        stepState.concludeRune(resultRune.rune, true)
        stepState.concludeRune(templateRune.rune, true)
        argRunes.map(_.rune).foreach({ case argRune =>
          stepState.concludeRune(argRune, true)
        })
        Ok(())
      }
      case CoordComponentsSR(_, resultRune, ownershipRune, kindRune) => {
        stepState.concludeRune(resultRune.rune, true)
        stepState.concludeRune(ownershipRune.rune, true)
        stepState.concludeRune(kindRune.rune, true)
        Ok(())
      }
      case PrototypeComponentsSR(_, resultRune, nameRune, paramListRune, returnRune) => {
        stepState.concludeRune(resultRune.rune, true)
        stepState.concludeRune(nameRune.rune, true)
        stepState.concludeRune(paramListRune.rune, true)
        stepState.concludeRune(returnRune.rune, true)
        Ok(())
      }
      case CoordIsaSR(_, subRune, superRune) => {
        stepState.concludeRune(subRune.rune, true)
        stepState.concludeRune(superRune.rune, true)
        Ok(())
      }
      case OneOfSR(_, resultRune, literals) => {
        stepState.concludeRune(resultRune.rune, true)
        Ok(())
      }
      case EqualsSR(_, leftRune, rightRune) => {
        stepState.concludeRune(leftRune.rune, true)
        stepState.concludeRune(rightRune.rune, true)
        Ok(())
      }
      case IsConcreteSR(_, rune) => {
        stepState.concludeRune(rune.rune, true)
        Ok(())
      }
      case IsInterfaceSR(_, rune) => {
        stepState.concludeRune(rune.rune, true)
        Ok(())
      }
      case IsStructSR(_, rune) => {
        stepState.concludeRune(rune.rune, true)
        Ok(())
      }
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => {
        stepState.concludeRune(resultRune.rune, true)
        stepState.concludeRune(coordListRune.rune, true)
        Ok(())
      }
      case CoerceToCoordSR(_, coordRune, kindRune) => {
        stepState.concludeRune(kindRune.rune, true)
        stepState.concludeRune(coordRune.rune, true)
        Ok(())
      }
      case LiteralSR(_, rune, literal) => {
        stepState.concludeRune(rune.rune, true)
        Ok(())
      }
      case LookupSR(range, rune, name) => {
        stepState.concludeRune(rune.rune, true)
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
        stepState.concludeRune(rune.rune, true)
        Ok(())
      }
      case AugmentSR(_, resultRune, ownership, innerRune) => {
        stepState.concludeRune(resultRune.rune, true)
        stepState.concludeRune(innerRune.rune, true)
        Ok(())
      }
      case PackSR(_, resultRune, memberRunes) => {
        memberRunes.foreach(x => stepState.concludeRune(x.rune, true))
        stepState.concludeRune(resultRune.rune, true)
        Ok(())
      }
      case StaticSizedArraySR(_, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => {
        stepState.concludeRune(resultRune.rune, true)
        stepState.concludeRune(mutabilityRune.rune, true)
        stepState.concludeRune(variabilityRune.rune, true)
        stepState.concludeRune(sizeRune.rune, true)
        stepState.concludeRune(elementRune.rune, true)
        Ok(())
      }
      case RuntimeSizedArraySR(_, resultRune, mutabilityRune, elementRune) => {
        stepState.concludeRune(resultRune.rune, true)
        stepState.concludeRune(mutabilityRune.rune, true)
        stepState.concludeRune(elementRune.rune, true)
        Ok(())
      }
    }
  }

  def solve(
    sanityCheck: Boolean,
    useOptimizedSolver: Boolean,
    range: RangeS,
    rules: IndexedSeq[IRulexSR],
    identifyingRunes: Iterable[IRuneS]):
  Result[Map[IRuneS, Boolean], IdentifiabilitySolveError] = {
    val initiallyKnownRunes = identifyingRunes.map(r => (r, true)).toMap
    val solver =
      new Solver[IRulexSR, IRuneS, Unit, Unit, Boolean, IIdentifiabilityRuleError](
        sanityCheck, useOptimizedSolver)
    val solverState =
      solver
        .makeInitialSolverState(
          rules, getRunes, (rule: IRulexSR) => getPuzzles(rule), initiallyKnownRunes)
    val (steps, conclusions) =
      solver.solve(
        (rule: IRulexSR) => getPuzzles(rule),
        Unit,
        Unit,
        solverState,
        new ISolveRule[IRulexSR, IRuneS, Unit, Unit, Boolean, IIdentifiabilityRuleError] {
          override def complexSolve(state: Unit, env: Unit, stepState: IStepState[IRulexSR, IRuneS, Boolean]): Result[Unit, ISolverError[IRuneS, Boolean, IIdentifiabilityRuleError]] = {
            Ok(())
          }
          override def solve(state: Unit, env: Unit, ruleIndex: Int, rule: IRulexSR, stepState: IStepState[IRulexSR, IRuneS, Boolean]):
          Result[Unit, ISolverError[IRuneS, Boolean, IIdentifiabilityRuleError]] = {
            solveRule(state, env, ruleIndex, rule, stepState)
          }
        }) match {
        case Ok((steps, conclusionsStream)) => (steps.toVector, conclusionsStream.toMap)
        case Err(e) => return Err(IdentifiabilitySolveError(range, e))
      }
    val allRunes = solverState.getAllRunes().map(solverState.getUserRune)
    val unsolvedRunes = allRunes -- conclusions.keySet
    if (unsolvedRunes.nonEmpty) {
      Err(
        IdentifiabilitySolveError(
          range,
          IncompleteSolve(
            steps,
            solverState.getUnsolvedRules(),
            unsolvedRunes)))
    } else {
      Ok(conclusions)
    }
  }
}
