package net.verdagon.vale.hinputs

import net.verdagon.vale.templar.{CitizenName2, Edge2, FullName2, Function2, FunctionName2, IFunctionName2, Impl2, InterfaceEdgeBlueprint, LambdaCitizenName2, Program2, simpleName}
import net.verdagon.vale.templar.templata.{FunctionBanner2, Prototype2, Signature2}
import net.verdagon.vale.templar.types.{InterfaceDefinition2, InterfaceRef2, StructDefinition2, StructRef2}
import net.verdagon.vale.vfail

import scala.collection.immutable.List

case class ETable2(struct: StructRef2, table: TetrisTable[InterfaceRef2, InterfaceRef2])

case class Hinputs(
  interfaces: List[InterfaceDefinition2],
  structs: List[StructDefinition2],
  emptyPackStructRef: StructRef2,
  functions: List[Function2],
  externPrototypes: List[Prototype2],
  edgeBlueprintsByInterface: Map[InterfaceRef2, InterfaceEdgeBlueprint],
  edges: List[Edge2]) {

  def lookupStruct(structRef: StructRef2): StructDefinition2 = {
    structs.find(_.getRef == structRef).get
  }
  def lookupInterface(interfaceRef: InterfaceRef2): InterfaceDefinition2 = {
    interfaces.find(_.getRef == interfaceRef).get
  }
  def lookupFunction(signature2: Signature2): Option[Function2] = {
    functions.find(_.header.toSignature == signature2).headOption
  }

  def lookupFunction(humanName: String): Function2 = {
    val matches = functions.filter(f => {
      f.header.fullName.last match {
        case FunctionName2(n, _, _) if n == humanName => true
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

  def lookupStruct(humanName: String): StructDefinition2 = {
    val matches = structs.filter(s => {
      s.fullName.last match {
        case CitizenName2(n, _) if n == humanName => true
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

  def lookupImpl(structRef: StructRef2, interfaceRef: InterfaceRef2): Edge2 = {
    edges.find(impl => impl.struct == structRef && impl.interface == interfaceRef).get
  }

  def lookupInterface(humanName: String): InterfaceDefinition2 = {
    val matches = interfaces.filter(s => {
      s.fullName.last match {
        case CitizenName2(n, _) if n == humanName => true
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

  def lookupUserFunction(humanName: String): Function2 = {
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

  def nameIsLambdaIn(name: FullName2[IFunctionName2], needleFunctionHumanName: String): Boolean = {
    val lastThree = name.steps.slice(name.steps.size - 3, name.steps.size)
    lastThree match {
      case List(
      FunctionName2(functionHumanName, _, _),
      LambdaCitizenName2(_),
      FunctionName2("__call", _, _)) if functionHumanName == needleFunctionHumanName => true
      case _ => false
    }
  }

  def lookupLambdaIn(needleFunctionHumanName: String): Function2 = {
    val matches = functions.filter(f => nameIsLambdaIn(f.header.fullName, needleFunctionHumanName))
    if (matches.size == 0) {
      vfail("Lambda for \"" + needleFunctionHumanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def getAllNonExternFunctions: Iterable[Function2] = {
    functions.filter(!_.header.isExtern)
  }
  def getAllUserFunctions: Iterable[Function2] = {
    functions.filter(_.header.isUserFunction)
  }
}