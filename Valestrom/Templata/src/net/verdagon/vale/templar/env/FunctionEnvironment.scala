package net.verdagon.vale.templar.env

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.LocalVariable1
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.templata.{ITemplata, Queriable2}
import net.verdagon.vale.templar.types.{Coord, StructRef2, Variability}
import net.verdagon.vale.{IProfiler, vassert, vfail, vimpl, vwat}

import scala.collection.immutable.{List, Map, Set}

case class BuildingFunctionEnvironmentWithClosureds(
  parentEnv: IEnvironment,
  fullName: FullName2[BuildingFunctionNameWithClosureds2],
  function: FunctionA,
  variables: List[IVariable2],
  templatas: TemplatasStore
) extends IEnvironment {
  override def getParentEnv(): Option[IEnvironment] = Some(parentEnv)
  override def globalEnv: PackageEnvironment[IName2] = parentEnv.globalEnv
  override def getAllTemplatasWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    templatas.getAllTemplatasWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getNearestTemplataWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    templatas.getNearestTemplataWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getAllTemplatasWithName(profiler: IProfiler, name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    templatas.getAllTemplatasWithName(profiler, this, name, lookupFilter)
  }
  override def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    templatas.getNearestTemplataWithName(this, name, lookupFilter)
  }
}

case class BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs(
  parentEnv: IEnvironment,
  fullName: FullName2[BuildingFunctionNameWithClosuredsAndTemplateArgs2],
  function: FunctionA,
  variables: List[IVariable2],
  templatas: TemplatasStore
) extends IEnvironment {
  override def getParentEnv(): Option[IEnvironment] = Some(parentEnv)
  override def globalEnv: PackageEnvironment[IName2] = parentEnv.globalEnv
  override def getAllTemplatasWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    templatas.getAllTemplatasWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getNearestTemplataWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    templatas.getNearestTemplataWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getAllTemplatasWithName(profiler: IProfiler, name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    templatas.getAllTemplatasWithName(profiler, this, name, lookupFilter)
  }
  override def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    templatas.getNearestTemplataWithName(this, name, lookupFilter)
  }
}

