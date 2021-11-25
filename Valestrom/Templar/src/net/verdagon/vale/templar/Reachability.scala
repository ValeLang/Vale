package net.verdagon.vale.templar

import net.verdagon.vale.templar.ast.{AsSubtypeTE, ConstructArrayTE, DestroyRuntimeSizedArrayTE, DestroyStaticSizedArrayIntoFunctionTE, EdgeT, FunctionCallTE, FunctionT, InterfaceEdgeBlueprint, LockWeakTE, ProgramT, SignatureT, StaticArrayFromCallableTE, getFunctionLastName}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.names.{FreeNameT, FullNameT, FunctionNameT, IFunctionNameT, VirtualFreeNameT}
import net.verdagon.vale.templar.templata.CoordTemplata
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{Collector, PackageCoordinate, vassertOne, vassertSome, vcurious, vimpl, vpass}

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
  def findReachables(program: Temputs, emptyTupleStruct: StructTT, edgeBlueprints: Vector[InterfaceEdgeBlueprint], edges: Vector[EdgeT]): Reachables = {
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
      exposedFunctions.map(_.header.toSignature).foreach(visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, _))
      exposedStructs.map(_.getRef).foreach(visitStruct(program, emptyTupleStruct, edgeBlueprints, edges, reachables, _))
      exposedInterfaces.map(_.getRef).foreach(visitInterface(program, emptyTupleStruct, edgeBlueprints, edges, reachables, _))
    } while (reachables.size != sizeBefore)
    visitStruct(program, emptyTupleStruct, edgeBlueprints, edges, reachables, emptyTupleStruct)
    reachables
  }
  def visitFunction(program: Temputs, emptyTupleStruct: StructTT, edgeBlueprints: Vector[InterfaceEdgeBlueprint], edges: Vector[EdgeT], reachables: Reachables, calleeSignature: SignatureT): Unit = {
    if (reachables.functions.contains(calleeSignature)) {
      return
    }
    reachables.functions.add(calleeSignature)
    val function = vassertSome(program.lookupFunction(calleeSignature))
    Collector.all(function, {
      case FunctionCallTE(calleePrototype, _) => {
        vpass()
        vpass()
        visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      }
      case ConstructArrayTE(_, _, _, calleePrototype) => visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case StaticArrayFromCallableTE(_, _, calleePrototype) => visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case DestroyStaticSizedArrayIntoFunctionTE(_, _, _, calleePrototype) => visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case DestroyRuntimeSizedArrayTE(_, _, _, calleePrototype) => visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case sr @ StructTT(_) => visitStruct(program, emptyTupleStruct, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceTT(_) => visitInterface(program, emptyTupleStruct, edgeBlueprints, edges, reachables, ir)
      case ssa @ StaticSizedArrayTT(_, _) => visitStaticSizedArray(program, emptyTupleStruct, edgeBlueprints, edges, reachables, ssa)
      case rsa @ RuntimeSizedArrayTT(_) => visitRuntimeSizedArray(program, emptyTupleStruct, edgeBlueprints, edges, reachables, rsa)
      case LockWeakTE(_, _, someConstructor, noneConstructor) => {
        visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, someConstructor.toSignature)
        visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, noneConstructor.toSignature)
      }
      case AsSubtypeTE(_, _, _, someConstructor, noneConstructor) => {
        visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, someConstructor.toSignature)
        visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, noneConstructor.toSignature)
      }
    })
  }

  def visitStruct(program: Temputs, emptyTupleStruct: StructTT, edgeBlueprints: Vector[InterfaceEdgeBlueprint], edges: Vector[EdgeT], reachables: Reachables, structTT: StructTT): Unit = {
    if (reachables.structs.contains(structTT)) {
      return
    }
    reachables.structs.add(structTT)
    val structDef = program.lookupStruct(structTT)
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (structDef.mutability == ImmutableT && structTT != emptyTupleStruct) {
      val destructorSignature = program.findImmDestructor(structTT).toSignature
      visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, destructorSignature)
    }
    Collector.all(structDef, {
      case sr @ StructTT(_) => visitStruct(program, emptyTupleStruct, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceTT(_) => visitInterface(program, emptyTupleStruct, edgeBlueprints, edges, reachables, ir)
    })
    edges.filter(_.struct == structTT).foreach(visitImpl(program, emptyTupleStruct, edgeBlueprints, edges, reachables, _))

    if (structDef.mutability == ImmutableT) {
      if (structTT != emptyTupleStruct) {
        val destructorSignature =
          vassertOne(
            program.getAllFunctions().find(func => {
              func.header.toSignature match {
                case SignatureT(FullNameT(_, _, FreeNameT(_, kind))) if kind == structTT => true
                case SignatureT(FullNameT(_, _, VirtualFreeNameT(_, kind))) if kind == structTT => true
                case _ => false
              }
            })).header.toSignature
        visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, destructorSignature)
      }
    }
  }

  def visitInterface(program: Temputs, emptyTupleStruct: StructTT, edgeBlueprints: Vector[InterfaceEdgeBlueprint], edges: Vector[EdgeT], reachables: Reachables, interfaceTT: InterfaceTT): Unit = {
    if (reachables.interfaces.contains(interfaceTT)) {
      return
    }
    reachables.interfaces.add(interfaceTT)
    val interfaceDef = program.lookupInterface(interfaceTT)
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (interfaceDef.mutability == ImmutableT) {
      val destructorSignature = program.findImmDestructor(interfaceTT).toSignature
      visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, destructorSignature)
    }
    Collector.all(interfaceDef, {
      case sr @ StructTT(_) => visitStruct(program, emptyTupleStruct, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceTT(_) => visitInterface(program, emptyTupleStruct, edgeBlueprints, edges, reachables, ir)
    })
    edgeBlueprints.find(_.interface == interfaceTT).get.superFamilyRootBanners.foreach(f => {
      visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, f.toSignature)
    })
    edges.filter(_.interface == interfaceTT).foreach(visitImpl(program, emptyTupleStruct, edgeBlueprints, edges, reachables, _))

    if (interfaceDef.mutability == ImmutableT) {
      val destructorSignature =
        vassertOne(
          program.getAllFunctions().find(func => {
            func.header.toSignature match {
              case SignatureT(FullNameT(_, _, FreeNameT(_, kind))) if kind == interfaceTT => true
              case SignatureT(FullNameT(_, _, VirtualFreeNameT(_, kind))) if kind == interfaceTT => true
              case _ => false
            }
          })).header.toSignature
      visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, destructorSignature)
    }
  }

  def visitImpl(program: Temputs, emptyTupleStruct: StructTT, edgeBlueprints: Vector[InterfaceEdgeBlueprint], edges: Vector[EdgeT], reachables: Reachables, edge: EdgeT): Unit = {
    if (reachables.edges.contains(edge)) {
      return
    }
    reachables.edges.add(edge)
    edges.foreach(edge => {
      visitStruct(program, emptyTupleStruct, edgeBlueprints, edges, reachables, edge.struct)
      visitInterface(program, emptyTupleStruct, edgeBlueprints, edges, reachables, edge.interface)
      edge.methods.map(_.toSignature).foreach(visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, _))
    })
  }

  def visitStaticSizedArray(
    program: Temputs,
    emptyTupleStruct: StructTT,
    edgeBlueprints: Vector[InterfaceEdgeBlueprint],
    edges: Vector[EdgeT],
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
      val destructorSignature =
        vassertOne(
          program.getAllFunctions().find(func => {
            func.header.toSignature match {
              case SignatureT(FullNameT(_, _, FreeNameT(_, kind))) if kind == ssa => true
              case SignatureT(FullNameT(_, _, VirtualFreeNameT(_, kind))) if kind == ssa => true
              case _ => false
            }
          })).header.toSignature
      visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, destructorSignature)
    }
  }

  def visitRuntimeSizedArray(
    program: Temputs,
    emptyTupleStruct: StructTT,
    edgeBlueprints: Vector[InterfaceEdgeBlueprint],
    edges: Vector[EdgeT],
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
      val destructorSignature =
        vassertOne(
          program.getAllFunctions().find(func => {
            func.header.toSignature match {
              case SignatureT(FullNameT(_, _, FreeNameT(_, kind))) if kind == rsa => true
              case SignatureT(FullNameT(_, _, VirtualFreeNameT(_, kind))) if kind == rsa => true
              case _ => false
            }
          })).header.toSignature
      visitFunction(program, emptyTupleStruct, edgeBlueprints, edges, reachables, destructorSignature)
    }
  }
}
