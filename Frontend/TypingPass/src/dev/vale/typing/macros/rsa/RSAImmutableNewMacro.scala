package dev.vale.typing.macros.rsa

import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.{CodeRuneS, RuneNameS}
import dev.vale.typing.CompilerOutputs
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, NewImmRuntimeSizedArrayTE, ParameterT, ReturnTE}
import dev.vale.typing.env.{FunctionEnvironment, TemplataLookupContext}
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.templata.{CoordTemplata, MutabilityTemplata, PrototypeTemplata}
import dev.vale.typing.types.{CoordT, FinalT, ImmutableT, MutableT, RuntimeSizedArrayTT, VaryingT}
import dev.vale.{Interner, Keywords, Profiler, RangeS, StrI, vassertSome}
import dev.vale.postparsing.CodeRuneS
import dev.vale.typing.ast._
import dev.vale.typing.env.TemplataLookupContext
import dev.vale.typing.templata.PrototypeTemplata
import dev.vale.typing.types._
import dev.vale.typing.ast


class RSAImmutableNewMacro(interner: Interner, keywords: Keywords) extends IFunctionBodyMacro {
  val generatorId: StrI = keywords.vale_runtime_sized_array_imm_new

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
      FunctionHeaderT(
        env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val CoordTemplata(elementType) =
      vassertSome(
        env.lookupNearestWithImpreciseName(
          interner.intern(RuneNameS(CodeRuneS(keywords.E))), Set(TemplataLookupContext)))

    val MutabilityTemplata(mutability) =
      vassertSome(
        env.lookupNearestWithImpreciseName(
          interner.intern(RuneNameS(CodeRuneS(keywords.M))), Set(TemplataLookupContext)))

    val PrototypeTemplata(generatorPrototype) =
      vassertSome(
        env.lookupNearestWithImpreciseName(
          interner.intern(RuneNameS(CodeRuneS(keywords.F))), Set(TemplataLookupContext)))

    val variability =
      mutability match {
        case ImmutableT => FinalT
        case MutableT => VaryingT
      }

    val arrayTT = interner.intern(RuntimeSizedArrayTT(mutability, elementType))

    coutputs.addFunction(
      FunctionT(
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