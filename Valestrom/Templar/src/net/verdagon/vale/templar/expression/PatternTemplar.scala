package net.verdagon.vale.templar.expression

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{LendConstraintP, UseP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.infer.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar._
import net.verdagon.vale.{IProfiler, vassert, vassertSome, vfail}

import scala.collection.immutable.List

class PatternTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
    inferTemplar: InferTemplar,
    arrayTemplar: ArrayTemplar,
    convertHelper: ConvertHelper,
    destructorTemplar: DestructorTemplar,
    localHelper: LocalHelper) {
  // Note: This will unlet/drop the input expressions. Be warned.
  // patternInputsTE is a list of reference expression because they're coming in from
  // god knows where... arguments, the right side of a let, a variable, don't know!
  // If a pattern needs to send it to multiple places, the pattern is free to put it into
  // a local variable.
  // PatternTemplar must be sure to NOT USE IT TWICE! That would mean copying the entire
  // expression subtree that it contains!
  def translatePatternList(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    patternsA: List[AtomAP],
    patternInputsTE: List[ReferenceExpressionTE],
    // This would be a continuation-ish lambda that evaluates:
    // - The body of an if-let statement
    // - The body of a match's case statement
    // - The rest of the pattern that contains this pattern
    // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
    afterPatternsSuccessContinuation: (Temputs, FunctionEnvironmentBox, List[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    profiler.newProfile("translatePatternList", fate.fullName.toString, () => {
      iterateTranslateListAndMaybeContinue(
        temputs, fate, List(), patternsA, patternInputsTE, afterPatternsSuccessContinuation)
    })
  }

  def iterateTranslateListAndMaybeContinue(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    liveCaptureLocals: List[ILocalVariableT],
    patternsA: List[AtomAP],
    patternInputsTE: List[ReferenceExpressionTE],
    // This would be a continuation-ish lambda that evaluates:
    // - The body of an if-let statement
    // - The body of a match's case statement
    // - The rest of the pattern that contains this pattern
    // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
    afterPatternsSuccessContinuation: (Temputs, FunctionEnvironmentBox, List[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    vassert(liveCaptureLocals == liveCaptureLocals.distinct)

    (patternsA, patternInputsTE) match {
      case (Nil, Nil) => afterPatternsSuccessContinuation(temputs, fate, liveCaptureLocals)
      case (headPatternA :: tailPatternsA, headPatternInputTE :: tailPatternInputsTE) => {
        innerTranslateSubPatternAndMaybeContinue(
          temputs, fate, headPatternA, liveCaptureLocals, headPatternInputTE,
          (temputs, fate, liveCaptureLocals) => {
            vassert(liveCaptureLocals == liveCaptureLocals.distinct)

            iterateTranslateListAndMaybeContinue(
              temputs, fate, liveCaptureLocals, tailPatternsA, tailPatternInputsTE, afterPatternsSuccessContinuation)
          })
      }
      case _ => vfail("wat")
    }
  }

  // Note: This will unlet/drop the input expression. Be warned.
  def inferAndTranslatePattern(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      rules: List[IRulexAR],
      typeByRune: Map[IRuneA, ITemplataType],
      localRunes: Set[IRuneA],
      pattern: AtomAP,
      inputExpr: ReferenceExpressionTE,
      // This would be a continuation-ish lambda that evaluates:
      // - The body of an if-let statement
      // - The body of a match's case statement
      // - The rest of the pattern that contains this pattern
      // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
      afterPatternsSuccessContinuation: (Temputs, FunctionEnvironmentBox, List[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    profiler.newProfile("inferAndTranslatePattern", fate.fullName.toString, () => {
      val templatasByRune =
        inferTemplar.inferFromArgCoords(fate.snapshot, temputs, List.empty, rules, typeByRune, localRunes, List(pattern), None, pattern.range, List.empty, List(ParamFilter(inputExpr.resultRegister.reference, None))) match {
          case isf @ InferSolveFailure(_, _, _, _, range, _, _) => {
            throw CompileErrorExceptionT(RangedInternalErrorT(range, "Couldn't figure out runes for pattern!\n" + isf))
          }
          case InferSolveSuccess(tbr) => (tbr.templatasByRune.mapValues(v => List(TemplataEnvEntry(v))))
        }

      fate.addEntries(opts.useOptimization, templatasByRune.map({ case (key, value) => (key, value) }).toMap)

      innerTranslateSubPatternAndMaybeContinue(temputs, fate, pattern, List(), inputExpr, afterPatternsSuccessContinuation)
    })
  }

  // returns:
  // - All captures that have happened so far. We'll drop these if a subsequent pattern match fails.
  // -
  private def innerTranslateSubPatternAndMaybeContinue(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      pattern: AtomAP,
      previousLiveCaptureLocals: List[ILocalVariableT],
      unconvertedInputExpr: ReferenceExpressionTE,
      // This would be a continuation-ish lambda that evaluates:
      // - The body of an if-let statement
      // - The body of a match's case statement
      // - The rest of the pattern that contains this pattern
      // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
      afterSubPatternSuccessContinuation: (Temputs, FunctionEnvironmentBox, List[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    vassert(previousLiveCaptureLocals == previousLiveCaptureLocals.distinct)

    val AtomAP(range, maybeCaptureLocalVarA, maybeVirtuality, coordRuneA, maybeDestructure) = pattern

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

    // We make it here instead of down in the maybeDestructure clauses because whether we destructure it or not
    // is unrelated to whether we destructure it.


    var currentInstructions = List[ReferenceExpressionTE]()

    val (maybeCaptureLocalVarT, exprToDestructureOrDropOrPassTE) =
      maybeCaptureLocalVarA match {
        case None => (None, inputExpr)
        case Some(captureLocalVar) => {
          val localT = localHelper.makeUserLocalVariable(temputs, fate, range, captureLocalVar, expectedCoord)
          currentInstructions = currentInstructions :+ LetNormalTE(localT, inputExpr)
          val capturedLocalAliasTE =
            localHelper.softLoad(fate, range, LocalLookupTE(range, localT, localT.reference, FinalT), LendConstraintP(None))
          (Some(localT), capturedLocalAliasTE)
        }
      }
    // If we captured it as a local, then the exprToDestructureOrDropOrPassTE should be a non-owning alias of it
    if (maybeCaptureLocalVarT.nonEmpty) {
      vassert(exprToDestructureOrDropOrPassTE.resultRegister.reference.ownership != OwnT)
    }

    val liveCaptureLocals = previousLiveCaptureLocals ++ maybeCaptureLocalVarT.toList
    vassert(liveCaptureLocals == liveCaptureLocals.distinct)

    Templar.consecutive(
      currentInstructions :+
        (maybeDestructure match {
        case None => {
          // Do nothing
          afterSubPatternSuccessContinuation(temputs, fate, liveCaptureLocals)
        }
        case Some(listOfMaybeDestructureMemberPatterns) => {
          exprToDestructureOrDropOrPassTE.resultRegister.reference.ownership match {
            case OwnT => {
              // We aren't capturing the var, so the destructuring should consume the incoming value.
              destructureOwning(
                temputs, fate, range, liveCaptureLocals, expectedCoord, exprToDestructureOrDropOrPassTE, listOfMaybeDestructureMemberPatterns, afterSubPatternSuccessContinuation)
            }
            case ConstraintT | ShareT => {
              destructureNonOwningAndMaybeContinue(
                temputs, fate, range, liveCaptureLocals, expectedCoord, exprToDestructureOrDropOrPassTE, listOfMaybeDestructureMemberPatterns, afterSubPatternSuccessContinuation)
            }
          }
        }
      }))
  }

  private def destructureOwning(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      range: RangeS,
      initialLiveCaptureLocals: List[ILocalVariableT],
      expectedCoord: CoordT,
      inputExpr: ReferenceExpressionTE,
      listOfMaybeDestructureMemberPatterns: List[AtomAP],
      afterDestructureSuccessContinuation: (Temputs, FunctionEnvironmentBox, List[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(initialLiveCaptureLocals == initialLiveCaptureLocals.distinct)

    val CoordT(OwnT, expectedContainerPermission, expectedContainerKind) = expectedCoord
    expectedContainerKind match {
      case StructTT(_) => {
        // Example:
        //   struct Marine { bork: Bork; }
        //   Marine(b) = m;
        // In this case, expectedStructType1 = TypeName1("Marine") and
        // destructureMemberPatterns = List(CaptureSP("b", FinalP, None)).
        // Since we're receiving an owning reference, and we're *not* capturing
        // it in a variable, it will be destroyed and we will harvest its parts.
        translateDestroyStructInnerAndMaybeContinue(
          temputs, fate, range, initialLiveCaptureLocals, listOfMaybeDestructureMemberPatterns, expectedCoord, inputExpr, afterDestructureSuccessContinuation)
      }
      case PackTT(_, underlyingStruct@StructTT(_)) => {
        val structType2 = CoordT(OwnT, expectedContainerPermission, underlyingStruct)
        val reinterpretExpr2 = TemplarReinterpretTE(inputExpr, structType2)
        translateDestroyStructInnerAndMaybeContinue(
          temputs, fate, range, initialLiveCaptureLocals, listOfMaybeDestructureMemberPatterns, structType2, reinterpretExpr2, afterDestructureSuccessContinuation)
      }
      case TupleTT(_, underlyingStruct@StructTT(_)) => {
        val structType2 = CoordT(OwnT, expectedContainerPermission, underlyingStruct)
        val reinterpretExpr2 = TemplarReinterpretTE(inputExpr, structType2)
        translateDestroyStructInnerAndMaybeContinue(
          temputs, fate, range, initialLiveCaptureLocals, listOfMaybeDestructureMemberPatterns, structType2, reinterpretExpr2, afterDestructureSuccessContinuation)
      }
      case staticSizedArrayT@StaticSizedArrayTT(size, RawArrayTT(elementType, _, _)) => {
        if (size != listOfMaybeDestructureMemberPatterns.size) {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Wrong num exprs!"))
        }

        val elementLocals = (0 until size).map(_ => localHelper.makeTemporaryLocal(fate, elementType)).toList
        val destroyTE = DestroyStaticSizedArrayIntoLocalsTE(inputExpr, staticSizedArrayT, elementLocals)
        val liveCaptureLocals = initialLiveCaptureLocals ++ elementLocals
        vassert(liveCaptureLocals == liveCaptureLocals.distinct)

        val lets = makeLetsForOwnAndMaybeContinue(temputs, fate, liveCaptureLocals, elementLocals, listOfMaybeDestructureMemberPatterns, afterDestructureSuccessContinuation)
        Templar.consecutive(List(destroyTE, lets))
      }
      case _ => vfail("impl!")
    }
  }

  private def destructureNonOwningAndMaybeContinue(
      temputs: Temputs,
      fate: FunctionEnvironmentBox,
      range: RangeS,
      liveCaptureLocals: List[ILocalVariableT],
      expectedCoord: CoordT,
      containerTE: ReferenceExpressionTE,
      listOfMaybeDestructureMemberPatterns: List[AtomAP],
      afterDestructureSuccessContinuation: (Temputs, FunctionEnvironmentBox, List[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(liveCaptureLocals == liveCaptureLocals.distinct)

    val localT = localHelper.makeTemporaryLocal(fate, expectedCoord)
    val letTE = LetNormalTE(localT, containerTE)
    val containerAliasingExprTE =
      localHelper.softLoad(fate, range, LocalLookupTE(range, localT, localT.reference, FinalT), LendConstraintP(None))

    Templar.consecutive(
      List(
        letTE,
        iterateDestructureNonOwningAndMaybeContinue(
          temputs, fate, range, liveCaptureLocals, expectedCoord, containerAliasingExprTE, 0, listOfMaybeDestructureMemberPatterns, afterDestructureSuccessContinuation)))
  }

  private def iterateDestructureNonOwningAndMaybeContinue(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    liveCaptureLocals: List[ILocalVariableT],
    expectedContainerCoord: CoordT,
    containerAliasingExprTE: ReferenceExpressionTE,
    memberIndex: Int,
    listOfMaybeDestructureMemberPatterns: List[AtomAP],
    afterDestructureSuccessContinuation: (Temputs, FunctionEnvironmentBox, List[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(liveCaptureLocals == liveCaptureLocals.distinct)

    val CoordT(expectedContainerOwnership, expectedContainerPermission, expectedContainerKind) = expectedContainerCoord

    listOfMaybeDestructureMemberPatterns match {
      case Nil => afterDestructureSuccessContinuation(temputs, fate, liveCaptureLocals)
      case headMaybeDestructureMemberPattern :: tailDestructureMemberPatternMaybes => {
        val memberAddrExprTE =
          expectedContainerKind match {
            case structTT@StructTT(_) => {
              // Example:
              //   struct Marine { bork: Bork; }
              //   Marine(b) = m;
              // In this case, expectedStructType1 = TypeName1("Marine") and
              // destructureMemberPatterns = List(CaptureSP("b", FinalP, None)).
              // Since we're receiving an owning reference, and we're *not* capturing
              // it in a variable, it will be destroyed and we will harvest its parts.

              loadFromStruct(temputs, range, expectedContainerPermission, containerAliasingExprTE, structTT, memberIndex)
            }
            case PackTT(_, underlyingStruct@StructTT(_)) => {
              val reinterpretedStructTT = CoordT(expectedContainerOwnership, expectedContainerPermission, underlyingStruct)
              val reinterpretedContainerAliasingExprTE = TemplarReinterpretTE(containerAliasingExprTE, reinterpretedStructTT)
              loadFromStruct(
                temputs, range, expectedContainerPermission, reinterpretedContainerAliasingExprTE, underlyingStruct, memberIndex)
            }
            case TupleTT(_, underlyingStruct@StructTT(_)) => {
              val reinterpretedStructTT = CoordT(expectedContainerOwnership, expectedContainerPermission, underlyingStruct)
              val reinterpretedContainerAliasingExprTE = TemplarReinterpretTE(containerAliasingExprTE, reinterpretedStructTT)
              loadFromStruct(
                temputs, range, expectedContainerPermission, reinterpretedContainerAliasingExprTE, underlyingStruct, memberIndex)
            }
            case staticSizedArrayT@StaticSizedArrayTT(size, RawArrayTT(elementType, _, _)) => {
              loadFromStaticSizedArray(range, staticSizedArrayT, expectedContainerCoord, expectedContainerOwnership, expectedContainerPermission, containerAliasingExprTE, memberIndex)
            }
            case _ => vfail("impl!")
          }

        val memberOwnershipInStruct = memberAddrExprTE.resultRegister.reference.ownership
        val coerceToOwnership = loadResultOwnership(memberOwnershipInStruct)
        val loadExpr = SoftLoadTE(memberAddrExprTE, coerceToOwnership, expectedContainerPermission)
        innerTranslateSubPatternAndMaybeContinue(
          temputs, fate, headMaybeDestructureMemberPattern, liveCaptureLocals, loadExpr,
          (temputs, fate, liveCaptureLocals) => {
            vassert(liveCaptureLocals == liveCaptureLocals.distinct)

            val nextMemberIndex = memberIndex + 1
            iterateDestructureNonOwningAndMaybeContinue(
              temputs,
              fate,
              range,
              liveCaptureLocals,
              expectedContainerCoord,
              containerAliasingExprTE,
              nextMemberIndex,
              tailDestructureMemberPatternMaybes,
              afterDestructureSuccessContinuation)
          })
      }
    }
  }

  private def translateDestroyStructInnerAndMaybeContinue(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    initialLiveCaptureLocals: List[ILocalVariableT],
    innerPatternMaybes: List[AtomAP],
    structType2: CoordT,
    inputStructExpr: ReferenceExpressionTE,
    afterDestroySuccessContinuation: (Temputs, FunctionEnvironmentBox, List[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(initialLiveCaptureLocals == initialLiveCaptureLocals.distinct)

    val CoordT(_, _, structTT @ StructTT(_)) = structType2
    val structDefT = temputs.getStructDefForRef(structTT)
    // We don't pattern match against closure structs.

    val memberLocals =
      structDefT.members
        .map(_.tyype.expectReferenceMember().reference)
        .map(memberType => localHelper.makeTemporaryLocal(fate, memberType)).toList
    val destroyTE = DestroyTE(inputStructExpr, structTT, memberLocals)
    val liveCaptureLocals = initialLiveCaptureLocals ++ memberLocals
    vassert(liveCaptureLocals == liveCaptureLocals.distinct)

    val restTE =
      makeLetsForOwnAndMaybeContinue(
        temputs,
        fate,
        liveCaptureLocals,
        memberLocals,
        innerPatternMaybes,
        afterDestroySuccessContinuation)
    Templar.consecutive(List(destroyTE, restTE))
  }

  private def makeLetsForOwnAndMaybeContinue(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    initialLiveCaptureLocals: List[ILocalVariableT],
    memberLocalVariables: List[ILocalVariableT],
    innerPatternMaybes: List[AtomAP],
    afterLetsSuccessContinuation: (Temputs, FunctionEnvironmentBox, List[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(initialLiveCaptureLocals == initialLiveCaptureLocals.distinct)

    (memberLocalVariables, innerPatternMaybes) match {
      case (Nil, Nil) => {
        afterLetsSuccessContinuation(temputs, fate, initialLiveCaptureLocals)
      }
      case (headMemberLocalVariable :: tailMemberLocalVariables, headMaybeInnerPattern :: tailInnerPatternMaybes) => {
        val unletExpr = localHelper.unletLocal(fate, headMemberLocalVariable)
        val liveCaptureLocals = initialLiveCaptureLocals.filter(_ != headMemberLocalVariable)
        vassert(liveCaptureLocals.size == initialLiveCaptureLocals.size - 1)

        innerTranslateSubPatternAndMaybeContinue(
          temputs, fate, headMaybeInnerPattern, liveCaptureLocals, unletExpr,
          (temputs, fate, liveCaptureLocals) => {
            vassert(initialLiveCaptureLocals == initialLiveCaptureLocals.distinct)

            makeLetsForOwnAndMaybeContinue(
              temputs, fate, liveCaptureLocals, tailMemberLocalVariables, tailInnerPatternMaybes, afterLetsSuccessContinuation)
          })
      }
    }
  }

  private def loadResultOwnership(memberOwnershipInStruct: OwnershipT): OwnershipT = {
    memberOwnershipInStruct match {
      case OwnT => ConstraintT
      case ConstraintT => ConstraintT
      case WeakT => WeakT
      case ShareT => ShareT
    }
  }

  private def loadFromStruct(
    temputs: Temputs,
    range: RangeS,
    structPermission: PermissionT,
    containerAlias: ReferenceExpressionTE,
    structTT: StructTT,
    index: Int) = {
    val structDefT = temputs.getStructDefForRef(structTT)

    val member = structDefT.members(index)

    val memberCoord = member.tyype.expectReferenceMember().reference

    val memberPermissionInStruct = structDefT.members(index).tyype.reference.permission
    val resultPermission = Templar.intersectPermission(memberPermissionInStruct, structPermission)

    ReferenceMemberLookupTE(
      range,
      containerAlias,
      structDefT.fullName.addStep(structDefT.members(index).name),
      memberCoord,
      resultPermission,
      member.variability)
  }

  private def loadFromStaticSizedArray(
    range: RangeS,
    staticSizedArrayT: StaticSizedArrayTT,
    localCoord: CoordT,
    structOwnership: OwnershipT,
    structPermission: PermissionT,
    containerAlias: ReferenceExpressionTE,
    index: Int) = {
    arrayTemplar.lookupInStaticSizedArray(range, containerAlias, ConstantIntTE(index, 32), staticSizedArrayT)
  }
}
