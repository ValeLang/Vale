package net.verdagon.vale.templar;

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser.CaptureP
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.rules.IRulexSR
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.{DestructorTemplar, DropHelper}
import net.verdagon.vale.templar.infer.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.templar.templata.TemplataTemplar
import net.verdagon.vale.{IProfiler, vfail}

import scala.collection.immutable.List

// either want:
// 1. (nonchecking) thing that just trusts its good and extracts it into locals. (for lets)
// 2. (checking) thing that checks it matches, returns None if not, otherwise returns
//    a struct containing it all (for signatures)

class PatternTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
    inferTemplar: InferTemplar,
    convertHelper: ConvertHelper,
  dropHelper: DropHelper,
    localHelper: LocalHelper) {
  // Note: This will unlet/drop the input expressions. Be warned.
  // patternInputExprs2 is a list of reference expression because they're coming in from
  // god knows where... arguments, the right side of a let, a variable, don't know!
  // If a pattern needs to send it to multiple places, the pattern is free to put it into
  // a local variable.
  // PatternTemplar must be sure to NOT USE IT TWICE! That would mean copying the entire
  // expression subtree that it contains!
  // Has "InferAnd" because we evaluate the template rules too.
  // Returns:
  // - Temputs
  // - Function state
  // - Exports, to toss into the environment
  // - Local variables
  def nonCheckingTranslateList(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
      patterns1: List[AtomAP],
      patternInputExprs2: List[ReferenceExpression2]):
  (List[ReferenceExpression2]) = {
    profiler.newProfile("nonCheckingTranslateList", fate.fullName.toString, () => {
      patterns1.zip(patternInputExprs2) match {
        case Nil => (Nil)
        case (pattern1, patternInputExpr2) :: _ => {
          val headLets =
            innerNonCheckingTranslate(
              temputs, fate, pattern1, patternInputExpr2);
          val tailLets =
            nonCheckingTranslateList(
              temputs, fate, patterns1.tail, patternInputExprs2.tail)
          (headLets ++ tailLets)
        }
        case _ => vfail("wat")
      }
    })
  }

  // Note: This will unlet/drop the input expression. Be warned.
  def nonCheckingInferAndTranslate(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      rules: List[IRulexAR],
      typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
      pattern: AtomAP,
      inputExpr: ReferenceExpression2):
  (List[ReferenceExpression2]) = {
    profiler.newProfile("nonCheckingInferAndTranslate", fate.fullName.toString, () => {

      val templatasByRune =
        inferTemplar.inferFromArgCoords(fate.snapshot, temputs, List(), rules, typeByRune, localRunes, List(pattern), None, pattern.range, List(), List(ParamFilter(inputExpr.resultRegister.reference, None))) match {
          case (isf@InferSolveFailure(_, _, _, _, range, _, _)) => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldn't figure out runes for pattern!\n" + isf))
          case (InferSolveSuccess(tbr)) => (tbr.templatasByRune.mapValues(v => List(TemplataEnvEntry(v))))
        }

      fate.addEntries(opts.useOptimization, templatasByRune.map({ case (key, value) => (key, value) }).toMap)

      innerNonCheckingTranslate(
        temputs, fate, pattern, inputExpr)
    })
  }

  // Note: This will unlet/drop the input expression. Be warned.
  def nonCheckingTranslate(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      pattern: AtomAP,
      inputExpr: ReferenceExpression2):
  (List[ReferenceExpression2]) = {
    innerNonCheckingTranslate(
      temputs, fate, pattern, inputExpr)
  }

  // the #1 case above
  // returns:
  // - the temputs
  // - the new seq num
  // - a bunch of lets.
  // - exports, to toss into the env
  // - function state
  private def innerNonCheckingTranslate(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      pattern: AtomAP,
      unconvertedInputExpr: ReferenceExpression2):
  (List[ReferenceExpression2]) = {

    val AtomAP(range, lv @ LocalVariableA(varName, variability, _, _, _, _, _, _), maybeVirtuality, coordRuneA, maybeDestructure) = pattern

    if (maybeVirtuality.nonEmpty) {
      // This is actually to be expected for when we translate the patterns from the
      // function's parameters. Ignore them.
    }

    val expectedTemplata = fate.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(coordRuneA), Set(TemplataLookupContext))
    val expectedCoord =
      expectedTemplata match {
        case Some(CoordTemplata(coord)) => coord
        case Some(_) => throw CompileErrorExceptionT(RangedInternalErrorT(range, "not a coord!"))
        case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "not found!"))
      }

    // Now we convert m to a Marine. This also checks that it *can* be
    // converted to a Marine.
    val inputExpr =
      convertHelper.convert(fate.snapshot, temputs, range, unconvertedInputExpr, expectedCoord);

    val export =
      localHelper.makeUserLocalVariable(
        temputs, fate, range, lv, expectedCoord)
    val let = LetNormal2(export, inputExpr);

    fate.addVariable(export)

    val lets0 = List(let)

    maybeDestructure match {
      case None => {
        (lets0)
      }
      case Some(listOfMaybeDestructureMemberPatterns) => {
        // This will mark the variable as moved
        val localLookupExpr =
          localHelper.softLoad(
            fate, range, LocalLookup2(range, export, inputExpr.resultRegister.reference, Final), Own)

        expectedCoord.referend match {
          case StructRef2(_) => {
            // Example:
            //   struct Marine { bork: Bork; }
            //   Marine(b) = m;
            // In this case, expectedStructType1 = TypeName1("Marine") and
            // destructureMemberPatterns = List(CaptureSP("b", FinalP, None)).
            // Since we're receiving an owning reference, and we're *not* capturing
            // it in a variable, it will be destroyed and we will harvest its parts.

            val innerLets =
              nonCheckingTranslateStructInner(
                temputs, fate, range, listOfMaybeDestructureMemberPatterns, expectedCoord, localLookupExpr)
            (lets0 ++ innerLets)
          }
          case PackT2(_, underlyingStruct @ StructRef2(_)) => {
            val structType2 = Coord(expectedCoord.ownership, underlyingStruct)
            val reinterpretExpr2 = TemplarReinterpret2(localLookupExpr, structType2)
            val innerLets =
              nonCheckingTranslateStructInner(
                temputs, fate, range, listOfMaybeDestructureMemberPatterns, structType2, reinterpretExpr2)
            (lets0 ++ innerLets)
          }
          case TupleT2(_, underlyingStruct @ StructRef2(_)) => {
            val structType2 = Coord(expectedCoord.ownership, underlyingStruct)
            val reinterpretExpr2 = TemplarReinterpret2(localLookupExpr, structType2)
            val innerLets =
              nonCheckingTranslateStructInner(
                temputs, fate, range, listOfMaybeDestructureMemberPatterns, structType2, reinterpretExpr2)
            (lets0 ++ innerLets)
          }
          case KnownSizeArrayT2(size, RawArrayT2(memberType, mutability)) => {
            if (size != listOfMaybeDestructureMemberPatterns.size) {
              throw CompileErrorExceptionT(RangedInternalErrorT(range, "Wrong num exprs!"))
            }
            val innerLets =
              nonCheckingTranslateArraySeq(
                temputs, fate, range, listOfMaybeDestructureMemberPatterns, localLookupExpr)
            (lets0 ++ innerLets)
          }
          case _ => vfail("impl!")
        }
      }
    }
