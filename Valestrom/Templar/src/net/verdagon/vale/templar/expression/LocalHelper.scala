package net.verdagon.vale.templar.expression

import net.verdagon.vale.astronomer.LocalA
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{MaybeUsed, NotUsed, RangeS}
import net.verdagon.vale.templar.env.{AddressibleLocalVariableT, FunctionEnvironmentBox, ILocalVariableT, ReferenceLocalVariableT}
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.templata.Conversions
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar._
import net.verdagon.vale.{vassert, vfail}

import scala.collection.immutable.List

class LocalHelper(
    opts: TemplarOptions,
  destructorTemplar: DestructorTemplar) {

  def makeTemporaryLocal(
    fate: FunctionEnvironmentBox,
    life: LocationInFunctionEnvironment,
    coord: CoordT):
  ReferenceLocalVariableT = {
    val varId = fate.functionEnvironment.fullName.addStep(TemplarTemporaryVarNameT(life))
    val rlv = ReferenceLocalVariableT(varId, FinalT, coord)
    fate.addVariable(rlv)
    rlv
  }

  // This makes a borrow ref, but can easily turn that into a weak
  // separately.
  def makeTemporaryLocal(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    life: LocationInFunctionEnvironment,
    r: ReferenceExpressionTE):
  (DeferTE) = {
    val rlv = makeTemporaryLocal(fate, life, r.resultRegister.reference)
    val letExpr2 = LetAndLendTE(rlv, r)

    val unlet = unletLocal(fate, rlv)
    val destructExpr2 =
      destructorTemplar.drop(fate, temputs, unlet)
    vassert(destructExpr2.kind == VoidT())

    // No Discard here because the destructor already returns void.

    (DeferTE(letExpr2, destructExpr2))
  }

  def unletLocal(fate: FunctionEnvironmentBox, localVar: ILocalVariableT):
  (UnletTE) = {
    fate.markLocalUnstackified(localVar.id)
    UnletTE(localVar)
  }

  def unletAll(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    variables: Vector[ILocalVariableT]):
  (Vector[ReferenceExpressionTE]) = {
    variables.map({ case variable =>
      val unlet = unletLocal(fate, variable)
      val maybeHeadExpr2 =
        destructorTemplar.drop(fate, temputs, unlet)
      maybeHeadExpr2
    })
  }

  // A user local variable is one that the user can address inside their code.
  // Users never see the names of non-user local variables, so they can't be
  // looked up.
  // Non-user local variables are reference local variables, so can't be
  // mutated from inside closures.
  def makeUserLocalVariable(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    localVariableA: LocalA,
    referenceType2: CoordT):
  ILocalVariableT = {
    val varId = NameTranslator.translateVarNameStep(localVariableA.varName)

    if (fate.getVariable(varId).nonEmpty) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "There's already a variable named " + varId))
    }

    val variability = LocalHelper.determineLocalVariability(localVariableA)

    val mutable = Templar.getMutability(temputs, referenceType2.kind)
    val addressible = LocalHelper.determineIfLocalIsAddressible(mutable, localVariableA)

    val fullVarName = fate.fullName.addStep(varId)
    val localVar =
      if (addressible) {
        AddressibleLocalVariableT(fullVarName, variability, referenceType2)
      } else {
        ReferenceLocalVariableT(fullVarName, variability, referenceType2)
      }
    fate.addVariable(localVar)
    localVar
  }

  def maybeBorrowSoftLoad(
      temputs: Temputs,
      expr2: ExpressionT):
  ReferenceExpressionTE = {
    expr2 match {
      case e : ReferenceExpressionTE => e
      case e : AddressExpressionTE => borrowSoftLoad(temputs, e)
    }
  }

