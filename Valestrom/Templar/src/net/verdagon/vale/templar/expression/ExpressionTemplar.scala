package net.verdagon.vale.templar.expression

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast.{LoadAsBorrowOrIfContainerIsPointerThenPointerP, LoadAsBorrowP, LoadAsP, LoadAsPointerP, LoadAsWeakP, MoveP, PermissionP, ReadonlyP, ReadwriteP, UseP}
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.rules.{EqualsSR, RuneParentEnvLookupSR, RuneUsage}
import net.verdagon.vale.scout.{RuneTypeSolver, Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.{ast, _}
import net.verdagon.vale.templar.ast.{AddressExpressionTE, AddressMemberLookupTE, ArgLookupTE, BlockTE, BorrowToPointerTE, BorrowToWeakTE, BreakTE, ConsecutorTE, ConstantBoolTE, ConstantFloatTE, ConstantIntTE, ConstantStrTE, ConstructTE, DestroyTE, ExpressionT, FunctionCallTE, IfTE, LetNormalTE, LocalLookupTE, LocationInFunctionEnvironment, MutateTE, NarrowPermissionTE, PointerToBorrowTE, PointerToWeakTE, ProgramT, PrototypeT, ReferenceExpressionTE, ReferenceMemberLookupTE, ReturnTE, RuntimeSizedArrayLookupTE, StaticSizedArrayLookupTE, TemplarReinterpretTE, VoidLiteralTE, WhileTE}
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.function.FunctionTemplar.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
import net.verdagon.vale.templar.names.{ArbitraryNameT, CitizenNameT, CitizenTemplateNameT, ClosureParamNameT, CodeVarNameT, FullNameT, IVarNameT, NameTranslator, RuneNameT, SelfNameT, TemplarBlockResultVarNameT, TemplarFunctionResultVarNameT, TemplarTemporaryVarNameT}
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
    containingNodeEnv: NodeEnvironment,
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
      nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
    (ReferenceExpressionTE, Set[CoordT]) = {
      ExpressionTemplar.this.evaluateAndCoerceToReferenceExpression(
        temputs, nenv, life, expr1)
    }

    override def dropSince(temputs: Temputs, startingNenv: NodeEnvironment, nenv: NodeEnvironmentBox, life: LocationInFunctionEnvironment, unresultifiedUndestructedExpressions: ReferenceExpressionTE): ReferenceExpressionTE = {
      ExpressionTemplar.this.dropSince(
        temputs, startingNenv, nenv, life, unresultifiedUndestructedExpressions)
    }
  })

  def evaluateAndCoerceToReferenceExpressions(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    exprs1: Vector[IExpressionSE]):
  (Vector[ReferenceExpressionTE], Set[CoordT]) = {
    val things =
      exprs1.zipWithIndex.map({ case (expr, index) =>
        evaluateAndCoerceToReferenceExpression(temputs, nenv, life + index, expr)
      })
    (things.map(_._1), things.map(_._2).flatten.toSet)
  }

  private def evaluateLookupForLoad(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    range: RangeS,
    name: IVarNameT,
    targetOwnership: LoadAsP):
  (Option[ExpressionT]) = {
    evaluateAddressibleLookup(temputs, nenv, range, name) match {
      case Some(x) => {
        val thing = localHelper.softLoad(nenv, range, x, targetOwnership)
        (Some(thing))
      }
      case None => {
        nenv.lookupNearestWithName(profiler, name, Set(TemplataLookupContext)) match {
          case Some(IntegerTemplata(num)) => (Some(ConstantIntTE(num, 32)))
          case Some(BooleanTemplata(bool)) => (Some(ConstantBoolTE(bool)))
          case None => (None)
        }
      }
    }
  }

  private def evaluateAddressibleLookupForMutate(
      temputs: Temputs,
      nenv: NodeEnvironmentBox,
      range: RangeS,
      nameA: IVarNameS):
  Option[AddressExpressionTE] = {
    nenv.getVariable(NameTranslator.translateVarNameStep(nameA)) match {
      case Some(alv @ AddressibleLocalVariableT(_, _, reference)) => {
        Some(LocalLookupTE(range, alv))
      }
      case Some(rlv @ ReferenceLocalVariableT(id, _, reference)) => {
        Some(LocalLookupTE(range, rlv))
      }
      case Some(AddressibleClosureVariableT(id, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) BorrowT else ShareT
        val closuredVarsStructRefPermission = if (mutability == MutableT) ReadwriteT else ReadonlyT // See LHRSP
        val closuredVarsStructRefRef = CoordT(ownership, closuredVarsStructRefPermission, closuredVarsStructRef)
        val name2 = nenv.fullName.addStep(ClosureParamNameT())
        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookupTE(
              range,
              ReferenceLocalVariableT(name2, FinalT, closuredVarsStructRefRef)))

        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == id))

        val index = closuredVarsStructDef.members.indexWhere(_.name == id.last)
