package dev.vale.templar.macros

import dev.vale.RangeS
import dev.vale.astronomer.FunctionA
import dev.vale.templar.Temputs
import dev.vale.templar.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, IsSameInstanceTE, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.templar.citizen.StructTemplar
import dev.vale.templar.env.FunctionEnvironment
import dev.vale.templar.types.CoordT
import dev.vale.RangeS
import dev.vale.templar.ast
import dev.vale.templar.ast._
import dev.vale.templar.function.FunctionTemplarCore

class SameInstanceMacro() extends IFunctionBodyMacro {
  val generatorId: String = "vale_same_instance"

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
            IsSameInstanceTE(
              ArgLookupTE(0, paramCoords(0).tyype), ArgLookupTE(1, paramCoords(1).tyype))))))
    header
  }
}
