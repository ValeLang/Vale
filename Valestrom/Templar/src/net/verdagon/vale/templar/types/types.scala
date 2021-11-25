package net.verdagon.vale.templar.types

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.{FunctionHeaderT, ICitizenAttributeT, VirtualityT}
import net.verdagon.vale.templar.env.IEnvironment
import net.verdagon.vale.templar.names.{AnonymousSubstructNameT, AnonymousSubstructTemplateNameT, CitizenNameT, CitizenTemplateNameT, ClosureParamNameT, CodeVarNameT, FullNameT, FunctionNameT, ICitizenNameT, INameT, IVarNameT, ImplDeclareNameT, LambdaCitizenNameT, LetNameT, MagicParamNameT, RawArrayNameT, RuntimeSizedArrayNameT, StaticSizedArrayNameT, UnnamedLocalNameT}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{CodeLocationS, PackageCoordinate, vassert, vcurious, vfail, vimpl, vpass}

import scala.collection.immutable.List

sealed trait OwnershipT  {
  def order: Int;
}
case object ShareT   extends OwnershipT {
  override def order: Int = 1;



  override def toString: String = "share"
}
case object OwnT extends OwnershipT {
  override def order: Int = 2;



  override def toString: String = "own"
}
case object ConstraintT extends OwnershipT {
  override def order: Int = 3;



  override def toString: String = "constraint"
}
case object WeakT extends OwnershipT {
  override def order: Int = 4;



  override def toString: String = "weak"
}

sealed trait MutabilityT  {
  def order: Int;
}
case object MutableT extends MutabilityT {
  override def order: Int = 1;



  override def toString: String = "mut"
}
case object ImmutableT extends MutabilityT {
  override def order: Int = 2;



  override def toString: String = "imm"
}

sealed trait VariabilityT  {
  def order: Int;
}
case object FinalT extends VariabilityT {
  override def order: Int = 1;



  override def toString: String = "final"
}
case object VaryingT extends VariabilityT {
  override def order: Int = 2;



  override def toString: String = "vary"
}

sealed trait PermissionT  {
  def order: Int;
}
case object ReadonlyT extends PermissionT {
  override def order: Int = 1;



  override def toString: String = "ro"
}
case object ReadwriteT extends PermissionT {
  override def order: Int = 2;



  override def toString: String = "rw"
}
//case object ExclusiveReadwrite extends Permission {
//  override def order: Int = 3;
//
//  def all[T](func: PartialFunction[Queriable2, T]): Vector[T] = {
//    Vector(this).collect(func)
//  }
//}

sealed trait LocationT  {
  def order: Int;
}
case object InlineT extends LocationT {
  override def order: Int = 1;



  override def toString: String = "inl"
}
case object YonderT extends LocationT {
  override def order: Int = 1;



  override def toString: String = "heap"
}


case class CoordT(ownership: OwnershipT, permission: PermissionT, kind: KindT)  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  kind match {
    case IntT(_) | BoolT() | StrT() | FloatT() | VoidT() | NeverT() => {
      vassert(ownership == ShareT)
    }
    case _ =>
  }
  if (ownership == ShareT) {
    vassert(permission == ReadonlyT)
  }
  if (ownership == OwnT) {
    // See CSHROOR for why we don't assert this.
    // vassert(permission == Readwrite)
  }
}

sealed trait KindT  {
  def order: Int;

  // Note, we don't have a mutability: Mutability in here because this Kind
  // should be enough to uniquely identify a type, and no more.
  // We can always get the mutability for a struct from the temputs.
}

// like Scala's Nothing. No instance of this can ever happen.
case class NeverT() extends KindT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 6;


}

// Mostly for interoperability with extern functions
case class VoidT() extends KindT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 16;


}

object IntT {
  val i32: IntT = IntT(32)
  val i64: IntT = IntT(64)
}
case class IntT(bits: Int) extends KindT {
  val hash = 546325456 + bits; override def hashCode(): Int = hash;
  override def order: Int = 8;


}

