package net.verdagon.vale.astronomer

import net.verdagon.vale.scout.{ICitizenDeclarationNameS, IFunctionDeclarationNameS, IImpreciseNameS, INameS, IRuneS, TopLevelCitizenDeclarationNameS}
import net.verdagon.vale.{CodeLocationS, PackageCoordinate, vassert, vimpl, vpass}

//// An absolute name is one where we know *exactly* where it's defined; if parser and scout
//// put their brains together they could know exactly where the thing is.
//case class AbsoluteNameA[+T <: INameA](file: String, initSteps: Vector[INameA], last: T) {// extends IImpreciseNameA[T] {
//def addStep[Y <: INameA](newLast: Y): AbsoluteNameA[Y] = AbsoluteNameA[Y](file, initSteps :+ last, newLast)
//  def steps: Vector[INameA] = initSteps :+ last
//  def init: AbsoluteNameA[INameA] = AbsoluteNameA[INameA](file, initSteps.init, initSteps.last)
//}
//// An imprecise name is one where we don't know exactly where the thing is defined.
//// For example, in
////   fn main() int export {
////     doStuff("hello");
////   }
//// we don't know exactly where doStuff was defined, that depends on what overload the
//// typing stage decides.
//case class ImpreciseNameA[+T <: INameS](init: Vector[INameS], last: T) {//extends IImpreciseNameS[T] {
//def addStep[Y <: INameS](newLast: Y): ImpreciseNameA[Y] = ImpreciseNameA[Y](init :+ last, newLast)
//}

//sealed trait INameA
//sealed trait IVarNameA extends INameA
//sealed trait ITypeDeclarationNameA extends INameA {
//  def packageCoordinate: PackageCoordinate
//}
//sealed trait IFunctionDeclarationNameS extends INameS {
//  def packageCoordinate: PackageCoordinate
//}
//case class LambdaNameS(codeLocation: CodeLocationS) extends IFunctionDeclarationNameA {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  override def packageCoordinate: PackageCoordinate = codeLocation.file.packageCoordinate
//}
//case class FunctionNameA(name: String, codeLocation: CodeLocationS) extends IFunctionDeclarationNameA {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  override def packageCoordinate: PackageCoordinate = codeLocation.file.packageCoordinate
//}
//case class TopLevelCitizenDeclarationNameS(name: String, codeLocation: CodeLocationS) extends ITypeDeclarationNameA {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  override def packageCoordinate: PackageCoordinate = codeLocation.file.packageCoordinate
//}
//case class LambdaStructNameA(lambdaName: LambdaNameS) extends ITypeDeclarationNameA {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  override def packageCoordinate: PackageCoordinate = lambdaName.codeLocation.file.packageCoordinate
//}
//case class ImplNameA(subCitizenHumanName: String, codeLocation: CodeLocationS) extends INameA {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  def packageCoordinate: PackageCoordinate = codeLocation.file.packageCoordinate
//}
//case class LetNameA(codeLocation: CodeLocationS) extends INameA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class UnnamedLocalNameA(codeLocation: CodeLocationS) extends IVarNameA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ClosureParamNameA() extends IVarNameA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ConstructingMemberNameA(name: String) extends IVarNameA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class AnonymousSubstructMemberNameA(index: Int) extends IVarNameA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class MagicParamNameA(codeLocation: CodeLocationS) extends IVarNameA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ExportAsNameA(codeLocation: CodeLocationS) extends IVarNameA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class CodeVarNameA(name: String) extends IVarNameA { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//// Only made by templar, see if we can take these out
case class ConstructorNameS(tlcd: ICitizenDeclarationNameS) extends IFunctionDeclarationNameS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def packageCoordinate: PackageCoordinate = tlcd.range.begin.file.packageCoordinate
  override def getImpreciseName: IImpreciseNameS = tlcd.getImpreciseName
}
case class ImmConcreteDestructorNameS(packageCoordinate: PackageCoordinate) extends IFunctionDeclarationNameS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def getImpreciseName: IImpreciseNameS = vimpl()
}
case class ImmInterfaceDestructorNameS(packageCoordinate: PackageCoordinate) extends IFunctionDeclarationNameS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def getImpreciseName: IImpreciseNameS = vimpl()
}
//
//sealed trait IRuneS extends INameA with IRuneS

//case class CodeRuneA(name: String) extends IRuneS {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  vassert(name != "str")
//}
//case class ImplicitRuneA(containerName: INameA, name: Int) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ImplicitRuneFromScoutA(scoutPath: Array[Int]) extends IRuneS {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  override def toString: String = "ImplicitRuneFromScoutA#([" + scoutPath.mkString(",") + "])"
//}
//case class ArraySizeImplicitRuneA() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ArrayVariabilityImplicitRuneA() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ArrayMutabilityImplicitRuneA() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class LetImplicitRuneA(codeLocationS: CodeLocationS, name: Int) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class MemberRuneA(memberIndex: Int) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class MagicImplicitRuneA(scoutPath: Array[Int]) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ReturnRuneA() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//// Only made by templar, see if we can take these out
//case class AnonymousSubstructParentInterfaceRuneA() extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ExplicitTemplateArgRuneA(index: Int) extends IRuneS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

//sealed trait INameS
//case class CodeTypeNameA(name: String) extends INameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//// When we're calling a function, we're addressing an overload set, not a specific function.
//// If we want a specific function, we use TopLevelDeclarationNameS.
//case class GlobalFunctionFamilyNameS(name: String) extends INameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class ImpreciseCodeVarNameA(name: String) extends INameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ImplImpreciseNameS(structImpreciseName: IImpreciseNameS) extends IImpreciseNameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class FreeImpreciseNameS() extends IImpreciseNameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class FreeImpreciseNameS() extends IImpreciseNameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
//case class FreeDeclarationNameS(codeLoc: CodeLocationS) extends IFunctionDeclarationNameS {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  override def getImpreciseName: IImpreciseNameS = FreeImpreciseNameS()
//  override def packageCoordinate: PackageCoordinate = codeLoc.file.packageCoordinate
//}

// See NSIDN for why we need this virtual name
case class VirtualFreeImpreciseNameS() extends IImpreciseNameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class VirtualFreeDeclarationNameS(codeLoc: CodeLocationS) extends IFunctionDeclarationNameS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def getImpreciseName: IImpreciseNameS = VirtualFreeImpreciseNameS()
  override def packageCoordinate: PackageCoordinate = codeLoc.file.packageCoordinate
}


//case class DropImpreciseNameS() extends INameS