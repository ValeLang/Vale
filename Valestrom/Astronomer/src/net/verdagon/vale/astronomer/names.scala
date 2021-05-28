package net.verdagon.vale.astronomer

import net.verdagon.vale.scout.CodeLocationS
import net.verdagon.vale.{PackageCoordinate, vassert, vpass}

//// An absolute name is one where we know *exactly* where it's defined; if parser and scout
//// put their brains together they could know exactly where the thing is.
//case class AbsoluteNameA[+T <: INameA](file: String, initSteps: List[INameA], last: T) {// extends IImpreciseNameA[T] {
//def addStep[Y <: INameA](newLast: Y): AbsoluteNameA[Y] = AbsoluteNameA[Y](file, initSteps :+ last, newLast)
//  def steps: List[INameA] = initSteps :+ last
//  def init: AbsoluteNameA[INameA] = AbsoluteNameA[INameA](file, initSteps.init, initSteps.last)
//}
//// An imprecise name is one where we don't know exactly where the thing is defined.
//// For example, in
////   fn main() int export {
////     doStuff("hello");
////   }
//// we don't know exactly where doStuff was defined, that depends on what overload the
//// typing stage decides.
//case class ImpreciseNameA[+T <: IImpreciseNameStepA](init: List[IImpreciseNameStepA], last: T) {//extends IImpreciseNameS[T] {
//def addStep[Y <: IImpreciseNameStepA](newLast: Y): ImpreciseNameA[Y] = ImpreciseNameA[Y](init :+ last, newLast)
//}

sealed trait INameA
sealed trait IVarNameA extends INameA
sealed trait ITypeDeclarationNameA extends INameA {
  def packageCoordinate: PackageCoordinate
}
sealed trait IFunctionDeclarationNameA extends INameA {
  def packageCoordinate: PackageCoordinate
}
case class LambdaNameA(codeLocation: CodeLocationS) extends IFunctionDeclarationNameA {
  override def packageCoordinate: PackageCoordinate = codeLocation.file.packageCoordinate
}
case class FunctionNameA(name: String, codeLocation: CodeLocationS) extends IFunctionDeclarationNameA {
  override def packageCoordinate: PackageCoordinate = codeLocation.file.packageCoordinate
}
case class TopLevelCitizenDeclarationNameA(name: String, codeLocation: CodeLocationS) extends ITypeDeclarationNameA {
  override def packageCoordinate: PackageCoordinate = codeLocation.file.packageCoordinate
}
case class LambdaStructNameA(lambdaName: LambdaNameA) extends ITypeDeclarationNameA {
  override def packageCoordinate: PackageCoordinate = lambdaName.codeLocation.file.packageCoordinate
}
case class ImplNameA(subCitizenHumanName: String, codeLocation: CodeLocationS) extends INameA {
  def packageCoordinate: PackageCoordinate = codeLocation.file.packageCoordinate
}
case class LetNameA(codeLocation: CodeLocationS) extends INameA
case class UnnamedLocalNameA(codeLocation: CodeLocationS) extends IVarNameA
case class ClosureParamNameA() extends IVarNameA
case class ConstructingMemberNameA(name: String) extends IVarNameA
case class AnonymousSubstructMemberNameA(index: Int) extends IVarNameA
case class MagicParamNameA(codeLocation: CodeLocationS) extends IVarNameA
case class ExportAsNameA(codeLocation: CodeLocationS) extends IVarNameA
case class CodeVarNameA(name: String) extends IVarNameA
// Only made by templar, see if we can take these out
case class ConstructorNameA(tlcd: TopLevelCitizenDeclarationNameA) extends IFunctionDeclarationNameA {
  override def packageCoordinate: PackageCoordinate = tlcd.codeLocation.file.packageCoordinate
}
case class ImmConcreteDestructorNameA(packageCoordinate: PackageCoordinate) extends IFunctionDeclarationNameA
case class ImmInterfaceDestructorNameA(packageCoordinate: PackageCoordinate) extends IFunctionDeclarationNameA
case class ImmDropNameA(packageCoordinate: PackageCoordinate) extends IFunctionDeclarationNameA

sealed trait IRuneA extends INameA
case class CodeRuneA(name: String) extends IRuneA {
  vassert(name != "str")
}
case class ImplicitRuneA(containerName: INameA, name: Int) extends IRuneA
case class ArraySizeImplicitRuneA() extends IRuneA
case class ArrayVariabilityImplicitRuneA() extends IRuneA
case class ArrayMutabilityImplicitRuneA() extends IRuneA
case class LetImplicitRuneA(codeLocationS: CodeLocationS, name: Int) extends IRuneA
case class MemberRuneA(memberIndex: Int) extends IRuneA
case class MagicImplicitRuneA(codeLocationS: CodeLocationS) extends IRuneA
case class ReturnRuneA() extends IRuneA
// Only made by templar, see if we can take these out
case class AnonymousSubstructParentInterfaceRuneA() extends IRuneA
case class ExplicitTemplateArgRuneA(index: Int) extends IRuneA

sealed trait IImpreciseNameStepA
case class CodeTypeNameA(name: String) extends IImpreciseNameStepA
// When we're calling a function, we're addressing an overload set, not a specific function.
// If we want a specific function, we use TopLevelDeclarationNameS.
case class GlobalFunctionFamilyNameA(name: String) extends IImpreciseNameStepA
case class ImpreciseCodeVarNameA(name: String) extends IImpreciseNameStepA
case class ImplImpreciseNameA(subCitizenHumanName: String) extends IImpreciseNameStepA
case class ImmConcreteDestructorImpreciseNameA() extends IImpreciseNameStepA
case class ImmInterfaceDestructorImpreciseNameA() extends IImpreciseNameStepA
case class ImmDropImpreciseNameA() extends IImpreciseNameStepA