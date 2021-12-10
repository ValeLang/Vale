package net.verdagon.vale.scout

import net.verdagon.vale._
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.solver.{FailedSolve, IIncompleteOrFailedSolve, ISolveRule, ISolverError, IStepState, IncompleteSolve, Solver, SolverConflict}

import scala.collection.immutable.Map

case class RuneTypeSolveError(range: RangeS, failedSolve: IIncompleteOrFailedSolve[IRulexSR, IRuneS, ITemplataType, IRuneTypeRuleError]) {
  vpass()
}

sealed trait IRuneTypeRuleError
case class LookupDidntMatchExpectedType(range: RangeS, expectedType: ITemplataType, actualType: ITemplataType) extends IRuneTypeRuleError

object RuneTypeSolver {
  def getRunes(rule: IRulexSR): Array[IRuneS] = {
    val sanityCheck =
      rule match {
        case LookupSR(range, rune, literal) => Array(rune)
        case RuneParentEnvLookupSR(range, rune) => Array(rune)
        case EqualsSR(range, left, right) => Array(left, right)
        case CoordIsaSR(range, sub, suuper) => Array(sub, suuper)
        case KindIsaSR(range, sub, suuper) => Array(sub, suuper)
        case KindComponentsSR(range, resultRune, mutabilityRune) => Array(resultRune, mutabilityRune)
        case CoordComponentsSR(range, resultRune, ownershipRune, permissionRune, kindRune) => Array(resultRune, ownershipRune, permissionRune, kindRune)
        case PrototypeComponentsSR(range, resultRune, nameRune, paramsListRune, returnRune) => Array(resultRune, nameRune, paramsListRune, returnRune)
        case OneOfSR(range, rune, literals) => Array(rune)
        case IsConcreteSR(range, rune) => Array(rune)
        case IsInterfaceSR(range, rune) => Array(rune)
        case IsStructSR(range, rune) => Array(rune)
        case CoerceToCoordSR(range, coordRune, kindRune) => Array(coordRune, kindRune)
        case LiteralSR(range, rune, literal) => Array(rune)
        case AugmentSR(range, resultRune, literal, innerRune) => Array(resultRune, innerRune)
        case CallSR(range, resultRune, templateRune, args) => Array(resultRune, templateRune) ++ args
        case PrototypeSR(range, resultRune, name, parameters, returnTypeRune) => Array(resultRune) ++ parameters ++ Array(returnTypeRune)
        case PackSR(range, resultRune, members) => Array(resultRune) ++ members
        case RepeaterSequenceSR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => Array(resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune)
//        case ManualSequenceSR(range, resultRune, elements) => Array(resultRune) ++ elements
        case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => Array(resultRune, coordListRune)
//        case CoordListSR(range, resultRune, elements) => Array(resultRune) ++ elements
      }
    val result = rule.runeUsages
    vassert(result.map(_.rune) sameElements sanityCheck.map(_.rune))
    result.map(_.rune)
  }

