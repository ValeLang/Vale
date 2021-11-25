package net.verdagon.vale.templar.templata

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.{BooleanTemplataType, CoordTemplataType, FunctionNameS, ITemplataType, IntegerTemplataType, KindTemplataType, LocationTemplataType, MutabilityTemplataType, OwnershipTemplataType, PackTemplataType, PermissionTemplataType, PrototypeTemplataType, StringTemplataType, TemplateTemplataType, TopLevelCitizenDeclarationNameS, VariabilityTemplataType}
import net.verdagon.vale.templar.ast.{FunctionHeaderT, PrototypeT}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.names.{CitizenNameT, CitizenTemplateNameT, FullNameT, FunctionNameT, INameT, NameTranslator, PackageTopLevelNameT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{PackageCoordinate, vassert, vfail, vimpl, vpass}

import scala.collection.immutable.List


sealed trait ITemplata  {
  def order: Int;
  def tyype: ITemplataType
}

case class CoordTemplata(reference: CoordT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 1;
  override def tyype: ITemplataType = CoordTemplataType


}
case class KindTemplata(kind: KindT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 2;
  override def tyype: ITemplataType = KindTemplataType


}
case class RuntimeSizedArrayTemplateTemplata() extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 3;
  override def tyype: ITemplataType = TemplateTemplataType(Vector(MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), KindTemplataType)
}
case class StaticSizedArrayTemplateTemplata() extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 3;
  override def tyype: ITemplataType = TemplateTemplataType(Vector(IntegerTemplataType, MutabilityTemplataType, VariabilityTemplataType, CoordTemplataType), KindTemplataType)
}



