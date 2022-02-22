package net.verdagon.vale.templar.macros.rsa

import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.scout.{CodeRuneS, RuneNameS}
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.env.{FunctionEnvironment, TemplataLookupContext}
import net.verdagon.vale.templar.macros.IFunctionBodyMacro
import net.verdagon.vale.templar.templata.{CoordTemplata, MutabilityTemplata, PrototypeTemplata}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.{Temputs, ast}
import net.verdagon.vale.{IProfiler, Interner, RangeS, vassertSome}


class RSAImmutableNewMacro(profiler: IProfiler, interner: Interner) extends IFunctionBodyMacro {
  val generatorId: String = "vale_runtime_sized_array_imm_new"

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

    val MutabilityTemplata(mutability) =
      vassertSome(
        env.lookupNearestWithImpreciseName(
          profiler, interner.intern(RuneNameS(CodeRuneS("M"))), Set(TemplataLookupContext)))

    val PrototypeTemplata(generatorPrototype) =
      vassertSome(
        env.lookupNearestWithImpreciseName(
          profiler, interner.intern(RuneNameS(CodeRuneS("F"))), Set(TemplataLookupContext)))

    val variability =
      mutability match {
        case ImmutableT => FinalT
        case MutableT => VaryingT
      }

    val arrayTT = interner.intern(RuntimeSizedArrayTT(mutability, elementType))

    temputs.addFunction(
      ast.FunctionT(
        header,
        BlockTE(
          ReturnTE(
            NewImmRuntimeSizedArrayTE(
              arrayTT,
              ArgLookupTE(0, paramCoords(0).tyype),
              ArgLookupTE(1, paramCoords(1).tyype),
              generatorPrototype)))))
    header
  }
}