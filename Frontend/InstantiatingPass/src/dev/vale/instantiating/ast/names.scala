package dev.vale.instantiating.ast

import dev.vale._
import dev.vale.instantiating.ast.ITemplataI._
import dev.vale.postparsing._
import dev.vale.typing.types.CoordT

// Scout's/Astronomer's name parts correspond to where they are in the source code,
// but Compiler's correspond more to what packages and stamped functions / structs
// they're in. See TNAD.

case class IdI[+R <: IRegionsModeI, +I <: INameI[R]](
  packageCoord: PackageCoordinate,
  initSteps: Vector[INameI[R]],
  localName: I
) {
  // PackageTopLevelName2 is just here because names have to have a last step.
  vassert(initSteps.collectFirst({ case PackageTopLevelNameI() => }).isEmpty)

  vcurious(initSteps.distinct == initSteps)

  override def equals(obj: Any): Boolean = {
    obj match {
      case IdI(thatPackageCoord, thatInitSteps, thatLast) => {
        packageCoord == thatPackageCoord && initSteps == thatInitSteps && localName == thatLast
      }
      case _ => false
    }
  }

  def packageId: IdI[R, PackageTopLevelNameI[R]] = {
    IdI(packageCoord, Vector(), PackageTopLevelNameI())
  }

  def initId: IdI[R, INameI[R]] = {
    if (initSteps.isEmpty) {
      IdI(packageCoord, Vector(), PackageTopLevelNameI())
    } else {
      IdI(packageCoord, initSteps.init, initSteps.last)
    }
  }

  def initNonPackageId(): Option[IdI[R, INameI[R]]] = {
    if (initSteps.isEmpty) {
      None
    } else {
      Some(IdI(packageCoord, initSteps.init, initSteps.last))
    }
  }

  def steps: Vector[INameI[R]] = {
    localName match {
      case PackageTopLevelNameI() => initSteps
      case _ => initSteps :+ localName
    }
  }
}

object INameI {
  def addStep[R <: IRegionsModeI, I <: INameI[R], Y <: INameI[R]](old: IdI[R, I], newLast: Y): IdI[R, Y] = {
    IdI[R, Y](old.packageCoord, old.steps, newLast)
  }
}

