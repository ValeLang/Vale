package dev.vale.typing.macros.ssa

import dev.vale.{Err, Interner, Keywords, Ok, RangeS, StrI, vassertSome, vimpl}
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.rules.{RuneParentEnvLookupSR, RuneUsage}
import dev.vale.postparsing.{CodeNameS, CodeRuneS, FunctorParamRuneNameS, FunctorPrototypeRuneNameS, FunctorReturnRuneNameS, IRuneS, RuneNameS}
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReturnTE, VoidLiteralTE}
import dev.vale.typing.env.{FunctionEnvironment, FunctionEnvironmentBox, TemplataEnvEntry, TemplataLookupContext}
import dev.vale.typing.{ArrayCompiler, CompileErrorExceptionT, Compiler, CompilerOutputs, CouldntEvaluateFunction, CouldntFindFunctionToCallT, OverloadResolver, RangedInternalErrorT, ast}
import dev.vale.typing.function.{DestructorCompiler, FunctionCompiler}
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.types._
import dev.vale.typing.ast._
import dev.vale.typing.types._
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.function.FunctionCompiler.{EvaluateFunctionFailure, EvaluateFunctionSuccess}
import dev.vale.typing.names.RuneNameT
import dev.vale.typing.templata.{CoordTemplata, PrototypeTemplata}

class SSAFreeMacro(
  interner: Interner,
  keywords: Keywords,
  arrayCompiler: ArrayCompiler,
  overloadResolver: OverloadResolver,
  destructorCompiler: DestructorCompiler
) extends IFunctionBodyMacro {

  val generatorId: StrI = keywords.vale_static_sized_array_free

  override def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: StrI,
    life: LocationInFunctionEnvironment,
    callRange: List[RangeS],
    originFunction1: Option[FunctionA],
    params2: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  (FunctionHeaderT, ReferenceExpressionTE) = {
    val Vector(ssaCoord @ CoordT(ShareT, arrayTT @ contentsStaticSizedArrayTT(_, _, _, elementCoord))) = params2.map(_.tyype)

    val ret = CoordT(ShareT, VoidT())
    val header = FunctionHeaderT(env.fullName, Vector.empty, params2, ret, Some(env.templata))

    val PrototypeTemplata(_, dropPrototype) =
      vassertSome(
        env.lookupNearestWithImpreciseName(
          interner.intern(RuneNameS(CodeRuneS(keywords.D))),
          Set(TemplataLookupContext)))
    val expr =
      DestroyStaticSizedArrayIntoFunctionTE(
        ArgLookupTE(0, ssaCoord), arrayTT, VoidLiteralTE(), dropPrototype)
    val body = BlockTE(Compiler.consecutive(Vector(expr, ReturnTE(VoidLiteralTE()))))
    (header, body)
  }
}
