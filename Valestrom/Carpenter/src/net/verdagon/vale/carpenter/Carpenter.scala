package net.verdagon.vale.carpenter

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.templar.EdgeTemplar.{FoundFunction, NeededOverride, PartialEdge2}
import net.verdagon.vale.templar.{CompleteProgram2, Edge2, EdgeTemplar, Program2, Temputs}
import net.verdagon.vale.{vassert, vwat}

object Carpenter {
  def translate(
    debugOut: (String => Unit),
    program: Temputs): Hinputs = {

    val Temputs(
      declaredSignatures,
      returnTypesBySignature,
      functions,
      envByFunctionSignature,
      externPrototypes,
      mutabilitiesByCitizenRef,
      declaredStructs,
      structDefsByRef,
      envByStructRef,
      declaredInterfaces,
      interfaceDefsByRef,
      envByInterfaceRef,
      impls,
      packTypes,
      arraySequenceTypes,
      unknownSizeArrayTypes) = program

    val edgeBlueprints =
      EdgeTemplar.makeInterfaceEdgeBlueprints(functions, program.getAllInterfaces())
    val partialEdges =
      EdgeTemplar.assemblePartialEdges(functions, program.getAllInterfaces(), impls)
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



    val reachables = Reachability.findReachables(program, edgeBlueprintsAsList, edges)

    val categorizedFunctions = functions.groupBy(f => reachables.functions.contains(f.header.toSignature))
    val reachableFunctions = categorizedFunctions.getOrElse(true, List())
    val unreachableFunctions = categorizedFunctions.getOrElse(false, List())
    unreachableFunctions.foreach(f => debugOut("Shaking out unreachable: " + f.header.fullName))
    reachableFunctions.foreach(f => debugOut("Including: " + f.header.fullName))

    val categorizedStructs = program.getAllStructs().groupBy(f => reachables.structs.contains(f.getRef))
    val reachableStructs = categorizedStructs.getOrElse(true, List())
    val unreachableStructs = categorizedStructs.getOrElse(false, List())
    unreachableStructs.foreach(f => debugOut("Shaking out unreachable: " + f.fullName))
    reachableStructs.foreach(f => debugOut("Including: " + f.fullName))

    val categorizedInterfaces = program.getAllInterfaces().groupBy(f => reachables.interfaces.contains(f.getRef))
    val reachableInterfaces = categorizedInterfaces.getOrElse(true, List())
    val unreachableInterfaces = categorizedInterfaces.getOrElse(false, List())
    unreachableInterfaces.foreach(f => debugOut("Shaking out unreachable: " + f.fullName))
    reachableInterfaces.foreach(f => debugOut("Including: " + f.fullName))

    val categorizedEdges = edges.groupBy(f => reachables.edges.contains(f))
    val reachableEdges = categorizedEdges.getOrElse(true, List())
    val unreachableEdges = categorizedEdges.getOrElse(false, List())
    unreachableEdges.foreach(f => debugOut("Shaking out unreachable: " + f))
    reachableEdges.foreach(f => debugOut("Including: " + f))

    Hinputs(
      reachableInterfaces,
      reachableStructs,
      Program2.emptyTupleStructRef,
      reachableFunctions,
      externPrototypes.toList,
      edgeBlueprintsByInterface,
      edges)
  }
}
