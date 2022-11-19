package dev.vale.typing.macros.rsa

import dev.vale.{Err, Interner, Keywords, Ok, RangeS, StrI, vassert, vassertSome, vimpl}
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.{CodeNameS, CodeRuneS, FunctorParamRuneNameS, FunctorPrototypeRuneNameS, FunctorReturnRuneNameS, IRuneS, RuneNameS}
import dev.vale.postparsing.rules.{RuneParentEnvLookupSR, RuneUsage}
import dev.vale.typing.ast.{ArgLookupTE, BlockTE, FunctionHeaderT, FunctionDefinitionT, LocationInFunctionEnvironment, ParameterT, ReturnTE, VoidLiteralTE}
import dev.vale.typing.env.{FunctionEnvironment, FunctionEnvironmentBox, TemplataEnvEntry, TemplataLookupContext}
import dev.vale.typing.{ArrayCompiler, CompileErrorExceptionT, Compiler, CompilerOutputs, CouldntFindFunctionToCallT, OverloadResolver}
import dev.vale.typing.function.DestructorCompiler
import dev.vale.typing.macros.IFunctionBodyMacro
import dev.vale.typing.types._
import dev.vale.typing.ast._
import dev.vale.typing.types._
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.names.RuneNameT
import dev.vale.typing.templata.{CoordTemplata, PrototypeTemplata}

class RSAFreeMacro(
  interner: Interner,
  keywords: Keywords,
  arrayCompiler: ArrayCompiler,
  overloadResolver: OverloadResolver,
  destructorCompiler: DestructorCompiler
) extends IFunctionBodyMacro {

  val generatorId: StrI = keywords.vale_runtime_sized_array_free

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
    val Vector(rsaCoord @ CoordT(ShareT, arrayTT @ contentsRuntimeSizedArrayTT(_, _))) = params2.map(_.tyype)

    val ret = CoordT(ShareT, VoidT())
    val header = FunctionHeaderT(env.fullName, Vector.empty, params2, ret, Some(env.templata))

//    val dropFunction = destructorCompiler.getDropFunction(env, coutputs, callRange, elementCoord)
//    val args = Vector(CoordT(ShareT, VoidT())) ++ dropFunction.prototype.paramTypes
//    val newEnv =
//      env.addEntries(
//        interner,
//        Vector(
//          interner.intern(RuneNameT(FunctorParamRuneNameS(0))) -> TemplataEnvEntry(CoordTemplata(dropFunction.prototype.paramTypes.head)),
//          interner.intern(RuneNameT(FunctorReturnRuneNameS())) -> TemplataEnvEntry(CoordTemplata(dropFunction.prototype.returnType))))
//    val callName = interner.intern(CodeNameS(keywords.underscoresCall))
//    val callRules =
//      Vector(
//        RuneParentEnvLookupSR(callRange.head, RuneUsage(callRange.head, FunctorParamRuneNameS(0))),
//        RuneParentEnvLookupSR(callRange.head, RuneUsage(callRange.head, FunctorReturnRuneNameS())))
//    val callRunes = Vector[IRuneS](FunctorParamRuneNameS(0), FunctorReturnRuneNameS())
//    val consumerPrototype =
//      overloadResolver.findFunction(
//        newEnv, coutputs, callRange, callName, callRules, callRunes, args, Vector(), true, true) match {
//        case Ok(prototype) => prototype.prototype
//        case Err(fffr) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, fffr))
//      }

    val PrototypeTemplata(_, dropPrototype) =
      vassertSome(
        env.lookupNearestWithImpreciseName(
          interner.intern(RuneNameS(CodeRuneS(keywords.D))),
          Set(TemplataLookupContext)))

//    val freePrototype =
//      destructorCompiler.getFreeFunction(
//        coutputs, env, callRange, rsaCoord)
//        .function.prototype
//    vassert(coutputs.getInstantiationBounds(freePrototype.fullName).nonEmpty)

    val expr =
      DestroyImmRuntimeSizedArrayTE(
        ArgLookupTE(0, rsaCoord), arrayTT, VoidLiteralTE(), dropPrototype)

    val body = BlockTE(Compiler.consecutive(Vector(expr, ReturnTE(VoidLiteralTE()))))
    (header, body)
  }
}
