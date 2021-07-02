package net.verdagon.vale.templar.expression

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.DestructorTemplar
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
  IEvaluateFunctionResult[PrototypeT]

  def evaluateClosureStruct(
    temputs: Temputs,
    containingFunctionEnv: FunctionEnvironment,
    callRange: RangeS,
    name: LambdaNameA,
    function1: BFunctionA):
  StructRefT
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
    destructorTemplar: DestructorTemplar,
    convertHelper: ConvertHelper,
    delegate: IExpressionTemplarDelegate) {
  val localHelper = new LocalHelper(opts, destructorTemplar)
  val callTemplar = new CallTemplar(opts, templataTemplar, convertHelper, localHelper, overloadTemplar)
  val patternTemplar = new PatternTemplar(opts, profiler, inferTemplar, arrayTemplar, convertHelper, destructorTemplar, localHelper)
  val blockTemplar = new BlockTemplar(opts, newTemplataStore, destructorTemplar, localHelper, new IBlockTemplarDelegate {
    override def evaluateAndCoerceToReferenceExpression(
        temputs: Temputs, fate: FunctionEnvironmentBox, expr1: IExpressionAE):
    (ReferenceExpressionTE, Set[CoordT]) = {
      ExpressionTemplar.this.evaluateAndCoerceToReferenceExpression(temputs, fate, expr1)
    }
  })

  def evaluateAndCoerceToReferenceExpressions(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      exprs1: List[IExpressionAE]):
  (List[ReferenceExpressionTE], Set[CoordT]) = {
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
    name: IVarNameT,
    targetOwnership: LoadAsP):
  (Option[ExpressionT]) = {
    evaluateAddressibleLookup(temputs, fate, range, name) match {
      case Some(x) => {
        val thing = localHelper.softLoad(fate, range, x, targetOwnership)
        (Some(thing))
      }
      case None => {
        fate.getNearestTemplataWithAbsoluteName2(name, Set(TemplataLookupContext)) match {
          case Some(IntegerTemplata(num)) => (Some(ConstantIntTE(num, 32)))
          case Some(BooleanTemplata(bool)) => (Some(ConstantBoolTE(bool)))
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
  Option[AddressExpressionT] = {
    fate.getVariable(NameTranslator.translateVarNameStep(nameA)) match {
      case Some(alv @ AddressibleLocalVariableT(_, _, reference)) => {
        Some(LocalLookupT(range, alv, reference, alv.variability))
      }
      case Some(rlv @ ReferenceLocalVariableT(id, _, reference)) => {
        Some(LocalLookupT(range, rlv, reference, rlv.variability))
      }
      case Some(AddressibleClosureVariableT(id, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) ConstraintT else ShareT
        val closuredVarsStructRefPermission = if (mutability == MutableT) ReadwriteT else ReadonlyT // See LHRSP
        val closuredVarsStructRefRef = CoordT(ownership, closuredVarsStructRefPermission, closuredVarsStructRef)
        val name2 = fate.fullName.addStep(ClosureParamNameT())
        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookupT(
              range,
              ReferenceLocalVariableT(name2, FinalT, closuredVarsStructRefRef),
              closuredVarsStructRefRef,
              FinalT))

        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == id))

        val index = closuredVarsStructDef.members.indexWhere(_.name == id.last)
//        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership
        val lookup = AddressMemberLookupT(range, borrowExpr, id, tyype, variability)
        Some(lookup)
      }
      case Some(ReferenceClosureVariableT(varName, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) ConstraintT else ShareT
        val closuredVarsStructRefPermission = if (mutability == MutableT) ReadwriteT else ReadonlyT // See LHRSP
        val closuredVarsStructRefCoord = CoordT(ownership, closuredVarsStructRefPermission, closuredVarsStructRef)
//        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookupT(
              range,
              ReferenceLocalVariableT(fate.fullName.addStep(ClosureParamNameT()), FinalT, closuredVarsStructRefCoord),
              closuredVarsStructRefCoord,
              FinalT))
//        val index = closuredVarsStructDef.members.indexWhere(_.name == varName)

        val lookup =
          ReferenceMemberLookupT(range, borrowExpr, varName, tyype, tyype.permission, variability)
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
    name2: IVarNameT):
  Option[AddressExpressionT] = {
    fate.getVariable(name2) match {
      case Some(alv @ AddressibleLocalVariableT(varId, variability, reference)) => {
        vassert(!fate.unstackifieds.contains(varId))
        Some(LocalLookupT(range, alv, reference, variability))
      }
      case Some(rlv @ ReferenceLocalVariableT(varId, variability, reference)) => {
        if (fate.unstackifieds.contains(varId)) {
          throw CompileErrorExceptionT(CantUseUnstackifiedLocal(range, varId.last))
        }
        Some(LocalLookupT(range, rlv, reference, variability))
      }
      case Some(AddressibleClosureVariableT(id, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) ConstraintT else ShareT
        val closuredVarsStructRefPermission = if (mutability == MutableT) ReadwriteT else ReadonlyT // See LHRSP
        val closuredVarsStructRefRef = CoordT(ownership, closuredVarsStructRefPermission, closuredVarsStructRef)
        val closureParamVarName2 = fate.fullName.addStep(ClosureParamNameT())

        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookupT(
              range,
              ReferenceLocalVariableT(closureParamVarName2, FinalT, closuredVarsStructRefRef),
              closuredVarsStructRefRef,
              variability))
        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == id))

        val index = closuredVarsStructDef.members.indexWhere(_.name == id.last)
        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership
        val lookup = AddressMemberLookupT(range, borrowExpr, id, tyype, variability)
        Some(lookup)
      }
      case Some(ReferenceClosureVariableT(varName, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) ConstraintT else ShareT
        val closuredVarsStructRefPermission = if (mutability == MutableT) ReadwriteT else ReadonlyT // See LHRSP
        val closuredVarsStructRefCoord = CoordT(ownership, closuredVarsStructRefPermission, closuredVarsStructRef)
        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == varName))
        val index = closuredVarsStructDef.members.indexWhere(_.name == varName.last)

        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookupT(
              range,
              ReferenceLocalVariableT(fate.fullName.addStep(ClosureParamNameT()), FinalT, closuredVarsStructRefCoord),
              closuredVarsStructRefCoord,
              FinalT))

