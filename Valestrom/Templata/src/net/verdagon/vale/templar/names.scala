package net.verdagon.vale.templar

import net.verdagon.vale.scout.CodeLocationS
import net.verdagon.vale.templar.templata.{CodeLocation2, CoordTemplata, ITemplata, Queriable2}
import net.verdagon.vale.templar.types.{Coord, InterfaceRef2, Kind, KnownSizeArrayT2, Mutability, Share, StructRef2, UnknownSizeArrayT2}
import net.verdagon.vale.{vassert, vwat}

// Scout's/Astronomer's name parts correspond to where they are in the source code,
// but Templar's correspond more to what namespaces and stamped functions / structs
// they're in. See TNAD.

case class FullName2[+T <: IName2](initSteps: List[IName2], last: T) extends Queriable2 {
  // GlobalNamespaceName2 is just here because names have to have a last step.
  vassert(!initSteps.contains(GlobalNamespaceName2()))

  def steps: List[IName2] = {
    last match {
      case GlobalNamespaceName2() => initSteps
      case _ => initSteps :+ last
    }
  }
  def addStep[Y <: IName2](newLast: Y): FullName2[Y] = {
    FullName2[Y](steps, newLast)
  }
  def init: FullName2[IName2] = FullName2[IName2](initSteps.init, initSteps.last)

  def all[X](func: PartialFunction[Queriable2, X]): List[X] = {
    List(this).collect(func) ++ initSteps.flatMap(_.all(func)) ++ last.all(func)
  }
}
// not sure if we need imprecise names in templar
//// An imprecise name is one where we don't know exactly where the thing is defined.
//// For example, in
////   fn main() {
////     doStuff("hello");
////   }
//// we don't know exactly where doStuff was defined, that depends on what overload the
//// typing stage decides.
//case class ImpreciseName2[+T <: IImpreciseNameStep2](init: List[IImpreciseNameStep2], last: T) {//extends IImpreciseNameS[T] {
//  def addStep[Y <: IImpreciseNameStep2](newLast: Y): ImpreciseName2[Y] = ImpreciseName2[Y](init :+ last, newLast)
//}

sealed trait IName2 extends Queriable2 {
  def order: Int
}
sealed trait IFunctionName2 extends IName2 {
  def templateArgs: List[ITemplata]
  def parameters: List[Coord]
}
sealed trait ICitizenName2 extends IName2 {
  def templateArgs: List[ITemplata]
}
case class ImplDeclareName2(codeLocation: CodeLocation2) extends IName2 { def order = 1; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) ++ codeLocation.all(func) } }
case class LetName2(codeLocation: CodeLocation2) extends IName2 { def order = 2; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) ++ codeLocation.all(func) } }

