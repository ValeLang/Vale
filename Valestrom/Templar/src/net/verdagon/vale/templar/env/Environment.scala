package net.verdagon.vale.templar.env

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.TemplatasStore.{entryMatchesFilter, entryToTemplata, getImpreciseName}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.macros.drop._
import net.verdagon.vale.templar.macros.{AnonymousInterfaceMacro, FunctorHelper, IFunctionBodyMacro, IOnImplDefinedMacro, IOnInterfaceDefinedMacro, IOnStructDefinedMacro, StructConstructorMacro}
import net.verdagon.vale.templar.names.{AnonymousSubstructConstructorTemplateNameT, AnonymousSubstructImplNameT, AnonymousSubstructNameT, AnonymousSubstructTemplateNameT, ArbitraryNameT, CitizenNameT, CitizenTemplateNameT, ClosureParamNameT, FreeTemplateNameT, FullNameT, FunctionNameT, FunctionTemplateNameT, INameT, ImplDeclareNameT, LambdaCitizenNameT, LambdaCitizenTemplateNameT, LambdaTemplateNameT, NameTranslator, PackageTopLevelNameT, PrimitiveNameT, RuneNameT, SelfNameT, VirtualFreeTemplateNameT}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.{CodeLocationS, Err, IProfiler, Ok, PackageCoordinate, Result, vassert, vcurious, vfail, vimpl, vwat}

import scala.collection.immutable.{List, Map, Set}


trait IEnvironment {
  override def toString: String = {
    "#Environment"
  }
  override def hashCode(): Int = vfail() // Shouldnt hash these, too big.

  def globalEnv: GlobalEnvironment
//  // These are ones that the user imports (or that we implicitly import from parent packages)
//  def globalNamespaces: Vector[TemplatasStore]
//  // These are likely ones from our parent struct, interface, function, etc.
//  // Nearest ones first.
//  def localNamespaces: List[TemplatasStore]

  private[env] def lookupWithImpreciseNameInner(
    profiler: IProfiler,
    nameS: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata]

  private[env] def lookupWithNameInner(
    profiler: IProfiler,
    nameS: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata]

  def lookupAllWithImpreciseName(
    profiler: IProfiler,
    nameS: IImpreciseNameS,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata] = {
    profiler.newProfile("lookupImprecise", "", () => {
      lookupWithImpreciseNameInner(profiler, nameS, lookupFilter, false)
    })
  }

  def lookupAllWithName(
    profiler: IProfiler,
    nameS: INameT,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata] = {
    profiler.newProfile("lookupPrecise", "", () => {
      lookupWithNameInner(profiler, nameS, lookupFilter, false)
    })
  }

  def lookupNearestWithName(
    profiler: IProfiler,
    nameS: INameT,
    lookupFilter: Set[ILookupContext]):
  Option[ITemplata] = {
    profiler.newProfile("lookupPrecise", "", () => {
      lookupWithNameInner(profiler, nameS, lookupFilter, true).toList match {
        case List() => None
        case List(only) => Some(only)
        case multiple => vfail("Too many with name " + nameS + ": " + multiple)
      }
    })
  }

