package dev.vale.typing.macros

import dev.vale.{Keywords, RangeS, StrI, vimpl}
import dev.vale.highertyping.FunctionA
import dev.vale.typing.CompilerOutputs
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, LockWeakTE, ParameterT, ReturnTE}
import dev.vale.typing.env.FunctionEnvironment
import dev.vale.typing.expression.ExpressionCompiler
import dev.vale.typing.types._
import dev.vale.typing.ast._
import dev.vale.typing.types._
import dev.vale.typing.ast


class LockWeakMacro(
  keywords: Keywords,
  expressionCompiler: ExpressionCompiler
) extends IFunctionBodyMacro {
  val generatorId: StrI = keywords.vale_lock_weak

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

    val borrowCoord = paramCoords.head.tyype.copy(ownership = BorrowT)
    val (optCoord, someConstructor, noneConstructor, someImplFullName, noneImplFullName) =
      expressionCompiler.getOption(coutputs, env, callRange, borrowCoord)
    val lockExpr =
      LockWeakTE(
        ArgLookupTE(0, paramCoords.head.tyype),
        optCoord,
        someConstructor,
        noneConstructor,
        someImplFullName,
        noneImplFullName)

    val body = BlockTE(ReturnTE(lockExpr))

    (header, body)
  }
}