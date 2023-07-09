package dev.vale.instantiating.ast

import dev.vale._
import dev.vale.postparsing._

import scala.collection.immutable._

// We won't always have a return type for a banner... it might have not specified its return
// type, so we're currently evaluating the entire body for it right now.
// If we ever find ourselves wanting the return type for a banner, we need to:
// - Check if it's in the returnTypesByBanner map. If so, good.
// - If not, then check if the banner is in declaredBanners. If so, then we're currently in
//   the process of evaluating the entire body. In this case, throw an error because we're
//   about to infinite loop. Hopefully this is a user error, they need to specify a return
//   type to avoid a cyclical definition.
// - If not in declared banners, then tell FunctionCompiler to start evaluating it.

// case class ImplI(
//   // These are ICitizenTI and InterfaceIT which likely have placeholder templatas in them.
//   // We do this because a struct might implement an interface in multiple ways, see SCIIMT.
//   // We have the template names as well as the placeholders for better searching, see MLUIBTN.
//
//   templata: ImplDefinitionTemplataI[cI],
//
//   instantiatedId: IdI[cI, IImplNameI[cI]],
//   templateId: IdI[cI, IImplTemplateNameI[cI]],
//
//   subCitizenTemplateId: IdI[cI, ICitizenTemplateNameI[cI]],
//   subCitizen: ICitizenIT[cI],
//
//   superInterface: InterfaceIT[cI],
//   superInterfaceTemplateId: IdI[cI, IInterfaceTemplateNameI[cI]],
//
//   // This is similar to FunctionT.runeToFuncBound
//   runeToFuncBound: Map[IRuneS, IdI[cI, FunctionBoundNameI[cI]]],
//   runeToImplBound: Map[IRuneS, IdI[cI, ImplBoundNameI[cI]]],
//
//   runeIndexToIndependence: Vector[Boolean],
//
//   // A function will inherit bounds from its parameters' kinds. Same with an impl from its sub
//   // citizen, and a case block from its receiving kind.
//   // We'll need to remember those, so the instantiator can do its thing.
//   // See TIBANFC for more.
//   reachableBoundsFromSubCitizen: Vector[PrototypeI[cI]]
//
// //  // Starting from a placeholdered super interface, this is the interface that would result.
// //  // We get this by solving the impl, given a placeholdered sub citizen.
// //  subCitizenFromPlaceholderedParentInterface: ICitizenIT,
// )

case class KindExportI(
  range: RangeS,
  tyype: KindIT[cI],
  // Good for knowing the package of this export for later prefixing the exportedName, also good
  // for getting its region.
  id: IdI[cI, ExportNameI[cI]],
  exportedName: StrI
)  {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

}

case class FunctionExportI(
  range: RangeS,
  prototype: PrototypeI[cI],
  exportId: IdI[cI, ExportNameI[cI]],
  exportedName: StrI
)  {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}

//case class KindExternI(
//  tyype: KindIT,
//  packageCoordinate: PackageCoordinate,
//  externName: StrI
//)  {
//  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//
//}

case class FunctionExternI(
//  range: RangeS,
  prototype: PrototypeI[cI],
//  packageCoordinate: PackageCoordinate,
  externName: StrI
)  {
  vpass()

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

}

case class InterfaceEdgeBlueprintI(
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  interface: IdI[cI, IInterfaceNameI[cI]],
  superFamilyRootHeaders: Vector[(PrototypeI[cI], Int)]) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

case class EdgeI(
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  edgeId: IdI[cI, IImplNameI[cI]],
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  subCitizen: ICitizenIT[cI],
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  superInterface: IdI[cI, IInterfaceNameI[cI]],
  // This is similar to FunctionT.runeToFuncBound
  runeToFuncBound: Map[IRuneS, IdI[cI, FunctionBoundNameI[cI]]],
  runeToImplBound: Map[IRuneS, IdI[cI, ImplBoundNameI[cI]]],
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  abstractFuncToOverrideFunc: Map[IdI[cI, IFunctionNameI[cI]], PrototypeI[cI]]
) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def equals(obj: Any): Boolean = {
    obj match {
      case EdgeI(thatEdgeFullName, thatStruct, thatInterface, _, _, _) => {
        val isSame = subCitizen == thatStruct && superInterface == thatInterface
        if (isSame) {
          vassert(edgeId == thatEdgeFullName)
        }
        isSame
      }
    }
  }
}

