package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer.{AbstractBodyA, CodeBodyA, ExternBodyA, FunctionA, GeneratedBodyA, IFunctionDeclarationNameA}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.CodeBody1
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.FunctionTemplar.IEvaluateFunctionResult
import net.verdagon.vale.{vassert, vfail, vimpl}

import scala.collection.immutable.{List, Map}

// When templaring a function, these things need to happen:
// - Spawn a local environment for the function
// - Add any closure args to the environment
// - Incorporate any template arguments into the environment
// There's a layer to take care of each of these things.
// This file is the outer layer, which spawns a local environment for the function.
object FunctionTemplarClosureOrLightLayer {

  // This is for the early stages of Templar when it's scanning banners to put in
  // its env. We just want its banner, we don't want to evaluate it.
  def predictOrdinaryLightFunctionBanner(
    outerEnv: IEnvironment,
    temputs: TemputsBox,
    function: FunctionA):
  (FunctionBanner2) = {
    checkNotClosure(function);
    vassert(!function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    FunctionTemplarOrdinaryOrTemplatedLayer.predictOrdinaryFunctionBanner(
      newEnv, temputs)
  }


  def evaluateOrdinaryLightFunctionFromNonCallForBanner(
      outerEnv: IEnvironment,
      temputs: TemputsBox,
    function: FunctionA):
  (FunctionBanner2) = {
    checkNotClosure(function);
    vassert(!function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    FunctionTemplarOrdinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForBanner(
      newEnv, temputs)
  }

  def evaluateTemplatedClosureFunctionFromCallForBanner(
    outerEnv: IEnvironment,
      temputs: TemputsBox,
      closureStructRef: StructRef2,
    function: FunctionA,
    alreadySpecifiedTemplateArgs: List[ITemplata],
      argTypes2: List[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBanner2]) = {
    vassert(function.isTemplate)

    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val name = makeNameWithClosureds(outerEnv, function.name)
    val newEnv = BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, variables, entries)

    FunctionTemplarOrdinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForBanner(
      newEnv, temputs, alreadySpecifiedTemplateArgs, argTypes2)
  }

  def evaluateTemplatedClosureFunctionFromCallForPrototype(
    outerEnv: IEnvironment,
    temputs: TemputsBox,
    closureStructRef: StructRef2,
    function: FunctionA,
    alreadySpecifiedTemplateArgs: List[ITemplata],
    argTypes2: List[ParamFilter]):
  (IEvaluateFunctionResult[Prototype2]) = {
    vassert(function.isTemplate)

    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val name = makeNameWithClosureds(outerEnv, function.name)
    val newEnv = BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, variables, entries)
    FunctionTemplarOrdinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForPrototype(
      newEnv, temputs, alreadySpecifiedTemplateArgs, argTypes2)
  }

  def evaluateTemplatedLightFunctionFromNonCallForHeader(
      ourEnv: IEnvironment,
      temputs: TemputsBox,
    function: FunctionA,
      explicitTemplateArgs: List[ITemplata]):
  (FunctionHeader2) = {
    vassert(function.isTemplate)
    vassert(function.identifyingRunes.size == explicitTemplateArgs.size);
    checkNotClosure(function)

    val newEnv = makeEnvWithoutClosureStuff(ourEnv, function)
    FunctionTemplarOrdinaryOrTemplatedLayer.evaluateTemplatedFunctionFromNonCallForHeader(
      newEnv, temputs)
  }

  def evaluateTemplatedLightFunctionFromCallForPrototype2(
      ourEnv: IEnvironment,
      temputs: TemputsBox,
    function: FunctionA,
      explicitTemplateArgs: List[ITemplata],
      args: List[ParamFilter]):
  (IEvaluateFunctionResult[Prototype2]) = {
    checkNotClosure(function);
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(ourEnv, function)
    FunctionTemplarOrdinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForPrototype(
      newEnv, temputs, explicitTemplateArgs, args)
  }

