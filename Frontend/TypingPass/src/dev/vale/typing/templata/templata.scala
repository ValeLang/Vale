package dev.vale.typing.templata

import dev.vale.highertyping.{FunctionA, ImplA, InterfaceA, StructA}
import dev.vale.postparsing._
import dev.vale.typing.ast.{FunctionHeaderT, PrototypeT}
import dev.vale.typing.env.IInDenizenEnvironmentT
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, IdT, FunctionNameT, IFunctionNameT, IImplNameT, INameT, InterfaceTemplateNameT, KindPlaceholderNameT}
import dev.vale.typing.types._
import dev.vale.{RangeS, StrI, vassert, vfail, vimpl, vpass, vwat}
import dev.vale.highertyping._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import dev.vale.typing.types._

import scala.collection.immutable.List


object ITemplataT {
  def expectMutability(templata: ITemplataT[ITemplataType]): ITemplataT[MutabilityTemplataType] = {
    templata match {
      case t @ MutabilityTemplataT(_) => t
      case PlaceholderTemplataT(idT, MutabilityTemplataType()) => PlaceholderTemplataT(idT, MutabilityTemplataType())
      case _ => vfail()
    }
  }

  def expectVariability(templata: ITemplataT[ITemplataType]): ITemplataT[VariabilityTemplataType] = {
    templata match {
      case t @ VariabilityTemplataT(_) => t
      case PlaceholderTemplataT(idT, VariabilityTemplataType()) => PlaceholderTemplataT(idT, VariabilityTemplataType())
      case _ => vfail()
    }
  }

  def expectInteger(templata: ITemplataT[ITemplataType]): ITemplataT[IntegerTemplataType] = {
    templata match {
      case t @ IntegerTemplataT(_) => t
      case PlaceholderTemplataT(idT, IntegerTemplataType()) => PlaceholderTemplataT(idT, IntegerTemplataType())
      case other => vfail(other)
    }
  }

  def expectCoord(templata: ITemplataT[ITemplataType]): ITemplataT[CoordTemplataType] = {
    templata match {
      case t @ CoordTemplataT(_) => t
      case PlaceholderTemplataT(idT, CoordTemplataType()) => PlaceholderTemplataT(idT, CoordTemplataType())
      case other => vfail(other)
    }
  }

  def expectCoordTemplata(templata: ITemplataT[ITemplataType]): CoordTemplataT = {
    templata match {
      case t @ CoordTemplataT(_) => t
      case other => vfail(other)
    }
  }

  def expectIntegerTemplata(templata: ITemplataT[ITemplataType]): IntegerTemplataT = {
    templata match {
      case t @ IntegerTemplataT(_) => t
      case _ => vfail()
    }
  }

  def expectMutabilityTemplata(templata: ITemplataT[ITemplataType]): MutabilityTemplataT = {
    templata match {
      case t @ MutabilityTemplataT(_) => t
      case _ => vfail()
    }
  }

  def expectVariabilityTemplata(templata: ITemplataT[ITemplataType]): ITemplataT[VariabilityTemplataType] = {
    templata match {
      case t @ VariabilityTemplataT(_) => t
      case _ => vfail()
    }
  }

  def expectKind(templata: ITemplataT[ITemplataType]): ITemplataT[KindTemplataType] = {
    templata match {
      case t @ KindTemplataT(_) => t
      case PlaceholderTemplataT(idT, KindTemplataType()) => PlaceholderTemplataT(idT, KindTemplataType())
      case _ => vfail()
    }
  }

  def expectKindTemplata(templata: ITemplataT[ITemplataType]): KindTemplataT = {
    templata match {
      case t @ KindTemplataT(_) => t
      case _ => vfail()
    }
  }
}

sealed trait ITemplataT[+T <: ITemplataType]  {
  def tyype: T
}

case class CoordTemplataT(coord: CoordT) extends ITemplataT[CoordTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: CoordTemplataType = CoordTemplataType()

  vpass()
}
case class PlaceholderTemplataT[+T <: ITemplataType](
  idT: IdT[KindPlaceholderNameT],
  tyype: T
) extends ITemplataT[T] {
  tyype match {
    case CoordTemplataType() => vwat()
    case KindTemplataType() => vwat()
    case _ =>
  }
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}
case class KindTemplataT(kind: KindT) extends ITemplataT[KindTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: KindTemplataType = KindTemplataType()
}
case class RuntimeSizedArrayTemplateTemplataT() extends ITemplataT[TemplateTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: TemplateTemplataType = TemplateTemplataType(Vector(MutabilityTemplataType(), CoordTemplataType()), KindTemplataType())
}
case class StaticSizedArrayTemplateTemplataT() extends ITemplataT[TemplateTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: TemplateTemplataType = TemplateTemplataType(Vector(IntegerTemplataType(), MutabilityTemplataType(), VariabilityTemplataType(), CoordTemplataType()), KindTemplataType())
}



