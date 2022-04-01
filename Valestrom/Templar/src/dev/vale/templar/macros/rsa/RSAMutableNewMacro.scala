package dev.vale.templar.macros.rsa

import dev.vale.astronomer.FunctionA
import dev.vale.scout.{CodeRuneS, RuneNameS}
import dev.vale.templar.Temputs
import dev.vale.templar.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, NewMutRuntimeSizedArrayTE, ParameterT, ReturnTE}
import dev.vale.templar.env.{FunctionEnvironment, TemplataLookupContext}
import dev.vale.templar.macros.IFunctionBodyMacro
import dev.vale.templar.templata.{CoordTemplata, MutabilityTemplata}
import dev.vale.templar.types.{CoordT, RuntimeSizedArrayTT}
import dev.vale.{Interner, RangeS, vassertSome}
import dev.vale.scout.CodeRuneS
import dev.vale.templar.ast._
import dev.vale.templar.env.TemplataLookupContext
import dev.vale.templar.templata.MutabilityTemplata
import dev.vale.templar.types.RuntimeSizedArrayTT
import dev.vale.templar.ast
import dev.vale.{Interner, Profiler, RangeS, vassertSome}


class RSAMutableNewMacro( interner: Interner) extends IFunctionBodyMacro {
  val generatorId: String = "vale_runtime_sized_array_mut_new"

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

    val MutabilityTemplata(mutability) =
      vassertSome(
        env.lookupNearestWithImpreciseName(
          interner.intern(RuneNameS(CodeRuneS("M"))), Set(TemplataLookupContext)))

    val arrayTT = interner.intern(RuntimeSizedArrayTT(mutability, elementType))

    temputs.addFunction(
      FunctionT(
        header,
        BlockTE(
          ReturnTE(
            NewMutRuntimeSizedArrayTE(
              arrayTT,
              ArgLookupTE(0, paramCoords(0).tyype))))))
    header
  }
}