sealed trait INameI[+R <: IRegionsModeI]
sealed trait ITemplateNameI[+R <: IRegionsModeI] extends INameI[R]
sealed trait IFunctionTemplateNameI[+R <: IRegionsModeI] extends ITemplateNameI[R] {
//  def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplataI[R]], params: Vector[CoordI]): IFunctionNameI
}
sealed trait IInstantiationNameI[+R <: IRegionsModeI] extends INameI[R] {
  def template: ITemplateNameI[R]
  def templateArgs: Vector[ITemplataI[R]]
}
sealed trait IFunctionNameI[+R <: IRegionsModeI] extends IInstantiationNameI[R] {
  def template: IFunctionTemplateNameI[R]
  def templateArgs: Vector[ITemplataI[R]]
  def parameters: Vector[CoordI[R]]
}
sealed trait ISuperKindTemplateNameI[+R <: IRegionsModeI] extends ITemplateNameI[R]
sealed trait ISubKindTemplateNameI[+R <: IRegionsModeI] extends ITemplateNameI[R]
sealed trait ICitizenTemplateNameI[+R <: IRegionsModeI] extends ISubKindTemplateNameI[R] {
//  def makeCitizenName(templateArgs: Vector[ITemplataI[R]]): ICitizenNameI
}
sealed trait IStructTemplateNameI[+R <: IRegionsModeI] extends ICitizenTemplateNameI[R] {
//  def makeStructName(templateArgs: Vector[ITemplataI[R]]): IStructNameI
//  override def makeCitizenName(templateArgs: Vector[ITemplataI[R]]):
//  ICitizenNameI = {
//    makeStructName(templateArgs)
//  }
}
sealed trait IInterfaceTemplateNameI[+R <: IRegionsModeI] extends ICitizenTemplateNameI[R] with ISuperKindTemplateNameI[R] {
//  def makeInterfaceName(templateArgs: Vector[ITemplataI[R]]): IInterfaceNameI
}
sealed trait ISuperKindNameI[+R <: IRegionsModeI] extends IInstantiationNameI[R] {
  def template: ISuperKindTemplateNameI[R]
  def templateArgs: Vector[ITemplataI[R]]
}
sealed trait ISubKindNameI[+R <: IRegionsModeI] extends IInstantiationNameI[R] {
  def template: ISubKindTemplateNameI[R]
  def templateArgs: Vector[ITemplataI[R]]
}
sealed trait ICitizenNameI[+R <: IRegionsModeI] extends ISubKindNameI[R] {
  def template: ICitizenTemplateNameI[R]
  def templateArgs: Vector[ITemplataI[R]]
}
sealed trait IStructNameI[+R <: IRegionsModeI] extends ICitizenNameI[R] with ISubKindNameI[R] {
  override def template: IStructTemplateNameI[R]
  override def templateArgs: Vector[ITemplataI[R]]
}
sealed trait IInterfaceNameI[+R <: IRegionsModeI] extends ICitizenNameI[R] with ISubKindNameI[R] with ISuperKindNameI[R] {
  override def template: IInterfaceTemplateNameI[R]
  override def templateArgs: Vector[ITemplataI[R]]
}
sealed trait IImplTemplateNameI[+R <: IRegionsModeI] extends ITemplateNameI[R] {
//  def makeImplName(templateArgs: Vector[ITemplataI[R]], subCitizen: ICitizenIT): IImplNameI
}
sealed trait IImplNameI[+R <: IRegionsModeI] extends IInstantiationNameI[R] {
  def template: IImplTemplateNameI[R]
}

sealed trait IRegionNameI[+R <: IRegionsModeI] extends INameI[R]

case class RegionNameI[+R <: IRegionsModeI](rune: IRuneS) extends IRegionNameI[R]
case class DenizenDefaultRegionNameI[+R <: IRegionsModeI]() extends IRegionNameI[R]

case class ExportTemplateNameI[+R <: IRegionsModeI](codeLoc: CodeLocationS) extends ITemplateNameI[R]
case class ExportNameI[+R <: IRegionsModeI](
  template: ExportTemplateNameI[R],
  region: RegionTemplataI[R]
) extends IInstantiationNameI[R] {
  override def templateArgs: Vector[ITemplataI[R]] = Vector(region)
}

case class ExternTemplateNameI[+R <: IRegionsModeI](codeLoc: CodeLocationS) extends ITemplateNameI[R]
case class ExternNameI[+R <: IRegionsModeI](
  template: ExternTemplateNameI[R],
  region: RegionTemplataI[R]
) extends IInstantiationNameI[R] {
  override def templateArgs: Vector[ITemplataI[R]] = Vector(region)
}

case class ImplTemplateNameI[+R <: IRegionsModeI](codeLocationS: CodeLocationS) extends IImplTemplateNameI[R] {
  vpass()
//  override def makeImplName(templateArgs: Vector[ITemplataI[R]], subCitizen: ICitizenIT): ImplNameI = {
//    ImplNameI(this, templateArgs, subCitizen)
//  }
}
case class ImplNameI[+R <: IRegionsModeI](
  template: IImplTemplateNameI[R],
  templateArgs: Vector[ITemplataI[R]],
  // The instantiator wants this so it can know the struct type up-front before monomorphizing the
  // whole impl, so it can hoist some bounds out of the struct, like NBIFP.
  subCitizen: ICitizenIT[R]
) extends IImplNameI[R] {
  vpass()
}

