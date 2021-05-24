package net.verdagon.vale.templar.infer

import net.verdagon.vale._
import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{ConstraintP, OwnP, ReadonlyP, ReadwriteP, ShareP, WeakP}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, OverrideSP}
import net.verdagon.vale.scout.{RangeS, Environment => _, FunctionEnvironment => _, IEnvironment => _}
import net.verdagon.vale.templar.{CompileErrorExceptionT, FunctionName2, IName2, IRune2, NameTranslator, RangedInternalErrorT}
import net.verdagon.vale.templar.infer.infer._
import net.verdagon.vale.templar.templata.{Conversions, _}
import net.verdagon.vale.templar.types._

import scala.collection.immutable.List

trait IInfererMatcherDelegate[Env, State] {
  def lookupMemberTypes(
    state: State,
    kind: Kind,
    // This is here so that the predictor can just give us however many things
    // we expect.
    expectedNumMembers: Int):
  Option[List[Coord]]

  def getMutability(state: State, kind: Kind): Mutability

  def getAncestorInterfaceDistance(
    temputs: State,
    descendantCitizenRef: CitizenRef2,
    ancestorInterfaceRef: InterfaceRef2):
  (Option[Int])

  def getAncestorInterfaces(temputs: State, descendantCitizenRef: CitizenRef2): Set[InterfaceRef2]

  def structIsClosure(state: State, structRef: StructRef2): Boolean

  def lookupTemplata(env: Env, range: RangeS, name: IName2): ITemplata
  def lookupTemplata(profiler: IProfiler, env: Env, range: RangeS, name: IImpreciseNameStepA): ITemplata
}

