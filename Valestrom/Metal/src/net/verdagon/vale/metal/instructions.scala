package net.verdagon.vale.metal

import net.verdagon.vale.templar.{FullName2, IVarName2, LetNormal2}
import net.verdagon.vale.{vassert, vcurious, vfail, vwat}

// Common trait for all instructions.
sealed trait ExpressionH[+T <: ReferendH] {
  def resultType: ReferenceH[T]

  def expectStructAccess(): ExpressionH[StructRefH] = {
    resultType match {
      case ReferenceH(_, _, x @ StructRefH(_)) => {
        this.asInstanceOf[ExpressionH[StructRefH]]
      }
      case _ => vfail()
    }
  }
  def expectInterfaceAccess(): ExpressionH[InterfaceRefH] = {
    resultType match {
      case ReferenceH(_, _, x @ InterfaceRefH(_)) => {
        this.asInstanceOf[ExpressionH[InterfaceRefH]]
      }
    }
  }
  def expectUnknownSizeArrayAccess(): ExpressionH[UnknownSizeArrayTH] = {
    resultType match {
      case ReferenceH(_, _, x @ UnknownSizeArrayTH(_, _)) => {
        this.asInstanceOf[ExpressionH[UnknownSizeArrayTH]]
      }
    }
  }
  def expectKnownSizeArrayAccess(): ExpressionH[KnownSizeArrayTH] = {
    resultType match {
      case ReferenceH(_, _, x @ KnownSizeArrayTH(_, _, _)) => {
        this.asInstanceOf[ExpressionH[KnownSizeArrayTH]]
      }
    }
  }
  def expectIntAccess(): ExpressionH[IntH] = {
    resultType match {
      case ReferenceH(_, _, x @ IntH()) => {
        this.asInstanceOf[ExpressionH[IntH]]
      }
    }
  }
  def expectBoolAccess(): ExpressionH[BoolH] = {
    resultType match {
      case ReferenceH(_, _, x @ BoolH()) => {
        this.asInstanceOf[ExpressionH[BoolH]]
      }
    }
  }
}

// Creates an integer and puts it into a register.
case class ConstantI64H(
  // The value of the integer.
  value: Int
) extends ExpressionH[IntH] {
  override def resultType: ReferenceH[IntH] = ReferenceH(ShareH, InlineH, IntH())
}

// Creates a boolean and puts it into a register.
case class ConstantBoolH(
  // The value of the boolean.
  value: Boolean
) extends ExpressionH[BoolH] {
  override def resultType: ReferenceH[BoolH] = ReferenceH(ShareH, InlineH, BoolH())
}

// Creates a string and puts it into a register.
case class ConstantStrH(
  // The value of the string.
  value: String
) extends ExpressionH[StrH] {
  override def resultType: ReferenceH[StrH] = ReferenceH(ShareH, YonderH, StrH())
}

// Creates a float and puts it into a register.
case class ConstantF64H(
  // The value of the float.
  value: Float
) extends ExpressionH[FloatH] {
  override def resultType: ReferenceH[FloatH] = ReferenceH(ShareH, InlineH, FloatH())
}

// Grabs the argument and puts it into a register.
// There can only be one of these per argument; this conceptually destroys
// the containing argument and puts its value into the register.
case class ArgumentH(
  resultType: ReferenceH[ReferendH],
  // The index of the argument, starting at 0.
  argumentIndex: Int
) extends ExpressionH[ReferendH]

// Takes a value from a register (the "source" register) and puts it into a local
// variable on the stack.
case class StackifyH(
  // The register to read a value from.
  // As with any read from a register, this will invalidate the register.
  sourceExpr: ExpressionH[ReferendH],
  // Describes the local we're making.
  local: Local,
  // Name of the local variable. Used for debugging.
  name: Option[FullNameH]
) extends ExpressionH[ReferendH] {
  vassert(sourceExpr.resultType.kind == NeverH() ||
    sourceExpr.resultType == local.typeH)

  override def resultType: ReferenceH[StructRefH] = ReferenceH(ShareH, InlineH, ProgramH.emptyTupleStructRef)
}

