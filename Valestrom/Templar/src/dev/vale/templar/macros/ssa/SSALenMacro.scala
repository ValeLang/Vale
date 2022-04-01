package dev.vale.templar.macros.ssa

import dev.vale.RangeS
import dev.vale.astronomer.FunctionA
import dev.vale.templar.{CompileErrorExceptionT, RangedInternalErrorT, Temputs}
import dev.vale.templar.ast.{ArgLookupTE, BlockTE, ConsecutorTE, ConstantIntTE, DiscardTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.templar.env.FunctionEnvironment
import dev.vale.templar.macros.IFunctionBodyMacro
import dev.vale.templar.types.{CoordT, StaticSizedArrayTT}
import dev.vale.templar.ast._
import dev.vale.templar.types.StaticSizedArrayTT
import dev.vale.templar.ast


class SSALenMacro() extends IFunctionBodyMacro {
  val generatorId: String = "vale_static_sized_array_len"

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
    val len =
      header.paramTypes match {
        case Vector(CoordT(_, StaticSizedArrayTT(size, _, _, _))) => size
        case _ => throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "SSALenMacro received non-SSA param: " + header.paramTypes))
      }
    temputs.addFunction(
      FunctionT(
        header,
        BlockTE(
          ConsecutorTE(
            Vector(
              DiscardTE(ArgLookupTE(0, paramCoords(0).tyype)),
              ReturnTE(
                ConstantIntTE(len, 32)))))))
    header
  }
}