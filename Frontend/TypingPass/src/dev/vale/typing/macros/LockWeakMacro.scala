package dev.vale.typing.macros

import dev.vale.{Keywords, RangeS, StrI}
import dev.vale.highertyping.FunctionA
import dev.vale.typing.CompilerOutputs
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, LockWeakTE, ParameterT, ReturnTE}
import dev.vale.typing.env.FunctionEnvironment
import dev.vale.typing.expression.ExpressionCompiler
import dev.vale.typing.types.{BorrowT, CoordT}
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
    callRange: RangeS,
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT = {
    val header =
      FunctionHeaderT(env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val borrowCoord = paramCoords.head.tyype.copy(ownership = BorrowT)
    val (optCoord, someConstructor, noneConstructor) =
      expressionCompiler.getOption(coutputs, env, callRange, borrowCoord)
    val lockExpr =
      LockWeakTE(
        ArgLookupTE(0, paramCoords.head.tyype),
        optCoord,
        someConstructor,
        noneConstructor)

    coutputs.addFunction(FunctionT(header, BlockTE(ReturnTE(lockExpr))))

    header
  }
}