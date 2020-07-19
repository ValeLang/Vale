package net.verdagon.vale.templar.templata

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.ShareP
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.{IName2, NameTranslator}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vfail, vimpl}

import scala.collection.immutable.List

// Order of these members matters for comparison
case class TypeDistance(upcastDistance: Int, ownershipDistance: Int) {
  def lessThanOrEqualTo(that: TypeDistance): Boolean = {
    if (this.upcastDistance < that.upcastDistance) return true;
    if (this.upcastDistance > that.upcastDistance) return false;
    if (this.ownershipDistance < that.ownershipDistance) return true;
    if (this.ownershipDistance > that.ownershipDistance) return false;
    true
  }
}

trait ITemplataTemplarInnerDelegate[Env, State] {
  def lookupTemplata(env: Env, name: IName2): ITemplata
  def lookupTemplataImprecise(env: Env, name: IImpreciseNameStepA): ITemplata

  def getMutability(state: State, kind: Kind): Mutability

  def getPackKind(env: Env, temputs: State, types2: List[Coord]): (PackT2, Mutability)

  def evaluateStructTemplata(
    state: State,
    templata: StructTemplata,
    templateArgs: List[ITemplata]):
  (Kind)

  def evaluateInterfaceTemplata(
    state: State,
    templata: InterfaceTemplata,
    templateArgs: List[ITemplata]):
  (Kind)

  def getAncestorInterfaceDistance(
    temputs: State,
    descendantCitizenRef: CitizenRef2,
    ancestorInterfaceRef: InterfaceRef2):
  (Option[Int])

  def getArraySequenceKind(
    env: Env,
    state: State,
    mutability: Mutability,
    size: Int,
    element: Coord):
  (KnownSizeArrayT2)

  def getInterfaceTemplataType(it: InterfaceTemplata): ITemplataType
  def getStructTemplataType(st: StructTemplata): ITemplataType
}

class TemplataTemplarInner[Env, State](delegate: ITemplataTemplarInnerDelegate[Env, State]) {

//  def coerceTemplataToKind(
//    env: Env,
//    state: State,
//    templata: ITemplata,
//    ownershipIfMutable: Ownership):
//  (Kind) = {
//    templata match {
//      case CoordTemplata(Coord(_, kind)) => (kind)
//      case KindTemplata(kind) => (kind)
//      case st @ StructTemplata(_, _) => delegate.evaluateStructTemplata(state, st, List())
//      case it @ InterfaceTemplata(_, _) => delegate.evaluateInterfaceTemplata(state, it, List())
//      case _ => vfail("not yet")
//    }
//  }
//  def coerceTemplataToReference(
//    env: Env,
//    state: State,
//    templata: ITemplata,
//    ownershipIfMutable: Ownership):
//  (Coord) = {
//    templata match {
//      case CoordTemplata(reference) => (reference)
//      case KindTemplata(referend) => {
//        (pointifyReferend(state, referend, ownershipIfMutable))
//      }
//      case st @ StructTemplata(_, _) => {
//        val kind = delegate.evaluateStructTemplata(state, st, List())
//        (pointifyReferend(state, kind, ownershipIfMutable))
//      }
//      case it @ InterfaceTemplata(_, _) => {
//        val kind = delegate.evaluateInterfaceTemplata(state, it, List())
//        (pointifyReferend(state, kind, ownershipIfMutable))
//      }
//      case _ => vfail("not yet")
//    }
//  }

