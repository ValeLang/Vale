package dev.vale.finalast

import dev.vale.{vassert, vcurious, vfail, vwat}
import dev.vale.vimpl

// Common trait for all instructions.
sealed trait ExpressionH[+T <: KindHT] {
  def resultType: CoordH[T]

  // Convenience functions for accessing this expression as the kind returning
  // a certain type.
  def expectStructAccess(): ExpressionH[StructHT] = {
    resultType match {
      case CoordH(_, _, x @ StructHT(_)) => {
        this.asInstanceOf[ExpressionH[StructHT]]
      }
      case _ => vfail()
    }
  }
  def expectInterfaceAccess(): ExpressionH[InterfaceHT] = {
    resultType match {
      case CoordH(_, _, x @ InterfaceHT(_)) => {
        this.asInstanceOf[ExpressionH[InterfaceHT]]
      }
    }
  }
  def expectRuntimeSizedArrayAccess(): ExpressionH[RuntimeSizedArrayHT] = {
    resultType match {
      case CoordH(_, _, x @ RuntimeSizedArrayHT(_)) => {
        this.asInstanceOf[ExpressionH[RuntimeSizedArrayHT]]
      }
    }
  }
  def expectStaticSizedArrayAccess(): ExpressionH[StaticSizedArrayHT] = {
    resultType match {
      case CoordH(_, _, x @ StaticSizedArrayHT(_)) => {
        this.asInstanceOf[ExpressionH[StaticSizedArrayHT]]
      }
    }
  }
  def expectIntAccess(): ExpressionH[IntHT] = {
    resultType match {
      case CoordH(_, _, x @ IntHT(32)) => {
        this.asInstanceOf[ExpressionH[IntHT]]
      }
    }
  }
  def expectI64Access(): ExpressionH[IntHT] = {
    resultType match {
      case CoordH(_, _, x @ IntHT(64)) => {
        this.asInstanceOf[ExpressionH[IntHT]]
      }
    }
  }
  def expectBoolAccess(): ExpressionH[BoolHT] = {
    resultType match {
      case CoordH(_, _, x @ BoolHT()) => {
        this.asInstanceOf[ExpressionH[BoolHT]]
      }
    }
  }
}

// Produces a void.
case class ConstantVoidH() extends ExpressionH[VoidHT] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = 1337
  override def resultType: CoordH[VoidHT] = CoordH(ShareH, InlineH, VoidHT())
}

// Produces an integer.
case class ConstantIntH(
  // The value of the integer.
  value: Long,
  bits: Int
) extends ExpressionH[IntHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[IntHT] = CoordH(ShareH, InlineH, IntHT(bits))
}

// Produces a boolean.
case class ConstantBoolH(
  // The value of the boolean.
  value: Boolean
) extends ExpressionH[BoolHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[BoolHT] = CoordH(ShareH, InlineH, BoolHT())
}

// Produces a string.
case class ConstantStrH(
  // The value of the string.
  value: String
) extends ExpressionH[StrHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[StrHT] = CoordH(ShareH, YonderH, StrHT())
}

// Produces a float.
case class ConstantF64H(
  // The value of the float.
  value: Double
) extends ExpressionH[FloatHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[FloatHT] = CoordH(ShareH, InlineH, FloatHT())
}

// Produces the value from an argument.
// There can only be one of these per argument; this conceptually destroys
// the containing argument and produces its value.
case class ArgumentH(
  resultType: CoordH[KindHT],
  // The index of the argument, starting at 0.
  argumentIndex: Int
) extends ExpressionH[KindHT]

// Takes a value from the source expression and puts it into a local
// variable on the stack.
case class StackifyH(
  // The expressions to read a value from.
  sourceExpr: ExpressionH[KindHT],
  // Describes the local we're making.
  local: Local,
  // Name of the local variable. Used for debugging.
  name: Option[IdH]
) extends ExpressionH[KindHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();

  // See BRCOBS, source shouldn't be Never.
  sourceExpr.resultType.kind match { case NeverHT(_) => vwat() case _ => }
  vassert(sourceExpr.resultType == local.typeH)

  override def resultType: CoordH[VoidHT] = CoordH(ShareH, InlineH, VoidHT())
}