case class FunctionDefinitionI(
  header: FunctionHeaderI,
  runeToFuncBound: Map[IRuneS, IdI[cI, FunctionBoundNameI[cI]]],
  runeToImplBound: Map[IRuneS, IdI[cI, ImplBoundNameI[cI]]],
  body: ReferenceExpressionIE)  {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  // We always end a function with a ret, whose result is a Never.
  vassert(body.result.kind == NeverIT[cI](false))

  def isPure: Boolean = header.isPure
}

object getFunctionLastName {
  def unapply(f: FunctionDefinitionI): Option[IFunctionNameI[cI]] = Some(f.header.id.localName)
}

// A unique location in a function. Environment is in the name so it spells LIFE!
case class LocationInFunctionEnvironmentI(path: Vector[Int]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  def +(subLocation: Int): LocationInFunctionEnvironmentI = {
    LocationInFunctionEnvironmentI(path :+ subLocation)
  }

  override def toString: String = path.mkString(".")
}

case class AbstractI()

case class ParameterI(
  name: IVarNameI[cI],
  virtuality: Option[AbstractI],
  preChecked: Boolean,
  tyype: CoordI[cI]) {

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  // Use same instead, see EHCFBD for why we dont like equals.
  override def equals(obj: Any): Boolean = vcurious();

  def same(that: ParameterI): Boolean = {
    name == that.name &&
      virtuality == that.virtuality &&
      tyype == that.tyype
  }
}

// A "signature" is just the things required for overload resolution, IOW function name and arg types.

// An autograph could be a super signature; a signature plus attributes like virtual and mutable.
// If we ever need it, a "schema" could be something.

// A FunctionBanner2 is everything in a FunctionHeader2 minus the return type.
// These are only made by the FunctionCompiler, to signal that it's currently being
// evaluated or it's already been evaluated.
// It's easy to see all possible function banners, but not easy to see all possible
// function headers, because functions don't have to specify their return types and
// it takes a complete typingpass evaluate to deduce a function's return type.

case class SignatureI[+R <: IRegionsModeI](id: IdI[R, IFunctionNameI[R]]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def paramTypes: Vector[CoordI[R]] = id.localName.parameters
}

sealed trait IFunctionAttributeI
sealed trait ICitizenAttributeI
case class ExternI(packageCoord: PackageCoordinate) extends IFunctionAttributeI with ICitizenAttributeI { // For optimization later
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
// There's no Export2 here, we use separate KindExport and FunctionExport constructs.
//case class Export2(packageCoord: PackageCoordinate) extends IFunctionAttribute2 with ICitizenAttribute2
case object PureI extends IFunctionAttributeI
case object SealedI extends ICitizenAttributeI
case object UserFunctionI extends IFunctionAttributeI // Whether it was written by a human. Mostly for tests right now.

case class RegionI(
  name: IRegionNameI[cI],
  mutable: Boolean)

case class FunctionHeaderI(
  // This one little name field can illuminate much of how the compiler works, see UINIT.
  id: IdI[cI, IFunctionNameI[cI]],
  attributes: Vector[IFunctionAttributeI],
//  regions: Vector[cIegionI],
  params: Vector[ParameterI],
  returnType: CoordI[cI]) {

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

//  val perspectiveRegion =
//    id.localName.templateArgs.last match {
//      case PlaceholderTemplata(IdI(packageCoord, initSteps, r @ RegionPlaceholderNameI(_, _, _, _, _)), RegionTemplataType()) => {
//        IdI(packageCoord, initSteps, r)
//      }
//      case _ => vwat()
//    }
//  if (attributes.contains(PureI)) {
//    // Instantiator relies on this assumption so that it knows when certain things are pure.
//    vassert(perspectiveRegion.localName.originalMaybeNearestPureLocation == Some(LocationInDenizen(Vector())))
//  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case FunctionHeaderI(thatName, _, _, _) => {
        id == thatName
      }
      case _ => false
    }
  }

  // Make sure there's no duplicate names
  vassert(params.map(_.name).toSet.size == params.size);

  vassert(id.localName.parameters == paramTypes)

  def isExtern = attributes.exists({ case ExternI(_) => true case _ => false })
  //  def isExport = attributes.exists({ case Export2(_) => true case _ => false })
  def isUserFunction = attributes.contains(UserFunctionI)
