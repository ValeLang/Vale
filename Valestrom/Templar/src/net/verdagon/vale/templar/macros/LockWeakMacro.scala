package net.verdagon.vale.templar.macros

import net.verdagon.vale.RangeS
import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.env.FunctionEnvironment
import net.verdagon.vale.templar.expression.ExpressionTemplar
import net.verdagon.vale.templar.types.{PointerT, CoordT}
import net.verdagon.vale.templar.{Temputs, ast}


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
      ast.FunctionHeaderT(env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
    temputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val borrowCoord = paramCoords.head.tyype.copy(ownership = PointerT)
    val (optCoord, someConstructor, noneConstructor) =
      expressionTemplar.getOption(temputs, env, callRange, borrowCoord)
    val lockExpr =
      LockWeakTE(
        ArgLookupTE(0, paramCoords.head.tyype),
        optCoord,
        someConstructor,
        noneConstructor)

    temputs.addFunction(ast.FunctionT(header, BlockTE(ReturnTE(lockExpr))))

    header
  }
}