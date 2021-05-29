package net.verdagon.vale.templar.templata

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.{CitizenName2, CitizenTemplateName2, FunctionName2, FunctionTemplateName2, ICitizenName2, IName2, ImplDeclareName2, NameTranslator}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vfail, vimpl}

import scala.collection.immutable.List


sealed trait ITemplata extends Queriable2 {
  def order: Int;
  def tyype: ITemplataType
}

case class CoordTemplata(reference: Coord) extends ITemplata {
  override def order: Int = 1;
  override def tyype: ITemplataType = CoordTemplataType

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ reference.all(func)
  }
}
case class KindTemplata(referend: Kind) extends ITemplata {
  override def order: Int = 2;
  override def tyype: ITemplataType = KindTemplataType

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ referend.all(func)
  }
}
case class ArrayTemplateTemplata() extends ITemplata {
  override def order: Int = 3;
  override def tyype: ITemplataType = TemplateTemplataType(List(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), KindTemplataType)

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
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
//  unevaluatedContainers: List[IContainer],

  // This is the env entry that the function came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  function: FunctionA
) extends ITemplata {
  override def order: Int = 6
  override def tyype: ITemplataType = vfail()

  // Make sure we didn't accidentally code something to include the function's name as
  // the last step.
  // This assertion is helpful now, but will false-positive trip when someone
  // tries to make an interface with the same name as its containing. At that point,
  // feel free to remove this assertion.
  (outerEnv.fullName.last, function.name) match {
    case (FunctionName2(envFunctionName, _, _), FunctionNameA(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }

  def getTemplateName(): IName2 = {
    NameTranslator.translateFunctionNameToTemplateName(function.name)
  }

  def debugString: String = outerEnv.fullName + ":" + function.name
}

case class StructTemplata(
  // The paackage this interface was declared in.
  // has the name of the surrounding environment, does NOT include struct's name.
  // See TMRE for more on these environments.
  env: PackageEnvironment[IName2],
//
//  // The containers are the structs/interfaces/impls/functions that this thing is inside.
//  // E.g. if LinkedList has a Node substruct, then the Node's templata will have one
//  // container, the LinkedList.
//  // See NTKPRR for why we have these parents.
//  containers: List[IContainer],

  // This is the env entry that the struct came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  originStruct: StructA,
) extends ITemplata {
  override def order: Int = 7
  override def tyype: ITemplataType = originStruct.tyype

  // Make sure we didn't accidentally code something to include the structs's name as
  // the last step.
  // This assertion is helpful now, but will false-positive trip when someone
  // tries to make an interface with the same name as its containing. At that point,
  // feel free to remove this assertion.
  (env.fullName.last, originStruct.name) match {
    case (CitizenName2(envFunctionName, _), TopLevelCitizenDeclarationNameA(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }

  def getTemplateName(): IName2 = {
    CitizenTemplateName2(originStruct.name.name, NameTranslator.translateCodeLocation(originStruct.name.codeLocation))
  }

  def debugString: String = env.fullName + ":" + originStruct.name
}

sealed trait IContainer
case class ContainerInterface(interface: InterfaceA) extends IContainer
case class ContainerStruct(struct: StructA) extends IContainer
case class ContainerFunction(function: FunctionA) extends IContainer
case class ContainerImpl(impl: ImplA) extends IContainer

case class InterfaceTemplata(
  // The paackage this interface was declared in.
  // Has the name of the surrounding environment, does NOT include interface's name.
  // See TMRE for more on these environments.
  env: PackageEnvironment[IName2],
//
//  // The containers are the structs/interfaces/impls/functions that this thing is inside.
//  // E.g. if LinkedList has a Node substruct, then the Node's templata will have one
//  // container, the LinkedList.
//  // See NTKPRR for why we have these parents.
//  containers: List[IContainer],

  // This is the env entry that the interface came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  originInterface: InterfaceA
) extends ITemplata {
  override def order: Int = 8
  override def tyype: ITemplataType = originInterface.tyype

  // Make sure we didn't accidentally code something to include the interface's name as
  // the last step.
  // This assertion is helpful now, but will false-positive trip when someone
  // tries to make an interface with the same name as its containing. At that point,
  // feel free to remove this assertion.
  (env.fullName.last, originInterface.name) match {
    case (CitizenName2(envFunctionName, _), TopLevelCitizenDeclarationNameA(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }

  def getTemplateName(): IName2 = {
    CitizenTemplateName2(originInterface.name.name, NameTranslator.translateCodeLocation(originInterface.name.codeLocation))
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
//  containers: List[IContainer],

  // This is the impl that the interface came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  impl: ImplA
) extends ITemplata {
  override def order: Int = 9
  override def tyype: ITemplataType = vfail()

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}

case class OwnershipTemplata(ownership: Ownership) extends ITemplata {
  override def order: Int = 10;
  override def tyype: ITemplataType = OwnershipTemplataType

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ ownership.all(func)
  }
}
case class VariabilityTemplata(variability: Variability) extends ITemplata {
  override def order: Int = 11;
  override def tyype: ITemplataType = VariabilityTemplataType

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ variability.all(func)
  }
}
case class MutabilityTemplata(mutability: Mutability) extends ITemplata {
  override def order: Int = 12;
  override def tyype: ITemplataType = MutabilityTemplataType

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ mutability.all(func)
  }
}
case class PermissionTemplata(mutability: Permission) extends ITemplata {
  override def order: Int = 13;
  override def tyype: ITemplataType = PermissionTemplataType

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ mutability.all(func)
  }
}
case class LocationTemplata(mutability: Location) extends ITemplata {
  override def order: Int = 14;
  override def tyype: ITemplataType = LocationTemplataType

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ mutability.all(func)
  }
}

case class BooleanTemplata(value: Boolean) extends ITemplata {
  override def order: Int = 15;
  override def tyype: ITemplataType = BooleanTemplataType

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case class IntegerTemplata(value: Integer) extends ITemplata {
  override def order: Int = 16;
  override def tyype: ITemplataType = IntegerTemplataType

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case class StringTemplata(value: String) extends ITemplata {
  override def order: Int = 17;
  override def tyype: ITemplataType = StringTemplataType

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case class PrototypeTemplata(value: Prototype2) extends ITemplata {
  override def order: Int = 18;
  override def tyype: ITemplataType = PrototypeTemplataType

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}
case class CoordListTemplata(value: List[Coord]) extends ITemplata {
  override def order: Int = 18;
  override def tyype: ITemplataType = PackTemplataType(CoordTemplataType)

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func)
  }
}

// ExternFunction/ImplTemplata are here because for example when we create an anonymous interface
// substruct, we want to add its forwarding functions and its impl to the environment, but it's
// very difficult to add the ImplA and FunctionA for those. So, we allow having temputs like
// these directly in the environment.
// These should probably be renamed from Extern to something else... they could be supplied
// by plugins, but theyre also used internally.

case class ExternFunctionTemplata(header: FunctionHeader2) extends ITemplata {
  override def order: Int = 1337
  override def tyype: ITemplataType = vfail()

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ header.all(func)
  }
}

case class ExternImplTemplata(struct: StructRef2, interface: InterfaceRef2) extends ITemplata {
  override def order: Int = 1338
  override def tyype: ITemplataType = vfail()

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ struct.all(func) ++ interface.all(func)
  }
}