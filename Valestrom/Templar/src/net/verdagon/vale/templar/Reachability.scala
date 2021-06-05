package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata.{CoordTemplata, Export2, Signature2}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassertSome, vcurious}

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
  def findReachables(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[Edge2]): Reachables = {
    val structs = program.getAllStructs()
    val interfaces = program.getAllInterfaces()
    val functions = program.getAllFunctions()

    val exposedFunctions =
      functions.filter(func => {
        (func.header.fullName.last match {
          case FunctionName2("main", _, _) => true
          case _ => false
        }) ||
        func.header.isExport
      })
    val exposedStructs = structs.filter(_.attributes.contains(Export2))
    val exposedInterfaces = interfaces.filter(_.attributes.contains(Export2))
    val reachables = new Reachables(mutable.Set(), mutable.Set(), mutable.Set(), mutable.Set())
    var sizeBefore = 0
    do {
      vcurious(sizeBefore == 0) // do we ever need multiple iterations, or is the DFS good enough?
      sizeBefore = reachables.size
      exposedFunctions.map(_.header.toSignature).foreach(visitFunction(program, edgeBlueprints, edges, reachables, _))
      exposedStructs.map(_.getRef).foreach(visitStruct(program, edgeBlueprints, edges, reachables, _))
      exposedInterfaces.map(_.getRef).foreach(visitInterface(program, edgeBlueprints, edges, reachables, _))
    } while (reachables.size != sizeBefore)
    visitStruct(program, edgeBlueprints, edges, reachables, Program2.emptyTupleStructRef)
    reachables
  }
  def visitFunction(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[Edge2], reachables: Reachables, calleeSignature: Signature2): Unit = {
    if (reachables.functions.contains(calleeSignature)) {
      return
    }
    reachables.functions.add(calleeSignature)
    val function = vassertSome(program.lookupFunction(calleeSignature))
    function.all({
      case FunctionCall2(calleePrototype, _) => visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case ConstructArray2(_, _, _, calleePrototype) => visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case StaticArrayFromCallable2(_, _, calleePrototype) => visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case sr @ StructRef2(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceRef2(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
      case ksa @ KnownSizeArrayT2(_, _) => visitKnownSizeArray(program, edgeBlueprints, edges, reachables, ksa)
      case usa @ UnknownSizeArrayT2(_) => visitUnknownSizeArray(program, edgeBlueprints, edges, reachables, usa)
      case LockWeak2(_, _, someConstructor, noneConstructor) => {
        visitFunction(program, edgeBlueprints, edges, reachables, someConstructor.toSignature)
        visitFunction(program, edgeBlueprints, edges, reachables, noneConstructor.toSignature)
      }
      case AsSubtype2(_, _, _, someConstructor, noneConstructor) => {
        visitFunction(program, edgeBlueprints, edges, reachables, someConstructor.toSignature)
        visitFunction(program, edgeBlueprints, edges, reachables, noneConstructor.toSignature)
      }
    })
  }

  def visitStruct(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[Edge2], reachables: Reachables, structRef: StructRef2): Unit = {
    if (reachables.structs.contains(structRef)) {
      return
    }
    reachables.structs.add(structRef)
    val structDef = program.lookupStruct(structRef)
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (structDef.mutability == Immutable && structRef != Program2.emptyTupleStructRef) {
      val destructorSignature =
        Signature2(FullName2(List(), ImmConcreteDestructorName2(structRef)))
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
    structDef.all({
      case sr @ StructRef2(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceRef2(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
    })
    edges.filter(_.struct == structRef).foreach(visitImpl(program, edgeBlueprints, edges, reachables, _))
  }

  def visitInterface(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[Edge2], reachables: Reachables, interfaceRef: InterfaceRef2): Unit = {
    if (reachables.interfaces.contains(interfaceRef)) {
      return
    }
    reachables.interfaces.add(interfaceRef)
    val interfaceDef = program.lookupInterface(interfaceRef)
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (interfaceDef.mutability == Immutable) {
      val destructorSignature =
        Signature2(FullName2(List(), ImmInterfaceDestructorName2(List(CoordTemplata(Coord(Share, Readonly, interfaceRef))), List(Coord(Share, Readonly, interfaceRef)))))
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
    interfaceDef.all({
      case sr @ StructRef2(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceRef2(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
    })
    edgeBlueprints.find(_.interface == interfaceRef).get.superFamilyRootBanners.foreach(f => {
      visitFunction(program, edgeBlueprints, edges, reachables, f.toSignature)
    })
    edges.filter(_.interface == interfaceRef).foreach(visitImpl(program, edgeBlueprints, edges, reachables, _))
  }

  def visitImpl(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[Edge2], reachables: Reachables, edge: Edge2): Unit = {
    if (reachables.edges.contains(edge)) {
      return
    }
    reachables.edges.add(edge)
    edges.foreach(edge => {
      visitStruct(program, edgeBlueprints, edges, reachables, edge.struct)
      visitInterface(program, edgeBlueprints, edges, reachables, edge.interface)
      edge.methods.map(_.toSignature).foreach(visitFunction(program, edgeBlueprints, edges, reachables, _))
    })
  }

  def visitKnownSizeArray(
    program: Temputs,
    edgeBlueprints: List[InterfaceEdgeBlueprint],
    edges: List[Edge2],
    reachables: Reachables,
    ksa: KnownSizeArrayT2): Unit = {
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (ksa.array.mutability == Immutable) {
      val destructorSignature =
        Signature2(FullName2(List(), ImmConcreteDestructorName2(ksa)))
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
  }

  def visitUnknownSizeArray(
    program: Temputs,
    edgeBlueprints: List[InterfaceEdgeBlueprint],
    edges: List[Edge2],
    reachables: Reachables,
    usa: UnknownSizeArrayT2): Unit = {
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (usa.array.mutability == Immutable) {
      val destructorSignature =
        Signature2(FullName2(List(), ImmConcreteDestructorName2(usa)))
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
  }
}