// Takes a value from a local variable on the stack, and moves it into a register.
// The local variable is now invalid, since its value has been taken out.
// See LocalLoadH for a similar instruction that *doesnt* invalidate the local var.
case class UnstackifyH(
  // Describes the local we're pulling from. This is equal to the corresponding
  // StackifyH's `local` member.
  local: Local,
//  // The type we expect to get out of the local. Should be equal to local.
//  // TODO: If the vcurious below doesn't panic, then let's get rid of this redundant member.
//  resultType: ReferenceH[ReferendH]
) extends ExpressionH[ReferendH] {
  // Panics if this is ever not the case.
  vcurious(local.typeH == resultType)

  override def resultType: ReferenceH[ReferendH] = local.typeH
}

// Takes a struct from the given register, and destroys it.
// All of its members are saved from the jaws of death, and put into the specified
// local variables.
// This creates those local variables, much as a StackifyH would, and puts into them
// the values from the dying struct instance.
case class DestroyH(
  // The register to take the struct from.
  // As with any read from a register, this will invalidate the register.
  structRegister: ExpressionH[StructRefH],
  // A list of types, one for each local variable we'll make.
  // TODO: If the vcurious below doesn't panic, get rid of this redundant member.
  localTypes: List[ReferenceH[ReferendH]],
  // The locals to put the struct's members into.
localIndices: Vector[Local],
) extends ExpressionH[StructRefH] {
  vassert(localTypes.size == localIndices.size)
  vcurious(localTypes == localIndices.map(_.typeH).toList)

  override def resultType: ReferenceH[StructRefH] = ReferenceH(ShareH, InlineH, ProgramH.emptyTupleStructRef)
}

// Takes a struct from the given register, and destroys it.
// All of its members are saved from the jaws of death, and put into the specified
// local variables.
// This creates those local variables, much as a StackifyH would, and puts into them
// the values from the dying struct instance.
case class DestroyKnownSizeArrayIntoLocalsH(
  // The register to take the struct from.
  // As with any read from a register, this will invalidate the register.
  structRegister: ExpressionH[KnownSizeArrayTH],
  // A list of types, one for each local variable we'll make.
  // TODO: If the vcurious below doesn't panic, get rid of this redundant member.
  localTypes: List[ReferenceH[ReferendH]],
  // The locals to put the struct's members into.
  localIndices: Vector[Local]
) extends ExpressionH[StructRefH] {
  vassert(localTypes.size == localIndices.size)
  vcurious(localTypes == localIndices.map(_.typeH).toList)

  override def resultType: ReferenceH[StructRefH] = ReferenceH(ShareH, InlineH, ProgramH.emptyTupleStructRef)
}

// Takes a struct reference from the "source" register, and makes an interface reference
// to it, as the "target" reference, and puts it into another register.
case class StructToInterfaceUpcastH(
  // The register to get the struct reference from.
  // As with any read from a register, this will invalidate the register.
  sourceRegister: ExpressionH[StructRefH],
  // The type of interface to cast to.
  targetInterfaceRef: InterfaceRefH
) extends ExpressionH[InterfaceRefH] {
  // The resulting type will have the same ownership as the source register had.
  def resultType = ReferenceH(sourceRegister.resultType.ownership, sourceRegister.resultType.location, targetInterfaceRef)
}

// Takes an interface reference from the "source" register, and makes another reference
// to it, as the "target" inference, and puts it into another register.
case class InterfaceToInterfaceUpcastH(
  // The register to get the source interface reference from.
  // As with any read from a register, this will invalidate the register.
  sourceRegister: ExpressionH[InterfaceRefH],
  // The type of interface to cast to.
  targetInterfaceRef: InterfaceRefH
) extends ExpressionH[InterfaceRefH] {
  // The resulting type will have the same ownership as the source register had.
  def resultType = ReferenceH(sourceRegister.resultType.ownership, sourceRegister.resultType.location, targetInterfaceRef)
}