case class ImplBoundTemplateNameI[+R <: IRegionsModeI](codeLocationS: CodeLocationS) extends IImplTemplateNameI[R] {
//  override def makeImplName(templateArgs: Vector[ITemplataI[R]], subCitizen: ICitizenIT): ImplBoundNameI = {
//    ImplBoundNameI(this, templateArgs)
//  }
}
case class ImplBoundNameI[+R <: IRegionsModeI](
  template: ImplBoundTemplateNameI[R],
  templateArgs: Vector[ITemplataI[R]]
) extends IImplNameI[R] {

}

//// The name of an impl that is subclassing some interface. To find all impls subclassing an interface,
//// look for this name.
//case class ImplImplementingSuperInterfaceNameI[+R <: IRegionsModeI](superInterface: FullNameI[IInterfaceTemplateNameI]) extends IImplTemplateNameI
//// The name of an impl that is augmenting some sub citizen. To find all impls subclassing an interface,
//// look for this name.
//case class ImplAugmentingSubCitizenNameI[+R <: IRegionsModeI](subCitizen: FullNameI[ICitizenTemplateNameI]) extends IImplTemplateNameI

case class LetNameI[+R <: IRegionsModeI](codeLocation: CodeLocationS) extends INameI[R]
case class ExportAsNameI[+R <: IRegionsModeI](codeLocation: CodeLocationS) extends INameI[R]

case class RawArrayNameI[+R <: IRegionsModeI](
  mutability: MutabilityI,
  elementType: CoordTemplataI[R],
  selfRegion: RegionTemplataI[R]
) extends INameI[R] {
}

case class ReachablePrototypeNameI[+R <: IRegionsModeI](num: Int) extends INameI[R]

case class StaticSizedArrayTemplateNameI[+R <: IRegionsModeI]() extends ICitizenTemplateNameI[R] {
//  override def makeCitizenName(templateArgs: Vector[ITemplataI[R]]): ICitizenNameI = {
//    vassert(templateArgs.size == 5)
//    val size = expectIntegerTemplata(templateArgs(0)).value
//    val mutability = expectMutabilityTemplata(templateArgs(1)).mutability
//    val variability = expectVariabilityTemplata(templateArgs(2)).variability
//    val elementType = expectCoordTemplata(templateArgs(3)).coord
//    val selfRegion = expectRegionTemplata(templateArgs(4))
//    StaticSizedArrayNameI(this, size, variability, RawArrayNameI(mutability, elementType, selfRegion))
//  }
}

case class StaticSizedArrayNameI[+R <: IRegionsModeI](
  template: StaticSizedArrayTemplateNameI[R],
  size: Long,
  variability: VariabilityI,
  arr: RawArrayNameI[R]
) extends ICitizenNameI[R] {

  override def templateArgs: Vector[ITemplataI[R]] = {
    Vector(
      IntegerTemplataI(size),
      MutabilityTemplataI(arr.mutability),
      VariabilityTemplataI(variability),
      arr.elementType)
  }
}

case class RuntimeSizedArrayTemplateNameI[+R <: IRegionsModeI]() extends ICitizenTemplateNameI[R] {
//  override def makeCitizenName(templateArgs: Vector[ITemplataI[R]]): ICitizenNameI = {
//    vassert(templateArgs.size == 3)
//    val mutability = expectMutabilityTemplata(templateArgs(0)).mutability
//    val elementType = expectCoordTemplata(templateArgs(1)).coord
//    val region = expectRegionTemplata(templateArgs(2))
//    RuntimeSizedArrayNameI(this, RawArrayNameI(mutability, elementType, region))
//  }
}

case class RuntimeSizedArrayNameI[+R <: IRegionsModeI](
  template: RuntimeSizedArrayTemplateNameI[R],
  arr: RawArrayNameI[R]
) extends ICitizenNameI[R] {
  override def templateArgs: Vector[ITemplataI[R]] = {
    Vector(
      MutabilityTemplataI(arr.mutability),
      arr.elementType)
  }
}

