package dev.vale.typing.env

import dev.vale.{CodeLocationS, Err, Interner, Ok, PackageCoordinate, Profiler, Result, StrI, vassert, vassertSome, vcurious, vfail, vimpl, vwat}
import dev.vale.postparsing._
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.macros.citizen._
import dev.vale.typing.macros.{AnonymousInterfaceMacro, FunctorHelper, IFunctionBodyMacro, IOnImplDefinedMacro, IOnInterfaceDefinedMacro, IOnStructDefinedMacro, StructConstructorMacro}
import dev.vale.highertyping._
import dev.vale.postparsing._
import dev.vale.typing._
import TemplatasStore.{entryMatchesFilter, entryToTemplata, getImpreciseName}
import dev.vale.typing.names._
import dev.vale.typing.templata
import dev.vale.typing.templata._
import dev.vale.typing.macros.citizen._
import dev.vale.typing.macros.IOnImplDefinedMacro
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types.{InterfaceTT, PlaceholderT, StructTT}

import scala.collection.immutable.{List, Map, Set}


trait IEnvironment {
  override def toString: String = {
    "#Environment:" + fullName
  }
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vfail() // Shouldnt hash these, too big.

  def globalEnv: GlobalEnvironment

  // This is the denizen that we're currently compiling.
  // If we're compiling a generic, it's the denizen that currently has placeholders defined.
  def rootCompilingDenizenEnv: IEnvironment

  def templatas: TemplatasStore

  private[env] def lookupWithImpreciseNameInner(
    nameS: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata[ITemplataType]]

  private[env] def lookupWithNameInner(
    nameS: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata[ITemplataType]]

  def lookupAllWithImpreciseName(
    nameS: IImpreciseNameS,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata[ITemplataType]] = {
    Profiler.frame(() => {
      lookupWithImpreciseNameInner(nameS, lookupFilter, false)
    })
  }

  def lookupAllWithName(
    nameS: INameT,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata[ITemplataType]] = {
    Profiler.frame(() => {
      lookupWithNameInner(nameS, lookupFilter, false)
    })
  }

  def lookupNearestWithName(
    nameS: INameT,
    lookupFilter: Set[ILookupContext]):
  Option[ITemplata[ITemplataType]] = {
    Profiler.frame(() => {
      lookupWithNameInner(nameS, lookupFilter, true).toList match {
        case List() => None
        case List(only) => Some(only)
        case multiple => vfail("Too many with name " + nameS + ": " + multiple)
      }
    })
  }

  def lookupNearestWithImpreciseName(
    nameS: IImpreciseNameS,
    lookupFilter: Set[ILookupContext]):
  Option[ITemplata[ITemplataType]] = {
    Profiler.frame(() => {
      lookupWithImpreciseNameInner(nameS, lookupFilter, true).toList match {
        case List() => None
        case List(only) => Some(only)
        case many => vfail("Too many with name: " + nameS + ":\n" + many.mkString("\n"))
      }
    })
  }

  def fullName: IdT[INameT]
}

trait IEnvironmentBox extends IEnvironment {
  def snapshot: IEnvironment
  override def toString: String = {
    "#Environment:" + fullName
  }
  def globalEnv: GlobalEnvironment

  def fullName: IdT[INameT]
}

sealed trait ILookupContext
case object TemplataLookupContext extends ILookupContext
case object ExpressionLookupContext extends ILookupContext

case class GlobalEnvironment(
  functorHelper: FunctorHelper,
  structConstructorMacro: StructConstructorMacro,
  structDropMacro: StructDropMacro,
//  structFreeMacro: StructFreeMacro,
  interfaceDropMacro: InterfaceDropMacro,
//  interfaceFreeMacro: InterfaceFreeMacro,
  anonymousInterfaceMacro: AnonymousInterfaceMacro,
  nameToStructDefinedMacro: Map[StrI, IOnStructDefinedMacro],
  nameToInterfaceDefinedMacro: Map[StrI, IOnInterfaceDefinedMacro],
  nameToImplDefinedMacro: Map[StrI, IOnImplDefinedMacro],
  nameToFunctionBodyMacro: Map[StrI, IFunctionBodyMacro],
  // We *dont* search through these in lookupWithName etc.
  // This doesn't just contain the user's things, it can contain generated things
  // like struct constructors, interface constructors, etc.
  // This isn't just packages, structs can have entries here too, because their
  // environments might have things, like a struct's methods might be here.
  // Any particular IEnvironment subclass has a subset of these.
  nameToTopLevelEnvironment: Map[IdT[PackageTopLevelNameT], TemplatasStore],
  // Primitives and other builtins
  builtins: TemplatasStore
)

