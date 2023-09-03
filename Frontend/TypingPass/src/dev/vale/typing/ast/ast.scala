package dev.vale.typing.ast

import dev.vale.highertyping.FunctionA
import dev.vale.typing.names._
import dev.vale.typing.templata.FunctionTemplataT
import dev.vale.{PackageCoordinate, RangeS, vassert, vcurious, vfail}
import dev.vale.typing.types._
import dev.vale._
import dev.vale.postparsing._
import dev.vale.typing._
import dev.vale.typing.env.IInDenizenEnvironmentT
import dev.vale.typing.templata._
import dev.vale.typing.types._

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

case class ImplT(
  // These are ICitizenTT and InterfaceTT which likely have placeholder templatas in them.
  // We do this because a struct might implement an interface in multiple ways, see SCIIMT.
  // We have the template names as well as the placeholders for better searching, see MLUIBTN.

  templata: ImplDefinitionTemplataT,

  instantiatedId: IdT[IImplNameT],
  templateId: IdT[IImplTemplateNameT],

  subCitizenTemplateId: IdT[ICitizenTemplateNameT],
  subCitizen: ICitizenTT,

  superInterface: InterfaceTT,
  superInterfaceTemplateId: IdT[IInterfaceTemplateNameT],

  // This is similar to FunctionT.instantiationBoundParams.
  // We'll line up anything in here with the instantiation bound args to form a nice
  // map the instantiator can use. See IBAMIBP.
  instantiationBoundParams: InstantiationBoundArgumentsT[FunctionBoundNameT, ImplBoundNameT],

  runeIndexToIndependence: Vector[Boolean],
) {
  vpass()
}

case class KindExportT(
  range: RangeS,
  tyype: KindT,
  // Good for knowing the package of this export for later prefixing the exportedName, also good
  // for getting its region.
  id: IdT[ExportNameT],
  exportedName: StrI
)  {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

}

case class FunctionExportT(
  range: RangeS,
  prototype: PrototypeT[IFunctionNameT],
  exportId: IdT[ExportNameT],
  exportedName: StrI
)  {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}

case class KindExternT(
  tyype: KindT,
  packageCoordinate: PackageCoordinate,
  externName: StrI
)  {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

}

case class FunctionExternT(
  range: RangeS,
  externPlaceholderedId: IdT[ExternNameT],
  prototype: PrototypeT[IFunctionNameT],
  externName: StrI
)  {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

}

case class InterfaceEdgeBlueprintT(
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  interface: IdT[IInterfaceNameT],
  superFamilyRootHeaders: Vector[(PrototypeT[IFunctionNameT], Int)]) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

case class OverrideT(
  // it seems right here we'll need some sort of mapping of abstract func placeholder to the
  // override impl case placeholders, and perhaps also the existence of the <T>s for the case?
  // we need to instantiate the override, so its going to need some values for it... i guess
  // its from the impl, so the impl has it i think. so maybe a map from the impl rune to it



  // This is the name of the conceptual function called by the abstract function.
  // It has enough information to do simple dispatches, but not all cases, it can't handle
  // the Milano case, see OMCNAGP.
  // This will have some placeholders from the abstract function; this is the abstract function
  // calling the dispatcher.
  // This is like:
  //   abstract func send<T>(self &IObserver<T>, event T) void
  // calling:
  //   func send<int>(self &IObserver<int>, event int) void
  // or a more complex case:
  //   func send<Opt<int>>(self &IObserver<Opt<int>>, event Opt<int>) void
  // as you can see there may be some interesting templatas in there like that Opt<int>, they
  // might not be simple placeholders.
  dispatcherCallId: IdT[OverrideDispatcherNameT],

  implPlaceholderToDispatcherPlaceholder: Vector[(IdT[IPlaceholderNameT], ITemplataT[ITemplataType])],
  implPlaceholderToCasePlaceholder: Vector[(IdT[IPlaceholderNameT], ITemplataT[ITemplataType])],

  // These are the prototypes we'll pull from the impl's own bounds, and these CaseFunctionFromImplNameT names contain
  // the rune that the impl internally refers to them as.
  dispatcherAndCasePlaceholderedImplReachablePrototypes: Map[IRuneS, Map[IRuneS, PrototypeT[FunctionBoundNameT]]],

  // This is the name of the conceptual case that's calling the override prototype. It'll have
  // template args inherited from the dispatcher function and template args inherited from the
  // translated from the impl into "case placeholders". After typing pass these will be placeholders, and after
  // instantiator these will be actual real templatas.
  caseId: IdT[OverrideDispatcherCaseNameT],

  // The override function we're calling.
  // Conceptually, this is being called from the case's environment. It might even have some complex stuff
  // in the template args.
  overridePrototype: PrototypeT[IFunctionNameT],

  // Any FunctionT has a runeToFunctionBound, which is a map of the function's rune to its required
  // bounds. This is the one for our conceptual dispatcher function.
  dispatcherInstantiationBoundParams: InstantiationBoundArgumentsT[FunctionBoundNameT, ImplBoundNameT],
)

