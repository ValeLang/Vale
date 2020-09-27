package net.verdagon.vale.templar.env

import com.jprofiler.api.probe.embedded.{Payload, PayloadProbe, Split, SplitProbe}
import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.{vassert, vfail, vimpl, vwat}

import scala.collection.immutable.{List, Map}

trait IEnvironment {
  override def toString: String = {
    "#Environment"
  }
  def entries: Map[IName2, List[IEnvEntry]]
  def getParentEnv(): Option[IEnvironment]
  def globalEnv: NamespaceEnvironment[IName2]
  def getAllTemplatasWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata]
  def getNearestTemplataWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): Option[ITemplata]
  def getAllTemplatasWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata]
  def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata]
  def fullName: FullName2[IName2]
}

trait IEnvironmentBox {
  def snapshot: IEnvironment
  override def toString: String = {
    "#Environment"
  }
  def globalEnv: NamespaceEnvironment[IName2]
  def getAllTemplatasWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata]
  def getNearestTemplataWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): Option[ITemplata]
  def getAllTemplatasWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata]
  def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata]
  def fullName: FullName2[IName2]
}

sealed trait ILookupContext
case object TemplataLookupContext extends ILookupContext
case object ExpressionLookupContext extends ILookupContext

case class NamespaceEnvironment[+T <: IName2](
  maybeParentEnv: Option[IEnvironment],
  fullName: FullName2[T],
  entries: Map[IName2, List[IEnvEntry]]
) extends IEnvironment {
  maybeParentEnv match {
    case None =>
    case Some(parentEnv) => vassert(fullName.steps.startsWith(parentEnv.fullName.steps))
  }

  override def globalEnv: NamespaceEnvironment[IName2] = {
    maybeParentEnv match {
      case None => this
      case Some(parentEnv) => parentEnv.globalEnv
    }
  }

  override def getAllTemplatasWithAbsoluteName2(
    name: IName2,
    lookupFilter: Set[ILookupContext]):
  List[ITemplata] = {
    EnvironmentUtils.getAllTemplatasWithAbsoluteName2(this, name, lookupFilter)
  }

  override def getNearestTemplataWithAbsoluteName2(
    name: IName2,
    lookupFilter: Set[ILookupContext]):
  Option[ITemplata] = {
    EnvironmentUtils.getNearestTemplataWithAbsoluteName2(this, name, lookupFilter)
  }

  override def getAllTemplatasWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    EnvironmentUtils.getAllTemplatasWithName(this, name, lookupFilter)
  }

  override def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    EnvironmentUtils.getNearestTemplataWithName(this, name, lookupFilter)
  }

  def addUnevaluatedFunction(
    function: FunctionA
  ): NamespaceEnvironment[T] = {
    NamespaceEnvironment(
      maybeParentEnv,
      fullName,
      EnvironmentUtils.addUnevaluatedFunction(entries, function))
  }

  def addEntry(name: IName2, entry: IEnvEntry): NamespaceEnvironment[T] = {
    NamespaceEnvironment(
      maybeParentEnv,
      fullName,
      EnvironmentUtils.addEntry(entries, name, entry))
  }

  def addEntries(newEntries: Map[IName2, List[IEnvEntry]]): NamespaceEnvironment[T] = {
    NamespaceEnvironment(
      maybeParentEnv,
      fullName,
      EnvironmentUtils.addEntries(entries, newEntries))
  }

  override def getParentEnv(): Option[IEnvironment] = maybeParentEnv
}

object EnvironmentUtils {
  def entryToTemplata(env: IEnvironment, entry: IEnvEntry): ITemplata = {
    entry match {
      case FunctionEnvEntry(func) => {
        FunctionTemplata(env, func)
      }
      case StructEnvEntry(struct) => {
        StructTemplata(NamespaceEnvironment(Some(env), env.fullName, Map()), struct)
      }
      case InterfaceEnvEntry(interface) => {
        InterfaceTemplata(NamespaceEnvironment(Some(env), env.fullName, Map()), interface)
      }
      case ImplEnvEntry(impl) => ImplTemplata(env, impl)
      case TemplataEnvEntry(templata) => templata
    }
  }

