package net.verdagon.vale.templar.ast

//import net.verdagon.vale.astronomer.IVarNameA
import net.verdagon.vale._
import net.verdagon.vale.templar.env.{ILocalVariableT, ReferenceLocalVariableT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, ExternFunctionNameT, FullNameT, IVarNameT}

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
  override def hashCode(): Int = vcurious()

  override def underlyingReference: CoordT = reference
  override def kind = reference.kind

}
case class ReferenceResultT(reference: CoordT) extends IExpressionResultT {
  override def hashCode(): Int = vcurious()

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
    expr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(variable.reference == expr.result.reference)

  override def result: ReferenceResultT = {
    val CoordT(ownership, permission, kind) = expr.result.reference
    ReferenceResultT(CoordT(if (ownership == ShareT) ShareT else ConstraintT, permission, kind))
  }


}

case class NarrowPermissionTE(
    expr: ReferenceExpressionTE,
    targetPermission: PermissionT
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  expr.result.reference.ownership match {
    case OwnT => vfail() // This only works on non owning references
    case ShareT => vfail() // Share only has readonly
    case ConstraintT | WeakT => // fine
  }
  // Only thing we support so far is Readwrite -> Readonly
  vassert(expr.result.reference.permission == ReadwriteT)
  vassert(targetPermission == ReadonlyT)

  override def result: ReferenceResultT = {
    val CoordT(ownership, permission, kind) = expr.result.reference
    ReferenceResultT(CoordT(ownership, targetPermission, kind))
  }


}

case class LockWeakTE(
  innerExpr: ReferenceExpressionTE,
  // We could just calculate this, but it feels better to let the StructTemplar
  // make it, so we're sure it's created.
  resultOptBorrowType: CoordT,

  // Function to give a borrow ref to to make a Some(borrow ref)
  someConstructor: PrototypeT,
  // Function to make a None of the right type
  noneConstructor: PrototypeT,
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = {
    ReferenceResultT(resultOptBorrowType)
  }


}

// Turns a constraint ref into a weak ref
// Note that we can also get a weak ref from LocalLoad2'ing a
// constraint ref local into a weak ref.
case class WeakAliasTE(
  innerExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(innerExpr.result.reference.ownership == ConstraintT)

  override def result: ReferenceResultT = {
    ReferenceResultT(CoordT(WeakT, innerExpr.result.reference.permission, innerExpr.kind))
  }


}

case class LetNormalTE(
    variable: ILocalVariableT,
    expr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  expr match {
    case ReturnTE(_) => vwat()
    case _ =>
  }


}

// Only ExpressionTemplar.unletLocal should make these
case class UnletTE(variable: ILocalVariableT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
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
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  expr.result.reference.ownership match {
    case ConstraintT =>
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
  override def hashCode(): Int = vcurious()

  override def result = ReferenceResultT(innerExpr.result.reference)

  vassert(deferredExpr.result.reference == CoordT(ShareT, ReadonlyT, VoidT()))


}


// Eventually, when we want to do if-let, we'll have a different construct
// entirely. See comment below If2.
// These are blocks because we don't want inner locals to escape.
case class IfTE(
    condition: ReferenceExpressionTE,
    thenCall: ReferenceExpressionTE,
    elseCall: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  private val conditionResultCoord = condition.result.reference
  private val thenResultCoord = thenCall.result.reference
  private val elseResultCoord = elseCall.result.reference

  vassert(conditionResultCoord == CoordT(ShareT, ReadonlyT, BoolT()))
  vassert(
    thenResultCoord.kind == NeverT() ||
      elseResultCoord.kind == NeverT() ||
      thenResultCoord == elseResultCoord)

  private val commonSupertype =
    if (thenResultCoord.kind == NeverT()) {
      elseResultCoord
    } else {
      thenResultCoord
    }

  override def result = ReferenceResultT(commonSupertype)


}

// case class IfLet2
// This would check whether:
// - The nullable condition expression evaluates to not null, or
// - The interface condition expression evaluates to the specified sub-citizen
// It would have to use a new chunk of PatternTemplar which produces an
// expression which is a ton of if-statements and try-cast things and assigns
// variables, and puts the given body inside all that.


// The block is expected to return a boolean (false = stop, true = keep going).
// The block will probably contain an If2(the condition, the body, false)
case class WhileTE(block: BlockTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))


}

case class MutateTE(
  destinationExpr: AddressExpressionTE,
  sourceExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(destinationExpr.result.reference)


}


case class ReturnTE(
  sourceExpr: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, NeverT()))

  def getFinalExpr(expression2: ExpressionT): Unit = {
    expression2 match {
      case BlockTE(expr) => getFinalExpr(expr)
    }
  }


}


//case class CurriedFuncH(closureExpr: ExpressionH, funcName: String) extends ExpressionH

