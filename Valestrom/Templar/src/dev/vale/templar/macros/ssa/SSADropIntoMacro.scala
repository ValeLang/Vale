package dev.vale.templar.macros.ssa

import dev.vale.RangeS
import dev.vale.astronomer.FunctionA
import dev.vale.templar.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox}
import dev.vale.templar.{ArrayTemplar, Temputs}
import dev.vale.templar.macros.IFunctionBodyMacro
import dev.vale.templar.types.CoordT
import dev.vale.templar.ast._
import dev.vale.templar.env.FunctionEnvironmentBox
import dev.vale.templar.ast

class SSADropIntoMacro(arrayTemplar: ArrayTemplar) extends IFunctionBodyMacro {
  val generatorId: String = "vale_static_sized_array_drop_into"

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
    val fate = FunctionEnvironmentBox(env)
    temputs.addFunction(
      FunctionT(
        header,
        BlockTE(
          ReturnTE(
            arrayTemplar.evaluateDestroyStaticSizedArrayIntoCallable(
              temputs,
              fate,
              callRange,
              ArgLookupTE(0, paramCoords(0).tyype),
              ArgLookupTE(1, paramCoords(1).tyype))))))
    header
  }
}
