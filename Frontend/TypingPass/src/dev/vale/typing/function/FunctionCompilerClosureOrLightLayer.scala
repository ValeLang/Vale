package dev.vale.typing.function

import dev.vale.{Interner, RangeS, vassert, vfail}
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.{AbstractBodyS, CodeBodyS, ExternBodyS, GeneratedBodyS, IFunctionDeclarationNameS}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.postparsing.IFunctionDeclarationNameS
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import FunctionCompiler.IEvaluateFunctionResult
import dev.vale.typing.ast.{FunctionBannerT, FunctionHeaderT, PrototypeT}
import dev.vale.typing.env.{AddressibleClosureVariableT, BuildingFunctionEnvironmentWithClosureds, IEnvEntry, IEnvironment, IVariableT, ReferenceClosureVariableT, TemplataEnvEntry, TemplatasStore}
import dev.vale.typing.{ConvertHelper, InferCompiler, TypingPassOptions, TemplataCompiler, CompilerOutputs, env}
import dev.vale.typing.names.{BuildingFunctionNameWithClosuredsT, FullNameT, INameT, NameTranslator}
import dev.vale.typing.templata.{ITemplata, KindTemplata}
import dev.vale.typing.types.{AddressMemberTypeT, ParamFilter, ReferenceMemberTypeT, StructTT}
import dev.vale.typing.names.BuildingFunctionNameWithClosuredsT
import dev.vale.{Interner, Profiler, RangeS, vassert, vfail, vimpl}

import scala.collection.immutable.{List, Map}