case class EdgeT(
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  edgeId: IdT[IImplNameT],
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  subCitizen: ICitizenTT,
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  superInterface: IdT[IInterfaceNameT],
  // This is similar to FunctionT.runeToFuncBound
  instantiationBoundParams: InstantiationBoundArgumentsT[FunctionBoundNameT, ImplBoundNameT],
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  abstractFuncToOverrideFunc: Map[IdT[IFunctionNameT], OverrideT]
) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def equals(obj: Any): Boolean = {
    obj match {
      case EdgeT(thatEdgeId, thatStruct, thatInterface, _, _) => {
        val isSame = subCitizen == thatStruct && superInterface == thatInterface
        if (isSame) {
          vassert(edgeId == thatEdgeId)
        }
        isSame
      }
    }
  }
}

case class FunctionDefinitionT(
  header: FunctionHeaderT,
  instantiationBoundParams: InstantiationBoundArgumentsT[FunctionBoundNameT, ImplBoundNameT],
  body: ReferenceExpressionTE)  {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  // We always end a function with a ret, whose result is a Never.
  vassert(body.result.kind == NeverT(false))

  def isPure: Boolean = header.isPure
}

object getFunctionLastName {
  def unapply(f: FunctionDefinitionT): Option[IFunctionNameT] = Some(f.header.id.localName)
}

// A unique location in a function. Environment is in the name so it spells LIFE!
case class LocationInFunctionEnvironmentT(path: Vector[Int]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  def +(subLocation: Int): LocationInFunctionEnvironmentT = {
    LocationInFunctionEnvironmentT(path :+ subLocation)
  }

  override def toString: String = path.mkString(".")
}

case class AbstractT()

case class ParameterT(
  name: IVarNameT,
  virtuality: Option[AbstractT],
  preChecked: Boolean,
  tyype: CoordT)  {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  // Use same instead, see EHCFBD for why we dont like equals.
  override def equals(obj: Any): Boolean = vcurious();

  def same(that: ParameterT): Boolean = {
    name == that.name &&
      virtuality == that.virtuality &&
      tyype == that.tyype
  }
}

sealed trait ICalleeCandidate

