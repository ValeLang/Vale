package net.verdagon.vale.vivem

import net.verdagon.vale.metal._
import net.verdagon.vale.vivem.ExpressionVivem.{NodeContinue, NodeReturn}
import net.verdagon.vale.{vimpl, vwat, metal => m}

object FunctionVivem {
  def executeFunction(
      programH: ProgramH,
      stdin: (() => String),
      stdout: (String => Unit),
      heap: Heap,
      args: Vector[ReferenceV],
      functionH: FunctionH
  ): (CallId, NodeReturn) = {
    val callId = heap.pushNewStackFrame(functionH.prototype, args)

//    heap.vivemDout.println("About to execute:")
//    functionH.nodes.foreach(heap.vivemDout.println)
//    heap.vivemDout.println("/Function")

    heap.vivemDout.print("  " * callId.callDepth + "Entering function " + callId)

    // Increment all the args to show that they have arguments referring to them.
    // These will be decremented at some point in the callee function.
    args.indices.foreach(argIndex => {
      heap.incrementReferenceRefCount(
        ArgumentToObjectReferrer(ArgumentId(callId, argIndex), args(argIndex).ownership),
        args(argIndex))
    })

    heap.vivemDout.println()

    val rootExpressionId = ExpressionId(callId, List.empty)
    val returnRef =
      ExpressionVivem.executeNode(programH, stdin, stdout, heap, rootExpressionId, functionH.body) match {
        case NodeReturn(r) => NodeReturn(r)
        case NodeContinue(r) => NodeReturn(r)
      }

    heap.vivemDout.println()
    heap.vivemDout.print("  " * callId.callDepth + "Returning")

    heap.popStackFrame(callId)

    heap.vivemDout.println()

    (callId, returnRef)
  }

  def getExternFunction(programH: ProgramH, ref: PrototypeH): (AdapterForExterns, Vector[ReferenceV]) => ReferenceV = {
    ref.fullName.toFullString() match {
      case """::F("__vbi_addI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.addI32
      case """::F("__vbi_addFloatFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.addFloatFloat
      case """::F("__vbi_panic")""" => VivemExterns.panic
      case """::F("__vbi_multiplyI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.multiplyI32
      case """::F("__vbi_subtractFloatFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.subtractFloatFloat
      case """::F("__vbi_divideI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.divideI32
      case """::F("__vbi_multiplyFloatFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.multiplyFloatFloat
      case """::F("__vbi_divideFloatFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.divideFloatFloat
      case """::F("__vbi_subtractI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.subtractI32
      case """::F("__vale_addStr",[],[R(*,>,#,s),R(*,<,#,i(32)),R(*,<,#,i(32)),R(*,>,#,s),R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.addStrStr
      case """ioutils::F("__getch")""" => VivemExterns.getch
      case """::F("__vbi_eqFloatFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.eqFloatFloat
      case """math::F("sqrt",[],[R(*,<,#,f)])""" => VivemExterns.sqrt
      case """::F("__vbi_lessThanI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.lessThanI32
      case """::F("__vbi_lessThanFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.lessThanFloat
      case """::F("__vbi_greaterThanOrEqI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.greaterThanOrEqI32
      case """::F("__vbi_eqI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.eqI32
      case """::F("__vbi_eqBoolBool",[],[R(*,<,#,b),R(*,<,#,b)])""" => VivemExterns.eqBoolBool
      case """::F("__vprintStr",[],[R(*,>,#,s),R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.print
      case """::F("__vbi_not",[],[R(*,<,#,b)])""" => VivemExterns.not
      case """::F("__vale_castI32Str",[],[R(*,<,#,i(32))])""" => VivemExterns.castI32Str
      case """::F("__vale_castI32Float",[],[R(*,<,#,i(32))])""" => VivemExterns.castI32Float
      case """::F("__vale_castFloatI32",[],[R(*,<,#,f)])""" => VivemExterns.castFloatI32
      case """::F("__vbi_lessThanOrEqI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.lessThanOrEqI32
      case """::F("__vbi_and",[],[R(*,<,#,b),R(*,<,#,b)])""" => VivemExterns.and
      case """::F("__vbi_or",[],[R(*,<,#,b),R(*,<,#,b)])""" => VivemExterns.or
      case """::F("__vbi_modI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.modI32
      case """::F("__vbi_strLength",[],[R(*,>,#,s)])""" => VivemExterns.strLength
      case """::F("__vale_castFloatStr",[],[R(*,<,#,f)])""" => VivemExterns.castFloatStr
      case """::F("vstr_eq",[],[R(*,>,#,s),R(*,<,#,i(32)),R(*,<,#,i(32)),R(*,>,#,s),R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.eqStrStr
      case """::F("__vbi_negateFloat",[],[R(*,<,#,f)])""" => VivemExterns.negateFloat
      case """::F("__vbi_addI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.addI64
      case """::F("__vbi_multiplyI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.multiplyI64
      case """::F("__vbi_divideI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.divideI64
      case """::F("__vbi_subtractI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.subtractI64
      case """::F("__vbi_lessThanI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.lessThanI64
      case """::F("__vbi_greaterThanOrEqI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.greaterThanOrEqI64
      case """::F("__vbi_eqI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.eqI64
      case """::F("__vbi_castI64Str",[],[R(*,<,#,i(64))])""" => VivemExterns.castI64Str
      case """::F("__vbi_castI64Float",[],[R(*,<,#,i(64))])""" => VivemExterns.castI64Float
      case """::F("__vbi_castFloatI64",[],[R(*,<,#,f)])""" => VivemExterns.castFloatI64
      case """::F("__vbi_lessThanOrEqI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.lessThanOrEqI64
      case """::F("__vbi_modI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.modI64


      case _ => vimpl(ref.fullName.toFullString())
    }
  }
}
