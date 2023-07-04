package dev.vale.typing.expression

import dev.vale.{Interner, RangeS, vassert, vfail, vimpl}
import dev.vale.parsing.ast.{LoadAsBorrowP, LoadAsP, LoadAsWeakP, MoveP, UseP}
import dev.vale.postparsing._
import dev.vale.typing.{CantMoveOutOfMemberT, CompileErrorExceptionT, Compiler, CompilerOutputs, RangedInternalErrorT, TypingPassOptions, ast, env}
import dev.vale.typing.ast.{AddressExpressionTE, AddressMemberLookupTE, DeferTE, ExpressionT, LetAndLendTE, LocalLookupTE, LocationInFunctionEnvironmentT, ReferenceExpressionTE, ReferenceMemberLookupTE, RuntimeSizedArrayLookupTE, SoftLoadTE, StaticSizedArrayLookupTE, UnletTE}
import dev.vale.typing.env.{AddressibleLocalVariableT, ILocalVariableT, NodeEnvironmentBox, ReferenceLocalVariableT}
import dev.vale.typing.function.DestructorCompiler
import dev.vale.typing.names.{NameTranslator, TypingPassTemporaryVarNameT}
import dev.vale.typing.templata.{Conversions, ITemplataT, MutabilityTemplataT, PlaceholderTemplataT}
import dev.vale.typing.types._
import dev.vale.parsing._
import dev.vale.parsing.ast._
import dev.vale.postparsing.LocalS
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.types._
import dev.vale.typing.{ast, _}
import dev.vale.typing.ast._
import dev.vale.typing.names.TypingPassTemporaryVarNameT

import scala.collection.immutable.List