//        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership
        val lookup = AddressMemberLookupTE(range, borrowExpr, id, tyype, variability)
        Some(lookup)
      }
      case Some(ReferenceClosureVariableT(varName, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) BorrowT else ShareT
        val closuredVarsStructRefPermission = if (mutability == MutableT) ReadwriteT else ReadonlyT // See LHRSP
        val closuredVarsStructRefCoord = CoordT(ownership, closuredVarsStructRefPermission, closuredVarsStructRef)
//        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookupTE(
              range,
              ReferenceLocalVariableT(nenv.fullName.addStep(ClosureParamNameT()), FinalT, closuredVarsStructRefCoord)))
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
    nenv: NodeEnvironmentBox,
    range: RangeS,
    name2: IVarNameT):
  Option[AddressExpressionTE] = {
    nenv.getVariable(name2) match {
      case Some(alv @ AddressibleLocalVariableT(varId, variability, reference)) => {
        vassert(!nenv.unstackifieds.contains(varId))
        Some(LocalLookupTE(range, alv))
      }
      case Some(rlv @ ReferenceLocalVariableT(varId, variability, reference)) => {
        if (nenv.unstackifieds.contains(varId)) {
          throw CompileErrorExceptionT(CantUseUnstackifiedLocal(range, varId.last))
        }
        Some(LocalLookupTE(range, rlv))
      }
      case Some(AddressibleClosureVariableT(id, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) BorrowT else ShareT
        val closuredVarsStructRefPermission = if (mutability == MutableT) ReadwriteT else ReadonlyT // See LHRSP
        val closuredVarsStructRefRef = CoordT(ownership, closuredVarsStructRefPermission, closuredVarsStructRef)
        val closureParamVarName2 = nenv.fullName.addStep(ClosureParamNameT())

        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookupTE(
              range,
              ReferenceLocalVariableT(closureParamVarName2, FinalT, closuredVarsStructRefRef)))
        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == id))

        val index = closuredVarsStructDef.members.indexWhere(_.name == id.last)
        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership
        val lookup = ast.AddressMemberLookupTE(range, borrowExpr, id, tyype, variability)
        Some(lookup)
      }
      case Some(ReferenceClosureVariableT(varName, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) BorrowT else ShareT
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
              ReferenceLocalVariableT(nenv.fullName.addStep(ClosureParamNameT()), FinalT, closuredVarsStructRefCoord)))

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
    nenv: NodeEnvironmentBox,
      range: RangeS,
      closureStructRef: StructTT):
  (ReferenceExpressionTE) = {
    val closureStructDef = temputs.lookupStruct(closureStructRef);
    // Note, this is where the unordered closuredNames set becomes ordered.
    val lookupExpressions2 =
      closureStructDef.members.map({
        case StructMemberT(memberName, variability, tyype) => {
          val lookup =
            evaluateAddressibleLookup(temputs, nenv, range, memberName) match {
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
    nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
  (ReferenceExpressionTE, Set[CoordT]) = {
    val (expr2, returnsFromExpr) =
      evaluate(temputs, nenv, life, expr1)
    expr2 match {
      case r : ReferenceExpressionTE => {
        (r, returnsFromExpr)
      }
      case a : AddressExpressionTE => {
        val expr = coerceToReferenceExpression(nenv, a)
        (expr, returnsFromExpr)
      }
      case _ => vwat()
    }
  }

  def coerceToReferenceExpression(nenv: NodeEnvironmentBox, expr2: ExpressionT):
  (ReferenceExpressionTE) = {
    expr2 match {
      case r : ReferenceExpressionTE => (r)
      case a : AddressExpressionTE => localHelper.softLoad(nenv, a.range, a, UseP)
    }
  }

  private def evaluateExpectedAddressExpression(
      temputs: Temputs,
      nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
  (AddressExpressionTE, Set[CoordT]) = {
    val (expr2, returns) =
      evaluate(temputs, nenv, life, expr1)
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
    nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
  (ExpressionT, Set[CoordT]) = {
    profiler.newProfile(expr1.getClass.getSimpleName, nenv.fullName.toString, () => {
      expr1 match {
        case VoidSE(range) => (VoidLiteralTE(), Set())
        case ConstantIntSE(range, i, bits) => (ConstantIntTE(i, bits), Set())
        case ConstantBoolSE(range, i) => (ConstantBoolTE(i), Set())
        case ConstantStrSE(range, s) => (ConstantStrTE(s), Set())
        case ConstantFloatSE(range, f) => (ConstantFloatTE(f), Set())
        case ArgLookupSE(range, index) => {
          val paramCoordRune = nenv.function.params(index).pattern.coordRune.get
          val paramCoordTemplata = vassertOne(nenv.lookupNearestWithImpreciseName(profiler, RuneNameS(paramCoordRune.rune), Set(TemplataLookupContext)))
          val CoordTemplata(paramCoord) = paramCoordTemplata
          vassert(nenv.functionEnvironment.fullName.last.parameters(index) == paramCoord)
          (ArgLookupTE(index, paramCoord), Set())
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, maybeTemplateArgs, callableTargetOwnership), argsExprs1) => {
//          vassert(callableTargetOwnership == PointConstraintP(Some(ReadonlyP)))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, nenv, life + 0, argsExprs1)
          val callExpr2 =
            callTemplar.evaluatePrefixCall(
              temputs,
              nenv,
              life + 1,
              range,
              newGlobalFunctionGroupExpression(nenv.snapshot, temputs, name),
              rules.toVector,
              maybeTemplateArgs.toArray.flatMap(_.map(_.rune)),
              argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, templateArgTemplexesS, callableTargetOwnership), argsExprs1) => {
//          vassert(callableTargetOwnership == PointConstraintP(None))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, nenv, life + 0, argsExprs1)
          val callExpr2 =
            callTemplar.evaluatePrefixCall(
              temputs,
              nenv,
              life + 1,
              range,
              newGlobalFunctionGroupExpression(nenv.snapshot, temputs, name),
              rules.toVector,
              templateArgTemplexesS.toArray.flatMap(_.map(_.rune)),
              argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, templateArgs, callableTargetOwnership), argsExprs1) => {
          vassert(callableTargetOwnership == LoadAsPointerP(None))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, nenv, life, argsExprs1)
          val callExpr2 =
            callTemplar.evaluateNamedPrefixCall(temputs, nenv, range, name, rules.toVector, templateArgs.toVector.flatten.map(_.rune), argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, templateArgs, callableTargetOwnership), argsPackExpr1) => {
//          vassert(callableTargetOwnership == PointConstraintP(Some(ReadonlyP)))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, nenv, life, argsPackExpr1)
          val callExpr2 =
            callTemplar.evaluateNamedPrefixCall(temputs, nenv, range, name, rules.toVector, Vector(), argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, callableExpr1, argsExprs1) => {
          val (undecayedCallableExpr2, returnsFromCallable) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life + 0, callableExpr1);
          val decayedCallableExpr2 =
            localHelper.maybeBorrowSoftLoad(temputs, undecayedCallableExpr2)
          val decayedCallableReferenceExpr2 =
            coerceToReferenceExpression(nenv, decayedCallableExpr2)
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, nenv, life + 1, argsExprs1)
          val functionPointerCall2 =
            callTemplar.evaluatePrefixCall(temputs, nenv, life + 2, range, decayedCallableReferenceExpr2, Vector(), Array(), argsExprs2)
          (functionPointerCall2, returnsFromCallable ++ returnsFromArgs)
        }

        case OwnershippedSE(range, sourceSE, loadAsP) => {
          val (sourceTE, returnsFromInner) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life + 0, sourceSE);
          val resultExpr2 =
            sourceTE.result.underlyingReference.ownership match {
              case OwnT => {
                loadAsP match {
                  case MoveP => {
                    // this can happen if we put a ^ on an owning reference. No harm, let it go.
                    sourceTE
                  }
                  case LoadAsBorrowP(maybePermission) => {
                    val expr =
                      localHelper.makeTemporaryLocal(temputs, nenv, life + 1, sourceTE, BorrowT)
                    maybePermission match {
                      case None => expr
                      case Some(permission) => maybeNarrowPermission(range, expr, permission)
                    }
                  }
                  case LoadAsPointerP(maybePermission) => {
                    val expr =
                      localHelper.makeTemporaryLocal(temputs, nenv, life + 1, sourceTE, PointerT)
                    maybePermission match {
                      case None => expr
                      case Some(permission) => maybeNarrowPermission(range, expr, permission)
                    }
                  }
                  case LoadAsBorrowOrIfContainerIsPointerThenPointerP(maybePermission) => {
                    val targetOwnership =
                      sourceTE.result.reference.ownership match {
                        case PointerT => PointerT
                        case ShareT => ShareT
                        case OwnT | BorrowT => BorrowT
                        case WeakT => vimpl()
                      }
                    val expr =
                      localHelper.makeTemporaryLocal(temputs, nenv, life + 1, sourceTE, targetOwnership)
                    maybePermission match {
                      case None => expr
                      case Some(permission) => maybeNarrowPermission(range, expr, permission)
                    }
                  }
                  case LoadAsWeakP(permission) => {
                    val expr = localHelper.makeTemporaryLocal(temputs, nenv, life + 3, sourceTE, BorrowT)
                    weakAlias(temputs, maybeNarrowPermission(range, expr, permission))
                  }
                  case UseP => vcurious()
                }
              }
              case BorrowT => {
                loadAsP match {
                  case MoveP => vcurious() // Can we even coerce to an owning reference?
                  case LoadAsPointerP(None) => BorrowToPointerTE(sourceTE)
                  case LoadAsPointerP(Some(permission)) => BorrowToPointerTE(maybeNarrowPermission(range, sourceTE, permission))
                  case LoadAsBorrowP(None) | LoadAsBorrowOrIfContainerIsPointerThenPointerP(None) => sourceTE
                  case LoadAsBorrowP(Some(permission)) => maybeNarrowPermission(range, sourceTE, permission)
                  case LoadAsBorrowOrIfContainerIsPointerThenPointerP(Some(permission)) => maybeNarrowPermission(range, sourceTE, permission)
                  case LoadAsWeakP(permission) => weakAlias(temputs, maybeNarrowPermission(range, sourceTE, permission))
                  case UseP => sourceTE
                }
              }
              case PointerT => {
                loadAsP match {
                  case MoveP => vcurious() // Can we even coerce to an owning reference?
                  case LoadAsBorrowP(None) => PointerToBorrowTE(sourceTE)
                  case LoadAsBorrowP(Some(permission)) => PointerToBorrowTE(maybeNarrowPermission(range, sourceTE, permission))
                  case LoadAsPointerP(None) | LoadAsBorrowOrIfContainerIsPointerThenPointerP(None) => sourceTE
                  case LoadAsPointerP(Some(permission)) => maybeNarrowPermission(range, sourceTE, permission)
                  case LoadAsBorrowOrIfContainerIsPointerThenPointerP(Some(permission)) => maybeNarrowPermission(range, sourceTE, permission)
                  case LoadAsWeakP(permission) => weakAlias(temputs, maybeNarrowPermission(range, sourceTE, permission))
                  case UseP => sourceTE
                }
              }
              case WeakT => {
                loadAsP match {
                  case MoveP => vcurious() // Can we even coerce to an owning reference?
                  case LoadAsPointerP(permission) => vfail() // Need to call lock() to do this
                  case LoadAsWeakP(permission) => maybeNarrowPermission(range, sourceTE, permission)
                  case UseP => sourceTE
                }
              }
              case ShareT => {
                loadAsP match {
                  case MoveP => {
                    // Allow this, we can do ^ on a share ref, itll just give us a share ref.
                    sourceTE
                  }
                  case LoadAsPointerP(_) | LoadAsBorrowP(_) | LoadAsBorrowOrIfContainerIsPointerThenPointerP(_) => {
                    // Allow this, we can do & on a share ref, itll just give us a share ref.
                    sourceTE
                  }
                  case LoadAsWeakP(permission) => {
                    vfail()
                  }
                  case UseP => sourceTE
                }
              }
            }
          (resultExpr2, returnsFromInner)
        }
        case LocalLoadSE(range, nameA, targetOwnership) => {
          val name = NameTranslator.translateVarNameStep(nameA)
          val lookupExpr1 =
            evaluateLookupForLoad(temputs, nenv, range, name, targetOwnership) match {
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
            nenv.lookupAllWithImpreciseName(profiler, name, Set(ExpressionLookupContext)) match {
              case Vector(BooleanTemplata(value)) => ConstantBoolTE(value)
              case Vector(IntegerTemplata(value)) => ConstantIntTE(value, 32)
              case templatas if templatas.nonEmpty && templatas.collect({ case FunctionTemplata(_, _) => case ExternFunctionTemplata(_) => }).size == templatas.size => {
                if (targetOwnership == MoveP) {
                  throw CompileErrorExceptionT(CantMoveFromGlobal(range, "Can't move from globals. Name: " + name))
                }
                newGlobalFunctionGroupExpression(nenv.snapshot, temputs, name)
              }
              case things if things.size > 1 => {
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Found too many different things named \"" + name + "\" in env:\n" + things.map("\n" + _)))
              }
              case Vector() => {
                //              println("members: " + nenv.getAllTemplatasWithName(name, Set(ExpressionLookupContext, TemplataLookupContext)))
                throw CompileErrorExceptionT(CouldntFindIdentifierToLoadT(range, name))
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldn't find anything named \"" + name + "\" in env:\n" + nenv))
              }
            }
          (templataFromEnv, Set())
        }
        case LocalMutateSE(range, name, sourceExpr1) => {
          val destinationExpr2 =
            evaluateAddressibleLookupForMutate(temputs, nenv, range, name) match {
              case None => {
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldnt find " + name))
              }
              case Some(x) => x
            }
          val (unconvertedSourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life, sourceExpr1)

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
            convertHelper.convert(nenv.snapshot, temputs, range, unconvertedSourceExpr2, destinationExpr2.result.reference);

          val mutate2 = MutateTE(destinationExpr2, convertedSourceExpr2);
          (mutate2, returnsFromSource)
        }
        case ExprMutateSE(range, destinationExpr1, sourceExpr1) => {
          val (unconvertedSourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life + 0, sourceExpr1)
          val (destinationExpr2, returnsFromDestination) =
            evaluateExpectedAddressExpression(temputs, nenv, life + 1, destinationExpr1)
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
              case RuntimeSizedArrayLookupTE(range, arrayExpr, arrayType, _, _, _) => {
                throw CompileErrorExceptionT(CantMutateFinalElement(range, arrayExpr.result.reference))
              }
              case StaticSizedArrayLookupTE(range, arrayExpr, arrayType, _, _, _) => {
                throw CompileErrorExceptionT(CantMutateFinalElement(range, arrayExpr.result.reference))
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
            convertHelper.convert(nenv.snapshot, temputs, range, unconvertedSourceExpr2, destinationExpr2.result.reference);

          val mutate2 = MutateTE(destinationExpr2, convertedSourceExpr2);
          (mutate2, returnsFromSource ++ returnsFromDestination)
        }
        case OutsideLoadSE(range, rules, name, templateArgs1, targetOwnership) => {
          // So far, we only allow these when they're immediately called like functions
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Raw template specified lookups unimplemented!"))
        }
        case IndexSE(range, containerExpr1, indexExpr1) => {
          val (unborrowedContainerExpr2, returnsFromContainerExpr) =
            evaluate(temputs, nenv, life + 0, containerExpr1);
          val containerExpr2 =
            dotBorrow(temputs, nenv, life + 1, unborrowedContainerExpr2)

          val (indexExpr2, returnsFromIndexExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life + 2, indexExpr1);

          val exprTemplata =
            containerExpr2.result.reference.kind match {
              case rsa @ RuntimeSizedArrayTT(_, _) => {
                arrayTemplar.lookupInUnknownSizedArray(range, containerExpr2, indexExpr2, rsa)
              }
              case at@StaticSizedArrayTT(_, _, _, _) => {
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
            evaluate(temputs, nenv, life + 0, containerExpr1)
          val containerExpr2 =
            dotBorrow(temputs, nenv, life + 1, unborrowedContainerExpr2)

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
              case as@StaticSizedArrayTT(_, _, _, _) => {
                if (memberNameStr.forall(Character.isDigit)) {
                  arrayTemplar.lookupInStaticSizedArray(range, containerExpr2, ConstantIntTE(memberNameStr.toInt, 32), as)
                } else {
                  throw CompileErrorExceptionT(RangedInternalErrorT(range, "Sequence has no member named " + memberNameStr))
                }
              }
              case at@RuntimeSizedArrayTT(_, _) => {
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
          val callExpr2 = evaluateClosure(temputs, nenv, range, name, functionS)
          (callExpr2, Set())
        }
        case TupleSE(range, elements1) => {
          val (exprs2, returnsFromElements) =
            evaluateAndCoerceToReferenceExpressions(temputs, nenv, life + 0, elements1);

          // would we need a sequence templata? probably right?
          val expr2 = sequenceTemplar.evaluate(nenv.snapshot, temputs, exprs2)
          (expr2, returnsFromElements)
        }
        case StaticArrayFromValuesSE(range, rules, maybeElementTypeRuneA, mutabilityRune, variabilityRune, sizeRuneA, elements1) => {
          val (exprs2, returnsFromElements) =
            evaluateAndCoerceToReferenceExpressions(temputs, nenv, life, elements1);
          // would we need a sequence templata? probably right?
          val expr2 =
            arrayTemplar.evaluateStaticSizedArrayFromValues(
              temputs, nenv.snapshot, range, rules.toVector, maybeElementTypeRuneA.map(_.rune), sizeRuneA.rune, mutabilityRune.rune, variabilityRune.rune, exprs2)
          (expr2, returnsFromElements)
        }
        case StaticArrayFromCallableSE(range, rules, maybeElementTypeRune, maybeMutabilityRune, maybeVariabilityRune, sizeRuneA, callableAE) => {
          val (callableTE, returnsFromCallable) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life, callableAE);
          val expr2 =
            arrayTemplar.evaluateStaticSizedArrayFromCallable(
              temputs, nenv.snapshot, range, rules.toVector, maybeElementTypeRune.map(_.rune), sizeRuneA.rune, maybeMutabilityRune.rune, maybeVariabilityRune.rune, callableTE)
          (expr2, returnsFromCallable)
        }
        case RuntimeArrayFromCallableSE(range, rulesA, maybeElementTypeRune, mutabilityRune, sizeAE, callableAE) => {
          val (sizeTE, returnsFromSize) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life + 0, sizeAE);
          val (callableTE, returnsFromCallable) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life + 1, callableAE);

          val expr2 =
            arrayTemplar.evaluateRuntimeSizedArrayFromCallable(
              temputs, nenv.snapshot, range, rulesA.toVector, maybeElementTypeRune.map(_.rune), mutabilityRune.rune, sizeTE, callableTE)
          (expr2, returnsFromSize ++ returnsFromCallable)
        }
        case LetSE(range, rulesA, pattern, sourceExpr1) => {
          val (sourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life + 0, sourceExpr1)

          val runeToInitiallyKnownType = PatternSUtils.getRuneTypesFromPattern(pattern)
          val runeToType =
            RuneTypeSolver.solve(
                opts.globalOptions.sanityCheck,
                opts.globalOptions.useOptimizedSolver,
                nameS => vassertOne(nenv.lookupNearestWithImpreciseName(profiler, nameS, Set(TemplataLookupContext))).tyype,
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
              temputs, nenv, life + 1, rulesA.toVector, runeToType, pattern, sourceExpr2,
              (temputs, nenv, life, liveCaptureLocals) => VoidLiteralTE())

          (resultTE, returnsFromSource)
        }
        case r @ RuneLookupSE(range, runeA) => {
          val templata = vassertOne(nenv.lookupNearestWithImpreciseName(profiler, RuneNameS(runeA), Set(TemplataLookupContext)))
          templata match {
            case IntegerTemplata(value) => (ConstantIntTE(value, 32), Set())
            case PrototypeTemplata(value) => {
              val tinyEnv =
                nenv.functionEnvironment.makeChildNodeEnvironment(r, life)
                  .addEntries(Vector(ArbitraryNameT() -> TemplataEnvEntry(PrototypeTemplata(value))))
              val expr = newGlobalFunctionGroupExpression(tinyEnv, temputs, ArbitraryNameS())
              (expr, Set())
            }
          }
        }
        case IfSE(range, conditionSE, thenBodySE, elseBodySE) => {
          // We make a block for the if-statement which contains its condition (the "if block"),
          // and then two child blocks under that for the then and else blocks.
          // The then and else blocks are children of the block which contains the condition
          // so they can access any locals declared by the condition.

          val (conditionExpr, returnsFromCondition) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life + 1, conditionSE)
          if (conditionExpr.result.reference != CoordT(ShareT, ReadonlyT, BoolT())) {
            throw CompileErrorExceptionT(IfConditionIsntBoolean(conditionSE.range, conditionExpr.result.reference))
          }


          val thenFate = NodeEnvironmentBox(nenv.makeChild(thenBodySE))

          val (thenExpressionsWithResult, thenReturnsFromExprs) =
            evaluateBlockStatements(temputs, thenFate.snapshot, thenFate, life + 2, thenBodySE)
          val uncoercedThenBlock2 = BlockTE(thenExpressionsWithResult)

          val thenUnstackifiedAncestorLocals = thenFate.snapshot.getEffectsSince(nenv.snapshot)
          val thenContinues = uncoercedThenBlock2.result.reference.kind != NeverT()

          val elseFate = NodeEnvironmentBox(nenv.makeChild(elseBodySE))

          val (elseExpressionsWithResult, elseReturnsFromExprs) =
            evaluateBlockStatements(temputs, elseFate.snapshot, elseFate, life + 3, elseBodySE)
          val uncoercedElseBlock2 = BlockTE(elseExpressionsWithResult)

          val elseUnstackifiedAncestorLocals = elseFate.snapshot.getEffectsSince(nenv.snapshot)
          val elseContinues = uncoercedElseBlock2.result.reference.kind != NeverT()

          val commonType =
            (uncoercedThenBlock2.kind, uncoercedElseBlock2.kind) match {
              case (NeverT(), NeverT()) => uncoercedThenBlock2.result.reference
              case (NeverT(), _) => uncoercedElseBlock2.result.reference
              case (_, NeverT()) => uncoercedThenBlock2.result.reference
              case (a, b) if a == b => uncoercedThenBlock2.result.reference
              case (a : CitizenRefT, b : CitizenRefT) => {
                val aAncestors = ancestorHelper.getAncestorInterfaces(temputs, a).keys.toSet
                val bAncestors = ancestorHelper.getAncestorInterfaces(temputs, b).keys.toSet
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
                throw CompileErrorExceptionT(CantReconcileBranchesResults(range, uncoercedThenBlock2.result.reference, uncoercedElseBlock2.result.reference))
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
            thenUnstackifiedAncestorLocals.foreach(nenv.markLocalUnstackified)
          } else {
            // One of them continues and the other does not.
            if (thenContinues) {
              thenUnstackifiedAncestorLocals.foreach(nenv.markLocalUnstackified)
            } else if (elseContinues) {
              elseUnstackifiedAncestorLocals.foreach(nenv.markLocalUnstackified)
            } else vfail()
          }


          val ifBlockUnstackifiedAncestorLocals = nenv.snapshot.getEffectsSince(nenv.snapshot)
          ifBlockUnstackifiedAncestorLocals.foreach(nenv.markLocalUnstackified)


          (ifExpr2, returnsFromCondition ++ thenReturnsFromExprs ++ elseReturnsFromExprs)
        }
        case w @ WhileSE(range, bodySE) => {
          // We make a block for the while-statement which contains its condition (the "if block"),
          // and the body block, so they can access any locals declared by the condition.

          // See BEAFB for why we make a new environment for the While
          val loopNenv = nenv.makeChild(w)

          val loopBlockFate = NodeEnvironmentBox(loopNenv.makeChild(bodySE))
          val (bodyExpressionsWithResult, bodyReturnsFromExprs) =
            evaluateBlockStatements(temputs, loopBlockFate.snapshot, loopBlockFate, life + 1, bodySE)
          val uncoercedBodyBlock2 = BlockTE(bodyExpressionsWithResult)

          val bodyUnstackifiedAncestorLocals = loopBlockFate.snapshot.getEffectsSince(nenv.snapshot)
          if (bodyUnstackifiedAncestorLocals.nonEmpty) {
            throw CompileErrorExceptionT(CantUnstackifyOutsideLocalFromInsideWhile(range, bodyUnstackifiedAncestorLocals.head.last))
          }

          val loopExpr2 = WhileTE(uncoercedBodyBlock2)
          (loopExpr2, /*returnsFromCondition ++*/ bodyReturnsFromExprs)
        }
        case m @ MapSE(range, bodySE) => {
          // Preprocess the entire loop once, to predict what its result type
          // will be.
          // We can't just use this, because any returns inside won't drop
          // the temporary list.
          val elementRefT =
            {
              // See BEAFB for why we make a new environment for the While
              val loopNenv = nenv.makeChild(m)
              val loopBlockFate = NodeEnvironmentBox(loopNenv.makeChild(bodySE))
              val (bodyExpressionsWithResult, _) =
                evaluateBlockStatements(temputs, loopBlockFate.snapshot, loopBlockFate, life + 1, bodySE)
              bodyExpressionsWithResult.result.reference
            }

          // Now that we know the result type, let's make a temporary list.

          val callEnv =
            nenv.snapshot
              .copy(templatas =
                nenv.snapshot.templatas
                  .addEntry(RuneNameT(SelfRuneS()), TemplataEnvEntry(CoordTemplata(elementRefT))))
          val makeListTE =
            callTemplar.evaluatePrefixCall(
              temputs,
              nenv,
              life + 1,
              range,
              newGlobalFunctionGroupExpression(callEnv, temputs, CodeNameS("List")),
              Vector(RuneParentEnvLookupSR(range, RuneUsage(range, SelfRuneS()))),
              Array(SelfRuneS()),
              Vector())

          val listLocal =
            localHelper.makeTemporaryLocal(
              nenv, life + 2, makeListTE.result.reference)
          val letListTE =
            LetNormalTE(listLocal, makeListTE)

          val (loopTE, returnsFromLoop) =
            {
              // See BEAFB for why we make a new environment for the While
              val loopNenv = nenv.makeChild(m)

              val loopBlockFate = NodeEnvironmentBox(loopNenv.makeChild(bodySE))
              val (userBodyTE, bodyReturnsFromExprs) =
                evaluateBlockStatements(temputs, loopBlockFate.snapshot, loopBlockFate, life + 1, bodySE)

              // We store the iteration result in a local because the loop body will have
              // breaks, and we can't have a BreakTE inside a FunctionCallTE, see BRCOBS.
              val iterationResultLocal =
                localHelper.makeTemporaryLocal(
                  nenv, life + 3, userBodyTE.result.reference)
              val letIterationResultTE =
                LetNormalTE(iterationResultLocal, userBodyTE)

              val addCall =
                callTemplar.evaluatePrefixCall(
                  temputs,
                  nenv,
                  life + 4,
                  range,
                  newGlobalFunctionGroupExpression(callEnv, temputs, CodeNameS("add")),
                  Vector(),
                  Array(),
                  Vector(
                    localHelper.borrowSoftLoad(
                      temputs,
                      LocalLookupTE(
                        range,
                        listLocal)),
                    localHelper.unletLocal(nenv, iterationResultLocal)))
              val bodyTE = BlockTE(Templar.consecutive(Vector(letIterationResultTE, addCall)))

              val bodyUnstackifiedAncestorLocals = loopBlockFate.snapshot.getEffectsSince(nenv.snapshot)
              if (bodyUnstackifiedAncestorLocals.nonEmpty) {
                throw CompileErrorExceptionT(CantUnstackifyOutsideLocalFromInsideWhile(range, bodyUnstackifiedAncestorLocals.head.last))
              }

              val whileTE = WhileTE(bodyTE)
              (whileTE, bodyReturnsFromExprs)
            }

          val unletListTE =
            localHelper.unletLocal(nenv, listLocal)

          val combinedTE =
            Templar.consecutive(Vector(letListTE, loopTE, unletListTE))

          (combinedTE, returnsFromLoop)
        }
        case ConsecutorSE(exprsSE) => {

          val (initExprsTE, initReturnsUnflattened) =
            exprsSE.init.zipWithIndex.map({ case (exprSE, index) =>
              val (undroppedExprTE, returns) =
                evaluateAndCoerceToReferenceExpression(temputs, nenv, life + index, exprSE)
              val exprTE =
                undroppedExprTE.result.kind match {
                  case VoidT() => undroppedExprTE
                  case _ => destructorTemplar.drop(nenv.snapshot, temputs, undroppedExprTE)
                }
              (exprTE, returns)
            }).unzip

          val (lastExprTE, lastReturns) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life + (exprsSE.size - 1), exprsSE.last)

          (Templar.consecutive(initExprsTE :+ lastExprTE), (initReturnsUnflattened.flatten ++ lastReturns).toSet)
        }
        case b @ BlockSE(range, locals, _) => {
          val childEnvironment = NodeEnvironmentBox(nenv.makeChild(b))

          val (expressionsWithResult, returnsFromExprs) =
            evaluateBlockStatements(temputs, childEnvironment.snapshot, childEnvironment, life, b)
          val block2 = BlockTE(expressionsWithResult)

          val unstackifiedAncestorLocals = childEnvironment.snapshot.getEffectsSince(nenv.snapshot)
          unstackifiedAncestorLocals.foreach(nenv.markLocalUnstackified)

          (block2, returnsFromExprs)
        }
        case DestructSE(range, innerAE) => {
          val (innerExpr2, returnsFromArrayExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life + 0, innerAE);

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
                        localHelper.makeTemporaryLocal(nenv, life + 1 + index, reference)
                      }
                      case _ => vfail()
                    }
                  }))
              }
              case interfaceTT @ InterfaceTT(_) => {
                destructorTemplar.drop(nenv.snapshot, temputs, innerExpr2)
              }
              case _ => vfail("Can't destruct type: " + innerExpr2.kind)
            }
          (destroy2, returnsFromArrayExpr)
        }
        case ReturnSE(range, innerExprA) => {
          val (uncastedInnerExpr2, returnsFromInnerExpr) =
            evaluateAndCoerceToReferenceExpression(temputs, nenv, life + 0, innerExprA);

          val innerExpr2 =
            nenv.maybeReturnType match {
              case None => (uncastedInnerExpr2)
              case Some(returnType) => {
                templataTemplar.isTypeConvertible(temputs, uncastedInnerExpr2.result.reference, returnType) match {
                  case (false) => {
                    throw CompileErrorExceptionT(
                      CouldntConvertForReturnT(range, returnType, uncastedInnerExpr2.result.reference))
                  }
                  case (true) => {
                    convertHelper.convert(nenv.snapshot, temputs, range, uncastedInnerExpr2, returnType)
                  }
                }
              }
            }

          val allLocals = nenv.getAllLocals()
          val unstackifiedLocals = nenv.getAllUnstackifiedLocals()
          val variablesToDestruct = allLocals.filter(x => !unstackifiedLocals.contains(x.id))
          val reversedVariablesToDestruct = variablesToDestruct.reverse

          val resultVarId = nenv.fullName.addStep(TemplarFunctionResultVarNameT())
          val resultVariable = ReferenceLocalVariableT(resultVarId, FinalT, innerExpr2.result.reference)
          val resultLet = LetNormalTE(resultVariable, innerExpr2)
          nenv.addVariable(resultVariable)

          val destructExprs =
            localHelper.unletAll(temputs, nenv, reversedVariablesToDestruct)

          val getResultExpr =
            localHelper.unletLocal(nenv, resultVariable)

          val consecutor = Templar.consecutive(Vector(resultLet) ++ destructExprs ++ Vector(getResultExpr))

          val returns = returnsFromInnerExpr + innerExpr2.result.reference

          (ReturnTE(consecutor), returns)
        }
        case BreakSE(range) => {
          // See BEAFB, we need to find the nearest while to see local since then.
          nenv.nearestLoopEnv() match {
            case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Using break while not inside loop!"))
            case Some((whileNenv, _)) => {
              val dropsTE =
                dropSince(temputs, whileNenv, nenv, life, VoidLiteralTE())
              val dropsAndBreakTE = Templar.consecutive(Vector(dropsTE, BreakTE()))
              (dropsAndBreakTE, Set())
            }
          }
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

  def getOption(temputs: Temputs, nenv: FunctionEnvironment, range: RangeS, containedCoord: CoordT):
  (CoordT, PrototypeT, PrototypeT) = {
    val interfaceTemplata =
      nenv.lookupNearestWithImpreciseName(profiler, CodeNameS("Opt"), Set(TemplataLookupContext)).toList match {
        case List(it@InterfaceTemplata(_, _)) => it
        case _ => vfail()
      }
    val optInterfaceRef =
      structTemplar.getInterfaceRef(temputs, range, interfaceTemplata, Vector(CoordTemplata(containedCoord)))
    val ownOptCoord = CoordT(OwnT, ReadwriteT, optInterfaceRef)

    val someConstructorTemplata =
      nenv.lookupNearestWithImpreciseName(profiler, CodeNameS("Some"), Set(ExpressionLookupContext)).toList match {
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
      nenv.lookupNearestWithImpreciseName(profiler, CodeNameS("None"), Set(ExpressionLookupContext)).toList match {
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

  def getResult(temputs: Temputs, nenv: FunctionEnvironment, range: RangeS, containedSuccessCoord: CoordT, containedFailCoord: CoordT):
  (CoordT, PrototypeT, PrototypeT) = {
    val interfaceTemplata =
      nenv.lookupNearestWithImpreciseName(profiler, CodeNameS("Result"), Set(TemplataLookupContext)).toList match {
        case List(it@InterfaceTemplata(_, _)) => it
        case _ => vfail()
      }
    val resultInterfaceRef =
      structTemplar.getInterfaceRef(temputs, range, interfaceTemplata, Vector(CoordTemplata(containedSuccessCoord), CoordTemplata(containedFailCoord)))
    val ownResultCoord = CoordT(OwnT, ReadwriteT, resultInterfaceRef)

    val okConstructorTemplata =
      nenv.lookupNearestWithImpreciseName(profiler, CodeNameS("Ok"), Set(ExpressionLookupContext)).toList match {
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
      nenv.lookupNearestWithImpreciseName(profiler, CodeNameS("Err"), Set(ExpressionLookupContext)).toList match {
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

    expr.result.reference.ownership match {
      case BorrowT => BorrowToWeakTE(expr)
      case PointerT => PointerToWeakTE(expr)
      case other => vwat(other)
    }
  }

  // Borrow like the . does. If it receives an owning reference, itll make a temporary.
  // If it receives an owning address, that's fine, just borrowsoftload from it.
  // Rename this someday.
  private def dotBorrow(
      temputs: Temputs,
      nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      undecayedUnborrowedContainerExpr2: ExpressionT):
  (ReferenceExpressionTE) = {
    undecayedUnborrowedContainerExpr2 match {
      case a: AddressExpressionTE => {
        (localHelper.borrowSoftLoad(temputs, a))
      }
      case r: ReferenceExpressionTE => {
        val unborrowedContainerExpr2 = r// decaySoloPack(nenv, life + 0, r)
        unborrowedContainerExpr2.result.reference.ownership match {
          case OwnT => localHelper.makeTemporaryLocal(temputs, nenv, life + 1, unborrowedContainerExpr2, BorrowT)
          case PointerT | BorrowT | ShareT => (unborrowedContainerExpr2)
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
      nenv: NodeEnvironmentBox,
      range: RangeS,
      name: IFunctionDeclarationNameS,
      functionS: FunctionS):
  (ReferenceExpressionTE) = {

    val functionA = astronomizeLambda(temputs, nenv, functionS)

    val closurestructTT =
      delegate.evaluateClosureStruct(temputs, nenv.snapshot, range, name, functionA);
    val closureCoord =
      templataTemplar.pointifyKind(temputs, closurestructTT, OwnT)

    val constructExpr2 = makeClosureStructConstructExpression(temputs, nenv, functionA.range, closurestructTT)
    vassert(constructExpr2.result.reference == closureCoord)
    // The result of a constructor is always an own or a share.

    // The below code was here, but i see no reason we need to put it in a temporary and point it out.
    // shouldnt this be done automatically if we try to call the function which accepts a borrow?
//    val closureVarId = FullName2(nenv.lambdaNumber, "__closure_" + function1.origin.lambdaNumber)
//    val closureLocalVar = ReferenceLocalVariable2(closureVarId, Final, resultExpr2.resultRegister.reference)
//    val letExpr2 = LetAndPoint2(closureLocalVar, resultExpr2)
//    val unlet2 = localHelper.unletLocal(nenv, closureLocalVar)
//    val dropExpr =
//      DestructorTemplar.drop(env, temputs, nenv, unlet2)
//    val deferExpr2 = Defer2(letExpr2, dropExpr)
//    (temputs, nenv, deferExpr2)

    constructExpr2
  }

  private def newGlobalFunctionGroupExpression(env: IEnvironment, temputs: Temputs, name: IImpreciseNameS): ReferenceExpressionTE = {
    TemplarReinterpretTE(
      VoidLiteralTE(),
      CoordT(
        ShareT,
        ReadonlyT,
        OverloadSet(env, name)))
  }

  def evaluateBlockStatements(
    temputs: Temputs,
    startingNenv: NodeEnvironment,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    block: BlockSE):
  (ReferenceExpressionTE, Set[CoordT]) = {
    blockTemplar.evaluateBlockStatements(temputs, startingNenv, nenv, life, block)
  }

  def translatePatternList(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    patterns1: Vector[AtomSP],
    patternInputExprs2: Vector[ReferenceExpressionTE]
  ): ReferenceExpressionTE = {
    patternTemplar.translatePatternList(
      temputs, nenv, life, patterns1, patternInputExprs2,
      (temputs, nenv, liveCaptureLocals) => VoidLiteralTE())
  }

  def astronomizeLambda(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
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
            vassertSome(nenv.lookupNearestWithImpreciseName(profiler, n, Set(TemplataLookupContext))).tyype
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


  def dropSince(
    temputs: Temputs,
    startingNenv: NodeEnvironment,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    exprTE: ReferenceExpressionTE):
  ReferenceExpressionTE = {
    val unreversedVariablesToDestruct = nenv.snapshot.getLiveVariablesIntroducedSince(startingNenv)

    val newExpr =
      if (unreversedVariablesToDestruct.isEmpty) {
        exprTE
      } else if (exprTE.kind == NeverT()) {
        val moots = mootAll(temputs, nenv, unreversedVariablesToDestruct)
        Templar.consecutive(Vector(exprTE) ++ moots)
      } else if (exprTE.kind == VoidT()) {
        val reversedVariablesToDestruct = unreversedVariablesToDestruct.reverse
        // Dealiasing should be done by hammer. But destructors are done here
        val destroyExpressions = localHelper.unletAll(temputs, nenv, reversedVariablesToDestruct)

        Templar.consecutive(
          (Vector(exprTE) ++ destroyExpressions) :+
            VoidLiteralTE())
      } else {
        val (resultifiedExpr, resultLocalVariable) =
          resultifyExpressions(nenv, life + 1, exprTE)

        val reversedVariablesToDestruct = unreversedVariablesToDestruct.reverse
        // Dealiasing should be done by hammer. But destructors are done here
        val destroyExpressions = localHelper.unletAll(temputs, nenv, reversedVariablesToDestruct)

        Templar.consecutive(
          (Vector(resultifiedExpr) ++ destroyExpressions) :+
            localHelper.unletLocal(nenv, resultLocalVariable))
      }
    newExpr
  }

  // Makes the last expression stored in a variable.
  // Dont call this for void or never or no expressions.
  // Maybe someday we can do this even for Never and Void, for consistency and so
  // we dont have any special casing.
  def resultifyExpressions(
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    expr: ReferenceExpressionTE):
  (ReferenceExpressionTE, ReferenceLocalVariableT) = {
    val resultVarId = nenv.fullName.addStep(TemplarBlockResultVarNameT(life))
    val resultVariable = ReferenceLocalVariableT(resultVarId, FinalT, expr.result.reference)
    val resultLet = LetNormalTE(resultVariable, expr)
    nenv.addVariable(resultVariable)
    (resultLet, resultVariable)
  }

  def mootAll(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    variables: Vector[ILocalVariableT]):
  (Vector[ReferenceExpressionTE]) = {
    variables.map({ case head =>
      ast.UnreachableMootTE(localHelper.unletLocal(nenv, head))
    })
  }
}
