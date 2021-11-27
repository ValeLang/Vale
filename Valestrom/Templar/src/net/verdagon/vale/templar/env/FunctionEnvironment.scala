package net.verdagon.vale.templar.env

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.{BlockSE, IImpreciseNameS, INameS, LocalS}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.names.{BuildingFunctionNameWithClosuredsAndTemplateArgsT, BuildingFunctionNameWithClosuredsT, FullNameT, IFunctionNameT, INameT, IVarNameT}
import net.verdagon.vale.templar.templata.ITemplata
import net.verdagon.vale.templar.types.{CoordT, StructTT, VariabilityT}
import net.verdagon.vale.{IProfiler, vassert, vcurious, vfail, vimpl, vwat}

import scala.collection.immutable.{List, Map, Set}

case class BuildingFunctionEnvironmentWithClosureds(
  globalEnv: GlobalEnvironment,
  parentEnv: IEnvironment,
  fullName: FullNameT[BuildingFunctionNameWithClosuredsT],
  templatas: TemplatasStore,
  function: FunctionA,
  variables: Vector[IVariableT]
) extends IEnvironment {

  val hash = runtime.ScalaRunTime._hashCode(fullName); override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[IEnvironment]) {
      return false
    }
    return fullName.equals(obj.asInstanceOf[IEnvironment].fullName)
  }


  private[env] override def lookupWithNameInner(
    profiler: IProfiler,
    name: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    EnvironmentHelper.lookupWithNameInner(
      this, templatas, parentEnv, profiler, name, lookupFilter, getOnlyNearest)
  }

  private[env] override def lookupWithImpreciseNameInner(
    profiler: IProfiler,
    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    EnvironmentHelper.lookupWithImpreciseNameInner(
      this, templatas, parentEnv, profiler, name, lookupFilter, getOnlyNearest)
  }
}

case class BuildingFunctionEnvironmentWithClosuredsAndTemplateArgs(
  globalEnv: GlobalEnvironment,
  parentEnv: IEnvironment,
  fullName: FullNameT[BuildingFunctionNameWithClosuredsAndTemplateArgsT],
  templatas: TemplatasStore,
  function: FunctionA,
  variables: Vector[IVariableT]
) extends IEnvironment {

  val hash = runtime.ScalaRunTime._hashCode(fullName); override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[IEnvironment]) {
      return false
    }
    return fullName.equals(obj.asInstanceOf[IEnvironment].fullName)
  }


  private[env] override def lookupWithNameInner(
    profiler: IProfiler,
    name: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    EnvironmentHelper.lookupWithNameInner(
      this, templatas, parentEnv, profiler, name, lookupFilter, getOnlyNearest)
  }

  private[env] override def lookupWithImpreciseNameInner(
    profiler: IProfiler,
    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    EnvironmentHelper.lookupWithImpreciseNameInner(
      this, templatas, parentEnv, profiler, name, lookupFilter, getOnlyNearest)
  }

}

