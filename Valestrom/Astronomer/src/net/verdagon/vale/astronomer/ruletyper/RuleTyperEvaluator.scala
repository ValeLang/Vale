package net.verdagon.vale.astronomer.ruletyper

import net.verdagon.vale.astronomer.{INameA, ITemplataType, _}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.vfail

import scala.collection.immutable.List

trait IRuleTyperEvaluatorDelegate[Env, State] {
  def lookupType(state: State, env: Env, range: RangeS, name: CodeTypeNameS): ITemplataType
  def lookupType(state: State, env: Env, range: RangeS, name: INameS): ITemplataType
}

// Given enough user specified template params and param inputs, we should be able to
// infer everything.
// This class's purpose is to take those things, and see if it can figure out as many
// inferences as possible.

class RuleTyperEvaluator[Env, State](
  delegate: IRuleTyperEvaluatorDelegate[Env, State]) {

  def solve(
    state: State,
    env: Env,
    rules: List[IRulexSR],
    paramAtoms: List[AtomSP],
    maybeNeededRunes: Option[Set[IRuneA]]
  ): (Conclusions, IRuleTyperSolveResult[List[IRulexAR]]) = {
    // First, we feed into the system the things the user already specified.

    // This used to be a parameter for some reason... could move it back if needed.
    val conclusions = ConclusionsBox(Conclusions(Map()))

    // Now we'll try solving a bunch, just to see if there's any contradictions,
    // and if so bail out early.
    solveUntilSettled(state, env, rules, conclusions) match {
        case (isc @ RuleTyperSolveFailure(_, _, _)) => return (conclusions.conclusions, RuleTyperSolveFailure(conclusions, "Failed during pre-solving!", List(isc)))
        case (RuleTyperSolveSuccess(_)) =>
      }

    // Now we have template args the user specified, and we know there's no contradictions yet.

    // Next, we'll feed in the arguments that they used in the call.

    paramAtoms.zipWithIndex.foreach({
        case ((paramAtom, paramIndex)) => {
          makeMatcher().matchAgainstAtomSP(state, env, conclusions, paramAtom) match {
            case (isc @ RuleTyperMatchConflict(_, _, _)) => return (conclusions.conclusions, RuleTyperSolveFailure(conclusions, "Failed solving types for param " + paramIndex, List(isc)))
            case (RuleTyperMatchSuccess(_)) =>
          }
        }
      })

    val listOfMaybeRuleTypes =
      solveUntilSettled(state, env, rules, conclusions) match {
        case (isc @ RuleTyperSolveFailure(_, _, _)) => return (conclusions.conclusions, RuleTyperSolveFailure(conclusions, "Failed to solve!", List(isc)))
        case (RuleTyperSolveSuccess(rt)) => (rt)
      }

    // No need to do one last match, because we just did an entire iteration where nothing changed.

    val knowns = listOfMaybeRuleTypes.collect({ case Some(x) => x })
    if (knowns.size != listOfMaybeRuleTypes.size) {
      val unknownIndices =
        listOfMaybeRuleTypes.zipWithIndex.filter(_._1.isEmpty).map(_._2)
      return (
        conclusions.conclusions,
        RuleTyperSolveFailure(
          conclusions,
          "Couldn't figure out types of all rules! Couldn't figure out rules at indices: " + unknownIndices,
          List()))
    }

    val unfiguredOutRunes = maybeNeededRunes.getOrElse(Set()) -- conclusions.typeByRune.keySet
    if (unfiguredOutRunes.nonEmpty) {
      return (
        conclusions.conclusions,
        RuleTyperSolveFailure(
          conclusions,
          "Couldn't figure out types of all runes! Couldn't figure out: " + unfiguredOutRunes,
          List()))
    }

    (conclusions.conclusions, RuleTyperSolveSuccess(knowns))
  }

  private def solveUntilSettled(
    state: State,
    env: Env,
    rules: List[IRulexSR],
    conclusions: ConclusionsBox,
  ): (IRuleTyperSolveResult[List[Option[IRulexAR]]]) = {
    val results =
      rules.map(rule => {
        evaluateRule(state, env, conclusions, rule) match {
          case (iec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperSolveFailure(conclusions, "", List(iec)))
          case (RuleTyperEvaluateUnknown()) => None
          case (RuleTyperEvaluateSuccess(result)) => Some(result)
        }
      })

    if (conclusions != conclusions) {
      // Things have not settled, we made some sort of progress in this last iteration.
      // Keep going.
      solveUntilSettled(state, env, rules, conclusions)
    } else {
      (RuleTyperSolveSuccess(results))
    }
  }

  def evaluateRule(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    rule: IRulexSR,
  ): (IRuleTyperEvaluateResult[IRulexAR]) = {
    rule match {
      case r @ IsaSR(_, _) => evaluateIsaRule(state, env, conclusions, r)
      case r @ EqualsSR(_, _) => evaluateEqualsRule(state, env, conclusions, r)
      case r @ OrSR(_) => evaluateOrRule(state, env, conclusions, r)
      case r @ ComponentsSR(_, _) => evaluateComponentsRule(state, env, conclusions, r)
      case r @ TypedSR(_, _) => evaluateTypedRule(state, env, conclusions, r)
      case TemplexSR(templexS) => {
        evaluateTemplex(state, env, conclusions, templexS) match {
          case (rtec @ RuleTyperEvaluateConflict(_, _, _)) => (RuleTyperEvaluateConflict(conclusions.conclusions, "", Some(rtec)))
          case (RuleTyperEvaluateUnknown()) => (RuleTyperEvaluateUnknown())
          case (RuleTyperEvaluateSuccess(templexT)) => {
            (RuleTyperEvaluateSuccess(TemplexAR(templexT)))
          }
        }
      }
      case r @ CallSR(_, _) => evaluateRuleCall(state, env, conclusions, r)
      case other => vfail(other.toString)
    }
  }

  def evaluateRules(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    rules: List[IRulexSR],
  ): (IRuleTyperEvaluateResult[List[IRulexAR]]) = {
    val initialResult: IRuleTyperEvaluateResult[List[IRulexAR]] =
      RuleTyperEvaluateSuccess(List())
    rules.zipWithIndex.foldLeft((initialResult))({
      case ((RuleTyperEvaluateUnknown()), (rule, index)) => {
        evaluateRule(state, env, conclusions, rule) match {
          case (iec @ RuleTyperEvaluateConflict(_, _, _)) => {
            return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed evaluating rule index " + index, Some(iec)))
          }
          case (RuleTyperEvaluateUnknown()) => {
            (RuleTyperEvaluateUnknown())
          }
          case (RuleTyperEvaluateSuccess(result)) => {
            // Throw it away; since one is unknown theyre all unknown
            val _ = result
            (RuleTyperEvaluateUnknown())
          }
        }
      }
      case ((RuleTyperEvaluateSuccess(previousResults)), (rule, index)) => {
        evaluateRule(state, env, conclusions, rule) match {
          case (iec @ RuleTyperEvaluateConflict(_, _, _)) => {
            return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed evaluating rule index " + index, Some(iec)))
          }
          case (RuleTyperEvaluateUnknown()) => {
            (RuleTyperEvaluateUnknown())
          }
          case (RuleTyperEvaluateSuccess(result)) => {
            (RuleTyperEvaluateSuccess(previousResults :+ result))
          }
        }
      }
    })
  }

  def evaluateRuleCall(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    ruleCall: CallSR,
  ): (IRuleTyperEvaluateResult[CallAR]) = {
    val CallSR(name, argumentRules) = ruleCall

    name match {
//      case "ownership" => {
//        val List(CoordTemplata(coord)) = argTemplatas
//        (RuleTyperEvaluateSuccess(OwnershipTemplata(coord.ownership)))
//      }
//      case "mutability" => {
//        val List(KindTemplata(kind)) = argTemplatas
//        val mutability = delegate.getMutability(kind)
//        (RuleTyperEvaluateSuccess(MutabilityTemplata(mutability)))
//      }
      case "toRef" => {
        if (argumentRules.size != 1) {
          return (RuleTyperEvaluateConflict(conclusions.conclusions, "toRef expects 1 argument, but received " + argumentRules.size, None))
        }
        val List(kindRule) = argumentRules
        makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, KindTemplataType, kindRule) match {
          case (rtmc @ RuleTyperMatchConflict(_, _, _)) => (RuleTyperEvaluateConflict(conclusions.conclusions, "Conflict in toRef argument!", Some(rtmc)))
          case (RuleTyperMatchSuccess(kindRuleT)) => {
            val ruleT = CallAR(name, List(kindRuleT), CoordTemplataType)
            (RuleTyperEvaluateSuccess(ruleT))
          }
        }
      }
      case "passThroughIfInterface" => {
        if (argumentRules.size != 1) {
          return (RuleTyperEvaluateConflict(conclusions.conclusions, "passThroughIfInterface expects 1 argument, but received " + argumentRules.size, None))
        }
        val List(kindRule) = argumentRules
        makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, KindTemplataType, kindRule) match {
          case (rtmc @ RuleTyperMatchConflict(_, _, _)) => (RuleTyperEvaluateConflict(conclusions.conclusions, "Conflict in toRef argument!", Some(rtmc)))
          case (RuleTyperMatchSuccess(kindRuleT)) => {
            val ruleT = CallAR(name, List(kindRuleT), KindTemplataType)
            (RuleTyperEvaluateSuccess(ruleT))
          }
        }
      }
      case "resolveExactSignature" => {
        if (argumentRules.size != 2) {
          return (RuleTyperEvaluateConflict(conclusions.conclusions, "resolveExactSignature expects 2 argument, but received " + argumentRules.size, None))
        }
        val List(nameRule, argsRule) = argumentRules
        val nameRuleT =
          makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, StringTemplataType, nameRule) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return RuleTyperEvaluateConflict(conclusions.conclusions, "Conflict in toRef argument!", Some(rtmc))
            case (RuleTyperMatchSuccess(nameRuleT)) => nameRuleT
          }
        val argsListRuleT =
          makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, PackTemplataType(CoordTemplataType), argsRule) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return RuleTyperEvaluateConflict(conclusions.conclusions, "Conflict in toRef argument!", Some(rtmc))
            case (RuleTyperMatchSuccess(nameRuleT)) => nameRuleT
          }
        val ruleT = CallAR("resolveExactSignature", List(nameRuleT, argsListRuleT), PrototypeTemplataType)
        RuleTyperEvaluateSuccess(ruleT)
      }
      case _ => vfail("Unknown function \"" + name + "\"!");
    }
  }

  def evaluateTemplex(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    ruleTemplex: ITemplexS,
  ): (IRuleTyperEvaluateResult[ITemplexA]) = {
    ruleTemplex match {
      case IntST(value) => (RuleTyperEvaluateSuccess(IntAT(value)))
      case BoolST(value) => (RuleTyperEvaluateSuccess(BoolAT(value)))
      case MutabilityST(value) => (RuleTyperEvaluateSuccess(MutabilityAT(value)))
      case PermissionST(value) => (RuleTyperEvaluateSuccess(PermissionAT(value)))
      case LocationST(value) => (RuleTyperEvaluateSuccess(LocationAT(value)))
      case OwnershipST(value) => (RuleTyperEvaluateSuccess(OwnershipAT(value)))
      case VariabilityST(value) => (RuleTyperEvaluateSuccess(VariabilityAT(value)))
      case NameST(range, nameS) => {
        delegate.lookupType(state, env, range, nameS) match {
          case (KindTemplataType) => {
            // The thing identified by `name` is a kind, but we don't know whether we're trying to access it
            // as a kind, or trying to access it like a coord.
            // Kinds from the outside are ambiguous until we know from context whether we're trying to use
            // them like a kind or a coord.
            (RuleTyperEvaluateUnknown())
          }
          case (otherType) => {
            val nameA = Astronomer.translateImpreciseName(nameS)
            (RuleTyperEvaluateSuccess(NameAT(nameA, otherType)))
          }
        }
      }
      case RuneST(runeS) => {
        val runeA = Astronomer.translateRune(runeS)
        conclusions.typeByRune.get(runeA) match {
          case Some(tyype) => (RuleTyperEvaluateSuccess(RuneAT(runeA, tyype)))
          case None => (RuleTyperEvaluateUnknown())
        }
      }
      case OwnershippedST(ownershipP, innerCoordTemplexS) => {
        makeMatcher().matchTypeAgainstTemplexS(state, env, conclusions, CoordTemplataType, innerCoordTemplexS) match {
          case (rtmc @ RuleTyperMatchConflict(_, _, _)) => (RuleTyperEvaluateConflict(conclusions.conclusions, "Conflict in inner coord part!", Some(rtmc)))
          case (RuleTyperMatchUnknown()) => {
            (RuleTyperEvaluateUnknown())
          }
          case (RuleTyperMatchSuccess(kindTemplexT)) => {
            val templexT = OwnershippedAT(ownershipP, kindTemplexT)
            (RuleTyperEvaluateSuccess(templexT))
          }
        }
      }
      case CallST(templateRule, paramRules) => {
        val maybeTemplateT =
          evaluateTemplex(state, env, conclusions, templateRule) match {
            case (iec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "bogglewogget", Some(iec)))
            case (RuleTyperEvaluateUnknown()) => (None)
            case (RuleTyperEvaluateSuccess(templexT)) => {
              templexT.resultType match {
                case TemplateTemplataType(_, _) =>
                case _ => {
                  return (RuleTyperEvaluateConflict(conclusions.conclusions, "Trying to call something that's not a template! Is actually: " + templexT.resultType, None))
                }
              }
              (Some(templexT))
            }
          }

        maybeTemplateT match {
          case None => {
            // We don't know the template type, so we can't know the resulting type and can't assemble
            // the CallAR... but evaluating the arguments anyway might yield clues as to the types of
            // the runes, so evaluate them anyway.

            paramRules.zipWithIndex.foreach({
                case ((paramRule, paramIndex)) => {
                  evaluateTemplex(state, env, conclusions, paramRule) match {
                    case (imc @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Conflict while evaluating param #" + paramIndex + "! " + paramRule, Some(imc)))
                    case (RuleTyperEvaluateUnknown()) =>
                    case (RuleTyperEvaluateSuccess(paramRuleT)) => {
                      // Throw it away; without knowing the template type, even with the argument types
                      // we can't know the return type.
                      val _ = paramRuleT
                    }
                  }
                }
              })
            (RuleTyperEvaluateUnknown())
          }
          case Some(templateT) => {
            val TemplateTemplataType(paramTypes, returnType) = templateT.resultType
            val maybeRulesT =
              paramTypes.zip(paramRules).zipWithIndex.map({
                case (((paramType, paramRule), paramIndex)) => {
                  makeMatcher().matchTypeAgainstTemplexS(state, env, conclusions, paramType, paramRule) match {
                    case (imc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Conflict while matching param #" + paramIndex + "! Was matching " + paramType + " and " + paramRule, Some(imc)))
                    case (RuleTyperMatchUnknown()) => None
                    case (RuleTyperMatchSuccess(paramTemplexT)) => Some(paramTemplexT)
                  }
                }
              })

            if (maybeRulesT.contains(None)) {
              (RuleTyperEvaluateUnknown())
            } else {
              returnType match {
                case KindTemplataType => {
                  // Return unknown, because we don't know if it should actually be a kind, or a coord.
                  // Only the matcher can figure this out.
                  (RuleTyperEvaluateUnknown())
                }
                case _ => {
                  (RuleTyperEvaluateSuccess(CallAT(templateT, maybeRulesT.flatten, returnType)))
                }
              }
            }
          }
        }
      }
      case PrototypeST(_, _, _) => {
        vfail("Unimplemented")
      }
      case PackST(_) => {
//        evaluateTemplexes(env, conclusions, memberTemplexes) match {
//          case (iec @ RuleTyperEvaluateConflict(_, _, _)) => {
//            return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed to evaluate CallST arguments", Some(iec)))
//          }
//          case (RuleTyperEvaluateUnknown()) => {
//            (RuleTyperEvaluateUnknown())
//          }
//          case (RuleTyperEvaluateSuccess(memberTemplatas)) => {
//            val memberCoords = memberTemplatas.collect({ case CoordTemplata(coord) => coord })
//            if (memberCoords.size != memberTemplatas.size) {
//              vfail("Packs can only take coords!")
//            }
//
//            val (packKind, _) = delegate.getPackKind(env, memberCoords)
//            (RuleTyperEvaluateSuccess(KindTemplata(packKind)))
//          }
//        }
        vfail()
      }
      case RepeaterSequenceST(mutabilityTemplexS, sizeTemplexS, elementTemplexS) => {
        // It's futile to try and get the templexTs for size and element, since we don't know whether this
        // thing will end up as a kind or coord (only matching can know that) but hey, let's match into
        // them anyway, they might provide some nice intel for our conclusions.

          makeMatcher().matchTypeAgainstTemplexS(state, env, conclusions, MutabilityTemplataType, mutabilityTemplexS) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Conflict in mutability part!", Some(rtmc)))
            case (RuleTyperMatchUnknown()) =>
            case (RuleTyperMatchSuccess(_)) =>
          }
          makeMatcher().matchTypeAgainstTemplexS(state, env, conclusions, IntegerTemplataType, sizeTemplexS) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Conflict in element part!", Some(rtmc)))
            case (RuleTyperMatchUnknown()) =>
            case (RuleTyperMatchSuccess(_)) =>
          }
          makeMatcher().matchTypeAgainstTemplexS(state, env, conclusions, CoordTemplataType, elementTemplexS) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Conflict in element part!", Some(rtmc)))
            case (RuleTyperMatchUnknown()) =>
            case (RuleTyperMatchSuccess(_)) =>
          }

        // We don't know whether this thing is expected to be a kind or a coord, only matching can figure that out.
        // Return unknown.
        (RuleTyperEvaluateUnknown())
      }
      case ManualSequenceST(_) => {
        vfail("Unimplemented")
      }
    }
  }

  def evaluateTypedRule(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    rule: TypedSR,
  ): (IRuleTyperEvaluateResult[TemplexAR]) = {
    val TypedSR(runeS, typeSR) = rule
    val runeA = Astronomer.translateRune(runeS)

    val templataType =
      typeSR match {
        case CoordTypeSR => CoordTemplataType
        case IntTypeSR => IntegerTemplataType
        case KindTypeSR => KindTemplataType
        case MutabilityTypeSR => MutabilityTemplataType
        case PrototypeTypeSR => PrototypeTemplataType
      }

    conclusions.typeByRune.get(runeA) match {
      case None =>
      case Some(typeFromConclusions) => {
        if (typeFromConclusions != templataType) {
          return (RuleTyperEvaluateConflict(conclusions.conclusions, "Typed rule failed: expected rune " + runeA + " to be " + templataType + " but previously concluded " + typeFromConclusions, None))
        }
      }
    }

    makeMatcher().matchTypeAgainstTypedSR(state, env, conclusions, templataType, rule) match {
      case (imc @ RuleTyperMatchConflict(_, _, _)) => (RuleTyperEvaluateConflict(conclusions.conclusions, "", Some(imc)))
      case (RuleTyperMatchUnknown()) => (RuleTyperEvaluateUnknown())
      case (RuleTyperMatchSuccess(ruleT)) => (RuleTyperEvaluateSuccess(ruleT))
    }
  }

  def evaluateIsaRule(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    rule: IsaSR,
  ): (IRuleTyperEvaluateResult[IsaAR]) = {
    val IsaSR(leftRuleS, rightRuleS) = rule

    val maybeLeftRuleT =
      makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, KindTemplataType, leftRuleS) match {
        case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed matching isa's left rule", Some(rtmc)))
        case (RuleTyperMatchUnknown()) => (None)
        case (RuleTyperMatchSuccess(leftRuleT)) => (Some(leftRuleT))
      }

    val maybeRightRuleT =
      makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, KindTemplataType, rightRuleS) match {
        case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed matching isa's right rule", Some(rtmc)))
        case (RuleTyperMatchUnknown()) => (None)
        case (RuleTyperMatchSuccess(rightRuleT)) => (Some(rightRuleT))
      }

    (maybeLeftRuleT, maybeRightRuleT) match {
      case (Some(leftRuleT), Some(rightRuleT)) => (RuleTyperEvaluateSuccess(IsaAR(leftRuleT, rightRuleT)))
      case (_, _) => (RuleTyperEvaluateUnknown())
    }
  }

  def evaluateEqualsRule(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    rule: EqualsSR,
  ): (IRuleTyperEvaluateResult[EqualsAR]) = {
    val EqualsSR(leftRuleS, rightRuleS) = rule

    val maybeLeftRuleT =
      evaluateRule(state, env, conclusions, leftRuleS) match {
        case (iec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed evaluating left rule!", Some(iec)))
        case (RuleTyperEvaluateUnknown()) => (None)
        case (RuleTyperEvaluateSuccess(leftRuleT)) => (Some(leftRuleT))
      }

    val maybeRightRuleT =
      evaluateRule(state, env, conclusions, rightRuleS) match {
        case (iec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed evaluating right rule!", Some(iec)))
        case (RuleTyperEvaluateUnknown()) => (None)
        case (RuleTyperEvaluateSuccess(rightRuleT)) => (Some(rightRuleT))
      }

    (maybeLeftRuleT, maybeRightRuleT) match {
      case (Some(leftRuleT), Some(rightRuleT)) => {
        (RuleTyperEvaluateSuccess(EqualsAR(leftRuleT, rightRuleT)))
      }
      case (Some(leftRuleT), None) => {
        // We know the left, but don't know the right. Use the type from the left
        // to try and figure out the thing on the right.
        makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, leftRuleT.resultType, rightRuleS) match {
          case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed matching right rule with type from left (" + leftRuleT.resultType + ")", Some(rtmc)))
          case (RuleTyperMatchUnknown()) => (RuleTyperEvaluateUnknown())
          case (RuleTyperMatchSuccess(rightRuleT)) => {
            (RuleTyperEvaluateSuccess(EqualsAR(leftRuleT, rightRuleT)))
          }
        }
      }
      case (None, Some(rightRuleT)) => {
        // We know the left, but don't know the right. Use the type from the left
        // to try and figure out the thing on the right.
        makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, rightRuleT.resultType, leftRuleS) match {
          case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed matching left rule with type from right (" + rightRuleT.resultType + ")", Some(rtmc)))
          case (RuleTyperMatchUnknown()) => (RuleTyperEvaluateUnknown())
          case (RuleTyperMatchSuccess(leftRuleT)) => {
            (RuleTyperEvaluateSuccess(EqualsAR(leftRuleT, rightRuleT)))
          }
        }
      }
      case (None, None) => {
        (RuleTyperEvaluateUnknown())
      }
    }
  }

  def evaluateOrRule(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    rule: OrSR
  ): (IRuleTyperEvaluateResult[OrAR]) = {
    val listOfMaybeAlternativeT =
      rule.alternatives.zipWithIndex.foldLeft((List[Option[IRulexAR]]()))({
        case ((Nil), (alternative, alternativeIndex)) => {
          evaluateRule(state, env, conclusions, alternative) match {
            case (rtec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed to evaluate alternative index " + alternativeIndex, Some(rtec)))
            case (RuleTyperEvaluateSuccess(alternativeRuleT)) => {
              (List(Some(alternativeRuleT)))
            }
            case (RuleTyperEvaluateUnknown()) => {
              (List(None))
            }
          }
        }
        case ((previousMaybeAlternativesT), (alternative, alternativeIndex)) => {
          val maybeKnownType = previousMaybeAlternativesT.flatten.headOption.map(_.resultType)
          maybeKnownType match {
            case None => {
              evaluateRule(state, env, conclusions, alternative) match {
                case (rtec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed to evaluate alternative index " + alternativeIndex, Some(rtec)))
                case (RuleTyperEvaluateUnknown()) => {
                  (previousMaybeAlternativesT :+ None)
                }
                case (RuleTyperEvaluateSuccess(alternativeRuleT)) => {
                  (previousMaybeAlternativesT :+ Some(alternativeRuleT))
                }
              }
            }
            case Some(knownType) => {
              makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, knownType, alternative) match {
                case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed to evaluate alternative index " + alternativeIndex, Some(rtmc)))
                case (RuleTyperMatchUnknown()) => {
                  (previousMaybeAlternativesT :+ None)
                }
                case (RuleTyperMatchSuccess(alternativeRuleT)) => {
                  (previousMaybeAlternativesT :+ Some(alternativeRuleT))
                }
              }
            }
          }
        }
      })
    if (listOfMaybeAlternativeT.contains(None)) {
      (RuleTyperEvaluateUnknown())
    } else {
      val alternativesT = listOfMaybeAlternativeT.flatten
      (RuleTyperEvaluateSuccess(OrAR(alternativesT)))
    }
  }

  def evaluateComponentsRule(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    rule: ComponentsSR,
  ): (IRuleTyperEvaluateResult[EqualsAR]) = {
    val ComponentsSR(typedRule, components) = rule

    val maybeTypeAndRuneRuleT =
      evaluateTypedRule(state, env, conclusions, typedRule) match {
        case (iec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Components rule type disagrees!", Some(iec)))
        case (RuleTyperEvaluateUnknown()) => (None)
        case (RuleTyperEvaluateSuccess(typeAndRuneRuleT)) => (Some(typeAndRuneRuleT))
      }

    val maybeComponentRulesT =
      typedRule.tyype match {
        case KindTypeSR => {
          evaluateKindComponents(state, env, conclusions, components) match {
            case (iec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed evaluating kind components!", Some(iec)))
            case (RuleTyperEvaluateUnknown()) => (None)
            case (RuleTyperEvaluateSuccess(templataFromRune)) => (Some(templataFromRune))
          }
        }
        case CoordTypeSR => {
          evaluateCoordComponents(state, env, conclusions, components) match {
            case (iec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed evaluating coord components!", Some(iec)))
            case (RuleTyperEvaluateUnknown()) => (None)
            case (RuleTyperEvaluateSuccess(templataFromRune)) => (Some(templataFromRune))
          }
        }
        case PrototypeTypeSR => {
          evaluatePrototypeComponents(state, env, conclusions, components) match {
            case (iec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Failed evaluating prototype components!", Some(iec)))
            case (RuleTyperEvaluateUnknown()) => (None)
            case (RuleTyperEvaluateSuccess(templataFromRune)) => (Some(templataFromRune))
          }
        }
        case _ => vfail("Can only destructure coords and kinds!")
      }

    (maybeTypeAndRuneRuleT, maybeComponentRulesT) match {
      case (Some(typeAndRuneRuleT), Some(componentRulesT)) => {
        val equalsT =
          EqualsAR(
            typeAndRuneRuleT,
            ComponentsAR(typeAndRuneRuleT.resultType, componentRulesT))
        (RuleTyperEvaluateSuccess(equalsT))
      }
      case (None, None) => {
        (RuleTyperEvaluateUnknown())
      }
    }
  }

  private def evaluateCoordComponents(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    components: List[IRulexSR]):
  (IRuleTyperEvaluateResult[List[IRulexAR]]) = {
    components match {
      case List(ownershipRuleS, kindRuleS) => {
        val maybeOwnershipRuleT =
          makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, OwnershipTemplataType, ownershipRuleS) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Ownership component conflicted!", Some(rtmc)))
            case (RuleTyperMatchUnknown()) => (None)
            case (RuleTyperMatchSuccess(ownershipRuleT)) => (Some(ownershipRuleT))
          }
//        val maybeLocationRuleT =
//          makeMatcher().matchTypeAgainstRulexSR(env, conclusions, LocationTemplataType, locationRuleS) match {
//            case rtmc @ RuleTyperMatchConflict(_, _, _) => return RuleTyperEvaluateConflict(conclusions.conclusions, "Location component conflicted!", Some(rtmc))
//            case RuleTyperMatchUnknown(c) => (c, None)
//            case RuleTyperMatchContinue(c, locationRuleT) => (c, Some(locationRuleT))
//          }
//        val maybePermissionRuleT =
//          makeMatcher().matchTypeAgainstRulexSR(env, conclusions, PermissionTemplataType, permissionRuleS) match {
//            case rtmc @ RuleTyperMatchConflict(_, _, _) => return RuleTyperEvaluateConflict(conclusions.conclusions, "Permission component conflicted!", Some(rtmc))
//            case RuleTyperMatchUnknown(c) => (c, None)
//            case RuleTyperMatchContinue(c, permissionRuleT) => (c, Some(permissionRuleT))
//          }
        val maybeKindRuleT =
          makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, KindTemplataType, kindRuleS) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Kind component conflicted!", Some(rtmc)))
            case (RuleTyperMatchUnknown()) => (None)
            case (RuleTyperMatchSuccess(kindRuleT)) => (Some(kindRuleT))
          }
        (maybeOwnershipRuleT, maybeKindRuleT) match {
          case (Some(ownershipRuleT), Some(kindRuleT)) => {
            (RuleTyperEvaluateSuccess(List(ownershipRuleT, kindRuleT)))
          }
          case (_, _) => {
            (RuleTyperEvaluateUnknown())
          }
        }

      }
      case _ => vfail("Coords must have 2 components")
    }
  }

  private def evaluatePrototypeComponents(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    components: List[IRulexSR]):
  (IRuleTyperEvaluateResult[List[IRulexAR]]) = {
    components match {
      case List(humanNameRuleS, argsPackRuleS, returnRuleS) => {
        val maybeHumanNameRuleT =
          makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, StringTemplataType, humanNameRuleS) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Prototype name component conflicted!", Some(rtmc)))
            case (RuleTyperMatchUnknown()) => (None)
            case (RuleTyperMatchSuccess(humanNameRuleT)) => (Some(humanNameRuleT))
          }
        val maybeCoordPackRuleT =
          makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, PackTemplataType(CoordTemplataType), argsPackRuleS) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => {
              return (RuleTyperEvaluateConflict(conclusions.conclusions, "Prototype args component conflicted!", Some(rtmc)))
            }
            case (RuleTyperMatchUnknown()) => (None)
            case (RuleTyperMatchSuccess(kindRuleT)) => (Some(kindRuleT))
          }
        val maybeCoordRuleT =
          makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, CoordTemplataType, returnRuleS) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Prototype return component conflicted!", Some(rtmc)))
            case (RuleTyperMatchUnknown()) => (None)
            case (RuleTyperMatchSuccess(kindRuleT)) => (Some(kindRuleT))
          }
        (maybeHumanNameRuleT, maybeCoordPackRuleT, maybeCoordRuleT) match {
          case (Some(humanNameRuleT), Some(coordPackRuleT), Some(coordRuleT)) => {
            (RuleTyperEvaluateSuccess(List(humanNameRuleT, coordPackRuleT, coordRuleT)))
          }
          case (_, _, _) => {
            (RuleTyperEvaluateUnknown())
          }
        }

      }
      case _ => vfail("Prototypes must have 3 components")
    }
  }

  private def evaluateKindComponents(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    components: List[IRulexSR]):
  (IRuleTyperEvaluateResult[List[IRulexAR]]) = {
    components match {
      case List(mutabilityRule) => {
        val maybeMutabilityRule =
          makeMatcher().matchTypeAgainstRulexSR(state, env, conclusions, MutabilityTemplataType, mutabilityRule) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperEvaluateConflict(conclusions.conclusions, "Mutability component conflicted!", Some(rtmc)))
            case (RuleTyperMatchUnknown()) => (None)
            case (RuleTyperMatchSuccess(mutabilityRuleT)) => (Some(mutabilityRuleT))
          }
        maybeMutabilityRule match {
          case None => (RuleTyperEvaluateUnknown())
          case Some(mutabilityRuleT) => (RuleTyperEvaluateSuccess(List(mutabilityRuleT)))
        }
      }
      case _ => vfail("Kind rule must have one component")
    }
  }
  
  private def makeMatcher() = {
    new RuleTyperMatcher[Env, State](
      evaluateTemplex,
      new RuleTyperMatcherDelegate[Env, State] {
        override def lookupType(state: State, env: Env, range: RangeS, name: CodeTypeNameS): ITemplataType = {
          delegate.lookupType(state, env, range, name)
        }

        override def lookupType(state: State, env: Env, range: RangeS, name: INameS): ITemplataType = {
          delegate.lookupType(state, env, range, name)
        }
      })
  }
}
