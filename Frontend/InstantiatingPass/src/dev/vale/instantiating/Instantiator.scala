package dev.vale.instantiating

import dev.vale.instantiating.ast._
import dev.vale.options.GlobalOptions
import dev.vale._
import dev.vale.instantiating.ast.ITemplataI.expectRegionTemplata
import dev.vale.postparsing._
import dev.vale.typing.TemplataCompiler._
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import dev.vale.typing.names._
import dev.vale.typing.templata.ITemplataT._
import dev.vale.typing.templata._
import dev.vale.typing.types._

import scala.collection.immutable.Map
import scala.collection.mutable

case class DenizenBoundToDenizenCallerBoundArgS(
  funcIdToBoundArgPrototype: Map[IdT[FunctionBoundNameT], PrototypeI[sI]],
  boundParamImplIdToBoundArgImplId: Map[IdT[ImplBoundNameT], IdI[sI, IImplNameI[sI]]]) {

  def plus(that: DenizenBoundToDenizenCallerBoundArgS): DenizenBoundToDenizenCallerBoundArgS = {
    val DenizenBoundToDenizenCallerBoundArgS(
      thatFuncIdToBoundArgPrototype,
      thatBoundParamImplIdToBoundArgImplId
    ) = that

    DenizenBoundToDenizenCallerBoundArgS(
      U.unionMapsExpectNoConflict[IdT[FunctionBoundNameT], PrototypeI[sI]](funcIdToBoundArgPrototype, thatFuncIdToBoundArgPrototype, _==_),
      U.unionMapsExpectNoConflict[IdT[ImplBoundNameT], IdI[sI, IImplNameI
          [sI]]](boundParamImplIdToBoundArgImplId, thatBoundParamImplIdToBoundArgImplId, _==_))
  }
}

class InstantiatedOutputs() {
  val functions: mutable.HashMap[IdI[cI, IFunctionNameI[cI]], FunctionDefinitionI] =
    mutable.HashMap()
  val structs: mutable.HashMap[IdI[cI, IStructNameI[cI]], StructDefinitionI] = mutable.HashMap()
  val interfacesWithoutMethods: mutable.HashMap[IdI[cI, IInterfaceNameI[cI]], InterfaceDefinitionI] = mutable.HashMap()

  // We can get some recursion if we have a self-referential struct like:
  //   struct Node<T> { value T; next Opt<Node<T>>; }
  // So we need these to short-circuit that nonsense.
  val structToMutability: mutable.HashMap[IdI[cI, IStructNameI[cI]], MutabilityI] = mutable.HashMap()
  val structToBounds: mutable.HashMap[IdI[sI, IStructNameI[sI]], DenizenBoundToDenizenCallerBoundArgS] = mutable.HashMap()
  val interfaceToMutability: mutable.HashMap[IdI[cI, IInterfaceNameI[cI]], MutabilityI] = mutable.HashMap()
  val interfaceToBounds: mutable.HashMap[IdI[sI, IInterfaceNameI[sI]], DenizenBoundToDenizenCallerBoundArgS] = mutable.HashMap()
  val implToMutability: mutable.HashMap[IdI[cI, IImplNameI[cI]], MutabilityI] = mutable.HashMap()
  val implToBounds: mutable.HashMap[IdI[sI, IImplNameI[sI]], DenizenBoundToDenizenCallerBoundArgS] = mutable.HashMap()

  //  val immKindToDestructor: mutable.HashMap[KindT, PrototypeT] =
  //    mutable.HashMap[KindT, PrototypeT]()

  // We already know from the hinputs that Some<T> implements Opt<T>.
  // In this map, we'll know that Some<int> implements Opt<int>, Some<bool> implements Opt<bool>, etc.
  val interfaceToImpls: mutable.HashMap[IdI[cI, IInterfaceNameI[cI]], mutable.HashSet[(IdT[IImplNameT], IdI[cI, IImplNameI[cI]])]] =
  mutable.HashMap()
  val interfaceToAbstractFuncToVirtualIndex: mutable.HashMap[IdI[cI, IInterfaceNameI[cI]], mutable.HashMap[PrototypeI[cI], Int]] =
    mutable.HashMap()
  val impls:
    mutable.HashMap[
      IdI[cI, IImplNameI[cI]],
      (ICitizenIT[cI], IdI[cI, IInterfaceNameI[cI]], DenizenBoundToDenizenCallerBoundArgS, InstantiationBoundArgumentsI)] =
    mutable.HashMap()
  // We already know from the hinputs that Opt<T has drop> has func drop(T).
  // In this map, we'll know that Opt<int> has func drop(int).
  val abstractFuncToBounds: mutable.HashMap[IdI[cI, IFunctionNameI[cI]], (DenizenBoundToDenizenCallerBoundArgS, InstantiationBoundArgumentsI)] =
    mutable.HashMap()
  // This map collects all overrides for every impl. We'll use it to assemble vtables soon.
  val interfaceToImplToAbstractPrototypeToOverride:
    mutable.HashMap[IdI[cI, IInterfaceNameI[cI]], mutable.HashMap[IdI[cI, IImplNameI[cI]], mutable.HashMap[PrototypeI[cI], PrototypeI[cI]]]] =
    mutable.HashMap()

  // These are new impls and abstract funcs we discover for interfaces.
  // As we discover a new impl or a new abstract func, we'll later need to stamp a lot more overrides either way.
  val newImpls: mutable.Queue[(IdT[IImplNameT], IdI[nI, IImplNameI[nI]], InstantiationBoundArgumentsI)] = mutable.Queue()
  // The int is a virtual index
  val newAbstractFuncs: mutable.Queue[(PrototypeT[IFunctionNameT], PrototypeI[nI], Int, IdI[cI, IInterfaceNameI[cI]], InstantiationBoundArgumentsI)] = mutable.Queue()
  val newFunctions: mutable.Queue[(PrototypeT[IFunctionNameT], PrototypeI[nI], InstantiationBoundArgumentsI, Option[DenizenBoundToDenizenCallerBoundArgS])] = mutable.Queue()

  def addMethodToVTable(
    implId: IdI[cI, IImplNameI[cI]],
    superInterfaceId: IdI[cI, IInterfaceNameI[cI]],
    abstractFuncPrototype: PrototypeI[cI],
    overrride: PrototypeI[cI]
  ) = {
    val map =
      interfaceToImplToAbstractPrototypeToOverride
        .getOrElseUpdate(superInterfaceId, mutable.HashMap())
        .getOrElseUpdate(implId, mutable.HashMap())
    vassert(!map.contains(abstractFuncPrototype))
    map.put(abstractFuncPrototype, overrride)
  }
}

object Instantiator {
  def translate(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: HinputsT):
  HinputsI = {
    val monouts = new InstantiatedOutputs()
    val instantiator = new Instantiator(opts, interner, keywords, hinputs, monouts)
    instantiator.translate()
  }
}

class Instantiator(
  opts: GlobalOptions,
  interner: Interner,
  keywords: Keywords,
  hinputs: HinputsT,
  monouts: InstantiatedOutputs) {

  def translate():
  HinputsI = {

    val HinputsT(
    interfacesT,
    structsT,
    functionsT,
    //      oldImmKindToDestructorT,
    interfaceToEdgeBlueprintsT,
    interfaceToSubCitizenToEdgeT,
    instantiationNameToFunctionBoundToRuneT,
    kindExportsT,
    functionExportsT,
    functionExternsT) = hinputs

    val kindExportsC =
      kindExportsT.map({ case KindExportT(range, tyype, exportPlaceholderedIdT, exportedName) =>

        val exportIdS =
          translateId[ExportNameT, ExportNameI[sI]](
            exportPlaceholderedIdT,
            { case ExportNameT(ExportTemplateNameT(codeLoc), _) =>
              ExportNameI(ExportTemplateNameI(codeLoc), RegionTemplataI(0))
            })
        val exportIdC =
          RegionCollapserIndividual.collapseExportId(RegionCounter.countExportId(exportIdS), exportIdS)

        val exportTemplateIdT = TemplataCompiler.getExportTemplate(exportPlaceholderedIdT)


        val substitutions =
          Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]](
            exportTemplateIdT -> assemblePlaceholderMap(exportPlaceholderedIdT, exportIdS))

        val denizenBoundToDenizenCallerSuppliedThing = DenizenBoundToDenizenCallerBoundArgS(Map(), Map())
        val kindIT =
          translateKind(
            exportPlaceholderedIdT, denizenBoundToDenizenCallerSuppliedThing, substitutions, vimpl(), tyype)
        val kindCT = RegionCollapserIndividual.collapseKind(kindIT)

        KindExportI(range, kindCT, exportIdC, exportedName)
      })

    val functionExportsC =
      functionExportsT.map({ case FunctionExportT(range, prototypeT, exportPlaceholderedIdT, exportedName) =>
        val perspectiveRegionT =
          exportPlaceholderedIdT.localName.templateArgs.last match {
            case PlaceholderTemplataT(IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _)), RegionTemplataType()) => {
              RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r), RegionTemplataType()))
            }
            case _ => vwat()
          }

        val exportIdS =
          translateId[ExportNameT, ExportNameI[sI]](
            exportPlaceholderedIdT,
            { case ExportNameT(ExportTemplateNameT(codeLoc), RegionT(PlaceholderTemplataT(_, RegionTemplataType()))) =>
              ExportNameI(ExportTemplateNameI(codeLoc), RegionTemplataI(0))
            })
        val exportIdC =
          RegionCollapserIndividual.collapseExportId(RegionCounter.countExportId(exportIdS), exportIdS)

        val exportTemplateIdT = TemplataCompiler.getExportTemplate(exportPlaceholderedIdT)

        val substitutions =
          Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]](
            exportTemplateIdT -> assemblePlaceholderMap(exportPlaceholderedIdT, exportIdS))

        val (_, prototypeC) =
          translatePrototype(
            exportPlaceholderedIdT,
            DenizenBoundToDenizenCallerBoundArgS(Map(), Map()),
            substitutions,
            perspectiveRegionT,
            prototypeT)
        Collector.all(prototypeC, { case PlaceholderTemplataT(_, _) => vwat() })
        FunctionExportI(range, prototypeC, exportIdC, exportedName)
      })

    val funcExternsC =
      functionExternsT.map({ case FunctionExternT(range, externPlaceholderedIdT, prototypeT, externedName) =>
        val perspectiveRegionT =
          externPlaceholderedIdT.localName.templateArgs.last match {
            case PlaceholderTemplataT(IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _)), RegionTemplataType()) => {
              RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r), RegionTemplataType()))
            }
            case _ => vwat()
          }

        val externIdS =
          translateId[ExternNameT, ExternNameI[sI]](
            externPlaceholderedIdT,
            { case ExternNameT(ExternTemplateNameT(codeLoc), RegionT(PlaceholderTemplataT(_, RegionTemplataType()))) =>
              ExternNameI(ExternTemplateNameI(codeLoc), RegionTemplataI(0))
            })
        val externIdC =
          RegionCollapserIndividual.collapseExternId(RegionCounter.countExternId(externIdS), externIdS)

        val externTemplateIdT = TemplataCompiler.getExternTemplate(externPlaceholderedIdT)

        val substitutions =
          Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]](
            externTemplateIdT -> assemblePlaceholderMap(externPlaceholderedIdT, externIdS))

        val (_, prototypeC) =
          translatePrototype(
            externPlaceholderedIdT,
            DenizenBoundToDenizenCallerBoundArgS(Map(), Map()),
            substitutions,
            perspectiveRegionT,
            prototypeT)
        Collector.all(prototypeC, { case PlaceholderTemplataT(_, _) => vwat() })
        FunctionExternI(prototypeC, externedName)
      })

    while ({
      // We make structs and interfaces eagerly as we come across them
      // if (monouts.newStructs.nonEmpty) {
      //   val newStructName = monouts.newStructs.dequeue()
      //   DenizentranslateStructDefinition(opts, interner, keywords, hinputs, monouts, newStructName)
      //   true
      // } else if (monouts.newInterfaces.nonEmpty) {
      //   val (newInterfaceName, calleeRuneToSuppliedPrototype) = monouts.newInterfaces.dequeue()
      //   DenizentranslateInterfaceDefinition(
      //     opts, interner, keywords, hinputs, monouts, newInterfaceName, calleeRuneToSuppliedPrototype)
      //   true
      // } else
      if (monouts.newFunctions.nonEmpty) {
        val (newFuncIdT, newFuncIdN, instantiationBoundArgs, maybeDenizenBoundToDenizenCallerSuppliedThing) =
          monouts.newFunctions.dequeue()
        translateFunction(
          opts, interner, keywords, hinputs, monouts, newFuncIdT, newFuncIdN, instantiationBoundArgs,
          maybeDenizenBoundToDenizenCallerSuppliedThing)
        true
      } else if (monouts.newImpls.nonEmpty) {
        val (implIdT, implIdN, instantiationBoundsForUnsubstitutedImpl) = monouts.newImpls.dequeue()
        translateImpl(
          opts, interner, keywords, hinputs, monouts, implIdT, implIdN, instantiationBoundsForUnsubstitutedImpl)
        true
      } else if (monouts.newAbstractFuncs.nonEmpty) {
        val (abstractFuncT, abstractFunc, virtualIndex, interfaceId, instantiationBoundArgs) =
          monouts.newAbstractFuncs.dequeue()
        translateAbstractFunc(
          opts, interner, keywords, hinputs, monouts, interfaceId, abstractFuncT, abstractFunc, virtualIndex, instantiationBoundArgs)
        true
      } else {
        false
      }
    }) {}

    //    interfaceToEdgeBlueprints.foreach({ case (interfacePlaceholderedId, edge) =>
    //      val instantiator = new DenizenInstantiator(interner, monouts, interfacePlaceholderedId)
    //
    //    })

    val interfaceEdgeBlueprints =
      monouts.interfaceToAbstractFuncToVirtualIndex.map({ case (interface, abstractFuncPrototypes) =>
        interface -> InterfaceEdgeBlueprintI(interface, abstractFuncPrototypes.toVector)
      }).toMap

    val interfaces =
      monouts.interfacesWithoutMethods.values.map(interface => {
        val InterfaceDefinitionI(ref, attributes, weakable, mutability, _, _, _) = interface
        InterfaceDefinitionI(
          ref, attributes, weakable, mutability, Map(), Map(),
          vassertSome(
            monouts.interfaceToAbstractFuncToVirtualIndex.get(ref.id)).toVector)
      })

    val interfaceToSubCitizenToEdge =
      monouts.interfaceToImpls.map({ case (interface, impls) =>
        interface ->
          impls.map({ case (implIdT, implIdI) =>
            val (subCitizen, parentInterface, _, _) = vassertSome(monouts.impls.get(implIdI))
            vassert(parentInterface == interface)
            val abstractFuncToVirtualIndex =
              vassertSome(monouts.interfaceToAbstractFuncToVirtualIndex.get(interface))
            val abstractFuncPrototypeToOverridePrototype =
              abstractFuncToVirtualIndex.map({ case (abstractFuncPrototype, virtualIndex) =>
                val overrride =
                  vassertSome(
                    vassertSome(
                      vassertSome(monouts.interfaceToImplToAbstractPrototypeToOverride.get(interface))
                        .get(implIdI))
                      .get(abstractFuncPrototype))

                vassert(
                  abstractFuncPrototype.id.localName.parameters(virtualIndex).kind !=
                    overrride.id.localName.parameters(virtualIndex).kind)

                abstractFuncPrototype.id -> overrride
              })
            val edge =
              EdgeI(
                implIdI,
                subCitizen,
                interface,
                Map(),
                Map(),
                abstractFuncPrototypeToOverridePrototype.toMap)
            subCitizen.id -> edge
          }).toMap
      }).toMap

    val resultHinputs =
      HinputsI(
        interfaces.toVector,
        monouts.structs.values.toVector,
        monouts.functions.values.toVector,
        //      monouts.immKindToDestructor.toMap,
        interfaceEdgeBlueprints,
        interfaceToSubCitizenToEdge,
//        Map(),
        kindExportsC,
        functionExportsC,
        funcExternsC)

    resultHinputs
  }

  def translateId[T <: INameT, Y <: INameI[sI]](idT: IdT[T], func: T => Y): IdI[sI, Y] = {
    val IdT(packageCoord, initStepsT, localNameT) = idT
    IdI[sI, Y](packageCoord, initStepsT.map(translateName(_)), func(localNameT))
  }

  def translateExportName(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    exportNameT: ExportNameT):
  ExportNameI[sI] = {
    val ExportNameT(ExportTemplateNameT(codeLoc), region) = exportNameT
    ExportNameI(
      ExportTemplateNameI(codeLoc),
      ITemplataI.expectRegionTemplata(
        translateTemplata(
          denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, region.region)))
  }

  def translateExportTemplateName(exportTemplateNameT: ExportTemplateNameT): ExportTemplateNameI[sI] = {
    val ExportTemplateNameT(codeLoc) = exportTemplateNameT
    ExportTemplateNameI(codeLoc)
  }

  def translateName(t: INameT): INameI[sI] = {
    vimpl()
  }

  def collapseAndTranslateInterfaceDefinition(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: HinputsT,
    monouts: InstantiatedOutputs,
    interfaceIdT: IdT[IInterfaceNameT],
    interfaceIdS: IdI[sI, IInterfaceNameI[sI]],
    instantiationBoundArgs: InstantiationBoundArgumentsI):
  Unit = {
    val interfaceTemplateIdT = TemplataCompiler.getInterfaceTemplate(interfaceIdT)

    val interfaceDefT =
      vassertOne(hinputs.interfaces.filter(_.templateName == interfaceTemplateIdT))

    val denizenBoundToDenizenCallerSuppliedThing =
      assembleInstantiationBoundParamToArg(
        interfaceDefT.instantiationBoundParams,
        instantiationBoundArgs)
    monouts.interfaceToBounds.get(interfaceIdS) match {
      case Some(x) => {
        vcurious(x == denizenBoundToDenizenCallerSuppliedThing)
      }
      case None =>
    }
    monouts.interfaceToBounds.put(interfaceIdS, denizenBoundToDenizenCallerSuppliedThing)

    val topLevelDenizenId =
      getTopLevelDenizenId(interfaceIdT)
    val topLevelDenizenTemplateId =
      TemplataCompiler.getTemplate(topLevelDenizenId)

    val substitutions =
      Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]](
        topLevelDenizenTemplateId ->
            assemblePlaceholderMap(
              // One would imagine we'd get interfaceId.last.templateArgs here, because that's the interface
              // we're about to monomorphize. However, only the top level denizen has placeholders, see LHPCTLD.
              // This interface might not be the top level denizen, such as if it's a lambda.
              // TODO(regions): might be obsolete?
              interfaceDefT.instantiatedCitizen.id,
              interfaceIdS))
    //    val instantiator =
