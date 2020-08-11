package net.verdagon.vale.astronomer

import net.verdagon.vale.scout.IRuneS
import net.verdagon.vale.vassert

package object ruletyper {
  case class ConclusionsBox(var conclusions: Conclusions) {
    def typeByRune = conclusions.typeByRune
    def addConclusion(rune: IRuneA, tyype: ITemplataType): Unit = {
      conclusions = conclusions.addConclusion(rune, tyype)
    }
  }

  case class Conclusions(
      typeByRune: Map[IRuneA, ITemplataType]) {
    def addConclusion(rune: IRuneA, tyype: ITemplataType): Conclusions = {
      vassert(!typeByRune.contains(rune))
      Conclusions(typeByRune + (rune -> tyype))
    }
  }

  trait IConflictCause
  case class MultipleCauses(causes: List[IConflictCause])

  sealed trait IRuleTyperSolveResult[T]
  case class RuleTyperSolveFailure[T](
    conclusions: ConclusionsBox,
    message: String,
    inner: List[IConflictCause]
  ) extends IRuleTyperSolveResult[T] with IConflictCause
  case class RuleTyperSolveSuccess[T](
    types: T
  ) extends IRuleTyperSolveResult[T]


  sealed trait IRuleTyperEvaluateResult[+T]
  case class RuleTyperEvaluateConflict[T](
    // This is in here because when we do an Or rule, we want to know why each
    // case failed; we want to have all the conflicts in a row, we want to have
    // the conclusions for each failure.
    conclusions: Conclusions,
    message: String,
    cause: Option[IConflictCause]
  ) extends IRuleTyperEvaluateResult[T] with IConflictCause
  case class RuleTyperEvaluateUnknown[T](
  ) extends IRuleTyperEvaluateResult[T]
  case class RuleTyperEvaluateSuccess[+T](
    result: T
  ) extends IRuleTyperEvaluateResult[T]


  sealed trait IRuleTyperMatchResult[+T]
  case class RuleTyperMatchConflict[+T](
    // This is in here because when we do an Or rule, we want to know why each
    // case failed; we want to have all the conflicts in a row, we want to have
    // the conclusions for each failure.
    conclusions: Conclusions,
    message: String,
    // For an Or rule, this will contain all the conflicts for each branch.
    causes: List[IConflictCause]
  ) extends IRuleTyperMatchResult[T] with IConflictCause {
    override def toString: String = {
      // The # signals the reader that we overrode toString
      "RuleTyperMatchConflict#(" + message + ", " + causes + ", " + conclusions + ")"
    }
  }
  // This means that we don't deeply know the entire subtree.
  case class RuleTyperMatchUnknown[+T](
  ) extends IRuleTyperMatchResult[T]
  case class RuleTyperMatchSuccess[+T](
    result: T
  ) extends IRuleTyperMatchResult[T]
}
