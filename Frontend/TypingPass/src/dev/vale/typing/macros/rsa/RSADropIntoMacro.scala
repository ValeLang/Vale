package dev.vale.typing.macros.rsa

import dev.vale.{Keywords, RangeS, StrI}
import dev.vale.highertyping.FunctionA
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.typing.env.{FunctionEnvironment, FunctionEnvironmentBox}
import dev.vale.typing.{ArrayCompiler, CompilerOutputs}
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.types.CoordT
import dev.vale.typing.ast._
import dev.vale.typing.env.FunctionEnvironmentBox
import dev.vale.typing.ast

class RSADropIntoMacro(keywords: Keywords, arrayCompiler: ArrayCompiler) extends IFunctionBodyMacro {
  val generatorId: StrI = keywords.vale_runtime_sized_array_drop_into

  def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: StrI,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT = {
    val header =
      FunctionHeaderT(env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)
    val fate = FunctionEnvironmentBox(env)
    coutputs.addFunction(
      FunctionT(
        header,
        BlockTE(
          ReturnTE(
            arrayCompiler.evaluateDestroyRuntimeSizedArrayIntoCallable(
              coutputs,
              fate,
              callRange,
              ArgLookupTE(0, paramCoords(0).tyype),
              ArgLookupTE(1, paramCoords(1).tyype))))))
    header
  }
}