//
//    pattern match {
//      case TypeOfSP(TemplateCallT1(templateName, templateArgTypes1)) => {
//        val expectedCitizenRef2 =
//          ConvertHelper.callTemplate(env, temputs, templateName, templateArgTypes1)
//
//        // Our resulting variable will have this ownership
//        val expectedCitizenDef2 =
//          expectedCitizenRef2 match {
//            case ReferendTemplata(sr @ StructRef2(_)) => temputs.lookupCitizen(sr)
//            case ReferendTemplata(ir @ InterfaceRef2(_)) => temputs.lookupCitizen(ir)
//          }
//
//        val expectedOwnership =
//          if (expectedCitizenDef2.mutability == ImmutableP) {
//            Share
//          } else {
////            if (expectBorrow) Borrow else Own
//            Own
//          }
//
//        val expectedPointerType = Coord(expectedOwnership, expectedCitizenDef2.getRef)
//
//        // Don't need output, since we're just doing a compile time check here
//        ConvertHelper.convert(env, temputs, inputExpr, expectedPointerType)
//
//        (temputs, fate, List(), List())
//      }
//      case TypeOfSP(type1) => {
//        val unborrowedTargetReference =
//          ConvertHelper.evaluateAndReferencifyType(
//            env, temputs, type1, Own)
//        // If we expect a borrow, then here we make a targetReference that reflects that
//        val targetReference =
//          if (unborrowedTargetReference.ownership == Share) {
//            unborrowedTargetReference
//          } else {
////            if (expectBorrow) {
////              Coord(Borrow, unborrowedTargetReference.referend)
////            } else {
//              unborrowedTargetReference
////            }
//          }
//
//        // Don't need output, since we're just doing a compile time check here
//        ConvertHelper.convert(env, temputs, inputExpr, targetReference)
//
//        (temputs, fate, List(), List())
//      }
//      case CaptureSP(name, variability, _, None) => already moved
//      case CaptureSP(name, variability, _, Some(TypeOfSP(expectedType1))) => {
//        // Example:
//        //   struct Marine { bork: Bork; }
//        //   Marine(b : Bork) = m;
//        // In this case, name = 'b' and inner1 = 'Bork'
//
//        // This is local variable b
//        val variableId = FullName2(env.currentFunction1.get.lambdaNumber, name)
//        // This is where we figure out that b should be an owning Bork
//        val expectedPointerType =
//          ConvertHelper.evaluateAndReferencifyType(
//            env, temputs, expectedType1, Own)
//        // Now we convert Marine's first member to a Bork. This also checks that
//        // it *can* be converted to a Bork.
//        val convertedInputLookupExpr =
//          ConvertHelper.convert(env, temputs, inputExpr, expectedPointerType);
//        // Now we make the local variable b
//        val newExport =
//          ExpressionTemplar.makeUserLocalVariable(
//            env, temputs, variableId, variability, convertedInputLookupExpr.resultRegister.reference)
//        val newLet = LetNormal2(newExport, convertedInputLookupExpr)
//
//        (temputs, fate, List(newLet), List(newExport))
//      }
//      case CaptureSP(name, variability, _, Some(DestructureSP(expectedStructType1, destructureMemberPatterns))) => {
//        // Example:
//        //   struct Marine { bork: Bork; }
//        //   m : Marine(b) = inMarine;
//        // In this case, name = 'm', expectedStructType1 = TypeName1("Marine"),
//        // and destructureMemberPatterns = List(CaptureSP("b", FinalP, None)).
//        // The local m is actually an owning reference, and things inside the
//        // Marine (like b) have to be borrow references (the other way wouldnt
//        // make sense, what would b point to? A dead object?).
//
//        // This is local variable m
//        val variableId = FullName2(env.currentFunction1.get.lambdaNumber, name)
//        // This is where we figure out that m should be an owning Marine
//        val (expectedPointerType @ Coord(_, StructRef2(_))) =
//          ConvertHelper.evaluateAndReferencifyType(
//            env, temputs, expectedStructType1, Own)
//        // Now we convert inMarine to a Marine. This also checks that
//        // it *can* be converted to a Marine.
//        val convertedInputLookupExpr =
//          ConvertHelper.convert(env, temputs, inputExpr, expectedPointerType);
//        // Now we make the local variable m
//        val newExport =
//          ExpressionTemplar.makeUserLocalVariable(
//            env, temputs, variableId, variability, convertedInputLookupExpr.resultRegister.reference)
//        val newLet = LetNormal2(newExport, convertedInputLookupExpr)
//
//        // The input to the inner destructure expressions will be a borrow of m.
//        val innerInputLookup = LocalLookup2(newExport, expectedPointerType)
//        val innerInputBorrow =
//          ExpressionTemplar.borrowSoftLoad(temputs, innerInputLookup)
//
//        val (innerLets, innerExports) =
//          nonCheckingTranslateStructInner(env, temputs, fate, patternId, destructureMemberPatterns, expectedPointerType, innerInputBorrow)
//
//        (temputs, fate, newLet :: innerLets, newExport :: innerExports)
//      }
//      case d @ DestructureSP(expectedStructType1, destructureMemberPatterns) => already moved
//      case p @ PackSP(_) => already moved
//      case _ => {
//        vfail("not yet " + pattern.toString)
//      }
//    }
  }

  private def nonCheckingTranslateArraySeq(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    innerPatternMaybes: List[AtomAP],
    inputArraySeqExpr: ReferenceExpression2):
  (List[ReferenceExpression2]) = {
    // we gotta:
    // destructure the incoming pack expression into a bunch of locals.
    // for each member, unlet its local and pass it to the subpattern.

    val arrSeqRef2 = inputArraySeqExpr.resultRegister.reference
    val Coord(arrSeqRefOwnership, arraySeqT @ KnownSizeArrayT2(numElements, RawArrayT2(elementType, arrayMutability))) = arrSeqRef2

    val memberTypes = (0 until numElements).toList.map(_ => elementType)

    val counter = fate.nextVarCounter()

    arrSeqRefOwnership match {
      case Own => {
        val memberLocalVariables = makeLocals(fate, counter, memberTypes)
        val destroy =
          DestroyArraySequenceIntoLocals2(
            inputArraySeqExpr, arraySeqT, memberLocalVariables)
        val lets = makeLetsForOwn(temputs, fate, innerPatternMaybes, memberLocalVariables)
        (destroy :: lets)
      }
      case Share => {
        // This is different from the Own case because we're not destructuring the incoming thing, we're just
        // loading from it.

        val arrSeqLocalVariableId = fate.fullName.addStep(TemplarPatternDestructureeName2(counter))
        val arrSeqLocalVariable = ReferenceLocalVariable2(arrSeqLocalVariableId, Final, arrSeqRef2)
        val arrSeqLet = LetNormal2(arrSeqLocalVariable, inputArraySeqExpr);
        fate.addVariable(arrSeqLocalVariable)

        val innerLets =
          innerPatternMaybes.zip(memberTypes).zipWithIndex
            .flatMap({
              case (((innerPattern, memberType), index)) => {
                val loadExpr =
                  SoftLoad2(
                    ArraySequenceLookup2(range, inputArraySeqExpr, arraySeqT, IntLiteral2(index), Final),
                    Share)
                innerNonCheckingTranslate(temputs, fate, innerPattern, loadExpr)
              }
            })

        val packUnlet = localHelper.unletLocal(fate, arrSeqLocalVariable)
        val dropExpr =
          dropHelper.drop(fate, temputs, packUnlet)

        ((arrSeqLet :: innerLets) :+ dropExpr)
      }
      case Borrow => {
        // here, instead of doing a destructure, we'd just put this in a variable
        // and do a bunch of lookups on it.
        vfail("implement!")
      }
    }
  }

  private def nonCheckingTranslateStructInner(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    innerPatternMaybes: List[AtomAP],
    structType2: Coord,
    inputStructExpr: ReferenceExpression2):
  (List[ReferenceExpression2]) = {
    val Coord(structOwnership, structRef2 @ StructRef2(_)) = structType2
    val structDef2 = temputs.getStructDefForRef(structRef2)
    // We don't pattern match against closure structs.

    val counter = fate.nextVarCounter()

    structOwnership match {
      case Own => {
        val memberLocalVariables =
          makeLocals(fate, counter, structDef2.members.map(_.tyype.expectReferenceMember().reference))

        val destroy2 = Destroy2(inputStructExpr, structRef2, memberLocalVariables)

        val lets = makeLetsForOwn(temputs, fate, innerPatternMaybes, memberLocalVariables)
        (destroy2 :: lets)
      }
      case Share | Borrow => {
        // This is different from the Own case because we're not destructuring the incoming thing, we're just
        // loading from it.

        val packLocalVariableId = fate.fullName.addStep(TemplarPatternDestructureeName2(counter))
        val packLocalVariable = ReferenceLocalVariable2(packLocalVariableId, Final, structType2)
        val packLet = LetNormal2(packLocalVariable, inputStructExpr);
        fate.addVariable(packLocalVariable)

        val innerLets =
          innerPatternMaybes.zip(structDef2.members).zipWithIndex
            .flatMap({
              case (((innerPattern, member), index)) => {
                val memberCoord = member.tyype.expectReferenceMember().reference
                val resultOwnership = if (memberCoord.ownership == Share) Share else Borrow

                val index = structDef2.members.indexWhere(_.name == member.name)
                val ownershipInClosureStruct = structDef2.members(index).tyype.reference.ownership
                val coerceToOwnership =
                  ownershipInClosureStruct match {
                    case Own => Borrow
                    case Borrow => Borrow
                    case Weak => Weak
                    case Share => Share
                  }

                val loadExpr =
                  SoftLoad2(
                    ReferenceMemberLookup2(
                      range,
                      SoftLoad2(LocalLookup2(range, packLocalVariable, structType2, Final), structOwnership),
                      structDef2.fullName.addStep(structDef2.members(index).name),
                      memberCoord,
                      member.variability,
                      coerceToOwnership),
                    resultOwnership)
                innerNonCheckingTranslate(temputs, fate, innerPattern, loadExpr)
              }
            })

        val packUnlet = localHelper.unletLocal(fate, packLocalVariable)
        val dropExpr =
          dropHelper.drop(fate, temputs, packUnlet)

        ((packLet :: innerLets) :+ dropExpr)
      }
    }
  }
