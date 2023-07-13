package dev.vale.instantiating.ast

import dev.vale._
import dev.vale.postparsing._

trait ExpressionI  {
  def result: CoordI[cI]
}
trait ReferenceExpressionIE extends ExpressionI { }
// This is an Expression2 because we sometimes take an address and throw it
// directly into a struct (closures!), which can have addressible members.
trait AddressExpressionIE extends ExpressionI {
//  def range: RangeS

//  // Whether or not we can change where this address points to
//  def variability: VariabilityI
}

case class LetAndLendIE(
  variable: ILocalVariableI,
  expr: ReferenceExpressionIE,
  targetOwnership: OwnershipI,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(variable.collapsedCoord == expr.result)

  (expr.result.ownership, targetOwnership) match {
    case (MutableShareI, MutableShareI) =>
    case (ImmutableShareI, ImmutableShareI) =>
    case (OwnI | MutableBorrowI | WeakI | MutableShareI, MutableBorrowI) =>
    case (ImmutableBorrowI, ImmutableBorrowI) =>
  }

  expr match {
    case BreakIE() | ReturnIE(_) => vwat() // See BRCOBS
    case _ =>
  }
}

case class LockWeakIE(
  innerExpr: ReferenceExpressionIE,
  // We could just calculaIE this, but it feels better to let the StructCompiler
  // make it, so we're sure it's created.
  resultOptBorrowType: CoordI[cI],

  // Function to give a borrow ref to to make a Some(borrow ref)
  someConstructor: PrototypeI[cI],
  // Function to make a None of the right type
  noneConstructor: PrototypeI[cI],

  // This is the impl we use to allow/permit the upcast from the some to the none.
  // It'll be useful for monomorphization and later on for locating the itable ptr to put in fat pointers.
  someImplName: IdI[cI, IImplNameI[cI]],
  // This is the impl we use to allow/permit the upcast from the some to the none.
  // It'll be useful for monomorphization and later on for locating the itable ptr to put in fat pointers.
  noneImplName: IdI[cI, IImplNameI[cI]],

  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe: CoordI[cI] = resultOptBorrowType
}

// Turns a borrow ref into a weak ref
// NoIE that we can also get a weak ref from LocalLoad2'ing a
// borrow ref local into a weak ref.
case class BorrowToWeakIE(
  innerExpr: ReferenceExpressionIE,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  vassert(
    innerExpr.result.ownership == ImmutableBorrowI ||
      innerExpr.result.ownership == MutableBorrowI)

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  innerExpr.result.ownership match {
    case MutableBorrowI | ImmutableBorrowI =>
  }
  vassert(result.ownership == vregionmut(WeakI))

//  override def resultRemoveMe: CoordI[cI] = {
//    vimpl()//ReferenceResultI(CoordI[cI](WeakI, innerExpr.kind))
//  }
}

case class LetNormalIE(
  variable: ILocalVariableI,
  expr: ReferenceExpressionIE,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  expr.result.kind match {
    case NeverIT(_) => // then we can put it into whatever type we want
    case _ => {
      variable.collapsedCoord.kind match {
        case NeverIT(_) => vfail() // can't receive into a never
        case _ => vassert(variable.collapsedCoord == expr.result)
      }
    }
  }

  expr match {
    case BreakIE() | ReturnIE(_) => vwat() // See BRCOBS
    case _ =>
  }
}

case class RestackifyIE(
  variable: ILocalVariableI,
  expr: ReferenceExpressionIE,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  expr.result.kind match {
    case NeverIT(_) => // then we can put it into whatever type we want
    case _ => {
      variable.collapsedCoord.kind match {
        case NeverIT(_) => vfail() // can't receive into a never
        case _ => vassert(variable.collapsedCoord == expr.result)
      }
    }
  }

  expr match {
    case BreakIE() | ReturnIE(_) => vwat() // See BRCOBS
    case _ =>
  }
}

