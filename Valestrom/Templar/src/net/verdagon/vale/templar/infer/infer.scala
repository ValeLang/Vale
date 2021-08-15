package net.verdagon.vale.templar.infer

import net.verdagon.vale.astronomer.ITemplataType
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.IRuneT
import net.verdagon.vale.templar.templata.ITemplata
import net.verdagon.vale.templar.types.ParamFilter
import net.verdagon.vale.{vassert, vcurious, vimpl}

import scala.collection.immutable.List

package object infer {
  // When the user says "int", that's assumed to be a Kind.
  // It can sometimes be coerced to a coord, see CCKTC.
  case class UncoercedTemplata(templata: ITemplata) { override def hashCode(): Int = vcurious() }

  trait IConflictCause {
    def range: RangeS
    def inferences: Inferences
    def message: String
    def causes: List[IConflictCause]
  }

  sealed trait IInferSolveResult
  case class InferSolveFailure(
    typeByRune: Map[IRuneT, ITemplataType],
    directInputs: Map[IRuneT, ITemplata],
    maybeParamInputs: Option[List[ParamFilter]],
    inferences: Inferences,
    range: RangeS,
    message: String,
    causes: List[IConflictCause]
  ) extends IInferSolveResult with IConflictCause {
    override def hashCode(): Int = vcurious()
    vassert(message.nonEmpty || causes.nonEmpty)
  }
  case class InferSolveSuccess(
    inferences: Inferences
  ) extends IInferSolveResult { override def hashCode(): Int = vcurious() }

  sealed trait IInferEvaluateResult[+T]
  case class InferEvaluateConflict[T](
    // This is in here because when we do an Or rule, we want to know why each
    // case failed; we want to have all the conflicts in a row, we want to have
    // the inferences for each failure.
    inferences: Inferences,
    range: RangeS,
    message: String,
    causes: List[IConflictCause]
  ) extends IInferEvaluateResult[T] with IConflictCause {
    override def hashCode(): Int = vcurious()
    vassert(message.nonEmpty || causes.nonEmpty)
  }
  case class InferEvaluateUnknown[T](
    // Whether we've satisfied every rule in this subtree.
    // This can be false for example if we have rule like `Moo = (Bork like ISomething<#T>)`
    // when we don't know #T yet, but we do know the result of the
    // `(Bork like ISomething<#T>)` rule.
    // See IEUNDS for why unknowns need deeplySatisfied.
    deeplySatisfied: Boolean,
  ) extends IInferEvaluateResult[T] {
    override def hashCode(): Int = vcurious()
  }
  case class InferEvaluateSuccess[T](
    templata: T,

    // Whether we've satisfied every rule in this subtree.
    // This can be false for example if we have rule like `Moo = (Bork like ISomething<#T>)`
    // when we don't know #T yet, but we do know the result of the
    // `(Bork like ISomething<#T>)` rule.
    deeplySatisfied: Boolean,
  ) extends IInferEvaluateResult[T] {
    override def hashCode(): Int = vcurious()
  }


  sealed trait IInferMatchResult
  case class InferMatchConflict(
    // This is in here because when we do an Or rule, we want to know why each
    // case failed; we want to have all the conflicts in a row, we want to have
    // the inferences for each failure.
    inferences: Inferences,
    range: RangeS,
    message: String,
    // For an Or rule, this will contain all the conflicts for each branch.
    causes: List[IConflictCause]
  ) extends IInferMatchResult with IConflictCause {
    override def hashCode(): Int = vcurious()
    vassert(message.nonEmpty || causes.nonEmpty)
    override def toString: String = {
      // The # signals the reader that we overrode toString
      "InferMatchConflict#(" + message + ", " + causes + ", " + inferences + ")"
    }
  }
  case class InferMatchSuccess(
    // Whether we've satisfied every rule in this subtree.
    // This can be false for example if we have rule like `Moo = (Bork like ISomething<#T>)`
    // when we don't know #T yet, but we do know the result of the
    // `(Bork like ISomething<#T>)` rule.
    deeplySatisfied: Boolean
  ) extends IInferMatchResult {
    override def hashCode(): Int = vcurious()
  }
}
