package net.verdagon.vale.scout.patterns

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, _}
import net.verdagon.vale.{vassert, vassertSome, vcurious, vfail, vimpl, vwat}

import scala.collection.immutable.List

case class RuleStateBox(var rate: IRuleState) {
  override def hashCode(): Int = vfail() // Shouldnt hash, is mutable

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
  override def hashCode(): Int = vcurious()
  def newImplicitRune(): (RuleState, IRuneS) = {
    (RuleState(containerName, nextImplicitRune + 1),
      ImplicitRuneS(containerName, nextImplicitRune))
  }
}
case class LetRuleState(
    envFullName: INameS,
    letCodeLocation: CodeLocationS,
    nextImplicitRune: Int) extends IRuleState {
  override def hashCode(): Int = vcurious()
  def newImplicitRune(): (LetRuleState, IRuneS) = {
    (
      LetRuleState(envFullName, letCodeLocation, nextImplicitRune + 1),
      LetImplicitRuneS(letCodeLocation, nextImplicitRune))
  }
}

object PatternScout {
  def getParameterCaptures(pattern: AtomSP): Vector[VariableDeclaration] = {
    val AtomSP(_, maybeCapture, _, _, maybeDestructure) = pattern
  Vector.empty ++
      maybeCapture.toVector.flatMap(getCaptureCaptures) ++
        maybeDestructure.toVector.flatten.flatMap(getParameterCaptures)
  }
  private def getCaptureCaptures(capture: CaptureS): Vector[VariableDeclaration] = {
    Vector(VariableDeclaration(capture.name))
  }

  // Returns:
  // - New rules
  // - Scouted patterns
  private[scout] def scoutPatterns(
      stackFrame: StackFrame,
      rulesS: RuleStateBox,
      params: Vector[PatternPP]):
  (Vector[IRulexSR], Vector[AtomSP]) = {
    params.foldLeft((Vector[IRulexSR](), Vector[AtomSP]()))({
      case ((previousNewRulesS, previousPatternsS), patternP) => {
        val (newRulesS, patternS) =
          PatternScout.translatePattern(stackFrame, rulesS, patternP)
        (previousNewRulesS ++ newRulesS, previousPatternsS :+ patternS)
      }
    })
  }

  sealed trait INameRequirement
  case object NameNotRequired extends INameRequirement
  case class NameRequired(nameSuggestion: String) extends INameRequirement { override def hashCode(): Int = vcurious() }

