package dev.vale.typing.macros.rsa

import dev.vale.highertyping.FunctionA
import dev.vale.postparsing._
import dev.vale.typing.{ArrayCompiler, CompileErrorExceptionT, CompilerErrorHumanizer, CompilerOutputs, CouldntFindFunctionToCallT, OverloadResolver, ast}
import dev.vale.typing.ast._
import dev.vale.typing.env.{FunctionEnvironmentT, TemplataLookupContext}
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{Err, Interner, Keywords, Ok, Profiler, RangeS, StrI, vassert, vassertSome, vfail, vimpl, vwat}
import dev.vale.postparsing.CodeRuneS
import dev.vale.typing.ast._
import dev.vale.typing.env.TemplataLookupContext
import dev.vale.typing.function.DestructorCompiler
import dev.vale.typing.names._
import dev.vale.typing.templata.ITemplataT._
import dev.vale.typing.templata.PrototypeTemplataT
import dev.vale.typing.types._


class RSAImmutableNewMacro(
  interner: Interner,
  keywords: Keywords,
  overloadResolver: OverloadResolver,
  arrayCompiler: ArrayCompiler,
  destructorCompiler: DestructorCompiler
) extends IFunctionBodyMacro {
  val generatorId: StrI = keywords.vale_runtime_sized_array_imm_new

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
        env.id,
        Vector.empty,
//        Vector(RegionT(env.defaultRegion.localName, true)),
        paramCoords,
        maybeRetCoord.get,
        Some(env.templata))
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

    val region = RegionT(expectRegionPlaceholder(expectRegion(vassertSome(env.id.localName.templateArgs.lastOption))))

    val arrayTT = arrayCompiler.resolveRuntimeSizedArray(elementType, mutability, region)

    val generatorArgCoord =
      paramCoords(1).tyype match {
        case c @ CoordT(ShareT, _, _) => c
        case c @ CoordT(BorrowT, _, _) => c
        case CoordT(OwnT, _, _) => vwat() // shouldnt happen, signature takes in an &
      }

    val generatorPrototype =
      overloadResolver.findFunction(
        env,
        coutputs,
        callRange,
        callLocation,
        interner.intern(CodeNameS(keywords.underscoresCall)),
        Vector(),
        Vector(),
        region,
        Vector(generatorArgCoord, CoordT(ShareT, paramCoords(0).tyype.region, IntT(32))),
        Vector(),
        false) match {
        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
        case Ok(x) => x
      }

    vassert(generatorPrototype.prototype.returnType.ownership == ShareT)

    val sizeTE = ArgLookupTE(0, paramCoords(0).tyype)
    val generatorTE = ArgLookupTE(1, paramCoords(1).tyype)

    vimpl() // pure?
    val body =
      BlockTE(
        ReturnTE(
          NewImmRuntimeSizedArrayTE(
            arrayTT,
            vassertSome(maybeRetCoord).region,
            sizeTE,
            generatorTE,
            generatorPrototype.prototype)))
    (header, body)
  }
}