  def evaluateTemplex(
    env: Env,
    state: State,
    type1: ITemplexA):
  (ITemplata) = {
    vassert(type1.isInstanceOf[ITemplexA])
    type1 match {
      case NameAT(name, tyype) => {
        val thing = delegate.lookupTemplataImprecise(env, name)
        coerce(state, thing, tyype)
      }
      case RuneAT(rune, resultType) => {
        val thing = delegate.lookupTemplata(env, NameTranslator.translateRune(rune))
        coerce(state, thing, resultType)
      }
      case RepeaterSequenceAT(mutabilityTemplexS, sizeTemplexS, elementTemplexS, tyype) => {
        val (MutabilityTemplata(mutability)) = evaluateTemplex(env, state, mutabilityTemplexS)

        val (IntegerTemplata(size)) = evaluateTemplex(env, state, sizeTemplexS)

        val (CoordTemplata(elementType2)) = evaluateTemplex(env, state, elementTemplexS)

        val kind = KindTemplata(KnownSizeArrayT2(size, RawArrayT2(elementType2, mutability)))
        coerce(state, kind, tyype)
      }
      case OwnershippedAT(ownershipS, innerType1) => {
        val ownership = Conversions.evaluateOwnership(ownershipS)
        val (KindTemplata(innerKind)) = evaluateTemplex(env, state, innerType1)
        val mutability = delegate.getMutability(state, innerKind)
        vassert((mutability == Immutable) == (ownership == Share))
        (CoordTemplata(Coord(ownership, innerKind)))
      }
      case NullableAT(_) => {
        //        val innerValueType2 = evaluateTemplex(env, state, innerType1)
        //        val innerPointerType2 = TypeTemplar.pointify(innerValueType2)
        //        env.lookupType("Option") match {
        //          case TemplataStructTemplate(_) => {
        //            StructTemplar.getStructRef(env.globalEnv, state, "Option", List(TemplataType(innerPointerType2)))
        //          }
        //        }
        vfail("support unions kkthx")
      }
      case CallAT(templateTemplexS, templateArgTemplexesS, resultType) => {
        val templateTemplata = evaluateTemplex(env, state, templateTemplexS)
        val templateArgsTemplatas = evaluateTemplexes(env, state, templateArgTemplexesS)
        templateTemplata match {
          case st @ StructTemplata(_, _) => {
            val kind = delegate.evaluateStructTemplata(state, st, templateArgsTemplatas)
            coerce(state, KindTemplata(kind), resultType)
          }
          case it @ InterfaceTemplata(_, _) => {
            val kind = delegate.evaluateInterfaceTemplata(state, it, templateArgsTemplatas)
            coerce(state, KindTemplata(kind), resultType)
          }
          case ArrayTemplateTemplata() => {
            val List(MutabilityTemplata(mutability), CoordTemplata(elementCoord)) = templateArgsTemplatas
            val result = UnknownSizeArrayT2(RawArrayT2(elementCoord, mutability))
            coerce(state, KindTemplata(result), resultType)
          }
        }
      }
//      case PackAT(memberTypeTemplexesS, resultType) => {
//        val memberTemplatas = evaluateTemplexes(env, state, memberTypeTemplexesS)
//        vassert(memberTemplatas.forall(_.tyype == CoordTemplataType))
//        val memberCoords = memberTemplatas.map({ case CoordTemplata(c) => c })
//        val (packKind, _) = delegate.getPackKind(env, state, memberCoords)
//        coerce(state, KindTemplata(packKind), resultType)
//      }
      case x => {
        println(x)
        vfail("not yet " + x)
      }
    }
  }

  def evaluateTemplexes(
    env: Env,
    state: State,
    types1: List[ITemplexA]):
  (List[ITemplata]) = {
    types1 match {
      case Nil => (Nil)
      case head1 :: tail1 => {
        val head2 = evaluateTemplex(env, state, head1);
        val tail2 = evaluateTemplexes(env, state, tail1);
        (head2 :: tail2)
      }
    }
  }

  def isTypeConvertible(
    temputs: State,
    sourcePointerType: Coord,
    targetPointerType: Coord):
  (Boolean) = {
    val maybeDistance =
      getTypeDistance(temputs, sourcePointerType, targetPointerType)
    (maybeDistance.nonEmpty)
  }

  def getTypeDistance(
    temputs: State,
    sourcePointerType: Coord,
    targetPointerType: Coord):
  (Option[TypeDistance]) = {
    val Coord(targetOwnership, targetType) = targetPointerType;
    val Coord(sourceOwnership, sourceType) = sourcePointerType;

    if (sourceType == Never2()) {
      return (Some(TypeDistance(0, 0)))
    }

    val upcastDistance =
      if (sourceType == targetType) {
        (0)
      } else {
        (sourceType, targetType) match {
          case (Void2(), _) => return (None)
          case (Int2(), _) => return (None)
          case (Bool2(), _) => return (None)
          case (Str2(), _) => return (None)
          case (_, Void2()) => return (None)
          case (_, Int2()) => return (None)
          case (_, Bool2()) => return (None)
          case (_, Str2()) => return (None)
          case (_, StructRef2(_)) => return (None)
          case (a @ StructRef2(_), b @ InterfaceRef2(_)) => {
            delegate.getAncestorInterfaceDistance(temputs, a, b) match {
              case (None) => return (None)
              case (Some(distance)) => (distance)
            }
          }
          case (a @ InterfaceRef2(_), b @ InterfaceRef2(_)) => {
            delegate.getAncestorInterfaceDistance(temputs, a, b) match {
              case (None) => return (None)
              case (Some(distance)) => (distance)
            }
          }
          case (PackT2(List(), _), Void2()) => vfail("figure out void<->emptypack")
          case (Void2(), PackT2(List(), _)) => vfail("figure out void<->emptypack")
          case (PackT2(List(), _), _) => return (None)
          case (_, PackT2(List(), _)) => return (None)
          case (_ : CitizenRef2, Int2() | Bool2() | Str2() | Float2()) => return (None)
          case (Int2() | Bool2() | Str2() | Float2(), _ : CitizenRef2) => return (None)
          case _ => {
            vfail("Can't convert from " + sourceType + " to " + targetType)
          }
        }
      }

    val ownershipDistance =
      (sourceOwnership, targetOwnership) match {
        case (Own, Own) => 0
        case (Own, Borrow) => 1
        case (Own, Weak) => 1
        case (Own, Share) => return None
        case (Borrow, Own) => return (None)
        case (Borrow, Borrow) => 0
        case (Borrow, Weak) => 1
        case (Borrow, Share) => return None
        case (Weak, Own) => return None
        case (Weak, Borrow) => return None
        case (Weak, Weak) => 0
        case (Weak, Share) => return None
        case (Share, Share) => 0
        case (Share, Borrow) => return None
        case (Share, Weak) => return None
        case (Share, Own) => return None
      }

    (Some(TypeDistance(upcastDistance, ownershipDistance)))
  }

