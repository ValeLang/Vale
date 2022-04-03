package dev.vale.typing.macros

import dev.vale.{RangeS, vassertSome, vfail}
import dev.vale.highertyping.FunctionA
import dev.vale.typing.{CantDowncastToInterface, CantDowncastUnrelatedTypes, CompileErrorExceptionT, RangedInternalErrorT, CompilerOutputs}
import dev.vale.typing.ast.{ArgLookupTE, AsSubtypeTE, BlockTE, FunctionCallTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, ReferenceExpressionTE, ReturnTE}
import dev.vale.typing.citizen.AncestorHelper
import dev.vale.typing.env.FunctionEnvironment
import dev.vale.typing.expression.ExpressionCompiler
import dev.vale.typing.templata.KindTemplata
import dev.vale.typing.types.{CitizenRefT, CoordT, InterfaceTT, StructTT}
import dev.vale.RangeS
import dev.vale.typing.ast._
import dev.vale.typing.env.FunctionEnvironmentBox
import dev.vale.typing.types.InterfaceTT
import dev.vale.typing.ast

class AsSubtypeMacro(
  ancestorHelper: AncestorHelper,
  expressionCompiler: ExpressionCompiler) extends IFunctionBodyMacro {
  val generatorId: String = "vale_as_subtype"

  def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: String,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT = {
    val header =
      FunctionHeaderT(env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
    coutputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val sourceKind = vassertSome(paramCoords.headOption).tyype.kind
    val KindTemplata(targetKind) = vassertSome(env.fullName.last.templateArgs.headOption)

    val sourceCitizen =
      sourceKind match {
        case c : CitizenRefT => c
        case _ => throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
      }

    val targetCitizen =
      targetKind match {
        case c : CitizenRefT => c
        case _ => throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
      }

    // We dont support downcasting to interfaces yet
    val targetStruct =
      targetCitizen match {
        case sr @ StructTT(_) => sr
        case ir @ InterfaceTT(_) => throw CompileErrorExceptionT(CantDowncastToInterface(callRange, ir))
        case _ => vfail()
      }


    val incomingCoord = paramCoords(0).tyype
    val incomingSubkind = incomingCoord.kind

    // Because we dont yet put borrows in structs
    val resultOwnership = incomingCoord.ownership
    val successCoord = CoordT(resultOwnership, targetKind)
    val failCoord = CoordT(resultOwnership, incomingSubkind)
    val (resultCoord, okConstructor, errConstructor) =
      expressionCompiler.getResult(coutputs, env, callRange, successCoord, failCoord)
    if (resultCoord != vassertSome(maybeRetCoord)) {
      throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Bad result coord:\n" + resultCoord + "\nand\n" + vassertSome(maybeRetCoord)))
    }

    val asSubtypeExpr: ReferenceExpressionTE =
      sourceCitizen match {
        case sourceInterface @ InterfaceTT(_) => {
          if (ancestorHelper.isAncestor(coutputs, targetStruct, sourceInterface).nonEmpty) {
            AsSubtypeTE(
              ArgLookupTE(0, incomingCoord),
              targetKind,
              resultCoord,
              okConstructor,
              errConstructor)
          } else {
            throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
          }
        }
        case sourceStruct @ StructTT(_) => {
          if (sourceStruct == targetStruct) {
            FunctionCallTE(
              okConstructor,
              Vector(ArgLookupTE(0, incomingCoord)))
          } else {
            throw CompileErrorExceptionT(CantDowncastUnrelatedTypes(callRange, sourceKind, targetKind))
          }
        }
      }

    coutputs.addFunction(FunctionT(header, BlockTE(ReturnTE(asSubtypeExpr))))

    header
  }
}