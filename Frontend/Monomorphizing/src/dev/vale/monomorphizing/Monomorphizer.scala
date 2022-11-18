package dev.vale.monomorphizing

import dev.vale.options.GlobalOptions
import dev.vale.{Accumulator, Collector, Interner, Keywords, StrI, vassert, vassertOne, vassertSome, vcurious, vfail, vimpl, vpass, vwat}
import dev.vale.postparsing.{IRuneS, ITemplataType, IntegerTemplataType}
import dev.vale.typing.TemplataCompiler.{getTopLevelDenizenFullName, substituteTemplatasInKind}
import dev.vale.typing.{Hinputs, InstantiationBoundArguments, TemplataCompiler}
import dev.vale.typing.ast.{EdgeT, _}
import dev.vale.typing.env._
import dev.vale.typing.names._
import dev.vale.typing.templata.ITemplata.{expectIntegerTemplata, expectKind, expectKindTemplata, expectMutabilityTemplata, expectVariabilityTemplata}
import dev.vale.typing.templata._
import dev.vale.typing.types._

import scala.collection.immutable.Map
import scala.collection.mutable

case class DenizenBoundToDenizenCallerBoundArg(
  funcBoundToCallerSuppliedBoundArgFunc: Map[FullNameT[FunctionBoundNameT], PrototypeT],
  implBoundToCallerSuppliedBoundArgImpl: Map[FullNameT[ImplBoundNameT], FullNameT[IImplNameT]])

class MonomorphizedOutputs() {
  val functions: mutable.HashMap[FullNameT[IFunctionNameT], FunctionT] =
    mutable.HashMap[FullNameT[IFunctionNameT], FunctionT]()
  val structs: mutable.HashMap[FullNameT[IStructNameT], StructDefinitionT] = mutable.HashMap()
  val interfacesWithoutMethods: mutable.HashMap[FullNameT[IInterfaceNameT], InterfaceDefinitionT] = mutable.HashMap()

  // We can get some recursion if we have a self-referential struct like:
  //   struct Node<T> { value T; next Opt<Node<T>>; }
  // So we need these to short-circuit that nonsense.
  val startedStructs: mutable.HashMap[FullNameT[IStructNameT], (MutabilityT, DenizenBoundToDenizenCallerBoundArg)] = mutable.HashMap()
  val startedInterfaces: mutable.HashMap[FullNameT[IInterfaceNameT], (MutabilityT, DenizenBoundToDenizenCallerBoundArg)] = mutable.HashMap()

  //  val immKindToDestructor: mutable.HashMap[KindT, PrototypeT] =
  //    mutable.HashMap[KindT, PrototypeT]()

  // We already know from the hinputs that Some<T> implements Opt<T>.
  // In this map, we'll know that Some<int> implements Opt<int>, Some<bool> implements Opt<bool>, etc.
  val interfaceToImpls: mutable.HashMap[FullNameT[IInterfaceNameT], mutable.HashSet[FullNameT[IImplNameT]]] =
  mutable.HashMap()
  val interfaceToAbstractFuncToVirtualIndex: mutable.HashMap[FullNameT[IInterfaceNameT], mutable.HashMap[PrototypeT, Int]] =
    mutable.HashMap()
  val impls:
    mutable.HashMap[
      FullNameT[IImplNameT],
      (ICitizenTT, FullNameT[IInterfaceNameT], DenizenBoundToDenizenCallerBoundArg, Monomorphizer)] =
    mutable.HashMap()
  // We already know from the hinputs that Opt<T has drop> has func drop(T).
  // In this map, we'll know that Opt<int> has func drop(int).
  val abstractFuncToMonomorphizerAndSuppliedPrototypes: mutable.HashMap[FullNameT[IFunctionNameT], (Monomorphizer, InstantiationBoundArguments)] =
  mutable.HashMap()
  // This map collects all overrides for every impl. We'll use it to assemble vtables soon.
  val interfaceToImplToAbstractPrototypeToOverride:
    mutable.HashMap[FullNameT[IInterfaceNameT], mutable.HashMap[FullNameT[IImplNameT], mutable.HashMap[PrototypeT, OverrideT]]] =
    mutable.HashMap()

  // These are new impls and abstract funcs we discover for interfaces.
  // As we discover a new impl or a new abstract func, we'll later need to stamp a lot more overrides either way.
  val newImpls: mutable.Queue[(FullNameT[IImplNameT], InstantiationBoundArguments)] = mutable.Queue()
  // The int is a virtual index
  val newAbstractFuncs: mutable.Queue[(PrototypeT, Int, FullNameT[IInterfaceNameT], InstantiationBoundArguments)] = mutable.Queue()
  val newFunctions: mutable.Queue[(PrototypeT, InstantiationBoundArguments, Option[DenizenBoundToDenizenCallerBoundArg])] = mutable.Queue()

  def addMethodToVTable(
    implFullName: FullNameT[IImplNameT],
    superInterfaceFullName: FullNameT[IInterfaceNameT],
    abstractFuncPrototype: PrototypeT,
    overrride: OverrideT
  ) = {
    val map =
      interfaceToImplToAbstractPrototypeToOverride
        .getOrElseUpdate(superInterfaceFullName, mutable.HashMap())
        .getOrElseUpdate(implFullName, mutable.HashMap())
    vassert(!map.contains(abstractFuncPrototype))
    map.put(abstractFuncPrototype, overrride)
  }
}

object Monomorphizer {
  def translate(opts: GlobalOptions, interner: Interner, keywords: Keywords, hinputs: Hinputs): Hinputs = {
    val Hinputs(
    interfacesT,
    structsT,
    functionsT,
    //      oldImmKindToDestructorT,
    interfaceToEdgeBlueprintsT,
    interfaceToSubCitizenToEdgeT,
    instantiationNameToFunctionBoundToRuneT,
    kindExportsT,
    functionExportsT,
    kindExternsT,
    functionExternsT) = hinputs

    val monouts = new MonomorphizedOutputs()

    kindExportsT.foreach({ case KindExportT(range, tyype, packageCoordinate, exportedName) =>
      val packageName = FullNameT(packageCoordinate, Vector(), interner.intern(PackageTopLevelNameT()))
      val exportName =
        packageName.addStep(interner.intern(ExportNameT(interner.intern(ExportTemplateNameT(range.begin)))))
      val exportTemplateName = TemplataCompiler.getExportTemplate(exportName)
      val monomorphizer =
        new Monomorphizer(
          opts, interner, keywords, hinputs, monouts, exportTemplateName, exportName, Map(), DenizenBoundToDenizenCallerBoundArg(Map(), Map()))
      KindExportT(
        range,
        monomorphizer.translateKind(tyype),
        packageCoordinate,
        exportedName)
    })

    functionExportsT.foreach({ case FunctionExportT(range, prototype, packageCoordinate, exportedName) =>
      val packageName = FullNameT(packageCoordinate, Vector(), interner.intern(PackageTopLevelNameT()))
      val exportName =
        packageName.addStep(
          interner.intern(ExportNameT(interner.intern(ExportTemplateNameT(range.begin)))))
      val exportTemplateName = TemplataCompiler.getExportTemplate(exportName)
      val monomorphizer =
        new Monomorphizer(
          opts, interner, keywords, hinputs, monouts, exportTemplateName, exportName, Map(), DenizenBoundToDenizenCallerBoundArg(Map(), Map()))
      FunctionExportT(
        range,
        monomorphizer.translatePrototype(prototype),
        packageCoordinate,
        exportedName)
    })

    while ({
      // We make structs and interfaces eagerly as we come across them
      // if (monouts.newStructs.nonEmpty) {
      //   val newStructName = monouts.newStructs.dequeue()
      //   DenizenMonomorphizer.translateStructDefinition(opts, interner, keywords, hinputs, monouts, newStructName)
      //   true
      // } else if (monouts.newInterfaces.nonEmpty) {
      //   val (newInterfaceName, calleeRuneToSuppliedPrototype) = monouts.newInterfaces.dequeue()
      //   DenizenMonomorphizer.translateInterfaceDefinition(
      //     opts, interner, keywords, hinputs, monouts, newInterfaceName, calleeRuneToSuppliedPrototype)
      //   true
      // } else
      if (monouts.newFunctions.nonEmpty) {
        val (newFuncName, instantiationBoundArgs, maybeDenizenBoundToDenizenCallerSuppliedThing) =
          monouts.newFunctions.dequeue()
        Monomorphizer.translateFunction(
          opts, interner, keywords, hinputs, monouts, newFuncName, instantiationBoundArgs,
          maybeDenizenBoundToDenizenCallerSuppliedThing)
        true
      } else if (monouts.newImpls.nonEmpty) {
        val (implFullName, instantiationBoundsForUnsubstitutedImpl) = monouts.newImpls.dequeue()
        Monomorphizer.translateImpl(
          opts, interner, keywords, hinputs, monouts, implFullName, instantiationBoundsForUnsubstitutedImpl)
        true
      } else if (monouts.newAbstractFuncs.nonEmpty) {
        val (abstractFunc, virtualIndex, interfaceFullName, instantiationBoundArgs) = monouts.newAbstractFuncs.dequeue()
        Monomorphizer.translateAbstractFunc(
          opts, interner, keywords, hinputs, monouts, interfaceFullName, abstractFunc, virtualIndex, instantiationBoundArgs)
        true
      } else {
        false
      }
    }) {}

    //    interfaceToEdgeBlueprints.foreach({ case (interfacePlaceholderedFullName, edge) =>
    //      val monomorphizer = new DenizenMonomorphizer(interner, monouts, interfacePlaceholderedFullName)
    //
    //    })

    val interfaceEdgeBlueprints =
      monouts.interfaceToAbstractFuncToVirtualIndex.map({ case (interface, abstractFuncPrototypes) =>
        interface ->
          InterfaceEdgeBlueprint(
            interface,
            abstractFuncPrototypes.toVector)
      }).toMap

    val interfaces =
      monouts.interfacesWithoutMethods.values.map(interface => {
        val InterfaceDefinitionT(templateName, instantiatedInterface, ref, attributes, weakable, mutability, _, _, _) = interface
        InterfaceDefinitionT(
          templateName, instantiatedInterface, ref, attributes, weakable, mutability, Map(), Map(),
          vassertSome(monouts.interfaceToAbstractFuncToVirtualIndex.get(interface.ref.fullName)).toVector)
      })

    val interfaceToSubCitizenToEdge =
      monouts.interfaceToImpls.map({ case (interface, impls) =>
        interface ->
          impls.map(implFullName => {
            val (subCitizen, parentInterface, _, implMonomorphizer) = vassertSome(monouts.impls.get(implFullName))
            vassert(parentInterface == interface)
            val abstractFuncToVirtualIndex =
              vassertSome(monouts.interfaceToAbstractFuncToVirtualIndex.get(interface))
            val abstractFuncPrototypeToOverridePrototype =
              abstractFuncToVirtualIndex.map({ case (abstractFuncPrototype, virtualIndex) =>
                val overrride =
                  vassertSome(
                    vassertSome(
                      vassertSome(monouts.interfaceToImplToAbstractPrototypeToOverride.get(interface))
                        .get(implFullName))
                      .get(abstractFuncPrototype))

                vassert(
                  abstractFuncPrototype.fullName.last.parameters(virtualIndex).kind !=
                    overrride.overridePrototype.fullName.last.parameters(virtualIndex).kind)

                abstractFuncPrototype.fullName -> overrride
              })
            val edge =
              EdgeT(
                implFullName,
                subCitizen,
                interface,
                Map(),
                Map(),
                abstractFuncPrototypeToOverridePrototype.toMap)
            subCitizen.fullName -> edge
          }).toMap
      }).toMap

    Hinputs(
      interfaces.toVector,
      monouts.structs.values.toVector,
      monouts.functions.values.toVector,
      //      monouts.immKindToDestructor.toMap,
      interfaceEdgeBlueprints,
      interfaceToSubCitizenToEdge,
      Map(),
      kindExportsT,
      functionExportsT,
      kindExternsT,
      functionExternsT)
  }