  def isTypeTriviallyConvertible(
    temputs: State,
    sourcePointerType: Coord,
    targetPointerType: Coord):
  (Boolean) = {
    val Coord(targetOwnership, targetType) = targetPointerType;
    val Coord(sourceOwnership, sourceType) = sourcePointerType;

    if (sourceType == Never2()) {
      return (true)
    }

      if (sourceType == targetType) {

      } else {
        (sourceType, targetType) match {
          case (Void2(), _) => return (false)
          case (Int2(), _) => return (false)
          case (Bool2(), _) => return (false)
          case (Str2(), _) => return (false)
          case (_, Void2()) => return (false)
          case (_, Int2()) => return (false)
          case (_, Bool2()) => return (false)
          case (_, Str2()) => return (false)
          case (_, StructRef2(_)) => return (false)
          case (a @ StructRef2(_), b @ InterfaceRef2(_)) => {
            delegate.getAncestorInterfaceDistance(temputs, a, b) match {
              case (None) => return (false)
              case (Some(_)) =>
            }
          }
          case (a @ InterfaceRef2(_), b @ InterfaceRef2(_)) => {
            delegate.getAncestorInterfaceDistance(temputs, a, b) match {
              case (None) => return (false)
              case (Some(_)) =>
            }
          }
          case (PackT2(List(), _), Void2()) => vfail("figure out void<->emptypack")
          case (Void2(), PackT2(List(), _)) => vfail("figure out void<->emptypack")
          case (PackT2(List(), _), _) => return (false)
          case (_, PackT2(List(), _)) => return (false)
          case (_ : CitizenRef2, Int2() | Bool2() | Str2() | Float2()) => return (false)
          case (Int2() | Bool2() | Str2() | Float2(), _ : CitizenRef2) => return (false)
          case _ => {
            vfail("Can't convert from " + sourceType + " to " + targetType)
          }
        }
      }

    (sourceOwnership, targetOwnership) match {
      case (Own, Own) =>
      case (Borrow, Own) => return (false)
      case (Own, Borrow) => return (false)
      case (Borrow, Borrow) =>
      case (Share, Share) =>
    }

    (true)
  }

  def pointifyReferend(state: State, referend: Kind, ownershipIfMutable: Ownership): Coord = {
    referend match {
      case a @ UnknownSizeArrayT2(array) => {
        val ownership = if (array.mutability == Mutable) ownershipIfMutable else Share
        Coord(ownership, a)
      }
      case a @ KnownSizeArrayT2(_, RawArrayT2(_, mutability)) => {
        val ownership = if (mutability == Mutable) ownershipIfMutable else Share
        Coord(ownership, a)
      }
      case a @ PackT2(_, underlyingStruct) => {
        val ownership = if (delegate.getMutability(state, underlyingStruct) == Mutable) ownershipIfMutable else Share
        Coord(ownership, a)
      }
      case s @ StructRef2(_) => {
        val ownership = if (delegate.getMutability(state, s) == Mutable) ownershipIfMutable else Share
        Coord(ownership, s)
      }
      case i @ InterfaceRef2(_) => {
        val ownership = if (delegate.getMutability(state, i) == Mutable) ownershipIfMutable else Share
        Coord(ownership, i)
      }
      case Void2() => {
        Coord(Share, Void2())
      }
      case Int2() => {
        Coord(Share, Int2())
      }
      case Float2() => {
        Coord(Share, Float2())
      }
      case Bool2() => {
        Coord(Share, Bool2())
      }
      case Str2() => {
        Coord(Share, Str2())
      }
    }
  }

  def pointifyReferends(state: State, valueTypes: List[Kind], ownershipIfMutable: Ownership): List[Coord] = {
    valueTypes.map(valueType => pointifyReferend(state, valueType, ownershipIfMutable))
  }


