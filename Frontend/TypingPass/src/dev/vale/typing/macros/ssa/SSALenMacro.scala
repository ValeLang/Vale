package dev.vale.typing.macros.ssa

import dev.vale.RangeS
import dev.vale.highertyping.FunctionA
import dev.vale.typing.{CompileErrorExceptionT, RangedInternalErrorT, CompilerOutputs}
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, ConsecutorTE, ConstantIntTE, DiscardTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.typing.env.FunctionEnvironment
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.types.{CoordT, StaticSizedArrayTT}
import dev.vale.typing.ast._
import dev.vale.typing.types.StaticSizedArrayTT
import dev.vale.typing.ast


class SSALenMacro() extends IFunctionBodyMacro {
  val generatorId: String = "vale_static_sized_array_len"

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
    val len =
      header.paramTypes match {
        case Vector(CoordT(_, StaticSizedArrayTT(size, _, _, _))) => size
        case _ => throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "SSALenMacro received non-SSA param: " + header.paramTypes))
      }
    coutputs.addFunction(
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