package net.verdagon.vale.hinputs

import net.verdagon.vale.templar.{CompleteProgram2, Edge2, Function2, Impl2, InterfaceEdgeBlueprint, Program2}
import net.verdagon.vale.templar.templata.{FunctionBanner2, Prototype2, Signature2}
import net.verdagon.vale.templar.types.{InterfaceDefinition2, InterfaceRef2, StructDefinition2, StructRef2}

import scala.collection.immutable.List

case class ETable2(struct: StructRef2, table: TetrisTable[InterfaceRef2, InterfaceRef2])

case class Hinputs(
  interfaces: List[InterfaceDefinition2],
  structs: List[StructDefinition2],
  emptyPackStructRef: StructRef2,
  functions: List[Function2],
  externPrototypes: List[Prototype2],
  edgeBlueprintsByInterface: Map[InterfaceRef2, InterfaceEdgeBlueprint],
  edges: List[Edge2]) {

  def lookupStruct(structRef: StructRef2): StructDefinition2 = {
    structs.find(_.getRef == structRef).get
  }
  def lookupInterface(interfaceRef: InterfaceRef2): InterfaceDefinition2 = {
    interfaces.find(_.getRef == interfaceRef).get
  }
  def lookupFunction(signature2: Signature2): Option[Function2] = {
    functions.find(_.header.toSignature == signature2).headOption
  }
}