// when we make a closure, we make a struct full of pointers to all our variables
// and the first element is our parent closure
// this can live on the stack, since blocks are limited to this expression
// later we can optimize it to only have the things we use

// Block2 is required to unlet all the variables it introduces.
case class BlockTE(
    inner: ReferenceExpressionTE
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()

  override def result = inner.result


}

case class ConsecutorTE(exprs: Vector[ReferenceExpressionTE]) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  // There shouldn't be a 0-element consecutor.
  // If we want a consecutor that returns nothing, put a VoidLiteralTE in it.
  vassert(exprs.nonEmpty)

  // There shouldn't be a 1-element consecutor.
  // This isn't a hard technical requirement, but it does simplify the resulting AST a bit.
  // Call Templar.consecutive to conform to this.
  vassert(exprs.size >= 2)

  // A consecutor should never contain another consecutor.
  // This isn't a hard technical requirement, but it does simplify the resulting AST a bit.
  // Call Templar.consecutive to make new consecutors in a way that conforms to this.
  exprs.collect({ case ConsecutorTE(_) => vfail() })

  // Everything but the last should result in a Void or a Never.
  // The last can be anything, even a Void or a Never.
  exprs.init.foreach(expr => vassert(expr.kind == VoidT() || expr.kind == NeverT()))

  // If there's a Never2() anywhere, then the entire block should end in an unreachable
  // or panic or something.
  if (exprs.exists(_.kind == NeverT())) {
    vassert(exprs.last.kind == NeverT())
  }

  vassert(exprs.collect({
    case ReturnTE(_) =>
  }).size <= 1)

  def lastReferenceExpr = exprs.last
  override def result = lastReferenceExpr.result


}

//case class PackTE(
//    elements: Vector[ReferenceExpressionTE],
//    resultReference: CoordT,
//    packType: PackTT) extends ReferenceExpressionTE {
//  override def hashCode(): Int = vcurious()
//  override def resultRegister = ReferenceResultT(resultReference)
//
//}

case class TupleTE(
    elements: Vector[ReferenceExpressionTE],
    resultReference: CoordT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(resultReference)

}

// Discards a reference, whether it be owned or borrow or whatever.
// This is used after panics or other never-returning things, to signal that a certain
// variable should be considered gone. See AUMAP.
// This can also be used if theres anything after a panic in a block, like
//   fn main() int export {
//     __panic();
//     println("hi");
//   }
case class UnreachableMootTE(innerExpr: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, NeverT()))

}

case class StaticArrayFromValuesTE(
    elements: Vector[ReferenceExpressionTE],
    resultReference: CoordT,
    arrayType: StaticSizedArrayTT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(resultReference)

}

case class ArraySizeTE(array: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, IntT.i32))

}

case class IsSameInstanceTE(left: ReferenceExpressionTE, right: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(left.result.reference == right.result.reference)

  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, BoolT()))

}

case class AsSubtypeTE(
    sourceExpr: ReferenceExpressionTE,
    targetSubtype: KindT,

    // We could just calculate this, but it feels better to let the StructTemplar
    // make it, so we're sure it's created.
    resultResultType: CoordT,
    // Function to give a borrow ref to to make a Some(borrow ref)
    okConstructor: PrototypeT,
    // Function to make a None of the right type
    errConstructor: PrototypeT,
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(resultResultType)

}

case class VoidLiteralTE() extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))


}

case class ConstantIntTE(value: Long, bits: Int) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, IntT(bits)))


}

case class ConstantBoolTE(value: Boolean) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, BoolT()))


}

case class ConstantStrTE(value: String) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, StrT()))


}

case class ConstantFloatTE(value: Double) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, FloatT()))


}

case class LocalLookupTE(
  range: RangeS,
  localVariable: ILocalVariableT,
  reference: CoordT,
  variability: VariabilityT
) extends AddressExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = AddressResultT(reference)


}

case class ArgLookupTE(
    paramIndex: Int,
    reference: CoordT
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(reference)


}

//case class PackLookup2(packExpr: Expression2, index: Int) extends Expression2 {
//  override def resultType: BaseType2 = {
//    // A pack can never be in a changeable variable, and so can't be an addressible, so will always
//    // be a pointer.
//    // (it can be in a final variable, when its spawned by pattern matching)
//    TypeUtils.softDecay(packExpr.resultType).innerType match {
//      case PackT2(memberTypes, underlyingStructRef) => memberTypes(index)
//    }
//  }
//
//  def all[T](func: PartialFunction[Ast2, T]): Vector[T] = {
//    Vector(this).collect(func) ++ packExpr.all(func)
//  }
//}

