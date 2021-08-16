package net.verdagon.vale.templar.templata

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.{CitizenNameT, CitizenTemplateNameT, FullNameT, FunctionNameT, FunctionTemplateNameT, ICitizenNameT, INameT, ImmConcreteDestructorNameT, ImmDropNameT, ImplDeclareNameT, NameTranslator, PackageTopLevelNameT}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{PackageCoordinate, vassert, vfail, vimpl}

import scala.collection.immutable.List


sealed trait ITemplata extends QueriableT {
  def order: Int;
  def tyype: ITemplataType
}

case class CoordTemplata(reference: CoordT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 1;
  override def tyype: ITemplataType = CoordTemplataType

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ reference.all(func)
  }
}
case class KindTemplata(kind: KindT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 2;
  override def tyype: ITemplataType = KindTemplataType

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ kind.all(func)
  }
}
case class ArrayTemplateTemplata() extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 3;
  override def tyype: ITemplataType = TemplateTemplataType(Vector(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), KindTemplataType)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}


case class FunctionTemplata(
  // The environment this function was declared in.
  // Has the name of the surrounding environment, does NOT include function's name.
  // We need this because, for example, lambdas need to find their underlying struct
  // somewhere.
  // See TMRE for more on these environments.
  outerEnv: IEnvironment,
//
//  // The containers are the structs/interfaces/impls/functions that this thing is inside.
//  // E.g. if LinkedList has a Node substruct, then the Node's templata will have one
//  // container, the LinkedList.
//  // See NTKPRR for why we have these parents.
//  unevaluatedContainers: Vector[IContainer],

  // This is the env entry that the function came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  function: FunctionA
) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def order: Int = 6
  override def tyype: ITemplataType = vfail()

