package dev.vale.templar.macros.rsa

import dev.vale.RangeS
import dev.vale.astronomer.FunctionA
import dev.vale.templar.Temputs
import dev.vale.templar.ast.{ArgLookupTE, ArrayLengthTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.templar.env.FunctionEnvironment
import dev.vale.templar.macros.IFunctionBodyMacro
import dev.vale.templar.types.CoordT
import dev.vale.templar.ast._
import dev.vale.templar.ast


class RSALenMacro() extends IFunctionBodyMacro {
  val generatorId: String = "vale_runtime_sized_array_len"

  def generateFunctionBody(
    env: FunctionEnvironment,
    temputs: Temputs,
    generatorId: String,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT = {
    val header =
      FunctionHeaderT(env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
    temputs.declareFunctionReturnType(header.toSignature, header.returnType)
    temputs.addFunction(
      FunctionT(
        header,
        BlockTE(
          ReturnTE(
            ArrayLengthTE(
              ArgLookupTE(0, paramCoords(0).tyype))))))
    header
  }
}