package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{PackageCoordinate, vassert, vassertSome, vfail, vpass, vwat}

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

case class Impl2(
  struct: StructRef2,
  interface: InterfaceRef2
) extends Queriable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    struct.all(func) ++ interface.all(func)
  }
}

case class ExportAs2(
  tyype: Kind,
  packageCoordinate: PackageCoordinate,
  exportedName: String
) extends Queriable2 {
  vpass()

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    tyype.all(func)
  }
}

case class InterfaceEdgeBlueprint(
  interface: InterfaceRef2,
  superFamilyRootBanners: List[FunctionBanner2])

case class Edge2(
  struct: StructRef2,
  interface: InterfaceRef2,
  methods: List[Prototype2])

object Program2 {
  val emptyTupleStructRef = StructRef2(FullName2(PackageCoordinate.BUILTIN, List(), TupleName2(List())))
  val emptyTupleType: PackT2 = PackT2(List(), Program2.emptyTupleStructRef)
  val emptyTupleReference: Coord = Coord(Share, Readonly, emptyTupleType)
  val emptyPackExpression: PackE2 = PackE2(List(), Coord(Share, Readonly, Program2.emptyTupleType), Program2.emptyTupleType)

  val intType = Coord(Share, Readonly, Int2())
  val boolType = Coord(Share, Readonly, Bool2())
}

//trait Program2 {
//  def getAllInterfaces: Set[InterfaceDefinition2]
//  def getAllStructs: Set[StructDefinition2]
//  def getAllImpls: List[Impl2]
//  def getAllFunctions: Set[Function2]
//  def getAllCitizens: Set[CitizenDefinition2] = getAllInterfaces ++ getAllStructs
//  def getAllExterns: Set[FunctionHeader2]
//  def emptyPackStructRef: StructRef2
//
//  def lookupStruct(structRef: StructRef2): StructDefinition2;
//  def lookupInterface(interfaceRef: InterfaceRef2): InterfaceDefinition2;
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

case class Function2(
  header: FunctionHeader2,
  // Used for testing
  variables: List[ILocalVariable2],
  body: ReferenceExpression2) extends Queriable2 {

  vassert(
    body.resultRegister.referend == Never2() ||
    header.returnType.referend == Never2() ||
    body.resultRegister.reference == header.returnType)

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ header.all(func) ++ variables.flatMap(_.all(func)) ++ body.all(func)
  }
}