case class BoolT() extends KindT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 9;


}

case class StrT() extends KindT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 10;


}

case class FloatT() extends KindT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 11;


}

//case class PackTT(members: Vector[CoordT], underlyingStruct: StructTT) extends KindT {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  override def order: Int = 21;
//}
//
//case class TupleTT(members: Vector[CoordT], underlyingStruct: StructTT) extends KindT {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  override def order: Int = 20;
//}

case class RawArrayTT(
  elementType: CoordT,
  mutability: MutabilityT,
  variability: VariabilityT
)  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}

case class StaticSizedArrayTT(size: Int, array: RawArrayTT) extends KindT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 12;

  def name: FullNameT[StaticSizedArrayNameT] = FullNameT(PackageCoordinate.BUILTIN, Vector.empty, StaticSizedArrayNameT(size, RawArrayNameT(array.mutability, array.elementType)))


}

case class RuntimeSizedArrayTT(array: RawArrayTT) extends KindT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 19;

  def name: FullNameT[RuntimeSizedArrayNameT] = FullNameT(PackageCoordinate.BUILTIN, Vector.empty, RuntimeSizedArrayNameT(RawArrayNameT(array.mutability, array.elementType)))


}

case class StructMemberT(
  name: IVarNameT,
  // In the case of address members, this refers to the variability of the pointee variable.
  variability: VariabilityT,
  tyype: IMemberTypeT
)  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}

sealed trait IMemberTypeT  {
  def reference: CoordT

  def expectReferenceMember(): ReferenceMemberTypeT = {
    this match {
      case r @ ReferenceMemberTypeT(_) => r
      case a @ AddressMemberTypeT(_) => vfail("Expected reference member, was address member!")
    }
  }
  def expectAddressMember(): AddressMemberTypeT = {
    this match {
      case r @ ReferenceMemberTypeT(_) => vfail("Expected reference member, was address member!")
      case a @ AddressMemberTypeT(_) => a
    }
  }
}
case class AddressMemberTypeT(reference: CoordT) extends IMemberTypeT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class ReferenceMemberTypeT(reference: CoordT) extends IMemberTypeT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}

trait CitizenDefinitionT {
  def getRef: CitizenRefT;
}


// We include templateArgTypes to aid in looking this up... same reason we have name
case class StructDefinitionT(
  fullName: FullNameT[ICitizenNameT],
  attributes: Vector[ICitizenAttributeT],
  weakable: Boolean,
  mutability: MutabilityT,
  members: Vector[StructMemberT],
  isClosure: Boolean
) extends CitizenDefinitionT {
  override def hashCode(): Int = vcurious()
  // debt: move this to somewhere else. let's allow packs to have packs, just nothing else.
//  all({
//    case StructMember2(_, _, ReferenceMemberType2(Coord(_, PackT2(_, _)))) => {
//      vfail("Structs can't have packs in them!")
//    }
//  })

  override def getRef: StructTT = StructTT(fullName)



  def getMember(memberName: String): StructMemberT = {
    members.find(p => p.name.equals(memberName)) match {
      case None => vfail("Couldn't find member " + memberName)
      case Some(member) => member
    }
  }

  private def getIndex(memberName: IVarNameT): Int = {
    members.zipWithIndex.find(p => p._1.name.equals(memberName)) match {
      case None => vfail("wat")
      case Some((member, index)) => index
    }
  }

  def getMemberAndIndex(memberName: IVarNameT): Option[(StructMemberT, Int)] = {
    members.zipWithIndex.find(p => p._1.name.equals(memberName))
  }
}

