package net.verdagon.vale.templar.infer

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{ConstraintP, OwnP, ReadonlyP, ReadwriteP, ShareP, WeakP}
import net.verdagon.vale.scout.{RangeS, Environment => _, FunctionEnvironment => _, IEnvironment => _}
import net.verdagon.vale.templar.{CompileErrorExceptionT, INameT, IRuneT, NameTranslator, RangedInternalErrorT, SolverKindRuneT, Templar}
import net.verdagon.vale.templar.infer.infer._
import net.verdagon.vale.templar.templata.{Conversions, ITemplata, _}
import net.verdagon.vale.templar.types.{KindT, _}

import scala.collection.immutable.List

private[infer] trait IInfererEvaluatorDelegate[Env, State] {
  def lookupMemberTypes(
    state: State,
    kind: KindT,
    // This is here so that the predictor can just give us however many things
    // we expect.
    expectedNumMembers: Int
  ): Option[List[CoordT]]

  def getMutability(state: State, kind: KindT): MutabilityT

  def getAncestorInterfaceDistance(temputs: State, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT): (Option[Int])

  def getAncestorInterfaces(temputs: State, descendantCitizenRef: CitizenRefT): Set[InterfaceTT]

  def lookupTemplata(env: Env, range: RangeS, rune: INameT): ITemplata
  def lookupTemplata(profiler: IProfiler, env: Env, range: RangeS, name: IImpreciseNameStepA): ITemplata

  def getMemberCoords(state: State, structTT: StructTT): List[CoordT]

  def structIsClosure(state: State, structTT: StructTT): Boolean

  def resolveExactSignature(env: Env, state: State, range: RangeS, name: String, coords: List[CoordT]): PrototypeT
}

// Given enough user specified template params and param inputs, we should be able to
// infer everything.
// This class's purpose is to take those things, and see if it can figure out as many
// inferences as possible.

