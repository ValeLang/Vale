package net.verdagon.vale.vivem

import java.io.PrintStream

import net.verdagon.vale.metal._
import net.verdagon.vale.vassertSome
import net.verdagon.vale.vivem.ExpressionVivem

//import net.verdagon.vale.hammer._
//import net.verdagon.vale.scout.RefCountCategory
//import net.verdagon.vale.templar.types.{Ownership, Raw, Share}
import net.verdagon.vale.{vassert, vcurious, vfail, vimpl}
import net.verdagon.von._

import scala.collection.mutable

class AdapterForExterns(
    val programH: ProgramH,
    private val heap: Heap,
    callId: CallId,
    val stdin: (() => String),
    val stdout: (String => Unit)
) {
  def dereference(reference: ReferenceV) = {
    heap.dereference(reference)
  }

  def addAllocationForReturn(ownership: OwnershipH, location: LocationH, referend: ReferendV): ReferenceV = {
    val ref = heap.add(ownership, location, referend)
//    heap.incrementReferenceRefCount(ResultToObjectReferrer(callId), ref) // incrementing because putting it in a return
//    ReturnV(callId, ref)
    ref
  }

  def makeVoid(): ReferenceV = {
    val emptyPackStructRefH = ProgramH.emptyTupleStructRef
    val emptyPackStructDefH = vassertSome(programH.structs.find(_.getRef == emptyPackStructRefH))
    heap.newStruct(emptyPackStructDefH, ReferenceH(ShareH, InlineH, emptyPackStructRefH), List())
  }
}

class AllocationMap(vivemDout: PrintStream) {
  private val objectsById = mutable.HashMap[AllocationId, Allocation]()
  private val voidishIds = mutable.HashMap[ReferendH, AllocationId]()

  private var nextId = 501;
  private def newId() = {
    val id = nextId;
    nextId = nextId + 1
    id
  }

  def isEmpty: Boolean = {
    objectsById.isEmpty
  }

  def size = {
    objectsById.size
  }

  def get(allocId: AllocationId) = {
    val allocation = vassertSome(objectsById.get(allocId))
    vassert(allocation.referend.tyype == allocId.tyype)
    allocation
  }

  def remove(allocId: AllocationId): Unit = {
    if (voidishIds.contains(allocId.tyype.hamut)) {
      return;
    }
    vassert(contains(allocId))
    objectsById.remove(allocId)
  }

  def contains(allocId: AllocationId): Boolean = {
    objectsById.get(allocId) match {
      case None => false
      case Some(allocation) => {
        vassert(allocation.referend.tyype.hamut == allocId.tyype.hamut)
        true
      }
    }
  }

  def add(ownership: OwnershipH, location: LocationH, referend: ReferendV) = {
    val shouldIntern =
      referend match {
        case StructInstanceV(structH, members) if structH.mutability == Immutable && members.get.isEmpty => true
        case _ => false
      }
    if (shouldIntern) {
      voidishIds.get(referend.tyype.hamut) match {
        case Some(allocId) => get(allocId)
        case None => // continue
      }
    }

    val reference =
      ReferenceV(
        // These two are the same because when we allocate something,
        // we see it for what it truly is.
        //                                          ~ Wisdom ~
        actualKind = referend.tyype,
        seenAsKind = referend.tyype,
        ownership,
        location,
        newId())
    val allocation = new Allocation(reference, referend)
    objectsById.put(reference.allocId, allocation)
    if (shouldIntern) {
      voidishIds.put(referend.tyype.hamut, reference.allocId)
    }
    reference
  }

  def printAll(): Unit = {
    objectsById.foreach({
      case (id, allocation) => vivemDout.println(id + " (" + allocation.getTotalRefCount(None) + " refs) = " + allocation.referend)
    })
  }

  def checkForLeaks(): Unit = {
    val nonInternedObjects =
      objectsById
        .values
        .filter(value => !voidishIds.contains(value.referend.tyype.hamut))
    if (nonInternedObjects.nonEmpty) {
      nonInternedObjects
        .map(_.reference.allocId.num)
        .toArray
        .sorted
        .foreach(objId => print("o" + objId + " "))
      println()
      nonInternedObjects.toArray.sortWith(_.reference.allocId.num < _.reference.allocId.num).foreach(_.printRefs())
      vfail("Memory leaks! See above for ")
    }
  }
}

