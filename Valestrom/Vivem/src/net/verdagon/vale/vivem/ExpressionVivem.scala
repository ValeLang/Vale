package net.verdagon.vale.vivem

import net.verdagon.vale.metal._
import net.verdagon.vale.{Err, PackageCoordinate, Result, vassert, vassertSome, vcurious, vfail, vimpl, vwat, metal => m}

import scala.collection.mutable

object ExpressionVivem {
  // The contained reference has a ResultToObjectReferrer pointing at it.
  // This is so if we do something like [4, 5].0, and that 4 is being
  // returned to the parent node, it's not deallocated from its ref count
  // going to 0.
  sealed trait INodeExecuteResult
  case class NodeContinue(resultRef: ReferenceV) extends INodeExecuteResult { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
  case class NodeReturn(returnRef: ReferenceV) extends INodeExecuteResult { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
  case class NodeBreak() extends INodeExecuteResult { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

  def makePrimitive(heap: Heap, callId: CallId, location: LocationH, kind: KindV) = {
    vassert(kind != VoidV)
    val ref = heap.allocateTransient(ShareH, location, ReadonlyH, kind)
    heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, ShareH), ref)
    ref
  }

  def takeArgument(heap: Heap, callId: CallId, argumentIndex: Int, resultType: ReferenceH[KindH]) = {
    val ref = heap.takeArgument(callId, argumentIndex, resultType)
    heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, resultType.ownership), ref)
    ref
  }

  def possessCalleeReturn(heap: Heap, callId: CallId, calleeCallId: CallId, result: NodeReturn) = {
    heap.decrementReferenceRefCount(RegisterToObjectReferrer(calleeCallId, result.returnRef.ownership), result.returnRef)
    heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, result.returnRef.ownership), result.returnRef)
    result.returnRef
  }

  def upcast(sourceReference: ReferenceV, targetInterfaceRef: InterfaceRefH): ReferenceV = {
    ReferenceV(
      sourceReference.actualKind,
      RRKind(targetInterfaceRef),
      sourceReference.ownership,
      sourceReference.location,
      sourceReference.permission,
      sourceReference.num)
  }

  def executeNode(
    programH: ProgramH,
    stdin: (() => String),
    stdout: (String => Unit),
    heap: Heap,
    expressionId: ExpressionId,
    node: ExpressionH[KindH] // rename to expression
  ): INodeExecuteResult = {
    heap.vivemDout.print("<" + node.getClass.getSimpleName + "> ")
    val result = executeNodeInner(programH, stdin, stdout, heap, expressionId, node)
    heap.vivemDout.println("</" + node.getClass.getSimpleName + ">")
    result
  }

  def executeNodeInner(
                   programH: ProgramH,
                   stdin: (() => String),
                   stdout: (String => Unit),
                   heap: Heap,
                   expressionId: ExpressionId,
                   node: ExpressionH[KindH] // rename to expression
  ): INodeExecuteResult = {
    val callId = expressionId.callId

    node match {
      case DiscardH(sourceExpr) => {
        sourceExpr.resultType.ownership match {
          case ShareH =>
          case PointerH =>
          case BorrowH =>
          case WeakH =>
        }
        val sourceRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        // Lots of instructions do this, not just Discard, see DINSIE.
        discard(programH, heap, stdout, stdin, callId, sourceExpr.resultType, sourceRef)
        NodeContinue(heap.void)
      }
      case ConstantVoidH() => {
        val ref = heap.void
        NodeContinue(ref)
      }
      case ConstantIntH(value, bits) => {
        val ref = makePrimitive(heap, callId, InlineH, IntV(value, bits))
        NodeContinue(ref)
      }
      case ConstantF64H(value) => {
        val ref = makePrimitive(heap, callId, InlineH, FloatV(value))
        NodeContinue(ref)
      }
      case ConstantStrH(value) => {
        val ref = makePrimitive(heap, callId, YonderH, StrV(value))
        NodeContinue(ref)
      }
      case ConstantBoolH(value) => {
        val ref = makePrimitive(heap, callId, InlineH, BoolV(value))
        NodeContinue(ref)
      }
      case ArgumentH(resultType, argumentIndex) => {
        val ref = takeArgument(heap, callId, argumentIndex, resultType)
        NodeContinue(ref)
      }
      case BreakH() => {
        return NodeBreak()
      }
      case ReturnH(sourceExpr) => {
        val sourceRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ NodeReturn(_) => {
              // This can happen if we do for example:
              //   ret if (true) {
              //         ret 7;
              //       } else {
              //         8
              //       };
              return r
            }
            case NodeContinue(r) => r
          }
        return NodeReturn(sourceRef)
      }
      case IsSameInstanceH(leftExpr, rightExpr) => {
        val leftRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), leftExpr) match {
            case r @ NodeReturn(_) => {
              vcurious()
              return r
            }
            case NodeContinue(r) => r
          }
        val rightRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), rightExpr) match {
            case r @ NodeReturn(_) => {
              vcurious()
              return r
            }
            case NodeContinue(r) => r
          }
        discard(programH, heap, stdout, stdin, callId, leftExpr.resultType, leftRef)
        discard(programH, heap, stdout, stdin, callId, rightExpr.resultType, rightRef)

        val ref = heap.isSameInstance(callId, leftRef, rightRef)

        NodeContinue(ref)
      }
      case BlockH(sourceExpr) => {
        executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr)
      }
      case ConsecutorH(innerExprs) => {
        var lastInnerExprResultRef: Option[ReferenceV] = None

        for (i <- innerExprs.indices) {
          val innerExpr = innerExprs(i)

          executeNode(programH, stdin, stdout, heap, expressionId.addStep(i), innerExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(innerExprResultRef) => {
              if (i == innerExprs.size - 1) {
                lastInnerExprResultRef = Some(innerExprResultRef)
              }
            }
          }

          heap.vivemDout.println()
        }

        NodeContinue(vassertSome(lastInnerExprResultRef))
      }
      case DestroyH(structExpr, localTypes, locals) => {
        val structReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), structExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        heap.decrementReferenceRefCount(
          RegisterToObjectReferrer(callId, structExpr.resultType.ownership),
          structReference)

        // DDSOT
        heap.ensureRefCount(structReference, Some(Set(OwnH, PointerH)), 0)

        val oldMemberReferences = heap.destructure(structReference)

        vassert(oldMemberReferences.size == locals.size)
        oldMemberReferences.zip(localTypes).zip(locals).foreach({ case ((memberRef, localType), localIndex) =>
          val varAddr = heap.getVarAddress(expressionId.callId, localIndex)
          heap.addLocal(varAddr, memberRef, localType)
          heap.vivemDout.print(" v" + varAddr + "<-o" + memberRef.num)
        })
        NodeContinue(heap.void)
      }
      case DestroyStaticSizedArrayIntoLocalsH(arrExpr, localTypes, locals) => {
        val arrReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, arrReference.ownership), arrReference)

        if (arrExpr.resultType.ownership == OwnH) {
          heap.ensureRefCount(arrReference, None, 0)
        } else {
          // Not doing
          //   heap.ensureTotalRefCount(arrReference, 0)
          // for share because we might be taking in a shared reference and not be destroying it.
        }

        val oldMemberReferences = heap.destructureArray(arrReference)

        if (arrReference.ownership == OwnH) {
          heap.zero(arrReference)
          heap.deallocateIfNoWeakRefs(arrReference)
        }

        vassert(oldMemberReferences.size == locals.size)
        oldMemberReferences.zip(localTypes).zip(locals).foreach({ case ((memberRef, localType), localIndex) =>
          val varAddr = heap.getVarAddress(expressionId.callId, localIndex)
          heap.addLocal(varAddr, memberRef, localType)
          heap.vivemDout.print(" v" + varAddr + "<-o" + memberRef.num)
        })
        NodeContinue(heap.void)
      }
      case ArrayLengthH(arrExpr) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val arr @ ArrayInstanceV(_, _, _, _) = heap.dereference(arrayReference)

        discard(programH, heap, stdout, stdin, callId, arrExpr.resultType, arrayReference)

        val lenRef = makePrimitive(heap, callId, InlineH, IntV(arr.getSize(), 32))
        NodeContinue(lenRef)
      }
      case ArrayCapacityH(arrExpr) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val arr @ ArrayInstanceV(_, _, _, _) = heap.dereference(arrayReference)

        discard(programH, heap, stdout, stdin, callId, arrExpr.resultType, arrayReference)

        val lenRef = makePrimitive(heap, callId, InlineH, IntV(arr.capacity, 32))
        NodeContinue(lenRef)
      }
      case waH @ BorrowToWeakH(sourceExpr) => {
        val constraintRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        vassert(constraintRef.ownership == BorrowH)

        val weakRef = heap.transmute(constraintRef, sourceExpr.resultType, waH.resultType)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, weakRef.ownership), weakRef)
        discard(programH, heap, stdout, stdin, callId, sourceExpr.resultType, constraintRef)

        NodeContinue(weakRef)
      }
      case waH @ PointerToWeakH(sourceExpr) => {
        val constraintRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        vassert(constraintRef.ownership == PointerH)

        val weakRef = heap.transmute(constraintRef, sourceExpr.resultType, waH.resultType)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, weakRef.ownership), weakRef)
        discard(programH, heap, stdout, stdin, callId, sourceExpr.resultType, constraintRef)

        NodeContinue(weakRef)
      }
      case AsSubtypeH(sourceExpr, targetKind, resultType, okConstructor, errConstructor) => {
        val sourceRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        if (sourceRef.actualKind.hamut == targetKind) {
//          val newRef = ReferenceH(BorrowH, YonderH, sourceExpr.resultType.permission, sourceExpr.resultType.kind)
          val refAliasedAsSubtype = heap.transmute(sourceRef, sourceExpr.resultType, okConstructor.params.head)

          heap.vivemDout.println()
          heap.vivemDout.println("  " * expressionId.callId.callDepth + "Making new stack frame (lock call)")

          val function = programH.lookupFunction(okConstructor)
          // The receiver should increment with their own arg referrers.
          heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, sourceRef.ownership), sourceRef)

          val (calleeCallId, retuurn) =
            FunctionVivem.executeFunction(
              programH, stdin, stdout, heap, Vector(refAliasedAsSubtype), function)
          heap.vivemDout.print("  " * expressionId.callId.callDepth + "Getting return reference")

          val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)

          NodeContinue(upcast(returnRef, resultType.kind))
        } else {
          heap.vivemDout.println()
          heap.vivemDout.println("  " * expressionId.callId.callDepth + "Making new stack frame (lock call)")

          val function = programH.lookupFunction(errConstructor)
          // The receiver should increment with their own arg referrers.
          heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, sourceRef.ownership), sourceRef)

          val (calleeCallId, retuurn) =
            FunctionVivem.executeFunction(
              programH, stdin, stdout, heap, Vector(sourceRef), function)
          heap.vivemDout.print("  " * expressionId.callId.callDepth + "Getting return reference")

          val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)

          NodeContinue(upcast(returnRef, resultType.kind))
        }
      }
      case LockWeakH(sourceExpr, resultType, someConstructor, noneConstructor) => {
        val weakRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        vassert(weakRef.ownership == WeakH)

        if (heap.containsLiveObject(weakRef)) {
          val expectedRef = ReferenceH(PointerH, YonderH, sourceExpr.resultType.permission, sourceExpr.resultType.kind)
          val constraintRef = heap.transmute(weakRef, sourceExpr.resultType, expectedRef)

          heap.vivemDout.println()
          heap.vivemDout.println("  " * expressionId.callId.callDepth + "Making new stack frame (lock call)")

          val function = programH.lookupFunction(someConstructor)
          // The receiver should increment with their own arg referrers.
          heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, weakRef.ownership), weakRef)

          val (calleeCallId, retuurn) =
            FunctionVivem.executeFunction(
              programH, stdin, stdout, heap, Vector(constraintRef), function)
          heap.vivemDout.print("  " * expressionId.callId.callDepth + "Getting return reference")

          val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)

          NodeContinue(upcast(returnRef, resultType.kind))
        } else {
          discard(programH, heap, stdout, stdin, callId, sourceExpr.resultType, weakRef)

          heap.vivemDout.println()
          heap.vivemDout.println("  " * expressionId.callId.callDepth + "Making new stack frame (lock call)")

          val function = programH.lookupFunction(noneConstructor)

          val (calleeCallId, retuurn) =
            FunctionVivem.executeFunction(
              programH, stdin, stdout, heap, Vector(), function)
          heap.vivemDout.print("  " * expressionId.callId.callDepth + "Getting return reference")

          val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)
          NodeContinue(upcast(returnRef, resultType.kind))
        }
      }
      case StackifyH(sourceExpr, localIndex, name) => {
        val reference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val varAddr = heap.getVarAddress(expressionId.callId, localIndex)
        heap.addLocal(varAddr, reference, sourceExpr.resultType)
        heap.vivemDout.print(" v" + varAddr + "<-o" + reference.num)

        discard(programH, heap, stdout, stdin, callId, sourceExpr.resultType, reference)

        NodeContinue(heap.void)
      }
      case LocalStoreH(localIndex, sourceExpr, name) => {
        val reference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val varAddress = heap.getVarAddress(expressionId.callId, localIndex)
        heap.vivemDout.print(" " + varAddress + "(\"" + name + "\")")
        heap.vivemDout.print("<-" + reference.num)
        val oldRef = heap.mutateVariable(varAddress, reference, sourceExpr.resultType)

        discard(programH, heap, stdout, stdin, callId, sourceExpr.resultType, reference)

        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, oldRef.ownership), oldRef)
        NodeContinue(oldRef)
      }

      case MemberStoreH(resultType, structExpr, memberIndex, sourceExpr, memberName) => {
        val structReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), structExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val sourceReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val address = MemberAddressV(structReference.allocId, memberIndex)
        heap.vivemDout.print(" " + address + "(\"" + memberName + "\")")
        heap.vivemDout.print("<-" + sourceReference.num)
        val oldMemberReference = heap.mutateStruct(address, sourceReference, sourceExpr.resultType)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, oldMemberReference.ownership), oldMemberReference)

        discard(programH, heap, stdout, stdin, callId, structExpr.resultType, structReference)
        discard(programH, heap, stdout, stdin, callId, sourceExpr.resultType, sourceReference)

        NodeContinue(oldMemberReference)
      }

      case RuntimeSizedArrayStoreH(arrayExpr, indexExpr, sourceExpr, resultType) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val indexReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), indexExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val sourceReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(2), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val IntV(elementIndex, 32) = heap.dereference(indexReference)

        val address = ElementAddressV(arrayReference.allocId, elementIndex.toInt)
        heap.vivemDout.print(" " + address)
        heap.vivemDout.print("<-" + sourceReference.num)
        val oldMemberReference = heap.mutateArray(address, sourceReference, sourceExpr.resultType)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, oldMemberReference.ownership), oldMemberReference)

        discard(programH, heap, stdout, stdin, callId, sourceExpr.resultType, sourceReference)
        discard(programH, heap, stdout, stdin, callId, indexExpr.resultType, indexReference)
        discard(programH, heap, stdout, stdin, callId, arrayExpr.resultType, arrayReference)
        NodeContinue(oldMemberReference)
      }

      case StaticSizedArrayStoreH(structExpr, indexExpr, sourceExpr, resultType) => {
        vimpl()
//        val indexReference = heap.takeReferenceFromExpr(ExprId(blockId, indexExpr.exprId), indexExpr.resultType)
//        val arrayReference = heap.takeReferenceFromExpr(ExprId(blockId, structExpr.exprId), structExpr.resultType)
//        val IntV(elementIndex) = heap.dereference(indexReference)
//
//        val address = ElementAddressV(arrayReference.allocId, elementIndex)
//        val reference = heap.takeReferenceFromExpr(ExprId(blockId, sourceExpr.exprId), sourceExpr.resultType)
//        heap.vivemDout.print(" " + address)
//        heap.vivemDout.print("<-" + reference.num)
//        val oldMemberReference = heap.mutateArray(address, reference, sourceExpr.resultType)
//        heap.setReferenceExpr(exprId, oldMemberReference)
//        NodeContinue(exprId))
      }

      case ll @ LocalLoadH(local, targetOwnership, targetPermission, name) => {
        vassert(targetOwnership != OwnH) // should have been Unstackified instead
        val varAddress = heap.getVarAddress(expressionId.callId, local)
        val reference = heap.getReferenceFromLocal(varAddress, local.typeH, ll.resultType)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, reference.ownership), reference)
        heap.vivemDout.print(" *" + varAddress)
        NodeContinue(reference)
      }

      case ll @ NarrowPermissionH(sourceExpr, targetOwnership) => {
        val sourceReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val permissionedReference = heap.cast(callId, sourceReference, sourceExpr.resultType, ll.resultType)

        NodeContinue(permissionedReference)
      }

      case btp @ BorrowToPointerH(sourceExpr) => {
        val sourceReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val permissionedReference = heap.cast(callId, sourceReference, sourceExpr.resultType, btp.resultType)

        NodeContinue(permissionedReference)
      }

      case ptb @ PointerToBorrowH(sourceExpr) => {
        val sourceReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val permissionedReference = heap.cast(callId, sourceReference, sourceExpr.resultType, ptb.resultType)

        NodeContinue(permissionedReference)
      }

      case UnstackifyH(local) => {
        val varAddress = heap.getVarAddress(expressionId.callId, local)
        val reference = heap.getReferenceFromLocal(varAddress, local.typeH, local.typeH)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, reference.ownership), reference)
        heap.vivemDout.print(" ^" + varAddress)
        heap.removeLocal(varAddress, local.typeH)
        NodeContinue(reference)
      }
      case CallH(prototypeH, argsExprs) => {
        val argRefs =
          argsExprs.zipWithIndex.map({ case (argExpr, i) =>
            executeNode(programH, stdin, stdout, heap, expressionId.addStep(i), argExpr) match {
              case NodeBreak() | NodeReturn(_) => {
                // This shouldnt be possible because break and return can only
                // be statements, not expressions, see BRCOBS.
                vwat()
              }
              case NodeContinue(r) => r
            }
          })

        if (programH.lookupFunction(prototypeH).isExtern) {
          val externFunction = FunctionVivem.getExternFunction(programH, prototypeH)

          val resultRef =
            externFunction(
              new AdapterForExterns(
                programH,
                heap,
                CallId(expressionId.callId.callDepth + 1, prototypeH),
                stdin,
                stdout),
              argRefs.toVector)
          heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, resultRef.ownership), resultRef)

          // Special case for externs; externs arent allowed to change ref counts at all.
          // So, we just drop these normally.
          argRefs.zip(argsExprs.map(_.resultType))
            .foreach({ case (r, expectedType) => discard(programH, heap, stdout, stdin, callId, expectedType, r) })

          NodeContinue(resultRef)
        } else {
          heap.vivemDout.println()
          heap.vivemDout.println("  " * expressionId.callId.callDepth + "Making new stack frame (call)")

          val function = programH.lookupFunction(prototypeH)

          // The receiver should increment with their own arg referrers.
          argRefs.foreach(r => heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, r.ownership), r))

          val (calleeCallId, retuurn) =
            FunctionVivem.executeFunction(
              programH, stdin, stdout, heap, argRefs.toVector, function)
          heap.vivemDout.print("  " * expressionId.callId.callDepth + "Getting return reference")

          val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)
          NodeContinue(returnRef)
        }
      }
      case InterfaceCallH(argsExprs, virtualParamIndex, interfaceRefH, indexInEdge, functionType) => {
        // undeviewed = not deviewed = the virtual param is still a view and we want it to
        // be a struct.
        val undeviewedArgReferences =
          argsExprs.zipWithIndex.map({ case (argExpr, i) =>
            executeNode(programH, stdin, stdout, heap, expressionId.addStep(i), argExpr) match {
              case r @ NodeReturn(_) => {
                vimpl() // do we have to, like, discard the previously made arguments?
                // what happens with those?
                return r
              }
              case NodeContinue(r) => r
            }
          })

        heap.vivemDout.println()
        heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (icall)")

        // The receiver should increment with their own arg referrers.
        undeviewedArgReferences.foreach(r => heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, r.ownership), r))

        val (functionH, (calleeCallId, retuurn)) =
          executeInterfaceFunction(programH, stdin, stdout, heap, undeviewedArgReferences, virtualParamIndex, interfaceRefH, indexInEdge, functionType)

        val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)
        NodeContinue(returnRef)
      }
      case NewStructH(argsExprs, targetMemberNames, structRefH) => {
        val structDefH = programH.lookupStruct(structRefH.kind)

        val memberReferences =
          argsExprs.zipWithIndex.map({ case (argExpr, i) =>
            executeNode(programH, stdin, stdout, heap, expressionId.addStep(i), argExpr) match {
              case r @ NodeReturn(_) => {
                vimpl() // do we have to, like, discard the previously made arguments?
                // what happens with those?
                return r
              }
              case NodeContinue(r) => r
            }
          })

        memberReferences.foreach(r => heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, r.ownership), r))

        vassert(memberReferences.size == structDefH.members.size)
        val reference = heap.newStruct(structDefH, structRefH, memberReferences)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, reference.ownership), reference)

        NodeContinue(reference)
      }
      case NewArrayFromValuesH(arrayRefType, elementExprs) => {
        val elementRefs =
          elementExprs.zipWithIndex.map({ case (argExpr, i) =>
            executeNode(programH, stdin, stdout, heap, expressionId.addStep(i), argExpr) match {
              case r @ NodeReturn(_) => {
                vimpl() // do we have to, like, discard the previously made arguments?
                // what happens with those?
                return r
              }
              case NodeContinue(r) => r
            }
          })

        elementRefs.foreach(r => heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, r.ownership), r))

        val ssaDef = programH.lookupStaticSizedArray(arrayRefType.kind)
        val (arrayReference, arrayInstance) =
          heap.addArray(ssaDef, arrayRefType, elementRefs)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)

        heap.vivemDout.print(" o" + arrayReference.num + "=")
        heap.printKind(arrayInstance)
        NodeContinue(arrayReference)
      }

      case ml @ MemberLoadH(structExpr, memberIndex, expectedMemberType, resultType, memberName) => {
        val structReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), structExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val address = MemberAddressV(structReference.allocId, memberIndex)

        heap.vivemDout.print(" *" + address)
        val memberReference = heap.getReferenceFromStruct(address, expectedMemberType, ml.resultType)
        vassert(resultType.ownership != OwnH)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, memberReference.ownership), memberReference)

        discard(programH, heap, stdout, stdin, callId, structExpr.resultType, structReference)
        NodeContinue(memberReference)
      }

      case rsal @ RuntimeSizedArrayLoadH(arrayExpr, indexExpr, targetOwnership, targetPermission, expectedElementType, resultType) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val indexIntReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), indexExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val index =
          heap.dereference(indexIntReference) match {
            case IntV(value, 32) => value.toInt
          }

        val address = ElementAddressV(arrayReference.allocId, index)

        heap.vivemDout.print(" *" + address)
        val source = heap.getReferenceFromArray(address, expectedElementType, resultType)
        if (targetOwnership == OwnH) {
          vfail("impl me?")
        } else {
        }
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, source.ownership), source)

        discard(programH, heap, stdout, stdin, callId, indexExpr.resultType, indexIntReference)
        discard(programH, heap, stdout, stdin, callId, arrayExpr.resultType, arrayReference)
        NodeContinue(source)
      }

      case StaticSizedArrayLoadH(arrayExpr, indexExpr, targetOwnership, targetPermission, expectedElementType, arraySize, resultType) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val indexReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), indexExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val index =
          heap.dereference(indexReference) match {
            case IntV(value, 32) => value.toInt
          }

        val address = ElementAddressV(arrayReference.allocId, index)

        heap.vivemDout.print(" *" + address)
        val source = heap.getReferenceFromArray(address, expectedElementType, resultType)
        if (targetOwnership == OwnH) {
          vfail("impl me?")
        } else {
        }
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, source.ownership), source)

        discard(programH, heap, stdout, stdin, callId, indexExpr.resultType, indexReference)
        discard(programH, heap, stdout, stdin, callId, arrayExpr.resultType, arrayReference)
        NodeContinue(source)
      }
      case siu @ StructToInterfaceUpcastH(sourceExpr, targetInterfaceRef) => {
        val sourceReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val targetReference = upcast(sourceReference, targetInterfaceRef)
        NodeContinue(targetReference)
      }
      case IfH(conditionBlock, thenBlock, elseBlock, commonSupertype) => {
        val conditionReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), conditionBlock) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val conditionKind = heap.dereference(conditionReference)
        val BoolV(conditionValue) = conditionKind;

        discard(programH, heap, stdout, stdin, callId, conditionBlock.resultType, conditionReference)

        val blockResult =
          if (conditionValue == true) {
            executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), thenBlock) match {
              case r @ (NodeReturn(_) | NodeBreak()) => return r
              case NodeContinue(r) => r
            }
          } else {
            executeNode(programH, stdin, stdout, heap, expressionId.addStep(2), elseBlock) match {
              case r @ (NodeReturn(_) | NodeBreak()) => return r
              case NodeContinue(r) => r
            }
          }
        NodeContinue(blockResult)
      }
      case WhileH(bodyBlock) => {
        var continue = true
        while (continue) {
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), bodyBlock) match {
            case r @ NodeReturn(_) => return r
            case NodeBreak() => continue = false
            case NodeContinue(r) => {
              discard(programH, heap, stdout, stdin, callId, bodyBlock.resultType, r)
            }
          }
        }
        NodeContinue(heap.void)
      }
      case cac @ NewImmRuntimeSizedArrayH(sizeExpr, generatorExpr, generatorPrototype, _, arrayRefType) => {
        val sizeReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sizeExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val sizeKind = heap.dereference(sizeReference)
        val IntV(size, 32) = sizeKind;
        val rsaDef = programH.lookupRuntimeSizedArray(arrayRefType.kind)
        val (arrayReference, arrayInstance) =
          heap.addUninitializedArray(rsaDef, arrayRefType, size.toInt)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)

        val generatorReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), generatorExpr) match {
            case nr @ NodeReturn(_) => return nr
            case NodeContinue(v) => v
          }

        generateElements(
          programH, stdin, stdout, heap, expressionId, callId, generatorReference, generatorPrototype, size.toInt,
          (i, elementRef) => {
            // No need to increment or decrement, we're conceptually moving the return value
            // from the return slot to the array slot
            heap.initializeArrayElement(arrayReference, elementRef)
          })

        discard(programH, heap, stdout, stdin, callId, generatorExpr.resultType, generatorReference)
        discard(programH, heap, stdout, stdin, callId, sizeExpr.resultType, sizeReference)

        heap.vivemDout.print(" o" + arrayReference.num + "=")
        heap.printKind(arrayInstance)

        NodeContinue(arrayReference)
      }

      case NewMutRuntimeSizedArrayH(capacityHE, elementHT, arrayRefType) => {
        val capacityReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), capacityHE) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val capacityValue = heap.dereference(capacityReference)
        val IntV(capacity, 32) = capacityValue;

        val rsaDef = programH.lookupRuntimeSizedArray(arrayRefType.kind)
        val (arrayReference, arrayInstance) =
          heap.addUninitializedArray(rsaDef, arrayRefType, capacity.toInt)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)

        discard(programH, heap, stdout, stdin, callId, capacityHE.resultType, capacityReference)

        heap.vivemDout.print(" o" + arrayReference.num + "=")
        heap.printKind(arrayInstance)
        NodeContinue(arrayReference)
      }

      case PushRuntimeSizedArrayH(arrayHE, newcomerHE) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayHE) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val rsaDef = programH.lookupRuntimeSizedArray(arrayHE.resultType.kind)
        vassert(rsaDef.elementType == newcomerHE.resultType)
        val rsa @ ArrayInstanceV(_, _, _, _) = heap.dereference(arrayReference)

        val newcomerReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), newcomerHE) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        val newcomerVE = heap.dereference(newcomerReference)

        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, newcomerReference.ownership), newcomerReference)
        heap.initializeArrayElement(arrayReference, newcomerReference)

        discard(programH, heap, stdout, stdin, callId, arrayHE.resultType, arrayReference)

        heap.vivemDout.print(" o" + arrayReference.num + "+=")
        heap.printKind(newcomerVE)
        NodeContinue(heap.void)
      }

      case PopRuntimeSizedArrayH(arrayHE, elementType) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayHE) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
