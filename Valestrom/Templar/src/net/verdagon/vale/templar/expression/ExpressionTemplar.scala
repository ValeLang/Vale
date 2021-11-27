package net.verdagon.vale.templar.expression

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.{RuneTypeSolver, Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.{ast, _}
import net.verdagon.vale.templar.ast.{AddressExpressionTE, AddressMemberLookupTE, ArgLookupTE, BlockTE, ConstantBoolTE, ConstantFloatTE, ConstantIntTE, ConstantStrTE, ConstructTE, DestroyTE, ExpressionT, IfTE, LetNormalTE, LocalLookupTE, LocationInFunctionEnvironment, MutateTE, NarrowPermissionTE, ProgramT, PrototypeT, ReferenceExpressionTE, ReferenceMemberLookupTE, ReturnTE, RuntimeSizedArrayLookupTE, StaticSizedArrayLookupTE, TemplarReinterpretTE, VoidLiteralTE, WeakAliasTE, WhileTE}
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.function.FunctionTemplar.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
import net.verdagon.vale.templar.names.{ArbitraryNameT, CitizenNameT, CitizenTemplateNameT, ClosureParamNameT, CodeVarNameT, FullNameT, IVarNameT, NameTranslator, TemplarFunctionResultVarNameT, TemplarTemporaryVarNameT}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._

import scala.collection.immutable.{List, Nil, Set}

case class TookWeakRefOfNonWeakableError() extends Throwable { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

trait IExpressionTemplarDelegate {
  def evaluateTemplatedFunctionFromCallForPrototype(
    temputs: Temputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata,
    explicitTemplateArgs: Vector[ITemplata],
    args: Vector[ParamFilter]):
  IEvaluateFunctionResult[PrototypeT]

  def evaluateClosureStruct(
    temputs: Temputs,
    containingFunctionEnv: FunctionEnvironment,
    callRange: RangeS,
    name: IFunctionDeclarationNameS,
    function1: FunctionA):
  StructTT
}

class ExpressionTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,

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
  val blockTemplar = new BlockTemplar(opts, destructorTemplar, localHelper, new IBlockTemplarDelegate {
    override def evaluateAndCoerceToReferenceExpression(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
    (ReferenceExpressionTE, Set[CoordT]) = {
      ExpressionTemplar.this.evaluateAndCoerceToReferenceExpression(temputs, fate, life, expr1)
    }
  })

  def evaluateAndCoerceToReferenceExpressions(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    life: LocationInFunctionEnvironment,
    exprs1: Vector[IExpressionSE]):
  (Vector[ReferenceExpressionTE], Set[CoordT]) = {
    val things =
      exprs1.zipWithIndex.map({ case (expr, index) =>
        evaluateAndCoerceToReferenceExpression(temputs, fate, life + index, expr)
      })
    (things.map(_._1), things.map(_._2).flatten.toSet)
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
        fate.lookupNearestWithName(profiler, name, Set(TemplataLookupContext)) match {
          case Some(IntegerTemplata(num)) => (Some(ConstantIntTE(num, 32)))
          case Some(BooleanTemplata(bool)) => (Some(ConstantBoolTE(bool)))
          case None => (None)
        }
      }
    }
  }

  private def evaluateAddressibleLookupForMutate(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      range: RangeS,
      nameA: IVarNameS):
  Option[AddressExpressionTE] = {
    fate.getVariable(NameTranslator.translateVarNameStep(nameA)) match {
      case Some(alv @ AddressibleLocalVariableT(_, _, reference)) => {
        Some(LocalLookupTE(range, alv, reference, alv.variability))
      }
      case Some(rlv @ ReferenceLocalVariableT(id, _, reference)) => {
        Some(LocalLookupTE(range, rlv, reference, rlv.variability))
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
            LocalLookupTE(
              range,
              ReferenceLocalVariableT(name2, FinalT, closuredVarsStructRefRef),
              closuredVarsStructRefRef,
              FinalT))

        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == id))

        val index = closuredVarsStructDef.members.indexWhere(_.name == id.last)
//        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership
        val lookup = AddressMemberLookupTE(range, borrowExpr, id, tyype, variability)
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
            LocalLookupTE(
              range,
              ReferenceLocalVariableT(fate.fullName.addStep(ClosureParamNameT()), FinalT, closuredVarsStructRefCoord),
              closuredVarsStructRefCoord,
              FinalT))
//        val index = closuredVarsStructDef.members.indexWhere(_.name == varName)

        val lookup =
          ReferenceMemberLookupTE(range, borrowExpr, varName, tyype, tyype.permission, variability)
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
  Option[AddressExpressionTE] = {
    fate.getVariable(name2) match {
      case Some(alv @ AddressibleLocalVariableT(varId, variability, reference)) => {
        vassert(!fate.unstackifieds.contains(varId))
        Some(LocalLookupTE(range, alv, reference, variability))
      }
      case Some(rlv @ ReferenceLocalVariableT(varId, variability, reference)) => {
        if (fate.unstackifieds.contains(varId)) {
          throw CompileErrorExceptionT(CantUseUnstackifiedLocal(range, varId.last))
        }
        Some(LocalLookupTE(range, rlv, reference, variability))
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
            LocalLookupTE(
              range,
              ReferenceLocalVariableT(closureParamVarName2, FinalT, closuredVarsStructRefRef),
              closuredVarsStructRefRef,
              variability))
        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == id))

        val index = closuredVarsStructDef.members.indexWhere(_.name == id.last)
        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership
        val lookup = ast.AddressMemberLookupTE(range, borrowExpr, id, tyype, variability)
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
            LocalLookupTE(
              range,
              ReferenceLocalVariableT(fate.fullName.addStep(ClosureParamNameT()), FinalT, closuredVarsStructRefCoord),
              closuredVarsStructRefCoord,
              FinalT))