  def translateInterfaceDefinition(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: Hinputs,
    monouts: MonomorphizedOutputs,
    interfaceFullName: FullNameT[IInterfaceNameT],
    instantiationBoundArgs: InstantiationBoundArguments):
  Unit = {
    val interfaceTemplate = TemplataCompiler.getInterfaceTemplate(interfaceFullName)

    val interfaceDefT =
      vassertOne(hinputs.interfaces.filter(_.templateName == interfaceTemplate))

    val monomorphizer =
      new Monomorphizer(
        opts,
        interner,
        keywords,
        hinputs,
        monouts,
        interfaceTemplate,
        interfaceFullName,
        interfaceFullName.last.templateArgs.toVector.zipWithIndex.map({ case (templateArg, index) =>
          interfaceTemplate.addStep(interner.intern(PlaceholderNameT(interner.intern(PlaceholderTemplateNameT(index))))) -> templateArg
        }).toMap,
        DenizenBoundToDenizenCallerBoundArg(
          assembleCalleeDenizenFunctionBounds(
            interfaceDefT.runeToFunctionBound,
            instantiationBoundArgs.runeToFunctionBoundArg),
          assembleCalleeDenizenImplBounds(
            interfaceDefT.runeToImplBound,
            instantiationBoundArgs.runeToImplBoundArg)))
    monomorphizer.translateInterfaceDefinition(interfaceFullName, interfaceDefT)
  }

  def assembleCalleeDenizenFunctionBounds(
    // This is from the receiver's perspective, they have some runes for their required functions.
    calleeRuneToReceiverBoundT: Map[IRuneS, FullNameT[FunctionBoundNameT]],
    // This is a map from the receiver's rune to the bound that the caller is supplying.
    calleeRuneToSuppliedPrototype: Map[IRuneS, PrototypeT]
  ): Map[FullNameT[FunctionBoundNameT], PrototypeT] = {
    calleeRuneToSuppliedPrototype.map({ case (calleeRune, suppliedFunctionT) =>
      vassertSome(calleeRuneToReceiverBoundT.get(calleeRune)) -> suppliedFunctionT
    })
  }

  def assembleCalleeDenizenImplBounds(
    // This is from the receiver's perspective, they have some runes for their required functions.
    calleeRuneToReceiverBoundT: Map[IRuneS, FullNameT[ImplBoundNameT]],
    // This is a map from the receiver's rune to the bound that the caller is supplying.
    calleeRuneToSuppliedImpl: Map[IRuneS, FullNameT[IImplNameT]]
  ): Map[FullNameT[ImplBoundNameT], FullNameT[IImplNameT]] = {
    calleeRuneToSuppliedImpl.map({ case (calleeRune, suppliedFunctionT) =>
      vassertSome(calleeRuneToReceiverBoundT.get(calleeRune)) -> suppliedFunctionT
    })
  }

  def translateStructDefinition(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: Hinputs,
    monouts: MonomorphizedOutputs,
    structFullName: FullNameT[IStructNameT],
    instantiationBoundArgs: InstantiationBoundArguments):
  Unit = {
    if (opts.sanityCheck) {
      vassert(Collector.all(structFullName, { case PlaceholderNameT(_) => }).isEmpty)
    }

    val structTemplate = TemplataCompiler.getStructTemplate(structFullName)

    val structDefT = findStruct(hinputs, structFullName)

    val topLevelDenizenFullName =
      getTopLevelDenizenFullName(structFullName)
    val topLevelDenizenTemplateFullName =
      TemplataCompiler.getTemplate(topLevelDenizenFullName)

    // One would imagine we'd get structFullName.last.templateArgs here, because that's the struct
    // we're about to monomorphize. However, only the top level denizen has placeholders, see LHPCTLD.
    // This struct might not be the top level denizen, such as if it's a lambda.
    val topLevelDenizenPlaceholderIndexToTemplata =
    topLevelDenizenFullName.last.templateArgs

    val denizenBoundToDenizenCallerSuppliedThing =
      DenizenBoundToDenizenCallerBoundArg(
        assembleCalleeDenizenFunctionBounds(
          structDefT.runeToFunctionBound, instantiationBoundArgs.runeToFunctionBoundArg),
        assembleCalleeDenizenImplBounds(
          structDefT.runeToImplBound, instantiationBoundArgs.runeToImplBoundArg))
    val monomorphizer =
      new Monomorphizer(
        opts,
        interner,
        keywords,
        hinputs,
        monouts,
        structTemplate,
        structFullName,
        topLevelDenizenPlaceholderIndexToTemplata.toVector.zipWithIndex.map({ case (templateArg, index) =>
          val placeholderName =
            topLevelDenizenTemplateFullName
              .addStep(interner.intern(PlaceholderNameT(interner.intern(PlaceholderTemplateNameT(index)))))
          placeholderName -> templateArg
        }).toMap,
        denizenBoundToDenizenCallerSuppliedThing)

    monomorphizer.translateStructDefinition(structFullName, structDefT)
  }

  private def findStruct(hinputs: Hinputs, structFullName: FullNameT[IStructNameT]) = {
    vassertOne(
      hinputs.structs
        .filter(structT => {
          TemplataCompiler.getSuperTemplate(structT.instantiatedCitizen.fullName) ==
            TemplataCompiler.getSuperTemplate(structFullName)
        }))
  }

  private def findInterface(hinputs: Hinputs, interfaceFullName: FullNameT[IInterfaceNameT]) = {
    vassertOne(
      hinputs.interfaces
        .filter(interfaceT => {
          TemplataCompiler.getSuperTemplate(interfaceT.instantiatedCitizen.fullName) ==
            TemplataCompiler.getSuperTemplate(interfaceFullName)
        }))
  }

  def translateAbstractFunc(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: Hinputs,
    monouts: MonomorphizedOutputs,
    interfaceFullName: FullNameT[IInterfaceNameT],
    abstractFunc: PrototypeT,
    virtualIndex: Int,
    instantiationBoundArgs: InstantiationBoundArguments):
  Unit = {
    val funcTemplateNameT = TemplataCompiler.getFunctionTemplate(abstractFunc.fullName)

    val funcT =
      vassertOne(
        hinputs.functions.filter(func => {
          TemplataCompiler.getFunctionTemplate(func.header.fullName) == funcTemplateNameT
        }))

    val abstractFuncMonomorphizer =
      new Monomorphizer(
        opts,
        interner,
        keywords,
        hinputs,
        monouts,
        funcTemplateNameT,
        abstractFunc.fullName,
        abstractFunc.fullName.last.templateArgs.toVector.zipWithIndex.map({ case (templateArg, index) =>
          funcTemplateNameT.addStep(interner.intern(PlaceholderNameT(interner.intern(PlaceholderTemplateNameT(index))))) -> templateArg
        }).toMap,
        DenizenBoundToDenizenCallerBoundArg(
          assembleCalleeDenizenFunctionBounds(
            funcT.runeToFuncBound, instantiationBoundArgs.runeToFunctionBoundArg),
          assembleCalleeDenizenImplBounds(
            funcT.runeToImplBound, instantiationBoundArgs.runeToImplBoundArg)))

    vassert(!monouts.abstractFuncToMonomorphizerAndSuppliedPrototypes.contains(abstractFunc.fullName))
    monouts.abstractFuncToMonomorphizerAndSuppliedPrototypes.put(abstractFunc.fullName, (abstractFuncMonomorphizer, instantiationBoundArgs))

    val abstractFuncs = vassertSome(monouts.interfaceToAbstractFuncToVirtualIndex.get(interfaceFullName))
    vassert(!abstractFuncs.contains(abstractFunc))
    abstractFuncs.put(abstractFunc, virtualIndex)

    vassertSome(monouts.interfaceToImpls.get(interfaceFullName)).foreach(impl => {
      translateOverride(opts, interner, keywords, hinputs, monouts, impl, abstractFunc)
    })
  }

