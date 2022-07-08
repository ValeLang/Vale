package dev.vale.typing.expression

import dev.vale
import dev.vale.highertyping.{CompileErrorExceptionA, CouldntSolveRulesA, FunctionA, PatternSUtils}
import dev.vale.{Err, Interner, Keywords, Ok, Profiler, RangeS, vassert, vassertOne, vassertSome, vcheck, vcurious, vfail, vimpl, vwat, _}
import dev.vale.parsing.ast.{LoadAsBorrowP, LoadAsP, LoadAsWeakP, MoveP, UseP}
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.postparsing.rules.{RuneParentEnvLookupSR, RuneUsage}
import dev.vale.postparsing.{ArbitraryNameS, ArgLookupSE, BlockSE, BreakSE, CodeNameS, ConsecutorSE, ConstantBoolSE, ConstantFloatSE, ConstantIntSE, ConstantStrSE, CoordTemplataType, DestructSE, DotSE, ExprMutateSE, FunctionCallSE, FunctionS, FunctionSE, FunctionTemplataType, IExpressionSE, IFunctionDeclarationNameS, IImpreciseNameS, IVarNameS, IfSE, IndexSE, LambdaStructImpreciseNameS, LetSE, LocalLoadSE, LocalMutateSE, MapSE, NewRuntimeSizedArraySE, OutsideLoadSE, OwnershippedSE, PostParser, ReturnSE, RuneLookupSE, RuneNameS, RuneTypeSolver, SelfRuneS, StaticArrayFromCallableSE, StaticArrayFromValuesSE, TupleSE, UnletSE, UserFunctionS, VoidSE, WhileSE}
import dev.vale.typing.{ArrayCompiler, CannotSubscriptT, CantMoveFromGlobal, CantMutateFinalElement, CantMutateFinalMember, CantReconcileBranchesResults, CantUnstackifyOutsideLocalFromInsideWhile, CantUseUnstackifiedLocal, CompileErrorExceptionT, Compiler, CompilerOutputs, ConvertHelper, CouldntConvertForMutateT, CouldntConvertForReturnT, CouldntFindIdentifierToLoadT, CouldntFindMemberT, HigherTypingInferError, IfConditionIsntBoolean, InferCompiler, OverloadResolver, RangedInternalErrorT, SequenceCompiler, TemplataCompiler, TypingPassOptions, ast, templata}
import dev.vale.typing.ast.{AddressExpressionTE, AddressMemberLookupTE, ArgLookupTE, BlockTE, BorrowToWeakTE, BreakTE, ConstantBoolTE, ConstantFloatTE, ConstantIntTE, ConstantStrTE, ConstructTE, DestroyTE, ExpressionT, IfTE, LetNormalTE, LocalLookupTE, LocationInFunctionEnvironment, MutateTE, PrototypeT, ReferenceExpressionTE, ReferenceMemberLookupTE, ReinterpretTE, ReturnTE, RuntimeSizedArrayLookupTE, StaticSizedArrayLookupTE, VoidLiteralTE, WhileTE}
import dev.vale.typing.citizen.{AncestorHelper, StructCompiler}
import dev.vale.typing.env.{AddressibleClosureVariableT, AddressibleLocalVariableT, ExpressionLookupContext, FunctionEnvironment, IEnvironment, ILocalVariableT, NodeEnvironment, NodeEnvironmentBox, ReferenceClosureVariableT, ReferenceLocalVariableT, TemplataEnvEntry, TemplataLookupContext}
import dev.vale.typing.function.DestructorCompiler
import dev.vale.highertyping._
import dev.vale.parsing._
import dev.vale.parsing.ast._
import dev.vale.postparsing.rules.RuneParentEnvLookupSR
import dev.vale.postparsing.RuneTypeSolver
import dev.vale.typing.{ast, _}
import dev.vale.typing.ast._
import dev.vale.typing.citizen.AncestorHelper
import dev.vale.typing.env._
import dev.vale.typing.function.FunctionCompiler.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
import dev.vale.typing.names.{ArbitraryNameT, ClosureParamNameT, CodeVarNameT, IVarNameT, NameTranslator, RuneNameT, TypingPassBlockResultVarNameT, TypingPassFunctionResultVarNameT}
import dev.vale.typing.templata.{BooleanTemplata, CoordTemplata, ExternFunctionTemplata, FunctionTemplata, ITemplata, IntegerTemplata, InterfaceTemplata, PrototypeTemplata}
import dev.vale.typing.types.{AddressMemberTypeT, BoolT, BorrowT, CitizenRefT, CoordT, FinalT, ImmutableT, IntT, InterfaceTT, MutabilityT, MutableT, NeverT, OverloadSetT, OwnT, ParamFilter, ReferenceMemberTypeT, RuntimeSizedArrayTT, ShareT, StaticSizedArrayTT, StructMemberT, StructTT, VaryingT, VoidT, WeakT}
import dev.vale.typing.names.CitizenTemplateNameT
import dev.vale.typing.templata._
import dev.vale.typing.types._

import scala.collection.immutable.{List, Nil, Set}

