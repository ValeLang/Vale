package net.verdagon.vale.carpenter

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.templar.EdgeTemplar.{FoundFunction, NeededOverride, PartialEdge2}
import net.verdagon.vale.templar.{CompleteProgram2, Edge2, EdgeTemplar, Program2, Temputs}
import net.verdagon.vale.{vassert, vwat}

object Carpenter {
  def translate(program2: Temputs): Hinputs = {
    val edgeBlueprints =
      EdgeTemplar.makeInterfaceEdgeBlueprints(program2.functions, program2.getAllInterfaces())
    val partialEdges =
      EdgeTemplar.assemblePartialEdges(program2.functions, program2.getAllInterfaces(), program2.impls)
    val edges =
      partialEdges.map({ case PartialEdge2(struct, interface, methods) =>
        Edge2(
          struct,
          interface,
          methods.map({
            case FoundFunction(prototype) => prototype
            case NeededOverride(_, _) => vwat()
          })
        )
      })

    // NEVER ZIP TWO SETS TOGETHER
    val edgeBlueprintsAsList = edgeBlueprints.toList
    val edgeBlueprintsByInterface = edgeBlueprintsAsList.map(_.interface).zip(edgeBlueprintsAsList).toMap;

    edgeBlueprintsByInterface.foreach({ case (interfaceRef, edgeBlueprint) =>
      vassert(edgeBlueprint.interface == interfaceRef)
    })



    val reachables = Reachability.findReachables(program2, edgeBlueprintsAsList, edges)

    val categorizedFunctions = program2.functions.groupBy(f => reachables.functions.contains(f.header.toSignature))
    val reachableFunctions = categorizedFunctions.getOrElse(true, List())
    val unreachableFunctions = categorizedFunctions.getOrElse(false, List())
    unreachableFunctions.foreach(f => println("Shaking out unreachable: " + f.header.fullName))
    reachableFunctions.foreach(f => println("Including: " + f.header.fullName))

    val categorizedStructs = program2.getAllStructs().groupBy(f => reachables.structs.contains(f.getRef))
    val reachableStructs = categorizedStructs.getOrElse(true, List())
    val unreachableStructs = categorizedStructs.getOrElse(false, List())
    unreachableStructs.foreach(f => println("Shaking out unreachable: " + f.fullName))
    reachableStructs.foreach(f => println("Including: " + f.fullName))

    val categorizedInterfaces = program2.getAllInterfaces().groupBy(f => reachables.interfaces.contains(f.getRef))
    val reachableInterfaces = categorizedInterfaces.getOrElse(true, List())
    val unreachableInterfaces = categorizedInterfaces.getOrElse(false, List())
    unreachableInterfaces.foreach(f => println("Shaking out unreachable: " + f.fullName))
    reachableInterfaces.foreach(f => println("Including: " + f.fullName))

    val categorizedEdges = edges.groupBy(f => reachables.edges.contains(f))
    val reachableEdges = categorizedEdges.getOrElse(true, List())
    val unreachableEdges = categorizedEdges.getOrElse(false, List())
    unreachableEdges.foreach(f => println("Shaking out unreachable: " + f))
    reachableEdges.foreach(f => println("Including: " + f))

    Hinputs(
      reachableInterfaces,
      reachableStructs,
      Program2.emptyTupleStructRef,
      reachableFunctions,
      edgeBlueprintsByInterface,
      edges)
  }
}
