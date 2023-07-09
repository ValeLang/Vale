package dev.vale.instantiating.ast

import dev.vale.postparsing.IRuneS
import dev.vale.typing.ast._
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, IdT, FunctionNameT, IFunctionNameT, LambdaCitizenNameT}
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{StrI, vassertOne, vassertSome, vcurious, vfail, vimpl}
import dev.vale.typing.ast._
import dev.vale.typing.names._
import dev.vale.typing.types._

import scala.collection.mutable

case class InstantiationBoundArgumentsI(
  runeToFunctionBoundArg: Map[IRuneS, PrototypeI[sI]],
  runeToImplBoundArg: Map[IRuneS, IdI[sI, IImplNameI[sI]]])

case class HinputsI(
  interfaces: Vector[InterfaceDefinitionI],
  structs: Vector[StructDefinitionI],
//  emptyPackStructRef: StructTT,
  functions: Vector[FunctionDefinitionI],
//  immKindToDestructor: Map[KindT, PrototypeI],

  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  interfaceToEdgeBlueprints: Map[IdI[cI, IInterfaceNameI[cI]], InterfaceEdgeBlueprintI],
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  interfaceToSubCitizenToEdge: Map[IdI[cI, IInterfaceNameI[cI]], Map[IdI[cI, ICitizenNameI[cI]], EdgeI]],

//  instantiationNameToInstantiationBounds: Map[IdI[cI, IInstantiationNameI[cI]], InstantiationBoundArgumentsI],

  kindExports: Vector[KindExportI],
  functionExports: Vector[FunctionExportI],
//  kindExterns: Vector[KindExternI],
  functionExterns: Vector[FunctionExternI],
) {

  private val subCitizenToInterfaceToEdgeMutable = mutable.HashMap[IdI[cI, ICitizenNameI[cI]], mutable.HashMap[IdI[cI, IInterfaceNameI[cI]], EdgeI]]()
  interfaceToSubCitizenToEdge.foreach({ case (interface, subCitizenToEdge) =>
    subCitizenToEdge.foreach({ case (subCitizen, edge) =>
      subCitizenToInterfaceToEdgeMutable
        .getOrElseUpdate(subCitizen, mutable.HashMap[IdI[cI, IInterfaceNameI[cI]], EdgeI]())
        .put(interface, edge)
    })
  })
  val subCitizenToInterfaceToEdge: Map[IdI[cI, ICitizenNameI[cI]], Map[IdI[cI, IInterfaceNameI[cI]], EdgeI]] =
    subCitizenToInterfaceToEdgeMutable.mapValues(_.toMap).toMap

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vfail() // Would need a really good reason to hash something this big

  def lookupStruct(structId: IdI[cI, IStructNameI[cI]]): StructDefinitionI = {
    vassertSome(structs.find(_.instantiatedCitizen.id == structId))
  }

  def lookupInterface(interfaceId: IdI[cI, IInterfaceNameI[cI]]): InterfaceDefinitionI = {
    vassertSome(interfaces.find(_.instantiatedCitizen.id == interfaceId))
  }

  def lookupStructByTemplate(structTemplateName: IStructTemplateNameI[cI]): StructDefinitionI = {
    vassertSome(structs.find(_.instantiatedCitizen.id.localName.template == structTemplateName))
  }

  def lookupInterfaceByTemplate(interfaceTemplateName: IInterfaceTemplateNameI[cI]): InterfaceDefinitionI = {
    vassertSome(interfaces.find(_.instantiatedCitizen.id.localName.template == interfaceTemplateName))
  }

  def lookupImplByTemplate(implTemplateName: IImplTemplateNameI[cI]): EdgeI = {
    vassertSome(interfaceToSubCitizenToEdge.flatMap(_._2.values).find(_.edgeId.localName.template == implTemplateName))
  }

  def lookupEdge(implId: IdI[cI, IImplNameI[cI]]): EdgeI = {
    vassertOne(interfaceToSubCitizenToEdge.flatMap(_._2.values).find(_.edgeId == implId))
  }

//  def getInstantiationBoundArgs(instantiationName: IdI[cI, IInstantiationNameI[cI]]): InstantiationBoundArgumentsI = {
//    vassertSome(instantiationNameToInstantiationBounds.get(instantiationName))
//  }

//  def lookupStructByTemplateFullName(structTemplateId: IdI[IStructTemplateNameI]): StructDefinitionI = {
//    vassertSome(structs.find(_.templateName == structTemplateId))
//  }
//
//  def lookupInterfaceByTemplateFullName(interfaceTemplateId: IdI[IInterfaceTemplateNameI]): InterfaceDefinitionI = {
//    vassertSome(interfaces.find(_.templateName == interfaceTemplateId))
//  }
//
//  def lookupCitizenByTemplateFullName(interfaceTemplateId: IdI[ICitizenTemplateNameI]): CitizenDefinitionI = {
//    interfaceTemplateId match {
//      case IdI(packageCoord, initSteps, t: IStructTemplateNameI) => {
//        lookupStructByTemplateFullName(IdI(packageCoord, initSteps, t))
//      }
//      case IdI(packageCoord, initSteps, t: IInterfaceTemplateNameI) => {
//        lookupInterfaceByTemplateFullName(IdI(packageCoord, initSteps, t))
//      }
//    }
//  }
//
//  def lookupStructByTemplateName(structTemplateName: StructTemplateNameI): StructDefinitionI = {
//    vassertOne(structs.filter(_.templateName.localName == structTemplateName))
//  }
//
//  def lookupInterfaceByTemplateName(interfaceTemplateName: InterfaceTemplateNameI): InterfaceDefinitionI = {
//    vassertSome(interfaces.find(_.templateName.localName == interfaceTemplateName))
//  }

  // def lookupFunction(signature2: SignatureI[cI]): Option[FunctionDefinitionI] = {
  //   functions.find(_.header.toSignature == signature2).headOption
  // }

  def lookupFunction(funcTemplateName: IFunctionTemplateNameI[cI]): Option[FunctionDefinitionI] = {
    functions.find(_.header.id.localName.template == funcTemplateName).headOption
  }

  def lookupFunction(humanName: String): FunctionDefinitionI = {
    val matches = functions.filter(f => {
      f.header.id.localName match {
        case FunctionNameIX(n, _, _) if n.humanName.str == humanName => true
        case _ => false
      }
    })
    if (matches.size == 0) {
      vfail("Function \"" + humanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def lookupStruct(humanName: String): StructDefinitionI = {
    val matches = structs.filter(s => {
      s.instantiatedCitizen.id.localName match {
        case StructNameI(StructTemplateNameI(n), _) if n.str == humanName => true
        case _ => false
      }
    })
    if (matches.size == 0) {
      vfail("Struct \"" + humanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def lookupImpl(
    subCitizenIT: IdI[cI, ICitizenNameI[cI]],
    interfaceIT: IdI[cI, IInterfaceNameI[cI]]):
  EdgeI = {
    vassertSome(
      vassertSome(interfaceToSubCitizenToEdge.get(interfaceIT))
        .get(subCitizenIT))
  }

  def lookupInterface(humanName: String): InterfaceDefinitionI = {
    val matches = interfaces.filter(s => {
      s.instantiatedCitizen.id.localName match {
        case InterfaceNameI(InterfaceTemplateNameI(n), _) if n.str == humanName => true
        case _ => false
      }
    })
    if (matches.size == 0) {
      vfail("Interface \"" + humanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def lookupUserFunction(humanName: String): FunctionDefinitionI = {
    val matches =
      functions
        .filter(function => simpleNameI.unapply(function.header.id).contains(humanName))
        .filter(_.header.isUserFunction)
    if (matches.size == 0) {
      vfail("Not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

//  def nameIsLambdaIn(name: IdI[cI, IFunctionNameI[cI]], needleFunctionHumanName: String): Boolean = {
//    val first = name.steps.head
//    val lastTwo = name.steps.slice(name.steps.size - 2, name.steps.size)
//    (first, lastTwo) match {
//      case (
//        FunctionNameIX(FunctionTemplateNameI(StrI(hayFunctionHumanName), _), _, _),
//        Vector(
//          LambdaCitizenTemplateNameI(_),
//          LambdaCallFunctionNameI(LambdaCallFunctionTemplateNameI(_, _), _, _)))
//        if hayFunctionHumanName == needleFunctionHumanName => true
//      case _ => false
//    }
//  }

//  def lookupLambdasIn(needleFunctionHumanName: String): Vector[FunctionDefinitionI] = {
//    functions.filter(f => nameIsLambdaIn(f.header.id, needleFunctionHumanName)).toVector
//  }

//  def lookupLambdaIn(needleFunctionHumanName: String): FunctionDefinitionI = {
//    vassertOne(lookupLambdasIn(needleFunctionHumanName))
//  }

  def getAllNonExternFunctions: Iterable[FunctionDefinitionI] = {
    functions.filter(!_.header.isExtern)
  }

  def getAllUserFunctions: Iterable[FunctionDefinitionI] = {
    functions.filter(_.header.isUserFunction)
  }
}