//      new Instantiator(
//        opts,
//        interner,
//        keywords,
//        hinputs,
//        monouts,
    //        interfaceTemplate,
//        interfaceIdT,
//        denizenBoundToDenizenCallerSuppliedThing)
    val interfaceIdC =
      RegionCollapserIndividual.collapseInterfaceId(interfaceIdS)

    translateCollapsedInterfaceDefinition(
      interfaceIdT, denizenBoundToDenizenCallerSuppliedThing, substitutions, interfaceIdC, interfaceDefT)
  }

  def assembleInstantiationBoundParamToArg(
      instantiationBoundParams: InstantiationBoundArgumentsT[FunctionBoundNameT, ImplBoundNameT],
      instantiationBoundArgs: InstantiationBoundArgumentsI): DenizenBoundToDenizenCallerBoundArgS = {
    vassert(instantiationBoundArgs.runeToFunctionBoundArg.size == instantiationBoundParams.runeToBoundPrototype.size)
    vassert(
      instantiationBoundArgs.callerRuneToCalleeRuneToReachableFunc.count(_._2.nonEmpty) ==
          instantiationBoundParams.runeToCitizenRuneToReachablePrototype.count(_._2.citizenRuneToReachablePrototype.nonEmpty))
    vassert(instantiationBoundArgs.runeToImplBoundArg.size == instantiationBoundParams.runeToBoundImpl.size)
    DenizenBoundToDenizenCallerBoundArgS(
      instantiationBoundArgs.runeToFunctionBoundArg.map({ case (calleeRune, suppliedFunctionI) =>
        vassertSome(instantiationBoundParams.runeToBoundPrototype.get(calleeRune)).id -> suppliedFunctionI
      }) ++
      instantiationBoundArgs.callerRuneToCalleeRuneToReachableFunc.flatMap({ case (callerRune, calleeRuneToReachableFunc) =>
        if (calleeRuneToReachableFunc.nonEmpty) {
          val m = vassertSome(instantiationBoundParams.runeToCitizenRuneToReachablePrototype.get(callerRune))
          vassert(m.citizenRuneToReachablePrototype.size == calleeRuneToReachableFunc.size)
          calleeRuneToReachableFunc.map({ case (calleeRune, reachableFuncI) =>
            val reachableFuncT = vassertSome(m.citizenRuneToReachablePrototype.get(calleeRune))
            reachableFuncT.id -> reachableFuncI
          })
        } else {
          List()
        }
      }),
      instantiationBoundArgs.runeToImplBoundArg.map({ case (calleeRune, suppliedImplT) =>
        vassertSome(instantiationBoundParams.runeToBoundImpl.get(calleeRune)) -> suppliedImplT
      }))
  }

  def assembleCalleeDenizenFunctionBounds(
    // This is from the receiver's perspective, they have some runes for their required functions.
    calleeRuneToReceiverBoundT: Map[IRuneS, IdT[FunctionBoundNameT]],
    // This is a map from the receiver's rune to the bound that the caller is supplying.
    calleeRuneToSuppliedPrototype: Map[IRuneS, PrototypeI[sI]]
  ): Map[IdT[FunctionBoundNameT], PrototypeI[sI]] = {
    calleeRuneToSuppliedPrototype.map({ case (calleeRune, suppliedFunctionT) =>
      vassertSome(calleeRuneToReceiverBoundT.get(calleeRune)) -> suppliedFunctionT
    })
  }

  def assembleCalleeDenizenImplBounds(
    // This is from the receiver's perspective, they have some runes for their required functions.
    calleeRuneToReceiverBoundT: Map[IRuneS, IdT[ImplBoundNameT]],
    // This is a map from the receiver's rune to the bound that the caller is supplying.
    calleeRuneToSuppliedImpl: Map[IRuneS, IdI[sI, IImplNameI[sI]]]
  ): Map[IdT[ImplBoundNameT], IdI[sI, IImplNameI[sI]]] = {
    calleeRuneToSuppliedImpl.map({ case (calleeRune, suppliedFunctionT) =>
      vassertSome(calleeRuneToReceiverBoundT.get(calleeRune)) -> suppliedFunctionT
    })
  }

  def collapseAndTranslateStructDefinition(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: HinputsT,
    monouts: InstantiatedOutputs,
    structIdT: IdT[IStructNameT],
    structIdS: IdI[sI, IStructNameI[sI]],
    instantiationBoundArgs: InstantiationBoundArgumentsI):
  Unit = {
    if (opts.sanityCheck) {
      vassert(Collector.all(structIdS, { case KindPlaceholderNameT(_) => }).isEmpty)
    }

    val structTemplate = TemplataCompiler.getStructTemplate(structIdT)

    val structDefT = findStruct(hinputs, structIdT)

    val denizenBoundToDenizenCallerSuppliedThing =
      assembleInstantiationBoundParamToArg(structDefT.instantiationBoundParams, instantiationBoundArgs)
      // DenizenBoundToDenizenCallerBoundArgS(
      //   assembleCalleeDenizenFunctionBounds(
      //     .runeToFunctionBoundArg.mapValues(_.id), instantiationBoundArgs.runeToFunctionBoundArg),
      //   assembleCalleeDenizenImplBounds(
      //     structDefT.instantiationBoundParams.runeToBoundImpl, instantiationBoundArgs.runeToBoundImpl))
    monouts.structToBounds.get(structIdS) match {
      case Some(x) => {
        vcurious(x == denizenBoundToDenizenCallerSuppliedThing)
        return
      }
      case None =>
    }
    monouts.structToBounds.put(structIdS, denizenBoundToDenizenCallerSuppliedThing)

    val topLevelDenizenId =
      getTopLevelDenizenId(structIdT)
    val topLevelDenizenTemplateId =
      TemplataCompiler.getTemplate(topLevelDenizenId)

    val substitutions =
      Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]](
        topLevelDenizenTemplateId ->
          assemblePlaceholderMap(
            // One would imagine we'd get structId.last.templateArgs here, because that's the struct
            // we're about to monomorphize. However, only the top level denizen has placeholders, see LHPCTLD.
            // This struct might not be the top level denizen, such as if it's a lambda.
            // DO NOT SUBMIT might be obsolete
            structDefT.instantiatedCitizen.id,
            structIdS))
//    val instantiator =
//      new Instantiator(
//        opts,
//        interner,
//        keywords,
//        hinputs,
//        monouts,
//        structTemplate,
//        structIdT,
//        denizenBoundToDenizenCallerSuppliedThing)
    val structIdC =
      RegionCollapserIndividual.collapseStructId(structIdS)
    translateCollapsedStructDefinition(
      structIdT, denizenBoundToDenizenCallerSuppliedThing, substitutions, structIdT, structIdC, structDefT)
  }

  private def findStruct(hinputs: HinputsT, structId: IdT[IStructNameT]) = {
    vassertOne(
      hinputs.structs
        .filter(structT => {
          TemplataCompiler.getSuperTemplate(structT.instantiatedCitizen.id) ==
            TemplataCompiler.getSuperTemplate(structId)
        }))
  }

  private def findInterface(hinputs: HinputsT, interfaceId: IdT[IInterfaceNameT]) = {
    vassertOne(
      hinputs.interfaces
        .filter(interfaceT => {
          TemplataCompiler.getSuperTemplate(interfaceT.instantiatedCitizen.id) ==
            TemplataCompiler.getSuperTemplate(interfaceId)
        }))
  }

  private def findImpl(hinputs: HinputsT, implId: IdT[IImplNameT]): EdgeT = {
    vassertOne(
      hinputs.interfaceToSubCitizenToEdge.values.flatMap(subCitizenToEdge => {
        subCitizenToEdge.values.filter(edge => {
          TemplataCompiler.getSuperTemplate(edge.edgeId) ==
              TemplataCompiler.getSuperTemplate(implId)
        })
      }))
  }

  def translateOverride(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: HinputsT,
    monouts: InstantiatedOutputs,
    implIdT: IdT[IImplNameT],
    implIdC: IdI[cI, IImplNameI[cI]],
    abstractFuncPrototypeT: PrototypeT[IFunctionNameT],
    abstractFuncPrototypeC: PrototypeI[cI],
    abstractFuncInstantiationBoundArgs: InstantiationBoundArgumentsI):
  Unit = {
    // Our ultimate goal in here is to make a PrototypeI[cI] for the override.
    // To do that, we're going to compile a dispatcher function given an impl, see CDFGI.
    //
    // For example:
    //
    //   abstract func launch<X, Y, Z>(self &ISpaceship<X, Y, Z>, bork X) where exists drop(Y)void; // Known as "abst"
    //   struct Raza<A, B, C> { ... }
    //   impl<I, J> ISpaceship<int, I, J> for Raza<I, J>; // This impl known as "ri"
    //   func launch<Y, Z>(self &Raza<Y, Z>, bork int) where exists drop(Y)void { ... } // Known as "over"
    //
    // we're going to pretend that there's instead a "dispatcher" function that's just forwarding calls based on the
    // type of self:
    //
    //   func dispatcher<int, str, bool>(self &ISpaceship<int, str, bool>, bork int) where exists drop(str)void {
    //     match self {
    //       raza &Raza<Y, Z> => launch(raza, bork)
    //       ...
    //     }
    //   }
    //
    // this dispatcher function is also known as "dis". Note how we know the concrete types right now (int, str, bool),
    // that's because we're in the instantiator and we know those.
    // Our ultimate goal is to find (and instantiate) the prototype for that launch(raza, bork) in there.

    // First step: gather a bunch of details about the given impl, super interface (ISpaceship), sub citizen (Raza)
    // and the abstract function (virtual func launch).
    val implTemplateId = TemplataCompiler.getImplTemplate(implIdT)
    val edgeT =
      vassertOne(
        hinputs.interfaceToSubCitizenToEdge
          .flatMap(_._2.values)
          .filter(edge => TemplataCompiler.getImplTemplate(edge.edgeId) == implTemplateId))
    val EdgeT(
      edgeId,
      edgeSubCitizen,
      edgeSuperInterface,
      _,
      edgeAbstractFuncToOverrideFunc
    ) = edgeT

    val abstractFuncTemplateName = TemplataCompiler.getFunctionTemplate(abstractFuncPrototypeT.id)
    val abstractFuncPlaceholderedNameT =
      vassertSome(
        hinputs.functions
          .find(func => TemplataCompiler.getFunctionTemplate(func.header.id) == abstractFuncTemplateName))
        .header.id

    // Luckily, the typing phase knows what the override is.
    // In this example, it's func launch<Y, Z>(self &Raza<Y, Z>, bork int)
    // We just have to instantiate it, given that someone called the abstract function with certain known types.
    // If they called launch(&ISpaceship<int, str, bool>, int) then we know:
    // - abst$A = int
    // - abst$B = str
    // - abst$C = bool
    // But we need to know over$Y and over$Z.

    val OverrideT(
    dispatcherIdT,
    implPlaceholderToDispatcherPlaceholder,
    implPlaceholderToCasePlaceholder,
        dispatcherAndCasePlaceholderedImplReachablePrototypes,
    dispatcherCaseIdT,
        overridePrototypeT,
        dispatcherInstantiationBoundParams) =
      vassertSome(edgeAbstractFuncToOverrideFunc.get(abstractFuncPlaceholderedNameT))
    val dispatcherTemplateId = TemplataCompiler.getTemplate(dispatcherIdT)

    // We currently know the abstract function's caller's runes and how they map to the instantiated values,
    // - abst$A = int
    // - abst$B = str
    // - abst$C = bool
    // ...but this dispatcher function is different than the abstract function. The dispatcher function has its own
    // runes, here:
    // - dis$I
    // - dis$J
    // So we'll map the abstract function's caller's runes to the dispatcher's runes.
    // TODO: Feels like these can be simplified somehow...
    val dispatcherPlaceholderIdToSuppliedTemplata =
      dispatcherIdT.localName.templateArgs
        .map(dispatcherPlaceholderTemplata => {
          val dispatcherPlaceholderId =
            TemplataCompiler.getPlaceholderTemplataId(dispatcherPlaceholderTemplata)
          val implPlaceholder =
            vassertSome(
              // This implPlaceholderToDispatcherPlaceholder has a map of the impl runes to the dispatcher runes, like:
              // - ri$I -> dis$I
              // - ri$J -> dis$J
              implPlaceholderToDispatcherPlaceholder.find(_._2 == dispatcherPlaceholderTemplata))._1
          val IdT(_, _, KindPlaceholderNameT(KindPlaceholderTemplateNameT(index, rune))) = implPlaceholder
          // Here we're grabbing it from the instantiated impl that we're overriding, here ri<bool, str>.
          val templataC = implIdC.localName.templateArgs(index)
          // This is a collapsed, but it needs to be subjective from this dispatcher's perspective.

          // TODO(regions): Figure out how to turn this into an sI.
          dispatcherPlaceholderId -> vimpl(templataC)
        })
    // In this case we'll end up with:
    //   dis/dis$I -> bool
    //   dis/dis$J -> str
    // However, such as in the Milano case, there might be some independent runes that we need to
    // figure out how to supply. We'll conceptually grab these from the receiving struct.
    // The impl knows the receiving struct.
    val dispatcherCasePlaceholderIdToSuppliedTemplata =
      dispatcherCaseIdT.localName.independentImplTemplateArgs.zipWithIndex.map({
        case (casePlaceholderTemplata, index) => {
          val casePlaceholderId =
            TemplataCompiler.getPlaceholderTemplataId(casePlaceholderTemplata)
          val implPlaceholder =
            vassertSome(
              implPlaceholderToCasePlaceholder.find(_._2 == casePlaceholderTemplata))._1
          val IdT(_, _, KindPlaceholderNameT(KindPlaceholderTemplateNameT(index, rune))) = implPlaceholder
          val templata = implIdC.localName.templateArgs(index)
          // TODO(regions): Figure out how to turn this into an sI.
          casePlaceholderId -> vimpl(templata.asInstanceOf[ITemplataI[sI]])
        }
      })
    val dispatcherPlaceholderIdToSuppliedTemplataMap = dispatcherPlaceholderIdToSuppliedTemplata.toMap
    val dispatcherCasePlaceholderIdToSuppliedTemplataMap = dispatcherCasePlaceholderIdToSuppliedTemplata.toMap
    // Sanity check there's no overlap
    vassert(
      (dispatcherPlaceholderIdToSuppliedTemplataMap ++ dispatcherCasePlaceholderIdToSuppliedTemplataMap).size ==
        dispatcherPlaceholderIdToSuppliedTemplataMap.size + dispatcherCasePlaceholderIdToSuppliedTemplataMap.size)

    val caseSubstitutions =
      Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]](
        dispatcherTemplateId -> dispatcherPlaceholderIdToSuppliedTemplataMap,
        dispatcherIdT -> dispatcherCasePlaceholderIdToSuppliedTemplataMap)

    // Now that we have the values for the dispatcher placeholders, let's get the values for the function/impl bounds.

    // The instantiator's next step for an override is to bring in some bound functions from the impl that already
    // exists.
    //
    // Let's say we had this:
    //   interface IObserver<W> { }
    //   abstract func handleLaunch<X>(self virtual &IObserver<LaunchEvent<X>>);
    //   struct Firefly<Y> where exists drop(T)void { }
    //   impl<Z> IObserver<LaunchEvent<Z>> for Firefly<Engine<Z>>; // inherits drop(Engine<Z>)
    //   func handleLaunch<T>(self &Ship<LaunchEvent<T>>) { ... }
    // What we need is the sub citizen for this interface, in terms of the abstract function.
    //
    // To do that, the first step was to pretend we're compiling the abstract function, like so:
    //   func handleLaunch<launch$X>(self &IObserver<LaunchEvent<launch$X>>) { ... }
    // and have it feed the interface self param into the impl to make it resolve the struct, like so:
    //   impl<Z> (IObserver<LaunchEvent<launch$X>> = IObserver<LaunchEvent<Z>>) for Firefly<Engine<Z>>;
    // which solved for Firefly<Engine<launch$X>> and predicts that some bounds should exist:
    // - ZD = func impl.predicted:drop(Firefly<Engine<$launchX>>);
    //
    // At this point, we need to conjure some bounds from that, knowing that the instantiator can fill the actual ones
    // from the impl.
    // We *could* use the PredictedFunctionNameT that come out of the solver, buuut let's not. Let's turn it from:
    // - ZD = func impl                   .predicted:drop(Firefly<Engine<$launchX>>);
    // into:
    // - ZD = func dispatcher:handleLaunch.bound:drop    (Firefly<Engine<$launchX>>);
    // remembering the impl rune it came from.

    // Grab the actual instantiated bounds that were used to make the impl.
    val implRuneToImplInstantiationBoundArgs = vassertSome(monouts.impls.get(implIdC))._4
    val boundParamPrototypeTToBoundArgPrototypeIFromImpl =
      // This is how the typing phase referred to the impl's bound prototypes.
      // We're making a map from those names to the actual prototypes the impl was instantiated with.
      dispatcherAndCasePlaceholderedImplReachablePrototypes.toVector.flatMap({
        case (runeInImpl, citizenRuneToBound) => {
          citizenRuneToBound.toVector.map({
            case (runeInCitizen, prototypeT@PrototypeT(IdT(_, _, FunctionBoundNameT(FunctionBoundTemplateNameT(_), _, _)), _)) => {
              val prototypeI =
                vassertSome(
                  vassertSome(
                    implRuneToImplInstantiationBoundArgs.callerRuneToCalleeRuneToReachableFunc
                        .get(runeInImpl))
                      .get(runeInCitizen))
              prototypeT.id -> prototypeI
            }
          })
        }
      })
          .toMap
    // TODO: Catch impls up

    val dispatcherInstantiationBoundParamsToArgs =
    // Here we're matching up the runes of the callsite's instantiation bound args with the
    // runes of the abstract function definition's instantiation bound params.
      assembleInstantiationBoundParamToArg(
        dispatcherInstantiationBoundParams,
        abstractFuncInstantiationBoundArgs)

    // Here we're adding in any bounds that the struct/impl know about that the call site might not know about.
    val caseInstantiationBoundParamsToArgs =
      dispatcherInstantiationBoundParamsToArgs
          .plus(
            DenizenBoundToDenizenCallerBoundArgS(
              boundParamPrototypeTToBoundArgPrototypeIFromImpl,
              Map())) // TODO: Catch impls up

    val (overridePrototypeS, overridePrototypeC) =
      translatePrototype(
        dispatcherCaseIdT,
        caseInstantiationBoundParamsToArgs,
        caseSubstitutions,
        vimpl(),
        overridePrototypeT)

    val superInterfaceId = vassertSome(monouts.impls.get(implIdC))._2

    monouts.addMethodToVTable(implIdC, superInterfaceId, abstractFuncPrototypeC, overridePrototypeC)
  }

  def translateImpl(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: HinputsT,
    monouts: InstantiatedOutputs,
    implIdT: IdT[IImplNameT],
    implIdN: IdI[nI, IImplNameI[nI]],
    instantiationBoundsForUnsubstitutedImpl: InstantiationBoundArgumentsI):
  Unit = {
    // This works because the sI/cI are never actually used in these instances, they are just a
    // compile-time type-system bit of tracking, see CCFCTS.
    val implIdS: IdI[sI, IImplNameI[sI]] = implIdN
    val implIdC = RegionCollapserIndividual.collapseImplId(implIdS)

    val implTemplateId = TemplataCompiler.getImplTemplate(implIdT)
    val implDefinition =
      vassertOne(
        hinputs.interfaceToSubCitizenToEdge
          .flatMap(_._2.values)
          .filter(edge => {
            //TemplataCompiler.getSuperTemplate(edge.edgeId) == TemplataCompiler.getSuperTemplate(implTemplateId), doesnt fix it
            TemplataCompiler.getImplTemplate(edge.edgeId) == implTemplateId
          }))

    val denizenBoundToDenizenCallerSuppliedThing =
      assembleInstantiationBoundParamToArg(
        implDefinition.instantiationBoundParams,
        instantiationBoundsForUnsubstitutedImpl)

    val substitutions =
      Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]](
        implTemplateId -> assemblePlaceholderMap(implDefinition.edgeId, implIdS))
    translateCollapsedImplDefinition(
      implIdT,
      instantiationBoundsForUnsubstitutedImpl,
      denizenBoundToDenizenCallerSuppliedThing,
      substitutions,
      implIdT,
      implIdS,
      implIdC,
      implDefinition)
  }

  // DO NOT SUBMIT figure out better name
  def translateFunction(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: HinputsT,
    monouts: InstantiatedOutputs,
    desiredPrototypeT: PrototypeT[IFunctionNameT],
    desiredPrototypeN: PrototypeI[nI],
    suppliedBoundArgs: InstantiationBoundArgumentsI,
    // This is only Some if this is a lambda. This will contain the prototypes supplied to the top
    // level denizen by its own caller, see LCNBAFA.
    maybeDenizenBoundToDenizenCallerSuppliedThing: Option[DenizenBoundToDenizenCallerBoundArgS]):
  FunctionDefinitionI = {
    // This works because the sI/cI are never actually used in these instances, they are just a
    // compile-time type-system bit of tracking, see CCFCTS.
    val desiredPrototypeS: PrototypeI[sI] = desiredPrototypeN
    val desiredPrototypeC =
      RegionCollapserIndividual.collapsePrototype(desiredPrototypeS)

    val desiredFuncSuperTemplateName = TemplataCompiler.getSuperTemplate(desiredPrototypeT.id)
    val funcT =
      vassertOne(
        hinputs.functions
          .filter(funcT => TemplataCompiler.getSuperTemplate(funcT.header.id) == desiredFuncSuperTemplateName))


    val denizenBoundToDenizenCallerSuppliedThingFromDenizenItself =
      maybeDenizenBoundToDenizenCallerSuppliedThing.getOrElse({
        assembleInstantiationBoundParamToArg(funcT.instantiationBoundParams, suppliedBoundArgs)
        // DenizenBoundToDenizenCallerBoundArgS(
        //   // This is a top level denizen, and someone's calling it. Assemble the bounds!
        //   assembleCalleeDenizenFunctionBounds(funcT.instantiationBoundParams.runeToFunctionBoundArg.mapValues(_.id), suppliedBoundArgs.runeToFunctionBoundArg),
        //   // This is a top level denizen, and someone's calling it. Assemble the bounds!
        //   assembleCalleeDenizenImplBounds(funcT.instantiationBoundParams.runeToBoundImpl, suppliedBoundArgs.runeToBoundImpl))
      })
    val argsM = desiredPrototypeS.id.localName.parameters.map(_.kind)
    val paramsT = funcT.header.params.map(_.tyype.kind)
    // val denizenBoundToDenizenCallerSuppliedThingFromParams =
    //   paramsT.zip(argsM).flatMap({ case (a, x) =>
    //     hoistBoundsFromParameter(hinputs, monouts, a, x)
    //   })

    val denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams =
      denizenBoundToDenizenCallerSuppliedThingFromDenizenItself
      // Vector(denizenBoundToDenizenCallerSuppliedThingFromDenizenItself) ++
      //   denizenBoundToDenizenCallerSuppliedThingFromParams

    val denizenBoundToDenizenCallerSuppliedThing =
        denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
      // DenizenBoundToDenizenCallerBoundArgS(
      //   denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
      //     .map(_.funcIdToBoundArgPrototype)
      //     .reduceOption(_ ++ _).getOrElse(Map()),
      //   denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
      //       .map(_.funcReachableToCallerSuppliedReachableArgFunc)
      //       .reduceOption(_ ++ _).getOrElse(Map()),
      //   denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
      //     .map(_.boundParamImplIdToBoundArgImplId)
      //     .reduceOption(_ ++ _).getOrElse(Map()))


    val topLevelDenizenId =
      getTopLevelDenizenId(desiredPrototypeT.id)
    val topLevelDenizenTemplateId =
      TemplataCompiler.getTemplate(topLevelDenizenId)
    // One would imagine we'd get structId.last.templateArgs here, because that's the struct
    // we're about to monomorphize. However, only the top level denizen has placeholders, see LHPCTLD.
    val topLevelDenizenPlaceholderIndexToTemplata =
    topLevelDenizenId.localName.templateArgs

    val substitutions =
      Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]](
        topLevelDenizenTemplateId ->
          assemblePlaceholderMap(funcT.header.id, desiredPrototypeS.id))
