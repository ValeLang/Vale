package dev.vale.typing

import dev.vale.typing.ast.{AsSubtypeTE, DestroyImmRuntimeSizedArrayTE, DestroyStaticSizedArrayIntoFunctionTE, EdgeT, FunctionCallTE, InterfaceEdgeBlueprint, LockWeakTE, NewImmRuntimeSizedArrayTE, PrototypeT, SignatureT, StaticArrayFromCallableTE}
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.names.{FreeNameT, FullNameT, FunctionNameT}
import dev.vale.typing.templata.CoordTemplata
import dev.vale.typing.types.{ImmutableT, InterfaceTT, RuntimeSizedArrayTT, StaticSizedArrayTT, StructTT}
import dev.vale.{Collector, vassertOne, vassertSome, vcurious, vpass}
import dev.vale.typing.ast._
import dev.vale.typing.names._
import dev.vale.typing.types._
import dev.vale.Collector

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
  def findReachables(program: CompilerOutputs, edgeBlueprints: Vector[InterfaceEdgeBlueprint], edges: Map[InterfaceTT, Map[StructTT, Vector[PrototypeT]]]): Reachables = {
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
    reachables
  }
  def visitFunction(program: CompilerOutputs, edgeBlueprints: Vector[InterfaceEdgeBlueprint], edges: Map[InterfaceTT, Map[StructTT, Vector[PrototypeT]]], reachables: Reachables, calleeSignature: SignatureT): Unit = {
    if (reachables.functions.contains(calleeSignature)) {
      return
    }
    reachables.functions.add(calleeSignature)
    val function = vassertSome(program.lookupFunction(calleeSignature))
    Collector.all(function, {
      case FunctionCallTE(calleePrototype, _) => {
        vpass()
        vpass()
        visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      }
      case NewImmRuntimeSizedArrayTE(_, _, _, calleePrototype) => visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case StaticArrayFromCallableTE(_, _, calleePrototype) => visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case DestroyStaticSizedArrayIntoFunctionTE(_, _, _, calleePrototype) => visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case DestroyImmRuntimeSizedArrayTE(_, _, _, calleePrototype) => visitFunction(program, edgeBlueprints, edges, reachables, calleePrototype.toSignature)
      case sr @ StructTT(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceTT(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
      case ssa @ StaticSizedArrayTT(_, _, _, _) => visitStaticSizedArray(program, edgeBlueprints, edges, reachables, ssa)
      case rsa @ RuntimeSizedArrayTT(_, _) => visitRuntimeSizedArray(program, edgeBlueprints, edges, reachables, rsa)
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

  def visitStruct(program: CompilerOutputs, edgeBlueprints: Vector[InterfaceEdgeBlueprint], edges: Map[InterfaceTT, Map[StructTT, Vector[PrototypeT]]], reachables: Reachables, structTT: StructTT): Unit = {
    if (reachables.structs.contains(structTT)) {
      return
    }
    reachables.structs.add(structTT)
    val structDef = program.lookupStruct(structTT)
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (structDef.mutability == ImmutableT) {
      val destructorSignature = program.findImmDestructor(structTT).toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
    Collector.all(structDef, {
      case sr @ StructTT(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceTT(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
      case ssa @ StaticSizedArrayTT(_, _, _, _) => visitStaticSizedArray(program, edgeBlueprints, edges, reachables, ssa)
      case rsa @ RuntimeSizedArrayTT(_, _) => visitRuntimeSizedArray(program, edgeBlueprints, edges, reachables, rsa)
    })
    edges.foreach({ case (interface, structToMethods) =>
      structToMethods.get(structTT) match {
        case None =>
        case Some(methods) => visitImpl(program, edgeBlueprints, edges, reachables, interface, structTT, methods)
      }
    })

    if (structDef.mutability == ImmutableT) {
      val destructorSignature =
        vassertOne(
          program.getAllFunctions().find(func => {
            func.header.toSignature match {
              case SignatureT(FullNameT(_, _, FreeNameT(_, kind))) if kind == structTT => true
//              case SignatureT(FullNameT(_, _, AbstractVirtualFreeNameT(_, kind))) if kind == structTT => true
//              case SignatureT(FullNameT(_, _, OverrideVirtualFreeNameT(_, kind))) if kind == structTT => true
              case _ => false
            }
          })).header.toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
  }

  def visitInterface(program: CompilerOutputs, edgeBlueprints: Vector[InterfaceEdgeBlueprint], edges: Map[InterfaceTT, Map[StructTT, Vector[PrototypeT]]], reachables: Reachables, interfaceTT: InterfaceTT): Unit = {
    if (reachables.interfaces.contains(interfaceTT)) {
      return
    }
    reachables.interfaces.add(interfaceTT)
    val interfaceDef = program.lookupInterface(interfaceTT)
    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (interfaceDef.mutability == ImmutableT) {
      val destructorSignature = program.findImmDestructor(interfaceTT).toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
    Collector.all(interfaceDef, {
      case sr @ StructTT(_) => visitStruct(program, edgeBlueprints, edges, reachables, sr)
      case ir @ InterfaceTT(_) => visitInterface(program, edgeBlueprints, edges, reachables, ir)
      case ssa @ StaticSizedArrayTT(_, _, _, _) => visitStaticSizedArray(program, edgeBlueprints, edges, reachables, ssa)
      case rsa @ RuntimeSizedArrayTT(_, _) => visitRuntimeSizedArray(program, edgeBlueprints, edges, reachables, rsa)
    })
    edgeBlueprints.find(_.interface == interfaceTT).get.superFamilyRootBanners.foreach(f => {
      visitFunction(program, edgeBlueprints, edges, reachables, f.toSignature)
    })
    vassertSome(edges.get(interfaceTT)).foreach({ case (structTT, methods) =>
      visitImpl(program, edgeBlueprints, edges, reachables, interfaceTT, structTT, methods)
    })

    if (interfaceDef.mutability == ImmutableT) {
      val destructorSignature =
        vassertOne(
          program.getAllFunctions().find(func => {
            func.header.toSignature match {
              case SignatureT(FullNameT(_, _, FreeNameT(_, kind))) if kind == interfaceTT => true
//              case SignatureT(FullNameT(_, _, AbstractVirtualFreeNameT(_, kind))) if kind == interfaceTT => true
//              case SignatureT(FullNameT(_, _, OverrideVirtualFreeNameT(_, kind))) if kind == interfaceTT => true
              case _ => false
            }
          })).header.toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
  }

  def visitImpl(
      program: CompilerOutputs,
      edgeBlueprints: Vector[InterfaceEdgeBlueprint],
      edges: Map[InterfaceTT, Map[StructTT, Vector[PrototypeT]]],
      reachables: Reachables,
      interfaceTT: InterfaceTT,
      structTT: StructTT,
      methods: Vector[PrototypeT]):
  Unit = {
    val edge = ast.EdgeT(structTT, interfaceTT, methods)
    if (reachables.edges.contains(edge)) {
      return
    }
    reachables.edges.add(edge)
    edges.foreach(edge => {
      visitStruct(program, edgeBlueprints, edges, reachables, structTT)
      visitInterface(program, edgeBlueprints, edges, reachables, interfaceTT)
      methods.map(_.toSignature).foreach(visitFunction(program, edgeBlueprints, edges, reachables, _))
    })
  }

  def visitStaticSizedArray(
    program: CompilerOutputs,
    edgeBlueprints: Vector[InterfaceEdgeBlueprint],
    edges: Map[InterfaceTT, Map[StructTT, Vector[PrototypeT]]],
    reachables: Reachables,
    ssa: StaticSizedArrayTT
  ): Unit = {
    if (reachables.staticSizedArrays.contains(ssa)) {
      return
    }
    reachables.staticSizedArrays.add(ssa)

    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (ssa.mutability == ImmutableT) {
      val destructorSignature =
        vassertOne(
          program.getAllFunctions().find(func => {
            func.header.toSignature match {
              case SignatureT(FullNameT(_, _, FreeNameT(_, kind))) if kind == ssa => true
//              case SignatureT(FullNameT(_, _, AbstractVirtualFreeNameT(_, kind))) if kind == ssa => true
//              case SignatureT(FullNameT(_, _, OverrideVirtualFreeNameT(_, kind))) if kind == ssa => true
              case _ => false
            }
          })).header.toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
  }

  def visitRuntimeSizedArray(
    program: CompilerOutputs,
    edgeBlueprints: Vector[InterfaceEdgeBlueprint],
    edges: Map[InterfaceTT, Map[StructTT, Vector[PrototypeT]]],
    reachables: Reachables,
    rsa: RuntimeSizedArrayTT
  ): Unit = {
    if (reachables.runtimeSizedArrays.contains(rsa)) {
      return
    }
    reachables.runtimeSizedArrays.add(rsa)

    // Make sure the destructor got in, because for immutables, it's implicitly called by lots of instructions
    // that let go of a reference.
    if (rsa.mutability == ImmutableT) {
      val destructorSignature =
        vassertOne(
          program.getAllFunctions().find(func => {
            func.header.toSignature match {
              case SignatureT(FullNameT(_, _, FreeNameT(_, kind))) if kind == rsa => true
//              case SignatureT(FullNameT(_, _, AbstractVirtualFreeNameT(_, kind))) if kind == rsa => true
//              case SignatureT(FullNameT(_, _, OverrideVirtualFreeNameT(_, kind))) if kind == rsa => true
              case _ => false
            }
          })).header.toSignature
      visitFunction(program, edgeBlueprints, edges, reachables, destructorSignature)
    }
  }
}
