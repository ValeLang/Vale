package dev.vale.typing.citizen

import dev.vale.highertyping.ImplA
import dev.vale.postparsing.{IRuneS, ITemplataType, ImplImpreciseNameS, ImplSubCitizenImpreciseNameS, ImplTemplataType, LocationInDenizen}
import dev.vale.postparsing.rules.{Equivalencies, IRulexSR, RuleScout}
import dev.vale.solver._
import dev.vale.typing.OverloadResolver.InferFailure
import dev.vale.typing.env.{ExpressionLookupContext, TemplataLookupContext, TemplatasStore}
import dev.vale.typing._
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{Accumulator, Err, Interner, Ok, Profiler, RangeS, Result, U, postparsing, vassert, vassertSome, vcurious, vfail, vimpl, vregionmut, vwat}
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing._
import dev.vale.typing.ast.{CitizenDefinitionT, ImplT, InterfaceDefinitionT}
import dev.vale.typing.env._
import dev.vale.typing.function._
import dev.vale.typing.infer.ITypingPassSolverError

import scala.collection.immutable.Set

sealed trait IsParentResult
case class IsParent(
  templata: ITemplataT[ImplTemplataType],
  conclusions: Map[IRuneS, ITemplataT[ITemplataType]],
  implId: IdT[IImplNameT]
) extends IsParentResult
case class IsntParent(
  candidates: Vector[IIncompleteOrFailedCompilerSolve]
) extends IsParentResult