case class FunctionTemplata(
  // The environment this function was declared in.
  // Has the name of the surrounding environment, does NOT include function's name.
  // We need this because, for example, lambdas need to find their underlying struct
  // somewhere.
  // See TMRE for more on these environments.
  outerEnv: IEnvironment,

  // This is the env entry that the function came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  function: FunctionA
) extends ITemplata {
  vassert(outerEnv.fullName.packageCoord == function.name.packageCoordinate)

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def order: Int = 6
  override def tyype: ITemplataType = vfail()

  vpass()

//  this match {
//    case FunctionTemplata(
//      env,
//      FunctionA(_, ImmConcreteDestructorNameS(PackageCoordinate(_,Vector.empty)),_, _, _, _, _, _, _, _, _, _))
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
    case (FunctionNameT(envFunctionName, _, _), FunctionNameS(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }



  def getTemplateName(): FullNameT[INameT] = {
    outerEnv.fullName.addStep(NameTranslator.translateFunctionNameToTemplateName(function.name))
  }

  def debugString: String = outerEnv.fullName + ":" + function.name
}

//object FunctionTemplata {
//  def make(globalEnv: GlobalEnvironment, namespaceName: FullNameT[INameT], function: FunctionA) = {
//    // THIS IS TEMPORARY, it pulls in all global namespaces!
//    // See https://github.com/ValeLang/Vale/issues/356
//
//    hello
//    // when we pull something from the templatas store, we need to pair it with the environment
//    // that created it. easy for structs and interfaces, those we can look up in the temputs.
//    // but for functions... how do we do that.
//    // an interesting case is closure functions, which are found in their struct's environment.
//    // when we look those up, we need to pair it with that struct's environment.
//    // or maybe i shouldnt be using the word environment, perhaps namespace.
//    // we need to establish an environment with the struct's namespace?
//    // and some namespaces can be anonymous.
//    // i think?
//
//    val definingEnv = PackageEnvironment.makeTopLevelEnvironment(globalEnv, namespaceName)
//    FunctionTemplata(definingEnv, function)
//  }
//}
//
//object StructTemplata {
//  def make(globalEnv: GlobalEnvironment, namespaceName: FullNameT[INameT], struct: StructA) = {
//    // THIS IS TEMPORARY, it pulls in all global namespaces!
//    // See https://github.com/ValeLang/Vale/issues/356
//    val definingEnv = PackageEnvironment.makeTopLevelEnvironment(globalEnv, namespaceName)
//    StructTemplata(definingEnv, struct)
//  }
//}
//
//object InterfaceTemplata {
//  def make(globalEnv: GlobalEnvironment, namespaceName: FullNameT[INameT], interface: InterfaceA) = {
//    // THIS IS TEMPORARY, it pulls in all global namespaces!
//    // See https://github.com/ValeLang/Vale/issues/356
//    val definingEnv = PackageEnvironment.makeTopLevelEnvironment(globalEnv, namespaceName)
//    InterfaceTemplata(definingEnv, interface)
//  }
//}
//
//object ImplTemplata {
//  def make(globalEnv: GlobalEnvironment, namespaceName: FullNameT[INameT], impl: ImplA) = {
//    // THIS IS TEMPORARY, it pulls in all global namespaces!
//    // See https://github.com/ValeLang/Vale/issues/356
//    val definingEnv = PackageEnvironment.makeTopLevelEnvironment(globalEnv, namespaceName)
//    ImplTemplata(definingEnv, impl)
//  }
//}

case class StructTemplata(
  // The paackage this interface was declared in.
  // has the name of the surrounding environment, does NOT include struct's name.
  // See TMRE for more on these environments.
  env: IEnvironment,

  // This is the env entry that the struct came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  originStruct: StructA,
) extends ITemplata {
  vassert(env.fullName.packageCoord == originStruct.name.range.file.packageCoordinate)

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 7
  override def tyype: ITemplataType = {
    // Note that this might disagree with originStruct.tyype, which might not be a TemplateTemplataType.
    // In Templar, StructTemplatas are templates, even if they have zero arguments.
    TemplateTemplataType(originStruct.identifyingRunes.map(_.rune).map(originStruct.runeToType), KindTemplataType)
  }

  // Make sure we didn't accidentally code something to include the structs's name as
  // the last step.
  // This assertion is helpful now, but will false-positive trip when someone
  // tries to make an interface with the same name as its containing. At that point,
  // feel free to remove this assertion.
  (env.fullName.last, originStruct.name) match {
    case (CitizenNameT(envFunctionName, _), TopLevelCitizenDeclarationNameS(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }



//  def getTemplateName(): INameT = {
//    CitizenTemplateNameT(originStruct.name.name)//, NameTranslator.translateCodeLocation(originStruct.name.range.begin))
//  }

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
  env: IEnvironment,
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
  vassert(env.fullName.packageCoord == originInterface.name.range.file.packageCoordinate)

  vpass()
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 8
  override def tyype: ITemplataType = {
    // Note that this might disagree with originStruct.tyype, which might not be a TemplateTemplataType.
    // In Templar, StructTemplatas are templates, even if they have zero arguments.
    TemplateTemplataType(originInterface.identifyingRunes.map(_.rune).map(originInterface.runeToType), KindTemplataType)
  }

  // Make sure we didn't accidentally code something to include the interface's name as
  // the last step.
  // This assertion is helpful now, but will false-positive trip when someone
  // tries to make an interface with the same name as its containing. At that point,
  // feel free to remove this assertion.
  (env.fullName.last, originInterface.name) match {
    case (CitizenNameT(envFunctionName, _), TopLevelCitizenDeclarationNameS(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }



  def getTemplateName(): INameT = {
    CitizenTemplateNameT(originInterface.name.name)//, NameTranslator.translateCodeLocation(originInterface.name.range.begin))
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


}

case class OwnershipTemplata(ownership: OwnershipT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 10;
  override def tyype: ITemplataType = OwnershipTemplataType


}
case class VariabilityTemplata(variability: VariabilityT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 11;
  override def tyype: ITemplataType = VariabilityTemplataType


}
case class MutabilityTemplata(mutability: MutabilityT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 12;
  override def tyype: ITemplataType = MutabilityTemplataType


}
case class PermissionTemplata(permission: PermissionT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 13;
  override def tyype: ITemplataType = PermissionTemplataType


}
case class LocationTemplata(location: LocationT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 14;
  override def tyype: ITemplataType = LocationTemplataType


}

case class BooleanTemplata(value: Boolean) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 15;
  override def tyype: ITemplataType = BooleanTemplataType


}
case class IntegerTemplata(value: Long) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 16;
  override def tyype: ITemplataType = IntegerTemplataType


}
case class StringTemplata(value: String) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 17;
  override def tyype: ITemplataType = StringTemplataType


}
case class PrototypeTemplata(value: PrototypeT) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 18;
  override def tyype: ITemplataType = PrototypeTemplataType


}
case class CoordListTemplata(coords: Vector[CoordT]) extends ITemplata {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def order: Int = 18;
  override def tyype: ITemplataType = PackTemplataType(CoordTemplataType)


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


}

//case class ExternImplTemplata(struct: StructTT, interface: InterfaceTT) extends ITemplata {
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//  override def order: Int = 1338
//  override def tyype: ITemplataType = vfail()
//
//
//}
