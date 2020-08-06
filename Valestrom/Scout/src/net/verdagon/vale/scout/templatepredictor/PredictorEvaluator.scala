package net.verdagon.vale.scout.templatepredictor

import net.verdagon.vale.scout.{IEnvironment => _, FunctionEnvironment => _, Environment => _, _}
import net.verdagon.vale.scout.patterns.{AtomSP, PatternSUtils}
import net.verdagon.vale.scout.predictor.{Conclusions, ConclusionsBox}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.vfail

import scala.collection.immutable.List

// Given enough user specified template params and param inputs, we should be able to
// infer everything.
// This class's purpose is to take those things, and see if it can figure out as many
// inferences as possible.

object PredictorEvaluator {

  private[scout] def getAllRunes(
    userSpecifiedIdentifyingRunes: List[IRuneS],
    rules: List[IRulexSR],
    patterns1: List[AtomSP],
    maybeRetRune: Option[IRuneS]
  ): Set[IRuneS] = {
    (
      userSpecifiedIdentifyingRunes ++
        patterns1.flatMap(PatternSUtils.getDistinctOrderedRunesForPattern) ++
        RuleSUtils.getDistinctOrderedRunesForRulexes(rules) ++
        maybeRetRune.toList
      ).toSet
  }

  private[scout] def solve(
    // See MKKRFA
    knowableRunesFromAbove: Set[IRuneS],
    rules: List[IRulexSR],
    paramAtoms: List[AtomSP],
  ): Conclusions = {
    val conclusionsBox = ConclusionsBox(Conclusions(knowableRunesFromAbove, Map()))
    solveUntilSettled(rules, conclusionsBox)
    conclusionsBox.conclusions
  }

  private def solveUntilSettled(
    rules: List[IRulexSR],
    conclusions: ConclusionsBox,
  ): Unit = {
    val conclusionsBefore = conclusions.conclusions

    val _ = evaluateRules(conclusions, rules)

    if (conclusions.conclusions != conclusionsBefore) {
      // Things have not settled, we made some sort of progress in this last iteration.
      // Keep going.
      solveUntilSettled(rules, conclusions)
    } else {
      // No need to do one last match, because we just did an entire iteration where nothing changed.

    }
  }

  private def evaluateRule(conclusions: ConclusionsBox, rule: IRulexSR): Boolean = {
    rule match {
      case r @ EqualsSR(_, _) => evaluateEqualsRule(conclusions, r)
      case r @ IsaSR(_, _) => evaluateIsaRule(conclusions, r)
      case r @ OrSR(_) => evaluateOrRule(conclusions, r)
      case r @ ComponentsSR(_, _) => evaluateComponentsRule(conclusions, r)
      case r @ TypedSR(_, _) => evaluateTypedRule(conclusions, r)
      case TemplexSR(templex) => evaluateTemplex(conclusions, templex)
      case r @ CallSR(_, _) => evaluateRuleCall(conclusions, r)
//      case r @ PackSR(_) => evaluatePackRule(conclusions, r)
    }
  }

//  private def evaluatePackRule(conclusions: ConclusionsBox, rule: PackSR): Boolean = {
//    val PackSR(elements) = rule
//    evaluateRules(conclusions, elements).forall(_ == true)
//  }

  private def evaluateRules(
    conclusions: ConclusionsBox,
    rules: List[IRulexSR],
  ): List[Boolean] = {
    rules.map(evaluateRule(conclusions, _))
  }

  private def evaluateRuleCall(
    conclusions: ConclusionsBox,
    ruleCall: CallSR,
  ): Boolean = {
    val CallSR(name, argumentRules) = ruleCall

    name match {
      case "toRef" => {
        val List(kindRule) = argumentRules
        evaluateRule(conclusions, kindRule)
      }
      case "passThroughIfConcrete" => {
        val List(kindRule) = argumentRules
        evaluateRule(conclusions, kindRule)
      }
      case "passThroughIfStruct" => {
        val List(kindRule) = argumentRules
        evaluateRule(conclusions, kindRule)
      }
      case "passThroughIfInterface" => {
        val List(kindRule) = argumentRules
        evaluateRule(conclusions, kindRule)
      }
//      case "resolveExactSignature" => {
//        val List(nameRule, argsRule) = argumentRules
//        val evaluateNameSuccess = evaluateRule(conclusions, nameRule)
//        val evaluateArgsSuccess = evaluateRule(conclusions, argsRule)
//        evaluateNameSuccess && evaluateArgsSuccess
//      }
      case _ => vfail("Unknown function \"" + name + "\"!");
    }
  }
  private def evaluateTemplexes(
    conclusions: ConclusionsBox,
    ruleTemplexes: List[ITemplexS],
  ): List[Boolean] = {
    val knowns =
      ruleTemplexes.map({
        case (ruleTemplex) => {
          val result = evaluateTemplex(conclusions, ruleTemplex)
          result
        }
      })
    knowns
  }

