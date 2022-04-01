package dev.vale.templar.macros.ssa

import dev.vale.RangeS
import dev.vale.astronomer.FunctionA
import dev.vale.templar.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE, VoidLiteralTE}
import dev.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox}
import dev.vale.templar.{ArrayTemplar, Templar, Temputs}
import dev.vale.templar.function.DestructorTemplar
import dev.vale.templar.macros.IFunctionBodyMacro
import dev.vale.templar.types.{CoordT, ShareT, StaticSizedArrayTT, VoidT}
import dev.vale.templar.ast._
import dev.vale.templar.env.FunctionEnvironmentBox
import dev.vale.templar.types._
import dev.vale.templar.ArrayTemplar

class SSAFreeMacro(
  arrayTemplar: ArrayTemplar,
  destructorTemplar: DestructorTemplar
) extends IFunctionBodyMacro {

  val generatorId: String = "vale_static_sized_array_free"

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

    val Vector(rsaCoord @ CoordT(ShareT, StaticSizedArrayTT(_, _, _, elementCoord))) = params2.map(_.tyype)

    val ret = CoordT(ShareT, VoidT())
    val header = FunctionHeaderT(env.fullName, Vector.empty, params2, ret, originFunction1)

    temputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val elementDropFunction = destructorTemplar.getDropFunction(env.globalEnv, temputs, callRange, elementCoord)
    val elementDropFunctorTE =
      env.globalEnv.functorHelper.getFunctorForPrototype(env, temputs, callRange, elementDropFunction)

    val expr =
      arrayTemplar.evaluateDestroyStaticSizedArrayIntoCallable(
        temputs, bodyEnv, originFunction1.get.range,
        ArgLookupTE(0, rsaCoord),
        elementDropFunctorTE)

    val function2 = FunctionT(header, BlockTE(Templar.consecutive(Vector(expr, ReturnTE(VoidLiteralTE())))))
    temputs.addFunction(function2)
    function2.header
  }
}