case class ReinterpretH(
  sourceExpr: ExpressionH[ReferendH],
  resultType: ReferenceH[ReferendH]
) extends ExpressionH[ReferendH] {
  // We shouldn't abuse ReinterpretH, it was originally only added for NeverH.
  // Templar's reinterpret is much more flexible, but they don't necessarily
  // mean the hammer should do lots of reinterprets.
  vassert(sourceExpr.resultType.kind == NeverH())
}

// Takes a reference from the given "source" register, and puts it into an *existing*
// local variable.
case class LocalStoreH(
  // The existing local to store into.
  local: Local,
  // The register to get the source reference from.
  // As with any read from a register, this will invalidate the register.
  sourceRegister: ExpressionH[ReferendH],
  // Name of the local variable, for debug purposes.
  localName: FullNameH
) extends ExpressionH[ReferendH] {
  override def resultType: ReferenceH[ReferendH] = local.typeH
}

// Takes a reference from the given local variable, and puts it into a new register.
// This can never move a reference, only alias it. The instruction which can move a
// reference is UnstackifyH.
case class LocalLoadH(
  // The existing local to load from.
  local: Local,
  // The ownership of the reference to put into the register. This doesn't have to
  // match the ownership of the reference from the local. For example, we might want
  // to load a constraint reference from an owning local.
  targetOwnership: OwnershipH,
  // Name of the local variable, for debug purposes.
  localName: FullNameH
) extends ExpressionH[ReferendH] {
  override def resultType: ReferenceH[ReferendH] = {
    val location =
      (targetOwnership, local.typeH.location) match {
        case (BorrowH, _) => YonderH
        case (WeakH, _) => YonderH
        case (OwnH, location) => location
        case (ShareH, location) => location
      }
    ReferenceH(targetOwnership, location, local.typeH.kind)
  }
}

// Takes a reference from the given "source" register, and swaps it into the given
// struct's member. The member's old reference is put into a new register.
case class MemberStoreH(
  resultType: ReferenceH[ReferendH],
  // Register containing a reference to the struct whose member we will swap.
  structRegister: ExpressionH[StructRefH],
  // Which member to swap out, starting at 0.
  memberIndex: Int,
  // Register containing the new value for the struct's member.
  // As with any read from a register, this will invalidate the register.
  sourceRegister: ExpressionH[ReferendH],
  // Name of the member, for debug purposes.
  memberName: FullNameH
) extends ExpressionH[ReferendH]

// Takes a reference from the given "struct" register, and copies it into a new
// register. This can never move a reference, only alias it.
case class MemberLoadH(
  // Register containing a reference to the struct whose member we will read.
  // As with any read from a register, this will invalidate the register.
  structRegister: ExpressionH[StructRefH],
  // Which member to read from, starting at 0.
  memberIndex: Int,
  // The ownership to load as. For example, we might load a constraint reference from a
  // owning Car reference member.
  targetOwnership: OwnershipH,
  // The type we expect the member to be. This can easily be looked up, but is provided
  // here to be convenient for LLVM.
  expectedMemberType: ReferenceH[ReferendH],
//  // The type of the resulting reference.
//  resultType: ReferenceH[ReferendH],
  // Member's name, for debug purposes.
  memberName: FullNameH
) extends ExpressionH[ReferendH] {
  vassert(resultType.ownership == targetOwnership)

  override def resultType: ReferenceH[ReferendH] = {
    val location =
      (targetOwnership, expectedMemberType.location) match {
        case (BorrowH, _) => YonderH
        case (OwnH, location) => location
        case (ShareH, location) => location
      }
    ReferenceH(targetOwnership, location, expectedMemberType.kind)
  }
}

// Creates an array whose size is fixed and known at compile time, and puts it into
// a register.
case class NewArrayFromValuesH(
  // The resulting type of the array.
  // TODO: See if we can infer this from the types in the registers.
  resultType: ReferenceH[KnownSizeArrayTH],
  // The registers from which we'll get the values to put into the array.
  // As with any read from a register, this will invalidate the registers.
  sourceRegisters: List[ExpressionH[ReferendH]]
) extends ExpressionH[KnownSizeArrayTH]

