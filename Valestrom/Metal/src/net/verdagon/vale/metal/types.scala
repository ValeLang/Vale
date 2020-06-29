package net.verdagon.vale.metal

import net.verdagon.vale.vfail

// Represents a reference type.
// A reference contains these things:
// - The referend; the thing that this reference points at.
// - The ownership; this reference's relationship to the referend. This can be:
//   - Share, which means the references all share ownership of the referend. This
//     means that the referend will only be deallocated once all references to it are
//     gone. Share references can only point at immutable referends, and immutable
//     referends can *only* be pointed at by share references.
//   - Owning, which means this reference owns the object, and when this reference
//     disappears (without being moved), the object should disappear (this is taken
//     care of by the typing stage). Owning refs can only point at mutable referends.
//   - Constraint, which means this reference doesn't own the referend. The referend
//     is guaranteed not to die while this constraint ref is active (indeed if it did
//     the program would panic). Constraint refs can only point at mutable referends.
//   - Raw, which is a weird ownership and should go away. We point at Void with this.
//     TODO: Get rid of raw.
//   - (in the future) Weak, which is a reference that will null itself out when the
//     referend is destroyed. Weak refs can only point at mutable referends.
// - (in the future) Permission, either readonly or readwrite.
// - (in the future) Location, either inline or yonder. Inline means that this reference
//   isn't actually a pointer, it's just the value itself, like C's Car vs Car*.
// In previous stages, this is referred to as a "coord", because these four things can be
// thought of as dimensions of a coordinate.
case class ReferenceH[+T <: ReferendH](ownership: OwnershipH, kind: T) {
  kind match {
    case IntH() | BoolH() | StrH() | FloatH() => {
      // Make sure that if we're pointing at a primitives, it's via a Share reference.
      if (ownership != ShareH) {
        vfail("wot")
      }
    }
    case _ =>
  }

  // Convenience function for casting this to a Reference which the compiler knows
  // points at a known size array.
  def expectKnownSizeArrayReference() = {
    kind match {
      case atH @ KnownSizeArrayTH(_, _) => ReferenceH[KnownSizeArrayTH](ownership, atH)
    }
  }
  // Convenience function for casting this to a Reference which the compiler knows
  // points at an unknown size array.
  def expectUnknownSizeArrayReference() = {
    kind match {
      case atH @ UnknownSizeArrayTH(_) => ReferenceH[UnknownSizeArrayTH](ownership, atH)
    }
  }
  // Convenience function for casting this to a Reference which the compiler knows
  // points at struct.
  def expectStructReference() = {
    kind match {
      case atH @ StructRefH(_) => ReferenceH[StructRefH](ownership, atH)
    }
  }
}

// A value, a thing that can be pointed at. See ReferenceH for more information.
sealed trait ReferendH

case class IntH() extends ReferendH
case class BoolH() extends ReferendH
case class StrH() extends ReferendH
case class FloatH() extends ReferendH
// A primitive which can never be instantiated. If something returns this, it
// means that it will never actually return. For example, the return type of
// panic() is a NeverH.
// TODO: This feels weird being a referend in metal. Figure out a way to not
// have this? Perhaps replace all referends with Optional[Optional[ReferendH]],
// where None is never, Some(None) is Void, and Some(Some(_)) is a normal thing.[
case class NeverH() extends ReferendH

case class InterfaceRefH(
  // Unique identifier for the interface.
  fullName: FullNameH
) extends ReferendH

case class StructRefH(
  // Unique identifier for the interface.
  fullName: FullNameH
) extends ReferendH

// An array whose size is known at compile time, and therefore doesn't need to
// carry around its size at runtime.
case class KnownSizeArrayTH(
  // The size of the array.
  size: Int,
  // The underlying array.
  rawArray: RawArrayTH
) extends ReferendH

case class UnknownSizeArrayTH(
  // The underlying array.
  rawArray: RawArrayTH
) extends ReferendH

// This is not a referend, but instead has the common fields of UnknownSizeArrayTH/KnownSizeArrayTH,
// and lets us handle their code similarly.
case class RawArrayTH(elementType: ReferenceH[ReferendH])


