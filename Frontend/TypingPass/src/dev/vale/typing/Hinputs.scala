package dev.vale.typing

import dev.vale.postparsing.IRuneS
import dev.vale.typing.ast.{EdgeT, FunctionExportT, FunctionExternT, FunctionT, InterfaceEdgeBlueprint, KindExportT, KindExternT, PrototypeT, SignatureT}
import dev.vale.typing.names.{CitizenNameT, CitizenTemplateNameT, FullNameT, FunctionNameT, IFunctionNameT, LambdaCitizenNameT}
import dev.vale.typing.templata.{PrototypeTemplata, simpleName}
import dev.vale.typing.types._
import dev.vale.{StrI, vassertOne, vassertSome, vcurious, vfail, vimpl}
import dev.vale.typing.ast._
import dev.vale.typing.names._
import dev.vale.typing.types._

import scala.collection.mutable

case class InstantiationBoundArguments(
  runeToFunctionBoundArg: Map[IRuneS, PrototypeT],
  runeToImplBoundArg: Map[IRuneS, FullNameT[IImplNameT]])

case class Hinputs(
  interfaces: Vector[InterfaceDefinitionT],
  structs: Vector[StructDefinitionT],
//  emptyPackStructRef: StructTT,
  functions: Vector[FunctionT],
//  immKindToDestructor: Map[KindT, PrototypeT],

  // The typing pass keys this by placeholdered name, and the monomorphizer keys this by non-placeholdered names
  interfaceToEdgeBlueprints: Map[FullNameT[IInterfaceNameT], InterfaceEdgeBlueprint],
  // The typing pass keys this by placeholdered name, and the monomorphizer keys this by non-placeholdered names
  interfaceToSubCitizenToEdge: Map[FullNameT[IInterfaceNameT], Map[FullNameT[ICitizenNameT], EdgeT]],

  instantiationNameToInstantiationBounds: Map[FullNameT[IInstantiationNameT], InstantiationBoundArguments],

  kindExports: Vector[KindExportT],
  functionExports: Vector[FunctionExportT],
  kindExterns: Vector[KindExternT],
  functionExterns: Vector[FunctionExternT],
) {

  private val subCitizenToInterfaceToEdgeMutable = mutable.HashMap[FullNameT[ICitizenNameT], mutable.HashMap[FullNameT[IInterfaceNameT], EdgeT]]()
  interfaceToSubCitizenToEdge.foreach({ case (interface, subCitizenToEdge) =>
    subCitizenToEdge.foreach({ case (subCitizen, edge) =>
      subCitizenToInterfaceToEdgeMutable
        .getOrElseUpdate(subCitizen, mutable.HashMap[FullNameT[IInterfaceNameT], EdgeT]())
        .put(interface, edge)
    })
  })
  val subCitizenToInterfaceToEdge: Map[FullNameT[ICitizenNameT], Map[FullNameT[IInterfaceNameT], EdgeT]] =
    subCitizenToInterfaceToEdgeMutable.mapValues(_.toMap).toMap

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vfail() // Would need a really good reason to hash something this big

  def lookupStruct(structFullName: FullNameT[IStructNameT]): StructDefinitionT = {
    vassertSome(structs.find(_.instantiatedCitizen.fullName == structFullName))
  }

  def lookupInterface(interfaceFullName: FullNameT[IInterfaceNameT]): InterfaceDefinitionT = {
    vassertSome(interfaces.find(_.instantiatedCitizen.fullName == interfaceFullName))
  }

  def lookupEdge(implFullName: FullNameT[IImplNameT]): EdgeT = {
    vassertOne(interfaceToSubCitizenToEdge.flatMap(_._2.values).find(_.edgeFullName == implFullName))
  }

  def getInstantiationBoundArgs(instantiationName: FullNameT[IInstantiationNameT]): InstantiationBoundArguments = {
    vassertSome(instantiationNameToInstantiationBounds.get(instantiationName))
  }

  def lookupStructByTemplateFullName(structTemplateFullName: FullNameT[IStructTemplateNameT]): StructDefinitionT = {
    vassertSome(structs.find(_.templateName == structTemplateFullName))
  }

  def lookupInterfaceByTemplateFullName(interfaceTemplateFullName: FullNameT[IInterfaceTemplateNameT]): InterfaceDefinitionT = {
    vassertSome(interfaces.find(_.templateName == interfaceTemplateFullName))
  }

  def lookupCitizenByTemplateFullName(interfaceTemplateFullName: FullNameT[ICitizenTemplateNameT]): CitizenDefinitionT = {
    interfaceTemplateFullName match {
      case FullNameT(packageCoord, initSteps, t: IStructTemplateNameT) => {
        lookupStructByTemplateFullName(FullNameT(packageCoord, initSteps, t))
      }
      case FullNameT(packageCoord, initSteps, t: IInterfaceTemplateNameT) => {
        lookupInterfaceByTemplateFullName(FullNameT(packageCoord, initSteps, t))
      }
    }
  }

  def lookupStructByTemplateName(structTemplateName: StructTemplateNameT): StructDefinitionT = {
    vassertOne(structs.filter(_.templateName.last == structTemplateName))
  }

  def lookupInterfaceByTemplateName(interfaceTemplateName: InterfaceTemplateNameT): InterfaceDefinitionT = {
    vassertSome(interfaces.find(_.templateName.last == interfaceTemplateName))
  }

  def lookupFunction(signature2: SignatureT): Option[FunctionT] = {
    functions.find(_.header.toSignature == signature2).headOption
  }

  def lookupFunction(funcTemplateName: IFunctionTemplateNameT): Option[FunctionT] = {
    functions.find(_.header.fullName.last.template == funcTemplateName).headOption
  }

  def lookupFunction(humanName: String): FunctionT = {
    val matches = functions.filter(f => {
      f.header.fullName.last match {
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
      s.templateName.last match {
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
    subCitizenTT: FullNameT[ICitizenNameT],
    interfaceTT: FullNameT[IInterfaceNameT]):
  EdgeT = {
    vassertSome(
      vassertSome(interfaceToSubCitizenToEdge.get(interfaceTT))
        .get(subCitizenTT))
  }

  def lookupInterface(humanName: String): InterfaceDefinitionT = {
    val matches = interfaces.filter(s => {
      s.templateName.last match {
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

  def lookupUserFunction(humanName: String): FunctionT = {
    val matches =
      functions
        .filter(function => simpleName.unapply(function.header.fullName).contains(humanName))
        .filter(_.header.isUserFunction)
    if (matches.size == 0) {
      vfail("Not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def nameIsLambdaIn(name: FullNameT[IFunctionNameT], needleFunctionHumanName: String): Boolean = {
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

  def lookupLambdasIn(needleFunctionHumanName: String): Vector[FunctionT] = {
    functions.filter(f => nameIsLambdaIn(f.header.fullName, needleFunctionHumanName)).toVector
  }

  def lookupLambdaIn(needleFunctionHumanName: String): FunctionT = {
    vassertOne(lookupLambdasIn(needleFunctionHumanName))
  }

  def getAllNonExternFunctions: Iterable[FunctionT] = {
    functions.filter(!_.header.isExtern)
  }

  def getAllUserFunctions: Iterable[FunctionT] = {
    functions.filter(_.header.isUserFunction)
  }
}