// Just keeps track of all active objects
class Heap(in_vivemDout: PrintStream) {
  val vivemDout = in_vivemDout

  /*private*/ val objectsById = new AllocationMap(vivemDout)
  
  private val callIdStack = mutable.Stack[CallId]();
  private val callsById = mutable.HashMap[CallId, Call]()

//  def get(reference: ReferenceV) = {
//    val allocation = objectsById(reference.allocId)
//    vassert(allocation.referend.tyype.hamut == reference.actualKind.hamut)
//    allocation
//  }

  def addLocal(varAddr: VariableAddressV, reference: ReferenceV, expectedType: ReferenceH[ReferendH]) = {
    val call = getCurrentCall(varAddr.callId)
    call.addLocal(varAddr, reference, expectedType)
    incrementReferenceRefCount(VariableToObjectReferrer(varAddr, expectedType.ownership), reference)
  }

  def getReference(varAddr: VariableAddressV, expectedType: ReferenceH[ReferendH]) = {
    callsById(varAddr.callId).getLocal(varAddr).reference
  }

  def removeLocal(varAddr: VariableAddressV, expectedType: ReferenceH[ReferendH]) = {
    val call = getCurrentCall(varAddr.callId)
    val variable = getLocal(varAddr)
    val actualReference = variable.reference
    checkReference(expectedType, actualReference)
    decrementReferenceRefCount(VariableToObjectReferrer(varAddr, expectedType.ownership), actualReference)
    call.removeLocal(varAddr)
  }

  def getReferenceFromLocal(
      varAddr: VariableAddressV,
      expectedType: ReferenceH[ReferendH],
      targetType: ReferenceH[ReferendH]
  ): ReferenceV = {
    val variable = getLocal(varAddr)
    if (variable.expectedType != expectedType) {
      vfail("blort")
    }
    checkReference(expectedType, variable.reference)
    alias(variable.reference, expectedType, targetType)
  }

  private def getLocal(varAddr: VariableAddressV): VariableV = {
    callsById(varAddr.callId).getLocal(varAddr)
  }

  def mutateVariable(varAddress: VariableAddressV, reference: ReferenceV, expectedType: ReferenceH[ReferendH]): ReferenceV = {
    val variable = callsById(varAddress.callId).getLocal(varAddress)
    checkReference(expectedType, reference)
    checkReference(variable.expectedType, reference)
    val oldReference = variable.reference
    decrementReferenceRefCount(VariableToObjectReferrer(varAddress, expectedType.ownership), oldReference)

    incrementReferenceRefCount(VariableToObjectReferrer(varAddress, expectedType.ownership), reference)
    callsById(varAddress.callId).mutateLocal(varAddress, reference, expectedType)
    oldReference
  }
  def mutateArray(elementAddress: ElementAddressV, reference: ReferenceV, expectedType: ReferenceH[ReferendH]): ReferenceV = {
    val ElementAddressV(arrayRef, elementIndex) = elementAddress
    objectsById.get(arrayRef).referend match {
      case ai @ ArrayInstanceV(_, _, _, _) => {
        val oldReference = ai.getElement(elementIndex)
        decrementReferenceRefCount(ElementToObjectReferrer(elementAddress, expectedType.ownership), oldReference)

        ai.setElement(elementIndex, reference)
        incrementReferenceRefCount(ElementToObjectReferrer(elementAddress, expectedType.ownership), reference)
        oldReference
      }
    }
  }
  def mutateStruct(memberAddress: MemberAddressV, reference: ReferenceV, expectedType: ReferenceH[ReferendH]):
  ReferenceV = {
    val MemberAddressV(objectId, fieldIndex) = memberAddress
    objectsById.get(objectId).referend match {
      case si @ StructInstanceV(structDefH, Some(members)) => {
        val oldMemberReference = members(fieldIndex)
        decrementReferenceRefCount(MemberToObjectReferrer(memberAddress, expectedType.ownership), oldMemberReference)
//        maybeDeallocate(actualReference)
        vassert(structDefH.members(fieldIndex).tyype == expectedType)
        // We only do this to check that it's non-empty. curiosity assert, do we gotta do somethin special if somethin was moved out
        si.getReferenceMember(fieldIndex)
        si.setReferenceMember(fieldIndex, reference)
        incrementReferenceRefCount(MemberToObjectReferrer(memberAddress, expectedType.ownership), reference)
        oldMemberReference
      }
    }
  }
//
//  def blacklistElement(elementAddress: ElementAddressV, expectedType: ReferenceH[ReferendH]): Unit = {
//    objectsById.get(elementAddress.arrayId).referend match {
//      case ai @ ArrayInstanceV(_, _, _) => {
//        val ref = ai.getElement(elementAddress.elementIndex)
//        checkReference(expectedType, ref)
//        decrementReferenceRefCount(
//          ElementToObjectReferrer(elementAddress),
//          ref)
//        ai.blacklistElement(elementAddress.elementIndex)
//      }
//    }
//  }

