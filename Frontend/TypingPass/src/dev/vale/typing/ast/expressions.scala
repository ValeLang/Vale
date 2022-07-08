package dev.vale.typing.ast

//import dev.vale.astronomer.IVarNameA
import dev.vale.typing.env.{ILocalVariableT, ReferenceLocalVariableT}
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, ExternFunctionNameT, FullNameT, IVarNameT}
import dev.vale.{RangeS, vassert, vcurious, vfail, vpass, vwat}
import dev.vale.typing.types.{BoolT, BorrowT, CoordT, FloatT, ImmutableT, IntT, InterfaceTT, KindT, MutableT, NeverT, OwnT, OwnershipT, RuntimeSizedArrayTT, ShareT, StaticSizedArrayTT, StrT, StructTT, VariabilityT, VoidT, WeakT}
import dev.vale._
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.types._
import dev.vale.typing.names.CitizenTemplateNameT

trait IExpressionResultT  {
  def expectReference(): ReferenceResultT = {
    this match {
      case r @ ReferenceResultT(_) => r
      case AddressResultT(_) => vfail("Expected a reference as a result, but got an address!")
    }
  }
  def expectAddress(): AddressResultT = {
    this match {
      case a @ AddressResultT(_) => a
      case ReferenceResultT(_) => vfail("Expected an address as a result, but got a reference!")
    }
  }
  def underlyingReference: CoordT
  def kind: KindT
}
case class AddressResultT(reference: CoordT) extends IExpressionResultT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  override def underlyingReference: CoordT = reference
  override def kind = reference.kind
}
case class ReferenceResultT(reference: CoordT) extends IExpressionResultT {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  override def underlyingReference: CoordT = reference
  override def kind = reference.kind
}
trait ExpressionT  {
  def result: IExpressionResultT
  def kind: KindT
}
trait ReferenceExpressionTE extends ExpressionT {
  override def result: ReferenceResultT
  override def kind = result.reference.kind
}
// This is an Expression2 because we sometimes take an address and throw it
// directly into a struct (closures!), which can have addressible members.
trait AddressExpressionTE extends ExpressionT {
  override def result: AddressResultT
  override def kind = result.reference.kind

  def range: RangeS

  // Whether or not we can change where this address points to
  def variability: VariabilityT
}

case class LetAndLendTE(
    variable: ILocalVariableT,
    expr: ReferenceExpressionTE,
  targetOwnership: OwnershipT
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(variable.reference == expr.result.reference)

  (expr.result.reference.ownership, targetOwnership) match {
    case (ShareT, ShareT) =>
    case (OwnT | BorrowT | WeakT, BorrowT) =>
  }

  expr match {
    case BreakTE() | ReturnTE(_) => vwat() // See BRCOBS
    case _ =>
  }

  override def result: ReferenceResultT = {
    val CoordT(oldOwnership, kind) = expr.result.reference
    ReferenceResultT(CoordT(targetOwnership, kind))
  }
}

case class LockWeakTE(
  innerExpr: ReferenceExpressionTE,
  // We could just calculate this, but it feels better to let the StructCompiler
  // make it, so we're sure it's created.
  resultOptBorrowType: CoordT,

  // Function to give a borrow ref to to make a Some(borrow ref)
  someConstructor: PrototypeT,
  // Function to make a None of the right type
  noneConstructor: PrototypeT,
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = {
    ReferenceResultT(resultOptBorrowType)
  }
}

// Turns a borrow ref into a weak ref
// Note that we can also get a weak ref from LocalLoad2'ing a
// borrow ref local into a weak ref.
case class BorrowToWeakTE(
  innerExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  vassert(innerExpr.result.reference.ownership == BorrowT)

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  innerExpr.result.reference.ownership match {
    case BorrowT =>
  }

  override def result: ReferenceResultT = {
    ReferenceResultT(CoordT(WeakT, innerExpr.kind))
  }
}

case class LetNormalTE(
    variable: ILocalVariableT,
    expr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, VoidT()))

  expr.kind match {
    case NeverT(_) => // then we can put it into whatever type we want
    case _ => {
      variable.reference.kind match {
        case NeverT(_) => vfail() // can't receive into a never
        case _ => vassert(variable.reference == expr.result.reference)
      }
    }
  }

  expr match {
    case BreakTE() | ReturnTE(_) => vwat() // See BRCOBS
    case _ =>
  }
}

// Only ExpressionCompiler.unletLocal should make these
case class UnletTE(variable: ILocalVariableT) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(variable.reference)

  vpass()
}

