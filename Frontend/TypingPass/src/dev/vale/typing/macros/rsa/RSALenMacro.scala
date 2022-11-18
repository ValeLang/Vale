package dev.vale.typing.macros.rsa

import dev.vale.{Keywords, RangeS, StrI, vimpl}
import dev.vale.highertyping.FunctionA
import dev.vale.typing.CompilerOutputs
import dev.vale.typing.ast.{ArgLookupTE, ArrayLengthTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.typing.env.FunctionEnvironment
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.types.CoordT
import dev.vale.typing.ast._
import dev.vale.typing.ast


class RSALenMacro(keywords: Keywords) extends IFunctionBodyMacro {
  val generatorId: StrI = keywords.vale_runtime_sized_array_len

  def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: StrI,
    life: LocationInFunctionEnvironment,
    callRange: List[RangeS],
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  (FunctionHeaderT, ReferenceExpressionTE) = {
    val header =
      FunctionHeaderT(env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, Some(env.templata))
    val body =
      BlockTE(
        ReturnTE(
          ArrayLengthTE(
            ArgLookupTE(0, paramCoords(0).tyype))))
    (header, body)
  }
}