//    val instantiator =
//      new Instantiator(
//        opts,
//        interner,
//        keywords,
//        hinputs,
//        monouts,
//        funcTemplateNameT,
//        desiredPrototypeT.id,
//        denizenBoundToDenizenCallerSuppliedThing)

    val monomorphizedFuncT =
      translateCollapsedFunction(
        desiredPrototypeT.id, denizenBoundToDenizenCallerSuppliedThing, substitutions, desiredPrototypeC, funcT)

    vassert(desiredPrototypeC.returnType == monomorphizedFuncT.header.returnType)

    monomorphizedFuncT
  }

  def translateAbstractFunc(
      opts: GlobalOptions,
      interner: Interner,
      keywords: Keywords,
      hinputs: HinputsT,
      monouts: InstantiatedOutputs,
      interfaceIdC: IdI[cI, IInterfaceNameI[cI]],
      desiredAbstractPrototypeT: PrototypeT[IFunctionNameT],
      desiredAbstractPrototypeN: PrototypeI[nI],
      virtualIndex: Int,
      suppliedBoundArgs: InstantiationBoundArgumentsI):
  Unit = {
    // This works because the sI/cI are never actually used in these instances, they are just a
    // compile-time type-system bit of tracking, see CCFCTS.
    val desiredAbstractPrototypeS: PrototypeI[sI] = desiredAbstractPrototypeN
    val desiredAbstractPrototypeC =
      RegionCollapserIndividual.collapsePrototype(desiredAbstractPrototypeS)

    val desiredSuperTemplateId = TemplataCompiler.getSuperTemplate(desiredAbstractPrototypeT.id)
    val funcT =
      vassertOne(
        hinputs.functions
            .filter(funcT => TemplataCompiler.getSuperTemplate(funcT.header.id) == desiredSuperTemplateId))


    val denizenBoundToDenizenCallerSuppliedThingFromDenizenItself =
      assembleInstantiationBoundParamToArg(funcT.instantiationBoundParams, suppliedBoundArgs)
        // DenizenBoundToDenizenCallerBoundArgS(
        //   // This is a top level denizen, and someone's calling it. Assemble the bounds!
        //   assembleCalleeDenizenFunctionBounds(funcT.instantiationBoundParams.runeToFunctionBoundArg.mapValues(_.id), suppliedBoundArgs.runeToFunctionBoundArg),
        //   // This is a top level denizen, and someone's calling it. Assemble the bounds!
        //   assembleCalleeDenizenImplBounds(funcT.instantiationBoundParams.runeToBoundImpl, suppliedBoundArgs.runeToBoundImpl))
    val argsM = desiredAbstractPrototypeS.id.localName.parameters.map(_.kind)
    val paramsT = funcT.header.params.map(_.tyype.kind)
    // val denizenBoundToDenizenCallerSuppliedThingFromParams =
    //   paramsT.zip(argsM).flatMap({ case (a, x) =>
    //     hoistBoundsFromParameter(hinputs, monouts, a, x)
    //   })

    // val denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams =
    //   Vector(denizenBoundToDenizenCallerSuppliedThingFromDenizenItself) ++
    //       denizenBoundToDenizenCallerSuppliedThingFromParams

    val denizenBoundToDenizenCallerSuppliedThing = denizenBoundToDenizenCallerSuppliedThingFromDenizenItself
      // DenizenBoundToDenizenCallerBoundArgS(
      //   denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
      //       .map(_.funcIdToBoundArgPrototype)
      //       .reduceOption(_ ++ _).getOrElse(Map()),
      //   denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
      //       .map(_.funcReachableToCallerSuppliedReachableArgFunc)
      //       .reduceOption(_ ++ _).getOrElse(Map()),
      //   denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
      //       .map(_.boundParamImplIdToBoundArgImplId)
      //       .reduceOption(_ ++ _).getOrElse(Map()))


    vassert(!monouts.abstractFuncToBounds.contains(desiredAbstractPrototypeC.id))
    monouts.abstractFuncToBounds.put(desiredAbstractPrototypeC.id, (denizenBoundToDenizenCallerSuppliedThing, suppliedBoundArgs))

    val abstractFuncs = vassertSome(monouts.interfaceToAbstractFuncToVirtualIndex.get(interfaceIdC))
    vassert(!abstractFuncs.contains(desiredAbstractPrototypeC))
    abstractFuncs.put(desiredAbstractPrototypeC, virtualIndex)

    vassertSome(monouts.interfaceToImpls.get(interfaceIdC)).foreach({ case (implT, impl) =>
      translateOverride(opts, interner, keywords, hinputs, monouts, implT, impl, desiredAbstractPrototypeT, desiredAbstractPrototypeC, suppliedBoundArgs)
    })
  }

  def assemblePlaceholderMap(
      idT: IdT[INameT],
      idS: IdI[sI, INameI[sI]]):
  Map[IdT[IPlaceholderNameT], ITemplataI[sI]] = {
    (idT.initNonPackageId() match {
      case None => Map()
      case Some(initNonPackageIdT) => {
        assemblePlaceholderMap(initNonPackageIdT, vassertSome(idS.initNonPackageId()))
      }
    }) ++
    (idT match {
      case IdT(packageCoordT, initStepsT, localNameT: IInstantiationNameT) => {
        val instantiationIdT = IdT(packageCoordT, initStepsT, localNameT)
        val instantiationIdS =
          idS match {
            case IdI(packageCoordI, initStepsI, localNameUncastedI) => {
              if (localNameUncastedI.isInstanceOf[IInstantiationNameI[sI]]) {
                IdI(packageCoordI, initStepsI, localNameUncastedI.asInstanceOf[IInstantiationNameI[sI]])
              } else {
                vwat()
              }
            }
            case _ => vwat()
          }
        assemblePlaceholderMapInner(instantiationIdT, instantiationIdS)
      }
      case _ => Map()
    })
  }

  def assemblePlaceholderMapInner(
    idT: IdT[IInstantiationNameT],
    idS: IdI[sI, IInstantiationNameI[sI]]):
  Map[IdT[IPlaceholderNameT], ITemplataI[sI]] = {
    val placeholderedName = idT
//    val placeholderedName =
//      idT match {
//        case IdT(_, _, localName : IStructNameT) => {
//          hinputs.lookupStructByTemplate(localName.template).instantiatedCitizen.id
//        }
//        case IdT(_, _, localName : IInterfaceNameT) => {
//          hinputs.lookupInterfaceByTemplate(localName.template).instantiatedInterface.id
//        }
//        case IdT(_, _, localName : IFunctionNameT) => {
//          vassertSome(hinputs.lookupFunction(localName.template)).header.id
//        }
//        case IdT(_, _, localName : IImplNameT) => {
//          hinputs.lookupImplByTemplate(localName.template).edgeId
//        }
//        case IdT(_, _, localName : ExportNameT) => {
//          vassertOne(
//            hinputs.kindExports.filter(_.id.localName.template == localName.template).map(_.id) ++
//              hinputs.functionExports.filter(_.exportId.localName.template == localName.template).map(_.exportId))
//        }
//      }

      placeholderedName.localName.templateArgs
        .zip(idS.localName.templateArgs)
        .flatMap({
          case (
              CoordTemplataT(CoordT(placeholderOwnership, RegionT(PlaceholderTemplataT(regionPlaceholderId @ IdT(_, _, rpn), RegionTemplataType())), kindT)),
              c @ CoordTemplataI(regionI, _)) => {
                kindT match {
                  case KindPlaceholderT(kindPlaceholderId) => {
                    vregionmut()
                    // vassert(placeholderOwnership == OwnT || placeholderOwnership == ShareT)
                    // In "Array has" test, we actually have a placeholder thats a borrow.

                // // We might need to do something with placeholderRegion here, but I think we can just
                // // assume it correctly matches up with the coord's region. The typing phase should have
                // // made sure it matches up nicely.
                // // If we hit this vimpl, then we might need to find some way to hand in the region,
                // // even though we lost that in the translation to IdI which has no regions. We might be
                // // able to scavenge it from the name, though it might be tricky to get the region of
                // // region-less primitives. Perhaps we can assume theyre the same region as their
                // // parent template?
                // val regionTemplata =
                //   maybeRegionHeight.map(x => RegionTemplataI[sI](x)).getOrElse(vimpl())
                vcurious(regionI.pureHeight <= 0) // These are subjective, but they should be negative
                List(
                  (regionPlaceholderId -> regionI),
                  (kindPlaceholderId -> c))
              }
              // This could be e.g. *i32 and *!i32, in other words the template arg is already populated. This can
              // happen if we're processing a lambda's name.
              // placeholderedName *doesn't* contain a placeholder like one might normally expect:
              //   test/main.lam:0:34.__call{lam:0:34, *i32}<__call$0>    (doesn't have this)
              // Instead placeholderedName might be:
              //   test/main.lam:0:34.__call{lam:0:34, *i32}<*i32>
              // ...because the typing phase already filled it in.
              // Theoretically the typing phase could have stripped that out before now, maybe. Don't know.
              // Either way, it is there.
              // Just ignore it, we don't need a mapping for it.
              case _ => {
                List()
              }
            }
          }
          case (KindTemplataT(KindPlaceholderT(placeholderId)), kindTemplataI) => {
            List((placeholderId -> kindTemplataI))
          }
          case (PlaceholderTemplataT(placeholderId, tyype), templataI) => {
            List((placeholderId -> templataI))
          }
          case (MutabilityTemplataT(MutableT),MutabilityTemplataI(MutableI)) |
               (MutabilityTemplataT(ImmutableT),MutabilityTemplataI(ImmutableI)) => {
            // We once got a `mut` for the placeholdered name's templata.
            // That's because we do some specialization for arrays still.
            // They don't come with a placeholder, so ignore them.
            List()
          }
          case other => vimpl(other)
        })
        .toMap
  }

  def translateStructMember(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    member: IStructMemberT):
  (CoordI[sI], StructMemberI) = {
    member match {
      case NormalStructMemberT(name, variability, tyype) => {
        val (memberSubjectiveIT, memberTypeI) =
          tyype match {
            case ReferenceMemberTypeT(unsubstitutedCoord) => {
              val typeS =
                translateCoord(
                  denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, unsubstitutedCoord)
              val result =
                ReferenceMemberTypeI(
                  RegionCollapserIndividual.collapseCoord(typeS.coord))
              (typeS, result)
            }
            case AddressMemberTypeT(unsubstitutedCoord) => {
              val typeS =
                translateCoord(
                  denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, unsubstitutedCoord)
              val result = AddressMemberTypeI(RegionCollapserIndividual.collapseCoord(typeS.coord))
              (typeS, result)
            }
          }
        val nameS = translateVarName(name)
        val memberC =
          StructMemberI(
            RegionCollapserIndividual.collapseVarName(nameS),
            translateVariability(variability),
            memberTypeI)
        (memberSubjectiveIT.coord, memberC)
      }
      case VariadicStructMemberT(name, tyype) => {
        vimpl()
      }
    }
  }

  def translateVariability(x: VariabilityT): VariabilityI = {
    x match {
      case VaryingT => VaryingI
      case FinalT => FinalI
    }
  }

  def translateMutability(m: MutabilityT): MutabilityI = {
    m match {
      case MutableT => MutableI
      case ImmutableT => ImmutableI
    }
  }

  // This is run at the call site, from the caller's perspective
  def translatePrototype(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    desiredPrototypeT: PrototypeT[IFunctionNameT]):
  (PrototypeI[sI], PrototypeI[cI]) = {
    val PrototypeT(desiredPrototypeIdUnsubstituted, desiredPrototypeReturnTypeUnsubstituted) = desiredPrototypeT

    val runeToBoundArgsForCall =
      translateBoundArgsForCallee(
        denizenName,
        denizenBoundToDenizenCallerSuppliedThing,
        substitutions,
        perspectiveRegionT,
        hinputs.getInstantiationBoundArgs(desiredPrototypeT.id))

    val returnSubjectiveIT =
      translateCoord(
        denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, desiredPrototypeReturnTypeUnsubstituted)

    val desiredPrototypeS =
      PrototypeI[sI](
        translateFunctionId(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, desiredPrototypeIdUnsubstituted),
        returnSubjectiveIT.coord)

    desiredPrototypeT.id match {
      case IdT(packageCoord, initSteps, name @ FunctionBoundNameT(_, _, _)) => {
        val funcBoundName = IdT(packageCoord, initSteps, name)
        val prototypeS = vassertSome(denizenBoundToDenizenCallerSuppliedThing.funcIdToBoundArgPrototype.get(funcBoundName))
        //        if (opts.sanityCheck) {
        //          vassert(Collector.all(result, { case PlaceholderTemplateNameT(_) => }).isEmpty)
        //        }

        val prototypeC =
          RegionCollapserIndividual.collapsePrototype(prototypeS)

        (prototypeS, prototypeC)
      }

      case IdT(packageCoord, initSteps, name@FunctionBoundNameT(_, _, _)) => {
        val actualPrototypeS =
          vassertSome(denizenBoundToDenizenCallerSuppliedThing.funcIdToBoundArgPrototype.get(IdT(packageCoord, initSteps, name)))

        val actualDesiredPrototypeC =
          RegionCollapserIndividual.collapsePrototype(actualPrototypeS)
        (actualPrototypeS, actualDesiredPrototypeC)
      }
      case IdT(_, _, ExternFunctionNameT(_, _)) => {
        if (opts.sanityCheck) {
          vassert(Collector.all(desiredPrototypeS, { case KindPlaceholderTemplateNameT(_, _) => }).isEmpty)
        }
        val desiredPrototypeC =
          RegionCollapserIndividual.collapsePrototype(desiredPrototypeS)
        (desiredPrototypeS, desiredPrototypeC)
      }
      case IdT(_, _, last) => {
        last match {
          case LambdaCallFunctionNameT(_, _, _) => {
            (denizenName.steps.last, desiredPrototypeS.id.steps.init.init.last) match {
              case (
                  FunctionNameT(FunctionTemplateNameT(nameA,codeLocA),templateArgsA,parametersA),
                  FunctionNameIX(FunctionTemplateNameI(nameB,codeLocB),templateArgsB,parametersB)) => {
                // Make sure we're talking about roughly the same function
                vassert(nameA == nameB)
                vassert(codeLocA == codeLocB)
                vassert(templateArgsA.length == templateArgsB.length)
                vassert(parametersA.length == parametersB.length)
                // Could we have a false positive here if we're doing things on different templates?
                // I don't think so.
              }
              case (
                  LambdaCallFunctionNameT(LambdaCallFunctionTemplateNameT(codeLocA,paramsTTA),templateArgsA,parametersA),
                  LambdaCallFunctionNameI(LambdaCallFunctionTemplateNameI(codeLocB,paramsTTB),templateArgsB,parametersB)) => {
                // Make sure we're talking about roughly the same function
                vassert(codeLocA == codeLocB)
                vassert(paramsTTA == paramsTTB)
                vassert(templateArgsA.length == templateArgsB.length)
                vassert(parametersA.length == parametersB.length)
              }
              case other => vwat(other)
            }
          }
          case _ =>
        }

//        // Let's say we want to call 1'myPureDisplay(0'board).
//        // We want that to become 0'myPureDisplay(-1'board).
//        // The default region we send should always be zero, and all incoming imms should be negative.
//        // DO NOT SUBMIT centralize docs
//        // TODO use an array instead of a map here
//        val oldRegionPureHeights =
//          Collector.all(uncollapsedDesiredPrototypeI, {
//            case RegionTemplataI(pureHeight) => pureHeight
//          }).toVector.distinct.sorted
//        val oldToNewRegionPureHeight =
//          oldRegionPureHeights.zipWithIndex.map({ case (oldRegionPureHeight, index) =>
//            (oldRegionPureHeight, index - (oldRegionPureHeights.length - 1))
//          }).toMap

        val desiredPrototypeC =
          RegionCollapserIndividual.collapsePrototype(desiredPrototypeS)

        val desiredPrototypeN =
          RegionCollapserConsistent.collapsePrototype(
            RegionCounter.countPrototype(desiredPrototypeS),
            desiredPrototypeS)

        vassert(RegionCollapserIndividual.collapsePrototype(desiredPrototypeN) == desiredPrototypeC)

        monouts.newFunctions.enqueue(
          (
            desiredPrototypeT,
            desiredPrototypeN,
            runeToBoundArgsForCall,
            // If we're instantiating something whose name starts with our name, then we're instantiating our lambda.
            if (TemplataCompiler.getSuperTemplate(desiredPrototypeT.id).steps.startsWith(TemplataCompiler.getSuperTemplate(denizenName).steps)) {
            // We need to supply our bounds to our lambdas, see LCCPGB and LCNBAFA.
              Some(denizenBoundToDenizenCallerSuppliedThing)
            } else {
              if (opts.sanityCheck) {
                val desiredFuncSuperTemplateName = TemplataCompiler.getSuperTemplate(desiredPrototypeT.id)
                val funcT =
                  vassertOne(
                    hinputs.functions
                        .filter(funcT => TemplataCompiler.getSuperTemplate(funcT.header.id) == desiredFuncSuperTemplateName))
                vassert(runeToBoundArgsForCall.runeToFunctionBoundArg.size == funcT.instantiationBoundParams.runeToBoundPrototype.size)
                vassert(
                  runeToBoundArgsForCall.callerRuneToCalleeRuneToReachableFunc.count(_._2.nonEmpty) ==
                      funcT.instantiationBoundParams.runeToCitizenRuneToReachablePrototype.count(_._2.citizenRuneToReachablePrototype.nonEmpty))
                vassert(runeToBoundArgsForCall.runeToImplBoundArg.size == funcT.instantiationBoundParams.runeToBoundImpl.size)
              }
              None
            }))
        (desiredPrototypeS, desiredPrototypeC)
      }
    }
  }

  private def translateBoundArgsForCallee(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    // This contains a map from rune to a prototype, specifically the prototype that we
    // (the *template* caller) is supplying to the *template* callee. This prototype might
    // be a placeholder, phrased in terms of our (the *template* caller's) placeholders
    instantiationBoundArgsForCallUnsubstituted: InstantiationBoundArgumentsT[IFunctionNameT, IImplNameT]):
  InstantiationBoundArgumentsI = {
    val runeToSuppliedBoundPrototypeForCallUnsubstituted =
      instantiationBoundArgsForCallUnsubstituted.runeToBoundPrototype
    val runeToSuppliedPrototypeForCall =
    // For any that are placeholders themselves, let's translate those into actual prototypes.
      runeToSuppliedBoundPrototypeForCallUnsubstituted.map({ case (rune, suppliedPrototypeUnsubstituted) =>
        rune ->
          (suppliedPrototypeUnsubstituted.id match {
            case IdT(packageCoord, initSteps, name @ FunctionBoundNameT(_, _, _)) => {
              vassertSome(
                denizenBoundToDenizenCallerSuppliedThing.funcIdToBoundArgPrototype.get(
                  IdT(packageCoord, initSteps, name)))
            }
            case IdT(packageCoord, initSteps, name@FunctionBoundNameT(_, _, _)) => {
              vassertSome(
                denizenBoundToDenizenCallerSuppliedThing.funcIdToBoundArgPrototype.get(
                  IdT(packageCoord, initSteps, name)))
            }
            case _ => {
              val (prototypeI, prototypeC) =
                translatePrototype(
                  denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, suppliedPrototypeUnsubstituted)
              prototypeI
            }
          })
      })
    // And now we have a map from the callee's rune to the *instantiated* callee's prototypes.

    val callerRuneToCalleeRuneToSuppliedReachablePrototypeForCallUnsubstituted =
      instantiationBoundArgsForCallUnsubstituted.runeToCitizenRuneToReachablePrototype
    val runeToSuppliedReachablePrototypeForCall =
    // For any that are placeholders themselves, let's translate those into actual prototypes.
      callerRuneToCalleeRuneToSuppliedReachablePrototypeForCallUnsubstituted.map({ case (callerRune, calleeRuneToSuppliedReachablePrototypeForCallUnsubstituted) =>
        callerRune ->
        calleeRuneToSuppliedReachablePrototypeForCallUnsubstituted.citizenRuneToReachablePrototype
            .map({ case (calleeRune, suppliedReachablePrototypeForCallUnsubstituted) =>
              calleeRune ->
                  (suppliedReachablePrototypeForCallUnsubstituted.id match {
                    case IdT(packageCoord, initSteps, name@FunctionBoundNameT(_, _, _)) => {
                      vassertSome(
                        denizenBoundToDenizenCallerSuppliedThing.funcIdToBoundArgPrototype.get(
                          IdT(packageCoord, initSteps, name)))
                    }
//                    case IdT(packageCoord, initSteps, name@FunctionBoundNameT(_, _, _)) => {
//                      vassertSome(
//                        denizenBoundToDenizenCallerSuppliedThing.funcIdToBoundArgPrototype.get(
//                          IdT(packageCoord, initSteps, name)))
//                    }
                    case _ => {
                      val (prototypeI, prototypeC) =
                        translatePrototype(
                          denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, suppliedReachablePrototypeForCallUnsubstituted)
                      prototypeI
                    }
                  })
            })
      })
    // And now we have a map from the callee's rune to the *instantiated* callee's prototypes.

    val runeToSuppliedImplForCallUnsubstituted =
      instantiationBoundArgsForCallUnsubstituted.runeToBoundImpl
    val runeToSuppliedImplForCall =
    // For any that are placeholders themselves, let's translate those into actual prototypes.
      runeToSuppliedImplForCallUnsubstituted.map({ case (rune, suppliedImplUnsubstituted) =>
        rune ->
          (suppliedImplUnsubstituted match {
            case IdT(packageCoord, initSteps, name @ ImplBoundNameT(_, _)) => {
              vassertSome(
                denizenBoundToDenizenCallerSuppliedThing.boundParamImplIdToBoundArgImplId.get(
                  IdT(packageCoord, initSteps, name)))
            }
            case _ => {
              val implNameS =
                translateImplId(
                  denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, suppliedImplUnsubstituted)
              implNameS
            }
          })
      })
    // And now we have a map from the callee's rune to the *instantiated* callee's impls.

    InstantiationBoundArgumentsI(
      runeToSuppliedPrototypeForCall,
      runeToSuppliedReachablePrototypeForCall,
      runeToSuppliedImplForCall)
  }

  def translateCollapsedStructDefinition(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    newIdT: IdT[IStructNameT],
    newId: IdI[cI, IStructNameI[cI]],
    structDefT: StructDefinitionT):
  Unit = {
    val StructDefinitionT(templateName, instantiatedCitizen, attributes, weakable, mutabilityT, members, isClosure, _) = structDefT

    if (opts.sanityCheck) {
      vassert(Collector.all(newId, { case KindPlaceholderNameT(_) => }).isEmpty)
    }

    val perspectiveRegionT =
      structDefT.instantiatedCitizen.id.localName.templateArgs.last match {
        case PlaceholderTemplataT(IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _)), RegionTemplataType()) => {
          RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r), RegionTemplataType()))
        }
        case _ => vwat()
      }

    val mutability =
      ITemplataI.expectMutabilityTemplata(
        translateTemplata(
          denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, mutabilityT)).mutability

    if (monouts.structToMutability.contains(newId)) {
      return
    }
    monouts.structToMutability.put(newId, mutability)