  def addEntries(
      oldEntries: Map[IName2, List[IEnvEntry]],
      newEntries: Map[IName2, List[IEnvEntry]]):
  Map[IName2, List[IEnvEntry]] = {
    val combinedEntries =
      oldEntries ++
        newEntries ++
        oldEntries.keySet.intersect(newEntries.keySet)
          .map(key => (key -> (oldEntries(key) ++ newEntries(key))))
          .toMap

    newEntries.keys.foreach(newEntryName => {
      val entriesWithThisName = combinedEntries(newEntryName)
      val (unflattenedNumTemplatas, unflattenedNumNonTemplatas) =
        entriesWithThisName
          .map({
            case tee @ TemplataEnvEntry(_) => (1, 0)
            case other => (0, 1)
          })
          .unzip
      val numTemplatas = unflattenedNumTemplatas.sum
      val numNonTemplatas = unflattenedNumNonTemplatas.sum
      // Itd be weird to have two templatas directly in this env, there would be
      // no way to distinguish them.
      vassert(numTemplatas <= 1)
      // We dont want both a templata and a non templata directly in this env,
      // the templata would always take precedence.
      vassert(numTemplatas == 0 || numNonTemplatas == 0)
    })

    combinedEntries
  }

  def addEntry(
      oldEntries: Map[IName2, List[IEnvEntry]],
      name: IName2,
      entry: IEnvEntry):
  Map[IName2, List[IEnvEntry]] = {
    addEntries(oldEntries, Map(name -> List(entry)))
  }

  def addUnevaluatedFunction(
    oldEntries: Map[IName2, List[IEnvEntry]],
    functionA: FunctionA
  ): Map[IName2, List[IEnvEntry]] = {
    val functionName = NameTranslator.translateFunctionNameToTemplateName(functionA.name)

    addEntry(oldEntries, functionName, FunctionEnvEntry(functionA))
  }


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
          case KindTemplata(_) => contexts.contains(TemplataLookupContext)
          case StructTemplata(_, _) => contexts.contains(TemplataLookupContext)
          case InterfaceTemplata(_, _) => contexts.contains(TemplataLookupContext)
          case ArrayTemplateTemplata() => contexts.contains(TemplataLookupContext)
          case BooleanTemplata(_) => true
          case FunctionTemplata(_, _) => contexts.contains(ExpressionLookupContext)
          case ImplTemplata(_, _) => contexts.contains(ExpressionLookupContext)
          case IntegerTemplata(_) => true
          case LocationTemplata(_) => contexts.contains(TemplataLookupContext)
          case MutabilityTemplata(_) => contexts.contains(TemplataLookupContext)
          case OwnershipTemplata(_) => contexts.contains(TemplataLookupContext)
          case PermissionTemplata(_) => contexts.contains(TemplataLookupContext)
          case VariabilityTemplata(_) => contexts.contains(TemplataLookupContext)
          case ExternImplTemplata(_, _) => contexts.contains(TemplataLookupContext)
          case ExternFunctionTemplata(_) => contexts.contains(ExpressionLookupContext)
        }
      }
    }
  }

  def impreciseNamesMatch(nameA: IImpreciseNameStepA, name2: IName2): Boolean = {
    // If something's in these two switch statements, then we've factored them into the main one below.
    // When you add something to the main list, make sure you handle all its cases and add it to one of
    // these too.
    nameA match {
      case CodeTypeNameA(_) =>
      case GlobalFunctionFamilyNameA(_) =>
      case ImplImpreciseNameA() =>
      case ImmConcreteDestructorImpreciseNameA() =>
      case ImmInterfaceDestructorImpreciseNameA() =>
      case ImmDropImpreciseNameA() =>
      case _ => vimpl()
    }
    name2 match {
      case CitizenTemplateName2(_, _) =>
      case FunctionTemplateName2(_, _) =>
      case PrimitiveName2(_) =>
      case ReturnRune2() =>
      case ImplicitRune2(_, _) =>
      case CodeRune2(_) =>
      case LambdaCitizenName2(_) =>
      case ClosureParamName2() =>
      case FunctionName2(_, _, _) =>
      case AnonymousSubstructParentInterfaceRune2() =>
      case AnonymousSubstructImplName2() =>
      case SolverKindRune2(_) =>
      case ImplDeclareName2(_) =>
      case LetImplicitRune2(_, _) =>
      case MemberRune2(_) =>
      case CitizenName2(_, _) =>
      case MagicImplicitRune2(_) =>
      case ImmConcreteDestructorTemplateName2() =>
      case ImmInterfaceDestructorTemplateName2() =>
      case ImmDropTemplateName2() =>
      case _ => vimpl()
    }
    (nameA, name2) match {
      case (CodeTypeNameA(humanNameA), CitizenTemplateName2(humanNameT, _)) => humanNameA == humanNameT
      case (CodeTypeNameA(humanNameA), CitizenTemplateName2(humanNameT, _)) => humanNameA == humanNameT
      case (CodeTypeNameA(humanNameA), FunctionTemplateName2(humanNameT, _)) => humanNameA == humanNameT
      case (CodeTypeNameA(humanNameA), PrimitiveName2(humanNameT)) => humanNameA == humanNameT
      case (CodeTypeNameA(humanNameA), CitizenName2(humanNameT, _)) => humanNameA == humanNameT
      case (GlobalFunctionFamilyNameA(humanNameA), FunctionTemplateName2(humanNameT, _)) => humanNameA == humanNameT
      case (GlobalFunctionFamilyNameA(humanNameA), FunctionName2(humanNameT, _, _)) => humanNameA == humanNameT
      case (ImplImpreciseNameA(), ImplDeclareName2(_)) => true
      case (ImmDropImpreciseNameA(), ImmDropTemplateName2()) => true
      case (ImmConcreteDestructorImpreciseNameA(), ImmConcreteDestructorTemplateName2()) => true
      case (ImmInterfaceDestructorImpreciseNameA(), ImmInterfaceDestructorTemplateName2()) => true
//      case (ImplImpreciseNameA(), AnonymousSubstructImplName2()) => true // not really needed if we use ImplDeclareName?
      case _ => false
    }
  }