  def translateOverride(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: Hinputs,
    monouts: MonomorphizedOutputs,
    implFullName: FullNameT[IImplNameT],
    abstractFuncPrototype: PrototypeT):
  Unit = {
    //    val superInterfaceFullName: FullNameT[IInterfaceNameT],

    val implTemplateFullName = TemplataCompiler.getImplTemplate(implFullName)
    val implDefinitionT =
      vassertOne(
        hinputs.interfaceToSubCitizenToEdge
          .flatMap(_._2.values)
          .filter(edge => TemplataCompiler.getImplTemplate(edge.edgeFullName) == implTemplateFullName))

    val superInterfaceTemplateFullName = TemplataCompiler.getInterfaceTemplate(implDefinitionT.superInterface)
    val superInterfaceDefinitionT = hinputs.lookupInterfaceByTemplateFullName(superInterfaceTemplateFullName)
    val superInterfacePlaceholderedName = superInterfaceDefinitionT.instantiatedInterface
    val subCitizenTemplateFullName = TemplataCompiler.getCitizenTemplate(implDefinitionT.subCitizen.fullName)
    val subCitizenDefinitionT = hinputs.lookupCitizenByTemplateFullName(subCitizenTemplateFullName)
    val subCitizenPlaceholderedName = subCitizenDefinitionT.instantiatedCitizen

    val abstractFuncTemplateName = TemplataCompiler.getFunctionTemplate(abstractFuncPrototype.fullName)
    val abstractFuncPlaceholderedNameT =
      vassertSome(
        hinputs.functions
          .find(func => TemplataCompiler.getFunctionTemplate(func.header.fullName) == abstractFuncTemplateName))
        .header.fullName

    val edgeT =
      vassertSome(
        vassertSome(hinputs.interfaceToSubCitizenToEdge.get(superInterfacePlaceholderedName.fullName))
          .get(subCitizenPlaceholderedName.fullName))

    val OverrideT(
    dispatcherFullNameT,
    implPlaceholderToDispatcherPlaceholder,
    implPlaceholderToCasePlaceholder,
    implSubCitizenReachableBoundsToCaseSubCitizenReachableBounds,
    dispatcherRuneToFunctionBound,
    dispatcherRuneToImplBound,
    dispatcherCaseFullNameT,
    overridePrototypeT) =
      vassertSome(edgeT.abstractFuncToOverrideFunc.get(abstractFuncPlaceholderedNameT))

    val (abstractFunctionMonomorphizer, abstractFunctionRuneToCallerSuppliedInstantiationBoundArgs) =
      vassertSome(monouts.abstractFuncToMonomorphizerAndSuppliedPrototypes.get(abstractFuncPrototype.fullName))
    // The dispatcher was originally made from the abstract function, they have the same runes.
    val dispatcherRuneToCallerSuppliedPrototype = abstractFunctionRuneToCallerSuppliedInstantiationBoundArgs.runeToFunctionBoundArg
    val dispatcherRuneToCallerSuppliedImpl = abstractFunctionRuneToCallerSuppliedInstantiationBoundArgs.runeToImplBoundArg

    val edgeMonomorphizer =
      vassertSome(monouts.impls.get(implFullName))._4

    val dispatcherPlaceholderFullNameToSuppliedTemplata =
      dispatcherFullNameT.last.templateArgs
        .map(dispatcherPlaceholderTemplata => {// FullNameT(_, _, PlaceholderNameT(PlaceholderTemplateNameT(index))) =>
          val dispatcherPlaceholderFullName =
            TemplataCompiler.getPlaceholderTemplataFullName(dispatcherPlaceholderTemplata)
          val implPlaceholder =
            vassertSome(
              implPlaceholderToDispatcherPlaceholder.find(_._2 == dispatcherPlaceholderTemplata))._1
          val FullNameT(_, _, PlaceholderNameT(PlaceholderTemplateNameT(index))) = implPlaceholder
          val templata = implFullName.last.templateArgs(index)
          dispatcherPlaceholderFullName -> templata
        })

    val dispatcherFunctionBoundToIncomingPrototype =
      assembleCalleeDenizenFunctionBounds(
        dispatcherRuneToFunctionBound,
        dispatcherRuneToCallerSuppliedPrototype)
    val dispatcherImplBoundToIncomingImpl =
      assembleCalleeDenizenImplBounds(
        dispatcherRuneToImplBound,
        dispatcherRuneToCallerSuppliedImpl)
    val dispatcherMonomorphizer =
      new Monomorphizer(
        opts,
        interner,
        keywords,
        hinputs,
        monouts,
        TemplataCompiler.getFunctionTemplate(dispatcherFullNameT),
        dispatcherFullNameT,
        dispatcherPlaceholderFullNameToSuppliedTemplata.toMap,
        DenizenBoundToDenizenCallerBoundArg(
          dispatcherFunctionBoundToIncomingPrototype,
          dispatcherImplBoundToIncomingImpl))

    // These are the placeholders' templatas that should be visible from inside the dispatcher case.
    // These will be used to call the override properly.
    val placeholderFullNameToTemplata =
    dispatcherPlaceholderFullNameToSuppliedTemplata ++
      dispatcherCaseFullNameT.last.independentImplTemplateArgs.zipWithIndex.map({
        case (casePlaceholderTemplata, index) => {
          val casePlaceholderFullName =
            TemplataCompiler.getPlaceholderTemplataFullName(casePlaceholderTemplata)
          val implPlaceholder =
            vassertSome(
              implPlaceholderToCasePlaceholder.find(_._2 == casePlaceholderTemplata))._1
          val FullNameT(_, _, PlaceholderNameT(PlaceholderTemplateNameT(index))) = implPlaceholder
          val templata = implFullName.last.templateArgs(index)
          casePlaceholderFullName -> templata
          //          // templata is the value from the edge that's doing the overriding. It comes from the impl.
          //          val dispatcherCasePlaceholderFullName =
          //            dispatcherCaseFullNameT.addStep(interner.intern(PlaceholderNameT(interner.intern(PlaceholderTemplateNameT(index)))))
          //          val templataGivenToCaseFromImpl =
          //            edgeMonomorphizer.translateTemplata(templataGivenToCaseFromImplT)
          //          dispatcherCasePlaceholderFullName -> templataGivenToCaseFromImpl
        }
      })

    val edgeDenizenBoundToDenizenCallerSuppliedThing =
      edgeMonomorphizer.denizenBoundToDenizenCallerSuppliedThing


    // See TIBANFC, we need this map to bring in the impl bound args for the override dispatcher
    // case.
    val caseFunctionBoundToIncomingPrototype =
      dispatcherFunctionBoundToIncomingPrototype ++
        // We're using the supplied prototypes from the impl, but we need to rephrase the keys
        // of this map to be in terms of the override dispatcher function's placeholders, not the
        // original impl's placeholders.
        edgeDenizenBoundToDenizenCallerSuppliedThing.funcBoundToCallerSuppliedBoundArgFunc
          .map({ case (implPlaceholderedBound, implPlaceholderedBoundArg) =>
            vassertSome(implSubCitizenReachableBoundsToCaseSubCitizenReachableBounds.get(implPlaceholderedBound)) -> implPlaceholderedBoundArg
          })
    val caseImplBoundToIncomingImpl =
      dispatcherImplBoundToIncomingImpl ++
        edgeDenizenBoundToDenizenCallerSuppliedThing.implBoundToCallerSuppliedBoundArgImpl
          .map({ case (key, value) => {
            vimpl()
          }})

    val caseDenizenBoundToDenizenCallerSuppliedThing =
      DenizenBoundToDenizenCallerBoundArg(
        caseFunctionBoundToIncomingPrototype,
        caseImplBoundToIncomingImpl)

    // we should pull in all the impl's placeholders
    // override should have info: what extra args there are, and what index from the impl full name


    //    val caseRuneToSuppliedFunction =
    //      abstractFunctionRuneToSuppliedFunction



    //    val denizenBoundToDenizenCallerSuppliedThingFromParams =
    //      paramsT.zip(argsM).flatMap({ case (a, x) =>
    //        hoistBoundsFromParameter(hinputs, monouts, a, x)
    //      })
    //    val denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams =
    //      Vector(denizenBoundToDenizenCallerSuppliedThingFromDenizenItself) ++
    //        denizenBoundToDenizenCallerSuppliedThingFromParams
    //    val denizenBoundToDenizenCallerSuppliedThing =
    //      DenizenBoundToDenizenCallerSuppliedThing(
    //        denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
    //          .map(_.functionBoundToCallerSuppliedPrototype)
    //          .reduceOption(_ ++ _).getOrElse(Map()),
    //        denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
    //          .map(_.implBoundToCallerSuppliedImpl)
    //          .reduceOption(_ ++ _).getOrElse(Map()))


    val caseMonomorphizer =
      new Monomorphizer(
        opts,
        interner,
        keywords,
        hinputs,
        monouts,
        dispatcherCaseFullNameT,
        dispatcherCaseFullNameT,
        placeholderFullNameToTemplata.toMap,
        caseDenizenBoundToDenizenCallerSuppliedThing)


    // right here we're calling it from the perspective of the abstract function
    // we need to call it from the perspective of the abstract dispatcher function's case.
    // we might need a sub-monomorphizer if that makes sense...

    // we need to make a monomorphizer that thinks in terms of impl overrides.

    val overridePrototype = caseMonomorphizer.translatePrototype(overridePrototypeT)

    val superInterfaceFullName = vassertSome(monouts.impls.get(implFullName))._2

    val overrride =
      OverrideT(
        dispatcherFullNameT, Vector(), Vector(), Map(), Map(), Map(), dispatcherCaseFullNameT, overridePrototype)
    monouts.addMethodToVTable(implFullName, superInterfaceFullName, abstractFuncPrototype, overrride)
  }

  def translateImpl(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: Hinputs,
    monouts: MonomorphizedOutputs,
    implFullName: FullNameT[IImplNameT],
    instantiationBoundsForUnsubstitutedImpl: InstantiationBoundArguments):
  Unit = {
    val implTemplateFullName = TemplataCompiler.getImplTemplate(implFullName)
    val implDefinition =
      vassertOne(
        hinputs.interfaceToSubCitizenToEdge
          .flatMap(_._2.values)
          .filter(edge => {
            TemplataCompiler.getImplTemplate(edge.edgeFullName) == implTemplateFullName
          }))


    val subCitizenT = implDefinition.subCitizen
    val subCitizenM =
      implFullName.last match {
        case ImplNameT(template, templateArgs, subCitizen) => subCitizen
        case AnonymousSubstructImplNameT(template, templateArgs, subCitizen) => subCitizen
        case other => vimpl(other)
      }

    val denizenBoundToDenizenCallerSuppliedThingFromDenizenItself =
      DenizenBoundToDenizenCallerBoundArg(
        assembleCalleeDenizenFunctionBounds(
          implDefinition.runeToFuncBound, instantiationBoundsForUnsubstitutedImpl.runeToFunctionBoundArg),
        assembleCalleeDenizenImplBounds(
          implDefinition.runeToImplBound, instantiationBoundsForUnsubstitutedImpl.runeToImplBoundArg))
    val denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams =
      Vector(denizenBoundToDenizenCallerSuppliedThingFromDenizenItself) ++
        hoistBoundsFromParameter(hinputs, monouts, subCitizenT, subCitizenM)

    val denizenBoundToDenizenCallerSuppliedThing =
      DenizenBoundToDenizenCallerBoundArg(
        denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
          .map(_.funcBoundToCallerSuppliedBoundArgFunc)
          .reduceOption(_ ++ _).getOrElse(Map()),
        denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
          .map(_.implBoundToCallerSuppliedBoundArgImpl)
          .reduceOption(_ ++ _).getOrElse(Map()))

    val monomorphizer =
      new Monomorphizer(
        opts,
        interner,
        keywords,
        hinputs,
        monouts,
        implTemplateFullName,
        implFullName,
        implFullName.last.templateArgs.toVector.zipWithIndex.map({ case (templateArg, index) =>
          implTemplateFullName.addStep(interner.intern(PlaceholderNameT(interner.intern(PlaceholderTemplateNameT(index))))) -> templateArg
        }).toMap,
        denizenBoundToDenizenCallerSuppliedThing)
    monomorphizer.translateImplDefinition(implFullName, implDefinition)


    //    val (subCitizenFullName, superInterfaceFullName, implBoundToImplCallerSuppliedPrototype) = vassertSome(monouts.impls.get(implFullName))
    //    val subCitizenTemplateFullName = TemplataCompiler.getCitizenTemplate(subCitizenFullName)
    //    val subCitizenDefinition = hinputs.lookupCitizenByTemplateFullName(subCitizenTemplateFullName)
    //    val subCitizenPlaceholderedName = subCitizenDefinition.instantiatedCitizen
    //    val superInterfaceTemplateFullName = TemplataCompiler.getInterfaceTemplate(superInterfaceFullName)
    //    val superInterfaceDefinition = hinputs.lookupInterfaceByTemplateFullName(superInterfaceTemplateFullName)
    //    val superInterfacePlaceholderedName = superInterfaceDefinition.instantiatedInterface

    //    val abstractFuncToBounds = vassertSome(monouts.interfaceToAbstractFuncToBounds.get(superInterfaceFullName))
    //    abstractFuncToBounds.foreach({ case (abstractFunc, _) =>
    //      val edge =
    //        vassertSome(
    //          vassertSome(hinputs.interfaceToSubCitizenToEdge.get(superInterfacePlaceholderedName.fullName))
    //            .get(subCitizenPlaceholderedName.fullName))
    //      val abstractFuncTemplateName = TemplataCompiler.getFunctionTemplate(abstractFunc.fullName)
    //
    //      val overridePrototype =
    //        vassertSome(edge.abstractFuncTemplateToOverrideFunc.get(abstractFuncTemplateName))
    //
    //      val funcT =
    //        DenizenMonomorphizer.translateFunction(
    //          opts, interner, keywords, hinputs, monouts, overridePrototype.fullName,
    //          translateBoundArgsForCallee(
    //            hinputs.getInstantiationBounds(overridePrototype.fullName)))
    //
    //      monouts.addMethodToVTable(implFullName, superInterfaceFullName, abstractFunc, funcT)
    //    })

  }