// When typingpassing a function, these things need to happen:
// - Spawn a local environment for the function
// - Add any closure args to the environment
// - Incorporate any template arguments into the environment
// There's a layer to take care of each of these things.
// This file is the outer layer, which spawns a local environment for the function.
class FunctionCompilerClosureOrLightLayer(
    opts: TypingPassOptions,

    interner: Interner,
    nameTranslator: NameTranslator,
    templataCompiler: TemplataCompiler,
    inferCompiler: InferCompiler,
    convertHelper: ConvertHelper,
    structCompiler: StructCompiler,
    delegate: IFunctionCompilerDelegate) {
  val ordinaryOrTemplatedLayer =
    new FunctionCompilerOrdinaryOrTemplatedLayer(
      opts, interner, nameTranslator, templataCompiler, inferCompiler, convertHelper, structCompiler, delegate)

  // This is for the early stages of Compiler when it's scanning banners to put in
  // its env. We just want its banner, we don't want to evaluate it.
  def predictOrdinaryLightFunctionBanner(
    outerEnv: IEnvironment,
    coutputs: CompilerOutputs,
    function: FunctionA):
  (FunctionBannerT) = {
    checkNotClosure(function);
    vassert(!function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.predictOrdinaryFunctionBanner(
      newEnv, coutputs)
  }


  def evaluateOrdinaryLightFunctionFromNonCallForBanner(
      outerEnv: IEnvironment,
      coutputs: CompilerOutputs,
    callRange: RangeS,
    function: FunctionA):
  (FunctionBannerT) = {
    checkNotClosure(function);
    vassert(!function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForBanner(
      newEnv, coutputs, callRange)
  }

  def evaluateTemplatedClosureFunctionFromCallForBanner(
    outerEnv: IEnvironment,
      coutputs: CompilerOutputs,
    callRange: RangeS,
      closureStructRef: StructTT,
    function: FunctionA,
    alreadySpecifiedTemplateArgs: Vector[ITemplata],
      argTypes2: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    vassert(function.isTemplate)

    val (variables, entries) = makeClosureVariablesAndEntries(coutputs, closureStructRef)
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
      newEnv, coutputs, callRange, alreadySpecifiedTemplateArgs, argTypes2)
  }

  def evaluateTemplatedClosureFunctionFromCallForPrototype(
    outerEnv: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    closureStructRef: StructTT,
    function: FunctionA,
    alreadySpecifiedTemplateArgs: Vector[ITemplata],
    argTypes2: Vector[ParamFilter]):
  (IEvaluateFunctionResult[PrototypeT]) = {
    vassert(function.isTemplate)

    val (variables, entries) = makeClosureVariablesAndEntries(coutputs, closureStructRef)
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
      newEnv, coutputs, callRange, alreadySpecifiedTemplateArgs, argTypes2)
  }

  def evaluateTemplatedLightFunctionFromCallForPrototype2(
      ourEnv: IEnvironment,
      coutputs: CompilerOutputs,
    callRange: RangeS,
    function: FunctionA,
      explicitTemplateArgs: Vector[ITemplata],
      args: Vector[ParamFilter]):
  (IEvaluateFunctionResult[PrototypeT]) = {
    checkNotClosure(function);
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(ourEnv, function)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForPrototype(
      newEnv, coutputs, callRange, explicitTemplateArgs, args)
  }

  def evaluateOrdinaryLightFunctionFromNonCallForHeader(
      outerEnv: IEnvironment,
      coutputs: CompilerOutputs,
    function: FunctionA):
  (FunctionHeaderT) = {
    vassert(!function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForHeader(
      newEnv, coutputs)
  }

  def evaluateTemplatedLightFunctionFromNonCallForHeader(
    outerEnv: IEnvironment,
    coutputs: CompilerOutputs,
    function: FunctionA):
  (FunctionHeaderT) = {
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromNonCallForHeader(
      newEnv, coutputs)
  }

  // We would want only the prototype instead of the entire header if, for example,
  // we were calling the function. This is necessary for a recursive function like
  // func main():Int{main()}
  def evaluateOrdinaryLightFunctionFromNonCallForPrototype(
    outerEnv: IEnvironment,
    coutputs: CompilerOutputs,
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
      newEnv, coutputs, callRange)
  }

  def evaluateOrdinaryClosureFunctionFromNonCallForBanner(
    outerEnv: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    closureStructRef: StructTT,
    function: FunctionA):
  (FunctionBannerT) = {
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val (variables, entries) = makeClosureVariablesAndEntries(coutputs, closureStructRef)
    val newEnv =
      env.BuildingFunctionEnvironmentWithClosureds(
        outerEnv.globalEnv,
        outerEnv,
        name,
        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
        function,
        variables)
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForBanner(
      newEnv, coutputs, callRange)
  }

  def evaluateOrdinaryClosureFunctionFromNonCallForHeader(
      outerEnv: IEnvironment,
      coutputs: CompilerOutputs,
      closureStructRef: StructTT,
    function: FunctionA):
  (FunctionHeaderT) = {
    // We dont here because it knows from how many variables
    // it closures... but even lambdas without closured vars are still closures and are still
    // backed by structs.
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val (variables, entries) = makeClosureVariablesAndEntries(coutputs, closureStructRef)
    val newEnv =
      env.BuildingFunctionEnvironmentWithClosureds(
        outerEnv.globalEnv,
        outerEnv,
        name,
        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
        function,
        variables)
    ordinaryOrTemplatedLayer.evaluateOrdinaryFunctionFromNonCallForHeader(
      newEnv, coutputs)
  }

  def evaluateTemplatedClosureFunctionFromNonCallForHeader(
    outerEnv: IEnvironment,
    coutputs: CompilerOutputs,
    closureStructRef: StructTT,
    function: FunctionA):
  (FunctionHeaderT) = {
    // We dont here because it knows from how many variables
    // it closures... but even lambdas without closured vars are still closures and are still
    // backed by structs.
    vassert(!function.isTemplate)

    val name = makeNameWithClosureds(outerEnv, function.name)
    val (variables, entries) = makeClosureVariablesAndEntries(coutputs, closureStructRef)
    val newEnv =
      env.BuildingFunctionEnvironmentWithClosureds(
        outerEnv.globalEnv,
        outerEnv,
        name,
        TemplatasStore(name, Map(), Map()).addEntries(interner, entries),
        function,
        variables)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromNonCallForHeader(
      newEnv, coutputs)
  }

  // This is called while we're trying to figure out what function1s to call when there
  // are a lot of overloads available.
  // This assumes it met any type bound restrictions (or, will; not implemented yet)
  def evaluateTemplatedLightBannerFromCall(
      functionOuterEnv: IEnvironment,
      coutputs: CompilerOutputs,
    callRange: RangeS,
      function: FunctionA,
      explicitTemplateArgs: Vector[ITemplata],
      args: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    checkNotClosure(function)
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(functionOuterEnv, function)
    ordinaryOrTemplatedLayer.evaluateTemplatedLightBannerFromCall(
        newEnv, coutputs, callRange, explicitTemplateArgs, args)
  }

  def evaluateTemplatedFunctionFromCallForBanner(
      outerEnv: IEnvironment,
      coutputs: CompilerOutputs,
      function: FunctionA,
    callRange: RangeS,
      alreadySpecifiedTemplateArgs: Vector[ITemplata],
      paramFilters: Vector[ParamFilter]):
  (IEvaluateFunctionResult[FunctionBannerT]) = {
    vassert(function.isTemplate)

    val newEnv = makeEnvWithoutClosureStuff(outerEnv, function)
    ordinaryOrTemplatedLayer.evaluateTemplatedFunctionFromCallForBanner(
        newEnv, coutputs, callRange, alreadySpecifiedTemplateArgs, paramFilters)
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

  private def makeClosureVariablesAndEntries(coutputs: CompilerOutputs, closureStructRef: StructTT):
  (Vector[IVariableT], Vector[(INameT, IEnvEntry)]) = {
    val closureStructDef = coutputs.lookupStruct(closureStructRef);
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