case class InterfaceDefinitionT(
    fullName: FullNameT[CitizenNameT],
    attributes: Vector[ICitizenAttributeT],
    weakable: Boolean,
    mutability: MutabilityT,
    // This does not include abstract functions declared outside the interface.
    // See IMRFDI for why we need to remember only the internal methods here.
    internalMethods: Vector[FunctionHeaderT]
) extends CitizenDefinitionT  {
  override def hashCode(): Int = vcurious()
  override def getRef = InterfaceTT(fullName)


}

trait CitizenRefT extends KindT {
  def fullName: FullNameT[ICitizenNameT]
}

// These should only be made by struct templar, which puts the definition into temputs at the same time
case class StructTT(fullName: FullNameT[ICitizenNameT]) extends CitizenRefT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 14;
}

// Represents a bunch of functions that have the same name.
// See ROS.
// Lowers to an empty struct.
case class OverloadSet(
  env: IEnvironment,
  // The name to look for in the environment.
  name: IImpreciseNameS,
  voidStructRef: StructTT
) extends KindT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def order: Int = 19;


}

case class InterfaceTT(
  fullName: FullNameT[ICitizenNameT]
) extends CitizenRefT  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def order: Int = 15;


}

// This is what we use to search for overloads.
case class ParamFilter(
    tyype: CoordT,
    virtuality: Option[VirtualityT]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  def debugString: String = {
    tyype.toString + virtuality.map(" impl " + _.toString).getOrElse("")
  }
}


//object ReferenceComparator extends Ordering[CoordT] {
//  override def compare(a: CoordT, b: CoordT): Int = {
//    val orderDiff = a.ownership.order compare b.ownership.order;
//    if (orderDiff != 0) {
//      orderDiff
//    } else {
//      KindComparator.compare(a.kind, b.kind)
//    }
//  }
//}

//object KindComparator extends Ordering[KindT] {
//  override def compare(a: KindT, b: KindT): Int = {
//    val orderDiff = a.order compare b.order;
//    if (orderDiff != 0) {
//      orderDiff
//    } else {
//      a match {
//        case IntT(aBits) => {
//          val IntT(bBits) = b
//          aBits compare bBits
//        }
//        case BoolT() => 0
//        case StrT() => 0
////        case PackTT(innerTypes, underlyingStruct) => compare(underlyingStruct, b.asInstanceOf[PackTT].underlyingStruct)
//        case StructTT(thisFullName) => {
//          val StructTT(thatFullName) = b.asInstanceOf[StructTT];
//          FullNameComparator.compare(thisFullName, thatFullName)
//        }
//        case _ => vfail("wat " + a)
//      }
//    }
//  }
//}
//
//object FullNameComparator extends Ordering[FullNameT[INameT]] {
//  override def compare(a: FullNameT[INameT], b: FullNameT[INameT]): Int = {
//    val aSteps = a.steps
//    val bSteps = b.steps
//
//    if (aSteps.length == 0) {
//      if (bSteps.length == 0) {
//        0
//      } else {
//        -1
//      }
//    } else {
//      if (bSteps.length == 0) {
//        1
//      } else {
//        val humanNameDiff = aSteps.head.order.compare(bSteps.head.order)
//        if (humanNameDiff != 0) {
//          humanNameDiff
//        } else {
//          (aSteps.head, bSteps.head) match {
//            case (ImplDeclareNameT(codeLocationA), ImplDeclareNameT(codeLocationB)) => compare(codeLocationA, codeLocationB)
//            case (LetNameT(codeLocationA), LetNameT(codeLocationB)) => compare(codeLocationA, codeLocationB)
//            case (UnnamedLocalNameT(codeLocationA), UnnamedLocalNameT(codeLocationB)) => compare(codeLocationA, codeLocationB)
//            case (ClosureParamNameT(), ClosureParamNameT()) => 0
//            case (MagicParamNameT(codeLocationA), MagicParamNameT(codeLocationB)) => compare(codeLocationA, codeLocationB)
//            case (CodeVarNameT(nameA), CodeVarNameT(nameB)) => nameA.compareTo(nameB)
//            case (FunctionNameT(humanNameS, templateArgsA, parametersA), FunctionNameT(humanNameB, templateArgsB, parametersB)) => {
//              val nameDiff = humanNameS.compareTo(humanNameB)
//              if (nameDiff != 0)
//                return nameDiff
//              val templateArgsDiff = TemplataTypeListComparator.compare(templateArgsA, templateArgsB)
//              if (templateArgsDiff != 0)
//                return templateArgsDiff
//              TemplataTypeListComparator.compare(parametersA.map(CoordTemplata), parametersB.map(CoordTemplata))
//            }
//            case (CitizenNameT(humanNameS, templateArgsA), CitizenNameT(humanNameB, templateArgsB)) => {
//              val nameDiff = humanNameS.compareTo(humanNameB)
//              if (nameDiff != 0)
//                return nameDiff
//              TemplataTypeListComparator.compare(templateArgsA, templateArgsB)
//            }
////            case (TupleNameT(membersA), TupleNameT(membersB)) => {
////              TemplataTypeListComparator.compare(membersA.map(CoordTemplata), membersB.map(CoordTemplata))
////            }
//            case (LambdaCitizenNameT(codeLocationA), LambdaCitizenNameT(codeLocationB)) => {
//              compare(codeLocationA, codeLocationB)
//            }
//            case (CitizenNameT(humanNameS, templateArgsA), CitizenNameT(humanNameB, templateArgsB)) => {
//              val nameDiff = humanNameS.compareTo(humanNameB)
//              if (nameDiff != 0)
//                return nameDiff
//              TemplataTypeListComparator.compare(templateArgsA, templateArgsB)
//            }
//          }
//        }
//      }
//    }
//  }
//
//  def compare(a: CodeLocationS, b: CodeLocationS): Int = {
//    val fileDiff = a.file.compareTo(b.file)
//    if (fileDiff != 0)
//      return fileDiff
//    a.offset.compareTo(b.offset)
//  }
//}

