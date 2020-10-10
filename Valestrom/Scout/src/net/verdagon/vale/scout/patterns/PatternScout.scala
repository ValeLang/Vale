package net.verdagon.vale.scout.patterns

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, _}
import net.verdagon.vale.{vassert, vassertSome, vfail, vwat}

import scala.collection.immutable.List

case class RuleStateBox(var rate: IRuleState) {
  def newImplicitRune(): IRuneS = {
    val (newRate, rune) = rate.newImplicitRune()
    rate = newRate
    rune
  }
}

sealed trait IRuleState {
  def newImplicitRune(): (IRuleState, IRuneS)
}

// Sometimes referred to as a "rate"
case class RuleState(
    containerName: INameS,
    nextImplicitRune: Int) extends IRuleState {
  def newImplicitRune(): (RuleState, IRuneS) = {
    (RuleState(containerName, nextImplicitRune + 1),
      ImplicitRuneS(containerName, nextImplicitRune))
  }
}
case class LetRuleState(
    envFullName: INameS,
    letCodeLocation: CodeLocationS,
    nextImplicitRune: Int) extends IRuleState {
  def newImplicitRune(): (LetRuleState, IRuneS) = {
    (
      LetRuleState(envFullName, letCodeLocation, nextImplicitRune + 1),
      LetImplicitRuneS(letCodeLocation, nextImplicitRune))
  }
}

object PatternScout {
  def getParameterCaptures(pattern: AtomSP): List[VariableDeclaration] = {
    val AtomSP(_, maybeCapture, _, _, maybeDestructure) = pattern
    List() ++
        getCaptureCaptures(maybeCapture) ++
        maybeDestructure.toList.flatten.flatMap(getParameterCaptures)
  }
  private def getCaptureCaptures(capture: CaptureS): List[VariableDeclaration] = {
    List(VariableDeclaration(capture.name, capture.variability))
  }

  // Returns:
  // - New rules
  // - Scouted patterns
  private[scout] def scoutPatterns(
      stackFrame: StackFrame,
      rulesS: RuleStateBox,
      params: List[PatternPP]):
  (List[IRulexSR], List[AtomSP]) = {
    params.foldLeft((List[IRulexSR](), List[AtomSP]()))({
      case ((previousNewRulesS, previousPatternsS), patternP) => {
        val (newRulesS, patternS) =
          PatternScout.translatePattern(stackFrame, rulesS, patternP)
        (previousNewRulesS ++ newRulesS, previousPatternsS :+ patternS)
      }
    })
  }

  sealed trait INameRequirement
  case object NameNotRequired extends INameRequirement
  case class NameRequired(nameSuggestion: String) extends INameRequirement

  // Returns:
  // - Rules, which are likely just TypedSR
  // - The translated patterns
  private[scout] def translatePattern(
    stackFrame: StackFrame,
    ruleState: RuleStateBox,
    patternPP: PatternPP):
  (List[IRulexSR], AtomSP) = {
    val PatternPP(range,_,maybeCaptureP, maybeTypeP, maybeDestructureP, maybeVirtualityP) = patternPP

    val (newRulesFromVirtuality, maybeVirtualityS) =
      maybeVirtualityP match {
        case None => (List(), None)
        case Some(AbstractP) => (List(), Some(AbstractSP))
        case Some(OverrideP(range, typeP)) => {
          typeP match {
            case OwnershippedPT(range, _, _) => {
              throw CompileErrorExceptionS(CantOverrideOwnershipped(Scout.evalRange(stackFrame.file, range)))
            }
            case _ =>
          }

          val (newRulesFromVirtuality, rune) =
            translateMaybeTypeIntoRune(
              stackFrame.parentEnv,
              ruleState,
              Scout.evalRange(stackFrame.file, range),
              Some(typeP),
              KindTypePR)
          (newRulesFromVirtuality, Some(OverrideSP(Scout.evalRange(stackFrame.file, range), rune)))
        }
      }

    val (newRulesFromType, coordRune) =
      translateMaybeTypeIntoRune(
        stackFrame.parentEnv, ruleState, Scout.evalRange(stackFrame.file, range), maybeTypeP, CoordTypePR)

    val (newRulesFromDestructures, maybePatternsS) =
      maybeDestructureP match {
        case None => (List(), None)
        case Some(DestructureP(_, destructureP)) => {
          val (newRulesFromDestructures, patternsS) =
            destructureP.foldLeft((List[IRulexSR](), List[AtomSP]()))({
              case ((previousNewRulesS, previousPatternsS), patternP) => {
                val (newRulesFromDestructure, patternS) =
                  translatePattern(stackFrame, ruleState, patternP)
                (previousNewRulesS ++ newRulesFromDestructure, previousPatternsS :+ patternS)
              }
            })
          (newRulesFromDestructures, Some(patternsS))
        }
      }

    val captureS =
      maybeCaptureP match {
        case None => {
          val codeLocation = Scout.evalPos(stackFrame.file, patternPP.range.begin)
          CaptureS(UnnamedLocalNameS(codeLocation), FinalP)
        }
        case Some(CaptureP(_,LocalNameP(StringP(_, name)), variability)) => {
          CaptureS(CodeVarNameS(name), variability)
        }
        case Some(CaptureP(_,ConstructingMemberNameP(StringP(_, name)), variability)) => {
          CaptureS(ConstructingMemberNameS(name), variability)
        }
      }

    val atomSP = AtomSP(Scout.evalRange(stackFrame.file, range), captureS, maybeVirtualityS, coordRune, maybePatternsS)
    (newRulesFromType ++ newRulesFromDestructures ++ newRulesFromVirtuality, atomSP)
  }

