package dev.vale.simplifying

import dev.vale.{CodeLocationS, finalast, vimpl}
import dev.vale.finalast.{BorrowH, CodeLocation, Final, Immutable, InlineH, LocationH, Mutability, Mutable, OwnH, OwnershipH, ShareH, Variability, Varying, WeakH, YonderH}
import dev.vale.postparsing._
import dev.vale.typing.types._
import dev.vale.highertyping._
import dev.vale.finalast._
import dev.vale.postparsing.rules._
import dev.vale.typing.templata.{ITemplataT, IntegerTemplataT, MutabilityTemplataT, VariabilityTemplataT}
import dev.vale.typing.types._
import dev.vale.typing.{types => t}
import dev.vale.{finalast => m, postparsing => s}

object Conversions {
  def evaluateCodeLocation(loc: CodeLocationS): CodeLocation = {
    val CodeLocationS(line, col) = loc
    finalast.CodeLocation(line, col)
  }

  def evaluateMutability(mutability: MutabilityT): Mutability = {
    mutability match {
      case MutableT => Mutable
      case ImmutableT => Immutable
    }
  }

  def evaluateMutabilityTemplata(mutability: ITemplataT[MutabilityTemplataType]): Mutability = {
    mutability match {
      case MutabilityTemplataT(MutableT) => Mutable
      case MutabilityTemplataT(ImmutableT) => Immutable
    }
  }

  def evaluateVariabilityTemplata(mutability: ITemplataT[VariabilityTemplataType]): Variability = {
    mutability match {
      case VariabilityTemplataT(VaryingT) => Varying
      case VariabilityTemplataT(FinalT) => Final
    }
  }

  def evaluateIntegerTemplata(templata: ITemplataT[IntegerTemplataType]): Long = {
    templata match {
      case IntegerTemplataT(n) => n
    }
  }

  def evaluateLocation(location: LocationT): LocationH = {
    location match {
      case InlineT => InlineH
      case YonderT => YonderH
    }
  }

  def evaluateVariability(variability: VariabilityT): Variability = {
    variability match {
      case FinalT => Final
      case VaryingT => Varying
    }
  }

  def evaluateOwnership(ownership: OwnershipT): OwnershipH = {
    ownership match {
      case OwnT => OwnH
      case BorrowT => BorrowH
      case ShareT => ShareH
      case WeakT => WeakH
    }
  }

  def unevaluateOwnership(ownership: OwnershipH): OwnershipH = {
    ownership match {
      case OwnH => finalast.OwnH
      case BorrowH => finalast.BorrowH
      case ShareH => finalast.ShareH
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