case class FunctionCalleeCandidate(ft: FunctionTemplataT) extends ICalleeCandidate {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class HeaderCalleeCandidate(header: FunctionHeaderT) extends ICalleeCandidate {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class PrototypeTemplataCalleeCandidate(
  // We don't want a range because we want to merge all sorts of different bound functions, see MFBFDP.
  //   range: RangeS,
  prototypeT: PrototypeT[IFunctionNameT]) extends ICalleeCandidate {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}

//sealed trait IValidCalleeCandidate {
//  def range: Option[RangeS]
//  def paramTypes: Vector[CoordT]
//}
//case class ValidHeaderCalleeCandidate(
//  header: FunctionHeaderT
//) extends IValidCalleeCandidate {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
//
//  override def range: Option[RangeS] = header.maybeOriginFunctionTemplata.map(_.function.range)
//  override def paramTypes: Vector[CoordT] = header.paramTypes.toVector
//}
//case class ValidPrototypeTemplataCalleeCandidate(
//  prototype: PrototypeTemplataT[IFunctionNameT]
//) extends IValidCalleeCandidate {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  override def equals(obj: Any): Boolean = {
//    val that = obj.asInstanceOf[ValidPrototypeTemplataCalleeCandidate]
//    if (that == null) {
//      return false
//    }
//    prototype == that.prototype
//  }
//
//  override def range: Option[RangeS] = None
//  override def paramTypes: Vector[CoordT] = prototype.prototype.id.localName.parameters.toVector
//}
////case class ValidCalleeCandidate(
////  banner: FunctionHeaderT,
////  templateArgs: Vector[ITemplataT[ITemplataType]],
////  function: FunctionTemplataT
////) extends IValidCalleeCandidate {
////  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious();
////
////  override def range: Option[RangeS] = banner.maybeOriginFunctionTemplata.map(_.function.range)
////  override def paramTypes: Vector[CoordT] = banner.paramTypes.toVector
////}

// A "signature" is just the things required for overload resolution, IOW function name and arg types.

// An autograph could be a super signature; a signature plus attributes like virtual and mutable.
// If we ever need it, a "schema" could be something.

// A FunctionBanner2 is everything in a FunctionHeader2 minus the return type.
// These are only made by the FunctionCompiler, to signal that it's currently being
// evaluated or it's already been evaluated.
// It's easy to see all possible function banners, but not easy to see all possible
// function headers, because functions don't have to specify their return types and
// it takes a complete typingpass evaluate to deduce a function's return type.

case class SignatureT(id: IdT[IFunctionNameT]) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def paramTypes: Vector[CoordT] = id.localName.parameters
}

case class FunctionBannerT(
  originFunctionTemplata: Option[FunctionTemplataT],
  name: IdT[IFunctionNameT])   {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  // Use same instead, see EHCFBD for why we dont like equals.
  override def equals(obj: Any): Boolean = vcurious();

  def same(that: FunctionBannerT): Boolean = {
    originFunctionTemplata.map(_.function) == that.originFunctionTemplata.map(_.function) && name == that.name
  }



//  def unapply(arg: FunctionBannerT):
//  Option[(FullNameT[IFunctionNameT], Vector[ParameterT])] =
//    Some(templateName, params)

  override def toString: String = {
    // # is to signal that we override this
//    "FunctionBanner2#(" + templateName + ")"
//        "FunctionBanner2#(" + templateName + ", " + params + ")"
    "FunctionBanner2#(" + name + ")"
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
case object AdditiveT extends IFunctionAttributeT
case object SealedT extends ICitizenAttributeT
case object UserFunctionT extends IFunctionAttributeT // Whether it was written by a human. Mostly for tests right now.

case class FunctionHeaderT(
  // This one little name field can illuminate much of how the compiler works, see UINIT.
  id: IdT[IFunctionNameT],
  attributes: Vector[IFunctionAttributeT],
//  regions: Vector[RegionT],
  params: Vector[ParameterT],
  returnType: CoordT,
  maybeOriginFunctionTemplata: Option[FunctionTemplataT]) {

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

//  val perspectiveRegion =
//    id.localName.templateArgs.last match {
//      case PlaceholderTemplata(IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _, _)), RegionTemplataType()) => {
//        IdT(packageCoord, initSteps, r)
//      }
//      case _ => vwat()
//    }
//  if (attributes.contains(PureT)) {
//    // Instantiator relies on this assumption so that it knows when certain things are pure.
//    vassert(perspectiveRegion.localName.originalMaybeNearestPureLocation == Some(LocationInDenizen(Vector())))
//  }

  vassert({
    maybeOriginFunctionTemplata match {
      case None =>
      case Some(originFunctionTemplata) => {
        val templateName = TemplataCompiler.getFunctionTemplate(id)
        val placeholdersInThisFunctionName =
          Collector.all(id, {
            case KindPlaceholderT(name) => name
            case PlaceholderTemplataT(name, _) => name
          })
        // Filter out any placeholders that came from the parent, in case this is a lambda function.
        val selfPlaceholdersInThisFunctionName =
          placeholdersInThisFunctionName.filter({ case IdT(packageCoord, initSteps, last) =>
            val parentName = IdT(packageCoord, initSteps.init, initSteps.last)
            // Not sure which one it is, this should catch both.
            parentName == id || parentName == templateName
          })

        if (originFunctionTemplata.function.isLambda()) {
          // make sure there are no placeholders
          vassert(selfPlaceholdersInThisFunctionName.isEmpty)
        } else {
          if (originFunctionTemplata.function.genericParameters.isEmpty) {
            // make sure there are no placeholders
            vassert(selfPlaceholdersInThisFunctionName.isEmpty)
          } else {
            // Make sure all the placeholders in the generic parameters exist as template args in
            // the original function definition.
            selfPlaceholdersInThisFunctionName.foreach({
              case placeholderName @ IdT(_, _, NonKindNonRegionPlaceholderNameT(index, rune)) => {
                id.localName.templateArgs(index) match {
                  case PlaceholderTemplataT(placeholderNameAtIndex, _) => {
                    vassert(placeholderName == placeholderNameAtIndex)
                  }
                  case other => {
                    println("other: " + other)
                    println("placeholderName: " + placeholderName)
                    vfail(other)
                  }
                }
              }
              case placeholderName @ IdT(_, _, RegionPlaceholderNameT(index, rune, _, _)) => {
                id.localName.templateArgs(index) match {
                  case PlaceholderTemplataT(placeholderNameAtIndex, _) => {
                    vassert(placeholderName == placeholderNameAtIndex)
                  }
                  case other => {
                    println("other: " + other)
                    println("placeholderName: " + placeholderName)
                    vfail(other)
                  }
                }
              }
              case placeholderName@IdT(_, _, CoordGenericParamRegionPlaceholderNameT(index, rune, _, _)) => {
                id.localName.templateArgs(index) match {
                  case CoordTemplataT(CoordT(_, RegionT(PlaceholderTemplataT(regionPlaceholderId, _)), KindPlaceholderT(kindPlaceholderId))) => {
                    vassert(placeholderName == regionPlaceholderId)
                  }
                  case other => {
                    println("other: " + other)
                    println("placeholderName: " + placeholderName)
                    vfail(other)
                  }
                }
              }
              case placeholderName @ IdT(_, _, KindPlaceholderNameT(KindPlaceholderTemplateNameT(index, rune))) => {
                id.localName.templateArgs(index) match {
                  case KindTemplataT(KindPlaceholderT(placeholderNameAtIndex)) => {
                    vassert(placeholderName == placeholderNameAtIndex)
                  }
                  case CoordTemplataT(CoordT(_, _, KindPlaceholderT(placeholderNameAtIndex))) => {
                    vassert(placeholderName == placeholderNameAtIndex)
                  }
                  case _ => vfail()
                }
              }
            })
          }
        }
      }
    }
    true
  })

  override def equals(obj: Any): Boolean = {
    obj match {
      case FunctionHeaderT(thatName, _, _, _, _) => {
        id == thatName
      }
      case _ => false
    }
  }

  // Make sure there's no duplicate names
  vassert(params.map(_.name).toSet.size == params.size);

  vassert(id.localName.parameters == paramTypes)

  //  def isExport = attributes.exists({ case Export2(_) => true case _ => false })
  def isUserFunction = attributes.contains(UserFunctionT)
//  def getAbstractInterface: Option[InterfaceTT] = toBanner.getAbstractInterface
////  def getOverride: Option[(StructTT, InterfaceTT)] = toBanner.getOverride
//  def getVirtualIndex: Option[Int] = toBanner.getVirtualIndex

//  def toSignature(interner: Interner, keywords: Keywords): SignatureT = {
//    val newLastStep = templateName.last.makeFunctionName(interner, keywords, templateArgs, params)
//    val fullName = FullNameT(templateName.packageCoord, name.initSteps, newLastStep)
//
//    SignatureT(fullName)
//
//  }
//  def paramTypes: Vector[CoordT] = params.map(_.tyype)

  def getAbstractInterface: Option[InterfaceTT] = {
    val abstractInterfaces =
      params.collect({
        case ParameterT(_, Some(AbstractT()), _, CoordT(_, _, ir @ InterfaceTT(_))) => ir
      })
    vassert(abstractInterfaces.size <= 1)
    abstractInterfaces.headOption
  }

  def getVirtualIndex: Option[Int] = {
    val indices =
      params.zipWithIndex.collect({
        case (ParameterT(_, Some(AbstractT()), _, _), index) => index
      })
    vassert(indices.size <= 1)
    indices.headOption
  }

//  maybeOriginFunction.foreach(originFunction => {
//    if (originFunction.genericParameters.size != fullName.last.templateArgs.size) {
//      vfail("wtf m8")
//    }
//  })

  def toBanner: FunctionBannerT = FunctionBannerT(maybeOriginFunctionTemplata, id)
  def toPrototype: PrototypeT[IFunctionNameT] = {
//    val substituter = TemplataCompiler.getPlaceholderSubstituter(interner, fullName, templateArgs)
//    val paramTypes = params.map(_.tyype).map(substituter.substituteForCoord)
//    val newLastStep = fullName.last.makeFunctionName(interner, keywords, templateArgs, paramTypes)
//    val newName = FullNameT(fullName.packageCoord, fullName.initSteps, newLastStep)
    PrototypeT(id, returnType)
  }
  def toSignature: SignatureT = {
    toPrototype.toSignature
  }

  def paramTypes: Vector[CoordT] = id.localName.parameters

  def unapply(arg: FunctionHeaderT): Option[(IdT[IFunctionNameT], Vector[ParameterT], CoordT)] = {
    Some(id, params, returnType)
  }

  def isPure: Boolean = {
    attributes.collectFirst({ case PureT => }).nonEmpty
  }
}

case class PrototypeT[+T <: IFunctionNameT](
    id: IdT[T],
    returnType: CoordT) {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def paramTypes: Vector[CoordT] = id.localName.parameters
  def toSignature: SignatureT = SignatureT(id)
}
