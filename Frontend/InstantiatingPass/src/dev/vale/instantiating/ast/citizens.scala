package dev.vale.instantiating.ast

import dev.vale.postparsing._
import dev.vale._

import scala.collection.immutable.Map

// A "citizen" is a struct or an interface.
trait CitizenDefinitionI {
//  def genericParamTypes: Vector[ITemplataType]
  def instantiatedCitizen: ICitizenIT[cI]
}

case class StructDefinitionI(
//  templateName: IdI[cI, IStructTemplateNameI],
  // In typing pass, this will have placeholders. Monomorphizing will give it a real name.
  instantiatedCitizen: StructIT[cI],
  attributes: Vector[ICitizenAttributeI],
  weakable: Boolean,
  mutability: MutabilityI,
  members: Vector[StructMemberI],
  isClosure: Boolean,
  runeToFunctionBound: Map[IRuneS, IdI[cI, FunctionBoundNameI[cI]]],
  runeToImplBound: Map[IRuneS, IdI[cI, ImplBoundNameI[cI]]],
) extends CitizenDefinitionI {
//  override def genericParamTypes: Vector[ITemplataType] = {
//    instantiatedCitizen.id.localName.templateArgs.map(_.tyype)
//  }

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

//  override def getRef: StructIT = ref
//
//  def getMember(memberName: StrI): StructMemberI = {
//    members.find(p => p.name.equals(CodeVarNameI(memberName))) match {
//      case None => vfail("Couldn't find member " + memberName)
//      case Some(member) => member
//    }
//  }
//
//  private def getIndex(memberName: IVarNameI): Int = {
//    members.zipWithIndex.find(p => p._1.name.equals(memberName)) match {
//      case None => vfail("wat")
//      case Some((member, index)) => index
//    }
//  }

  def getMemberAndIndex(needleName: IVarNameI[cI]): Option[(StructMemberI, Int)] = {
    members.zipWithIndex
      .foreach({
        case (m @ StructMemberI(hayName, _, _), index) if hayName == needleName => {
          return Some((m, index))
        }
        case _ =>
      })
    None
  }
}

case class StructMemberI(
  name: IVarNameI[cI],
  // In the case of address members, this refers to the variability of the pointee variable.
  variability: VariabilityI,
  tyype: IMemberTypeI
) {
  vpass()
}

sealed trait IMemberTypeI  {
  def reference: CoordI[cI]

  def expectReferenceMember(): ReferenceMemberTypeI = {
    this match {
      case r @ ReferenceMemberTypeI(_) => r
      case a @ AddressMemberTypeI(_) => vfail("Expected reference member, was address member!")
    }
  }
  def expectAddressMember(): AddressMemberTypeI = {
    this match {
      case r @ ReferenceMemberTypeI(_) => vfail("Expected reference member, was address member!")
      case a @ AddressMemberTypeI(_) => a
    }
  }
}

case class AddressMemberTypeI(reference: CoordI[cI]) extends IMemberTypeI
case class ReferenceMemberTypeI(reference: CoordI[cI]) extends IMemberTypeI

case class InterfaceDefinitionI(
//  templateName: IdI[cI, IInterfaceTemplateNameI],
  instantiatedInterface: InterfaceIT[cI],
//  ref: InterfaceIT,
  attributes: Vector[ICitizenAttributeI],
  weakable: Boolean,
  mutability: MutabilityI,
  runeToFunctionBound: Map[IRuneS, IdI[cI, FunctionBoundNameI[cI]]],
  runeToImplBound: Map[IRuneS, IdI[cI, ImplBoundNameI[cI]]],
  // This does not include abstract functions declared outside the interface.
  // Note from later: Though, sometimes macros add functions into the inside.
  // See IMRFDI for why we need to remember only the internal methods here.
  internalMethods: Vector[(PrototypeI[cI], Int)]
) extends CitizenDefinitionI {
//  override def genericParamTypes: Vector[ITemplataType] = {
//    instantiatedCitizen.id.localName.templateArgs.map(_.tyype)
//  }

  override def instantiatedCitizen: ICitizenIT[cI] = instantiatedInterface
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
//  override def getRef = ref
}
