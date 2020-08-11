package net.verdagon.vale.templar.types

import net.verdagon.vale.astronomer.{GlobalFunctionFamilyNameA, INameA}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.IEnvironment
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vcurious, vfail}

import scala.collection.immutable.List

sealed trait Ownership extends Queriable2 {
  def order: Int;
}
case object Share extends Ownership {
  override def order: Int = 1;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case object Own extends Ownership {
  override def order: Int = 2;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case object Borrow extends Ownership {
  override def order: Int = 3;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case object Weak extends Ownership {
  override def order: Int = 4;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}

sealed trait Mutability extends Queriable2 {
  def order: Int;
}
case object Mutable extends Mutability {
  override def order: Int = 1;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case object Immutable extends Mutability {
  override def order: Int = 2;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}

sealed trait Variability extends Queriable2 {
  def order: Int;
}
case object Final extends Variability {
  override def order: Int = 1;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case object Varying extends Variability {
  override def order: Int = 2;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}

sealed trait Permission extends Queriable2 {
  def order: Int;
}
case object Readonly extends Permission {
  override def order: Int = 1;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case object Readwrite extends Permission {
  override def order: Int = 2;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case object ExclusiveReadwrite extends Permission {
  override def order: Int = 3;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}

sealed trait Location extends Queriable2 {
  def order: Int;
}
case object Inline extends Location {
  override def order: Int = 1;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case object Yonder extends Location {
  override def order: Int = 1;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}


case class Coord(ownership: Ownership, referend: Kind) extends Queriable2 {
  referend match {
    case Int2() | Bool2() | Str2() | Float2() | Void2() | Never2() => {
      vassert(ownership == Share)
    }
    case _ =>
  }

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ ownership.all(func) ++ referend.all(func)
  }
}
sealed trait Kind extends Queriable2 {
  def order: Int;

  // Note, we don't have a mutability: Mutability in here because this Kind
  // should be enough to uniquely identify a type, and no more.
  // We can always get the mutability for a struct from the temputs.
}

// like Scala's Nothing. No instance of this can ever happen.
case class Never2() extends Kind {
  override def order: Int = 6;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = List(this).collect(func)
}

// Mostly for interoperability with extern functions
case class Void2() extends Kind {
  override def order: Int = 16;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = List(this).collect(func)
}

case class Int2() extends Kind {
  override def order: Int = 8;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = List(this).collect(func)
}

case class Bool2() extends Kind {
  override def order: Int = 9;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = List(this).collect(func)
}

case class Str2() extends Kind {
  override def order: Int = 10;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = List(this).collect(func)
}

case class Float2() extends Kind {
  override def order: Int = 11;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = List(this).collect(func)
}

case class PackT2(members: List[Coord], underlyingStruct: StructRef2) extends Kind {
  override def order: Int = 21;

  underlyingStruct.all({
    case AddressMemberType2(_) => vfail("Packs' underlying structs cant have addressibles in them!")
  })

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ members.flatMap(_.all(func)) ++ underlyingStruct.all(func)
  }
}

case class TupleT2(members: List[Coord], underlyingStruct: StructRef2) extends Kind {
  override def order: Int = 20;

  underlyingStruct.all({
    case AddressMemberType2(_) => vfail("Tuples' underlying structs cant have addressibles in them!")
  })

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ members.flatMap(_.all(func)) ++ underlyingStruct.all(func)
  }
}

case class RawArrayT2(
  memberType: Coord,
  mutability: Mutability) extends Queriable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ memberType.all(func)
  }
}

case class KnownSizeArrayT2(size: Int, array: RawArrayT2) extends Kind {
  override def order: Int = 12;

  def name: FullName2[KnownSizeArrayName2] = FullName2(List(), KnownSizeArrayName2(size, RawArrayName2(array.mutability, array.memberType)))

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ array.all(func)
  }
}

case class UnknownSizeArrayT2(array: RawArrayT2) extends Kind {
  override def order: Int = 19;

  def name: FullName2[UnknownSizeArrayName2] = FullName2(List(), UnknownSizeArrayName2(RawArrayName2(array.mutability, array.memberType)))

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ array.all(func)
  }
}

case class StructMember2(
  name: IVarName2,
  // In the case of address members, this refers to the variability of the pointee variable.
  variability: Variability,
  tyype: IMemberType2
) extends Queriable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ tyype.all(func)
  }
}