// Loads from the "source" register and swaps it into the array from arrayRegister at
// the position specified by the integer in indexRegister. The old value from the
// array is moved out into registerId.
// This is for the kind of array whose size we know at compile time, the kind that
// doesn't need to carry around a size. For the corresponding instruction for the
// unknown-size-at-compile-time array, see UnknownSizeArrayStoreH.
case class KnownSizeArrayStoreH(
                                 // Register containing the array whose element we'll swap out.
                                 // As with any read from a register, this will invalidate the register.
                                 arrayRegister: ExpressionH[KnownSizeArrayTH],
                                 // Register containing the index of the element we'll swap out.
                                 // As with any read from a register, this will invalidate the register.
                                 indexRegister: ExpressionH[IntH],
                                 // Register containing the value we'll swap into the array.
                                 // As with any read from a register, this will invalidate the register.
                                 sourceRegister: ExpressionH[ReferendH]
) extends ExpressionH[ReferendH] {
  override def resultType: ReferenceH[ReferendH] = arrayRegister.resultType.kind.rawArray.elementType
}

// Loads from the "source" register and swaps it into the array from arrayRegister at
// the position specified by the integer in indexRegister. The old value from the
// array is moved out into registerId.
// This is for the kind of array whose size we don't know at compile time, the kind
// that needs to carry around a size. For the corresponding instruction for the
// known-size-at-compile-time array, see KnownSizeArrayStoreH.
case class UnknownSizeArrayStoreH(
                                   // Register containing the array whose element we'll swap out.
                                   // As with any read from a register, this will invalidate the register.
                                   arrayRegister: ExpressionH[UnknownSizeArrayTH],
                                   // Register containing the index of the element we'll swap out.
                                   // As with any read from a register, this will invalidate the register.
                                   indexRegister: ExpressionH[IntH],
                                   // Register containing the value we'll swap into the array.
                                   // As with any read from a register, this will invalidate the register.
                                   sourceRegister: ExpressionH[ReferendH]
) extends ExpressionH[ReferendH] {
  override def resultType: ReferenceH[ReferendH] = arrayRegister.resultType.kind.rawArray.elementType
}

// Loads from the array in arrayRegister at the index in indexRegister, and stores
// the result in registerId. This can never move a reference, only alias it.
// This is for the kind of array whose size we don't know at compile time, the kind
// that needs to carry around a size. For the corresponding instruction for the
// known-size-at-compile-time array, see KnownSizeArrayLoadH.
case class UnknownSizeArrayLoadH(
  // Register containing the array whose element we'll read.
  // As with any read from a register, this will invalidate the register.
  arrayRegister: ExpressionH[UnknownSizeArrayTH],
  // Register containing the index of the element we'll read.
  // As with any read from a register, this will invalidate the register.
  indexRegister: ExpressionH[IntH],
  // The ownership to load as. For example, we might load a constraint reference from a
  // owning Car reference element.
  targetOwnership: OwnershipH
) extends ExpressionH[ReferendH] {

  override def resultType: ReferenceH[ReferendH] = {
    val location =
      (targetOwnership, arrayRegister.resultType.kind.rawArray.elementType.location) match {
        case (BorrowH, _) => YonderH
        case (OwnH, location) => location
        case (ShareH, location) => location
      }
    ReferenceH(targetOwnership, location, arrayRegister.resultType.kind.rawArray.elementType.kind)
  }
}

// Loads from the array in arrayRegister at the index in indexRegister, and stores
// the result in registerId. This can never move a reference, only alias it.
// This is for the kind of array whose size we know at compile time, the kind that
// doesn't need to carry around a size. For the corresponding instruction for the
// known-size-at-compile-time array, see KnownSizeArrayStoreH.
case class KnownSizeArrayLoadH(
  // Register containing the array whose element we'll read.
  // As with any read from a register, this will invalidate the register.
  arrayRegister: ExpressionH[KnownSizeArrayTH],
  // Register containing the index of the element we'll read.
  // As with any read from a register, this will invalidate the register.
  indexRegister: ExpressionH[IntH],
  // The ownership to load as. For example, we might load a constraint reference from a
  // owning Car reference element.
  targetOwnership: OwnershipH
) extends ExpressionH[ReferendH] {

  override def resultType: ReferenceH[ReferendH] = {
    val location =
      (targetOwnership, arrayRegister.resultType.kind.rawArray.elementType.location) match {
        case (BorrowH, _) => YonderH
        case (OwnH, location) => location
        case (ShareH, location) => location
      }
    ReferenceH(targetOwnership, location, arrayRegister.resultType.kind.rawArray.elementType.kind)
  }
}

