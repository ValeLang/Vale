package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer.{ImplA, ImplImpreciseNameA, ImplNameA}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.infer.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.{vassertSome, vfail, vwat}

import scala.collection.immutable.List

trait IAncestorHelperDelegate {
  def getInterfaceRef(
    temputs: TemputsBox,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceTemplata,
    uncoercedTemplateArgs: List[ITemplata]):
  InterfaceRef2
}

class AncestorHelper(
    opts: TemplarOptions,
    inferTemplar: InferTemplar,
    delegate: IAncestorHelperDelegate) {

  private def getMaybeImplementedInterface(
    temputs: TemputsBox,
    childCitizenRef: CitizenRef2,
    implTemplata: ImplTemplata):
  (Option[InterfaceRef2]) = {
    val ImplTemplata(env, impl) = implTemplata
    val ImplA(codeLocation, rules, typeByRune, localRunes, structKindRune, interfaceKindRune) = impl

    val result =
      inferTemplar.inferFromExplicitTemplateArgs(
        env,
        temputs,
        List(structKindRune),
        rules,
        typeByRune,
        localRunes,
        List(),
        None,
        List(KindTemplata(childCitizenRef)))

    result match {
      case isf @ InferSolveFailure(_, _, _, _, _, _) => {
        val _ = isf
        (None)
      }
      case InferSolveSuccess(inferences) => {
        inferences.templatasByRune(NameTranslator.translateRune(interfaceKindRune)) match {
          case KindTemplata(interfaceRef @ InterfaceRef2(_)) => {
            (Some(interfaceRef))
          }
          case it @ InterfaceTemplata(_, _) => {
            val interfaceRef =
              delegate.getInterfaceRef(temputs, it, List())
            (Some(interfaceRef))
          }
        }
      }
    }
  }

  def getParentInterfaces(
    temputs: TemputsBox,
    childCitizenRef: CitizenRef2):
  (List[InterfaceRef2]) = {
    val citizenEnv =
      childCitizenRef match {
        case sr @ StructRef2(_) => vassertSome(temputs.envByStructRef.get(sr))
        case ir @ InterfaceRef2(_) => vassertSome(temputs.envByInterfaceRef.get(ir))
      }
    citizenEnv.getAllTemplatasWithName(ImplImpreciseNameA(), Set(TemplataLookupContext, ExpressionLookupContext))
      .flatMap({
        case it @ ImplTemplata(_, _) => getMaybeImplementedInterface(temputs, childCitizenRef, it).toList
        case ExternImplTemplata(structRef, interfaceRef) => if (structRef == childCitizenRef) List(interfaceRef) else List()
        case other => vwat(other.toString)
      })
  }

  def getAncestorInterfaces(
    temputs: TemputsBox,
    descendantCitizenRef: CitizenRef2):
  (Set[InterfaceRef2]) = {
    val ancestorInterfacesWithDistance =
      getAncestorInterfacesWithDistance(temputs, descendantCitizenRef)
    (ancestorInterfacesWithDistance.keySet)
  }

  def isAncestor(
    temputs: TemputsBox,
    descendantCitizenRef: CitizenRef2,
    ancestorInterfaceRef: InterfaceRef2):
  (Boolean) = {
    val ancestorInterfacesWithDistance =
      getAncestorInterfacesWithDistance(temputs, descendantCitizenRef)
    (ancestorInterfacesWithDistance.contains(ancestorInterfaceRef))
  }

  def getAncestorInterfaceDistance(
    temputs: TemputsBox,
    descendantCitizenRef: CitizenRef2,
    ancestorInterfaceRef: InterfaceRef2):
  (Option[Int]) = {
    val ancestorInterfacesWithDistance =
      getAncestorInterfacesWithDistance(temputs, descendantCitizenRef)
    (ancestorInterfacesWithDistance.get(ancestorInterfaceRef))
  }

  // Doesn't include self
  def getAncestorInterfacesWithDistance(
    temputs: TemputsBox,
    descendantCitizenRef: CitizenRef2):
  (Map[InterfaceRef2, Int]) = {
    val parentInterfaceRefs =
      getParentInterfaces(temputs, descendantCitizenRef)

    // Make a map that contains all the parent interfaces, with distance 1
    val foundSoFar = parentInterfaceRefs.map((_, 1)).toMap

    getAncestorInterfacesInner(
      temputs,
      foundSoFar,
      1,
      parentInterfaceRefs.toSet)
  }

  private def getAncestorInterfacesInner(
    temputs: TemputsBox,
    // This is so we can know what we've already searched.
    nearestDistanceByInterfaceRef: Map[InterfaceRef2, Int],
    // All the interfaces that are at most this distance away are inside foundSoFar.
    currentDistance: Int,
    // These are the interfaces that are *exactly* currentDistance away.
    // We will do our searching from here.
    interfacesAtCurrentDistance: Set[InterfaceRef2]):
  (Map[InterfaceRef2, Int]) = {
    val interfacesAtNextDistance =
      interfacesAtCurrentDistance.foldLeft((Set[InterfaceRef2]()))({
        case ((previousAncestorInterfaceRefs), parentInterfaceRef) => {
          val parentAncestorInterfaceRefs =
            getParentInterfaces(temputs, parentInterfaceRef)
          (previousAncestorInterfaceRefs ++ parentAncestorInterfaceRefs)
        }
      })
    val nextDistance = currentDistance + 1

    // Discard the ones that have already been found; they're actually at
    // a closer distance.
    val newlyFoundInterfaces =
      interfacesAtNextDistance.diff(nearestDistanceByInterfaceRef.keySet)

    if (newlyFoundInterfaces.isEmpty) {
      (nearestDistanceByInterfaceRef)
    } else {
      // Combine the previously found ones with the newly found ones.
      val newNearestDistanceByInterfaceRef =
        nearestDistanceByInterfaceRef ++
          newlyFoundInterfaces.map((_, nextDistance)).toMap

      getAncestorInterfacesInner(
        temputs,
        newNearestDistanceByInterfaceRef,
        nextDistance,
        newlyFoundInterfaces)
    }
  }
}