object TemplatasStore {
  def entryMatchesFilter(entry: IEnvEntry, contexts: Set[ILookupContext]): Boolean = {
    entry match {
      case FunctionEnvEntry(_) => contexts.contains(ExpressionLookupContext)
      case ImplEnvEntry(_) => contexts.contains(TemplataLookupContext)
      case StructEnvEntry(_) => contexts.contains(TemplataLookupContext)
      case InterfaceEnvEntry(_) => contexts.contains(TemplataLookupContext)
      case TemplataEnvEntry(templata) => {
        templata match {
          case PlaceholderTemplata(_, _) => contexts.contains(TemplataLookupContext)
          case IsaTemplata(_, _, _, _) => contexts.contains(TemplataLookupContext)
//          case PrototypeTemplata(_, _, _) => true
          case CoordTemplata(_) => contexts.contains(TemplataLookupContext)
          case CoordListTemplata(_) => contexts.contains(TemplataLookupContext)
          case PrototypeTemplata(_, _) => true
          case KindTemplata(_) => contexts.contains(TemplataLookupContext)
          case StructDefinitionTemplata(_, _) => contexts.contains(TemplataLookupContext)
          case InterfaceDefinitionTemplata(_, _) => contexts.contains(TemplataLookupContext)
          case RuntimeSizedArrayTemplateTemplata() => contexts.contains(TemplataLookupContext)
          case BooleanTemplata(_) => true
          case FunctionTemplata(_, _) => contexts.contains(ExpressionLookupContext)
          case ImplDefinitionTemplata(_, _) => contexts.contains(ExpressionLookupContext)
          case IntegerTemplata(_) => true
          case StringTemplata(_) => true
          case LocationTemplata(_) => contexts.contains(TemplataLookupContext)
          case MutabilityTemplata(_) => contexts.contains(TemplataLookupContext)
          case OwnershipTemplata(_) => contexts.contains(TemplataLookupContext)
          case VariabilityTemplata(_) => contexts.contains(TemplataLookupContext)
//          case ExternImplTemplata(_, _) => contexts.contains(TemplataLookupContext)
          case ExternFunctionTemplata(_) => contexts.contains(ExpressionLookupContext)
        }
      }
    }
  }

  def entryToTemplata(definingEnv: IEnvironment, entry: IEnvEntry): ITemplata[ITemplataType] = {
    //    vassert(env.fullName != FullName2(PackageCoordinate.BUILTIN, Vector.empty, PackageTopLevelName2()))
    entry match {
      case FunctionEnvEntry(func) => templata.FunctionTemplata(definingEnv, func)
      case StructEnvEntry(struct) => templata.StructDefinitionTemplata(definingEnv, struct)
      case InterfaceEnvEntry(interface) => templata.InterfaceDefinitionTemplata(definingEnv, interface)
      case ImplEnvEntry(impl) => templata.ImplDefinitionTemplata(definingEnv, impl)
      case TemplataEnvEntry(templata) => templata
    }
  }

