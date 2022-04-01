package dev.vale.templar.macros.rsa

import dev.vale.astronomer.FunctionA
import dev.vale.scout.{CodeRuneS, RuneNameS}
import dev.vale.templar.Temputs
import dev.vale.templar.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, PopRuntimeSizedArrayTE, ReturnTE}
import dev.vale.templar.env.{FunctionEnvironment, TemplataLookupContext}
import dev.vale.templar.macros.IFunctionBodyMacro
import dev.vale.templar.templata.CoordTemplata
import dev.vale.templar.types.{CoordT, MutableT, RuntimeSizedArrayTT}
import dev.vale.{Interner, RangeS, vassertSome}
import dev.vale.scout.CodeRuneS
import dev.vale.templar.ast._
import dev.vale.templar.env.TemplataLookupContext
import dev.vale.templar.types._
import dev.vale.templar.ast
import dev.vale.{Interner, Profiler, RangeS, vassertSome}


class RSAMutablePopMacro( interner: Interner) extends IFunctionBodyMacro {
  val generatorId: String = "vale_runtime_sized_array_pop"

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
      FunctionHeaderT(
        env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
    temputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val CoordTemplata(elementType) =
      vassertSome(
        env.lookupNearestWithImpreciseName(
          interner.intern(RuneNameS(CodeRuneS("E"))), Set(TemplataLookupContext)))

    val arrayTT = interner.intern(RuntimeSizedArrayTT(MutableT, elementType))

    temputs.addFunction(
      FunctionT(
        header,
        BlockTE(
          ReturnTE(
            PopRuntimeSizedArrayTE(
              ArgLookupTE(0, paramCoords(0).tyype))))))
    header
  }
}