package net.verdagon.vale.vivem

import net.verdagon.vale.metal._
import net.verdagon.vale.{vassert, vcheck, vfail, vimpl}

// RR = Runtime Result. Don't use these to determine behavior, just use
// these to check that things are as we expect.
case class RRReference(hamut: ReferenceH[KindH]) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class RRKind(hamut: KindH) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

class Allocation(
    val reference: ReferenceV, // note that this cannot change
    val kind: KindV // note that this cannot change
) {
  private var referrers = Map[IObjectReferrer, Int]()

  def id = reference.allocId

  def incrementRefCount(referrer: IObjectReferrer): Unit = {
    if (kind == VoidV) {
      // Void has no RC
      return
    }
    referrer match {
      case RegisterToObjectReferrer(_, _) => {
        // We can have multiple of these, thats fine
      }
      case _ => {
        if (referrers.contains(referrer)) {
          vfail("nooo")
        }
      }
    }
    referrers = referrers + (referrer -> (referrers.getOrElse(referrer, 0) + 1))
  }

  def decrementRefCount(referrer: IObjectReferrer): Unit = {
    if (kind == VoidV) {
      // Void has no RC
      return
    }
    if (!referrers.contains(referrer)) {
      vfail("nooooo\n" + referrer + "\nnot in:\n" + referrers)
    }
    referrers = referrers + (referrer -> (referrers(referrer) - 1))
    if (referrers(referrer) == 0) {
      referrers = referrers - referrer
      vassert(!referrers.contains(referrer))
    }
  }

  def getRefCount(): Int = {
    if (kind == VoidV) {
      // Pretend void has 1 RC so nobody ever deallocates it
      return 1
    }
    referrers
      .toVector
      .map(_._2)
      .sum
  }

  def ensureRefCount(maybeOwnershipFilter: Option[Set[OwnershipH]], expectedNum: Int): Unit = {
    if (kind == VoidV) {
      // Void has no RC
      return
    }
    var referrers = this.referrers
    referrers =
      maybeOwnershipFilter match {
        case None => referrers
        case Some(ownershipFilter) => referrers.filter({ case (key, _) => ownershipFilter.contains(key.ownership)})
      }
    val matchingReferrers = referrers.toVector.map(_._2)
    vcheck(
      matchingReferrers.size == expectedNum,
      "Expected " +
        expectedNum + " of " +
        maybeOwnershipFilter.map(_.toString + " ").getOrElse("") +
        "but was " + matchingReferrers.size + ":\n" +
        matchingReferrers.mkString("\n"),
      ConstraintViolatedException)
  }

  def printRefs() = {
    if (getTotalRefCount(None) > 0) {
      println("o" + reference.allocId.num + ": " + referrers.mkString(" "))
    }
  }

  def getTotalRefCount(maybeOwnershipFilter: Option[OwnershipH]): Int = {
    if (kind == VoidV) {
      // Pretend void has 1 RC so nobody ever deallocates it
      return 1
    }
    maybeOwnershipFilter match {
      case None => referrers.size
      case Some(ownershipFilter) => referrers.keys.filter(_.ownership == ownershipFilter).size
    }

  }

  override def finalize(): Unit = {
//    vassert(referrers.isEmpty)
  }

  def unapply(arg: Allocation): Option[KindV] = Some(kind)
}

object Allocation {
  def unapply(arg: Allocation): Option[KindV] = {
    Some(arg.kind)
  }
}

sealed trait KindV {
  def tyype: RRKind
}
sealed trait PrimitiveKindV extends KindV
case object VoidV extends PrimitiveKindV {
  override def tyype = RRKind(VoidH())
}
case class IntV(value: Long, bits: Int) extends PrimitiveKindV {
  override def tyype = RRKind(IntH(bits))
}
case class BoolV(value: Boolean) extends PrimitiveKindV {
  override def tyype = RRKind(BoolH())
}
case class FloatV(value: Double) extends PrimitiveKindV {
  override def tyype = RRKind(FloatH())
}
case class StrV(value: String) extends PrimitiveKindV {
  override def tyype = RRKind(StrH())
}

case class StructInstanceV(
    structH: StructDefinitionH,
    private var members: Option[Vector[ReferenceV]]
) extends KindV {
  vassert(members.get.size == structH.members.size)

  override def tyype = RRKind(structH.getRef)

  def getReferenceMember(index: Int) = {
    (structH.members(index).tyype, members.get(index)) match {
      case (_, ref) => ref
    }
  }

  def setReferenceMember(index: Int, reference: ReferenceV) = {
    members = Some(members.get.updated(index, reference))
  }

  // Zeros out the memory, which happens when the owning ref goes away.
  // The allocation is still alive until we let go of the last weak ref
  // too.
  def zero(): Unit = {
    members = None
  }
}