class InfererMatcher[Env, State](
    profiler: IProfiler,
    templataTemplar: TemplataTemplarInner[Env, State],
    equator: InfererEquator[Env, State],
    evaluate: (Env, State, Map[IRune2, ITemplataType], Set[IRune2], InferencesBox, IRulexTR) => (IInferEvaluateResult[ITemplata]),
    delegate: IInfererMatcherDelegate[Env, State]) {

  private[infer] def matchTemplataAgainstRuneSP(
    env: Env,
    state: State,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    range: RangeS,
    instance: ITemplata,
    rune: IRune2,
    expectedType: ITemplataType
  ): (IInferMatchResult) = {
    val alreadyExistingTemplata =
      if (localRunes.contains(rune)) {
        inferences.templatasByRune.get(rune) match {
          case None => {
            inferences.addConclusion(rune, instance)
            return (InferMatchSuccess(true))
          }
          case Some(alreadyInferredTemplata) => {
            alreadyInferredTemplata
          }
        }
      } else {
        // We might be grabbing a rune from a parent environment thats already solved,
        // such as when we do spaceship.fly() in TMRE.
        val templataFromEnv = delegate.lookupTemplata(env, range, rune)
        if (templataFromEnv.tyype != expectedType) {
          return (InferMatchConflict(inferences.inferences, range, "Rune " + rune + " is of type " + expectedType + ", but it received a " + templataFromEnv.tyype + ", specifically " + templataFromEnv, List()))
        }
        templataFromEnv
      }
    val equal =
      equator.templatasEqual(state, range, instance, alreadyExistingTemplata, expectedType)
    if (equal) {
      (InferMatchSuccess(true))
    } else {
      (InferMatchConflict(inferences.inferences, range, s"Disagreement about templata #${rune}:\n${alreadyExistingTemplata}\n${instance}", List()))
    }
  }

  private[infer] def matchReference2AgainstRuneSP(
    env: Env,
    state: State,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    range: RangeS,
    instance: Coord,
    coordRune: IRune2):
  (IInferMatchResult) = {

    inferences.templatasByRune.get(coordRune) match {
      case None => {
        inferences.addConclusion(coordRune, CoordTemplata(instance))
        (InferMatchSuccess(true))
      }
      case Some(CoordTemplata(alreadyInferredCoord)) => {
        if (instance == alreadyInferredCoord) {
          (InferMatchSuccess(true))
        } else {
          (InferMatchConflict(inferences.inferences, range, s"Disagreement about ref #${coordRune}:\n${CoordTemplata(alreadyInferredCoord)}\n${instance}", List()))
        }
      }
    }
  }

  private[infer] def matchReferend2AgainstRuneSP(
    env: Env,
    state: State,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    range: RangeS,
    instance: Kind,
    kindRune: IRune2):
  (IInferMatchResult) = {
    inferences.templatasByRune.get(kindRune) match {
      case None => {
        inferences.addConclusion(kindRune, KindTemplata(instance))
        (InferMatchSuccess(true))
      }
      case Some(KindTemplata(alreadyInferredKind)) => {
        if (instance == alreadyInferredKind) {
          (InferMatchSuccess(true))
        } else {
          (InferMatchConflict(inferences.inferences, range, s"Disagreement about kind #${alreadyInferredKind}:\n${KindTemplata(alreadyInferredKind)}\n${instance}", List()))
        }
      }
    }
  }

  private[infer] def matchReference2AgainstDestructure(
    env: Env,
    state: State,
    range: RangeS,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    instance: Coord,
    parts: List[AtomSP]):
  (IInferMatchResult) = {

    val structMemberTypes =
      delegate.lookupMemberTypes(state, instance.referend, expectedNumMembers = parts.size) match {
        case None => throw CompileErrorExceptionT(RangedInternalErrorT(range, "this thing cant be destructured, has no member types!"))
        case Some(x) => x
      }

    val destructuresDeeplySatisfied =
      structMemberTypes.zip(parts).foldLeft((true))({
        case ((deeplySatisfiedSoFar), (structMemberType, part)) => {
          val paramFilter = ParamFilter(structMemberType, None)
          matchParamFilterAgainstAtomSP(env, state, typeByRune, localRunes, inferences, paramFilter, part) match {
            case imc @ InferMatchConflict(_, _, _, _) => return imc
            case InferMatchSuccess(deeplySatisfied) => (deeplySatisfiedSoFar && deeplySatisfied)
          }
        }
      })

    (InferMatchSuccess(destructuresDeeplySatisfied))
  }

  private[infer] def matchParamFilterAgainstAtomSP(
      env: Env,
      state: State,
    typeByRune: Map[IRune2, ITemplataType],
      localRunes: Set[IRune2],
      inferences: InferencesBox,
      instance: ParamFilter,
      zrule: AtomSP):
  (IInferMatchResult) = {
    val AtomSP(patternRange, name, virtuality, ruleCoordRuneS, destructure) = zrule
    val runeCoordRuneA = Astronomer.translateRune(ruleCoordRuneS)

    val coordDeeplySatisfied =
      matchReference2AgainstRuneSP(env, state, typeByRune, localRunes, inferences, patternRange, instance.tyype, NameTranslator.translateRune(runeCoordRuneA)) match {
        case imc @ InferMatchConflict(_, _, _, _) => return imc
        case (InferMatchSuccess(ds)) => (ds)
      }

    val destructureDeeplySatisfied =
      destructure match {
        case None => (true)
        case Some(parts) => {
          matchReference2AgainstDestructure(env, state, patternRange, typeByRune, localRunes, inferences, instance.tyype, parts) match {
            case imc @ InferMatchConflict(_, _, _, _) => return imc
            case (InferMatchSuccess(ds)) => (ds)
          }
        }
      }

    val virtualityDeeplySatisfied =
      ((instance.virtuality, virtuality) match {
        case (None, _) => (true)
        case (Some(Abstract2), Some(AbstractSP)) => (true)
        case (Some(Abstract2), _) => return (InferMatchConflict(inferences.inferences, patternRange, s"ParamFilter virtuality didn't match rule:\n${instance.virtuality}\n${virtuality}", List()))
        case (Some(Override2(instanceSuperInterfaceRef2)), Some(OverrideSP(range, kindRuneS))) => {
          val kindRuneA = Astronomer.translateRune(kindRuneS)
          matchReferend2AgainstRuneSP(env, state, typeByRune, localRunes, inferences, range, instanceSuperInterfaceRef2, NameTranslator.translateRune(kindRuneA)) match {
            case imc @ InferMatchConflict(_, _, _, _) => return imc
            case (InferMatchSuccess(ds)) => (ds)
          }
        }
        case (Some(Override2(_)), _) => return (InferMatchConflict(inferences.inferences, patternRange, s"ParamFilter virtuality didn't match rule:\n${instance.virtuality}\n${virtuality}", List()))
      })

    (InferMatchSuccess(coordDeeplySatisfied && destructureDeeplySatisfied && virtualityDeeplySatisfied))
  }

  private[infer] def matchCitizenAgainstCallTT(
    env: Env,
    state: State,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    range: RangeS,
    call: CallTT,
    actualCitizen: CitizenRef2):
  IInferMatchResult = {
    evaluate(env, state, typeByRune, localRunes, inferences, TemplexTR(call.template)) match {
      case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferMatchConflict(inferences.inferences, range, "Couldn't evaluate template!", List(iec)))
      case (InferEvaluateUnknown(_)) => {
        vcurious() // Can this ever happen? If it does, is the below conflict appropriate?
        (InferMatchConflict(inferences.inferences, range, "Couldn't figure out template!", List()))
      }
      case (InferEvaluateSuccess(callTemplateTemplata, templateDeeplySatisfied)) => {
        // debt: TEST THIS!

        if (templataTemplar.citizenIsFromTemplate(actualCitizen, callTemplateTemplata)) {
          val expectedArgs = call.args
          if (actualCitizen.fullName.steps.size > 1) {
            vimpl()
          }
          val actualArgs = actualCitizen.fullName.last.templateArgs

          // Check to see that the actual template args match the expected template args
          val argsDeeplySatisfied =
            expectedArgs.zip(actualArgs).foldLeft((true))({
              case ((deeplySatisfiedSoFar), (expectedArg, actualArg)) => {
                matchTemplataAgainstTemplexTR(env, state, typeByRune, localRunes, inferences, actualArg, expectedArg) match {
                  case imc @ InferMatchConflict(_, _, _, _) => return imc
                  case InferMatchSuccess(deeplySatisfied) => (deeplySatisfiedSoFar && deeplySatisfied)
                }
              }
            })
          // If the function is the same, and the args are the same... it's the same.
          // This is important for avoiding stack overflows, see NMORFI.
          InferMatchSuccess(templateDeeplySatisfied && argsDeeplySatisfied)
        } else {
          return InferMatchConflict(inferences.inferences, range, "Given citizen didn't come from expected template!\nCitizen: " + actualCitizen + "\nTemplate: " + callTemplateTemplata, List())
        }
      }
    }
  }

  private[infer] def matchArrayAgainstCallTT(
    env: Env,
    state: State,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    expectedTemplate: ITemplexT,
    expectedArgs: List[ITemplexT],
    actualArgs: List[ITemplata]):
  (IInferMatchResult) = {
    // Check to see that the actual template matches the expected template
    val templateDeeplySatisfied =
      matchTemplataAgainstTemplexTR(env, state, typeByRune, localRunes, inferences, ArrayTemplateTemplata(), expectedTemplate) match {
        case imc @ InferMatchConflict(_, _, _, _) => return imc
        case (InferMatchSuccess(ds)) => (ds)
      }
    // Check to see that the actual template args match the expected template args
    val argsDeeplySatisfied =
      expectedArgs.zip(actualArgs).foldLeft((true))({
        case ((deeplySatisfiedSoFar), (expectedArg, actualArg)) => {
          matchTemplataAgainstTemplexTR(env, state, typeByRune, localRunes, inferences, actualArg, expectedArg) match {
            case imc @ InferMatchConflict(_, _, _, _) => return imc
            case InferMatchSuccess(deeplySatisfied) => (deeplySatisfiedSoFar && deeplySatisfied)
          }
        }
      })
    // If the function is the same, and the args are the same... it's the same.
    (InferMatchSuccess(templateDeeplySatisfied && argsDeeplySatisfied))
  }

  private[infer] def matchTemplataAgainstTemplexTR(
      env: Env,
      state: State,
      typeByRune: Map[IRune2, ITemplataType],
      localRunes: Set[IRune2],
      inferences: InferencesBox,
      instance: ITemplata,
      rule: ITemplexT):
  (IInferMatchResult) = {
    (rule, instance) match {
      case (IntTT(range, expectedValue), IntegerTemplata(actualValue))
          if actualValue == expectedValue => {
        (InferMatchSuccess(true))
      }
      case (BoolTT(range, expectedValue), BooleanTemplata(actualValue))
          if actualValue == expectedValue => {
        (InferMatchSuccess(true))
      }
      case (OwnershipTT(range, expectedOwnership), OwnershipTemplata(actualOwnership)) => {
        if (actualOwnership == Conversions.evaluateOwnership(expectedOwnership)) {
          return (InferMatchSuccess(true))
        } else if (actualOwnership == Share) {
          // Anything is compatible with share
          return (InferMatchSuccess(true))
        }
        return (InferMatchConflict(inferences.inferences, range, s"Supplied ownership ${actualOwnership} doesn't match expected ${expectedOwnership}", List()))
      }
      case (MutabilityTT(range, expectedMutability), MutabilityTemplata(actualMutability)) => {
        if (actualMutability == Conversions.evaluateMutability(expectedMutability)) {
          (InferMatchSuccess(true))
        } else {
          return (InferMatchConflict(inferences.inferences, range, s"Supplied mutability ${actualMutability} doesn't match expected ${expectedMutability}", List()))
        }
      }
      case (PermissionTT(range, expectedPermission), PermissionTemplata(actualPermission)) => {
        if (actualPermission == Conversions.evaluatePermission(expectedPermission)) {
          (InferMatchSuccess(true))
        } else {
          return (InferMatchConflict(inferences.inferences, range, s"Supplied permission ${actualPermission} doesn't match expected ${expectedPermission}", List()))
        }
      }
      case (LocationTT(range, expectedLocation), LocationTemplata(actualLocation)) => {
        if (actualLocation == Conversions.evaluateLocation(expectedLocation)) {
          (InferMatchSuccess(true))
        } else {
          return (InferMatchConflict(inferences.inferences, range, s"Supplied location ${actualLocation} doesn't match expected ${expectedLocation}", List()))
        }
      }
      case (VariabilityTT(range, expectedVariability), VariabilityTemplata(actualVariability)) => {
        if (actualVariability == Conversions.evaluateVariability(expectedVariability)) {
          (InferMatchSuccess(true))
        } else {
          return (InferMatchConflict(inferences.inferences, range, s"Supplied variability ${actualVariability} doesn't match expected ${expectedVariability}", List()))
        }
      }
      case (AbsoluteNameTT(range, expectedName, expectedType), actualTemplata) => {
        val expectedUncoercedTemplata = delegate.lookupTemplata(env, range, NameTranslator.translateNameStep(expectedName))

        if (templataTemplar.uncoercedTemplataEquals(env, state, actualTemplata, expectedUncoercedTemplata, expectedType)) {
          return InferMatchSuccess(true)
        } else {
          return (InferMatchConflict(inferences.inferences, range, s"Supplied templata doesn't match '${expectedName}':\n'${expectedName}' in environment:${expectedUncoercedTemplata}\nActual:${actualTemplata}", List()))
        }
      }
      case (NameTT(range, expectedName, expectedType), actualTemplata) => {
        val expectedUncoercedTemplata = delegate.lookupTemplata(profiler, env, range, expectedName)

        if (templataTemplar.uncoercedTemplataEquals(env, state, actualTemplata, expectedUncoercedTemplata, expectedType)) {
          return InferMatchSuccess(true)
        } else {
          return (InferMatchConflict(inferences.inferences, range, s"Supplied templata doesn't match '${expectedName}':\n'${expectedName}' in environment:${expectedUncoercedTemplata}\nActual:${actualTemplata}", List()))
        }

//        if (actualTemplata != expectedTemplata) {
//          // Right here, thought about checking for subtypes, but I don't think we should.
//          // For example, let's say we have this impl:
//          //   impl ITopInterface for IMiddleInterface;
//          // and a struct MyStruct that implements IMiddleInterface.
//          //
//          // If we search for all superinterfaces of IMiddleInterface, we'll be testing
//          // IMiddleInterface against IMiddleInterface, and itll correctly tell us that
//          // yes, it matches.
//          // If, however, we search for all superinterfaces of MyStruct, we'll be testing
//          // against that impl and ask if MyStruct matches that IMiddleInterface. We want
//          // it to say "no, it doesn't match." here.
//          //
//          // If we decide to check for subtypes here, it will do the incorrect thing in
//          // that latter case. So, we don't check for subtypes here, just strict equality.
//          return (InferMatchConflict(inferences.inferences, range, s"Supplied templata doesn't match '${name}':\n'${name}' in environment:${expectedTemplata}\nActual:${actualTemplata}", List()))
//        }
//        (InferMatchSuccess(true))
      }
      case (RuneTT(range, rune, expectedType), actualTemplata) => {
        if (actualTemplata.tyype != expectedType) {
          return InferMatchConflict(inferences.inferences, range, s"Doesn't match type! Expected ${expectedType} but received ${actualTemplata.tyype}", List())
        }
        // Catch any mismatch between the type as declared by the struct/function/whatever,
        // and the type we think it is in the actual RuneTT.
        typeByRune.get(rune) match {
          case None =>
          case Some(expectedTypeFromAbove) => vassert(expectedType == expectedTypeFromAbove)
        }
        matchTemplataAgainstRuneSP(env, state, typeByRune, localRunes, inferences, range, actualTemplata, rune, expectedType) match {
          case imc @ InferMatchConflict(_, _, _, _) => return imc
          case ims @ InferMatchSuccess(_) => ims
        }
      }
      case (ct @ CallTT(range, _, _, resultType), CoordTemplata(Coord(ownership, permission, structRef @ StructRef2(_)))) => {
        vassert(instance.tyype == ct.resultType)

        // This check is to help with NMORFI temporarily. It assumes that we'll never have any templates that return
        // coords, only kinds.
        // Eventually, we should change all of our coercing nonsense into toRef calls, see SCCTT.
        ownership match {
          case Share => // fine, continue
          case Own => // fine, continue
          case Constraint | Weak => {
            return InferMatchConflict(inferences.inferences, range, "Expected Own or Share, but was given " + ownership, List())
          }
        }

//        if (delegate.structIsClosure(state, structRef)) {
//          // If it's a closure, see if we can conform it to the receiving interface.
//
//          // We can make this smarter later, but for now, require that we have enough information
//          // up-front to completely know what the receiving thing is.
//          evaluate(env, state, typeByRune, localRunes, inferences, TemplexTR(ct)) match {
//            case InferEvaluateSuccess(templata, deeplySatisfied) => {
//              vassert(deeplySatisfied)
//              templata match {
//                case CoordTemplata(coord) => vimpl()
//                case _ => vwat()
//              }
//            }
//            case InferEvaluateUnknown(_) => {
//              vimpl("Shortcalling inferring not implemented yet!")
//            }
//            case iec @ InferEvaluateConflict(_, _, _, _) => InferMatchConflict(inferences.inferences, range, "Conflict in shortcall", List(iec))
//          }
//        } else {
          // If its not a closure, then there's nothing special to do here.

          matchCitizenAgainstCallTT(env, state, typeByRune, localRunes, inferences, range, ct, structRef)
//        }


        // this will get us... the FunctionA for the interface.
        //              val interfaceMethod =
        //                delegate.getSimpleInterfaceMethod(state, callTemplateTemplata)
        // Now let's make a little sub-world to try and figure out its runes.
        // We know one of its rune parameters so we can supply the int there...
        // Then we'll realize we know all the function parameters, but not the return
        // type, so we'll try evaluating the function.
        // We'll then get the return type of the function, and then set the rune.
        // Then we'll know the full IFunction1, and can proceed to glory.
      }
      case (ct @ CallTT(range, _, _, resultType), CoordTemplata(Coord(ownership, permission, cit @ InterfaceRef2(_)))) => {
        vassert(instance.tyype == ct.resultType)

        // This check is to help with NMORFI temporarily. It assumes that we'll never have any templates that return
        // coords, only kinds.
        // Eventually, we should change all of our coercing nonsense into toRef calls, see SCCTT.
        ownership match {
          case Share => // fine, continue
          case Own => // fine, continue
          case Constraint | Weak => {
            return InferMatchConflict(inferences.inferences, range, "Expected Own or Share, but was given " + ownership, List())
          }
        }

        matchCitizenAgainstCallTT(env, state, typeByRune, localRunes, inferences, range, ct, cit)
      }
      case (ct @ CallTT(range, _, _, _), KindTemplata(structRef @ StructRef2(_))) => {
        vassert(instance.tyype == ct.resultType)

//        if (delegate.structIsClosure(state, structRef)) {
//          // If it's a closure, see if we can conform it to the receiving interface.
//
//          // We can make this smarter later, but for now, require that we have enough information
//          // up-front to completely know what the receiving thing is.
//          evaluate(env, state, typeByRune, localRunes, inferences, TemplexTR(ct)) match {
//            case InferEvaluateSuccess(templata, deeplySatisfied) => {
//              vassert(deeplySatisfied)
//              templata match {
//                case CoordTemplata(coord) => vimpl()
//                case _ => vwat()
//              }
//            }
//            case InferEvaluateUnknown(_) => {
//              vimpl("Shortcalling inferring not implemented yet!")
//            }
//            case iec @ InferEvaluateConflict(_, _, _, _) => InferMatchConflict(inferences.inferences, range, "Conflict in shortcall", List(iec))
//          }
//        } else {
          // If its not a closure, then there's nothing special to do here.

          matchCitizenAgainstCallTT(env, state, typeByRune, localRunes, inferences, range, ct, structRef)
//        }


        // this will get us... the FunctionA for the interface.
        //              val interfaceMethod =
        //                delegate.getSimpleInterfaceMethod(state, callTemplateTemplata)
        // Now let's make a little sub-world to try and figure out its runes.
        // We know one of its rune parameters so we can supply the int there...
        // Then we'll realize we know all the function parameters, but not the return
        // type, so we'll try evaluating the function.
        // We'll then get the return type of the function, and then set the rune.
        // Then we'll know the full IFunction1, and can proceed to glory.
      }
      case (ct @ CallTT(range, _, _, _), KindTemplata(cit @ InterfaceRef2(_))) => {
        vassert(instance.tyype == ct.resultType)
        matchCitizenAgainstCallTT(env, state, typeByRune, localRunes, inferences, range, ct, cit)
      }
      case (ct @ CallTT(range, _, _, _), KindTemplata(Str2())) => {
        return (InferMatchConflict(inferences.inferences, range, "Can't match string against a CallTT, no such rule exists", List()))
      }
      case (CallTT(range, expectedTemplate, expectedArgs, resultType), KindTemplata(UnknownSizeArrayT2(RawArrayT2(elementArg,mutability)))) => {
        vassert(instance.tyype == resultType)
        matchArrayAgainstCallTT(
          env, state, typeByRune, localRunes, inferences, expectedTemplate, expectedArgs, List(MutabilityTemplata(mutability), CoordTemplata(elementArg)))
      }
      case (CallTT(range, _, _, _), KindTemplata(KnownSizeArrayT2(_, RawArrayT2(_, _)))) => {
        return (InferMatchConflict(inferences.inferences, range, "Can't match array sequence against a CallTT, no such rule exists", List()))
      }
      case (CallTT(range, _, _, _), CoordTemplata(Coord(_, _, KnownSizeArrayT2(_, _)))) => {
        return (InferMatchConflict(inferences.inferences, range, "Can't match array sequence against a CallTT, no such rule exists", List()))
      }
      case (CallTT(range, expectedTemplate, expectedArgs, resultType), CoordTemplata(Coord(_, _, UnknownSizeArrayT2(RawArrayT2(elementArg,mutability))))) => {
        vassert(instance.tyype == resultType)
        matchArrayAgainstCallTT(
          env, state, typeByRune, localRunes, inferences, expectedTemplate, expectedArgs, List(MutabilityTemplata(mutability), CoordTemplata(elementArg)))
      }
      case (CallTT(range, expectedTemplate, expectedArgs, resultType), ct @ CoordTemplata(_)) => {
        return (InferMatchConflict(inferences.inferences, range, "Can't match " + ct + " against CallTT", List()))
      }
      case (PrototypeTT(_, _, _, _), _) => {
        vfail("what even is this")
      }
//      case (PackTT(expectedMembers, _), KindTemplata(PackT2(actualMembers, _))) => {
//        val membersDeeplySatisfied =
//          expectedMembers.zip(actualMembers).foldLeft((true))({
//            case ((deeplySatisfiedSoFar), (expectedMember, actualMember)) => {
//              matchTemplataAgainstTemplexTR(env, state, typeByRune, localRunes, inferences, CoordTemplata(actualMember), expectedMember) match {
//                case imc @ InferMatchConflict(_, _, _, _) => return imc
//                case InferMatchSuccess(deeplySatisfied) => (deeplySatisfiedSoFar && deeplySatisfied)
//              }
//            }
//          })
//        (InferMatchSuccess(membersDeeplySatisfied))
//      }
      case (RepeaterSequenceTT(range, mutabilityTemplex, sizeTemplex, elementTemplex, resultType), CoordTemplata(Coord(ownership, _, KnownSizeArrayT2(size, RawArrayT2(elementCoord, mutability))))) => {
        vassert(resultType == CoordTemplataType)
        vcurious(ownership == Share || ownership == Own, "Got a non-share non-own repeater sequence!")
        matchArraySequenceKind(env, state, typeByRune, localRunes, inferences, mutabilityTemplex, sizeTemplex, elementTemplex, size, elementCoord, mutability)
      }
      case (RepeaterSequenceTT(range, mutabilityTemplex, sizeTemplex, elementTemplex, resultType), KindTemplata(KnownSizeArrayT2(size, RawArrayT2(elementCoord, mutability)))) => {
        vassert(resultType == KindTemplataType)
        matchArraySequenceKind(env, state, typeByRune, localRunes, inferences, mutabilityTemplex, sizeTemplex, elementTemplex, size, elementCoord, mutability)
      }
      case (RepeaterSequenceTT(range, _, _, _, _), KindTemplata(otherKind)) => {
        (InferMatchConflict(inferences.inferences, range, "Expected repeater sequence, was: " + otherKind, List()))
      }
      case (RepeaterSequenceTT(range, _, _, _, _), CoordTemplata(otherCoord)) => {
        (InferMatchConflict(inferences.inferences, range, "Expected repeater sequence, was: " + otherCoord, List()))
      }
      case (ManualSequenceTT(range, expectedElementTemplexesT, resultType), CoordTemplata(Coord(ownership, _, TupleT2(elements, _)))) => {
        vassert(resultType == CoordTemplataType)
        vcurious(ownership == Share || ownership == Own)
        matchTupleKind(env, state, typeByRune, localRunes, inferences, expectedElementTemplexesT, elements)
      }
      case (ManualSequenceTT(range, expectedElementTemplexesT, resultType), KindTemplata(TupleT2(elements, _))) => {
        vassert(resultType == KindTemplataType)
        matchTupleKind(env, state, typeByRune, localRunes, inferences, expectedElementTemplexesT, elements)
      }
      case (ManualSequenceTT(range, _, _), KindTemplata(otherKind)) => {
        (InferMatchConflict(inferences.inferences, range, "Expected repeater sequence, was: " + otherKind, List()))
      }
      case (ManualSequenceTT(range, _, _), CoordTemplata(otherCoord)) => {
        (InferMatchConflict(inferences.inferences, range, "Expected repeater sequence, was: " + otherCoord, List()))
      }
      case (OwnershipTT(range, ownershipP), OwnershipTemplata(ownershipT)) => {
        if (ownershipT == Share) {
          // Doesn't matter what the ownership rule was, ownership doesnt apply to Share.
          (InferMatchSuccess(true))
        } else if (ownershipT == Conversions.evaluateOwnership(ownershipP)) {
          (InferMatchSuccess(true))
        } else {
          (InferMatchConflict(inferences.inferences, range, s"Ownerships don't match: ${ownershipP} and ${ownershipT}", List()))
        }
      }
      case (StringTT(range, expectedValue), StringTemplata(actualValue)) => {
        if (actualValue == expectedValue) {
          (InferMatchSuccess(true))
        } else {
          (InferMatchConflict(inferences.inferences, range, s"Strings don't match: ${actualValue} and ${expectedValue}", List()))
        }
      }
      case (CoordListTT(range, expectedCoordRules), CoordListTemplata(actualCoords)) => {
        vassert(expectedCoordRules.size == actualCoords.size)

        val deeplySatisfied =
          expectedCoordRules.zip(actualCoords).zipWithIndex.foldLeft(true)({ case (deeplySatisfiedSoFar, ((expectedCoordRule, actualCoord), index)) =>
            matchTemplataAgainstTemplexTR(env, state, typeByRune, localRunes, inferences, CoordTemplata(actualCoord), expectedCoordRule) match {
              case imc @ InferMatchConflict(_, _, _, _) => {
                return InferMatchConflict(inferences.inferences, range, "Coord list element " + (index + 1) / (actualCoords.size) + " doesn't match!", List(imc))
              }
              case InferMatchSuccess(deeplySatisfied) => (deeplySatisfiedSoFar && deeplySatisfied)
            }
          })

        InferMatchSuccess(deeplySatisfied)
      }
      case (InterpretedTT(range, expectedOwnership, expectedPermission, innerCoordTemplex), CoordTemplata(Coord(instanceOwnership, instancePermission, instanceKind))) => {
        // When we're matching e.g. a &Spaceship instance into a &T InterpretedTT rule, it's almost as if the
        // InterpretedTT rule is stripping off the &, to figure out that T = Spaceship.
        // Here's some examples:
        // - &Spaceship into &T rule: T = Spaceship
        // - &&Spaceship into &&T rule: T = Spaceship
        // - &!Spaceship into &!T rule: T = Spaceship
        // If there's a mismatch, it's a conflict.
        // Shared refs are easy, they seem to just ignore and sail through InterpretedTT unchanged... except when
        // dealing with weak references, those are conflicts.

        val ownershipCompatible =
          (instanceOwnership, expectedOwnership) match {
            case (Own, OwnP) => true
            case (Own, ConstraintP) => false
            case (Own, WeakP) => false
            case (Own, ShareP) => false

            case (Constraint, OwnP) => false
            case (Constraint, ConstraintP) => true
            case (Constraint, WeakP) => false
            case (Constraint, ShareP) => false

            case (Weak, OwnP) => false
            case (Weak, ConstraintP) => false
            case (Weak, WeakP) => true
            case (Weak, ShareP) => false

            case (Share, OwnP) => true
            case (Share, ConstraintP) => true
            case (Share, WeakP) => false
            case (Share, ShareP) => true
          }
        if (!ownershipCompatible) {
          return InferMatchConflict(inferences.inferences, range, s"Couldn't match incoming ${instanceOwnership} against expected ${expectedOwnership}", List())
        }

        val permissionCompatible =
          if (instanceOwnership == Share) {
            // honey badger
            true
          } else {
            (instancePermission, expectedPermission) match {
              case (Readonly, ReadonlyP) => true
              case (Readonly, ReadwriteP) => false
              case (Readwrite, ReadonlyP) => false
              case (Readwrite, ReadwriteP) => true
            }
          }

        if (!permissionCompatible) {
          return InferMatchConflict(inferences.inferences, range, s"Couldn't match incoming ${instancePermission} against expected ${expectedPermission}", List())
        }

        val resultCoord =
          if (instanceOwnership == Share) {
            CoordTemplata(Coord(Share, Readonly, instanceKind))
          } else {
            CoordTemplata(Coord(Own, Readwrite, instanceKind))
          }
        matchTemplataAgainstTemplexTR(env, state, typeByRune, localRunes, inferences, resultCoord, innerCoordTemplex)
      }
      case other => throw CompileErrorExceptionT(RangedInternalErrorT(other._1.range, "Can't match rule " + rule + " against instance " + instance))
    }
  }

  private def matchArraySequenceKind(
      env: Env,
      state: State,
      typeByRune: Map[IRune2, ITemplataType],
      localRunes: Set[IRune2],
      inferences: InferencesBox,
      mutabilityTemplex: ITemplexT,
      sizeTemplex: ITemplexT,
      elementTemplex: ITemplexT,
      size: Int,
      elementCoord: Coord,
      mutability: Mutability):
  IInferMatchResult = {
    val mutabilityDeeplySatisfied =
      matchTemplataAgainstTemplexTR(env, state, typeByRune, localRunes, inferences, MutabilityTemplata(mutability), mutabilityTemplex) match {
        case (imc@InferMatchConflict(_, _, _, _)) => return imc
        case InferMatchSuccess(deeplySatisfied) => (deeplySatisfied)
      }

    val sizeDeeplySatisfied =
      matchTemplataAgainstTemplexTR(env, state, typeByRune, localRunes, inferences, IntegerTemplata(size), sizeTemplex) match {
        case (imc@InferMatchConflict(_, _, _, _)) => return imc
        case InferMatchSuccess(deeplySatisfied) => (deeplySatisfied)
      }

    val elementDeeplySatisfied =
      matchTemplataAgainstTemplexTR(env, state, typeByRune, localRunes, inferences, CoordTemplata(elementCoord), elementTemplex) match {
        case (imc@InferMatchConflict(_, _, _, _)) => return imc
        case InferMatchSuccess(deeplySatisfied) => (deeplySatisfied)
      }

    val deeplySatisfied = mutabilityDeeplySatisfied && sizeDeeplySatisfied && elementDeeplySatisfied
    InferMatchSuccess(deeplySatisfied)
  }

  private def matchTupleKind(
    env: Env,
    state: State,
    typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    expectedElementTemplexesT: List[ITemplexT],
    actualElements: List[Coord]):
  IInferMatchResult = {
    val deeplySatisfied =
      expectedElementTemplexesT.zip(actualElements).foldLeft((true))({
        case ((deeplySatisfiedSoFar), (expectedArg, actualArg)) => {
          matchTemplataAgainstTemplexTR(env, state, typeByRune, localRunes, inferences, CoordTemplata(actualArg), expectedArg) match {
            case imc @ InferMatchConflict(_, _, _, _) => return imc
            case InferMatchSuccess(deeplySatisfied) => (deeplySatisfiedSoFar && deeplySatisfied)
          }
        }
      })
    InferMatchSuccess(deeplySatisfied)
  }

  private[infer] def matchTemplataAgainstRulexTR(
    env: Env,
    state: State,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    instance: ITemplata,
    irule: IRulexTR):
  (IInferMatchResult) = {
    irule match {
      case rule @ EqualsTR(_, _, _) => {
        matchTemplataAgainstEqualsTR(env, state, typeByRune, localRunes, inferences, instance, rule)
      }
      case rule @ IsaTR(_, _, _) => {
        matchTemplataAgainstIsaTR(env, state, typeByRune, localRunes, inferences, instance, rule)
      }
      case rule @ OrTR(_, _) => {
        matchTemplataAgainstOrTR(env, state, typeByRune, localRunes, inferences, instance, rule)
      }
      case rule @ ComponentsTR(_, _, _) => {
        matchTemplataAgainstComponentsTR(env, state, typeByRune, localRunes, inferences, instance, rule)
      }
      case TemplexTR(itemplexTT) => {
        matchTemplataAgainstTemplexTR(env, state, typeByRune, localRunes, inferences, instance, itemplexTT)
      }
      case rule @ CallTR(_, _, _, _) => {
        matchTemplataAgainstCallTR(env, state, typeByRune, localRunes, inferences, instance, rule)
      }
    }
  }

  // debt: rename from instance
  private[infer] def matchTemplataAgainstCallTR(
    env: Env,
    state: State,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    instance: ITemplata,
    rule: CallTR):
  (IInferMatchResult) = {
    vassert(instance.tyype == rule.resultType)

    // We don't match into the argRules here, see MDMIA.
    // But we can't just *not* match them, and evaluating could return unknown, in which case we
    // don't know what to do.
    // For now, we'll only allow calls like toRef that are 1<1>, and so can be matched.

    val CallTR(range, name, args, resultType) = rule

    if (instance.tyype != resultType) {
      return (InferMatchConflict(inferences.inferences, range, "Call result expected type " + resultType + ", but was " + instance, List()))
    }

    name match {
      case "toRef" => {
        val List(kindRule) = args
        instance match {
          case CoordTemplata(Coord(instanceOwnership, instancePermission, instanceKind)) => {
            val defaultOwnershipForKind =
              if (delegate.getMutability(state, instanceKind) == Mutable) Own else Share
            if (instanceOwnership != defaultOwnershipForKind) {
              return (InferMatchConflict(inferences.inferences, range, "Coord matching into toRef doesn't have default ownership: " + instanceOwnership, List()))
            }
            matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, KindTemplata(instanceKind), kindRule)
          }
          case _ => return (InferMatchConflict(inferences.inferences, range, "Bad arguments to toRef: " + args, List()))
        }
      }
      case "passThroughIfConcrete" => {
        val List(kindRule) = args
        instance match {
          case KindTemplata(StructRef2(_) | PackT2(_, _) | TupleT2(_, _) | KnownSizeArrayT2(_, _) | UnknownSizeArrayT2(_)) => {
            matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, instance, kindRule)
          }
          case _ => return (InferMatchConflict(inferences.inferences, range, "Bad arguments to passThroughIfConcrete: " + args, List()))
        }
      }
      case "passThroughIfInterface" => {
        val List(kindRule) = args
        instance match {
          case KindTemplata(InterfaceRef2(_)) => {
            matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, instance, kindRule)
          }
          case _ => return (InferMatchConflict(inferences.inferences, range, "Bad arguments to passThroughIfInterface: " + args, List()))
        }
      }
      case "passThroughIfStruct" => {
        val List(kindRule) = args
        instance match {
          case KindTemplata(StructRef2(_)) => {
            matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, instance, kindRule)
          }
          case _ => return (InferMatchConflict(inferences.inferences, range, "Bad arguments to passThroughIfStruct: " + instance, List()))
        }
      }
    }
  }

  private[infer] def matchTemplataAgainstComponentsTR(
    env: Env,
    state: State,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    instance: ITemplata,
    rule: ComponentsTR):
  (IInferMatchResult) = {
    val ComponentsTR(range, tyype, components) = rule

    if (!equator.templataMatchesType(instance, tyype)) {
      return (InferMatchConflict(inferences.inferences, range, s"Supplied templata isn't the right type! Type: ${rule.tyype} but gave: ${instance}", List()))
    }

    instance match {
      case KindTemplata(actualReferend) => {
        vcheck(components.size == 1, "Wrong number of components for kind")
        val List(mutabilityRule) = components

        val actualMutability = delegate.getMutability(state, actualReferend)
        matchTemplataAgainstRulexTR(
          env, state, typeByRune, localRunes, inferences, MutabilityTemplata(actualMutability), mutabilityRule)
      }
      case CoordTemplata(actualReference) => {
        vcheck(components.size == 3, "Wrong number of components for coord")
        val List(ownershipRule, permissionRule, kindRule) = components
        val actualOwnership = OwnershipTemplata(actualReference.ownership)
        val ownershipDeeplySatisfied =
          matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, actualOwnership, ownershipRule) match {
            case imc @ InferMatchConflict(_, _, _, _) => return imc
            case (InferMatchSuccess(ods)) => (ods)
          }
        val actualPermission = PermissionTemplata(actualReference.permission)
        val permissionDeeplySatisfied =
          matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, actualPermission, permissionRule) match {
            case imc @ InferMatchConflict(_, _, _, _) => return imc
            case (InferMatchSuccess(ods)) => (ods)
          }
        val actualKind = KindTemplata(actualReference.referend)
        val kindDeeplySatisfied =
          matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, actualKind, kindRule) match {
            case imc @ InferMatchConflict(_, _, _, _) => return imc
            case (InferMatchSuccess(kds)) => (kds)
          }
        (InferMatchSuccess(ownershipDeeplySatisfied && permissionDeeplySatisfied && kindDeeplySatisfied))
      }
      case PrototypeTemplata(actualPrototype) => {
        vcheck(components.size == 3, "Wrong number of components for prototype")
        val List(nameRule, paramsRule, retRule) = components
        val actualHumanNameTemplata =
          actualPrototype.fullName.last match {
            case FunctionName2(humanName, _, _) => StringTemplata(humanName)
            case _ => return InferMatchConflict(inferences.inferences, range, "Actual prototype doesn't have a human name: " + actualPrototype.fullName.last, List())
          }
        val actualParamsTemplata = CoordListTemplata(actualPrototype.paramTypes)
        val actualRetTemplata = CoordTemplata(actualPrototype.returnType)

        val nameDeeplySatisfied =
          matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, actualHumanNameTemplata, nameRule) match {
            case imc @ InferMatchConflict(_, _, _, _) => return imc
            case (InferMatchSuccess(ods)) => ods
          }

        val paramsDeeplySatisfied =
          matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, actualParamsTemplata, paramsRule) match {
            case imc @ InferMatchConflict(_, _, _, _) => return imc
            case (InferMatchSuccess(kds)) => (kds)
          }

        val retDeeplySatisfied =
          matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, actualRetTemplata, retRule) match {
            case imc @ InferMatchConflict(_, _, _, _) => return imc
            case (InferMatchSuccess(kds)) => (kds)
          }

        (InferMatchSuccess(nameDeeplySatisfied && paramsDeeplySatisfied && retDeeplySatisfied))
      }
    }
  }

  private[infer] def matchTemplataAgainstEqualsTR(
    env: Env,
    state: State,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    instance: ITemplata,
    rule: EqualsTR):
  (IInferMatchResult) = {
    val EqualsTR(range, left, right) = rule

    matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, instance, left) match {
      case imc @ InferMatchConflict(_, _, _, _) => imc
      case (InferMatchSuccess(leftDeeplySatisfied)) => {
        matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, instance, right) match {
          case imc @ InferMatchConflict(_, _, _, _) => imc
          case (InferMatchSuccess(rightDeeplySatisfied)) => {
            (InferMatchSuccess(leftDeeplySatisfied && rightDeeplySatisfied))
          }
        }
      }
    }
  }

  private[infer] def matchTemplataAgainstIsaTR(
    env: Env,
    state: State,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    subTemplata: ITemplata,
    rule: IsaTR):
  (IInferMatchResult) = {
    val IsaTR(range, left, right) = rule

    matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, subTemplata, left) match {
      case imc @ InferMatchConflict(_, _, _, _) => imc
      case (InferMatchSuccess(subDeeplySatisfied)) => {
        evaluate(env, state, typeByRune, localRunes, inferences, right) match {
          case (iec @ InferEvaluateConflict(_, _, _, _)) => return (InferMatchConflict(inferences.inferences, range, "Couldn't evaluate concept!", List(iec)))
          case (InferEvaluateUnknown(conceptRuleDeeplySatisfied)) => {

            // Doesn't matter whether the concept rule is deeply satisfied because this conforms
            // rule itself isn't satisfied yet.
            val _ = conceptRuleDeeplySatisfied
            val isaRuleSatisfied = false

            (InferMatchSuccess(isaRuleSatisfied))
          }
          case (InferEvaluateSuccess(conceptTemplata, conceptDeeplySatisfied)) => {
            val KindTemplata(sub : CitizenRef2) = subTemplata
            val KindTemplata(interface @ InterfaceRef2(_)) = conceptTemplata

            val supers = delegate.getAncestorInterfaces(state, sub)

            if (supers.contains(interface)) {
              val isaSatisfied = true
              val deeplySatisfied = subDeeplySatisfied && conceptDeeplySatisfied && isaSatisfied
              InferMatchSuccess(deeplySatisfied)
            } else {
              return (InferMatchConflict(inferences.inferences, range, "Isa failed!\nSub: " + sub + "\nSuper: " + interface, List()))
            }
          }
        }
      }
    }
  }

  private[infer] def matchTemplataAgainstOrTR(
    env: Env,
    state: State,
      typeByRune: Map[IRune2, ITemplataType],
    localRunes: Set[IRune2],
    inferences: InferencesBox,
    instance: ITemplata,
    rule: OrTR):
  (IInferMatchResult) = {
    val OrTR(range, possibilities) = rule

    val results = possibilities.map(matchTemplataAgainstRulexTR(env, state, typeByRune, localRunes, inferences, instance, _))

    // Look for one that's deeply satisfied, and return it.
    results.collect({
      case ims @ InferMatchSuccess(true) => return (ims)
    })
    // Since there were no deeply satisfied ones, look for one that matched at all.
    results.collect({
      case ims @ InferMatchSuccess(false) => return (ims)
    })
    // They must all be conflicts.
    val conflicts =
      results.map({ case imc @ InferMatchConflict(_, _, _, _) => imc })
    (InferMatchConflict(inferences.inferences, range, "No branches of the Or rule matched!", conflicts))
  }

