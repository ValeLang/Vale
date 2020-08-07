package net.verdagon.vale.templar;

import net.verdagon.vale.astronomer._
import net.verdagon.vale.astronomer.ruletyper.{IRuleTyperEvaluatorDelegate, RuleTyperEvaluator}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.FunctionTemplar.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
import net.verdagon.vale.templar.function.{DestructorTemplar, DropHelper, FunctionTemplar}
import net.verdagon.vale.templar.templata.TemplataTemplar
import net.verdagon.vale.{vassert, vassertSome, vcheck, vcurious, vfail, vimpl, vwat}

import scala.collection.immutable.{List, Map, Nil, Set}

case class TookWeakRefOfNonWeakableError() extends Throwable

trait IExpressionTemplarDelegate {
  def evaluateTemplatedFunctionFromCallForPrototype(
    temputs: TemputsBox,
    functionTemplata: FunctionTemplata,
    explicitTemplateArgs: List[ITemplata],
    args: List[ParamFilter]):
  IEvaluateFunctionResult[Prototype2]

  def evaluateClosureStruct(
    temputs: TemputsBox,
    containingFunctionEnv: FunctionEnvironment,
    name: LambdaNameA,
    function1: BFunctionA):
  StructRef2
}

class ExpressionTemplar(
    opts: TemplarOptions,
    templataTemplar: TemplataTemplar,
    inferTemplar: InferTemplar,
    arrayTemplar: ArrayTemplar,
    structTemplar: StructTemplar,
    packTemplar: PackTemplar,
    sequenceTemplar: SequenceTemplar,
    overloadTemplar: OverloadTemplar,
  dropHelper: DropHelper,
    convertHelper: ConvertHelper,
    delegate: IExpressionTemplarDelegate) {
  val localHelper = new LocalHelper(opts, dropHelper)
  val callTemplar = new CallTemplar(opts, templataTemplar, convertHelper, localHelper, overloadTemplar)
  val patternTemplar = new PatternTemplar(opts, inferTemplar, convertHelper, dropHelper, localHelper)
  val blockTemplar = new BlockTemplar(opts, dropHelper, localHelper, new IBlockTemplarDelegate {
    override def evaluateAndCoerceToReferenceExpression(
        temputs: TemputsBox, fate: FunctionEnvironmentBox, expr1: IExpressionAE):
    (ReferenceExpression2, Set[Coord]) = {
      ExpressionTemplar.this.evaluateAndCoerceToReferenceExpression(temputs, fate, expr1)
    }
  })

  private def evaluateList(
      temputs: TemputsBox,
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
      temputs: TemputsBox,
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

  private def evaluateLookup(
    temputs: TemputsBox,
    fate: FunctionEnvironmentBox,
    name: IVarName2,
    targetOwnership: OwnershipP):
  (Option[Expression2]) = {
    evaluateAddressibleLookup(temputs, fate, name) match {
      case Some(x) => {
        val thing = localHelper.softLoad(fate, x, Conversions.evaluateOwnership(targetOwnership))
        (Some(thing))
      }
      case None => {
        fate.getNearestTemplataWithAbsoluteName2(name, Set(TemplataLookupContext)) match {
          case Some(IntegerTemplata(num)) => (Some(IntLiteral2(num)))
          case Some(BooleanTemplata(bool)) => (Some(BoolLiteral2(bool)))
          case None => (None)
        }
      }
    }
  }

  private def evaluateAddressibleLookup(
      temputs: TemputsBox,
      fate: FunctionEnvironmentBox,
      nameA: IVarNameA):
  Option[AddressExpression2] = {
    fate.getVariable(NameTranslator.translateVarNameStep(nameA)) match {
      case Some(alv @ AddressibleLocalVariable2(_, _, reference)) => {
        Some(LocalLookup2(alv, reference))
      }
      case Some(rlv @ ReferenceLocalVariable2(id, _, reference)) => {
        Some(LocalLookup2(rlv, reference))
      }
      case Some(AddressibleClosureVariable2(id, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == Mutable) Borrow else Share
        val closuredVarsStructRefRef = Coord(ownership, closuredVarsStructRef)
        val name2 = fate.fullName.addStep(ClosureParamName2())
        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookup2(
              ReferenceLocalVariable2(name2, Final, closuredVarsStructRefRef),
              closuredVarsStructRefRef))
//        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        val varName = nameA match { case CodeVarNameA(n) => n case _ => vwat() }
//        val index =
//          closuredVarsStructDef.members.indexWhere(_.name == varName)
//        vassert(index >= 0)
        val lookup = AddressMemberLookup2(borrowExpr, id, tyype)
        Some(lookup)
      }
      case Some(ReferenceClosureVariable2(varName, closuredVarsStructRef, _, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == Mutable) Borrow else Share
        val closuredVarsStructRefCoord = Coord(ownership, closuredVarsStructRef)
        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookup2(
              ReferenceLocalVariable2(fate.fullName.addStep(ClosureParamName2()), Final, closuredVarsStructRefCoord),
              closuredVarsStructRefCoord))
//        val index = closuredVarsStructDef.members.indexWhere(_.name == varName)
        val lookup =
          ReferenceMemberLookup2(
            borrowExpr,
            varName,
            tyype)
        Some(lookup)
      }
      case None => None
    }
  }

  private def evaluateAddressibleLookup(
    temputs: TemputsBox,
    fate: FunctionEnvironmentBox,
    name2: IVarName2):
  Option[AddressExpression2] = {
    fate.getVariable(name2) match {
      case Some(alv @ AddressibleLocalVariable2(_, _, reference)) => {
        Some(LocalLookup2(alv, reference))
      }
      case Some(rlv @ ReferenceLocalVariable2(id, _, reference)) => {
        Some(LocalLookup2(rlv, reference))
      }
      case Some(AddressibleClosureVariable2(id, closuredVarsStructRef, variability, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == Mutable) Borrow else Share
        val closuredVarsStructRefRef = Coord(ownership, closuredVarsStructRef)
        val closureParamVarName2 = fate.fullName.addStep(ClosureParamName2())
        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookup2(
              ReferenceLocalVariable2(closureParamVarName2, Final, closuredVarsStructRefRef),
              closuredVarsStructRefRef))
        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        vassert(closuredVarsStructDef.members.exists(member => closuredVarsStructRef.fullName.addStep(member.name) == id))
        val lookup = AddressMemberLookup2(borrowExpr, id, tyype)
        Some(lookup)
      }
      case Some(ReferenceClosureVariable2(varName, closuredVarsStructRef, _, tyype)) => {
        val mutability = Templar.getMutability(temputs, closuredVarsStructRef)
        val ownership = if (mutability == Mutable) Borrow else Share
        val closuredVarsStructRefCoord = Coord(ownership, closuredVarsStructRef)
        val closuredVarsStructDef = temputs.lookupStruct(closuredVarsStructRef)
        val borrowExpr =
          localHelper.borrowSoftLoad(
            temputs,
            LocalLookup2(
              ReferenceLocalVariable2(fate.fullName.addStep(ClosureParamName2()), Final, closuredVarsStructRefCoord),
              closuredVarsStructRefCoord))
//        val index = closuredVarsStructDef.members.indexWhere(_.name == varName)
        val lookup =
          ReferenceMemberLookup2(
            borrowExpr,
            varName,
            tyype)
        Some(lookup)
      }
      case None => None
    }
  }

  private def makeClosureStructConstructExpression(
      temputs: TemputsBox,
      fate: FunctionEnvironmentBox,
      closureStructRef: StructRef2):
  (ReferenceExpression2) = {
    val closureStructDef = temputs.lookupStruct(closureStructRef);
    // Note, this is where the unordered closuredNames set becomes ordered.
    val lookupExpressions2 =
      closureStructDef.members.map({
        case StructMember2(memberName, variability, tyype) => {
          val lookup =
            evaluateAddressibleLookup(temputs, fate, memberName) match {
              case None => vfail("Couldn't find " + memberName)
              case Some(l) => l
            }
          tyype match {
            case ReferenceMemberType2(reference) => {
              // We might have to softload an own into a borrow, but the referends
              // should at least be the same right here.
              vassert(reference.referend == lookup.resultRegister.reference.referend)
              // Closures never contain owning references.
              // If we're capturing an own, then on the inside of the closure
              // it's a borrow. See "Captured own is borrow" test for more.
              localHelper.softLoad(fate, lookup, Borrow)
            }
            case AddressMemberType2(reference) => {
              vassert(reference == lookup.resultRegister.reference)
              (lookup)
            }
          }
        }
      });
    val ownership = if (closureStructDef.mutability == Mutable) Own else Share
    val resultPointerType = Coord(ownership, closureStructRef)
    val constructExpr2 =
      Construct2(closureStructRef, resultPointerType, lookupExpressions2)
    (constructExpr2)
  }

  def evaluateAndCoerceToReferenceExpression(
      temputs: TemputsBox,
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
    }
  }

  def coerceToReferenceExpression(fate: FunctionEnvironmentBox, expr2: Expression2):
  (ReferenceExpression2) = {
    expr2 match {
      case r : ReferenceExpression2 => (r)
      case a : AddressExpression2 => localHelper.softLoad(fate, a, a.coerceToOwnership)
    }
  }

  private def evaluateExpectedAddressExpression(
      temputs: TemputsBox,
      fate: FunctionEnvironmentBox,
      expr1: IExpressionAE):
  (AddressExpression2, Set[Coord]) = {
    val (expr2, returns) =
      evaluate(temputs, fate, expr1)
    expr2 match {
      case a : AddressExpression2 => (a, returns)
      case _ : ReferenceExpression2 => vfail("Expected reference expression!")
    }
  }

  // returns:
  // - temputs
  // - "fate", moved locals (subset of exporteds)
  // - resulting expression
  // - all the types that are returned from inside the body via ret
  private def evaluate(
      temputs: TemputsBox,
      fate: FunctionEnvironmentBox,
      expr1: IExpressionAE):
  (Expression2, Set[Coord]) = {
    expr1 match {
      case VoidAE() => (VoidLiteral2(), Set())
      case IntLiteralAE(i) => (IntLiteral2(i), Set())
      case BoolLiteralAE(i) => (BoolLiteral2(i), Set())
      case StrLiteralAE(s) => (StrLiteral2(s), Set())
      case FloatLiteralAE(f) => (FloatLiteral2(f), Set())
      case ArgLookupAE(index) => {
        val paramCoordRuneA = fate.function.params(index).pattern.coordRune
        val paramCoordRune = NameTranslator.translateRune(paramCoordRuneA)
        val paramCoordTemplata = fate.getNearestTemplataWithAbsoluteName2(paramCoordRune, Set(TemplataLookupContext)).get
        val CoordTemplata(paramCoord) = paramCoordTemplata
        (ArgLookup2(index, paramCoord), Set())
      }
      case FunctionCallAE(TemplateSpecifiedLookupAE(name, templateArgTemplexesS), argsExprs1) => {
        val (argsExprs2, returnsFromArgs) =
          evaluateAndCoerceToReferenceExpressions(temputs, fate, argsExprs1)
        val callExpr2 =
          callTemplar.evaluatePrefixCall(
            temputs,
            fate,
            newGlobalFunctionGroupExpression(fate, GlobalFunctionFamilyNameA(name)),
            templateArgTemplexesS,
            argsExprs2)
        (callExpr2, returnsFromArgs)
      }
      case FunctionCallAE(TemplateSpecifiedLookupAE(name, templateArgTemplexesS), argsExprs1) => {
        val (argsExprs2, returnsFromArgs) =
          evaluateAndCoerceToReferenceExpressions(temputs, fate, argsExprs1)
        val callExpr2 =
          callTemplar.evaluateNamedPrefixCall(temputs, fate, GlobalFunctionFamilyNameA(name), templateArgTemplexesS, argsExprs2)
        (callExpr2, returnsFromArgs)
      }
      case FunctionCallAE(FunctionLoadAE(name), argsPackExpr1) => {
        val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, argsPackExpr1)
        val callExpr2 =
          callTemplar.evaluateNamedPrefixCall(temputs, fate, name, List(), argsExprs2)
        (callExpr2, returnsFromArgs)
      }
      case FunctionCallAE(callableExpr1, argsExprs1) => {
        val (undecayedCallableExpr2, returnsFromCallable) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, callableExpr1);
        val decayedCallableExpr2 =
          localHelper.maybeSoftLoad(fate, undecayedCallableExpr2, Borrow)
        val decayedCallableReferenceExpr2 =
          coerceToReferenceExpression(fate, decayedCallableExpr2)
        val (argsExprs2, returnsFromArgs) =
          evaluateAndCoerceToReferenceExpressions(temputs, fate, argsExprs1)
        val functionPointerCall2 =
          callTemplar.evaluatePrefixCall(temputs, fate, decayedCallableReferenceExpr2, List(), argsExprs2)
        (functionPointerCall2, returnsFromCallable ++ returnsFromArgs)
      }

      case LendAE(innerExpr1, targetOwnership) => {
        val (innerExpr2, returnsFromInner) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, innerExpr1);
        val resultExpr2 =
          innerExpr2.resultRegister.underlyingReference.ownership match {
            case Own => {
              targetOwnership match {
                case OwnP => vcurious() // Can we even coerce to an owning reference?
                case BorrowP => localHelper.makeTemporaryLocal(temputs, fate, innerExpr2)
                case WeakP => weakAlias(temputs.temputs, localHelper.makeTemporaryLocal(temputs, fate, innerExpr2))
                case ShareP => vfail()
              }
            }
            case Borrow => {
              targetOwnership match {
                case OwnP => vcurious() // Can we even coerce to an owning reference?
                case BorrowP => innerExpr2
                case WeakP => weakAlias(temputs.temputs, innerExpr2)
                case ShareP => vfail()
              }
            }
            case Weak => {
              targetOwnership match {
                case OwnP => vcurious() // Can we even coerce to an owning reference?
                case BorrowP => vfail() // Need to call lock() to do this
                case WeakP => innerExpr2
                case ShareP => vfail()
              }
            }
            case Share => {
              targetOwnership match {
                case OwnP => vcurious() // Can we even coerce to an owning reference?
                case BorrowP => {
                  // Allow this, we can do & on a share ref, itll just give us a share ref.
                  innerExpr2
                }
                case WeakP => vfail()
                case ShareP => innerExpr2
              }
            }
          }
        (resultExpr2, returnsFromInner)
      }
      case LockWeakAE(innerExpr1) => {
        val (innerExpr2, returnsFromInner) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, innerExpr1);
        vcheck(innerExpr2.resultRegister.reference.ownership == Weak, "Can only lock a weak")

        val kind = innerExpr2.referend
        val borrowCoord = Coord(Borrow, kind)

        val interfaceTemplata =
          fate.getNearestTemplataWithName(CodeTypeNameA("Opt"), Set(TemplataLookupContext)) match {
            case Some(it @ InterfaceTemplata(_, _)) => it
            case _ => vfail()
          }
        val optBorrowInterfaceRef =
          structTemplar.getInterfaceRef(temputs, interfaceTemplata, List(CoordTemplata(borrowCoord)))
        val ownOptBorrowCoord = Coord(Own, optBorrowInterfaceRef)

        val someConstructorTemplata =
          fate.getNearestTemplataWithName(GlobalFunctionFamilyNameA("Some"), Set(ExpressionLookupContext)) match {
            case Some(ft @ FunctionTemplata(_, _)) => ft
            case _ => vwat();
          }
        val someConstructor =
          delegate.evaluateTemplatedFunctionFromCallForPrototype(
            temputs, someConstructorTemplata, List(CoordTemplata(borrowCoord)), List(ParamFilter(borrowCoord, None))) match {
            case seff @ EvaluateFunctionFailure(_) => vfail(seff.toString)
            case EvaluateFunctionSuccess(p) => p
          }

        val noneConstructorTemplata =
          fate.getNearestTemplataWithName(GlobalFunctionFamilyNameA("None"), Set(ExpressionLookupContext)) match {
            case Some(ft @ FunctionTemplata(_, _)) => ft
            case _ => vwat();
          }
        val noneConstructor =
          delegate.evaluateTemplatedFunctionFromCallForPrototype(
            temputs, noneConstructorTemplata, List(CoordTemplata(borrowCoord)), List()) match {
            case seff @ EvaluateFunctionFailure(_) => vfail(seff.toString)
            case EvaluateFunctionSuccess(p) => p
          }

        val resultExpr2 = LockWeak2(innerExpr2, ownOptBorrowCoord, someConstructor, noneConstructor)
        (resultExpr2, returnsFromInner)
      }
      case LocalLoadAE(nameA, targetOwnership) => {
        val name = NameTranslator.translateVarNameStep(nameA)
        val lookupExpr1 =
          evaluateLookup(temputs, fate, name, targetOwnership) match {
            case (None) => vfail("Couldnt find " + name)
            case (Some(x)) => (x)
          }
        (lookupExpr1, Set())
      }
      case FunctionLoadAE(name) => {
        // Note, we don't get here if we're about to call something with this, that's handled
        // by a different case.

        // We can't use *anything* from the global environment; we're in expression context,
        // not in templata context.

        val templataFromEnv =
          fate.getAllTemplatasWithName(name, Set(ExpressionLookupContext)) match {
            case List(BooleanTemplata(value)) => BoolLiteral2(value)
            case List(IntegerTemplata(value)) => IntLiteral2(value)
            case templatas if templatas.nonEmpty && templatas.collect({ case FunctionTemplata(_, _) => case ExternFunctionTemplata(_) => }).size == templatas.size => {
              newGlobalFunctionGroupExpression(fate, name)
            }
            case things if things.size > 1 => {
              vfail("Found too many different things named \"" + name + "\" in env:\n" + things.map("\n" + _));
            }
            case List() => {
              println("members: " + fate.getAllTemplatasWithName(name, Set(ExpressionLookupContext, TemplataLookupContext)))
              vfail("Couldn't find anything named \"" + name + "\" in env:\n" + fate);
            }
          }
        (templataFromEnv, Set())
      }
      case LocalMutateAE(name, sourceExpr1) => {
        val destinationExpr2 =
          evaluateAddressibleLookup(temputs, fate, name) match {
            case None => vfail("Couldnt find " + name)
            case Some(x) => x
          }
        val (unconvertedSourceExpr2, returnsFromSource) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, sourceExpr1)

        val isConvertible =
          templataTemplar.isTypeConvertible(
            temputs, unconvertedSourceExpr2.resultRegister.reference, destinationExpr2.resultRegister.reference)
        vassert(isConvertible)
        val convertedSourceExpr2 =
          convertHelper.convert(fate.snapshot, temputs, unconvertedSourceExpr2, destinationExpr2.resultRegister.reference);

        val mutate2 = Mutate2(destinationExpr2, convertedSourceExpr2);
        (mutate2, returnsFromSource)
      }
      case ExprMutateAE(destinationExpr1, sourceExpr1) => {
        val (unconvertedSourceExpr2, returnsFromSource) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, sourceExpr1)
        val (destinationExpr2, returnsFromDestination) =
          evaluateExpectedAddressExpression(temputs, fate, destinationExpr1)
        val isConvertible =
          templataTemplar.isTypeConvertible(temputs, unconvertedSourceExpr2.resultRegister.reference, destinationExpr2.resultRegister.reference)
        if (!isConvertible) {
          vfail("In mutate, can't convert from: " + unconvertedSourceExpr2.resultRegister.reference + "\nto: " + destinationExpr2.resultRegister.reference)
        }
        val convertedSourceExpr2 =
          convertHelper.convert(fate.snapshot, temputs, unconvertedSourceExpr2, destinationExpr2.resultRegister.reference);

        val mutate2 = Mutate2(destinationExpr2, convertedSourceExpr2);
        (mutate2, returnsFromSource ++ returnsFromDestination)
      }
      case CheckRefCountAE(refExpr1, category, numExpr1) => {
        val (refExpr2, returnsFromRef) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, refExpr1);
        val (numExpr2, returnsFromNum) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, numExpr1);
        (CheckRefCount2(refExpr2, Conversions.evaluateRefCountCategory(category), numExpr2), returnsFromRef ++ returnsFromNum)
      }
      case TemplateSpecifiedLookupAE(name, templateArgs1) => {
        // So far, we only allow these when they're immediately called like functions
        vfail("unimplemented")
      }
      case DotCallAE(containerExpr1, indexExpr1) => {
        val (unborrowedContainerExpr2, returnsFromContainerExpr) =
          evaluate(temputs, fate, containerExpr1);
        val containerExpr2 =
          dotBorrow(temputs, fate, unborrowedContainerExpr2)

        val (indexExpr2, returnsFromIndexExpr) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, indexExpr1);

        val exprTemplata =
          containerExpr2.resultRegister.reference.referend match {
            case at @ UnknownSizeArrayT2(_) => {
              UnknownSizeArrayLookup2(containerExpr2, at, indexExpr2)
            }
            case at @ KnownSizeArrayT2(_, _) => {
              ArraySequenceLookup2(
                containerExpr2,
                at,
                indexExpr2)
            }
            case at @ TupleT2(members, understruct) => {
              indexExpr2 match {
                case IntLiteral2(index) => {
                  var understructDef = temputs.lookupStruct(understruct);
                  val memberType = understructDef.members(index).tyype
                  val memberName = understructDef.fullName.addStep(understructDef.members(index).name)
                  ReferenceMemberLookup2(containerExpr2, memberName, memberType.reference)
                }
                case _ => vimpl("impl random access of structs' members")
              }
            }
            // later on, a map type could go here
          }
        (exprTemplata, returnsFromContainerExpr ++ returnsFromIndexExpr)
      }
      case DotAE(containerExpr1, memberNameStr, borrowContainer) => {
        var memberName = CodeVarName2(memberNameStr)
        val (unborrowedContainerExpr2, returnsFromContainerExpr) =
          evaluate(temputs, fate, containerExpr1);
        val containerExpr2 =
          dotBorrow(temputs, fate, unborrowedContainerExpr2)

        val expr2 =
          containerExpr2.resultRegister.reference.referend match {
            case structRef @ StructRef2(_) => {
              temputs.lookupStruct(structRef) match {
                case structDef : StructDefinition2 => {
                  val (structMember, memberIndex) = structDef.getMemberAndIndex(memberName)
                  val memberFullName = structDef.fullName.addStep(structDef.members(memberIndex).name)
                  val memberType = structMember.tyype.expectReferenceMember().reference;
                  ReferenceMemberLookup2(
                    containerExpr2,
                    memberFullName,
                    memberType)
                }
              }
            }
            case TupleT2(_, structRef) => {
              temputs.lookupStruct(structRef) match {
                case structDef @ StructDefinition2(_, _, _, _, _, _) => {
                  val (structMember, memberIndex) = structDef.getMemberAndIndex(memberName)
                  val memberFullName = structDef.fullName.addStep(structDef.members(memberIndex).name)
                  val memberType = structMember.tyype.expectReferenceMember().reference;
                  ReferenceMemberLookup2(containerExpr2, memberFullName, memberType)
                }
              }
            }
            case as @ KnownSizeArrayT2(_, _) => {
              if (memberNameStr.forall(Character.isDigit)) {
                ArraySequenceLookup2(
                  containerExpr2,
                  as,
                  IntLiteral2(memberNameStr.toInt))
              } else {
                vfail("Sequence has no member named " + memberNameStr)
              }
            }
            case at @ UnknownSizeArrayT2(_) => {
              if (memberNameStr.forall(Character.isDigit)) {
                UnknownSizeArrayLookup2(
                  containerExpr2,
                  at,
                  IntLiteral2(memberNameStr.toInt))
              } else {
                vfail("Array has no member named " + memberNameStr)
              }
            }
            case other => {
              vfail("Can't apply ." + memberNameStr + " to " + other)
            }
          }

        (expr2, returnsFromContainerExpr)
      }
      case FunctionAE(name, function1 @ FunctionA(_, _, _, _, _, _, _, _, _, _, CodeBodyA(body))) => {
        val callExpr2 = evaluateClosure(temputs, fate, name, BFunctionA(function1, body))
        (callExpr2, Set())
      }
      case SequenceEAE(elements1) => {
        val (exprs2, returnsFromElements) =
          evaluateAndCoerceToReferenceExpressions(temputs, fate, elements1);

        // would we need a sequence templata? probably right?
        val expr2 =
          sequenceTemplar.evaluate(fate, temputs, exprs2)
        (expr2, returnsFromElements)
      }