case class FunctionEnvironment(
  // These things are the "environment"; they are the same for every line in a function.
  parentEnv: IEnvironment,
  fullName: FullName2[IFunctionName2], // Includes the name of the function
  function: FunctionA,
  templatas: TemplatasStore,
  maybeReturnType: Option[Coord],

  // The things below are the "state"; they can be different for any given line in a function.
  varCounter: Int,
  locals: List[IVariable2],
  // This can refer to vars in parent environments, see UCRTVPE.
  unstackifieds: Set[FullName2[IVarName2]]

  // We just happen to combine these two things into one FunctionEnvironment.
  // It might even prove useful one day... since the StructDef for a lambda remembers
  // its original environment, a closure can know all the variable IDs and moveds for
  // its containing function at that time.
  // See AENS for some more thoughts on environment vs state.

) extends IEnvironment {
  vassert(fullName.steps.startsWith(parentEnv.fullName.steps))

  vassert(locals == locals.distinct)

  override def getParentEnv(): Option[IEnvironment] = Some(parentEnv)
  override def globalEnv: PackageEnvironment[IName2] = parentEnv.globalEnv

  def addVariables(newVars: List[IVariable2]): FunctionEnvironment = {
    FunctionEnvironment(parentEnv, fullName, function, templatas, maybeReturnType, varCounter, locals ++ newVars, unstackifieds)
  }
  def addVariable(newVar: IVariable2): FunctionEnvironment = {
    FunctionEnvironment(parentEnv, fullName, function, templatas, maybeReturnType, varCounter, locals :+ newVar, unstackifieds)
  }
  def markLocalUnstackified(newUnstackified: FullName2[IVarName2]): FunctionEnvironment = {
    vassert(!getAllUnstackifiedLocals(true).contains(newUnstackified))
    vassert(getAllLocals(true).exists(_.id == newUnstackified))
    // Even if the local belongs to a parent env, we still mark it unstackified here, see UCRTVPE.
    FunctionEnvironment(parentEnv, fullName, function, templatas, maybeReturnType, varCounter, locals, unstackifieds + newUnstackified)
  }
  def nextVarCounter(): (FunctionEnvironment, Int) = {
    (FunctionEnvironment(parentEnv, fullName, function, templatas, maybeReturnType, varCounter + 1, locals, unstackifieds), varCounter)
  }
  // n is how many values to get
  def nextCounters(n: Int): (FunctionEnvironment, List[Int]) = {
    (
      FunctionEnvironment(parentEnv, fullName, function, templatas, maybeReturnType, varCounter + n, locals, unstackifieds),
      (0 until n).map(_ + varCounter).toList)
  }

  def addEntry(useOptimization: Boolean, name: IName2, entry: IEnvEntry): FunctionEnvironment = {
    FunctionEnvironment(
      parentEnv,
      fullName,
      function,
      templatas.addEntry(useOptimization, name, entry),
      maybeReturnType,
      varCounter,
      locals,
      unstackifieds)
  }
  def addEntries(useOptimization: Boolean, newEntries: Map[IName2, List[IEnvEntry]]): FunctionEnvironment = {
    FunctionEnvironment(
      parentEnv,
      fullName,
      function,
      templatas.addEntries(useOptimization, newEntries),
      maybeReturnType,
      varCounter,
      locals,
      unstackifieds)
  }

  override def getAllTemplatasWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    templatas.getAllTemplatasWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getNearestTemplataWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    templatas.getNearestTemplataWithAbsoluteName2(this, name, lookupFilter)
  }
  override def getAllTemplatasWithName(profiler: IProfiler, name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    templatas.getAllTemplatasWithName(profiler, this, name, lookupFilter)
  }
  override def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    templatas.getNearestTemplataWithName(this, name, lookupFilter)
  }

  def getVariable(name: IVarName2): Option[IVariable2] = {
    locals.find(_.id.last == name) match {
      case Some(v) => Some(v)
      case None => {
        parentEnv match {
          case pfe @ FunctionEnvironment(_, _, _, _, _, _, _, _) => pfe.getVariable(name)
          case _ => None
        }
      }
    }
  }

  // Dont have a getAllUnstackifiedLocals or getAllLiveLocals here. We learned that the hard way.
  // See UCRTVPE, child environments would be the ones that know about their unstackifying of locals
  // from parent envs.

  def getAllLocals(includeAncestorEnvs: Boolean): List[ILocalVariable2] = {
    val parentLiveLocals =
      if (includeAncestorEnvs) {
        parentEnv match {
          case parentFuncEnv@FunctionEnvironment(_, _, _, _, _, _, _, _) => parentFuncEnv.getAllLocals(includeAncestorEnvs)
          case _ => List()
        }
      } else {
        List()
      }
    val liveLocals = locals.collect({ case i : ILocalVariable2 => i })
    parentLiveLocals ++ liveLocals
  }

  def getAllUnstackifiedLocals(includeAncestorEnvs: Boolean): List[FullName2[IVarName2]] = {
    val parentUnstackifiedLocals =
      if (includeAncestorEnvs) {
        parentEnv match {
          case parentFuncEnv@FunctionEnvironment(_, _, _, _, _, _, _, _) => parentFuncEnv.getAllUnstackifiedLocals(includeAncestorEnvs)
          case _ => List()
        }
      } else {
        List()
      }
    parentUnstackifiedLocals ++ unstackifieds
  }

  def makeChildEnvironment(newTemplataStore: () => TemplatasStore) = {
    FunctionEnvironment(
      this,
      fullName,
      function,
      newTemplataStore(),
      maybeReturnType,
      varCounter,
      List(),
      Set())
  }

  // No particular reason we don't have an addFunction like PackageEnvironment does
}

