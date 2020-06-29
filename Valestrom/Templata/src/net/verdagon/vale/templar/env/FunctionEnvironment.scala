package net.verdagon.vale.templar.env

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.LocalVariable1
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.{ITemplata, Queriable2}
import net.verdagon.vale.templar.types.{Coord, StructRef2, Variability}
import net.verdagon.vale.{vassert, vfail, vimpl}

import scala.collection.immutable.{List, Map, Set}

case class BuildingFunctionEnvironmentWithClosureds(
  parentEnv: IEnvironment,
  fullName: FullName2[BuildingFunctionNameWithClosureds2],
  function: FunctionA,
  variables: List[IVariable2],
  entries: Map[IName2, List[IEnvEntry]]
) extends IEnvironment {
  override def getParentEnv(): Option[IEnvironment] = Some(parentEnv)
  override def globalEnv: NamespaceEnvironment[IName2] = parentEnv.globalEnv
  override def getAllTemplatasWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    EnvironmentUtils.getAllTemplatasWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getNearestTemplataWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    EnvironmentUtils.getNearestTemplataWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getAllTemplatasWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    EnvironmentUtils.getAllTemplatasWithName(this, name, lookupFilter)
  }
  override def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    EnvironmentUtils.getNearestTemplataWithName(this, name, lookupFilter)
  }
}

case class BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs(
  parentEnv: IEnvironment,
  fullName: FullName2[BuildingFunctionNameWithClosuredsAndTemplateArgs2],
  function: FunctionA,
  variables: List[IVariable2],
  entries: Map[IName2, List[IEnvEntry]]
) extends IEnvironment {
  override def getParentEnv(): Option[IEnvironment] = Some(parentEnv)
  override def globalEnv: NamespaceEnvironment[IName2] = parentEnv.globalEnv
  override def getAllTemplatasWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    EnvironmentUtils.getAllTemplatasWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getNearestTemplataWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    EnvironmentUtils.getNearestTemplataWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getAllTemplatasWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    EnvironmentUtils.getAllTemplatasWithName(this, name, lookupFilter)
  }
  override def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    EnvironmentUtils.getNearestTemplataWithName(this, name, lookupFilter)
  }
}

