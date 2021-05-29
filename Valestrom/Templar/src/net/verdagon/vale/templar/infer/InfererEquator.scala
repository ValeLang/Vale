package net.verdagon.vale.templar.infer

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vfail

import scala.collection.immutable.List

class InfererEquator[Env, State](
    templataTemplarInner: TemplataTemplarInner[Env, State]) {

  private[infer] def templatasEqual(
    state: State,
    range: RangeS,
    left: ITemplata,
    right: ITemplata,
    expectedType: ITemplataType):
  (Boolean) = {
    (left, right) match {
      case (KindTemplata(leftKind), rightStructTemplata @ StructTemplata(_, _)) => {
        val rightKind =
          templataTemplarInner.evaluateStructTemplata(state, range, rightStructTemplata, List(), expectedType)
        (leftKind == rightKind)
      }
      case (KindTemplata(leftKind), KindTemplata(rightKind)) => {
        (leftKind == rightKind)
      }
      case (CoordTemplata(leftCoord), CoordTemplata(rightCoord)) => {
        (leftCoord == rightCoord)
      }
      case (OwnershipTemplata(leftOwnership), OwnershipTemplata(rightOwnership)) => {
        (leftOwnership == rightOwnership)
      }
      case (PermissionTemplata(leftPermission), PermissionTemplata(rightPermission)) => {
        (leftPermission == rightPermission)
      }
      case (MutabilityTemplata(leftMutability), MutabilityTemplata(rightMutability)) => {
        (leftMutability == rightMutability)
      }
      case (VariabilityTemplata(leftVariability), VariabilityTemplata(rightVariability)) => {
        (leftVariability == rightVariability)
      }
      case (StringTemplata(leftVal), StringTemplata(righVal)) => {
        (leftVal == righVal)
      }
      case (IntegerTemplata(leftVal), IntegerTemplata(righVal)) => {
        (leftVal == righVal)
      }
      case (leftInterfaceTemplata @ InterfaceTemplata(_, _), rightInterfaceTemplata @ InterfaceTemplata(_, _)) => {
        (leftInterfaceTemplata == rightInterfaceTemplata)
      }
      case (CoordListTemplata(leftCoords), CoordListTemplata(rightCoords)) => {
        leftCoords == rightCoords
      }
      case (PrototypeTemplata(leftP), PrototypeTemplata(rightP)) => leftP == rightP
      case _ => vfail("make case for:\n" + left + "\n" + right)
    }
  }

  private[infer] def templataMatchesType(templata: ITemplata, tyype: ITemplataType): Boolean = {
    // Every time we make this function consider another type, add it to here.
    // If this match fails, then we know we're running into a case we haven't considered.
    tyype match {
      case IntegerTemplataType =>
      case BooleanTemplataType =>
      case OwnershipTemplataType =>
      case MutabilityTemplataType =>
      case PermissionTemplataType =>
      case LocationTemplataType =>
      case CoordTemplataType =>
      case KindTemplataType =>
      case PrototypeTemplataType =>
//      case StructTypeSR =>
//      case InterfaceTypeSR =>
//      case SequenceTypeSR =>
//      case PackTypeSR =>
//      case SequenceTypeSR =>
//      case CallableTypeSR =>
//      case CallableTypeSR =>
//      case ArrayTypeSR =>
    }
    // Every time we make this function consider another templata, add it to here.
    // If this match fails, then we know we're running into a case we haven't considered.
    templata match {
      case IntegerTemplata(_) =>
      case BooleanTemplata(_) =>
      case OwnershipTemplata(_) =>
      case MutabilityTemplata(_) =>
      case PermissionTemplata(_) =>
      case LocationTemplata(_) =>
      case CoordTemplata(_) =>
      case KindTemplata(_) =>
      case KindTemplata(StructRef2(_)) =>
      case KindTemplata(InterfaceRef2(_)) =>
      case KindTemplata(KnownSizeArrayT2(_, _)) =>
      case KindTemplata(PackT2(_, _)) =>
      case KindTemplata(UnknownSizeArrayT2(_)) =>
      case KindTemplata(_) =>
      case InterfaceTemplata(_, _) =>
      case StructTemplata(_, _) =>
      case PrototypeTemplata(_) =>
    }

    (tyype, templata) match {
      case (IntegerTemplataType, IntegerTemplata(_)) => true
      case (BooleanTemplataType, BooleanTemplata(_)) => true
      case (OwnershipTemplataType, OwnershipTemplata(_)) => true
      case (MutabilityTemplataType, MutabilityTemplata(_)) => true
      case (PermissionTemplataType, PermissionTemplata(_)) => true
      case (LocationTemplataType, LocationTemplata(_)) => true
      case (CoordTemplataType, CoordTemplata(_)) => true
      case (CoordTemplataType, KindTemplata(_)) => false
      case (KindTemplataType, KindTemplata(_)) => true
      case (PrototypeTemplataType, PrototypeTemplata(_)) => true
      case (KindTemplataType, StructTemplata(_, structA)) => {
        if (structA.isTemplate) {
          // It's a struct template, not a struct.
          false
        } else {
          true
        }
      }
      case (KindTemplataType, InterfaceTemplata(_, interfaceA)) => {
        if (interfaceA.isTemplate) {
          // It's an interface template, not an interface.
          false
        } else {
          true
        }
      }
//      case (ArrayTypeSR, KindTemplata(ArraySequenceT2(_, _))) => true
//      case (ArrayTypeSR, KindTemplata(UnknownSizeArrayT2(_))) => true
//      case (StructTypeSR, KindTemplata(StructRef2(_))) => true
//      case (StructTypeSR, StructTemplata(_, structS)) => {
//        if (PredictingEvaluator.structIsTemplate(structS)) {
//          // It's a struct template, not a struct.
//          false
//        } else {
//          true
//        }
//      }
//      case (InterfaceTypeSR, KindTemplata(InterfaceRef2(_))) => true
//      case (InterfaceTypeSR, InterfaceTemplata(_, interfaceS)) => {
//        if (PredictingEvaluator.interfaceIsTemplate(interfaceS)) {
//          // It's an interface template, not an interface.
//          false
//        } else {
//          true
//        }
//      }
//      case (SequenceTypeSR, KindTemplata(ArraySequenceT2(_, _))) => true
//      case (PackTypeSR, KindTemplata(PackT2(_, _))) => true
//      case (SequenceTypeSR, KindTemplata(UnknownSizeArrayT2(_))) => true
//      case (CallableTypeSR, KindTemplata(FunctionT2(_, _))) => true
//      case (CallableTypeSR, KindTemplata(StructRef2(_))) => {
//        // do we want to consider closures here? i think so?
//        vfail()
//      }
//      case (CallableTypeSR, KindTemplata(InterfaceRef2(_))) => {
//        // can interfaces have __call?
//        vfail()
//      }
//      case (CallableTypeSR, KindTemplata(_)) => {
//        // Gotta check if the referend is callable
//        vfail("unimplemented")
//      }
//      case _ => false
    }
  }

}
