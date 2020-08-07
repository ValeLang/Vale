package net.verdagon.vale.templar

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.ITemplexS
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env.{IEnvironment, ILookupContext, TemplataLookupContext}
import net.verdagon.vale.templar.infer.{IInfererDelegate, _}
import net.verdagon.vale.templar.infer.infer.{IInferSolveResult, InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassertSome, vfail, vimpl}

import scala.collection.immutable.List

class InferTemplar(
    opts: TemplarOptions,
    delegate: IInfererDelegate[IEnvironment, TemputsBox]) {
  private def solve(
    env: IEnvironment,
    state: TemputsBox,
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
    directInputs: Map[IRuneA, ITemplata],
    paramAtoms: List[AtomAP],
    maybeParamInputs: Option[List[ParamFilter]],
    checkAllRunesPresent: Boolean,
  ): (IInferSolveResult) = {
    Inferer.solve[IEnvironment, TemputsBox](
      delegate,
      env,
      state,
      translateRules(rules),
      typeByRune.map({ case (key, value) => NameTranslator.translateRune(key) -> value}),
      localRunes.map(NameTranslator.translateRune),
      directInputs.map({ case (key, value) => NameTranslator.translateRune(key) -> value}),
      paramAtoms,
      maybeParamInputs,
      checkAllRunesPresent)
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
    solve(env0, temputs, rules, typeByRune, localRunes, Map(), List(), None, true) match {
      case (InferSolveSuccess(inferences)) => {
        (inferences.templatasByRune)
      }
      case (isf @ InferSolveFailure(_, _, _, _, _, _)) => {
        vfail("Conflict in determining ordinary rules' runes: " + isf)
      }
    }
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
    explicits: List[ITemplata],
  ): (IInferSolveResult) = {
    if (identifyingRunes.size != explicits.size) {
      vfail("Wrong number of template args!")
    }

    solve(
      env0,
      temputs,
      rules,
      typeByRune,
      localRunes,
      identifyingRunes.zip(explicits).toMap,
      patterns1,
      None,
      true)
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
    alreadySpecifiedTemplateArgs: List[ITemplata],
    patternInputCoords: List[ParamFilter]
  ): (IInferSolveResult) = {

    solve(
      env0,
      temputs,
      rules,
      typeByRune,
      localRunes,
      // Note: this two things we're zipping are of different length, that's fine.
      identifyingRunes.zip(alreadySpecifiedTemplateArgs).toMap,
      patterns1,
      Some(patternInputCoords),
      true)
  }

  def translateRules(rs: List[IRulexAR]): List[IRulexTR] = {
    rs.map(translateRule)
  }

  def translateRule(rulexA: IRulexAR): IRulexTR = {
    rulexA match {
      case EqualsAR(left, right) => EqualsTR(translateRule(left), translateRule(right))
      case TemplexAR(templex) => TemplexTR(translateTemplex(templex))
      case ComponentsAR(tyype, componentsA) => ComponentsTR(tyype, componentsA.map(translateRule))
      case OrAR(possibilities) => OrTR(possibilities.map(translateRule))
      case CallAR(name, args, resultType) => CallTR(name, args.map(translateRule), resultType)
//      case CoordListAR(rules) => CoordListTR(rules.map(translateRule))
      case _ => vimpl()
    }
  }

  def translateTemplex(templexA: ITemplexA): ITemplexT = {
    templexA match {
      case RuneAT(rune, resultType) => RuneTT(NameTranslator.translateRune(rune), resultType)
      case NameAT(name, resultType) => NameTT(name, resultType)
      case OwnershipAT(ownership) => OwnershipTT(ownership)
      case OwnershippedAT(ownership, inner) => OwnershippedTT(ownership, translateTemplex(inner))
      case AbsoluteNameAT(name, resultType) => AbsoluteNameTT(name, resultType)
      case CallAT(template, args, resultType) => CallTT(translateTemplex(template), args.map(translateTemplex), resultType)
      case MutabilityAT(m) => MutabilityTT(m)
      case RepeaterSequenceAT(mutability, size, element, resultType) => RepeaterSequenceTT(translateTemplex(mutability), translateTemplex(size), translateTemplex(element), resultType)
//      case PackAT(members, resultType) => PackTT(members.map(translateTemplex), resultType)
      case IntAT(value) => IntTT(value)
      case StringAT(value) => StringTT(value)
      case CoordListAT(elements) => CoordListTT(elements.map(translateTemplex))
      case _ => vimpl(templexA.toString)
    }
  }
}
