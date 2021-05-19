package net.verdagon.vale.templar.expression

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.DropHelper
import net.verdagon.vale.templar.function.FunctionTemplar.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
import net.verdagon.vale.templar.templata.{TemplataTemplar, _}
import net.verdagon.vale.templar.types._

import scala.collection.immutable.{List, Nil, Set}

case class TookWeakRefOfNonWeakableError() extends Throwable

trait IExpressionTemplarDelegate {
  def evaluateTemplatedFunctionFromCallForPrototype(
    temputs: Temputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata,
    explicitTemplateArgs: List[ITemplata],
    args: List[ParamFilter]):
  IEvaluateFunctionResult[Prototype2]

  def evaluateClosureStruct(
    temputs: Temputs,
    containingFunctionEnv: FunctionEnvironment,
    callRange: RangeS,
    name: LambdaNameA,
    function1: BFunctionA):
  StructRef2
}

class ExpressionTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
  newTemplataStore: () => TemplatasStore,
    templataTemplar: TemplataTemplar,
    inferTemplar: InferTemplar,
    arrayTemplar: ArrayTemplar,
    structTemplar: StructTemplar,
    ancestorHelper: AncestorHelper,
    sequenceTemplar: SequenceTemplar,
    overloadTemplar: OverloadTemplar,
    dropHelper: DropHelper,
    convertHelper: ConvertHelper,
    delegate: IExpressionTemplarDelegate) {
  val localHelper = new LocalHelper(opts, dropHelper)
  val callTemplar = new CallTemplar(opts, templataTemplar, convertHelper, localHelper, overloadTemplar)
  val patternTemplar = new PatternTemplar(opts, profiler, inferTemplar, convertHelper, dropHelper, localHelper)
  val blockTemplar = new BlockTemplar(opts, newTemplataStore, dropHelper, localHelper, new IBlockTemplarDelegate {
    override def evaluateAndCoerceToReferenceExpression(
        temputs: Temputs, fate: FunctionEnvironmentBox, expr1: IExpressionAE):
    (ReferenceExpression2, Set[Coord]) = {
      ExpressionTemplar.this.evaluateAndCoerceToReferenceExpression(temputs, fate, expr1)
    }
  })

  private def evaluateList(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      expr1: List[IExpressionAE]):
      (List[Expression2], Set[Coord]) = {
    expr1 match {
      case Nil => (List(), Set())
      case first1 :: rest1 => {
        val (first2, returnsFromFirst) = evaluate(temputs, fate, first1);
        val (rest2, returnsFromRest) = evaluateList(temputs, fate, rest1);
        (first2 :: rest2, returnsFromFirst ++ returnsFromRest)
      }
    }
  }

  def evaluateAndCoerceToReferenceExpressions(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      exprs1: List[IExpressionAE]):
  (List[ReferenceExpression2], Set[Coord]) = {
    exprs1 match {
      case Nil => (List(), Set())
      case first1 :: rest1 => {
        val (first2, returnsFromFirst) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, first1);
        val (rest2, returnsFromRest) =
          evaluateAndCoerceToReferenceExpressions(temputs, fate, rest1);
        (first2 :: rest2, returnsFromFirst ++ returnsFromRest)
      }
    }
  }

  private def evaluateLookupForLoad(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    name: IVarName2,
    targetOwnership: LoadAsP):
  (Option[Expression2]) = {
    evaluateAddressibleLookup(temputs, fate, range, name) match {
      case Some(x) => {
        val thing = localHelper.softLoad(fate, range, x, targetOwnership)
        (Some(thing))
      }
      case None => {
        fate.getNearestTemplataWithAbsoluteName2(name, Set(TemplataLookupContext)) match {
          case Some(IntegerTemplata(num)) => (Some(IntLiteral2(num)))
          case Some(BooleanTemplata(bool)) => (Some(BoolLiteral2(bool)))
          case None => (None)
          case _ => vwat()
        }
      }
    }
  }

  private def evaluateAddressibleLookupForMutate(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      range: RangeS,
      nameA: IVarNameA):
  Option[AddressExpression2] = {
    fate.getVariable(NameTranslator.translateVarNameStep(nameA)) match {
      case Some(alv @ AddressibleLocalVariable2(_, _, reference)) => {
        Some(LocalLookup2(range, alv, reference, alv.variability))
      }
      case Some(rlv @ ReferenceLocalVariable2(id, _, reference)) => {
        Some(LocalLookup2(range, rlv, reference, rlv.variability))
      }
      case Some(AddressibleClosureVariable2(id, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == Mutable) Constraint else Share
        val closuredVarsStructRefPermission = if (mutability == Mutable) Readwrite else Readonly // See LHRSP
        val closuredVarsStructRefRef = Coord(ownership, closuredVarsStructRefPermission, closuredVarsStructRef)
        val name2 = fate.fullName.addStep(ClosureParamName2())
        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookup2(
              range,
              ReferenceLocalVariable2(name2, Final, closuredVarsStructRefRef),
              closuredVarsStructRefRef,
              Final))

        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == id))

        val index = closuredVarsStructDef.members.indexWhere(_.name == id.last)
//        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership
        val lookup = AddressMemberLookup2(range, borrowExpr, id, tyype, variability)
        Some(lookup)
      }
      case Some(ReferenceClosureVariable2(varName, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == Mutable) Constraint else Share
        val closuredVarsStructRefPermission = if (mutability == Mutable) Readwrite else Readonly // See LHRSP
        val closuredVarsStructRefCoord = Coord(ownership, closuredVarsStructRefPermission, closuredVarsStructRef)
//        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookup2(
              range,
              ReferenceLocalVariable2(fate.fullName.addStep(ClosureParamName2()), Final, closuredVarsStructRefCoord),
              closuredVarsStructRefCoord,
              Final))