case class RawArrayName2(mutability: Mutability, elementType: Coord) extends IName2 { def order = 40; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) ++ elementType.all(func) } }
case class KnownSizeArrayName2(size: Int, arr: RawArrayName2) extends IName2 { def order = 42; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) ++ arr.all(func) } }
case class UnknownSizeArrayName2(arr: RawArrayName2) extends IName2 { def order = 47; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) ++ arr.all(func) } }
sealed trait IVarName2 extends IName2
case class TemplarBlockResultVarName2(num: Int) extends IVarName2 { def order = 18; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class TemplarFunctionResultVarName2() extends IVarName2 { def order = 19; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class TemplarTemporaryVarName2(num: Int) extends IVarName2 { def order = 20; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class TemplarPatternMemberName2(num: Int, memberIndex: Int) extends IVarName2 { def order = 23; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class TemplarPatternDestructureeName2(num: Int) extends IVarName2 { def order = 23; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class UnnamedLocalName2(codeLocation: CodeLocation2) extends IVarName2 { def order = 3; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) ++ codeLocation.all(func) } }
case class ClosureParamName2() extends IVarName2 { def order = 41; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class ConstructingMemberName2(name: String) extends IVarName2 { def order = 4; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class MagicParamName2(codeLocation2: CodeLocation2) extends IVarName2 { def order = 5; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class CodeVarName2(name: String) extends IVarName2 { def order = 6; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
// We dont use CodeVarName2(0), CodeVarName2(1) etc because we dont want the user to address these members directly.
case class AnonymousSubstructMemberName2(index: Int) extends IVarName2 { def order = 24; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class PrimitiveName2(humanName: String) extends IName2 { def order = 26; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
// Only made in templar
case class GlobalNamespaceName2() extends IName2 { def order = 25; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }

// We use this one to look for impls, which are disambiguated by the above ImplDeclareName2
//case class ImplImpreciseName2() extends IName2 { def order = 22; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }

// This is the name of a function that we're still figuring out in the function templar.
// We have its closured variables, but are still figuring out its template args and params.
case class BuildingFunctionNameWithClosureds2(
  templateName: IFunctionTemplateName2,
) extends IName2 {
  def order = 33;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ templateName.all(func)
  }
}
// This is the name of a function that we're still figuring out in the function templar.
// We have its closured variables and template args, but are still figuring out its params.
case class BuildingFunctionNameWithClosuredsAndTemplateArgs2(
  templateName: IFunctionTemplateName2,
  templateArgs: List[ITemplata]
) extends IName2 {
  def order = 37;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ templateName.all(func) ++ templateArgs.flatMap(_.all(func))
  }
}

// We dont just use "destructor" as the name because we don't want the user to override it.
case class ImmConcreteDestructorName2(kind: Kind) extends IFunctionName2 {
  override def templateArgs: List[ITemplata] = List(CoordTemplata(Coord(Share, kind)))
  override def parameters: List[Coord] = List(Coord(Share, kind))

  kind match {
    case InterfaceRef2(_) => vwat()
    case _ =>
  }

  def order = 38;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ templateArgs.flatMap(_.all(func)) ++ parameters.flatMap(_.all(func))
  }
}
// We dont just use "idestructor" as the name because we don't want the user to override it.
case class ImmInterfaceDestructorName2(
    templateArgs: List[ITemplata],
    parameters: List[Coord]
) extends IFunctionName2 {
  def order = 38;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ templateArgs.flatMap(_.all(func)) ++ parameters.flatMap(_.all(func))
  }
}
// We dont just use "drop" as the name because we don't want the user to override it.
case class ImmDropName2(kind: Kind) extends IFunctionName2 {
  override def templateArgs: List[ITemplata] = List(CoordTemplata(Coord(Share, kind)))
  override def parameters: List[Coord] = List(Coord(Share, kind))

  def order = 39;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ templateArgs.flatMap(_.all(func)) ++ parameters.flatMap(_.all(func))
  }
}

case class FunctionName2(
  humanName: String,
  templateArgs: List[ITemplata],
  parameters: List[Coord]
) extends IFunctionName2 {

  def order = 13;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ templateArgs.flatMap(_.all(func)) ++ parameters.flatMap(_.all(func))
  }
}
sealed trait IFunctionTemplateName2 extends IName2

case class FunctionTemplateName2(
    humanName: String,
    codeLocation: CodeLocation2
) extends IName2 with IFunctionTemplateName2 {
  def order = 31;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ codeLocation.all(func)
  }
}
case class LambdaTemplateName2(
  codeLocation: CodeLocation2
) extends IName2 with IFunctionTemplateName2 {
  def order = 36;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ codeLocation.all(func)
  }
}
case class ConstructorTemplateName2(
  codeLocation: CodeLocation2
) extends IName2 with IFunctionTemplateName2 {
  def order = 35;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ codeLocation.all(func)
  }
}
case class ImmConcreteDestructorTemplateName2() extends IName2 with IFunctionTemplateName2 {
  def order = 43;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case class ImmInterfaceDestructorTemplateName2() extends IName2 with IFunctionTemplateName2 {
  def order = 44;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case class ImmDropTemplateName2() extends IName2 with IFunctionTemplateName2 {
  def order = 45;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case class ConstructorName2(
  parameters: List[Coord]
) extends IFunctionName2 {
  def order = 21;
  def templateArgs: List[ITemplata] = List()
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
//// We have this and LambdaCitizenName2 both because sometimes lambdas dont come with
//// a struct, like if they capture nothing. When they do come with structs, theyll both
//// be in the name, this one after the LambdaCitizenName2 name.
//case class LambdaName2(
//  codeLocation: CodeLocation2,
//  templateArgs: List[ITemplata],
//  parameters: List[Coord]
//) extends IFunctionName2 {
//  def order = 14;
//  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
//    List(this).collect(func) ++ templateArgs.flatMap(_.all(func)) ++ parameters.flatMap(_.all(func))
//  }
//}
//case class CitizenName2(
//  humanName: String,
//  templateArgs: List[ITemplata]
//) extends ICitizenName2 {
//  def order = 15;
//  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
//    List(this).collect(func) ++ templateArgs.flatMap(_.all(func))
//  }
//}
case class CitizenName2(
  humanName: String,
  templateArgs: List[ITemplata]
) extends ICitizenName2 {
  def order = 15;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ templateArgs.flatMap(_.all(func))
  }
}
case class TupleName2(
  members: List[Coord]
) extends ICitizenName2 {
  override def templateArgs: List[ITemplata] = members.map(CoordTemplata)
  def order = 16;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ members.flatMap(_.all(func))
  }
}
case class LambdaCitizenName2(
  codeLocation: CodeLocation2,
) extends ICitizenName2 {
  def templateArgs: List[ITemplata] = List()
  def order = 17;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ templateArgs.toList.flatMap(_.all(func))
  }
}
case class CitizenTemplateName2(
  humanName: String,
  codeLocation: CodeLocation2
) extends IName2 {
  def order = 30;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ codeLocation.all(func)
  }

  def makeCitizenName(templateArgs: List[ITemplata]): CitizenName2 = {
    CitizenName2(humanName, templateArgs)
  }
}
case class AnonymousSubstructName2(callables: List[Coord]) extends ICitizenName2 {
  def order = 27;
  def templateArgs: List[ITemplata] = callables.map(CoordTemplata)
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ templateArgs.toList.flatMap(_.all(func))
  }
}
case class AnonymousSubstructImplName2() extends IName2 {
  def order = 29;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
//// This one is probably only used by the templar, so we can have a way to
//// figure out the closure struct for a certain environment.
//case class EnvClosureName2() extends IName2 {
//  def order = 32;
//  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
//    List(this).collect(func)
//  }
//}

// This is an IName2 because we put these into the environment.
// We don't just reuse INameA because there are some templar-specific ones.
sealed trait IRune2 extends IName2
case class CodeRune2(name: String) extends IRune2 { def order = 7; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class ImplicitRune2(parentName: IName2, name: Int) extends IRune2 { def order = 8; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class LetImplicitRune2(codeLocation: CodeLocation2, name: Int) extends IRune2 { def order = 34; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class MemberRune2(memberIndex: Int) extends IRune2 { def order = 9; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class MagicImplicitRune2(codeLocation: CodeLocation2) extends IRune2 { def order = 10; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class ReturnRune2() extends IRune2 { def order = 11; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }
case class SolverKindRune2(paramRune: IRune2) extends IRune2 { def order = 12; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) ++ paramRune.all(func) } }
case class ExplicitTemplateArgRune2(index: Int) extends IRune2 { def order = 34; def all[T](func: PartialFunction[Queriable2, T]): List[T] = { List(this).collect(func) } }

case class AnonymousSubstructParentInterfaceRune2() extends IRune2 {
  def order = 28;
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}

//
//sealed trait IImpreciseNameStep2
//case class CodeTypeName2(name: String) extends IImpreciseNameStep2
//// When we're calling a function, we're addressing an overload set, not a specific function.
//// If we want a specific function, we use TopLevelDeclarationNameS.
//case class GlobalFunctionFamilyName2(name: String) extends IImpreciseNameStep2
//case class ImpreciseCodeVarName2(name: String) extends IImpreciseNameStep2
