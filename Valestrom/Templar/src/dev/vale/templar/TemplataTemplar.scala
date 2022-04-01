package dev.vale.templar

import dev.vale.{RangeS, vassertOne, vfail}
import dev.vale.scout.rules.IRulexSR
import dev.vale.scout.{CoordTemplataType, IImpreciseNameS, ITemplataType, KindTemplataType}
import dev.vale.templar.env.{IEnvironment, TemplataLookupContext}
import dev.vale.templar.names.{INameT, NameTranslator}
import dev.vale.templar.templata.{CoordTemplata, ITemplata, InterfaceTemplata, KindTemplata, MutabilityTemplata, RuntimeSizedArrayTemplateTemplata, StructTemplata}
import dev.vale.templar.types.{BoolT, BorrowT, CitizenRefT, CoordT, FloatT, IntT, InterfaceTT, KindT, MutabilityT, MutableT, NeverT, OwnT, OwnershipT, RuntimeSizedArrayTT, ShareT, StaticSizedArrayTT, StrT, StructTT, VariabilityT, VoidT, WeakT}
import dev.vale.astronomer._
import dev.vale.scout._
import dev.vale.templar._
import dev.vale.templar.citizen.AncestorHelper
import dev.vale.templar.env.TemplataLookupContext
import dev.vale.templar.names.AnonymousSubstructNameT
import dev.vale.RangeS
import dev.vale.templar.types._
import dev.vale.templar.templata._

import scala.collection.immutable.{List, Map, Set}

trait ITemplataTemplarDelegate {

  def isAncestor(
    temputs: Temputs,
    descendantCitizenRef: CitizenRefT,
    ancestorInterfaceRef: InterfaceTT):
  Boolean

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

  def getRuntimeSizedArrayKind(env: IEnvironment, temputs: Temputs, element: CoordT, arrayMutability: MutabilityT): RuntimeSizedArrayTT
}

