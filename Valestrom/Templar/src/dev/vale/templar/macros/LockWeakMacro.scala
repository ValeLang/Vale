package dev.vale.templar.macros

import dev.vale.RangeS
import dev.vale.astronomer.FunctionA
import dev.vale.templar.Temputs
import dev.vale.templar.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, LockWeakTE, ParameterT, ReturnTE}
import dev.vale.templar.env.FunctionEnvironment
import dev.vale.templar.expression.ExpressionTemplar
import dev.vale.templar.types.{BorrowT, CoordT}
import dev.vale.templar.ast._
import dev.vale.templar.types._
import dev.vale.templar.ast


class LockWeakMacro(
  expressionTemplar: ExpressionTemplar
) extends IFunctionBodyMacro {
  val generatorId: String = "vale_lock_weak"

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

    val borrowCoord = paramCoords.head.tyype.copy(ownership = BorrowT)
    val (optCoord, someConstructor, noneConstructor) =
      expressionTemplar.getOption(temputs, env, callRange, borrowCoord)
    val lockExpr =
      LockWeakTE(
        ArgLookupTE(0, paramCoords.head.tyype),
        optCoord,
        someConstructor,
        noneConstructor)

    temputs.addFunction(FunctionT(header, BlockTE(ReturnTE(lockExpr))))

    header
  }
}