// See NNSPAFOC.
case class OverrideDispatcherTemplateNameI[+R <: IRegionsModeI](
  implId: IdI[R, IImplTemplateNameI[R]]
) extends IFunctionTemplateNameI[R] {
//  override def makeFunctionName(
//    interner: Interner,
//    keywords: Keywords,
//    templateArgs: Vector[ITemplataI[R]],
//    params: Vector[CoordI]):
//  OverrideDispatcherNameI = {
//    interner.intern(OverrideDispatcherNameI(this, templateArgs, params))
//  }
}

case class OverrideDispatcherNameI[+R <: IRegionsModeI](
  template: OverrideDispatcherTemplateNameI[R],
  // This will have placeholders in it after the typing pass.
  templateArgs: Vector[ITemplataI[R]],
  parameters: Vector[CoordI[R]]
) extends IFunctionNameI[R] {
  vpass()
}

case class OverrideDispatcherCaseNameI[+R <: IRegionsModeI](
  // These are the templatas for the independent runes from the impl, like the <ZZ> for Milano, see
  // OMCNAGP.
  independentImplTemplateArgs: Vector[ITemplataI[R]]
) extends ITemplateNameI[R] with IInstantiationNameI[R] {
  override def template: ITemplateNameI[R] = this
  override def templateArgs: Vector[ITemplataI[R]] = independentImplTemplateArgs
}

sealed trait IVarNameI[+R <: IRegionsModeI] extends INameI[R]
case class TypingPassBlockResultVarNameI[+R <: IRegionsModeI](life: LocationInFunctionEnvironmentI) extends IVarNameI[R]
case class TypingPassFunctionResultVarNameI[+R <: IRegionsModeI]() extends IVarNameI[R]
case class TypingPassTemporaryVarNameI[+R <: IRegionsModeI](life: LocationInFunctionEnvironmentI) extends IVarNameI[R]
case class TypingPassPatternMemberNameI[+R <: IRegionsModeI](life: LocationInFunctionEnvironmentI) extends IVarNameI[R]
case class TypingIgnoredParamNameI[+R <: IRegionsModeI](num: Int) extends IVarNameI[R]
case class TypingPassPatternDestructureeNameI[+R <: IRegionsModeI](life: LocationInFunctionEnvironmentI) extends IVarNameI[R]
case class UnnamedLocalNameI[+R <: IRegionsModeI](codeLocation: CodeLocationS) extends IVarNameI[R]
case class ClosureParamNameI[+R <: IRegionsModeI](codeLocation: CodeLocationS) extends IVarNameI[R]
case class ConstructingMemberNameI[+R <: IRegionsModeI](name: StrI) extends IVarNameI[R]
case class WhileCondResultNameI[+R <: IRegionsModeI](range: RangeS) extends IVarNameI[R]
case class IterableNameI[+R <: IRegionsModeI](range: RangeS) extends IVarNameI[R] {  }
case class IteratorNameI[+R <: IRegionsModeI](range: RangeS) extends IVarNameI[R] {  }
case class IterationOptionNameI[+R <: IRegionsModeI](range: RangeS) extends IVarNameI[R] {  }
case class MagicParamNameI[+R <: IRegionsModeI](codeLocation2: CodeLocationS) extends IVarNameI[R]
case class CodeVarNameI[+R <: IRegionsModeI](name: StrI) extends IVarNameI[R]
// We dont use CodeVarName2(0), CodeVarName2(1) etc because we dont want the user to address these members directly.
case class AnonymousSubstructMemberNameI[+R <: IRegionsModeI](index: Int) extends IVarNameI[R]
case class PrimitiveNameI[+R <: IRegionsModeI](humanName: StrI) extends INameI[R]
// Only made in typingpass
case class PackageTopLevelNameI[+R <: IRegionsModeI]() extends INameI[R]
case class ProjectNameI[+R <: IRegionsModeI](name: StrI) extends INameI[R]
case class PackageNameI[+R <: IRegionsModeI](name: StrI) extends INameI[R]
case class RuneNameI[+R <: IRegionsModeI](rune: IRuneS) extends INameI[R]