// Only ExpressionCompiler.unletLocal should make these
case class UnletIE(
  variable: ILocalVariableI,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe = variable.collapsedCoord

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
case class DiscardIE(
  expr: ReferenceExpressionIE
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: CoordI[cI] = CoordI[cI](MutableShareI, VoidIT())

  expr.result.ownership match {
    case MutableBorrowI =>
    case ImmutableBorrowI =>
    case MutableShareI | ImmutableShareI =>
    case WeakI =>
  }

  expr match {
    case ConsecutorIE(exprs, _) => {
      exprs.last match {
        case DiscardIE(_) => vwat()
        case _ =>
      }
    }
    case _ =>
  }
}

case class DeferIE(
  innerExpr: ReferenceExpressionIE,
  // Every deferred expression should discard its result, IOW, return Void.
  deferredExpr: ReferenceExpressionIE,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

//  override def resultRemoveMe = ReferenceResultI(innerExpr.result)

  vassert(deferredExpr.result == CoordI[cI](MutableShareI, VoidIT()))
}


// Eventually, when we want to do if-let, we'll have a different construct
// entirely. See comment below If2.
// These are blocks because we don't want inner locals to escape.
case class IfIE(
  condition: ReferenceExpressionIE,
  thenCall: ReferenceExpressionIE,
  elseCall: ReferenceExpressionIE,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  private val conditionResultCoord = condition.result
  private val thenResultCoord = thenCall.result
  private val elseResultCoord = elseCall.result

  conditionResultCoord match {
    case CoordI(MutableShareI | ImmutableShareI, BoolIT()) =>
    case other => vfail(other)
  }

  (thenResultCoord.kind, thenResultCoord.kind) match {
    case (NeverIT(_), _) =>
    case (_, NeverIT(_)) =>
    case (a, b) if a == b =>
    case _ => vwat()
  }

  private val commonSupertype =
    thenResultCoord.kind match {
      case NeverIT(_) => elseResultCoord
      case _ => thenResultCoord
    }

//  override def resultRemoveMe = ReferenceResultI(commonSupertype)
}

// The block is expected to return a boolean (false = stop, true = keep going).
// The block will probably contain an If2(the condition, the body, false)
case class WhileIE(
  block: BlockIE,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe = ReferenceResultI(resultCoord)
  vpass()
}

case class MutateIE(
  destinationExpr: AddressExpressionIE,
  sourceExpr: ReferenceExpressionIE,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe = ReferenceResultI(destinationExpr.result)
}


case class ReturnIE(
  sourceExpr: ReferenceExpressionIE
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  override def result: CoordI[cI] = CoordI[cI](MutableShareI, NeverIT(false))
}

case class BreakIE() extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: CoordI[cI] = CoordI[cI](MutableShareI, NeverIT(true))
}

// when we make a closure, we make a struct full of pointers to all our variables
// and the first element is our parent closure
// this can live on the stack, since blocks are additive to this expression
// later we can optimize it to only have the things we use

// Block2 is required to unlet all the variables it introduces.
case class BlockIE(
  inner: ReferenceExpressionIE,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  vpass()

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe = inner.result
}