case class StaticSizedArrayLookupTE(
  range: RangeS,
    arrayExpr: ReferenceExpressionTE,
    arrayType: StaticSizedArrayTT,
    indexExpr: ReferenceExpressionTE,
    // See RMLRMO for why we dont have a targetOwnership field here.
    // See RMLHTP why we can have this here.
    targetPermission: PermissionT,
    variability: VariabilityT
) extends AddressExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(arrayExpr.result.reference.kind == arrayType)

  override def result = AddressResultT(arrayType.array.elementType)


}

case class RuntimeSizedArrayLookupTE(
  range: RangeS,
    arrayExpr: ReferenceExpressionTE,
    arrayType: RuntimeSizedArrayTT,
    indexExpr: ReferenceExpressionTE,
  // See RMLRMO for why we dont have a targetOwnership field here.
  // See RMLHTP why we can have this here.
  targetPermission: PermissionT,
  variability: VariabilityT
) extends AddressExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(arrayExpr.result.reference.kind == arrayType)

  override def result = AddressResultT(arrayType.array.elementType)


}

case class ArrayLengthTE(arrayExpr: ReferenceExpressionTE) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = ReferenceResultT(CoordT(ShareT, ReadonlyT, IntT.i32))

}

case class ReferenceMemberLookupTE(
    range: RangeS,
    structExpr: ReferenceExpressionTE,
    memberName: FullNameT[IVarNameT],
    memberReference: CoordT,
    // See RMLRMO for why we dont have a targetOwnership field here.
    // See RMLHTP why we can have this here.
    targetPermission: PermissionT,
    variability: VariabilityT) extends AddressExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = {
    if (structExpr.result.reference.permission == ReadonlyT) {
      vassert(targetPermission == ReadonlyT)
    }
    if (targetPermission == ReadwriteT) {
      vassert(structExpr.result.reference.permission == ReadwriteT)
    }
    // See RMLRMO why we just return the member type.
    AddressResultT(memberReference.copy(permission = targetPermission))
  }


}
case class AddressMemberLookupTE(
    range: RangeS,
    structExpr: ReferenceExpressionTE,
    memberName: FullNameT[IVarNameT],
    resultType2: CoordT,
    variability: VariabilityT) extends AddressExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result = AddressResultT(resultType2)


}

//
//case class FunctionLookup2(prototype: Prototype2) extends ReferenceExpression2 {
//  override def resultRegister: ReferenceRegister2 =
//    ReferenceRegister2(Coord(Raw, prototype.functionType))
//
//  def all[T](func: PartialFunction[Queriable2, T]): Vector[T] = {
//    Vector(this).collect(func) ++ prototype.all(func)
//  }
//}

case class InterfaceFunctionCallTE(
    superFunctionHeader: FunctionHeaderT,
    resultReference: CoordT,
    args: Vector[ReferenceExpressionTE]) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT =
    ReferenceResultT(resultReference)


}

case class ExternFunctionCallTE(
    prototype2: PrototypeT,
    args: Vector[ReferenceExpressionTE]) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
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
  override def hashCode(): Int = vcurious()

  vassert(callable.paramTypes.size == args.size)
  vassert(callable.paramTypes == args.map(_.result.reference))

  override def result: ReferenceResultT = {
    ReferenceResultT(callable.returnType)
  }


}
//case class TupleTE(
//    elements: Vector[ReferenceExpressionTE],
//    tupleReference: CoordT) extends ReferenceExpressionTE {
//  override def resultRegister = ReferenceResultT(tupleReference)
//
//
//}

// A templar reinterpret is interpreting a type as a different one which is hammer-equivalent.
// For example, a pack and a struct are the same thing to hammer.
// Also, a closure and a struct are the same thing to hammer.
// But, Templar attaches different meanings to these things. The templar is free to reinterpret
// between hammer-equivalent things as it wants.
case class TemplarReinterpretTE(
    expr: ReferenceExpressionTE,
    resultReference: CoordT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(expr.result.reference != resultReference)

  override def result = ReferenceResultT(resultReference)

  // Unless it's a Never...
  if (expr.result.reference.kind != NeverT()) {
    if (resultReference.ownership != expr.result.reference.ownership) {
      // Cant reinterpret to a different ownership!
      vfail("wat");
    }
  }


}

case class ConstructTE(
    structTT: StructTT,
    resultReference: CoordT,
    args: Vector[ExpressionT]) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vpass()

  override def result = ReferenceResultT(resultReference)


}

// Note: the functionpointercall's last argument is a Placeholder2,
// it's up to later stages to replace that with an actual index
case class ConstructArrayTE(
    arrayType: RuntimeSizedArrayTT,
    sizeExpr: ReferenceExpressionTE,
    generator: ReferenceExpressionTE,
    generatorMethod: PrototypeT
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        if (arrayType.array.mutability == MutableT) OwnT else ShareT,
        if (arrayType.array.mutability == MutableT) ReadwriteT else ReadonlyT,
        arrayType))
  }


}