  def getReferenceFromStruct(
    address: MemberAddressV,
    expectedType: ReferenceH[ReferendH],
    targetType: ReferenceH[ReferendH]
  ): ReferenceV = {
    val MemberAddressV(objectId, fieldIndex) = address
    objectsById.get(objectId).referend match {
      case StructInstanceV(_, Some(members)) => {
        val actualReference = members(fieldIndex)
        checkReference(expectedType, actualReference)
        alias(actualReference, expectedType, targetType)
      }
    }
  }
  def getReferenceFromArray(
    address: ElementAddressV,
    expectedType: ReferenceH[ReferendH],
    targetType: ReferenceH[ReferendH]
  ): ReferenceV = {
    val ElementAddressV(objectId, elementIndex) = address
    objectsById.get(objectId).referend match {
      case ai @ ArrayInstanceV(_, _, _, _) => {
        val ref = ai.getElement(elementIndex)
        checkReference(expectedType, ref)
        alias(ref, expectedType, targetType)
      }
    }
  }

  def containsLiveObject(reference: ReferenceV): Boolean = {
    containsLiveObject(reference.allocId)
  }

  private def containsLiveObject(allocId: AllocationId): Boolean = {
    if (!objectsById.contains(allocId))
      return false
    val alloc = objectsById.get(allocId)
    alloc.referend match {
      case StructInstanceV(_, None) => false
      case StructInstanceV(_, Some(_)) => true
      case _ => true
    }
  }

  // Undead meaning it's been zero'd out, but its still allocated because a weak
  // is pointing at it.
  private def containsLiveOrUndeadObject(allocId: AllocationId): Boolean = {
    objectsById.contains(allocId)
  }

  // Undead meaning it's been zero'd out, but its still allocated because a weak
  // is pointing at it.
  def dereference(reference: ReferenceV, allowUndead: Boolean = false): ReferendV = {
    if (!allowUndead) {
      vassert(containsLiveObject(reference.allocId))
    }
    objectsById.get(reference.allocId).referend
  }

  def isSameInstance(callId: CallId, left: ReferenceV, right: ReferenceV): ReferenceV = {
    val ref = allocateTransient(ShareH, InlineH, BoolV(left.allocId == right.allocId))
    incrementReferenceRefCount(RegisterToObjectReferrer(callId, ShareH), ref)
    ref
  }

  def incrementReferenceHoldCount(expressionId: ExpressionId, reference: ReferenceV) = {
    incrementObjectRefCount(RegisterHoldToObjectReferrer(expressionId, reference.ownership), reference.allocId)
  }

  def decrementReferenceHoldCount(expressionId: ExpressionId, reference: ReferenceV) = {
    decrementObjectRefCount(RegisterHoldToObjectReferrer(expressionId, reference.ownership), reference.allocId)
  }

  // rename to incrementObjectRefCount
  def incrementReferenceRefCount(referrer: IObjectReferrer, reference: ReferenceV) = {
    incrementObjectRefCount(referrer, reference.allocId, reference.ownership == WeakH)
  }

  // rename to decrementObjectRefCount
  def decrementReferenceRefCount(referrer: IObjectReferrer, reference: ReferenceV) = {
    decrementObjectRefCount(referrer, reference.allocId, reference.ownership == WeakH)
  }

