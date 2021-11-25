package net.verdagon.vale.templar.macros.drop

import net.verdagon.vale.astronomer.{FunctionA, StructA}
import net.verdagon.vale.scout._
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS}
import net.verdagon.vale.scout.rules.{CallSR, EqualsSR, LookupSR, RuneUsage}
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env.{FunctionEnvEntry, FunctionEnvironment, FunctionEnvironmentBox, IEnvEntry, ReferenceLocalVariableT, TemplataLookupContext}
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.macros.{IFunctionBodyMacro, IOnRuntimeSizedArrayDefinedMacro, IOnStructDefinedMacro}
import net.verdagon.vale.templar.names.{FullNameT, INameT, NameTranslator}
import net.verdagon.vale.templar.templata.{CoordTemplata, MutabilityTemplata, PrototypeTemplata, StructTemplata}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.{ArrayTemplar, OverloadTemplar, Templar, Temputs}
import net.verdagon.vale.{Profiler, RangeS, vwat}

class RSAFreeMacro(
  arrayTemplar: ArrayTemplar,
  destructorTemplar: DestructorTemplar
) extends IFunctionBodyMacro {

  val generatorId: String = "vale_runtime_sized_array_free"

  override def generateFunctionBody(
    env: FunctionEnvironment,
    temputs: Temputs,
    generatorId: String,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    originFunction1: Option[FunctionA],
    params2: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT = {
    val bodyEnv = FunctionEnvironmentBox(env)

    val Vector(rsaCoord @ CoordT(ShareT, ReadonlyT, RuntimeSizedArrayTT(RawArrayTT(elementCoord, _, _)))) = params2.map(_.tyype)

    val ret = CoordT(ShareT, ReadonlyT, VoidT())
    val header = FunctionHeaderT(env.fullName, Vector.empty, params2, ret, originFunction1)

    temputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val elementDropFunction = destructorTemplar.getDropFunction(env.globalEnv, temputs, elementCoord)
    val elementDropFunctorTE =
      env.globalEnv.functorHelper.getFunctorForPrototype(env, temputs, callRange, elementDropFunction)

    val expr =
      arrayTemplar.evaluateDestroyRuntimeSizedArrayIntoCallable(
        temputs, bodyEnv, originFunction1.get.range,
        ArgLookupTE(0, rsaCoord),
        elementDropFunctorTE)

    val function2 = FunctionT(header, BlockTE(Templar.consecutive(Vector(expr, ReturnTE(VoidLiteralTE())))))
    temputs.addFunction(function2)
    function2.header
  }
}
