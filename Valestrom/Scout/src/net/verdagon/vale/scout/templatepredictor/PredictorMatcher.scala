package net.verdagon.vale.scout.templatepredictor

import net.verdagon.vale._
import net.verdagon.vale.scout.{IEnvironment => _, FunctionEnvironment => _, Environment => _, _}
import net.verdagon.vale.scout.predictor.ConclusionsBox
import net.verdagon.vale.scout.rules._

object PredictorMatcher {
  def matchAgainstTemplexSR(
      conclusions: ConclusionsBox,
      rule: ITemplexS):
  Unit = {
    rule match {
      case IntST(_) =>
      case BoolST(_) =>
      case MutabilityST(_) =>
      case PermissionST(_) =>
      case LocationST(_) =>
      case OwnershipST(_) =>
      case VariabilityST(_) =>
      case NameST(_) =>
      case AbsoluteNameST(_) =>
      case RuneST(rune) => conclusions.markRuneValueKnowable(rune)
      case CallST(template, args) => {
        matchAgainstTemplexSR(conclusions, template)
        args.foreach(matchAgainstTemplexSR(conclusions, _))
      }
      case OwnershippedST(_, inner) => matchAgainstTemplexSR(conclusions, inner)
      case RepeaterSequenceST(mutabilityRule, sizeRule,elementRule) => {
        matchAgainstTemplexSR(conclusions, mutabilityRule)
        matchAgainstTemplexSR(conclusions, sizeRule)
        matchAgainstTemplexSR(conclusions, elementRule)
      }
      case _ => vimpl()
    }
  }

  def matchAgainstRulexSR(conclusions: ConclusionsBox, irule: IRulexSR): Unit = {
    irule match {
      case rule @ EqualsSR(_, _) => matchAgainstEqualsSR(conclusions, rule)
      case rule @ OrSR(_) => matchAgainstOrSR(conclusions, rule)
      case rule @ ComponentsSR(_, _) => matchAgainstComponentsSR(conclusions, rule)
      case rule @ TypedSR(_, _) => matchAgainstTypedSR(conclusions, rule)
      case TemplexSR(itemplexST) => matchAgainstTemplexSR(conclusions, itemplexST)
      case rule @ CallSR(_, _) => matchAgainstCallSR(conclusions, rule)
    }
  }

  def matchAgainstTypedSR(conclusions: ConclusionsBox, rule: TypedSR): Unit = {
    val TypedSR(rune, _) = rule
    conclusions.markRuneValueKnowable(rune)
  }

  def matchAgainstCallSR(conclusions: ConclusionsBox, rule: CallSR): Unit = {
    val CallSR(_, argRules) = rule

    // We don't do anything with the argRules; we don't evaluate or match them here, see MDMIA.
    val _ = argRules

    // We could check that the types are good, but we already do that in the evaluate layer.
    // So... nothing to do here!
  }

  def matchAgainstComponentsSR(conclusions: ConclusionsBox, rule: ComponentsSR): Unit = {
    val ComponentsSR(container, components) = rule
    matchAgainstTypedSR(conclusions, container)
    components.foreach(matchAgainstRulexSR(conclusions, _))
  }

  def matchAgainstEqualsSR(conclusions: ConclusionsBox, rule: EqualsSR): Unit = {
    val EqualsSR(left, right) = rule
    matchAgainstRulexSR(conclusions, left)
    matchAgainstRulexSR(conclusions, right)
  }

  def matchAgainstOrSR(conclusions: ConclusionsBox, rule: OrSR): Unit = {
    // Do nothing... information doesn't flow downwards into Ors
  }
}
