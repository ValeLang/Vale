package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata.{CoordTemplata, Export2, SignatureT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{PackageCoordinate, vassertSome, vcurious}

import scala.collection.mutable

class Reachables(
  val functions: mutable.Set[SignatureT],
  val structs: mutable.Set[StructRefT],
  val staticSizedArrays: mutable.Set[StaticSizedArrayTT],
  val runtimeSizedArrays: mutable.Set[RuntimeSizedArrayTT],
  val interfaces: mutable.Set[InterfaceRefT],
  val edges: mutable.Set[EdgeT]
) {
  def size = functions.size + structs.size + staticSizedArrays.size + runtimeSizedArrays.size + interfaces.size + edges.size
}

object Reachability {
  def findReachables(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[EdgeT]): Reachables = {
    val structs = program.getAllStructs()
    val interfaces = program.getAllInterfaces()
    val functions = program.getAllFunctions()

    val exposedFunctions =
      functions.filter(func => {
        (func.header.fullName.last match {
          case FunctionNameT("main", _, _) => true
          case _ => false
        }) ||
        func.header.isExport
      })
    val exposedStructs = structs.filter(_.attributes.exists({ case Export2(_) => true }))
    val exposedInterfaces = interfaces.filter(_.attributes.exists({ case Export2(_) => true }))
    val reachables = new Reachables(mutable.Set(), mutable.Set(), mutable.Set(), mutable.Set(), mutable.Set(), mutable.Set())
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
  def visitFunction(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[EdgeT], reachables: Reachables, calleeSignature: SignatureT): Unit = {
    if (reachables.functions.contains(calleeSignature)) {
      return
    }
    reachables.functions.add(calleeSignature)
    val function = vassertSome(program.lookupFunction(calleeSignature))
    function.all({
      case FunctionCallTE(calleePrototype, _) => visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case ConstructArrayTE(_, _, _, calleePrototype) => visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case StaticArrayFromCallableTE(_, _, calleePrototype) => visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case DestroyStaticSizedArrayIntoFunctionTE(_, _, _, calleePrototype) => visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case sr @ StructRefT(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceRefT(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
      case ssa @ StaticSizedArrayTT(_, _) => visitStaticSizedArray(program, edgeBlueprints, edges, reachables, ssa)
      case rsa @ RuntimeSizedArrayTT(_) => visitRuntimeSizedArray(program, edgeBlueprints, edges, reachables, rsa)
      case LockWeakTE(_, _, someConstructor, noneConstructor) => {
        visitFunction(program, edgeBlueprints, edges, reachables, someConstructor.toSignature)
        visitFunction(program, edgeBlueprints, edges, reachables, noneConstructor.toSignature)
      }
      case AsSubtypeTE(_, _, _, someConstructor, noneConstructor) => {
        visitFunction(program, edgeBlueprints, edges, reachables, someConstructor.toSignature)
        visitFunction(program, edgeBlueprints, edges, reachables, noneConstructor.toSignature)
      }
    })
  }

  def visitStruct(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[EdgeT], reachables: Reachables, structRef: StructRefT): Unit = {
    if (reachables.structs.contains(structRef)) {
      return
    }
    reachables.structs.add(structRef)
    val structDef = program.lookupStruct(structRef)
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (structDef.mutability == ImmutableT && structRef != Program2.emptyTupleStructRef) {
      val destructorSignature = program.getDestructor(structRef).toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
    structDef.all({
      case sr @ StructRefT(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceRefT(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
    })
    edges.filter(_.struct == structRef).foreach(visitImpl(program, edgeBlueprints, edges, reachables, _))
  }

  def visitInterface(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[EdgeT], reachables: Reachables, interfaceRef: InterfaceRefT): Unit = {
    if (reachables.interfaces.contains(interfaceRef)) {
      return
    }
    reachables.interfaces.add(interfaceRef)
    val interfaceDef = program.lookupInterface(interfaceRef)
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (interfaceDef.mutability == ImmutableT) {
      val destructorSignature = program.getDestructor(interfaceRef).toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
    interfaceDef.all({
      case sr @ StructRefT(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceRefT(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
    })
    edgeBlueprints.find(_.interface == interfaceRef).get.superFamilyRootBanners.foreach(f => {
      visitFunction(program, edgeBlueprints, edges, reachables, f.toSignature)
    })
    edges.filter(_.interface == interfaceRef).foreach(visitImpl(program, edgeBlueprints, edges, reachables, _))
  }

  def visitImpl(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[EdgeT], reachables: Reachables, edge: EdgeT): Unit = {
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

  def visitStaticSizedArray(
    program: Temputs,
    edgeBlueprints: List[InterfaceEdgeBlueprint],
    edges: List[EdgeT],
    reachables: Reachables,
    ssa: StaticSizedArrayTT
  ): Unit = {
    if (reachables.staticSizedArrays.contains(ssa)) {
      return
    }
    reachables.staticSizedArrays.add(ssa)

    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (ssa.array.mutability == ImmutableT) {
      val destructorSignature = program.getDestructor(ssa).toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
  }

  def visitRuntimeSizedArray(
    program: Temputs,
    edgeBlueprints: List[InterfaceEdgeBlueprint],
    edges: List[EdgeT],
    reachables: Reachables,
    rsa: RuntimeSizedArrayTT
  ): Unit = {
    if (reachables.runtimeSizedArrays.contains(rsa)) {
      return
    }
    reachables.runtimeSizedArrays.add(rsa)

    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (rsa.array.mutability == ImmutableT) {
      val destructorSignature = program.getDestructor(rsa).toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
  }
}