//    val currentPureHeight = vimpl()

    val result =
      StructDefinitionI(
//        templateName,
        StructIT(newId),
        attributes.map(vimpl(_)),
        weakable,
        mutability,
        members.map(memberT => {
          translateStructMember(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, memberT)._2
        }),
        isClosure,
        Map(),
        Map())

    vassert(result.instantiatedCitizen.id == newId)

    monouts.structs.put(result.instantiatedCitizen.id, result)

    if (opts.sanityCheck) {
      vassert(Collector.all(result.instantiatedCitizen, { case KindPlaceholderNameT(_) => }).isEmpty)
      vassert(Collector.all(result.members, { case KindPlaceholderNameT(_) => }).isEmpty)
    }
    result
  }

  // This inner function is conceptually from the interface's own perspective. That's why it's
  // taking in a collapsed id.
  def translateCollapsedInterfaceDefinition(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    newIdC: IdI[cI, IInterfaceNameI[cI]],
    interfaceDefT: InterfaceDefinitionT):
  Unit = {
    if (monouts.interfaceToMutability.contains(newIdC)) {
      return
    }

    val InterfaceDefinitionT(templateName, instantiatedCitizen, ref, attributes, weakable, mutabilityT, _, internalMethods) = interfaceDefT

    vassert(!monouts.interfaceToImplToAbstractPrototypeToOverride.contains(newIdC))
    monouts.interfaceToImplToAbstractPrototypeToOverride.put(newIdC, mutable.HashMap())

    vassert(!monouts.interfaceToAbstractFuncToVirtualIndex.contains(newIdC))
    monouts.interfaceToAbstractFuncToVirtualIndex.put(newIdC, mutable.HashMap())

    vassert(!monouts.interfaceToImpls.contains(newIdC))
    monouts.interfaceToImpls.put(newIdC, mutable.HashSet())

    if (opts.sanityCheck) {
      vassert(Collector.all(newIdC, { case KindPlaceholderNameT(_) => }).isEmpty)
    }

    val perspectiveRegionT =
      interfaceDefT.instantiatedCitizen.id.localName.templateArgs.last match {
        case PlaceholderTemplataT(IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _)), RegionTemplataType()) => {
          RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r), RegionTemplataType()))
        }
        case _ => vwat()
      }

    val mutability = ITemplataI.expectMutabilityTemplata(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, mutabilityT)).mutability

    vassert(!monouts.interfaceToMutability.contains(newIdC))
    monouts.interfaceToMutability.put(newIdC, mutability)

    //    val currentPureHeight = vimpl()

    val newInterfaceIT = InterfaceIT(newIdC)

    val result =
      InterfaceDefinitionI(
        newInterfaceIT,
        attributes.map({
          case SealedT => SealedI
          case other => vimpl(other)
        }),
        weakable,
        mutability,
        Map(),
        Map(),
        Vector())

    monouts.interfacesWithoutMethods.put(newIdC, result)

    vassert(result.instantiatedCitizen.id == newIdC)

    if (opts.sanityCheck) {
      vassert(Collector.all(result.instantiatedInterface, { case KindPlaceholderNameT(_) => }).isEmpty)
    }

    result
  }

  def translateFunctionHeader(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    header: FunctionHeaderT):
  FunctionHeaderI = {
    val FunctionHeaderT(id, attributes, params, returnType, maybeOriginFunctionTemplata) = header

    val newIdS =
      translateFunctionId(
        denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, id)
    val newIdC =
      RegionCollapserIndividual.collapseId[IFunctionNameI[sI], IFunctionNameI[cI]](
        newIdS,
        x => RegionCollapserIndividual.collapseFunctionName( x))

    val returnIT =
      translateCoord(
        denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, returnType)
    val returnIC = RegionCollapserIndividual.collapseCoord(returnIT.coord)

    val result =
      FunctionHeaderI(
        newIdC,
        attributes.map(translateFunctionAttribute),
        params.map(translateParameter(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
        returnIC)

    result
  }

  def translateFunctionAttribute(x: IFunctionAttributeT): IFunctionAttributeI = {
    x match {
      case UserFunctionT => UserFunctionI
      case PureT => PureI
      case ExternT(packageCoord) => ExternI(packageCoord)
      case other => vimpl(other)
    }
  }

  // DO NOT SUBMIT why does this one not take in the collapsed id like the struct and interface things
  def translateCollapsedFunction(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    // For doublechecking we're getting the actual function we requested
    desiredPrototypeC: PrototypeI[cI],
    functionT: FunctionDefinitionT):
  FunctionDefinitionI = {
    val FunctionDefinitionT(headerT, _, bodyT) = functionT

    val FunctionHeaderT(id, attributes, params, returnType, maybeOriginFunctionTemplata) = headerT

    if (opts.sanityCheck) {
      Collector.all(substitutions.toVector, {
        case RegionTemplataI(x) if x > 0 => vwat()
      })
    }

    val perspectiveRegionT =
      functionT.header.id.localName.templateArgs.last match {
        case PlaceholderTemplataT(IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _)), RegionTemplataType()) => {
          RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r), RegionTemplataType()))
        }
        case _ => vwat()
      }

    val functionIdS =
      translateFunctionId(
        denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, id)
    val functionIdC =
      RegionCollapserIndividual.collapseFunctionId(functionIdS)

    monouts.functions.get(functionIdC) match {
      case Some(func) => return func
      case None =>
    }

    val newHeader =
      translateFunctionHeader(
        denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, headerT)

    if (newHeader.toPrototype != desiredPrototypeC) {
      translateFunctionHeader(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, headerT)
      vfail()
    }

    val (bodySubjectiveIT, bodyCE) =
      translateRefExpr(
        denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, bodyT)

    val result = FunctionDefinitionI(newHeader, Map(), Map(), bodyCE)

    monouts.functions.put(result.header.id, result)
    result
  }

  def translateLocalVariable(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    variable: ILocalVariableT):
  // Returns subjective coord and the local var
  (CoordI[sI], ILocalVariableI) = {
    variable match {
      case r @ ReferenceLocalVariableT(_, _, _) => {
        translateReferenceLocalVariable(
          denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, r)
      }
      case a @ AddressibleLocalVariableT(_, _, _) => {
        translateAddressibleLocalVariable(
          denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, a)
      }
    }
  }

  def translateReferenceLocalVariable(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    variable: ReferenceLocalVariableT):
  // Returns subjective coord and the local var
  (CoordI[sI], ReferenceLocalVariableI) = {
    val ReferenceLocalVariableT(id, variability, coord) = variable
    val coordS =
      translateCoord(
        denizenName,
        denizenBoundToDenizenCallerSuppliedThing,
        substitutions,
        perspectiveRegionT,
        coord)
    val varNameS = translateVarName(id)
    val localC =
      ReferenceLocalVariableI(
        RegionCollapserIndividual.collapseVarName(varNameS),
        translateVariability(variability),
        RegionCollapserIndividual.collapseCoord(coordS.coord))
    (coordS.coord, localC)
  }

  def translateAddressibleLocalVariable(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    variable: AddressibleLocalVariableT):
  // Returns subjective coord and the local var
  (CoordI[sI], AddressibleLocalVariableI) = {
    val AddressibleLocalVariableT(id, variability, coord) = variable
    val coordS =
      translateCoord(
        denizenName,
        denizenBoundToDenizenCallerSuppliedThing,
        substitutions,
        // The LocalVariable's type is from its own perspective.
        coord.region,
        coord)
    val varS = translateVarName(id)
    val localC =
      AddressibleLocalVariableI(
        RegionCollapserIndividual.collapseVarName(varS),
        translateVariability(variability),
        RegionCollapserIndividual.collapseCoord(coordS.coord))
    (coordS.coord, localC)
  }

  def translateAddrExpr(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    expr: AddressExpressionTE):
  // Returns the subjective coord (see HCCSCS) and the expression.
  (CoordI[sI], AddressExpressionIE) = {
    expr match {
      case LocalLookupTE(range, localVariableT) => {
//        // We specifically don't *translate* LocalLookupTE.localVariable because we can't translate
//        // it properly from here with our current understandings of the regions' mutabilities, we
//        // need its original type. See CTOTFIPB.
//        val localVariable = env.lookupOriginalTranslatedVariable(localVariableT.name)
        val (localSubjectiveIT, localVariableI) =
          translateLocalVariable(
            denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, localVariableT)
//
//        val sourceRegion =
//          ITemplataI.expectRegionTemplata(
//            translateTemplata(
//              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, vimpl()))

//        val subjectiveResultIT =
//          CoordI(
//            (localVariableI.coord.ownership, coordRegionIsMutable(substitutions, perspectiveRegionT, localVariableT.coord)) match {
//              case (OwnT, _) => OwnI
//              case other => vimpl(other)
//            },
//            localVariableI.coord.kind)

        val resultSubjectiveIT = localSubjectiveIT
        val resultCE =
          LocalLookupIE(
            localVariableI,
            RegionCollapserIndividual.collapseCoord(resultSubjectiveIT))
        (resultSubjectiveIT, resultCE)
      }
      case ReferenceMemberLookupTE(range, structExprT, memberNameT, memberCoordT, variability) => {
        val (structSubjectiveIT, structCE) =
          translateRefExpr(
            denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, structExprT)
        val memberName = translateVarName(memberNameT)

        val memberCoordS =
          translateCoord(
            denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, memberCoordT)

        val resultSubjectiveIT = memberCoordS
        val resultCE =
          ReferenceMemberLookupIE(
            range,
            structCE,
            RegionCollapserIndividual.collapseVarName(memberName),
            RegionCollapserIndividual.collapseCoord(resultSubjectiveIT.coord),
            translateVariability(variability))
        (resultSubjectiveIT.coord, resultCE)
      }
      case StaticSizedArrayLookupTE(range, arrayExprT, arrayType, indexExprT, elementTypeT, variability) => {
        // DO NOT SUBMIT combine a lot of this with the ReferenceMemberLookupTE case
        val (arraySubjectiveIT, arrayCE) =
          translateRefExpr(
            denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, arrayExprT)

        val elementTypeS =
          translateCoord(
            denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, elementTypeT).coord

        val (indexIT, indexCE) =
          translateRefExpr(
            denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, indexExprT)

        val resultCoord = CoordI(elementTypeS.ownership, elementTypeS.kind)

        val resultCE =
          StaticSizedArrayLookupIE(
            range,
            arrayCE,
            indexCE,
            RegionCollapserIndividual.collapseCoord(resultCoord),
            translateVariability(variability))
        (resultCoord, resultCE)
      }
      case AddressMemberLookupTE(range, structExpr, memberName, resultType2, variability) => {
        val (structIT, structCE) =
          translateRefExpr(
            denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, structExpr)
        val varNameS = translateVarName(memberName)
        val resultIT =
          translateCoord(
            denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, resultType2)
        val variabilityC = translateVariability(variability)

        val resultCE =
          AddressMemberLookupIE(
            structCE,
            RegionCollapserIndividual.collapseVarName(varNameS),
            RegionCollapserIndividual.collapseCoord(resultIT.coord),
            variabilityC)
        (resultIT.coord, resultCE)
      }
      case RuntimeSizedArrayLookupTE(range, arrayExpr, rsaTT, indexExpr, variability) => {
        val (arrayIT, arrayCE) =
          translateRefExpr(
            denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, arrayExpr)
        val rsaIT =
          translateRuntimeSizedArray(
            denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, rsaTT)
        val (indexIT, indexCE) =
          translateRefExpr(
            denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, indexExpr)
        val variabilityC = translateVariability(variability)

        // We can't just say rsaIT.elementType here because that's the element from the array's own
        // perspective.
        val elementIT =
          translateCoord(
            denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, rsaTT.elementType)

        val resultIT = elementIT
        val resultCE =
          RuntimeSizedArrayLookupIE(
            arrayCE, indexCE, RegionCollapserIndividual.collapseCoord(elementIT.coord), variabilityC)
        (resultIT.coord, resultCE)
      }
      case other => vimpl(other)
    }
  }

  def translateExpr(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    expr: ExpressionT):
  // Returns the subjective coord (see HCCSCS) and the expression.
  (CoordI[sI], ExpressionI) = {
    expr match {
      case r : ReferenceExpressionTE => {
        translateRefExpr(
          denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, r)
      }
      case a : AddressExpressionTE => {
        translateAddrExpr(
          denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, a)
      }
    }
  }

  def translateRefExpr(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    expr: ReferenceExpressionTE):
  // Returns the subjective coord (see HCCSCS) and the expression.
  (CoordI[sI], ReferenceExpressionIE) = {
    val denizenTemplateName = TemplataCompiler.getTemplate(denizenName)
    val (resultIT, resultCE) =
      expr match {
        case RestackifyTE(variable, inner) => {
          val (innerIT, innerCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, inner)
          val (localIT, localI) =
            translateLocalVariable(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, variable)
          //          env.addTranslatedVariable(variableT.name, vimpl(translatedVariable))
          val subjectiveResultIT = CoordI[sI](MutableShareI, VoidIT())
          val exprCE =
            RestackifyIE(
              localI, innerCE, RegionCollapserIndividual.collapseCoord(subjectiveResultIT))
          (subjectiveResultIT, exprCE)
        }

        case LetNormalTE(variableT, innerTE) => {
          val (innerIT, innerCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, innerTE)
          val (localIT, localI) =
            translateLocalVariable(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, variableT)
//          env.addTranslatedVariable(variableT.name, vimpl(translatedVariable))
          val subjectiveResultIT = CoordI[sI](MutableShareI, VoidIT())
          val exprCE =
            LetNormalIE(
              localI, innerCE, RegionCollapserIndividual.collapseCoord(subjectiveResultIT))
          (subjectiveResultIT, exprCE)
        }
        case PureTE(newDefaultRegionT, innerTE, resultCoordT) => {
          val oldPerspectiveRegionT = perspectiveRegionT
          val resultOutsideIT =
            translateCoord(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, oldPerspectiveRegionT, resultCoordT)
                .coord
          val resultOutsideCT =
            RegionCollapserIndividual.collapseCoord(resultOutsideIT)

          val (innerIT, innerCE) =
            runInNewPureRegion(
              denizenName,
              denizenBoundToDenizenCallerSuppliedThing,
              substitutions,
              denizenTemplateName,
              newDefaultRegionT,
              (newSubstitutions, newPerspectiveRegionT) => {
                translateRefExpr(
                  denizenName, denizenBoundToDenizenCallerSuppliedThing,newSubstitutions, newPerspectiveRegionT, innerTE)
              })

          // DO NOT SUBMIT combine with code in FunctionCallTE case
          if (innerCE.result.kind != resultOutsideCT.kind) {
            vimpl("Transmigration unimplemented!")
          }
          val mutabilifyNeeded =
            (innerIT.ownership, resultOutsideIT.ownership) match {
              case (x, y) if x == y => false
              case (ImmutableBorrowI, MutableBorrowI) => true
              case (ImmutableShareI, MutableShareI) => true
              case other => vimpl(other)
            }
          val resultIE =
            if (mutabilifyNeeded) {
              MutabilifyIE(innerCE, resultOutsideCT)
            } else {
              innerCE
            }

          (resultOutsideIT, resultIE)
        }
        case BlockTE(inner) => {
          val (innerIT, innerCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, inner)
          val resultIT = innerIT
          val resultCE = BlockIE(innerCE, RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)
        }
        case ReturnTE(inner) => {
          val (innerIT, innerCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, inner)
          val resultCE = ReturnIE(innerCE)
          (CoordI[sI](MutableShareI, NeverIT(false)), resultCE)
        }
        case c @ ConsecutorTE(inners) => {
          val resultTT = c.result.coord
          val resultIT =
            translateCoord(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, resultTT)
                .coord
          val innersCE =
            inners.map(innerTE => {
              translateRefExpr(
                denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, innerTE)._2
            })
          val resultCE = ConsecutorIE(innersCE, RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)
        }
        case ConstantIntTE(value, bits, region) => {
          val resultCE =
            ConstantIntIE(
              ITemplataI.expectIntegerTemplata(
                translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, value)).value,
              bits)
          (CoordI[sI](MutableShareI, IntIT(bits)), resultCE)
        }
        case ConstantStrTE(value, region) => {
          val resultCE = ConstantStrIE(value)
          (CoordI[sI](MutableShareI, StrIT()), resultCE)
        }
        case ConstantBoolTE(value, region) => {
          val resultCE = ConstantBoolIE(value)
          (CoordI[sI](MutableShareI, BoolIT()), resultCE)
        }
        case ConstantFloatTE(value, region) => {
          val resultCE = ConstantFloatIE(value)
          (CoordI[sI](MutableShareI, BoolIT()), resultCE)
        }
        case UnletTE(variable) => {
          val (localIT, localCE) =
            translateLocalVariable(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, variable)
          val resultIT = localIT
//          val local = env.lookupOriginalTranslatedVariable(variable.name)
          val resultCE = UnletIE(localCE, RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)
        }
        case DiscardTE(innerTE) => {
          val (innerIT, innerCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, innerTE)
          val resultCE = DiscardIE(innerCE)
          (CoordI[sI](MutableShareI, VoidIT()), resultCE)
        }
        case VoidLiteralTE(region) => {
          (CoordI[sI](MutableShareI, VoidIT()), VoidLiteralIE())
        }
        case FunctionCallTE(prototypeT, pure, maybeNewRegion, args, returnCoordT) => {
          val innersCE =
            args.map(argTE => {
              val (argIT, argCE) =
                translateRefExpr(
                  denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, argTE)
              // if (pure && argIT.ownership == MutableBorrowI) {
              //   PreCheckBorrowIE(argCE)
              // } else {
                argCE
              // }
            })

          if (!pure) {
            val (prototypeI, prototypeC) =
              translatePrototype(
                denizenName,
                denizenBoundToDenizenCallerSuppliedThing,
                substitutions,
                perspectiveRegionT,
                prototypeT)
            vassert(
              prototypeI.returnType ==
                translateCoord(
                  denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, returnCoordT)
                .coord)
            val returnCoordIT =
              translateCoord(
                denizenName,
                denizenBoundToDenizenCallerSuppliedThing,
                substitutions,
                perspectiveRegionT,
                returnCoordT)
                  .coord
            val returnCoordCT =
              RegionCollapserIndividual.collapseCoord(returnCoordIT)
            val resultIE = FunctionCallIE(prototypeC, innersCE, returnCoordCT)
            (returnCoordIT, resultIE)
          } else {
            val newRegion = vassertSome(maybeNewRegion)
            val oldPerspectiveRegionT = perspectiveRegionT
            val returnCoordOutsideIT =
              translateCoord(
                denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, oldPerspectiveRegionT, returnCoordT)
                  .coord

            val (returnCoordInsideIT, callCE) =
              runInNewPureRegion(
                denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, denizenTemplateName, newRegion,
                (newSubstitutions, newPerspectiveRegionT) => {
                  val returnCoordIT =
                    translateCoord(
                      denizenName,
                      denizenBoundToDenizenCallerSuppliedThing,
                      substitutions,
                      perspectiveRegionT,
                      returnCoordT)
                        .coord
                  val returnCoordCT =
                    RegionCollapserIndividual.collapseCoord(returnCoordIT)
                  val (prototypeI, prototypeC) =
                    translatePrototype(
                      denizenName,
                      denizenBoundToDenizenCallerSuppliedThing,
                      newSubstitutions,
                      newPerspectiveRegionT,
                      prototypeT)
                  val callCE =
                    FunctionCallIE(
                      prototypeC,
                      innersCE.map(innerIE => {
                        maybeImmutabilify(innerIE)
                      }),
                      returnCoordCT)
                  (returnCoordIT, callCE)
                })

            if (returnCoordInsideIT.kind != returnCoordOutsideIT.kind) {
              vimpl("Transmigration unimplemented!")
            }
            val mutabilifyNeeded =
              (returnCoordInsideIT.ownership, returnCoordOutsideIT.ownership) match {
                case (x, y) if x == y => false
                case (ImmutableBorrowI, MutableBorrowI) => true
                case (ImmutableShareI, MutableShareI) => true
                case other => vimpl(other)
              }
            val resultCE =
              if (mutabilifyNeeded) {
                MutabilifyIE(callCE, RegionCollapserIndividual.collapseCoord(returnCoordOutsideIT))
              } else {
                callCE
              }
            (returnCoordOutsideIT, resultCE)
          }
        }
        case InterfaceFunctionCallTE(superFunctionPrototypeT, virtualParamIndex, resultReference, args) => {
          val (superFunctionPrototypeI, superFunctionPrototypeC) =
            translatePrototype(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, superFunctionPrototypeT)
          val resultIT = superFunctionPrototypeI.returnType
          val resultCE =
            InterfaceFunctionCallIE(
              superFunctionPrototypeC,
              virtualParamIndex,
              args.map(arg => {
                translateRefExpr(
                  denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, arg)._2
              }),
              RegionCollapserIndividual.collapseCoord(resultIT))
          val interfaceIdC =
            superFunctionPrototypeC.paramTypes(virtualParamIndex).kind.expectInterface().id
          //        val interfaceId =
          //          translateInterfaceId(
          //            interfaceIdT,
          //            translateBoundArgsForCallee(denizenName, denizenBoundToDenizenCallerSuppliedThing,
          //              hinputs.getInstantiationBounds(callee.toPrototype.id)))

          val instantiationBoundArgs =
            translateBoundArgsForCallee(denizenName, denizenBoundToDenizenCallerSuppliedThing,
              substitutions,
              perspectiveRegionT,
              // but this is literally calling itself from where its defined
              // perhaps we want the thing that originally called
              hinputs.getInstantiationBoundArgs(superFunctionPrototypeT.id))

          val superFunctionPrototypeN =
            RegionCollapserConsistent.collapsePrototype(
              RegionCounter.countPrototype(superFunctionPrototypeI),
              superFunctionPrototypeI)

          vassert(RegionCollapserIndividual.collapsePrototype(superFunctionPrototypeN) == superFunctionPrototypeC)

          monouts.newAbstractFuncs.enqueue(
            (superFunctionPrototypeT, superFunctionPrototypeN, virtualParamIndex, interfaceIdC, instantiationBoundArgs))

          (resultIT, resultCE)
        }
        case ArgLookupTE(paramIndex, reference) => {
          val typeS =
            translateCoord(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, reference)
                .coord
          val resultCE = ArgLookupIE(paramIndex, RegionCollapserIndividual.collapseCoord(typeS))
          (typeS, resultCE)
        }
        case SoftLoadTE(originalInner, originalTargetOwnership) => {
          val (innerIT, innerCE) =
            translateAddrExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, originalInner)
          val targetOwnership =
            // First, figure out what ownership it is after substitution.
            // if we have an owned T but T is a &Ship, then own + borrow = borrow
            (originalTargetOwnership, innerIT.ownership) match {
//              case (a, b) if a == b => a
              case (ShareT, ImmutableShareI) => ImmutableShareI
              case (ShareT, MutableShareI) => MutableShareI
              case (BorrowT, ImmutableShareI) => ImmutableShareI
              case (BorrowT, MutableShareI) => MutableShareI
              case (BorrowT, ImmutableBorrowI) => ImmutableBorrowI
              case (BorrowT, MutableBorrowI | OwnI) => {
//                MutableBorrowI
                if (coordRegionIsMutable(substitutions, perspectiveRegionT, originalInner.result.coord)) {
                  MutableBorrowI
                } else {
                  ImmutableBorrowI
                }
              }
              case (WeakT, ImmutableShareI) => ImmutableShareI
              case (WeakT, MutableShareI) => MutableShareI
//              case (WeakT, OwnT) => WeakT
              case (WeakT, ImmutableBorrowI) => vimpl(WeakT)
              case (WeakT, MutableBorrowI) => vimpl(WeakT)
              case other => vwat(other)
            }
          val resultIT = CoordI[sI](targetOwnership, innerIT.kind)
          val resultCE =
            SoftLoadIE(innerCE, targetOwnership, RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)
        }
        case ExternFunctionCallTE(prototype2, args) => {
          val (prototypeI, prototypeC) =
            translatePrototype(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, prototype2)
          val argsCE =
            args.map(argTE => {
              translateRefExpr(denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, argTE)._2
            })
          val resultIT = prototypeI.returnType
          val resultCE = ExternFunctionCallIE(prototypeC, argsCE, prototypeC.returnType)
          (resultIT, resultCE)
        }
        case ConstructTE(structTT, resultReference, args) => {
          val resultIT =
            translateCoord(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, resultReference)
                .coord

          //          val freePrototype = translatePrototype(freePrototypeT)
          //          // They might disagree on the ownership, and thats fine.
          //          // That free prototype is only going to take an owning or a share reference, and we'll only
          //          // use it if we have a shared reference so it's all good.
          //          vassert(coord.kind == vassertSome(freePrototype.id.last.parameters.headOption).kind)
          //          if (coord.ownership == ShareT) {
          //            monouts.immKindToDestructor.put(coord.kind, freePrototype)
          //          }

          val argsCE =
            args.map(argTE => {
              translateExpr(
                denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, argTE)._2
            })

          val structIT =
            translateStruct(
              denizenName,
              denizenBoundToDenizenCallerSuppliedThing,
              substitutions,
              perspectiveRegionT,
              structTT,
              translateBoundArgsForCallee(
                denizenName,
                denizenBoundToDenizenCallerSuppliedThing,
                substitutions,
                perspectiveRegionT,
                hinputs.getInstantiationBoundArgs(structTT.id)))

          val resultCE =
            ConstructIE(
              StructIT(RegionCollapserIndividual.collapseStructId(structIT.id)),
              RegionCollapserIndividual.collapseCoord(resultIT),
              argsCE)
          (resultIT, resultCE)
        }
        case DestroyTE(exprT, structTT, destinationReferenceVariables) => {
          val (sourceIT, sourceCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, exprT)

          val structIT =
            translateStructId(
              denizenName,
              denizenBoundToDenizenCallerSuppliedThing,
              substitutions,
              perspectiveRegionT,
              structTT.id,
              translateBoundArgsForCallee(
                denizenName,
                denizenBoundToDenizenCallerSuppliedThing,
                substitutions,
                perspectiveRegionT,
                hinputs.getInstantiationBoundArgs(structTT.id)))

//          val resultT =
//            expr.result.coord.kind match {
//              case s @ StructIT(_) => s
//              case other => vwat(other)
//            }

//          val structDef = vassertSome(monouts.structs.get(resultT.id))
//          vassert(structDef.members.size == destinationReferenceVariables.size)

          val resultCE =
            DestroyIE(
              sourceCE,
              StructIT(RegionCollapserIndividual.collapseStructId(structIT)),
              destinationReferenceVariables.map(destRefVarT => {
                translateReferenceLocalVariable(
                  denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions,
                  perspectiveRegionT, destRefVarT)._2
              }))
          (CoordI[sI](MutableShareI, VoidIT()), resultCE)
        }
        case DestroyStaticSizedArrayIntoLocalsTE(exprT, ssaTT, destinationReferenceVariables) => {
          val (sourceIT, sourceCE) = translateRefExpr(denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, exprT)
          val (ssaIT, size) =
            sourceIT.kind match {
              case s @ StaticSizedArrayIT(IdI(_, _, StaticSizedArrayNameI(_, size, _, _))) => (s, size)
              case other => vwat(other)
            }

          vassert(size == destinationReferenceVariables.size)
          val resultCE =
            DestroyStaticSizedArrayIntoLocalsIE(
              sourceCE,
              RegionCollapserIndividual.collapseStaticSizedArray(ssaIT),
            destinationReferenceVariables.map(destRefVarT => {
              translateReferenceLocalVariable(
                denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, destRefVarT)._2
            }))
          (CoordI[sI](MutableShareI, VoidIT()), resultCE)
        }
        case MutateTE(destinationTT, sourceExpr) => {
          val (destinationIT, destinationCE) =
            translateAddrExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, destinationTT)
          val (sourceIT, sourceCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, sourceExpr)
          val resultIT = destinationIT
          val resultCE = MutateIE(destinationCE, sourceCE, RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)
        }
        case u @ UpcastTE(innerExprUnsubstituted, targetSuperKind, untranslatedImplId) => {
          val implId =
            translateImplId(
              denizenName,
              denizenBoundToDenizenCallerSuppliedThing,
              substitutions,
              perspectiveRegionT,
              untranslatedImplId)
          //          val freePrototype = translatePrototype(freePrototypeT)
          val resultIT =
            translateCoord(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, u.result.coord)
          // They might disagree on the ownership, and thats fine.
          // That free prototype is only going to take an owning or a share reference, and we'll only
          // use it if we have a shared reference so it's all good.
          //          vassert(coord.kind == vassertSome(freePrototype.id.last.parameters.headOption).kind)
          //          if (coord.ownership == ShareT) {
          //            monouts.immKindToDestructor.put(coord.kind, freePrototypeT)
          //          }

          val (innerIT, innerCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, innerExprUnsubstituted)

          val superKindS =
            translateSuperKind(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, targetSuperKind)

          val resultCE =
            UpcastIE(
              innerCE,
              InterfaceIT(RegionCollapserIndividual.collapseInterfaceId(superKindS.id)),
              RegionCollapserIndividual.collapseImplId(implId),
              RegionCollapserIndividual.collapseCoord(resultIT.coord))
          (resultIT.coord, resultCE)
        }
        case IfTE(condition, thenCall, elseCall) => {
          val (conditionIT, conditionCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, condition)
          val (thenIT, thenCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, thenCall)
          val (elseIT, elseCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, elseCall)
          val resultIT =
            (thenIT, elseIT) match {
              case (a, b) if a == b => a
              case (a, CoordI(_, NeverIT(_))) => a
              case (CoordI(_, NeverIT(_)), b) => b
              case other => vwat(other)
            }

          val resultCE = IfIE(conditionCE, thenCE, elseCE, RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)
        }
        case IsSameInstanceTE(left, right) => {
          val (leftIT, leftCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, left)
          val (rightIT, rightCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, right)
          val resultCE = IsSameInstanceIE(leftCE, rightCE)
          (CoordI[sI](MutableShareI, BoolIT()), resultCE)
        }
        case StaticArrayFromValuesTE(elements, resultReference, arrayType) => {

          //          val freePrototype = translatePrototype(freePrototypeT)
          val resultIT =
            translateCoord(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, resultReference)
                .coord
          // They might disagree on the ownership, and thats fine.
          // That free prototype is only going to take an owning or a share reference, and we'll only
          // use it if we have a shared reference so it's all good.
          //          vassert(coord.kind == vassertSome(freePrototype.id.last.parameters.headOption).kind)
          //          if (coord.ownership == ShareT) {
          //            monouts.immKindToDestructor.put(coord.kind, freePrototypeT)
          //          }

          val elementsCE =
            elements.map(elementTE => {
              translateRefExpr(denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, elementTE)._2
            })

          val ssaTT =
            translateStaticSizedArray(
              denizenName,
              denizenBoundToDenizenCallerSuppliedThing,
              substitutions,
              perspectiveRegionT,
              arrayType)

          val resultCE =
            StaticArrayFromValuesIE(
              elementsCE,
              RegionCollapserIndividual.collapseCoord(resultIT),
              RegionCollapserIndividual.collapseStaticSizedArray(ssaTT))
          (resultIT, resultCE)
        }
        case DeferTE(innerExpr, deferredExpr) => {
          val (innerIT, innerCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, innerExpr)
          val (deferredIT, deferredCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, deferredExpr)
          val resultIT = innerIT
          val resultCE =
            DeferIE(innerCE, deferredCE, RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)
        }
        case LetAndLendTE(variable, sourceExprT, outerOwnershipT) => {
          val (sourceSubjectiveIT, sourceCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, sourceExprT)

          val resultOwnershipC =
            translateOwnership(
              substitutions,
              perspectiveRegionT,
              // TODO: see if we can combine this with the other composeOwnerships function.
              composeOwnerships(outerOwnershipT, sourceSubjectiveIT.ownership),
              sourceExprT.result.coord.region)

          val resultIT = CoordI(resultOwnershipC, sourceSubjectiveIT.kind)

          val (localIT, localI) =
            translateLocalVariable(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, variable)

          val resultCE =
            LetAndLendIE(
              localI,
              sourceCE,
              resultOwnershipC,
              RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)
        }
        case BorrowToWeakTE(innerExpr) => {
          val (innerIT, innerCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, innerExpr)


          val resultIT = innerIT.copy(ownership = WeakI)
          val resultCT = RegionCollapserIndividual.collapseCoord(resultIT)

          (resultIT, BorrowToWeakIE(innerCE, resultCT))
        }
        case WhileTE(BlockTE(inner)) => {
          val (innerIT, innerCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, inner)

          // While loops must always produce void.
          // If we want a foreach/map/whatever construct, the loop should instead
          // add things to a list inside; WhileIE shouldnt do it for it.
          val resultIT =
            innerIT match {
              case CoordI(_, VoidIT()) => innerIT
              case CoordI(_, NeverIT(true)) => CoordI[sI](MutableShareI, VoidIT())
              case CoordI(_, NeverIT(false)) => innerIT
              case _ => vwat()
            }

          val resultCE =
            WhileIE(
              BlockIE(innerCE, RegionCollapserIndividual.collapseCoord(innerIT)),
              RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)
        }
        case BreakTE(region) => {
          val resultCE = BreakIE()
          (CoordI[sI](MutableShareI, NeverIT(true)), resultCE)
        }
        case LockWeakTE(innerExpr, resultOptBorrowType, someConstructor, noneConstructor, someImplUntranslatedId, noneImplUntranslatedId) => {
          val resultIT =
            translateCoord(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, resultOptBorrowType).coord
          val resultCT = RegionCollapserIndividual.collapseCoord(resultIT)
          val resultCE =
            LockWeakIE(
              translateRefExpr(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, innerExpr)._2,
              resultCT,
              translatePrototype(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, someConstructor)._2,
              translatePrototype(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, noneConstructor)._2,
              RegionCollapserIndividual.collapseImplId(
                translateImplId(
                  denizenName,
                  denizenBoundToDenizenCallerSuppliedThing,
                  substitutions,
                  perspectiveRegionT,
                  someImplUntranslatedId)),
              RegionCollapserIndividual.collapseImplId(
                translateImplId(
                  denizenName,
                  denizenBoundToDenizenCallerSuppliedThing,
                  substitutions,
                  perspectiveRegionT,
                  noneImplUntranslatedId)),
              resultCT)
          (resultIT, resultCE)
        }
        case DestroyStaticSizedArrayIntoFunctionTE(arrayExpr, arrayType, consumer, consumerMethod) => {
          val (arrayIT, arrayCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, arrayExpr)
          val ssaIT =
            translateStaticSizedArray(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, arrayType)
          val (consumerIT, consumerCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, consumer)
          val (consumerPrototypeI, consumerPrototypeC) =
            translatePrototype(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, consumerMethod)
          val resultCE =
            DestroyStaticSizedArrayIntoFunctionIE(
              arrayCE, RegionCollapserIndividual.collapseStaticSizedArray(ssaIT), consumerCE, consumerPrototypeC)
          (CoordI[sI](MutableShareI, VoidIT()), resultCE)
        }
        case NewImmRuntimeSizedArrayTE(arrayType, region, sizeExpr, generator, generatorMethod) => {
          //          val freePrototype = translatePrototype(freePrototypeT)

          val rsaIT =
            translateRuntimeSizedArray(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, arrayType)
          val (sizeIT, sizeCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, sizeExpr)
          val (generatorIT, generatorCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, generator)
          val (generatorPrototypeI, generatorPrototypeC) =
            translatePrototype(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, generatorMethod)

          val resultIT =
            CoordI[sI](
              rsaIT.mutability match {
                case MutableI => OwnI
                case ImmutableI => MutableShareI
              },
              rsaIT)

          val resultCE =
            NewImmRuntimeSizedArrayIE(
              RegionCollapserIndividual.collapseRuntimeSizedArray(rsaIT),
              sizeCE,
              generatorCE,
              generatorPrototypeC,
              RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)

          // They might disagree on the ownership, and thats fine.
          // That free prototype is only going to take an owning or a share reference, and we'll only
          // use it if we have a shared reference so it's all good.
          //          vassert(coord.kind == vassertSome(freePrototype.id.last.parameters.headOption).kind)
          //          if (coord.ownership == ShareT) {
          //            monouts.immKindToDestructor.put(coord.kind, freePrototype)
          //          }

        }
        case StaticArrayFromCallableTE(arrayType, region, generator, generatorMethod) => {
          //          val freePrototype = translatePrototype(freePrototypeT)

          val ssaIT =
            translateStaticSizedArray(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, arrayType)
          val (generatorIT, generatorCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, generator)
          val (generatorPrototypeI, generatorPrototypeC) =
            translatePrototype(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, generatorMethod)

          val resultIT =
            CoordI[sI](
              ssaIT.mutability match {
                case MutableI => OwnI
                case ImmutableI => MutableShareI
              },
              ssaIT)

          val resultCE =
            StaticArrayFromCallableIE(
              RegionCollapserIndividual.collapseStaticSizedArray(ssaIT),
              generatorCE,
              generatorPrototypeC,
              RegionCollapserIndividual.collapseCoord(resultIT))

          // They might disagree on the ownership, and thats fine.
          // That free prototype is only going to take an owning or a share reference, and we'll only
          // use it if we have a shared reference so it's all good.
          //          vassert(coord.kind == vassertSome(freePrototype.id.last.parameters.headOption).kind)
          //          if (coord.ownership == ShareT) {
          //            monouts.immKindToDestructor.put(coord.kind, freePrototype)
          //          }

          (resultIT, resultCE)
        }
        case RuntimeSizedArrayCapacityTE(arrayExpr) => {
          val (arrayIT, arrayCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, arrayExpr)
          val resultCE = RuntimeSizedArrayCapacityIE(arrayCE)
          (CoordI[sI](MutableShareI, IntIT(32)), resultCE)
        }
        case PushRuntimeSizedArrayTE(arrayExpr, newElementExpr) => {
          val (arrayIT, arrayCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, arrayExpr)
          val (elementIT, elementCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, newElementExpr)
          val resultCE = PushRuntimeSizedArrayIE(arrayCE, elementCE)
          (CoordI[sI](MutableShareI, VoidIT()), resultCE)
        }
        case PopRuntimeSizedArrayTE(arrayExpr) => {
          val (arrayIT, arrayCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, arrayExpr)
          val elementIT =
            arrayIT.kind match {
              case RuntimeSizedArrayIT(IdI(_, _, RuntimeSizedArrayNameI(_, RawArrayNameI(_, elementType, _)))) => {
                elementType.coord
              }
              case other => vwat(other)
            }
          val resultCE = PopRuntimeSizedArrayIE(arrayCE, RegionCollapserIndividual.collapseCoord(elementIT))
          (elementIT, resultCE)
        }
        case ArrayLengthTE(arrayExpr) => {
          val (arrayIT, arrayCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, arrayExpr)
          val resultIT = CoordI[sI](MutableShareI, IntIT(32))
          val resultCE = ArrayLengthIE(arrayCE)
          (resultIT, resultCE)
        }
        case TransmigrateTE(sourceExpr, targetRegion) => {
          vassert(sourceExpr.kind.isPrimitive)
          val (sourceIT, sourceCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, sourceExpr)
          // Just a pass through, TransmigrateTE only exists for typing stage's benefit
          (sourceIT, sourceCE)
        }
        case DestroyImmRuntimeSizedArrayTE(arrayExpr, arrayType, consumer, consumerMethod) => {
          val (arrayIT, arrayCE) = translateRefExpr(denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, arrayExpr)
          val rsaIT = translateRuntimeSizedArray(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, arrayType)
          val (consumerIT, consumerCE) = translateRefExpr(denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, consumer)
          val (prototypeI, prototypeC) =
            translatePrototype(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, consumerMethod)

          val resultCE =
            DestroyImmRuntimeSizedArrayIE(
              arrayCE,
              RegionCollapserIndividual.collapseRuntimeSizedArray(rsaIT),
              consumerCE,
              prototypeC)
          (CoordI[sI](MutableShareI, VoidIT()), resultCE)
        }
        case DestroyMutRuntimeSizedArrayTE(arrayExpr) => {
          val (arrayIT, arrayCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, arrayExpr)
          val resultCE = DestroyMutRuntimeSizedArrayIE(arrayCE)
          (CoordI.void[sI], resultCE)
        }
        case NewMutRuntimeSizedArrayTE(arrayTT, region, capacityExpr) => {
          val arrayIT =
            translateRuntimeSizedArray(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, arrayTT)
          val resultIT =
            CoordI[sI](
              arrayIT.mutability match {
                case MutableI => OwnI
                case ImmutableI => MutableShareI
              },
              arrayIT)

          val (capacityIT, capacityCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, capacityExpr)

          val resultCE =
            NewMutRuntimeSizedArrayIE(
              RegionCollapserIndividual.collapseRuntimeSizedArray(arrayIT),
              capacityCE,
              RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)
        }
        case TupleTE(elements, resultReference) => {
          val elementsCE =
            elements.map(elementTE => {
              translateRefExpr(denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, elementTE)._2
            })

          val resultIT =
            translateCoord(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, resultReference)
                .coord

          (resultIT, TupleIE(elementsCE, RegionCollapserIndividual.collapseCoord(resultIT)))
        }
        case AsSubtypeTE(sourceExpr, targetSubtype, resultResultType, okConstructor, errConstructor, implIdT, okResultImplIdT, errResultImplIdT) => {
          val (sourceIT, sourceCE) =
            translateRefExpr(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, sourceExpr)
          val resultIT =
            translateCoord(
              denizenName, denizenBoundToDenizenCallerSuppliedThing,
              substitutions,
              perspectiveRegionT,
              resultResultType).coord
          val resultCE =
            AsSubtypeIE(
              sourceCE,
              RegionCollapserIndividual.collapseCoord(translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, targetSubtype).coord),
              RegionCollapserIndividual.collapseCoord(translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, resultResultType).coord),
              translatePrototype(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, okConstructor)._2,
              translatePrototype(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, errConstructor)._2,
              RegionCollapserIndividual.collapseImplId(
                translateImplId(
                  denizenName,
                  denizenBoundToDenizenCallerSuppliedThing,
                  substitutions,
                  perspectiveRegionT,
                  implIdT)),
              RegionCollapserIndividual.collapseImplId(
                translateImplId(
                  denizenName,
                  denizenBoundToDenizenCallerSuppliedThing,
                  substitutions,
                  perspectiveRegionT,
                  okResultImplIdT)),
              RegionCollapserIndividual.collapseImplId(
                translateImplId(
                  denizenName,
                  denizenBoundToDenizenCallerSuppliedThing,
                 substitutions,
                  perspectiveRegionT,
                  errResultImplIdT)),
              RegionCollapserIndividual.collapseCoord(resultIT))
          (resultIT, resultCE)
        }
        case other => vimpl(other)
      }
    //    if (opts.sanityCheck) {
    //      vassert(Collector.all(resultRefExpr, { case PlaceholderNameT(_) => }).isEmpty)
    //    }
    (resultIT, resultCE)
  }

  private def maybeImmutabilify(innerIE: ReferenceExpressionIE): ReferenceExpressionIE = {
    innerIE.result.kind match {
      case x if x.isPrimitive => {
        return innerIE // These are conceptually moved into the receiver's region
      }
      case _ => // continue
    }
    innerIE match {
      case SoftLoadIE(expr, MutableBorrowI, result) => {
        return SoftLoadIE(expr, ImmutableBorrowI, result.copy(ownership = ImmutableBorrowI))
      }
      case SoftLoadIE(expr, MutableShareI, result) => {
        return SoftLoadIE(expr, ImmutableShareI, result.copy(ownership = ImmutableShareI))
      }
      case _ => //continue
    }
    innerIE.result.ownership match {
      case OwnI => innerIE // These are being moved into the receiver's region
      case ImmutableBorrowI | ImmutableShareI => innerIE
      case MutableBorrowI => {
        ImmutabilifyIE(innerIE, innerIE.result.copy(ownership = ImmutableBorrowI))
      }
      case MutableShareI => {
        ImmutabilifyIE(innerIE, innerIE.result.copy(ownership = ImmutableShareI))
      }
    }
  }

  private def runInNewPureRegion[T](
      denizenName: IdT[IInstantiationNameT],
      denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
      substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
      denizenTemplateName: IdT[ITemplateNameT],
      newDefaultRegionT: RegionT,
      run: (Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]], RegionT) => T):
  T = {
    val newDefaultRegionNameT =
      newDefaultRegionT match {
        case RegionT(PlaceholderTemplataT(id@IdT(packageCoord, initSteps, r@RegionPlaceholderNameT(_, _, _, _)), RegionTemplataType())) => {
          RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r), RegionTemplataType()))
        }
        case other => vwat(other)
      }
    val newPerspectiveRegionT = newDefaultRegionNameT

    val newDefaultRegion = RegionTemplataI[sI](vassertSome(vregionmut(newDefaultRegionNameT.region.idT.localName.asInstanceOf[RegionPlaceholderNameT]).height))
    val oldSubstitutionsForThisDenizenTemplate =
      substitutions.getOrElse(denizenTemplateName, Map())
    val newSubstitutionsForThisDenizenTemplate =
      oldSubstitutionsForThisDenizenTemplate + (newDefaultRegionNameT.region.idT -> newDefaultRegion)
    val newSubstitutions =
      substitutions + (denizenTemplateName -> newSubstitutionsForThisDenizenTemplate)

    run(newSubstitutions, newPerspectiveRegionT)
  }

  def translateOwnership(
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    ownershipT: OwnershipT,
    regionT: RegionT):
  OwnershipI = {
    ownershipT match { // Now  if it's a borrow, figure out whether it's mutable or immutable
      case OwnT => OwnI
      case BorrowT => {
        if (regionIsMutable(substitutions, perspectiveRegionT, regionT)) {
          MutableBorrowI
        } else {
          ImmutableBorrowI
        }
      }
      case ShareT => {
        if (regionIsMutable(substitutions, perspectiveRegionT, regionT)) {
          MutableShareI
        } else {
          ImmutableShareI
        }
      }
      case WeakT => vimpl()
    }
  }

  private def composeOwnerships(outerOwnership: OwnershipT, innerOwnership: OwnershipI, kind: KindIT[sI]) = {
    // TODO: see if we can combine this with the other composeOwnerships function.
    kind match {
      case IntIT(_) | BoolIT() | VoidIT() => {
        // We don't want any ImmutableShareH for primitives, it's better to only ever have one
        // ownership for primitives.
        MutableShareI
      }
      case _ => {
        ((outerOwnership, innerOwnership) match {
          case (OwnT, OwnI) => OwnI
          // case (OwnT, ImmutableShareI) => ImmutableShareI
          case (OwnT | BorrowT, MutableShareI | ImmutableShareI) => {
            // We disregard whether it's a MutableShareI or ImmutableShareI because
            // that was likely calculated under different circumstances from a
            // different perspective region.
            // We'll recalculate it now with out own perspective region.
            // See IPOMFIC.
            //if (regionIsMutable(substitutions, perspectiveRegionT, expectRegionPlaceholder(outerRegion))) {
            MutableShareI
            // } else {
            //   ImmutableShareI
            // }
          }
          //                case (OwnT, BorrowT) => BorrowT
          case (OwnT, MutableBorrowI) => {
            vregionmut() // here too maybe?
            MutableBorrowI
          }
          //                case (BorrowT, OwnT) => BorrowT
          case (BorrowT, OwnI) => {
            vregionmut() // we'll probably want a regionIsMutable call like above
            MutableBorrowI
          }
          //                case (BorrowT, BorrowT) => BorrowT
          case (BorrowT, MutableBorrowI) => {
            vregionmut() // we'll probably want a regionIsMutable call like above
            MutableBorrowI
          }
          //                case (BorrowT, WeakT) => WeakT
          //                case (BorrowT, ShareT) => ShareT
          //                case (WeakT, OwnT) => WeakT
          case (WeakT, OwnI) => {
            vregionmut() // here too maybe?
            WeakI
          }
          //                case (WeakT, BorrowT) => WeakT
          //                case (WeakT, WeakT) => WeakT
          //                case (WeakT, ShareT) => ShareT
          //                case (ShareT, ShareT) => ShareT
          case (ShareT, MutableShareI) => {
            vregionmut() // here too maybe?
            MutableShareI
          }
          //                case (OwnT, ShareT) => ShareT
          case other => vwat(other)
        })
      }
    }
  }

  // TODO: see if we can combine this with the other composeOwnerships function.
  def composeOwnerships(
    outerOwnership: OwnershipT,
    innerOwnership: OwnershipI):
  OwnershipT = {
    (outerOwnership, innerOwnership) match {
      case (OwnT, OwnI) => OwnT
      case (OwnT, MutableBorrowI) => BorrowT
      case (BorrowT, OwnI) => BorrowT
      case (BorrowT, MutableBorrowI) => BorrowT
      case (BorrowT, WeakI) => WeakT
      case (BorrowT, MutableShareI) => ShareT
      case (WeakT, OwnI) => WeakT
      case (WeakT, MutableBorrowI) => WeakT
      case (WeakT, WeakI) => WeakT
      case (WeakT, MutableShareI) => ShareT
      case (ShareT, MutableShareI) => ShareT
      case (OwnT, MutableShareI) => ShareT
      case other => vwat(other)
    }
  }

  private def coordRegionIsMutable(
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    coord: CoordT):
  Boolean = {
    regionIsMutable(substitutions, perspectiveRegionT, coord.region)
  }


  private def regionIsMutable(
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    region: RegionT):
  Boolean = {
    val RegionPlaceholderNameT(_, _, _, _) = region.region.idT.localName

    val perspectiveActualPureHeight =
      ITemplataI.expectRegionTemplata(
        vassertSome(vassertSome(substitutions.get(perspectiveRegionT.region.idT.initId(interner)))
          .get(perspectiveRegionT.region.idT)))
        .pureHeight

    val regionActualPureHeight =
      ITemplataI.expectRegionTemplata(
        vassertSome(vassertSome(substitutions.get(region.region.idT.initId(interner)))
          .get(region.region.idT)))
        .pureHeight

    perspectiveActualPureHeight == regionActualPureHeight
  }

  def translateFunctionId(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    idT: IdT[IFunctionNameT]):
  IdI[sI, IFunctionNameI[sI]] = {
    val IdT(module, steps, last) = idT
    val id =
      IdI(
        module,
        steps.map(translateName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
        translateFunctionName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, last))
    //    if (opts.sanityCheck) {
    //      vassert(Collector.all(id, { case PlaceholderNameT(_) => }).isEmpty)
    //    }
    id
  }

