package dev.vale.typing.templata

import dev.vale.highertyping.{FunctionA, ImplA, InterfaceA, StructA}
import dev.vale.postparsing._
import dev.vale.typing.ast.{FunctionHeaderT, PrototypeT}
import dev.vale.typing.env.IEnvironment
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, IdT, FunctionNameT, IFunctionNameT, IImplNameT, INameT, InterfaceTemplateNameT, PlaceholderNameT}
import dev.vale.typing.types._
import dev.vale.{RangeS, StrI, vassert, vfail, vimpl, vpass, vwat}
import dev.vale.highertyping._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import dev.vale.typing.types._

import scala.collection.immutable.List


object ITemplata {
  def expectMutability(templata: ITemplata[ITemplataType]): ITemplata[MutabilityTemplataType] = {
    templata match {
      case t @ MutabilityTemplata(_) => t
      case PlaceholderTemplata(fullNameT, MutabilityTemplataType()) => PlaceholderTemplata(fullNameT, MutabilityTemplataType())
      case _ => vfail()
    }
  }

  def expectVariability(templata: ITemplata[ITemplataType]): ITemplata[VariabilityTemplataType] = {
    templata match {
      case t @ VariabilityTemplata(_) => t
      case PlaceholderTemplata(fullNameT, VariabilityTemplataType()) => PlaceholderTemplata(fullNameT, VariabilityTemplataType())
      case _ => vfail()
    }
  }

  def expectInteger(templata: ITemplata[ITemplataType]): ITemplata[IntegerTemplataType] = {
    templata match {
      case t @ IntegerTemplata(_) => t
      case PlaceholderTemplata(fullNameT, IntegerTemplataType()) => PlaceholderTemplata(fullNameT, IntegerTemplataType())
      case other => vfail(other)
    }
  }

  def expectCoord(templata: ITemplata[ITemplataType]): ITemplata[CoordTemplataType] = {
    templata match {
      case t @ CoordTemplata(_) => t
      case PlaceholderTemplata(fullNameT, CoordTemplataType()) => PlaceholderTemplata(fullNameT, CoordTemplataType())
      case other => vfail(other)
    }
  }

  def expectCoordTemplata(templata: ITemplata[ITemplataType]): CoordTemplata = {
    templata match {
      case t @ CoordTemplata(_) => t
      case other => vfail(other)
    }
  }

  def expectIntegerTemplata(templata: ITemplata[ITemplataType]): IntegerTemplata = {
    templata match {
      case t @ IntegerTemplata(_) => t
      case _ => vfail()
    }
  }

  def expectMutabilityTemplata(templata: ITemplata[ITemplataType]): MutabilityTemplata = {
    templata match {
      case t @ MutabilityTemplata(_) => t
      case _ => vfail()
    }
  }

  def expectVariabilityTemplata(templata: ITemplata[ITemplataType]): ITemplata[VariabilityTemplataType] = {
    templata match {
      case t @ VariabilityTemplata(_) => t
      case _ => vfail()
    }
  }

  def expectKind(templata: ITemplata[ITemplataType]): ITemplata[KindTemplataType] = {
    templata match {
      case t @ KindTemplata(_) => t
      case PlaceholderTemplata(fullNameT, KindTemplataType()) => PlaceholderTemplata(fullNameT, KindTemplataType())
      case _ => vfail()
    }
  }

  def expectKindTemplata(templata: ITemplata[ITemplataType]): KindTemplata = {
    templata match {
      case t @ KindTemplata(_) => t
      case _ => vfail()
    }
  }
}

sealed trait ITemplata[+T <: ITemplataType]  {
  def tyype: T
}