//        val rsaDef = programH.lookupRuntimeSizedArray(arrayHE.resultType.kind)
//        val rsa @ ArrayInstanceV(_, _, _, _) = heap.dereference(arrayReference)

        val resultReference = heap.deinitializeArrayElement(arrayReference)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, resultReference.ownership), resultReference)
        val resultValue = heap.dereference(resultReference)

        discard(programH, heap, stdout, stdin, callId, arrayHE.resultType, arrayReference)

        heap.vivemDout.print(" o" + arrayReference.num + "-=")
        heap.printKind(resultValue)
        NodeContinue(resultReference)
      }

      case cac @ StaticArrayFromCallableH(generatorExpr, generatorPrototype, _, arrayRefType) => {
        val ssaDef = programH.lookupStaticSizedArray(arrayRefType.kind)

        val generatorReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), generatorExpr) match {
            case nr @ NodeReturn(_) => return nr
            case NodeContinue(v) => v
          }

        val elementRefs = mutable.MutableList[ReferenceV]()

        generateElements(
          programH, stdin, stdout, heap, expressionId, callId, generatorReference, generatorPrototype, ssaDef.size,
          (i, elementRef) => {
            // No need to increment or decrement, we're conceptually moving the return value
            // from the return slot to the array slot
            elementRefs += elementRef
          })

        discard(programH, heap, stdout, stdin, callId, generatorExpr.resultType, generatorReference)

        val (arrayReference, arrayInstance) =
          heap.addArray(ssaDef, arrayRefType, elementRefs.toVector)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)

        heap.vivemDout.print(" o" + arrayReference.num + "=")
        heap.printKind(arrayInstance)
        NodeContinue(arrayReference)
      }

      case DestroyStaticSizedArrayIntoFunctionH(arrayExpr, consumerME, consumerMethod, arrayElementType, arraySize) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val consumerReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), consumerME) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        heap.checkReference(consumerME.resultType, consumerReference)

        // Temporarily reduce to 0. We do this instead of ensure(1) to better detect a bug
        // where there might be one different kind of referrer.
        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)
        heap.ensureRefCount(arrayReference, None, 0)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)

        val ssaDefM = programH.lookupStaticSizedArray(arrayExpr.resultType.kind)

        consumeElements(
          programH, stdin, stdout, heap, expressionId, callId, arrayReference, consumerReference, consumerMethod, ssaDefM.size, (_, _) => {})

        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)
        heap.zero(arrayReference)
        heap.deallocateIfNoWeakRefs(arrayReference)

        discard(programH, heap, stdout, stdin, callId, consumerME.resultType, consumerReference)

        NodeContinue(heap.void)
      }

      case cac @ DestroyImmRuntimeSizedArrayH(arrayExpr, consumerInterfaceExpr, consumerMethod, arrayElementType) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        val consumerReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), consumerInterfaceExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }
        heap.checkReference(consumerInterfaceExpr.resultType, consumerReference)

        // Temporarily reduce to 0. We do this instead of ensure(1) to better detect a bug
        // where there might be one different kind of referrer.
        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)
        heap.ensureRefCount(arrayReference, None, 0)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)

        val size =
          heap.dereference(arrayReference) match {
            case ArrayInstanceV(_, _, s, _) => s
          }

        consumeElements(
          programH, stdin, stdout, heap, expressionId, callId, arrayReference, consumerReference, consumerMethod, size, (_, _) => {})

        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)
        heap.zero(arrayReference)
        heap.deallocateIfNoWeakRefs(arrayReference)

        discard(programH, heap, stdout, stdin, callId, consumerInterfaceExpr.resultType, consumerReference)

        NodeContinue(heap.void)
      }

      case DestroyMutRuntimeSizedArrayH(arrayExpr) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayExpr) match {
            case r @ (NodeReturn(_) | NodeBreak()) => return r
            case NodeContinue(r) => r
          }

        // Temporarily reduce to 0. We do this instead of ensure(1) to better detect a bug
        // where there might be one different kind of referrer.
        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)
        heap.ensureRefCount(arrayReference, None, 0)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)

        val elements =
          heap.dereference(arrayReference) match {
            case ArrayInstanceV(_, _, _, elements) => elements
          }
        vassert(elements.size == 0)

        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, arrayReference.ownership), arrayReference)
        heap.zero(arrayReference)
        heap.deallocateIfNoWeakRefs(arrayReference)

        NodeContinue(heap.void)
      }
    }
  }

  private def consumeElements(
    programH: ProgramH,
    stdin: () => String,
    stdout: String => Unit,
    heap: Heap,
    expressionId: ExpressionId,
    callId: CallId,
    arrayReference: ReferenceV,
    consumerReference: ReferenceV,
    consumerPrototype: PrototypeH,
    size: Int,
    receiver: (Int, ReferenceV) => Unit):
  Unit = {
    val consumerFunction = programH.lookupFunction(consumerPrototype)

    (0 until size).reverse.foreach(i => {
      heap.vivemDout.println()
      heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (consumer)")

//      val indexReference = heap.allocateTransient(ShareH, InlineH, ReadonlyH, IntV(i, 32))

      heap.vivemDout.println()

      heap.vivemDout.print(" *" + ElementAddressV(arrayReference.allocId, i))
      val elementReference = heap.deinitializeArrayElement(arrayReference)
      // Not incrementing ref count here because we're moving it from the array

      heap.vivemDout.println()
      heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (icall)")

      val (calleeCallId, retuurn) =
        FunctionVivem.executeFunction(
          programH,
          stdin,
          stdout,
          heap,
          Vector(consumerReference, elementReference),
          consumerFunction)

      heap.vivemDout.print("  " * callId.callDepth + "Getting return reference")

      val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)

      // This decrements it, but does not discard it.
      heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, returnRef.ownership), returnRef)

      receiver(i, returnRef)
    });
  }

  private def generateElements(
    programH: ProgramH,
    stdin: () => String,
    stdout: String => Unit,
    heap: Heap,
    expressionId: ExpressionId,
    callId: CallId,
    generatorReference: ReferenceV,
    generatorPrototype: PrototypeH,
    size: Int,
    receiver: (Int, ReferenceV) => Unit):
  Unit = {
    val generatorFunction = programH.lookupFunction(generatorPrototype)

    (0 until size).foreach(i => {
      heap.vivemDout.println()
      heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (generator)")

      val indexReference = heap.allocateTransient(ShareH, InlineH, ReadonlyH, IntV(i, 32))

      heap.vivemDout.println()

      heap.vivemDout.println()
      heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (icall)")

      val (calleeCallId, retuurn) =
        FunctionVivem.executeFunction(
          programH,
          stdin,
          stdout,
          heap,
          Vector(generatorReference, indexReference),
          generatorFunction)

      heap.vivemDout.print("  " * callId.callDepth + "Getting return reference")

      val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)

      // This decrements it, but does not discard it.
      heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, returnRef.ownership), returnRef)

      receiver(i, returnRef)
    });
  }

  private def executeInterfaceFunction(
      programH: ProgramH,
      stdin: () => String,
      stdout: String => Unit,
      heap: Heap,
      undeviewedArgReferences: Vector[ReferenceV],
      virtualParamIndex: Int,
      interfaceRefH: InterfaceRefH,
      indexInEdge: Int,
      functionType: PrototypeH) = {

    val interfaceReference = undeviewedArgReferences(virtualParamIndex)

    // Vivem wants to know the type of an undead object so it can call a weak-self
    // method after it's been dropped. Midas can do this (it relies on it for resilient
    // mode) though some other platforms probably won't be able to.
    val edge =
      heap.dereference(interfaceReference, allowUndead = true) match {
        case StructInstanceV(structH, _) => structH.edges.find(_.interface == interfaceRefH).get
        case other => vwat(other.toString)
      }

    val ReferenceV(actualStruct, actualInterfaceKind, actualOwnership, actualLocation, actualPermission, allocNum) = interfaceReference
    vassert(actualInterfaceKind.hamut == interfaceRefH)
    val structReference = ReferenceV(actualStruct, actualStruct, actualOwnership, actualLocation, actualPermission, allocNum)

    val prototypeH = edge.structPrototypesByInterfaceMethod.values.toVector(indexInEdge)
    val functionH = programH.lookupFunction(prototypeH)

    val actualPrototype = functionH.prototype
    val expectedPrototype = functionType
    // We would compare functionH.type to functionType directly, but
    // functionH.type expects a struct and prototypeH expects an interface.

    // First, check that all the other params are correct.
    undeviewedArgReferences.zipWithIndex.zip(actualPrototype.params).zip(expectedPrototype.params).foreach({
      case (((argReference, index), actualFunctionParamType), expectedFunctionParamType) => {
        // Skip the interface line for now, we check it below
        if (index != virtualParamIndex) {
          heap.checkReference(actualFunctionParamType, argReference)
          heap.checkReference(expectedFunctionParamType, argReference)
          vassert(actualFunctionParamType == expectedFunctionParamType)
        }
      }
    })

    val deviewedArgReferences = undeviewedArgReferences.updated(virtualParamIndex, structReference)

    val maybeReturnReference =
      FunctionVivem.executeFunction(
        programH,
        stdin,
        stdout,
        heap,
        deviewedArgReferences.toVector,
        functionH)
    (functionH, maybeReturnReference)
  }

  def discard(
    programH: ProgramH,
    heap: Heap,
    stdout: String => Unit,
    stdin: () => String,
    callId: CallId,
    expectedReference: ReferenceH[KindH],
    actualReference: ReferenceV
  ): Unit = {

    heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId, actualReference.ownership), actualReference)

    if (heap.getTotalRefCount(actualReference) == 0) {
      expectedReference.ownership match {
        case OwnH => // Do nothing, Vivem often discards owning things, if we're making a new owning reference to it.
        case WeakH => {
          heap.deallocateIfNoWeakRefs(actualReference)
        }
        case PointerH => // Do nothing.
        case ShareH => {
          expectedReference.kind match {
            case VoidH() | IntH(_) | BoolH() | StrH() | FloatH() => {
              heap.zero(actualReference)
              heap.deallocateIfNoWeakRefs(actualReference)
            }
//            case ir @ InterfaceRefH(_) => {
//              heap.vivemDout.println()
//              heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (discard icall)")
//              val prototypeH = programH.lookupPackage(expectedReference.kind.packageCoord).immDestructorsByKind(expectedReference.kind)
//              val indexInEdge = programH.lookupInterface(ir).methods.indexWhere(_.prototypeH == prototypeH)
//              vassert(indexInEdge >= 0)
//              val (functionH, (calleeCallId, retuurn)) =
//                executeInterfaceFunction(programH, stdin, stdout, heap, Vector(actualReference), 0, ir, indexInEdge, prototypeH)
//              heap.vivemDout.print("  " * callId.callDepth + "Getting return reference")
//              val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)
//              vassert(returnRef.actualKind.hamut == ProgramH.emptyTupleStructRef)
//              discard(programH, heap, stdout, stdin, callId, prototypeH.returnType, returnRef)
//            }
            case InterfaceRefH(_) | StructRefH(_) | RuntimeSizedArrayHT(_) | StaticSizedArrayHT(_) => {
              heap.vivemDout.println()
              heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (discard call)")
              val prototypeH = vassertSome(programH.lookupPackage(expectedReference.kind.packageCoord).immDestructorsByKind.get(expectedReference.kind))
              val functionH = programH.lookupFunction(prototypeH)
              val (calleeCallId, retuurn) =
                FunctionVivem.executeFunction(
                  programH, stdin, stdout, heap, Vector(actualReference), functionH)
              heap.vivemDout.print("  " * callId.callDepth + "Getting return reference")
              val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)
              vassert(returnRef.actualKind.hamut == VoidH())
              discard(programH, heap, stdout, stdin, callId, prototypeH.returnType, returnRef)
            }
          }
        }
      }
    }
  }
}