//        val index = closuredVarsStructDef.members.indexWhere(_.name == varName)

        val lookup =
          ReferenceMemberLookup2(range, borrowExpr, varName, tyype, tyype.permission, variability)
        Some(lookup)
      }
      case None => None
      case _ => vwat()
    }
  }

  private def evaluateAddressibleLookup(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    name2: IVarName2):
  Option[AddressExpression2] = {
    fate.getVariable(name2) match {
      case Some(alv @ AddressibleLocalVariable2(varId, variability, reference)) => {
        vassert(!fate.unstackifieds.contains(varId))
        Some(LocalLookup2(range, alv, reference, variability))
      }
      case Some(rlv @ ReferenceLocalVariable2(varId, variability, reference)) => {
        if (fate.unstackifieds.contains(varId)) {
          throw CompileErrorExceptionT(CantUseUnstackifiedLocal(range, varId.last))
        }
        Some(LocalLookup2(range, rlv, reference, variability))
      }
      case Some(AddressibleClosureVariable2(id, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == Mutable) Constraint else Share
        val closuredVarsStructRefPermission = if (mutability == Mutable) Readwrite else Readonly // See LHRSP
        val closuredVarsStructRefRef = Coord(ownership, closuredVarsStructRefPermission, closuredVarsStructRef)
        val closureParamVarName2 = fate.fullName.addStep(ClosureParamName2())

        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookup2(
              range,
              ReferenceLocalVariable2(closureParamVarName2, Final, closuredVarsStructRefRef),
              closuredVarsStructRefRef,
              variability))
        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == id))

        val index = closuredVarsStructDef.members.indexWhere(_.name == id.last)
        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership
        val lookup = AddressMemberLookup2(range, borrowExpr, id, tyype, variability)
        Some(lookup)
      }
      case Some(ReferenceClosureVariable2(varName, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == Mutable) Constraint else Share
        val closuredVarsStructRefPermission = if (mutability == Mutable) Readwrite else Readonly // See LHRSP
        val closuredVarsStructRefCoord = Coord(ownership, closuredVarsStructRefPermission, closuredVarsStructRef)
        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == varName))
        val index = closuredVarsStructDef.members.indexWhere(_.name == varName.last)

        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookup2(
              range,
              ReferenceLocalVariable2(fate.fullName.addStep(ClosureParamName2()), Final, closuredVarsStructRefCoord),
              closuredVarsStructRefCoord,
              Final))

