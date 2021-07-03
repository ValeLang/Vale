package net.verdagon.vale.hinputs

import net.verdagon.vale.templar.{CitizenNameT, EdgeT, FullNameT, FunctionT, FunctionExportT, FunctionExternT, FunctionNameT, IFunctionNameT, ImplT, InterfaceEdgeBlueprint, KindExportT, KindExternT, LambdaCitizenNameT, Program2, simpleName}
import net.verdagon.vale.templar.templata.{FunctionBannerT, PrototypeT, SignatureT}
import net.verdagon.vale.templar.types.{InterfaceDefinitionT, InterfaceRefT, KindT, StructDefinitionT, StructRefT}
import net.verdagon.vale.{PackageCoordinate, vassertSome, vfail}

import scala.collection.immutable.List

case class Hinputs(
    interfaces: List[InterfaceDefinitionT],
    structs: List[StructDefinitionT],
    emptyPackStructRef: StructRefT,
    functions: List[FunctionT],
    kindToDestructor: Map[KindT, PrototypeT],
    edgeBlueprintsByInterface: Map[InterfaceRefT, InterfaceEdgeBlueprint],
    edges: List[EdgeT],
    kindExports: List[KindExportT],
    functionExports: List[FunctionExportT],
    kindExterns: List[KindExternT],
    functionExterns: List[FunctionExternT]) {

  def lookupStruct(structRef: StructRefT): StructDefinitionT = {
    structs.find(_.getRef == structRef) match {
      case None => vfail("Couldn't find struct: " + structRef)
      case Some(s) => s
    }
  }
  def lookupInterface(interfaceRef: InterfaceRefT): InterfaceDefinitionT = {
    vassertSome(interfaces.find(_.getRef == interfaceRef))
  }
  def lookupFunction(signature2: SignatureT): Option[FunctionT] = {
    functions.find(_.header.toSignature == signature2).headOption
  }

  def lookupFunction(humanName: String): FunctionT = {
    val matches = functions.filter(f => {
      f.header.fullName.last match {
        case FunctionNameT(n, _, _) if n == humanName => true
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
      s.fullName.last match {
        case CitizenNameT(n, _) if n == humanName => true
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

  def lookupImpl(structRef: StructRefT, interfaceRef: InterfaceRefT): EdgeT = {
    edges.find(impl => impl.struct == structRef && impl.interface == interfaceRef).get
  }

  def lookupInterface(humanName: String): InterfaceDefinitionT = {
    val matches = interfaces.filter(s => {
      s.fullName.last match {
        case CitizenNameT(n, _) if n == humanName => true
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
    val lastThree = name.steps.slice(name.steps.size - 3, name.steps.size)
    lastThree match {
      case List(
      FunctionNameT(functionHumanName, _, _),
      LambdaCitizenNameT(_),
      FunctionNameT("__call", _, _)) if functionHumanName == needleFunctionHumanName => true
      case _ => false
    }
  }

  def lookupLambdaIn(needleFunctionHumanName: String): FunctionT = {
    val matches = functions.filter(f => nameIsLambdaIn(f.header.fullName, needleFunctionHumanName))
    if (matches.size == 0) {
      vfail("Lambda for \"" + needleFunctionHumanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def getAllNonExternFunctions: Iterable[FunctionT] = {
    functions.filter(!_.header.isExtern)
  }
  def getAllUserFunctions: Iterable[FunctionT] = {
    functions.filter(_.header.isUserFunction)
  }
}