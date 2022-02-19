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
case object ShareT extends OwnershipT {
  override def order: Int = 1
  override def toString: String = "share"
}
case object OwnT extends OwnershipT {
  override def order: Int = 2
  override def toString: String = "own"
}
case object BorrowT extends OwnershipT {
  override def order: Int = 3
  override def toString: String = "borrow"
}
case object PointerT extends OwnershipT {
  override def order: Int = 3
  override def toString: String = "ptr"
}
case object WeakT extends OwnershipT {
  override def order: Int = 4
  override def toString: String = "weak"
}

sealed trait MutabilityT  {
  def order: Int;
}
case object MutableT extends MutabilityT {
  override def order: Int = 1
  override def toString: String = "mut"
}
case object ImmutableT extends MutabilityT {
  override def order: Int = 2
  override def toString: String = "imm"
}

sealed trait VariabilityT  {
  def order: Int;
}
case object FinalT extends VariabilityT {
  override def order: Int = 1
  override def toString: String = "final"
}
case object VaryingT extends VariabilityT {
  override def order: Int = 2
  override def toString: String = "vary"
}

sealed trait PermissionT  {
  def order: Int;
}
case object ReadonlyT extends PermissionT {
  override def order: Int = 1
  override def toString: String = "ro"
}
case object ReadwriteT extends PermissionT {
  override def order: Int = 2
  override def toString: String = "rw"
}

sealed trait LocationT  {
  def order: Int;
}
case object InlineT extends LocationT {
  override def order: Int = 1
  override def toString: String = "inl"
}
case object YonderT extends LocationT {
  override def order: Int = 1
  override def toString: String = "heap"
}


case class CoordT(ownership: OwnershipT, permission: PermissionT, kind: KindT)  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  vpass()

  kind match {
    case IntT(_) | BoolT() | StrT() | FloatT() | VoidT() | NeverT(_) => {
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
case class NeverT(
  // True if this Never came from a break.
  // While will have to know about this; if it's a Never from a return, it should
  // propagate it, but if its body is a break never, the while produces a void.
  // See BRCOBS.
  fromBreak: Boolean
) extends KindT {
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

case class StaticSizedArrayTT(
  size: Int,
  mutability: MutabilityT,
  variability: VariabilityT,
  elementType: CoordT
) extends KindT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 12;

  def name: FullNameT[StaticSizedArrayNameT] = FullNameT(PackageCoordinate.BUILTIN, Vector.empty, StaticSizedArrayNameT(size, RawArrayNameT(mutability, elementType)))
}

case class RuntimeSizedArrayTT(
  mutability: MutabilityT,
  elementType: CoordT
) extends KindT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 19;

  def name: FullNameT[RuntimeSizedArrayNameT] = FullNameT(PackageCoordinate.BUILTIN, Vector.empty, RuntimeSizedArrayNameT(RawArrayNameT(mutability, elementType)))
}

case class StructMemberT(
  name: IVarNameT,
  // In the case of address members, this refers to the variability of the pointee variable.
  variability: VariabilityT,
  tyype: IMemberTypeT
)  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()
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
  name: IImpreciseNameS
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
