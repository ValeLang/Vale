package net.verdagon.vale.templar

import net.verdagon.vale.scout.CodeLocationS
import net.verdagon.vale.templar.templata.{CodeLocationT, CoordTemplata, ITemplata, QueriableT}
import net.verdagon.vale.templar.types.{CoordT, IntT, InterfaceTT, KindT, StaticSizedArrayTT, MutabilityT, ReadonlyT, ShareT, StructTT, RuntimeSizedArrayTT}
import net.verdagon.vale.{PackageCoordinate, vassert, vfail, vpass, vwat}

import scala.collection.immutable.List

// Scout's/Astronomer's name parts correspond to where they are in the source code,
// but Templar's correspond more to what packages and stamped functions / structs
// they're in. See TNAD.

case class FullNameT[+T <: INameT](
  packageCoord: PackageCoordinate,
  initSteps: Vector[INameT],
  last: T
) extends QueriableT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  // PackageTopLevelName2 is just here because names have to have a last step.
  vassert(!initSteps.contains(PackageTopLevelNameT()))

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
  def init: FullNameT[INameT] = FullNameT[INameT](packageCoord, initSteps.init, initSteps.last)

  def all[X](func: PartialFunction[QueriableT, X]): Vector[X] = {
    Vector(this).collect(func) ++ initSteps.flatMap(_.all(func)) ++ last.all(func)
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

sealed trait INameT extends QueriableT {
  def order: Int
}
sealed trait IFunctionNameT extends INameT {
  def templateArgs: Vector[ITemplata]
  def parameters: Vector[CoordT]
}
sealed trait ICitizenNameT extends INameT {
  def templateArgs: Vector[ITemplata]
}
case class ImplDeclareNameT(subCitizenHumanName: String, codeLocation: CodeLocationT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 1; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) ++ codeLocation.all(func) } }
case class LetNameT(codeLocation: CodeLocationT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 2; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) ++ codeLocation.all(func) } }
case class ExportAsNameT(codeLocation: CodeLocationT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 2; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) ++ codeLocation.all(func) } }

case class RawArrayNameT(mutability: MutabilityT, elementType: CoordT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 40; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) ++ elementType.all(func) } }
case class StaticSizedArrayNameT(size: Int, arr: RawArrayNameT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 42; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) ++ arr.all(func) } }
case class RuntimeSizedArrayNameT(arr: RawArrayNameT) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 47; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) ++ arr.all(func) } }
sealed trait IVarNameT extends INameT
case class TemplarBlockResultVarNameT(num: Int) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 18; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class TemplarFunctionResultVarNameT() extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 19; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class TemplarTemporaryVarNameT(num: Int) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 20; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class TemplarPatternMemberNameT(num: Int, memberIndex: Int) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 23; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class TemplarIgnoredParamNameT(num: Int) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 53; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class TemplarPatternDestructureeNameT(num: Int) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 23; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class UnnamedLocalNameT(codeLocation: CodeLocationT) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 3; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) ++ codeLocation.all(func) } }
case class ClosureParamNameT() extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 41; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class ConstructingMemberNameT(name: String) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 4; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class MagicParamNameT(codeLocation2: CodeLocationT) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 5; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class CodeVarNameT(name: String) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 6; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
// We dont use CodeVarName2(0), CodeVarName2(1) etc because we dont want the user to address these members directly.
case class AnonymousSubstructMemberNameT(index: Int) extends IVarNameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 24; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class PrimitiveNameT(humanName: String) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 26; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
// Only made in templar
case class PackageTopLevelNameT() extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 25; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class ProjectNameT(name: String) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 51; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class PackageNameT(name: String) extends INameT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 52; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }

// We use this one to look for impls, which are disambiguated by the above ImplDeclareName2
//case class ImplImpreciseName2() extends IName2 { def order = 22; def all[T](func: PartialFunction[Queriable2, T]): Vector[T] = { Vector(this).collect(func) } }

// This is the name of a function that we're still figuring out in the function templar.
// We have its closured variables, but are still figuring out its template args and params.
case class BuildingFunctionNameWithClosuredsT(
  templateName: IFunctionTemplateNameT,
) extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 33;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ templateName.all(func)
  }
}
// This is the name of a function that we're still figuring out in the function templar.
// We have its closured variables and template args, but are still figuring out its params.
case class BuildingFunctionNameWithClosuredsAndTemplateArgsT(
  templateName: IFunctionTemplateNameT,
  templateArgs: Vector[ITemplata]
) extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 37;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ templateName.all(func) ++ templateArgs.flatMap(_.all(func))
  }
}

