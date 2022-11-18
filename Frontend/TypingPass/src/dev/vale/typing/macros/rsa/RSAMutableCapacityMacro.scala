package dev.vale.typing.macros.rsa

import dev.vale.highertyping.FunctionA
import dev.vale.postparsing._
import dev.vale.typing.CompilerOutputs
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE, RuntimeSizedArrayCapacityTE}
import dev.vale.typing.env.{FunctionEnvironment, TemplataLookupContext}
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.templata.{CoordTemplata, MutabilityTemplata}
import dev.vale.typing.types._
import dev.vale.{Interner, Keywords, Profiler, RangeS, StrI, vassertSome, vimpl}
import dev.vale.postparsing.CodeRuneS
import dev.vale.typing.ast._
import dev.vale.typing.env.TemplataLookupContext
import dev.vale.typing.types._
import dev.vale.typing.ast


class RSAMutableCapacityMacro(interner: Interner, keywords: Keywords) extends IFunctionBodyMacro {
  val generatorId: StrI = keywords.vale_runtime_sized_array_capacity

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
      FunctionHeaderT(
        env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, Some(env.templata))

//    val CoordTemplata(elementType) =
//      vassertSome(
//        env.lookupNearestWithImpreciseName(
//          interner.intern(RuneNameS(CodeRuneS(keywords.E))), Set(TemplataLookupContext)))

    val body =
      BlockTE(
        ReturnTE(
          RuntimeSizedArrayCapacityTE(
            ArgLookupTE(0, paramCoords(0).tyype))))
    (header, body)
  }
}