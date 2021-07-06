package net.verdagon.vale.templar.templata


import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.{FullNameT, FunctionNameT, IFunctionNameT, IVarNameT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{FileCoordinate, PackageCoordinate, vassert, vassertSome, vfail, vimpl}

case class CovariantFamily(
    root: PrototypeT,
    covariantParamIndices: List[Int],
    overrides: List[PrototypeT])

trait QueriableT {
  def all[T](func: PartialFunction[QueriableT, T]): List[T];

  def allOf[T](classs: Class[T]): List[T] = {
    all({
      case x if classs.isInstance(x) => classs.cast(x)
    })
  }

  def only[T](func: PartialFunction[QueriableT, T]): T = {
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

trait VirtualityT extends QueriableT {
  def all[T](func: PartialFunction[QueriableT, T]): List[T];
}
case object AbstractT$ extends VirtualityT {
  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    List(this).collect(func)
  }
}
case class OverrideT(interface: InterfaceRefT) extends VirtualityT {
  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    List(this).collect(func) ++ interface.all(func)
  }
}

case class ParameterT(
    name: IVarNameT,
    virtuality: Option[VirtualityT],
    tyype: CoordT) extends QueriableT {
  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    List(this).collect(func) ++ virtuality.toList.flatMap(_.all(func)) ++ tyype.all(func)
  }
}

sealed trait IPotentialBanner {
  def banner: FunctionBannerT
}

case class PotentialBannerFromFunctionS(
  banner: FunctionBannerT,
  function: FunctionTemplata
) extends IPotentialBanner

case class PotentialBannerFromExternFunction(
  header: FunctionHeaderT
) extends IPotentialBanner {
  override def banner: FunctionBannerT = header.toBanner
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

case class SignatureT(fullName: FullNameT[IFunctionNameT]) {
  def paramTypes: List[CoordT] = fullName.last.parameters
}

case class FunctionBannerT(
    originFunction: Option[FunctionA],
    fullName: FullNameT[IFunctionNameT],
    params: List[ParameterT]) extends QueriableT  {

  vassert(fullName.last.parameters == params.map(_.tyype))

  def toSignature: SignatureT = SignatureT(fullName)
  def paramTypes: List[CoordT] = params.map(_.tyype)

  def getAbstractInterface: Option[InterfaceRefT] = {
    val abstractInterfaces =
      params.collect({
        case ParameterT(_, Some(AbstractT$), CoordT(_, _, ir @ InterfaceRefT(_))) => ir
      })
    vassert(abstractInterfaces.size <= 1)
    abstractInterfaces.headOption
  }

  def getOverride: Option[(StructRefT, InterfaceRefT)] = {
    val overrides =
      params.collect({
        case ParameterT(_, Some(OverrideT(ir)), CoordT(_, _, sr @ StructRefT(_))) => (sr, ir)
      })
    vassert(overrides.size <= 1)
    overrides.headOption
  }

  def getVirtualIndex: Option[Int] = {
    val indices =
      params.zipWithIndex.collect({
        case (ParameterT(_, Some(OverrideT(_)), _), index) => index
        case (ParameterT(_, Some(AbstractT$), _), index) => index
      })
    vassert(indices.size <= 1)
    indices.headOption
  }

  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    List(this).collect(func) ++ params.flatMap(_.all(func))
  }

  def unapply(arg: FunctionBannerT):
  Option[(FullNameT[IFunctionNameT], List[ParameterT])] =
    Some(fullName, params)

  override def toString: String = {
    // # is to signal that we override this
    "FunctionBanner2#(" + fullName + ", " + params + ")"
  }
}

sealed trait IFunctionAttribute2
sealed trait ICitizenAttribute2
case class Extern2(packageCoord: PackageCoordinate) extends IFunctionAttribute2 with ICitizenAttribute2 // For optimization later
case class Export2(packageCoord: PackageCoordinate) extends IFunctionAttribute2 with ICitizenAttribute2
case object Pure2 extends IFunctionAttribute2 with ICitizenAttribute2
case object UserFunction2 extends IFunctionAttribute2 // Whether it was written by a human. Mostly for tests right now.

case class FunctionHeaderT(
    fullName: FullNameT[IFunctionNameT],
    attributes: List[IFunctionAttribute2],
    params: List[ParameterT],
    returnType: CoordT,
    maybeOriginFunction: Option[FunctionA]) extends QueriableT {

  // Make sure there's no duplicate names
  vassert(params.map(_.name).toSet.size == params.size);

  vassert(fullName.last.parameters == paramTypes)

  def isExtern = attributes.exists({ case Extern2(_) => true case _ => false })
  def isExport = attributes.exists({ case Export2(_) => true case _ => false })
  def isUserFunction = attributes.contains(UserFunction2)
  def getAbstractInterface: Option[InterfaceRefT] = toBanner.getAbstractInterface
  def getOverride: Option[(StructRefT, InterfaceRefT)] = toBanner.getOverride
  def getVirtualIndex: Option[Int] = toBanner.getVirtualIndex

  maybeOriginFunction.foreach(originFunction => {
    if (originFunction.identifyingRunes.size != fullName.last.templateArgs.size) {
      vfail("wtf m8")
    }
  })

  def toBanner: FunctionBannerT = FunctionBannerT(maybeOriginFunction, fullName, params)
  def toPrototype: PrototypeT = PrototypeT(fullName, returnType)
  def toSignature: SignatureT = toPrototype.toSignature

  def paramTypes: List[CoordT] = params.map(_.tyype)

  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    List(this).collect(func) ++ params.flatMap(_.all(func)) ++ returnType.all(func)
  }

  def unapply(arg: FunctionHeaderT): Option[(FullNameT[IFunctionNameT], List[ParameterT], CoordT)] =
    Some(fullName, params, returnType)
}

case class PrototypeT(
    fullName: FullNameT[IFunctionNameT],
    returnType: CoordT) extends QueriableT {
  def paramTypes: List[CoordT] = fullName.last.parameters
  def toSignature: SignatureT = SignatureT(fullName)

  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    List(this).collect(func) ++ paramTypes.flatMap(_.all(func)) ++ returnType.all(func)
  }
}

case class CodeLocationT(
  file: FileCoordinate,
  offset: Int
) extends QueriableT {
  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    List(this).collect(func)
  }

  override def toString: String = file + ":" + offset
}

object CodeLocationT {
  // Keep in sync with CodeLocationS
  val zero = CodeLocationT.internal(-1)
  def internal(internalNum: Int): CodeLocationT = {
    vassert(internalNum < 0)
    CodeLocationT(FileCoordinate("", List.empty, "internal"), internalNum)
  }
}