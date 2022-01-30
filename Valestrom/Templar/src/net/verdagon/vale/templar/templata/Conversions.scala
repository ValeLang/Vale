package net.verdagon.vale.templar.templata

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast.{BorrowP, FinalP, ImmutableP, InlineP, LocationP, MutabilityP, MutableP, OwnP, OwnershipP, PermissionP, PointerP, ReadonlyP, ReadwriteP, ShareP, VariabilityP, VaryingP, WeakP, YonderP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.{scout => s}
import net.verdagon.vale.templar.{types => t}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vimpl

object Conversions {
  def evaluateMutability(mutability: MutabilityP): MutabilityT = {
    mutability match {
      case MutableP => MutableT
      case ImmutableP => ImmutableT
    }
  }

  def evaluatePermission(permission: PermissionP): PermissionT = {
    permission match {
      case ReadonlyP => ReadonlyT
      case ReadwriteP => ReadwriteT
//      case ExclusiveReadwriteP => ExclusiveReadwrite
      case _ => vimpl()
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
      case PointerP => PointerT
      case BorrowP => BorrowT
      case WeakP => WeakT
      case ShareP => ShareT
    }
  }

  def evaluateMaybeOwnership(maybeOwnership: Option[OwnershipP]): Option[OwnershipT] = {
    maybeOwnership.map({
      case OwnP => OwnT
      case PointerP => PointerT
      case WeakP => WeakT
      case ShareP => ShareT
    })
  }

  def unevaluateOwnership(ownership: OwnershipT): OwnershipP = {
    ownership match {
      case OwnT => OwnP
      case PointerT => PointerP
      case BorrowT => BorrowP
      case WeakT => WeakP
      case ShareT => ShareP
    }
  }

  def unevaluatePermission(permission: PermissionT): PermissionP = {
    permission match {
      case ReadonlyT => ReadonlyP
      case ReadwriteT => ReadwriteP
//      case ExclusiveReadwrite => ExclusiveReadwriteP
    }
  }

  def unevaluateMutability(mutability: MutabilityT): MutabilityP = {
    mutability match {
      case MutableT => MutableP
      case ImmutableT => ImmutableP
    }
  }
}
