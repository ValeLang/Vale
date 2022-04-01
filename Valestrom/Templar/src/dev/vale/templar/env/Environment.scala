package dev.vale.templar.env

import dev.vale.{CodeLocationS, Interner, Profiler, vassert, vcurious, vfail, vimpl, vwat}
import dev.vale.scout.{AnonymousSubstructTemplateImpreciseNameS, ArbitraryNameS, ClosureParamNameS, CodeNameS, FreeImpreciseNameS, IImpreciseNameS, LambdaImpreciseNameS, LambdaStructImpreciseNameS, RuneNameS, SelfNameS}
import dev.vale.templar.expression.CallTemplar
import dev.vale.templar.macros.citizen.{InterfaceDropMacro, InterfaceFreeMacro, StructDropMacro, StructFreeMacro}
import dev.vale.templar.macros.{AnonymousInterfaceMacro, FunctorHelper, IFunctionBodyMacro, IOnImplDefinedMacro, IOnInterfaceDefinedMacro, IOnStructDefinedMacro, StructConstructorMacro}
import dev.vale.astronomer._
import dev.vale.scout._
import dev.vale.templar._
import TemplatasStore.{entryMatchesFilter, entryToTemplata, getImpreciseName}
import dev.vale.templar.names.{AnonymousSubstructConstructorTemplateNameT, AnonymousSubstructImplNameT, AnonymousSubstructNameT, AnonymousSubstructTemplateNameT, ArbitraryNameT, CitizenNameT, CitizenTemplateNameT, ClosureParamNameT, ForwarderFunctionNameT, ForwarderFunctionTemplateNameT, FreeNameT, FreeTemplateNameT, FullNameT, FunctionNameT, FunctionTemplateNameT, INameT, ImplDeclareNameT, LambdaCitizenNameT, LambdaCitizenTemplateNameT, LambdaTemplateNameT, PackageTopLevelNameT, PrimitiveNameT, RuneNameT, SelfNameT}
import dev.vale.templar.templata
import dev.vale.templar.templata.{BooleanTemplata, CoordListTemplata, CoordTemplata, ExternFunctionTemplata, FunctionTemplata, ITemplata, ImplTemplata, IntegerTemplata, InterfaceTemplata, KindTemplata, LocationTemplata, MutabilityTemplata, OwnershipTemplata, PrototypeTemplata, RuntimeSizedArrayTemplateTemplata, StringTemplata, StructTemplata, VariabilityTemplata}
import dev.vale.templar.macros.citizen._
import dev.vale.templar.macros.IOnImplDefinedMacro
import dev.vale.templar.names._
import dev.vale.templar.templata._
import dev.vale.{CodeLocationS, Err, Interner, Ok, PackageCoordinate, Profiler, Result, vassert, vcurious, vfail, vimpl, vwat}

import scala.collection.immutable.{List, Map, Set}


trait IEnvironment {
  override def toString: String = {
    "#Environment"
  }
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vfail() // Shouldnt hash these, too big.

  def globalEnv: GlobalEnvironment

  private[env] def lookupWithImpreciseNameInner(

    nameS: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata]

  private[env] def lookupWithNameInner(

    nameS: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata]

  def lookupAllWithImpreciseName(

    nameS: IImpreciseNameS,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata] = {
    Profiler.frame(() => {
      lookupWithImpreciseNameInner(nameS, lookupFilter, false)
    })
  }

  def lookupAllWithName(

    nameS: INameT,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata] = {
    Profiler.frame(() => {
      lookupWithNameInner(nameS, lookupFilter, false)
    })
  }

  def lookupNearestWithName(

    nameS: INameT,
    lookupFilter: Set[ILookupContext]):
  Option[ITemplata] = {
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
  Option[ITemplata] = {
    Profiler.frame(() => {
      lookupWithImpreciseNameInner(nameS, lookupFilter, true).toList match {
        case List() => None
        case List(only) => Some(only)
        case many => vfail("Too many with name: " + nameS + ":\n" + many.mkString("\n"))
      }
    })
  }

  def fullName: FullNameT[INameT]
}

trait IEnvironmentBox extends IEnvironment {
  def snapshot: IEnvironment
  override def toString: String = {
    "#Environment"
  }
  def globalEnv: GlobalEnvironment