// Takes a value from a local variable on the stack, and produces it.
// The local variable is now invalid, since its value has been taken out.
// See LocalLoadH for a similar instruction that *doesnt* invalidate the local var.
case class UnstackifyH(
  // Describes the local we're pulling from. This is equal to the corresponding
  // StackifyH's `local` member.
  local: Local
) extends ExpressionH[KindHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  // Panics if this is ever not the case.
  vcurious(local.typeH == resultType)

  override def resultType: CoordH[KindHT] = local.typeH
}

// Takes a struct from the given expressions, and destroys it.
// All of its members are saved from the jaws of death, and put into the specified
// local variables.
// This creates those local variables, much as a StackifyH would, and puts into them
// the values from the dying struct instance.
case class DestroyH(
  // The expressions to take the struct from.
  structExpression: ExpressionH[StructHT],
  // A list of types, one for each local variable we'll make.
  // TODO: If the vcurious below doesn't panic, get rid of this redundant member.
  localTypes: Vector[CoordH[KindHT]],
  // The locals to put the struct's members into.
  localIndices: Vector[Local],
) extends ExpressionH[VoidHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  vassert(localTypes.size == localIndices.size)
  vcurious(localTypes == localIndices.map(_.typeH).toVector)

  // See BRCOBS, source shouldn't be Never.
  // Nevermind, type system guarantees it
  // structExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }

  override def resultType: CoordH[VoidHT] = CoordH(ShareH, InlineH, VoidHT())
}

// Takes a struct from the given expressions, and destroys it.
// All of its members are saved from the jaws of death, and put into the specified
// local variables.
// This creates those local variables, much as a StackifyH would, and puts into them
// the values from the dying struct instance.
case class DestroyStaticSizedArrayIntoLocalsH(
  // The expressions to take the struct from.
  structExpression: ExpressionH[StaticSizedArrayHT],
  // A list of types, one for each local variable we'll make.
  // TODO: If the vcurious below doesn't panic, get rid of this redundant member.
  localTypes: Vector[CoordH[KindHT]],
  // The locals to put the struct's members into.
  localIndices: Vector[Local]
) extends ExpressionH[VoidHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  vassert(localTypes.size == localIndices.size)
  vcurious(localTypes == localIndices.map(_.typeH).toVector)

  // See BRCOBS, source shouldn't be Never.
  // Nevermind, type system guarantees it
  // structExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }

  override def resultType: CoordH[VoidHT] = CoordH(ShareH, InlineH, VoidHT())
}

// Takes a struct reference from the "source" expressions, and makes an interface reference
// to it, as the "target" reference, and puts it into another expressions.
case class StructToInterfaceUpcastH(
  // The expressions to get the struct reference from.
  sourceExpression: ExpressionH[StructHT],
  // The type of interface to cast to.
  targetInterface: InterfaceHT
) extends ExpressionH[InterfaceHT] {

  // See BRCOBS, source shouldn't be Never.
  // Nevermind, type system guarantees it
  //  sourceExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  // The resulting type will have the same ownership as the source expressions had.
  def resultType = CoordH(sourceExpression.resultType.ownership, sourceExpression.resultType.location, targetInterface)
}

// Takes an interface reference from the "source" expressions, and makes another reference
// to it, as the "target" inference, and puts it into another expressions.
case class InterfaceToInterfaceUpcastH(
  // The expressions to get the source interface reference from.
  sourceExpression: ExpressionH[InterfaceHT],
  // The type of interface to cast to.
  targetInterface: InterfaceHT
) extends ExpressionH[InterfaceHT] {

  // See BRCOBS, source shouldn't be Never.
  // Nevermind, type system guarantees it
  // sourceExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  // The resulting type will have the same ownership as the source expressions had.
  def resultType = CoordH(sourceExpression.resultType.ownership, sourceExpression.resultType.location, targetInterface)
}

// Takes a reference from the given "source" expressions, and puts it into an *existing*
// local variable.
case class LocalStoreH(
  // The existing local to store into.
  local: Local,
  // The expressions to get the source reference from.
  sourceExpression: ExpressionH[KindHT],
  // Name of the local variable, for debug purposes.
  localName: IdH
) extends ExpressionH[KindHT] {

  // See BRCOBS, source shouldn't be Never.
  sourceExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[KindHT] = local.typeH
}