// Throws away a reference.
// Unless given to an instruction which consumes it, all borrow and share
// references must eventually hit a Discard2, just like all owning
// references must eventually hit a Destructure2.
// Depending on the backend, it will either be a no-op (like for GC'd backends)
// or a decrement+maybedestruct (like for RC'd backends)
// See DINSIE for why this isnt three instructions, and why we dont have the
// destructor in here for shareds.
case class DiscardTE(
  expr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, VoidT()))

  expr.result.reference.ownership match {
    case BorrowT =>
    case ShareT =>
    case WeakT =>
  }

  expr match {
    case ConsecutorTE(exprs) => {
      exprs.last match {
        case DiscardTE(_) => vwat()
        case _ =>
      }
    }
    case _ =>
  }
}

case class DeferTE(
  innerExpr: ReferenceExpressionTE,
  // Every deferred expression should discard its result, IOW, return Void.
  deferredExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  override def result = ReferenceResultT(innerExpr.result.reference)

  vassert(deferredExpr.result.reference == CoordT(ShareT, VoidT()))
}


// Eventually, when we want to do if-let, we'll have a different construct
// entirely. See comment below If2.
// These are blocks because we don't want inner locals to escape.
case class IfTE(
    condition: ReferenceExpressionTE,
    thenCall: ReferenceExpressionTE,
    elseCall: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  private val conditionResultCoord = condition.result.reference
  private val thenResultCoord = thenCall.result.reference
  private val elseResultCoord = elseCall.result.reference

  vassert(conditionResultCoord == CoordT(ShareT, BoolT()))

  (thenResultCoord.kind, thenResultCoord.kind) match {
    case (NeverT(_), _) =>
    case (_, NeverT(_)) =>
    case (a, b) if a == b =>
    case _ => vwat()
  }

  private val commonSupertype =
    thenResultCoord.kind match {
      case NeverT(_) => elseResultCoord
      case _ => thenResultCoord
    }

  override def result = ReferenceResultT(commonSupertype)
}

// The block is expected to return a boolean (false = stop, true = keep going).
// The block will probably contain an If2(the condition, the body, false)
case class WhileTE(block: BlockTE) extends ReferenceExpressionTE {
  // While loops must always produce void.
  // If we want a foreach/map/whatever construct, the loop should instead
  // add things to a list inside; WhileTE shouldnt do it for it.
  val resultCoord =
    block.kind match {
      case VoidT() => CoordT(ShareT, VoidT())
      case NeverT(true) => CoordT(ShareT, VoidT())
      case NeverT(false) => CoordT(ShareT, NeverT(false))
      case _ => vwat()
    }

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(resultCoord)
  vpass()
}

case class MutateTE(
  destinationExpr: AddressExpressionTE,
  sourceExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(destinationExpr.result.reference)
}


case class ReturnTE(
  sourceExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, NeverT(false)))

  def getFinalExpr(expression2: ExpressionT): Unit = {
    expression2 match {
      case BlockTE(expr) => getFinalExpr(expr)
    }
  }
}

case class BreakTE() extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, NeverT(true)))
}

// when we make a closure, we make a struct full of pointers to all our variables
// and the first element is our parent closure
// this can live on the stack, since blocks are limited to this expression
// later we can optimize it to only have the things we use

// Block2 is required to unlet all the variables it introduces.
case class BlockTE(
    inner: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  override def result = inner.result
}

case class ConsecutorTE(exprs: Vector[ReferenceExpressionTE]) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  // There shouldn't be a 0-element consecutor.
  // If we want a consecutor that returns nothing, put a VoidLiteralTE in it.
  vassert(exprs.nonEmpty)

  if (exprs.size > 1) {
    vassert(exprs.init.collect({ case VoidLiteralTE() => }).isEmpty)
  }

  // There shouldn't be a 1-element consecutor.
  // This isn't a hard technical requirement, but it does simplify the resulting AST a bit.
  // Call Compiler.consecutive to conform to this.
  vassert(exprs.size >= 2)

  // A consecutor should never contain another consecutor.
  // This isn't a hard technical requirement, but it does simplify the resulting AST a bit.
  // Call Compiler.consecutive to make new consecutors in a way that conforms to this.
  exprs.collect({ case ConsecutorTE(_) => vfail() })

  // Everything but the last should result in a Void or a Never.
  // The last can be anything, even a Void or a Never.
  exprs.init.foreach(expr => {
    expr.kind match {
      case VoidT() | NeverT(_) =>
      case _ => vwat()
    }
  })

  //  // If there's a Never2() anywhere, then the entire block should end in an unreachable
  //  // or panic or something.
  //  if (exprs.exists(_.kind == NeverT())) {
  //    vassert(exprs.last.kind == NeverT())
  //  }
  // Nevermind, we made it so the consecutor's result is Never if there's
  // a Never *anywhere* inside it.

  vassert(exprs.collect({
    case ReturnTE(_) =>
  }).size <= 1)

  override val result: ReferenceResultT =
    exprs.map(_.result.reference)
        .collectFirst({ case n @ CoordT(ShareT, NeverT(_)) => n }) match {
      case Some(n) => ReferenceResultT(n)
      case None => exprs.last.result
    }

  def lastReferenceExpr = exprs.last
}