//  this match {
//    case FunctionTemplata(
//      env,
//      FunctionA(_, ImmConcreteDestructorNameA(PackageCoordinate(_,Vector.empty)),_, _, _, _, _, _, _, _, _, _))
//    if env.fullName == FullName2(PackageCoordinate.TEST_TLD,Vector.empty,PackageTopLevelName2()) => vfail()
//    case _ =>
//  }
//  this match {
//    case FunctionTemplata(env, _) if env.fullName == FullName2(PackageCoordinate.TEST_TLD,Vector.empty,PackageTopLevelName2()) => vfail()
//    case _ =>
//  }

  // Make sure we didn't accidentally code something to include the function's name as
  // the last step.
  // This assertion is helpful now, but will false-positive trip when someone
  // tries to make an interface with the same name as its containing. At that point,
  // feel free to remove this assertion.
  (outerEnv.fullName.last, function.name) match {
    case (FunctionNameT(envFunctionName, _, _), FunctionNameA(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }

  def getTemplateName(): INameT = {
    NameTranslator.translateFunctionNameToTemplateName(function.name)
  }

  def debugString: String = outerEnv.fullName + ":" + function.name
}

object FunctionTemplata {
  def make(parentEnv: IEnvironment, function: FunctionA) = {
    val functionEnvName = FullNameT(function.range.file.packageCoordinate, parentEnv.fullName.steps, PackageTopLevelNameT())
    val functionEnv = PackageEnvironment(Some(parentEnv), functionEnvName, TemplatasStore(Map(), Map()))
    FunctionTemplata(functionEnv, function)
  }
}

object StructTemplata {
  def make(parentEnv: IEnvironment, struct: StructA) = {
    val structEnvName = FullNameT(struct.range.file.packageCoordinate, parentEnv.fullName.steps, PackageTopLevelNameT())
    val structEnv = PackageEnvironment(Some(parentEnv), structEnvName, TemplatasStore(Map(), Map()))
    StructTemplata(structEnv, struct)
  }
}

object InterfaceTemplata {
  def make(parentEnv: IEnvironment, interface: InterfaceA) = {
    val interfaceEnvName = FullNameT(interface.range.file.packageCoordinate, parentEnv.fullName.steps, PackageTopLevelNameT())
    val interfaceEnv = PackageEnvironment(Some(parentEnv), interfaceEnvName, TemplatasStore(Map(), Map()))
    InterfaceTemplata(interfaceEnv, interface)
  }
}

object ImplTemplata {
  def make(parentEnv: IEnvironment, impl: ImplA) = {
    val implEnvName = FullNameT(impl.range.file.packageCoordinate, parentEnv.fullName.steps, PackageTopLevelNameT())
    val implEnv = PackageEnvironment(Some(parentEnv), implEnvName, TemplatasStore(Map(), Map()))
    ImplTemplata(implEnv, impl)
  }
}

case class StructTemplata(
  // The paackage this interface was declared in.
  // has the name of the surrounding environment, does NOT include struct's name.
  // See TMRE for more on these environments.
  env: PackageEnvironment[INameT],
//
//  // The containers are the structs/interfaces/impls/functions that this thing is inside.
//  // E.g. if LinkedList has a Node substruct, then the Node's templata will have one
//  // container, the LinkedList.
//  // See NTKPRR for why we have these parents.
//  containers: Vector[IContainer],

  // This is the env entry that the struct came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  originStruct: StructA,
) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 7
  override def tyype: ITemplataType = originStruct.tyype

  // Make sure we didn't accidentally code something to include the structs's name as
  // the last step.
  // This assertion is helpful now, but will false-positive trip when someone
  // tries to make an interface with the same name as its containing. At that point,
  // feel free to remove this assertion.
  (env.fullName.last, originStruct.name) match {
    case (CitizenNameT(envFunctionName, _), TopLevelCitizenDeclarationNameA(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }

  def getTemplateName(): INameT = {
    CitizenTemplateNameT(originStruct.name.name, NameTranslator.translateCodeLocation(originStruct.name.codeLocation))
  }

  def debugString: String = env.fullName + ":" + originStruct.name
}

sealed trait IContainer
case class ContainerInterface(interface: InterfaceA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ContainerStruct(struct: StructA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ContainerFunction(function: FunctionA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ContainerImpl(impl: ImplA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

case class InterfaceTemplata(
  // The paackage this interface was declared in.
  // Has the name of the surrounding environment, does NOT include interface's name.
  // See TMRE for more on these environments.
  env: PackageEnvironment[INameT],
//
//  // The containers are the structs/interfaces/impls/functions that this thing is inside.
//  // E.g. if LinkedList has a Node substruct, then the Node's templata will have one
//  // container, the LinkedList.
//  // See NTKPRR for why we have these parents.
//  containers: Vector[IContainer],

  // This is the env entry that the interface came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  originInterface: InterfaceA
) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 8
  override def tyype: ITemplataType = originInterface.tyype

  // Make sure we didn't accidentally code something to include the interface's name as
  // the last step.
  // This assertion is helpful now, but will false-positive trip when someone
  // tries to make an interface with the same name as its containing. At that point,
  // feel free to remove this assertion.
  (env.fullName.last, originInterface.name) match {
    case (CitizenNameT(envFunctionName, _), TopLevelCitizenDeclarationNameA(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }

  def getTemplateName(): INameT = {
    CitizenTemplateNameT(originInterface.name.name, NameTranslator.translateCodeLocation(originInterface.name.codeLocation))
  }

  def debugString: String = env.fullName + ":" + originInterface.name
}

case class ImplTemplata(
  // The paackage this interface was declared in.
  // See TMRE for more on these environments.
  env: IEnvironment,
//
//  // The containers are the structs/interfaces/impls/functions that this thing is inside.
//  // E.g. if LinkedList has a Node substruct, then the Node's templata will have one
//  // container, the LinkedList.
//  // See NTKPRR for why we have these parents.
//  containers: Vector[IContainer],

  // This is the impl that the interface came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  impl: ImplA
) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 9
  override def tyype: ITemplataType = vfail()

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}

case class OwnershipTemplata(ownership: OwnershipT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 10;
  override def tyype: ITemplataType = OwnershipTemplataType

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ ownership.all(func)
  }
}
case class VariabilityTemplata(variability: VariabilityT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 11;
  override def tyype: ITemplataType = VariabilityTemplataType

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ variability.all(func)
  }
}
case class MutabilityTemplata(mutability: MutabilityT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 12;
  override def tyype: ITemplataType = MutabilityTemplataType

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ mutability.all(func)
  }
}
case class PermissionTemplata(mutability: PermissionT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 13;
  override def tyype: ITemplataType = PermissionTemplataType

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ mutability.all(func)
  }
}
case class LocationTemplata(mutability: LocationT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 14;
  override def tyype: ITemplataType = LocationTemplataType

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ mutability.all(func)
  }
}

case class BooleanTemplata(value: Boolean) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 15;
  override def tyype: ITemplataType = BooleanTemplataType

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}
case class IntegerTemplata(value: Long) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 16;
  override def tyype: ITemplataType = IntegerTemplataType

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}
case class StringTemplata(value: String) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 17;
  override def tyype: ITemplataType = StringTemplataType

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}
case class PrototypeTemplata(value: PrototypeT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 18;
  override def tyype: ITemplataType = PrototypeTemplataType

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}
case class CoordListTemplata(value: Vector[CoordT]) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 18;
  override def tyype: ITemplataType = PackTemplataType(CoordTemplataType)

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func)
  }
}

// ExternFunction/ImplTemplata are here because for example when we create an anonymous interface
// substruct, we want to add its forwarding functions and its impl to the environment, but it's
// very difficult to add the ImplA and FunctionA for those. So, we allow having temputs like
// these directly in the environment.
// These should probably be renamed from Extern to something else... they could be supplied
// by plugins, but theyre also used internally.

case class ExternFunctionTemplata(header: FunctionHeaderT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 1337
  override def tyype: ITemplataType = vfail()

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ header.all(func)
  }
}

case class ExternImplTemplata(struct: StructTT, interface: InterfaceTT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 1338
  override def tyype: ITemplataType = vfail()

  def all[T](func: PartialFunction[QueriableT, T]): Vector[T] = {
    Vector(this).collect(func) ++ struct.all(func) ++ interface.all(func)
  }
}