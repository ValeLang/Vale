package net.verdagon.vale.templar.templata

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.ShareP
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.{AnonymousSubstructNameT, CitizenNameT, INameT, LambdaCitizenNameT, NameTranslator, TupleNameT}
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
  def lookupTemplata(env: Env, range: RangeS, name: INameT): ITemplata
  def lookupTemplataImprecise(env: Env, range: RangeS, name: IImpreciseNameStepA): ITemplata

  def getMutability(state: State, kind: KindT): MutabilityT

//  def getPackKind(env: Env, temputs: State, types2: List[Coord]): (PackT2, Mutability)

  def evaluateStructTemplata(
    state: State,
    callRange: RangeS,
    templata: StructTemplata,
    templateArgs: List[ITemplata]):
  (KindT)

  def evaluateInterfaceTemplata(
    state: State,
    callRange: RangeS,
    templata: InterfaceTemplata,
    templateArgs: List[ITemplata]):
  (KindT)

  def getAncestorInterfaceDistance(
    temputs: State,
    descendantCitizenRef: CitizenRefT,
    ancestorInterfaceRef: InterfaceRefT):
  (Option[Int])

  def getStaticSizedArrayKind(
    env: Env,
    state: State,
    mutability: MutabilityT,
    variability: VariabilityT,
    size: Int,
    element: CoordT):
  (StaticSizedArrayTT)

  def getRuntimeSizedArrayKind(
    env: Env,
    state: State,
    type2: CoordT,
    arrayMutability: MutabilityT,
    arrayVariability: VariabilityT):
  RuntimeSizedArrayTT

  def getTupleKind(
    env: Env,
    state: State,
    elements: List[CoordT]):
  (TupleTT)

  def getInterfaceTemplataType(it: InterfaceTemplata): ITemplataType
  def getStructTemplataType(st: StructTemplata): ITemplataType
}

class TemplataTemplarInner[Env, State](delegate: ITemplataTemplarInnerDelegate[Env, State]) {

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