case class TupleTE(
    elements: Vector[ReferenceExpressionTE],
    resultReference: CoordT) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(resultReference)
}

//// Discards a reference, whether it be owned or borrow or whatever.
//// This is used after panics or other never-returning things, to signal that a certain
//// variable should be considered gone. See AUMAP.
//// This can also be used if theres anything after a panic in a block, like
////   exported func main() int {
////     __panic();
////     println("hi");
////   }
//case class UnreachableMootTE(innerExpr: ReferenceExpressionTE) extends ReferenceExpressionTE {
//  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def result = ReferenceResultT(CoordT(ShareT, NeverT()))
//}

case class StaticArrayFromValuesTE(
    elements: Vector[ReferenceExpressionTE],
    resultReference: CoordT,
    arrayType: StaticSizedArrayTT) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(resultReference)
}

case class ArraySizeTE(array: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, IntT.i32))
}

case class IsSameInstanceTE(left: ReferenceExpressionTE, right: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(left.result.reference == right.result.reference)

  override def result = ReferenceResultT(CoordT(ShareT, BoolT()))
}

case class AsSubtypeTE(
    sourceExpr: ReferenceExpressionTE,
    targetSubtype: KindT,

    // We could just calculate this, but it feels better to let the StructCompiler
    // make it, so we're sure it's created.
    resultResultType: CoordT,
    // Function to give a borrow ref to to make a Some(borrow ref)
    okConstructor: PrototypeT,
    // Function to make a None of the right type
    errConstructor: PrototypeT,
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(resultResultType)
}

case class VoidLiteralTE() extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, VoidT()))
}

case class ConstantIntTE(value: Long, bits: Int) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, IntT(bits)))
}

case class ConstantBoolTE(value: Boolean) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, BoolT()))
}

case class ConstantStrTE(value: String) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, StrT()))
}

case class ConstantFloatTE(value: Double) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, FloatT()))
}

case class LocalLookupTE(
  range: RangeS,
  localVariable: ILocalVariableT,
//  reference: CoordT,
//  variability: VariabilityT
) extends AddressExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = AddressResultT(localVariable.reference)
  override def variability: VariabilityT = localVariable.variability
}

case class ArgLookupTE(
    paramIndex: Int,
    reference: CoordT
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(reference)
}

case class StaticSizedArrayLookupTE(
  range: RangeS,
    arrayExpr: ReferenceExpressionTE,
    arrayType: StaticSizedArrayTT,
    indexExpr: ReferenceExpressionTE,
    // See RMLRMO for why we dont have a targetOwnership field here.
    variability: VariabilityT
) extends AddressExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(arrayExpr.result.reference.kind == arrayType)

  override def result = AddressResultT(arrayType.elementType)
}

case class RuntimeSizedArrayLookupTE(
  range: RangeS,
    arrayExpr: ReferenceExpressionTE,
    arrayType: RuntimeSizedArrayTT,
    indexExpr: ReferenceExpressionTE,
  // See RMLRMO for why we dont have a targetOwnership field here.
  variability: VariabilityT
) extends AddressExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(arrayExpr.result.reference.kind == arrayType)

  override def result = {
    val CoordT(ownership, kind) = arrayType.elementType
    AddressResultT(CoordT(ownership, kind))
  }
}

case class ArrayLengthTE(arrayExpr: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, IntT.i32))
}

case class ReferenceMemberLookupTE(
    range: RangeS,
    structExpr: ReferenceExpressionTE,
    memberName: FullNameT[IVarNameT],
    memberReference: CoordT,
    // See RMLRMO for why we dont have a targetOwnership field here.
    variability: VariabilityT) extends AddressExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = {
    // See RMLRMO why we just return the member type.
    AddressResultT(memberReference)
  }
  vpass()
}
case class AddressMemberLookupTE(
    range: RangeS,
    structExpr: ReferenceExpressionTE,
    memberName: FullNameT[IVarNameT],
    resultType2: CoordT,
    variability: VariabilityT) extends AddressExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = AddressResultT(resultType2)
}

