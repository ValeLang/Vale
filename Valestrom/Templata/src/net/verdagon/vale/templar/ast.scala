package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{PackageCoordinate, vassert, vassertSome, vcurious, vfail, vimpl, vpass, vwat}

import scala.collection.immutable._
import scala.collection.mutable

// We won't always have a return type for a banner... it might have not specified its return
// type, so we're currently evaluating the entire body for it right now.
// If we ever find ourselves wanting the return type for a banner, we need to:
// - Check if it's in the returnTypesByBanner map. If so, good.
// - If not, then check if the banner is in declaredBanners. If so, then we're currently in
//   the process of evaluating the entire body. In this case, throw an error because we're
//   about to infinite loop. Hopefully this is a user error, they need to specify a return
//   type to avoid a cyclical definition.
// - If not in declared banners, then tell FunctionTemplar to start evaluating it.

case class ImplT(
  struct: StructTT,
  interface: InterfaceTT
) extends QueriableT {
  override def hashCode(): Int = vcurious()
  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    struct.all(func) ++ interface.all(func)
  }
}

case class KindExportT(
  range: RangeS,
  tyype: KindT,
  packageCoordinate: PackageCoordinate,
  exportedName: String
) extends QueriableT {
  override def hashCode(): Int = vcurious()
  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    tyype.all(func)
  }
}

case class FunctionExportT(
  range: RangeS,
  prototype: PrototypeT,
  packageCoordinate: PackageCoordinate,
  exportedName: String
) extends QueriableT {
  override def hashCode(): Int = vcurious()
  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    prototype.all(func)
  }
}

case class KindExternT(
  tyype: KindT,
  packageCoordinate: PackageCoordinate,
  externName: String
) extends QueriableT {
  override def hashCode(): Int = vcurious()
  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    tyype.all(func)
  }
}

case class FunctionExternT(
  range: RangeS,
  prototype: PrototypeT,
  packageCoordinate: PackageCoordinate,
  externName: String
) extends QueriableT {
  override def hashCode(): Int = vcurious()
  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    prototype.all(func)
  }
}

case class InterfaceEdgeBlueprint(
  interface: InterfaceTT,
  superFamilyRootBanners: List[FunctionBannerT]) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class EdgeT(
  struct: StructTT,
  interface: InterfaceTT,
  methods: List[PrototypeT]) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

object Program2 {
  val emptyTupleStructRef = StructTT(FullNameT(PackageCoordinate.BUILTIN, List.empty, TupleNameT(List.empty)))
  val emptyTupleType: PackTT = PackTT(List.empty, Program2.emptyTupleStructRef)
  val emptyTupleReference: CoordT = CoordT(ShareT, ReadonlyT, emptyTupleType)
  val emptyPackExpression: PackTE = PackTE(List.empty, CoordT(ShareT, ReadonlyT, Program2.emptyTupleType), Program2.emptyTupleType)

  val intType = CoordT(ShareT, ReadonlyT, IntT.i32)
  val boolType = CoordT(ShareT, ReadonlyT, BoolT())
}

//trait Program2 {
//  def getAllInterfaces: Set[InterfaceDefinition2]
//  def getAllStructs: Set[StructDefinition2]
//  def getAllImpls: List[Impl2]
//  def getAllFunctions: Set[Function2]
//  def getAllCitizens: Set[CitizenDefinition2] = getAllInterfaces ++ getAllStructs
//  def getAllExterns: Set[FunctionHeader2]
//  def emptyPackStructRef: structTT
//
//  def lookupStruct(structTT: structTT): StructDefinition2;
//  def lookupInterface(interfaceTT: InterfaceRef2): InterfaceDefinition2;
//  def lookupCitizen(citizenRef: CitizenRef2): CitizenDefinition2;
//  def lookupFunction(signature2: Signature2): Option[Function2];
//
//  def getAllNonExternFunctions: Set[Function2] = {
//    getAllFunctions.filter(!_.header.isExtern)
//  }
//  def getAllUserFunctions: Set[Function2] = {
//    getAllFunctions.filter(_.header.isUserFunction)
//  }
//}

case class FunctionT(
  header: FunctionHeaderT,
//  // Used for testing
//  variables: List[ILocalVariableT],
  body: ReferenceExpressionTE) extends QueriableT {
  override def hashCode(): Int = vcurious()

  // We always end a function with a return, whose result is a Never.
  vassert(body.resultRegister.kind == NeverT())

  def all[T](func: PartialFunction[QueriableT, T]): List[T] = {
    List(this).collect(func) ++ header.all(func) ++ body.all(func)// ++ variables.flatMap(_.all(func))
  }
}