  // Returns:
  // - Rules, which are likely just TypedSR
  // - The translated patterns
  private[scout] def translatePattern(
    stackFrame: StackFrame,
    ruleState: RuleStateBox,
    patternPP: PatternPP):
  (Vector[IRulexSR], AtomSP) = {
    val PatternPP(range,_,maybeCaptureP, maybeTypeP, maybeDestructureP, maybeVirtualityP) = patternPP

    val (newRulesFromVirtuality, maybeVirtualityS) =
      maybeVirtualityP match {
        case None => (Vector.empty, None)
        case Some(AbstractP) => (Vector.empty, Some(AbstractSP))
        case Some(OverrideP(range, typeP)) => {
          typeP match {
            case InterpretedPT(range, _, _, _) => {
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
        case None => (Vector.empty, None)
        case Some(DestructureP(_, destructureP)) => {
          val (newRulesFromDestructures, patternsS) =
            destructureP.foldLeft((Vector[IRulexSR](), Vector[AtomSP]()))({
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
//          val codeLocation = Scout.evalPos(stackFrame.file, patternPP.range.begin)
          None
        }
        case Some(CaptureP(_,LocalNameP(NameP(_, name)))) => {
          if (name == "set" || name == "mut") {
            throw CompileErrorExceptionS(CantUseThatLocalName(Scout.evalRange(stackFrame.file, range), name))
          }
          Some(CaptureS(CodeVarNameS(name)))
        }
        case Some(CaptureP(_,ConstructingMemberNameP(NameP(_, name)))) => {
          Some(CaptureS(ConstructingMemberNameS(name)))
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
      runeType: ITypePR,
      // Determines whether the rune is on the left or the right in the Equals rule, which
      // can (unfortunately) affect the order in which the generics engine evaluates things.
      // This is a temporary solution, see DCRC, option A.
      runeOnLeft: Boolean = true):
  (Vector[IRulexSR], IRuneS) = {
    maybeTypeP match {
      case None => {
        val rune = rulesS.newImplicitRune()
        val newRule = TypedSR(range, rune, RuleScout.translateType(runeType))
        (Vector(newRule), rune)
      }
      case Some(NameOrRunePT(NameP(_, nameOrRune))) if env.allUserDeclaredRunes().contains(CodeRuneS(nameOrRune)) => {
        val rune = CodeRuneS(nameOrRune)
        val newRule = TypedSR(range, rune, RuleScout.translateType(runeType))
        (Vector(newRule), rune)
      }
      case Some(nonRuneTemplexP) => {
        val (newRulesFromInner, templexS, maybeRune) =
          translatePatternTemplex(env, rulesS, nonRuneTemplexP)
        maybeRune match {
          case Some(rune) => (newRulesFromInner, rune)
          case None => {
            val rune = rulesS.newImplicitRune()
            val newRule =
              if (runeOnLeft) {
                EqualsSR(templexS.range, TypedSR(range, rune, RuleScout.translateType(runeType)), TemplexSR(templexS))
              } else {
                EqualsSR(templexS.range, TemplexSR(templexS), TypedSR(range, rune, RuleScout.translateType(runeType)))
              }
            (newRulesFromInner ++ Vector(newRule), rune)
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
    runeType: ITypePR,
    // Determines whether the rune is on the left or the right in the Equals rule, which
    // can (unfortunately) affect the order in which the generics engine evaluates things.
    // This is a temporary solution, see DCRC, option A.
    runeOnLeft: Boolean = true):
  (Vector[IRulexSR], Option[IRuneS]) = {
    if (maybeTypeP.isEmpty) {
      (Vector.empty, None)
    } else {
      val (newRules, rune) =
        translateMaybeTypeIntoRune(
          env, rulesS, range, maybeTypeP, runeType, runeOnLeft)
      (newRules, Some(rune))
    }
  }

//  private def translatePatternTemplexes(rulesS: WorkingRulesAndRunes, templexesP: Vector[ITemplexPT]):
//  (Vector[IRulexSR], Vector[ITemplexS]) = {
//    templexesP match {
//      case Nil => (rulesS, Vector())
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
    templexesP: Vector[ITemplexPT]):
  (Vector[IRulexSR], Vector[ITemplexS]) = {
    val results = templexesP.map(translatePatternTemplex(env, rulesS, _))
    (results.map(_._1).flatten, results.map(_._2))
  }

  // Returns:
  // - Any new rules we need to add
  // - A templex that represents the result
  // - If any, the rune associated with this exact result.
  def translatePatternTemplex(
      env: IEnvironment,
      rulesS: RuleStateBox,
      templexP: ITemplexPT):
  (Vector[IRulexSR], ITemplexS, Option[IRuneS]) = {
    val evalRange = (range: Range) => Scout.evalRange(env.file, range)

    templexP match {
      case AnonymousRunePT(range) => {
        val rune = rulesS.newImplicitRune()
        (Vector.empty, RuneST(evalRange(range), rune), Some(rune))
      }
      case IntPT(range,value) => (Vector.empty, IntST(evalRange(range), value), None)
      case BoolPT(range,value) => (Vector.empty, BoolST(evalRange(range), value), None)
      case NameOrRunePT(NameP(range, nameOrRune)) => {
        if (env.allUserDeclaredRunes().contains(CodeRuneS(nameOrRune))) {
          (Vector.empty, RuneST(evalRange(range), CodeRuneS(nameOrRune)), Some(CodeRuneS(nameOrRune)))
        } else {
          (Vector.empty, NameST(Scout.evalRange(env.file, range), CodeTypeNameS(nameOrRune)), None)
        }
      }
      case MutabilityPT(range, mutability) => (Vector.empty, MutabilityST(evalRange(range), mutability), None)
      case VariabilityPT(range, variability) => (Vector.empty, VariabilityST(evalRange(range), variability), None)
      case InterpretedPT(range,ownership,permission, innerP) => {
        val (newRules, innerS, _) =
          translatePatternTemplex(env, rulesS, innerP)
        (newRules, InterpretedST(evalRange(range), ownership, permission, innerS), None)
      }
      case CallPT(range,maybeTemplateP, argsMaybeTemplexesP) => {
        val (newRulesFromTemplate, maybeTemplateS, _) = translatePatternTemplex(env, rulesS, maybeTemplateP)
        val (newRulesFromArgs, argsMaybeTemplexesS) = translatePatternTemplexes(env, rulesS, argsMaybeTemplexesP)
        (newRulesFromTemplate ++ newRulesFromArgs, CallST(evalRange(range), maybeTemplateS, argsMaybeTemplexesS), None)
      }
      case RepeaterSequencePT(range, mutabilityP, variabilityP, sizeP, elementP) => {
        val (newRulesFromMutability, mutabilityS, _) = translatePatternTemplex(env, rulesS, mutabilityP)
        val (newRulesFromVariability, variabilityS, _) = translatePatternTemplex(env, rulesS, variabilityP)
        val (newRulesFromSize, sizeS, _) = translatePatternTemplex(env, rulesS, sizeP)
        val (newRulesFromElement, elementS, _) = translatePatternTemplex(env, rulesS, elementP)
        (newRulesFromMutability ++ newRulesFromVariability ++ newRulesFromSize ++ newRulesFromElement, RepeaterSequenceST(evalRange(range), mutabilityS, variabilityS, sizeS, elementS), None)
      }
      case ManualSequencePT(range,maybeMembersP) => {
        val (newRules, maybeMembersS) = translatePatternTemplexes(env, rulesS, maybeMembersP)
        (newRules, ManualSequenceST(evalRange(range), maybeMembersS), None)
      }
//      case FunctionPT(mutableP, paramsP, retP) => {
//        val (mutableS, _) = translatePatternMaybeTemplex(declaredRunes, rulesS, mutableP, None)
//        val paramsS = translatePatternTemplexes(declaredRunes, rulesS, paramsP)
//        val (retS, _) = translatePatternTemplex(env, rulesS, retP)

//        vfail("impl!")
//        CallST(
//          NameST("IFunction"),
//          Vector(
//            mutableS.getOrElse(MutableP),
//            paramsS,
//            retS))

//        (rulesS, FunctionST(mutableS, PackST(paramsS), retS), None)
//      }
      case x => vwat(x.toString)
    }
  }
}