case class InterfaceFunctionCallTE(
    superFunctionHeader: FunctionHeaderT,
    resultReference: CoordT,
    args: Vector[ReferenceExpressionTE]) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = ReferenceResultT(resultReference)
}

case class ExternFunctionCallTE(
    prototype2: PrototypeT,
    args: Vector[ReferenceExpressionTE]) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  // We dont:
  //   vassert(prototype2.fullName.last.templateArgs.isEmpty)
  // because we totally can have extern templates.
  // Will one day be useful for plugins, and we already use it for
  // lock<T>, which is generated by the backend.

  prototype2.fullName.last match {
    case ExternFunctionNameT(_, _) =>
    case _ => vwat()
  }



  override def result = ReferenceResultT(prototype2.returnType)
}

case class FunctionCallTE(
    callable: PrototypeT,
    args: Vector[ReferenceExpressionTE]) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  vassert(callable.paramTypes.size == args.size)
  args.map(_.result.reference).zip(callable.paramTypes).foreach({
    case (CoordT(_, NeverT(_)), _) =>
    case (a, b) => vassert(a == b)
  })

  override def result: ReferenceResultT = {
    ReferenceResultT(callable.returnType)
  }
}

// A typingpass reinterpret is interpreting a type as a different one which is hammer-equivalent.
// For example, a pack and a struct are the same thing to hammer.
// Also, a closure and a struct are the same thing to hammer.
// But, Compiler attaches different meanings to these things. The typingpass is free to reinterpret
// between hammer-equivalent things as it wants.
case class ReinterpretTE(
    expr: ReferenceExpressionTE,
    resultReference: CoordT) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(expr.result.reference != resultReference)

  override def result = ReferenceResultT(resultReference)

  expr.result.reference.kind match {
    // Unless it's a Never...
    case NeverT(_) =>
    case _ => {
      if (resultReference.ownership != expr.result.reference.ownership) {
        // Cant reinterpret to a different ownership!
        vfail("wat");
      }
    }
  }
}

case class ConstructTE(
    structTT: StructTT,
    resultReference: CoordT,
    args: Vector[ExpressionT]) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()

  override def result = ReferenceResultT(resultReference)
}

// Note: the functionpointercall's last argument is a Placeholder2,
// it's up to later stages to replace that with an actual index
case class NewMutRuntimeSizedArrayTE(
  arrayType: RuntimeSizedArrayTT,
  capacityExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        if (arrayType.mutability == MutableT) OwnT else ShareT,
        arrayType))
  }
}

case class StaticArrayFromCallableTE(
  arrayType: StaticSizedArrayTT,
  generator: ReferenceExpressionTE,
  generatorMethod: PrototypeT
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        if (arrayType.mutability == MutableT) OwnT else ShareT,
        arrayType))
  }
}

// Note: the functionpointercall's last argument is a Placeholder2,
// it's up to later stages to replace that with an actual index
// This returns nothing, as opposed to DrainStaticSizedArray2 which returns a
// sequence of results from the call.
case class DestroyStaticSizedArrayIntoFunctionTE(
    arrayExpr: ReferenceExpressionTE,
    arrayType: StaticSizedArrayTT,
    consumer: ReferenceExpressionTE,
    consumerMethod: PrototypeT) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(consumerMethod.paramTypes.size == 2)
  vassert(consumerMethod.paramTypes(0) == consumer.result.reference)
  vassert(consumerMethod.paramTypes(1) == arrayType.elementType)

  // See https://github.com/ValeLang/Vale/issues/375
  consumerMethod.returnType.kind match {
    case StructTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT(name), _))) => {
      vassert(name.str == "Tup")
    }
    case VoidT() =>
    case _ => vwat()
  }

  override def result: ReferenceResultT = ReferenceResultT(CoordT(ShareT, VoidT()))
}

// We destroy both Share and Own things
// If the struct contains any addressibles, those die immediately and aren't stored
// in the destination variables, which is why it's a list of ReferenceLocalVariable2.
case class DestroyStaticSizedArrayIntoLocalsTE(
  expr: ReferenceExpressionTE,
  staticSizedArray: StaticSizedArrayTT,
  destinationReferenceVariables: Vector[ReferenceLocalVariableT]
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = ReferenceResultT(CoordT(ShareT, VoidT()))

  vassert(expr.kind == staticSizedArray)
  if (expr.result.reference.ownership == BorrowT) {
    vfail("wot")
  }
}

case class DestroyMutRuntimeSizedArrayTE(
  arrayExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def result: ReferenceResultT = ReferenceResultT(CoordT(ShareT, VoidT()))
}