// We dont just use "destructor" as the name because we don't want the user to override it.
case class ImmConcreteDestructorNameT(kind: KindT) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def templateArgs: Vector[ITemplata] = Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, kind)))
  override def parameters: Vector[CoordT] = Vector(CoordT(ShareT, ReadonlyT, kind))

  kind match {
    case InterfaceTT(_) => vwat()
    case _ =>
  }

  def order = 38;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ templateArgs.flatMap(_.all(func)) ++ parameters.flatMap(_.all(func))
  }
}
// We dont just use "idestructor" as the name because we don't want the user to override it.
case class ImmInterfaceDestructorNameT(
    templateArgs: Vector[ITemplata],
    parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 38;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ templateArgs.flatMap(_.all(func)) ++ parameters.flatMap(_.all(func))
  }
}
// We dont just use "drop" as the name because we don't want the user to override it.
case class ImmDropNameT(kind: KindT) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def templateArgs: Vector[ITemplata] = Vector(CoordTemplata(CoordT(ShareT, ReadonlyT, kind)))
  override def parameters: Vector[CoordT] = Vector(CoordT(ShareT, ReadonlyT, kind))

  def order = 39;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ templateArgs.flatMap(_.all(func)) ++ parameters.flatMap(_.all(func))
  }
}


case class ExternFunctionNameT(
  humanName: String,
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def templateArgs: Vector[ITemplata] = Vector.empty

  def order = 46;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ templateArgs.flatMap(_.all(func)) ++ parameters.flatMap(_.all(func))
  }
}

case class FunctionNameT(
  humanName: String,
  templateArgs: Vector[ITemplata],
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  def order = 13;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ templateArgs.flatMap(_.all(func)) ++ parameters.flatMap(_.all(func))
  }
}
sealed trait IFunctionTemplateNameT extends INameT

case class FunctionTemplateNameT(
    humanName: String,
    codeLocation: CodeLocationT
) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 31;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ codeLocation.all(func)
  }
}
case class LambdaTemplateNameT(
  codeLocation: CodeLocationT
) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 36;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ codeLocation.all(func)
  }
}
case class ConstructorTemplateNameT(
  codeLocation: CodeLocationT
) extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 35;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ codeLocation.all(func)
  }
}
case class ImmConcreteDestructorTemplateNameT() extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 43;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}
case class ImmInterfaceDestructorTemplateNameT() extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 44;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}
case class ImmDropTemplateNameT() extends INameT with IFunctionTemplateNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 45;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}
case class ConstructorNameT(
  parameters: Vector[CoordT]
) extends IFunctionNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 21;
  def templateArgs: Vector[ITemplata] = Vector.empty
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
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
  humanName: String,
  templateArgs: Vector[ITemplata]
) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 15;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ templateArgs.flatMap(_.all(func))
  }
}
case class TupleNameT(
  members: Vector[CoordT]
) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()
  override def templateArgs: Vector[ITemplata] = members.map(CoordTemplata)
  def order = 16;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ members.flatMap(_.all(func))
  }
}
case class LambdaCitizenNameT(
  codeLocation: CodeLocationT,
) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()

  def templateArgs: Vector[ITemplata] = Vector.empty
  def order = 17;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ templateArgs.toVector.flatMap(_.all(func))
  }
}
case class CitizenTemplateNameT(
  humanName: String,
  codeLocation: CodeLocationT
) extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 30;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ codeLocation.all(func)
  }

  def makeCitizenName(templateArgs: Vector[ITemplata]): CitizenNameT = {
    CitizenNameT(humanName, templateArgs)
  }
}
case class AnonymousSubstructNameT(callables: Vector[CoordT]) extends ICitizenNameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 27;
  def templateArgs: Vector[ITemplata] = callables.map(CoordTemplata)
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ templateArgs.toVector.flatMap(_.all(func))
  }
}
case class AnonymousSubstructImplNameT() extends INameT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 29;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
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
sealed trait IRuneT extends INameT
case class CodeRuneT(name: String) extends IRuneT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 7; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class ImplicitRuneT(parentName: INameT, name: Int) extends IRuneT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 8; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class LetImplicitRuneT(codeLocation: CodeLocationT, name: Int) extends IRuneT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 34; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class ArraySizeImplicitRuneT() extends IRuneT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 48; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class ArrayVariabilityImplicitRuneT() extends IRuneT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 49; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class ArrayMutabilityImplicitRuneT() extends IRuneT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 50; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class MemberRuneT(memberIndex: Int) extends IRuneT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 9; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class MagicImplicitRuneT(codeLocation: CodeLocationT) extends IRuneT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 10; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class ReturnRuneT() extends IRuneT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 11; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }
case class SolverKindRuneT(paramRune: IRuneT) extends IRuneT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 12; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) ++ paramRune.all(func) } }
case class ExplicitTemplateArgRuneT(index: Int) extends IRuneT { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; def order = 34; def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = { Vector(this).collect(func) } }

case class AnonymousSubstructParentInterfaceRuneT() extends IRuneT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  def order = 28;
  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}

//
//sealed trait IImpreciseNameStep2
//case class CodeTypeName2(name: String) extends IImpreciseNameStep2 { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//// When we're calling a function, we're addressing an overload set, not a specific function.
//// If we want a specific function, we use TopLevelDeclarationNameS.
//case class GlobalFunctionFamilyName2(name: String) extends IImpreciseNameStep2 { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ImpreciseCodeVarName2(name: String) extends IImpreciseNameStep2 { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