  def evaluateOrdinaryLightFunctionFromNonCallForHeader(
      outerEnv: IEnvironment,
      temputs: TemputsBox,
    function: FunctionA):
  (FunctionHeader2) = {
    vassert(!function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    FunctionTemplarOrdinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForHeader(
      newEnv, temputs)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // fn main():Int{main()}
  def evaluateOrdinaryLightFunctionFromNonCallForPrototype(
    outerEnv: IEnvironment,
    temputs: TemputsBox,
    function: FunctionA
  ): Prototype2 = {
    checkNotClosure(function)
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val newEnv = BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, List(), Map())
    FunctionTemplarOrdinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForPrototype(
      newEnv, temputs)
  }

  def evaluateOrdinaryClosureFunctionFromNonCallForBanner(
    outerEnv: IEnvironment,
    temputs: TemputsBox,
    closureStructRef: StructRef2,
    function: FunctionA):
  (FunctionBanner2) = {
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val newEnv = BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, variables, entries)
    FunctionTemplarOrdinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForBanner(
      newEnv, temputs)
  }

  def evaluateOrdinaryClosureFunctionFromNonCallForHeader(
      outerEnv: IEnvironment,
      temputs: TemputsBox,
      closureStructRef: StructRef2,
    function: FunctionA):
  (FunctionHeader2) = {
    // We dont here because it knows from how many variables
    // it closures... but even lambdas without closured vars are still closures and are still
    // backed by structs.
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val (variables, entries) = makeClosureVariablesAndEntries(temputs, closureStructRef)
    val newEnv = BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, variables, entries)
    FunctionTemplarOrdinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForHeader(
      newEnv, temputs)
  }

  // This is called while we're trying to figure out what function1s to call when there
  // are a lot of overloads available.
  // This assumes it met any type bound restrictions (or, will; not implemented yet)
  def evaluateTemplatedLightBannerFromCall(
      functionOuterEnv: IEnvironment,
      temputs: TemputsBox,
      function: FunctionA,
      explicitTemplateArgs: List[ITemplata],
      args: List[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBanner2]) = {
    checkNotClosure(function)
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(functionOuterEnv, function)
    FunctionTemplarOrdinaryOrTemplatedLayer.evaluateTemplatedLightBannerFromCall(
        newEnv, temputs, explicitTemplateArgs, args)
  }

  def evaluateTemplatedFunctionFromCallForBanner(
      outerEnv: IEnvironment,
      temputs: TemputsBox,
      function: FunctionA,
      alreadySpecifiedTemplateArgs: List[ITemplata],
      paramFilters: List[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBanner2]) = {
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    FunctionTemplarOrdinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForBanner(
        newEnv, temputs, alreadySpecifiedTemplateArgs, paramFilters)
  }

//  def scanOrdinaryInterfaceMember(
//    env1: IEnvironment,
//    temputs: TemputsBox,
//    interfaceExplicitTemplateArgs: List[ITemplata],
//    function: FunctionA):
//  (FunctionHeader2) = {
//
//    vassert(!function.isTemplate)
//
//    val newEnv = makeEnvWithoutClosureStuff(env1, function)
//    FunctionTemplarOrdinaryOrTemplatedLayer.scanOrdinaryInterfaceMember(
//      newEnv, temputs, interfaceExplicitTemplateArgs)
//  }

  private def makeEnvWithoutClosureStuff(
    outerEnv: IEnvironment,
    function: FunctionA
  ): BuildingFunctionEnvironmentWithClosureds = {
    val name = makeNameWithClosureds(outerEnv, function.name)
    BuildingFunctionEnvironmentWithClosureds(outerEnv, name, function, List(), Map())
  }

  private def makeNameWithClosureds(outerEnv: IEnvironment, functionName: IFunctionDeclarationNameA) = {
    outerEnv.fullName.addStep(
      BuildingFunctionNameWithClosureds2(
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

  private def makeClosureVariablesAndEntries(temputs: TemputsBox, closureStructRef: StructRef2):
  (List[IVariable2], Map[IName2, List[IEnvEntry]]) = {
    val closureStructDef = temputs.lookupStruct(closureStructRef);
    val variables =
      closureStructDef.members.map(member => {
        val variableFullName = closureStructDef.fullName.addStep(member.name)
        member.tyype match {
          case AddressMemberType2(reference) => {
            AddressibleClosureVariable2(variableFullName, closureStructRef, member.variability, reference)
          }
          case ReferenceMemberType2(reference) => {
            ReferenceClosureVariable2(variableFullName, closureStructRef, member.variability, reference)
          }
        }
      })
    val entries =
      Map[IName2, List[IEnvEntry]](
        closureStructRef.fullName.last ->
          List(TemplataEnvEntry(KindTemplata(closureStructRef))))
    (variables, entries)
  }
}