case class StaticArrayFromCallableTE(
  arrayType: StaticSizedArrayTT,
  generator: ReferenceExpressionTE,
  generatorMethod: PrototypeT
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        if (arrayType.array.mutability == MutableT) OwnT else ShareT,
        if (arrayType.array.mutability == MutableT) ReadwriteT else ReadonlyT,
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
  override def hashCode(): Int = vcurious()
  vassert(consumerMethod.paramTypes.size == 2)
  vassert(consumerMethod.paramTypes(0) == consumer.result.reference)
  vassert(consumerMethod.paramTypes(1) == arrayType.array.elementType)

  override def result: ReferenceResultT = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))


}

// We destroy both Share and Own things
// If the struct contains any addressibles, those die immediately and aren't stored
// in the destination variables, which is why it's a list of ReferenceLocalVariable2.
case class DestroyStaticSizedArrayIntoLocalsTE(
  expr: ReferenceExpressionTE,
  staticSizedArray: StaticSizedArrayTT,
  destinationReferenceVariables: Vector[ReferenceLocalVariableT]
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  vassert(expr.kind == staticSizedArray)
  if (expr.result.reference.ownership == ConstraintT) {
    vfail("wot")
  }


}

case class DestroyRuntimeSizedArrayTE(
    arrayExpr: ReferenceExpressionTE,
    arrayType: RuntimeSizedArrayTT,
    consumer: ReferenceExpressionTE,
    consumerMethod: PrototypeT
) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  vassert(consumerMethod.paramTypes.size == 2)
  vassert(consumerMethod.paramTypes(0) == consumer.result.reference)
//  vassert(consumerMethod.paramTypes(1) == Program2.intType)
  vassert(consumerMethod.paramTypes(1) == arrayType.array.elementType)

  override def result: ReferenceResultT = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))


}

case class InterfaceToInterfaceUpcastTE(
    innerExpr: ReferenceExpressionTE,
    targetInterfaceRef: InterfaceTT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  def result: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        innerExpr.result.reference.ownership,
        innerExpr.result.reference.permission,
        targetInterfaceRef))
  }


}

case class StructToInterfaceUpcastTE(innerExpr: ReferenceExpressionTE, targetInterfaceRef: InterfaceTT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()
  def result: ReferenceResultT = {
    ReferenceResultT(
      CoordT(
        innerExpr.result.reference.ownership,
        innerExpr.result.reference.permission,
        targetInterfaceRef))
  }


}

// A soft load is one that turns an int** into an int*. a hard load turns an int* into an int.
// Turns an Addressible(Pointer) into an OwningPointer. Makes the source owning pointer into null

// If the source was an own and target is borrow, that's a lend

case class SoftLoadTE(
    expr: AddressExpressionTE, targetOwnership: OwnershipT, targetPermission: PermissionT) extends ReferenceExpressionTE {
  override def hashCode(): Int = vcurious()

  vassert((targetOwnership == ShareT) == (expr.result.reference.ownership == ShareT))
  vassert(targetOwnership != OwnT) // need to unstackify or destroy to get an owning reference
  // This is just here to try the asserts inside Coord's constructor
  CoordT(targetOwnership, targetPermission, expr.result.reference.kind)

  (expr.result.reference.permission, targetPermission) match {
    case (ReadonlyT, ReadonlyT) =>
    case (ReadwriteT, ReadonlyT) =>
    case (ReadwriteT, ReadwriteT) =>
    case (ReadonlyT, ReadwriteT) =>
    case _ => vwat()
  }

  override def result: ReferenceResultT = {
    ReferenceResultT(CoordT(targetOwnership, targetPermission, expr.result.reference.kind))
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
  override def hashCode(): Int = vcurious()
  override def result: ReferenceResultT = ReferenceResultT(CoordT(ShareT, ReadonlyT, VoidT()))

  if (expr.result.reference.ownership == ConstraintT) {
    vfail("wot")
  }


}

//// If source was an own and target is borrow, that's a lend
//// (thats the main purpose of this)
//case class Alias2(expr: ReferenceExpression2, targetOwnership: Ownership) extends ReferenceExpression2 {
//  override def resultRegister: ReferenceRegister2 = {
//    expr.resultRegister.reference match {
//      case Coord(_, innerType) => ReferenceRegister2(Coord(targetOwnership, innerType))
//    }
//  }
//
//  def all[T](func: PartialFunction[Queriable2, T]): Vector[T] = {
//    Vector(this).collect(func) ++ expr.all(func)
//  }
//}

object referenceExprResultStructName {
  def unapply(expr: ReferenceExpressionTE): Option[String] = {
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