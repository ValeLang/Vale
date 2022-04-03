package dev.vale.typing.macros.ssa

import dev.vale.RangeS
import dev.vale.highertyping.FunctionA
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE, VoidLiteralTE}
import dev.vale.typing.env.{FunctionEnvironment, FunctionEnvironmentBox}
import dev.vale.typing.{ArrayCompiler, Compiler, CompilerOutputs}
import dev.vale.typing.function.DestructorCompiler
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.types.{CoordT, ShareT, StaticSizedArrayTT, VoidT}
import dev.vale.typing.ast._
import dev.vale.typing.env.FunctionEnvironmentBox
import dev.vale.typing.types._
import dev.vale.typing.ArrayCompiler

class SSAFreeMacro(
  arrayCompiler: ArrayCompiler,
  destructorCompiler: DestructorCompiler
) extends IFunctionBodyMacro {

  val generatorId: String = "vale_static_sized_array_free"

  override def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
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

    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val elementDropFunction = destructorCompiler.getDropFunction(env.globalEnv, coutputs, callRange, elementCoord)
    val elementDropFunctorTE =
      env.globalEnv.functorHelper.getFunctorForPrototype(env, coutputs, callRange, elementDropFunction)

    val expr =
      arrayCompiler.evaluateDestroyStaticSizedArrayIntoCallable(
        coutputs, bodyEnv, originFunction1.get.range,
        ArgLookupTE(0, rsaCoord),
        elementDropFunctorTE)

    val function2 = FunctionT(header, BlockTE(Compiler.consecutive(Vector(expr, ReturnTE(VoidLiteralTE())))))
    coutputs.addFunction(function2)
    function2.header
  }
}