case class FunctionEnvironment(
  // These things are the "environment"; they are the same for every line in a function.
  globalEnv: GlobalEnvironment,
  // This points to the environment containing the function, not parent blocks, see WTHPFE.
  parentEnv: IEnvironment,
  fullName: FullNameT[IFunctionNameT], // Includes the name of the function

  templatas: TemplatasStore,

  function: FunctionA,
  maybeReturnType: Option[CoordT],

  // Eventually we might have a list of imported environments here, pointing at the
  // environments in the global environment.

  // This is stuff that's not state, but could be different line-to-line.
  // The containing Block, useful for looking up LocalS for any locals we're making.
  containingBlockS: Option[BlockSE],

  // The things below are the "state"; they can be different for any given line in a function.
  // This contains locals from parent blocks, see WTHPFE.
  declaredLocals: Vector[IVariableT],
  // This can refer to vars in parent environments, see UCRTVPE.
  unstackifiedLocals: Set[FullNameT[IVarNameT]]
) extends IEnvironment {
  val hash = runtime.ScalaRunTime._hashCode(fullName); override def hashCode(): Int = hash;

  vassert(declaredLocals == declaredLocals.distinct)

  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[IEnvironment]) {
      return false
    }
    return fullName.equals(obj.asInstanceOf[IEnvironment].fullName)
  }

  def addVariables(newVars: Vector[IVariableT]): FunctionEnvironment = {
    FunctionEnvironment(globalEnv, parentEnv, fullName, templatas, function, maybeReturnType, containingBlockS, declaredLocals ++ newVars, unstackifiedLocals)
  }
  def addVariable(newVar: IVariableT): FunctionEnvironment = {
    FunctionEnvironment(globalEnv, parentEnv, fullName, templatas, function, maybeReturnType, containingBlockS, declaredLocals :+ newVar, unstackifiedLocals)
  }
  def markLocalUnstackified(newUnstackified: FullNameT[IVarNameT]): FunctionEnvironment = {
    vassert(!getAllUnstackifiedLocals().contains(newUnstackified))
    vassert(getAllLocals().exists(_.id == newUnstackified))
    // Even if the local belongs to a parent env, we still mark it unstackified here, see UCRTVPE.
    FunctionEnvironment(globalEnv, parentEnv, fullName, templatas, function, maybeReturnType, containingBlockS, declaredLocals, unstackifiedLocals + newUnstackified)
  }

  def addEntry(name: INameT, entry: IEnvEntry): FunctionEnvironment = {
    FunctionEnvironment(
      globalEnv,
      parentEnv,
      fullName,
      templatas.addEntry(name, entry),
      function,
      maybeReturnType,
      containingBlockS,
      declaredLocals,
      unstackifiedLocals)
  }
  def addEntries(newEntries: Vector[(INameT, IEnvEntry)]): FunctionEnvironment = {
    FunctionEnvironment(
      globalEnv,
      parentEnv,
      fullName,
      templatas.addEntries(newEntries),
      function,
      maybeReturnType,
      containingBlockS,
      declaredLocals,
      unstackifiedLocals)
  }

  private[env] override def lookupWithNameInner(
    profiler: IProfiler,
    name: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    EnvironmentHelper.lookupWithNameInner(
      this, templatas, parentEnv, profiler, name, lookupFilter, getOnlyNearest)
  }

  private[env] override def lookupWithImpreciseNameInner(
    profiler: IProfiler,
    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    EnvironmentHelper.lookupWithImpreciseNameInner(
      this, templatas, parentEnv, profiler, name, lookupFilter, getOnlyNearest)
  }

  def getVariable(name: IVarNameT): Option[IVariableT] = {
    declaredLocals.find(_.id.last == name)
  }

  // Dont have a getAllUnstackifiedLocals or getAllLiveLocals here. We learned that the hard way.
  // See UCRTVPE, child environments would be the ones that know about their unstackifying of locals
  // from parent envs.

  def getAllLocals(): Vector[ILocalVariableT] = {
    declaredLocals.collect({ case i : ILocalVariableT => i })
  }

  def getAllUnstackifiedLocals(): Vector[FullNameT[IVarNameT]] = {
    unstackifiedLocals.toVector
  }

  def makeChildBlockEnvironment( newContainingBlockS: Option[BlockSE]) = {
    FunctionEnvironment(
      globalEnv,
      this,
      fullName,
      TemplatasStore(fullName, Map(), Map()),
      function,
      maybeReturnType,
      newContainingBlockS,
      declaredLocals, // See WTHPFE.
      unstackifiedLocals) // See WTHPFE.
  }

  // No particular reason we don't have an addFunction like PackageEnvironment does
}

