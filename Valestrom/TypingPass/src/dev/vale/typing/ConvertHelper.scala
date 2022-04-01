package dev.vale.typing

import dev.vale.typing.ast.{ReferenceExpressionTE, StructToInterfaceUpcastTE}
import dev.vale.typing.env.IEnvironment
import dev.vale.{RangeS, vcurious, vfail}
import dev.vale.typing.types.{BorrowT, CitizenRefT, CoordT, InterfaceTT, NeverT, OwnT, ShareT, StructTT, WeakT}
import dev.vale._
import dev.vale.typing.ast._
//import dev.vale.astronomer.IRulexSR
import dev.vale.typing.citizen.AncestorHelper
import dev.vale.typing.env.IEnvironmentBox
import dev.vale.typing.templata._
import dev.vale.typing.types._

import scala.collection.immutable.List
//import dev.vale.carpenter.CovarianceCarpenter
import dev.vale.postparsing.{_}

trait IConvertHelperDelegate {
  def isAncestor(
    coutputs: CompilerOutputs,
    descendantCitizenRef: CitizenRefT,
    ancestorInterfaceRef: InterfaceTT):
  Boolean
}

class ConvertHelper(
    opts: TypingPassOptions,
    delegate: IConvertHelperDelegate) {
  def convertExprs(
      env: IEnvironment,
      coutputs: CompilerOutputs,
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
          convert(env, coutputs, range, sourceExpr, targetPointerType)
        (previousRefExprs :+ refExpr)
      }
    })
  }

  def convert(
      env: IEnvironment,
      coutputs: CompilerOutputs,
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
//      return (CompilerReinterpret2(sourceExpr, targetPointerType))
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
            convert(env, coutputs, range, sourceExpr, s, i)
          }
          case _ => vfail()
        }
      };

    (sourceExprConverted)
  }

  def convert(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    range: RangeS,
    sourceExpr: ReferenceExpressionTE,
    sourceStructRef: StructTT,
    targetInterfaceRef: InterfaceTT):
  (ReferenceExpressionTE) = {
    if (delegate.isAncestor(coutputs, sourceStructRef, targetInterfaceRef)) {
      StructToInterfaceUpcastTE(sourceExpr, targetInterfaceRef)
    } else {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't upcast a " + sourceStructRef + " to a " + targetInterfaceRef))
    }
  }
}