  def destructureArray(reference: ReferenceV): Vector[ReferenceV] = {
    val allocation = dereference(reference)
    allocation match {
      case ArrayInstanceV(typeH, elementTypeH, size, elements) => {
        val elementRefs =
          elements.indices.toVector
            .reverse
            .map(index => deinitializeArrayElement(reference, index))
            .reverse
        elementRefs
      }
    }
  }

  def destructure(reference: ReferenceV): Vector[ReferenceV] = {
    val allocation = dereference(reference)
    allocation match {
      case StructInstanceV(structDefH, Some(memberRefs)) => {
        memberRefs.zipWithIndex.foreach({ case (memberRef, index) =>
          decrementReferenceRefCount(
            MemberToObjectReferrer(
              MemberAddressV(reference.allocId, index), memberRef.ownership),
            memberRef)
        })

        zero(reference)
        deallocateIfNoWeakRefs(reference)
        memberRefs
      }
    }
  }

  // This is *conceptually* destroying the object, this is when the owning
  // reference disappears. If there are no weak refs, we also deallocate.
  // Otherwise, we deallocate when the last weak ref disappears.
  def zero(reference: ReferenceV) = {
    val allocation = objectsById.get(reference.allocId)
    vassert(allocation.getTotalRefCount(Some(OwnH)) == 0)
    vassert(allocation.getTotalRefCount(Some(BorrowH)) == 0)
    allocation.referend match {
      case si @ StructInstanceV(_, _) => si.zero()
      case _ =>
    }
    vivemDout.print(" o" + reference.allocId.num + "zero")
  }

  // This happens when the last owning and weak references disappear.
  def deallocateIfNoWeakRefs(reference: ReferenceV) = {
    val allocation = objectsById.get(reference.allocId)
    if (allocation.getTotalRefCount(None) == 0) {
      objectsById.remove(reference.allocId)
      vivemDout.print(" o" + reference.allocId.num + "dealloc")
    }
  }

  private def incrementObjectRefCount(
      pointingFrom: IObjectReferrer,
      allocId: AllocationId,
      allowUndead: Boolean = false) = {
    if (!allowUndead) {
      if (!containsLiveObject(allocId)) {
        vfail("Trying to increment dead object: " + allocId)
      }
    }
    val obj = objectsById.get(allocId)
    obj.incrementRefCount(pointingFrom)
    val newRefCount = obj.getTotalRefCount(None)
    vivemDout.print(" o" + allocId.num + "rc" + (newRefCount - 1) + "->" + newRefCount)
  }

  private def decrementObjectRefCount(
      pointedFrom: IObjectReferrer,
      allocId: AllocationId,
      allowUndead: Boolean = false):
  Int = {
    if (!allowUndead) {
      if (!containsLiveObject(allocId)) {
        vfail("Can't decrement object " + allocId + ", not in heap!")
      }
    }
    val obj = objectsById.get(allocId)
    obj.decrementRefCount(pointedFrom)
    val newRefCount = obj.getTotalRefCount(None)
    vivemDout.print(" o" + allocId.num + "rc" + (newRefCount + 1) + "->" + newRefCount)
//    if (newRefCount == 0) {
//      deallocate(objectId)
//    }
    newRefCount
  }

  def getRefCount(reference: ReferenceV, category: RefCountCategory): Int = {
    if (reference.ownership == WeakH) {
      vassert(containsLiveOrUndeadObject(reference.allocId))
    } else {
      vassert(containsLiveObject(reference.allocId))
    }
    val allocation = objectsById.get(reference.allocId)
    allocation.getRefCount(category)
  }

  def getTotalRefCount(reference: ReferenceV): Int = {
    if (reference.ownership == WeakH) {
      vassert(containsLiveOrUndeadObject(reference.allocId))
    } else {
      vassert(containsLiveObject(reference.allocId))
    }
    val allocation = objectsById.get(reference.allocId)
    allocation.getTotalRefCount(None)
  }

  def ensureRefCount(reference: ReferenceV, categoryFilter: Option[RefCountCategory], ownershipFilter: Option[Set[OwnershipH]], expectedNum: Int) = {
    vassert(containsLiveObject(reference.allocId))
    val allocation = objectsById.get(reference.allocId)
    allocation.ensureRefCount(categoryFilter, ownershipFilter, expectedNum)
  }

