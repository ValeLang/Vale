package dev.vale.typing.macros.rsa

import dev.vale.highertyping.FunctionA
import dev.vale.postparsing._
import dev.vale.typing.{ArrayCompiler, CompileErrorExceptionT, CompilerErrorHumanizer, CompilerOutputs, CouldntFindFunctionToCallT, OverloadResolver, ast}
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionDefinitionT, LocationInFunctionEnvironmentT, NewImmRuntimeSizedArrayTE, ParameterT, ReturnTE}
import dev.vale.typing.env.{FunctionEnvironmentT, TemplataLookupContext}
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{Err, Interner, Keywords, Ok, Profiler, RangeS, StrI, vassert, vassertSome, vfail, vimpl, vwat}
import dev.vale.postparsing.CodeRuneS
import dev.vale.typing.ast._
import dev.vale.typing.env.TemplataLookupContext
import dev.vale.typing.function.DestructorCompiler
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

//    val PrototypeTemplata(generatorRange, generatorFullName, generatorReturnCoord) =
//      vassertSome(
//        env.lookupNearestWithImpreciseName(
//          interner.intern(RuneNameS(CodeRuneS(keywords.F))), Set(TemplataLookupContext)))

//    val variability =
//      mutability match {
//        case PlaceholderTemplata(fullNameT, tyype) => vimpl()
//        case MutabilityTemplata(ImmutableT) => FinalT
//        case MutabilityTemplata(MutableT) => VaryingT
//      }

    val arrayTT = arrayCompiler.resolveRuntimeSizedArray(elementType, mutability)

    val generatorArgCoord =
      paramCoords(1).tyype match {
        case CoordT(ShareT, kind) => CoordT(ShareT, kind)
        case CoordT(BorrowT, kind) => CoordT(BorrowT, kind)
        case CoordT(OwnT, kind) => vwat() // shouldnt happen, signature takes in an &
      }

    val generatorPrototype =
      overloadResolver.findFunction(
        env,
        coutputs,
        callRange,
        interner.intern(CodeNameS(keywords.underscoresCall)),
        Vector(),
        Vector(),
        Vector(generatorArgCoord, CoordT(ShareT, IntT(32))),
        Vector(),
        false,
        true) match {
        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
        case Ok(x) => x
      }

    vassert(generatorPrototype.prototype.prototype.returnType.ownership == ShareT)

    val sizeTE = ArgLookupTE(0, paramCoords(0).tyype)
    val generatorTE = ArgLookupTE(1, paramCoords(1).tyype)

    val body =
      BlockTE(
        ReturnTE(
          NewImmRuntimeSizedArrayTE(
            arrayTT,
            sizeTE,
            generatorTE,
            generatorPrototype.prototype.prototype)))
    (header, body)
  }
}
