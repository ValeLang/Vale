package net.verdagon.vale.vivem

import net.verdagon.vale.metal._
import net.verdagon.vale.{vassert, vassertSome, vcurious, vfail, vimpl, vwat, metal => m}

object ExpressionVivem {
  // The contained reference has a ResultToObjectReferrer pointing at it.
  // This is so if we do something like [4, 5].0, and that 4 is being
  // returned to the parent node, it's not deallocated from its ref count
  // going to 0.
  sealed trait INodeExecuteResult
  case class NodeContinue(resultRef: ReferenceV) extends INodeExecuteResult
  case class NodeReturn(returnRef: ReferenceV) extends INodeExecuteResult

  def makeVoid(programH: ProgramH, heap: Heap, callId: CallId) = {
    val emptyPackStructRefH = ProgramH.emptyTupleStructRef
    val emptyPackStructDefH = vassertSome(programH.structs.find(_.getRef == emptyPackStructRefH))
    val void = heap.newStruct(emptyPackStructDefH, ReferenceH(ShareH, emptyPackStructRefH), List())
    heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), void)
    void
  }

  def makeInstance(heap: Heap, callId: CallId, referend: ReferendV) = {
    val ref = heap.allocateTransient(ShareH, referend)
    heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), ref)
    ref
  }

  def takeArgument(heap: Heap, callId: CallId, argumentIndex: Int, resultType: ReferenceH[ReferendH]) = {
    val ref = heap.takeArgument(callId, argumentIndex, resultType)
    heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), ref)
    ref
  }

  def possessCalleeReturn(heap: Heap, callId: CallId, calleeCallId: CallId, result: NodeReturn) = {
    heap.decrementReferenceRefCount(RegisterToObjectReferrer(calleeCallId), result.returnRef)
    heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), result.returnRef)
    result.returnRef
  }

  def executeNode(
                   programH: ProgramH,
                   stdin: (() => String),
                   stdout: (String => Unit),
                   heap: Heap,
                   expressionId: ExpressionId,
                   node: ExpressionH[ReferendH] // rename to expression
  ): INodeExecuteResult = {
    val callId = expressionId.callId
    node match {
      case DiscardH(sourceExpr) => {
        sourceExpr.resultType.ownership match {
          case BorrowH | ShareH =>
        }
        val sourceRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, sourceRef)

        NodeContinue(makeVoid(programH, heap, callId))
      }
      case ReinterpretH(sourceExpr, resultType) => {
        executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
          case r @ NodeReturn(_) => return r
          case NodeContinue(r) => vfail()
        }
      }
      case UnreachableMootH(_) => {
        vfail()
      }
      case ConstantI64H(value) => {
        val ref = makeInstance(heap, callId, IntV(value))
        NodeContinue(ref)
      }
      case ConstantF64H(value) => {
        val ref = makeInstance(heap, callId, FloatV(value))
        NodeContinue(ref)
      }
      case ConstantStrH(value) => {
        val ref = makeInstance(heap, callId, StrV(value))
        NodeContinue(ref)
      }
      case ConstantBoolH(value) => {
        val ref = makeInstance(heap, callId, BoolV(value))
        NodeContinue(ref)
      }
      case ArgumentH(resultType, argumentIndex) => {
        val ref = takeArgument(heap, callId, argumentIndex, resultType)
        NodeContinue(ref)
      }
      case ReturnH(sourceExpr) => {
        val sourceRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ NodeReturn(_) => {
              vcurious()
              return r
            }
            case NodeContinue(r) => r
          }
        return NodeReturn(sourceRef)
      }
      case CheckRefCountH(objExpr, category, numExpr) => {

        val objRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), objExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        val numRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), numExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        val num =
          heap.dereference(numRef) match {
            case IntV(n) => n
          }
        heap.ensureRefCount(objRef, category, num)

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, objRef)
        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, numRef)
        NodeContinue(makeVoid(programH, heap, callId))
      }
