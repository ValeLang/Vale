package dev.vale.typing

import dev.vale.{RangeS, vassertOne, vfail}
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing.{CoordTemplataType, IImpreciseNameS, ITemplataType, KindTemplataType}
import dev.vale.typing.env.{IEnvironment, TemplataLookupContext}
import dev.vale.typing.names.{INameT, NameTranslator}
import dev.vale.typing.templata.{CoordTemplata, ITemplata, InterfaceTemplata, KindTemplata, MutabilityTemplata, RuntimeSizedArrayTemplateTemplata, StructTemplata}
import dev.vale.typing.types.{BoolT, BorrowT, CitizenRefT, CoordT, FloatT, IntT, InterfaceTT, KindT, MutabilityT, MutableT, NeverT, OwnT, OwnershipT, RuntimeSizedArrayTT, ShareT, StaticSizedArrayTT, StrT, StructTT, VariabilityT, VoidT, WeakT}
import dev.vale.highertyping._
import dev.vale.postparsing._
import dev.vale.typing._
import dev.vale.typing.citizen.AncestorHelper
import dev.vale.typing.env.TemplataLookupContext
import dev.vale.typing.names.AnonymousSubstructNameT
import dev.vale.RangeS
import dev.vale.typing.types._
import dev.vale.typing.templata._

import scala.collection.immutable.{List, Map, Set}

trait ITemplataCompilerDelegate {

  def isAncestor(
    coutputs: CompilerOutputs,
    descendantCitizenRef: CitizenRefT,
    ancestorInterfaceRef: InterfaceTT):
  Boolean

  def getStructRef(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    structTemplata: StructTemplata,
    uncoercedTemplateArgs: Vector[ITemplata]):
  StructTT

  def getInterfaceRef(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceTemplata,
    uncoercedTemplateArgs: Vector[ITemplata]):
  InterfaceTT

  def getStaticSizedArrayKind(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    mutability: MutabilityT,
    variability: VariabilityT,
    size: Int,
    type2: CoordT):
  StaticSizedArrayTT

  def getRuntimeSizedArrayKind(env: IEnvironment, coutputs: CompilerOutputs, element: CoordT, arrayMutability: MutabilityT): RuntimeSizedArrayTT
}

