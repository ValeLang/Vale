package net.verdagon.vale.templar.templata


import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.{FullName2, FunctionName2, IFunctionName2, IVarName2}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vassertSome, vfail, vimpl}

case class CovariantFamily(
    root: Prototype2,
    covariantParamIndices: List[Int],
    overrides: List[Prototype2])

trait Queriable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T];

  def allOf[T](classs: Class[T]): List[T] = {
    all({
      case x if classs.isInstance(x) => classs.cast(x)
    })
  }

  def only[T](func: PartialFunction[Queriable2, T]): T = {
    val list = all(func)
    if (list.size > 1) {
      vfail("More than one!");
    } else if (list.isEmpty) {
      vfail("Not found!");
    }
    list.head
  }

  def onlyOf[T](classs: Class[T]): T = {
    val list =
      all({
        case x if classs.isInstance(x) => classs.cast(x)
      })
    if (list.size > 1) {
      vfail("More than one!");
    } else if (list.isEmpty) {
      vfail("Not found!");
    }
    list.head
  }
}

trait Virtuality2 extends Queriable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T];
}
case object Abstract2 extends Virtuality2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case class Override2(interface: InterfaceRef2) extends Virtuality2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ interface.all(func)
  }
}

case class Parameter2(
    name: IVarName2,
    virtuality: Option[Virtuality2],
    tyype: Coord) extends Queriable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ virtuality.toList.flatMap(_.all(func)) ++ tyype.all(func)
  }
}

sealed trait IPotentialBanner {
  def banner: FunctionBanner2
}

case class PotentialBannerFromFunctionS(
  banner: FunctionBanner2,
  function: FunctionTemplata
) extends IPotentialBanner

case class PotentialBannerFromExternFunction(
  header: FunctionHeader2
) extends IPotentialBanner {
  override def banner: FunctionBanner2 = header.toBanner
}

// A "signature" is just the things required for overload resolution, IOW function name and arg types.

// An autograph could be a super signature; a signature plus attributes like virtual and mutable.
// If we ever need it, a "schema" could be something.

// A FunctionBanner2 is everything in a FunctionHeader2 minus the return type.
// These are only made by the FunctionTemplar, to signal that it's currently being
// evaluated or it's already been evaluated.
// It's easy to see all possible function banners, but not easy to see all possible
// function headers, because functions don't have to specify their return types and
// it takes a complete templar evaluate to deduce a function's return type.

case class Signature2(fullName: FullName2[IFunctionName2]) {
  def paramTypes: List[Coord] = fullName.last.parameters
}

case class FunctionBanner2(
    originFunction: Option[FunctionA],
    fullName: FullName2[IFunctionName2],
    params: List[Parameter2]) extends Queriable2  {

  vassert(fullName.last.parameters == params.map(_.tyype))

  def toSignature: Signature2 = Signature2(fullName)
  def paramTypes: List[Coord] = params.map(_.tyype)

  def getAbstractInterface: Option[InterfaceRef2] = {
    val abstractInterfaces =
      params.collect({
        case Parameter2(_, Some(Abstract2), Coord(_, ir @ InterfaceRef2(_))) => ir
      })
    vassert(abstractInterfaces.size <= 1)
    abstractInterfaces.headOption
  }

  def getOverride: Option[(StructRef2, InterfaceRef2)] = {
    val overrides =
      params.collect({
        case Parameter2(_, Some(Override2(ir)), Coord(_, sr @ StructRef2(_))) => (sr, ir)
      })
    vassert(overrides.size <= 1)
    overrides.headOption
  }

  def getVirtualIndex: Option[Int] = {
    val indices =
      params.zipWithIndex.collect({
        case (Parameter2(_, Some(Override2(_)), _), index) => index
        case (Parameter2(_, Some(Abstract2), _), index) => index
      })
    vassert(indices.size <= 1)
    indices.headOption
  }

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ params.flatMap(_.all(func))
  }

  def unapply(arg: FunctionBanner2):
  Option[(FullName2[IFunctionName2], List[Parameter2])] =
    Some(fullName, params)

  override def toString: String = {
    // # is to signal that we override this
    "FunctionBanner2#(" + fullName + ", " + params + ")"
  }
}

case class FunctionHeader2(
    fullName: FullName2[IFunctionName2],
    isExtern: Boolean, // For optimization later
    isUserFunction: Boolean, // Whether it was written by a human. Mostly for tests right now.
    params: List[Parameter2],
    returnType: Coord,
    maybeOriginFunction: Option[FunctionA]) extends Queriable2 {

  // Make sure there's no duplicate names
  vassert(params.map(_.name).toSet.size == params.size);

  vassert(fullName.last.parameters == paramTypes)

  def getAbstractInterface: Option[InterfaceRef2] = toBanner.getAbstractInterface
  def getOverride: Option[(StructRef2, InterfaceRef2)] = toBanner.getOverride
  def getVirtualIndex: Option[Int] = toBanner.getVirtualIndex

  maybeOriginFunction.foreach(originFunction => {
    if (originFunction.identifyingRunes.size != fullName.last.templateArgs.size) {
      vfail("wtf m8")
    }
  })

  def toBanner: FunctionBanner2 = FunctionBanner2(maybeOriginFunction, fullName, params)
  def toPrototype: Prototype2 = Prototype2(fullName, returnType)
  def toSignature: Signature2 = toPrototype.toSignature

  def paramTypes: List[Coord] = params.map(_.tyype)

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ params.flatMap(_.all(func)) ++ returnType.all(func)
  }

  def unapply(arg: FunctionHeader2): Option[(FullName2[IFunctionName2], List[Parameter2], Coord)] =
    Some(fullName, params, returnType)
}

case class Prototype2(
    fullName: FullName2[IFunctionName2],
    returnType: Coord) extends Queriable2 {
  def paramTypes: List[Coord] = fullName.last.parameters
  def toSignature: Signature2 = Signature2(fullName)

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ paramTypes.flatMap(_.all(func)) ++ returnType.all(func)
  }
}

case class CodeLocation2(
  file: Int,
  offset: Int
) extends Queriable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }

  override def toString: String = file + ":" + offset
}