//  def translateRegionId(
//    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplata[ITemplataType]]],
//    perspectiveRegionT: RegionT,
//    idT: IdT[IRegionNameT]//,
//    //instantiationBoundArgs: InstantiationBoundArguments
//  ):
//  IdT[IRegionNameT] = {
//    idT match {
//      case IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _)) => {
//          IdT(
//            packageCoord,
//            initSteps.map(translateName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
//            translateRegionName(substitutions, perspectiveRegionT, r))
//      }
//    }
//  }

  def translateStructId(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    idT: IdT[IStructNameT],
    instantiationBoundArgs: InstantiationBoundArgumentsI):
  IdI[sI, IStructNameI[sI]] = {
    val IdT(module, steps, lastT) = idT

    val idI =
      IdI(
        module,
        steps.map(translateName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
        translateStructName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, lastT))


    collapseAndTranslateStructDefinition(
      opts, interner, keywords, hinputs, monouts, idT, idI, instantiationBoundArgs)

    idI
  }

  def translateInterfaceId(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    idT: IdT[IInterfaceNameT],
    instantiationBoundArgs: InstantiationBoundArgumentsI):
  IdI[sI, IInterfaceNameI[sI]] = {
    val IdT(module, steps, last) = idT
    val newIdS =
      IdI(
        module,
        steps.map(translateName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
        translateInterfaceName(denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, last))


    collapseAndTranslateInterfaceDefinition(
      opts, interner, keywords, hinputs, monouts, idT, newIdS, instantiationBoundArgs)

    newIdS
  }

  def translateImplId(
      denizenName: IdT[IInstantiationNameT],
      denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
      substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
      perspectiveRegionT: RegionT,
      implIdT: IdT[IImplNameT]):
  IdI[sI, IImplNameI[sI]] = {
    val IdT(module, steps, lastT) = implIdT

    // collapseAndTranslateImplDefinition(
    //   opts, interner, keywords, hinputs, monouts, implIdT, implIdT, instantiationBoundArgs)

    implIdT match {
      case IdT(packageCoord, initSteps, name @ ImplBoundNameT(_, _)) => {
        val implBoundName = IdT(packageCoord, initSteps, name)
        val implIdS = vassertSome(denizenBoundToDenizenCallerSuppliedThing.boundParamImplIdToBoundArgImplId.get(implBoundName))

        // val implIdC =
        //   RegionCollapserIndividual.collapseImplId(implIdS)
        implIdS
      }
      case _ => {
        val implIdS =
          IdI(
            module,
            steps.map(translateName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
            translateImplName(
              denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, lastT))

        val implIdN =
          RegionCollapserConsistent.collapseImplId(
            RegionCounter.countImplId(implIdS),
            implIdS)

        val runeToBoundArgsForNewImpl =
          translateBoundArgsForCallee(
            denizenName,
            denizenBoundToDenizenCallerSuppliedThing,
            substitutions,
            perspectiveRegionT,
            hinputs.getInstantiationBoundArgs(implIdT))

        monouts.newImpls.enqueue((implIdT, implIdN, runeToBoundArgsForNewImpl))

        implIdS
      }
    }
  }

  def translateCitizenName(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    t: ICitizenNameT):
  ICitizenNameI[sI] = {
    t match {
      case s : IStructNameT => translateStructName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, s)
      case i : IInterfaceNameT => translateInterfaceName(denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, i)
    }
  }

  def translateId(
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    id: IdT[INameT]):
  IdI[sI, INameI[sI]] = {
    id match {
      case other => vimpl(other)
    }
  }

  def translateCitizenId(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    id: IdT[ICitizenNameT],
    instantiationBoundArgs: InstantiationBoundArgumentsI):
  IdI[sI, ICitizenNameI[sI]] = {
    id match {
      case IdT(module, steps, last : IStructNameT) => {
        translateStructId(
          denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, IdT(module, steps, last), instantiationBoundArgs)
      }
      case IdT(module, steps, last : IInterfaceNameT) => {
        translateInterfaceId(
          denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, IdT(module, steps, last), instantiationBoundArgs)
      }
      case other => vimpl(other)
    }
  }

  def translateImplId(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    idT: IdT[IImplNameT],
    instantiationBoundArgs: InstantiationBoundArgumentsI):
  IdI[sI, IImplNameI[sI]] = {
    val IdT(module, steps, last) = idT
    val idI =
      IdI(
        module,
        steps.map(translateName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
        translateImplName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, last))
    val idC = vimpl(idI)

    idT match {
      case IdT(packageCoord, initSteps, name@ImplBoundNameT(_, _)) => {
        val implBoundNameT = IdT(packageCoord, initSteps, name)
        val result =
          vassertSome(
            denizenBoundToDenizenCallerSuppliedThing.boundParamImplIdToBoundArgImplId.get(implBoundNameT))
        //        if (opts.sanityCheck) {
        //          vassert(Collector.all(result, { case PlaceholderTemplateNameT(_) => }).isEmpty)
        //        }
        result
      }
      case IdT(_, _, _) => {
        monouts.newImpls.enqueue((idT, idC, instantiationBoundArgs))
        idI
      }
    }
  }

  def translateCoord(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    coord: CoordT):
  CoordTemplataI[sI] = {
    val CoordT(outerOwnership, outerRegion, kind) = coord
    val outerRegionI =
      translateTemplata(
        denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, outerRegion.region)
          .expectRegionTemplata()

    kind match {
      case KindPlaceholderT(placeholderId) => {
        // Let's get the index'th placeholder from the top level denizen.
        // If we're compiling a function or a struct, it might actually be a lambda function or lambda struct.
        // In these cases, the topLevelDenizenPlaceholderIndexToTemplata actually came from the containing function,
        // see LHPCTLD.

        vassertSome(vassertSome(substitutions.get(placeholderId.initId(interner))).get(placeholderId)) match {
          case CoordTemplataI(region, CoordI(innerOwnership, kind)) => {
            // TODO: see if we can combine this with the other composeOwnerships function.
            val combinedOwnership =
              composeOwnerships(outerOwnership, innerOwnership, kind)
//            vassert(innerRegion == translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, outerRegion))
            CoordTemplataI(outerRegionI, CoordI(combinedOwnership, kind))
          }
          case KindTemplataI(kind) => {
//            val newOwnership =
//              getMutability(kind) match {
//                case ImmutableT => ShareT
//                case MutableT => outerOwnership
//              }
            CoordTemplataI(outerRegionI, CoordI(vimpl(/*newOwnership*/), vimpl(kind)))
          }
        }
      }
      case other => {
        // We could, for example, be translating an Vector<myFunc$0, T> (which is temporarily regarded mutable)
        // to an Vector<imm, int> (which is immutable).
        // So, we have to check for that here and possibly make the ownership share.
        val kind = translateKind(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, other)
        val newOwnership =
          kind match {
            case IntIT(_) | BoolIT() | VoidIT() => {
              // We don't want any ImmutableShareH for primitives, it's better to only ever have one
              // ownership for primitives.
              MutableShareI
            }
            case _ => {
              val mutability = getMutability(RegionCollapserIndividual.collapseKind(kind))
              ((outerOwnership, mutability) match {
                case (_, ImmutableI) => ShareT
                case (other, MutableI) => other
              }) match { // Now  if it's a borrow, figure out whether it's mutable or immutable
                case BorrowT => {
                  if (regionIsMutable(substitutions, perspectiveRegionT, outerRegion)) {
                    MutableBorrowI
                  } else {
                    ImmutableBorrowI
                  }
                }
                case ShareT => {
                  if (regionIsMutable(substitutions, perspectiveRegionT, outerRegion)) {
                    MutableShareI
                  } else {
                    ImmutableShareI
                  }
                }
                case OwnT => {
                  // We don't have this assert because we sometimes can see owning references even
                  // though we dont hold them, see RMLRMO.
                  // vassert(regionIsMutable(substitutions, perspectiveRegionT, expectRegionPlaceholder(outerRegion)))
                  OwnI
                }
                case WeakT => vimpl()
              }
            }
          }
//        val newRegion = expectRegionTemplata(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, outerRegion))
        CoordTemplataI(outerRegionI, CoordI(newOwnership, kind))
      }
    }
  }

  def getMutability(t: KindIT[cI]): MutabilityI = {
    t match {
      case IntIT(_) | BoolIT() | StrIT() | NeverIT(_) | FloatIT() | VoidIT() => ImmutableI
      case StructIT(name) => {
        vassertSome(monouts.structToMutability.get(name))
      }
      case InterfaceIT(name) => {
        vassertSome(monouts.interfaceToMutability.get(name))
      }
      case RuntimeSizedArrayIT(IdI(_, _, RuntimeSizedArrayNameI(_, RawArrayNameI(mutability, _, region)))) => {
        mutability
      }
      case StaticSizedArrayIT(IdI(_, _, StaticSizedArrayNameI(_, _, _, RawArrayNameI(mutability, _, region)))) => {
        mutability
      }
      case other => vimpl(other)
    }
  }

  def translateCitizen(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    citizen: ICitizenTT,
    instantiationBoundArgs: InstantiationBoundArgumentsI):
  ICitizenIT[sI] = {
    citizen match {
      case s @ StructTT(_) => translateStruct(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, s, instantiationBoundArgs)
      case s @ InterfaceTT(_) => translateInterface(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, s, instantiationBoundArgs)
    }
  }

  def translateStruct(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    struct: StructTT,
    instantiationBoundArgs: InstantiationBoundArgumentsI):
  StructIT[sI] = {
    val StructTT(id) = struct

    val desiredStruct =
      StructIT(
        translateStructId(
          denizenName, denizenBoundToDenizenCallerSuppliedThing,
          substitutions, perspectiveRegionT, id, instantiationBoundArgs))

    desiredStruct
  }

  def translateInterface(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    interface: InterfaceTT,
    instantiationBoundArgs: InstantiationBoundArgumentsI):
  InterfaceIT[sI] = {
    val InterfaceTT(id) = interface

    val desiredInterface =
      InterfaceIT(
        translateInterfaceId(
          denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, id, instantiationBoundArgs))

    desiredInterface
  }

  def translateSuperKind(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    kind: ISuperKindTT):
  InterfaceIT[sI] = {
    kind match {
      case i @ InterfaceTT(_) => {
        translateInterface(
          denizenName,
          denizenBoundToDenizenCallerSuppliedThing,
          substitutions,
          perspectiveRegionT,
          i,
          translateBoundArgsForCallee(
            denizenName,
            denizenBoundToDenizenCallerSuppliedThing,
            substitutions,
            perspectiveRegionT,
            hinputs.getInstantiationBoundArgs(i.id)))
      }
      case p @ KindPlaceholderT(_) => {
        translatePlaceholder(substitutions, p) match {
          case s : InterfaceIT[_] => {
            vassert(s.isInstanceOf[InterfaceIT[sI]])
            s.asInstanceOf[InterfaceIT[sI]]
          }
          case other => vwat(other)
        }
      }
    }
  }

  def translatePlaceholder(
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    t: KindPlaceholderT):
  KindIT[sI] = {
    val newSubstitutingTemplata =
      vassertSome(
        vassertSome(substitutions.get(t.id.initId(interner)))
        .get(t.id))
    ITemplataI.expectKindTemplata(newSubstitutingTemplata).kind
  }

  def translateStaticSizedArray(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    ssaTT: StaticSizedArrayTT):
  StaticSizedArrayIT[sI] = {
    val StaticSizedArrayTT(
    IdT(
    packageCoord,
    initSteps,
    StaticSizedArrayNameT(StaticSizedArrayTemplateNameT(), sizeT, variabilityT, RawArrayNameT(mutabilityT, elementTypeT, ssaRegionT)))) = ssaTT

    val newPerspectiveRegionT =
      ssaRegionT match {
        case RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _)), RegionTemplataType())) => {
          RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r), RegionTemplataType()))
        }
        case _ => vwat()
      }

    // We use newPerspectiveRegionT for these because of TTTDRM.
    val ssaRegion =
      ITemplataI.expectRegionTemplata(
        translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, newPerspectiveRegionT, ssaRegionT.region))
    // We dont have this assert because this might be a templata deep in a struct or function's
    // name, so the heights might actually be negative.
    // vassert(Some(ssaRegion.pureHeight) == newPerspectiveRegionT.localName.pureHeight)
    val intTemplata = ITemplataI.expectIntegerTemplata(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, newPerspectiveRegionT, sizeT)).value
    val variabilityTemplata = ITemplataI.expectVariabilityTemplata(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, newPerspectiveRegionT, variabilityT)).variability
    val mutabilityTemplata =
      ITemplataI.expectMutabilityTemplata(
        translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, newPerspectiveRegionT, mutabilityT)).mutability
    val elementType =
      translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, newPerspectiveRegionT, elementTypeT)

    StaticSizedArrayIT(
      IdI(
        packageCoord,
        initSteps.map(translateName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
        StaticSizedArrayNameI(
          StaticSizedArrayTemplateNameI(),
          intTemplata,
          variabilityTemplata,
          RawArrayNameI(
            mutabilityTemplata,
            elementType,
            ssaRegion))))
  }

  def translateRuntimeSizedArray(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
      rsaTT: RuntimeSizedArrayTT):
  RuntimeSizedArrayIT[sI] = {
    val RuntimeSizedArrayTT(
      IdT(
      packageCoord,
      initSteps,
      RuntimeSizedArrayNameT(RuntimeSizedArrayTemplateNameT(), RawArrayNameT(mutabilityT, elementTypeT, rsaRegionT)))) = rsaTT

    val newPerspectiveRegionT =
      rsaRegionT match {
        case RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _)), RegionTemplataType())) => {
          RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r), RegionTemplataType()))
        }
        case _ => vwat()
      }

    // We use newPerspectiveRegionT for these because of TTTDRM.
    val rsaRegion =
      ITemplataI.expectRegionTemplata(
        translateTemplata(
          denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, newPerspectiveRegionT, rsaRegionT.region))
    // We dont have this assert because this might be a templata deep in a struct or function's
    // name, so the heights might actually be negative.
    // vassert(Some(ssaRegion.pureHeight) == newPerspectiveRegionT.localName.pureHeight)
    val mutabilityTemplata =
      ITemplataI.expectMutabilityTemplata(
        translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, newPerspectiveRegionT, mutabilityT)).mutability
    val elementType = translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, newPerspectiveRegionT, elementTypeT)

    RuntimeSizedArrayIT(
      IdI(
        packageCoord,
        initSteps.map(translateName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
        RuntimeSizedArrayNameI(
          RuntimeSizedArrayTemplateNameI(),
          RawArrayNameI(
            mutabilityTemplata,
            elementType,
            rsaRegion))))
  }

  def translateKind(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    kind: KindT):
  KindIT[sI] = {
    kind match {
      case IntT(bits) => IntIT(bits)
      case BoolT() => BoolIT()
      case FloatT() => FloatIT()
      case VoidT() => VoidIT()
      case StrT() => StrIT()
      case NeverT(fromBreak) => NeverIT(fromBreak)
      case p @ KindPlaceholderT(_) => translatePlaceholder(substitutions, p)
      case s @ StructTT(_) => {
        translateStruct(
          denizenName,
          denizenBoundToDenizenCallerSuppliedThing,
          substitutions,
          perspectiveRegionT,
          s,
          translateBoundArgsForCallee(denizenName, denizenBoundToDenizenCallerSuppliedThing,
            substitutions, perspectiveRegionT, hinputs.getInstantiationBoundArgs(s.id)))
      }
      case s @ InterfaceTT(_) => {
        translateInterface(
          denizenName,
          denizenBoundToDenizenCallerSuppliedThing,
          substitutions,
          perspectiveRegionT,
          s,
          translateBoundArgsForCallee(
            denizenName, denizenBoundToDenizenCallerSuppliedThing,
            substitutions, perspectiveRegionT, hinputs.getInstantiationBoundArgs(s.id)))
      }
      case a @ contentsStaticSizedArrayTT(_, _, _, _, _) => translateStaticSizedArray(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, a)
      case a @ contentsRuntimeSizedArrayTT(_, _, _) => translateRuntimeSizedArray(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, a)
      case other => vimpl(other)
    }
  }

  def translateParameter(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    param: ParameterT):
  ParameterI = {
    val ParameterT(name, virtuality, preChecked, tyype) = param
    val typeIT =
      translateCoord(
        denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, tyype)
          .coord
    val nameS = translateVarName(name)
    ParameterI(
      RegionCollapserIndividual.collapseVarName(nameS),
      virtuality.map({ case AbstractT() => AbstractI() }),
      preChecked,
      RegionCollapserIndividual.collapseCoord(typeIT))
  }

  def translateTemplata(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    templata: ITemplataT[ITemplataType]):
  ITemplataI[sI] = {
    val result =
      templata match {
        case PlaceholderTemplataT(n, tyype) => {
          val substitution =
            vassertSome(vassertSome(substitutions.get(n.initId(interner))).get(n))
          tyype match {
            case RegionTemplataType() => {
              n match {
                case IdT(_, _, RegionPlaceholderNameT(_, _, maybeLocalPureHeightT, _)) => {
                  RegionTemplataI[sI](
                    maybeLocalPureHeightT match {
                      case Some(n) => n
                      case None => expectRegionTemplata(substitution).pureHeight
                    })
                }
                case IdT(_, _, CoordGenericParamRegionPlaceholderNameT(_, _, maybeLocalPureHeightT, _)) => {
                  RegionTemplataI[sI](
                    maybeLocalPureHeightT match {
                      case Some(n) => n
                      case None => expectRegionTemplata(substitution).pureHeight
                    })
                }
                case other => vwat(other)
              }
            }
            case _ => substitution
          }
        }
        case IntegerTemplataT(value) => IntegerTemplataI[sI](value)
        case BooleanTemplataT(value) => BooleanTemplataI[sI](value)
        case StringTemplataT(value) => StringTemplataI[sI](value)
        case CoordTemplataT(coord) => {
          translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, coord)
        }
        case MutabilityTemplataT(mutability) => MutabilityTemplataI[sI](translateMutability(mutability))
        case VariabilityTemplataT(variability) => VariabilityTemplataI[sI](translateVariability(variability))
        case KindTemplataT(kind) => KindTemplataI[sI](translateKind(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, kind))
        case other => vimpl(other)
      }
    if (opts.sanityCheck) {
      vassert(Collector.all(result, { case KindPlaceholderNameT(_) => }).isEmpty)
    }
    result
  }

  def translateVarName(
    name: IVarNameT):
  IVarNameI[sI] = {
    name match {
      case TypingPassFunctionResultVarNameT() => TypingPassFunctionResultVarNameI()
      case CodeVarNameT(x) => CodeVarNameI(x)
      case ClosureParamNameT(x) => ClosureParamNameI(x)
      case TypingPassBlockResultVarNameT(LocationInFunctionEnvironmentT(path)) => TypingPassBlockResultVarNameI(LocationInFunctionEnvironmentI(path))
      case TypingPassTemporaryVarNameT(LocationInFunctionEnvironmentT(path)) => TypingPassTemporaryVarNameI(LocationInFunctionEnvironmentI(path))
      case ConstructingMemberNameT(x) => ConstructingMemberNameI(x)
      case IterableNameT(range) => IterableNameI(range)
      case IteratorNameT(range) => IteratorNameI(range)
      case IterationOptionNameT(range) => IterationOptionNameI(range)
      case MagicParamNameT(codeLocation2) => MagicParamNameI(codeLocation2)
      case SelfNameT() => SelfNameI()
      case other => vimpl(other)
    }
  }

  def translateFunctionTemplateName(name: IFunctionTemplateNameT): IFunctionTemplateNameI[sI] = {
    name match {
      case FunctionTemplateNameT(humanName, codeLocation) => FunctionTemplateNameI(humanName, codeLocation)
      case other => vimpl(other)
    }
  }

  def translateFunctionName(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    name: IFunctionNameT):
  IFunctionNameI[sI] = {
    name match {
      case FunctionNameT(FunctionTemplateNameT(humanName, codeLoc), templateArgs, params) => {
        FunctionNameIX(
          FunctionTemplateNameI(humanName, codeLoc),
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
          params.map(translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _).coord))
      }
      case ForwarderFunctionNameT(ForwarderFunctionTemplateNameT(innerTemplate, index), inner) => {
        ForwarderFunctionNameI(
          ForwarderFunctionTemplateNameI(
            translateFunctionTemplateName(innerTemplate),
            index),
          translateFunctionName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, inner))
      }
      case ExternFunctionNameT(humanName, parameters) => {
        ExternFunctionNameI(
          humanName, parameters.map(translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _).coord))
      }
      case FunctionBoundNameT(FunctionBoundTemplateNameT(humanName), templateArgs, params) => {
        FunctionBoundNameI(
          FunctionBoundTemplateNameI(humanName),
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
          params.map(translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _).coord))
      }
      case AnonymousSubstructConstructorNameT(template, templateArgs, params) => {
        AnonymousSubstructConstructorNameI(
          translateName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, template) match {
            case x @ AnonymousSubstructConstructorTemplateNameI(_) => x
            case other => vwat(other)
          },
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
          params.map(translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _).coord))
      }
      case LambdaCallFunctionNameT(LambdaCallFunctionTemplateNameT(codeLocation, paramTypesForGeneric), templateArgs, paramTypes) => {
        LambdaCallFunctionNameI(
          LambdaCallFunctionTemplateNameI(
            codeLocation,
            // We dont translate these, as these are what uniquely identify generics, and we need that
            // information later to map this back to its originating generic.
            // See DMPOGN for a more detailed explanation. This oddity is really tricky.
            paramTypesForGeneric),
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
          paramTypes.map(translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _).coord))
      }
      case OverrideDispatcherNameT(OverrideDispatcherTemplateNameT(implTemplateId), templateArgs, paramTypes) => {
        OverrideDispatcherNameI(
          OverrideDispatcherTemplateNameI(
            translateId[IImplTemplateNameT, IImplTemplateNameI[sI]](implTemplateId, translateImplTemplateName)),
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
          paramTypes.map(translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _).coord))
      }
