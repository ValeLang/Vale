package net.verdagon.vale.templar.templata

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.ShareP
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.{AnonymousSubstructName2, CitizenName2, IName2, LambdaCitizenName2, NameTranslator, TupleName2}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vfail, vimpl, vwat}

import scala.collection.immutable.List

// Order of these members matters for comparison
case class TypeDistance(upcastDistance: Int, ownershipDistance: Int, permissionDistance: Int) {
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

trait ITemplataTemplarInnerDelegate[Env, State] {
  def lookupTemplata(env: Env, range: RangeS, name: IName2): ITemplata
  def lookupTemplataImprecise(env: Env, range: RangeS, name: IImpreciseNameStepA): ITemplata

  def getMutability(state: State, kind: Kind): Mutability

//  def getPackKind(env: Env, temputs: State, types2: List[Coord]): (PackT2, Mutability)

  def evaluateStructTemplata(
    state: State,
    callRange: RangeS,
    templata: StructTemplata,
    templateArgs: List[ITemplata]):
  (Kind)

  def evaluateInterfaceTemplata(
    state: State,
    callRange: RangeS,
    templata: InterfaceTemplata,
    templateArgs: List[ITemplata]):
  (Kind)

  def getAncestorInterfaceDistance(
    temputs: State,
    descendantCitizenRef: CitizenRef2,
    ancestorInterfaceRef: InterfaceRef2):
  (Option[Int])

  def getStaticSizedArrayKind(
    env: Env,
    state: State,
    mutability: Mutability,
    variability: Variability,
    size: Int,
    element: Coord):
  (StaticSizedArrayT2)

  def getRuntimeSizedArrayKind(
    env: Env,
    state: State,
    type2: Coord,
    arrayMutability: Mutability,
    arrayVariability: Variability):
  RuntimeSizedArrayT2

  def getTupleKind(
    env: Env,
    state: State,
    elements: List[Coord]):
  (TupleT2)

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
      case NameAT(range, name, tyype) => {
        val thing = delegate.lookupTemplataImprecise(env, range, name)
        coerce(state, range, thing, tyype)
      }
      case RuneAT(range, rune, resultType) => {
        val thing = delegate.lookupTemplata(env, range, NameTranslator.translateRune(rune))
        coerce(state, range, thing, resultType)
      }
      case RepeaterSequenceAT(range, mutabilityTemplexS, variabilityTemplexS, sizeTemplexS, elementTemplexS, tyype) => {
        val MutabilityTemplata(mutability) = evaluateTemplex(env, state, mutabilityTemplexS)
        val VariabilityTemplata(variability) = evaluateTemplex(env, state, variabilityTemplexS)
        val IntegerTemplata(size) = evaluateTemplex(env, state, sizeTemplexS)

        val CoordTemplata(elementType2) = evaluateTemplex(env, state, elementTemplexS)

        val kind = KindTemplata(delegate.getStaticSizedArrayKind(env, state, mutability, variability, size, elementType2))
        coerce(state, range, kind, tyype)
      }
      case InterpretedAT(range, ownershipS, permissionS, innerType1) => {
        val ownership = Conversions.evaluateOwnership(ownershipS)
        val permission = Conversions.evaluatePermission(permissionS)
        val (KindTemplata(innerKind)) = evaluateTemplex(env, state, innerType1)
        val mutability = delegate.getMutability(state, innerKind)
        vassert((mutability == Immutable) == (ownership == Share))
        (CoordTemplata(Coord(ownership, permission, innerKind)))
      }
      case NullableAT(range, _) => {
        //        val innerValueType2 = evaluateTemplex(env, state, innerType1)
        //        val innerPointerType2 = ConvertHelper.pointify(innerValueType2)
        //        env.lookupType("Option") match {
        //          case TemplataStructTemplate(_) => {
        //            StructTemplar.getStructRef(env.globalEnv, state, "Option", List(TemplataType(innerPointerType2)))
        //          }
        //        }
        vfail("support unions kkthx")
      }
      case CallAT(range, templateTemplexS, templateArgTemplexesS, resultType) => {
        val templateTemplata = evaluateTemplex(env, state, templateTemplexS)
        val templateArgsTemplatas = evaluateTemplexes(env, state, templateArgTemplexesS)
        templateTemplata match {
          case st @ StructTemplata(_, _) => {
            val kind = delegate.evaluateStructTemplata(state, range, st, templateArgsTemplatas)
            coerce(state, range, KindTemplata(kind), resultType)
          }
          case it @ InterfaceTemplata(_, _) => {
            val kind = delegate.evaluateInterfaceTemplata(state, range, it, templateArgsTemplatas)
            coerce(state, range, KindTemplata(kind), resultType)
          }
          case ArrayTemplateTemplata() => {
            val List(MutabilityTemplata(mutability), VariabilityTemplata(variability), CoordTemplata(elementCoord)) = templateArgsTemplatas
            val result = RuntimeSizedArrayT2(RawArrayT2(elementCoord, mutability, variability))
            vimpl() // we should be calling into arraytemplar for that ^
            coerce(state, range, KindTemplata(result), resultType)
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
    val Coord(targetOwnership, targetPermission, targetType) = targetPointerType;
    val Coord(sourceOwnership, sourcePermission, sourceType) = sourcePointerType;

    if (sourceType == Never2()) {
      return (Some(TypeDistance(0, 0, 0)))
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
        case (Own, Constraint) => 1
        case (Own, Weak) => 1
        case (Own, Share) => return None
        case (Constraint, Own) => return (None)
        case (Constraint, Constraint) => 0
        case (Constraint, Weak) => 1
        case (Constraint, Share) => return None
        case (Weak, Own) => return None
        case (Weak, Constraint) => return None
        case (Weak, Weak) => 0
        case (Weak, Share) => return None
        case (Share, Share) => 0
        case (Share, Constraint) => return None
        case (Share, Weak) => return None
        case (Share, Own) => return None
      }

    val permissionDistance =
      (sourcePermission, targetPermission) match {
        case (Readonly, Readonly) => 0
        case (Readonly, Readwrite) => return None
        // Could eventually make this 1 instead of None, if we want to implicitly
        // go from readwrite to readonly, that would be nice.
        case (Readwrite, Readonly) => return None
        case (Readwrite, Readwrite) => 0
//        case (Readonly, ExclusiveReadwrite) => 1
//        case (Readwrite, ExclusiveReadwrite) => 1
//        case (ExclusiveReadwrite, Readonly) => 1
//        case (ExclusiveReadwrite, Readonly) => 1
//        case (ExclusiveReadwrite, ExclusiveReadwrite) => 0
      }

    (Some(TypeDistance(upcastDistance, ownershipDistance, permissionDistance)))
  }

  def isTypeTriviallyConvertible(
    temputs: State,
    sourcePointerType: Coord,
    targetPointerType: Coord):
  (Boolean) = {
    val Coord(targetOwnership, targetPermission, targetType) = targetPointerType;
    val Coord(sourceOwnership, sourcePermission, sourceType) = sourcePointerType;

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

    if (sourceOwnership != targetOwnership) {
      return false
    }

    if (sourcePermission != targetPermission) {
      return false
    }

    true
  }

  def pointifyReferend(state: State, referend: Kind, ownershipIfMutable: Ownership): Coord = {
    val mutability = delegate.getMutability(state, referend)
    val ownership = if (mutability == Mutable) ownershipIfMutable else Share
    val permission = if (mutability == Mutable) Readwrite else Readonly
    referend match {
      case a @ RuntimeSizedArrayT2(array) => {
        Coord(ownership, permission, a)
      }
      case a @ StaticSizedArrayT2(_, RawArrayT2(_, mutability, variability)) => {
        Coord(ownership, permission, a)
      }
      case a @ PackT2(_, underlyingStruct) => {
        Coord(ownership, permission, a)
      }
      case s @ StructRef2(_) => {
        Coord(ownership, permission, s)
      }
      case i @ InterfaceRef2(_) => {
        Coord(ownership, permission, i)
      }
      case Void2() => {
        Coord(Share, Readonly, Void2())
      }
      case Int2() => {
        Coord(Share, Readonly, Int2())
      }
      case Float2() => {
        Coord(Share, Readonly, Float2())
      }
      case Bool2() => {
        Coord(Share, Readonly, Bool2())
      }
      case Str2() => {
        Coord(Share, Readonly, Str2())
      }
    }
  }

//  def pointifyReferends(state: State, valueTypes: List[Kind], ownershipIfMutable: Ownership): List[Coord] = {
//    valueTypes.map(valueType => pointifyReferend(state, valueType, ownershipIfMutable))
//  }


  def evaluateStructTemplata(
    state: State,
    callRange: RangeS,
    template: StructTemplata,
    templateArgs: List[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.evaluateStructTemplata(state, callRange, template, templateArgs)
    val templata =
      coerce(state, callRange, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def evaluateInterfaceTemplata(
    state: State,
    callRange: RangeS,
    template: InterfaceTemplata,
    templateArgs: List[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.evaluateInterfaceTemplata(state, callRange, template, templateArgs)
    val templata =
      coerce(state, callRange, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def evaluateBuiltinTemplateTemplata(
    env: Env,
    state: State,
    range: RangeS,
    template: ArrayTemplateTemplata,
    templateArgs: List[ITemplata],
    expectedType: ITemplataType):
  (ITemplata) = {
    val List(MutabilityTemplata(mutability), VariabilityTemplata(variability), CoordTemplata(elementType)) = templateArgs
    val arrayKindTemplata = delegate.getRuntimeSizedArrayKind(env, state, elementType, mutability, variability)
    val templata =
      coerce(state, range, KindTemplata(arrayKindTemplata), expectedType)
    (templata)
  }

//  def getPackKind(
//    env: Env,
//    state: State,
//    members: List[Coord],
//    expectedType: ITemplataType):
//  (ITemplata) = {
//    val (uncoercedTemplata, _) =
//      delegate.getPackKind(env, state, members)
//    val templata =
//      coerce(state, KindTemplata(uncoercedTemplata), expectedType)
//    (templata)
//  }

  def getStaticSizedArrayKind(
    env: Env,
    state: State,
    callRange: RangeS,
    mutability: Mutability,
    variability: Variability,
    size: Int,
    element: Coord,
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.getStaticSizedArrayKind(env, state, mutability, variability, size, element)
    val templata =
      coerce(state, callRange, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def getTupleKind(
    env: Env,
    state: State,
    callRange: RangeS,
    elements: List[Coord],
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata =
      delegate.getTupleKind(env, state, elements)
    val templata =
      coerce(state, callRange, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

  def lookupTemplata(
    env: Env,
    state: State,
    range: RangeS,
    name: IName2,
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata = delegate.lookupTemplata(env, range, name)
    coerce(state, range, uncoercedTemplata, expectedType)
  }

  def lookupTemplata(
    env: Env,
    state: State,
    range: RangeS,
    name: IImpreciseNameStepA,
    expectedType: ITemplataType):
  (ITemplata) = {
    val uncoercedTemplata = delegate.lookupTemplataImprecise(env, range, name)
    coerce(state, range, uncoercedTemplata, expectedType)
  }

  def coerce(
    state: State,
    range: RangeS,
    templata: ITemplata,
    tyype: ITemplataType):
  (ITemplata) = {
    // debt: find a way to simplify this function, seems simplifiable
    (templata, tyype) match {
      case (MutabilityTemplata(_), MutabilityTemplataType) => {
        (templata)
      }
      case (PrototypeTemplata(_), PrototypeTemplataType) => {
        (templata)
      }
      case (KindTemplata(kind), CoordTemplataType) => {
        val mutability = delegate.getMutability(state, kind)
        val coerced =
          CoordTemplata(
            Coord(
              if (mutability == Mutable) Own else Share,
              if (mutability == Mutable) Readwrite else Readonly,
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
          delegate.evaluateStructTemplata(state, range, st, List())
        (KindTemplata(kind))
      }
      case (it @ InterfaceTemplata(_, interfaceA), KindTemplataType) => {
        if (interfaceA.isTemplate) {
          vfail("Can't coerce " + interfaceA.name + " to be a kind, is a template!")
        }
        val kind =
          delegate.evaluateInterfaceTemplata(state, range, it, List())
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
          delegate.evaluateStructTemplata(state, range, st, List())
        val mutability = delegate.getMutability(state, kind)

        // Default ownership is own for mutables, share for imms
        val ownership = if (mutability == Mutable) Own else Share
        // Default permission is readwrite for mutables, readonly for imms
        val permission = if (mutability == Mutable) Readwrite else Readonly
        val coerced = CoordTemplata(Coord(ownership, permission, kind))
        (coerced)
      }
      case (it @ InterfaceTemplata(_, interfaceA), CoordTemplataType) => {
        if (interfaceA.isTemplate) {
          vfail("Can't coerce " + interfaceA.name + " to be a coord, is a template!")
        }
        val kind =
          delegate.evaluateInterfaceTemplata(state, range, it, List())
        val mutability = delegate.getMutability(state, kind)
        val coerced =
          CoordTemplata(
            Coord(
              if (mutability == Mutable) Own else Share,
              if (mutability == Mutable) Readwrite else Readonly,
              kind))
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

  // This is useful for checking if something (a) is equal to something that came from a name (b).
  def uncoercedTemplataEquals(env: Env, state: State, a: ITemplata, b: ITemplata, bExpectedType: ITemplataType): Boolean = {
    // When we consider a new templata, add it to one of these two following matches, then be VERY careful and make sure
    // to consider it against every other type in the other match.
    a match {
      case KindTemplata(_) =>
      case CoordTemplata(_) =>
      case ArrayTemplateTemplata() =>
      case _ => vimpl()
    }
    b match {
      case KindTemplata(_) =>
      case CoordTemplata(_) =>
      case StructTemplata(_, _) =>
      case InterfaceTemplata(_, _) =>
      case ArrayTemplateTemplata() =>
      case _ => vimpl()
    }

    (a, b) match {
      case (KindTemplata(_) | CoordTemplata(_), KindTemplata(_) | CoordTemplata(_)) => {
        a == coerce(state, RangeS.internal(-1345), b, bExpectedType)
      }
      case (KindTemplata(actualStructRef @ StructRef2(_)), expectedStructTemplata @ StructTemplata(_, _)) => {
        vassert(bExpectedType == KindTemplataType)
        citizenMatchesTemplata(actualStructRef, expectedStructTemplata, List())
      }
      case (CoordTemplata(Coord(Share | Own, actualPermission, actualStructRef @ StructRef2(_))), expectedStructTemplata @ StructTemplata(_, _)) => {
        vassert(bExpectedType == CoordTemplataType)
        val mutability = delegate.getMutability(state, actualStructRef)
        val expectedPermission = if (mutability == Mutable) Readwrite else Readonly
        val permissionMatches = expectedPermission == actualPermission
        permissionMatches && citizenMatchesTemplata(actualStructRef, expectedStructTemplata, List())
      }
      case (KindTemplata(actualInterfaceRef @ InterfaceRef2(_)), expectedInterfaceTemplata @ InterfaceTemplata(_, _)) => {
        vassert(bExpectedType == KindTemplataType)
        citizenMatchesTemplata(actualInterfaceRef, expectedInterfaceTemplata, List())
      }
      case (CoordTemplata(Coord(Share | Own, actualPermission, actualInterfaceRef @ InterfaceRef2(_))), expectedInterfaceTemplata @ InterfaceTemplata(_, _)) => {
        vassert(bExpectedType == CoordTemplataType)
        val mutability = delegate.getMutability(state, actualInterfaceRef)
        val expectedPermission = if (mutability == Mutable) Readwrite else Readonly
        val permissionMatches = expectedPermission == actualPermission
        permissionMatches && citizenMatchesTemplata(actualInterfaceRef, expectedInterfaceTemplata, List())
      }
      case (ArrayTemplateTemplata(), ArrayTemplateTemplata()) => true
      case (KindTemplata(RuntimeSizedArrayT2(_)), ArrayTemplateTemplata()) => true
      case (CoordTemplata(Coord(Share | Own, Readonly, RuntimeSizedArrayT2(_))), ArrayTemplateTemplata()) => true
      case (ArrayTemplateTemplata(), ArrayTemplateTemplata()) => true
      case (ArrayTemplateTemplata(), KindTemplata(RuntimeSizedArrayT2(_))) => true
      case (ArrayTemplateTemplata(), CoordTemplata(Coord(Share | Own, Readonly, RuntimeSizedArrayT2(_)))) => true
      case _ => false
    }
  }

  def citizenIsFromTemplate(actualCitizenRef: CitizenRef2, expectedCitizenTemplata: ITemplata): Boolean = {
    val (citizenTemplateNameInitSteps, citizenTemplateName) =
      expectedCitizenTemplata match {
        case StructTemplata(env, originStruct) => (env.fullName.steps, originStruct.name.name)
        case InterfaceTemplata(env, originInterface) => (env.fullName.steps, originInterface.name.name)
        case KindTemplata(expectedKind) => return actualCitizenRef == expectedKind
        case CoordTemplata(Coord(Own | Share, Readonly, actualKind)) => return actualCitizenRef == actualKind
        case _ => return false
      }
    if (actualCitizenRef.fullName.initSteps != citizenTemplateNameInitSteps) {
      // Packages dont match, bail
      return false
    }
    actualCitizenRef.fullName.last match {
      case CitizenName2(humanName, templateArgs) => {
        if (humanName != citizenTemplateName) {
          // Names dont match, bail
          return false
        }
      }
      case TupleName2(_) => return false
      case _ => vwat()
    }
    return true
  }

  def citizenMatchesTemplata(actualCitizenRef: CitizenRef2, expectedCitizenTemplata: ITemplata, expectedCitizenTemplateArgs: List[ITemplata]): (Boolean) = {
    vassert(expectedCitizenTemplateArgs.isEmpty) // implement

    if (!citizenIsFromTemplate(actualCitizenRef, expectedCitizenTemplata)) {
      return false
    }

    if (actualCitizenRef.fullName.last.templateArgs.nonEmpty) {
      // This function doesnt support template args yet (hence above assert)
      // If the actualCitizenRef has template args, it sure doesnt match the template when its made with no args.
      return false
    }

    return true
  }

}
