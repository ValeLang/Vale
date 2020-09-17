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

    val rootExpressionId = ExpressionId(callId, List())
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
      case """F("__addIntInt",[],[R(*,<,i),R(*,<,i)])""" => VivemExterns.addIntInt
      case """F("__addFloatFloat",[],[R(*,<,f),R(*,<,f)])""" => VivemExterns.addFloatFloat
      case """F("__panic")""" => VivemExterns.panic
      case """F("__multiplyIntInt",[],[R(*,<,i),R(*,<,i)])""" => VivemExterns.multiplyIntInt
      case """F("__subtractFloatFloat",[],[R(*,<,f),R(*,<,f)])""" => VivemExterns.subtractFloatFloat
      case """F("__divideIntInt",[],[R(*,<,i),R(*,<,i)])""" => VivemExterns.divideIntInt
//      case PrototypeH(FullNameH(List(NamePartH("__multiplyFloatFloat", Some(List()), Some(List(ReferenceH(m.Share,FloatH()), ReferenceH(m.Share,FloatH()))), None))), List(ReferenceH(m.Share,FloatH()), ReferenceH(m.Share,FloatH())), ReferenceH(m.Share,FloatH())) =>
//        VivemExterns.multiplyFloatFloat
      case """F("__subtractIntInt",[],[R(*,<,i),R(*,<,i)])""" => VivemExterns.subtractIntInt
      case """F("__addStrStr",[],[R(*,>,s),R(*,>,s)])""" => VivemExterns.addStrStr
      case """F("__getch")""" => VivemExterns.getch
      case """F("sqrt",[],[R(*,<,f)])""" => VivemExterns.sqrt
//      case PrototypeH(FullNameH(List(NamePartH("__sqrt", Some(List()), Some(List(ReferenceH(m.Share,FloatH()))), None))),List(ReferenceH(m.Share,FloatH())),ReferenceH(m.Share,FloatH())) =>
//        VivemExterns.sqrt
      case """F("__lessThanInt",[],[R(*,<,i),R(*,<,i)])""" => VivemExterns.lessThanInt
      case """F("__lessThanFloat",[],[R(*,<,f),R(*,<,f)])""" => VivemExterns.lessThanFloat
//      case PrototypeH(FullNameH(List(NamePartH("__greaterThanFloat", Some(List()), Some(List(ReferenceH(m.Share,FloatH()), ReferenceH(m.Share,FloatH()))), None))), List(ReferenceH(m.Share,FloatH()), ReferenceH(m.Share,FloatH())), ReferenceH(m.Share,BoolH())) =>
//        VivemExterns.greaterThanFloat
//      case PrototypeH(FullNameH(List(NamePartH("__greaterThanInt", Some(List()), Some(List(ReferenceH(m.Share,IntH()), ReferenceH(m.Share,IntH()))), None))), List(ReferenceH(m.Share,IntH()), ReferenceH(m.Share,IntH())), ReferenceH(m.Share,BoolH())) =>
//        VivemExterns.greaterThanInt
      case """F("__eqStrStr",[],[R(*,>,s),R(*,>,s)])""" => VivemExterns.eqStrStr
      case """F("__greaterThanOrEqInt",[],[R(*,<,i),R(*,<,i)])""" => VivemExterns.greaterThanOrEqInt
      case """F("__eqIntInt",[],[R(*,<,i),R(*,<,i)])""" => VivemExterns.eqIntInt
      case """F("__eqBoolBool",[],[R(*,<,b),R(*,<,b)])""" => VivemExterns.eqBoolBool
      case """F("__print",[],[R(*,>,s)])""" => VivemExterns.print
      case """F("__not",[],[R(*,<,b)])""" => VivemExterns.not
      case """F("__castIntStr",[],[R(*,<,i)])""" => VivemExterns.castIntStr
      case """F("__castIntFloat",[],[R(*,<,i)])""" => VivemExterns.castIntFloat
      case """F("__castFloatInt",[],[R(*,<,f)])""" => VivemExterns.castFloatInt
      case """F("__lessThanOrEqInt",[],[R(*,<,i),R(*,<,i)])""" => VivemExterns.lessThanOrEqInt
//      case PrototypeH(FullNameH(List(NamePartH("__castFloatStr", Some(List()), Some(List(ReferenceH(m.Share,FloatH()))), None))),List(ReferenceH(m.Share,FloatH())),ReferenceH(m.Share,StrH())) =>
//        VivemExterns.castFloatStr
      case """F("__and",[],[R(*,<,b),R(*,<,b)])""" => VivemExterns.and
      case """F("__or",[],[R(*,<,b),R(*,<,b)])""" => VivemExterns.or
      case """F("__mod",[],[R(*,<,i),R(*,<,i)])""" => VivemExterns.mod
      case _ => vimpl(ref.fullName.toString)
    }
  }
}
