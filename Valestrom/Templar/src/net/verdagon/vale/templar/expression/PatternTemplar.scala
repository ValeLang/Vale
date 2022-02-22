package net.verdagon.vale.templar.expression

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.ast.{LoadAsBorrowP, LoadAsPointerP}
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.rules.{IRulexSR, RuneUsage}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.DestructorTemplar
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.{ConstantIntTE, DestroyMutRuntimeSizedArrayTE, DestroyStaticSizedArrayIntoLocalsTE, DestroyTE, LetNormalTE, LocalLookupTE, LocationInFunctionEnvironment, ReferenceExpressionTE, ReferenceMemberLookupTE, SoftLoadTE, TemplarReinterpretTE}
import net.verdagon.vale.templar.names.RuneNameT
import net.verdagon.vale.{IProfiler, Interner, RangeS, vassert, vassertSome, vfail}

import scala.collection.immutable.List

class PatternTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
    interner: Interner,
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
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    patternsA: Vector[AtomSP],
    patternInputsTE: Vector[ReferenceExpressionTE],
    // This would be a continuation-ish lambda that evaluates:
    // - The body of an if-let statement
    // - The body of a match's case statement
    // - The rest of the pattern that contains this pattern
    // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
    afterPatternsSuccessContinuation: (Temputs, NodeEnvironmentBox, Vector[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    profiler.newProfile("translatePatternList", nenv.fullName.toString, () => {
      iterateTranslateListAndMaybeContinue(
        temputs, nenv, life, Vector(), patternsA.toList, patternInputsTE.toList, afterPatternsSuccessContinuation)
    })
  }

  def iterateTranslateListAndMaybeContinue(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    liveCaptureLocals: Vector[ILocalVariableT],
    patternsA: List[AtomSP],
    patternInputsTE: List[ReferenceExpressionTE],
    // This would be a continuation-ish lambda that evaluates:
    // - The body of an if-let statement
    // - The body of a match's case statement
    // - The rest of the pattern that contains this pattern
    // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
    afterPatternsSuccessContinuation: (Temputs, NodeEnvironmentBox, Vector[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    vassert(liveCaptureLocals.map(_.id) == liveCaptureLocals.map(_.id).distinct)

    (patternsA, patternInputsTE) match {
      case (Nil, Nil) => afterPatternsSuccessContinuation(temputs, nenv, liveCaptureLocals)
      case (headPatternA :: tailPatternsA, headPatternInputTE :: tailPatternInputsTE) => {
        innerTranslateSubPatternAndMaybeContinue(
          temputs, nenv, life + 0, headPatternA, liveCaptureLocals, headPatternInputTE,
          (temputs, nenv, life, liveCaptureLocals) => {
            vassert(liveCaptureLocals.map(_.id) == liveCaptureLocals.map(_.id).distinct)

            iterateTranslateListAndMaybeContinue(
              temputs, nenv, life + 1, liveCaptureLocals, tailPatternsA, tailPatternInputsTE, afterPatternsSuccessContinuation)
          })
      }
      case _ => vfail("wat")
    }
  }

  // Note: This will unlet/drop the input expression. Be warned.
  def inferAndTranslatePattern(
      temputs: Temputs,
    nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      rules: Vector[IRulexSR],
      runeToType: Map[IRuneS, ITemplataType],
      pattern: AtomSP,
      unconvertedInputExpr: ReferenceExpressionTE,
      // This would be a continuation-ish lambda that evaluates:
      // - The body of an if-let statement
      // - The body of a match's case statement
      // - The rest of the pattern that contains this pattern
      // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
      afterPatternsSuccessContinuation: (Temputs, NodeEnvironmentBox, LocationInFunctionEnvironment, Vector[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    profiler.newProfile("inferAndTranslatePattern", nenv.fullName.toString, () => {

      // The rules are different depending on the incoming type.
      // See Impl Rule For Upcasts (IRFU).
      val convertedInputExpr =
        pattern.coordRune match {
          case None => {
            unconvertedInputExpr
          }
          case Some(receiverRune) => {
            val templatasByRune =
              inferTemplar.solveExpectComplete(
                nenv.snapshot,
                temputs,
                rules,
                runeToType,
                pattern.range,
                Vector(),
                Vector(
                  InitialSend(
                    RuneUsage(pattern.range, PatternInputRuneS(pattern.range.begin)),
                    receiverRune,
                    CoordTemplata(unconvertedInputExpr.result.reference))))
            nenv.addEntries(
              interner,
              templatasByRune.toVector
                .map({ case (key, value) => (interner.intern(RuneNameT(key)), TemplataEnvEntry(value)) }))
            val CoordTemplata(expectedCoord) = vassertSome(templatasByRune.get(receiverRune.rune))

            // Now we convert m to a Marine. This also checks that it *can* be
            // converted to a Marine.
            convertHelper.convert(nenv.snapshot, temputs, pattern.range, unconvertedInputExpr, expectedCoord)
          }
        }

      innerTranslateSubPatternAndMaybeContinue(temputs, nenv, life, pattern, Vector(), convertedInputExpr, afterPatternsSuccessContinuation)
    })
  }

  private def innerTranslateSubPatternAndMaybeContinue(
      temputs: Temputs,
    nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      pattern: AtomSP,
      previousLiveCaptureLocals: Vector[ILocalVariableT],
      inputExpr: ReferenceExpressionTE,
      // This would be a continuation-ish lambda that evaluates:
      // - The body of an if-let statement
      // - The body of a match's case statement
      // - The rest of the pattern that contains this pattern
      // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
      afterSubPatternSuccessContinuation: (Temputs, NodeEnvironmentBox, LocationInFunctionEnvironment, Vector[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    vassert(previousLiveCaptureLocals.map(_.id) == previousLiveCaptureLocals.map(_.id).distinct)

    val AtomSP(range, maybeCaptureLocalVarA, maybeVirtuality, coordRuneA, maybeDestructure) = pattern

    if (maybeVirtuality.nonEmpty) {
      // This is actually to be expected for when we translate the patterns from the
      // function's parameters. Ignore them.
    }

    // We make it here instead of down in the maybeDestructure clauses because whether we destructure it or not
    // is unrelated to whether we destructure it.


    var currentInstructions = Vector[ReferenceExpressionTE]()

    val (maybeCaptureLocalVarT, exprToDestructureOrDropOrPassTE) =
      maybeCaptureLocalVarA match {
        case None => (None, inputExpr)
        case Some(captureS) => {
          val localS = vassertSome(vassertSome(nenv.nearestBlockEnv())._2.locals.find(_.varName == captureS.name))
          val localT = localHelper.makeUserLocalVariable(temputs, nenv, range, localS, inputExpr.result.reference)
          currentInstructions = currentInstructions :+ LetNormalTE(localT, inputExpr)
          val capturedLocalAliasTE =
            localHelper.softLoad(nenv, range, LocalLookupTE(range, localT), LoadAsBorrowP(None))
          (Some(localT), capturedLocalAliasTE)
        }
      }
    // If we captured it as a local, then the exprToDestructureOrDropOrPassTE should be a non-owning alias of it
    if (maybeCaptureLocalVarT.nonEmpty) {
      vassert(exprToDestructureOrDropOrPassTE.result.reference.ownership != OwnT)
    }

    val liveCaptureLocals = previousLiveCaptureLocals ++ maybeCaptureLocalVarT.toVector
    vassert(liveCaptureLocals.map(_.id) == liveCaptureLocals.map(_.id).distinct)

    Templar.consecutive(
      currentInstructions :+
        (maybeDestructure match {
        case None => {
          // Do nothing
          afterSubPatternSuccessContinuation(temputs, nenv, life + 0, liveCaptureLocals)
        }
        case Some(listOfMaybeDestructureMemberPatterns) => {
          exprToDestructureOrDropOrPassTE.result.reference.ownership match {
            case OwnT => {
              // We aren't capturing the var, so the destructuring should consume the incoming value.
              destructureOwning(
                temputs, nenv, life + 1, range, liveCaptureLocals, exprToDestructureOrDropOrPassTE, listOfMaybeDestructureMemberPatterns, afterSubPatternSuccessContinuation)
            }
            case PointerT | BorrowT | ShareT => {
              destructureNonOwningAndMaybeContinue(
                temputs, nenv, life + 2, range, liveCaptureLocals, exprToDestructureOrDropOrPassTE, listOfMaybeDestructureMemberPatterns, afterSubPatternSuccessContinuation)
            }
          }
        }
      }))
  }

  private def destructureOwning(
      temputs: Temputs,
    nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      range: RangeS,
      initialLiveCaptureLocals: Vector[ILocalVariableT],
      inputExpr: ReferenceExpressionTE,
      listOfMaybeDestructureMemberPatterns: Vector[AtomSP],
      afterDestructureSuccessContinuation: (Temputs, NodeEnvironmentBox, LocationInFunctionEnvironment, Vector[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(initialLiveCaptureLocals.map(_.id) == initialLiveCaptureLocals.map(_.id).distinct)

    val CoordT(OwnT, expectedContainerPermission, expectedContainerKind) = inputExpr.result.reference
    expectedContainerKind match {
      case StructTT(_) => {
        // Example:
        //   struct Marine { bork: Bork; }
        //   Marine(b) = m;
        // In this case, expectedStructType1 = TypeName1("Marine") and
        // destructureMemberPatterns = Vector(CaptureSP("b", FinalP, None)).
        // Since we're receiving an owning reference, and we're *not* capturing
        // it in a variable, it will be destroyed and we will harvest its parts.
        translateDestroyStructInnerAndMaybeContinue(
          temputs, nenv, life + 0, range, initialLiveCaptureLocals, listOfMaybeDestructureMemberPatterns, inputExpr, afterDestructureSuccessContinuation)
      }
      case staticSizedArrayT @ StaticSizedArrayTT(size, _, _, elementType) => {
        if (size != listOfMaybeDestructureMemberPatterns.size) {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Wrong num exprs!"))
        }

        val elementLocals = (0 until size).map(i => localHelper.makeTemporaryLocal(nenv, life + 3 + i, elementType)).toVector
        val destroyTE = DestroyStaticSizedArrayIntoLocalsTE(inputExpr, staticSizedArrayT, elementLocals)
        val liveCaptureLocals = initialLiveCaptureLocals ++ elementLocals
        vassert(liveCaptureLocals.map(_.id) == liveCaptureLocals.map(_.id).distinct)

        if (elementLocals.size != listOfMaybeDestructureMemberPatterns.size) {
          throw CompileErrorExceptionT(WrongNumberOfDestructuresError(range, listOfMaybeDestructureMemberPatterns.size, elementLocals.size))
        }
        val lets =
          makeLetsForOwnAndMaybeContinue(
            temputs, nenv, life + 4, liveCaptureLocals, elementLocals.toList, listOfMaybeDestructureMemberPatterns.toList, afterDestructureSuccessContinuation)
        Templar.consecutive(Vector(destroyTE, lets))
      }
      case rsa @ RuntimeSizedArrayTT(_, _) => {
        if (listOfMaybeDestructureMemberPatterns.nonEmpty) {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can only destruct RSA with zero destructure targets."))
        }
        DestroyMutRuntimeSizedArrayTE(inputExpr)
      }
      case _ => vfail("impl!")
    }
  }

  private def destructureNonOwningAndMaybeContinue(
      temputs: Temputs,
    nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      range: RangeS,
      liveCaptureLocals: Vector[ILocalVariableT],
      containerTE: ReferenceExpressionTE,
      listOfMaybeDestructureMemberPatterns: Vector[AtomSP],
      afterDestructureSuccessContinuation: (Temputs, NodeEnvironmentBox, LocationInFunctionEnvironment, Vector[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(liveCaptureLocals.map(_.id) == liveCaptureLocals.map(_.id).distinct)

    val localT = localHelper.makeTemporaryLocal(nenv, life + 0, containerTE.result.reference)
    val letTE = LetNormalTE(localT, containerTE)
    val containerAliasingExprTE =
      localHelper.softLoad(nenv, range, LocalLookupTE(range, localT), LoadAsPointerP(None))

    Templar.consecutive(
      Vector(
        letTE,
        iterateDestructureNonOwningAndMaybeContinue(
          temputs, nenv, life + 1, range, liveCaptureLocals, containerTE.result.reference, containerAliasingExprTE, 0, listOfMaybeDestructureMemberPatterns.toList, afterDestructureSuccessContinuation)))
  }

  private def iterateDestructureNonOwningAndMaybeContinue(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    range: RangeS,
    liveCaptureLocals: Vector[ILocalVariableT],
    expectedContainerCoord: CoordT,
    containerAliasingExprTE: ReferenceExpressionTE,
    memberIndex: Int,
    listOfMaybeDestructureMemberPatterns: List[AtomSP],
    afterDestructureSuccessContinuation: (Temputs, NodeEnvironmentBox, LocationInFunctionEnvironment, Vector[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(liveCaptureLocals.map(_.id) == liveCaptureLocals.map(_.id).distinct)

    val CoordT(expectedContainerOwnership, expectedContainerPermission, expectedContainerKind) = expectedContainerCoord

    listOfMaybeDestructureMemberPatterns match {
      case Nil => afterDestructureSuccessContinuation(temputs, nenv, life + 0, liveCaptureLocals)
      case headMaybeDestructureMemberPattern :: tailDestructureMemberPatternMaybes => {
        val memberAddrExprTE =
          expectedContainerKind match {
            case structTT@StructTT(_) => {
              // Example:
              //   struct Marine { bork: Bork; }
              //   Marine(b) = m;
              // In this case, expectedStructType1 = TypeName1("Marine") and
              // destructureMemberPatterns = Vector(CaptureSP("b", FinalP, None)).
              // Since we're receiving an owning reference, and we're *not* capturing
              // it in a variable, it will be destroyed and we will harvest its parts.

              loadFromStruct(temputs, range, expectedContainerPermission, containerAliasingExprTE, structTT, memberIndex)
            }
            case staticSizedArrayT@StaticSizedArrayTT(size, _, _, elementType) => {
              loadFromStaticSizedArray(range, staticSizedArrayT, expectedContainerCoord, expectedContainerOwnership, expectedContainerPermission, containerAliasingExprTE, memberIndex)
            }
            case other => {
              throw CompileErrorExceptionT(RangedInternalErrorT(range, "Unknown type to destructure: " + other))
            }
          }

        val memberOwnershipInStruct = memberAddrExprTE.result.reference.ownership
        val coerceToOwnership = loadResultOwnership(memberOwnershipInStruct)
        val memberPermissionInStruct = memberAddrExprTE.result.reference.permission
        val corceToPermission = Templar.intersectPermission(memberPermissionInStruct, expectedContainerPermission)

        if (coerceToOwnership == ShareT) {
          if (corceToPermission != ReadonlyT) {
            throw CompileErrorExceptionT(RangedInternalErrorT(range, "Share ref doesnt have readonly permission!"))
          }
        }

        val loadExpr =
          SoftLoadTE(memberAddrExprTE, coerceToOwnership, corceToPermission)
        innerTranslateSubPatternAndMaybeContinue(
          temputs, nenv, life + 1, headMaybeDestructureMemberPattern, liveCaptureLocals, loadExpr,
          (temputs, nenv, life, liveCaptureLocals) => {
            vassert(liveCaptureLocals.map(_.id) == liveCaptureLocals.map(_.id).distinct)

            val nextMemberIndex = memberIndex + 1
            iterateDestructureNonOwningAndMaybeContinue(
              temputs,
              nenv,
              life,
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
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    range: RangeS,
    initialLiveCaptureLocals: Vector[ILocalVariableT],
    innerPatternMaybes: Vector[AtomSP],
    inputStructExpr: ReferenceExpressionTE,
    afterDestroySuccessContinuation: (Temputs, NodeEnvironmentBox, LocationInFunctionEnvironment, Vector[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(initialLiveCaptureLocals.map(_.id) == initialLiveCaptureLocals.map(_.id).distinct)

    val CoordT(_, _, structTT @ StructTT(_)) = inputStructExpr.result.reference
    val structDefT = temputs.getStructDefForRef(structTT)
    // We don't pattern match against closure structs.

    val memberLocals =
      structDefT.members
        .map(_.tyype.expectReferenceMember().reference)
        .zipWithIndex
        .map({ case (memberType, i) => localHelper.makeTemporaryLocal(nenv, life + 1 + i, memberType) }).toVector
    val destroyTE = DestroyTE(inputStructExpr, structTT, memberLocals)
    val liveCaptureLocals = initialLiveCaptureLocals ++ memberLocals
    vassert(liveCaptureLocals.map(_.id) == liveCaptureLocals.map(_.id).distinct)

    if (memberLocals.size != innerPatternMaybes.size) {
      throw CompileErrorExceptionT(WrongNumberOfDestructuresError(range, innerPatternMaybes.size, memberLocals.size))
    }
    val restTE =
      makeLetsForOwnAndMaybeContinue(
        temputs,
        nenv,
        life + 0,
        liveCaptureLocals,
        memberLocals.toList,
        innerPatternMaybes.toList,
        afterDestroySuccessContinuation)
    Templar.consecutive(Vector(destroyTE, restTE))
  }

  private def makeLetsForOwnAndMaybeContinue(
    temputs: Temputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironment,
    initialLiveCaptureLocals: Vector[ILocalVariableT],
    memberLocalVariables: List[ILocalVariableT],
    innerPatternMaybes: List[AtomSP],
    afterLetsSuccessContinuation: (Temputs, NodeEnvironmentBox, LocationInFunctionEnvironment, Vector[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(initialLiveCaptureLocals.map(_.id) == initialLiveCaptureLocals.map(_.id).distinct)

    vassert(memberLocalVariables.size == innerPatternMaybes.size)

    (memberLocalVariables, innerPatternMaybes) match {
      case (Nil, Nil) => {
        afterLetsSuccessContinuation(temputs, nenv, life + 0, initialLiveCaptureLocals)
      }
      case (headMemberLocalVariable :: tailMemberLocalVariables, headMaybeInnerPattern :: tailInnerPatternMaybes) => {
        val unletExpr = localHelper.unletLocal(nenv, headMemberLocalVariable)
        val liveCaptureLocals = initialLiveCaptureLocals.filter(_.id != headMemberLocalVariable.id)
        vassert(liveCaptureLocals.size == initialLiveCaptureLocals.size - 1)

        innerTranslateSubPatternAndMaybeContinue(
          temputs, nenv, life + 1, headMaybeInnerPattern, liveCaptureLocals, unletExpr,
          (temputs, nenv, life, liveCaptureLocals) => {
            vassert(initialLiveCaptureLocals.map(_.id) == initialLiveCaptureLocals.map(_.id).distinct)

            makeLetsForOwnAndMaybeContinue(
              temputs, nenv, life, liveCaptureLocals, tailMemberLocalVariables, tailInnerPatternMaybes, afterLetsSuccessContinuation)
          })
      }
    }
  }

  private def loadResultOwnership(memberOwnershipInStruct: OwnershipT): OwnershipT = {
    memberOwnershipInStruct match {
      case OwnT => BorrowT
      case BorrowT => BorrowT
      case PointerT => PointerT
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
