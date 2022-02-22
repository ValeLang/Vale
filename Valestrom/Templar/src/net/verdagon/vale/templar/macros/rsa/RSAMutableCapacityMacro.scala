package net.verdagon.vale.templar.macros.rsa

import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.scout.{CodeRuneS, RuneNameS}
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.env.{FunctionEnvironment, TemplataLookupContext}
import net.verdagon.vale.templar.macros.IFunctionBodyMacro
import net.verdagon.vale.templar.templata.CoordTemplata
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.{Temputs, ast}
import net.verdagon.vale.{IProfiler, Interner, RangeS, vassertSome}


class RSAMutableCapacityMacro(profiler: IProfiler, interner: Interner) extends IFunctionBodyMacro {
  val generatorId: String = "vale_runtime_sized_array_capacity"

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
      ast.FunctionHeaderT(
        env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
    temputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val CoordTemplata(elementType) =
      vassertSome(
        env.lookupNearestWithImpreciseName(
          profiler, interner.intern(RuneNameS(CodeRuneS("E"))), Set(TemplataLookupContext)))

    val arrayTT = RuntimeSizedArrayTT(MutableT, elementType)

    temputs.addFunction(
      ast.FunctionT(
        header,
        BlockTE(
          ReturnTE(
            RuntimeSizedArrayCapacityTE(
              ArgLookupTE(0, paramCoords(0).tyype))))))
    header
  }
}