case class RuntimeSizedArrayCapacityTE(
  arrayExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def result: ReferenceResultT = ReferenceResultT(CoordT(ShareT, IntT(32)))
}

case class PushRuntimeSizedArrayTE(
  arrayExpr: ReferenceExpressionTE,
//  arrayType: RuntimeSizedArrayTT,
  newElementExpr: ReferenceExpressionTE,
//  newElementType: CoordT,
) extends ReferenceExpressionTE {
  override def result: ReferenceResultT = ReferenceResultT(CoordT(ShareT, VoidT()))
}

case class PopRuntimeSizedArrayTE(
  arrayExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  private val elementType =
    arrayExpr.result.reference.kind match {
      case RuntimeSizedArrayTT(_, e) => e
      case other => vwat(other)
    }
  override def result: ReferenceResultT = ReferenceResultT(elementType)
}

case class InterfaceToInterfaceUpcastTE(
    innerExpr: ReferenceExpressionTE,
    targetInterfaceRef: InterfaceTT) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  def result: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        innerExpr.result.reference.ownership,
        targetInterfaceRef))
  }
}

case class StructToInterfaceUpcastTE(innerExpr: ReferenceExpressionTE, targetInterfaceRef: InterfaceTT) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  def result: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        innerExpr.result.reference.ownership,
        targetInterfaceRef))
  }
}

// A soft load is one that turns an int&& into an int*. a hard load turns an int* into an int.
// Turns an Addressible(Pointer) into an OwningPointer. Makes the source owning pointer into null

// If the source was an own and target is borrow, that's a point

case class SoftLoadTE(
    expr: AddressExpressionTE,
    targetOwnership: OwnershipT
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  vassert((targetOwnership == ShareT) == (expr.result.reference.ownership == ShareT))
  vassert(targetOwnership != OwnT) // need to unstackify or destroy to get an owning reference
  // This is just here to try the asserts inside Coord's constructor
  CoordT(targetOwnership, expr.result.reference.kind)

  override def result: ReferenceResultT = {
    ReferenceResultT(CoordT(targetOwnership, expr.result.reference.kind))
  }
}

// Destroy an object.
// If the struct contains any addressibles, those die immediately and aren't stored
// in the destination variables, which is why it's a list of ReferenceLocalVariable2.
//
// We also destroy shared things with this, see DDSOT.
case class DestroyTE(
    expr: ReferenceExpressionTE,
    structTT: StructTT,
    destinationReferenceVariables: Vector[ReferenceLocalVariableT]
) extends ReferenceExpressionTE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = ReferenceResultT(CoordT(ShareT, VoidT()))

  if (expr.result.reference.ownership == BorrowT) {
    vfail("wot")
  }
}

case class DestroyImmRuntimeSizedArrayTE(
  arrayExpr: ReferenceExpressionTE,
  arrayType: RuntimeSizedArrayTT,
  consumer: ReferenceExpressionTE,
  consumerMethod: PrototypeT
) extends ReferenceExpressionTE {
  vassert(arrayType.mutability == ImmutableT)

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(consumerMethod.paramTypes.size == 2)
  vassert(consumerMethod.paramTypes(0) == consumer.result.reference)
  //  vassert(consumerMethod.paramTypes(1) == Program2.intType)
  vassert(consumerMethod.paramTypes(1) == arrayType.elementType)

  // See https://github.com/ValeLang/Vale/issues/375
  consumerMethod.returnType.kind match {
    case VoidT() =>
  }

  override def result: ReferenceResultT = ReferenceResultT(CoordT(ShareT, VoidT()))
}

// Note: the functionpointercall's last argument is a Placeholder2,
// it's up to later stages to replace that with an actual index
case class NewImmRuntimeSizedArrayTE(
  arrayType: RuntimeSizedArrayTT,
  sizeExpr: ReferenceExpressionTE,
  generator: ReferenceExpressionTE,
  generatorMethod: PrototypeT
) extends ReferenceExpressionTE {
  vassert(arrayType.mutability == ImmutableT)

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        if (arrayType.mutability == MutableT) OwnT else ShareT,
        arrayType))
  }
}

object referenceExprResultStructName {
  def unapply(expr: ReferenceExpressionTE): Option[StrI] = {
    expr.result.reference.kind match {
      case StructTT(FullNameT(_, _, CitizenNameT(CitizenTemplateNameT(name), _))) => Some(name)
      case _ => None
    }
  }
}

object referenceExprResultKind {
  def unapply(expr: ReferenceExpressionTE): Option[KindT] = {
    Some(expr.result.reference.kind)
  }
}
