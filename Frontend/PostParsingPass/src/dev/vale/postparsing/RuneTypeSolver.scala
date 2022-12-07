package dev.vale.postparsing

import dev.vale.{Err, Interner, Ok, RangeS, Result, vassert, vassertSome, vfail, vpass, vwat}
import dev.vale.postparsing.rules._
import dev.vale.solver.{IIncompleteOrFailedSolve, ISolveRule, ISolverError, ISolverState, IStepState, IncompleteSolve, Solver, SolverConflict}
import dev.vale._
import dev.vale.postparsing.rules._

import scala.collection.immutable.Map

case class RuneTypeSolveError(range: List[RangeS], failedSolve: IIncompleteOrFailedSolve[IRulexSR, IRuneS, ITemplataType, IRuneTypeRuleError]) {
  vpass()
}

sealed trait IRuneTypeRuleError
case class LookupDidntMatchExpectedType(range: List[RangeS], expectedType: ITemplataType, actualType: ITemplataType) extends IRuneTypeRuleError

class RuneTypeSolver(interner: Interner) {
  def getRunes(rule: IRulexSR): Vector[IRuneS] = {
    val sanityCheck: Vector[RuneUsage] =
      rule match {
        case LookupSR(range, rune, literal) => Vector(rune)
        case RuneParentEnvLookupSR(range, rune) => Vector(rune)
        case EqualsSR(range, left, right) => Vector(left, right)
        case DefinitionCoordIsaSR(range, result, sub, suuper) => Vector(result, sub, suuper)
        case CallSiteCoordIsaSR(range, result, sub, suuper) => result.toVector ++ Vector(sub, suuper)
        case KindComponentsSR(range, resultRune, mutabilityRune) => Vector(resultRune, mutabilityRune)
        case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => Vector(resultRune, ownershipRune, kindRune)
        case PrototypeComponentsSR(range, resultRune, paramsRune, returnRune) => Vector(resultRune, paramsRune, returnRune)
        case ResolveSR(range, resultRune, name, paramsListRune, returnRune) => Vector(resultRune, paramsListRune, returnRune)
        case CallSiteFuncSR(range, resultRune, name, paramsListRune, returnRune) => Vector(resultRune, paramsListRune, returnRune)
        case DefinitionFuncSR(range, resultRune, name, paramsListRune, returnRune) => Vector(resultRune, paramsListRune, returnRune)
        case OneOfSR(range, rune, literals) => Vector(rune)
        case IsConcreteSR(range, rune) => Vector(rune)
        case IsInterfaceSR(range, rune) => Vector(rune)
        case IsStructSR(range, rune) => Vector(rune)
        case CoerceToCoordSR(range, coordRune, kindRune) => Vector(coordRune, kindRune)
        case LiteralSR(range, rune, literal) => Vector(rune)
        case AugmentSR(range, resultRune, ownership, innerRune) => Vector(resultRune, innerRune)
        case CallSR(range, resultRune, templateRune, args) => Vector(resultRune, templateRune) ++ args
//        case PrototypeSR(range, resultRune, name, parameters, returnTypeRune) => Vector(resultRune, returnTypeRune) ++ parameters
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

  def getPuzzles(predicting: Boolean, rule: IRulexSR): Vector[Vector[IRuneS]] = {
    rule match {
      case EqualsSR(range, leftRune, rightRune) => Vector(Vector(leftRune.rune), Vector(rightRune.rune))
      case LookupSR(range, rune, _) => {
        if (predicting) {
          // This Vector() literally means nothing can solve this puzzle.
          // It needs to be passed in via plan/solve's initiallyKnownRunes parameter.
          Vector()
        } else {
          // We need to know the type beforehand, because we don't know if we'll be coercing or not.
          Vector(Vector(rune.rune))
        }
      }
      case RuneParentEnvLookupSR(range, rune) => {
        if (predicting) {
          // This Vector() literally means nothing can solve this puzzle.
          // It needs to be passed in via plan/solve's initiallyKnownRunes parameter.
          Vector()
        } else {
          // We need to know the type beforehand, because we don't know if we'll be coercing or not.
          Vector(Vector(rune.rune))
        }
      }
      case CallSR(range, resultRune, templateRune, args) => {
        // We can't determine the template from the result and args because we might be coercing its
        // returned kind to a coord. So we need the template.
        // We can't determine the return type because we don't know whether we're coercing or not.
        Vector(Vector(resultRune.rune, templateRune.rune))
      }
      case PackSR(range, resultRune, members) => {
        // Packs are always lists of coords
        Vector(Vector())
      }
      case DefinitionCoordIsaSR(range, resultRune, subRune, superRune) => Vector(Vector())
      case CallSiteCoordIsaSR(range, resultRune, subRune, superRune) => Vector(Vector())
      case KindComponentsSR(range, resultRune, mutabilityRune) => Vector(Vector())
      case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => Vector(Vector())
      case PrototypeComponentsSR(range, resultRune, paramsRune, returnRune) => Vector(Vector())
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
//      case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => Vector(Vector(resultRune.rune))
//      case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) => Vector(Vector(resultRune.rune))
//      case ManualSequenceSR(range, resultRune, elements) => Vector(Vector(resultRune.rune))
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => Vector(Vector())
        // solverState.addPuzzle(ruleIndex, Vector(senderRune, receiverRune))
//      case CoordListSR(range, resultRune, elements) => Vector(Vector())
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
        stepState.concludeRune(List(range), resultRune.rune, KindTemplataType())
        stepState.concludeRune(List(range), mutabilityRune.rune, MutabilityTemplataType())
        Ok(())
      }
      case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => {
        stepState.concludeRune(List(range), resultRune.rune, CoordTemplataType())
        stepState.concludeRune(List(range), ownershipRune.rune, OwnershipTemplataType())
        stepState.concludeRune(List(range), kindRune.rune, KindTemplataType())
        Ok(())
      }
      case PrototypeComponentsSR(range, resultRune, paramsRune, returnRune) => {
        stepState.concludeRune(List(range), resultRune.rune, PrototypeTemplataType())
        stepState.concludeRune(List(range), paramsRune.rune, PackTemplataType(CoordTemplataType()))
        stepState.concludeRune(List(range), returnRune.rune, CoordTemplataType())
        Ok(())
      }
      case CallSR(range, resultRune, templateRune, argRunes) => {
        vassertSome(stepState.getConclusion(templateRune.rune)) match {
          case TemplateTemplataType(paramTypes, returnType) => {
            argRunes.map(_.rune).zip(paramTypes).foreach({ case (argRune, paramType) =>
              stepState.concludeRune(List(range), argRune, paramType)
            })
            Ok(())
          }
          case other => vwat(other)
        }
      }
      case ResolveSR(range, resultRune, name, paramListRune, returnRune) => {
        stepState.concludeRune(List(range), resultRune.rune, PrototypeTemplataType())
        stepState.concludeRune(List(range), paramListRune.rune, PackTemplataType(CoordTemplataType()))
        stepState.concludeRune(List(range), returnRune.rune, CoordTemplataType())
        Ok(())
      }
      case CallSiteFuncSR(range, resultRune, name, paramListRune, returnRune) => {
        stepState.concludeRune(List(range), resultRune.rune, PrototypeTemplataType())
        stepState.concludeRune(List(range), paramListRune.rune, PackTemplataType(CoordTemplataType()))
        stepState.concludeRune(List(range), returnRune.rune, CoordTemplataType())
        Ok(())
      }
      case DefinitionFuncSR(range, resultRune, name, paramListRune, returnRune) => {
        stepState.concludeRune(List(range), resultRune.rune, PrototypeTemplataType())
        stepState.concludeRune(List(range), paramListRune.rune, PackTemplataType(CoordTemplataType()))
        stepState.concludeRune(List(range), returnRune.rune, CoordTemplataType())
        Ok(())
      }
      case DefinitionCoordIsaSR(range, resultRune, subRune, superRune) => {
        stepState.concludeRune(List(range), resultRune.rune, ImplTemplataType())
        stepState.concludeRune(List(range), subRune.rune, CoordTemplataType())
        stepState.concludeRune(List(range), superRune.rune, CoordTemplataType())
        Ok(())
      }
      case CallSiteCoordIsaSR(range, resultRune, subRune, superRune) => {
        resultRune match {
          case Some(resultRune) => stepState.concludeRune(List(range), resultRune.rune, ImplTemplataType())
          case None =>
        }
        stepState.concludeRune(List(range), subRune.rune, CoordTemplataType())
        stepState.concludeRune(List(range), superRune.rune, CoordTemplataType())
        Ok(())
      }
      case OneOfSR(range, resultRune, literals) => {
        val types = literals.map(_.getType()).toSet
        if (types.size > 1) {
          vfail("OneOf rule's possibilities must all be the same type!")
        }
        stepState.concludeRune(List(range), resultRune.rune, types.head)
        Ok(())
      }
      case EqualsSR(range, leftRune, rightRune) => {
        stepState.getConclusion(leftRune.rune) match {
          case None => {
            stepState.concludeRune(List(range), leftRune.rune, vassertSome(stepState.getConclusion(rightRune.rune)))
            Ok(())
          }
          case Some(left) => {
            stepState.concludeRune(List(range), rightRune.rune, left)
            Ok(())
          }
        }
      }
      case IsConcreteSR(range, rune) => {
        stepState.concludeRune(List(range), rune.rune, KindTemplataType())
        Ok(())
      }
      case IsInterfaceSR(range, rune) => {
        stepState.concludeRune(List(range), rune.rune, KindTemplataType())
        Ok(())
      }
      case IsStructSR(range, rune) => {
        stepState.concludeRune(List(range), rune.rune, KindTemplataType())
        Ok(())
      }
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => {
        stepState.concludeRune(List(range), resultRune.rune, MutabilityTemplataType())
        stepState.concludeRune(List(range), coordListRune.rune, PackTemplataType(CoordTemplataType()))
        Ok(())
      }
      case CoerceToCoordSR(range, coordRune, kindRune) => {
        stepState.concludeRune(List(range), kindRune.rune, KindTemplataType())
        stepState.concludeRune(List(range), coordRune.rune, CoordTemplataType())
        Ok(())
      }
      case LiteralSR(range, rune, literal) => {
        stepState.concludeRune(List(range), rune.rune, literal.getType())
        Ok(())
      }
      case LookupSR(range, rune, name) => {
        (env(name), vassertSome(stepState.getConclusion(rune.rune))) match {
          case (KindTemplataType(), CoordTemplataType()) =>
          case (TemplateTemplataType(Vector(), KindTemplataType()), CoordTemplataType()) =>
          case (TemplateTemplataType(Vector(), result), expected) if result == expected =>
          case (from, to) if from == to =>
          case (from, to) => {
            return Err(SolverConflict(rune.rune, to, from))
          }
        }
        Ok(())
      }
      case RuneParentEnvLookupSR(range, rune) => {
        (env(interner.intern(RuneNameS(rune.rune))), vassertSome(stepState.getConclusion(rune.rune))) match {
          case (KindTemplataType(), CoordTemplataType()) =>
          case (TemplateTemplataType(Vector(), KindTemplataType()), CoordTemplataType()) =>
          case (TemplateTemplataType(Vector(), result), expected) if result == expected =>
          case (from, to) if from == to =>
          case (from, to) => {
            return Err(SolverConflict(rune.rune, to, from))
          }
        }
        Ok(())
      }
      case LookupSR(range, rune, name) => {
        stepState.concludeRune(List(range), rune.rune, KindTemplataType())
        Ok(())
      }
      case AugmentSR(range, resultRune, ownership, innerRune) => {
        stepState.concludeRune(List(range), resultRune.rune, CoordTemplataType())
        stepState.concludeRune(List(range), innerRune.rune, CoordTemplataType())
        Ok(())
      }
      case PackSR(range, resultRune, memberRunes) => {
        memberRunes.foreach(x => stepState.concludeRune(List(range), x.rune, CoordTemplataType()))
        stepState.concludeRune(List(range), resultRune.rune, PackTemplataType(CoordTemplataType()))
        Ok(())
      }
//      case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => {
//        stepState.concludeRune(List(range), mutabilityRune.rune, MutabilityTemplataType())
//        stepState.concludeRune(List(range), variabilityRune.rune, VariabilityTemplataType())
//        stepState.concludeRune(List(range), sizeRune.rune, IntegerTemplataType())
//        stepState.concludeRune(List(range), elementRune.rune, CoordTemplataType())
//        Ok(())
//      }
//      case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) => {
//        stepState.concludeRune(List(range), mutabilityRune.rune, MutabilityTemplataType())
//        stepState.concludeRune(List(range), elementRune.rune, CoordTemplataType())
//        Ok(())
//      }
    }
  }

  def solve(
    sanityCheck: Boolean,
    useOptimizedSolver: Boolean,
    env: IImpreciseNameS => ITemplataType,
    range: List[RangeS],
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
            case LookupSR(range, rune, name) => {
              env(name) match {
                // We don't know whether we'll interpret this kind as a coord.
                case KindTemplataType() => List()
                case TemplateTemplataType(Vector(), KindTemplataType()) => List()
                // If it's not a kind, then we'll use it as it is.
                case other => List(rune.rune -> other)
              }
            }
            case _ => List()
          }).toMap
        })
    val solver =
      new Solver[IRulexSR, IRuneS, IImpreciseNameS => ITemplataType, Unit, ITemplataType, IRuneTypeRuleError](
        sanityCheck,
        useOptimizedSolver,
        interner,
        (rule: IRulexSR) => getPuzzles(predicting, rule),
        getRunes,
        new ISolveRule[IRulexSR, IRuneS, IImpreciseNameS => ITemplataType, Unit, ITemplataType, IRuneTypeRuleError] {
          override def sanityCheckConclusion(env: IImpreciseNameS => ITemplataType, state: Unit, rune: IRuneS, conclusion: ITemplataType): Unit = {}

          override def complexSolve(state: Unit, env: IImpreciseNameS => ITemplataType, solverState: ISolverState[IRulexSR, IRuneS, ITemplataType], stepState: IStepState[IRulexSR, IRuneS, ITemplataType]): Result[Unit, ISolverError[IRuneS, ITemplataType, IRuneTypeRuleError]] = {
            Ok(())
          }

          override def solve(state: Unit, env: IImpreciseNameS => ITemplataType, solverState: ISolverState[IRulexSR, IRuneS, ITemplataType], ruleIndex: Int, rule: IRulexSR, stepState: IStepState[IRulexSR, IRuneS, ITemplataType]): Result[Unit, ISolverError[IRuneS, ITemplataType, IRuneTypeRuleError]] = {
            solveRule(state, env, ruleIndex, rule, stepState)
          }
        },
        range,
        rules,
        initiallyKnownRunes)
    while ({
      solver.advance(env, Unit) match {
        case Ok(continue) => continue
        case Err(e) => return Err(RuneTypeSolveError(range, e))
      }
    }) {}
    val steps = solver.getSteps().toStream
    val conclusions = solver.userifyConclusions().toMap

    val allRunes = solver.getAllRunes().map(solver.getUserRune) ++ additionalRunes
    val unsolvedRunes = allRunes -- conclusions.keySet
    if (expectCompleteSolve && unsolvedRunes.nonEmpty) {
      Err(
        RuneTypeSolveError(
          range,
          IncompleteSolve(
            steps,
            solver.getUnsolvedRules(),
            unsolvedRunes,
            conclusions)))
    } else {
      Ok(conclusions)
    }
  }
}