  private def evaluateTemplex(
    conclusions: ConclusionsBox,
    ruleTemplex: ITemplexS,
  ): Boolean = {
    ruleTemplex match {
      case IntST(_) => true
      case StringST(_) => true
      case BoolST(_) => true
      case MutabilityST(_) => true
      case PermissionST(_) => true
      case LocationST(_) => true
      case OwnershipST(_) => true
      case VariabilityST(_) => true
      case NameST(_, _) => true
      case AbsoluteNameST(_, _) => true
      case BorrowST(inner) => evaluateTemplex(conclusions, inner)
      case RuneST(rune) => {
        conclusions.knowableValueRunes.contains(rune)
      }
      case OwnershippedST(_, kindRule) => evaluateTemplex(conclusions, kindRule)
      case CallST(templateRule, paramRules) => {
        val templateKnown = evaluateTemplex(conclusions, templateRule)
        val argsKnown = evaluateTemplexes(conclusions, paramRules)
        templateKnown && argsKnown.forall(_ == true)
      }
      case PrototypeST(_, _, _) => {
        vfail("Unimplemented")
      }
      case PackST(memberTemplexes) => {
        val membersKnown =
          evaluateTemplexes(conclusions, memberTemplexes)
        membersKnown.forall(_ == true)
      }
      case RepeaterSequenceST(mutabilityTemplex, sizeTemplex, elementTemplex) => {
        val mutabilityKnown =
          evaluateTemplex(conclusions, mutabilityTemplex)
        val sizeKnown =
          evaluateTemplex(conclusions, sizeTemplex)
        val elementKnown =
          evaluateTemplex(conclusions, elementTemplex)
        mutabilityKnown && sizeKnown && elementKnown
      }
      case ManualSequenceST(elementsTemplexes) => {
        val membersKnown =
          evaluateTemplexes(conclusions, elementsTemplexes)
        membersKnown.forall(_ == true)
      }
    }
  }

  private def evaluateTypedRule(
    conclusions: ConclusionsBox,
    rule: TypedSR):
  Boolean = {
    val TypedSR(rune, tyype) = rule
    conclusions.markRuneTypeKnown(rune, tyype)
    conclusions.knowableValueRunes.contains(rune)
  }

  private def evaluateEqualsRule(
    conclusions: ConclusionsBox,
    rule: EqualsSR,
  ): Boolean = {
    val EqualsSR(leftRule, rightRule) = rule

    val leftKnown =
      evaluateRule(conclusions, leftRule)
    val rightKnown =
      evaluateRule(conclusions, rightRule)
    if (!leftKnown && !rightKnown) {
      false
    } else {
      PredictorMatcher.matchAgainstRulexSR(conclusions, leftRule)
      PredictorMatcher.matchAgainstRulexSR(conclusions, rightRule)
      true
    }
  }

  private def evaluateIsaRule(
    conclusions: ConclusionsBox,
    rule: IsaSR,
  ): Boolean = {
    val IsaSR(leftRule, rightRule) = rule

    val leftKnown =
      evaluateRule(conclusions, leftRule)
    val rightKnown =
      evaluateRule(conclusions, rightRule)

    // Knowing the right rule doesn't really help us with anything, unfortunately...
    val _ = rightKnown

    // We return the left thing for the rule, so if we know the left thing, we know the result of the rule.
    leftKnown
  }

  private def evaluateOrRule(
    conclusions: ConclusionsBox,
    rule: OrSR
  ): Boolean = {
    val possibilitiesKnowns =
      evaluateRules(conclusions, rule.alternatives)
    println("is this right?")
    // Just took a guess, really. Maybe we return true if one is known?
    possibilitiesKnowns.forall(_ == true)
  }

  private def evaluateComponentsRule(
    conclusions: ConclusionsBox,
    rule: ComponentsSR,
  ): Boolean = {
    val ComponentsSR(typedRule, componentsRules) = rule

    val runeKnown =
      evaluateRule(conclusions, typedRule)

    val componentsKnown =
      evaluateRules(conclusions, componentsRules)
    val allComponentsKnown = componentsKnown.forall(_ == true)

    runeKnown || allComponentsKnown
  }
}
