package dev.vale.typing.citizen

import dev.vale.highertyping.FunctionA
import dev.vale.{Interner, Keywords, Profiler, RangeS, vcurious, _}
import dev.vale.postparsing.{IFunctionDeclarationNameS, IImpreciseNameS, IRuneS, IEnvironment => _}
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.typing.ast.{FunctionHeaderT, PrototypeT}
import dev.vale.typing.env.IEnvironment
import dev.vale.typing.{CompilerOutputs, InferCompiler, TypingPassOptions}
import dev.vale.typing.names.NameTranslator
import dev.vale.typing.templata.{FunctionTemplata, ITemplata, InterfaceTemplata, StructTemplata}
import dev.vale.typing.types.{AddressMemberTypeT, CoordT, ImmutableT, InterfaceTT, MutabilityT, MutableT, ParamFilter, ReferenceMemberTypeT, ShareT, StructMemberT, StructTT}
import dev.vale.highertyping._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.parsing._
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.postparsing.rules._
import dev.vale.typing._
import dev.vale.typing.env._
import dev.vale.typing.function.FunctionCompiler
import dev.vale.typing.ast._
import dev.vale.typing.names.ICitizenNameT

import scala.collection.immutable.List
import scala.collection.mutable

case class WeakableImplingMismatch(structWeakable: Boolean, interfaceWeakable: Boolean) extends Throwable { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

trait IStructCompilerDelegate {
  def evaluateOrdinaryFunctionFromNonCallForHeader(
    coutputs: CompilerOutputs,
    functionTemplata: FunctionTemplata):
  FunctionHeaderT

  def evaluateTemplatedFunctionFromNonCallForHeader(
    coutputs: CompilerOutputs,
    functionTemplata: FunctionTemplata):
  FunctionHeaderT

  def scoutExpectedFunctionForPrototype(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
    args: Vector[ParamFilter],
    extraEnvsToLookIn: Vector[IEnvironment],
    exact: Boolean):
  PrototypeT
}

class StructCompiler(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    nameTranslator: NameTranslator,
    inferCompiler: InferCompiler,
    ancestorHelper: AncestorHelper,
    delegate: IStructCompilerDelegate) {
  val templateArgsLayer =
    new StructCompilerGenericArgsLayer(
      opts, interner, keywords, nameTranslator, inferCompiler, ancestorHelper, delegate)

  def getStructRef(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    structTemplata: StructTemplata,
    uncoercedTemplateArgs: Vector[ITemplata]):
  (StructTT) = {
    Profiler.frame(() => {
      templateArgsLayer.getStructRef(
        coutputs, callRange, structTemplata, uncoercedTemplateArgs)
    })
  }

  def getInterfaceRef(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceTemplata,
    uncoercedTemplateArgs: Vector[ITemplata]):
  (InterfaceTT) = {
//    Profiler.reentrant("StructCompiler-getInterfaceRef", interfaceTemplata.debugString + "<" + uncoercedTemplateArgs.mkString(", ") + ">", () => {
      templateArgsLayer.getInterfaceRef(
        coutputs, callRange, interfaceTemplata, uncoercedTemplateArgs)
//    })
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    coutputs: CompilerOutputs,
    name: IFunctionDeclarationNameS,
    functionS: FunctionA,
    members: Vector[StructMemberT]):
  (StructTT, MutabilityT, FunctionTemplata) = {
//    Profiler.reentrant("StructCompiler-makeClosureUnderstruct", name.codeLocation.toString, () => {
      templateArgsLayer.makeClosureUnderstruct(containingFunctionEnv, coutputs, name, functionS, members)
//    })
  }

  def getMemberCoords(coutputs: CompilerOutputs, structTT: StructTT): Vector[CoordT] = {
    coutputs.getStructDefForRef(structTT).members.map(_.tyype).map({
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

object StructCompiler {
  def getCompoundTypeMutability(memberTypes2: Vector[CoordT])
  : MutabilityT = {
    val membersOwnerships = memberTypes2.map(_.ownership)
    val allMembersImmutable = membersOwnerships.isEmpty || membersOwnerships.toSet == Set(ShareT)
    if (allMembersImmutable) ImmutableT else MutableT
  }
}