case class FunctionEnvironmentBox(var functionEnvironment: FunctionEnvironment) extends IEnvironmentBox {
  override def snapshot: FunctionEnvironment = functionEnvironment
  def parentEnv: IEnvironment = functionEnvironment.parentEnv
  def fullName: FullName2[IFunctionName2] = functionEnvironment.fullName
  def function: FunctionA = functionEnvironment.function
  def templatas: TemplatasStore = functionEnvironment.templatas
  def maybeReturnType: Option[Coord] = functionEnvironment.maybeReturnType
  def varCounter: Int = functionEnvironment.varCounter
  def locals: List[IVariable2] = functionEnvironment.locals
  def unstackifieds: Set[FullName2[IVarName2]] = functionEnvironment.unstackifieds
  override def globalEnv: PackageEnvironment[IName2] = parentEnv.globalEnv

  def setReturnType(returnType: Option[Coord]): Unit = {
    functionEnvironment = functionEnvironment.copy(maybeReturnType = returnType)
  }

  def addVariables(newVars: List[IVariable2]): Unit= {
    functionEnvironment = functionEnvironment.addVariables(newVars)
  }
  def addVariable(newVar: IVariable2): Unit= {
    functionEnvironment = functionEnvironment.addVariable(newVar)
  }
  def markLocalUnstackified(newMoved: FullName2[IVarName2]): Unit= {
    functionEnvironment = functionEnvironment.markLocalUnstackified(newMoved)
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

  def addEntry(useOptimization: Boolean, name: IName2, entry: IEnvEntry): Unit = {
    functionEnvironment = functionEnvironment.addEntry(useOptimization, name, entry)
  }
  def addEntries(useOptimization: Boolean, newEntries: Map[IName2, List[IEnvEntry]]): Unit= {
    functionEnvironment = functionEnvironment.addEntries(useOptimization, newEntries)
  }

  override def getAllTemplatasWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    functionEnvironment.getAllTemplatasWithAbsoluteName2(name, lookupFilter)
  }

  override def getNearestTemplataWithAbsoluteName2(name: IName2, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    functionEnvironment.getNearestTemplataWithAbsoluteName2(name, lookupFilter)
  }

  override def getAllTemplatasWithName(profiler: IProfiler, name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): List[ITemplata] = {
    functionEnvironment.getAllTemplatasWithName(profiler, name, lookupFilter)
  }

  override def getNearestTemplataWithName(name: IImpreciseNameStepA, lookupFilter: Set[ILookupContext]): Option[ITemplata] = {
    functionEnvironment.getNearestTemplataWithName(name, lookupFilter)
  }

  def getVariable(name: IVarName2): Option[IVariable2] = {
    functionEnvironment.getVariable(name)
  }

  def getAllLocals(includeAncestorEnvs: Boolean): List[ILocalVariable2] = {
    functionEnvironment.getAllLocals(includeAncestorEnvs)
  }

  def getAllUnstackifiedLocals(includeAncestorEnvs: Boolean): List[FullName2[IVarName2]] = {
    functionEnvironment.getAllUnstackifiedLocals(includeAncestorEnvs)
  }

  // Gets the effects that this environment had on the outside world (on its parent
  // environments).
  def getEffects(): (Set[FullName2[IVarName2]], Int) = {
    // We may have unstackified outside locals from inside the block, make sure
    // the parent environment knows about that.
    val unstackifiedAncestorLocals = unstackifieds -- locals.map(_.id)

    // We may have made some temporary vars in the block, make sure we don't accidentally reuse their numbers,
    // carry the var counter into the original fate.
    val numVarsMade =
      varCounter -
        (parentEnv match {
          case fe @ FunctionEnvironment(_, _, _, _, _, _, _, _) => fe.varCounter
          case _ => vwat()
        })

    (unstackifiedAncestorLocals, numVarsMade)
  }

  def makeChildEnvironment(newTemplataStore: () => TemplatasStore):
  FunctionEnvironmentBox = {
    FunctionEnvironmentBox(
      functionEnvironment.makeChildEnvironment(newTemplataStore))
  }

  // No particular reason we don't have an addFunction like PackageEnvironment does
}

sealed trait IVariable2 extends Queriable2 {
  def id: FullName2[IVarName2]
  def variability: Variability
  def reference: Coord
}
sealed trait ILocalVariable2 extends IVariable2 {
  def reference: Coord
  def id: FullName2[IVarName2]
}
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
