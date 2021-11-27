package net.verdagon.vale.templar

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.{FunctionTemplataType, GlobalFunctionFamilyNameS, IImpreciseNameS, IRuneS, RuneNameS, RuneTypeSolveError, RuneTypeSolver, TemplateTemplataType}
import net.verdagon.vale.scout.rules.{EqualsSR, IRulexSR, RuneParentEnvLookupSR, RuneUsage}
import net.verdagon.vale.solver.{CompleteSolve, FailedSolve, IIncompleteOrFailedSolve}
import net.verdagon.vale.templar.OverloadTemplar.{Outscored, RuleTypeSolveFailure, SpecificParamDoesntMatchExactly, SpecificParamDoesntSend}
import net.verdagon.vale.templar.ast.{AbstractT, FunctionBannerT, FunctionCalleeCandidate, HeaderCalleeCandidate, ICalleeCandidate, IValidCalleeCandidate, OverrideT, ParameterT, PrototypeT, ReferenceExpressionTE, ValidCalleeCandidate, ValidHeaderCalleeCandidate}
import net.verdagon.vale.templar.infer.ITemplarSolverError
import net.verdagon.vale.templar.names.TemplataNamer
import net.verdagon.vale.{Err, Ok, RangeS, Result, vassertOne, vassertSome, vpass}
//import net.verdagon.vale.astronomer.ruletyper.{IRuleTyperEvaluatorDelegate, RuleTyperEvaluator, RuleTyperSolveFailure, RuleTyperSolveSuccess}
//import net.verdagon.vale.scout.rules.{EqualsSR, TemplexSR, TypedSR}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{CodeRuneS, CodeNameS, ExplicitTemplateArgRuneS, INameS}
import net.verdagon.vale.templar.OverloadTemplar.{IFindFunctionFailureReason, InferFailure, FindFunctionFailure, SpecificParamVirtualityDoesntMatch, WrongNumberOfArguments, WrongNumberOfTemplateArguments}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.function.FunctionTemplar
import net.verdagon.vale.templar.function.FunctionTemplar.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
//import net.verdagon.vale.templar.infer.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.{IProfiler, vassert, vcurious, vfail, vimpl}

import scala.collection.immutable.List

object OverloadTemplar {

  sealed trait IFindFunctionFailureReason
  case class WrongNumberOfArguments(supplied: Int, expected: Int) extends IFindFunctionFailureReason {
    vpass()

    override def hashCode(): Int = vcurious()
  }
  case class WrongNumberOfTemplateArguments(supplied: Int, expected: Int) extends IFindFunctionFailureReason { override def hashCode(): Int = vcurious() }
  case class SpecificParamDoesntSend(index: Int, argument: CoordT, parameter: CoordT) extends IFindFunctionFailureReason { override def hashCode(): Int = vcurious() }
  case class SpecificParamDoesntMatchExactly(index: Int, argument: CoordT, parameter: CoordT) extends IFindFunctionFailureReason {
    override def hashCode(): Int = vcurious()
    vpass()
  }
  case class SpecificParamVirtualityDoesntMatch(index: Int) extends IFindFunctionFailureReason { override def hashCode(): Int = vcurious() }
  case class Outscored() extends IFindFunctionFailureReason { override def hashCode(): Int = vcurious() }
  case class RuleTypeSolveFailure(reason: RuneTypeSolveError) extends IFindFunctionFailureReason { override def hashCode(): Int = vcurious() }
  case class InferFailure(reason: IIncompleteOrFailedSolve[IRulexSR, IRuneS, ITemplata, ITemplarSolverError]) extends IFindFunctionFailureReason { override def hashCode(): Int = vcurious() }

  case class FindFunctionFailure(
    name: IImpreciseNameS,
    args: Vector[ParamFilter],
    // All the banners we rejected, and the reason why
    rejectedCalleeToReason: Map[ICalleeCandidate, IFindFunctionFailureReason]
  ) {
    vpass()
    override def hashCode(): Int = vcurious()
  }
}

class OverloadTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
    templataTemplar: TemplataTemplar,
    inferTemplar: InferTemplar,
    functionTemplar: FunctionTemplar) {
  def findFunction(
    env: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
    args: Vector[ParamFilter],
    extraEnvsToLookIn: Vector[IEnvironment],
    exact: Boolean):
  PrototypeT = {
    profiler.newProfile("findFunctionForPrototype", "", () => {
      findPotentialFunction(
        env,
        temputs,
        callRange,
        functionName,
        explicitTemplateArgRulesS,
        explicitTemplateArgRunesS,
        args,
        extraEnvsToLookIn,
        exact) match {
        case Err(e) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, e))
        case Ok(potentialBanner) => {
          stampPotentialFunctionForPrototype(temputs, callRange, potentialBanner, args)
        }
      }
    })
  }

  private def paramsMatch(
    temputs: Temputs,
    desiredParams: Vector[ParamFilter],
    candidateParams: Vector[ParameterT],
    exact: Boolean):
  Result[Unit, IFindFunctionFailureReason] = {
    if (desiredParams.size != candidateParams.size) {
      return Err(WrongNumberOfArguments(desiredParams.size, candidateParams.size))
    }
    desiredParams.zip(candidateParams).zipWithIndex.foreach({
      case ((desiredParam, candidateParam), paramIndex) => {
        val ParamFilter(desiredTemplata, desiredMaybeVirtuality) = desiredParam
        val ParameterT(_, candidateMaybeVirtuality, candidateType) = candidateParam

        if (exact) {
          if (desiredTemplata != candidateType) {
            return Err(SpecificParamDoesntMatchExactly(paramIndex, desiredTemplata, candidateType))
          }
        } else {
          if (!templataTemplar.isTypeConvertible(temputs, desiredTemplata, candidateType)) {
            return Err(SpecificParamDoesntSend(paramIndex, desiredTemplata, candidateType))
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
    temputs: Temputs,
    callRange: RangeS,
    functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
    paramFilters: Vector[ParamFilter],
    extraEnvsToLookIn: Vector[IEnvironment],
    exact: Boolean):
  Vector[ICalleeCandidate] = {
    val candidates =
      findHayTemplatas(env, temputs, functionName, paramFilters, extraEnvsToLookIn)
    candidates.flatMap({
      case KindTemplata(OverloadSet(overloadsEnv, nameInOverloadsEnv, _)) => {
        getCandidateBanners(
          overloadsEnv, temputs, callRange, nameInOverloadsEnv,
          explicitTemplateArgRulesS, explicitTemplateArgRunesS, paramFilters, Vector.empty, exact)
      }
      case KindTemplata(sr@StructTT(_)) => {
        val structEnv = temputs.getEnvForKind(sr)
        getCandidateBanners(
          structEnv, temputs, callRange, CodeNameS(CallTemplar.CALL_FUNCTION_NAME), explicitTemplateArgRulesS, explicitTemplateArgRunesS, paramFilters, Vector.empty, exact)
      }
      case KindTemplata(sr@InterfaceTT(_)) => {
        val interfaceEnv = temputs.getEnvForKind(sr)
        getCandidateBanners(
          interfaceEnv, temputs, callRange, CodeNameS(CallTemplar.CALL_FUNCTION_NAME), explicitTemplateArgRulesS, explicitTemplateArgRunesS, paramFilters, Vector.empty, exact)
      }
      case ExternFunctionTemplata(header) => {
        Vector(HeaderCalleeCandidate(header))
      }
      case PrototypeTemplata(prototype) => {
        val header = vassertSome(temputs.lookupFunction(prototype.toSignature)).header
        Vector(HeaderCalleeCandidate(header))
      }
      case ft@FunctionTemplata(_, function) => {
        Vector(FunctionCalleeCandidate(ft))
      }
    })
  }

  private def attemptCandidateBanner(
    env: IEnvironment,
    temputs: Temputs,
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
              RuneTypeSolver.solve(
                opts.globalOptions.sanityCheck,
                opts.globalOptions.useOptimizedSolver,
                (nameS: IImpreciseNameS) => vassertOne(env.lookupNearestWithImpreciseName(profiler, nameS, Set(TemplataLookupContext))).tyype,
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
                              profiler, RuneNameS(rune.rune), Set(TemplataLookupContext)))
                        val newConclusions = previousConclusions :+ InitialKnown(rune, templata)
                        (newConclusions, remainingRules)
                      }
                      case ((previousConclusions, remainingRules), rule) => {
                        (previousConclusions, remainingRules :+ rule)
                      }
                    })

                  // We only want to solve the template arg runes
                  profiler.childFrame("solve template args", () => {
                    inferTemplar.solveComplete(
                      env,
                      temputs,
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

                        functionTemplar.evaluateTemplatedFunctionFromCallForBanner(
                          temputs, callRange, ft, explicitlySpecifiedTemplateArgTemplatas.toVector, paramFilters) match {
                          case (EvaluateFunctionFailure(reason)) => Err(reason)
                          case (EvaluateFunctionSuccess(banner)) => {
                            paramsMatch(temputs, paramFilters, banner.params, exact) match {
                              case Err(rejectionReason) => Err(rejectionReason)
                              case Ok(()) => Ok(ValidCalleeCandidate(banner, ft))
                            }
                          }
                        }
                      }
                    }
                  })
                }
              }
            }
            case FunctionTemplataType => {
              // So it's not a template, but it's a template in context. We'll still need to
              // feed it into the inferer.
              functionTemplar.evaluateTemplatedFunctionFromCallForBanner(
                temputs, callRange, ft, Vector.empty, paramFilters) match {
                case (EvaluateFunctionFailure(reason)) => {
                  Err(reason)
                }
                case (EvaluateFunctionSuccess(banner)) => {
                  paramsMatch(temputs, paramFilters, banner.params, exact) match {
                    case Err(reason) => Err(reason)
                    case Ok(_) => Ok(ast.ValidCalleeCandidate(banner, ft))
                  }
                }
              }
            }
          }
        } else {
          val banner = functionTemplar.evaluateOrdinaryFunctionFromNonCallForBanner(temputs, callRange, ft)
          paramsMatch(temputs, paramFilters, banner.params, exact) match {
            case Ok(_) => Ok(ast.ValidCalleeCandidate(banner, ft))
            case Err(reason) => Err(reason)
          }
        }
      }
      case HeaderCalleeCandidate(header) => {
        paramsMatch(temputs, paramFilters, header.params, exact) match {
          case Ok(_) => Ok(ValidHeaderCalleeCandidate(header))
          case Err(fff) => Err(fff)
        }
      }
    }
  }

  // Gets all the environments for all the arguments.
  private def getParamEnvironments(temputs: Temputs, paramFilters: Vector[ParamFilter]):
  Vector[IEnvironment] = {
    paramFilters.flatMap({ case ParamFilter(tyype, virtuality) =>
      (tyype.kind match {
        case sr @ StructTT(_) => Vector(temputs.getEnvForKind(sr))
        case ir @ InterfaceTT(_) => Vector(temputs.getEnvForKind(ir))
        case _ => Vector.empty
      }) ++
        (virtuality match {
          case None => Vector.empty
          case Some(AbstractT) => Vector.empty
          case Some(OverrideT(ir)) => Vector(temputs.getEnvForKind(ir))
        })
    })
  }

  // Looks in all the environments of the given arguments for something with the given name.
  private def findHayTemplatas(
      env: IEnvironment,
      temputs: Temputs,
      impreciseName: IImpreciseNameS,
      paramFilters: Vector[ParamFilter],
      extraEnvsToLookIn: Vector[IEnvironment]):
  Vector[ITemplata] = {
    val environments = Vector(env) ++ getParamEnvironments(temputs, paramFilters) ++ extraEnvsToLookIn
    environments
      .flatMap(_.lookupAllWithImpreciseName(profiler, impreciseName, Set(ExpressionLookupContext)))
      .distinct
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
      temputs: Temputs,
      callRange: RangeS,
      functionName: IImpreciseNameS,
    explicitTemplateArgRulesS: Vector[IRulexSR],
    explicitTemplateArgRunesS: Array[IRuneS],
      args: Vector[ParamFilter],
    extraEnvsToLookIn: Vector[IEnvironment],
      exact: Boolean):
  Result[IValidCalleeCandidate, FindFunctionFailure] = {
    val candidates =
      getCandidateBanners(
        env, temputs, callRange, functionName, explicitTemplateArgRulesS,
        explicitTemplateArgRunesS, args, extraEnvsToLookIn, exact)
    val attempted =
      candidates.map(candidate => {
        attemptCandidateBanner(
          env, temputs, callRange, explicitTemplateArgRulesS,
          explicitTemplateArgRunesS, args, candidate, exact)
          .mapError(e => (candidate -> e))
      })
    val (successes, failedToReasonUnmerged) = Result.split(attempted)
    val failedToReason = failedToReasonUnmerged.toMap

    if (successes.isEmpty) {
      Err(FindFunctionFailure(functionName, args, failedToReason))
    } else if (successes.size == 1) {
      Ok(successes.head)
    } else {
      val (best, outscoreReasonByBanner) =
        narrowDownCallableOverloads(temputs, callRange, successes.toSet, args.map(_.tyype))
      Ok(best)
    }
  }

  private def getBannerParamScores(
    temputs: Temputs,
    banner: IValidCalleeCandidate,
    argTypes: Vector[CoordT]):
  (Vector[TypeDistance]) = {
    banner.banner.paramTypes.zip(argTypes)
      .foldLeft((Vector[TypeDistance]()))({
        case ((previousParamsScores), (paramType, argType)) => {
          templataTemplar.getTypeDistance(temputs, argType, paramType) match {
            case (None) => vfail("wat")
            case (Some(distance)) => (previousParamsScores :+ distance)
          }
        }
      })
  }

  private def narrowDownCallableOverloads(
      temputs: Temputs,
      callRange: RangeS,
      unfilteredBanners: Set[IValidCalleeCandidate],
      argTypes: Vector[CoordT]):
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
          if (potentialBannerByBannerSoFar.exists(_.banner == currentPotentialBanner.banner)) {
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

    val bannersAndScores =
      banners.foldLeft((Vector[(IValidCalleeCandidate, Vector[TypeDistance])]()))({
        case ((previousBannersAndScores), banner) => {
          val scores =
            getBannerParamScores(temputs, banner, argTypes)
          (previousBannersAndScores :+ (banner, scores))
        }
      })

    val bestScore =
      bannersAndScores.map(_._2).reduce((aScore, bScore) => {
        if (aScore == bScore) {
          // Doesn't matter, just return one
          aScore
        } else {
          val aIsBetter =
            aScore.zip(bScore).forall({
              case (aScorePart, bScorePart) => aScorePart.lessThanOrEqualTo(bScorePart)
            })
          if (aIsBetter) aScore else bScore
        }
      })

    val bannerByIsBestScore =
      bannersAndScores.groupBy[Boolean]({ case (_, score) => score == bestScore })


    val bannerWithBestScore =
      if (bannerByIsBestScore.getOrElse(true, Vector.empty).isEmpty) {
        vfail("wat")
      } else if (bannerByIsBestScore.getOrElse(true, Vector.empty).size > 1) {
        throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Can't resolve between:\n" + bannerByIsBestScore.mapValues(_.mkString("\n")).mkString("\n")))
      } else {
        bannerByIsBestScore(true).head._1
      };

    val rejectedBanners =
      bannerByIsBestScore.getOrElse(false, Vector.empty).map(_._1)
    val rejectionReasonByBanner =
      rejectedBanners.map((_, Outscored())).toMap

    (bannerWithBestScore, rejectionReasonByBanner)
  }

  def stampPotentialFunctionForBanner(
      env: IEnvironmentBox,
      temputs: Temputs,
      callRange: RangeS,
      potentialBanner: IValidCalleeCandidate):
  (FunctionBannerT) = {
    potentialBanner match {
      case ValidCalleeCandidate(signature, ft @ FunctionTemplata(_, _)) => {
        if (ft.function.isTemplate) {
          val (EvaluateFunctionSuccess(banner)) =
            functionTemplar.evaluateTemplatedLightFunctionFromCallForBanner(
              temputs, callRange, ft, Vector.empty, signature.paramTypes.map(p => ParamFilter(p, None)));
          (banner)
        } else {
          functionTemplar.evaluateOrdinaryFunctionFromNonCallForBanner(
            temputs, callRange, ft)
        }
      }
      case ValidHeaderCalleeCandidate(header) => {
        (header.toBanner)
      }
    }
  }

  // The "for temputs" thing is important, it means we don't care what the result is, we just
  // want to make sure it gets into the outputs.
  private def stampPotentialFunctionForPrototype(
      temputs: Temputs,
      range: RangeS,
      potentialBanner: IValidCalleeCandidate,
      args: Vector[ParamFilter]):
  (PrototypeT) = {
    potentialBanner match {
      case ValidCalleeCandidate(signature, ft @ FunctionTemplata(_, _)) => {
        if (ft.function.isTemplate) {
          functionTemplar.evaluateTemplatedFunctionFromCallForPrototype(
              temputs, range, ft, signature.fullName.last.templateArgs, args) match {
            case (EvaluateFunctionSuccess(prototype)) => (prototype)
            case (eff @ EvaluateFunctionFailure(_)) => vfail(eff.toString)
          }
        } else {
          // debt: look into making FunctionTemplar's methods accept function templatas
          // so we dont pass in the wrong environment again
          functionTemplar.evaluateOrdinaryFunctionFromNonCallForPrototype(
            temputs, range, ft)
        }
      }
      case ValidHeaderCalleeCandidate(header) => {
        (header.toPrototype)
      }
    }
  }

  def getArrayGeneratorPrototype(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    callableTE: ReferenceExpressionTE):
  PrototypeT = {
    val funcName = CodeNameS(CallTemplar.CALL_FUNCTION_NAME)
    val paramFilters =
      Vector(
        ParamFilter(callableTE.result.underlyingReference, None),
        ParamFilter(CoordT(ShareT, ReadonlyT, IntT.i32), None))
      findFunction(
        fate.snapshot, temputs, range, funcName, Vector.empty, Array.empty,
        paramFilters, Vector.empty, false)
  }

  def getArrayConsumerPrototype(
    temputs: Temputs,
    fate: FunctionEnvironmentBox,
    range: RangeS,
    callableTE: ReferenceExpressionTE,
    elementType: CoordT):
  PrototypeT = {
    val funcName = CodeNameS(CallTemplar.CALL_FUNCTION_NAME)
    val paramFilters =
      Vector(
        ParamFilter(callableTE.result.underlyingReference, None),
        ParamFilter(elementType, None))
    findFunction(
      fate.snapshot, temputs, range, funcName, Vector.empty, Array.empty, paramFilters, Vector.empty, false)
  }
}
