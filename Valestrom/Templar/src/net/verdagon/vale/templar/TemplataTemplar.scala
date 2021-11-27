package net.verdagon.vale.templar

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.rules.IRulexSR
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env.{IEnvironment, IEnvironmentBox, TemplataLookupContext}
import net.verdagon.vale.templar.names.{AnonymousSubstructNameT, CitizenNameT, INameT, NameTranslator}
import net.verdagon.vale.{IProfiler, RangeS, vassert, vassertOne, vassertSome, vcurious, vfail, vimpl, vwat}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._

import scala.collection.immutable.{List, Map, Set}

trait ITemplataTemplarDelegate {

  def getAncestorInterfaceDistance(
    temputs: Temputs,
    descendantCitizenRef: CitizenRefT,
    ancestorInterfaceRef: InterfaceTT):
  Option[Int]

  def getStructRef(
    temputs: Temputs,
    callRange: RangeS,
    structTemplata: StructTemplata,
    uncoercedTemplateArgs: Vector[ITemplata]):
  StructTT

  def getInterfaceRef(
    temputs: Temputs,
    callRange: RangeS,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceTemplata,
    uncoercedTemplateArgs: Vector[ITemplata]):
  InterfaceTT

  def getStaticSizedArrayKind(
    env: IEnvironment,
    temputs: Temputs,
    mutability: MutabilityT,
    variability: VariabilityT,
    size: Int,
    type2: CoordT):
  StaticSizedArrayTT

  def getRuntimeSizedArrayKind(env: IEnvironment, temputs: Temputs, element: CoordT, arrayMutability: MutabilityT, arrayVariability: VariabilityT): RuntimeSizedArrayTT
}

class TemplataTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
    delegate: ITemplataTemplarDelegate) {

  def getTypeDistance(
    temputs: Temputs,
    sourcePointerType: CoordT,
    targetPointerType: CoordT):
  (Option[TypeDistance]) = {

    val CoordT(targetOwnership, targetPermission, targetType) = targetPointerType;
    val CoordT(sourceOwnership, sourcePermission, sourceType) = sourcePointerType;

    if (sourceType == NeverT()) {
      return (Some(TypeDistance(0, 0, 0)))
    }

    val upcastDistance =
      if (sourceType == targetType) {
        (0)
      } else {
        (sourceType, targetType) match {
          case (VoidT(), _) => return (None)
          case (IntT(_), _) => return (None)
          case (BoolT(), _) => return (None)
          case (StrT(), _) => return (None)
          case (_, VoidT()) => return (None)
          case (_, IntT(_)) => return (None)
          case (_, BoolT()) => return (None)
          case (_, StrT()) => return (None)
          case (_, StructTT(_)) => return (None)
          case (a @ StructTT(_), b @ InterfaceTT(_)) => {
            delegate.getAncestorInterfaceDistance(temputs, a, b) match {
              case (None) => return (None)
              case (Some(distance)) => (distance)
            }
          }
          case (a @ InterfaceTT(_), b @ InterfaceTT(_)) => {
            delegate.getAncestorInterfaceDistance(temputs, a, b) match {
              case (None) => return (None)
              case (Some(distance)) => (distance)
            }
          }
          case (_ : CitizenRefT, IntT(_) | BoolT() | StrT() | FloatT()) => return (None)
          case (IntT(_) | BoolT() | StrT() | FloatT(), _ : CitizenRefT) => return (None)
          case (_, RuntimeSizedArrayTT(_)) => return None
          case (RuntimeSizedArrayTT(_), _) => return None
          case (_, StaticSizedArrayTT(_, _)) => return None
          case (StaticSizedArrayTT(_, _), _) => return None
          case _ => {
            vfail("Can't convert from " + sourceType + " to " + targetType)
          }
        }
      }

    val ownershipDistance =
      (sourceOwnership, targetOwnership) match {
        case (OwnT, OwnT) => 0
        case (OwnT, ConstraintT) => 1
        case (OwnT, WeakT) => 1
        case (OwnT, ShareT) => return None
        case (ConstraintT, OwnT) => return (None)
        case (ConstraintT, ConstraintT) => 0
        case (ConstraintT, WeakT) => 1
        case (ConstraintT, ShareT) => return None
        case (WeakT, OwnT) => return None
        case (WeakT, ConstraintT) => return None
        case (WeakT, WeakT) => 0
        case (WeakT, ShareT) => return None
        case (ShareT, ShareT) => 0
        case (ShareT, ConstraintT) => return None
        case (ShareT, WeakT) => return None
        case (ShareT, OwnT) => return None
      }

    val permissionDistance =
      (sourcePermission, targetPermission) match {
        case (ReadonlyT, ReadonlyT) => 0
        case (ReadonlyT, ReadwriteT) => return None
        // Could eventually make this 1 instead of None, if we want to implicitly
        // go from readwrite to readonly, that would be nice.
        case (ReadwriteT, ReadonlyT) => return None
        case (ReadwriteT, ReadwriteT) => 0
      }

    (Some(TypeDistance(upcastDistance, ownershipDistance, permissionDistance)))
  }

  def isTypeConvertible(
    temputs: Temputs,
    sourcePointerType: CoordT,
    targetPointerType: CoordT):
  (Boolean) = {
    val maybeDistance =
      getTypeDistance(temputs, sourcePointerType, targetPointerType)
    (maybeDistance.nonEmpty)
  }

  def isTypeTriviallyConvertible(
    temputs: Temputs,
    sourcePointerType: CoordT,
    targetPointerType: CoordT):
  (Boolean) = {
    val CoordT(targetOwnership, targetPermission, targetType) = targetPointerType;
    val CoordT(sourceOwnership, sourcePermission, sourceType) = sourcePointerType;

    if (sourceType == NeverT()) {
      return (true)
    }

    if (sourceType == targetType) {

    } else {
      (sourceType, targetType) match {
        case (VoidT(), _) => return (false)
        case (IntT(_), _) => return (false)
        case (BoolT(), _) => return (false)
        case (StrT(), _) => return (false)
        case (RuntimeSizedArrayTT(_), _) => return (false)
        case (StaticSizedArrayTT(_, _), _) => return (false)
        case (_, VoidT()) => return (false)
        case (_, IntT(_)) => return (false)
        case (_, BoolT()) => return (false)
        case (_, StrT()) => return (false)
        case (_, StaticSizedArrayTT(_, _)) => return (false)
        case (_, StructTT(_)) => return (false)
        case (a @ StructTT(_), b @ InterfaceTT(_)) => {
          delegate.getAncestorInterfaceDistance(temputs, a, b) match {
            case (None) => return (false)
            case (Some(_)) =>
          }
        }
        case (a @ InterfaceTT(_), b @ InterfaceTT(_)) => {
          delegate.getAncestorInterfaceDistance(temputs, a, b) match {
            case (None) => return (false)
            case (Some(_)) =>
          }
        }
        case (_ : CitizenRefT, IntT(_) | BoolT() | StrT() | FloatT()) => return (false)
        case (IntT(_) | BoolT() | StrT() | FloatT(), _ : CitizenRefT) => return (false)
        case _ => {
          vfail("Can't convert from " + sourceType + " to " + targetType)
        }
      }
    }

    if (sourceOwnership != targetOwnership) {
      return false
    }

    if (sourcePermission != targetPermission) {
      return false
    }

    true
  }

  def pointifyKind(temputs: Temputs, kind: KindT, ownershipIfMutable: OwnershipT): CoordT = {
    val mutability = Templar.getMutability(temputs, kind)
    val ownership = if (mutability == MutableT) ownershipIfMutable else ShareT
    val permission = if (mutability == MutableT) ReadwriteT else ReadonlyT
    kind match {
      case a @ RuntimeSizedArrayTT(array) => {
        CoordT(ownership, permission, a)
      }
      case a @ StaticSizedArrayTT(_, RawArrayTT(_, mutability, variability)) => {
        CoordT(ownership, permission, a)
      }
      case s @ StructTT(_) => {
        CoordT(ownership, permission, s)
      }
      case i @ InterfaceTT(_) => {
        CoordT(ownership, permission, i)
      }
      case VoidT() => {
        CoordT(ShareT, ReadonlyT, VoidT())
      }
      case i @ IntT(_) => {
        CoordT(ShareT, ReadonlyT, i)
      }
      case FloatT() => {
        CoordT(ShareT, ReadonlyT, FloatT())
      }
      case BoolT() => {
        CoordT(ShareT, ReadonlyT, BoolT())
      }
      case StrT() => {
        CoordT(ShareT, ReadonlyT, StrT())
      }
    }
  }

  def evaluateStructTemplata(
    temputs: Temputs,
    callRange: RangeS,
    template: StructTemplata,
    templateArgs: Vector[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.getStructRef(temputs, callRange, template, templateArgs)
    val templata =
      coerce(temputs, callRange, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def evaluateInterfaceTemplata(
    temputs: Temputs,
    callRange: RangeS,
    template: InterfaceTemplata,
    templateArgs: Vector[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.getInterfaceRef(temputs, callRange, template, templateArgs)
    val templata =
      coerce(temputs, callRange, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def evaluateBuiltinTemplateTemplata(
    env: IEnvironment,
    temputs: Temputs,
    range: RangeS,
    template: RuntimeSizedArrayTemplateTemplata,
    templateArgs: Vector[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val Vector(MutabilityTemplata(mutability), VariabilityTemplata(variability), CoordTemplata(elementType)) = templateArgs
    val arrayKindTemplata = delegate.getRuntimeSizedArrayKind(env, temputs, elementType, mutability, variability)
    val templata =
      coerce(temputs, range, KindTemplata(arrayKindTemplata), expectedType)
    (templata)
  }

  def getStaticSizedArrayKind(
    env: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    mutability: MutabilityT,
    variability: VariabilityT,
    size: Int,
    element: CoordT,
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.getStaticSizedArrayKind(env, temputs, mutability, variability, size, element)
    val templata =
      coerce(temputs, callRange, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def lookupTemplata(
    env: IEnvironment,
    temputs: Temputs,
    range: RangeS,
    name: INameT):
  (ITemplata) = {
    // Changed this from AnythingLookupContext to TemplataLookupContext
    // because this is called from StructTemplar to figure out its members.
    // We could instead pipe a lookup context through, if this proves problematic.
    vassertOne(env.lookupNearestWithName(profiler, name, Set(TemplataLookupContext)))
  }

  def lookupTemplata(
    env: IEnvironment,
    temputs: Temputs,
    range: RangeS,
    name: IImpreciseNameS):
  Option[ITemplata] = {
    // Changed this from AnythingLookupContext to TemplataLookupContext
    // because this is called from StructTemplar to figure out its members.
    // We could instead pipe a lookup context through, if this proves problematic.
    val results = env.lookupNearestWithImpreciseName(profiler, name, Set(TemplataLookupContext))
    if (results.size > 1) {
      vfail()
    }
    results.headOption
  }

  def coerceKindToCoord(temputs: Temputs, kind: KindT):
  CoordT = {
    val mutability = Templar.getMutability(temputs, kind)
    CoordT(
      if (mutability == MutableT) OwnT else ShareT,
      if (mutability == MutableT) ReadwriteT else ReadonlyT,
      kind)
  }

  def coerce(
    temputs: Temputs,
    range: RangeS,
    templata: ITemplata,
    tyype: ITemplataType):
  (ITemplata) = {
    if (templata.tyype == tyype) {
      templata
    } else {
      (templata, tyype) match {
        case (KindTemplata(kind), CoordTemplataType) => {
          CoordTemplata(coerceKindToCoord(temputs, kind))
        }
        case (st@StructTemplata(_, structA), KindTemplataType) => {
          if (structA.isTemplate) {
            vfail("Can't coerce " + structA.name + " to be a kind, is a template!")
          }
          val kind =
            delegate.getStructRef(temputs, range, st, Vector.empty)
          (KindTemplata(kind))
        }
        case (it@InterfaceTemplata(_, interfaceA), KindTemplataType) => {
          if (interfaceA.isTemplate) {
            vfail("Can't coerce " + interfaceA.name + " to be a kind, is a template!")
          }
          val kind =
            delegate.getInterfaceRef(temputs, range, it, Vector.empty)
          (KindTemplata(kind))
        }
        case (st@StructTemplata(_, structA), CoordTemplataType) => {
          if (structA.isTemplate) {
            vfail("Can't coerce " + structA.name + " to be a coord, is a template!")
          }
          val kind =
            delegate.getStructRef(temputs, range, st, Vector.empty)
          val mutability = Templar.getMutability(temputs, kind)

          // Default ownership is own for mutables, share for imms
          val ownership = if (mutability == MutableT) OwnT else ShareT
          // Default permission is readwrite for mutables, readonly for imms
          val permission = if (mutability == MutableT) ReadwriteT else ReadonlyT
          val coerced = CoordTemplata(CoordT(ownership, permission, kind))
          (coerced)
        }
        case (it@InterfaceTemplata(_, interfaceA), CoordTemplataType) => {
          if (interfaceA.isTemplate) {
            vfail("Can't coerce " + interfaceA.name + " to be a coord, is a template!")
          }
          val kind =
            delegate.getInterfaceRef(temputs, range, it, Vector.empty)
          val mutability = Templar.getMutability(temputs, kind)
          val coerced =
            CoordTemplata(
              CoordT(
                if (mutability == MutableT) OwnT else ShareT,
                if (mutability == MutableT) ReadwriteT else ReadonlyT,
                kind))
          (coerced)
        }
        case _ => {
          vfail("Can't coerce a " + templata.tyype + " to be a " + tyype)
        }
      }
    }
  }

  def citizenIsFromTemplate(actualCitizenRef: CitizenRefT, expectedCitizenTemplata: ITemplata): Boolean = {
    val citizenTemplateFullName =
      expectedCitizenTemplata match {
        case StructTemplata(env, originStruct) => {
          env.fullName.addStep(NameTranslator.translateCitizenName(originStruct.name))
        }
        case InterfaceTemplata(env, originInterface) => {
          env.fullName.addStep(NameTranslator.translateCitizenName(originInterface.name))
        }
        case KindTemplata(expectedKind) => return actualCitizenRef == expectedKind
        case CoordTemplata(CoordT(OwnT | ShareT, ReadonlyT, actualKind)) => return actualCitizenRef == actualKind
        case _ => return false
      }
    if (actualCitizenRef.fullName.initSteps != citizenTemplateFullName.initSteps) {
      // Packages dont match, bail
      return false
    }
    citizenTemplateFullName.last == actualCitizenRef.fullName.last.template
  }
}

case class TypeDistance(upcastDistance: Int, ownershipDistance: Int, permissionDistance: Int) {
  override def hashCode(): Int = vcurious()
  def lessThanOrEqualTo(that: TypeDistance): Boolean = {
    if (this.upcastDistance < that.upcastDistance) return true;
    if (this.upcastDistance > that.upcastDistance) return false;
    if (this.ownershipDistance < that.ownershipDistance) return true;
    if (this.ownershipDistance > that.ownershipDistance) return false;
    if (this.permissionDistance < that.permissionDistance) return true;
    if (this.permissionDistance > that.permissionDistance) return false;
    true
  }
}
