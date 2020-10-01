package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer.{FunctionA, InterfaceA, LambdaNameA, StructA}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.{FunctionEnvironment, IEnvironment, ITemplatasStore, InterfaceEnvEntry, NamespaceEnvironment, TemplataEnvEntry}
import net.verdagon.vale.templar.function.{FunctionTemplar, FunctionTemplarCore, VirtualTemplar}
import net.verdagon.vale.{IProfiler, vfail, vimpl}

import scala.collection.immutable.List

class StructTemplarMiddle(
    opts: TemplarOptions,
    profiler: IProfiler,
    newTemplataStore: () => ITemplatasStore,
    ancestorHelper: AncestorHelper,
    delegate: IStructTemplarDelegate) {
  val core = new StructTemplarCore(opts, profiler, newTemplataStore, ancestorHelper, delegate)

  def addBuiltInStructs(env: NamespaceEnvironment[IName2], temputs: Temputs): Unit = {
    core.addBuiltInStructs(env, temputs)
  }

  def makeStructConstructor(
    temputs: Temputs,
    maybeConstructorOriginFunctionA: Option[FunctionA],
    structDef: StructDefinition2,
    constructorFullName: FullName2[IFunctionName2]):
  FunctionHeader2 = {
    core.makeStructConstructor(temputs, maybeConstructorOriginFunctionA, structDef, constructorFullName)
  }

  def getStructRef(
    structOuterEnv: NamespaceEnvironment[IName2],
    temputs: Temputs,
    callRange: RangeS,
    structS: StructA,
    templatasByRune: Map[IRune2, ITemplata]):
  (StructRef2) = {
    val coercedFinalTemplateArgs2 = structS.identifyingRunes.map(NameTranslator.translateRune).map(templatasByRune)

    val localEnv =
      structOuterEnv.addEntries(
        templatasByRune.map({ case (rune, templata) => (rune, List(TemplataEnvEntry(templata))) }))
    val structDefinition2 =
      core.maakeStruct(
        localEnv, temputs, structS, coercedFinalTemplateArgs2);

    (structDefinition2.getRef)
  }

  def getInterfaceRef(
    interfaceOuterEnv: NamespaceEnvironment[IName2],
    temputs: Temputs,
    callRange: RangeS,
    interfaceA: InterfaceA,
    templatasByRune: Map[IRune2, ITemplata]):
  (InterfaceRef2) = {
    val coercedFinalTemplateArgs2 = interfaceA.identifyingRunes.map(NameTranslator.translateRune).map(templatasByRune)

    val localEnv =
      interfaceOuterEnv.addEntries(
        templatasByRune.map({ case (rune, templata) => (rune, List(TemplataEnvEntry(templata))) }))
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
    members: List[StructMember2]):
  (StructRef2, Mutability, FunctionTemplata) = {
    core.makeClosureUnderstruct(containingFunctionEnv, temputs, name, functionS, members)
  }

  // Makes a struct to back a pack or tuple
  def makeSeqOrPackUnderstruct(
    env: NamespaceEnvironment[IName2],
    temputs: Temputs,
    memberTypes2: List[Coord],
    name: ICitizenName2):
  (StructRef2, Mutability) = {
    core.makeSeqOrPackUnderstruct(env, temputs, memberTypes2, name)
  }

  // Makes an anonymous substruct of the given interface, with the given lambdas as its members.
  def makeAnonymousSubstruct(
    interfaceEnv: IEnvironment,
    temputs: Temputs,
    range: RangeS,
    interfaceRef: InterfaceRef2,
    substructName: FullName2[AnonymousSubstructName2]):
  (StructRef2, Mutability) = {

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
      interfaceRef)
  }

  // Makes an anonymous substruct of the given interface, which just forwards its method to the given prototype.
  def prototypeToAnonymousStruct(
    outerEnv: IEnvironment,
    temputs: Temputs,
    prototype: Prototype2,
    structFullName: FullName2[ICitizenName2]):
  StructRef2 = {
    core.prototypeToAnonymousStruct(
      outerEnv, temputs, prototype, structFullName)
  }
}
