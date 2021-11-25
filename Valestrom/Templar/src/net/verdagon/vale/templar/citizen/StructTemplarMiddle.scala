package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer.{FunctionA, InterfaceA, StructA}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.{LocationInFunctionEnvironment, PrototypeT}
import net.verdagon.vale.templar.env.{CitizenEnvironment, FunctionEnvironment, IEnvironment, InterfaceEnvEntry, PackageEnvironment, TemplataEnvEntry, TemplatasStore}
import net.verdagon.vale.templar.function.{FunctionTemplar, FunctionTemplarCore, VirtualTemplar}
import net.verdagon.vale.templar.names.{AnonymousSubstructNameT, FullNameT, ICitizenNameT, INameT, RuneNameT}
import net.verdagon.vale.{IProfiler, RangeS, vfail, vimpl}

import scala.collection.immutable.List

class StructTemplarMiddle(
    opts: TemplarOptions,
    profiler: IProfiler,

    ancestorHelper: AncestorHelper,
    delegate: IStructTemplarDelegate) {
  val core = new StructTemplarCore(opts, profiler, ancestorHelper, delegate)

  def addBuiltInStructs(env: PackageEnvironment[INameT], temputs: Temputs): Unit = {
    core.addBuiltInStructs(env, temputs)
  }

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
            templatasByRune.toVector
              .map({ case (rune, templata) => (RuneNameT(rune), TemplataEnvEntry(templata)) })))
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
      CitizenEnvironment(
        interfaceOuterEnv.globalEnv,
        interfaceOuterEnv,
        interfaceOuterEnv.fullName,
        TemplatasStore(interfaceOuterEnv.fullName, Map(), Map())
          .addEntries(
            templatasByRune.toVector
              .map({ case (rune, templata) => (RuneNameT(rune), TemplataEnvEntry(templata)) })))
    val interfaceDefinition2 =
      core.makeInterface(
        localEnv, temputs, interfaceA, coercedFinalTemplateArgs2);

// Now that we have an env, we can use it for the internal methods.
//      interfaceS.internalMethods.foldLeft(temputs)({
//        case (function) => {
//          FunctionTemplar.evaluateOrdinaryLightFunctionFromNonCallForTemputs(
//            temputs, FunctionTemplata(localEnv, function))
//        }
//      })

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

//  // Makes a struct to back a pack or tuple
//  def makeSeqOrPackUnderstruct(
//    env: PackageEnvironment[INameT],
//    temputs: Temputs,
//    memberTypes2: Vector[CoordT],
//    name: ICitizenNameT):
//  (StructTT, MutabilityT) = {
//    core.makeSeqOrPackUnderstruct(env, temputs, memberTypes2, name)
//  }
}
