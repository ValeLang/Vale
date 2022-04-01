package dev.vale.templar.citizen

import dev.vale.astronomer.{FunctionA, InterfaceA, StructA}
import dev.vale.scout.{IFunctionDeclarationNameS, IRuneS}
import dev.vale.templar.env.{CitizenEnvironment, IEnvironment, TemplataEnvEntry, TemplatasStore}
import dev.vale.templar.{TemplarOptions, Temputs, env}
import dev.vale.templar.names.{NameTranslator, RuneNameT}
import dev.vale.templar.templata.{FunctionTemplata, ITemplata}
import dev.vale.templar.types.{InterfaceTT, MutabilityT, StructMemberT, StructTT}
import dev.vale.{Interner, RangeS}
import dev.vale.astronomer.FunctionA
import dev.vale.templar.types._
import dev.vale.templar.templata._
import dev.vale.scout._
import dev.vale.templar._
import dev.vale.templar.ast._
import dev.vale.templar.env.CitizenEnvironment
import dev.vale.templar.function.FunctionTemplar
import dev.vale.templar.names.AnonymousSubstructNameT
import dev.vale.{Interner, Profiler, RangeS, vfail, vimpl}

import scala.collection.immutable.List

class StructTemplarMiddle(
    opts: TemplarOptions,

    interner: Interner,
    nameTranslator: NameTranslator,

    ancestorHelper: AncestorHelper,
    delegate: IStructTemplarDelegate) {
  val core = new StructTemplarCore(opts, interner, nameTranslator, ancestorHelper, delegate)

  def getStructRef(
    structOuterEnv: IEnvironment,
    temputs: Temputs,
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
        localEnv, temputs, structS, coercedFinalTemplateArgs2);

    (structDefinition2.getRef)
  }

  def getInterfaceRef(
    interfaceOuterEnv: IEnvironment,
    temputs: Temputs,
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
        localEnv, temputs, interfaceA, coercedFinalTemplateArgs2);

    (interfaceDefinition2.getRef)
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    temputs: Temputs,
    name: IFunctionDeclarationNameS,
    functionS: FunctionA,
    members: Vector[StructMemberT]):
  (StructTT, MutabilityT, FunctionTemplata) = {
    core.makeClosureUnderstruct(containingFunctionEnv, temputs, name, functionS, members)
  }
}
