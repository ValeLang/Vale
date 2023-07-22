package dev.vale.typing.expression

import dev.vale.highertyping.HigherTypingPass.explicifyLookups
import dev.vale.parsing.ast.LoadAsBorrowP
import dev.vale.postparsing._
import dev.vale.postparsing.patterns._
import dev.vale.{Err, Interner, Keywords, Ok, Profiler, RangeS, Result, vassert, vassertSome, vfail, vimpl}
import dev.vale.postparsing.rules.{IRulexSR, RuneUsage}
import dev.vale.typing.{ArrayCompiler, CompileErrorExceptionT, Compiler, CompilerOutputs, ConvertHelper, InferCompiler, InitialSend, RangedInternalErrorT, TypingPassOptions, WrongNumberOfDestructuresError}
import dev.vale.typing.ast.{ConstantIntTE, DestroyMutRuntimeSizedArrayTE, DestroyStaticSizedArrayIntoLocalsTE, DestroyTE, LetNormalTE, LocalLookupTE, LocationInFunctionEnvironmentT, ReferenceExpressionTE, ReferenceMemberLookupTE, SoftLoadTE}
import dev.vale.typing.env.{ILocalVariableT, NodeEnvironmentBox, TemplataEnvEntry}
import dev.vale.typing.function.DestructorCompiler
import dev.vale.typing.names._
import dev.vale.typing.templata.CoordTemplataT
import dev.vale.typing.types._
import dev.vale.highertyping._
import dev.vale.parsing.ast.LoadAsBorrowP
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing._
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.typing.env._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.typing._
import dev.vale.typing.ast._