// Takes a reference from the given local variable, and puts it into a new expressions.
// This can never move a reference, only alias it. The instruction which can move a
// reference is UnstackifyH.
case class LocalLoadH(
  // The existing local to load from.
  local: Local,
  // The ownership of the resulting reference. This doesn't have to
  // match the ownership of the source reference. For example, we might want
  // to load a borrow reference from an owning local.
  targetOwnership: OwnershipH,
  // Name of the local variable, for debug purposes.
  localName: IdH
) extends ExpressionH[KindHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  vassert(targetOwnership != OwnH) // must unstackify to get an owning reference

  override def resultType: CoordH[KindHT] = {
    val location =
      (targetOwnership, local.typeH.location) match {
        case (BorrowH, _) => YonderH
        case (WeakH, _) => YonderH
        case (OwnH, location) => location
        case (ShareH, location) => location
      }
    CoordH(targetOwnership, location, local.typeH.kind)
  }
}

// Takes a reference from the given "source" expressions, and swaps it into the given
// struct's member. The member's old reference is put into a new expressions.
case class MemberStoreH(
  resultType: CoordH[KindHT],
  // Expression containing a reference to the struct whose member we will swap.
  structExpression: ExpressionH[StructHT],
  // Which member to swap out, starting at 0.
  memberIndex: Int,
  // Expression containing the new value for the struct's member.
  sourceExpression: ExpressionH[KindHT],
  // Name of the member, for debug purposes.
  memberName: IdH
) extends ExpressionH[KindHT] {

  // See BRCOBS, struct shouldn't be Never.
  // Nevermind, type system guarantees it
  //  structExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
  // See BRCOBS, source shouldn't be Never.
  sourceExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

}

// Takes a reference from the given "struct" expressions, and copies it into a new
// expressions. This can never move a reference, only alias it.
case class MemberLoadH(
  // Expression containing a reference to the struct whose member we will read.
  structExpression: ExpressionH[StructHT],
  // Which member to read from, starting at 0.
  memberIndex: Int,
  // The type we expect the member to be. This can easily be looked up, but is provided
  // here to be convenient for LLVM.
  expectedMemberType: CoordH[KindHT],
  // The type of the resulting reference.
  resultType: CoordH[KindHT],
  // Member's name, for debug purposes.
  memberName: IdH
) extends ExpressionH[KindHT] {

  // See BRCOBS, source shouldn't be Never.
  // Nevermind, type system guarantees it
  //  structExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
//  vassert(resultType.ownership == targetOwnership)
//  vassert(resultType.permission == targetPermission)

  if (resultType.ownership == WeakH) vassert(resultType.location == YonderH)
}

// Produces an array whose size is fixed and known at compile time, and puts it into
// a expressions.
case class NewArrayFromValuesH(
  // The resulting type of the array.
  // TODO: See if we can infer this from the types in the expressions.
  resultType: CoordH[StaticSizedArrayHT],
  // The expressions from which we'll get the values to put into the array.
  sourceExpressions: Vector[ExpressionH[KindHT]]
) extends ExpressionH[StaticSizedArrayHT] {

  // See BRCOBS, source shouldn't be Never.
  sourceExpressions.foreach(expr => {
    expr.resultType.kind match { case NeverHT(_) => vwat() case _ => }
  })
}

// Loads from the "source" expressions and swaps it into the array from arrayExpression at
// the position specified by the integer in indexExpression. The old value from the
// array is moved out into expressionsId.
// This is for the kind of array whose size we know at compile time, the kind that
// doesn't need to carry around a size. For the corresponding instruction for the
// unknown-size-at-compile-time array, see RuntimeSizedArrayStoreH.
case class StaticSizedArrayStoreH(
  // Expression containing the array whose element we'll swap out.
  arrayExpression: ExpressionH[StaticSizedArrayHT],
  // Expression containing the index of the element we'll swap out.
  indexExpression: ExpressionH[IntHT],
  // Expression containing the value we'll swap into the array.
  sourceExpression: ExpressionH[KindHT],
  resultType: CoordH[KindHT],
) extends ExpressionH[KindHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  vassert(indexExpression.resultType.kind == IntHT.i32)

  // See BRCOBS, source shouldn't be Never.
  // Nevermind, type system guarantees it
  //  arrayExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
  //  indexExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
  sourceExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }
}

