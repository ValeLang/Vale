package dev.vale.typing.types

import dev.vale._
import dev.vale.postparsing.IImpreciseNameS
import dev.vale.typing.ast.{AbstractT, FunctionHeaderT, ICitizenAttributeT}
import dev.vale.typing.env.IEnvironment
import dev.vale.typing.names._
import dev.vale.highertyping._
import dev.vale.postparsing._
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.templata._
import dev.vale.typing.types._

import scala.collection.immutable.List

sealed trait OwnershipT  {
}
case object ShareT extends OwnershipT {
  override def toString: String = "share"
}
case object OwnT extends OwnershipT {
  override def toString: String = "own"
}
case object BorrowT extends OwnershipT {
  override def toString: String = "borrow"
}
case object WeakT extends OwnershipT {
  override def toString: String = "weak"
}

sealed trait MutabilityT  {
}
case object MutableT extends MutabilityT {
  override def toString: String = "mut"
}
case object ImmutableT extends MutabilityT {
  override def toString: String = "imm"
}

sealed trait VariabilityT  {
}
case object FinalT extends VariabilityT {
  override def toString: String = "final"
}
case object VaryingT extends VariabilityT {
  override def toString: String = "vary"
}

sealed trait LocationT  {
}
case object InlineT extends LocationT {
  override def toString: String = "inl"
}
case object YonderT extends LocationT {
  override def toString: String = "heap"
}


case class CoordT(ownership: OwnershipT, kind: KindT)  {
  vpass()

  kind match {
    case IntT(_) | BoolT() | StrT() | FloatT() | VoidT() | NeverT(_) => {
      vassert(ownership == ShareT)
    }
    case _ =>
  }
  if (ownership == OwnT) {
    // See CSHROOR for why we don't assert this.
    // vassert(permission == Readwrite)
  }
}

sealed trait KindT {
  // Note, we don't have a mutability: Mutability in here because this Kind
  // should be enough to uniquely identify a type, and no more.
  // We can always get the mutability for a struct from the coutputs.

  def expectCitizen(): ICitizenTT = {
    this match {
      case c : ICitizenTT => c
      case _ => vfail()
    }
  }

  def expectInterface(): InterfaceTT = {
    this match {
      case c @ InterfaceTT(_) => c
      case _ => vfail()
    }
  }

  def expectStruct(): StructTT = {
    this match {
      case c @ StructTT(_) => c
      case _ => vfail()
    }
  }
}

// like Scala's Nothing. No instance of this can ever happen.
case class NeverT(
  // True if this Never came from a break.
  // While will have to know about this; if it's a Never from a ret, it should
  // propagate it, but if its body is a break never, the while produces a void.
  // See BRCOBS.
  fromBreak: Boolean
) extends KindT {

}

// Mostly for interoperability with extern functions
case class VoidT() extends KindT {

}

object IntT {
  val i32: IntT = IntT(32)
  val i64: IntT = IntT(64)
}
case class IntT(bits: Int) extends KindT {
}

case class BoolT() extends KindT {

}

case class StrT() extends KindT {

}

case class FloatT() extends KindT {

}

object contentsStaticSizedArrayTT {
  def unapply(ssa: StaticSizedArrayTT):
  Option[(ITemplataT[IntegerTemplataType], ITemplataT[MutabilityTemplataType], ITemplataT[VariabilityTemplataType], CoordT)] = {
    val IdT(_, _, StaticSizedArrayNameT(_, size, variability, RawArrayNameT(mutability, coord))) = ssa.name
    Some((size, mutability, variability, coord))
  }
}
case class StaticSizedArrayTT(
  name: IdT[StaticSizedArrayNameT]
) extends KindT with IInterning {
  vassert(name.initSteps.isEmpty)
  def mutability: ITemplataT[MutabilityTemplataType] = name.localName.arr.mutability
  def elementType = name.localName.arr.elementType
  def size = name.localName.size
  def variability = name.localName.variability
}

object contentsRuntimeSizedArrayTT {
  def unapply(rsa: RuntimeSizedArrayTT): Option[(ITemplataT[MutabilityTemplataType], CoordT)] = {
    val IdT(_, _, RuntimeSizedArrayNameT(_, RawArrayNameT(mutability, coord))) = rsa.name
    Some((mutability, coord))
  }
}
case class RuntimeSizedArrayTT(
  name: IdT[RuntimeSizedArrayNameT]
) extends KindT with IInterning {
  def mutability = name.localName.arr.mutability
  def elementType = name.localName.arr.elementType
}

object ICitizenTT {
  def unapply(self: ICitizenTT): Option[IdT[ICitizenNameT]] = {
    Some(self.id)
  }
}

// Structs, interfaces, and placeholders
sealed trait ISubKindTT extends KindT {
  def id: IdT[ISubKindNameT]
}
// Interfaces and placeholders
sealed trait ISuperKindTT extends KindT {
  def id: IdT[ISuperKindNameT]
}

sealed trait ICitizenTT extends ISubKindTT with IInterning {
  def id: IdT[ICitizenNameT]
}

// These should only be made by StructCompiler, which puts the definition and bounds into coutputs at the same time
case class StructTT(id: IdT[IStructNameT]) extends ICitizenTT {
  (id.initSteps.lastOption, id.localName) match {
    case (Some(StructTemplateNameT(_)), StructNameT(_, _)) => vfail()
    case _ =>
  }
}

case class InterfaceTT(id: IdT[IInterfaceNameT]) extends ICitizenTT with ISuperKindTT {
  (id.initSteps.lastOption, id.localName) match {
    case (Some(InterfaceTemplateNameT(_)), InterfaceNameT(_, _)) => vfail()
    case _ =>
  }
}

// Represents a bunch of functions that have the same name.
// See ROS.
// Lowers to an empty struct.
case class OverloadSetT(
  env: IEnvironment,
  // The name to look for in the environment.
  name: IImpreciseNameS
) extends KindT with IInterning {
  vpass()

}

case class PlaceholderT(id: IdT[PlaceholderNameT]) extends ISubKindTT with ISuperKindTT