case class FunctionEnvironment(
  // These things are the "environment"; they are the same for every line in a function.
  parentEnv: IEnvironment,
  fullName: FullName2[IFunctionName2], // Includes the name of the function
  function: FunctionA,
  entries: Map[IName2, List[IEnvEntry]],
  maybeReturnType: Option[Coord],

  // The scout information for locals for this block and all parent blocks in this function.
  scoutedLocals: List[LocalVariableA],

  // The things below are the "state"; they can be different for any given line in a function.
  varCounter: Int,
  variables: List[IVariable2],
  moveds: Set[FullName2[IVarName2]]

  // We just happen to combine these two things into one FunctionEnvironment.
  // It might even prove useful one day... since the StructDef for a lambda remembers
  // its original environment, a closure can know all the variable IDs and moveds for
  // its containing function at that time.
  // See AENS for some more thoughts on environment vs state.

) extends IEnvironment {
  vassert(fullName.steps.startsWith(parentEnv.fullName.steps))

  vassert(scoutedLocals == scoutedLocals.distinct)
  vassert(variables == variables.distinct)

  override def getParentEnv(): Option[IEnvironment] = Some(parentEnv)
  override def globalEnv: NamespaceEnvironment[IName2] = parentEnv.globalEnv

  def addScoutedLocals(newScoutedLocals: List[LocalVariableA]): FunctionEnvironment = {
    FunctionEnvironment(parentEnv, fullName, function, entries, maybeReturnType, scoutedLocals ++ newScoutedLocals, varCounter, variables, moveds)
  }
  def addVariables(newVars: List[IVariable2]): FunctionEnvironment = {
    FunctionEnvironment(parentEnv, fullName, function, entries, maybeReturnType, scoutedLocals, varCounter, variables ++ newVars, moveds)
  }
  def addVariable(newVar: IVariable2): FunctionEnvironment = {
    FunctionEnvironment(parentEnv, fullName, function, entries, maybeReturnType, scoutedLocals, varCounter, variables :+ newVar, moveds)
  }
  def markVariablesMoved(newMoveds: Set[FullName2[IVarName2]]): FunctionEnvironment = {
    newMoveds.foldLeft(this)({
      case (intermediateFate, newMoved) => intermediateFate.markVariableMoved(newMoved)
    })
  }
  def markVariableMoved(newMoved: FullName2[IVarName2]): FunctionEnvironment = {
    if (variables.exists(_.id == newMoved)) {
      FunctionEnvironment(parentEnv, fullName, function, entries, maybeReturnType, scoutedLocals, varCounter, variables, moveds + newMoved)
    } else {
      val parentFuncEnv =
        parentEnv match { case f @ FunctionEnvironment(_, _, _, _, _, _, _, _, _) => f case _ => vfail() }
      val newParent = parentFuncEnv.markVariableMoved(newMoved)
      FunctionEnvironment(newParent, fullName, function, entries, maybeReturnType, scoutedLocals, varCounter, variables, moveds)
    }
  }
  def nextVarCounter(): (FunctionEnvironment, Int) = {
    (FunctionEnvironment(parentEnv, fullName, function, entries, maybeReturnType, scoutedLocals, varCounter + 1, variables, moveds), varCounter)
  }
  // n is how many values to get
  def nextCounters(n: Int): (FunctionEnvironment, List[Int]) = {
    (
      FunctionEnvironment(parentEnv, fullName, function, entries, maybeReturnType, scoutedLocals, varCounter + n, variables, moveds),
      (0 until n).map(_ + varCounter).toList)
  }

  def addEntry(name: IName2, entry: IEnvEntry): FunctionEnvironment = {
    FunctionEnvironment(
      parentEnv,
      fullName,
      function,
      EnvironmentUtils.addEntry(entries, name, entry),
      maybeReturnType,
      scoutedLocals,
      varCounter,
      variables,
      moveds)
  }
  def addEntries(newEntries: Map[IName2, List[IEnvEntry]]): FunctionEnvironment = {
    FunctionEnvironment(
      parentEnv,
      fullName,
      function,
      EnvironmentUtils.addEntries(entries, newEntries),
      maybeReturnType,
      scoutedLocals,
      varCounter,
      variables,
      moveds)
  }

  override def getAllTemplatasWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    EnvironmentUtils.getAllTemplatasWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getNearestTemplataWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    EnvironmentUtils.getNearestTemplataWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getAllTemplatasWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    EnvironmentUtils.getAllTemplatasWithName(this, name, lookupFilter)
  }
  override def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    EnvironmentUtils.getNearestTemplataWithName(this, name, lookupFilter)
  }

  def getVariable(name: IVarName2): Option[IVariable2] = {
    variables.find(_.id.last == name) match {
      case Some(v) => Some(v)
      case None => {
        parentEnv match {
          case pfe @ FunctionEnvironment(_, _, _, _, _, _, _, _, _) => pfe.getVariable(name)
          case _ => None
        }
      }
    }
  }

  def getAllLiveLocals(): List[ILocalVariable2] = {
    val parentLiveLocals =
      parentEnv match {
        case parentFuncEnv @ FunctionEnvironment(_, _, _, _, _, _, _, _, _) => parentFuncEnv.getAllLiveLocals()
        case _ => List()
      }
    val liveLocals =
      variables.flatMap({
        case i : ILocalVariable2 => if (!moveds.contains(i.id)) List(i) else List()
        case _ => List()
      })
    parentLiveLocals ++ liveLocals
  }

  // No particular reason we don't have an addFunction like NamespaceEnvironment does
}

