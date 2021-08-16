package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer.{FunctionA, InterfaceA, LambdaNameA, StructA}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.{FunctionEnvironment, IEnvironment, TemplatasStore, InterfaceEnvEntry, PackageEnvironment, TemplataEnvEntry}
import net.verdagon.vale.templar.function.{FunctionTemplar, FunctionTemplarCore, VirtualTemplar}
import net.verdagon.vale.{IProfiler, vfail, vimpl}

import scala.collection.immutable.List

class StructTemplarMiddle(
    opts: TemplarOptions,
    profiler: IProfiler,
    newTemplataStore: () => TemplatasStore,
    ancestorHelper: AncestorHelper,
    delegate: IStructTemplarDelegate) {
  val core = new StructTemplarCore(opts, profiler, newTemplataStore, ancestorHelper, delegate)

  def addBuiltInStructs(env: PackageEnvironment[INameT], temputs: Temputs): Unit = {
    core.addBuiltInStructs(env, temputs)
  }

  def makeStructConstructor(
    temputs: Temputs,
    maybeConstructorOriginFunctionA: Option[FunctionA],
    structDef: StructDefinitionT,
    constructorFullName: FullNameT[IFunctionNameT]):
  FunctionHeaderT = {
    core.makeStructConstructor(temputs, maybeConstructorOriginFunctionA, structDef, constructorFullName)
  }

  def getStructRef(
    structOuterEnv: PackageEnvironment[INameT],
    temputs: Temputs,
    callRange: RangeS,
    structS: StructA,
    templatasByRune: Map[IRuneT, ITemplata]):
  (StructTT) = {
    val coercedFinalTemplateArgs2 = structS.identifyingRunes.map(NameTranslator.translateRune).map(templatasByRune)

    val localEnv =
      structOuterEnv.addEntries(
        opts.useOptimization,
        templatasByRune.map({ case (rune, templata) => (rune, Vector(TemplataEnvEntry(templata))) }))
    val structDefinition2 =
      core.makeStruct(
        localEnv, temputs, structS, coercedFinalTemplateArgs2);

    (structDefinition2.getRef)
  }

  def getInterfaceRef(
    interfaceOuterEnv: PackageEnvironment[INameT],
    temputs: Temputs,
    callRange: RangeS,
    interfaceA: InterfaceA,
    templatasByRune: Map[IRuneT, ITemplata]):
  (InterfaceTT) = {
    val coercedFinalTemplateArgs2 = interfaceA.identifyingRunes.map(NameTranslator.translateRune).map(templatasByRune)

    val localEnv =
      interfaceOuterEnv.addEntries(
        opts.useOptimization,
        templatasByRune.map({ case (rune, templata) => (rune, Vector(TemplataEnvEntry(templata))) }))
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
    name: LambdaNameA,
    functionS: FunctionA,
    members: Vector[StructMemberT]):
  (StructTT, MutabilityT, FunctionTemplata) = {
    core.makeClosureUnderstruct(containingFunctionEnv, temputs, name, functionS, members)
  }

  // Makes a struct to back a pack or tuple
  def makeSeqOrPackUnderstruct(
    env: PackageEnvironment[INameT],
    temputs: Temputs,
    memberTypes2: Vector[CoordT],
    name: ICitizenNameT):
  (StructTT, MutabilityT) = {
    core.makeSeqOrPackUnderstruct(env, temputs, memberTypes2, name)
  }

  // Makes an anonymous substruct of the given interface, with the given lambdas as its members.
  def makeAnonymousSubstruct(
    interfaceEnv: IEnvironment,
    temputs: Temputs,
    range: RangeS,
    interfaceTT: InterfaceTT,
    substructName: FullNameT[AnonymousSubstructNameT]):
  (StructTT, MutabilityT) = {

//    val anonymousSubstructName: FullName2[AnonymousSubCitizenName2] =
//      functionFullName.addStep(AnonymousSubCitizenName2())
    // but we do need some sort of codelocation in there, right?
    // otherwise i can say IMyInterface(sum, mul) + IMyInterface(sum, mul)
    // actually thats probably fine. we would just reuse the existing one.
    // ...we best write a doc section on this.

    core.makeAnonymousSubstruct(
      interfaceEnv,
      temputs,
      range,
      substructName,
      interfaceTT)
  }

  // Makes an anonymous substruct of the given interface, which just forwards its method to the given prototype.
  def prototypeToAnonymousStruct(
    outerEnv: IEnvironment,
    temputs: Temputs,
    range: RangeS,
    prototype: PrototypeT,
    structFullName: FullNameT[ICitizenNameT]):
  StructTT = {
    core.prototypeToAnonymousStruct(
      outerEnv, temputs, range, prototype, structFullName)
  }
}