  def translateFunction(
    opts: GlobalOptions,
    interner: Interner,
    keywords: Keywords,
    hinputs: Hinputs,
    monouts: MonomorphizedOutputs,
    desiredPrototype: PrototypeT,
    suppliedBoundArgs: InstantiationBoundArguments,
    // This is only Some if this is a lambda. This will contain the prototypes supplied to the top level denizen by its
    // own caller, see LCNBAFA.
    maybeDenizenBoundToDenizenCallerSuppliedThing: Option[DenizenBoundToDenizenCallerBoundArg]):
  FunctionT = {
    val funcTemplateNameT = TemplataCompiler.getFunctionTemplate(desiredPrototype.fullName)

    val desiredFuncSuperTemplateName = TemplataCompiler.getSuperTemplate(desiredPrototype.fullName)
    val funcT =
      vassertOne(
        hinputs.functions
          .filter(funcT => TemplataCompiler.getSuperTemplate(funcT.header.fullName) == desiredFuncSuperTemplateName))


    val denizenBoundToDenizenCallerSuppliedThingFromDenizenItself =
      maybeDenizenBoundToDenizenCallerSuppliedThing.getOrElse({
        DenizenBoundToDenizenCallerBoundArg(
          // This is a top level denizen, and someone's calling it. Assemble the bounds!
          assembleCalleeDenizenFunctionBounds(funcT.runeToFuncBound, suppliedBoundArgs.runeToFunctionBoundArg),
          // This is a top level denizen, and someone's calling it. Assemble the bounds!
          assembleCalleeDenizenImplBounds(funcT.runeToImplBound, suppliedBoundArgs.runeToImplBoundArg))
      })
    val argsM = desiredPrototype.fullName.last.parameters.map(_.kind)
    val paramsT = funcT.header.params.map(_.tyype.kind)
    val denizenBoundToDenizenCallerSuppliedThingFromParams =
      paramsT.zip(argsM).flatMap({ case (a, x) =>
        hoistBoundsFromParameter(hinputs, monouts, a, x)
      })

    val denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams =
      Vector(denizenBoundToDenizenCallerSuppliedThingFromDenizenItself) ++
        denizenBoundToDenizenCallerSuppliedThingFromParams

    val denizenBoundToDenizenCallerSuppliedThing =
      DenizenBoundToDenizenCallerBoundArg(
        denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
          .map(_.funcBoundToCallerSuppliedBoundArgFunc)
          .reduceOption(_ ++ _).getOrElse(Map()),
        denizenBoundToDenizenCallerSuppliedThingFromDenizenItselfAndParams
          .map(_.implBoundToCallerSuppliedBoundArgImpl)
          .reduceOption(_ ++ _).getOrElse(Map()))


    val topLevelDenizenFullName =
      getTopLevelDenizenFullName(desiredPrototype.fullName)
    val topLevelDenizenTemplateFullName =
      TemplataCompiler.getTemplate(topLevelDenizenFullName)
    // One would imagine we'd get structFullName.last.templateArgs here, because that's the struct
    // we're about to monomorphize. However, only the top level denizen has placeholders, see LHPCTLD.
    val topLevelDenizenPlaceholderIndexToTemplata =
    topLevelDenizenFullName.last.templateArgs


    val monomorphizer =
      new Monomorphizer(
        opts,
        interner,
        keywords,
        hinputs,
        monouts,
        funcTemplateNameT,
        desiredPrototype.fullName,
        topLevelDenizenPlaceholderIndexToTemplata.toVector.zipWithIndex.map({ case (templateArg, index) =>
          val placeholderName =
            topLevelDenizenTemplateFullName
              .addStep(interner.intern(PlaceholderNameT(interner.intern(PlaceholderTemplateNameT(index)))))
          placeholderName -> templateArg
        }).toMap,
        denizenBoundToDenizenCallerSuppliedThing)

    desiredPrototype.fullName match {
      case FullNameT(_,Vector(),FunctionNameT(FunctionTemplateNameT(StrI("as"),_),_, _)) => {
        vpass()
      }
      case _ => false
    }

    val monomorphizedFuncT = monomorphizer.translateFunction(funcT)

    vassert(desiredPrototype.returnType == monomorphizedFuncT.header.returnType)

    monomorphizedFuncT
  }

  // This isn't just for parameters, it's for impl subcitizens, and someday for cases too.
  // See NBIFP
  private def hoistBoundsFromParameter(
    hinputs: Hinputs,
    monouts: MonomorphizedOutputs,
    paramT: KindT,
    paramM: KindT):
  Option[DenizenBoundToDenizenCallerBoundArg] = {
    (paramT, paramM) match {
      case (StructTT(structFullNameT), StructTT(structFullNameM)) => {
        val calleeRuneToBoundArgT = hinputs.getInstantiationBoundArgs(structFullNameT)
        val (_, structDenizenBoundToDenizenCallerSuppliedThing) = vassertSome(monouts.startedStructs.get(structFullNameM))
        val structT = findStruct(hinputs, structFullNameT)
        val denizenBoundToDenizenCallerSuppliedThing =
          hoistBoundsFromParameterInner(
            structDenizenBoundToDenizenCallerSuppliedThing, calleeRuneToBoundArgT, structT.runeToFunctionBound, structT.runeToImplBound)
        Some(denizenBoundToDenizenCallerSuppliedThing)
      }
      case (InterfaceTT(interfaceFullNameT), InterfaceTT(interfaceFullNameM)) => {
        val calleeRuneToBoundArgT = hinputs.getInstantiationBoundArgs(interfaceFullNameT)
        val (_, interfaceDenizenBoundToDenizenCallerSuppliedThing) = vassertSome(monouts.startedInterfaces.get(interfaceFullNameM))
        val interfaceT = findInterface(hinputs, interfaceFullNameT)
        val denizenBoundToDenizenCallerSuppliedThing =
          hoistBoundsFromParameterInner(
            interfaceDenizenBoundToDenizenCallerSuppliedThing, calleeRuneToBoundArgT, interfaceT.runeToFunctionBound, interfaceT.runeToImplBound)
        Some(denizenBoundToDenizenCallerSuppliedThing)
      }
      case _ => None
    }
  }

  // See NBIFP
  private def hoistBoundsFromParameterInner(
    parameterDenizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArg,
    calleeRuneToBoundArgT: InstantiationBoundArguments,
    calleeRuneToCalleeFunctionBoundT: Map[IRuneS, FullNameT[FunctionBoundNameT]],
    calleeRuneToCalleeImplBoundT: Map[IRuneS, FullNameT[ImplBoundNameT]]):
  DenizenBoundToDenizenCallerBoundArg = {
    val calleeFunctionBoundTToBoundArgM = parameterDenizenBoundToDenizenCallerSuppliedThing.funcBoundToCallerSuppliedBoundArgFunc
    val implBoundTToBoundArgM = parameterDenizenBoundToDenizenCallerSuppliedThing.implBoundToCallerSuppliedBoundArgImpl

    val callerSuppliedBoundToInstantiatedFunction =
      calleeRuneToCalleeFunctionBoundT.map({ case (calleeRune, calleeBoundT) =>
        // We don't care about the callee bound, we only care about what we're sending in to it.
        val (_) = calleeBoundT

        // This is the prototype the caller is sending in to the callee to satisfy its bounds.
        val boundArgT = vassertSome(calleeRuneToBoundArgT.runeToFunctionBoundArg.get(calleeRune))
        boundArgT.fullName match {
          case FullNameT(packageCoord, initSteps, last@FunctionBoundNameT(_, _, _)) => {
            // The bound arg is also the same thing as the caller bound.
            val callerBoundT = FullNameT(packageCoord, initSteps, last)

            // The bound arg we're sending in is actually one of our (the caller) own bounds.
            //
            // "But wait, we didn't specify any bounds."
            // This is actually a bound that was implicitly added from NBIFP.
            //
            // We're going to pull this in as our own bound.
            val instantiatedPrototype = vassertSome(calleeFunctionBoundTToBoundArgM.get(calleeBoundT))
            Some(callerBoundT -> instantiatedPrototype)
          }
          case _ => None
        }
      }).flatten.toMap

    val callerSuppliedBoundToInstantiatedImpl =
      calleeRuneToCalleeImplBoundT.map({
        case (calleeRune, calleeBoundT) =>
          // We don't care about the callee bound, we only care about what we're sending in to it.
          val (_) = calleeBoundT

          // This is the prototype the caller is sending in to the callee to satisfy its bounds.
          val boundArgT = vassertSome(calleeRuneToBoundArgT.runeToImplBoundArg.get(calleeRune))
          boundArgT match {
            case FullNameT(packageCoord, initSteps, last@ImplBoundNameT(_, _)) => {
              val boundT = FullNameT(packageCoord, initSteps, last)
              // The bound arg we're sending in is actually one of our (the caller) own bounds.
              //
              // "But wait, we didn't specify any bounds."
              // This is actually a bound that was implicitly added from NBIFP.
              //
              // We're going to pull this in as our own bound.
              val instantiatedPrototype = vassertSome(implBoundTToBoundArgM.get(boundT))
              Some(boundT -> instantiatedPrototype)
            }
            case _ => None
          }
      }).flatten.toMap

    val denizenBoundToDenizenCallerSuppliedThing =
      DenizenBoundToDenizenCallerBoundArg(
        callerSuppliedBoundToInstantiatedFunction,
        callerSuppliedBoundToInstantiatedImpl)
    denizenBoundToDenizenCallerSuppliedThing
  }
}

