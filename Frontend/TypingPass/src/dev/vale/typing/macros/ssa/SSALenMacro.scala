package dev.vale.typing.macros.ssa

import dev.vale.{Keywords, RangeS, StrI, vimpl}
import dev.vale.highertyping.FunctionA
import dev.vale.typing.{CompileErrorExceptionT, CompilerOutputs, RangedInternalErrorT}
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, ConsecutorTE, ConstantIntTE, DiscardTE, FunctionHeaderT, FunctionDefinitionT, LocationInFunctionEnvironmentT, ParameterT, ReturnTE}
import dev.vale.typing.env.FunctionEnvironmentT
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.types._
import dev.vale.typing.ast._
import dev.vale.typing.types.StaticSizedArrayTT
import dev.vale.typing.ast


class SSALenMacro(keywords: Keywords) extends IFunctionBodyMacro {
  val generatorId: StrI = keywords.vale_static_sized_array_len

  def generateFunctionBody(
    env: FunctionEnvironmentT,
    coutputs: CompilerOutputs,
    generatorId: StrI,
    life: LocationInFunctionEnvironmentT,
    callRange: List[RangeS],
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  (FunctionHeaderT, ReferenceExpressionTE) = {
    val header =
      FunctionHeaderT(env.id, Vector.empty, paramCoords, maybeRetCoord.get, Some(env.templata))
    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)
    val len =
      header.paramTypes match {
        case Vector(CoordT(_, contentsStaticSizedArrayTT(size, _, _, _))) => size
        case _ => throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "SSALenMacro received non-SSA param: " + header.paramTypes))
      }
    val body =
      BlockTE(
        ConsecutorTE(
          Vector(
            DiscardTE(ArgLookupTE(0, paramCoords(0).tyype)),
            ReturnTE(
              ConstantIntTE(len, 32)))))
    (header, body)
  }
}