// This is the name of a function that we're still figuring out in the function typingpass.
// We have its closured variables, but are still figuring out its template args and params.
case class BuildingFunctionNameWithClosuredsI[+R <: IRegionsModeI](
  templateName: IFunctionTemplateNameI[R],
) extends INameI[R] {



}

case class ExternFunctionNameI[+R <: IRegionsModeI](
  humanName: StrI,
  parameters: Vector[CoordI[R]]
) extends IFunctionNameI[R] with IFunctionTemplateNameI[R] {
  override def template: IFunctionTemplateNameI[R] = this

//  override def makeFunctionName(
//    interner: Interner,
//    keywords: Keywords,
//    templateArgs: Vector[ITemplataI[R]],
//    params: Vector[CoordI]):
//  IFunctionNameI = this

  override def templateArgs: Vector[ITemplataI[R]] = Vector.empty
}

case class FunctionNameIX[+R <: IRegionsModeI](
  template: FunctionTemplateNameI[R],
  templateArgs: Vector[ITemplataI[R]],
  parameters: Vector[CoordI[R]]
) extends IFunctionNameI[R]

case class ForwarderFunctionNameI[+R <: IRegionsModeI](
  template: ForwarderFunctionTemplateNameI[R],
  inner: IFunctionNameI[R]
) extends IFunctionNameI[R] {
  override def templateArgs: Vector[ITemplataI[R]] = inner.templateArgs
  override def parameters: Vector[CoordI[R]] = inner.parameters
}

case class FunctionBoundTemplateNameI[+R <: IRegionsModeI](
  humanName: StrI,
  codeLocation: CodeLocationS
) extends INameI[R] with IFunctionTemplateNameI[R] {
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplataI[R]], params: Vector[CoordI]): FunctionBoundNameI = {
//    interner.intern(FunctionBoundNameI(this, templateArgs, params))
//  }
}

case class FunctionBoundNameI[+R <: IRegionsModeI](
  template: FunctionBoundTemplateNameI[R],
  templateArgs: Vector[ITemplataI[R]],
  parameters: Vector[CoordI[R]]
) extends IFunctionNameI[R]

case class FunctionTemplateNameI[+R <: IRegionsModeI](
    humanName: StrI,
    codeLocation: CodeLocationS
) extends INameI[R] with IFunctionTemplateNameI[R] {
  vpass()
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplataI[R]], params: Vector[CoordI]): IFunctionNameI = {
//    interner.intern(FunctionNameI(this, templateArgs, params))
//  }
}

case class LambdaCallFunctionTemplateNameI[+R <: IRegionsModeI](
  codeLocation: CodeLocationS,
  paramTypes: Vector[CoordT]
) extends INameI[R] with IFunctionTemplateNameI[R] {
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplataI[R]], params: Vector[CoordI]): IFunctionNameI = {
//    // Post instantiator, the params will be real, but our template paramTypes will still be placeholders
//    // vassert(params == paramTypes)
//    interner.intern(LambdaCallFunctionNameI(this, templateArgs, params))
//  }
}

case class LambdaCallFunctionNameI[+R <: IRegionsModeI](
  template: LambdaCallFunctionTemplateNameI[R],
  templateArgs: Vector[ITemplataI[R]],
  parameters: Vector[CoordI[R]]
) extends IFunctionNameI[R]

case class ForwarderFunctionTemplateNameI[+R <: IRegionsModeI](
  inner: IFunctionTemplateNameI[R],
  index: Int
) extends INameI[R] with IFunctionTemplateNameI[R] {
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplataI[R]], params: Vector[CoordI]): IFunctionNameI = {
//    interner.intern(ForwarderFunctionNameI(this, inner.makeFunctionName(keywords, templateArgs, params)))//, index))
//  }
}


