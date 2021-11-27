package net.verdagon.vale.templar.macros

import net.verdagon.vale.{RangeS, vassertOne, vwat}
import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.env.FunctionEnvironment
import net.verdagon.vale.templar.types.{CoordT, StaticSizedArrayTT}
import net.verdagon.vale.templar.{CompileErrorExceptionT, RangedInternalErrorT, Temputs, ast}


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
      ast.FunctionHeaderT(env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
    temputs.declareFunctionReturnType(header.toSignature, header.returnType)
    val len =
      header.paramTypes match {
        case Vector(CoordT(_, _, StaticSizedArrayTT(size, _))) => size
        case _ => throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "SSALenMacro received non-SSA param: " + header.paramTypes))
      }
    temputs.addFunction(
      ast.FunctionT(
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