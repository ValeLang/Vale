package dev.vale.typing.ast

import dev.vale.postparsing._
import dev.vale.typing.TemplataCompiler
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{StrI, vcurious, vfail, vpass}

import scala.collection.immutable.Map

// A "citizen" is a struct or an interface.
trait CitizenDefinitionT {
  def templateName: IdT[ICitizenTemplateNameT]
  def genericParamTypes: Vector[ITemplataType]
  def instantiatedCitizen: ICitizenTT
}

case class StructDefinitionT(
  templateName: IdT[IStructTemplateNameT],
  // In typing pass, this will have placeholders. Monomorphizing will give it a real name.
  instantiatedCitizen: StructTT,
  attributes: Vector[ICitizenAttributeT],
  weakable: Boolean,
  mutability: ITemplataT[MutabilityTemplataType],
  members: Vector[IStructMemberT],
  isClosure: Boolean,
  runeToFunctionBound: Map[IRuneS, IdT[FunctionBoundNameT]],
  runeToImplBound: Map[IRuneS, IdT[ImplBoundNameT]],
) extends CitizenDefinitionT {
  override def genericParamTypes: Vector[ITemplataType] = {
    instantiatedCitizen.id.localName.templateArgs.map(_.tyype)
  }

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

//  override def getRef: StructTT = ref
//
//  def getMember(memberName: StrI): NormalStructMemberT = {
//    members.find(p => p.name.equals(CodeVarNameT(memberName))) match {
//      case None => vfail("Couldn't find member " + memberName)
//      case Some(member) => member
//    }
//  }
//
//  private def getIndex(memberName: IVarNameT): Int = {
//    members.zipWithIndex.find(p => p._1.name.equals(memberName)) match {
//      case None => vfail("wat")
//      case Some((member, index)) => index
//    }
//  }

  def getMemberAndIndex(needleName: IVarNameT): Option[(NormalStructMemberT, Int)] = {
    members.zipWithIndex
      .foreach({
        case (m @ NormalStructMemberT(hayName, _, _), index) if hayName == needleName => {
          return Some((m, index))
        }
        case _ =>
      })
    None
  }
}

sealed trait IStructMemberT {
  def name: IVarNameT
}

case class NormalStructMemberT(
  name: IVarNameT,
  // In the case of address members, this refers to the variability of the pointee variable.
  variability: VariabilityT,
  tyype: IMemberTypeT
) extends IStructMemberT {
  vpass()
}

case class VariadicStructMemberT(
  name: IVarNameT,
  tyype: PlaceholderTemplataT[PackTemplataType]
) extends IStructMemberT {
  vpass()
}

sealed trait IMemberTypeT  {
  def reference: CoordT

  def expectReferenceMember(): ReferenceMemberTypeT = {
    this match {
      case r @ ReferenceMemberTypeT(_) => r
      case a @ AddressMemberTypeT(_) => vfail("Expected reference member, was address member!")
    }
  }
  def expectAddressMember(): AddressMemberTypeT = {
    this match {
      case r @ ReferenceMemberTypeT(_) => vfail("Expected reference member, was address member!")
      case a @ AddressMemberTypeT(_) => a
    }
  }
}

case class AddressMemberTypeT(reference: CoordT) extends IMemberTypeT
case class ReferenceMemberTypeT(reference: CoordT) extends IMemberTypeT

case class InterfaceDefinitionT(
  templateName: IdT[IInterfaceTemplateNameT],
  instantiatedInterface: InterfaceTT,
  ref: InterfaceTT,
  attributes: Vector[ICitizenAttributeT],
  weakable: Boolean,
  mutability: ITemplataT[MutabilityTemplataType],
  runeToFunctionBound: Map[IRuneS, IdT[FunctionBoundNameT]],
  runeToImplBound: Map[IRuneS, IdT[ImplBoundNameT]],
  // This does not include abstract functions declared outside the interface.
  // Note from later: Though, sometimes macros add functions into the inside.
  // See IMRFDI for why we need to remember only the internal methods here.
  internalMethods: Vector[(PrototypeT, Int)]
) extends CitizenDefinitionT {
  override def genericParamTypes: Vector[ITemplataType] = {
    instantiatedCitizen.id.localName.templateArgs.map(_.tyype)
  }

  override def instantiatedCitizen: ICitizenTT = instantiatedInterface
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def getRef = ref
}