class LocalHelper(
    opts: TypingPassOptions,
    interner: Interner,
    nameTranslator: NameTranslator,
    destructorCompiler: DestructorCompiler) {

  def makeTemporaryLocal(
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    coord: CoordT):
  ReferenceLocalVariableT = {
    val varId = interner.intern(TypingPassTemporaryVarNameT(life))
    val rlv = ReferenceLocalVariableT(varId, FinalT, coord)
    nenv.addVariable(rlv)
    rlv
  }

  // This makes a borrow ref, but can easily turn that into a weak
  // separately.
  def makeTemporaryLocal(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    range: List[RangeS],
    life: LocationInFunctionEnvironmentT,
    r: ReferenceExpressionTE,
    targetOwnership: OwnershipT):
  (DeferTE) = {
    targetOwnership match {
      case BorrowT =>
    }

    val rlv = makeTemporaryLocal(nenv, life, r.result.coord)
    val letExpr2 = LetAndLendTE(rlv, r, targetOwnership)

    val unlet = unletLocalWithoutDropping(nenv, rlv)
    val destructExpr2 =
      destructorCompiler.drop(nenv.snapshot, coutputs, range, unlet)
    vassert(destructExpr2.kind == VoidT())

    // No Discard here because the destructor already returns void.

    (DeferTE(letExpr2, destructExpr2))
  }

  def unletLocalWithoutDropping(nenv: NodeEnvironmentBox, localVar: ILocalVariableT):
  (UnletTE) = {
    nenv.markLocalUnstackified(localVar.name)
    UnletTE(localVar)
  }

  def unletAndDropAll(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    range: List[RangeS],
    variables: Vector[ILocalVariableT]):
  (Vector[ReferenceExpressionTE]) = {
    variables.map({ case variable =>
      val unlet = unletLocalWithoutDropping(nenv, variable)
      val maybeHeadExpr2 =
        destructorCompiler.drop(nenv.snapshot, coutputs, range, unlet)
      maybeHeadExpr2
    })
  }

  def unletAllWithoutDropping(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    range: List[RangeS],
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
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    range: List[RangeS],
    localVariableA: LocalS,
    referenceType2: CoordT):
  ILocalVariableT = {
    val varId = nameTranslator.translateVarNameStep(localVariableA.varName)

    if (nenv.getVariable(varId).nonEmpty) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "There's already a variable named " + varId))
    }

    val variability = LocalHelper.determineLocalVariability(localVariableA)

    val mutable = Compiler.getMutability(coutputs, referenceType2.kind)
    val addressible = LocalHelper.determineIfLocalIsAddressible(mutable, localVariableA)

    val localVar =
      if (addressible) {
        AddressibleLocalVariableT(varId, variability, referenceType2)
      } else {
        env.ReferenceLocalVariableT(varId, variability, referenceType2)
      }
    nenv.addVariable(localVar)
    localVar
  }

  def maybeBorrowSoftLoad(
      coutputs: CompilerOutputs,
      expr2: ExpressionT):
  ReferenceExpressionTE = {
    expr2 match {
      case e : ReferenceExpressionTE => e
      case e : AddressExpressionTE => borrowSoftLoad(coutputs, e)
    }
  }

  def softLoad(
      nenv: NodeEnvironmentBox,
      loadRange: List[RangeS],
      a: AddressExpressionTE,
      loadAsP: LoadAsP):
  ReferenceExpressionTE = {
    a.result.coord.ownership match {
      case ShareT => {
        SoftLoadTE(a, ShareT)
      }
      case OwnT => {
        loadAsP match {
          case UseP => {
            a match {
              case LocalLookupTE(_, lv) => {
                nenv.markLocalUnstackified(lv.name)
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
                nenv.markLocalUnstackified(lv.name)
                UnletTE(lv)
              }
              case ReferenceMemberLookupTE(_,_, name, _, _) => {
                throw CompileErrorExceptionT(CantMoveOutOfMemberT(loadRange, name))
              }
              case AddressMemberLookupTE(_, _, name, _, _) => {
                throw CompileErrorExceptionT(CantMoveOutOfMemberT(loadRange, name))
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
          case UseP => SoftLoadTE(a, a.result.coord.ownership)
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

  def borrowSoftLoad(coutputs: CompilerOutputs, expr2: AddressExpressionTE):
  ReferenceExpressionTE = {
    val ownership = getBorrowOwnership(coutputs, expr2.result.coord.kind)
    ast.SoftLoadTE(expr2, ownership)
  }

  def getBorrowOwnership(coutputs: CompilerOutputs, kind: KindT):
  OwnershipT = {
    kind match {
      case IntT(_) => ShareT
      case BoolT() => ShareT
      case FloatT() => ShareT
      case StrT() => ShareT
      case VoidT() => ShareT
      case contentsStaticSizedArrayTT(_, mutability, _, _) => {
        mutability match {
          case MutabilityTemplataT(MutableT) => BorrowT
          case MutabilityTemplataT(ImmutableT) => ShareT
          case PlaceholderTemplataT(idT, MutabilityTemplataType()) => BorrowT
        }
      }
      case contentsRuntimeSizedArrayTT(mutability, _) => {
        mutability match {
          case MutabilityTemplataT(MutableT) => BorrowT
          case MutabilityTemplataT(ImmutableT) => ShareT
          case PlaceholderTemplataT(idT, MutabilityTemplataType()) => BorrowT
        }
      }
      case p @ KindPlaceholderT(id) => {
        val mutability = Compiler.getMutability(coutputs, p)
        mutability match {
          case MutabilityTemplataT(MutableT) => BorrowT
          case MutabilityTemplataT(ImmutableT) => ShareT
          case PlaceholderTemplataT(idT, MutabilityTemplataType()) => BorrowT
        }
      }
      case sr2 @ StructTT(_) => {
        val mutability = Compiler.getMutability(coutputs, sr2)
        mutability match {
          case MutabilityTemplataT(MutableT) => BorrowT
          case MutabilityTemplataT(ImmutableT) => ShareT
          case PlaceholderTemplataT(idT, MutabilityTemplataType()) => BorrowT
        }
      }
      case ir2 @ InterfaceTT(_) => {
        val mutability = Compiler.getMutability(coutputs, ir2)
        mutability match {
          case MutabilityTemplataT(MutableT) => BorrowT
          case MutabilityTemplataT(ImmutableT) => ShareT
          case PlaceholderTemplataT(idT, MutabilityTemplataType()) => BorrowT
        }
      }
      case OverloadSetT(_, _) => {
        ShareT
      }
    }
  }
}

object LocalHelper {
  // See ClosureTests for requirements here
  def determineIfLocalIsAddressible(mutability: ITemplataT[MutabilityTemplataType], localA: LocalS): Boolean = {
    mutability match {
      case MutabilityTemplataT(MutableT) => {
        localA.childMutated != NotUsed || localA.childMoved != NotUsed
      }
      case _ => {
        localA.childMutated != NotUsed
      }
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