case class FunctionEnvironmentBox(var functionEnvironment: FunctionEnvironment) extends IEnvironmentBox {
  override def hashCode(): Int = vfail() // Shouldnt hash, is mutable

  override def snapshot: FunctionEnvironment = functionEnvironment
  def fullName: FullNameT[IFunctionNameT] = functionEnvironment.fullName
  def function: FunctionA = functionEnvironment.function
  def maybeReturnType: Option[CoordT] = functionEnvironment.maybeReturnType
  def containingBlockS: Option[BlockSE] = functionEnvironment.containingBlockS
  def declaredLocals: Vector[IVariableT] = functionEnvironment.declaredLocals
  def unstackifieds: Set[FullNameT[IVarNameT]] = functionEnvironment.unstackifiedLocals
  override def globalEnv: GlobalEnvironment = functionEnvironment.globalEnv

  def setReturnType(returnType: Option[CoordT]): Unit = {
    functionEnvironment = functionEnvironment.copy(maybeReturnType = returnType)
  }

  def addVariable(newVar: IVariableT): Unit= {
    functionEnvironment = functionEnvironment.addVariable(newVar)
  }
  def markLocalUnstackified(newMoved: FullNameT[IVarNameT]): Unit= {
    functionEnvironment = functionEnvironment.markLocalUnstackified(newMoved)
  }

  def addEntry(name: INameT, entry: IEnvEntry): Unit = {
    functionEnvironment = functionEnvironment.addEntry(name, entry)
  }
  def addEntries(newEntries: Vector[(INameT, IEnvEntry)]): Unit= {
    functionEnvironment = functionEnvironment.addEntries(newEntries)
  }

  override def lookupNearestWithImpreciseName(
    profiler: IProfiler,
    nameS: IImpreciseNameS,
    lookupFilter: Set[ILookupContext]):
  Option[ITemplata] = {
    functionEnvironment.lookupNearestWithImpreciseName(profiler, nameS, lookupFilter)
  }

  override def lookupNearestWithName(
    profiler: IProfiler,
    nameS: INameT,
    lookupFilter: Set[ILookupContext]):
  Option[ITemplata] = {
    functionEnvironment.lookupNearestWithName(profiler, nameS, lookupFilter)
  }

  override def lookupAllWithImpreciseName(profiler: IProfiler, nameS: IImpreciseNameS, lookupFilter: Set[ILookupContext]): Iterable[ITemplata] = {
    functionEnvironment.lookupAllWithImpreciseName(profiler, nameS, lookupFilter)
  }

  override def lookupAllWithName(profiler: IProfiler, nameS: INameT, lookupFilter: Set[ILookupContext]): Iterable[ITemplata] = {
    functionEnvironment.lookupAllWithName(profiler, nameS, lookupFilter)
  }

  override private[env] def lookupWithImpreciseNameInner(profiler: IProfiler, nameS: IImpreciseNameS, lookupFilter: Set[ILookupContext], getOnlyNearest: Boolean) = {
    functionEnvironment.lookupWithImpreciseNameInner(profiler, nameS, lookupFilter, getOnlyNearest)
  }

  override private[env] def lookupWithNameInner(profiler: IProfiler, nameS: INameT, lookupFilter: Set[ILookupContext], getOnlyNearest: Boolean) = {
    functionEnvironment.lookupWithNameInner(profiler, nameS, lookupFilter, getOnlyNearest)
  }

  def getVariable(name: IVarNameT): Option[IVariableT] = {
    functionEnvironment.getVariable(name)
  }

  def getAllLocals(): Vector[ILocalVariableT] = {
    functionEnvironment.getAllLocals()
  }

  def getAllUnstackifiedLocals(): Vector[FullNameT[IVarNameT]] = {
    functionEnvironment.getAllUnstackifiedLocals()
  }

  // Gets the effects that this environment had on the outside world (on its parent
  // environments).
  def getEffectsSince(earlierFate: FunctionEnvironment): Set[FullNameT[IVarNameT]] = {
    // We may have unstackified outside locals from inside the block, make sure
    // the parent environment knows about that.

    // declaredLocals contains things from parent environment, which is why we need to receive
    // an earlier environment to compare to, see WTHPFE.
    val earlierFateDeclaredLocals = earlierFate.declaredLocals.map(_.id).toSet
    val earlierFateLiveLocals = earlierFateDeclaredLocals -- earlierFate.unstackifiedLocals
    val liveLocalsIntroducedSinceEarlier =
      declaredLocals.map(_.id).filter(x => !earlierFateLiveLocals.contains(x))

    val unstackifiedAncestorLocals = unstackifieds -- liveLocalsIntroducedSinceEarlier
    unstackifiedAncestorLocals
  }

  def makeChildBlockEnvironment( containingBlockS: Option[BlockSE]):
  FunctionEnvironmentBox = {
    FunctionEnvironmentBox(
      functionEnvironment
        .makeChildBlockEnvironment(containingBlockS))
  }

  // No particular reason we don't have an addFunction like PackageEnvironment does
}

sealed trait IVariableT  {
  def id: FullNameT[IVarNameT]
  def variability: VariabilityT
  def reference: CoordT
}
sealed trait ILocalVariableT extends IVariableT {
  def reference: CoordT
  def id: FullNameT[IVarNameT]
}
// Why the difference between reference and addressible:
// If we mutate/move a variable from inside a closure, we need to put
// the local's address into the struct. But, if the closures don't
// mutate/move, then we could just put a regular reference in the struct.
// Lucky for us, the parser figured out if any of our child closures did
// any mutates/moves/borrows.
case class AddressibleLocalVariableT(
  id: FullNameT[IVarNameT],
  variability: VariabilityT,
  reference: CoordT
) extends ILocalVariableT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class ReferenceLocalVariableT(
  id: FullNameT[IVarNameT],
  variability: VariabilityT,
  reference: CoordT
) extends ILocalVariableT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class AddressibleClosureVariableT(
  id: FullNameT[IVarNameT],
  closuredVarsStructType: StructTT,
  variability: VariabilityT,
  reference: CoordT
) extends IVariableT {

}
case class ReferenceClosureVariableT(
  id: FullNameT[IVarNameT],
  closuredVarsStructType: StructTT,
  variability: VariabilityT,
  reference: CoordT
) extends IVariableT {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}

object EnvironmentHelper {
  private[env] def lookupWithNameInner(
    requestingEnv: IEnvironment,
    templatas: TemplatasStore,
    parent: IEnvironment,
    profiler: IProfiler,
    name: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    val result = templatas.lookupWithNameInner(requestingEnv, profiler, name, lookupFilter)
    if (result.nonEmpty && getOnlyNearest) {
      result
    } else {
      result ++ parent.lookupWithNameInner(profiler, name, lookupFilter, getOnlyNearest)
    }
  }

  private[env] def lookupWithImpreciseNameInner(
    requestingEnv: IEnvironment,
    templatas: TemplatasStore,
    parent: IEnvironment,
    profiler: IProfiler,
    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    val result = templatas.lookupWithImpreciseNameInner(requestingEnv, profiler, name, lookupFilter)
    if (result.nonEmpty && getOnlyNearest) {
      result
    } else {
      result ++ parent.lookupWithImpreciseNameInner(profiler, name, lookupFilter, getOnlyNearest)
    }
  }
}