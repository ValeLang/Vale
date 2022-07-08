package dev.vale.typing

import dev.vale.{Err, Interner, Keywords, Ok, Profiler, RangeS, Result, vassertSome, vcurious, vfail, vpass}
import dev.vale.postparsing.{CodeNameS, FunctionTemplataType, IImpreciseNameS, IRuneS, PostParserErrorHumanizer, RuneNameS, RuneTypeSolveError, RuneTypeSolver, TemplateTemplataType}
import dev.vale.postparsing.rules.{IRulexSR, RuneParentEnvLookupSR}
import dev.vale.solver.IIncompleteOrFailedSolve
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.function.FunctionCompiler
import dev.vale.typing.infer.ITypingPassSolverError
import dev.vale.typing.types.{CoordT, IntT, InterfaceTT, OverloadSetT, ParamFilter, ShareT, StructTT}
import dev.vale.highertyping._
import dev.vale.postparsing.PostParserErrorHumanizer
import dev.vale.postparsing.rules.RuneParentEnvLookupSR
import dev.vale.solver.FailedSolve
import OverloadResolver.{Outscored, RuleTypeSolveFailure, SpecificParamDoesntMatchExactly, SpecificParamDoesntSend}
import dev.vale.typing.ast.{AbstractT, FunctionBannerT, FunctionCalleeCandidate, HeaderCalleeCandidate, ICalleeCandidate, IValidCalleeCandidate, ParameterT, PrototypeT, ReferenceExpressionTE, ValidCalleeCandidate, ValidHeaderCalleeCandidate}
import dev.vale.typing.env.{ExpressionLookupContext, FunctionEnvironmentBox, IEnvironment, IEnvironmentBox, TemplataLookupContext}
import dev.vale.typing.templata.{ExternFunctionTemplata, FunctionTemplata, ITemplata, KindTemplata, PrototypeTemplata}
import dev.vale.typing.ast._
//import dev.vale.astronomer.ruletyper.{IRuleTyperEvaluatorDelegate, RuleTyperEvaluator, RuleTyperSolveFailure, RuleTyperSolveSuccess}
//import dev.vale.postparsing.rules.{EqualsSR, TemplexSR, TypedSR}
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.postparsing.ExplicitTemplateArgRuneS
import OverloadResolver.{IFindFunctionFailureReason, InferFailure, FindFunctionFailure, SpecificParamVirtualityDoesntMatch, WrongNumberOfArguments, WrongNumberOfTemplateArguments}
import dev.vale.typing.env._
import FunctionCompiler.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
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
  case class SpecificParamDoesntSend(index: Int, paramFilter: ParamFilter, parameter: CoordT) extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
  case class SpecificParamDoesntMatchExactly(index: Int, paramFilter: ParamFilter, parameter: CoordT) extends IFindFunctionFailureReason {
    override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
    vpass()
  }
  case class SpecificParamVirtualityDoesntMatch(index: Int) extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
  case class Outscored() extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
  case class RuleTypeSolveFailure(reason: RuneTypeSolveError) extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
  case class InferFailure(reason: IIncompleteOrFailedSolve[IRulexSR, IRuneS, ITemplata, ITypingPassSolverError]) extends IFindFunctionFailureReason { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

  case class FindFunctionFailure(
    name: IImpreciseNameS,
    args: Vector[ParamFilter],
    // All the banners we rejected, and the reason why
    rejectedCalleeToReason: Iterable[(ICalleeCandidate, IFindFunctionFailureReason)]
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
    env: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
    args: Vector[ParamFilter],
    extraEnvsToLookIn: Vector[IEnvironment],
    exact: Boolean):
  Result[PrototypeT, FindFunctionFailure] = {
    Profiler.frame(() => {
      findPotentialFunction(
        env,
        coutputs,
        callRange,
        functionName,
        explicitTemplateArgRulesS,
        explicitTemplateArgRunesS,
        args,
        extraEnvsToLookIn,
        exact) match {
        case Err(e) => return Err(e)
        case Ok(potentialBanner) => {
          Ok(stampPotentialFunctionForPrototype(coutputs, callRange, potentialBanner, args))
        }
      }
    })
  }

  private def paramsMatch(
    coutputs: CompilerOutputs,
    desiredParams: Vector[ParamFilter],
    candidateParams: Vector[ParameterT],
    exact: Boolean):
  Result[Unit, IFindFunctionFailureReason] = {
    if (desiredParams.size != candidateParams.size) {
      return Err(WrongNumberOfArguments(desiredParams.size, candidateParams.size))
    }
    desiredParams.zip(candidateParams).zipWithIndex.foreach({
      case ((desiredParam, candidateParam), paramIndex) => {
        val ParamFilter(desiredOwnership, desiredKind, desiredMaybeVirtuality) = desiredParam
        val ParameterT(_, candidateMaybeVirtuality, candidateCoord @ CoordT(candidateOwnership, candidateKind)) = candidateParam

        if (exact) {
          if (desiredOwnership != candidateOwnership || desiredKind != candidateKind) {
            return Err(SpecificParamDoesntMatchExactly(paramIndex, desiredParam, candidateCoord))
          }
        } else {
          if (!templataCompiler.isOwnershipConvertible(desiredOwnership, candidateOwnership) ||
            !templataCompiler.isKindConvertible(coutputs, desiredKind, candidateKind)) {
            return Err(SpecificParamDoesntSend(paramIndex, desiredParam, candidateCoord))
          }
        }
        desiredMaybeVirtuality match {
          case None =>
          case desiredVirtuality => {
            if (desiredVirtuality != candidateMaybeVirtuality) {
              return Err(SpecificParamVirtualityDoesntMatch(paramIndex))
            }
          }
        }
      }
    })
    // Would have bailed out early if there was a false
    Ok(())
  }

  private def getCandidateBanners(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
    paramFilters: Vector[ParamFilter],
    extraEnvsToLookIn: Vector[IEnvironment],
    exact: Boolean):
  Vector[ICalleeCandidate] = {
    val candidates =
      findHayTemplatas(env, coutputs, functionName, paramFilters, extraEnvsToLookIn)
    candidates.flatMap({
      case KindTemplata(OverloadSetT(overloadsEnv, nameInOverloadsEnv)) => {
        getCandidateBanners(
          overloadsEnv, coutputs, callRange, nameInOverloadsEnv,
          explicitTemplateArgRulesS, explicitTemplateArgRunesS, paramFilters, Vector.empty, exact)
      }
      case KindTemplata(sr@StructTT(_)) => {
        val structEnv = coutputs.getEnvForKind(sr)
        getCandidateBanners(
          structEnv, coutputs, callRange, interner.intern(CodeNameS(keywords.CALL_FUNCTION_NAME)), explicitTemplateArgRulesS, explicitTemplateArgRunesS, paramFilters, Vector.empty, exact)
      }
      case KindTemplata(sr@InterfaceTT(_)) => {
        val interfaceEnv = coutputs.getEnvForKind(sr)
        getCandidateBanners(
          interfaceEnv, coutputs, callRange, interner.intern(CodeNameS(keywords.CALL_FUNCTION_NAME)), explicitTemplateArgRulesS, explicitTemplateArgRunesS, paramFilters, Vector.empty, exact)
      }
      case ExternFunctionTemplata(header) => {
        Vector(HeaderCalleeCandidate(header))
      }
      case PrototypeTemplata(prototype) => {
        val header = vassertSome(coutputs.lookupFunction(prototype.toSignature)).header
        Vector(HeaderCalleeCandidate(header))
      }
      case ft@FunctionTemplata(_, function) => {
        Vector(FunctionCalleeCandidate(ft))
      }
    })
  }

  private def attemptCandidateBanner(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
    paramFilters: Vector[ParamFilter],
    candidate: ICalleeCandidate,
    exact: Boolean):
  Result[IValidCalleeCandidate, IFindFunctionFailureReason] = {
    candidate match {
      case FunctionCalleeCandidate(ft@FunctionTemplata(_, function)) => {
        // See OFCBT.
        if (ft.function.isTemplate) {
          function.tyype match {
            case TemplateTemplataType(identifyingRuneTemplataTypes, FunctionTemplataType) => {
              if (explicitTemplateArgRunesS.size > identifyingRuneTemplataTypes.size) {
                throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Supplied more arguments than there are identifying runes!"))
              }

              // Now that we know what types are expected, we can FINALLY rule-type these explicitly
              // specified template args! (The rest of the rule-typing happened back in the astronomer,
              // this is the one time we delay it, see MDRTCUT).

              // There might be less explicitly specified template args than there are types, and that's
              // fine. Hopefully the rest will be figured out by the rule evaluator.
              val explicitTemplateArgRuneToType =
              explicitTemplateArgRunesS.zip(identifyingRuneTemplataTypes).toMap

              // And now that we know the types that are expected of these template arguments, we can
              // run these template argument templexes through the solver so it can evaluate them in
              // context of the current environment and spit out some templatas.
              runeTypeSolver.solve(
                opts.globalOptions.sanityCheck,
                opts.globalOptions.useOptimizedSolver,
                (nameS: IImpreciseNameS) => {
                  env.lookupNearestWithImpreciseName(nameS, Set(TemplataLookupContext)) match {
                    case Some(x) => x.tyype
                    case None => {
                      throw CompileErrorExceptionT(
                        RangedInternalErrorT(
                          callRange,
                          "Couldn't find a: " + PostParserErrorHumanizer.humanizeImpreciseName(nameS)))
                    }
                  }
                },
                callRange,
                false,
                explicitTemplateArgRulesS,
                explicitTemplateArgRunesS,
                true,
                explicitTemplateArgRuneToType) match {
                case Err(e@RuneTypeSolveError(_, _)) => {
                  Err(RuleTypeSolveFailure(e))
                }
                case Ok(runeTypeConclusions) => {
                  // rulesA is the equals rules, but rule typed. Now we'll run them through the solver to get
                  // some actual templatas.

                  // We preprocess out the rune parent env lookups, see MKRFA.
                  val (initialKnowns, rulesWithoutRuneParentEnvLookups) =
                    explicitTemplateArgRulesS.foldLeft((Vector[InitialKnown](), Vector[IRulexSR]()))({
                      case ((previousConclusions, remainingRules), RuneParentEnvLookupSR(_, rune)) => {
                        val templata =
                          vassertSome(
                            env.lookupNearestWithImpreciseName(
                              interner.intern(RuneNameS(rune.rune)), Set(TemplataLookupContext)))
                        val newConclusions = previousConclusions :+ InitialKnown(rune, templata)
                        (newConclusions, remainingRules)
                      }
                      case ((previousConclusions, remainingRules), rule) => {
                        (previousConclusions, remainingRules :+ rule)
                      }
                    })

                  // We only want to solve the template arg runes
                  inferCompiler.solveComplete(
                    env,
                    coutputs,
                    rulesWithoutRuneParentEnvLookups,
                    explicitTemplateArgRuneToType ++ runeTypeConclusions,
                    callRange,
                    initialKnowns,
                    Vector()) match {
                    case (Err(e)) => {
                      Err(InferFailure(e))
                    }
                    case (Ok(explicitRuneSToTemplata)) => {
                      val explicitlySpecifiedTemplateArgTemplatas = explicitTemplateArgRunesS.map(explicitRuneSToTemplata)

                      functionCompiler.evaluateTemplatedFunctionFromCallForBanner(
                        coutputs, callRange, ft, explicitlySpecifiedTemplateArgTemplatas.toVector, paramFilters) match {
                        case (EvaluateFunctionFailure(reason)) => Err(reason)
                        case (EvaluateFunctionSuccess(banner)) => {
                          paramsMatch(coutputs, paramFilters, banner.params, exact) match {
                            case Err(rejectionReason) => Err(rejectionReason)
                            case Ok(()) => {
                              Ok(ast.ValidCalleeCandidate(banner, ft))
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            case FunctionTemplataType => {
              // So it's not a template, but it's a template in context. We'll still need to
              // feed it into the inferer.
              functionCompiler.evaluateTemplatedFunctionFromCallForBanner(
                coutputs, callRange, ft, Vector.empty, paramFilters) match {
                case (EvaluateFunctionFailure(reason)) => {
                  Err(reason)
                }
                case (EvaluateFunctionSuccess(banner)) => {
                  paramsMatch(coutputs, paramFilters, banner.params, exact) match {
                    case Err(reason) => Err(reason)
                    case Ok(_) => {
                      Ok(ValidCalleeCandidate(banner, ft))
                    }
                  }
                }
              }
            }
          }
        } else {
          val banner = functionCompiler.evaluateOrdinaryFunctionFromNonCallForBanner(coutputs, callRange, ft)
          paramsMatch(coutputs, paramFilters, banner.params, exact) match {
            case Ok(_) => {
              Ok(ast.ValidCalleeCandidate(banner, ft))
            }
            case Err(reason) => Err(reason)
          }
        }
      }
      case HeaderCalleeCandidate(header) => {
        paramsMatch(coutputs, paramFilters, header.params, exact) match {
          case Ok(_) => {
            Ok(ValidHeaderCalleeCandidate(header))
          }
          case Err(fff) => Err(fff)
        }
      }
    }
  }

  // Gets all the environments for all the arguments.
  private def getParamEnvironments(coutputs: CompilerOutputs, paramFilters: Vector[ParamFilter]):
  Vector[IEnvironment] = {
    paramFilters.flatMap({ case ParamFilter(ownership, kind, virtuality) =>
      (kind match {
        case sr @ StructTT(_) => Vector(coutputs.getEnvForKind(sr))
        case ir @ InterfaceTT(_) => Vector(coutputs.getEnvForKind(ir))
        case _ => Vector.empty
      }) ++
        (virtuality match {
          case None => Vector.empty
          case Some(AbstractT()) => Vector.empty
//          case Some(OverrideT(ir)) => Vector(coutputs.getEnvForKind(ir))
        })
    })
  }

  // Looks in all the environments of the given arguments for something with the given name.
  private def findHayTemplatas(
      env: IEnvironment,
      coutputs: CompilerOutputs,
      impreciseName: IImpreciseNameS,
      paramFilters: Vector[ParamFilter],
      extraEnvsToLookIn: Vector[IEnvironment]):
  Vector[ITemplata] = {
    val environments = Vector(env) ++ getParamEnvironments(coutputs, paramFilters) ++ extraEnvsToLookIn
    val undeduped =
      environments.flatMap(_.lookupAllWithImpreciseName(impreciseName, Set(ExpressionLookupContext)))
    undeduped.distinct
  }

  // Checks to see if there's a function that *could*
  // exist that takes in these parameter types, and returns what the signature *would* look like.
  // Only considers when arguments match exactly.
  // If given something in maybeSuperInterfaceRef2, it will search for a function that
  // overrides that interfaceTT in that position. If we ever support multimethods we
  // might need to take a list of these, same length as the arg types... or combine
  // them somehow.
  def findPotentialFunction(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    callRange: RangeS,
    functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
    args: Vector[ParamFilter],
    extraEnvsToLookIn: Vector[IEnvironment],
    exact: Boolean):
  Result[IValidCalleeCandidate, FindFunctionFailure] = {
    val undedupedCandidates =
      getCandidateBanners(
        env, coutputs, callRange, functionName, explicitTemplateArgRulesS,
        explicitTemplateArgRunesS, args, extraEnvsToLookIn, exact)
    val candidates = undedupedCandidates.distinct
    val attempted =
      candidates.map(candidate => {
        attemptCandidateBanner(
          env, coutputs, callRange, explicitTemplateArgRulesS,
          explicitTemplateArgRunesS, args, candidate, exact)
          .mapError(e => (candidate -> e))
      })
    val (successes, failedToReason) = Result.split(attempted)

    if (successes.isEmpty) {
      Err(FindFunctionFailure(functionName, args, failedToReason))
    } else if (successes.size == 1) {
      Ok(successes.head)
    } else {
      val (best, outscoreReasonByBanner) =
        narrowDownCallableOverloads(coutputs, callRange, successes, args)
      Ok(best)
    }
  }

  // Returns either:
  // - None if banners incompatible
  // - Some(param to needs-conversion)
  private def getBannerParamScores(
    coutputs: CompilerOutputs,
    banner: IValidCalleeCandidate,
    argTypes: Vector[ParamFilter]):
  (Option[Vector[Boolean]]) = {
    val initial: Option[Vector[Boolean]] = Some(Vector())
    banner.banner.paramTypes.zip(argTypes)
      .foldLeft(initial)({
        case (None, _) => None
        case (Some(previous), (paramType, argType)) => {
          if (argType.ownership == paramType.ownership && argType.kind == paramType.kind) {
            Some(previous :+ false)
          } else {
            if (templataCompiler.matchesParamFilter(coutputs, argType, paramType)) {
              Some(previous :+ true)
            } else {
              None
            }
          }
        }
      })
  }

  private def narrowDownCallableOverloads(
      coutputs: CompilerOutputs,
      callRange: RangeS,
      unfilteredBanners: Iterable[IValidCalleeCandidate],
      argTypes: Vector[ParamFilter]):
  (
    IValidCalleeCandidate,
    // Rejection reason by banner
    Map[IValidCalleeCandidate, IFindFunctionFailureReason]) = {

    // Sometimes a banner might come from many different environments (remember,
    // when we do a call, we look in the environments of all the arguments' types).
    // Here we weed out these duplicates.
    val dedupedBanners =
      unfilteredBanners.foldLeft(Vector[IValidCalleeCandidate]())({
        case (potentialBannerByBannerSoFar, currentPotentialBanner) => {
          if (potentialBannerByBannerSoFar.exists(_.banner.same(currentPotentialBanner.banner))) {
            potentialBannerByBannerSoFar
          } else {
            potentialBannerByBannerSoFar :+ currentPotentialBanner
          }
        }
      })

    // If there are multiple overloads with the same exact parameter list,
    // then get rid of the templated ones; ordinary ones get priority.
    val banners =
      dedupedBanners.groupBy(_.banner.paramTypes).values.flatMap({ potentialBannersWithSameParamTypes =>
        val ordinaryBanners =
          potentialBannersWithSameParamTypes.filter({
            case ValidCalleeCandidate(_, function) => !function.function.isTemplate
            case ValidHeaderCalleeCandidate(_) => true
          })
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
        vassertSome(getBannerParamScores(coutputs, banner, argTypes))
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
    val survivingBannerIndex =
      if (survivingBannerIndices.size == 0) {
        // This can happen if the parameters don't agree who the best
        // candidates are.
        vfail("No candidate is a clear winner!")
      } else if (survivingBannerIndices.size == 1) {
        survivingBannerIndices.head
      } else {
        throw CompileErrorExceptionT(
          CouldntNarrowDownCandidates(
            callRange,
            survivingBannerIndices.map(banners)
              .map(_.banner.originFunction.map(_.range).getOrElse(RangeS.internal(interner, -296729)))))
      }

    val rejectedBanners =
      banners.zipWithIndex.filter(_._2 != survivingBannerIndex).map(_._1)
    val rejectionReasonByBanner =
      rejectedBanners.map((_, Outscored())).toMap

    (banners(survivingBannerIndex), rejectionReasonByBanner)
  }

  def stampPotentialFunctionForBanner(
      env: IEnvironmentBox,
      coutputs: CompilerOutputs,
      callRange: RangeS,
      potentialBanner: IValidCalleeCandidate):
  (FunctionBannerT) = {
    potentialBanner match {
      case ValidCalleeCandidate(signature, ft @ FunctionTemplata(_, _)) => {
        if (ft.function.isTemplate) {
          val (EvaluateFunctionSuccess(banner)) =
            functionCompiler.evaluateTemplatedLightFunctionFromCallForBanner(
              coutputs, callRange, ft, Vector.empty, signature.paramTypes.map(p => ParamFilter(p.ownership, p.kind, None)));
          (banner)
        } else {
          functionCompiler.evaluateOrdinaryFunctionFromNonCallForBanner(
            coutputs, callRange, ft)
        }
      }
      case ValidHeaderCalleeCandidate(header) => {
        (header.toBanner)
      }
    }
  }

  // The "for coutputs" thing is important, it means we don't care what the result is, we just
  // want to make sure it gets into the outputs.
  private def stampPotentialFunctionForPrototype(
      coutputs: CompilerOutputs,
      range: RangeS,
      potentialBanner: IValidCalleeCandidate,
      args: Vector[ParamFilter]):
  (PrototypeT) = {
    potentialBanner match {
      case ValidCalleeCandidate(signature, ft @ FunctionTemplata(_, _)) => {
        if (ft.function.isTemplate) {
          functionCompiler.evaluateTemplatedFunctionFromCallForPrototype(
              coutputs, range, ft, signature.fullName.last.templateArgs, args) match {
            case (EvaluateFunctionSuccess(prototype)) => (prototype)
            case (eff @ EvaluateFunctionFailure(_)) => vfail(eff.toString)
          }
        } else {
          // debt: look into making FunctionCompiler's methods accept function templatas
          // so we dont pass in the wrong environment again
          functionCompiler.evaluateOrdinaryFunctionFromNonCallForPrototype(
            coutputs, range, ft)
        }
      }
      case ValidHeaderCalleeCandidate(header) => {
        (header.toPrototype)
      }
    }
  }

  def getArrayGeneratorPrototype(
    coutputs: CompilerOutputs,
    fate: IEnvironment,
    range: RangeS,
    callableTE: ReferenceExpressionTE):
  PrototypeT = {
    val funcName = interner.intern(CodeNameS(keywords.CALL_FUNCTION_NAME))
    val paramFilters =
      Vector(
        ParamFilter(callableTE.result.underlyingReference.ownership, callableTE.result.underlyingReference.kind, None),
        ParamFilter(ShareT, IntT.i32, None))
      findFunction(
        fate, coutputs, range, funcName, Vector.empty, Array.empty,
        paramFilters, Vector.empty, false) match {
        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
        case Ok(x) => x
      }
  }

  def getArrayConsumerPrototype(
    coutputs: CompilerOutputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    callableTE: ReferenceExpressionTE,
    elementType: CoordT):
  PrototypeT = {
    val funcName = interner.intern(CodeNameS(keywords.CALL_FUNCTION_NAME))
    val paramFilters =
      Vector(
        ParamFilter(callableTE.result.underlyingReference.ownership, callableTE.result.underlyingReference.kind, None),
        ParamFilter(elementType.ownership, elementType.kind, None))
    findFunction(
      fate.snapshot, coutputs, range, funcName, Vector.empty, Array.empty, paramFilters, Vector.empty, false) match {
      case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(range, e))
      case Ok(x) => x
    }
  }
}