// Loads from the "source" expressions and swaps it into the array from arrayExpression at
// the position specified by the integer in indexExpression. The old value from the
// array is moved out into expressionsId.
// This is for the kind of array whose size we don't know at compile time, the kind
// that needs to carry around a size. For the corresponding instruction for the
// known-size-at-compile-time array, see StaticSizedArrayStoreH.
case class RuntimeSizedArrayStoreH(
  // Expression containing the array whose element we'll swap out.
  arrayExpression: ExpressionH[RuntimeSizedArrayHT],
  // Expression containing the index of the element we'll swap out.
  indexExpression: ExpressionH[IntHT],
  // Expression containing the value we'll swap into the array.
  sourceExpression: ExpressionH[KindHT],
  resultType: CoordH[KindHT],
) extends ExpressionH[KindHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  vassert(indexExpression.resultType.kind == IntHT.i32)

  // See BRCOBS, source shouldn't be Never.
  // Nevermind, type system guarantees it
  //  arrayExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
  //  indexExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
  sourceExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }
}

// Loads from the array in arrayExpression at the index in indexExpression, and stores
// the result in expressionsId. This can never move a reference, only alias it.
// This is for the kind of array whose size we don't know at compile time, the kind
// that needs to carry around a size. For the corresponding instruction for the
// known-size-at-compile-time array, see StaticSizedArrayLoadH.
case class RuntimeSizedArrayLoadH(
  // Expression containing the array whose element we'll read.
  arrayExpression: ExpressionH[RuntimeSizedArrayHT],
  // Expression containing the index of the element we'll read.
  indexExpression: ExpressionH[IntHT],
  // The ownership to load as. For example, we might load a borrow reference from a
  // owning Car reference element.
  targetOwnership: OwnershipH,
  expectedElementType: CoordH[KindHT],
  resultType: CoordH[KindHT],
) extends ExpressionH[KindHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  vassert(indexExpression.resultType.kind == IntHT.i32)

  // See BRCOBS, source shouldn't be Never.
  // Nevermind, type system guarantees it
  //arrayExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
  //indexExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
}

// Loads from the array in arrayExpression at the index in indexExpression, and stores
// the result in expressionsId. This can never move a reference, only alias it.
// This is for the kind of array whose size we know at compile time, the kind that
// doesn't need to carry around a size. For the corresponding instruction for the
// known-size-at-compile-time array, see StaticSizedArrayStoreH.
case class StaticSizedArrayLoadH(
  // Expression containing the array whose element we'll read.
  arrayExpression: ExpressionH[StaticSizedArrayHT],
  // Expression containing the index of the element we'll read.
  indexExpression: ExpressionH[IntHT],
  // The ownership to load as. For example, we might load a borrow reference from a
  // owning Car reference element.
  targetOwnership: OwnershipH,
  expectedElementType: CoordH[KindHT],
  arraySize: Long,
  resultType: CoordH[KindHT],
) extends ExpressionH[KindHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  vassert(indexExpression.resultType.kind == IntHT.i32)

  // See BRCOBS, source shouldn't be Never.
  // Nevermind, type system guarantees it
  //  arrayExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
  //  indexExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
}

// Calls a function.
case class CallH(
  // Identifies which function to call.
  function: PrototypeH,
  // Expressions containing the arguments to pass to the function.
  argsExpressions: Vector[ExpressionH[KindHT]]
) extends ExpressionH[KindHT] {
  // See BRCOBS, no arguments should be Never.
  argsExpressions.foreach(expr => {
    expr.resultType.kind match { case NeverHT(_) => vwat() case _ => }
  })

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[KindHT] = function.returnType
}

// Calls a function defined in some other module.
case class ExternCallH(
  // Identifies which function to call.
  function: PrototypeH,
  // Expressions containing the arguments to pass to the function.
  argsExpressions: Vector[ExpressionH[KindHT]]
) extends ExpressionH[KindHT] {
  // See BRCOBS, no arguments should be Never.
  argsExpressions.foreach(expr => {
    expr.resultType.kind match { case NeverHT(_) => vwat() case _ => }
  })

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[KindHT] = function.returnType
}

// Calls a function on an interface.
case class InterfaceCallH(
  // Expressions containing the arguments to pass to the function.
  argsExpressions: Vector[ExpressionH[KindHT]],
  // Which parameter has the interface whose table we'll read to get the function.
  virtualParamIndex: Int,
  // The type of the interface.
  // TODO: Take this out, it's redundant, can get it from argsExpressions[virtualParamIndex]
  interfaceH: InterfaceHT,
  // The index in the vtable for the function.
  indexInEdge: Int,
  // The function we expect to be calling. Note that this is the prototype for the abstract
  // function, not the prototype for the function that will eventually be called. The
  // difference is that this prototype will have an interface at the virtualParamIndex'th
  // parameter, and the function that is eventually called will have a struct there.
  functionType: PrototypeH
) extends ExpressionH[KindHT] {
  // See BRCOBS, no arguments should be Never.
  argsExpressions.foreach(expr => {
    expr.resultType.kind match { case NeverHT(_) => vwat() case _ => }
  })

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[KindHT] = functionType.returnType
  vassert(indexInEdge >= 0)
}