//      case ConstructAE(type1, argExprs1) => {
//        val (argExprs2, returnsFromArgs) =
//          evaluateList(temputs, fate, argExprs1);
//
//        val stuff = vfail() // this is where we do the thing
//
//        val kind = templataTemplar.evaluateTemplex(fate.snapshot, temputs, stuff)
//        val constructExpr2 =
//          kind match {
//            case KindTemplata(structRef2 @ StructRef2(_)) => {
//              val structDef2 = temputs.lookupStruct(structRef2)
//              val ownership = if (structDef2.mutability == Mutable) Own else Share
//              val resultPointerType = Coord(ownership, structRef2)
//              Construct2(structRef2, resultPointerType, argExprs2)
//            }
//            case _ => vfail("wat")
//          }
//        (constructExpr2, returnsFromArgs)
//      }
      case ConstructArrayAE(elementCoordTemplex, sizeExpr1, generatorExpr1, arrayMutabilityP) => {
        val (CoordTemplata(elementCoord)) = templataTemplar.evaluateTemplex(fate.snapshot, temputs, elementCoordTemplex)

        val arrayMutability = Conversions.evaluateMutability(arrayMutabilityP)

        val (sizeExpr2, returnsFromSize) =
          evaluate(temputs, fate, sizeExpr1);

        val (generatorExpr2, returnsFromGenerator) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, generatorExpr1);

        val memberType2 =
          generatorExpr2.referend match {
            case InterfaceRef2(FullName2(List(), CitizenName2("IFunction1", List(MutabilityTemplata(_), CoordTemplata(Coord(Share, Int2())), CoordTemplata(element))))) => element
            case other => vwat(other.toString)
          }

        val isConvertible =
          templataTemplar.isTypeTriviallyConvertible(temputs, memberType2, elementCoord)
        if (!isConvertible) {
          vfail(memberType2 + " cant convert to " + elementCoord)
        }

        if (arrayMutability == Immutable &&
            Templar.getMutability(temputs, elementCoord.referend) == Mutable) {
          vfail("Can't have an immutable array of mutable elements!")
        }
        val arrayType = arrayTemplar.makeUnknownSizeArrayType(fate.snapshot, temputs, elementCoord, arrayMutability)

        val sizeRefExpr2 = coerceToReferenceExpression(fate, sizeExpr2)
        vassert(sizeRefExpr2.resultRegister.expectReference().reference == Coord(Share, Int2()))

        val constructExpr2 =
          ConstructArray2(
            arrayType,
            sizeRefExpr2,
            generatorExpr2)
        (constructExpr2, returnsFromSize ++ returnsFromGenerator)
      }
      case LetAE(rulesA, typeByRune, localRunesA, pattern, sourceExpr1) => {
        val (sourceExpr2, returnsFromSource) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, sourceExpr1)

        val fateSnapshot = fate.snapshot
        val lets2 =
          patternTemplar.nonCheckingInferAndTranslate(
            temputs, fate, rulesA, typeByRune, localRunesA, pattern, sourceExpr2)

        val resultExprBlock2 = Consecutor2(lets2)

        (resultExprBlock2, returnsFromSource)
      }
      case RuneLookupAE(runeA,tyype) => {
        val templata = vassertSome(fate.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(runeA), Set(TemplataLookupContext)))
        (tyype, templata) match {
          case (IntegerTemplataType, IntegerTemplata(value)) => (IntLiteral2(value), Set())
        }
      }
      case IfAE(condition1, thenBody1, elseBody1) => {
        val (conditionExpr2, returnsFromCondition) =
          blockTemplar.evaluateBlock(fate, temputs, condition1)
        val fateAfterBranch = fate.functionEnvironment

        val FunctionEnvironment(parentEnv, function, functionFullName, entries, maybeReturnType, scoutedLocals, counterBeforeBranch, variablesBeforeBranch, _) = fateAfterBranch

        val fateForThen = FunctionEnvironmentBox(fateAfterBranch)
        val (uncoercedThenExpr2, returnsFromThen) = blockTemplar.evaluateBlock(fateForThen, temputs, thenBody1)
        val fateAfterThen = fateForThen.functionEnvironment

        val thenContinues = uncoercedThenExpr2.resultRegister.reference.referend != Never2()
        val FunctionEnvironment(_, _, _, _, _, _, counterAfterThen, variablesAfterThen, movedsAfterThen) = fateAfterThen

        // Give the else branch the same fate the then branch got, except let the counter
        // remain higher.
        val fateForElse = FunctionEnvironmentBox(fateAfterBranch)
        val _ = fateForElse.nextCounters(counterAfterThen - counterBeforeBranch)
        val (uncoercedElseExpr2, returnsFromElse) =
          blockTemplar.evaluateBlock(fateForElse, temputs, elseBody1)
        val fateAfterElse = fateForElse.functionEnvironment

        val elseContinues = uncoercedElseExpr2.resultRegister.reference.referend != Never2()
        val FunctionEnvironment(_, _, _, _, _, _, counterAfterElse, variablesAfterElse, movedsAfterElse) = fateAfterElse

        val commonType =
          (uncoercedThenExpr2.referend, uncoercedElseExpr2.referend) match {
            case (Never2(), Never2()) => uncoercedThenExpr2.resultRegister.reference
            case (Never2(), _) => uncoercedElseExpr2.resultRegister.reference
            case (_, Never2()) => uncoercedThenExpr2.resultRegister.reference
            case (a, b) if a == b => uncoercedThenExpr2.resultRegister.reference
            case _ => vimpl()
          }
        val thenExpr2 = convertHelper.convert(fate.snapshot, temputs, uncoercedThenExpr2, commonType)
        val elseExpr2 = convertHelper.convert(fate.snapshot, temputs, uncoercedElseExpr2, commonType)

        val ifExpr2 = If2(conditionExpr2, thenExpr2, elseExpr2)

        // We should have no new variables introduced.
        vassert(variablesBeforeBranch == variablesAfterThen)
        vassert(variablesBeforeBranch == variablesAfterElse)

        val finalFate =
          if (thenContinues == elseContinues) { // Both continue, or both don't
            // Each branch might have moved some things. Make sure they moved the same things.
            if (movedsAfterThen != movedsAfterElse) {
              vfail("Must move same variables from inside branches!\nFrom then branch: " + movedsAfterThen + "\nFrom else branch: " + movedsAfterElse)
            }

            val mergedFate =
              FunctionEnvironment(
                parentEnv, function, functionFullName, entries, maybeReturnType, scoutedLocals,
                counterAfterElse, // Since else took up where then left off
                variablesBeforeBranch,
                movedsAfterThen)

            // vfail("merge these function states!")
            // we used to do conditionExporteds ++ thenExporteds ++ elseExporteds

            (mergedFate)
          } else {
            // One of them continues and the other does not.
            if (thenContinues) {
              (fateAfterThen)
            } else if (elseContinues) {
              (fateAfterElse)
            } else vfail()
          }
        fate.functionEnvironment = finalFate
        (ifExpr2, returnsFromCondition ++ returnsFromThen ++ returnsFromElse)
      }
      case WhileAE(condition1, body1) => {
        val (conditionExpr2, returnsFromCondition) =
          blockTemplar.evaluateBlock(fate, temputs, condition1)
        val (bodyExpr2, returnsFromBody) =
          blockTemplar.evaluateBlock(fate, temputs, body1)

        vassert(fate.variables == fate.variables)
        (fate.moveds != fate.moveds, "Don't move things from inside whiles!")

        val thenBody =
          if (bodyExpr2.referend == Never2()) {
            bodyExpr2
          } else {
            Block2(List(bodyExpr2, BoolLiteral2(true)))
          }

        val ifExpr2 =
          If2(
            conditionExpr2,
            thenBody,
            Block2(List(BoolLiteral2(false))))
        val whileExpr2 = While2(Block2(List(ifExpr2)))
        (whileExpr2, returnsFromCondition ++ returnsFromBody)
      }
      case b @ BlockAE(_, _) => {
        val (block2, returnsFromBlock) =
          blockTemplar.evaluateBlock(fate, temputs, b);
        (block2, returnsFromBlock)
      }
      case ArrayLengthAE(arrayExprA) => {
        val (arrayExpr2, returnsFromArrayExpr) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, arrayExprA);
        (ArrayLength2(arrayExpr2), returnsFromArrayExpr)
      }
      case DestructAE(innerAE) => {
        val (innerExpr2, returnsFromArrayExpr) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, innerAE);

        // should just ignore others, TODO impl
        vcheck(innerExpr2.resultRegister.reference.ownership == Own, "can only destruct own")

        val destroy2 =
          innerExpr2.referend match {
            case structRef @ StructRef2(_) => {
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
            case _ => vfail()
          }
        (destroy2, returnsFromArrayExpr)
      }
      case ReturnAE(innerExprA) => {
        val (uncastedInnerExpr2, returnsFromInnerExpr) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, innerExprA);

        val innerExpr2 =
          fate.maybeReturnType match {
            case None => (uncastedInnerExpr2)
            case Some(returnType) => {
              templataTemplar.isTypeConvertible(temputs, uncastedInnerExpr2.resultRegister.reference, returnType) match {
                case (false) => vfail("Can't convert " + uncastedInnerExpr2.resultRegister.reference + " to return type " + returnType)
                case (true) => {
                  convertHelper.convert(fate.snapshot, temputs, uncastedInnerExpr2, returnType)
                }
              }
            }
          }

        val variablesToDestruct = fate.getAllLiveLocals()
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
      temputs: TemputsBox,
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
          case Borrow | Share => (unborrowedContainerExpr2)
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
      temputs: TemputsBox,
      fate: FunctionEnvironmentBox,
      name: LambdaNameA,
      function1: BFunctionA):
  (ReferenceExpression2) = {

    val closureStructRef2 =
      delegate.evaluateClosureStruct(temputs, fate.snapshot, name, function1);
    val closureCoord =
      templataTemplar.pointifyReferend(temputs, closureStructRef2, Own)

    val constructExpr2 = makeClosureStructConstructExpression(temputs, fate, closureStructRef2)
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
      PackTemplar.emptyPackExpression,
      Coord(
        Share,
        OverloadSet(
          env.snapshot,
          name,
          Program2.emptyTupleStructRef)))
  }

  def evaluateBlockStatements(
    temputs: TemputsBox, startingFate: FunctionEnvironment, fate: FunctionEnvironmentBox, exprs: List[IExpressionAE]):
  (List[ReferenceExpression2], Set[Coord]) = {
    blockTemplar.evaluateBlockStatements(temputs, startingFate, fate, exprs)
  }

  def nonCheckingTranslateList(temputs: TemputsBox, fate: FunctionEnvironmentBox, patterns1: List[AtomAP], patternInputExprs2: List[ReferenceExpression2]): List[ReferenceExpression2] = {
    patternTemplar.nonCheckingTranslateList(temputs, fate, patterns1, patternInputExprs2)
  }
}