//case class AbstractVirtualDropFunctionTemplateNameI[+R <: IRegionsModeI](
//  implName: INameI[R]
//) extends INameI[R] with IFunctionTemplateNameI {
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplata[ITemplataType]], params: Vector[CoordI]): IFunctionNameI = {
//    interner.intern(
//      AbstractVirtualDropFunctionNameI(implName, templateArgs, params))
//  }
//}

//case class AbstractVirtualDropFunctionNameI[+R <: IRegionsModeI](
//  implName: INameI[R],
//  templateArgs: Vector[ITemplata[ITemplataType]],
//  parameters: Vector[CoordI]
//) extends INameI[R] with IFunctionNameI

//case class OverrideVirtualDropFunctionTemplateNameI[+R <: IRegionsModeI](
//  implName: INameI[R]
//) extends INameI[R] with IFunctionTemplateNameI {
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplata[ITemplataType]], params: Vector[CoordI]): IFunctionNameI = {
//    interner.intern(
//      OverrideVirtualDropFunctionNameI(implName, templateArgs, params))
//  }
//}

//case class OverrideVirtualDropFunctionNameI[+R <: IRegionsModeI](
//  implName: INameI[R],
//  templateArgs: Vector[ITemplata[ITemplataType]],
//  parameters: Vector[CoordI]
//) extends INameI[R] with IFunctionNameI

//case class LambdaTemplateNameI[+R <: IRegionsModeI](
//  codeLocation: CodeLocationS
//) extends INameI[R] with IFunctionTemplateNameI {
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplata[ITemplataType]], params: Vector[CoordI]): IFunctionNameI = {
//    interner.intern(FunctionNameI(interner.intern(FunctionTemplateNameI(keywords.underscoresCall, codeLocation)), templateArgs, params))
//  }
//}
case class ConstructorTemplateNameI[+R <: IRegionsModeI](
  codeLocation: CodeLocationS
) extends INameI[R] with IFunctionTemplateNameI[R] {
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplataI[R]], params: Vector[CoordI]): IFunctionNameI = vimpl()
}