// A pure block will:
// 1. Create a new region (someday possibly with an allocator)
// 2. Freeze the existing region
// 3. Run the inner code
// 4. Un-freeze the existing region
// 5. Merge (transmigrate) any results from the new region into the existing region
// 6. Destroy the new region
case class MutabilifyIE(
  inner: ReferenceExpressionIE,
  result: CoordI[cI] // See HCCSCS
) extends ReferenceExpressionIE {
  vpass()
  vassert(inner.result.kind == result.kind)

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

// See NPFCASTN
case class ImmutabilifyIE(
    inner: ReferenceExpressionIE,
    result: CoordI[cI]
) extends ReferenceExpressionIE {
  vpass()
  vassert(inner.result.kind == result.kind)
  vassert(inner.result.ownership == MutableBorrowI || inner.result.ownership == MutableShareI)
  vassert(result.ownership == ImmutableBorrowI || result.ownership == ImmutableShareI)
  inner match {
    case SoftLoadIE(_, _, _) => {
      // The SoftLoadIE should be immutabilifying on its own.
      // We should have code that looks for this and simplifies it away.
      vwat()
    }
    case _ =>
  }

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

case class PreCheckBorrowIE(
  inner: ReferenceExpressionIE
) extends ReferenceExpressionIE {
  vpass()
  vassert(inner.result.ownership == MutableBorrowI)

  override def result: CoordI[cI] = inner.result
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

case class ConsecutorIE(
  exprs: Vector[ReferenceExpressionIE],
  result: CoordI[cI]
  ) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  // There shouldn't be a 0-element consecutor.
  // If we want a consecutor that returns nothing, put a VoidLiteralIE in it.
  vassert(exprs.nonEmpty)
}

case class TupleIE(
  elements: Vector[ReferenceExpressionIE],
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe = ReferenceResultI(resultReference)
}

//// Discards a reference, whether it be owned or borrow or whatever.
//// This is used after panics or other never-returning things, to signal that a certain
//// variable should be considered gone. See AUMAP.
//// This can also be used if theres anything after a panic in a block, like
////   exported func main() int {
////     __panic();
////     println("hi");
////   }
//case class UnreachableMootIE(innerExpr: ReferenceExpressionIE) extends ReferenceExpressionIE {
//  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe = ReferenceResulIT(CoordI[cI](MutableShareI, NeverI()))
//}

case class StaticArrayFromValuesIE(
  elements: Vector[ReferenceExpressionIE],
  resultReference: CoordI[cI],
  arrayType: StaticSizedArrayIT[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  override def result: CoordI[cI] = resultReference
}

case class ArraySizeIE(
  array: ReferenceExpressionIE,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe = ReferenceResultI(CoordI[cI](MutableShareI, IntIT.i32))
}

// Can we do an === of objects in two regions? It could be pretty useful.
case class IsSameInstanceIE(
  left: ReferenceExpressionIE,
  right: ReferenceExpressionIE
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(left.result == right.result)

  override def result: CoordI[cI] = CoordI[cI](MutableShareI, BoolIT())
}

case class AsSubtypeIE(
  sourceExpr: ReferenceExpressionIE,
  targetType: CoordI[cI],

  // We could just calculaIE this, but it feels better to let the StructCompiler
  // make it, so we're sure it's created.
  resultResultType: CoordI[cI],
  // Function to give a borrow ref to to make a Some(borrow ref)
  okConstructor: PrototypeI[cI],
  // Function to make a None of the right type
  errConstructor: PrototypeI[cI],

  // This is the impl we use to allow/permit the downcast. It'll be useful for monomorphization.
  implName: IdI[cI, IImplNameI[cI]],

  // These are the impls that we conceptually use to upcast the created Ok/Err to Result.
  // Really they're here so the instantiator can know what impls it needs to instantiaIE.
  okImplName: IdI[cI, IImplNameI[cI]],
  errImplName: IdI[cI, IImplNameI[cI]],

  result: CoordI[cI]
) extends ReferenceExpressionIE {
  vpass()

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe = ReferenceResultI(resultResultType)
}

case class VoidLiteralIE() extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: CoordI[cI] = CoordI[cI](MutableShareI, VoidIT())
}

case class ConstantIntIE(value: Long, bits: Int) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = CoordI[cI](MutableShareI, IntIT(bits))
}

case class ConstantBoolIE(value: Boolean) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = CoordI[cI](MutableShareI, BoolIT())
}

case class ConstantStrIE(value: String) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = CoordI[cI](MutableShareI, StrIT())
}

case class ConstantFloatIE(value: Double) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result = CoordI[cI](MutableShareI, FloatIT())
}

case class LocalLookupIE(
  // This is the local variable at the time it was created
  localVariable: ILocalVariableI,
//  // The instantiator might want to load this as a different region mutability than the mutability
//  // when originally created, so tihs field will be able to hold that.
//  // Conceptually, it's the current mutability of the source region at the time of the local lookup.
//  pureHeight: Int,
  // nevermind, we leave it to SoftLoad to figure out the target ownership/immutability

  //  reference: CoordI[cI],
  //  variability: VariabilityI
  result: CoordI[cI]
) extends AddressExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def variability: VariabilityI = localVariable.variability
}

case class ArgLookupIE(
  paramIndex: Int,
  coord: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: CoordI[cI] = coord
}

case class StaticSizedArrayLookupIE(
  range: RangeS,
  arrayExpr: ReferenceExpressionIE,
  indexExpr: ReferenceExpressionIE,
  // See RMLRMO for why this is the same ownership as the original field.
  elementType: CoordI[cI],
  // See RMLRMO for why we dont have a targetOwnership field here.
  variability: VariabilityI
) extends AddressExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  // See RMLRMO why we just return the element type.
  override def result: CoordI[cI] = elementType
}