  def add(ownership: OwnershipH, location: LocationH, referend: ReferendV): ReferenceV = {
    objectsById.add(ownership, location, referend)
  }

  def alias(
    reference: ReferenceV,
    expectedType: ReferenceH[ReferendH],
    targetType: ReferenceH[ReferendH]):
  ReferenceV = {
    if (expectedType.ownership == targetType.ownership) {
      return reference
    }

    val ReferenceV(actualKind, oldSeenAsType, oldOwnership, oldLocation, objectId) = reference
    vassert((oldOwnership == ShareH) == (targetType.ownership == ShareH))
    if (oldSeenAsType.hamut != expectedType.kind) {
      // not sure if the above .actualType is right

      vfail("wot")
    }
    ReferenceV(
      actualKind,
      RRReferend(expectedType.kind),
      targetType.ownership,
      targetType.location,
      objectId)
  }

  def isEmpty: Boolean = {
    objectsById.isEmpty
  }

  def printAll() = {
    objectsById.printAll()
  }

  def countUnreachableAllocations(roots: Vector[ReferenceV]) = {
    val numReachables = findReachableAllocations(roots).size
    vassert(numReachables <= objectsById.size)
    objectsById.size - numReachables
  }

  def findReachableAllocations(
      inputReachables: Vector[ReferenceV]): Map[ReferenceV, Allocation] = {
    val destinationMap = mutable.Map[ReferenceV, Allocation]()
    inputReachables.foreach(inputReachable => {
      innerFindReachableAllocations(destinationMap, inputReachable)
    })
    destinationMap.toMap
  }

  private def innerFindReachableAllocations(
      destinationMap: mutable.Map[ReferenceV, Allocation],
      inputReachable: ReferenceV): Unit = {
    // Doublecheck that all the inputReachables are actually in this ..
    vassert(containsLiveObject(inputReachable.allocId))
    vassert(objectsById.get(inputReachable.allocId).referend.tyype.hamut == inputReachable.actualKind.hamut)

    val allocation = objectsById.get(inputReachable.allocId)
    if (destinationMap.contains(inputReachable)) {
      return
    }

    destinationMap.put(inputReachable, allocation)
    allocation.referend match {
      case IntV(_) =>
      case BoolV(_) =>
      case FloatV(_) =>
      case StructInstanceV(structDefH, Some(members)) => {
        members.zip(structDefH.members).foreach({
          case (reference, StructMemberH(_, _, referenceH)) => {
            innerFindReachableAllocations(destinationMap, reference)
          }
        })
      }
    }
  }

  def checkForLeaks(): Unit = {
    objectsById.checkForLeaks()
  }

  def getCurrentCall(expectedCallId: CallId) = {
    vassert(callIdStack.top == expectedCallId)
    callsById(expectedCallId)
  }

  def takeArgument(callId: CallId, argumentIndex: Int, expectedType: ReferenceH[ReferendH]) = {
    val reference = getCurrentCall(callId).takeArgument(argumentIndex)
    checkReference(expectedType, reference)
    decrementReferenceRefCount(
      ArgumentToObjectReferrer(ArgumentId(callId, argumentIndex), expectedType.ownership),
      reference) // decrementing because taking it out of arg
    // Now, the register is the only one that has this reference.
    reference
  }

//  def returnFromRegister(expressionId: ExpressionId, expectedType: ReferenceH[ReferendH]) = {
//    val ref = takeReferenceFromRegister(expressionId, expectedType)
//    incrementReferenceRefCount(
//      ResultToObjectReferrer(expressionId.callId),
//      ref) // incrementing because putting it into the return slot
//    ReturnV(expressionId.blockId, ref)
//  }

  // For example, for the integer we pass into the array generator
  def allocateTransient(ownership: OwnershipH, location: LocationH, referend: ReferendV) = {
    val ref = add(ownership, location, referend)
    vivemDout.print(" o" + ref.allocId.num + "=")
    printReferend(referend)
    ref
  }

//  def aliasIntoRegister(expressionId: ExpressionId, reference: ReferenceV, expectedType: ReferenceH[ReferendH], targetOwnership: OwnershipH) = {
//    val ref = alias(reference, expectedType, targetOwnership)
//    setReferenceRegister(expressionId, ref)
//  }

