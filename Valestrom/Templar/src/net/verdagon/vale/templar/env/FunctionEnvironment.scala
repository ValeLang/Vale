package net.verdagon.vale.templar.env

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.{BlockSE, IExpressionSE, IImpreciseNameS, INameS, LocalS, WhileSE}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.LocationInFunctionEnvironment
import net.verdagon.vale.templar.names.{BuildingFunctionNameWithClosuredsAndTemplateArgsT, BuildingFunctionNameWithClosuredsT, FullNameT, IFunctionNameT, INameT, IVarNameT}
import net.verdagon.vale.templar.templata.ITemplata
import net.verdagon.vale.templar.types.{CoordT, StructTT, VariabilityT}
import net.verdagon.vale.{IProfiler, vassert, vcurious, vfail, vimpl, vpass, vwat}

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

case class NodeEnvironment(
  parentFunctionEnv: FunctionEnvironment,
  parentNodeEnv: Option[NodeEnvironment],
  node: IExpressionSE,
  life: LocationInFunctionEnvironment,

  // The things below are the "state"; they can be different for any given line in a function.
  templatas: TemplatasStore,
  // This contains locals from parent blocks, see WTHPFE.
  declaredLocals: Vector[IVariableT],
  // This can refer to vars in parent blocks, see UCRTVPE.
  unstackifiedLocals: Set[FullNameT[IVarNameT]]
) extends IEnvironment {
  vassert(declaredLocals == declaredLocals.distinct)

  val hash = fullName.hashCode() ^ life.hashCode();
  override def hashCode(): Int = hash;
  override def equals(obj: Any): Boolean = vcurious()

  override def fullName: FullNameT[INameT] = parentFunctionEnv.fullName
  def function = parentFunctionEnv.function

  private[env] override def lookupWithNameInner(
    profiler: IProfiler,
    name: INameT,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    EnvironmentHelper.lookupWithNameInner(
      this, templatas, parentNodeEnv.getOrElse(parentFunctionEnv), profiler, name, lookupFilter, getOnlyNearest)
  }

  private[env] override def lookupWithImpreciseNameInner(
    profiler: IProfiler,
    name: IImpreciseNameS,
    lookupFilter: Set[ILookupContext],
    getOnlyNearest: Boolean):
  Iterable[ITemplata] = {
    EnvironmentHelper.lookupWithImpreciseNameInner(
      this, templatas, parentNodeEnv.getOrElse(parentFunctionEnv), profiler, name, lookupFilter, getOnlyNearest)
  }

  def globalEnv: GlobalEnvironment = parentFunctionEnv.globalEnv

  def parentEnv: IEnvironment = {
    parentNodeEnv.getOrElse(parentFunctionEnv)
  }

  def getVariable(name: IVarNameT): Option[IVariableT] = {
    declaredLocals.find(_.id.last == name) match {
      case Some(v) => Some(v)
      case None => {
        parentNodeEnv match {
          case Some(p) => p.getVariable(name)
          case None => {
            parentFunctionEnv.closuredLocals.find(_.id.last == name)
          }
        }
      }
    }
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

  def addVariables(newVars: Vector[IVariableT]): NodeEnvironment = {
    NodeEnvironment(parentFunctionEnv, parentNodeEnv, node, life, templatas, declaredLocals ++ newVars, unstackifiedLocals)
  }
  def addVariable(newVar: IVariableT): NodeEnvironment = {
    NodeEnvironment(parentFunctionEnv, parentNodeEnv, node, life, templatas, declaredLocals :+ newVar, unstackifiedLocals)
  }
  def markLocalUnstackified(newUnstackified: FullNameT[IVarNameT]): NodeEnvironment = {
    vassert(!getAllUnstackifiedLocals().contains(newUnstackified))
    vassert(getAllLocals().exists(_.id == newUnstackified))
    // Even if the local belongs to a parent env, we still mark it unstackified here, see UCRTVPE.
    NodeEnvironment(parentFunctionEnv, parentNodeEnv, node, life, templatas, declaredLocals, unstackifiedLocals + newUnstackified)
  }

  // Gets the effects that this environment had on the outside world (on its parent
  // environments).
  def getEffectsSince(earlierNodeEnv: NodeEnvironment): Set[FullNameT[IVarNameT]] = {
    vassert(parentFunctionEnv == earlierNodeEnv.parentFunctionEnv)

    // We may have unstackified outside locals from inside the block, make sure
    // the parent environment knows about that.

    // declaredLocals contains things from parent environment, which is why we need to receive
    // an earlier environment to compare to, see WTHPFE.
    val earlierNodeEnvDeclaredLocals = earlierNodeEnv.declaredLocals.map(_.id).toSet
    val earlierNodeEnvLiveLocals = earlierNodeEnvDeclaredLocals -- earlierNodeEnv.unstackifiedLocals
    val liveLocalsIntroducedSinceEarlier =
      declaredLocals.map(_.id).filter(x => !earlierNodeEnvLiveLocals.contains(x))

    val unstackifiedAncestorLocals = unstackifiedLocals -- liveLocalsIntroducedSinceEarlier
    unstackifiedAncestorLocals
  }

  def makeChild(node: IExpressionSE): NodeEnvironment = {
    NodeEnvironment(
      parentFunctionEnv,
      Some(this),
      node,
      life,
      TemplatasStore(fullName, Map(), Map()),
      declaredLocals, // See WTHPFE.
      unstackifiedLocals) // See WTHPFE.
  }

  def addEntry(name: INameT, entry: IEnvEntry): NodeEnvironment = {
    NodeEnvironment(
      parentFunctionEnv,
      parentNodeEnv,
      node,
      life,
      templatas.addEntry(name, entry),
      declaredLocals,
      unstackifiedLocals)
  }
  def addEntries(newEntries: Vector[(INameT, IEnvEntry)]): NodeEnvironment = {
    NodeEnvironment(
      parentFunctionEnv,
      parentNodeEnv,
      node,
      life,
      templatas.addEntries(newEntries),
      declaredLocals,
      unstackifiedLocals)
  }

  def nearestBlockEnv(): Option[(NodeEnvironment, BlockSE)] = {
    node match {
      case b @ BlockSE(_, _, _) => Some((this, b))
      case _ => parentNodeEnv.flatMap(_.nearestBlockEnv())
    }
  }
  def nearestWhileEnv(): Option[(NodeEnvironment, WhileSE)] = {
    node match {
      case w @ WhileSE(_, _) => Some((this, w))
      case _ => parentNodeEnv.flatMap(_.nearestWhileEnv())
    }
  }
}

case class NodeEnvironmentBox(var nodeEnvironment: NodeEnvironment) {
  override def hashCode(): Int = vfail() // Shouldnt hash, is mutable

  def snapshot: NodeEnvironment = nodeEnvironment
  def fullName: FullNameT[IFunctionNameT] = nodeEnvironment.parentFunctionEnv.fullName
  def node: IExpressionSE = nodeEnvironment.node
  def maybeReturnType: Option[CoordT] = nodeEnvironment.parentFunctionEnv.maybeReturnType
  def globalEnv: GlobalEnvironment = nodeEnvironment.globalEnv
  def declaredLocals: Vector[IVariableT] = nodeEnvironment.declaredLocals
  def unstackifieds: Set[FullNameT[IVarNameT]] = nodeEnvironment.unstackifiedLocals
  def function = nodeEnvironment.function
  def functionEnvironment = nodeEnvironment.parentFunctionEnv

  def addVariable(newVar: IVariableT): Unit= {
    nodeEnvironment = nodeEnvironment.addVariable(newVar)
  }
  def markLocalUnstackified(newMoved: FullNameT[IVarNameT]): Unit= {
    nodeEnvironment = nodeEnvironment.markLocalUnstackified(newMoved)
  }

  def getVariable(name: IVarNameT): Option[IVariableT] = {
    nodeEnvironment.getVariable(name)
  }

  def getAllLocals(): Vector[ILocalVariableT] = {
    nodeEnvironment.getAllLocals()
  }

  def getAllUnstackifiedLocals(): Vector[FullNameT[IVarNameT]] = {
    nodeEnvironment.getAllUnstackifiedLocals()
  }

  def lookupNearestWithImpreciseName(
    profiler: IProfiler,
    nameS: IImpreciseNameS,
    lookupFilter: Set[ILookupContext]):
  Option[ITemplata] = {
    nodeEnvironment.lookupNearestWithImpreciseName(profiler, nameS, lookupFilter)
  }

  def lookupNearestWithName(
    profiler: IProfiler,
    nameS: INameT,
    lookupFilter: Set[ILookupContext]):
  Option[ITemplata] = {
    nodeEnvironment.lookupNearestWithName(profiler, nameS, lookupFilter)
  }

  def lookupAllWithImpreciseName(profiler: IProfiler, nameS: IImpreciseNameS, lookupFilter: Set[ILookupContext]): Iterable[ITemplata] = {
    nodeEnvironment.lookupAllWithImpreciseName(profiler, nameS, lookupFilter)
  }

  def lookupAllWithName(profiler: IProfiler, nameS: INameT, lookupFilter: Set[ILookupContext]): Iterable[ITemplata] = {
    nodeEnvironment.lookupAllWithName(profiler, nameS, lookupFilter)
  }

  private[env] def lookupWithImpreciseNameInner(profiler: IProfiler, nameS: IImpreciseNameS, lookupFilter: Set[ILookupContext], getOnlyNearest: Boolean) = {
    nodeEnvironment.lookupWithImpreciseNameInner(profiler, nameS, lookupFilter, getOnlyNearest)
  }

  private[env] def lookupWithNameInner(profiler: IProfiler, nameS: INameT, lookupFilter: Set[ILookupContext], getOnlyNearest: Boolean) = {
    nodeEnvironment.lookupWithNameInner(profiler, nameS, lookupFilter, getOnlyNearest)
  }

  def makeChild(node: IExpressionSE): NodeEnvironment = {
    nodeEnvironment.makeChild(node)
  }

  def addEntry(name: INameT, entry: IEnvEntry): Unit = {
    nodeEnvironment = nodeEnvironment.addEntry(name, entry)
  }
  def addEntries(newEntries: Vector[(INameT, IEnvEntry)]): Unit= {
    nodeEnvironment = nodeEnvironment.addEntries(newEntries)
  }

  def nearestBlockEnv(): Option[(NodeEnvironment, BlockSE)] = {
    nodeEnvironment.nearestBlockEnv()
  }
  def nearestWhileEnv(): Option[(NodeEnvironment, WhileSE)] = {
    nodeEnvironment.nearestWhileEnv()
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

  closuredLocals: Vector[IVariableT],

  // Eventually we might have a list of imported environments here, pointing at the
  // environments in the global environment.
) extends IEnvironment {
  val hash = runtime.ScalaRunTime._hashCode(fullName); override def hashCode(): Int = hash;

  override def equals(obj: Any): Boolean = {
    if (!obj.isInstanceOf[IEnvironment]) {
      return false
    }
    return fullName.equals(obj.asInstanceOf[IEnvironment].fullName)
  }


  def addEntry(name: INameT, entry: IEnvEntry): FunctionEnvironment = {
    FunctionEnvironment(
      globalEnv,
      parentEnv,
      fullName,
      templatas.addEntry(name, entry),
      function,
      maybeReturnType,
      closuredLocals)
  }
  def addEntries(newEntries: Vector[(INameT, IEnvEntry)]): FunctionEnvironment = {
    FunctionEnvironment(
      globalEnv,
      parentEnv,
      fullName,
      templatas.addEntries(newEntries),
      function,
      maybeReturnType,
      closuredLocals)
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

  def makeChildNodeEnvironment(node: IExpressionSE, life: LocationInFunctionEnvironment): NodeEnvironment = {
    // See WTHPFE, if this is a lambda, we let our blocks start with
    // locals from the parent function.
    val (declaredLocals, unstackifiedLocals) =
      parentEnv match {
        case NodeEnvironment(_, _, _, _, _, declaredLocals, unstackifiedLocals) => {
          (declaredLocals, unstackifiedLocals)
        }
        case _ => (Vector(), Set[FullNameT[IVarNameT]]())
      }

    NodeEnvironment(
      this,
      None,
      node,
      life,
      TemplatasStore(fullName, Map(), Map()),
      declaredLocals, // See WTHPFE.
      unstackifiedLocals) // See WTHPFE.
  }

  def getClosuredDeclaredLocals(): Vector[IVariableT] = {
    parentEnv match {
      case n @ NodeEnvironment(_, _, _, _, _, _, _) => n.declaredLocals
      case f @ FunctionEnvironment(_, _, _, _, _, _, _) => f.getClosuredDeclaredLocals()
      case _ => Vector()
    }
  }

//  def getClosuredUnstackifiedLocals(): Vector[IVariableT] = {
//    parentEnv match {
//      case n @ NodeEnvironment(_, _, _, _, _, _, _) => n.unstackifiedLocals
//      case f @ FunctionEnvironment(_, _, _, _, _, _) => f.getClosuredDeclaredLocals()
//      case _ => Vector()
//    }
//  }

  // No particular reason we don't have an addFunction like PackageEnvironment does
}

case class FunctionEnvironmentBox(var functionEnvironment: FunctionEnvironment) extends IEnvironmentBox {
  override def hashCode(): Int = vfail() // Shouldnt hash, is mutable

  override def snapshot: FunctionEnvironment = functionEnvironment
  def fullName: FullNameT[IFunctionNameT] = functionEnvironment.fullName
  def function: FunctionA = functionEnvironment.function
  def maybeReturnType: Option[CoordT] = functionEnvironment.maybeReturnType
  override def globalEnv: GlobalEnvironment = functionEnvironment.globalEnv

  def setReturnType(returnType: Option[CoordT]): Unit = {
    functionEnvironment = functionEnvironment.copy(maybeReturnType = returnType)
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

  def makeChildNodeEnvironment(node: IExpressionSE, life: LocationInFunctionEnvironment): NodeEnvironment = {
    functionEnvironment.makeChildNodeEnvironment(node, life)
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
  vpass()
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