//        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership

        val lookup = ReferenceMemberLookup2(range, borrowExpr, varName, tyype, tyype.permission, variability)
        Some(lookup)
      }
      case None => None
      case _ => vwat()
    }
  }

  private def makeClosureStructConstructExpression(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      range: RangeS,
      closureStructRef: StructRef2):
  (ReferenceExpression2) = {
    val closureStructDef = temputs.lookupStruct(closureStructRef);
    // Note, this is where the unordered closuredNames set becomes ordered.
    val lookupExpressions2 =
      closureStructDef.members.map({
        case StructMember2(memberName, variability, tyype) => {
          val lookup =
            evaluateAddressibleLookup(temputs, fate, range, memberName) match {
              case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldn't find " + memberName))
              case Some(l) => l
            }
          tyype match {
            case ReferenceMemberType2(reference) => {
              // We might have to softload an own into a borrow, but the referends
              // should at least be the same right here.
              vassert(reference.referend == lookup.resultRegister.reference.referend)
              // Closures never contain owning references.
              // If we're capturing an own, then on the inside of the closure
              // it's a borrow or a weak. See "Captured own is borrow" test for more.

              vassert(reference.ownership != Own)
              localHelper.borrowSoftLoad(temputs, lookup)
            }
            case AddressMemberType2(reference) => {
              vassert(reference == lookup.resultRegister.reference)
              (lookup)
            }
            case _ => vwat()
          }
        }
      });
    val ownership = if (closureStructDef.mutability == Mutable) Own else Share
    val permission = if (closureStructDef.mutability == Mutable) Readwrite else Readonly
    val resultPointerType = Coord(ownership, permission, closureStructRef)
    val constructExpr2 =
      Construct2(closureStructRef, resultPointerType, lookupExpressions2)
    (constructExpr2)
  }

  def evaluateAndCoerceToReferenceExpression(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      expr1: IExpressionAE):
  (ReferenceExpression2, Set[Coord]) = {
    val (expr2, returnsFromExpr) =
      evaluate(temputs, fate, expr1)
    expr2 match {
      case r : ReferenceExpression2 => {
        (r, returnsFromExpr)
      }
      case a : AddressExpression2 => {
        val expr = coerceToReferenceExpression(fate, a)
        (expr, returnsFromExpr)
      }
      case _ => vwat()
    }
  }

  def coerceToReferenceExpression(fate: FunctionEnvironmentBox, expr2: Expression2):
  (ReferenceExpression2) = {
    expr2 match {
      case r : ReferenceExpression2 => (r)
      case a : AddressExpression2 => localHelper.softLoad(fate, a.range, a, UseP)
    }
  }

  private def evaluateExpectedAddressExpression(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      expr1: IExpressionAE):
  (AddressExpression2, Set[Coord]) = {
    val (expr2, returns) =
      evaluate(temputs, fate, expr1)
    expr2 match {
      case a : AddressExpression2 => (a, returns)
      case _ : ReferenceExpression2 => throw CompileErrorExceptionT(RangedInternalErrorT(expr1.range, "Expected reference expression!"))
    }
  }

  // returns:
  // - resulting expression
  // - all the types that are returned from inside the body via ret
  private def evaluate(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      expr1: IExpressionAE):
  (Expression2, Set[Coord]) = {
    profiler.newProfile(expr1.getClass.getSimpleName, fate.fullName.toString, () => {
      expr1 match {
        case VoidAE(range) => (VoidLiteral2(), Set())
        case IntLiteralAE(range, i) => (IntLiteral2(i), Set())
        case BoolLiteralAE(range, i) => (BoolLiteral2(i), Set())
        case StrLiteralAE(range, s) => (StrLiteral2(s), Set())
        case FloatLiteralAE(range, f) => (FloatLiteral2(f), Set())
        case ArgLookupAE(range, index) => {
          val paramCoordRuneA = fate.function.params(index).pattern.coordRune
          val paramCoordRune = NameTranslator.translateRune(paramCoordRuneA)
          val paramCoordTemplata = fate.getNearestTemplataWithAbsoluteName2(paramCoordRune, Set(TemplataLookupContext)).get
          val CoordTemplata(paramCoord) = paramCoordTemplata
          vassert(fate.functionEnvironment.fullName.last.parameters(index) == paramCoord)
          (ArgLookup2(index, paramCoord), Set())
        }
        case FunctionCallAE(range, TemplateSpecifiedLookupAE(_, name, templateArgTemplexesS, callableTargetOwnership), argsExprs1) => {
          vassert(callableTargetOwnership == LendConstraintP(None))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, argsExprs1)
          val callExpr2 =
            callTemplar.evaluatePrefixCall(
              temputs,
              fate,
              range,
              newGlobalFunctionGroupExpression(fate, GlobalFunctionFamilyNameA(name)),
              templateArgTemplexesS,
              argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallAE(range, TemplateSpecifiedLookupAE(_, name, templateArgTemplexesS, callableTargetOwnership), argsExprs1) => {
          vassert(callableTargetOwnership == LendConstraintP(None))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, argsExprs1)
          val callExpr2 =
            callTemplar.evaluatePrefixCall(
              temputs,
              fate,
              range,
              newGlobalFunctionGroupExpression(fate, GlobalFunctionFamilyNameA(name)),
              templateArgTemplexesS,
              argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallAE(range, TemplateSpecifiedLookupAE(_, name, templateArgTemplexesS, callableTargetOwnership), argsExprs1) => {
          vassert(callableTargetOwnership == LendConstraintP(None))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, argsExprs1)
          val callExpr2 =
            callTemplar.evaluateNamedPrefixCall(temputs, fate, range, GlobalFunctionFamilyNameA(name), templateArgTemplexesS, argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallAE(range, OutsideLoadAE(_, name, callableTargetOwnership), argsPackExpr1) => {
          vassert(callableTargetOwnership == LendConstraintP(None))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, argsPackExpr1)
          val callExpr2 =
            callTemplar.evaluateNamedPrefixCall(temputs, fate, range, GlobalFunctionFamilyNameA(name), List(), argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallAE(range, callableExpr1, argsExprs1) => {
          val (undecayedCallableExpr2, returnsFromCallable) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, callableExpr1);
          val decayedCallableExpr2 =
            localHelper.maybeBorrowSoftLoad(temputs, undecayedCallableExpr2)
          val decayedCallableReferenceExpr2 =
            coerceToReferenceExpression(fate, decayedCallableExpr2)
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, argsExprs1)
          val functionPointerCall2 =
            callTemplar.evaluatePrefixCall(temputs, fate, range, decayedCallableReferenceExpr2, List(), argsExprs2)
          (functionPointerCall2, returnsFromCallable ++ returnsFromArgs)
        }

        case LendAE(range, innerExpr1, loadAsP) => {
          val (innerExpr2, returnsFromInner) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, innerExpr1);
          val resultExpr2 =
            innerExpr2.resultRegister.underlyingReference.ownership match {
              case Own => {
                loadAsP match {
                  case MoveP => {
                    // this can happen if we put a ^ on an owning reference. No harm, let it go.
                    innerExpr2
                  }
                  case LendConstraintP(None) => {
                    localHelper.makeTemporaryLocal(temputs, fate, innerExpr2)
                  }
                  case LendConstraintP(Some(permission)) => {
                    maybeNarrowPermission(range, localHelper.makeTemporaryLocal(temputs, fate, innerExpr2), permission)
                  }
                  case LendWeakP(permission) => {
                    weakAlias(temputs, maybeNarrowPermission(range, localHelper.makeTemporaryLocal(temputs, fate, innerExpr2), permission))
                  }
                  case UseP => vcurious()
                }
              }
              case Constraint => {
                loadAsP match {
                  case MoveP => vcurious() // Can we even coerce to an owning reference?
                  case LendConstraintP(None) => innerExpr2
                  case LendConstraintP(Some(permission)) => maybeNarrowPermission(range, innerExpr2, permission)
                  case LendWeakP(permission) => weakAlias(temputs, maybeNarrowPermission(range, innerExpr2, permission))
                  case UseP => innerExpr2
                }
              }
              case Weak => {
                loadAsP match {
                  case MoveP => vcurious() // Can we even coerce to an owning reference?
                  case LendConstraintP(permission) => vfail() // Need to call lock() to do this
                  case LendWeakP(permission) => maybeNarrowPermission(range, innerExpr2, permission)
                  case UseP => innerExpr2
                }
              }
              case Share => {
                loadAsP match {
                  case MoveP => {
                    // Allow this, we can do ^ on a share ref, itll just give us a share ref.
                    innerExpr2
                  }
                  case LendConstraintP(permission) => {
                    // Allow this, we can do & on a share ref, itll just give us a share ref.
                    innerExpr2
                  }
                  case LendWeakP(permission) => {
                    vfail()
                  }
                  case UseP => innerExpr2
                }
              }
            }
          (resultExpr2, returnsFromInner)
        }
        case LockWeakAE(range, innerExpr1) => {
          val (innerExpr2, returnsFromInner) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, innerExpr1);
          vcheck(innerExpr2.resultRegister.reference.ownership == Weak, "Can only lock a weak")

          val borrowCoord = Coord(Constraint, Readonly, innerExpr2.referend)

          val interfaceTemplata =
            fate.getNearestTemplataWithName(CodeTypeNameA("Opt"), Set(TemplataLookupContext)) match {
              case Some(it@InterfaceTemplata(_, _)) => it
              case _ => vfail()
            }
          val optBorrowInterfaceRef =
            structTemplar.getInterfaceRef(temputs, range, interfaceTemplata, List(CoordTemplata(borrowCoord)))
          val ownOptBorrowCoord = Coord(Own,Readwrite, optBorrowInterfaceRef)

          val someConstructorTemplata =
            fate.getNearestTemplataWithName(GlobalFunctionFamilyNameA("Some"), Set(ExpressionLookupContext)) match {
              case Some(ft@FunctionTemplata(_, _)) => ft
              case _ => vwat();
            }
          val someConstructor =
            delegate.evaluateTemplatedFunctionFromCallForPrototype(
              temputs, range, someConstructorTemplata, List(CoordTemplata(borrowCoord)), List(ParamFilter(borrowCoord, None))) match {
              case seff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, seff.toString))
              case EvaluateFunctionSuccess(p) => p
            }

          val noneConstructorTemplata =
            fate.getNearestTemplataWithName(GlobalFunctionFamilyNameA("None"), Set(ExpressionLookupContext)) match {
              case Some(ft@FunctionTemplata(_, _)) => ft
              case _ => vwat();
            }
          val noneConstructor =
            delegate.evaluateTemplatedFunctionFromCallForPrototype(
              temputs, range, noneConstructorTemplata, List(CoordTemplata(borrowCoord)), List()) match {
              case seff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, seff.toString))
              case EvaluateFunctionSuccess(p) => p
            }

          val resultExpr2 = LockWeak2(innerExpr2, ownOptBorrowCoord, someConstructor, noneConstructor)
          (resultExpr2, returnsFromInner)
        }
        case LocalLoadAE(range, nameA, targetOwnership) => {
          val name = NameTranslator.translateVarNameStep(nameA)
          val lookupExpr1 =
            evaluateLookupForLoad(temputs, fate, range, name, targetOwnership) match {
              case (None) => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldnt find " + name))
              case (Some(x)) => (x)
            }
          (lookupExpr1, Set())
        }
        case OutsideLoadAE(range, name, targetOwnership) => {
          // Note, we don't get here if we're about to call something with this, that's handled
          // by a different case.

          // We can't use *anything* from the global environment; we're in expression context,
          // not in templata context.

          val templataFromEnv =
            fate.getAllTemplatasWithName(profiler, GlobalFunctionFamilyNameA(name), Set(ExpressionLookupContext)) match {
              case List(BooleanTemplata(value)) => BoolLiteral2(value)
              case List(IntegerTemplata(value)) => IntLiteral2(value)
              case templatas if templatas.nonEmpty && templatas.collect({ case FunctionTemplata(_, _) => case ExternFunctionTemplata(_) => }).size == templatas.size => {
                if (targetOwnership == MoveP) {
                  throw CompileErrorExceptionT(CantMoveFromGlobal(range, "Can't move from globals. Name: " + name))
                }
                newGlobalFunctionGroupExpression(fate, GlobalFunctionFamilyNameA(name))
              }
              case things if things.size > 1 => {
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Found too many different things named \"" + name + "\" in env:\n" + things.map("\n" + _)))
              }
              case List() => {
                //              println("members: " + fate.getAllTemplatasWithName(name, Set(ExpressionLookupContext, TemplataLookupContext)))
                throw CompileErrorExceptionT(CouldntFindIdentifierToLoadT(range, name))
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldn't find anything named \"" + name + "\" in env:\n" + fate))
              }
            }
          (templataFromEnv, Set())
        }
        case LocalMutateAE(range, name, sourceExpr1) => {
          val destinationExpr2 =
            evaluateAddressibleLookupForMutate(temputs, fate, range, name) match {
              case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldnt find " + name))
              case Some(x) => x
            }
          val (unconvertedSourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, sourceExpr1)

          if (destinationExpr2.variability != Varying) {
            throw CompileErrorExceptionT(CantMutateFinalLocal(range, name))
          }

          val isConvertible =
            templataTemplar.isTypeConvertible(
              temputs, unconvertedSourceExpr2.resultRegister.reference, destinationExpr2.resultRegister.reference)
          if (!isConvertible) {
            throw CompileErrorExceptionT(
              CouldntConvertForMutateT(
                range, destinationExpr2.resultRegister.reference, unconvertedSourceExpr2.resultRegister.reference))
          }
          vassert(isConvertible)
          val convertedSourceExpr2 =
            convertHelper.convert(fate.snapshot, temputs, range, unconvertedSourceExpr2, destinationExpr2.resultRegister.reference);

          val mutate2 = Mutate2(destinationExpr2, convertedSourceExpr2);
          (mutate2, returnsFromSource)
        }
        case ExprMutateAE(range, destinationExpr1, sourceExpr1) => {
          val (unconvertedSourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, sourceExpr1)
          val (destinationExpr2, returnsFromDestination) =
            evaluateExpectedAddressExpression(temputs, fate, destinationExpr1)
          if (destinationExpr2.variability != Varying) {
            destinationExpr2 match {
              case ReferenceMemberLookup2(range, structExpr, memberName, _, _, _) => {
                structExpr.referend match {
                  case s @ StructRef2(_) => {
                    throw CompileErrorExceptionT(CantMutateFinalMember(range, s.fullName, memberName))
                  }
                  case s @ TupleT2(_, _) => {
                    throw CompileErrorExceptionT(CantMutateFinalMember(range, s.underlyingStruct.fullName, memberName))
                  }
                  case _ => vimpl(structExpr.referend.toString)
                }
              }
              case _ => vimpl()
            }
          }
//          destinationExpr2.resultRegister.reference.permission match {
//            case Readonly => {
//              destinationExpr2 match {
//                case ReferenceMemberLookup2(range, structExpr, memberName, _, _) => {
//                  structExpr.referend match {
//                    case s @ StructRef2(_) => {
//                      throw CompileErrorExceptionT(CantMutateReadonlyMember(range, s, memberName))
//                    }
//                    case _ => vimpl()
//                  }
//                }
//                case _ => vimpl()
//              }
//            }
//            case Readwrite =>
//            case _ => vfail()
//          }

          val isConvertible =
            templataTemplar.isTypeConvertible(temputs, unconvertedSourceExpr2.resultRegister.reference, destinationExpr2.resultRegister.reference)
          if (!isConvertible) {
            throw CompileErrorExceptionT(CouldntConvertForMutateT(range, destinationExpr2.resultRegister.reference, unconvertedSourceExpr2.resultRegister.reference))
          }
          val convertedSourceExpr2 =
            convertHelper.convert(fate.snapshot, temputs, range, unconvertedSourceExpr2, destinationExpr2.resultRegister.reference);

          val mutate2 = Mutate2(destinationExpr2, convertedSourceExpr2);
          (mutate2, returnsFromSource ++ returnsFromDestination)
        }
        case CheckRefCountAE(range, refExpr1, category, numExpr1) => {
          val (refExpr2, returnsFromRef) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, refExpr1);
          val (numExpr2, returnsFromNum) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, numExpr1);
          (CheckRefCount2(refExpr2, Conversions.evaluateRefCountCategory(category), numExpr2), returnsFromRef ++ returnsFromNum)
        }
        case TemplateSpecifiedLookupAE(range, name, templateArgs1, targetOwnership) => {
          // So far, we only allow these when they're immediately called like functions
          vfail("unimplemented")
        }
        case DotCallAE(range, containerExpr1, indexExpr1) => {
          val (unborrowedContainerExpr2, returnsFromContainerExpr) =
            evaluate(temputs, fate, containerExpr1);
          val containerExpr2 =
            dotBorrow(temputs, fate, unborrowedContainerExpr2)

          val (indexExpr2, returnsFromIndexExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, indexExpr1);

          val exprTemplata =
            containerExpr2.resultRegister.reference.referend match {
              case at@UnknownSizeArrayT2(_) => {
                UnknownSizeArrayLookup2(range, containerExpr2, at, indexExpr2, Varying)
              }
              case at@KnownSizeArrayT2(_, _) => {
                ArraySequenceLookup2(
                  range,
                  containerExpr2,
                  at,
                  indexExpr2,
                  Final)
              }
              case at@TupleT2(members, understruct) => {
                indexExpr2 match {
                  case IntLiteral2(index) => {
                    val understructDef = temputs.lookupStruct(understruct);
                    val memberName = understructDef.fullName.addStep(understructDef.members(index).name)
                    val memberType = understructDef.members(index).tyype

                    vassert(understructDef.members.exists(member => understructDef.fullName.addStep(member.name) == memberName))

//                    val ownershipInClosureStruct = understructDef.members(index).tyype.reference.ownership

                    val targetPermission =
                      Templar.intersectPermission(
                        containerExpr2.resultRegister.reference.permission,
                        memberType.reference.permission)

                    ReferenceMemberLookup2(range, containerExpr2, memberName, memberType.reference, targetPermission, Final)
                  }
                  case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Struct random access not implemented yet!"))
                }
              }
              case sr@StructRef2(_) => {
                throw CompileErrorExceptionT(CannotSubscriptT(range, containerExpr2.resultRegister.reference.referend))
              }
              case _ => vwat()
              // later on, a map type could go here
            }
          (exprTemplata, returnsFromContainerExpr ++ returnsFromIndexExpr)
        }
        case DotAE(range, containerExpr1, memberNameStr, borrowContainer) => {
          val memberName = CodeVarName2(memberNameStr)
          val (unborrowedContainerExpr2, returnsFromContainerExpr) =
            evaluate(temputs, fate, containerExpr1);
          val containerExpr2 =
            dotBorrow(temputs, fate, unborrowedContainerExpr2)

          val expr2 =
            containerExpr2.resultRegister.reference.referend match {
              case structRef@StructRef2(_) => {
                val structDef = temputs.lookupStruct(structRef)
                val (structMember, memberIndex) =
                  structDef.getMemberAndIndex(memberName) match {
                    case None => throw CompileErrorExceptionT(CouldntFindMemberT(range, memberName.name))
                    case Some(x) => x
                  }
                val memberFullName = structDef.fullName.addStep(structDef.members(memberIndex).name)
                val memberType = structMember.tyype.expectReferenceMember().reference;


                vassert(structDef.members.exists(member => structDef.fullName.addStep(member.name) == memberFullName))

                val index = structDef.members.indexWhere(_.name == memberFullName.last)
//                val ownershipInClosureStruct = structDef.members(index).tyype.reference.ownership

                val effectiveVariability =
                  (containerExpr2.resultRegister.reference.permission, structMember.variability) match {
                    case (Readonly, Final) => Final
                    case (Readwrite, Final) => Final
                    case (Readonly, Varying) => Final
                    case (Readwrite, Varying) => Varying
                  }

                val targetPermission =
                  Templar.intersectPermission(
                    containerExpr2.resultRegister.reference.permission,
                    memberType.permission)

                ReferenceMemberLookup2(range, containerExpr2, memberFullName, memberType, targetPermission, effectiveVariability)
              }
              case TupleT2(_, structRef) => {
                temputs.lookupStruct(structRef) match {
                  case structDef@StructDefinition2(_, _, _, _, _, _) => {
                    val (structMember, memberIndex) = vassertSome(structDef.getMemberAndIndex(memberName))
                    val memberFullName = structDef.fullName.addStep(structDef.members(memberIndex).name)
                    val memberType = structMember.tyype.expectReferenceMember().reference;


                    vassert(structDef.members.exists(member => structDef.fullName.addStep(member.name) == memberFullName))

                    val index = structDef.members.indexWhere(_.name == memberFullName.last)
                    val ownershipInClosureStruct = structDef.members(index).tyype.reference.ownership

                    val targetPermission =
                      Templar.intersectPermission(
                        containerExpr2.resultRegister.reference.permission,
                        memberType.permission)

                    ReferenceMemberLookup2(range, containerExpr2, memberFullName, memberType, targetPermission, structMember.variability)
                  }
                }
              }
              case as@KnownSizeArrayT2(_, _) => {
                if (memberNameStr.forall(Character.isDigit)) {
                  ArraySequenceLookup2(
                    range,
                    containerExpr2,
                    as,
                    IntLiteral2(memberNameStr.toInt),
                    Final)
                } else {
                  throw CompileErrorExceptionT(RangedInternalErrorT(range, "Sequence has no member named " + memberNameStr))
                }
              }
              case at@UnknownSizeArrayT2(_) => {
                if (memberNameStr.forall(Character.isDigit)) {
                  UnknownSizeArrayLookup2(
                    range,
                    containerExpr2,
                    at,
                    IntLiteral2(memberNameStr.toInt),
                    Varying)
                } else {
                  throw CompileErrorExceptionT(RangedInternalErrorT(range, "Array has no member named " + memberNameStr))
                }
              }
              case other => {
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't apply ." + memberNameStr + " to " + other))
              }
            }

          (expr2, returnsFromContainerExpr)
        }
        case FunctionAE(name, function1@FunctionA(range, _, _, _, _, _, _, _, _, _, _, CodeBodyA(body))) => {
          val callExpr2 = evaluateClosure(temputs, fate, range, name, BFunctionA(function1, body))
          (callExpr2, Set())
        }
        case TupleAE(range, elements1) => {
          val (exprs2, returnsFromElements) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, elements1);

          // would we need a sequence templata? probably right?
          val expr2 =
            sequenceTemplar.evaluate(fate, temputs, exprs2)
          (expr2, returnsFromElements)
        }
        case StaticArrayFromValuesAE(range, rules, typeByRune, sizeRuneA, maybeMutabilityRune, maybeVariabilityRune, elements1) => {
          val (exprs2, returnsFromElements) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, elements1);
          // would we need a sequence templata? probably right?
          val expr2 =
            arrayTemplar.evaluateStaticSizedArrayFromValues(
              temputs, fate, range, rules, typeByRune, sizeRuneA, maybeMutabilityRune, maybeVariabilityRune, exprs2)
          (expr2, returnsFromElements)
        }
        case StaticArrayFromCallableAE(range, rules, typeByRune, sizeRuneA, maybeMutabilityRune, maybeVariabilityRune, callableAE) => {
          val (callableTE, returnsFromCallable) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, callableAE);
          val expr2 =
            arrayTemplar.evaluateStaticSizedArrayFromCallable(
              temputs, fate, range, rules, typeByRune, sizeRuneA, maybeMutabilityRune, maybeVariabilityRune, callableTE)
          (expr2, returnsFromCallable)
        }
        case ConstructArrayAE(range, mutabilityTemplex, elementCoordTemplex, generatorPrototypeTemplex, sizeExpr1, generatorExpr1) => {
          val (MutabilityTemplata(arrayMutability)) = templataTemplar.evaluateTemplex(fate.snapshot, temputs, mutabilityTemplex)
          val (CoordTemplata(elementCoord)) = templataTemplar.evaluateTemplex(fate.snapshot, temputs, elementCoordTemplex)
          val (PrototypeTemplata(generatorPrototype)) = templataTemplar.evaluateTemplex(fate.snapshot, temputs, generatorPrototypeTemplex)

          val (sizeExpr2, returnsFromSize) =
            evaluate(temputs, fate, sizeExpr1);

          val (generatorExpr2, returnsFromGenerator) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, generatorExpr1);