class Monomorphizer(
  opts: GlobalOptions,
  interner: Interner,
  keywords: Keywords,
  hinputs: Hinputs,
  monouts: MonomorphizedOutputs,
  denizenTemplateName: FullNameT[ITemplateNameT],
  denizenName: FullNameT[IInstantiationNameT],
  // This might be the top level denizen and not necessarily *this* denizen, see LHPCTLD.
  placeholderFullNameToTemplata: Map[FullNameT[PlaceholderNameT], ITemplata[ITemplataType]],
  val denizenBoundToDenizenCallerSuppliedThing: DenizenBoundToDenizenCallerBoundArg) {
  //  selfFunctionBoundToRuneUnsubstituted: Map[PrototypeT, IRuneS],
  //  denizenRuneToDenizenCallerPrototype: Map[IRuneS, PrototypeT]) {

  // This is just here to get scala to include these fields so i can see them in the debugger
  vassert(TemplataCompiler.getTemplate(denizenName) == denizenTemplateName)

  //  if (opts.sanityCheck) {
  //    denizenFunctionBoundToDenizenCallerSuppliedPrototype.foreach({
  //      case (denizenFunctionBound, denizenCallerSuppliedPrototype) => {
  //        vassert(Collector.all(denizenCallerSuppliedPrototype, { case PlaceholderTemplateNameT(_) => }).isEmpty)
  //      }
  //    })
  //  }

  def translateStructMember(member: IStructMemberT): IStructMemberT = {
    member match {
      case NormalStructMemberT(name, variability, tyype) => {
        NormalStructMemberT(
          translateVarName(name),
          variability,
          tyype match {
            case ReferenceMemberTypeT((unsubstitutedCoord)) => {
              ReferenceMemberTypeT(translateCoord(unsubstitutedCoord))
            }
            case AddressMemberTypeT((unsubstitutedCoord)) => {
              AddressMemberTypeT(translateCoord(unsubstitutedCoord))
            }
          })
      }
      case VariadicStructMemberT(name, tyype) => {
        vimpl()
      }
    }
  }

  // This is run at the call site, from the caller's perspective
  def translatePrototype(
    desiredPrototypeUnsubstituted: PrototypeT):
  PrototypeT = {
    val PrototypeT(desiredPrototypeFullNameUnsubstituted, desiredPrototypeReturnTypeUnsubstituted) = desiredPrototypeUnsubstituted

    val runeToBoundArgsForCall =
      translateBoundArgsForCallee(
        hinputs.getInstantiationBoundArgs(desiredPrototypeUnsubstituted.fullName))

    val desiredPrototype =
      PrototypeT(
        translateFunctionFullName(desiredPrototypeFullNameUnsubstituted),
        translateCoord(desiredPrototypeReturnTypeUnsubstituted))

    desiredPrototypeUnsubstituted.fullName match {
      case FullNameT(packageCoord, initSteps, name @ FunctionBoundNameT(_, _, _)) => {
        val funcBoundName = FullNameT(packageCoord, initSteps, name)
        val result = vassertSome(denizenBoundToDenizenCallerSuppliedThing.funcBoundToCallerSuppliedBoundArgFunc.get(funcBoundName))
        //        if (opts.sanityCheck) {
        //          vassert(Collector.all(result, { case PlaceholderTemplateNameT(_) => }).isEmpty)
        //        }
        result
      }
      case FullNameT(_, _, ExternFunctionNameT(_, _)) => {
        if (opts.sanityCheck) {
          vassert(Collector.all(desiredPrototype, { case PlaceholderTemplateNameT(_) => }).isEmpty)
        }
        desiredPrototype
      }
      case FullNameT(_, _, last) => {
        last match {
          case LambdaCallFunctionNameT(_, _, _) => {
            vassert(
              desiredPrototype.fullName.steps.slice(0, desiredPrototype.fullName.steps.length - 2) ==
                denizenName.steps)
            vcurious(desiredPrototype.fullName.steps.startsWith(denizenName.steps))
          }
          case _ =>
        }

        monouts.newFunctions.enqueue(
          (
            desiredPrototype,
            runeToBoundArgsForCall,
            // We need to supply our bounds to our lambdas, see LCCPGB and LCNBAFA.
            if (desiredPrototype.fullName.steps.startsWith(denizenName.steps)) {
              Some(denizenBoundToDenizenCallerSuppliedThing)
            } else {
              None
            }))
        desiredPrototype
      }
    }
  }

  private def translateBoundArgsForCallee(
    // This contains a map from rune to a prototype, specifically the prototype that we
    // (the *template* caller) is supplying to the *template* callee. This prototype might
    // be a placeholder, phrased in terms of our (the *template* caller's) placeholders
    instantiationBoundArgsForCallUnsubstituted: InstantiationBoundArguments):
  InstantiationBoundArguments = {
    val runeToSuppliedPrototypeForCallUnsubstituted =
      instantiationBoundArgsForCallUnsubstituted.runeToFunctionBoundArg
    val runeToSuppliedPrototypeForCall =
    // For any that are placeholders themselves, let's translate those into actual prototypes.
      runeToSuppliedPrototypeForCallUnsubstituted.map({ case (rune, suppliedPrototypeUnsubstituted) =>
        rune ->
          (suppliedPrototypeUnsubstituted.fullName match {
            case FullNameT(packageCoord, initSteps, name @ FunctionBoundNameT(_, _, _)) => {
              vassertSome(
                denizenBoundToDenizenCallerSuppliedThing.funcBoundToCallerSuppliedBoundArgFunc.get(
                  FullNameT(packageCoord, initSteps, name)))
            }
            case _ => {
              translatePrototype(suppliedPrototypeUnsubstituted)
            }
          })
      })
    // And now we have a map from the callee's rune to the *instantiated* callee's prototypes.

    val runeToSuppliedImplForCallUnsubstituted =
      instantiationBoundArgsForCallUnsubstituted.runeToImplBoundArg
    val runeToSuppliedImplForCall =
    // For any that are placeholders themselves, let's translate those into actual prototypes.
      runeToSuppliedImplForCallUnsubstituted.map({ case (rune, suppliedImplUnsubstituted) =>
        rune ->
          (suppliedImplUnsubstituted match {
            case FullNameT(packageCoord, initSteps, name @ ImplBoundNameT(_, _)) => {
              vassertSome(
                denizenBoundToDenizenCallerSuppliedThing.implBoundToCallerSuppliedBoundArgImpl.get(
                  FullNameT(packageCoord, initSteps, name)))
            }
            case _ => {
              // Not sure about these three lines, but they seem to work.
              val runeToBoundArgsForCall =
                translateBoundArgsForCallee(
                  hinputs.getInstantiationBoundArgs(suppliedImplUnsubstituted))
              translateImplFullName(suppliedImplUnsubstituted, runeToBoundArgsForCall)
            }
          })
      })
    // And now we have a map from the callee's rune to the *instantiated* callee's impls.

    InstantiationBoundArguments(runeToSuppliedPrototypeForCall, runeToSuppliedImplForCall)
  }

  def translateStructDefinition(
    newFullName: FullNameT[IStructNameT],
    structDefT: StructDefinitionT):
  Unit = {
    val StructDefinitionT(templateName, instantiatedCitizen, attributes, weakable, mutabilityT, members, isClosure, _, _) = structDefT

    if (opts.sanityCheck) {
      vassert(Collector.all(newFullName, { case PlaceholderNameT(_) => }).isEmpty)
    }

    val mutability = expectMutabilityTemplata(translateTemplata(mutabilityT)).mutability

    if (monouts.startedStructs.contains(newFullName)) {
      return
    }
    monouts.startedStructs.put(newFullName, (mutability, this.denizenBoundToDenizenCallerSuppliedThing))

    val result =
      StructDefinitionT(
        templateName,
        interner.intern(StructTT(newFullName)),
        attributes,
        weakable,
        MutabilityTemplata(mutability),
        members.map(translateStructMember),
        isClosure,
        Map(),
        Map())

    vassert(result.instantiatedCitizen.fullName == newFullName)

    monouts.structs.put(result.instantiatedCitizen.fullName, result)

    if (opts.sanityCheck) {
      vassert(Collector.all(result.instantiatedCitizen, { case PlaceholderNameT(_) => }).isEmpty)
      vassert(Collector.all(result.members, { case PlaceholderNameT(_) => }).isEmpty)
    }
    result
  }

  def translateInterfaceDefinition(
    newFullName: FullNameT[IInterfaceNameT],
    interfaceDefT: InterfaceDefinitionT):
  Unit = {
    val InterfaceDefinitionT(templateName, instantiatedCitizen, ref, attributes, weakable, mutabilityT, _, _, internalMethods) = interfaceDefT

    val mutability = expectMutabilityTemplata(translateTemplata(mutabilityT)).mutability

    if (monouts.startedInterfaces.contains(newFullName)) {
      return
    }
    monouts.startedInterfaces.put(newFullName, (mutability, this.denizenBoundToDenizenCallerSuppliedThing))

    val newInterfaceTT = interner.intern(InterfaceTT(newFullName))

    val result =
      InterfaceDefinitionT(
        templateName,
        newInterfaceTT,
        newInterfaceTT,
        attributes,
        weakable,
        MutabilityTemplata(mutability),
        Map(),
        Map(),
        Vector())

    if (opts.sanityCheck) {
      vassert(Collector.all(result, { case PlaceholderNameT(_) => }).isEmpty)
    }

    vassert(!monouts.interfaceToImplToAbstractPrototypeToOverride.contains(newFullName))
    monouts.interfaceToImplToAbstractPrototypeToOverride.put(newFullName, mutable.HashMap())

    monouts.interfacesWithoutMethods.put(newFullName, result)

    vassert(!monouts.interfaceToAbstractFuncToVirtualIndex.contains(newFullName))
    monouts.interfaceToAbstractFuncToVirtualIndex.put(newFullName, mutable.HashMap())

    vassert(!monouts.interfaceToImpls.contains(newFullName))
    monouts.interfaceToImpls.put(newFullName, mutable.HashSet())

    vassert(result.instantiatedCitizen.fullName == newFullName)
  }

  def translateFunctionHeader(header: FunctionHeaderT): FunctionHeaderT = {
    val FunctionHeaderT(fullName, attributes, params, returnType, maybeOriginFunctionTemplata) = header

    val newFullName = translateFunctionFullName(fullName)

    val result =
      FunctionHeaderT(
        newFullName,
        attributes,
        params.map(translateParameter),
        translateCoord(returnType),
        maybeOriginFunctionTemplata)

    //    if (opts.sanityCheck) {
    //      vassert(Collector.all(result.fullName, { case PlaceholderNameT(_) => }).isEmpty)
    //      vassert(Collector.all(result.attributes, { case PlaceholderNameT(_) => }).isEmpty)
    //      vassert(Collector.all(result.params, { case PlaceholderNameT(_) => }).isEmpty)
    //      vassert(Collector.all(result.returnType, { case PlaceholderNameT(_) => }).isEmpty)
    //    }

    result
  }

  def translateFunction(
    functionT: FunctionT):
  FunctionT = {
    val FunctionT(headerT, _, _, bodyT) = functionT

    val FunctionHeaderT(fullName, attributes, params, returnType, maybeOriginFunctionTemplata) = headerT

    val newFullName = translateFunctionFullName(fullName)

    monouts.functions.get(newFullName) match {
      case Some(func) => return func
      case None =>
    }

    val newHeader = translateFunctionHeader(headerT)

    val result = FunctionT(newHeader, Map(), Map(), translateRefExpr(bodyT))
    monouts.functions.put(result.header.fullName, result)
    result
  }

  def translateLocalVariable(
    variable: ILocalVariableT):
  ILocalVariableT = {
    variable match {
      case r @ ReferenceLocalVariableT(_, _, _) => translateReferenceLocalVariable(r)
      case AddressibleLocalVariableT(id, variability, reference) => {
        AddressibleLocalVariableT(
          translateVarFullName(id),
          variability,
          translateCoord(reference))
      }
    }
  }

  def translateReferenceLocalVariable(
    variable: ReferenceLocalVariableT):
  ReferenceLocalVariableT = {
    val ReferenceLocalVariableT(id, variability, reference) = variable
    ReferenceLocalVariableT(
      translateVarFullName(id),
      variability,
      translateCoord(reference))
  }

  def translateAddrExpr(
    expr: AddressExpressionTE):
  AddressExpressionTE = {
    expr match {
      case LocalLookupTE(range, localVariable) => {
        LocalLookupTE(range, translateLocalVariable(localVariable))
      }
      case ReferenceMemberLookupTE(range, structExpr, memberName, memberReference, variability) => {
        ReferenceMemberLookupTE(
          range,
          translateRefExpr(structExpr),
          translateVarFullName(memberName),
          translateCoord(memberReference),
          variability)
      }
      case StaticSizedArrayLookupTE(range, arrayExpr, arrayType, indexExpr, variability) => {
        StaticSizedArrayLookupTE(
          range,
          translateRefExpr(arrayExpr),
          translateStaticSizedArray(arrayType),
          translateRefExpr(indexExpr),
          variability)
      }
      case AddressMemberLookupTE(range, structExpr, memberName, resultType2, variability) => {
        AddressMemberLookupTE(
          range,
          translateRefExpr(structExpr),
          translateVarFullName(memberName),
          translateCoord(resultType2),
          variability)
      }
      case RuntimeSizedArrayLookupTE(range, arrayExpr, arrayType, indexExpr, variability) => {
        RuntimeSizedArrayLookupTE(
          range,
          translateRefExpr(arrayExpr),
          translateRuntimeSizedArray(arrayType),
          translateRefExpr(indexExpr),
          variability)
      }
      case other => vimpl(other)
    }
  }

  def translateExpr(
    expr: ExpressionT):
  ExpressionT = {
    expr match {
      case r : ReferenceExpressionTE => translateRefExpr(r)
      case a : AddressExpressionTE => translateAddrExpr(a)
    }
  }

  def translateRefExpr(
    expr: ReferenceExpressionTE):
  ReferenceExpressionTE = {
    val resultRefExpr =
      expr match {
        case LetNormalTE(variable, inner) => LetNormalTE(translateLocalVariable(variable), translateRefExpr(inner))
        case BlockTE(inner) => BlockTE(translateRefExpr(inner))
        case ReturnTE(inner) => ReturnTE(translateRefExpr(inner))
        case ConsecutorTE(inners) => ConsecutorTE(inners.map(translateRefExpr))
        case ConstantIntTE(value, bits) => {
          ConstantIntTE(ITemplata.expectIntegerTemplata(translateTemplata(value)), bits)
        }
        case ConstantStrTE(value) => ConstantStrTE(value)
        case ConstantBoolTE(value) => ConstantBoolTE(value)
        case ConstantFloatTE(value) => ConstantFloatTE(value)
        case UnletTE(variable) => UnletTE(translateLocalVariable(variable))
        case DiscardTE(expr) => DiscardTE(translateRefExpr(expr))
        case VoidLiteralTE() => VoidLiteralTE()
        case FunctionCallTE(prototypeT, args) => {
          val prototype = translatePrototype(prototypeT)
          FunctionCallTE(
            prototype,
            args.map(translateRefExpr))
        }
        case InterfaceFunctionCallTE(superFunctionPrototypeT, virtualParamIndex, resultReference, args) => {
          val superFunctionPrototype = translatePrototype(superFunctionPrototypeT)
          val result =
            InterfaceFunctionCallTE(
              superFunctionPrototype,
              virtualParamIndex,
              translateCoord(resultReference),
              args.map(translateRefExpr))
          val interfaceFullName =
            superFunctionPrototype.paramTypes(virtualParamIndex).kind.expectInterface().fullName
          //        val interfaceFullName =
          //          translateInterfaceFullName(
          //            interfaceFullNameT,
          //            translateBoundArgsForCallee(
          //              hinputs.getInstantiationBounds(callee.toPrototype.fullName)))

          val instantiationBoundArgs =
            translateBoundArgsForCallee(
              // but this is literally calling itself from where its defined
              // perhaps we want the thing that originally called
              hinputs.getInstantiationBoundArgs(superFunctionPrototypeT.fullName))

          monouts.newAbstractFuncs.enqueue(
            (superFunctionPrototype, virtualParamIndex, interfaceFullName, instantiationBoundArgs))

          result
        }
        case ArgLookupTE(paramIndex, reference) => ArgLookupTE(paramIndex, translateCoord(reference))
        case SoftLoadTE(originalInner, originalTargetOwnership) => {
          val inner = translateAddrExpr(originalInner)
          val targetOwnership =
            (originalTargetOwnership, inner.result.reference.ownership) match {
              case (a, b) if a == b => a
              case (BorrowT, ShareT) => ShareT
              case (BorrowT, WeakT) => WeakT
              case (BorrowT, OwnT) => BorrowT
              case (WeakT, ShareT) => ShareT
              case (WeakT, OwnT) => WeakT
              case (WeakT, BorrowT) => WeakT
              case other => vwat(other)
            }
          SoftLoadTE(inner, targetOwnership)
        }
        case ExternFunctionCallTE(prototype2, args) => {
          ExternFunctionCallTE(
            translatePrototype(prototype2),
            args.map(translateRefExpr))
        }
        case ConstructTE(structTT, resultReference, args) => {
          val coord = translateCoord(resultReference)

          //          val freePrototype = translatePrototype(freePrototypeT)
          //          // They might disagree on the ownership, and thats fine.
          //          // That free prototype is only going to take an owning or a share reference, and we'll only
          //          // use it if we have a shared reference so it's all good.
          //          vassert(coord.kind == vassertSome(freePrototype.fullName.last.parameters.headOption).kind)
          //          if (coord.ownership == ShareT) {
          //            monouts.immKindToDestructor.put(coord.kind, freePrototype)
          //          }

          ConstructTE(
            translateStruct(
              structTT,
              translateBoundArgsForCallee(
                hinputs.getInstantiationBoundArgs(structTT.fullName))),
            coord,
            args.map(translateExpr))
        }
        case DestroyTE(expr, structTT, destinationReferenceVariables) => {
          DestroyTE(
            translateRefExpr(expr),
            translateStruct(
              structTT,
              translateBoundArgsForCallee(
                hinputs.getInstantiationBoundArgs(structTT.fullName))),
            destinationReferenceVariables.map(translateReferenceLocalVariable))
        }
        case MutateTE(destinationExpr, sourceExpr) => {
          MutateTE(
            translateAddrExpr(destinationExpr),
            translateRefExpr(sourceExpr))
        }
        case u @ UpcastTE(innerExprUnsubstituted, targetSuperKind, untranslatedImplFullName) => {
          val implFullName =
            translateImplFullName(
              untranslatedImplFullName,
              translateBoundArgsForCallee(
                hinputs.getInstantiationBoundArgs(untranslatedImplFullName)))
          //          val freePrototype = translatePrototype(freePrototypeT)
          val coord = translateCoord(u.result.reference)
          // They might disagree on the ownership, and thats fine.
          // That free prototype is only going to take an owning or a share reference, and we'll only
          // use it if we have a shared reference so it's all good.
          //          vassert(coord.kind == vassertSome(freePrototype.fullName.last.parameters.headOption).kind)
          //          if (coord.ownership == ShareT) {
          //            monouts.immKindToDestructor.put(coord.kind, freePrototypeT)
          //          }

          UpcastTE(
            translateRefExpr(innerExprUnsubstituted),
            translateSuperKind(targetSuperKind),
            implFullName)//,
          //            freePrototype)
        }
        case IfTE(condition, thenCall, elseCall) => {
          IfTE(
            translateRefExpr(condition),
            translateRefExpr(thenCall),
            translateRefExpr(elseCall))
        }
        case IsSameInstanceTE(left, right) => {
          IsSameInstanceTE(
            translateRefExpr(left),
            translateRefExpr(right))
        }
        case StaticArrayFromValuesTE(elements, resultReference, arrayType) => {

          //          val freePrototype = translatePrototype(freePrototypeT)
          val coord = translateCoord(resultReference)
          // They might disagree on the ownership, and thats fine.
          // That free prototype is only going to take an owning or a share reference, and we'll only
          // use it if we have a shared reference so it's all good.
          //          vassert(coord.kind == vassertSome(freePrototype.fullName.last.parameters.headOption).kind)
          //          if (coord.ownership == ShareT) {
          //            monouts.immKindToDestructor.put(coord.kind, freePrototypeT)
          //          }

          StaticArrayFromValuesTE(
            elements.map(translateRefExpr),
            translateCoord(resultReference),
            arrayType)
        }
        case DeferTE(innerExpr, deferredExpr) => {
          DeferTE(
            translateRefExpr(innerExpr),
            translateRefExpr(deferredExpr))
        }
        case LetAndLendTE(variable, sourceExprT, targetOwnership) => {
          val sourceExpr = translateRefExpr(sourceExprT)

          val resultOwnership =
            (targetOwnership, sourceExpr.result.reference.ownership) match {
              case (OwnT, OwnT) => OwnT
              case (OwnT, BorrowT) => BorrowT
              case (BorrowT, OwnT) => BorrowT
              case (BorrowT, BorrowT) => BorrowT
              case (BorrowT, WeakT) => WeakT
              case (BorrowT, ShareT) => ShareT
              case (WeakT, OwnT) => WeakT
              case (WeakT, BorrowT) => WeakT
              case (WeakT, WeakT) => WeakT
              case (WeakT, ShareT) => ShareT
              case (ShareT, ShareT) => ShareT
              case (OwnT, ShareT) => ShareT
              case other => vwat(other)
            }

          LetAndLendTE(
            translateLocalVariable(variable),
            sourceExpr,
            resultOwnership)
        }
        case BorrowToWeakTE(innerExpr) => {
          BorrowToWeakTE(translateRefExpr(innerExpr))
        }
        case WhileTE(BlockTE(inner)) => {
          WhileTE(BlockTE(translateRefExpr(inner)))
        }
        case BreakTE() => BreakTE()
        case LockWeakTE(innerExpr, resultOptBorrowType, someConstructor, noneConstructor, someImplUntranslatedFullName, noneImplUntranslatedFullName) => {
          LockWeakTE(
            translateRefExpr(innerExpr),
            translateCoord(resultOptBorrowType),
            translatePrototype(someConstructor),
            translatePrototype(noneConstructor),
            translateImplFullName(
              someImplUntranslatedFullName,
              translateBoundArgsForCallee(
                hinputs.getInstantiationBoundArgs(someImplUntranslatedFullName))),
            translateImplFullName(
              noneImplUntranslatedFullName,
              translateBoundArgsForCallee(
                hinputs.getInstantiationBoundArgs(noneImplUntranslatedFullName))))
        }
        case DestroyStaticSizedArrayIntoFunctionTE(arrayExpr, arrayType, consumer, consumerMethod) => {
          DestroyStaticSizedArrayIntoFunctionTE(
            translateRefExpr(arrayExpr),
            translateStaticSizedArray(arrayType),
            translateRefExpr(consumer),
            translatePrototype(consumerMethod))
        }
        case NewImmRuntimeSizedArrayTE(arrayType, sizeExpr, generator, generatorMethod) => {
          //          val freePrototype = translatePrototype(freePrototypeT)

          val result =
            NewImmRuntimeSizedArrayTE(
              translateRuntimeSizedArray(arrayType),
              translateRefExpr(sizeExpr),
              translateRefExpr(generator),
              translatePrototype(generatorMethod))

          val coord = result.result.reference
          // They might disagree on the ownership, and thats fine.
          // That free prototype is only going to take an owning or a share reference, and we'll only
          // use it if we have a shared reference so it's all good.
          //          vassert(coord.kind == vassertSome(freePrototype.fullName.last.parameters.headOption).kind)
          //          if (coord.ownership == ShareT) {
          //            monouts.immKindToDestructor.put(coord.kind, freePrototype)
          //          }

          result
        }
        case StaticArrayFromCallableTE(arrayType, generator, generatorMethod) => {
          //          val freePrototype = translatePrototype(freePrototypeT)

          val result =
            StaticArrayFromCallableTE(
              translateStaticSizedArray(arrayType),
              translateRefExpr(generator),
              translatePrototype(generatorMethod))

          val coord = result.result.reference
          // They might disagree on the ownership, and thats fine.
          // That free prototype is only going to take an owning or a share reference, and we'll only
          // use it if we have a shared reference so it's all good.
          //          vassert(coord.kind == vassertSome(freePrototype.fullName.last.parameters.headOption).kind)
          //          if (coord.ownership == ShareT) {
          //            monouts.immKindToDestructor.put(coord.kind, freePrototype)
          //          }

          result
        }
        case RuntimeSizedArrayCapacityTE(arrayExpr) => {
          RuntimeSizedArrayCapacityTE(translateRefExpr(arrayExpr))
        }
        case PushRuntimeSizedArrayTE(arrayExpr, newElementExpr) => {
          PushRuntimeSizedArrayTE(
            translateRefExpr(arrayExpr),
            translateRefExpr(newElementExpr))
        }
        case PopRuntimeSizedArrayTE(arrayExpr) => {
          PopRuntimeSizedArrayTE(translateRefExpr(arrayExpr))
        }
        case ArrayLengthTE(arrayExpr) => {
          ArrayLengthTE(translateRefExpr(arrayExpr))
        }
        case DestroyImmRuntimeSizedArrayTE(arrayExpr, arrayType, consumer, consumerMethod) => {
          DestroyImmRuntimeSizedArrayTE(
            translateRefExpr(arrayExpr),
            translateRuntimeSizedArray(arrayType),
            translateRefExpr(consumer),
            translatePrototype(consumerMethod))
          //            translatePrototype(freePrototype))
        }
        case DestroyMutRuntimeSizedArrayTE(arrayExpr) => {
          DestroyMutRuntimeSizedArrayTE(translateRefExpr(arrayExpr))
        }
        case NewMutRuntimeSizedArrayTE(arrayType, capacityExpr) => {
          NewMutRuntimeSizedArrayTE(
            translateRuntimeSizedArray(arrayType),
            translateRefExpr(capacityExpr))
        }
        case TupleTE(elements, resultReference) => {
          TupleTE(
            elements.map(translateRefExpr),
            translateCoord(resultReference))
        }
        case AsSubtypeTE(sourceExpr, targetSubtype, resultResultType, okConstructor, errConstructor, implFullNameT, okResultImplFullNameT, errResultImplFullNameT) => {
          AsSubtypeTE(
            translateRefExpr(sourceExpr),
            translateCoord(targetSubtype),
            translateCoord(resultResultType),
            translatePrototype(okConstructor),
            translatePrototype(errConstructor),
            translateImplFullName(
              implFullNameT,
              translateBoundArgsForCallee(
                hinputs.getInstantiationBoundArgs(implFullNameT))),
            translateImplFullName(
              okResultImplFullNameT,
              translateBoundArgsForCallee(
                hinputs.getInstantiationBoundArgs(okResultImplFullNameT))),
            translateImplFullName(
              errResultImplFullNameT,
              translateBoundArgsForCallee(
                hinputs.getInstantiationBoundArgs(errResultImplFullNameT))))
        }
        case other => vimpl(other)
      }
    //    if (opts.sanityCheck) {
    //      vassert(Collector.all(resultRefExpr, { case PlaceholderNameT(_) => }).isEmpty)
    //    }
    resultRefExpr
  }

  def translateVarFullName(
    fullName: FullNameT[IVarNameT]):
  FullNameT[IVarNameT] = {
    val FullNameT(module, steps, last) = fullName
    val result =
      FullNameT(
        module,
        steps.map(translateName),
        translateVarName(last))
    result
  }

  def translateFunctionFullName(
    fullNameT: FullNameT[IFunctionNameT]):
  FullNameT[IFunctionNameT] = {
    val FullNameT(module, steps, last) = fullNameT
    val fullName =
      FullNameT(
        module,
        steps.map(translateName),
        translateFunctionName(last))
    //    if (opts.sanityCheck) {
    //      vassert(Collector.all(fullName, { case PlaceholderNameT(_) => }).isEmpty)
    //    }
    fullName
  }

  def translateStructFullName(
    fullNameT: FullNameT[IStructNameT],
    instantiationBoundArgs: InstantiationBoundArguments):
  FullNameT[IStructNameT] = {
    val FullNameT(module, steps, lastT) = fullNameT

    val fullName =
      FullNameT(
        module,
        steps.map(translateName),
        translateStructName(lastT))


    Monomorphizer.translateStructDefinition(
      opts, interner, keywords, hinputs, monouts, fullName, instantiationBoundArgs)

    return fullName
  }

  def translateInterfaceFullName(
    fullNameT: FullNameT[IInterfaceNameT],
    instantiationBoundArgs: InstantiationBoundArguments):
  FullNameT[IInterfaceNameT] = {
    val FullNameT(module, steps, last) = fullNameT
    val newFullName =
      FullNameT(
        module,
        steps.map(translateName),
        translateInterfaceName(last))


    Monomorphizer.translateInterfaceDefinition(
      opts, interner, keywords, hinputs, monouts, newFullName, instantiationBoundArgs)

    newFullName
  }

  def translateCitizenName(t: ICitizenNameT): ICitizenNameT = {
    t match {
      case s : IStructNameT => translateStructName(s)
      case i : IInterfaceNameT => translateInterfaceName(i)
    }
  }

  def translateCitizenFullName(
    fullName: FullNameT[ICitizenNameT],
    instantiationBoundArgs: InstantiationBoundArguments):
  FullNameT[ICitizenNameT] = {
    fullName match {
      case FullNameT(module, steps, last : IStructNameT) => {
        translateStructFullName(FullNameT(module, steps, last), instantiationBoundArgs)
      }
      case FullNameT(module, steps, last : IInterfaceNameT) => {
        translateInterfaceFullName(FullNameT(module, steps, last), instantiationBoundArgs)
      }
      case other => vimpl(other)
    }
  }

  def translateImplFullName(
    fullNameT: FullNameT[IImplNameT],
    instantiationBoundArgs: InstantiationBoundArguments):
  FullNameT[IImplNameT] = {
    val FullNameT(module, steps, last) = fullNameT
    val fullName =
      FullNameT(
        module,
        steps.map(translateName),
        translateImplName(last, instantiationBoundArgs))


    fullNameT match {
      case FullNameT(packageCoord, initSteps, name@ImplBoundNameT(_, _)) => {
        val implBoundName = FullNameT(packageCoord, initSteps, name)
        val result = vassertSome(denizenBoundToDenizenCallerSuppliedThing.implBoundToCallerSuppliedBoundArgImpl.get(implBoundName))
        //        if (opts.sanityCheck) {
        //          vassert(Collector.all(result, { case PlaceholderTemplateNameT(_) => }).isEmpty)
        //        }
        result
      }
      case FullNameT(_, _, _) => {
        monouts.newImpls.enqueue((fullName, instantiationBoundArgs))
        fullName
      }
    }
  }

  def translateFullName(
    fullName: FullNameT[INameT]):
  FullNameT[INameT] = {
    vimpl()
  }

  def translateCoord(
    coord: CoordT):
  CoordT = {
    val CoordT(ownership, kind) = coord
    kind match {
      case PlaceholderT(placeholderFullName @ FullNameT(_, _, PlaceholderNameT(PlaceholderTemplateNameT(_)))) => {
        // Let's get the index'th placeholder from the top level denizen.
        // If we're compiling a function or a struct, it might actually be a lambda function or lambda struct.
        // In these cases, the topLevelDenizenPlaceholderIndexToTemplata actually came from the containing function,
        // see LHPCTLD.

        vassertSome(placeholderFullNameToTemplata.get(placeholderFullName)) match {
          case CoordTemplata(CoordT(innerOwnership, kind)) => {
            val combinedOwnership =
              (ownership, innerOwnership) match {
                case (OwnT, OwnT) => OwnT
                case (OwnT, BorrowT) => BorrowT
                case (BorrowT, OwnT) => BorrowT
                case (BorrowT, BorrowT) => BorrowT
                case (BorrowT, WeakT) => WeakT
                case (BorrowT, ShareT) => ShareT
                case (WeakT, OwnT) => WeakT
                case (WeakT, BorrowT) => WeakT
                case (WeakT, WeakT) => WeakT
                case (WeakT, ShareT) => ShareT
                case (ShareT, ShareT) => ShareT
                case (OwnT, ShareT) => ShareT
                case other => vwat(other)
              }
            CoordT(combinedOwnership, kind)
          }
          case KindTemplata(kind) => CoordT(ownership, kind)
        }
      }
      case other => {
        // We could, for example, be translating an Vector<myFunc$0, T> (which is temporarily regarded mutable)
        // to an Vector<imm, int> (which is immutable).
        // So, we have to check for that here and possibly make the ownership share.
        val kind = translateKind(other)
        val mutability = getMutability(kind)
        val newOwnership =
          (ownership, mutability) match {
            case (_, ImmutableT) => ShareT
            case (other, MutableT) => other
          }
        CoordT(newOwnership, translateKind(other))
      }
    }
  }

  def getMutability(t: KindT): MutabilityT = {
    t match {
      case IntT(_) | BoolT() | StrT() | NeverT(_) | FloatT() | VoidT() => ImmutableT
      case StructTT(name) => {
        vassertSome(monouts.startedStructs.get(name))._1
      }
      case InterfaceTT(name) => {
        vassertSome(monouts.startedInterfaces.get(name))._1
      }
      case RuntimeSizedArrayTT(FullNameT(_, _, RuntimeSizedArrayNameT(_, RawArrayNameT(mutability, _)))) => {
        expectMutabilityTemplata(mutability).mutability
      }
      case StaticSizedArrayTT(FullNameT(_, _, StaticSizedArrayNameT(_, _, _, RawArrayNameT(mutability, _)))) => {
        expectMutabilityTemplata(mutability).mutability
      }
      case other => vimpl(other)
    }
  }

  def translateCitizen(citizen: ICitizenTT, instantiationBoundArgs: InstantiationBoundArguments): ICitizenTT = {
    citizen match {
      case s @ StructTT(_) => translateStruct(s, instantiationBoundArgs)
      case s @ InterfaceTT(_) => translateInterface(s, instantiationBoundArgs)
    }
  }

  def translateStruct(struct: StructTT, instantiationBoundArgs: InstantiationBoundArguments): StructTT = {
    val StructTT(fullName) = struct

    val desiredStruct = interner.intern(StructTT(translateStructFullName(fullName, instantiationBoundArgs)))

    desiredStruct
  }

  def translateInterface(interface: InterfaceTT, instantiationBoundArgs: InstantiationBoundArguments): InterfaceTT = {
    val InterfaceTT(fullName) = interface

    val desiredInterface = interner.intern(InterfaceTT(translateInterfaceFullName(fullName, instantiationBoundArgs)))

    desiredInterface
  }

  def translateSuperKind(kind: ISuperKindTT): ISuperKindTT = {
    kind match {
      case i @ InterfaceTT(_) => {
        translateInterface(
          i,
          translateBoundArgsForCallee(
            hinputs.getInstantiationBoundArgs(i.fullName)))
      }
      case p @ PlaceholderT(_) => {
        translatePlaceholder(p) match {
          case s : ISuperKindTT => s
          case other => vwat(other)
        }
      }
    }
  }

  def translatePlaceholder(t: PlaceholderT): KindT = {
    ITemplata.expectKindTemplata(vassertSome(placeholderFullNameToTemplata.get(t.fullName))).kind
  }

  def translateStaticSizedArray(ssaTT: StaticSizedArrayTT): StaticSizedArrayTT = {
    val StaticSizedArrayTT(
    FullNameT(
    packageCoord,
    initSteps,
    StaticSizedArrayNameT(template, size, variability, RawArrayNameT(mutability, elementType)))) = ssaTT

    interner.intern(StaticSizedArrayTT(
      FullNameT(
        packageCoord,
        initSteps,
        interner.intern(StaticSizedArrayNameT(
          template,
          expectIntegerTemplata(translateTemplata(size)),
          expectVariabilityTemplata(translateTemplata(variability)),
          interner.intern(RawArrayNameT(
            expectMutabilityTemplata(translateTemplata(mutability)),
            translateCoord(elementType))))))))
  }

  def translateRuntimeSizedArray(ssaTT: RuntimeSizedArrayTT): RuntimeSizedArrayTT = {
    val RuntimeSizedArrayTT(
    FullNameT(
    packageCoord,
    initSteps,
    RuntimeSizedArrayNameT(template, RawArrayNameT(mutability, elementType)))) = ssaTT

    interner.intern(RuntimeSizedArrayTT(
      FullNameT(
        packageCoord,
        initSteps,
        interner.intern(RuntimeSizedArrayNameT(
          template,
          interner.intern(RawArrayNameT(
            expectMutabilityTemplata(translateTemplata(mutability)),
            translateCoord(elementType))))))))
  }

  def translateKind(kind: KindT): KindT = {
    kind match {
      case IntT(bits) => IntT(bits)
      case BoolT() => BoolT()
      case FloatT() => FloatT()
      case VoidT() => VoidT()
      case StrT() => StrT()
      case NeverT(fromBreak) => NeverT(fromBreak)
      case p @ PlaceholderT(_) => translatePlaceholder(p)
      case s @ StructTT(_) => {
        translateStruct(
          s, translateBoundArgsForCallee(hinputs.getInstantiationBoundArgs(s.fullName)))
      }
      case s @ InterfaceTT(_) => {
        translateInterface(
          s, translateBoundArgsForCallee(hinputs.getInstantiationBoundArgs(s.fullName)))
      }
      case a @ contentsStaticSizedArrayTT(_, _, _, _) => translateStaticSizedArray(a)
      case a @ contentsRuntimeSizedArrayTT(_, _) => translateRuntimeSizedArray(a)
      case other => vimpl(other)
    }
  }

  def translateParameter(
    param: ParameterT):
  ParameterT = {
    val ParameterT(name, virtuality, tyype) = param
    ParameterT(
      translateVarName(name),
      virtuality,
      translateCoord(tyype))
  }

  def translateTemplata(
    templata: ITemplata[ITemplataType]):
  ITemplata[ITemplataType] = {
    val result =
      templata match {
        case PlaceholderTemplata(n @ FullNameT(_, _, _), _) =>  {
          vassertSome(placeholderFullNameToTemplata.get(n))
        }
        case IntegerTemplata(value) => IntegerTemplata(value)
        case BooleanTemplata(value) => BooleanTemplata(value)
        case StringTemplata(value) => StringTemplata(value)
        case CoordTemplata(coord) => CoordTemplata(translateCoord(coord))
        case MutabilityTemplata(mutability) => MutabilityTemplata(mutability)
        case VariabilityTemplata(variability) => VariabilityTemplata(variability)
        case KindTemplata(kind) => KindTemplata(translateKind(kind))
        case other => vimpl(other)
      }
    if (opts.sanityCheck) {
      vassert(Collector.all(result, { case PlaceholderNameT(_) => }).isEmpty)
    }
    result
  }

  def translateVarName(
    name: IVarNameT):
  IVarNameT = {
    name match {
      case TypingPassFunctionResultVarNameT() => name
      case CodeVarNameT(_) => name
      case ClosureParamNameT() => name
      case TypingPassBlockResultVarNameT(life) => name
      case TypingPassTemporaryVarNameT(life) => name
      case ConstructingMemberNameT(_) => name
      case IterableNameT(range) => name
      case IteratorNameT(range) => name
      case IterationOptionNameT(range) => name
      case MagicParamNameT(codeLocation2) => name
      case SelfNameT() => name
      case other => vimpl(other)
    }
  }

  def translateFunctionName(
    name: IFunctionNameT):
  IFunctionNameT = {
    name match {
      case FunctionNameT(FunctionTemplateNameT(humanName, codeLoc), templateArgs, params) => {
        interner.intern(FunctionNameT(
          interner.intern(FunctionTemplateNameT(humanName, codeLoc)),
          templateArgs.map(translateTemplata),
          params.map(translateCoord)))
      }
      case ForwarderFunctionNameT(ForwarderFunctionTemplateNameT(innerTemplate, index), inner) => {
        interner.intern(ForwarderFunctionNameT(
          interner.intern(ForwarderFunctionTemplateNameT(
            // We dont translate these, as these are what uniquely identify generics, and we need that
            // information later to map this back to its originating generic.
            // See DMPOGN for a more detailed explanation. This oddity is really tricky.
            innerTemplate,
            index)),
          translateFunctionName(inner)))
      }
      case ExternFunctionNameT(humanName, parameters) => {
        interner.intern(ExternFunctionNameT(humanName, parameters.map(translateCoord)))
      }
      case FunctionBoundNameT(FunctionBoundTemplateNameT(humanName, codeLocation), templateArgs, params) => {
        interner.intern(FunctionBoundNameT(
          interner.intern(FunctionBoundTemplateNameT(humanName, codeLocation)),
          templateArgs.map(translateTemplata),
          params.map(translateCoord)))
      }
      case AnonymousSubstructConstructorNameT(template, templateArgs, params) => {
        interner.intern(AnonymousSubstructConstructorNameT(
          translateName(template) match {
            case x @ AnonymousSubstructConstructorTemplateNameT(_) => x
            case other => vwat(other)
          },
          templateArgs.map(translateTemplata),
          params.map(translateCoord)))
      }
      case LambdaCallFunctionNameT(LambdaCallFunctionTemplateNameT(codeLocation, paramTypesForGeneric), templateArgs, paramTypes) => {
        interner.intern(LambdaCallFunctionNameT(
          interner.intern(LambdaCallFunctionTemplateNameT(
            codeLocation,
            // We dont translate these, as these are what uniquely identify generics, and we need that
            // information later to map this back to its originating generic.
            // See DMPOGN for a more detailed explanation. This oddity is really tricky.
            paramTypesForGeneric)),
          templateArgs.map(translateTemplata),
          paramTypes.map(translateCoord)))
      }
      case other => vimpl(other)
    }
  }

  def translateImplName(
    name: IImplNameT,
    instantiationBoundArgs: InstantiationBoundArguments):
  IImplNameT = {
    name match {
      case ImplNameT(ImplTemplateNameT(codeLocationS), templateArgs, subCitizen) => {
        interner.intern(ImplNameT(
          interner.intern(ImplTemplateNameT(codeLocationS)),
          templateArgs.map(translateTemplata),
          translateCitizen(
            subCitizen,
            hinputs.getInstantiationBoundArgs(subCitizen.fullName))))
      }
      case ImplBoundNameT(ImplBoundTemplateNameT(codeLocationS), templateArgs) => {
        interner.intern(ImplBoundNameT(
          interner.intern(ImplBoundTemplateNameT(codeLocationS)),
          templateArgs.map(translateTemplata)))
      }
      case AnonymousSubstructImplNameT(AnonymousSubstructImplTemplateNameT(interface), templateArgs, subCitizen) => {
        interner.intern(AnonymousSubstructImplNameT(
          interner.intern(AnonymousSubstructImplTemplateNameT(
            // We dont translate these, as these are what uniquely identify generics, and we need that
            // information later to map this back to its originating generic.
            // See DMPOGN for a more detailed explanation. This oddity is really tricky.
            interface)),
          templateArgs.map(translateTemplata),
          translateCitizen(
            subCitizen,
            hinputs.getInstantiationBoundArgs(subCitizen.fullName))))
      }
    }
  }

  def translateStructName(
    name: IStructNameT):
  IStructNameT = {
    name match {
      case StructNameT(StructTemplateNameT(humanName), templateArgs) => {
        interner.intern(StructNameT(
          interner.intern(StructTemplateNameT(humanName)),
          templateArgs.map(translateTemplata)))
      }
      case AnonymousSubstructNameT(AnonymousSubstructTemplateNameT(interface), templateArgs) => {
        interner.intern(AnonymousSubstructNameT(
          interner.intern(AnonymousSubstructTemplateNameT(
            translateInterfaceTemplateName(interface))),
          templateArgs.map(translateTemplata)))
      }
      case LambdaCitizenNameT(LambdaCitizenTemplateNameT(codeLocation)) => name
      case other => vimpl(other)
    }
  }

  def translateInterfaceName(
    name: IInterfaceNameT):
  IInterfaceNameT = {
    name match {
      case InterfaceNameT(InterfaceTemplateNameT(humanName), templateArgs) => {
        interner.intern(InterfaceNameT(
          interner.intern(InterfaceTemplateNameT(humanName)),
          templateArgs.map(translateTemplata)))
      }
      case other => vimpl(other)
    }
  }

  def translateInterfaceTemplateName(
    name: IInterfaceTemplateNameT):
  IInterfaceTemplateNameT = {
    name match {
      case InterfaceTemplateNameT(humanName) => name
      case other => vimpl(other)
    }
  }

  def translateName(
    name: INameT):
  INameT = {
    name match {
      case v : IVarNameT => translateVarName(v)
      case PlaceholderTemplateNameT(index) => vwat()
      case PlaceholderNameT(inner) => vwat()
      case StructNameT(StructTemplateNameT(humanName), templateArgs) => {
        interner.intern(StructNameT(
          interner.intern(StructTemplateNameT(humanName)),
          templateArgs.map(translateTemplata)))
      }
      case ForwarderFunctionTemplateNameT(inner, index) => {
        interner.intern(ForwarderFunctionTemplateNameT(
          // We dont translate these, as these are what uniquely identify generics, and we need that
          // information later to map this back to its originating generic.
          // See DMPOGN for a more detailed explanation. This oddity is really tricky.
          inner,
          index))
      }
      case AnonymousSubstructConstructorTemplateNameT(substructTemplateName) => {
        interner.intern(AnonymousSubstructConstructorTemplateNameT(
          translateName(substructTemplateName) match {
            case x : ICitizenTemplateNameT => x
            case other => vwat(other)
          }))
      }
      case FunctionTemplateNameT(humanName, codeLoc) => name
      case StructTemplateNameT(humanName) => name
      case LambdaCitizenTemplateNameT(codeLoc) => name
      case AnonymousSubstructTemplateNameT(interface) => {
        interner.intern(AnonymousSubstructTemplateNameT(
          translateInterfaceTemplateName(interface)))
      }
      case LambdaCitizenNameT(LambdaCitizenTemplateNameT(codeLocation)) => name
      case InterfaceTemplateNameT(humanNamee) => name
      //      case FreeTemplateNameT(codeLoc) => name
      case f : IFunctionNameT => translateFunctionName(f)
      case other => vimpl(other)
    }
  }

  def translateImplDefinition(
    implFullName: FullNameT[IImplNameT],
    implDefinition: EdgeT):
  Unit = {
    if (monouts.impls.contains(implFullName)) {
      return
    }

    val citizen =
      translateCitizen(
        implDefinition.subCitizen,
        translateBoundArgsForCallee(hinputs.getInstantiationBoundArgs(implDefinition.subCitizen.fullName)))
    val superInterface =
      translateInterfaceFullName(
        implDefinition.superInterface,
        translateBoundArgsForCallee(hinputs.getInstantiationBoundArgs(implDefinition.superInterface)))
    monouts.impls.put(implFullName, (citizen, superInterface, denizenBoundToDenizenCallerSuppliedThing, this))

    vassertSome(monouts.interfaceToImplToAbstractPrototypeToOverride.get(superInterface))
      .put(implFullName, mutable.HashMap())
    vassertSome(monouts.interfaceToImpls.get(superInterface)).add(implFullName)


    vassertSome(monouts.interfaceToAbstractFuncToVirtualIndex.get(superInterface))
      .foreach({ case (abstractFuncPrototype, virtualIndex) =>
        Monomorphizer.translateOverride(
          opts, interner, keywords, hinputs, monouts, implFullName, abstractFuncPrototype)
      })
  }
}
