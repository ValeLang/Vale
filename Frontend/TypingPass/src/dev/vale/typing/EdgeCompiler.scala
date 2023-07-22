package dev.vale.typing

//import dev.vale.astronomer.{GlobalFunctionFamilyNameS, INameS, INameA, ImmConcreteDestructorImpreciseNameA, ImmConcreteDestructorNameA, ImmInterfaceDestructorImpreciseNameS}
//import dev.vale.astronomer.VirtualFreeImpreciseNameS
import dev.vale.postparsing.rules.RuneUsage
import dev.vale._
import dev.vale.postparsing._
import dev.vale.typing.ast.{InterfaceEdgeBlueprintT, PrototypeT}
import dev.vale.typing.env.{GeneralEnvironmentT, IInDenizenEnvironmentT, TemplataEnvEntry, TemplataLookupContext, TemplatasStore}
import dev.vale.typing.types._
import dev.vale.typing.ast._
import dev.vale.typing.citizen.ImplCompiler
import dev.vale.typing.function.FunctionCompiler
import dev.vale.typing.function._
import dev.vale.typing.names._
import dev.vale.typing.templata.ITemplataT.{expectCoord, expectCoordTemplata, expectKindTemplata}
import dev.vale.typing.templata._
import dev.vale.typing.types._

import scala.collection.mutable