//case class FreeTemplateNameI[+R <: IRegionsModeI](codeLoc: CodeLocationS) extends INameI[R] with IFunctionTemplateNameI {
//  vpass()
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplata[ITemplataType]], params: Vector[CoordI]): IFunctionNameI = {
//    params match {
//      case Vector(coord) => {
//        interner.intern(FreeNameI(this, templateArgs, coord))
//      }
//      case other => vwat(other)
//    }
//  }
//}
//case class FreeNameI[+R <: IRegionsModeI](
//  template: FreeTemplateNameI,
//  templateArgs: Vector[ITemplata[ITemplataType]],
//  coordT: CoordI
//) extends IFunctionNameI {
//  override def parameters: Vector[CoordI] = Vector(coordI)
//}

//// See NSIDN for why we have these virtual names
//case class AbstractVirtualFreeTemplateNameI[+R <: IRegionsModeI](codeLoc: CodeLocationS) extends INameI[R] with IFunctionTemplateNameI {
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplata[ITemplataType]], params: Vector[CoordI]): IFunctionNameI = {
//    val Vector(CoordI(ShareI, kind)) = params
//    interner.intern(AbstractVirtualFreeNameI(templateArgs, kind))
//  }
//}
//// See NSIDN for why we have these virtual names
//case class AbstractVirtualFreeNameI[+R <: IRegionsModeI](templateArgs: Vector[ITemplata[ITemplataType]], param: KindI) extends IFunctionNameI {
//  override def parameters: Vector[CoordI] = Vector(CoordI(ShareI, param))
//}
//
//// See NSIDN for why we have these virtual names
//case class OverrideVirtualFreeTemplateNameI[+R <: IRegionsModeI](codeLoc: CodeLocationS) extends INameI[R] with IFunctionTemplateNameI {
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplata[ITemplataType]], params: Vector[CoordI]): IFunctionNameI = {
//    val Vector(CoordI(ShareI, kind)) = params
//    interner.intern(OverrideVirtualFreeNameI(templateArgs, kind))
//  }
//}
//// See NSIDN for why we have these virtual names
//case class OverrideVirtualFreeNameI[+R <: IRegionsModeI](templateArgs: Vector[ITemplata[ITemplataType]], param: KindI) extends IFunctionNameI {
//  override def parameters: Vector[CoordI] = Vector(CoordI(ShareI, param))
//}

// Vale has no Self, its just a convenient first name parameter.
// See also SelfNameS.
case class SelfNameI[+R <: IRegionsModeI]() extends IVarNameI[R]
case class ArbitraryNameI[+R <: IRegionsModeI]() extends INameI[R]

sealed trait CitizenNameI[+R <: IRegionsModeI] extends ICitizenNameI[R] {
  def template: ICitizenTemplateNameI[R]
  def templateArgs: Vector[ITemplataI[R]]
}

object CitizenNameI {
  def unapply[R <: IRegionsModeI](c: CitizenNameI[R]): Option[(ICitizenTemplateNameI[R], Vector[ITemplataI[R]])] = {
    c match {
      case StructNameI(template, templateArgs) => Some((template, templateArgs))
      case InterfaceNameI(template, templateArgs) => Some((template, templateArgs))
    }
  }
}

case class StructNameI[+R <: IRegionsModeI](
  template: IStructTemplateNameI[R],
  templateArgs: Vector[ITemplataI[R]]
) extends IStructNameI[R] with CitizenNameI[R] {
  vpass()
}

case class InterfaceNameI[+R <: IRegionsModeI](
  template: IInterfaceTemplateNameI[R],
  templateArgs: Vector[ITemplataI[R]]
) extends IInterfaceNameI[R] with CitizenNameI[R] {
  vpass()
}

case class LambdaCitizenTemplateNameI[+R <: IRegionsModeI](
  codeLocation: CodeLocationS
) extends IStructTemplateNameI[R] {
//  override def makeStructName(templateArgs: Vector[ITemplataI[R]]): IStructNameI = {
//    vassert(templateArgs.isEmpty)
//    interner.intern(LambdaCitizenNameI(this))
//  }
}

case class LambdaCitizenNameI[+R <: IRegionsModeI](
  template: LambdaCitizenTemplateNameI[R]
) extends IStructNameI[R] {
  def templateArgs: Vector[ITemplataI[R]] = Vector.empty
  vpass()
}

sealed trait CitizenTemplateNameI[+R <: IRegionsModeI] extends ICitizenTemplateNameI[R] {
  def humanName: StrI
  // We don't include a CodeLocation here because:
  // - There's no struct overloading, so there should only ever be one, we don't have to disambiguate
  //   with code locations
  // - It makes it easier to determine the CitizenTemplateNameI from a CitizenNameI which doesn't
  //   remember its code location.
  //codeLocation: CodeLocationS

//  override def makeCitizenName(templateArgs: Vector[ITemplata[ITemplataType]]): ICitizenNameI = {
//    interner.intern(CitizenNameI(this, templateArgs))
//  }
}

case class StructTemplateNameI[+R <: IRegionsModeI](
  humanName: StrI,
  // We don't include a CodeLocation here because:
  // - There's no struct overloading, so there should only ever be one, we don't have to disambiguate
  //   with code locations
  // - It makes it easier to determine the StructTemplateNameI from a StructNameI which doesn't
  //   remember its code location.
  //   (note from later: not sure this is true anymore, since StructNameI contains a StructTemplateNameI)
  //codeLocation: CodeLocationS
) extends IStructTemplateNameI[R] with CitizenTemplateNameI[R] {
  vpass()

//  override def makeStructName(templateArgs: Vector[ITemplataI[R]]): IStructNameI = {
//    interner.intern(StructNameI(this, templateArgs))
//  }
}
case class InterfaceTemplateNameI[+R <: IRegionsModeI](
  humanNamee: StrI,
  // We don't include a CodeLocation here because:
  // - There's no struct overloading, so there should only ever be one, we don't have to disambiguate
  //   with code locations
  // - It makes it easier to determine the InterfaceTemplateNameI from a InterfaceNameI which doesn't
  //   remember its code location.
  //codeLocation: CodeLocationS
) extends IInterfaceTemplateNameI[R] with CitizenTemplateNameI[R] with ICitizenTemplateNameI[R] {
  override def humanName = humanNamee
//  override def makeInterfaceName(templateArgs: Vector[ITemplataI[R]]): IInterfaceNameI = {
//    interner.intern(InterfaceNameI(this, templateArgs))
//  }
//  override def makeCitizenName(templateArgs: Vector[ITemplataI[R]]): ICitizenNameI = {
//    makeInterfaceName(templateArgs)
//  }
}

case class AnonymousSubstructImplTemplateNameI[+R <: IRegionsModeI](
  interface: IInterfaceTemplateNameI[R]
) extends IImplTemplateNameI[R] {
//  override def makeImplName(templateArgs: Vector[ITemplataI[R]], subCitizen: ICitizenIT): IImplNameI = {
//    AnonymousSubstructImplNameI(this, templateArgs, subCitizen)
//  }
}
case class AnonymousSubstructImplNameI[+R <: IRegionsModeI](
  template: AnonymousSubstructImplTemplateNameI[R],
  templateArgs: Vector[ITemplataI[R]],
  subCitizen: ICitizenIT[R]
) extends IImplNameI[R]


case class AnonymousSubstructTemplateNameI[+R <: IRegionsModeI](
  // This happens to be the same thing that appears before this AnonymousSubstructNameI in a FullNameT.
  // This is really only here to help us calculate the imprecise name for this thing.
  interface: IInterfaceTemplateNameI[R]
) extends IStructTemplateNameI[R] {
//  override def makeStructName(templateArgs: Vector[ITemplataI[R]]): IStructNameI = {
//    interner.intern(AnonymousSubstructNameI(this, templateArgs))
//  }
}
case class AnonymousSubstructConstructorTemplateNameI[+R <: IRegionsModeI](
  substruct: ICitizenTemplateNameI[R]
) extends IFunctionTemplateNameI[R] {
//  override def makeFunctionName(keywords: Keywords, templateArgs: Vector[ITemplataI[R]], params: Vector[CoordI]): IFunctionNameI = {
//    interner.intern(AnonymousSubstructConstructorNameI(this, templateArgs, params))
//  }
}

case class AnonymousSubstructConstructorNameI[+R <: IRegionsModeI](
  template: AnonymousSubstructConstructorTemplateNameI[R],
  templateArgs: Vector[ITemplataI[R]],
  parameters: Vector[CoordI[R]]
) extends IFunctionNameI[R]

case class AnonymousSubstructNameI[+R <: IRegionsModeI](
  // This happens to be the same thing that appears before this AnonymousSubstructNameI in a FullNameT.
  // This is really only here to help us calculate the imprecise name for this thing.
  template: AnonymousSubstructTemplateNameI[R],
  templateArgs: Vector[ITemplataI[R]]
) extends IStructNameI[R] {

}
//case class AnonymousSubstructImplNameI[+R <: IRegionsModeI]() extends INameI[R] {
//
//}

case class ResolvingEnvNameI[+R <: IRegionsModeI]() extends INameI[R] {
  vpass()
}

case class CallEnvNameI[+R <: IRegionsModeI]() extends INameI[R] {
  vpass()
}
