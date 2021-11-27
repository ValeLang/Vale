package net.verdagon.vale.astronomer

import net.verdagon.vale.scout.{ICitizenDeclarationNameS, IFunctionDeclarationNameS, IImpreciseNameS, INameS, IRuneS, TopLevelCitizenDeclarationNameS}
import net.verdagon.vale.{CodeLocationS, PackageCoordinate, vassert, vimpl, vpass}

// Only made by templar, see if we can take these out
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

case class ImplImpreciseNameS(structImpreciseName: IImpreciseNameS) extends IImpreciseNameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

// See NSIDN for why we need this virtual name
case class VirtualFreeImpreciseNameS() extends IImpreciseNameS { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class VirtualFreeDeclarationNameS(codeLoc: CodeLocationS) extends IFunctionDeclarationNameS {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def getImpreciseName: IImpreciseNameS = VirtualFreeImpreciseNameS()
  override def packageCoordinate: PackageCoordinate = codeLoc.file.packageCoordinate
}