//      case FunctionBoundNameT(FunctionBoundTemplateNameT(humanName, runeInImpl, runeInCitizen), templateArgs, paramTypes) => {
//        CaseFunctionFromImplNameI(
//          CaseFunctionFromImplTemplateNameI(humanName, runeInImpl, runeInCitizen),
//          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
//          paramTypes.map(translateCoord(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _).coord))
//      }
      case other => vimpl(other)
    }
  }

  def translateImplName(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    name: IImplNameT):
  IImplNameI[sI] = {
    name match {
      case ImplNameT(ImplTemplateNameT(codeLocationS), templateArgs, subCitizen) => {
        ImplNameI(
          ImplTemplateNameI(codeLocationS),
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
          translateCitizen(denizenName, denizenBoundToDenizenCallerSuppliedThing,
            substitutions,
            perspectiveRegionT,
            subCitizen,
            translateBoundArgsForCallee(
              denizenName,
              denizenBoundToDenizenCallerSuppliedThing,
              substitutions,
              perspectiveRegionT,
              hinputs.getInstantiationBoundArgs(subCitizen.id))))
      }
      case ImplBoundNameT(ImplBoundTemplateNameT(codeLocationS), templateArgs) => {
        ImplBoundNameI(
          ImplBoundTemplateNameI(codeLocationS),
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)))
      }
      case AnonymousSubstructImplNameT(AnonymousSubstructImplTemplateNameT(interface), templateArgs, subCitizen) => {
        AnonymousSubstructImplNameI(
          AnonymousSubstructImplTemplateNameI(
            translateInterfaceTemplateName(interface)),
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)),
          translateCitizen(denizenName, denizenBoundToDenizenCallerSuppliedThing,
            substitutions,
            perspectiveRegionT,
            subCitizen,
            translateBoundArgsForCallee(
              denizenName,
              denizenBoundToDenizenCallerSuppliedThing,
              substitutions,
              perspectiveRegionT,
              hinputs.getInstantiationBoundArgs(subCitizen.id))))
      }
    }
  }

  def translateImplTemplateName(
      name: IImplTemplateNameT):
  IImplTemplateNameI[sI] = {
    name match {
      case ImplTemplateNameT(codeLocationS) => ImplTemplateNameI(codeLocationS)
      case ImplBoundTemplateNameT(codeLocationS) => ImplBoundTemplateNameI(codeLocationS)
      case AnonymousSubstructImplTemplateNameT(interface) => {
        AnonymousSubstructImplTemplateNameI(
          translateInterfaceTemplateName(interface))
      }
    }
  }