case class CoordTemplata(coord: CoordT) extends ITemplata[CoordTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: CoordTemplataType = CoordTemplataType()

  vpass()
}
case class PlaceholderTemplata[+T <: ITemplataType](
  fullNameT: IdT[PlaceholderNameT],
  tyype: T
) extends ITemplata[T] {
  tyype match {
    case CoordTemplataType() => vwat()
    case KindTemplataType() => vwat()
    case _ =>
  }
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class KindTemplata(kind: KindT) extends ITemplata[KindTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: KindTemplataType = KindTemplataType()
}
case class RuntimeSizedArrayTemplateTemplata() extends ITemplata[TemplateTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: TemplateTemplataType = TemplateTemplataType(Vector(MutabilityTemplataType(), CoordTemplataType()), KindTemplataType())
}
case class StaticSizedArrayTemplateTemplata() extends ITemplata[TemplateTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: TemplateTemplataType = TemplateTemplataType(Vector(IntegerTemplataType(), MutabilityTemplataType(), VariabilityTemplataType(), CoordTemplataType()), KindTemplataType())
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
) extends ITemplata[TemplateTemplataType] {
  vassert(outerEnv.fullName.packageCoord == function.name.packageCoordinate)

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def equals(obj: Any): Boolean = {
    obj match {
      case FunctionTemplata(thatEnv, thatFunction) => {
        function.range == thatFunction.range &&
          function.name == thatFunction.name
      }
      case _ => false
    }
  }

  override def tyype: TemplateTemplataType = vfail()

  vpass()

  // Make sure we didn't accidentally code something to include the function's name as
  // the last step.
  // This assertion is helpful now, but will false-positive trip when someone
  // tries to make an interface with the same name as its containing. At that point,
  // feel free to remove this assertion.
  (outerEnv.fullName.localName, function.name) match {
    case (FunctionNameT(envFunctionName, _, _), FunctionNameS(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }



  def getTemplateName(): IdT[INameT] = {
    vimpl()
//    outerEnv.fullName.addStep(nameTranslator.translateFunctionNameToTemplateName(function.name))
  }

  def debugString: String = outerEnv.fullName + ":" + function.name
}

case class StructDefinitionTemplata(
  // The paackage this interface was declared in.
  // has the name of the surrounding environment, does NOT include struct's name.
  // See TMRE for more on these environments.
  declaringEnv: IEnvironment,

  // This is the env entry that the struct came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  originStruct: StructA,
) extends CitizenDefinitionTemplata {
  override def originCitizen: CitizenA = originStruct

  vassert(declaringEnv.fullName.packageCoord == originStruct.name.range.file.packageCoordinate)

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: TemplateTemplataType = {
    // Note that this might disagree with originStruct.tyype, which might not be a TemplateTemplataType().
    // In Compiler, StructTemplatas are templates, even if they have zero arguments.
    val allRuneToType = originStruct.headerRuneToType ++ originStruct.membersRuneToType
    TemplateTemplataType(
      originStruct.genericParameters
        .map(_.rune.rune)
        .map(allRuneToType),
      KindTemplataType())
  }

  // Make sure we didn't accidentally code something to include the structs's name as
  // the last step.
  // This assertion is helpful now, but will false-positive trip when someone
  // tries to make an interface with the same name as its containing. At that point,
  // feel free to remove this assertion.
  (declaringEnv.fullName.localName, originStruct.name) match {
    case (CitizenNameT(envFunctionName, _), TopLevelCitizenDeclarationNameS(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }

  def debugString: String = declaringEnv.fullName + ":" + originStruct.name
}

sealed trait IContainer
case class ContainerInterface(interface: InterfaceA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ContainerStruct(struct: StructA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ContainerFunction(function: FunctionA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ContainerImpl(impl: ImplA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

sealed trait CitizenDefinitionTemplata extends ITemplata[TemplateTemplataType] {
  def declaringEnv: IEnvironment
  def originCitizen: CitizenA
}
object CitizenDefinitionTemplata {
  def unapply(c: CitizenDefinitionTemplata): Option[(IEnvironment, CitizenA)] = {
    c match {
      case StructDefinitionTemplata(env, origin) => Some((env, origin))
      case InterfaceDefinitionTemplata(env, origin) => Some((env, origin))
    }
  }
}

case class InterfaceDefinitionTemplata(
  // The paackage this interface was declared in.
  // Has the name of the surrounding environment, does NOT include interface's name.
  // See TMRE for more on these environments.
  declaringEnv: IEnvironment,

  // This is the env entry that the interface came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  originInterface: InterfaceA
) extends CitizenDefinitionTemplata {
  override def originCitizen: CitizenA = originInterface

  vassert(declaringEnv.fullName.packageCoord == originInterface.name.range.file.packageCoordinate)

  vpass()
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: TemplateTemplataType = {
    // Note that this might disagree with originStruct.tyype, which might not be a TemplateTemplataType().
    // In Compiler, StructTemplatas are templates, even if they have zero arguments.
    TemplateTemplataType(
      originInterface.genericParameters.map(_.rune.rune).map(originInterface.runeToType),
      KindTemplataType())
  }

  // Make sure we didn't accidentally code something to include the interface's name as
  // the last step.
  // This assertion is helpful now, but will false-positive trip when someone
  // tries to make an interface with the same name as its containing. At that point,
  // feel free to remove this assertion.
  (declaringEnv.fullName.localName, originInterface.name) match {
    case (CitizenNameT(envFunctionName, _), TopLevelCitizenDeclarationNameS(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }



  def getTemplateName(): INameT = {
    InterfaceTemplateNameT(originInterface.name.name)//, nameTranslator.translateCodeLocation(originInterface.name.range.begin))
  }

  def debugString: String = declaringEnv.fullName + ":" + originInterface.name
}

case class ImplDefinitionTemplata(
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
) extends ITemplata[ImplTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: ImplTemplataType = ImplTemplataType()
}

case class OwnershipTemplata(ownership: OwnershipT) extends ITemplata[OwnershipTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: OwnershipTemplataType = OwnershipTemplataType()
}
case class VariabilityTemplata(variability: VariabilityT) extends ITemplata[VariabilityTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: VariabilityTemplataType = VariabilityTemplataType()
}
case class MutabilityTemplata(mutability: MutabilityT) extends ITemplata[MutabilityTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: MutabilityTemplataType = MutabilityTemplataType()
}
case class LocationTemplata(location: LocationT) extends ITemplata[LocationTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: LocationTemplataType = LocationTemplataType()
}

case class BooleanTemplata(value: Boolean) extends ITemplata[BooleanTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: BooleanTemplataType = BooleanTemplataType()
}
case class IntegerTemplata(value: Long) extends ITemplata[IntegerTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: IntegerTemplataType = IntegerTemplataType()
}
case class StringTemplata(value: String) extends ITemplata[StringTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: StringTemplataType = StringTemplataType()
}
case class PrototypeTemplata(declarationRange: RangeS, prototype: PrototypeT) extends ITemplata[PrototypeTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: PrototypeTemplataType = PrototypeTemplataType()
}
case class IsaTemplata(declarationRange: RangeS, implName: IdT[IImplNameT], subKind: KindT, superKind: KindT) extends ITemplata[ImplTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: ImplTemplataType = ImplTemplataType()
}
case class CoordListTemplata(coords: Vector[CoordT]) extends ITemplata[PackTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: PackTemplataType = PackTemplataType(CoordTemplataType())
  vpass()

}

// ExternFunction/ImplTemplata are here because for example when we create an anonymous interface
// substruct, we want to add its forwarding functions and its impl to the environment, but it's
// very difficult to add the ImplA and FunctionA for those. So, we allow having coutputs like
// these directly in the environment.
// These should probably be renamed from Extern to something else... they could be supplied
// by plugins, but theyre also used internally.

case class ExternFunctionTemplata(header: FunctionHeaderT) extends ITemplata[ITemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: ITemplataType = vfail()
}
