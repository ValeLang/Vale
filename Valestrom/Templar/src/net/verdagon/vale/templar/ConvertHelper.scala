package net.verdagon.vale.templar

import net.verdagon.vale._
import net.verdagon.vale.templar.ast.{ReferenceExpressionTE, StructToInterfaceUpcastTE}
//import net.verdagon.vale.astronomer.IRulexSR
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env.{IEnvironment, IEnvironmentBox}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._

import scala.collection.immutable.List
//import net.verdagon.vale.carpenter.CovarianceCarpenter
import net.verdagon.vale.scout.{IEnvironment => _, FunctionEnvironment => _, Environment => _, _}

trait IConvertHelperDelegate {
  def isAncestor(
    temputs: Temputs,
    descendantCitizenRef: CitizenRefT,
    ancestorInterfaceRef: InterfaceTT):
  Boolean
}

class ConvertHelper(
    opts: TemplarOptions,
    delegate: IConvertHelperDelegate) {
  def convertExprs(
      env: IEnvironment,
      temputs: Temputs,
      range: RangeS,
      sourceExprs: Vector[ReferenceExpressionTE],
      targetPointerTypes: Vector[CoordT]):
  (Vector[ReferenceExpressionTE]) = {
    if (sourceExprs.size != targetPointerTypes.size) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "num exprs mismatch, source:\n" + sourceExprs + "\ntarget:\n" + targetPointerTypes))
    }
    (sourceExprs zip targetPointerTypes).foldLeft((Vector[ReferenceExpressionTE]()))({
      case ((previousRefExprs), (sourceExpr, targetPointerType)) => {
        val refExpr =
          convert(env, temputs, range, sourceExpr, targetPointerType)
        (previousRefExprs :+ refExpr)
      }
    })
  }

  def convert(
      env: IEnvironment,
      temputs: Temputs,
      range: RangeS,
      sourceExpr: ReferenceExpressionTE,
      targetPointerType: CoordT):
  (ReferenceExpressionTE) = {
    val sourcePointerType = sourceExpr.result.reference

    if (sourceExpr.result.reference == targetPointerType) {
      return sourceExpr
    }

    if (sourceExpr.result.reference.kind == NeverT()) {
      return sourceExpr
    }

    val CoordT(targetOwnership, targetPermission, targetType) = targetPointerType;
    val CoordT(sourceOwnership, sourcePermission, sourceType) = sourcePointerType;

    vcurious(targetPointerType.kind != NeverT())

    // We make the hammer aware of nevers.
//    if (sourceType == Never2()) {
//      return (TemplarReinterpret2(sourceExpr, targetPointerType))
//    }

    (sourceOwnership, targetOwnership) match {
      case (OwnT, OwnT) =>
      case (ConstraintT, OwnT) => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Supplied a borrow but target wants to own the argument"))
      }
      case (OwnT, ConstraintT) => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Supplied an owning but target wants to only borrow"))
      }
      case (ConstraintT, ConstraintT) =>
      case (ShareT, ShareT) =>
      case (OwnT, ShareT) => vwat();
      case (ConstraintT, ShareT) => vwat();
      case (WeakT, WeakT) =>
    }

    (sourcePermission, targetPermission) match {
      case (ReadwriteT, ReadwriteT) =>
      case (ReadonlyT, ReadwriteT) => {
        throw CompileErrorExceptionT(CantUseReadonlyReferenceAsReadwrite(range))
      }
      case (ReadwriteT, ReadonlyT) =>
      case (ReadonlyT, ReadonlyT) =>
    }

    val sourceExprConverted =
      if (sourceType == targetType) {
        sourceExpr
      } else {
        (sourceType, targetType) match {
          case (s @ StructTT(_), i : InterfaceTT) => {
            convert(env, temputs, range, sourceExpr, s, i)
          }
          case _ => vfail()
        }
      };

    (sourceExprConverted)
  }

  def convert(
    env: IEnvironment,
    temputs: Temputs,
    range: RangeS,
    sourceExpr: ReferenceExpressionTE,
    sourceStructRef: StructTT,
    targetInterfaceRef: InterfaceTT):
  (ReferenceExpressionTE) = {
    if (delegate.isAncestor(temputs, sourceStructRef, targetInterfaceRef)) {
      StructToInterfaceUpcastTE(sourceExpr, targetInterfaceRef)
    } else {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't upcast a " + sourceStructRef + " to a " + targetInterfaceRef))
    }
  }
}