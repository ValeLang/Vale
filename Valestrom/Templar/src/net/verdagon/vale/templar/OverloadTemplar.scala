package net.verdagon.vale.templar

import net.verdagon.vale.astronomer._
import net.verdagon.vale.astronomer.ruletyper.{IRuleTyperEvaluatorDelegate, RuleTyperEvaluator, RuleTyperSolveFailure, RuleTyperSolveSuccess}
import net.verdagon.vale.scout.rules.{EqualsSR, TemplexSR, TypedSR}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata.{IPotentialBanner, _}
import net.verdagon.vale.scout.{CodeRuneS, CodeTypeNameS, ExplicitTemplateArgRuneS, INameS, ITemplexS}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.FunctionTemplar
import net.verdagon.vale.templar.function.FunctionTemplar.{EvaluateFunctionFailure, EvaluateFunctionSuccess, IEvaluateFunctionResult}
import net.verdagon.vale.templar.infer.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.{vassert, vfail}

import scala.collection.immutable.List

object OverloadTemplar {
  def scoutMaybeFunctionForPrototype(
      // The environment to look in.
      env: IEnvironment,
      temputs: TemputsBox,
      functionName: GlobalFunctionFamilyNameA,
      explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
      args: List[ParamFilter],
    extraEnvsToLookIn: List[IEnvironment],
      exact: Boolean):
  (
    Option[Prototype2],
    // All the ones that could have worked, but were outscored by the best match
    Map[IPotentialBanner, String],
    // All the banners we rejected, and the reason why
    Map[FunctionBanner2, String],
    // All the FunctionA we rejected, and the reason why
    Map[FunctionA, String]
  ) = {
    val (maybePotentialBanner, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction) =
      scoutPotentialFunction(
        env, temputs, functionName, explicitlySpecifiedTemplateArgTemplexesS, args, extraEnvsToLookIn, exact)
    maybePotentialBanner match {
      case None => {
        (None, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction)
      }
      case Some(potentialBanner) => {
        val thing =
          stampPotentialFunctionForPrototype(temputs, potentialBanner, args)
        (Some(thing), outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction)
      }
    }
  }