case class RuntimeSizedArrayLookupIE(
  arrayExpr: ReferenceExpressionIE,
//  arrayType: RuntimeSizedArrayIT[cI],
  indexExpr: ReferenceExpressionIE,
  // See RMLRMO for why this is the same ownership as the original field.
  elementType: CoordI[cI],
  // See RMLRMO for why we dont have a targetOwnership field here.
  variability: VariabilityI
) extends AddressExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  vassert(arrayExpr.result.kind == arrayType)

  // See RMLRMO why we just return the element type.
  override def result: CoordI[cI] = elementType
}

case class ArrayLengthIE(arrayExpr: ReferenceExpressionIE) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: CoordI[cI] = CoordI[cI](MutableShareI, IntIT(32))
}

case class ReferenceMemberLookupIE(
  range: RangeS,
  structExpr: ReferenceExpressionIE,
  memberName: IVarNameI[cI],
  // See RMLRMO for why this is the same ownership as the original field.
  memberReference: CoordI[cI],
  // See RMLRMO for why we dont have a targetOwnership field here.
  variability: VariabilityI
) extends AddressExpressionIE {
  vpass()

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  // See RMLRMO why we just return the member type.
  override def result: CoordI[cI] = memberReference
}
case class AddressMemberLookupIE(
  structExpr: ReferenceExpressionIE,
  memberName: IVarNameI[cI],
  // See RMLRMO for why this is the same ownership as the original field.
  memberReference: CoordI[cI],
  variability: VariabilityI
) extends AddressExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  // See RMLRMO why we just return the member type.
  override def result: CoordI[cI] = memberReference
}

case class InterfaceFunctionCallIE(
  superFunctionPrototype: PrototypeI[cI],
  virtualParamIndex: Int,
  args: Vector[ReferenceExpressionIE],
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe: CoordI[cI] = ReferenceResultI(resultReference)
}

case class ExternFunctionCallIE(
  prototype2: PrototypeI[cI],
  args: Vector[ReferenceExpressionIE],
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  // We dont:
  //   vassert(prototype2.fullName.last.templateArgs.isEmpty)
  // because we totally can have extern templates.
  // Will one day be useful for plugins, and we already use it for
  // lock<T>, which is generated by the backend.

  prototype2.id.localName match {
    case ExternFunctionNameI(_, _) =>
    case _ => vwat()
  }



//  override def resultRemoveMe = ReferenceResultI(prototype2.returnType)
}

case class FunctionCallIE(
  callable: PrototypeI[cI],
  args: Vector[ReferenceExpressionIE],
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  vassert(callable.paramTypes.size == args.size)
  args.map(_.result).zip(callable.paramTypes).foreach({
    case (CoordI(_, NeverIT(_)), _) =>
    case (a, b) => vassert(a == b)
  })

//  override def resultRemoveMe: CoordI[cI] = {
//    ReferenceResultI(callable.returnType)
//  }
}

// A typingpass reinterpret is interpreting a type as a different one which is hammer-equivalent.
// For example, a pack and a struct are the same thing to hammer.
// Also, a closure and a struct are the same thing to hammer.
// But, Compiler attaches different meanings to these things. The typingpass is free to reinterpret
// between hammer-equivalent things as it wants.
case class ReinterpretIE(
  expr: ReferenceExpressionIE,
  resultReference: CoordI[cI],
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(expr.result != resultReference)

//  override def resultRemoveMe = ReferenceResultI(resultReference)

  expr.result.kind match {
    // Unless it's a Never...
    case NeverIT(_) =>
    case _ => {
      if (resultReference.ownership != expr.result.ownership) {
        // Cant reinterpret to a different ownership!
        vfail("wat");
      }
    }
  }
}

