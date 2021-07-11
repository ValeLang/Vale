package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer.{ImplA, ImplImpreciseNameA, ImplNameA}
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.infer.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.{IProfiler, vassertSome, vfail, vimpl, vwat}

import scala.collection.immutable.List

trait IAncestorHelperDelegate {
  def getInterfaceRef(
    temputs: Temputs,
    callRange: RangeS,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceTemplata,
    uncoercedTemplateArgs: List[ITemplata]):
  InterfaceTT
}

class AncestorHelper(
    opts: TemplarOptions,
    profiler: IProfiler,
    inferTemplar: InferTemplar,
    delegate: IAncestorHelperDelegate) {

  private def getMaybeImplementedInterface(
    temputs: Temputs,
    childCitizenRef: CitizenRefT,
    implTemplata: ImplTemplata):
  (Option[InterfaceTT]) = {
    val ImplTemplata(env, impl) = implTemplata
    val ImplA(range, codeLocation, rulesFromStructDirection, rulesFromInterfaceDirection, typeByRune, localRunes, structKindRune, interfaceKindRune) = impl

    // We use the rules from the struct direction because they'll fail faster, and we won't accidentally evaluate a ton
    // of things we would otherwise. See NMORFI for more.
    val rules = rulesFromStructDirection

    val result =
      profiler.childFrame("getMaybeImplementedInterface", () => {
        inferTemplar.inferFromExplicitTemplateArgs(
          env,
          temputs,
          List(structKindRune),
          rules,
          typeByRune,
          localRunes,
          List.empty,
          None,
          RangeS.internal(-1875),
          List(KindTemplata(childCitizenRef)))
      })

    result match {
      case isf @ InferSolveFailure(_, _, _, _, _, _, _) => {
        val _ = isf
        (None)
      }
      case InferSolveSuccess(inferences) => {
        inferences.templatasByRune(NameTranslator.translateRune(interfaceKindRune)) match {
          case KindTemplata(interfaceTT @ InterfaceTT(_)) => {
            (Some(interfaceTT))
          }
          case KindTemplata(sr @ StructTT(_)) => {
            throw CompileErrorExceptionT(CantImplStruct(range, sr))
          }
          case it @ InterfaceTemplata(_, _) => {
            val interfaceTT =
              delegate.getInterfaceRef(temputs, vimpl(), it, List.empty)
            (Some(interfaceTT))
          }
        }
      }
    }
  }

  def getParentInterfaces(
    temputs: Temputs,
    childCitizenRef: CitizenRefT):
  (List[InterfaceTT]) = {
    val needleImplName =
      NameTranslator.getImplNameForName(opts.useOptimization, childCitizenRef) match {
        case None => return List.empty
        case Some(x) => x
      }

    val citizenEnv =
      childCitizenRef match {
        case sr @ StructTT(_) => temputs.getEnvForStructRef(sr)
        case ir @ InterfaceTT(_) => temputs.getEnvForInterfaceRef(ir)
      }
    citizenEnv.getAllTemplatasWithName(profiler, needleImplName, Set(TemplataLookupContext, ExpressionLookupContext))
      .flatMap({
        case it @ ImplTemplata(_, _) => getMaybeImplementedInterface(temputs, childCitizenRef, it).toList
        case ExternImplTemplata(structTT, interfaceTT) => if (structTT == childCitizenRef) List(interfaceTT) else List.empty
        case other => vwat(other.toString)
      })
  }

  def getAncestorInterfaces(
    temputs: Temputs,
    descendantCitizenRef: CitizenRefT):
  (Set[InterfaceTT]) = {
    profiler.childFrame("getAncestorInterfaces", () => {
      val ancestorInterfacesWithDistance =
        getAncestorInterfacesWithDistance(temputs, descendantCitizenRef)
      (ancestorInterfacesWithDistance.keySet)
    })
  }

  def isAncestor(
    temputs: Temputs,
    descendantCitizenRef: CitizenRefT,
    ancestorInterfaceRef: InterfaceTT):
  (Boolean) = {
    profiler.childFrame("isAncestor", () => {
      val ancestorInterfacesWithDistance =
        getAncestorInterfacesWithDistance(temputs, descendantCitizenRef)
      (ancestorInterfacesWithDistance.contains(ancestorInterfaceRef))
    })
  }

  def getAncestorInterfaceDistance(
    temputs: Temputs,
    descendantCitizenRef: CitizenRefT,
    ancestorInterfaceRef: InterfaceTT):
  (Option[Int]) = {
    profiler.childFrame("getAncestorInterfaceDistance", () => {
      val ancestorInterfacesWithDistance =
        getAncestorInterfacesWithDistance(temputs, descendantCitizenRef)
      (ancestorInterfacesWithDistance.get(ancestorInterfaceRef))
    })
  }

  // Doesn't include self
  def getAncestorInterfacesWithDistance(
    temputs: Temputs,
    descendantCitizenRef: CitizenRefT):
  (Map[InterfaceTT, Int]) = {
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
    temputs: Temputs,
    // This is so we can know what we've already searched.
    nearestDistanceByInterfaceRef: Map[InterfaceTT, Int],
    // All the interfaces that are at most this distance away are inside foundSoFar.
    currentDistance: Int,
    // These are the interfaces that are *exactly* currentDistance away.
    // We will do our searching from here.
    interfacesAtCurrentDistance: Set[InterfaceTT]):
  (Map[InterfaceTT, Int]) = {
    val interfacesAtNextDistance =
      interfacesAtCurrentDistance.foldLeft((Set[InterfaceTT]()))({
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