//        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership

        val lookup = ReferenceMemberLookupT(range, borrowExpr, varName, tyype, tyype.permission, variability)
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
      closureStructRef: StructRefT):
  (ReferenceExpressionTE) = {
    val closureStructDef = temputs.lookupStruct(closureStructRef);
    // Note, this is where the unordered closuredNames set becomes ordered.
    val lookupExpressions2 =
      closureStructDef.members.map({
        case StructMemberT(memberName, variability, tyype) => {
          val lookup =
            evaluateAddressibleLookup(temputs, fate, range, memberName) match {
              case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldn't find " + memberName))
              case Some(l) => l
            }
          tyype match {
            case ReferenceMemberTypeT(reference) => {
              // We might have to softload an own into a borrow, but the kinds
              // should at least be the same right here.
              vassert(reference.kind == lookup.resultRegister.reference.kind)
              // Closures never contain owning references.
              // If we're capturing an own, then on the inside of the closure
              // it's a borrow or a weak. See "Captured own is borrow" test for more.

              vassert(reference.ownership != OwnT)
              localHelper.borrowSoftLoad(temputs, lookup)
            }
            case AddressMemberTypeT(reference) => {
              vassert(reference == lookup.resultRegister.reference)
              (lookup)
            }
            case _ => vwat()
          }
        }
      });
    val ownership = if (closureStructDef.mutability == MutableT) OwnT else ShareT
    val permission = if (closureStructDef.mutability == MutableT) ReadwriteT else ReadonlyT
    val resultPointerType = CoordT(ownership, permission, closureStructRef)
    val constructExpr2 =
      ConstructTE(closureStructRef, resultPointerType, lookupExpressions2)
    (constructExpr2)
  }

  def evaluateAndCoerceToReferenceExpression(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      expr1: IExpressionAE):
  (ReferenceExpressionTE, Set[CoordT]) = {
    val (expr2, returnsFromExpr) =
      evaluate(temputs, fate, expr1)
    expr2 match {
      case r : ReferenceExpressionTE => {
        (r, returnsFromExpr)
      }
      case a : AddressExpressionT => {
        val expr = coerceToReferenceExpression(fate, a)
        (expr, returnsFromExpr)
      }
      case _ => vwat()
    }
  }

  def coerceToReferenceExpression(fate: FunctionEnvironmentBox, expr2: ExpressionT):
  (ReferenceExpressionTE) = {
    expr2 match {
      case r : ReferenceExpressionTE => (r)
      case a : AddressExpressionT => localHelper.softLoad(fate, a.range, a, UseP)
    }
  }

  private def evaluateExpectedAddressExpression(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      expr1: IExpressionAE):
  (AddressExpressionT, Set[CoordT]) = {
    val (expr2, returns) =
      evaluate(temputs, fate, expr1)
    expr2 match {
      case a : AddressExpressionT => (a, returns)
      case _ : ReferenceExpressionTE => throw CompileErrorExceptionT(RangedInternalErrorT(expr1.range, "Expected reference expression!"))
    }
  }

  // returns:
  // - resulting expression
  // - all the types that are returned from inside the body via ret
  private def evaluate(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      expr1: IExpressionAE):
  (ExpressionT, Set[CoordT]) = {
    profiler.newProfile(expr1.getClass.getSimpleName, fate.fullName.toString, () => {
      expr1 match {
        case VoidAE(range) => (VoidLiteralTE(), Set())
        case ConstantIntAE(range, i, bits) => (ConstantIntTE(i, bits), Set())
        case ConstantBoolAE(range, i) => (ConstantBoolTE(i), Set())
        case ConstantStrAE(range, s) => (ConstantStrTE(s), Set())
        case ConstantFloatAE(range, f) => (ConstantFloatTE(f), Set())
        case ArgLookupAE(range, index) => {
          val paramCoordRuneA = fate.function.params(index).pattern.coordRune
          val paramCoordRune = NameTranslator.translateRune(paramCoordRuneA)
          val paramCoordTemplata = fate.getNearestTemplataWithAbsoluteName2(paramCoordRune, Set(TemplataLookupContext)).get
          val CoordTemplata(paramCoord) = paramCoordTemplata
          vassert(fate.functionEnvironment.fullName.last.parameters(index) == paramCoord)
          (ArgLookupTE(index, paramCoord), Set())
        }
        case FunctionCallAE(range, TemplateSpecifiedLookupAE(_, name, templateArgTemplexesS, callableTargetOwnership), argsExprs1) => {
//          vassert(callableTargetOwnership == LendConstraintP(Some(ReadonlyP)))
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
//          vassert(callableTargetOwnership == LendConstraintP(None))
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
//          vassert(callableTargetOwnership == LendConstraintP(Some(ReadonlyP)))
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
              case OwnT => {
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
              case ConstraintT => {
                loadAsP match {
                  case MoveP => vcurious() // Can we even coerce to an owning reference?
                  case LendConstraintP(None) => innerExpr2
                  case LendConstraintP(Some(permission)) => maybeNarrowPermission(range, innerExpr2, permission)
                  case LendWeakP(permission) => weakAlias(temputs, maybeNarrowPermission(range, innerExpr2, permission))
                  case UseP => innerExpr2
                }
              }
              case WeakT => {
                loadAsP match {
                  case MoveP => vcurious() // Can we even coerce to an owning reference?
                  case LendConstraintP(permission) => vfail() // Need to call lock() to do this
                  case LendWeakP(permission) => maybeNarrowPermission(range, innerExpr2, permission)
                  case UseP => innerExpr2
                }
              }
              case ShareT => {
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
          vcheck(innerExpr2.resultRegister.reference.ownership == WeakT, "Can only lock a weak")

          val borrowCoord = CoordT(ConstraintT, ReadonlyT, innerExpr2.kind)

          val (optCoord, someConstructor, noneConstructor) =
            getOption(temputs, fate.snapshot, range, borrowCoord)

          val resultExpr2 = LockWeakTE(innerExpr2, optCoord, someConstructor, noneConstructor)
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
              case List(BooleanTemplata(value)) => ConstantBoolTE(value)
              case List(IntegerTemplata(value)) => ConstantIntTE(value, 32)
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

          // We should have inferred variability from the presents of sets
          vassert(destinationExpr2.variability == VaryingT)

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

          val mutate2 = MutateTE(destinationExpr2, convertedSourceExpr2);
          (mutate2, returnsFromSource)
        }
        case ExprMutateAE(range, destinationExpr1, sourceExpr1) => {
          val (unconvertedSourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, sourceExpr1)
          val (destinationExpr2, returnsFromDestination) =
            evaluateExpectedAddressExpression(temputs, fate, destinationExpr1)
          if (destinationExpr2.variability != VaryingT) {
            destinationExpr2 match {
              case ReferenceMemberLookupT(range, structExpr, memberName, _, _, _) => {
                structExpr.kind match {
                  case s @ StructRefT(_) => {
                    throw CompileErrorExceptionT(CantMutateFinalMember(range, s.fullName, memberName))
                  }
                  case s @ TupleTT(_, _) => {
                    throw CompileErrorExceptionT(CantMutateFinalMember(range, s.underlyingStruct.fullName, memberName))
                  }
                  case _ => vimpl(structExpr.kind.toString)
                }
              }
              case RuntimeSizedArrayLookupT(range, _, arrayType, _, _, _) => {
                throw CompileErrorExceptionT(CantMutateFinalElement(range, arrayType.name))
              }
              case StaticSizedArrayLookupT(range, _, arrayType, _, _, _) => {
                throw CompileErrorExceptionT(CantMutateFinalElement(range, arrayType.name))
              }
              case x => vimpl(x.toString)
            }
          }
//          destinationExpr2.resultRegister.reference.permission match {
//            case Readonly => {
//              destinationExpr2 match {
//                case ReferenceMemberLookup2(range, structExpr, memberName, _, _) => {
//                  structExpr.kind match {
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

          val mutate2 = MutateTE(destinationExpr2, convertedSourceExpr2);
          (mutate2, returnsFromSource ++ returnsFromDestination)
        }
        case CheckRefCountAE(range, refExpr1, category, numExpr1) => {
          val (refExpr2, returnsFromRef) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, refExpr1);
          val (numExpr2, returnsFromNum) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, numExpr1);
          (CheckRefCountTE(refExpr2, Conversions.evaluateRefCountCategory(category), numExpr2), returnsFromRef ++ returnsFromNum)
        }
        case TemplateSpecifiedLookupAE(range, name, templateArgs1, targetOwnership) => {
          // So far, we only allow these when they're immediately called like functions
          vfail("unimplemented")
        }
        case IndexAE(range, containerExpr1, indexExpr1) => {
          val (unborrowedContainerExpr2, returnsFromContainerExpr) =
            evaluate(temputs, fate, containerExpr1);
          val containerExpr2 =
            dotBorrow(temputs, fate, unborrowedContainerExpr2)

          val (indexExpr2, returnsFromIndexExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, indexExpr1);

          val exprTemplata =
            containerExpr2.resultRegister.reference.kind match {
              case rsa @ RuntimeSizedArrayTT(_) => {
                arrayTemplar.lookupInUnknownSizedArray(range, containerExpr2, indexExpr2, rsa)
              }
              case at@StaticSizedArrayTT(_, _) => {
                arrayTemplar.lookupInStaticSizedArray(range, containerExpr2, indexExpr2, at)
              }
              case at@TupleTT(members, understruct) => {
                indexExpr2 match {
                  case ConstantIntTE(index, _) => {
                    val understructDef = temputs.lookupStruct(understruct);
                    val memberName = understructDef.fullName.addStep(understructDef.members(index.toInt).name)
                    val memberType = understructDef.members(index.toInt).tyype

                    vassert(understructDef.members.exists(member => understructDef.fullName.addStep(member.name) == memberName))

//                    val ownershipInClosureStruct = understructDef.members(index).tyype.reference.ownership

                    val targetPermission =
                      Templar.intersectPermission(
                        containerExpr2.resultRegister.reference.permission,
                        memberType.reference.permission)

                    ReferenceMemberLookupT(range, containerExpr2, memberName, memberType.reference, targetPermission, FinalT)
                  }
                  case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Struct random access not implemented yet!"))
                }
              }
              case sr@StructRefT(_) => {
                throw CompileErrorExceptionT(CannotSubscriptT(range, containerExpr2.resultRegister.reference.kind))
              }
              case _ => vwat()
              // later on, a map type could go here
            }
          (exprTemplata, returnsFromContainerExpr ++ returnsFromIndexExpr)
        }
        case DotAE(range, containerExpr1, memberNameStr, borrowContainer) => {
          val memberName = CodeVarNameT(memberNameStr)
          val (unborrowedContainerExpr2, returnsFromContainerExpr) =
            evaluate(temputs, fate, containerExpr1);
          val containerExpr2 =
            dotBorrow(temputs, fate, unborrowedContainerExpr2)

          val expr2 =
            containerExpr2.resultRegister.reference.kind match {
              case structRef@StructRefT(_) => {
                val structDef = temputs.lookupStruct(structRef)
                val (structMember, memberIndex) =
                  structDef.getMemberAndIndex(memberName) match {
                    case None => throw CompileErrorExceptionT(CouldntFindMemberT(range, memberName.name))
                    case Some(x) => x
                  }
                val memberFullName = structDef.fullName.addStep(structDef.members(memberIndex).name)
                val memberType = structMember.tyype.expectReferenceMember().reference;

                vassert(structDef.members.exists(member => structDef.fullName.addStep(member.name) == memberFullName))

                val (effectiveVariability, targetPermission) =
                  Templar.factorVariabilityAndPermission(
                    containerExpr2.resultRegister.reference.permission,
                    structMember.variability,
                    memberType.permission)

                ReferenceMemberLookupT(range, containerExpr2, memberFullName, memberType, targetPermission, effectiveVariability)
              }
              case TupleTT(_, structRef) => {
                temputs.lookupStruct(structRef) match {
                  case structDef@StructDefinitionT(_, _, _, _, _, _) => {
                    val (structMember, memberIndex) = vassertSome(structDef.getMemberAndIndex(memberName))
                    val memberFullName = structDef.fullName.addStep(structDef.members(memberIndex).name)
                    val memberType = structMember.tyype.expectReferenceMember().reference;

                    vassert(structDef.members.exists(member => structDef.fullName.addStep(member.name) == memberFullName))
                    vassert(structDef.members.exists(_.name == memberFullName.last))

                    val (effectiveVariability, targetPermission) =
                      Templar.factorVariabilityAndPermission(
                        containerExpr2.resultRegister.reference.permission,
                        structMember.variability,
                        memberType.permission)

                    ReferenceMemberLookupT(range, containerExpr2, memberFullName, memberType, targetPermission, effectiveVariability)
                  }
                }
              }
              case as@StaticSizedArrayTT(_, _) => {
                if (memberNameStr.forall(Character.isDigit)) {
                  arrayTemplar.lookupInStaticSizedArray(range, containerExpr2, ConstantIntTE(memberNameStr.toInt, 32), as)
                } else {
                  throw CompileErrorExceptionT(RangedInternalErrorT(range, "Sequence has no member named " + memberNameStr))
                }
              }
              case at@RuntimeSizedArrayTT(_) => {
                if (memberNameStr.forall(Character.isDigit)) {
                  arrayTemplar.lookupInUnknownSizedArray(range, containerExpr2, ConstantIntTE(memberNameStr.toInt, 32), at)
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
        case RuntimeArrayFromCallableAE(range, rules, typeByRune, maybeMutabilityRune, maybeVariabilityRune, sizeAE, callableAE) => {
          val (sizeTE, returnsFromSize) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, sizeAE);
          val (callableTE, returnsFromCallable) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, callableAE);

          val expr2 =
            arrayTemplar.evaluateRuntimeSizedArrayFromCallable(
              temputs, fate, range, rules, typeByRune, maybeMutabilityRune, maybeVariabilityRune, sizeTE, callableTE)
          (expr2, returnsFromSize ++ returnsFromCallable)
        }
        case StaticSizedArrayFromCallableAE(range, mutabilityTemplex, variabilityTemplex, elementCoordTemplex, generatorPrototypeTemplex, sizeExpr1, generatorExpr1) => {
          val (MutabilityTemplata(arrayMutability)) = templataTemplar.evaluateTemplex(fate.snapshot, temputs, mutabilityTemplex)
          val (VariabilityTemplata(arrayVariability)) = templataTemplar.evaluateTemplex(fate.snapshot, temputs, variabilityTemplex)
          val (CoordTemplata(elementCoord)) = templataTemplar.evaluateTemplex(fate.snapshot, temputs, elementCoordTemplex)
          val (PrototypeTemplata(generatorPrototype)) = templataTemplar.evaluateTemplex(fate.snapshot, temputs, generatorPrototypeTemplex)

          val (sizeExpr2, returnsFromSize) =
            evaluate(temputs, fate, sizeExpr1);

          val (generatorExpr2, returnsFromGenerator) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, generatorExpr1);

          checkArray(
            temputs, range, arrayMutability, elementCoord, generatorPrototype, generatorExpr2.resultRegister.reference)
          val arrayType = arrayTemplar.getRuntimeSizedArrayKind(fate.snapshot, temputs, elementCoord, arrayMutability, arrayVariability)

          val sizeRefExpr2 = coerceToReferenceExpression(fate, sizeExpr2)
          vassert(sizeRefExpr2.resultRegister.expectReference().reference == CoordT(ShareT, ReadonlyT, IntT.i32))

          val generatorMethod2 = generatorPrototype
          val constructExpr2 =
            ConstructArrayTE(
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

          val resultExprBlock2 = ConsecutorTE(lets2)

          (resultExprBlock2, returnsFromSource)
        }
        case RuneLookupAE(range, runeA, tyype) => {
          val templata = vassertSome(fate.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(runeA), Set(TemplataLookupContext)))
          (tyype, templata) match {
            case (IntegerTemplataType, IntegerTemplata(value)) => (ConstantIntTE(value, 32), Set())
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
          if (conditionExpr.resultRegister.reference != CoordT(ShareT, ReadonlyT, BoolT())) {
            throw CompileErrorExceptionT(IfConditionIsntBoolean(conditionSE.range, conditionExpr.resultRegister.reference))
          }


          val thenFate = ifBlockFate.makeChildEnvironment(newTemplataStore)

          val (thenExpressionsWithResult, thenReturnsFromExprs) =
            evaluateBlockStatements(temputs, thenFate.snapshot, thenFate, thenBody1.exprs)
          val uncoercedThenBlock2 = BlockTE(thenExpressionsWithResult)

          val (thenUnstackifiedAncestorLocals, thenVarCountersUsed) = thenFate.getEffects()
          val thenContinues = uncoercedThenBlock2.resultRegister.reference.kind != NeverT()

          ifBlockFate.nextCounters(thenVarCountersUsed)


          val elseFate = ifBlockFate.makeChildEnvironment(newTemplataStore)

          val (elseExpressionsWithResult, elseReturnsFromExprs) =
            evaluateBlockStatements(temputs, elseFate.snapshot, elseFate, elseBody1.exprs)
          val uncoercedElseBlock2 = BlockTE(elseExpressionsWithResult)

          val (elseUnstackifiedAncestorLocals, elseVarCountersUsed) = elseFate.getEffects()
          val elseContinues = uncoercedElseBlock2.resultRegister.reference.kind != NeverT()

          ifBlockFate.nextCounters(elseVarCountersUsed)


          val commonType =
            (uncoercedThenBlock2.kind, uncoercedElseBlock2.kind) match {
              case (NeverT(), NeverT()) => uncoercedThenBlock2.resultRegister.reference
              case (NeverT(), _) => uncoercedElseBlock2.resultRegister.reference
              case (_, NeverT()) => uncoercedThenBlock2.resultRegister.reference
              case (a, b) if a == b => uncoercedThenBlock2.resultRegister.reference
              case (a : CitizenRefT, b : CitizenRefT) => {
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
                  CoordT(ownership, permission, commonAncestors.head)
                }
              }
              case (a, b) => {
                vimpl(s"Couldnt reconcile branches of if:\n${a}\n${b}")
              }
            }
          val thenExpr2 = convertHelper.convert(thenFate.snapshot, temputs, range, uncoercedThenBlock2, commonType)
          val elseExpr2 = convertHelper.convert(elseFate.snapshot, temputs, range, uncoercedElseBlock2, commonType)

          val ifExpr2 = IfTE(conditionExpr, thenExpr2, elseExpr2)


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
          if (conditionExpr.resultRegister.reference != CoordT(ShareT, ReadonlyT, BoolT())) {
            throw CompileErrorExceptionT(WhileConditionIsntBoolean(conditionSE.range, conditionExpr.resultRegister.reference))
          }


          val (bodyExpressionsWithResult, bodyReturnsFromExprs) =
            evaluateBlockStatements(temputs, whileBlockFate.snapshot, whileBlockFate, List(body1))
          val uncoercedBodyBlock2 = BlockTE(bodyExpressionsWithResult)

          val bodyContinues = uncoercedBodyBlock2.resultRegister.reference.kind != NeverT()


          val (bodyUnstackifiedAncestorLocals, bodyVarCountersUsed) = whileBlockFate.getEffects()
          if (bodyUnstackifiedAncestorLocals.nonEmpty) {
            throw CompileErrorExceptionT(CantUnstackifyOutsideLocalFromInsideWhile(range, bodyUnstackifiedAncestorLocals.head.last))
          }
          whileBlockFate.nextCounters(bodyVarCountersUsed)


          val thenBody =
            if (uncoercedBodyBlock2.kind == NeverT()) {
              uncoercedBodyBlock2
            } else {
              BlockTE(List(uncoercedBodyBlock2, ConstantBoolTE(true)))
            }

          val ifExpr2 =
            IfTE(
              conditionExpr,
              thenBody,
              BlockTE(List(ConstantBoolTE(false))))
          val whileExpr2 = WhileTE(BlockTE(List(ifExpr2)))
          (whileExpr2, returnsFromCondition ++ bodyReturnsFromExprs)
        }
        case BlockAE(range, blockExprs) => {
          val childEnvironment = fate.makeChildEnvironment(newTemplataStore)

          val (expressionsWithResult, returnsFromExprs) =
            evaluateBlockStatements(temputs, childEnvironment.functionEnvironment, childEnvironment, blockExprs)
          val block2 = BlockTE(expressionsWithResult)

          val (unstackifiedAncestorLocals, varCountersUsed) = childEnvironment.getEffects()
          unstackifiedAncestorLocals.foreach(fate.markLocalUnstackified)
          fate.nextCounters(varCountersUsed)

          (block2, returnsFromExprs)
        }
        case ArrayLengthAE(range, arrayExprA) => {
          val (arrayExpr2, returnsFromArrayExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, arrayExprA);
          (ArrayLengthTE(arrayExpr2), returnsFromArrayExpr)
        }
        case DestructAE(range, innerAE) => {
          val (innerExpr2, returnsFromArrayExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, innerAE);

          // should just ignore others, TODO impl
          vcheck(innerExpr2.resultRegister.reference.ownership == OwnT, "can only destruct own")

          val destroy2 =
            innerExpr2.kind match {
              case structRef@StructRefT(_) => {
                val structDef = temputs.lookupStruct(structRef)
                DestroyTE(
                  innerExpr2,
                  structRef,
                  structDef.members.map(_.tyype).map({ case ReferenceMemberTypeT(reference) =>
                    val rlv = localHelper.makeTemporaryLocal(temputs, fate, reference)
                    rlv
                  case _ => vfail()
                  }))
              }
              case interfaceRef @ InterfaceRefT(_) => {
                destructorTemplar.drop(fate, temputs, innerExpr2)
              }
              case _ => vfail("Can't destruct type: " + innerExpr2.kind)
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

          val resultVarId = fate.fullName.addStep(TemplarFunctionResultVarNameT())
          val resultVariable = ReferenceLocalVariableT(resultVarId, FinalT, innerExpr2.resultRegister.reference)
          val resultLet = LetNormalTE(resultVariable, innerExpr2)
          fate.addVariable(resultVariable)

          val destructExprs =
            localHelper.unletAll(temputs, fate, reversedVariablesToDestruct)

          val getResultExpr =
            localHelper.unletLocal(fate, resultVariable)

          val consecutor = ConsecutorTE(List(resultLet) ++ destructExprs ++ List(getResultExpr))

          val returns = returnsFromInnerExpr + innerExpr2.resultRegister.reference

          (ReturnTE(consecutor), returns)
        }
        case _ => {
          println(expr1)
          vfail(expr1.toString)
        }
      }
    })
  }

  private def checkArray(
      temputs: Temputs,
      range: RangeS,
      arrayMutability: MutabilityT,
      elementCoord: CoordT,
      generatorPrototype: PrototypeT,
      generatorType: CoordT
  ) = {
    if (generatorPrototype.returnType != elementCoord) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Generator return type doesn't agree with array element type!"))
    }
    if (generatorPrototype.paramTypes.size != 2) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Generator must take in 2 args!"))
    }
    if (generatorPrototype.paramTypes(0) != generatorType) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Generator first param doesn't agree with generator expression's result!"))
    }
    if (generatorPrototype.paramTypes(1) != CoordT(ShareT, ReadonlyT, IntT.i32)) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Generator must take in an integer as its second param!"))
    }
    if (arrayMutability == ImmutableT &&
      Templar.getMutability(temputs, elementCoord.kind) == MutableT) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't have an immutable array of mutable elements!"))
    }
  }

  def getOption(temputs: Temputs, fate: FunctionEnvironment, range: RangeS, containedCoord: CoordT):
  (CoordT, PrototypeT, PrototypeT) = {
    val interfaceTemplata =
      fate.getNearestTemplataWithName(CodeTypeNameA("Opt"), Set(TemplataLookupContext)) match {
        case Some(it@InterfaceTemplata(_, _)) => it
        case _ => vfail()
      }
    val optInterfaceRef =
      structTemplar.getInterfaceRef(temputs, range, interfaceTemplata, List(CoordTemplata(containedCoord)))
    val ownOptCoord = CoordT(OwnT, ReadwriteT, optInterfaceRef)

    val someConstructorTemplata =
      fate.getNearestTemplataWithName(GlobalFunctionFamilyNameA("Some"), Set(ExpressionLookupContext)) match {
        case Some(ft@FunctionTemplata(_, _)) => ft
        case _ => vwat();
      }
    val someConstructor =
      delegate.evaluateTemplatedFunctionFromCallForPrototype(
        temputs, range, someConstructorTemplata, List(CoordTemplata(containedCoord)), List(ParamFilter(containedCoord, None))) match {
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
        temputs, range, noneConstructorTemplata, List(CoordTemplata(containedCoord)), List()) match {
        case seff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, seff.toString))
        case EvaluateFunctionSuccess(p) => p
      }
    (ownOptCoord, someConstructor, noneConstructor)
  }

  def getResult(temputs: Temputs, fate: FunctionEnvironment, range: RangeS, containedSuccessCoord: CoordT, containedFailCoord: CoordT):
  (CoordT, PrototypeT, PrototypeT) = {
    val interfaceTemplata =
      fate.getNearestTemplataWithName(CodeTypeNameA("Result"), Set(TemplataLookupContext)) match {
        case Some(it@InterfaceTemplata(_, _)) => it
        case _ => vfail()
      }
    val resultInterfaceRef =
      structTemplar.getInterfaceRef(temputs, range, interfaceTemplata, List(CoordTemplata(containedSuccessCoord), CoordTemplata(containedFailCoord)))
    val ownResultCoord = CoordT(OwnT, ReadwriteT, resultInterfaceRef)

    val okConstructorTemplata =
      fate.getNearestTemplataWithName(GlobalFunctionFamilyNameA("Ok"), Set(ExpressionLookupContext)) match {
        case Some(ft@FunctionTemplata(_, _)) => ft
        case _ => vwat();
      }
    val okConstructor =
      delegate.evaluateTemplatedFunctionFromCallForPrototype(
        temputs, range, okConstructorTemplata, List(CoordTemplata(containedSuccessCoord), CoordTemplata(containedFailCoord)), List(ParamFilter(containedSuccessCoord, None))) match {
        case seff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, seff.toString))
        case EvaluateFunctionSuccess(p) => p
      }

    val errConstructorTemplata =
      fate.getNearestTemplataWithName(GlobalFunctionFamilyNameA("Err"), Set(ExpressionLookupContext)) match {
        case Some(ft@FunctionTemplata(_, _)) => ft
        case _ => vwat();
      }
    val errConstructor =
      delegate.evaluateTemplatedFunctionFromCallForPrototype(
        temputs, range, errConstructorTemplata, List(CoordTemplata(containedSuccessCoord), CoordTemplata(containedFailCoord)), List(ParamFilter(containedFailCoord, None))) match {
        case seff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, seff.toString))
        case EvaluateFunctionSuccess(p) => p
      }

    (ownResultCoord, okConstructor, errConstructor)
  }

  private def maybeNarrowPermission(range: RangeS, innerExpr2: ReferenceExpressionTE, permission: PermissionP) = {
    (innerExpr2.resultRegister.reference.permission, permission) match {
      case (ReadonlyT, ReadonlyP) => innerExpr2
      case (ReadwriteT, ReadonlyP) => NarrowPermissionTE(innerExpr2, ReadonlyT)
      case (ReadonlyT, ReadwriteP) => {
        throw CompileErrorExceptionT(CantUseReadonlyReferenceAsReadwrite(range))
      }
      case (ReadwriteT, ReadwriteP) => innerExpr2
    }
  }

  def weakAlias(temputs: Temputs, expr: ReferenceExpressionTE): ReferenceExpressionTE = {
    expr.kind match {
      case sr @ StructRefT(_) => {
        val structDef = temputs.lookupStruct(sr)
        vcheck(structDef.weakable, TookWeakRefOfNonWeakableError)
      }
      case ir @ InterfaceRefT(_) => {
        val interfaceDef = temputs.lookupInterface(ir)
        vcheck(interfaceDef.weakable, TookWeakRefOfNonWeakableError)
      }
      case _ => vfail()
    }

    WeakAliasTE(expr)
  }

  private def decaySoloPack(fate: FunctionEnvironmentBox, refExpr: ReferenceExpressionTE):
  (ReferenceExpressionTE) = {
    refExpr.resultRegister.reference.kind match {
      case PackTT(List(onlyMember), understruct) => {
        val varNameCounter = fate.nextVarCounter()
        val varId = fate.fullName.addStep(TemplarTemporaryVarNameT(varNameCounter))
        val localVar = ReferenceLocalVariableT(varId, FinalT, onlyMember)
        val destroy2 = DestroyTE(refExpr, understruct, List(localVar))
        val unletExpr = localHelper.unletLocal(fate, localVar)
        (ConsecutorTE(List(destroy2, unletExpr)))
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
      undecayedUnborrowedContainerExpr2: ExpressionT):
  (ReferenceExpressionTE) = {
    undecayedUnborrowedContainerExpr2 match {
      case a: AddressExpressionT => {
        (localHelper.borrowSoftLoad(temputs, a))
      }
      case r: ReferenceExpressionTE => {
        val unborrowedContainerExpr2 = decaySoloPack(fate, r)
        unborrowedContainerExpr2.resultRegister.reference.ownership match {
          case OwnT => localHelper.makeTemporaryLocal(temputs, fate, unborrowedContainerExpr2)
          case ConstraintT | ShareT => (unborrowedContainerExpr2)
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
  (ReferenceExpressionTE) = {

    val closureStructRef2 =
      delegate.evaluateClosureStruct(temputs, fate.snapshot, range, name, function1);
    val closureCoord =
      templataTemplar.pointifyKind(temputs, closureStructRef2, OwnT)

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

  private def newGlobalFunctionGroupExpression(env: IEnvironmentBox, name: GlobalFunctionFamilyNameA): ReferenceExpressionTE = {
    TemplarReinterpretTE(
      Program2.emptyPackExpression,
      CoordT(
        ShareT,
        ReadonlyT,
        OverloadSet(
          env.snapshot,
          name,
          Program2.emptyTupleStructRef)))
  }

  def evaluateBlockStatements(
    temputs: Temputs, startingFate: FunctionEnvironment, fate: FunctionEnvironmentBox, exprs: List[IExpressionAE]):
  (List[ReferenceExpressionTE], Set[CoordT]) = {
    blockTemplar.evaluateBlockStatements(temputs, startingFate, fate, exprs)
  }

  def nonCheckingTranslateList(temputs: Temputs, fate: FunctionEnvironmentBox, patterns1: List[AtomAP], patternInputExprs2: List[ReferenceExpressionTE]): List[ReferenceExpressionTE] = {
    patternTemplar.nonCheckingTranslateList(temputs, fate, patterns1, patternInputExprs2)
  }
}