// These are a lowered form of template arguments, they're only preserved by the hammer so that
// we can uniquely identify things. For example, we don't want sum(Int, Int)Int and
// sum(Float, Float)Float to have the same name, and we don't want List<Bool> and List<Str> to
// collide, so we include the arguments and template arguments in FullName.
//sealed trait ITemplataH
//case class CoordTemplataH(reference: ReferenceH[ReferendH]) extends ITemplataH
//case class KindTemplataH(referend: ReferendH) extends ITemplataH
//case class ArrayTemplateTemplataH() extends ITemplataH
//case class FunctionTemplataH(envName: FullNameH, unevaluatedContainers: List[ContainerH], humanName: String, location: CodeLocation) extends ITemplataH
//case class StructTemplataH(envName: FullNameH, humanName: String, location: CodeLocation) extends ITemplataH
//case class InterfaceTemplataH(envName: FullNameH, humanName: String, location: CodeLocation) extends ITemplataH
//case class ImplTemplataH(envName: FullNameH, location: CodeLocation) extends ITemplataH
//case class ExternFunctionTemplataH(fullName: FullNameH) extends ITemplataH
//case class OwnershipTemplataH(ownership: Ownership) extends ITemplataH
//case class VariabilityTemplataH(variability: Variability) extends ITemplataH
//case class MutabilityTemplataH(mutability: Mutability) extends ITemplataH
//case class PermissionTemplataH(mutability: Permission) extends ITemplataH
//case class LocationTemplataH(mutability: Location) extends ITemplataH
//case class BooleanTemplataH(value: Boolean) extends ITemplataH
//case class IntegerTemplataH(value: Integer) extends ITemplataH
//case class ContainerH(humanName: String, location: CodeLocation)

// Place in the original source code that something came from. Useful for uniquely
// identifying templates.
case class CodeLocation(
  line: Int,
  char: Int)

// Ownership is the way a reference relates to the referend's lifetime, see
// ReferenceH for explanation.
sealed trait OwnershipH
case object OwnH extends OwnershipH
case object BorrowH extends OwnershipH
case object ShareH extends OwnershipH

// Permission says whether a reference can modify the referend it's pointing at.
// See ReferenceH for explanation.
sealed trait Permission
case object Readonly extends Permission
case object Readwrite extends Permission
case object ExclusiveReadwrite extends Permission

// Location says whether a reference contains the referend's location (yonder) or
// contains the referend itself (inline).
// Yes, it's weird to consider a reference containing a referend, but it makes a
// lot of things simpler for the language.
// Examples (with C++ translations):
//   This will create a variable `car` that lives on the stack ("inline"):
//     Vale: car = inl Car(4, "Honda Civic");
//     C++:  Car car(4, "Honda Civic");
//   This will create a variable `car` that lives on the heap ("yonder"):
//     Vale: car = Car(4, "Honda Civic");
//     C++:  Car* car = new Car(4, "Honda Civic");
//   This will create a struct Spaceship whose engine and reactor are allocated
//   separately somewhere else on the heap (yonder):
//     Vale: struct Car { engine Engine; reactor Reactor; }
//     C++:  class Car { Engine* engine; Reactor* reactor; }
//   This will create a struct Spaceship whose engine and reactor are embedded
//   into its own memory (inline):
//     Vale: struct Car { engine inl Engine; reactor inl Reactor; }
//     C++:  class Car { Engine engine; Reactor reactor; }
// Note that the compiler will often automatically add an `inl` onto whatever
// local variables it can, to speed up the program.
sealed trait Location
// Means that the referend will be in the containing stack frame or struct.
case object Inline extends Location
// Means that the referend will be allocated separately, in the heap.
case object Yonder extends Location

// Used to say whether an object can be modified or not.
// Structs and interfaces specify whether theyre immutable or mutable, but all
// primitives are immutable (after all, you can't change 4 itself to be another
// number).
sealed trait Mutability
// Immutable structs can only contain or point at other immutable structs, in
// other words, something immutable is *deeply* immutable.
// Immutable things can only be referred to with Share references.
case object Immutable extends Mutability
// Mutable objects have a lifetime.
case object Mutable extends Mutability

// Used to say whether a variable (or member) reference can be changed to point
// at something else.
// Examples (with C++ translations):
//   This will create a varying local, which can be changed to point elsewhere:
//     Vale:
//       x! = Car(4, "Honda Civic");
//       mut x = someOtherCar;
//       mut x = Car(4, "Toyota Camry");
//     C++:
//       Car* x = new Car(4, "Honda Civic");
//       x = someOtherCar;
//       x = new Car(4, "Toyota Camry");
//   This will create a final local, which can't be changed to point elsewhere:
//     Vale: x = Car(4, "Honda Civic");
//     C++:  Car* const x = new Car(4, "Honda Civic");
//   Note the position of the const, which says that the pointer cannot change,
//   but we can still change the members of the Car, which is also true in Vale:
//     Vale:
//       x = Car(4, "Honda Civic");
//       mut x.numWheels = 6;
//     C++:
//       Car* const x = new Car(4, "Honda Civic");
//       x->numWheels = 6;
// In other words, variability affects whether the variable (or member) can be
// changed to point at something different, but it doesn't affect whether we can
// change anything inside the referend (this reference's permission and the
// referend struct's member's variability affect that).
sealed trait Variability
case object Final extends Variability
case object Varying extends Variability