import scala.collection.immutable.{List, Set}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class PatternCompiler(
    opts: TypingPassOptions,

    interner: Interner,
  keywords: Keywords,
  inferCompiler: InferCompiler,
    arrayCompiler: ArrayCompiler,
    convertHelper: ConvertHelper,
    nameTranslator: NameTranslator,
    destructorCompiler: DestructorCompiler,
    localHelper: LocalHelper) {
  // Note: This will unlet/drop the input expressions. Be warned.
  // patternInputsTE is a list of reference expression because they're coming in from
  // god knows where... arguments, the right side of a let, a variable, don't know!
  // If a pattern needs to send it to multiple places, the pattern is free to put it into
  // a local variable.
  // PatternCompiler must be sure to NOT USE IT TWICE! That would mean copying the entire
  // expression subtree that it contains!
  def translatePatternList(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    patternsA: Vector[AtomSP],
    patternInputsTE: Vector[ReferenceExpressionTE],
    region: RegionT,
    // This would be a continuation-ish lambda that evaluates:
    // - The body of an if-let statement
    // - The body of a match's case statement
    // - The rest of the pattern that contains this pattern
    // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
    afterPatternsSuccessContinuation: (CompilerOutputs, NodeEnvironmentBox, Vector[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    Profiler.frame(() => {
      iterateTranslateListAndMaybeContinue(
        coutputs, nenv, life, parentRanges, callLocation, Vector(), patternsA.toList, patternInputsTE.toList, region, afterPatternsSuccessContinuation)
    })
  }

  def iterateTranslateListAndMaybeContinue(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    liveCaptureLocals: Vector[ILocalVariableT],
    patternsA: List[AtomSP],
    patternInputsTE: List[ReferenceExpressionTE],
    region: RegionT,
    // This would be a continuation-ish lambda that evaluates:
    // - The body of an if-let statement
    // - The body of a match's case statement
    // - The rest of the pattern that contains this pattern
    // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
    afterPatternsSuccessContinuation: (CompilerOutputs, NodeEnvironmentBox, Vector[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    vassert(liveCaptureLocals.map(_.name) == liveCaptureLocals.map(_.name).distinct)

    (patternsA, patternInputsTE) match {
      case (Nil, Nil) => afterPatternsSuccessContinuation(coutputs, nenv, liveCaptureLocals)
      case (headPatternA :: tailPatternsA, headPatternInputTE :: tailPatternInputsTE) => {
        innerTranslateSubPatternAndMaybeContinue(
          coutputs, nenv, life + 0, parentRanges, callLocation, headPatternA, liveCaptureLocals, headPatternInputTE,
          region,
          (coutputs, nenv, life, liveCaptureLocals) => {
            vassert(liveCaptureLocals.map(_.name) == liveCaptureLocals.map(_.name).distinct)

            iterateTranslateListAndMaybeContinue(
              coutputs, nenv, life + 1, parentRanges, callLocation, liveCaptureLocals, tailPatternsA, tailPatternInputsTE, region, afterPatternsSuccessContinuation)
          })
      }
      case _ => vfail("wat")
    }
  }

  // Note: This will unlet/drop the input expression. Be warned.
  def inferAndTranslatePattern(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    rulesWithImplicitlyCoercingLookupsS: Vector[IRulexSR],
    runeAToTypeWithImplicitlyCoercingLookupsS: Map[IRuneS, ITemplataType],
    pattern: AtomSP,
    unconvertedInputExpr: ReferenceExpressionTE,
    region: RegionT,
    // This would be a continuation-ish lambda that evaluates:
    // - The body of an if-let statement
    // - The body of a match's case statement
    // - The rest of the pattern that contains this pattern
    // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
    afterPatternsSuccessContinuation: (CompilerOutputs, NodeEnvironmentBox, LocationInFunctionEnvironmentT, Vector[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    Profiler.frame(() => {

      // The rules are different depending on the incoming type.
      // See Impl Rule For Upcasts (IRFU).
      val convertedInputExpr =
        pattern.coordRune match {
          case None => {
            unconvertedInputExpr
          }
          case Some(receiverRune) => {
            val runeTypeSolveEnv = TemplataCompiler.createRuneTypeSolverEnv(nenv.snapshot)

            val runeAToType =
              mutable.HashMap[IRuneS, ITemplataType]((runeAToTypeWithImplicitlyCoercingLookupsS.toSeq): _*)
            // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
            // loose. We intentionally ignored the types of the things they're looking up, so we could know
            // what types we *expect* them to be, so we could coerce.
            // That coercion is good, but lets make it more explicit.
            val ruleBuilder = ArrayBuffer[IRulexSR]()
            explicifyLookups(
              runeTypeSolveEnv,
              runeAToType, ruleBuilder, rulesWithImplicitlyCoercingLookupsS) match {
              case Err(RuneTypingTooManyMatchingTypes(range, name)) => throw CompileErrorExceptionT(TooManyTypesWithNameT(range :: parentRanges, name))
              case Err(RuneTypingCouldntFindType(range, name)) => throw CompileErrorExceptionT(CouldntFindTypeT(range :: parentRanges, name))
              case Ok(()) =>
            }
            val rulesA = ruleBuilder.toVector

            val CompleteCompilerSolve(_, templatasByRune, _, Vector()) =
              // We could probably just solveForResolving (see DBDAR) but seems right to solveForDefining since we're
              // declaring a bunch of things.
              inferCompiler.solveForDefining(
                InferEnv(nenv.snapshot, parentRanges, callLocation, nenv.snapshot, nenv.defaultRegion),
                coutputs,
                rulesA,
                runeAToType.toMap,
                pattern.range :: parentRanges,
                callLocation,
                Vector(),
                Vector(
                  InitialSend(
                    RuneUsage(pattern.range, PatternInputRuneS(pattern.range.begin)),
                    receiverRune,
                    CoordTemplataT(unconvertedInputExpr.result.coord))),
                Vector()) match {
                case Err(f) => throw CompileErrorExceptionT(TypingPassSolverError(pattern.range :: parentRanges, f))
                case Ok(c@CompleteCompilerSolve(_, _, _, _)) => c
              }

            nenv.addEntries(
              interner,
              templatasByRune.toVector
                .map({ case (key, value) => (interner.intern(RuneNameT(key)), TemplataEnvEntry(value)) }))
            val CoordTemplataT(expectedCoord) = vassertSome(templatasByRune.get(receiverRune.rune))

            // Now we convert m to a Marine. This also checks that it *can* be
            // converted to a Marine.
            convertHelper.convert(nenv.snapshot, coutputs, pattern.range :: parentRanges, callLocation, unconvertedInputExpr, expectedCoord)
          }
        }

      innerTranslateSubPatternAndMaybeContinue(coutputs, nenv, life, parentRanges, callLocation, pattern, Vector(), convertedInputExpr, region, afterPatternsSuccessContinuation)
    })
  }

  private def innerTranslateSubPatternAndMaybeContinue(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    pattern: AtomSP,
    previousLiveCaptureLocals: Vector[ILocalVariableT],
    inputExpr: ReferenceExpressionTE,
    region: RegionT,
    // This would be a continuation-ish lambda that evaluates:
    // - The body of an if-let statement
    // - The body of a match's case statement
    // - The rest of the pattern that contains this pattern
    // But if we're doing a regular let statement, then it doesn't need to contain everything past it.
    afterSubPatternSuccessContinuation: (CompilerOutputs, NodeEnvironmentBox, LocationInFunctionEnvironmentT, Vector[ILocalVariableT]) => ReferenceExpressionTE):
  ReferenceExpressionTE = {
    vassert(previousLiveCaptureLocals.map(_.name) == previousLiveCaptureLocals.map(_.name).distinct)

    val AtomSP(range, maybeCaptureLocalVarA, coordRuneA, maybeDestructure) = pattern
    // TODO(CRASTBU): make test that we have the right type in there, cuz the coordRuneA seems to be unused

    // We make it here instead of down in the maybeDestructure clauses because whether we destructure it or not
    // is unrelated to whether we destructure it.


    var currentInstructions = Vector[ReferenceExpressionTE]()

    val (maybeCaptureLocalVarT, exprToDestructureOrDropOrPassTE) =
      maybeCaptureLocalVarA match {
        case None => (None, inputExpr)
        case Some(CaptureS(localNameS, mutate)) => {
          val localNameT = nameTranslator.translateVarNameStep(localNameS)
          val localT =
            if (mutate) {
              val localT =
                nenv.declaredLocals.find(_.name == localNameT) match {
                  case Some(rlv@ReferenceLocalVariableT(_, _, _)) => rlv
                }
              nenv.markLocalRestackified(localNameT)
              currentInstructions =
                currentInstructions :+
                    RestackifyTE(localT, inputExpr)
              localT
            } else {
              val localS = vassertSome(vassertSome(nenv.nearestBlockEnv())._2.locals.find(_.varName == localNameS))
              val localT = localHelper.makeUserLocalVariable(coutputs, nenv, range :: parentRanges, localS, inputExpr.result.coord)
              currentInstructions =
                currentInstructions :+
                    LetNormalTE(localT, inputExpr)
              localT
            }
          val capturedLocalAliasTE =
            localHelper.softLoad(nenv, range :: parentRanges, LocalLookupTE(range, localT), LoadAsBorrowP, region)
          (Some(localT), capturedLocalAliasTE)
        }
      }
    // If we captured it as a local, then the exprToDestructureOrDropOrPassTE should be a non-owning alias of it
    if (maybeCaptureLocalVarT.nonEmpty) {
      vassert(exprToDestructureOrDropOrPassTE.result.coord.ownership != OwnT)
    }

    val liveCaptureLocals = previousLiveCaptureLocals ++ maybeCaptureLocalVarT.toVector
    vassert(liveCaptureLocals.map(_.name) == liveCaptureLocals.map(_.name).distinct)

    Compiler.consecutive(
      currentInstructions ++
        (maybeDestructure match {
          case None => {
            // If we get here, we aren't destructuring, or doing anything with the exprToDestructureOrDropOrPassTE.

            (maybeCaptureLocalVarA match {
              case None => {
                // If we didn't store it, and we aren't destructuring it, then we're just ignoring it. Let's drop it.
                List(
                  destructorCompiler.drop(
                    nenv.snapshot, coutputs, range :: parentRanges, callLocation, region, exprToDestructureOrDropOrPassTE))
              }
              case Some(_) => {
                // We aren't destructuring it, but we stored it, so just do nothing.
                List()
              }
            }) ++
            List(
              // ...and then continue on.
              afterSubPatternSuccessContinuation(coutputs, nenv, life + 0, liveCaptureLocals))
          }
          case Some(listOfMaybeDestructureMemberPatterns) => {
            exprToDestructureOrDropOrPassTE.result.coord.ownership match {
              case OwnT => {
                // We aren't capturing the var, so the destructuring should consume the incoming value.
                List(
                  destructureOwning(
                    coutputs, nenv, life + 1, range :: parentRanges, callLocation, liveCaptureLocals, exprToDestructureOrDropOrPassTE, listOfMaybeDestructureMemberPatterns, region, afterSubPatternSuccessContinuation))
              }
              case BorrowT | ShareT => {
                List(
                  destructureNonOwningAndMaybeContinue(
                    coutputs, nenv, life + 2, range :: parentRanges, callLocation, liveCaptureLocals, exprToDestructureOrDropOrPassTE, listOfMaybeDestructureMemberPatterns, region, afterSubPatternSuccessContinuation))
              }
            }
          }
        }))
  }

  private def destructureOwning(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    initialLiveCaptureLocals: Vector[ILocalVariableT],
    inputExpr: ReferenceExpressionTE,
    listOfMaybeDestructureMemberPatterns: Vector[AtomSP],
    region: RegionT,
    afterDestructureSuccessContinuation: (CompilerOutputs, NodeEnvironmentBox, LocationInFunctionEnvironmentT, Vector[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(initialLiveCaptureLocals.map(_.name) == initialLiveCaptureLocals.map(_.name).distinct)

    val CoordT(OwnT, _, expectedContainerKind) = inputExpr.result.coord
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
          coutputs, nenv, life + 0, parentRanges, callLocation, initialLiveCaptureLocals, listOfMaybeDestructureMemberPatterns, inputExpr, region, afterDestructureSuccessContinuation)
      }
      case staticSizedArrayT @ contentsStaticSizedArrayTT(sizeTemplata, _, _, elementType, _) => {
        val size =
          sizeTemplata match {
            case PlaceholderTemplataT(_, IntegerTemplataType()) => {
              throw CompileErrorExceptionT(RangedInternalErrorT(parentRanges, "Can't create static sized array by values, can't guarantee size is correct!"))
            }
            case IntegerTemplataT(size) => {
              if (size != listOfMaybeDestructureMemberPatterns.size) {
                throw CompileErrorExceptionT(RangedInternalErrorT(parentRanges, "Wrong num exprs!"))
              }
              size
            }
          }

        val elementLocals = (0 until size.toInt).map(i => localHelper.makeTemporaryLocal(nenv, life + 3 + i, elementType)).toVector
        val destroyTE = DestroyStaticSizedArrayIntoLocalsTE(inputExpr, staticSizedArrayT, elementLocals)
        val liveCaptureLocals = initialLiveCaptureLocals ++ elementLocals
        vassert(liveCaptureLocals.map(_.name) == liveCaptureLocals.map(_.name).distinct)

        if (elementLocals.size != listOfMaybeDestructureMemberPatterns.size) {
          throw CompileErrorExceptionT(WrongNumberOfDestructuresError(parentRanges, listOfMaybeDestructureMemberPatterns.size, elementLocals.size))
        }
        val lets =
          makeLetsForOwnAndMaybeContinue(
            coutputs, nenv, life + 4, parentRanges, callLocation, liveCaptureLocals, elementLocals.toList, listOfMaybeDestructureMemberPatterns.toList, region, afterDestructureSuccessContinuation)
        Compiler.consecutive(Vector(destroyTE, lets))
      }
      case rsa @ contentsRuntimeSizedArrayTT(_, _, _) => {
        if (listOfMaybeDestructureMemberPatterns.nonEmpty) {
          throw CompileErrorExceptionT(RangedInternalErrorT(parentRanges, "Can only destruct RSA with zero destructure targets."))
        }
        DestroyMutRuntimeSizedArrayTE(inputExpr)
      }
      case _ => vfail("impl!")
    }
  }

  private def destructureNonOwningAndMaybeContinue(
      coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironmentT,
      range: List[RangeS],
      callLocation: LocationInDenizen,
      liveCaptureLocals: Vector[ILocalVariableT],
      containerTE: ReferenceExpressionTE,
      listOfMaybeDestructureMemberPatterns: Vector[AtomSP],
    region: RegionT,
      afterDestructureSuccessContinuation: (CompilerOutputs, NodeEnvironmentBox, LocationInFunctionEnvironmentT, Vector[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(liveCaptureLocals.map(_.name) == liveCaptureLocals.map(_.name).distinct)

    val localT = localHelper.makeTemporaryLocal(nenv, life + 0, containerTE.result.coord)
    val letTE = LetNormalTE(localT, containerTE)
    val containerAliasingExprTE =
      localHelper.softLoad(nenv, range, LocalLookupTE(range.head, localT), LoadAsBorrowP, region)

    Compiler.consecutive(
      Vector(
        letTE,
        iterateDestructureNonOwningAndMaybeContinue(
          coutputs, nenv, life + 1, range, callLocation, liveCaptureLocals, containerTE.result.coord, containerAliasingExprTE, 0, listOfMaybeDestructureMemberPatterns.toList, region, afterDestructureSuccessContinuation)))
  }

  private def iterateDestructureNonOwningAndMaybeContinue(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    liveCaptureLocals: Vector[ILocalVariableT],
    expectedContainerCoord: CoordT,
    containerAliasingExprTE: ReferenceExpressionTE,
    memberIndex: Int,
    listOfMaybeDestructureMemberPatterns: List[AtomSP],
    region: RegionT,
    afterDestructureSuccessContinuation: (CompilerOutputs, NodeEnvironmentBox, LocationInFunctionEnvironmentT, Vector[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(liveCaptureLocals.map(_.name) == liveCaptureLocals.map(_.name).distinct)

    val CoordT(expectedContainerOwnership, expectedRegion, expectedContainerKind) = expectedContainerCoord

    listOfMaybeDestructureMemberPatterns match {
      case Nil => afterDestructureSuccessContinuation(coutputs, nenv, life + 0, liveCaptureLocals)
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

              loadFromStruct(
                coutputs,
                nenv.snapshot,
                headMaybeDestructureMemberPattern.range,
                expectedRegion,
                containerAliasingExprTE,
                structTT,
                memberIndex)
            }
            case staticSizedArrayT@contentsStaticSizedArrayTT(size, _, _, elementType, _) => {
              loadFromStaticSizedArray(headMaybeDestructureMemberPattern.range, staticSizedArrayT, expectedContainerCoord, expectedContainerOwnership, containerAliasingExprTE, memberIndex)
            }
            case other => {
              throw CompileErrorExceptionT(RangedInternalErrorT(parentRanges, "Unknown type to destructure: " + other))
            }
          }

        val memberOwnershipInStruct = memberAddrExprTE.result.coord.ownership
        val coerceToOwnership = loadResultOwnership(memberOwnershipInStruct)

        val loadExpr = SoftLoadTE(memberAddrExprTE, coerceToOwnership)
        innerTranslateSubPatternAndMaybeContinue(
          coutputs, nenv, life + 1, parentRanges, callLocation, headMaybeDestructureMemberPattern, liveCaptureLocals, loadExpr,
          region,
          (coutputs, nenv, life, liveCaptureLocals) => {
            vassert(liveCaptureLocals.map(_.name) == liveCaptureLocals.map(_.name).distinct)

            val nextMemberIndex = memberIndex + 1
            iterateDestructureNonOwningAndMaybeContinue(
              coutputs,
              nenv,
              life,
              parentRanges,
              callLocation,
              liveCaptureLocals,
              expectedContainerCoord,
              containerAliasingExprTE,
              nextMemberIndex,
              tailDestructureMemberPatternMaybes,
              region,
              afterDestructureSuccessContinuation)
          })
      }
    }
  }

  private def translateDestroyStructInnerAndMaybeContinue(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    initialLiveCaptureLocals: Vector[ILocalVariableT],
    innerPatterns: Vector[AtomSP],
    inputStructExpr: ReferenceExpressionTE,
    region: RegionT,
    afterDestroySuccessContinuation: (CompilerOutputs, NodeEnvironmentBox, LocationInFunctionEnvironmentT, Vector[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(initialLiveCaptureLocals.map(_.name) == initialLiveCaptureLocals.map(_.name).distinct)

    val CoordT(_, _, structTT @ StructTT(_)) = inputStructExpr.result.coord
    val structDefT = coutputs.lookupStruct(structTT.id)
    // We don't pattern match against closure structs.

    val substituter =
      TemplataCompiler.getPlaceholderSubstituter(
        interner,
        keywords,
        structTT.id,
        // We're receiving something of this type, so it should supply its own bounds.
        InheritBoundsFromTypeItself)

    val memberLocals =
      structDefT.members
        .map({
          case NormalStructMemberT(name, variability, ReferenceMemberTypeT(reference)) => reference
          case NormalStructMemberT(name, variability, AddressMemberTypeT(_)) => vimpl()
          case VariadicStructMemberT(name, tyype) => vimpl()
        })
        .map(unsubstitutedMemberCoord => substituter.substituteForCoord(coutputs, unsubstitutedMemberCoord))
        .zipWithIndex
        .map({ case (memberType, i) => localHelper.makeTemporaryLocal(nenv, life + 1 + i, memberType) }).toVector
    val destroyTE = DestroyTE(inputStructExpr, structTT, memberLocals)
    val liveCaptureLocals = initialLiveCaptureLocals ++ memberLocals
    vassert(liveCaptureLocals.map(_.name) == liveCaptureLocals.map(_.name).distinct)

    if (memberLocals.size != innerPatterns.size) {
      throw CompileErrorExceptionT(WrongNumberOfDestructuresError(parentRanges, innerPatterns.size, memberLocals.size))
    }
    val restTE =
      makeLetsForOwnAndMaybeContinue(
        coutputs,
        nenv,
        life + 0,
        parentRanges,
        callLocation,
        liveCaptureLocals,
        memberLocals.toList,
        innerPatterns.toList,
        region,
        afterDestroySuccessContinuation)
    Compiler.consecutive(Vector(destroyTE, restTE))
  }

  private def makeLetsForOwnAndMaybeContinue(
    coutputs: CompilerOutputs,
    nenv: NodeEnvironmentBox,
    life: LocationInFunctionEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    initialLiveCaptureLocals: Vector[ILocalVariableT],
    memberLocalVariables: List[ILocalVariableT],
    innerPatterns: List[AtomSP],
    region: RegionT,
    afterLetsSuccessContinuation: (CompilerOutputs, NodeEnvironmentBox, LocationInFunctionEnvironmentT, Vector[ILocalVariableT]) => ReferenceExpressionTE
  ): ReferenceExpressionTE = {
    vassert(initialLiveCaptureLocals.map(_.name) == initialLiveCaptureLocals.map(_.name).distinct)

    vassert(memberLocalVariables.size == innerPatterns.size)

    (memberLocalVariables, innerPatterns) match {
      case (Nil, Nil) => {
        afterLetsSuccessContinuation(coutputs, nenv, life + 0, initialLiveCaptureLocals)
      }
      case (headMemberLocalVariable :: tailMemberLocalVariables, headInnerPattern :: tailInnerPatternMaybes) => {
        val unletExpr = localHelper.unletLocalWithoutDropping(nenv, headMemberLocalVariable)
        val liveCaptureLocals = initialLiveCaptureLocals.filter(_.name != headMemberLocalVariable.name)
        vassert(liveCaptureLocals.size == initialLiveCaptureLocals.size - 1)

        innerTranslateSubPatternAndMaybeContinue(
          coutputs, nenv, life + 1, headInnerPattern.range :: parentRanges, callLocation, headInnerPattern, liveCaptureLocals, unletExpr,
          region,
          (coutputs, nenv, life, liveCaptureLocals) => {
            vassert(initialLiveCaptureLocals.map(_.name) == initialLiveCaptureLocals.map(_.name).distinct)

            makeLetsForOwnAndMaybeContinue(
              coutputs, nenv, life, parentRanges, callLocation, liveCaptureLocals, tailMemberLocalVariables, tailInnerPatternMaybes, region, afterLetsSuccessContinuation)
          })
      }
    }
  }

  private def loadResultOwnership(memberOwnershipInStruct: OwnershipT): OwnershipT = {
    memberOwnershipInStruct match {
      case OwnT => BorrowT
      case BorrowT => BorrowT
      case WeakT => WeakT
      case ShareT => ShareT
    }
  }

  private def loadFromStruct(
    coutputs: CompilerOutputs,
    env: IInDenizenEnvironmentT,
    loadRange: RangeS,
    region: RegionT,
    containerAlias: ReferenceExpressionTE,
    structTT: StructTT,
    index: Int):
  ReferenceMemberLookupTE = {
    val structDefT = coutputs.lookupStruct(structTT.id)

    val member = structDefT.members(index)

    val (variability, unsubstitutedMemberCoord) =
      member match {
        case NormalStructMemberT(name, variability, ReferenceMemberTypeT(reference)) => (variability, reference)
        case NormalStructMemberT(name, variability, AddressMemberTypeT(_)) => vimpl()
        case VariadicStructMemberT(name, tyype) => vimpl()
      }
    val memberType =
      TemplataCompiler.getPlaceholderSubstituter(
        interner,
        keywords,
        structTT.id,
        // Use the bounds that we supplied to the struct
        UseBoundsFromContainer(
          structDefT.runeToFunctionBound,
          structDefT.runeToImplBound,
          vassertSome(coutputs.getInstantiationBounds(structTT.id))))
        .substituteForCoord(coutputs, unsubstitutedMemberCoord)

    ReferenceMemberLookupTE(
      loadRange,
      containerAlias,
      structDefT.members(index).name,
      memberType,
      variability)
  }

  private def loadFromStaticSizedArray(
      range: RangeS,
      staticSizedArrayT: StaticSizedArrayTT,
      localCoord: CoordT,
      structOwnership: OwnershipT,
      containerAlias: ReferenceExpressionTE,
      index: Int): StaticSizedArrayLookupTE = {
    arrayCompiler.lookupInStaticSizedArray(
      range, containerAlias, ConstantIntTE(IntegerTemplataT(index), 32, RegionT()), staticSizedArrayT)
  }
}