//      case SoftLoadH(_, sourceExpr, targetOwnership) => {
//        val sourceExprId = ExprId(blockId, sourceExpr.exprId)
//        val address = heap.takeAddressFromExpr(sourceExprId, sourceExpr.resultType)
//        heap.vivemDout.print(" *")
//        printAddress(heap.vivemDout, address)
//        val source = heap.dereferenceAddress(address, sourceExpr.resultType)
//        if (targetOwnership == Own) {
//          heap.setReferenceExpr(exprId, source)
//          heap.blacklistAddress(address, sourceExpr.resultType)
//        } else {
//          heap.aliasIntoExpr(
//            exprId,
//            source,
//            sourceExpr.resultType,
//            targetOwnership)
//        }
//        heap.maybeDeallocateAddressExpr(sourceExprId, address)
//      }
      case BlockH(innerExprs) => {
        var lastInnerExprResultRef: Option[ReferenceV] = None

        for (i <- innerExprs.indices) {
          val innerExpr = innerExprs(i)

          executeNode(programH, stdin, stdout, heap, expressionId.addStep(i), innerExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(innerExprResultRef) => {
              if (i == innerExprs.size - 1) {
                lastInnerExprResultRef = Some(innerExprResultRef)
              }
            }
          }
        }

        NodeContinue(vassertSome(lastInnerExprResultRef))
      }
      case DestructureH(structExpr, localTypes, locals) => {
        val structReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), structExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId), structReference)

        if (structExpr.resultType.ownership == OwnH) {
          heap.ensureTotalRefCount(structReference, 0)
        } else {
          // Not doing
          //   heap.ensureTotalRefCount(structReference, 0)
          // for share because we might be taking in a shared reference and not be destroying it.
        }

        val oldMemberReferences = heap.destructure(structReference, structReference.ownership == OwnH)

        vassert(oldMemberReferences.size == locals.size)
        oldMemberReferences.zip(localTypes).zip(locals).foreach({ case ((memberRef, localType), localIndex) =>
          val varAddr = heap.getVarAddress(expressionId.callId, localIndex)
          heap.addLocal(varAddr, memberRef, localType)
          heap.vivemDout.print(" v" + varAddr + "<-o" + memberRef.num)
        })
        NodeContinue(makeVoid(programH, heap, callId))
      }
      case DestructureArraySequenceH(arrExpr, localTypes, locals) => {
        val arrReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId), arrReference)

        if (arrExpr.resultType.ownership == OwnH) {
          heap.ensureTotalRefCount(arrReference, 0)
        } else {
          // Not doing
          //   heap.ensureTotalRefCount(arrReference, 0)
          // for share because we might be taking in a shared reference and not be destroying it.
        }

        val oldMemberReferences = heap.destructureArray(arrReference, arrReference.ownership == OwnH)

        vassert(oldMemberReferences.size == locals.size)
        oldMemberReferences.zip(localTypes).zip(locals).foreach({ case ((memberRef, localType), localIndex) =>
          val varAddr = heap.getVarAddress(expressionId.callId, localIndex)
          heap.addLocal(varAddr, memberRef, localType)
          heap.vivemDout.print(" v" + varAddr + "<-o" + memberRef.num)
        })
        NodeContinue(makeVoid(programH, heap, callId))
      }
      case ArrayLengthH(arrExpr) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        val arr @ ArrayInstanceV(_, _, _, _) = heap.dereference(arrayReference)

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, arrayReference)

        val lenRef = makeInstance(heap, callId, IntV(arr.getSize()))
        NodeContinue(lenRef)
      }
      case StackifyH(sourceExpr, localIndex, name) => {
        val reference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        val varAddr = heap.getVarAddress(expressionId.callId, localIndex)
        heap.addLocal(varAddr, reference, sourceExpr.resultType)
        heap.vivemDout.print(" v" + varAddr + "<-o" + reference.num)

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, reference)

        NodeContinue(makeVoid(programH, heap, callId))
      }
