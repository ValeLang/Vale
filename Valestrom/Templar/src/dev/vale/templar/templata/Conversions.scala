package dev.vale.templar.templata

import dev.vale.parser.ast.{BorrowP, FinalP, ImmutableP, InlineP, LocationP, MutabilityP, MutableP, OwnP, OwnershipP, ShareP, VariabilityP, VaryingP, WeakP, YonderP}
import dev.vale.templar.types.{BorrowT, FinalT, ImmutableT, InlineT, LocationT, MutabilityT, MutableT, OwnT, OwnershipT, ShareT, VariabilityT, VaryingT, WeakT, YonderT}
import dev.vale.vimpl
import dev.vale.astronomer._
import dev.vale.parser._
import dev.vale.parser.ast._
import dev.vale.scout.rules._
import dev.vale.{scout => s}
import dev.vale.templar.{types => t}
import dev.vale.templar.types._

object Conversions {
  def evaluateMutability(mutability: MutabilityP): MutabilityT = {
    mutability match {
      case MutableP => MutableT
      case ImmutableP => ImmutableT
    }
  }

  def evaluateLocation(location: LocationP): LocationT = {
    location match {
      case InlineP => InlineT
      case YonderP => YonderT
    }
  }

  def evaluateVariability(variability: VariabilityP): VariabilityT = {
    variability match {
      case FinalP => FinalT
      case VaryingP => VaryingT
    }
  }

  def evaluateOwnership(ownership: OwnershipP): OwnershipT = {
    ownership match {
      case OwnP => OwnT
      case BorrowP => BorrowT
      case WeakP => WeakT
      case ShareP => ShareT
    }
  }

  def evaluateMaybeOwnership(maybeOwnership: Option[OwnershipP]): Option[OwnershipT] = {
    maybeOwnership.map({
      case OwnP => OwnT
      case WeakP => WeakT
      case ShareP => ShareT
    })
  }

  def unevaluateOwnership(ownership: OwnershipT): OwnershipP = {
    ownership match {
      case OwnT => OwnP
      case BorrowT => BorrowP
      case WeakT => WeakP
      case ShareT => ShareP
    }
  }

  def unevaluateMutability(mutability: MutabilityT): MutabilityP = {
    mutability match {
      case MutableT => MutableP
      case ImmutableT => ImmutableP
    }
  }
}
