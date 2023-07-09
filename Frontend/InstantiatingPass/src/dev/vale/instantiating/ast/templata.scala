package dev.vale.instantiating.ast

import dev.vale.postparsing._
import dev.vale.typing.env._
import dev.vale.typing.names._
import dev.vale.typing.types._
import dev.vale.{RangeS, StrI, vassert, vfail, vimpl, vpass, vwat}
import dev.vale.highertyping._
import dev.vale.typing.ast._
import dev.vale.typing.env._
import dev.vale.typing.types._

import scala.collection.immutable.List


object ITemplataI {
  def expectCoord[R <: IRegionsModeI](templata: ITemplataI[R]): ITemplataI[R] = {
    templata match {
      case t @ CoordTemplataI(_, _) => t
      case other => vfail(other)
    }
  }

  def expectCoordTemplata[R <: IRegionsModeI](templata: ITemplataI[R]): CoordTemplataI[R] = {
    templata match {
      case t @ CoordTemplataI(_, _) => t
      case other => vfail(other)
    }
  }

  def expectIntegerTemplata[R <: IRegionsModeI](templata: ITemplataI[R]): IntegerTemplataI[R] = {
    templata match {
      case t @ IntegerTemplataI(_) => t
      case _ => vfail()
    }
  }

  def expectMutabilityTemplata[R <: IRegionsModeI](templata: ITemplataI[R]): MutabilityTemplataI[R] = {
    templata match {
      case t @ MutabilityTemplataI(_) => t
      case _ => vfail()
    }
  }

  def expectVariabilityTemplata[R <: IRegionsModeI](templata: ITemplataI[R]): VariabilityTemplataI[R] = {
    templata match {
      case t @ VariabilityTemplataI(_) => t
      case _ => vfail()
    }
  }

  def expectKind[R <: IRegionsModeI](templata: ITemplataI[R]): ITemplataI[R] = {
    templata match {
      case t @ KindTemplataI(_) => t
      case _ => vfail()
    }
  }

  def expectKindTemplata[R <: IRegionsModeI](templata: ITemplataI[R]): KindTemplataI[R] = {
    templata match {
      case t @ KindTemplataI(_) => t
      case _ => vfail()
    }
  }

  def expectRegionTemplata[R <: IRegionsModeI](templata: ITemplataI[R]): RegionTemplataI[R] = {
    templata match {
      case t @ RegionTemplataI(_) => t
      case _ => vfail()
    }
  }

}

sealed trait ITemplataI[+R <: IRegionsModeI] {
  def expectCoordTemplata(): CoordTemplataI[R] = {
    this match {
      case c@CoordTemplataI(_, _) => c
      case other => vwat(other)
    }
  }

  def expectRegionTemplata(): RegionTemplataI[R] = {
    this match {
      case c@RegionTemplataI(_) => c
      case other => vwat(other)
    }
  }
}

//// The typing phase never makes one of these, they're purely abstract and conceptual in the
//// typing phase. The monomorphizer is the one that actually makes these templatas.
//case class RegionTemplataI[+R <: IRegionsModeI](pureHeight: Int) extends ITemplataI[R] {
//  vpass()
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//
//}

case class CoordTemplataI[+R <: IRegionsModeI](
    region: RegionTemplataI[R],
    coord: CoordI[R]
) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  this match {
    case CoordTemplataI(RegionTemplataI(-1), CoordI(ImmutableShareI, StrIT())) => {
      vpass()
    }
    case _ =>
  }

  vpass()
}
//case class PlaceholderTemplataI[+T <: ITemplataType](
//  fullNameT: IdI[R, IPlaceholderNameI],
//  tyype: T
//) extends ITemplataI[R] {
//  tyype match {
//    case CoordTemplataType() => vwat()
//    case KindTemplataType() => vwat()
//    case _ =>
//  }
//  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
//}
case class KindTemplataI[+R <: IRegionsModeI](kind: KindIT[R]) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class RuntimeSizedArrayTemplateTemplataI[+R <: IRegionsModeI]() extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class StaticSizedArrayTemplateTemplataI[+R <: IRegionsModeI]() extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}



case class FunctionTemplataI[+R <: IRegionsModeI](
  envId: IdI[R, FunctionTemplateNameI[R]]
) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;



  def getTemplateName(): IdI[R, INameI[R]] = vimpl()
}

case class StructDefinitionTemplataI[+R <: IRegionsModeI](
  envId: IdI[R, StructTemplateNameI[R]],
  tyype: TemplateTemplataType
) extends CitizenDefinitionTemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}

sealed trait CitizenDefinitionTemplataI[+R <: IRegionsModeI] extends ITemplataI[R]

case class InterfaceDefinitionTemplataI[+R <: IRegionsModeI](
  envId: IdI[R, InterfaceTemplateNameI[R]],
  tyype: TemplateTemplataType
) extends CitizenDefinitionTemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
}

case class ImplDefinitionTemplataI[+R <: IRegionsModeI](
  envId: IdI[R, INameI[R]]
//  // The paackage this interface was declared in.
//  // See TMRE for more on these environments.
//  env: IEnvironment,
////
////  // The containers are the structs/interfaces/impls/functions that this thing is inside.
////  // E.g. if LinkedList has a Node substruct, then the Node's templata will have one
////  // container, the LinkedList.
////  // See NTKPRR for why we have these parents.
////  containers: Vector[IContainer],
//
//  // This is the impl that the interface came from originally. It has all the parent
//  // structs and interfaces. See NTKPRR for more.
//  impl: ImplA
) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}

case class OwnershipTemplataI[+R <: IRegionsModeI](ownership: OwnershipI) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class VariabilityTemplataI[+R <: IRegionsModeI](variability: VariabilityI) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class MutabilityTemplataI[+R <: IRegionsModeI](mutability: MutabilityI) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class LocationTemplataI[+R <: IRegionsModeI](location: LocationI) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}

case class BooleanTemplataI[+R <: IRegionsModeI](value: Boolean) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class IntegerTemplataI[+R <: IRegionsModeI](value: Long) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class StringTemplataI[+R <: IRegionsModeI](value: String) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class PrototypeTemplataI[+R <: IRegionsModeI](declarationRange: RangeS, prototype: PrototypeI[R]) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class IsaTemplataI[+R <: IRegionsModeI](declarationRange: RangeS, implName: IdI[R, IImplNameI[R]], subKind: KindT, superKind: KindIT[R]) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
case class CoordListTemplataI[+R <: IRegionsModeI](coords: Vector[CoordI[R]]) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

  vpass()
}
case class RegionTemplataI[+R <: IRegionsModeI](pureHeight: Int) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}

// ExternFunction/ImplTemplata are here because for example when we create an anonymous interface
// substruct, we want to add its forwarding functions and its impl to the environment, but it's
// very difficult to add the ImplA and FunctionA for those. So, we allow having coutputs like
// these directly in the environment.
// These should probably be renamed from Extern to something else... they could be supplied
// by plugins, but theyre also used internally.

case class ExternFunctionTemplataI[+R <: IRegionsModeI](header: FunctionHeaderI) extends ITemplataI[R] {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;

}