//      case LocalLookupH(_, localIndex, resultType, name) => {
//        // Check that its there
//        heap.getReferenceFromLocal(VariableAddressV(callId, localIndex), resultType)
//
//        heap.setVariableAddressExpr(exprId, VariableAddressV(callId, localIndex))
//      }

      case LocalStoreH(localIndex, sourceExpr, name) => {
        val reference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        val varAddress = heap.getVarAddress(expressionId.callId, localIndex)
        heap.vivemDout.print(" " + varAddress + "(\"" + name + "\")")
        heap.vivemDout.print("<-" + reference.num)
        val oldRef = heap.mutateVariable(varAddress, reference, sourceExpr.resultType)

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, reference)

        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), oldRef)
        NodeContinue(oldRef)
      }

      case MemberStoreH(resultType, structExpr, memberIndex, sourceExpr, memberName) => {
        val structReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), structExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        val sourceReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), sourceExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        val address = MemberAddressV(structReference.allocId, memberIndex)
        heap.vivemDout.print(" " + address + "(\"" + memberName + "\")")
        heap.vivemDout.print("<-" + sourceReference.num)
        val oldMemberReference = heap.mutateStruct(address, sourceReference, sourceExpr.resultType)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), oldMemberReference)

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, structReference)
        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, sourceReference)

        NodeContinue(oldMemberReference)
      }

      case UnknownSizeArrayStoreH(arrayExpr, indexExpr, sourceExpr) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        val indexReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), indexExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        val sourceReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(2), sourceExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        val IntV(elementIndex) = heap.dereference(indexReference)

        val address = ElementAddressV(arrayReference.allocId, elementIndex)
        heap.vivemDout.print(" " + address)
        heap.vivemDout.print("<-" + sourceReference.num)
        val oldMemberReference = heap.mutateArray(address, sourceReference, sourceExpr.resultType)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), oldMemberReference)

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, sourceReference)
        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, indexReference)
        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, arrayReference)
        NodeContinue(oldMemberReference)
      }

      case KnownSizeArrayStoreH(structExpr, indexExpr, sourceExpr) => {
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

      case LocalLoadH(local, targetOwnership, name) => {
        vassert(targetOwnership != OwnH) // should have been Unstackified instead
        val varAddress = heap.getVarAddress(expressionId.callId, local)
        val reference = heap.getReferenceFromLocal(varAddress, local.typeH, targetOwnership)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), reference)
        heap.vivemDout.print(" *" + varAddress)
        NodeContinue(reference)
      }

      case UnstackifyH(local) => {
        val varAddress = heap.getVarAddress(expressionId.callId, local)
        val reference = heap.getReferenceFromLocal(varAddress, local.typeH, local.typeH.ownership)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), reference)
        heap.vivemDout.print(" ^" + varAddress)
        heap.removeLocal(varAddress, local.typeH)
        NodeContinue(reference)
      }
      case CallH(functionRef, argsExprs) => {
        val argRefs =
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

        if (programH.functions.find(_.prototype == functionRef).get.isExtern) {
          val externFunction = FunctionVivem.getExternFunction(programH, functionRef)

          val resultRef =
            externFunction(
              new AdapterForExterns(
                programH,
                heap,
                CallId(expressionId.callId.callDepth + 1, functionRef),
                stdin,
                stdout),
              argRefs.toVector)
          heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), resultRef)

          // Special case for externs; externs arent allowed to change ref counts at all.
          // So, we just drop these normally.
          argRefs.foreach(r => dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, r))

          NodeContinue(resultRef)
        } else {
          heap.vivemDout.println()
          heap.vivemDout.println("  " * expressionId.callId.callDepth + "Making new stack frame (call)")

          val function =
            programH.functions.find(_.prototype == functionRef).get

          // The receiver should increment with their own arg referrers.
          argRefs.foreach(r => heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId), r))

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
        undeviewedArgReferences.foreach(r => heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId), r))

        val (functionH, (calleeCallId, retuurn)) =
          executeInterfaceFunction(programH, stdin, stdout, heap, undeviewedArgReferences, virtualParamIndex, interfaceRefH, indexInEdge, functionType)

        val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)
        NodeContinue(returnRef)
      }
      case NewStructH(argsExprs, structRefH) => {
        val structDefH = vassertSome(programH.structs.find(_.getRef == structRefH.kind))

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

        memberReferences.foreach(r => heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId), r))

        vassert(memberReferences.size == structDefH.members.size)
        val reference = heap.newStruct(structDefH, structRefH, memberReferences)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), reference)

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

        elementRefs.foreach(r => heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId), r))

        val (arrayReference, arrayInstance) =
          heap.addArray(arrayRefType, elementRefs)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), arrayReference)

        heap.vivemDout.print(" o" + arrayReference.num + "=")
        heap.printReferend(arrayInstance)
        NodeContinue(arrayReference)
      }

      case MemberLoadH(structExpr, memberIndex, targetOwnership, expectedMemberType, expectedResultType, memberName) => {
        val structReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), structExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        val address = MemberAddressV(structReference.allocId, memberIndex)

        heap.vivemDout.print(" *" + address)
        val memberReference = heap.getReferenceFromStruct(address, expectedMemberType, targetOwnership)
        vassert(targetOwnership != OwnH)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), memberReference)

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, structReference)
        NodeContinue(memberReference)
      }

      case UnknownSizeArrayLoadH(arrayExpr, indexExpr, resultType, targetOwnership) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        val indexIntReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), indexExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        val index =
          heap.dereference(indexIntReference) match {
            case IntV(value) => value
          }

        val address = ElementAddressV(arrayReference.allocId, index)

        heap.vivemDout.print(" *" + address)
        val source = heap.getReferenceFromArray(address, arrayExpr.resultType.kind.rawArray.elementType, targetOwnership)
        if (targetOwnership == OwnH) {
          vfail("impl me?")
        } else {
        }
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), source)

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, indexIntReference)
        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, arrayReference)
        NodeContinue(source)
      }

      case KnownSizeArrayLoadH(arrayExpr, indexExpr, resultType, targetOwnership) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        val indexReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), indexExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        val index =
          heap.dereference(indexReference) match {
            case IntV(value) => value
          }

        val address = ElementAddressV(arrayReference.allocId, index)

        heap.vivemDout.print(" *" + address)
        val source = heap.getReferenceFromArray(address, arrayExpr.resultType.kind.rawArray.elementType, targetOwnership)
        if (targetOwnership == OwnH) {
          vfail("impl me?")
        } else {
        }
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), source)

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, indexReference)
        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, arrayReference)
        NodeContinue(source)
      }
      case siu @ StructToInterfaceUpcastH(sourceExpr, targetInterfaceRef) => {
        val sourceReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sourceExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        val ownership = sourceReference.ownership

        val targetReference =
          ReferenceV(
            sourceReference.actualKind,
            RRReferend(targetInterfaceRef),
            sourceReference.ownership,
            sourceReference.num)
        NodeContinue(targetReference)
      }
      case IfH(conditionBlock, thenBlock, elseBlock, commonSupertype) => {
        val conditionReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), conditionBlock) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        val conditionReferend = heap.dereference(conditionReference)
        val BoolV(conditionValue) = conditionReferend;

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, conditionReference)

        val blockResult =
          if (conditionValue == true) {
            executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), thenBlock) match {
              case r @ NodeReturn(_) => return r
              case NodeContinue(r) => r
            }
          } else {
            executeNode(programH, stdin, stdout, heap, expressionId.addStep(2), elseBlock) match {
              case r @ NodeReturn(_) => return r
              case NodeContinue(r) => r
            }
          }
        NodeContinue(blockResult)
      }
      case WhileH(bodyBlock) => {
        var continue = true
        while (continue) {
          val conditionReference =
            executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), bodyBlock) match {
              case r @ NodeReturn(_) => return r
              case NodeContinue(r) => r
            }
          val conditionReferend = heap.dereference(conditionReference)
          val BoolV(conditionValue) = conditionReferend;
          dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, conditionReference)
          continue = conditionValue
        }
        NodeContinue(makeVoid(programH, heap, callId))
      }
      case cac @ ConstructUnknownSizeArrayH(sizeExpr, generatorInterfaceExpr, arrayRefType) => {
        val sizeReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), sizeExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        val sizeReferend = heap.dereference(sizeReference)
        val IntV(size) = sizeReferend;
        val (arrayReference, arrayInstance) =
          heap.addUninitializedArray(arrayRefType, size)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), arrayReference)

        val generatorInterfaceRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), generatorInterfaceExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        (0 until size).foreach(i => {
          heap.vivemDout.println()
          heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (generator)")

          val indexReference = heap.allocateTransient(ShareH, IntV(i))

          // We're assuming here that theres only 1 method in the interface.
          val indexInEdge = 0
          // We're assuming that it takes self then the index int as arguments.
          val virtualParamIndex = 0

          val interfaceDefH =
            programH.interfaces.find(_.getRef == generatorInterfaceExpr.resultType.kind).get
          val interfaceMethodPrototype = interfaceDefH.prototypes.head

          heap.vivemDout.println()

          heap.vivemDout.println()
          heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (icall)")

          val (functionH, (calleeCallId, retuurn)) =
            executeInterfaceFunction(
              programH,
              stdin,
              stdout,
              heap,
              List(generatorInterfaceRef, indexReference),
              virtualParamIndex,
              generatorInterfaceExpr.resultType.kind,
              indexInEdge,
              interfaceMethodPrototype)

          heap.vivemDout.print("  " * callId.callDepth + "Getting return reference")

          val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)

          // No need to increment or decrement, we're conceptually moving the return value
          // from the return slot to the array slot
          heap.initializeArrayElement(arrayReference, i, returnRef)
          dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, returnRef)
        });

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, generatorInterfaceRef)
        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, sizeReference)

        heap.vivemDout.print(" o" + arrayReference.num + "=")
        heap.printReferend(arrayInstance)

        NodeContinue(arrayReference)
      }

      case DestroyKnownSizeArrayH(arrayExpr, consumerInterfaceExpr) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        val consumerInterfaceRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), consumerInterfaceExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        heap.checkReference(consumerInterfaceExpr.resultType, consumerInterfaceRef)