// An if-statement. It will get a boolean from running conditionBlock, and use it to either
// call thenBlock or elseBlock. The result of the thenBlock or elseBlock will be put into
// expressionsId.
case class IfH(
  // The block for the condition. If this results in a true, we'll run thenBlock, otherwise
  // we'll run elseBlock.
  conditionBlock: ExpressionH[BoolHT],
  // The block to run if conditionBlock results in a true. The result of this block will be
  // put into expressionsId.
  thenBlock: ExpressionH[KindHT],
  // The block to run if conditionBlock results in a false. The result of this block will be
  // put into expressionsId.
  elseBlock: ExpressionH[KindHT],

  commonSupertype: CoordH[KindHT],
) extends ExpressionH[KindHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  (thenBlock.resultType.kind, elseBlock.resultType.kind) match {
    case (NeverHT(false), _) =>
    case (_, NeverHT(false)) =>
    case (NeverHT(true), _) =>
    case (_, NeverHT(true)) =>
    case (a, b) if a == b =>
    case _ => vwat()
  }
  override def resultType: CoordH[KindHT] = commonSupertype
}

// A while loop. Continuously runs bodyBlock until it returns false.
case class WhileH(
  // The block to run until it returns false.
  bodyBlock: ExpressionH[KindHT]
) extends ExpressionH[KindHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();

  val resultCoord =
    bodyBlock.resultType.kind match {
      case VoidHT() => CoordH(ShareH, InlineH, VoidHT())
      case NeverHT(true) => CoordH(ShareH, InlineH, VoidHT())
      case NeverHT(false) => CoordH(ShareH, InlineH, NeverHT(false))
      case _ => vwat()
    }

  override def resultType: CoordH[KindHT] = resultCoord
}

// A collection of instructions. The last one will be used as the block's result.
case class ConsecutorH(
  // The instructions to run.
  exprs: Vector[ExpressionH[KindHT]],
) extends ExpressionH[KindHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  // We should simplify these away
  vassert(exprs.nonEmpty)

  exprs.init.foreach(nonLastResultLine => {
    // The init ones should never just be VoidLiteralHs, those should be stripped out.
    // Use Hammer.consecutive to conform to this.
    nonLastResultLine match {
      case NewStructH(Vector(), Vector(), CoordH(_, InlineH, _)) => vfail("Should be no creating voids in the middle of a consecutor!")
      case _ =>
    }

    // The init ones should always return void structs.
    // If there's a never somewhere in there, then there should be nothing afterward.
    // Use Hammer.consecutive to conform to this.
    vassert(nonLastResultLine.resultType == CoordH(ShareH, InlineH, VoidHT()))
  })

  val indexOfFirstNever =
    exprs.map(_.resultType.kind).indexWhere({ case NeverHT(_) => true case _ => false })
  if (indexOfFirstNever >= 0) {
    // The first never should be the last line. There shouldn't be anything after never.
    if (indexOfFirstNever != exprs.size - 1) {
      vfail()
    }
  }

  override def resultType: CoordH[KindHT] = exprs.last.resultType
}

//// A collection of instructions to evaluate, knowing that we'll never
//// finish evaluating, we'll panic before then.
//// This is used when we have a source expression that's a never, such as:
////   someFunc(Ship(), panic())
//// We'll turn it into:
////   __consecrash(Ship(), panic())
//// This is different from consecutor in that:
//// 1. We'll make sure we crash at the end.
//// 2. We're allowed to leak all of these inner exprs, such as that Ship()
//case class ConsecutorH(
//  // The instructions to run.
//  exprs: Vector[ExpressionH[KindH]],
//) extends ExpressionH[KindH] {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
//  // We should simplify these away
//  vassert(exprs.nonEmpty)
//
//  exprs.init.foreach(nonLastResultLine => {
//    // The init ones should never just be VoidLiteralHs, those should be stripped out.
//    // Use Hammer.consecutive to conform to this.
//    nonLastResultLine match {
//      case NewStructH(Vector(), Vector(), ReferenceH(_, InlineH, _, _)) => vfail("Should be no creating voids in the middle of a consecutor!")
//      case _ =>
//    }
//
//    // The init ones should always return void structs.
//    // If there's a never somewhere in there, then there should be nothing afterward.
//    // Use Hammer.consecutive to conform to this.
//    vassert(nonLastResultLine.resultType == ReferenceH(ShareH, InlineH, VoidH()))
//  })
//
//  val indexOfFirstNever = exprs.indexWhere(_.resultType.kind == NeverH())
//  if (indexOfFirstNever >= 0) {
//    // The first never should be the last line. There shouldn't be anything after never.
//    if (indexOfFirstNever != exprs.size - 1) {
//      vfail()
//    }
//  }
//
//  override def resultType: ReferenceH[KindH] = exprs.last.resultType
//}