  def translateMaybeTypeIntoRune(
      env: IEnvironment,
      rulesS: RuleStateBox,
    range: RangeS,
      maybeTypeP: Option[ITemplexPT],
      runeType: ITypePR):
  (List[IRulexSR], IRuneS) = {
    maybeTypeP match {
      case None => {
        val rune = rulesS.newImplicitRune()
        val newRule = TypedSR(range, rune, RuleScout.translateType(runeType))
        (List(newRule), rune)
      }
      case Some(NameOrRunePT(StringP(_, nameOrRune))) if env.allUserDeclaredRunes().contains(CodeRuneS(nameOrRune)) => {
        val rune = CodeRuneS(nameOrRune)
        val newRule = TypedSR(range, rune, RuleScout.translateType(runeType))
        (List(newRule), rune)
      }
      case Some(nonRuneTemplexP) => {
        val (newRulesFromInner, templexS, maybeRune) =
          translatePatternTemplex(env, rulesS, nonRuneTemplexP)
        maybeRune match {
          case Some(rune) => (newRulesFromInner, rune)
          case None => {
            val rune = rulesS.newImplicitRune()
            val newRule = EqualsSR(templexS.range, TypedSR(range, rune, RuleScout.translateType(runeType)), TemplexSR(templexS))
            (newRulesFromInner ++ List(newRule), rune)
          }
        }
      }
    }
  }
  def translateMaybeTypeIntoMaybeRune(
    env: IEnvironment,
    rulesS: RuleStateBox,
    range: RangeS,
    maybeTypeP: Option[ITemplexPT],
    runeType: ITypePR):
  (List[IRulexSR], Option[IRuneS]) = {
    if (maybeTypeP.isEmpty) {
      (List(), None)
    } else {
      val (newRules, rune) =
        translateMaybeTypeIntoRune(
          env, rulesS, range, maybeTypeP, runeType)
      (newRules, Some(rune))
    }
  }

//  private def translatePatternTemplexes(rulesS: WorkingRulesAndRunes, templexesP: List[ITemplexPT]):
//  (List[IRulexSR], List[ITemplexS]) = {
//    templexesP match {
//      case Nil => (rulesS, Nil)
//      case headTemplexP :: tailTemplexesP => {
//        val (rulesS, headTemplexS) = translatePatternTemplex(rulesS, headTemplexP)
//        val (rulesS, tailTemplexesS) = translatePatternTemplexes(rulesS, tailTemplexesP)
//        (rulesS, headTemplexS :: tailTemplexesS)
//      }
//    }
//  }

  private def translatePatternTemplexes(
    env: IEnvironment,
    rulesS: RuleStateBox,
    templexesP: List[ITemplexPT]):
  (List[IRulexSR], List[ITemplexS]) = {
    templexesP match {
      case Nil => (Nil, Nil)
      case headTemplexP :: tailTemplexesP => {
        val (newRulesFromHead, headTemplexS, _) = translatePatternTemplex(env, rulesS, headTemplexP)
        val (newRulesFromTail, tailTemplexesS) = translatePatternTemplexes(env, rulesS, tailTemplexesP)
        (newRulesFromHead ++ newRulesFromTail, headTemplexS :: tailTemplexesS)
      }
    }
  }

