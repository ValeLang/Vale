package dev.vale.typing.function


import dev.vale.postparsing._
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.expression.CallCompiler
import dev.vale.{Err, Interner, Keywords, Ok, PackageCoordinate, RangeS, vassert, vfail, vimpl}
import dev.vale.highertyping._
import dev.vale.postparsing.patterns._
import dev.vale.postparsing.rules.OwnershipLiteralSL
import dev.vale.postparsing.GlobalFunctionFamilyNameS
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.{CompileErrorExceptionT, CompilerOutputs, CouldntFindFunctionToCallT, OverloadResolver, RangedInternalErrorT, TypingPassOptions}
import dev.vale.typing.ast.{DiscardTE, FunctionCallTE, PrototypeT, ReferenceExpressionTE}
import dev.vale.typing.env.{GlobalEnvironment, IInDenizenEnvironmentT, PackageEnvironmentT}
import dev.vale.typing.names.{IdT, PackageTopLevelNameT}
import dev.vale.typing.types._
import dev.vale.typing.{ast, _}
import dev.vale.typing.ast._
import dev.vale.typing.env._
import dev.vale.typing.function.FunctionCompiler.EvaluateFunctionSuccess
import dev.vale.typing.names.PackageTopLevelNameT

import scala.collection.immutable.List

class DestructorCompiler(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    structCompiler: StructCompiler,
    overloadCompiler: OverloadResolver) {
  def getDropFunction(
    env: IInDenizenEnvironmentT,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
    type2: CoordT):
  EvaluateFunctionSuccess = {
    val name = interner.intern(CodeNameS(keywords.drop))
    val args = Vector(type2)
    overloadCompiler.findFunction(
      env, coutputs, callRange, name, Vector.empty, Vector.empty, args, Vector(), true, true) match {
      case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
      case Ok(x) => x
    }
  }

  def drop(
    env: IInDenizenEnvironmentT,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
    undestructedExpr2: ReferenceExpressionTE):
  (ReferenceExpressionTE) = {
    val resultExpr2 =
      undestructedExpr2.result.coord match {
        case CoordT(ShareT, _, NeverT(_)) => undestructedExpr2
        case CoordT(ShareT, _, _) => DiscardTE(undestructedExpr2)
        case r@CoordT(OwnT, _, _) => {
          val destructorPrototype = getDropFunction(env, coutputs, callRange, r)
          vassert(coutputs.getInstantiationBounds(destructorPrototype.prototype.prototype.id).nonEmpty)
          FunctionCallTE(destructorPrototype.prototype.prototype, Vector(undestructedExpr2))
        }
        case CoordT(BorrowT, _, _) => (DiscardTE(undestructedExpr2))
        case CoordT(WeakT, _, _) => (DiscardTE(undestructedExpr2))
      }
    resultExpr2.result.coord.kind match {
      case VoidT() | NeverT(_) =>
      case _ => {
        throw CompileErrorExceptionT(
          RangedInternalErrorT(
            callRange,
            "Unexpected return type for drop autocall.\nReturn: " + resultExpr2.result.coord.kind + "\nParam: " + undestructedExpr2.result.coord))
      }
    }
    resultExpr2
  }
}
