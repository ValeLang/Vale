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
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  // PackageTopLevelName2 is just here because names have to have a last step.
  vassert(!initSteps.contains(PackageTopLevelNameT()))

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
  def init: FullNameT[INameT] = {
    if (initSteps.isEmpty) {
      if (last == PackageTopLevelNameT()) {
        vimpl()
      } else {
        FullNameT(packageCoord, Vector(), PackageTopLevelNameT())
      }
    } else {
      FullNameT(packageCoord, initSteps.init, initSteps.last)
    }
  }

  def parent: Option[FullNameT[INameT]] = {
    if (initSteps.isEmpty) {
      packageCoord.parent match {
        case None => None
        case Some(parentPackage) => Some(FullNameT(parentPackage, Vector(), PackageTopLevelNameT()))
      }
    } else {
      Some(FullNameT(packageCoord, initSteps.init, initSteps.last))
    }
  }

  def selfAndParents: List[FullNameT[INameT]] = {
    parent match {
      case None => List(this)
      case Some(parent) => this :: parent.selfAndParents
    }
  }

  def parents: List[FullNameT[INameT]] = {
    parent match {
      case None => List()
      case Some(parent) => parent.selfAndParents
    }
  }
}
// not sure if we need imprecise names in templar
//// An imprecise name is one where we don't know exactly where the thing is defined.
//// For example, in
////   fn main() int export {
////     doStuff("hello");
////   }
//// we don't know exactly where doStuff was defined, that depends on what overload the
//// typing stage decides.
//case class ImpreciseName2[+T <: IImpreciseNameStep2](init: Vector[IImpreciseNameStep2], last: T) {//extends IImpreciseNameS[T] {
//  def addStep[Y <: IImpreciseNameStep2](newLast: Y): ImpreciseName2[Y] = ImpreciseName2[Y](init :+ last, newLast)
//}

sealed trait INameT  {
  def order: Int
}
sealed trait ITemplateNameT extends INameT
sealed trait IFunctionTemplateNameT extends ITemplateNameT {
  def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT
}
sealed trait IFunctionNameT extends INameT {
  def templateArgs: Vector[ITemplata]
  def parameters: Vector[CoordT]
}
sealed trait ICitizenTemplateNameT extends ITemplateNameT {
  def makeCitizenName(templateArgs: Vector[ITemplata]): ICitizenNameT
}
sealed trait ICitizenNameT extends INameT {
  def template: ICitizenTemplateNameT
  def templateArgs: Vector[ITemplata]
}
case class ImplDeclareNameT(codeLocation: CodeLocationS) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 1;  }
case class LetNameT(codeLocation: CodeLocationS) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 2;  }
case class ExportAsNameT(codeLocation: CodeLocationS) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 2;  }

case class RawArrayNameT(mutability: MutabilityT, elementType: CoordT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 40;  }
case class StaticSizedArrayNameT(size: Int, arr: RawArrayNameT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 42;  }
case class RuntimeSizedArrayNameT(arr: RawArrayNameT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 47;  }
sealed trait IVarNameT extends INameT
case class TemplarBlockResultVarNameT(life: LocationInFunctionEnvironment) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 18;  }
case class TemplarFunctionResultVarNameT() extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 19;  }
case class TemplarTemporaryVarNameT(life: LocationInFunctionEnvironment) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 20;  }
case class TemplarPatternMemberNameT(life: LocationInFunctionEnvironment) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 23;  }
case class TemplarIgnoredParamNameT(num: Int) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 53;  }
case class TemplarPatternDestructureeNameT(life: LocationInFunctionEnvironment) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 23;  }
case class UnnamedLocalNameT(codeLocation: CodeLocationS) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 3;  }
case class ClosureParamNameT() extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 41;  }
case class ConstructingMemberNameT(name: String) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 4;  }
case class MagicParamNameT(codeLocation2: CodeLocationS) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 5;  }
case class CodeVarNameT(name: String) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 6;  }
// We dont use CodeVarName2(0), CodeVarName2(1) etc because we dont want the user to address these members directly.
case class AnonymousSubstructMemberNameT(index: Int) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 24;  }
case class PrimitiveNameT(humanName: String) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 26;  }
// Only made in templar
case class PackageTopLevelNameT() extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 25;  }
case class ProjectNameT(name: String) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 51;  }
case class PackageNameT(name: String) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 52;  }
case class RuneNameT(rune: IRuneS) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 52;  }

// We use this one to look for impls, which are disambiguated by the above ImplDeclareName2
//case class ImplImpreciseName2() extends IName2 { def order = 22; def all[T](func: PartialFunction[Queriable2, T]): Vector[T] = { Vector(this).collect(func) } }

// This is the name of a function that we're still figuring out in the function templar.
// We have its closured variables, but are still figuring out its template args and params.
case class BuildingFunctionNameWithClosuredsT(
  templateName: IFunctionTemplateNameT,
) extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 33;

}
// This is the name of a function that we're still figuring out in the function templar.
// We have its closured variables and template args, but are still figuring out its params.
case class BuildingFunctionNameWithClosuredsAndTemplateArgsT(
  templateName: IFunctionTemplateNameT,
  templateArgs: Vector[ITemplata]
) extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 37;

}