  // Returns:
  // - Any new rules we need to add
  // - A templex that represents the result
  // - If any, the rune associated with this exact result.
  def translatePatternTemplex(
      env: IEnvironment,
      rulesS: RuleStateBox,
      templexP: ITemplexPT):
  (List[IRulexSR], ITemplexS, Option[IRuneS]) = {
    val evalRange = (range: Range) => Scout.evalRange(env.file, range)

    templexP match {
      case AnonymousRunePT(range) => {
        val rune = rulesS.newImplicitRune()
        (List(), RuneST(evalRange(range), rune), Some(rune))
      }
      case IntPT(range,value) => (List(), IntST(evalRange(range), value), None)
      case BoolPT(range,value) => (List(), BoolST(evalRange(range), value), None)
      case NameOrRunePT(StringP(range, nameOrRune)) => {
        if (env.allUserDeclaredRunes().contains(CodeRuneS(nameOrRune))) {
          (List(), RuneST(evalRange(range), CodeRuneS(nameOrRune)), Some(CodeRuneS(nameOrRune)))
        } else {
          (List(), NameST(Scout.evalRange(env.file, range), CodeTypeNameS(nameOrRune)), None)
        }
      }
      case MutabilityPT(range, mutability) => (List(), MutabilityST(evalRange(range), mutability), None)
      case OwnershippedPT(rangeP,ownership @ (BorrowP|WeakP), NameOrRunePT(StringP(_, ownedCoordRuneName))) if env.allUserDeclaredRunes().contains(CodeRuneS(ownedCoordRuneName)) => {
        val range = evalRange(rangeP)
        vassert(env.allUserDeclaredRunes().contains(CodeRuneS(ownedCoordRuneName)))
        val ownedCoordRune = CodeRuneS(ownedCoordRuneName)
        val kindRune = rulesS.newImplicitRune()
        val borrowedCoordRune = rulesS.newImplicitRune()
        val newRules =
          List(
            TypedSR(range, kindRune, KindTypeSR),
            // It's a user rune so it's already in the orderedRunes
            TypedSR(range, ownedCoordRune, CoordTypeSR),
            TypedSR(range, borrowedCoordRune, CoordTypeSR),
            ComponentsSR(
              range,
              TypedSR(range, ownedCoordRune, CoordTypeSR),
              List(
                TemplexSR(OwnershipST(range, OwnP)),
                TemplexSR(RuneST(range, kindRune)))),
            ComponentsSR(
              range,
              TypedSR(range, borrowedCoordRune, CoordTypeSR),
              List(
                TemplexSR(OwnershipST(range, ownership)),
                TemplexSR(RuneST(range, kindRune)))))
        (newRules, RuneST(range, borrowedCoordRune), Some(borrowedCoordRune))
      }
      case OwnershippedPT(range,ownership, innerP) => {
        val (newRules, innerS, _) =
          translatePatternTemplex(env, rulesS, innerP)
        (newRules, OwnershippedST(evalRange(range), ownership, innerS), None)
      }
      case CallPT(range,maybeTemplateP, argsMaybeTemplexesP) => {
        val (newRulesFromTemplate, maybeTemplateS, _) = translatePatternTemplex(env, rulesS, maybeTemplateP)
        val (newRulesFromArgs, argsMaybeTemplexesS) = translatePatternTemplexes(env, rulesS, argsMaybeTemplexesP)
        (newRulesFromTemplate ++ newRulesFromArgs, CallST(evalRange(range), maybeTemplateS, argsMaybeTemplexesS), None)
      }
      case RepeaterSequencePT(range,mutabilityP, sizeP, elementP) => {
        val (newRulesFromMutability, mutabilityS, _) = translatePatternTemplex(env, rulesS, mutabilityP)
        val (newRulesFromSize, sizeS, _) = translatePatternTemplex(env, rulesS, sizeP)
        val (newRulesFromElement, elementS, _) = translatePatternTemplex(env, rulesS, elementP)
        (newRulesFromMutability ++ newRulesFromSize ++ newRulesFromElement, RepeaterSequenceST(evalRange(range), mutabilityS, sizeS, elementS), None)
      }
      case ManualSequencePT(range,maybeMembersP) => {
        val (newRules, maybeMembersS) = translatePatternTemplexes(env, rulesS, maybeMembersP)
        (newRules, ManualSequenceST(evalRange(range), maybeMembersS), None)
      }
      case PermissionedPT(_, permission, innerP) => {
        // TODO: Add permissions!
        // This is just a pass-through until then.
        val (newRules, innerS, _) =
          translatePatternTemplex(env, rulesS, innerP)
        (newRules, innerS, None)
      }
//      case FunctionPT(mutableP, paramsP, retP) => {
//        val (mutableS, _) = translatePatternMaybeTemplex(declaredRunes, rulesS, mutableP, None)
//        val paramsS = translatePatternTemplexes(declaredRunes, rulesS, paramsP)
//        val (retS, _) = translatePatternTemplex(env, rulesS, retP)

//        vfail("impl!")
//        CallST(
//          NameST("IFunction"),
//          List(
//            mutableS.getOrElse(MutableP),
//            paramsS,
//            retS))

//        (rulesS, FunctionST(mutableS, PackST(paramsS), retS), None)
//      }
      case x => vwat(x.toString)
    }
  }
}
