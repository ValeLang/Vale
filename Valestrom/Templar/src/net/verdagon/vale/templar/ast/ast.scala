package net.verdagon.vale.templar.ast

import net.verdagon.vale._
import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, FullNameT, IFunctionNameT, IVarNameT, PackageTopLevelNameT}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._

import scala.collection.immutable._

// We won't always have a return type for a banner... it might have not specified its return
// type, so we're currently evaluating the entire body for it right now.
// If we ever find ourselves wanting the return type for a banner, we need to:
// - Check if it's in the returnTypesByBanner map. If so, good.
// - If not, then check if the banner is in declaredBanners. If so, then we're currently in
//   the process of evaluating the entire body. In this case, throw an error because we're
//   about to infinite loop. Hopefully this is a user error, they need to specify a return
//   type to avoid a cyclical definition.
// - If not in declared banners, then tell FunctionTemplar to start evaluating it.

case class ImplT(
  struct: StructTT,
  interface: InterfaceTT
)  {
  override def hashCode(): Int = vcurious()

}

case class KindExportT(
  range: RangeS,
  tyype: KindT,
  packageCoordinate: PackageCoordinate,
  exportedName: String
)  {
  override def hashCode(): Int = vcurious()

}

case class FunctionExportT(
  range: RangeS,
  prototype: PrototypeT,
  packageCoordinate: PackageCoordinate,
  exportedName: String
)  {
  override def hashCode(): Int = vcurious()

}

case class KindExternT(
  tyype: KindT,
  packageCoordinate: PackageCoordinate,
  externName: String
)  {
  override def hashCode(): Int = vcurious()

}

case class FunctionExternT(
  range: RangeS,
  prototype: PrototypeT,
  packageCoordinate: PackageCoordinate,
  externName: String
)  {
  override def hashCode(): Int = vcurious()

}

case class InterfaceEdgeBlueprint(
  interface: InterfaceTT,
  superFamilyRootBanners: Vector[FunctionBannerT]) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class EdgeT(
  struct: StructTT,
  interface: InterfaceTT,
  methods: Vector[PrototypeT]) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

object ProgramT {
  val topLevelName = FullNameT(PackageCoordinate.BUILTIN, Vector.empty, PackageTopLevelNameT())
  val tupleHumanName = "Tup"
//  val emptyTupleTT =
//    StructTT(FullNameT(PackageCoordinate.BUILTIN, Vector(), CitizenNameT(CitizenTemplateNameT(tupleHumanName), Vector(CoordListTemplata(Vector())))))

  val intType = CoordT(ShareT, ReadonlyT, IntT.i32)
  val boolType = CoordT(ShareT, ReadonlyT, BoolT())
}

case class FunctionT(
  header: FunctionHeaderT,
  body: ReferenceExpressionTE)  {
  override def hashCode(): Int = vcurious()

  // We always end a function with a return, whose result is a Never.
  vassert(body.result.kind == NeverT(false))
}

object getFunctionLastName {
  def unapply(f: FunctionT): Option[IFunctionNameT] = Some(f.header.fullName.last)
}

// A unique location in a function. Environment is in the name so it spells LIFE!
case class LocationInFunctionEnvironment(path: Vector[Int]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  def +(subLocation: Int): LocationInFunctionEnvironment = {
    LocationInFunctionEnvironment(path :+ subLocation)
  }

  override def toString: String = path.mkString(".")
}

trait VirtualityT
case object AbstractT extends VirtualityT {

}
case class OverrideT(interface: InterfaceTT) extends VirtualityT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}

case class ParameterT(
  name: IVarNameT,
  virtuality: Option[VirtualityT],
  tyype: CoordT)  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}

sealed trait ICalleeCandidate

