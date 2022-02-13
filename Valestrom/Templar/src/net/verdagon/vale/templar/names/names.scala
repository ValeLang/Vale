package net.verdagon.vale.templar.names

import net.verdagon.vale._
import net.verdagon.vale.scout.IRuneS
import net.verdagon.vale.templar.ast.LocationInFunctionEnvironment
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.templata.{CoordTemplata, ITemplata}
import net.verdagon.vale.templar.types._

// Scout's/Astronomer's name parts correspond to where they are in the source code,
// but Templar's correspond more to what packages and stamped functions / structs
// they're in. See TNAD.

case class FullNameT[+T <: INameT](
  packageCoord: PackageCoordinate,
  initSteps: Vector[INameT],
  last: T
)  {

  override def equals(obj: Any): Boolean = {
    obj match {
      case FullNameT(thatPackageCoord, thatInitSteps, thatLast) => {
        packageCoord == thatPackageCoord && initSteps == thatInitSteps && last == thatLast
      }
      case _ => false
    }
  }
  // PackageTopLevelName2 is just here because names have to have a last step.
  vassert(initSteps.collectFirst({ case PackageTopLevelNameT() => }).isEmpty)

  vassert(!initSteps.exists({
    case AnonymousSubstructTemplateNameT(_) => true
    case _ => false
  }))
  vcurious(initSteps.distinct == initSteps)

  this match {
    case FullNameT(PackageCoordinate.TEST_TLD, Vector(), FunctionNameT("main", Vector(), Vector())) =>
    case _ =>
  }

  def steps: Vector[INameT] = {
    last match {
      case PackageTopLevelNameT() => initSteps
      case _ => initSteps :+ last
    }
  }
  def addStep[Y <: INameT](newLast: Y): FullNameT[Y] = {
    FullNameT[Y](packageCoord, steps, newLast)
  }
//  def init: FullNameT[INameT] = {
//    if (initSteps.isEmpty) {
//      last match {
//        case PackageTopLevelNameT() => vimpl()
//        case _ => {
//          FullNameT(packageCoord, Vector(), interner.intern(PackageTopLevelNameT()))
//        }
//      }
//    } else {
//      FullNameT(packageCoord, initSteps.init, initSteps.last)
//    }
//  }

//  def parent: Option[FullNameT[INameT]] = {
//    if (initSteps.isEmpty) {
//      packageCoord.parent match {
//        case None => None
//        case Some(parentPackage) => Some(FullNameT(parentPackage, Vector(), interner.intern(PackageTopLevelNameT())))
//      }
//    } else {
//      Some(FullNameT(packageCoord, initSteps.init, initSteps.last))
//    }
//  }
//
//  def selfAndParents: List[FullNameT[INameT]] = {
//    parent match {
//      case None => List(this)
//      case Some(parent) => this :: parent.selfAndParents
//    }
//  }
//
//  def parents: List[FullNameT[INameT]] = {
//    parent match {
//      case None => List()
//      case Some(parent) => parent.selfAndParents
//    }
//  }
}