  def getImpreciseName(interner: Interner, name2: INameT): Option[IImpreciseNameS] = {
    name2 match {
      case StructTemplateNameT(humanName) => Some(interner.intern(CodeNameS(humanName)))
      case InterfaceTemplateNameT(humanName) => Some(interner.intern(CodeNameS(humanName)))
      case PrimitiveNameT(humanName) => Some(interner.intern(CodeNameS(humanName)))
      case CitizenNameT(templateName, _) => getImpreciseName(interner, templateName)
      case FunctionTemplateNameT(humanName, _) => Some(interner.intern(CodeNameS(humanName)))
      case FunctionNameT(FunctionTemplateNameT(humanName, _), _, _) => Some(interner.intern(CodeNameS(humanName)))
      case RuneNameT(r) => Some(interner.intern(RuneNameS(r)))
      case LambdaCitizenNameT(template) => getImpreciseName(interner, template)
      case LambdaCitizenTemplateNameT(loc) => Some(interner.intern(LambdaStructImpreciseNameS(interner.intern(LambdaImpreciseNameS()))))
      case ClosureParamNameT() => Some(interner.intern(ClosureParamNameS()))
      case SelfNameT() => Some(interner.intern(SelfNameS()))
      case ArbitraryNameT() => Some(interner.intern(ArbitraryNameS()))
      case AnonymousSubstructImplNameT(_, _, _) => None
      case AnonymousSubstructConstructorTemplateNameT(StructTemplateNameT(humanName)) => {
        Some(interner.intern(CodeNameS(humanName)))
      }
      case AnonymousSubstructTemplateNameT(ctn) => {
        getImpreciseName(interner, ctn).map(x => interner.intern(AnonymousSubstructTemplateImpreciseNameS(x)))
      }
      case AnonymousSubstructConstructorTemplateNameT(AnonymousSubstructTemplateNameT(InterfaceTemplateNameT(humanName))) => {
        Some(interner.intern(CodeNameS(humanName)))
      }
      case AnonymousSubstructNameT(interfaceName, _) => getImpreciseName(interner, interfaceName)
      case ImplTemplateNameT(_) => {
        // We shouldn't get here, caller shouldn't pass these in. Should instead get the impl
        // imprecise name from the ImplA or somewhere else.
        vwat()
      }
//      case LambdaTemplateNameT(codeLocation) => Some(interner.intern(LambdaImpreciseNameS()))
      case PlaceholderNameT(PlaceholderTemplateNameT(index)) => Some(interner.intern(PlaceholderImpreciseNameS(index)))
      case ReachablePrototypeNameT(num) => None
//      case AbstractVirtualFreeTemplateNameT(codeLoc) => Some(interner.intern(VirtualFreeImpreciseNameS()))
      case ForwarderFunctionTemplateNameT(inner, index) => getImpreciseName(interner, inner)
      case ForwarderFunctionNameT(_, inner) => getImpreciseName(interner, inner)
      case FunctionBoundNameT(inner, _, _) => getImpreciseName(interner, inner)
      case FunctionBoundTemplateNameT(humanName, _) => Some(interner.intern(CodeNameS(humanName)))
//      case AnonymousSubstructImplTemplateNameT(inner) => getImpreciseName(interner, inner).map(ImplImpreciseNameS)
//      case OverrideVirtualFreeTemplateNameT(codeLoc) => Some(interner.intern(VirtualFreeImpreciseNameS()))
//      case AbstractVirtualFreeNameT(_, _) => Some(interner.intern(VirtualFreeImpreciseNameS()))
//      case OverrideVirtualFreeNameT(_, _) => Some(interner.intern(VirtualFreeImpreciseNameS()))
//      case OverrideVirtualDropFunctionTemplateNameT(_) => Some(interner.intern(CodeNameS(Scout.VIRTUAL_DROP_FUNCTION_NAME)))
//      case AbstractVirtualDropFunctionTemplateNameT(_) => Some(interner.intern(CodeNameS(Scout.VIRTUAL_DROP_FUNCTION_NAME)))
//      case OverrideVirtualDropFunctionNameT(_, _, _) => Some(interner.intern(CodeNameS(Scout.VIRTUAL_DROP_FUNCTION_NAME)))
//      case AbstractVirtualDropFunctionNameT(_, _, _) => Some(interner.intern(CodeNameS(Scout.VIRTUAL_DROP_FUNCTION_NAME)))
      case other => vimpl(other.toString)
    }
  }

  def codeLocationsMatch(codeLocationA: CodeLocationS, codeLocation2: CodeLocationS): Boolean = {
    val CodeLocationS(lineS, charS) = codeLocationA
    val CodeLocationS(line2, char2) = codeLocation2
    lineS == line2 && charS == char2
  }
}