// Calls a function.
case class CallH(
  // Identifies which function to call.
  function: PrototypeH,
  // Registers containing the arguments to pass to the function.
  // As with any read from a register, this will invalidate the registers.
  argsRegisters: List[ExpressionH[ReferendH]]
) extends ExpressionH[ReferendH] {
  override def resultType: ReferenceH[ReferendH] = function.returnType
}

// Calls a function defined in some other module.
case class ExternCallH(
  // Identifies which function to call.
  function: PrototypeH,
  // Registers containing the arguments to pass to the function.
  // As with any read from a register, this will invalidate the registers.
  argsRegisters: List[ExpressionH[ReferendH]]
) extends ExpressionH[ReferendH] {
  override def resultType: ReferenceH[ReferendH] = function.returnType
}

// Calls a function on an interface.
case class InterfaceCallH(
  // Registers containing the arguments to pass to the function.
  // As with any read from a register, this will invalidate the registers.
  argsRegisters: List[ExpressionH[ReferendH]],
  // Which parameter has the interface whose table we'll read to get the function.
  virtualParamIndex: Int,
  // The type of the interface.
  // TODO: Take this out, it's redundant, can get it from argsRegisters[virtualParamIndex]
  interfaceRefH: InterfaceRefH,
  // The index in the vtable for the function.
  indexInEdge: Int,
  // The function we expect to be calling. Note that this is the prototype for the abstract
  // function, not the prototype for the function that will eventually be called. The
  // difference is that this prototype will have an interface at the virtualParamIndex'th
  // parameter, and the function that is eventually called will have a struct there.
  functionType: PrototypeH
) extends ExpressionH[ReferendH] {
  override def resultType: ReferenceH[ReferendH] = functionType.returnType
  vassert(indexInEdge >= 0)
}

// An if-statement. It will get a boolean from running conditionBlock, and use it to either
// call thenBlock or elseBlock. The result of the thenBlock or elseBlock will be put into
// registerId.
case class IfH(
  // The block for the condition. If this results in a true, we'll run thenBlock, otherwise
  // we'll run elseBlock.
  conditionBlock: ExpressionH[BoolH],
  // The block to run if conditionBlock results in a true. The result of this block will be
  // put into registerId.
  thenBlock: ExpressionH[ReferendH],
  // The block to run if conditionBlock results in a false. The result of this block will be
  // put into registerId.
  elseBlock: ExpressionH[ReferendH],

  commonSupertype: ReferenceH[ReferendH],
) extends ExpressionH[ReferendH] {
  (thenBlock.resultType.kind, elseBlock.resultType.kind) match {
    case (NeverH(), _) =>
    case (_, NeverH()) =>
    case (a, b) if a == b =>
    case _ => vwat()
  }
  override def resultType: ReferenceH[ReferendH] = commonSupertype
}

// A while loop. Continuously runs bodyBlock until it returns false.
case class WhileH(
  // The block to run until it returns false.
  bodyBlock: ExpressionH[BoolH]
) extends ExpressionH[StructRefH] {
  override def resultType: ReferenceH[StructRefH] = ReferenceH(ShareH, InlineH, ProgramH.emptyTupleStructRef)
}

