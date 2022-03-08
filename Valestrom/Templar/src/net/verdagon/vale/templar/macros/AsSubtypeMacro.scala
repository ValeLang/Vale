package net.verdagon.vale.templar.macros

import net.verdagon.vale.{RangeS, vassert, vassertSome, vfail}
import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.citizen.AncestorHelper
import net.verdagon.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox}
import net.verdagon.vale.templar.expression.ExpressionTemplar
import net.verdagon.vale.templar.templata.KindTemplata
import net.verdagon.vale.templar.types.{BorrowT, CitizenRefT, CoordT, InterfaceTT, PointerT, StructTT}
import net.verdagon.vale.templar.{ArrayTemplar, CantDowncastToInterface, CantDowncastUnrelatedTypes, CompileErrorExceptionT, RangedInternalErrorT, Temputs, ast}

class AsSubtypeMacro(
  ancestorHelper: AncestorHelper,
  expressionTemplar: ExpressionTemplar) extends IFunctionBodyMacro {
  val generatorId: String = "vale_as_subtype"

  def generateFunctionBody(
    env: FunctionEnvironment,
    temputs: Temputs,
    generatorId: String,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT = {
    val header =
      ast.FunctionHeaderT(env.fullName, Vector.empty, paramCoords, maybeRetCoord.get, originFunction)
    temputs.declareFunctionReturnType(header.toSignature, header.returnType)

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
    val resultOwnership =
      incomingCoord.ownership match {
        case BorrowT => PointerT
        case other => other
      }
    val successCoord =
      CoordT(
        resultOwnership,
        incomingCoord.permission,
        targetKind)
    val failCoord =
      CoordT(
        resultOwnership,
        incomingCoord.permission,
        incomingSubkind)
    val (resultCoord, okConstructor, errConstructor) =
      expressionTemplar.getResult(temputs, env, callRange, successCoord, failCoord)
    if (resultCoord != vassertSome(maybeRetCoord)) {
      throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Bad result coord:\n" + resultCoord + "\nand\n" + vassertSome(maybeRetCoord)))
    }

    val asSubtypeExpr: ReferenceExpressionTE =
      sourceCitizen match {
        case sourceInterface @ InterfaceTT(_) => {
          if (ancestorHelper.isAncestor(temputs, targetStruct, sourceInterface).nonEmpty) {
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

    temputs.addFunction(ast.FunctionT(header, BlockTE(ReturnTE(asSubtypeExpr))))

    header
  }
}