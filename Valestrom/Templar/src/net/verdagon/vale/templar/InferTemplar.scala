package net.verdagon.vale.templar

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.{ITemplexS, RangeS}
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env.{IEnvironment, ILookupContext, TemplataLookupContext}
import net.verdagon.vale.templar.infer.{IInfererDelegate, _}
import net.verdagon.vale.templar.infer.infer.{IInferSolveResult, InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{IProfiler, vassertSome, vfail, vimpl}

import scala.collection.immutable.List

class InferTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
    delegate: IInfererDelegate[IEnvironment, TemputsBox]) {
  private def solve(
    env: IEnvironment,
    state: TemputsBox,
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
    invocationRange: RangeS,
    directInputs: Map[IRuneA, ITemplata],
    paramAtoms: List[AtomAP],
    maybeParamInputs: Option[List[ParamFilter]],
    checkAllRunesPresent: Boolean,
  ): (IInferSolveResult) = {
    profiler.childFrame("infer", () => {
      Inferer.solve[IEnvironment, TemputsBox](
        profiler,
        delegate,
        env,
        state,
        translateRules(rules),
        typeByRune.map({ case (key, value) => NameTranslator.translateRune(key) -> value }),
        localRunes.map(NameTranslator.translateRune),
        invocationRange,
        directInputs.map({ case (key, value) => NameTranslator.translateRune(key) -> value }),
        paramAtoms,
        maybeParamInputs,
        checkAllRunesPresent)
    })
  }

  // No incoming types needed (like manually specified template args, or argument coords from a call).
  // This is for when we want to figure out the types for an ordinary function like
  //   fn sum(a: Int, b: Int)Int { }
  // which, remember, actually *does* have rules:
  //   fn sum
  //   rules(#1 = Int, #2 = Int, #3 = Int)
  //   (a: #1, b: #2) #3 { ...}
  def inferOrdinaryRules(
    env0: IEnvironment,
    temputs: TemputsBox,
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
  ): (Map[IRune2, ITemplata]) = {
    profiler.childFrame("inferOrdinaryRules", () => {
      solve(env0, temputs, rules, typeByRune, localRunes, RangeS.internal(-13337), Map(), List(), None, true) match {
        case (InferSolveSuccess(inferences)) => {
          (inferences.templatasByRune)
        }
        case (isf@InferSolveFailure(_, _, _, _, _, _, _)) => {
          vfail("Conflict in determining ordinary rules' runes: " + isf)
        }
      }
    })
  }

  def inferFromExplicitTemplateArgs(
    env0: IEnvironment,
    temputs: TemputsBox,
    identifyingRunes: List[IRuneA],
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
    patterns1: List[AtomAP],
    maybeRetRune: Option[IRuneA],
    invocationRange: RangeS,
    explicits: List[ITemplata],
  ): (IInferSolveResult) = {
    profiler.childFrame("inferFromExplicitTemplateArgs", () => {
      if (identifyingRunes.size != explicits.size) {
        vfail("Wrong number of template args!")
      }

      solve(
        env0,
        temputs,
        rules,
        typeByRune,
        localRunes,
        invocationRange,
        identifyingRunes.zip(explicits).toMap,
        patterns1,
        None,
        true)
    })
  }

  def inferFromArgCoords(
    env0: IEnvironment,
    temputs: TemputsBox,
    identifyingRunes: List[IRuneA],
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
    patterns1: List[AtomAP],
    maybeRetRune: Option[IRuneA],
    invocationRange: RangeS,
    alreadySpecifiedTemplateArgs: List[ITemplata],
    patternInputCoords: List[ParamFilter]
  ): (IInferSolveResult) = {
    profiler.childFrame("inferFromArgCoords", () => {
      solve(
        env0,
        temputs,
        rules,
        typeByRune,
        localRunes,
        invocationRange,
        // Note: this two things we're zipping are of different length, that's fine.
        identifyingRunes.zip(alreadySpecifiedTemplateArgs).toMap,
        patterns1,
        Some(patternInputCoords),
        true)
    })
  }

  def translateRules(rs: List[IRulexAR]): List[IRulexTR] = {
    rs.map(translateRule)
  }

  def translateRule(rulexA: IRulexAR): IRulexTR = {
    rulexA match {
      case EqualsAR(range, left, right) => EqualsTR(range, translateRule(left), translateRule(right))
      case TemplexAR(templex) => TemplexTR(translateTemplex(templex))
      case ComponentsAR(range, tyype, componentsA) => ComponentsTR(range, tyype, componentsA.map(translateRule))
      case OrAR(range, possibilities) => OrTR(range, possibilities.map(translateRule))
      case CallAR(range, name, args, resultType) => CallTR(range, name, args.map(translateRule), resultType)
//      case CoordListAR(rules) => CoordListTR(rules.map(translateRule))
      case _ => vimpl()
    }
  }

  def translateTemplex(templexA: ITemplexA): ITemplexT = {
    templexA match {
      case RuneAT(range, rune, resultType) => RuneTT(range, NameTranslator.translateRune(rune), resultType)
      case NameAT(range, name, resultType) => NameTT(range, name, resultType)
      case OwnershipAT(range, ownership) => OwnershipTT(range, ownership)
      case OwnershippedAT(range, ownership, inner) => OwnershippedTT(range, ownership, translateTemplex(inner))
      case AbsoluteNameAT(range, name, resultType) => AbsoluteNameTT(range, name, resultType)
      case CallAT(range, template, args, resultType) => CallTT(range, translateTemplex(template), args.map(translateTemplex), resultType)
      case MutabilityAT(range, m) => MutabilityTT(range, m)
      case ManualSequenceAT(range, m, resultType) => ManualSequenceTT(range, m.map(translateTemplex), resultType)
      case RepeaterSequenceAT(range, mutability, size, element, resultType) => RepeaterSequenceTT(range, translateTemplex(mutability), translateTemplex(size), translateTemplex(element), resultType)
//      case PackAT(range, members, resultType) => PackTT(range, members.map(translateTemplex), resultType)
      case IntAT(range, value) => IntTT(range, value)
      case StringAT(range, value) => StringTT(range, value)
      case CoordListAT(range, elements) => CoordListTT(range, elements.map(translateTemplex))
      case _ => vimpl(templexA.toString)
    }
  }
}
