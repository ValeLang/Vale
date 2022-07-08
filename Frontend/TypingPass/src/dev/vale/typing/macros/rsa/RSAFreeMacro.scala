package dev.vale.typing.macros.rsa

import dev.vale.{Keywords, RangeS, StrI}
import dev.vale.highertyping.FunctionA
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE, VoidLiteralTE}
import dev.vale.typing.env.{FunctionEnvironment, FunctionEnvironmentBox}
import dev.vale.typing.{ArrayCompiler, Compiler, CompilerOutputs}
import dev.vale.typing.function.DestructorCompiler
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.types.{CoordT, RuntimeSizedArrayTT, ShareT, VoidT}
import dev.vale.typing.ast._
import dev.vale.typing.env.FunctionEnvironmentBox
import dev.vale.typing.types._
import dev.vale.typing.ArrayCompiler

class RSAFreeMacro(
  keywords: Keywords,
  arrayCompiler: ArrayCompiler,
  destructorCompiler: DestructorCompiler
) extends IFunctionBodyMacro {

  val generatorId: StrI = keywords.vale_runtime_sized_array_free

  override def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: StrI,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    originFunction1: Option[FunctionA],
    params2: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT = {
    val bodyEnv = FunctionEnvironmentBox(env)

    val Vector(rsaCoord @ CoordT(ShareT, RuntimeSizedArrayTT(_, elementCoord))) = params2.map(_.tyype)

    val ret = CoordT(ShareT, VoidT())
    val header = FunctionHeaderT(env.fullName, Vector.empty, params2, ret, originFunction1)

    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val elementDropFunction = destructorCompiler.getDropFunction(env.globalEnv, coutputs, callRange, elementCoord)
    val elementDropFunctorTE =
      env.globalEnv.functorHelper.getFunctorForPrototype(env, coutputs, callRange, elementDropFunction)

    val expr =
      arrayCompiler.evaluateDestroyRuntimeSizedArrayIntoCallable(
        coutputs, bodyEnv, originFunction1.get.range,
        ArgLookupTE(0, rsaCoord),
        elementDropFunctorTE)

    val function2 = FunctionT(header, BlockTE(Compiler.consecutive(Vector(expr, ReturnTE(VoidLiteralTE())))))
    coutputs.addFunction(function2)
    function2.header
  }
}