// See DBTSAE for difference between TemplatasStore and Environment.
case class TemplatasStore(
  templatasStoreName: IdT[INameT],
  // This is the source of truth. Anything in the environment is in here.
  entriesByNameT: Map[INameT, IEnvEntry],
  // This is just an index for quick looking up of things by their imprecise name.
  // Not everything in the above entriesByNameT will have something in here.
  // Vector because multiple things can share an INameS; function overloads.
  entriesByImpreciseNameS: Map[IImpreciseNameS, Vector[IEnvEntry]]
) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  entriesByNameT.values.foreach({
    case FunctionEnvEntry(function) => vassert(function.name.packageCoordinate == templatasStoreName.packageCoord)
    case StructEnvEntry(struct) => vassert(struct.range.file.packageCoordinate == templatasStoreName.packageCoord)
    case InterfaceEnvEntry(interface) => vassert(interface.name.range.file.packageCoordinate == templatasStoreName.packageCoord)
    case _ =>
  })

  //  // The above map, indexed by human name. If it has no human name, it won't be in here.
  //  private var entriesByHumanName = Map[String, Vector[IEnvEntry]]()

  def addEntries(interner: Interner, newEntriesList: Vector[(INameT, IEnvEntry)]): TemplatasStore = {
    val newEntries = newEntriesList.toMap
    vassert(newEntries.size == newEntriesList.size)

    val oldEntries = entriesByNameT

    val combinedEntries = oldEntries ++ newEntries
    val intersection = oldEntries.keySet.intersect(newEntries.keySet)

    oldEntries.keySet.intersect(newEntries.keySet).foreach(key => {
      vassert(oldEntries(key) == newEntries(key))
      // We can get here  if we use RuneEnvLookup rules,
      // those "figure out" the rune, though it already existed.
      // They end up reintroducing those rules to the env, even though
      // they were already there.
    })

    val newEntriesByNameS =
      newEntries
        .toVector
        .flatMap({
          case (key, value @ TemplataEnvEntry(PrototypeTemplata(_, prototype))) => {
            // This is so if we have:
            //    where func moo(T)T
            // then that prototype will be accessible via not only ImplicitRune(1.4.6.1)
            // but also CodeNameS("moo").
            getImpreciseName(interner, key).toList.map(_ -> value) ++
              getImpreciseName(interner, prototype.fullName.localName).map(_ -> value) ++
              List(interner.intern(PrototypeNameS()) -> value)
          }
          case (key, entry @ ImplEnvEntry(implA)) => {
            List(
              interner.intern(ImplImpreciseNameS(implA.subCitizenImpreciseName, implA.superInterfaceImpreciseName)) -> entry,
              interner.intern(ImplSubCitizenImpreciseNameS(implA.subCitizenImpreciseName)) -> entry,
              interner.intern(ImplSuperInterfaceImpreciseNameS(implA.superInterfaceImpreciseName)) -> entry)
          }
          case (key, entry @ TemplataEnvEntry(IsaTemplata(_, _, subKind, superKind))) => {
            val subImpreciseName =
              subKind match {
                case StructTT(fullName) => vassertSome(getImpreciseName(interner, fullName.localName))
                case InterfaceTT(fullName) => vassertSome(getImpreciseName(interner, fullName.localName))
                case PlaceholderT(fullName) => vassertSome(getImpreciseName(interner, fullName.localName))
                case _ => vwat()
              }
            val superImpreciseName =
              superKind match {
                case InterfaceTT(fullName) => vassertSome(getImpreciseName(interner, fullName.localName))
                case PlaceholderT(fullName) => vassertSome(getImpreciseName(interner, fullName.localName))
                case _ => vwat()
              }
            getImpreciseName(interner, key).toList.map(_ -> entry) ++
            List(
              interner.intern(ImplImpreciseNameS(subImpreciseName, superImpreciseName)) -> entry,
              interner.intern(ImplSubCitizenImpreciseNameS(subImpreciseName)) -> entry,
              interner.intern(ImplSuperInterfaceImpreciseNameS(superImpreciseName)) -> entry)
          }
          case (key, value) => {
            getImpreciseName(interner, key).toList.map(_ -> value)
          }
        })
        .groupBy(_._1)
        .mapValues(_.map(_._2))
    val combinedEntriesByNameS =
      entriesByImpreciseNameS ++
        newEntriesByNameS ++
        entriesByImpreciseNameS.keySet.intersect(newEntriesByNameS.keySet)
          .map(key => (key -> (entriesByImpreciseNameS(key) ++ newEntriesByNameS(key))))
          .toMap

    TemplatasStore(templatasStoreName, combinedEntries, combinedEntriesByNameS)
  }

  def addEntry(interner: Interner, name: INameT, entry: IEnvEntry): TemplatasStore = {
    addEntries(interner, Vector(name -> entry))
  }

  private[env] def lookupWithNameInner(
    definingEnv: IEnvironment,

    name: INameT,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata[ITemplataType]] = {
    entriesByNameT.get(name)
      .filter(entryMatchesFilter(_, lookupFilter))
      .map(entryToTemplata(definingEnv, _))
  }

  private[env] def lookupWithImpreciseNameInner(
    definingEnv: IEnvironment,

    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata[ITemplataType]] = {
    val a1 = entriesByImpreciseNameS.getOrElse(name, Vector())
    val a2 = a1.filter(entryMatchesFilter(_, lookupFilter))
    val a3 = a2.map(entryToTemplata(definingEnv, _))
    a3
  }
}

object PackageEnvironment {
  // THIS IS TEMPORARY, it pulls in all global namespaces!
  // See https://github.com/ValeLang/Vale/issues/356
  def makeTopLevelEnvironment(globalEnv: GlobalEnvironment, namespaceName: IdT[INameT]): PackageEnvironment[INameT] = {
    PackageEnvironment(
      globalEnv,
      namespaceName,
      globalEnv.nameToTopLevelEnvironment.values.toVector)
  }
}