//  def maybeSoftLoad(
//    fate: FunctionEnvironmentBox,
//    range: RangeS,
//    expr2: Expression2,
//    specifiedTargetOwnership: Option[Ownership]):
//  (ReferenceExpression2) = {
//    expr2 match {
//      case e : ReferenceExpression2 => (e)
//      case e : AddressExpression2 => softLoad(fate, range, e, specifiedTargetOwnership)
//    }
//  }

  def softLoad(
      fate: FunctionEnvironmentBox,
      loadRange: RangeS,
      a: AddressExpressionTE,
      loadAsP: LoadAsP):
  ReferenceExpressionTE = {
    a.resultRegister.reference.ownership match {
      case ShareT => {
        SoftLoadTE(a, ShareT, ReadonlyT)
      }
      case OwnT => {
        loadAsP match {
          case UseP => {
            a match {
              case LocalLookupTE(_, lv, _, _) => {
                fate.markLocalUnstackified(lv.id)
                UnletTE(lv)
              }
              // See CSHROOR for why these aren't just Readwrite.
              case l @ RuntimeSizedArrayLookupTE(_, _, _, _, _, _) => SoftLoadTE(l, ConstraintT, a.resultRegister.reference.permission)
              case l @ StaticSizedArrayLookupTE(_, _, _, _, _, _) => SoftLoadTE(l, ConstraintT, a.resultRegister.reference.permission)
              case l @ ReferenceMemberLookupTE(_,_, _, _, _, _) => SoftLoadTE(l, ConstraintT, a.resultRegister.reference.permission)
              case l @ AddressMemberLookupTE(_, _, _, _, _) => SoftLoadTE(l, ConstraintT, a.resultRegister.reference.permission)
            }
          }
          case MoveP => {
            a match {
              case LocalLookupTE(_, lv, _, _) => {
                fate.markLocalUnstackified(lv.id)
                UnletTE(lv)
              }
              case ReferenceMemberLookupTE(_,_, name, _, _, _) => {
                throw CompileErrorExceptionT(CantMoveOutOfMemberT(loadRange, name.last))
              }
              case AddressMemberLookupTE(_, _, name, _, _) => {
                throw CompileErrorExceptionT(CantMoveOutOfMemberT(loadRange, name.last))
              }
            }
          }
          case LendConstraintP(None) => SoftLoadTE(a, ConstraintT, a.resultRegister.reference.permission)
          case LendConstraintP(Some(permission)) => SoftLoadTE(a, ConstraintT, Conversions.evaluatePermission(permission))
          case LendWeakP(permission) => SoftLoadTE(a, WeakT, Conversions.evaluatePermission(permission))
        }
      }
      case ConstraintT => {
        loadAsP match {
          case MoveP => vfail()
          case UseP => SoftLoadTE(a, ConstraintT, a.resultRegister.reference.permission)
          case LendConstraintP(None) => SoftLoadTE(a, ConstraintT, a.resultRegister.reference.permission)
          case LendConstraintP(Some(permission)) => SoftLoadTE(a, ConstraintT, Conversions.evaluatePermission(permission))
          case LendWeakP(permission) => SoftLoadTE(a, WeakT, Conversions.evaluatePermission(permission))
        }
      }
      case WeakT => {
        loadAsP match {
          case UseP => SoftLoadTE(a, WeakT, a.resultRegister.reference.permission)
          case MoveP => vfail()
          case LendConstraintP(None) => SoftLoadTE(a, WeakT, a.resultRegister.reference.permission)
          case LendConstraintP(Some(permission)) => SoftLoadTE(a, WeakT, Conversions.evaluatePermission(permission))
          case LendWeakP(permission) => SoftLoadTE(a, WeakT, Conversions.evaluatePermission(permission))
        }
      }
    }
  }

  def borrowSoftLoad(temputs: Temputs, expr2: AddressExpressionTE):
  ReferenceExpressionTE = {
    val ownership = getBorrowOwnership(temputs, expr2.resultRegister.reference.kind)
    SoftLoadTE(expr2, ownership, expr2.resultRegister.reference.permission)
  }

  def getBorrowOwnership(temputs: Temputs, kind: KindT):
  OwnershipT = {
    kind match {
      case IntT(_) => ShareT
      case BoolT() => ShareT
      case FloatT() => ShareT
      case StrT() => ShareT
      case VoidT() => ShareT
      //      case FunctionT2(_, _) => Raw
      case PackTT(_, understruct2) => {
        val mutability = Templar.getMutability(temputs, understruct2)
        if (mutability == MutableT) ConstraintT else ShareT
      }
      case TupleTT(_, understruct2) => {
        val mutability = Templar.getMutability(temputs, understruct2)
        if (mutability == MutableT) ConstraintT else ShareT
      }
      case StaticSizedArrayTT(_, RawArrayTT(_, mutability, _)) => {
        if (mutability == MutableT) ConstraintT else ShareT
      }
      case RuntimeSizedArrayTT(RawArrayTT(_, mutability, _)) => {
        if (mutability == MutableT) ConstraintT else ShareT
      }
      //      case TemplatedClosure2(_, structTT, _) => {
      //        val mutability = Templar.getMutability(temputs, structTT)
      //        if (mutability == Mutable) Borrow else Share
      //      }
      //      case OrdinaryClosure2(_, structTT, _) => {
      //        val mutability = Templar.getMutability(temputs, structTT)
      //        if (mutability == Mutable) Borrow else Share
      //      }
      case sr2 @ StructTT(_) => {
        val mutability = Templar.getMutability(temputs, sr2)
        if (mutability == MutableT) ConstraintT else ShareT
      }
      case ir2 @ InterfaceTT(_) => {
        val mutability = Templar.getMutability(temputs, ir2)
        if (mutability == MutableT) ConstraintT else ShareT
      }
      case OverloadSet(_, _, voidStructRef) => {
        getBorrowOwnership(temputs, voidStructRef)
      }
    }
  }
}

object LocalHelper {
  // See ClosureTests for requirements here
  def determineIfLocalIsAddressible(mutability: MutabilityT, localA: LocalA): Boolean = {
    if (mutability == MutableT) {
      localA.childMutated != NotUsed || localA.selfMoved == MaybeUsed || localA.childMoved != NotUsed
    } else {
      localA.childMutated != NotUsed
    }
  }

  def determineLocalVariability(localA: LocalA): VariabilityT = {
    if (localA.selfMutated != NotUsed || localA.childMutated != NotUsed) {
      VaryingT
    } else {
      FinalT
    }
  }
}