sealed trait INameT extends IInterning
sealed trait ITemplateNameT extends INameT
sealed trait IFunctionTemplateNameT extends ITemplateNameT {
  def makeFunctionName(interner: Interner, templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT
}
sealed trait IFunctionNameT extends INameT {
  def templateArgs: Vector[ITemplata]
  def parameters: Vector[CoordT]
}
sealed trait ICitizenTemplateNameT extends ITemplateNameT {
  def makeCitizenName(interner: Interner, templateArgs: Vector[ITemplata]): ICitizenNameT
}
sealed trait ICitizenNameT extends INameT {
  def template: ICitizenTemplateNameT
  def templateArgs: Vector[ITemplata]
}
trait IImplTemplateNameT extends INameT
trait IImplDeclareNameT extends INameT {    }
case class ImplDeclareNameT(codeLocation: CodeLocationS) extends IImplDeclareNameT {    }
case class AnonymousSubstructImplDeclarationNameT(interfaceName: INameT) extends IImplDeclareNameT
case class LetNameT(codeLocation: CodeLocationS) extends INameT {    }
case class ExportAsNameT(codeLocation: CodeLocationS) extends INameT {    }

case class RawArrayNameT(mutability: MutabilityT, elementType: CoordT) extends INameT {    }
case class StaticSizedArrayNameT(size: Int, arr: RawArrayNameT) extends INameT {    }
case class RuntimeSizedArrayNameT(arr: RawArrayNameT) extends INameT {    }
sealed trait IVarNameT extends INameT
case class TemplarBlockResultVarNameT(life: LocationInFunctionEnvironment) extends IVarNameT {    }
case class TemplarFunctionResultVarNameT() extends IVarNameT {    }
case class TemplarTemporaryVarNameT(life: LocationInFunctionEnvironment) extends IVarNameT {    }
case class TemplarPatternMemberNameT(life: LocationInFunctionEnvironment) extends IVarNameT {    }
case class TemplarIgnoredParamNameT(num: Int) extends IVarNameT {    }
case class TemplarPatternDestructureeNameT(life: LocationInFunctionEnvironment) extends IVarNameT {    }
case class UnnamedLocalNameT(codeLocation: CodeLocationS) extends IVarNameT {    }
case class ClosureParamNameT() extends IVarNameT {    }
case class ConstructingMemberNameT(name: String) extends IVarNameT {    }
case class WhileCondResultNameT(range: RangeS) extends IVarNameT {    }
case class IterableNameT(range: RangeS) extends IVarNameT {  }
case class IteratorNameT(range: RangeS) extends IVarNameT {  }
case class IterationOptionNameT(range: RangeS) extends IVarNameT {  }
case class MagicParamNameT(codeLocation2: CodeLocationS) extends IVarNameT {    }
case class CodeVarNameT(name: String) extends IVarNameT {    }
// We dont use CodeVarName2(0), CodeVarName2(1) etc because we dont want the user to address these members directly.
case class AnonymousSubstructMemberNameT(index: Int) extends IVarNameT {    }
case class PrimitiveNameT(humanName: String) extends INameT {    }
// Only made in templar
case class PackageTopLevelNameT() extends INameT {    }
case class ProjectNameT(name: String) extends INameT {    }
case class PackageNameT(name: String) extends INameT {    }
case class RuneNameT(rune: IRuneS) extends INameT {    }

// This is the name of a function that we're still figuring out in the function templar.
// We have its closured variables, but are still figuring out its template args and params.
case class BuildingFunctionNameWithClosuredsT(
  templateName: IFunctionTemplateNameT,
) extends INameT {



}
// This is the name of a function that we're still figuring out in the function templar.
// We have its closured variables and template args, but are still figuring out its params.
case class BuildingFunctionNameWithClosuredsAndTemplateArgsT(
  templateName: IFunctionTemplateNameT,
  templateArgs: Vector[ITemplata]
) extends INameT {



}

case class ExternFunctionNameT(
  humanName: String,
  parameters: Vector[CoordT]
) extends IFunctionNameT {

  override def templateArgs: Vector[ITemplata] = Vector.empty



}

case class FunctionNameT(
  humanName: String,
  templateArgs: Vector[ITemplata],
  parameters: Vector[CoordT]
) extends IFunctionNameT


case class ForwarderFunctionNameT(
  inner: IFunctionNameT,
  index: Int
) extends IFunctionNameT {
  override def templateArgs: Vector[ITemplata] = inner.templateArgs
  override def parameters: Vector[CoordT] = inner.parameters
}

case class FunctionTemplateNameT(
    humanName: String,
    codeLocation: CodeLocationS
) extends INameT with IFunctionTemplateNameT {
  vpass()
  override def makeFunctionName(interner: Interner, templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    interner.intern(FunctionNameT(humanName, templateArgs, params))
  }
}

case class ForwarderFunctionTemplateNameT(
  inner: IFunctionTemplateNameT,
  index: Int
) extends INameT with IFunctionTemplateNameT {
  override def makeFunctionName(interner: Interner, templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    interner.intern(ForwarderFunctionNameT(inner.makeFunctionName(interner, templateArgs, params), index))
  }
}


//case class AbstractVirtualDropFunctionTemplateNameT(
//  implName: INameT
//) extends INameT with IFunctionTemplateNameT {
//  override def makeFunctionName(interner: Interner, templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
//    interner.intern(
//      AbstractVirtualDropFunctionNameT(implName, templateArgs, params))
//  }
//}

//case class AbstractVirtualDropFunctionNameT(
//  implName: INameT,
//  templateArgs: Vector[ITemplata],
//  parameters: Vector[CoordT]
//) extends INameT with IFunctionNameT

//case class OverrideVirtualDropFunctionTemplateNameT(
//  implName: INameT
//) extends INameT with IFunctionTemplateNameT {
//  override def makeFunctionName(interner: Interner, templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
//    interner.intern(
//      OverrideVirtualDropFunctionNameT(implName, templateArgs, params))
//  }
//}

//case class OverrideVirtualDropFunctionNameT(
//  implName: INameT,
//  templateArgs: Vector[ITemplata],
//  parameters: Vector[CoordT]
//) extends INameT with IFunctionNameT

case class LambdaTemplateNameT(
  codeLocation: CodeLocationS
) extends INameT with IFunctionTemplateNameT {
  override def makeFunctionName(interner: Interner, templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    interner.intern(FunctionNameT(CallTemplar.CALL_FUNCTION_NAME, templateArgs, params))
  }
}
case class ConstructorTemplateNameT(
  codeLocation: CodeLocationS
) extends INameT with IFunctionTemplateNameT {
  override def makeFunctionName(interner: Interner, templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = vimpl()
}

case class FreeTemplateNameT(codeLoc: CodeLocationS) extends INameT with IFunctionTemplateNameT {
  override def makeFunctionName(interner: Interner, templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    val Vector(CoordT(ShareT, kind)) = params
    interner.intern(FreeNameT(templateArgs, kind))
  }
}
case class FreeNameT(templateArgs: Vector[ITemplata], kind: KindT) extends IFunctionNameT {
  override def parameters: Vector[CoordT] = Vector(CoordT(ShareT, kind))
}

//// See NSIDN for why we have these virtual names
//case class AbstractVirtualFreeTemplateNameT(codeLoc: CodeLocationS) extends INameT with IFunctionTemplateNameT {
//  override def makeFunctionName(interner: Interner, templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
//    val Vector(CoordT(ShareT, kind)) = params
//    interner.intern(AbstractVirtualFreeNameT(templateArgs, kind))
//  }
//}
//// See NSIDN for why we have these virtual names
//case class AbstractVirtualFreeNameT(templateArgs: Vector[ITemplata], param: KindT) extends IFunctionNameT {
//  override def parameters: Vector[CoordT] = Vector(CoordT(ShareT, param))
//}
//
//// See NSIDN for why we have these virtual names
//case class OverrideVirtualFreeTemplateNameT(codeLoc: CodeLocationS) extends INameT with IFunctionTemplateNameT {
//  override def makeFunctionName(interner: Interner, templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
//    val Vector(CoordT(ShareT, kind)) = params
//    interner.intern(OverrideVirtualFreeNameT(templateArgs, kind))
//  }
//}
//// See NSIDN for why we have these virtual names
//case class OverrideVirtualFreeNameT(templateArgs: Vector[ITemplata], param: KindT) extends IFunctionNameT {
//  override def parameters: Vector[CoordT] = Vector(CoordT(ShareT, param))
//}

// Vale has no Self, its just a convenient first name parameter.
// See also SelfNameS.
case class SelfNameT() extends IVarNameT
case class ArbitraryNameT() extends INameT
case class ConstructorNameT(
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  def templateArgs: Vector[ITemplata] = Vector.empty
}

case class CitizenNameT(
  template: ICitizenTemplateNameT,
  templateArgs: Vector[ITemplata]
) extends ICitizenNameT {
  vpass()
}

case class LambdaCitizenTemplateNameT(
  codeLocation: CodeLocationS
) extends ICitizenTemplateNameT {
  def templateArgs: Vector[ITemplata] = Vector.empty
  override def makeCitizenName(interner: Interner, templateArgs: Vector[ITemplata]): ICitizenNameT = {
    vassert(templateArgs.isEmpty)
    interner.intern(LambdaCitizenNameT(this))
  }
}

case class LambdaCitizenNameT(
  template: LambdaCitizenTemplateNameT
) extends ICitizenNameT {
  def templateArgs: Vector[ITemplata] = Vector.empty
}

case class AnonymousSubstructLambdaTemplateNameT(
  codeLocation: CodeLocationS
) extends ICitizenTemplateNameT {
  def templateArgs: Vector[ITemplata] = Vector.empty
  override def makeCitizenName(interner: Interner, templateArgs: Vector[ITemplata]): ICitizenNameT = {
    vassert(templateArgs.isEmpty)
    interner.intern(AnonymousSubstructLambdaNameT(this))
  }
}

case class AnonymousSubstructLambdaNameT(
  template: AnonymousSubstructLambdaTemplateNameT
) extends ICitizenNameT {
  def templateArgs: Vector[ITemplata] = Vector.empty
}

case class CitizenTemplateNameT(
  humanName: String,
  // We don't include a CodeLocation here because:
  // - There's no struct overloading, so there should only ever be one, we don't have to disambiguate
  //   with code locations
  // - It makes it easier to determine the CitizenTemplateNameT from a CitizenNameT which doesn't
  //   remember its code location.
  //codeLocation: CodeLocationS
) extends ICitizenTemplateNameT {



  override def makeCitizenName(interner: Interner, templateArgs: Vector[ITemplata]): ICitizenNameT = {
    interner.intern(CitizenNameT(this, templateArgs))
  }
}

case class AnonymousSubstructImplTemplateNameT(
  interface: ICitizenTemplateNameT
) extends IImplTemplateNameT

case class AnonymousSubstructTemplateNameT(
  // This happens to be the same thing that appears before this AnonymousSubstructNameT in a FullNameT.
  // This is really only here to help us calculate the imprecise name for this thing.
  interface: ICitizenTemplateNameT
) extends ICitizenTemplateNameT {



  interface match {
    case AnonymousSubstructTemplateNameT(_) => vwat()
    case _ =>
  }

  override def makeCitizenName(interner: Interner, templateArgs: Vector[ITemplata]): ICitizenNameT = {
    interner.intern(AnonymousSubstructNameT(this, templateArgs))
  }
}
case class AnonymousSubstructConstructorTemplateNameT(
  substruct: ICitizenTemplateNameT
) extends IFunctionTemplateNameT {
  override def makeFunctionName(interner: Interner, templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    interner.intern(AnonymousSubstructConstructorNameT(templateArgs, params))
  }
}

case class AnonymousSubstructConstructorNameT(
  templateArgs: Vector[ITemplata],
  parameters: Vector[CoordT]
) extends IFunctionNameT

case class AnonymousSubstructNameT(
  // This happens to be the same thing that appears before this AnonymousSubstructNameT in a FullNameT.
  // This is really only here to help us calculate the imprecise name for this thing.
  template: AnonymousSubstructTemplateNameT,
  templateArgs: Vector[ITemplata]
) extends ICitizenNameT {

}
case class AnonymousSubstructImplNameT() extends INameT {

}