  def printReferend(referend: ReferendV) = {
    referend match {
      case IntV(value) => vivemDout.print(value)
      case BoolV(value) => vivemDout.print(value)
      case StrV(value) => vivemDout.print(value)
      case FloatV(value) => vivemDout.print(value)
      case StructInstanceV(structH, Some(members)) => {
        vivemDout.print(structH.fullName + "{" + members.map("o" + _.allocId.num).mkString(", ") + "}")
      }
      case ArrayInstanceV(typeH, memberTypeH, size, elements) => vivemDout.print("array:" + size + ":" + memberTypeH + "{" + elements.map("o" + _.allocId.num).mkString(", ") + "}")
    }
  }

//  def setReferenceRegister(expressionId: ExpressionId, reference: ReferenceV) = {
//    val call = getCurrentCall(expressionId.callId)
//    incrementReferenceRefCount(RegisterToObjectReferrer(expressionId), reference) // incrementing because putting it into a register
//    call.setRegister(expressionId, ReferenceRegisterV(reference))
//    vivemDout.print(" r" + expressionId.line + "<-o" + reference.allocId.num)
//  }
//
//  def setReferenceRegisterFromReturn(expressionId: ExpressionId, ret: ReturnV) = {
//    incrementReferenceRefCount(RegisterToObjectReferrer(expressionId), ret.reference)
//    decrementReferenceRefCount(ResultToObjectReferrer(ret.blockId.callId), ret.reference)
//    getCurrentCall(expressionId.callId)
//      .setRegister(expressionId, ReferenceRegisterV(ret.reference))
//    vivemDout.print(" r" + expressionId.line + "<-o" + ret.reference.allocId.num)
//  }

//  def getReferenceFromReturn(ret: ReturnV) = {
//    decrementReferenceRefCount(ResultToObjectReferrer(ret.zorkcallId), ret.zorkreference)
//    ret.zorkreference
//  }


//  def deallocateFromReturn(ret: ReturnV) = {
//    decrementReferenceRefCount(ResultToObjectReferrer(ret.callId), ret.reference)
//    deallocate(ret.reference)
//  }

  def initializeArrayElement(
      arrayReference: ReferenceV,
      index: Int,
      ret: ReferenceV) = {
    dereference(arrayReference) match {
      case a @ ArrayInstanceV(_, _, _, _) => {
        incrementReferenceRefCount(
          ElementToObjectReferrer(
            ElementAddressV(arrayReference.allocId, index), a.elementTypeH.ownership),
          ret)
        a.initializeElement(index, ret)
      }
    }
  }

  def newStruct(
      structDefH: StructDefinitionH,
      structRefH: ReferenceH[StructRefH],
      memberReferences: List[ReferenceV]):
  ReferenceV = {
    val instance = StructInstanceV(structDefH, Some(memberReferences.toVector))
    val reference = add(structRefH.ownership, structRefH.location, instance)

    memberReferences.zipWithIndex.foreach({ case (memberReference, index) =>
      incrementReferenceRefCount(
        MemberToObjectReferrer(MemberAddressV(reference.allocId, index), memberReference.ownership),
        memberReference)
    })

    vivemDout.print(" o" + reference.num + "=")
    printReferend(instance)
    reference
  }

  def deinitializeArrayElement(arrayReference: ReferenceV, index: Int) = {
    val arrayInstance @ ArrayInstanceV(_, _, _, _) = dereference(arrayReference)
    val elementReference = arrayInstance.deinitializeElement(index)
    decrementReferenceRefCount(
      ElementToObjectReferrer(ElementAddressV(arrayReference.allocId, index), elementReference.ownership),
      elementReference)
    elementReference
  }