// A collection of instructions. The last one will be used as the block's result.
case class ConsecutorH(
  // The instructions to run.
  nodes: List[ExpressionH[ReferendH]],
) extends ExpressionH[ReferendH] {
  // We should simplify these away
  vassert(nodes.nonEmpty)
  // The init ones should always return void structs.
  nodes.init.foreach(n => n.resultType.kind match { case StructRefH(_) => case NeverH() => })

  val indexOfFirstNever = nodes.indexWhere(_.resultType.kind == NeverH())
  if (indexOfFirstNever >= 0) {
    // The first never should be the last line. There shouldn't be anything after never.
    if (indexOfFirstNever != nodes.size - 1) {
      vfail()
    }
  }

  override def resultType: ReferenceH[ReferendH] = nodes.last.resultType
}

// An expression where all locals declared inside will be destroyed by the time we exit.
case class BlockH(
  // The instructions to run. This will probably be a consecutor.
  inner: ExpressionH[ReferendH],
) extends ExpressionH[ReferendH] {
  override def resultType: ReferenceH[ReferendH] = inner.resultType
}

// Ends the current function and returns a reference. A function will always end
// with a return statement.
case class ReturnH(
  // The register to read from, whose value we'll return from the function.
  // As with any read from a register, this will invalidate the register.
  sourceRegister: ExpressionH[ReferendH]
) extends ExpressionH[NeverH] {
  override def resultType: ReferenceH[NeverH] = ReferenceH(ShareH, InlineH, NeverH())
}

// Constructs an unknown-size array, whose length is the integer from sizeRegister,
// whose values are generated from the function from generatorRegister. Puts the
// result in a new register.
case class ConstructUnknownSizeArrayH(
  // Register containing the size of the new array.
  // As with any read from a register, this will invalidate the register.
  sizeRegister: ExpressionH[IntH],
  // Register containing the IFunction<int, T> interface reference which we'll
  // call to generate each element of the array.
  // More specifically, we'll call the "__call" function on the interface, which
  // should be the only function on it.
  // This is a constraint reference.
  // As with any read from a register, this will invalidate the register.
  generatorRegister: ExpressionH[InterfaceRefH],
  // The resulting type of the array.
  // TODO: Remove this, it's redundant with the generatorRegister's interface's
  // only method's return type.
  resultType: ReferenceH[UnknownSizeArrayTH]
) extends ExpressionH[UnknownSizeArrayTH] {
  vassert(
    generatorRegister.resultType.ownership == BorrowH ||
      generatorRegister.resultType.ownership == ShareH)
}

// Destroys an array previously created with NewArrayFromValuesH.
case class DestroyKnownSizeArrayIntoFunctionH(
  // Register containing the array we'll destroy.
  // This is an owning reference.
  // As with any read from a register, this will invalidate the register.
  arrayRegister: ExpressionH[KnownSizeArrayTH],
  // Register containing the IFunction<T, Void> interface reference which we'll
  // call to destroy each element of the array.
  // More specifically, we'll call the "__call" function on the interface, which
  // should be the only function on it.
  // This is a constraint reference.
  // As with any read from a register, this will invalidate the register.
  consumerRegister: ExpressionH[InterfaceRefH]
) extends ExpressionH[StructRefH] {
  override def resultType: ReferenceH[StructRefH] = ReferenceH(ShareH, InlineH, ProgramH.emptyTupleStructRef)
}

// Destroys an array previously created with ConstructUnknownSizeArrayH.
case class DestroyUnknownSizeArrayH(
  // Register containing the array we'll destroy.
  // This is an owning reference.
  // As with any read from a register, this will invalidate the register.
  arrayRegister: ExpressionH[UnknownSizeArrayTH],
  // Register containing the IFunction<T, Void> interface reference which we'll
  // call to destroy each element of the array.
  // More specifically, we'll call the "__call" function on the interface, which
  // should be the only function on it.
  // This is a constraint reference.
  // As with any read from a register, this will invalidate the register.
  consumerRegister: ExpressionH[InterfaceRefH]
) extends ExpressionH[StructRefH] {
  override def resultType: ReferenceH[StructRefH] = ReferenceH(ShareH, InlineH, ProgramH.emptyTupleStructRef)
}

// Creates a new struct instance.
case class NewStructH(
  // Registers containing the values we'll use as members of the new struct.
  // As with any read from a register, this will invalidate the register.
  sourceRegisters: List[ExpressionH[ReferendH]],
  // The type of struct we'll create.
  resultType: ReferenceH[StructRefH]
) extends ExpressionH[StructRefH]

