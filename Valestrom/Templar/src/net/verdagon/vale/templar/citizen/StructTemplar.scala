package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplar, FunctionTemplarCore, FunctionTemplarMiddleLayer}
import net.verdagon.vale._
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.citizen.StructTemplarTemplateArgsLayer
import net.verdagon.vale.templar.names.{ICitizenNameT, INameT, NameTranslator}

import scala.collection.immutable.List
import scala.collection.mutable

case class WeakableImplingMismatch(structWeakable: Boolean, interfaceWeakable: Boolean) extends Throwable { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

trait IStructTemplarDelegate {
  def evaluateOrdinaryFunctionFromNonCallForHeader(
    temputs: Temputs,
    functionTemplata: FunctionTemplata):
  FunctionHeaderT

  def evaluateTemplatedFunctionFromNonCallForHeader(
    temputs: Temputs,
    functionTemplata: FunctionTemplata):
  FunctionHeaderT

  def scoutExpectedFunctionForPrototype(
    env: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
    args: Vector[ParamFilter],
    extraEnvsToLookIn: Vector[IEnvironment],
    exact: Boolean):
  PrototypeT
}

class StructTemplar(
    opts: TemplarOptions,
    interner: Interner,
    nameTranslator: NameTranslator,
    inferTemplar: InferTemplar,
    ancestorHelper: AncestorHelper,
    delegate: IStructTemplarDelegate) {
  val templateArgsLayer =
    new StructTemplarTemplateArgsLayer(
      opts, interner, nameTranslator, inferTemplar, ancestorHelper, delegate)

  def getStructRef(
    temputs: Temputs,
    callRange: RangeS,
    structTemplata: StructTemplata,
    uncoercedTemplateArgs: Vector[ITemplata]):
  (StructTT) = {
    Profiler.frame(() => {
      templateArgsLayer.getStructRef(
        temputs, callRange, structTemplata, uncoercedTemplateArgs)
    })
  }

  def getInterfaceRef(
    temputs: Temputs,
    callRange: RangeS,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceTemplata,
    uncoercedTemplateArgs: Vector[ITemplata]):
  (InterfaceTT) = {
//    Profiler.reentrant("StructTemplar-getInterfaceRef", interfaceTemplata.debugString + "<" + uncoercedTemplateArgs.mkString(", ") + ">", () => {
      templateArgsLayer.getInterfaceRef(
        temputs, callRange, interfaceTemplata, uncoercedTemplateArgs)
//    })
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    temputs: Temputs,
    name: IFunctionDeclarationNameS,
    functionS: FunctionA,
    members: Vector[StructMemberT]):
  (StructTT, MutabilityT, FunctionTemplata) = {
//    Profiler.reentrant("StructTemplar-makeClosureUnderstruct", name.codeLocation.toString, () => {
      templateArgsLayer.makeClosureUnderstruct(containingFunctionEnv, temputs, name, functionS, members)
//    })
  }

  def getMemberCoords(temputs: Temputs, structTT: StructTT): Vector[CoordT] = {
    temputs.getStructDefForRef(structTT).members.map(_.tyype).map({
      case ReferenceMemberTypeT(coord) => coord
      case AddressMemberTypeT(_) => {
        // At time of writing, the only one who calls this is the inferer, who wants to know so it
        // can match incoming arguments into a destructure. Can we even destructure things with
        // addressible members?
        vcurious()
      }
    })
  }

}

object StructTemplar {
  def getCompoundTypeMutability(memberTypes2: Vector[CoordT])
  : MutabilityT = {
    val membersOwnerships = memberTypes2.map(_.ownership)
    val allMembersImmutable = membersOwnerships.isEmpty || membersOwnerships.toSet == Set(ShareT)
    if (allMembersImmutable) ImmutableT else MutableT
  }
}