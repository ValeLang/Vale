package dev.vale.templar.citizen

import dev.vale.astronomer.FunctionA
import dev.vale.{Interner, Profiler, RangeS, vcurious}
import dev.vale.scout.{IFunctionDeclarationNameS, IImpreciseNameS, IRuneS}
import dev.vale.scout.rules.IRulexSR
import dev.vale.templar.ast.{FunctionHeaderT, PrototypeT}
import dev.vale.templar.env.IEnvironment
import dev.vale.templar.{InferTemplar, TemplarOptions, Temputs}
import dev.vale.templar.names.NameTranslator
import dev.vale.templar.templata.{FunctionTemplata, ITemplata, InterfaceTemplata, StructTemplata}
import dev.vale.templar.types.{AddressMemberTypeT, CoordT, ImmutableT, InterfaceTT, MutabilityT, MutableT, ParamFilter, ReferenceMemberTypeT, ShareT, StructMemberT, StructTT}
import dev.vale.astronomer._
import dev.vale.templar.types._
import dev.vale.templar.templata._
import dev.vale.parser._
import dev.vale.scout._
import dev.vale.scout.patterns.AtomSP
import dev.vale.scout.rules._
import dev.vale.templar._
import dev.vale.templar.env._
import dev.vale.templar.function.FunctionTemplar
import dev.vale._
import dev.vale.templar.ast._
import dev.vale.templar.names.ICitizenNameT

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