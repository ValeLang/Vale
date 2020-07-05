package net.verdagon.vale.vivem

//import net.verdagon.vale.hammer._
//import net.verdagon.vale.scout.{MemberRefCount, RefCountCategory, RegisterRefCount, VariableRefCount}
//import net.verdagon.vale.templar.types.Ownership
import net.verdagon.vale.metal._
import net.verdagon.vale.{vassert, vfail}

// RR = Runtime Result. Don't use these to determine behavior, just use
// these to check that things are as we expect.
case class RRReference(hamut: ReferenceH[ReferendH])
case class RRReferend(hamut: ReferendH)

class Allocation(
    val reference: ReferenceV, // note that this cannot change
    val referend: ReferendV // note that this cannot change
) {
  private var referrers = Map[IObjectReferrer, Int]()

  def id = reference.allocId

  def incrementRefCount(referrer: IObjectReferrer) = {
    referrer match {
      case RegisterToObjectReferrer(_) => {
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

  def decrementRefCount(referrer: IObjectReferrer) = {
    if (!referrers.contains(referrer)) {
      vfail("nooooo\n" + referrer + "\nnot in:\n" + referrers)
    }
    referrers = referrers + (referrer -> (referrers(referrer) - 1))
    if (referrers(referrer) == 0) {
      referrers = referrers - referrer
      vassert(!referrers.contains(referrer))
    }
  }

  private def getCategory(referrer: IObjectReferrer) = {
    referrer match {
      case VariableToObjectReferrer(_) => VariableRefCount
      case MemberToObjectReferrer(_) => MemberRefCount
      case RegisterToObjectReferrer(_) => RegisterRefCount
      case ArgumentToObjectReferrer(_) => ArgumentRefCount
    }
  }

  def getRefCount(category: RefCountCategory) = {
    referrers
      .toList
      .filter({ case (key, _) => getCategory(key) == category })
      .map(_._2)
      .sum
  }

  def ensureRefCount(category: RefCountCategory, expectedNum: Int) = {
    val matchingReferrers =
      referrers
        .toList
        .filter({ case (key, _) => getCategory(key) == category })
        .map(_._2)
    if (matchingReferrers.size != expectedNum) {
      vfail(
        "Expected " + expectedNum + " " + category + " but was " + matchingReferrers.size + ":\n" +
        matchingReferrers.mkString("\n"))
    }
  }

  def ensureTotalRefCount(expectedNum: Int) = {
    if (referrers.size != expectedNum) {
      vfail(
        "o" + reference.allocId.num + " expected " + expectedNum + " but was " + referrers.size + ":\n" +
            referrers.mkString("\n") + "\nReferend:\n" + referend)
    }
  }

  def printRefs() = {
    if (getTotalRefCount() > 0) {
      println("o" + reference.allocId.num + ": " + referrers.mkString(" "))
    }
  }

  def getTotalRefCount() = {
    referrers.size
  }

  override def finalize(): Unit = {
//    vassert(referrers.isEmpty)
  }

  def unapply(arg: Allocation): Option[ReferendV] = Some(referend)
}

object Allocation {
  def unapply(arg: Allocation): Option[ReferendV] = {
    Some(arg.referend)
  }
}

sealed trait ReferendV {
  def tyype: RRReferend
}
sealed trait PrimitiveReferendV extends ReferendV
case class IntV(value: Int) extends PrimitiveReferendV {
  override def tyype = RRReferend(IntH())
}
case class BoolV(value: Boolean) extends PrimitiveReferendV {
  override def tyype = RRReferend(BoolH())
}
case class FloatV(value: Float) extends PrimitiveReferendV {
  override def tyype = RRReferend(FloatH())
}
case class StrV(value: String) extends PrimitiveReferendV {
  override def tyype = RRReferend(StrH())
}

case class StructInstanceV(
    structH: StructDefinitionH,
    private var members: Vector[ReferenceV]
) extends ReferendV {
  vassert(members.size == structH.members.size)

  override def tyype = RRReferend(structH.getRef)

  def getReferenceMember(index: Int) = {
    (structH.members(index).tyype, members(index)) match {
      case (_, ref) => ref
    }
  }

  def setReferenceMember(index: Int, reference: ReferenceV) = {
    members = members.updated(index, reference)
  }
}

case class ArrayInstanceV(
    typeH: ReferenceH[ReferendH],
    elementTypeH: ReferenceH[ReferendH],
    private val size: Int,
    private var elements: Vector[ReferenceV]
) extends ReferendV {
  override def tyype = RRReferend(typeH.kind)

  def getElement(index: Int): ReferenceV = {
    // Make sure we're initialized
    vassert(elements.size == size)
    if (index < 0 || index >= size) {
      throw PanicException();
    }
    elements(index)
  }

  def setElement(index: Int, ref: ReferenceV) = {
    // Make sure we're initialized
    vassert(elements.size == size)
    if (index < 0 || index >= size) {
      throw PanicException();
    }
    elements = elements.updated(index, ref)
  }

  def initializeElement(index: Int, ref: ReferenceV) = {
    // Make sure we're not yet initialized
    vassert(elements.size < size)
    // Make sure we're initializing the *next* empty slot
    vassert(index == elements.size)
    elements = elements :+ ref
  }

  def deinitializeElement(index: Int) = {
    // Make sure we're initializing the *next* empty slot
    if (index != elements.size - 1) {
      vfail("wot")
    }
    val ref = elements(index)
    elements = elements.slice(0, elements.size - 1)
    ref
  }

  def getSize() = {
    // Make sure we're initialized
    vassert(elements.size == size)
    size
  }
}

case class AllocationId(tyype: RRReferend, num: Int)

case class ReferenceV(
  // actualType and seenAsType will be different in the case of interface reference.
  // Otherwise they'll be the same.

  // What is the actual type of what we're pointing to (as opposed to an interface).
  // If we have a Car reference to a Civic, then this will be Civic.
  actualKind: RRReferend,
  // What do we see the type as. If we have a Car reference to a Civic, then this will be Car.
  seenAsKind: RRReferend,

  ownership: OwnershipH,

  location: LocationH,

  // Negative number means it's an empty struct (like void).
  num: Int
) {
  def allocId = AllocationId(RRReferend(actualKind.hamut), num)
  val actualCoord: RRReference = RRReference(ReferenceH(ownership, location, actualKind.hamut))
  val seenAsCoord: RRReference = RRReference(ReferenceH(ownership, location, seenAsKind.hamut))
}

sealed trait IObjectReferrer
case class VariableToObjectReferrer(varAddr: VariableAddressV) extends IObjectReferrer
case class MemberToObjectReferrer(memberAddr: MemberAddressV) extends IObjectReferrer
case class ElementToObjectReferrer(elementAddr: ElementAddressV) extends IObjectReferrer
case class RegisterToObjectReferrer(callId: CallId) extends IObjectReferrer
// This is us holding onto something during a while loop or array generator call, so the called functions dont eat them and deallocate them
case class RegisterHoldToObjectReferrer(expressionId: ExpressionId) extends IObjectReferrer
//case class ResultToObjectReferrer(callId: CallId) extends IObjectReferrer
case class ArgumentToObjectReferrer(argumentId: ArgumentId) extends IObjectReferrer

case class VariableAddressV(callId: CallId, local: Local) {
  override def toString: String = "&v:" + callId + "#v" + local.id
}
case class MemberAddressV(structId: AllocationId, fieldIndex: Int) {
  override def toString: String = "&o:" + structId.num + "." + fieldIndex
}
case class ElementAddressV(arrayId: AllocationId, elementIndex: Int) {
  override def toString: String = "&o:" + arrayId.num + "." + elementIndex
}

// Used in tracking reference counts/maps.
case class CallId(callDepth: Int, function: PrototypeH) {
  override def toString: String = "ƒ" + callDepth + "/" + function.fullName.toString
}
//case class RegisterId(blockId: BlockId, lineInBlock: Int)
case class ArgumentId(callId: CallId, index: Int)
case class VariableV(
    id: VariableAddressV,
    var reference: ReferenceV,
    expectedType: ReferenceH[ReferendH]) {
  vassert(reference != None)
}

case class ExpressionId(
  callId: CallId,
  path: List[Int]
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
case class ReferenceRegisterV(reference: ReferenceV) extends RegisterV


case class VivemPanic(message: String) extends Exception