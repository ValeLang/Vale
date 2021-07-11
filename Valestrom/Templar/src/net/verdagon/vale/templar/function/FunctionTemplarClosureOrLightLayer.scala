package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.{AbstractBodyA, CodeBodyA, ExternBodyA, FunctionA, GeneratedBodyA, IFunctionDeclarationNameA}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{CodeBodyS, RangeS}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.FunctionTemplar.IEvaluateFunctionResult
import net.verdagon.vale.{IProfiler, vassert, vfail, vimpl}

import scala.collection.immutable.{List, Map}

// When templaring a function, these things need to happen:
// - Spawn a local environment for the function
// - Add any closure args to the environment
// - Incorporate any template arguments into the environment
// There's a layer to take care of each of these things.
// This file is the outer layer, which spawns a local environment for the function.
class FunctionTemplarClosureOrLightLayer(
    opts: TemplarOptions,
  profiler: IProfiler,
  newTemplataStore: () => TemplatasStore,
  templataTemplar: TemplataTemplar,
    inferTemplar: InferTemplar,
  convertHelper: ConvertHelper,
    structTemplar: StructTemplar,
    delegate: IFunctionTemplarDelegate) {
  val ordinaryOrTemplatedLayer = new FunctionTemplarOrdinaryOrTemplatedLayer(opts, profiler, newTemplataStore, templataTemplar, inferTemplar, convertHelper, structTemplar, delegate)

  // This is for the early stages of Templar when it's scanning banners to put in
  // its env. We just want its banner, we don't want to evaluate it.
  def predictOrdinaryLightFunctionBanner(
    outerEnv: IEnvironment,
    temputs: Temputs,
    function: FunctionA):
  (FunctionBannerT) = {
    checkNotClosure(function);
    vassert(!function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.predictOrdinaryFunctionBanner(
      newEnv, temputs)
  }


  def evaluateOrdinaryLightFunctionFromNonCallForBanner(
      outerEnv: IEnvironment,
      temputs: Temputs,
    callRange: RangeS,
    function: FunctionA):
  (FunctionBannerT) = {
    checkNotClosure(function);
    vassert(!function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForBanner(
      newEnv, temputs, callRange)
  }

  def evaluateTemplatedClosureFunctionFromCallForBanner(
    outerEnv: IEnvironment,
      temputs: Temputs,
    callRange: RangeS,
      closureStructRef: StructTT,
    function: FunctionA,
    alreadySpecifiedTemplateArgs: List[ITemplata],
      argTypes2: List[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    vassert(function.isTemplate)

    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val name = makeNameWithClosureds(outerEnv, function.name)
    val newEnv = BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, variables, newTemplataStore().addEntries(opts.useOptimization, entries))

    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForBanner(
      newEnv, temputs, callRange, alreadySpecifiedTemplateArgs, argTypes2)
  }

  def evaluateTemplatedClosureFunctionFromCallForPrototype(
    outerEnv: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    closureStructRef: StructTT,
    function: FunctionA,
    alreadySpecifiedTemplateArgs: List[ITemplata],
    argTypes2: List[ParamFilter]):
  (IEvaluateFunctionResult[PrototypeT]) = {
    vassert(function.isTemplate)

    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val name = makeNameWithClosureds(outerEnv, function.name)
    val newEnv = BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, variables, newTemplataStore().addEntries(opts.useOptimization, entries))
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForPrototype(
      newEnv, temputs, callRange, alreadySpecifiedTemplateArgs, argTypes2)
  }

//  def evaluateTemplatedLightFunctionFromNonCallForHeader(
//      ourEnv: IEnvironment,
//      temputs: Temputs,
//    function: FunctionA,
//      explicitTemplateArgs: List[ITemplata]):
//  (FunctionHeader2) = {
//    vassert(function.isTemplate)
//    vassert(function.identifyingRunes.size == explicitTemplateArgs.size);
//    checkNotClosure(function)
//
//    val newEnv = makeEnvWithoutClosureStuff(ourEnv, function)
//    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromNonCallForHeader(
//      newEnv, temputs)
//  }

  def evaluateTemplatedLightFunctionFromCallForPrototype2(
      ourEnv: IEnvironment,
      temputs: Temputs,
    callRange: RangeS,
    function: FunctionA,
      explicitTemplateArgs: List[ITemplata],
      args: List[ParamFilter]):
  (IEvaluateFunctionResult[PrototypeT]) = {
    checkNotClosure(function);
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(ourEnv, function)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForPrototype(
      newEnv, temputs, callRange, explicitTemplateArgs, args)
  }

  def evaluateOrdinaryLightFunctionFromNonCallForHeader(
      outerEnv: IEnvironment,
      temputs: Temputs,
    callRange: RangeS,
    function: FunctionA):
  (FunctionHeaderT) = {
    vassert(!function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForHeader(
      newEnv, temputs, callRange)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // fn main():Int{main()}
  def evaluateOrdinaryLightFunctionFromNonCallForPrototype(
    outerEnv: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    function: FunctionA
  ): PrototypeT = {
    checkNotClosure(function)
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val newEnv = BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, List.empty, newTemplataStore())
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForPrototype(
      newEnv, temputs, callRange)
  }

  def evaluateOrdinaryClosureFunctionFromNonCallForBanner(
    outerEnv: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    closureStructRef: StructTT,
    function: FunctionA):
  (FunctionBannerT) = {
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val newEnv = BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, variables, newTemplataStore().addEntries(opts.useOptimization, entries))
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForBanner(
      newEnv, temputs, callRange)
  }

  def evaluateOrdinaryClosureFunctionFromNonCallForHeader(
      outerEnv: IEnvironment,
      temputs: Temputs,
    callRange: RangeS,
      closureStructRef: StructTT,
    function: FunctionA):
  (FunctionHeaderT) = {
    // We dont here because it knows from how many variables
    // it closures... but even lambdas without closured vars are still closures and are still
    // backed by structs.
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val newEnv = BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, variables, newTemplataStore().addEntries(opts.useOptimization, entries))
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForHeader(
      newEnv, temputs, callRange)
  }

  // This is called while we're trying to figure out what function1s to call when there
  // are a lot of overloads available.
  // This assumes it met any type bound restrictions (or, will; not implemented yet)
  def evaluateTemplatedLightBannerFromCall(
      functionOuterEnv: IEnvironment,
      temputs: Temputs,
    callRange: RangeS,
      function: FunctionA,
      explicitTemplateArgs: List[ITemplata],
      args: List[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    checkNotClosure(function)
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(functionOuterEnv, function)
    ordinaryOrTemplatedLayer.evaluateTemplatedLightBannerFromCall(
        newEnv, temputs, callRange, explicitTemplateArgs, args)
  }

  def evaluateTemplatedFunctionFromCallForBanner(
      outerEnv: IEnvironment,
      temputs: Temputs,
      function: FunctionA,
    callRange: RangeS,
      alreadySpecifiedTemplateArgs: List[ITemplata],
      paramFilters: List[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForBanner(
        newEnv, temputs, callRange, alreadySpecifiedTemplateArgs, paramFilters)
  }

//  def scanOrdinaryInterfaceMember(
//    env1: IEnvironment,
//    temputs: Temputs,
//    interfaceExplicitTemplateArgs: List[ITemplata],
//    function: FunctionA):
//  (FunctionHeader2) = {
//
//    vassert(!function.isTemplate)
//
//    val newEnv = makeEnvWithoutClosureStuff(env1, function)
//    ordinaryOrTemplatedLayer.scanOrdinaryInterfaceMember(
//      newEnv, temputs, interfaceExplicitTemplateArgs)
//  }

  private def makeEnvWithoutClosureStuff(
    outerEnv: IEnvironment,
    function: FunctionA
  ): BuildingFunctionEnvironmentWithClosureds = {
    val name = makeNameWithClosureds(outerEnv, function.name)
    BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, List.empty, newTemplataStore())
  }

  private def makeNameWithClosureds(
    outerEnv: IEnvironment,
    functionName: IFunctionDeclarationNameA
  ): FullNameT[BuildingFunctionNameWithClosuredsT] = {
    outerEnv.fullName.addStep(
      BuildingFunctionNameWithClosuredsT(
        NameTranslator.translateFunctionNameToTemplateName(functionName)))
  }

  private def checkNotClosure(function: FunctionA) = {
    function.body match {
      case CodeBodyA(body1) => vassert(body1.closuredNames.isEmpty)
      case ExternBodyA =>
      case GeneratedBodyA(_) =>
      case AbstractBodyA =>
      case _ => vfail()
    }
  }

  private def makeClosureVariablesAndEntries(temputs: Temputs, closureStructRef: StructTT):
  (List[IVariableT], Map[INameT, List[IEnvEntry]]) = {
    val closureStructDef = temputs.lookupStruct(closureStructRef);
    val variables =
      closureStructDef.members.map(member => {
        val variableFullName = closureStructDef.fullName.addStep(member.name)
        member.tyype match {
          case AddressMemberTypeT(reference) => {
            AddressibleClosureVariableT(variableFullName, closureStructRef, member.variability, reference)
          }
          case ReferenceMemberTypeT(reference) => {
            ReferenceClosureVariableT(variableFullName, closureStructRef, member.variability, reference)
          }
        }
      })
    val entries =
      Map[INameT, List[IEnvEntry]](
        closureStructRef.fullName.last ->
          List(TemplataEnvEntry(KindTemplata(closureStructRef))))
    (variables, entries)
  }
}
