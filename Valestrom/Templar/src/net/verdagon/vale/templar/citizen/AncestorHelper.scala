package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer.ImplA
import net.verdagon.vale.scout.ImplImpreciseNameS
import net.verdagon.vale.scout.rules.Equivalencies
import net.verdagon.vale.solver.SolverErrorHumanizer
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.names.NameTranslator
import net.verdagon.vale.{Err, Profiler, Interner, Ok, RangeS, vassertSome, vfail, vimpl, vwat}

import scala.collection.immutable.List

trait IAncestorHelperDelegate {
  def getInterfaceRef(
    temputs: Temputs,
    callRange: RangeS,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceTemplata,
    uncoercedTemplateArgs: Vector[ITemplata]):
  InterfaceTT
}

class AncestorHelper(
    opts: TemplarOptions,

    interner: Interner,
    inferTemplar: InferTemplar,
    delegate: IAncestorHelperDelegate) {

  private def getMaybeImplementedInterface(
    temputs: Temputs,
    childCitizenRef: CitizenRefT,
    implTemplata: ImplTemplata):
  (Option[(InterfaceTT, ImplTemplata)]) = {
    val ImplTemplata(env, impl) = implTemplata
    val ImplA(range, name, impreciseName, identifyingRunes, rules, runeToType, structKindRune, interfaceKindRune) = impl

    val result =
      inferTemplar.solveComplete(
        env,
        temputs,
        rules,
        runeToType,
        RangeS.internal(-1875),
        Vector(InitialKnown(structKindRune, KindTemplata(childCitizenRef))),
        Vector())

    result match {
      case Err(e) => {
        val _ = e
        (None)
      }
      case Ok(inferences) => {
        inferences(interfaceKindRune.rune) match {
          case KindTemplata(interfaceTT @ InterfaceTT(_)) => {
            (Some((interfaceTT, implTemplata)))
          }
          case it @ InterfaceTemplata(_, _) => {
            val interfaceTT =
              delegate.getInterfaceRef(temputs, RangeS.internal(-1875), it, Vector.empty)
            (Some((interfaceTT, implTemplata)))
          }
          case KindTemplata(other) => {
            throw CompileErrorExceptionT(CantImplNonInterface(range, other))
          }
        }
      }
    }
  }

  private def getMatchingImpls(
    temputs: Temputs,
    childCitizenRef: CitizenRefT):
  (Vector[ImplTemplata]) = {

    // See INSHN, the imprecise name for an impl is the wrapped imprecise name of its struct template.
    val needleImplName =
      TemplatasStore.getImpreciseName(interner, childCitizenRef.fullName.last) match {
        case None => return Vector.empty
        case Some(x) => interner.intern(ImplImpreciseNameS(x))
      }
    val citizenEnv =
      childCitizenRef match {
        case sr @ StructTT(_) => temputs.getEnvForKind(sr)
        case ir @ InterfaceTT(_) => temputs.getEnvForKind(ir)
      }
    citizenEnv.lookupAllWithImpreciseName(needleImplName, Set(TemplataLookupContext, ExpressionLookupContext))
      .map({
        case it @ ImplTemplata(_, _) => it
        //        case ExternImplTemplata(structTT, interfaceTT) => if (structTT == childCitizenRef) Vector(interfaceTT) else Vector.empty
        case other => vwat(other.toString)
      })
      .toVector
  }

  def getParentInterfaces(
    temputs: Temputs,
    childCitizenRef: CitizenRefT):
  (Vector[(InterfaceTT, ImplTemplata)]) = {
    Profiler.frame(() => {
      getMatchingImpls(temputs, childCitizenRef).flatMap({
        case it@ImplTemplata(_, _) => getMaybeImplementedInterface(temputs, childCitizenRef, it)
        case other => vwat(other.toString)
      })
    })
  }

  def isAncestor(
    temputs: Temputs,
    descendantCitizenRef: CitizenRefT,
    ancestorInterfaceRef: InterfaceTT):
  (Option[ImplTemplata]) = {
    val ancestorInterfacesWithDistance =
      getAncestorInterfaces(temputs, descendantCitizenRef)
    (ancestorInterfacesWithDistance.get(ancestorInterfaceRef))
  }

  // Doesn't include self
  def getAncestorInterfaces(
    temputs: Temputs,
    descendantCitizenRef: CitizenRefT):
  (Map[InterfaceTT, ImplTemplata]) = {
    Profiler.frame(() => {
      val parentInterfacesAndImpls =
        getParentInterfaces(temputs, descendantCitizenRef)

      // Make a map that contains all the parent interfaces, with distance 1
      val foundSoFar =
        parentInterfacesAndImpls.map({ case (interfaceRef, impl) => (interfaceRef, impl) }).toMap

      getAncestorInterfacesInner(
        temputs,
        foundSoFar,
        parentInterfacesAndImpls.toMap)
    })
  }

  private def getAncestorInterfacesInner(
    temputs: Temputs,
    // This is so we can know what we've already searched.
    nearestDistanceByInterfaceRef: Map[InterfaceTT, ImplTemplata],
    // These are the interfaces that are *exactly* currentDistance away.
    // We will do our searching from here.
    interfacesAtCurrentDistance: Map[InterfaceTT, ImplTemplata]):
  (Map[InterfaceTT, ImplTemplata]) = {
    val interfacesAtNextDistance =
      interfacesAtCurrentDistance.foldLeft((Map[InterfaceTT, ImplTemplata]()))({
        case ((previousAncestorInterfaceRefs), (parentInterfaceRef, parentImpl)) => {
          val parentAncestorInterfaceRefs =
            getParentInterfaces(temputs, parentInterfaceRef)
          (previousAncestorInterfaceRefs ++ parentAncestorInterfaceRefs)
        }
      })

    // Discard the ones that have already been found; they're actually at
    // a closer distance.
    val newlyFoundInterfaces =
      interfacesAtNextDistance.keySet
        .diff(nearestDistanceByInterfaceRef.keySet)
        .toVector
        .map(key => (key -> interfacesAtNextDistance(key)))
        .toMap

    if (newlyFoundInterfaces.isEmpty) {
      (nearestDistanceByInterfaceRef)
    } else {
      // Combine the previously found ones with the newly found ones.
      val newNearestDistanceByInterfaceRef =
        nearestDistanceByInterfaceRef ++ newlyFoundInterfaces.toMap

      getAncestorInterfacesInner(
        temputs,
        newNearestDistanceByInterfaceRef,
        newlyFoundInterfaces)
    }
  }
}
