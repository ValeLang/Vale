package net.verdagon.vale.astronomer

import net.verdagon.vale.scout.{ICitizenDeclarationNameS, IFunctionDeclarationNameS, IImpreciseNameS, INameS, IRuneS, TopLevelCitizenDeclarationNameS}
import net.verdagon.vale.{CodeLocationS, Interner, PackageCoordinate, vassert, vcurious, vimpl, vpass}

// Only made by templar, see if we can take these out
case class ConstructorNameS(tlcd: ICitizenDeclarationNameS) extends IFunctionDeclarationNameS {
  override def packageCoordinate: PackageCoordinate = tlcd.range.begin.file.packageCoordinate
  override def getImpreciseName(interner: Interner): IImpreciseNameS = tlcd.getImpreciseName(interner)
}
case class ImmConcreteDestructorNameS(packageCoordinate: PackageCoordinate) extends IFunctionDeclarationNameS {
  override def getImpreciseName(interner: Interner): IImpreciseNameS = vimpl()
}
case class ImmInterfaceDestructorNameS(packageCoordinate: PackageCoordinate) extends IFunctionDeclarationNameS {
  override def getImpreciseName(interner: Interner): IImpreciseNameS = vimpl()
}

case class ImplImpreciseNameS(structImpreciseName: IImpreciseNameS) extends IImpreciseNameS { }

// See NSIDN for why we need this virtual name
case class VirtualFreeImpreciseNameS() extends IImpreciseNameS { }
case class VirtualFreeDeclarationNameS(codeLoc: CodeLocationS) extends IFunctionDeclarationNameS {
  override def getImpreciseName(interner: Interner): IImpreciseNameS = interner.intern(VirtualFreeImpreciseNameS())
  override def packageCoordinate: PackageCoordinate = codeLoc.file.packageCoordinate
}