//
//  private[infer] def matchStructAgainstOverrideSP(
//      structRef2: StructRef2,
//      overrideRule: Override1):
//  Boolean = {
//    val structDef2 = State.lookupStruct(structRef2)
//    val superInterfaces = structDef2.getAncestorInterfacesNotIncludingSelf(State)
//    val matchingInterfacesAndSubTemplars =
//      superInterfaces
//        .flatMap(interfaceRef2 => {
//          println("dont do this?")
//          val subTemplar = new InferTemplarMatcher(env, State, delegate, rules, inferences)
//          if (subTemplar.matchReferend2AgainstReferendRefSP(interfaceRef2, Some(overrideRule.tyype))) {
//            List((interfaceRef2, subTemplar))
//          } else {
//            List()
//          }
//        })
//    if (matchingInterfacesAndSubTemplars.size > 1) {
//      vfail("Can't figure for struct " + structRef2 + " which of these interfaces it implements! " + matchingInterfacesAndSubTemplars.map(_._1))
//    }
//    matchingInterfacesAndSubTemplars.headOption match {
//      case None => false
//      case Some((_, matchingSubTemplar)) => {
////        State = matchingSubTemplar.State
//        inferences = matchingSubTemplar.inferences
//        true
//      }
//    }
//  }

//  private[infer] def matchStructAgainstCitizenTemplate(instance: StructRef2, rule: CitizenTerrySP) = {
//    val StructRef2(instanceHumanName, instanceTemplateArgs) = instance
//    val CitizenTerrySP(citizenTemplateRule, templateArgRules) = rule
//
//    if (instanceTemplateArgs.size == templateArgRules.size) {
//      env.lookupTemplata(instanceHumanName) match {
//        case None => vfail("wot")
//        case Some(StructTerryTemplata(StructTerry(outerEnv, name, alreadySpecifiedExplicitTemplateArgs))) => {
//          vfail("i have no idea")
//          true
//        }
//        case Some(InterfaceTerryTemplata(InterfaceTerry(outerEnv, name, alreadySpecifiedExplicitTemplateArgs))) => {
//          vfail("wot")
//        }
//      }
//    } else {
//      // Is this possible?
//      vfail("impl?")
//    }
//  }
//
//  private[infer] def matchInterfaceAgainstCitizenTemplate(instance: InterfaceRef2, rule: CitizenTerrySP) = {
//    val InterfaceRef2(instanceHumanName, instanceTemplateArgs) = instance
//    val CitizenTerrySP(citizenTemplateRule, templateArgRules) = rule
//
//    if (instanceTemplateArgs.size == templateArgRules.size) {
//      env.lookupTemplata(instanceHumanName) match {
//        case None => vfail("wot")
//        case Some(StructTerryTemplata(StructTerry(outerEnv, name, alreadySpecifiedExplicitTemplateArgs))) => {
//          vfail("wot")
//        }
//        case Some(InterfaceTerryTemplata(InterfaceTerry(outerEnv, name, alreadySpecifiedExplicitTemplateArgs))) => {
//          vfail("i have no idea")
//          true
//        }
//      }
//    } else {
//      // Is this possible?
//      vfail("impl?")
//    }
//  }
}
