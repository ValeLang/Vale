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

    ref.fullName.fullyQualifiedName
      // The tests have a mode where they can interpret the builtins as separate packages, instead
      // of pulling it all in as one giant namespace. In that case, it prefixes things such as
      // v::builtins::arith. We can add other prefixes here too as needed.
      .replaceAllLiterally("v::builtins::arith", "") match {
      case """__vbi_addI32(i32, i32)""" => VivemExterns.addI32
      case """__vbi_addFloatFloat(float, float)""" => VivemExterns.addFloatFloat
      case """__vbi_panic""" => VivemExterns.panic
      case """__vbi_multiplyI32(i32, i32)""" => VivemExterns.multiplyI32
      case """__vbi_subtractFloatFloat(float, float)""" => VivemExterns.subtractFloatFloat
      case """__vbi_divideI32(i32, i32)""" => VivemExterns.divideI32
      case """__vbi_multiplyFloatFloat(float, float)""" => VivemExterns.multiplyFloatFloat
      case """__vbi_divideFloatFloat(float, float)""" => VivemExterns.divideFloatFloat
      case """__vbi_subtractI32(i32, i32)""" => VivemExterns.subtractI32
      case """addStr(str, i32, i32, str, i32, i32)""" => VivemExterns.addStrStr
      case """__getch""" => VivemExterns.getch
      case """__vbi_eqFloatFloat(float, float)""" => VivemExterns.eqFloatFloat
      case """sqrt(float)""" => VivemExterns.sqrt
      case """__vbi_lessThanI32(i32, i32)""" => VivemExterns.lessThanI32
      case """__vbi_lessThanFloat(float, float)""" => VivemExterns.lessThanFloat
      case """__vbi_greaterThanOrEqI32(i32, i32)""" => VivemExterns.greaterThanOrEqI32
      case """__vbi_greaterThanI32(i32, i32)""" => VivemExterns.greaterThanI32
      case """__vbi_eqI32(i32, i32)""" => VivemExterns.eqI32
      case """__vbi_eqBoolBool(bool, bool)""" => VivemExterns.eqBoolBool
      case """printstr(str, i32, i32)""" => VivemExterns.print
      case """__vbi_not(bool)""" => VivemExterns.not
      case """castI32Str(i32)""" => VivemExterns.castI32Str
      case """castI64Str(i64)""" => VivemExterns.castI64Str
      case """castI32Float(i32)""" => VivemExterns.castI32Float
      case """castFloatI32(float)""" => VivemExterns.castFloatI32
      case """__vbi_lessThanOrEqI32(i32, i32)""" => VivemExterns.lessThanOrEqI32
      case """__vbi_and(bool, bool)""" => VivemExterns.and
      case """__vbi_or(bool, bool)""" => VivemExterns.or
      case """__vbi_modI32(i32, i32)""" => VivemExterns.modI32
      case """__vbi_strLength(str)""" => VivemExterns.strLength
      case """castFloatStr(float)""" => VivemExterns.castFloatStr
      case """streq(str, i32, i32, str, i32, i32)""" => VivemExterns.eqStrStr
      case """__vbi_negateFloat(float)""" => VivemExterns.negateFloat
      case """__vbi_addI64(i64, i64)""" => VivemExterns.addI64
      case """__vbi_multiplyI64(i64, i64)""" => VivemExterns.multiplyI64
      case """__vbi_divideI64(i64, i64)""" => VivemExterns.divideI64
      case """__vbi_subtractI64(i64, i64)""" => VivemExterns.subtractI64
      case """__vbi_lessThanI64(i64, i64)""" => VivemExterns.lessThanI64
      case """__vbi_greaterThanOrEqI64(i64, i64)""" => VivemExterns.greaterThanOrEqI64
      case """__vbi_eqI64(i64, i64)""" => VivemExterns.eqI64
      case """__vbi_castI64Str(i64)""" => VivemExterns.castI64Str
      case """__vbi_castI64Float(i64)""" => VivemExterns.castI64Float
      case """__vbi_castFloatI64(float)""" => VivemExterns.castFloatI64
      case """__vbi_lessThanOrEqI64(i64, i64)""" => VivemExterns.lessThanOrEqI64
      case """__vbi_modI64(i64, i64)""" => VivemExterns.modI64
      case """TruncateI64ToI32(i64)""" => VivemExterns.truncateI64ToI32

      case _ => vimpl(ref.fullName.fullyQualifiedName)
    }
  }
}