// Gets the length of an unknown-sized array.
case class ArrayLengthH(
  // Register containing the array whose length we'll get.
  // As with any read from a register, this will invalidate the register.
  sourceRegister: ExpressionH[ReferendH],
) extends ExpressionH[IntH] {
  override def resultType: ReferenceH[IntH] = ReferenceH(ShareH, InlineH, IntH())
}

// Turns a constraint ref into a weak ref.
case class WeakAliasH(
  // Register containing the constraint reference to turn into a weak ref.
  refRegister: ExpressionH[ReferendH],
) extends ExpressionH[ReferendH] {
  override def resultType: ReferenceH[ReferendH] = ReferenceH(WeakH, YonderH, refRegister.resultType.kind)
}

// Locks a weak ref to turn it into an optional of borrow ref.
case class LockWeakH(
  // Register containing the array whose length we'll get.
  // As with any read from a register, this will invalidate the register.
  sourceRegister: ExpressionH[ReferendH],
  // Should be an owned ref to optional of borrow ref of something
  resultType: ReferenceH[InterfaceRefH],
  // Function to give a borrow ref to to make a Some(borrow ref)
  someConstructor: PrototypeH,
  // Function to make a None of the right type
  noneConstructor: PrototypeH,
) extends ExpressionH[ReferendH]

// Only used for the VM, or a debug mode. Checks that the reference count
// is as we expected.
// This instruction can be safely ignored, it's mainly here for tests.
case class CheckRefCountH(
  // Register containing the reference whose ref count we'll measure.
  refRegister: ExpressionH[ReferendH],
  // The type of ref count to check.
  category: RefCountCategory,
  // Register containing a number, so we can assert it's equal to the object's
  // ref count.
  numRegister: ExpressionH[IntH]
) extends ExpressionH[StructRefH] {
  override def resultType: ReferenceH[StructRefH] = ProgramH.emptyTupleStructType
}
// The type of ref count that an object might have. Used with the CheckRefCountH
// instruction for counting how many references of a certain type there are.
sealed trait RefCountCategory
// Used to count how many variables are refering to an object.
case object VariableRefCount extends RefCountCategory
// Used to count how many members are refering to an object.
case object MemberRefCount extends RefCountCategory
// Used to count how many arguments are refering to an object.
case object ArgumentRefCount extends RefCountCategory
// Used to count how many registers are refering to an object.
case object RegisterRefCount extends RefCountCategory

// See DINSIE for why this isn't three instructions, and why we don't have the
// destructor prototype in it.
case class DiscardH(sourceRegister: ExpressionH[ReferendH]) extends ExpressionH[StructRefH] {
  sourceRegister.resultType.ownership match {
    case BorrowH | ShareH | WeakH =>
  }
  override def resultType: ReferenceH[StructRefH] = ReferenceH(ShareH, InlineH, ProgramH.emptyTupleStructRef)
}

trait IRegisterH {
  def expectReferenceRegister(): ReferenceRegisterH = {
    this match {
      case r @ ReferenceRegisterH(_) => r
      case AddressRegisterH(_) => vfail("Expected a reference as a result, but got an address!")
    }
  }
  def expectAddressRegister(): AddressRegisterH = {
    this match {
      case a @ AddressRegisterH(_) => a
      case ReferenceRegisterH(_) => vfail("Expected an address as a result, but got a reference!")
    }
  }
}
case class ReferenceRegisterH(reference: ReferenceH[ReferendH]) extends IRegisterH
case class AddressRegisterH(reference: ReferenceH[ReferendH]) extends IRegisterH

// Identifies a local variable.
case class Local(
  // No two variables in a FunctionH have the same id.
  id: VariableIdH,

  // The type of the reference this local variable has.
  typeH: ReferenceH[ReferendH])

case class VariableIdH(
  // Just to uniquify VariableIdH instances. No two variables in a FunctionH will have
  // the same number.
  number: Int,
  // Just for debugging purposes
  name: Option[FullNameH]) {
}