//
//  // Assumes the templated stuff has already been put into this environment
//  def getParameterType2(env: IEnvironmentBox, temputs: Temputs, param1: AtomAP):
//  (Temputs, Coord) = {
//    val type1 = getPatternType1(param1)
//    val type2 = ConvertHelper.evaluateType(env, temputs, type1)
//    (temputs, TemplataTemplar.coerceTemplataToReference(temputs, type2, Own))
//  }
//
//  // Assumes the templated stuff has already been put into this environment
//  def getPatternType2(env: IEnvironmentBox, temputs: Temputs, pattern1: AtomAP):
//  (Temputs, Coord) = {
//    val type1 = getPatternType1(pattern1)
//    val type2 =
//      ConvertHelper.evaluateType(env, temputs, type1)
//    (temputs, TemplataTemplar.coerceTemplataToReference(temputs, type2, Own))
//  }
//
//  def getParameterType1(param1: AtomAP): ITemplexS = {
//    getPatternType1(param1)
//  }
//
//  def getParameterType1s(params1: List[AtomAP]): List[ITemplexS] = {
//    getPatternType1s(params1)
//  }
//
//  def getPatternType1s(patterns1: List[AtomAP]):
//  List[ITemplexS] = {
//    patterns1.map(pattern1 => getPatternType1(pattern1))
//  }

  // Once we know that a function isnt templated, we use this to figure out
  // the types of its AtomAP
