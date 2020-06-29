package net.verdagon.vale.carpenter

import net.verdagon.vale.hinputs.ETable2
import net.verdagon.vale.templar.Edge2
import net.verdagon.vale.templar.types.{InterfaceRef2, StructRef2}

//object ETableGenerator {
//  def generateETables(
//      interfaceIdsByInterface: Map[InterfaceRef2, Int],
//      edges: Set[Edge2]):
//  Map[StructRef2, ETable2] = {
//    val edgesByStruct = edges.groupBy(_.struct);
//
//    edgesByStruct.map({
//      case (struct, edgesForStruct) => {
//        (struct -> generateETableForStruct(interfaceIdsByInterface, struct, edgesForStruct))
//      }
//    }).toMap
//  }
//
//  def generateETableForStruct(
//      interfaceIdsByInterface: Map[InterfaceRef2, Int],
//      struct: StructRef2,
//      edges: Set[Edge2]):
//  ETable2 = {
//    val interfaceRefs = edges.map(_.interface);
//    val interfaceIdsMap = interfaceRefs.zip(interfaceRefs).toMap
//    val tetrisTable =
//      new TetrisTableGenerator[InterfaceRef2, InterfaceRef2]()
//          .generateTetrisTable(interfaceIdsMap, interfaceRef => interfaceIdsByInterface(interfaceRef))
//    ETable2(struct, tetrisTable)
//  }
//}