  def lookupNearestWithImpreciseName(
    profiler: IProfiler,
    nameS: IImpreciseNameS,
    lookupFilter: Set[ILookupContext]):
  Option[ITemplata] = {
    profiler.newProfile("lookupImprecise", "", () => {
      lookupWithImpreciseNameInner(profiler, nameS, lookupFilter, true).toList match {
        case List() => None
        case List(only) => Some(only)
        case _ => vfail("Too many with name: " + nameS)
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

//  def lookupWithImpreciseName(
//    profiler: IProfiler,
//    nameS: IImpreciseNameS,
//    lookupFilter: Set[ILookupContext],
//    getOnlyNearest: Boolean):
//  Iterable[ITemplata]
//
//  def lookupWithName(
//    profiler: IProfiler,
//    nameS: INameT,
//    lookupFilter: Set[ILookupContext],
//    getOnlyNearest: Boolean):
//  Iterable[ITemplata]
//
//  def lookupAllWithImpreciseName(
//    profiler: IProfiler,
//    nameS: IImpreciseNameS,
//    lookupFilter: Set[ILookupContext]):
//  Iterable[ITemplata]
//
//  def lookupAllWithName(
//    profiler: IProfiler,
//    nameS: INameT,
//    lookupFilter: Set[ILookupContext]):
//  Iterable[ITemplata]
//
//  def lookupNearestWithImpreciseName(
//    profiler: IProfiler,
//    nameS: IImpreciseNameS,
//    lookupFilter: Set[ILookupContext]):
//  Option[ITemplata]
//
//  def lookupNearestWithName(
//    profiler: IProfiler,
//    nameS: INameT,
//    lookupFilter: Set[ILookupContext]):
//  Option[ITemplata]


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
//  def parallelLookupWithName(
//    globalEnvironment: GlobalEnvironment,
//    namespaces: Vector[TemplatasStore],
//    profiler: IProfiler,
//    name: INameT,
//    lookupFilter: Set[ILookupContext]): Iterable[ITemplata] = {
//  }
//
//  def parallelLookupWithImpreciseName(
//    globalEnvironment: GlobalEnvironment,
//    namespaces: Vector[TemplatasStore],
//    profiler: IProfiler,
//    name: INameS,
//    lookupFilter: Set[ILookupContext]): Iterable[ITemplata] = {
//    namespaces.flatMap(namespace => {
//      namespace.entriesByImpreciseNameS.getOrElse(name, Vector())
//        .filter(entryMatchesFilter(_, lookupFilter))
//        .map(entryToTemplata(globalEnvironment, _))
//    })
//  }

//  def lookupWithName(
//    requestingEnv: IEnvironment,
//    profiler: IProfiler,
//    name: INameT,
//    lookupFilter: Set[ILookupContext],
//    getOnlyNearest: Boolean):
//  Iterable[ITemplata] = {
//    profiler.childFrame("lookupWithName", () => {
//      if (getOnlyNearest) {
//        localNamespaces match {
//          case local :: rest => {
//            parallelLookupWithName(globalEnvironment, Vector(local), profiler, name, lookupFilter) match {
//              case Vector(entry) => return Some(entry)
//              case Vector() => return lookupWithName(globalEnvironment, rest, globalNamespaces, profiler, name, lookupFilter, getOnlyNearest)
//              case multiple => vfail("Too many things named " + name + ":" + multiple);
//            }
//          }
//          case Nil => {
//            // Look through all the global namespaces in parallel.
//            parallelLookupWithName(globalEnvironment, globalNamespaces.toVector, profiler, name, lookupFilter) match {
//              case Vector(entry) => return Some(entry)
//              case Vector() => None
//              case multiple => vfail("Too many things named " + name + ":" + multiple);
//            }
//          }
//        }
//      } else {
//        localNamespaces match {
//          case local :: rest => {
//            parallelLookupWithName(globalEnvironment, Vector(local), profiler, name, lookupFilter) ++
//              lookupWithName(globalEnvironment, rest, globalNamespaces, profiler, name, lookupFilter, getOnlyNearest)
//          }
//          case Nil => {
//            parallelLookupWithName(globalEnvironment, globalNamespaces.toVector, profiler, name, lookupFilter)
//          }
//        }
//      }
//    })
//  }
//
//  def lookupWithImpreciseName(
//    globalEnvironment: GlobalEnvironment,
//    localNamespaces: List[TemplatasStore],
//    globalNamespaces: Vector[TemplatasStore],
//    profiler: IProfiler,
//    name: INameS,
//    lookupFilter: Set[ILookupContext],
//    getOnlyNearest: Boolean):
//  Iterable[ITemplata] = {
//    profiler.childFrame("lookupWithImpreciseName", () => {
//      if (getOnlyNearest) {
//        localNamespaces match {
//          case local :: rest => {
//            parallelLookupWithImpreciseName(globalEnvironment, Vector(local), profiler, name, lookupFilter) match {
//              case Vector(entry) => return Some(entry)
//              case Vector() => return lookupWithImpreciseName(globalEnvironment, rest, globalNamespaces, profiler, name, lookupFilter, getOnlyNearest)
//              case multiple => vfail("Too many things named " + name + ":" + multiple);
//            }
//          }
//          case Nil => {
//            // Look through all the global namespaces in parallel.
//            parallelLookupWithImpreciseName(globalEnvironment, globalNamespaces.toVector, profiler, name, lookupFilter) match {
//              case Vector(entry) => return Some(entry)
//              case Vector() => return None
//              case multiple => vfail("Too many things named " + name + ":" + multiple);
//            }
//          }
//        }
//      } else {
//        localNamespaces match {
//          case local :: rest => {
//            parallelLookupWithImpreciseName(globalEnvironment, Vector(local), profiler, name, lookupFilter) ++
//              lookupWithImpreciseName(globalEnvironment, rest, globalNamespaces, profiler, name, lookupFilter, getOnlyNearest)
//          }
//          case Nil => {
//            parallelLookupWithImpreciseName(globalEnvironment, globalNamespaces.toVector, profiler, name, lookupFilter)
//          }
//        }
//      }
//    })
//  }

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
          case PermissionTemplata(_) => contexts.contains(TemplataLookupContext)
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
      case FunctionEnvEntry(func) => FunctionTemplata(definingEnv, func)
      case StructEnvEntry(struct) => StructTemplata(definingEnv, struct)
      case InterfaceEnvEntry(interface) => InterfaceTemplata(definingEnv, interface)
      case ImplEnvEntry(impl) => ImplTemplata(definingEnv, impl)
      case TemplataEnvEntry(templata) => templata
    }
  }

  //  def impreciseNamesMatch(nameA: INameS, name2: INameT): Boolean = {
  //    // If something's in these two switch statements, then we've factored them into the main one below.
  //    // When you add something to the main list, make sure you handle all its cases and add it to one of
  //    // these too.
  //    nameA match {
  //      case CodeTypeNameS(_) =>
  //      case GlobalFunctionFamilyNameS(_) =>
  //      case ImplImpreciseNameS(_) =>
  ////      case ImmConcreteDestructorStringNameS() =>
  ////      case ImmInterfaceDestructorStringNameS() =>
  ////      case ImmDropStringNameS() =>
  //      case _ => vimpl()
  //    }
  //    name2 match {
  //      case CitizenTemplateNameT(_, _) =>
  //      case FunctionTemplateNameT(_, _) =>
  //      case PrimitiveNameT(_) =>
  ////      case ReturnRuneS() =>
  ////      case ImplicitRuneS(_, _) =>
  ////      case CodeRuneS(_) =>
  //      case LambdaCitizenNameT(_) =>
  //      case ClosureParamNameT() =>
  //      case FunctionNameT(_, _, _) =>
  ////      case AnonymousSubstructParentInterfaceRuneS() =>
  //      case AnonymousSubstructImplNameT() =>
  ////      case SolverKindRuneS(_) =>
  //      case ImplDeclareNameT(_, _) =>
  ////      case LetImplicitRuneS(_, _) =>
  ////      case MemberRuneS(_) =>
  //      case CitizenNameT(_, _) =>
  ////      case MagicImplicitRuneS(_) =>
  //      case ImmConcreteDestructorTemplateNameT() =>
  //      case ImmInterfaceDestructorTemplateNameT() =>
  //      case ImmDropTemplateNameT() =>
  //      case _ => vimpl()
  //    }
  //    (nameA, name2) match {
  //      case (CodeTypeNameS(humanNameS), CitizenTemplateNameT(humanNameT, _)) => humanNameS == humanNameT
  //      case (CodeTypeNameS(humanNameS), CitizenTemplateNameT(humanNameT, _)) => humanNameS == humanNameT
  //      case (CodeTypeNameS(humanNameS), FunctionTemplateNameT(humanNameT, _)) => humanNameS == humanNameT
  //      case (CodeTypeNameS(humanNameS), PrimitiveNameT(humanNameT)) => humanNameS == humanNameT
  //      case (CodeTypeNameS(humanNameS), CitizenNameT(humanNameT, _)) => humanNameS == humanNameT
  //      case (GlobalFunctionFamilyNameS(humanNameS), FunctionTemplateNameT(humanNameT, _)) => humanNameS == humanNameT
  //      case (GlobalFunctionFamilyNameS(humanNameS), FunctionNameT(humanNameT, _, _)) => humanNameS == humanNameT
  //      case (ImplImpreciseNameS(subCitizenHumanNameS), ImplDeclareNameT(subCitizenHumanNameT, _)) => subCitizenHumanNameS == subCitizenHumanNameT
  ////      case (ImmDropStringNameS(), ImmDropTemplateNameT()) => true
  ////      case (ImmConcreteDestructorStringNameS(), ImmConcreteDestructorTemplateNameT()) => true
  ////      case (ImmInterfaceDestructorStringNameS(), ImmInterfaceDestructorTemplateNameT()) => true
  //      //      case (ImplStringNameS(), AnonymousSubstructImplName2()) => true // not really needed if we use ImplDeclareName?
  //      case _ => false
  //    }
  //  }

  def getImpreciseName(name2: INameT): Option[IImpreciseNameS] = {
    name2 match {
      case CitizenTemplateNameT(humanName) => Some(CodeNameS(humanName))
      case PrimitiveNameT(humanName) => Some(CodeNameS(humanName))
      case CitizenNameT(templateName, _) => getImpreciseName(templateName)
      case FunctionTemplateNameT(humanName, _) => Some(CodeNameS(humanName))
      case FunctionNameT(humanName, _, _) => Some(CodeNameS(humanName))
      case RuneNameT(r) => Some(RuneNameS(r))


//      case DropTemplateNameT() => Some(CodeVarNameS(CallTemplar.DROP_FUNCTION_NAME))
//      case ImmConcreteDestructorTemplateNameT() => Some(ImmConcreteDestructorImpreciseNameS())
//      case ImmInterfaceDestructorTemplateNameT() => Some(ImmInterfaceDestructorImpreciseNameS())
      //      case RuneNameT(ImplicitRuneS(_)) => None
      //      case RuneNameT(LetImplicitRuneS(_)) => None
      //      case RuneNameT(SolverKindRuneS(_)) => None
      //      case RuneNameT(ReturnRuneS()) => None
      //      case RuneNameT(MemberRuneS(_)) => None
      case LambdaCitizenNameT(template) => getImpreciseName(template)
      case LambdaCitizenTemplateNameT(loc) => Some(LambdaStructImpreciseNameS(LambdaImpreciseNameS()))
      case ClosureParamNameT() => Some(ClosureParamNameS())
      case SelfNameT() => Some(SelfNameS())
      case ArbitraryNameT() => Some(ArbitraryNameS())
      //      case AnonymousSubstructParentInterfaceRuneS() => None
      case AnonymousSubstructImplNameT() => None
      case AnonymousSubstructConstructorTemplateNameT(CitizenTemplateNameT(humanName)) => {
        Some(CodeNameS(humanName))
      }
      case AnonymousSubstructTemplateNameT(ctn) => {
        getImpreciseName(ctn).map(AnonymousSubstructTemplateImpreciseNameS)
      }
      case AnonymousSubstructConstructorTemplateNameT(AnonymousSubstructTemplateNameT(CitizenTemplateNameT(humanName))) => {
        Some(CodeNameS(humanName))
      }
      case AnonymousSubstructNameT(interfaceName, _) => getImpreciseName(interfaceName)
//      case AnonymousSubstructNameT(interfaceNameT, _) => {
//        vassert(steps.nonEmpty)
//
//      }
      case ImplDeclareNameT(_) => {
        // We shouldn't get here, caller shouldn't pass these in. Should instead get the impl
        // imprecise name from the ImplA or somewhere else.
        vwat()
      }
      case FreeTemplateNameT(codeLocation) => Some(FreeImpreciseNameS())
      case LambdaTemplateNameT(codeLocation) => Some(LambdaImpreciseNameS())
      case FreeTemplateNameT(codeLoc) => Some(FreeImpreciseNameS())
      case VirtualFreeTemplateNameT(codeLoc) => Some(VirtualFreeImpreciseNameS())
      case other => vimpl(other.toString)
    }
  }

  //  def runesMatch(runeA: IRuneS, rune2: IRuneT): Boolean = {
  //    (runeA, rune2) match {
  //      case (CodeRuneA(nameA), CodeRune2(name2)) => nameA == name2
  //      case (ImplicitRuneA(nameA), ImplicitRune2(name2)) => nameA == name2
  //      case (MemberRuneA(memberIndexA), MemberRune2(memberIndex2)) => memberIndexA == memberIndex2
  //      case (MagicImplicitRuneA(magicParamIndexA), MagicImplicitRune2(magicParamIndex2)) => magicParamIndexA == magicParamIndex2
  //      case (ReturnRuneA(), ReturnRune2()) => true
  //    }
  //  }

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
  override def hashCode(): Int = vcurious()

  entriesByNameT.values.foreach({
    case FunctionEnvEntry(function) => vassert(function.name.packageCoordinate == name.packageCoord)
    case StructEnvEntry(struct) => vassert(struct.range.file.packageCoordinate == name.packageCoord)
    case InterfaceEnvEntry(interface) => vassert(interface.name.range.file.packageCoordinate == name.packageCoord)
    case _ =>
  })

  //  // The above map, indexed by human name. If it has no human name, it won't be in here.
  //  private var entriesByHumanName = Map[String, Vector[IEnvEntry]]()

  def addEntries(newEntriesList: Vector[(INameT, IEnvEntry)]): TemplatasStore = {
    val newEntries = newEntriesList.toMap
    vassert(newEntries.size == newEntriesList.size)

    val oldEntries = entriesByNameT

    val combinedEntries = oldEntries ++ newEntries
    oldEntries.keySet.intersect(newEntries.keySet).foreach(key => {
      vassert(oldEntries(key) == newEntries(key))
    })


//    newEntries.keys.foreach(newEntryName => {
//      val entryWithThisName = combinedEntries(newEntryName)
//      val (unflattenedNumTemplatas, unflattenedNumNonTemplatas) =
//        entriesWithThisName
//          .map({
//            case tee @ TemplataEnvEntry(_) => (1, 0)
//            case other => (0, 1)
//          })
//          .unzip
//      val numTemplatas = unflattenedNumTemplatas.sum
//      val numNonTemplatas = unflattenedNumNonTemplatas.sum
//      // Itd be weird to have two templatas directly in this env, there would be
//      // no way to distinguish them.
//      vassert(numTemplatas <= 1)
//      // We dont want both a templata and a non templata directly in this env,
//      // the templata would always take precedence.
//      vassert(numTemplatas == 0 || numNonTemplatas == 0)
//    })

    val newEntriesByNameS =
      newEntries
        .toVector
        .map({
          case (key, value @ ImplEnvEntry(implA)) => (Some(implA.impreciseName), value)
          case (key, value) => (getImpreciseName(key), value)
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

  def addUnevaluatedFunction(functionA: FunctionA): TemplatasStore = {
    val functionName = NameTranslator.translateFunctionNameToTemplateName(functionA.name)
    addEntry(functionName, FunctionEnvEntry(functionA))
  }


  def addEntry(name: INameT, entry: IEnvEntry): TemplatasStore = {
    addEntries(Vector(name -> entry))
  }

  private[env] def lookupWithNameInner(
    definingEnv: IEnvironment,
    profiler: IProfiler,
    name: INameT,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata] = {
    profiler.childFrame("lookupWithName", () => {
      entriesByNameT.get(name)
        .filter(entryMatchesFilter(_, lookupFilter))
        .map(entryToTemplata(definingEnv, _))
    })
  }

  private[env] def lookupWithImpreciseNameInner(
    definingEnv: IEnvironment,
    profiler: IProfiler,
    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext]):
  Iterable[ITemplata] = {
    entriesByImpreciseNameS.getOrElse(name, Vector())
      .filter(entryMatchesFilter(_, lookupFilter))
      .map(entryToTemplata(definingEnv, _))
  }
}

object PackageEnvironment {
//  // THIS IS TEMPORARY, it pulls in all global namespaces!
//  // See https://github.com/ValeLang/Vale/issues/356
//  def makeTopLevelEnvironment(globalEnv: GlobalEnvironment, packageCoordinate: PackageCoordinate): PackageEnvironment[INameT] = {
//    makeTopLevelEnvironment(
//      globalEnv, FullNameT(packageCoordinate, Vector(), PackageTopLevelNameT()))
//  }
  // THIS IS TEMPORARY, it pulls in all global namespaces!
  // See https://github.com/ValeLang/Vale/issues/356
  def makeTopLevelEnvironment(globalEnv: GlobalEnvironment, namespaceName: FullNameT[INameT]): PackageEnvironment[INameT] = {
    PackageEnvironment(
      globalEnv,
      namespaceName,
      globalEnv.nameToTopLevelEnvironment.values.toVector)
  }

//  def childOf[T <: INameT](env: IEnvironment, newStep: T): PackageEnvironment[T] = {
//    PackageEnvironment(env.globalEnv, env.fullName.addStep(newStep), env.globalNamespaces, env.localNamespaces)
//  }
//  def child[T <: INameT](env: IEnvironment, newName: FullNameT[T]): PackageEnvironment[T] = {
//    vassert(newName.steps.startsWith(env.fullName.steps))
//    PackageEnvironment(env.globalEnv, newName, env.globalNamespaces, env.localNamespaces)
//  }
//  def child[T <: INameT](env: IEnvironment, newName: FullNameT[T], templatas: TemplatasStore): PackageEnvironment[T] = {
//    vassert(newName.steps.startsWith(env.fullName.steps))
//    vassert(newName == templatas.namespaceName)
//    PackageEnvironment(env.globalEnv, newName, env.globalNamespaces, templatas :: env.localNamespaces)
//  }
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

//  override def importGlobalNamespace(importee: TemplatasStore): PackageEnvironment[T] = {
//    PackageEnvironment(globalEnv, fullName, globalNamespaces :+ importee, localNamespaces)
//  }

  private[env] override def lookupWithNameInner(
    profiler: IProfiler,
    name: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    globalEnv.builtins.lookupWithNameInner(this, profiler, name, lookupFilter) ++
    globalNamespaces.flatMap(ns => {
      val env = PackageEnvironment(globalEnv, ns.name, globalNamespaces)
      ns.lookupWithNameInner(env, profiler, name, lookupFilter)
    })
  }

  private[env] override def lookupWithImpreciseNameInner(
    profiler: IProfiler,
    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    globalEnv.builtins.lookupWithImpreciseNameInner(this, profiler, name, lookupFilter) ++
    globalNamespaces.flatMap(ns => {
      ns.lookupWithImpreciseNameInner(
        PackageEnvironment(globalEnv, ns.name, globalNamespaces),
        profiler, name, lookupFilter)
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

  //  override def importGlobalNamespace(importee: TemplatasStore): PackageEnvironment[T] = {
  //    PackageEnvironment(globalEnv, fullName, globalNamespaces :+ importee, localNamespaces)
  //  }

  private[env] override def lookupWithNameInner(
    profiler: IProfiler,
    name: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    val result = templatas.lookupWithNameInner(this, profiler, name, lookupFilter)
    if (result.nonEmpty && getOnlyNearest) {
      result
    } else {
      result ++ parentEnv.lookupWithNameInner(profiler, name, lookupFilter, getOnlyNearest)
    }
  }

  private[env] override def lookupWithImpreciseNameInner(
    profiler: IProfiler,
    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    val result = templatas.lookupWithImpreciseNameInner(this, profiler, name, lookupFilter)
    if (result.nonEmpty && getOnlyNearest) {
      result
    } else {
      result ++ parentEnv.lookupWithImpreciseNameInner(profiler, name, lookupFilter, getOnlyNearest)
    }
  }
}