//object TemplataTypeComparator extends Ordering[ITemplata] {
//  override def compare(a: ITemplata, b: ITemplata):Int = {
//    if (a.order != b.order) {
//      Math.signum(a.order - b.order).toInt
//    } else {
//      (a, b) match {
//        case _ => vfail("impl")
////        case (StructTemplateTemplata(struct1A), StructTemplateTemplata(struct1B)) => {
////          Math.signum(struct1A.struct1Id - struct1B.struct1Id).toInt
////        }
////        case (InterfaceTemplateTemplata(interface1A), InterfaceTemplateTemplata(interface1B)) => {
////          Math.signum(interface1A.interface1Id - interface1B.interface1Id).toInt
////        }
//      }
//    }
//  }
//}

//object ReferenceListComparator extends Ordering[Vector[CoordT]] {
//  override def compare(a: Vector[CoordT], b: Vector[CoordT]):Int = {
//    if (a.length == 0) {
//      if (b.length == 0) {
//        0
//      } else {
//        -1
//      }
//    } else {
//      if (b.length == 0) {
//        1
//      } else {
//        val firstDiff = ReferenceComparator.compare(a.head, b.head);
//        if (firstDiff != 0) {
//          firstDiff
//        } else {
//          compare(a.tail, b.tail)
//        }
//      }
//    }
//  }
//}

//object TemplataTypeListComparator extends Ordering[Vector[ITemplata]] {
//  override def compare(a: Vector[ITemplata], b: Vector[ITemplata]):Int = {
//    if (a.length == 0) {
//      if (b.length == 0) {
//        0
//      } else {
//        -1
//      }
//    } else {
//      if (b.length == 0) {
//        1
//      } else {
//        val firstDiff = TemplataTypeComparator.compare(a.head, b.head);
//        if (firstDiff != 0) {
//          firstDiff
//        } else {
//          compare(a.tail, b.tail)
//        }
//      }
//    }
//  }
//}