  def initializeArrayElementFromRegister(
      arrayReference: ReferenceV,
      index: Int,
      elementReference: ReferenceV) = {
    val arrayInstance @ ArrayInstanceV(_, _, _, _) = dereference(arrayReference)
    incrementReferenceRefCount(
      ElementToObjectReferrer(ElementAddressV(arrayReference.allocId, index), elementReference.ownership),
      elementReference)
    arrayInstance.initializeElement(index, elementReference)
  }

//  def discardReturn(ret: ReturnV) = {
//    decrementReferenceRefCount(ResultToObjectReferrer(ret.zorkcallId), ret.zorkreference)
////    maybeDeallocate(ret.reference.allocId)
//  }
//
//  def takeReferenceFromRegister(expressionId: ExpressionId, expectedType: ReferenceH[ReferendH]) = {
//    val register = getCurrentCall(expressionId.callId).takeRegister(expressionId)
//    val ref = checkReferenceRegister(expectedType, register).reference
//    decrementReferenceRefCount(RegisterToObjectReferrer(expressionId), ref)
//    ref
//  }
//
//  def takeReferencesFromRegistersInReverse(blockId: BlockId, expressionIds: List[RegisterAccessH[ReferendH]]): List[ReferenceV] = {
//    expressionIds
//        .reverse
//        .map({
//          case RegisterAccessH(argRegisterId, expectedType) => {
//            takeReferenceFromRegister(RegisterId(blockId, argRegisterId), expectedType)
//          }
//        })
//        .reverse
//  }
//
//  def allocateIntoRegister(
//    expressionId: ExpressionId,
//    ownership: OwnershipH,
//    referend: ReferendV
//  ): ReferenceV = {
//    val ref = add(ownership, referend)
//    vivemDout.print(" o" + ref.allocId.num + "=")
//    printReferend(referend)
//    setReferenceRegister(expressionId, ref)
//    ref
//  }

  def addUninitializedArray(
      arrayRefType: ReferenceH[UnknownSizeArrayTH],
      size: Int):
  (ReferenceV, ArrayInstanceV) = {
    val instance = ArrayInstanceV(arrayRefType, arrayRefType.kind.rawArray.elementType, size, Vector())
    val reference = add(arrayRefType.ownership, arrayRefType.location, instance)
    (reference, instance)
  }

  def addArray(
    arrayRefType: ReferenceH[KnownSizeArrayTH],
    memberRefs: List[ReferenceV]):
  (ReferenceV, ArrayInstanceV) = {
    val instance = ArrayInstanceV(arrayRefType, arrayRefType.kind.rawArray.elementType, memberRefs.size, memberRefs.toVector)
    val reference = add(arrayRefType.ownership, arrayRefType.location, instance)
    memberRefs.zipWithIndex.foreach({ case (memberRef, index) =>
      incrementReferenceRefCount(
        ElementToObjectReferrer(ElementAddressV(reference.allocId, index), memberRef.ownership),
        memberRef)
    })
    (reference, instance)
  }


  def checkReference(expectedType: ReferenceH[ReferendH], actualReference: ReferenceV): Unit = {
    if (actualReference.ownership == WeakH) {
      vassert(containsLiveOrUndeadObject(actualReference.allocId))
    } else {
      vassert(containsLiveObject(actualReference.allocId))
    }
    if (actualReference.seenAsCoord.hamut != expectedType) {
      vfail("Expected " + expectedType + " but was " + actualReference.seenAsCoord.hamut)
    }
    val actualReferend = dereference(actualReference, actualReference.ownership == WeakH)
    checkReferend(expectedType.kind, actualReferend)
  }


  def checkReferenceRegister(tyype: ReferenceH[ReferendH], register: RegisterV): ReferenceRegisterV = {
    val reg = register.expectReferenceRegister()
    checkReference(tyype, reg.reference)
    reg
  }

