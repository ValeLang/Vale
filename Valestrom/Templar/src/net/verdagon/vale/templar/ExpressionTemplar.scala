package net.verdagon.vale.templar;

import net.verdagon.vale.astronomer._
import net.verdagon.vale.astronomer.ruletyper.{IRuleTyperEvaluatorDelegate, RuleTyperEvaluator}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.BlockTemplar.unletAll
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplar}
import net.verdagon.vale.templar.templata.TemplataTemplar
import net.verdagon.vale.{vassert, vassertSome, vcheck, vfail, vimpl, vwat}

import scala.collection.immutable.{List, Map, Nil, Set}

object ExpressionTemplar {
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
    borrow: Boolean):
  (Option[Expression2]) = {
    evaluateAddressibleLookup(temputs, fate, name) match {
      case Some(x) => {
        val thing = ExpressionTemplar.softLoad(fate, x, borrow)
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
          ExpressionTemplar.borrowSoftLoad(
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
          ExpressionTemplar.borrowSoftLoad(
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
          ExpressionTemplar.borrowSoftLoad(
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
          ExpressionTemplar.borrowSoftLoad(
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
            ExpressionTemplar.evaluateAddressibleLookup(temputs, fate, memberName) match {
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
              ExpressionTemplar.softLoad(fate, lookup, borrow = true)
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
      case a : AddressExpression2 => softLoad(fate, a, borrow = a.coerceToBorrow)
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

  // See ClosureTests for requirements here
  def determineIfLocalIsAddressible(mutability: Mutability, variable1: LocalVariableA): Boolean = {
    if (mutability == Mutable) {
      variable1.childMutated != NotUsed || variable1.selfMoved == MaybeUsed || variable1.childMoved != NotUsed
    } else {
      variable1.childMutated != NotUsed
    }
  }


  // A user local variable is one that the user can address inside their code.
  // Users never see the names of non-user local variables, so they can't be
  // looked up.
  // Non-user local variables are reference local variables, so can't be
  // mutated from inside closures.
  def makeUserLocalVariable(
      temputs: TemputsBox,
      fate: FunctionEnvironmentBox,
      varId: IVarName2,
      variability: Variability,
      referenceType2: Coord):
  ILocalVariable2 = {
    if (fate.getVariable(varId).nonEmpty) {
      vfail("There's already a variable named " + varId)
    }

    val variable1 =
      fate.scoutedLocals.find(localA => NameTranslator.translateVarNameStep(localA.varName) == varId) match {
        case None => vfail("Missing local variable information from FunctionA for " + fate.function.name + " for variable " + varId)
        case Some(v) => v
      }

    val mutable = Templar.getMutability(temputs, referenceType2.referend)
    val addressible = determineIfLocalIsAddressible(mutable, variable1)

    val fullVarName = fate.fullName.addStep(varId)
    val localVar =
      if (addressible) {
        AddressibleLocalVariable2(fullVarName, variability, referenceType2)
      } else {
        ReferenceLocalVariable2(fullVarName, variability, referenceType2)
      }
    localVar
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
          CallTemplar.evaluatePrefixCall(
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
          CallTemplar.evaluateNamedPrefixCall(temputs, fate, GlobalFunctionFamilyNameA(name), templateArgTemplexesS, argsExprs2)
        (callExpr2, returnsFromArgs)
      }
      case FunctionCallAE(FunctionLoadAE(name), argsPackExpr1) => {
        val (argsExprs2, returnsFromArgs) =
            evaluateAndCoerceToReferenceExpressions(temputs, fate, argsPackExpr1)
        val callExpr2 =
          CallTemplar.evaluateNamedPrefixCall(temputs, fate, name, List(), argsExprs2)
        (callExpr2, returnsFromArgs)
      }
      case FunctionCallAE(callableExpr1, argsExprs1) => {
        val (undecayedCallableExpr2, returnsFromCallable) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, callableExpr1);
        val decayedCallableExpr2 =
          maybeSoftLoad(fate, undecayedCallableExpr2, true)
        val (argsExprs2, returnsFromArgs) =
          evaluateAndCoerceToReferenceExpressions(temputs, fate, argsExprs1)
        val functionPointerCall2 =
          CallTemplar.evaluatePrefixCall(temputs, fate, decayedCallableExpr2, List(), argsExprs2)
        (functionPointerCall2, returnsFromCallable ++ returnsFromArgs)
      }

      case ExpressionLendAE(innerExpr1) => {
        val (innerExpr2, returnsFromInner) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, innerExpr1);
        val resultExpr2 =
          innerExpr2.resultRegister.underlyingReference.ownership match {
            case Borrow | Share => (innerExpr2)
            case Own => makeTemporaryLocal(temputs, fate, innerExpr2)
          }
        (resultExpr2, returnsFromInner)
      }
      case LocalLoadAE(nameA, borrow) => {
        val name = NameTranslator.translateVarNameStep(nameA)
        val lookupExpr1 =
          evaluateLookup(temputs, fate, name, borrow) match {
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
          TemplataTemplar.isTypeConvertible(
            temputs, unconvertedSourceExpr2.resultRegister.reference, destinationExpr2.resultRegister.reference)
        vassert(isConvertible)
        val convertedSourceExpr2 =
          TypeTemplar.convert(fate.snapshot, temputs, unconvertedSourceExpr2, destinationExpr2.resultRegister.reference);

        val mutate2 = Mutate2(destinationExpr2, convertedSourceExpr2);
        (mutate2, returnsFromSource)
      }
      case ExprMutateAE(destinationExpr1, sourceExpr1) => {
        val (unconvertedSourceExpr2, returnsFromSource) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, sourceExpr1)
        val (destinationExpr2, returnsFromDestination) =
          evaluateExpectedAddressExpression(temputs, fate, destinationExpr1)
        val isConvertible =
          TemplataTemplar.isTypeConvertible(temputs, unconvertedSourceExpr2.resultRegister.reference, destinationExpr2.resultRegister.reference)
        if (!isConvertible) {
          vfail("In mutate, can't convert from: " + unconvertedSourceExpr2.resultRegister.reference + "\nto: " + destinationExpr2.resultRegister.reference)
        }
        val convertedSourceExpr2 =
          TypeTemplar.convert(fate.snapshot, temputs, unconvertedSourceExpr2, destinationExpr2.resultRegister.reference);

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
            case at @ ArraySequenceT2(_, _) => {
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
                case structDef @ StructDefinition2(_, _, _, _, _) => {
                  val (structMember, memberIndex) = structDef.getMemberAndIndex(memberName)
                  val memberFullName = structDef.fullName.addStep(structDef.members(memberIndex).name)
                  val memberType = structMember.tyype.expectReferenceMember().reference;
                  ReferenceMemberLookup2(containerExpr2, memberFullName, memberType)
                }
              }
            }
            case as @ ArraySequenceT2(_, _) => {
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
      case p @ PackAE(_) => {
        // Put them all into a pack because im not quite ready yet to have packs
        // that have templatas... we'd have to make some sort of... TemplataPack thing...
        // Note from later than that: yeah we definitely need a TemplataPack btw
        val (packExpr2, returnsFromPack) =
          PackTemplar.evaluate(temputs, fate, p)
        (packExpr2, returnsFromPack)
      }
      case SequenceEAE(elements1) => {
        val (exprs2, returnsFromElements) =
          evaluateAndCoerceToReferenceExpressions(temputs, fate, elements1);

        // would we need a sequence templata? probably right?
        val expr2 =
          SequenceTemplar.evaluate(fate, temputs, exprs2)
        (expr2, returnsFromElements)
      }
//      case ConstructAE(type1, argExprs1) => {
//        val (argExprs2, returnsFromArgs) =
//          evaluateList(temputs, fate, argExprs1);
//
//        val stuff = vfail() // this is where we do the thing
//
//        val kind = TemplataTemplar.evaluateTemplex(fate.snapshot, temputs, stuff)
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
        val (CoordTemplata(elementCoord)) = TemplataTemplar.evaluateTemplex(fate.snapshot, temputs, elementCoordTemplex)

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
          TemplataTemplar.isTypeTriviallyConvertible(temputs, memberType2, elementCoord)
        if (!isConvertible) {
          vfail(memberType2 + " cant convert to " + elementCoord)
        }

        if (arrayMutability == Immutable &&
            Templar.getMutability(temputs, elementCoord.referend) == Mutable) {
          vfail("Can't have an immutable array of mutable elements!")
        }
        val arrayType = ArrayTemplar.makeUnknownSizeArrayType(fate.snapshot, temputs, elementCoord, arrayMutability)

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
          PatternTemplar.nonCheckingInferAndTranslate(
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
          BlockTemplar.evaluateBlock(fate, temputs, condition1)
        val fateAfterBranch = fate.functionEnvironment

        val FunctionEnvironment(parentEnv, function, functionFullName, entries, maybeReturnType, scoutedLocals, counterBeforeBranch, variablesBeforeBranch, _) = fateAfterBranch

        val fateForThen = FunctionEnvironmentBox(fateAfterBranch)
        val (uncoercedThenExpr2, returnsFromThen) = BlockTemplar.evaluateBlock(fateForThen, temputs, thenBody1)
        val fateAfterThen = fateForThen.functionEnvironment

        val thenContinues = uncoercedThenExpr2.resultRegister.reference.referend != Never2()
        val FunctionEnvironment(_, _, _, _, _, _, counterAfterThen, variablesAfterThen, movedsAfterThen) = fateAfterThen

        // Give the else branch the same fate the then branch got, except let the counter
        // remain higher.
        val fateForElse = FunctionEnvironmentBox(fateAfterBranch)
        val _ = fateForElse.nextCounters(counterAfterThen - counterBeforeBranch)
        val (uncoercedElseExpr2, returnsFromElse) =
          BlockTemplar.evaluateBlock(fateForElse, temputs, elseBody1)
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
        val thenExpr2 = TypeTemplar.convert(fate.snapshot, temputs, uncoercedThenExpr2, commonType)
        val elseExpr2 = TypeTemplar.convert(fate.snapshot, temputs, uncoercedElseExpr2, commonType)

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
          BlockTemplar.evaluateBlock(fate, temputs, condition1)
        val (bodyExpr2, returnsFromBody) =
          BlockTemplar.evaluateBlock(fate, temputs, body1)

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
          BlockTemplar.evaluateBlock(fate, temputs, b);
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

        val destructure2 =
          innerExpr2.referend match {
            case structRef @ StructRef2(_) => {
              val structDef = temputs.lookupStruct(structRef)
              Destructure2(
                innerExpr2,
                structRef,
                structDef.members.map(_.tyype).map({ case ReferenceMemberType2(reference) =>
                  val rlv = makeTemporaryLocal(temputs, fate, reference)
                  rlv
                case _ => vfail()
              }))
            }
            case _ => vfail()
          }
        (destructure2, returnsFromArrayExpr)
      }
      case ReturnAE(innerExprA) => {
        val (uncastedInnerExpr2, returnsFromInnerExpr) =
          evaluateAndCoerceToReferenceExpression(temputs, fate, innerExprA);

        val innerExpr2 =
          fate.maybeReturnType match {
            case None => (uncastedInnerExpr2)
            case Some(returnType) => {
              TemplataTemplar.isTypeConvertible(temputs, uncastedInnerExpr2.resultRegister.reference, returnType) match {
                case (false) => vfail("Can't convert " + uncastedInnerExpr2.resultRegister.reference + " to return type " + returnType)
                case (true) => {
                  TypeTemplar.convert(fate.snapshot, temputs, uncastedInnerExpr2, returnType)
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
          unletAll(temputs, fate, reversedVariablesToDestruct)

        val getResultExpr =
          ExpressionTemplar.unletLocal(fate, resultVariable)

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

  private def decaySoloPack(fate: FunctionEnvironmentBox, refExpr: ReferenceExpression2):
  (ReferenceExpression2) = {
    refExpr.resultRegister.reference.referend match {
      case PackT2(List(onlyMember), understruct) => {
        val varNameCounter = fate.nextVarCounter()
        val varId = fate.fullName.addStep(TemplarTemporaryVarName2(varNameCounter))
        val localVar = ReferenceLocalVariable2(varId, Final, onlyMember)
        val destructure = Destructure2(refExpr, understruct, List(localVar))
        val unletExpr = unletLocal(fate, localVar)
        (Consecutor2(List(destructure, unletExpr)))
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
        (borrowSoftLoad(temputs, a))
      }
      case r: ReferenceExpression2 => {
        val unborrowedContainerExpr2 = decaySoloPack(fate, r)
        unborrowedContainerExpr2.resultRegister.reference.ownership match {
          case Own => makeTemporaryLocal(temputs, fate, unborrowedContainerExpr2)
          case Borrow | Share => (unborrowedContainerExpr2)
        }
      }
    }
  }

  def makeTemporaryLocal(
    temputs: TemputsBox,
    fate: FunctionEnvironmentBox,
    coord: Coord):
  ReferenceLocalVariable2 = {
    val varNameCounter = fate.nextVarCounter()
    val varId = fate.functionEnvironment.fullName.addStep(TemplarTemporaryVarName2(varNameCounter))
    val rlv = ReferenceLocalVariable2(varId, Final, coord)
    fate.addVariable(rlv)
    rlv
  }

  def makeTemporaryLocal(
      temputs: TemputsBox,
      fate: FunctionEnvironmentBox,
      r: ReferenceExpression2):
  (Defer2) = {
    val rlv = makeTemporaryLocal(temputs, fate, r.resultRegister.reference)
    val letExpr2 = LetAndLend2(rlv, r)

    val unlet = ExpressionTemplar.unletLocal(fate, rlv)
    val destructExpr2 =
      DestructorTemplar.drop(fate, temputs, unlet)
    vassert(destructExpr2.referend == Void2())

    // No Discard here because the destructor already returns void.

    (Defer2(letExpr2, destructExpr2))
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
      FunctionTemplar.evaluateClosureStruct(temputs, fate.snapshot, name, function1);
    val closureCoord =
      TemplataTemplar.pointifyReferend(temputs, closureStructRef2, Own)

    val constructExpr2 = makeClosureStructConstructExpression(temputs, fate, closureStructRef2)
    vassert(constructExpr2.resultRegister.reference == closureCoord)
    // The result of a constructor is always an own or a share.

    // The below code was here, but i see no reason we need to put it in a temporary and lend it out.
    // shouldnt this be done automatically if we try to call the function which accepts a borrow?
//    val closureVarId = FullName2(fate.lambdaNumber, "__closure_" + function1.origin.lambdaNumber)
//    val closureLocalVar = ReferenceLocalVariable2(closureVarId, Final, resultExpr2.resultRegister.reference)
//    val letExpr2 = LetAndLend2(closureLocalVar, resultExpr2)
//    val unlet2 = ExpressionTemplar.unletLocal(fate, closureLocalVar)
//    val dropExpr =
//      DestructorTemplar.drop(env, temputs, fate, unlet2)
//    val deferExpr2 = Defer2(letExpr2, dropExpr)
//    (temputs, fate, deferExpr2)

    constructExpr2
  }

  def getBorrowOwnership(temputs: TemputsBox, referend: Kind):
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
      case ArraySequenceT2(_, RawArrayT2(_, mutability)) => {
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

  def maybeSoftLoad(
      fate: FunctionEnvironmentBox,
      expr2: Expression2,
      borrow: Boolean):
  (ReferenceExpression2) = {
    expr2 match {
      case e : ReferenceExpression2 => (e)
      case e : AddressExpression2 => softLoad(fate, e, borrow)
    }
  }

  def softLoad(fate: FunctionEnvironmentBox, a: AddressExpression2, borrow: Boolean):
  (ReferenceExpression2) = {
    if (borrow) {
      val targetOwnership =
        a.resultRegister.reference.ownership match {
          case Own => Borrow
          case Borrow => Borrow // it's fine if they accidentally borrow a borrow ref
          case Share => Share
        }
      (SoftLoad2(a, targetOwnership))
    } else {
      a.resultRegister.reference.ownership match {
        case Own => {
          val localVar =
            a match {
              case LocalLookup2(lv, _) => lv
              case AddressMemberLookup2(_, _, _) => {
                vfail("Can't move out of a member!")
              }
            }
          fate.markVariableMoved(localVar.id)
          (Unlet2(localVar))
        }
        case Borrow | Share => {
          (SoftLoad2(a, a.resultRegister.reference.ownership))
        }
      }
    }
  }

  def borrowSoftLoad(temputs: TemputsBox, expr2: AddressExpression2):
  ReferenceExpression2 = {
    val ownership =
      getBorrowOwnership(temputs, expr2.resultRegister.reference.referend)
    SoftLoad2(expr2, ownership)
  }

  def unletLocal(fate: FunctionEnvironmentBox, localVar: ILocalVariable2):
  (Unlet2) = {
    fate.markVariableMoved(localVar.id)
    Unlet2(localVar)
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
}