  def evaluateStructTemplata(
    state: State,
    template: StructTemplata,
    templateArgs: List[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.evaluateStructTemplata(state, template, templateArgs)
    val templata =
      coerce(state, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def evaluateInterfaceTemplata(
    state: State,
    template: InterfaceTemplata,
    templateArgs: List[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.evaluateInterfaceTemplata(state, template, templateArgs)
    val templata =
      coerce(state, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def evaluateBuiltinTemplateTemplata(
    state: State,
    template: ArrayTemplateTemplata,
    templateArgs: List[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val List(MutabilityTemplata(mutability), CoordTemplata(elementType)) = templateArgs
    val arrayKindTemplata = KindTemplata(UnknownSizeArrayT2(RawArrayT2(elementType, mutability)))
    val templata =
      coerce(state, arrayKindTemplata, expectedType)
    (templata)
  }

  def getPackKind(
    env: Env,
    state: State,
    members: List[Coord],
    expectedType: ITemplataType):
  (ITemplata) = {
    val (uncoercedTemplata, _) =
      delegate.getPackKind(env, state, members)
    val templata =
      coerce(state, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def getArraySequenceKind(
    env: Env,
    state: State,
    mutability: Mutability,
    size: Int,
    element: Coord,
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.getArraySequenceKind(env, state, mutability, size, element)
    val templata =
      coerce(state, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def lookupTemplata(
    env: Env,
    state: State,
    name: IName2,
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata = delegate.lookupTemplata(env, name)
    coerce(state, uncoercedTemplata, expectedType)
  }

  def lookupTemplata(
    env: Env,
    state: State,
    name: IImpreciseNameStepA,
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata = delegate.lookupTemplataImprecise(env, name)
    coerce(state, uncoercedTemplata, expectedType)
  }

  def coerce(
    state: State,
    templata: ITemplata,
    tyype: ITemplataType):
  (ITemplata) = {
    // debt: find a way to simplify this function, seems simplifiable
    (templata, tyype) match {
      case (MutabilityTemplata(_), MutabilityTemplataType) => {
        (templata)
      }
      case (KindTemplata(kind), CoordTemplataType) => {
        val mutability = delegate.getMutability(state, kind)
        val coerced =
          CoordTemplata(
            Coord(
              if (mutability == Mutable) Own else Share,
              kind))
        (coerced)
      }
      case (KindTemplata(_), KindTemplataType) => (templata)
      case (CoordTemplata(_), CoordTemplataType) => (templata)
      case (st @ StructTemplata(_, structA), KindTemplataType) => {
        if (structA.isTemplate) {
          vfail("Can't coerce " + structA.name + " to be a kind, is a template!")
        }
        val kind =
          delegate.evaluateStructTemplata(state, st, List())
        (KindTemplata(kind))
      }
      case (it @ InterfaceTemplata(_, interfaceA), KindTemplataType) => {
        if (interfaceA.isTemplate) {
          vfail("Can't coerce " + interfaceA.name + " to be a kind, is a template!")
        }
        val kind =
          delegate.evaluateInterfaceTemplata(state, it, List())
        (KindTemplata(kind))
      }
      case (st @ StructTemplata(_, structA), ttt @ TemplateTemplataType(_, _)) => {
        vassert(structA.isTemplate)
        vassert(delegate.getStructTemplataType(st) == ttt)
        (st)
      }
      case (st @ StructTemplata(_, structA), CoordTemplataType) => {
        if (structA.isTemplate) {
          vfail("Can't coerce " + structA.name + " to be a coord, is a template!")
        }
        val kind =
          delegate.evaluateStructTemplata(state, st, List())
        val mutability = delegate.getMutability(state, kind)
        val coerced =
          CoordTemplata(Coord(if (mutability == Mutable) Own else Share, kind))
        (coerced)
      }
      case (it @ InterfaceTemplata(_, interfaceA), CoordTemplataType) => {
        if (interfaceA.isTemplate) {
          vfail("Can't coerce " + interfaceA.name + " to be a coord, is a template!")
        }
        val kind =
          delegate.evaluateInterfaceTemplata(state, it, List())
        val mutability = delegate.getMutability(state, kind)
        val coerced =
          CoordTemplata(Coord(if (mutability == Mutable) Own else Share, kind))
        (coerced)
      }
      case (it @ InterfaceTemplata(_, interfaceA), ttt @ TemplateTemplataType(_, _)) => {
        vassert(interfaceA.isTemplate)
        vassert(delegate.getInterfaceTemplataType(it) == ttt)
        (it)
      }
      case (btt @ ArrayTemplateTemplata(), ttt @ TemplateTemplataType(_, _)) => {
        vassert(btt.tyype == ttt)
        (btt)
      }
      case _ => vimpl((templata, tyype).toString())
    }
  }
}
