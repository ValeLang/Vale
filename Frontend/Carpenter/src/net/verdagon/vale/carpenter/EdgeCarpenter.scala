package net.verdagon.vale.carpenter

import net.verdagon.vale.templar.types.InterfaceDefinition2
import net.verdagon.vale.templar.{Edge2, EdgeTemplar, FunctionName2, Impl2, InterfaceEdgeBlueprint}
import net.verdagon.vale.{templar, vassert, vfail}

import scala.collection.immutable

object EdgeCarpenter {
//  def assembleEdges(
//    functions: List[net.verdagon.vale.templar.Function2],
//    interfaces: List[InterfaceDefinition2],
//    impls: List[Impl2]):
//  (Set[InterfaceEdgeBlueprint], Set[Edge2]) = {
//
//    val interfaceEdgeBlueprints =
//      EdgeTemplar.makeInterfaceEdgeBlueprints(functions, interfaces)
//
//    val overrideFunctionsAndIndicesByStructAndInterface =
//      EdgeTemplar.doBlah(functions, interfaces, impls, interfaceEdgeBlueprints)
//
//    val edges =
//      overrideFunctionsAndIndicesByStructAndInterface
//      .map({ case ((struct, superInterface), overrideFunctionsAndIndices) =>
//        val blueprint = interfaceEdgeBlueprints.find(_.interface == superInterface).get
//        val overrideFunctionsByIndex =
//          overrideFunctionsAndIndices.groupBy(_._2).mapValues(_.map(_._1).toList)
//        val overrideFunctions =
//          blueprint.superFamilyRootBanners.zipWithIndex.map({ case (superFunction, index) =>
//            overrideFunctionsByIndex.get(index) match {
//              case None => {
//                vfail("No override for struct\n  " + struct + "\nfor interface\n  " + superInterface + "\nfor function\n  " + superFunction.toSignature)
//              }
//              case Some(List()) => vfail("wot")
//              case Some(List(overrideFunction)) => overrideFunction
//              case Some(multipleOverrides) => {
//                vfail("Multiple overrides for struct " + struct + " for interface " + superInterface + ": " + multipleOverrides.map(_.header.toSignature).mkString(", "))
//              }
//            }
//          })
//        Edge2(struct, superInterface, overrideFunctions.map(_.header.toPrototype))
//      })
//
//    (interfaceEdgeBlueprints.toSet, edges.toSet)
//  }
}
