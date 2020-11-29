package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.LocalVariableA
import net.verdagon.vale.scout.{MaybeUsed, NotUsed, RangeS}
import net.verdagon.vale.templar.env.{AddressibleLocalVariable2, FunctionEnvironmentBox, ILocalVariable2, ReferenceLocalVariable2}
import net.verdagon.vale.templar.function.{DestructorTemplar, DropHelper}
import net.verdagon.vale.templar.templata.Conversions
import net.verdagon.vale.templar.types.{Bool2, Borrow, Coord, Final, Float2, Int2, InterfaceRef2, Kind, KnownSizeArrayT2, Mutability, Mutable, OverloadSet, Own, Ownership, PackT2, RawArrayT2, Share, Str2, StructRef2, TupleT2, UnknownSizeArrayT2, Variability, Void2, Weak}
import net.verdagon.vale.{vassert, vfail}

import scala.collection.immutable.List

class LocalHelper(
    opts: TemplarOptions,
  dropHelper: DropHelper) {

  def makeTemporaryLocal(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    coord: Coord):
  ReferenceLocalVariable2 = {
    val varNameCounter = fate.nextVarCounter()
    val varId = fate.functionEnvironment.fullName.addStep(TemplarTemporaryVarName2(varNameCounter))
    val rlv = ReferenceLocalVariable2(varId, Final, coord)
    fate.addVariable(rlv)
    rlv
  }

  // This makes a borrow ref, but can easily turn that into a weak
  // separately.
  def makeTemporaryLocal(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    r: ReferenceExpression2):
  (Defer2) = {
    val rlv = makeTemporaryLocal(temputs, fate, r.resultRegister.reference)
    val letExpr2 = LetAndLend2(rlv, r)

    val unlet = unletLocal(fate, rlv)
    val destructExpr2 =
      dropHelper.drop(fate, temputs, unlet)
    vassert(destructExpr2.referend == Void2())

    // No Discard here because the destructor already returns void.

    (Defer2(letExpr2, destructExpr2))
  }

  def unletLocal(fate: FunctionEnvironmentBox, localVar: ILocalVariable2):
  (Unlet2) = {
    fate.markLocalUnstackified(localVar.id)
    Unlet2(localVar)
  }

  def unletAll(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    variables: List[ILocalVariable2]):
  (List[ReferenceExpression2]) = {
    variables match {
      case Nil => (List())
      case head :: tail => {
        val unlet = unletLocal(fate, head)
        val maybeHeadExpr2 =
          dropHelper.drop(fate, temputs, unlet)
        val tailExprs2 =
          unletAll(temputs, fate, tail)
        (maybeHeadExpr2 :: tailExprs2)
      }
    }
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
    localVariableA: LocalVariableA,
    referenceType2: Coord):
  ILocalVariable2 = {
    val varId = NameTranslator.translateVarNameStep(localVariableA.varName)
    val variability = Conversions.evaluateVariability(localVariableA.variability)

    if (fate.getVariable(varId).nonEmpty) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "There's already a variable named " + varId))
    }

    val mutable = Templar.getMutability(temputs, referenceType2.referend)
    val addressible = LocalHelper.determineIfLocalIsAddressible(mutable, localVariableA)

    val fullVarName = fate.fullName.addStep(varId)
    val localVar =
      if (addressible) {
        AddressibleLocalVariable2(fullVarName, variability, referenceType2)
      } else {
        ReferenceLocalVariable2(fullVarName, variability, referenceType2)
      }
    localVar
  }

  def maybeSoftLoad(
    fate: FunctionEnvironmentBox,
    range: RangeS,
    expr2: Expression2,
    targetOwnership: Ownership):
  (ReferenceExpression2) = {
    expr2 match {
      case e : ReferenceExpression2 => (e)
      case e : AddressExpression2 => softLoad(fate, range, e, targetOwnership)
    }
  }

  def softLoad(fate: FunctionEnvironmentBox, loadRange: RangeS, a: AddressExpression2, specifiedTargetOwnership: Ownership):
  (ReferenceExpression2) = {
    specifiedTargetOwnership match {
      case Borrow => {
        val actualTargetOwnership =
          a.resultRegister.reference.ownership match {
            case Own => Borrow
            case Borrow => Borrow // it's fine if they accidentally borrow a borrow ref
            case Weak => Weak // If you borrow a weak, just interpret it as a weak
            case Share => Share
          }
        (SoftLoad2(a, actualTargetOwnership))
      }
      case Weak => {
        val actualTargetOwnership =
          a.resultRegister.reference.ownership match {
            case Own => Weak
            case Borrow => Weak // it's fine if they weak a borrow ref
            case Weak => Weak // it's fine if they accidentally weak a borrow ref
            case Share => Share
          }
        (SoftLoad2(a, actualTargetOwnership))
      }
      case Own => {
        a.resultRegister.reference.ownership match {
          case Own => {
            val localVar =
              a match {
                case LocalLookup2(_, lv, _, _) => lv
                case AddressMemberLookup2(_, _, name, _, _) => {
                  throw CompileErrorExceptionT(CantMoveOutOfMemberT(loadRange, name.last))
                }
              }
            fate.markLocalUnstackified(localVar.id)
            (Unlet2(localVar))
          }
          case Borrow | Share | Weak => {
            (SoftLoad2(a, a.resultRegister.reference.ownership))
          }
        }
      }
    }
  }

  def borrowSoftLoad(temputs: Temputs, expr2: AddressExpression2):
  ReferenceExpression2 = {
    val ownership =
      getBorrowOwnership(temputs, expr2.resultRegister.reference.referend)
    SoftLoad2(expr2, ownership)
  }

  def getBorrowOwnership(temputs: Temputs, referend: Kind):
  Ownership = {
    referend match {
      case Int2() => Share
      case Bool2() => Share
      case Float2() => Share
      case Str2() => Share
      case Void2() => Share
      //      case FunctionT2(_, _) => Raw
      case PackT2(_, understruct2) => {
        val mutability = Templar.getMutability(temputs, understruct2)
        if (mutability == Mutable) Borrow else Share
      }
      case TupleT2(_, understruct2) => {
        val mutability = Templar.getMutability(temputs, understruct2)
        if (mutability == Mutable) Borrow else Share
      }
      case KnownSizeArrayT2(_, RawArrayT2(_, mutability)) => {
        if (mutability == Mutable) Borrow else Share
      }
      case UnknownSizeArrayT2(array) => {
        if (array.mutability == Mutable) Borrow else Share
      }
      //      case TemplatedClosure2(_, structRef, _) => {
      //        val mutability = Templar.getMutability(temputs, structRef)
      //        if (mutability == Mutable) Borrow else Share
      //      }
      //      case OrdinaryClosure2(_, structRef, _) => {
      //        val mutability = Templar.getMutability(temputs, structRef)
      //        if (mutability == Mutable) Borrow else Share
      //      }
      case sr2 @ StructRef2(_) => {
        val mutability = Templar.getMutability(temputs, sr2)
        if (mutability == Mutable) Borrow else Share
      }
      case ir2 @ InterfaceRef2(_) => {
        val mutability = Templar.getMutability(temputs, ir2)
        if (mutability == Mutable) Borrow else Share
      }
      case OverloadSet(_, _, voidStructRef) => {
        getBorrowOwnership(temputs, voidStructRef)
      }
    }
  }

}

object LocalHelper {
  // See ClosureTests for requirements here
  def determineIfLocalIsAddressible(mutability: Mutability, variable1: LocalVariableA): Boolean = {
    if (mutability == Mutable) {
      variable1.childMutated != NotUsed || variable1.selfMoved == MaybeUsed || variable1.childMoved != NotUsed
    } else {
      variable1.childMutated != NotUsed
    }
  }
}