class InfererEvaluator[Env, State](
  profiler: IProfiler,
  templataTemplar: TemplataTemplarInner[Env, State],
  equator: InfererEquator[Env, State],
  delegate: IInfererEvaluatorDelegate[Env, State]) {

  // typeByRune only contains stuff that we need to solve.
  // If it's a rune thats already figured out and in the environment, dont
  // include it in typeByRune.
  private[infer] def solve(
    env: Env,
    state: State,
    initialRules: List[IRulexTR],
    invocationRange: RangeS,
    typeByRune: Map[IRuneT, ITemplataType],
    localRuneWithoutParameterRunes: Set[IRuneT],
    directInputs: Map[IRuneT, ITemplata],
    paramAtoms: List[AtomAP],
    maybeParamInputs: Option[List[ParamFilter]],
    checkAllRunesPresent: Boolean
  ): (IInferSolveResult) = {
    val inferences = InferencesBox(Inferences(typeByRune, Map(), Map()))

    // Feed into the system the things the user already specified.

      directInputs.foreach({
        case ((rune, directInputTemplata)) => {
          val expectedType = vassertSome(typeByRune.get(rune))
          if (directInputTemplata.tyype != expectedType) {
            return (InferSolveFailure(typeByRune, directInputs, maybeParamInputs, inferences.inferences, invocationRange, "Input for rune " + rune + " has type " + directInputTemplata.tyype + " that doesn't match expected type: " + expectedType, List.empty))
          }
          inferences.addConclusion(rune, directInputTemplata)
        }
      })

    // Now we'll try solving a bunch, just to see if there's any contradictions,
    // and if so bail out early.
    solveUntilSettled(env, state, initialRules, typeByRune, localRuneWithoutParameterRunes, inferences, invocationRange) match {
      case (isc @ InferEvaluateConflict(_, _, _, _)) => return (InferSolveFailure(typeByRune, directInputs, maybeParamInputs, inferences.inferences, invocationRange, "", List(isc)))
      case (InferEvaluateSuccess(_, deeplySatisfied)) => {
        // Don't care if its not deeply satisfied, because we'll try solving again soon.
        val _ = deeplySatisfied
      }
    }

    // Now we have template args the user specified, and we know there's no contradictions yet.

    // Next, we'll feed in the arguments that they used in the call.

    val rulesAndAddedRunesTypeByRuneFromParamInputs =
      maybeParamInputs match {
        case None => List.empty
        case Some(paramInputs) => {
          if (paramAtoms.size != paramInputs.size) {
            return InferSolveFailure(
              typeByRune,
              directInputs,
              maybeParamInputs,
              inferences.inferences,
              invocationRange,
              "Expected " + paramAtoms.size + " args but got " + paramInputs.size + "\n" +
              "Expected:\n" + paramAtoms.zipWithIndex.map({ case (paramAtom, i) => "  " + i + " " + paramAtom }).mkString("\n") + "\n" +
              "Got:\n" + paramInputs.zipWithIndex.map({ case (paramInput, i) => "  " + i + " " + paramInput }).mkString("\n"),
              List.empty)
          }
          paramAtoms.zip(paramInputs).zipWithIndex.map({
            case (((paramAtom, paramFilterInstance), paramIndex)) => {
              addParameterRules(state, inferences, invocationRange, paramAtom, paramFilterInstance, List(paramIndex)) match {
                case (iec @ InferEvaluateConflict(_, _, _, _), _) => {
                  return (InferSolveFailure(typeByRune, directInputs, maybeParamInputs, inferences.inferences, invocationRange, "Failed to add parameter " + paramIndex, List(iec)))
                }
                case (InferEvaluateSuccess(rules17, true), addedRunesTypeByRune) => (rules17, addedRunesTypeByRune)
              }
            }
          })
        }
      }
    val (rulesFromParamInputs, unflattenedAddedRunesTypeByRune) = rulesAndAddedRunesTypeByRuneFromParamInputs.unzip
    val addedRunesTypeByRune = unflattenedAddedRunesTypeByRune.foldLeft(Map[IRuneT, ITemplataType]())(_ ++ _)
    val combinedTypeByRune = typeByRune ++ addedRunesTypeByRune

    val rules = initialRules ++ rulesFromParamInputs.flatten
    val localRunes = localRuneWithoutParameterRunes ++ addedRunesTypeByRune.keySet

    // Now we'll try solving a bunch, just to see if there's any contradictions,
    // and if so bail.
    val deeplySatisfied =
      solveUntilSettled(env, state, rules, combinedTypeByRune, localRunes, inferences, invocationRange) match {
        case (isc @ InferEvaluateConflict(_, _, _, _)) => return (InferSolveFailure(combinedTypeByRune, directInputs, maybeParamInputs, inferences.inferences, invocationRange, "", List(isc)))
        case (InferEvaluateSuccess(_, ds)) => (ds)
      }

    if (checkAllRunesPresent) {
      val neededRunes = localRunes
      if ((neededRunes -- inferences.inferences.templatasByRune.keySet).nonEmpty) {
        val message = "Not enough to solve! Couldn't figure out: " + (neededRunes -- inferences.inferences.templatasByRune.keySet)
        return (InferSolveFailure(combinedTypeByRune, directInputs, maybeParamInputs, inferences.inferences, invocationRange, message, List.empty))
      }
    }
    if (!deeplySatisfied) {
      return (InferSolveFailure(combinedTypeByRune, directInputs, maybeParamInputs, inferences.inferences, invocationRange, "Not deeply satisfied!", List.empty))
    }

    (InferSolveSuccess(inferences.inferences))
  }

  // We aren't matching or evaluating anything here, we're just adding more rules depending on what
  // we find in the parameters.
  // Well, I suppose we are adding the argument possibilities (it and superclasses) to the inferences.
  // debt: make a rule that can do that for us. a implicitlyCastableTo rule or something?
  // debt: should we perhaps assemble these rules beforehand? maybe in the scout? it might be a templar concern,
  // is it? if it is, perhaps we can assemble these in some sort of pre-evaluate stage? maybe in the ruletyper,
  // since it's kind of like a compiler?
  // these aren't rules of the function, these are more rules that can help us call them and know what can
  // cast to things that can eventually call it.
  // Returns:
  // - result containing the new rules
  // - runes conjured up here, type by rune
  private def addParameterRules(
      state: State,
      inferences: InferencesBox,
      invocationRange: RangeS,
      paramAtom: AtomAP,
      paramFilterInstance: ParamFilter,
      paramLocation: List[Int]):
  // TODO: Don't use IInferEvaluateResult for this, because it has a deeplySatisfied member
  // which is n/a for this kind of thing.
  (IInferEvaluateResult[List[IRulexTR]], Map[IRuneT, ITemplataType]) = {
    val AtomAP(paramRange, _, patternVirtuality, patternCoordRuneA, maybePatternDestructure) = paramAtom
    val patternCoordRune2 = NameTranslator.translateRune(patternCoordRuneA)

    val (rulesFromType, runesAddedForType) =
      paramFilterInstance.tyype.kind match {
        case c: CitizenRefT => {
          val ancestorInterfaces = delegate.getAncestorInterfaces(state, c)
          val selfAndAncestors = List(c) ++ ancestorInterfaces
          val kindRune = SolverKindRuneT(patternCoordRune2)
          inferences.addPossibilities(kindRune, selfAndAncestors.map(KindTemplata))
          val rule =
            EqualsTR(
              paramRange,
              TemplexTR(RuneTT(paramRange, patternCoordRune2, CoordTemplataType)),
              ComponentsTR(
                paramRange,
                CoordTemplataType,
                List(
                  // This seems weird. We should probably remove this, see GAOFPS
                  TemplexTR(OwnershipTT(paramRange, Conversions.unevaluateOwnership(paramFilterInstance.tyype.ownership))),
                  TemplexTR(PermissionTT(paramRange, Conversions.unevaluatePermission(paramFilterInstance.tyype.permission))),
                  TemplexTR(RuneTT(paramRange, kindRune, KindTemplataType)))))
          (List(rule), Map[IRuneT, ITemplataType](kindRune -> KindTemplataType))
        }
        case _ => {
          inferences.templatasByRune.get(patternCoordRune2) match {
            case Some(existingOne) if existingOne != CoordTemplata(paramFilterInstance.tyype) => {
              return (
                InferEvaluateConflict(
                  inferences.inferences,
                  invocationRange,
                  "Incoming argument type doesnt match already known rune " + paramAtom.coordRune + " value. Had value " + existingOne + " but incoming arg was " + paramFilterInstance.tyype,
                  Nil),
                Map[IRuneT, ITemplataType]())
            }
            case _ =>
          }
          inferences.addConclusion(patternCoordRune2, CoordTemplata(paramFilterInstance.tyype))
          (List.empty, Map[IRuneT, ITemplataType]())
        }
      }
    val rulesFromVirtuality =
      (paramFilterInstance.virtuality, patternVirtuality) match {
        case (None, _) => List.empty
        case (Some(AbstractT$), Some(AbstractAP)) => List.empty
        case (Some(OverrideT(superInterface)), Some(OverrideAP(range, superInterfaceRune))) => {
          // We might already have this superInterface figured out.
          inferences.templatasByRune.get(NameTranslator.translateRune(superInterfaceRune)) match {
            case None => {
              val ancestorInterfaces = delegate.getAncestorInterfaces(state, superInterface)
              val selfAndAncestors = List(superInterface) ++ ancestorInterfaces
              inferences.addPossibilities(
                NameTranslator.translateRune(superInterfaceRune),
                selfAndAncestors.map(KindTemplata))
              List.empty
            }
            case Some(existingInference) => {
              vassert(existingInference == KindTemplata(superInterface))
              List.empty
            }
          }
        }
        case (paramFilterVirtuality, patternVirtuality) => {
          return (
            InferEvaluateConflict(
              inferences.inferences,
              invocationRange,
              "Param filter's virtuality and pattern's virtualities didnt match:\n" + paramFilterVirtuality + "\nand:\n" + patternVirtuality,
              Nil),
            Map())
        }
      }
    vcurious(rulesFromVirtuality == List.empty) // do no rules come from virtuality?
    val rulesFromPatternDestructure =
      maybePatternDestructure match {
        case None => List.empty
        case Some(patternDestructures) => {
          addDestructureRules(state, inferences, invocationRange, patternCoordRune2, paramFilterInstance.tyype, patternDestructures, paramLocation) match {
            case iec @ InferEvaluateConflict(_, _, _, _) => return (iec, Map())
            case InferEvaluateSuccess(r, _) => r
          }
        }
      }
    (InferEvaluateSuccess(rulesFromType ++ rulesFromVirtuality ++ rulesFromPatternDestructure, true), runesAddedForType)
  }

  private def addDestructureRules(
    state: State,
    inferences: InferencesBox,
    invocationRange: RangeS,
    expectedContainerTypeRune: IRuneT,
    incomingContainerCoord: CoordT,
    patternDestructures: List[AtomAP],
    paramLocation: List[Int]
  ): IInferEvaluateResult[List[IRulexTR]] = {
    val incomingMembers =
      getMemberCoords(state, inferences, incomingContainerCoord.kind, invocationRange, patternDestructures.size) match {
        case iec@InferEvaluateConflict(_, _, _, _) => return InferEvaluateConflict(inferences.inferences, invocationRange, "Failed getting incomingMembers for destructure", List(iec))
        case InferEvaluateSuccess(m, true) => m
      }
    // Should have already been checked in getMemberCoords
    vassert(incomingMembers.size == patternDestructures.size)

    val rules40 =
      patternDestructures.zip(incomingMembers).zipWithIndex.flatMap({
        // debt: rename this patternDestructure to something. we need a term for an atom
        // that comes from a destructure.
        // debt: rename atom. probably just to pattern again?
        case (((patternDestructure, incomingMemberCoord), memberIndex)) => {
          val AtomAP(range, _, patternVirtuality, patternCoordRuneA, maybePatternDestructure) = patternDestructure
          vassert(patternVirtuality.isEmpty) // We dont yet have virtuals in patterns... BUT OUR DAY WILL COME
          val patternCoordRune2 = NameTranslator.translateRune(patternCoordRuneA)

          val memberCoord =
            inferences.templatasByRune.get(patternCoordRune2) match {
              case Some(CoordTemplata(r)) if r != incomingMemberCoord => {
                return InferEvaluateConflict(
                  inferences.inferences,
                  range,
                  "Incoming argument type doesnt match already known rune " + patternDestructure.coordRune + " value. Had value " + r + " but incoming arg was " + incomingMemberCoord,
                  Nil)
              }
              case Some(CoordTemplata(r)) => r
              case Some(_) => vwat()
              case None => {
                inferences.addConclusion(patternCoordRune2, CoordTemplata(incomingMemberCoord))
                incomingMemberCoord
              }
            }

          val rulesFromPatternDestructure =
            maybePatternDestructure match {
              case None => List.empty
              case Some(patternDestructures) => {
                val memberLocation = paramLocation :+ memberIndex
                addDestructureRules(state, inferences, range, patternCoordRune2, memberCoord, patternDestructures, memberLocation) match {
                  case iec @ InferEvaluateConflict(_, _, _, _) => {
                    return InferEvaluateConflict(inferences.inferences, range, "Failed to add parameter " + memberLocation.mkString("/"), List(iec))
                  }
                  case InferEvaluateSuccess(r, _) => r
                }
              }
            }
          rulesFromPatternDestructure
        }
      })
    InferEvaluateSuccess(rules40, true)
  }

  private def solveUntilSettled(
    env: Env,
    state: State,
    rules: List[IRulexTR],
    typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    invocationRange: RangeS,
  ): (IInferEvaluateResult[Unit]) = {
    val initialInferences = inferences.inferences
    val deeplySatisfied =
      rules.foldLeft((true))({
        case ((deeplySatisfiedSoFar), rule) => {
          evaluateRule(env, state, typeByRune, localRunes, inferences, rule) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, invocationRange, "", List(iec)))
            case (InferEvaluateUnknown(thisDeeplySatisfied)) => {
              (deeplySatisfiedSoFar && thisDeeplySatisfied)
            }
            case (InferEvaluateSuccess(_, thisDeeplySatisfied)) => {
              (deeplySatisfiedSoFar && thisDeeplySatisfied)
            }
          }
        }
      })

    if (inferences.inferences != initialInferences) {
      // Things have not settled, we made some sort of progress in this last iteration.
      // Keep going.
      solveUntilSettled(env, state, rules, typeByRune, localRunes, inferences, invocationRange)
    } else {
      // No need to do one last match, because we just did an entire iteration where nothing changed.

      // Now that things are settled, see if there's any possibilities open.
      // Pick any of the possibility sets, and try all of the options.
      inferences.possibilitiesByRune.keySet.headOption match {
        case Some(rune) => {
          val possibilities = inferences.pop(rune)
//          println("possibilities to try:\n" + possibilities.mkString("\n"))

          possibilities match {
            case Nil => vwat()
            case List(onlyPossibility) => {
              inferences.addConclusion(rune, onlyPossibility)
              solveUntilSettled(env, state, rules, typeByRune, localRunes, inferences, invocationRange)
            }
            case _ => {
              val maybeInitialSuccessfulUniverse: Option[IInferEvaluateResult[Unit]] = None
              val (failedUniversesFailures, maybeSuccessfulUniverse) =
                possibilities.foldLeft((List[InferEvaluateConflict[Unit]](), maybeInitialSuccessfulUniverse))({
                  case ((previousFailures, Some(iss @ InferEvaluateSuccess(_, _))), _) => {
                    (previousFailures, Some(iss))
                  }
                  case ((previousFailures, None), possibility) => {
                    // IMPORTANT: Here we are making an alternate universe for trying out an inference,
                    // BUT WE ARE NOT making an alternate State! That means that we are
                    // NOT MAKING AN ALTERNATE State/Temputs!
                    // This is good because one attempt's Temputs can be reused for the next,
                    // but a little nerve-wracking because if something in the language design
                    // causes compilation to be non-idempotent, an alternate universe can put something
                    // weird into the Temputs forever.
//                    println("trying universe for " + rune + ": " + possibility)
                    val alternateUniverseInferencesBox = InferencesBox(inferences.inferences)
                    alternateUniverseInferencesBox.addConclusion(rune, possibility)
                    solveUntilSettled(env, state, rules, typeByRune, localRunes, alternateUniverseInferencesBox, invocationRange) match {
                      case (isf @ InferEvaluateConflict(_, _, _, _)) => {
//                        println("it didnt work! reason: " + isf)
                        (isf :: previousFailures, None)
                      }
                      case (iss @ InferEvaluateSuccess(_, _)) => {
//                        println("it worked!")
                        inferences.inferences = alternateUniverseInferencesBox.inferences
                        (List.empty, Some(iss))
                      }
                    }
                  }
                })
              maybeSuccessfulUniverse match {
                case None => (InferEvaluateConflict(inferences.inferences, invocationRange, "No options for " + rune + " worked!", failedUniversesFailures))
                case Some(successfulUniverse) => (successfulUniverse)
              }
            }
          }
        }
        case None => {
          // No possibilities, we have nothing left to explore, bail!
          (InferEvaluateSuccess((), deeplySatisfied))
        }
      }

    }
  }

  private[infer] def evaluateRule(
    env: Env,
    state: State,
      typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    rule: IRulexTR
  ): (IInferEvaluateResult[ITemplata]) = {
    rule match {
//      case r @ CoordListTR(_) => evaluateCoordListRule(env, state, typeByRune, localRunes, inferences, r)
      case r @ EqualsTR(_, _, _) => evaluateEqualsRule(env, state, typeByRune, localRunes, inferences, r)
      case r @ IsaTR(_, _, _) => evaluateIsaRule(env, state, typeByRune, localRunes, inferences, r)
      case r @ OrTR(_, _) => evaluateOrRule(env, state, typeByRune, localRunes, inferences, r)
      case r @ ComponentsTR(_, _, _) => evaluateComponentsRule(env, state, typeByRune, localRunes, inferences, r)
      case TemplexTR(templex) => evaluateTemplex(env, state, typeByRune, localRunes, inferences, templex)
      case r @ CallTR(_, _, _, _) => evaluateRuleCall(env, state, typeByRune, localRunes, inferences, r)
    }
  }

  private[infer] def evaluateRules(
    env: Env,
    state: State,
      typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    range: RangeS,
    rules: List[IRulexTR],
  ): (IInferEvaluateResult[List[ITemplata]]) = {
    val initialResult: IInferEvaluateResult[List[ITemplata]] =
      InferEvaluateSuccess(List.empty, true)
    rules.zipWithIndex.foldLeft((initialResult))({
      case ((InferEvaluateUnknown(deeplySatisfiedSoFar)), (rule, index)) => {
        evaluateRule(env, state, typeByRune, localRunes, inferences, rule) match {
          case (iec @ InferEvaluateConflict(_, _, _, _)) => {
            return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating rule index " + index, List(iec)))
          }
          case (InferEvaluateUnknown(deeplySatisfied)) => {
            (InferEvaluateUnknown(deeplySatisfiedSoFar && deeplySatisfied))
          }
          case (InferEvaluateSuccess(result, deeplySatisfied)) => {
            // Throw it away; since one is unknown theyre all unknown
            val _ = result
            (InferEvaluateUnknown(deeplySatisfiedSoFar && deeplySatisfied))
          }
        }
      }
      case ((InferEvaluateSuccess(resultsSoFar, deeplySatisfiedSoFar)), (rule, index)) => {
        evaluateRule(env, state, typeByRune, localRunes, inferences, rule) match {
          case (iec @ InferEvaluateConflict(_, _, _, _)) => {
            return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating rule index " + index, List(iec)))
          }
          case (InferEvaluateUnknown(deeplySatisfied)) => {
            (InferEvaluateUnknown(deeplySatisfiedSoFar && deeplySatisfied))
          }
          case (InferEvaluateSuccess(result, deeplySatisfied)) => {
            (InferEvaluateSuccess(resultsSoFar :+ result, deeplySatisfiedSoFar && deeplySatisfied))
          }
        }
      }
    })
  }

