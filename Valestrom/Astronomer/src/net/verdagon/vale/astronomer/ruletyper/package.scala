package net.verdagon.vale.astronomer

import net.verdagon.vale.scout.{IRuneS, RangeS}
import net.verdagon.vale.{vassert, vcurious, vimpl}

package object ruletyper {
  case class ConclusionsBox(var conclusions: Conclusions) {
    def typeByRune = conclusions.typeByRune
    def addConclusion(rune: IRuneA, tyype: ITemplataType): Unit = {
      conclusions = conclusions.addConclusion(rune, tyype)
    }
  }

  case class Conclusions(
      typeByRune: Map[IRuneA, ITemplataType]) {
    override def hashCode(): Int = vcurious();
    def addConclusion(rune: IRuneA, tyype: ITemplataType): Conclusions = {
      vassert(!typeByRune.contains(rune))
      Conclusions(typeByRune + (rune -> tyype))
    }
  }

  trait IConflictCause {
    def range: RangeS
  }
  case class MultipleCauses(causes: Vector[IConflictCause]) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

  sealed trait IRuleTyperSolveResult[T]
  case class RuleTyperSolveFailure[T](
    conclusions: ConclusionsBox,
    range: RangeS,
    message: String,
    inner: Vector[IConflictCause]
  ) extends IRuleTyperSolveResult[T] with IConflictCause {
    override def hashCode(): Int = vcurious()
  }
  case class RuleTyperSolveSuccess[T](
    types: T
  ) extends IRuleTyperSolveResult[T] {
    override def hashCode(): Int = vcurious()
  }


  sealed trait IRuleTyperEvaluateResult[+T]
  case class RuleTyperEvaluateConflict[T](
    // This is in here because when we do an Or rule, we want to know why each
    // case failed; we want to have all the conflicts in a row, we want to have
    // the conclusions for each failure.
    conclusions: Conclusions,
    range: RangeS,
    message: String,
    cause: Option[IConflictCause]
  ) extends IRuleTyperEvaluateResult[T] with IConflictCause {
    override def hashCode(): Int = vcurious()
  }
  case class RuleTyperEvaluateUnknown[T](
  ) extends IRuleTyperEvaluateResult[T] {
    override def hashCode(): Int = vcurious()
  }
  case class RuleTyperEvaluateSuccess[+T](
    result: T
  ) extends IRuleTyperEvaluateResult[T] {
    override def hashCode(): Int = vcurious()
  }


  sealed trait IRuleTyperMatchResult[+T]
  case class RuleTyperMatchConflict[+T](
    // This is in here because when we do an Or rule, we want to know why each
    // case failed; we want to have all the conflicts in a row, we want to have
    // the conclusions for each failure.
    conclusions: Conclusions,
    range: RangeS,
    message: String,
    // For an Or rule, this will contain all the conflicts for each branch.
    causes: Vector[IConflictCause]
  ) extends IRuleTyperMatchResult[T] with IConflictCause {
    override def hashCode(): Int = vcurious()
    override def toString: String = {
      // The # signals the reader that we overrode toString
      "RuleTyperMatchConflict#(" + message + ", " + causes + ", " + conclusions + ")"
    }
  }
  // This means that we don't deeply know the entire subtree.
  case class RuleTyperMatchUnknown[+T](
  ) extends IRuleTyperMatchResult[T] {
    override def hashCode(): Int = vcurious()
  }
  case class RuleTyperMatchSuccess[+T](
    result: T
  ) extends IRuleTyperMatchResult[T] {
    override def hashCode(): Int = vcurious()
  }
}