        val kind = KindTemplata(delegate.getStaticSizedArrayKind(env, state, mutability, variability, size.toInt, elementType2))
        coerce(state, range, kind, tyype)
      }
      case InterpretedAT(range, ownershipS, permissionS, innerType1) => {
        val ownership = Conversions.evaluateOwnership(ownershipS)
        val permission = Conversions.evaluatePermission(permissionS)
        val (KindTemplata(innerKind)) = evaluateTemplex(env, state, innerType1)
        val mutability = delegate.getMutability(state, innerKind)
        vassert((mutability == ImmutableT) == (ownership == ShareT))
        (CoordTemplata(CoordT(ownership, permission, innerKind)))
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
            val result = RuntimeSizedArrayTT(RawArrayTT(elementCoord, mutability, variability))
            vimpl() // we should be calling into arraytemplar for that ^
            coerce(state, range, KindTemplata(result), resultType)
          }
        }
      }
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
    sourcePointerType: CoordT,
    targetPointerType: CoordT):
  (Boolean) = {
    val maybeDistance =
      getTypeDistance(temputs, sourcePointerType, targetPointerType)
    (maybeDistance.nonEmpty)
  }

  def getTypeDistance(
    temputs: State,
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
          case (_, StructRefT(_)) => return (None)
          case (a @ StructRefT(_), b @ InterfaceRefT(_)) => {
            delegate.getAncestorInterfaceDistance(temputs, a, b) match {
              case (None) => return (None)
              case (Some(distance)) => (distance)
            }
          }
          case (a @ InterfaceRefT(_), b @ InterfaceRefT(_)) => {
            delegate.getAncestorInterfaceDistance(temputs, a, b) match {
              case (None) => return (None)
              case (Some(distance)) => (distance)
            }
          }
          case (PackTT(List(), _), VoidT()) => vfail("figure out void<->emptypack")
          case (VoidT(), PackTT(List(), _)) => vfail("figure out void<->emptypack")
          case (PackTT(List(), _), _) => return (None)
          case (_, PackTT(List(), _)) => return (None)
          case (_ : CitizenRefT, IntT(_) | BoolT() | StrT() | FloatT()) => return (None)
          case (IntT(_) | BoolT() | StrT() | FloatT(), _ : CitizenRefT) => return (None)
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
        case (_, StructRefT(_)) => return (false)
        case (a @ StructRefT(_), b @ InterfaceRefT(_)) => {
          delegate.getAncestorInterfaceDistance(temputs, a, b) match {
            case (None) => return (false)
            case (Some(_)) =>
          }
        }
        case (a @ InterfaceRefT(_), b @ InterfaceRefT(_)) => {
          delegate.getAncestorInterfaceDistance(temputs, a, b) match {
            case (None) => return (false)
            case (Some(_)) =>
          }
        }
        case (PackTT(List(), _), VoidT()) => vfail("figure out void<->emptypack")
        case (VoidT(), PackTT(List(), _)) => vfail("figure out void<->emptypack")
        case (PackTT(List(), _), _) => return (false)
        case (_, PackTT(List(), _)) => return (false)
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

  def pointifyKind(state: State, kind: KindT, ownershipIfMutable: OwnershipT): CoordT = {
    val mutability = delegate.getMutability(state, kind)
    val ownership = if (mutability == MutableT) ownershipIfMutable else ShareT
    val permission = if (mutability == MutableT) ReadwriteT else ReadonlyT
    kind match {
      case a @ RuntimeSizedArrayTT(array) => {
        CoordT(ownership, permission, a)
      }
      case a @ StaticSizedArrayTT(_, RawArrayTT(_, mutability, variability)) => {
        CoordT(ownership, permission, a)
      }
      case a @ PackTT(_, underlyingStruct) => {
        CoordT(ownership, permission, a)
      }
      case s @ StructRefT(_) => {
        CoordT(ownership, permission, s)
      }
      case i @ InterfaceRefT(_) => {
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

//  def pointifyKinds(state: State, valueTypes: List[Kind], ownershipIfMutable: Ownership): List[Coord] = {
//    valueTypes.map(valueType => pointifyKind(state, valueType, ownershipIfMutable))
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
    mutability: MutabilityT,
    variability: VariabilityT,
    size: Int,
    element: CoordT,
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
    elements: List[CoordT],
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
    name: INameT,
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
            CoordT(
              if (mutability == MutableT) OwnT else ShareT,
              if (mutability == MutableT) ReadwriteT else ReadonlyT,
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
        val ownership = if (mutability == MutableT) OwnT else ShareT
        // Default permission is readwrite for mutables, readonly for imms
        val permission = if (mutability == MutableT) ReadwriteT else ReadonlyT
        val coerced = CoordTemplata(CoordT(ownership, permission, kind))
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
            CoordT(
              if (mutability == MutableT) OwnT else ShareT,
              if (mutability == MutableT) ReadwriteT else ReadonlyT,
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
      case (KindTemplata(actualStructRef @ StructRefT(_)), expectedStructTemplata @ StructTemplata(_, _)) => {
        vassert(bExpectedType == KindTemplataType)
        citizenMatchesTemplata(actualStructRef, expectedStructTemplata, List())
      }
      case (CoordTemplata(CoordT(ShareT | OwnT, actualPermission, actualStructRef @ StructRefT(_))), expectedStructTemplata @ StructTemplata(_, _)) => {
        vassert(bExpectedType == CoordTemplataType)
        val mutability = delegate.getMutability(state, actualStructRef)
        val expectedPermission = if (mutability == MutableT) ReadwriteT else ReadonlyT
        val permissionMatches = expectedPermission == actualPermission
        permissionMatches && citizenMatchesTemplata(actualStructRef, expectedStructTemplata, List())
      }
      case (KindTemplata(actualInterfaceRef @ InterfaceRefT(_)), expectedInterfaceTemplata @ InterfaceTemplata(_, _)) => {
        vassert(bExpectedType == KindTemplataType)
        citizenMatchesTemplata(actualInterfaceRef, expectedInterfaceTemplata, List())
      }
      case (CoordTemplata(CoordT(ShareT | OwnT, actualPermission, actualInterfaceRef @ InterfaceRefT(_))), expectedInterfaceTemplata @ InterfaceTemplata(_, _)) => {
        vassert(bExpectedType == CoordTemplataType)
        val mutability = delegate.getMutability(state, actualInterfaceRef)
        val expectedPermission = if (mutability == MutableT) ReadwriteT else ReadonlyT
        val permissionMatches = expectedPermission == actualPermission
        permissionMatches && citizenMatchesTemplata(actualInterfaceRef, expectedInterfaceTemplata, List())
      }
      case (ArrayTemplateTemplata(), ArrayTemplateTemplata()) => true
      case (KindTemplata(RuntimeSizedArrayTT(_)), ArrayTemplateTemplata()) => true
      case (CoordTemplata(CoordT(ShareT | OwnT, ReadonlyT, RuntimeSizedArrayTT(_))), ArrayTemplateTemplata()) => true
      case (ArrayTemplateTemplata(), ArrayTemplateTemplata()) => true
      case (ArrayTemplateTemplata(), KindTemplata(RuntimeSizedArrayTT(_))) => true
      case (ArrayTemplateTemplata(), CoordTemplata(CoordT(ShareT | OwnT, ReadonlyT, RuntimeSizedArrayTT(_)))) => true
      case _ => false
    }
  }

  def citizenIsFromTemplate(actualCitizenRef: CitizenRefT, expectedCitizenTemplata: ITemplata): Boolean = {
    val (citizenTemplateNameInitSteps, citizenTemplateName) =
      expectedCitizenTemplata match {
        case StructTemplata(env, originStruct) => (env.fullName.steps, originStruct.name.name)
        case InterfaceTemplata(env, originInterface) => (env.fullName.steps, originInterface.name.name)
        case KindTemplata(expectedKind) => return actualCitizenRef == expectedKind
        case CoordTemplata(CoordT(OwnT | ShareT, ReadonlyT, actualKind)) => return actualCitizenRef == actualKind
        case _ => return false
      }
    if (actualCitizenRef.fullName.initSteps != citizenTemplateNameInitSteps) {
      // Packages dont match, bail
      return false
    }
    actualCitizenRef.fullName.last match {
      case CitizenNameT(humanName, templateArgs) => {
        if (humanName != citizenTemplateName) {
          // Names dont match, bail
          return false
        }
      }
      case TupleNameT(_) => return false
      case _ => vwat()
    }
    return true
  }

  def citizenMatchesTemplata(actualCitizenRef: CitizenRefT, expectedCitizenTemplata: ITemplata, expectedCitizenTemplateArgs: List[ITemplata]): (Boolean) = {
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