case class TookWeakRefOfNonWeakableError() extends Throwable { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

trait IExpressionCompilerDelegate {
  def evaluateTemplatedFunctionFromCallForPrototype(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    functionTemplata: FunctionTemplata,
    explicitTemplateArgs: Vector[ITemplata],
    args: Vector[ParamFilter]):
  IEvaluateFunctionResult[PrototypeT]

  def evaluateClosureStruct(
    coutputs: CompilerOutputs,
    containingNodeEnv: NodeEnvironment,
    callRange: RangeS,
    name: IFunctionDeclarationNameS,
    function1: FunctionA):
  StructTT
}

class ExpressionCompiler(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    nameTranslator: NameTranslator,

    templataCompiler: TemplataCompiler,
    inferCompiler: InferCompiler,
    arrayCompiler: ArrayCompiler,
    structCompiler: StructCompiler,
    ancestorHelper: AncestorHelper,
    sequenceCompiler: SequenceCompiler,
    overloadCompiler: OverloadResolver,
    destructorCompiler: DestructorCompiler,
    convertHelper: ConvertHelper,
    delegate: IExpressionCompilerDelegate) {
  val localHelper = new LocalHelper(opts, interner, nameTranslator, destructorCompiler)
  val callCompiler = new CallCompiler(opts, interner, keywords, templataCompiler, convertHelper, localHelper, overloadCompiler)
  val patternCompiler = new PatternCompiler(opts, interner, inferCompiler, arrayCompiler, convertHelper, destructorCompiler, localHelper)
  val blockCompiler = new BlockCompiler(opts, destructorCompiler, localHelper, new IBlockCompilerDelegate {
    override def evaluateAndCoerceToReferenceExpression(
      coutputs: CompilerOutputs,
      nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
    (ReferenceExpressionTE, Set[CoordT]) = {
      ExpressionCompiler.this.evaluateAndCoerceToReferenceExpression(
        coutputs, nenv, life, expr1)
    }

    override def dropSince(
      coutputs: CompilerOutputs,
      startingNenv: NodeEnvironment,
      nenv: NodeEnvironmentBox,
      range: RangeS,
      life: LocationInFunctionEnvironment,
      unresultifiedUndestructedExpressions: ReferenceExpressionTE):
    ReferenceExpressionTE = {
      ExpressionCompiler.this.dropSince(
        coutputs, startingNenv, nenv, range, life, unresultifiedUndestructedExpressions)
    }
  })

  def evaluateAndCoerceToReferenceExpressions(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    exprs1: Vector[IExpressionSE]):
  (Vector[ReferenceExpressionTE], Set[CoordT]) = {
    val things =
      exprs1.zipWithIndex.map({ case (expr, index) =>
        evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + index, expr)
      })
    (things.map(_._1), things.map(_._2).flatten.toSet)
  }

  private def evaluateLookupForLoad(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    range: RangeS,
    name: IVarNameT,
    targetOwnership: LoadAsP):
  (Option[ExpressionT]) = {
    evaluateAddressibleLookup(coutputs, nenv, range, name) match {
      case Some(x) => {
        val thing = localHelper.softLoad(nenv, range, x, targetOwnership)
        (Some(thing))
      }
      case None => {
        nenv.lookupNearestWithName(name, Set(TemplataLookupContext)) match {
          case Some(IntegerTemplata(num)) => (Some(ConstantIntTE(num, 32)))
          case Some(BooleanTemplata(bool)) => (Some(ConstantBoolTE(bool)))
          case None => (None)
        }
      }
    }
  }

  private def evaluateAddressibleLookupForMutate(
      coutputs: CompilerOutputs,
      nenv: NodeEnvironmentBox,
      range: RangeS,
      nameA: IVarNameS):
  Option[AddressExpressionTE] = {
    nenv.getVariable(nameTranslator.translateVarNameStep(nameA)) match {
      case Some(alv @ AddressibleLocalVariableT(_, _, reference)) => {
        Some(LocalLookupTE(range, alv))
      }
      case Some(rlv @ ReferenceLocalVariableT(id, _, reference)) => {
        Some(LocalLookupTE(range, rlv))
      }
      case Some(AddressibleClosureVariableT(id, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Compiler.getMutability(coutputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) BorrowT else ShareT
        val closuredVarsStructRefRef = CoordT(ownership, closuredVarsStructRef)
        val name2 = nenv.fullName.addStep(interner.intern(ClosureParamNameT()))
        val borrowExpr =
          localHelper.borrowSoftLoad(
            coutputs,
            LocalLookupTE(
              range,
              ReferenceLocalVariableT(name2, FinalT, closuredVarsStructRefRef)))

        val closuredVarsStructDef = coutputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == id))

        val index = closuredVarsStructDef.members.indexWhere(_.name == id.last)
//        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership
        val lookup = ast.AddressMemberLookupTE(range, borrowExpr, id, tyype, variability)
        Some(lookup)
      }
      case Some(ReferenceClosureVariableT(varName, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Compiler.getMutability(coutputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) BorrowT else ShareT
        val closuredVarsStructRefCoord = CoordT(ownership, closuredVarsStructRef)
//        val closuredVarsStructDef = coutputs.lookupStruct(closuredVarsStructRef)
        val borrowExpr =
          localHelper.borrowSoftLoad(
            coutputs,
            LocalLookupTE(
              range,
              ReferenceLocalVariableT(nenv.fullName.addStep(interner.intern(ClosureParamNameT())), FinalT, closuredVarsStructRefCoord)))
//        val index = closuredVarsStructDef.members.indexWhere(_.name == varName)

        val lookup =
          ast.ReferenceMemberLookupTE(range, borrowExpr, varName, tyype, variability)
        Some(lookup)
      }
      case None => None
      case _ => vwat()
    }
  }

  private def evaluateAddressibleLookup(
    coutputs: CompilerOutputs,
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
        val mutability = Compiler.getMutability(coutputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) BorrowT else ShareT
        val closuredVarsStructRefRef = CoordT(ownership, closuredVarsStructRef)
        val closureParamVarName2 = nenv.fullName.addStep(interner.intern(ClosureParamNameT()))

        val borrowExpr =
          localHelper.borrowSoftLoad(
            coutputs,
            LocalLookupTE(
              range,
              ReferenceLocalVariableT(closureParamVarName2, FinalT, closuredVarsStructRefRef)))
        val closuredVarsStructDef = coutputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == id))

        val index = closuredVarsStructDef.members.indexWhere(_.name == id.last)
        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership
        val lookup = AddressMemberLookupTE(range, borrowExpr, id, tyype, variability)
        Some(lookup)
      }
      case Some(ReferenceClosureVariableT(varName, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Compiler.getMutability(coutputs, closuredVarsStructRef)
        val ownership = if (mutability == MutableT) BorrowT else ShareT
        val closuredVarsStructRefCoord = CoordT(ownership, closuredVarsStructRef)
        val closuredVarsStructDef = coutputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == varName))
        val index = closuredVarsStructDef.members.indexWhere(_.name == varName.last)

        val borrowExpr =
          localHelper.borrowSoftLoad(
            coutputs,
            LocalLookupTE(
              range,
              ReferenceLocalVariableT(nenv.fullName.addStep(interner.intern(ClosureParamNameT())), FinalT, closuredVarsStructRefCoord)))