class ImplCompiler(
    opts: TypingPassOptions,
    interner: Interner,
    nameTranslator: NameTranslator,
    structCompiler: StructCompiler,
    templataCompiler: TemplataCompiler,
    inferCompiler: InferCompiler) {

  // We don't have an isAncestor call, see REMUIDDA.

  def resolveImpl(
      coutputs: CompilerOutputs,
      parentRanges: List[RangeS],
      callLocation: LocationInDenizen,
      callingEnv: IInDenizenEnvironmentT,
      initialKnowns: Vector[InitialKnown],
      implTemplata: ImplDefinitionTemplataT):
  Result[CompleteCompilerSolve, IIncompleteOrFailedCompilerSolve] = {

    val ImplDefinitionTemplataT(parentEnv, impl) = implTemplata
    val ImplA(
    range,
    name,
    identifyingRunes,
    rules,
    runeToType,
    structKindRune,
    subCitizenImpreciseName,
    interfaceKindRune,
    superInterfaceImpreciseName
    ) = impl

    val implTemplateId =
      parentEnv.id.addStep(nameTranslator.translateImplName(name))

    val outerEnv =
      CitizenEnvironmentT(
        parentEnv.globalEnv,
        parentEnv,
        implTemplateId,
        implTemplateId,
        TemplatasStore(implTemplateId, Map(), Map()))

    // Remember, impls can have rules too, such as:
    //   impl<T> Opt<T> for Some<T> where func drop(T)void;
    // so we do need to filter them out when compiling.
    val definitionRules = rules.filter(InferCompiler.includeRuleInCallSiteSolve)

    // This is callingEnv because we might be coming from an abstract function that's trying
    // to evaluate an override.
    val originalCallingEnv = callingEnv
    val envs = InferEnv(originalCallingEnv, range :: parentRanges, callLocation, outerEnv, RegionT())
    val solver =
      inferCompiler.makeSolver(
        envs, coutputs, definitionRules, runeToType, range :: parentRanges, initialKnowns, Vector())

    inferCompiler.continue(envs, coutputs, solver) match {
      case Ok(()) =>
      case Err(e) => return Err(e)
    }

    inferCompiler.checkResolvingConclusionsAndResolve(
      envs,
      coutputs,
      range :: parentRanges,
      callLocation,
      runeToType,
      definitionRules,
      // We include the reachable bounds for the struct rune. Those are bounds that this impl will
      // have to satisfy when it calls the interface.
      Vector(structKindRune.rune),
      solver)
  }

  // WARNING: Doesn't verify conclusions to make sure that any bounds are satisfied!
  def partialResolveImpl(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    callingEnv: IInDenizenEnvironmentT,
    initialKnowns: Vector[InitialKnown],
    implTemplata: ImplDefinitionTemplataT):
  Result[Map[IRuneS, ITemplataT[ITemplataType]], FailedCompilerSolve] = {

    val ImplDefinitionTemplataT(parentEnv, impl) = implTemplata
    val ImplA(
    range,
    name,
    identifyingRunes,
    rules,
    runeToType,
    structKindRune,
    subCitizenImpreciseName,
    interfaceKindRune,
    superInterfaceImpreciseName
    ) = impl

    val implTemplateId =
      parentEnv.id.addStep(nameTranslator.translateImplName(name))

    val outerEnv =
      CitizenEnvironmentT(
        parentEnv.globalEnv,
        parentEnv,
        implTemplateId,
        implTemplateId,
        TemplatasStore(implTemplateId, Map(), Map()))

    // Remember, impls can have rules too, such as:
    //   impl<T> Opt<T> for Some<T> where func drop(T)void;
    // so we do need to filter them out when compiling.
    val definitionRules = rules.filter(InferCompiler.includeRuleInCallSiteSolve)

    // This is callingEnv because we might be coming from an abstract function that's trying
    // to evaluate an override.
    val originalCallingEnv = callingEnv
    val envs = InferEnv(originalCallingEnv, range :: parentRanges, callLocation, outerEnv, RegionT())
    val solver =
      inferCompiler.makeSolver(
        envs, coutputs, definitionRules, runeToType, range :: parentRanges, initialKnowns, Vector())
    inferCompiler.continue(envs, coutputs, solver) match {
      case Ok(()) =>
      case Err(e) => return Err(e)
    }
    Ok(solver.userifyConclusions().toMap)
  }

  // This will just figure out the struct template and interface template,
  // so we can add it to the temputs.
  def compileImpl(coutputs: CompilerOutputs, callLocation: LocationInDenizen, implTemplata: ImplDefinitionTemplataT): Unit = {
    val ImplDefinitionTemplataT(parentEnv, implA) = implTemplata

    val implTemplateId =
      parentEnv.id.addStep(
        nameTranslator.translateImplName(implA.name))

    val implOuterEnv =
      CitizenEnvironmentT(
        parentEnv.globalEnv,
        parentEnv,
        implTemplateId,
        implTemplateId,
        TemplatasStore(implTemplateId, Map(), Map()))

    // We might one day need to incrementally solve and add placeholders here like we do for
    // functions and structs, see IRAGP.
    val implPlaceholders =
      implA.genericParams.zipWithIndex.map({ case (rune, index) =>
        val placeholder =
          templataCompiler.createPlaceholder(
            coutputs, implOuterEnv, implTemplateId, rune, index, implA.runeToType, vregionmut(None), true)
        InitialKnown(rune.rune, placeholder)
      })

    val ImplA(
    range,
    name,
    identifyingRunes,
    rules,
    runeToType,
    structKindRune,
    subCitizenImpreciseName,
    interfaceKindRune,
    superInterfaceImpreciseName
    ) = implA

    val outerEnv =
      CitizenEnvironmentT(
        parentEnv.globalEnv,
        parentEnv,
        implTemplateId,
        implTemplateId,
        TemplatasStore(implTemplateId, Map(), Map()))

    // Remember, impls can have rules too, such as:
    //   impl<T> Opt<T> for Some<T> where func drop(T)void;
    // so we do need to filter them out when compiling.
    val definitionRules = rules.filter(InferCompiler.includeRuleInDefinitionSolve)

    val envs =
      InferEnv(
        implOuterEnv,
        List(range),
        callLocation,
        outerEnv,
        RegionT())
    val CompleteCompilerSolve(_, inferences, runeToFunctionBound1, declaredBoundsFromSubCitizen, reachableBoundsFromSubCitizen) =
      inferCompiler.solveForDefining(
        envs,
        coutputs,
        definitionRules,
        runeToType,
        List(range),
        callLocation,
        implPlaceholders,
        Vector(),
        // We include reachable bounds for the struct so we don't have to re-specify all its bounds in the impl.
        Vector(structKindRune.rune)) match {
        case Ok(i) => i
        case Err(e) => throw CompileErrorExceptionT(CouldntEvaluatImpl(List(implA.range), e))
      }

    val subCitizen =
      inferences.get(implA.subCitizenRune.rune) match {
        case None => vwat()
        case Some(KindTemplataT(s: ICitizenTT)) => s
        case _ => vwat()
      }
    val subCitizenTemplateId =
      TemplataCompiler.getCitizenTemplate(subCitizen.id)

    val superInterface =
      inferences.get(implA.interfaceKindRune.rune) match {
        case None => vwat()
        case Some(KindTemplataT(i@InterfaceTT(_))) => i
        case Some(other) => throw CompileErrorExceptionT(CantImplNonInterface(List(implA.range), other))
      }
    val superInterfaceTemplateId =
      TemplataCompiler.getInterfaceTemplate(superInterface.id)


    val templateArgs = implA.genericParams.map(_.rune.rune).map(inferences)
    val instantiatedId = assembleImplName(implTemplateId, templateArgs, subCitizen)

    val implInnerEnv =
      GeneralEnvironmentT.childOf(
        interner,
        implOuterEnv,
        instantiatedId,
        reachableBoundsFromSubCitizen.zipWithIndex.map({ case (templata, index) =>
          interner.intern(ReachablePrototypeNameT(index)) -> TemplataEnvEntry(templata)
        }).toVector ++
        inferences.map({ case (nameS, templata) =>
          interner.intern(RuneNameT((nameS))) -> TemplataEnvEntry(templata)
        }).toVector)
    val runeToNeededFunctionBound = TemplataCompiler.assembleRuneToFunctionBound(implInnerEnv.templatas)
    val runeToNeededImplBound = TemplataCompiler.assembleRuneToImplBound(implInnerEnv.templatas)
//    vcurious(runeToFunctionBound1 == runeToNeededFunctionBound) // which do we want?

    val runeIndexToIndependence =
      calculateRunesIndependence(coutputs, callLocation, implTemplata, implOuterEnv, superInterface)

    val implT =
      interner.intern(
        ImplT(
          implTemplata,
          implOuterEnv,
          instantiatedId,
          implTemplateId,
          subCitizenTemplateId,
          subCitizen,
          superInterface,
          superInterfaceTemplateId,
          runeToNeededFunctionBound,
          runeToNeededImplBound,
          runeIndexToIndependence.toVector,
          reachableBoundsFromSubCitizen.map(_.prototype)))
    coutputs.declareType(implTemplateId)
    coutputs.declareTypeOuterEnv(implTemplateId, implOuterEnv)
    coutputs.declareTypeInnerEnv(implTemplateId, implInnerEnv)
    coutputs.addImpl(implT)
  }

  def calculateRunesIndependence(
    coutputs: CompilerOutputs,
    callLocation: LocationInDenizen,
    implTemplata: ImplDefinitionTemplataT,
    implOuterEnv: IInDenizenEnvironmentT,
    interface: InterfaceTT,
  ): Vector[Boolean] = {

    // Now we're going to figure out the <ZZ> for the eg Milano case.

    // Don't verify conclusions, because this will likely be a partial solve, which means we
    // might not even be able to solve the struct, which means we can't pull in any declared
    // function bounds that come from them. We'll check them later.
    val partialCaseConclusionsFromSuperInterface =
      partialResolveImpl(
        coutputs,
        List(implTemplata.impl.range),
        callLocation,
        implOuterEnv,
        Vector(
          InitialKnown(
            implTemplata.impl.interfaceKindRune,
            // We may be feeding in something interesting like IObserver<Opt<T>> here should be fine,
            // the impl will receive it and match it to its own unknown runes appropriately.
            KindTemplataT(interface))),
        implTemplata) match {
            case Ok(conclusions) => conclusions
        case Err(e) => {
          throw CompileErrorExceptionT(CouldntEvaluatImpl(List(implTemplata.impl.range), e))
        }
      }
    // These will be anything that wasn't already determined by the incoming interface.
    // These are the "independent" generic params, like the <ZZ> in Milano.
    // No particular reason they're ordered, it just feels appropriate to keep them in the same
    // order they appeared in the impl.
    val runeToIndependence =
      implTemplata.impl.genericParams.map(_.rune.rune)
        .map(rune => !partialCaseConclusionsFromSuperInterface.contains(rune))

    runeToIndependence
  }

  def assembleImplName(
    templateName: IdT[IImplTemplateNameT],
    templateArgs: Vector[ITemplataT[ITemplataType]],
    subCitizen: ICitizenTT):
  IdT[IImplNameT] = {
    templateName.copy(
      localName = templateName.localName.makeImplName(interner, templateArgs, subCitizen))
  }

  //    // First, figure out what citizen is implementing.
  //    val subCitizenImpreciseName = RuleScout.getRuneKindTemplate(implA.rules, implA.structKindRune.rune)
  //    val subCitizenTemplata =
  //      implOuterEnv.lookupNearestWithImpreciseName(subCitizenImpreciseName, Set(TemplataLookupContext)) match {
  //        case None => throw CompileErrorExceptionT(ImplSubCitizenNotFound(implA.range, subCitizenImpreciseName))
  //        case Some(it @ CitizenTemplata(_, _)) => it
  //        case Some(other) => throw CompileErrorExceptionT(NonCitizenCantImpl(implA.range, other))
  //      }
  //    val subCitizenTemplateFullName = templataCompiler.resolveCitizenTemplate(subCitizenTemplata)
  //    val subCitizenDefinition = coutputs.lookupCitizen(subCitizenTemplateFullName)
  //    val subCitizenPlaceholders =
  //      subCitizenDefinition.genericParamTypes.zipWithIndex.map({ case (tyype, index) =>
  //        templataCompiler.createPlaceholder(coutputs, implOuterEnv, implTemplateFullName, index, tyype)
  //      })
  //    val placeholderedSubCitizenTT =
  //      structCompiler.resolveCitizen(coutputs, implOuterEnv, implA.range, subCitizenTemplata, subCitizenPlaceholders)
  //
  //
  //    // Now, figure out what interface is being implemented.
  //    val superInterfaceImpreciseName = RuleScout.getRuneKindTemplate(implA.rules, implA.interfaceKindRune.rune)
  //    val superInterfaceTemplata =
  //      implOuterEnv.lookupNearestWithImpreciseName(superInterfaceImpreciseName, Set(TemplataLookupContext)) match {
  //        case None => throw CompileErrorExceptionT(ImplSuperInterfaceNotFound(implA.range, superInterfaceImpreciseName))
  //        case Some(it @ InterfaceTemplata(_, _)) => it
  //        case Some(other) => throw CompileErrorExceptionT(CantImplNonInterface(implA.range, other))
  //      }
  //    val superInterfaceTemplateFullName = templataCompiler.resolveCitizenTemplate(superInterfaceTemplata)
  //    val superInterfaceDefinition = coutputs.lookupCitizen(superInterfaceTemplateFullName)
  //    val superInterfacePlaceholders =
  //      superInterfaceDefinition.genericParamTypes.zipWithIndex.map({ case (tyype, index) =>
  //        val placeholderNameT = implTemplateFullName.addStep(PlaceholderNameT(PlaceholderTemplateNameT(index)))
  //        templataCompiler.createPlaceholder(coutputs, implOuterEnv, implTemplateFullName, index, tyype)
  //      })
  //    val placeholderedSuperInterfaceTT =
  //      structCompiler.resolveInterface(coutputs, implOuterEnv, implA.range, superInterfaceTemplata, superInterfacePlaceholders)
  //
  //    // Now compile it from the sub citizen's perspective.
  //    compileImplGivenSubCitizen(coutputs, placeholderedSubCitizenTT, implTemplata)
  //    // Now compile it from the super interface's perspective.
  //    compileImplGivenSuperInterface(coutputs, placeholderedSuperInterfaceTT, implTemplata)
  //  }
  //
  //  def compileParentImplsForSubCitizen(
  //    coutputs: CompilerOutputs,
  //    subCitizenDefinition: CitizenDefinitionT):
  //  Unit = {
  //    Profiler.frame(() => {
  //      val subCitizenTemplateFullName = subCitizenDefinition.templateName
  //      val subCitizenEnv = coutputs.getEnvForTemplate(subCitizenTemplateFullName)
  //      // See INSHN, the imprecise name for an impl is the wrapped imprecise name of its struct template.
  //      val needleImplTemplateFullName = interner.intern(ImplTemplateSubNameT(subCitizenTemplateFullName))
  //      val implTemplates =
  //        subCitizenEnv.lookupAllWithName(needleImplTemplateFullName, Set(TemplataLookupContext))
  //      implTemplates.foreach({
  //        case it @ ImplTemplata(_, _) => {
  //          compileImplGivenSubCitizen(coutputs, subCitizenDefinition, it)
  //        }
  //        case other => vwat(other)
  //      })
  //    })
  //  }
  //
  //  def compileChildImplsForParentInterface(
  //    coutputs: CompilerOutputs,
  //    parentInterfaceDefinition: InterfaceDefinitionT):
  //  Unit = {
  //    Profiler.frame(() => {
  //      val parentInterfaceTemplateFullName = parentInterfaceDefinition.templateName
  //      val parentInterfaceEnv = coutputs.getEnvForTemplate(parentInterfaceTemplateFullName)
  //      // See INSHN, the imprecise name for an impl is the wrapped imprecise name of its struct template.
  //      val needleImplTemplateFullName = interner.intern(ImplTemplateSuperNameT(parentInterfaceTemplateFullName))
  //      val implTemplates =
  //        parentInterfaceEnv.lookupAllWithName(needleImplTemplateFullName, Set(TemplataLookupContext))
  //      implTemplates.foreach({
  //        case impl @ ImplTemplata(_, _) => {
  //          compileImplGivenSuperInterface(coutputs, parentInterfaceDefinition, impl)
  //        }
  //        case other => vwat(other)
  //      })
  //    })
  //  }

  //  // Doesn't include self
  //  def compileGetAncestorInterfaces(
  //    coutputs: CompilerOutputs,
  //    descendantCitizenRef: ICitizenTT):
  //  (Map[InterfaceTT, ImplTemplateNameT]) = {
  //    Profiler.frame(() => {
  //      val parentInterfacesAndImpls =
  //        compileGetParentInterfaces(coutputs, descendantCitizenRef)
  //
  //      // Make a map that contains all the parent interfaces, with distance 1
  //      val foundSoFar =
  //        parentInterfacesAndImpls.map({ case (interfaceRef, impl) => (interfaceRef, impl) }).toMap
  //
  //      compileGetAncestorInterfacesInner(
  //        coutputs,
  //        foundSoFar,
  //        parentInterfacesAndImpls.toMap)
  //    })
  //  }
  //
  //  private def compileGetAncestorInterfacesInner(
  //    coutputs: CompilerOutputs,
  //    // This is so we can know what we've already searched.
  //    nearestDistanceByInterfaceRef: Map[InterfaceTT, ImplTemplateNameT],
  //    // These are the interfaces that are *exactly* currentDistance away.
  //    // We will do our searching from here.
  //    interfacesAtCurrentDistance: Map[InterfaceTT, ImplTemplateNameT]):
  //  (Map[InterfaceTT, ImplTemplateNameT]) = {
  //    val interfacesAtNextDistance =
  //      interfacesAtCurrentDistance.foldLeft((Map[InterfaceTT, ImplTemplateNameT]()))({
  //        case ((previousAncestorInterfaceRefs), (parentInterfaceRef, parentImpl)) => {
  //          val parentAncestorInterfaceRefs =
  //            compileGetParentInterfaces(coutputs, parentInterfaceRef)
  //          (previousAncestorInterfaceRefs ++ parentAncestorInterfaceRefs)
  //        }
  //      })
  //
  //    // Discard the ones that have already been found; they're actually at
  //    // a closer distance.
  //    val newlyFoundInterfaces =
  //    interfacesAtNextDistance.keySet
  //      .diff(nearestDistanceByInterfaceRef.keySet)
  //      .toVector
  //      .map(key => (key -> interfacesAtNextDistance(key)))
  //      .toMap
  //
  //    if (newlyFoundInterfaces.isEmpty) {
  //      (nearestDistanceByInterfaceRef)
  //    } else {
  //      // Combine the previously found ones with the newly found ones.
  //      val newNearestDistanceByInterfaceRef =
  //        nearestDistanceByInterfaceRef ++ newlyFoundInterfaces.toMap
  //
  //      compileGetAncestorInterfacesInner(
  //        coutputs,
  //        newNearestDistanceByInterfaceRef,
  //        newlyFoundInterfaces)
  //    }
  //  }
  //
  //  def getParents(
  //    coutputs: CompilerOutputs,
  //    subCitizenTT: ICitizenTT):
  //  Vector[InterfaceTT] = {
  //    val subCitizenTemplateFullName = TemplataCompiler.getCitizenTemplate(subCitizenTT.fullName)
  //    coutputs
  //      .getParentImplsForSubCitizenTemplate(subCitizenTemplateFullName)
  //      .map({ case ImplT(_, parentInterfaceFromPlaceholderedSubCitizen, _, _) =>
  //        val substituter =
  //          TemplataCompiler.getPlaceholderSubstituter(interner, subCitizenTT.fullName)
  //        substituter.substituteForInterface(parentInterfaceFromPlaceholderedSubCitizen)
  //      }).toVector
  //  }
  //

  def isDescendant(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    callingEnv: IInDenizenEnvironmentT,
    kind: ISubKindTT):
  Boolean = {
    getParents(coutputs, parentRanges, callLocation, callingEnv, kind).nonEmpty
  }

  // def getImplDescendantGivenParent(
  //   coutputs: CompilerOutputs,
  //   parentRanges: List[RangeS],
  //     callLocation: LocationInDenizen,
  //   callingEnv: IInDenizenEnvironmentT,
  //   implTemplata: ImplDefinitionTemplataT,
  //   parent: InterfaceTT,
  //   verifyConclusions: Boolean,
  //   declareBounds: Boolean):
  // Result[ICitizenTT, IIncompleteOrFailedCompilerSolve] = {
  //   val initialKnowns =
  //     Vector(
  //       InitialKnown(implTemplata.impl.interfaceKindRune, KindTemplataT(parent)))
  //   val CompleteCompilerSolve(_, conclusions, _, _) =
  //     solveImplForCall(coutputs, parentRanges, callLocation, callingEnv, initialKnowns, implTemplata, declareBounds, true) match {
  //       case ccs @ CompleteCompilerSolve(_, _, _, _) => ccs
  //       case x : IIncompleteOrFailedCompilerSolve => return Err(x)
  //     }
  //   val parentTT = conclusions.get(implTemplata.impl.subCitizenRune.rune)
  //   vassertSome(parentTT) match {
  //     case KindTemplataT(i : ICitizenTT) => Ok(i)
  //     case _ => vwat()
  //   }
  // }

  def getImplParentGivenSubCitizen(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
      callLocation: LocationInDenizen,
    callingEnv: IInDenizenEnvironmentT,
    implTemplata: ImplDefinitionTemplataT,
    child: ICitizenTT):
  Result[InterfaceTT, IIncompleteOrFailedCompilerSolve] = {
    val initialKnowns =
      Vector(
        InitialKnown(implTemplata.impl.subCitizenRune, KindTemplataT(child)))
    val childEnv =
      coutputs.getOuterEnvForType(
        parentRanges,
        TemplataCompiler.getCitizenTemplate(child.id))
          val CompleteCompilerSolve(_, conclusions, _, Vector(), _) =
      resolveImpl(coutputs, parentRanges, callLocation, callingEnv, initialKnowns, implTemplata) match {
        case Ok(ccs) => ccs
        case Err(x) => return Err(x)
      }
    val parentTT = conclusions.get(implTemplata.impl.interfaceKindRune.rune)
    vassertSome(parentTT) match {
      case KindTemplataT(i @ InterfaceTT(_)) => Ok(i)
      case _ => vwat()
    }
  }

  def getParents(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    callingEnv: IInDenizenEnvironmentT,
    subKind: ISubKindTT):
  Vector[ISuperKindTT] = {
    val subKindId = subKind.id
    val subKindTemplateName = TemplataCompiler.getSubKindTemplate(subKindId)
    val subKindEnv = coutputs.getOuterEnvForType(parentRanges, subKindTemplateName)
    val subKindImpreciseName =
      TemplatasStore.getImpreciseName(interner, subKindId.localName) match {
        case None => return Vector()
        case Some(n) => n
      }
    val implImpreciseNameS =
      interner.intern(ImplSubCitizenImpreciseNameS(subKindImpreciseName))

    val matching =
      subKindEnv.lookupAllWithImpreciseName(implImpreciseNameS, Set(TemplataLookupContext)) ++
      callingEnv.lookupAllWithImpreciseName(implImpreciseNameS, Set(TemplataLookupContext))

    val implDefsWithDuplicates = new Accumulator[ImplDefinitionTemplataT]()
    val implTemplatasWithDuplicates = new Accumulator[IsaTemplataT]()

    matching.foreach({
      case it@ImplDefinitionTemplataT(_, _) => implDefsWithDuplicates.add(it)
      case it@IsaTemplataT(_, _, _, _) => implTemplatasWithDuplicates.add(it)
      case _ => vwat()
    })

    val implDefs =
      implDefsWithDuplicates.buildArray().groupBy(_.impl.range).map(_._2.head)
    val parentsFromImplDefs =
      implDefs.flatMap(impl => {
        subKind match {
          case subCitizen : ICitizenTT => {
            getImplParentGivenSubCitizen(coutputs, parentRanges, callLocation, callingEnv, impl, subCitizen) match {
              case Ok(x) => List(x)
              case Err(_) => {
                opts.debugOut("Throwing away error! TODO: Use an index or something instead.")
                List()
              }
            }
          }
          case _ => List()
        }
      }).toVector

    val parentsFromImplTemplatas =
      implTemplatasWithDuplicates
        .buildArray()
        .filter(_.subKind == subKind)
        .map(_.superKind)
        .collect({ case x : ISuperKindTT => x })
        .distinct

    parentsFromImplDefs ++ parentsFromImplTemplatas
  }

  def isParent(
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    subKindTT: ISubKindTT,
    superKindTT: ISuperKindTT):
  IsParentResult = {
    val superKindImpreciseName =
      TemplatasStore.getImpreciseName(interner, superKindTT.id.localName) match {
        case None => return IsntParent(Vector())
        case Some(n) => n
      }
    val subKindImpreciseName =
      TemplatasStore.getImpreciseName(interner, subKindTT.id.localName) match {
        case None => return IsntParent(Vector())
        case Some(n) => n
      }
    val implImpreciseNameS =
      interner.intern(ImplImpreciseNameS(subKindImpreciseName, superKindImpreciseName))

    val subKindEnv =
      coutputs.getOuterEnvForType(
        parentRanges, TemplataCompiler.getSubKindTemplate(subKindTT.id))
    val superKindEnv =
      coutputs.getOuterEnvForType(
        parentRanges, TemplataCompiler.getSuperKindTemplate(superKindTT.id))

    val matching =
      callingEnv.lookupAllWithImpreciseName(implImpreciseNameS, Set(TemplataLookupContext)) ++
      subKindEnv.lookupAllWithImpreciseName(implImpreciseNameS, Set(TemplataLookupContext)) ++
      superKindEnv.lookupAllWithImpreciseName(implImpreciseNameS, Set(TemplataLookupContext))

    val implsDefsWithDuplicates = new Accumulator[ImplDefinitionTemplataT]()
    val implTemplatasWithDuplicatesAcc = new Accumulator[IsaTemplataT]()
    matching.foreach({
      case it@ImplDefinitionTemplataT(_, _) => implsDefsWithDuplicates.add(it)
      case it@IsaTemplataT(_, _, _, _) => implTemplatasWithDuplicatesAcc.add(it)
      case _ => vwat()
    })
    val implTemplatasWithDuplicates = implTemplatasWithDuplicatesAcc.buildArray()

    implTemplatasWithDuplicates.find(i => i.subKind == subKindTT && i.superKind == superKindTT) match {
      case Some(impl) => {
        coutputs.addInstantiationBounds(impl.implName, InstantiationBoundArgumentsT(Map(), Map()))
        return IsParent(impl, Map(), impl.implName)
      }
      case None =>
    }

    val impls =
      implsDefsWithDuplicates.buildArray().groupBy(_.impl.range).map(_._2.head)
    val results =
      impls.map(impl => {
        val initialKnowns =
          Vector(
            InitialKnown(impl.impl.subCitizenRune, KindTemplataT(subKindTT)),
            InitialKnown(impl.impl.interfaceKindRune, KindTemplataT(superKindTT)))
        resolveImpl(coutputs, parentRanges, callLocation, callingEnv, initialKnowns, impl) match {
          case Ok(ccs @ CompleteCompilerSolve(_, _, _, _, _)) => Ok((impl, ccs))
          case Err(x) => Err(x)
        }
      })
    val (oks, errs) = Result.split(results)
    vcurious(oks.size <= 1)
    oks.headOption match {
      case Some((implTemplata, CompleteCompilerSolve(_, conclusions, runeToSuppliedFunction, Vector(), reachableBoundsFromSubCitizen))) => {
        // Dont need this for anything yet
        val _ = reachableBoundsFromSubCitizen

        val templateArgs =
          implTemplata.impl.genericParams.map(_.rune.rune).map(conclusions)
        val implTemplateId =
          implTemplata.env.id.addStep(
            nameTranslator.translateImplName(implTemplata.impl.name))
        val instantiatedId = assembleImplName(implTemplateId, templateArgs, subKindTT.expectCitizen())
        coutputs.addInstantiationBounds(instantiatedId, runeToSuppliedFunction)
        IsParent(implTemplata, conclusions, instantiatedId)
      }
      case None => IsntParent(errs.toVector)
    }
  }
}
