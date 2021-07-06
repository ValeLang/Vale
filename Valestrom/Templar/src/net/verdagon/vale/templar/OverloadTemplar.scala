package net.verdagon.vale.templar

import net.verdagon.vale.astronomer._
import net.verdagon.vale.astronomer.ruletyper.{IRuleTyperEvaluatorDelegate, RuleTyperEvaluator, RuleTyperSolveFailure, RuleTyperSolveSuccess}
import net.verdagon.vale.scout.rules.{EqualsSR, TemplexSR, TypedSR}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata.{IPotentialBanner, _}
import net.verdagon.vale.scout.{CodeRuneS, CodeTypeNameS, ExplicitTemplateArgRuneS, INameS, ITemplexS, RangeS}
import net.verdagon.vale.templar.OverloadTemplar.{IScoutExpectedFunctionFailureReason, IScoutExpectedFunctionResult, InferFailure, Outscored, ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess, SpecificParamDoesntMatch, SpecificParamVirtualityDoesntMatch, WrongNumberOfArguments, WrongNumberOfTemplateArguments}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.function.FunctionTemplar
import net.verdagon.vale.templar.function.FunctionTemplar.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
import net.verdagon.vale.templar.infer.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.{IProfiler, vassert, vfail}

import scala.collection.immutable.List

object OverloadTemplar {

  sealed trait IScoutExpectedFunctionFailureReason
  case class WrongNumberOfArguments(supplied: Int, expected: Int) extends IScoutExpectedFunctionFailureReason
  case class WrongNumberOfTemplateArguments(supplied: Int, expected: Int) extends IScoutExpectedFunctionFailureReason
  case class SpecificParamDoesntMatch(index: Int, reason: String) extends IScoutExpectedFunctionFailureReason
  case class SpecificParamVirtualityDoesntMatch(index: Int) extends IScoutExpectedFunctionFailureReason
  case class Outscored() extends IScoutExpectedFunctionFailureReason
  case class InferFailure(reason: InferSolveFailure) extends IScoutExpectedFunctionFailureReason


  sealed trait IScoutExpectedFunctionResult
  case class ScoutExpectedFunctionSuccess(prototype: PrototypeT) extends IScoutExpectedFunctionResult
  case class ScoutExpectedFunctionFailure(
    name: IImpreciseNameStepA,
    args: List[ParamFilter],
    // All the ones that could have worked, but were outscored by the best match
    outscoredReasonByPotentialBanner: Map[IPotentialBanner, IScoutExpectedFunctionFailureReason],
    // All the banners we rejected, and the reason why
    rejectedReasonByBanner: Map[FunctionBannerT, IScoutExpectedFunctionFailureReason],
    // All the FunctionA we rejected, and the reason why
    rejectedReasonByFunction: Map[FunctionA, IScoutExpectedFunctionFailureReason]
  ) extends IScoutExpectedFunctionResult {
    override def toString = {
      "Couldn't find a fn " + name + "(" + args.map(TemplataNamer.getIdentifierName(_)).mkString(", ") + ")\n" +
        "Outscored:\n" + outscoredReasonByPotentialBanner.map({
        case (potentialBanner, outscoredReason) => TemplataNamer.getFullNameIdentifierName(potentialBanner.banner.fullName) + ":\n  " + outscoredReason
      }).mkString("\n") + "\n" +
        "Rejected:\n" + rejectedReasonByBanner.map({
        case (banner, rejectedReason) => TemplataNamer.getFullNameIdentifierName(banner.fullName) + ":\n  " + rejectedReason
      }).mkString("\n") + "\n" +
        "Rejected:\n" + rejectedReasonByFunction.map({
        case (functionS, rejectedReason) => functionS + ":\n  " + rejectedReason
      }).mkString("\n") + "\n"
    }
  }
}

class OverloadTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
    templataTemplar: TemplataTemplar,
    inferTemplar: InferTemplar,
    functionTemplar: FunctionTemplar) {
  def scoutMaybeFunctionForPrototype(
      // The environment to look in.
      env: IEnvironment,
      temputs: Temputs,
      callRange: RangeS,
      functionName: IImpreciseNameStepA,
      explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
      args: List[ParamFilter],
    extraEnvsToLookIn: List[IEnvironment],
      exact: Boolean):
  (
    Option[PrototypeT],
    // All the ones that could have worked, but were outscored by the best match
    Map[IPotentialBanner, IScoutExpectedFunctionFailureReason],
    // All the banners we rejected, and the reason why
    Map[FunctionBannerT, IScoutExpectedFunctionFailureReason],
    // All the FunctionA we rejected, and the reason why
    Map[FunctionA, IScoutExpectedFunctionFailureReason]
  ) = {
    val (maybePotentialBanner, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction) =
      scoutPotentialFunction(
        env, temputs, callRange, functionName, explicitlySpecifiedTemplateArgTemplexesS, args, extraEnvsToLookIn, exact)
    maybePotentialBanner match {
      case None => {
        (None, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction)
      }
      case Some(potentialBanner) => {
        val thing =
          stampPotentialFunctionForPrototype(temputs, callRange, potentialBanner, args)
        (Some(thing), outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction)
      }
    }
  }

  def scoutExpectedFunctionForPrototype(
    env: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    functionName: IImpreciseNameStepA,
    explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
    args: List[ParamFilter],
    extraEnvsToLookIn: List[IEnvironment],
    exact: Boolean):
  (IScoutExpectedFunctionResult) = {
    val (maybeFunction, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction) =
      scoutMaybeFunctionForPrototype(
        env, temputs, callRange, functionName, explicitlySpecifiedTemplateArgTemplexesS, args, extraEnvsToLookIn, exact)
    maybeFunction match {
      case None => {
        (ScoutExpectedFunctionFailure(functionName, args, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction))
      }
      case Some(function) => {
        (ScoutExpectedFunctionSuccess(function))
      }
    }
  }

  private def paramMatches(
    temputs: Temputs,
    source: CoordT,
    destination: CoordT,
    exact: Boolean):
  (
    // Rejection reason, if any. None means it matches.
    Option[String]
  ) = {
    if (exact) {
      if (source == destination) {
        (None)
      } else {
        (Some(TemplataNamer.getReferenceIdentifierName(source) + " is not " + TemplataNamer.getReferenceIdentifierName(destination)))
      }
    } else {
      templataTemplar.isTypeConvertible(temputs, source, destination) match {
        case (true) => (None)
        case (false) => (Some(source + " cannot convert to " + destination))
      }
    }
  }

  private def paramsMatch(
    temputs: Temputs,
    desiredParams: List[ParamFilter],
    candidateParams: List[ParameterT],
    exact: Boolean):
  (
    // Rejection reason, if any. None means it matches.
    Option[IScoutExpectedFunctionFailureReason]
  ) = {
    if (desiredParams.size != candidateParams.size) {
      return (Some(WrongNumberOfArguments(desiredParams.size, candidateParams.size)))
    }
    desiredParams.zip(candidateParams).zipWithIndex.foreach({
      case (((desiredParam, candidateParam), paramIndex)) => {
        val ParamFilter(desiredTemplata, desiredMaybeVirtuality) = desiredParam
        val ParameterT(_, candidateMaybeVirtuality, candidateType) = candidateParam
        paramMatches(temputs, desiredTemplata, candidateType, exact) match {
          case (Some(rejectionReason)) => {
            return (Some(SpecificParamDoesntMatch(paramIndex, rejectionReason)))
          }
          case (None) => temputs
        }
        ((desiredMaybeVirtuality, candidateMaybeVirtuality) match {
          case (None, _) =>
          case (desiredVirtuality, candidateVirtuality) => {
            if (desiredVirtuality != candidateVirtuality) {
              return (Some(SpecificParamVirtualityDoesntMatch(paramIndex)))
            }
          }
        })
      }
    })
    // Would have bailed out early if there was a false
    (None)
  }

  private def getCandidateBanners(
    env: IEnvironment,
    temputs: Temputs,
    callRange: RangeS,
    functionName: IImpreciseNameStepA,
    explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
    paramFilters: List[ParamFilter],
    extraEnvsToLookIn: List[IEnvironment],
    exact: Boolean):
  (
    Set[IPotentialBanner],
    // rejection reason by banner
    Map[FunctionBannerT, IScoutExpectedFunctionFailureReason],
    // rejection reason by function
    Map[FunctionA, IScoutExpectedFunctionFailureReason]
  ) = {
    val hayTemplatas = findHayTemplatas(env, temputs, functionName, paramFilters, extraEnvsToLookIn)

    val (allPotentialBanners, allRejectionReasonByBanner, allRejectionReasonByFunction) =
      hayTemplatas.foldLeft((Set[IPotentialBanner](), Map[FunctionBannerT, IScoutExpectedFunctionFailureReason](), Map[FunctionA, IScoutExpectedFunctionFailureReason]()))({
        case ((previousPotentials, previousRejectionReasonByBanner, previousRejectionReasonByFunction), templata) => {
          val (potentialBanners, rejectionReasonByBanner, rejectionReasonByFunction) =
            templata match {
              case KindTemplata(OverloadSet(overloadsEnv, nameInOverloadsEnv, _)) => {
                getCandidateBanners(
                  overloadsEnv, temputs, callRange, nameInOverloadsEnv, explicitlySpecifiedTemplateArgTemplexesS, paramFilters, List.empty, exact)
              }
              case KindTemplata(sr @ StructRefT(_)) => {
                val structEnv = temputs.getEnvForStructRef(sr)
                getCandidateBanners(
                  structEnv, temputs, callRange, GlobalFunctionFamilyNameA(CallTemplar.CALL_FUNCTION_NAME), explicitlySpecifiedTemplateArgTemplexesS, paramFilters, List.empty, exact)
              }
              case KindTemplata(sr @ InterfaceRefT(_)) => {
                val interfaceEnv = temputs.getEnvForInterfaceRef(sr)
                getCandidateBanners(
                  interfaceEnv, temputs, callRange, GlobalFunctionFamilyNameA(CallTemplar.CALL_FUNCTION_NAME), explicitlySpecifiedTemplateArgTemplexesS, paramFilters, List.empty, exact)
              }
              case ExternFunctionTemplata(header) => {
                paramsMatch(temputs, paramFilters, header.params, exact) match {
                  case (None) => {
                    (List(PotentialBannerFromExternFunction(header)), Map(), Map())
                  }
                  case (Some(rejectionReason)) => {
                    (List.empty, Map(header.toBanner -> rejectionReason), Map())
                  }
                }
              }
              case ft @ FunctionTemplata(_, function) => {
                // See OFCBT.
                if (ft.function.isTemplate) {
                  function.tyype match {
                    case TemplateTemplataType(identifyingRuneTemplataTypes, FunctionTemplataType) => {
                      val ruleTyper =
                        new RuleTyperEvaluator[IEnvironment, Temputs](
                          new IRuleTyperEvaluatorDelegate[IEnvironment, Temputs] {
                            override def lookupType(state: Temputs, env: IEnvironment, range: RangeS, name: INameS): ITemplataType = {
                              val templata =
                                env.getNearestTemplataWithAbsoluteName2(NameTranslator.translateNameStep(Astronomer.translateName(name)), Set[ILookupContext](TemplataLookupContext)) match {
                                  case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Nothing found with name " + name))
                                  case Some(t) => t
                                }
                              (templata.tyype)
                            }
                            override def lookupType(state: Temputs, env: IEnvironment, rangeS: RangeS, name: CodeTypeNameS): ITemplataType = {
                              val templata =
                                env.getNearestTemplataWithName(Astronomer.translateImpreciseName(name), Set(TemplataLookupContext)) match {
                                  case None => throw CompileErrorExceptionT(CouldntFindTypeT(rangeS, name.name))
                                  case Some(t) => t
                                }
                              (templata.tyype)
                            }
                          }
                        )

                      if (explicitlySpecifiedTemplateArgTemplexesS.size > identifyingRuneTemplataTypes.size) {
                        throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Supplied more arguments than there are identifying runes!"))
                      }

                      // Now that we know what types are expected, we can FINALLY rule-type these explicitly
                      // specified template args! (The rest of the rule-typing happened back in the astronomer,
                      // this is the one time we delay it, see MDRTCUT).

                      // There might be less explicitly specified template args than there are types, and that's
                      // fine. Hopefully the rest will be figured out by the rule evaluator.
                      val templateArgRuneNamesS = explicitlySpecifiedTemplateArgTemplexesS.indices.toList.map(ExplicitTemplateArgRuneS)
                      val equalsRules =
                        explicitlySpecifiedTemplateArgTemplexesS.zip(identifyingRuneTemplataTypes).zip(templateArgRuneNamesS).map({
                          case ((explicitlySpecifiedTemplateArgTemplexS, identifyingRuneTemplataType), templateArgRuneNames) => {
                            EqualsSR(
                              callRange,
                              TypedSR(callRange, templateArgRuneNames, Conversions.unevaluateTemplataType(identifyingRuneTemplataType)),
                              TemplexSR(explicitlySpecifiedTemplateArgTemplexS))
                          }
                        })
                      val templateArgRuneNamesA = templateArgRuneNamesS.map(Astronomer.translateRune)
                      val templateArgRuneNames2 = templateArgRuneNamesA.map(NameTranslator.translateRune)

                      (try {
                        // And now that we know the types that are expected of these template arguments, we can
                        // run these template argument templexes through the solver so it can evaluate them in
                        // context of the current environment and spit out some templatas.
                        ruleTyper.solve(temputs, env, equalsRules, callRange, List.empty, Some(templateArgRuneNamesA.toSet))
                      } catch {
                        case CompileErrorExceptionA(err) => throw CompileErrorExceptionT(InferAstronomerError(err))
                      }) match {
                        case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => {
                          val reason = WrongNumberOfTemplateArguments(identifyingRuneTemplataTypes.size, explicitlySpecifiedTemplateArgTemplexesS.size)
                          (List.empty, Map(), Map(function -> reason))
                        }
                        case (runeTypeConclusions, RuleTyperSolveSuccess(rulesA)) => {
                          // rulesA is the equals rules, but rule typed. Now we'll run them through the solver to get
                          // some actual templatas.
//
//                          val explicitTemplatas = templataTemplar.evaluateTemplexes(env, temputs, explicitlySpecifiedTemplateArgTemplexesS)

                          // We only want to solve the template arg runes
                          profiler.childFrame("late astronoming", () => {
                            inferTemplar.inferFromExplicitTemplateArgs(
                                env, temputs, List.empty, rulesA, runeTypeConclusions.typeByRune, templateArgRuneNamesA.toSet, List.empty, None, callRange, List.empty) match {
                              case (isf @ InferSolveFailure(_, _, _, _, _, _, _)) => {
                                (List.empty, Map(), Map(function -> InferFailure(isf)))
                              }
                              case (InferSolveSuccess(inferences)) => {
                                val explicitlySpecifiedTemplateArgTemplatas = templateArgRuneNames2.map(inferences.templatasByRune)

                                functionTemplar.evaluateTemplatedFunctionFromCallForBanner(
                                  temputs, callRange, ft, explicitlySpecifiedTemplateArgTemplatas, paramFilters) match {
                                  case (EvaluateFunctionFailure(reason)) => {
                                    (List.empty, Map(), Map(function -> reason))
                                  }
                                  case (EvaluateFunctionSuccess(banner)) => {
                                    paramsMatch(temputs, paramFilters, banner.params, exact) match {
                                      case (Some(rejectionReason)) => {
                                        (List.empty, Map(banner -> rejectionReason), Map())
                                      }
                                      case (None) => {
                                        (List(PotentialBannerFromFunctionS(banner, ft)), Map(), Map())
                                      }
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
                        temputs, callRange, ft, List.empty, paramFilters) match {
                        case (EvaluateFunctionFailure(reason)) => {
                          (List.empty, Map(), Map(function -> reason))
                        }
                        case (EvaluateFunctionSuccess(banner)) => {
                          paramsMatch(temputs, paramFilters, banner.params, exact) match {
                            case (Some(rejectionReason)) => {
                              (List.empty, Map(banner -> rejectionReason), Map())
                            }
                            case (None) => {
                              (List(PotentialBannerFromFunctionS(banner, ft)), Map(), Map())
                            }
                          }
                        }
                      }
                    }
                  }
                } else {
                    val banner =
                      functionTemplar.evaluateOrdinaryFunctionFromNonCallForBanner(
                        temputs, callRange, ft)
                    paramsMatch(temputs, paramFilters, banner.params, exact) match {
                      case (None) => {
                        (List(PotentialBannerFromFunctionS(banner, ft)), Map(), Map())
                      }
                      case (Some(rejectionReason)) => {
                        (List.empty, Map(banner -> rejectionReason), Map())
                      }
                    }
                }
              }
            }
          (previousPotentials ++ potentialBanners, previousRejectionReasonByBanner ++ rejectionReasonByBanner, previousRejectionReasonByFunction ++ rejectionReasonByFunction)
        }
      })
    (allPotentialBanners, allRejectionReasonByBanner, allRejectionReasonByFunction)
  }

  // Gets all the environments for all the arguments.
  private def getParamEnvironments(temputs: Temputs, paramFilters: List[ParamFilter]):
  List[IEnvironment] = {
    paramFilters.flatMap({ case ParamFilter(tyype, virtuality) =>
      (tyype.kind match {
        case sr @ StructRefT(_) => List(temputs.getEnvForStructRef(sr))
        case ir @ InterfaceRefT(_) => List(temputs.getEnvForInterfaceRef(ir))
        case _ => List.empty
      }) ++
        (virtuality match {
          case None => List.empty
          case Some(AbstractT$) => List.empty
          case Some(OverrideT(ir)) => List(temputs.getEnvForInterfaceRef(ir))
        })
    })
  }

  // Looks in all the environments of the given arguments for something with the given name.
  private def findHayTemplatas(
      env: IEnvironment,
      temputs: Temputs,
      impreciseName: IImpreciseNameStepA,
      paramFilters: List[ParamFilter],
      extraEnvsToLookIn: List[IEnvironment]):
  Set[ITemplata] = {
    val environments = env :: getParamEnvironments(temputs, paramFilters) ++ extraEnvsToLookIn
    environments.flatMap(_.getAllTemplatasWithName(profiler, impreciseName, Set(ExpressionLookupContext))).toSet
  }

  // Checks to see if there's a function that *could*
  // exist that takes in these parameter types, and returns what the signature *would* look like.
  // Only considers when arguments match exactly.
  // If given something in maybeSuperInterfaceRef2, it will search for a function that
  // overrides that interfaceRef2 in that position. If we ever support multimethods we
  // might need to take a list of these, same length as the arg types... or combine
  // them somehow.
  def scoutPotentialFunction(
      env: IEnvironment,
      temputs: Temputs,
      callRange: RangeS,
      functionName: IImpreciseNameStepA,
      explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
      args: List[ParamFilter],
    extraEnvsToLookIn: List[IEnvironment],
      exact: Boolean):
  (
    // Best match, if any
    Option[IPotentialBanner],
    // All the ones that could have worked, but were outscored by the best match
    Map[IPotentialBanner, IScoutExpectedFunctionFailureReason],
    // All the banners we rejected, and the reason why
    Map[FunctionBannerT, IScoutExpectedFunctionFailureReason],
    // All the FunctionA we rejected, and the reason why
    Map[FunctionA, IScoutExpectedFunctionFailureReason]
  ) = {
    profiler.childFrame("scout potential function", () => {
      val (candidateBanners, rejectionReasonByBanner, rejectionReasonByFunction) =
        getCandidateBanners(env, temputs, callRange, functionName, explicitlySpecifiedTemplateArgTemplexesS, args, extraEnvsToLookIn, exact);
      if (candidateBanners.isEmpty) {
        (None, Map(), rejectionReasonByBanner, rejectionReasonByFunction)
      } else if (candidateBanners.size == 1) {
        (Some(candidateBanners.head), Map(), rejectionReasonByBanner, rejectionReasonByFunction)
      } else {
        val (best, outscoreReasonByBanner) =
          narrowDownCallableOverloads(temputs, callRange, candidateBanners, args.map(_.tyype))
        (Some(best), outscoreReasonByBanner, rejectionReasonByBanner, rejectionReasonByFunction)
      }
    })
  }

  private def getBannerParamScores(
    temputs: Temputs,
    banner: IPotentialBanner,
    argTypes: List[CoordT]):
  (List[TypeDistance]) = {
    banner.banner.paramTypes.zip(argTypes)
      .foldLeft((List[TypeDistance]()))({
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
      unfilteredBanners: Set[IPotentialBanner],
      argTypes: List[CoordT]):
  (
    IPotentialBanner,
    // Rejection reason by banner
    Map[IPotentialBanner, IScoutExpectedFunctionFailureReason]) = {

    // Sometimes a banner might come from many different environments (remember,
    // when we do a call, we look in the environments of all the arguments' types).
    // Here we weed out these duplicates.
    val dedupedBanners =
      unfilteredBanners.foldLeft(List[IPotentialBanner]())({
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
            case PotentialBannerFromFunctionS(_, function) => !function.function.isTemplate
            case PotentialBannerFromExternFunction(_) => true
          })
        if (ordinaryBanners.isEmpty) {
          // No ordinary banners, so include all the templated ones
          potentialBannersWithSameParamTypes
        } else {
          // There are some ordinary banners, so only consider the ordinary banners
          ordinaryBanners
        }
      }).toList

    val bannersAndScores =
      banners.foldLeft((List[(IPotentialBanner, List[TypeDistance])]()))({
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
      if (bannerByIsBestScore.getOrElse(true, List.empty).isEmpty) {
        vfail("wat")
      } else if (bannerByIsBestScore.getOrElse(true, List.empty).size > 1) {
        throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Can't resolve between:\n" + bannerByIsBestScore.mapValues(_.mkString("\n")).mkString("\n")))
      } else {
        bannerByIsBestScore(true).head._1
      };

    val rejectedBanners =
      bannerByIsBestScore.getOrElse(false, List.empty).map(_._1)
    val rejectionReasonByBanner =
      rejectedBanners.map((_, Outscored())).toMap

    (bannerWithBestScore, rejectionReasonByBanner)
  }

  def stampPotentialFunctionForBanner(
      env: IEnvironmentBox,
      temputs: Temputs,
      callRange: RangeS,
      potentialBanner: IPotentialBanner):
  (FunctionBannerT) = {
    potentialBanner match {
      case PotentialBannerFromFunctionS(signature, ft @ FunctionTemplata(_, _)) => {
        if (ft.function.isTemplate) {
          val (EvaluateFunctionSuccess(banner)) =
            functionTemplar.evaluateTemplatedLightFunctionFromCallForBanner(
              temputs, callRange, ft, List.empty, signature.paramTypes.map(p => ParamFilter(p, None)));
          (banner)
        } else {
          functionTemplar.evaluateOrdinaryFunctionFromNonCallForBanner(
            temputs, callRange, ft)
        }
      }
      case PotentialBannerFromExternFunction(header) => {
        (header.toBanner)
      }
    }
  }

  // The "for temputs" thing is important, it means we don't care what the result is, we just
  // want to make sure it gets into the outputs.
  private def stampPotentialFunctionForPrototype(
      temputs: Temputs,
      range: RangeS,
      potentialBanner: IPotentialBanner,
      args: List[ParamFilter]):
  (PrototypeT) = {
    potentialBanner match {
      case PotentialBannerFromFunctionS(signature, ft @ FunctionTemplata(_, _)) => {
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
      case PotentialBannerFromExternFunction(header) => {
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
    val funcName = GlobalFunctionFamilyNameA(CallTemplar.CALL_FUNCTION_NAME)
    val paramFilters =
      List(
        ParamFilter(callableTE.resultRegister.underlyingReference, None),
        ParamFilter(CoordT(ShareT, ReadonlyT, IntT.i32), None))
    val prototype =
      scoutExpectedFunctionForPrototype(
        fate.snapshot, temputs, range, funcName, List.empty, paramFilters, List.empty, false) match {
        case seff@ScoutExpectedFunctionFailure(name, args, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction) => {
          throw CompileErrorExceptionT(RangedInternalErrorT(range, seff.toString))
        }
        case ScoutExpectedFunctionSuccess(p) => p
      }
    prototype
  }

}