case class FunctionTemplataT(
  // The environment this function was declared in.
  // Has the name of the surrounding environment, does NOT include function's name.
  // We need this because, for example, lambdas need to find their underlying struct
  // somewhere.
  // See TMRE for more on these environments.
  outerEnv: IEnvironmentT,

  // This is the env entry that the function came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  function: FunctionA
) extends ITemplataT[TemplateTemplataType] {
  vassert(outerEnv.id.packageCoord == function.name.packageCoordinate)

  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  override def equals(obj: Any): Boolean = {
    obj match {
      case FunctionTemplataT(thatEnv, thatFunction) => {
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
  (outerEnv.id.localName, function.name) match {
    case (FunctionNameT(envFunctionName, _, _), FunctionNameS(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }



  def getTemplateName(): IdT[INameT] = {
    vimpl()
//    outerEnv.fullName.addStep(nameTranslator.translateFunctionNameToTemplateName(function.name))
  }

  def debugString: String = outerEnv.id + ":" + function.name
}

case class StructDefinitionTemplataT(
  // The paackage this interface was declared in.
  // has the name of the surrounding environment, does NOT include struct's name.
  // See TMRE for more on these environments.
  declaringEnv: IEnvironmentT,

  // This is the env entry that the struct came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  originStruct: StructA,
) extends CitizenDefinitionTemplataT {
  override def originCitizen: CitizenA = originStruct

  vassert(declaringEnv.id.packageCoord == originStruct.name.range.file.packageCoordinate)

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
  (declaringEnv.id.localName, originStruct.name) match {
    case (CitizenNameT(envFunctionName, _), TopLevelCitizenDeclarationNameS(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }

  def debugString: String = declaringEnv.id + ":" + originStruct.name
}

sealed trait IContainer
case class ContainerInterface(interface: InterfaceA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ContainerStruct(struct: StructA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ContainerFunction(function: FunctionA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ContainerImpl(impl: ImplA) extends IContainer { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

sealed trait CitizenDefinitionTemplataT extends ITemplataT[TemplateTemplataType] {
  def declaringEnv: IEnvironmentT
  def originCitizen: CitizenA
}
object CitizenDefinitionTemplataT {
  def unapply(c: CitizenDefinitionTemplataT): Option[(IEnvironmentT, CitizenA)] = {
    c match {
      case StructDefinitionTemplataT(env, origin) => Some((env, origin))
      case InterfaceDefinitionTemplataT(env, origin) => Some((env, origin))
    }
  }
}

case class InterfaceDefinitionTemplataT(
  // The paackage this interface was declared in.
  // Has the name of the surrounding environment, does NOT include interface's name.
  // See TMRE for more on these environments.
  declaringEnv: IEnvironmentT,

  // This is the env entry that the interface came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  originInterface: InterfaceA
) extends CitizenDefinitionTemplataT {
  override def originCitizen: CitizenA = originInterface

  vassert(declaringEnv.id.packageCoord == originInterface.name.range.file.packageCoordinate)

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
  (declaringEnv.id.localName, originInterface.name) match {
    case (CitizenNameT(envFunctionName, _), TopLevelCitizenDeclarationNameS(sourceName, _)) => vassert(envFunctionName != sourceName)
    case _ =>
  }



  def getTemplateName(): INameT = {
    InterfaceTemplateNameT(originInterface.name.name)//, nameTranslator.translateCodeLocation(originInterface.name.range.begin))
  }

  def debugString: String = declaringEnv.id + ":" + originInterface.name
}

case class ImplDefinitionTemplataT(
  // The paackage this interface was declared in.
  // See TMRE for more on these environments.
  env: IEnvironmentT,
//
//  // The containers are the structs/interfaces/impls/functions that this thing is inside.
//  // E.g. if LinkedList has a Node substruct, then the Node's templata will have one
//  // container, the LinkedList.
//  // See NTKPRR for why we have these parents.
//  containers: Vector[IContainer],

  // This is the impl that the interface came from originally. It has all the parent
  // structs and interfaces. See NTKPRR for more.
  impl: ImplA
) extends ITemplataT[ImplTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: ImplTemplataType = ImplTemplataType()
}

case class OwnershipTemplataT(ownership: OwnershipT) extends ITemplataT[OwnershipTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: OwnershipTemplataType = OwnershipTemplataType()
}
case class VariabilityTemplataT(variability: VariabilityT) extends ITemplataT[VariabilityTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: VariabilityTemplataType = VariabilityTemplataType()
}
case class MutabilityTemplataT(mutability: MutabilityT) extends ITemplataT[MutabilityTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: MutabilityTemplataType = MutabilityTemplataType()
}
case class LocationTemplataT(location: LocationT) extends ITemplataT[LocationTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: LocationTemplataType = LocationTemplataType()
}

case class BooleanTemplataT(value: Boolean) extends ITemplataT[BooleanTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: BooleanTemplataType = BooleanTemplataType()
}
case class IntegerTemplataT(value: Long) extends ITemplataT[IntegerTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: IntegerTemplataType = IntegerTemplataType()
}
case class StringTemplataT(value: String) extends ITemplataT[StringTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: StringTemplataType = StringTemplataType()
}
case class PrototypeTemplataT(declarationRange: RangeS, prototype: PrototypeT) extends ITemplataT[PrototypeTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: PrototypeTemplataType = PrototypeTemplataType()
}
case class IsaTemplataT(declarationRange: RangeS, implName: IdT[IImplNameT], subKind: KindT, superKind: KindT) extends ITemplataT[ImplTemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: ImplTemplataType = ImplTemplataType()
}
case class CoordListTemplataT(coords: Vector[CoordT]) extends ITemplataT[PackTemplataType] {
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

case class ExternFunctionTemplataT(header: FunctionHeaderT) extends ITemplataT[ITemplataType] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  override def tyype: ITemplataType = vfail()
}