//  def getAbstractInterface: Option[InterfaceIT] = toBanner.getAbstractInterface
////  def getOverride: Option[(StructIT, InterfaceIT)] = toBanner.getOverride
//  def getVirtualIndex: Option[Int] = toBanner.getVirtualIndex

//  def toSignature(interner: Interner, keywords: Keywords): SignatureI = {
//    val newLastStep = templateName.last.makeFunctionName(interner, keywords, templateArgs, params)
//    val fullName = FullNameI(templateName.packageCoord, name.initSteps, newLastStep)
//
//    SignatureI(fullName)
//
//  }
//  def paramTypes: Vector[CoordI[cI]] = params.map(_.tyype)

  def getAbstractInterface: Option[InterfaceIT[cI]] = {
    val abstractInterfaces =
      params.collect({
        case ParameterI(_, Some(AbstractI()), _, CoordI(_, ir @ InterfaceIT(_))) => ir
      })
    vassert(abstractInterfaces.size <= 1)
    abstractInterfaces.headOption
  }

  def getVirtualIndex: Option[Int] = {
    val indices =
      params.zipWithIndex.collect({
        case (ParameterI(_, Some(AbstractI()), _, _), index) => index
      })
    vassert(indices.size <= 1)
    indices.headOption
  }

//  maybeOriginFunction.foreach(originFunction => {
//    if (originFunction.genericParameters.size != fullName.last.templateArgs.size) {
//      vfail("wtf m8")
//    }
//  })

  def toPrototype: PrototypeI[cI] = {
//    val substituter = TemplataCompiler.getPlaceholderSubstituter(interner, fullName, templateArgs)
//    val paramTypes = params.map(_.tyype).map(substituter.substituteForCoord)
//    val newLastStep = fullName.last.makeFunctionName(interner, keywords, templateArgs, paramTypes)
//    val newName = FullNameI(fullName.packageCoord, fullName.initSteps, newLastStep)
    PrototypeI(id, returnType)
  }
  def toSignature: SignatureI[cI] = {
    toPrototype.toSignature
  }

  def paramTypes: Vector[CoordI[cI]] = id.localName.parameters

  def unapply(arg: FunctionHeaderI): Option[(IdI[cI, IFunctionNameI[cI]], Vector[ParameterI], CoordI[cI])] = {
    Some(id, params, returnType)
  }

  def isPure: Boolean = {
    attributes.collectFirst({ case PureI => }).nonEmpty
  }
}

case class PrototypeI[+R <: IRegionsModeI](
    id: IdI[R, IFunctionNameI[R]],
    returnType: CoordI[R]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def paramTypes: Vector[CoordI[R]] = id.localName.parameters
  def toSignature: SignatureI[R] = SignatureI[R](id)
}

sealed trait IVariableI  {
  def name: IVarNameI[cI]
  def variability: VariabilityI
  def collapsedCoord: CoordI[cI]
}
sealed trait ILocalVariableI extends IVariableI {
  def name: IVarNameI[cI]
  def collapsedCoord: CoordI[cI]
}
// Why the difference between reference and addressible:
// If we mutate/move a variable from inside a closure, we need to put
// the local's address into the struct. But, if the closures don't
// mutate/move, then we could just put a regular reference in the struct.
// Lucky for us, the parser figured out if any of our child closures did
// any mutates/moves/borrows.
case class AddressibleLocalVariableI(
  name: IVarNameI[cI],
  variability: VariabilityI,
  collapsedCoord: CoordI[cI]
) extends ILocalVariableI {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
}
case class ReferenceLocalVariableI(
  name: IVarNameI[cI],
  variability: VariabilityI,
  collapsedCoord: CoordI[cI]
) extends ILocalVariableI {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
  vpass()
}
case class AddressibleClosureVariableI(
  name: IVarNameI[cI],
  closuredVarsStructType: StructIT[cI],
  variability: VariabilityI,
  collapsedCoord: CoordI[cI]
) extends IVariableI {
  vpass()
}
case class ReferenceClosureVariableI(
  name: IVarNameI[cI],
  closuredVarsStructType: StructIT[cI],
  variability: VariabilityI,
  collapsedCoord: CoordI[cI]
) extends IVariableI {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();

}
