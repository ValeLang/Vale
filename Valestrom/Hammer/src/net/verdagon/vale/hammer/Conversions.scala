package net.verdagon.vale.hammer

import net.verdagon.vale.astronomer._
import net.verdagon.vale.metal._
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.templar.{types => t}
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.{vimpl, scout => s}

object Conversions {
  def evaluateCodeLocation(loc: s.CodeLocationS): m.CodeLocation = {
    val s.CodeLocationS(line, col) = loc
    m.CodeLocation(line, col)
  }

  def evaluateMutability(mutability: t.MutabilityT): Mutability = {
    mutability match {
      case t.MutableT => Mutable
      case t.ImmutableT => Immutable
    }
  }

  def evaluatePermission(permission: t.PermissionT): PermissionH = {
    permission match {
      case t.ReadonlyT => ReadonlyH
      case t.ReadwriteT => ReadwriteH
    }
  }

  def evaluateLocation(location: t.LocationT): LocationH = {
    location match {
      case t.InlineT => InlineH
      case t.YonderT => YonderH
    }
  }

  def evaluateVariability(variability: t.VariabilityT): Variability = {
    variability match {
      case t.FinalT => Final
      case t.VaryingT => Varying
    }
  }

  def evaluateOwnership(ownership: t.OwnershipT): OwnershipH = {
    ownership match {
      case t.OwnT => OwnH
      case t.ConstraintT => BorrowH
      case t.ShareT => ShareH
      case t.WeakT => WeakH
    }
  }

  def unevaluateOwnership(ownership: OwnershipH): m.OwnershipH = {
    ownership match {
      case OwnH => m.OwnH
      case BorrowH => m.BorrowH
      case ShareH => m.ShareH
    }
  }

  def unevaluateTemplataType(tyype: ITemplataType): ITypeSR = {
    tyype match {
      case CoordTemplataType => CoordTypeSR
      case KindTemplataType => KindTypeSR
      case IntegerTemplataType => IntTypeSR
      case BooleanTemplataType => BoolTypeSR
      case MutabilityTemplataType => MutabilityTypeSR
      case PermissionTemplataType => PermissionTypeSR
      case LocationTemplataType => LocationTypeSR
      case OwnershipTemplataType => OwnershipTypeSR
      case VariabilityTemplataType => VariabilityTypeSR
      case TemplateTemplataType(_, _) => vimpl() // can we even specify template types in the syntax?
    }
  }
}