sealed trait IMemberType2 extends Queriable2 {
  def reference: Coord

  def expectReferenceMember(): ReferenceMemberType2 = {
    this match {
      case r @ ReferenceMemberType2(_) => r
      case a @ AddressMemberType2(_) => vfail("Expected reference member, was address member!")
    }
  }
  def expectAddressMember(): AddressMemberType2 = {
    this match {
      case r @ ReferenceMemberType2(_) => vfail("Expected reference member, was address member!")
      case a @ AddressMemberType2(_) => a
    }
  }
}
case class AddressMemberType2(reference: Coord) extends IMemberType2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ reference.all(func)
  }
}
case class ReferenceMemberType2(reference: Coord) extends IMemberType2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ reference.all(func)
  }
}

trait CitizenDefinition2 {
  def getRef: CitizenRef2;
}


// We include templateArgTypes to aid in looking this up... same reason we have name
case class StructDefinition2(
  fullName: FullName2[ICitizenName2],
  attributes: List[ICitizenAttribute2],
  weakable: Boolean,
  mutability: Mutability,
  members: List[StructMember2],
  isClosure: Boolean
) extends CitizenDefinition2 with Queriable2 {
  // debt: move this to somewhere else. let's allow packs to have packs, just nothing else.
//  all({
//    case StructMember2(_, _, ReferenceMemberType2(Coord(_, PackT2(_, _)))) => {
//      vfail("Structs can't have packs in them!")
//    }
//  })

  override def getRef: StructRef2 = StructRef2(fullName)

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++
      fullName.all(func) ++
      members.flatMap(_.all(func))
  }

  def getMember(memberName: String): StructMember2 = {
    members.find(p => p.name.equals(memberName)) match {
      case None => vfail("Couldn't find member " + memberName)
      case Some(member) => member
    }
  }

  private def getIndex(memberName: IVarName2): Int = {
    members.zipWithIndex.find(p => p._1.name.equals(memberName)) match {
      case None => vfail("wat")
      case Some((member, index)) => index
    }
  }

  def getMemberAndIndex(memberName: IVarName2): (StructMember2, Int) = {
    members.zipWithIndex.find(p => p._1.name.equals(memberName)) match {
      case None => vfail("wat")
      case Some((member, index)) => (member, index)
    }
  }
}

case class InterfaceDefinition2(
    fullName: FullName2[CitizenName2],
    attributes: List[ICitizenAttribute2],
    weakable: Boolean,
    mutability: Mutability,
    // This does not include abstract functions declared outside the interface.
    // See IMRFDI for why we need to remember only the internal methods here.
    internalMethods: List[FunctionHeader2]
) extends CitizenDefinition2 with Queriable2 {
  override def getRef = InterfaceRef2(fullName)

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ fullName.all(func) ++ internalMethods.flatMap(_.all(func))
  }
}

trait CitizenRef2 extends Kind {
  def fullName: FullName2[ICitizenName2]
}

// These should only be made by struct templar, which puts the definition into temputs at the same time
case class StructRef2(fullName: FullName2[ICitizenName2]) extends CitizenRef2 {
  override def order: Int = 14;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ fullName.all(func)
  }
}

// Represents a bunch of functions that have the same name.
// See ROS.
// Lowers to an empty struct.
case class OverloadSet(
    env: IEnvironment,
    name: GlobalFunctionFamilyNameA,
    voidStructRef: StructRef2
) extends Kind {
  override def order: Int = 19;

  if (name == GlobalFunctionFamilyNameA("true")) {
    vcurious()
  }

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}

