package net.verdagon.vale.carpenter

import net.verdagon.vale.templar.templata.{FunctionHeader2, Signature2}
import net.verdagon.vale.templar.types.{InterfaceRef2, StructRef2}
import net.verdagon.vale.templar.{Edge2, FunctionCall2, FunctionName2, Impl2, InterfaceEdgeBlueprint, Program2, Temputs}
import net.verdagon.vale.vcurious

import scala.collection.mutable

class Reachables(
  val functions: mutable.Set[Signature2],
  val structs: mutable.Set[StructRef2],
  val interfaces: mutable.Set[InterfaceRef2],
  val edges: mutable.Set[Edge2]
) {
  def size = functions.size + structs.size + interfaces.size + edges.size
}

object Reachability {
  def findReachables(temputs: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[Edge2]): Reachables = {
    val exposedFunctions =
      temputs.functions.filter(_.header.fullName.last match {
        case FunctionName2("main", _, _) => true
        case _ => false
      })
    val reachables = new Reachables(mutable.Set(), mutable.Set(), mutable.Set(), mutable.Set())
    var sizeBefore = 0
    do {
      vcurious(sizeBefore == 0) // do we ever need multiple iterations, or is the DFS good enough?
      sizeBefore = reachables.size
      exposedFunctions.map(_.header.toSignature).foreach(visitFunction(temputs, edgeBlueprints, edges, reachables, _))
    } while (reachables.size != sizeBefore)
    visitStruct(temputs, edgeBlueprints, edges, reachables, Program2.emptyTupleStructRef)
    reachables
  }
  def visitFunction(temputs: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[Edge2], reachables: Reachables, calleeSignature: Signature2): Unit = {
    if (reachables.functions.contains(calleeSignature)) {
      return
    }
    reachables.functions.add(calleeSignature)
    val function = temputs.lookupFunction(calleeSignature).get
    function.all({
      case FunctionCall2(calleePrototype, _) => visitFunction(temputs, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case sr @ StructRef2(_) => visitStruct(temputs, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceRef2(_) => visitInterface(temputs, edgeBlueprints, edges, reachables, ir)
    })
  }

  def visitStruct(temputs: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[Edge2], reachables: Reachables, structRef: StructRef2): Unit = {
    if (reachables.structs.contains(structRef)) {
      return
    }
    reachables.structs.add(structRef)
    val structDef = temputs.lookupStruct(structRef)
    structDef.all({
      case sr @ StructRef2(_) => visitStruct(temputs, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceRef2(_) => visitInterface(temputs, edgeBlueprints, edges, reachables, ir)
    })
    edges.filter(_.struct == structRef).foreach(visitImpl(temputs, edgeBlueprints, edges, reachables, _))
  }

  def visitInterface(temputs: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[Edge2], reachables: Reachables, interfaceRef: InterfaceRef2): Unit = {
    if (reachables.interfaces.contains(interfaceRef)) {
      return
    }
    reachables.interfaces.add(interfaceRef)
    val interfaceDef = temputs.lookupInterface(interfaceRef)
    interfaceDef.all({
      case sr @ StructRef2(_) => visitStruct(temputs, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceRef2(_) => visitInterface(temputs, edgeBlueprints, edges, reachables, ir)
    })
    edgeBlueprints.find(_.interface == interfaceRef).get.superFamilyRootBanners.foreach(f => {
      visitFunction(temputs, edgeBlueprints, edges, reachables, f.toSignature)
    })
    edges.filter(_.interface == interfaceRef).foreach(visitImpl(temputs, edgeBlueprints, edges, reachables, _))
  }

  def visitImpl(temputs: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[Edge2], reachables: Reachables, edge: Edge2): Unit = {
    if (reachables.edges.contains(edge)) {
      return
    }
    reachables.edges.add(edge)
    edges.foreach(edge => {
      visitStruct(temputs, edgeBlueprints, edges, reachables, edge.struct)
      visitInterface(temputs, edgeBlueprints, edges, reachables, edge.interface)
      edge.methods.map(_.toSignature).foreach(visitFunction(temputs, edgeBlueprints, edges, reachables, _))
    })
  }
}
