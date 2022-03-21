package net.verdagon.vale.templar.expression

import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast._
import net.verdagon.vale.scout.{LocalS, NotUsed}
import net.verdagon.vale.templar.env.{AddressibleLocalVariableT, FunctionEnvironmentBox, ILocalVariableT, NodeEnvironmentBox, ReferenceLocalVariableT}
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.templata.Conversions
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.{ast, _}
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.names.{NameTranslator, TemplarTemporaryVarNameT}
import net.verdagon.vale.{Interner, RangeS, vassert, vfail, vimpl, vwat}

import scala.collection.immutable.List

class LocalHelper(
    opts: TemplarOptions,
    interner: Interner,
    nameTranslator: NameTranslator,
    destructorTemplar: DestructorTemplar) {

  def makeTemporaryLocal(
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    coord: CoordT):
  ReferenceLocalVariableT = {
    val varId = nenv.functionEnvironment.fullName.addStep(interner.intern(TemplarTemporaryVarNameT(life)))
    val rlv = ReferenceLocalVariableT(varId, FinalT, coord)
    nenv.addVariable(rlv)
    rlv
  }

  // This makes a borrow ref, but can easily turn that into a weak
  // separately.
  def makeTemporaryLocal(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    range: RangeS,
    life: LocationInFunctionEnvironment,
    r: ReferenceExpressionTE,
    targetOwnership: OwnershipT):
  (DeferTE) = {
    targetOwnership match {
      case BorrowT =>
    }

    val rlv = makeTemporaryLocal(nenv, life, r.result.reference)
    val letExpr2 = LetAndLendTE(rlv, r, targetOwnership)

    val unlet = unletLocalWithoutDropping(nenv, rlv)
    val destructExpr2 =
      destructorTemplar.drop(nenv.snapshot, temputs, range, unlet)
    vassert(destructExpr2.kind == VoidT())

    // No Discard here because the destructor already returns void.

    (ast.DeferTE(letExpr2, destructExpr2))
  }

  def unletLocalWithoutDropping(nenv: NodeEnvironmentBox, localVar: ILocalVariableT):
  (UnletTE) = {
    nenv.markLocalUnstackified(localVar.id)
    UnletTE(localVar)
  }

  def unletAndDropAll(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    range: RangeS,
    variables: Vector[ILocalVariableT]):
  (Vector[ReferenceExpressionTE]) = {
    variables.map({ case variable =>
      val unlet = unletLocalWithoutDropping(nenv, variable)
      val maybeHeadExpr2 =
        destructorTemplar.drop(nenv.snapshot, temputs, range, unlet)
      maybeHeadExpr2
    })
  }

  def unletAllWithoutDropping(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    range: RangeS,
    variables: Vector[ILocalVariableT]):
  (Vector[ReferenceExpressionTE]) = {
    variables.map(variable => unletLocalWithoutDropping(nenv, variable))
  }

  // A user local variable is one that the user can address inside their code.
  // Users never see the names of non-user local variables, so they can't be
  // looked up.
  // Non-user local variables are reference local variables, so can't be
  // mutated from inside closures.
  def makeUserLocalVariable(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    range: RangeS,
    localVariableA: LocalS,
    referenceType2: CoordT):
  ILocalVariableT = {
    val varId = nameTranslator.translateVarNameStep(localVariableA.varName)

    if (nenv.getVariable(varId).nonEmpty) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "There's already a variable named " + varId))
    }

    val variability = LocalHelper.determineLocalVariability(localVariableA)

    val mutable = Templar.getMutability(temputs, referenceType2.kind)
    val addressible = LocalHelper.determineIfLocalIsAddressible(mutable, localVariableA)

    val fullVarName = nenv.fullName.addStep(varId)
    val localVar =
      if (addressible) {
        AddressibleLocalVariableT(fullVarName, variability, referenceType2)
      } else {
        ReferenceLocalVariableT(fullVarName, variability, referenceType2)
      }
    nenv.addVariable(localVar)
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

  def softLoad(
      nenv: NodeEnvironmentBox,
      loadRange: RangeS,
      a: AddressExpressionTE,
      loadAsP: LoadAsP):
  ReferenceExpressionTE = {
    a.result.reference.ownership match {
      case ShareT => {
        SoftLoadTE(a, ShareT)
      }
      case OwnT => {
        loadAsP match {
          case UseP => {
            a match {
              case LocalLookupTE(_, lv) => {
                nenv.markLocalUnstackified(lv.id)
                UnletTE(lv)
              }
              // See CSHROOR for why these aren't just Readwrite.
              case l @ RuntimeSizedArrayLookupTE(_, _, _, _, _) => SoftLoadTE(l, BorrowT)
              case l @ StaticSizedArrayLookupTE(_, _, _, _, _) => SoftLoadTE(l, BorrowT)
              case l @ ReferenceMemberLookupTE(_,_, _, _, _) => SoftLoadTE(l, BorrowT)
              case l @ AddressMemberLookupTE(_, _, _, _, _) => SoftLoadTE(l, BorrowT)
            }
          }
          case MoveP => {
            a match {
              case LocalLookupTE(_, lv) => {
                nenv.markLocalUnstackified(lv.id)
                UnletTE(lv)
              }
              case ReferenceMemberLookupTE(_,_, name, _, _) => {
                throw CompileErrorExceptionT(CantMoveOutOfMemberT(loadRange, name.last))
              }
              case AddressMemberLookupTE(_, _, name, _, _) => {
                throw CompileErrorExceptionT(CantMoveOutOfMemberT(loadRange, name.last))
              }
            }
          }
          case LoadAsBorrowP => SoftLoadTE(a, BorrowT)
          case LoadAsWeakP => SoftLoadTE(a, WeakT)
        }
      }
      case BorrowT => {
        loadAsP match {
          case MoveP => vfail()
          case UseP => SoftLoadTE(a, a.result.reference.ownership)
          case LoadAsBorrowP => SoftLoadTE(a, BorrowT)
          case LoadAsWeakP => SoftLoadTE(a, WeakT)
        }
      }
      case WeakT => {
        loadAsP match {
          case UseP => SoftLoadTE(a, WeakT)
          case MoveP => vfail()
          case LoadAsBorrowP => SoftLoadTE(a, WeakT)
          case LoadAsWeakP => SoftLoadTE(a, WeakT)
        }
      }
    }
  }

  def borrowSoftLoad(temputs: Temputs, expr2: AddressExpressionTE):
  ReferenceExpressionTE = {
    val ownership = getBorrowOwnership(temputs, expr2.result.reference.kind)
    SoftLoadTE(expr2, ownership)
  }

  def getBorrowOwnership(temputs: Temputs, kind: KindT):
  OwnershipT = {
    kind match {
      case IntT(_) => ShareT
      case BoolT() => ShareT
      case FloatT() => ShareT
      case StrT() => ShareT
      case VoidT() => ShareT
      case StaticSizedArrayTT(_, mutability, _, _) => {
        if (mutability == MutableT) BorrowT else ShareT
      }
      case RuntimeSizedArrayTT(mutability, _) => {
        if (mutability == MutableT) BorrowT else ShareT
      }
      case sr2 @ StructTT(_) => {
        val mutability = Templar.getMutability(temputs, sr2)
        if (mutability == MutableT) BorrowT else ShareT
      }
      case ir2 @ InterfaceTT(_) => {
        val mutability = Templar.getMutability(temputs, ir2)
        if (mutability == MutableT) BorrowT else ShareT
      }
      case OverloadSetT(_, _) => {
        ShareT
      }
    }
  }
}

object LocalHelper {
  // See ClosureTests for requirements here
  def determineIfLocalIsAddressible(mutability: MutabilityT, localA: LocalS): Boolean = {
    if (mutability == MutableT) {
      localA.childMutated != NotUsed || localA.childMoved != NotUsed
    } else {
      localA.childMutated != NotUsed
    }
  }

  def determineLocalVariability(localA: LocalS): VariabilityT = {
    if (localA.selfMutated != NotUsed || localA.childMutated != NotUsed) {
      VaryingT
    } else {
      FinalT
    }
  }
}