case class FunctionCalleeCandidate(ft: FunctionTemplata) extends ICalleeCandidate {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class HeaderCalleeCandidate(header: FunctionHeaderT) extends ICalleeCandidate {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}

sealed trait IValidCalleeCandidate {
  def banner: FunctionBannerT
}
case class ValidHeaderCalleeCandidate(
  header: FunctionHeaderT
) extends IValidCalleeCandidate {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def banner: FunctionBannerT = header.toBanner
}
case class ValidCalleeCandidate(
  banner: FunctionBannerT,
  function: FunctionTemplata
) extends IValidCalleeCandidate {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
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
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def paramTypes: Vector[CoordT] = fullName.last.parameters
}

case class FunctionBannerT(
  originFunction: Option[FunctionA],
  fullName: FullNameT[IFunctionNameT],
  params: Vector[ParameterT])   {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  vassert(fullName.last.parameters == params.map(_.tyype))

  def toSignature: SignatureT = SignatureT(fullName)
  def paramTypes: Vector[CoordT] = params.map(_.tyype)

  def getAbstractInterface: Option[InterfaceTT] = {
    val abstractInterfaces =
      params.collect({
        case ParameterT(_, Some(AbstractT), CoordT(_, _, ir @ InterfaceTT(_))) => ir
      })
    vassert(abstractInterfaces.size <= 1)
    abstractInterfaces.headOption
  }

  def getOverride: Option[(StructTT, InterfaceTT)] = {
    val overrides =
      params.collect({
        case ParameterT(_, Some(OverrideT(ir)), CoordT(_, _, sr @ StructTT(_))) => (sr, ir)
      })
    vassert(overrides.size <= 1)
    overrides.headOption
  }

  def getVirtualIndex: Option[Int] = {
    val indices =
      params.zipWithIndex.collect({
        case (ParameterT(_, Some(OverrideT(_)), _), index) => index
        case (ParameterT(_, Some(AbstractT), _), index) => index
      })
    vassert(indices.size <= 1)
    indices.headOption
  }



  def unapply(arg: FunctionBannerT):
  Option[(FullNameT[IFunctionNameT], Vector[ParameterT])] =
    Some(fullName, params)

  override def toString: String = {
    // # is to signal that we override this
    "FunctionBanner2#(" + fullName + ", " + params + ")"
  }
}

sealed trait IFunctionAttributeT
sealed trait ICitizenAttributeT
case class ExternT(packageCoord: PackageCoordinate) extends IFunctionAttributeT with ICitizenAttributeT { // For optimization later
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
// There's no Export2 here, we use separate KindExport and FunctionExport constructs.
//case class Export2(packageCoord: PackageCoordinate) extends IFunctionAttribute2 with ICitizenAttribute2
case object PureT extends IFunctionAttributeT
case object SealedT extends ICitizenAttributeT
case object UserFunctionT extends IFunctionAttributeT // Whether it was written by a human. Mostly for tests right now.

case class FunctionHeaderT(
  fullName: FullNameT[IFunctionNameT],
  attributes: Vector[IFunctionAttributeT],
  params: Vector[ParameterT],
  returnType: CoordT,
  maybeOriginFunction: Option[FunctionA])  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  // Make sure there's no duplicate names
  vassert(params.map(_.name).toSet.size == params.size);

  vassert(fullName.last.parameters == paramTypes)

  def isExtern = attributes.exists({ case ExternT(_) => true case _ => false })
  //  def isExport = attributes.exists({ case Export2(_) => true case _ => false })
  def isUserFunction = attributes.contains(UserFunctionT)
  def getAbstractInterface: Option[InterfaceTT] = toBanner.getAbstractInterface
  def getOverride: Option[(StructTT, InterfaceTT)] = toBanner.getOverride
  def getVirtualIndex: Option[Int] = toBanner.getVirtualIndex

  maybeOriginFunction.foreach(originFunction => {
    if (originFunction.identifyingRunes.size != fullName.last.templateArgs.size) {
      vfail("wtf m8")
    }
  })

  def toBanner: FunctionBannerT = FunctionBannerT(maybeOriginFunction, fullName, params)
  def toPrototype: PrototypeT = PrototypeT(fullName, returnType)
  def toSignature: SignatureT = toPrototype.toSignature

  def paramTypes: Vector[CoordT] = params.map(_.tyype)



  def unapply(arg: FunctionHeaderT): Option[(FullNameT[IFunctionNameT], Vector[ParameterT], CoordT)] =
    Some(fullName, params, returnType)
}

case class PrototypeT(
  fullName: FullNameT[IFunctionNameT],
  returnType: CoordT)  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def paramTypes: Vector[CoordT] = fullName.last.parameters
  def toSignature: SignatureT = SignatureT(fullName)


}
