package dev.vale.typing.citizen

import dev.vale.highertyping.ImplA
import dev.vale.postparsing.ImplImpreciseNameS
import dev.vale.postparsing.rules.Equivalencies
import dev.vale.solver.SolverErrorHumanizer
import dev.vale.typing.env.{ExpressionLookupContext, TemplataLookupContext, TemplatasStore}
import dev.vale.typing.{CantImplNonInterface, CompileErrorExceptionT, InferCompiler, InitialKnown, TypingPassOptions, CompilerOutputs}
import dev.vale.typing.names.NameTranslator
import dev.vale.typing.templata.{ITemplata, ImplTemplata, InterfaceTemplata, KindTemplata}
import dev.vale.typing.types.{CitizenRefT, InterfaceTT, StructTT}
import dev.vale.{Err, Interner, Ok, Profiler, RangeS, postparsing, vwat}
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing._
import dev.vale.typing.env._
import dev.vale.{Err, Interner, Ok, Profiler, RangeS, vassertSome, vfail, vimpl, vwat}

import scala.collection.immutable.List

trait IAncestorHelperDelegate {
  def getInterfaceRef(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceTemplata,
    uncoercedTemplateArgs: Vector[ITemplata]):
  InterfaceTT
}

class AncestorHelper(
    opts: TypingPassOptions,

    interner: Interner,
    inferCompiler: InferCompiler,
    delegate: IAncestorHelperDelegate) {

  private def getMaybeImplementedInterface(
    coutputs: CompilerOutputs,
    childCitizenRef: CitizenRefT,
    implTemplata: ImplTemplata):
  (Option[(InterfaceTT, ImplTemplata)]) = {
    val ImplTemplata(env, impl) = implTemplata
    val ImplA(range, name, impreciseName, identifyingRunes, rules, runeToType, structKindRune, interfaceKindRune) = impl

    val result =
      inferCompiler.solveComplete(
        env,
        coutputs,
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
              delegate.getInterfaceRef(coutputs, RangeS.internal(-1875), it, Vector.empty)
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
    coutputs: CompilerOutputs,
    childCitizenRef: CitizenRefT):
  (Vector[ImplTemplata]) = {

    // See INSHN, the imprecise name for an impl is the wrapped imprecise name of its struct template.
    val needleImplName =
      TemplatasStore.getImpreciseName(interner, childCitizenRef.fullName.last) match {
        case None => return Vector.empty
        case Some(x) => interner.intern(postparsing.ImplImpreciseNameS(x))
      }
    val citizenEnv =
      childCitizenRef match {
        case sr @ StructTT(_) => coutputs.getEnvForKind(sr)
        case ir @ InterfaceTT(_) => coutputs.getEnvForKind(ir)
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
    coutputs: CompilerOutputs,
    childCitizenRef: CitizenRefT):
  (Vector[(InterfaceTT, ImplTemplata)]) = {
    Profiler.frame(() => {
      getMatchingImpls(coutputs, childCitizenRef).flatMap({
        case it@ImplTemplata(_, _) => getMaybeImplementedInterface(coutputs, childCitizenRef, it)
        case other => vwat(other.toString)
      })
    })
  }

  def isAncestor(
    coutputs: CompilerOutputs,
    descendantCitizenRef: CitizenRefT,
    ancestorInterfaceRef: InterfaceTT):
  (Option[ImplTemplata]) = {
    val ancestorInterfacesWithDistance =
      getAncestorInterfaces(coutputs, descendantCitizenRef)
    (ancestorInterfacesWithDistance.get(ancestorInterfaceRef))
  }

  // Doesn't include self
  def getAncestorInterfaces(
    coutputs: CompilerOutputs,
    descendantCitizenRef: CitizenRefT):
  (Map[InterfaceTT, ImplTemplata]) = {
    Profiler.frame(() => {
      val parentInterfacesAndImpls =
        getParentInterfaces(coutputs, descendantCitizenRef)

      // Make a map that contains all the parent interfaces, with distance 1
      val foundSoFar =
        parentInterfacesAndImpls.map({ case (interfaceRef, impl) => (interfaceRef, impl) }).toMap

      getAncestorInterfacesInner(
        coutputs,
        foundSoFar,
        parentInterfacesAndImpls.toMap)
    })
  }

  private def getAncestorInterfacesInner(
    coutputs: CompilerOutputs,
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
            getParentInterfaces(coutputs, parentInterfaceRef)
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
        coutputs,
        newNearestDistanceByInterfaceRef,
        newlyFoundInterfaces)
    }
  }
}