class TemplataTemplar(
  opts: TemplarOptions,

  nameTranslator: NameTranslator,
  delegate: ITemplataTemplarDelegate) {

  def isTypeConvertible(
    temputs: Temputs,
    sourcePointerType: CoordT,
    targetPointerType: CoordT):
  Boolean = {

    val CoordT(targetOwnership, targetType) = targetPointerType;
    val CoordT(sourceOwnership, sourceType) = sourcePointerType;

    // Note the Never case will short-circuit a true, regardless of the other checks (ownership)

    (sourceType, targetType) match {
      case (NeverT(_), _) => return true
      case (a, b) if a == b =>
      case (VoidT() | IntT(_) | BoolT() | StrT() | FloatT() | RuntimeSizedArrayTT(_, _) | StaticSizedArrayTT(_, _, _, _), _) => return false
      case (_, VoidT() | IntT(_) | BoolT() | StrT() | FloatT() | RuntimeSizedArrayTT(_, _) | StaticSizedArrayTT(_, _, _, _)) => return false
      case (_, StructTT(_)) => return false
      case (a @ StructTT(_), b @ InterfaceTT(_)) => {
        if (!delegate.isAncestor(temputs, a, b)) {
          return false
        }
      }
      case (a @ InterfaceTT(_), b @ InterfaceTT(_)) => {
        if (!delegate.isAncestor(temputs, a, b)) {
          return false
        }
      }
      case _ => {
        vfail("Dont know if we can convert from " + sourceType + " to " + targetType)
      }
    }

    (sourceOwnership, targetOwnership) match {
      case (a, b) if a == b =>
      // At some point maybe we should automatically convert borrow to pointer and vice versa
      // and perhaps automatically promote borrow or pointer to weak?
      case (OwnT, BorrowT) => return false
      case (OwnT, WeakT) => return false
      case (OwnT, ShareT) => return false
      case (BorrowT, OwnT) => return false
      case (BorrowT, WeakT) => return false
      case (BorrowT, ShareT) => return false
      case (WeakT, OwnT) => return false
      case (WeakT, BorrowT) => return false
      case (WeakT, ShareT) => return false
      case (ShareT, BorrowT) => return false
      case (ShareT, WeakT) => return false
      case (ShareT, OwnT) => return false
    }

    true
  }
//
//  def isTypeTriviallyConvertible(
//    temputs: Temputs,
//    sourcePointerType: CoordT,
//    targetPointerType: CoordT):
//  (Boolean) = {
//    val CoordT(targetOwnership, targetPermission, targetType) = targetPointerType;
//    val CoordT(sourceOwnership, sourcePermission, sourceType) = sourcePointerType;
//
//    if (sourceType == NeverT()) {
//      return (true)
//    }
//
//    if (sourceType == targetType) {
//
//    } else {
//      (sourceType, targetType) match {
//        case (VoidT(), _) => return (false)
//        case (IntT(_), _) => return (false)
//        case (BoolT(), _) => return (false)
//        case (StrT(), _) => return (false)
//        case (RuntimeSizedArrayTT(_, _), _) => return (false)
//        case (StaticSizedArrayTT(_, _, _, _), _) => return (false)
//        case (_, VoidT()) => return (false)
//        case (_, IntT(_)) => return (false)
//        case (_, BoolT()) => return (false)
//        case (_, StrT()) => return (false)
//        case (_, StaticSizedArrayTT(_, _, _, _)) => return (false)
//        case (_, StructTT(_)) => return (false)
//        case (a @ StructTT(_), b @ InterfaceTT(_)) => {
//          delegate.isAncestor(temputs, a, b) match {
//            case (None) => return (false)
//            case (Some(_)) =>
//          }
//        }
//        case (a @ InterfaceTT(_), b @ InterfaceTT(_)) => {
//          delegate.isAncestor(temputs, a, b) match {
//            case (None) => return (false)
//            case (Some(_)) =>
//          }
//        }
//        case (_ : CitizenRefT, IntT(_) | BoolT() | StrT() | FloatT()) => return (false)
//        case (IntT(_) | BoolT() | StrT() | FloatT(), _ : CitizenRefT) => return (false)
//        case _ => {
//          vfail("Can't convert from " + sourceType + " to " + targetType)
//        }
//      }
//    }
//
//    if (sourceOwnership != targetOwnership) {
//      return false
//    }
//
//    if (sourcePermission != targetPermission) {
//      return false
//    }
//
//    true
//  }

  def pointifyKind(temputs: Temputs, kind: KindT, ownershipIfMutable: OwnershipT): CoordT = {
    val mutability = Templar.getMutability(temputs, kind)
    val ownership = if (mutability == MutableT) ownershipIfMutable else ShareT
    kind match {
      case a @ RuntimeSizedArrayTT(_, _) => {
        CoordT(ownership, a)
      }
      case a @ StaticSizedArrayTT(_, _, _, _) => {
        CoordT(ownership, a)
      }
      case s @ StructTT(_) => {
        CoordT(ownership, s)
      }
      case i @ InterfaceTT(_) => {
        CoordT(ownership, i)
      }
      case VoidT() => {
        CoordT(ShareT, VoidT())
      }
      case i @ IntT(_) => {
        CoordT(ShareT, i)
      }
      case FloatT() => {
        CoordT(ShareT, FloatT())
      }
      case BoolT() => {
        CoordT(ShareT, BoolT())
      }
      case StrT() => {
        CoordT(ShareT, StrT())
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
    val Vector(MutabilityTemplata(mutability), CoordTemplata(elementType)) = templateArgs
    val arrayKindTemplata = delegate.getRuntimeSizedArrayKind(env, temputs, elementType, mutability)
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
    vassertOne(env.lookupNearestWithName(name, Set(TemplataLookupContext)))
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
    val results = env.lookupNearestWithImpreciseName(name, Set(TemplataLookupContext))
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
          val coerced = CoordTemplata(CoordT(ownership, kind))
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
          env.fullName.addStep(nameTranslator.translateCitizenName(originStruct.name))
        }
        case InterfaceTemplata(env, originInterface) => {
          env.fullName.addStep(nameTranslator.translateCitizenName(originInterface.name))
        }
        case KindTemplata(expectedKind) => return actualCitizenRef == expectedKind
        case CoordTemplata(CoordT(OwnT | ShareT, actualKind)) => return actualCitizenRef == actualKind
        case _ => return false
      }
    if (actualCitizenRef.fullName.initSteps != citizenTemplateFullName.initSteps) {
      // Packages dont match, bail
      return false
    }
    citizenTemplateFullName.last == actualCitizenRef.fullName.last.template
  }
}
