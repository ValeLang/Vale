package dev.vale.templar.function

import dev.vale.{Interner, RangeS, vassert, vfail}
import dev.vale.astronomer.FunctionA
import dev.vale.scout.{AbstractBodyS, CodeBodyS, ExternBodyS, GeneratedBodyS, IFunctionDeclarationNameS}
import dev.vale.templar.citizen.StructTemplar
import dev.vale.templar.types._
import dev.vale.templar.templata._
import dev.vale.scout.IFunctionDeclarationNameS
import dev.vale.templar._
import dev.vale.templar.ast._
import dev.vale.templar.env._
import FunctionTemplar.IEvaluateFunctionResult
import dev.vale.templar.ast.{FunctionBannerT, FunctionHeaderT, PrototypeT}
import dev.vale.templar.env.{AddressibleClosureVariableT, BuildingFunctionEnvironmentWithClosureds, IEnvEntry, IEnvironment, IVariableT, ReferenceClosureVariableT, TemplataEnvEntry, TemplatasStore}
import dev.vale.templar.{ConvertHelper, InferTemplar, TemplarOptions, TemplataTemplar, Temputs, env}
import dev.vale.templar.names.{BuildingFunctionNameWithClosuredsT, FullNameT, INameT, NameTranslator}
import dev.vale.templar.templata.{ITemplata, KindTemplata}
import dev.vale.templar.types.{AddressMemberTypeT, ParamFilter, ReferenceMemberTypeT, StructTT}
import dev.vale.templar.names.BuildingFunctionNameWithClosuredsT
import dev.vale.{Interner, Profiler, RangeS, vassert, vfail, vimpl}

import scala.collection.immutable.{List, Map}

