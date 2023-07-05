package dev.vale.typing.macros.rsa

import dev.vale.highertyping.FunctionA
import dev.vale.postparsing._
import dev.vale.typing.{ArrayCompiler, CompilerOutputs, ast}
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionDefinitionT, LocationInFunctionEnvironmentT, NewMutRuntimeSizedArrayTE, ParameterT, ReturnTE}
import dev.vale.typing.env.{FunctionEnvironmentT, TemplataLookupContext}
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{Interner, Keywords, Profiler, RangeS, StrI, vassert, vassertSome, vimpl}
import dev.vale.postparsing.CodeRuneS
import dev.vale.typing.ast._
import dev.vale.typing.env.TemplataLookupContext
import dev.vale.typing.function.DestructorCompiler
import dev.vale.typing.templata.MutabilityTemplataT
import dev.vale.typing.types.RuntimeSizedArrayTT


class RSAMutableNewMacro(
  interner: Interner,
  keywords: Keywords,
  arrayCompiler: ArrayCompiler,
  destructorCompiler: DestructorCompiler
) extends IFunctionBodyMacro {
  val generatorId: StrI = keywords.vale_runtime_sized_array_mut_new

  def generateFunctionBody(
    env: FunctionEnvironmentT,
    coutputs: CompilerOutputs,
    generatorId: StrI,
    life: LocationInFunctionEnvironmentT,
    callRange: List[RangeS],
      callLocation: LocationInDenizen,
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  (FunctionHeaderT, ReferenceExpressionTE) = {
    val header =
      FunctionHeaderT(
        env.id, Vector.empty, paramCoords, maybeRetCoord.get, Some(env.templata))
    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val CoordTemplataT(elementType) =
      vassertSome(
        env.lookupNearestWithImpreciseName(
          interner.intern(RuneNameS(CodeRuneS(keywords.E))), Set(TemplataLookupContext)))

    val mutability =
      ITemplataT.expectMutability(
        vassertSome(
          env.lookupNearestWithImpreciseName(
            interner.intern(RuneNameS(CodeRuneS(keywords.M))), Set(TemplataLookupContext))))

    val arrayTT = arrayCompiler.resolveRuntimeSizedArray(elementType, mutability)

    val body =
      BlockTE(
        ReturnTE(
          NewMutRuntimeSizedArrayTE(
            arrayTT,
            ArgLookupTE(0, paramCoords(0).tyype))))
//            freePrototype)))
    (header, body)
  }
}