//  private[infer] def evaluateCoordListRule(
//      env: Env,
//      state: State,
//      runes: Set[IRune2],
//      inferences: InferencesBox,
//      rule: CoordListTR):
//  IInferEvaluateResult[CoordListTemplata] = {
//    val CoordListTR(coordRules) = rule
//
//    evaluateRules(env, state, runes, inferences, coordRules) match {
//      case InferEvaluateSuccess(templatas, deeplySatisfied) => {
//        val coords = templatas.map({ case CoordTemplata(c) => c })
//        InferEvaluateSuccess(CoordListTemplata(coords), deeplySatisfied)
//      }
//      case InferEvaluateUnknown(deeplySatisfied) => InferEvaluateUnknown(deeplySatisfied)
//      case imc @ InferEvaluateConflict(_, _, _, _) => InferEvaluateConflict(inferences.inferences, range, "Failed evaluating coord list", List(imc))
//    }
//  }

  private[infer] def evaluateRuleCall(
    env: Env,
    state: State,
      typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    ruleCall: CallTR
  ): (IInferEvaluateResult[ITemplata]) = {
    val CallTR(range, name, argumentRules, resultType) = ruleCall

    name match {
//      case "ownership" => {
//        checkArgs(List(CoordTypeTR), argTemplatas)
//        val List(CoordTemplata(coord)) = argTemplatas
//        (InferEvaluateSuccess(OwnershipTemplata(coord.ownership)))
//      }
//      case "mutability" => {
//        checkArgs(List(KindTypeTR), argTemplatas)
//        val List(KindTemplata(kind)) = argTemplatas
//        val mutability = delegate.getMutability(state, kind)
//        (InferEvaluateSuccess(MutabilityTemplata(mutability)))
//      }
      case "toRef" => {
        val (argTemplatas, deeplySatisfied) =
          evaluateRules(env, state, typeByRune, localRunes, inferences, range, argumentRules) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => {
              return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating CallAR arguments", List(iec)))
            }
            case (InferEvaluateUnknown(argDeeplySatisfied)) => {
              // Doesn't matter if the arg is deeply satisfied because this rule itself is not satisfied.
              val _ = argDeeplySatisfied
              val deeplySatisfied = false
//              println("toRef unsatisfied")
              return (InferEvaluateUnknown(deeplySatisfied))
            }
            case (InferEvaluateSuccess(arguments, ds)) => {
              (arguments, ds)
            }
          }

        val List(KindTemplata(kind)) = argTemplatas
        val coord = templataTemplar.pointifyKind(state, kind, OwnT)
        (InferEvaluateSuccess(CoordTemplata(coord), deeplySatisfied))
      }
      case "passThroughIfConcrete" => {
        val (argTemplatas, deeplySatisfied) =
          evaluateRules(env, state, typeByRune, localRunes, inferences, range, argumentRules) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => {
              return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating CallAR arguments", List(iec)))
            }
            case (InferEvaluateUnknown(argDeeplySatisfied)) => {
              // Doesn't matter if the arg is deeply satisfied because this rule itself is not satisfied.
              val _ = argDeeplySatisfied
              val deeplySatisfied = false
//              println("passThroughIfConcrete unsatisfied")
              return (InferEvaluateUnknown(deeplySatisfied))
            }
            case (InferEvaluateSuccess(arguments, ds)) => {
              (arguments, ds)
            }
          }
        val List(templata) = argTemplatas
        templata match {
          case k @ KindTemplata(StructTT(_) | PackTT(_, _) | TupleTT(_, _) | StaticSizedArrayTT(_, _) | RuntimeSizedArrayTT(_)) => {
            (InferEvaluateSuccess(k, deeplySatisfied))
          }
          case _ => return (InferEvaluateConflict(inferences.inferences, range, "passThroughIfConcrete expected concrete kind, but got " + templata, List.empty))
        }
      }
      case "passThroughIfInterface" => {
        val (argTemplatas, deeplySatisfied) =
          evaluateRules(env, state, typeByRune, localRunes, inferences, range, argumentRules) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => {
              return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating CallAR arguments", List(iec)))
            }
            case (InferEvaluateUnknown(argDeeplySatisfied)) => {
              // Doesn't matter if the arg is deeply satisfied because this rule itself is not satisfied.
              val _ = argDeeplySatisfied
              val deeplySatisfied = false
//              println("passThroughIfInterface unsatisfied")
              return (InferEvaluateUnknown(deeplySatisfied))
            }
            case (InferEvaluateSuccess(arguments, ds)) => {
              (arguments, ds)
            }
          }
        val List(templata) = argTemplatas
        templata match {
          case k @ KindTemplata(InterfaceTT(_)) => {
            (InferEvaluateSuccess(k, deeplySatisfied))
          }
          case _ => {
            return (InferEvaluateConflict(inferences.inferences, range, "passThroughIfInterface expected interface kind, but got " + templata, List.empty))
          }
        }
      }
      case "passThroughIfStruct" => {
        val (argTemplatas, deeplySatisfied) =
          evaluateRules(env, state, typeByRune, localRunes, inferences, range, argumentRules) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => {
              return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating CallAR arguments", List(iec)))
            }
            case (InferEvaluateUnknown(argDeeplySatisfied)) => {
              // Doesn't matter if the arg is deeply satisfied because this rule itself is not satisfied.
              val _ = argDeeplySatisfied
              val deeplySatisfied = false
//              println("passThroughIfStruct unsatisfied")
              return (InferEvaluateUnknown(deeplySatisfied))
            }
            case (InferEvaluateSuccess(arguments, ds)) => {
              (arguments, ds)
            }
          }
        val List(templata) = argTemplatas
        templata match {
          case k @ KindTemplata(StructTT(_)) => {
            (InferEvaluateSuccess(k, deeplySatisfied))
          }
          case _ => {
            return (InferEvaluateConflict(inferences.inferences, range, "passThroughIfStruct expected struct kind, but got " + templata, List.empty))
          }
        }
      }
      case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Unknown function \"" + name + "\"!"))
    }
  }

  private[infer] def evaluateTemplex(
    env: Env,
    state: State,
      typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    ruleTemplex: ITemplexT
  ): (IInferEvaluateResult[ITemplata]) = {
    ruleTemplex match {
      case StringTT(_, value) => InferEvaluateSuccess(StringTemplata(value), true)
      case IntTT(_, value) => InferEvaluateSuccess(IntegerTemplata(value), true)
      case BoolTT(_, value) => InferEvaluateSuccess(BooleanTemplata(value), true)
      case MutabilityTT(_, mutability) => {
        (InferEvaluateSuccess(MutabilityTemplata(Conversions.evaluateMutability(mutability)), true))
      }
      case PermissionTT(_, permission) => {
        (InferEvaluateSuccess(PermissionTemplata(Conversions.evaluatePermission(permission)), true))
      }
      case LocationTT(_, location) => {
        (InferEvaluateSuccess(LocationTemplata(Conversions.evaluateLocation(location)), true))
      }
      case OwnershipTT(_, ownership) => {
        (InferEvaluateSuccess(OwnershipTemplata(Conversions.evaluateOwnership(ownership)), true))
      }
      case VariabilityTT(_, variability) => {
        (InferEvaluateSuccess(VariabilityTemplata(Conversions.evaluateVariability(variability)), true))
      }
      case NameTT(range, name, expectedType) => {
        val templata =
          templataTemplar.lookupTemplata(env, state, range, name, expectedType)
        (InferEvaluateSuccess(templata, true))
      }
      case AbsoluteNameTT(range, name, expectedType) => {
        val templata =
          templataTemplar.lookupTemplata(env, state, range, NameTranslator.translateNameStep(name), expectedType)
        (InferEvaluateSuccess(templata, true))
      }
      case RuneTT(range, rune, expectedType) => {
        if (localRunes.contains(rune)) {
          inferences.templatasByRune.get(rune) match {
            case Some(templata) => {
              if (templata.tyype != expectedType) {
                return (InferEvaluateConflict(inferences.inferences, range, "Rune " + rune + " is of type " + expectedType + ", but it received a " + templata.tyype + ", specifically " + templata, List.empty))
              }
              (InferEvaluateSuccess(templata, true))
            }
            case None => {
//              println("RuneAT unsatisfied")
              (InferEvaluateUnknown(false))
            }
          }
        } else {
          // We might be grabbing a rune from a parent environment thats already solved,
          // such as when we do spaceship.fly() in TMRE.
          val templata = delegate.lookupTemplata(env, range, rune)
          if (templata.tyype != expectedType) {
            return (InferEvaluateConflict(inferences.inferences, range, "Rune " + rune + " is of type " + expectedType + ", but it received a " + templata.tyype + ", specifically " + templata, List.empty))
          }
          (InferEvaluateSuccess(templata, true))
        }
      }
      case InterpretedTT(range, targetOwnership, targetPermission, innerKindRule) => {
        evaluateTemplex(env, state, typeByRune, localRunes, inferences, innerKindRule) match {
          case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "bogglewogget", List(iec)))
          case (InferEvaluateUnknown(innerCoordDeeplySatisfied)) => {
            // If we don't know the inner coord, we can't verify that the ownership is compatible with the inner kind.
            // For example, we can't do a borrow of something that's already a borrow or a weak.
            val _ = innerCoordDeeplySatisfied
            val deeplySatisfied = false
//            println("InterpretedAT unsatisfied")

            (InferEvaluateUnknown(deeplySatisfied))
          }
          case (InferEvaluateSuccess(CoordTemplata(CoordT(innerCoordOwnership, innerCoordPermission, innerCoordKind)), innerCoordDeeplySatisfied)) => {

            val resultingOwnership =
              (innerCoordOwnership, targetOwnership) match {
                case (OwnT, ShareP) => return (InferEvaluateConflict(inferences.inferences, range, "Expected a share, but was an own!", List.empty))
                case (OwnT, OwnP) => OwnT // No change, allow it
                case (OwnT, ConstraintP) => ConstraintT // Can borrow an own, allow it
                case (OwnT, WeakP) => WeakT // Can weak an own, allow it
                case (ConstraintT, ShareP) => return (InferEvaluateConflict(inferences.inferences, range, "Expected a share, but was a borrow!", List.empty))
                case (ConstraintT, OwnP) => OwnT // Can turn a borrow into an own, allow it
                case (ConstraintT, ConstraintP) => ConstraintT // No change, allow it
                case (ConstraintT, WeakP) => WeakT // Can weak a borrow, allow it
                case (WeakT, ShareP) => return (InferEvaluateConflict(inferences.inferences, range, "Expected a share, but was a weak!", List.empty))
                case (WeakT, OwnP) => return (InferEvaluateConflict(inferences.inferences, range, "Expected a own, but was a weak!", List.empty))
                case (WeakT, ConstraintP) => return (InferEvaluateConflict(inferences.inferences, range, "Expected a borrow, but was a weak!", List.empty))
                case (WeakT, WeakP) => WeakT // No change, allow it
                case (ShareT, OwnP) => ShareT // Can own a share, just becomes another share.
                case (ShareT, ConstraintP) => ShareT // Can borrow a share, just becomes another share.
                case (ShareT, WeakP) => return (InferEvaluateConflict(inferences.inferences, range, "Expected a weak, but was a share!", List.empty)) // Cant get a weak ref to a share because it doesnt have lock().
                case (ShareT, ShareP) => ShareT // No change, allow it
              }

            val resultingPermission =
              if (innerCoordOwnership == ShareT) {
                if (targetPermission == ReadwriteP) {
                  // It would technically be *weird* to make a Readwrite reference to an immutable, but it happens
                  // accidentally as part of making an owning reference to something, like with ^T. Using ^T in a rule
                  // but handing in a share is a pretty reasonable thing to happen, so let's let it slide.
                }
                ReadonlyT
              } else {
                // For mutables, we can turn a &T into a &!T, or vice versa, or anything else.
                Conversions.evaluatePermission(targetPermission)
              }

            // If we got here then the ownership and mutability were compatible.
            val satisfied = true
            val deeplySatisfied = innerCoordDeeplySatisfied && satisfied

            (InferEvaluateSuccess(CoordTemplata(CoordT(resultingOwnership, resultingPermission, innerCoordKind)), deeplySatisfied))
          }
        }
      }
      case CallTT(range, templateRule, templexesT, callResultType) => {

        // it should be a template that results in a `tyype`

        val (maybeTemplateTemplata, templateDeeplySatisfied) =
          evaluateTemplex(env, state, typeByRune, localRunes, inferences, templateRule) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "bogglewogget", List(iec)))
            case (InferEvaluateUnknown(ds)) => (None, ds)
            case (InferEvaluateSuccess(templata, ds)) => (Some(templata), ds)
          }

        val (maybeArgTemplatas, argsDeeplySatisfied) =
          evaluateTemplexes(env, state, typeByRune, localRunes, inferences, range, templexesT) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => {
              return (InferEvaluateConflict(inferences.inferences, range, "Failed to evaluate CallAT arguments", List(iec)))
            }
            case (InferEvaluateUnknown(ds)) => {
              (None, ds)
            }
            case (InferEvaluateSuccess(argTemplatas, ds)) => {
              (Some(argTemplatas), ds)
            }
          }

        (maybeTemplateTemplata, maybeArgTemplatas) match {
          case (None, _) => {
//            println("CallAT 1 unsatisfied")
            (InferEvaluateUnknown(false))
          }
          case (_, None) => {
//            println("CallAT 2 unsatisfied")
            (InferEvaluateUnknown(false))
          }
          case (Some(it @ InterfaceTemplata(_, _)), Some(listOfArgTemplatas)) => {
            val result =
              templataTemplar.evaluateInterfaceTemplata(state, range, it, listOfArgTemplatas, callResultType)
            (InferEvaluateSuccess(result, templateDeeplySatisfied && argsDeeplySatisfied))
          }
          case (Some(st @ StructTemplata(_, _)), Some(listOfArgTemplatas)) => {
            val result =
              templataTemplar.evaluateStructTemplata(state, range, st, listOfArgTemplatas, callResultType)
            (InferEvaluateSuccess(result, templateDeeplySatisfied && argsDeeplySatisfied))
          }
          case (Some(btt @ ArrayTemplateTemplata()), Some(listOfArgTemplatas)) => {
            val result =
              templataTemplar.evaluateBuiltinTemplateTemplata(env, state, range, btt, listOfArgTemplatas, callResultType)
            (InferEvaluateSuccess(result, templateDeeplySatisfied && argsDeeplySatisfied))
          }
          case (_, _) => {
            vcurious() // it feels sfinae-ey
            (InferEvaluateUnknown(vimpl()))
          }
        }
      }
      case PrototypeTT(_, _, _, _) => {
        vfail("Unimplemented")
      }
      case CoordListTT(range, memberTemplexes) => {
        evaluateTemplexes(env, state, typeByRune, localRunes, inferences, range, memberTemplexes) match {
          case (iec @ InferEvaluateConflict(_, _, _, _)) => {
            return (InferEvaluateConflict(inferences.inferences, range, "Failed to evaluate CoordListTT arguments", List(iec)))
          }
          case (InferEvaluateUnknown(deeplySatisfied)) => InferEvaluateUnknown(deeplySatisfied)
          case (InferEvaluateSuccess(memberTemplatas, deeplySatisfied)) => {
            val memberCoords = memberTemplatas.collect({ case CoordTemplata(coord) => coord })
            if (memberCoords.size != memberTemplatas.size) {
              throw CompileErrorExceptionT(RangedInternalErrorT(range, "Packs can only take coords!"))
            }
            InferEvaluateSuccess(CoordListTemplata(memberCoords), deeplySatisfied)
          }
        }
      }
      case RepeaterSequenceTT(range, mutabilityTemplex, variabilityTemplex, sizeTemplex, elementTemplex, resultType) => {
        val (maybeMutability, mutabilityDeeplySatisfied) =
          evaluateTemplex(env, state, typeByRune, localRunes, inferences, mutabilityTemplex) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Failed to evaluate mutability", List(iec)))
            case (InferEvaluateUnknown(ds)) => (None, ds)
            case (InferEvaluateSuccess(MutabilityTemplata(mutability), ds)) => (Some(mutability), ds)
            case (InferEvaluateSuccess(notInt, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Mutability isn't a mutability: " + notInt, Nil))
          }
        val (maybeVariability, variabilityDeeplySatisfied) =
          evaluateTemplex(env, state, typeByRune, localRunes, inferences, variabilityTemplex) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Failed to evaluate variability", List(iec)))
            case (InferEvaluateUnknown(ds)) => (None, ds)
            case (InferEvaluateSuccess(VariabilityTemplata(variability), ds)) => (Some(variability), ds)
            case (InferEvaluateSuccess(notInt, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Variability isn't a variability: " + notInt, Nil))
          }
        val (maybeSize, sizeDeeplySatisfied) =
          evaluateTemplex(env, state, typeByRune, localRunes, inferences, sizeTemplex) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Failed to evaluate element", List(iec)))
            case (InferEvaluateUnknown(ds)) => (None, ds)
            case (InferEvaluateSuccess(IntegerTemplata(size), ds)) => (Some(size), ds)
            case (InferEvaluateSuccess(notCoord, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Element isn't a coord: " + notCoord, Nil))
          }
        val (maybeElement, elementDeeplySatisfied) =
          evaluateTemplex(env, state, typeByRune, localRunes, inferences, elementTemplex) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Failed to evaluate element", List(iec)))
            case (InferEvaluateUnknown(ds)) => (None, ds)
            case (InferEvaluateSuccess(CoordTemplata(coord), ds)) => (Some(coord), ds)
            case (InferEvaluateSuccess(notCoord, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Element isn't a coord: " + notCoord, Nil))
          }

        (maybeMutability, maybeVariability, maybeSize, maybeElement) match {
          case (Some(mutability), Some(variability), Some(size), Some(element)) => {
            val tuple =
              templataTemplar.getStaticSizedArrayKind(env, state, range, mutability, variability, size.toInt, element, resultType)
            (InferEvaluateSuccess(tuple, mutabilityDeeplySatisfied && variabilityDeeplySatisfied && sizeDeeplySatisfied && elementDeeplySatisfied))
          }
          case _ => {
            // Not satisfied because there's an implicit constraint that these things together make up a valid repeater sequence.
            val deeplySatisfied = false
//            println("Repeater unsatisfied")
            (InferEvaluateUnknown(deeplySatisfied))
          }
        }
      }
      case ManualSequenceTT(range, elements, resultType) => {
        val (maybeTemplatas, elementsDeeplySatisfied) =
          evaluateTemplexes(env, state, typeByRune, localRunes, inferences, range, elements) match {
            case (iec @ InferEvaluateConflict(_, _, _, _)) => {
              return (InferEvaluateConflict(inferences.inferences, range, "Failed to evaluate CallAT arguments", List(iec)))
            }
            case (InferEvaluateUnknown(ds)) => {
              (None, ds)
            }
            case (InferEvaluateSuccess(argTemplatas, ds)) => {
              (Some(argTemplatas), ds)
            }
          }
        maybeTemplatas match {
          case None => {
            val deeplySatisfied = false
            InferEvaluateUnknown(deeplySatisfied)
          }
          case Some(templatas) => {
            val coords = templatas.collect({ case CoordTemplata(coord) => coord })
            if (coords.size != templatas.size) {
              throw CompileErrorExceptionT(RangedInternalErrorT(range, "Not all templatas given to tuple were coords!"))
            }
            val tuple = templataTemplar.getTupleKind(env, state, range, coords, resultType)
            (InferEvaluateSuccess(tuple, elementsDeeplySatisfied))
          }
        }
      }
    }
  }

  private[infer] def evaluateTemplexes(
    env: Env,
    state: State,
      typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    range: RangeS,
    templexes: List[ITemplexT]):
  (IInferEvaluateResult[List[ITemplata]]) = {
    val initialFoldyThing: IInferEvaluateResult[List[ITemplata]] =
      InferEvaluateSuccess(List[ITemplata](), true)
    templexes.zipWithIndex.foldLeft((initialFoldyThing))({
      case ((InferEvaluateSuccess(resultsSoFar, deeplySatisfiedSoFar)), (maybeArgRule, index)) => {
        evaluateTemplex(env, state, typeByRune, localRunes, inferences, maybeArgRule) match {
          case (iec @ InferEvaluateConflict(_, _, _, _)) => {
            return (InferEvaluateConflict[List[ITemplata]](inferences.inferences, range, "Failed to evaluate templex " + index, List(iec)))
          }
          case (InferEvaluateUnknown(deeplySatisfied)) => {
            (InferEvaluateUnknown(deeplySatisfiedSoFar && deeplySatisfied))
          }
          case (InferEvaluateSuccess(result, deeplySatisfied)) => {
            (InferEvaluateSuccess(resultsSoFar :+ result, deeplySatisfiedSoFar && deeplySatisfied))
          }
        }
      }
      case ((InferEvaluateUnknown(deeplySatisfiedSoFar)), (maybeArgRule, index)) => {
        evaluateTemplex(env, state, typeByRune, localRunes, inferences, maybeArgRule) match {
          case (iec @ InferEvaluateConflict(_, _, _, _)) => {
            return (InferEvaluateConflict[List[ITemplata]](inferences.inferences, range, "Failed to evaluate templex " + index, List(iec)))
          }
          case (InferEvaluateUnknown(deeplySatisfied)) => {
            (InferEvaluateUnknown(deeplySatisfiedSoFar && deeplySatisfied))
          }
          case (InferEvaluateSuccess(result, deeplySatisfied)) => {
            // Throw it away; since there was one unknown the entire thing's unknown.
            val _ = result
            (InferEvaluateUnknown(deeplySatisfiedSoFar && deeplySatisfied))
          }
        }
      }
    })
  }

  private[infer] def evaluateEqualsRule(
    env: Env,
    state: State,
      typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    rule: EqualsTR
  ): (IInferEvaluateResult[ITemplata]) = {
    val EqualsTR(range, leftRule, rightRule) = rule

    evaluateRule(env, state, typeByRune, localRunes, inferences, leftRule) match {
      case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating left rule!", List(iec)))
      case (InferEvaluateUnknown(leftEvalDeeplySatisfied)) => {
        evaluateRule(env, state, typeByRune, localRunes, inferences, rightRule) match {
          case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating right rule!", List(iec)))
          case (InferEvaluateUnknown(rightDeeplySatisfied)) => {
            // Both sides are unknown, so return an unknown.

            // Doesn't matter if either side was deeply satisfied, because this equals itself isn't satisfied.
            val _ = leftEvalDeeplySatisfied
            val __ = rightDeeplySatisfied
            val deeplySatisfied = false
//            println("Equals 1 unsatisfied")

            (InferEvaluateUnknown(deeplySatisfied))
          }
          case (InferEvaluateSuccess(rightTemplata, rightDeeplySatisfied)) => {
            // Left is unknown, but right is known. Use the thing from the right
            // and match it against the left.
            val maybeResultH =
              makeMatcher().matchTemplataAgainstRulexTR(
                env, state, typeByRune, localRunes, inferences, rightTemplata, leftRule)
            maybeResultH match {
              case imc @ InferMatchConflict(_, _, _, _) => {
                // None from the match means something conflicted, bail!
                return (InferEvaluateConflict(inferences.inferences, range, "Failed to match known right against unknown left!", List(imc)))
              }
              case InferMatchSuccess(leftMatchDeeplySatisfied) => {
                // Doesn't matter if the left was deeply satisfied in eval, because it's more likely
                // that it was satisfied in the match.
                val _ = leftEvalDeeplySatisfied
                val deeplySatisfied = leftMatchDeeplySatisfied && rightDeeplySatisfied

                (InferEvaluateSuccess(rightTemplata, deeplySatisfied))
              }
            }
          }
        }
      }
      case (InferEvaluateSuccess(leftTemplata, leftDeeplySatisfied)) => {
        // Below, we match the known left (leftTemplata) against the rightRule.
        // Previously, we did something different:
        // - Try evaluating rightRule first
        // - If it came up known, do a simple comparison of the leftTemplata and the rightTemplata
        // - If it came up unknown, *then* do a match from leftTemplata to rightRule
        // However, this caused problems, see NMORFI. We were evaluating a lot of things we didn't have to.
        // It turns out, evaluating is expensive, and we should avoid it when possible, instead doing MDESOI
        // when at all possible.

        // Use the thing from the left and match it against the right.
        val maybeInferencesH =
          makeMatcher().matchTemplataAgainstRulexTR(
            env, state, typeByRune, localRunes, inferences, leftTemplata, rightRule)
        maybeInferencesH match {
          case imc @ InferMatchConflict(_, _, _, _) => {
            // None from the match means something conflicted, bail!
            return (InferEvaluateConflict(inferences.inferences, range, "Failed to match known left against the right side!\nLeft: " + leftTemplata + "\nRight rule: " + rightRule + "\n", List(imc)))
          }
          case InferMatchSuccess(rightMatchDeeplySatisfied) => {
            (InferEvaluateSuccess(leftTemplata, leftDeeplySatisfied && rightMatchDeeplySatisfied))
          }
        }
      }
    }
  }

  private[infer] def evaluateIsaRule(
    env: Env,
    state: State,
      typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    rule: IsaTR
  ): (IInferEvaluateResult[ITemplata]) = {
    val IsaTR(range, subRule, superRule) = rule

    val (maybeSub, subDeeplySatisfied) =
      evaluateRule(env, state, typeByRune, localRunes, inferences, subRule) match {
        case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating sub rule!", List(iec)))
        case (InferEvaluateUnknown(ds)) => (None, ds)
        case (InferEvaluateSuccess(subTemplata, ds)) => (Some(subTemplata), ds)
      }

    val (maybeConcept, conceptDeeplySatisfied) =
      evaluateRule(env, state, typeByRune, localRunes, inferences, superRule) match {
        case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating concept rule!", List(iec)))
        case (InferEvaluateUnknown(ds)) => (None, ds)
        case (InferEvaluateSuccess(subTemplata, ds)) => (Some(subTemplata), ds)
      }

    (maybeSub, maybeConcept) match {
      case (Some(KindTemplata(sub : CitizenRefT)), Some(KindTemplata(suuper : InterfaceTT))) => {
        val supers = delegate.getAncestorInterfaces(state, sub)

        if (supers.contains(suuper)) {
          val isaSatisfied = true
          val deeplySatisfied = subDeeplySatisfied && conceptDeeplySatisfied && isaSatisfied
          (InferEvaluateSuccess(KindTemplata(sub), deeplySatisfied))
        } else {
          return (InferEvaluateConflict(inferences.inferences, range, "Isa failed!\nSub: " + sub + "\nSuper: " + suuper, List.empty))
        }
      }
      case (Some(_), Some(_)) => vfail()
      case _ => {
//        println("conforms unsatisfied")
        (InferEvaluateUnknown(false))
      }
    }
  }


  private[infer] def evaluateOrRule(
    env: Env,
    state: State,
      typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    rule: OrTR
  ): (IInferEvaluateResult[ITemplata]) = {
    // We don't actually evaluate Ors, we only match against them.
    // For this reason, it doesn't make sense to have an or at the top level.
    // We just return unknown since we can't know which of the branches we're using.

    // We can't satisfy Or rules with evaluating, only with matching.
    val deeplySatisfied = false
//    println("or unsatisfied")

    (InferEvaluateUnknown(deeplySatisfied))
  }

  private[infer] def evaluateComponentsRule(
    env: Env,
    state: State,
      typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    rule: ComponentsTR
  ): (IInferEvaluateResult[ITemplata]) = {
    val ComponentsTR(range, _, components) = rule

    // We don't have a value from the rune, we just have the type. Try to evaluate the components.
    rule.tyype match {
      case KindTemplataType => {
        evaluateKindComponents(env, state, typeByRune, localRunes, inferences, range, components) match {
          case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating kind components!", List(iec)))
          case (InferEvaluateUnknown(ds)) => (InferEvaluateUnknown(ds))
          case (InferEvaluateSuccess(templataFromRune, ds)) => (InferEvaluateSuccess(templataFromRune, ds))
        }
      }
      case CoordTemplataType => {
        evaluateCoordComponents(env, state, typeByRune, localRunes, inferences, range, components) match {
          case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating coord components!", List(iec)))
          case (InferEvaluateUnknown(ds)) => (InferEvaluateUnknown(ds))
          case (InferEvaluateSuccess(templataFromRune, ds)) => (InferEvaluateSuccess(templataFromRune, ds))
        }
      }
      case PrototypeTemplataType => {
        evaluatePrototypeComponents(env, state, typeByRune, localRunes, inferences, range, components) match {
          case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "Failed evaluating coord components!", List(iec)))
          case (InferEvaluateUnknown(ds)) => (InferEvaluateUnknown(ds))
          case (InferEvaluateSuccess(templataFromRune, ds)) => (InferEvaluateSuccess(templataFromRune, ds))
        }
      }
      case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Can only destructure coords and kinds!"))
    }
  }

  private def evaluateCoordComponents(
    env: Env,
    state: State,
      typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    range: RangeS,
    components: List[IRulexTR]):
  (IInferEvaluateResult[ITemplata]) = {
    // Now we're going to try and evaluate all the components.
    // At the end, if we have values for every component, then we'll
    // assemble a shiny new coord out of them!
    components match {
      case List(ownershipRule, permissionRule, kindRule) => {
        val (maybeOwnership, ownershipDeeplySatisfied) =
          evaluateRule(env, state, typeByRune, localRunes, inferences, ownershipRule) match {
            case (iec@InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "floop", List(iec)))
            case (InferEvaluateUnknown(ds)) => (None, ds)
            case (InferEvaluateSuccess(templata, ds)) => {
              templata match {
                case OwnershipTemplata(ownership) => (Some(ownership), ds)
                case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "First component of Coord must be an ownership!"))
              }
            }
          }
        val (maybePermission, permissionDeeplySatisfied) =
          evaluateRule(env, state, typeByRune, localRunes, inferences, permissionRule) match {
            case (iec@InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "floop", List(iec)))
            case (InferEvaluateUnknown(ds)) => (None, ds)
            case (InferEvaluateSuccess(templata, ds)) => {
              templata match {
                case PermissionTemplata(permission) => (Some(permission), ds)
                case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "First component of Coord must be a permission!"))
              }
            }
          }
        val (maybeKind, kindDeeplySatisfied) =
          evaluateRule(env, state, typeByRune, localRunes, inferences, kindRule) match {
            case (iec@InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "sparklebark", List(iec)))
            case (InferEvaluateUnknown(ds)) => (None, ds)
            case (InferEvaluateSuccess(templata, ds)) => {
              templata match {
                case KindTemplata(kind) => (Some(kind), ds)
                case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Fourth component of Coord must be a kind!"))
              }
            }
          }
        val deeplySatisfied = ownershipDeeplySatisfied && permissionDeeplySatisfied && kindDeeplySatisfied
        (maybeOwnership, maybePermission, maybeKind) match {
          case (Some(ownership), Some(permission), Some(kind)) => {
            val newOwnership =
              if (delegate.getMutability(state, kind) == ImmutableT) ShareT
              else ownership
            val newPermission =
              if (delegate.getMutability(state, kind) == ImmutableT) ReadonlyT
              else permission
            (InferEvaluateSuccess(CoordTemplata(CoordT(newOwnership, newPermission, kind)), deeplySatisfied))
          }
          case _ => {
            // deeplySatisfied can still be true even if the result is unknown, see IEUNDS.
            (InferEvaluateUnknown(deeplySatisfied))
          }
        }
      }
      case _ => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Coords must have 3 components"))
      }
    }
  }

  private def evaluateKindComponents(
    env: Env,
    state: State,
      typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    range: RangeS,
    components: List[IRulexTR],
  ): (IInferEvaluateResult[ITemplata]) = {
    val List(mutabilityRule) = components
    evaluateRule(env, state, typeByRune, localRunes, inferences, mutabilityRule) match {
      case (iec@InferEvaluateConflict(_, _, _, _)) => (InferEvaluateConflict(inferences.inferences, range, "klippityklap", List(iec)))
      case (InferEvaluateUnknown(ds)) => (InferEvaluateUnknown(ds))
      case (InferEvaluateSuccess(_, deeplySatisfied)) => {
        // We have the mutability, but we can't know the entire kind just given a mutability.
        // Just hand upwards an unknown.
        (InferEvaluateUnknown(deeplySatisfied))
      }
    }
  }

  private def evaluatePrototypeComponents(
    env: Env,
    state: State,
    typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    inferences: InferencesBox,
    range: RangeS,
    components: List[IRulexTR]):
  (IInferEvaluateResult[ITemplata]) = {
    // Now we're going to try and evaluate all the components.
    // At the end, if we have values for every component, then we'll
    // assemble a shiny new coord out of them!
    vcheck(components.size == 3, "Prototypes must have 3 components (name, coord list, coord), supplied " + components.size)
    val List(nameRule, paramsRule, returnRule) = components

    val (maybeName, nameDeeplySatisfied) =
      evaluateRule(env, state, typeByRune, localRunes, inferences, nameRule) match {
        case (iec@InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "floop", List(iec)))
        case (InferEvaluateUnknown(ds)) => (None, ds)
        case (InferEvaluateSuccess(templata, ds)) => {
          templata match {
            case StringTemplata(name) => (Some(name), ds)
            case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "First component of Prototype must be a string!"))
          }
        }
      }
    val (maybeParams, paramsDeeplySatisfied) =
      evaluateRule(env, state, typeByRune, localRunes, inferences, paramsRule) match {
        case (iec@InferEvaluateConflict(_, _, _, _)) => return (InferEvaluateConflict(inferences.inferences, range, "floop", List(iec)))
        case (InferEvaluateUnknown(ds)) => (None, ds)
        case (InferEvaluateSuccess(templata, ds)) => {
          templata match {
            case CoordListTemplata(coords) => (Some(coords), ds)
            case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "First component of Coord must be an ownership!"))
          }
        }
      }

    (maybeName, maybeParams) match {
      case (Some(name), Some(params)) => {
        // the prototype components rule is kind of weird because it can figure out the whole prototype
        // from just the name and the params. So, we resolve it, and then once we get the prototype,
        // we can match its return value against the return value part of the components rule.

        val prot = delegate.resolveExactSignature(env, state, range, name, params)
        val retDeeplySatisfied =
          makeMatcher().matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, CoordTemplata(prot.returnType), returnRule) match {
            case imc@InferMatchConflict(_, _, _, _) => {
              // None from the match means something conflicted, bail!
              return (InferEvaluateConflict(inferences.inferences, range, s"Prot rule evaluated name ${name} and params ${params} and found a prototype with return value ${prot.returnType}, but failed to match it against the Prot rule's return rule.", List(imc)))
            }
            case InferMatchSuccess(ds) => ds
          }
        val deeplySatisfied = nameDeeplySatisfied && paramsDeeplySatisfied && retDeeplySatisfied
        (InferEvaluateSuccess(PrototypeTemplata(prot), deeplySatisfied))
      }
      case _ => {
        // If we get here, then we couldn't evaluate the name or the params. Let's evaluate the return
        // rule, just for kicks. Who knows, it could contain hints we could later use to evaluate
        // name or params, in some weird circuitous way.

        val retDeeplySatisfied =
          evaluateRule(env, state, typeByRune, localRunes, inferences, returnRule) match {
            case (iec@InferEvaluateConflict(_, _, _, _)) => return InferEvaluateConflict(inferences.inferences, range, "sparklebark", List(iec))
            case (InferEvaluateUnknown(retDeeplySatisfied)) => retDeeplySatisfied
            case (InferEvaluateSuccess(templata, retDeeplySatisfied)) => {
              // There's nothing really useful to do with this return coord, we can't use it to resolve a function
              // because functions are identified by name and params.
              val _ = templata
              retDeeplySatisfied
            }
          }
        val deeplySatisfied = nameDeeplySatisfied && paramsDeeplySatisfied && retDeeplySatisfied
        vcurious(!deeplySatisfied) // We only got here because name and params failed, so can this ever be true?

        // deeplySatisfied can still be true even if the result is unknown, see IEUNDS.
        InferEvaluateUnknown(deeplySatisfied)
      }
    }
  }

  def makeMatcher(): InfererMatcher[Env, State] = {
    new InfererMatcher(
      profiler,
      templataTemplar,
      equator,
      evaluateRule,
      new IInfererMatcherDelegate[Env, State] {
        override def getAncestorInterfaceDistance(temputs: State, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT) = {
          delegate.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
        }

        override def getMutability(state: State, kind: KindT): MutabilityT = {
          delegate.getMutability(state, kind)
        }

        override def lookupMemberTypes(state: State, kind: KindT, expectedNumMembers: Int): Option[List[CoordT]] = {
          delegate.lookupMemberTypes(state, kind, expectedNumMembers)
        }

        override def getAncestorInterfaces(temputs: State, descendantCitizenRef: CitizenRefT): Set[InterfaceTT] = {
          delegate.getAncestorInterfaces(temputs, descendantCitizenRef)
        }

        override def structIsClosure(state: State, structTT: StructTT): Boolean = {
          delegate.structIsClosure(state, structTT)
        }

        override def lookupTemplata(env: Env, range: RangeS, name: INameT): ITemplata = {
          delegate.lookupTemplata(env, range, name)
        }
        override def lookupTemplata(profiler: IProfiler, env: Env, range: RangeS, name: IImpreciseNameStepA): ITemplata = {
          delegate.lookupTemplata(profiler, env, range, name)
        }
      })
  }

  private[infer] def getMemberCoords(
    state: State,
    inferences: InferencesBox,
    kind: KindT,
    range: RangeS,
    // We hand this in because this is the number of pattern destructures they have.
    // This avoids a massive memory explosion if they hand us a million element array sequence.
    expectedNumMembers: Int):
  IInferEvaluateResult[List[CoordT]] = {
    kind match {
      case sr @ StructTT(_) => {
        val memberCoords = delegate.getMemberCoords(state, sr)
        if (memberCoords.size != expectedNumMembers) {
          return InferEvaluateConflict(inferences.inferences, range, "Expected something with " + expectedNumMembers + " members but received " + kind, List.empty)
        }
        InferEvaluateSuccess(memberCoords, true)
      }
      case PackTT(members, _) => {
        if (members.size != expectedNumMembers) {
          return InferEvaluateConflict(inferences.inferences, range, "Expected something with " + expectedNumMembers + " members but received " + kind, List.empty)
        }
        InferEvaluateSuccess(members, true)
      }
      case TupleTT(members, _) => {
        if (members.size != expectedNumMembers) {
          return InferEvaluateConflict(inferences.inferences, range, "Expected something with " + expectedNumMembers + " members but received " + kind, List.empty)
        }
        InferEvaluateSuccess(members, true)
      }
      case StaticSizedArrayTT(size, RawArrayTT(memberType, _, _)) => {
        // We need to do this check right here because right after this we're making an array of size `size`
        // which we just received as an integer from the user.
        if (size != expectedNumMembers) {
          return InferEvaluateConflict(inferences.inferences, range, "Expected something with " + expectedNumMembers + " members but received " + kind, List.empty)
        }
        InferEvaluateSuccess((0 until size).toList.map(_ => memberType), true)
      }
      case _ => {
        return InferEvaluateConflict(inferences.inferences, range, "Expected something destructurable but received " + kind, List.empty)
      }
    }
  }
}