// When templaring a function, these things need to happen:
// - Spawn a local environment for the function
// - Add any closure args to the environment
// - Incorporate any template arguments into the environment
// There's a layer to take care of each of these things.
// This file is the outer layer, which spawns a local environment for the function.
class FunctionTemplarClosureOrLightLayer(
    opts: TemplarOptions,

    interner: Interner,
    nameTranslator: NameTranslator,
    templataTemplar: TemplataTemplar,
    inferTemplar: InferTemplar,
    convertHelper: ConvertHelper,
    structTemplar: StructTemplar,
    delegate: IFunctionTemplarDelegate) {
  val ordinaryOrTemplatedLayer =
    new FunctionTemplarOrdinaryOrTemplatedLayer(
      opts, interner, nameTranslator, templataTemplar, inferTemplar, convertHelper, structTemplar, delegate)

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
    alreadySpecifiedTemplateArgs: Vector[ITemplata],
      argTypes2: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    vassert(function.isTemplate)

    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val name = makeNameWithClosureds(outerEnv, function.name)
    val newEnv =
      BuildingFunctionEnvironmentWithClosureds(
        outerEnv.globalEnv,
        outerEnv,
        name,
        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
        function,
        variables)

    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForBanner(
      newEnv, temputs, callRange, alreadySpecifiedTemplateArgs, argTypes2)
  }

  def evaluateTemplatedClosureFunctionFromCallForPrototype(
    outerEnv: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    closureStructRef: StructTT,
    function: FunctionA,
    alreadySpecifiedTemplateArgs: Vector[ITemplata],
    argTypes2: Vector[ParamFilter]):
  (IEvaluateFunctionResult[PrototypeT]) = {
    vassert(function.isTemplate)

    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val name = makeNameWithClosureds(outerEnv, function.name)
    val newEnv =
      env.BuildingFunctionEnvironmentWithClosureds(
        outerEnv.globalEnv,
        outerEnv,
        name,
        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
        function,
        variables)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForPrototype(
      newEnv, temputs, callRange, alreadySpecifiedTemplateArgs, argTypes2)
  }

  def evaluateTemplatedLightFunctionFromCallForPrototype2(
      ourEnv: IEnvironment,
      temputs: Temputs,
    callRange: RangeS,
    function: FunctionA,
      explicitTemplateArgs: Vector[ITemplata],
      args: Vector[ParamFilter]):
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
    function: FunctionA):
  (FunctionHeaderT) = {
    vassert(!function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForHeader(
      newEnv, temputs)
  }

  def evaluateTemplatedLightFunctionFromNonCallForHeader(
    outerEnv: IEnvironment,
    temputs: Temputs,
    function: FunctionA):
  (FunctionHeaderT) = {
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromNonCallForHeader(
      newEnv, temputs)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // func main():Int{main()}
  def evaluateOrdinaryLightFunctionFromNonCallForPrototype(
    outerEnv: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    function: FunctionA
  ): PrototypeT = {
    checkNotClosure(function)
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val newEnv =
      env.BuildingFunctionEnvironmentWithClosureds(
        outerEnv.globalEnv,
        outerEnv,
        name,
        TemplatasStore(name, Map(), Map()),
        function,
        Vector.empty)
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
    val newEnv =
      env.BuildingFunctionEnvironmentWithClosureds(
        outerEnv.globalEnv,
        outerEnv,
        name,
        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
        function,
        variables)
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForBanner(
      newEnv, temputs, callRange)
  }

  def evaluateOrdinaryClosureFunctionFromNonCallForHeader(
      outerEnv: IEnvironment,
      temputs: Temputs,
      closureStructRef: StructTT,
    function: FunctionA):
  (FunctionHeaderT) = {
    // We dont here because it knows from how many variables
    // it closures... but even lambdas without closured vars are still closures and are still
    // backed by structs.
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val newEnv =
      env.BuildingFunctionEnvironmentWithClosureds(
        outerEnv.globalEnv,
        outerEnv,
        name,
        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
        function,
        variables)
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForHeader(
      newEnv, temputs)
  }

  def evaluateTemplatedClosureFunctionFromNonCallForHeader(
    outerEnv: IEnvironment,
    temputs: Temputs,
    closureStructRef: StructTT,
    function: FunctionA):
  (FunctionHeaderT) = {
    // We dont here because it knows from how many variables
    // it closures... but even lambdas without closured vars are still closures and are still
    // backed by structs.
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val newEnv =
      env.BuildingFunctionEnvironmentWithClosureds(
        outerEnv.globalEnv,
        outerEnv,
        name,
        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
        function,
        variables)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromNonCallForHeader(
      newEnv, temputs)
  }

  // This is called while we're trying to figure out what function1s to call when there
  // are a lot of overloads available.
  // This assumes it met any type bound restrictions (or, will; not implemented yet)
  def evaluateTemplatedLightBannerFromCall(
      functionOuterEnv: IEnvironment,
      temputs: Temputs,
    callRange: RangeS,
      function: FunctionA,
      explicitTemplateArgs: Vector[ITemplata],
      args: Vector[ParamFilter]):
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
      alreadySpecifiedTemplateArgs: Vector[ITemplata],
      paramFilters: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForBanner(
        newEnv, temputs, callRange, alreadySpecifiedTemplateArgs, paramFilters)
  }

  private def makeEnvWithoutClosureStuff(
    outerEnv: IEnvironment,
    function: FunctionA
  ): BuildingFunctionEnvironmentWithClosureds = {
    val name = makeNameWithClosureds(outerEnv, function.name)
    env.BuildingFunctionEnvironmentWithClosureds(
      outerEnv.globalEnv,
      outerEnv,
      name,
      TemplatasStore(name, Map(), Map()),
      function,
      Vector.empty)
  }

  private def makeNameWithClosureds(
    outerEnv: IEnvironment,
    functionName: IFunctionDeclarationNameS
  ): FullNameT[BuildingFunctionNameWithClosuredsT] = {
    val templateName = nameTranslator.translateFunctionNameToTemplateName(functionName)
    outerEnv.fullName.addStep(BuildingFunctionNameWithClosuredsT(templateName))
  }

  private def checkNotClosure(function: FunctionA) = {
    function.body match {
      case CodeBodyS(body1) => vassert(body1.closuredNames.isEmpty)
      case ExternBodyS =>
      case GeneratedBodyS(_) =>
      case AbstractBodyS =>
      case _ => vfail()
    }
  }

  private def makeClosureVariablesAndEntries(temputs: Temputs, closureStructRef: StructTT):
  (Vector[IVariableT], Vector[(INameT, IEnvEntry)]) = {
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
      Vector[(INameT, IEnvEntry)](
        closureStructRef.fullName.last ->
          TemplataEnvEntry(KindTemplata(closureStructRef)))
    (variables, entries)
  }
}