  def getPuzzles(predicting: Boolean, rule: IRulexSR): Array[Array[IRuneS]] = {
    rule match {
      case EqualsSR(_, leftRune, rightRune) => Array(Array(leftRune.rune), Array(rightRune.rune))
      case LookupSR(_, rune, _) => {
        if (predicting) {
          // This Array() literally means nothing can solve this puzzle.
          // It needs to be passed in via plan/solve's initiallyKnownRunes parameter.
          Array()
        } else {
          // We need to know the type beforehand, because we don't know if we'll be coercing or not.
          Array(Array(rune.rune))
        }
      }
      case RuneParentEnvLookupSR(_, rune) => {
        if (predicting) {
          // This Array() literally means nothing can solve this puzzle.
          // It needs to be passed in via plan/solve's initiallyKnownRunes parameter.
          Array()
        } else {
          // We need to know the type beforehand, because we don't know if we'll be coercing or not.
          Array(Array(rune.rune))
        }
      }
      case CallSR(range, resultRune, templateRune, args) => {
        // We can't determine the template from the result and args because we might be coercing its
        // returned kind to a coord. So we need the template.
        // We can't determine the return type because we don't know whether we're coercing or not.
        Array(Array(resultRune.rune, templateRune.rune))
      }
      case PackSR(_, resultRune, members) => {
        // Packs are always lists of coords
        Array(Array())
      }
      case CoordIsaSR(_, subRune, superRune) => Array(Array())
      case KindComponentsSR(_, resultRune, mutabilityRune) => Array(Array())
      case CoordComponentsSR(_, resultRune, ownershipRune, permissionRune, kindRune) => Array(Array())
      case PrototypeComponentsSR(_, resultRune, nameRune, paramsListRune, returnRune) => Array(Array())
      case OneOfSR(_, rune, literals) => Array(Array())
      case IsConcreteSR(_, rune) => Array(Array(rune.rune))
      case IsInterfaceSR(_, rune) => Array(Array())
      case IsStructSR(_, rune) => Array(Array())
      case CoerceToCoordSR(_, coordRune, kindRune) => Array(Array())
      case LiteralSR(_, rune, literal) => Array(Array())
      case AugmentSR(_, resultRune, literals, innerRune) => Array(Array())
      case RepeaterSequenceSR(_, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => Array(Array(resultRune.rune))
//      case ManualSequenceSR(_, resultRune, elements) => Array(Array(resultRune.rune))
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => Array(Array())
        // solverState.addPuzzle(ruleIndex, Array(senderRune, receiverRune))
//      case CoordListSR(_, resultRune, elements) => Array(Array())
    }
  }

  private def solveRule(
    state: Unit,
    env: IImpreciseNameS => ITemplataType,
    ruleIndex: Int,
    rule: IRulexSR,
    stepState: IStepState[IRulexSR, IRuneS, ITemplataType]):
  Result[Unit, ISolverError[IRuneS, ITemplataType, IRuneTypeRuleError]] = {
    rule match {
      case KindComponentsSR(range, resultRune, mutabilityRune) => {
        stepState.concludeRune(resultRune.rune, KindTemplataType)
        stepState.concludeRune(mutabilityRune.rune, MutabilityTemplataType)
        Ok(())
      }
      case CallSR(range, resultRune, templateRune, argRunes) => {
        vassertSome(stepState.getConclusion(templateRune.rune)) match {
          case TemplateTemplataType(paramTypes, returnType) => {
            argRunes.map(_.rune).zip(paramTypes).foreach({ case (argRune, paramType) =>
              stepState.concludeRune(argRune, paramType)
            })
            Ok(())
          }
          case other => vwat(other)
        }
      }
      case CoordComponentsSR(_, resultRune, ownershipRune, permissionRune, kindRune) => {
        stepState.concludeRune(resultRune.rune, CoordTemplataType)
        stepState.concludeRune(ownershipRune.rune, OwnershipTemplataType)
        stepState.concludeRune(permissionRune.rune, PermissionTemplataType)
        stepState.concludeRune(kindRune.rune, KindTemplataType)
        Ok(())
      }
      case PrototypeComponentsSR(_, resultRune, nameRune, paramListRune, returnRune) => {
        stepState.concludeRune(resultRune.rune, PrototypeTemplataType)
        stepState.concludeRune(nameRune.rune, StringTemplataType)
        stepState.concludeRune(paramListRune.rune, PackTemplataType(CoordTemplataType))
        stepState.concludeRune(returnRune.rune, CoordTemplataType)
        Ok(())
      }
      case CoordIsaSR(_, subRune, superRune) => {
        stepState.concludeRune(subRune.rune, CoordTemplataType)
        stepState.concludeRune(superRune.rune, CoordTemplataType)
        Ok(())
      }
      case OneOfSR(_, resultRune, literals) => {
        val types = literals.map(_.getType()).toSet
        if (types.size > 1) {
          vfail("OneOf rule's possibilities must all be the same type!")
        }
        stepState.concludeRune(resultRune.rune, types.head)
        Ok(())
      }
      case EqualsSR(_, leftRune, rightRune) => {
        stepState.getConclusion(leftRune.rune) match {
          case None => {
            stepState.concludeRune(leftRune.rune, vassertSome(stepState.getConclusion(rightRune.rune)))
            Ok(())
          }
          case Some(left) => {
            stepState.concludeRune(rightRune.rune, left)
            Ok(())
          }
        }
      }
      case IsConcreteSR(_, rune) => {
        stepState.concludeRune(rune.rune, KindTemplataType)
        Ok(())
      }
      case IsInterfaceSR(_, rune) => {
        stepState.concludeRune(rune.rune, KindTemplataType)
        Ok(())
      }
      case IsStructSR(_, rune) => {
        stepState.concludeRune(rune.rune, KindTemplataType)
        Ok(())
      }
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => {
        stepState.concludeRune(resultRune.rune, MutabilityTemplataType)
        stepState.concludeRune(coordListRune.rune, PackTemplataType(CoordTemplataType))
        Ok(())
      }
      case CoerceToCoordSR(_, coordRune, kindRune) => {
        stepState.concludeRune(kindRune.rune, KindTemplataType)
        stepState.concludeRune(coordRune.rune, CoordTemplataType)
        Ok(())
      }
      case LiteralSR(_, rune, literal) => {
        stepState.concludeRune(rune.rune, literal.getType())
        Ok(())
      }
      case LookupSR(range, rune, name) => {
        (env(name), vassertSome(stepState.getConclusion(rune.rune))) match {
          case (KindTemplataType, CoordTemplataType) =>
          case (TemplateTemplataType(Vector(), KindTemplataType), CoordTemplataType) =>
          case (TemplateTemplataType(Vector(), result), expected) if result == expected =>
          case (from, to) if from == to =>
          case (from, to) => {
            return Err(SolverConflict(rune.rune, to, from))
          }
        }
        Ok(())
      }
      case RuneParentEnvLookupSR(range, rune) => {
        (env(RuneNameS(rune.rune)), vassertSome(stepState.getConclusion(rune.rune))) match {
          case (KindTemplataType, CoordTemplataType) =>
          case (TemplateTemplataType(Vector(), KindTemplataType), CoordTemplataType) =>
          case (TemplateTemplataType(Vector(), result), expected) if result == expected =>
          case (from, to) if from == to =>
          case (from, to) => {
            return Err(SolverConflict(rune.rune, to, from))
          }
        }
        Ok(())
      }
      case LookupSR(range, rune, name) => {
        stepState.concludeRune(rune.rune, KindTemplataType)
        Ok(())
      }
      case AugmentSR(_, resultRune, literals, innerRune) => {
        stepState.concludeRune(resultRune.rune, CoordTemplataType)
        stepState.concludeRune(innerRune.rune, CoordTemplataType)
        Ok(())
      }
      case PackSR(_, resultRune, memberRunes) => {
        memberRunes.foreach(x => stepState.concludeRune(x.rune, CoordTemplataType))
        stepState.concludeRune(resultRune.rune, PackTemplataType(CoordTemplataType))
        Ok(())
      }
      case RepeaterSequenceSR(_, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => {
        stepState.concludeRune(mutabilityRune.rune, MutabilityTemplataType)
        stepState.concludeRune(variabilityRune.rune, VariabilityTemplataType)
        stepState.concludeRune(sizeRune.rune, IntegerTemplataType)
        stepState.concludeRune(elementRune.rune, CoordTemplataType)
        Ok(())
      }
    }
  }

  def solve(
    sanityCheck: Boolean,
    useOptimizedSolver: Boolean,
    env: IImpreciseNameS => ITemplataType,
    range: RangeS,
    predicting: Boolean,
    rules: IndexedSeq[IRulexSR],
    // Some runes don't appear in the rules, for example if they are in the identifying runes,
    // but not in any of the members or rules.
    additionalRunes: Iterable[IRuneS],
    expectCompleteSolve: Boolean,
    unpreprocessedInitiallyKnownRunes: Map[IRuneS, ITemplataType]):
  Result[Map[IRuneS, ITemplataType], RuneTypeSolveError] = {
    val initiallyKnownRunes =
      unpreprocessedInitiallyKnownRunes ++
        (if (predicting) {
          Map()
        } else {
          // Calculate what types we can beforehand, see KVCIE.
          rules.flatMap({
            case LookupSR(_, rune, name) => {
              env(name) match {
                // We don't know whether we'll interpret this kind as a coord.
                case KindTemplataType => List()
                case TemplateTemplataType(Vector(), KindTemplataType) => List()
                // If it's not a kind, then we'll use it as it is.
                case other => List(rune.rune -> other)
              }
            }
            case _ => List()
          }).toMap
        })
    val solverState =
      Solver.makeInitialSolverState(
        sanityCheck, useOptimizedSolver, rules, getRunes, (rule: IRulexSR) => getPuzzles(predicting, rule), initiallyKnownRunes)
    val (steps, conclusions) =
      Solver.solve[IRulexSR, IRuneS, IImpreciseNameS => ITemplataType, Unit, ITemplataType, IRuneTypeRuleError](
        (rule: IRulexSR) => getPuzzles(predicting, rule),
        Unit,
        env,
        solverState,
        new ISolveRule[IRulexSR, IRuneS, IImpreciseNameS => ITemplataType, Unit, ITemplataType, IRuneTypeRuleError] {
          override def complexSolve(state: Unit, env: IImpreciseNameS => ITemplataType, stepState: IStepState[IRulexSR, IRuneS, ITemplataType]): Result[Unit, ISolverError[IRuneS, ITemplataType, IRuneTypeRuleError]] = {
            Ok(())
          }
          override def solve(state: Unit, env: IImpreciseNameS => ITemplataType, ruleIndex: Int, rule: IRulexSR, stepState: IStepState[IRulexSR, IRuneS, ITemplataType]):
          Result[Unit, ISolverError[IRuneS, ITemplataType, IRuneTypeRuleError]] = {
            solveRule(state, env, ruleIndex, rule, stepState)
          }
        }) match {
        case Ok((steps, conclusionsStream)) => (steps.toVector, conclusionsStream.toMap)
        case Err(e) => return Err(RuneTypeSolveError(range, e))
      }
    val allRunes = solverState.getAllRunes().map(solverState.getUserRune) ++ additionalRunes
    val unsolvedRunes = allRunes -- conclusions.keySet
    if (expectCompleteSolve && unsolvedRunes.nonEmpty) {
      Err(
        RuneTypeSolveError(
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
