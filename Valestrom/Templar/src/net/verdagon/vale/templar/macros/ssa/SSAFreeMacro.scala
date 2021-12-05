package net.verdagon.vale.templar.macros.ssa

import net.verdagon.vale.RangeS
import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox}
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.macros.IFunctionBodyMacro
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.{ArrayTemplar, Templar, Temputs}

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

    val Vector(rsaCoord @ CoordT(ShareT, ReadonlyT, StaticSizedArrayTT(_, _, _, elementCoord))) = params2.map(_.tyype)

    val ret = CoordT(ShareT, ReadonlyT, VoidT())
    val header = FunctionHeaderT(env.fullName, Vector.empty, params2, ret, originFunction1)

    temputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val elementDropFunction = destructorTemplar.getDropFunction(env.globalEnv, temputs, elementCoord)
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