//  def translateRegionName(
//    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplata[ITemplataType]]],
//    perspectiveRegionT: RegionT,
//    name: IRegionNameT):
//  IRegionNameT = {
//    name match {
//      case RegionPlaceholderNameT(index, rune, originallyIntroducedLocation, originallyMutable) => {
//
//      }
//    }
//  }

  def translateStructName(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    // See TTTDRM, this is the region from which we're determining other regions' mutabilities.
    perspectiveRegionT: RegionT,
    name: IStructNameT):
  IStructNameI[sI] = {
    val newPerspectiveRegionT =
      vassertSome(name.templateArgs.lastOption) match {
        case PlaceholderTemplataT(IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _)), RegionTemplataType()) => {
          RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r), RegionTemplataType()))
        }
        case _ => vwat()
      }
    name match {
      case StructNameT(StructTemplateNameT(humanName), templateArgs) => {
        StructNameI(
          StructTemplateNameI(humanName),
          // We use newPerspectiveRegionT here because of TTTDRM.
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, newPerspectiveRegionT, _)))
      }
      case AnonymousSubstructNameT(AnonymousSubstructTemplateNameT(interface), templateArgs) => {
        AnonymousSubstructNameI(
          AnonymousSubstructTemplateNameI(
            translateInterfaceTemplateName(interface)),
          // We use newPerspectiveRegionT here because of TTTDRM.
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, newPerspectiveRegionT, _)))
      }
      case LambdaCitizenNameT(LambdaCitizenTemplateNameT(codeLocation)) => {
        LambdaCitizenNameI(LambdaCitizenTemplateNameI(codeLocation))
      }
      case other => vimpl(other)
    }
  }

  def translateInterfaceName(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    name: IInterfaceNameT):
  IInterfaceNameI[sI] = {
    name match {
      case InterfaceNameT(InterfaceTemplateNameT(humanName), templateArgs) => {
        InterfaceNameI(
          InterfaceTemplateNameI(humanName),
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)))
      }
      case other => vimpl(other)
    }
  }

  def translateInterfaceTemplateName(
    name: IInterfaceTemplateNameT):
  IInterfaceTemplateNameI[sI] = {
    name match {
      case InterfaceTemplateNameT(humanName) => InterfaceTemplateNameI(humanName)
      case other => vimpl(other)
    }
  }

  def translateName(
    denizenName: IdT[IInstantiationNameT],
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    perspectiveRegionT: RegionT,
    name: INameT):
  INameI[sI] = {
    name match {
      case v : IVarNameT => translateVarName(v)
      case KindPlaceholderTemplateNameT(index, _) => vwat()
      case KindPlaceholderNameT(inner) => vwat()
      case StructNameT(StructTemplateNameT(humanName), templateArgs) => {
        StructNameI(
          StructTemplateNameI(humanName),
          templateArgs.map(translateTemplata(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, _)))
      }
      case ForwarderFunctionTemplateNameT(inner, index) => {
        ForwarderFunctionTemplateNameI(
          translateFunctionTemplateName(inner),
          index)
      }
      case AnonymousSubstructConstructorTemplateNameT(substructTemplateName) => {
        AnonymousSubstructConstructorTemplateNameI(
          translateName(denizenName, denizenBoundToDenizenCallerSuppliedThing, substitutions, perspectiveRegionT, substructTemplateName) match {
            case x : ICitizenTemplateNameI[sI] => x
            case other => vwat(other)
          })
      }
      case FunctionTemplateNameT(humanName, codeLoc) => FunctionTemplateNameI(humanName, codeLoc)
      case StructTemplateNameT(humanName) => StructTemplateNameI(humanName)
      case LambdaCitizenTemplateNameT(codeLoc) => LambdaCitizenTemplateNameI(codeLoc)
      case AnonymousSubstructTemplateNameT(interface) => {
        AnonymousSubstructTemplateNameI(
          translateInterfaceTemplateName(interface))
      }
      case LambdaCitizenNameT(LambdaCitizenTemplateNameT(codeLocation)) => {
        LambdaCitizenNameI(LambdaCitizenTemplateNameI(codeLocation))
      }
      case InterfaceTemplateNameT(humanNamee) => InterfaceTemplateNameI(humanNamee)
      //      case FreeTemplateNameT(codeLoc) => name
      case f : IFunctionNameT => translateFunctionName(denizenName, denizenBoundToDenizenCallerSuppliedThing,substitutions, perspectiveRegionT, f)
      case other => vimpl(other)
    }
  }

  def translateCollapsedImplDefinition(
    denizenName: IdT[IInstantiationNameT],
    implInstantiationBoundArgs: InstantiationBoundArgumentsI,
    denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArgS,
    substitutions: Map[IdT[INameT], Map[IdT[IPlaceholderNameT], ITemplataI[sI]]],
    implIdT: IdT[IImplNameT],
    implIdS: IdI[sI, IImplNameI[sI]],
    implIdC: IdI[cI, IImplNameI[cI]],
    implDefinition: EdgeT):
  Unit = {
    val EdgeT(_, subCitizen, superInterface, instantiationBoundParams, abstractFuncToOverrideFunc) = implDefinition

    if (opts.sanityCheck) {
      vassert(Collector.all(implIdS, { case KindPlaceholderNameT(_) => }).isEmpty)
    }

    val perspectiveRegionT =
      implIdT.localName.templateArgs.last match {
        case PlaceholderTemplataT(IdT(packageCoord, initSteps, r @ RegionPlaceholderNameT(_, _, _, _)), RegionTemplataType()) => {
          RegionT(PlaceholderTemplataT(IdT(packageCoord, initSteps, r), RegionTemplataType()))
        }
        case _ => vwat()
      }

    val subCitizenS =
      translateCitizen(
        denizenName, denizenBoundToDenizenCallerSuppliedThing,
        substitutions,
        vimpl(),
        implDefinition.subCitizen,
        translateBoundArgsForCallee(denizenName, denizenBoundToDenizenCallerSuppliedThing,
          substitutions,
          vimpl(),
          hinputs.getInstantiationBoundArgs(implDefinition.subCitizen.id)))
    val subCitizenC =
      RegionCollapserIndividual.collapseCitizen(subCitizenS)
    val superInterfaceS =
      translateInterfaceId(
        denizenName, denizenBoundToDenizenCallerSuppliedThing,
        substitutions,
        vimpl(),
        implDefinition.superInterface,
        translateBoundArgsForCallee(denizenName, denizenBoundToDenizenCallerSuppliedThing,
          substitutions,
          vimpl(),
          hinputs.getInstantiationBoundArgs(implDefinition.superInterface)))
    val superInterfaceC =
      RegionCollapserIndividual.collapseInterfaceId(superInterfaceS)

    val mutability = vassertSome(monouts.interfaceToMutability.get(superInterfaceC))
    if (monouts.implToMutability.contains(implIdC)) {
      return
    }
    monouts.implToMutability.put(implIdC, mutability)


    // We assemble the EdgeI at the very end of the instantiating stage.

    monouts.impls.put(implIdC, (subCitizenC, superInterfaceC, denizenBoundToDenizenCallerSuppliedThing, implInstantiationBoundArgs))

    vassertSome(monouts.interfaceToImplToAbstractPrototypeToOverride.get(superInterfaceC))
      .put(implIdC, mutable.HashMap())
    vassertSome(monouts.interfaceToImpls.get(superInterfaceC)).add((implIdT, implIdC))
  }
}
