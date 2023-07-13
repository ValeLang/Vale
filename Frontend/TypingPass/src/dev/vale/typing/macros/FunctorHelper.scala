package dev.vale.typing.macros

import dev.vale.postparsing.rules._
import dev.vale.postparsing.{CodeNameS, FunctorPrototypeRuneNameS}
import dev.vale.{Interner, Keywords, Profiler, RangeS, StrI, vfail, vimpl, vwat}
import dev.vale.typing.{CompileErrorExceptionT, CompilerOutputs, CouldntEvaluateFunction, OverloadResolver}
import dev.vale.typing.ast.{ConstructTE, PrototypeT}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.env.{ExpressionLookupContext, FunctionEnvironmentT, TemplataEnvEntry, TemplataLookupContext}
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.typing.ast._
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.function.FunctionCompiler
import dev.vale.typing.function._
import dev.vale.typing.names.RuneNameT
import dev.vale.typing.templata.PrototypeTemplataT
import dev.vale.typing.types.CoordT

class FunctorHelper( interner: Interner, keywords: Keywords) {
  def getFunctorForPrototype(
    env: FunctionEnvironmentT,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
    dropFunction: PrototypeTemplataT):
  ReinterpretTE = {
    vfail()
//    val functorTemplate =
//      env.lookupNearestWithImpreciseName(
//        interner.intern(CodeNameS(keywords.underscoresCall)), Set(ExpressionLookupContext)) match {
//        case Some(st@FunctionTemplata(_, _)) => st
//        case other => vwat(other)
//      }
//    val functorPrototypeTT =
//      functionCompiler.evaluateGenericLightFunctionFromCallForPrototype(
//          coutputs, callRange, env, functorTemplate, , args) match {
//        case (EvaluateFunctionSuccess(prototype)) => (prototype)
//        case (EvaluateFunctionFailure(fffr)) => {
//          throw CompileErrorExceptionT(CouldntEvaluateFunction(callRange, fffr))
//        }
//      }
//
//    ReinterpretTE(
//      VoidLiteralTE(),
//      CoordT(ShareT, FunctorT(functorPrototypeTT)))
  }
}