  def fullName: FullNameT[INameT]
}

sealed trait ILookupContext
case object TemplataLookupContext extends ILookupContext
case object ExpressionLookupContext extends ILookupContext

case class GlobalEnvironment(
  functorHelper: FunctorHelper,
  structConstructorMacro: StructConstructorMacro,
  structDropMacro: StructDropMacro,
  structFreeMacro: StructFreeMacro,
  interfaceDropMacro: InterfaceDropMacro,
  interfaceFreeMacro: InterfaceFreeMacro,
  anonymousInterfaceMacro: AnonymousInterfaceMacro,
  nameToStructDefinedMacro: Map[String, IOnStructDefinedMacro],
  nameToInterfaceDefinedMacro: Map[String, IOnInterfaceDefinedMacro],
  nameToImplDefinedMacro: Map[String, IOnImplDefinedMacro],
  nameToFunctionBodyMacro: Map[String, IFunctionBodyMacro],
  // We *dont* search through these in lookupWithName etc.
  // This doesn't just contain the user's things, it can contain generated things
  // like struct constructors, interface constructors, etc.
  // This isn't just packages, structs can have entries here too, because their
  // environments might have things, like a struct's methods might be here.
  // Any particular IEnvironment subclass has a subset of these.
  nameToTopLevelEnvironment: Map[FullNameT[PackageTopLevelNameT], TemplatasStore],
  // Primitives and other builtins
  builtins: TemplatasStore
)

object TemplatasStore {
  def entryMatchesFilter(entry: IEnvEntry, contexts: Set[ILookupContext]): Boolean = {
    entry match {
      case FunctionEnvEntry(_) => contexts.contains(ExpressionLookupContext)
      case ImplEnvEntry(_) => contexts.contains(ExpressionLookupContext)
      case StructEnvEntry(_) => contexts.contains(TemplataLookupContext)
      case InterfaceEnvEntry(_) => contexts.contains(TemplataLookupContext)
      case TemplataEnvEntry(templata) => {
        templata match {
          case PrototypeTemplata(_) => true
          case CoordTemplata(_) => contexts.contains(TemplataLookupContext)
          case CoordListTemplata(_) => contexts.contains(TemplataLookupContext)
          case KindTemplata(_) => contexts.contains(TemplataLookupContext)
          case StructTemplata(_, _) => contexts.contains(TemplataLookupContext)
          case InterfaceTemplata(_, _) => contexts.contains(TemplataLookupContext)
          case RuntimeSizedArrayTemplateTemplata() => contexts.contains(TemplataLookupContext)
          case BooleanTemplata(_) => true
          case FunctionTemplata(_, _) => contexts.contains(ExpressionLookupContext)
          case ImplTemplata(_, _) => contexts.contains(ExpressionLookupContext)
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

  def entryToTemplata(definingEnv: IEnvironment, entry: IEnvEntry): ITemplata = {
    //    vassert(env.fullName != FullName2(PackageCoordinate.BUILTIN, Vector.empty, PackageTopLevelName2()))
    entry match {
      case FunctionEnvEntry(func) => templata.FunctionTemplata(definingEnv, func)
      case StructEnvEntry(struct) => templata.StructTemplata(definingEnv, struct)
      case InterfaceEnvEntry(interface) => templata.InterfaceTemplata(definingEnv, interface)
      case ImplEnvEntry(impl) => templata.ImplTemplata(definingEnv, impl)
      case TemplataEnvEntry(templata) => templata
    }
  }

  def getImpreciseName(interner: Interner, name2: INameT): Option[IImpreciseNameS] = {
    name2 match {
      case CitizenTemplateNameT(humanName) => Some(interner.intern(CodeNameS(humanName)))
      case PrimitiveNameT(humanName) => Some(interner.intern(CodeNameS(humanName)))
      case CitizenNameT(templateName, _) => getImpreciseName(interner, templateName)
      case FunctionTemplateNameT(humanName, _) => Some(interner.intern(CodeNameS(humanName)))
      case FunctionNameT(humanName, _, _) => Some(interner.intern(CodeNameS(humanName)))
      case RuneNameT(r) => Some(interner.intern(RuneNameS(r)))
      case LambdaCitizenNameT(template) => getImpreciseName(interner, template)
      case LambdaCitizenTemplateNameT(loc) => Some(interner.intern(LambdaStructImpreciseNameS(interner.intern(LambdaImpreciseNameS()))))
      case ClosureParamNameT() => Some(interner.intern(ClosureParamNameS()))
      case SelfNameT() => Some(interner.intern(SelfNameS()))
      case ArbitraryNameT() => Some(interner.intern(ArbitraryNameS()))
      case AnonymousSubstructImplNameT() => None
      case AnonymousSubstructConstructorTemplateNameT(CitizenTemplateNameT(humanName)) => {
        Some(interner.intern(CodeNameS(humanName)))
      }
      case AnonymousSubstructTemplateNameT(ctn) => {
        getImpreciseName(interner, ctn).map(x => interner.intern(AnonymousSubstructTemplateImpreciseNameS(x)))
      }
      case AnonymousSubstructConstructorTemplateNameT(AnonymousSubstructTemplateNameT(CitizenTemplateNameT(humanName))) => {
        Some(interner.intern(CodeNameS(humanName)))
      }
      case AnonymousSubstructNameT(interfaceName, _) => getImpreciseName(interner, interfaceName)
      case ImplDeclareNameT(_) => {
        // We shouldn't get here, caller shouldn't pass these in. Should instead get the impl
        // imprecise name from the ImplA or somewhere else.
        vwat()
      }
      case FreeTemplateNameT(codeLocation) => Some(interner.intern(FreeImpreciseNameS()))
      case FreeNameT(templateArgs, kind) => Some(interner.intern(FreeImpreciseNameS()))
      case LambdaTemplateNameT(codeLocation) => Some(interner.intern(LambdaImpreciseNameS()))
      case FreeTemplateNameT(codeLoc) => Some(interner.intern(FreeImpreciseNameS()))
//      case AbstractVirtualFreeTemplateNameT(codeLoc) => Some(interner.intern(VirtualFreeImpreciseNameS()))
      case ForwarderFunctionTemplateNameT(inner, index) => getImpreciseName(interner, inner)
      case ForwarderFunctionNameT(inner, index) => getImpreciseName(interner, inner)
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
  name: FullNameT[INameT],
  // This is the source of truth. Anything in the environment is in here.
  entriesByNameT: Map[INameT, IEnvEntry],
  // This is just an index for quick looking up of things by their imprecise name.
  // Not everything in the above entriesByNameT will have something in here.
  // Vector because multiple things can share an INameS; function overloads.
  entriesByImpreciseNameS: Map[IImpreciseNameS, Vector[IEnvEntry]]
) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  entriesByNameT.values.foreach({
    case FunctionEnvEntry(function) => vassert(function.name.packageCoordinate == name.packageCoord)
    case StructEnvEntry(struct) => vassert(struct.range.file.packageCoordinate == name.packageCoord)
    case InterfaceEnvEntry(interface) => vassert(interface.name.range.file.packageCoordinate == name.packageCoord)
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
        .map({
          case (key, value @ ImplEnvEntry(implA)) => (Some(implA.impreciseName), value)
          case (key, value) => (getImpreciseName(interner, key), value)
        })
        .filter(_._1.nonEmpty)
        .map({ case (key, value) => (key.get, value) })
        .groupBy(_._1)
        .mapValues(_.map(_._2))
    vassert(newEntriesByNameS.size <= newEntries.size)
    val combinedEntriesByNameS =
      entriesByImpreciseNameS ++
        newEntriesByNameS ++
        entriesByImpreciseNameS.keySet.intersect(newEntriesByNameS.keySet)
          .map(key => (key -> (entriesByImpreciseNameS(key) ++ newEntriesByNameS(key))))
          .toMap

    TemplatasStore(name, combinedEntries, combinedEntriesByNameS)
  }

  def addEntry(interner: Interner, name: INameT, entry: IEnvEntry): TemplatasStore = {
    addEntries(interner, Vector(name -> entry))
  }

  private[env] def lookupWithNameInner(
    definingEnv: IEnvironment,

    name: INameT,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata] = {
    entriesByNameT.get(name)
      .filter(entryMatchesFilter(_, lookupFilter))
      .map(entryToTemplata(definingEnv, _))
  }

  private[env] def lookupWithImpreciseNameInner(
    definingEnv: IEnvironment,

    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata] = {
    entriesByImpreciseNameS.getOrElse(name, Vector())
      .filter(entryMatchesFilter(_, lookupFilter))
      .map(entryToTemplata(definingEnv, _))
  }
}

object PackageEnvironment {
  // THIS IS TEMPORARY, it pulls in all global namespaces!
  // See https://github.com/ValeLang/Vale/issues/356
  def makeTopLevelEnvironment(globalEnv: GlobalEnvironment, namespaceName: FullNameT[INameT]): PackageEnvironment[INameT] = {
    PackageEnvironment(
      globalEnv,
      namespaceName,
      globalEnv.nameToTopLevelEnvironment.values.toVector)
  }
}

case class PackageEnvironment[+T <: INameT](
  globalEnv: GlobalEnvironment,
  fullName: FullNameT[T],

  // These are ones that the user imports (or the ancestors that we implicitly import)
  globalNamespaces: Vector[TemplatasStore]
) extends IEnvironment {
  val hash = runtime.ScalaRunTime._hashCode(fullName); override def hashCode(): Int = hash;

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
  Iterable[ITemplata] = {
    globalEnv.builtins.lookupWithNameInner(this, name, lookupFilter) ++
    globalNamespaces.flatMap(ns => {
      val env = PackageEnvironment(globalEnv, ns.name, globalNamespaces)
      ns.lookupWithNameInner(env, name, lookupFilter)
    })
  }

  private[env] override def lookupWithImpreciseNameInner(

    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    globalEnv.builtins.lookupWithImpreciseNameInner(this, name, lookupFilter) ++
    globalNamespaces.flatMap(ns => {
      ns.lookupWithImpreciseNameInner(
        PackageEnvironment(globalEnv, ns.name, globalNamespaces),
        name, lookupFilter)
    })
  }
}


case class CitizenEnvironment[+T <: INameT](
  globalEnv: GlobalEnvironment,
  parentEnv: IEnvironment,
  fullName: FullNameT[T],
  templatas: TemplatasStore
) extends IEnvironment {
  vassert(templatas.name == fullName)

  val hash = runtime.ScalaRunTime._hashCode(fullName); override def hashCode(): Int = hash;
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
  Iterable[ITemplata] = {
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
  Iterable[ITemplata] = {
    val result = templatas.lookupWithImpreciseNameInner(this, name, lookupFilter)
    if (result.nonEmpty && getOnlyNearest) {
      result
    } else {
      result ++ parentEnv.lookupWithImpreciseNameInner(name, lookupFilter, getOnlyNearest)
    }
  }
}