  sealed trait IScoutExpectedFunctionResult
  case class ScoutExpectedFunctionSuccess(prototype: Prototype2) extends IScoutExpectedFunctionResult
  case class ScoutExpectedFunctionFailure(
    humanName: GlobalFunctionFamilyNameA,
    args: List[ParamFilter],
    // All the ones that could have worked, but were outscored by the best match
    outscoredReasonByPotentialBanner: Map[IPotentialBanner, String],
    // All the banners we rejected, and the reason why
    rejectedReasonByBanner: Map[FunctionBanner2, String],
    // All the FunctionA we rejected, and the reason why
    rejectedReasonByFunction: Map[FunctionA, String]
  ) extends IScoutExpectedFunctionResult {
    override def toString = {
      "Couldn't find a fn " + humanName + "(" + args.map(TemplataNamer.getIdentifierName(_)).mkString(", ") + ")\n" +
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

  def scoutExpectedFunctionForPrototype(
    env: IEnvironment,
    temputs: TemputsBox,
    functionName: GlobalFunctionFamilyNameA,
    explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
    args: List[ParamFilter],
    extraEnvsToLookIn: List[IEnvironment],
    exact: Boolean):
  (IScoutExpectedFunctionResult) = {
    val (maybeFunction, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction) =
      scoutMaybeFunctionForPrototype(
        env, temputs, functionName, explicitlySpecifiedTemplateArgTemplexesS, args, extraEnvsToLookIn, exact)
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
    temputs: TemputsBox,
    source: Coord,
    destination: Coord,
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
      TemplataTemplar.isTypeConvertible(temputs, source, destination) match {
        case (true) => (None)
        case (false) => (Some(source + " cannot convert to " + destination))
      }
    }
  }

  private def paramsMatch(
    temputs: TemputsBox,
    desiredParams: List[ParamFilter],
    candidateParams: List[Parameter2],
    exact: Boolean):
  (
    // Rejection reason, if any. None means it matches.
    Option[String]
  ) = {
    if (desiredParams.size != candidateParams.size) {
      return (Some("Number of params doesn't match! Supplied " + desiredParams.size + " but function takes " + candidateParams.size))
    }
    desiredParams.zip(candidateParams).zipWithIndex.foreach({
      case (((desiredParam, candidateParam), paramIndex)) => {
        val ParamFilter(desiredTemplata, desiredMaybeVirtuality) = desiredParam
        val Parameter2(_, candidateMaybeVirtuality, candidateType) = candidateParam
        paramMatches(temputs, desiredTemplata, candidateType, exact) match {
          case (Some(rejectionReason)) => {
            return (Some("Param at index " + paramIndex + " doesn't match: " + rejectionReason))
          }
          case (None) => temputs
        }
        ((desiredMaybeVirtuality, candidateMaybeVirtuality) match {
          case (None, _) =>
          case (desiredVirtuality, candidateVirtuality) => {
            if (desiredVirtuality != candidateVirtuality) {
              return (Some("Virtualities don't match at index " + paramIndex))
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
    temputs: TemputsBox,
    functionName: GlobalFunctionFamilyNameA,
    explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
    paramFilters: List[ParamFilter],
    extraEnvsToLookIn: List[IEnvironment],
    exact: Boolean):
  (
    Set[IPotentialBanner],
    // rejection reason by banner
    Map[FunctionBanner2, String],
    // rejection reason by function
    Map[FunctionA, String]
  ) = {
    val hayTemplatas = findHayTemplatas(env, temputs, functionName, paramFilters, extraEnvsToLookIn)

    val (allPotentialBanners, allRejectionReasonByBanner, allRejectionReasonByFunction) =
      hayTemplatas.foldLeft((Set[IPotentialBanner](), Map[FunctionBanner2, String](), Map[FunctionA, String]()))({
        case ((previousPotentials, previousRejectionReasonByBanner, previousRejectionReasonByFunction), templata) => {
          val (potentialBanners, rejectionReasonByBanner, rejectionReasonByFunction) =
            templata match {
              case KindTemplata(OverloadSet(overloadsEnv, nameInOverloadsEnv, _)) => {
                getCandidateBanners(
                  overloadsEnv, temputs, nameInOverloadsEnv, explicitlySpecifiedTemplateArgTemplexesS, paramFilters, List(), exact)
              }
              case KindTemplata(sr @ StructRef2(_)) => {
                val structEnv = temputs.envByStructRef(sr)
                getCandidateBanners(
                  structEnv, temputs, GlobalFunctionFamilyNameA(CallTemplar.CALL_FUNCTION_NAME), explicitlySpecifiedTemplateArgTemplexesS, paramFilters, List(), exact)
              }
              case KindTemplata(sr @ InterfaceRef2(_)) => {
                val interfaceEnv = temputs.envByInterfaceRef(sr)
                getCandidateBanners(
                  interfaceEnv, temputs, GlobalFunctionFamilyNameA(CallTemplar.CALL_FUNCTION_NAME), explicitlySpecifiedTemplateArgTemplexesS, paramFilters, List(), exact)
              }
              case ExternFunctionTemplata(header) => {
                paramsMatch(temputs, paramFilters, header.params, exact) match {
                  case (None) => {
                    (List(PotentialBannerFromExternFunction(header)), Map(), Map())
                  }
                  case (Some(rejectionReason)) => {
                    (List(), Map(header.toBanner -> rejectionReason), Map())
                  }
                }
              }
              case ft @ FunctionTemplata(_, function) => {
                // See OFCBT.
                if (ft.function.isTemplate) {
                  function.tyype match {
                    case TemplateTemplataType(identifyingRuneTemplataTypes, FunctionTemplataType) => {
                      val ruleTyper =
                        new RuleTyperEvaluator[IEnvironment, TemputsBox](
                          new IRuleTyperEvaluatorDelegate[IEnvironment, TemputsBox] {
                            override def lookupType(state: TemputsBox, env: IEnvironment, name: INameS): ITemplataType = {
                              val templata =
                                env.getNearestTemplataWithAbsoluteName2(NameTranslator.translateNameStep(Astronomer.translateName(name)), Set[ILookupContext](TemplataLookupContext)) match {
                                  case None => vfail("Nothing found with name " + name)
                                  case Some(t) => t
                                }
                              (templata.tyype)
                            }
                            override def lookupType(state: TemputsBox, env: IEnvironment, name: CodeTypeNameS): ITemplataType = {
                              val templata =
                                env.getNearestTemplataWithName(Astronomer.translateImpreciseName(name), Set(TemplataLookupContext)) match {
                                  case None => vfail("Nothing found with name " + name)
                                  case Some(t) => t
                                }
                              (templata.tyype)
                            }
                          }
                        )

                      if (explicitlySpecifiedTemplateArgTemplexesS.size > identifyingRuneTemplataTypes.size) {
                        vfail("Supplied more arguments than there are identifying runes!")
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
                              TypedSR(templateArgRuneNames, Conversions.unevaluateTemplataType(identifyingRuneTemplataType)),
                              TemplexSR(explicitlySpecifiedTemplateArgTemplexS))
                          }
                        })
                      val templateArgRuneNamesA = templateArgRuneNamesS.map(Astronomer.translateRune)
                      val templateArgRuneNames2 = templateArgRuneNamesA.map(NameTranslator.translateRune)

                      // And now that we know the types that are expected of these template arguments, we can
                      // run these template argument templexes through the solver so it can evaluate them in
                      // context of the current environment and spit out some templatas.
                      ruleTyper.solve(temputs, env, equalsRules, List(), Some(templateArgRuneNamesA.toSet)) match {
                        case (_, rtsf @ RuleTyperSolveFailure(_, _, _)) => (List(), Map(), Map(function -> ("Specified template args don't match expected types!\nExpected types: (" + identifyingRuneTemplataTypes.mkString(",") + ")\nSpecified template args: " + explicitlySpecifiedTemplateArgTemplexesS + "\nCause: " + rtsf.toString)))
                        case (runeTypeConclusions, RuleTyperSolveSuccess(rulesA)) => {
                          // rulesA is the equals rules, but rule typed. Now we'll run them through the solver to get
                          // some actual templatas.
//
//                          val explicitTemplatas = TemplataTemplar.evaluateTemplexes(env, temputs, explicitlySpecifiedTemplateArgTemplexesS)

                          // We only want to solve the template arg runes
                          InferTemplar.inferFromExplicitTemplateArgs(
                              env, temputs, List(), rulesA, runeTypeConclusions.typeByRune, templateArgRuneNamesA.toSet, List(), None, List()) match {
                            case (isf @ InferSolveFailure(_, _, _, _, _, _)) => {
                              (List(), Map(), Map(function -> ("Couldn't evaluate template args: " + isf.toString)))
                            }
                            case (InferSolveSuccess(inferences)) => {
                              val explicitlySpecifiedTemplateArgTemplatas = templateArgRuneNames2.map(inferences.templatasByRune)

                              FunctionTemplar.evaluateTemplatedFunctionFromCallForBanner(
                                temputs, ft, explicitlySpecifiedTemplateArgTemplatas, paramFilters) match {
                                case (EvaluateFunctionFailure(reason)) => {
                                  (List(), Map(), Map(function -> reason))
                                }
                                case (EvaluateFunctionSuccess(banner)) => {
                                  paramsMatch(temputs, paramFilters, banner.params, exact) match {
                                    case (Some(rejectionReason)) => {
                                      (List(), Map(banner -> rejectionReason), Map())
                                    }
                                    case (None) => {
                                      (List(PotentialBannerFromFunctionS(banner, ft)), Map(), Map())
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
                      FunctionTemplar.evaluateTemplatedFunctionFromCallForBanner(
                        temputs, ft, List(), paramFilters) match {
                        case (EvaluateFunctionFailure(reason)) => {
                          (List(), Map(), Map(function -> reason))
                        }
                        case (EvaluateFunctionSuccess(banner)) => {
                          paramsMatch(temputs, paramFilters, banner.params, exact) match {
                            case (Some(rejectionReason)) => {
                              (List(), Map(banner -> rejectionReason), Map())
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
                      FunctionTemplar.evaluateOrdinaryFunctionFromNonCallForBanner(
                        temputs, ft)
                    paramsMatch(temputs, paramFilters, banner.params, exact) match {
                      case (None) => {
                        (List(PotentialBannerFromFunctionS(banner, ft)), Map(), Map())
                      }
                      case (Some(rejectionReason)) => {
                        (List(), Map(banner -> rejectionReason), Map())
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
  private def getParamEnvironments(temputs: TemputsBox, paramFilters: List[ParamFilter]):
  List[IEnvironment] = {
    paramFilters.flatMap({ case ParamFilter(tyype, virtuality) =>
      (tyype.referend match {
        case sr @ StructRef2(_) => List(temputs.envByStructRef(sr))
        case ir @ InterfaceRef2(_) => List(temputs.envByInterfaceRef(ir))
        case _ => List()
      }) ++
        (virtuality match {
          case None => List()
          case Some(Abstract2) => List()
          case Some(Override2(ir)) => List(temputs.envByInterfaceRef(ir))
        })
    })
  }

  // Looks in all the environments of the given arguments for something with the given name.
  private def findHayTemplatas(
      env: IEnvironment,
      temputs: TemputsBox,
      impreciseName: IImpreciseNameStepA,
      paramFilters: List[ParamFilter],
      extraEnvsToLookIn: List[IEnvironment]):
  Set[ITemplata] = {
    val environments = env :: getParamEnvironments(temputs, paramFilters) ++ extraEnvsToLookIn
    environments.flatMap(_.getAllTemplatasWithName(impreciseName, Set(ExpressionLookupContext))).toSet
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
      temputs: TemputsBox,
      functionName: GlobalFunctionFamilyNameA,
      explicitlySpecifiedTemplateArgTemplexesS: List[ITemplexS],
      args: List[ParamFilter],
    extraEnvsToLookIn: List[IEnvironment],
      exact: Boolean):
  (
    // Best match, if any
    Option[IPotentialBanner],
    // All the ones that could have worked, but were outscored by the best match
    Map[IPotentialBanner, String],
    // All the banners we rejected, and the reason why
    Map[FunctionBanner2, String],
    // All the FunctionA we rejected, and the reason why
    Map[FunctionA, String]
  ) = {
    val (candidateBanners, rejectionReasonByBanner, rejectionReasonByFunction) =
      getCandidateBanners(env, temputs, functionName, explicitlySpecifiedTemplateArgTemplexesS, args, extraEnvsToLookIn, exact);
    if (candidateBanners.isEmpty) {
      (None, Map(), rejectionReasonByBanner, rejectionReasonByFunction)
    } else if (candidateBanners.size == 1) {
      (Some(candidateBanners.head), Map(), rejectionReasonByBanner, rejectionReasonByFunction)
    } else {
      val (best, outscoreReasonByBanner) =
        narrowDownCallableOverloads(temputs, candidateBanners, args.map(_.tyype))
      (Some(best), outscoreReasonByBanner, rejectionReasonByBanner, rejectionReasonByFunction)
    }
  }

  private def getBannerParamScores(
    temputs: TemputsBox,
    banner: IPotentialBanner,
    argTypes: List[Coord]):
  (List[TypeDistance]) = {
    banner.banner.paramTypes.zip(argTypes)
      .foldLeft((List[TypeDistance]()))({
        case ((previousParamsScores), (paramType, argType)) => {
          TemplataTemplar.getTypeDistance(temputs, argType, paramType) match {
            case (None) => vfail("wat")
            case (Some(distance)) => (previousParamsScores :+ distance)
          }
        }
      })
  }

  private def narrowDownCallableOverloads(
      temputs: TemputsBox,
      unfilteredBanners: Set[IPotentialBanner],
      argTypes: List[Coord]):
  (
    IPotentialBanner,
    // Rejection reason by banner
    Map[IPotentialBanner, String]) = {

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
      if (bannerByIsBestScore.getOrElse(true, List()).isEmpty) {
        vfail("wat")
      } else if (bannerByIsBestScore.getOrElse(true, List()).size > 1) {
        vfail("Can't resolve between:\n" + bannerByIsBestScore.mkString("\n"))
      } else {
        bannerByIsBestScore(true).head._1
      };

    val rejectedBanners =
      bannerByIsBestScore.getOrElse(false, List()).map(_._1)
    val rejectionReasonByBanner =
      rejectedBanners.map((_, "TODO: rejection reason here")).toMap

    (bannerWithBestScore, rejectionReasonByBanner)
  }

  def stampPotentialFunctionForBanner(
      env: IEnvironmentBox,
      temputs: TemputsBox,
      potentialBanner: IPotentialBanner):
  (FunctionBanner2) = {
    potentialBanner match {
      case PotentialBannerFromFunctionS(signature, ft @ FunctionTemplata(_, _)) => {
        if (ft.function.isTemplate) {
          val (EvaluateFunctionSuccess(banner)) =
            FunctionTemplar.evaluateTemplatedLightFunctionFromCallForBanner(
              temputs, ft, List(), signature.paramTypes.map(p => ParamFilter(p, None)));
          (banner)
        } else {
          FunctionTemplar.evaluateOrdinaryFunctionFromNonCallForBanner(
            temputs, ft)
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
      temputs: TemputsBox,
      potentialBanner: IPotentialBanner,
      args: List[ParamFilter]):
  (Prototype2) = {
    potentialBanner match {
      case PotentialBannerFromFunctionS(signature, ft @ FunctionTemplata(_, _)) => {
        if (ft.function.isTemplate) {
          FunctionTemplar.evaluateTemplatedFunctionFromCallForPrototype(
              temputs, ft, signature.fullName.last.templateArgs, args) match {
            case (EvaluateFunctionSuccess(prototype)) => (prototype)
            case (eff @ EvaluateFunctionFailure(_)) => vfail(eff.toString)
          }
        } else {
          // debt: look into making FunctionTemplar's methods accept function templatas
          // so we dont pass in the wrong environment again
          FunctionTemplar.evaluateOrdinaryFunctionFromNonCallForPrototype(
            temputs, ft)
        }
      }
      case PotentialBannerFromExternFunction(header) => {
        (header.toPrototype)
      }
    }
//    temputs.functions.find(_.header.toBanner == banner) match {
//      case Some(existingFunction) => {
//        // Then it was already stamped/evaluated. This is the case if it came from
//        // a light lambda. We have to do this because the env.functions1ByOrdinarySignature
//        // will fail down there, because lambdas aren't included there...
//        (temputs, existingFunction.header.toPrototype)
//      }
//      case None => {
//        // Then the best banner came from an ordinary banner. Let's speed up its evaluating to now.
//        val maybeOriginFunction = banner.originFunction;
//        maybeOriginFunction match {
//          case None => {
//            vfail("?") // what do we do when we want to stamp something with no origin function?
//          }
//          case Some(originFunction) => {
//            originFunction.body match {
//              case CodeBody1(block) => {
//                FunctionTemplar.evaluateOrdinaryLightFunctionFromNonCallForPrototype(
//                  env, temputs, originFunction)
//              }
//              case _ => {
//                vfail("?") // what do we do when we want to stamp an abstract function?
//                // val header =
//                //   FunctionTemplar.evaluateOrdinaryLightAbstractFunctionForHeader(env, temputs, originFunction)
//                // (temputs, header.toPrototype)
//              }
//            }
//          }
//        }
//      }
  }
}
