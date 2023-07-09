package dev.vale.testvm

import dev.vale.finalast.{FunctionH, ProgramH, PrototypeH}
import dev.vale.testvm.ExpressionVivem.NodeReturn
import dev.vale.finalast._
import ExpressionVivem.{NodeBreak, NodeContinue, NodeReturn}
import dev.vale.{vimpl, vwat}
import dev.vale.{finalast => m}

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

    heap.vivemDout.print("  " * callId.callDepth + "Entering function " + callId)

    // Increment all the args to show that they have arguments referring to them.
    // These will be decremented at some point in the callee function.
    args.indices.foreach(argIndex => {
      heap.incrementReferenceRefCount(
        ArgumentToObjectReferrer(ArgumentId(callId, argIndex), args(argIndex).ownership),
        args(argIndex))
    })

    heap.vivemDout.println()

    val rootExpressionId = ExpressionId(callId, Vector.empty)
    val returnRef =
      ExpressionVivem.executeNode(programH, stdin, stdout, heap, rootExpressionId, functionH.body) match {
        case NodeReturn(r) => NodeReturn(r)
        case NodeBreak() => vwat()
        case NodeContinue(r) => NodeReturn(r)
      }

    heap.vivemDout.println()
    heap.vivemDout.print("  " * callId.callDepth + "Returning")

    heap.popStackFrame(callId)

    heap.vivemDout.println()

    (callId, returnRef)
  }

  def getExternFunction(programH: ProgramH, ref: PrototypeH): (AdapterForExterns, Vector[ReferenceV]) => ReferenceV = {
    ref.id.fullyQualifiedName
      // The tests have a mode where they can interpret the builtins as separate packages, instead
      // of pulling it all in as one giant namespace. In that case, it prefixes things such as
      // v::builtins::arith. We can add other prefixes here too as needed.
      .replaceAllLiterally("v::builtins::arith", "") match {
      case """__vbi_addI32(i32, i32)""" => VivemExterns.addI32
      case """__vbi_addFloatFloat""" => VivemExterns.addFloatFloat
      case """__vbi_panic""" => VivemExterns.panic
      case """__vbi_multiplyI32""" => VivemExterns.multiplyI32
      case """__vbi_subtractFloatFloat""" => VivemExterns.subtractFloatFloat
      case """__vbi_divideI32""" => VivemExterns.divideI32
      case """__vbi_multiplyFloatFloat""" => VivemExterns.multiplyFloatFloat
      case """__vbi_divideFloatFloat""" => VivemExterns.divideFloatFloat
      case """__vbi_subtractI32""" => VivemExterns.subtractI32
      case """addStr""" => VivemExterns.addStrStr
      case """__getch""" => VivemExterns.getch
      case """__vbi_eqFloatFloat""" => VivemExterns.eqFloatFloat
      case """sqrt""" => VivemExterns.sqrt
      case """__vbi_lessThanI32""" => VivemExterns.lessThanI32
      case """__vbi_lessThanFloat""" => VivemExterns.lessThanFloat
      case """__vbi_greaterThanOrEqI32""" => VivemExterns.greaterThanOrEqI32
      case """__vbi_greaterThanI32""" => VivemExterns.greaterThanI32
      case """__vbi_eqI32""" => VivemExterns.eqI32
      case """__vbi_eqBoolBool""" => VivemExterns.eqBoolBool
      case """printstr""" => VivemExterns.print
      case """__vbi_not""" => VivemExterns.not
      case """castI32Str""" => VivemExterns.castI32Str
      case """castI64Str""" => VivemExterns.castI64Str
      case """castI32Float""" => VivemExterns.castI32Float
      case """castFloatI32""" => VivemExterns.castFloatI32
      case """__vbi_lessThanOrEqI32""" => VivemExterns.lessThanOrEqI32
      case """__vbi_and""" => VivemExterns.and
      case """__vbi_or""" => VivemExterns.or
      case """__vbi_modI32""" => VivemExterns.modI32
      case """__vbi_strLength""" => VivemExterns.strLength
      case """castFloatStr""" => VivemExterns.castFloatStr
      case """streq""" => VivemExterns.eqStrStr
      case """__vbi_negateFloat""" => VivemExterns.negateFloat
      case """__vbi_addI64""" => VivemExterns.addI64
      case """__vbi_multiplyI64""" => VivemExterns.multiplyI64
      case """__vbi_divideI64""" => VivemExterns.divideI64
      case """__vbi_subtractI64""" => VivemExterns.subtractI64
      case """__vbi_lessThanI64""" => VivemExterns.lessThanI64
      case """__vbi_greaterThanOrEqI64""" => VivemExterns.greaterThanOrEqI64
      case """__vbi_eqI64""" => VivemExterns.eqI64
      case """__vbi_castI64Str""" => VivemExterns.castI64Str
      case """__vbi_castI64Float""" => VivemExterns.castI64Float
      case """__vbi_castFloatI64""" => VivemExterns.castFloatI64
      case """__vbi_lessThanOrEqI64""" => VivemExterns.lessThanOrEqI64
      case """__vbi_modI64""" => VivemExterns.modI64
      case """TruncateI64ToI32""" => VivemExterns.truncateI64ToI32
      case _ => vimpl(ref.id.fullyQualifiedName)
    }
  }
}