//// In the case of:
//// fn main() {
////   x = 3;
////   {
////     println(x);
////     x = 6;
////     {
////       println(x);
////       x = 6;
////     }();
////   }();
//// }
//// main has a lambda number of 0,
//// the first lambda might have a lambda number of, say, 7
//// the inner lambda might have a lambda number of like 12
//// Let's say this TemplatedClosure2 represents the inner lambda.
//// The containing lambda number would be 7.
//case class OrdinaryClosure2(
//    containingFunctionLambdaNumber: Int,
//    structRef: StructRef2,
//    prototype2: Prototype2) extends Kind {
//  override def order: Int = 17;
//
//  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
//    List(this).collect(func) ++ structRef.all(func)
//  }
//}

case class InterfaceRef2(
  fullName: FullName2[ICitizenName2]
) extends CitizenRef2 with Queriable2 {
  override def order: Int = 15;

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ fullName.all(func)
  }
}

// This is what we use to search for overloads.
case class ParamFilter(
    tyype: Coord,
    virtuality: Option[Virtuality2])


object ReferenceComparator extends Ordering[Coord] {
  override def compare(a: Coord, b: Coord): Int = {
    val orderDiff = a.ownership.order compare b.ownership.order;
    if (orderDiff != 0) {
      orderDiff
    } else {
      ReferendComparator.compare(a.referend, b.referend)
    }
  }
}

object ReferendComparator extends Ordering[Kind] {
  override def compare(a: Kind, b: Kind): Int = {
    val orderDiff = a.order compare b.order;
    if (orderDiff != 0) {
      orderDiff
    } else {
      a match {
        case Int2() => 0
        case Bool2() => 0
        case Str2() => 0
        case PackT2(innerTypes, underlyingStruct) => compare(underlyingStruct, b.asInstanceOf[PackT2].underlyingStruct)
        case StructRef2(thisFullName) => {
          val StructRef2(thatFullName) = b.asInstanceOf[StructRef2];
          FullNameComparator.compare(thisFullName, thatFullName)
        }
        case _ => vfail("wat " + a)
      }
    }
  }
}

object FullNameComparator extends Ordering[FullName2[IName2]] {
  override def compare(a: FullName2[IName2], b: FullName2[IName2]): Int = {
    val aSteps = a.steps
    val bSteps = b.steps

    if (aSteps.length == 0) {
      if (bSteps.length == 0) {
        0
      } else {
        -1
      }
    } else {
      if (bSteps.length == 0) {
        1
      } else {
        val humanNameDiff = aSteps.head.order.compare(bSteps.head.order)
        if (humanNameDiff != 0) {
          humanNameDiff
        } else {
          (aSteps.head, bSteps.head) match {
            case (ImplDeclareName2(codeLocationA), ImplDeclareName2(codeLocationB)) => compare(codeLocationA, codeLocationB)
            case (LetName2(codeLocationA), LetName2(codeLocationB)) => compare(codeLocationA, codeLocationB)
            case (UnnamedLocalName2(codeLocationA), UnnamedLocalName2(codeLocationB)) => compare(codeLocationA, codeLocationB)
            case (ClosureParamName2(), ClosureParamName2()) => 0
            case (MagicParamName2(codeLocationA), MagicParamName2(codeLocationB)) => compare(codeLocationA, codeLocationB)
            case (CodeVarName2(nameA), CodeVarName2(nameB)) => nameA.compareTo(nameB)
//            case (CodeRune2(nameA), CodeRune2(nameB)) => nameA.compareTo(nameB)
//            case (ImplicitRune2(nameA), ImplicitRune2(nameB)) => nameA.compareTo(nameB)
//            case (MemberRune2(memberIndexA), MemberRune2(memberIndexB)) => memberIndexA.compareTo(memberIndexB)
//            case (MagicImplicitRune2(magicParamIndexA), MagicImplicitRune2(magicParamIndexB)) => magicParamIndexA.compareTo(magicParamIndexB)
//            case (ReturnRune2(), ReturnRune2()) => 0
            case (FunctionName2(humanNameA, templateArgsA, parametersA), FunctionName2(humanNameB, templateArgsB, parametersB)) => {
              val nameDiff = humanNameA.compareTo(humanNameB)
              if (nameDiff != 0)
                return nameDiff
              val templateArgsDiff = TemplataTypeListComparator.compare(templateArgsA, templateArgsB)
              if (templateArgsDiff != 0)
                return templateArgsDiff
              TemplataTypeListComparator.compare(parametersA.map(CoordTemplata), parametersB.map(CoordTemplata))
            }
//            case (LambdaName2(codeLocationA, templateArgsA, parametersA), LambdaName2(codeLocationB, templateArgsB, parametersB)) => {
//              val locDiff = compare(codeLocationA, codeLocationB)
//              if (locDiff != 0)
//                return locDiff
//              val templateArgsDiff = TemplataTypeListComparator.compare(templateArgsA, templateArgsB)
//              if (templateArgsDiff != 0)
//                return templateArgsDiff
//              TemplataTypeListComparator.compare(parametersA.map(CoordTemplata), parametersB.map(CoordTemplata))
//            }
            case (CitizenName2(humanNameA, templateArgsA), CitizenName2(humanNameB, templateArgsB)) => {
              val nameDiff = humanNameA.compareTo(humanNameB)
              if (nameDiff != 0)
                return nameDiff
              TemplataTypeListComparator.compare(templateArgsA, templateArgsB)
            }
            case (TupleName2(membersA), TupleName2(membersB)) => {
              TemplataTypeListComparator.compare(membersA.map(CoordTemplata), membersB.map(CoordTemplata))
            }
            case (LambdaCitizenName2(codeLocationA), LambdaCitizenName2(codeLocationB)) => {
              compare(codeLocationA, codeLocationB)
            }
            case (CitizenName2(humanNameA, templateArgsA), CitizenName2(humanNameB, templateArgsB)) => {
              val nameDiff = humanNameA.compareTo(humanNameB)
              if (nameDiff != 0)
                return nameDiff
              TemplataTypeListComparator.compare(templateArgsA, templateArgsB)
            }
          }
        }
      }
    }
  }