// An expression where all locals declared inside will be destroyed by the time we exit.
case class BlockH(
  // The instructions to run. This will probably be a consecutor.
  inner: ExpressionH[KindHT],
) extends ExpressionH[KindHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[KindHT] = inner.resultType
}

// Ends the current function and returns a reference. A function will always end
// with a return statement.
case class ReturnH(
  // The expressions to read from, whose value we'll return from the function.
  sourceExpression: ExpressionH[KindHT]
) extends ExpressionH[NeverHT] {
  // See BRCOBS, no arguments should be Never.
  sourceExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[NeverHT] = CoordH(ShareH, InlineH, NeverHT(false))
}

// Constructs an immutable unknown-size array, whose length is the integer from sizeExpression,
// whose values are generated from the function from generatorExpression. Puts the
// result in a new expressions.
case class NewImmRuntimeSizedArrayH(
  // Expression containing the size of the new array.
  sizeExpression: ExpressionH[IntHT],
  // Expression containing the IFunction<int, T> interface reference which we'll
  // call to generate each element of the array.
  // More specifically, we'll call the "__call" function on the interface, which
  // should be the only function on it.
  // This is a borrow reference.
  generatorExpression: ExpressionH[KindHT],
  // The prototype for the "__call" function to call on the interface for each element.
  generatorMethod: PrototypeH,

  elementType: CoordH[KindHT],
  // The resulting type of the array.
  // TODO: Remove this, it's redundant with the generatorExpression's interface's
  // only method's return type.
  resultType: CoordH[RuntimeSizedArrayHT]
) extends ExpressionH[RuntimeSizedArrayHT] {
  // See BRCOBS, no arguments should be Never.
  // Nevermind, type system guarantees it
  //  sizeExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
  generatorExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  generatorExpression.resultType.ownership match {
    case ShareH | BorrowH =>
    case other => vwat(other)
  }
  vassert(sizeExpression.resultType.kind == IntHT.i32)
}

// Constructs an empty mutable unknown-size array, whose length is the integer from capacityExpression,
// whose values are generated from the function from generatorExpression. Puts the
// result in a new expressions.
case class NewMutRuntimeSizedArrayH(
  // Expression containing the capacity of the new array.
  capacityExpression: ExpressionH[IntHT],

  elementType: CoordH[KindHT],

  // The resulting type of the array.
  resultType: CoordH[RuntimeSizedArrayHT]
) extends ExpressionH[RuntimeSizedArrayHT] {
  // See BRCOBS, no arguments should be Never.
  // Nevermind, type system guarantees it
//  capacityExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
}

// Adds a new element to the end of a mutable unknown-size array.
case class PushRuntimeSizedArrayH(
  // Expression for the array to add to.
  arrayExpression: ExpressionH[RuntimeSizedArrayHT],
  // Expression for the new element.
  newcomerExpression: ExpressionH[KindHT],
) extends ExpressionH[KindHT] {
  // See BRCOBS, no arguments should be Never.
  // Nevermind, type system guarantees it
//  arrayExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
  newcomerExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();

  override def resultType: CoordH[KindHT] = CoordH(ShareH, InlineH, VoidHT())
}

// Adds a new element to the end of a mutable unknown-size array.
case class PopRuntimeSizedArrayH(
  // Expression for the array to add to.
  arrayExpression: ExpressionH[RuntimeSizedArrayHT],
  // The element type for the array.
  elementType: CoordH[KindHT]
) extends ExpressionH[KindHT] {
  // See BRCOBS, no arguments should be Never.
  // Nevermind, type system guarantees it
//  arrayExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();

  override def resultType: CoordH[KindHT] = elementType
}