//  def getPatternType1(pattern: AtomAP):
//      ITemplexS = {
//    pattern.coordPattern.get.iname match {
//      case TemplexSP(name) =>
//      case TypeOfSP(type1) => type1
//      case CaptureSP(name, mutable, _, Some(inner1)) => {
//        getPatternType1(inner1)
//      }
//      case PackSP(elements0) => {
//        PackT1(getPatternType1s(elements0))
//      }
//      case CaptureSP(name, mutable, _, None) => {
//        // we should already know that this isn't templated
//        vfail("wat")
//      }
//      case _ => vfail("wat " + pattern)
//    }
//  }

  private def makeLocals(
    fate: FunctionEnvironmentBox,
    counter: Int,
    memberTypes: List[Coord]
  ): List[ReferenceLocalVariable2] = {
    memberTypes.zipWithIndex.map({
      case ((memberType, index)) => {
        val variableId = fate.fullName.addStep(TemplarPatternMemberName2(counter, index))
        val localVariable = ReferenceLocalVariable2(variableId, Final, memberType)
        fate.addVariable(localVariable)
        localVariable
      }
    })
  }

  //  private def getPatternsCaptureDeclarations(patterns: List[AtomAP]):
//      VariableDeclarations = {
//    patterns.foldLeft(VariableDeclarations(Set()))({ case (previousDeclarations, pattern) =>
//      previousDeclarations ++ getPatternCaptureDeclarations(pattern)
//    })
//  }
  private def makeLetsForOwn(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    innerPatternMaybes: List[AtomAP],
    memberLocalVariables: List[ReferenceLocalVariable2]
  ): List[ReferenceExpression2] = {
    innerPatternMaybes.zip(memberLocalVariables).flatMap({
      case ((innerPattern, localVariable)) => {
        val unletExpr = localHelper.unletLocal(fate, localVariable)
        innerNonCheckingTranslate(temputs, fate, innerPattern, unletExpr)
      }
    })
  }
}
