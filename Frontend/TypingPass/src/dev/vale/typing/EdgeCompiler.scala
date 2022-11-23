package dev.vale.typing

//import dev.vale.astronomer.{GlobalFunctionFamilyNameS, INameS, INameA, ImmConcreteDestructorImpreciseNameA, ImmConcreteDestructorNameA, ImmInterfaceDestructorImpreciseNameS}
//import dev.vale.astronomer.VirtualFreeImpreciseNameS
import dev.vale.postparsing.rules.RuneUsage
import dev.vale.{Err, Interner, Keywords, Ok, RangeS, StrI, U, vassert, vassertOne, vassertSome, vcurious, vfail, vimpl, vpass, vwat}
import dev.vale.postparsing._
import dev.vale.typing.ast.{InterfaceEdgeBlueprint, PrototypeT}
import dev.vale.typing.env.{GeneralEnvironment, IEnvironment, TemplataEnvEntry, TemplataLookupContext, TemplatasStore}
import dev.vale.typing.types._
import dev.vale.typing.ast._
import dev.vale.typing.citizen.ImplCompiler
import dev.vale.typing.function.FunctionCompiler
import dev.vale.typing.function.FunctionCompiler.{EvaluateFunctionFailure, EvaluateFunctionSuccess}
import dev.vale.typing.names._
import dev.vale.typing.templata.ITemplata.{expectCoord, expectCoordTemplata, expectKindTemplata}
import dev.vale.typing.templata.{CoordTemplata, FunctionTemplata, ITemplata, KindTemplata, MutabilityTemplata, PlaceholderTemplata}
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
    Vector[InterfaceEdgeBlueprint],
    Map[
      IdT[IInterfaceNameT],
      Map[
        IdT[ICitizenNameT],
        EdgeT]]) = {
    val interfaceEdgeBlueprints =
      makeInterfaceEdgeBlueprints(coutputs)

    val itables =
      interfaceEdgeBlueprints.map(interfaceEdgeBlueprint => {
        val interfacePlaceholderedFullName = interfaceEdgeBlueprint.interface
        val interfaceTemplateFullName = TemplataCompiler.getInterfaceTemplate(interfacePlaceholderedFullName)
        val interfaceFullName =
          coutputs.lookupInterface(interfaceTemplateFullName).instantiatedInterface.fullName
        val interfaceDefinition = coutputs.lookupInterface(interfaceTemplateFullName)
//        val interfacePlaceholderedCitizen = interfaceDefinition.placeholderedInterface
        val overridingImpls = coutputs.getChildImplsForSuperInterfaceTemplate(interfaceTemplateFullName)
        val overridingCitizenToFoundFunction =
          overridingImpls.map(overridingImpl => {
            val overridingCitizenTemplateFullName = overridingImpl.subCitizenTemplateFullName
            val superInterfaceWithSubCitizenPlaceholders = overridingImpl.superInterface


            val foundFunctions =
              interfaceEdgeBlueprint.superFamilyRootHeaders.map({ case (abstractFunctionPrototype, abstractIndex) =>
                val overrride =
                  lookForOverride(
                    coutputs,
                    overridingImpl,
                    interfaceTemplateFullName,
                    overridingCitizenTemplateFullName,
                    abstractFunctionPrototype,
                    abstractIndex)
                abstractFunctionPrototype.fullName -> overrride
              })
            val overridingCitizen = overridingImpl.subCitizen
            vassert(coutputs.getInstantiationBounds(overridingCitizen.fullName).nonEmpty)
            val superInterfaceFullName = overridingImpl.superInterface.fullName
            vassert(coutputs.getInstantiationBounds(superInterfaceFullName).nonEmpty)
            val edge =
              EdgeT(
                overridingImpl.instantiatedFullName,
                overridingCitizen,
                overridingImpl.superInterface.fullName,
                overridingImpl.runeToFuncBound,
                overridingImpl.runeToImplBound,
                foundFunctions.toMap)
            val overridingCitizenDef = coutputs.lookupCitizen(overridingCitizenTemplateFullName)
            overridingCitizenDef.instantiatedCitizen.fullName -> edge
          }).toMap
        interfaceFullName -> overridingCitizenToFoundFunction
      }).toMap
    (interfaceEdgeBlueprints, itables)
  }

  private def makeInterfaceEdgeBlueprints(coutputs: CompilerOutputs): Vector[InterfaceEdgeBlueprint] = {
    val x1 =
      coutputs.getAllFunctions().flatMap({ case function =>
        function.header.getAbstractInterface match {
          case None => Vector.empty
          case Some(abstractInterface) => {
            val abstractInterfaceTemplate =
              TemplataCompiler.getInterfaceTemplate(abstractInterface.fullName)
            Vector(abstractInterfaceTemplate -> function)
          }
        }
      })
    val x2 = x1.groupBy(_._1)
    val x3 = x2.mapValues(_.map(_._2))
    val x4 =
      x3.map({ case (interfaceTemplateFullName, functions) =>
        // Sort so that the interface's internal methods are first and in the same order
        // they were declared in. It feels right, and vivem also depends on it
        // when it calls array generators/consumers' first method.
        val interfaceDef = coutputs.getAllInterfaces().find(_.templateName == interfaceTemplateFullName).get
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
        (interfaceTemplateFullName -> orderedMethods)
      })
    val abstractFunctionHeadersByInterfaceTemplateFullNameWithoutEmpties = x4
    // Some interfaces would be empty and they wouldn't be in
    // abstractFunctionsByInterfaceWithoutEmpties, so we add them here.
    val abstractFunctionHeadersByInterfaceTemplateFullName =
      abstractFunctionHeadersByInterfaceTemplateFullNameWithoutEmpties ++
        coutputs.getAllInterfaces().map({ case i =>
          (i.templateName -> abstractFunctionHeadersByInterfaceTemplateFullNameWithoutEmpties.getOrElse(i.templateName, Set()))
        })

    val interfaceEdgeBlueprints =
      abstractFunctionHeadersByInterfaceTemplateFullName
        .map({ case (interfaceTemplateFullName, functionHeaders2) =>
          InterfaceEdgeBlueprint(
            coutputs.lookupInterface(interfaceTemplateFullName).instantiatedInterface.fullName,
            // This is where they're given order and get an implied index
            functionHeaders2.toVector)
        })
    interfaceEdgeBlueprints.toVector
  }

  def createOverridePlaceholderMimicking(
    coutputs: CompilerOutputs,
    originalTemplataToMimic: ITemplata[ITemplataType],
    dispatcherOuterEnv: IEnvironment,
    index: Int,
    rune: IRuneS):
  ITemplata[ITemplataType] = {
    // Need New Special Placeholders for Abstract Function Override Case (NNSPAFOC)
    //
    // One would think that we could just conjure up some placeholders under the abstract
    // function's name, like:
    //
    //   val placeholderName =
    //     PlaceholderNameT(PlaceholderTemplateNameT(placeholderToSubstitution.size))
    //   val placeholderFullName =
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
      interner.intern(PlaceholderNameT(
        interner.intern(PlaceholderTemplateNameT(index, rune))))
    val placeholderFullName = dispatcherOuterEnv.fullName.addStep(placeholderName)
    // And, because it's new, we need to declare it and its environment.
    val placeholderTemplateFullName =
      TemplataCompiler.getPlaceholderTemplate(placeholderFullName)

    coutputs.declareType(placeholderTemplateFullName)
    coutputs.declareTypeOuterEnv(
      placeholderTemplateFullName,
      GeneralEnvironment.childOf(interner, dispatcherOuterEnv, placeholderTemplateFullName))

    val result =
      originalTemplataToMimic match {
        case PlaceholderTemplata(_, tyype) => {
          PlaceholderTemplata(placeholderFullName, tyype)
        }
        case KindTemplata(PlaceholderT(originalPlaceholderFullName)) => {
          val originalPlaceholderTemplateFullName = TemplataCompiler.getPlaceholderTemplate(originalPlaceholderFullName)
          val mutability = coutputs.lookupMutability(originalPlaceholderTemplateFullName)
          coutputs.declareTypeMutability(placeholderTemplateFullName, mutability)
          KindTemplata(PlaceholderT(placeholderFullName))
        }
        case CoordTemplata(CoordT(ownership, PlaceholderT(originalPlaceholderFullName))) => {
          val originalPlaceholderTemplateFullName = TemplataCompiler.getPlaceholderTemplate(originalPlaceholderFullName)
          val mutability = coutputs.lookupMutability(originalPlaceholderTemplateFullName)
          coutputs.declareTypeMutability(placeholderTemplateFullName, mutability)
          CoordTemplata(CoordT(ownership, PlaceholderT(placeholderFullName)))
        }
        case other => vwat(other)
      }
    result
  }

  private def lookForOverride(
    coutputs: CompilerOutputs,
    impl: ImplT,
    interfaceTemplateFullName: IdT[IInterfaceTemplateNameT],
    subCitizenTemplateFullName: IdT[ICitizenTemplateNameT],
    abstractFunctionPrototype: PrototypeT,
    abstractIndex: Int):
  OverrideT = {
    val abstractFuncTemplateFullName =
      TemplataCompiler.getFunctionTemplate(abstractFunctionPrototype.fullName)
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
      coutputs.getOuterEnvForFunction(abstractFuncTemplateFullName)

    val dispatcherTemplateName =
      interner.intern(OverrideDispatcherTemplateNameT(impl.templateFullName))
    val dispatcherTemplateFullName =
      abstractFuncTemplateFullName.addStep(dispatcherTemplateName)
    val dispatcherOuterEnv =
      GeneralEnvironment.childOf(
        interner,
        abstractFuncOuterEnv,
        dispatcherTemplateFullName)

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
      U.mapWithIndex[ITemplata[ITemplataType], (IdT[PlaceholderNameT], ITemplata[ITemplataType])](
        impl.instantiatedFullName.localName.templateArgs.toVector
        .zip(impl.runeIndexToIndependence)
        .filter({ case (templata, independent) => !independent }) // Only grab dependent runes
        .map({ case (templata, independent) => templata }),
        { case (implPlaceholderIndex, implPlaceholder) =>
          val implPlaceholderFullName = TemplataCompiler.getPlaceholderTemplataFullName(implPlaceholder)
          // Sanity check we're in an impl template, we're about to replace it with a function template
          implPlaceholderFullName.initSteps.last match { case _: IImplTemplateNameT => case _ => vwat() }

          val implRune = implPlaceholderFullName.localName.template.rune
          val dispatcherRune = DispatcherRuneFromImplS(implRune)

          val dispatcherPlaceholder =
            createOverridePlaceholderMimicking(
              coutputs, implPlaceholder, dispatcherOuterEnv, implPlaceholderIndex, dispatcherRune)
          (implPlaceholderFullName, dispatcherPlaceholder)
        })
    val dispatcherPlaceholders = implPlaceholderToDispatcherPlaceholder.map(_._2)

    implPlaceholderToDispatcherPlaceholder.map(_._1).foreach(x => vassert(x.initFullName(interner) == impl.templateFullName))

    val dispatcherPlaceholderedInterface =
      expectKindTemplata(
        TemplataCompiler.substituteTemplatasInKind(
          coutputs,
          interner,
          keywords,
          impl.templateFullName,
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
      functionCompiler.evaluateGenericLightFunctionParentForPrototype(
        coutputs,
        List(range, impl.templata.impl.range),
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
    val dispatcherFullName =
      dispatcherTemplateFullName.copy(localName =
        dispatcherTemplateFullName.localName.makeFunctionName(interner, keywords, dispatcherPlaceholders.toVector, dispatcherParams))

    val dispatcherInnerEnv =
      GeneralEnvironment.childOf(
        interner,
        dispatcherOuterEnv,
        dispatcherFullName,
        dispatcherInnerInferences
          .map({ case (nameS, templata) =>
            interner.intern(RuneNameT((nameS))) -> TemplataEnvEntry(templata)
          }).toVector)
    val dispatcherRuneToFunctionBound = TemplataCompiler.assembleRuneToFunctionBound(dispatcherInnerEnv.templatas)
    val dispatcherRuneToImplBound = TemplataCompiler.assembleRuneToImplBound(dispatcherInnerEnv.templatas)

    // Step 3: Figure Out Dependent And Independent Runes, see FODAIR.

    val implRuneToImplPlaceholderAndCasePlaceholder =
      U.mapWithIndex[(IRuneS, ITemplata[ITemplataType]), (IRuneS, IdT[PlaceholderNameT], ITemplata[ITemplataType])](
        impl.templata.impl.genericParams.map(_.rune.rune).toVector
          .zip(impl.instantiatedFullName.localName.templateArgs.toIterable)
          .zip(impl.runeIndexToIndependence)
          .filter({ case ((implRune, templata), independent) => independent }) // Only grab independent runes for the case
          .map({ case ((implRune, templata), independent) => implRune -> templata }),
        { case (index, (implRune, implPlaceholderTemplata)) =>
          val caseRune = CaseRuneFromImplS(implRune)

          val implPlaceholderFullName =
            TemplataCompiler.getPlaceholderTemplataFullName(implPlaceholderTemplata)
          val casePlaceholder =
            createOverridePlaceholderMimicking(
              coutputs, implPlaceholderTemplata, dispatcherInnerEnv, index, caseRune)
          (implRune, implPlaceholderFullName, casePlaceholder)
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
            val funcBoundFullName = IdT(packageCoord, initSteps, fb)
            val casePlaceholderedReachableFuncBoundFullName =
              TemplataCompiler.substituteTemplatasInFunctionBoundFullName(
                coutputs,
                interner,
                keywords,
                impl.templateFullName,
                (implPlaceholderToDispatcherPlaceholder ++ implPlaceholderToCasePlaceholder).map(_._2),
                // These are bounds we're bringing in from the sub citizen.
                InheritBoundsFromTypeItself,
                funcBoundFullName)
            funcBoundFullName -> casePlaceholderedReachableFuncBoundFullName
          }
          case other => vimpl(other)
        }).toMap

    // Step 4: Figure Out Struct For Case, see FOSFC.

    // Avoids a collision below
    vassert(!implRuneToCasePlaceholder.exists(_._1 == impl.templata.impl.interfaceKindRune.rune))

    // See IBFCS, ONBIFS and NBIFP for why we need reachableBoundsFromSubCitizen in our below env.
    val (caseConclusions, reachableBoundsFromSubCitizen) =
      implCompiler.solveImplForCall(
        coutputs,
        List(range),
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
            KindTemplata(dispatcherPlaceholderedInterface))) ++
        implRuneToCasePlaceholder
          .map({ case (rune, templata) => InitialKnown(RuneUsage(range, rune), templata) }),
        impl.templata,
        false,
        true
        // Keep in mind, at the end of the solve, we're actually pulling in some reachable bounds
        // from the struct we're solving for here.
      ) match {
        case CompleteCompilerSolve(_, conclusions, _, reachableBoundsFromFullSolve) => (conclusions, reachableBoundsFromFullSolve)
        case IncompleteCompilerSolve(_, _, _, _) => vfail()
        case fcs @ FailedCompilerSolve(_, _, _) => {
          throw CompileErrorExceptionT(CouldntEvaluatImpl(List(range), fcs))
        }
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
      vassertSome(TemplatasStore.getImpreciseName(interner, abstractFunctionPrototype.fullName.localName))
    val dispatcherCaseEnv =
      GeneralEnvironment.childOf(
        interner,
        dispatcherInnerEnv,
        dispatcherInnerEnv.fullName.addStep(
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
        overrideImpreciseName,
        Vector.empty,
        Vector.empty,
        overrideFunctionParamTypes,
        Vector(
          coutputs.getOuterEnvForType(List(range, impl.templata.impl.range), interfaceTemplateFullName),
          coutputs.getOuterEnvForType(List(range, impl.templata.impl.range), subCitizenTemplateFullName)),
        true,
        true) match {
        case Err(e) => throw CompileErrorExceptionT(CouldntFindOverrideT(List(range, impl.templata.impl.range), e))
        case Ok(x) => x
      }
    vassert(coutputs.getInstantiationBounds(foundFunction.prototype.prototype.fullName).nonEmpty)

    OverrideT(
      dispatcherFullName,
      implPlaceholderToDispatcherPlaceholder.toVector,
      implPlaceholderToCasePlaceholder.toVector,
      implSubCitizenReachableBoundsToCaseSubCitizenReachableBounds,
      dispatcherRuneToFunctionBound,
      dispatcherRuneToImplBound,
      dispatcherCaseEnv.fullName,
      foundFunction.prototype.prototype)
  }

}