//  def runesMatch(runeA: IRuneA, rune2: IRune2): Boolean = {
//    (runeA, rune2) match {
//      case (CodeRuneA(nameA), CodeRune2(name2)) => nameA == name2
//      case (ImplicitRuneA(nameA), ImplicitRune2(name2)) => nameA == name2
//      case (MemberRuneA(memberIndexA), MemberRune2(memberIndex2)) => memberIndexA == memberIndex2
//      case (MagicImplicitRuneA(magicParamIndexA), MagicImplicitRune2(magicParamIndex2)) => magicParamIndexA == magicParamIndex2
//      case (ReturnRuneA(), ReturnRune2()) => true
//    }
//  }

  def codeLocationsMatch(codeLocationA: CodeLocationS, codeLocation2: CodeLocation2): Boolean = {
    val CodeLocationS(lineS, charS) = codeLocationA
    val CodeLocation2(line2, char2) = codeLocation2
    lineS == line2 && charS == char2
  }


  def getAllTemplatasWithAbsoluteName2(from: IEnvironment, name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    from.entries
      .get(name)
      .toList
      .flatten
      .filter(EnvironmentUtils.entryMatchesFilter(_, lookupFilter))
      .map(EnvironmentUtils.entryToTemplata(from, _)) ++
    from.getParentEnv().toList.flatMap(_.getAllTemplatasWithAbsoluteName2(name, lookupFilter))
  }

  def getNearestTemplataWithAbsoluteName2(
      from: IEnvironment,
      name: IName2,
      lookupFilter: Set[ILookupContext]):
  Option[ITemplata] = {
    from.entries
      .get(name)
      .toList
      .flatten
      .filter(EnvironmentUtils.entryMatchesFilter(_, lookupFilter)) match {
      case List(entry) => Some(EnvironmentUtils.entryToTemplata(from, entry))
      case List() => from.getParentEnv().flatMap(_.getNearestTemplataWithAbsoluteName2(name, lookupFilter))
      case multiple => vfail("Too many things named " + name + ":" + multiple);
    }
  }

  def getAllTemplatasWithName(
    from: IEnvironment,
    name: IImpreciseNameStepA,
    lookupFilter: Set[ILookupContext]):
  List[ITemplata] = {
    from.entries
      .filter({ case (key, _) => EnvironmentUtils.impreciseNamesMatch(name, key) })
      .values
      .toList
      .flatten
      .filter(EnvironmentUtils.entryMatchesFilter(_, lookupFilter))
      .map(x => EnvironmentUtils.entryToTemplata(from, x))
      .toList ++
      from.getParentEnv().toList.flatMap(_.getAllTemplatasWithName(name, lookupFilter))
  }

  def getNearestTemplataWithName(
      from: IEnvironment,
      name: IImpreciseNameStepA,
      lookupFilter: Set[ILookupContext]):
  Option[ITemplata] = {
    from.entries
      .filter({ case (key, _) => EnvironmentUtils.impreciseNamesMatch(name, key) })
      .values
      .flatten
      .filter(EnvironmentUtils.entryMatchesFilter(_, lookupFilter)) match {
      case List(entry) => Some(EnvironmentUtils.entryToTemplata(from, entry))
      case List() => from.getParentEnv().flatMap(_.getNearestTemplataWithName(name, lookupFilter))
      case multiple => vfail("Too many things named " + name + ":" + multiple);
    }
  }
}