class TemplataCompiler(
  opts: TypingPassOptions,

  nameTranslator: NameTranslator,
  delegate: ITemplataCompilerDelegate) {

  def matchesParamFilter(
    coutputs: CompilerOutputs,
    sourcePointerType: ParamFilter,
    targetPointerType: CoordT):
  Boolean = {
    isOwnershipConvertible(sourcePointerType.ownership, targetPointerType.ownership) &&
      isKindConvertible(coutputs, sourcePointerType.kind, targetPointerType.kind)
  }

  def isTypeConvertible(
    coutputs: CompilerOutputs,
    sourcePointerType: CoordT,
    targetPointerType: CoordT):
  Boolean = {
    sourcePointerType.kind match {
      case NeverT(_) => return true
      case _ =>
    }
    isOwnershipConvertible(sourcePointerType.ownership, targetPointerType.ownership) &&
      isKindConvertible(coutputs, sourcePointerType.kind, targetPointerType.kind)
  }

  def isKindConvertible(
    coutputs: CompilerOutputs,
    sourceKind: KindT,
    targetKind: KindT):
  Boolean = {
    (sourceKind, targetKind) match {
      case (NeverT(_), _) => return true
      case (a, b) if a == b =>
      case (VoidT() | IntT(_) | BoolT() | StrT() | FloatT() | RuntimeSizedArrayTT(_, _) | StaticSizedArrayTT(_, _, _, _), _) => return false
      case (_, VoidT() | IntT(_) | BoolT() | StrT() | FloatT() | RuntimeSizedArrayTT(_, _) | StaticSizedArrayTT(_, _, _, _)) => return false
      case (_, StructTT(_)) => return false
      case (a@StructTT(_), b@InterfaceTT(_)) => {
        if (!delegate.isAncestor(coutputs, a, b)) {
          return false
        }
      }
      case (a@InterfaceTT(_), b@InterfaceTT(_)) => {
        if (!delegate.isAncestor(coutputs, a, b)) {
          return false
        }
      }
      case _ => {
        vfail("Dont know if we can convert from " + sourceKind + " to " + targetKind)
      }
    }
    true
  }

  def isOwnershipConvertible(
    sourceOwnership: OwnershipT,
    targetOwnership: OwnershipT):
  Boolean = {
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
//    coutputs: CompilerOutputs,
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
//          delegate.isAncestor(coutputs, a, b) match {
//            case (None) => return (false)
//            case (Some(_)) =>
//          }
//        }
//        case (a @ InterfaceTT(_), b @ InterfaceTT(_)) => {
//          delegate.isAncestor(coutputs, a, b) match {
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

  def pointifyKind(coutputs: CompilerOutputs, kind: KindT, ownershipIfMutable: OwnershipT): CoordT = {
    val mutability = Compiler.getMutability(coutputs, kind)
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
    coutputs: CompilerOutputs,
    callRange: RangeS,
    template: StructTemplata,
    templateArgs: Vector[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.getStructRef(coutputs, callRange, template, templateArgs)
    val templata =
      coerce(coutputs, callRange, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def evaluateInterfaceTemplata(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    template: InterfaceTemplata,
    templateArgs: Vector[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.getInterfaceRef(coutputs, callRange, template, templateArgs)
    val templata =
      coerce(coutputs, callRange, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def evaluateBuiltinTemplateTemplata(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    range: RangeS,
    template: RuntimeSizedArrayTemplateTemplata,
    templateArgs: Vector[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val Vector(MutabilityTemplata(mutability), CoordTemplata(elementType)) = templateArgs
    val arrayKindTemplata = delegate.getRuntimeSizedArrayKind(env, coutputs, elementType, mutability)
    val templata =
      coerce(coutputs, range, KindTemplata(arrayKindTemplata), expectedType)
    (templata)
  }

  def getStaticSizedArrayKind(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    mutability: MutabilityT,
    variability: VariabilityT,
    size: Int,
    element: CoordT,
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.getStaticSizedArrayKind(env, coutputs, mutability, variability, size, element)
    val templata =
      coerce(coutputs, callRange, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def lookupTemplata(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    range: RangeS,
    name: INameT):
  (ITemplata) = {
    // Changed this from AnythingLookupContext to TemplataLookupContext
    // because this is called from StructCompiler to figure out its members.
    // We could instead pipe a lookup context through, if this proves problematic.
    vassertOne(env.lookupNearestWithName(name, Set(TemplataLookupContext)))
  }

  def lookupTemplata(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    range: RangeS,
    name: IImpreciseNameS):
  Option[ITemplata] = {
    // Changed this from AnythingLookupContext to TemplataLookupContext
    // because this is called from StructCompiler to figure out its members.
    // We could instead pipe a lookup context through, if this proves problematic.
    val results = env.lookupNearestWithImpreciseName(name, Set(TemplataLookupContext))
    if (results.size > 1) {
      vfail()
    }
    results.headOption
  }

  def coerceKindToCoord(coutputs: CompilerOutputs, kind: KindT):
  CoordT = {
    val mutability = Compiler.getMutability(coutputs, kind)
    CoordT(
      if (mutability == MutableT) OwnT else ShareT,
      kind)
  }

  def coerce(
    coutputs: CompilerOutputs,
    range: RangeS,
    templata: ITemplata,
    tyype: ITemplataType):
  (ITemplata) = {
    if (templata.tyype == tyype) {
      templata
    } else {
      (templata, tyype) match {
        case (KindTemplata(kind), CoordTemplataType) => {
          CoordTemplata(coerceKindToCoord(coutputs, kind))
        }
        case (st@StructTemplata(_, structA), KindTemplataType) => {
          if (structA.isTemplate) {
            vfail("Can't coerce " + structA.name + " to be a kind, is a template!")
          }
          val kind =
            delegate.getStructRef(coutputs, range, st, Vector.empty)
          (KindTemplata(kind))
        }
        case (it@InterfaceTemplata(_, interfaceA), KindTemplataType) => {
          if (interfaceA.isTemplate) {
            vfail("Can't coerce " + interfaceA.name + " to be a kind, is a template!")
          }
          val kind =
            delegate.getInterfaceRef(coutputs, range, it, Vector.empty)
          (KindTemplata(kind))
        }
        case (st@StructTemplata(_, structA), CoordTemplataType) => {
          if (structA.isTemplate) {
            vfail("Can't coerce " + structA.name + " to be a coord, is a template!")
          }
          val kind =
            delegate.getStructRef(coutputs, range, st, Vector.empty)
          val mutability = Compiler.getMutability(coutputs, kind)

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
            delegate.getInterfaceRef(coutputs, range, it, Vector.empty)
          val mutability = Compiler.getMutability(coutputs, kind)
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
