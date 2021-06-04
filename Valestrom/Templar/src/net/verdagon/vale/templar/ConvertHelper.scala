package net.verdagon.vale.templar

import net.verdagon.vale._
import net.verdagon.vale.astronomer.ITemplexA
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
    descendantCitizenRef: CitizenRef2,
    ancestorInterfaceRef: InterfaceRef2):
  Boolean
}

class ConvertHelper(
    opts: TemplarOptions,
    delegate: IConvertHelperDelegate) {
  def convertExprs(
      env: IEnvironment,
      temputs: Temputs,
      range: RangeS,
      sourceExprs: List[ReferenceExpression2],
      targetPointerTypes: List[Coord]):
  (List[ReferenceExpression2]) = {
    if (sourceExprs.size != targetPointerTypes.size) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "num exprs mismatch, source:\n" + sourceExprs + "\ntarget:\n" + targetPointerTypes))
    }
    (sourceExprs zip targetPointerTypes).foldLeft((List[ReferenceExpression2]()))({
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
      sourceExpr: ReferenceExpression2,
      targetPointerType: Coord):
  (ReferenceExpression2) = {
    val sourcePointerType = sourceExpr.resultRegister.reference

    if (sourceExpr.resultRegister.reference == targetPointerType) {
      return sourceExpr
    }

    if (sourceExpr.resultRegister.reference.kind == Never2()) {
      return sourceExpr
    }

    val Coord(targetOwnership, targetPermission, targetType) = targetPointerType;
    val Coord(sourceOwnership, sourcePermission, sourceType) = sourcePointerType;

    vcurious(targetPointerType.kind != Never2())

    // We make the hammer aware of nevers.
//    if (sourceType == Never2()) {
//      return (TemplarReinterpret2(sourceExpr, targetPointerType))
//    }

    (sourceOwnership, targetOwnership) match {
      case (Own, Own) =>
      case (Constraint, Own) => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Supplied a borrow but target wants to own the argument"))
      }
      case (Own, Constraint) => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Supplied an owning but target wants to only borrow"))
      }
      case (Constraint, Constraint) =>
      case (Share, Share) =>
      case (Own, Share) => vwat();
      case (Constraint, Share) => vwat();
      case (Weak, Weak) =>
    }

    (sourcePermission, targetPermission) match {
//      case (ExclusiveReadwrite, ExclusiveReadwrite) =>
//      case (Readwrite, ExclusiveReadwrite) => {
//        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Supplied a readwrite reference but target wants an xreadwrite!"))
//      }
//      case (Readonly, ExclusiveReadwrite) => {
//        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Supplied a readonly reference but target wants an xreadwrite!"))
//      }
//
//      case (ExclusiveReadwrite, Readwrite) => {
////        PermissionedSE(range, sourceExpr, Conversions.unevaluatePermission(targetPermission))
//      }
      case (Readwrite, Readwrite) =>
      case (Readonly, Readwrite) => {
        throw CompileErrorExceptionT(CantUseReadonlyReferenceAsReadwrite(range))
      }

//      case (ExclusiveReadwrite, Readonly) =>
      case (Readwrite, Readonly) =>
      case (Readonly, Readonly) =>
    }

    val sourceExprConverted =
      if (sourceType == targetType) {
        sourceExpr
      } else {
        (sourceType, targetType) match {
          case (s @ StructRef2(_), i : InterfaceRef2) => {
            convert(env.globalEnv, temputs, range, sourceExpr, s, i)
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
    sourceExpr: ReferenceExpression2,
    sourceStructRef: StructRef2,
    targetInterfaceRef: InterfaceRef2):
  (ReferenceExpression2) = {
    if (delegate.isAncestor(temputs, sourceStructRef, targetInterfaceRef)) {
      StructToInterfaceUpcast2(sourceExpr, targetInterfaceRef)
    } else {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't upcast a " + sourceStructRef + " to a " + targetInterfaceRef))
    }
  }
}