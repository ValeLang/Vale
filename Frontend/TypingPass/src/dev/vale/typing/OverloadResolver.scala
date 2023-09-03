package dev.vale.typing

import dev.vale._
import dev.vale.postparsing._
import dev.vale.postparsing.rules._
import dev.vale.solver.IIncompleteOrFailedSolve
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.function._
import dev.vale.typing.infer.ITypingPassSolverError
import dev.vale.typing.types._
import dev.vale.highertyping._
import dev.vale.typing.function._
import dev.vale.postparsing.PostParserErrorHumanizer
import dev.vale.solver.FailedSolve
import OverloadResolver._
import dev.vale.highertyping.HigherTypingPass.explicifyLookups
import dev.vale.parsing.ast.ReadOnlyRegionRuneAttributeP
import dev.vale.typing.ast._
import dev.vale.typing.env._
import dev.vale.typing.templata._
import dev.vale.typing.ast._
import dev.vale.typing.names._
import dev.vale.typing.templata.ITemplataT.expectRegionPlaceholder

import scala.collection.immutable.{Map, Set}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
//import dev.vale.astronomer.ruletyper.{IRuleTyperEvaluatorDelegate, RuleTyperEvaluator, RuleTyperSolveFailure, RuleTyperSolveSuccess}
//import dev.vale.postparsing.rules.{EqualsSR, TemplexSR, TypedSR}
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.postparsing.ExplicitTemplateArgRuneS
import OverloadResolver.{IFindFunctionFailureReason, InferFailure, FindFunctionFailure, SpecificParamVirtualityDoesntMatch, WrongNumberOfArguments, WrongNumberOfTemplateArguments}
import dev.vale.typing.env._
//import dev.vale.typingpass.infer.infer.{InferSolveFailure, InferSolveSuccess}
import dev.vale.Profiler

import scala.collection.immutable.List

object OverloadResolver {

  sealed trait IFindFunctionFailureReason
  case class WrongNumberOfArguments(supplied: Int, expected: Int) extends IFindFunctionFailureReason {
    vpass()

    override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  }
  case class WrongNumberOfTemplateArguments(supplied: Int, expected: Int) extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
  case class SpecificParamDoesntSend(index: Int, argument: CoordT, parameter: CoordT) extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
  case class SpecificParamDoesntMatchExactly(index: Int, argument: CoordT, parameter: CoordT) extends IFindFunctionFailureReason {
    override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
    vpass()
  }
  case class SpecificParamRegionDoesntMatch(rune: IRuneS, suppliedMutability: IRegionMutabilityS, calleeMutability: IRegionMutabilityS) extends IFindFunctionFailureReason {
    override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
    vpass()
  }
  case class SpecificParamVirtualityDoesntMatch(index: Int) extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
  case class Outscored() extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
  case class RuleTypeSolveFailure(reason: RuneTypeSolveError) extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
  case class InferFailure(reason: IIncompleteOrFailedCompilerSolve) extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
  case class FindFunctionResolveFailure(reason: IResolvingError) extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
  case class CouldntEvaluateTemplateError(reason: IDefiningError) extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }


  case class FindFunctionFailure(
    name: IImpreciseNameS,
    args: Vector[CoordT],
    // All the banners we rejected, and the reason why
    rejectedCalleeToReason: Iterable[(ICalleeCandidate, IFindFunctionFailureReason)]
  ) {
    vpass()
    override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  }

  case class EvaluateFunctionFailure2(
    name: IImpreciseNameS,
    args: Vector[CoordT],
    // All the banners we rejected, and the reason why
    rejectedCalleeToReason: Iterable[(PrototypeT[IFunctionNameT], IFindFunctionFailureReason)]
  ) {
    vpass()
    override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  }
}