case class ArrayInstanceV(
    typeH: ReferenceH[KindH],
    elementTypeH: ReferenceH[KindH],
    capacity: Int,
    private var elements: Vector[ReferenceV]
) extends KindV {
  override def tyype = RRKind(typeH.kind)

  def getElement(index: Int): ReferenceV = {
    if (index < 0 || index >= elements.size) {
      throw PanicException();
    }
    elements(index)
  }

  def setElement(index: Int, ref: ReferenceV) = {
    if (index < 0 || index >= elements.size) {
      throw PanicException();
    }
    elements = elements.updated(index, ref)
  }

  def initializeElement(ref: ReferenceV) = {
    vassert(elements.size < capacity)
    elements = elements :+ ref
  }

  def deinitializeElement() = {
    vassert(elements.nonEmpty)
    val ref = elements.last
    elements = elements.slice(0, elements.size - 1)
    ref
  }

  def getSize() = {
    elements.size
  }
}

case class AllocationId(tyype: RRKind, num: Int) {
  override def hashCode(): Int = num
}

case class ReferenceV(
  // actualType and seenAsType will be different in the case of interface reference.
  // Otherwise they'll be the same.

  // What is the actual type of what we're pointing to (as opposed to an interface).
  // If we have a Car reference to a Civic, then this will be Civic.
  actualKind: RRKind,
  // What do we see the type as. If we have a Car reference to a Civic, then this will be Car.
  seenAsKind: RRKind,

  ownership: OwnershipH,

  location: LocationH,
  permission: PermissionH,

  // Negative number means it's an empty struct (like void).
  num: Int
) {
  def allocId = AllocationId(RRKind(actualKind.hamut), num)
  val actualCoord: RRReference = RRReference(ReferenceH(ownership, location, permission, actualKind.hamut))
  val seenAsCoord: RRReference = RRReference(ReferenceH(ownership, location, permission, seenAsKind.hamut))
}

sealed trait IObjectReferrer {
  def ownership: OwnershipH
}
case class VariableToObjectReferrer(varAddr: VariableAddressV, ownership: OwnershipH) extends IObjectReferrer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class MemberToObjectReferrer(memberAddr: MemberAddressV, ownership: OwnershipH) extends IObjectReferrer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ElementToObjectReferrer(elementAddr: ElementAddressV, ownership: OwnershipH) extends IObjectReferrer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class RegisterToObjectReferrer(callId: CallId, ownership: OwnershipH) extends IObjectReferrer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
// This is us holding onto something during a while loop or array generator call, so the called functions dont eat them and deallocate them
case class RegisterHoldToObjectReferrer(expressionId: ExpressionId, ownership: OwnershipH) extends IObjectReferrer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ResultToObjectReferrer(callId: CallId) extends IObjectReferrer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ArgumentToObjectReferrer(argumentId: ArgumentId, ownership: OwnershipH) extends IObjectReferrer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class VariableAddressV(callId: CallId, local: Local) {
  override def toString: String = "*v:" + callId + "#v" + local.id.number
}
case class MemberAddressV(structId: AllocationId, fieldIndex: Int) {
  override def toString: String = "*o:" + structId.num + "." + fieldIndex
}
case class ElementAddressV(arrayId: AllocationId, elementIndex: Int) {
  override def toString: String = "*o:" + arrayId.num + "." + elementIndex
}

// Used in tracking reference counts/maps.
case class CallId(callDepth: Int, function: PrototypeH) {
  override def toString: String = "ƒ" + callDepth + "/" + (function.fullName.readableName + "_" + function.fullName.id)
  override def hashCode(): Int = callDepth + function.fullName.id
}
//case class RegisterId(blockId: BlockId, lineInBlock: Int) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ArgumentId(callId: CallId, index: Int) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class VariableV(
    id: VariableAddressV,
    var reference: ReferenceV,
    expectedType: ReferenceH[KindH]) {
  vassert(reference != None)
}

case class ExpressionId(
  callId: CallId,
  path: Vector[Int]
) {
  def addStep(i: Int): ExpressionId = ExpressionId(callId, path :+ i)
}

sealed trait RegisterV {
  def expectReferenceRegister() = {
    this match {
      case rr @ ReferenceRegisterV(reference) => {
        rr
      }
    }
  }
}
case class ReferenceRegisterV(reference: ReferenceV) extends RegisterV { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }


case class VivemPanic(message: String) extends Exception