// Constructs an unknown-size array, whose length is the integer from sizeExpression,
// whose values are generated from the function from generatorExpression. Puts the
// result in a new expressions.
case class StaticArrayFromCallableH(
  // Expression containing the IFunction<int, T> interface reference which we'll
  // call to generate each element of the array.
  // More specifically, we'll call the "__call" function on the interface, which
  // should be the only function on it.
  // This is a borrow reference.
  generatorExpression: ExpressionH[KindHT],
  // The prototype for the "__call" function to call on the interface for each element.
  generatorMethod: PrototypeH,

  elementType: CoordH[KindHT],
  // The resulting type of the array.
  // TODO: Remove this, it's redundant with the generatorExpression's interface's
  // only method's return type.
  resultType: CoordH[StaticSizedArrayHT]
) extends ExpressionH[StaticSizedArrayHT] {
  // See BRCOBS, no arguments should be Never.
  generatorExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  vassert(
    generatorExpression.resultType.ownership == BorrowH ||
      generatorExpression.resultType.ownership == ShareH)
}

// Destroys an array previously created with NewArrayFromValuesH.
case class DestroyStaticSizedArrayIntoFunctionH(
  // Expression containing the array we'll destroy.
  // This is an owning reference.
  arrayExpression: ExpressionH[StaticSizedArrayHT],
  // Expression containing the argument we'll pass to consumerMethod with the element.
  consumerExpression: ExpressionH[KindHT],
  // The prototype for the "__call" function to call on the interface for each element.
  consumerMethod: PrototypeH,
  arrayElementType: CoordH[KindHT],
  arraySize: Long
) extends ExpressionH[VoidHT] {
  // See BRCOBS, no arguments should be Never.
  // Nevermind, type system guarantees it
//  arrayExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
  consumerExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[VoidHT] = CoordH(ShareH, InlineH, VoidHT())
}

// Destroys an array previously created with ConstructRuntimeSizedArrayH.
case class DestroyImmRuntimeSizedArrayH(
  // Expression containing the array we'll destroy.
  // This is an owning reference.
  arrayExpression: ExpressionH[RuntimeSizedArrayHT],
  // Expression containing the argument we'll pass to consumerMethod with the element.
  consumerExpression: ExpressionH[KindHT],
  // The prototype for the "__call" function to call on the interface for each element.
  consumerMethod: PrototypeH,
  arrayElementType: CoordH[KindHT],
) extends ExpressionH[VoidHT] {
  // See BRCOBS, no arguments should be Never.
  // Nevermind, type system guarantees it
//  arrayExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }
  consumerExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[VoidHT] = CoordH(ShareH, InlineH, VoidHT())
}

// Destroys an array previously created with ConstructRuntimeSizedArrayH.
case class DestroyMutRuntimeSizedArrayH(
  // Expression containing the array we'll destroy.
  // This is an owning reference.
  arrayExpression: ExpressionH[RuntimeSizedArrayHT]
) extends ExpressionH[VoidHT] {
  // See BRCOBS, no arguments should be Never.
  // Nevermind, type system guarantees it
//  arrayExpression.resultType.kind match { case NeverH(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[VoidHT] = CoordH(ShareH, InlineH, VoidHT())
}

// Jumps to after the closest containing loop.
case class BreakH() extends ExpressionH[NeverHT] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[NeverHT] = CoordH(ShareH, InlineH, NeverHT(true))
}

// Creates a new struct instance.
case class NewStructH(
  // Expressions containing the values we'll use as members of the new struct.
  sourceExpressions: Vector[ExpressionH[KindHT]],
  // Names of the members of the struct, in order.
  targetMemberNames: Vector[IdH],
  // The type of struct we'll create.
  resultType: CoordH[StructHT]
) extends ExpressionH[StructHT] {
  // See BRCOBS, no arguments should be Never.
  sourceExpressions.foreach(expr => {
    expr.resultType.kind match { case NeverHT(_) => vwat() case _ => }
  })
}

// Gets the length of an unknown-sized array.
case class ArrayLengthH(
  // Expression containing the array whose length we'll get.
  sourceExpression: ExpressionH[KindHT],
) extends ExpressionH[IntHT] {
  // See BRCOBS, no arguments should be Never.
  sourceExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[IntHT] = CoordH(ShareH, InlineH, IntHT.i32)
}