class OverloadResolver(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    templataCompiler: TemplataCompiler,
    inferCompiler: InferCompiler,
    functionCompiler: FunctionCompiler) {
  val runeTypeSolver = new RuneTypeSolver(interner)

  def findFunction(
    callingEnv: IInDenizenEnvironmentT,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Vector[IRuneS],
    contextRegion: RegionT,
    args: Vector[CoordT],
    extraEnvsToLookIn: Vector[IInDenizenEnvironmentT],
    exact: Boolean):
  Result[StampFunctionSuccess, FindFunctionFailure] = {
    Profiler.frame(() => {
      findPotentialFunction(
        callingEnv,
        coutputs,
        callRange,
        callLocation,
        functionName,
        explicitTemplateArgRulesS,
        explicitTemplateArgRunesS,
        contextRegion,
        args,
        extraEnvsToLookIn,
        exact) match {
        case Err(e) => return Err(e)
        case Ok(AttemptedCandidate(pure, maybeNewRegion, prototype)) => {
          Ok(StampFunctionSuccess(pure, maybeNewRegion, prototype, Map()))
//          Ok(
//            stampPotentialFunctionForPrototype(
//              coutputs, callingEnv, callRange, callLocation, potentialBanner, contextRegion, args))
        }
      }
    })
  }

  private def paramsMatch(
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    desiredParams: Vector[CoordT],
    candidateParams: Vector[CoordT],
    exact: Boolean):
  Result[Unit, IFindFunctionFailureReason] = {
    if (desiredParams.size != candidateParams.size) {
      return Err(WrongNumberOfArguments(desiredParams.size, candidateParams.size))
    }
    desiredParams.zip(candidateParams).zipWithIndex.foreach({
      case ((desiredParam, candidateParam), paramIndex) => {
        val desiredTemplata = desiredParam
        val candidateType = candidateParam

        if (exact) {
          if (desiredTemplata != candidateType) {
            return Err(SpecificParamDoesntMatchExactly(paramIndex, desiredTemplata, candidateType))
          }
        } else {
          if (!templataCompiler.isTypeConvertible(coutputs, callingEnv, parentRanges, callLocation, desiredTemplata, candidateType)) {
            return Err(SpecificParamDoesntSend(paramIndex, desiredTemplata, candidateType))
          }
        }
      }
    })
    // Would have bailed out early if there was a false
    Ok(())
  }

  case class SearchedEnvironment(
    needle: IImpreciseNameS,
    environment: IInDenizenEnvironmentT,
    matchingTemplatas: Vector[ITemplataT[ITemplataType]])

  private def getCandidateBanners(
    env: IInDenizenEnvironmentT,
    coutputs: CompilerOutputs,
    range: List[RangeS],
    functionName: IImpreciseNameS,
    paramFilters: Vector[CoordT],
    extraEnvsToLookIn: Vector[IInDenizenEnvironmentT],
    searchedEnvs: Accumulator[SearchedEnvironment],
    results: Accumulator[ICalleeCandidate]):
  Unit = {
    getCandidateBannersInner(env, coutputs, range, functionName, searchedEnvs, results)
    getParamEnvironments(coutputs, range, paramFilters)
      .foreach(e => getCandidateBannersInner(e, coutputs, range, functionName, searchedEnvs, results))
    extraEnvsToLookIn
      .foreach(e => getCandidateBannersInner(env, coutputs, range, functionName, searchedEnvs, results))
  }

  private def getCandidateBannersInner(
    env: IInDenizenEnvironmentT,
    coutputs: CompilerOutputs,
    range: List[RangeS],
    functionName: IImpreciseNameS,
    searchedEnvs: Accumulator[SearchedEnvironment],
    results: Accumulator[ICalleeCandidate]):
  Unit = {
    val candidates =
      env.lookupAllWithImpreciseName(functionName, Set(ExpressionLookupContext)).toVector.distinct
    searchedEnvs.add(SearchedEnvironment(functionName, env, candidates))
    candidates.foreach({
      case KindTemplataT(OverloadSetT(overloadsEnv, nameInOverloadsEnv)) => {
        getCandidateBannersInner(
          overloadsEnv, coutputs, range, nameInOverloadsEnv, searchedEnvs, results)
      }
      case KindTemplataT(sr@StructTT(_)) => {
        val structEnv = coutputs.getOuterEnvForType(range, TemplataCompiler.getStructTemplate(sr.id))
        getCandidateBannersInner(
          structEnv, coutputs, range, interner.intern(CodeNameS(keywords.underscoresCall)), searchedEnvs, results)
      }
      case KindTemplataT(sr@InterfaceTT(_)) => {
        val interfaceEnv = coutputs.getOuterEnvForType(range, TemplataCompiler.getInterfaceTemplate(sr.id))
        getCandidateBannersInner(
          interfaceEnv, coutputs, range, interner.intern(CodeNameS(keywords.underscoresCall)), searchedEnvs, results)
      }
      case ExternFunctionTemplataT(header) => {
        results.add(HeaderCalleeCandidate(header))
      }
      case PrototypeTemplataT(prototype) => {
        vassert(coutputs.getInstantiationBounds(prototype.id).nonEmpty)
        results.add(PrototypeTemplataCalleeCandidate(prototype))
      }
      case ft@FunctionTemplataT(_, _) => {
        results.add(FunctionCalleeCandidate(ft))
      }
    })
  }

  case class AttemptedCandidate(
      pure: Boolean,
      maybeNewRegion: Option[RegionT],
      prototype: PrototypeT[IFunctionNameT]
  )

  private def attemptCandidateBanner(
    callingEnv: IInDenizenEnvironmentT,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Vector[IRuneS],
    contextRegion: RegionT,
    args: Vector[CoordT],
    candidate: ICalleeCandidate,
    exact: Boolean):
  Result[AttemptedCandidate, IFindFunctionFailureReason] = {
    candidate match {
      case FunctionCalleeCandidate(ft@FunctionTemplataT(declaringEnv, function)) => {
        val identifyingRuneTemplataTypes = function.tyype.paramTypes
        if (explicitTemplateArgRunesS.size > identifyingRuneTemplataTypes.size) {
          Err(WrongNumberOfTemplateArguments(explicitTemplateArgRunesS.size, identifyingRuneTemplataTypes.size))
        } else {
          // Now that we know what types are expected, we can FINALLY rule-type these explicitly
          // specified template args! (The rest of the rule-typing happened back in the astronomer,
          // this is the one time we delay it, see MDRTCUT).

          // There might be less explicitly specified template args than there are types, and that's
          // fine. Hopefully the rest will be figured out by the rule evaluator.
          val explicitTemplateArgRuneToType =
          explicitTemplateArgRunesS.zip(identifyingRuneTemplataTypes).toMap


          val runeTypeSolveEnv =
            new IRuneTypeSolverEnv {
              override def lookup(range: RangeS, nameS: IImpreciseNameS):
              Result[IRuneTypeSolverLookupResult, IRuneTypingLookupFailedError] = {
                callingEnv.lookupNearestWithImpreciseName(nameS, Set(TemplataLookupContext)) match {
                  case Some(x) => Ok(TemplataLookupResult(x.tyype))
                  case None => Err(RuneTypingCouldntFindType(range, nameS))
//                        {
//                          throw CompileErrorExceptionT(
//                            RangedInternalErrorT(
//                              callRange,
//                              "Couldn't find a: " + PostParserErrorHumanizer.humanizeImpreciseName(nameS)))
//                        }
                }
              }
            }

          // And now that we know the types that are expected of these template arguments, we can
          // run these template argument templexes through the solver so it can evaluate them in
          // context of the current environment and spit out some templatas.
          runeTypeSolver.solve(
            opts.globalOptions.sanityCheck,
            opts.globalOptions.useOptimizedSolver,
            runeTypeSolveEnv,
            callRange,
            false,
            explicitTemplateArgRulesS,
            explicitTemplateArgRunesS,
            true,
            explicitTemplateArgRuneToType) match {
            case Err(e@RuneTypeSolveError(_, _)) => {
              Err(RuleTypeSolveFailure(e))
            }
            case Ok(runeAToTypeWithImplicitlyCoercingLookupsS) => {
              // rulesA is the equals rules, but rule typed. Now we'll run them through the solver to get
              // some actual templatas.

              val runeTypeSolveEnv = TemplataCompiler.createRuneTypeSolverEnv(callingEnv)

              val runeAToType =
                mutable.HashMap[IRuneS, ITemplataType]((runeAToTypeWithImplicitlyCoercingLookupsS.toSeq): _*)
              // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
              // loose. We intentionally ignored the types of the things they're looking up, so we could know
              // what types we *expect* them to be, so we could coerce.
              // That coercion is good, but lets make it more explicit.
              val ruleBuilder = ArrayBuffer[IRulexSR]()
              explicifyLookups(
                runeTypeSolveEnv,
                runeAToType, ruleBuilder, explicitTemplateArgRulesS) match {
                case Err(RuneTypingTooManyMatchingTypes(range, name)) => throw CompileErrorExceptionT(TooManyTypesWithNameT(range :: callRange, name))
                case Err(RuneTypingCouldntFindType(range, name)) => throw CompileErrorExceptionT(CouldntFindTypeT(range :: callRange, name))
                case Ok(()) =>
              }
              val rulesWithoutImplicitCoercionsA = ruleBuilder.toVector

              // We preprocess out the rune parent env lookups, see MKRFA.
              val (initialKnownsWithoutDefaultRegion, rulesWithoutRuneParentEnvLookups) =
                rulesWithoutImplicitCoercionsA.foldLeft((Vector[InitialKnown](), Vector[IRulexSR]()))({
                  case ((previousConclusions, remainingRules), RuneParentEnvLookupSR(_, rune)) => {
                    val templata =
                      vassertSome(
                        callingEnv.lookupNearestWithImpreciseName(
                          interner.intern(RuneNameS(rune.rune)), Set(TemplataLookupContext)))
                    val newConclusions = previousConclusions :+ InitialKnown(rune, templata)
                    (newConclusions, remainingRules)
                  }
                  case ((previousConclusions, remainingRules), rule) => {
                    (previousConclusions, remainingRules :+ rule)
                  }
                })
              // If the rules ask for a default region, here's where we supply it
              val initialKnowns =
               if (runeAToType.contains(contextRegion.region.idT.localName.rune)) {
                 initialKnownsWithoutDefaultRegion :+
                   InitialKnown(
                     RuneUsage(callRange.head, contextRegion.region.idT.localName.rune),
                     contextRegion.region)
               } else {
                  initialKnownsWithoutDefaultRegion
               }

              // We only want to solve the template arg runes
              inferCompiler.solveForResolving(
                InferEnv(callingEnv, callRange, callLocation, declaringEnv, contextRegion),
                coutputs,
                rulesWithoutRuneParentEnvLookups,
                explicitTemplateArgRuneToType ++ runeAToType,
                callRange,
                callLocation,
                initialKnowns,
                Vector()) match {
                case (Err(e)) => {
                  Err(FindFunctionResolveFailure(e))
                }
                case (Ok(CompleteResolveSolve(explicitRuneSToTemplata, _))) => {
                  val explicitlySpecifiedTemplateArgTemplatas =
                    explicitTemplateArgRunesS.map(explicitRuneSToTemplata)

                  if (ft.function.isLambda()) {
                    // We pass in our env because the callee needs to see functions declared here, see CSSNCE.
                    functionCompiler.evaluateTemplatedFunctionFromCallForPrototype(
                      coutputs, callingEnv, callRange, callLocation, ft, explicitlySpecifiedTemplateArgTemplatas.toVector, contextRegion, args) match {
                      case (EvaluateFunctionFailure(reason)) => Err(CouldntEvaluateTemplateError(reason))
                      case (EvaluateFunctionSuccess(prototype, conclusions, _)) => {
                              paramsMatch(coutputs, callingEnv, callRange, callLocation, args, prototype.prototype.paramTypes, exact) match {
                          case Err(rejectionReason) => Err(rejectionReason)
                          case Ok(()) => {
                            vassert(coutputs.getInstantiationBounds(prototype.prototype.id).nonEmpty)
                            vregionmut() // check regions?
                            Ok(AttemptedCandidate(vimpl(), vimpl(), prototype.prototype))
                          }
                        }
                      }
                    }
                  } else {
                    def makeNewDefaultRegion(newHeight: Int): RegionT = {
                      // The callee is going to reinterpret everything as additive/pure, so we
                      // need to make a new region for it to use as its default region.
                      RegionT(
                        PlaceholderTemplataT(
                          callingEnv.denizenId
                            .addStep(
                              interner.intern(RegionPlaceholderNameT(
                                -1,
                                CallRegionRuneS(callLocation),
                                Some(newHeight),
                                // All context regions are readwrite
                                ReadWriteRegionS))),
                          RegionTemplataType()))
                    }

                    // (This is all for pure merging)
                    //
                    //// Per PMHBRS, we need to do some pure merging logic before the actual solving,
                    //// because the solver isn't smart enough to know when it can merge two regions.
                    //val paramRegionRuneToArgRegion =
                    //  mutable.HashMap[IRuneS, Set[ITemplataT[RegionTemplataType]]]()
                    //args.zip(function.params).foreach({ case (arg, param) =>
                    //  val existingSet =
                    //    paramRegionRuneToArgRegion.getOrElse(param.outerRegionRune, Set())
                    //  paramRegionRuneToArgRegion.put(
                    //    param.outerRegionRune, existingSet + arg.region)
                    //})
                    //// As a temporary solution, if any regions need to be merged, we'll merge *all*
                    //// regions, see PMHBRS.
                    //val anyMerging = paramRegionRuneToArgRegion.values.exists(_.size > 1)
                    //
                    //val perhapsMergedArgCoords =
                    //  if (anyMerging) {
                    //    // Recall the callee's context region is at callingEnv.currentHeight + 1, so
                    //    // it's fine to have a new region at callingEnv.currentHeight.
                    //    val mergedRegionHeight = callingEnv.currentHeight
                    //    val mergedRegion =
                    //      PlaceholderTemplataT(
                    //        callingEnv.denizenId
                    //          .addStep(
                    //            interner.intern(RegionPlaceholderNameT(
                    //              -1,
                    //              CallPureMergeRegionRuneS(callLocation),
                    //              Some(mergedRegionHeight),
                    //              ImmutableRegionS))),
                    //        RegionTemplataType())
                    //    paramRegionRuneToArgRegion
                    //
                    //    args.map({ case CoordT(ownership, region, kind) =>
                    //      ownership match {
                    //        case BorrowT => // good
                    //        case ShareT => // good
                    //        case _ => vimpl()
                    //      }
                    //      kind match {
                    //        case IntT(_) | BoolT() | StrT() | FloatT() | VoidT() | NeverT(_) =>
                    //        case StructTT(structName) => {
                    //          if (structName.localName.templateArgs.nonEmpty) {
                    //            vimpl()
                    //          }
                    //        }
                    //
                    //      }
                    //      CoordT(ownership, mergedRegion, kind)
                    //    })
                    //  } else {
                    //
                    //  }

                    val (calleeContextRegion, isPure, newMaybeNearestPureHeight, newMaybeNearestAdditiveHeight) =
                      if (ft.function.attributes.collectFirst({ case PureS => }).nonEmpty) {
                        val newHeight = callingEnv.currentHeight + 1
                        // The callee is going to reinterpret everything as pure, so we
                        // need to make a new region for it to use as its default region.
                        (makeNewDefaultRegion(newHeight), true, Some(newHeight), callingEnv.additiveHeight)
                      } else if (ft.function.attributes.collectFirst({ case AdditiveS => }).nonEmpty) {
                        val newHeight = callingEnv.currentHeight + 1
                        // The callee is going to reinterpret everything as pure, so we
                        // need to make a new region for it to use as its default region.
                        (makeNewDefaultRegion(newHeight), false, callingEnv.pureHeight, Some(newHeight))
                      } else {
                        (contextRegion, false, callingEnv.pureHeight, callingEnv.additiveHeight)
                      }

                    val perhapsTransmigratedArgs =
                      if (contextRegion == calleeContextRegion) {
                        args
                      } else {
                        args.map({
                          case x if x.kind.isPrimitive => {
                            x.copy(region = calleeContextRegion)
                          }
                          case other => other
                        })
                      }

                    // We pass in our env because the callee needs to see functions declared here, see CSSNCE.
                    functionCompiler.evaluateGenericLightFunctionFromCallForPrototype(
                      coutputs, callRange, callLocation, callingEnv, ft, explicitlySpecifiedTemplateArgTemplatas.toVector, calleeContextRegion, perhapsTransmigratedArgs) match {
                      case (ResolveFunctionFailure(reason)) => Err(FindFunctionResolveFailure(reason))
                      case (ResolveFunctionSuccess(prototype, conclusions)) => {
                        paramsMatch(coutputs, callingEnv, callRange, callLocation, perhapsTransmigratedArgs, prototype.prototype.paramTypes, exact) match {
                          case Err(rejectionReason) => Err(rejectionReason)
                          case Ok(()) => {
                            vassert(coutputs.getInstantiationBounds(prototype.prototype.id).nonEmpty)
                            checkRegions(ft, newMaybeNearestPureHeight, newMaybeNearestAdditiveHeight, conclusions) match {
                              case Err(e) => return Err(e)
                              case Ok(()) =>
                            }
                            Ok(AttemptedCandidate(isPure, Some(calleeContextRegion), prototype.prototype))
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      case HeaderCalleeCandidate(header) => {
        vregionmut() // what if its a pure call

        paramsMatch(coutputs, callingEnv, callRange, callLocation, args, header.paramTypes, exact) match {
          case Ok(_) => {
            Ok(AttemptedCandidate(false, None, header.toPrototype))
          }
          case Err(fff) => Err(fff)
        }
      }
      case PrototypeTemplataCalleeCandidate(prototype) => {
        vregionmut() // what if its a pure call

        // We get here if we're considering a function that's being passed in as a bound.
        vcurious(prototype.id.localName.templateArgs.isEmpty)
        val substituter =
          TemplataCompiler.getPlaceholderSubstituter(
            opts.globalOptions.sanityCheck,
            interner, keywords,
            callingEnv.denizenTemplateId,
            prototype.id,
            // These types are phrased in terms of the calling denizen already, so we can grab their
            // bounds.
            InheritBoundsFromTypeItself)
        val params = prototype.id.localName.parameters.map(paramType => {
          substituter.substituteForCoord(coutputs, paramType)
        })
        paramsMatch(coutputs, callingEnv, callRange, callLocation, args, params, exact) match {
          case Ok(_) => {
            // This can be for example:
            //   func bork<T>(a T) where func drop(T)void {
            //     drop(a);
            //   }
            // We're calling a function that came from a bound.
            // Function bounds (like the `func drop(T)void` don't have bounds themselves)
            // so we just supply an empty map here.
            val bounds = Map[IRuneS, PrototypeTemplataT[IFunctionNameT]]()

            vassert(coutputs.getInstantiationBounds(prototype.id).nonEmpty)
            // Supplying false here because we don't have any pure bounds yet.
            Ok(AttemptedCandidate(vregionmut(false), None, prototype))
          }
          case Err(fff) => Err(fff)
        }
      }
    }
  }

  private def getRegionMutability(
    maybeNearestPureHeight: Option[Int],
    maybeNearestAdditiveHeight: Option[Int],
    region: RegionPlaceholderNameT):
  IRegionMutabilityS = {
    (region.height, maybeNearestPureHeight) match {
      case (None, None) => {
        // region height None means it was handed in and has an unknown height, see RGPPHASZ.
        // maybeNearestPureHeight means there was no pure block, and the function isn't pure.
        // Continue on.
      }
      case (Some(height), None) => {
        // This region was introduced during the function.
        // However, there's no pure block, so we can't yet conclude its immutable.
        // Continue on.
      }
      case (None, Some(nearestPureHeight)) => {
        // region height None means it was handed in and has an unknown height, see RGPPHASZ.
        // There was a pure block since then (or the function is pure) so we know it's immutable.
        return ImmutableRegionS
      }
      case (Some(height), Some(nearestPureHeight)) => {
        // This region was introduced during the function.
        // There was a pure block since then (or the function is pure).
        // If the region was introduced before the pure, then it's immutable.
        if (height < nearestPureHeight) {
          return ImmutableRegionS
        } else {
          // Otherwise, we can't conclude it's immutable yet.
        }
      }
    }

    (region.height, maybeNearestAdditiveHeight) match {
      case (None, None) => {
        // region height None means it was handed in and has an unknown height, see RGPPHASZ.
        // maybeNearestAdditiveHeight means there was no additive block, and the function isn't additive.
        // Continue on.
      }
      case (Some(height), None) => {
        // This region was introduced during the function.
        // However, there's no additive block, so we can't yet conclude its additive.
        // Continue on.
      }
      case (None, Some(nearestAdditiveHeight)) => {
        // region height None means it was handed in and has an unknown height, see RGPPHASZ.
        // There was a additive block since then (or the function is additive) so we know it's additive.
        return AdditiveRegionS
      }
      case (Some(height), Some(nearestAdditiveHeight)) => {
        // This region was introduced during the function.
        // There was a additive block since then (or the function is additive).
        // If the region was introduced before the additive, then it's additive.
        if (height < nearestAdditiveHeight) {
          return AdditiveRegionS
        } else {
          // Otherwise, we can't conclude it's additive yet.
        }
      }
    }

    // If we get here, then fall back to the region's original mutability.
    region.originalMutability
  }

  private def checkRegions(
    ft: FunctionTemplataT,
    maybeNearestPureHeight: Option[Int],
    maybeNearestAdditiveHeight: Option[Int],
    conclusions: Map[IRuneS, ITemplataT[ITemplataType]]):
  Result[Unit, IFindFunctionFailureReason] = {
    // The only place we can specify a region's mutability in the receiving function
    // is in the generic parameter declaration. So all we need to do is loop
    // through the generic parameter declarations and make sure the region we
    // give for them has the right mutability.
    ft.function.genericParameters.foreach(genericParam => {
      genericParam.tyype match {
        case RegionGenericParameterTypeS(calleeMutability) => {
          calleeMutability match {
            case ReadOnlyRegionS => {
              // Do nothing, it can receive any region mutability
            }
            case ReadWriteRegionS | AdditiveRegionS | ImmutableRegionS => {
              conclusions.get(genericParam.rune.rune) match {
                case Some(PlaceholderTemplataT(IdT(_, _, argRegion @ RegionPlaceholderNameT(index, rune, maybeArgRegionHeight, argRegionOriginalMutability)), RegionTemplataType())) => {
                  val argRegionMutability =
                    getRegionMutability(
                      maybeNearestPureHeight, maybeNearestAdditiveHeight, argRegion)

                  val compatible =
                    (argRegionMutability, calleeMutability) match {
                      case (ReadWriteRegionS, ReadWriteRegionS) => true
                      case (ReadWriteRegionS, AdditiveRegionS) => true
                      case (ReadWriteRegionS, ReadOnlyRegionS) => true
                      case (ReadWriteRegionS, ImmutableRegionS) => false
                      case (AdditiveRegionS, ReadWriteRegionS) => false
                      case (AdditiveRegionS, AdditiveRegionS) => true
                      case (AdditiveRegionS, ReadOnlyRegionS) => true
                      case (AdditiveRegionS, ImmutableRegionS) => false
                      case (ReadOnlyRegionS, ReadWriteRegionS) => false
                      case (ReadOnlyRegionS, AdditiveRegionS) => false
                      case (ReadOnlyRegionS, ReadOnlyRegionS) => true
                      case (ReadOnlyRegionS, ImmutableRegionS) => false
                      case (ImmutableRegionS, ReadWriteRegionS) => false
                      case (ImmutableRegionS, AdditiveRegionS) => false
                      case (ImmutableRegionS, ReadOnlyRegionS) => true
                      case (ImmutableRegionS, ImmutableRegionS) => true
                    }
                  if (!compatible) {
                    // DO NOT SUBMIT test this
                    return Err(SpecificParamRegionDoesntMatch(genericParam.rune.rune, argRegionMutability, calleeMutability))
                  }
                }
                case other => vwat(other)
              }
            }
          }
        }
        case _ =>
      }
    })
    Ok(())
  }

  // Gets all the environments for all the arguments.
  private def getParamEnvironments(coutputs: CompilerOutputs, range: List[RangeS], paramFilters: Vector[CoordT]):
  Vector[IInDenizenEnvironmentT] = {
    paramFilters.flatMap({ case tyype =>
      (tyype.kind match {
        case sr @ StructTT(_) => Vector(coutputs.getOuterEnvForType(range, TemplataCompiler.getStructTemplate(sr.id)))
        case ir @ InterfaceTT(_) => Vector(coutputs.getOuterEnvForType(range, TemplataCompiler.getInterfaceTemplate(ir.id)))
        case KindPlaceholderT(id) => Vector(coutputs.getOuterEnvForType(range, TemplataCompiler.getPlaceholderTemplate(id)))
        case _ => Vector.empty
      })
    })
  }

  // Checks to see if there's a function that *could*
  // exist that takes in these parameter types, and returns what the signature *would* look like.
  // Only considers when arguments match exactly.
  // If given something in maybeSuperInterfaceRef2, it will search for a function that
  // overrides that interfaceTT in that position. If we ever support multimethods we
  // might need to take a list of these, same length as the arg types... or combine
  // them somehow.
  def findPotentialFunction(
    env: IInDenizenEnvironmentT,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Vector[IRuneS],
    contextRegion: RegionT,
    args: Vector[CoordT],
    extraEnvsToLookIn: Vector[IInDenizenEnvironmentT],
    exact: Boolean):
  Result[AttemptedCandidate, FindFunctionFailure] = {
    // This is here for debugging, so when we dont find something we can see what envs we searched
    val searchedEnvs = new Accumulator[SearchedEnvironment]()
    val undedupedCandidates = new Accumulator[ICalleeCandidate]()
    getCandidateBanners(
      env, coutputs, callRange, functionName, args, extraEnvsToLookIn, searchedEnvs, undedupedCandidates)
    val candidates = undedupedCandidates.buildArray().distinct
    val attempted =
      candidates.map(candidate => {
        attemptCandidateBanner(
          env, coutputs, callRange, callLocation, explicitTemplateArgRulesS,
          explicitTemplateArgRunesS, contextRegion, args, candidate, exact)
          .mapError(e => (candidate -> e))
      })
    val (successes, failedToReason) = Result.split(attempted)

    if (successes.isEmpty) {
      Err(FindFunctionFailure(functionName, args, failedToReason))
    } else if (successes.size == 1) {
      Ok(successes.head)
    } else {
      val (best, outscoreReasonByBanner) =
        narrowDownCallableOverloads(coutputs, env, callRange, callLocation, successes, args)
      Ok(best)
    }
  }

  // Returns either:
  // - None if banners incompatible
  // - Some(param to needs-conversion)
  private def getBannerParamScores(
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    candidate: PrototypeT[IFunctionNameT],
    argTypes: Vector[CoordT]):
  (Option[Vector[Boolean]]) = {
    val initial: Option[Vector[Boolean]] = Some(Vector())
    val result =
      candidate.paramTypes.zip(argTypes)
        .foldLeft(initial)({
          case (None, _) => None
          case (Some(previous), (paramType, argType)) => {
            if (argType == paramType) {
              Some(previous :+ false)
            } else {
            if (templataCompiler.isTypeConvertible(coutputs, callingEnv, parentRanges, callLocation, argType, paramType)) {
                Some(previous :+ true)
              } else {
                None
              }
            }
          }
        })
    result match {
      case Some(a) => vassert(a.length == argTypes.length)
      case None =>
    }
    result
  }

  private def narrowDownCallableOverloads(
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT,
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    unfilteredBanners: Iterable[AttemptedCandidate],
    argTypes: Vector[CoordT]):
  (
    AttemptedCandidate,
    // Rejection reason by banner
    Map[AttemptedCandidate, IFindFunctionFailureReason]) = {

    // Sometimes a banner might come from many different environments (remember,
    // when we do a call, we look in the environments of all the arguments' types).
    // Here we weed out these duplicates.
    val dedupedBanners =
//      unfilteredBanners.foldLeft(Vector[IValidCalleeCandidate]())({
//        case (potentialBannerByBannerSoFar, currentPotentialBanner) => {
//          if (potentialBannerByBannerSoFar.exists(_.range == currentPotentialBanner.range)) {
//            potentialBannerByBannerSoFar
//          } else {
//            potentialBannerByBannerSoFar :+ currentPotentialBanner
//          }
//        }
//      })
        unfilteredBanners.toVector.distinct

    // If there are multiple overloads with the same exact parameter list,
    // then get rid of the templated ones; ordinary ones get priority.
    val banners =
      dedupedBanners.groupBy(_.prototype.paramTypes).values.flatMap({ potentialBannersWithSameParamTypes =>
        val ordinaryBanners =
          potentialBannersWithSameParamTypes
        if (ordinaryBanners.isEmpty) {
          // No ordinary banners, so include all the templated ones
          potentialBannersWithSameParamTypes
        } else {
          // There are some ordinary banners, so only consider the ordinary banners
          ordinaryBanners
        }
      }).toVector

    val bannerIndexToScore =
      banners.map(banner => {
        vassertSome(getBannerParamScores(coutputs, callingEnv, callRange, callLocation, banner.prototype, argTypes))
      })

    // For any given parameter:
    // - If all candidates require a conversion, keep going
    //   (This might be a mistake, should we throw an error instead?)
    // - If no candidates require a conversion, keep going
    // - If some candidates require a conversion, disqualify those candidates

    val paramIndexToSurvivingBannerIndices =
      argTypes.indices.map(paramIndex => {
        val bannerIndexToRequiresConversion =
          bannerIndexToScore.zipWithIndex.map({
            case (paramIndexToScore, bannerIndex) => paramIndexToScore(paramIndex)
          })
        if (bannerIndexToRequiresConversion.forall(_ == true)) {
          // vfail("All candidates require conversion for param " + paramIndex)
          bannerIndexToScore.indices
        } else if (bannerIndexToRequiresConversion.forall(_ == false)) {
          bannerIndexToScore.indices
        } else {
          val survivingBannerIndices =
            bannerIndexToRequiresConversion.zipWithIndex.filter(_._1).map(_._2)
          survivingBannerIndices
        }
      })
    // Now, each parameter knows what candidates it disqualifies.
    // See if there's exactly one candidate that all parameters agree on.
    val survivingBannerIndices =
      paramIndexToSurvivingBannerIndices.foldLeft(bannerIndexToScore.indices.toVector)({
        case (a, b) => a.intersect(b)
      })

    val (normalIndicesAndCandidates, boundIndicesAndCandidates) =
      survivingBannerIndices
          .map(i => i -> banners(i))
          .foldLeft((List[(Int, PrototypeT[IFunctionNameT])](), List[(Int, PrototypeT[FunctionBoundNameT])]()))({
            case ((normalCandidates, boundCandidates), (index, thisCandidate)) => {
              thisCandidate.prototype match {
                case PrototypeT(IdT(packageCoord, initSteps, FunctionBoundNameT(tn, firstTemplateArgs, firstParameters)), firstReturnType) => {
                  val thisBoundCandidate = PrototypeT(IdT(packageCoord, initSteps, FunctionBoundNameT(tn, firstTemplateArgs, firstParameters)), firstReturnType)
                  (normalCandidates, (index -> thisBoundCandidate) :: boundCandidates)
                }
                case other => {
                  ((index -> other) :: normalCandidates, boundCandidates)
            }
          }
      }
          })

    val finalBannerIndex =
      if (normalIndicesAndCandidates.size > 1) {
        val duplicateBanners = normalIndicesAndCandidates.map(_._2)
        throw CompileErrorExceptionT(
          CouldntNarrowDownCandidates(
            callRange,
            vimpl()))
        //            duplicateBanners.map(_.range.getOrElse(RangeS.internal(interner, -296729)))))
      } else if (normalIndicesAndCandidates.size == 1) {
        normalIndicesAndCandidates.head._1
      } else if (boundIndicesAndCandidates.nonEmpty) {
        val sortedByNameLength = boundIndicesAndCandidates.sortBy(_._2.id.steps.length)
        val (shortestCandidateIndex, shortestCandidate) = sortedByNameLength.head
        sortedByNameLength.tail.foreach(otherCandidate => {
          vassert(otherCandidate._2.id.initSteps.startsWith(shortestCandidate.id.initSteps))
//          val duplicateBanners = normalIndicesAndCandidates.map(_._2)
//          throw CompileErrorExceptionT(
//            CouldntNarrowDownCandidates(
//              callRange,
//              vimpl()))
        })
        shortestCandidateIndex
      } else {
        vfail("No candidate is a clear winner!")
      }

    val rejectedBanners =
      banners.zipWithIndex.filter(_._2 != finalBannerIndex).map(_._1)
    val rejectionReasonByBanner =
      rejectedBanners.map((_, Outscored())).toMap

    (banners(finalBannerIndex), rejectionReasonByBanner)
  }

//  def stampPotentialFunctionForBanner(
//    callingEnv: IDenizenEnvironmentBoxT,
//    coutputs: CompilerOutputs,
//    callRange: List[RangeS],
//    callLocation: LocationInDenizen,
//    potentialBanner: PrototypeT[IFunctionNameT],
//    contextRegion: RegionT,
//    verifyConclusions: Boolean):
//  (PrototypeTemplataT[IFunctionNameT]) = {
//    potentialBanner match {
////      case ValidCalleeCandidate(banner, _, ft @ FunctionTemplataT(_, _)) => {
//////        if (ft.function.isTemplate) {
////          val (EvaluateFunctionSuccess(successBanner, conclusions, _)) =
////            functionCompiler.evaluateTemplatedLightFunctionFromCallForPrototype(
////              coutputs, callingEnv, callRange, callLocation, ft, Vector.empty, contextRegion, banner.paramTypes);
////          successBanner
//////        } else {
//////          functionCompiler.evaluateOrdinaryFunctionFromNonCallForBanner(
//////            coutputs, callRange, ft, verifyConclusions)
//////        }
////      }
//      case ValidHeaderCalleeCandidate(header) => {
//        vassert(coutputs.getInstantiationBounds(header.toPrototype.id).nonEmpty)
//        PrototypeTemplataT(header.toPrototype)
//      }
//        }
//  }

//  private def stampPotentialFunctionForPrototype(
//    coutputs: CompilerOutputs,
//    callingEnv: IInDenizenEnvironmentT, // See CSSNCE
//    callRange: List[RangeS],
//    callLocation: LocationInDenizen,
//    potentialBanner: PrototypeT[IFunctionNameT],
//    contextRegion: RegionT,
//    args: Vector[CoordT]):
//  StampFunctionSuccess = {
//    potentialBanner match {
////      case ValidCalleeCandidate(header, templateArgs, ft @ FunctionTemplataT(_, _)) => {
////        if (ft.function.isLambda()) {
//////          if (ft.function.isTemplate) {
////            functionCompiler.evaluateTemplatedFunctionFromCallForPrototype(
////                coutputs,callRange, callLocation, callingEnv, ft, templateArgs, contextRegion, args) match {
////              case EvaluateFunctionSuccess(prototype, inferences, _) => StampFunctionSuccess(prototype, inferences)
////              case (eff@EvaluateFunctionFailure(_)) => vfail(eff.toString)
////            }
//////          } else {
//////            // debt: look into making FunctionCompiler's methods accept function templatas
//////            // so we dont pass in the wrong environment again
//////            functionCompiler.evaluateOrdinaryFunctionFromCallForPrototype(
//////              coutputs, callingEnv, callRange, ft)
//////          }
////        } else {
////          functionCompiler.evaluateGenericLightFunctionFromCallForPrototype(
////            coutputs, callRange, callLocation, callingEnv, ft, templateArgs, contextRegion, args) match {
////            case ResolveFunctionSuccess(prototype, inferences) => {
////              StampFunctionSuccess(prototype, inferences)
////            }
////            case (ResolveFunctionFailure(fffr)) => {
////              throw CompileErrorExceptionT(TypingPassResolvingError(callRange, fffr))
////            }
////          }
////        }
////      }
//      case ValidHeaderCalleeCandidate(header) => {
////        val declarationRange = vassertSome(header.maybeOriginFunctionTemplata).function.range
//        vassert(coutputs.getInstantiationBounds(header.toPrototype.id).nonEmpty)
//        StampFunctionSuccess(PrototypeTemplataT(header.toPrototype), Map())
//      }
//      case ValidPrototypeTemplataCalleeCandidate(prototype) => {
//        vassert(coutputs.getInstantiationBounds(prototype.prototype.id).nonEmpty)
//        StampFunctionSuccess(prototype, Map())
//          }
//    }
//  }

  def getArrayGeneratorPrototype(
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT,
    range: List[RangeS],
    callLocation: LocationInDenizen,
    callableTE: ReferenceExpressionTE,
    contextRegion: RegionT):
  PrototypeT[IFunctionNameT] = {
    val funcName = interner.intern(CodeNameS(keywords.underscoresCall))
    val paramFilters =
      Vector(
        callableTE.result.underlyingCoord,
        CoordT(ShareT, vimpl(), IntT.i32))
      findFunction(
        callingEnv, coutputs, range, callLocation, funcName, Vector.empty, Vector.empty, contextRegion,
        paramFilters, Vector.empty, false) match {
        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
        case Ok(x) => x.prototype
      }
  }

  def getArrayConsumerPrototype(
    coutputs: CompilerOutputs,
    fate: FunctionEnvironmentBoxT,
    range: List[RangeS],
    callLocation: LocationInDenizen,
    callableTE: ReferenceExpressionTE,
    elementType: CoordT,
    contextRegion: RegionT):
  PrototypeT[IFunctionNameT] = {
    val funcName = interner.intern(CodeNameS(keywords.underscoresCall))
    val paramFilters =
      Vector(
        callableTE.result.underlyingCoord,
        elementType)
    findFunction(
      fate.snapshot, coutputs, range, callLocation, funcName, Vector.empty, Vector.empty, contextRegion, paramFilters, Vector.empty, false) match {
      case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
      case Ok(x) => x.prototype
    }
  }
}
