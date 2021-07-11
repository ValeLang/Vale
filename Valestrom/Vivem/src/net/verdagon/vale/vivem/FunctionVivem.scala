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
      case """::F("__addI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.addI32
      case """::F("__addFloatFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.addFloatFloat
      case """::F("__panic")""" => VivemExterns.panic
      case """::F("__multiplyI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.multiplyI32
      case """::F("__subtractFloatFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.subtractFloatFloat
      case """::F("__divideI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.divideI32
      case """::F("__multiplyFloatFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.multiplyFloatFloat
      case """::F("__divideFloatFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.divideFloatFloat
      case """::F("__subtractI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.subtractI32
      case """::F("__vaddStr",[],[R(*,>,#,s),R(*,<,#,i(32)),R(*,<,#,i(32)),R(*,>,#,s),R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.addStrStr
      case """ioutils::F("__getch")""" => VivemExterns.getch
      case """::F("__eqFloatFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.eqFloatFloat
      case """math::F("sqrt",[],[R(*,<,#,f)])""" => VivemExterns.sqrt
      case """::F("__lessThanI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.lessThanI32
      case """::F("__lessThanFloat",[],[R(*,<,#,f),R(*,<,#,f)])""" => VivemExterns.lessThanFloat
      case """::F("__greaterThanOrEqI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.greaterThanOrEqI32
      case """::F("__eqI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.eqI32
      case """::F("__eqBoolBool",[],[R(*,<,#,b),R(*,<,#,b)])""" => VivemExterns.eqBoolBool
      case """::F("__vprintStr",[],[R(*,>,#,s),R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.print
      case """::F("__not",[],[R(*,<,#,b)])""" => VivemExterns.not
      case """::F("__castI32Str",[],[R(*,<,#,i(32))])""" => VivemExterns.castI32Str
      case """::F("__castI32Float",[],[R(*,<,#,i(32))])""" => VivemExterns.castI32Float
      case """::F("__castFloatI32",[],[R(*,<,#,f)])""" => VivemExterns.castFloatI32
      case """::F("__lessThanOrEqI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.lessThanOrEqI32
      case """::F("__and",[],[R(*,<,#,b),R(*,<,#,b)])""" => VivemExterns.and
      case """::F("__or",[],[R(*,<,#,b),R(*,<,#,b)])""" => VivemExterns.or
      case """::F("__modI32",[],[R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.modI32
      case """::F("__strLength",[],[R(*,>,#,s)])""" => VivemExterns.strLength
      case """::F("__castFloatStr",[],[R(*,<,#,f)])""" => VivemExterns.castFloatStr
      case """::F("vstr_eq",[],[R(*,>,#,s),R(*,<,#,i(32)),R(*,<,#,i(32)),R(*,>,#,s),R(*,<,#,i(32)),R(*,<,#,i(32))])""" => VivemExterns.eqStrStr
      case """::F("__negateFloat",[],[R(*,<,#,f)])""" => VivemExterns.negateFloat      case """::F("__addI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.addI64
      case """::F("__multiplyI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.multiplyI64
      case """::F("__divideI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.divideI64
      case """::F("__subtractI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.subtractI64
      case """::F("__lessThanI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.lessThanI64
      case """::F("__greaterThanOrEqI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.greaterThanOrEqI64
      case """::F("__eqI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.eqI64
      case """::F("__castI64Str",[],[R(*,<,#,i(64))])""" => VivemExterns.castI64Str
      case """::F("__castI64Float",[],[R(*,<,#,i(64))])""" => VivemExterns.castI64Float
      case """::F("__castFloatI64",[],[R(*,<,#,f)])""" => VivemExterns.castFloatI64
      case """::F("__lessThanOrEqI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.lessThanOrEqI64
      case """::F("__modI64",[],[R(*,<,#,i(64)),R(*,<,#,i(64))])""" => VivemExterns.modI64


      case _ => vimpl(ref.fullName.toFullString())
    }
  }
}
