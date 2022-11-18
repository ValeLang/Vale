package dev.vale.typing.citizen

import dev.vale.highertyping.FunctionA
import dev.vale.{Interner, Keywords, Profiler, RangeS, vcurious, _}
import dev.vale.postparsing._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.typing.ast.{FunctionHeaderT, PrototypeT}
import dev.vale.typing.env.IEnvironment
import dev.vale.typing.{CompilerOutputs, IIncompleteOrFailedCompilerSolve, InferCompiler, TypingPassOptions, _}
import dev.vale.typing.names.{FullNameT, ICitizenNameT, ICitizenTemplateNameT, IInterfaceTemplateNameT, IStructTemplateNameT, ITemplateNameT, NameTranslator, PackageTopLevelNameT}
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.highertyping._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.parsing._
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.postparsing.rules._
import dev.vale.typing.env._
import dev.vale.typing.function.FunctionCompiler
import dev.vale.typing.ast._
import dev.vale.typing.function.FunctionCompiler.{EvaluateFunctionSuccess, IEvaluateFunctionResult}
import dev.vale.typing.templata.ITemplata.expectMutability

import scala.collection.immutable.List
import scala.collection.mutable

case class WeakableImplingMismatch(structWeakable: Boolean, interfaceWeakable: Boolean) extends Throwable { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

trait IStructCompilerDelegate {
  def evaluateGenericFunctionFromNonCallForHeader(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    functionTemplata: FunctionTemplata,
    verifyConclusions: Boolean):
  FunctionHeaderT
//
//  def evaluateGenericLightFunctionFromCallForPrototype(
//    coutputs: CompilerOutputs,
//    callRange: List[RangeS],
//    callingEnv: IEnvironment, // See CSSNCE
//    functionTemplata: FunctionTemplata,
//    explicitTemplateArgs: Vector[ITemplata[ITemplataType]],
//    args: Vector[Option[CoordT]]):
//  IEvaluateFunctionResult

  def scoutExpectedFunctionForPrototype(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
    functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Vector[IRuneS],
    args: Vector[CoordT],
    extraEnvsToLookIn: Vector[IEnvironment],
    exact: Boolean,
    verifyConclusions: Boolean):
  EvaluateFunctionSuccess
}

sealed trait IResolveOutcome[+T <: KindT] {
  def expect(): ResolveSuccess[T]
}
case class ResolveSuccess[+T <: KindT](kind: T) extends IResolveOutcome[T] {
  override def expect(): ResolveSuccess[T] = this
}
case class ResolveFailure[+T <: KindT](range: List[RangeS], x: IIncompleteOrFailedCompilerSolve) extends IResolveOutcome[T] {
  override def expect(): ResolveSuccess[T] = {
    throw CompileErrorExceptionT(TypingPassSolverError(range, x))
  }
}

class StructCompiler(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    nameTranslator: NameTranslator,
    templataCompiler: TemplataCompiler,
    inferCompiler: InferCompiler,
    delegate: IStructCompilerDelegate) {
  val templateArgsLayer =
    new StructCompilerGenericArgsLayer(
      opts, interner, keywords, nameTranslator, templataCompiler, inferCompiler, delegate)

  def resolveStruct(
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    structTemplata: StructDefinitionTemplata,
    uncoercedTemplateArgs: Vector[ITemplata[ITemplataType]]):
  IResolveOutcome[StructTT] = {
    Profiler.frame(() => {
      templateArgsLayer.resolveStruct(
        coutputs, callingEnv, callRange, structTemplata, uncoercedTemplateArgs)
    })
  }

  def precompileStruct(
    coutputs: CompilerOutputs,
    structTemplata: StructDefinitionTemplata):
  Unit = {
    val StructDefinitionTemplata(declaringEnv, structA) = structTemplata

    val structTemplateFullName = templataCompiler.resolveStructTemplate(structTemplata)

    coutputs.declareType(structTemplateFullName)

    structA.maybePredictedMutability match {
      case None =>
      case Some(predictedMutability) => {
        coutputs.declareTypeMutability(
          structTemplateFullName,
          MutabilityTemplata(Conversions.evaluateMutability(predictedMutability)))
      }
    }

    // We declare the struct's outer environment this early because of MDATOEF.
    val outerEnv =
      CitizenEnvironment(
        declaringEnv.globalEnv,
        declaringEnv,
        structTemplateFullName,
        structTemplateFullName,
        TemplatasStore(structTemplateFullName, Map(), Map())
          .addEntries(
            interner,
            // Merge in any things from the global environment that say they're part of this
            // structs's namespace (see IMRFDI and CODME).
            // StructFreeMacro will put a free function here.
            declaringEnv.globalEnv.nameToTopLevelEnvironment
              .get(structTemplateFullName.addStep(interner.intern(PackageTopLevelNameT())))
              .toVector
              .flatMap(_.entriesByNameT)))
    coutputs.declareTypeOuterEnv(structTemplateFullName, outerEnv)
  }

  def precompileInterface(
    coutputs: CompilerOutputs,
    interfaceTemplata: InterfaceDefinitionTemplata):
  Unit = {
    val InterfaceDefinitionTemplata(declaringEnv, interfaceA) = interfaceTemplata

    val interfaceTemplateFullName = templataCompiler.resolveInterfaceTemplate(interfaceTemplata)

    coutputs.declareType(interfaceTemplateFullName)

    interfaceA.maybePredictedMutability match {
      case None =>
      case Some(predictedMutability) => {
        coutputs.declareTypeMutability(
          interfaceTemplateFullName,
          MutabilityTemplata(Conversions.evaluateMutability(predictedMutability)))
      }
    }

    // We do this here because we might compile a virtual function somewhere before we compile the interface.
    // The virtual function will need to know if the type is sealed to know whether it's allowed to be
    // virtual on this interface.
    coutputs.declareTypeSealed(interfaceTemplateFullName, interfaceA.attributes.contains(SealedS))


    // We declare the interface's outer environment this early because of MDATOEF.
    val outerEnv =
      CitizenEnvironment(
        declaringEnv.globalEnv,
        declaringEnv,
        interfaceTemplateFullName,
        interfaceTemplateFullName,
        TemplatasStore(interfaceTemplateFullName, Map(), Map())
          .addEntries(
            interner,
            // TODO: Take those internal methods that were defined inside the interface, and move them to
            // just be name-prefixed like Free is, see IMRFDI.
            interfaceA.internalMethods
              .map(internalMethod => {
                val functionName = nameTranslator.translateGenericFunctionName(internalMethod.name)
                (functionName -> FunctionEnvEntry(internalMethod))
              }) ++
              // Merge in any things from the global environment that say they're part of this
              // interface's namespace (see IMRFDI and CODME).
              declaringEnv.globalEnv.nameToTopLevelEnvironment
                .get(interfaceTemplateFullName.addStep(interner.intern(PackageTopLevelNameT())))
                .toVector
                .flatMap(_.entriesByNameT)))
    coutputs.declareTypeOuterEnv(interfaceTemplateFullName, outerEnv)
  }

  def compileStruct(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    structTemplata: StructDefinitionTemplata):
  Unit = {
    Profiler.frame(() => {
      templateArgsLayer.compileStruct(coutputs, parentRanges, structTemplata)
    })
  }

  // See SFWPRL for how this is different from resolveInterface.
  def predictInterface(
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceDefinitionTemplata,
    uncoercedTemplateArgs: Vector[ITemplata[ITemplataType]]):
  (InterfaceTT) = {
    templateArgsLayer.predictInterface(
      coutputs, callingEnv, callRange, interfaceTemplata, uncoercedTemplateArgs)
  }

  // See SFWPRL for how this is different from resolveStruct.
  def predictStruct(
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    structTemplata: StructDefinitionTemplata,
    uncoercedTemplateArgs: Vector[ITemplata[ITemplataType]]):
  (StructTT) = {
    templateArgsLayer.predictStruct(
      coutputs, callingEnv, callRange, structTemplata, uncoercedTemplateArgs)
  }

  def resolveInterface(
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceDefinitionTemplata,
    uncoercedTemplateArgs: Vector[ITemplata[ITemplataType]]):
  IResolveOutcome[InterfaceTT] = {
    val success =
      templateArgsLayer.resolveInterface(
        coutputs, callingEnv, callRange, interfaceTemplata, uncoercedTemplateArgs)

    success
  }

  def resolveCitizen(
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    citizenTemplata: CitizenDefinitionTemplata,
    uncoercedTemplateArgs: Vector[ITemplata[ITemplataType]]):
  IResolveOutcome[ICitizenTT] = {
    citizenTemplata match {
      case st @ StructDefinitionTemplata(_, _) => resolveStruct(coutputs, callingEnv, callRange, st, uncoercedTemplateArgs)
      case it @ InterfaceDefinitionTemplata(_, _) => resolveInterface(coutputs, callingEnv, callRange, it, uncoercedTemplateArgs)
    }
  }

  def compileInterface(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceDefinitionTemplata):
  Unit = {
    templateArgsLayer.compileInterface(
      coutputs, parentRanges, interfaceTemplata)
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: NodeEnvironment,
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    name: IFunctionDeclarationNameS,
    functionS: FunctionA,
    members: Vector[NormalStructMemberT]):
  (StructTT, MutabilityT, FunctionTemplata) = {
//    Profiler.reentrant("StructCompiler-makeClosureUnderstruct", name.codeLocation.toString, () => {
      templateArgsLayer.makeClosureUnderstruct(containingFunctionEnv, coutputs, parentRanges, name, functionS, members)
//    })
  }

//  def getMemberCoords(coutputs: CompilerOutputs, structTT: StructTT): Vector[CoordT] = {
//    coutputs.lookupStruct(structTT).members.map(_.tyype).map({
//      case ReferenceMemberTypeT(coord) => coord
//      case AddressMemberTypeT(_) => {
//        // At time of writing, the only one who calls this is the inferer, who wants to know so it
//        // can match incoming arguments into a destructure. Can we even destructure things with
//        // addressible members?
//        vcurious()
//      }
//    })
//  }

}

object StructCompiler {
  def getCompoundTypeMutability(memberTypes2: Vector[CoordT])
  : MutabilityT = {
    val membersOwnerships = memberTypes2.map(_.ownership)
    val allMembersImmutable = membersOwnerships.isEmpty || membersOwnerships.toSet == Set(ShareT)
    if (allMembersImmutable) ImmutableT else MutableT
  }

  def getMutability(
    interner: Interner,
    keywords: Keywords,
    coutputs: CompilerOutputs,
    structTT: StructTT,
    boundArgumentsSource: IBoundArgumentsSource):
  ITemplata[MutabilityTemplataType] = {
    val definition = coutputs.lookupStruct(structTT)
    val transformer =
      TemplataCompiler.getPlaceholderSubstituter(interner, keywords, structTT.fullName, boundArgumentsSource)
    val result = transformer.substituteForTemplata(coutputs, definition.mutability)
    ITemplata.expectMutability(result)
  }
}