//        heap.incrementReferenceHoldCount(expressionId, consumerInterfaceRef)

        // Temporarily reduce to 0. We do this instead of ensure(1) to better detect a bug
        // where there might be one different kind of referrer.
        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId), arrayReference)
        heap.ensureTotalRefCount(arrayReference, 0)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), arrayReference)

        val consumerInterfaceDefH =
          programH.interfaces.find(_.getRef == consumerInterfaceExpr.resultType.kind).get
        val consumerInterfaceMethodPrototype = consumerInterfaceDefH.prototypes.head

        val size = arrayExpr.resultType.kind.size
        (0 until size).foreach(ascendingI => {
          val i = size - ascendingI - 1

          heap.vivemDout.println()
          heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (consumer)")

          // We're assuming here that theres only 1 method in the interface.
          val indexInEdge = 0
          // We're assuming that it takes self then the index int as arguments.
          val virtualParamIndex = 0

          heap.vivemDout.println()

          heap.vivemDout.println()
          heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (icall)")

          val elementReference = heap.deinitializeArrayElement(arrayReference, i)

          val consumerInterfaceRefAlias =
            heap.alias(consumerInterfaceRef, consumerInterfaceExpr.resultType, consumerInterfaceExpr.resultType.ownership)

          val (functionH, (calleeCallId, retuurn)) =
            executeInterfaceFunction(
              programH,
              stdin,
              stdout,
              heap,
              List(consumerInterfaceRefAlias, elementReference),
              virtualParamIndex,
              consumerInterfaceExpr.resultType.kind,
              indexInEdge,
              consumerInterfaceMethodPrototype)

          val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)
          dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, returnRef)
        });

        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId), arrayReference)
        heap.deallocate(arrayReference)

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, consumerInterfaceRef)

        NodeContinue(makeVoid(programH, heap, callId))
      }

      case cac @ DestroyUnknownSizeArrayH(arrayExpr, consumerInterfaceExpr) => {
        val arrayReference =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(0), arrayExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }

        val consumerInterfaceRef =
          executeNode(programH, stdin, stdout, heap, expressionId.addStep(1), consumerInterfaceExpr) match {
            case r @ NodeReturn(_) => return r
            case NodeContinue(r) => r
          }
        heap.checkReference(consumerInterfaceExpr.resultType, consumerInterfaceRef)

        //        heap.incrementReferenceHoldCount(expressionId, consumerInterfaceRef)

        // Temporarily reduce to 0. We do this instead of ensure(1) to better detect a bug
        // where there might be one different kind of referrer.
        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId), arrayReference)
        heap.ensureTotalRefCount(arrayReference, 0)
        heap.incrementReferenceRefCount(RegisterToObjectReferrer(callId), arrayReference)

        val consumerInterfaceDefH =
          programH.interfaces.find(_.getRef == consumerInterfaceExpr.resultType.kind).get
        val consumerInterfaceMethodPrototype = consumerInterfaceDefH.prototypes.head

        val size =
          heap.dereference(arrayReference) match {
            case ArrayInstanceV(_, _, s, _) => s
          }
        (0 until size).foreach(ascendingI => {
          val i = size - ascendingI - 1

          heap.vivemDout.println()
          heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (consumer)")

          // We're assuming here that theres only 1 method in the interface.
          val indexInEdge = 0
          // We're assuming that it takes self then the index int as arguments.
          val virtualParamIndex = 0

          heap.vivemDout.println()

          heap.vivemDout.println()
          heap.vivemDout.println("  " * callId.callDepth + "Making new stack frame (icall)")

          val elementReference = heap.deinitializeArrayElement(arrayReference, i)

          val consumerInterfaceRefAlias =
            heap.alias(consumerInterfaceRef, consumerInterfaceExpr.resultType, consumerInterfaceExpr.resultType.ownership)

          val (functionH, (calleeCallId, retuurn)) =
            executeInterfaceFunction(
              programH,
              stdin,
              stdout,
              heap,
              List(consumerInterfaceRefAlias, elementReference),
              virtualParamIndex,
              consumerInterfaceExpr.resultType.kind,
              indexInEdge,
              consumerInterfaceMethodPrototype)

          val returnRef = possessCalleeReturn(heap, callId, calleeCallId, retuurn)
          dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, returnRef)
        });

        heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId), arrayReference)
        heap.deallocate(arrayReference)

        dropReferenceIfNonOwning(programH, heap, stdout, stdin, callId, consumerInterfaceRef)

        NodeContinue(makeVoid(programH, heap, callId))
      }
    }
  }

  private def executeInterfaceFunction(
      programH: ProgramH,
      stdin: () => String,
      stdout: String => Unit,
      heap: Heap,
      undeviewedArgReferences: List[ReferenceV],
      virtualParamIndex: Int,
      interfaceRefH: InterfaceRefH,
      indexInEdge: Int,
      functionType: PrototypeH) = {

    val interfaceReference = undeviewedArgReferences(virtualParamIndex)

    val structH =
      heap.dereference(interfaceReference) match {
        case StructInstanceV(s, _) => s
        case other => vwat(other.toString)
      }

    val edge = structH.edges.find(_.interface == interfaceRefH).get

    val ReferenceV(actualStruct, actualInterfaceKind, actualOwnership, allocNum) = interfaceReference
    vassert(actualInterfaceKind.hamut == interfaceRefH)
    val structReference = ReferenceV(actualStruct, actualStruct, actualOwnership, allocNum)

    val prototypeH = edge.structPrototypesByInterfacePrototype.values.toList(indexInEdge)
    val functionH = programH.functions.find(_.prototype == prototypeH).get;

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

  def dropReferenceIfNonOwning(
      programH: ProgramH,
      heap: Heap,
      stdout: String => Unit,
      stdin: () => String,
      callId: CallId,
      reference: ReferenceV) = {
    heap.decrementReferenceRefCount(RegisterToObjectReferrer(callId), reference)
    dropReference(programH, heap, stdout, stdin, callId, reference)
//    reference.ownership match {
//      case OwnH =>
//      case BorrowH | ShareH => {
//      }
//    }
  }

  def dropReference(
      programH: ProgramH,
      heap: Heap,
      stdout: String => Unit,
      stdin: () => String,
      callId: CallId,
      reference: ReferenceV
  ): Unit = {
    if (heap.getTotalRefCount(reference) == 0) {
      // If it's a share ref, then use runtime information to crawl through and decrement
      // ref counts.
      reference.ownership match {
        case ShareH => {
          reference.actualKind.hamut match {
            case InterfaceRefH(_) => {
              // We don't expect this because we asked for the actualKind, not the seenAsKind.
              vwat()
            }
            case IntH() | StrH() | BoolH() | FloatH() => {
              heap.deallocate(reference)
            }
            case StructRefH(_) => {
              // Deallocates the thing.
              val references = heap.destructure(reference, true)
              references.foreach(dropReference(programH, heap, stdout, stdin, callId, _))
            }
            case UnknownSizeArrayTH(_) => {
              val ArrayInstanceV(_, _, _, elements) = heap.dereference(reference)
              val references = elements.indices.map(i => heap.deinitializeArrayElement(reference, elements.size - 1 - i))
              heap.deallocate(reference)
              references.foreach(dropReference(programH, heap, stdout, stdin, callId, _))
            }
            case KnownSizeArrayTH(_, _) => {
              val ArrayInstanceV(_, _, _, elements) = heap.dereference(reference)
              val references = elements.indices.map(i => heap.deinitializeArrayElement(reference, elements.size - 1 - i))
              heap.deallocate(reference)
              references.foreach(dropReference(programH, heap, stdout, stdin, callId, _))
            }
          }
        }
//        case OwnH => {
//          vimpl()
//        }
      }
    }
  }
}