sealed trait IMethod
case class NeededOverride(
  name: IImpreciseNameS,
  paramFilters: Vector[CoordT]
) extends IMethod { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }
case class FoundFunction(prototype: PrototypeT) extends IMethod { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

case class PartialEdgeT(
  struct: StructTT,
  interface: InterfaceTT,
  methods: Vector[IMethod]) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class EdgeCompiler(
    interner: Interner,
    keywords: Keywords,
    functionCompiler: FunctionCompiler,
    overloadCompiler: OverloadResolver,
    implCompiler: ImplCompiler) {
  def compileITables(coutputs: CompilerOutputs):
  (
    Vector[InterfaceEdgeBlueprintT],
    Map[
      IdT[IInterfaceNameT],
      Map[
        IdT[ICitizenNameT],
        EdgeT]]) = {
    val interfaceEdgeBlueprints =
      makeInterfaceEdgeBlueprints(coutputs)

    val itables =
      interfaceEdgeBlueprints.map(interfaceEdgeBlueprint => {
        val interfacePlaceholderedId = interfaceEdgeBlueprint.interface
        val interfaceTemplateId = TemplataCompiler.getInterfaceTemplate(interfacePlaceholderedId)
        val interfaceId =
          coutputs.lookupInterface(interfaceTemplateId).instantiatedInterface.id
        val interfaceDefinition = coutputs.lookupInterface(interfaceTemplateId)
//        val interfacePlaceholderedCitizen = interfaceDefinition.placeholderedInterface
        val overridingImpls = coutputs.getChildImplsForSuperInterfaceTemplate(interfaceTemplateId)
        val overridingCitizenToFoundFunction =
          overridingImpls.map(overridingImpl => {
            val overridingCitizenTemplateId = overridingImpl.subCitizenTemplateId
            val superInterfaceWithSubCitizenPlaceholders = overridingImpl.superInterface


            val foundFunctions =
              interfaceEdgeBlueprint.superFamilyRootHeaders.map({ case (abstractFunctionPrototype, abstractIndex) =>
                val overrride =
                  lookForOverride(
                    coutputs,
                    LocationInDenizen(Vector()),
                    overridingImpl,
                    interfaceTemplateId,
                    overridingCitizenTemplateId,
                    abstractFunctionPrototype,
                    abstractIndex)
                abstractFunctionPrototype.id -> overrride
              })
            val overridingCitizen = overridingImpl.subCitizen
            vassert(coutputs.getInstantiationBounds(overridingCitizen.id).nonEmpty)
            val superInterfaceId = overridingImpl.superInterface.id
            vassert(coutputs.getInstantiationBounds(superInterfaceId).nonEmpty)
            val edge =
              EdgeT(
                overridingImpl.instantiatedId,
                overridingCitizen,
                overridingImpl.superInterface.id,
                overridingImpl.runeToFuncBound,
                overridingImpl.runeToImplBound,
                foundFunctions.toMap)
            val overridingCitizenDef = coutputs.lookupCitizen(overridingCitizenTemplateId)
            overridingCitizenDef.instantiatedCitizen.id -> edge
          }).toMap
        interfaceId -> overridingCitizenToFoundFunction
      }).toMap
    (interfaceEdgeBlueprints, itables)
  }

  private def makeInterfaceEdgeBlueprints(coutputs: CompilerOutputs): Vector[InterfaceEdgeBlueprintT] = {
    val x1 =
      coutputs.getAllFunctions().flatMap({ case function =>
        function.header.getAbstractInterface match {
          case None => Vector.empty
          case Some(abstractInterface) => {
            val abstractInterfaceTemplate =
              TemplataCompiler.getInterfaceTemplate(abstractInterface.id)
            Vector(abstractInterfaceTemplate -> function)
          }
        }
      })
    val x2 = x1.groupBy(_._1)
    val x3 = x2.mapValues(_.map(_._2))
    val x4 =
      x3.map({ case (interfaceTemplateId, functions) =>
        // Sort so that the interface's internal methods are first and in the same order
        // they were declared in. It feels right, and vivem also depends on it
        // when it calls array generators/consumers' first method.
        val interfaceDef = coutputs.getAllInterfaces().find(_.templateName == interfaceTemplateId).get
        // Make sure `functions` has everything that the interface def wanted.
        vassert(
          (interfaceDef.internalMethods.toSet --
            functions.map(func => func.header.toPrototype -> vassertSome(func.header.getVirtualIndex)).toSet)
            .isEmpty)
        // Move all the internal methods to the front.
        val orderedMethods =
          interfaceDef.internalMethods ++
            functions.map(_.header)
              .filter(x => !interfaceDef.internalMethods.exists(y => y._1.toSignature == x.toSignature))
              .map(header => header.toPrototype -> vassertSome(header.getVirtualIndex))
        (interfaceTemplateId -> orderedMethods)
      })
    val abstractFunctionHeadersByInterfaceTemplateIdWithoutEmpties = x4
    // Some interfaces would be empty and they wouldn't be in
    // abstractFunctionsByInterfaceWithoutEmpties, so we add them here.
    val abstractFunctionHeadersByInterfaceTemplateId =
      abstractFunctionHeadersByInterfaceTemplateIdWithoutEmpties ++
        coutputs.getAllInterfaces().map({ case i =>
          (i.templateName -> abstractFunctionHeadersByInterfaceTemplateIdWithoutEmpties.getOrElse(i.templateName, Set()))
        })

    val interfaceEdgeBlueprints =
      abstractFunctionHeadersByInterfaceTemplateId
        .map({ case (interfaceTemplateId, functionHeaders2) =>
          InterfaceEdgeBlueprintT(
            coutputs.lookupInterface(interfaceTemplateId).instantiatedInterface.id,
            // This is where they're given order and get an implied index
            functionHeaders2.toVector)
        })
    interfaceEdgeBlueprints.toVector
  }

  def createOverridePlaceholderMimicking(
    coutputs: CompilerOutputs,
    originalTemplataToMimic: ITemplataT[ITemplataType],
    dispatcherOuterEnv: IInDenizenEnvironmentT,
    index: Int,
    rune: IRuneS):
  ITemplataT[ITemplataType] = {
    // Need New Special Placeholders for Abstract Function Override Case (NNSPAFOC)
    //
    // One would think that we could just conjure up some placeholders under the abstract
    // function's name, like:
    //
    //   val placeholderName =
    //     PlaceholderNameT(PlaceholderTemplateNameT(placeholderToSubstitution.size))
    //   val placeholderId =
    //     FullNameT(packageCoord, abstractFuncTemplateFullName.steps, placeholderName)
    //
    // It would even mostly work, because the abstract function was already compiled, already
    // made a bunch of placeholders, and registered them and their envs with the coutputs so
    // we can just reuse them.
    //
    // Alas, not so simple, because of the Milano case. Those god damned Milanos.
    //
    // Recall this line:
    //
    //   <ZZ> milano &Milano<X, Y, Z, ZZ> => launch(milano, bork)
    //
    // We're actually introducing a fourth placeholder, one that doesn't really refer to a
    // generic arg of the abstract function. This is a launch$3, and launch only had generic args
    // launch$0-launch$2.
    //
    // So, we need to conjure an entirely new placeholder.
    //
    // And of course, since this might happen multiple times (for multiple impls), and we
    // don't want to double-register anything with the coutputs. To get around that, we're
    // just going to make entirely new placeholders every time.
    //
    // For that, we need a unique name, so we'll put the impl's name inside the placeholder's
    // name. The placeholder's full name will be the abstract function's name, then a step
    // containing the impl's name, and then the placeholder name.
    //
    // To be consistent, we'll do this for every placeholder, not just the extra one like ZZ.

    val placeholderName =
      interner.intern(KindPlaceholderNameT(
        interner.intern(KindPlaceholderTemplateNameT(index, rune))))
    val placeholderId = dispatcherOuterEnv.id.addStep(placeholderName)
    // And, because it's new, we need to declare it and its environment.
    val placeholderTemplateId =
      TemplataCompiler.getPlaceholderTemplate(placeholderId)

    coutputs.declareType(placeholderTemplateId)
    coutputs.declareTypeOuterEnv(
      placeholderTemplateId,
      GeneralEnvironmentT.childOf(interner, dispatcherOuterEnv, placeholderTemplateId))

    val result =
      originalTemplataToMimic match {
        case PlaceholderTemplataT(_, tyype) => {
          PlaceholderTemplataT(placeholderId, tyype)
        }
        case KindTemplataT(KindPlaceholderT(originalPlaceholderId)) => {
          val originalPlaceholderTemplateId = TemplataCompiler.getPlaceholderTemplate(originalPlaceholderId)
          val mutability = coutputs.lookupMutability(originalPlaceholderTemplateId)
          coutputs.declareTypeMutability(placeholderTemplateId, mutability)
          KindTemplataT(KindPlaceholderT(placeholderId))
        }
        case CoordTemplataT(CoordT(ownership, _, KindPlaceholderT(originalPlaceholderId))) => {
          val originalPlaceholderTemplateId = TemplataCompiler.getPlaceholderTemplate(originalPlaceholderId)
          val mutability = coutputs.lookupMutability(originalPlaceholderTemplateId)
          coutputs.declareTypeMutability(placeholderTemplateId, mutability)
          CoordTemplataT(CoordT(ownership, RegionT(), KindPlaceholderT(placeholderId)))
        }
        case other => vwat(other)
      }
    result
  }

  private def lookForOverride(
    coutputs: CompilerOutputs,
    callLocation: LocationInDenizen,
    impl: ImplT,
    interfaceTemplateId: IdT[IInterfaceTemplateNameT],
    subCitizenTemplateId: IdT[ICitizenTemplateNameT],
    abstractFunctionPrototype: PrototypeT,
    abstractIndex: Int):
  OverrideT = {
    val abstractFuncTemplateId =
      TemplataCompiler.getFunctionTemplate(abstractFunctionPrototype.id)
    val abstractFunctionParamUnsubstitutedTypes = abstractFunctionPrototype.paramTypes
    vassert(abstractIndex >= 0)
    val abstractParamUnsubstitutedType = abstractFunctionParamUnsubstitutedTypes(abstractIndex)

    val maybeOriginFunctionTemplata =
      coutputs.lookupFunction(abstractFunctionPrototype.toSignature)
        .flatMap(_.header.maybeOriginFunctionTemplata)

    val range =
      maybeOriginFunctionTemplata.map(_.function.range)
        .getOrElse(RangeS.internal(interner, -2976395))

    val originFunctionTemplata = vassertSome(maybeOriginFunctionTemplata)

    val abstractFuncOuterEnv =
      coutputs.getOuterEnvForFunction(abstractFuncTemplateId)

    val dispatcherTemplateName =
      interner.intern(OverrideDispatcherTemplateNameT(impl.templateId))
    val dispatcherTemplateId =
      abstractFuncTemplateId.addStep(dispatcherTemplateName)
    val dispatcherOuterEnv =
      GeneralEnvironmentT.childOf(
        interner,
        abstractFuncOuterEnv,
        dispatcherTemplateId)

    // Step 1: Get The Compiled Impl's Interface, see GTCII.

    // One would think we could just call the abstract function and the override functions
    // from the impl's inner environment. It would even be convenient, because the impl
    // already has the interface and subcitizen in terms of the impl's placeholders.
    // However:
    // - We need to do the abstract function from the abstract function's environment
    // - We need to do at least the override resolve from the abstract function's environment
    //   (or something under it) so that we can have the bounds that come from the abstract function.

    // This is a straight mapping from the impl placeholders to the new dispatcher placeholders.
    // This might have some placeholders that won't actually be part of the dispatcher generic args,
    // for example if we have `impl<ZZ> IObserver for MyStruct<ZZ>` then the dispatcher function won't
    // have that ZZ.
    // Note that these placeholder indexes might not line up with the ones from the original impl.
    val implPlaceholderToDispatcherPlaceholder =
      U.mapWithIndex[ITemplataT[ITemplataType], (IdT[IPlaceholderNameT], ITemplataT[ITemplataType])](
        impl.instantiatedId.localName.templateArgs.toVector
        .zip(impl.runeIndexToIndependence)
        .filter({ case (templata, independent) => !independent }) // Only grab dependent runes
        .map({ case (templata, independent) => templata }),
        { case (implPlaceholderIndex, implPlaceholder) =>
          val implPlaceholderId = TemplataCompiler.getPlaceholderTemplataId(implPlaceholder)
          // Sanity check we're in an impl template, we're about to replace it with a function template
          implPlaceholderId.initSteps.last match { case _: IImplTemplateNameT => case _ => vwat() }

          val implRune = implPlaceholderId.localName.rune
          val dispatcherRune = DispatcherRuneFromImplS(implRune)

          val dispatcherPlaceholder =
            createOverridePlaceholderMimicking(
              coutputs, implPlaceholder, dispatcherOuterEnv, implPlaceholderIndex, dispatcherRune)
          (implPlaceholderId, dispatcherPlaceholder)
        })
    val dispatcherPlaceholders = implPlaceholderToDispatcherPlaceholder.map(_._2)

    implPlaceholderToDispatcherPlaceholder.map(_._1).foreach(x => vassert(x.initId(interner) == impl.templateId))

    val dispatcherPlaceholderedInterface =
      expectKindTemplata(
        TemplataCompiler.substituteTemplatasInKind(
          coutputs,
          interner,
          keywords,
          impl.templateId,
          implPlaceholderToDispatcherPlaceholder.map(_._2),
          // The dispatcher is receiving these types as parameters, so it can bring in bounds from
          // them.
          InheritBoundsFromTypeItself,
          impl.superInterface)).kind.expectInterface()
    val dispatcherPlaceholderedAbstractParamType =
      abstractParamUnsubstitutedType.copy(kind = dispatcherPlaceholderedInterface)
    // Now we have a ISpaceship<int, launch$0, launch$1> that we can use to compile the abstract
    // function header. (Using the Raza example)
    // In the Milano case, we have an ISpaceship<launch$0, launch$1, launch$2> and also another
    // substitution for a launch$3 that doesnt actually correspond to any template parameter
    // of the abstract function.

    // Step 2: Compile Dispatcher Function Given Interface, see CDFGI

    val EvaluateFunctionSuccess(dispatchingFuncPrototype, dispatcherInnerInferences) =
      functionCompiler.evaluateGenericVirtualDispatcherFunctionForPrototype(
        coutputs,
        List(range, impl.templata.impl.range),
        callLocation,
        dispatcherOuterEnv,
        originFunctionTemplata,
        abstractFunctionPrototype.paramTypes.indices.map(_ => None)
          .updated(abstractIndex, Some(dispatcherPlaceholderedAbstractParamType))
          .toVector) match {
        case EvaluateFunctionFailure(x) => {
          throw CompileErrorExceptionT(CouldntEvaluateFunction(List(range), x))
        }
        case efs@EvaluateFunctionSuccess(_, _) => efs
      }
    val dispatcherParams =
      originFunctionTemplata.function.params.map(_.pattern.coordRune).map(vassertSome(_)).map(_.rune)
        .map(rune => expectCoordTemplata(dispatcherInnerInferences(rune)).coord)
    val dispatcherId =
      dispatcherTemplateId.copy(localName =
        dispatcherTemplateId.localName.makeFunctionName(interner, keywords, dispatcherPlaceholders.toVector, dispatcherParams))

    val dispatcherInnerEnv =
      GeneralEnvironmentT.childOf(
        interner,
        dispatcherOuterEnv,
        dispatcherId,
        dispatcherInnerInferences
          .map({ case (nameS, templata) =>
            interner.intern(RuneNameT((nameS))) -> TemplataEnvEntry(templata)
          }).toVector)
    val dispatcherRuneToFunctionBound = TemplataCompiler.assembleRuneToFunctionBound(dispatcherInnerEnv.templatas)
    val dispatcherRuneToImplBound = TemplataCompiler.assembleRuneToImplBound(dispatcherInnerEnv.templatas)

    // Step 3: Figure Out Dependent And Independent Runes, see FODAIR.

    val implRuneToImplPlaceholderAndCasePlaceholder =
      U.mapWithIndex[(IRuneS, ITemplataT[ITemplataType]), (IRuneS, IdT[IPlaceholderNameT], ITemplataT[ITemplataType])](
        impl.templata.impl.genericParams.map(_.rune.rune).toVector
          .zip(impl.instantiatedId.localName.templateArgs.toIterable)
          .zip(impl.runeIndexToIndependence)
          .filter({ case ((implRune, templata), independent) => independent }) // Only grab independent runes for the case
          .map({ case ((implRune, templata), independent) => implRune -> templata }),
        { case (index, (implRune, implPlaceholderTemplata)) =>
          val caseRune = CaseRuneFromImplS(implRune)

          val implPlaceholderId =
            TemplataCompiler.getPlaceholderTemplataId(implPlaceholderTemplata)
          val casePlaceholder =
            createOverridePlaceholderMimicking(
              coutputs, implPlaceholderTemplata, dispatcherInnerEnv, index, caseRune)
          (implRune, implPlaceholderId, casePlaceholder)
        })
    val implRuneToCasePlaceholder =
      implRuneToImplPlaceholderAndCasePlaceholder
        .map({ case (implRune, implPlaceholder, casePlaceholder) => (implRune, casePlaceholder) })
    val implPlaceholderToCasePlaceholder =
      implRuneToImplPlaceholderAndCasePlaceholder
        .map({ case (implRune, implPlaceholder, casePlaceholder) => (implPlaceholder, casePlaceholder) })


    // ??? Supposedly here is where we'd pull in some bounds from the impl if it has any, like if it
    // inherits any from the struct (see ONBIFS).

    // This is needed for pulling the impl bound args in for the override dispatcher's case.
    val implSubCitizenReachableBoundsToCaseSubCitizenReachableBounds =
      impl.reachableBoundsFromSubCitizen
        .map({
          case PrototypeT(IdT(packageCoord, initSteps, fb @ FunctionBoundNameT(_, _, _)), _) => {
            val funcBoundId = IdT(packageCoord, initSteps, fb)
            val casePlaceholderedReachableFuncBoundId =
              TemplataCompiler.substituteTemplatasInFunctionBoundId(
                coutputs,
                interner,
                keywords,
                impl.templateId,
                (implPlaceholderToDispatcherPlaceholder ++ implPlaceholderToCasePlaceholder).map(_._2),
                // These are bounds we're bringing in from the sub citizen.
                InheritBoundsFromTypeItself,
                funcBoundId)
            funcBoundId -> casePlaceholderedReachableFuncBoundId
          }
          case other => vimpl(other)
        }).toMap

    // Step 4: Figure Out Struct For Case, see FOSFC.

    // Avoids a collision below
    vassert(!implRuneToCasePlaceholder.exists(_._1 == impl.templata.impl.interfaceKindRune.rune))

    // See IBFCS, ONBIFS and NBIFP for why we need reachableBoundsFromSubCitizen in our below env.
    val (caseConclusions, reachableBoundsFromSubCitizen) =
      implCompiler.resolveImpl(
        coutputs,
        List(range),
        callLocation,
        dispatcherInnerEnv,
        // For example, if we're doing the Milano case:
        //   impl<I, J, K, L> ISpaceship<I, J, K> for Milano<I, J, K, L>;
        // Then right now we're feeding in:
        //   interfaceKindRune = ISpaceship<dis$0, dis$1, dis$2>
        //   L = case$3
        // so we should get a complete solve.
        Vector(
          InitialKnown(
            impl.templata.impl.interfaceKindRune,
            // We may be feeding in something interesting like IObserver<Opt<T>> here should be fine,
            // the impl will receive it and match it to its own unknown runes appropriately.
            KindTemplataT(dispatcherPlaceholderedInterface))) ++
        implRuneToCasePlaceholder
          .map({ case (rune, templata) => InitialKnown(RuneUsage(range, rune), templata) }),
        impl.templata
        // Keep in mind, at the end of the solve, we're actually pulling in some reachable bounds
        // from the struct we're solving for here.
      ) match {
        case Ok(CompleteCompilerSolve(_, conclusions, _, reachableBoundsFromFullSolve)) => (conclusions, reachableBoundsFromFullSolve)
        case Err(e) => throw CompileErrorExceptionT(CouldntEvaluatImpl(List(range), e))
      }
    val caseSubCitizen =
      expectKindTemplata(
        vassertSome(caseConclusions.get(impl.templata.impl.subCitizenRune.rune)))
        .kind.expectCitizen()

    // Step 5: Assemble the Case Environment For Resolving the Override, see ACEFRO

    // We don't do this here:
    //   coutputs.getInnerEnvForFunction(abstractFunctionPrototype.fullName)
    // because that will get the original declaration's inner env.
    // We want an environment with the above inferences instead.
    val overrideImpreciseName =
      vassertSome(TemplatasStore.getImpreciseName(interner, abstractFunctionPrototype.id.localName))
    val dispatcherCaseEnv =
      GeneralEnvironmentT.childOf(
        interner,
        dispatcherInnerEnv,
        dispatcherInnerEnv.id.addStep(
          interner.intern(
            OverrideDispatcherCaseNameT(implRuneToCasePlaceholder.map(_._2)))),
        // See IBFCS, ONBIFS and NBIFP for why we need these bounds in our env here.
        reachableBoundsFromSubCitizen.zipWithIndex.map({ case (templata, num) =>
          interner.intern(RuneNameT(ReachablePrototypeRuneS(num))) -> TemplataEnvEntry(templata)
        }))

    // Step 6: Use Case Environment to Find Override, see UCEFO.

    // Now we have the `Raza<launch$1, launch$0>`, so we can try to resolve that `launch(myBike)`,
    // in other words look for a `launch(&Raza<launch$1, launch$0>)`.
    // This is also important for getting the instantiation bounds for that particular invocation,
    // so that the instantiator can later know how to properly convey the abstract function's
    // bounds (such as a drop(T)void) down to the override's bounds.
    val overridingParamCoord = dispatcherPlaceholderedAbstractParamType.copy(kind = caseSubCitizen)
    val overrideFunctionParamTypes =
      dispatchingFuncPrototype.prototype.paramTypes
        .updated(abstractIndex, overridingParamCoord)
    // We need the abstract function's conclusions because it contains knowledge of the
    // existence of certain things like concept functions, see NFIEFRO.
    val foundFunction =
      overloadCompiler.findFunction(
        // It's like the abstract function is the one calling the override.
        // This is important so the override can see existing concept functions, see NAFEWRO.
        dispatcherCaseEnv,
        coutputs,
        List(range, impl.templata.impl.range),
        callLocation,
        overrideImpreciseName,
        Vector.empty,
        Vector.empty,
        RegionT(),
        overrideFunctionParamTypes,
        Vector(
          coutputs.getOuterEnvForType(List(range, impl.templata.impl.range), interfaceTemplateId),
          coutputs.getOuterEnvForType(List(range, impl.templata.impl.range), subCitizenTemplateId)),
        true) match {
        case Err(e) => throw CompileErrorExceptionT(CouldntFindOverrideT(List(range, impl.templata.impl.range), e))
        case Ok(x) => x
      }
    vassert(coutputs.getInstantiationBounds(foundFunction.prototype.prototype.id).nonEmpty)

    OverrideT(
      dispatcherId,
      implPlaceholderToDispatcherPlaceholder.toVector,
      implPlaceholderToCasePlaceholder.toVector,
      implSubCitizenReachableBoundsToCaseSubCitizenReachableBounds,
      dispatcherRuneToFunctionBound,
      dispatcherRuneToImplBound,
      dispatcherCaseEnv.id,
      foundFunction.prototype.prototype)
  }

}
