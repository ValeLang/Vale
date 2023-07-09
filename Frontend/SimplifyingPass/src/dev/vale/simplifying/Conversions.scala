package dev.vale.simplifying

import dev.vale.{CodeLocationS, finalast, vimpl}
import dev.vale.finalast._
import dev.vale.postparsing._
import dev.vale.highertyping._
import dev.vale.finalast._
import dev.vale.instantiating.ast._
import dev.vale.postparsing.rules._
import dev.vale.{finalast => m, postparsing => s}

object Conversions {
  def evaluateCodeLocation(loc: CodeLocationS): CodeLocation = {
    val CodeLocationS(line, col) = loc
    finalast.CodeLocation(line, col)
  }

  def evaluateMutability(mutability: MutabilityI): Mutability = {
    mutability match {
      case MutableI => Mutable
      case ImmutableI => Immutable
    }
  }

  def evaluateMutabilityTemplata(mutability: MutabilityI): Mutability = {
    mutability match {
      case MutableI => Mutable
      case ImmutableI => Immutable
    }
  }

  def evaluateVariabilityTemplata(mutability: VariabilityI): Variability = {
    mutability match {
      case VaryingI => Varying
      case FinalI => Final
    }
  }

  def evaluateLocation(location: LocationI): LocationH = {
    location match {
      case InlineI => InlineH
      case YonderI => YonderH
    }
  }

  def evaluateVariability(variability: VariabilityI): Variability = {
    variability match {
      case FinalI => Final
      case VaryingI => Varying
    }
  }

  def evaluateOwnership(ownership: OwnershipI): OwnershipH = {
    ownership match {
      case OwnI => OwnH
      case ImmutableBorrowI => ImmutableBorrowH
      case MutableBorrowI => MutableBorrowH
      case ImmutableShareI => ImmutableShareH
      case MutableShareI => MutableShareH
      case WeakI => WeakH
    }
  }

  def unevaluateTemplataType()(tyype: ITemplataType): ITemplataType = {
    tyype match {
      case CoordTemplataType() => CoordTemplataType()
      case KindTemplataType() => KindTemplataType()
      case IntegerTemplataType() => IntegerTemplataType()
      case BooleanTemplataType() => BooleanTemplataType()
      case MutabilityTemplataType() => MutabilityTemplataType()
      case LocationTemplataType() => LocationTemplataType()
      case OwnershipTemplataType() => OwnershipTemplataType()
      case VariabilityTemplataType() => VariabilityTemplataType()
      case TemplateTemplataType(_, _) => vimpl() // can we even specify template types in the syntax?
    }
  }
}