case class PackageEnvironment[+T <: INameT](
  globalEnv: GlobalEnvironment,
  fullName: IdT[T],

  // These are ones that the user imports (or the ancestors that we implicitly import)
  globalNamespaces: Vector[TemplatasStore]
) extends IEnvironment {
  val hash = runtime.ScalaRunTime._hashCode(fullName); override def hashCode(): Int = hash;

  override def templatas: TemplatasStore = {
    vimpl()
  }

  override def rootCompilingDenizenEnv: IEnvironment = vwat()

  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[IEnvironment]) {
      return false
    }
    return fullName.equals(obj.asInstanceOf[IEnvironment].fullName)
  }

  private[env] override def lookupWithNameInner(
    name: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata[ITemplataType]] = {
    globalEnv.builtins.lookupWithNameInner(this, name, lookupFilter) ++
    globalNamespaces.flatMap(ns => {
      val env = PackageEnvironment(globalEnv, ns.templatasStoreName, globalNamespaces)
      ns.lookupWithNameInner(env, name, lookupFilter)
    })
  }

  private[env] override def lookupWithImpreciseNameInner(
    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata[ITemplataType]] = {
    globalEnv.builtins.lookupWithImpreciseNameInner(this, name, lookupFilter) ++
    globalNamespaces.flatMap(ns => {
      ns.lookupWithImpreciseNameInner(
        PackageEnvironment(globalEnv, ns.templatasStoreName, globalNamespaces),
        name, lookupFilter)
    })
  }
}


case class CitizenEnvironment[+T <: INameT, +Y <: ITemplateNameT](
  globalEnv: GlobalEnvironment,
  parentEnv: IEnvironment,
  templateName: IdT[Y],
  fullName: IdT[T],
  templatas: TemplatasStore
) extends IEnvironment {
  vassert(templatas.templatasStoreName == fullName)

  val hash = runtime.ScalaRunTime._hashCode(fullName); override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[IEnvironment]) {
      return false
    }
    return fullName.equals(obj.asInstanceOf[IEnvironment].fullName)
  }

  override def rootCompilingDenizenEnv: IEnvironment = {
    (fullName.localName, parentEnv.fullName.localName) match {
      case (_ : IInstantiationNameT, _ : ITemplateNameT) => this
      case (_, PackageTopLevelNameT()) => this
      case _ => {
        val result = parentEnv.rootCompilingDenizenEnv
        result.fullName.localName match {
          case _ : IInstantiationNameT =>
          case other => vwat(other)
        }
        result
      }
    }
  }

  private[env] override def lookupWithNameInner(

    name: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata[ITemplataType]] = {
    val result = templatas.lookupWithNameInner(this, name, lookupFilter)
    if (result.nonEmpty && getOnlyNearest) {
      result
    } else {
      result ++ parentEnv.lookupWithNameInner(name, lookupFilter, getOnlyNearest)
    }
  }

  private[env] override def lookupWithImpreciseNameInner(

    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata[ITemplataType]] = {
    val result = templatas.lookupWithImpreciseNameInner(this, name, lookupFilter)
    if (result.nonEmpty && getOnlyNearest) {
      result
    } else {
      result ++ parentEnv.lookupWithImpreciseNameInner(name, lookupFilter, getOnlyNearest)
    }
  }
}

object GeneralEnvironment {
  def childOf[Y <: INameT](
    interner: Interner,
    parentEnv: IEnvironment,
    newName: IdT[Y],
    newEntriesList: Vector[(INameT, IEnvEntry)] = Vector()):
  GeneralEnvironment[Y] = {
    GeneralEnvironment(
      parentEnv.globalEnv,
      parentEnv,
      newName,
      new TemplatasStore(newName, Map(), Map())
        .addEntries(interner, newEntriesList))
  }
}

case class GeneralEnvironment[+T <: INameT](
  globalEnv: GlobalEnvironment,
  parentEnv: IEnvironment,
  fullName: IdT[T],
  templatas: TemplatasStore
) extends IEnvironment {
  override def equals(obj: Any): Boolean = vcurious();

  override def hashCode(): Int = vcurious()

  override def rootCompilingDenizenEnv: IEnvironment = {
    parentEnv match {
      case PackageEnvironment(_, _, _) => this
      case _ => parentEnv.rootCompilingDenizenEnv
    }
  }

  override def lookupWithNameInner(
    name: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata[ITemplataType]] = {
    EnvironmentHelper.lookupWithNameInner(
      this, templatas, parentEnv, name, lookupFilter, getOnlyNearest)
  }

  override def lookupWithImpreciseNameInner(
    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata[ITemplataType]] = {
    EnvironmentHelper.lookupWithImpreciseNameInner(
      this, templatas, parentEnv, name, lookupFilter, getOnlyNearest)
  }
}