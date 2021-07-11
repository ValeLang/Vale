package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata.{CoordTemplata, SignatureT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{PackageCoordinate, vassertSome, vcurious}

import scala.collection.mutable

class Reachables(
  val functions: mutable.Set[SignatureT],
  val structs: mutable.Set[StructTT],
  val staticSizedArrays: mutable.Set[StaticSizedArrayTT],
  val runtimeSizedArrays: mutable.Set[RuntimeSizedArrayTT],
  val interfaces: mutable.Set[InterfaceTT],
  val edges: mutable.Set[EdgeT]
) {
  def size = functions.size + structs.size + staticSizedArrays.size + runtimeSizedArrays.size + interfaces.size + edges.size
}

object Reachability {
  def findReachables(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[EdgeT]): Reachables = {
    val structs = program.getAllStructs()
    val interfaces = program.getAllInterfaces()
    val functions = program.getAllFunctions()
    val exportedKinds = program.getKindExports.map(_.tyype).toSet
    val exportedFunctionSignatures = program.getFunctionExports.map(_.prototype.toSignature).toSet

    val exposedFunctions =
      functions.filter(func => {
        (func.header.fullName.last match {
          case FunctionNameT("main", _, _) => true
          case _ => false
        }) ||
        exportedFunctionSignatures.contains(func.header.toSignature)
      })
    val exposedStructs = structs.filter(struct => exportedKinds.contains(struct.getRef))
    val exposedInterfaces = interfaces.filter(interface => exportedKinds.contains(interface.getRef))
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
      case sr @ StructTT(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceTT(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
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

  def visitStruct(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[EdgeT], reachables: Reachables, structTT: StructTT): Unit = {
    if (reachables.structs.contains(structTT)) {
      return
    }
    reachables.structs.add(structTT)
    val structDef = program.lookupStruct(structTT)
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (structDef.mutability == ImmutableT && structTT != Program2.emptyTupleStructRef) {
      val destructorSignature = program.getDestructor(structTT).toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
    structDef.all({
      case sr @ StructTT(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceTT(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
    })
    edges.filter(_.struct == structTT).foreach(visitImpl(program, edgeBlueprints, edges, reachables, _))
  }

  def visitInterface(program: Temputs, edgeBlueprints: List[InterfaceEdgeBlueprint], edges: List[EdgeT], reachables: Reachables, interfaceTT: InterfaceTT): Unit = {
    if (reachables.interfaces.contains(interfaceTT)) {
      return
    }
    reachables.interfaces.add(interfaceTT)
    val interfaceDef = program.lookupInterface(interfaceTT)
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (interfaceDef.mutability == ImmutableT) {
      val destructorSignature = program.getDestructor(interfaceTT).toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
    interfaceDef.all({
      case sr @ StructTT(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceTT(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
    })
    edgeBlueprints.find(_.interface == interfaceTT).get.superFamilyRootBanners.foreach(f => {
      visitFunction(program, edgeBlueprints, edges, reachables, f.toSignature)
    })
    edges.filter(_.interface == interfaceTT).foreach(visitImpl(program, edgeBlueprints, edges, reachables, _))
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