case class ConstructIE(
  structTT: StructIT[cI],
  result: CoordI[cI],
  args: Vector[ExpressionI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()

//  override def resultRemoveMe = ReferenceResultI(resultReference)
}

// NoIE: the functionpointercall's last argument is a Placeholder2,
// it's up to later stages to replace that with an actual index
case class NewMutRuntimeSizedArrayIE(
  arrayType: RuntimeSizedArrayIT[cI],
  capacityExpr: ReferenceExpressionIE,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe: CoordI[cI] = {
//    ReferenceResultI(
//      CoordI[cI](
//        arrayType.mutability match {
//          case MutableI => OwnI
//          case ImmutableI => MutableShareI
//        },
//        arrayType))
//  }
}

case class StaticArrayFromCallableIE(
  arrayType: StaticSizedArrayIT[cI],
  generator: ReferenceExpressionIE,
  generatorMethod: PrototypeI[cI],
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe: CoordI[cI] = {
//    ReferenceResultI(
//      CoordI[cI](
//        arrayType.mutability match {
//          case MutableI => OwnI
//          case ImmutableI => MutableShareI
//        },
//        arrayType))
//  }
}

// NoIE: the functionpointercall's last argument is a Placeholder2,
// it's up to later stages to replace that with an actual index
// This returns nothing, as opposed to DrainStaticSizedArray2 which returns a
// sequence of results from the call.
case class DestroyStaticSizedArrayIntoFunctionIE(
  arrayExpr: ReferenceExpressionIE,
  arrayType: StaticSizedArrayIT[cI],
  consumer: ReferenceExpressionIE,
  consumerMethod: PrototypeI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(consumerMethod.paramTypes.size == 2)
//  vassert(consumerMethod.paramTypes(0) == consumer.result)
  vassert(consumerMethod.paramTypes(1) == arrayType.elementType.coord)

  // See https://github.com/ValeLang/Vale/issues/375
  consumerMethod.returnType.kind match {
    case StructIT(IdI(_, _, StructNameI(StructTemplateNameI(name), _))) => {
      vassert(name.str == "Tup")
    }
    case VoidIT() =>
    case _ => vwat()
  }

  override def result: CoordI[cI] = CoordI[cI](MutableShareI, VoidIT())
}

// We destroy both Share and Own things
// If the struct contains any addressibles, those die immediately and aren't stored
// in the destination variables, which is why it's a list of ReferenceLocalVariable2.
case class DestroyStaticSizedArrayIntoLocalsIE(
  expr: ReferenceExpressionIE,
  staticSizedArray: StaticSizedArrayIT[cI],
  destinationReferenceVariables: Vector[ReferenceLocalVariableI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: CoordI[cI] = CoordI[cI](MutableShareI, VoidIT())

  vassert(expr.result.kind == staticSizedArray)
}

case class DestroyMutRuntimeSizedArrayIE(
  arrayExpr: ReferenceExpressionIE
) extends ReferenceExpressionIE {
  override def result: CoordI[cI] = CoordI[cI](MutableShareI, VoidIT())
}

case class RuntimeSizedArrayCapacityIE(
  arrayExpr: ReferenceExpressionIE
) extends ReferenceExpressionIE {
  override def result: CoordI[cI] = CoordI[cI](MutableShareI, IntIT(32))
//  override def resultRemoveMe: CoordI[cI] = ReferenceResultI(CoordI[cI](MutableShareI, IntIT(32)))
}

case class PushRuntimeSizedArrayIE(
  arrayExpr: ReferenceExpressionIE,
  //  arrayType: RuntimeSizedArrayIT[cI],
  newElementExpr: ReferenceExpressionIE,
  //  newElementType: CoordI[cI]
) extends ReferenceExpressionIE {
  override def result: CoordI[cI] = CoordI[cI](MutableShareI, VoidIT())
}

case class PopRuntimeSizedArrayIE(
  arrayExpr: ReferenceExpressionIE,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  private val elementType =
    arrayExpr.result.kind match {
      case contentsRuntimeSizedArrayIT(_, e, _) => e
      case other => vwat(other)
    }
//  override def resultRemoveMe: CoordI[cI] = ReferenceResultI(elementType)
}

case class InterfaceToInterfaceUpcastIE(
  innerExpr: ReferenceExpressionIE,
  targetInterface: InterfaceIT[cI],
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  def result: ReferenceResultI = {
//    ReferenceResultI(
//      CoordI[cI](
//        innerExpr.result.ownership,
//        targetInterface))
//  }
}

// This used to be StructToInterfaceUpcastIE, and then we added generics.
// Now, it could be that we're upcasting a placeholder to an interface, or a
// placeholder to another placeholder. For all we know, this'll eventually be
// upcasting an int to an int.
// So, the target kind can be anything, not just an interface.
case class UpcastIE(
  innerExpr: ReferenceExpressionIE,
  targetInterface: InterfaceIT[cI],
  // This is the impl we use to allow/permit the upcast. It'll be useful for monomorphization
  // and later on for locating the itable ptr to put in fat pointers.
  implName: IdI[cI, IImplNameI[cI]],
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  def result: ReferenceResultI = {
//    ReferenceResultI(
//      CoordI[cI](
//        innerExpr.result.ownership,
//        targetSuperKind))
//  }
}

// A soft load is one that turns an int&& into an int*. a hard load turns an int* into an int.
// Turns an Addressible(Pointer) into an OwningPointer. Makes the source owning pointer into null

// If the source was an own and target is borrow, that's a point

case class SoftLoadIE(
  expr: AddressExpressionIE,
  targetOwnership: OwnershipI,
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  vassert(targetOwnership == result.ownership)

  if (targetOwnership == MutableShareI) {
    vassert(expr.result.ownership != ImmutableShareI)
  }
  vassert(targetOwnership != OwnI) // need to unstackify or destroy to get an owning reference
  // This is just here to try the asserts inside Coord's constructor
  CoordI[cI](targetOwnership, expr.result.kind)

  result.kind match {
    case IntIT(_) | BoolIT() | FloatIT() => {
      vassert(targetOwnership == MutableShareI)
    }
    case _ =>
  }

//  override def resultRemoveMe: CoordI[cI] = {
//    ReferenceResultI(CoordI[cI](targetOwnership, expr.result.kind))
//  }
}

// Destroy an object.
// If the struct contains any addressibles, those die immediately and aren't stored
// in the destination variables, which is why it's a list of ReferenceLocalVariable2.
//
// We also destroy shared things with this, see DDSOT.
case class DestroyIE(
  expr: ReferenceExpressionIE,
  structTT: StructIT[cI],
  destinationReferenceVariables: Vector[ReferenceLocalVariableI]
) extends ReferenceExpressionIE {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def result: CoordI[cI] = CoordI[cI](MutableShareI, VoidIT())
}

case class DestroyImmRuntimeSizedArrayIE(
  arrayExpr: ReferenceExpressionIE,
  arrayType: RuntimeSizedArrayIT[cI],
  consumer: ReferenceExpressionIE,
  consumerMethod: PrototypeI[cI]
) extends ReferenceExpressionIE {
  arrayType.mutability match {
    case ImmutableI =>
    case _ => vwat()
  }

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vassert(consumerMethod.paramTypes.size == 2)
  vassert(consumerMethod.paramTypes(0) == consumer.result)
  //  vassert(consumerMethod.paramTypes(1) == Program2.intType)
  vassert(consumerMethod.paramTypes(1) == arrayType.elementType.coord)

  // See https://github.com/ValeLang/Vale/issues/375
  consumerMethod.returnType.kind match {
    case VoidIT() =>
  }

  override def result: CoordI[cI] = CoordI[cI](MutableShareI, VoidIT())
}

// NoIE: the functionpointercall's last argument is a Placeholder2,
// it's up to later stages to replace that with an actual index
case class NewImmRuntimeSizedArrayIE(
  arrayType: RuntimeSizedArrayIT[cI],
  sizeExpr: ReferenceExpressionIE,
  generator: ReferenceExpressionIE,
  generatorMethod: PrototypeI[cI],
  result: CoordI[cI]
) extends ReferenceExpressionIE {
  arrayType.mutability match {
    case ImmutableI =>
    case _ => vwat()
  }
  // We dont want to own the generator
  generator.result.ownership match {
    case MutableBorrowI | ImmutableBorrowI | ImmutableShareI | MutableShareI =>
    case other => vwat(other)
  }
  generatorMethod.returnType.ownership match {
    case ImmutableShareI | MutableShareI =>
    case other => vwat(other)
  }

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def resultRemoveMe: CoordI[cI] = {
//    ReferenceResultI(
//      CoordI[cI](
//        arrayType.mutability match {
//          case MutableI => OwnI
//          case ImmutableI => MutableShareI
//        },
//        arrayType))
//  }
}

object referenceExprResultStructName {
  def unapply(expr: ReferenceExpressionIE): Option[StrI] = {
    expr.result.kind match {
      case StructIT(IdI(_, _, StructNameI(StructTemplateNameI(name), _))) => Some(name)
      case _ => None
    }
  }
}

object referenceExprResultKind {
  def unapply(expr: ReferenceExpressionIE): Option[KindIT[cI]] = {
    Some(expr.result.kind)
  }
}