//        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership

        val lookup = ast.ReferenceMemberLookupTE(range, borrowExpr, varName, tyype, tyype.permission, variability)
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
      closureStructRef: StructTT):
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
              vassert(reference.kind == lookup.result.reference.kind)
              // Closures never contain owning references.
              // If we're capturing an own, then on the inside of the closure
              // it's a borrow or a weak. See "Captured own is borrow" test for more.

              vassert(reference.ownership != OwnT)
              localHelper.borrowSoftLoad(temputs, lookup)
            }
            case AddressMemberTypeT(reference) => {
              vassert(reference == lookup.result.reference)
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
      life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
  (ReferenceExpressionTE, Set[CoordT]) = {
    val (expr2, returnsFromExpr) =
      evaluate(temputs, fate, life, expr1)
    expr2 match {
      case r : ReferenceExpressionTE => {
        (r, returnsFromExpr)
      }
      case a : AddressExpressionTE => {
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
      case a : AddressExpressionTE => localHelper.softLoad(fate, a.range, a, UseP)
    }
  }

  private def evaluateExpectedAddressExpression(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
    life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
  (AddressExpressionTE, Set[CoordT]) = {
    val (expr2, returns) =
      evaluate(temputs, fate, life, expr1)
    expr2 match {
      case a : AddressExpressionTE => (a, returns)
      case _ : ReferenceExpressionTE => throw CompileErrorExceptionT(RangedInternalErrorT(expr1.range, "Expected reference expression!"))
    }
  }

  // returns:
  // - resulting expression
  // - all the types that are returned from inside the body via ret
  private def evaluate(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
  (ExpressionT, Set[CoordT]) = {
    profiler.newProfile(expr1.getClass.getSimpleName, fate.fullName.toString, () => {
      expr1 match {
        case VoidSE(range) => (VoidLiteralTE(), Set())
        case ConstantIntSE(range, i, bits) => (ConstantIntTE(i, bits), Set())
        case ConstantBoolSE(range, i) => (ConstantBoolTE(i), Set())
        case ConstantStrSE(range, s) => (ConstantStrTE(s), Set())
        case ConstantFloatSE(range, f) => (ConstantFloatTE(f), Set())
        case ArgLookupSE(range, index) => {
          val paramCoordRune = fate.function.params(index).pattern.coordRune.get
          val paramCoordTemplata = vassertOne(fate.lookupNearestWithImpreciseName(profiler, RuneNameS(paramCoordRune.rune), Set(TemplataLookupContext)))
          val CoordTemplata(paramCoord) = paramCoordTemplata
          vassert(fate.functionEnvironment.fullName.last.parameters(index) == paramCoord)
          (ArgLookupTE(index, paramCoord), Set())
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, maybeTemplateArgs, callableTargetOwnership), argsExprs1) => {
//          vassert(callableTargetOwnership == LendConstraintP(Some(ReadonlyP)))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, life + 0, argsExprs1)
          val callExpr2 =
            callTemplar.evaluatePrefixCall(
              temputs,
              fate,
              life + 1,
              range,
              newGlobalFunctionGroupExpression(fate.snapshot, temputs, name),
              rules.toVector,
              maybeTemplateArgs.toArray.flatMap(_.map(_.rune)),
              argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, templateArgTemplexesS, callableTargetOwnership), argsExprs1) => {
//          vassert(callableTargetOwnership == LendConstraintP(None))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, life + 0, argsExprs1)
          val callExpr2 =
            callTemplar.evaluatePrefixCall(
              temputs,
              fate,
              life + 1,
              range,
              newGlobalFunctionGroupExpression(fate.snapshot, temputs, name),
              rules.toVector,
              templateArgTemplexesS.toArray.flatMap(_.map(_.rune)),
              argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, templateArgs, callableTargetOwnership), argsExprs1) => {
          vassert(callableTargetOwnership == LendConstraintP(None))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, life, argsExprs1)
          val callExpr2 =
            callTemplar.evaluateNamedPrefixCall(temputs, fate, range, name, rules.toVector, templateArgs.toVector.flatten.map(_.rune), argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, templateArgs, callableTargetOwnership), argsPackExpr1) => {
//          vassert(callableTargetOwnership == LendConstraintP(Some(ReadonlyP)))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, life, argsPackExpr1)
          val callExpr2 =
            callTemplar.evaluateNamedPrefixCall(temputs, fate, range, name, rules.toVector, Vector(), argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, callableExpr1, argsExprs1) => {
          val (undecayedCallableExpr2, returnsFromCallable) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, life + 0, callableExpr1);
          val decayedCallableExpr2 =
            localHelper.maybeBorrowSoftLoad(temputs, undecayedCallableExpr2)
          val decayedCallableReferenceExpr2 =
            coerceToReferenceExpression(fate, decayedCallableExpr2)
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, life + 1, argsExprs1)
          val functionPointerCall2 =
            callTemplar.evaluatePrefixCall(temputs, fate, life + 2, range, decayedCallableReferenceExpr2, Vector(), Array(), argsExprs2)
          (functionPointerCall2, returnsFromCallable ++ returnsFromArgs)
        }

        case OwnershippedSE(range, innerExpr1, loadAsP) => {
          val (innerExpr2, returnsFromInner) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, life + 0, innerExpr1);
          val resultExpr2 =
            innerExpr2.result.underlyingReference.ownership match {
              case OwnT => {
                loadAsP match {
                  case MoveP => {
                    // this can happen if we put a ^ on an owning reference. No harm, let it go.
                    innerExpr2
                  }
                  case LendConstraintP(None) => {
                    localHelper.makeTemporaryLocal(temputs, fate, life + 1, innerExpr2)
                  }
                  case LendConstraintP(Some(permission)) => {
                    maybeNarrowPermission(range, localHelper.makeTemporaryLocal(temputs, fate, life + 2, innerExpr2), permission)
                  }
                  case LendWeakP(permission) => {
                    weakAlias(temputs, maybeNarrowPermission(range, localHelper.makeTemporaryLocal(temputs, fate, life + 3, innerExpr2), permission))
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
        case LocalLoadSE(range, nameA, targetOwnership) => {
          val name = NameTranslator.translateVarNameStep(nameA)
          val lookupExpr1 =
            evaluateLookupForLoad(temputs, fate, range, name, targetOwnership) match {
              case (None) => {
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldnt find " + name))
              }
              case (Some(x)) => (x)
            }
          (lookupExpr1, Set())
        }
        case OutsideLoadSE(range, rules, name, templateArgs, targetOwnership) => {
          // Note, we don't get here if we're about to call something with this, that's handled
          // by a different case.

          // We can't use *anything* from the global environment; we're in expression context,
          // not in templata context.

          val templataFromEnv =
            fate.lookupAllWithImpreciseName(profiler, name, Set(ExpressionLookupContext)) match {
              case Vector(BooleanTemplata(value)) => ConstantBoolTE(value)
              case Vector(IntegerTemplata(value)) => ConstantIntTE(value, 32)
              case templatas if templatas.nonEmpty && templatas.collect({ case FunctionTemplata(_, _) => case ExternFunctionTemplata(_) => }).size == templatas.size => {
                if (targetOwnership == MoveP) {
                  throw CompileErrorExceptionT(CantMoveFromGlobal(range, "Can't move from globals. Name: " + name))
                }
                newGlobalFunctionGroupExpression(fate.snapshot, temputs, name)
              }
              case things if things.size > 1 => {
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Found too many different things named \"" + name + "\" in env:\n" + things.map("\n" + _)))
              }
              case Vector() => {
                //              println("members: " + fate.getAllTemplatasWithName(name, Set(ExpressionLookupContext, TemplataLookupContext)))
                throw CompileErrorExceptionT(CouldntFindIdentifierToLoadT(range, name))
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldn't find anything named \"" + name + "\" in env:\n" + fate))
              }
            }
          (templataFromEnv, Set())
        }
        case LocalMutateSE(range, name, sourceExpr1) => {
          val destinationExpr2 =
            evaluateAddressibleLookupForMutate(temputs, fate, range, name) match {
              case None => {
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldnt find " + name))
              }
              case Some(x) => x
            }
          val (unconvertedSourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, life, sourceExpr1)

          // We should have inferred variability from the presents of sets
          vassert(destinationExpr2.variability == VaryingT)

          val isConvertible =
            templataTemplar.isTypeConvertible(
              temputs, unconvertedSourceExpr2.result.reference, destinationExpr2.result.reference)
          if (!isConvertible) {
            throw CompileErrorExceptionT(
              CouldntConvertForMutateT(
                range, destinationExpr2.result.reference, unconvertedSourceExpr2.result.reference))
          }
          vassert(isConvertible)
          val convertedSourceExpr2 =
            convertHelper.convert(fate.snapshot, temputs, range, unconvertedSourceExpr2, destinationExpr2.result.reference);

          val mutate2 = MutateTE(destinationExpr2, convertedSourceExpr2);
          (mutate2, returnsFromSource)
        }
        case ExprMutateSE(range, destinationExpr1, sourceExpr1) => {
          val (unconvertedSourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, life + 0, sourceExpr1)
          val (destinationExpr2, returnsFromDestination) =
            evaluateExpectedAddressExpression(temputs, fate, life + 1, destinationExpr1)
          if (destinationExpr2.variability != VaryingT) {
            destinationExpr2 match {
              case ReferenceMemberLookupTE(range, structExpr, memberName, _, _, _) => {
                structExpr.kind match {
                  case s @ StructTT(_) => {
                    throw CompileErrorExceptionT(CantMutateFinalMember(range, s.fullName, memberName))
                  }
                  case _ => vimpl(structExpr.kind.toString)
                }
              }
              case RuntimeSizedArrayLookupTE(range, _, arrayType, _, _, _) => {
                throw CompileErrorExceptionT(CantMutateFinalElement(range, arrayType.name))
              }
              case StaticSizedArrayLookupTE(range, _, arrayType, _, _, _) => {
                throw CompileErrorExceptionT(CantMutateFinalElement(range, arrayType.name))
              }
              case x => vimpl(x.toString)
            }
          }

          val isConvertible =
            templataTemplar.isTypeConvertible(temputs, unconvertedSourceExpr2.result.reference, destinationExpr2.result.reference)
          if (!isConvertible) {
            throw CompileErrorExceptionT(CouldntConvertForMutateT(range, destinationExpr2.result.reference, unconvertedSourceExpr2.result.reference))
          }
          val convertedSourceExpr2 =
            convertHelper.convert(fate.snapshot, temputs, range, unconvertedSourceExpr2, destinationExpr2.result.reference);

          val mutate2 = MutateTE(destinationExpr2, convertedSourceExpr2);
          (mutate2, returnsFromSource ++ returnsFromDestination)
        }
        case OutsideLoadSE(range, rules, name, templateArgs1, targetOwnership) => {
          // So far, we only allow these when they're immediately called like functions
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Raw template specified lookups unimplemented!"))
        }
        case IndexSE(range, containerExpr1, indexExpr1) => {
          val (unborrowedContainerExpr2, returnsFromContainerExpr) =
            evaluate(temputs, fate, life + 0, containerExpr1);
          val containerExpr2 =
            dotBorrow(temputs, fate, life + 1, unborrowedContainerExpr2)

          val (indexExpr2, returnsFromIndexExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, life + 2, indexExpr1);

          val exprTemplata =
            containerExpr2.result.reference.kind match {
              case rsa @ RuntimeSizedArrayTT(_) => {
                arrayTemplar.lookupInUnknownSizedArray(range, containerExpr2, indexExpr2, rsa)
              }
              case at@StaticSizedArrayTT(_, _) => {
                arrayTemplar.lookupInStaticSizedArray(range, containerExpr2, indexExpr2, at)
              }
              case at@StructTT(FullNameT(ProgramT.topLevelName, Vector(), CitizenNameT(CitizenTemplateNameT(ProgramT.tupleHumanName), _))) => {
                indexExpr2 match {
                  case ConstantIntTE(index, _) => {
                    val understructDef = temputs.lookupStruct(at);
                    val memberName = understructDef.fullName.addStep(understructDef.members(index.toInt).name)
                    val memberType = understructDef.members(index.toInt).tyype

                    vassert(understructDef.members.exists(member => understructDef.fullName.addStep(member.name) == memberName))

//                    val ownershipInClosureStruct = understructDef.members(index).tyype.reference.ownership

                    val targetPermission =
                      Templar.intersectPermission(
                        containerExpr2.result.reference.permission,
                        memberType.reference.permission)

                    ast.ReferenceMemberLookupTE(range, containerExpr2, memberName, memberType.reference, targetPermission, FinalT)
                  }
                  case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Struct random access not implemented yet!"))
                }
              }
              case sr@StructTT(_) => {
                throw CompileErrorExceptionT(CannotSubscriptT(range, containerExpr2.result.reference.kind))
              }
              case _ => vwat()
              // later on, a map type could go here
            }
          (exprTemplata, returnsFromContainerExpr ++ returnsFromIndexExpr)
        }
        case DotSE(range, containerExpr1, memberNameStr, borrowContainer) => {
          val memberName = CodeVarNameT(memberNameStr)
          val (unborrowedContainerExpr2, returnsFromContainerExpr) =
            evaluate(temputs, fate, life + 0, containerExpr1);
          val containerExpr2 =
            dotBorrow(temputs, fate, life + 1, unborrowedContainerExpr2)

          val expr2 =
            containerExpr2.result.reference.kind match {
              case structTT@StructTT(_) => {
                val structDef = temputs.lookupStruct(structTT)
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
                    containerExpr2.result.reference.permission,
                    structMember.variability,
                    memberType.permission)

                ast.ReferenceMemberLookupTE(range, containerExpr2, memberFullName, memberType, targetPermission, effectiveVariability)
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
        case FunctionSE(functionS @ FunctionS(range, name, _, _, _, _, _, _, _)) => {
          val callExpr2 = evaluateClosure(temputs, fate, range, name, functionS)
          (callExpr2, Set())
        }
        case TupleSE(range, elements1) => {
          val (exprs2, returnsFromElements) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, life + 0, elements1);

          // would we need a sequence templata? probably right?
          val expr2 = sequenceTemplar.evaluate(fate.snapshot, temputs, exprs2)
          (expr2, returnsFromElements)
        }
        case StaticArrayFromValuesSE(range, rules, mutabilityRune, variabilityRune, sizeRuneA, elements1) => {
          val (exprs2, returnsFromElements) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, life, elements1);
          // would we need a sequence templata? probably right?
          val expr2 =
            arrayTemplar.evaluateStaticSizedArrayFromValues(
              temputs, fate, range, rules.toVector, sizeRuneA.rune, mutabilityRune.rune, variabilityRune.rune, exprs2)
          (expr2, returnsFromElements)
        }
        case StaticArrayFromCallableSE(range, rules, maybeMutabilityRune, maybeVariabilityRune, sizeRuneA, callableAE) => {
          val (callableTE, returnsFromCallable) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, life, callableAE);
          val expr2 =
            arrayTemplar.evaluateStaticSizedArrayFromCallable(
              temputs, fate, range, rules.toVector, sizeRuneA.rune, maybeMutabilityRune.rune, maybeVariabilityRune.rune, callableTE)
          (expr2, returnsFromCallable)
        }
        case RuntimeArrayFromCallableSE(range, rulesA, mutabilityRune, variabilityRune, sizeAE, callableAE) => {
          val (sizeTE, returnsFromSize) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, life + 0, sizeAE);
          val (callableTE, returnsFromCallable) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, life + 1, callableAE);

          val expr2 =
            arrayTemplar.evaluateRuntimeSizedArrayFromCallable(
              temputs, fate, range, rulesA.toVector, mutabilityRune.rune, variabilityRune.rune, sizeTE, callableTE)
          (expr2, returnsFromSize ++ returnsFromCallable)
        }
        case LetSE(range, rulesA, pattern, sourceExpr1) => {
          val (sourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, life + 0, sourceExpr1)

          val runeToInitiallyKnownType = PatternSUtils.getRuneTypesFromPattern(pattern)
          val runeToType =
            RuneTypeSolver.solve(
                opts.globalOptions.sanityCheck,
                opts.globalOptions.useOptimizedSolver,
                nameS => vassertOne(fate.lookupNearestWithImpreciseName(profiler, nameS, Set(TemplataLookupContext))).tyype,
                range,
                false,
                rulesA,
                List(),
                true,
                runeToInitiallyKnownType.toMap) match {
              case Ok(r) => r
              case Err(e) => throw CompileErrorExceptionT(InferAstronomerError(range, e))
            }
          val resultTE =
            patternTemplar.inferAndTranslatePattern(
              temputs, fate, life + 1, rulesA.toVector, runeToType, pattern, sourceExpr2,
              (temputs, fate, life, liveCaptureLocals) => VoidLiteralTE())

          (resultTE, returnsFromSource)
        }
        case RuneLookupSE(range, runeA) => {
          val templata = vassertOne(fate.lookupNearestWithImpreciseName(profiler, RuneNameS(runeA), Set(TemplataLookupContext)))
          templata match {
            case IntegerTemplata(value) => (ConstantIntTE(value, 32), Set())
            case PrototypeTemplata(value) => {
              val tinyEnv =
                fate.functionEnvironment.makeChildBlockEnvironment(None)
                  .addEntries(Vector(ArbitraryNameT() -> TemplataEnvEntry(PrototypeTemplata(value))))
              val expr = newGlobalFunctionGroupExpression(tinyEnv, temputs, ArbitraryNameS())
              (expr, Set())
            }
          }
        }
        case IfSE(range, conditionSE, thenBody1, elseBody1) => {
          // We make a block for the if-statement which contains its condition (the "if block"),
          // and then two child blocks under that for the then and else blocks.
          // The then and else blocks are children of the block which contains the condition
          // so they can access any locals declared by the condition.

          val ifBlockFate = fate.makeChildBlockEnvironment(None)

          val (conditionExpr, returnsFromCondition) =
            evaluateAndCoerceToReferenceExpression(temputs, ifBlockFate, life + 1, conditionSE)
          if (conditionExpr.result.reference != CoordT(ShareT, ReadonlyT, BoolT())) {
            throw CompileErrorExceptionT(IfConditionIsntBoolean(conditionSE.range, conditionExpr.result.reference))
          }


          val thenFate = ifBlockFate.makeChildBlockEnvironment(Some(thenBody1))

          val (thenExpressionsWithResult, thenReturnsFromExprs) =
            evaluateBlockStatements(temputs, thenFate.snapshot, thenFate, life + 2, thenBody1.exprs)
          val uncoercedThenBlock2 = BlockTE(thenExpressionsWithResult)

          val thenUnstackifiedAncestorLocals = thenFate.getEffectsSince(ifBlockFate.snapshot)
          val thenContinues = uncoercedThenBlock2.result.reference.kind != NeverT()

          val elseFate = ifBlockFate.makeChildBlockEnvironment(Some(elseBody1))

          val (elseExpressionsWithResult, elseReturnsFromExprs) =
            evaluateBlockStatements(temputs, elseFate.snapshot, elseFate, life + 3, elseBody1.exprs)
          val uncoercedElseBlock2 = BlockTE(elseExpressionsWithResult)

          val elseUnstackifiedAncestorLocals = elseFate.getEffectsSince(ifBlockFate.snapshot)
          val elseContinues = uncoercedElseBlock2.result.reference.kind != NeverT()

          val commonType =
            (uncoercedThenBlock2.kind, uncoercedElseBlock2.kind) match {
              case (NeverT(), NeverT()) => uncoercedThenBlock2.result.reference
              case (NeverT(), _) => uncoercedElseBlock2.result.reference
              case (_, NeverT()) => uncoercedThenBlock2.result.reference
              case (a, b) if a == b => uncoercedThenBlock2.result.reference
              case (a : CitizenRefT, b : CitizenRefT) => {
                val aAncestors = ancestorHelper.getAncestorInterfacesWithDistance(temputs, a).keys.toSet
                val bAncestors = ancestorHelper.getAncestorInterfacesWithDistance(temputs, b).keys.toSet
                val commonAncestors = aAncestors.intersect(bAncestors)

                if (uncoercedElseBlock2.result.reference.ownership != uncoercedElseBlock2.result.reference.ownership) {
                  throw CompileErrorExceptionT(RangedInternalErrorT(range, "Two branches of if have different ownerships!\\n${a}\\n${b}"))
                }
                val ownership = uncoercedElseBlock2.result.reference.ownership
                val permission = uncoercedElseBlock2.result.reference.permission

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


          val ifBlockUnstackifiedAncestorLocals = ifBlockFate.getEffectsSince(fate.snapshot)
          ifBlockUnstackifiedAncestorLocals.foreach(fate.markLocalUnstackified)


          (ifExpr2, returnsFromCondition ++ thenReturnsFromExprs ++ elseReturnsFromExprs)
        }
        case WhileSE(range, conditionSE, body1) => {
          // We make a block for the while-statement which contains its condition (the "if block"),
          // and the body block, so they can access any locals declared by the condition.

          val whileBlockFate = fate.makeChildBlockEnvironment(None)

          val (conditionExpr, returnsFromCondition) =
            evaluateAndCoerceToReferenceExpression(temputs, whileBlockFate, life + 0, conditionSE)
          if (conditionExpr.result.reference != CoordT(ShareT, ReadonlyT, BoolT())) {
            throw CompileErrorExceptionT(WhileConditionIsntBoolean(conditionSE.range, conditionExpr.result.reference))
          }


          val (bodyExpressionsWithResult, bodyReturnsFromExprs) =
            evaluateBlockStatements(temputs, whileBlockFate.snapshot, whileBlockFate, life + 1, Vector(body1))
          val uncoercedBodyBlock2 = BlockTE(bodyExpressionsWithResult)

          val bodyContinues = uncoercedBodyBlock2.result.reference.kind != NeverT()


          val bodyUnstackifiedAncestorLocals = whileBlockFate.getEffectsSince(fate.snapshot)
          if (bodyUnstackifiedAncestorLocals.nonEmpty) {
            throw CompileErrorExceptionT(CantUnstackifyOutsideLocalFromInsideWhile(range, bodyUnstackifiedAncestorLocals.head.last))
          }


          val thenBody =
            if (uncoercedBodyBlock2.kind == NeverT()) {
              uncoercedBodyBlock2
            } else {
              BlockTE(Templar.consecutive(Vector(uncoercedBodyBlock2, ConstantBoolTE(true))))
            }

          val ifExpr2 =
            ast.IfTE(
              conditionExpr,
              thenBody,
              BlockTE(ConstantBoolTE(false)))
          val whileExpr2 = WhileTE(BlockTE(ifExpr2))
          (whileExpr2, returnsFromCondition ++ bodyReturnsFromExprs)
        }
        case b @ BlockSE(range, locals, blockExprs) => {
          val childEnvironment = fate.makeChildBlockEnvironment(Some(b))

          val (expressionsWithResult, returnsFromExprs) =
            evaluateBlockStatements(temputs, childEnvironment.functionEnvironment, childEnvironment, life, blockExprs)
          val block2 = BlockTE(expressionsWithResult)

          val unstackifiedAncestorLocals = childEnvironment.getEffectsSince(fate.snapshot)
          unstackifiedAncestorLocals.foreach(fate.markLocalUnstackified)

          (block2, returnsFromExprs)
        }
        case DestructSE(range, innerAE) => {
          val (innerExpr2, returnsFromArrayExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, life + 0, innerAE);

          // should just ignore others, TODO impl
          vcheck(innerExpr2.result.reference.ownership == OwnT, "can only destruct own")

          val destroy2 =
            innerExpr2.kind match {
              case structTT@StructTT(_) => {
                val structDef = temputs.lookupStruct(structTT)
                DestroyTE(
                  innerExpr2,
                  structTT,
                  structDef.members.map(_.tyype).zipWithIndex.map({ case (memberType, index) =>
                    memberType match {
                      case ReferenceMemberTypeT(reference) => {
                        localHelper.makeTemporaryLocal(fate, life + 1 + index, reference)
                      }
                      case _ => vfail()
                    }
                  }))
              }
              case interfaceTT @ InterfaceTT(_) => {
                destructorTemplar.drop(fate, temputs, innerExpr2)
              }
              case _ => vfail("Can't destruct type: " + innerExpr2.kind)
            }
          (destroy2, returnsFromArrayExpr)
        }
        case ReturnSE(range, innerExprA) => {
          val (uncastedInnerExpr2, returnsFromInnerExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, fate, life + 0, innerExprA);

          val innerExpr2 =
            fate.maybeReturnType match {
              case None => (uncastedInnerExpr2)
              case Some(returnType) => {
                templataTemplar.isTypeConvertible(temputs, uncastedInnerExpr2.result.reference, returnType) match {
                  case (false) => {
                    throw CompileErrorExceptionT(
                      CouldntConvertForReturnT(range, returnType, uncastedInnerExpr2.result.reference))
                  }
                  case (true) => {
                    convertHelper.convert(fate.snapshot, temputs, range, uncastedInnerExpr2, returnType)
                  }
                }
              }
            }

          val allLocals = fate.getAllLocals()
          val unstackifiedLocals = fate.getAllUnstackifiedLocals()
          val variablesToDestruct = allLocals.filter(x => !unstackifiedLocals.contains(x.id))
          val reversedVariablesToDestruct = variablesToDestruct.reverse

          val resultVarId = fate.fullName.addStep(TemplarFunctionResultVarNameT())
          val resultVariable = ReferenceLocalVariableT(resultVarId, FinalT, innerExpr2.result.reference)
          val resultLet = LetNormalTE(resultVariable, innerExpr2)
          fate.addVariable(resultVariable)

          val destructExprs =
            localHelper.unletAll(temputs, fate, reversedVariablesToDestruct)

          val getResultExpr =
            localHelper.unletLocal(fate, resultVariable)

          val consecutor = Templar.consecutive(Vector(resultLet) ++ destructExprs ++ Vector(getResultExpr))

          val returns = returnsFromInnerExpr + innerExpr2.result.reference

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
      fate.lookupNearestWithImpreciseName(profiler, CodeNameS("Opt"), Set(TemplataLookupContext)).toList match {
        case List(it@InterfaceTemplata(_, _)) => it
        case _ => vfail()
      }
    val optInterfaceRef =
      structTemplar.getInterfaceRef(temputs, range, interfaceTemplata, Vector(CoordTemplata(containedCoord)))
    val ownOptCoord = CoordT(OwnT, ReadwriteT, optInterfaceRef)

    val someConstructorTemplata =
      fate.lookupNearestWithImpreciseName(profiler, CodeNameS("Some"), Set(ExpressionLookupContext)).toList match {
        case List(ft@FunctionTemplata(_, _)) => ft
        case _ => vwat();
      }
    val someConstructor =
      delegate.evaluateTemplatedFunctionFromCallForPrototype(
        temputs, range, someConstructorTemplata, Vector(CoordTemplata(containedCoord)), Vector(ParamFilter(containedCoord, None))) match {
        case fff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, fff.toString))
        case EvaluateFunctionSuccess(p) => p
      }

    val noneConstructorTemplata =
      fate.lookupNearestWithImpreciseName(profiler, CodeNameS("None"), Set(ExpressionLookupContext)).toList match {
        case List(ft@FunctionTemplata(_, _)) => ft
        case _ => vwat();
      }
    val noneConstructor =
      delegate.evaluateTemplatedFunctionFromCallForPrototype(
        temputs, range, noneConstructorTemplata, Vector(CoordTemplata(containedCoord)), Vector()) match {
        case fff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, fff.toString))
        case EvaluateFunctionSuccess(p) => p
      }
    (ownOptCoord, someConstructor, noneConstructor)
  }

  def getResult(temputs: Temputs, fate: FunctionEnvironment, range: RangeS, containedSuccessCoord: CoordT, containedFailCoord: CoordT):
  (CoordT, PrototypeT, PrototypeT) = {
    val interfaceTemplata =
      fate.lookupNearestWithImpreciseName(profiler, CodeNameS("Result"), Set(TemplataLookupContext)).toList match {
        case List(it@InterfaceTemplata(_, _)) => it
        case _ => vfail()
      }
    val resultInterfaceRef =
      structTemplar.getInterfaceRef(temputs, range, interfaceTemplata, Vector(CoordTemplata(containedSuccessCoord), CoordTemplata(containedFailCoord)))
    val ownResultCoord = CoordT(OwnT, ReadwriteT, resultInterfaceRef)

    val okConstructorTemplata =
      fate.lookupNearestWithImpreciseName(profiler, CodeNameS("Ok"), Set(ExpressionLookupContext)).toList match {
        case List(ft@FunctionTemplata(_, _)) => ft
        case _ => vwat();
      }
    val okConstructor =
      delegate.evaluateTemplatedFunctionFromCallForPrototype(
        temputs, range, okConstructorTemplata, Vector(CoordTemplata(containedSuccessCoord), CoordTemplata(containedFailCoord)), Vector(ParamFilter(containedSuccessCoord, None))) match {
        case fff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, fff.toString))
        case EvaluateFunctionSuccess(p) => p
      }

    val errConstructorTemplata =
      fate.lookupNearestWithImpreciseName(profiler, CodeNameS("Err"), Set(ExpressionLookupContext)).toList match {
        case List(ft@FunctionTemplata(_, _)) => ft
        case _ => vwat();
      }
    val errConstructor =
      delegate.evaluateTemplatedFunctionFromCallForPrototype(
        temputs, range, errConstructorTemplata, Vector(CoordTemplata(containedSuccessCoord), CoordTemplata(containedFailCoord)), Vector(ParamFilter(containedFailCoord, None))) match {
        case fff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, fff.toString))
        case EvaluateFunctionSuccess(p) => p
      }

    (ownResultCoord, okConstructor, errConstructor)
  }

  private def maybeNarrowPermission(range: RangeS, innerExpr2: ReferenceExpressionTE, permission: PermissionP) = {
    (innerExpr2.result.reference.permission, permission) match {
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
      case sr @ StructTT(_) => {
        val structDef = temputs.lookupStruct(sr)
        vcheck(structDef.weakable, TookWeakRefOfNonWeakableError)
      }
      case ir @ InterfaceTT(_) => {
        val interfaceDef = temputs.lookupInterface(ir)
        vcheck(interfaceDef.weakable, TookWeakRefOfNonWeakableError)
      }
      case _ => vfail()
    }

    WeakAliasTE(expr)
  }

  // Borrow like the . does. If it receives an owning reference, itll make a temporary.
  // If it receives an owning address, that's fine, just borrowsoftload from it.
  // Rename this someday.
  private def dotBorrow(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      life: LocationInFunctionEnvironment,
      undecayedUnborrowedContainerExpr2: ExpressionT):
  (ReferenceExpressionTE) = {
    undecayedUnborrowedContainerExpr2 match {
      case a: AddressExpressionTE => {
        (localHelper.borrowSoftLoad(temputs, a))
      }
      case r: ReferenceExpressionTE => {
        val unborrowedContainerExpr2 = r// decaySoloPack(fate, life + 0, r)
        unborrowedContainerExpr2.result.reference.ownership match {
          case OwnT => localHelper.makeTemporaryLocal(temputs, fate, life + 1, unborrowedContainerExpr2)
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
      name: IFunctionDeclarationNameS,
      functionS: FunctionS):
  (ReferenceExpressionTE) = {

    val functionA = astronomizeLambda(temputs, fate, functionS)

    val closurestructTT =
      delegate.evaluateClosureStruct(temputs, fate.snapshot, range, name, functionA);
    val closureCoord =
      templataTemplar.pointifyKind(temputs, closurestructTT, OwnT)

    val constructExpr2 = makeClosureStructConstructExpression(temputs, fate, functionA.range, closurestructTT)
    vassert(constructExpr2.result.reference == closureCoord)
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

  private def newGlobalFunctionGroupExpression(env: IEnvironment, temputs: Temputs, name: IImpreciseNameS): ReferenceExpressionTE = {
    TemplarReinterpretTE(
      sequenceTemplar.makeEmptyTuple(env, temputs),
      CoordT(
        ShareT,
        ReadonlyT,
        OverloadSet(
          env,
          name,
          sequenceTemplar.makeTupleKind(env, temputs, Vector()))))
  }

  def evaluateBlockStatements(
    temputs: Temputs,
    startingFate: FunctionEnvironment,
    fate: FunctionEnvironmentBox,
    life: LocationInFunctionEnvironment,
    exprs: Vector[IExpressionSE]):
  (ReferenceExpressionTE, Set[CoordT]) = {
    blockTemplar.evaluateBlockStatements(temputs, startingFate, fate, life, exprs)
  }

  def translatePatternList(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    life: LocationInFunctionEnvironment,
    patterns1: Vector[AtomSP],
    patternInputExprs2: Vector[ReferenceExpressionTE]
  ): ReferenceExpressionTE = {
    patternTemplar.translatePatternList(
      temputs, fate, life, patterns1, patternInputExprs2,
      (temputs, fate, liveCaptureLocals) => VoidLiteralTE())
  }

  def astronomizeLambda(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    functionS: FunctionS):
  FunctionA = {
    val FunctionS(rangeS, nameS, attributesS, identifyingRunesS, runeToExplicitType, paramsS, maybeRetCoordRune, rulesS, bodyS) = functionS

    val runeSToPreKnownTypeA =
      runeToExplicitType ++
        paramsS.map(_.pattern.coordRune.get.rune -> CoordTemplataType).toMap
    val runeSToType =
      RuneTypeSolver.solve(
        opts.globalOptions.sanityCheck,
        opts.globalOptions.useOptimizedSolver,
        {
          // This is here because if we tried to look up this lambda struct, it wouldn't exist yet.
          // It's not an insurmountable problem, it will exist slightly later when we're inside StructTemplar,
          // but this workaround makes for a cleaner separation between FunctionTemplar and StructTemplar
          // at least for now.
          // If this proves irksome, consider rearranging FunctionTemplar and StructTemplar's steps in
          // evaluating lambdas.
          case LambdaStructImpreciseNameS(_) => CoordTemplataType
          case n => {
            vassertSome(fate.lookupNearestWithImpreciseName(profiler, n, Set(TemplataLookupContext))).tyype
          }
        },
        rangeS,
        false, rulesS, identifyingRunesS.map(_.rune), true, runeSToPreKnownTypeA) match {
        case Ok(t) => t
        case Err(e) => throw CompileErrorExceptionA(CouldntSolveRulesA(rangeS, e))
      }

    // Shouldnt fail because we got a complete solve on the rules
    val tyype = Scout.determineDenizenType(FunctionTemplataType, identifyingRunesS.map(_.rune), runeSToType).getOrDie()

    FunctionA(
      rangeS,
      nameS,
      attributesS ++ Vector(UserFunctionS),
      tyype,
      identifyingRunesS,
      runeSToType,
      paramsS,
      maybeRetCoordRune,
      rulesS.toVector,
      bodyS)
  }
}