//// We dont just use "destructor" as the name because we don't want the user to override it.
//case class ImmConcreteDestructorNameT(kind: KindT) extends IFunctionNameT {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  override def templateArgs: Vector[ITemplata] = Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, kind)))
//  override def parameters: Vector[CoordT] = Vector(CoordT(ShareT, ReadonlyT, kind))
//
//  kind match {
//    case InterfaceTT(_) => vwat()
//    case _ =>
//  }
//
//  def order = 38;
//
//}
// We dont just use "idestructor" as the name because we don't want the user to override it.
//case class ImmInterfaceDestructorNameT(
//    templateArgs: Vector[ITemplata],
//    parameters: Vector[CoordT]
//) extends IFunctionNameT {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  def order = 38;
//
//}
// We dont just use "drop" as the name because we don't want the user to override it.
//case class DropNameT(templateArgs: Vector[ITemplata], coord: CoordT) extends IFunctionNameT {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
////  override def templateArgs: Vector[ITemplata] = Vector()
//  override def parameters: Vector[CoordT] = Vector(coord)
//  def order = 39;
//}


case class ExternFunctionNameT(
  humanName: String,
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def templateArgs: Vector[ITemplata] = Vector.empty

  def order = 46;

}

case class FunctionNameT(
  humanName: String,
  templateArgs: Vector[ITemplata],
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  def order = 13;
}

case class FunctionTemplateNameT(
    humanName: String,
    codeLocation: CodeLocationS
) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 31;

  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    FunctionNameT(humanName, templateArgs, params)
  }
}

//case class FreeTemplateNameT(
//  codeLocation: CodeLocationS
//) extends INameT with IFunctionTemplateNameT {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  def order = 31;
//  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
//    FreeNameT(codeLocation, templateArgs, params)
//  }
//}
//
//case class FreeNameT(
//  codeLocation: CodeLocationS,
//  templateArgs: Vector[ITemplata],
//  parameters: Vector[CoordT]
//) extends INameT with IFunctionNameT {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  def order = 31;
//}

case class LambdaTemplateNameT(
  codeLocation: CodeLocationS
) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 36;

  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    FunctionNameT(CallTemplar.CALL_FUNCTION_NAME, templateArgs, params)
  }

}
case class ConstructorTemplateNameT(
  codeLocation: CodeLocationS
) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 35;

  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = vimpl()
}

case class FreeTemplateNameT(codeLoc: CodeLocationS) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 43;
  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    val Vector(CoordT(ShareT, ReadonlyT, kind)) = params
    FreeNameT(templateArgs, kind)
  }
}
case class FreeNameT(templateArgs: Vector[ITemplata], kind: KindT) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 43;
  override def parameters: Vector[CoordT] = Vector(CoordT(ShareT, ReadonlyT, kind))
}

// See NSIDN for why we have these virtual names
case class VirtualFreeTemplateNameT(codeLoc: CodeLocationS) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 43;
  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    val Vector(CoordT(ShareT, ReadonlyT, kind)) = params
    VirtualFreeNameT(templateArgs, kind)
  }
}
// See NSIDN for why we have these virtual names
case class VirtualFreeNameT(templateArgs: Vector[ITemplata], param: KindT) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 43;
  override def parameters: Vector[CoordT] = Vector(CoordT(ShareT, ReadonlyT, param))
}