// Gets the capacity of an unknown-sized array.
case class ArrayCapacityH(
  // Expression containing the array whose length we'll get.
  sourceExpression: ExpressionH[KindHT],
) extends ExpressionH[IntHT] {
  // See BRCOBS, no arguments should be Never.
  sourceExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[IntHT] = CoordH(ShareH, InlineH, IntHT.i32)
}

// Turns a borrow ref into a weak ref.
case class BorrowToWeakH(
  // Expression containing the borrow reference to turn into a weak ref.
  refExpression: ExpressionH[KindHT],
) extends ExpressionH[KindHT] {
  // See BRCOBS, no arguments should be Never.
  refExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  vassert(refExpression.resultType.ownership == BorrowH)

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[KindHT] = CoordH(WeakH, YonderH, refExpression.resultType.kind)
}

// Checks if the given args are the same instance.
case class IsSameInstanceH(
  leftExpression: ExpressionH[KindHT],
  rightExpression: ExpressionH[KindHT],
) extends ExpressionH[KindHT] {
  // See BRCOBS, no arguments should be Never.
  leftExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }
  rightExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  override def resultType: CoordH[KindHT] = CoordH(ShareH, InlineH, BoolHT())
}

// Tries to downcast to the specified subtype and wrap in a Some.
// If it fails, will result in a None.
case class AsSubtypeH(
  // Expression whose result we'll try to downcast
  sourceExpression: ExpressionH[KindHT],
  // The subtype to try and cast the source to.
  targetType: KindHT,
  // Should be an owned ref to optional of something
  resultType: CoordH[InterfaceHT],
  // Function to give a ref to to make a Some(ref) {
  // val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }
  someConstructor: PrototypeH,
  // Function to make a None of the right type
  noneConstructor: PrototypeH,
) extends ExpressionH[KindHT] {
  // See BRCOBS, no arguments should be Never.
  sourceExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }
}

// Locks a weak ref to turn it into an optional of borrow ref.
case class LockWeakH(
  // Expression containing the array whose length we'll get.
  sourceExpression: ExpressionH[KindHT],
  // Should be an owned ref to optional of borrow ref of something
  resultType: CoordH[InterfaceHT],
  // Function to give a borrow ref to to make a Some(borrow ref)
  someConstructor: PrototypeH,
  // Function to make a None of the right type
  noneConstructor: PrototypeH,
) extends ExpressionH[KindHT] {
  // See BRCOBS, no arguments should be Never.
  sourceExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }
}

// See DINSIE for why this isn't three instructions, and why we don't have the
// destructor prototype in it.
case class DiscardH(sourceExpression: ExpressionH[KindHT]) extends ExpressionH[VoidHT] {
  // See BRCOBS, no arguments should be Never.
  sourceExpression.resultType.kind match { case NeverHT(_) => vwat() case _ => }

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  sourceExpression.resultType.ownership match {
    case BorrowH | ShareH | WeakH =>
  }
  override def resultType: CoordH[VoidHT] = CoordH(ShareH, InlineH, VoidHT())
}

trait IExpressionH {
  def expectReferenceExpression(): ReferenceExpressionH = {
    this match {
      case r @ ReferenceExpressionH(_) => r
      case AddressExpressionH(_) => vfail("Expected a reference as a result, but got an address!")
    }
  }
  def expectAddressExpression(): AddressExpressionH = {
    this match {
      case a @ AddressExpressionH(_) => a
      case ReferenceExpressionH(_) => vfail("Expected an address as a result, but got a reference!")
    }
  }
}
case class ReferenceExpressionH(reference: CoordH[KindHT]) extends IExpressionH {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }
case class AddressExpressionH(reference: CoordH[KindHT]) extends IExpressionH {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

// Identifies a local variable.
case class Local(
  // No two variables in a FunctionH have the same id.
  id: VariableIdH,

  // Whether the local is ever changed or not.
  variability: Variability,

  // The type of the reference this local variable has.
  typeH: CoordH[KindHT],

  // Usually filled by catalyst, for Midas' benefit. Used in HGM.
  keepAlive: Boolean) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

case class VariableIdH(
  // Just to uniquify VariableIdH instances. No two variables in a FunctionH will have
  // the same number.
  number: Int,
  // Where the variable is relative to the stack frame's beginning.
  height: Int,
  // Just for debugging purposes
  name: Option[IdH]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
