package net.verdagon.vale.templar.macros

import net.verdagon.vale.{Profiler, RangeS}
import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.templar.{ArrayTemplar, IFunctionGenerator, Temputs, ast}
import net.verdagon.vale.templar.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, IsSameInstanceTE, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env.FunctionEnvironment
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplarCore}
import net.verdagon.vale.templar.types.CoordT

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
      ast.FunctionHeaderT(env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
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