//case class ImmInterfaceDestructorTemplateNameT() extends INameT with IFunctionTemplateNameT {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  def order = 44;
//
//}
//case class DropTemplateNameT() extends INameT with IFunctionTemplateNameT {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  def order = 45;
//}
// Vale has no Self, its just a convenient first name parameter.
// See also SelfNameS.
case class SelfNameT() extends IVarNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 55;
}
case class ArbitraryNameT() extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 56;
}
case class ConstructorNameT(
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 21;
  def templateArgs: Vector[ITemplata] = Vector.empty

}
//// We have this and LambdaCitizenName2 both because sometimes lambdas dont come with
//// a struct, like if they capture nothing. When they do come with structs, theyll both
//// be in the name, this one after the LambdaCitizenName2 name.
//case class LambdaName2(
//  codeLocation: CodeLocation2,
//  templateArgs: Vector[ITemplata],
//  parameters: Vector[Coord]
//) extends IFunctionName2 {
//  def order = 14;
//  def all[T](func: PartialFunction[Queriable2, T]): Vector[T] = {
//    Vector(this).collect(func) ++ templateArgs.flatMap(_.all(func)) ++ parameters.flatMap(_.all(func))
//  }
//}
//case class CitizenName2(
//  humanName: String,
//  templateArgs: Vector[ITemplata]
//) extends ICitizenName2 {
//  def order = 15;
//  def all[T](func: PartialFunction[Queriable2, T]): Vector[T] = {
//    Vector(this).collect(func) ++ templateArgs.flatMap(_.all(func))
//  }
//}
case class CitizenNameT(
  template: ICitizenTemplateNameT,
  templateArgs: Vector[ITemplata]
) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 15;

  vpass()
}
//case class TupleNameT(
//  members: Vector[CoordT]
//) extends ICitizenNameT {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  vpass()
//  override def templateArgs: Vector[ITemplata] = members.map(CoordTemplata)
//  def order = 16;
//
//}
case class LambdaCitizenTemplateNameT(
  codeLocation: CodeLocationS
) extends ICitizenTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()

  def templateArgs: Vector[ITemplata] = Vector.empty
  def order = 17;

  override def makeCitizenName(templateArgs: Vector[ITemplata]): ICitizenNameT = {
    vassert(templateArgs.isEmpty)
    LambdaCitizenNameT(this)
  }
}
case class LambdaCitizenNameT(
  template: LambdaCitizenTemplateNameT
) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()

  def templateArgs: Vector[ITemplata] = Vector.empty
  def order = 17;

}
case class AnonymousSubstructLambdaTemplateNameT(
  codeLocation: CodeLocationS
) extends ICitizenTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()

  def templateArgs: Vector[ITemplata] = Vector.empty
  def order = 54;

  override def makeCitizenName(templateArgs: Vector[ITemplata]): ICitizenNameT = {
    vassert(templateArgs.isEmpty)
    AnonymousSubstructLambdaNameT(this)
  }
}
case class AnonymousSubstructLambdaNameT(
  template: AnonymousSubstructLambdaTemplateNameT
) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()

  def templateArgs: Vector[ITemplata] = Vector.empty
  def order = 54;

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
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 30;

  override def makeCitizenName(templateArgs: Vector[ITemplata]): ICitizenNameT = {
    CitizenNameT(this, templateArgs)
  }
}
case class AnonymousSubstructTemplateNameT(
  // This happens to be the same thing that appears before this AnonymousSubstructNameT in a FullNameT.
  // This is really only here to help us calculate the imprecise name for this thing.
  interface: ICitizenTemplateNameT
) extends ICitizenTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 27;

  interface match {
    case AnonymousSubstructTemplateNameT(_) => vwat()
    case _ =>
  }

  override def makeCitizenName(templateArgs: Vector[ITemplata]): ICitizenNameT = {
    AnonymousSubstructNameT(this, templateArgs)
  }
}
case class AnonymousSubstructConstructorTemplateNameT(
  substruct: ICitizenTemplateNameT
) extends IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 27;

  override def makeFunctionName(templateArgs: Vector[ITemplata], params: Vector[CoordT]): IFunctionNameT = {
    AnonymousSubstructConstructorNameT(templateArgs, params)
  }
}
case class AnonymousSubstructConstructorNameT(
  templateArgs: Vector[ITemplata],
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 27;
}
case class AnonymousSubstructNameT(
  // This happens to be the same thing that appears before this AnonymousSubstructNameT in a FullNameT.
  // This is really only here to help us calculate the imprecise name for this thing.
  template: AnonymousSubstructTemplateNameT,
  templateArgs: Vector[ITemplata]
) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 27;
//  def templateArgs: Vector[ITemplata] = callables.map(CoordTemplata)
}
case class AnonymousSubstructImplNameT() extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 29;

}
//// This one is probably only used by the templar, so we can have a way to
//// figure out the closure struct for a certain environment.
//case class EnvClosureName2() extends IName2 {
//  def order = 32;
//  def all[T](func: PartialFunction[Queriable2, T]): Vector[T] = {
//    Vector(this).collect(func)
//  }
//}

// This is an IName2 because we put these into the environment.
// We don't just reuse INameA because there are some templar-specific ones.
//case class CodeRuneS(name: String) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 7;  }
//case class ImplicitRuneS(parentName: INameT, name: Int) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 8;  }
//case class LetImplicitRuneS(codeLocation: CodeLocationT, name: Int) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 34;  }
//case class ArraySizeImplicitRuneS() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 48;  }
//case class ArrayVariabilityImplicitRuneS() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 49;  }
//case class ArrayMutabilityImplicitRuneS() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 50;  }
//case class MemberRuneS(memberIndex: Int) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 9;  }
//case class MagicImplicitRuneS(scoutPath: Array[Int]) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 10;  }
//case class ReturnRuneS() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 11;  }
//case class SolverKindRuneS(paramRune: IRuneS) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 12;  }
//case class ExplicitTemplateArgRuneS(index: Int) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 34;  }
//
//case class AnonymousSubstructParentInterfaceRuneS() extends IRuneS {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  def order = 28;
//
//}

//
//sealed trait IImpreciseNameStep2
//case class CodeTypeName2(name: String) extends IImpreciseNameStep2 { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//// When we're calling a function, we're addressing an overload set, not a specific function.
//// If we want a specific function, we use TopLevelDeclarationNameS.
//case class GlobalFunctionFamilyName2(name: String) extends IImpreciseNameStep2 { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ImpreciseCodeVarName2(name: String) extends IImpreciseNameStep2 { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