  def checkReferend(expectedType: ReferendH, actualReferend: ReferendV): Unit = {
    (actualReferend, expectedType) match {
      case (IntV(_), IntH()) =>
      case (BoolV(_), BoolH()) =>
      case (StrV(_), StrH()) =>
      case (FloatV(_), FloatH()) =>
      case (StructInstanceV(structDefH, _), structRefH @ StructRefH(_)) => {
        if (structDefH.getRef != structRefH) {
          vfail("Expected " + structRefH + " but was " + structDefH)
        }
      }
      case (ArrayInstanceV(typeH, actualElementTypeH, _, _), arrayH @ UnknownSizeArrayTH(_, _)) => {
        if (typeH.kind != arrayH) {
          vfail("Expected " + arrayH + " but was " + typeH)
        }
      }
      case (ArrayInstanceV(typeH, actualElementTypeH, _, _), arrayH @ KnownSizeArrayTH(_, _, _)) => {
        if (typeH.kind != arrayH) {
          vfail("Expected " + arrayH + " but was " + typeH)
        }
      }
      case (StructInstanceV(structDefH, _), irH @ InterfaceRefH(_)) => {
        val structImplementsInterface =
          structDefH.edges.exists(_.interface == irH)
        if (!structImplementsInterface) {
          vfail("Struct " + structDefH.getRef + " doesnt implement interface " + irH);
        }
      }
      case (a, b) => {
        vfail("Mismatch! " + a + " is not a " + b)
      }
    }
  }

  def checkStructId(expectedStructType: StructRefH, expectedStructPointerType: ReferenceH[ReferendH], register: RegisterV): AllocationId = {
    val reference = checkReferenceRegister(expectedStructPointerType, register).reference
    dereference(reference) match {
      case siv @ StructInstanceV(structDefH, _) => {
        vassert(structDefH.getRef == expectedStructType)
      }
      case _ => vfail("Expected a struct but was " + register)
    }
    reference.allocId
  }

  def checkStructReference(expectedStructType: StructRefH, expectedStructPointerType: ReferenceH[ReferendH], register: RegisterV): StructInstanceV = {
    val reference = checkReferenceRegister(expectedStructPointerType, register).reference
    dereference(reference) match {
      case siv @ StructInstanceV(structDefH, _) => {
        vassert(structDefH.getRef == expectedStructType)
        siv
      }
      case _ => vfail("Expected a struct but was " + register)
    }
  }

  def checkStructReference(expectedStructType: StructRefH, reference: ReferenceV): StructInstanceV = {
    dereference(reference) match {
      case siv @ StructInstanceV(structDefH, _) => {
        vassert(structDefH.getRef == expectedStructType)
        siv
      }
      case _ => vfail("Expected a struct but was " + reference)
    }
  }

  def pushNewStackFrame(functionH: PrototypeH, args: Vector[ReferenceV]) = {
    vassert(callsById.size == callIdStack.size)
    val callId =
      CallId(
        if (callIdStack.nonEmpty) callIdStack.top.callDepth + 1 else 0,
        functionH)
    val call = new Call(callId, args)
    callsById.put(callId, call)
    callIdStack.push(callId)
    vassert(callsById.size == callIdStack.size)
    callId
  }

  def popStackFrame(expectedCallId: CallId): Unit = {
    vassert(callsById.size == callIdStack.size)
    vassert(callIdStack.top == expectedCallId)
    val call = callsById(expectedCallId)
    call.prepareToDie()
    callIdStack.pop()
    callsById.remove(expectedCallId)
    vassert(callsById.size == callIdStack.size)
  }

//  def pushNewBlock(callId: CallId): BlockId = {
//    vassert(callsById.size == callIdStack.size)
//    getCurrentCall(callId).pushNewBlock()
//  }
//
//  def popBlock(blockId: BlockId): Unit = {
//    vassert(callsById.size == callIdStack.size)
//    getCurrentCall(blockId.callId).popBlock(blockId)
//    vassert(callsById.size == callIdStack.size)
//  }

  def toVon(ref: ReferenceV): IVonData = {
    dereference(ref) match {
      case IntV(value) => VonInt(value)
      case FloatV(value) => VonFloat(value)
      case BoolV(value) => VonBool(value)
      case StrV(value) => VonStr(value)
      case ArrayInstanceV(typeH, elementTypeH, size, elements) => {
        VonArray(None, elements.map(toVon))
      }
      case StructInstanceV(structH, Some(members)) => {
        vassert(members.size == structH.members.size)
        VonObject(
          structH.fullName.toString,
          None,
          structH.members.zip(members).zipWithIndex.map({ case ((memberH, memberV), index) =>
            VonMember(vimpl(memberH.name.toString), toVon(memberV))
          }).toVector)
      }
    }
  }

  def getVarAddress(callId: CallId, local: Local) = {
    VariableAddressV(callId, local)
  }
}
