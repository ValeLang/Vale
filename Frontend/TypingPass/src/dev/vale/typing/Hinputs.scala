package dev.vale.typing

import dev.vale.postparsing.{IRuneS, ITemplataType}
import dev.vale.typing.ast.{EdgeT, FunctionDefinitionT, FunctionExportT, FunctionExternT, InterfaceEdgeBlueprintT, KindExportT, KindExternT, PrototypeT, SignatureT}
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, FunctionNameT, IFunctionNameT, IdT, LambdaCitizenNameT}
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{PackageCoordinate, StrI, vassert, vassertOne, vassertSome, vcurious, vfail, vimpl}
import dev.vale.typing.ast._
import dev.vale.typing.names._
import dev.vale.typing.types._

import scala.collection.mutable

case class InstantiationBoundArgumentsT(
  runeToFunctionBoundArg: Map[IRuneS, PrototypeT],
  runeToImplBoundArg: Map[IRuneS, IdT[IImplNameT]])

case class Hinputs(
  interfaces: Vector[InterfaceDefinitionT],
  structs: Vector[StructDefinitionT],
//  emptyPackStructRef: StructTT,
  functions: Vector[FunctionDefinitionT],
//  immKindToDestructor: Map[KindT, PrototypeT],

  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  interfaceToEdgeBlueprints: Map[IdT[IInterfaceNameT], InterfaceEdgeBlueprintT],
  // The typing pass keys this by placeholdered name, and the instantiator keys this by non-placeholdered names
  interfaceToSubCitizenToEdge: Map[IdT[IInterfaceNameT], Map[IdT[ICitizenNameT], EdgeT]],

  instantiationNameToInstantiationBounds: Map[IdT[IInstantiationNameT], InstantiationBoundArgumentsT],

  kindExports: Vector[KindExportT],
  functionExports: Vector[FunctionExportT],
  kindExterns: Vector[KindExternT],
  functionExterns: Vector[FunctionExternT],
) {

  private val subCitizenToInterfaceToEdgeMutable = mutable.HashMap[IdT[ICitizenNameT], mutable.HashMap[IdT[IInterfaceNameT], EdgeT]]()
  interfaceToSubCitizenToEdge.foreach({ case (interface, subCitizenToEdge) =>
    subCitizenToEdge.foreach({ case (subCitizen, edge) =>
      subCitizenToInterfaceToEdgeMutable
        .getOrElseUpdate(subCitizen, mutable.HashMap[IdT[IInterfaceNameT], EdgeT]())
        .put(interface, edge)
    })
  })
  val subCitizenToInterfaceToEdge: Map[IdT[ICitizenNameT], Map[IdT[IInterfaceNameT], EdgeT]] =
    subCitizenToInterfaceToEdgeMutable.mapValues(_.toMap).toMap

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vfail() // Would need a really good reason to hash something this big

  def lookupStruct(structId: IdT[IStructNameT]): StructDefinitionT = {
    vassertSome(structs.find(_.instantiatedCitizen.id == structId))
  }

  def lookupStructByTemplate(structTemplateName: IStructTemplateNameT): StructDefinitionT = {
    vassertSome(structs.find(_.instantiatedCitizen.id.localName.template == structTemplateName))
  }

  def lookupInterfaceByTemplate(interfaceTemplateName: IInterfaceTemplateNameT): InterfaceDefinitionT = {
    vassertSome(interfaces.find(_.instantiatedCitizen.id.localName.template == interfaceTemplateName))
  }

  def lookupImplByTemplate(implTemplateName: IImplTemplateNameT): EdgeT = {
    vassertSome(interfaceToSubCitizenToEdge.flatMap(_._2.values).find(_.edgeId.localName.template == implTemplateName))
  }

  def lookupInterface(interfaceId: IdT[IInterfaceNameT]): InterfaceDefinitionT = {
    vassertSome(interfaces.find(_.instantiatedCitizen.id == interfaceId))
  }

  def lookupEdge(implId: IdT[IImplNameT]): EdgeT = {
    vassertOne(interfaceToSubCitizenToEdge.flatMap(_._2.values).find(_.edgeId == implId))
  }

  def getInstantiationBoundArgs(instantiationName: IdT[IInstantiationNameT]): InstantiationBoundArgumentsT = {
    vassertSome(instantiationNameToInstantiationBounds.get(instantiationName))
  }

  def lookupStructByTemplateId(structTemplateId: IdT[IStructTemplateNameT]): StructDefinitionT = {
    vassertSome(structs.find(_.templateName == structTemplateId))
  }

  def lookupInterfaceByTemplateId(interfaceTemplateId: IdT[IInterfaceTemplateNameT]): InterfaceDefinitionT = {
    vassertSome(interfaces.find(_.templateName == interfaceTemplateId))
  }

  def lookupCitizenByTemplateId(interfaceTemplateId: IdT[ICitizenTemplateNameT]): CitizenDefinitionT = {
    interfaceTemplateId match {
      case IdT(packageCoord, initSteps, t: IStructTemplateNameT) => {
        lookupStructByTemplateId(IdT(packageCoord, initSteps, t))
      }
      case IdT(packageCoord, initSteps, t: IInterfaceTemplateNameT) => {
        lookupInterfaceByTemplateId(IdT(packageCoord, initSteps, t))
      }
    }
  }

  def lookupStructByTemplateName(structTemplateName: StructTemplateNameT): StructDefinitionT = {
    vassertOne(structs.filter(_.templateName.localName == structTemplateName))
  }

  def lookupInterfaceByTemplateName(interfaceTemplateName: InterfaceTemplateNameT): InterfaceDefinitionT = {
    vassertSome(interfaces.find(_.templateName.localName == interfaceTemplateName))
  }

  def lookupFunction(signature2: SignatureT): Option[FunctionDefinitionT] = {
    functions.find(_.header.toSignature == signature2).headOption
  }

  def lookupFunction(funcTemplateName: IFunctionTemplateNameT): Option[FunctionDefinitionT] = {
    functions.find(_.header.id.localName.template == funcTemplateName).headOption
  }

  def lookupFunction(humanName: String): FunctionDefinitionT = {
    val matches = functions.filter(f => {
      f.header.id.localName match {
        case FunctionNameT(n, _, _) if n.humanName.str == humanName => true
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

  def lookupStruct(humanName: String): StructDefinitionT = {
    val matches = structs.filter(s => {
      s.templateName.localName match {
        case StructTemplateNameT(n) if n.str == humanName => true
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
    subCitizenTT: IdT[ICitizenNameT],
    interfaceTT: IdT[IInterfaceNameT]):
  EdgeT = {
    vassertSome(
      vassertSome(interfaceToSubCitizenToEdge.get(interfaceTT))
        .get(subCitizenTT))
  }

  def lookupInterface(humanName: String): InterfaceDefinitionT = {
    val matches = interfaces.filter(s => {
      s.templateName.localName match {
        case InterfaceTemplateNameT(n) if n.str == humanName => true
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

  def lookupUserFunction(humanName: String): FunctionDefinitionT = {
    val matches =
      functions
        .filter(function => simpleName.unapply(function.header.id).contains(humanName))
        .filter(_.header.isUserFunction)
    if (matches.size == 0) {
      vfail("Not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def nameIsLambdaIn(name: IdT[IFunctionNameT], needleFunctionHumanName: String): Boolean = {
    val first = name.steps.head
    val lastTwo = name.steps.slice(name.steps.size - 2, name.steps.size)
    (first, lastTwo) match {
      case (
        FunctionNameT(FunctionTemplateNameT(StrI(hayFunctionHumanName), _), _, _),
        Vector(
          LambdaCitizenTemplateNameT(_),
          LambdaCallFunctionNameT(LambdaCallFunctionTemplateNameT(_, _), _, _)))
        if hayFunctionHumanName == needleFunctionHumanName => true
      case _ => false
    }
  }

  def lookupLambdasIn(needleFunctionHumanName: String): Vector[FunctionDefinitionT] = {
    functions.filter(f => nameIsLambdaIn(f.header.id, needleFunctionHumanName)).toVector
  }

  def lookupLambdaIn(needleFunctionHumanName: String): FunctionDefinitionT = {
    vassertOne(lookupLambdasIn(needleFunctionHumanName))
  }

  def getAllNonExternFunctions: Iterable[FunctionDefinitionT] = {
    functions.filter(!_.header.isExtern)
  }

  def getAllUserFunctions: Iterable[FunctionDefinitionT] = {
    functions.filter(_.header.isUserFunction)
  }
}