  def compare(a: CodeLocation2, b: CodeLocation2): Int = {
    val fileDiff = a.file.compareTo(b.file)
    if (fileDiff != 0)
      return fileDiff
    a.offset.compareTo(b.offset)
  }
}

object TemplataTypeComparator extends Ordering[ITemplata] {
  override def compare(a: ITemplata, b: ITemplata):Int = {
    if (a.order != b.order) {
      Math.signum(a.order - b.order).toInt
    } else {
      (a, b) match {
        case _ => vfail("impl")
//        case (StructTemplateTemplata(struct1A), StructTemplateTemplata(struct1B)) => {
//          Math.signum(struct1A.struct1Id - struct1B.struct1Id).toInt
//        }
//        case (InterfaceTemplateTemplata(interface1A), InterfaceTemplateTemplata(interface1B)) => {
//          Math.signum(interface1A.interface1Id - interface1B.interface1Id).toInt
//        }
      }
    }
  }
}

object ReferenceListComparator extends Ordering[List[Coord]] {
  override def compare(a: List[Coord], b: List[Coord]):Int = {
    if (a.length == 0) {
      if (b.length == 0) {
        0
      } else {
        -1
      }
    } else {
      if (b.length == 0) {
        1
      } else {
        val firstDiff = ReferenceComparator.compare(a.head, b.head);
        if (firstDiff != 0) {
          firstDiff
        } else {
          compare(a.tail, b.tail)
        }
      }
    }
  }
}

object TemplataTypeListComparator extends Ordering[List[ITemplata]] {
  override def compare(a: List[ITemplata], b: List[ITemplata]):Int = {
    if (a.length == 0) {
      if (b.length == 0) {
        0
      } else {
        -1
      }
    } else {
      if (b.length == 0) {
        1
      } else {
        val firstDiff = TemplataTypeComparator.compare(a.head, b.head);
        if (firstDiff != 0) {
          firstDiff
        } else {
          compare(a.tail, b.tail)
        }
      }
    }
  }
}

// The type of ref count that an object might have. Used with the CheckRefCountH
// instruction for counting how many references of a certain type there are.
sealed trait RefCountCategory
// Used to count how many variables are refering to an object.
case object VariableRefCount extends RefCountCategory
// Used to count how many members are refering to an object.
case object MemberRefCount extends RefCountCategory
// Used to count how many registers are refering to an object.
case object RegisterRefCount extends RefCountCategory
