package dev.vale.templar

import dev.vale.templar.ast.{ReferenceExpressionTE, StructToInterfaceUpcastTE}
import dev.vale.templar.env.IEnvironment
import dev.vale.{RangeS, vcurious, vfail}
import dev.vale.templar.types.{BorrowT, CitizenRefT, CoordT, InterfaceTT, NeverT, OwnT, ShareT, StructTT, WeakT}
import dev.vale._
import dev.vale.templar.ast._
//import dev.vale.astronomer.IRulexSR
import dev.vale.templar.citizen.AncestorHelper
import dev.vale.templar.env.IEnvironmentBox
import dev.vale.templar.templata._
import dev.vale.templar.types._

import scala.collection.immutable.List
//import dev.vale.carpenter.CovarianceCarpenter
import dev.vale.scout.{_}

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

    sourceExpr.result.reference.kind match {
      case NeverT(_) => return sourceExpr
      case _ =>
    }

    val CoordT(targetOwnership, targetType) = targetPointerType;
    val CoordT(sourceOwnership, sourceType) = sourcePointerType;

    targetPointerType.kind match {
      case NeverT(_) => vcurious()
      case _ =>
    }

    // We make the hammer aware of nevers.
//    if (sourceType == Never2()) {
//      return (TemplarReinterpret2(sourceExpr, targetPointerType))
//    }

    (sourceOwnership, targetOwnership) match {
      case (OwnT, OwnT) =>
      case (BorrowT, OwnT) => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Supplied a borrow but target wants to own the argument"))
      }
      case (OwnT, BorrowT) => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Supplied an owning but target wants to only borrow"))
      }
      case (BorrowT, BorrowT) =>
      case (ShareT, ShareT) =>
      case (WeakT, WeakT) =>
      case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Supplied a " + sourceOwnership + " but target wants " + targetOwnership))
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