case class FunctionEnvironmentBox(var functionEnvironment: FunctionEnvironment) extends IEnvironmentBox {
  override def snapshot: FunctionEnvironment = functionEnvironment
  def parentEnv: IEnvironment = functionEnvironment.parentEnv
  def fullName: FullName2[IFunctionName2] = functionEnvironment.fullName
  def function: FunctionA = functionEnvironment.function
  def entries: Map[IName2, List[IEnvEntry]] = functionEnvironment.entries
  def maybeReturnType: Option[Coord] = functionEnvironment.maybeReturnType
  def scoutedLocals: List[LocalVariableA] = functionEnvironment.scoutedLocals
  def varCounter: Int = functionEnvironment.varCounter
  def variables: List[IVariable2] = functionEnvironment.variables
  def moveds: Set[FullName2[IVarName2]] = functionEnvironment.moveds
  override def globalEnv: NamespaceEnvironment[IName2] = parentEnv.globalEnv

  def setReturnType(returnType: Option[Coord]): Unit = {
    functionEnvironment = functionEnvironment.copy(maybeReturnType = returnType)
  }

  def setFullName(fullName: FullName2[IFunctionName2]): Unit = {
    functionEnvironment = functionEnvironment.copy(fullName = fullName)
  }

  def addScoutedLocals(newScoutedLocals: List[LocalVariableA]): Unit = {
    functionEnvironment = functionEnvironment.addScoutedLocals(newScoutedLocals)
  }
  def addVariables(newVars: List[IVariable2]): Unit= {
    functionEnvironment = functionEnvironment.addVariables(newVars)
  }
  def addVariable(newVar: IVariable2): Unit= {
    functionEnvironment = functionEnvironment.addVariable(newVar)
  }
  def markVariablesMoved(newMoveds: Set[FullName2[IVarName2]]): Unit= {
    functionEnvironment = functionEnvironment.markVariablesMoved(newMoveds)
  }
  def markVariableMoved(newMoved: FullName2[IVarName2]): Unit= {
    functionEnvironment = functionEnvironment.markVariableMoved(newMoved)
  }
  def nextVarCounter(): Int = {
    val (newFunctionEnvironment, varCounter) = functionEnvironment.nextVarCounter()
    functionEnvironment = newFunctionEnvironment
    varCounter
  }
  // n is how many values to get
  def nextCounters(n: Int): List[Int] = {
    val (newFunctionEnvironment, counters) = functionEnvironment.nextCounters(n)
    functionEnvironment = newFunctionEnvironment
    counters
  }

  def addEntry(name: IName2, entry: IEnvEntry): Unit = {
    functionEnvironment = functionEnvironment.addEntry(name, entry)
  }
  def addEntries(newEntries: Map[IName2, List[IEnvEntry]]): Unit= {
    functionEnvironment = functionEnvironment.addEntries(newEntries)
  }

  override def getAllTemplatasWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    functionEnvironment.getAllTemplatasWithAbsoluteName2(name, lookupFilter)
  }

  override def getNearestTemplataWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    functionEnvironment.getNearestTemplataWithAbsoluteName2(name, lookupFilter)
  }

  override def getAllTemplatasWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    functionEnvironment.getAllTemplatasWithName(name, lookupFilter)
  }

  override def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    functionEnvironment.getNearestTemplataWithName(name, lookupFilter)
  }

  def getVariable(name: IVarName2): Option[IVariable2] = {
    functionEnvironment.getVariable(name)
  }

  def getAllLiveLocals(): List[ILocalVariable2] = {
    functionEnvironment.getAllLiveLocals()
  }

  // No particular reason we don't have an addFunction like NamespaceEnvironment does
}

sealed trait IVariable2 extends Queriable2 {
  def id: FullName2[IVarName2]
  def variability: Variability
  def reference: Coord
}
sealed trait ILocalVariable2 extends IVariable2
// Why the difference between reference and addressible:
// If we mutate/move a variable from inside a closure, we need to put
// the local's address into the struct. But, if the closures don't
// mutate/move, then we could just put a regular reference in the struct.
// Lucky for us, the parser figured out if any of our child closures did
// any mutates/moves/borrows.
case class AddressibleLocalVariable2(
  id: FullName2[IVarName2],
  variability: Variability,
  reference: Coord
) extends ILocalVariable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ id.all(func) ++ variability.all(func) ++ reference.all(func)
  }
}
case class ReferenceLocalVariable2(
  id: FullName2[IVarName2],
  variability: Variability,
  reference: Coord
) extends ILocalVariable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ id.all(func) ++ variability.all(func) ++ reference.all(func)
  }
}
case class AddressibleClosureVariable2(
  id: FullName2[IVarName2],
  closuredVarsStructType: StructRef2,
  variability: Variability,
  reference: Coord
) extends IVariable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ id.all(func) ++ closuredVarsStructType.all(func) ++ variability.all(func) ++ reference.all(func)
  }
}
case class ReferenceClosureVariable2(
  id: FullName2[IVarName2],
  closuredVarsStructType: StructRef2,
  variability: Variability,
  reference: Coord
) extends IVariable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ id.all(func) ++ closuredVarsStructType.all(func) ++ variability.all(func) ++ reference.all(func)
  }
}
