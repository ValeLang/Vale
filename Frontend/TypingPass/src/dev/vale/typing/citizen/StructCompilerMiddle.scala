package dev.vale.typing.citizen

import dev.vale.highertyping.{FunctionA, InterfaceA, StructA}
import dev.vale.postparsing.{IFunctionDeclarationNameS, IRuneS}
import dev.vale.typing.env.{CitizenEnvironment, IEnvironment, TemplataEnvEntry, TemplatasStore}
import dev.vale.typing.{CompilerOutputs, TypingPassOptions, env}
import dev.vale.typing.names.{NameTranslator, RuneNameT}
import dev.vale.typing.templata.{FunctionTemplata, ITemplata}
import dev.vale.typing.types.{InterfaceTT, MutabilityT, StructMemberT, StructTT}
import dev.vale.{Interner, Keywords, Profiler, RangeS, vfail, vimpl}
import dev.vale.highertyping.FunctionA
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.postparsing._
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env.CitizenEnvironment
import dev.vale.typing.function.FunctionCompiler
import dev.vale.typing.names.AnonymousSubstructNameT

import scala.collection.immutable.List

class StructCompilerMiddle(
    opts: TypingPassOptions,
    interner: Interner,
  keywords: Keywords,
    nameTranslator: NameTranslator,

    ancestorHelper: AncestorHelper,
    delegate: IStructCompilerDelegate) {
  val core = new StructCompilerCore(opts, interner, keywords, nameTranslator, ancestorHelper, delegate)

  def getStructRef(
    structOuterEnv: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    structS: StructA,
    templatasByRune: Map[IRuneS, ITemplata]):
  (StructTT) = {
    val coercedFinalTemplateArgs2 = structS.identifyingRunes.map(_.rune).map(templatasByRune)

    val localEnv =
      CitizenEnvironment(
        structOuterEnv.globalEnv,
        structOuterEnv,
        structOuterEnv.fullName,
        TemplatasStore(structOuterEnv.fullName, Map(), Map())
          .addEntries(
            interner,
            templatasByRune.toVector
              .map({ case (rune, templata) => (interner.intern(RuneNameT(rune)), TemplataEnvEntry(templata)) })))
    val structDefinition2 =
      core.makeStruct(
        localEnv, coutputs, structS, coercedFinalTemplateArgs2);

    (structDefinition2.getRef)
  }

  def getInterfaceRef(
    interfaceOuterEnv: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    interfaceA: InterfaceA,
    templatasByRune: Map[IRuneS, ITemplata]):
  (InterfaceTT) = {
    val coercedFinalTemplateArgs2 = interfaceA.identifyingRunes.map(_.rune).map(templatasByRune)

    val localEnv =
      env.CitizenEnvironment(
        interfaceOuterEnv.globalEnv,
        interfaceOuterEnv,
        interfaceOuterEnv.fullName,
        env.TemplatasStore(interfaceOuterEnv.fullName, Map(), Map())
          .addEntries(
            interner,
            templatasByRune.toVector
              .map({ case (rune, templata) => (interner.intern(RuneNameT(rune)), TemplataEnvEntry(templata)) })))
    val interfaceDefinition2 =
      core.makeInterface(
        localEnv, coutputs, interfaceA, coercedFinalTemplateArgs2);

    (interfaceDefinition2.getRef)
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    coutputs: CompilerOutputs,
    name: IFunctionDeclarationNameS,
    functionS: FunctionA,
    members: Vector[StructMemberT]):
  (StructTT, MutabilityT, FunctionTemplata) = {
    core.makeClosureUnderstruct(containingFunctionEnv, coutputs, name, functionS, members)
  }
}
