package net.verdagon.vale.astronomer.ruletyper

import net.verdagon.vale.scout.{IEnvironment => _, FunctionEnvironment => _, Environment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, OverrideSP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale._
import net.verdagon.vale.astronomer._

import scala.collection.immutable.List

trait RuleTyperMatcherDelegate[Env, State] {
  def lookupType(state: State, env: Env, name: CodeTypeNameS): ITemplataType
  def lookupType(state: State, env: Env, name: INameS): ITemplataType
}

class RuleTyperMatcher[Env, State](
    evaluate: (State, Env, ConclusionsBox, ITemplexS) => (IRuleTyperEvaluateResult[ITemplexA]),
    delegate: RuleTyperMatcherDelegate[Env, State]) {
  private def addConclusion(
    conclusions: ConclusionsBox,
    rune: IRuneA,
    tyype: ITemplataType):
  IRuleTyperMatchResult[Unit] = {
    conclusions.typeByRune.get(rune) match {
      case None => {
        conclusions.addConclusion(rune, tyype)
        RuleTyperMatchSuccess(())
      }
      case Some(existing) => {
        if (existing == tyype) {
          RuleTyperMatchSuccess(())
        } else {
          RuleTyperMatchConflict(conclusions.conclusions, "Disagreement about rune " + rune + "! " + existing + " and " + tyype, List())
        }
      }
    }
  }

  def matchAgainstDestructure(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    parts: List[AtomSP]):
  (IRuleTyperMatchResult[Unit]) = {
    parts.foreach(part => {
      matchAgainstAtomSP(state, env, conclusions, part) match {
        case (imc @ RuleTyperMatchConflict(_, _, _)) => return (imc)
        case (RuleTyperMatchSuccess(())) =>
      }
    })
    (RuleTyperMatchSuccess(()))
  }

  def matchAgainstAtomSP(
      state: State,
      env: Env,
      conclusions: ConclusionsBox,
      rule: AtomSP):
  (IRuleTyperMatchResult[Unit]) = {
    val coordRuneA = Astronomer.translateRune(rule.coordRune)

    addConclusion(conclusions, coordRuneA, CoordTemplataType) match {
      case (imc @ RuleTyperMatchConflict(_, _, _)) => return (imc)
      case (RuleTyperMatchSuccess(())) =>
    }

    rule.destructure match {
      case None => ()
      case Some(parts) => {
        matchAgainstDestructure(state, env, conclusions, parts) match {
          case (imc @ RuleTyperMatchConflict(_, _, _)) => return (imc)
          case (RuleTyperMatchSuccess(())) => ()
        }
      }
    }

    rule.virtuality match {
      case None => conclusions
      case Some(AbstractSP) =>
      case Some(OverrideSP(kindRuneS)) => {
        val kindRuneA = Astronomer.translateRune(kindRuneS)
        addConclusion(conclusions, kindRuneA, KindTemplataType) match {
          case (imc @ RuleTyperMatchConflict(_, _, _)) => return (imc)
          case (RuleTyperMatchSuccess(())) =>
        }
      }
    }

    (RuleTyperMatchSuccess(()))
  }

//  def matchCitizenAgainstCallST(
//    env: Env,
//    conclusions: ConclusionsBox,
//    expectedTemplate: ITemplexS,
//    expectedArgs: List[ITemplexS],
//    actualTemplateName: String,
//    actualArgs: List[ITemplata]):
//  IRuleTyperMatchResult = {
//    val actualTemplate =
//      delegate.lookupTemplata(env, actualTemplateName) match {
//        case None => return (RuleTyperMatchConflict(conclusions.conclusions, s"Couldn't find template '${actualTemplateName}'", List()))
//        case Some(x) => x
//      }
//    // Check to see that the actual template matches the expected template
//    val conclusions =
//      matchTypeAgainstTemplexSR(env, conclusions, actualTemplate, expectedTemplate) match {
//        case (imc @ RuleTyperMatchConflict(_, _, _)) => return imc
//        case (RuleTyperMatchContinue(conclusions)) => conclusions
//      }
//    // Check to see that the actual template args match the expected template args
//    val conclusions =
//      expectedArgs.zip(actualArgs).foldLeft(conclusions)({
//        case (conclusions, (expectedArg, actualArg)) => {
//          matchTypeAgainstTemplexSR(env, conclusions, actualArg, expectedArg) match {
//            case (imc @ RuleTyperMatchConflict(_, _, _)) => return imc
//            case (RuleTyperMatchContinue(conclusions)) => conclusions
//          }
//        }
//      })
//    // If the function is the same, and the args are the same... it's the same.
//    (RuleTyperMatchContinue(conclusions))
//  }

  def matchTypeAgainstTemplexesS(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    expectedTypes: List[ITemplataType],
    rules: List[ITemplexS]):
  (IRuleTyperMatchResult[List[ITemplexA]]) = {
    vassert(expectedTypes.size == rules.size)
    val resultTemplexesT =
      expectedTypes.zip(rules).zipWithIndex.flatMap({
        case ((expectedType, rule), index) => {
          matchTypeAgainstTemplexS(state, env, conclusions, expectedType, rule) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Failed evaluating templex " + index, List(rtmc)))
            case (RuleTyperMatchUnknown()) => Nil
            case (RuleTyperMatchSuccess(templexT)) => List(templexT)
          }
        }
      })
    if (resultTemplexesT.size == rules.size) {
      (RuleTyperMatchSuccess(resultTemplexesT))
    } else {
      (RuleTyperMatchUnknown())
    }
  }

  def matchTypeAgainstTemplexS(
      state: State,
      env: Env,
      conclusions: ConclusionsBox,
      expectedType: ITemplataType,
      rule: ITemplexS):
  IRuleTyperMatchResult[ITemplexA] = {
    (rule, expectedType) match {
      case (IntST(value), IntegerTemplataType) => (RuleTyperMatchSuccess(IntAT(value)))
      case (BoolST(value), BooleanTemplataType) => (RuleTyperMatchSuccess(BoolAT(value)))
      case (StringST(value), StringTemplataType) => (RuleTyperMatchSuccess(StringAT(value)))
      case (MutabilityST(value), MutabilityTemplataType) => (RuleTyperMatchSuccess(MutabilityAT(value)))
      case (PermissionST(value), PermissionTemplataType) => (RuleTyperMatchSuccess(PermissionAT(value)))
      case (LocationST(value), LocationTemplataType) => (RuleTyperMatchSuccess(LocationAT(value)))
      case (OwnershipST(value), OwnershipTemplataType) => (RuleTyperMatchSuccess(OwnershipAT(value)))
      case (VariabilityST(value), VariabilityTemplataType) => (RuleTyperMatchSuccess(VariabilityAT(value)))
      case (AbsoluteNameST(nameS), _) => {
        val tyype = delegate.lookupType(state, env, nameS)
        val nameA = Astronomer.translateName(nameS)
        matchNameTypeAgainstTemplataType(conclusions, tyype, expectedType) match {
          case RuleTyperMatchSuccess(()) => RuleTyperMatchSuccess(AbsoluteNameAT(nameA, expectedType))
          case rtmc @ RuleTyperMatchConflict(_, _, _) => {
            return (RuleTyperMatchConflict(conclusions.conclusions, nameA + "doesn't match needed " + expectedType, List(rtmc)))
          }
        }
      }
      case (NameST(nameS), _) => {
        val tyype = delegate.lookupType(state, env, nameS)
        val nameA = Astronomer.translateImpreciseName(nameS)
        matchNameTypeAgainstTemplataType(conclusions, tyype, expectedType) match {
          case RuleTyperMatchSuccess(()) => RuleTyperMatchSuccess(NameAT(nameA, expectedType))
          case rtmc @ RuleTyperMatchConflict(_, _, _) => {
            return (RuleTyperMatchConflict(conclusions.conclusions, nameA + "doesn't match needed " + expectedType, List(rtmc)))
          }
        }
      }
      case (RuneST(runeS), _) => {
        val runeA = Astronomer.translateRune(runeS)
        addConclusion(conclusions, runeA, expectedType) match {
          case (imc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Conflict in rune!", List(imc)))
          case (RuleTyperMatchSuccess(())) => {
            (RuleTyperMatchSuccess(RuneAT(runeA, expectedType)))
          }
        }
      }
//      case (CallST(_, _), CoordTemplata(Coord(_, _))) => {
//        // is this where we do coercion to get to something like the below case?
//        vfail("impl?")
//      }
      case (CallST(template, templateArgs), KindTemplataType | CoordTemplataType) => {
        val maybeTemplateTemplexT =
          evaluate(state, env, conclusions, template) match {
            case (rtec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Couldn't evaluate callee template!", List(rtec)))
            case (RuleTyperEvaluateUnknown()) => (None)
            case (RuleTyperEvaluateSuccess(templateT)) => (Some(templateT))
          }

        maybeTemplateTemplexT match {
          case None => {
            // We couldn't figure out the template, so we don't even know what the args are supposed to be.
            // But try evaluating them anyway, maybe that'll provide some nice clues to the types of some
            // runes.
            templateArgs.zipWithIndex.foreach({
              case ((templateArg, index)) => {
                evaluate(state, env, conclusions, templateArg) match {
                  case (rtec @ RuleTyperEvaluateConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Couldn't evaluate template call arg " + index, List(rtec)))
                  case (RuleTyperEvaluateUnknown()) => (conclusions)
                  case (RuleTyperEvaluateSuccess(templateArgT)) => {
                    // We can't do anything with the templateArgT anyway because we don't have the
                    // template to bundle it with into a call, throw it away.
                    val _ = templateArgT

                  }
                }
              }
            })
            (RuleTyperMatchUnknown())
          }
          case Some(templateTemplexT) => {
            val templexTemplateType =
              templateTemplexT.resultType match {
                case ttt @ TemplateTemplataType(_, _) => ttt
                case _ => return (RuleTyperMatchConflict(conclusions.conclusions, "Expected template call callee's to be a template but was " + templateTemplexT.resultType, List()))
              }

            (templexTemplateType.returnType, expectedType) match {
              case (a, b) if a == b =>
              // We can coerce kinds to coords, that's fine
              case (KindTemplataType, CoordTemplataType) =>
              case _ => return (RuleTyperMatchConflict(conclusions.conclusions, "Expected template call callee's return type to be " + expectedType + " but was " + templexTemplateType.returnType, List()))
            }
            matchTypeAgainstTemplexesS(state, env, conclusions, templexTemplateType.paramTypes, templateArgs) match {
              case (rtec @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Couldn't evaluate template call args!", List(rtec)))
              case (RuleTyperMatchUnknown()) => {
                (RuleTyperMatchUnknown())
              }
              case (RuleTyperMatchSuccess(argTemplexesT)) => {
                (RuleTyperMatchSuccess(CallAT(templateTemplexT, argTemplexesT, expectedType)))
              }
            }
          }
        }
      }
      case (PackST(elements), PackTemplataType(CoordTemplataType)) => {
        matchTypeAgainstTemplexesS(state, env, conclusions, elements.indices.toList.map(_ => CoordTemplataType), elements) match {
          case (rtec @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Couldn't evaluate template call args!", List(rtec)))
          case (RuleTyperMatchUnknown()) => {
            (RuleTyperMatchUnknown())
          }
          case (RuleTyperMatchSuccess(coordTemplexesT)) => {
            (RuleTyperMatchSuccess(CoordListAT(coordTemplexesT)))
          }
        }
      }
//      case (CallST(expectedTemplate, expectedArgs), KindTemplata(InterfaceRef2(actualTemplateName, actualArgs))) => {
//        matchCitizenAgainstCallST(env, conclusions, expectedTemplate, expectedArgs, actualTemplateName, actualArgs)
//      }
//      case (PrototypeST(_, _, _), _) => {
//        vfail("what even is this")
//      }
//      case (PackST(expectedMembers), KindTemplata(PackT2(actualMembers, _))) => {
//        val conclusions =
//          expectedMembers.zip(actualMembers).foldLeft(conclusions)({
//            case (conclusions, (expectedMember, actualMember)) => {
//              matchTypeAgainstTemplexSR(env, conclusions, CoordTemplata(actualMember), expectedMember) match {
//                case (imc @ RuleTyperMatchConflict(_, _, _)) => return imc
//                case (RuleTyperMatchContinue(conclusions)) => conclusions
//              }
//            }
//          })
//        (RuleTyperMatchContinue(conclusions))
//      }
//      case (RepeaterSequenceST(_, _), _) => {
//        vfail("impl")
//      }
//      case (OwnershipST(ownershipP), OwnershipTemplata(ownershipT)) => {
//        if (ownershipT != Conversions.evaluateOwnership(ownershipP)) {
//          (RuleTyperMatchConflict(conclusions.conclusions, s"Ownerships don't match: ${ownershipP} and ${ownershipT}", List()))
//        } else {
//          (RuleTyperMatchContinue(conclusions))
//        }
//      }
      case (OwnershippedST(ownership, coordTemplex), CoordTemplataType) => {
        matchTypeAgainstTemplexS(state, env, conclusions, CoordTemplataType, coordTemplex) match {
          case (rtec @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Couldn't evaluate ownershipped's kind!", List(rtec)))
          case (RuleTyperMatchUnknown()) => (RuleTyperMatchUnknown())
          case (RuleTyperMatchSuccess(innerCoordRuleT)) => {
            (RuleTyperMatchSuccess(OwnershippedAT(ownership, innerCoordRuleT)))
          }
        }
      }
      case (RepeaterSequenceST(mutabilityTemplexS, sizeTemplexS, elementTemplexS), KindTemplataType | CoordTemplataType) => {
        val maybeMutabilityTemplexT =
          matchTypeAgainstTemplexS(state, env, conclusions, MutabilityTemplataType, mutabilityTemplexS) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Conflict in mutability part!", List(rtmc)))
            case (RuleTyperMatchUnknown()) => (None)
            case (RuleTyperMatchSuccess(sizeTemplexT)) => (Some(sizeTemplexT))
          }
        val maybeSizeTemplexT =
          matchTypeAgainstTemplexS(state, env, conclusions, IntegerTemplataType, sizeTemplexS) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Conflict in size part!", List(rtmc)))
            case (RuleTyperMatchUnknown()) => (None)
            case (RuleTyperMatchSuccess(sizeTemplexT)) => (Some(sizeTemplexT))
          }
        val maybeElementTemplexT =
          matchTypeAgainstTemplexS(state, env, conclusions, CoordTemplataType, elementTemplexS) match {
            case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Conflict in element part!", List(rtmc)))
            case (RuleTyperMatchUnknown()) => (None)
            case (RuleTyperMatchSuccess(elementTemplexT)) => (Some(elementTemplexT))
          }
        (maybeMutabilityTemplexT, maybeSizeTemplexT, maybeElementTemplexT) match {
          case (Some(mutabilityTemplexT), Some(sizeTemplexT), (Some(elementTemplexT))) => {
            (RuleTyperMatchSuccess(RepeaterSequenceAT(mutabilityTemplexT, sizeTemplexT, elementTemplexT, expectedType)))
          }
          case (_, _, _) => (RuleTyperMatchUnknown())
        }
      }
      case (BorrowST(inner), CoordTemplataType) => {
        matchTypeAgainstTemplexS(state, env, conclusions, CoordTemplataType, inner)
      }
      case _ => vfail("Can't match " + rule + " against type " + expectedType)
    }
  }

  def matchNameTypeAgainstTemplataType(
    conclusions: ConclusionsBox,
    tyype: ITemplataType,
    expectedType: ITemplataType):
  IRuleTyperMatchResult[Unit] = {
    // Add something to this case to note that we've added it, and all its combinations,
    // to the main match below.
    tyype match {
      case KindTemplataType =>
      case MutabilityTemplataType =>
      case CoordTemplataType =>
      case TemplateTemplataType(_, _) => // We check for strict equality, nothing fancy here.
      case _ => vfail()
    }
    // Add something to this case to note that we've added it, and all its combinations,
    // to the main match below.
    expectedType match {
      case KindTemplataType =>
      case CoordTemplataType =>
      case MutabilityTemplataType =>
      case IntegerTemplataType =>
      case TemplateTemplataType(_, _) => // We check for strict equality, nothing fancy here.
      case _ => vfail(expectedType.toString)
    }
    // When something's missing, consider all of the combinations it has with everything
    // else, then once youve considered them, add them to the above matches.
    (tyype, expectedType) match {
      case (IntegerTemplataType, IntegerTemplataType) => {
        (RuleTyperMatchSuccess(()))
      }
      case (nonIntType, IntegerTemplataType) => {
        (RuleTyperMatchConflict(conclusions.conclusions, "Expected an int, but was " + nonIntType, List()))
      }
      case (MutabilityTemplataType, MutabilityTemplataType) => {
        (RuleTyperMatchSuccess(()))
      }
      case (CoordTemplataType, CoordTemplataType) => {
        (RuleTyperMatchSuccess(()))
      }
      case (KindTemplataType, KindTemplataType | CoordTemplataType) => {
        (RuleTyperMatchSuccess(()))
      }
      case (TemplateTemplataType(paramTypes, returnType), TemplateTemplataType(expectedParamTypes, expectedReturnType)) => {
        if (paramTypes.size != expectedParamTypes.size) {
          return (RuleTyperMatchConflict(conclusions.conclusions, "Received " + paramTypes.size + " template params but expected " + expectedParamTypes.size, List()))
        }
        if (paramTypes != expectedParamTypes) {
          return (RuleTyperMatchConflict(conclusions.conclusions, "Received " + paramTypes + " template params but expected " + expectedParamTypes, List()))
        }
        if (returnType != expectedReturnType) {
          return (RuleTyperMatchConflict(conclusions.conclusions, "Received " + returnType + " return type but expected " + expectedReturnType, List()))
        }
        (RuleTyperMatchSuccess(()))
      }
      //          // Is this right? Can't we look it up as a coord, like we did with KindTemplata/CoordTemplataType?
      //          case (InterfaceTemplata(_, interfaceS), KindTemplataType | CoordTemplataType) => {
      //            if (Inferer.interfaceIsTemplate(interfaceS)) {
      //              RuleTyperMatchConflict(conclusions.conclusions, "Tried making a '" + name + "' but it's a template and no arguments were supplied!", List())
      //            } else {
      //              RuleTyperMatchSuccess(NameAT(name, expectedType))
      //            }
      //          }
      //          // Is this right? Can't we look it up as a coord, like we did with KindTemplata/CoordTemplataType?
      //          case (StructTemplata(_, structS), KindTemplataType | CoordTemplataType) => {
      //            if (Inferer.structIsTemplate(structS)) {
      //              RuleTyperMatchConflict(conclusions.conclusions, "Tried making a '" + name + "' but it's a template and no arguments were supplied!", List())
      //            } else {
      //              RuleTyperMatchSuccess(NameAT(name, expectedType))
      //            }
      //          }
      //          case (it @ InterfaceTemplata(_, _), TemplateTemplataType(paramTypes, KindTemplataType)) => {
      //            val TemplateTemplataType(paramTypes, resultType) = delegate.getInterfaceTemplataType(it)
      //            vimpl()
      //          }
      //          case (st @ StructTemplata(_, _), TemplateTemplataType(paramTypes, KindTemplataType)) => {
      //            val TemplateTemplataType(paramTypes, resultType) = delegate.getStructTemplataType(st)
      //            vimpl()
      //          }
      case _ => (RuleTyperMatchConflict(conclusions.conclusions, "Given name doesn't match needed " + expectedType, List()))
    }
  }

  def matchTypeAgainstRulexSR(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    expectedType: ITemplataType,
    irule: IRulexSR):
  (IRuleTyperMatchResult[IRulexAR]) = {
    irule match {
//      case rule @ PackSR(_) => {
//        matchTypeAgainstPackSR(state, env, conclusions, expectedType, rule)
//      }
      case rule @ EqualsSR(_, _) => {
        matchTypeAgainstEqualsSR(state, env, conclusions, expectedType, rule)
      }
      case rule @ OrSR(_) => {
        matchTypeAgainstOrSR(state, env, conclusions, expectedType, rule)
      }
      case rule @ ComponentsSR(_, _) => {
        matchTypeAgainstComponentsSR(state, env, conclusions, expectedType, rule)
      }
      case rule @ TypedSR(_, _) => {
        matchTypeAgainstTypedSR(state, env, conclusions, expectedType, rule)
      }
      case TemplexSR(itemplexST) => {
        matchTypeAgainstTemplexS(state, env, conclusions, expectedType, itemplexST) match {
          case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "", List(rtmc)))
          case (RuleTyperMatchUnknown()) => (RuleTyperMatchUnknown())
          case (RuleTyperMatchSuccess(templexT)) => (RuleTyperMatchSuccess(TemplexAR(templexT)))
        }
      }
      case rule @ CallSR(_, _) => {
        val result = matchTypeAgainstCallSR(state, env, conclusions, expectedType, rule)
        (result)
      }
    }
  }

  def matchTypeAgainstTypedSR(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    expectedType: ITemplataType,
    rule: TypedSR):
  (IRuleTyperMatchResult[TemplexAR]) = {
    // If we fail here, that means we didn't take this ITemplataType into account
    // in the main match below.
    expectedType match {
      case PackTemplataType(CoordTemplataType) =>
      case CoordTemplataType =>
      case KindTemplataType =>
      case MutabilityTemplataType =>
      case PrototypeTemplataType =>
    }
    // If we fail here, that means we didn't take this ITypeSR into account
    // in the main match below.
    rule.tyype match {
      case CoordTypeSR =>
      case KindTypeSR =>
      case MutabilityTypeSR =>
      case PrototypeTypeSR =>
    }
    (expectedType, rule.tyype) match {
      case (CoordTemplataType, CoordTypeSR) =>
      case (KindTemplataType, KindTypeSR) =>
      case (MutabilityTemplataType, MutabilityTypeSR) =>
      case (PrototypeTemplataType, PrototypeTypeSR) =>
      // When you add a case here, make sure you consider all combinations, and
      // add it to the above matches to note that you did.
      case _ => return (RuleTyperMatchConflict(conclusions.conclusions, "Type from above (" + expectedType + ") didn't match type from rule (" + rule.tyype + ")", List()))
    }

    val runeA = Astronomer.translateRune(rule.rune)

    addConclusion(conclusions, runeA, expectedType) match {
      case (imc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "", List(imc)))
      case (RuleTyperMatchSuccess(())) =>
    }

    RuleTyperMatchSuccess(TemplexAR(RuneAT(runeA, expectedType)))
  }

  def matchTypeAgainstCallSR(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    expectedType: ITemplataType,
    rule: CallSR):
  (IRuleTyperMatchResult[CallAR]) = {
    val CallSR(name, argRules) = rule
//
//    // We don't do anything with the argRules; we don't evaluate or match them here, see MDMIA.
//    val _ = argRules
//
//    // We could check that the types are good, but we already do that in the evaluate layer.
//    // So... nothing to do here!
//    (RuleTyperMatchContinue(conclusions))

    name match {
      case "passThroughIfConcrete" => {
        if (expectedType != KindTemplataType) {
          return (RuleTyperMatchConflict(conclusions.conclusions, "passThroughIfConcrete returns a kind, but tried to match " + expectedType, List()))
        }
        val List(argRule) = argRules
        matchTypeAgainstRulexSR(state, env, conclusions, KindTemplataType, argRule) match {
          case (imc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Couldn't match against " + name + " argument", List(imc)))
          case (RuleTyperMatchSuccess(ruleT)) => (RuleTyperMatchSuccess(CallAR(name, List(ruleT), KindTemplataType)))
        }
      }
      case "passThroughIfInterface" => {
        if (expectedType != KindTemplataType) {
          return (RuleTyperMatchConflict(conclusions.conclusions, "passThroughIfInterface returns a kind, but tried to match " + expectedType, List()))
        }
        val List(argRule) = argRules
        matchTypeAgainstRulexSR(state, env, conclusions, KindTemplataType, argRule) match {
          case (imc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Couldn't match against " + name + " argument", List(imc)))
          case (RuleTyperMatchSuccess(ruleT)) => (RuleTyperMatchSuccess(CallAR(name, List(ruleT), KindTemplataType)))
        }
      }
      case "passThroughIfStruct" => {
        if (expectedType != KindTemplataType) {
          return (RuleTyperMatchConflict(conclusions.conclusions, "passThroughIfStruct returns a kind, but tried to match " + expectedType, List()))
        }
        val List(argRule) = argRules
        matchTypeAgainstRulexSR(state, env, conclusions, KindTemplataType, argRule) match {
          case (imc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Couldn't match against " + name + " argument", List(imc)))
          case (RuleTyperMatchSuccess(ruleT)) => (RuleTyperMatchSuccess(CallAR(name, List(ruleT), KindTemplataType)))
        }
      }
      case _ => vfail()
    }
  }

  def matchTypeAgainstComponentsSR(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    expectedType: ITemplataType,
    rule: ComponentsSR):
  (IRuleTyperMatchResult[EqualsAR]) = {
    val ComponentsSR(containerTypeAndRuneRuleS, components) = rule

    val containerTypeAndRuneRuleT =
      matchTypeAgainstTypedSR(state, env, conclusions, expectedType, containerTypeAndRuneRuleS) match {
        case (imc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Couldn't match against type/rune of components!", List(imc)))
        case (RuleTyperMatchSuccess(typedAR)) => (typedAR)
      }

    rule.container.tyype match {
      case KindTypeSR => {
        components match {
          case List(mutabilityRuleS) => {
            val maybeMutabilityRuleT =
              matchTypeAgainstRulexSR(state, env, conclusions, MutabilityTemplataType, mutabilityRuleS) match {
                case (rtmc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Couldn't match against mutability rule of kind components rule", List(rtmc)))
                case (RuleTyperMatchUnknown()) => (None)
                case (RuleTyperMatchSuccess(mutabilityRuleT)) => (Some(mutabilityRuleT))
              }
            maybeMutabilityRuleT match {
              case None => (RuleTyperMatchUnknown())
              case Some(mutabilityRuleT) => {
                val componentsRuleT =
                  EqualsAR(
                    containerTypeAndRuneRuleT,
                    ComponentsAR(containerTypeAndRuneRuleT.resultType, List(mutabilityRuleT)))
                (RuleTyperMatchSuccess(componentsRuleT))
              }
            }
          }
          case _ => vfail("Wrong number of components for kind")
        }
      }
      case CoordTypeSR => {
        components match {
          case List(ownershipRule, kindRule) => {
            val maybeOwnershipRuleT =
              matchTypeAgainstRulexSR(state, env, conclusions, OwnershipTemplataType, ownershipRule) match {
                case (imc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Failed matching ownership component of coord rule", List(imc)))
                case (RuleTyperMatchSuccess(ownershipRuleT)) => (Some(ownershipRuleT))
              }
            val maybeKindRuleT =
              matchTypeAgainstRulexSR(state, env, conclusions, KindTemplataType, kindRule) match {
                case (imc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Failed matching kind component of coord rule", List(imc)))
                case (RuleTyperMatchSuccess(kindRuleT)) => (Some(kindRuleT))
              }

            (maybeOwnershipRuleT, maybeKindRuleT) match {
              case (Some(ownershipRuleT), Some(kindRuleT)) => {
                val componentsRuleT =
                  EqualsAR(
                    containerTypeAndRuneRuleT,
                    ComponentsAR(containerTypeAndRuneRuleT.resultType, List(ownershipRuleT, kindRuleT)))
                (RuleTyperMatchSuccess(componentsRuleT))
              }
              case (_, _) => (RuleTyperMatchUnknown())
            }
          }
          case _ => vfail("Wrong number of components for kind")
        }
      }
    }
  }
//
//  def matchTypeAgainstPackSR(
//    state: State,
//    env: Env,
//    conclusions: ConclusionsBox,
//    expectedType: ITemplataType,
//    rule: PackSR):
//  (IRuleTyperMatchResult[CoordListAR]) = {
//    (expectedType, rule) match {
//      case (PackTemplataType(CoordTemplataType), PackSR(elements)) => {
//        val rulesA =
//          elements.map(element => {
//            matchTypeAgainstRulexSR(state, env, conclusions, CoordTemplataType, element) match {
//              case (imc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Failed matching element of pack", List(imc)))
//              case RuleTyperMatchSuccess(elementRuleT) => elementRuleT
//            }
//          })
//        RuleTyperMatchSuccess(CoordListAR(rulesA))
//      }
//    }
//  }

  def matchTypeAgainstEqualsSR(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    expectedType: ITemplataType,
    rule: EqualsSR):
  (IRuleTyperMatchResult[EqualsAR]) = {
    val EqualsSR(left, right) = rule
    matchTypeAgainstRulexSR(state, env, conclusions, expectedType, left) match {
      case (imc @ RuleTyperMatchConflict(_, _, _)) => (RuleTyperMatchConflict(conclusions.conclusions, "Conflict while evaluating left side of equals!", List(imc)))
      case (RuleTyperMatchSuccess(leftT)) => {
        matchTypeAgainstRulexSR(state, env, conclusions, expectedType, right) match {
          case (imc @ RuleTyperMatchConflict(_, _, _)) => (RuleTyperMatchConflict(conclusions.conclusions, "Conflict while evaluating right side of equals!", List(imc)))
          case (RuleTyperMatchSuccess(rightT)) => {
            (RuleTyperMatchSuccess(EqualsAR(leftT, rightT)))
          }
        }
      }
    }
  }

  def matchTypeAgainstOrSR(
    state: State,
    env: Env,
    conclusions: ConclusionsBox,
    expectedType: ITemplataType,
    rule: OrSR):
  (IRuleTyperMatchResult[OrAR]) = {
    val OrSR(possibilities) = rule

    val possibilitiesT =
      possibilities.zipWithIndex.map({
        case ((possibility, possibilityIndex)) => {
          matchTypeAgainstRulexSR(state, env, conclusions, expectedType, possibility) match {
            case (imc @ RuleTyperMatchConflict(_, _, _)) => return (RuleTyperMatchConflict(conclusions.conclusions, "Conflict while evaluating alternative " + possibilityIndex, List(imc)))
            case (RuleTyperMatchSuccess(possibilityRuleT)) => possibilityRuleT
          }
        }
      })
    (RuleTyperMatchSuccess(OrAR(possibilitiesT)))
  }
}
