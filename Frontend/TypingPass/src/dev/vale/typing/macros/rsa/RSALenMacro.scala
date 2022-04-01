package dev.vale.typing.macros.rsa

import dev.vale.RangeS
import dev.vale.highertyping.FunctionA
import dev.vale.typing.CompilerOutputs
import dev.vale.typing.ast.{ArgLookupTE, ArrayLengthTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.typing.env.FunctionEnvironment
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.types.CoordT
import dev.vale.typing.ast._
import dev.vale.typing.ast


class RSALenMacro() extends IFunctionBodyMacro {
  val generatorId: String = "vale_runtime_sized_array_len"

  def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: String,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT = {
    val header =
      FunctionHeaderT(env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)
    coutputs.addFunction(
      FunctionT(
        header,
        BlockTE(
          ReturnTE(
            ArrayLengthTE(
              ArgLookupTE(0, paramCoords(0).tyype))))))
    header
  }
}