package net.verdagon.vale.hammer

import net.verdagon.vale.astronomer._
import net.verdagon.vale.metal._
import net.verdagon.vale.scout.{BooleanTemplataType, CoordTemplataType, ITemplataType, IntegerTemplataType, KindTemplataType, LocationTemplataType, MutabilityTemplataType, OwnershipTemplataType, PermissionTemplataType, TemplateTemplataType, VariabilityTemplataType}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.{types => t}
import net.verdagon.vale.{CodeLocationS, vimpl, metal => m, scout => s}

object Conversions {
  def evaluateCodeLocation(loc: CodeLocationS): m.CodeLocation = {
    val CodeLocationS(line, col) = loc
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
      case t.PointerT => PointerH
      case t.BorrowT => BorrowH
      case t.ShareT => ShareH
      case t.WeakT => WeakH
    }
  }

  def unevaluateOwnership(ownership: OwnershipH): m.OwnershipH = {
    ownership match {
      case OwnH => m.OwnH
      case PointerH => m.PointerH
      case ShareH => m.ShareH
    }
  }

  def unevaluateTemplataType(tyype: ITemplataType): ITemplataType = {
    tyype match {
      case CoordTemplataType => CoordTemplataType
      case KindTemplataType => KindTemplataType
      case IntegerTemplataType => IntegerTemplataType
      case BooleanTemplataType => BooleanTemplataType
      case MutabilityTemplataType => MutabilityTemplataType
      case PermissionTemplataType => PermissionTemplataType
      case LocationTemplataType => LocationTemplataType
      case OwnershipTemplataType => OwnershipTemplataType
      case VariabilityTemplataType => VariabilityTemplataType
      case TemplateTemplataType(_, _) => vimpl() // can we even specify template types in the syntax?
    }
  }
}