//          val memberType2 =
//            generatorExpr2.referend match {
//              case InterfaceRef2(FullName2(List(), CitizenName2("IFunction1", List(MutabilityTemplata(_), CoordTemplata(Coord(Share, Readonly, Int2())), CoordTemplata(element))))) => element
//              case other => vwat(other.toString)
//            }

//          val isConvertible =
//            templataTemplar.isTypeTriviallyConvertible(temputs, memberType2, elementCoord)
//          if (!isConvertible) {
//            throw CompileErrorExceptionT(RangedInternalErrorT(range, memberType2 + " cant convert to " + elementCoord))
//          }

          if (generatorPrototype.returnType != elementCoord) {
            throw CompileErrorExceptionT(RangedInternalErrorT(range, "Generator return type doesn't agree with array element type!"))
          }
          if (generatorPrototype.paramTypes.size != 2) {
            throw CompileErrorExceptionT(RangedInternalErrorT(range, "Generator must take in 2 args!"))
          }
          if (generatorPrototype.paramTypes(0) != generatorExpr2.resultRegister.reference) {
            throw CompileErrorExceptionT(RangedInternalErrorT(range, "Generator first param doesn't agree with generator expression's result!"))
          }
          if (generatorPrototype.paramTypes(1) != Coord(Share, Readonly, Int2())) {
            throw CompileErrorExceptionT(RangedInternalErrorT(range, "Generator must take in an integer as its second param!"))
          }
          if (arrayMutability == Immutable &&
            Templar.getMutability(temputs, elementCoord.referend) == Mutable) {
            throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't have an immutable array of mutable elements!"))
          }
          val arrayType = arrayTemplar.makeUnknownSizeArrayType(fate.snapshot, temputs, elementCoord, arrayMutability)

          val sizeRefExpr2 = coerceToReferenceExpression(fate, sizeExpr2)
          vassert(sizeRefExpr2.resultRegister.expectReference().reference == Coord(Share, Readonly, Int2()))

          val generatorMethod2 = generatorPrototype
          val constructExpr2 =
            ConstructArray2(
              arrayType,
              sizeRefExpr2,
              generatorExpr2,
              generatorMethod2)
          (constructExpr2, returnsFromSize ++ returnsFromGenerator)
        }
        case LetAE(range, rulesA, typeByRune, localRunesA, pattern, sourceExpr1) => {
          val (sourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, sourceExpr1)

          val fateSnapshot = fate.snapshot
          val lets2 =
            patternTemplar.nonCheckingInferAndTranslate(
              temputs, fate, rulesA, typeByRune, localRunesA, pattern, sourceExpr2)

          val resultExprBlock2 = Consecutor2(lets2)

          (resultExprBlock2, returnsFromSource)
        }
        case RuneLookupAE(range, runeA, tyype) => {
          val templata = vassertSome(fate.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(runeA), Set(TemplataLookupContext)))
          (tyype, templata) match {
            case (IntegerTemplataType, IntegerTemplata(value)) => (IntLiteral2(value), Set())
          }
        }
        case IfAE(range, conditionSE, thenBody1, elseBody1) => {
          // We make a block for the if-statement which contains its condition (the "if block"),
          // and then two child blocks under that for the then and else blocks.
          // The then and else blocks are children of the block which contains the condition
          // so they can access any locals declared by the condition.

          val ifBlockFate = fate.makeChildEnvironment(newTemplataStore)

          val (conditionExpr, returnsFromCondition) =
            evaluateAndCoerceToReferenceExpression(temputs, ifBlockFate, conditionSE)
          if (conditionExpr.resultRegister.reference != Coord(Share, Readonly, Bool2())) {
            throw CompileErrorExceptionT(IfConditionIsntBoolean(conditionSE.range, conditionExpr.resultRegister.reference))
          }


          val thenFate = ifBlockFate.makeChildEnvironment(newTemplataStore)

          val (thenExpressionsWithResult, thenReturnsFromExprs) =
            evaluateBlockStatements(temputs, thenFate.snapshot, thenFate, thenBody1.exprs)
          val uncoercedThenBlock2 = Block2(thenExpressionsWithResult)

          val (thenUnstackifiedAncestorLocals, thenVarCountersUsed) = thenFate.getEffects()
          val thenContinues = uncoercedThenBlock2.resultRegister.reference.referend != Never2()

          ifBlockFate.nextCounters(thenVarCountersUsed)


          val elseFate = ifBlockFate.makeChildEnvironment(newTemplataStore)

          val (elseExpressionsWithResult, elseReturnsFromExprs) =
            evaluateBlockStatements(temputs, elseFate.snapshot, elseFate, elseBody1.exprs)
          val uncoercedElseBlock2 = Block2(elseExpressionsWithResult)

          val (elseUnstackifiedAncestorLocals, elseVarCountersUsed) = elseFate.getEffects()
          val elseContinues = uncoercedElseBlock2.resultRegister.reference.referend != Never2()

          ifBlockFate.nextCounters(elseVarCountersUsed)


          val commonType =
            (uncoercedThenBlock2.referend, uncoercedElseBlock2.referend) match {
              case (Never2(), Never2()) => uncoercedThenBlock2.resultRegister.reference
              case (Never2(), _) => uncoercedElseBlock2.resultRegister.reference
              case (_, Never2()) => uncoercedThenBlock2.resultRegister.reference
              case (a, b) if a == b => uncoercedThenBlock2.resultRegister.reference
              case (a : CitizenRef2, b : CitizenRef2) => {
                val aAncestors = ancestorHelper.getAncestorInterfacesWithDistance(temputs, a).keys.toSet
                val bAncestors = ancestorHelper.getAncestorInterfacesWithDistance(temputs, b).keys.toSet
                val commonAncestors = aAncestors.intersect(bAncestors)

                if (uncoercedElseBlock2.resultRegister.reference.ownership != uncoercedElseBlock2.resultRegister.reference.ownership) {
                  throw CompileErrorExceptionT(RangedInternalErrorT(range, "Two branches of if have different ownerships!\\n${a}\\n${b}"))
                }
                val ownership = uncoercedElseBlock2.resultRegister.reference.ownership
                val permission = uncoercedElseBlock2.resultRegister.reference.permission

                if (commonAncestors.isEmpty) {
                  vimpl(s"No common ancestors of two branches of if:\n${a}\n${b}")
                } else if (commonAncestors.size > 1) {
                  vimpl(s"More than one common ancestor of two branches of if:\n${a}\n${b}")
                } else {
                  Coord(ownership, permission, commonAncestors.head)
                }
              }
              case (a, b) => {
                vimpl(s"Couldnt reconcile branches of if:\n${a}\n${b}")
              }
            }
          val thenExpr2 = convertHelper.convert(thenFate.snapshot, temputs, range, uncoercedThenBlock2, commonType)
          val elseExpr2 = convertHelper.convert(elseFate.snapshot, temputs, range, uncoercedElseBlock2, commonType)

          val ifExpr2 = If2(conditionExpr, thenExpr2, elseExpr2)


          if (thenContinues == elseContinues) { // Both continue, or both don't
            // Each branch might have moved some things. Make sure they moved the same things.
            if (thenUnstackifiedAncestorLocals != elseUnstackifiedAncestorLocals) {
              throw CompileErrorExceptionT(RangedInternalErrorT(range, "Must move same variables from inside branches!\nFrom then branch: " + thenUnstackifiedAncestorLocals + "\nFrom else branch: " + elseUnstackifiedAncestorLocals))
            }
            thenUnstackifiedAncestorLocals.foreach(ifBlockFate.markLocalUnstackified)
          } else {
            // One of them continues and the other does not.
            if (thenContinues) {
              thenUnstackifiedAncestorLocals.foreach(ifBlockFate.markLocalUnstackified)
            } else if (elseContinues) {
              elseUnstackifiedAncestorLocals.foreach(ifBlockFate.markLocalUnstackified)
            } else vfail()
          }


          val (ifBlockUnstackifiedAncestorLocals, ifBlockVarCountersUsed) = ifBlockFate.getEffects()
          fate.nextCounters(ifBlockVarCountersUsed)
          ifBlockUnstackifiedAncestorLocals.foreach(fate.markLocalUnstackified)


          (ifExpr2, returnsFromCondition ++ thenReturnsFromExprs ++ elseReturnsFromExprs)
        }
        case WhileAE(range, conditionSE, body1) => {
          // We make a block for the while-statement which contains its condition (the "if block"),
          // and the body block, so they can access any locals declared by the condition.

          val whileBlockFate = fate.makeChildEnvironment(newTemplataStore)

          val (conditionExpr, returnsFromCondition) =
            evaluateAndCoerceToReferenceExpression(temputs, whileBlockFate, conditionSE)
          if (conditionExpr.resultRegister.reference != Coord(Share, Readonly, Bool2())) {
            throw CompileErrorExceptionT(WhileConditionIsntBoolean(conditionSE.range, conditionExpr.resultRegister.reference))
          }


          val (bodyExpressionsWithResult, bodyReturnsFromExprs) =
            evaluateBlockStatements(temputs, whileBlockFate.snapshot, whileBlockFate, List(body1))
          val uncoercedBodyBlock2 = Block2(bodyExpressionsWithResult)

          val bodyContinues = uncoercedBodyBlock2.resultRegister.reference.referend != Never2()


          val (bodyUnstackifiedAncestorLocals, bodyVarCountersUsed) = whileBlockFate.getEffects()
          if (bodyUnstackifiedAncestorLocals.nonEmpty) {
            throw CompileErrorExceptionT(CantUnstackifyOutsideLocalFromInsideWhile(range, bodyUnstackifiedAncestorLocals.head.last))
          }
          whileBlockFate.nextCounters(bodyVarCountersUsed)


          val thenBody =
            if (uncoercedBodyBlock2.referend == Never2()) {
              uncoercedBodyBlock2
            } else {
              Block2(List(uncoercedBodyBlock2, BoolLiteral2(true)))
            }

          val ifExpr2 =
            If2(
              conditionExpr,
              thenBody,
              Block2(List(BoolLiteral2(false))))
          val whileExpr2 = While2(Block2(List(ifExpr2)))
          (whileExpr2, returnsFromCondition ++ bodyReturnsFromExprs)
        }
        case BlockAE(range, blockExprs) => {
          val childEnvironment = fate.makeChildEnvironment(newTemplataStore)

          val (expressionsWithResult, returnsFromExprs) =
            evaluateBlockStatements(temputs, childEnvironment.functionEnvironment, childEnvironment, blockExprs)
          val block2 = Block2(expressionsWithResult)

          val (unstackifiedAncestorLocals, varCountersUsed) = childEnvironment.getEffects()
          unstackifiedAncestorLocals.foreach(fate.markLocalUnstackified)
          fate.nextCounters(varCountersUsed)

          (block2, returnsFromExprs)
        }
        case ArrayLengthAE(range, arrayExprA) => {
          val (arrayExpr2, returnsFromArrayExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, arrayExprA);
          (ArrayLength2(arrayExpr2), returnsFromArrayExpr)
        }
        case DestructAE(range, innerAE) => {
          val (innerExpr2, returnsFromArrayExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, innerAE);

          // should just ignore others, TODO impl
          vcheck(innerExpr2.resultRegister.reference.ownership == Own, "can only destruct own")

          val destroy2 =
            innerExpr2.referend match {
              case structRef@StructRef2(_) => {
                val structDef = temputs.lookupStruct(structRef)
                Destroy2(
                  innerExpr2,
                  structRef,
                  structDef.members.map(_.tyype).map({ case ReferenceMemberType2(reference) =>
                    val rlv = localHelper.makeTemporaryLocal(temputs, fate, reference)
                    rlv
                  case _ => vfail()
                  }))
              }
              case interfaceRef @ InterfaceRef2(_) => {
                dropHelper.drop(fate, temputs, innerExpr2)
              }
              case _ => vfail("Can't destruct type: " + innerExpr2.referend)
            }
          (destroy2, returnsFromArrayExpr)
        }
        case ReturnAE(range, innerExprA) => {
          val (uncastedInnerExpr2, returnsFromInnerExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, innerExprA);

          val innerExpr2 =
            fate.maybeReturnType match {
              case None => (uncastedInnerExpr2)
              case Some(returnType) => {
                templataTemplar.isTypeConvertible(temputs, uncastedInnerExpr2.resultRegister.reference, returnType) match {
                  case (false) => {
                    throw CompileErrorExceptionT(
                      CouldntConvertForReturnT(range, returnType, uncastedInnerExpr2.resultRegister.reference))
                  }
                  case (true) => {
                    convertHelper.convert(fate.snapshot, temputs, range, uncastedInnerExpr2, returnType)
                  }
                }
              }
            }

          val allLocals = fate.getAllLocals(true)
          val unstackifiedLocals = fate.getAllUnstackifiedLocals(true)
          val variablesToDestruct = allLocals.filter(x => !unstackifiedLocals.contains(x.id))
          val reversedVariablesToDestruct = variablesToDestruct.reverse

          val resultVarId = fate.fullName.addStep(TemplarFunctionResultVarName2())
          val resultVariable = ReferenceLocalVariable2(resultVarId, Final, innerExpr2.resultRegister.reference)
          val resultLet = LetNormal2(resultVariable, innerExpr2)
          fate.addVariable(resultVariable)

          val destructExprs =
            localHelper.unletAll(temputs, fate, reversedVariablesToDestruct)

          val getResultExpr =
            localHelper.unletLocal(fate, resultVariable)

          val consecutor = Consecutor2(List(resultLet) ++ destructExprs ++ List(getResultExpr))

          val returns = returnsFromInnerExpr + innerExpr2.resultRegister.reference

          (Return2(consecutor), returns)
        }
        case _ => {
          println(expr1)
          vfail(expr1.toString)
        }
      }
    })
  }

  private def maybeNarrowPermission(range: RangeS, innerExpr2: ReferenceExpression2, permission: PermissionP) = {
    (innerExpr2.resultRegister.reference.permission, permission) match {
      case (Readonly, ReadonlyP) => innerExpr2
      case (Readwrite, ReadonlyP) => NarrowPermission2(innerExpr2, Readonly)
      case (Readonly, ReadwriteP) => {
        throw CompileErrorExceptionT(CantUseReadonlyReferenceAsReadwrite(range))
      }
      case (Readwrite, ReadwriteP) => innerExpr2
    }
  }

  def weakAlias(temputs: Temputs, expr: ReferenceExpression2): ReferenceExpression2 = {
    expr.referend match {
      case sr @ StructRef2(_) => {
        val structDef = temputs.lookupStruct(sr)
        vcheck(structDef.weakable, TookWeakRefOfNonWeakableError)
      }
      case ir @ InterfaceRef2(_) => {
        val interfaceDef = temputs.lookupInterface(ir)
        vcheck(interfaceDef.weakable, TookWeakRefOfNonWeakableError)
      }
      case _ => vfail()
    }

    WeakAlias2(expr)
  }

  private def decaySoloPack(fate: FunctionEnvironmentBox, refExpr: ReferenceExpression2):
  (ReferenceExpression2) = {
    refExpr.resultRegister.reference.referend match {
      case PackT2(List(onlyMember), understruct) => {
        val varNameCounter = fate.nextVarCounter()
        val varId = fate.fullName.addStep(TemplarTemporaryVarName2(varNameCounter))
        val localVar = ReferenceLocalVariable2(varId, Final, onlyMember)
        val destroy2 = Destroy2(refExpr, understruct, List(localVar))
        val unletExpr = localHelper.unletLocal(fate, localVar)
        (Consecutor2(List(destroy2, unletExpr)))
      }
      case _ => (refExpr)
    }
  }

  // Borrow like the . does. If it receives an owning reference, itll make a temporary.
  // If it receives an owning address, that's fine, just borrowsoftload from it.
  // Rename this someday.
  private def dotBorrow(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      undecayedUnborrowedContainerExpr2: Expression2):
  (ReferenceExpression2) = {
    undecayedUnborrowedContainerExpr2 match {
      case a: AddressExpression2 => {
        (localHelper.borrowSoftLoad(temputs, a))
      }
      case r: ReferenceExpression2 => {
        val unborrowedContainerExpr2 = decaySoloPack(fate, r)
        unborrowedContainerExpr2.resultRegister.reference.ownership match {
          case Own => localHelper.makeTemporaryLocal(temputs, fate, unborrowedContainerExpr2)
          case Constraint | Share => (unborrowedContainerExpr2)
        }
      }
    }
  }

  // Given a function1, this will give a closure (an OrdinaryClosure2 or a TemplatedClosure2)
  // returns:
  // - temputs
  // - resulting templata
  // - exported things (from let)
  // - hoistees; expressions to hoist (like initializing blocks)
  def evaluateClosure(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      range: RangeS,
      name: LambdaNameA,
      function1: BFunctionA):
  (ReferenceExpression2) = {

    val closureStructRef2 =
      delegate.evaluateClosureStruct(temputs, fate.snapshot, range, name, function1);
    val closureCoord =
      templataTemplar.pointifyReferend(temputs, closureStructRef2, Own)

    val constructExpr2 = makeClosureStructConstructExpression(temputs, fate, function1.origin.range, closureStructRef2)
    vassert(constructExpr2.resultRegister.reference == closureCoord)
    // The result of a constructor is always an own or a share.

    // The below code was here, but i see no reason we need to put it in a temporary and lend it out.
    // shouldnt this be done automatically if we try to call the function which accepts a borrow?
//    val closureVarId = FullName2(fate.lambdaNumber, "__closure_" + function1.origin.lambdaNumber)
//    val closureLocalVar = ReferenceLocalVariable2(closureVarId, Final, resultExpr2.resultRegister.reference)
//    val letExpr2 = LetAndLend2(closureLocalVar, resultExpr2)
//    val unlet2 = localHelper.unletLocal(fate, closureLocalVar)
//    val dropExpr =
//      DestructorTemplar.drop(env, temputs, fate, unlet2)
//    val deferExpr2 = Defer2(letExpr2, dropExpr)
//    (temputs, fate, deferExpr2)

    constructExpr2
  }

  private def newGlobalFunctionGroupExpression(env: IEnvironmentBox, name: GlobalFunctionFamilyNameA): ReferenceExpression2 = {
    TemplarReinterpret2(
      Program2.emptyPackExpression,
      Coord(
        Share,
        Readonly,
        OverloadSet(
          env.snapshot,
          name,
          Program2.emptyTupleStructRef)))
  }

  def evaluateBlockStatements(
    temputs: Temputs, startingFate: FunctionEnvironment, fate: FunctionEnvironmentBox, exprs: List[IExpressionAE]):
  (List[ReferenceExpression2], Set[Coord]) = {
    blockTemplar.evaluateBlockStatements(temputs, startingFate, fate, exprs)
  }

  def nonCheckingTranslateList(temputs: Temputs, fate: FunctionEnvironmentBox, patterns1: List[AtomAP], patternInputExprs2: List[ReferenceExpression2]): List[ReferenceExpression2] = {
    patternTemplar.nonCheckingTranslateList(temputs, fate, patterns1, patternInputExprs2)
  }
}