//        val ownershipInClosureStruct = closuredVarsStructDef.members(index).tyype.reference.ownership

        val lookup = ReferenceMemberLookupTE(range, borrowExpr, varName, tyype, variability)
        Some(lookup)
      }
      case None => None
      case _ => vwat()
    }
  }

  private def makeClosureStructConstructExpression(
      coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
      range: RangeS,
      closureStructRef: StructTT):
  (ReferenceExpressionTE) = {
    val closureStructDef = coutputs.lookupStruct(closureStructRef);
    // Note, this is where the unordered closuredNames set becomes ordered.
    val lookupExpressions2 =
      closureStructDef.members.map({
        case StructMemberT(memberName, variability, tyype) => {
          val lookup =
            evaluateAddressibleLookup(coutputs, nenv, range, memberName) match {
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
              localHelper.borrowSoftLoad(coutputs, lookup)
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
    val resultPointerType = CoordT(ownership, closureStructRef)
    val constructExpr2 =
      ConstructTE(closureStructRef, resultPointerType, lookupExpressions2)
    (constructExpr2)
  }

  def evaluateAndCoerceToReferenceExpression(
      coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
  (ReferenceExpressionTE, Set[CoordT]) = {
    val (expr2, returnsFromExpr) =
      evaluate(coutputs, nenv, life, expr1)
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
      coutputs: CompilerOutputs,
      nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
  (AddressExpressionTE, Set[CoordT]) = {
    val (expr2, returns) =
      evaluate(coutputs, nenv, life, expr1)
    expr2 match {
      case a : AddressExpressionTE => (a, returns)
      case _ : ReferenceExpressionTE => throw CompileErrorExceptionT(RangedInternalErrorT(expr1.range, "Expected reference expression!"))
    }
  }

  // returns:
  // - resulting expression
  // - all the types that are returned from inside the body via return
  private def evaluate(
      coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      expr1: IExpressionSE):
  (ExpressionT, Set[CoordT]) = {
    Profiler.frame(() => {
      expr1 match {
        case VoidSE(range) => (VoidLiteralTE(), Set())
        case ConstantIntSE(range, i, bits) => (ConstantIntTE(i, bits), Set())
        case ConstantBoolSE(range, i) => (ConstantBoolTE(i), Set())
        case ConstantStrSE(range, s) => (ConstantStrTE(s), Set())
        case ConstantFloatSE(range, f) => (ConstantFloatTE(f), Set())
        case ArgLookupSE(range, index) => {
          val paramCoordRune = nenv.function.params(index).pattern.coordRune.get
          val paramCoordTemplata = vassertOne(nenv.lookupNearestWithImpreciseName(interner.intern(RuneNameS(paramCoordRune.rune)), Set(TemplataLookupContext)))
          val CoordTemplata(paramCoord) = paramCoordTemplata
          vassert(nenv.functionEnvironment.fullName.last.parameters(index) == paramCoord)
          (ArgLookupTE(index, paramCoord), Set())
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, maybeTemplateArgs, callableTargetOwnership), argsExprs1) => {
//          vassert(callableTargetOwnership == PointConstraintP(Some(ReadonlyP)))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(coutputs, nenv, life + 0, argsExprs1)
          val callExpr2 =
            callCompiler.evaluatePrefixCall(
              coutputs,
              nenv,
              life + 1,
              range,
              newGlobalFunctionGroupExpression(nenv.snapshot, coutputs, name),
              rules.toVector,
              maybeTemplateArgs.toArray.flatMap(_.map(_.rune)),
              argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, templateArgTemplexesS, callableTargetOwnership), argsExprs1) => {
//          vassert(callableTargetOwnership == PointConstraintP(None))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(coutputs, nenv, life + 0, argsExprs1)
          val callExpr2 =
            callCompiler.evaluatePrefixCall(
              coutputs,
              nenv,
              life + 1,
              range,
              newGlobalFunctionGroupExpression(nenv.snapshot, coutputs, name),
              rules.toVector,
              templateArgTemplexesS.toArray.flatMap(_.map(_.rune)),
              argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, templateArgs, callableTargetOwnership), argsExprs1) => {
          vassert(callableTargetOwnership == LoadAsBorrowP)
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(coutputs, nenv, life, argsExprs1)
          val callExpr2 =
            callCompiler.evaluateNamedPrefixCall(coutputs, nenv, range, name, rules.toVector, templateArgs.toVector.flatten.map(_.rune), argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, OutsideLoadSE(_, rules, name, templateArgs, callableTargetOwnership), argsPackExpr1) => {
//          vassert(callableTargetOwnership == PointConstraintP(Some(ReadonlyP)))
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(coutputs, nenv, life, argsPackExpr1)
          val callExpr2 =
            callCompiler.evaluateNamedPrefixCall(coutputs, nenv, range, name, rules.toVector, Vector(), argsExprs2)
          (callExpr2, returnsFromArgs)
        }
        case FunctionCallSE(range, callableExpr1, argsExprs1) => {
          val (undecayedCallableExpr2, returnsFromCallable) =
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + 0, callableExpr1);
          val decayedCallableExpr2 =
            localHelper.maybeBorrowSoftLoad(coutputs, undecayedCallableExpr2)
          val decayedCallableReferenceExpr2 =
            coerceToReferenceExpression(nenv, decayedCallableExpr2)
          val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(coutputs, nenv, life + 1, argsExprs1)
          val functionPointerCall2 =
            callCompiler.evaluatePrefixCall(coutputs, nenv, life + 2, range, decayedCallableReferenceExpr2, Vector(), Array(), argsExprs2)
          (functionPointerCall2, returnsFromCallable ++ returnsFromArgs)
        }

        case OwnershippedSE(range, sourceSE, loadAsP) => {
          val (sourceTE, returnsFromInner) =
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + 0, sourceSE);
          val resultExpr2 =
            sourceTE.result.underlyingReference.ownership match {
              case OwnT => {
                loadAsP match {
                  case MoveP => {
                    // this can happen if we put a ^ on an owning reference. No harm, let it go.
                    sourceTE
                  }
                  case LoadAsBorrowP => {
                    localHelper.makeTemporaryLocal(coutputs, nenv, range, life + 1, sourceTE, BorrowT)
                  }
                  case LoadAsWeakP => {
                    val expr = localHelper.makeTemporaryLocal(coutputs, nenv, range, life + 3, sourceTE, BorrowT)
                    weakAlias(coutputs, expr)
                  }
                  case UseP => vcurious()
                }
              }
              case BorrowT => {
                loadAsP match {
                  case MoveP => vcurious() // Can we even coerce to an owning reference?
                  case LoadAsBorrowP => sourceTE
                  case LoadAsWeakP => weakAlias(coutputs, sourceTE)
                  case UseP => sourceTE
                }
              }
              case WeakT => {
                loadAsP match {
                  case MoveP => vcurious() // Can we even coerce to an owning reference?
                  case LoadAsBorrowP => vimpl()
                  case LoadAsWeakP => sourceTE
                  case UseP => sourceTE
                }
              }
              case ShareT => {
                loadAsP match {
                  case MoveP => {
                    // Allow this, we can do ^ on a share ref, itll just give us a share ref.
                    sourceTE
                  }
                  case LoadAsBorrowP => {
                    // Allow this, we can do & on a share ref, itll just give us a share ref.
                    sourceTE
                  }
                  case LoadAsWeakP => {
                    vfail()
                  }
                  case UseP => sourceTE
                }
              }
            }
          (resultExpr2, returnsFromInner)
        }
        case LocalLoadSE(range, nameA, targetOwnership) => {
          val name = nameTranslator.translateVarNameStep(nameA)
          val lookupExpr1 =
            evaluateLookupForLoad(coutputs, nenv, range, name, targetOwnership) match {
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
            nenv.lookupAllWithImpreciseName(name, Set(ExpressionLookupContext)) match {
              case Vector(BooleanTemplata(value)) => ConstantBoolTE(value)
              case Vector(IntegerTemplata(value)) => ConstantIntTE(value, 32)
              case templatas if templatas.nonEmpty && templatas.collect({ case FunctionTemplata(_, _) => case ExternFunctionTemplata(_) => }).size == templatas.size => {
                if (targetOwnership == MoveP) {
                  throw CompileErrorExceptionT(CantMoveFromGlobal(range, "Can't move from globals. Name: " + name))
                }
                newGlobalFunctionGroupExpression(nenv.snapshot, coutputs, name)
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
            evaluateAddressibleLookupForMutate(coutputs, nenv, range, name) match {
              case None => {
                throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldnt find " + name))
              }
              case Some(x) => x
            }
          val (unconvertedSourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life, sourceExpr1)

          // We should have inferred variability from the presents of sets
          vassert(destinationExpr2.variability == VaryingT)

          val isConvertible =
            templataCompiler.isTypeConvertible(
              coutputs, unconvertedSourceExpr2.result.reference, destinationExpr2.result.reference)
          if (!isConvertible) {
            throw CompileErrorExceptionT(
              CouldntConvertForMutateT(
                range, destinationExpr2.result.reference, unconvertedSourceExpr2.result.reference))
          }
          vassert(isConvertible)
          val convertedSourceExpr2 =
            convertHelper.convert(nenv.snapshot, coutputs, range, unconvertedSourceExpr2, destinationExpr2.result.reference);

          val mutate2 = MutateTE(destinationExpr2, convertedSourceExpr2);
          (mutate2, returnsFromSource)
        }
        case ExprMutateSE(range, destinationExpr1, sourceExpr1) => {
          val (unconvertedSourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + 0, sourceExpr1)
          val (destinationExpr2, returnsFromDestination) =
            evaluateExpectedAddressExpression(coutputs, nenv, life + 1, destinationExpr1)
          if (destinationExpr2.variability != VaryingT) {
            destinationExpr2 match {
              case ReferenceMemberLookupTE(range, structExpr, memberName, _, _) => {
                structExpr.kind match {
                  case s @ StructTT(_) => {
                    throw CompileErrorExceptionT(CantMutateFinalMember(range, s, memberName))
                  }
                  case _ => vimpl(structExpr.kind.toString)
                }
              }
              case RuntimeSizedArrayLookupTE(range, arrayExpr, arrayType, _, _) => {
                throw CompileErrorExceptionT(CantMutateFinalElement(range, arrayExpr.result.reference))
              }
              case StaticSizedArrayLookupTE(range, arrayExpr, arrayType, _, _) => {
                throw CompileErrorExceptionT(CantMutateFinalElement(range, arrayExpr.result.reference))
              }
              case x => vimpl(x.toString)
            }
          }

          val isConvertible =
            templataCompiler.isTypeConvertible(coutputs, unconvertedSourceExpr2.result.reference, destinationExpr2.result.reference)
          if (!isConvertible) {
            throw CompileErrorExceptionT(CouldntConvertForMutateT(range, destinationExpr2.result.reference, unconvertedSourceExpr2.result.reference))
          }
          val convertedSourceExpr2 =
            convertHelper.convert(nenv.snapshot, coutputs, range, unconvertedSourceExpr2, destinationExpr2.result.reference);

          val mutate2 = MutateTE(destinationExpr2, convertedSourceExpr2);
          (mutate2, returnsFromSource ++ returnsFromDestination)
        }
        case OutsideLoadSE(range, rules, name, templateArgs1, targetOwnership) => {
          // So far, we only allow these when they're immediately called like functions
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Raw template specified lookups unimplemented!"))
        }
        case IndexSE(range, containerExpr1, indexExpr1) => {
          val (unborrowedContainerExpr2, returnsFromContainerExpr) =
            evaluate(coutputs, nenv, life + 0, containerExpr1);
          val containerExpr2 =
            dotBorrow(coutputs, nenv, range, life + 1, unborrowedContainerExpr2)

          val (indexExpr2, returnsFromIndexExpr) =
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + 2, indexExpr1);

          val exprTemplata =
            containerExpr2.result.reference.kind match {
              case rsa @ RuntimeSizedArrayTT(_, _) => {
                arrayCompiler.lookupInUnknownSizedArray(range, containerExpr2, indexExpr2, rsa)
              }
              case at@StaticSizedArrayTT(_, _, _, _) => {
                arrayCompiler.lookupInStaticSizedArray(range, containerExpr2, indexExpr2, at)
              }
//              case at@StructTT(FullNameT(ProgramT.topLevelName, Vector(), CitizenNameT(CitizenTemplateNameT(ProgramT.tupleHumanName), _))) => {
//                indexExpr2 match {
//                  case ConstantIntTE(index, _) => {
//                    val understructDef = coutputs.lookupStruct(at);
//                    val memberName = understructDef.fullName.addStep(understructDef.members(index.toInt).name)
//                    val memberType = understructDef.members(index.toInt).tyype
//
//                    vassert(understructDef.members.exists(member => understructDef.fullName.addStep(member.name) == memberName))
//
////                    val ownershipInClosureStruct = understructDef.members(index).tyype.reference.ownership
//
//                    val targetPermission =
//                      Compiler.intersectPermission(
//                        containerExpr2.result.reference.permission,
//                        memberType.reference.permission)
//
//                    ast.ReferenceMemberLookupTE(range, containerExpr2, memberName, memberType.reference, targetPermission, FinalT)
//                  }
//                  case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Struct random access not implemented yet!"))
//                }
//              }
              case _ => throw CompileErrorExceptionT(CannotSubscriptT(range, containerExpr2.result.reference.kind))
              // later on, a map type could go here
            }
          (exprTemplata, returnsFromContainerExpr ++ returnsFromIndexExpr)
        }
        case DotSE(range, containerExpr1, memberNameStr, borrowContainer) => {
          val memberName = interner.intern(CodeVarNameT(memberNameStr))
          val (unborrowedContainerExpr2, returnsFromContainerExpr) =
            evaluate(coutputs, nenv, life + 0, containerExpr1)
          val containerExpr2 =
            dotBorrow(coutputs, nenv, range, life + 1, unborrowedContainerExpr2)

          val expr2 =
            containerExpr2.result.reference.kind match {
              case structTT@StructTT(_) => {
                val structDef = coutputs.lookupStruct(structTT)
                val (structMember, memberIndex) =
                  structDef.getMemberAndIndex(memberName) match {
                    case None => throw CompileErrorExceptionT(CouldntFindMemberT(range, memberName.name.str))
                    case Some(x) => x
                  }
                val memberFullName = structDef.fullName.addStep(structDef.members(memberIndex).name)
                val memberType = structMember.tyype.expectReferenceMember().reference;

                vassert(structDef.members.exists(member => structDef.fullName.addStep(member.name) == memberFullName))

                ast.ReferenceMemberLookupTE(range, containerExpr2, memberFullName, memberType, structMember.variability)
              }
              case as@StaticSizedArrayTT(_, _, _, _) => {
                if (memberNameStr.str.forall(Character.isDigit)) {
                  arrayCompiler.lookupInStaticSizedArray(range, containerExpr2, ConstantIntTE(memberNameStr.str.toInt, 32), as)
                } else {
                  throw CompileErrorExceptionT(RangedInternalErrorT(range, "Sequence has no member named " + memberNameStr))
                }
              }
              case at@RuntimeSizedArrayTT(_, _) => {
                if (memberNameStr.str.forall(Character.isDigit)) {
                  arrayCompiler.lookupInUnknownSizedArray(range, containerExpr2, ConstantIntTE(memberNameStr.str.toInt, 32), at)
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
          val callExpr2 = evaluateClosure(coutputs, nenv, range, name, functionS)
          (callExpr2, Set())
        }
        case TupleSE(range, elements1) => {
          val (exprs2, returnsFromElements) =
            evaluateAndCoerceToReferenceExpressions(coutputs, nenv, life + 0, elements1);

          // would we need a sequence templata? probably right?
          val expr2 = sequenceCompiler.evaluate(nenv.snapshot, coutputs, exprs2)
          (expr2, returnsFromElements)
        }
        case StaticArrayFromValuesSE(range, rules, maybeElementTypeRuneA, mutabilityRune, variabilityRune, sizeRuneA, elements1) => {
          val (exprs2, returnsFromElements) =
            evaluateAndCoerceToReferenceExpressions(coutputs, nenv, life, elements1);
          // would we need a sequence templata? probably right?
          val expr2 =
            arrayCompiler.evaluateStaticSizedArrayFromValues(
              coutputs, nenv.snapshot, range, rules.toVector, maybeElementTypeRuneA.map(_.rune), sizeRuneA.rune, mutabilityRune.rune, variabilityRune.rune, exprs2)
          (expr2, returnsFromElements)
        }
        case StaticArrayFromCallableSE(range, rules, maybeElementTypeRune, maybeMutabilityRune, maybeVariabilityRune, sizeRuneA, callableAE) => {
          val (callableTE, returnsFromCallable) =
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life, callableAE);
          val expr2 =
            arrayCompiler.evaluateStaticSizedArrayFromCallable(
              coutputs, nenv.snapshot, range, rules.toVector, maybeElementTypeRune.map(_.rune), sizeRuneA.rune, maybeMutabilityRune.rune, maybeVariabilityRune.rune, callableTE)
          (expr2, returnsFromCallable)
        }
        case NewRuntimeSizedArraySE(range, rulesA, maybeElementTypeRune, mutabilityRune, sizeAE, maybeCallableAE) => {
          val (sizeTE, returnsFromSize) =
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + 0, sizeAE);
          val (maybeCallableTE, returnsFromCallable) =
            maybeCallableAE match {
              case None => (None, Vector())
              case Some(callableAE) => {
                val (callableTE, rets) =
                  evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + 1, callableAE);
                (Some(callableTE), rets)
              }
            }


          val expr2 =
            arrayCompiler.evaluateRuntimeSizedArrayFromCallable(
              coutputs, nenv, range, rulesA.toVector, maybeElementTypeRune.map(_.rune), mutabilityRune.rune, sizeTE, maybeCallableTE)
          (expr2, returnsFromSize ++ returnsFromCallable)
        }
        case LetSE(range, rulesA, pattern, sourceExpr1) => {
          val (sourceExpr2, returnsFromSource) =
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + 0, sourceExpr1)

          val runeToInitiallyKnownType = PatternSUtils.getRuneTypesFromPattern(pattern)
          val runeToType =
            new RuneTypeSolver(interner).solve(
                opts.globalOptions.sanityCheck,
                opts.globalOptions.useOptimizedSolver,
                nameS => vassertOne(nenv.lookupNearestWithImpreciseName(nameS, Set(TemplataLookupContext))).tyype,
                range,
                false,
                rulesA,
                List(),
                true,
                runeToInitiallyKnownType.toMap) match {
              case Ok(r) => r
              case Err(e) => throw CompileErrorExceptionT(HigherTypingInferError(range, e))
            }
          val resultTE =
            patternCompiler.inferAndTranslatePattern(
              coutputs, nenv, life + 1, rulesA.toVector, runeToType, pattern, sourceExpr2,
              (coutputs, nenv, life, liveCaptureLocals) => VoidLiteralTE())

          (resultTE, returnsFromSource)
        }
        case r @ RuneLookupSE(range, runeA) => {
          val templata = vassertOne(nenv.lookupNearestWithImpreciseName(interner.intern(RuneNameS(runeA)), Set(TemplataLookupContext)))
          templata match {
            case IntegerTemplata(value) => (ConstantIntTE(value, 32), Set())
            case PrototypeTemplata(value) => {
              val tinyEnv =
                nenv.functionEnvironment.makeChildNodeEnvironment(r, life)
                  .addEntries(interner, Vector(ArbitraryNameT() -> TemplataEnvEntry(PrototypeTemplata(value))))
              val expr = newGlobalFunctionGroupExpression(tinyEnv, coutputs, interner.intern(ArbitraryNameS()))
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
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + 1, conditionSE)
          if (conditionExpr.result.reference != CoordT(ShareT, BoolT())) {
            throw CompileErrorExceptionT(IfConditionIsntBoolean(conditionSE.range, conditionExpr.result.reference))
          }


          val thenFate = NodeEnvironmentBox(nenv.makeChild(thenBodySE))

          val (thenExpressionsWithResult, thenReturnsFromExprs) =
            evaluateBlockStatements(coutputs, thenFate.snapshot, thenFate, life + 2, thenBodySE)
          val uncoercedThenBlock2 = BlockTE(thenExpressionsWithResult)

          val thenUnstackifiedAncestorLocals = thenFate.snapshot.getEffectsSince(nenv.snapshot)
          val thenContinues =
            uncoercedThenBlock2.result.reference.kind match {
              case NeverT(_) => false
              case _ => true
            }

          val elseFate = NodeEnvironmentBox(nenv.makeChild(elseBodySE))

          val (elseExpressionsWithResult, elseReturnsFromExprs) =
            evaluateBlockStatements(coutputs, elseFate.snapshot, elseFate, life + 3, elseBodySE)
          val uncoercedElseBlock2 = BlockTE(elseExpressionsWithResult)

          val elseUnstackifiedAncestorLocals = elseFate.snapshot.getEffectsSince(nenv.snapshot)
          val elseContinues =
            uncoercedElseBlock2.result.reference.kind match {
              case NeverT(_) => false
              case _ => true
            }

          if (thenContinues && elseContinues && uncoercedThenBlock2.result.reference.ownership != uncoercedElseBlock2.result.reference.ownership) {
            throw CompileErrorExceptionT(CantReconcileBranchesResults(range, uncoercedThenBlock2.result.reference, uncoercedElseBlock2.result.reference))
          }

          val commonType =
            (uncoercedThenBlock2.kind, uncoercedElseBlock2.kind) match {
              // If one side has a return-never, use the other side.
              case (NeverT(false), _) => uncoercedElseBlock2.result.reference
              case (_, NeverT(false)) => uncoercedThenBlock2.result.reference
              // If we get here, theres no return-nevers in play.
              // If one side has a break-never, use the other side.
              case (NeverT(true), _) => uncoercedElseBlock2.result.reference
              case (_, NeverT(true)) => uncoercedThenBlock2.result.reference
              case (a, b) if a == b => uncoercedThenBlock2.result.reference
              case (a : CitizenRefT, b : CitizenRefT) => {
                val aAncestors = ancestorHelper.getAncestorInterfaces(coutputs, a).keys.toSet
                val bAncestors = ancestorHelper.getAncestorInterfaces(coutputs, b).keys.toSet
                val commonAncestors = aAncestors.intersect(bAncestors)

                if (uncoercedElseBlock2.result.reference.ownership != uncoercedElseBlock2.result.reference.ownership) {
                  throw CompileErrorExceptionT(RangedInternalErrorT(range, "Two branches of if have different ownerships!\\n${a}\\n${b}"))
                }
                val ownership = uncoercedElseBlock2.result.reference.ownership

                if (commonAncestors.isEmpty) {
                  throw CompileErrorExceptionT(RangedInternalErrorT(range, s"No common ancestors of two branches of if:\n${a}\n${b}"))
                } else if (commonAncestors.size > 1) {
                  throw CompileErrorExceptionT(RangedInternalErrorT(range, s"More than one common ancestor of two branches of if:\n${a}\n${b}"))
                } else {
                  CoordT(ownership, commonAncestors.head)
                }
              }
              case (a, b) => {
                throw CompileErrorExceptionT(CantReconcileBranchesResults(range, uncoercedThenBlock2.result.reference, uncoercedElseBlock2.result.reference))
              }
            }
          val thenExpr2 = convertHelper.convert(thenFate.snapshot, coutputs, range, uncoercedThenBlock2, commonType)
          val elseExpr2 = convertHelper.convert(elseFate.snapshot, coutputs, range, uncoercedElseBlock2, commonType)

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
            evaluateBlockStatements(coutputs, loopBlockFate.snapshot, loopBlockFate, life + 1, bodySE)
          val uncoercedBodyBlock2 = BlockTE(bodyExpressionsWithResult)

          uncoercedBodyBlock2.kind match {
            case NeverT(_) =>
            case _ => {
              val bodyUnstackifiedAncestorLocals = loopBlockFate.snapshot.getEffectsSince(nenv.snapshot)
              if (bodyUnstackifiedAncestorLocals.nonEmpty) {
                throw CompileErrorExceptionT(CantUnstackifyOutsideLocalFromInsideWhile(range, bodyUnstackifiedAncestorLocals.head.last))
              }
            }
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
                evaluateBlockStatements(coutputs, loopBlockFate.snapshot, loopBlockFate, life + 1, bodySE)
              bodyExpressionsWithResult.result.reference
            }

          // Now that we know the result type, let's make a temporary list.

          val callEnv =
            nenv.snapshot
              .copy(templatas =
                nenv.snapshot.templatas
                  .addEntry(interner, interner.intern(RuneNameT(SelfRuneS())), TemplataEnvEntry(templata.CoordTemplata(elementRefT))))
          val makeListTE =
            callCompiler.evaluatePrefixCall(
              coutputs,
              nenv,
              life + 1,
              range,
              newGlobalFunctionGroupExpression(callEnv, coutputs, interner.intern(CodeNameS(keywords.List))),
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
                evaluateBlockStatements(coutputs, loopBlockFate.snapshot, loopBlockFate, life + 1, bodySE)

              // We store the iteration result in a local because the loop body will have
              // breaks, and we can't have a BreakTE inside a FunctionCallTE, see BRCOBS.
              val iterationResultLocal =
                localHelper.makeTemporaryLocal(
                  nenv, life + 3, userBodyTE.result.reference)
              val letIterationResultTE =
                LetNormalTE(iterationResultLocal, userBodyTE)

              val addCall =
                callCompiler.evaluatePrefixCall(
                  coutputs,
                  nenv,
                  life + 4,
                  range,
                  newGlobalFunctionGroupExpression(callEnv, coutputs, interner.intern(CodeNameS(keywords.add))),
                  Vector(),
                  Array(),
                  Vector(
                    localHelper.borrowSoftLoad(
                      coutputs,
                      LocalLookupTE(
                        range,
                        listLocal)),
                    localHelper.unletLocalWithoutDropping(nenv, iterationResultLocal)))
              val bodyTE = BlockTE(Compiler.consecutive(Vector(letIterationResultTE, addCall)))

              val bodyUnstackifiedAncestorLocals = loopBlockFate.snapshot.getEffectsSince(nenv.snapshot)
              if (bodyUnstackifiedAncestorLocals.nonEmpty) {
                throw CompileErrorExceptionT(CantUnstackifyOutsideLocalFromInsideWhile(range, bodyUnstackifiedAncestorLocals.head.last))
              }

              val whileTE = WhileTE(bodyTE)
              (whileTE, bodyReturnsFromExprs)
            }

          val unletListTE =
            localHelper.unletLocalWithoutDropping(nenv, listLocal)

          val combinedTE =
            Compiler.consecutive(Vector(letListTE, loopTE, unletListTE))

          (combinedTE, returnsFromLoop)
        }
        case ConsecutorSE(exprsSE) => {

          val (initExprsTE, initReturnsUnflattened) =
            exprsSE.init.zipWithIndex.map({ case (exprSE, index) =>
              val (undroppedExprTE, returns) =
                evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + index, exprSE)
              val exprTE =
                undroppedExprTE.result.kind match {
                  case VoidT() => undroppedExprTE
                  case _ => destructorCompiler.drop(nenv.snapshot, coutputs, exprSE.range, undroppedExprTE)
                }
              (exprTE, returns)
            }).unzip

          val (lastExprTE, lastReturns) =
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + (exprsSE.size - 1), exprsSE.last)

          (Compiler.consecutive(initExprsTE :+ lastExprTE), (initReturnsUnflattened.flatten ++ lastReturns).toSet)
        }
        case b @ BlockSE(range, locals, _) => {
          val childEnvironment = NodeEnvironmentBox(nenv.makeChild(b))

          val (expressionsWithResult, returnsFromExprs) =
            evaluateBlockStatements(coutputs, childEnvironment.snapshot, childEnvironment, life, b)
          val block2 = BlockTE(expressionsWithResult)

          val unstackifiedAncestorLocals = childEnvironment.snapshot.getEffectsSince(nenv.snapshot)
          unstackifiedAncestorLocals.foreach(nenv.markLocalUnstackified)

          (block2, returnsFromExprs)
        }
        case DestructSE(range, innerAE) => {
          val (innerExpr2, returnsFromArrayExpr) =
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + 0, innerAE);

          // should just ignore others, TODO impl
          vcheck(innerExpr2.result.reference.ownership == OwnT, "can only destruct own")

          val destroy2 =
            innerExpr2.kind match {
              case structTT@StructTT(_) => {
                val structDef = coutputs.lookupStruct(structTT)
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
                destructorCompiler.drop(nenv.snapshot, coutputs, range, innerExpr2)
              }
              case _ => vfail("Can't destruct type: " + innerExpr2.kind)
            }
          (destroy2, returnsFromArrayExpr)
        }
        case UnletSE(range, nameA) => {
          val name = nameTranslator.translateVarNameStep(nameA)
          val local =
            nenv.getVariable(name) match {
              case Some(lv : ILocalVariableT) => lv
              case Some(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't unlet local: " + name))
              case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "No local with name: " + name))
            }
          val resultExpr = localHelper.unletLocalWithoutDropping(nenv, local)
          // This will likely be dropped, as theyre probably not doing anything with it.
          // But who knows, maybe they'll do something with it, like pass it as a parameter
          // to something.

          (resultExpr, Set())
        }
        case ReturnSE(range, innerExprA) => {
          val (uncastedInnerExpr2, returnsFromInnerExpr) =
            evaluateAndCoerceToReferenceExpression(coutputs, nenv, life + 0, innerExprA);

          val innerExpr2 =
            nenv.maybeReturnType match {
              case None => (uncastedInnerExpr2)
              case Some(returnType) => {
                templataCompiler.isTypeConvertible(coutputs, uncastedInnerExpr2.result.reference, returnType) match {
                  case (false) => {
                    throw CompileErrorExceptionT(
                      CouldntConvertForReturnT(range, returnType, uncastedInnerExpr2.result.reference))
                  }
                  case (true) => {
                    convertHelper.convert(nenv.snapshot, coutputs, range, uncastedInnerExpr2, returnType)
                  }
                }
              }
            }

          val allLocals = nenv.getAllLocals()
          val unstackifiedLocals = nenv.getAllUnstackifiedLocals()
          val variablesToDestruct = allLocals.filter(x => !unstackifiedLocals.contains(x.id))
          val reversedVariablesToDestruct = variablesToDestruct.reverse

          val returns = returnsFromInnerExpr + innerExpr2.result.reference

          val resultVarId = nenv.fullName.addStep(interner.intern(TypingPassFunctionResultVarNameT()))
          val resultVariable = ReferenceLocalVariableT(resultVarId, FinalT, innerExpr2.result.reference)
          val resultLet = ast.LetNormalTE(resultVariable, innerExpr2)
          nenv.addVariable(resultVariable)

          val destructExprs =
            localHelper.unletAndDropAll(coutputs, nenv, range, reversedVariablesToDestruct)

          val getResultExpr =
            localHelper.unletLocalWithoutDropping(nenv, resultVariable)

          val consecutor = Compiler.consecutive(Vector(resultLet) ++ destructExprs ++ Vector(getResultExpr))

          (ReturnTE(consecutor), returns)
        }
        case BreakSE(range) => {
          // See BEAFB, we need to find the nearest while to see local since then.
          nenv.nearestLoopEnv() match {
            case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Using break while not inside loop!"))
            case Some((whileNenv, _)) => {
              val dropsTE =
                dropSince(coutputs, whileNenv, nenv, range, life, VoidLiteralTE())
              val dropsAndBreakTE = Compiler.consecutive(Vector(dropsTE, BreakTE()))
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
      coutputs: CompilerOutputs,
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
    if (generatorPrototype.paramTypes(1) != CoordT(ShareT, IntT.i32)) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Generator must take in an integer as its second param!"))
    }
    if (arrayMutability == ImmutableT &&
      Compiler.getMutability(coutputs, elementCoord.kind) == MutableT) {
      throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can't have an immutable array of mutable elements!"))
    }
  }

  def getOption(coutputs: CompilerOutputs, nenv: FunctionEnvironment, range: RangeS, containedCoord: CoordT):
  (CoordT, PrototypeT, PrototypeT) = {
    val interfaceTemplata =
      nenv.lookupNearestWithImpreciseName(interner.intern(CodeNameS(keywords.Opt)), Set(TemplataLookupContext)).toList match {
        case List(it@InterfaceTemplata(_, _)) => it
        case _ => vfail()
      }
    val optInterfaceRef =
      structCompiler.getInterfaceRef(coutputs, range, interfaceTemplata, Vector(CoordTemplata(containedCoord)))
    val ownOptCoord = CoordT(OwnT, optInterfaceRef)

    val someConstructorTemplata =
      nenv.lookupNearestWithImpreciseName(interner.intern(CodeNameS(keywords.Some)), Set(ExpressionLookupContext)).toList match {
        case List(ft@FunctionTemplata(_, _)) => ft
        case _ => vwat();
      }
    val someConstructor =
      delegate.evaluateTemplatedFunctionFromCallForPrototype(
        coutputs, range, someConstructorTemplata, Vector(CoordTemplata(containedCoord)), Vector(ParamFilter(containedCoord.ownership, containedCoord.kind, None))) match {
        case fff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, fff.toString))
        case EvaluateFunctionSuccess(p) => p
      }

    val noneConstructorTemplata =
      nenv.lookupNearestWithImpreciseName(interner.intern(CodeNameS(keywords.None)), Set(ExpressionLookupContext)).toList match {
        case List(ft@FunctionTemplata(_, _)) => ft
        case _ => vwat();
      }
    val noneConstructor =
      delegate.evaluateTemplatedFunctionFromCallForPrototype(
        coutputs, range, noneConstructorTemplata, Vector(CoordTemplata(containedCoord)), Vector()) match {
        case fff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, fff.toString))
        case EvaluateFunctionSuccess(p) => p
      }
    (ownOptCoord, someConstructor, noneConstructor)
  }

  def getResult(coutputs: CompilerOutputs, nenv: FunctionEnvironment, range: RangeS, containedSuccessCoord: CoordT, containedFailCoord: CoordT):
  (CoordT, PrototypeT, PrototypeT) = {
    val interfaceTemplata =
      nenv.lookupNearestWithImpreciseName(interner.intern(CodeNameS(keywords.Result)), Set(TemplataLookupContext)).toList match {
        case List(it@InterfaceTemplata(_, _)) => it
        case _ => vfail()
      }
    val resultInterfaceRef =
      structCompiler.getInterfaceRef(coutputs, range, interfaceTemplata, Vector(CoordTemplata(containedSuccessCoord), CoordTemplata(containedFailCoord)))
    val ownResultCoord = CoordT(OwnT, resultInterfaceRef)

    val okConstructorTemplata =
      nenv.lookupNearestWithImpreciseName(interner.intern(CodeNameS(keywords.Ok)), Set(ExpressionLookupContext)).toList match {
        case List(ft@FunctionTemplata(_, _)) => ft
        case _ => vwat();
      }
    val okConstructor =
      delegate.evaluateTemplatedFunctionFromCallForPrototype(
        coutputs, range, okConstructorTemplata, Vector(CoordTemplata(containedSuccessCoord), CoordTemplata(containedFailCoord)), Vector(ParamFilter(containedSuccessCoord.ownership, containedSuccessCoord.kind, None))) match {
        case fff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, fff.toString))
        case EvaluateFunctionSuccess(p) => p
      }

    val errConstructorTemplata =
      nenv.lookupNearestWithImpreciseName(interner.intern(CodeNameS(keywords.Err)), Set(ExpressionLookupContext)).toList match {
        case List(ft@FunctionTemplata(_, _)) => ft
        case _ => vwat();
      }
    val errConstructor =
      delegate.evaluateTemplatedFunctionFromCallForPrototype(
        coutputs, range, errConstructorTemplata, Vector(CoordTemplata(containedSuccessCoord), CoordTemplata(containedFailCoord)), Vector(ParamFilter(containedFailCoord.ownership, containedFailCoord.kind, None))) match {
        case fff@EvaluateFunctionFailure(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, fff.toString))
        case EvaluateFunctionSuccess(p) => p
      }

    (ownResultCoord, okConstructor, errConstructor)
  }

  def weakAlias(coutputs: CompilerOutputs, expr: ReferenceExpressionTE): ReferenceExpressionTE = {
    expr.kind match {
      case sr @ StructTT(_) => {
        val structDef = coutputs.lookupStruct(sr)
        vcheck(structDef.weakable, TookWeakRefOfNonWeakableError)
      }
      case ir @ InterfaceTT(_) => {
        val interfaceDef = coutputs.lookupInterface(ir)
        vcheck(interfaceDef.weakable, TookWeakRefOfNonWeakableError)
      }
      case _ => vfail()
    }

    expr.result.reference.ownership match {
      case BorrowT => BorrowToWeakTE(expr)
      case other => vale.vwat(other)
    }
  }

  // Borrow like the . does. If it receives an owning reference, itll make a temporary.
  // If it receives an owning address, that's fine, just borrowsoftload from it.
  // Rename this someday.
  private def dotBorrow(
      coutputs: CompilerOutputs,
      nenv: NodeEnvironmentBox,
      range: RangeS,
      life: LocationInFunctionEnvironment,
      undecayedUnborrowedContainerExpr2: ExpressionT):
  (ReferenceExpressionTE) = {
    undecayedUnborrowedContainerExpr2 match {
      case a: AddressExpressionTE => {
        (localHelper.borrowSoftLoad(coutputs, a))
      }
      case r: ReferenceExpressionTE => {
        val unborrowedContainerExpr2 = r// decaySoloPack(nenv, life + 0, r)
        unborrowedContainerExpr2.result.reference.ownership match {
          case OwnT => localHelper.makeTemporaryLocal(coutputs, nenv, range, life + 1, unborrowedContainerExpr2, BorrowT)
          case BorrowT | ShareT => (unborrowedContainerExpr2)
        }
      }
    }
  }

  // Given a function1, this will give a closure (an OrdinaryClosure2 or a TemplatedClosure2)
  // returns:
  // - coutputs
  // - resulting templata
  // - exported things (from let)
  // - hoistees; expressions to hoist (like initializing blocks)
  def evaluateClosure(
      coutputs: CompilerOutputs,
      nenv: NodeEnvironmentBox,
      range: RangeS,
      name: IFunctionDeclarationNameS,
      functionS: FunctionS):
  (ReferenceExpressionTE) = {

    val functionA = astronomizeLambda(coutputs, nenv, functionS)

    val closurestructTT =
      delegate.evaluateClosureStruct(coutputs, nenv.snapshot, range, name, functionA);
    val closureCoord =
      templataCompiler.pointifyKind(coutputs, closurestructTT, OwnT)

    val constructExpr2 = makeClosureStructConstructExpression(coutputs, nenv, functionA.range, closurestructTT)
    vassert(constructExpr2.result.reference == closureCoord)
    // The result of a constructor is always an own or a share.

    // The below code was here, but i see no reason we need to put it in a temporary and point it out.
    // shouldnt this be done automatically if we try to call the function which accepts a borrow?
//    val closureVarId = FullName2(nenv.lambdaNumber, "__closure_" + function1.origin.lambdaNumber)
//    val closureLocalVar = ReferenceLocalVariable2(closureVarId, Final, resultExpr2.resultRegister.reference)
//    val letExpr2 = LetAndPoint2(closureLocalVar, resultExpr2)
//    val unlet2 = localHelper.unletLocal(nenv, closureLocalVar)
//    val dropExpr =
//      DestructorCompiler.drop(env, coutputs, nenv, unlet2)
//    val deferExpr2 = Defer2(letExpr2, dropExpr)
//    (coutputs, nenv, deferExpr2)

    constructExpr2
  }

  private def newGlobalFunctionGroupExpression(env: IEnvironment, coutputs: CompilerOutputs, name: IImpreciseNameS): ReferenceExpressionTE = {
    ReinterpretTE(
      VoidLiteralTE(),
      CoordT(
        ShareT,
        interner.intern(OverloadSetT(env, name))))
  }

  def evaluateBlockStatements(
    coutputs: CompilerOutputs,
    startingNenv: NodeEnvironment,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    block: BlockSE):
  (ReferenceExpressionTE, Set[CoordT]) = {
    blockCompiler.evaluateBlockStatements(coutputs, startingNenv, nenv, life, block)
  }

  def translatePatternList(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    patterns1: Vector[AtomSP],
    patternInputExprs2: Vector[ReferenceExpressionTE]
  ): ReferenceExpressionTE = {
    patternCompiler.translatePatternList(
      coutputs, nenv, life, patterns1, patternInputExprs2,
      (coutputs, nenv, liveCaptureLocals) => VoidLiteralTE())
  }

  def astronomizeLambda(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    functionS: FunctionS):
  FunctionA = {
    val FunctionS(rangeS, nameS, attributesS, identifyingRunesS, runeToExplicitType, paramsS, maybeRetCoordRune, rulesS, bodyS) = functionS

    val runeSToPreKnownTypeA =
      runeToExplicitType ++
        paramsS.map(_.pattern.coordRune.get.rune -> CoordTemplataType).toMap
    val runeSToType =
      new RuneTypeSolver(interner).solve(
        opts.globalOptions.sanityCheck,
        opts.globalOptions.useOptimizedSolver,
        {
          // This is here because if we tried to look up this lambda struct, it wouldn't exist yet.
          // It's not an insurmountable problem, it will exist slightly later when we're inside StructCompiler,
          // but this workaround makes for a cleaner separation between FunctionCompiler and StructCompiler
          // at least for now.
          // If this proves irksome, consider rearranging FunctionCompiler and StructCompiler's steps in
          // evaluating lambdas.
          case LambdaStructImpreciseNameS(_) => CoordTemplataType
          case n => {
            vassertSome(nenv.lookupNearestWithImpreciseName(n, Set(TemplataLookupContext))).tyype
          }
        },
        rangeS,
        false, rulesS, identifyingRunesS.map(_.rune), true, runeSToPreKnownTypeA) match {
        case Ok(t) => t
        case Err(e) => throw CompileErrorExceptionA(CouldntSolveRulesA(rangeS, e))
      }

    // Shouldnt fail because we got a complete solve on the rules
    val tyype = PostParser.determineDenizenType(FunctionTemplataType, identifyingRunesS.map(_.rune), runeSToType).getOrDie()

    vale.highertyping.FunctionA(
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
    coutputs: CompilerOutputs,
    startingNenv: NodeEnvironment,
    nenv: NodeEnvironmentBox,
    range: RangeS,
    life: LocationInFunctionEnvironment,
    exprTE: ReferenceExpressionTE):
  ReferenceExpressionTE = {
    val unreversedVariablesToDestruct = nenv.snapshot.getLiveVariablesIntroducedSince(startingNenv)

    val newExpr =
      if (unreversedVariablesToDestruct.isEmpty) {
        exprTE
      } else {
        exprTE.kind match {
          case VoidT() => {
            val reversedVariablesToDestruct = unreversedVariablesToDestruct.reverse
            // Dealiasing should be done by hammer. But destructors are done here
            val destroyExpressions = localHelper.unletAndDropAll(coutputs, nenv, range, reversedVariablesToDestruct)

            Compiler.consecutive(
              (Vector(exprTE) ++ destroyExpressions) :+
                VoidLiteralTE())
          }
          case NeverT(_) => {
            // In this case, we want to not drop them, so we can support things like:
            //   func drop(self Server) {
            //     panic("unreachable");
            //   }
            // and not drop Server.

            val reversedVariablesToDestruct = unreversedVariablesToDestruct.reverse
            val destroyExpressions = localHelper.unletAllWithoutDropping(coutputs, nenv, range, reversedVariablesToDestruct)
            // Just dont add in the destroyExpressions, let em go.
            // We did the above simply to mark them as unstackified.
            exprTE
          }
          case _ => {
            val (resultifiedExpr, resultLocalVariable) =
              resultifyExpressions(nenv, life + 1, exprTE)

            val reversedVariablesToDestruct = unreversedVariablesToDestruct.reverse
            // Dealiasing should be done by hammer. But destructors are done here
            val destroyExpressions = localHelper.unletAndDropAll(coutputs, nenv, range, reversedVariablesToDestruct)

            Compiler.consecutive(
              (Vector(resultifiedExpr) ++ destroyExpressions) :+
                localHelper.unletLocalWithoutDropping(nenv, resultLocalVariable))
          }
        }
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
    val resultVarId = nenv.fullName.addStep(interner.intern(TypingPassBlockResultVarNameT(life)))
    val resultVariable = ReferenceLocalVariableT(resultVarId, FinalT, expr.result.reference)
    val resultLet = LetNormalTE(resultVariable, expr)
    nenv.addVariable(resultVariable)
    (resultLet, resultVariable)
  }

//  def mootAll(
//    coutputs: CompilerOutputs,
//    nenv: NodeEnvironmentBox,
//    variables: Vector[ILocalVariableT]):
//  (Vector[ReferenceExpressionTE]) = {
//    variables.map({ case head =>
//      ast.UnreachableMootTE(localHelper.unletLocal(nenv, head))
//    })
//  }
}
